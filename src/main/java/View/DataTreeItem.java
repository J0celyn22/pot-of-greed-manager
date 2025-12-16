/*
 * DataTreeItem.java - Custom TreeItem that wraps a display value and associated data.
 *
 * This class is used to attach data (such as a Box or a CardsGroup) to a TreeView node,
 * allowing the UI layer to retrieve and utilize additional context for each tree item.
 */

package View;

import javafx.scene.control.TreeItem;

public class DataTreeItem<T> extends TreeItem<String> {
    private T data;

    /**
     * Constructs a new DataTreeItem with the given display value and associated data.
     *
     * @param value the string to be displayed in the tree view.
     * @param data  the object associated with this tree item.
     */
    public DataTreeItem(String value, T data) {
        super(value);
        this.data = data;
    }

    /**
     * Returns the data associated with this tree item.
     *
     * @return the stored data.
     */
    public T getData() {
        return data;
    }
}
