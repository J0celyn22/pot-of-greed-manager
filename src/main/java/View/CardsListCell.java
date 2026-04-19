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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
        setupDropTarget();
        setupSelectionHandler();
        myCollContextMenu = buildContextMenuForMyCollection();
        decksContextMenu = buildContextMenuForDecks();
    }

    private static MenuItem makeDecksCollectionItemForCards(
            String collName, java.util.Collection<Model.CardsLists.Card> cards) {
        MenuItem mi = new MenuItem(collName);
        mi.setOnAction(e -> {
            if (cards.size() == 1) {
                Controller.MenuActionHandler.handleAddToCollectionCards(
                        cards.iterator().next(), collName);
            } else {
                Controller.MenuActionHandler.handleBulkAddToCollectionCards(cards, collName);
            }
        });
        return mi;
    }


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
        lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
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

        // Add to…
        Menu addToMenu = makeMenuHeader("Add to...");
        addToMenu.getItems().add(loadingPlaceholder());
        addToMenu.setOnShowing(evt -> {
            addToMenu.getItems().clear();
            Card clickedCard = getItem();
            java.util.Collection<Model.CardsLists.Card> cardsToAdd =
                    getEffectiveRightPaneCards(clickedCard);
            List<MenuItem> items = buildAllDecksDestinationItemsForCards(cardsToAdd);
            if (items.isEmpty()) {
                MenuItem none = new MenuItem("No destinations available");
                none.setDisable(true);
                addToMenu.getItems().add(none);
            } else {
                addToMenu.getItems().addAll(items);
            }
        });
        cm.getItems().add(addToMenu);

        // Copy
        MenuItem copyMenuItem = new MenuItem();
        {
            Label lbl = new Label("Copy");
            lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
            HBox g = new HBox(lbl);
            g.setAlignment(Pos.CENTER_LEFT);
            g.setPadding(new javafx.geometry.Insets(2, 6, 2, 6));
            copyMenuItem.setGraphic(g);
            copyMenuItem.setText("");
            copyMenuItem.setOnAction(e -> {
                Card clickedCard = getItem();
                if (clickedCard == null) return;
                Controller.CardClipboard.copyCards(
                        new java.util.ArrayList<>(getEffectiveRightPaneCards(clickedCard)));
            });
        }
        cm.getItems().add(copyMenuItem);

        return cm;
    }

    private static MenuItem makeDecksExclusionItemForCards(
            String collName, java.util.Collection<Model.CardsLists.Card> cards) {
        MenuItem mi = new MenuItem(collName + " / Exclusion List");
        mi.setOnAction(e -> {
            if (cards.size() == 1) {
                Controller.MenuActionHandler.handleAddToExclusionList(
                        cards.iterator().next(), collName);
            } else {
                Controller.MenuActionHandler.handleBulkAddToExclusionList(cards, collName);
            }
        });
        return mi;
    }

    private static MenuItem makeDecksDestItemForCards(
            String path, java.util.Collection<Model.CardsLists.Card> cards) {
        MenuItem mi = new MenuItem(path);
        mi.setOnAction(e -> {
            if (cards.size() == 1) {
                Controller.MenuActionHandler.handleAddToDeck(cards.iterator().next(), path);
            } else {
                Controller.MenuActionHandler.handleBulkAddToDeck(cards, path);
            }
        });
        return mi;
    }

    private static MenuItem makeMyCollDestItemForCards(
            String path, java.util.Collection<Model.CardsLists.Card> cards) {
        MenuItem mi = new MenuItem(path);
        mi.setOnAction(e -> {
            if (cards.size() == 1) {
                Controller.MenuActionHandler.handleAddCopy(cards.iterator().next(), path);
            } else {
                Controller.MenuActionHandler.handleBulkAddCopy(cards, path);
            }
        });
        return mi;
    }

    /**
     * Makes this list cell a drop target.
     * RIGHT → RIGHT: ignore (nothing happens).
     * MIDDLE → RIGHT: remove the dragged CardElements from the middle pane.
     */
    private void setupDropTarget() {
        this.setOnDragOver(event -> {
            if ("MIDDLE".equals(Controller.DragDropManager.getDragSourcePane())
                    && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        this.setOnDragDropped(event -> {
            if (!"MIDDLE".equals(Controller.DragDropManager.getDragSourcePane())) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            java.util.List<Model.CardsLists.CardElement> elements =
                    new java.util.ArrayList<>(Controller.DragDropManager.getDraggedElements());
            if (!elements.isEmpty()) {
                // Try D&C removal first; if nothing removed, fall back to OwnedCollection removal
                Controller.MenuActionHandler.handleBulkRemoveElementsFromDecksAndCollections(elements);
                Controller.MenuActionHandler.handleBulkRemoveFromOwnedCollection(elements);
            }
            event.setDropCompleted(true);
            event.consume();
        });
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

    private List<MenuItem> buildAllDecksDestinationItemsForCards(
            java.util.Collection<Model.CardsLists.Card> cards) {
        java.util.List<MenuItem> items = new java.util.ArrayList<>();
        if (cards == null || cards.isEmpty()) return items;
        try {
            Model.CardsLists.DecksAndCollectionsList dac =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (dac == null) {
                try {
                    Controller.UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
                } catch (Exception ignored) {
                }
                dac = Controller.UserInterfaceFunctions.getDecksList();
            }
            if (dac == null) return items;

            final java.util.Collection<Model.CardsLists.Card> finalCards = cards;

            if (dac.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                    if (tc == null) continue;
                    String coll = sanitize(tc.getName());
                    items.add(makeDecksCollectionItemForCards(coll, finalCards));
                    if (tc.getLinkedDecks() != null) {
                        for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Model.CardsLists.Deck deck : unit) {
                                if (deck == null) continue;
                                String base = coll + " / " + sanitize(deck.getName());
                                items.add(makeDecksDestItemForCards(base + " / Main Deck", finalCards));
                                items.add(makeDecksDestItemForCards(base + " / Extra Deck", finalCards));
                                items.add(makeDecksDestItemForCards(base + " / Side Deck", finalCards));
                            }
                        }
                    }
                    items.add(makeDecksExclusionItemForCards(coll, finalCards));
                }
            }
            if (dac.getDecks() != null) {
                for (Model.CardsLists.Deck deck : dac.getDecks()) {
                    if (deck == null) continue;
                    String d = sanitize(deck.getName());
                    items.add(makeDecksDestItemForCards(d + " / Main Deck", finalCards));
                    items.add(makeDecksDestItemForCards(d + " / Extra Deck", finalCards));
                    items.add(makeDecksDestItemForCards(d + " / Side Deck", finalCards));
                }
            }
        } catch (Exception ex) {
            logger.error("buildAllDecksDestinationItemsForCards failed", ex);
        }
        return items;
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

    private void setupDragAndDrop() {
        this.setOnDragDetected(event -> {
            Card card = getItem();
            if (card == null) return;

            // Primary card first, then other selected RIGHT-pane cards in selection order,
            // capped at 5 total. Only expand to multi-card if this card is part of a
            // RIGHT-pane multi-selection.
            java.util.Set<Card> selected = SelectionManager.getSelectedCards();
            List<Card> dragCards = new ArrayList<>();
            dragCards.add(card);
            if ("RIGHT".equals(SelectionManager.getActivePart())
                    && selected.size() > 1 && selected.contains(card)) {
                for (Card c : selected) {
                    if (c != card) {
                        dragCards.add(c);
                        if (dragCards.size() >= 5) break;
                    }
                }
            }

            // Resolve raw images (no selection border)
            List<Image> ghostImages = new ArrayList<>();
            for (Card c : dragCards) {
                String path = getImagePath(c);
                Image img = path != null ? LruImageCache.getImage(path) : null;
                if (img == null && path != null) {
                    try {
                        img = new Image(path, imageWidth, imageHeight, true, true);
                    } catch (Exception ignored) {
                    }
                }
                ghostImages.add(img);
            }

            Dragboard db = startDragAndDrop(TransferMode.MOVE);
            WritableImage ghost = DragDropManager.buildDragGhost(ghostImages, imageWidth, imageHeight);
            if (ghost != null) {
                // Anchor at centre of the frontmost (dragged) card
                db.setDragView(ghost, imageWidth / 2.0, imageHeight / 2.0);
            }

            ClipboardContent content = new ClipboardContent();
            content.putString(card.getPassCode());
            db.setContent(content);
            DragDropManager.startRightDrag(dragCards);
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

        // Add to…
        Menu addToMenu = makeMenuHeader("Add to...");
        addToMenu.getItems().add(loadingPlaceholder());
        addToMenu.setOnShowing(evt -> {
            addToMenu.getItems().clear();
            Card clickedCard = getItem();
            java.util.Collection<Model.CardsLists.Card> cardsToAdd =
                    getEffectiveRightPaneCards(clickedCard);
            List<MenuItem> items = buildMyCollectionDestinationItemsForCards(cardsToAdd);
            if (items.isEmpty()) {
                MenuItem none = new MenuItem("No destinations available");
                none.setDisable(true);
                addToMenu.getItems().add(none);
            } else {
                addToMenu.getItems().addAll(items);
            }
        });
        cm.getItems().add(addToMenu);

        // Copy
        MenuItem copyMenuItem = new MenuItem();
        {
            Label lbl = new Label("Copy");
            lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
            HBox g = new HBox(lbl);
            g.setAlignment(Pos.CENTER_LEFT);
            g.setPadding(new javafx.geometry.Insets(2, 6, 2, 6));
            copyMenuItem.setGraphic(g);
            copyMenuItem.setText("");
            copyMenuItem.setOnAction(e -> {
                Card clickedCard = getItem();
                if (clickedCard == null) return;
                Controller.CardClipboard.copyCards(
                        new java.util.ArrayList<>(getEffectiveRightPaneCards(clickedCard)));
            });
        }
        cm.getItems().add(copyMenuItem);

        return cm;
    }

    private void setupSelectionHandler() {
        this.setOnMouseClicked(event -> {
            // Let right-clicks fall through to the context menu
            if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                event.consume(); // Prevent the empty-space handler from clearing the selection
                return;
            }
            Card card = getItem();
            if (card == null) return;

            if (event.isControlDown()) {
                SelectionManager.toggleSelection(card, "RIGHT");
            } else if (event.isShiftDown()) {
                // Build an ordered list from this ListView for range selection
                java.util.List<Card> orderedCardsInList = new java.util.ArrayList<>();
                if (getListView() != null && getListView().getItems() != null) {
                    orderedCardsInList.addAll(getListView().getItems());
                }
                SelectionManager.rangeSelect(card, "RIGHT", orderedCardsInList);
            } else {
                SelectionManager.selectCard(card, "RIGHT");
            }

            if (getListView() != null) {
                getListView().refresh();
            }
            event.consume();
        });
    }

    private List<MenuItem> buildMyCollectionDestinationItemsForCards(
            java.util.Collection<Model.CardsLists.Card> cards) {
        java.util.List<MenuItem> items = new java.util.ArrayList<>();
        if (cards == null || cards.isEmpty()) return items;
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

            final java.util.Collection<Model.CardsLists.Card> finalCards = cards;
            for (Model.CardsLists.Box box : owned.getOwnedCollection()) {
                if (box == null) continue;
                String boxName = sanitize(box.getName());
                if (boxName.isEmpty()) boxName = "(Unnamed box)";
                final String finalBoxName = boxName;
                items.add(makeMyCollDestItemForCards(finalBoxName, finalCards));
                if (box.getContent() != null) {
                    for (Model.CardsLists.CardsGroup g : box.getContent()) {
                        if (g == null) continue;
                        String groupName = sanitize(g.getName());
                        if (groupName.isEmpty()) continue;
                        final String path = finalBoxName + " / " + groupName;
                        items.add(makeMyCollDestItemForCards(path, finalCards));
                    }
                }
                if (box.getSubBoxes() != null) {
                    for (Model.CardsLists.Box subBox : box.getSubBoxes()) {
                        if (subBox == null) continue;
                        String subName = sanitize(subBox.getName());
                        if (subName.isEmpty()) subName = "(Unnamed sub-box)";
                        final String finalSubName = subName;
                        items.add(makeMyCollDestItemForCards(finalSubName, finalCards));
                        if (subBox.getContent() != null) {
                            for (Model.CardsLists.CardsGroup g : subBox.getContent()) {
                                if (g == null) continue;
                                String groupName = sanitize(g.getName());
                                if (groupName.isEmpty()) continue;
                                final String path = finalSubName + " / " + groupName;
                                items.add(makeMyCollDestItemForCards(path, finalCards));
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("buildMyCollectionDestinationItemsForCards failed", ex);
        }
        return items;
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

    /**
     * Returns either the full RIGHT-pane selection (when multi-select is active
     * and the clicked card is part of it) or a singleton containing only the
     * clicked card. The right pane is read-only so this is used only for
     * Add-type commands and Copy.
     */
    private java.util.Collection<Model.CardsLists.Card> getEffectiveRightPaneCards(Card clickedCard) {
        if (clickedCard == null) return java.util.Collections.emptyList();
        boolean isRightMultiSelect =
                "RIGHT".equals(Controller.SelectionManager.getActivePart())
                        && Controller.SelectionManager.getSelectedCards().size() > 1
                        && Controller.SelectionManager.getSelectedCards().contains(clickedCard);
        return isRightMultiSelect
                ? Controller.SelectionManager.getSelectedCards()
                : java.util.Collections.singletonList(clickedCard);
    }
}