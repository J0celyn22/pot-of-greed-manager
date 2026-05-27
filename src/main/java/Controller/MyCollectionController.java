package Controller;

import Model.CardsLists.*;
import View.*;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyCollectionController — manages all display and navigation logic for the
 * "My Collection" tab.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Building and refreshing the My Collection {@link TreeView} from the model.</li>
 *   <li>Populating the left-hand navigation menu with Boxes and CardsGroups.</li>
 *   <li>Wiring navigation drag-and-drop for the My Collection hierarchy.</li>
 *   <li>Starting inline renames for newly created Boxes and CardsGroups.</li>
 *   <li>Registering/unregistering the owned-collection refreshers with
 *       {@link UserInterfaceFunctions}.</li>
 *   <li>Quality-check helpers (unsorted cards, missing print codes, incomplete cards)
 *       used to highlight nav items.</li>
 *   <li>Scroll helpers for bringing newly added cards into view.</li>
 * </ul>
 *
 * <p>All shared state (card width/height properties, the tree-view reference, the
 * {@link SharedCollectionTab}) is injected via the constructor. The
 * {@link RealMainController} coordinator retains ownership of those objects.
 */
public class MyCollectionController {

    private static final Logger logger = LoggerFactory.getLogger(MyCollectionController.class);

    // ── Injected shared state ─────────────────────────────────────────────────

    private final DoubleProperty cardWidthProperty;
    private final DoubleProperty cardHeightProperty;
    private final SharedCollectionTab myCollectionTab;

    /**
     * The coordinator that owns the tree-view reference.
     * Updated by {@link #setMyCollectionTreeView} after each tree rebuild.
     */
    private final RealMainController coordinator;

    /**
     * Cached, position-aware sort-need results.
     * Key = container object (CardsGroup, Box, or raw list); value = per-index cache.
     */
    private final ConcurrentHashMap<Object, List<Boolean>> positionSortCache =
            new ConcurrentHashMap<>();

    // ── Live tree view ────────────────────────────────────────────────────────

    /**
     * The currently displayed My Collection TreeView. Rebuilt on every structure refresh.
     */
    private TreeView<String> myCollectionTreeView;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates a MyCollectionController and immediately registers the owned-collection
     * refreshers with {@link UserInterfaceFunctions}.
     *
     * @param coordinator        the thin coordinator (used to route display callbacks)
     * @param cardWidthProperty  shared card-width property (bound to all cell factories)
     * @param cardHeightProperty shared card-height property
     * @param myCollectionTab    the tab UI container for My Collection
     */
    public MyCollectionController(RealMainController coordinator,
                                  DoubleProperty cardWidthProperty,
                                  DoubleProperty cardHeightProperty,
                                  SharedCollectionTab myCollectionTab) {
        this.coordinator = coordinator;
        this.cardWidthProperty = cardWidthProperty;
        this.cardHeightProperty = cardHeightProperty;
        this.myCollectionTab = myCollectionTab;

        registerRefreshers();
    }

    // ── Refresher registration ────────────────────────────────────────────────

    private void registerRefreshers() {
        UserInterfaceFunctions.registerOwnedCollectionRefresher(() -> {
            try {
                String addedTarget = MenuActionHandler.getAndClearLastAddedTarget();
                populateMyCollectionMenu();
                if (myCollectionTreeView != null) {
                    myCollectionTreeView.refresh();
                }
                if (addedTarget != null) {
                    scrollToNewCardInGroup(addedTarget);
                }
                coordinator.updateTabDirtyIndicators();
            } catch (Exception exception) {
                logger.debug("My Collection refresher failed", exception);
            }
        });

        UserInterfaceFunctions.registerOwnedCollectionStructureRefresher(() -> {
            try {
                displayMyCollection();
                populateMyCollectionMenu();
                Object renameTarget = UserInterfaceFunctions.getAndClearPendingRenameTarget();
                if (renameTarget != null) {
                    final Object finalTarget = renameTarget;
                    Platform.runLater(() -> {
                        logger.debug("Pending rename: searching nav for target={}", finalTarget);
                        NavigationItem toRename = NavigationHelper.findNavItemInMenuVBox(
                                myCollectionTab.getMenuVBox(), finalTarget);
                        if (toRename != null) {
                            logger.debug("Pending rename: found NavigationItem '{}', starting inline rename",
                                    toRename.getLabel().getText());
                            NavigationHelper.expandNavAncestors(toRename);
                            NavigationHelper.scrollNavToItem(myCollectionTab, toRename);
                            startAddRename(toRename, finalTarget);
                        } else {
                            logger.warn("Pending rename: NavigationItem not found for target={}",
                                    finalTarget);
                        }
                    });
                }
            } catch (Exception exception) {
                logger.debug("My Collection structure refresher failed", exception);
            }
        });
    }

    /**
     * Unregisters all refreshers registered during construction.
     * Call this when the controller is being torn down.
     */
    public void dispose() {
        // UserInterfaceFunctions does not currently expose unregistration per-lambda,
        // so disposal is a no-op until the API is extended. The refresher lambdas
        // capture this controller, so they will be GC'd with it if the tab is closed.
    }

    // ── Display ───────────────────────────────────────────────────────────────

    /**
     * Builds and installs a new My Collection {@link TreeView} into the tab's
     * content pane.  The tree is rebuilt from scratch on every call.
     *
     * @throws Exception if the owned collection cannot be loaded
     */
    public void displayMyCollection() throws Exception {
        AnchorPane contentPane = myCollectionTab.getContentPane();
        contentPane.getChildren().clear();

        OwnedCardsCollection collection = loadOwnedCollection();
        if (collection == null || collection.getOwnedCollection() == null) {
            logger.warn("OwnedCardsCollection is not available.");
        }

        DataTreeItem<Object> rootItem = new DataTreeItem<>("My Cards Collection", "ROOT");
        rootItem.setExpanded(true);

        if (collection != null && collection.getOwnedCollection() != null) {
            for (Box box : collection.getOwnedCollection()) {
                DataTreeItem<Object> boxItem = createBoxTreeItem(box);
                rootItem.getChildren().add(boxItem);
            }
        }

        myCollectionTreeView = new TreeView<>(rootItem);
        myCollectionTreeView.setUserData("MY_COLLECTION");
        myCollectionTreeView.setCellFactory(
                param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        myCollectionTreeView.setStyle("-fx-background-color: #100317;");
        myCollectionTreeView.setShowRoot(false);
        myCollectionTreeView.addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                coordinator.buildMiddlePaneEmptySpaceFilter());

        contentPane.getChildren().add(myCollectionTreeView);
        AnchorPane.setTopAnchor(myCollectionTreeView, 0.0);
        AnchorPane.setBottomAnchor(myCollectionTreeView, 0.0);
        AnchorPane.setLeftAnchor(myCollectionTreeView, 0.0);
        AnchorPane.setRightAnchor(myCollectionTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        myCollectionTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());

        // Let the coordinator keep a reference for global refresh/selection handling.
        coordinator.setMyCollectionTreeView(myCollectionTreeView);

        logger.info("My Collection displayed.");
    }

    // ── Navigation menu ───────────────────────────────────────────────────────

    /**
     * Rebuilds the left-hand navigation menu for My Collection from the model.
     *
     * @throws Exception if the owned collection cannot be loaded
     */
    public void populateMyCollectionMenu() throws Exception {
        VBox menuVBox = myCollectionTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        OwnedCardsCollection collection = loadOwnedCollection();
        if (collection == null || collection.getOwnedCollection() == null) {
            logger.warn("OwnedCardsCollection is not available.");
            menuVBox.getChildren().add(navigationMenu);
            return;
        }

        final OwnedCardsCollection finalCollection = collection;

        for (Box box : collection.getOwnedCollection()) {
            String rawBoxName = box.getName() == null ? "" : box.getName();
            String boxName = OwnedCardsCollection.extractName(rawBoxName, '=');

            NavigationItem boxItem = NavigationHelper.createNavigationItem(boxName, 0);
            boxItem.setUserData(box);
            boxItem.setItemType(NavigationItem.ItemType.BOX);
            attachNavItemDropHandlers(boxItem, box);
            enableNavDragSource(boxItem, box);
            enableNavItemAsNavDndTarget(boxItem, box,
                    (dragged, pos) -> handleMyCollectionNavDrop(dragged, box, pos, finalCollection));
            boxItem.setOnLabelClicked(evt -> {
                SelectionManager.setLastClickedNavigationItem(box);
                NavigationHelper.navigateToTree(myCollectionTreeView, boxName);
            });

            // ── Highlight logic ───────────────────────────────────────────────
            boolean boxHasUnsorted;
            String boxInitMsg;
            if (CardTreeCell.isIncompleteMarkingEnabled() && boxHasMissingPrintCode(box)) {
                boxHasUnsorted = true;
                boxInitMsg = "This box contains cards without a print code.";
            } else if (CardTreeCell.isIncompleteMarkingEnabled() && boxHasIncompleteCards(box)) {
                boxHasUnsorted = true;
                boxInitMsg = "This box contains cards with no condition or rarity set.";
            } else {
                boxHasUnsorted = boxHasUnsortedCards(box, boxName);
                boxInitMsg = "This box contains unsorted cards.";
            }
            NavigationHelper.applyNavigationItemHighlight(boxItem, boxHasUnsorted, boxInitMsg);

            // ── Context menu ──────────────────────────────────────────────────
            ContextMenu boxContextMenu =
                    NavigationContextMenuBuilder.forMyCollectionBox(box, collection);
            boxItem.setOnContextMenuRequested(event -> {
                boxContextMenu.show(boxItem, event.getScreenX(), event.getScreenY());
                event.consume();
            });

            // ── Groups ────────────────────────────────────────────────────────
            if (box.getContent() != null) {
                for (CardsGroup group : box.getContent()) {
                    String rawGroupName = group.getName() == null ? "" : group.getName();
                    String groupName = OwnedCardsCollection.extractName(rawGroupName, '-');
                    if (groupName.isEmpty()) {
                        continue;
                    }
                    NavigationItem groupItem = NavigationHelper.createNavigationItem(groupName, 1);
                    groupItem.setUserData(group);
                    groupItem.setItemType(NavigationItem.ItemType.CATEGORY);
                    attachNavItemDropHandlers(groupItem, group);
                    enableNavDragSource(groupItem, group);
                    enableNavItemAsNavDndTarget(groupItem, group,
                            (dragged, pos) -> handleMyCollectionNavDrop(
                                    dragged, group, pos, finalCollection));
                    groupItem.setOnLabelClicked(evt -> {
                        SelectionManager.setLastClickedNavigationItem(group);
                        NavigationHelper.navigateToTree(myCollectionTreeView, boxName, groupName);
                    });

                    boolean groupHasUnsorted;
                    String groupMsg;
                    if (CardTreeCell.isIncompleteMarkingEnabled() && groupHasMissingPrintCode(group)) {
                        groupHasUnsorted = true;
                        groupMsg = "This category contains cards without a print code.";
                    } else if (CardTreeCell.isIncompleteMarkingEnabled()
                            && groupHasIncompleteCards(group)) {
                        groupHasUnsorted = true;
                        groupMsg = "This category contains cards with no condition or rarity set.";
                    } else {
                        groupHasUnsorted = groupHasUnsortedCards(group, groupName);
                        groupMsg = "This category contains unsorted cards.";
                    }
                    NavigationHelper.applyNavigationItemHighlight(groupItem, groupHasUnsorted, groupMsg);
                    if (groupHasUnsorted && !boxHasUnsorted) {
                        NavigationHelper.applyNavigationItemHighlight(boxItem, true, groupMsg);
                        boxHasUnsorted = true;
                    }

                    ContextMenu groupContextMenu =
                            NavigationContextMenuBuilder.forMyCollectionCategory(group, box, collection);
                    groupItem.setOnContextMenuRequested(event -> {
                        groupContextMenu.show(groupItem, event.getScreenX(), event.getScreenY());
                        event.consume();
                    });

                    boxItem.addSubItem(groupItem);
                }
            }

            // ── Sub-boxes ─────────────────────────────────────────────────────
            if (box.getSubBoxes() != null) {
                for (Box subBox : box.getSubBoxes()) {
                    String rawSubBoxName = subBox.getName() == null ? "" : subBox.getName();
                    String subBoxName = OwnedCardsCollection.extractName(rawSubBoxName, '=');
                    NavigationItem subBoxItem =
                            NavigationHelper.createNavigationItem(subBoxName, 1);
                    subBoxItem.setUserData(subBox);
                    subBoxItem.setItemType(NavigationItem.ItemType.BOX);
                    attachNavItemDropHandlers(subBoxItem, subBox);
                    enableNavDragSource(subBoxItem, subBox);
                    enableNavItemAsNavDndTarget(subBoxItem, subBox,
                            (dragged, pos) -> handleMyCollectionNavDrop(
                                    dragged, subBox, pos, finalCollection));
                    subBoxItem.setOnLabelClicked(evt -> {
                        SelectionManager.setLastClickedNavigationItem(subBox);
                        NavigationHelper.navigateToTree(myCollectionTreeView, subBoxName);
                    });

                    boolean subBoxHasUnsorted;
                    String subBoxMsg;
                    if (CardTreeCell.isIncompleteMarkingEnabled() && boxHasMissingPrintCode(subBox)) {
                        subBoxHasUnsorted = true;
                        subBoxMsg = "This box contains cards without a print code.";
                    } else if (CardTreeCell.isIncompleteMarkingEnabled()
                            && boxHasIncompleteCards(subBox)) {
                        subBoxHasUnsorted = true;
                        subBoxMsg = "This box contains cards with no condition or rarity set.";
                    } else {
                        subBoxHasUnsorted = boxHasUnsortedCards(subBox, subBoxName);
                        subBoxMsg = "This box contains unsorted cards.";
                    }
                    NavigationHelper.applyNavigationItemHighlight(subBoxItem, subBoxHasUnsorted,
                            subBoxMsg);
                    if (subBoxHasUnsorted && !boxHasUnsorted) {
                        NavigationHelper.applyNavigationItemHighlight(boxItem, true, subBoxMsg);
                        boxHasUnsorted = true;
                    }

                    ContextMenu subBoxContextMenu =
                            NavigationContextMenuBuilder.forMyCollectionBox(subBox, collection);
                    subBoxItem.setOnContextMenuRequested(event -> {
                        subBoxContextMenu.show(subBoxItem, event.getScreenX(), event.getScreenY());
                        event.consume();
                    });

                    if (subBox.getContent() != null) {
                        for (CardsGroup group : subBox.getContent()) {
                            String rawGroupName = group.getName() == null ? "" : group.getName();
                            String groupName =
                                    OwnedCardsCollection.extractName(rawGroupName, '-');
                            if (groupName.isEmpty()) {
                                continue;
                            }
                            NavigationItem groupItem =
                                    NavigationHelper.createNavigationItem(groupName, 2);
                            groupItem.setUserData(group);
                            groupItem.setItemType(NavigationItem.ItemType.CATEGORY);
                            attachNavItemDropHandlers(groupItem, group);
                            enableNavDragSource(groupItem, group);
                            enableNavItemAsNavDndTarget(groupItem, group,
                                    (dragged, pos) -> handleMyCollectionNavDrop(
                                            dragged, group, pos, finalCollection));
                            groupItem.setOnLabelClicked(evt -> {
                                SelectionManager.setLastClickedNavigationItem(group);
                                NavigationHelper.navigateToTree(
                                        myCollectionTreeView, subBoxName, groupName);
                            });

                            boolean groupHasUnsorted;
                            String groupMsg;
                            if (CardTreeCell.isIncompleteMarkingEnabled()
                                    && groupHasMissingPrintCode(group)) {
                                groupHasUnsorted = true;
                                groupMsg = "This category contains cards without a print code.";
                            } else if (CardTreeCell.isIncompleteMarkingEnabled()
                                    && groupHasIncompleteCards(group)) {
                                groupHasUnsorted = true;
                                groupMsg =
                                        "This category contains cards with no condition or rarity set.";
                            } else {
                                groupHasUnsorted = groupHasUnsortedCards(group, groupName);
                                groupMsg = "This category contains unsorted cards.";
                            }
                            NavigationHelper.applyNavigationItemHighlight(groupItem,
                                    groupHasUnsorted, groupMsg);
                            if (groupHasUnsorted && !subBoxHasUnsorted) {
                                NavigationHelper.applyNavigationItemHighlight(subBoxItem, true,
                                        groupMsg);
                                subBoxHasUnsorted = true;
                            }
                            if (groupHasUnsorted && !boxHasUnsorted) {
                                NavigationHelper.applyNavigationItemHighlight(boxItem, true,
                                        groupMsg);
                                boxHasUnsorted = true;
                            }

                            ContextMenu groupContextMenu =
                                    NavigationContextMenuBuilder.forMyCollectionCategory(
                                            group, subBox, collection);
                            groupItem.setOnContextMenuRequested(event -> {
                                groupContextMenu.show(groupItem, event.getScreenX(),
                                        event.getScreenY());
                                event.consume();
                            });

                            subBoxItem.addSubItem(groupItem);
                        }
                    }

                    enableBoxFooterDropZone(subBoxItem, subBox, finalCollection);
                    boxItem.addSubItem(subBoxItem);
                }
            }

            enableBoxFooterDropZone(boxItem, box, finalCollection);
            navigationMenu.addItem(boxItem);
        }

        menuVBox.getChildren().add(navigationMenu);

        ContextMenu emptyAreaContextMenu =
                NavigationContextMenuBuilder.forMyCollectionEmpty();
        menuVBox.setOnContextMenuRequested(event ->
                emptyAreaContextMenu.show(menuVBox, event.getScreenX(), event.getScreenY()));
    }

    // ── Nav drag-and-drop ─────────────────────────────────────────────────────

    /**
     * Handles a NAV drag-drop onto a My Collection navigation item.
     *
     * <p>Rules:
     * <ul>
     *   <li>Box → Box (BEFORE/AFTER): reorder at the same hierarchy level.</li>
     *   <li>Box → Box (INTO): nest the dragged box as a sub-box of the target.</li>
     *   <li>CardsGroup → CardsGroup (BEFORE/AFTER): reorder across box boundaries.</li>
     *   <li>CardsGroup → Box (INTO / AFTER): move category into that box.</li>
     * </ul>
     *
     * @param dragged    the model object being dragged
     * @param target     the model object of the nav item being dropped onto
     * @param pos        resolved drop position
     * @param collection the live OwnedCardsCollection
     */
    public void handleMyCollectionNavDrop(Object dragged, Object target,
                                          NavigationItem.DropPosition pos,
                                          OwnedCardsCollection collection) {
        if (dragged == null || target == null || dragged == target) {
            return;
        }

        boolean changed = false;

        if (dragged instanceof Box draggedBox) {
            if (target instanceof Box targetBox) {
                switch (pos) {
                    case BEFORE -> changed = NavigationDragDrop.moveBoxBefore(draggedBox, targetBox, collection);
                    case AFTER -> changed = NavigationDragDrop.moveBoxAfter(draggedBox, targetBox, collection);
                    case INTO -> changed = NavigationDragDrop.nestBoxInto(draggedBox, targetBox, collection);
                }
            } else if (target instanceof CardsGroup targetGroup) {
                Box parentBox = NavigationDragDrop.findCategoryParent(targetGroup, collection);
                if (parentBox != null) {
                    changed = NavigationDragDrop.nestBoxInto(draggedBox, parentBox, collection);
                }
            }
        } else if (dragged instanceof CardsGroup draggedGroup) {
            if (target instanceof CardsGroup targetGroup) {
                switch (pos) {
                    case BEFORE -> changed = NavigationDragDrop.moveCategoryBefore(
                            draggedGroup, targetGroup, collection);
                    case AFTER, INTO -> changed = NavigationDragDrop.moveCategoryAfter(
                            draggedGroup, targetGroup, collection);
                }
            } else if (target instanceof Box targetBox) {
                changed = NavigationDragDrop.moveCategoryIntoBox(draggedGroup, targetBox, collection);
            }
        }

        if (changed) {
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionStructure();
        }
    }

    /**
     * Adds and wires a footer drop-zone strip to {@code boxItem}.
     *
     * <p>The footer is an 8 px strip below all sub-items. Top half → INTO (nest inside box),
     * bottom half → AFTER (place after box at the same hierarchy level).
     *
     * @param boxItem    the navigation item for the box
     * @param box        the model Box
     * @param collection the live OwnedCardsCollection
     */
    public void enableBoxFooterDropZone(NavigationItem boxItem, Box box,
                                        OwnedCardsCollection collection) {
        HBox footer = boxItem.addFooterZone();

        footer.setOnDragOver(event -> {
            if ("NAV".equals(DragDropManager.getDragSourcePane())
                    && event.getDragboard().hasString()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
                boolean intoHalf = event.getY() < footer.getHeight() / 2.0;
                if (intoHalf) {
                    footer.setStyle(
                            "-fx-background-color: transparent;" +
                                    "-fx-border-color: #cdfc04 transparent transparent transparent;" +
                                    "-fx-border-width: 2 0 2 0;");
                } else {
                    footer.setStyle(
                            "-fx-background-color: transparent;" +
                                    "-fx-border-color: transparent transparent #cdfc04 transparent;" +
                                    "-fx-border-width: 2 0 2 0;");
                }
            }
            event.consume();
        });

        footer.setOnDragExited(event -> {
            footer.setStyle(
                    "-fx-background-color: transparent; -fx-border-color: transparent;" +
                            " -fx-border-width: 2 0 2 0;");
            event.consume();
        });

        footer.setOnDragDropped(event -> {
            Object dragged = DragDropManager.getDraggedNavObject();
            boolean intoHalf = event.getY() < footer.getHeight() / 2.0;
            boolean changed = false;

            if (dragged instanceof Box draggedBox && dragged != box) {
                if (intoHalf) {
                    changed = NavigationDragDrop.nestBoxInto(draggedBox, box, collection);
                } else {
                    changed = NavigationDragDrop.moveBoxAfter(draggedBox, box, collection);
                }
            } else if (dragged instanceof CardsGroup draggedGroup) {
                if (intoHalf) {
                    changed = NavigationDragDrop.moveCategoryIntoBox(draggedGroup, box, collection);
                } else {
                    List<CardsGroup> content = box.getContent();
                    if (content != null && !content.isEmpty()) {
                        CardsGroup lastGroup = content.get(content.size() - 1);
                        if (lastGroup != draggedGroup) {
                            changed = NavigationDragDrop.moveCategoryAfter(
                                    draggedGroup, lastGroup, collection);
                        }
                    } else {
                        changed = NavigationDragDrop.moveCategoryIntoBox(draggedGroup, box, collection);
                    }
                }
            }

            footer.setStyle(
                    "-fx-background-color: transparent; -fx-border-color: transparent;" +
                            " -fx-border-width: 2 0 2 0;");
            if (changed) {
                UserInterfaceFunctions.markMyCollectionDirty();
                UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                UserInterfaceFunctions.refreshOwnedCollectionStructure();
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }

    // ── Inline rename ─────────────────────────────────────────────────────────

    /**
     * Starts an inline rename on a freshly created Box or CardsGroup.
     *
     * <p>Confirm → updates the model name and performs a full structure refresh.
     * Cancel → removes the element from the model and performs a full structure refresh.
     *
     * @param navItem  the nav item whose label hosts the rename field
     * @param modelObj the Box or CardsGroup to rename
     */
    public void startAddRename(NavigationItem navItem, Object modelObj) {
        navItem.startInlineRename(
                newName -> {
                    if (modelObj instanceof Box) {
                        ((Box) modelObj).setName(newName);
                    } else if (modelObj instanceof CardsGroup) {
                        ((CardsGroup) modelObj).setName(newName);
                    }
                    UserInterfaceFunctions.markMyCollectionDirty();
                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    UserInterfaceFunctions.refreshOwnedCollectionStructure();
                },
                () -> {
                    try {
                        OwnedCardsCollection owned = OuicheList.getMyCardsCollection();
                        if (owned != null) {
                            if (modelObj instanceof Box cancelledBox) {
                                if (!owned.getOwnedCollection().remove(cancelledBox)) {
                                    for (Box topBox : owned.getOwnedCollection()) {
                                        if (topBox.getSubBoxes() != null
                                                && topBox.getSubBoxes().remove(cancelledBox)) {
                                            break;
                                        }
                                    }
                                }
                            } else if (modelObj instanceof CardsGroup cancelledGroup) {
                                outer:
                                for (Box topBox : owned.getOwnedCollection()) {
                                    if (topBox.getContent() != null
                                            && topBox.getContent().remove(cancelledGroup)) {
                                        break;
                                    }
                                    if (topBox.getSubBoxes() != null) {
                                        for (Box subBox : topBox.getSubBoxes()) {
                                            if (subBox.getContent() != null
                                                    && subBox.getContent().remove(cancelledGroup)) {
                                                break outer;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    UserInterfaceFunctions.refreshOwnedCollectionStructure();
                }
        );
    }

    // ── Tree-item builders ────────────────────────────────────────────────────

    /**
     * Builds a {@link DataTreeItem} for a {@link Box}, recursively adding its
     * groups and sub-boxes as children.
     *
     * @param box the Box to represent
     * @return the populated DataTreeItem
     */
    public DataTreeItem<Object> createBoxTreeItem(Box box) {
        String cleanName = OwnedCardsCollection.extractName(
                box.getName() == null ? "" : box.getName(), '=');
        DataTreeItem<Object> boxItem = new DataTreeItem<>(cleanName, box);
        boxItem.setExpanded(true);
        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                DataTreeItem<Object> groupItem = createGroupTreeItem(group);
                boxItem.getChildren().add(groupItem);
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box subBox : box.getSubBoxes()) {
                DataTreeItem<Object> subBoxItem = createBoxTreeItem(subBox);
                boxItem.getChildren().add(subBoxItem);
            }
        }
        return boxItem;
    }

    /**
     * Builds a {@link DataTreeItem} for a {@link CardsGroup}.
     *
     * @param group the CardsGroup to represent
     * @return the DataTreeItem
     */
    public DataTreeItem<Object> createGroupTreeItem(CardsGroup group) {
        String displayName = OwnedCardsCollection.extractName(
                group.getName() == null ? "" : group.getName(), '-');
        DataTreeItem<Object> groupItem = new DataTreeItem<>(displayName, group);
        groupItem.setExpanded(true);
        return groupItem;
    }

    // ── Scroll helpers ────────────────────────────────────────────────────────

    /**
     * Scrolls the My Collection TreeView so the bottom of the group cell for
     * {@code handlerTarget} is visible after an "Add to…" action.
     *
     * @param handlerTarget the target path string (box/group names joined by " / ")
     */
    public void scrollToNewCardInGroup(String handlerTarget) {
        if (myCollectionTreeView == null || handlerTarget == null) {
            return;
        }

        String[] parts = handlerTarget.split("\\s*/\\s*");

        Platform.runLater(() -> {
            if (myCollectionTreeView == null) {
                return;
            }
            TreeItem<String> root = myCollectionTreeView.getRoot();
            if (root == null) {
                return;
            }

            TreeItem<String> target = NavigationHelper.findTreeItemByPath(root, parts, 0);
            if (target == null && parts.length > 1) {
                target = NavigationHelper.findTreeItemByPath(
                        root, new String[]{parts[parts.length - 1]}, 0);
            }
            if (target == null) {
                target = NavigationHelper.findTreeItemByPath(root, new String[]{parts[0]}, 0);
            }
            if (target == null) {
                return;
            }

            if (target instanceof DataTreeItem) {
                Object data = ((DataTreeItem<?>) target).getData();
                if (!(data instanceof CardsGroup)) {
                    for (TreeItem<String> child : target.getChildren()) {
                        if (child instanceof DataTreeItem
                                && ((DataTreeItem<?>) child).getData() instanceof CardsGroup) {
                            target = child;
                            break;
                        }
                    }
                }
            }

            for (TreeItem<String> ancestor = target.getParent();
                 ancestor != null;
                 ancestor = ancestor.getParent()) {
                ancestor.setExpanded(true);
            }

            final int targetRow = myCollectionTreeView.getRow(target);
            if (targetRow < 0) {
                return;
            }

            javafx.scene.control.skin.VirtualFlow<?> virtualFlow = getVirtualFlow();
            boolean rowInView = false;
            if (virtualFlow != null) {
                int firstVisible = virtualFlow.getFirstVisibleCell() != null
                        ? virtualFlow.getFirstVisibleCell().getIndex() : -1;
                int lastVisible = virtualFlow.getLastVisibleCell() != null
                        ? virtualFlow.getLastVisibleCell().getIndex() : -1;
                rowInView = firstVisible >= 0
                        && targetRow >= firstVisible
                        && targetRow <= lastVisible;
            }

            if (!rowInView) {
                myCollectionTreeView.scrollTo(targetRow);
            }

            Platform.runLater(() -> adjustScrollToShowCellBottom(targetRow));
        });
    }

    /**
     * Scrolls just enough so the bottom of the target group's cell (= the last card)
     * is visible. Does nothing if it is already visible or above the viewport.
     *
     * @param handlerTarget the target path string
     */
    public void scrollToLastCardInGroup(String handlerTarget) {
        if (myCollectionTreeView == null || handlerTarget == null) {
            return;
        }

        String[] parts = handlerTarget.split("\\s*/\\s*");
        TreeItem<String> root = myCollectionTreeView.getRoot();
        if (root == null) {
            return;
        }

        TreeItem<String> target = NavigationHelper.findTreeItemByPath(root, parts, 0);
        if (target == null && parts.length > 1) {
            target = NavigationHelper.findTreeItemByPath(
                    root, new String[]{parts[parts.length - 1]}, 0);
        }
        if (target == null) {
            target = NavigationHelper.findTreeItemByPath(root, new String[]{parts[0]}, 0);
        }
        if (target == null) {
            return;
        }

        if (target instanceof DataTreeItem
                && !(((DataTreeItem<?>) target).getData() instanceof CardsGroup)) {
            for (TreeItem<String> child : target.getChildren()) {
                if (child instanceof DataTreeItem
                        && ((DataTreeItem<?>) child).getData() instanceof CardsGroup) {
                    target = child;
                    break;
                }
            }
        }

        for (TreeItem<String> ancestor = target.getParent();
             ancestor != null;
             ancestor = ancestor.getParent()) {
            ancestor.setExpanded(true);
        }

        final int targetRow = myCollectionTreeView.getRow(target);
        if (targetRow < 0) {
            return;
        }

        javafx.scene.control.skin.VirtualFlow<?> virtualFlow = getVirtualFlow();
        int firstVisible = virtualFlow != null && virtualFlow.getFirstVisibleCell() != null
                ? virtualFlow.getFirstVisibleCell().getIndex() : -1;
        int lastVisible = virtualFlow != null && virtualFlow.getLastVisibleCell() != null
                ? virtualFlow.getLastVisibleCell().getIndex() : -1;

        if (firstVisible >= 0 && targetRow >= firstVisible && targetRow <= lastVisible) {
            adjustScrollToShowCellBottom(targetRow);
        } else if (lastVisible >= 0 && targetRow > lastVisible) {
            myCollectionTreeView.scrollTo(targetRow);
            Platform.runLater(() -> adjustScrollToShowCellBottom(targetRow));
        }
    }

    /**
     * Scrolls down by exactly the amount needed to reveal the bottom edge of the rendered
     * cell at {@code row}. No-ops if the bottom is already within the viewport.
     *
     * @param row the 0-based row index in the tree view
     */
    public void adjustScrollToShowCellBottom(int row) {
        if (myCollectionTreeView == null) {
            return;
        }

        Bounds treeBounds = myCollectionTreeView.localToScene(
                myCollectionTreeView.getBoundsInLocal());
        if (treeBounds == null) {
            return;
        }
        double treeBottom = treeBounds.getMaxY();

        for (Node node : myCollectionTreeView.lookupAll(".card-tree-cell")) {
            if (!(node instanceof TreeCell)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            TreeCell<String> cell = (TreeCell<String>) node;
            if (cell.isEmpty() || cell.getTreeItem() == null) {
                continue;
            }
            if (myCollectionTreeView.getRow(cell.getTreeItem()) != row) {
                continue;
            }

            Bounds cellBounds = cell.localToScene(cell.getBoundsInLocal());
            if (cellBounds == null) {
                break;
            }

            double cellBottom = cellBounds.getMaxY();
            if (cellBottom > treeBottom) {
                javafx.scene.control.skin.VirtualFlow<?> virtualFlow = getVirtualFlow();
                if (virtualFlow != null) {
                    virtualFlow.scrollPixels(cellBottom - treeBottom);
                }
            }
            break;
        }
    }

    /**
     * Returns the VirtualFlow inside the My Collection TreeView, or {@code null}
     * if it is not yet available (tree not yet rendered).
     */
    public javafx.scene.control.skin.VirtualFlow<?> getVirtualFlow() {
        if (myCollectionTreeView == null) {
            return null;
        }
        try {
            for (Node node : myCollectionTreeView.lookupAll(".virtual-flow")) {
                if (node instanceof javafx.scene.control.skin.VirtualFlow) {
                    return (javafx.scene.control.skin.VirtualFlow<?>) node;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ── Quality-check helpers ─────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code group} contains at least one card that needs
     * sorting according to its display name.
     *
     * @param group            the group to inspect
     * @param groupDisplayName the sanitized display name (no = or -)
     */
    public boolean groupHasUnsortedCards(CardsGroup group, String groupDisplayName) {
        if (group == null || group.getCardList() == null) {
            return false;
        }
        for (CardElement cardElement : group.getCardList()) {
            if (cardElement == null || cardElement.getCard() == null) {
                continue;
            }
            try {
                if (CardQualityService.computeCardNeedsSorting(
                        cardElement.getCard(), groupDisplayName)) {
                    return true;
                }
                if (CardQualityService.computeCardNeedsSortingWithUpgrade(
                        cardElement, groupDisplayName)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code group} contains at least one card that needs
     * sorting (single-arg overload — uses the group's own name).
     */
    public boolean groupHasUnsortedCards(CardsGroup group) {
        if (group == null) {
            return false;
        }
        List<CardElement> cardList;
        try {
            cardList = group.getCardList();
        } catch (Exception exception) {
            return false;
        }
        if (cardList == null || cardList.isEmpty()) {
            return false;
        }
        String groupName = group.getName() == null ? "" : group.getName();
        for (int index = 0; index < cardList.size(); index++) {
            CardElement cardElement = cardList.get(index);
            if (cardElement == null) {
                continue;
            }
            Card card = cardElement.getCard();
            if (card == null) {
                continue;
            }
            if (cardNeedsSortingCached(card, group, index, groupName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code box} or any nested sub-box/group contains
     * at least one card that needs sorting.
     *
     * @param box            the Box to inspect
     * @param boxDisplayName the sanitized display name (no =)
     */
    public boolean boxHasUnsortedCards(Box box, String boxDisplayName) {
        if (box == null) {
            return false;
        }
        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                String rawGroupName = group.getName() == null ? "" : group.getName();
                String groupName = OwnedCardsCollection.extractName(rawGroupName, '-');
                if (groupHasUnsortedCards(group, groupName)) {
                    return true;
                }
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box subBox : box.getSubBoxes()) {
                String subBoxDisplayName = OwnedCardsCollection.extractName(
                        subBox.getName() == null ? "" : subBox.getName(), '=');
                if (boxHasUnsortedCards(subBox, subBoxDisplayName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code box} or any of its groups/sub-boxes contains
     * at least one card that needs sorting (single-arg overload using reflection).
     */
    public boolean boxHasUnsortedCards(Box box) {
        if (box == null) {
            return false;
        }
        String boxName = box.getName() == null ? "" : box.getName();

        try {
            Method getCardListMethod = null;
            try {
                getCardListMethod = box.getClass().getMethod("getCardList");
            } catch (NoSuchMethodException ignored) {
            }

            if (getCardListMethod != null) {
                Object maybeList = getCardListMethod.invoke(box);
                if (maybeList instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<?> rawList = (List<?>) maybeList;
                    if (containsUnsortedFromRawList(rawList, boxName)) {
                        return true;
                    }
                }
            } else {
                Field cardListField = null;
                try {
                    cardListField = box.getClass().getDeclaredField("cardList");
                } catch (NoSuchFieldException firstException) {
                    try {
                        cardListField = box.getClass().getDeclaredField("cardsList");
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                if (cardListField != null) {
                    cardListField.setAccessible(true);
                    Object maybeList = cardListField.get(box);
                    if (maybeList instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<?> rawList = (List<?>) maybeList;
                        if (containsUnsortedFromRawList(rawList, boxName)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception exception) {
            logger.debug("boxHasUnsortedCards: reflection check failed for box '{}': {}",
                    boxName, exception.toString());
        }

        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                if (groupHasUnsortedCards(group)) {
                    return true;
                }
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box subBox : box.getSubBoxes()) {
                if (subBox != null && boxHasUnsortedCards(subBox)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when {@code group} has a card whose {@link Card#getPrintCode()}
     * is null or blank.
     */
    public boolean groupHasMissingPrintCode(CardsGroup group) {
        if (group == null || group.getCardList() == null) {
            return false;
        }
        for (CardElement cardElement : group.getCardList()) {
            if (cardElement == null || cardElement.getCard() == null) {
                continue;
            }
            Card card = cardElement.getCard();
            if (card.getPrintCode() == null || card.getPrintCode().isBlank()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when {@code group} has a card that has a printCode
     * but is missing condition or rarity.
     */
    public boolean groupHasIncompleteCards(CardsGroup group) {
        if (group == null || group.getCardList() == null) {
            return false;
        }
        for (CardElement cardElement : group.getCardList()) {
            if (cardElement == null || cardElement.getCard() == null) {
                continue;
            }
            Card card = cardElement.getCard();
            if (card.getPrintCode() == null || card.getPrintCode().isBlank()) {
                continue;
            }
            if (cardElement.getCondition() == null || cardElement.getRarity() == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when {@code box} or any nested sub-box/group has a
     * card with a missing printCode.
     */
    public boolean boxHasMissingPrintCode(Box box) {
        if (box == null) {
            return false;
        }
        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                if (groupHasMissingPrintCode(group)) {
                    return true;
                }
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box subBox : box.getSubBoxes()) {
                if (subBox != null && boxHasMissingPrintCode(subBox)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when {@code box} or any nested sub-box/group has a
     * card that has a printCode but is missing condition or rarity.
     */
    public boolean boxHasIncompleteCards(Box box) {
        if (box == null) {
            return false;
        }
        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                if (groupHasIncompleteCards(group)) {
                    return true;
                }
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box subBox : box.getSubBoxes()) {
                if (subBox != null && boxHasIncompleteCards(subBox)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Cached, position-aware wrapper that returns whether the card at a given
     * index inside a container needs sorting.
     */
    private boolean cardNeedsSortingCached(Card card, Object container, int index,
                                           String elementName) {
        if (card == null) {
            return false;
        }
        if (container == null || index < 0) {
            return CardQualityService.computeCardNeedsSorting(card, elementName);
        }

        List<Boolean> cacheList = positionSortCache.computeIfAbsent(container,
                key -> Collections.synchronizedList(new ArrayList<>()));

        synchronized (cacheList) {
            while (cacheList.size() <= index) {
                cacheList.add(null);
            }
            Boolean cached = cacheList.get(index);
            if (cached != null) {
                return cached;
            }
        }

        boolean result = CardQualityService.computeCardNeedsSorting(card, elementName);

        synchronized (cacheList) {
            cacheList.set(index, result);
        }
        return result;
    }

    /**
     * Scans a raw (untyped) list for any entry that needs sorting.
     */
    private boolean containsUnsortedFromRawList(List<?> rawList, String elementName) {
        if (rawList == null || rawList.isEmpty()) {
            return false;
        }
        for (int index = 0; index < rawList.size(); index++) {
            Object entry = rawList.get(index);
            if (entry == null) {
                continue;
            }
            Card card = null;
            if (entry instanceof CardElement) {
                card = ((CardElement) entry).getCard();
            } else if (entry instanceof Card) {
                card = (Card) entry;
            } else {
                continue;
            }
            if (card == null) {
                continue;
            }
            if (cardNeedsSortingCached(card, rawList, index, elementName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tries to load the OwnedCardsCollection, falling back to loading from file
     * if the in-memory instance is null.
     */
    private OwnedCardsCollection loadOwnedCollection() {
        OwnedCardsCollection collection = null;
        try {
            collection = OuicheList.getMyCardsCollection();
        } catch (Throwable ignored) {
        }
        if (collection == null) {
            try {
                UserInterfaceFunctions.loadCollectionFile();
            } catch (Throwable ignored) {
            }
            try {
                collection = OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
        }
        return collection;
    }

    // ── Nav DnD wiring (shared with DecksCollectionsController) ──────────────

    private void attachNavItemDropHandlers(NavigationItem navItem, Object modelObj) {
        navItem.setOnDragOver(event -> {
            if (event.getDragboard().hasString()
                    && DragDropManager.getDragSourcePane() != null) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            }
            event.consume();
        });

        navItem.setOnDragDropped(event -> {
            String sourcePane = DragDropManager.getDragSourcePane();
            if (sourcePane == null) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            coordinator.doCardPasteOnNavItem(modelObj, sourcePane, event);
            event.setDropCompleted(true);
            event.consume();
        });
    }

    private void enableNavDragSource(NavigationItem navItem, Object modelObj) {
        Label label = navItem.getLabel();
        label.setOnDragDetected(event -> {
            javafx.scene.input.Dragboard dragboard =
                    label.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
            javafx.scene.input.ClipboardContent content =
                    new javafx.scene.input.ClipboardContent();
            content.putString("NAV");
            dragboard.setContent(content);
            DragDropManager.startNavDrag(modelObj);
            event.consume();
        });
        label.setOnDragDone(event -> {
            DragDropManager.clearCurrentlyDraggedCard();
            navItem.clearDropIndicator();
            event.consume();
        });
    }

    private void enableNavItemAsNavDndTarget(NavigationItem navItem, Object modelObj,
                                             java.util.function.BiConsumer<Object,
                                                     NavigationItem.DropPosition> onNavDrop) {
        navItem.setOnDragOver(event -> {
            String sourcePane = DragDropManager.getDragSourcePane();
            if (event.getDragboard().hasString() && sourcePane != null) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
                if ("NAV".equals(sourcePane)) {
                    boolean isContainer = modelObj instanceof Box;
                    NavigationItem.DropPosition dropPos =
                            NavigationHelper.resolveDropPosition(navItem, event.getY(), isContainer);
                    navItem.showDropIndicator(dropPos);
                }
            }
            event.consume();
        });

        navItem.setOnDragExited(event -> {
            navItem.clearDropIndicator();
            event.consume();
        });

        navItem.setOnDragDropped(event -> {
            String sourcePane = DragDropManager.getDragSourcePane();
            if (sourcePane == null) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            if ("NAV".equals(sourcePane)) {
                Object dragged = DragDropManager.getDraggedNavObject();
                if (dragged != null && dragged != modelObj) {
                    boolean isContainer = modelObj instanceof Box;
                    NavigationItem.DropPosition dropPos =
                            NavigationHelper.resolveDropPosition(navItem, event.getY(), isContainer);
                    navItem.clearDropIndicator();
                    onNavDrop.accept(dragged, dropPos);
                }
            } else {
                coordinator.doCardPasteOnNavItem(modelObj, sourcePane, event);
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }

    // ── Accessor ──────────────────────────────────────────────────────────────

    /**
     * Returns the currently displayed My Collection TreeView (may be null before first display).
     */
    public TreeView<String> getMyCollectionTreeView() {
        return myCollectionTreeView;
    }
}