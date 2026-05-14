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
import javafx.stage.Popup;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Mosaic view of cards.
 * Cards with a known image are displayed as a plain image.
 * Cards whose image is unavailable show a placeholder with all known
 * identifiers (print code, passcode, Konami ID) overlaid at the bottom.
 * Every card shows a hover popup with all available identifiers and names.
 */
public class CardsMosaicView extends FlowPane {

    // Single shared popup for the whole view — shown/hidden per-cell via
    // direct mouse event handlers on each StackPane cell.
    // Using a raw Popup (not Tooltip) guarantees it works on any Node inside
    // any container, regardless of CSS skin availability.
    private final Popup hoverPopup = new Popup();
    private final Label hoverLabel = new Label();

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

        initHoverPopup();

        for (Card card : cards) {
            getChildren().add(createCardCell(card, cellWidth, cellHeight));
        }

        Platform.runLater(this::styleAncestorScrollPane);
    }

    // ------------------------------------------------------------------
    // Hover popup
    // ------------------------------------------------------------------

    /**
     * Identifier text for the placeholder overlay (print code / passcode /
     * Konami ID only, one per line).
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

    // ------------------------------------------------------------------
    // Cell builder
    // ------------------------------------------------------------------

    /**
     * Full tooltip text: all known identifiers and names, labeled, one per line.
     */
    private static String buildTooltipText(Card card) {
        StringBuilder sb = new StringBuilder();
        if (card.getPrintCode() != null && !card.getPrintCode().isEmpty())
            sb.append("Print code : ").append(card.getPrintCode()).append("\n");
        if (card.getPassCode() != null && !card.getPassCode().isEmpty())
            sb.append("Passcode   : ").append(card.getPassCode()).append("\n");
        if (card.getKonamiId() != null && !card.getKonamiId().isEmpty())
            sb.append("Konami ID  : ").append(card.getKonamiId()).append("\n");
        if (card.getName_EN() != null && !card.getName_EN().isEmpty())
            sb.append("EN : ").append(card.getName_EN()).append("\n");
        if (card.getName_FR() != null && !card.getName_FR().isEmpty())
            sb.append("FR : ").append(card.getName_FR()).append("\n");
        if (card.getName_JA() != null && !card.getName_JA().isEmpty())
            sb.append("JA : ").append(card.getName_JA()).append("\n");
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n')
            sb.deleteCharAt(sb.length() - 1);
        return sb.length() > 0 ? sb.toString() : "(no data)";
    }

    // ------------------------------------------------------------------
    // Text helpers
    // ------------------------------------------------------------------

    private void initHoverPopup() {
        hoverLabel.setWrapText(true);
        hoverLabel.setMaxWidth(260);
        hoverLabel.setTextAlignment(TextAlignment.LEFT);
        hoverLabel.setStyle(
                "-fx-background-color: #1a0428; " +
                        "-fx-background-radius: 6; " +
                        "-fx-border-color: #cdfc04; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 6; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 12; " +
                        "-fx-padding: 8 10 8 10;"
        );
        hoverPopup.getContent().add(hoverLabel);
        hoverPopup.setAutoFix(true);   // nudges back on-screen near edges
        hoverPopup.setAutoHide(false); // we control hide ourselves
    }

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

        StackPane cell = new StackPane(iv);
        cell.setMinSize(cellWidth, cellHeight);
        cell.setPrefSize(cellWidth, cellHeight);
        cell.setMaxSize(cellWidth, cellHeight);

        if (usedPlaceholder) {
            Label idLabel = new Label(buildIdentifierText(card));
            idLabel.setWrapText(true);
            idLabel.setTextAlignment(TextAlignment.CENTER);
            idLabel.setMaxWidth(cellWidth - 8);
            idLabel.setStyle(
                    "-fx-text-fill: white; " +
                            "-fx-font-size: 10; " +
                            "-fx-font-weight: bold; " +
                            "-fx-background-color: rgba(0,0,0,0.65); " +
                            "-fx-background-radius: 3; " +
                            "-fx-padding: 3 4 3 4;"
            );
            StackPane.setAlignment(idLabel, Pos.BOTTOM_CENTER);
            StackPane.setMargin(idLabel, new Insets(0, 4, 4, 4));
            cell.getChildren().add(idLabel);
        }

        // Hover popup: pre-build the text so the lambda captures a String,
        // not the Card object, avoiding any closure retention issues.
        String tooltipText = buildTooltipText(card);

        cell.setOnMouseEntered(e -> {
            hoverLabel.setText(tooltipText);
            hoverPopup.show(cell, e.getScreenX() + 14, e.getScreenY() + 14);
        });
        cell.setOnMouseMoved(e ->
                hoverPopup.show(cell, e.getScreenX() + 14, e.getScreenY() + 14)
        );
        cell.setOnMouseExited(e -> hoverPopup.hide());

        return cell;
    }

    // ------------------------------------------------------------------
    // ScrollPane styling
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