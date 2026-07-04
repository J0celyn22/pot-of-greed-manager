package Controller;

import Model.CardsLists.Box;
import Model.CardsLists.CardsGroup;
import Model.CardsLists.OuicheList;
import Model.CardsLists.OwnedCardsCollection;
import View.*;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

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
 *   <li>Scroll helpers for bringing newly added cards into view.</li>
 * </ul>
 *
 * <p>Quality-check predicates (unsorted cards, missing print codes, incomplete
 * cards) used to highlight nav items live in {@link MyCollectionQualityChecks},
 * a stateless helper with no dependency on this controller's TreeView, tab, or
 * coordinator state.
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
     * @param coordinator       the thin coordinator (used to route display callbacks)
     * @param cardWidthProperty shared card-width property (bound to all cell factories)
     * @param cardHeightProperty shared card-height property
     * @param myCollectionTab   the tab UI container for My Collection
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
    //
    // Menu construction and NAV drag-and-drop wiring moved to
    // MyCollectionNavigationPopulator. This method remains the sole public
    // entry point, retaining the collection-load/null-check that callers
    // (RealMainController, SaveStateCoordinator) rely on.

    /**
     * Rebuilds the left-hand navigation menu for My Collection from the model.
     *
     * @throws Exception if the owned collection cannot be loaded
     */
    public void populateMyCollectionMenu() throws Exception {
        VBox menuVBox = myCollectionTab.getMenuVBox();
        menuVBox.getChildren().clear();

        OwnedCardsCollection collection = loadOwnedCollection();
        if (collection == null || collection.getOwnedCollection() == null) {
            logger.warn("OwnedCardsCollection is not available.");
            menuVBox.getChildren().add(new NavigationMenu());
            return;
        }

        MyCollectionNavigationPopulator.populateMenu(coordinator, myCollectionTreeView, menuVBox, collection);
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

    // ── Private helpers ───────────────────────────────────────────────────────
    //
    // Quality-check predicates (unsorted / missing print code / incomplete
    // cards) moved to MyCollectionQualityChecks.

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

    // ── Accessor ──────────────────────────────────────────────────────────────

    /** Returns the currently displayed My Collection TreeView (may be null before first display). */
    public TreeView<String> getMyCollectionTreeView() {
        return myCollectionTreeView;
    }
}