package View;

import Model.CardsLists.CardElement;
import Model.Database.DataBaseUpdate;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Insets;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import org.controlsfx.control.GridCell;

public class CardGridCellWrapper extends GridCell<CardElement> {
    private ImageView cardImageView;
    private DoubleProperty cardWidthProperty;
    private DoubleProperty cardHeightProperty;

    public CardGridCellWrapper(DoubleProperty cardWidthProperty, DoubleProperty cardHeightProperty) {
        this.cardWidthProperty = cardWidthProperty;
        this.cardHeightProperty = cardHeightProperty;
        cardImageView = new ImageView();
        cardImageView.setPreserveRatio(true);
        cardImageView.fitWidthProperty().bind(cardWidthProperty);
        cardImageView.fitHeightProperty().bind(cardHeightProperty);

        StackPane pane = new StackPane(cardImageView);
        pane.setPadding(new Insets(5));
        setGraphic(pane);
    }

    @Override
    protected void updateItem(CardElement cardElement, boolean empty) {
        super.updateItem(cardElement, empty);
        if (empty || cardElement == null) {
            cardImageView.setImage(null);
        } else {
            String imageUrl = getImagePath(cardElement);
            if (imageUrl != null) {
                try {
                    Image image = new Image(imageUrl, cardWidthProperty.get(), cardHeightProperty.get(), true, true);
                    cardImageView.setImage(image);
                } catch (IllegalArgumentException ex) {
                    // Fallback to a placeholder in case of invalid URL.
                    Image placeholder = new Image("file:./src/main/resources/placeholder.jpg",
                            cardWidthProperty.get(), cardHeightProperty.get(), true, true);
                    cardImageView.setImage(placeholder);
                }
            } else {
                // Use the placeholder image if no valid image URL is available.
                Image placeholder = new Image("file:./src/main/resources/placeholder.jpg",
                        cardWidthProperty.get(), cardHeightProperty.get(), true, true);
                cardImageView.setImage(placeholder);
            }
        }
    }

    /**
     * Resolves the image path using a method similar to that used in CardTreeCell.
     */
    private String getImagePath(CardElement cardElement) {
        if (cardElement == null || cardElement.getCard() == null || cardElement.getCard().getImagePath() == null) {
            return null;
        }
        // Get the base image key.
        String imageKey = cardElement.getCard().getImagePath();
        // Retrieve possible addresses using DataBaseUpdate.
        String[] addresses = DataBaseUpdate.getAddresses(imageKey + ".jpg");
        if (addresses != null && addresses.length > 0) {
            return "file:" + addresses[0];
        }
        return null;
    }
}