package View;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Unit 9's artwork picker for candidates with multiple artworks — the third region the plan
 * doc's Unit 8 section flagged as not fitting the header-row strip alongside the preview and
 * printCode columns. Rendered into the active tab's {@code rightContentPane} instead (the real
 * scrollable card grid described in the plan doc's "Grounding" section, not the dead
 * {@code AllExistingCardsPane} class), as a sibling of that pane's existing content rather than
 * replacing it — {@code CardScannerCoordinator} toggles this gallery's visibility against the
 * tab's normal card-grid content instead of tearing either down, so closing the scanner never
 * needs to reconstruct whatever the tab was already showing.
 *
 * <p>Lays out artwork images as wrapping rows (fill left to right, wrap once a row runs out of
 * width) via {@link FlowPane}, at the same size as the card images already used in that grid
 * ({@link CardImageLoader}, sized from whichever {@code cardWidthProperty}/
 * {@code cardHeightProperty} the caller passes in — see {@code RealMainController}'s shared
 * 100x146 defaults). This class only renders images and reports clicks; it has no opinion on
 * what a click should do — that's {@link CardScannerPane#onArtworkSelected(String, boolean)}'s
 * job, via the {@code onArtworkClicked} callback passed to {@link #showArtworkOptions}, which
 * also reports whether the click was CTRL-held so that decision can tell a select-only click
 * from an ordinary one.
 */
public class CardScannerArtworkGallery extends VBox {

    private static final String ARTWORK_ID_KEY = "artworkId";

    private final FlowPane artworkFlowPane;
    private final ScrollPane scrollPane;
    private final CardImageLoader imageLoader;

    private StackPane selectedTile;

    public CardScannerArtworkGallery(
            DoubleProperty cardWidthProperty,
            DoubleProperty cardHeightProperty) {
        this.imageLoader = new CardImageLoader(cardWidthProperty, cardHeightProperty);

        artworkFlowPane = new FlowPane();
        artworkFlowPane.setHgap(8);
        artworkFlowPane.setVgap(8);
        artworkFlowPane.setPadding(new Insets(8));
        artworkFlowPane.setStyle("-fx-background-color: #121216;");

        scrollPane = new ScrollPane(artworkFlowPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #121216; -fx-background-color: #121216;");

        this.getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        this.setVisible(false);
        this.setManaged(false);
    }

    /**
     * Renders one image tile per artwork option, replacing whatever was shown before, and clears
     * any prior selection styling. Passing an empty or {@code null} list is equivalent to calling
     * {@link #clearArtworkOptions()}.
     *
     * @param artworkOptions   the artwork variants to render, each paired with the opaque
     *                         identifier {@link CardScannerPane#onArtworkSelected(String, boolean)}
     *                         should be called with when it's clicked
     * @param onArtworkClicked invoked with an option's identifier and whether CTRL was held when
     *                         its tile is clicked
     */
    public void showArtworkOptions(
            List<ArtworkOption> artworkOptions, BiConsumer<String, Boolean> onArtworkClicked) {
        clearArtworkOptions();
        if (artworkOptions == null || artworkOptions.isEmpty()) {
            return;
        }
        for (ArtworkOption option : artworkOptions) {
            artworkFlowPane.getChildren().add(buildArtworkTile(option, onArtworkClicked));
        }
        this.setVisible(true);
        this.setManaged(true);
    }

    /**
     * Removes every artwork tile and hides this gallery, so the tab's normal card-grid content
     * (its sibling in {@code rightContentPane}) reclaims the full pane again.
     */
    public void clearArtworkOptions() {
        artworkFlowPane.getChildren().clear();
        selectedTile = null;
        this.setVisible(false);
        this.setManaged(false);
    }

    /**
     * Restyles whichever tile matches {@code artworkId} as selected, and un-styles the
     * previously-selected tile if there was one. Called by {@code CardScannerCoordinator} after
     * a printCode button click leaves an artwork already selected from a prior render, so the
     * gallery visually reflects {@link CardScannerPane#getSelectedArtworkId()} without needing to
     * be rebuilt.
     *
     * @param artworkId the identifier to mark selected, or {@code null} to just clear the current
     *                  selection styling without selecting a new tile
     */
    public void setSelectedArtwork(String artworkId) {
        if (selectedTile != null) {
            applyTileSelectionStyle(selectedTile, false);
            selectedTile = null;
        }
        if (artworkId == null) {
            return;
        }
        for (Node node : artworkFlowPane.getChildren()) {
            if (node instanceof StackPane tile && artworkId.equals(tile.getProperties().get(ARTWORK_ID_KEY))) {
                applyTileSelectionStyle(tile, true);
                selectedTile = tile;
                return;
            }
        }
    }

    private StackPane buildArtworkTile(ArtworkOption option, BiConsumer<String, Boolean> onArtworkClicked) {
        ImageView imageView = new ImageView();
        imageView.setFitWidth(100);
        imageView.setFitHeight(146);
        imageView.setPreserveRatio(true);
        imageLoader.loadCardImage(new CardElement(option.card()), imageView);

        StackPane tile = new StackPane(imageView);
        tile.getProperties().put(ARTWORK_ID_KEY, option.artworkId());
        tile.setPadding(new Insets(3));
        applyTileSelectionStyle(tile, false);
        tile.setAlignment(Pos.CENTER);
        tile.setOnMouseClicked(event -> {
            if (onArtworkClicked != null) {
                onArtworkClicked.accept(option.artworkId(), event.isShortcutDown());
            }
        });
        tile.setStyle(tile.getStyle() + "-fx-cursor: hand;");
        return tile;
    }

    private void applyTileSelectionStyle(StackPane tile, boolean selected) {
        tile.setStyle(selected
                ? "-fx-background-color: #121216; -fx-border-color: #cdfc04; -fx-border-width: 3;"
                : "-fx-background-color: #121216; -fx-border-color: #444444; -fx-border-width: 1;");
    }

    /**
     * One artwork variant to render as a tile, as built by {@code CardScannerCoordinator} from a
     * {@code CardCandidates} result.
     *
     * @param card      the artwork-specific {@link Card} whose image this tile shows
     * @param artworkId opaque identifier round-tripped through
     *                  {@link CardScannerPane#onArtworkSelected(String, boolean)} unchanged — this
     *                  class and {@link CardScannerPane} never inspect it, only compare it for
     *                  equality
     */
    public record ArtworkOption(Card card, String artworkId) {
    }
}