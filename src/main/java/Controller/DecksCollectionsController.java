package Controller;

import Model.CardsLists.*;
import Utils.CardNameUtils;
import View.*;
import View.SharedCollectionTab.TabType;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

/**
 * DecksCollectionsController — manages all display and navigation logic for the
 * "Decks and Collections" tab.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Building and refreshing the Decks & Collections {@link TreeView}.</li>
 *   <li>Populating the left-hand navigation menu with collections and decks.</li>
 *   <li>Wiring navigation drag-and-drop for decks and collections.</li>
 *   <li>Starting inline renames for newly created decks, collections, and the
 *       "Create Collection from Deck" flow.</li>
 *   <li>Tree-item builders for {@link Deck} and {@link ThemeCollection}.</li>
 *   <li>Scroll helpers for the Decks tree.</li>
 * </ul>
 *
 * <p>{@link DeckCollectionQualityChecks} holds the quality-check and
 * identifier-presence query helpers (missing cards, missing artworks,
 * konami/passcode lookups) that used to live here — they never touched this
 * class's TreeView or coordinator state, so they moved out as plain static
 * utilities.</p>
 */
public class DecksCollectionsController {

    /**
     * Sentinel prepended to {@link CardsGroup} names that represent archetype groups.
     */
    public static final String ARCHETYPE_MARKER = "[ARCHETYPE]";
    private static final Logger logger =
            LoggerFactory.getLogger(DecksCollectionsController.class);

    // ── Static display flag ───────────────────────────────────────────────────

    /**
     * When {@code true}, the "Archetypes" and "Cards not to add" (Exceptions) sections
     * are omitted from every collection's tree node in the Decks &amp; Collections tab.
     * Defaults to {@code false} so both sections are visible by default.
     */
    private static boolean hideArchetypesEnabled = false;

    /**
     * Returns whether archetype/exception hiding is currently active.
     */
    public static boolean isHideArchetypesEnabled() {
        return hideArchetypesEnabled;
    }

    /**
     * Enables or disables archetype/exception hiding.
     * After changing this flag the caller must trigger a full tree rebuild so that
     * the sections appear or disappear.
     *
     * @param enabled {@code true} to hide archetypes &amp; exceptions, {@code false} to show them
     */
    public static void setHideArchetypesEnabled(boolean enabled) {
        hideArchetypesEnabled = enabled;
    }

    // ── Injected shared state ─────────────────────────────────────────────────
    private final DoubleProperty cardWidthProperty;
    private final DoubleProperty cardHeightProperty;
    private final SharedCollectionTab decksTab;
    private final RealMainController coordinator;

    // ── Live tree view ────────────────────────────────────────────────────────

    private TreeView<String> decksAndCollectionsTreeView;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates a DecksCollectionsController.
     * No refreshers are auto-registered here; they are registered on-demand when
     * the user first switches to the Decks tab (see {@code initialize()} in the coordinator).
     *
     * @param coordinator        the thin coordinator
     * @param cardWidthProperty  shared card-width property
     * @param cardHeightProperty shared card-height property
     * @param decksTab           the tab UI container for Decks and Collections
     */
    public DecksCollectionsController(RealMainController coordinator,
                                      DoubleProperty cardWidthProperty,
                                      DoubleProperty cardHeightProperty,
                                      SharedCollectionTab decksTab) {
        this.coordinator = coordinator;
        this.cardWidthProperty = cardWidthProperty;
        this.cardHeightProperty = cardHeightProperty;
        this.decksTab = decksTab;
    }

    // ── Display ───────────────────────────────────────────────────────────────

    /**
     * Builds and installs a new Decks & Collections {@link TreeView} into the tab's
     * content pane.
     *
     * @throws Exception if the DecksAndCollectionsList is null after loading
     */
    public void displayDecksAndCollections() throws Exception {
        AnchorPane contentPane = decksTab.getContentPane();
        contentPane.getChildren().clear();

        if (UserInterfaceFunctions.getDecksList() == null) {
            UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
        }
        DecksAndCollectionsList decksCollection = UserInterfaceFunctions.getDecksList();
        if (decksCollection == null) {
            throw new Exception(
                    "DecksAndCollectionsList is null. Please check the decks folder path.");
        }

        DataTreeItem<Object> rootItem =
                new DataTreeItem<>("Decks and Collections", "ROOT");
        rootItem.setExpanded(true);

        if (decksCollection.getCollections() != null) {
            for (ThemeCollection collection : decksCollection.getCollections()) {
                DataTreeItem<Object> collectionItem =
                        createThemeCollectionTreeItem(collection, TabType.DECKS);
                rootItem.getChildren().add(collectionItem);
            }
        }

        if (decksCollection.getDecks() != null) {
            for (Deck deck : decksCollection.getDecks()) {
                DataTreeItem<Object> deckItem = createDeckTreeItem(deck);
                rootItem.getChildren().add(deckItem);
            }
        }

        decksAndCollectionsTreeView = new TreeView<>(rootItem);
        decksAndCollectionsTreeView.setUserData("DECKS_COLLECTIONS");
        decksAndCollectionsTreeView.setCellFactory(
                param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        decksAndCollectionsTreeView.setStyle("-fx-background-color: #100317;");
        decksAndCollectionsTreeView.setShowRoot(false);
        decksAndCollectionsTreeView.addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                coordinator.buildMiddlePaneEmptySpaceFilter());

        contentPane.getChildren().add(decksAndCollectionsTreeView);
        AnchorPane.setTopAnchor(decksAndCollectionsTreeView, 0.0);
        AnchorPane.setBottomAnchor(decksAndCollectionsTreeView, 0.0);
        AnchorPane.setLeftAnchor(decksAndCollectionsTreeView, 0.0);
        AnchorPane.setRightAnchor(decksAndCollectionsTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        decksAndCollectionsTreeView.getStylesheets()
                .add(new File(stylesheetPath).toURI().toString());

        coordinator.setDecksAndCollectionsTreeView(decksAndCollectionsTreeView);

        logger.info("Decks and Collections displayed using the unified layout.");
    }

    // ── Navigation menu ───────────────────────────────────────────────────────

    /**
     * Rebuilds the left-hand navigation menu for Decks & Collections from the model.
     *
     * @throws Exception if the model cannot be loaded
     */
    public void populateDecksAndCollectionsMenu() throws Exception {
        VBox menuVBox = decksTab.getMenuVBox();

        // Snapshot expanded state before the rebuild.
        Map<Object, Boolean> expandedState = new IdentityHashMap<>();
        for (Node node : menuVBox.getChildren()) {
            if (node instanceof NavigationMenu) {
                for (NavigationItem item : ((NavigationMenu) node).getItems()) {
                    captureExpandedState(item, expandedState);
                }
            } else if (node instanceof NavigationItem) {
                captureExpandedState((NavigationItem) node, expandedState);
            }
        }

        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        if (UserInterfaceFunctions.getDecksList() == null) {
            UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
        }
        DecksAndCollectionsList decksCollection = UserInterfaceFunctions.getDecksList();

        if (decksCollection != null) {
            buildCollectionNavItems(decksCollection, navigationMenu, expandedState);
            buildStandaloneDeckNavItems(decksCollection, navigationMenu);
        } else {
            Label errorLabel = new Label("No Decks and Collections loaded.");
            errorLabel.setStyle("-fx-text-fill: white;");
            navigationMenu.addItem(NavigationHelper.createNavigationItem(
                    errorLabel.getText(), 0));
        }

        menuVBox.getChildren().add(navigationMenu);

        ContextMenu emptyAreaContextMenu = DecksNavMenuBuilder.forDecksEmpty();
        menuVBox.setOnContextMenuRequested(event ->
                emptyAreaContextMenu.show(menuVBox, event.getScreenX(), event.getScreenY()));
    }

    private void buildCollectionNavItems(DecksAndCollectionsList decksCollection,
                                         NavigationMenu navigationMenu,
                                         Map<Object, Boolean> expandedState) {
        if (decksCollection.getCollections() == null) {
            return;
        }
        for (ThemeCollection collection : decksCollection.getCollections()) {
            NavigationItem collectionNavItem =
                    NavigationHelper.createNavigationItem(collection.getName(), 0);
            collectionNavItem.setUserData(collection);
            collectionNavItem.setItemType(NavigationItem.ItemType.COLLECTION);
            NavigationDragDropWiring.attachNavItemDropHandlers(
                    collectionNavItem, collection, coordinator);
            NavigationDragDropWiring.enableNavItemAsNavDndTarget(
                    collectionNavItem, collection, coordinator,
                    (dragged, pos) ->
                            handleDecksNavDrop(dragged, collection, pos, decksCollection));

            boolean isDirty = UserInterfaceFunctions.isDirty(collection);
            boolean isLoose = Boolean.TRUE.equals(collection.getConnectToWholeCollection());

            if (isDirty) {
                collectionNavItem.getLabel().setText(
                        "* " + CardNameUtils.sanitize(collection.getName()));
            }
            String labelStyle = "-fx-font-weight: bold;";
            if (isLoose) {
                labelStyle += " -fx-font-style: italic;";
            }
            collectionNavItem.getLabel().setStyle(labelStyle);

            boolean hasMissing = DeckCollectionQualityChecks.collectionHasMissing(collection);
            boolean hasMissingArtwork =
                    DeckCollectionQualityChecks.collectionHasMissingArtworks(collection);
            boolean highlight = hasMissing || hasMissingArtwork;
            String highlightMessage;
            if (hasMissing && hasMissingArtwork) {
                highlightMessage =
                        "This collection contains missing archetype cards and cards with missing artwork variants.";
            } else if (hasMissing) {
                highlightMessage = "This collection contains missing archetype cards.";
            } else {
                highlightMessage =
                        "This collection contains cards with missing artwork variants.";
            }
            NavigationHelper.applyNavigationItemHighlight(collectionNavItem, highlight,
                    highlightMessage);

            collectionNavItem.setOnLabelClicked(evt ->
                    NavigationHelper.navigateToTree(
                            decksAndCollectionsTreeView, collection.getName()));

            boolean wasExpanded = expandedState.getOrDefault(collection, false);
            collectionNavItem.setExpanded(wasExpanded);

            ContextMenu collectionContextMenu =
                    DecksNavMenuBuilder.forDecksCollection(collection, decksCollection);
            collectionNavItem.setOnContextMenuRequested(event -> {
                collectionContextMenu.show(collectionNavItem,
                        event.getScreenX(), event.getScreenY());
                event.consume();
            });

            navigationMenu.addItem(collectionNavItem);

            if (collection.getLinkedDecks() != null) {
                buildLinkedDeckNavItems(collection, collectionNavItem, decksCollection);
            }

            collectionNavItem.setOnLabelClicked(evt -> {
                SelectionManager.setLastClickedNavigationItem(collection);
                NavigationHelper.navigateToTree(
                        decksAndCollectionsTreeView, collection.getName());
            });
        }
    }

    private void buildLinkedDeckNavItems(ThemeCollection collection,
                                         NavigationItem collectionNavItem,
                                         DecksAndCollectionsList decksCollection) {
        boolean firstNonNullUnit = true;
        int unitIndex = -1;
        for (List<Deck> unit : collection.getLinkedDecks()) {
            if (unit == null || unit.isEmpty()) {
                continue;
            }
            unitIndex++;
            if (!firstNonNullUnit) {
                final int afterUnitIndex = unitIndex - 1;
                HBox separator = collectionNavItem.addDeckListSeparator();
                wireSeparatorAsDropTarget(separator, collection, afterUnitIndex, decksCollection);
            }
            firstNonNullUnit = false;
            for (Deck linkedDeck : unit) {
                if (linkedDeck == null) {
                    continue;
                }
                NavigationItem deckSubItem =
                        NavigationHelper.createNavigationItem(linkedDeck.getName(), 1);
                deckSubItem.setUserData(linkedDeck);
                deckSubItem.setItemType(NavigationItem.ItemType.DECK);
                NavigationDragDropWiring.attachNavItemDropHandlers(
                        deckSubItem, linkedDeck, coordinator);
                NavigationDragDropWiring.enableNavDragSource(deckSubItem, linkedDeck);
                NavigationDragDropWiring.enableNavItemAsNavDndTarget(
                        deckSubItem, linkedDeck, coordinator,
                        (dragged, pos) ->
                                handleDecksNavDrop(dragged, linkedDeck, pos, decksCollection));
                if (UserInterfaceFunctions.isDirty(linkedDeck)) {
                    deckSubItem.getLabel().setText(
                            "* " + CardNameUtils.sanitize(linkedDeck.getName()));
                }

                ContextMenu deckContextMenu =
                        DecksNavMenuBuilder.forDecksDeck(linkedDeck, decksCollection);
                deckSubItem.setOnContextMenuRequested(event -> {
                    deckContextMenu.show(deckSubItem, event.getScreenX(), event.getScreenY());
                    event.consume();
                });

                collectionNavItem.addSubItem(deckSubItem);
                deckSubItem.setOnLabelClicked(evt -> {
                    SelectionManager.setLastClickedNavigationItem(linkedDeck);
                    NavigationHelper.navigateToTree(decksAndCollectionsTreeView,
                            collection.getName(), "Decks", linkedDeck.getName());
                });
            }
        }
    }

    private void buildStandaloneDeckNavItems(DecksAndCollectionsList decksCollection,
                                             NavigationMenu navigationMenu) {
        if (decksCollection.getDecks() == null) {
            return;
        }
        for (Deck deck : decksCollection.getDecks()) {
            NavigationItem navItem =
                    NavigationHelper.createNavigationItem(deck.getName(), 0);
            navItem.setUserData(deck);
            navItem.setItemType(NavigationItem.ItemType.DECK);
            NavigationDragDropWiring.attachNavItemDropHandlers(navItem, deck, coordinator);
            NavigationDragDropWiring.enableNavDragSource(navItem, deck);
            NavigationDragDropWiring.enableNavItemAsNavDndTarget(navItem, deck, coordinator,
                    (dragged, pos) ->
                            handleDecksNavDrop(dragged, deck, pos, decksCollection));
            if (UserInterfaceFunctions.isDirty(deck)) {
                navItem.getLabel().setText("* " + CardNameUtils.sanitize(deck.getName()));
            }

            navItem.setOnLabelClicked(evt ->
                    NavigationHelper.navigateToTree(decksAndCollectionsTreeView, deck.getName()));

            ContextMenu deckContextMenu =
                    DecksNavMenuBuilder.forDecksDeck(deck, decksCollection);
            navItem.setOnContextMenuRequested(event -> {
                deckContextMenu.show(navItem, event.getScreenX(), event.getScreenY());
                event.consume();
            });

            navigationMenu.addItem(navItem);
            navItem.setOnLabelClicked(evt -> {
                SelectionManager.setLastClickedNavigationItem(deck);
                NavigationHelper.navigateToTree(decksAndCollectionsTreeView, deck.getName());
            });
        }
    }

    // ── Nav drag-and-drop ─────────────────────────────────────────────────────

    /**
     * Handles a NAV drag-drop onto a Decks & Collections navigation item.
     *
     * <p>Rules:
     * <ul>
     *   <li>Deck → Deck (BEFORE/AFTER): reorder; respects standalone vs in-collection.</li>
     *   <li>Deck → Collection (INTO / AFTER): move deck into that collection.</li>
     *   <li>Deck → Collection (BEFORE): same as INTO (prepend as first unit).</li>
     * </ul>
     *
     * @param dragged the dragged model object (must be a Deck)
     * @param target  the target model object (Deck or ThemeCollection)
     * @param pos     resolved drop position
     * @param dcl     the live DecksAndCollectionsList
     */
    public void handleDecksNavDrop(Object dragged, Object target,
                                   NavigationItem.DropPosition pos,
                                   DecksAndCollectionsList dcl) {
        if (dragged == null || target == null || dragged == target) {
            return;
        }
        if (!(dragged instanceof Deck draggedDeck)) {
            return;
        }

        NavigationDragDrop.DeckLocation sourceLocation =
                NavigationDragDrop.findDeckLocation(draggedDeck, dcl);
        ThemeCollection sourceCollection = sourceLocation.collection;

        boolean changed = false;
        ThemeCollection destCollection = null;

        if (target instanceof Deck targetDeck) {
            NavigationDragDrop.DeckLocation targetLocation =
                    NavigationDragDrop.findDeckLocation(targetDeck, dcl);
            destCollection = targetLocation.collection;
            switch (pos) {
                case BEFORE -> changed = NavigationDragDrop.moveDeckBefore(draggedDeck, targetDeck, dcl);
                case AFTER, INTO -> changed = NavigationDragDrop.moveDeckAfter(draggedDeck, targetDeck, dcl);
            }
        } else if (target instanceof ThemeCollection targetCollection) {
            destCollection = targetCollection;
            changed = NavigationDragDrop.moveDeckToCollection(draggedDeck, targetCollection, dcl);
        }

        if (changed) {
            if (sourceCollection != null) {
                UserInterfaceFunctions.markDirty(sourceCollection);
            }
            if (destCollection != null && destCollection != sourceCollection) {
                UserInterfaceFunctions.markDirty(destCollection);
            }
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        }
    }

    /**
     * Wires a deck-list separator node as a drop target that creates a new unit
     * between the two surrounding deck lists.
     *
     * @param separatorNode  the HBox returned by {@link NavigationItem#addDeckListSeparator()}
     * @param collection     the ThemeCollection the separator belongs to
     * @param afterUnitIndex 0-based index of the unit immediately above this separator
     * @param dcl            the live DecksAndCollectionsList
     */
    public void wireSeparatorAsDropTarget(HBox separatorNode, ThemeCollection collection,
                                          int afterUnitIndex, DecksAndCollectionsList dcl) {
        separatorNode.setOnDragOver(event -> {
            if ("NAV".equals(DragDropManager.getDragSourcePane())
                    && event.getDragboard().hasString()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
                separatorNode.setStyle(
                        "-fx-background-color: #cdfc04;" +
                                "-fx-min-height: 1; -fx-pref-height: 1; -fx-max-height: 1;");
            }
            event.consume();
        });
        separatorNode.setOnDragExited(event -> {
            separatorNode.setStyle(
                    "-fx-background-color: #555577;" +
                            "-fx-min-height: 1; -fx-pref-height: 1; -fx-max-height: 1;");
            event.consume();
        });
        separatorNode.setOnDragDropped(event -> {
            Object dragged = DragDropManager.getDraggedNavObject();
            boolean changed = false;
            if (dragged instanceof Deck draggedDeck) {
                NavigationDragDrop.DeckLocation sourceLocation =
                        NavigationDragDrop.findDeckLocation(draggedDeck, dcl);
                ThemeCollection sourceCollection = sourceLocation.collection;

                changed = NavigationDragDrop.moveDeckToNewUnit(
                        draggedDeck, collection, afterUnitIndex, dcl);

                if (changed) {
                    if (sourceCollection != null && sourceCollection != collection) {
                        UserInterfaceFunctions.markDirty(sourceCollection);
                    }
                    UserInterfaceFunctions.markDirty(collection);
                }
            }
            separatorNode.setStyle(
                    "-fx-background-color: #555577;" +
                            "-fx-min-height: 1; -fx-pref-height: 1; -fx-max-height: 1;");
            if (changed) {
                UserInterfaceFunctions.refreshDecksAndCollectionsView();
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }

    // ── Inline renames ────────────────────────────────────────────────────────

    /**
     * Starts an inline rename on a freshly created Deck or ThemeCollection.
     *
     * <p>Confirm → updates the model name and refreshes. Cancel → removes the
     * element from the model and refreshes.
     */
    public void startDecksAddRename(NavigationItem navItem, Object modelObj) {
        navItem.startInlineRename(
                newName -> {
                    if (modelObj instanceof Deck) {
                        ((Deck) modelObj).setName(newName);
                    } else if (modelObj instanceof ThemeCollection) {
                        ((ThemeCollection) modelObj).setName(newName);
                    }
                    UserInterfaceFunctions.markDirty(modelObj);
                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    UserInterfaceFunctions.setPendingDecksFullRebuild();
                    UserInterfaceFunctions.refreshDecksAndCollectionsView();
                },
                () -> {
                    try {
                        DecksAndCollectionsList dac = UserInterfaceFunctions.getDecksList();
                        if (dac != null) {
                            if (modelObj instanceof Deck cancelledDeck) {
                                boolean removed = dac.getDecks() != null
                                        && dac.getDecks().remove(cancelledDeck);
                                if (!removed && dac.getCollections() != null) {
                                    outer:
                                    for (ThemeCollection themeCollection : dac.getCollections()) {
                                        if (themeCollection == null
                                                || themeCollection.getLinkedDecks() == null) {
                                            continue;
                                        }
                                        for (List<Deck> unit : themeCollection.getLinkedDecks()) {
                                            if (unit != null && unit.remove(cancelledDeck)) {
                                                break outer;
                                            }
                                        }
                                    }
                                }
                            } else if (modelObj instanceof ThemeCollection) {
                                if (dac.getCollections() != null) {
                                    dac.getCollections().remove(modelObj);
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    UserInterfaceFunctions.clearDirty(modelObj);
                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    UserInterfaceFunctions.refreshDecksAndCollectionsView();
                }
        );
    }

    /**
     * Starts an inline rename for a ThemeCollection just created from a standalone
     * Deck ("Create Collection" action).
     *
     * <p>Confirm → renames and rebuilds. Cancel → removes the collection and
     * restores the deck to standalone.
     */
    public void startDecksCreateCollectionRename(NavigationItem navItem,
                                                 ThemeCollection newCollection,
                                                 Deck movedDeck) {
        navItem.startInlineRename(
                newName -> {
                    newCollection.setName(newName);
                    UserInterfaceFunctions.markDirty(newCollection);
                    UserInterfaceFunctions.markDirty(movedDeck);
                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    UserInterfaceFunctions.setPendingDecksFullRebuild();
                    UserInterfaceFunctions.setPendingDecksExpandTarget(newCollection);
                    UserInterfaceFunctions.refreshDecksAndCollectionsView();
                },
                () -> {
                    try {
                        DecksAndCollectionsList dac = UserInterfaceFunctions.getDecksList();
                        if (dac != null) {
                            if (dac.getCollections() != null) {
                                dac.getCollections().remove(newCollection);
                            }
                            if (dac.getDecks() == null) {
                                dac.setDecks(new ArrayList<>());
                            }
                            dac.getDecks().add(movedDeck);
                        }
                    } catch (Throwable ignored) {
                    }
                    UserInterfaceFunctions.clearDirty(newCollection);
                    UserInterfaceFunctions.clearDirty(movedDeck);
                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    UserInterfaceFunctions.refreshDecksAndCollectionsView();
                }
        );
    }

    // ── Scroll helpers ────────────────────────────────────────────────────────

    /**
     * Scrolls the Decks tree so the node corresponding to {@code handlerTarget}
     * is visible after a tree rebuild. Maps known aliases ("Exclusion List" → "Cards not to add").
     *
     * @param handlerTarget the target path string
     */
    public void scrollToTargetInDecksTree(String handlerTarget) {
        if (decksAndCollectionsTreeView == null || handlerTarget == null) {
            return;
        }

        String[] rawParts = handlerTarget.split("\\s*/\\s*");
        String[] parts = new String[rawParts.length];
        for (int index = 0; index < rawParts.length; index++) {
            String part = rawParts[index].trim();
            if (part.equalsIgnoreCase("Exclusion List")) {
                part = "Cards not to add";
            }
            parts[index] = part;
        }

        Platform.runLater(() -> {
            if (decksAndCollectionsTreeView == null) {
                return;
            }
            TreeItem<String> root = decksAndCollectionsTreeView.getRoot();
            if (root == null) {
                return;
            }

            TreeItem<String> target = NavigationHelper.findTreeItemByPath(root, parts, 0);
            if (target == null && parts.length >= 2) {
                target = NavigationHelper.findTreeItemByPath(root,
                        new String[]{parts[parts.length - 2], parts[parts.length - 1]}, 0);
            }
            if (target == null) {
                target = NavigationHelper.findTreeItemByPath(
                        root, new String[]{parts[parts.length - 1]}, 0);
            }
            if (target == null) {
                return;
            }

            for (TreeItem<String> ancestor = target.getParent();
                 ancestor != null;
                 ancestor = ancestor.getParent()) {
                ancestor.setExpanded(true);
            }

            final TreeItem<String> finalTarget = target;
            Platform.runLater(() -> {
                int row = decksAndCollectionsTreeView.getRow(finalTarget);
                if (row >= 0) {
                    decksAndCollectionsTreeView.scrollTo(row);
                }
            });
        });
    }

    /**
     * After a deck move, scrolls the Decks tree and nav menu so the moved deck
     * is visible.
     *
     * @param deckObj the moved Deck (must be a {@link Deck} instance)
     */
    public void scrollToMovedDeck(Object deckObj) {
        if (!(deckObj instanceof Deck)) {
            return;
        }
        Deck deck = (Deck) deckObj;
        String deckName = deck.getName() == null ? ""
                : deck.getName().replaceAll("[=\\-]", "");

        Platform.runLater(() -> {
            if (decksAndCollectionsTreeView == null) {
                return;
            }
            TreeItem<String> root = decksAndCollectionsTreeView.getRoot();
            if (root == null) {
                return;
            }
            TreeItem<String> target =
                    NavigationHelper.findTreeItemByPath(root, new String[]{deckName}, 0);
            if (target == null) {
                return;
            }
            for (TreeItem<String> ancestor = target.getParent();
                 ancestor != null;
                 ancestor = ancestor.getParent()) {
                ancestor.setExpanded(true);
            }
            Platform.runLater(() -> {
                int row = decksAndCollectionsTreeView.getRow(target);
                if (row >= 0) {
                    decksAndCollectionsTreeView.scrollTo(row);
                }
            });
        });

        Platform.runLater(() -> {
            NavigationItem navItem =
                    NavigationHelper.findNavItemInMenuVBox(decksTab.getMenuVBox(), deckObj);
            if (navItem == null) {
                navItem = NavigationHelper.findNavItemByNameInMenuVBox(
                        decksTab.getMenuVBox(), deckName);
            }
            if (navItem != null) {
                NavigationHelper.expandNavAncestors(navItem);
                NavigationHelper.scrollNavToItem(decksTab, navItem);
            }
        });
    }

    /**
     * Returns the [0..1] VirtualFlow scroll position of the Decks tree, or -1 if unavailable.
     */
    public double getDecksTreeScrollPosition() {
        if (decksAndCollectionsTreeView == null) {
            return -1;
        }
        try {
            for (Node node : decksAndCollectionsTreeView.lookupAll(".virtual-flow")) {
                if (node instanceof javafx.scene.control.skin.VirtualFlow) {
                    return ((javafx.scene.control.skin.VirtualFlow<?>) node).getPosition();
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    /**
     * Restores a previously captured [0..1] scroll position on the Decks tree.
     * Must be called from the FX thread after the new tree has been laid out.
     *
     * @param position the [0..1] scroll value to restore; values below 0 are ignored
     */
    public void restoreDecksTreeScrollPosition(double position) {
        if (position < 0 || decksAndCollectionsTreeView == null) {
            return;
        }
        try {
            for (Node node : decksAndCollectionsTreeView.lookupAll(".virtual-flow")) {
                if (node instanceof javafx.scene.control.skin.VirtualFlow) {
                    ((javafx.scene.control.skin.VirtualFlow<?>) node).setPosition(position);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ── Tree-item builders ────────────────────────────────────────────────────

    /**
     * Builds a {@link DataTreeItem} for a {@link Deck}, wiring reactive section nodes
     * (Main Deck, Extra Deck, Side Deck) that appear/disappear as cards are added or removed.
     *
     * @param deck the Deck to represent
     * @return the populated DataTreeItem
     */
    public DataTreeItem<Object> createDeckTreeItem(Deck deck) {
        String cleanName = deck.getName() == null ? "" : deck.getName();
        DataTreeItem<Object> deckItem = new DataTreeItem<>(cleanName, deck);
        deckItem.setExpanded(true);

        java.util.function.BiConsumer<List<CardElement>, String> wireSection =
                (rawList, sectionLabel) -> {
                    if (rawList == null) {
                        return;
                    }
                    String sectionKey = sectionLabel.toLowerCase(Locale.ROOT)
                            .replace(" deck", "").trim();
                    CardsGroup sectionGroup = CardGroupRegistry.getOrCreateDeckSectionGroup(
                            deck, sectionKey, sectionLabel, rawList);
                    DataTreeItem<Object> sectionItem =
                            new DataTreeItem<>(sectionLabel, sectionGroup);
                    sectionItem.setExpanded(true);

                    if (!rawList.isEmpty()) {
                        deckItem.getChildren().add(sectionItem);
                    }

                    javafx.collections.ObservableList<CardElement> observableList =
                            CardGroupRegistry.observableListFor(sectionGroup);
                    observableList.addListener(
                            (javafx.collections.ListChangeListener<CardElement>) change ->
                                    Platform.runLater(() -> {
                                        boolean hasCards = !observableList.isEmpty();
                                        boolean shown =
                                                deckItem.getChildren().contains(sectionItem);
                                        if (hasCards && !shown) {
                                            deckItem.getChildren().add(sectionItem);
                                        } else if (!hasCards && shown) {
                                            deckItem.getChildren().remove(sectionItem);
                                        }
                                    })
                    );
                };

        wireSection.accept(deck.getMainDeck(), "Main Deck");
        wireSection.accept(deck.getExtraDeck(), "Extra Deck");
        wireSection.accept(deck.getSideDeck(), "Side Deck");

        return deckItem;
    }

    /**
     * Builds a {@link DataTreeItem} for a {@link ThemeCollection}, wiring reactive
     * sections (Cards, Decks, Archetypes, Exceptions) with {@link ListChangeListener}s.
     *
     * @param collection the ThemeCollection to represent
     * @param tabType    the tab type (DECKS or OUICHE_LIST); controls which sections are built
     * @return the populated DataTreeItem
     */
    public DataTreeItem<Object> createThemeCollectionTreeItem(ThemeCollection collection,
                                                              TabType tabType) {
        String cleanName = collection.getName() == null ? "" : collection.getName();
        DataTreeItem<Object> collectionItem = new DataTreeItem<>(cleanName, collection);
        collectionItem.setExpanded(true);

        // ── Cards section ─────────────────────────────────────────────────────
        Set<String> missingArtworkSet =
                DeckCollectionQualityChecks.computeCardsWithMissingArtworks(collection);
        {
            List<CardElement> cardsList = collection.getCardsList();
            if (cardsList == null) {
                cardsList = new ArrayList<>();
                collection.setCardsList(cardsList);
            }
            CardsGroup cardsGroup = CardGroupRegistry.getOrCreateCollectionCardsGroup(
                    collection, cardsList);
            CardGroupRegistry.setMissingArtworkSetForGroup(cardsGroup, missingArtworkSet);

            DataTreeItem<Object> cardsGroupItem = new DataTreeItem<>("Cards", cardsGroup);
            cardsGroupItem.setExpanded(true);

            if (!cardsList.isEmpty()) {
                collectionItem.getChildren().add(cardsGroupItem);
            }

            javafx.collections.ObservableList<CardElement> cardsObservable =
                    CardGroupRegistry.observableListFor(cardsGroup);
            cardsObservable.addListener(
                    (javafx.collections.ListChangeListener<CardElement>) change ->
                            Platform.runLater(() -> {
                                boolean hasCards = !cardsObservable.isEmpty();
                                boolean shown =
                                        collectionItem.getChildren().contains(cardsGroupItem);
                                if (hasCards && !shown) {
                                    collectionItem.getChildren().add(0, cardsGroupItem);
                                } else if (!hasCards && shown) {
                                    collectionItem.getChildren().remove(cardsGroupItem);
                                }
                            })
            );
        }

        // ── Decks section ─────────────────────────────────────────────────────
        DataTreeItem<Object> decksParent =
                new DataTreeItem<>("Decks", "DECKS_SECTION");
        decksParent.setExpanded(true);
        boolean decksParentHasChildren = false;

        if (collection.getLinkedDecks() != null && !collection.getLinkedDecks().isEmpty()) {
            int unitIndex = 1;
            for (List<Deck> unit : collection.getLinkedDecks()) {
                if (unit == null || unit.isEmpty()) {
                    DataTreeItem<Object> emptyUnit =
                            new DataTreeItem<>("Group " + unitIndex, "DECK_GROUP");
                    emptyUnit.setExpanded(false);
                    decksParent.getChildren().add(emptyUnit);
                    unitIndex++;
                    continue;
                }
                if (unit.size() > 1) {
                    DataTreeItem<Object> unitNode =
                            new DataTreeItem<>("Group " + unitIndex, "DECK_GROUP");
                    unitNode.setExpanded(false);
                    for (Deck deck : unit) {
                        DataTreeItem<Object> deckItem = createDeckTreeItem(deck);
                        unitNode.getChildren().add(deckItem);
                    }
                    decksParent.getChildren().add(unitNode);
                } else {
                    Deck single = unit.get(0);
                    DataTreeItem<Object> deckItem = createDeckTreeItem(single);
                    decksParent.getChildren().add(deckItem);
                }
                unitIndex++;
            }
            decksParentHasChildren = !decksParent.getChildren().isEmpty();
        }

        if (decksParentHasChildren) {
            collectionItem.getChildren().add(decksParent);
        }

        if (tabType != TabType.OUICHE_LIST) {
            // ── Archetypes section ────────────────────────────────────────────
            // Suppressed when the "Hide archetypes & exceptions" toggle is on.
            if (!hideArchetypesEnabled) {
                addArchetypesSectionToCollection(collection, collectionItem, missingArtworkSet);
            }

            // ── Exceptions section ────────────────────────────────────────────
            // Suppressed when the "Hide archetypes & exceptions" toggle is on.
            if (!hideArchetypesEnabled) {
                List<CardElement> exceptions = collection.getExceptionsToNotAdd();
                if (exceptions == null) {
                    exceptions = new ArrayList<>();
                }
                CardsGroup exceptionsGroup = CardGroupRegistry.getOrCreateCollectionExceptionsGroup(
                        collection, exceptions);
                CardGroupRegistry.setMissingArtworkSetForGroup(exceptionsGroup, missingArtworkSet);

                DataTreeItem<Object> exceptionsNode =
                        new DataTreeItem<>("Cards not to add", exceptionsGroup);
                exceptionsNode.setExpanded(true);

                if (!exceptions.isEmpty()) {
                    collectionItem.getChildren().add(exceptionsNode);
                }

                javafx.collections.ObservableList<CardElement> exceptionsObservable =
                        CardGroupRegistry.observableListFor(exceptionsGroup);
                exceptionsObservable.addListener(
                        (javafx.collections.ListChangeListener<CardElement>) change ->
                                Platform.runLater(() -> {
                                    boolean hasExceptions = !exceptionsObservable.isEmpty();
                                    boolean shown =
                                            collectionItem.getChildren().contains(exceptionsNode);
                                    if (hasExceptions && !shown) {
                                        collectionItem.getChildren().add(exceptionsNode);
                                    } else if (!hasExceptions && shown) {
                                        collectionItem.getChildren().remove(exceptionsNode);
                                    }
                                })
                );
            }
        }

        return collectionItem;
    }

    private void addArchetypesSectionToCollection(ThemeCollection collection,
                                                  DataTreeItem<Object> collectionItem,
                                                  Set<String> missingArtworkSet) {
        DataTreeItem<Object> archetypesParent =
                new DataTreeItem<>("Archetypes", "ARCHETYPES_SECTION");
        archetypesParent.setExpanded(true);
        boolean archetypesAdded = false;
        boolean hasArchetypesMethod = false;

        try {
            Method archetypesMethod = collection.getClass().getMethod("getArchetypes");
            hasArchetypesMethod = true;
            Object result = archetypesMethod.invoke(collection);
            if (result instanceof List) {
                List<?> archetypes = (List<?>) result;
                logger.debug("Collection '{}' getArchetypes() returned list size={}",
                        collection.getName(), archetypes.size());

                for (Object archetypeObj : archetypes) {
                    if (archetypeObj == null) {
                        continue;
                    }
                    archetypesAdded |= addSingleArchetypeNode(
                            archetypeObj, collection, archetypesParent);
                }
            }
        } catch (NoSuchMethodException ignored) {
            hasArchetypesMethod = false;
        } catch (Exception exception) {
            logger.debug("Archetypes reflection failed for collection " + collection.getName(),
                    exception);
        }

        if (!hasArchetypesMethod && !archetypesAdded) {
            archetypesAdded = addGlobalArchetypesFallback(collection, archetypesParent);
        }

        if (archetypesAdded) {
            logger.info("Collection '{}' -> added {} archetype group(s)",
                    collection.getName(), archetypesParent.getChildren().size());
            collectionItem.getChildren().add(archetypesParent);
        }
    }

    private boolean addSingleArchetypeNode(Object archetypeObj, ThemeCollection collection,
                                           DataTreeItem<Object> archetypesParent) {
        if (archetypeObj instanceof String archetypeName) {
            archetypeName = archetypeName.trim();
            if (archetypeName.isEmpty()) {
                return false;
            }
            List<CardElement> elements =
                    DeckCollectionQualityChecks.buildElementsFromGlobalArchetype(archetypeName);
            Set<String> missing =
                    DeckCollectionQualityChecks.computeMissingIdsForElements(collection, elements);
            CardsGroup archetypeGroup =
                    new CardsGroup(ARCHETYPE_MARKER + archetypeName, elements);
            Map<String, Object> data = new HashMap<>();
            data.put("group", archetypeGroup);
            data.put("missing", missing);
            DataTreeItem<Object> archetypeNode =
                    new DataTreeItem<>(archetypeName, data);
            archetypeNode.setExpanded(false);
            archetypesParent.getChildren().add(archetypeNode);
            return true;
        }

        String name = null;
        List<CardElement> elements = new ArrayList<>();
        try {
            Method nameMethod = archetypeObj.getClass().getMethod("getName");
            Object nameVal = nameMethod.invoke(archetypeObj);
            if (nameVal != null) {
                name = nameVal.toString();
            }
        } catch (Exception ignored) {
        }
        try {
            Method cardsMethod = archetypeObj.getClass().getMethod("getCards");
            Object cardsVal = cardsMethod.invoke(archetypeObj);
            if (cardsVal instanceof List) {
                for (Object entry : (List<?>) cardsVal) {
                    if (entry instanceof CardElement) {
                        elements.add((CardElement) entry);
                    } else if (entry instanceof Card) {
                        elements.add(new CardElement((Card) entry));
                    } else if (entry instanceof String) {
                        try {
                            elements.add(new CardElement((String) entry));
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (elements.isEmpty() && name != null) {
            elements = DeckCollectionQualityChecks.buildElementsFromGlobalArchetype(name);
        }
        if (name == null) {
            name = "Archetype";
        }

        Set<String> missing = DeckCollectionQualityChecks.computeMissingIdsForElements(collection, elements);
        CardsGroup archetypeGroup = new CardsGroup(ARCHETYPE_MARKER + name, elements);
        Map<String, Object> data = new HashMap<>();
        data.put("group", archetypeGroup);
        data.put("missing", missing);
        DataTreeItem<Object> archetypeNode = new DataTreeItem<>(name, data);
        archetypeNode.setExpanded(false);
        archetypesParent.getChildren().add(archetypeNode);
        return true;
    }

    private boolean addGlobalArchetypesFallback(ThemeCollection collection,
                                                DataTreeItem<Object> archetypesParent) {
        boolean archetypesAdded = false;
        try {
            List<String> globalNames = Model.CardsLists.SubListCreator.getArchetypesList();
            List<List<Card>> globalLists = Model.CardsLists.SubListCreator.getArchetypesCardsLists();
            if (globalNames != null && globalLists != null
                    && globalNames.size() == globalLists.size()) {
                for (int index = 0; index < globalNames.size(); index++) {
                    String archetypeName = globalNames.get(index);
                    if (archetypeName == null) {
                        continue;
                    }
                    List<Card> cardsForArchetype = globalLists.get(index);
                    List<CardElement> elements = new ArrayList<>();
                    if (cardsForArchetype != null) {
                        for (Card card : cardsForArchetype) {
                            if (card != null) {
                                elements.add(new CardElement(card));
                            }
                        }
                    }
                    Set<String> missing =
                            DeckCollectionQualityChecks.computeMissingIdsForElements(collection, elements);
                    CardsGroup archetypeGroup =
                            new CardsGroup(ARCHETYPE_MARKER + archetypeName, elements);
                    Map<String, Object> data = new HashMap<>();
                    data.put("group", archetypeGroup);
                    data.put("missing", missing);
                    DataTreeItem<Object> archetypeNode =
                            new DataTreeItem<>(archetypeName, data);
                    archetypeNode.setExpanded(false);
                    archetypesParent.getChildren().add(archetypeNode);
                    archetypesAdded = true;
                }
            }
        } catch (Exception exception) {
            logger.debug("Fallback archetypes population failed", exception);
        }
        return archetypesAdded;
    }

    // ── Expanded-state capture ────────────────────────────────────────────────

    /**
     * Recursively captures the expanded state of a NavigationItem and all its
     * sub-items into {@code map}, keyed by {@link NavigationItem#getUserData()}.
     *
     * @param item the root NavigationItem to start from
     * @param map  the map to populate
     */
    public void captureExpandedState(NavigationItem item, Map<Object, Boolean> map) {
        if (item == null) {
            return;
        }
        Object key = item.getUserData();
        if (key != null) {
            map.put(key, item.isExpanded());
        }
        for (NavigationItem subItem : item.getSubItems()) {
            captureExpandedState(subItem, map);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /**
     * Returns the currently displayed Decks and Collections TreeView (may be null).
     */
    public TreeView<String> getDecksAndCollectionsTreeView() {
        return decksAndCollectionsTreeView;
    }
}