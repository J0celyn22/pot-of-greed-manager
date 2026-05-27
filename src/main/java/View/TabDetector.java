package View;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeCell;

/**
 * TabDetector — lightweight helper that walks the JavaFX scene graph from an anchor node
 * to locate the nearest ancestor {@link TabPane} and report which tab is currently selected.
 *
 * <p>This class was extracted from the tab-detection helpers on {@code CardTreeCell} so that
 * {@link CardGridCell} can query the active tab without needing a direct reference to the
 * outer tree-cell instance.</p>
 */
public final class TabDetector {

    /**
     * Anchor node used to walk up to the enclosing TabPane.
     */
    private final TreeCell<?> anchorCell;

    /**
     * Creates a new TabDetector anchored to the given tree cell.
     *
     * @param anchorCell the tree cell from which the TabPane ancestor will be located
     */
    public TabDetector(TreeCell<?> anchorCell) {
        this.anchorCell = anchorCell;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public tab-detection methods
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Removes the leading {@code "* "} dirty marker from a tab label if present,
     * returning the bare display text.
     */
    private static String stripDirtyPrefix(String tabText) {
        if (tabText == null) {
            return "";
        }
        String trimmed = tabText.trim();
        return trimmed.startsWith("* ") ? trimmed.substring(2).trim() : trimmed;
    }

    /**
     * Returns {@code true} when the currently selected tab is the My Collection tab.
     * Comparison is case-insensitive on the tab text after stripping any dirty prefix
     * ({@code "* "}).
     */
    public boolean isMyCollectionTabSelected() {
        Tab selectedTab = findSelectedTab();
        if (selectedTab == null) {
            return false;
        }
        return stripDirtyPrefix(selectedTab.getText()).equalsIgnoreCase("My Collection");
    }

    /**
     * Returns {@code true} when the currently selected tab is the Decks and Collections tab.
     */
    public boolean isDecksAndCollectionsTabSelected() {
        Tab selectedTab = findSelectedTab();
        if (selectedTab == null) {
            return false;
        }
        String cleanText = stripDirtyPrefix(selectedTab.getText());
        return cleanText.equalsIgnoreCase("Decks and Collections")
                || cleanText.equalsIgnoreCase("Decks & Collections");
    }

    /**
     * Returns {@code true} when the currently selected tab is the OuicheList tab.
     */
    public boolean isOuicheListTabSelected() {
        Tab selectedTab = findSelectedTab();
        if (selectedTab == null) {
            return false;
        }
        return stripDirtyPrefix(selectedTab.getText()).equalsIgnoreCase("OuicheList");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the currently selected tab is the first tab
     * (index 0) in the TabPane.
     */
    public boolean isFirstTabSelected() {
        try {
            Node node = anchorCell.getTreeView();
            while (node != null && !(node instanceof TabPane)) {
                node = node.getParent();
            }
            if (node instanceof TabPane) {
                TabPane tabPane = (TabPane) node;
                if (tabPane.getSelectionModel() != null) {
                    return tabPane.getSelectionModel().getSelectedIndex() == 0;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Walks up the scene graph from the anchor cell's TreeView to find the nearest
     * ancestor {@link TabPane}, then returns its currently selected {@link Tab}.
     * Returns {@code null} if no TabPane ancestor is found or if no tab is selected.
     */
    private Tab findSelectedTab() {
        try {
            Node node = anchorCell.getTreeView();
            while (node != null && !(node instanceof TabPane)) {
                node = node.getParent();
            }
            if (node instanceof TabPane) {
                return ((TabPane) node).getSelectionModel().getSelectedItem();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}