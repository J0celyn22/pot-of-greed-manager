package Model.CardMarket;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
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
        // session across all 5 like the old (blocked) design did.
        for (String expansionId : SAMPLE_EXPANSION_IDS) {
            fetchOnceAndReport("Sequential test, expansion id=" + expansionId,
                    BASE_URL + "&idExpansion=" + expansionId);
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
