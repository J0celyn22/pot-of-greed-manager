package Controller;

import Model.CardsLists.Card;
import View.CardScannerArtworkGallery;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure, JavaFX-free helpers behind Unit 9's printCode+artwork disambiguation — pulled out of
 * {@link CardScannerCoordinator} the same way {@code CardQualityService} and
 * {@code Model.CardScanner.ScanLockDebouncer} already separate this project's decision logic
 * from the JavaFX state it operates on. {@link CardScannerCoordinator} itself can't be
 * unit-tested this way, since it directly holds {@code TabPane}/{@code TreeView}/
 * {@code AnchorPane} references that need a live JavaFX toolkit to construct — this class holds
 * neither, so it's testable with plain {@link Card} fixtures and no toolkit at all.
 *
 * <p>{@link #buildArtworkOptions} and {@link #resolveArtworkCard} are inverses of each other:
 * the first assigns each artwork option an identifier (its {@link Card#getArtNumber()}), the
 * second looks a {@link Card} back up by that same identifier once a click reports it. Keeping
 * both here, next to each other, means the identifier scheme only needs to be understood in one
 * place rather than re-derived independently at each end.
 */
final class CardScannerArtworkResolver {

    private CardScannerArtworkResolver() {
    }

    /**
     * Translates a Konami ID's artwork options (as returned by
     * {@code CardTextMatcher.CardCandidates#getArtworkOptions()}) into the view-model list
     * {@link CardScannerArtworkGallery#showArtworkOptions} renders, keying each option by its
     * {@link Card#getArtNumber()} — stable and unique within one Konami ID's artwork list per
     * {@code CardDatabaseManager.getAliasCards}'s own javadoc, and the same identifier
     * {@link #resolveArtworkCard} looks back up once a tile is clicked.
     *
     * @param artworkOptions the artwork variants to translate, in the order they should be
     *                       rendered; never {@code null}
     * @return one {@link CardScannerArtworkGallery.ArtworkOption} per input card, in the same
     * order, backed by the same {@link Card} references
     */
    static List<CardScannerArtworkGallery.ArtworkOption> buildArtworkOptions(List<Card> artworkOptions) {
        List<CardScannerArtworkGallery.ArtworkOption> options = new ArrayList<>();
        for (Card artworkCard : artworkOptions) {
            options.add(new CardScannerArtworkGallery.ArtworkOption(artworkCard, artworkCard.getArtNumber()));
        }
        return options;
    }

    /**
     * Resolves an artwork identifier (an artNumber string, as produced by
     * {@link #buildArtworkOptions}) back to the specific {@link Card} it names among
     * {@code artworkOptions} — the lookup {@link CardScannerCoordinator} performs once an
     * artwork tile click completes a printCode+artwork add, to find the {@link Card} whose
     * printCode gets stamped and inserted.
     *
     * @param artworkOptions the same list {@link #buildArtworkOptions} was called with for this
     *                       candidate set; never {@code null}
     * @param artworkId      the identifier to look up, as reported by a click; may be
     *                       {@code null}, in which case this always returns {@code null}
     * @return the matching {@link Card}, or {@code null} if none of {@code artworkOptions} has a
     * matching {@link Card#getArtNumber()} (including when {@code artworkId} itself is
     * {@code null})
     */
    static Card resolveArtworkCard(List<Card> artworkOptions, String artworkId) {
        if (artworkId == null) {
            return null;
        }
        for (Card candidateArtworkCard : artworkOptions) {
            if (artworkId.equals(candidateArtworkCard.getArtNumber())) {
                return candidateArtworkCard;
            }
        }
        return null;
    }
}