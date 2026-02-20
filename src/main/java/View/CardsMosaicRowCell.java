package View;

import Controller.DragDropManager;
import Controller.SelectionManager;
import Model.CardsLists.Card;
import Utils.LruImageCache;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CardsMosaicRowCell extends ListCell<List<Card>> {

    private static final Logger logger = LoggerFactory.getLogger(CardsMosaicRowCell.class);
    private HBox hbox;
    private double imageWidth;
    private double imageHeight;

    public CardsMosaicRowCell(double imageWidth, double imageHeight) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        hbox = new HBox(5);
        hbox.setPadding(new Insets(5));
        hbox.setStyle("-fx-background-color: transparent;");
        // Disable default row selection
        hbox.setPickOnBounds(true);
        setStyle("-fx-background-color: transparent;");
        setGraphic(hbox);
        setFocusTraversable(true);
    }

    public static Callback<ListView<List<Card>>, ListCell<List<Card>>> forListView(double imageWidth, double imageHeight) {
        return listView -> {
            listView.setSelectionModel(null); // disable default row selection
            return new CardsMosaicRowCell(imageWidth, imageHeight);
        };
    }

    @Override
    public void updateSelected(boolean selected) {
        // Overridden to prevent the default ListCell selection visuals.
    }

    /**
     * Attaches an "Add to..." right-click context menu to the given ImageView.
     * The menu is tied to {@code card} so each image in the row gets its own
     * independent menu instance pointing to the correct card.
     *
     * @param wrapper
     * @param card    the Card that this image represents
     */
    private static void attachAddToContextMenu(StackPane wrapper, Model.CardsLists.Card card) {
        if (wrapper == null || card == null) return;

        ContextMenu cm = new ContextMenu();
        cm.setStyle(
                "-fx-background-color: #100317; " +
                        "-fx-background-radius: 6; " +
                        "-fx-border-color: #3a3a3a; " +
                        "-fx-border-radius: 6; " +
                        "-fx-border-width: 1;"
        );

        MenuItem addToItem = new MenuItem();
        Label addToLabel = new Label("Add to...");
        addToLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(addToLabel);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        addToItem.setGraphic(graphic);
        addToItem.setText("");
        // TODO: implement Add to... action
        addToItem.setOnAction(e -> { /* TODO */ });

        cm.getItems().add(addToItem);
        cm.show(wrapper, javafx.geometry.Side.BOTTOM, 0, 0);
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

    private static ContextMenu buildAddToContextMenu(Model.CardsLists.Card card) {
        ContextMenu cm = new ContextMenu();
        cm.setStyle(
                "-fx-background-color: #100317; " +
                        "-fx-background-radius: 6; " +
                        "-fx-border-color: #3a3a3a; " +
                        "-fx-border-radius: 6; " +
                        "-fx-border-width: 1;"
        );

        MenuItem addToItem = new MenuItem();
        Label addToLabel = new Label("Add to...");
        addToLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(addToLabel);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        addToItem.setGraphic(graphic);
        addToItem.setText("");
        addToItem.setOnAction(e -> { /* TODO: implement Add to... for: card */ });

        cm.getItems().add(addToItem);
        return cm;
    }

    @Override
    protected void updateItem(List<Card> row, boolean empty) {
        super.updateItem(row, empty);
        hbox.getChildren().clear();
        if (empty || row == null) {
            setGraphic(null);
        } else {
            for (Card card : row) {
                String fullUrl = getImagePath(card);
                Image image = LruImageCache.getImage(fullUrl);
                if (image == null) {
                    try {
                        image = new Image(fullUrl, imageWidth, imageHeight, true, true);
                    } catch (Exception e) {
                        image = new Image("file:./src/main/resources/placeholder.jpg", imageWidth, imageHeight, true, true);
                    }
                }
                ImageView iv = new ImageView(image);
                iv.setFitWidth(imageWidth);
                iv.setFitHeight(imageHeight);
                iv.setPreserveRatio(true);
                iv.setPickOnBounds(true);
                iv.setFocusTraversable(true);

                // Wrap the ImageView in a StackPane so we can show a selection border.
                StackPane wrapper = new StackPane(iv);
                wrapper.setPadding(new Insets(2));
                // Apply selection style only if the active part is "RIGHT".
                if ("RIGHT".equals(SelectionManager.getActivePart()) &&
                        SelectionManager.getSelectedCards().contains(card)) {
                    wrapper.setStyle("-fx-border-color: #cdfc04; -fx-border-width: 3; -fx-border-radius: 8; -fx-background-radius: 8;");
                } else {
                    wrapper.setStyle("");
                }

                // Build the menu once for this card/wrapper — outside setOnMousePressed
                ContextMenu cardContextMenu = buildAddToContextMenu(card);

                // Click handler for selection. Always passes "RIGHT".
                wrapper.setOnMouseClicked(event -> {
                    if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                        event.consume();
                        return;
                    }
                    event.consume();
                    if (card == null) return;
                    String currentPart = "RIGHT";
                    if (event.isControlDown()) {
                        if (!currentPart.equals(SelectionManager.getActivePart())) {
                            SelectionManager.clearSelection();
                            SelectionManager.selectCard(card, currentPart);
                        } else {
                            if (SelectionManager.getSelectedCards().contains(card)) {
                                SelectionManager.removeFromSelection(card);
                            } else {
                                SelectionManager.addToSelection(card, currentPart);
                            }
                        }
                    } else if (event.isShiftDown()) {
                        if (!currentPart.equals(SelectionManager.getActivePart())) {
                            SelectionManager.clearSelection();
                            SelectionManager.selectCard(card, currentPart);
                        } else {
                            SelectionManager.addToSelection(card, currentPart);
                        }
                    } else {
                        SelectionManager.clearSelection();
                        SelectionManager.selectCard(card, currentPart);
                    }
                    if (getListView() != null) {
                        getListView().refresh();
                    }
                });

                // Drag handlers.
                wrapper.setOnDragDetected(event -> {
                    Dragboard db = wrapper.startDragAndDrop(TransferMode.MOVE);
                    SnapshotParameters params = new SnapshotParameters();
                    params.setFill(Color.TRANSPARENT);
                    WritableImage ghost = wrapper.snapshot(params, null);
                    if (ghost != null) {
                        db.setDragView(ghost, ghost.getWidth() / 2, ghost.getHeight() / 2);
                    }
                    ClipboardContent content = new ClipboardContent();
                    content.putString(card.getPassCode());
                    db.setContent(content);
                    DragDropManager.setCurrentlyDraggedCard(card);
                    logger.debug("Started drag for card in mosaic: {}", card.getName_EN());
                    event.consume();
                });

                wrapper.setOnDragDone(event -> {
                    DragDropManager.clearCurrentlyDraggedCard();
                    logger.debug("Drag done for mosaic card.");
                    event.consume();
                });

                wrapper.setOnMousePressed(event -> {
                    if (event.isSecondaryButtonDown()) {
                        cardContextMenu.show(wrapper, event.getScreenX(), event.getScreenY());
                        event.consume();
                        return;
                    }
                });

                wrapper.setOnMouseReleased(event -> {
                    if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                        event.consume();
                    }
                });

                wrapper.setOnContextMenuRequested(event -> {
                    cardContextMenu.show(wrapper, event.getScreenX(), event.getScreenY());
                    event.consume();
                });

                hbox.getChildren().add(wrapper);
            }
            setGraphic(hbox);
        }
    }
}