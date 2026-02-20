package View;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.HBox;

/**
 * NavigationContextMenuBuilder
 * <p>
 * Builds styled right-click context menus for the navigation panel,
 * varying by tab and item type. All action handlers are stubs
 * and must be wired in a later implementation step.
 * <p>
 * Styling mirrors the existing card context menus in CardTreeCell:
 * - Dark background  (#100317)
 * - White text for regular items
 * - Red text (#ff4d4d) + trash-can icon for "Remove"
 * - Subtle border (#3a3a3a)
 */
public final class NavigationContextMenuBuilder {

    private NavigationContextMenuBuilder() { /* static factory */ }

    // =========================================================================
    // My Collection tab
    // =========================================================================

    /**
     * Context menu shown when right-clicking a <b>Box</b> name in the
     * My Collection navigation panel.
     * <p>
     * Items: Move to… | Add Box | Add Category | Rename | ── | Remove
     */
    public static ContextMenu forMyCollectionBox(String boxName) {
        ContextMenu cm = styledContextMenu();
        cm.getItems().addAll(
                makeItem("Move to..."),
                makeItem("Add Box"),
                makeItem("Add Category"),
                makeItem("Rename"),
                new SeparatorMenuItem(),
                makeRemoveItem()
        );
        return cm;
    }

    /**
     * Context menu shown when right-clicking a <b>Category</b> (group/sub-box)
     * name in the My Collection navigation panel.
     * <p>
     * Items: Move to… | Add Category | Rename | ── | Remove
     */
    public static ContextMenu forMyCollectionCategory(String categoryName) {
        ContextMenu cm = styledContextMenu();
        cm.getItems().addAll(
                makeItem("Move to..."),
                makeItem("Add Category"),
                makeItem("Rename"),
                new SeparatorMenuItem(),
                makeRemoveItem()
        );
        return cm;
    }

    /**
     * Context menu shown when right-clicking an <b>empty area</b> in the
     * My Collection navigation panel.
     * <p>
     * Items: Add Box
     */
    public static ContextMenu forMyCollectionEmpty() {
        ContextMenu cm = styledContextMenu();
        cm.getItems().add(makeItem("Add Box"));
        return cm;
    }

    // =========================================================================
    // Decks and Collections tab
    // =========================================================================

    /**
     * Context menu shown when right-clicking a <b>Collection</b> name in the
     * Decks and Collections navigation panel.
     * <p>
     * Items: Move to… | Add Collection | Add Deck | Add archetype | Rename | ── | Remove
     */
    public static ContextMenu forDecksCollection(String collectionName) {
        ContextMenu cm = styledContextMenu();
        cm.getItems().addAll(
                makeItem("Move to..."),
                makeItem("Add Collection"),
                makeItem("Add Deck"),
                makeItem("Add archetype"),
                makeItem("Rename"),
                new SeparatorMenuItem(),
                makeRemoveItem()
        );
        return cm;
    }

    /**
     * Context menu shown when right-clicking a <b>Deck</b> name in the
     * Decks and Collections navigation panel.
     * <p>
     * Items: Move to… | Move to new Collection | Add Collection | Add Deck |
     * Rename | ── | Remove
     */
    public static ContextMenu forDecksDeck(String deckName) {
        ContextMenu cm = styledContextMenu();
        cm.getItems().addAll(
                makeItem("Move to..."),
                makeItem("Move to new Collection"),
                makeItem("Add Collection"),
                makeItem("Add Deck"),
                makeItem("Rename"),
                new SeparatorMenuItem(),
                makeRemoveItem()
        );
        return cm;
    }

    /**
     * Context menu shown when right-clicking an <b>empty area</b> in the
     * Decks and Collections navigation panel.
     * <p>
     * Items: Add Collection | Add Deck
     */
    public static ContextMenu forDecksEmpty() {
        ContextMenu cm = styledContextMenu();
        cm.getItems().addAll(
                makeItem("Add Collection"),
                makeItem("Add Deck")
        );
        return cm;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Build a regular (white-text) MenuItem using a Label graphic so the style
     * matches the rest of the application's menus exactly.
     */
    public static MenuItem makeItem(String text) {
        MenuItem mi = new MenuItem();
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(graphic);
        mi.setText("");
        // Action stub – to be implemented later
        mi.setOnAction(e -> { /* TODO: implement action for: " + text + " */ });
        return mi;
    }

    /**
     * Build the red "Remove" MenuItem with a trash-can icon to its left,
     * matching the style used in CardTreeCell's card context menus.
     */
    public static MenuItem makeRemoveItem() {
        MenuItem mi = new MenuItem();

        // Trash-can icon (🗑 U+1F5D1)
        Label trashIcon = new Label("\uD83D\uDDD1");
        trashIcon.setStyle("-fx-text-fill: #ff4d4d; -fx-font-size: 13;");

        Label removeLabel = new Label("Remove");
        removeLabel.setStyle("-fx-text-fill: #ff4d4d; -fx-font-weight: bold; -fx-font-size: 13;");

        HBox graphic = new HBox(6, trashIcon, removeLabel);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(graphic);
        mi.setText("");
        // Action stub – to be implemented later
        mi.setOnAction(e -> { /* TODO: implement remove action */ });
        return mi;
    }

    /**
     * Create a ContextMenu pre-styled to the application's dark theme.
     * The CSS stylesheet loaded at application start handles hover states;
     * only the container background needs to be set inline.
     */
    public static ContextMenu styledContextMenu() {
        ContextMenu cm = new ContextMenu();
        cm.setStyle(
                "-fx-background-color: #100317; " +
                        "-fx-background-radius: 6; " +
                        "-fx-border-color: #3a3a3a; " +
                        "-fx-border-radius: 6; " +
                        "-fx-border-width: 1;"
        );
        return cm;
    }
}