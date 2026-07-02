package View;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static Utils.CardNameUtils.rebuildDecoratedName;
import static Utils.CardNameUtils.sanitize;

/**
 * Static factory for context menus shown on the My Collection navigation pane.
 *
 * <p>Shared styling and dialog helpers live in {@link NavigationContextMenuBuilder}.
 * This class contains only the My Collection specific entry points and their
 * private supporting methods.</p>
 */
public final class MyCollectionNavMenuBuilder {

    private static final Logger logger =
            LoggerFactory.getLogger(MyCollectionNavMenuBuilder.class);

    private MyCollectionNavMenuBuilder() {
    }

    // =========================================================================
    // Public entry points
    // =========================================================================

    /**
     * Context menu for a {@link Model.CardsLists.Box} node in the My Collection tree.
     * Provides Move, Add Box in/after, Add Category, Rename, Paste, and Remove actions.
     */
    public static ContextMenu forMyCollectionBox(
            Model.CardsLists.Box box,
            Model.CardsLists.OwnedCardsCollection owned) {

        ContextMenu contextMenu = NavigationContextMenuBuilder.styledContextMenu();

        Menu moveMenu = NavigationContextMenuBuilder.makeLazyMenu("Move to...");
        moveMenu.setOnShowing(evt -> populateBoxMoveMenu(moveMenu, box, owned));

        Runnable removeAction = () -> {
            if (!NavigationContextMenuBuilder.isBoxEmpty(box)
                    && !NavigationContextMenuBuilder.confirmRemoval("Box")) {
                return;
            }
            if (owned != null && owned.getOwnedCollection() != null) {
                if (owned.getOwnedCollection().remove(box)) {
                    Controller.UserInterfaceFunctions.markMyCollectionDirty();
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    NavigationContextMenuBuilder.refreshOwnedCollectionView();
                    return;
                }
                for (Model.CardsLists.Box parent : owned.getOwnedCollection()) {
                    if (parent == null || parent.getSubBoxes() == null) {
                        continue;
                    }
                    if (parent.getSubBoxes().remove(box)) {
                        Controller.UserInterfaceFunctions.markMyCollectionDirty();
                        Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                        NavigationContextMenuBuilder.refreshOwnedCollectionView();
                        return;
                    }
                }
            }
        };

        contextMenu.getItems().addAll(
                moveMenu,
                makeAddBoxInItem(box, owned),
                makeAddBoxAfterItem(box, owned),
                makeAddCategoryItem(box, null, owned),
                makeRenameItem(box, null, owned),
                NavigationContextMenuBuilder.makePasteItem(() -> {
                    Model.CardsLists.CardsGroup defaultGroup =
                            Controller.MenuActionHandler.getOrCreateDefaultGroup(box);
                    if (defaultGroup == null) {
                        return;
                    }
                    javafx.collections.ObservableList<Model.CardsLists.CardElement> observableList =
                            CardTreeCell.observableListFor(defaultGroup);
                    for (Model.CardsLists.CardElement clipboardElement
                            : Controller.CardClipboard.getContents()) {
                        if (clipboardElement != null) {
                            observableList.add(
                                    new Model.CardsLists.CardElement(clipboardElement));
                        }
                    }
                    CardTreeCell.triggerHeightAdjustment(defaultGroup);
                    Controller.UserInterfaceFunctions.markMyCollectionDirty();
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    NavigationContextMenuBuilder.refreshOwnedCollectionView();
                }),
                new SeparatorMenuItem(),
                NavigationContextMenuBuilder.makeRemoveItem(removeAction)
        );
        return contextMenu;
    }

    /**
     * Context menu for a {@link Model.CardsLists.CardsGroup} (category) node in the
     * My Collection tree. Provides Move, Add Category, Rename, Paste, and Remove actions.
     */
    public static ContextMenu forMyCollectionCategory(
            Model.CardsLists.CardsGroup category,
            Model.CardsLists.Box parentBox,
            Model.CardsLists.OwnedCardsCollection owned) {

        ContextMenu contextMenu = NavigationContextMenuBuilder.styledContextMenu();

        Menu moveMenu = NavigationContextMenuBuilder.makeLazyMenu("Move to...");
        moveMenu.setOnShowing(evt -> populateCategoryMoveMenu(moveMenu, category, parentBox, owned));

        Runnable removeAction = () -> {
            if (!NavigationContextMenuBuilder.isCategoryEmpty(category)
                    && !NavigationContextMenuBuilder.confirmRemoval("Category")) {
                return;
            }
            if (parentBox != null && parentBox.getContent() != null) {
                parentBox.getContent().remove(category);
                Controller.UserInterfaceFunctions.markMyCollectionDirty();
                Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                NavigationContextMenuBuilder.refreshOwnedCollectionView();
            }
        };

        contextMenu.getItems().addAll(
                moveMenu,
                makeAddCategoryItem(parentBox, category, owned),
                makeRenameItem(null, category, owned),
                NavigationContextMenuBuilder.makePasteItem(() -> {
                    javafx.collections.ObservableList<Model.CardsLists.CardElement> observableList =
                            CardTreeCell.observableListFor(category);
                    for (Model.CardsLists.CardElement clipboardElement
                            : Controller.CardClipboard.getContents()) {
                        if (clipboardElement != null) {
                            observableList.add(
                                    new Model.CardsLists.CardElement(clipboardElement));
                        }
                    }
                    CardTreeCell.triggerHeightAdjustment(category);
                    Controller.UserInterfaceFunctions.markMyCollectionDirty();
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    NavigationContextMenuBuilder.refreshOwnedCollectionView();
                }),
                new SeparatorMenuItem(),
                NavigationContextMenuBuilder.makeRemoveItem(removeAction)
        );
        return contextMenu;
    }

    /**
     * Context menu shown when right-clicking the empty background of the My Collection
     * navigation pane. Provides only an "Add Box" action.
     */
    public static ContextMenu forMyCollectionEmpty() {
        ContextMenu contextMenu = NavigationContextMenuBuilder.styledContextMenu();
        contextMenu.getItems().add(makeAddBoxAtTopLevelItem());
        return contextMenu;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Populates {@code moveMenu} with every valid "move this box to..." destination:
     * every other top-level box (and its sub-boxes), plus a "No Box (top level)"
     * entry when {@code box} is currently a sub-box. Boxes that are {@code box}
     * itself or one of its own descendants are excluded to prevent creating a cycle.
     */
    private static void populateBoxMoveMenu(
            Menu moveMenu,
            Model.CardsLists.Box box,
            Model.CardsLists.OwnedCardsCollection owned) {
        moveMenu.getItems().clear();

        if (owned == null || owned.getOwnedCollection() == null) {
            moveMenu.getItems().add(NavigationContextMenuBuilder.disabledItem(
                    "No destinations available"));
            return;
        }

        Model.CardsLists.Box parentOfBox = findParentOfBox(box, owned);
        boolean isSubBox = parentOfBox != null;

        java.util.Set<Model.CardsLists.Box> descendants = collectDescendants(box);

        for (Model.CardsLists.Box topBox : owned.getOwnedCollection()) {
            if (topBox == null || topBox == box) {
                continue;
            }
            if (descendants.contains(topBox)) {
                continue;
            }

            String name = sanitize(topBox.getName());
            if (name.isEmpty()) {
                name = "(Unnamed box)";
            }
            final Model.CardsLists.Box target = topBox;
            MenuItem boxMenuItem = NavigationContextMenuBuilder.makeActionItem(name, () -> {
                doMoveBoxToSubBox(box, target, owned);
                Controller.UserInterfaceFunctions.markMyCollectionDirty();
                Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                NavigationContextMenuBuilder.refreshOwnedCollectionView();
            });
            moveMenu.getItems().add(boxMenuItem);

            if (topBox.getSubBoxes() != null) {
                for (Model.CardsLists.Box sub : topBox.getSubBoxes()) {
                    if (sub == null || sub == box) {
                        continue;
                    }
                    if (descendants.contains(sub)) {
                        continue;
                    }
                    String subName = sanitize(sub.getName());
                    if (subName.isEmpty()) {
                        subName = "(Unnamed sub-box)";
                    }
                    final Model.CardsLists.Box subTarget = sub;
                    final String finalName = name;
                    final String finalSubName = subName;
                    MenuItem subBoxMenuItem = NavigationContextMenuBuilder.makeActionItem(
                            finalName + " / " + finalSubName, () -> {
                                doMoveBoxToSubBox(box, subTarget, owned);
                                Controller.UserInterfaceFunctions.markMyCollectionDirty();
                                Controller.UserInterfaceFunctions
                                        .triggerTabDirtyIndicatorUpdate();
                                NavigationContextMenuBuilder.refreshOwnedCollectionView();
                            });
                    moveMenu.getItems().add(subBoxMenuItem);
                }
            }
        }

        if (isSubBox) {
            if (!moveMenu.getItems().isEmpty()) {
                moveMenu.getItems().add(new SeparatorMenuItem());
            }
            MenuItem noBoxMenuItem = NavigationContextMenuBuilder.makeActionItem(
                    "No Box (top level)", () -> {
                        doMoveBoxToTopLevel(box, owned);
                        Controller.UserInterfaceFunctions.markMyCollectionDirty();
                        Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                        NavigationContextMenuBuilder.refreshOwnedCollectionView();
                    });
            moveMenu.getItems().add(noBoxMenuItem);
        }

        if (moveMenu.getItems().isEmpty()) {
            moveMenu.getItems().add(NavigationContextMenuBuilder.disabledItem(
                    "No other boxes"));
        }
    }

    /**
     * Populates {@code moveMenu} with every valid "move this category to..."
     * destination: every box, its categories (for after-insertion), and its
     * sub-boxes and their categories. The category itself is excluded from the
     * after-insertion targets.
     */
    private static void populateCategoryMoveMenu(
            Menu moveMenu,
            Model.CardsLists.CardsGroup category,
            Model.CardsLists.Box parentBox,
            Model.CardsLists.OwnedCardsCollection owned) {
        moveMenu.getItems().clear();

        if (owned == null || owned.getOwnedCollection() == null) {
            moveMenu.getItems().add(NavigationContextMenuBuilder.disabledItem(
                    "No destinations available"));
            return;
        }

        for (Model.CardsLists.Box box : owned.getOwnedCollection()) {
            if (box == null) {
                continue;
            }
            String boxName = sanitize(box.getName());
            if (boxName.isEmpty()) {
                boxName = "(Unnamed box)";
            }

            final Model.CardsLists.Box destBox = box;
            final String destBoxName = boxName;
            MenuItem boxMenuItem = NavigationContextMenuBuilder.makeActionItem(destBoxName, () -> {
                doMoveCategory(category, parentBox, destBox, null, owned);
                Controller.UserInterfaceFunctions.markMyCollectionDirty();
                Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                NavigationContextMenuBuilder.refreshOwnedCollectionView();
            });
            moveMenu.getItems().add(boxMenuItem);

            if (box.getContent() != null) {
                for (Model.CardsLists.CardsGroup group : box.getContent()) {
                    if (group == null || group == category) {
                        continue;
                    }
                    String groupName = sanitize(group.getName());
                    if (groupName.isEmpty()) {
                        continue;
                    }
                    final Model.CardsLists.CardsGroup afterGroup = group;
                    MenuItem groupMenuItem = NavigationContextMenuBuilder.makeActionItem(
                            destBoxName + " / " + groupName, () -> {
                                doMoveCategory(category, parentBox, destBox, afterGroup, owned);
                                Controller.UserInterfaceFunctions.markMyCollectionDirty();
                                Controller.UserInterfaceFunctions
                                        .triggerTabDirtyIndicatorUpdate();
                                NavigationContextMenuBuilder.refreshOwnedCollectionView();
                            });
                    moveMenu.getItems().add(groupMenuItem);
                }
            }

            if (box.getSubBoxes() != null) {
                for (Model.CardsLists.Box subBox : box.getSubBoxes()) {
                    if (subBox == null) {
                        continue;
                    }
                    String subName = sanitize(subBox.getName());
                    if (subName.isEmpty()) {
                        subName = "(Unnamed sub-box)";
                    }
                    final Model.CardsLists.Box destSub = subBox;
                    final String destSubName = subName;
                    MenuItem subBoxMenuItem = NavigationContextMenuBuilder.makeActionItem(
                            destSubName, () -> {
                                doMoveCategory(category, parentBox, destSub, null, owned);
                                Controller.UserInterfaceFunctions.markMyCollectionDirty();
                                Controller.UserInterfaceFunctions
                                        .triggerTabDirtyIndicatorUpdate();
                                NavigationContextMenuBuilder.refreshOwnedCollectionView();
                            });
                    moveMenu.getItems().add(subBoxMenuItem);

                    if (subBox.getContent() != null) {
                        for (Model.CardsLists.CardsGroup group : subBox.getContent()) {
                            if (group == null || group == category) {
                                continue;
                            }
                            String groupName = sanitize(group.getName());
                            if (groupName.isEmpty()) {
                                continue;
                            }
                            final Model.CardsLists.CardsGroup afterGroup = group;
                            MenuItem subGroupMenuItem = NavigationContextMenuBuilder.makeActionItem(
                                    destSubName + " / " + groupName, () -> {
                                        doMoveCategory(category, parentBox, destSub,
                                                afterGroup, owned);
                                        Controller.UserInterfaceFunctions
                                                .markMyCollectionDirty();
                                        Controller.UserInterfaceFunctions
                                                .triggerTabDirtyIndicatorUpdate();
                                        NavigationContextMenuBuilder
                                                .refreshOwnedCollectionView();
                                    });
                            moveMenu.getItems().add(subGroupMenuItem);
                        }
                    }
                }
            }
        }

        if (moveMenu.getItems().isEmpty()) {
            moveMenu.getItems().add(NavigationContextMenuBuilder.disabledItem(
                    "No destinations available"));
        }
    }

    /**
     * "Add Box in" — creates a sub-box inside the clicked box.
     */
    private static MenuItem makeAddBoxInItem(
            Model.CardsLists.Box parentBox,
            Model.CardsLists.OwnedCardsCollection owned) {
        return NavigationContextMenuBuilder.makeActionItem("Add Box in", () -> {
            Model.CardsLists.Box newBox = new Model.CardsLists.Box("New Box");
            if (parentBox.getSubBoxes() == null) {
                parentBox.setSubBoxes(new java.util.ArrayList<>());
            }
            parentBox.getSubBoxes().add(newBox);
            Controller.UserInterfaceFunctions.setPendingRenameTarget(newBox);
            Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Box after" — creates a sibling box immediately after the clicked box.
     */
    private static MenuItem makeAddBoxAfterItem(
            Model.CardsLists.Box referenceBox,
            Model.CardsLists.OwnedCardsCollection owned) {
        return NavigationContextMenuBuilder.makeActionItem("Add Box after", () -> {
            Model.CardsLists.Box newBox = new Model.CardsLists.Box("New Box");
            java.util.List<Model.CardsLists.Box> topLevel = owned.getOwnedCollection();
            int idx = topLevel.indexOf(referenceBox);
            if (idx >= 0) {
                topLevel.add(idx + 1, newBox);
            } else {
                boolean inserted = false;
                outer:
                for (Model.CardsLists.Box top : topLevel) {
                    if (top.getSubBoxes() == null) {
                        continue;
                    }
                    idx = top.getSubBoxes().indexOf(referenceBox);
                    if (idx >= 0) {
                        top.getSubBoxes().add(idx + 1, newBox);
                        inserted = true;
                        break;
                    }
                }
                if (!inserted) {
                    topLevel.add(newBox);
                }
            }
            Controller.UserInterfaceFunctions.setPendingRenameTarget(newBox);
            Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Category" — creates a new category in {@code targetBox}.
     * If {@code afterCategory} is non-null it is inserted after that category;
     * otherwise it is appended.
     */
    private static MenuItem makeAddCategoryItem(
            Model.CardsLists.Box targetBox,
            Model.CardsLists.CardsGroup afterCategory,
            Model.CardsLists.OwnedCardsCollection owned) {
        return NavigationContextMenuBuilder.makeActionItem("Add Category", () -> {
            Model.CardsLists.CardsGroup newCategory =
                    new Model.CardsLists.CardsGroup("New Category");
            if (targetBox.getContent() == null) {
                targetBox.setContent(new java.util.ArrayList<>());
            }
            if (afterCategory != null) {
                int idx = targetBox.getContent().indexOf(afterCategory);
                if (idx >= 0) {
                    targetBox.getContent().add(idx + 1, newCategory);
                } else {
                    targetBox.getContent().add(newCategory);
                }
            } else {
                targetBox.getContent().add(newCategory);
            }
            Controller.UserInterfaceFunctions.setPendingRenameTarget(newCategory);
            Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Box" from the empty nav-menu background — appends at the top level.
     */
    private static MenuItem makeAddBoxAtTopLevelItem() {
        return NavigationContextMenuBuilder.makeActionItem("Add Box", () -> {
            Model.CardsLists.OwnedCardsCollection owned = null;
            try {
                owned = Model.CardsLists.OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
            if (owned == null) {
                return;
            }
            Model.CardsLists.Box newBox = new Model.CardsLists.Box("New Box");
            owned.getOwnedCollection().add(newBox);
            Controller.UserInterfaceFunctions.setPendingRenameTarget(newBox);
            Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * Builds a "Rename" MenuItem for a Box or Category. The actual inline editor
     * is launched on the {@link NavigationItem} found by walking the scene graph
     * from the menu's anchor node.
     *
     * <p>Pass non-null {@code box} for a Box rename, non-null {@code category}
     * for a Category rename.</p>
     */
    private static MenuItem makeRenameItem(
            Model.CardsLists.Box box,
            Model.CardsLists.CardsGroup category,
            Model.CardsLists.OwnedCardsCollection owned) {

        MenuItem menuItem = new MenuItem();
        Label label = new Label("Rename");
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        menuItem.setGraphic(graphic);
        menuItem.setText("");

        menuItem.setOnAction(e -> {
            javafx.scene.Node owner = null;
            try {
                owner = menuItem.getParentPopup().getOwnerNode();
            } catch (Throwable ignored) {
            }
            NavigationItem navItem =
                    NavigationContextMenuBuilder.findNavigationItemAncestor(owner);
            if (navItem == null) {
                return;
            }
            navItem.startInlineRename(
                    newName -> {
                        if (box != null) {
                            String raw = box.getName() == null ? "" : box.getName();
                            box.setName(rebuildDecoratedName(raw, newName, '='));
                            navItem.getLabel().setText(newName);
                        } else if (category != null) {
                            String raw = category.getName() == null ? "" : category.getName();
                            category.setName(rebuildDecoratedName(raw, newName, '-'));
                            navItem.getLabel().setText(newName);
                        }
                        Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
                        Controller.UserInterfaceFunctions.markMyCollectionDirty();
                        Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    },
                    null
            );
        });
        return menuItem;
    }

    /**
     * Moves {@code box} to become a sub-box of {@code targetBox}, removing it
     * from its current location first.
     */
    private static void doMoveBoxToSubBox(
            Model.CardsLists.Box box,
            Model.CardsLists.Box targetBox,
            Model.CardsLists.OwnedCardsCollection owned) {
        if (box == null || targetBox == null || owned == null) {
            return;
        }
        removeBoxFromAnywhere(box, owned);
        if (targetBox.getSubBoxes() == null) {
            targetBox.setSubBoxes(new java.util.ArrayList<>());
        }
        targetBox.getSubBoxes().add(box);
    }

    /**
     * Promotes {@code box} from its current sub-box position to the top level
     * of the collection.
     */
    private static void doMoveBoxToTopLevel(
            Model.CardsLists.Box box,
            Model.CardsLists.OwnedCardsCollection owned) {
        if (box == null || owned == null) {
            return;
        }
        removeBoxFromAnywhere(box, owned);
        owned.getOwnedCollection().add(box);
    }

    /**
     * Removes {@code box} from wherever it currently lives (top-level or sub-box).
     */
    private static void removeBoxFromAnywhere(
            Model.CardsLists.Box box,
            Model.CardsLists.OwnedCardsCollection owned) {
        if (box == null || owned == null || owned.getOwnedCollection() == null) {
            return;
        }
        if (owned.getOwnedCollection().remove(box)) {
            return;
        }
        for (Model.CardsLists.Box top : owned.getOwnedCollection()) {
            if (top == null || top.getSubBoxes() == null) {
                continue;
            }
            if (top.getSubBoxes().remove(box)) {
                return;
            }
        }
    }

    /**
     * Returns the Box that contains {@code box} as a sub-box, or {@code null}
     * if {@code box} is at the top level.
     */
    private static Model.CardsLists.Box findParentOfBox(
            Model.CardsLists.Box box,
            Model.CardsLists.OwnedCardsCollection owned) {
        if (owned == null || owned.getOwnedCollection() == null) {
            return null;
        }
        for (Model.CardsLists.Box top : owned.getOwnedCollection()) {
            if (top == null || top.getSubBoxes() == null) {
                continue;
            }
            for (Model.CardsLists.Box sub : top.getSubBoxes()) {
                if (sub == box) {
                    return top;
                }
            }
        }
        return null;
    }

    /**
     * Returns all boxes that are descendants of {@code root}, used to prevent
     * moving a box into one of its own descendants (which would create a cycle).
     */
    private static java.util.Set<Model.CardsLists.Box> collectDescendants(
            Model.CardsLists.Box root) {
        java.util.Set<Model.CardsLists.Box> result = new java.util.HashSet<>();
        if (root == null || root.getSubBoxes() == null) {
            return result;
        }
        java.util.Deque<Model.CardsLists.Box> stack =
                new java.util.ArrayDeque<>(root.getSubBoxes());
        while (!stack.isEmpty()) {
            Model.CardsLists.Box current = stack.pop();
            if (current == null || !result.add(current)) {
                continue;
            }
            if (current.getSubBoxes() != null) {
                stack.addAll(current.getSubBoxes());
            }
        }
        return result;
    }

    /**
     * Moves {@code category} from {@code fromBox} to {@code toBox}.
     *
     * <p>If {@code insertAfter} is non-null the category is inserted immediately
     * after that group in {@code toBox}'s content list; otherwise it is appended.</p>
     */
    private static void doMoveCategory(
            Model.CardsLists.CardsGroup category,
            Model.CardsLists.Box fromBox,
            Model.CardsLists.Box toBox,
            Model.CardsLists.CardsGroup insertAfter,
            Model.CardsLists.OwnedCardsCollection owned) {
        if (category == null || toBox == null) {
            return;
        }
        if (fromBox != null && fromBox.getContent() != null) {
            fromBox.getContent().remove(category);
        } else if (owned != null && owned.getOwnedCollection() != null) {
            outer:
            for (Model.CardsLists.Box box : owned.getOwnedCollection()) {
                if (box == null) {
                    continue;
                }
                for (Model.CardsLists.Box candidate : new java.util.ArrayList<>(
                        box.getSubBoxes() == null
                                ? java.util.Collections.emptyList()
                                : box.getSubBoxes())) {
                    if (candidate != null
                            && candidate.getContent() != null
                            && candidate.getContent().remove(category)) {
                        break outer;
                    }
                }
                if (box.getContent() != null && box.getContent().remove(category)) {
                    break;
                }
            }
        }
        if (toBox.getContent() == null) {
            toBox.setContent(new java.util.ArrayList<>());
        }
        if (insertAfter != null) {
            int idx = toBox.getContent().indexOf(insertAfter);
            if (idx >= 0 && idx + 1 <= toBox.getContent().size()) {
                toBox.getContent().add(idx + 1, category);
                return;
            }
        }
        toBox.getContent().add(category);
    }
}