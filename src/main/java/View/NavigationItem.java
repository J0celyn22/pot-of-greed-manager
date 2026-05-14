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

    /**
     * Separator nodes inserted between deck lists; managed alongside subItems for visibility.
     */
    private final List<javafx.scene.Node> deckListSeparators = new ArrayList<>();

    private final List<NavigationItem> subItems = new ArrayList<>();
    private HBox rowHBox;        // The single header row (triangle + label)
    private final int depth;
    private String name;
    private boolean isExpanded = true; // Start as expanded by default for most items
    private Label label;
    private Label triangleLabel; // For the triangle indicator
    /**
     * Optional icon prepended before the label — never modifies label.getText().
     */
    private Label iconLabel;
    /**
     * Optional footer drop-zone node appended after all sub-items.
     */
    private HBox footerZone = null;
    private javafx.event.EventHandler<? super MouseEvent> onLabelClicked;
    // Highlight state (true => accent color)
    private boolean highlighted = false;
    // Add field:
    private Object userData;

    // ── Hover warning popup (lazy) ─────────────────────────────────────────────
    private javafx.stage.Popup warningPopup;
    private Label warningPopupLabel;

    public NavigationItem(String name, int depth) {
        this.name = name;
        this.depth = depth;
        initialize();
    }

    public Object getUserData() {
        return this.userData;
    }

    // Add getter/setter:
    public void setUserData(Object data) {
        this.userData = data;
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

        // Icon label — prepended before the text label; hidden by default.
        // Using a separate node ensures label.getText() is never modified,
        // which keeps startInlineRename and navigateToTree safe.
        iconLabel = new Label("");
        iconLabel.setStyle("-fx-text-fill: white;");
        iconLabel.setVisible(false);
        iconLabel.setManaged(false);

        hbox.getChildren().addAll(triangleLabel, iconLabel, label);
        this.rowHBox = hbox;
        // Pre-allocate a 2 px top + 2 px bottom transparent border so that showing
        // drop indicators only changes colour — never size — avoiding layout reflow.
        this.rowHBox.setStyle("-fx-border-color: transparent; -fx-border-width: 2 0 2 0;");
        this.getChildren().add(hbox);

        // Initially show sub-items according to isExpanded
        updateSubItemsVisibility();
    }

    // ADD: Public getter for the internal label.
    public Label getLabel() {
        return label;
    }

    /**
     * Shows a visual drop indicator on the header row.
     * <ul>
     *   <li>BEFORE – yellow top border (insert before this item)</li>
     *   <li>AFTER  – yellow bottom border (insert after this item)</li>
     *   <li>INTO   – subtle background highlight (nest / move into this item)</li>
     * </ul>
     */
    public void showDropIndicator(DropPosition pos) {
        if (rowHBox == null) return;
        switch (pos) {
            case BEFORE:
                rowHBox.setStyle(
                        "-fx-border-color: #cdfc04 transparent transparent transparent;" +
                                "-fx-border-width: 2 0 2 0;");
                break;
            case AFTER:
                rowHBox.setStyle(
                        "-fx-border-color: transparent transparent #cdfc04 transparent;" +
                                "-fx-border-width: 2 0 2 0;");
                break;
            case INTO:
                rowHBox.setStyle(
                        "-fx-border-color: transparent;" +
                                "-fx-border-width: 2 0 2 0;" +
                                "-fx-background-color: #2a1050;");
                break;
        }
    }

    /**
     * Returns the single header row HBox (triangle + label).
     * Used by external code to measure the row height for DnD drop-zone detection.
     */
    public HBox getRowHBox() {
        return rowHBox;
    }

    /** Removes any active drop indicator from the header row. */
    public void clearDropIndicator() {
        if (rowHBox != null)
            rowHBox.setStyle("-fx-border-color: transparent; -fx-border-width: 2 0 2 0;");
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

    /**
     * Inserts a thin horizontal separator line between two deck lists.
     * The separator is indented at the same level as the deck sub-items and
     * participates in the expand/collapse visibility cycle just like they do.
     * Call this once between consecutive non-empty deck-list groups when
     * building the navigation menu for a collection.
     */
    /**
     * Inserts a thin horizontal separator line between two deck lists and returns
     * the separator node so the caller can attach drag-drop handlers to it.
     * The separator participates in the expand/collapse visibility cycle.
     */
    public HBox addDeckListSeparator() {
        // An empty HBox whose background IS the separator line.
        // VBox.fillWidth=true (the default) stretches it to the full NavigationItem width;
        // VBox.setMargin() provides the left indent (matching depth+1 items) and
        // the top/bottom breathing room.  No inner Region is needed, which avoids the
        // 0-preferred-width rendering pitfall of a bare Region inside an HBox.
        HBox sep = new HBox();
        sep.setPrefHeight(1);
        sep.setMinHeight(1);
        sep.setMaxHeight(1);
        sep.setStyle("-fx-background-color: #555577;");
        VBox.setMargin(sep, new Insets(4, 10, 4, 20 * (depth + 1)));

        deckListSeparators.add(sep);
        this.getChildren().add(sep);

        sep.setVisible(isExpanded);
        sep.setManaged(isExpanded);
        return sep;
    }

    /**
     * Appends a transparent footer drop-zone below all sub-items and returns it.
     * The caller attaches drag-over/drop handlers to the returned node.
     *
     * <p>The footer is 8 px tall — tall enough to be a reliable hit target but
     * visually invisible when no drag is in progress.  It collapses along with
     * the rest of the sub-items when the item is collapsed.
     *
     * <p>Must be called <em>after</em> all sub-items have been added so it stays
     * at the bottom of the VBox.  Calling it a second time replaces the previous
     * footer.
     */
    public HBox addFooterZone() {
        if (footerZone != null) this.getChildren().remove(footerZone);
        footerZone = new HBox();
        footerZone.setPrefHeight(8);
        footerZone.setMinHeight(8);
        footerZone.setMaxHeight(8);
        // Pre-allocate border space so indicator changes are colour-only.
        footerZone.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 2 0 2 0;");
        this.getChildren().add(footerZone);
        footerZone.setVisible(isExpanded);
        footerZone.setManaged(isExpanded);
        return footerZone;
    }

    private void updateSubItemsVisibility() {
        for (NavigationItem subItem : subItems) {
            subItem.setVisible(isExpanded);
            subItem.setManaged(isExpanded);
        }
        for (javafx.scene.Node sep : deckListSeparators) {
            sep.setVisible(isExpanded);
            sep.setManaged(isExpanded);
        }
        if (footerZone != null) {
            footerZone.setVisible(isExpanded);
            footerZone.setManaged(isExpanded);
        }
    }

    /**
     * Where an in-progress nav-menu drag will land relative to this item.
     * BEFORE / AFTER = reorder; INTO = nest (e.g. Box inside Box, Deck into Collection).
     */
    public enum DropPosition {BEFORE, INTO, AFTER}

    /**
     * Prepends the icon for the given {@link ItemType} before the text label.
     * The main {@code label} text is never modified, so inline rename and
     * tree navigation code that reads {@code label.getText()} are unaffected.
     */
    public void setItemType(ItemType type) {
        if (type == null) {
            iconLabel.setText("");
            iconLabel.setVisible(false);
            iconLabel.setManaged(false);
        } else {
            iconLabel.setText(type.icon);
            iconLabel.setVisible(true);
            iconLabel.setManaged(true);
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
        String currentLabelText = label.getText() == null ? "" : label.getText().trim();
        if (currentLabelText.startsWith("* ")) currentLabelText = currentLabelText.substring(2).trim();
        javafx.scene.control.TextField tf = new javafx.scene.control.TextField(currentLabelText);
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

    // ── Item type / icon ───────────────────────────────────────────────────────

    /**
     * Attaches a hover popup to this item's header row that shows {@code message}
     * in orange (RGB 235,158,52) when the cursor is over the row.
     * Safe to call multiple times — subsequent calls update the message in-place.
     * Call {@link #clearWarningTooltip()} to remove the popup.
     */
    public void setWarningTooltip(String message) {
        if (warningPopup == null) {
            warningPopupLabel = new Label();
            warningPopupLabel.setWrapText(true);
            warningPopupLabel.setMaxWidth(280);
            warningPopupLabel.setStyle(
                    "-fx-background-color: #1a0428; " +
                            "-fx-background-radius: 6; " +
                            "-fx-border-color: #EB9E34; " +
                            "-fx-border-width: 1; " +
                            "-fx-border-radius: 6; " +
                            "-fx-text-fill: #EB9E34; " +
                            "-fx-font-size: 12; " +
                            "-fx-font-weight: bold; " +
                            "-fx-padding: 7 10 7 10;"
            );
            warningPopup = new javafx.stage.Popup();
            warningPopup.getContent().add(warningPopupLabel);
            warningPopup.setAutoFix(true);
            warningPopup.setAutoHide(false);

            rowHBox.setOnMouseEntered(e -> {
                if (warningPopup != null && warningPopupLabel != null) {
                    warningPopup.show(rowHBox, e.getScreenX() + 14, e.getScreenY() + 14);
                }
            });
            rowHBox.setOnMouseMoved(e -> {
                if (warningPopup != null && warningPopup.isShowing()) {
                    warningPopup.show(rowHBox, e.getScreenX() + 14, e.getScreenY() + 14);
                }
            });
            rowHBox.setOnMouseExited(e -> {
                if (warningPopup != null) warningPopup.hide();
            });
        }
        warningPopupLabel.setText(message);
    }

    // ── Hover warning popup ────────────────────────────────────────────────────

    /**
     * Removes the hover warning popup from this item's header row.
     */
    public void clearWarningTooltip() {
        if (warningPopup != null) {
            warningPopup.hide();
            warningPopup = null;
            warningPopupLabel = null;
        }
        if (rowHBox != null) {
            rowHBox.setOnMouseEntered(null);
            rowHBox.setOnMouseMoved(null);
            rowHBox.setOnMouseExited(null);
        }
    }

    /**
     * Visual type of a navigation item, used to prepend an icon to the row.
     */
    public enum ItemType {
        BOX("📦 "),
        CATEGORY("📁 ");

        public final String icon;

        ItemType(String icon) {
            this.icon = icon; }
    }
}