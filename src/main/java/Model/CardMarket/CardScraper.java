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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * A single expansion CAN still exceed 300 offers though — confirmed live (a seller's own
 * unfiltered "2019 Gold Sarcophagus Tin Mega Pack" listing alone showed the same "300+
 * results" banner and an open-ended "Page 1 of 15+" pagination label). When one expansion is
 * itself still 300+, {@link #scrapeByExpansion} narrows further by rarity (the seller's own
 * {@code idRarity} filter, read the same way as expansions — see
 * {@link #extractRarityMap(Document)}), one rarity value at a time, since rarity × expansion
 * is very unlikely to also exceed 300. If some rarity slice is somehow STILL 300+ (no further
 * filter dimension left to add), that slice is logged to the console and summarized with a
 * single link to its own first page in the output file, rather than iterating potentially
 * dozens of pages for it.
 * <p>
 * Either way, "is there a next page" is answered from CardMarket's own "Page X of Y" label
 * (see {@link #extractTotalPageCount}) when it's present — stopping right after the last real
 * page instead of always fetching one page past the end just to confirm it's empty. Falls back
 * to fetch-and-check-if-empty when that label isn't found. That label can also be the
 * open-ended "Page X of Y+" form on a still-300+ page, which {@link #extractTotalPageCount}
 * deliberately refuses to treat as an exact count (Y is a floor, not the true last page).
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
    private static final int MAX_EXPANSIONS_PER_RUN = Integer.MAX_VALUE;

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
    private static final String CHALLENGE_PROMPT_TITLE = "Cloudflare needs manual verification";
    private static final String CHALLENGE_PROMPT_HEADER =
            "A visible browser window has opened for CardMarket's Cloudflare page.";
    private static final String CHALLENGE_PROMPT_CONTENT =
            "If a checkbox or wait screen appears, solve it or let it clear, then click Retry. "
                    + "If it's Cloudflare's hard block page instead (no checkbox, \"Attention "
                    + "Required\"), Retry likely won't help until it clears on its own or from a "
                    + "different network — look at the window to tell which case this is. "
                    + "Click Stop to cancel this scrape.";
    private static final String HARD_BLOCK_PROMPT_TITLE = "Cloudflare blocked this session";
    private static final String HARD_BLOCK_PROMPT_HEADER =
            "CardMarket's Cloudflare showed the hard \"Attention Required\" block page — "
                    + "nothing to solve in this session.";
    private static final String HARD_BLOCK_PROMPT_CONTENT =
            "Click Retry to close this browser session and start a fresh one, then try this page "
                    + "again — this discards the current session's cookies/profile and takes a moment "
                    + "to relaunch Chrome. If the block is tied to this network/IP rather than the "
                    + "session, a fresh session may hit the same block again. "
                    + "Click Stop to cancel this scrape.";

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
     * Regex for CardMarket's own "Page X of Y" pagination label — e.g. "Page 1 of 2" — used
     * by {@link #extractTotalPageCount} to read how many pages a filtered result set actually
     * has. Matched against {@link Document#text()} rather than a specific CSS selector: the
     * exact markup around this label hasn't been confirmed against a live page (CardMarket's
     * Cloudflare protection blocks a plain fetch even for inspection purposes), while the text
     * itself is what was actually confirmed live. A text-pattern match is also more resilient
     * to markup/class changes than a specific selector would be.
     * <p>
     * {@code Y} is intentionally unbounded digits, not capped — CardMarket could show
     * three-or-more-digit page counts for a very large unfiltered result set.
     * <p>
     * The trailing {@code (\+)?} captures an optional literal {@code "+"} right after the
     * digits — confirmed live on a 300+-results page, which showed "Page 1 of 15+", not a
     * plain "Page 1 of 15". That "+" means "at least 15, true count unknown" (matches this
     * seller's own "300+" hit count shown elsewhere on the same page), not "exactly 15". A
     * naive {@code \d+} capture still matches and returns 15 from "15+", which is wrong in a
     * way that actively loses data rather than just missing an optimization: stopping after
     * page 15 would silently drop every offer on pages 16 and beyond, for a result set that
     * can run into the hundreds of pages. {@link #extractTotalPageCount} checks this capture
     * group and refuses to return a count at all when it's present, falling back to
     * fetch-until-empty for exactly this case, the same as when the label is missing entirely.
     */
    private static final Pattern PAGE_COUNT_PATTERN = Pattern.compile("Page\\s+\\d+\\s+of\\s+(\\d+)(\\+)?");

    /**
     * Simple case: the base (unfiltered) query already sorts/paginates cleanly. Pages through
     * it with {@code &site=N}, stopping once CardMarket's own "Page X of Y" label (read from
     * the first page, see {@link #extractTotalPageCount}) says there's nothing left — rather
     * than always fetching one page past the end just to confirm it's empty. If that label
     * isn't found (unrecognized markup), falls back to fetching until a page comes back empty,
     * same as before this optimization existed.
     */
    private static List<Entry> scrapeSimplePagination(
            String baseUrl, Document firstPageDocument, List<CardElement> maOuicheList,
            double maxPrice, Map<String, Integer> ouicheCountMap, CardMarketSeller seller, BufferedWriter writer,
            PythonCardMarketBridge bridge) {

        List<Entry> collected = new ArrayList<>();
        Document pageDocument = firstPageDocument;
        int pageNumber = 1;
        Optional<Integer> totalPages = extractTotalPageCount(firstPageDocument);

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

            if (totalPages.isPresent() && pageNumber >= totalPages.get()) {
                break; // Just parsed the last page per CardMarket's own count — no next-page fetch needed.
            }

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
     * expansion individually, stopping each one as soon as CardMarket's own "Page X of Y"
     * label says there's nothing left, the same way {@link #scrapeSimplePagination} does — see
     * {@link #extractTotalPageCount} for why and its fallback when that label isn't found.
     * <p>
     * A single expansion can itself still be 300+ results (confirmed live — see this class's
     * own doc comment). When that happens for a given expansion, this method narrows further
     * by rarity via {@link #scrapeExpansionByRarity}, one rarity value at a time, instead of
     * paginating that expansion's raw (still-300+, unsorted) result set directly.
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

            String expansionUrl = baseUrl + "&idExpansion=" + expansionId;
            PaginatedScrapeResult expansionResult = scrapePaginated(
                    expansionUrl, maOuicheList, maxPrice, ouicheCountMap, seller, writer, bridge,
                    "expansion " + expansionLabel);
            collected.addAll(expansionResult.entries);

            if (expansionResult.stillTooManyResults) {
                collected.addAll(scrapeExpansionByRarity(
                        expansionUrl, firstPageDocument, expansionLabel, maOuicheList, maxPrice, ouicheCountMap,
                        seller, writer, bridge));
            }
        }

        return collected;
    }

    /**
     * Narrows a single expansion that's itself still 300+ results (see {@link #scrapeByExpansion}'s
     * own doc comment for when this is reached) by looping over the seller's rarity filter
     * ({@link #extractRarityMap}) on top of the expansion filter already applied, one rarity
     * value at a time — {@code &idExpansion=X&idRarity=Y}. Rarity × expansion is very unlikely
     * to also exceed 300 offers, the same assumption {@link #scrapeByExpansion} itself makes
     * about expansion alone relative to a seller's whole unfiltered catalog.
     * <p>
     * {@code firstPageDocument} is the seller's original unfiltered (or not-yet-rarity-filtered)
     * page — the same one {@link #extractExpansionMap} already read expansions from — since the
     * rarity filter's own option list ({@code options.rarityOptions}) lives in that same JSON
     * blob and doesn't need a fresh fetch to read.
     * <p>
     * If some rarity slice is somehow STILL 300+ even narrowed by both expansion and rarity —
     * no further filter dimension is applied here — that slice is not paginated at all. Instead:
     * a warning is logged to the console, and {@link #writeUnresolvedOverflowSummaryLine} adds
     * one line to the output file naming the slice and linking only its first page, rather than
     * silently iterating a potentially large number of pages for a sliver of the catalog this
     * scraper has no further way to narrow automatically.
     */
    private static List<Entry> scrapeExpansionByRarity(
            String expansionUrl, Document firstPageDocument, String expansionLabel,
            List<CardElement> maOuicheList, double maxPrice, Map<String, Integer> ouicheCountMap,
            CardMarketSeller seller, BufferedWriter writer, PythonCardMarketBridge bridge) {

        List<Entry> collected = new ArrayList<>();
        Map<String, String> rarityMap = extractRarityMap(firstPageDocument);
        logger.info("Expansion \"{}\" for {} is still 300+ results on its own; narrowing further by "
                        + "rarity ({} rarity values to check).",
                expansionLabel, seller.getDisplayName(), rarityMap.size());

        for (Map.Entry<String, String> rarityEntry : rarityMap.entrySet()) {
            String rarityLabel = rarityEntry.getKey();
            String rarityId = rarityEntry.getValue();
            String expansionAndRarityUrl = expansionUrl + "&idRarity=" + rarityId;
            String sliceDescription = "expansion " + expansionLabel + ", rarity " + rarityLabel;
            logger.debug("Scraping {} (idRarity={})", sliceDescription, rarityId);

            PaginatedScrapeResult rarityResult = scrapePaginated(
                    expansionAndRarityUrl, maOuicheList, maxPrice, ouicheCountMap, seller, writer, bridge,
                    sliceDescription);
            collected.addAll(rarityResult.entries);

            if (rarityResult.stillTooManyResults) {
                logger.warn("{} is STILL 300+ results even narrowed by rarity \u2014 no further filter "
                                + "available. Only its first page is linked in the output file; this "
                                + "slice was not fully paginated.",
                        sliceDescription);
                writeUnresolvedOverflowSummaryLine(writer, sliceDescription, expansionAndRarityUrl);
            }
        }

        return collected;
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
     * Fetches {@code filterUrl}'s first page, and if it's still showing CardMarket's "300+
     * results" banner, stops right there and reports {@code stillTooManyResults = true} without
     * fetching any further pages — pagination past page 1 only makes sense once a caller has
     * applied enough filters to bring a result set under 300 and CardMarket actually sorts and
     * paginates it normally; paginating a still-unsorted 300+ set would mean fetching a
     * potentially huge, arbitrarily-ordered number of pages for the same slice a caller is
     * about to re-fetch anyway under a narrower filter.
     * <p>
     * Otherwise, pages through {@code filterUrl} with {@code &site=N}, stopping once CardMarket's
     * own "Page X of Y" label (read from the first page, see {@link #extractTotalPageCount})
     * says there's nothing left, falling back to fetch-until-empty when that label isn't found —
     * the same pagination behavior {@link #scrapeSimplePagination} implements for the site-wide
     * unfiltered case, extracted here so {@link #scrapeByExpansion} and
     * {@link #scrapeExpansionByRarity} share one implementation instead of three copies of the
     * same loop.
     *
     * @param descriptionForLogging short human-readable label for this slice (e.g. "expansion
     *                              Foo" or "expansion Foo, rarity Bar"), used only in log/error
     *                              messages so they identify which slice a failure came from.
     */
    private static PaginatedScrapeResult scrapePaginated(
            String filterUrl, List<CardElement> maOuicheList, double maxPrice,
            Map<String, Integer> ouicheCountMap, CardMarketSeller seller, BufferedWriter writer,
            PythonCardMarketBridge bridge, String descriptionForLogging) {

        List<Entry> collected = new ArrayList<>();
        int pageNumber = 1;
        Document pageDocument;

        politeDelay();
        try {
            pageDocument = fetchPage(filterUrl, bridge);
        } catch (WebDriverException webDriverException) {
            logFetchFailure(writer, seller, descriptionForLogging + " page 1", webDriverException);
            return new PaginatedScrapeResult(collected, false);
        }

        if (pageDocument.text().contains(TOO_MANY_RESULTS_MARKER)) {
            return new PaginatedScrapeResult(collected, true);
        }

        Optional<Integer> totalPages = Optional.empty();
        while (true) {
            if (isEmptyResultsPage(pageDocument)) {
                break;
            }
            if (!pageHasOfferRows(pageDocument)) {
                dumpUnexpectedPage(writer, seller, descriptionForLogging + " page " + pageNumber, pageDocument);
                break;
            }
            if (pageNumber == 1) {
                totalPages = extractTotalPageCount(pageDocument);
            }
            String currentPageUrl = pageNumber == 1 ? filterUrl : filterUrl + "&site=" + pageNumber;
            List<Entry> pageEntries = parseOfferRows(pageDocument, maOuicheList, maxPrice, ouicheCountMap,
                    currentPageUrl);
            collected.addAll(pageEntries);

            if (totalPages.isPresent() && pageNumber >= totalPages.get()) {
                break; // Just parsed the last page per CardMarket's own count — no next-page fetch needed.
            }

            pageNumber++;
            String pageUrl = filterUrl + "&site=" + pageNumber;
            politeDelay();
            try {
                pageDocument = fetchPage(pageUrl, bridge);
            } catch (WebDriverException webDriverException) {
                logFetchFailure(writer, seller, descriptionForLogging + " page " + pageNumber, webDriverException);
                break;
            }
        }

        return new PaginatedScrapeResult(collected, false);
    }

    /**
     * Writes one line to the output file for a result slice this scraper couldn't fully
     * enumerate (still 300+ results even after every available filter — see
     * {@link #scrapeExpansionByRarity}), naming the slice and linking only its own first page
     * rather than the dozens of individual card links a fully-paginated slice would otherwise
     * get. IO failures here are logged and swallowed rather than propagated, matching how
     * {@link #logFetchFailure} already treats output-file writes elsewhere in this class as
     * best-effort: losing this one summary line isn't worth aborting an otherwise-successful
     * scrape over.
     */
    private static void writeUnresolvedOverflowSummaryLine(
            BufferedWriter writer, String sliceDescription, String firstPageUrl) {
        try {
            writer.write("STILL 300+ RESULTS (not fully scraped) \u2014 " + sliceDescription
                    + " \u2014 first page: " + firstPageUrl + "\n");
        } catch (IOException ignored) {
            // Best-effort logging only; nothing more useful to do if the writer itself fails.
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
        return needsManualFallback(document) ? resolveBridgeManualFallback(bridge, url, document) : document;
    }

    /**
     * The bridge-path counterpart of {@link #resolveWithManualFallback}. No equivalent of that
     * method's first step (making an already-open window visible) is needed here: the sidecar's
     * browser is visible for the entire run, not just when something needs solving — see
     * {@code cardmarket_bridge.py}'s own module docstring.
     * <p>
     * Branches on which of the two states {@code document} is actually in, since they call for
     * different Retry behavior:
     * <ul>
     *   <li>A resolvable challenge (checkbox / wait screen) goes through
     *       {@link #resolveBridgeChallengeRetry}, whose Retry just re-reads the same already-open
     *       page — solving the checkbox in the visible window is exactly what should make that
     *       re-read see real content next.</li>
     *   <li>Cloudflare's hard "Attention Required" block goes through
     *       {@link #resolveBridgeHardBlockRetry}, whose Retry restarts the whole browser session
     *       first — a hard block has nothing to click or wait out in the same session, so
     *       re-reading (or even re-navigating) it again would just see the same block. See that
     *       method for why a full session restart, not just a fresh URL, is what Retry does here.</li>
     * </ul>
     * A page can be stuck on the resolvable challenge for so long that the sidecar's own settle
     * deadline (25s) runs out and it reports {@code "status": "blocked"} without the page ever
     * actually reaching Cloudflare's hard-block title. That case is still routed to
     * {@link #resolveBridgeChallengeRetry}, not here — {@link #isBlockedByCloudflare} checks the
     * page's own title, not the sidecar's status string, so a timed-out-but-still-just-a-challenge
     * page correctly falls through to the challenge Retry path (re-poll, no session restart)
     * rather than being treated as a hard block it never actually reached.
     */
    private static Document resolveBridgeManualFallback(
            PythonCardMarketBridge bridge, String url, Document document)
            throws PythonCardMarketBridge.PythonBridgeException {
        return isBlockedByCloudflare(document)
                ? resolveBridgeHardBlockRetry(bridge, url)
                : resolveBridgeChallengeRetry(bridge, url);
    }

    /**
     * Retry behavior for Cloudflare's ordinary resolvable challenge (checkbox / wait screen)
     * on the bridge path: re-reads whatever is already loaded in the sidecar's browser via
     * {@link PythonCardMarketBridge#checkCurrentPage()}, without navigating anywhere.
     * <p>
     * This used to send a fresh {@code "fetch"} instead — a full re-navigation via UC Mode's
     * disconnect/reconnect. That was a real bug: solving the checkbox by hand in the visible
     * window already leaves the browser sitting on (or about to show) real content, and
     * re-navigating discards that progress and asks Cloudflare fresh again — which is what
     * made the browser visibly restart mid-solve and could tip Cloudflare into its harder
     * block on the very next attempt. {@link PythonCardMarketBridge#checkCurrentPage()} matches
     * what the direct-Selenium path's own Retry already does
     * ({@link #waitForRecognizedContentOnly}: re-poll the live driver, no fresh {@code driver.get}).
     */
    private static Document resolveBridgeChallengeRetry(PythonCardMarketBridge bridge, String url)
            throws PythonCardMarketBridge.PythonBridgeException {
        while (true) {
            if (promptManualChallengeChoice() == ManualChallengeChoice.STOP) {
                throw new ManualChallengeStoppedException(
                        "Scraping stopped: Cloudflare needed manual verification and the user "
                                + "chose to stop instead of retrying.");
            }
            Document retriedDocument = bridge.checkCurrentPage();
            if (isBlockedByCloudflare(retriedDocument)) {
                // Escalated to the hard block while we were checking — hand off instead of
                // looping here forever on a challenge Retry that can no longer succeed.
                logger.debug("Escalated to Cloudflare's hard block while resolving the challenge; "
                        + "switching to the hard-block Retry flow.");
                return resolveBridgeHardBlockRetry(bridge, url);
            }
            if (!needsManualFallback(retriedDocument)) {
                logger.debug("Recognized content appeared after manual solving; continuing the scrape.");
                return retriedDocument;
            }
            logger.debug("Still on Cloudflare's challenge page after Retry; asking again.");
        }
    }

    /**
     * Retry behavior for Cloudflare's hard "Attention Required" block on the bridge path.
     * Unlike the resolvable-challenge case, there's nothing to click or wait out in the same
     * session — a hard block is CardMarket/Cloudflare's decision about this specific browser
     * session (profile, cookies, fingerprint, request history), not a puzzle on the current
     * page. Re-reading or even re-navigating the same session would almost certainly just see
     * the same block again.
     * <p>
     * So Retry here calls {@link PythonCardMarketBridge#restartSession()} first — closing the
     * flagged browser and opening a brand-new one in the same sidecar process — then re-fetches
     * {@code url} as if this were the very first request of a fresh run. This does mean losing
     * whatever warm-up/cookies the old session had built up, and paying a full Chrome relaunch;
     * that cost is only worth it because the alternative (retrying inside the same flagged
     * session) has no realistic chance of succeeding at all.
     * <p>
     * A restart doesn't guarantee the fresh session won't also get blocked — if the block is
     * IP-based rather than session-based, restarting the browser won't help and the same prompt
     * will simply reappear. That's a real possibility this can't distinguish from a session-only
     * block, and Stop remains the right choice if repeated restarts keep landing back here.
     */
    private static Document resolveBridgeHardBlockRetry(PythonCardMarketBridge bridge, String url)
            throws PythonCardMarketBridge.PythonBridgeException {
        while (true) {
            if (promptManualHardBlockChoice() == ManualChallengeChoice.STOP) {
                throw new ManualChallengeStoppedException(
                        "Scraping stopped: Cloudflare's hard block page was shown and the user "
                                + "chose to stop instead of retrying with a fresh session.");
            }
            logger.debug("Restarting the bridge's browser session after a hard Cloudflare block...");
            bridge.restartSession();
            Document retriedDocument = bridge.fetchPage(url);
            if (!needsManualFallback(retriedDocument)) {
                logger.debug("Recognized content appeared after restarting the session; continuing the scrape.");
                return retriedDocument;
            }
            logger.debug("Still blocked (or challenged) after restarting the session; asking again.");
        }
    }

    /**
     * Asks the person to Retry or Stop, always via the real {@link Alert} dialog if at all
     * possible — including when called from a background thread with no JavaFX toolkit
     * running yet, like the live diagnostic test's plain JUnit run, where there's no console
     * to type into (an IDE's test runner doesn't attach an interactive stdin the way running
     * a plain {@code main()} does). {@link #promptManualChoiceViaConsole} only runs as a last
     * resort if the toolkit genuinely can't start (e.g. no display available).
     * <p>
     * Shared by both the resolvable-challenge prompt and the hard-block prompt — only the
     * dialog's text differs between them (see {@link #promptManualChallengeChoice()} and
     * {@link #promptManualHardBlockChoice()}), since what Retry actually does once chosen is
     * decided by the caller, not this method.
     */
    private static ManualChallengeChoice promptManualChoice(String title, String headerText, String contentText) {
        if (!ensureJavaFxToolkitAvailable()) {
            return promptManualChoiceViaConsole(contentText);
        }
        if (Platform.isFxApplicationThread()) {
            return showManualChallengeDialog(title, headerText, contentText);
        }
        // Not the FX thread (e.g. a plain JUnit test's main thread): schedule the dialog
        // onto the FX thread and block this thread until it's actually been answered.
        AtomicReference<ManualChallengeChoice> choiceHolder = new AtomicReference<>();
        CountDownLatch dialogClosedLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            choiceHolder.set(showManualChallengeDialog(title, headerText, contentText));
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

    private static ManualChallengeChoice promptManualChallengeChoice() {
        return promptManualChoice(CHALLENGE_PROMPT_TITLE, CHALLENGE_PROMPT_HEADER, CHALLENGE_PROMPT_CONTENT);
    }

    /**
     * The hard-block counterpart of {@link #promptManualChallengeChoice()} — same dialog
     * mechanics, different text, and its Retry means something different to the caller
     * ({@link #resolveBridgeHardBlockRetry}: restart the whole browser session, not just
     * re-read the current page).
     */
    private static ManualChallengeChoice promptManualHardBlockChoice() {
        return promptManualChoice(HARD_BLOCK_PROMPT_TITLE, HARD_BLOCK_PROMPT_HEADER, HARD_BLOCK_PROMPT_CONTENT);
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

    private static ManualChallengeChoice showManualChallengeDialog(
            String title, String headerText, String contentText) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(headerText);
        alert.setContentText(contentText);

        ButtonType retryButtonType = new ButtonType("Retry");
        ButtonType stopButtonType = new ButtonType("Stop");
        alert.getButtonTypes().setAll(retryButtonType, stopButtonType);

        Optional<ButtonType> result = alert.showAndWait();
        return (result.isPresent() && result.get() == retryButtonType)
                ? ManualChallengeChoice.RETRY
                : ManualChallengeChoice.STOP;
    }

    private static ManualChallengeChoice promptManualChoiceViaConsole(String contentText) {
        logger.warn("{} Then type \"retry\" and press Enter here, or type \"stop\" to give up.", contentText);
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

    /**
     * Reads the total page count from CardMarket's own "Page X of Y" label, or empty if that
     * label isn't present, doesn't parse, or is the open-ended "Page X of Y+" form CardMarket
     * shows for its 300+-results view (see {@link #PAGE_COUNT_PATTERN}'s own doc for why that
     * specific case can't be trusted as an exact count). Callers fall back to their existing
     * fetch-and-check-if-empty loop in any of these cases (see {@link #scrapeSimplePagination}
     * and {@link #scrapeByExpansion}), so an unrecognized or open-ended label degrades to the
     * previous (slightly less efficient, but correct) behavior instead of truncating results.
     * <p>
     * Reading this up front lets both pagination loops stop as soon as they've fetched the
     * last real page, instead of always fetching one extra page past the end just to confirm
     * it's empty — for a filtered set with N pages, that's N fetches instead of N+1, which adds
     * up over hundreds of expansions in a full run.
     */
    static Optional<Integer> extractTotalPageCount(Document doc) { // package-private for tests
        Matcher matcher = PAGE_COUNT_PATTERN.matcher(doc.text());
        if (!matcher.find()) {
            return Optional.empty();
        }
        if (matcher.group(2) != null) {
            // Open-ended "Page X of Y+" (seen on 300+-results pages) — Y is a floor, not the
            // real count. Must not be treated as "stop after page Y"; see this field's own
            // Javadoc on PAGE_COUNT_PATTERN for why.
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(matcher.group(1)));
        } catch (NumberFormatException numberFormatException) {
            logger.debug("Found a \"Page X of Y\" label but couldn't parse the page count out of it "
                    + "(non-fatal, falling back to fetch-until-empty): {}", numberFormatException.getMessage());
            return Optional.empty();
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
        return extractFilterOptionMap(doc, "expansionOptions", "expansion");
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
     * Reads the seller's rarity filter (label, idRarity) the same way {@link #extractExpansionMap}
     * reads expansions — same JSON blob, {@code options.rarityOptions} instead of
     * {@code options.expansionOptions}. Confirmed present live alongside expansionOptions in the
     * same {@code data-props} attribute (both come from CardMarket's own
     * {@code CategoryOffersFilterComponent}, not a separate widget or request).
     * <p>
     * Unlike expansions, rarity option labels here have no trailing offer count to strip — e.g.
     * "Ultra Rare", not "Ultra Rare (123)" — since CardMarket only shows per-rarity counts once
     * an expansion is already selected, and this map is read from the seller's unfiltered (or
     * expansion-only-filtered) page. {@link #scrapeByExpansion} only reaches for this map at all
     * when a single expansion is itself still 300+ results (see the class-level doc comment),
     * to narrow that one expansion further; it isn't used as a first-level split the way
     * expansions are, since almost every expansion needs no rarity narrowing at all.
     */
    static Map<String, String> extractRarityMap(Document doc) { // package-private for tests
        return extractFilterOptionMap(doc, "rarityOptions", "rarity");
    }

    /**
     * Shared reader behind {@link #extractExpansionMap} and {@link #extractRarityMap}: both
     * pull a {@code label}/{@code value} option list out of the same
     * {@code CategoryOffersFilterComponent} JSON blob, just under a different key
     * ({@code optionsArrayKey}) and for a different filter dimension ({@code filterNameForLogging},
     * used only in log messages). The pseudo "All" entry ({@code value == "0"}) is always
     * skipped — for expansions this scraper wants each real expansion individually, and for
     * rarities the "All" entry is exactly the unfiltered request that got this page's own
     * caller into "still 300+" trouble in the first place.
     */
    private static Map<String, String> extractFilterOptionMap(
            Document doc, String optionsArrayKey, String filterNameForLogging) {
        Map<String, String> optionMap = new LinkedHashMap<>();

        Element filterComponent = doc.selectFirst("div[data-component-name=CategoryOffersFilterComponent]");
        if (filterComponent == null) {
            logger.warn("Could not find CardMarket's {} filter component on the page.", filterNameForLogging);
            return optionMap;
        }
        String dataProps = filterComponent.attr("data-props");
        if (dataProps.isEmpty()) {
            logger.warn("CardMarket's {} filter component had no data-props attribute.", filterNameForLogging);
            return optionMap;
        }

        try {
            JSONObject propsJson = new JSONObject(dataProps);
            JSONArray options = propsJson.getJSONObject("options").getJSONArray(optionsArrayKey);
            for (int index = 0; index < options.length(); index++) {
                JSONObject option = options.getJSONObject(index);
                String label = option.getString("label");
                String value = String.valueOf(option.get("value"));
                if ("0".equals(value)) {
                    continue; // the pseudo "All" entry
                }
                optionMap.put(label, value);
            }
        } catch (Exception exception) {
            logger.error("Failed to parse CardMarket's {} filter JSON.", filterNameForLogging, exception);
        }

        return optionMap;
    }

    /**
     * Holds what {@link #scrapePaginated} found for one filtered URL: every matched entry
     * collected across all its pages, and whether the very first page was still showing
     * CardMarket's "300+ results" banner — meaning this URL's own filters weren't narrow
     * enough to bring the seller's sort-and-paginate view under CardMarket's 300-result cap,
     * and pagination past page 1 was skipped rather than blindly followed. Callers use this
     * flag to decide whether to apply another layer of filtering ({@link #scrapeByExpansion}
     * reacting to the site-wide result; {@link #scrapeExpansionByRarity} reacting to a single
     * expansion's result) or, if no further filter is available, to fall back to
     * {@link #writeUnresolvedOverflowSummaryLine} instead of paginating a possibly very large,
     * unsorted result set.
     */
    private static final class PaginatedScrapeResult {
        final List<Entry> entries;
        final boolean stillTooManyResults;

        PaginatedScrapeResult(List<Entry> entries, boolean stillTooManyResults) {
            this.entries = entries;
            this.stillTooManyResults = stillTooManyResults;
        }
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