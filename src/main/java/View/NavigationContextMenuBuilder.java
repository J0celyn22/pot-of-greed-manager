package View;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;

import java.util.List;
import java.util.Optional;

public final class NavigationContextMenuBuilder {

    private NavigationContextMenuBuilder() { /* static factory */ }

    // =========================================================================
    // My Collection tab
    // =========================================================================

    public static ContextMenu forMyCollectionBox(
            Model.CardsLists.Box box,
            Model.CardsLists.OwnedCardsCollection owned) {

        ContextMenu cm = styledContextMenu();

        Menu moveMenu = makeLazyMenu("Move to...");
        moveMenu.setOnShowing(evt -> {
            moveMenu.getItems().clear();

            if (owned == null || owned.getOwnedCollection() == null) {
                moveMenu.getItems().add(disabledItem("No destinations available"));
                return;
            }

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

            for (Model.CardsLists.Box other : owned.getOwnedCollection()) {
                if (other == null || other == box) continue;
                String name = sanitize(other.getName());
                if (name.isEmpty()) name = "(Unnamed box)";
                moveMenu.getItems().add(makeItem(name));
            }

            if (isSubBox) {
                moveMenu.getItems().add(new SeparatorMenuItem());
                moveMenu.getItems().add(makeItem("No Box"));
            }

            if (moveMenu.getItems().isEmpty()) {
                moveMenu.getItems().add(disabledItem("No other boxes"));
            }
        });

        Runnable removeAction = () -> {
            if (!isBoxEmpty(box) && !confirmRemoval("Box")) return;
            if (owned != null && owned.getOwnedCollection() != null) {
                if (owned.getOwnedCollection().remove(box)) {
                    refreshOwnedCollectionView();
                    return;
                }
                // Box might be a sub-box
                for (Model.CardsLists.Box parent : owned.getOwnedCollection()) {
                    if (parent == null || parent.getSubBoxes() == null) continue;
                    if (parent.getSubBoxes().remove(box)) {
                        refreshOwnedCollectionView();
                        return;
                    }
                }
            }
        };

        cm.getItems().addAll(
                moveMenu,
                makeItem("Add Box"),
                makeItem("Add Category"),
                makeItem("Rename"),
                new SeparatorMenuItem(),
                makeRemoveItem(removeAction)
        );
        return cm;
    }

    public static ContextMenu forMyCollectionCategory(
            Model.CardsLists.CardsGroup category,
            Model.CardsLists.Box parentBox,
            Model.CardsLists.OwnedCardsCollection owned) {

        ContextMenu cm = styledContextMenu();

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

                if (box == parentBox) continue;

                moveMenu.getItems().add(makeItem(boxName));

                if (box.getContent() != null) {
                    for (Model.CardsLists.CardsGroup g : box.getContent()) {
                        if (g == null || g == category) continue;
                        String groupName = sanitize(g.getName());
                        if (groupName.isEmpty()) continue;
                        moveMenu.getItems().add(makeItem(boxName + " / " + groupName));
                    }
                }

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

        Runnable removeAction = () -> {
            if (!isCategoryEmpty(category) && !confirmRemoval("Category")) return;
            if (parentBox != null && parentBox.getContent() != null) {
                parentBox.getContent().remove(category);
                refreshOwnedCollectionView();
            }
        };

        cm.getItems().addAll(
                moveMenu,
                makeItem("Add Category"),
                makeItem("Rename"),
                new SeparatorMenuItem(),
                makeRemoveItem(removeAction)
        );
        return cm;
    }

    public static ContextMenu forMyCollectionEmpty() {
        ContextMenu cm = styledContextMenu();
        cm.getItems().add(makeItem("Add Box"));
        return cm;
    }

    // =========================================================================
    // Decks and Collections tab
    // =========================================================================

    /**
     * Collections cannot be moved, so there is no "Move to..." entry.
     * Signature updated: now takes the actual ThemeCollection and DAC
     * so the Remove button can work.
     */
    public static ContextMenu forDecksCollection(
            Model.CardsLists.ThemeCollection collection,
            Model.CardsLists.DecksAndCollectionsList dac) {

        ContextMenu cm = styledContextMenu();

        Runnable removeAction = () -> {
            if (!isCollectionEmpty(collection) && !confirmRemoval("Collection")) return;
            if (dac != null && dac.getCollections() != null) {
                dac.getCollections().remove(collection);
                refreshDecksAndCollectionsView();
            }
        };

        cm.getItems().addAll(
                makeItem("Add Collection"),
                makeItem("Add Deck"),
                makeItem("Add archetype"),
                makeItem("Rename"),
                new SeparatorMenuItem(),
                makeRemoveItem(removeAction)
        );
        return cm;
    }

    public static ContextMenu forDecksDeck(
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList dac) {

        ContextMenu cm = styledContextMenu();

        Menu moveMenu = makeLazyMenu("Move to...");
        moveMenu.setOnShowing(evt -> {
            moveMenu.getItems().clear();

            if (dac == null || dac.getCollections() == null) {
                moveMenu.getItems().add(disabledItem("No collections available"));
                return;
            }

            boolean isInCollection = false;
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

            for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                if (tc == null) continue;
                String collName = sanitize(tc.getName());
                if (collName.isEmpty()) collName = "(Unnamed collection)";

                List<List<Model.CardsLists.Deck>> groups = tc.getLinkedDecks();
                int groupCount = (groups == null) ? 0 : groups.size();

                for (int i = 1; i <= groupCount; i++) {
                    moveMenu.getItems().add(makeItem(collName + " / Deck group " + i));
                }
                moveMenu.getItems().add(makeItem(collName + " / New Deck group"));
            }

            if (isInCollection) {
                moveMenu.getItems().add(new SeparatorMenuItem());
                moveMenu.getItems().add(makeItem("No Collection"));
            }

            if (moveMenu.getItems().isEmpty()) {
                moveMenu.getItems().add(disabledItem("No collections available"));
            }
        });

        Runnable removeAction = () -> {
            if (!isDeckEmpty(deck) && !confirmRemoval("Deck")) return;
            // Remove from standalone decks list
            if (dac.getDecks() != null && dac.getDecks().remove(deck)) {
                refreshDecksAndCollectionsView();
                return;
            }
            // Remove from a collection's deck group
            if (dac.getCollections() != null) {
                outer:
                for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                    if (tc == null || tc.getLinkedDecks() == null) continue;
                    for (List<Model.CardsLists.Deck> group : tc.getLinkedDecks()) {
                        if (group == null) continue;
                        if (group.remove(deck)) {
                            refreshDecksAndCollectionsView();
                            break outer;
                        }
                    }
                }
            }
        };

        cm.getItems().addAll(
                moveMenu,
                makeItem("Add Collection"),
                makeItem("Add Deck"),
                makeItem("Rename"),
                new SeparatorMenuItem(),
                makeRemoveItem(removeAction)
        );
        return cm;
    }

    public static ContextMenu forDecksEmpty() {
        ContextMenu cm = styledContextMenu();
        cm.getItems().addAll(
                makeItem("Add Collection"),
                makeItem("Add Deck")
        );
        return cm;
    }

    // =========================================================================
    // Public helpers (used by CardTreeCell)
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

    /**
     * Remove item with no action (stub).
     */
    public static MenuItem makeRemoveItem() {
        return makeRemoveItem(null);
    }

    /**
     * Remove item wired to the given action.
     */
    public static MenuItem makeRemoveItem(Runnable action) {
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
        mi.setOnAction(e -> {
            if (action != null) action.run(); });
        return mi;
    }

    public static ContextMenu styledContextMenu() {
        ContextMenu cm = new ContextMenu();
        cm.setStyle(
                "-fx-background-color: #100317; " +
                        "-fx-background-radius: 6; " +
                        "-fx-border-color: #3a3a3a; " +
                        "-fx-border-radius: 6; "            +
                        "-fx-border-width: 1;"
        );
        return cm;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private static Menu makeLazyMenu(String text) {
        Menu m = new Menu();
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox g = new HBox(lbl);
        g.setAlignment(Pos.CENTER_LEFT);
        g.setPadding(new Insets(2, 6, 2, 6));
        m.setGraphic(g);
        m.setText("");
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

    // ── Emptiness checks ─────────────────────────────────────────────────────

    private static boolean isBoxEmpty(Model.CardsLists.Box box) {
        if (box == null) return true;
        if (box.getSubBoxes() != null && !box.getSubBoxes().isEmpty()) return false;
        if (box.getContent() != null) {
            for (Model.CardsLists.CardsGroup g : box.getContent()) {
                if (g == null) continue;
                if (g.getCardList() != null && !g.getCardList().isEmpty()) return false;
            }
        }
        return true;
    }

    private static boolean isCategoryEmpty(Model.CardsLists.CardsGroup category) {
        if (category == null) return true;
        return category.getCardList() == null || category.getCardList().isEmpty();
    }

    private static boolean isCollectionEmpty(Model.CardsLists.ThemeCollection tc) {
        if (tc == null) return true;
        // Check the collection's own card list
        try {
            java.util.List<?> list = tc.toList();
            if (list != null && !list.isEmpty()) return false;
        } catch (Exception ignored) {
        }
        // Check linked decks
        if (tc.getLinkedDecks() != null) {
            for (List<Model.CardsLists.Deck> group : tc.getLinkedDecks()) {
                if (group != null && !group.isEmpty()) return false;
            }
        }
        // Check archetypes via reflection (method name may vary)
        try {
            java.lang.reflect.Method m = tc.getClass().getMethod("getArchetypes");
            Object result = m.invoke(tc);
            if (result instanceof java.util.Collection && !((java.util.Collection<?>) result).isEmpty())
                return false;
        } catch (Exception ignored) {
        }
        return true;
    }

    private static boolean isDeckEmpty(Model.CardsLists.Deck deck) {
        if (deck == null) return true;
        if (deck.getMainDeck() != null && !deck.getMainDeck().isEmpty()) return false;
        if (deck.getExtraDeck() != null && !deck.getExtraDeck().isEmpty()) return false;
        if (deck.getSideDeck() != null && !deck.getSideDeck().isEmpty()) return false;
        return true;
    }

    // ── Confirmation dialog ───────────────────────────────────────────────────

    private static boolean confirmRemoval(String elementType) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Removal");
        alert.setHeaderText(null);
        alert.setContentText("Do you really want to remove this " + elementType + "?");

        ButtonType yes = new ButtonType("Yes");
        ButtonType no = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(yes, no);

        javafx.scene.control.DialogPane dp = alert.getDialogPane();

        // ── Dialog pane background and border ───────────────────────────────
        dp.setStyle(
                "-fx-background-color: #100317;" +
                        "-fx-border-color: #cdfc04;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;"
        );

        // ── Content text ─────────────────────────────────────────────────────
        dp.setContentText("Do you really want to remove this " + elementType + "?");
        javafx.scene.Node contentLabel = dp.lookup(".content.label");
        if (contentLabel instanceof javafx.scene.control.Label lbl) {
            lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13px;");
        }

        // ── Button bar: center the buttons ───────────────────────────────────
        javafx.scene.Node barNode = dp.lookup(".button-bar");
        if (barNode instanceof javafx.scene.control.ButtonBar bar) {
            bar.setStyle("-fx-background-color: #100317; -fx-padding: 10 20 14 20;");
            bar.setButtonMinWidth(80);
            // CENTER_LAST puts both buttons as a centered group
            bar.setButtonOrder(javafx.scene.control.ButtonBar.BUTTON_ORDER_NONE);
            bar.setStyle(bar.getStyle() + "-fx-alignment: center;");
        }

        // ── Style buttons — must use show() listener so nodes exist ──────────
        alert.getDialogPane().getScene().windowProperty().addListener((obs, oldW, newW) -> {
            if (newW != null) stylePopupButtons(dp, yes, no);
        });
        // Also try immediately in case scene/window already set
        if (dp.getScene() != null && dp.getScene().getWindow() != null) {
            stylePopupButtons(dp, yes, no);
        }

        // Remove the title bar entirely
        javafx.stage.Stage stage = (javafx.stage.Stage) dp.getScene().getWindow();
        stage.initStyle(javafx.stage.StageStyle.UNDECORATED);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == yes;
    }

    private static void stylePopupButtons(javafx.scene.control.DialogPane dp,
                                          ButtonType yes, ButtonType no) {
        String base =
                "-fx-background-radius: 4;" +
                        "-fx-border-radius: 4;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-font-size: 13px;" +
                        "-fx-padding: 6 18 6 18;" +
                        "-fx-cursor: hand;";

        String yesNormal = base + "-fx-background-color: black; -fx-text-fill: #ff4d4d; -fx-border-color: #ff4d4d;";
        String yesHover = base + "-fx-background-color: #c92c2c; -fx-text-fill: black; -fx-font-weight: bold; -fx-border-color: #ff4d4d;";
        String noNormal = base + "-fx-background-color: black; -fx-text-fill: #cdfc04; -fx-border-color: #cdfc04;";
        String noHover = base + "-fx-background-color: #a4c904; -fx-text-fill: black; -fx-font-weight: bold; -fx-border-color: #cdfc04;";

        javafx.scene.Node yesNode = dp.lookupButton(yes);
        javafx.scene.Node noNode = dp.lookupButton(no);

        if (yesNode instanceof javafx.scene.control.Button btn) {
            btn.setStyle(yesNormal);
            btn.setOnMouseEntered(e -> btn.setStyle(yesHover));
            btn.setOnMouseExited(e -> btn.setStyle(yesNormal));
        }
        if (noNode instanceof javafx.scene.control.Button btn) {
            btn.setStyle(noNormal);
            btn.setOnMouseEntered(e -> btn.setStyle(noHover));
            btn.setOnMouseExited(e -> btn.setStyle(noNormal));
        }
    }

    // ── UI refresh helpers ────────────────────────────────────────────────────

    private static void refreshOwnedCollectionView() {
        Controller.UserInterfaceFunctions.refreshOwnedCollectionView();
    }

    private static void refreshDecksAndCollectionsView() {
        Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
    }
}