package Model.CardMarket;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.Shops.ShopCardMatcher;
import Model.Shops.ShopResultEntry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static Model.FilePaths.outputPath;

/**
 * CardScraper.java
 * <p>
 * Scrapes a single CardMarket seller's Yu-Gi-Oh singles offers for cards present in the
 * OuicheList.
 * <p>
 * CardMarket's own "Singles" listing for a seller only sorts/paginates cleanly up to 300
 * results; beyond that it shows "These results haven't been sorted (300+ results)." and
 * ignores {@code sortBy}. When that happens, this scraper falls back to looping over every
 * expansion the seller stocks (read straight out of the page's own filter-widget data,
 * see {@link #extractExpansionMap(Document)}) and paginating each one individually, since
 * a single expansion is very unlikely to also exceed 300 offers.
 * <p>
 * Either way, "is there a next page" is answered the simplest possible way: fetch the next
 * page and check whether CardMarket's own empty-state text is present.
 * <p>
 * Pages are fetched with a real, Selenium-driven Chrome browser rather than a plain HTTP
 * client — CardMarket's seller-offers pages return HTTP 403 to a bare Jsoup request (even
 * with a full browser-like header set), which points to bot detection that checks things a
 * plain HTTP client can't fake, like TLS fingerprint and JS execution. This requires Chrome
 * to be installed on the machine running the app; Selenium's built-in driver manager fetches
 * a matching ChromeDriver automatically, no manual setup needed beyond having Chrome itself.
 */
public class CardScraper {

    private static final Logger logger = LoggerFactory.getLogger(CardScraper.class);

    private static final String TOO_MANY_RESULTS_MARKER = "300+ results";
    private static final String NO_OFFERS_MARKER = "There are no offers for your selected category";

    /**
     * Headless Chrome is more likely to be fingerprinted as a bot than a normal, visible
     * Chrome window. If pages still come back blocked with this on, try flipping it off
     * first before assuming Selenium itself won't work here.
     */
    private static final boolean HEADLESS = true;

    /**
     * Retrieves cards from the given CardMarket seller's offers that are present in the
     * OuicheList.
     *
     * <p>The returned list is sorted by price (cheapest first), but entries for the same
     * card name are kept consecutive: if a card appears at prices 0.10€ and 0.50€, both
     * entries will appear together even if another card costs 0.20€.
     *
     * <p>Results are also written to {@code outputPath/ListeCardMarket_<username>.txt}.
     *
     * @param maOuicheList flat list of card elements from the OuicheList
     * @param maxPrice     entries above this price are skipped
     * @param seller       the CardMarket seller to scrape
     * @return list of {@link ShopResultEntry} objects ready for display
     */
    public static List<ShopResultEntry> getCardNamesFromWebsite(
            List<CardElement> maOuicheList, double maxPrice, CardMarketSeller seller) throws Exception {

        Map<String, Integer> ouicheCountMap = ShopCardMatcher.buildOuicheCountMap(maOuicheList);
        List<ShopResultEntry> result = new ArrayList<>();
        logger.debug("Starting CardMarket scrape for {} with {} OuicheList entries to match against.",
                seller.getDisplayName(), maOuicheList.size());

        String baseUrl = "https://www.cardmarket.com/en/YuGiOh/Users/" + seller.getUsername()
                + "/Offers/Singles?" + buildBaseQueryString(maxPrice);

        WebDriver driver;
        try {
            driver = createDriver();
        } catch (Exception exception) {
            logger.error("Could not start a Chrome browser for CardMarket scraping. "
                    + "Make sure Google Chrome is installed on this machine.", exception);
            return result;
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(outputPath + "\\ListeCardMarket_" + seller.getUsername() + ".txt"),
                        StandardCharsets.UTF_8))) {

            List<Entry> collected = new ArrayList<>();

            politeDelay();
            Document firstPageDocument = fetchPage(driver, baseUrl);

            if (firstPageDocument.text().contains(TOO_MANY_RESULTS_MARKER)) {
                logger.debug("{} has more than 300 unfiltered results; scraping expansion by expansion.",
                        seller.getDisplayName());
                collected.addAll(scrapeByExpansion(driver, baseUrl, firstPageDocument, maOuicheList, maxPrice,
                        ouicheCountMap, seller, writer));
            } else {
                collected.addAll(scrapeSimplePagination(driver, baseUrl, firstPageDocument, maOuicheList, maxPrice,
                        ouicheCountMap, seller, writer));
            }

            // ── 1. Sort all matched entries by price ascending ────────────────────────
            collected.sort(Comparator.comparingDouble(entry -> entry.price));

            // ── 2. Group same-name entries together ───────────────────────────────────
            //    The first occurrence of each name keeps its position in the price-sorted
            //    order; subsequent copies of the same card are placed immediately after it,
            //    even if their price is higher than the next card's first occurrence.
            Map<String, List<Entry>> entriesByName = new LinkedHashMap<>();
            for (Entry entry : collected) {
                entriesByName.computeIfAbsent(entry.name, key -> new ArrayList<>()).add(entry);
            }
            List<Entry> regrouped = new ArrayList<>(collected.size());
            for (List<Entry> group : entriesByName.values()) {
                regrouped.addAll(group);
            }
            collected = regrouped;

            // ── 3. Write to txt file and build the result list ────────────────────────
            Map<String, Integer> occurrenceCounts = new HashMap<>();
            for (Entry entry : collected) {
                int occurrence = occurrenceCounts.getOrDefault(entry.name, 0) + 1;
                occurrenceCounts.put(entry.name, occurrence);

                String priceString = String.format(Locale.US, "%.2f", entry.price);
                StringBuilder line = new StringBuilder();
                line.append(entry.name).append(", Price: ").append(priceString).append("€");

                if (occurrence > 1) {
                    line.append(", Occurrence: ").append(occurrence);
                }
                line.append(", InOuicheList: ").append(entry.ouicheCount);
                line.append(" Link: ").append(entry.productUrl);

                writer.write(line + "\n");
                logger.debug("{}", line);

                result.add(new ShopResultEntry(
                        entry.card, entry.name, entry.price, entry.ouicheCount, entry.productUrl, occurrence));
            }

            writer.write("\n");

        } catch (IOException ioException) {
            logger.error("Error writing CardMarket results file for {}.", seller.getDisplayName(), ioException);
        } finally {
            driver.quit();
        }

        return result;
    }

    // ── Scraping strategies ────────────────────────────────────────────────────────────

    /**
     * Simple case: the base (unfiltered) query already sorts/paginates cleanly. Just page
     * through it with {@code &site=N} until a page comes back empty.
     */
    private static List<Entry> scrapeSimplePagination(
            WebDriver driver, String baseUrl, Document firstPageDocument, List<CardElement> maOuicheList,
            double maxPrice, Map<String, Integer> ouicheCountMap, CardMarketSeller seller, BufferedWriter writer) {

        List<Entry> collected = new ArrayList<>();
        Document pageDocument = firstPageDocument;
        int pageNumber = 1;

        while (true) {
            if (isEmptyResultsPage(pageDocument)) {
                break;
            }
            List<Entry> pageEntries = parseOfferRows(pageDocument, maOuicheList, maxPrice, ouicheCountMap);
            if (pageEntries.isEmpty()) {
                break;
            }
            collected.addAll(pageEntries);

            pageNumber++;
            String pageUrl = baseUrl + "&site=" + pageNumber;
            politeDelay();
            try {
                pageDocument = fetchPage(driver, pageUrl);
            } catch (WebDriverException webDriverException) {
                logFetchFailure(writer, seller, "page " + pageNumber, webDriverException);
                break;
            }
        }

        return collected;
    }

    /**
     * Fallback for sellers with 300+ unfiltered results: read the seller's own expansion
     * list (with counts) straight out of the page we already fetched, then loop each
     * expansion individually, paginating each with {@code &site=N} until it comes back empty.
     */
    private static List<Entry> scrapeByExpansion(
            WebDriver driver, String baseUrl, Document firstPageDocument, List<CardElement> maOuicheList,
            double maxPrice, Map<String, Integer> ouicheCountMap, CardMarketSeller seller, BufferedWriter writer) {

        List<Entry> collected = new ArrayList<>();
        Map<String, String> expansionMap = extractExpansionMap(firstPageDocument);
        logger.debug("Found {} expansions to check for {}.", expansionMap.size(), seller.getDisplayName());

        for (Map.Entry<String, String> expansionEntry : expansionMap.entrySet()) {
            String expansionLabel = stripTrailingCount(expansionEntry.getKey());
            String expansionId = expansionEntry.getValue();
            logger.debug("Scraping expansion: {} (id={})", expansionLabel, expansionId);

            int pageNumber = 1;
            while (true) {
                String pageUrl = baseUrl + "&idExpansion=" + expansionId
                        + (pageNumber > 1 ? "&site=" + pageNumber : "");

                politeDelay();
                Document pageDocument;
                try {
                    pageDocument = fetchPage(driver, pageUrl);
                } catch (WebDriverException webDriverException) {
                    logFetchFailure(writer, seller, "expansion " + expansionLabel + " page " + pageNumber,
                            webDriverException);
                    break;
                }

                if (isEmptyResultsPage(pageDocument)) {
                    break;
                }
                List<Entry> pageEntries = parseOfferRows(pageDocument, maOuicheList, maxPrice, ouicheCountMap);
                if (pageEntries.isEmpty()) {
                    break;
                }
                collected.addAll(pageEntries);
                pageNumber++;
            }
        }

        return collected;
    }

    // ── Page fetching & parsing ─────────────────────────────────────────────────────────

    /**
     * Starts a Chrome session with a handful of standard tweaks to look less obviously
     * automated (a real, non-headless-looking user agent, the "automation" infobar/flag
     * disabled). None of this guarantees passing bot detection — it's the same category of
     * thing any browser-based scraper does, not a way around anything CardMarket couldn't
     * otherwise see.
     */
    private static WebDriver createDriver() {
        ChromeOptions options = new ChromeOptions();
        if (HEADLESS) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        WebDriver driver = new ChromeDriver(options);
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});");
        } catch (Exception exception) {
            logger.debug("Could not patch navigator.webdriver (non-fatal): {}", exception.getMessage());
        }
        return driver;
    }

    private static Document fetchPage(WebDriver driver, String url) {
        driver.get(url);
        waitForPageToSettle(driver);
        return Jsoup.parse(driver.getPageSource(), url);
    }

    /**
     * Waits for the page to finish loading, then gives it a little extra time — CardMarket's
     * automatic bot-check, when it appears, typically resolves and redirects to the real page
     * within a few seconds on its own.
     */
    private static void waitForPageToSettle(WebDriver driver) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(20)).until(webDriver ->
                    "complete".equals(((JavascriptExecutor) webDriver).executeScript("return document.readyState")));
            Thread.sleep(3000);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            logger.warn("Timed out waiting for the page to settle: {}", exception.getMessage());
        }
    }

    private static void politeDelay() {
        try {
            Thread.sleep(1000);
            Thread.sleep((long) (Math.random() * 2000));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    static boolean isEmptyResultsPage(Document doc) { // package-private for tests
        return doc.text().contains(NO_OFFERS_MARKER);
    }

    private static void logFetchFailure(
            BufferedWriter writer, CardMarketSeller seller, String what, WebDriverException cause) {
        logger.warn("Failed to fetch {} for {}: {}", what, seller.getDisplayName(), cause.getMessage());
        try {
            writer.write("Failed to fetch " + what + " for " + seller.getDisplayName()
                    + ": " + cause.getMessage() + "\n");
        } catch (IOException ignored) {
            // Best-effort logging only; nothing more useful to do if the writer itself fails.
        }
    }

    /**
     * Reads the seller's expansion filter (name, idExpansion, offer count) straight out of
     * the JSON embedded in the page's own filter widget
     * ({@code div[data-component-name=CategoryOffersFilterComponent]}, attribute
     * {@code data-props} → {@code options.expansionOptions}). This is present on every
     * offers page for the seller, filtered or not, so no extra request is needed.
     */
    static Map<String, String> extractExpansionMap(Document doc) { // package-private for tests
        Map<String, String> expansionMap = new LinkedHashMap<>();

        Element filterComponent = doc.selectFirst("div[data-component-name=CategoryOffersFilterComponent]");
        if (filterComponent == null) {
            logger.warn("Could not find CardMarket's expansion filter component on the page.");
            return expansionMap;
        }
        String dataProps = filterComponent.attr("data-props");
        if (dataProps.isEmpty()) {
            logger.warn("CardMarket's expansion filter component had no data-props attribute.");
            return expansionMap;
        }

        try {
            JSONObject propsJson = new JSONObject(dataProps);
            JSONArray expansionOptions = propsJson.getJSONObject("options").getJSONArray("expansionOptions");
            for (int index = 0; index < expansionOptions.length(); index++) {
                JSONObject expansionOption = expansionOptions.getJSONObject(index);
                String label = expansionOption.getString("label");
                String idExpansion = String.valueOf(expansionOption.get("value"));
                if ("0".equals(idExpansion)) {
                    continue; // the pseudo "All" entry
                }
                expansionMap.put(label, idExpansion);
            }
        } catch (Exception exception) {
            logger.error("Failed to parse CardMarket's expansion filter JSON.", exception);
        }

        return expansionMap;
    }

    /**
     * Strips the trailing offer count from an expansion label, e.g.
     * "2-Player Starter Deck Yuya &amp; Declan (32)" → "2-Player Starter Deck Yuya &amp; Declan".
     */
    static String stripTrailingCount(String label) { // package-private for tests
        return label.replaceAll("\\s*\\(\\d+\\)$", "");
    }

    static String buildBaseQueryString(double maxPrice) { // package-private for tests
        String maxPriceFormatted = String.format(Locale.US, "%.2f", maxPrice);
        return "maxPrice=" + maxPriceFormatted + "&minAmt=1&sortBy=name_asc";
    }

    /**
     * Parses every offer row on a fetched page, keeping only rows at or under
     * {@code maxPrice} that match a card in the OuicheList. Matching is done the same way
     * every other shop scraper does it (see {@link ShopCardMatcher}); CardMarket doesn't
     * expose a per-card print code on this page (only the set's own code, e.g. "YS15"), so
     * matching here is always name-based.
     */
    static List<Entry> parseOfferRows( // package-private for tests
            Document doc, List<CardElement> maOuicheList, double maxPrice,
            Map<String, Integer> ouicheCountMap) {

        List<Entry> rowEntries = new ArrayList<>();
        Elements rows = doc.select("#UserOffersTable div.article-row");
        int pricedRows = 0;
        List<String> sampleUnmatchedNames = new ArrayList<>();

        for (Element row : rows) {
            Element productLink = row.selectFirst("a[href*=/Products/Singles/]");
            if (productLink == null) {
                continue;
            }
            String name = productLink.text().trim();
            if (name.isEmpty()) {
                continue;
            }

            Element priceElement = row.selectFirst("div.col-offer div.price-container span.color-primary");
            if (priceElement == null) {
                continue;
            }
            String priceText = priceElement.text()
                    .replace("\u00A0", " ").replace("€", "").trim()
                    .replace(',', '.').replaceAll("[^0-9.]", "");
            double price;
            try {
                price = Double.parseDouble(priceText);
            } catch (NumberFormatException numberFormatException) {
                continue;
            }
            pricedRows++;
            if (price > maxPrice) {
                continue;
            }

            String normalizedName = ShopCardMatcher.normalizeForCompare(name);
            Card card = !normalizedName.isEmpty()
                    ? ShopCardMatcher.findCardByNormalizedName(maOuicheList, normalizedName, name)
                    : ShopCardMatcher.findCardByName(maOuicheList, name);
            if (card == null) {
                if (sampleUnmatchedNames.size() < 5) {
                    sampleUnmatchedNames.add(name);
                }
                continue;
            }

            String productUrl = productLink.absUrl("href");
            Entry entry = new Entry(name, price, productUrl);
            entry.card = card;
            String imagePath = card.getImagePath();
            entry.ouicheCount = (imagePath != null) ? ouicheCountMap.getOrDefault(imagePath, 0) : 0;
            rowEntries.add(entry);
        }

        logger.debug("Page: {} row(s) found, {} had a parseable price, {} matched the OuicheList.{}",
                rows.size(), pricedRows, rowEntries.size(),
                (rowEntries.isEmpty() && !sampleUnmatchedNames.isEmpty())
                        ? " Sample unmatched names: " + sampleUnmatchedNames
                        : "");

        return rowEntries;
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    /**
     * Package-private (not private) so CardScraperTest can build/inspect it directly.
     */
    static class Entry {
        final String name;
        final double price;
        final String productUrl;
        int ouicheCount = 0;
        /**
         * The matched Card object.
         */
        Card card = null;

        Entry(String name, double price, String productUrl) {
            this.name = name;
            this.price = price;
            this.productUrl = productUrl;
        }
    }
}
