package View;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/**
 * Utility methods for common JavaFX scene-graph operations shared across
 * view classes.
 */
public final class ViewUtils {

    private ViewUtils() {
    }

    /**
     * Removes the {@code "* "} dirty-state prefix from a tab title, returning
     * the clean display name.
     *
     * @param tabTitle the raw tab title text (may be {@code null})
     * @return the clean title, never {@code null}
     */
    public static String stripDirtyPrefix(String tabTitle) {
        if (tabTitle == null) {
            return "";
        }
        String trimmed = tabTitle.trim();
        return trimmed.startsWith("* ") ? trimmed.substring(2).trim() : trimmed;
    }

    /**
     * Searches the scene graph rooted at {@code parent} for the first
     * {@link TabPane}, using depth-first traversal.
     *
     * @param parent the root node to search from (may be {@code null})
     * @return the first {@link TabPane} found, or {@code null}
     */
    public static TabPane findTabPane(Parent parent) {
        if (parent == null) {
            return null;
        }
        if (parent instanceof TabPane) {
            return (TabPane) parent;
        }
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof TabPane) {
                return (TabPane) child;
            }
            if (child instanceof Parent) {
                TabPane found = findTabPane((Parent) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /**
     * Returns the clean (dirty-prefix stripped) name of the currently selected
     * tab in the nearest {@link TabPane} ancestor of {@code startNode}.
     *
     * <p>First walks up the parent chain from {@code startNode}; if no
     * {@link TabPane} is found that way, falls back to a scene-graph search
     * from the scene root.
     *
     * @param startNode any node currently in the scene (may be {@code null})
     * @return the current tab name, or an empty string if none can be determined
     */
    public static String getCurrentTabName(Node startNode) {
        try {
            Node node = startNode;
            while (node != null && !(node instanceof TabPane)) {
                node = node.getParent();
            }
            if (node instanceof TabPane) {
                Tab selectedTab = ((TabPane) node).getSelectionModel().getSelectedItem();
                if (selectedTab != null && selectedTab.getText() != null) {
                    return stripDirtyPrefix(selectedTab.getText());
                }
            }
            if (startNode != null && startNode.getScene() != null) {
                TabPane tabPane = findTabPane(startNode.getScene().getRoot());
                if (tabPane != null) {
                    Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
                    if (selectedTab != null && selectedTab.getText() != null) {
                        return stripDirtyPrefix(selectedTab.getText());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}