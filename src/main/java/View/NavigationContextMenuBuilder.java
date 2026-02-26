package View;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.util.List;

public final class NavigationContextMenuBuilder {

    private NavigationContextMenuBuilder() { /* static factory */ }

    // =========================================================================
    // My Collection tab
    // =========================================================================

    /**
     * Context menu for a Box in My Collection.
     * "Move to..." lists all other boxes (by instance); if the box is currently
     * a sub-box of another box, "No Box" is appended to allow promoting it.
     *
     * Signature changed: now takes the actual Box instance and the full collection
     * so identity comparisons and sub-box detection work correctly.
     */
    public static ContextMenu forMyCollectionBox(
            Model.CardsLists.Box box,
            Model.CardsLists.OwnedCardsCollection owned) {

        ContextMenu cm = styledContextMenu();

        // ── "Move to..." submenu ──────────────────────────────────────────────
        Menu moveMenu = makeLazyMenu("Move to...");
        moveMenu.setOnShowing(evt -> {
            moveMenu.getItems().clear();

            if (owned == null || owned.getOwnedCollection() == null) {
                MenuItem none = disabledItem("No destinations available");
                moveMenu.getItems().add(none);
                return;
            }

            // Detect whether this box is currently a sub-box of another
            boolean isSubBox = false;
            for (Model.CardsLists.Box parent : owned.getOwnedCollection()) {
                if (parent == null || parent.getSubBoxes() == null) continue;
                for (Model.CardsLists.Box sb : parent.getSubBoxes()) {
                    if (sb == box) {
                        isSubBox = true;
                        break;
                    }
                }
                if (isSubBox) break;
            }

            // One entry per other top-level box
            for (Model.CardsLists.Box other : owned.getOwnedCollection()) {
                if (other == null || other == box) continue;
                String name = sanitize(other.getName());
                if (name.isEmpty()) name = "(Unnamed box)";
                moveMenu.getItems().add(makeItem(name));
            }

            // "No Box" — only shown when the clicked box is currently nested
            if (isSubBox) {
                moveMenu.getItems().add(new SeparatorMenuItem());
                moveMenu.getItems().add(makeItem("No Box"));
            }

            if (moveMenu.getItems().isEmpty()) {
                moveMenu.getItems().add(disabledItem("No other boxes"));
            }
        });

        cm.getItems().addAll(
                moveMenu,
                makeItem("Add Box"),
                makeItem("Add Category"),
                makeItem("Rename"),
                new SeparatorMenuItem(),
                makeRemoveItem()
        );
        return cm;
    }

    /**
     * Context menu for a Category (CardsGroup) in My Collection.
     * "Move to..." lists every Box and every Category in the collection,
     * excluding the current category and the box it belongs to.
     *
     * Signature changed: takes the actual CardsGroup, its direct parent Box,
     * and the full collection.
     */
    public static ContextMenu forMyCollectionCategory(
            Model.CardsLists.CardsGroup category,
            Model.CardsLists.Box parentBox,
            Model.CardsLists.OwnedCardsCollection owned) {

        ContextMenu cm = styledContextMenu();

        // ── "Move to..." submenu ──────────────────────────────────────────────
        Menu moveMenu = makeLazyMenu("Move to...");
        moveMenu.setOnShowing(evt -> {
            moveMenu.getItems().clear();

            if (owned == null || owned.getOwnedCollection() == null) {
                moveMenu.getItems().add(disabledItem("No destinations available"));
                return;
            }

            for (Model.CardsLists.Box box : owned.getOwnedCollection()) {
                if (box == null) continue;
                String boxName = sanitize(box.getName());
                if (boxName.isEmpty()) boxName = "(Unnamed box)";

                // Skip the direct parent box (can't move category into the same box)
                if (box == parentBox) continue;

                // Add the box itself as a destination
                moveMenu.getItems().add(makeItem(boxName));

                // Add each category inside this box (skip the category being moved)
                if (box.getContent() != null) {
                    for (Model.CardsLists.CardsGroup g : box.getContent()) {
                        if (g == null || g == category) continue;
                        String groupName = sanitize(g.getName());
                        if (groupName.isEmpty()) continue;
                        moveMenu.getItems().add(makeItem(boxName + " / " + groupName));
                    }
                }

                // Sub-boxes and their categories
                if (box.getSubBoxes() != null) {
                    for (Model.CardsLists.Box sb : box.getSubBoxes()) {
                        if (sb == null) continue;
                        String subName = sanitize(sb.getName());
                        if (subName.isEmpty()) subName = "(Unnamed sub-box)";
                        moveMenu.getItems().add(makeItem(subName));
                        if (sb.getContent() != null) {
                            for (Model.CardsLists.CardsGroup g : sb.getContent()) {
                                if (g == null || g == category) continue;
                                String groupName = sanitize(g.getName());
                                if (groupName.isEmpty()) continue;
                                moveMenu.getItems().add(makeItem(subName + " / " + groupName));
                            }
                        }
                    }
                }
            }

            if (moveMenu.getItems().isEmpty()) {
                moveMenu.getItems().add(disabledItem("No destinations available"));
            }
        });

        cm.getItems().addAll(
                moveMenu,
                makeItem("Add Category"),
                makeItem("Rename"),
                new SeparatorMenuItem(),
                makeRemoveItem()
        );
        return cm;
    }

    /**
     * Context menu shown when right-clicking an empty area in My Collection.
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
     * Context menu for a Collection in Decks and Collections.
     * Collections cannot be moved, so there is no "Move to..." entry.
     */
    public static ContextMenu forDecksCollection(String collectionName) {
        ContextMenu cm = styledContextMenu();
        cm.getItems().addAll(
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
     * Context menu for a Deck in Decks and Collections.
     * "Move to..." lists every collection with numbered deck groups plus
     * a "New Deck group" entry for each collection.
     *
     * Signature changed: takes the actual Deck instance and the full
     * DecksAndCollectionsList so the submenu can be built accurately.
     */
    public static ContextMenu forDecksDeck(
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList dac) {

        ContextMenu cm = styledContextMenu();

        // ── "Move to..." submenu ──────────────────────────────────────────────
        Menu moveMenu = makeLazyMenu("Move to...");
        moveMenu.setOnShowing(evt -> {
            moveMenu.getItems().clear();

            if (dac == null || dac.getCollections() == null) {
                moveMenu.getItems().add(disabledItem("No collections available"));
                return;
            }

            // Detect whether this deck currently belongs to a collection
            boolean isInCollection = false;
            if (dac.getCollections() != null) {
                outer:
                for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                    if (tc == null || tc.getLinkedDecks() == null) continue;
                    for (List<Model.CardsLists.Deck> group : tc.getLinkedDecks()) {
                        if (group == null) continue;
                        for (Model.CardsLists.Deck d : group) {
                            if (d == deck) {
                                isInCollection = true;
                                break outer;
                            }
                        }
                    }
                }
            }

            for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                if (tc == null) continue;
                String collName = sanitize(tc.getName());
                if (collName.isEmpty()) collName = "(Unnamed collection)";

                List<List<Model.CardsLists.Deck>> groups = tc.getLinkedDecks();
                int groupCount = (groups == null) ? 0 : groups.size();

                // One entry per existing deck group
                for (int i = 1; i <= groupCount; i++) {
                    moveMenu.getItems().add(makeItem(collName + " / Deck group " + i));
                }

                // Always offer a "New Deck group" entry
                moveMenu.getItems().add(makeItem(collName + " / New Deck group"));
            }

            // "No Collection" — only shown when the deck is currently inside a collection
            if (isInCollection) {
                moveMenu.getItems().add(new SeparatorMenuItem());
                moveMenu.getItems().add(makeItem("No Collection"));
            }

            if (moveMenu.getItems().isEmpty()) {
                moveMenu.getItems().add(disabledItem("No collections available"));
            }
        });

        cm.getItems().addAll(
                moveMenu,
                makeItem("Add Collection"),
                makeItem("Add Deck"),
                makeItem("Rename"),
                new SeparatorMenuItem(),
                makeRemoveItem()
        );
        return cm;
    }

    /**
     * Context menu shown when right-clicking an empty area in Decks and Collections.
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
    // Helpers
    // =========================================================================

    public static MenuItem makeItem(String text) {
        MenuItem mi = new MenuItem();
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(graphic);
        mi.setText("");
        mi.setOnAction(e -> { /* TODO: implement action for: " + text + " */ });
        return mi;
    }

    public static MenuItem makeRemoveItem() {
        MenuItem mi = new MenuItem();
        Label trashIcon = new Label("\uD83D\uDDD1");
        trashIcon.setStyle("-fx-text-fill: #ff4d4d; -fx-font-size: 13;");
        Label removeLabel = new Label("Remove");
        removeLabel.setStyle("-fx-text-fill: #ff4d4d; -fx-font-weight: bold; -fx-font-size: 13;");
        HBox graphic = new HBox(6, trashIcon, removeLabel);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(graphic);
        mi.setText("");
        mi.setOnAction(e -> { /* TODO: implement remove action */ });
        return mi;
    }

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

    /**
     * A Menu whose header label is white, styled like the other menus.
     */
    private static Menu makeLazyMenu(String text) {
        Menu m = new Menu();
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox g = new HBox(lbl);
        g.setAlignment(Pos.CENTER_LEFT);
        g.setPadding(new Insets(2, 6, 2, 6));
        m.setGraphic(g);
        m.setText("");
        // Placeholder so JavaFX renders the arrow before first hover
        MenuItem ph = new MenuItem("Loading...");
        ph.setDisable(true);
        m.getItems().add(ph);
        return m;
    }

    private static MenuItem disabledItem(String text) {
        MenuItem mi = new MenuItem(text);
        mi.setDisable(true);
        return mi;
    }

    private static String sanitize(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[=\\-]", "").trim();
    }
}