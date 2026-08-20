package Model.CardScanner;

import javafx.application.Platform;
import javafx.scene.image.Image;
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
 * through here yet.
 * <p>
 * One instance is meant to be started once per scanner-pane-open and closed once per
 * scanner-pane-close (see {@code Controller.RealMainController}'s {@code startCardScanner()}/
 * {@code stopCardScanner()}) — not reused across multiple opens, unlike
 * {@link Model.CardMarket.PythonCardMarketBridge}, which is deliberately reused across many
 * fetches in one run. A fresh subprocess per open keeps "opening the pane starts the camera,
 * closing it stops the camera" a simple, always-true invariant, with no shared-instance state
 * to reset between opens.
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

    private final Consumer<Image> frameListener;
    private final Consumer<String> errorMessageListener;

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
     */
    public PythonCardScannerBridge(Consumer<Image> frameListener, Consumer<String> errorMessageListener) {
        this.frameListener = frameListener;
        this.errorMessageListener = errorMessageListener;
    }

    /**
     * Starts the Python sidecar and its background reader threads. Throws if the process can't
     * be started at all (Python not found, script missing) — same caveat as
     * {@link Model.CardMarket.PythonCardMarketBridge#start()}: anything that goes wrong past
     * that point (webcam missing, opencv-python not installed) is only discoverable once the
     * sidecar reports it as an {@code "error"} event, since starting the process successfully
     * doesn't mean the webcam it's about to try opening is actually available.
     */
    public void start() throws IOException {
        List<String> command = new ArrayList<>(PYTHON_EXECUTABLE_ARGS);
        command.add(BRIDGE_SCRIPT_PATH.toString());

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
            default -> logger.warn("card_scanner_bridge.py sent an unrecognized event type '{}': {}", type, line);
        }
    }

    private void handleFrameEvent(JSONObject event) {
        String jpegBase64 = event.optString("jpeg_base64", "");
        if (jpegBase64.isEmpty()) {
            logger.warn("card_scanner_bridge.py sent a \"frame\" event with no jpeg_base64 payload; ignoring.");
            return;
        }

        Image decodedFrame;
        try {
            byte[] jpegBytes = Base64.getDecoder().decode(jpegBase64);
            decodedFrame = new Image(new java.io.ByteArrayInputStream(jpegBytes));
        } catch (IllegalArgumentException decodeException) {
            logger.warn("Could not decode a preview frame (bad base64 or bad JPEG bytes); skipping it.",
                    decodeException);
            return;
        }

        Platform.runLater(() -> frameListener.accept(decodedFrame));
    }

    private void handleErrorEvent(JSONObject event) {
        String message = event.optString("message", "(no message)");
        logger.warn("[card_scanner_bridge.py] error: {}", message);
        if (errorMessageListener != null) {
            Platform.runLater(() -> errorMessageListener.accept(message));
        }
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