// NavigationItem.java
package View;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

public class NavigationItem extends VBox {
    private final List<NavigationItem> subItems = new ArrayList<>();
    private final int depth;
    private String name;
    private boolean isExpanded = true; // Start as expanded by default for most items
    private Label label;
    private Label triangleLabel; // For the triangle indicator
    private javafx.event.EventHandler<? super MouseEvent> onLabelClicked;

    public NavigationItem(String name, int depth) {
        this.name = name;
        this.depth = depth;
        initialize();
    }

    private void initialize() {
        // Create a horizontal box to hold the triangle and label
        HBox hbox = new HBox();
        hbox.setSpacing(5);
        hbox.setPadding(new Insets(5, 0, 5, 20 * depth)); // Indentation based on depth

        // Triangle label for expand/collapse
        triangleLabel = new Label();
        triangleLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        triangleLabel.setMinWidth(15); // Fixed width
        triangleLabel.setMaxWidth(15);
        triangleLabel.setAlignment(Pos.CENTER);
        updateTriangle();

        // Set mouse click handler on triangle to toggle expansion
        triangleLabel.setOnMouseClicked(event -> {
            if (!subItems.isEmpty()) {
                isExpanded = !isExpanded;
                updateTriangle();
                updateSubItemsVisibility();
            }
            event.consume();
        });

        // Label for navigation
        label = new Label(name);
        label.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        // Label click navigates to the element
        label.setOnMouseClicked(event -> {
            if (onLabelClicked != null) {
                onLabelClicked.handle(event);
            }
            event.consume();
        });

        hbox.getChildren().addAll(triangleLabel, label);
        this.getChildren().add(hbox);

        // Initially show sub-items according to isExpanded
        updateSubItemsVisibility();
    }

    // ADD: Public getter for the internal label.
    public Label getLabel() {
        return label;
    }

    private void updateTriangle() {
        if (subItems.isEmpty()) {
            triangleLabel.setText(" "); // Empty space when there are no sub-items
        } else {
            String triangle = isExpanded ? "\u25BC" : "\u25B6"; // ▼ or ▶
            triangleLabel.setText(triangle);
        }
    }

    public void addSubItem(NavigationItem subItem) {
        subItems.add(subItem);
        this.getChildren().add(subItem);
        updateTriangle();
        // Ensure the newly added subItem visibility matches current expanded state
        subItem.setVisible(isExpanded);
        subItem.setManaged(isExpanded);
    }

    public List<NavigationItem> getSubItems() {
        return subItems;
    }

    private void updateSubItemsVisibility() {
        for (NavigationItem subItem : subItems) {
            subItem.setVisible(isExpanded);
            subItem.setManaged(isExpanded);
        }
    }

    // Set the click handler for the label.
    public void setOnLabelClicked(javafx.event.EventHandler<? super MouseEvent> handler) {
        this.onLabelClicked = handler;
    }

    /**
     * Public API to query the expanded state.
     */
    public boolean isExpanded() {
        return isExpanded;
    }

    /**
     * Public API to set the expanded state programmatically.
     * When changed, the triangle and sub-items visibility are updated.
     */
    public void setExpanded(boolean expanded) {
        this.isExpanded = expanded;
        updateTriangle();
        updateSubItemsVisibility();
    }
}
