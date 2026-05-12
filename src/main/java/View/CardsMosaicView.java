package View;

import Model.CardsLists.Card;
import Utils.LruImageCache;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Mosaic view of cards.
 * Cards with a known image are displayed as a plain image.
 * Cards whose image is unavailable show a placeholder with all known
 * identifiers (print code, passcode, Konami ID) overlaid at the bottom.
 */
public class CardsMosaicView extends FlowPane {

    /**
     * Constructs a mosaic view.
     *
     * @param cards      the list of Card objects to display
     * @param cellWidth  the desired width for each card cell
     * @param cellHeight the desired height for each card cell
     */
    public CardsMosaicView(List<Card> cards, double cellWidth, double cellHeight) {
        setHgap(5);
        setVgap(5);
        setPadding(new Insets(5));
        this.getStyleClass().add("cards-mosaic-view");

        for (Card card : cards) {
            getChildren().add(createCardCell(card, cellWidth, cellHeight));
        }

        Platform.runLater(this::styleAncestorScrollPane);
    }

    // ------------------------------------------------------------------
    // Cell builder
    // ------------------------------------------------------------------

    /**
     * Returns all known identifiers for the card as a single string, one per
     * line, in priority order: print code -> passcode -> Konami ID.
     * At least one line is always produced (falls back to "(unknown)").
     */
    private static String buildIdentifierText(Card card) {
        List<String> lines = new ArrayList<>();
        if (card.getPrintCode() != null && !card.getPrintCode().isEmpty())
            lines.add(card.getPrintCode());
        if (card.getPassCode() != null && !card.getPassCode().isEmpty())
            lines.add(card.getPassCode());
        if (card.getKonamiId() != null && !card.getKonamiId().isEmpty())
            lines.add(card.getKonamiId());
        return lines.isEmpty() ? "(unknown)" : String.join("\n", lines);
    }

    /**
     * Builds the visual cell for one card.
     *
     * <p>When the card's image is in the cache a plain {@link ImageView} is
     * returned (no extra wrapper overhead).
     *
     * <p>When the image is unavailable the placeholder is shown inside a
     * {@link StackPane} sized to exactly {@code cellWidth x cellHeight}, with
     * a semi-transparent label overlaid at the bottom that lists every known
     * identifier in priority order (print code first, then passcode, then
     * Konami ID), one per line.
     */
    private Node createCardCell(Card card, double cellWidth, double cellHeight) {
        Image image = LruImageCache.getImage(card.getImagePath());
        boolean usedPlaceholder = (image == null || image.isError());

        if (usedPlaceholder) {
            image = new Image("file:./src/main/resources/placeholder.jpg");
        }

        ImageView iv = new ImageView(image);
        iv.setFitWidth(cellWidth);
        iv.setFitHeight(cellHeight);
        iv.setPreserveRatio(true);

        if (!usedPlaceholder) {
            return iv;
        }

        // Build the identifier text: one line per known identifier.
        String identifierText = buildIdentifierText(card);

        Label label = new Label(identifierText);
        label.setWrapText(true);
        label.setTextAlignment(TextAlignment.CENTER);
        label.setMaxWidth(cellWidth - 8);
        label.setStyle(
                "-fx-text-fill: white; " +
                        "-fx-font-size: 10; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-color: rgba(0,0,0,0.65); " +
                        "-fx-background-radius: 3; " +
                        "-fx-padding: 3 4 3 4;"
        );

        // Fix: give the StackPane an explicit fixed size so the label is
        // positioned relative to the full cell area, not just the rendered
        // (possibly smaller, due to preserveRatio) ImageView bounds.
        StackPane cell = new StackPane(iv, label);
        cell.setMinSize(cellWidth, cellHeight);
        cell.setPrefSize(cellWidth, cellHeight);
        cell.setMaxSize(cellWidth, cellHeight);
        StackPane.setAlignment(label, Pos.BOTTOM_CENTER);
        StackPane.setMargin(label, new Insets(0, 4, 4, 4));
        return cell;
    }

    // ------------------------------------------------------------------
    // ScrollPane styling (applied once after the scene is attached)
    // ------------------------------------------------------------------

    private void styleAncestorScrollPane() {
        try {
            Node current = this;
            while (current != null && !(current instanceof ScrollPane)) {
                current = current.getParent();
            }
            if (current instanceof ScrollPane) {
                applyStylesToScrollPane((ScrollPane) current);
            }
        } catch (Exception ignored) {
            // best-effort -- never crash the UI
        }
    }

    private void applyStylesToScrollPane(ScrollPane sp) {
        if (sp == null) return;
        Platform.runLater(() -> {
            try {
                Set<Node> bars = sp.lookupAll(".scroll-bar");
                bars.addAll(sp.lookupAll(".overlay-scroll-bar"));
                for (Node bar : bars) {
                    bar.setStyle(
                            "-fx-background-color: transparent; " +
                                    "-fx-background-image: null; " +
                                    "-fx-padding: 0;"
                    );
                    Node track = bar.lookup(".track");
                    if (track != null) track.setStyle(
                            "-fx-background-color: #100317; " +
                                    "-fx-background-image: null; " +
                                    "-fx-background-insets: 0; " +
                                    "-fx-background-radius: 4;"
                    );
                    Node thumb = bar.lookup(".thumb");
                    if (thumb != null) thumb.setStyle(
                            "-fx-background-color: #cdfc04; " +
                                    "-fx-background: #cdfc04; " +
                                    "-fx-background-image: null; " +
                                    "-fx-background-insets: 2; " +
                                    "-fx-background-radius: 6; " +
                                    "-fx-pref-width: 10; " +
                                    "-fx-pref-height: 24; " +
                                    "-fx-opacity: 1; " +
                                    "-fx-effect: null;"
                    );
                    Node inc = bar.lookup(".increment-button");
                    if (inc != null) {
                        inc.setStyle("-fx-background-color: #100317; -fx-background-image: null; " +
                                "-fx-padding: 2; -fx-background-radius: 4;");
                        Node arrow = inc.lookup(".increment-arrow");
                        if (arrow == null) arrow = inc.lookup(".arrow");
                        if (arrow != null) arrow.setStyle("-fx-background-color: #cdfc04;");
                    }
                    Node dec = bar.lookup(".decrement-button");
                    if (dec != null) {
                        dec.setStyle("-fx-background-color: #100317; -fx-background-image: null; " +
                                "-fx-padding: 2; -fx-background-radius: 4;");
                        Node arrow = dec.lookup(".decrement-arrow");
                        if (arrow == null) arrow = dec.lookup(".arrow");
                        if (arrow != null) arrow.setStyle("-fx-background-color: #cdfc04;");
                    }
                }
            } catch (Exception ignored) {
                // best-effort
            }
        });
    }
}