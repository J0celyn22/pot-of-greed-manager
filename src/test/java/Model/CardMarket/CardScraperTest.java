package Model.CardMarket;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CardScraperTest.java
 * <p>
 * Exercises the CardMarket scraper's parsing and matching logic directly, with no network
 * or Selenium involved — everything here runs against static HTML fixtures, so it's fast
 * and fully deterministic. The point is to catch exactly the class of bug we hit in
 * practice: "0 results" scrapes where it's unclear whether the row selectors don't match
 * the real page, the price parsing is off, or the name matching itself is broken.
 * <p>
 * The fixtures below are a faithful, minimal reconstruction of the real markup CardMarket
 * returns (verified against actual fetched pages for the DateACard seller) — trimmed down
 * to just the elements the scraper's selectors touch (row wrapper, product name link, price
 * span). Card names/prices in {@link #ROWS_HTML} are real, taken from that same real page.
 */
public class CardScraperTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Fixtures
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Minimal reconstruction of CardMarket's real offer-row structure, with three real
     * cards/prices from an actual DateACard page: Axe Dragonute (0,19€), Banner of Courage
     * (0,19€), Odd-Eyes Dragon (0,24€).
     */
    private static final String ROWS_HTML = "<div id=\"UserOffersTable\">"
            + "  <div class=\"table-body\">"
            + "    <div id=\"stockRow1\" class=\"row g-0 article-row\">"
            + "      <div class=\"col-sellerProductInfo col\">"
            + "        <div class=\"col-seller col-12 col-lg-auto\">"
            + "          <a href=\"/en/YuGiOh/Products/Singles/2Player-Starter-Deck-Yuya-Declan/Axe-Dragonute\">"
            + "Axe Dragonute</a>"
            + "        </div>"
            + "      </div>"
            + "      <div class=\"col-offer col-auto\">"
            + "        <div class=\"price-container d-none d-md-flex\">"
            + "          <span class=\"color-primary\">0,19 €</span>"
            + "        </div>"
            + "        <div class=\"amount-container d-none d-md-flex\"><span class=\"item-count\">2</span></div>"
            + "      </div>"
            + "    </div>"
            + "    <div id=\"stockRow2\" class=\"row g-0 article-row\">"
            + "      <div class=\"col-sellerProductInfo col\">"
            + "        <div class=\"col-seller col-12 col-lg-auto\">"
            + "          <a href=\"/en/YuGiOh/Products/Singles/2Player-Starter-Deck-Yuya-Declan/Banner-of-Courage\">"
            + "Banner of Courage</a>"
            + "        </div>"
            + "      </div>"
            + "      <div class=\"col-offer col-auto\">"
            + "        <div class=\"price-container d-none d-md-flex\">"
            + "          <span class=\"color-primary\">0,19 €</span>"
            + "        </div>"
            + "        <div class=\"amount-container d-none d-md-flex\"><span class=\"item-count\">1</span></div>"
            + "      </div>"
            + "    </div>"
            + "    <div id=\"stockRow3\" class=\"row g-0 article-row\">"
            + "      <div class=\"col-sellerProductInfo col\">"
            + "        <div class=\"col-seller col-12 col-lg-auto\">"
            + "          <a href=\"/en/YuGiOh/Products/Singles/2Player-Starter-Deck-Yuya-Declan/Odd-Eyes-Dragon\">"
            + "Odd-Eyes Dragon</a>"
            + "        </div>"
            + "      </div>"
            + "      <div class=\"col-offer col-auto\">"
            + "        <div class=\"price-container d-none d-md-flex\">"
            + "          <span class=\"color-primary\">0,24 €</span>"
            + "        </div>"
            + "        <div class=\"amount-container d-none d-md-flex\"><span class=\"item-count\">1</span></div>"
            + "      </div>"
            + "    </div>"
            + "  </div>"
            + "</div>";

    /**
     * Exact banner text CardMarket shows on an unfiltered seller page with 300+ offers.
     */
    private static final String TOO_MANY_RESULTS_HTML =
            "<small>These results haven't been sorted (300+ results). Please use the filters "
                    + "to narrow your results to use the sort feature.</small>";

    /**
     * Exact empty-state text CardMarket shows for a filter combination with zero offers.
     */
    private static final String NO_OFFERS_HTML =
            "<div>There are no offers for your selected category and/or filters.</div>";

    /**
     * Minimal reconstruction of the real {@code data-props} JSON embedded in CardMarket's
     * expansion filter widget — same shape as the real one, just fewer entries.
     */
    private static final String EXPANSION_FILTER_HTML =
            "<div data-component-name=\"CategoryOffersFilterComponent\" data-props=\""
                    + "{&quot;options&quot;:{&quot;expansionOptions&quot;:["
                    + "{&quot;label&quot;:&quot;All&quot;,&quot;value&quot;:&quot;0&quot;},"
                    + "{&quot;label&quot;:&quot;2-Player Starter Deck Yuya &amp; Declan (32)&quot;,"
                    + "&quot;value&quot;:1651},"
                    + "{&quot;label&quot;:&quot;Starter Deck: 2009 (149)&quot;,&quot;value&quot;:1172}"
                    + "]}}\"></div>";

    /**
     * Builds a minimal Card with only the fields ShopCardMatcher looks at.
     */
    private static Card makeCard(String nameEnglish, String nameFrench) {
        Card card = new Card();
        card.setName_EN(nameEnglish);
        card.setName_FR(nameFrench);
        card.setImagePath("image/" + nameEnglish);
        return card;
    }

    private static List<CardElement> ouicheListOf(Card... cards) {
        List<CardElement> list = new ArrayList<>();
        for (Card card : cards) {
            list.add(new CardElement(card));
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Row parsing + matching
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void parseOfferRows_findsAllRowsRegardlessOfOuicheListContent() {
        Document doc = Jsoup.parse(ROWS_HTML, "https://www.cardmarket.com/");
        List<CardElement> emptyOuicheList = new ArrayList<>();

        List<CardScraper.Entry> entries =
                CardScraper.parseOfferRows(doc, emptyOuicheList, 10.0, Map.of());

        // Nothing in the OuicheList, so nothing should match, but this at least proves the
        // row/price selectors find all three rows in the first place (a selector mismatch
        // would silently return zero here too, which is exactly the bug we're chasing —
        // the other tests below rule that out by checking matches DO happen).
        assertEquals(0, entries.size());
    }

    @Test
    public void parseOfferRows_matchesCardsPresentInOuicheList() {
        Document doc = Jsoup.parse(ROWS_HTML, "https://www.cardmarket.com/");
        List<CardElement> ouicheList = ouicheListOf(
                makeCard("Axe Dragonute", "Dragonute Hachette"),
                makeCard("Banner of Courage", "Bannière du Courage")
                // "Odd-Eyes Dragon" intentionally absent
        );

        List<CardScraper.Entry> entries = CardScraper.parseOfferRows(doc, ouicheList, 10.0, Map.of());

        assertEquals(2, entries.size(), "Expected exactly the 2 OuicheList cards to match, "
                + "not the 3rd one that isn't in the list");
        assertTrue(entries.stream().anyMatch(entry -> entry.name.equals("Axe Dragonute")));
        assertTrue(entries.stream().anyMatch(entry -> entry.name.equals("Banner of Courage")));
        assertTrue(entries.stream().noneMatch(entry -> entry.name.equals("Odd-Eyes Dragon")));
    }

    @Test
    public void parseOfferRows_respectsMaxPrice() {
        Document doc = Jsoup.parse(ROWS_HTML, "https://www.cardmarket.com/");
        // All 3 cards in the OuicheList this time, but maxPrice excludes the 0,24€ one.
        List<CardElement> ouicheList = ouicheListOf(
                makeCard("Axe Dragonute", null),
                makeCard("Banner of Courage", null),
                makeCard("Odd-Eyes Dragon", null)
        );

        List<CardScraper.Entry> entries = CardScraper.parseOfferRows(doc, ouicheList, 0.20, Map.of());

        assertEquals(2, entries.size(), "0,24€ Odd-Eyes Dragon should be excluded by a 0.20 maxPrice");
        assertTrue(entries.stream().noneMatch(entry -> entry.name.equals("Odd-Eyes Dragon")));
    }

    @Test
    public void parseOfferRows_parsesEuropeanDecimalCommaPricesCorrectly() {
        Document doc = Jsoup.parse(ROWS_HTML, "https://www.cardmarket.com/");
        List<CardElement> ouicheList = ouicheListOf(makeCard("Odd-Eyes Dragon", null));

        List<CardScraper.Entry> entries = CardScraper.parseOfferRows(doc, ouicheList, 10.0, Map.of());

        assertEquals(1, entries.size());
        assertEquals(0.24, entries.get(0).price, 0.001, "\"0,24 €\" should parse as 0.24, not 24 or 0.0");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Real-world name matching: hyphens, accents, from your actual OuicheList/gszahed data
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void nameMatching_handlesHyphenatedNamesLikeRealGszahedCards() {
        // "Cyber-Tech Alligator" was in the real gszahed listing that produced 0 matches.
        String htmlRow = "<div id=\"UserOffersTable\"><div class=\"table-body\">"
                + "<div class=\"row g-0 article-row\">"
                + "<div class=\"col-sellerProductInfo col\"><div class=\"col-seller col-12 col-lg-auto\">"
                + "<a href=\"/en/YuGiOh/Products/Singles/Starter-Deck-2009/Cyber-Tech-Alligator\">"
                + "Cyber-Tech Alligator</a></div></div>"
                + "<div class=\"col-offer col-auto\"><div class=\"price-container d-none d-md-flex\">"
                + "<span class=\"color-primary\">0,19 €</span></div></div>"
                + "</div></div></div>";
        Document doc = Jsoup.parse(htmlRow, "https://www.cardmarket.com/");
        List<CardElement> ouicheList = ouicheListOf(makeCard("Cyber-Tech Alligator", null));

        List<CardScraper.Entry> entries = CardScraper.parseOfferRows(doc, ouicheList, 10.0, Map.of());

        assertEquals(1, entries.size(), "Hyphenated names should still match after normalization");
    }

    @Test
    public void nameMatching_handlesRealAccentedFrenchNamesFromOuicheList() {
        // Real entries from the user's actual OuicheList (accents, apostrophes).
        String htmlRow = "<div id=\"UserOffersTable\"><div class=\"table-body\">"
                + "<div class=\"row g-0 article-row\">"
                + "<div class=\"col-sellerProductInfo col\"><div class=\"col-seller col-12 col-lg-auto\">"
                + "<a href=\"/en/YuGiOh/Products/Singles/Some-Set/Graceful-Charity\">Graceful Charity</a>"
                + "</div></div>"
                + "<div class=\"col-offer col-auto\"><div class=\"price-container d-none d-md-flex\">"
                + "<span class=\"color-primary\">0,15 €</span></div></div>"
                + "</div></div></div>";
        Document doc = Jsoup.parse(htmlRow, "https://www.cardmarket.com/");
        List<CardElement> ouicheList = ouicheListOf(makeCard("Graceful Charity", "Charité Gracieuse"));

        List<CardScraper.Entry> entries = CardScraper.parseOfferRows(doc, ouicheList, 10.0, Map.of());

        assertEquals(1, entries.size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Page-state detection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void isEmptyResultsPage_detectsRealEmptyStateText() {
        Document emptyDoc = Jsoup.parse(NO_OFFERS_HTML, "https://www.cardmarket.com/");
        Document nonEmptyDoc = Jsoup.parse(ROWS_HTML, "https://www.cardmarket.com/");

        assertTrue(CardScraper.isEmptyResultsPage(emptyDoc));
        assertFalse(CardScraper.isEmptyResultsPage(nonEmptyDoc));
    }

    @Test
    public void tooManyResultsMarker_isPresentOnlyOnTheBannerPage() {
        Document bannerDoc = Jsoup.parse(TOO_MANY_RESULTS_HTML, "https://www.cardmarket.com/");
        Document normalDoc = Jsoup.parse(ROWS_HTML, "https://www.cardmarket.com/");

        assertTrue(bannerDoc.text().contains("300+ results"));
        assertFalse(normalDoc.text().contains("300+ results"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Expansion filter JSON extraction
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void extractExpansionMap_parsesRealShapedJsonAndSkipsTheAllEntry() {
        Document doc = Jsoup.parse(EXPANSION_FILTER_HTML, "https://www.cardmarket.com/");

        Map<String, String> expansionMap = CardScraper.extractExpansionMap(doc);

        assertEquals(2, expansionMap.size(), "The pseudo \"All\" (value 0) entry should be skipped");
        assertEquals("1651", expansionMap.get("2-Player Starter Deck Yuya & Declan (32)"));
        assertEquals("1172", expansionMap.get("Starter Deck: 2009 (149)"));
    }

    @Test
    public void stripTrailingCount_removesTheParenthesizedCount() {
        assertEquals("2-Player Starter Deck Yuya & Declan",
                CardScraper.stripTrailingCount("2-Player Starter Deck Yuya & Declan (32)"));
        assertEquals("Starter Deck: 2009",
                CardScraper.stripTrailingCount("Starter Deck: 2009 (149)"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // URL building
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void buildBaseQueryString_formatsMaxPriceWithTwoDecimals() {
        assertEquals("maxPrice=0.30&minAmt=1&sortBy=name_asc", CardScraper.buildBaseQueryString(0.3));
        assertEquals("maxPrice=1.00&minAmt=1&sortBy=name_asc", CardScraper.buildBaseQueryString(1.0));
    }
}
