package Model.CardMarket;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * PythonCardMarketBridgeLiveDiagnosticTest.java
 * <p>
 * NOT a normal automated test. Exercises {@link PythonCardMarketBridge} against the live
 * cardmarket.com through the actual stdin/stdout JSON protocol it speaks to
 * {@code python/cardmarket_bridge.py} \u2014 as opposed to {@link CardMarketLiveDiagnosticTest},
 * which exercises {@link CardScraper}'s own direct-Selenium fetch path. Watching the sidecar's
 * Chrome window navigate by hand already confirmed the browser-level approach clears CardMarket's
 * block; these two methods confirm the protocol wrapped around it actually carries a real
 * response back to Java, which manual observation alone doesn't.
 * <p>
 * Prerequisites before running either method:
 * <ul>
 *     <li>Python on {@code PATH} resolving to an interpreter with {@code seleniumbase}
 *     installed ({@code pip install -r python/requirements.txt}) \u2014 see
 *     {@link PythonCardMarketBridge#PYTHON_EXECUTABLE_ARGS} if that's not the case.</li>
 *     <li>{@code python/cardmarket_bridge.py} present relative to the JVM's working directory
 *     (normally the project root) \u2014 see
 *     {@link PythonCardMarketBridge#BRIDGE_SCRIPT_PATH}.</li>
 *     <li>Google Chrome installed.</li>
 * </ul>
 * <p>
 * {@code @Disabled} by default so nothing here ever runs automatically. Run one method at a
 * time, manually, from your IDE, and watch the console for the {@code [cardmarket_bridge.py]}
 * lines the sidecar's own stderr gets forwarded to \u2014 they show what the browser itself is
 * doing, separate from the JSON responses this test reports on.
 */
public class PythonCardMarketBridgeLiveDiagnosticTest {

    private static final Logger logger = LoggerFactory.getLogger(PythonCardMarketBridgeLiveDiagnosticTest.class);

    private static final String SELLER = "DateACard";
    private static final String BASE_URL = "https://www.cardmarket.com/en/YuGiOh/Users/" + SELLER
            + "/Offers/Singles?maxPrice=0.30&minAmt=1&sortBy=name_asc";

    /**
     * A handful of real expansion IDs from DateACard's own list, for the sequential test.
     */
    private static final String[] SAMPLE_EXPANSION_IDS = {"1651", "5420", "1433", "1497", "1672"};

    @Test
    @Disabled("Live network test against real cardmarket.com, through the Python sidecar's stdin/stdout "
            + "protocol \u2014 run manually, by itself.")
    public void singleFetchThroughBridge() throws IOException {
        logger.info("Starting the Python bridge...");
        PythonCardMarketBridge bridge = new PythonCardMarketBridge();
        bridge.start();
        try {
            reportOneFetch(bridge, "Single fetch through the bridge", BASE_URL);
        } finally {
            bridge.close();
        }
    }

    @Test
    @Disabled("Live network test against real cardmarket.com, through the Python sidecar's stdin/stdout "
            + "protocol \u2014 run manually, by itself, and only after singleFetchThroughBridge() has already "
            + "passed. Reuses ONE PythonCardMarketBridge (one sidecar process, one UC Mode browser session) "
            + "across several fetches in a single run \u2014 this is the actual hypothesis being tested "
            + "(automated, back-to-back requests surviving in one session driven through the sidecar), not "
            + "just that manual, click-driven multi-page navigation survives, which was already confirmed "
            + "separately by hand.")
    public void sequentialFetchesThroughBridgeSameSession() throws IOException {
        logger.info("Starting the Python bridge...");
        PythonCardMarketBridge bridge = new PythonCardMarketBridge();
        bridge.start();
        try {
            boolean firstRequest = true;
            for (int index = 0; index < SAMPLE_EXPANSION_IDS.length; index++) {
                if (!firstRequest) {
                    CardScraper.politeDelay();
                }
                firstRequest = false;
                String url = BASE_URL + "&idExpansion=" + SAMPLE_EXPANSION_IDS[index];
                reportOneFetch(bridge, "Sequential bridge fetch #" + (index + 1), url);
            }
        } finally {
            bridge.close();
        }
    }

    private void reportOneFetch(PythonCardMarketBridge bridge, String label, String url) {
        logger.info("=== {} ===", label);
        logger.info("URL: {}", url);
        long startedAt = System.currentTimeMillis();
        try {
            Document document = bridge.fetchPage(url);
            long elapsedMs = System.currentTimeMillis() - startedAt;
            String classification = CardScraper.describeClassification(document);
            logger.info("Got a response after {}ms \u2014 {}", elapsedMs, classification);
            logger.info("Response HTML length: {} characters", document.outerHtml().length());
        } catch (PythonCardMarketBridge.PythonBridgeException pythonBridgeException) {
            long elapsedMs = System.currentTimeMillis() - startedAt;
            logger.error("No usable response after {}ms \u2014 {}", elapsedMs, pythonBridgeException.getMessage());
        }
    }
}