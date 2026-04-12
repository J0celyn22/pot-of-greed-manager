package View;

import Controller.DragDropManager;
import Controller.SelectionManager;
import Controller.UserInterfaceFunctions;
import Model.CardsLists.Card;
import Model.CardsLists.DecksAndCollectionsList;
import Model.Database.DataBaseUpdate;
import Utils.LruImageCache;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class CardsListCell extends ListCell<Card> {

    private static final Logger logger = LoggerFactory.getLogger(CardsListCell.class);
    private boolean printedMode;
    private double imageWidth;
    private double imageHeight;

    private final ContextMenu myCollContextMenu;
    private final ContextMenu decksContextMenu;

    public CardsListCell(boolean printedMode, double imageWidth, double imageHeight) {
        this.printedMode = printedMode;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        setFocusTraversable(true);
        setupDragAndDrop();
        setupSelectionHandler();
        myCollContextMenu = buildContextMenuForMyCollection();
        decksContextMenu = buildContextMenuForDecks();
    }

    // ── Destination lists ─────────────────────────────────────────────────────

    private static MenuItem makeMyCollDestItem(String path, Card card) {
        MenuItem mi = new MenuItem(path);
        mi.setOnAction(e -> {
            logger.debug("makeMyCollDestItem action fired: path='{}', card='{}'", path, card == null ? "null" : card.getName_EN());
            Controller.MenuActionHandler.handleAddCopy(card, path);
        });
        return mi;
    }

    private static MenuItem makeDestItem(String path) {
        MenuItem mi = new MenuItem(path);
        mi.setOnAction(e -> { /* TODO: implement Add to: path */ });
        return mi;
    }

    // ── Small factories ───────────────────────────────────────────────────────

    private static ContextMenu styledContextMenu() {
        ContextMenu cm = new ContextMenu();
        cm.setStyle("-fx-background-color: #100317; -fx-background-radius: 6; " +
                "-fx-border-color: #3a3a3a; -fx-border-radius: 6; -fx-border-width: 1;");
        return cm;
    }

    private static Menu makeMenuHeader(String text) {
        Menu m = new Menu();
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox g = new HBox(lbl);
        g.setAlignment(Pos.CENTER_LEFT);
        g.setPadding(new Insets(2, 6, 2, 6));
        m.setGraphic(g);
        m.setText("");
        return m;
    }

    private static MenuItem loadingPlaceholder() {
        MenuItem ph = new MenuItem("Loading...");
        ph.setDisable(true);
        return ph;
    }

    /**
     * Builds the flat list of all possible destinations in the current
     * DecksAndCollectionsList (no exclusion — AllExistingCards cards have no
     * "current location" in the editable structure).
     * <p>
     * Format:
     * CollectionName
     * CollectionName / DeckName / Main Deck
     * CollectionName / DeckName / Extra Deck
     * CollectionName / DeckName / Side Deck
     * CollectionName / Exclusion List
     * StandaloneDeckName / Main Deck
     * StandaloneDeckName / Extra Deck
     * StandaloneDeckName / Side Deck
     */
    private static List<MenuItem> buildAllDestinationItems() {
        java.util.List<MenuItem> items = new java.util.ArrayList<>();
        try {
            DecksAndCollectionsList dac = UserInterfaceFunctions.getDecksList();
            if (dac == null) {
                try {
                    UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
                } catch (Exception ignored) {
                }
                dac = UserInterfaceFunctions.getDecksList();
            }
            if (dac == null) return items;

            if (dac.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                    if (tc == null) continue;
                    String coll = sanitize(tc.getName());

                    // Collection cards slot
                    items.add(makeDestItem(coll));

                    // Linked decks
                    if (tc.getLinkedDecks() != null) {
                        for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Model.CardsLists.Deck deck : unit) {
                                if (deck == null) continue;
                                String base = coll + " / " + sanitize(deck.getName());
                                items.add(makeDestItem(base + " / Main Deck"));
                                items.add(makeDestItem(base + " / Extra Deck"));
                                items.add(makeDestItem(base + " / Side Deck"));
                            }
                        }
                    }

                    // Exclusion list
                    items.add(makeDestItem(coll + " / Exclusion List"));
                }
            }

            if (dac.getDecks() != null) {
                for (Model.CardsLists.Deck deck : dac.getDecks()) {
                    if (deck == null) continue;
                    String d = sanitize(deck.getName());
                    items.add(makeDestItem(d + " / Main Deck"));
                    items.add(makeDestItem(d + " / Extra Deck"));
                    items.add(makeDestItem(d + " / Side Deck"));
                }
            }
        } catch (Exception ex) {
            logger.error("buildAllDestinationItems failed", ex);
        }
        return items;
    }

    // ── Replace the existing static buildContextMenuForDecks() with this instance method ──

    private static MenuItem makeDecksCollectionItem(String collName, Card card) {
        MenuItem mi = new MenuItem(collName);
        mi.setOnAction(e -> Controller.MenuActionHandler.handleAddToCollectionCards(card, collName));
        return mi;
    }

    private static MenuItem makeDecksExclusionItem(String collName, Card card) {
        MenuItem mi = new MenuItem(collName + " / Exclusion List");
        mi.setOnAction(e -> Controller.MenuActionHandler.handleAddToExclusionList(card, collName));
        return mi;
    }

    private static MenuItem makeDecksDestItem(String path, Card card) {
        MenuItem mi = new MenuItem(path);
        mi.setOnAction(e -> Controller.MenuActionHandler.handleAddToDeck(card, path));
        return mi;
    }

    private ContextMenu buildContextMenuForDecks() {
        ContextMenu cm = styledContextMenu();
        Menu addToMenu = makeMenuHeader("Add to...");
        addToMenu.getItems().add(loadingPlaceholder());
        addToMenu.setOnShowing(evt -> {
            addToMenu.getItems().clear();
            List<MenuItem> items = buildAllDecksDestinationItems(getItem());
            if (items.isEmpty()) {
                MenuItem none = new MenuItem("No destinations available");
                none.setDisable(true);
                addToMenu.getItems().add(none);
            } else {
                addToMenu.getItems().addAll(items);
            }
        });
        cm.getItems().add(addToMenu);
        return cm;
    }

    /**
     * Builds the flat list of all Main / Extra / Side Deck slots from the
     * current DecksAndCollectionsList, wired to handleAddToDeck(card, path).
     */
    private List<MenuItem> buildAllDecksDestinationItems(Card card) {
        java.util.List<MenuItem> items = new java.util.ArrayList<>();
        if (card == null) return items;
        try {
            Model.CardsLists.DecksAndCollectionsList dac = Controller.UserInterfaceFunctions.getDecksList();
            if (dac == null) {
                try {
                    Controller.UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
                } catch (Exception ignored) {
                }
                dac = Controller.UserInterfaceFunctions.getDecksList();
            }
            if (dac == null) return items;

            if (dac.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                    if (tc == null) continue;
                    String coll = sanitize(tc.getName());

                    // Collection card-list slot
                    items.add(makeDecksCollectionItem(coll, card));

                    if (tc.getLinkedDecks() != null) {
                        for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Model.CardsLists.Deck deck : unit) {
                                if (deck == null) continue;
                                String base = coll + " / " + sanitize(deck.getName());
                                items.add(makeDecksDestItem(base + " / Main Deck", card));
                                items.add(makeDecksDestItem(base + " / Extra Deck", card));
                                items.add(makeDecksDestItem(base + " / Side Deck", card));
                            }
                        }
                    }

                    // Exclusion list slot
                    items.add(makeDecksExclusionItem(coll, card));
                }
            }

            if (dac.getDecks() != null) {
                for (Model.CardsLists.Deck deck : dac.getDecks()) {
                    if (deck == null) continue;
                    String d = sanitize(deck.getName());
                    items.add(makeDecksDestItem(d + " / Main Deck", card));
                    items.add(makeDecksDestItem(d + " / Extra Deck", card));
                    items.add(makeDecksDestItem(d + " / Side Deck", card));
                }
            }
        } catch (Exception ex) {
            logger.error("buildAllDecksDestinationItems failed", ex);
        }
        return items;
    }

    /*private static String sanitize(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[=\\-]", "").trim();
    }*/
    private static String sanitize(String raw) {
        if (raw == null) return "";
        // Strip only leading/trailing decorator characters (= for boxes, - for categories),
        // preserving hyphens that are genuinely part of the name.
        String s = raw.trim();
        // Strip leading = or -
        int start = 0;
        while (start < s.length() && (s.charAt(start) == '=' || s.charAt(start) == '-')) start++;
        // Strip trailing = or -
        int end = s.length();
        while (end > start && (s.charAt(end - 1) == '=' || s.charAt(end - 1) == '-')) end--;
        return s.substring(start, end).trim();
    }

    private static javafx.scene.control.TabPane findTabPane(javafx.scene.Parent parent) {
        if (parent == null) return null;
        if (parent instanceof javafx.scene.control.TabPane)
            return (javafx.scene.control.TabPane) parent;
        for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof javafx.scene.control.TabPane)
                return (javafx.scene.control.TabPane) child;
            if (child instanceof javafx.scene.Parent) {
                javafx.scene.control.TabPane found = findTabPane((javafx.scene.Parent) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String stripDirtyPrefix(String tabText) {
        if (tabText == null) return "";
        String trimmed = tabText.trim();
        return trimmed.startsWith("* ") ? trimmed.substring(2).trim() : trimmed;
    }

    private String getCurrentTabName() {
        try {
            javafx.scene.Node node = getListView();
            while (node != null && !(node instanceof javafx.scene.control.TabPane))
                node = node.getParent();
            if (node instanceof javafx.scene.control.TabPane) {
                javafx.scene.control.Tab sel =
                        ((javafx.scene.control.TabPane) node).getSelectionModel().getSelectedItem();
                if (sel != null && sel.getText() != null)
                    return stripDirtyPrefix(sel.getText());
            }
            if (getListView() != null && getListView().getScene() != null) {
                javafx.scene.control.TabPane tp = findTabPane(getListView().getScene().getRoot());
                if (tp != null) {
                    javafx.scene.control.Tab sel = tp.getSelectionModel().getSelectedItem();
                    if (sel != null && sel.getText() != null)
                        return stripDirtyPrefix(sel.getText());
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Existing methods — unchanged
    // ─────────────────────────────────────────────────────────────────────────

    private void setupDragAndDrop() {
        this.setOnDragDetected(event -> {
            Card card = getItem();
            if (card == null) return;
            Dragboard db = startDragAndDrop(TransferMode.MOVE);
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            WritableImage ghostImage = null;
            if (getGraphic() instanceof HBox) {
                HBox cellBox = (HBox) getGraphic();
                if (!cellBox.getChildren().isEmpty() && cellBox.getChildren().get(0) instanceof ImageView) {
                    ImageView iv = (ImageView) cellBox.getChildren().get(0);
                    ghostImage = iv.snapshot(params, null);
                }
            }
            if (ghostImage != null) {
                db.setDragView(ghostImage, ghostImage.getWidth() / 2, ghostImage.getHeight() / 2);
            }
            ClipboardContent content = new ClipboardContent();
            content.putString(card.getPassCode());
            db.setContent(content);
            DragDropManager.setCurrentlyDraggedCard(card);
            event.consume();
        });

        this.setOnDragDone(event -> {
            DragDropManager.clearCurrentlyDraggedCard();
            event.consume();
        });

        this.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                DragDropManager.clearCurrentlyDraggedCard();
                event.consume();
            }
        });
    }

    /**
     * Lists every Box and Category in the owned collection (no exclusions).
     */
    private List<MenuItem> buildMyCollectionDestinationItems(Card card) {
        java.util.List<MenuItem> items = new java.util.ArrayList<>();
        if (card == null) return items;
        try {
            Model.CardsLists.OwnedCardsCollection owned =
                    Model.CardsLists.OuicheList.getMyCardsCollection();
            if (owned == null) {
                try {
                    UserInterfaceFunctions.loadCollectionFile();
                } catch (Exception ignored) {
                }
                owned = Model.CardsLists.OuicheList.getMyCardsCollection();
            }
            if (owned == null || owned.getOwnedCollection() == null) return items;

            for (Model.CardsLists.Box box : owned.getOwnedCollection()) {
                if (box == null) continue;
                String boxName = sanitize(box.getName());
                if (boxName.isEmpty()) boxName = "(Unnamed box)";
                items.add(makeMyCollDestItem(boxName, card));

                if (box.getContent() != null) {
                    for (Model.CardsLists.CardsGroup g : box.getContent()) {
                        if (g == null) continue;
                        String groupName = sanitize(g.getName());
                        if (groupName.isEmpty()) continue;
                        items.add(makeMyCollDestItem(boxName + " / " + groupName, card));
                    }
                }
                if (box.getSubBoxes() != null) {
                    for (Model.CardsLists.Box sb : box.getSubBoxes()) {
                        if (sb == null) continue;
                        String subName = sanitize(sb.getName());
                        if (subName.isEmpty()) subName = "(Unnamed sub-box)";
                        items.add(makeMyCollDestItem(boxName + " / " + subName, card));
                        if (sb.getContent() != null) {
                            for (Model.CardsLists.CardsGroup g : sb.getContent()) {
                                if (g == null) continue;
                                String groupName = sanitize(g.getName());
                                if (groupName.isEmpty()) continue;
                                items.add(makeMyCollDestItem(
                                        boxName + " / " + subName + " / " + groupName, card));
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("buildMyCollectionDestinationItems failed", ex);
        }
        return items;
    }

    private void setupSelectionHandler() {
        this.setOnMouseClicked(event -> {
            // Only handle left-clicks; let right-clicks propagate so context menu fires
            if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                return;
            }
            Card card = getItem();
            if (card == null) return;
            String currentPart = "RIGHT";
            if (SelectionManager.getActivePart() == null || !SelectionManager.getActivePart().equals(currentPart)) {
                SelectionManager.clearSelection();
            }
            SelectionManager.selectCard(card, currentPart);
            if (getListView() != null) {
                getListView().refresh();
            }
            event.consume();
        });
    }

    @Override
    protected void updateItem(Card card, boolean empty) {
        super.updateItem(card, empty);
        if (empty || card == null) {
            setGraphic(null);
        } else {
            HBox cell = new HBox(10);
            cell.setPadding(new Insets(5));
            String defaultStyle = "-fx-border-color: white; -fx-border-width: 1; -fx-border-radius: 5; "
                    + "-fx-background-radius: 5; -fx-background-color: transparent;";
            if ("RIGHT".equals(SelectionManager.getActivePart()) &&
                    SelectionManager.getSelectedCards().contains(card)) {
                defaultStyle = "-fx-border-color: #cdfc04; -fx-border-width: 2; -fx-border-radius: 5; "
                        + "-fx-background-radius: 5; -fx-background-color: transparent;";
            }
            cell.setStyle(defaultStyle);

            String fullUrl = getImagePath(card);
            logger.debug("CardsListCell: Using image URL: " + fullUrl);
            Image image = null;
            if (fullUrl != null) {
                image = LruImageCache.getImage(fullUrl);
                if (image == null) {
                    try {
                        image = new Image(fullUrl, imageWidth, imageHeight, true, true);
                        logger.debug("CardsListCell: Loaded image: " + image.getUrl());
                    } catch (Exception e) {
                        logger.error("CardsListCell: Error loading image for " + fullUrl, e);
                        image = new Image("file:./src/main/resources/placeholder.jpg", imageWidth, imageHeight, true, true);
                    }
                }
            } else {
                image = new Image("file:./src/main/resources/placeholder.jpg", imageWidth, imageHeight, true, true);
            }
            ImageView iv = new ImageView(image);
            iv.setFitWidth(imageWidth);
            iv.setFitHeight(imageHeight);
            iv.setPreserveRatio(true);

            VBox info = new VBox(5);
            Label nameLabel = new Label(card.getName_EN());
            String nameStyle = "-fx-text-fill: white; -fx-font-size: 14;";
            String codeStyle = "-fx-text-fill: white; -fx-font-size: 12;";
            if ("RIGHT".equals(SelectionManager.getActivePart()) &&
                    SelectionManager.getSelectedCards().contains(card)) {
                nameStyle += " -fx-font-weight: bold; -fx-text-fill: #cdfc04;";
                codeStyle += " -fx-font-weight: bold; -fx-text-fill: #cdfc04;";
            }
            nameLabel.setStyle(nameStyle);
            info.getChildren().add(nameLabel);
            if (printedMode) {
                Label nativeLabel = new Label(card.getName_EN());
                String nativeStyle = "-fx-text-fill: white; -fx-font-size: 12;";
                if ("RIGHT".equals(SelectionManager.getActivePart()) &&
                        SelectionManager.getSelectedCards().contains(card)) {
                    nativeStyle += " -fx-font-weight: bold; -fx-text-fill: #cdfc04;";
                }
                nativeLabel.setStyle(nativeStyle);
                info.getChildren().add(nativeLabel);
            }
            Label codeLabel = new Label(printedMode ? card.getPrintCode() : card.getPassCode());
            codeLabel.setStyle(codeStyle);
            info.getChildren().add(codeLabel);

            cell.getChildren().addAll(iv, info);
            setGraphic(cell);
            setStyle("-fx-background-color: transparent;");
            String tab = getCurrentTabName();
            if (tab.equalsIgnoreCase("My Collection")) {
                setContextMenu(myCollContextMenu);
            } else if (tab.equalsIgnoreCase("Decks and Collections")
                    || tab.equalsIgnoreCase("Decks & Collections")) {
                setContextMenu(decksContextMenu);
            } else {
                setContextMenu(null);
            }
        }
    }

    private ContextMenu buildContextMenuForMyCollection() {
        ContextMenu cm = styledContextMenu();
        Menu addToMenu = makeMenuHeader("Add to...");
        addToMenu.getItems().add(loadingPlaceholder());
        addToMenu.setOnShowing(evt -> {
            addToMenu.getItems().clear();
            List<MenuItem> items = buildMyCollectionDestinationItems(getItem());
            if (items.isEmpty()) {
                MenuItem none = new MenuItem("No destinations available");
                none.setDisable(true);
                addToMenu.getItems().add(none);
            } else {
                addToMenu.getItems().addAll(items);
            }
        });
        cm.getItems().add(addToMenu);
        return cm;
    }

    private String getImagePath(Card card) {
        if (card == null || card.getImagePath() == null) return null;
        String imageKey = card.getImagePath();
        String[] addresses = DataBaseUpdate.getAddresses(imageKey + ".jpg");
        if (addresses != null && addresses.length > 0) {
            return "file:" + addresses[0];
        }
        return null;
    }
}