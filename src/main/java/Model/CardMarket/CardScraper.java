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
    private static final String CLOUDFLARE_BLOCKED_MARKER = "Attention Required! | Cloudflare";
    /**
     * Cloudflare's resolvable JS/Turnstile challenge ("Un instant…" / "Just a moment…") —
     * different from {@link #CLOUDFLARE_BLOCKED_MARKER}: this one can clear on its own
     * (confirmed manually: ~5s in a real browser), the block page never does. Matching on
     * this script path rather than the page title keeps it working regardless of the
     * response's language.
     */
    private static final String CHALLENGE_PAGE_MARKER = "challenge-platform";

    /**
     * Headless Chrome is more likely to be fingerprinted as a bot than a normal, visible
     * Chrome window. Off by default now — DateACard's real run got a hard Cloudflare
     * "Access Denied" block (not a timing/JS-challenge issue) on essentially every request
     * past the first, so it's worth ruling out headless-mode fingerprinting as a contributor.
     */
    /**
     * Headless by default — every test so far (headless on, headless off, minimized window)
     * has had the exact same outcome: first request succeeds, second gets blocked. No test
     * has actually isolated headless mode as the variable that mattered, so there's no real
     * evidence for keeping the window visible at the cost of it popping up on screen.
     */
    private static final boolean HEADLESS = true;

    /**
     * Point this at your own Chrome user-data directory to have Selenium drive your real
     * profile (real cookies, history, and accumulated trust) instead of a brand-new, blank
     * one. This looks like the actual fix, not the delay/headless tweaks — a fresh,
     * zero-history profile immediately doing systematic per-expansion requests is a strong
     * automation signal on its own, and your regular Chrome working fine on the same
     * machine/network rules out an IP-level block.
     * <p>
     * Your regular Chrome must be fully closed while the scraper runs — Chrome refuses to
     * let two processes share one profile directory.
     * <p>
     * Windows: usually {@code C:\Users\<you>\AppData\Local\Google\Chrome\User Data} — check
     * chrome://version on the "Profile Path" line to get yours exactly (it'll show the full
     * path including the profile folder name, e.g. "...\User Data\Default" — put everything
     * up to "User Data" here, and the last folder name in CHROME_PROFILE_NAME below).
     * <p>
     * Leave CHROME_USER_DATA_DIR blank to fall back to a fresh profile (previous behavior).
     */
    private static final String CHROME_USER_DATA_DIR = "";
    private static final String CHROME_PROFILE_NAME = "Default";

    /**
     * Caps how many expansions {@link #scrapeByExpansion} processes in a single run, for
     * testing at a small, controlled scale before committing to a full run (DateACard has
     * 623 expansions — a full run is a multi-hour commitment, and sustained repeated
     * requests haven't been validated the same way a single one has). Set to
     * {@code Integer.MAX_VALUE} for normal use (no limit); set to a small number like 5 or
     * 10 to test a short, deliberately bounded run first.
     */
    private static final int MAX_EXPANSIONS_PER_RUN = 5/*Integer.MAX_VALUE*/;

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

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(outputPath + "\\ListeCardMarket_" + seller.getUsername() + ".txt"),
                        StandardCharsets.UTF_8))) {

            List<Entry> collected = new ArrayList<>();

            politeDelay();
            Document firstPageDocument;
            try {
                firstPageDocument = fetchPage(baseUrl);
            } catch (Exception exception) {
                logger.error("Could not fetch the base page for {}. Make sure Google Chrome is installed on "
                        + "this machine.", seller.getDisplayName(), exception);
                return result;
            }

            if (firstPageDocument.text().contains(TOO_MANY_RESULTS_MARKER)) {
                logger.debug("{} has more than 300 unfiltered results; scraping expansion by expansion.",
                        seller.getDisplayName());
                collected.addAll(scrapeByExpansion(baseUrl, firstPageDocument, maOuicheList, maxPrice,
                        ouicheCountMap, seller, writer));
            } else {
                collected.addAll(scrapeSimplePagination(baseUrl, firstPageDocument, maOuicheList, maxPrice,
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
        }

        return result;
    }

    // ── Scraping strategies ────────────────────────────────────────────────────────────

    /**
     * Simple case: the base (unfiltered) query already sorts/paginates cleanly. Just page
     * through it with {@code &site=N} until a page comes back empty.
     */
    private static List<Entry> scrapeSimplePagination(
            String baseUrl, Document firstPageDocument, List<CardElement> maOuicheList,
            double maxPrice, Map<String, Integer> ouicheCountMap, CardMarketSeller seller, BufferedWriter writer) {

        List<Entry> collected = new ArrayList<>();
        Document pageDocument = firstPageDocument;
        int pageNumber = 1;

        while (true) {
            if (isEmptyResultsPage(pageDocument)) {
                break;
            }
            if (!pageHasOfferRows(pageDocument)) {
                dumpUnexpectedPage(writer, seller, "page " + pageNumber, pageDocument);
                break;
            }
            List<Entry> pageEntries = parseOfferRows(pageDocument, maOuicheList, maxPrice, ouicheCountMap);
            collected.addAll(pageEntries);

            pageNumber++;
            String pageUrl = baseUrl + "&site=" + pageNumber;
            politeDelay();
            try {
                pageDocument = fetchPage(pageUrl);
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
            String baseUrl, Document firstPageDocument, List<CardElement> maOuicheList,
            double maxPrice, Map<String, Integer> ouicheCountMap, CardMarketSeller seller, BufferedWriter writer) {

        List<Entry> collected = new ArrayList<>();
        Map<String, String> expansionMap = extractExpansionMap(firstPageDocument);
        logger.debug("Found {} expansions to check for {}.", expansionMap.size(), seller.getDisplayName());
        if (expansionMap.size() > MAX_EXPANSIONS_PER_RUN) {
            logger.warn("MAX_EXPANSIONS_PER_RUN is set to {}, well below the {} expansions found for {} — "
                            + "only checking the first {} this run.",
                    MAX_EXPANSIONS_PER_RUN, expansionMap.size(), seller.getDisplayName(), MAX_EXPANSIONS_PER_RUN);
        }

        int expansionsProcessed = 0;
        for (Map.Entry<String, String> expansionEntry : expansionMap.entrySet()) {
            if (expansionsProcessed >= MAX_EXPANSIONS_PER_RUN) {
                break;
            }
            expansionsProcessed++;
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
                    pageDocument = fetchPage(pageUrl);
                } catch (WebDriverException webDriverException) {
                    logFetchFailure(writer, seller, "expansion " + expansionLabel + " page " + pageNumber,
                            webDriverException);
                    break;
                }

                if (isEmptyResultsPage(pageDocument)) {
                    break;
                }
                if (!pageHasOfferRows(pageDocument)) {
                    dumpUnexpectedPage(writer, seller, "expansion " + expansionLabel + " page " + pageNumber,
                            pageDocument);
                    break;
                }
                List<Entry> pageEntries = parseOfferRows(pageDocument, maOuicheList, maxPrice, ouicheCountMap);
                collected.addAll(pageEntries);
                pageNumber++;
            }
        }

        return collected;
    }

    // ── Page fetching & parsing ─────────────────────────────────────────────────────────

    /**
     * Starts a Chrome session, using your real profile if {@link #CHROME_USER_DATA_DIR} is
     * set (see its comment), plus a couple of standard tweaks to avoid the most obvious
     * "controlled by automation" tells. None of this guarantees passing bot detection — it's
     * the same category of thing any browser-based scraper does, not a way around anything
     * CardMarket couldn't otherwise see.
     */
    static WebDriver createDriver() { // package-private for the live diagnostic test
        ChromeOptions options = new ChromeOptions();
        if (HEADLESS) {
            options.addArguments("--headless=new");
        }
        if (!CHROME_USER_DATA_DIR.isBlank()) {
            options.addArguments("--user-data-dir=" + CHROME_USER_DATA_DIR);
            options.addArguments("--profile-directory=" + CHROME_PROFILE_NAME);
        }
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-blink-features=AutomationControlled");
        // Restored from the last confirmed-working commit (804b84f) — removing this in
        // favor of Chrome's real UA was based on a real-profile conflict theory that never
        // actually applied (CHROME_USER_DATA_DIR has been blank in every real test since).
        // A common, everywhere-in-the-wild version string plausibly blends in better than
        // an unusually high, recent build number (Chrome's real UA when this regressed:
        // 150.0.7871.186) that stands out to fingerprinting.
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        } catch (Exception exception) {
            logger.debug("Could not set page load timeout (non-fatal): {}", exception.getMessage());
        }
        if (!HEADLESS) {
            // Headless Chrome has no real window to minimize — this call is meaningless
            // when HEADLESS is true, and window-management operations on a headless
            // "window" are a known source of quirky, version-dependent behavior. Only
            // run it when there's an actual window to minimize.
            try {
                driver.manage().window().minimize();
            } catch (Exception exception) {
                logger.debug("Could not minimize the browser window (non-fatal): {}", exception.getMessage());
            }
        }
        try {
            ((JavascriptExecutor) driver).executeScript(
                    "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});");
        } catch (Exception exception) {
            logger.debug("Could not patch navigator.webdriver (non-fatal): {}", exception.getMessage());
        }
        return driver;
    }

    /**
     * Fetches one page with a brand-new Chrome session, used once and closed immediately —
     * never reused across multiple fetches. One long-lived session making many sequential
     * requests turned out to be the actual trigger for CardMarket's block (confirmed: every
     * isolated single-request test succeeded repeatedly, while the real multi-request scrape
     * kept getting blocked using the exact same page-fetching code) — this is slower (Chrome
     * startup overhead on every single page) but it's what's actually been shown to work.
     */
    static Document fetchPage(String url) { // package-private for the live diagnostic test
        WebDriver driver = createDriver();
        try {
            driver.get(url);
            waitForPageToSettle(driver);
            return Jsoup.parse(driver.getPageSource(), url);
        } finally {
            driver.quit();
        }
    }

    /**
     * Waits until the page actually shows something we recognize — the real offers table,
     * CardMarket's own "no offers" text, the "300+ results" banner, or a Cloudflare block
     * page — rather than guessing a fixed delay. A bot-check interstitial's readyState hits
     * "complete" almost instantly too, well before real content loads, so waiting on
     * readyState alone isn't enough. The Cloudflare block page is a definitive answer, not
     * something to keep waiting past — recognizing it here means we stop in ~1s instead of
     * wasting the full timeout on a page that will never change.
     * <p>
     * Cloudflare's other, resolvable challenge page ({@link #CHALLENGE_PAGE_MARKER}) gets
     * special handling: the base 25s window extends to 60s total the moment that page is
     * seen, since it's known to sometimes clear on its own (confirmed manually) but
     * automation may take longer to clear it than a real human browsing session did.
     */
    private static void waitForPageToSettle(WebDriver driver) {
        long deadline = System.currentTimeMillis() + 25_000;
        boolean sawChallengePage = false;

        while (System.currentTimeMillis() < deadline) {
            String html = driver.getPageSource();
            if (html != null) {
                if (html.contains("UserOffersTable")
                        || html.contains(NO_OFFERS_MARKER)
                        || html.contains(TOO_MANY_RESULTS_MARKER)
                        || html.contains(CLOUDFLARE_BLOCKED_MARKER)) {
                    return;
                }
                if (!sawChallengePage && html.contains(CHALLENGE_PAGE_MARKER)) {
                    sawChallengePage = true;
                    deadline = System.currentTimeMillis() + 60_000;
                    logger.debug("Cloudflare's resolvable challenge page appeared; "
                            + "waiting up to 60s total for it to clear on its own.");
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        logger.warn("Gave up waiting for recognizable page content after {}; proceeding with whatever loaded.",
                sawChallengePage ? "60s (a Cloudflare challenge page appeared but never cleared)" : "25s");
    }

    /**
     * Much longer than before (was ~1-3s) — the block DateACard triggered came right after
     * the first few rapid, sequential idExpansion requests, which is exactly the kind of
     * pattern a WAF rule would key on. This is a genuine tradeoff: at ~8-15s per request,
     * a full 300+-expansion scrape realistically takes a couple of hours. If it still gets
     * blocked even at this pace, that's a real signal the pattern itself (not the speed) is
     * what's triggering it, and pushing the delay even higher probably won't change that.
     */
    private static void politeDelay() {
        try {
            Thread.sleep(8000);
            Thread.sleep((long) (Math.random() * 7000));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    static boolean isEmptyResultsPage(Document doc) { // package-private for tests
        return doc.text().contains(NO_OFFERS_MARKER);
    }

    static boolean isBlockedByCloudflare(Document doc) { // package-private for tests
        String title = doc.title();
        return title != null && title.contains("Attention Required");
    }

    static boolean isChallengePage(Document doc) { // package-private for tests
        return doc.html().contains(CHALLENGE_PAGE_MARKER);
    }

    /**
     * Whether the page has real offer rows at all, independent of whether any of them match
     * the OuicheList. Used to decide whether to keep paginating — {@link #parseOfferRows}
     * only returns *matched* rows, so checking that being empty would wrongly stop
     * pagination on any page that simply had no overlap with the OuicheList.
     */
    static boolean pageHasOfferRows(Document doc) { // package-private for tests
        return !doc.select("#UserOffersTable div.article-row").isEmpty();
    }

    /**
     * A page came back with no offer rows, but it also wasn't recognized as CardMarket's own
     * "no offers" state — something unexpected got captured (a bot-check interstitial, a
     * login wall, a changed page layout, etc). Dumps the actual HTML so it can be inspected,
     * instead of silently treating it the same as a real empty page.
     */
    private static void dumpUnexpectedPage(
            BufferedWriter writer, CardMarketSeller seller, String context, Document doc) {
        String debugFileName = outputPath + "\\CardMarketDebug_" + seller.getUsername()
                + "_" + System.currentTimeMillis() + ".html";
        try (BufferedWriter debugWriter = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(debugFileName), StandardCharsets.UTF_8))) {
            debugWriter.write(doc.outerHtml());
        } catch (IOException ioException) {
            logger.warn("Could not write debug dump for {}: {}", context, ioException.getMessage());
        }

        String reason;
        if (isBlockedByCloudflare(doc)) {
            reason = "got Cloudflare's hard block page (final, nothing to wait out)";
        } else if (isChallengePage(doc)) {
            reason = "got stuck on Cloudflare's resolvable challenge page — it never cleared "
                    + "even after waiting up to 60s";
        } else {
            reason = "no offer rows found, and CardMarket's own \"no offers\" text wasn't present either "
                    + "\u2014 Selenium likely didn't get the real page";
        }
        String message = context + ": " + reason + ". Dumped what it actually saw to " + debugFileName;
        logger.warn("{}", message);
        try {
            writer.write(message + "\n");
        } catch (IOException ignored) {
            // Best-effort logging only.
        }
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
