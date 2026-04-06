package View;

import Model.CardsLists.Box;
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

            // Determine whether this box is a sub-box (and if so, who is its parent)
            Box parentOfBox = findParentOfBox(box, owned);
            boolean isSubBox = parentOfBox != null;

            // Offer every top-level box (and their sub-boxes) except the box itself
            // and boxes that are descendants of it (to avoid cycles).
            java.util.Set<Box> descendants = collectDescendants(box);

            for (Box topBox : owned.getOwnedCollection()) {
                if (topBox == null || topBox == box) continue;
                if (descendants.contains(topBox)) continue;

                String name = sanitize(topBox.getName());
                if (name.isEmpty()) name = "(Unnamed box)";
                final Box target = topBox;
                MenuItem mi = makeActionItem(name, () -> {
                    doMoveBoxToSubBox(box, target, owned);
                    refreshOwnedCollectionView();
                });
                moveMenu.getItems().add(mi);

                // Also offer sub-boxes of this top-level box as targets
                if (topBox.getSubBoxes() != null) {
                    for (Box sub : topBox.getSubBoxes()) {
                        if (sub == null || sub == box) continue;
                        if (descendants.contains(sub)) continue;
                        String subName = sanitize(sub.getName());
                        if (subName.isEmpty()) subName = "(Unnamed sub-box)";
                        final Box subTarget = sub;
                        MenuItem subMi = makeActionItem(name + " / " + subName, () -> {
                            doMoveBoxToSubBox(box, subTarget, owned);
                            refreshOwnedCollectionView();
                        });
                        moveMenu.getItems().add(subMi);
                    }
                }
            }

            // If it is already a sub-box, offer "No Box" to promote it to top level
            if (isSubBox) {
                if (!moveMenu.getItems().isEmpty()) moveMenu.getItems().add(new SeparatorMenuItem());
                MenuItem noBox = makeActionItem("No Box (top level)", () -> {
                    doMoveBoxToTopLevel(box, owned);
                    refreshOwnedCollectionView();
                });
                moveMenu.getItems().add(noBox);
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
                makeAddBoxInItem(box, owned),
                makeAddBoxAfterItem(box, owned),
                makeAddCategoryItem(box, null, owned),
                makeRenameItem(box, null, owned),
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

                // Box-level destination (append category to box.content)
                final Model.CardsLists.Box destBox = box;
                final String destBoxName = boxName;
                MenuItem miBox = makeActionItem(destBoxName, () -> {
                    doMoveCategory(category, parentBox, destBox, null, owned);
                    refreshOwnedCollectionView();
                });
                moveMenu.getItems().add(miBox);

                // Group-level destinations: insert category after the named group
                if (box.getContent() != null) {
                    for (Model.CardsLists.CardsGroup g : box.getContent()) {
                        if (g == null || g == category) continue;
                        String groupName = sanitize(g.getName());
                        if (groupName.isEmpty()) continue;
                        final Model.CardsLists.CardsGroup afterGroup = g;
                        MenuItem miGroup = makeActionItem(destBoxName + " / " + groupName, () -> {
                            doMoveCategory(category, parentBox, destBox, afterGroup, owned);
                            refreshOwnedCollectionView();
                        });
                        moveMenu.getItems().add(miGroup);
                    }
                }

                // Sub-boxes
                if (box.getSubBoxes() != null) {
                    for (Model.CardsLists.Box sb : box.getSubBoxes()) {
                        if (sb == null) continue;
                        String subName = sanitize(sb.getName());
                        if (subName.isEmpty()) subName = "(Unnamed sub-box)";
                        final Model.CardsLists.Box destSub = sb;
                        final String destSubName = subName;
                        MenuItem miSub = makeActionItem(destSubName, () -> {
                            doMoveCategory(category, parentBox, destSub, null, owned);
                            refreshOwnedCollectionView();
                        });
                        moveMenu.getItems().add(miSub);

                        if (sb.getContent() != null) {
                            for (Model.CardsLists.CardsGroup g : sb.getContent()) {
                                if (g == null || g == category) continue;
                                String groupName = sanitize(g.getName());
                                if (groupName.isEmpty()) continue;
                                final Model.CardsLists.CardsGroup afterGroup = g;
                                MenuItem miSubGroup = makeActionItem(destSubName + " / " + groupName, () -> {
                                    doMoveCategory(category, parentBox, destSub, afterGroup, owned);
                                    refreshOwnedCollectionView();
                                });
                                moveMenu.getItems().add(miSubGroup);
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
                makeAddCategoryItem(parentBox, category, owned),
                makeRenameItem(null, category, owned),
                new SeparatorMenuItem(),
                makeRemoveItem(removeAction)
        );
        return cm;
    }

    public static ContextMenu forMyCollectionEmpty() {
        ContextMenu cm = styledContextMenu();
        cm.getItems().add(makeAddBoxAtTopLevelItem());
        return cm;
    }

    /**
     * "Add Box in" — creates a sub-box inside the clicked box.
     */
    private static MenuItem makeAddBoxInItem(Model.CardsLists.Box parentBox,
                                             Model.CardsLists.OwnedCardsCollection owned) {
        return makeActionItem("Add Box in", () -> {
            Model.CardsLists.Box newBox = new Model.CardsLists.Box("New Box");
            if (parentBox.getSubBoxes() == null) parentBox.setSubBoxes(new java.util.ArrayList<>());
            parentBox.getSubBoxes().add(newBox);
            Controller.UserInterfaceFunctions.setPendingRenameTarget(newBox);
            Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
        });
    }

    /**
     * "Add Box after" — creates a sibling box immediately after the clicked box.
     */
    private static MenuItem makeAddBoxAfterItem(Model.CardsLists.Box referenceBox,
                                                Model.CardsLists.OwnedCardsCollection owned) {
        return makeActionItem("Add Box after", () -> {
            Model.CardsLists.Box newBox = new Model.CardsLists.Box("New Box");
            // Find where referenceBox lives and insert after it
            java.util.List<Model.CardsLists.Box> topLevel = owned.getOwnedCollection();
            int idx = topLevel.indexOf(referenceBox);
            if (idx >= 0) {
                topLevel.add(idx + 1, newBox);
            } else {
                // Try sub-box lists
                boolean inserted = false;
                outer:
                for (Model.CardsLists.Box top : topLevel) {
                    if (top.getSubBoxes() == null) continue;
                    idx = top.getSubBoxes().indexOf(referenceBox);
                    if (idx >= 0) {
                        top.getSubBoxes().add(idx + 1, newBox);
                        inserted = true;
                        break;
                    }
                }
                if (!inserted) topLevel.add(newBox); // fallback
            }
            Controller.UserInterfaceFunctions.setPendingRenameTarget(newBox);
            Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
        });
    }

    /**
     * "Add Category" — creates a new category.
     * If {@code afterCategory} is non-null it is inserted after that category;
     * otherwise it is appended to {@code targetBox}.
     */
    private static MenuItem makeAddCategoryItem(
            Model.CardsLists.Box targetBox,
            Model.CardsLists.CardsGroup afterCategory,
            Model.CardsLists.OwnedCardsCollection owned) {

        return makeActionItem("Add Category", () -> {
            Model.CardsLists.CardsGroup newCat = new Model.CardsLists.CardsGroup("New Category");
            if (targetBox.getContent() == null) targetBox.setContent(new java.util.ArrayList<>());
            if (afterCategory != null) {
                int idx = targetBox.getContent().indexOf(afterCategory);
                if (idx >= 0) {
                    targetBox.getContent().add(idx + 1, newCat);
                } else {
                    targetBox.getContent().add(newCat);
                }
            } else {
                targetBox.getContent().add(newCat);
            }
            Controller.UserInterfaceFunctions.setPendingRenameTarget(newCat);
            Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
        });
    }

    /**
     * "Add Box" from the empty nav-menu background — appends at the top level.
     */
    private static MenuItem makeAddBoxAtTopLevelItem() {
        return makeActionItem("Add Box", () -> {
            Model.CardsLists.OwnedCardsCollection owned = null;
            try {
                owned = Model.CardsLists.OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
            if (owned == null) return;
            Model.CardsLists.Box newBox = new Model.CardsLists.Box("New Box");
            owned.getOwnedCollection().add(newBox);
            Controller.UserInterfaceFunctions.setPendingRenameTarget(newBox);
            Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
        });
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
        Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
    }

    private static void refreshDecksAndCollectionsView() {
        Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
        //Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
    }

    // ── Move helpers ─────────────────────────────────────────────────────────────

    /**
     * Creates a MenuItem with a white-styled label wired to the given action.
     */
    private static MenuItem makeActionItem(String text, Runnable action) {
        MenuItem mi = new MenuItem();
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(graphic);
        mi.setText("");
        mi.setOnAction(e -> {
            if (action != null) action.run();
        });
        return mi;
    }

    /**
     * Moves {@code box} to become a sub-box of {@code targetBox}.
     * The box is first removed from its current location in the collection.
     */
    private static void doMoveBoxToSubBox(
            Model.CardsLists.Box box,
            Model.CardsLists.Box targetBox,
            Model.CardsLists.OwnedCardsCollection owned) {

        if (box == null || targetBox == null || owned == null) return;
        removeBoxFromAnywhere(box, owned);
        if (targetBox.getSubBoxes() == null) targetBox.setSubBoxes(new java.util.ArrayList<>());
        targetBox.getSubBoxes().add(box);
    }

    /**
     * Promotes {@code box} from its current sub-box position to the top level
     * of the collection.
     */
    private static void doMoveBoxToTopLevel(
            Model.CardsLists.Box box,
            Model.CardsLists.OwnedCardsCollection owned) {

        if (box == null || owned == null) return;
        removeBoxFromAnywhere(box, owned);
        owned.getOwnedCollection().add(box);
    }

    /**
     * Removes {@code box} from wherever it currently lives (top-level or sub-box).
     */
    private static void removeBoxFromAnywhere(
            Model.CardsLists.Box box,
            Model.CardsLists.OwnedCardsCollection owned) {

        if (box == null || owned == null || owned.getOwnedCollection() == null) return;
        if (owned.getOwnedCollection().remove(box)) return;
        for (Model.CardsLists.Box top : owned.getOwnedCollection()) {
            if (top == null || top.getSubBoxes() == null) continue;
            if (top.getSubBoxes().remove(box)) return;
        }
    }

    /**
     * Finds the Box that contains {@code box} as a sub-box, or {@code null}
     * if {@code box} is at the top level.
     */
    private static Model.CardsLists.Box findParentOfBox(
            Model.CardsLists.Box box,
            Model.CardsLists.OwnedCardsCollection owned) {

        if (owned == null || owned.getOwnedCollection() == null) return null;
        for (Model.CardsLists.Box top : owned.getOwnedCollection()) {
            if (top == null || top.getSubBoxes() == null) continue;
            for (Model.CardsLists.Box sub : top.getSubBoxes()) {
                if (sub == box) return top;
            }
        }
        return null;
    }

    /**
     * Returns all boxes that are descendants of {@code root} (to prevent
     * moving a box into one of its own descendants, which would create a cycle).
     */
    private static java.util.Set<Model.CardsLists.Box> collectDescendants(
            Model.CardsLists.Box root) {

        java.util.Set<Model.CardsLists.Box> result = new java.util.HashSet<>();
        if (root == null || root.getSubBoxes() == null) return result;
        java.util.Deque<Model.CardsLists.Box> stack = new java.util.ArrayDeque<>(root.getSubBoxes());
        while (!stack.isEmpty()) {
            Model.CardsLists.Box cur = stack.pop();
            if (cur == null || !result.add(cur)) continue;
            if (cur.getSubBoxes() != null) stack.addAll(cur.getSubBoxes());
        }
        return result;
    }

    /**
     * Moves {@code category} from {@code fromBox} to {@code toBox}.
     * <p>
     * If {@code insertAfter} is non-null, the category is inserted immediately
     * after that group in {@code toBox}'s content list; otherwise it is appended.
     */
    private static void doMoveCategory(
            Model.CardsLists.CardsGroup category,
            Model.CardsLists.Box fromBox,
            Model.CardsLists.Box toBox,
            Model.CardsLists.CardsGroup insertAfter,
            Model.CardsLists.OwnedCardsCollection owned) {

        if (category == null || toBox == null) return;

        // Remove from source
        if (fromBox != null && fromBox.getContent() != null) {
            fromBox.getContent().remove(category);
        } else {
            // Fallback: search the whole collection
            if (owned != null && owned.getOwnedCollection() != null) {
                outer:
                for (Model.CardsLists.Box b : owned.getOwnedCollection()) {
                    if (b == null) continue;
                    for (Model.CardsLists.Box candidate : new java.util.ArrayList<>(
                            b.getSubBoxes() == null ? java.util.Collections.emptyList() : b.getSubBoxes())) {
                        if (candidate != null && candidate.getContent() != null
                                && candidate.getContent().remove(category)) break outer;
                    }
                    if (b.getContent() != null && b.getContent().remove(category)) break;
                }
            }
        }

        // Ensure target has a content list
        if (toBox.getContent() == null) toBox.setContent(new java.util.ArrayList<>());

        // Insert after the reference group, or append
        if (insertAfter != null) {
            int idx = toBox.getContent().indexOf(insertAfter);
            if (idx >= 0 && idx + 1 <= toBox.getContent().size()) {
                toBox.getContent().add(idx + 1, category);
                return;
            }
        }
        toBox.getContent().add(category);
    }

    /**
     * Builds a "Rename" MenuItem that stores the target model object.
     * The actual inline-editor is launched on the NavigationItem that owns the
     * context menu, found by walking the scene from the menu's anchor.
     * <p>
     * Pass non-null {@code box} for a Box rename, non-null {@code category} for a Category rename.
     */
    private static MenuItem makeRenameItem(
            Model.CardsLists.Box box,
            Model.CardsLists.CardsGroup category,
            Model.CardsLists.OwnedCardsCollection owned) {

        MenuItem mi = new MenuItem();
        Label lbl = new Label("Rename");
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox g = new HBox(lbl);
        g.setAlignment(Pos.CENTER_LEFT);
        g.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(g);
        mi.setText("");

        mi.setOnAction(e -> {
            // Find the NavigationItem whose label was right-clicked.
            // The ContextMenu remembers the node it was shown on via getOwnerNode().
            javafx.scene.Node owner = null;
            try {
                owner = mi.getParentPopup().getOwnerNode();
            } catch (Throwable ignored) {
            }

            // Walk up from the owner node to find the NavigationItem ancestor.
            View.NavigationItem navItem = findNavigationItemAncestor(owner);
            if (navItem == null) return;

            navItem.startInlineRename(
                    newName -> {
                        // Update the model
                        if (box != null) {
                            // Preserve decoration characters (=) so the file format stays intact.
                            // Simply replace the visible part between the leading/trailing = signs.
                            String raw = box.getName() == null ? "" : box.getName();
                            String updated = rebuildDecoratedName(raw, newName, '=');
                            box.setName(updated);
                            navItem.getLabel().setText(newName);
                        } else if (category != null) {
                            String raw = category.getName() == null ? "" : category.getName();
                            String updated = rebuildDecoratedName(raw, newName, '-');
                            category.setName(updated);
                            navItem.getLabel().setText(newName);
                        }
                        // Refresh both views
                        Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
                    },
                    null  // cancel: nothing to do
            );
        });

        return mi;
    }

    /**
     * Rebuilds a decorated name like "===OldName======" or "---OldName----"
     * by replacing the non-decoration portion with {@code newDisplayName}.
     * If the raw name contains no decoration characters the new display name
     * is returned as-is.
     */
    private static String rebuildDecoratedName(String raw, String newDisplayName, char decorator) {
        if (raw == null || raw.isEmpty()) return newDisplayName;
        // Count leading decoration characters
        int leading = 0;
        while (leading < raw.length() && raw.charAt(leading) == decorator) leading++;
        int trailing = 0;
        while (trailing < raw.length() && raw.charAt(raw.length() - 1 - trailing) == decorator) trailing++;
        if (leading == 0 && trailing == 0) return newDisplayName;
        String prefix = raw.substring(0, leading);
        String suffix = raw.substring(raw.length() - trailing);
        return prefix + newDisplayName + suffix;
    }

    /**
     * Walks up the scene-graph from {@code node} and returns the first
     * {@link View.NavigationItem} ancestor, or {@code null} if none is found.
     */
    private static View.NavigationItem findNavigationItemAncestor(javafx.scene.Node node) {
        javafx.scene.Node cur = node;
        while (cur != null) {
            if (cur instanceof View.NavigationItem) return (View.NavigationItem) cur;
            cur = cur.getParent();
        }
        return null;
    }
}