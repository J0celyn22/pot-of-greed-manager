package Controller;

import Model.CardsLists.Box;
import Model.CardsLists.CardsGroup;
import Model.CardsLists.OwnedCardsCollection;
import View.CardTreeCell;
import View.MyCollectionNavMenuBuilder;
import View.NavigationItem;
import View.NavigationMenu;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * MyCollectionNavigationPopulator — builds the left-hand navigation menu for
 * the My Collection tab (Boxes, sub-Boxes, and CardsGroups as nested
 * {@link NavigationItem}s) and wires their NAV drag-and-drop behavior.
 *
 * <p>Extracted from {@link MyCollectionController}, which retains ownership of
 * the shared TreeView, tab, and coordinator state and remains the sole public
 * entry point ({@link MyCollectionController#populateMyCollectionMenu()}) that
 * callers outside the Controller package use.</p>
 */
public final class MyCollectionNavigationPopulator {

    /**
     * Utility class — no instances.
     */
    private MyCollectionNavigationPopulator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Builds the full nav menu for {@code collection} into {@code menuVBox}.
     *
     * @param coordinator          the app coordinator, needed for nav drag-and-drop wiring
     * @param myCollectionTreeView the live My Collection TreeView, used to resolve
     *                             label-click navigation targets
     * @param menuVBox             the container to populate; assumed already cleared by the caller
     * @param collection           the loaded, non-null OwnedCardsCollection
     */
    public static void populateMenu(RealMainController coordinator,
                                    TreeView<String> myCollectionTreeView,
                                    VBox menuVBox,
                                    OwnedCardsCollection collection) {
        NavigationMenu navigationMenu = new NavigationMenu();
        final OwnedCardsCollection finalCollection = collection;

        for (Box box : collection.getOwnedCollection()) {
            String rawBoxName = box.getName() == null ? "" : box.getName();
            String boxName = OwnedCardsCollection.extractName(rawBoxName, '=');

            NavigationItem boxItem = NavigationHelper.createNavigationItem(boxName, 0);
            boxItem.setUserData(box);
            boxItem.setItemType(NavigationItem.ItemType.BOX);
            NavigationDragDropWiring.attachNavItemDropHandlers(boxItem, box, coordinator);
            NavigationDragDropWiring.enableNavDragSource(boxItem, box);
            NavigationDragDropWiring.enableNavItemAsNavDndTarget(boxItem, box, coordinator,
                    (dragged, pos) -> handleMyCollectionNavDrop(dragged, box, pos, finalCollection));
            boxItem.setOnLabelClicked(evt -> {
                SelectionManager.setLastClickedNavigationItem(box);
                NavigationHelper.navigateToTree(myCollectionTreeView, boxName);
            });

            // ── Highlight logic ───────────────────────────────────────────────
            boolean boxHasUnsorted;
            String boxInitMsg;
            if (CardTreeCell.isIncompleteMarkingEnabled()
                    && MyCollectionQualityChecks.boxHasMissingPrintCode(box)) {
                boxHasUnsorted = true;
                boxInitMsg = "This box contains cards without a print code.";
            } else if (CardTreeCell.isIncompleteMarkingEnabled()
                    && MyCollectionQualityChecks.boxHasIncompleteCards(box)) {
                boxHasUnsorted = true;
                boxInitMsg = "This box contains cards with no condition or rarity set.";
            } else {
                boxHasUnsorted = MyCollectionQualityChecks.boxHasUnsortedCards(box, boxName);
                boxInitMsg = "This box contains unsorted cards.";
            }
            NavigationHelper.applyNavigationItemHighlight(boxItem, boxHasUnsorted, boxInitMsg);

            // ── Context menu ──────────────────────────────────────────────────
            ContextMenu boxContextMenu =
                    MyCollectionNavMenuBuilder.forMyCollectionBox(box, collection);
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
                    NavigationDragDropWiring.attachNavItemDropHandlers(groupItem, group, coordinator);
                    NavigationDragDropWiring.enableNavDragSource(groupItem, group);
                    NavigationDragDropWiring.enableNavItemAsNavDndTarget(groupItem, group, coordinator,
                            (dragged, pos) -> handleMyCollectionNavDrop(
                                    dragged, group, pos, finalCollection));
                    groupItem.setOnLabelClicked(evt -> {
                        SelectionManager.setLastClickedNavigationItem(group);
                        NavigationHelper.navigateToTree(myCollectionTreeView, boxName, groupName);
                    });

                    boolean groupHasUnsorted;
                    String groupMsg;
                    if (CardTreeCell.isIncompleteMarkingEnabled()
                            && MyCollectionQualityChecks.groupHasMissingPrintCode(group)) {
                        groupHasUnsorted = true;
                        groupMsg = "This category contains cards without a print code.";
                    } else if (CardTreeCell.isIncompleteMarkingEnabled()
                            && MyCollectionQualityChecks.groupHasIncompleteCards(group)) {
                        groupHasUnsorted = true;
                        groupMsg = "This category contains cards with no condition or rarity set.";
                    } else {
                        groupHasUnsorted = MyCollectionQualityChecks.groupHasUnsortedCards(group, groupName);
                        groupMsg = "This category contains unsorted cards.";
                    }
                    NavigationHelper.applyNavigationItemHighlight(groupItem, groupHasUnsorted, groupMsg);
                    if (groupHasUnsorted && !boxHasUnsorted) {
                        NavigationHelper.applyNavigationItemHighlight(boxItem, true, groupMsg);
                        boxHasUnsorted = true;
                    }

                    ContextMenu groupContextMenu =
                            MyCollectionNavMenuBuilder.forMyCollectionCategory(group, box, collection);
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
                    NavigationDragDropWiring.attachNavItemDropHandlers(subBoxItem, subBox, coordinator);
                    NavigationDragDropWiring.enableNavDragSource(subBoxItem, subBox);
                    NavigationDragDropWiring.enableNavItemAsNavDndTarget(subBoxItem, subBox, coordinator,
                            (dragged, pos) -> handleMyCollectionNavDrop(
                                    dragged, subBox, pos, finalCollection));
                    subBoxItem.setOnLabelClicked(evt -> {
                        SelectionManager.setLastClickedNavigationItem(subBox);
                        NavigationHelper.navigateToTree(myCollectionTreeView, subBoxName);
                    });

                    boolean subBoxHasUnsorted;
                    String subBoxMsg;
                    if (CardTreeCell.isIncompleteMarkingEnabled()
                            && MyCollectionQualityChecks.boxHasMissingPrintCode(subBox)) {
                        subBoxHasUnsorted = true;
                        subBoxMsg = "This box contains cards without a print code.";
                    } else if (CardTreeCell.isIncompleteMarkingEnabled()
                            && MyCollectionQualityChecks.boxHasIncompleteCards(subBox)) {
                        subBoxHasUnsorted = true;
                        subBoxMsg = "This box contains cards with no condition or rarity set.";
                    } else {
                        subBoxHasUnsorted = MyCollectionQualityChecks.boxHasUnsortedCards(subBox, subBoxName);
                        subBoxMsg = "This box contains unsorted cards.";
                    }
                    NavigationHelper.applyNavigationItemHighlight(subBoxItem, subBoxHasUnsorted,
                            subBoxMsg);
                    if (subBoxHasUnsorted && !boxHasUnsorted) {
                        NavigationHelper.applyNavigationItemHighlight(boxItem, true, subBoxMsg);
                        boxHasUnsorted = true;
                    }

                    ContextMenu subBoxContextMenu =
                            MyCollectionNavMenuBuilder.forMyCollectionBox(subBox, collection);
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
                            NavigationDragDropWiring.attachNavItemDropHandlers(
                                    groupItem, group, coordinator);
                            NavigationDragDropWiring.enableNavDragSource(groupItem, group);
                            NavigationDragDropWiring.enableNavItemAsNavDndTarget(
                                    groupItem, group, coordinator,
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
                                    && MyCollectionQualityChecks.groupHasMissingPrintCode(group)) {
                                groupHasUnsorted = true;
                                groupMsg = "This category contains cards without a print code.";
                            } else if (CardTreeCell.isIncompleteMarkingEnabled()
                                    && MyCollectionQualityChecks.groupHasIncompleteCards(group)) {
                                groupHasUnsorted = true;
                                groupMsg =
                                        "This category contains cards with no condition or rarity set.";
                            } else {
                                groupHasUnsorted =
                                        MyCollectionQualityChecks.groupHasUnsortedCards(group, groupName);
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
                                    MyCollectionNavMenuBuilder.forMyCollectionCategory(
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
                MyCollectionNavMenuBuilder.forMyCollectionEmpty();
        menuVBox.setOnContextMenuRequested(event ->
                emptyAreaContextMenu.show(menuVBox, event.getScreenX(), event.getScreenY()));
    }

    // ── Incremental highlight refresh ─────────────────────────────────────────
    //
    // Used by MyCollectionController's registerOwnedCollectionRefresher lambda
    // (wired to every card add, including scanner adds) so a single-card add
    // doesn't have to clear and rebuild the whole nav menu just to recompute
    // one CardsGroup's glow. Falls back to signalling false whenever anything
    // can't be resolved, so the caller can fall back to populateMenu.

    /**
     * Updates just the highlight state (colour and tooltip) of {@code addedGroup}'s nav item and
     * its ancestor box nav item(s), without rebuilding the nav menu.
     *
     * <p>Recomputes each level fresh via {@link MyCollectionQualityChecks} — scoped to only the
     * box/sub-box that actually received the card, not every box in the collection — and
     * propagates a child's highlight reason up to its parent exactly like {@link #populateMenu}
     * does, so the result matches what a full rebuild would have produced.
     *
     * @param menuVBox   the currently displayed nav menu (assumed already built by a prior
     *                   {@link #populateMenu} call)
     * @param collection the live OwnedCardsCollection
     * @param addedGroup the group a card was just added to
     * @return {@code true} if every affected nav item was found and updated in place;
     * {@code false} if the caller should fall back to {@link #populateMenu} instead
     * (e.g. the group has no nav item yet, or its box can't be located)
     */
    public static boolean refreshHighlightsForAddedGroup(VBox menuVBox,
                                                         OwnedCardsCollection collection,
                                                         CardsGroup addedGroup) {
        if (menuVBox == null || collection == null || collection.getOwnedCollection() == null
                || addedGroup == null) {
            return false;
        }

        NavigationItem groupNavItem = NavigationHelper.findNavItemInMenuVBox(menuVBox, addedGroup);
        if (groupNavItem == null) {
            return false;
        }
        Box immediateBox = NavigationDragDrop.findCategoryParent(addedGroup, collection);
        if (immediateBox == null) {
            return false;
        }
        NavigationItem immediateBoxNavItem =
                NavigationHelper.findNavItemInMenuVBox(menuVBox, immediateBox);
        if (immediateBoxNavItem == null) {
            return false;
        }

        String groupName = OwnedCardsCollection.extractName(
                addedGroup.getName() == null ? "" : addedGroup.getName(), '-');
        HighlightResult groupResult = computeGroupHighlight(addedGroup, groupName);
        NavigationHelper.applyNavigationItemHighlight(
                groupNavItem, groupResult.highlighted, groupResult.message);

        String immediateBoxName = OwnedCardsCollection.extractName(
                immediateBox.getName() == null ? "" : immediateBox.getName(), '=');
        HighlightResult immediateBoxResult = computeBoxHighlight(immediateBox, immediateBoxName);
        if (groupResult.highlighted && !immediateBoxResult.highlighted) {
            immediateBoxResult = groupResult;
        }
        NavigationHelper.applyNavigationItemHighlight(
                immediateBoxNavItem, immediateBoxResult.highlighted, immediateBoxResult.message);

        Box topLevelBox = NavigationDragDrop.findTopLevelBoxContaining(immediateBox, collection);
        if (topLevelBox == null) {
            return false;
        }
        if (topLevelBox != immediateBox) {
            NavigationItem topBoxNavItem =
                    NavigationHelper.findNavItemInMenuVBox(menuVBox, topLevelBox);
            if (topBoxNavItem == null) {
                return false;
            }
            String topBoxName = OwnedCardsCollection.extractName(
                    topLevelBox.getName() == null ? "" : topLevelBox.getName(), '=');
            HighlightResult topBoxResult = computeBoxHighlight(topLevelBox, topBoxName);
            if (immediateBoxResult.highlighted && !topBoxResult.highlighted) {
                topBoxResult = immediateBoxResult;
            }
            NavigationHelper.applyNavigationItemHighlight(
                    topBoxNavItem, topBoxResult.highlighted, topBoxResult.message);
        }

        return true;
    }

    /**
     * Mirrors the group-level highlight branch inside {@link #populateMenu}: missing print code,
     * then incomplete cards, then unsorted cards.
     */
    private static HighlightResult computeGroupHighlight(CardsGroup group, String groupName) {
        if (CardTreeCell.isIncompleteMarkingEnabled()
                && MyCollectionQualityChecks.groupHasMissingPrintCode(group)) {
            return new HighlightResult(true, "This category contains cards without a print code.");
        }
        if (CardTreeCell.isIncompleteMarkingEnabled()
                && MyCollectionQualityChecks.groupHasIncompleteCards(group)) {
            return new HighlightResult(
                    true, "This category contains cards with no condition or rarity set.");
        }
        boolean unsorted = MyCollectionQualityChecks.groupHasUnsortedCards(group, groupName);
        return new HighlightResult(unsorted, "This category contains unsorted cards.");
    }

    /**
     * Mirrors the box-level highlight branch inside {@link #populateMenu} (shared by both
     * top-level boxes and sub-boxes): missing print code, then incomplete cards, then unsorted
     * cards. Each check recurses through the box's own content and sub-boxes.
     */
    private static HighlightResult computeBoxHighlight(Box box, String boxName) {
        if (CardTreeCell.isIncompleteMarkingEnabled()
                && MyCollectionQualityChecks.boxHasMissingPrintCode(box)) {
            return new HighlightResult(true, "This box contains cards without a print code.");
        }
        if (CardTreeCell.isIncompleteMarkingEnabled()
                && MyCollectionQualityChecks.boxHasIncompleteCards(box)) {
            return new HighlightResult(
                    true, "This box contains cards with no condition or rarity set.");
        }
        boolean unsorted = MyCollectionQualityChecks.boxHasUnsortedCards(box, boxName);
        return new HighlightResult(unsorted, "This box contains unsorted cards.");
    }

    /**
     * Whether a nav item should be highlighted and, if so, which tooltip message to show.
     * Returned by {@link #computeGroupHighlight} and {@link #computeBoxHighlight} so
     * {@link #refreshHighlightsForAddedGroup} can propagate a child's highlight reason up to its
     * parent, matching {@link #populateMenu}'s own propagation logic.
     */
    private static final class HighlightResult {
        private final boolean highlighted;
        private final String message;

        private HighlightResult(boolean highlighted, String message) {
            this.highlighted = highlighted;
            this.message = message;
        }
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
    private static void handleMyCollectionNavDrop(Object dragged, Object target,
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
    private static void enableBoxFooterDropZone(NavigationItem boxItem, Box box,
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
}