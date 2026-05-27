package Controller;

import View.NavigationItem;
import View.NavigationMenu;
import View.SharedCollectionTab;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NavigationHelper — static utilities shared across all tab-level controllers.
 *
 * <p>Provides:
 * <ul>
 *   <li>Navigation-item lookup by model object (userData) or display name.</li>
 *   <li>Ancestor expansion and scroll-to-item for the nav menu.</li>
 *   <li>TreeItem path-based lookup and tree navigation.</li>
 *   <li>NavigationItem factory and highlight application.</li>
 *   <li>Drop-position resolution for nav drag-and-drop.</li>
 * </ul>
 *
 * <p>All methods are static because this class has no mutable state; it operates
 * entirely on the objects passed to it.
 */
public final class NavigationHelper {

    private static final Logger logger = LoggerFactory.getLogger(NavigationHelper.class);

    /**
     * Utility class — no instances.
     */
    private NavigationHelper() {
    }

    // ── Navigation-item lookup by userData identity ───────────────────────────

    /**
     * Recursively searches {@code menuVBox} for the first {@link NavigationItem}
     * whose {@link NavigationItem#getUserData()} is the same object as {@code target}.
     *
     * @param menuVBox the VBox that contains the navigation menu
     * @param target   the model object to search for (identity comparison)
     * @return the matching NavigationItem, or {@code null} if not found
     */
    public static NavigationItem findNavItemInMenuVBox(VBox menuVBox, Object target) {
        if (menuVBox == null || target == null) {
            return null;
        }
        for (Node node : menuVBox.getChildren()) {
            NavigationItem found = findNavItemInNode(node, target);
            if (found != null) {
                return found;
            }
        }
        logger.debug("findNavItemInMenuVBox: no item found for target={}", target);
        return null;
    }

    /**
     * Searches a single node (NavigationMenu or NavigationItem) for a nav item
     * matching {@code target} by userData identity.
     */
    public static NavigationItem findNavItemInNode(Node node, Object target) {
        if (node instanceof NavigationMenu) {
            for (NavigationItem item : ((NavigationMenu) node).getItems()) {
                NavigationItem found = findNavItemByUserDataInItem(item, target);
                if (found != null) {
                    return found;
                }
            }
        } else if (node instanceof NavigationItem) {
            return findNavItemByUserDataInItem((NavigationItem) node, target);
        }
        return null;
    }

    /**
     * Recursively searches {@code item} and its sub-items for the first nav item
     * whose userData is the same object as {@code target}.
     */
    public static NavigationItem findNavItemByUserDataInItem(NavigationItem item, Object target) {
        if (item == null) {
            return null;
        }
        logger.debug("findNavItemByUserDataInItem: checking '{}' userData={}",
                item.getLabel() != null ? item.getLabel().getText() : "?",
                item.getUserData());
        if (item.getUserData() == target) {
            return item;
        }
        for (NavigationItem subItem : item.getSubItems()) {
            NavigationItem found = findNavItemByUserDataInItem(subItem, target);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // ── Navigation-item lookup by display name ────────────────────────────────

    /**
     * Name-based fallback search through the nav menu when userData identity fails.
     *
     * @param menuVBox the VBox that contains the navigation menu
     * @param name     the label text to search for (exact match)
     * @return the matching NavigationItem, or {@code null} if not found
     */
    public static NavigationItem findNavItemByNameInMenuVBox(VBox menuVBox, String name) {
        if (menuVBox == null || name == null) {
            return null;
        }
        for (Node node : menuVBox.getChildren()) {
            NavigationItem found = findNavItemByNameInNode(node, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Searches a single node (NavigationMenu or NavigationItem) for a nav item
     * whose label text equals {@code name}.
     */
    public static NavigationItem findNavItemByNameInNode(Node node, String name) {
        if (node instanceof NavigationMenu) {
            for (NavigationItem item : ((NavigationMenu) node).getItems()) {
                NavigationItem found = findNavItemByNameInItem(item, name);
                if (found != null) {
                    return found;
                }
            }
        } else if (node instanceof NavigationItem) {
            return findNavItemByNameInItem((NavigationItem) node, name);
        }
        return null;
    }

    /**
     * Recursively searches {@code item} and its sub-items for the first nav item
     * whose label text equals {@code name}.
     */
    public static NavigationItem findNavItemByNameInItem(NavigationItem item, String name) {
        if (item == null) {
            return null;
        }
        if (item.getLabel() != null && name.equals(item.getLabel().getText())) {
            return item;
        }
        for (NavigationItem subItem : item.getSubItems()) {
            NavigationItem found = findNavItemByNameInItem(subItem, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // ── Ancestor expansion and scroll-to ─────────────────────────────────────

    /**
     * Expands all ancestor {@link NavigationItem}s of {@code item} so that
     * {@code item} itself becomes visible in the nav menu.
     *
     * @param item the NavigationItem to make visible; must not be {@code null}
     */
    public static void expandNavAncestors(NavigationItem item) {
        if (item == null) {
            return;
        }
        Node parent = item.getParent();
        while (parent != null) {
            if (parent instanceof NavigationItem) {
                ((NavigationItem) parent).setExpanded(true);
            }
            parent = parent.getParent();
        }
    }

    /**
     * Scrolls the nav-menu {@link ScrollPane} inside {@code tab} so that
     * {@code item} is centred in the viewport.
     *
     * <p>Deferred with {@link Platform#runLater} so that the layout pass has
     * completed before bounds are read.
     *
     * @param tab  the tab whose nav scroll pane to scroll
     * @param item the nav item to scroll into view
     */
    public static void scrollNavToItem(SharedCollectionTab tab, NavigationItem item) {
        if (tab == null || item == null) {
            return;
        }
        Platform.runLater(() -> {
            try {
                ScrollPane scrollPane = tab.getMenuScrollPane();
                VBox content = tab.getMenuVBox();
                if (scrollPane == null || content == null) {
                    return;
                }

                double itemY = 0;
                Node node = item;
                while (node != null && node != content) {
                    itemY += node.getBoundsInParent().getMinY();
                    node = node.getParent();
                }

                Bounds viewportBounds = scrollPane.getViewportBounds();
                Bounds contentBounds = content.getBoundsInLocal();
                if (viewportBounds == null || contentBounds == null) {
                    return;
                }
                double viewportHeight = viewportBounds.getHeight();
                double contentHeight = contentBounds.getHeight();
                if (contentHeight <= viewportHeight) {
                    return;
                }

                double itemHeight = item.getBoundsInLocal().getHeight();
                double targetY = itemY - (viewportHeight - itemHeight) / 2.0;
                targetY = Math.max(0, Math.min(targetY, contentHeight - viewportHeight));
                scrollPane.setVvalue(targetY / (contentHeight - viewportHeight));
            } catch (Throwable ignored) {
            }
        });
    }

    // ── TreeItem path-based lookup and tree navigation ────────────────────────

    /**
     * Navigates to a node in the given {@link TreeView} by matching a path of
     * node text values.
     *
     * <p>Example: {@code navigateToTree(decksTreeView, "CollectionName", "Decks", "Zombie")}
     *
     * @param treeView the tree to navigate in
     * @param path     one or more node text values forming a path from root to target
     */
    public static void navigateToTree(TreeView<String> treeView, String... path) {
        if (treeView == null || path == null || path.length == 0) {
            return;
        }
        TreeItem<String> root = treeView.getRoot();
        if (root == null) {
            return;
        }

        TreeItem<String> found = findTreeItemByPath(root, path, 0);
        if (found != null) {
            TreeItem<String> parent = found.getParent();
            while (parent != null) {
                parent.setExpanded(true);
                parent = parent.getParent();
            }
            final TreeItem<String> toSelect = found;
            Platform.runLater(() -> {
                treeView.getSelectionModel().select(toSelect);
                int row = treeView.getRow(toSelect);
                if (row >= 0) {
                    treeView.scrollTo(row);
                }
            });
        }
    }

    /**
     * Recursively locates a tree item matching the next segment in {@code path}
     * starting from {@code node} at depth {@code index}.
     *
     * @param node  the current tree item to test
     * @param path  the full path array
     * @param index the current depth (0-based)
     * @return the matching {@link TreeItem}, or {@code null} if not found
     */
    public static TreeItem<String> findTreeItemByPath(TreeItem<String> node,
                                                      String[] path, int index) {
        if (node == null || path == null || index >= path.length) {
            return null;
        }
        String nodeValue = node.getValue();
        if (nodeValue == null) {
            nodeValue = "";
        }

        if (!nodeValue.equals(path[index])) {
            for (TreeItem<String> child : node.getChildren()) {
                TreeItem<String> result = findTreeItemByPath(child, path, index);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }

        if (index == path.length - 1) {
            return node;
        }

        for (TreeItem<String> child : node.getChildren()) {
            TreeItem<String> result = findTreeItemByPath(child, path, index + 1);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    // ── NavigationItem factory ────────────────────────────────────────────────

    /**
     * Creates a new {@link NavigationItem} with an empty on-label-clicked handler.
     * Callers must set a meaningful handler after creation.
     *
     * @param name  the label text; {@code null} is treated as empty string
     * @param depth the indentation depth (0 = top level)
     * @return the new NavigationItem
     */
    public static NavigationItem createNavigationItem(String name, int depth) {
        if (name == null) {
            name = "";
        }
        NavigationItem item = new NavigationItem(name, depth);
        item.setOnLabelClicked(event -> {
        });
        return item;
    }

    // ── Highlight application ─────────────────────────────────────────────────

    /**
     * Applies a yellow-green or white text colour to a nav item's label.
     *
     * @param navItem   the item to highlight or de-highlight
     * @param highlight {@code true} for yellow-green ({@code #cdfc04}), {@code false} for white
     */
    public static void applyNavigationItemHighlight(NavigationItem navItem, boolean highlight) {
        applyNavigationItemHighlight(navItem, highlight, null);
    }

    /**
     * Applies a yellow-green or white text colour to a nav item's label, and
     * optionally attaches a warning tooltip.
     *
     * <p>Existing {@code -fx-text-fill} declarations are stripped from the label's
     * inline style before the new colour is appended, so other properties are preserved.
     *
     * @param navItem        the item to highlight or de-highlight
     * @param highlight      {@code true} for yellow-green, {@code false} for white
     * @param warningMessage tooltip text to show when highlighted; {@code null} to clear it
     */
    public static void applyNavigationItemHighlight(NavigationItem navItem, boolean highlight,
                                                    String warningMessage) {
        if (navItem == null) {
            return;
        }
        Label label = navItem.getLabel();
        if (label == null) {
            return;
        }
        String baseStyle = label.getStyle() == null ? "" : label.getStyle();
        baseStyle = baseStyle.replaceAll("-fx-text-fill\\s*:[^;]*(;|$)", "").trim();
        String colour = highlight ? "#cdfc04" : "white";
        label.setStyle(baseStyle + (baseStyle.isEmpty() ? "" : " ") + "-fx-text-fill: " + colour + ";");

        if (highlight && warningMessage != null) {
            navItem.setWarningTooltip(warningMessage);
        } else {
            navItem.clearWarningTooltip();
        }
    }

    // ── Drop-position resolution ──────────────────────────────────────────────

    /**
     * Determines the {@link NavigationItem.DropPosition} from the cursor's Y
     * coordinate relative to {@code navItem}.
     *
     * <p>Two zone profiles:
     * <ul>
     *   <li><b>Container target</b> (Box) — INTO dominates: top/bottom 15% → BEFORE/AFTER,
     *       middle 70% → INTO.</li>
     *   <li><b>Non-container target</b> (Deck, CardsGroup) — reorder dominates:
     *       top/bottom 30% → BEFORE/AFTER, middle 40% → INTO.</li>
     * </ul>
     *
     * @param navItem           the nav item being dropped onto
     * @param cursorY           the cursor's Y coordinate relative to {@code navItem}
     * @param isContainerTarget {@code true} when the drop target can contain children (i.e. is a Box)
     * @return the resolved {@link NavigationItem.DropPosition}
     */
    public static NavigationItem.DropPosition resolveDropPosition(NavigationItem navItem,
                                                                  double cursorY,
                                                                  boolean isContainerTarget) {
        double rowHeight = 30.0;
        javafx.scene.layout.HBox rowHBox = navItem.getRowHBox();
        if (rowHBox != null) {
            double height = rowHBox.getBoundsInParent().getHeight();
            if (height > 0) {
                rowHeight = height;
            }
        }
        double beforeThreshold = isContainerTarget ? rowHeight * 0.15 : rowHeight * 0.30;
        double afterThreshold = isContainerTarget ? rowHeight * 0.85 : rowHeight * 0.70;
        if (cursorY < beforeThreshold) {
            return NavigationItem.DropPosition.BEFORE;
        }
        if (cursorY > afterThreshold) {
            return NavigationItem.DropPosition.AFTER;
        }
        return NavigationItem.DropPosition.INTO;
    }
}