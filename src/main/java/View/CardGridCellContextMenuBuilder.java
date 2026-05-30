package View;

import Controller.*;
import Model.CardsLists.*;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;

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
        ContextMenu contextMenu = styledContextMenu();

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
        ContextMenu contextMenu = styledContextMenu();

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
        ContextMenu contextMenu = styledContextMenu();

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
                    sortingMenu.getItems().add(disabledItem("No card selected"));
                    return;
                }
                Card card = cardElement.getCard();

                // 1) D&C proposals
                List<MenuItem> dcItems = buildDecksAndCollectionsProposals(card, cardElement, cell);
                if (!dcItems.isEmpty()) {
                    sortingMenu.getItems().addAll(dcItems);
                    sortingMenu.getItems().add(new SeparatorMenuItem());
                }

                // 2) Type-of-cards box proposals
                List<MenuItem> typeItems = buildTypeBoxProposals(card, cardElement, cell);
                if (!typeItems.isEmpty()) {
                    sortingMenu.getItems().addAll(typeItems);
                    sortingMenu.getItems().add(new SeparatorMenuItem());
                }

                // 3) Swap proposals (via reflection for safe late-binding)
                try {
                    java.lang.reflect.Method swapMethod = null;
                    try {
                        swapMethod = cell.outer.getClass().getDeclaredMethod(
                                "buildSwapProposals", Card.class, CardElement.class);
                    } catch (NoSuchMethodException firstTry) {
                        try {
                            swapMethod = cell.outer.getClass().getMethod(
                                    "buildSwapProposals", Card.class, CardElement.class);
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                    if (swapMethod != null) {
                        swapMethod.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        List<MenuItem> swapItems =
                                (List<MenuItem>) swapMethod.invoke(cell.outer, card, cardElement);
                        if (swapItems != null && !swapItems.isEmpty()) {
                            sortingMenu.getItems().addAll(swapItems);
                        }
                    }
                } catch (Throwable reflectionError) {
                    logger.debug("Failed to invoke buildSwapProposals via reflection", reflectionError);
                    sortingMenu.getItems().add(disabledItem("Error building swap proposals"));
                }

                if (sortingMenu.getItems().isEmpty()) {
                    sortingMenu.getItems().add(disabledItem("No proposals"));
                }
            } catch (Throwable buildError) {
                logger.debug("Error building sorting submenu", buildError);
                sortingMenu.getItems().clear();
                sortingMenu.getItems().add(disabledItem("Error building proposals"));
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

        moveToMenu.setOnShowing(event -> populateMoveToMenu(moveToMenu, cell));

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
            List<MenuItem> moveItems = buildMoveDestinationMenuItems(currentPath, cell);
            if (moveItems.isEmpty()) {
                decksMoveMenu.getItems().add(disabledItem("No destinations available"));
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
            List<MenuItem> addItems = buildAddDestinationMenuItems(cell);
            if (addItems.isEmpty()) {
                addMenu.getItems().add(disabledItem("No destinations available"));
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
        MenuItem mi = accentItem("Copy");
        mi.setOnAction(event -> cell.executeCopyAction());
        return mi;
    }

    private static MenuItem buildCutOwnedMenuItem(CardGridCell cell) {
        MenuItem mi = accentItem("Cut");
        mi.setOnAction(event -> cell.executeCutFromOwnedCollectionAction());
        return mi;
    }

    private static MenuItem buildCutDecksMenuItem(CardGridCell cell) {
        MenuItem mi = accentItem("Cut");
        mi.setOnAction(event -> cell.executeCutFromDecksAction());
        return mi;
    }

    private static MenuItem buildPasteOwnedMenuItem(CardGridCell cell) {
        MenuItem mi = accentItem("Paste");
        mi.setVisible(false);
        mi.setOnAction(event -> {
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
        return mi;
    }

    private static MenuItem buildPasteDecksMenuItem(CardGridCell cell) {
        MenuItem mi = accentItem("Paste");
        mi.setVisible(false);
        mi.setOnAction(event -> {
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
        return mi;
    }

    private static MenuItem buildEditCardMenuItem(CardGridCell cell) {
        MenuItem mi = accentItem("Edit Card");
        mi.setOnAction(event -> {
            CardElement currentItem = cell.getItem();
            if (currentItem != null) {
                MenuActionHandler.handleEditCard(currentItem, cell.wrapper);
            }
        });
        return mi;
    }

    private static MenuItem buildRemoveOwnedMenuItem(CardGridCell cell) {
        MenuItem mi = redRemoveItem();
        mi.setOnAction(event -> {
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
        return mi;
    }

    private static MenuItem buildRemoveDecksMenuItem(CardGridCell cell) {
        MenuItem mi = redRemoveItem();
        mi.setOnAction(event -> {
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
        return mi;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // "Move to..." population for My Collection tab
    // ─────────────────────────────────────────────────────────────────────────

    private static void populateMoveToMenu(Menu moveToMenu, CardGridCell cell) {
        moveToMenu.getItems().clear();

        CardElement clickedElement = cell.getItem();
        if (clickedElement == null) {
            moveToMenu.getItems().add(disabledItem("No card selected"));
            return;
        }

        OwnedCardsCollection ownedCollection = loadOwnedCollection();
        if (ownedCollection == null
                || ownedCollection.getOwnedCollection() == null
                || ownedCollection.getOwnedCollection().isEmpty()) {
            moveToMenu.getItems().add(disabledItem("No boxes available"));
            return;
        }

        // Identify the element's current box and group so we can skip it.
        Box currentBox = null;
        Box currentGroupBox = null;
        CardsGroup currentGroup = null;
        try {
            outerSearch:
            for (Box box : ownedCollection.getOwnedCollection()) {
                if (box == null) {
                    continue;
                }
                if (box.getContent() != null) {
                    for (CardsGroup group : box.getContent()) {
                        if (group == null || group.getCardList() == null) {
                            continue;
                        }
                        for (CardElement groupElement : group.getCardList()) {
                            if (groupElement == clickedElement) {
                                currentBox = box;
                                currentGroupBox = box;
                                currentGroup = group;
                                break outerSearch;
                            }
                        }
                    }
                }
                if (box.getSubBoxes() != null) {
                    for (Box subBox : box.getSubBoxes()) {
                        if (subBox == null || subBox.getContent() == null) {
                            continue;
                        }
                        for (CardsGroup group : subBox.getContent()) {
                            if (group == null || group.getCardList() == null) {
                                continue;
                            }
                            for (CardElement groupElement : group.getCardList()) {
                                if (groupElement == clickedElement) {
                                    currentBox = subBox;
                                    currentGroupBox = subBox;
                                    currentGroup = group;
                                    break outerSearch;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        final Box capturedCurrentBox = currentGroupBox;
        final CardsGroup capturedCurrentGroup = currentGroup;

        BiFunction<String, String, MenuItem> makeMoveItem = (displayName, handlerTarget) -> {
            MenuItem mi = new MenuItem(displayName);
            mi.setOnAction(moveEvent -> {
                try {
                    List<CardElement> elementsToMove = cell.getEffectiveMiddleElements();
                    if (elementsToMove.size() > 1) {
                        MenuActionHandler.handleBulkMove(new ArrayList<>(elementsToMove), handlerTarget);
                    } else {
                        MenuActionHandler.handleMove(cell.getItem(), handlerTarget);
                    }
                } catch (Throwable moveError) {
                    logger.debug("Move action failed for target {}", handlerTarget, moveError);
                }
            });
            return mi;
        };

        for (Box box : ownedCollection.getOwnedCollection()) {
            if (box == null) {
                continue;
            }
            String boxName = CardTreeCell.sanitizeDisplayName(box.getName() == null ? "" : box.getName());
            if (boxName.isEmpty()) {
                boxName = "(Unnamed box)";
            }

            moveToMenu.getItems().add(makeMoveItem.apply(boxName, boxName));

            if (box.getContent() != null) {
                for (CardsGroup group : box.getContent()) {
                    if (group == null) {
                        continue;
                    }
                    String rawGroupName = group.getName();
                    if (rawGroupName == null || rawGroupName.trim().isEmpty()) {
                        continue;
                    }
                    String groupName = CardTreeCell.sanitizeDisplayName(rawGroupName);
                    if (groupName.isEmpty()) {
                        continue;
                    }
                    boolean isCurrentGroup = capturedCurrentBox != null
                            && capturedCurrentGroup != null
                            && capturedCurrentBox == box
                            && capturedCurrentGroup == group;
                    if (isCurrentGroup) {
                        continue;
                    }
                    String display = boxName + " / " + groupName;
                    String handlerTarget = boxName + "/" + groupName;
                    moveToMenu.getItems().add(makeMoveItem.apply(display, handlerTarget));
                }
            }

            if (box.getSubBoxes() != null) {
                for (Box subBox : box.getSubBoxes()) {
                    if (subBox == null) {
                        continue;
                    }
                    String subBoxName = CardTreeCell.sanitizeDisplayName(
                            subBox.getName() == null ? "" : subBox.getName());
                    if (subBoxName.isEmpty()) {
                        subBoxName = "(Unnamed sub-box)";
                    }
                    moveToMenu.getItems().add(makeMoveItem.apply(subBoxName, subBoxName));

                    if (subBox.getContent() != null) {
                        for (CardsGroup group : subBox.getContent()) {
                            if (group == null) {
                                continue;
                            }
                            String groupName = CardTreeCell.sanitizeDisplayName(
                                    group.getName() == null ? "" : group.getName());
                            if (groupName.isEmpty()) {
                                groupName = "(Unnamed group)";
                            }
                            boolean isCurrentGroup = capturedCurrentBox != null
                                    && capturedCurrentGroup != null
                                    && capturedCurrentBox == subBox
                                    && capturedCurrentGroup == group;
                            if (isCurrentGroup) {
                                continue;
                            }
                            String display = subBoxName + " / " + groupName;
                            String handlerTarget = subBoxName + "/" + groupName;
                            moveToMenu.getItems().add(makeMoveItem.apply(display, handlerTarget));
                        }
                    }
                }
            }
        }

        if (moveToMenu.getItems().isEmpty()) {
            moveToMenu.getItems().add(disabledItem("No other destinations"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // "Move to..." for Decks & Collections (MOVE semantics)
    // ─────────────────────────────────────────────────────────────────────────

    static List<MenuItem> buildMoveDestinationMenuItems(
            String excludePath, CardGridCell cell) {
        List<MenuItem> items = new ArrayList<>();
        try {
            DecksAndCollectionsList dacList = ensureDacList();
            if (dacList == null) {
                return items;
            }

            if (dacList.getCollections() != null) {
                for (ThemeCollection themeCollection : dacList.getCollections()) {
                    if (themeCollection == null) {
                        continue;
                    }
                    String collName = CardTreeCell.sanitizeDisplayName(themeCollection.getName());
                    addMoveDestItem(items, collName, excludePath, cell);
                    if (themeCollection.getLinkedDecks() != null) {
                        for (List<Deck> deckUnit : themeCollection.getLinkedDecks()) {
                            if (deckUnit == null) {
                                continue;
                            }
                            for (Deck deck : deckUnit) {
                                if (deck == null) {
                                    continue;
                                }
                                String deckBase = collName + " / "
                                        + CardTreeCell.sanitizeDisplayName(deck.getName());
                                if (cell.moveSectionAllowed("Main Deck")) {
                                    addMoveDestItem(items, deckBase + " / Main Deck", excludePath, cell);
                                }
                                if (cell.moveSectionAllowed("Extra Deck")) {
                                    addMoveDestItem(items, deckBase + " / Extra Deck", excludePath, cell);
                                }
                                addMoveDestItem(items, deckBase + " / Side Deck", excludePath, cell);
                            }
                        }
                    }
                    addMoveDestItem(items, collName + " / Exclusion List", excludePath, cell);
                }
            }
            if (dacList.getDecks() != null) {
                for (Deck deck : dacList.getDecks()) {
                    if (deck == null) {
                        continue;
                    }
                    String deckName = CardTreeCell.sanitizeDisplayName(deck.getName());
                    if (cell.moveSectionAllowed("Main Deck")) {
                        addMoveDestItem(items, deckName + " / Main Deck", excludePath, cell);
                    }
                    if (cell.moveSectionAllowed("Extra Deck")) {
                        addMoveDestItem(items, deckName + " / Extra Deck", excludePath, cell);
                    }
                    addMoveDestItem(items, deckName + " / Side Deck", excludePath, cell);
                }
            }
        } catch (Exception buildError) {
            logger.debug("buildMoveDestinationMenuItems failed", buildError);
        }
        return items;
    }

    private static void addMoveDestItem(
            List<MenuItem> items, String path, String excludePath, CardGridCell cell) {
        if (path == null || path.equals(excludePath)) {
            return;
        }
        MenuItem mi = new MenuItem();
        Label pathLabel = new Label(path);
        pathLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
        HBox graphic = new HBox(pathLabel);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(graphic);
        mi.setText("");
        mi.setOnAction(event -> {
            // Build the list of elements to move.
            List<CardElement> elementsToMove;
            if (cell.isMiddleMultiSelectActive()) {
                elementsToMove = new ArrayList<>(SelectionManager.getSelectedMiddleElements());
            } else {
                CardElement singleElement = cell.getItem();
                if (singleElement == null) {
                    return;
                }
                elementsToMove = Collections.singletonList(singleElement);
            }

            // Remove each element from its source observable list.
            Set<Object> sourceOwners = new LinkedHashSet<>();
            for (CardElement cardElement : elementsToMove) {
                boolean wasRemoved = false;
                for (Map.Entry<CardsGroup, ObservableList<CardElement>> registryEntry
                        : CardGroupRegistry.GROUP_OBSERVABLE_LISTS.entrySet()) {
                    if (registryEntry.getValue().remove(cardElement)) {
                        Object owner = cell.findDacOwnerForCardsGroup(registryEntry.getKey());
                        if (owner != null) {
                            sourceOwners.add(owner);
                        }
                        wasRemoved = true;
                        break;
                    }
                }
                if (!wasRemoved && elementsToMove.size() == 1
                        && cell.getGridView() != null
                        && cell.getGridView().getItems() != null) {
                    cell.getGridView().getItems().remove(cardElement);
                }
            }

            // Mark source owners dirty.
            if (!sourceOwners.isEmpty()) {
                for (Object owner : sourceOwners) {
                    UserInterfaceFunctions.markDirty(owner);
                }
            } else {
                Object fallbackOwner = cell.resolveDecksTargetOwner(
                        excludePath != null ? excludePath : "");
                if (fallbackOwner != null) {
                    UserInterfaceFunctions.markDirty(fallbackOwner);
                } else {
                    UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                }
            }

            // Add all elements to the target list.
            List<CardElement> targetList = cell.resolveDecksTargetList(path);
            if (targetList != null) {
                targetList.addAll(elementsToMove);
                MenuActionHandler.setLastDecksAddedTarget(path);
                Object destOwner = cell.resolveDecksTargetOwner(path);
                if (destOwner != null) {
                    UserInterfaceFunctions.markDirty(destOwner);
                } else {
                    UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                }
            } else {
                logger.warn("addMoveDestItem: could not resolve path '{}'", path);
            }

            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();

            // If any source or destination belongs to a ThemeCollection's own list,
            // missing-sets are stale → force a full rebuild.
            boolean needsFullRebuild = sourceOwners.stream().anyMatch(
                    owner -> owner instanceof ThemeCollection);
            if (!needsFullRebuild) {
                Object destOwner = cell.resolveDecksTargetOwner(path);
                needsFullRebuild = destOwner instanceof ThemeCollection;
            }
            if (needsFullRebuild) {
                UserInterfaceFunctions.triggerDecksStructureRefresh();
            } else {
                UserInterfaceFunctions.refreshDecksAndCollectionsView();
            }
        });
        items.add(mi);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // "Add to..." for archetype cards (ADD semantics)
    // ─────────────────────────────────────────────────────────────────────────

    static List<MenuItem> buildAddDestinationMenuItems(CardGridCell cell) {
        List<MenuItem> items = new ArrayList<>();
        try {
            DecksAndCollectionsList dacList = ensureDacList();
            if (dacList == null) {
                return items;
            }

            if (dacList.getCollections() != null) {
                for (ThemeCollection themeCollection : dacList.getCollections()) {
                    if (themeCollection == null) {
                        continue;
                    }
                    String collName = CardTreeCell.sanitizeDisplayName(themeCollection.getName());
                    addAddDestItem(items, collName, cell);
                    if (themeCollection.getLinkedDecks() != null) {
                        for (List<Deck> deckUnit : themeCollection.getLinkedDecks()) {
                            if (deckUnit == null) {
                                continue;
                            }
                            for (Deck deck : deckUnit) {
                                if (deck == null) {
                                    continue;
                                }
                                String deckBase = collName + " / "
                                        + CardTreeCell.sanitizeDisplayName(deck.getName());
                                if (cell.addSectionAllowed("Main Deck")) {
                                    addAddDestItem(items, deckBase + " / Main Deck", cell);
                                }
                                if (cell.addSectionAllowed("Extra Deck")) {
                                    addAddDestItem(items, deckBase + " / Extra Deck", cell);
                                }
                                addAddDestItem(items, deckBase + " / Side Deck", cell);
                            }
                        }
                    }
                    addAddDestItem(items, collName + " / Exclusion List", cell);
                }
            }
            if (dacList.getDecks() != null) {
                for (Deck deck : dacList.getDecks()) {
                    if (deck == null) {
                        continue;
                    }
                    String deckName = CardTreeCell.sanitizeDisplayName(deck.getName());
                    if (cell.addSectionAllowed("Main Deck")) {
                        addAddDestItem(items, deckName + " / Main Deck", cell);
                    }
                    if (cell.addSectionAllowed("Extra Deck")) {
                        addAddDestItem(items, deckName + " / Extra Deck", cell);
                    }
                    addAddDestItem(items, deckName + " / Side Deck", cell);
                }
            }
        } catch (Exception buildError) {
            logger.debug("buildAddDestinationMenuItems failed", buildError);
        }
        return items;
    }

    private static void addAddDestItem(
            List<MenuItem> items, String path, CardGridCell cell) {
        if (path == null) {
            return;
        }
        MenuItem mi = new MenuItem();
        Label pathLabel = new Label(path);
        pathLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(pathLabel);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(graphic);
        mi.setText("");
        mi.setOnAction(event -> {
            CardElement currentItem = cell.getItem();
            if (currentItem == null || currentItem.getCard() == null) {
                return;
            }

            java.util.Collection<Card> cardsToAdd;
            if (cell.isMiddleMultiSelectActive()) {
                cardsToAdd = SelectionManager.getSelectedCards();
            } else {
                cardsToAdd = Collections.singletonList(currentItem.getCard());
            }

            String[] pathParts = path.split("\\s*/\\s*");
            String lastPart = pathParts[pathParts.length - 1].trim()
                    .toLowerCase(java.util.Locale.ROOT);
            boolean isExclusion = lastPart.equals("exclusion list")
                    || lastPart.equals("cards not to add");

            boolean targetWasEmpty = cell.isDecksTargetEmpty(pathParts, isExclusion);

            if (isExclusion && pathParts.length >= 2) {
                MenuActionHandler.handleBulkAddToExclusionList(cardsToAdd, pathParts[0].trim());
            } else if (pathParts.length == 1) {
                MenuActionHandler.handleBulkAddToCollectionCards(cardsToAdd, pathParts[0].trim());
            } else {
                MenuActionHandler.handleBulkAddToDeck(cardsToAdd, path);
            }

            boolean affectsCollectionList = (pathParts.length == 1 || isExclusion);
            if (affectsCollectionList || targetWasEmpty) {
                UserInterfaceFunctions.triggerDecksStructureRefresh();
            } else {
                UserInterfaceFunctions.refreshDecksAndCollectionsView();
            }
        });
        items.add(mi);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // D&C and type-box proposals (previously in CardTreeCell)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds "Sort card" menu items proposing Decks & Collections destinations for
     * {@code card}.  Orange-styled items are pure quality-upgrade proposals; lime-styled
     * items represent cards that are genuinely needed.
     */
    static List<MenuItem> buildDecksAndCollectionsProposals(
            Card card, CardElement clickedElement, CardGridCell cell) {
        List<MenuItem> items = new ArrayList<>();
        try {
            if (card == null) {
                return items;
            }
            if (UserInterfaceFunctions.getDecksList() == null) {
                UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
            }
            DecksAndCollectionsList dacList = UserInterfaceFunctions.getDecksList();
            if (dacList == null) {
                return items;
            }

            OwnedCardsCollection ownedCollection = loadOwnedCollection();
            if (ownedCollection == null || ownedCollection.getOwnedCollection() == null) {
                return items;
            }

            Map<String, Boolean> proposedTargets = new LinkedHashMap<>();
            Set<String> upgradeOnlyTargets = new HashSet<>();

            if (dacList.getCollections() != null) {
                for (ThemeCollection themeCollection : dacList.getCollections()) {
                    if (themeCollection == null) {
                        continue;
                    }
                    int countInCollection = countCardInList(themeCollection.toList(), card);
                    if (countInCollection <= 0) {
                        continue;
                    }
                    // Compare the TC's required count against the total copies
                    // already placed in the TC-named group(s) of the owned collection,
                    // not against any single group in isolation (Bug 3B fix).
                    int countInOwned = countInOwnedForDeckCombined(
                            ownedCollection, themeCollection.getName(), card);
                    boolean needsMore = countInCollection > countInOwned;
                    boolean qualityUpgrade = !needsMore
                            && isQualityUpgradeFor(themeCollection.getCardsList(),
                            clickedElement);
                    if (needsMore || qualityUpgrade) {
                        String target = CardTreeCell.sanitizeDisplayName(
                                themeCollection.getName());
                        boolean existsInOwned = locationExistsInOwned(
                                themeCollection.getName(), ownedCollection);
                        proposedTargets.put(target, existsInOwned);
                        if (qualityUpgrade) {
                            upgradeOnlyTargets.add(target);
                        }
                    }

                    if (themeCollection.getLinkedDecks() != null) {
                        for (List<Deck> deckUnit : themeCollection.getLinkedDecks()) {
                            if (deckUnit == null) {
                                continue;
                            }
                            for (Deck deck : deckUnit) {
                                if (deck == null) {
                                    continue;
                                }
                                addDeckSectionProposals(deck, card, clickedElement,
                                        ownedCollection, proposedTargets, upgradeOnlyTargets);
                            }
                        }
                    }
                }
            }

            if (dacList.getDecks() != null) {
                for (Deck deck : dacList.getDecks()) {
                    if (deck == null) {
                        continue;
                    }
                    addDeckSectionProposals(deck, card, clickedElement,
                            ownedCollection, proposedTargets, upgradeOnlyTargets);
                }
            }

            // Build the deduplicated menu items.
            Set<String> labelsAdded = new HashSet<>();
            for (Map.Entry<String, Boolean> proposalEntry : proposedTargets.entrySet()) {
                String rawTarget = proposalEntry.getKey();
                boolean existsInOwned = proposalEntry.getValue() != null
                        && proposalEntry.getValue();
                if (rawTarget == null || rawTarget.trim().isEmpty()) {
                    continue;
                }
                final String handlerTarget = rawTarget;

                // Strip "/Main Deck" etc. from the visible label.
                String displayTarget = rawTarget;
                if (displayTarget.endsWith("/Main Deck")
                        || displayTarget.endsWith("/Extra Deck")
                        || displayTarget.endsWith("/Side Deck")) {
                    displayTarget = displayTarget.substring(0, displayTarget.lastIndexOf('/'));
                }

                String itemLabel;
                if (existsInOwned) {
                    itemLabel = displayTarget;
                } else {
                    String baseName = displayTarget;
                    if (baseName.contains("/")) {
                        String[] parts = baseName.split("/");
                        baseName = parts[parts.length - 1];
                    }
                    itemLabel = "Add " + baseName;
                }

                if (labelsAdded.contains(itemLabel)) {
                    continue;
                }
                labelsAdded.add(itemLabel);

                boolean isUpgradeOnly = upgradeOnlyTargets.contains(rawTarget);
                MenuItem mi = new MenuItem(itemLabel);
                mi.setStyle(isUpgradeOnly
                        ? "-fx-text-fill: #EB9E34;"
                        : "-fx-text-fill: #cdfc04;");
                if (itemLabel.startsWith("Add ")) {
                    final String catName = itemLabel.substring(4).trim();
                    mi.setOnAction(ev ->
                            MenuActionHandler.handleAddCategoryAndMove(clickedElement, catName));
                } else if (!itemLabel.startsWith("Swap")) {
                    mi.setOnAction(ev ->
                            MenuActionHandler.handleMove(clickedElement, handlerTarget));
                }
                items.add(mi);
            }
        } catch (Exception buildError) {
            logger.debug("buildDecksAndCollectionsProposals failed", buildError);
        }
        return items;
    }

    /**
     * Adds main/extra/side deck section proposals for a single Deck to {@code proposedTargets}.
     */
    private static void addDeckSectionProposals(
            Deck deck, Card card, CardElement clickedElement,
            OwnedCardsCollection ownedCollection,
            Map<String, Boolean> proposedTargets,
            Set<String> upgradeOnlyTargets) {

        int countMain = countCardInList(deck.getMainDeck(), card);
        int countExtra = countCardInList(deck.getExtraDeck(), card);
        int countSide = countCardInList(deck.getSideDeck(), card);
        int requiredTotal = countMain + countExtra + countSide;
        if (requiredTotal <= 0) {
            return;
        }

        int presentTotal = countInOwnedForDeckCombined(ownedCollection, deck.getName(), card);
        boolean needsMore = requiredTotal > presentTotal;
        boolean upgradeMain = !needsMore && countMain > 0
                && isQualityUpgradeFor(deck.getMainDeck(), clickedElement);
        boolean upgradeExtra = !needsMore && countExtra > 0
                && isQualityUpgradeFor(deck.getExtraDeck(), clickedElement);
        boolean upgradeSide = !needsMore && countSide > 0
                && isQualityUpgradeFor(deck.getSideDeck(), clickedElement);

        if (!needsMore && !upgradeMain && !upgradeExtra && !upgradeSide) {
            return;
        }

        boolean existsInOwned = locationExistsInOwned(deck.getName(), ownedCollection);
        String deckName = CardTreeCell.sanitizeDisplayName(deck.getName());

        if (countMain > 0 && Utils.DeckCompatibility.isCompatibleWith(card, "Main Deck")
                && (needsMore || upgradeMain)) {
            String target = deckName + "/Main Deck";
            proposedTargets.put(target, existsInOwned);
            if (!needsMore && upgradeMain) {
                upgradeOnlyTargets.add(target);
            }
        }
        if (countExtra > 0 && Utils.DeckCompatibility.isCompatibleWith(card, "Extra Deck")
                && (needsMore || upgradeExtra)) {
            String target = deckName + "/Extra Deck";
            proposedTargets.put(target, existsInOwned);
            if (!needsMore && upgradeExtra) {
                upgradeOnlyTargets.add(target);
            }
        }
        if (countSide > 0 && (needsMore || upgradeSide)) {
            String target = deckName + "/Side Deck";
            proposedTargets.put(target, existsInOwned);
            if (!needsMore && upgradeSide) {
                upgradeOnlyTargets.add(target);
            }
        }
    }

    /**
     * Builds "Sort card" menu items proposing type-of-cards box destinations based on the
     * card's type and properties.
     */
    static List<MenuItem> buildTypeBoxProposals(
            Card card, CardElement clickedElement, CardGridCell cell) {
        List<MenuItem> items = new ArrayList<>();
        try {
            if (card == null) {
                return items;
            }

            Set<String> desiredFrenchCategories = new java.util.LinkedHashSet<>();
            String cardType = card.getCardType() == null ? "" : card.getCardType().trim();
            List<String> cardProperties = card.getCardProperties();
            Set<String> propertySet = new HashSet<>();
            if (cardProperties != null) {
                for (String property : cardProperties) {
                    if (property != null) {
                        propertySet.add(property.trim());
                    }
                }
            }

            if (cardType.toLowerCase().contains("trap")) {
                if (propertySet.contains("Counter")) {
                    desiredFrenchCategories.add("Pièges Contre");
                }
                if (propertySet.contains("Continuous")) {
                    desiredFrenchCategories.add("Pièges Continus");
                }
                if (propertySet.contains("Normal")) {
                    desiredFrenchCategories.add("Pièges Normaux");
                }
                desiredFrenchCategories.add("Pièges");
            }
            if (cardType.toLowerCase().contains("spell")) {
                if (propertySet.contains("Continuous")) {
                    desiredFrenchCategories.add("Magies Continues");
                }
                if (propertySet.contains("Quick-Play") || propertySet.contains("Quick Play")) {
                    desiredFrenchCategories.add("Magies Jeu-Rapide");
                }
                if (propertySet.contains("Equip")) {
                    desiredFrenchCategories.add("Magies Équipement");
                }
                if (propertySet.contains("Field")) {
                    desiredFrenchCategories.add("Magies Terrain");
                }
                if (propertySet.contains("Ritual")) {
                    desiredFrenchCategories.add("Magies Rituel");
                }
                if (propertySet.contains("Normal")) {
                    desiredFrenchCategories.add("Magies Normales");
                }
                desiredFrenchCategories.add("Magies");
            }
            if (cardType.toLowerCase().contains("monster")) {
                if (propertySet.contains("Effect")) {
                    desiredFrenchCategories.add("Monstres à Effet");
                }
                if (propertySet.contains("Tuner")) {
                    desiredFrenchCategories.add("Monstres Syntoniseurs");
                }
                if (propertySet.contains("Synchro")) {
                    desiredFrenchCategories.add("Monstres Synchro");
                }
                if (propertySet.contains("Pendulum")) {
                    desiredFrenchCategories.add("Monstres Pendule");
                }
                if (propertySet.contains("Fusion")) {
                    desiredFrenchCategories.add("Monstres Fusion");
                }
                if (propertySet.contains("Xyz")) {
                    desiredFrenchCategories.add("Monstres Xyz");
                }
                if (propertySet.contains("Link")) {
                    desiredFrenchCategories.add("Monstres Lien");
                }
                if (propertySet.contains("Ritual")) {
                    desiredFrenchCategories.add("Monstres Rituel");
                }
                if (propertySet.contains("Normal")) {
                    desiredFrenchCategories.add("Monstres Normaux");
                }
                if (propertySet.contains("Toon")) {
                    desiredFrenchCategories.add("Monstres Toon");
                }
                if (propertySet.contains("Flip")) {
                    desiredFrenchCategories.add("Monstres Flip");
                }
                if (propertySet.contains("Spirit")) {
                    desiredFrenchCategories.add("Monstres Spirit");
                }
                if (propertySet.contains("Union")) {
                    desiredFrenchCategories.add("Monstres Union");
                }
                if (propertySet.contains("Gemini")) {
                    desiredFrenchCategories.add("Monstres Gemini");
                }
                desiredFrenchCategories.add("Monstres");
            }

            if (desiredFrenchCategories.isEmpty()) {
                return items;
            }

            OwnedCardsCollection ownedCollection = loadOwnedCollection();
            if (ownedCollection == null || ownedCollection.getOwnedCollection() == null) {
                return items;
            }

            // Build a map from sanitised name → raw location strings.
            Map<String, List<String>> categoryToLocations = new LinkedHashMap<>();
            for (Box box : ownedCollection.getOwnedCollection()) {
                String rawBoxName = box.getName() == null ? "" : box.getName();
                String sanitisedBox = CardTreeCell.sanitizeDisplayName(rawBoxName).toLowerCase();
                categoryToLocations.computeIfAbsent(sanitisedBox, k -> new ArrayList<>())
                        .add(rawBoxName);
                if (box.getContent() != null) {
                    for (CardsGroup group : box.getContent()) {
                        String rawGroupName = group.getName() == null ? "" : group.getName();
                        String sanitisedGroup = CardTreeCell.sanitizeDisplayName(rawGroupName).toLowerCase();
                        categoryToLocations
                                .computeIfAbsent(sanitisedGroup, k -> new ArrayList<>())
                                .add(rawBoxName + "/" + rawGroupName);
                    }
                }
            }

            for (String desired : desiredFrenchCategories) {
                if (desired == null || desired.trim().isEmpty()) {
                    continue;
                }
                String desiredSanitised = CardTreeCell.sanitizeDisplayName(desired).toLowerCase();
                List<String> locations = categoryToLocations.get(desiredSanitised);
                if (locations == null || locations.isEmpty()) {
                    continue;
                }

                for (String rawLocation : locations) {
                    String[] locationParts = rawLocation.split("/", 2);
                    String boxRaw = locationParts.length > 0 ? locationParts[0] : "";
                    String groupRaw = locationParts.length > 1 ? locationParts[1] : null;

                    // Skip if the card is already in this location.
                    boolean alreadyThere = false;
                    for (Box box : ownedCollection.getOwnedCollection()) {
                        if (!CardTreeCell.sanitizeDisplayName(box.getName()).equalsIgnoreCase(
                                CardTreeCell.sanitizeDisplayName(boxRaw))) {
                            continue;
                        }
                        if (groupRaw == null) {
                            if (box.getContent() != null) {
                                for (CardsGroup group : box.getContent()) {
                                    if (countCardInList(group.getCardList(), card) > 0) {
                                        alreadyThere = true;
                                        break;
                                    }
                                }
                            }
                        } else {
                            if (box.getContent() != null) {
                                for (CardsGroup group : box.getContent()) {
                                    if (CardTreeCell.sanitizeDisplayName(group.getName())
                                            .equalsIgnoreCase(CardTreeCell.sanitizeDisplayName(groupRaw))) {
                                        if (countCardInList(group.getCardList(), card) > 0) {
                                            alreadyThere = true;
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                        if (alreadyThere) {
                            break;
                        }
                    }
                    if (alreadyThere) {
                        continue;
                    }

                    String displayBox = CardTreeCell.sanitizeDisplayName(boxRaw);
                    String displayTarget = groupRaw == null
                            ? displayBox
                            : displayBox + "/" + CardTreeCell.sanitizeDisplayName(groupRaw);
                    final String handlerTarget = displayTarget;

                    MenuItem mi = new MenuItem(displayTarget);
                    mi.setOnAction(ev -> MenuActionHandler.handleMove(clickedElement, handlerTarget));
                    items.add(mi);
                }
            }
        } catch (Exception buildError) {
            logger.debug("buildTypeBoxProposals failed", buildError);
        }
        return items;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Proposal helper utilities
    // ─────────────────────────────────────────────────────────────────────────

    private static int countCardInList(List<CardElement> cardList, Card card) {
        if (cardList == null || card == null) {
            return 0;
        }
        int count = 0;
        for (CardElement cardElement : cardList) {
            if (cardElement == null || cardElement.getCard() == null) {
                continue;
            }
            if (Utils.CardMatcher.cardsMatch(cardElement.getCard(), card)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isQualityUpgradeFor(
            List<CardElement> targetList, CardElement ownedElement) {
        if (ownedElement == null || targetList == null) {
            return false;
        }
        Card card = ownedElement.getCard();
        if (card == null) {
            return false;
        }
        List<CardElement> existingCopies = new ArrayList<>();
        for (CardElement existing : targetList) {
            if (existing == null || existing.getCard() == null) {
                continue;
            }
            if (Utils.CardMatcher.cardsMatch(existing.getCard(), card)) {
                existingCopies.add(existing);
            }
        }
        if (existingCopies.isEmpty()) {
            return false;
        }
        return Controller.CardQualityService.isQualityUpgrade(
                existingCopies, targetList, ownedElement);
    }

    private static int countInOwnedForDeckCombined(
            OwnedCardsCollection owned, String deckName, Card card) {
        if (owned == null || owned.getOwnedCollection() == null
                || deckName == null || deckName.trim().isEmpty()) {
            return 0;
        }
        String deckNorm = CardTreeCell.sanitizeDisplayName(deckName).toLowerCase();

        // 1) Box name matches deck name
        for (Box box : owned.getOwnedCollection()) {
            if (box == null) {
                continue;
            }
            if (CardTreeCell.sanitizeDisplayName(box.getName()).toLowerCase().equals(deckNorm)) {
                int sum = 0;
                if (box.getContent() != null) {
                    for (CardsGroup group : box.getContent()) {
                        if (group != null) {
                            sum += countCardInList(group.getCardList(), card);
                        }
                    }
                }
                return sum;
            }
        }

        // 2) Box contains a group named like the deck — sum only that group, not all groups
        // in the box. Summing all groups was Bug 3A: cards in unrelated groups like "Unsorted"
        // would inflate the count and hide a real deficit.
        for (Box box : owned.getOwnedCollection()) {
            if (box == null || box.getContent() == null) {
                continue;
            }
            int sum = 0;
            boolean hasDeckGroup = false;
            for (CardsGroup group : box.getContent()) {
                if (group == null) {
                    continue;
                }
                if (CardTreeCell.sanitizeDisplayName(group.getName()).toLowerCase()
                        .equals(deckNorm)) {
                    hasDeckGroup = true;
                    sum += countCardInList(group.getCardList(), card);
                }
            }
            if (hasDeckGroup) {
                return sum;
            }
        }

        // 3) Sum Main/Extra/Side Deck groups
        int sectionSum = 0;
        for (Box box : owned.getOwnedCollection()) {
            if (box == null || box.getContent() == null) {
                continue;
            }
            boolean boxMatchesDeck = CardTreeCell.sanitizeDisplayName(box.getName())
                    .toLowerCase().equals(deckNorm);
            boolean foundSectionInBox = false;
            for (CardsGroup group : box.getContent()) {
                if (group == null) {
                    continue;
                }
                String groupNorm = CardTreeCell.sanitizeDisplayName(group.getName()).toLowerCase();
                if (groupNorm.equals("main deck")
                        || groupNorm.equals("extra deck")
                        || groupNorm.equals("side deck")) {
                    sectionSum += countCardInList(group.getCardList(), card);
                    foundSectionInBox = true;
                }
            }
            if (boxMatchesDeck && foundSectionInBox) {
                return sectionSum;
            }
        }
        if (sectionSum > 0) {
            return sectionSum;
        }

        // 4) Fallback: any group named like the deck
        int fallback = 0;
        for (Box box : owned.getOwnedCollection()) {
            if (box == null || box.getContent() == null) {
                continue;
            }
            for (CardsGroup group : box.getContent()) {
                if (group != null && CardTreeCell.sanitizeDisplayName(group.getName())
                        .toLowerCase().equals(deckNorm)) {
                    fallback += countCardInList(group.getCardList(), card);
                }
            }
        }
        return fallback;
    }

    private static boolean locationExistsInOwned(String name, OwnedCardsCollection owned) {
        if (name == null || name.trim().isEmpty() || owned == null
                || owned.getOwnedCollection() == null) {
            return false;
        }
        String targetSan = CardTreeCell.sanitizeDisplayName(name).toLowerCase();
        for (Box box : owned.getOwnedCollection()) {
            if (box == null) {
                continue;
            }
            if (CardTreeCell.sanitizeDisplayName(box.getName()).toLowerCase().equals(targetSan)) {
                return true;
            }
            if (box.getContent() != null) {
                for (CardsGroup group : box.getContent()) {
                    if (group != null && CardTreeCell.sanitizeDisplayName(group.getName())
                            .toLowerCase().equals(targetSan)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Shared styling utilities
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a styled {@link ContextMenu} matching the application's dark theme.
     */
    private static ContextMenu styledContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.setStyle(
                "-fx-background-color: #100317; "
                        + "-fx-background-radius: 6; "
                        + "-fx-border-color: #3a3a3a; "
                        + "-fx-border-radius: 6; "
                        + "-fx-border-width: 1;");
        return contextMenu;
    }

    /**
     * Creates a lime-accented menu item with the given label text.
     * Used for most non-destructive actions.
     */
    private static MenuItem accentItem(String labelText) {
        MenuItem mi = new MenuItem();
        Label label = new Label(labelText);
        label.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
        HBox graphic = new HBox(label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(graphic);
        mi.setText("");
        return mi;
    }

    /**
     * Creates a red "Remove" menu item with a trash-can icon.
     * Used for destructive remove actions.
     */
    private static MenuItem redRemoveItem() {
        MenuItem mi = new MenuItem();
        Label trashIcon = new Label("\uD83D\uDDD1");
        trashIcon.setStyle("-fx-text-fill: #ff4d4d; -fx-font-size: 13;");
        Label removeLabel = new Label("Remove");
        removeLabel.setStyle("-fx-text-fill: #ff4d4d; -fx-font-weight: bold;");
        HBox graphic = new HBox(6, trashIcon, removeLabel);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(graphic);
        mi.setText("");
        return mi;
    }

    private static MenuItem disabledItem(String text) {
        MenuItem mi = new MenuItem(text);
        mi.setDisable(true);
        return mi;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data-loading helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static OwnedCardsCollection loadOwnedCollection() {
        OwnedCardsCollection owned = null;
        try {
            owned = Model.CardsLists.OuicheList.getMyCardsCollection();
        } catch (Throwable ignored) {
        }
        if (owned == null) {
            try {
                UserInterfaceFunctions.loadCollectionFile();
            } catch (Throwable ignored) {
            }
            try {
                owned = Model.CardsLists.OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
        }
        return owned;
    }

    private static DecksAndCollectionsList ensureDacList() {
        DecksAndCollectionsList dacList = UserInterfaceFunctions.getDecksList();
        if (dacList == null) {
            try {
                UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
            } catch (Exception ignored) {
            }
            dacList = UserInterfaceFunctions.getDecksList();
        }
        return dacList;
    }
}