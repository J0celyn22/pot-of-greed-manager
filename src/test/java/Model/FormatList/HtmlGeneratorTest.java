package Model.FormatList;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Partition-based tests for {@link HtmlGenerator}.
 * <p>
 * This class is 1300+ lines, most of it writer.write(...) calls for list/
 * mosaic HTML pages that are more naturally exercised through the
 * higher-level classes that call them (CardListToHtml, ThemeCollectionToHtml,
 * etc.) with real data. This file focuses on what's independently valuable
 * here: the pure-logic helpers ({@code formatPrice}, {@code computeAdvancement},
 * {@code getPriceCardElement}), the simple writer methods, and the two
 * methods that do real file I/O ({@code createHtmlFile}, {@code addHeader}).
 */
class HtmlGeneratorTest {

    private static String written(WriterAction action) throws IOException {
        StringWriter sw = new StringWriter();
        try (BufferedWriter bw = new BufferedWriter(sw)) {
            action.run(bw);
        }
        return sw.toString();
    }

    private static CardElement ownedCard(String price) {
        Card card = new Card();
        card.setPrice(price);
        return new CardElement(card, false, true, false, false);
    }

    /**
     * Mirrors HtmlGenerator's own {@code String.format("%.2f", ...)} /
     * {@code String.format("%.1f", ...)} calls, which don't pin a Locale and
     * so render with whatever decimal separator the JVM's default locale
     * uses (e.g. "2,50" on a French-locale machine, "2.50" on a US one).
     * Computing expected values the same way keeps these tests passing
     * regardless of locale, rather than hardcoding one separator.
     */
    private static String fmt2(float value) {
        return String.format("%.2f", value);
    }

    private static String fmt1(float value) {
        return String.format("%.1f", value);
    }

    private static CardElement missingCard(String price) {
        Card card = new Card();
        card.setPrice(price);
        return new CardElement(card, false, false, false, false);
    }

    @Test
    void formatPrice_null_returnsZeroFormatted() {
        assertEquals(fmt2(0f), HtmlGenerator.formatPrice(null));
    }

    // ── formatPrice ──────────────────────────────────────────────────────────

    @Test
    void formatPrice_empty_returnsZeroFormatted() {
        assertEquals(fmt2(0f), HtmlGenerator.formatPrice(""));
    }

    @Test
    void formatPrice_validValue_formattedToTwoDecimals() {
        assertEquals(fmt2(4.5f), HtmlGenerator.formatPrice("4.5"));
    }

    @Test
    void formatPrice_unparseable_currentlyReturnsOriginalStringNotZero() {
        // The javadoc says "Returns 0.00 if ... unparseable", but the catch
        // branch actually returns the original, unmodified string. Documenting
        // the real behavior, which contradicts its own docstring.
        assertEquals("not-a-number", HtmlGenerator.formatPrice("not-a-number"));
    }

    @Test
    void computeAdvancement_emptyList_zeroCountsAndFullPercentage() {
        HtmlGenerator.AdvancementStats stats = HtmlGenerator.computeAdvancement(new ArrayList<>());
        assertEquals(0, stats.missingCount);
        assertEquals(0, stats.totalCount);
        assertEquals("100.0", stats.ownedPercentage, "0/0 is defined as 100%, not computed as 0/0");
    }

    // ── computeAdvancement ───────────────────────────────────────────────────

    @Test
    void computeAdvancement_skipsNullElementsAndElementsWithNullCard() {
        List<CardElement> list = new ArrayList<>();
        list.add(null);
        list.add(new CardElement(null, false, false, false, false));
        list.add(ownedCard("1.0"));

        HtmlGenerator.AdvancementStats stats = HtmlGenerator.computeAdvancement(list);
        assertEquals(1, stats.totalCount, "the two unusable entries must not count");
    }

    @Test
    void computeAdvancement_ownedCard_notCountedAsMissing() {
        HtmlGenerator.AdvancementStats stats = HtmlGenerator.computeAdvancement(List.of(ownedCard("1.0")));
        assertEquals(0, stats.missingCount);
        assertEquals(1, stats.totalCount);
    }

    @Test
    void computeAdvancement_ownedSubstandardCard_countedAsMissing() {
        Card card = new Card();
        card.setPrice("1.0");
        CardElement substandard = new CardElement(card, false, false, false, false);
        substandard.setOwnershipStatus(Model.CardsLists.OwnershipStatus.OWNED_SUBSTANDARD);

        HtmlGenerator.AdvancementStats stats = HtmlGenerator.computeAdvancement(List.of(substandard));
        assertEquals(1, stats.missingCount, "OWNED_SUBSTANDARD still needs attention, so it counts as missing");
    }

    @Test
    void computeAdvancement_malformedCardPrice_treatedAsZero_noCrash() {
        HtmlGenerator.AdvancementStats stats = assertDoesNotThrow(() ->
                HtmlGenerator.computeAdvancement(List.of(missingCard("not-a-number"))));
        assertEquals(fmt2(0f), stats.totalPrice);
    }

    @Test
    void computeAdvancement_sumsMissingAndTotalPricesSeparately() {
        List<CardElement> list = List.of(ownedCard("10.0"), missingCard("5.0"));
        HtmlGenerator.AdvancementStats stats = HtmlGenerator.computeAdvancement(list);
        assertEquals(fmt2(5.0f), stats.missingPrice, "only the missing card's price counts here");
        assertEquals(fmt2(15.0f), stats.totalPrice, "both cards count toward the total");
    }

    @Test
    void computeAdvancement_ownedPercentageComputedFromCounts() {
        List<CardElement> list = List.of(ownedCard("1.0"), missingCard("1.0"), missingCard("1.0"), missingCard("1.0"));
        HtmlGenerator.AdvancementStats stats = HtmlGenerator.computeAdvancement(list);
        assertEquals(fmt1(25.0f), stats.ownedPercentage);
    }

    @Test
    void getPriceCardElement_sumsValidPrices() {
        assertEquals(fmt2(6.0f), HtmlGenerator.getPriceCardElement(List.of(ownedCard("2.5"), ownedCard("3.5"))));
    }

    // ── getPriceCardElement ──────────────────────────────────────────────────

    @Test
    void getPriceCardElement_skipsElementWithNullCard() {
        List<CardElement> list = List.of(
                new CardElement(null, false, false, false, false),
                ownedCard("2.5"));
        assertEquals(fmt2(2.5f), HtmlGenerator.getPriceCardElement(list));
    }

    @Test
    void getPriceCardElement_nullElementInList_currentlyThrowsNullPointerException() {
        // Unlike CardsGroup#getPrice, this method doesn't guard against a
        // literal null CardElement in the list -- only a null Card inside one.
        List<CardElement> list = new ArrayList<>();
        list.add(null);
        assertThrows(NullPointerException.class, () -> HtmlGenerator.getPriceCardElement(list));
    }

    @Test
    void addTitle_writesH1WithTitleText() throws IOException {
        assertEquals("<h1>My Deck</h1>\n", written(w -> HtmlGenerator.addTitle(w, "My Deck")));
    }

    // ── Simple writer methods ────────────────────────────────────────────────

    @Test
    void addTitle2_writesH2WithTitleText() throws IOException {
        assertEquals("<h2>My Deck</h2>\n", written(w -> HtmlGenerator.addTitle2(w, "My Deck")));
    }

    @Test
    void addTitle3_writesH3WithTitleText() throws IOException {
        assertEquals("<h3>My Deck</h3>\n", written(w -> HtmlGenerator.addTitle3(w, "My Deck")));
    }

    @Test
    void addTitle_withCountAndPrice_includesFormattedPrice() throws IOException {
        String result = written(w -> HtmlGenerator.addTitle(w, "My Deck", 5, "12.5"));
        assertEquals("<h1>My Deck (5 / " + fmt2(12.5f) + "€)</h1>\n", result);
    }

    @Test
    void addTitle3WithAdvancement_includesAllFiveStats() throws IOException {
        HtmlGenerator.AdvancementStats stats = new HtmlGenerator.AdvancementStats(2, 5, "10.00", "25.00", "60.0");
        String result = written(w -> HtmlGenerator.addTitle3WithAdvancement(w, "My Deck", stats));
        assertEquals("<h3>My Deck (2/5 - 10.00€/25.00€ - 60.0%)</h3>\n", result);
    }

    @Test
    void addListButton_includesFileNameInLinkAndLabel() throws IOException {
        String result = written(w -> HtmlGenerator.addListButton(w, "My Deck"));
        assertTrue(result.contains("href=\"My Deck - List.html\""));
        assertTrue(result.contains(">My Deck - List<"));
    }

    @Test
    void addMosaicButton_includesFileNameInLinkAndLabel() throws IOException {
        String result = written(w -> HtmlGenerator.addMosaicButton(w, "My Deck"));
        assertTrue(result.contains("href=\"My Deck - Mosaic.html\""));
    }

    @Test
    void addCollectionButton_linksToFixedCollectionPage() throws IOException {
        String result = written(HtmlGenerator::addCollectionButton);
        assertTrue(result.contains("href=\"Collection Complete List.html\""));
    }

    @Test
    void addOuicheListButton_linksToFixedOuicheListPage() throws IOException {
        String result = written(HtmlGenerator::addOuicheListButton);
        assertTrue(result.contains("href=\"OuicheList.html\""));
    }

    @Test
    void addOuicheListMosaicButton_linksToFixedMosaicPage() throws IOException {
        String result = written(HtmlGenerator::addOuicheListMosaicButton);
        assertTrue(result.contains("href=\"OuicheList - Mosaic.html\""));
    }

    @Test
    void createHtmlFile_createsParentDirectoriesAndFile(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("nested/dir/page.html");
        HtmlGenerator.createHtmlFile(target.toString());
        assertTrue(Files.exists(target));
    }

    // ── Real file I/O ────────────────────────────────────────────────────────

    @Test
    void createHtmlFile_calledTwice_doesNotFailOrTruncateExistingFile(@TempDir Path tempDir) throws IOException {
        Path target = tempDir.resolve("page.html");
        HtmlGenerator.createHtmlFile(target.toString());
        Files.writeString(target, "existing content");

        assertDoesNotThrow(() -> HtmlGenerator.createHtmlFile(target.toString()));
        assertEquals("existing content", Files.readString(target), "an existing file must not be truncated");
    }

    @Test
    void addHeader_writesTitleAndIconLink_andCreatesIconDirectory(@TempDir Path tempDir) throws IOException {
        // Note on a real inconsistency found while tracing this method (not
        // exercised directly here, since doing so would mean manipulating
        // real files relative to the JVM's working directory rather than an
        // isolated temp dir): the "already copied?" check tests
        // `new File(relativeImagePath + "Icon.png").exists()` -- relative to
        // the process's cwd -- while the actual copy target is
        // `dirPath + relativeImagePath + "Icon.png"`. Those are only the same
        // location when dirPath happens to equal cwd. In any other case
        // (like this test's @TempDir), the check is looking in the wrong
        // place: if cwd ever happened to have a same-named file sitting
        // around, the method would wrongly skip copying into the real
        // per-export target directory, believing it already had one there.
        // The HTML written below is unaffected either way, since the
        // writer.write() call happens unconditionally after the copy step.
        String dirPath = tempDir.toString() + "/";
        String relativeImagePath = "images/";

        String result = written(w -> HtmlGenerator.addHeader(w, "My Page", relativeImagePath, dirPath));

        assertTrue(result.contains("<title>My Page</title>"));
        assertTrue(result.contains("href=\"images/Icon.png\""));
        assertTrue(Files.exists(tempDir.resolve("images")), "the icon directory must have been created");
    }

    @FunctionalInterface
    private interface WriterAction {
        void run(BufferedWriter writer) throws IOException;
    }
}