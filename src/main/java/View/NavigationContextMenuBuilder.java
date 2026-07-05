package View;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared styling, dialog, and navigation helpers used by both
 * {@link MyCollectionNavMenuBuilder} and {@link DecksNavMenuBuilder}.
 *
 * <p>Tab-specific context menus live in their respective builder classes.
 * This class provides the shared utilities that both sub-builders and
 * external callers ({@link View.CardTreeCell}, controllers) depend on.</p>
 */
public final class NavigationContextMenuBuilder {

    private static final Logger logger =
            LoggerFactory.getLogger(NavigationContextMenuBuilder.class);

    private NavigationContextMenuBuilder() {
    }

    // =========================================================================
    // Public helpers (used by CardTreeCell and external controllers)
    // =========================================================================

    public static MenuItem makeItem(String text) {
        MenuItem menuItem = new MenuItem();
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        menuItem.setGraphic(graphic);
        menuItem.setText("");
        menuItem.setOnAction(e -> { /* TODO: implement action for: " + text + " */ });
        return menuItem;
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
        MenuItem menuItem = new MenuItem();
        Label trashIcon = new Label("\uD83D\uDDD1");
        trashIcon.setStyle("-fx-text-fill: #ff4d4d; -fx-font-size: 13;");
        Label removeLabel = new Label("Remove");
        removeLabel.setStyle("-fx-text-fill: #ff4d4d; -fx-font-weight: bold; -fx-font-size: 13;");
        HBox graphic = new HBox(6, trashIcon, removeLabel);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        menuItem.setGraphic(graphic);
        menuItem.setText("");
        menuItem.setOnAction(e -> {
            if (action != null) {
                action.run();
            }
        });
        return menuItem;
    }

    public static ContextMenu styledContextMenu() {
        return ContextMenuItemFactory.styledContextMenu();
    }

    // =========================================================================
    // Package-private helpers (used by MyCollectionNavMenuBuilder and DecksNavMenuBuilder)
    // =========================================================================

    static Menu makeLazyMenu(String text) {
        Menu menu = new Menu();
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        menu.setGraphic(graphic);
        menu.setText("");
        MenuItem placeholder = new MenuItem("Loading...");
        placeholder.setDisable(true);
        menu.getItems().add(placeholder);
        return menu;
    }

    static MenuItem disabledItem(String text) {
        return ContextMenuItemFactory.disabledItem(text);
    }

    static MenuItem makeActionItem(String text, Runnable action) {
        MenuItem menuItem = new MenuItem();
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        menuItem.setGraphic(graphic);
        menuItem.setText("");
        menuItem.setOnAction(e -> {
            if (action != null) {
                action.run();
            }
        });
        return menuItem;
    }

    static MenuItem makePasteItem(Runnable pasteAction) {
        MenuItem menuItem = new MenuItem();
        Label label = new Label("Paste");
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        menuItem.setGraphic(graphic);
        menuItem.setText("");
        menuItem.setVisible(!Controller.CardClipboard.isEmpty());
        Controller.CardClipboard.addChangeListener(() -> {
            javafx.application.Platform.runLater(() ->
                    menuItem.setVisible(!Controller.CardClipboard.isEmpty()));
        });
        menuItem.setOnAction(e -> {
            if (!Controller.CardClipboard.isEmpty()) {
                pasteAction.run();
            }
        });
        return menuItem;
    }

    // ── Emptiness checks ─────────────────────────────────────────────────────

    static boolean isBoxEmpty(Model.CardsLists.Box box) {
        if (box == null) {
            return true;
        }
        if (box.getSubBoxes() != null && !box.getSubBoxes().isEmpty()) {
            return false;
        }
        if (box.getContent() != null) {
            for (Model.CardsLists.CardsGroup group : box.getContent()) {
                if (group == null) {
                    continue;
                }
                if (group.getCardList() != null && !group.getCardList().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    static boolean isCategoryEmpty(Model.CardsLists.CardsGroup category) {
        if (category == null) {
            return true;
        }
        return category.getCardList() == null || category.getCardList().isEmpty();
    }

    static boolean isCollectionEmpty(Model.CardsLists.ThemeCollection collection) {
        if (collection == null) {
            return true;
        }
        try {
            java.util.List<?> list = collection.toList();
            if (list != null && !list.isEmpty()) {
                return false;
            }
        } catch (Exception ignored) {
        }
        if (collection.getLinkedDecks() != null) {
            for (java.util.List<Model.CardsLists.Deck> group : collection.getLinkedDecks()) {
                if (group != null && !group.isEmpty()) {
                    return false;
                }
            }
        }
        try {
            java.lang.reflect.Method method =
                    collection.getClass().getMethod("getArchetypes");
            Object result = method.invoke(collection);
            if (result instanceof java.util.Collection
                    && !((java.util.Collection<?>) result).isEmpty()) {
                return false;
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    static boolean isDeckEmpty(Model.CardsLists.Deck deck) {
        if (deck == null) {
            return true;
        }
        if (deck.getMainDeck() != null && !deck.getMainDeck().isEmpty()) {
            return false;
        }
        if (deck.getExtraDeck() != null && !deck.getExtraDeck().isEmpty()) {
            return false;
        }
        if (deck.getSideDeck() != null && !deck.getSideDeck().isEmpty()) {
            return false;
        }
        return true;
    }

    // ── Confirmation dialog ───────────────────────────────────────────────────

    /**
     * Shows a styled Yes/No confirmation dialog.
     * Enter triggers Yes, Escape triggers No.
     */
    public static boolean confirmWithCustomMessage(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm");
        alert.setHeaderText(null);

        ButtonType yes = new ButtonType("Yes", ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(yes, no);

        javafx.scene.control.DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle(
                "-fx-background-color: #100317;"
                        + "-fx-border-color: #cdfc04;"
                        + "-fx-border-width: 1.5;"
                        + "-fx-border-radius: 8;"
                        + "-fx-background-radius: 8;");
        dialogPane.setContentText(message);

        javafx.scene.Node contentLabel = dialogPane.lookup(".content.label");
        if (contentLabel instanceof javafx.scene.control.Label) {
            ((javafx.scene.control.Label) contentLabel)
                    .setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13px;");
        }

        javafx.scene.Node barNode = dialogPane.lookup(".button-bar");
        if (barNode instanceof javafx.scene.control.ButtonBar) {
            ((javafx.scene.control.ButtonBar) barNode).setStyle(
                    "-fx-background-color: #100317; -fx-padding: 10 20 14 20;");
        }

        alert.getDialogPane().getScene().windowProperty().addListener(
                (obs, oldWindow, newWindow) -> {
                    if (newWindow != null) {
                        stylePopupButtons(dialogPane, yes, no);
                    }
                });
        if (dialogPane.getScene() != null && dialogPane.getScene().getWindow() != null) {
            stylePopupButtons(dialogPane, yes, no);
        }

        javafx.stage.Stage stage =
                (javafx.stage.Stage) dialogPane.getScene().getWindow();
        stage.initStyle(javafx.stage.StageStyle.UNDECORATED);

        java.util.Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == yes;
    }

    static boolean confirmRemoval(String elementType) {
        return confirmWithCustomMessage(
                "Do you really want to remove this " + elementType + "?");
    }

    /**
     * Confirmation prompt for a "Clear" action, worded for emptying a list/deck
     * in place rather than removing the element itself.
     */
    static boolean confirmClear(String elementType) {
        return confirmWithCustomMessage(
                "Do you really want to clear all cards from this " + elementType + "?");
    }

    private static void stylePopupButtons(
            javafx.scene.control.DialogPane dialogPane,
            ButtonType yes,
            ButtonType no) {
        String base =
                "-fx-background-radius: 4;"
                        + "-fx-border-radius: 4;"
                        + "-fx-border-width: 1.5;"
                        + "-fx-font-size: 13px;"
                        + "-fx-padding: 6 18 6 18;"
                        + "-fx-cursor: hand;";
        String yesNormal = base
                + "-fx-background-color: black; -fx-text-fill: #ff4d4d;"
                + " -fx-border-color: #ff4d4d;";
        String yesHover = base
                + "-fx-background-color: #c92c2c; -fx-text-fill: black;"
                + " -fx-font-weight: bold; -fx-border-color: #ff4d4d;";
        String noNormal = base
                + "-fx-background-color: black; -fx-text-fill: #cdfc04;"
                + " -fx-border-color: #cdfc04;";
        String noHover = base
                + "-fx-background-color: #a4c904; -fx-text-fill: black;"
                + " -fx-font-weight: bold; -fx-border-color: #cdfc04;";

        javafx.scene.Node yesNode = dialogPane.lookupButton(yes);
        javafx.scene.Node noNode = dialogPane.lookupButton(no);

        if (yesNode instanceof javafx.scene.control.Button yesButton) {
            yesButton.setStyle(yesNormal);
            yesButton.setOnMouseEntered(e -> yesButton.setStyle(yesHover));
            yesButton.setOnMouseExited(e -> yesButton.setStyle(yesNormal));
        }
        if (noNode instanceof javafx.scene.control.Button noButton) {
            noButton.setStyle(noNormal);
            noButton.setOnMouseEntered(e -> noButton.setStyle(noHover));
            noButton.setOnMouseExited(e -> noButton.setStyle(noNormal));
        }
    }

    // ── UI refresh helpers ────────────────────────────────────────────────────

    static void refreshOwnedCollectionView() {
        Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
    }

    static void refreshDecksAndCollectionsView() {
        Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
    }

    // ── Scene-graph helper ────────────────────────────────────────────────────

    /**
     * Walks up the scene-graph from {@code node} and returns the first
     * {@link NavigationItem} ancestor, or {@code null} if none is found.
     */
    static NavigationItem findNavigationItemAncestor(javafx.scene.Node node) {
        javafx.scene.Node current = node;
        while (current != null) {
            if (current instanceof NavigationItem) {
                return (NavigationItem) current;
            }
            current = current.getParent();
        }
        return null;
    }
}