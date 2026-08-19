package Model.CardMarket;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Bridges to {@code python/cardmarket_bridge.py}, a SeleniumBase UC Mode sidecar, over a
 * line-delimited JSON protocol on the child process's stdin/stdout: {@code "fetch"} (navigate
 * and settle), {@code "check"} (re-poll the already-loaded page without navigating — see
 * {@link #checkCurrentPage()}), and {@code "restart_session"} (fresh browser, same process —
 * see {@link #restartSession()}), alongside {@code "shutdown"}. Exists because
 * {@link CardScraper}'s own Selenium+ChromeDriver setup was traced (see the project's history)
 * to a block happening after the first request on a CDP-attached session, at a layer plain
 * Selenium's Java API doesn't expose a way to change — UC Mode addresses that specific layer,
 * but is Python-only tooling.
 * <p>
 * One instance is meant to be started once per scraping run and reused across every page fetch
 * — not recreated per page — both to avoid paying UC Mode's Chrome-startup cost repeatedly, and
 * because reusing one browser session across many requests is UC Mode's whole point (unlike
 * plain Selenium, which {@link CardScraper} found couldn't survive a second request per session
 * at all — see {@code CardScraper.fetchPage}'s own comment). Implements {@link AutoCloseable} so
 * a full run can be wrapped in a single try-with-resources block.
 * <p>
 * Confirmed against the real site: two full runs (5 sequential fetches each, one browser
 * session, driven through this exact class via
 * {@code PythonCardMarketBridgeLiveDiagnosticTest}) came back with real classifiable HTML on
 * every fetch, and one browser session survived all of them. The one bug that testing did turn
 * up was on the Python side (an unconditional startup banner corrupting the first stdout line —
 * see {@code cardmarket_bridge.py}'s own docstring for the fix); this class's own protocol
 * handling needed no changes.
 */
public class PythonCardMarketBridge implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(PythonCardMarketBridge.class);

    /**
     * Adjust if {@code python} on your PATH doesn't resolve to the interpreter
     * {@code seleniumbase} was installed into. On Windows with multiple Python versions
     * installed side by side (e.g. 3.13 and 3.7), the per-version launcher is typically
     * invoked as {@code py} with a separate version flag argument — for example
     * {@code List.of("py", "-3.13")}, not a single combined string, since each element here
     * becomes one argument to {@link ProcessBuilder}.
     */
    private static final List<String> PYTHON_EXECUTABLE_ARGS = List.of("python");

    private static final Path BRIDGE_SCRIPT_PATH = Paths.get("python", "cardmarket_bridge.py");

    private static final int SHUTDOWN_WAIT_SECONDS = 10;

    private Process process;
    private BufferedWriter processInput;
    private BufferedReader processOutput;
    private Thread stderrDrainThread;

    /**
     * Starts the Python sidecar. Throws if the process can't be started at all (Python not
     * found, script missing) — anything that goes wrong past that point (browser failing to
     * launch inside the sidecar, SeleniumBase not installed, etc.) is only discoverable once a
     * fetch is attempted and its response or absence is examined, since this method has no way
     * to distinguish "still starting up" from "already broken" without an explicit readiness
     * signal in the protocol.
     */
    public void start() throws IOException {
        List<String> command = new ArrayList<>(PYTHON_EXECUTABLE_ARGS);
        command.add(BRIDGE_SCRIPT_PATH.toString());

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        process = processBuilder.start();

        processInput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        processOutput = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        // The child's stderr is where cardmarket_bridge.py's own log() calls go, plus anything
        // SeleniumBase/Selenium print there directly. Left undrained, a full pipe buffer can
        // block the child process entirely, so this thread's only job is to keep consuming it
        // and forward each line into our own logger.
        stderrDrainThread = new Thread(() -> drainStderr(process), "cardmarket-bridge-stderr");
        stderrDrainThread.setDaemon(true);
        stderrDrainThread.start();

        logger.info("Started the CardMarket Python bridge (pid {}).", process.pid());
    }

    private void drainStderr(Process childProcess) {
        try (BufferedReader errorReader = new BufferedReader(
                new InputStreamReader(childProcess.getErrorStream(), StandardCharsets.UTF_8))) {
            String line = errorReader.readLine();
            while (line != null) {
                logger.debug("[cardmarket_bridge.py] {}", line);
                line = errorReader.readLine();
            }
        } catch (IOException ioException) {
            logger.debug("Stopped reading the Python bridge's stderr (non-fatal, likely process exit): {}",
                    ioException.getMessage());
        }
    }

    /**
     * Fetches one page through the Python sidecar and returns it as a parsed {@link Document}
     * — the same contract as {@code CardScraper.fetchPage(String)}, so existing downstream code
     * ({@link CardScraper#isBlockedByCloudflare}, {@link CardScraper#isChallengePage}, and the
     * rest of the matching pipeline) keeps working unchanged regardless of which fetch path
     * produced the Document. A {@code "blocked"} response is still returned as an ordinary
     * Document — it isn't this class's job to decide what a blocked page means, only to hand
     * back whatever the sidecar actually saw.
     *
     * @throws PythonBridgeException if the sidecar reports a protocol/process-level error, or
     *                               produces no response at all (process died, wrote malformed output).
     */
    public Document fetchPage(String url) throws PythonBridgeException {
        JSONObject request = new JSONObject();
        request.put("action", "fetch");
        request.put("url", url);

        JSONObject response = sendRequestAndAwaitResponse(request);
        String status = response.optString("status", "");
        String html = response.optString("html", "");
        String finalUrl = response.optString("url", url);

        if ("ok".equals(status) || "blocked".equals(status)) {
            return Jsoup.parse(html, finalUrl);
        }
        throw new PythonBridgeException("Python bridge reported an error for " + url + ": "
                + response.optString("message", "(no message)"));
    }

    /**
     * Re-reads whatever page is already loaded in the bridge's browser, polling for it to
     * settle — without navigating anywhere first. Sends the sidecar's {@code "check"} action.
     * <p>
     * This exists specifically for {@link CardScraper}'s manual-fallback Retry: after a person
     * solves Cloudflare's checkbox by hand in the bridge's visible window, the page they just
     * solved is already sitting there loaded. Calling {@link #fetchPage(String)} again for
     * Retry would re-navigate to the same URL from scratch — throwing away whatever the person
     * just solved and asking Cloudflare fresh, which is both pointless and was, in practice,
     * visibly restarting the browser mid-solve. This method reads the current state instead,
     * the same way the direct-Selenium path's own Retry
     * ({@code CardScraper.waitForRecognizedContentOnly}) just re-polls its already-open driver
     * without a fresh {@code driver.get(url)}.
     *
     * @throws PythonBridgeException same failure modes as {@link #fetchPage(String)}
     */
    public Document checkCurrentPage() throws PythonBridgeException {
        JSONObject request = new JSONObject();
        request.put("action", "check");

        JSONObject response = sendRequestAndAwaitResponse(request);
        String status = response.optString("status", "");
        String html = response.optString("html", "");
        String finalUrl = response.optString("url", "");

        if ("ok".equals(status) || "blocked".equals(status)) {
            return Jsoup.parse(html, finalUrl);
        }
        throw new PythonBridgeException("Python bridge reported an error while checking the current page: "
                + response.optString("message", "(no message)"));
    }

    /**
     * Closes the bridge's current browser session and opens a brand new one in its place,
     * without restarting the sidecar process itself — sends the sidecar's
     * {@code "restart_session"} action and waits for its acknowledgement. Every fetch after
     * this call behaves like the very first fetch of a fresh run (a brand-new profile, no
     * carried-over Cloudflare flags from whatever got the old session hard-blocked).
     * <p>
     * Meant for {@link CardScraper}'s manual-fallback handling specifically when Cloudflare's
     * hard "Attention Required" block page turns up, not for its ordinary resolvable-challenge
     * Retry path (that one should use {@link #checkCurrentPage()} instead, to avoid discarding
     * a person's just-solved checkbox for no reason) — restarting the session is comparatively
     * expensive (full Chrome relaunch) and only worth paying when the old session is the
     * problem, not when it's mid-solve.
     *
     * @throws PythonBridgeException same failure modes as {@link #fetchPage(String)}
     */
    public void restartSession() throws PythonBridgeException {
        JSONObject request = new JSONObject();
        request.put("action", "restart_session");

        JSONObject response = sendRequestAndAwaitResponse(request);
        String status = response.optString("status", "");
        if (!"ok".equals(status)) {
            throw new PythonBridgeException("Python bridge reported an error while restarting its session: "
                    + response.optString("message", "(no message)"));
        }
    }

    private JSONObject sendRequestAndAwaitResponse(JSONObject request) throws PythonBridgeException {
        if (process == null || !process.isAlive()) {
            throw new PythonBridgeException("Python bridge process is not running \u2014 call start() first, "
                    + "or check whether it exited unexpectedly.");
        }
        try {
            processInput.write(request.toString());
            processInput.newLine();
            processInput.flush();

            String responseLine = processOutput.readLine();
            if (responseLine == null) {
                throw new PythonBridgeException("Python bridge closed its output stream without responding "
                        + "\u2014 it likely crashed. Check the logged [cardmarket_bridge.py] lines above for why.");
            }
            try {
                return new JSONObject(responseLine);
            } catch (JSONException jsonException) {
                throw new PythonBridgeException("Python bridge sent a non-JSON line on stdout, which should "
                        + "never happen per its own protocol contract (stdout is reserved for JSON, everything "
                        + "else goes to stderr) \u2014 the raw line was: " + responseLine, jsonException);
            }
        } catch (IOException ioException) {
            throw new PythonBridgeException("I/O error talking to the Python bridge: " + ioException.getMessage(),
                    ioException);
        }
    }

    /**
     * Asks the sidecar to shut down cleanly, then waits briefly before forcibly terminating it
     * if it hasn't exited on its own.
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
                logger.warn("Python bridge did not exit within {} seconds of shutdown; forcing termination.",
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

    /**
     * Thrown for any failure talking to the Python bridge itself — process not running,
     * malformed/missing response, or an explicit {@code "status": "error"} from the sidecar.
     * Distinct from a normal {@code "blocked"} classification, which is not an error: it comes
     * back as an ordinary Document for existing code
     * ({@link CardScraper#isBlockedByCloudflare}) to recognize, same as any other page content.
     */
    public static class PythonBridgeException extends Exception {
        public PythonBridgeException(String message) {
            super(message);
        }

        public PythonBridgeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}