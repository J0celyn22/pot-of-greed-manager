package View;

import Model.CardsLists.Card;
import Utils.LruImageCache;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * A simple VBox-based list view of {@link Card} objects showing image,
 * name, and code for each card.
 *
 * @deprecated This class may be superseded by {@link CardsListCell} used inside
 *             a {@link javafx.scene.control.ListView}. Verify usage before
 *             making further changes.
 */
public class CardsListView extends VBox {

    /**
     * Constructs a list view for the given cards.
     *
     * @param cards       the cards to display
     * @param printedMode {@code true} to show print codes, {@code false} for pass codes
     * @param imageWidth  desired card image width in pixels
     * @param imageHeight desired card image height in pixels
     */
    public CardsListView(List<Card> cards, boolean printedMode,
                         double imageWidth, double imageHeight) {
        setSpacing(5);
        setPadding(new Insets(5));
        getStyleClass().add("cards-list-view");

        if (cards.isEmpty()) {
            Label noCardLabel = new Label("No card found");
            noCardLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16;");
            getChildren().add(noCardLabel);
        } else {
            for (Card card : cards) {
                getChildren().add(createCardCell(card, printedMode, imageWidth, imageHeight));
            }
        }

        Platform.runLater(this::styleAncestorScrollPane);
    }

    private HBox createCardCell(Card card, boolean printedMode,
                                double imageWidth, double imageHeight) {
        HBox cell = new HBox(10);
        cell.setPadding(new Insets(5));
        cell.setStyle(
                "-fx-border-color: white; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 5; " +
                        "-fx-background-radius: 5;");

        Image image = LruImageCache.getImage(card.getImagePath());
        if (image == null) {
            image = new Image("file:./src/main/resources/placeholder.jpg");
        }
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(imageWidth);
        imageView.setFitHeight(imageHeight);
        imageView.setPreserveRatio(true);

        VBox infoBox = new VBox(5);

        Label nameLabel = new Label(card.getName_EN());
        nameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14;");
        infoBox.getChildren().add(nameLabel);

        if (printedMode) {
            Label nativeNameLabel = new Label(card.getName_EN());
            nativeNameLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12;");
            infoBox.getChildren().add(nativeNameLabel);
        }

        String codeText = printedMode
                ? "PrintCode: " + card.getPrintCode()
                : "PassCode: " + card.getPassCode();
        Label codeLabel = new Label(codeText);
        codeLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12;");
        infoBox.getChildren().add(codeLabel);

        cell.getChildren().addAll(imageView, infoBox);
        return cell;
    }

    /**
     * Finds the nearest ancestor {@link ScrollPane} and applies transparent
     * background styling to its viewport so it matches the app's dark theme.
     */
    private void styleAncestorScrollPane() {
        try {
            Node current = this;
            while (current != null && !(current instanceof ScrollPane)) {
                current = current.getParent();
            }
            if (current instanceof ScrollPane) {
                ScrollPane scrollPane = (ScrollPane) current;
                scrollPane.getStyleClass().add("all-cards-scroll-pane");
                scrollPane.setStyle(
                        "-fx-background: transparent; -fx-background-color: transparent;");
                Node viewport = scrollPane.lookup(".viewport");
                if (viewport != null) {
                    viewport.setStyle("-fx-background-color: transparent;");
                }
                Platform.runLater(() -> {
                    scrollPane.applyCss();
                    scrollPane.layout();
                });
            }
        } catch (Exception ignored) {
        }
    }
}