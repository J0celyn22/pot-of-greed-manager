package Model.CardMarket;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.Shops.ShopCardMatcher;
import Model.Shops.ShopResultEntry;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

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
     * Candidate {@code src} substrings for Cloudflare's interactive checkbox iframe. Widened
     * from a single exact domain: the page's own CSP allows the challenge frame to come from
     * either {@code challenges.cloudflare.com} or a same-origin {@code /cdn-cgi/challenge-platform}
     * path (managed challenges are commonly proxied same-origin), and neither page source
     * captured while writing this actually contained the injected iframe to confirm which one
     * is real here — both were pre-JavaScript snapshots (see {@link #dumpChallengeDiagnostics}).
     */
    private static final String[] CAPTCHA_IFRAME_SRC_MARKERS = {
            "challenges.cloudflare.com",
            "/cdn-cgi/challenge-platform",
    };

    /**
     * Cloudflare's Turnstile widget conventionally has an accessible iframe title like
     * "Widget containing a Cloudflare security challenge" — checked case-insensitively as a
     * fallback for when the {@code src} doesn't match either marker above.
     */
    private static final String CAPTCHA_IFRAME_TITLE_MARKER = "challenge";

    /**
     * The human-readable prompt next to the checkbox, in the languages seen so far. A
     * fallback for when the checkbox isn't inside a matchable iframe at all (e.g. rendered
     * directly in the main document). The French line is written with unicode escapes rather
     * than literal accented characters, matching this file's existing convention.
     */
    private static final String[] CAPTCHA_TEXT_MARKERS = {
            "V\u00e9rifiez que vous \u00eates humain",
            "Verify you are human",
    };

    /**
     * Minimum time to wait before {@link #waitForPageToSettle} attempts to click Cloudflare's
     * interactive checkbox again, once it's already tried it once. Prevents hammering the
     * checkbox on every 500ms content poll — clicking it repeatedly in quick succession is
     * itself an unnatural, bot-like pattern, and this gives Cloudflare's own post-click
     * validation JS time to run before deciding another attempt is needed.
     */
    private static final Duration CHALLENGE_CHECK_INTERVAL = Duration.ofSeconds(5);

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
     * Switches page fetching from a fresh direct-Selenium session per page (see
     * {@link #fetchPage(String)}) to a single {@link PythonCardMarketBridge} session reused
     * across the whole run. Exists as a separate switch, not a replacement, so the direct
     * path stays available to fall back to \u2014 see the class-level discussion in
     * {@link PythonCardMarketBridge} for why the bridge exists at all (SeleniumBase's UC
     * Mode survives more than one request per session; plain Selenium here couldn't).
     * <p>
     * When this is {@code true}, {@code python/cardmarket_bridge.py} needs to actually work
     * on this machine (Python on {@code PATH}, {@code seleniumbase} installed, Chrome
     * installed) \u2014 see {@link PythonCardMarketBridgeLiveDiagnosticTest} to check that in
     * isolation before trusting it for a real run.
     */
    private static final boolean USE_PYTHON_BRIDGE = true;

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

        PythonCardMarketBridge bridge = null;
        if (USE_PYTHON_BRIDGE) {
            try {
                bridge = new PythonCardMarketBridge();
                bridge.start();
            } catch (IOException startupIoException) {
                logger.error("Could not start the Python CardMarket bridge \u2014 check that Python is on "
                        + "PATH with seleniumbase installed, and that python/cardmarket_bridge.py is present "
                        + "relative to the working directory.", startupIoException);
                return result;
            }
        }

        try {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(outputPath + "\\ListeCardMarket_" + seller.getUsername() + ".txt"),
                            StandardCharsets.UTF_8))) {

                List<Entry> collected = new ArrayList<>();

                politeDelay();
                Document firstPageDocument;
                try {
                    firstPageDocument = fetchPage(baseUrl, bridge);
                } catch (Exception exception) {
                    logger.error("Could not fetch the base page for {}. Make sure Google Chrome is installed on "
                            + "this machine.", seller.getDisplayName(), exception);
                    return result;
                }

                if (firstPageDocument.text().contains(TOO_MANY_RESULTS_MARKER)) {
                    logger.debug("{} has more than 300 unfiltered results; scraping expansion by expansion.",
                            seller.getDisplayName());
                    collected.addAll(scrapeByExpansion(baseUrl, firstPageDocument, maOuicheList, maxPrice,
                            ouicheCountMap, seller, writer, bridge));
                } else {
                    collected.addAll(scrapeSimplePagination(baseUrl, firstPageDocument, maOuicheList, maxPrice,
                            ouicheCountMap, seller, writer, bridge));
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
        } finally {
            if (bridge != null) {
                bridge.close();
            }
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
            double maxPrice, Map<String, Integer> ouicheCountMap, CardMarketSeller seller, BufferedWriter writer,
            PythonCardMarketBridge bridge) {

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
            String currentPageUrl = pageNumber == 1 ? baseUrl : baseUrl + "&site=" + pageNumber;
            List<Entry> pageEntries = parseOfferRows(pageDocument, maOuicheList, maxPrice, ouicheCountMap,
                    currentPageUrl);
            collected.addAll(pageEntries);

            pageNumber++;
            String pageUrl = baseUrl + "&site=" + pageNumber;
            politeDelay();
            try {
                pageDocument = fetchPage(pageUrl, bridge);
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
            double maxPrice, Map<String, Integer> ouicheCountMap, CardMarketSeller seller, BufferedWriter writer,
            PythonCardMarketBridge bridge) {

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
                    pageDocument = fetchPage(pageUrl, bridge);
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
                List<Entry> pageEntries = parseOfferRows(pageDocument, maOuicheList, maxPrice, ouicheCountMap,
                        pageUrl);
                collected.addAll(pageEntries);
                pageNumber++;
            }
        }

        return collected;
    }

    // ── Page fetching & parsing ─────────────────────────────────────────────────────────
    /**
     * Selectors tried, in order, for the actual clickable checkbox inside Cloudflare's
     * captcha iframe. This widget's markup can only really be confirmed against the live
     * challenge page, not from documentation, so treat this list as a first guess: if it
     * turns out to miss the real element once you see it live, this is the list to fix —
     * the surrounding poll loop in {@link #waitForPageToSettle} doesn't need to change.
     */
    private static final String[] CAPTCHA_CHECKBOX_SELECTORS = {
            "input[type=checkbox]",
            "label.cb-lb",
            "#challenge-stage",
    };
    /**
     * Null until checked, then whether a GUI is actually available for
     * {@link #showManualChallengeDialog}. Set once by {@link #ensureJavaFxToolkitAvailable}.
     */
    private static volatile Boolean javaFxToolkitAvailable = null;

    /**
     * Starts a Chrome session, using your real profile if {@link #CHROME_USER_DATA_DIR} is
     * set (see its comment), plus a couple of standard tweaks to avoid the most obvious
     * "controlled by automation" tells. None of this guarantees passing bot detection — it's
     * the same category of thing any browser-based scraper does, not a way around anything
     * CardMarket couldn't otherwise see.
     */
    static WebDriver createDriver(boolean headless, boolean keepOffScreen) { // package-private for the live diagnostic test
        return createDriver(headless, keepOffScreen, true);
    }

    /**
     * Same as {@link #createDriver(boolean, boolean)}, with the {@code navigator.webdriver}
     * stealth patch made optional — only so the live diagnostic test can build a
     * patch-disabled control variant to compare against. Production code should keep using
     * the 2-arg overload, which always applies the patch.
     */
    static WebDriver createDriver(boolean headless, boolean keepOffScreen, boolean applyStealthPatch) { // package-private for the live diagnostic test
        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }
        if (!CHROME_USER_DATA_DIR.isBlank()) {
            options.addArguments("--user-data-dir=" + CHROME_USER_DATA_DIR);
            options.addArguments("--profile-directory=" + CHROME_PROFILE_NAME);
        }
        options.addArguments("--window-size=1920,1080");
        if (keepOffScreen) {
            // Positioned fully off any physical monitor before the window is ever created,
            // rather than created on-screen and minimized afterward. A post-launch
            // driver.manage().window().minimize() call still requires Chrome to first paint a
            // real, on-screen, focus-stealing window before the minimize command can be
            // applied — confirmed live: the window was visibly flashing onto the screen and
            // stealing focus on every single page fetch, which made the computer unusable
            // during a run of hundreds of pages. Launching already positioned off-screen means
            // Chrome never paints anything within the visible desktop area in the first place.
            options.addArguments("--window-position=-32000,-32000");
        }
        options.addArguments("--disable-blink-features=AutomationControlled");
        // Deliberately not spoofing the User-Agent string. Overriding only the UA header
        // (as a prior version of this method did, claiming Chrome/124 while the machine
        // actually runs Chrome 150+) does not change Chrome's Client Hints — the
        // Sec-CH-UA / Sec-CH-UA-Full-Version-List request headers and navigator.userAgentData
        // in JS still report the real installed version regardless of this flag. A UA header
        // that disagrees with the browser's own Client Hints is an internally inconsistent
        // fingerprint, and that inconsistency is a stronger bot signal to modern detection
        // than an honest, if unusually recent, version number would be.
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        // A curl comparison of the same request from a real browser vs. this driver showed the
        // real one sending 9 Accept-Language entries (personal language settings built up over
        // time) against this driver's default 4 — a fresh profile's Accept-Language falls back
        // to whatever the OS locale implies, not a real person's actual configured list.
        // intl.accept_languages is the profile preference Chrome actually derives the
        // Accept-Language header from.
        options.setExperimentalOption("prefs", Map.of(
                "intl.accept_languages", "fr-FR,fr,en-US,en,es,de,de-DE,ja"));

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        } catch (Exception exception) {
            logger.debug("Could not set page load timeout (non-fatal): {}", exception.getMessage());
        }
        if (applyStealthPatch) {
            applyStealthPatchToEveryDocument(driver);
        }
        return driver;
    }

    /**
     * Registers the {@code navigator.webdriver} override as a CDP "on every new document"
     * script, instead of running it once via {@link JavascriptExecutor#executeScript} right
     * after driver creation. A one-off {@code executeScript} call at that point only ever
     * patches the initial {@code about:blank} page — the override is silently gone the moment
     * {@link WebDriver#get} navigates to a real document, and gone again on every reload,
     * including the reload Cloudflare's own challenge triggers immediately after the checkbox
     * is validated. That means every page actually inspected by Cloudflare — and specifically
     * the one right after solving the checkbox — was being seen with {@code navigator.webdriver}
     * back to {@code true}, which is a plausible reason the challenge kept reappearing even
     * after clicking it. {@code Page.addScriptToEvaluateOnNewDocument} runs the patch before
     * any page script on every document loaded in this session, reload included.
     * <p>
     * Patches {@code Navigator.prototype}, not {@code navigator} directly, and returns
     * {@code false} rather than {@code undefined} — confirmed via a live check against
     * bot.sannysoft.com that an earlier version of this patch (own-property on {@code
     * navigator}, returning {@code undefined}) still left its stricter "WebDriver (New)" check
     * flagged as "present (failed)", even though a plain {@code navigator.webdriver} read came
     * back {@code null}. A real, non-automated Chrome reports the property as the boolean
     * {@code false}, defined on {@code Navigator.prototype}, not as an own property on the
     * instance — {@code undefined}, and {@code navigator.hasOwnProperty('webdriver')} being
     * {@code true}, are each themselves anomalies a stricter check can catch even when the
     * simple falsy-value check passes.
     */
    private static void applyStealthPatchToEveryDocument(WebDriver driver) {
        if (!(driver instanceof ChromeDriver chromeDriver)) {
            return;
        }
        try {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("source",
                    "Object.defineProperty(Navigator.prototype, 'webdriver', "
                            + "{get: () => false, configurable: true, enumerable: true});");
            chromeDriver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", parameters);
        } catch (Exception exception) {
            logger.debug("Could not register the navigator.webdriver override via CDP (non-fatal): {}",
                    exception.getMessage());
        }
    }

    /**
     * Fetches one page with a brand-new Chrome session, used once and closed immediately —
     * never reused across multiple fetches. One long-lived session making many sequential
     * requests turned out to be the actual trigger for CardMarket's block (confirmed: every
     * isolated single-request test succeeded repeatedly, while the real multi-request scrape
     * kept getting blocked using the exact same page-fetching code) — this is slower (Chrome
     * startup overhead on every single page) but it's what's actually been shown to work.
     * <p>
     * Drives a headed (non-headless) window positioned off-screen rather than a headless one.
     * Headless was the reason a visible browser window kept appearing for essentially every
     * page, not just genuinely Cloudflare-gated ones: live testing showed a headed session
     * reliably getting past Cloudflare while a headless one for the same URL almost never did,
     * no matter how the automated wait/checkbox routine was tuned. Starting off-screen (see
     * {@link #createDriver}) keeps the window from ever appearing on screen or stealing focus
     * for the common case (a page that just loads normally); it's only brought into view in
     * {@link #resolveWithManualFallback}, and only once this method's own settle attempt has
     * actually concluded the page is stuck on a genuine Cloudflare page.
     * <p>
     * Both of Cloudflare's page types — the resolvable challenge and the harder "Attention
     * Required" block — are handed to {@link #resolveWithManualFallback}, which reuses this
     * same driver and session rather than creating a second, separate one: tearing the
     * session down and starting fresh would also discard whatever partial progress Cloudflare's
     * own challenge state had made, and doubles Chrome startup cost on every gated page for no
     * benefit. It makes the window visible for the person to look at rather than silently
     * returning either page type as if it were real content. The hard block page has no
     * checkbox to click, so Retry may genuinely need a wait or a different network before it
     * clears — but the person should still get to see what actually came back instead of it
     * being decided for them.
     * <p>
     * Both checks run against the single page-source snapshot {@link #waitForPageToSettle}
     * itself already confirmed and returns — not a fresh, independent read of the live page.
     * A client-side re-render could otherwise land between two separate reads, briefly hiding
     * real content that was there a moment before and popping the manual-verification window
     * for a page that had actually loaded fine.
     */
    static Document fetchPage(String url) { // package-private for the live diagnostic test
        Document document;
        WebDriver driver = createDriver(false, true); // headed, off-screen: invisible unless Cloudflare needs solving
        try {
            driver.get(url);
            String pageSource = waitForPageToSettle(driver);
            Document settledDocument = Jsoup.parse(pageSource, url);
            boolean stillOnChallengePage = pageSource != null
                    && !hasRecognizedContent(pageSource)
                    && isChallengePage(settledDocument);
            boolean needsManualFallback = stillOnChallengePage || isBlockedByCloudflare(settledDocument);
            document = needsManualFallback ? resolveWithManualFallback(driver, url) : settledDocument;
        } finally {
            driver.quit();
        }
        return document;
    }

    /**
     * Fetches through {@code bridge} if one is running ({@link #USE_PYTHON_BRIDGE}), or the
     * direct-Selenium {@link #fetchPage(String)} otherwise. The two paths' failure modes are
     * deliberately unified here: a {@link PythonCardMarketBridge.PythonBridgeException} gets
     * wrapped as a {@link WebDriverException} so the call sites in
     * {@link #scrapeSimplePagination} and {@link #scrapeByExpansion} don't need their own
     * {@code catch} blocks to change depending on which path actually ran. A
     * {@link ManualChallengeStoppedException} raised while resolving a bridge-side manual
     * fallback is deliberately left unwrapped, for the same reason it isn't caught anywhere
     * along the direct path either — it's meant to propagate all the way out and abort the
     * whole run, not be mistaken for an ordinary fetch failure.
     */
    private static Document fetchPage(String url, PythonCardMarketBridge bridge) {
        if (bridge == null) {
            return fetchPage(url);
        }
        try {
            return fetchPageViaBridge(bridge, url);
        } catch (PythonCardMarketBridge.PythonBridgeException pythonBridgeException) {
            throw new WebDriverException(pythonBridgeException.getMessage(), pythonBridgeException);
        }
    }

    /**
     * The bridge-path counterpart of the manual-fallback branch inside {@link #fetchPage(String)}
     * itself: same "is this actually still stuck" check ({@link #needsManualFallback}), same
     * decision to hand off to a manual-resolve method when it is.
     */
    private static Document fetchPageViaBridge(PythonCardMarketBridge bridge, String url)
            throws PythonCardMarketBridge.PythonBridgeException {
        Document document = bridge.fetchPage(url);
        return needsManualFallback(document) ? resolveBridgeManualFallback(bridge, url) : document;
    }

    /**
     * Whether a fetched page is still stuck on Cloudflare's resolvable challenge, or hit its
     * hard block outright — the same two conditions {@link #fetchPage(String)} checks inline
     * against the raw page-source string {@link #waitForPageToSettle} hands it. This version
     * takes a {@link Document} instead, for callers (the bridge path) that only ever get one
     * back rather than a live driver to read a fresh string from; {@link Document#outerHtml()}
     * re-serializes rather than reproducing the original bytes, but every marker this checks
     * for is plain text or script content jsoup preserves, so that difference doesn't matter
     * here. Left as its own copy rather than folding into {@link #fetchPage(String)} itself —
     * that method's own inline version is deliberately reading the exact snapshot it already
     * settled on, not a document built from it, to avoid a second, racy read; no reason to
     * disturb an already-working method to share three lines of logic.
     */
    private static boolean needsManualFallback(Document doc) {
        boolean stillOnChallengePage = !hasRecognizedContent(doc.outerHtml()) && isChallengePage(doc);
        return stillOnChallengePage || isBlockedByCloudflare(doc);
    }

    /**
     * The bridge-path counterpart of {@link #resolveWithManualFallback}. No equivalent of that
     * method's first step (making an already-open window visible) is needed here: the sidecar's
     * browser is visible for the entire run, not just when something needs solving — see
     * {@code cardmarket_bridge.py}'s own module docstring. Reuses the same Retry/Stop prompt
     * ({@link #promptManualChallengeChoice}) unchanged, since it takes no {@link WebDriver} and
     * doesn't care which fetch path is asking.
     * <p>
     * Unlike the direct path's Retry, which just re-polls the same already-loaded page for
     * content to appear, a bridge Retry sends a fresh {@code "fetch"} request through the
     * sidecar — UC Mode's own disconnect/reconnect navigation runs again. That's the only lever
     * the stdin/stdout protocol offers as it stands (no "just re-read the current page" action
     * exists), and matches what solving the challenge in the visible window should actually
     * produce: the same URL, re-requested, now showing real content instead of the challenge.
     */
    private static Document resolveBridgeManualFallback(PythonCardMarketBridge bridge, String url)
            throws PythonCardMarketBridge.PythonBridgeException {
        while (true) {
            if (promptManualChallengeChoice() == ManualChallengeChoice.STOP) {
                throw new ManualChallengeStoppedException(
                        "Scraping stopped: Cloudflare needed manual verification and the user "
                                + "chose to stop instead of retrying.");
            }
            Document retriedDocument = bridge.fetchPage(url);
            if (!needsManualFallback(retriedDocument)) {
                logger.debug("Recognized content appeared after manual solving; continuing the scrape.");
                return retriedDocument;
            }
            logger.debug("Still on Cloudflare's challenge page after Retry; asking again.");
        }
    }

    /**
     * Waits until the page actually shows something we recognize — the real offers table,
     * CardMarket's own "no offers" text, the "300+ results" banner, or a Cloudflare block
     * page — rather than guessing a fixed delay. A bot-check interstitial's readyState hits
     * "complete" almost instantly too, well before real content loads, so waiting on
     * readyState alone isn't enough.
     * <p>
     * Deliberately does <em>not</em> treat {@link #CHALLENGE_PAGE_MARKER} alone as proof the
     * page is genuinely stuck on Cloudflare's resolvable challenge: CardMarket embeds that
     * same bot-management script on every page, including ones that already have (or are
     * about to have) real content, so the marker is typically present on the very first
     * page-source snapshot regardless of whether the page is actually being challenged or
     * simply hasn't finished rendering yet. Committing to challenge-handling on that first
     * sighting used to abandon this method's own poll loop early and hand off to a separate,
     * coarser check schedule — which is what let a perfectly normal page, or Cloudflare's own
     * self-clearing "Un instant…" flash, end up wrongly reported as still blocked, popping the
     * visible manual-solve window for pages that just needed another second or two to load.
     * <p>
     * Instead, this keeps polling for recognized content across the full deadline below —
     * which is what actually resolves both an ordinary slow load and the self-clearing
     * challenge — and only reaches for the interactive checkbox
     * ({@link #hasInteractiveCaptcha}) when one genuinely appears on the page. Only if the
     * full deadline elapses with no recognized content does the caller ({@link #fetchPage})
     * conclude the page is still blocked and fall back to a visible window; that fallback is
     * meant to be the rare case, not the common one.
     * <p>
     * Returns the exact page-source snapshot this method used to make that call, rather than
     * leaving the caller to read the live page again afterward. Reading twice independently —
     * once here, once in the caller — is a race: the DOM can mutate between the two reads (an
     * ongoing script, a re-render, anything), so a caller's own fresh read could miss content
     * this method had just confirmed a moment earlier, wrongly concluding a perfectly normal
     * page was still stuck and popping the manual-verification window for no reason.
     */
    static String waitForPageToSettle(WebDriver driver) { // package-private for the live diagnostic test
        long deadline = System.currentTimeMillis() + 25_000;
        long nextCaptchaClickAllowedAt = 0;
        String html = driver.getPageSource();

        while (System.currentTimeMillis() < deadline) {
            html = driver.getPageSource();
            if (hasRecognizedContent(html)) {
                return html;
            }
            long now = System.currentTimeMillis();
            if (now >= nextCaptchaClickAllowedAt && hasInteractiveCaptcha(driver)) {
                logger.debug("Cloudflare's interactive checkbox challenge appeared; clicking it.");
                clickCaptchaCheckbox(driver);
                nextCaptchaClickAllowedAt = now + CHALLENGE_CHECK_INTERVAL.toMillis();
                html = driver.getPageSource();
                if (hasRecognizedContent(html)) {
                    return html;
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return html;
            }
        }
        logger.warn("Gave up waiting for recognizable page content after 25s; dumping diagnostics "
                + "and proceeding with whatever loaded.");
        dumpChallengeDiagnostics(driver);
        return html;
    }

    /**
     * Whether {@code html} shows something recognized as real content: the actual offers
     * table, CardMarket's own "no offers" text, the "300+ results" banner, or Cloudflare's
     * hard block page. Factored out because {@link #CHALLENGE_PAGE_MARKER} alone isn't a
     * reliable "still blocked" signal on its own — CardMarket embeds an ongoing Cloudflare
     * bot-management script matching that marker on pages that already loaded real content
     * too, so every check that decides "are we still stuck" needs to rule this in first.
     */
    private static boolean hasRecognizedContent(String html) {
        return html != null && (html.contains("UserOffersTable")
                || html.contains(NO_OFFERS_MARKER)
                || html.contains(TOO_MANY_RESULTS_MARKER)
                || html.contains(CLOUDFLARE_BLOCKED_MARKER));
    }

    /**
     * Captures what the live driver actually sees the moment {@link #waitForPageToSettle}
     * gives up:
     * the current DOM (post-JavaScript — unlike a browser's "View Source", which only ever
     * shows the original server response and won't include anything injected afterward) plus
     * a screenshot. Headless Chrome still renders a real page and can be screenshotted even
     * with no visible window. This exists because neither of the two page sources checked
     * while writing {@link #hasInteractiveCaptcha} actually contained Cloudflare's injected
     * checkbox widget to confirm its markup against — both were snapshots of the page before
     * that widget gets added — so the detection markers there are still a best-effort guess
     * until a real dump like this one confirms or corrects them.
     */
    static void dumpChallengeDiagnostics(WebDriver driver) { // package-private for the live diagnostic test
        String timestamp = String.valueOf(System.currentTimeMillis());

        String htmlFileName = outputPath + "\\CardMarketChallengeDebug_" + timestamp + ".html";
        try (BufferedWriter debugWriter = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(htmlFileName), StandardCharsets.UTF_8))) {
            debugWriter.write(driver.getPageSource());
            logger.warn("Dumped the live challenge page (post-JavaScript) to {}", htmlFileName);
        } catch (IOException ioException) {
            logger.warn("Could not write challenge page HTML dump: {}", ioException.getMessage());
        }

        if (driver instanceof TakesScreenshot) {
            String screenshotFileName = outputPath + "\\CardMarketChallengeDebug_" + timestamp + ".png";
            try {
                File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
                Files.copy(screenshotFile.toPath(), Path.of(screenshotFileName), StandardCopyOption.REPLACE_EXISTING);
                logger.warn("Dumped a screenshot of the challenge page to {}", screenshotFileName);
            } catch (Exception exception) {
                logger.warn("Could not capture a challenge page screenshot: {}", exception.getMessage());
            }
        }
    }

    /**
     * Falls back to making the current, already-navigated driver visible when
     * {@link #waitForPageToSettle} couldn't clear Cloudflare's challenge (or resolve a normal
     * page) on its own within its deadline. Reuses {@code driver} as-is — no re-navigation —
     * since it's already sitting on the URL and possibly mid-way through Cloudflare's own
     * challenge state; starting a second, separate session here would throw that away and pay
     * Chrome's startup cost twice for one page.
     * <p>
     * First checks, without prompting anyone, whether the page has actually settled since
     * {@link #waitForPageToSettle} gave up — its deadline is generous but not infinite, and
     * content can land a moment after it returns. Only if that check also times out does it
     * show the Retry/Stop prompt: the person solves whatever Cloudflare is showing, then
     * chooses Retry (re-check the page) or Stop (cancel this fetch) — Retry can be chosen as
     * many times as needed; the same session stays open the whole time rather than being
     * recreated on every attempt, so nothing solved so far is lost.
     */
    private static Document resolveWithManualFallback(WebDriver driver, String url) {
        makeWindowVisibleForManualSolve(driver);
        String settledHtml = waitForRecognizedContentOnly(driver);
        if (settledHtml != null) {
            logger.debug("Recognized content appeared right after making the window visible; "
                    + "continuing the scrape without prompting.");
            return Jsoup.parse(settledHtml, url);
        }
        while (true) {
            if (promptManualChallengeChoice() == ManualChallengeChoice.STOP) {
                throw new ManualChallengeStoppedException(
                        "Scraping stopped: Cloudflare needed manual verification and the user "
                                + "chose to stop instead of retrying.");
            }
            settledHtml = waitForRecognizedContentOnly(driver);
            if (settledHtml != null) {
                logger.debug("Recognized content appeared after manual solving; continuing the scrape.");
                return Jsoup.parse(settledHtml, url);
            }
            logger.debug("Still on Cloudflare's challenge page after Retry; asking again.");
        }
    }

    /**
     * Brings an already-running driver's window into view so the person can see and solve
     * whatever Cloudflare is showing it. {@link #fetchPage} starts this window positioned
     * fully off-screen (see its comment), not minimized, so it's moved back onto the primary
     * monitor before maximizing — maximizing alone could otherwise size it to whatever
     * (nonexistent) monitor its off-screen coordinates nominally belong to.
     */
    private static void makeWindowVisibleForManualSolve(WebDriver driver) {
        try {
            driver.manage().window().setPosition(new Point(0, 0));
            driver.manage().window().maximize();
        } catch (Exception exception) {
            logger.debug("Could not bring the manual-solve browser window into view (non-fatal): {}",
                    exception.getMessage());
        }
    }

    /**
     * Best-effort warm-up for a fresh profile: visits CardMarket's general category page — not
     * the deep, filtered offers URL {@link #fetchPage} actually wants — and dismisses the
     * cookie-consent banner. A curl comparison of a real browser's request against this
     * driver's showed the real one carrying a {@code cookie_settings} cookie (CardMarket's own
     * consent-banner-dismissed marker) and a {@code _cfuvid} cookie (Cloudflare's per-visitor
     * rate-limiting cookie, meant to distinguish individual visitors sharing an IP) — neither
     * of which our fresh profile had. A profile whose very first-ever request is a deep
     * filtered URL, with no consent banner ever dismissed and no visitor cookie ever
     * established, doesn't resemble how a real first-time visitor's browser actually arrives at
     * that page. This is a hypothesis being tested, not a confirmed fix.
     * <p>
     * Clicks "Only Required Cookies", not "Accept All" — the real profile's own
     * {@code cookie_settings} cookie value ({@code preferences=0,statistics=0,marketing=0})
     * shows that's what was actually chosen there, and accepting all would consent to
     * marketing/analytics tracking that choice explicitly declined.
     */
    static void warmUpFreshProfile(WebDriver driver) { // package-private for the live diagnostic test
        try {
            driver.get("https://www.cardmarket.com/en/YuGiOh");
            waitForPageToSettle(driver);
        } catch (Exception exception) {
            logger.debug("Warm-up navigation to the category page failed (non-fatal): {}", exception.getMessage());
            return;
        }
        try {
            WebElement button = driver.findElement(By.cssSelector("button[data-testid='AcceptRequiredCookies']"));
            button.click();
            logger.debug("Clicked \"Only Required Cookies\" on the consent banner.");
            try {
                Thread.sleep(800); // the banner submits via AJAX, not a page reload — give it a moment
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
        } catch (Exception exception) {
            logger.debug("Could not find/click the \"Only Required Cookies\" button (non-fatal) — "
                    + "banner markup may have changed: {}", exception.getMessage());
        }
    }

    /**
     * Asks the person to Retry or Stop, always via the real {@link Alert} dialog if at all
     * possible — including when called from a background thread with no JavaFX toolkit
     * running yet, like the live diagnostic test's plain JUnit run, where there's no console
     * to type into (an IDE's test runner doesn't attach an interactive stdin the way running
     * a plain {@code main()} does). {@link #promptManualChallengeChoiceViaConsole} only runs
     * as a last resort if the toolkit genuinely can't start (e.g. no display available).
     */
    private static ManualChallengeChoice promptManualChallengeChoice() {
        if (!ensureJavaFxToolkitAvailable()) {
            return promptManualChallengeChoiceViaConsole();
        }
        if (Platform.isFxApplicationThread()) {
            return showManualChallengeDialog();
        }
        // Not the FX thread (e.g. a plain JUnit test's main thread): schedule the dialog
        // onto the FX thread and block this thread until it's actually been answered.
        AtomicReference<ManualChallengeChoice> choiceHolder = new AtomicReference<>();
        CountDownLatch dialogClosedLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            choiceHolder.set(showManualChallengeDialog());
            dialogClosedLatch.countDown();
        });
        try {
            dialogClosedLatch.await();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return ManualChallengeChoice.STOP;
        }
        return choiceHolder.get();
    }

    /**
     * Starts the JavaFX toolkit if it isn't already running — needed when this is called from
     * a plain JUnit run rather than the real app, which has already started it by the time any
     * button click reaches this code. Returns whether a GUI is actually available; only false
     * if the toolkit genuinely can't start (e.g. no display), in which case the caller falls
     * back to the console prompt instead of crashing. Result is cached after the first call.
     */
    private static synchronized boolean ensureJavaFxToolkitAvailable() {
        if (javaFxToolkitAvailable != null) {
            return javaFxToolkitAvailable;
        }
        try {
            Platform.startup(() -> {
            });
            // We're the ones who just started it, which only happens outside the real app.
            // Without this, the platform would shut itself down the moment this first
            // dialog closes, breaking every Retry attempt after the first.
            Platform.setImplicitExit(false);
            javaFxToolkitAvailable = true;
        } catch (IllegalStateException alreadyStarted) {
            // Real app case: Application.launch() already started the toolkit. Leave its
            // implicit-exit setting alone — flipping it here would change the whole app's
            // shutdown behavior for a case that doesn't need it.
            javaFxToolkitAvailable = true;
        } catch (Throwable startupFailure) {
            logger.warn("Could not start the JavaFX toolkit for the manual-verification dialog; "
                    + "falling back to a console prompt: {}", startupFailure.getMessage());
            javaFxToolkitAvailable = false;
        }
        return javaFxToolkitAvailable;
    }

    private static ManualChallengeChoice showManualChallengeDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Cloudflare needs manual verification");
        alert.setHeaderText("A visible browser window has opened for CardMarket's Cloudflare page.");
        alert.setContentText("If a checkbox or wait screen appears, solve it or let it clear, then "
                + "click Retry. If it's Cloudflare's hard block page instead (no checkbox, "
                + "\"Attention Required\"), Retry likely won't help until it clears on its own or "
                + "from a different network — look at the window to tell which case this is. "
                + "Click Stop to cancel this scrape.");

        ButtonType retryButtonType = new ButtonType("Retry");
        ButtonType stopButtonType = new ButtonType("Stop");
        alert.getButtonTypes().setAll(retryButtonType, stopButtonType);

        Optional<ButtonType> result = alert.showAndWait();
        return (result.isPresent() && result.get() == retryButtonType)
                ? ManualChallengeChoice.RETRY
                : ManualChallengeChoice.STOP;
    }

    private static ManualChallengeChoice promptManualChallengeChoiceViaConsole() {
        logger.warn("Cloudflare needs manual verification. Look at the visible browser window — "
                + "solve the checkbox or wait screen if one appears, or note if it's the hard "
                + "\"Attention Required\" block instead (nothing to click there). Then type "
                + "\"retry\" and press Enter here to re-check, or type \"stop\" to give up.");
        Scanner consoleScanner = new Scanner(System.in);
        while (true) {
            String response = consoleScanner.nextLine().trim().toLowerCase(Locale.ROOT);
            if ("retry".equals(response)) {
                return ManualChallengeChoice.RETRY;
            }
            if ("stop".equals(response)) {
                return ManualChallengeChoice.STOP;
            }
            logger.warn("Type \"retry\" or \"stop\".");
        }
    }

    /**
     * Polls for recognized real content or Cloudflare's hard block for up to 25s, without
     * taking any automated action of its own — no checkbox clicking. Deliberately kept
     * separate from {@link #waitForPageToSettle}, which does click a checkbox if one appears:
     * during manual solving, the person is already handling whatever's on screen by hand, and
     * an automated click landing at the same time could interfere with that.
     * <p>
     * Returns the exact page-source snapshot that was recognized, or {@code null} if the
     * deadline elapsed first — never makes the caller read the live page again, for the same
     * reason {@link #waitForPageToSettle} doesn't: a second, independent read can race a DOM
     * mutation and miss content this method had just confirmed.
     */
    private static String waitForRecognizedContentOnly(WebDriver driver) {
        long deadline = System.currentTimeMillis() + 25_000;
        while (System.currentTimeMillis() < deadline) {
            String html = driver.getPageSource();
            if (hasRecognizedContent(html)) {
                return html;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /**
     * Whether Cloudflare's challenge has escalated to the interactive checkbox: either a
     * matching iframe is present (see {@link #findCaptchaFrame}), or — in case the checkbox
     * turns out not to be in an iframe at all — the page text itself carries one of
     * {@link #CAPTCHA_TEXT_MARKERS}. The plain wait-it-out version of the challenge matches
     * neither.
     */
    private static boolean hasInteractiveCaptcha(WebDriver driver) {
        if (findCaptchaFrame(driver) != null) {
            return true;
        }
        String html = driver.getPageSource();
        if (html == null) {
            return false;
        }
        for (String marker : CAPTCHA_TEXT_MARKERS) {
            if (html.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the iframe that looks like Cloudflare's checkbox widget — matched by
     * {@code src} against {@link #CAPTCHA_IFRAME_SRC_MARKERS}, or failing that, by
     * {@code title} against {@link #CAPTCHA_IFRAME_TITLE_MARKER} — or {@code null} if no
     * iframe on the page matches either.
     */
    private static WebElement findCaptchaFrame(WebDriver driver) {
        for (WebElement frame : driver.findElements(By.tagName("iframe"))) {
            String frameSource = frame.getAttribute("src");
            if (frameSource != null) {
                for (String marker : CAPTCHA_IFRAME_SRC_MARKERS) {
                    if (frameSource.contains(marker)) {
                        return frame;
                    }
                }
            }
            String frameTitle = frame.getAttribute("title");
            if (frameTitle != null && frameTitle.toLowerCase(Locale.ROOT).contains(CAPTCHA_IFRAME_TITLE_MARKER)) {
                return frame;
            }
        }
        return null;
    }

    /**
     * Switches into Cloudflare's captcha iframe and clicks the checkbox, then switches back
     * to the main page regardless of whether the click succeeded. If the checkbox was
     * detected via page text rather than a matchable iframe (see {@link #hasInteractiveCaptcha}),
     * there's nothing to switch into, so this just logs that and returns — a case for the
     * manual-solve fallback, not something to guess a click for.
     */
    private static void clickCaptchaCheckbox(WebDriver driver) {
        WebElement captchaFrame = findCaptchaFrame(driver);
        if (captchaFrame == null) {
            logger.warn("The checkbox prompt was detected in the page text, but no matching iframe "
                    + "was found to click inside — leaving it for manual solving.");
            return;
        }
        try {
            driver.switchTo().frame(captchaFrame);
            if (!clickFirstMatchingSelector(driver, CAPTCHA_CHECKBOX_SELECTORS)) {
                logger.warn("Found the Cloudflare captcha iframe but none of the known checkbox "
                        + "selectors matched anything inside it.");
            }
        } catch (Exception exception) {
            logger.warn("Failed to click the Cloudflare captcha checkbox: {}", exception.getMessage());
        } finally {
            driver.switchTo().defaultContent();
        }
    }

    private static boolean clickFirstMatchingSelector(WebDriver driver, String[] selectors) {
        for (String selector : selectors) {
            List<WebElement> matches = driver.findElements(By.cssSelector(selector));
            if (!matches.isEmpty()) {
                matches.get(0).click();
                return true;
            }
        }
        return false;
    }

    /**
     * Parses every offer row on a fetched page, keeping only rows at or under
     * {@code maxPrice} that match a card in the OuicheList. Matching is done the same way
     * every other shop scraper does it (see {@link ShopCardMatcher}); CardMarket doesn't
     * expose a per-card print code on this page (only the set's own code, e.g. "YS15"), so
     * matching here is always name-based.
     *
     * <p>Every {@link Entry} produced from this page is linked to {@code pageUrl} — the
     * seller's own filtered offers page (expansion + page number) — rather than the
     * individual card's generic {@code /Products/Singles/...} page. Only the seller's offers
     * page carries this seller's actual listing and its "Add to cart" button; the generic
     * product page isn't specific to this seller at all.
     */
    static List<Entry> parseOfferRows( // package-private for tests
                                       Document doc, List<CardElement> maOuicheList, double maxPrice,
                                       Map<String, Integer> ouicheCountMap, String pageUrl) {

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

            Entry entry = new Entry(name, price, pageUrl);
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

    private enum ManualChallengeChoice {
        RETRY,
        STOP
    }

    /**
     * Much longer than before (was ~1-3s) — the block DateACard triggered came right after
     * the first few rapid, sequential idExpansion requests, which is exactly the kind of
     * pattern a WAF rule would key on. This is a genuine tradeoff: at ~1-8s per request,
     * a full 600+-expansion scrape realistically takes a couple of hours. If it still gets
     * blocked even at this pace, that's a real signal the pattern itself (not the speed) is
     * what's triggering it, and pushing the delay even higher probably won't change that.
     */
    static void politeDelay() { // package-private for the live diagnostic test
        try {
            Thread.sleep(1000);
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
     * Classifies a fetched page into a short, human-readable outcome label by composing the
     * predicates above, for diagnostic tests to print. Not used by the scraping pipeline
     * itself, which checks each predicate individually where the distinction actually changes
     * behavior. Shared so every diagnostic test describes outcomes the same way regardless of
     * which fetch path (direct Selenium, or a sidecar like the Python bridge) produced the
     * Document.
     */
    static String describeClassification(Document doc) { // package-private for tests
        if (isBlockedByCloudflare(doc)) {
            return "BLOCKED (Cloudflare \"Attention Required\")";
        }
        if (isChallengePage(doc)) {
            return "CHALLENGE PAGE (Cloudflare's resolvable challenge, never settled)";
        }
        if (isEmptyResultsPage(doc)) {
            return "OK \u2014 empty results page";
        }
        if (doc.text().contains(TOO_MANY_RESULTS_MARKER)) {
            return "OK \u2014 \"300+ results\" banner shown";
        }
        if (pageHasOfferRows(doc)) {
            return "OK \u2014 real offer rows found";
        }
        String bodyText = doc.body() != null ? doc.body().text() : "";
        return "UNRECOGNIZED \u2014 title=\"" + doc.title() + "\", first 200 chars of body: "
                + bodyText.substring(0, Math.min(200, bodyText.length()));
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
     * Thrown when the person clicks Stop on the manual-verification prompt. Deliberately a
     * plain, unchecked exception rather than {@link WebDriverException} so it passes straight
     * through the {@code catch (WebDriverException ...)} blocks elsewhere in this class (which
     * only skip the current page or expansion) and aborts the whole scrape instead — matching
     * what "Stop" is supposed to mean. Its writer/output file still gets closed properly on the
     * way out, since {@link #getCardNamesFromWebsite} opens it in a try-with-resources block.
     */
    static class ManualChallengeStoppedException extends RuntimeException { // package-private for tests
        ManualChallengeStoppedException(String message) {
            super(message);
        }
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    /**
     * Package-private (not private) so CardScraperTest can build/inspect it directly.
     */
    static class Entry {
        final String name;
        final double price;
        /**
         * URL of the seller's own filtered offers page (expansion + page number) this entry
         * was found on — not the card's generic {@code /Products/Singles/...} page. Only the
         * seller's offers page has this seller's actual listing and "Add to cart" button.
         */
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