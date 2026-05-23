package View;

import Controller.CardClipboard;
import Controller.DragDropManager;
import Controller.SelectionManager;
import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.Database.DataBaseUpdate;
import Utils.LruImageCache;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@link ListCell} that renders a single row of {@link Card} images in the
 * right-pane mosaic view. Each card is shown as an image thumbnail with
 * selection, drag-and-drop, and context-menu support.
 */
public class CardsMosaicRowCell extends ListCell<List<Card>> {

    private static final Logger logger = LoggerFactory.getLogger(CardsMosaicRowCell.class);

    private final HBox rowContainer;
    private final double imageWidth;
    private final double imageHeight;

    private final javafx.stage.Popup hoverPopup = new javafx.stage.Popup();
    private final Label hoverLabel = new Label();

    public CardsMosaicRowCell(double imageWidth, double imageHeight) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        rowContainer = new HBox(5);
        rowContainer.setPadding(new Insets(2));
        rowContainer.setStyle("-fx-background-color: transparent;");
        rowContainer.setPickOnBounds(true);
        setStyle("-fx-background-color: transparent;");
        setGraphic(rowContainer);
        setFocusTraversable(true);
        setupDropTarget();

        hoverLabel.setWrapText(true);
        hoverLabel.setMaxWidth(260);
        hoverLabel.setStyle(CardHoverPopup.LABEL_STYLE);
        hoverPopup.getContent().add(hoverLabel);
        hoverPopup.setAutoFix(true);
        hoverPopup.setAutoHide(false);
    }

    /**
     * Static factory for use as a {@link Callback} with a {@link javafx.scene.control.ListView}.
     * Disables the default row selection model so individual card wrappers handle selection.
     */
    public static Callback<javafx.scene.control.ListView<List<Card>>,
            ListCell<List<Card>>> forListView(double imageWidth, double imageHeight) {
        return listView -> {
            listView.setSelectionModel(null);
            return new CardsMosaicRowCell(imageWidth, imageHeight);
        };
    }

    /**
     * Builds the "Add to Decks & Collections" context menu for a single card cell.
     * Items are populated lazily when the menu opens so the current selection state
     * is always reflected.
     */
    private static ContextMenu buildDecksContextMenu(Card card) {
        ContextMenu contextMenu = ContextMenuItemFactory.styledContextMenu();

        Menu addToMenu = ContextMenuItemFactory.makeMenuHeader("Add to...");
        addToMenu.getItems().add(ContextMenuItemFactory.loadingPlaceholder());
        addToMenu.setOnShowing(event -> {
            addToMenu.getItems().clear();
            Collection<Card> cardsToAdd = ContextMenuItemFactory.resolveEffectiveRightPaneCards(card);
            List<MenuItem> destinationItems =
                    ContextMenuItemFactory.buildAllDecksDestinationItemsForCards(cardsToAdd);
            if (destinationItems.isEmpty()) {
                MenuItem noDestinations = new MenuItem("No destinations available");
                noDestinations.setDisable(true);
                addToMenu.getItems().add(noDestinations);
            } else {
                addToMenu.getItems().addAll(destinationItems);
            }
        });
        contextMenu.getItems().add(addToMenu);
        contextMenu.getItems().add(buildCopyMenuItemFor(card));
        return contextMenu;
    }

    // ── Drop target ────────────────────────────────────────────────────────────

    /**
     * Builds the "Add to My Collection" context menu for a single card cell.
     */
    private static ContextMenu buildMyCollectionContextMenu(Card card) {
        ContextMenu contextMenu = ContextMenuItemFactory.styledContextMenu();

        Menu addToMenu = ContextMenuItemFactory.makeMenuHeader("Add to...");
        addToMenu.getItems().add(ContextMenuItemFactory.loadingPlaceholder());
        addToMenu.setOnShowing(event -> {
            addToMenu.getItems().clear();
            Collection<Card> cardsToAdd = ContextMenuItemFactory.resolveEffectiveRightPaneCards(card);
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
        return contextMenu;
    }

    // ── Context menu builders ──────────────────────────────────────────────────

    /**
     * Shared "Copy" menu item for any card cell.
     */
    private static MenuItem buildCopyMenuItemFor(Card card) {
        MenuItem copyMenuItem = new MenuItem();
        Label copyLabel = new Label("Copy");
        copyLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
        HBox copyGraphic = new HBox(copyLabel);
        copyGraphic.setAlignment(Pos.CENTER_LEFT);
        copyGraphic.setPadding(new Insets(2, 6, 2, 6));
        copyMenuItem.setGraphic(copyGraphic);
        copyMenuItem.setText("");
        copyMenuItem.setOnAction(event -> {
            if (card == null) {
                return;
            }
            CardClipboard.copyCards(new ArrayList<>(
                    ContextMenuItemFactory.resolveEffectiveRightPaneCards(card)));
        });
        return copyMenuItem;
    }

    @Override
    public void updateSelected(boolean selected) {
        // Intentionally empty: per-card wrappers handle selection visuals.
    }

    /**
     * Makes the row cell a drop target for MIDDLE-pane drags. Dropping removes
     * the dragged elements from their source.
     */
    private void setupDropTarget() {
        setOnDragOver(event -> {
            if ("MIDDLE".equals(DragDropManager.getDragSourcePane())
                    && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        setOnDragDropped(event -> {
            if (!"MIDDLE".equals(DragDropManager.getDragSourcePane())) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            List<CardElement> draggedElements =
                    new ArrayList<>(DragDropManager.getDraggedElements());
            if (!draggedElements.isEmpty()) {
                Controller.MenuActionHandler
                        .handleBulkRemoveElementsFromDecksAndCollections(draggedElements);
                Controller.MenuActionHandler
                        .handleBulkRemoveFromOwnedCollection(draggedElements);
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }

    // ── Cell rendering ─────────────────────────────────────────────────────────

    @Override
    protected void updateItem(List<Card> row, boolean empty) {
        super.updateItem(row, empty);
        rowContainer.getChildren().clear();
        if (empty || row == null) {
            setGraphic(null);
            return;
        }

        for (Card card : row) {
            String fullImageUrl = getImagePath(card);
            Image image;
            try {
                image = LruImageCache.getImage(fullImageUrl);
                if (image == null) {
                    image = new Image(fullImageUrl, imageWidth, imageHeight, true, true);
                }
            } catch (Exception exception) {
                image = new Image("file:./src/main/resources/placeholder.jpg",
                        imageWidth, imageHeight, true, true);
            }

            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(imageWidth);
            imageView.setFitHeight(imageHeight);
            imageView.setPreserveRatio(true);
            imageView.setPickOnBounds(true);
            imageView.setFocusTraversable(true);

            StackPane cardWrapper = new StackPane(imageView);
            double wrapperWidth = imageWidth + 8;
            double wrapperHeight = imageHeight + 8;
            cardWrapper.setPadding(new Insets(4));
            cardWrapper.setMinWidth(wrapperWidth);
            cardWrapper.setPrefWidth(wrapperWidth);
            cardWrapper.setMaxWidth(wrapperWidth);
            cardWrapper.setMinHeight(wrapperHeight);
            cardWrapper.setPrefHeight(wrapperHeight);
            cardWrapper.setMaxHeight(wrapperHeight);

            boolean isSelected = "RIGHT".equals(SelectionManager.getActivePart())
                    && SelectionManager.getSelectedCards().contains(card);
            cardWrapper.setStyle(isSelected
                    ? "-fx-background-color: transparent; " +
                    "-fx-border-color: #cdfc04; " +
                    "-fx-border-width: 2; " +
                    "-fx-border-radius: 6;"
                    : "-fx-background-color: transparent;");

            ContextMenu decksCardContextMenu = buildDecksContextMenu(card);
            ContextMenu myCollCardContextMenu = buildMyCollectionContextMenu(card);

            cardWrapper.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.SECONDARY) {
                    event.consume();
                    return;
                }
                if (card == null) {
                    return;
                }
                event.consume();
                if (event.isControlDown()) {
                    SelectionManager.toggleSelection(card, "RIGHT");
                } else if (event.isShiftDown()) {
                    List<Card> allCardsAcrossRows = new ArrayList<>();
                    if (getListView() != null && getListView().getItems() != null) {
                        for (List<Card> rowInMosaic : getListView().getItems()) {
                            if (rowInMosaic != null) {
                                allCardsAcrossRows.addAll(rowInMosaic);
                            }
                        }
                    }
                    SelectionManager.rangeSelect(card, "RIGHT", allCardsAcrossRows);
                } else {
                    SelectionManager.selectCard(card, "RIGHT");
                }
                if (getListView() != null) {
                    getListView().refresh();
                }
            });

            cardWrapper.setOnDragDetected(event -> {
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
                    String path = getImagePath(dragCard);
                    Image cachedImage = path != null ? LruImageCache.getImage(path) : null;
                    if (cachedImage == null && path != null) {
                        try {
                            cachedImage = new Image(path, imageWidth, imageHeight, true, true);
                        } catch (Exception ignored) {
                        }
                    }
                    ghostImages.add(cachedImage);
                }

                Dragboard dragboard = cardWrapper.startDragAndDrop(TransferMode.MOVE);
                javafx.scene.image.WritableImage ghostImage =
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

            cardWrapper.setOnDragDone(event -> {
                DragDropManager.clearCurrentlyDraggedCard();
                event.consume();
            });

            cardWrapper.setOnMousePressed(event -> {
                if (event.isSecondaryButtonDown()) {
                    String tabName = ViewUtils.getCurrentTabName(getListView());
                    if (tabName.equalsIgnoreCase("My Collection")) {
                        myCollCardContextMenu.show(
                                cardWrapper, event.getScreenX(), event.getScreenY());
                    } else if (tabName.equalsIgnoreCase("Decks and Collections")
                            || tabName.equalsIgnoreCase("Decks & Collections")) {
                        decksCardContextMenu.show(
                                cardWrapper, event.getScreenX(), event.getScreenY());
                    }
                    event.consume();
                }
            });

            cardWrapper.setOnMouseReleased(event -> {
                if (event.getButton() == MouseButton.SECONDARY) {
                    event.consume();
                }
            });

            cardWrapper.setOnContextMenuRequested(event -> {
                String tabName = ViewUtils.getCurrentTabName(getListView());
                if (tabName.equalsIgnoreCase("My Collection")) {
                    myCollCardContextMenu.show(
                            cardWrapper, event.getScreenX(), event.getScreenY());
                } else if (tabName.equalsIgnoreCase("Decks and Collections")
                        || tabName.equalsIgnoreCase("Decks & Collections")) {
                    decksCardContextMenu.show(
                            cardWrapper, event.getScreenX(), event.getScreenY());
                }
                event.consume();
            });

            final String tooltipText = CardHoverPopup.buildTooltipText(card);
            cardWrapper.setOnMouseEntered(event -> {
                hoverLabel.setText(tooltipText);
                hoverPopup.show(cardWrapper, event.getScreenX() + 14, event.getScreenY() + 14);
            });
            cardWrapper.setOnMouseMoved(event ->
                    hoverPopup.show(cardWrapper, event.getScreenX() + 14, event.getScreenY() + 14));
            cardWrapper.setOnMouseExited(event -> hoverPopup.hide());

            rowContainer.getChildren().add(cardWrapper);
        }
        setGraphic(rowContainer);
    }

    // ── Image path resolution ──────────────────────────────────────────────────

    private String getImagePath(Card card) {
        if (card == null || card.getImagePath() == null) {
            return "file:./src/main/resources/placeholder.jpg";
        }
        String[] addresses = DataBaseUpdate.getAddresses(card.getImagePath() + ".jpg");
        if (addresses != null && addresses.length > 0) {
            return "file:" + addresses[0];
        }
        return "file:./src/main/resources/placeholder.jpg";
    }
}