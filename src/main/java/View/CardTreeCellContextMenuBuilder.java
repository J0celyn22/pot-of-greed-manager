//package View;
//
//import Controller.CardGroupRegistry;
//import Controller.UserInterfaceFunctions;
//import Model.CardsLists.*;
//import javafx.geometry.Insets;
//import javafx.geometry.Pos;
//import javafx.scene.control.ContextMenu;
//import javafx.scene.control.Label;
//import javafx.scene.control.MenuItem;
//import javafx.scene.control.SeparatorMenuItem;
//import javafx.scene.layout.HBox;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.ArrayList;
//import java.util.Iterator;
//import java.util.List;
//
/// **
// * CardTreeCellContextMenuBuilder — static factory that constructs the {@link ContextMenu}
// * objects displayed on tree-level header items (Deck nodes, ThemeCollection nodes, and
// * owned CardsGroup nodes) inside the middle-pane {@link javafx.scene.control.TreeView}.
// *
// * <p>This covers context menus that appear when the user right-clicks a Deck or Collection
// * <em>header row</em> (not on an individual card inside a {@link CardGridCell}).
// * Per-card menus are handled by {@link CardGridCellContextMenuBuilder}.</p>
// *
// * <p>All delete / structural operations are implemented inline here rather than delegating
// * to {@code MenuActionHandler}, because the required handler methods do not exist in the
// * current {@code MenuActionHandler} API.</p>
// */
//public final class CardTreeCellContextMenuBuilder {
//
//    private static final Logger logger =
//            LoggerFactory.getLogger(CardTreeCellContextMenuBuilder.class);
//
//    private CardTreeCellContextMenuBuilder() {
//    }
//
//    // ─────────────────────────────────────────────────────────────────────────
//    // Public factory methods
//    // ─────────────────────────────────────────────────────────────────────────
//
//    /**
//     * Builds the context menu for a Deck header node.
//     *
//     * <p>Items: Rename | Sort by Name | Sort by Type | Clear deck | Delete Deck.</p>
//     *
//     * @param deck      the Deck whose header was right-clicked
//     * @param ownerCell the {@link CardTreeCell} hosting the header row (needed for rename)
//     * @return a fully wired {@link ContextMenu}
//     */
//    public static ContextMenu buildDeckHeaderContextMenu(
//            Deck deck, CardTreeCell ownerCell) {
//
//        ContextMenu contextMenu = styledContextMenu();
//
//        MenuItem renameMenuItem = accentItem("Rename");
//        renameMenuItem.setOnAction(event -> {
//            if (deck != null && ownerCell != null) {
//                CardTreeCell.showRenamePopup(ownerCell, deck.getName(), (String newName) -> {
//                    if (newName != null && !newName.trim().isEmpty()) {
//                        deck.setName(newName.trim());
//                        Object deckOwner = CardGroupRegistry.findOwnerForGroup(
//                                CardGroupRegistry.getDeckSectionGroup(deck, "main"));
//                        if (deckOwner == null) {
//                            deckOwner = deck;
//                        }
//                        UserInterfaceFunctions.markDirty(deckOwner);
//                        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
//                        UserInterfaceFunctions.triggerDecksStructureRefresh();
//                    }
//                });
//            }
//        });
//
//        MenuItem sortByNameMenuItem = accentItem("Sort cards by Name");
//        sortByNameMenuItem.setOnAction(event -> {
//            if (deck != null) {
//                sortDeckSectionsByName(deck);
//                UserInterfaceFunctions.markDirty(deck);
//                UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
//                UserInterfaceFunctions.refreshDecksAndCollectionsView();
//            }
//        });
//
//        MenuItem sortByTypeMenuItem = accentItem("Sort cards by Type");
//        sortByTypeMenuItem.setOnAction(event -> {
//            if (deck != null) {
//                sortDeckSectionsByType(deck);
//                UserInterfaceFunctions.markDirty(deck);
//                UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
//                UserInterfaceFunctions.refreshDecksAndCollectionsView();
//            }
//        });
//
//        MenuItem clearDeckMenuItem = warnItem("Clear deck");
//        clearDeckMenuItem.setOnAction(event -> {
//            if (deck != null) {
//                clearDeckSections(deck);
//                UserInterfaceFunctions.markDirty(deck);
//                UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
//                UserInterfaceFunctions.refreshDecksAndCollectionsView();
//            }
//        });
//
//        MenuItem deleteDeckMenuItem = redDestructiveItem("Delete Deck");
//        deleteDeckMenuItem.setOnAction(event -> {
//            if (deck != null) {
//                deleteDeck(deck);
//            }
//        });
//
//        contextMenu.getItems().addAll(
//                renameMenuItem,
//                new SeparatorMenuItem(),
//                sortByNameMenuItem,
//                sortByTypeMenuItem,
//                new SeparatorMenuItem(),
//                clearDeckMenuItem,
//                deleteDeckMenuItem
//        );
//
//        return contextMenu;
//    }
//
//    /**
//     * Builds the context menu for a ThemeCollection header node.
//     *
//     * <p>Items: Rename | Sort cards by Name | Add New Deck | Delete Collection.</p>
//     *
//     * @param themeCollection the ThemeCollection whose header was right-clicked
//     * @param ownerCell       the {@link CardTreeCell} hosting the header row
//     * @return a fully wired {@link ContextMenu}
//     */
//    public static ContextMenu buildCollectionHeaderContextMenu(
//            ThemeCollection themeCollection, CardTreeCell ownerCell) {
//
//        ContextMenu contextMenu = styledContextMenu();
//
//        MenuItem renameMenuItem = accentItem("Rename");
//        renameMenuItem.setOnAction(event -> {
//            if (themeCollection != null && ownerCell != null) {
//                CardTreeCell.showRenamePopup(ownerCell, themeCollection.getName(), (String newName) -> {
//                    if (newName != null && !newName.trim().isEmpty()) {
//                        themeCollection.setName(newName.trim());
//                        UserInterfaceFunctions.markDirty(themeCollection);
//                        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
//                        UserInterfaceFunctions.triggerDecksStructureRefresh();
//                    }
//                });
//            }
//        });
//
//        MenuItem sortByNameMenuItem = accentItem("Sort cards by Name");
//        sortByNameMenuItem.setOnAction(event -> {
//            if (themeCollection != null) {
//                sortCollectionCardsByName(themeCollection);
//                UserInterfaceFunctions.markDirty(themeCollection);
//                UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
//                UserInterfaceFunctions.refreshDecksAndCollectionsView();
//            }
//        });
//
//        MenuItem addDeckMenuItem = accentItem("Add New Deck");
//        addDeckMenuItem.setOnAction(event -> {
//            if (themeCollection != null) {
//                addNewDeckToCollection(themeCollection);
//            }
//        });
//
//        MenuItem deleteCollectionMenuItem = redDestructiveItem("Delete Collection");
//        deleteCollectionMenuItem.setOnAction(event -> {
//            if (themeCollection != null) {
//                deleteCollection(themeCollection);
//            }
//        });
//
//        contextMenu.getItems().addAll(
//                renameMenuItem,
//                new SeparatorMenuItem(),
//                sortByNameMenuItem,
//                new SeparatorMenuItem(),
//                addDeckMenuItem,
//                new SeparatorMenuItem(),
//                deleteCollectionMenuItem
//        );
//
//        return contextMenu;
//    }
//
//    /**
//     * Builds the context menu for a CardsGroup header row inside the My Collection tab.
//     *
//     * <p>Items: Rename | Sort by Name | Sort by Type | Clear Group | Delete Group.</p>
//     *
//     * @param group     the CardsGroup whose header was right-clicked
//     * @param ownerCell the {@link CardTreeCell} hosting the header row
//     * @return a fully wired {@link ContextMenu}
//     */
//    public static ContextMenu buildOwnedGroupHeaderContextMenu(
//            CardsGroup group, CardTreeCell ownerCell) {
//
//        ContextMenu contextMenu = styledContextMenu();
//
//        MenuItem renameMenuItem = accentItem("Rename");
//        renameMenuItem.setOnAction(event -> {
//            if (group != null && ownerCell != null) {
//                CardTreeCell.showRenamePopup(ownerCell, group.getName(), (String newName) -> {
//                    if (newName != null && !newName.trim().isEmpty()) {
//                        group.setName(newName.trim());
//                        UserInterfaceFunctions.markMyCollectionDirty();
//                        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
//                        UserInterfaceFunctions.refreshOwnedCollectionView();
//                    }
//                });
//            }
//        });
//
//        MenuItem sortByNameMenuItem = accentItem("Sort cards by Name");
//        sortByNameMenuItem.setOnAction(event -> {
//            if (group != null) {
//                sortSectionByName(group);
//                UserInterfaceFunctions.markMyCollectionDirty();
//                UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
//                UserInterfaceFunctions.refreshOwnedCollectionView();
//            }
//        });
//
//        MenuItem sortByTypeMenuItem = accentItem("Sort cards by Type");
//        sortByTypeMenuItem.setOnAction(event -> {
//            if (group != null) {
//                sortSectionByType(group);
//                UserInterfaceFunctions.markMyCollectionDirty();
//                UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
//                UserInterfaceFunctions.refreshOwnedCollectionView();
//            }
//        });
//
//        MenuItem clearGroupMenuItem = warnItem("Clear Group");
//        clearGroupMenuItem.setOnAction(event -> {
//            if (group != null) {
//                clearCardsGroupObservableList(group);
//                UserInterfaceFunctions.markMyCollectionDirty();
//                UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
//                UserInterfaceFunctions.refreshOwnedCollectionView();
//            }
//        });
//
//        MenuItem deleteGroupMenuItem = redDestructiveItem("Delete Group");
//        deleteGroupMenuItem.setOnAction(event -> {
//            if (group != null) {
//                deleteCardsGroupFromOwnedCollection(group);
//            }
//        });
//
//        contextMenu.getItems().addAll(
//                renameMenuItem,
//                new SeparatorMenuItem(),
//                sortByNameMenuItem,
//                sortByTypeMenuItem,
//                new SeparatorMenuItem(),
//                clearGroupMenuItem,
//                deleteGroupMenuItem
//        );
//
//        return contextMenu;
//    }
//
//    // ─────────────────────────────────────────────────────────────────────────
//    // Structural operations (inline implementations)
//    // ─────────────────────────────────────────────────────────────────────────
//
//    /**
//     * Removes {@code deck} from its owner ({@link ThemeCollection} linked decks or the
//     * standalone {@link DecksAndCollectionsList#getDecks()} list), clears its registry
//     * entries, marks the owner dirty, and triggers a full D&amp;C structure refresh.
//     */
//    private static void deleteDeck(Deck deck) {
//        if (deck == null) {
//            return;
//        }
//        DecksAndCollectionsList dacList = UserInterfaceFunctions.getDecksList();
//        if (dacList == null) {
//            logger.warn("deleteDeck: DecksAndCollectionsList is null — cannot delete '{}'",
//                    deck.getName());
//            return;
//        }
//
//        Object dirtiedOwner = null;
//
//        // 1) Search linked decks inside ThemeCollections.
//        if (dacList.getCollections() != null) {
//            searchCollections:
//            for (ThemeCollection themeCollection : dacList.getCollections()) {
//                if (themeCollection == null
//                        || themeCollection.getLinkedDecks() == null) {
//                    continue;
//                }
//                for (List<Deck> deckUnit : themeCollection.getLinkedDecks()) {
//                    if (deckUnit == null) {
//                        continue;
//                    }
//                    Iterator<Deck> deckIterator = deckUnit.iterator();
//                    while (deckIterator.hasNext()) {
//                        Deck candidate = deckIterator.next();
//                        if (candidate == deck) {
//                            deckIterator.remove();
//                            dirtiedOwner = themeCollection;
//                            break searchCollections;
//                        }
//                    }
//                }
//            }
//        }
//
//        // 2) Search standalone decks.
//        if (dirtiedOwner == null && dacList.getDecks() != null) {
//            Iterator<Deck> standaloneIterator = dacList.getDecks().iterator();
//            while (standaloneIterator.hasNext()) {
//                if (standaloneIterator.next() == deck) {
//                    standaloneIterator.remove();
//                    dirtiedOwner = dacList;
//                    break;
//                }
//            }
//        }
//
//        // 3) Clean up registry entries.
//        CardGroupRegistry.DECK_SECTION_GROUPS.remove(deck);
//
//        // 4) Mark dirty and refresh.
//        if (dirtiedOwner instanceof ThemeCollection) {
//            UserInterfaceFunctions.markDirty(dirtiedOwner);
//        } else {
//            UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
//        }
//        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
//        UserInterfaceFunctions.triggerDecksStructureRefresh();
//    }
//
//    /**
//     * Creates a new empty {@link Deck} with a default name, adds it to
//     * {@code themeCollection}'s linked-decks list (creating the outer list if absent),
//     * marks the collection dirty, and triggers a full D&amp;C structure refresh so the new
//     * deck node appears in the tree immediately.
//     */
//    private static void addNewDeckToCollection(ThemeCollection themeCollection) {
//        if (themeCollection == null) {
//            return;
//        }
//        Deck newDeck = new Deck();
//        newDeck.setName("New Deck");
//        newDeck.setMainDeck(new ArrayList<>());
//        newDeck.setExtraDeck(new ArrayList<>());
//        newDeck.setSideDeck(new ArrayList<>());
//
//        if (themeCollection.getLinkedDecks() == null) {
//            themeCollection.setLinkedDecks(new ArrayList<>());
//        }
//        List<Deck> newUnit = new ArrayList<>();
//        newUnit.add(newDeck);
//        themeCollection.getLinkedDecks().add(newUnit);
//
//        UserInterfaceFunctions.markDirty(themeCollection);
//        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
//        UserInterfaceFunctions.triggerDecksStructureRefresh();
//    }
//
//    /**
//     * Removes {@code themeCollection} from the {@link DecksAndCollectionsList}, removes
//     * all its deck registry entries, marks the list dirty, and triggers a full structure
//     * refresh.
//     */
//    private static void deleteCollection(ThemeCollection themeCollection) {
//        if (themeCollection == null) {
//            return;
//        }
//        DecksAndCollectionsList dacList = UserInterfaceFunctions.getDecksList();
//        if (dacList == null || dacList.getCollections() == null) {
//            logger.warn("deleteCollection: list unavailable — cannot delete '{}'",
//                    themeCollection.getName());
//            return;
//        }
//
//        boolean removed = dacList.getCollections().remove(themeCollection);
//        if (!removed) {
//            logger.warn("deleteCollection: '{}' not found in collections list",
//                    themeCollection.getName());
//            return;
//        }
//
//        // Remove deck registry entries for all linked decks.
//        if (themeCollection.getLinkedDecks() != null) {
//            for (List<Deck> deckUnit : themeCollection.getLinkedDecks()) {
//                if (deckUnit == null) {
//                    continue;
//                }
//                for (Deck deck : deckUnit) {
//                    if (deck != null) {
//                        CardGroupRegistry.DECK_SECTION_GROUPS.remove(deck);
//                    }
//                }
//            }
//        }
//        CardGroupRegistry.COLLECTION_CARDS_GROUPS.remove(themeCollection);
//        CardGroupRegistry.COLLECTION_EXCEPTIONS_GROUPS.remove(themeCollection);
//
//        UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
//        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
//        UserInterfaceFunctions.triggerDecksStructureRefresh();
//    }
//
//    /**
//     * Removes {@code group} from whichever Box or sub-Box in My Collection currently
//     * contains it, then removes its registry entries, marks My Collection dirty, and
//     * triggers a view refresh.
//     */
//    private static void deleteCardsGroupFromOwnedCollection(CardsGroup group) {
//        if (group == null) {
//            return;
//        }
//        try {
//            Model.CardsLists.OwnedCardsCollection ownedCollection = null;
//            try {
//                ownedCollection = Model.CardsLists.OuicheList.getMyCardsCollection();
//            } catch (Throwable ignored) {
//            }
//            if (ownedCollection == null
//                    || ownedCollection.getOwnedCollection() == null) {
//                logger.warn("deleteCardsGroupFromOwnedCollection: owned collection unavailable");
//                return;
//            }
//
//            boolean removedFromCollection = false;
//            searchBoxes:
//            for (Model.CardsLists.Box box : ownedCollection.getOwnedCollection()) {
//                if (box == null) {
//                    continue;
//                }
//                if (box.getContent() != null) {
//                    Iterator<CardsGroup> groupIterator = box.getContent().iterator();
//                    while (groupIterator.hasNext()) {
//                        if (groupIterator.next() == group) {
//                            groupIterator.remove();
//                            removedFromCollection = true;
//                            break searchBoxes;
//                        }
//                    }
//                }
//                if (box.getSubBoxes() != null) {
//                    for (Model.CardsLists.Box subBox : box.getSubBoxes()) {
//                        if (subBox == null || subBox.getContent() == null) {
//                            continue;
//                        }
//                        Iterator<CardsGroup> subGroupIterator = subBox.getContent().iterator();
//                        while (subGroupIterator.hasNext()) {
//                            if (subGroupIterator.next() == group) {
//                                subGroupIterator.remove();
//                                removedFromCollection = true;
//                                break searchBoxes;
//                            }
//                        }
//                    }
//                }
//            }
//
//            if (!removedFromCollection) {
//                logger.warn("deleteCardsGroupFromOwnedCollection: group '{}' not found in any box",
//                        group.getName());
//            }
//
//            // Remove from registries regardless of whether we found it in the tree.
//            CardGroupRegistry.GROUP_OBSERVABLE_LISTS.remove(group);
//            CardGroupRegistry.GROUP_GRID_VIEWS.remove(group);
//            CardGroupRegistry.GROUP_FILTERED_LISTS.remove(group);
//            CardGroupRegistry.MISSING_ARTWORK_SETS.remove(group);
//
//        } catch (Throwable deleteError) {
//            logger.debug("deleteCardsGroupFromOwnedCollection failed", deleteError);
//        }
//
//        UserInterfaceFunctions.markMyCollectionDirty();
//        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
//        UserInterfaceFunctions.refreshOwnedCollectionView();
//    }
//
//    // ─────────────────────────────────────────────────────────────────────────
//    // Sort helpers
//    // ─────────────────────────────────────────────────────────────────────────
//
//    /**
//     * Sorts all three sections of {@code deck} (main, extra, side) alphabetically by
//     * the card's English name, using the CardGroupRegistry observable lists so that all
//     * live GridViews update automatically.
//     */
//    private static void sortDeckSectionsByName(Deck deck) {
//        if (deck == null) {
//            return;
//        }
//        sortSectionByName(CardGroupRegistry.getDeckSectionGroup(deck, "main"));
//        sortSectionByName(CardGroupRegistry.getDeckSectionGroup(deck, "extra"));
//        sortSectionByName(CardGroupRegistry.getDeckSectionGroup(deck, "side"));
//    }
//
//    private static void sortSectionByName(CardsGroup group) {
//        if (group == null) {
//            return;
//        }
//        try {
//            List<CardElement> elements = CardGroupRegistry.observableListFor(group);
//            elements.sort((elementA, elementB) -> {
//                String nameA = elementA != null && elementA.getCard() != null
//                        ? elementA.getCard().getName_EN() : "";
//                String nameB = elementB != null && elementB.getCard() != null
//                        ? elementB.getCard().getName_EN() : "";
//                if (nameA == null) {
//                    nameA = "";
//                }
//                if (nameB == null) {
//                    nameB = "";
//                }
//                return nameA.compareToIgnoreCase(nameB);
//            });
//            CardGroupRegistry.triggerHeightAdjustment(group);
//        } catch (Exception sortError) {
//            logger.debug("sortSectionByName failed for group '{}'",
//                    group.getName(), sortError);
//        }
//    }
//
//    /**
//     * Sorts all three sections of {@code deck} by card type priority
//     * (Monster → Spell → Trap), then alphabetically within each type.
//     */
//    private static void sortDeckSectionsByType(Deck deck) {
//        if (deck == null) {
//            return;
//        }
//        sortSectionByType(CardGroupRegistry.getDeckSectionGroup(deck, "main"));
//        sortSectionByType(CardGroupRegistry.getDeckSectionGroup(deck, "extra"));
//        sortSectionByType(CardGroupRegistry.getDeckSectionGroup(deck, "side"));
//    }
//
//    private static void sortSectionByType(CardsGroup group) {
//        if (group == null) {
//            return;
//        }
//        try {
//            List<CardElement> elements = CardGroupRegistry.observableListFor(group);
//            elements.sort((elementA, elementB) -> {
//                int priorityA = cardTypePriority(elementA);
//                int priorityB = cardTypePriority(elementB);
//                if (priorityA != priorityB) {
//                    return Integer.compare(priorityA, priorityB);
//                }
//                String nameA = elementA != null && elementA.getCard() != null
//                        ? elementA.getCard().getName_EN() : "";
//                String nameB = elementB != null && elementB.getCard() != null
//                        ? elementB.getCard().getName_EN() : "";
//                if (nameA == null) {
//                    nameA = "";
//                }
//                if (nameB == null) {
//                    nameB = "";
//                }
//                return nameA.compareToIgnoreCase(nameB);
//            });
//            CardGroupRegistry.triggerHeightAdjustment(group);
//        } catch (Exception sortError) {
//            logger.debug("sortSectionByType failed for group '{}'",
//                    group.getName(), sortError);
//        }
//    }
//
//    /**
//     * Returns a sort priority integer for a {@link CardElement} based on its card type.
//     * Monster = 0, Spell = 1, Trap = 2, unknown = 3.
//     */
//    private static int cardTypePriority(CardElement cardElement) {
//        if (cardElement == null || cardElement.getCard() == null) {
//            return 3;
//        }
//        String cardType = cardElement.getCard().getCardType();
//        if (cardType == null) {
//            return 3;
//        }
//        String lowerType = cardType.toLowerCase(java.util.Locale.ROOT);
//        if (lowerType.contains("monster")) {
//            return 0;
//        }
//        if (lowerType.contains("spell")) {
//            return 1;
//        }
//        if (lowerType.contains("trap")) {
//            return 2;
//        }
//        return 3;
//    }
//
//    /**
//     * Sorts the cards list of a {@link ThemeCollection} alphabetically by English name.
//     */
//    private static void sortCollectionCardsByName(ThemeCollection themeCollection) {
//        if (themeCollection == null) {
//            return;
//        }
//        CardsGroup cardsGroup = CardGroupRegistry.getCollectionCardsGroup(themeCollection);
//        if (cardsGroup != null) {
//            sortSectionByName(cardsGroup);
//        } else if (themeCollection.getCardsList() != null) {
//            themeCollection.getCardsList().sort((elementA, elementB) -> {
//                String nameA = elementA != null && elementA.getCard() != null
//                        ? elementA.getCard().getName_EN() : "";
//                String nameB = elementB != null && elementB.getCard() != null
//                        ? elementB.getCard().getName_EN() : "";
//                if (nameA == null) {
//                    nameA = "";
//                }
//                if (nameB == null) {
//                    nameB = "";
//                }
//                return nameA.compareToIgnoreCase(nameB);
//            });
//        }
//    }
//
//    // ─────────────────────────────────────────────────────────────────────────
//    // Clear helpers
//    // ─────────────────────────────────────────────────────────────────────────
//
//    /**
//     * Removes all cards from all three sections of {@code deck} via the observable lists
//     * so that all live GridViews update automatically.
//     */
//    private static void clearDeckSections(Deck deck) {
//        if (deck == null) {
//            return;
//        }
//        clearSectionGroup(CardGroupRegistry.getDeckSectionGroup(deck, "main"));
//        clearSectionGroup(CardGroupRegistry.getDeckSectionGroup(deck, "extra"));
//        clearSectionGroup(CardGroupRegistry.getDeckSectionGroup(deck, "side"));
//    }
//
//    private static void clearSectionGroup(CardsGroup group) {
//        if (group == null) {
//            return;
//        }
//        CardGroupRegistry.observableListFor(group).clear();
//        CardGroupRegistry.triggerHeightAdjustment(group);
//    }
//
//    /**
//     * Removes all cards from a {@link CardsGroup} via its observable list.
//     */
//    private static void clearCardsGroupObservableList(CardsGroup group) {
//        if (group == null) {
//            return;
//        }
//        CardGroupRegistry.observableListFor(group).clear();
//        CardGroupRegistry.triggerHeightAdjustment(group);
//    }
//
//    // ─────────────────────────────────────────────────────────────────────────
//    // Shared styling utilities
//    // ─────────────────────────────────────────────────────────────────────────
//
//    /**
//     * Creates a styled {@link ContextMenu} matching the application's dark theme.
//     */
//    private static ContextMenu styledContextMenu() {
//        ContextMenu contextMenu = new ContextMenu();
//        contextMenu.setStyle(
//                "-fx-background-color: #100317; "
//                        + "-fx-background-radius: 6; "
//                        + "-fx-border-color: #3a3a3a; "
//                        + "-fx-border-radius: 6; "
//                        + "-fx-border-width: 1;");
//        return contextMenu;
//    }
//
//    /**
//     * Creates a lime-accented menu item for standard (non-destructive) actions.
//     */
//    private static MenuItem accentItem(String labelText) {
//        MenuItem mi = new MenuItem();
//        Label label = new Label(labelText);
//        label.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
//        HBox graphic = new HBox(label);
//        graphic.setAlignment(Pos.CENTER_LEFT);
//        graphic.setPadding(new Insets(2, 6, 2, 6));
//        mi.setGraphic(graphic);
//        mi.setText("");
//        return mi;
//    }
//
//    /**
//     * Creates an orange-tinted menu item for potentially destructive but reversible
//     * actions (e.g. "Clear deck").
//     */
//    private static MenuItem warnItem(String labelText) {
//        MenuItem mi = new MenuItem();
//        Label label = new Label(labelText);
//        label.setStyle("-fx-text-fill: #EB9E34; -fx-font-size: 13;");
//        HBox graphic = new HBox(label);
//        graphic.setAlignment(Pos.CENTER_LEFT);
//        graphic.setPadding(new Insets(2, 6, 2, 6));
//        mi.setGraphic(graphic);
//        mi.setText("");
//        return mi;
//    }
//
//    /**
//     * Creates a red menu item for irreversible destructive actions (e.g. "Delete Deck").
//     */
//    private static MenuItem redDestructiveItem(String labelText) {
//        MenuItem mi = new MenuItem();
//        Label label = new Label(labelText);
//        label.setStyle("-fx-text-fill: #ff4d4d; -fx-font-weight: bold; -fx-font-size: 13;");
//        HBox graphic = new HBox(label);
//        graphic.setAlignment(Pos.CENTER_LEFT);
//        graphic.setPadding(new Insets(2, 6, 2, 6));
//        mi.setGraphic(graphic);
//        mi.setText("");
//        return mi;
//    }
//}