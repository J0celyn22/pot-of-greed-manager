package Model.CardScanner;

import javafx.application.Platform;
import javafx.scene.image.Image;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Bridges to {@code python/card_scanner_bridge.py}, the camera card-scanner's Python sidecar,
 * over a line-delimited JSON protocol on the child process's stdin/stdout — same shape as
 * {@link Model.CardMarket.PythonCardMarketBridge}'s bridge to {@code cardmarket_bridge.py}, but
 * an async event stream instead of blocking request/response: the sidecar pushes {@code
 * "frame"}/{@code "status"}/{@code "error"} events on its own schedule as soon as it starts,
 * rather than only replying to a request Java sent first. Java's write side stays
 * request-shaped (currently just {@code "shutdown"}) but one-way — no response line is paired
 * with a command.
 * <p>
 * Unit 4 of the camera card-scanner feature (see the project's camera-scanner plan doc): proves
 * this live-video path end-to-end with a "dumb" feed only — no OCR or card detection is wired
 * through here yet. Unit 6 adds the {@code "detection"} event: the sidecar now also runs OCR on
 * a throttled subset of captured frames and reports whatever text it read with confidence above
 * its own threshold, as an ordered list of candidate strings (highest confidence first, possibly
 * empty). This class stays a dumb transport for that too — resolving a candidate list into an
 * actual {@link Model.CardsLists.Card} (and deciding whether to add it) is
 * {@code Controller.CardScannerCoordinator}'s job, not this class's.
 * <p>
 * One instance is meant to be started once per scanner-pane-open and closed once per
 * scanner-pane-close (see {@code Controller.RealMainController}'s {@code startCardScanner()}/
 * {@code stopCardScanner()}) — not reused across multiple opens, unlike
 * {@link Model.CardMarket.PythonCardMarketBridge}, which is deliberately reused across many
 * fetches in one run. A fresh subprocess per open keeps "opening the pane starts the camera,
 * closing it stops the camera" a simple, always-true invariant, with no shared-instance state
 * to reset between opens.
 * <p>
 * Unit 10 adds {@link #listAvailableCameras()} — a static, blocking helper backing the "choose
 * camera" button, unrelated to any particular instance of this class. It runs the sidecar
 * script as a separate, short-lived {@code --list-cameras} invocation rather than a command sent
 * to an already-running instance, since an instance's subprocess only exists at all once its own
 * camera has already opened successfully; a command-based probe couldn't discover a working
 * camera in the case that matters most, the configured one failing to open. See
 * {@code Controller.CardScannerCoordinator#chooseCameraRequested()} for how it's called and how
 * a selection feeds back into {@link #start(int)}.
 */
public class PythonCardScannerBridge implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(PythonCardScannerBridge.class);

    /**
     * See {@link Model.CardMarket.PythonCardMarketBridge#PYTHON_EXECUTABLE_ARGS}'s own comment
     * for how to adjust this if plain {@code "python"} doesn't resolve to the interpreter
     * {@code opencv-python} was installed into.
     */
    private static final List<String> PYTHON_EXECUTABLE_ARGS = List.of("python");

    private static final Path BRIDGE_SCRIPT_PATH = Paths.get("python", "card_scanner_bridge.py");

    private static final int SHUTDOWN_WAIT_SECONDS = 10;

    /**
     * How long {@link #listAvailableCameras()} waits for the {@code --list-cameras} probe
     * process to exit on its own before forcing it. Probing up to
     * {@code MAX_CAMERAS_TO_PROBE} indices (5, as of this script) can take a few seconds if
     * several don't exist and the OS backend is slow to fail each one — this is a generous
     * upper bound above that, not a tuned value.
     */
    private static final int CAMERA_PROBE_WAIT_SECONDS = 15;

    /**
     * How often a delivered preview frame's FX-thread queue lag and JVM heap usage get logged —
     * every Nth delivery rather than every one, so a 12fps preview session doesn't spam the log.
     * Diagnostic aid for confirming {@link #deliverFrameToPreview} actually keeps the FX thread
     * caught up over a long camera-scan session; see this class's own javadoc.
     */
    private static final int FRAME_LAG_LOG_SAMPLE_INTERVAL = 24;

    private final Consumer<Image> frameListener;
    private final Consumer<String> errorMessageListener;
    private final Consumer<List<String>> detectionListener;

    /**
     * Guards preview-frame backpressure: {@link #handleFrameEvent} only decodes and schedules a
     * new frame when this is {@code false} (no undelivered frame currently in flight), and
     * {@link #deliverFrameToPreview} clears it once the FX thread has actually consumed that
     * frame. A frame event that arrives while this is {@code true} is dropped before its JPEG
     * bytes are even decoded — not decoded-then-discarded — so a temporarily-busy FX thread
     * reduces the sidecar's fixed frame rate down to the FX thread's own consumption rate rather
     * than piling up either undelivered {@link Image}s or the decode work that produces them.
     * See {@link #handleErrorEvent} for the same guard applied to error events, which can arrive
     * in an unthrottled burst if the sidecar's own capture loop starts failing rapidly (e.g. the
     * webcam disconnecting).
     */
    private final AtomicBoolean frameDeliveryPending = new AtomicBoolean(false);

    private final AtomicBoolean errorDeliveryPending = new AtomicBoolean(false);

    private final AtomicLong deliveredFrameCount = new AtomicLong(0);

    private Process process;
    private BufferedWriter processInput;
    private BufferedReader processOutput;
    private Thread stderrDrainThread;
    private Thread stdoutReaderThread;

    /**
     * @param frameListener        called on the JavaFX application thread with each decoded
     *                             preview frame as it arrives. Never called concurrently with
     *                             itself.
     * @param errorMessageListener called on the JavaFX application thread with a
     *                             human-readable message whenever the sidecar reports an
     *                             {@code "error"} event (e.g. the webcam failed to open) — for
     *                             surfacing that in the scanner pane's status label, distinct
     *                             from {@link #logger}, which every event is also logged
     *                             through regardless.
     * @param detectionListener    called on the JavaFX application thread with each {@code
     *                             "detection"} event's candidate text list, ordered
     *                             highest-confidence first — empty (never {@code null}) when a
     *                             detection cycle ran but found nothing above the sidecar's
     *                             confidence threshold. Never called concurrently with itself.
     */
    public PythonCardScannerBridge(Consumer<Image> frameListener, Consumer<String> errorMessageListener,
                                   Consumer<List<String>> detectionListener) {
        this.frameListener = frameListener;
        this.errorMessageListener = errorMessageListener;
        this.detectionListener = detectionListener;
    }

    /**
     * Probes for available cameras by running {@code python/card_scanner_bridge.py
     * --list-cameras} as a fresh, short-lived process — see this class's own javadoc and the
     * script's module docstring for why that's a separate invocation rather than a command sent
     * to an already-running instance. Blocks the calling thread until the probe process reports
     * its result or the wait times out; callers on the JavaFX application thread must invoke
     * this from a background thread (see
     * {@code Controller.CardScannerCoordinator#chooseCameraRequested()}).
     * <p>
     * The probe process's stderr is discarded rather than drained — unlike
     * {@link #drainStderr}, which feeds a long-lived session's diagnostics into {@link #logger}
     * for the whole time it runs, this is a one-shot blocking call whose only meaningful result
     * is the camera list itself; a probe that finds zero cameras is a normal, non-exceptional
     * outcome, not something stderr context would explain further.
     *
     * @return the camera indices that opened successfully, in ascending order (possibly empty,
     * never {@code null})
     * @throws IOException if the probe process itself could not be started (Python not found,
     *                     script missing) — distinct from "no cameras found," which is this
     *                     method returning an empty list normally
     */
    public static List<Integer> listAvailableCameras() throws IOException {
        List<String> command = new ArrayList<>(PYTHON_EXECUTABLE_ARGS);
        command.add(BRIDGE_SCRIPT_PATH.toString());
        command.add("--list-cameras");

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process probeProcess = processBuilder.start();

        List<Integer> availableCameraIndices;
        try (BufferedReader probeOutput = new BufferedReader(
                new InputStreamReader(probeProcess.getInputStream(), StandardCharsets.UTF_8))) {
            String line = probeOutput.readLine();
            availableCameraIndices = line == null ? List.of() : parseCameraListEvent(line);
        }

        try {
            boolean exitedCleanly = probeProcess.waitFor(CAMERA_PROBE_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!exitedCleanly) {
                logger.warn("Camera probe process did not exit within {} seconds; forcing termination.",
                        CAMERA_PROBE_WAIT_SECONDS);
                probeProcess.destroyForcibly();
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            probeProcess.destroyForcibly();
        }

        return availableCameraIndices;
    }

    private void drainStderr(Process childProcess) {
        try (BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(childProcess.getErrorStream(), StandardCharsets.UTF_8))) {
            String line = errorReader.readLine();
            while (line != null) {
                logger.debug("[card_scanner_bridge.py] {}", line);
                line = errorReader.readLine();
            }
        } catch (IOException ioException) {
            logger.debug("Stopped reading the card-scanner bridge's stderr (non-fatal, likely process exit): {}",
                    ioException.getMessage());
        }
    }

    /**
     * Runs for the sidecar's whole life on its own thread, dispatching each incoming stdout
     * line by its {@code "type"} field. Decoding a frame's JPEG bytes into an {@link Image}
     * happens here, off the JavaFX thread; only the final delivery to {@link #frameListener} is
     * pushed onto the JavaFX thread via {@link Platform#runLater}, since that is the only part
     * that touches a live scene-graph node.
     */
    private void readEventsUntilStreamCloses() {
        try {
            String line = processOutput.readLine();
            while (line != null) {
                handleEventLine(line);
                line = processOutput.readLine();
            }
        } catch (IOException ioException) {
            logger.debug("Stopped reading the card-scanner bridge's stdout (non-fatal, likely process exit): {}",
                    ioException.getMessage());
        }
    }

    private void handleEventLine(String line) {
        JSONObject event;
        try {
            event = new JSONObject(line);
        } catch (JSONException jsonException) {
            logger.warn("card_scanner_bridge.py sent a non-JSON line on stdout, ignoring: {}", line);
            return;
        }

        String type = event.optString("type", "");
        switch (type) {
            case "frame" -> handleFrameEvent(event);
            case "status" -> logger.info("[card_scanner_bridge.py] status: {}", event.optString("status", ""));
            case "error" -> handleErrorEvent(event);
            case "detection" -> handleDetectionEvent(event);
            default -> logger.warn("card_scanner_bridge.py sent an unrecognized event type '{}': {}", type, line);
        }
    }

    /**
     * Parses a {@code "detection"} event's {@code candidates} array (each entry a {@code
     * {"text": ..., "confidence": ...}} object) into an ordered list of just the text, and hands
     * it to {@link #detectionListener}. A malformed or missing {@code candidates} array is
     * treated the same as an empty detection cycle rather than a fatal error — one bad line from
     * the sidecar shouldn't take down live detection.
     */
    private void handleDetectionEvent(JSONObject event) {
        List<String> candidateTexts = new ArrayList<>();
        JSONArray candidates = event.optJSONArray("candidates");
        if (candidates != null) {
            for (int index = 0; index < candidates.length(); index++) {
                JSONObject candidate = candidates.optJSONObject(index);
                if (candidate == null) {
                    continue;
                }
                String text = candidate.optString("text", "");
                if (!text.isBlank()) {
                    candidateTexts.add(text);
                }
            }
        }
        if (detectionListener != null) {
            Platform.runLater(() -> detectionListener.accept(candidateTexts));
        }
    }

    /**
     * Only decodes and schedules a frame when no previously-decoded frame is still waiting on
     * the FX thread — see {@link #frameDeliveryPending}'s javadoc for why this is a decode-side
     * gate rather than a post-decode discard: skipping the JPEG decode entirely for frames that
     * would just be thrown away anyway is what actually cuts allocation/CPU churn under sustained
     * backpressure, rather than merely bounding how many decoded frames pile up.
     * <p>
     * Package-private rather than private so {@code PythonCardScannerBridgeFrameCoalescingTest}
     * can exercise the coalescing behavior directly, without a real subprocess — same rationale
     * as {@code Model.Database.CardNameIndex#levenshteinDistance}.
     */
    void handleFrameEvent(JSONObject event) {
        if (!frameDeliveryPending.compareAndSet(false, true)) {
            return;
        }

        long decodeStartNanos = System.nanoTime();
        String jpegBase64 = event.optString("jpeg_base64", "");
        if (jpegBase64.isEmpty()) {
            frameDeliveryPending.set(false);
            logger.warn("card_scanner_bridge.py sent a \"frame\" event with no jpeg_base64 payload; ignoring.");
            return;
        }

        Image decodedFrame;
        try {
            byte[] jpegBytes = Base64.getDecoder().decode(jpegBase64);
            decodedFrame = new Image(new java.io.ByteArrayInputStream(jpegBytes));
        } catch (IllegalArgumentException decodeException) {
            frameDeliveryPending.set(false);
            logger.warn("Could not decode a preview frame (bad base64 or bad JPEG bytes); skipping it.",
                    decodeException);
            return;
        }

        Platform.runLater(() -> deliverFrameToPreview(decodedFrame, decodeStartNanos));
    }

    /**
     * Runs on the JavaFX application thread: delivers {@code frame} to {@link #frameListener},
     * then clears {@link #frameDeliveryPending} so {@link #handleFrameEvent} can decode and
     * schedule the next one. Every {@link #FRAME_LAG_LOG_SAMPLE_INTERVAL}th delivery also logs
     * how long the frame sat waiting (from just before its decode to this callback actually
     * running) and current JVM heap usage against the configured max, to confirm over a long
     * scan session that the FX thread is staying caught up rather than progressively falling
     * behind.
     */
    private void deliverFrameToPreview(Image frame, long decodeStartNanos) {
        long deliveryCount = deliveredFrameCount.incrementAndGet();
        if (deliveryCount % FRAME_LAG_LOG_SAMPLE_INTERVAL == 0) {
            Utils.PerfLog.stage(logger,
                    "preview frame delivery #" + deliveryCount + ": FX-thread queue lag",
                    decodeStartNanos);
            Runtime runtime = Runtime.getRuntime();
            long heapUsedMegabytes = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            long heapMaxMegabytes = runtime.maxMemory() / (1024 * 1024);
            logger.info("[PERF] preview frame delivery #{}: JVM heap used {} MB / {} MB max",
                    deliveryCount, heapUsedMegabytes, heapMaxMegabytes);
        }

        frameListener.accept(frame);
        frameDeliveryPending.set(false);
    }

    /**
     * Every error is logged unconditionally (cheap — just a string), but forwarding to {@link
     * #errorMessageListener} on the FX thread is gated the same way {@link #handleFrameEvent}
     * gates frames: if a previous error's {@link Platform#runLater} callback hasn't run yet,
     * this one is dropped rather than scheduling another. Sidecar failures (e.g. the webcam
     * disconnecting) can make {@code card_scanner_bridge.py} emit error events in a tight,
     * unthrottled retry loop — without this gate, each one would schedule its own callback
     * regardless of how far behind the FX thread already was.
     */
    private void handleErrorEvent(JSONObject event) {
        String message = event.optString("message", "(no message)");
        logger.warn("[card_scanner_bridge.py] error: {}", message);
        if (errorMessageListener != null && errorDeliveryPending.compareAndSet(false, true)) {
            Platform.runLater(() -> {
                errorMessageListener.accept(message);
                errorDeliveryPending.set(false);
            });
        }
    }

    /**
     * Parses a {@code "camera_list"} event's {@code cameras} array (see this class's javadoc)
     * into a plain {@code List<Integer>}. A missing or malformed array, or a line that isn't the
     * expected event type at all, is treated as "no cameras found" rather than a fatal error —
     * mirroring {@link #handleDetectionEvent}'s tolerance of one bad line from the sidecar.
     */
    private static List<Integer> parseCameraListEvent(String line) {
        JSONObject event;
        try {
            event = new JSONObject(line);
        } catch (JSONException jsonException) {
            logger.warn("Camera probe sent a non-JSON line, ignoring: {}", line);
            return List.of();
        }
        if (!"camera_list".equals(event.optString("type", ""))) {
            logger.warn("Camera probe's first line wasn't a \"camera_list\" event, ignoring: {}", line);
            return List.of();
        }

        List<Integer> cameraIndices = new ArrayList<>();
        JSONArray camerasArray = event.optJSONArray("cameras");
        if (camerasArray != null) {
            for (int index = 0; index < camerasArray.length(); index++) {
                int cameraIndex = camerasArray.optInt(index, -1);
                if (cameraIndex >= 0) {
                    cameraIndices.add(cameraIndex);
                }
            }
        }
        return cameraIndices;
    }

    /**
     * Starts the Python sidecar and its background reader threads. Throws if the process can't
     * be started at all (Python not found, script missing) — same caveat as
     * {@link Model.CardMarket.PythonCardMarketBridge#start()}: anything that goes wrong past
     * that point (webcam missing, opencv-python not installed) is only discoverable once the
     * sidecar reports it as an {@code "error"} event, since starting the process successfully
     * doesn't mean the webcam it's about to try opening is actually available.
     *
     * @param cameraIndex which OS camera device the sidecar should open, passed through as
     *                    {@code --camera-index}. See {@link #listAvailableCameras()} for how a
     *                    caller discovers which indices are actually available to pass here.
     */
    public void start(int cameraIndex) throws IOException {
        List<String> command = new ArrayList<>(PYTHON_EXECUTABLE_ARGS);
        command.add(BRIDGE_SCRIPT_PATH.toString());
        command.add("--camera-index");
        command.add(String.valueOf(cameraIndex));

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        process = processBuilder.start();

        processInput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        processOutput = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        stderrDrainThread = new Thread(() -> drainStderr(process), "card-scanner-bridge-stderr");
        stderrDrainThread.setDaemon(true);
        stderrDrainThread.start();

        stdoutReaderThread = new Thread(this::readEventsUntilStreamCloses, "card-scanner-bridge-stdout");
        stdoutReaderThread.setDaemon(true);
        stdoutReaderThread.start();

        logger.info("Started the card-scanner Python bridge (pid {}).", process.pid());
    }

    /**
     * @return {@code true} if the sidecar process is currently running.
     */
    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    /**
     * Asks the sidecar to shut down cleanly (releasing the webcam) and waits briefly before
     * forcibly terminating it if it hasn't exited on its own. Blocks the calling thread for up
     * to {@link #SHUTDOWN_WAIT_SECONDS} seconds in the worst case — callers on the JavaFX
     * application thread should invoke this from a background thread rather than directly, to
     * avoid freezing the UI while the sidecar shuts down (see
     * {@code Controller.RealMainController#stopCardScanner()}).
     */
    @Override
    public void close() {
        if (process == null) {
            return;
        }
        try {
            if (process.isAlive()) {
                JSONObject shutdownRequest = new JSONObject();
                shutdownRequest.put("action", "shutdown");
                processInput.write(shutdownRequest.toString());
                processInput.newLine();
                processInput.flush();
            }
        } catch (IOException ioException) {
            logger.debug("Could not send a clean shutdown request (non-fatal): {}", ioException.getMessage());
        }

        try {
            boolean exitedCleanly = process.waitFor(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS);
            if (!exitedCleanly) {
                logger.warn("Card-scanner bridge did not exit within {} seconds of shutdown; forcing termination.",
                        SHUTDOWN_WAIT_SECONDS);
                process.destroyForcibly();
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }

        closeQuietly(processInput);
        closeQuietly(processOutput);
    }

    private void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ioException) {
            logger.debug("Non-fatal error closing a bridge stream: {}", ioException.getMessage());
        }
    }
}