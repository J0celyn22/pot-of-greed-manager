package View;

import Controller.DragDropManager;
import Controller.SelectionManager;
import Controller.UserInterfaceFunctions;
import Model.CardsLists.Card;
import Utils.LruImageCache;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CardsMosaicRowCell extends ListCell<List<Card>> {

    private static final Logger logger = LoggerFactory.getLogger(CardsMosaicRowCell.class);
    private HBox hbox;
    private double imageWidth;
    private double imageHeight;

    public CardsMosaicRowCell(double imageWidth, double imageHeight) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        hbox = new HBox(5);
        hbox.setPadding(new Insets(2));
        hbox.setStyle("-fx-background-color: transparent;");
        hbox.setPickOnBounds(true);
        setStyle("-fx-background-color: transparent;");
        setGraphic(hbox);
        setFocusTraversable(true);
        setupDropTarget();
    }

    /**
     * Makes the mosaic row cell a drop target.
     * MIDDLE → RIGHT: remove the dragged CardElements from the middle pane.
     * RIGHT → RIGHT: ignore.
     */
    private void setupDropTarget() {
        this.setOnDragOver(event -> {
            if ("MIDDLE".equals(Controller.DragDropManager.getDragSourcePane())
                    && event.getDragboard().hasString()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
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
                Controller.MenuActionHandler.handleBulkRemoveElementsFromDecksAndCollections(elements);
                Controller.MenuActionHandler.handleBulkRemoveFromOwnedCollection(elements);
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }

    public static Callback<ListView<List<Card>>, ListCell<List<Card>>> forListView(double imageWidth, double imageHeight) {
        return listView -> {
            listView.setSelectionModel(null); // disable default row selection
            return new CardsMosaicRowCell(imageWidth, imageHeight);
        };
    }

    @Override
    public void updateSelected(boolean selected) {
        // Overridden to prevent the default ListCell selection visuals.
    }

    /**
     * Attaches an "Add to..." right-click context menu to the given ImageView.
     * The menu is tied to {@code card} so each image in the row gets its own
     * independent menu instance pointing to the correct card.
     *
     * @param wrapper
     * @param card    the Card that this image represents
     */
    private static void attachAddToContextMenu(StackPane wrapper, Model.CardsLists.Card card) {
        if (wrapper == null || card == null) return;

        ContextMenu cm = new ContextMenu();
        cm.setStyle(
                "-fx-background-color: #100317; " +
                        "-fx-background-radius: 6; " +
                        "-fx-border-color: #3a3a3a; " +
                        "-fx-border-radius: 6; " +
                        "-fx-border-width: 1;"
        );

        MenuItem addToItem = new MenuItem();
        Label addToLabel = new Label("Add to...");
        addToLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(addToLabel);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        addToItem.setGraphic(graphic);
        addToItem.setText("");
        // TODO: implement Add to... action
        addToItem.setOnAction(e -> { /* TODO */ });

        cm.getItems().add(addToItem);
        cm.show(wrapper, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private String getImagePath(Card card) {
        if (card == null || card.getImagePath() == null)
            return "file:./src/main/resources/placeholder.jpg";
        String imageKey = card.getImagePath();
        String[] addresses = Model.Database.DataBaseUpdate.getAddresses(imageKey + ".jpg");
        if (addresses != null && addresses.length > 0) {
            return "file:" + addresses[0];
        }
        return "file:./src/main/resources/placeholder.jpg";
    }

    private static ContextMenu buildAddToContextMenu(Card clickedCard) {
        ContextMenu cm = new ContextMenu();
        cm.setStyle(
                "-fx-background-color: #100317; -fx-background-radius: 6; " +
                        "-fx-border-color: #3a3a3a; -fx-border-radius: 6; -fx-border-width: 1;");

        // Add to…
        Menu addToMenu = new Menu();
        {
            Label lbl = new Label("Add to...");
            lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
            HBox g = new HBox(lbl);
            g.setAlignment(Pos.CENTER_LEFT);
            g.setPadding(new Insets(2, 6, 2, 6));
            addToMenu.setGraphic(g);
            addToMenu.setText("");
            MenuItem placeholder = new MenuItem("Loading...");
            placeholder.setDisable(true);
            addToMenu.getItems().add(placeholder);
            addToMenu.setOnShowing(evt -> {
                addToMenu.getItems().clear();
                java.util.Collection<Model.CardsLists.Card> cardsToAdd =
                        getEffectiveRightPaneCardsStatic(clickedCard);
                List<MenuItem> items = buildAllDecksDestinationItemsForCards(cardsToAdd);
                if (items.isEmpty()) {
                    MenuItem none = new MenuItem("No destinations available");
                    none.setDisable(true);
                    addToMenu.getItems().add(none);
                } else {
                    addToMenu.getItems().addAll(items);
                }
            });
        }
        cm.getItems().add(addToMenu);

        // Copy
        MenuItem copyMenuItem = new MenuItem();
        {
            Label lbl = new Label("Copy");
            lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
            HBox g = new HBox(lbl);
            g.setAlignment(Pos.CENTER_LEFT);
            g.setPadding(new Insets(2, 6, 2, 6));
            copyMenuItem.setGraphic(g);
            copyMenuItem.setText("");
            copyMenuItem.setOnAction(e -> {
                if (clickedCard == null) return;
                Controller.CardClipboard.copyCards(new java.util.ArrayList<>(
                        getEffectiveRightPaneCardsStatic(clickedCard)));
            });
        }
        cm.getItems().add(copyMenuItem);

        return cm;
    }

    private static java.util.Collection<Model.CardsLists.Card> getEffectiveRightPaneCardsStatic(
            Model.CardsLists.Card clickedCard) {
        if (clickedCard == null) return java.util.Collections.emptyList();
        boolean isRightMultiSelect =
                "RIGHT".equals(Controller.SelectionManager.getActivePart())
                        && Controller.SelectionManager.getSelectedCards().size() > 1
                        && Controller.SelectionManager.getSelectedCards().contains(clickedCard);
        return isRightMultiSelect
                ? Controller.SelectionManager.getSelectedCards()
                : java.util.Collections.singletonList(clickedCard);
    }

    /**
     * Builds deck destination items wired to handleAddToDeck for the mosaic right-panel.
     */
    private static List<MenuItem> buildAllDecksDestinationItemsForCards(
            java.util.Collection<Model.CardsLists.Card> cards) {
        List<MenuItem> items = new ArrayList<>();
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

            if (dac.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                    if (tc == null) continue;
                    String coll = sanitize(tc.getName());
                    items.add(makeDecksCollectionItem(coll, cards));
                    if (tc.getLinkedDecks() != null) {
                        for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Model.CardsLists.Deck deck : unit) {
                                if (deck == null) continue;
                                String base = coll + " / " + sanitize(deck.getName());
                                items.add(makeDecksDestItem(base + " / Main Deck", cards));
                                items.add(makeDecksDestItem(base + " / Extra Deck", cards));
                                items.add(makeDecksDestItem(base + " / Side Deck", cards));
                            }
                        }
                    }
                    items.add(makeDecksExclusionItem(coll, cards));
                }
            }
            if (dac.getDecks() != null) {
                for (Model.CardsLists.Deck deck : dac.getDecks()) {
                    if (deck == null) continue;
                    String d = sanitize(deck.getName());
                    items.add(makeDecksDestItem(d + " / Main Deck", cards));
                    items.add(makeDecksDestItem(d + " / Extra Deck", cards));
                    items.add(makeDecksDestItem(d + " / Side Deck", cards));
                }
            }
        } catch (Exception ex) {
            logger.error("buildAllDecksDestinationItemsForCards failed", ex);
        }
        return items;
    }

    private static MenuItem makeDecksCollectionItem(
            String collName, java.util.Collection<Model.CardsLists.Card> cards) {
        MenuItem mi = new MenuItem();
        Label label = new Label(collName);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        javafx.scene.layout.HBox g = new javafx.scene.layout.HBox(label);
        g.setAlignment(Pos.CENTER_LEFT);
        g.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(g);
        mi.setText("");
        mi.setOnAction(e -> {
            if (cards.size() == 1) Controller.MenuActionHandler.handleAddToCollectionCards(
                    cards.iterator().next(), collName);
            else Controller.MenuActionHandler.handleBulkAddToCollectionCards(cards, collName);
        });
        return mi;
    }

    private static MenuItem makeDecksExclusionItem(
            String collName, java.util.Collection<Model.CardsLists.Card> cards) {
        MenuItem mi = new MenuItem();
        Label label = new Label(collName + " / Exclusion List");
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        javafx.scene.layout.HBox g = new javafx.scene.layout.HBox(label);
        g.setAlignment(Pos.CENTER_LEFT);
        g.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(g);
        mi.setText("");
        mi.setOnAction(e -> {
            if (cards.size() == 1) Controller.MenuActionHandler.handleAddToExclusionList(
                    cards.iterator().next(), collName);
            else Controller.MenuActionHandler.handleBulkAddToExclusionList(cards, collName);
        });
        return mi;
    }

    private static MenuItem makeDecksDestItem(
            String path, java.util.Collection<Model.CardsLists.Card> cards) {
        MenuItem mi = new MenuItem();
        Label label = new Label(path);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        javafx.scene.layout.HBox g = new javafx.scene.layout.HBox(label);
        g.setAlignment(Pos.CENTER_LEFT);
        g.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(g);
        mi.setText("");
        mi.setOnAction(e -> {
            if (cards.size() == 1) Controller.MenuActionHandler.handleAddToDeck(
                    cards.iterator().next(), path);
            else Controller.MenuActionHandler.handleBulkAddToDeck(cards, path);
        });
        return mi;
    }

    private static ContextMenu buildMyCollectionContextMenu(Card card) {
        ContextMenu cm = new ContextMenu();
        cm.setStyle("-fx-background-color: #100317; -fx-background-radius: 6; " +
                "-fx-border-color: #3a3a3a; -fx-border-radius: 6; -fx-border-width: 1;");

        Menu addToMenu = new Menu();
        Label lbl = new Label("Add to...");
        lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
        HBox graphic = new HBox(lbl);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        addToMenu.setGraphic(graphic);
        addToMenu.setText("");

        MenuItem placeholder = new MenuItem("Loading...");
        placeholder.setDisable(true);
        addToMenu.getItems().add(placeholder);

        addToMenu.setOnShowing(evt -> {
            addToMenu.getItems().clear();
            // Resolve effective cards at menu-open time so selection is always current
            java.util.Collection<Model.CardsLists.Card> cardsToAdd =
                    getEffectiveRightPaneCardsStatic(card);
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
        return cm;
    }

    /**
     * Builds "Add to My Collection" destination items for a set of cards.
     * Wires each item to handleAddCopy (single) or handleBulkAddCopy (multi).
     */
    private static List<MenuItem> buildMyCollectionDestinationItemsForCards(
            java.util.Collection<Model.CardsLists.Card> cards) {
        List<MenuItem> items = new ArrayList<>();
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
                items.add(makeMyCollDestItemForCards(boxName, finalCards));
                if (box.getContent() != null) {
                    for (Model.CardsLists.CardsGroup g : box.getContent()) {
                        if (g == null) continue;
                        String groupName = sanitize(g.getName());
                        if (groupName.isEmpty()) continue;
                        final String path = boxName + " / " + groupName;
                        items.add(makeMyCollDestItemForCards(path, finalCards));
                    }
                }
                if (box.getSubBoxes() != null) {
                    for (Model.CardsLists.Box sb : box.getSubBoxes()) {
                        if (sb == null) continue;
                        String subName = sanitize(sb.getName());
                        if (subName.isEmpty()) subName = "(Unnamed sub-box)";
                        items.add(makeMyCollDestItemForCards(subName, finalCards));
                        if (sb.getContent() != null) {
                            for (Model.CardsLists.CardsGroup g : sb.getContent()) {
                                if (g == null) continue;
                                String groupName = sanitize(g.getName());
                                if (groupName.isEmpty()) continue;
                                final String path = subName + " / " + groupName;
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

    private static MenuItem makeMyCollDestItemForCards(
            String path, java.util.Collection<Model.CardsLists.Card> cards) {
        MenuItem mi = new MenuItem();
        Label label = new Label(path);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        javafx.scene.layout.HBox g = new javafx.scene.layout.HBox(label);
        g.setAlignment(Pos.CENTER_LEFT);
        g.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(g);
        mi.setText("");
        mi.setOnAction(e -> {
            if (cards.size() == 1) {
                Controller.MenuActionHandler.handleAddCopy(cards.iterator().next(), path);
            } else {
                Controller.MenuActionHandler.handleBulkAddCopy(cards, path);
            }
        });
        return mi;
    }

    private static List<MenuItem> buildMyCollectionDestinationItems(Card card) {
        List<MenuItem> items = new ArrayList<>();
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
                items.add(makeDestItem(boxName, card));

                if (box.getContent() != null) {
                    for (Model.CardsLists.CardsGroup g : box.getContent()) {
                        if (g == null) continue;
                        String groupName = sanitize(g.getName());
                        if (groupName.isEmpty()) continue;
                        items.add(makeDestItem(boxName + " / " + groupName, card));
                    }
                }
                if (box.getSubBoxes() != null) {
                    for (Model.CardsLists.Box sb : box.getSubBoxes()) {
                        if (sb == null) continue;
                        String subName = sanitize(sb.getName());
                        if (subName.isEmpty()) subName = "(Unnamed sub-box)";
                        items.add(makeDestItem(subName, card));
                        if (sb.getContent() != null) {
                            for (Model.CardsLists.CardsGroup g : sb.getContent()) {
                                if (g == null) continue;
                                String groupName = sanitize(g.getName());
                                if (groupName.isEmpty()) continue;
                                items.add(makeDestItem(subName + " / " + groupName, card));
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

    private static MenuItem makeDestItem(String path, Card card) {
        MenuItem mi = new MenuItem();
        Label label = new Label(path);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        javafx.scene.layout.HBox g = new javafx.scene.layout.HBox(label);
        g.setAlignment(Pos.CENTER_LEFT);
        g.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(g);
        mi.setText("");
        if (card != null) {
            mi.setOnAction(e -> Controller.MenuActionHandler.handleAddCopy(card, path));
        } else {
            mi.setOnAction(e -> { /* TODO: implement Add to deck: path */ });
        }
        return mi;
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

    @Override
    protected void updateItem(List<Card> row, boolean empty) {
        super.updateItem(row, empty);
        hbox.getChildren().clear();
        if (empty || row == null) {
            setGraphic(null);
        } else {
            for (Card card : row) {
                String fullUrl = getImagePath(card);
                Image image = LruImageCache.getImage(fullUrl);
                if (image == null) {
                    try {
                        image = new Image(fullUrl, imageWidth, imageHeight, true, true);
                    } catch (Exception e) {
                        image = new Image("file:./src/main/resources/placeholder.jpg", imageWidth, imageHeight, true, true);
                    }
                }
                ImageView iv = new ImageView(image);
                iv.setFitWidth(imageWidth);
                iv.setFitHeight(imageHeight);
                iv.setPreserveRatio(true);
                iv.setPickOnBounds(true);
                iv.setFocusTraversable(true);

                // Wrap the ImageView in a StackPane so we can show a selection border.
                StackPane wrapper = new StackPane(iv);

                // Use the same padding as the middle-pane CardGridCell (5px on each side).
                // This creates a visible gap between the image and the selection border,
                // matching the middle-pane look exactly.
                wrapper.setPadding(new Insets(4));

                // Fix the wrapper to a constant size (padding × 2 per axis) so the selection
                // border is drawn within the existing space and never causes layout shifts
                // or a horizontal scrollbar.
                double wrapperWidth = imageWidth + 8; // 4px padding × 2 sides
                double wrapperHeight = imageHeight + 8;
                wrapper.setMinWidth(wrapperWidth);
                wrapper.setPrefWidth(wrapperWidth);
                wrapper.setMaxWidth(wrapperWidth);
                wrapper.setMinHeight(wrapperHeight);
                wrapper.setPrefHeight(wrapperHeight);
                wrapper.setMaxHeight(wrapperHeight);

                // Selection visual — identical parameters to the middle-pane CardGridCell:
                // 2px border, 6px radius, drawn inside the 5px padding so the gap between
                // the image edge and the inner face of the border is ~3px.
                if ("RIGHT".equals(SelectionManager.getActivePart())
                        && SelectionManager.getSelectedCards().contains(card)) {
                    wrapper.setStyle(
                            "-fx-background-color: transparent; " +
                                    "-fx-border-color: #cdfc04; " +
                                    "-fx-border-width: 2; " +
                                    "-fx-border-radius: 6;");
                } else {
                    wrapper.setStyle("-fx-background-color: transparent;");
                }

                // Build the menu once for this card/wrapper — outside setOnMousePressed
                ContextMenu decksCardContextMenu = buildAddToContextMenu(card);
                ContextMenu myCollCardContextMenu = buildMyCollectionContextMenu(card);

                wrapper.setOnMouseClicked(event -> {
                    if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                        event.consume();
                        return;
                    }
                    event.consume();
                    if (card == null) return;

                    if (event.isControlDown()) {
                        SelectionManager.toggleSelection(card, "RIGHT");
                    } else if (event.isShiftDown()) {
                        // Flatten ALL rows from the ListView into one ordered list so the range
                        // can span rows (the anchor might be in a completely different row).
                        java.util.List<Card> allCardsAcrossAllRows = new java.util.ArrayList<>();
                        if (getListView() != null && getListView().getItems() != null) {
                            for (java.util.List<Card> rowInMosaic : getListView().getItems()) {
                                if (rowInMosaic != null) allCardsAcrossAllRows.addAll(rowInMosaic);
                            }
                        }
                        SelectionManager.rangeSelect(card, "RIGHT", allCardsAcrossAllRows);
                    } else {
                        SelectionManager.selectCard(card, "RIGHT");
                    }

                    if (getListView() != null) {
                        getListView().refresh();
                    }
                });

                // Drag handlers.
                wrapper.setOnDragDetected(event -> {
                    // Primary card first, then other selected RIGHT-pane cards in order, cap 5
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

                    Dragboard db = wrapper.startDragAndDrop(TransferMode.MOVE);
                    javafx.scene.image.WritableImage ghost =
                            DragDropManager.buildDragGhost(ghostImages, imageWidth, imageHeight);
                    if (ghost != null) {
                        db.setDragView(ghost, imageWidth / 2.0, imageHeight / 2.0);
                    }

                    ClipboardContent content = new ClipboardContent();
                    content.putString(card.getPassCode());
                    db.setContent(content);
                    DragDropManager.startRightDrag(dragCards);
                    logger.debug("Started drag for card in mosaic: {}", card.getName_EN());
                    event.consume();
                });

                wrapper.setOnDragDone(event -> {
                    DragDropManager.clearCurrentlyDraggedCard();
                    logger.debug("Drag done for mosaic card.");
                    event.consume();
                });

                wrapper.setOnMousePressed(event -> {
                    if (event.isSecondaryButtonDown()) {
                        String tab = getCurrentTabName();
                        if (tab.equalsIgnoreCase("My Collection")) {
                            myCollCardContextMenu.show(wrapper, event.getScreenX(), event.getScreenY());
                        } else if (tab.equalsIgnoreCase("Decks and Collections")
                                || tab.equalsIgnoreCase("Decks & Collections")) {
                            decksCardContextMenu.show(wrapper, event.getScreenX(), event.getScreenY());
                        }
                        // Any other tab: consume without showing
                        event.consume();
                    }
                });

                wrapper.setOnMouseReleased(event -> {
                    if (event.getButton() == MouseButton.SECONDARY) {
                        event.consume();
                    }
                });

                wrapper.setOnContextMenuRequested(event -> {
                    String tab = getCurrentTabName();
                    if (tab.equalsIgnoreCase("My Collection")) {
                        myCollCardContextMenu.show(wrapper, event.getScreenX(), event.getScreenY());
                    } else if (tab.equalsIgnoreCase("Decks and Collections")
                            || tab.equalsIgnoreCase("Decks & Collections")) {
                        decksCardContextMenu.show(wrapper, event.getScreenX(), event.getScreenY());
                    }
                    // Any other tab: consume without showing
                    event.consume();
                });

                hbox.getChildren().add(wrapper);
            }
            setGraphic(hbox);
        }
    }
}