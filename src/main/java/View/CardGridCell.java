/*package View;

import Controller.DragDropManager;
import Controller.SelectionManager;
import Model.CardsLists.CardElement;
import Utils.LruImageCache;
import javafx.scene.SnapshotParameters;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

// Inside your CardTreeCell.java (or wherever your middle-part mosaic cells are defined)
public class CardGridCell extends org.controlsfx.control.GridCell<CardElement> {
    private ImageView cardImageView;
    private StackPane wrapper;

    public CardGridCell() {
        cardImageView = new ImageView();
        cardImageView.setPreserveRatio(true);
        // Bind to your zoomed properties.
        cardImageView.fitWidthProperty().bind(cardWidthProperty);
        cardImageView.fitHeightProperty().bind(cardHeightProperty);

        // Wrap the image view in a StackPane so we can apply selection borders.
        wrapper = new StackPane(cardImageView);
        wrapper.setPadding(new Insets(5));
        wrapper.setStyle("-fx-background-color: transparent;");
        // Ensure the wrapper receives mouse events.
        wrapper.setPickOnBounds(true);
        wrapper.setFocusTraversable(true);
        setGraphic(wrapper);

        // SELECTION: Clicking in the middle part always clears previous selection and selects this card.
        wrapper.setOnMouseClicked(event -> {
            if (getItem() == null) return;
            // Always clear and select this card (ignoring CTRL/SHIFT to force exclusive selection).
            SelectionManager.clearSelection();
            SelectionManager.selectCard(getItem().getCard());
            // Refresh the containing ListView to update visual state.
            if (getListView() != null) {
                getListView().refresh();
            }
            event.consume();
        });

        // DRAG HANDLING (unchanged from your working version):
        cardImageView.setOnDragDetected(event -> {
            if (getItem() == null) return;
            Dragboard db = cardImageView.startDragAndDrop(TransferMode.MOVE);
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            WritableImage ghostImage = cardImageView.snapshot(params, null);
            if (ghostImage != null) {
                // Center the ghost image under the mouse.
                db.setDragView(ghostImage, ghostImage.getWidth() / 2, ghostImage.getHeight() / 2);
            }
            ClipboardContent content = new ClipboardContent();
            content.putString(getItem().getCard().getPassCode());
            db.setContent(content);
            DragDropManager.setCurrentlyDraggedCard(getItem().getCard());
            event.consume();
        });
        cardImageView.setOnDragDone(event -> {
            DragDropManager.clearCurrentlyDraggedCard();
            event.consume();
        });

        // (Optional) Allow canceling drag via ESC.
        wrapper.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                DragDropManager.clearCurrentlyDraggedCard();
                event.consume();
            }
        });
    }

    @Override
    protected void updateItem(CardElement item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            cardImageView.setImage(null);
            wrapper.setStyle("-fx-background-color: transparent;");
        } else {
            // Load image synchronously.
            String fullUrl = getImagePath(item.getCard());
            Image image = LruImageCache.getImage(fullUrl);
            if (image == null) {
                image = new Image(fullUrl, cardWidthProperty.get(), cardHeightProperty.get(), true, true);
            }
            cardImageView.setImage(image);

            // Update visual selection styling.
            if (SelectionManager.getSelectedCards().contains(item.getCard())) {
                // For example, add a colored drop-shadow border or a CSS border.
                wrapper.setStyle("-fx-border-color: #cdfc04; -fx-border-width: 3; -fx-border-radius: 8; -fx-background-radius: 8;");
            } else {
                wrapper.setStyle("-fx-background-color: transparent;");
            }
        }
    }

    private String getImagePath(Card card) {
        if (card == null || card.getImagePath() == null)
            return "file:./src/main/resources/placeholder.jpg";
        String imageKey = card.getImagePath();
        String[] addresses = Model.Database.DataBaseUpdate.getAddresses(imageKey + ".jpg");
        if (addresses != null && addresses.length > 0) {
            return "file:" + addresses[0];
        }
        return "file:./src/main/resources/placeholder.jpg";
    }

    private void updateSelectionEffect() {
        if (!isEmpty() && getItem() != null) {
            if (getItem().equals(selectedCardElement.get())) {
                DropShadow borderGlow = new DropShadow();
                borderGlow.setColor(Color.BLUE);
                borderGlow.setOffsetX(0f);
                borderGlow.setOffsetY(0f);
                borderGlow.setWidth(30);
                borderGlow.setHeight(30);
                cardImageView.setEffect(borderGlow);
            } else {
                cardImageView.setEffect(null);
            }
        } else {
            cardImageView.setEffect(null);
        }
    }
}
*/