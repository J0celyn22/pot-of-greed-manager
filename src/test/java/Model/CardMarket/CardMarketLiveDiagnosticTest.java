package Model.CardMarket;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;

/**
 * CardMarketLiveDiagnosticTest.java
 * <p>
 * NOT a normal automated test. Every method here makes real requests to the live
 * cardmarket.com, through the exact same Selenium/Chrome path the real scraper uses
 * ({@link CardScraper#createDriver()} / {@link CardScraper#fetchPage}) — so whatever
 * CHROME_USER_DATA_DIR is set to in CardScraper.java applies here too.
 * <p>
 * Every method is {@code @Disabled} so nothing here ever runs automatically (a full test
 * run, CI, etc.). Run ONE method at a time, manually, from your IDE, when you actually want
 * to investigate — each one makes only 2 requests (not the full 623-expansion scrape), so
 * you get an answer in seconds instead of hours, and each one changes exactly one thing
 * relative to the first request, so a pass/fail actually isolates something.
 */
public class CardMarketLiveDiagnosticTest {

    private static final String SELLER = "DateACard";
    private static final String BASE_URL = "https://www.cardmarket.com/en/YuGiOh/Users/" + SELLER
            + "/Offers/Singles?maxPrice=0.30&minAmt=1&sortBy=name_asc";

    @Test
    @Disabled("Live network test against real cardmarket.com — run manually, one at a time")
    public void secondRequestToTheSameUnfilteredUrl() {
        // Isolates: does a 2nd request in the session get blocked even with no filter and
        // no pagination at all — i.e. is it about being request #2 in general, not about
        // idExpansion/site specifically?
        runDiagnostic("Repeat of the exact same base URL", BASE_URL, BASE_URL);
    }

    @Test
    @Disabled("Live network test against real cardmarket.com — run manually, one at a time")
    public void paginatingTheUnfilteredUrl() {
        // Isolates: does simple pagination (&site=2, no idExpansion filter) also get
        // blocked, or does it only happen when idExpansion is involved?
        runDiagnostic("Page 2 of the base URL, no filter", BASE_URL, BASE_URL + "&site=2");
    }

    @Test
    @Disabled("Live network test against real cardmarket.com — run manually, one at a time")
    public void filteringByExpansion() {
        // The case we already know fails in the full scraper — reproduced here in the
        // smallest possible harness for a clean, apples-to-apples comparison against the
        // two tests above.
        runDiagnostic("idExpansion filter", BASE_URL, BASE_URL + "&idExpansion=1651");
    }

    private void runDiagnostic(String label, String firstUrl, String secondUrl) {
        WebDriver driver = CardScraper.createDriver();
        try {
            System.out.println("=== " + label + " ===");

            Document first = CardScraper.fetchPage(driver, firstUrl);
            System.out.println("Request 1 [" + firstUrl + "]: " + describe(first));

            Document second = CardScraper.fetchPage(driver, secondUrl);
            System.out.println("Request 2 [" + secondUrl + "]: " + describe(second));
        } finally {
            driver.quit();
        }
    }

    private String describe(Document doc) {
        if (doc.title() != null && doc.title().contains("Attention Required")) {
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
        return "UNEXPECTED \u2014 title=\"" + doc.title() + "\", first 200 chars of body: "
                + doc.body().text().substring(0, Math.min(200, doc.body().text().length()));
    }
}
