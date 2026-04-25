package View;

import Model.CardsLists.Card;
import Model.Database.DataBaseUpdate;
import Model.UltraJeux.ShopResultEntry;
import Utils.LruImageCache;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * ShopResultListCell.java
 * <p>
 * A compact list cell for displaying a {@link ShopResultEntry}.
 * Layout mirrors the OuicheList compact-list style:
 * [card image]  [name]
 * [price  ·  occurrence if > 1]
 * [× N wanted]
 * <p>
 * The product URL is surfaced as a Tooltip on the cell so the layout stays clean.
 */
public class ShopResultListCell extends ListCell<ShopResultEntry> {

    private static final double IMG_W = 60.0;
    private static final double IMG_H = 88.0;

    @Override
    protected void updateItem(ShopResultEntry entry, boolean empty) {
        super.updateItem(entry, empty);

        if (empty || entry == null) {
            setGraphic(null);
            setText(null);
            setStyle("-fx-background-color: transparent;");
            setTooltip(null);
            return;
        }

        // ── Root cell row ───────────────────────────────────────────────────────
        HBox cell = new HBox(10);
        cell.setPadding(new Insets(5));
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setStyle(
                "-fx-border-color: white; -fx-border-width: 1; -fx-border-radius: 5; " +
                        "-fx-background-radius: 5; -fx-background-color: transparent;");

        // ── Card image ──────────────────────────────────────────────────────────
        ImageView iv = new ImageView(resolveImage(entry.getCard()));
        iv.setFitWidth(IMG_W);
        iv.setFitHeight(IMG_H);
        iv.setPreserveRatio(true);

        // ── Text column ─────────────────────────────────────────────────────────
        VBox info = new VBox(4);
        info.setAlignment(Pos.CENTER_LEFT);

        // Name: prefer FR, fall back to EN, then to the raw scraped name.
        Card card = entry.getCard();
        String displayName = entry.getName();
        if (card != null) {
            if (card.getName_FR() != null && !card.getName_FR().isBlank()) {
                displayName = card.getName_FR();
            } else if (card.getName_EN() != null && !card.getName_EN().isBlank()) {
                displayName = card.getName_EN();
            }
        }
        Label nameLabel = new Label(displayName);
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(Double.MAX_VALUE);

        // Price row: "0.10€" plus occurrence indicator when there are multiple
        String priceText = String.format(java.util.Locale.US, "%.2f€", entry.getPrice());
        if (entry.getOccurrence() > 1) {
            priceText += "   (copy " + entry.getOccurrence() + ")";
        }
        Label priceLabel = new Label(priceText);
        priceLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 12;");

        // Wanted count
        Label wantedLabel = new Label("× " + entry.getOuicheCount() + " wanted");
        wantedLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 11;");

        info.getChildren().addAll(nameLabel, priceLabel, wantedLabel);
        cell.getChildren().addAll(iv, info);

        setGraphic(cell);
        setText(null);
        setStyle("-fx-background-color: transparent;");

        // Surface the product URL as a tooltip – keeps the row compact
        String url = entry.getProductUrl();
        if (url != null && !url.isBlank()) {
            Tooltip tt = new Tooltip(url);
            tt.setStyle("-fx-font-size: 11; -fx-background-color: #1e0530; -fx-text-fill: #cdfc04;");
            setTooltip(tt);

            // Single click opens the product page in the default browser
            cell.setOnMouseClicked(event -> {
                if (event.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    try {
                        java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                    } catch (Exception ex) {
                        // Fallback: log to console – a dialog would be intrusive here
                        System.err.println("Could not open URL: " + url + " — " + ex.getMessage());
                    }
                    event.consume();
                }
            });
            cell.setStyle(cell.getStyle() + " -fx-cursor: hand;");
        } else {
            setTooltip(null);
            cell.setOnMouseClicked(null);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private Image resolveImage(Card card) {
        if (card != null && card.getImagePath() != null) {
            String[] addresses = DataBaseUpdate.getAddresses(card.getImagePath() + ".jpg");
            if (addresses != null && addresses.length > 0) {
                String url = "file:" + addresses[0];
                Image cached = LruImageCache.getImage(url);
                if (cached != null) return cached;
                try {
                    return new Image(url, IMG_W, IMG_H, true, true);
                } catch (Exception ignored) { /* fall through to placeholder */ }
            }
        }
        return new Image("file:./src/main/resources/placeholder.jpg", IMG_W, IMG_H, true, true);
    }
}