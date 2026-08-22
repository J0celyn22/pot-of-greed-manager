package Controller;

import Model.CardsLists.Card;
import View.CardScannerArtworkGallery;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CardScannerArtworkResolver}, the pure printCode+artwork disambiguation
 * helpers behind Unit 9's camera-scanner artwork gallery.
 *
 * <p>{@link CardScannerCoordinator} and the {@code View} classes it wires together
 * ({@link CardScannerArtworkGallery}, {@code CardScannerPane}) all need a live JavaFX toolkit to
 * construct, matching this project's existing precedent of not unit-testing JavaFX-heavy
 * view/controller classes directly (see {@code Model.CardScanner.ScanLockDebouncerTest} for the
 * same pattern applied to the debounce logic behind Unit 1). {@link CardScannerArtworkResolver}
 * holds no JavaFX state at all, so it's tested here with plain {@link Card} fixtures instead.
 */
class CardScannerArtworkResolverTest {

    // ── Fixture helpers ──────────────────────────────────────────────────────

    private static Card artworkCard(String artNumber) {
        Card card = new Card();
        card.setArtNumber(artNumber);
        return card;
    }

    // ── buildArtworkOptions ──────────────────────────────────────────────────

    @Test
    void buildArtworkOptions_emptyList_returnsEmptyList() {
        List<CardScannerArtworkGallery.ArtworkOption> options =
                CardScannerArtworkResolver.buildArtworkOptions(List.of());

        assertTrue(options.isEmpty());
    }

    @Test
    void buildArtworkOptions_keysEachOptionByItsOwnArtNumber() {
        Card firstArtwork = artworkCard("1");
        Card secondArtwork = artworkCard("2");

        List<CardScannerArtworkGallery.ArtworkOption> options =
                CardScannerArtworkResolver.buildArtworkOptions(List.of(firstArtwork, secondArtwork));

        assertEquals(2, options.size());
        assertEquals("1", options.get(0).artworkId());
        assertSame(firstArtwork, options.get(0).card());
        assertEquals("2", options.get(1).artworkId());
        assertSame(secondArtwork, options.get(1).card());
    }

    @Test
    void buildArtworkOptions_preservesInputOrder() {
        Card thirdArtwork = artworkCard("3");
        Card firstArtwork = artworkCard("1");
        Card secondArtwork = artworkCard("2");

        List<CardScannerArtworkGallery.ArtworkOption> options = CardScannerArtworkResolver.buildArtworkOptions(
                List.of(thirdArtwork, firstArtwork, secondArtwork));

        assertEquals(List.of("3", "1", "2"),
                options.stream().map(CardScannerArtworkGallery.ArtworkOption::artworkId).toList());
    }

    // ── resolveArtworkCard ───────────────────────────────────────────────────

    @Test
    void resolveArtworkCard_matchingArtNumber_returnsThatCard() {
        Card firstArtwork = artworkCard("1");
        Card secondArtwork = artworkCard("2");
        List<Card> artworkOptions = List.of(firstArtwork, secondArtwork);

        assertSame(secondArtwork, CardScannerArtworkResolver.resolveArtworkCard(artworkOptions, "2"));
    }

    @Test
    void resolveArtworkCard_noMatchingArtNumber_returnsNull() {
        List<Card> artworkOptions = List.of(artworkCard("1"), artworkCard("2"));

        assertNull(CardScannerArtworkResolver.resolveArtworkCard(artworkOptions, "3"));
    }

    @Test
    void resolveArtworkCard_nullArtworkId_returnsNull() {
        List<Card> artworkOptions = List.of(artworkCard("1"));

        assertNull(CardScannerArtworkResolver.resolveArtworkCard(artworkOptions, null));
    }

    @Test
    void resolveArtworkCard_emptyOptionsList_returnsNull() {
        assertNull(CardScannerArtworkResolver.resolveArtworkCard(List.of(), "1"));
    }

    @Test
    void resolveArtworkCard_isTheInverseOfBuildArtworkOptions() {
        Card firstArtwork = artworkCard("1");
        Card secondArtwork = artworkCard("2");
        Card thirdArtwork = artworkCard("3");
        List<Card> artworkOptions = List.of(firstArtwork, secondArtwork, thirdArtwork);

        List<CardScannerArtworkGallery.ArtworkOption> built =
                CardScannerArtworkResolver.buildArtworkOptions(artworkOptions);

        for (CardScannerArtworkGallery.ArtworkOption option : built) {
            assertSame(option.card(),
                    CardScannerArtworkResolver.resolveArtworkCard(artworkOptions, option.artworkId()));
        }
    }
}