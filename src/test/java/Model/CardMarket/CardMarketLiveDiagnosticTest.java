package Model.CardMarket;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;

import java.util.ArrayList;
import java.util.List;

/**
 * CardMarketLiveDiagnosticTest.java
 * <p>
 * NOT a normal automated test. Each method makes real navigations to the live cardmarket.com
 * through the exact same path the real scraper uses ({@link CardScraper#fetchPage}), and
 * reports what happened. A retry only happens if there's a genuine no-response failure
 * (network error, DNS failure, page-load timeout) — never just because the response turned
 * out to be a Cloudflare block page. A block page is a definitive answer, not a failed fetch.
 * <p>
 * {@code @Disabled} by default so nothing here ever runs automatically. Run one method at a
 * time, manually, from your IDE.
 */
public class CardMarketLiveDiagnosticTest {

    private static final String SELLER = "DateACard";
    private static final String BASE_URL = "https://www.cardmarket.com/en/YuGiOh/Users/" + SELLER
            + "/Offers/Singles?maxPrice=0.30&minAmt=1&sortBy=name_asc";

    /**
     * Retries only apply to a genuine no-response failure, not to a blocked/definitive answer.
     */
    private static final int MAX_ATTEMPTS_ON_NO_RESPONSE = 3;

    /** A handful of real expansion IDs from DateACard's own list, for the sequential test. */
    private static final String[] SAMPLE_EXPANSION_IDS = {"1651", "5420", "1433", "1497", "1672"};

    @Test
    @Disabled("Live network test against real cardmarket.com — run manually, by itself")
    public void mainSellerPage() {
        fetchOnceAndReport("Main seller page, no filter", BASE_URL);
    }

    @Test
    @Disabled("Live network test against real cardmarket.com — run manually, by itself, "
            + "and only as a separate step after checking mainSellerPage()")
    public void oneFilteredSetPage() {
        fetchOnceAndReport("One filtered set page (idExpansion=1651)", BASE_URL + "&idExpansion=1651");
    }

    @Test
    @Disabled("Live network test against real cardmarket.com — run manually, by itself. "
            + "Makes 5 real requests, one per fresh Chrome session, same as the real scraper now does.")
    public void fiveSequentialFilteredPagesEachWithAFreshSession() {
        // Validates the fresh-session-per-request fix at small scale before trusting it to a
        // full 623-expansion run: each of these 5 requests gets its own brand-new Chrome
        // session (exactly what CardScraper.fetchPage does now), rather than sharing one
        // session across all 5 like the old (blocked) design did. The delay between requests
        // matters here too — without it, this test itself fires far faster (roughly 2s apart)
        // than the real scraper ever does (8-15s via CardScraper.politeDelay()), which is a
        // bot-like burst pattern that risks tripping or escalating a Cloudflare block on its
        // own, independent of anything this test is meant to validate.
        boolean firstRequest = true;
        for (String expansionId : SAMPLE_EXPANSION_IDS) {
            if (!firstRequest) {
                CardScraper.politeDelay();
            }
            firstRequest = false;
            fetchOnceAndReport("Sequential test, expansion id=" + expansionId,
                    BASE_URL + "&idExpansion=" + expansionId);
        }
    }

    @Test
    @Disabled("Live network test against real cardmarket.com — run manually, by itself. "
            + "Deliberately reuses ONE Chrome session across 3 pages, unlike the real scraper's "
            + "normal fresh-session-per-page behavior, to isolate what specifically changes on "
            + "the second and third request within a single continuous session.")
    public void threeSequentialFilteredPagesSameSession() {
        // Bypasses CardScraper.fetchPage's own fresh-driver-per-page behavior on purpose —
        // that's the opposite experiment from fiveSequentialFilteredPagesEachWithAFreshSession
        // above. Uses the exact same building blocks fetchPage itself uses (createDriver,
        // waitForPageToSettle, dumpChallengeDiagnostics, isBlockedByCloudflare, etc.) so each
        // page's outcome here is directly comparable to a real fetchPage() call — the only
        // variable being changed is whether the driver/session is shared across requests.
        WebDriver driver = CardScraper.createDriver(false, true); // headed, off-screen, same as production
        try {
            boolean firstRequest = true;
            for (int i = 0; i < 3 && i < SAMPLE_EXPANSION_IDS.length; i++) {
                if (!firstRequest) {
                    CardScraper.politeDelay();
                }
                firstRequest = false;
                String url = BASE_URL + "&idExpansion=" + SAMPLE_EXPANSION_IDS[i];
                reportOnePageSameSession(driver, "Same-session request #" + (i + 1), url);
            }
        } finally {
            driver.quit();
        }
    }

    private void reportOnePageSameSession(WebDriver driver, String label, String url) {
        System.out.println("=== " + label + " ===");
        System.out.println("URL: " + url);
        long startedAt = System.currentTimeMillis();
        driver.get(url);
        String html = CardScraper.waitForPageToSettle(driver);
        long elapsedMs = System.currentTimeMillis() - startedAt;
        Document doc = Jsoup.parse(html, url);
        System.out.println("Settled after " + elapsedMs + "ms \u2014 " + describe(doc));
        // Dumped for every request, not just ones that time out internally, so page 1 (expected
        // fine) and a later blocked/challenged page can be compared side by side afterward.
        CardScraper.dumpChallengeDiagnostics(driver);
    }

    @Test
    @Disabled("Live network test — but deliberately NOT against cardmarket.com. Run manually, by "
            + "itself. Points our exact driver configuration at a public bot-detection test page "
            + "instead, to check what it actually exposes (navigator.webdriver in particular) "
            + "without spending another request against a site we suspect is already flagging us.")
    public void checkAutomationFingerprintOnThirdPartySite() {
        // Same createDriver(false, true) call fetchPage() itself uses in production — the
        // point is to see exactly what THIS configuration exposes, not a hypothetical one.
        // bot.sannysoft.com is a well-known, free automation-detection test page: it runs a
        // battery of checks (WebDriver flag, headless tells, plugin/language lists, WebGL
        // vendor, etc.) and renders them as a pass/fail table. If it's ever down or replaced,
        // the direct navigator.webdriver check below and the screenshot still give a usable
        // answer on their own.
        WebDriver driver = CardScraper.createDriver(false, true);
        try {
            driver.get("https://bot.sannysoft.com/");
            try {
                Thread.sleep(2000); // the page's checks populate the table asynchronously after load
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            Object webdriverFlag = ((JavascriptExecutor) driver).executeScript("return navigator.webdriver;");
            System.out.println("navigator.webdriver reports: " + webdriverFlag);
            System.out.println("(expected on a normal, non-automated browser: null or undefined \u2014 "
                    + "anything else, including the literal string \"true\", means our CDP patch "
                    + "for this isn't actually taking effect)");
            CardScraper.dumpChallengeDiagnostics(driver);
        } finally {
            driver.quit();
        }
    }

    private void fetchOnceAndReport(String label, String url) {
        System.out.println("=== " + label + " ===");
        System.out.println("URL: " + url);

        List<WebDriverException> failures = new ArrayList<>();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS_ON_NO_RESPONSE; attempt++) {
            try {
                Document doc = CardScraper.fetchPage(url);
                // A response came back at all - definitive, whatever it is. Stop here,
                // don't retry just because it happens to be a block page.
                System.out.println("Attempt " + attempt + ": got a response \u2014 " + describe(doc));
                return;
            } catch (WebDriverException webDriverException) {
                failures.add(webDriverException);
                boolean lastAttempt = attempt == MAX_ATTEMPTS_ON_NO_RESPONSE;
                System.out.println("Attempt " + attempt + ": NO RESPONSE (" + webDriverException.getMessage()
                        + ")" + (lastAttempt ? " \u2014 giving up." : " \u2014 retrying."));
            }
        }
        System.out.println("Gave up after " + MAX_ATTEMPTS_ON_NO_RESPONSE
                + " attempts with no response at all (not even a block page). Last error: "
                + (failures.isEmpty() ? "none" : failures.get(failures.size() - 1).getMessage()));
    }

    private String describe(Document doc) {
        if (CardScraper.isBlockedByCloudflare(doc)) {
            return "BLOCKED (Cloudflare \"Attention Required\")";
        }
        if (CardScraper.isEmptyResultsPage(doc)) {
            return "OK \u2014 empty results page";
        }
        if (doc.text().contains("300+ results")) {
            return "OK \u2014 \"300+ results\" banner shown";
        }
        if (CardScraper.pageHasOfferRows(doc)) {
            return "OK \u2014 real offer rows found";
        }
        String bodyText = doc.body() != null ? doc.body().text() : "";
        return "UNRECOGNIZED \u2014 title=\"" + doc.title() + "\", first 200 chars of body: "
                + bodyText.substring(0, Math.min(200, bodyText.length()));
    }
}