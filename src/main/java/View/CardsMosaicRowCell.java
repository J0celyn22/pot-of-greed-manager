package View;

import Controller.DragDropManager;
import Controller.SelectionManager;
import Controller.UserInterfaceFunctions;
import Model.CardsLists.Card;
import Model.CardsLists.DecksAndCollectionsList;
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
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
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
        hbox.setPadding(new Insets(5));
        hbox.setStyle("-fx-background-color: transparent;");
        // Disable default row selection
        hbox.setPickOnBounds(true);
        setStyle("-fx-background-color: transparent;");
        setGraphic(hbox);
        setFocusTraversable(true);
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

    private static ContextMenu buildAddToContextMenu(Card card) {
        ContextMenu cm = new ContextMenu();
        cm.setStyle(
                "-fx-background-color: #100317; -fx-background-radius: 6; " +
                        "-fx-border-color: #3a3a3a; -fx-border-radius: 6; -fx-border-width: 1;"
        );

        Menu addToMenu = new Menu();
        Label lbl = new Label("Add to...");
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        javafx.scene.layout.HBox graphic = new javafx.scene.layout.HBox(lbl);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        addToMenu.setGraphic(graphic);
        addToMenu.setText("");

        MenuItem placeholder = new MenuItem("Loading...");
        placeholder.setDisable(true);
        addToMenu.getItems().add(placeholder);

        addToMenu.setOnShowing(evt -> {
            addToMenu.getItems().clear();
            List<MenuItem> items = buildAllDestinationItems();
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

    private static ContextMenu buildMyCollectionContextMenu(Card card) {
        ContextMenu cm = new ContextMenu();
        cm.setStyle("-fx-background-color: #100317; -fx-background-radius: 6; " +
                "-fx-border-color: #3a3a3a; -fx-border-radius: 6; -fx-border-width: 1;");

        Menu addToMenu = new Menu();
        Label lbl = new Label("Add to...");
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
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
            List<MenuItem> items = buildMyCollectionDestinationItems(card); // pass card here
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

    private static List<MenuItem> buildAllDestinationItems() {
        List<MenuItem> items = new ArrayList<>();
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
                    items.add(makeDestItem(coll, null));
                    if (tc.getLinkedDecks() != null) {
                        for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Model.CardsLists.Deck deck : unit) {
                                if (deck == null) continue;
                                String base = coll + " / " + sanitize(deck.getName());
                                items.add(makeDestItem(base + " / Main Deck", null));
                                items.add(makeDestItem(base + " / Extra Deck", null));
                                items.add(makeDestItem(base + " / Side Deck", null));
                            }
                        }
                    }
                    items.add(makeDestItem(coll + " / Exclusion List", null));
                }
            }

            if (dac.getDecks() != null) {
                for (Model.CardsLists.Deck deck : dac.getDecks()) {
                    if (deck == null) continue;
                    String d = sanitize(deck.getName());
                    items.add(makeDestItem(d + " / Main Deck", null));
                    items.add(makeDestItem(d + " / Extra Deck", null));
                    items.add(makeDestItem(d + " / Side Deck", null));
                }
            }
        } catch (Exception ex) {
            logger.error("buildAllDestinationItems failed", ex);
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

    private static String sanitize(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[=\\-]", "").trim();
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

    private String getCurrentTabName() {
        try {
            // First: walk up the ancestor chain (works when the list is inside the TabPane)
            javafx.scene.Node node = getListView();
            while (node != null && !(node instanceof javafx.scene.control.TabPane)) {
                node = node.getParent();
            }
            if (node instanceof javafx.scene.control.TabPane) {
                javafx.scene.control.Tab sel =
                        ((javafx.scene.control.TabPane) node).getSelectionModel().getSelectedItem();
                if (sel != null && sel.getText() != null) return sel.getText().trim();
            }
            // Fallback: search the whole scene graph (works when the list is a sibling of the TabPane)
            if (getListView() != null && getListView().getScene() != null) {
                javafx.scene.control.TabPane tp = findTabPane(getListView().getScene().getRoot());
                if (tp != null) {
                    javafx.scene.control.Tab sel = tp.getSelectionModel().getSelectedItem();
                    if (sel != null && sel.getText() != null) return sel.getText().trim();
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
                wrapper.setPadding(new Insets(2));
                // Apply selection style only if the active part is "RIGHT".
                if ("RIGHT".equals(SelectionManager.getActivePart()) &&
                        SelectionManager.getSelectedCards().contains(card)) {
                    wrapper.setStyle("-fx-border-color: #cdfc04; -fx-border-width: 3; -fx-border-radius: 8; -fx-background-radius: 8;");
                } else {
                    wrapper.setStyle("");
                }

                // Build the menu once for this card/wrapper — outside setOnMousePressed
                ContextMenu decksCardContextMenu = buildAddToContextMenu(card);
                ContextMenu myCollCardContextMenu = buildMyCollectionContextMenu(card);

                // Click handler for selection. Always passes "RIGHT".
                wrapper.setOnMouseClicked(event -> {
                    if (event.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                        event.consume();
                        return;
                    }
                    event.consume();
                    if (card == null) return;
                    String currentPart = "RIGHT";
                    if (event.isControlDown()) {
                        if (!currentPart.equals(SelectionManager.getActivePart())) {
                            SelectionManager.clearSelection();
                            SelectionManager.selectCard(card, currentPart);
                        } else {
                            if (SelectionManager.getSelectedCards().contains(card)) {
                                SelectionManager.removeFromSelection(card);
                            } else {
                                SelectionManager.addToSelection(card, currentPart);
                            }
                        }
                    } else if (event.isShiftDown()) {
                        if (!currentPart.equals(SelectionManager.getActivePart())) {
                            SelectionManager.clearSelection();
                            SelectionManager.selectCard(card, currentPart);
                        } else {
                            SelectionManager.addToSelection(card, currentPart);
                        }
                    } else {
                        SelectionManager.clearSelection();
                        SelectionManager.selectCard(card, currentPart);
                    }
                    if (getListView() != null) {
                        getListView().refresh();
                    }
                });

                // Drag handlers.
                wrapper.setOnDragDetected(event -> {
                    Dragboard db = wrapper.startDragAndDrop(TransferMode.MOVE);
                    SnapshotParameters params = new SnapshotParameters();
                    params.setFill(Color.TRANSPARENT);
                    WritableImage ghost = wrapper.snapshot(params, null);
                    if (ghost != null) {
                        db.setDragView(ghost, ghost.getWidth() / 2, ghost.getHeight() / 2);
                    }
                    ClipboardContent content = new ClipboardContent();
                    content.putString(card.getPassCode());
                    db.setContent(content);
                    DragDropManager.setCurrentlyDraggedCard(card);
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