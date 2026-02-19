package View;

import Controller.MenuActionHandler;
import Controller.UserInterfaceFunctions;
import Model.CardsLists.*;
import Model.Database.DataBaseUpdate;
import Utils.LruImageCache;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.controlsfx.control.GridCell;
import org.controlsfx.control.GridView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * CardTreeCell - updated to accept DataTreeItem data that may be:
 * - a CardsGroup (normal groups), or
 * - a Map<String,Object> with keys "group" (CardsGroup) and "missing" (Set<String>) for archetype groups.
 *
 * Additionally: when rendering groups that belong to the "My Cards Collection" tree,
 * the GridView userData is set to a Set<String> of identifiers for cards that "need sorting".
 * For this first iteration "need sorting" is defined as:
 *   - card.getName_EN() contains "Trap" (case-insensitive)
 *   - OR card.getName_FR() contains "Piège" (case-insensitive)
 *
 * The renderer checks only that per-group missing set when deciding to glow.
 */
public class CardTreeCell extends TreeCell<String> {

    private static final Logger logger = LoggerFactory.getLogger(CardTreeCell.class);

    private final DoubleProperty cardWidthProperty;
    private final DoubleProperty cardHeightProperty;

    private static final ExecutorService imageLoadingExecutor = Executors.newFixedThreadPool(4);
    private static final ExecutorService pathResolverExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "image-path-resolver");
        t.setDaemon(true);
        return t;
    });

    private static final ConcurrentHashMap<String, String> imagePathCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ImageView, Future<?>> outstandingLoads = new ConcurrentHashMap<>();

    private Label customTriangleLabel;
    private GridView<CardElement> cardGridView;

    private static final String ARCHETYPE_MARKER = "[ARCHETYPE]";

    public CardTreeCell(DoubleProperty cardWidthProperty, DoubleProperty cardHeightProperty) {
        this.cardWidthProperty = cardWidthProperty;
        this.cardHeightProperty = cardHeightProperty;
        getStyleClass().add("card-tree-cell");
    }

    /**
     * Legacy global set kept for compatibility; preferred flow is per-group missing sets.
     */
    private static final Set<String> legacyGlobalMissingSet = ConcurrentHashMap.newKeySet();

    public static void markArchetypeMissing(String id, boolean missing) {
        if (id == null) return;
        if (missing) legacyGlobalMissingSet.add(id);
        else legacyGlobalMissingSet.remove(id);
    }

    public static void clearArchetypeMissingSet() {
        legacyGlobalMissingSet.clear();
    }

    @Override
    protected void updateItem(String itemName, boolean empty) {
        super.updateItem(itemName, empty);
        setText(null);
        setGraphic(null);
        getStyleClass().setAll("card-tree-cell");

        if (empty || itemName == null) {
            return;
        } else {
            TreeItem<String> treeItem = getTreeItem();
            if (treeItem instanceof DataTreeItem) {
                Object dataObject = ((DataTreeItem<?>) treeItem).getData();

                // Two possible data shapes:
                // 1) CardsGroup (normal)
                // 2) Map<String,Object> with "group" and "missing" keys (archetype group)
                CardsGroup group = null;
                Set<String> missingForThisGroup = null;

                if (dataObject instanceof CardsGroup) {
                    group = (CardsGroup) dataObject;
                } else if (dataObject instanceof Map) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) dataObject;
                        Object g = map.get("group");
                        Object m = map.get("missing");
                        if (g instanceof CardsGroup) group = (CardsGroup) g;
                        if (m instanceof Set) {
                            @SuppressWarnings("unchecked")
                            Set<String> s = (Set<String>) m;
                            missingForThisGroup = s;
                        } else if (m instanceof Collection) {
                            Set<String> s = new HashSet<>();
                            for (Object o : (Collection<?>) m) if (o != null) s.add(o.toString());
                            missingForThisGroup = s;
                        }
                    } catch (Exception e) {
                        logger.debug("Failed to extract archetype map data", e);
                    }
                }

                if (group != null) {
                    createCardsGroupCell(itemName, group, missingForThisGroup);
                } else if (dataObject instanceof CardElement) {
                    CardElement cardElement = (CardElement) dataObject;
                    HBox cardBox = new HBox(5);
                    cardBox.setAlignment(Pos.CENTER_LEFT);

                    ImageView imageView = new ImageView();
                    imageView.setFitWidth(cardWidthProperty.get());
                    imageView.setFitHeight(cardHeightProperty.get());
                    imageView.setPreserveRatio(true);

                    String imageKey = safeImageKey(cardElement);
                    String cachedFullPath = imageKey == null ? null : imagePathCache.get(imageKey);
                    if (cachedFullPath != null) {
                        Image cached = LruImageCache.getImage(cachedFullPath);
                        if (cached != null) {
                            imageView.setImage(cached);
                        } else {
                            imageView.setImage(getPlaceholderImage());
                            Future<?> f = loadImageWithResolvedPathAsync(cardElement, imageView, cachedFullPath);
                            if (f != null) outstandingLoads.put(imageView, f);
                        }
                    } else {
                        imageView.setImage(getPlaceholderImage());
                        resolveImagePathAsync(imageKey, resolvedPath -> {
                            if (resolvedPath == null) return;
                            Image cached = LruImageCache.getImage(resolvedPath);
                            if (cached != null) {
                                Platform.runLater(() -> {
                                    Object expected = imageView.getProperties().get("expectedImagePath");
                                    if (Objects.equals(expected, resolvedPath) || expected == null) {
                                        imageView.setImage(cached);
                                        imageView.getProperties().remove("expectedImagePath");
                                    }
                                });
                            } else {
                                imageView.getProperties().put("expectedImagePath", resolvedPath);
                                Future<?> f = loadImageWithResolvedPathAsync(cardElement, imageView, resolvedPath);
                                if (f != null) outstandingLoads.put(imageView, f);
                            }
                        });
                    }

                    Label nameLabel = new Label(cardElement.getCard() == null ? "" : cardElement.getCard().getName_EN());
                    nameLabel.setTextFill(javafx.scene.paint.Color.WHITE);

                    cardBox.getChildren().addAll(imageView, nameLabel);

                    // ----------------------------
                    // Determine whether to apply the "unsorted" glow
                    // ----------------------------
                    boolean needsSorting = false;
                    try {
                        // Retrieve userData from the GridView that was stored in createCardsGroupCell.
                        Object ud = null;
                        try {
                            if (this.cardGridView != null) ud = this.cardGridView.getUserData();
                        } catch (Exception ignored) {
                            ud = null;
                        }

                        // 1) Archetype missing-set logic (keeps Decks & Collections archetype highlighting)
                        Set<String> missingSet = null;
                        String elementNameFromUD = null;

                        if (ud instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> map = (Map<String, Object>) ud;
                            Object ms = map.get("missingSet");
                            if (ms instanceof Set) {
                                @SuppressWarnings("unchecked")
                                Set<String> s = (Set<String>) ms;
                                missingSet = s;
                            } else if (ms instanceof Collection) {
                                Set<String> s = new HashSet<>();
                                for (Object o : (Collection<?>) ms) if (o != null) s.add(o.toString());
                                missingSet = s;
                            }
                            Object en = map.get("elementName");
                            if (en instanceof String) elementNameFromUD = (String) en;
                        } else if (ud instanceof Set) {
                            @SuppressWarnings("unchecked")
                            Set<String> s = (Set<String>) ud;
                            missingSet = s;
                        } else if (ud instanceof String) {
                            elementNameFromUD = (String) ud;
                        }

                        // If a missing set exists and indicates this card is missing, use that (archetype glow)
                        if (missingSet != null && !missingSet.isEmpty()) {
                            String konamiId = cardElement.getCard() == null ? null : cardElement.getCard().getKonamiId();
                            String passCode = cardElement.getCard() == null ? null : cardElement.getCard().getPassCode();
                            boolean missing = false;
                            if (konamiId != null && missingSet.contains(konamiId)) missing = true;
                            if (!missing && passCode != null && missingSet.contains(passCode)) missing = true;
                            // fallback to legacy global set if needed (keeps previous behavior)
                            if (!missing && (missingSet.isEmpty())) {
                                missing = legacyGlobalMissingSet.contains(konamiId) || legacyGlobalMissingSet.contains(passCode);
                            }
                            needsSorting = missing;
                        } else {
                            // 2) No archetype missing-set -> try elementName + computeCardNeedsSorting
                            // But only when the FIRST tab is currently selected.
                            String elementName = elementNameFromUD;
                            boolean isFirstTabSelected = isFirstTabSelected();

                            if (elementName != null && !elementName.trim().isEmpty() && isFirstTabSelected) {
                                try {
                                    needsSorting = Controller.RealMainController.computeCardNeedsSorting(cardElement.getCard(), elementName);
                                } catch (Throwable t) {
                                    needsSorting = false;
                                }
                            } else {
                                needsSorting = false;
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("Failed to compute/apply archetype/sorting decision", e);
                        needsSorting = false;
                    }

                    // Apply glow if needed to the cardBox (keeps other visuals intact)
                    if (needsSorting) {
                        DropShadow innerGlow = new DropShadow();
                        innerGlow.setColor(javafx.scene.paint.Color.web("#ffffff", 1.0));
                        innerGlow.setOffsetX(0);
                        innerGlow.setOffsetY(0);
                        innerGlow.setRadius(4);
                        innerGlow.setSpread(0.9);

                        DropShadow outerGlow = new DropShadow();
                        outerGlow.setColor(javafx.scene.paint.Color.web("#ffffff", 0.22));
                        outerGlow.setOffsetX(0);
                        outerGlow.setOffsetY(0);
                        outerGlow.setRadius(14);
                        outerGlow.setSpread(0.12);

                        outerGlow.setInput(innerGlow);
                        cardBox.setEffect(outerGlow);
                    } else {
                        cardBox.setEffect(null);
                    }

                    setGraphic(cardBox);
                } else if (dataObject instanceof String && dataObject.equals("ROOT")) {
                    Label label = new Label(itemName);
                    label.getStyleClass().add("tree-root-label");
                    setGraphic(label);
                } else {
                    Label label = new Label(itemName);
                    label.getStyleClass().add("tree-item-label");
                    setGraphic(label);
                }
            } else {
                Label label = new Label(itemName);
                label.getStyleClass().add("tree-item-label");
                setGraphic(label);
            }
        }
    }

    /**
     * Helper: determine whether the currently selected Tab is the first tab in the TabPane ancestor.
     * Uses the TreeView ancestor to find a TabPane and checks the selected index.
     * Returns true only when the selected tab index is 0.
     */
    private boolean isFirstTabSelected() {
        try {
            Node node = getTreeView();
            while (node != null && !(node instanceof TabPane)) {
                node = node.getParent();
            }
            if (node instanceof TabPane) {
                TabPane tp = (TabPane) node;
                SingleSelectionModel<Tab> sm = tp.getSelectionModel();
                if (sm != null) {
                    int idx = sm.getSelectedIndex();
                    return idx == 0;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }


    /**
     * Helper: determine whether the currently selected Tab is the My Collection tab.
     * Uses the TreeView ancestor to find a TabPane and checks the selected Tab text.
     * Returns true only when the selected tab's text equals "My Collection" (case-insensitive).
     */
    private boolean isMyCollectionTabSelected() {
        try {
            Node node = getTreeView();
            while (node != null && !(node instanceof TabPane)) {
                node = node.getParent();
            }
            if (node instanceof TabPane) {
                TabPane tp = (TabPane) node;
                Tab sel = tp.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    String t = sel.getText();
                    return t != null && t.trim().equalsIgnoreCase("My Collection");
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Helper: determine whether the currently selected Tab is the OuicheList tab.
     * Uses the TreeView ancestor to find a TabPane and checks the selected Tab text.
     * Returns true only when the selected tab's text equals "OuicheList" (case-insensitive).
     */
    private boolean isOuicheListTabSelected() {
        try {
            Node node = getTreeView();
            while (node != null && !(node instanceof TabPane)) {
                node = node.getParent();
            }
            if (node instanceof TabPane) {
                TabPane tp = (TabPane) node;
                Tab sel = tp.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    String t = sel.getText();
                    return t != null && t.trim().equalsIgnoreCase("OuicheList");
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void resolveImagePathAsync(String imageKey, Consumer<String> callback) {
        if (imageKey == null) {
            callback.accept(null);
            return;
        }
        String cached = imagePathCache.get(imageKey);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        pathResolverExecutor.submit(() -> {
            try {
                String[] addresses = DataBaseUpdate.getAddresses(imageKey + ".jpg");
                String resolved = null;
                if (addresses != null && addresses.length > 0) {
                    resolved = "file:" + addresses[0];
                    imagePathCache.put(imageKey, resolved);
                }
                callback.accept(resolved);
            } catch (Exception e) {
                logger.warn("Failed to resolve image path for key {}", imageKey, e);
                callback.accept(null);
            }
        });
    }

    public static void shutdownImageLoadingExecutor() {
        imageLoadingExecutor.shutdownNow();
        pathResolverExecutor.shutdownNow();
    }

    private Future<?> loadImageWithResolvedPathAsync(CardElement cardElement, ImageView imageView, String resolvedPath) {
        if (resolvedPath == null) {
            Platform.runLater(() -> imageView.setImage(getPlaceholderImage()));
            return null;
        }

        Image cached = LruImageCache.getImage(resolvedPath);
        if (cached != null) {
            Platform.runLater(() -> {
                Object expected = imageView.getProperties().get("expectedImagePath");
                if (Objects.equals(expected, resolvedPath) || expected == null) {
                    imageView.setImage(cached);
                    imageView.getProperties().remove("expectedImagePath");
                }
            });
            return null;
        }

        imageView.getProperties().put("expectedImagePath", resolvedPath);

        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        Future<?> future = imageLoadingExecutor.submit(() -> {
            try {
                Image img = new Image(resolvedPath, cardWidthProperty.get(), cardHeightProperty.get(), true, true, true);

                if (img.getProgress() >= 1.0) {
                    LruImageCache.addImage(resolvedPath, img);
                    Platform.runLater(() -> {
                        Object expected = imageView.getProperties().get("expectedImagePath");
                        if (Objects.equals(expected, resolvedPath)) {
                            imageView.setImage(img);
                            imageView.getProperties().remove("expectedImagePath");
                        }
                    });
                } else {
                    Platform.runLater(() -> {
                        img.progressProperty().addListener((obs, oldV, newV) -> {
                            if (newV.doubleValue() >= 1.0) {
                                LruImageCache.addImage(resolvedPath, img);
                                Object expected = imageView.getProperties().get("expectedImagePath");
                                if (Objects.equals(expected, resolvedPath)) {
                                    imageView.setImage(img);
                                    imageView.getProperties().remove("expectedImagePath");
                                }
                            }
                        });
                    });
                }
            } catch (Exception e) {
                logger.error("Error loading image for card " + (cardElement != null && cardElement.getCard() != null ? cardElement.getCard().getName_EN() : "unknown"), e);
                Platform.runLater(() -> {
                    Object expected = imageView.getProperties().get("expectedImagePath");
                    if (expected == null || Objects.equals(expected, resolvedPath)) {
                        imageView.setImage(getPlaceholderImage());
                        imageView.getProperties().remove("expectedImagePath");
                    }
                });
            } finally {
                outstandingLoads.remove(imageView, futureRef.get());
            }
        });

        futureRef.set(future);
        outstandingLoads.put(imageView, future);
        return future;
    }

    private void updateCustomTriangle(boolean isExpanded) {
        String triangle = isExpanded ? "\u25BC" : "\u25B6";
        if (customTriangleLabel != null) customTriangleLabel.setText(triangle);
    }

    private Image getPlaceholderImage() {
        return PlaceholderHolder.PLACEHOLDER;
    }

    private static class PlaceholderHolder {
        static final Image PLACEHOLDER = new Image("file:./src/main/resources/placeholder.jpg");
    }

    private void adjustGridViewHeight(CardsGroup group) {
        if (cardGridView == null) return;
        int numItems = group.getCardList() == null ? 0 : group.getCardList().size();
        if (numItems <= 0) {
            cardGridView.setPrefHeight(0);
            cardGridView.setMinHeight(0);
            cardGridView.setMaxHeight(0);
            return;
        }
        double availableWidth = getTreeView().getWidth() - 70;
        double effectiveCellWidth = cardWidthProperty.get();
        double horizontalSpacing = cardGridView.getHorizontalCellSpacing();
        int numColumns = (int) Math.floor(availableWidth / (effectiveCellWidth + horizontalSpacing));
        if (numColumns < 1) {
            numColumns = 1;
        }
        int numRows = (int) Math.ceil((double) numItems / numColumns);
        double topPadding = cardGridView.getPadding().getTop();
        double bottomPadding = cardGridView.getPadding().getBottom();
        double verticalSpacing = cardGridView.getVerticalCellSpacing();
        double cellExtraPadding = 10;
        double effectiveCellHeight = cardHeightProperty.get() + cellExtraPadding;
        double prefHeight = topPadding + bottomPadding + (numRows * effectiveCellHeight)
                + ((numRows - 1) * verticalSpacing) + 10;
        cardGridView.setPrefHeight(prefHeight);
        cardGridView.setMinHeight(prefHeight);
        cardGridView.setMaxHeight(prefHeight);
        logger.debug("Adjusted grid view height to {} for {} items", prefHeight, numItems);
    }

    private String safeImageKey(CardElement item) {
        if (item == null) return null;
        try {
            Card c = item.getCard();
            if (c != null) return c.getImagePath();
        } catch (Exception ignored) {
        }
        try {
            String s = item.toString();
            if (s != null && !s.trim().isEmpty()) return s.trim();
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Create the visual cell for a CardsGroup.
     * <p>
     * If this group belongs to an archetype, the provided missingForThisGroup set is used.
     * If this group belongs to the "My Cards Collection" tree, compute a "to-sort" set
     * (cards that contain "Trap" in name_EN or "Piège" in name_FR) and set it as the GridView userData.
     */
    private void createCardsGroupCell(String itemName, CardsGroup group, Set<String> missingForThisGroup) {
        HBox hbox = new HBox();
        hbox.getStyleClass().add("card-group-hbox");
        hbox.setSpacing(5);

        customTriangleLabel = new Label();
        customTriangleLabel.getStyleClass().add("custom-triangle-label");
        customTriangleLabel.setMinWidth(15);
        customTriangleLabel.setAlignment(Pos.CENTER);

        String rawGroupName = group.getName() == null ? "" : group.getName();
        boolean isArchetype = false;
        String displayName = rawGroupName;
        if (rawGroupName.startsWith(ARCHETYPE_MARKER)) {
            isArchetype = true;
            displayName = rawGroupName.substring(ARCHETYPE_MARKER.length());
        }
        displayName = displayName.replaceAll("[=\\-]", "");

        Label label = new Label(displayName);
        label.getStyleClass().add("card-group-label");

        hbox.getChildren().addAll(customTriangleLabel, label);

        VBox vBox = new VBox();
        vBox.getStyleClass().add("card-group-vbox");
        vBox.getChildren().add(hbox);

        GridView<CardElement> grid = new GridView<>();
        grid.getStyleClass().add("card-grid-view");
        grid.setCellFactory(gridView -> new CardGridCell());
        grid.setItems(FXCollections.observableArrayList(group.getCardList() == null ? Collections.emptyList() : group.getCardList()));
        grid.cellWidthProperty().bind(cardWidthProperty);
        grid.cellHeightProperty().bind(cardHeightProperty);
        grid.setHorizontalCellSpacing(5);
        grid.setVerticalCellSpacing(5);
        grid.setPadding(new Insets(5));
        grid.prefWidthProperty().bind(getTreeView().widthProperty().subtract(50));

        /*
         * Store a Map as userData so the renderer has both pieces of information:
         *  - "missingSet" : Set<String> (may be empty or null)
         *  - "elementName": String (displayName)
         *
         * This preserves the archetype missing-set behavior (used by Decks & Collections / Archetypes)
         * while also providing the element name for My Collection compute logic.
         */
        Map<String, Object> ud = new HashMap<>();
        ud.put("missingSet", missingForThisGroup == null ? Collections.emptySet() : missingForThisGroup);
        ud.put("elementName", displayName);
        grid.setUserData(ud);

        this.cardGridView = grid;

        adjustGridViewHeight(group);
        cardWidthProperty.addListener((obs, oldVal, newVal) -> adjustGridViewHeight(group));
        getTreeView().widthProperty().addListener((obs, oldVal, newVal) -> adjustGridViewHeight(group));

        customTriangleLabel.setOnMouseClicked(event -> {
            boolean isExpanded = !grid.isVisible();
            grid.setVisible(isExpanded);
            grid.setManaged(isExpanded);
            updateCustomTriangle(isExpanded);
            event.consume();
        });
        grid.setVisible(true);
        grid.setManaged(true);
        updateCustomTriangle(true);

        vBox.getChildren().add(grid);
        VBox.setVgrow(grid, Priority.ALWAYS);
        setGraphic(vBox);
    }

    /**
     * Return a list of MenuItems representing sorting propositions for the given card.
     * Currently returns an empty list (no propositions). Later this method will compute
     * and return one or several actionable MenuItems.
     * <p>
     * Keep this method small and side-effect free; actions attached to returned MenuItems
     * can call into controller code to perform operations.
     */
    private List<MenuItem> getSortingPropositionsFor(Card card) {
        // Placeholder implementation: return empty list for now.
        // Later: compute proposals and return MenuItems with setOnAction handlers.
        return Collections.emptyList();
    }

    /**
     * Build menu items for Decks and Collections proposals.
     * Each MenuItem label follows the requested format:
     * - CollectionName
     * - CollectionName/DeckName/Main Deck (or Extra/Side)
     */
    private List<MenuItem> buildDecksAndCollectionsProposals(Model.CardsLists.Card card, CardElement clickedElement) {
        List<MenuItem> items = new ArrayList<>();
        try {
            if (card == null) return items;

            if (UserInterfaceFunctions.getDecksList() == null) {
                UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
            }
            DecksAndCollectionsList dac = UserInterfaceFunctions.getDecksList();
            if (dac == null) return items;

            OwnedCardsCollection owned = null;
            try {
                owned = Model.CardsLists.OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
            if (owned == null) {
                try {
                    Controller.UserInterfaceFunctions.loadCollectionFile();
                } catch (Throwable ignored) {
                }
                try {
                    owned = Model.CardsLists.OuicheList.getMyCardsCollection();
                } catch (Throwable ignored) {
                }
            }
            if (owned == null || owned.getOwnedCollection() == null) return items;

            OwnedCardsCollection finalOwned = owned;
            java.util.function.Predicate<String> existsLocation = (name) -> {
                if (name == null || name.trim().isEmpty()) return false;
                String targetSan = sanitizeDisplayName(name).toLowerCase();
                for (Box b : finalOwned.getOwnedCollection()) {
                    if (b == null) continue;
                    String boxSan = sanitizeDisplayName(b.getName()).toLowerCase();
                    if (boxSan.equals(targetSan)) return true;
                    if (b.getContent() != null) {
                        for (CardsGroup g : b.getContent()) {
                            if (g == null) continue;
                            String groupSan = sanitizeDisplayName(g.getName()).toLowerCase();
                            if (groupSan.equals(targetSan)) return true;
                        }
                    }
                }
                if (name.contains("/")) {
                    String[] parts = name.split("/");
                    for (String p : parts) {
                        String pSan = sanitizeDisplayName(p).toLowerCase();
                        if (pSan.isEmpty()) continue;
                        for (Box b : finalOwned.getOwnedCollection()) {
                            if (b == null) continue;
                            String boxSan = sanitizeDisplayName(b.getName()).toLowerCase();
                            if (boxSan.equals(pSan)) return true;
                            if (b.getContent() != null) {
                                for (CardsGroup g : b.getContent()) {
                                    if (g == null) continue;
                                    String groupSan = sanitizeDisplayName(g.getName()).toLowerCase();
                                    if (groupSan.equals(pSan)) return true;
                                }
                            }
                        }
                    }
                    String last = name.substring(name.lastIndexOf('/') + 1);
                    String lastSan = sanitizeDisplayName(last).toLowerCase();
                    if (!lastSan.isEmpty()) {
                        for (Box b : finalOwned.getOwnedCollection()) {
                            if (b == null) continue;
                            String boxSan = sanitizeDisplayName(b.getName()).toLowerCase();
                            if (boxSan.equals(lastSan)) return true;
                            if (b.getContent() != null) {
                                for (CardsGroup g : b.getContent()) {
                                    if (g == null) continue;
                                    String groupSan = sanitizeDisplayName(g.getName()).toLowerCase();
                                    if (groupSan.equals(lastSan)) return true;
                                }
                            }
                        }
                    }
                }
                return false;
            };

            Map<String, Boolean> proposedTargets = new LinkedHashMap<>();

            if (dac.getCollections() != null) {
                for (ThemeCollection tc : dac.getCollections()) {
                    if (tc == null) continue;
                    int countInCollection = countCardInList(tc.toList(), card);
                    if (countInCollection <= 0) continue;

                    boolean collectionProposed = false;
                    for (Box box : owned.getOwnedCollection()) {
                        if (box == null || box.getContent() == null) continue;
                        for (CardsGroup group : box.getContent()) {
                            int countInBoxGroup = countCardInList(group.getCardList(), card);
                            if (countInCollection > countInBoxGroup) {
                                String target = sanitizeDisplayName(tc.getName());
                                boolean exists = existsLocation.test(tc.getName());
                                proposedTargets.put(target, exists);
                                collectionProposed = true;
                                break;
                            }
                        }
                        if (collectionProposed) break;
                    }

                    if (tc.getLinkedDecks() != null) {
                        for (List<Deck> unit : tc.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Deck deck : unit) {
                                if (deck == null) continue;
                                int countMain = countCardInList(deck.getMainDeck(), card);
                                int countExtra = countCardInList(deck.getExtraDeck(), card);
                                int countSide = countCardInList(deck.getSideDeck(), card);

                                // compute total required by this deck across all lists
                                int requiredTotal = countMain + countExtra + countSide;
                                if (requiredTotal > 0) {
                                    int presentTotal = countInOwnedForDeckCombined(owned, deck.getName(), card);
                                    if (requiredTotal > presentTotal) {
                                        // propose targets for the individual lists as before (so user can choose where to place),
                                        // but only if the deck as a whole still needs copies.
                                        if (countMain > 0) {
                                            String target = sanitizeDisplayName(deck.getName()) + "/Main Deck";
                                            boolean exists = existsLocation.test(deck.getName()); // keep your original semantics
                                            proposedTargets.put(target, exists);
                                        }
                                        if (countExtra > 0) {
                                            String target = sanitizeDisplayName(deck.getName()) + "/Extra Deck";
                                            boolean exists = existsLocation.test(deck.getName());
                                            proposedTargets.put(target, exists);
                                        }
                                        if (countSide > 0) {
                                            String target = sanitizeDisplayName(deck.getName()) + "/Side Deck";
                                            boolean exists = existsLocation.test(deck.getName());
                                            proposedTargets.put(target, exists);
                                        }
                                    }
                                }

                            }
                        }
                    }
                }
            }

            if (dac.getDecks() != null) {
                for (Deck deck : dac.getDecks()) {
                    if (deck == null) continue;
                    int countMain = countCardInList(deck.getMainDeck(), card);
                    int countExtra = countCardInList(deck.getExtraDeck(), card);
                    int countSide = countCardInList(deck.getSideDeck(), card);

                    // compute total required by this deck across all lists
                    int requiredTotal = countMain + countExtra + countSide;
                    if (requiredTotal > 0) {
                        int presentTotal = countInOwnedForDeckCombined(owned, deck.getName(), card);
                        if (requiredTotal > presentTotal) {
                            // propose targets for the individual lists as before (so user can choose where to place),
                            // but only if the deck as a whole still needs copies.
                            if (countMain > 0) {
                                String target = sanitizeDisplayName(deck.getName()) + "/Main Deck";
                                boolean exists = existsLocation.test(deck.getName()); // keep your original semantics
                                proposedTargets.put(target, exists);
                            }
                            if (countExtra > 0) {
                                String target = sanitizeDisplayName(deck.getName()) + "/Extra Deck";
                                boolean exists = existsLocation.test(deck.getName());
                                proposedTargets.put(target, exists);
                            }
                            if (countSide > 0) {
                                String target = sanitizeDisplayName(deck.getName()) + "/Side Deck";
                                boolean exists = existsLocation.test(deck.getName());
                                proposedTargets.put(target, exists);
                            }
                        }
                    }
                }
            }

            // Deduplicate by visible label to avoid multiple identical "Add ..." entries
            Set<String> labelsAdded = new HashSet<>();
            for (Map.Entry<String, Boolean> e : proposedTargets.entrySet()) {
                String rawTarget = e.getKey();
                boolean exists = e.getValue() == null ? false : e.getValue();
                if (rawTarget == null || rawTarget.trim().isEmpty()) continue;
                final String handlerTarget = rawTarget;

                String label;
                if (exists) {
                    label = rawTarget;
                } else {
                    String baseName = rawTarget;
                    if (rawTarget.endsWith("/Main Deck") || rawTarget.endsWith("/Extra Deck") || rawTarget.endsWith("/Side Deck")) {
                        String[] parts = rawTarget.split("/");
                        if (parts.length >= 2) baseName = parts.length == 2 ? parts[0] : parts[parts.length - 2];
                    } else if (rawTarget.contains("/")) {
                        String[] parts = rawTarget.split("/");
                        if (parts.length > 0) baseName = parts[parts.length - 1];
                    }
                    label = "Add " + baseName;
                }

                if (labelsAdded.contains(label)) continue;
                labelsAdded.add(label);

                // create MenuItem and wire the move handler (only for direct-move items; Add/Swap left for later)
                MenuItem mi = new MenuItem(label);
                // If this is an "Add ..." or "Swap" label you plan to implement later, you can branch here.
                // For now we wire all non-Swap/Add items to the move handler; Add/Swap will be implemented later.
                if (!label.startsWith("Add ") && !label.startsWith("Swap")) {
                    mi.setOnAction(ev -> MenuActionHandler.handleMove(clickedElement, handlerTarget));
                }
                items.add(mi);
            }

        } catch (Exception ex) {
            logger.debug("buildDecksAndCollectionsProposals failed", ex);
        }
        return items;
    }

    // ---------- Helpers for building proposals (add inside CardTreeCell class) ----------

    /**
     * Count how many copies of `card` are present in the owned collection for the given deck,
     * treating the deck as a single category that may contain Main/Extra/Side together.
     * <p>
     * Matching rules (tolerant):
     * - Prefer Box whose name equals deckName and sum counts of all groups inside it (useful when deck is a Box).
     * - If a Box contains a group named deckName, sum counts of groups in that Box (useful when deck is a group owner).
     * - If groups named "Main Deck"/"Extra Deck"/"Side Deck" exist under a matching Box, sum those specifically.
     * - Fallback: sum counts of any groups named like the deck anywhere.
     */
    private int countInOwnedForDeckCombined(OwnedCardsCollection owned, String deckName, Model.CardsLists.Card card) {
        if (owned == null || owned.getOwnedCollection() == null || deckName == null || deckName.trim().isEmpty())
            return 0;
        String deckNorm = sanitizeDisplayName(deckName).toLowerCase();
        String mainNorm = sanitizeDisplayName("Main Deck").toLowerCase();
        String extraNorm = sanitizeDisplayName("Extra Deck").toLowerCase();
        String sideNorm = sanitizeDisplayName("Side Deck").toLowerCase();

        // 1) If a Box name equals deckName, sum all groups inside that Box (prefer this)
        for (Box b : owned.getOwnedCollection()) {
            if (b == null) continue;
            if (sanitizeDisplayName(b.getName()).toLowerCase().equals(deckNorm)) {
                int sum = 0;
                if (b.getContent() != null) {
                    for (CardsGroup g : b.getContent()) {
                        if (g == null) continue;
                        sum += countCardInList(g.getCardList(), card);
                    }
                }
                return sum;
            }
        }

        // 2) If a Box contains a group named deckName, sum groups in that Box (deck represented as a group owner)
        for (Box b : owned.getOwnedCollection()) {
            if (b == null || b.getContent() == null) continue;
            boolean hasDeckGroup = false;
            for (CardsGroup g : b.getContent()) {
                if (g == null) continue;
                if (sanitizeDisplayName(g.getName()).toLowerCase().equals(deckNorm)) {
                    hasDeckGroup = true;
                    break;
                }
            }
            if (hasDeckGroup) {
                int sum = 0;
                for (CardsGroup g : b.getContent()) {
                    if (g == null) continue;
                    sum += countCardInList(g.getCardList(), card);
                }
                return sum;
            }
        }

        // 3) If a Box contains explicit Main/Extra/Side groups, sum those across the owned collection
        int sumLists = 0;
        for (Box b : owned.getOwnedCollection()) {
            if (b == null || b.getContent() == null) continue;
            boolean boxMatchesDeck = sanitizeDisplayName(b.getName()).toLowerCase().equals(deckNorm);
            boolean anyListFoundInBox = false;
            for (CardsGroup g : b.getContent()) {
                if (g == null) continue;
                String gNorm = sanitizeDisplayName(g.getName()).toLowerCase();
                if (gNorm.equals(mainNorm) || gNorm.equals(extraNorm) || gNorm.equals(sideNorm)) {
                    sumLists += countCardInList(g.getCardList(), card);
                    anyListFoundInBox = true;
                }
            }
            // If we found list groups in a box that matches the deck name, prefer that box's lists
            if (boxMatchesDeck && anyListFoundInBox) return sumLists;
        }
        if (sumLists > 0) return sumLists;

        // 4) Fallback: sum counts of any group named like the deck anywhere
        int fallbackSum = 0;
        for (Box b : owned.getOwnedCollection()) {
            if (b == null || b.getContent() == null) continue;
            for (CardsGroup g : b.getContent()) {
                if (g == null) continue;
                if (sanitizeDisplayName(g.getName()).toLowerCase().equals(deckNorm)) {
                    fallbackSum += countCardInList(g.getCardList(), card);
                }
            }
        }
        return fallbackSum;
    }

    /**
     * Build menu items for Type-of-Cards boxes proposals.
     * We compute candidate element names using RealMainController.computeCardNeedsSorting(card, elementName)
     * by testing existing Box and Group names in the OwnedCardsCollection.
     */
    private List<MenuItem> buildTypeBoxProposals(Model.CardsLists.Card card, CardElement clickedElement) {
        List<MenuItem> items = new ArrayList<>();
        try {
            if (card == null) return items;

            Set<String> desiredFrenchCategories = new LinkedHashSet<>();
            String cardType = card.getCardType() == null ? "" : card.getCardType().trim();
            List<String> properties = card.getCardProperties();
            Set<String> propSet = new HashSet<>();
            if (properties != null) {
                for (String p : properties) if (p != null) propSet.add(p.trim());
            }

            if (cardType.toLowerCase().contains("trap")) {
                if (propSet.contains("Counter")) desiredFrenchCategories.add("Pièges Contre");
                if (propSet.contains("Continuous")) desiredFrenchCategories.add("Pièges Continus");
                if (propSet.contains("Normal")) desiredFrenchCategories.add("Pièges Normaux");
                desiredFrenchCategories.add("Pièges");
            }
            if (cardType.toLowerCase().contains("spell")) {
                if (propSet.contains("Continuous")) desiredFrenchCategories.add("Magies Continues");
                if (propSet.contains("Quick-Play") || propSet.contains("Quick Play"))
                    desiredFrenchCategories.add("Magies Jeu-Rapide");
                if (propSet.contains("Equip")) desiredFrenchCategories.add("Magies Équipement");
                if (propSet.contains("Field")) desiredFrenchCategories.add("Magies Terrain");
                if (propSet.contains("Ritual")) desiredFrenchCategories.add("Magies Rituel");
                if (propSet.contains("Normal")) desiredFrenchCategories.add("Magies Normales");
                desiredFrenchCategories.add("Magies");
            }
            if (cardType.toLowerCase().contains("monster")) {
                if (propSet.contains("Effect")) desiredFrenchCategories.add("Monstres à Effet");
                if (propSet.contains("Tuner")) desiredFrenchCategories.add("Monstres Syntoniseurs");
                if (propSet.contains("Synchro")) desiredFrenchCategories.add("Monstres Synchro");
                if (propSet.contains("Pendulum")) desiredFrenchCategories.add("Monstres Pendule");
                if (propSet.contains("Fusion")) desiredFrenchCategories.add("Monstres Fusion");
                if (propSet.contains("Xyz")) desiredFrenchCategories.add("Monstres Xyz");
                if (propSet.contains("Link")) desiredFrenchCategories.add("Monstres Lien");
                if (propSet.contains("Ritual")) desiredFrenchCategories.add("Monstres Rituel");
                if (propSet.contains("Normal")) desiredFrenchCategories.add("Monstres Normaux");
                if (propSet.contains("Toon")) desiredFrenchCategories.add("Monstres Toon");
                if (propSet.contains("Flip")) desiredFrenchCategories.add("Monstres Flip");
                if (propSet.contains("Spirit")) desiredFrenchCategories.add("Monstres Spirit");
                if (propSet.contains("Union")) desiredFrenchCategories.add("Monstres Union");
                if (propSet.contains("Gemini")) desiredFrenchCategories.add("Monstres Gemini");
                desiredFrenchCategories.add("Monstres");
            }

            if (desiredFrenchCategories.isEmpty()) return items;

            OwnedCardsCollection owned = null;
            try {
                owned = Model.CardsLists.OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
            if (owned == null) {
                try {
                    Controller.UserInterfaceFunctions.loadCollectionFile();
                } catch (Throwable ignored) {
                }
                try {
                    owned = Model.CardsLists.OuicheList.getMyCardsCollection();
                } catch (Throwable ignored) {
                }
            }
            if (owned == null || owned.getOwnedCollection() == null) return items;

            Map<String, List<String>> existingCategoryToLocations = new LinkedHashMap<>();
            for (Box box : owned.getOwnedCollection()) {
                String rawBoxName = box.getName() == null ? "" : box.getName();
                String sanitizedBox = sanitizeDisplayName(rawBoxName).toLowerCase();
                existingCategoryToLocations.computeIfAbsent(sanitizedBox, k -> new ArrayList<>()).add(rawBoxName);
                if (box.getContent() != null) {
                    for (CardsGroup g : box.getContent()) {
                        String rawGroupName = g.getName() == null ? "" : g.getName();
                        String sanitizedGroup = sanitizeDisplayName(rawGroupName).toLowerCase();
                        existingCategoryToLocations.computeIfAbsent(sanitizedGroup, k -> new ArrayList<>())
                                .add(rawBoxName + "/" + rawGroupName);
                    }
                }
            }

            for (String desired : desiredFrenchCategories) {
                if (desired == null || desired.trim().isEmpty()) continue;
                String desiredSan = sanitizeDisplayName(desired).toLowerCase();
                List<String> locations = existingCategoryToLocations.get(desiredSan);
                if (locations == null || locations.isEmpty()) continue;

                for (String rawLocation : locations) {
                    String[] parts = rawLocation.split("/", 2);
                    String boxRaw = parts.length > 0 ? parts[0] : "";
                    String groupRaw = parts.length > 1 ? parts[1] : null;

                    boolean alreadyThere = false;
                    for (Box box : owned.getOwnedCollection()) {
                        if (!sanitizeDisplayName(box.getName()).equalsIgnoreCase(sanitizeDisplayName(boxRaw))) continue;
                        if (groupRaw == null) {
                            if (box.getContent() != null) {
                                for (CardsGroup g : box.getContent()) {
                                    if (countCardInList(g.getCardList(), card) > 0) {
                                        alreadyThere = true;
                                        break;
                                    }
                                }
                            }
                        } else {
                            if (box.getContent() != null) {
                                for (CardsGroup g : box.getContent()) {
                                    if (sanitizeDisplayName(g.getName()).equalsIgnoreCase(sanitizeDisplayName(groupRaw))) {
                                        if (countCardInList(g.getCardList(), card) > 0) alreadyThere = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (alreadyThere) break;
                    }
                    if (alreadyThere) continue;

                    String displayBox = sanitizeDisplayName(boxRaw);
                    String displayTarget = groupRaw == null ? displayBox : displayBox + "/" + sanitizeDisplayName(groupRaw);
                    final String handlerTargetCopy = groupRaw == null ? displayBox : displayBox + "/" + sanitizeDisplayName(groupRaw);

                    // create MenuItem and wire the move handler
                    MenuItem mi = new MenuItem(displayTarget);
                    mi.setOnAction(ev -> MenuActionHandler.handleMove(clickedElement, handlerTargetCopy));
                    items.add(mi);
                }
            }

        } catch (Exception e) {
            logger.debug("buildTypeBoxProposals failed", e);
        }
        return items;
    }

    // Helper: sanitize display names (remove '=' and '-')
    private String sanitizeDisplayName(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[=\\-]", "");
    }

    /**
     * Find the location string for a CardElement inside the given OwnedCardsCollection.
     * Returned format: "BoxName/GroupName@index" where BoxName and GroupName are sanitized
     * (remove '=' and '-') and index is the position inside the group's list.
     * <p>
     * This method is robust: it first tries to locate the exact same CardElement instance
     * (identity). If that fails (for example because the UI and the collection use different
     * object instances), it attempts to find the best matching CardElement by comparing:
     * - card identity (passCode / printCode / konamiId)
     * - CardElement metadata (specificArtwork, artwork, isOwned, dontRemove, isInDeck)
     * <p>
     * If multiple candidates remain, the first matching candidate is returned. Returns null
     * only if no matching element is found in the provided collection.
     */
    private String findElementLocation(CardElement target, OwnedCardsCollection owned) {
        if (target == null || owned == null || owned.getOwnedCollection() == null) return null;

        // Helper to compare two Card objects (same logic as cardsMatch)
        java.util.function.BiPredicate<Model.CardsLists.Card, Model.CardsLists.Card> sameCard =
                (a, b) -> {
                    if (a == null || b == null) return false;
                    if (a.getPassCode() != null && b.getPassCode() != null && a.getPassCode().equals(b.getPassCode()))
                        return true;
                    if (a.getPrintCode() != null && b.getPrintCode() != null && a.getPrintCode().equals(b.getPrintCode()))
                        return true;
                    if (a.getKonamiId() != null && b.getKonamiId() != null && a.getKonamiId().equals(b.getKonamiId()))
                        return true;
                    return false;
                };

        // Helper to compare CardElement metadata for a stronger match when identity differs
        java.util.function.BiPredicate<CardElement, CardElement> elementMetadataEquals = (a, b) -> {
            if (a == null || b == null) return false;
            boolean sa = a.getSpecificArtwork() == null ? false : a.getSpecificArtwork();
            boolean sb = b.getSpecificArtwork() == null ? false : b.getSpecificArtwork();
            if (sa != sb) return false;
            if (a.getArtwork() != b.getArtwork()) return false;
            boolean oa = a.getOwned() == null ? false : a.getOwned();
            boolean ob = b.getOwned() == null ? false : b.getOwned();
            if (oa != ob) return false;
            boolean ida = a.getIsInDeck() == null ? false : a.getIsInDeck();
            boolean idb = b.getIsInDeck() == null ? false : b.getIsInDeck();
            if (ida != idb) return false;
            boolean da = a.getDontRemove() == null ? false : a.getDontRemove();
            boolean db = b.getDontRemove() == null ? false : b.getDontRemove();
            if (da != db) return false;
            return true;
        };

        // 1) Try identity search first (fast and exact)
        for (Box box : owned.getOwnedCollection()) {
            if (box == null || box.getContent() == null) continue;
            String boxLabel = sanitizeDisplayName(box.getName());
            for (CardsGroup g : box.getContent()) {
                if (g == null) continue;
                List<CardElement> list = g.getCardList();
                if (list == null) continue;
                for (int i = 0; i < list.size(); i++) {
                    CardElement ce = list.get(i);
                    if (ce == target) {
                        String groupLabel = sanitizeDisplayName(g.getName());
                        return boxLabel + "/" + groupLabel + "@" + i;
                    }
                }
            }
        }

        // 2) If identity search failed, try to find by card identity + metadata
        Model.CardsLists.Card targetCard = target.getCard();
        if (targetCard == null) return null;

        // Collect candidates that match by card identity
        List<FoundCandidate> candidates = new ArrayList<>();
        for (Box box : owned.getOwnedCollection()) {
            if (box == null || box.getContent() == null) continue;
            String boxLabel = sanitizeDisplayName(box.getName());
            for (CardsGroup g : box.getContent()) {
                if (g == null) continue;
                List<CardElement> list = g.getCardList();
                if (list == null) continue;
                for (int i = 0; i < list.size(); i++) {
                    CardElement ce = list.get(i);
                    if (ce == null || ce.getCard() == null) continue;
                    if (sameCard.test(ce.getCard(), targetCard)) {
                        String groupLabel = sanitizeDisplayName(g.getName());
                        candidates.add(new FoundCandidate(boxLabel, groupLabel, i, ce));
                    }
                }
            }
        }

        if (candidates.isEmpty()) return null;

        // 3) Prefer candidates that match metadata exactly
        for (FoundCandidate fc : candidates) {
            if (elementMetadataEquals.test(fc.element, target)) {
                return fc.box + "/" + fc.group + "@" + fc.index;
            }
        }

        // 4) If none matched metadata exactly, return the first candidate that is not the same instance
        // (we already checked identity earlier, but keep defensive check)
        for (FoundCandidate fc : candidates) {
            if (fc.element != target) {
                return fc.box + "/" + fc.group + "@" + fc.index;
            }
        }

        // 5) As a last resort, return the first candidate's location
        FoundCandidate fc = candidates.get(0);
        return fc.box + "/" + fc.group + "@" + fc.index;
    }

    /**
     * Build swap proposals for the given card and clickedElement.
     * <p>
     * Avoid proposing a swap with the same CardElement instance (identity) and avoid proposing
     * a swap with a card located at the same location as the clicked element (defensive).
     * <p>
     * Uses the shared OwnedCardsCollection from OuicheList.getMyCardsCollection() when available
     * so identity comparisons are meaningful; falls back to file-based constructor otherwise.
     */
    private List<MenuItem> buildSwapProposals(Model.CardsLists.Card card, CardElement clickedElement) {
        List<MenuItem> items = new ArrayList<>();
        try {
            OwnedCardsCollection owned = null;
            try {
                owned = Model.CardsLists.OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
            if (owned == null) {
                try {
                    Controller.UserInterfaceFunctions.loadCollectionFile();
                } catch (Throwable ignored) {
                }
                try {
                    owned = Model.CardsLists.OuicheList.getMyCardsCollection();
                } catch (Throwable ignored) {
                }
            }
            if (owned == null || owned.getOwnedCollection() == null) return items;

            String clickedLocation = findElementLocation(clickedElement, owned);

            for (Box box : owned.getOwnedCollection()) {
                if (box == null || box.getContent() == null) continue;
                for (CardsGroup g : box.getContent()) {
                    if (g == null) continue;
                    List<CardElement> list = g.getCardList();
                    if (list == null) continue;
                    for (int i = 0; i < list.size(); i++) {
                        CardElement ce = list.get(i);
                        if (ce == null || ce.getCard() == null) continue;
                        if (ce == clickedElement) continue;
                        if (!cardsMatch(ce.getCard(), card)) continue;

                        String candidateLocation = sanitizeDisplayName(box.getName()) + "/" + sanitizeDisplayName(g.getName()) + "@" + i;
                        if (clickedLocation != null && candidateLocation.equals(clickedLocation)) continue;

                        String boxLabel = sanitizeDisplayName(box.getName());
                        String groupLabel = sanitizeDisplayName(g.getName());
                        String displayLocation = boxLabel + "/" + groupLabel + " (index " + i + ")";
                        final String locationCopy = displayLocation;

                        MenuItem mi = new MenuItem(displayLocation);
                        mi.setOnAction(e -> onProposeSwapWith(clickedElement, locationCopy));
                        items.add(mi);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("buildSwapProposals failed", e);
        }
        return items;
    }

    /**
     * Count how many occurrences of the given card exist in the provided CardElement list.
     * Matching uses passCode, then printCode, then konamiId.
     */
    private int countCardInList(List<CardElement> list, Model.CardsLists.Card card) {
        if (list == null || card == null) return 0;
        int count = 0;
        for (CardElement ce : list) {
            if (ce == null || ce.getCard() == null) continue;
            if (cardsMatch(ce.getCard(), card)) count++;
        }
        return count;
    }

    /**
     * Compare two Card objects for identity using passCode, then printCode, then konamiId.
     */
    private boolean cardsMatch(Model.CardsLists.Card a, Model.CardsLists.Card b) {
        if (a == null || b == null) return false;
        if (a.getPassCode() != null && b.getPassCode() != null && a.getPassCode().equals(b.getPassCode())) return true;
        if (a.getPrintCode() != null && b.getPrintCode() != null && a.getPrintCode().equals(b.getPrintCode()))
            return true;
        if (a.getKonamiId() != null && b.getKonamiId() != null && a.getKonamiId().equals(b.getKonamiId())) return true;
        return false;
    }

    // Placeholder handlers: only log for now. Implement move/swap logic later.
    private void onProposeMoveToLocation(CardElement clickedElement, String target) {
        logger.info("Proposed move: {} -> {}", clickedElement == null ? "null" : clickedElement.toString(), target);
        // TODO: implement actual move logic later
    }

    private void onProposeSwapWith(CardElement clickedElement, String otherLocation) {
        logger.info("Proposed swap: {} <-> {}", clickedElement == null ? "null" : clickedElement.toString(), otherLocation);
        // TODO: implement actual swap logic later
    }

    // Small helper holder used by findElementLocation
    private static class FoundCandidate {
        final String box;
        final String group;
        final int index;
        final CardElement element;

        FoundCandidate(String box, String group, int index, CardElement element) {
            this.box = box;
            this.group = group;
            this.index = index;
            this.element = element;
        }
    }

    // Replace the existing inner class CardGridCell with this complete implementation
    private class CardGridCell extends GridCell<CardElement> {
        private final ImageView cardImageView;
        private final StackPane wrapper;
        private Future<?> imageLoadFuture;
        private String currentImageKey;

        public CardGridCell() {
            cardImageView = new ImageView();
            cardImageView.setPreserveRatio(true);
            cardImageView.fitWidthProperty().bind(cardWidthProperty);
            cardImageView.fitHeightProperty().bind(cardHeightProperty);

            wrapper = new StackPane(cardImageView);
            wrapper.setPadding(new Insets(5));
            wrapper.setStyle("-fx-background-color: transparent;");
            wrapper.setPickOnBounds(true);
            wrapper.setFocusTraversable(true);
            setGraphic(wrapper);

            // ----------------------------
            // Context menu: only in "My Collection" tab
            // ----------------------------
            ContextMenu contextMenu = new ContextMenu();

            // --- Sort card as a real Menu (submenu) with left-aligned accent label ---
            Menu sortingMenu = new Menu();
            {
                Label sortLabel = new Label("Sort card");
                sortLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-weight: normal;");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                HBox sortGraphic = new HBox(6, sortLabel, spacer);
                sortGraphic.setAlignment(Pos.CENTER_LEFT);
                sortGraphic.setPadding(new Insets(2, 6, 2, 6));

                sortingMenu.setGraphic(sortGraphic);
                sortingMenu.setText(""); // hide default text since we use the graphic

                // Add a disabled placeholder so the Menu is rendered as a submenu by the platform.
                MenuItem placeholder = new MenuItem("Loading...");
                placeholder.setDisable(true);
                sortingMenu.getItems().add(placeholder);

                // Populate the submenu each time it is shown (lazy population).
                // Replace the existing sortingMenu.setOnShowing(...) with this block
                sortingMenu.setOnShowing(evt -> {
                    try {
                        sortingMenu.getItems().clear();

                        CardElement ce = getItem();
                        if (ce == null || ce.getCard() == null) {
                            MenuItem none = new MenuItem("No card selected");
                            none.setDisable(true);
                            sortingMenu.getItems().add(none);
                            return;
                        }
                        Model.CardsLists.Card card = ce.getCard();

                        // 1) Decks and Collections section
                        List<MenuItem> dcItems = buildDecksAndCollectionsProposals(card, ce);
                        if (!dcItems.isEmpty()) {
                            sortingMenu.getItems().addAll(dcItems);
                            sortingMenu.getItems().add(new SeparatorMenuItem());
                        }

                        // 2) Type of Cards boxes section
                        List<MenuItem> typeItems = buildTypeBoxProposals(card, ce);
                        if (!typeItems.isEmpty()) {
                            sortingMenu.getItems().addAll(typeItems);
                            sortingMenu.getItems().add(new SeparatorMenuItem());
                        }

                        // 3) Swap section: try to call buildSwapProposals(card, ce) if it exists.
                        //    Use reflection so compilation does not require the method to be present.
                        try {
                            java.lang.reflect.Method swapMethod = null;
                            try {
                                swapMethod = CardTreeCell.class.getDeclaredMethod("buildSwapProposals", Model.CardsLists.Card.class, Model.CardsLists.CardElement.class);
                            } catch (NoSuchMethodException ns) {
                                // try public method on this class (in case it's declared elsewhere)
                                try {
                                    swapMethod = CardTreeCell.class.getMethod("buildSwapProposals", Model.CardsLists.Card.class, Model.CardsLists.CardElement.class);
                                } catch (NoSuchMethodException ignored) {
                                }
                            }

                            if (swapMethod != null) {
                                swapMethod.setAccessible(true);
                                @SuppressWarnings("unchecked")
                                List<MenuItem> swapItems = (List<MenuItem>) swapMethod.invoke(CardTreeCell.this, card, ce);
                                if (swapItems != null && !swapItems.isEmpty()) {
                                    sortingMenu.getItems().addAll(swapItems);
                                } else {
                                    // If helper returned nothing, show a small disabled placeholder to indicate no swap targets
                                    MenuItem noneSwap = new MenuItem("No swap proposals");
                                    noneSwap.setDisable(true);
                                    sortingMenu.getItems().add(noneSwap);
                                }
                            } else {
                                // If the helper isn't present, do nothing (preserve existing behavior)
                                // Optionally add a disabled placeholder if you prefer:
                                // MenuItem noneSwap = new MenuItem("Swap not available");
                                // noneSwap.setDisable(true);
                                // sortingMenu.getItems().add(noneSwap);
                            }
                        } catch (Throwable t) {
                            // If reflection or invocation fails, log and show a disabled error item
                            logger.debug("Failed to invoke buildSwapProposals via reflection", t);
                            MenuItem err = new MenuItem("Error building swap proposals");
                            err.setDisable(true);
                            sortingMenu.getItems().add(err);
                        }

                        // If nothing was added at all (very unlikely because earlier sections usually add items),
                        // ensure the menu shows a disabled placeholder so it renders correctly.
                        if (sortingMenu.getItems().isEmpty()) {
                            MenuItem none = new MenuItem("No proposals");
                            none.setDisable(true);
                            sortingMenu.getItems().add(none);
                        }
                    } catch (Throwable t) {
                        logger.debug("Error building sorting submenu", t);
                        sortingMenu.getItems().clear();
                        MenuItem err = new MenuItem("Error building proposals");
                        err.setDisable(true);
                        sortingMenu.getItems().add(err);
                    }
                });
            }
            contextMenu.getItems().add(sortingMenu);

            // --- Move to... as a Menu (submenu) with same left-aligned accent label ---
            Menu moveToMenu = new Menu();
            {
                Label moveLabel = new Label("Move to...");
                moveLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-weight: normal;");

                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);

                HBox moveGraphic = new HBox(6, moveLabel, spacer);
                moveGraphic.setAlignment(Pos.CENTER_LEFT);
                moveGraphic.setPadding(new Insets(2, 6, 2, 6));

                moveToMenu.setGraphic(moveGraphic);
                moveToMenu.setText("");

                // Add a disabled placeholder so the Menu is rendered as a submenu by the platform.
                MenuItem placeholder = new MenuItem("Loading...");
                placeholder.setDisable(true);
                moveToMenu.getItems().add(placeholder);

                // Populate the "Move to..." submenu lazily when it is shown.
                moveToMenu.setOnShowing(evt -> {
                    moveToMenu.getItems().clear();

                    // Get the clicked element (the card under the mouse / this cell)
                    CardElement clickedElement = getItem();
                    if (clickedElement == null) {
                        MenuItem none = new MenuItem("No card selected");
                        none.setDisable(true);
                        moveToMenu.getItems().add(none);
                        return;
                    }

                    // Obtain the owned collection (load if necessary)
                    Model.CardsLists.OwnedCardsCollection owned = null;
                    try {
                        owned = Model.CardsLists.OuicheList.getMyCardsCollection();
                    } catch (Throwable ignored) {
                    }
                    if (owned == null) {
                        try {
                            Controller.UserInterfaceFunctions.loadCollectionFile();
                        } catch (Throwable ignored) {
                        }
                        try {
                            owned = Model.CardsLists.OuicheList.getMyCardsCollection();
                        } catch (Throwable ignored) {
                        }
                    }

                    if (owned == null || owned.getOwnedCollection() == null || owned.getOwnedCollection().isEmpty()) {
                        MenuItem none = new MenuItem("No boxes available");
                        none.setDisable(true);
                        moveToMenu.getItems().add(none);
                        return;
                    }

                    // Find current source location (box and group) for the clicked element so we can exclude the exact category
                    Model.CardsLists.Box currentBox = null;
                    Model.CardsLists.CardsGroup currentGroup = null;
                    try {
                        outer:
                        for (Model.CardsLists.Box b : owned.getOwnedCollection()) {
                            if (b == null) continue;
                            if (b.getContent() != null) {
                                for (Model.CardsLists.CardsGroup g : b.getContent()) {
                                    if (g == null || g.getCardList() == null) continue;
                                    for (Model.CardsLists.CardElement ce : g.getCardList()) {
                                        if (ce == clickedElement) {
                                            currentBox = b;
                                            currentGroup = g;
                                            break outer;
                                        }
                                    }
                                }
                            }
                            if (b.getSubBoxes() != null) {
                                for (Model.CardsLists.Box sb : b.getSubBoxes()) {
                                    if (sb == null || sb.getContent() == null) continue;
                                    for (Model.CardsLists.CardsGroup g : sb.getContent()) {
                                        if (g == null || g.getCardList() == null) continue;
                                        for (Model.CardsLists.CardElement ce : g.getCardList()) {
                                            if (ce == clickedElement) {
                                                currentBox = sb;
                                                currentGroup = g;
                                                break outer;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }

                    // Helper to create a MenuItem that triggers the move handler
                    java.util.function.BiFunction<String, String, MenuItem> makeMoveItem = (display, handlerTarget) -> {
                        MenuItem mi = new MenuItem(display);
                        mi.setOnAction(ae -> {
                            try {
                                MenuActionHandler.handleMove(getItem(), handlerTarget);
                            } catch (Throwable t) {
                                logger.debug("Move action failed for target {}", handlerTarget, t);
                            }
                        });
                        return mi;
                    };

                    // Iterate owned boxes and their groups to create menu entries.
                    // We exclude ONLY the exact current group; box-level destinations are always allowed.
                    for (Model.CardsLists.Box b : owned.getOwnedCollection()) {
                        if (b == null) continue;
                        String boxName = b.getName() == null ? "" : b.getName().replaceAll("[=\\-]", "").trim();
                        if (boxName.isEmpty()) boxName = "(Unnamed box)";

                        // Always offer the box-level destination (even if the card currently lives in a category of this box)
                        MenuItem miBox = makeMoveItem.apply(boxName, boxName);
                        moveToMenu.getItems().add(miBox);

                        // Add entries for each group inside the box
                        if (b.getContent() != null) {
                            for (Model.CardsLists.CardsGroup g : b.getContent()) {
                                if (g == null) continue;

                                // Skip unnamed groups entirely — the Box button already covers this destination
                                String rawName = g.getName();
                                if (rawName == null || rawName.trim().isEmpty()) {
                                    continue;
                                }

                                String groupName = rawName.replaceAll("[=\\-]", "").trim();
                                if (groupName.isEmpty()) continue;

                                boolean groupIsCurrent =
                                        (currentBox != null && currentGroup != null && currentBox == b && currentGroup == g);
                                if (groupIsCurrent) continue;

                                String display = boxName + " / " + groupName;
                                String handlerTarget = boxName + "/" + groupName;
                                MenuItem miGroup = makeMoveItem.apply(display, handlerTarget);
                                moveToMenu.getItems().add(miGroup);
                            }
                        }


                        // Also include sub-boxes (if any) as separate entries (and their groups)
                        if (b.getSubBoxes() != null) {
                            for (Model.CardsLists.Box sb : b.getSubBoxes()) {
                                if (sb == null) continue;
                                String subBoxName = sb.getName() == null ? "" : sb.getName().replaceAll("[=\\-]", "").trim();
                                if (subBoxName.isEmpty()) subBoxName = "(Unnamed sub-box)";

                                // Always offer sub-box-level destination too
                                MenuItem miSubBox = makeMoveItem.apply(subBoxName, subBoxName);
                                moveToMenu.getItems().add(miSubBox);

                                if (sb.getContent() != null) {
                                    for (Model.CardsLists.CardsGroup g : sb.getContent()) {
                                        if (g == null) continue;
                                        String groupName = g.getName() == null ? "" : g.getName().replaceAll("[=\\-]", "").trim();
                                        if (groupName.isEmpty()) groupName = "(Unnamed group)";
                                        boolean groupIsCurrent = (currentBox != null && currentGroup != null && currentBox == sb && currentGroup == g);
                                        if (groupIsCurrent) continue;
                                        String display = subBoxName + " / " + groupName;
                                        String handlerTarget = subBoxName + "/" + groupName;
                                        MenuItem mi = makeMoveItem.apply(display, handlerTarget);
                                        moveToMenu.getItems().add(mi);
                                    }
                                }
                            }
                        }
                    }

                    // If no valid destinations were added, show a disabled placeholder
                    if (moveToMenu.getItems().isEmpty()) {
                        MenuItem none = new MenuItem("No other destinations");
                        none.setDisable(true);
                        moveToMenu.getItems().add(none);
                    }
                });

            }
            contextMenu.getItems().add(moveToMenu);

            // Root-level Remove item (red text) — left-aligned graphic version
            MenuItem removeRootItem = new MenuItem();
            Label removeLabel = new Label("Remove");
            removeLabel.setStyle("-fx-text-fill: #ff4d4d; -fx-font-weight: bold;");
            HBox removeGraphic = new HBox(removeLabel);
            removeGraphic.setAlignment(Pos.CENTER_LEFT);
            removeGraphic.setPadding(new Insets(2, 6, 2, 6));
            removeRootItem.setGraphic(removeGraphic);
            removeRootItem.setText(""); // ensure no duplicate text on the right
            removeRootItem.setOnAction(ae -> {
                CardElement ce = getItem();
                if (ce == null) return;

                // 1) perform the removal (model change)
                removeCardElement(ce);

                // 2) Try to invoke the exact same refresh helper used by the move action.
                Controller.UserInterfaceFunctions.refreshOwnedCollectionView();
            });


            contextMenu.getItems().add(new SeparatorMenuItem()); // optional separator for clarity
            contextMenu.getItems().add(removeRootItem);

            // Keep the Remove item disabled when no card is selected; update on showing
            contextMenu.setOnShowing(ev -> {
                CardElement ce = getItem();
                removeRootItem.setDisable(ce == null);
            });

            // Attach the context menu to the wrapper so right-click shows it
            wrapper.setOnContextMenuRequested(e -> {
                // Only show in My Collection tab (existing behavior)
                if (isMyCollectionTabSelected()) {
                    contextMenu.show(wrapper, e.getScreenX(), e.getScreenY());
                }
                e.consume();
            });
        }

        // Helper: remove the given CardElement from the owned collection and refresh UI.
        // Tries MenuActionHandler.handleRemove(...) first, falls back to direct removal.
        private void removeCardElement(Model.CardsLists.CardElement ce) {
            if (ce == null) return;

            // 1) Try centralized handler via reflection if available
            try {
                java.lang.reflect.Method m = Controller.MenuActionHandler.class.getMethod("handleRemove", Model.CardsLists.CardElement.class);
                if (m != null) {
                    m.invoke(null, ce);
                    // Ask UI to refresh
                    Platform.runLater(() -> {
                        try {
                            if (getGridView() != null) {
                                // try refresh() if present
                                try {
                                    java.lang.reflect.Method refresh = getGridView().getClass().getMethod("refresh");
                                    refresh.invoke(getGridView());
                                } catch (NoSuchMethodException ns) {
                                    getGridView().requestLayout();
                                }
                            } else {
                                wrapper.requestLayout();
                            }
                        } catch (Throwable ignored) {
                        }
                    });
                    return;
                }
            } catch (NoSuchMethodException ns) {
                // handler not present, fall back to direct removal
            } catch (Throwable t) {
                logger.debug("Error invoking MenuActionHandler.handleRemove", t);
            }

            // 2) Fallback: remove from owned collection directly
            try {
                Model.CardsLists.OwnedCardsCollection owned = null;
                try {
                    owned = Model.CardsLists.OuicheList.getMyCardsCollection();
                } catch (Throwable ignored) {
                }
                if (owned == null) {
                    try {
                        Controller.UserInterfaceFunctions.loadCollectionFile();
                    } catch (Throwable ignored) {
                    }
                    try {
                        owned = Model.CardsLists.OuicheList.getMyCardsCollection();
                    } catch (Throwable ignored) {
                    }
                }
                if (owned != null && owned.getOwnedCollection() != null) {
                    outer:
                    for (Model.CardsLists.Box b : owned.getOwnedCollection()) {
                        if (b == null) continue;
                        if (b.getContent() != null) {
                            for (Model.CardsLists.CardsGroup g : b.getContent()) {
                                if (g == null || g.getCardList() == null) continue;
                                Iterator<Model.CardsLists.CardElement> it = g.getCardList().iterator();
                                while (it.hasNext()) {
                                    Model.CardsLists.CardElement ce2 = it.next();
                                    if (ce2 == ce) {
                                        it.remove();
                                        break outer;
                                    }
                                }
                            }
                        }
                        if (b.getSubBoxes() != null) {
                            for (Model.CardsLists.Box sb : b.getSubBoxes()) {
                                if (sb == null || sb.getContent() == null) continue;
                                for (Model.CardsLists.CardsGroup g : sb.getContent()) {
                                    if (g == null || g.getCardList() == null) continue;
                                    Iterator<Model.CardsLists.CardElement> it = g.getCardList().iterator();
                                    while (it.hasNext()) {
                                        Model.CardsLists.CardElement ce2 = it.next();
                                        if (ce2 == ce) {
                                            it.remove();
                                            break outer;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3) Try to save collection if a save helper exists
                try {
                    java.lang.reflect.Method save = Controller.UserInterfaceFunctions.class.getMethod("saveCollectionFile");
                    if (save != null) {
                        try {
                            save.invoke(null);
                        } catch (Throwable ignored) {
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                }

            } catch (Throwable t) {
                logger.debug("Error removing card element directly", t);
            } finally {
                // 4) Refresh UI on FX thread
                Platform.runLater(() -> {
                    try {
                        if (getGridView() != null) {
                            try {
                                java.lang.reflect.Method refresh = getGridView().getClass().getMethod("refresh");
                                refresh.invoke(getGridView());
                            } catch (NoSuchMethodException ns) {
                                getGridView().requestLayout();
                            }
                        } else {
                            wrapper.requestLayout();
                        }
                    } catch (Throwable ignored) {
                    }
                });
            }
        }

        @Override
        protected void updateItem(CardElement cardElement, boolean empty) {
            super.updateItem(cardElement, empty);

            // Clear previous state for empty cells
            if (empty || cardElement == null) {
                cardImageView.setImage(null);
                cardImageView.setEffect(null);
                wrapper.setEffect(null);
                wrapper.setStyle("-fx-background-color: transparent;");
                setGraphic(wrapper);
                return;
            }

            // --- Image loading (unchanged logic) ---
            String imageKey = safeImageKey(cardElement);
            String cachedFullPath = imageKey == null ? null : imagePathCache.get(imageKey);
            if (cachedFullPath != null) {
                Image cached = LruImageCache.getImage(cachedFullPath);
                if (cached != null) {
                    cardImageView.setImage(cached);
                } else {
                    cardImageView.setImage(getPlaceholderImage());
                    Future<?> f = loadImageWithResolvedPathAsync(cardElement, cardImageView, cachedFullPath);
                    if (f != null) outstandingLoads.put(cardImageView, f);
                }
            } else {
                cardImageView.setImage(getPlaceholderImage());
                resolveImagePathAsync(imageKey, resolvedPath -> {
                    if (resolvedPath == null) return;
                    Image cached = LruImageCache.getImage(resolvedPath);
                    if (cached != null) {
                        Platform.runLater(() -> {
                            Object expected = cardImageView.getProperties().get("expectedImagePath");
                            if (Objects.equals(expected, resolvedPath) || expected == null) {
                                cardImageView.setImage(cached);
                                cardImageView.getProperties().remove("expectedImagePath");
                            }
                        });
                    } else {
                        cardImageView.getProperties().put("expectedImagePath", resolvedPath);
                        Future<?> f = loadImageWithResolvedPathAsync(cardElement, cardImageView, resolvedPath);
                        if (f != null) outstandingLoads.put(cardImageView, f);
                    }
                });
            }

            // --- Determine whether this card needs the "unsorted" white glow ---
            boolean needsSorting = false;
            try {
                // Retrieve GridView userData (we stored a Map with "missingSet" and "elementName")
                Object ud = null;
                try {
                    if (this.getGridView() != null) ud = this.getGridView().getUserData();
                } catch (Exception ignored) {
                    ud = null;
                }

                Set<String> missingSet = null;
                String elementNameFromUD = null;

                if (ud instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) ud;
                    Object ms = map.get("missingSet");
                    if (ms instanceof Set) {
                        @SuppressWarnings("unchecked")
                        Set<String> s = (Set<String>) ms;
                        missingSet = s;
                    } else if (ms instanceof Collection) {
                        Set<String> s = new HashSet<>();
                        for (Object o : (Collection<?>) ms) if (o != null) s.add(o.toString());
                        missingSet = s;
                    }
                    Object en = map.get("elementName");
                    if (en instanceof String) elementNameFromUD = (String) en;
                } else if (ud instanceof Set) {
                    @SuppressWarnings("unchecked")
                    Set<String> s = (Set<String>) ud;
                    missingSet = s;
                } else if (ud instanceof String) {
                    elementNameFromUD = (String) ud;
                }

                // If a missing set exists and indicates this card is missing, use that (archetype glow)
                if (missingSet != null && !missingSet.isEmpty()) {
                    String konamiId = cardElement.getCard() == null ? null : cardElement.getCard().getKonamiId();
                    String passCode = cardElement.getCard() == null ? null : cardElement.getCard().getPassCode();
                    boolean missing = false;
                    if (konamiId != null && missingSet.contains(konamiId)) missing = true;
                    if (!missing && passCode != null && missingSet.contains(passCode)) missing = true;
                    // fallback to legacy global set if needed
                    if (!missing && legacyGlobalMissingSet.contains(konamiId)) missing = true;
                    if (!missing && legacyGlobalMissingSet.contains(passCode)) missing = true;
                    needsSorting = missing;
                } else {
                    // No archetype missing-set -> try elementName + computeCardNeedsSorting
                    String elementName = elementNameFromUD;
                    boolean isFirstTabSelected = isFirstTabSelected();
                    if (elementName != null && !elementName.trim().isEmpty() && isFirstTabSelected) {
                        try {
                            needsSorting = Controller.RealMainController.computeCardNeedsSorting(cardElement.getCard(), elementName);
                        } catch (Throwable t) {
                            needsSorting = false;
                        }
                    } else {
                        needsSorting = false;
                    }
                }
            } catch (Exception e) {
                logger.debug("Failed to compute needsSorting in CardGridCell.updateItem", e);
                needsSorting = false;
            }

            // --- Apply glow if needed (on the wrapper StackPane) ---
            if (needsSorting) {
                DropShadow innerGlow = new DropShadow();
                innerGlow.setColor(javafx.scene.paint.Color.web("#ffffff", 1.0));
                innerGlow.setOffsetX(0);
                innerGlow.setOffsetY(0);
                innerGlow.setRadius(4);
                innerGlow.setSpread(0.9);

                DropShadow outerGlow = new DropShadow();
                outerGlow.setColor(javafx.scene.paint.Color.web("#ffffff", 0.22));
                outerGlow.setOffsetX(0);
                outerGlow.setOffsetY(0);
                outerGlow.setRadius(14);
                outerGlow.setSpread(0.12);

                outerGlow.setInput(innerGlow);
                wrapper.setEffect(outerGlow);
            } else {
                wrapper.setEffect(null);
            }

            // --- Apply grayscale for owned cards in OuicheList tab (on the imageView only) ---
            // This is intentionally applied to cardImageView, not wrapper, so it is independent
            // of the glow effect above and does not affect other tabs.
            boolean ouicheSelected = isOuicheListTabSelected();
            boolean owned = cardElement.getOwned() != null && cardElement.getOwned();
            if (ouicheSelected && owned) {
                javafx.scene.effect.ColorAdjust grayscale = new javafx.scene.effect.ColorAdjust();
                grayscale.setSaturation(-1.0);
                cardImageView.setEffect(grayscale);
            } else {
                cardImageView.setEffect(null);
            }

            // --- Selection visual: accent border when selected ---
            boolean selected = isSelected();
            if (selected) {
                wrapper.setStyle("-fx-background-color: transparent; -fx-border-color: #cdfc04; -fx-border-width: 2; -fx-border-radius: 6; -fx-padding: 3;");
            } else {
                wrapper.setStyle("-fx-background-color: transparent;");
            }

            selectedProperty().addListener((obs, oldV, newV) -> {
                if (newV != null && newV) {
                    wrapper.setStyle("-fx-background-color: transparent; -fx-border-color: #cdfc04; -fx-border-width: 2; -fx-border-radius: 6; -fx-padding: 3;");
                } else {
                    wrapper.setStyle("-fx-background-color: transparent;");
                }
            });

            // Finalize graphic
            setGraphic(wrapper);
        }
    }

}
