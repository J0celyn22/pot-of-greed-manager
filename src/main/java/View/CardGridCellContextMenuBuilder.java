package View;

import Controller.*;
import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.CardsLists.CardsGroup;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * CardGridCellContextMenuBuilder — static factory that constructs all {@link ContextMenu}
 * objects used by {@link CardGridCell}.
 *
 * <p>Three distinct menus are built:</p>
 * <ol>
 *   <li>{@link #buildMyCollectionContextMenu} — for My Collection cards</li>
 *   <li>{@link #buildDecksContextMenu} — for Decks &amp; Collections non-archetype cards</li>
 *   <li>{@link #buildArchetypeCardContextMenu} — for read-only archetype cards</li>
 * </ol>
 *
 * <p>All context menus are lazily populated (via {@code setOnShowing}) so the underlying
 * data is always current when the menu opens.</p>
 */
public final class CardGridCellContextMenuBuilder {

    private static final Logger logger =
            LoggerFactory.getLogger(CardGridCellContextMenuBuilder.class);

    private CardGridCellContextMenuBuilder() {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public factory methods
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the full context menu used for cards in the My Collection tab.
     * Contains: Sort card (submenu), Move to... (submenu), Copy, Cut, Paste, Edit Card,
     * separator, Remove.
     */
    public static ContextMenu buildMyCollectionContextMenu(CardGridCell cell) {
        ContextMenu contextMenu = ContextMenuItemFactory.styledContextMenu();

        Menu sortingMenu = buildSortingSubMenu(cell);
        Menu moveToMenu = buildMoveToSubMenu(cell);

        MenuItem copyMenuItem = buildCopyMenuItem(cell);
        MenuItem cutMenuItem = buildCutOwnedMenuItem(cell);
        MenuItem pasteMenuItem = buildPasteOwnedMenuItem(cell);
        MenuItem editCardMenuItem = buildEditCardMenuItem(cell);
        MenuItem removeMenuItem = buildRemoveOwnedMenuItem(cell);

        contextMenu.getItems().addAll(
                sortingMenu,
                moveToMenu,
                copyMenuItem,
                cutMenuItem,
                pasteMenuItem,
                editCardMenuItem,
                new SeparatorMenuItem(),
                removeMenuItem
        );

        contextMenu.setOnShowing(event -> {
            CardElement currentItem = cell.getItem();
            boolean isMultiSelect = cell.isMiddleMultiSelectActive();
            sortingMenu.setVisible(!isMultiSelect);
            editCardMenuItem.setDisable(currentItem == null || isMultiSelect);
            removeMenuItem.setDisable(currentItem == null);
            pasteMenuItem.setVisible(!CardClipboard.isEmpty());
        });

        return contextMenu;
    }

    /**
     * Builds the context menu used for non-archetype cards in the Decks &amp; Collections tab.
     * Contains: Move to... (submenu), Copy, Cut, Paste, Edit Card, separator, Remove.
     */
    public static ContextMenu buildDecksContextMenu(CardGridCell cell) {
        ContextMenu contextMenu = ContextMenuItemFactory.styledContextMenu();

        Menu decksMoveMenu = buildDecksMoveSubMenu(cell);

        MenuItem decksCopyMenuItem = buildCopyMenuItem(cell);
        MenuItem decksCutMenuItem = buildCutDecksMenuItem(cell);
        MenuItem decksPasteMenuItem = buildPasteDecksMenuItem(cell);
        MenuItem decksEditCardMenuItem = buildEditCardMenuItem(cell);
        MenuItem decksRemoveMenuItem = buildRemoveDecksMenuItem(cell);

        contextMenu.getItems().addAll(
                decksMoveMenu,
                decksCopyMenuItem,
                decksCutMenuItem,
                decksPasteMenuItem,
                decksEditCardMenuItem,
                new SeparatorMenuItem(),
                decksRemoveMenuItem
        );

        contextMenu.setOnShowing(event -> {
            decksPasteMenuItem.setVisible(!CardClipboard.isEmpty());
            decksEditCardMenuItem.setDisable(cell.getItem() == null);
        });

        return contextMenu;
    }

    /**
     * Builds the context menu used for read-only archetype cards.
     * Contains: Add to... (submenu), Copy.
     */
    public static ContextMenu buildArchetypeCardContextMenu(CardGridCell cell) {
        ContextMenu contextMenu = ContextMenuItemFactory.styledContextMenu();

        Menu archetypeAddMenu = buildArchetypeAddSubMenu(cell);
        MenuItem archetypeCopyMenuItem = buildCopyMenuItem(cell);

        contextMenu.getItems().addAll(archetypeAddMenu, archetypeCopyMenuItem);
        return contextMenu;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sub-menu builders
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the lazy "Sort card" submenu for the My Collection context menu.
     */
    private static Menu buildSortingSubMenu(CardGridCell cell) {
        Menu sortingMenu = new Menu();
        Label sortLabel = new Label("Sort card");
        sortLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-weight: normal; -fx-font-size: 13;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox sortGraphic = new HBox(6, sortLabel, spacer);
        sortGraphic.setAlignment(Pos.CENTER_LEFT);
        sortGraphic.setPadding(new Insets(2, 6, 2, 6));
        sortingMenu.setGraphic(sortGraphic);
        sortingMenu.setText("");

        MenuItem placeholder = new MenuItem("Loading...");
        placeholder.setDisable(true);
        sortingMenu.getItems().add(placeholder);

        sortingMenu.setOnShowing(event -> {
            try {
                sortingMenu.getItems().clear();
                CardElement cardElement = cell.getItem();
                if (cardElement == null || cardElement.getCard() == null) {
                    sortingMenu.getItems().add(ContextMenuItemFactory.disabledItem("No card selected"));
                    return;
                }
                Card card = cardElement.getCard();

                // 1) D&C proposals
                List<MenuItem> dcItems = CardGridDestinationMenuBuilder.buildDecksAndCollectionsProposals(card, cardElement, cell);
                if (!dcItems.isEmpty()) {
                    sortingMenu.getItems().addAll(dcItems);
                    sortingMenu.getItems().add(new SeparatorMenuItem());
                }

                // 2) Type-of-cards box proposals
                List<MenuItem> typeItems = CardGridDestinationMenuBuilder.buildTypeBoxProposals(card, cardElement, cell);
                if (!typeItems.isEmpty()) {
                    sortingMenu.getItems().addAll(typeItems);
                    sortingMenu.getItems().add(new SeparatorMenuItem());
                }

                // 3) Swap proposals
                List<MenuItem> swapItems =
                        SwapProposalBuilder.buildSwapProposals(card, cardElement, cell);
                if (!swapItems.isEmpty()) {
                    sortingMenu.getItems().addAll(swapItems);
                }

                if (sortingMenu.getItems().isEmpty()) {
                    sortingMenu.getItems().add(ContextMenuItemFactory.disabledItem("No proposals"));
                }
            } catch (Throwable buildError) {
                logger.debug("Error building sorting submenu", buildError);
                sortingMenu.getItems().clear();
                sortingMenu.getItems().add(ContextMenuItemFactory.disabledItem("Error building proposals"));
            }
        });

        return sortingMenu;
    }

    /**
     * Builds the lazy "Move to..." submenu for the My Collection context menu.
     */
    private static Menu buildMoveToSubMenu(CardGridCell cell) {
        Menu moveToMenu = new Menu();
        Label moveLabel = new Label("Move to...");
        moveLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-weight: normal; -fx-font-size: 13;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox moveGraphic = new HBox(6, moveLabel, spacer);
        moveGraphic.setAlignment(Pos.CENTER_LEFT);
        moveGraphic.setPadding(new Insets(2, 6, 2, 6));
        moveToMenu.setGraphic(moveGraphic);
        moveToMenu.setText("");

        MenuItem placeholder = new MenuItem("Loading...");
        placeholder.setDisable(true);
        moveToMenu.getItems().add(placeholder);

        moveToMenu.setOnShowing(event -> CardGridDestinationMenuBuilder.populateMoveToMenu(moveToMenu, cell));

        return moveToMenu;
    }

    /**
     * Builds the lazy "Move to..." submenu for the Decks context menu.
     */
    private static Menu buildDecksMoveSubMenu(CardGridCell cell) {
        Menu decksMoveMenu = new Menu();
        Label moveLabel = new Label("Move to...");
        moveLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
        HBox moveGraphic = new HBox(moveLabel);
        moveGraphic.setAlignment(Pos.CENTER_LEFT);
        moveGraphic.setPadding(new Insets(2, 6, 2, 6));
        decksMoveMenu.setGraphic(moveGraphic);
        decksMoveMenu.setText("");

        MenuItem placeholder = new MenuItem("Loading...");
        placeholder.setDisable(true);
        decksMoveMenu.getItems().add(placeholder);

        decksMoveMenu.setOnShowing(event -> {
            decksMoveMenu.getItems().clear();
            String currentPath = cell.findCurrentLocationPath();
            List<MenuItem> moveItems = CardGridDestinationMenuBuilder.buildMoveDestinationMenuItems(currentPath, cell);
            if (moveItems.isEmpty()) {
                decksMoveMenu.getItems().add(ContextMenuItemFactory.disabledItem("No destinations available"));
            } else {
                decksMoveMenu.getItems().addAll(moveItems);
            }
        });

        return decksMoveMenu;
    }

    /**
     * Builds the lazy "Add to..." submenu for the archetype card context menu.
     */
    private static Menu buildArchetypeAddSubMenu(CardGridCell cell) {
        Menu addMenu = new Menu();
        Label addLabel = new Label("Add to...");
        addLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
        HBox addGraphic = new HBox(addLabel);
        addGraphic.setAlignment(Pos.CENTER_LEFT);
        addGraphic.setPadding(new Insets(2, 6, 2, 6));
        addMenu.setGraphic(addGraphic);
        addMenu.setText("");

        MenuItem placeholder = new MenuItem("Loading...");
        placeholder.setDisable(true);
        addMenu.getItems().add(placeholder);

        addMenu.setOnShowing(event -> {
            addMenu.getItems().clear();
            List<MenuItem> addItems = CardGridDestinationMenuBuilder.buildAddDestinationMenuItems(cell);
            if (addItems.isEmpty()) {
                addMenu.getItems().add(ContextMenuItemFactory.disabledItem("No destinations available"));
            } else {
                addMenu.getItems().addAll(addItems);
            }
        });

        return addMenu;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Individual menu-item factories
    // ─────────────────────────────────────────────────────────────────────────

    private static MenuItem buildCopyMenuItem(CardGridCell cell) {
        MenuItem menuItem = accentItem("Copy");
        menuItem.setOnAction(event -> cell.executeCopyAction());
        return menuItem;
    }

    private static MenuItem buildCutOwnedMenuItem(CardGridCell cell) {
        MenuItem menuItem = accentItem("Cut");
        menuItem.setOnAction(event -> cell.executeCutFromOwnedCollectionAction());
        return menuItem;
    }

    private static MenuItem buildCutDecksMenuItem(CardGridCell cell) {
        MenuItem menuItem = accentItem("Cut");
        menuItem.setOnAction(event -> cell.executeCutFromDecksAction());
        return menuItem;
    }

    private static MenuItem buildPasteOwnedMenuItem(CardGridCell cell) {
        MenuItem menuItem = accentItem("Paste");
        menuItem.setVisible(false);
        menuItem.setOnAction(event -> {
            if (CardClipboard.isEmpty()) {
                return;
            }
            CardElement currentItem = cell.getItem();
            if (currentItem == null) {
                return;
            }
            MenuActionHandler.handlePasteElementsAfterElementInOwnedCollection(
                    CardClipboard.getContents(), currentItem);
        });
        return menuItem;
    }

    private static MenuItem buildPasteDecksMenuItem(CardGridCell cell) {
        MenuItem menuItem = accentItem("Paste");
        menuItem.setVisible(false);
        menuItem.setOnAction(event -> {
            if (CardClipboard.isEmpty()) {
                return;
            }
            CardElement currentItem = cell.getItem();
            if (currentItem == null) {
                return;
            }
            @SuppressWarnings("unchecked")
            ObservableList<CardElement> deckGroupItems =
                    (ObservableList<CardElement>) cell.getGridView().getItems();
            int insertionIndex = deckGroupItems.indexOf(currentItem);
            if (insertionIndex < 0) {
                insertionIndex = deckGroupItems.size() - 1;
            }
            // D&C deck sections only carry Card identity — extract the Card from
            // each clipboard element (condition/rarity are not used in this context).
            List<CardElement> clipboardElements = CardClipboard.getContents();
            for (int cardIndex = 0; cardIndex < clipboardElements.size(); cardIndex++) {
                CardElement clipboardElement = clipboardElements.get(cardIndex);
                if (clipboardElement == null || clipboardElement.getCard() == null) {
                    continue;
                }
                int targetIndex = Math.min(
                        insertionIndex + 1 + cardIndex, deckGroupItems.size());
                deckGroupItems.add(targetIndex,
                        new CardElement(clipboardElement.getCard()));
            }
            for (Map.Entry<CardsGroup, ObservableList<CardElement>> registryEntry
                    : CardGroupRegistry.GROUP_OBSERVABLE_LISTS.entrySet()) {
                if (registryEntry.getValue() == deckGroupItems) {
                    CardGroupRegistry.triggerHeightAdjustment(registryEntry.getKey());
                    Object pasteOwner = cell.findDacOwnerForCardsGroup(registryEntry.getKey());
                    if (pasteOwner != null) {
                        UserInterfaceFunctions.markDirty(pasteOwner);
                    } else {
                        UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                    }
                    break;
                }
            }
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        });
        return menuItem;
    }

    private static MenuItem buildEditCardMenuItem(CardGridCell cell) {
        MenuItem menuItem = accentItem("Edit Card");
        menuItem.setOnAction(event -> {
            CardElement currentItem = cell.getItem();
            if (currentItem != null) {
                MenuActionHandler.handleEditCard(currentItem, cell.wrapper);
            }
        });
        return menuItem;
    }

    private static MenuItem buildRemoveOwnedMenuItem(CardGridCell cell) {
        MenuItem menuItem = redRemoveItem();
        menuItem.setOnAction(event -> {
            CardElement currentItem = cell.getItem();
            if (currentItem == null) {
                return;
            }
            List<CardElement> elementsToRemove = cell.getEffectiveMiddleElements();
            if (elementsToRemove.size() > 1) {
                MenuActionHandler.handleBulkRemoveFromOwnedCollection(
                        new ArrayList<>(elementsToRemove));
            } else {
                cell.removeCardElement(currentItem);
                UserInterfaceFunctions.refreshOwnedCollectionView();
            }
        });
        return menuItem;
    }

    private static MenuItem buildRemoveDecksMenuItem(CardGridCell cell) {
        MenuItem menuItem = redRemoveItem();
        menuItem.setOnAction(event -> {
            CardElement currentItem = cell.getItem();
            if (currentItem == null) {
                return;
            }
            if (cell.isMiddleMultiSelectActive()) {
                MenuActionHandler.handleBulkRemoveElementsFromDecksAndCollections(
                        new ArrayList<>(SelectionManager.getSelectedMiddleElements()));
            } else {
                MenuActionHandler.handleBulkRemoveElementsFromDecksAndCollections(
                        Collections.singletonList(currentItem));
            }
        });
        return menuItem;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared styling utilities
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a lime-accented menu item with the given label text.
     * Used for most non-destructive actions.
     */
    private static MenuItem accentItem(String labelText) {
        MenuItem menuItem = new MenuItem();
        Label label = new Label(labelText);
        label.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
        HBox graphic = new HBox(label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        menuItem.setGraphic(graphic);
        menuItem.setText("");
        return menuItem;
    }

    /**
     * Creates a red "Remove" menu item with a trash-can icon.
     * Used for destructive remove actions.
     */
    private static MenuItem redRemoveItem() {
        MenuItem menuItem = new MenuItem();
        Label trashIcon = new Label("\uD83D\uDDD1");
        trashIcon.setStyle("-fx-text-fill: #ff4d4d; -fx-font-size: 13;");
        Label removeLabel = new Label("Remove");
        removeLabel.setStyle("-fx-text-fill: #ff4d4d; -fx-font-weight: bold;");
        HBox graphic = new HBox(6, trashIcon, removeLabel);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        menuItem.setGraphic(graphic);
        menuItem.setText("");
        return menuItem;
    }
}