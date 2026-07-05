package View;

import Controller.DragDropManager;
import Controller.SelectionManager;
import Model.CardsLists.Card;
import Model.Database.DataBaseUpdate;
import Utils.LruImageCache;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@link ListCell} that renders a single {@link Card} in the right-pane list
 * view. Supports drag-and-drop initiation, multi-selection, and a context menu
 * whose destination items vary by the currently active tab.
 */
public class CardsListCell extends ListCell<Card> {

    private static final Logger logger = LoggerFactory.getLogger(CardsListCell.class);

    private final boolean printedMode;
    private final double imageWidth;
    private final double imageHeight;

    private final ContextMenu myCollectionContextMenu;
    private final ContextMenu decksContextMenu;

    public CardsListCell(boolean printedMode, double imageWidth, double imageHeight) {
        this.printedMode = printedMode;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        setFocusTraversable(true);
        setupDragAndDrop();
        RightPaneRemovalDropTarget.install(this);
        setupSelectionHandler();
        myCollectionContextMenu = buildContextMenuForMyCollection();
        decksContextMenu = buildContextMenuForDecks();
    }

    // ── Drag source ────────────────────────────────────────────────────────────

    private void setupDragAndDrop() {
        setOnDragDetected(event -> {
            Card card = getItem();
            if (card == null) {
                return;
            }

            java.util.Set<Card> selectedCards = SelectionManager.getSelectedCards();
            List<Card> dragCards = new ArrayList<>();
            dragCards.add(card);
            if ("RIGHT".equals(SelectionManager.getActivePart())
                    && selectedCards.size() > 1
                    && selectedCards.contains(card)) {
                for (Card selectedCard : selectedCards) {
                    if (selectedCard != card) {
                        dragCards.add(selectedCard);
                    }
                }
            }

            List<Image> ghostImages = new ArrayList<>();
            int ghostCount = Math.min(dragCards.size(), 5);
            for (int index = 0; index < ghostCount; index++) {
                Card dragCard = dragCards.get(index);
                String imagePath = getImagePath(dragCard);
                Image cachedImage = imagePath != null ? LruImageCache.getImage(imagePath) : null;
                if (cachedImage == null && imagePath != null) {
                    try {
                        cachedImage = new Image(imagePath, imageWidth, imageHeight, true, true);
                    } catch (Exception ignored) {
                    }
                }
                ghostImages.add(cachedImage);
            }

            Dragboard dragboard = startDragAndDrop(TransferMode.MOVE);
            WritableImage ghostImage =
                    DragDropManager.buildDragGhost(ghostImages, imageWidth, imageHeight);
            if (ghostImage != null) {
                dragboard.setDragView(ghostImage, imageWidth / 2.0, imageHeight / 2.0);
            }

            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(card.getPassCode());
            dragboard.setContent(clipboardContent);
            DragDropManager.startRightDrag(dragCards);
            event.consume();
        });

        setOnDragDone(event -> {
            DragDropManager.clearCurrentlyDraggedCard();
            event.consume();
        });

        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                DragDropManager.clearCurrentlyDraggedCard();
                event.consume();
            }
        });
    }

    // ── Cell rendering ─────────────────────────────────────────────────────────

    @Override
    protected void updateItem(Card card, boolean empty) {
        super.updateItem(card, empty);
        if (empty || card == null) {
            setGraphic(null);
            return;
        }

        HBox cell = new HBox(10);
        cell.setPadding(new Insets(5));

        boolean isSelected = "RIGHT".equals(SelectionManager.getActivePart())
                && SelectionManager.getSelectedCards().contains(card);
        String cellStyle = isSelected
                ? "-fx-border-color: #cdfc04; -fx-border-width: 2; -fx-border-radius: 5; "
                + "-fx-background-radius: 5; -fx-background-color: transparent;"
                : "-fx-border-color: white; -fx-border-width: 1; -fx-border-radius: 5; "
                + "-fx-background-radius: 5; -fx-background-color: transparent;";
        cell.setStyle(cellStyle);

        String fullImageUrl = getImagePath(card);
        Image image;
        if (fullImageUrl != null) {
            image = LruImageCache.getImage(fullImageUrl);
            if (image == null) {
                try {
                    image = new Image(fullImageUrl, imageWidth, imageHeight, true, true);
                } catch (Exception exception) {
                    logger.error("Error loading card image from {}", fullImageUrl, exception);
                    image = new Image("file:./src/main/resources/placeholder.jpg",
                            imageWidth, imageHeight, true, true);
                }
            }
        } else {
            image = new Image("file:./src/main/resources/placeholder.jpg",
                    imageWidth, imageHeight, true, true);
        }

        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(imageWidth);
        imageView.setFitHeight(imageHeight);
        imageView.setPreserveRatio(true);

        VBox infoBox = new VBox(5);

        String nameStyle = isSelected
                ? "-fx-text-fill: #cdfc04; -fx-font-size: 14; -fx-font-weight: bold;"
                : "-fx-text-fill: white; -fx-font-size: 14;";
        String codeStyle = isSelected
                ? "-fx-text-fill: #cdfc04; -fx-font-size: 12; -fx-font-weight: bold;"
                : "-fx-text-fill: white; -fx-font-size: 12;";

        Label nameLabel = new Label(card.getName_EN());
        nameLabel.setStyle(nameStyle);
        infoBox.getChildren().add(nameLabel);

        if (printedMode) {
            Label nativeNameLabel = new Label(card.getName_EN());
            nativeNameLabel.setStyle(isSelected
                    ? "-fx-text-fill: #cdfc04; -fx-font-size: 12; -fx-font-weight: bold;"
                    : "-fx-text-fill: white; -fx-font-size: 12;");
            infoBox.getChildren().add(nativeNameLabel);
        }

        Label codeLabel = new Label(printedMode ? card.getPrintCode() : card.getPassCode());
        codeLabel.setStyle(codeStyle);
        infoBox.getChildren().add(codeLabel);

        cell.getChildren().addAll(imageView, infoBox);
        setGraphic(cell);
        setStyle("-fx-background-color: transparent;");

        String currentTabName = ViewUtils.getCurrentTabName(getListView());
        if (currentTabName.equalsIgnoreCase("My Collection")) {
            setContextMenu(myCollectionContextMenu);
        } else if (currentTabName.equalsIgnoreCase("Decks and Collections")
                || currentTabName.equalsIgnoreCase("Decks & Collections")) {
            setContextMenu(decksContextMenu);
        } else {
            setContextMenu(null);
        }
    }

    // ── Context menus ──────────────────────────────────────────────────────────

    private ContextMenu buildContextMenuForMyCollection() {
        ContextMenu contextMenu = ContextMenuItemFactory.styledContextMenu();

        Menu addToMenu = ContextMenuItemFactory.makeMenuHeader("Add to...");
        addToMenu.getItems().add(ContextMenuItemFactory.loadingPlaceholder());
        addToMenu.setOnShowing(event -> {
            addToMenu.getItems().clear();
            Card clickedCard = getItem();
            Collection<Card> cardsToAdd =
                    ContextMenuItemFactory.resolveEffectiveRightPaneCards(clickedCard);
            List<MenuItem> destinationItems =
                    ContextMenuItemFactory.buildMyCollectionDestinationItemsForCards(cardsToAdd);
            if (destinationItems.isEmpty()) {
                MenuItem noDestinations = new MenuItem("No destinations available");
                noDestinations.setDisable(true);
                addToMenu.getItems().add(noDestinations);
            } else {
                addToMenu.getItems().addAll(destinationItems);
            }
        });
        contextMenu.getItems().add(addToMenu);
        contextMenu.getItems().add(ContextMenuItemFactory.buildCopyMenuItem(this::getItem));
        return contextMenu;
    }

    private ContextMenu buildContextMenuForDecks() {
        return ContextMenuItemFactory.buildDecksContextMenu(this::getItem);
    }

    // ── Selection ──────────────────────────────────────────────────────────────

    private void setupSelectionHandler() {
        setOnMouseClicked(event -> {
            if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                event.consume();
                return;
            }
            Card card = getItem();
            if (card == null) {
                return;
            }
            if (event.isControlDown()) {
                SelectionManager.toggleSelection(card, "RIGHT");
            } else if (event.isShiftDown()) {
                List<Card> orderedCardsInList = new ArrayList<>();
                if (getListView() != null && getListView().getItems() != null) {
                    orderedCardsInList.addAll(getListView().getItems());
                }
                SelectionManager.rangeSelect(card, "RIGHT", orderedCardsInList);
            } else {
                SelectionManager.selectCard(card, "RIGHT");
            }
            if (getListView() != null) {
                getListView().refresh();
            }
            event.consume();
        });
    }

    // ── Image path resolution ──────────────────────────────────────────────────

    private String getImagePath(Card card) {
        if (card == null || card.getImagePath() == null) {
            return null;
        }
        String[] addresses = DataBaseUpdate.getAddresses(card.getImagePath() + ".jpg");
        if (addresses != null && addresses.length > 0) {
            return "file:" + addresses[0];
        }
        return null;
    }
}