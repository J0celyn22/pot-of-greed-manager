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

    // Highlight state (true => accent color)
    private boolean highlighted = false;

    // Add field:
    private Object userData;

    public Object getUserData() {
        return this.userData;
    }

    // Add getter/setter:
    public void setUserData(Object data) {
        this.userData = data;
    }

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
            if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                event.consume();
                return;
            }
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

    /**
     * Set or clear the highlight state for this item.
     * When highlighted, the label color becomes the accent color (#cdfc04).
     * When not highlighted, label color returns to white.
     * <p>
     * This method does not automatically propagate to parents; callers should
     * explicitly set parent highlights when they want propagation.
     */
    public void setHighlight(boolean highlight) {
        if (highlight) {
            label.setStyle("-fx-text-fill: #cdfc04; -fx-font-weight: bold;");
        } else {
            label.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        }
    }

    /**
     * Query whether this item is currently highlighted.
     */
    public boolean isHighlighted() {
        return highlighted;
    }

    /**
     * Replaces the label with an inline text field + confirm/cancel buttons.
     *
     * @param onConfirm called with the new (trimmed) name when the user accepts.
     *                  The name is guaranteed to be non-empty.
     * @param onCancel  called when the user presses Esc or the cancel button.
     */
    public void startInlineRename(java.util.function.Consumer<String> onConfirm, Runnable onCancel) {
        // Find the HBox that contains the triangle + label (first child of this VBox)
        if (getChildren().isEmpty()) return;
        javafx.scene.Node firstChild = getChildren().get(0);
        if (!(firstChild instanceof HBox)) return;
        HBox hbox = (HBox) firstChild;

        // Build the text field pre-filled with the current name
        javafx.scene.control.TextField tf = new javafx.scene.control.TextField(label.getText());
        tf.setStyle(
                "-fx-background-color: black;" +
                        "-fx-text-fill: #cdfc04;" +
                        "-fx-border-color: #cdfc04;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 4;" +
                        "-fx-background-radius: 4;" +
                        "-fx-font-size: 13px;" +
                        "-fx-padding: 3 6 3 6;"
        );
        tf.setPrefWidth(180);
        HBox.setHgrow(tf, javafx.scene.layout.Priority.ALWAYS);

        // Confirm button — green tick
        javafx.scene.control.Button confirmBtn = new javafx.scene.control.Button("✓");
        confirmBtn.setStyle(
                "-fx-background-color: black;" +
                        "-fx-text-fill: #00cc44;" +
                        "-fx-border-color: #00cc44;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 4;" +
                        "-fx-background-radius: 4;" +
                        "-fx-font-size: 13px;" +
                        "-fx-padding: 3 7 3 7;" +
                        "-fx-cursor: hand;"
        );

        // Cancel button — red cross
        javafx.scene.control.Button cancelBtn = new javafx.scene.control.Button("✗");
        cancelBtn.setStyle(
                "-fx-background-color: black;" +
                        "-fx-text-fill: #ff4040;" +
                        "-fx-border-color: #ff4040;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 4;" +
                        "-fx-background-radius: 4;" +
                        "-fx-font-size: 13px;" +
                        "-fx-padding: 3 7 3 7;" +
                        "-fx-cursor: hand;"
        );

        // Shared helpers
        Runnable doConfirm = () -> {
            String newName = tf.getText() == null ? "" : tf.getText().trim();
            if (!newName.isEmpty()) {
                restoreLabel(hbox, tf, confirmBtn, cancelBtn);
                onConfirm.accept(newName);
            }
        };

        Runnable doCancel = () -> {
            restoreLabel(hbox, tf, confirmBtn, cancelBtn);
            if (onCancel != null) onCancel.run();
        };

        // Key handlers
        tf.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                e.consume();
                doConfirm.run();
            }
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                e.consume();
                doCancel.run();
            }
        });

        confirmBtn.setOnAction(e -> doConfirm.run());
        cancelBtn.setOnAction(e -> doCancel.run());

        // Swap label → tf + buttons
        hbox.getChildren().remove(label);
        hbox.getChildren().addAll(tf, confirmBtn, cancelBtn);

        // Select all text and focus
        javafx.application.Platform.runLater(() -> {
            tf.requestFocus();
            tf.selectAll();
        });
    }

    /**
     * Restores the original label, removing the editing widgets.
     */
    private void restoreLabel(HBox hbox,
                              javafx.scene.control.TextField tf,
                              javafx.scene.control.Button confirmBtn,
                              javafx.scene.control.Button cancelBtn) {
        hbox.getChildren().removeAll(tf, confirmBtn, cancelBtn);
        if (!hbox.getChildren().contains(label)) {
            // Re-insert after the triangle
            hbox.getChildren().add(label);
        }
    }
}
