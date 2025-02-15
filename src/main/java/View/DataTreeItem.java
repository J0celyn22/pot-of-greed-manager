// DataTreeItem.java
package View;

import javafx.scene.control.TreeItem;

public class DataTreeItem<T> extends TreeItem<String> {
    private T data;

    public DataTreeItem(String value, T data) {
        super(value);
        this.data = data;
    }

    public T getData() {
        return data;
    }
}
