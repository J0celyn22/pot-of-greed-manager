package View;

import javafx.geometry.Insets;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public class NavigationMenu extends VBox {
    private final List<NavigationItem> items = new ArrayList<>();

    public NavigationMenu() {
        // Use a style class so CSS can target this menu reliably
        this.getStyleClass().add("navigation-menu");
        this.setSpacing(5);
        this.setPadding(new Insets(10));
    }

    public void addItem(NavigationItem item) {
        items.add(item);
        this.getChildren().add(item);
    }

    public List<NavigationItem> getItems() {
        return items;
    }
}
