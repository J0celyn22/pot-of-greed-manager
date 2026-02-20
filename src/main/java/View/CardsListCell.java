package View;

import Controller.DragDropManager;
import Controller.SelectionManager;
import Model.CardsLists.Card;
import Model.Database.DataBaseUpdate;
import Utils.LruImageCache;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CardsListCell extends ListCell<Card> {

    private static final Logger logger = LoggerFactory.getLogger(CardsListCell.class);
    private boolean printedMode;
    private double imageWidth;
    private double imageHeight;

    // Context menu built once and reused across cell recycling.
    // getItem() is called at show-time so it always reflects the current card.
    private final ContextMenu addToContextMenu;

    public CardsListCell(boolean printedMode, double imageWidth, double imageHeight) {
        this.printedMode = printedMode;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        setFocusTraversable(true);
        setupDragAndDrop();
        setupSelectionHandler();

        // Build the "Add to..." context menu ─────────────────────────────────
        // The menu is attached to the *cell* so right-clicking anywhere on the
        // row (image, text, or blank space) triggers it, which is the correct
        // behaviour for list mode.
        addToContextMenu = buildAddToContextMenu();
        this.setOnContextMenuRequested(event -> {
            Card card = getItem();
            if (card == null) {
                event.consume();
                return;
            }
            addToContextMenu.show(this, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Context menu builder
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates the "Add to..." context menu used by the AllExistingCards list.
     * Only one item is needed for now; the action is a stub to be wired later.
     */
    private static ContextMenu buildAddToContextMenu() {
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
        // TODO: implement Add to... action (populate submenu / dialog)
        addToItem.setOnAction(e -> { /* TODO */ });

        cm.getItems().add(addToItem);
        return cm;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Existing methods — UNCHANGED below this line
    // ─────────────────────────────────────────────────────────────────────────

    private void setupDragAndDrop() {
        this.setOnDragDetected(event -> {
            Card card = getItem();
            if (card == null) return;
            Dragboard db = startDragAndDrop(TransferMode.MOVE);
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            WritableImage ghostImage = null;
            if (getGraphic() instanceof HBox) {
                HBox cellBox = (HBox) getGraphic();
                if (!cellBox.getChildren().isEmpty() && cellBox.getChildren().get(0) instanceof ImageView) {
                    ImageView iv = (ImageView) cellBox.getChildren().get(0);
                    ghostImage = iv.snapshot(params, null);
                }
            }
            if (ghostImage != null) {
                db.setDragView(ghostImage, ghostImage.getWidth() / 2, ghostImage.getHeight() / 2);
            }
            ClipboardContent content = new ClipboardContent();
            content.putString(card.getPassCode());
            db.setContent(content);
            DragDropManager.setCurrentlyDraggedCard(card);
            event.consume();
        });

        this.setOnDragDone(event -> {
            DragDropManager.clearCurrentlyDraggedCard();
            event.consume();
        });

        this.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                DragDropManager.clearCurrentlyDraggedCard();
                event.consume();
            }
        });
    }

    private void setupSelectionHandler() {
        this.setOnMouseClicked(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                event.consume();
                return;
            }
            Card card = getItem();
            if (card == null) return;
            String currentPart = "RIGHT";
            if (SelectionManager.getActivePart() == null || !SelectionManager.getActivePart().equals(currentPart)) {
                SelectionManager.clearSelection();
            }
            SelectionManager.selectCard(card, currentPart);
            if (getListView() != null) {
                getListView().refresh();
            }
            event.consume();
        });
    }

    @Override
    protected void updateItem(Card card, boolean empty) {
        super.updateItem(card, empty);
        if (empty || card == null) {
            setGraphic(null);
        } else {
            HBox cell = new HBox(10);
            cell.setPadding(new Insets(5));
            String defaultStyle = "-fx-border-color: white; -fx-border-width: 1; -fx-border-radius: 5; "
                    + "-fx-background-radius: 5; -fx-background-color: transparent;";
            if ("RIGHT".equals(SelectionManager.getActivePart()) &&
                    SelectionManager.getSelectedCards().contains(card)) {
                defaultStyle = "-fx-border-color: #cdfc04; -fx-border-width: 2; -fx-border-radius: 5; "
                        + "-fx-background-radius: 5; -fx-background-color: transparent;";
            }
            cell.setStyle(defaultStyle);

            String fullUrl = getImagePath(card);
            logger.debug("CardsListCell: Using image URL: " + fullUrl);
            Image image = null;
            if (fullUrl != null) {
                image = LruImageCache.getImage(fullUrl);
                if (image == null) {
                    try {
                        image = new Image(fullUrl, imageWidth, imageHeight, true, true);
                        logger.debug("CardsListCell: Loaded image: " + image.getUrl());
                    } catch (Exception e) {
                        logger.error("CardsListCell: Error loading image for " + fullUrl, e);
                        image = new Image("file:./src/main/resources/placeholder.jpg", imageWidth, imageHeight, true, true);
                    }
                }
            } else {
                image = new Image("file:./src/main/resources/placeholder.jpg", imageWidth, imageHeight, true, true);
            }
            ImageView iv = new ImageView(image);
            iv.setFitWidth(imageWidth);
            iv.setFitHeight(imageHeight);
            iv.setPreserveRatio(true);

            VBox info = new VBox(5);
            Label nameLabel = new Label(card.getName_EN());
            String nameStyle = "-fx-text-fill: white; -fx-font-size: 14;";
            String codeStyle = "-fx-text-fill: white; -fx-font-size: 12;";
            if ("RIGHT".equals(SelectionManager.getActivePart()) &&
                    SelectionManager.getSelectedCards().contains(card)) {
                nameStyle += " -fx-font-weight: bold; -fx-text-fill: #cdfc04;";
                codeStyle += " -fx-font-weight: bold; -fx-text-fill: #cdfc04;";
            }
            nameLabel.setStyle(nameStyle);
            info.getChildren().add(nameLabel);
            if (printedMode) {
                Label nativeLabel = new Label(card.getName_EN());
                String nativeStyle = "-fx-text-fill: white; -fx-font-size: 12;";
                if ("RIGHT".equals(SelectionManager.getActivePart()) &&
                        SelectionManager.getSelectedCards().contains(card)) {
                    nativeStyle += " -fx-font-weight: bold; -fx-text-fill: #cdfc04;";
                }
                nativeLabel.setStyle(nativeStyle);
                info.getChildren().add(nativeLabel);
            }
            Label codeLabel = new Label(printedMode ? card.getPrintCode() : card.getPassCode());
            codeLabel.setStyle(codeStyle);
            info.getChildren().add(codeLabel);

            cell.getChildren().addAll(iv, info);
            setGraphic(cell);
            setStyle("-fx-background-color: transparent;");
        }
    }

    private String getImagePath(Card card) {
        if (card == null || card.getImagePath() == null) return null;
        String imageKey = card.getImagePath();
        String[] addresses = DataBaseUpdate.getAddresses(imageKey + ".jpg");
        if (addresses != null && addresses.length > 0) {
            return "file:" + addresses[0];
        }
        return null;
    }
}