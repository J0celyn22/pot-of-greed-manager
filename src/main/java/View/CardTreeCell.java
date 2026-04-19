package View;

import Controller.MenuActionHandler;
import Controller.UserInterfaceFunctions;
import Model.CardsLists.*;
import Model.Database.DataBaseUpdate;
import Utils.LruImageCache;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
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

    // ── Static registries ──────────────────────────────────────────────────────
    // WeakHashMap: entries are GC'd when the CardsGroup itself is no longer referenced.
    private static final java.util.WeakHashMap<CardsGroup, javafx.collections.ObservableList<CardElement>>
            GROUP_OBSERVABLE_LISTS = new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<CardsGroup, java.lang.ref.WeakReference<GridView<CardElement>>>
            GROUP_GRID_VIEWS = new java.util.WeakHashMap<>();
    /**
     * The StackPane wrapper inside every GridCell has Insets(5) padding on each side,
     * adding 2×5 = 10 px to both the rendered cell width and height beyond the bound
     * cardWidth / cardHeight properties.
     */
    private static final double CELL_INNER_PADDING = 5.0;

    /**
     * Returns (or creates) the ObservableList that backs the GridView for {@code group}.
     * Adding/removing through this list updates both the model and the GridView automatically.
     */
    public static javafx.collections.ObservableList<CardElement> observableListFor(CardsGroup group) {
        if (group == null) return javafx.collections.FXCollections.observableArrayList();
        return GROUP_OBSERVABLE_LISTS.computeIfAbsent(group, g -> {
            java.util.List<CardElement> backing = g.getCardList();
            if (backing == null) {
                backing = new java.util.ArrayList<>();
                g.setCardList(backing);
            }
            return javafx.collections.FXCollections.observableList(backing);
        });
    }

    /**
     * Recomputes the prefHeight of the GridView currently rendering {@code group}.
     * No-ops silently if the group's grid is not visible or has been GC'd.
     */
    public static void triggerHeightAdjustment(CardsGroup group) {
        if (group == null) return;
        javafx.application.Platform.runLater(() -> {
            java.lang.ref.WeakReference<GridView<CardElement>> ref = GROUP_GRID_VIEWS.get(group);
            if (ref == null) return;
            GridView<CardElement> grid = ref.get();
            if (grid == null) {
                GROUP_GRID_VIEWS.remove(group);
                return;
            }
            int numItems = group.getCardList() == null ? 0 : group.getCardList().size();
            adjustGridViewHeightStatic(grid, numItems);
        });
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

                } else if (dataObject instanceof Model.CardsLists.ThemeCollection) {
                    // ── Collection header row ──────────────────────────────────
                    // Shown in the Decks & Collections and OuicheList tree.
                    // Right-click: "Add Deck" + "Add Archetype"
                    Label label = new Label(itemName);
                    label.getStyleClass().add("tree-item-label");
                    setGraphic(label);

                    if (isDecksAndCollectionsTabSelected()) {
                        ContextMenu collectionCm = NavigationContextMenuBuilder.styledContextMenu();
                        collectionCm.getItems().addAll(
                                NavigationContextMenuBuilder.makeItem("Add Deck"),
                                NavigationContextMenuBuilder.makeItem("Add archetype")
                        );
                        label.setOnContextMenuRequested(e -> {
                            collectionCm.show(label, e.getScreenX(), e.getScreenY());
                            e.consume();
                        });
                    }

                } else if ("ARCHETYPES_SECTION".equals(dataObject)) {
                    // ── "Archetypes" section header ────────────────────────────
                    // Right-click: "Add" (add a new archetype to this collection)
                    Label label = new Label(itemName);
                    label.getStyleClass().add("tree-item-label");
                    setGraphic(label);

                    if (isDecksAndCollectionsTabSelected()) {
                        ContextMenu archetypesSectionCm = NavigationContextMenuBuilder.styledContextMenu();
                        archetypesSectionCm.getItems().add(
                                NavigationContextMenuBuilder.makeItem("Add")
                        );
                        label.setOnContextMenuRequested(e -> {
                            archetypesSectionCm.show(label, e.getScreenX(), e.getScreenY());
                            e.consume();
                        });
                    }

                } else {
                    // Default: plain label (section headers like "Decks", deck names, etc.)
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
     * Removes the leading "* " dirty marker from a tab label if present.
     */
    private static String stripDirtyPrefix(String tabText) {
        if (tabText == null) return "";
        String trimmed = tabText.trim();
        return trimmed.startsWith("* ") ? trimmed.substring(2).trim() : trimmed;
    }

    /**
     * Helper: determine whether the currently selected Tab is the My Collection tab.
     * Uses the TreeView ancestor to find a TabPane and checks the selected Tab text.
     * Returns true only when the selected tab's text equals "My Collection" (case-insensitive).
     */
    private boolean isMyCollectionTabSelected() {
        try {
            Node node = getTreeView();
            while (node != null && !(node instanceof TabPane))
                node = node.getParent();
            if (node instanceof TabPane) {
                Tab sel = ((TabPane) node).getSelectionModel().getSelectedItem();
                if (sel != null) {
                    String cleanText = stripDirtyPrefix(sel.getText());
                    return cleanText.equalsIgnoreCase("My Collection");
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
            while (node != null && !(node instanceof TabPane))
                node = node.getParent();
            if (node instanceof TabPane) {
                Tab sel = ((TabPane) node).getSelectionModel().getSelectedItem();
                if (sel != null) {
                    String cleanText = stripDirtyPrefix(sel.getText());
                    return cleanText.equalsIgnoreCase("OuicheList");
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Helper: determine whether the currently selected Tab is the
     * Decks and Collections tab.
     */
    private boolean isDecksAndCollectionsTabSelected() {
        try {
            Node node = getTreeView();
            while (node != null && !(node instanceof TabPane))
                node = node.getParent();
            if (node instanceof TabPane) {
                Tab sel = ((TabPane) node).getSelectionModel().getSelectedItem();
                if (sel != null) {
                    String cleanText = stripDirtyPrefix(sel.getText());
                    return cleanText.equalsIgnoreCase("Decks and Collections")
                            || cleanText.equalsIgnoreCase("Decks & Collections");
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Inner-class helper: returns true when the grid view this cell belongs
     * to renders cards from an archetype group.
     * Reads the "isArchetype" flag stored in the GridView's userData map by
     * createCardsGroupCell().
     */
    private boolean isInArchetypeGroup() {
        try {
            // CardGridCell (inner class) calls this through the outer CardTreeCell instance.
            // We access cardGridView which is a field of the outer CardTreeCell.
            if (cardGridView == null) return false;
            Object ud = cardGridView.getUserData();
            if (ud instanceof Map) {
                Object ia = ((Map<?, ?>) ud).get("isArchetype");
                return ia instanceof Boolean && (Boolean) ia;
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

    /**
     * Static counterpart of adjustGridViewHeight, driven by the grid's own bound properties.
     */
    private static void adjustGridViewHeightStatic(GridView<CardElement> grid, int numItems) {
        if (grid == null) return;
        if (numItems <= 0) {
            applyGridPrefHeight(grid, 0);
            return;
        }
        applyGridPrefHeight(grid, computeGridPrefHeight(grid, numItems));
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
     * Correct column count: uses the ACTUAL rendered cell width (cardWidth + 2×padding).
     */
    private static int computeGridColumns(GridView<CardElement> grid) {
        double totalW = grid.getWidth();
        if (totalW <= 0) totalW = grid.getPrefWidth();
        if (totalW <= 0) return 1;

        Insets pad = grid.getPadding();
        double padLR = (pad != null) ? pad.getLeft() + pad.getRight() : 0;
        double innerW = totalW - padLR;

        double cardW = grid.getCellWidth();
        double hSpace = grid.getHorizontalCellSpacing();
        double actualCellW = cardW + 2 * CELL_INNER_PADDING;   // e.g. 100 + 10 = 110

        int cols = (int) Math.max(1,
                Math.floor((innerW + hSpace) / (actualCellW + hSpace)));

        /*org.slf4j.LoggerFactory.getLogger(CardTreeCell.class).debug(
                "[GridView-cols] totalW={}  padLR={}  innerW={}  cardW={}  actualCellW={}  hSpace={}  cols={}",
                String.format("%.1f", totalW),
                String.format("%.1f", padLR),
                String.format("%.1f", innerW),
                String.format("%.1f", cardW),
                String.format("%.1f", actualCellW),
                String.format("%.1f", hSpace),
                cols);*/

        return cols;
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
                if (label.startsWith("Add ")) {
                    // Strip "Add " to get the category name to create
                    final String catName = label.substring(4).trim();
                    mi.setOnAction(ev ->
                            MenuActionHandler.handleAddCategoryAndMove(clickedElement, catName));
                } else if (!label.startsWith("Swap")) {
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

    // Helper: sanitize display names (strip only leading/trailing decoration chars)
    private String sanitizeDisplayName(String raw) {
        if (raw == null) return "";
        // Use extractName with '=' first to strip box-level decoration,
        // then with '-' to strip category-level decoration.
        // Since stored names are already clean, this is effectively a no-op
        // for clean names but correctly handles any residual decoration.
        String s = raw.trim();
        int start = 0;
        while (start < s.length() && (s.charAt(start) == '=' || s.charAt(start) == '-')) start++;
        int end = s.length();
        while (end > start && (s.charAt(end - 1) == '=' || s.charAt(end - 1) == '-')) end--;
        return s.substring(start, end).trim();
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

    /**
     * Correct preferred height: uses the ACTUAL rendered row span
     * (cardHeight + 2×padding + verticalSpacing).
     */
    private static double computeGridPrefHeight(GridView<CardElement> grid, int numItems) {
        if (numItems <= 0) return 0;

        int cols = computeGridColumns(grid);
        int rows = (int) Math.ceil((double) numItems / cols);
        Insets pad = grid.getPadding();
        double top = (pad != null) ? pad.getTop() : 0;
        double bottom = (pad != null) ? pad.getBottom() : 0;
        double cardH = grid.getCellHeight();
        double vSpace = grid.getVerticalCellSpacing();
        double actualCellH = cardH + 2 * CELL_INNER_PADDING;  // e.g. 146 + 10 = 156
        double rowSpan = actualCellH + vSpace;              // e.g. 156 + 5  = 161
        double h = top + bottom + rows * rowSpan + 1.0;

        /*org.slf4j.LoggerFactory.getLogger(CardTreeCell.class).debug(
                "[GridView-h] items={}  cols={}  rows={}  cardH={}  actualCellH={}  "
                        + "vSpace={}  rowSpan={}  top={}  bot={}  h={}",
                numItems, cols, rows,
                String.format("%.1f", cardH),
                String.format("%.1f", actualCellH),
                String.format("%.1f", vSpace),
                String.format("%.1f", rowSpan),
                String.format("%.1f", top),
                String.format("%.1f", bottom),
                String.format("%.2f", h));*/

        return h;
    }

    private static void applyGridPrefHeight(GridView<CardElement> grid, double h) {
        grid.setPrefHeight(h);
        grid.setMinHeight(h);
        grid.setMaxHeight(h);
    }

    /**
     * Collects all Card objects from the entire TreeView in display order
     * (depth-first traversal, collecting only from CardsGroup nodes).
     * Enables SHIFT+click range selection that spans multiple groups/lists.
     */
    public static List<Card> collectAllCardsInTreeOrder(TreeItem<String> rootItem) {
        List<Card> allCards = new ArrayList<>();
        if (rootItem == null) return allCards;
        collectCardsRecursive(rootItem, allCards);
        return allCards;
    }

    /**
     * Collects all CardElement objects from the entire TreeView in display order.
     * Enables SHIFT+click range selection spanning multiple groups.
     */
    public static List<CardElement> collectAllElementsInTreeOrder(TreeItem<String> rootItem) {
        List<CardElement> allElements = new ArrayList<>();
        if (rootItem == null) return allElements;
        collectElementsRecursive(rootItem, allElements);
        return allElements;
    }

    private static void collectElementsRecursive(TreeItem<String> treeItem, List<CardElement> result) {
        if (treeItem == null) return;
        if (treeItem instanceof DataTreeItem) {
            Object data = ((DataTreeItem<?>) treeItem).getData();
            CardsGroup cardsGroup = null;
            if (data instanceof CardsGroup) {
                cardsGroup = (CardsGroup) data;
            } else if (data instanceof Map) {
                Object groupObj = ((Map<?, ?>) data).get("group");
                if (groupObj instanceof CardsGroup) cardsGroup = (CardsGroup) groupObj;
            }
            if (cardsGroup != null) {
                List<CardElement> cardList = cardsGroup.getCardList();
                if (cardList != null) result.addAll(cardList);
                return;
            }
        }
        for (TreeItem<String> child : treeItem.getChildren()) {
            collectElementsRecursive(child, result);
        }
    }

    public static void collectCardsRecursive(TreeItem<String> treeItem, List<Card> result) {
        if (treeItem == null) return;

        if (treeItem instanceof DataTreeItem) {
            Object data = ((DataTreeItem<?>) treeItem).getData();
            CardsGroup cardsGroup = null;
            if (data instanceof CardsGroup) {
                cardsGroup = (CardsGroup) data;
            } else if (data instanceof Map) {
                Object groupObj = ((Map<?, ?>) data).get("group");
                if (groupObj instanceof CardsGroup) {
                    cardsGroup = (CardsGroup) groupObj;
                }
            }
            if (cardsGroup != null) {
                List<CardElement> cardList = cardsGroup.getCardList();
                if (cardList != null) {
                    for (CardElement cardElement : cardList) {
                        if (cardElement != null && cardElement.getCard() != null) {
                            result.add(cardElement.getCard());
                        }
                    }
                }
                return; // Leaf group node — don't recurse into visual children
            }
        }
        for (TreeItem<String> child : treeItem.getChildren()) {
            collectCardsRecursive(child, result);
        }
    }

    /**
     * Marks the owner of {@code group} dirty (My Collection, or the specific
     * Deck / ThemeCollection in D&C) and triggers the appropriate view refresh.
     */
    static void markDirtyAndRefreshForGroup(CardsGroup group) {
        if (group == null) return;
        // Try D&C owner first
        javafx.collections.ObservableList<CardElement> obs = GROUP_OBSERVABLE_LISTS.get(group);
        Object dacOwner = null;
        if (obs != null) {
            Model.CardsLists.DecksAndCollectionsList dac =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (dac != null) {
                outer:
                for (Model.CardsLists.ThemeCollection tc :
                        dac.getCollections() != null
                                ? dac.getCollections()
                                : java.util.Collections.<Model.CardsLists.ThemeCollection>emptyList()) {
                    if (tc == null) continue;
                    if (obs == tc.getCardsList() || obs == tc.getExceptionsToNotAdd()) {
                        dacOwner = tc;
                        break outer;
                    }
                    if (tc.getLinkedDecks() != null) {
                        for (java.util.List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Model.CardsLists.Deck d : unit) {
                                if (d == null) continue;
                                if (obs == d.getMainDeck()
                                        || obs == d.getExtraDeck()
                                        || obs == d.getSideDeck()) {
                                    dacOwner = d;
                                    break outer;
                                }
                            }
                        }
                    }
                }
                if (dacOwner == null && dac.getDecks() != null) {
                    for (Model.CardsLists.Deck d : dac.getDecks()) {
                        if (d == null) continue;
                        if (obs == d.getMainDeck()
                                || obs == d.getExtraDeck()
                                || obs == d.getSideDeck()) {
                            dacOwner = d;
                            break;
                        }
                    }
                }
            }
        }
        if (dacOwner != null) {
            Controller.UserInterfaceFunctions.markDirty(dacOwner);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
        } else {
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshOwnedCollectionView();
        }
    }

    /**
     * Inserts {@code newElements} into {@code targetList} at {@code insertionIndex}.
     * If {@code sourceElements} is non-null and non-empty, each source element is
     * first removed from its current group (MOVE semantics); otherwise new
     * {@link CardElement} wrappers are created from the cards (ADD semantics).
     *
     * <p>Dirty marking and view refresh are handled by the caller.
     */
    public static void dropInsertIntoGroup(
            CardsGroup targetGroup,
            int insertionIndex,
            java.util.List<Model.CardsLists.CardElement> sourceElements,
            java.util.List<Model.CardsLists.Card> sourceCards) {

        javafx.collections.ObservableList<CardElement> targetList = observableListFor(targetGroup);
        int idx = Math.max(0, Math.min(insertionIndex, targetList.size()));

        if (sourceElements != null && !sourceElements.isEmpty()) {
            // MOVE: remove from sources first (may be in different groups)
            for (CardElement ce : sourceElements) {
                for (java.util.Map.Entry<CardsGroup,
                        javafx.collections.ObservableList<CardElement>> entry
                        : GROUP_OBSERVABLE_LISTS.entrySet()) {
                    if (entry.getValue().remove(ce)) {
                        triggerHeightAdjustment(entry.getKey());
                        break;
                    }
                }
            }
            // Re-compute index in case removals shifted it
            idx = Math.min(idx, targetList.size());
            for (int i = 0; i < sourceElements.size(); i++) {
                int pos = Math.min(idx + i, targetList.size());
                targetList.add(pos, sourceElements.get(i));
            }
        } else if (sourceCards != null && !sourceCards.isEmpty()) {
            // ADD: create new elements
            for (int i = 0; i < sourceCards.size(); i++) {
                Model.CardsLists.Card card = sourceCards.get(i);
                if (card == null) continue;
                int pos = Math.min(idx + i, targetList.size());
                targetList.add(pos, new CardElement(card));
            }
        }
        triggerHeightAdjustment(targetGroup);
    }

    /**
     * Given a local X coordinate inside a {@link GridView} cell and the cell width,
     * returns {@code true} if the point is on the right half of the card image
     * (i.e. insert AFTER), {@code false} for the left half (insert BEFORE).
     */
    public static boolean isRightHalf(double localX, double cardWidth) {
        return localX >= (cardWidth + 2 * CELL_INNER_PADDING) / 2.0;
    }

    /**
     * Finds the CardsGroup whose observable list contains the given CardElement,
     * by searching the live GROUP_OBSERVABLE_LISTS registry.
     */

    /**
     * Computes the insertion index inside {@code group}'s list when the user
     * drops between cards at pixel position {@code gridLocalX, gridLocalY} inside
     * the {@link GridView}.
     *
     * <p>Returns -1 when the drop position is outside the card rows entirely
     * (e.g. below the last row).
     */
    public static int computeGapInsertionIndex(
            GridView<CardElement> grid, CardsGroup group,
            double gridLocalX, double gridLocalY) {
        if (group == null || group.getCardList() == null) return -1;
        int n = group.getCardList().size();
        if (n == 0) return 0;

        Insets pad = grid.getPadding();
        double padL = pad != null ? pad.getLeft() : 0;
        double padT = pad != null ? pad.getTop() : 0;
        double hSpace = grid.getHorizontalCellSpacing();
        double vSpace = grid.getVerticalCellSpacing();
        double cellW = grid.getCellWidth() + 2 * CELL_INNER_PADDING;
        double cellH = grid.getCellHeight() + 2 * CELL_INNER_PADDING;
        int cols = computeGridColumns(grid);

        double x = gridLocalX - padL;
        double y = gridLocalY - padT;
        if (x < 0 || y < 0) return 0;

        int col = (int) Math.floor(x / (cellW + hSpace));
        int row = (int) Math.floor(y / (cellH + vSpace));

        double colFrac = (x - col * (cellW + hSpace)) / cellW;
        // If click is in the spacing gap between columns, treat as right-half of left card
        boolean inHGap = colFrac > 1.0;
        col = Math.min(col, cols - 1);

        int flatIndex = row * cols + col;
        if (flatIndex >= n) return -1; // below last row content

        // If in the right half (or in a gap), insert after this card
        if (inHGap || colFrac >= 0.5) flatIndex++;
        return Math.min(flatIndex, n);
    }

    // ── Drop execution helpers (called from drag-drop handlers) ────────────────

    public static CardsGroup findGroupForCardElement(CardElement targetElement) {
        if (targetElement == null) return null;
        for (java.util.Map.Entry<CardsGroup,
                javafx.collections.ObservableList<CardElement>> entry
                : GROUP_OBSERVABLE_LISTS.entrySet()) {
            if (entry.getValue().contains(targetElement)) return entry.getKey();
        }
        return null;
    }

    /**
     * Calls refresh() on every GridView currently registered in GROUP_GRID_VIEWS.
     * This propagates selection-state changes into nested GridViews that a plain
     * TreeView.refresh() call does not reach.
     */
    public static void refreshAllGridViews() {
        Platform.runLater(() -> {
            for (java.lang.ref.WeakReference<GridView<CardElement>> ref : GROUP_GRID_VIEWS.values()) {
                GridView<CardElement> grid = ref.get();
                if (grid != null) {
                    javafx.collections.ObservableList<CardElement> items = grid.getItems();
                    grid.setItems(null);
                    grid.setItems(items);
                }
            }
        });
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
        boolean isArchetype;
        String displayName = rawGroupName;
        if (rawGroupName.startsWith(ARCHETYPE_MARKER)) {
            isArchetype = true;
            displayName = rawGroupName.substring(ARCHETYPE_MARKER.length());
        } else {
            isArchetype = false;
        }
        displayName = Model.CardsLists.OwnedCardsCollection.extractName(displayName, '-');

        Label label = new Label(displayName);
        label.getStyleClass().add("card-group-label");

        hbox.getChildren().addAll(customTriangleLabel, label);

        VBox vBox = new VBox();
        vBox.getStyleClass().add("card-group-vbox");
        vBox.getChildren().add(hbox);

        GridView<CardElement> grid = new GridView<>();
        grid.getStyleClass().add("card-grid-view");
        grid.setCellFactory(gridView -> new CardGridCell());
        javafx.collections.ObservableList<CardElement> groupItems = observableListFor(group);
        grid.setItems(groupItems);
        // Register as the current renderer so triggerHeightAdjustment() can find it.
        GROUP_GRID_VIEWS.put(group, new java.lang.ref.WeakReference<>(grid));
        grid.cellWidthProperty().bind(cardWidthProperty);
        grid.cellHeightProperty().bind(cardHeightProperty);
        grid.setHorizontalCellSpacing(6);
        grid.setVerticalCellSpacing(6);
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
        ud.put("isArchetype", isArchetype);
        grid.setUserData(ud);

        this.cardGridView = grid;

        // Initial call: best-effort using prefWidth (grid not yet laid out)
        adjustGridViewHeight(group);
        // Deferred correction: re-runs after the first layout pass when grid.getWidth() is valid
        Platform.runLater(() -> adjustGridViewHeight(group));

        // Listeners: deferred so grid.getWidth() reflects the new size after layout
        cardWidthProperty.addListener((obs, oldVal, newVal) ->
                Platform.runLater(() -> adjustGridViewHeight(group)));
        getTreeView().widthProperty().addListener((obs, oldVal, newVal) ->
                Platform.runLater(() -> adjustGridViewHeight(group)));

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

        // ── GridView drop target: between-card gaps ───────────────────────────────
        // The wrapper inside each CardGridCell consumes card-level events; the
        // GridView only receives events that land in gaps between or below cards.
        grid.setOnDragOver(event -> {
            if (!isArchetype
                    && event.getDragboard().hasString()
                    && Controller.DragDropManager.getDragSourcePane() != null) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            }
            event.consume();
        });

        grid.setOnDragDropped(event -> {
            if (isArchetype) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            int insertionIndex = computeGapInsertionIndex(
                    grid, group, event.getX(), event.getY());
            if (insertionIndex < 0) {
                // Click is outside the card rows — treat as append
                insertionIndex = group.getCardList() == null ? 0 : group.getCardList().size();
            }

            String srcPane = Controller.DragDropManager.getDragSourcePane();
            if (srcPane == null) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            boolean isMiddle = "MIDDLE".equals(srcPane);
            if (isMiddle) {
                java.util.List<CardElement> srcElements =
                        new java.util.ArrayList<>(Controller.DragDropManager.getDraggedElements());
                dropInsertIntoGroup(group, insertionIndex, srcElements, null);
            } else {
                java.util.List<Model.CardsLists.Card> srcCards =
                        new java.util.ArrayList<>(Controller.DragDropManager.getDraggedCards());
                dropInsertIntoGroup(group, insertionIndex, null, srcCards);
            }
            markDirtyAndRefreshForGroup(group);
            event.setDropCompleted(true);
            event.consume();
        });

        setGraphic(vBox);

        // For Decks & Collections tab only; different menu per group type.
        hbox.setOnContextMenuRequested(event -> {
            if (!isDecksAndCollectionsTabSelected()) {
                event.consume();
                return;
            }

            ContextMenu headerCm;

            if (isArchetype) {
                // Archetype group header  →  "Remove" (red + trash icon)
                headerCm = NavigationContextMenuBuilder.styledContextMenu();
                headerCm.getItems().add(NavigationContextMenuBuilder.makeRemoveItem());
            } else {
                // Regular group header (Cards, Main Deck, etc.) — no menu for now
                event.consume();
                return;
            }

            headerCm.show(hbox, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private void adjustGridViewHeight(CardsGroup group) {
        if (cardGridView == null) return;
        int numItems = group.getCardList() == null ? 0 : group.getCardList().size();
        if (numItems <= 0) {
            applyGridPrefHeight(cardGridView, 0);
            return;
        }
        applyGridPrefHeight(cardGridView, computeGridPrefHeight(cardGridView, numItems));
        logger.debug("Adjusted grid view height to {} for {} items",
                cardGridView.getPrefHeight(), numItems);
    }
    // Add this static method to CardTreeCell, after the existing refreshAllGridViews-like statics:

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
            wrapper.getProperties().put("cardWrapper", Boolean.TRUE);
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
                sortLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-weight: normal; -fx-font-size: 13;");

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
                moveLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-weight: normal; -fx-font-size: 13;");

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
                                List<CardElement> elementsToMove = getEffectiveMiddleElements();
                                if (elementsToMove.size() > 1) {
                                    Controller.MenuActionHandler.handleBulkMove(
                                            new ArrayList<>(elementsToMove), handlerTarget);
                                } else {
                                    Controller.MenuActionHandler.handleMove(getItem(), handlerTarget);
                                }
                            } catch (Throwable throwable) {
                                logger.debug("Move action failed for target {}", handlerTarget, throwable);
                            }
                        });
                        return mi;
                    };

                    // Iterate owned boxes and their groups to create menu entries.
                    // We exclude ONLY the exact current group; box-level destinations are always allowed.
                    for (Model.CardsLists.Box b : owned.getOwnedCollection()) {
                        if (b == null) continue;
                        String boxName = Model.CardsLists.OwnedCardsCollection.extractName(b.getName() == null ? "" : b.getName(), '=');
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

                                String groupName = Model.CardsLists.OwnedCardsCollection.extractName(rawName, '-');
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
                                String subBoxName = Model.CardsLists.OwnedCardsCollection.extractName(sb.getName() == null ? "" : sb.getName(), '=');
                                if (subBoxName.isEmpty()) subBoxName = "(Unnamed sub-box)";

                                // Always offer sub-box-level destination too
                                MenuItem miSubBox = makeMoveItem.apply(subBoxName, subBoxName);
                                moveToMenu.getItems().add(miSubBox);

                                if (sb.getContent() != null) {
                                    for (Model.CardsLists.CardsGroup g : sb.getContent()) {
                                        if (g == null) continue;
                                        String groupName = Model.CardsLists.OwnedCardsCollection.extractName(g.getName() == null ? "" : g.getName(), '-');
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

            // ── Copy ──────────────────────────────────────────────────────────────────────
            MenuItem copyForCollectionMenuItem = new MenuItem();
            {
                Label copyLabel = new Label("Copy");
                copyLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
                HBox copyGraphic = new HBox(copyLabel);
                copyGraphic.setAlignment(Pos.CENTER_LEFT);
                copyGraphic.setPadding(new Insets(2, 6, 2, 6));
                copyForCollectionMenuItem.setGraphic(copyGraphic);
                copyForCollectionMenuItem.setText("");
                copyForCollectionMenuItem.setOnAction(ae -> executeCopyAction());
            }
            contextMenu.getItems().add(copyForCollectionMenuItem);

            // ── Cut ───────────────────────────────────────────────────────────────────────
            MenuItem cutForCollectionMenuItem = new MenuItem();
            {
                Label cutLabel = new Label("Cut");
                cutLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
                HBox cutGraphic = new HBox(cutLabel);
                cutGraphic.setAlignment(Pos.CENTER_LEFT);
                cutGraphic.setPadding(new Insets(2, 6, 2, 6));
                cutForCollectionMenuItem.setGraphic(cutGraphic);
                cutForCollectionMenuItem.setText("");
                cutForCollectionMenuItem.setOnAction(ae -> executeCutFromOwnedCollectionAction());
            }
            contextMenu.getItems().add(cutForCollectionMenuItem);

            // ── Paste ─────────────────────────────────────────────────────────────────────
            final MenuItem pasteAfterCardMenuItem = new MenuItem();
            {
                Label pasteLabel = new Label("Paste");
                pasteLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
                HBox pasteGraphic = new HBox(pasteLabel);
                pasteGraphic.setAlignment(Pos.CENTER_LEFT);
                pasteGraphic.setPadding(new Insets(2, 6, 2, 6));
                pasteAfterCardMenuItem.setGraphic(pasteGraphic);
                pasteAfterCardMenuItem.setText("");
                pasteAfterCardMenuItem.setVisible(false);
                pasteAfterCardMenuItem.setOnAction(ae -> {
                    if (Controller.CardClipboard.isEmpty()) return;
                    CardElement currentItem = getItem();
                    if (currentItem == null) return;
                    Controller.MenuActionHandler.handlePasteAfterElementInOwnedCollection(
                            Controller.CardClipboard.getContents(), currentItem);
                });
            }
            contextMenu.getItems().add(pasteAfterCardMenuItem);

            // ── Remove ────────────────────────────────────────────────────────────────────
            MenuItem removeRootItem = new MenuItem();
            Label removeTrashIcon = new Label("\uD83D\uDDD1");
            removeTrashIcon.setStyle("-fx-text-fill: #ff4d4d; -fx-font-size: 13;");
            Label removeLabel = new Label("Remove");
            removeLabel.setStyle("-fx-text-fill: #ff4d4d; -fx-font-weight: bold;");
            HBox removeGraphic = new HBox(6, removeTrashIcon, removeLabel);
            removeGraphic.setAlignment(Pos.CENTER_LEFT);
            removeGraphic.setPadding(new Insets(2, 6, 2, 6));
            removeRootItem.setGraphic(removeGraphic);
            removeRootItem.setText("");
            removeRootItem.setOnAction(ae -> {
                CardElement currentItem = getItem();
                if (currentItem == null) return;
                List<CardElement> elementsToRemove = getEffectiveMiddleElements();
                if (elementsToRemove.size() > 1) {
                    Controller.MenuActionHandler.handleBulkRemoveFromOwnedCollection(
                            new ArrayList<>(elementsToRemove));
                } else {
                    removeCardElement(currentItem);
                    Controller.UserInterfaceFunctions.refreshOwnedCollectionView();
                }
            });

            contextMenu.getItems().add(new SeparatorMenuItem());
            contextMenu.getItems().add(removeRootItem);

            // Keep the Remove item disabled when no card is selected; update on showing
            contextMenu.setOnShowing(ev -> {
                CardElement currentItem = getItem();
                boolean multiSelect = isMiddleMultiSelectActive();
                sortingMenu.setVisible(!multiSelect);
                removeRootItem.setDisable(currentItem == null);
                pasteAfterCardMenuItem.setVisible(!Controller.CardClipboard.isEmpty());
            });

            // ── Context menu for Decks & Collections tab — NON-archetype cards ──
            ContextMenu decksContextMenu = new ContextMenu();
            decksContextMenu.setStyle(
                    "-fx-background-color: #100317; -fx-background-radius: 6; " +
                            "-fx-border-color: #3a3a3a; -fx-border-radius: 6; -fx-border-width: 1;"
            );
            {
                // "Move to..." submenu (lazy-populated placeholder — actions implemented later)
                Menu decksMoveMenu = new Menu();
                {
                    Label ml = new Label("Move to...");
                    ml.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
                    HBox mg = new HBox(ml);
                    mg.setAlignment(Pos.CENTER_LEFT);
                    mg.setPadding(new Insets(2, 6, 2, 6));
                    decksMoveMenu.setGraphic(mg);
                    decksMoveMenu.setText("");
                    MenuItem placeholder = new MenuItem("Loading...");
                    placeholder.setDisable(true);
                    decksMoveMenu.getItems().add(placeholder);
                    decksMoveMenu.setOnShowing(evt -> {
                        decksMoveMenu.getItems().clear();
                        String currentPath = findCurrentLocationPath();
                        List<MenuItem> items = buildMoveDestinationMenuItems(currentPath);
                        if (items.isEmpty()) {
                            MenuItem none = new MenuItem("No destinations available");
                            none.setDisable(true);
                            decksMoveMenu.getItems().add(none);
                        } else {
                            decksMoveMenu.getItems().addAll(items);
                        }
                    });
                }
                decksContextMenu.getItems().add(decksMoveMenu);

                // ── Copy (D&C) ────────────────────────────────────────────────────────────────
                MenuItem decksCopyMenuItem = new MenuItem();
                {
                    Label lbl = new Label("Copy");
                    lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
                    HBox g = new HBox(lbl);
                    g.setAlignment(Pos.CENTER_LEFT);
                    g.setPadding(new Insets(2, 6, 2, 6));
                    decksCopyMenuItem.setGraphic(g);
                    decksCopyMenuItem.setText("");
                    decksCopyMenuItem.setOnAction(ae -> executeCopyAction());
                }
                decksContextMenu.getItems().add(decksCopyMenuItem);

                // ── Cut (D&C) ─────────────────────────────────────────────────────────────────
                MenuItem decksCutMenuItem = new MenuItem();
                {
                    Label lbl = new Label("Cut");
                    lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
                    HBox g = new HBox(lbl);
                    g.setAlignment(Pos.CENTER_LEFT);
                    g.setPadding(new Insets(2, 6, 2, 6));
                    decksCutMenuItem.setGraphic(g);
                    decksCutMenuItem.setText("");
                    decksCutMenuItem.setOnAction(ae -> executeCutFromDecksAction());
                }
                decksContextMenu.getItems().add(decksCutMenuItem);

                // ── Paste (D&C) ───────────────────────────────────────────────────────────────
                MenuItem decksPasteMenuItem = new MenuItem();
                {
                    Label lbl = new Label("Paste");
                    lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
                    HBox g = new HBox(lbl);
                    g.setAlignment(Pos.CENTER_LEFT);
                    g.setPadding(new Insets(2, 6, 2, 6));
                    decksPasteMenuItem.setGraphic(g);
                    decksPasteMenuItem.setText("");
                    decksPasteMenuItem.setOnAction(ae -> {
                        if (Controller.CardClipboard.isEmpty()) return;
                        CardElement currentItem = getItem();
                        if (currentItem == null) return;
                        @SuppressWarnings("unchecked")
                        javafx.collections.ObservableList<CardElement> deckGroupItems =
                                (javafx.collections.ObservableList<CardElement>) getGridView().getItems();
                        int insertionIndex = deckGroupItems.indexOf(currentItem);
                        if (insertionIndex < 0) insertionIndex = deckGroupItems.size() - 1;
                        List<Model.CardsLists.Card> clipboardCards = Controller.CardClipboard.getContents();
                        for (int i = 0; i < clipboardCards.size(); i++) {
                            Model.CardsLists.Card card = clipboardCards.get(i);
                            if (card == null) continue;
                            int targetIndex = insertionIndex + 1 + i;
                            if (targetIndex > deckGroupItems.size()) targetIndex = deckGroupItems.size();
                            deckGroupItems.add(targetIndex, new CardElement(card));
                        }
                        for (Map.Entry<Model.CardsLists.CardsGroup,
                                javafx.collections.ObservableList<CardElement>> entry
                                : GROUP_OBSERVABLE_LISTS.entrySet()) {
                            if (entry.getValue() == deckGroupItems) {
                                CardTreeCell.triggerHeightAdjustment(entry.getKey());
                                // Mark only the owner of this specific list dirty
                                Object pasteOwner = findDacOwnerForCardsGroup(entry.getKey());
                                if (pasteOwner != null)
                                    Controller.UserInterfaceFunctions.markDirty(pasteOwner);
                                else
                                    Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                                break;
                            }
                        }
                        Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                        Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
                    });
                }
                decksContextMenu.getItems().add(decksPasteMenuItem);

                decksContextMenu.getItems().add(new SeparatorMenuItem());

                // "Remove" (red + trash icon)
                MenuItem decksRemoveItem = new MenuItem();
                Label decksTrash = new Label("\uD83D\uDDD1");
                decksTrash.setStyle("-fx-text-fill: #ff4d4d; -fx-font-size: 13;");
                Label decksRemoveLabel = new Label("Remove");
                decksRemoveLabel.setStyle("-fx-text-fill: #ff4d4d; -fx-font-weight: bold;");
                HBox decksRemoveGraphic = new HBox(6, decksTrash, decksRemoveLabel);
                decksRemoveGraphic.setAlignment(Pos.CENTER_LEFT);
                decksRemoveGraphic.setPadding(new Insets(2, 6, 2, 6));
                decksRemoveItem.setGraphic(decksRemoveGraphic);
                decksRemoveItem.setText("");
                decksRemoveItem.setOnAction(ae -> {
                    CardElement currentItem = getItem();
                    if (currentItem == null) return;
                    if (isMiddleMultiSelectActive()) {
                        Controller.MenuActionHandler.handleBulkRemoveElementsFromDecksAndCollections(
                                new ArrayList<>(Controller.SelectionManager.getSelectedMiddleElements()));
                        // Remove all selected cards from every list in D&C,
                        // marking only those that actually changed as dirty.
                        /*java.util.Set<Model.CardsLists.Card> cardsToRemove =
                                Controller.SelectionManager.getSelectedCards();
                        java.util.function.Predicate<List<CardElement>> removeMatchingCards = (list) -> {
                            if (list == null) return false;
                            return list.removeIf(ce -> ce != null && ce.getCard() != null
                                    && cardsToRemove.contains(ce.getCard()));
                        };
                        java.util.Set<Object> dirtyOwners = new java.util.LinkedHashSet<>();
                        Model.CardsLists.DecksAndCollectionsList dac =
                                Controller.UserInterfaceFunctions.getDecksList();
                        if (dac != null) {
                            if (dac.getCollections() != null) {
                                for (Model.CardsLists.ThemeCollection themeCollection : dac.getCollections()) {
                                    if (themeCollection == null) continue;
                                    boolean tcChanged =
                                            removeMatchingCards.test(themeCollection.getCardsList())
                                                    | removeMatchingCards.test(themeCollection.getExceptionsToNotAdd());
                                    if (tcChanged) dirtyOwners.add(themeCollection);
                                    if (themeCollection.getLinkedDecks() != null) {
                                        for (List<Model.CardsLists.Deck> unit : themeCollection.getLinkedDecks()) {
                                            if (unit == null) continue;
                                            for (Model.CardsLists.Deck deck : unit) {
                                                if (deck == null) continue;
                                                boolean changed =
                                                        removeMatchingCards.test(deck.getMainDeck())
                                                                | removeMatchingCards.test(deck.getExtraDeck())
                                                                | removeMatchingCards.test(deck.getSideDeck());
                                                if (changed) dirtyOwners.add(deck);
                                            }
                                        }
                                    }
                                }
                            }
                            if (dac.getDecks() != null) {
                                for (Model.CardsLists.Deck deck : dac.getDecks()) {
                                    if (deck == null) continue;
                                    boolean changed =
                                            removeMatchingCards.test(deck.getMainDeck())
                                                    | removeMatchingCards.test(deck.getExtraDeck())
                                                    | removeMatchingCards.test(deck.getSideDeck());
                                    if (changed) dirtyOwners.add(deck);
                                }
                            }
                        }
                        if (!dirtyOwners.isEmpty()) {
                            for (Object owner : dirtyOwners)
                                Controller.UserInterfaceFunctions.markDirty(owner);
                        } else {
                            // Fallback: nothing removed, but guard against inconsistent state
                            Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                        }
                        Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                        Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();*/
                    } else {
                        removeCardElementFromDecksList(currentItem);
                    }
                });
                decksContextMenu.getItems().add(decksRemoveItem);

                decksContextMenu.setOnShowing(ev -> {
                    decksPasteMenuItem.setVisible(!Controller.CardClipboard.isEmpty());
                });
            }

            // ── Context menu for Decks & Collections tab — ARCHETYPE cards ────
            ContextMenu archetypeCardContextMenu = new ContextMenu();
            archetypeCardContextMenu.setStyle(
                    "-fx-background-color: #100317; -fx-background-radius: 6; " +
                            "-fx-border-color: #3a3a3a; -fx-border-radius: 6; -fx-border-width: 1;"
            );
            {
                // "Add to..." submenu (lazy-populated placeholder — actions implemented later)
                Menu archetypeAddMenu = new Menu();
                {
                    Label al = new Label("Add to...");
                    al.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
                    HBox ag = new HBox(al);
                    ag.setAlignment(Pos.CENTER_LEFT);
                    ag.setPadding(new Insets(2, 6, 2, 6));
                    archetypeAddMenu.setGraphic(ag);
                    archetypeAddMenu.setText("");
                    MenuItem aPlaceholder = new MenuItem("Loading...");
                    aPlaceholder.setDisable(true);
                    archetypeAddMenu.getItems().add(aPlaceholder);
                    archetypeAddMenu.setOnShowing(evt -> {
                        archetypeAddMenu.getItems().clear();
                        // null = no exclusion: archetype cards have no editable location
                        List<MenuItem> items = buildAddDestinationMenuItems();
                        if (items.isEmpty()) {
                            MenuItem none = new MenuItem("No destinations available");
                            none.setDisable(true);
                            archetypeAddMenu.getItems().add(none);
                        } else {
                            archetypeAddMenu.getItems().addAll(items);
                        }
                    });
                }
                archetypeCardContextMenu.getItems().add(archetypeAddMenu);

                // ── Copy (Archetype) ──────────────────────────────────────────────────────────
                MenuItem archetypeCopyMenuItem = new MenuItem();
                {
                    Label lbl = new Label("Copy");
                    lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
                    HBox g = new HBox(lbl);
                    g.setAlignment(Pos.CENTER_LEFT);
                    g.setPadding(new Insets(2, 6, 2, 6));
                    archetypeCopyMenuItem.setGraphic(g);
                    archetypeCopyMenuItem.setText("");
                    archetypeCopyMenuItem.setOnAction(ae -> executeCopyAction());
                }
                archetypeCardContextMenu.getItems().add(archetypeCopyMenuItem);
            }

            // Attach the context menu to the wrapper so right-click shows it
            wrapper.setOnContextMenuRequested(e -> {
                if (isMyCollectionTabSelected()) {
                    // Existing My Collection menu (Sort card + Move to + Remove with trash)
                    contextMenu.show(wrapper, e.getScreenX(), e.getScreenY());

                } else if (isDecksAndCollectionsTabSelected()) {
                    // Determine whether this card lives in an archetype group
                    if (isInArchetypeGroup()) {
                        archetypeCardContextMenu.show(wrapper, e.getScreenX(), e.getScreenY());
                    } else {
                        decksContextMenu.show(wrapper, e.getScreenX(), e.getScreenY());
                    }
                }
                e.consume();
            });

            // ── Selection click handler (MIDDLE pane) ────────────────────────────────────
            wrapper.setOnMouseClicked(event -> {
                if (event.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                    return;
                }
                CardElement clickedCardElement = getItem();
                if (clickedCardElement == null || clickedCardElement.getCard() == null) {
                    event.consume();
                    return;
                }

                // Collect ALL elements from the entire TreeView in display order so that
                // SHIFT+click range selection spans across multiple groups/lists.
                TreeView<String> parentTreeView = CardTreeCell.this.getTreeView();
                java.util.List<CardElement> allElementsInTreeOrder =
                        (parentTreeView != null)
                                ? collectAllElementsInTreeOrder(parentTreeView.getRoot())
                                : new java.util.ArrayList<>();

                if (event.isControlDown()) {
                    Controller.SelectionManager.toggleElementSelection(clickedCardElement);
                } else if (event.isShiftDown()) {
                    Controller.SelectionManager.rangeSelectElements(clickedCardElement, allElementsInTreeOrder);
                } else {
                    Controller.SelectionManager.selectElement(clickedCardElement);
                }
                event.consume();
            });

            // ── Middle-pane drag ──────────────────────────────────────────────────────────
            wrapper.setOnDragDetected(event -> {
                CardElement ce = getItem();
                if (ce == null || ce.getCard() == null) return;

                if (isInArchetypeGroup()) {
                    // Archetype cards are read-only — behave like a right-pane (ADD) drag
                    java.util.List<Card> cards = new java.util.ArrayList<>();
                    cards.add(ce.getCard());
                    java.util.Set<CardElement> sel = Controller.SelectionManager.getSelectedMiddleElements();
                    if ("MIDDLE".equals(Controller.SelectionManager.getActivePart())
                            && sel.size() > 1 && sel.contains(ce)) {
                        for (CardElement el : sel) {
                            if (el != ce && el.getCard() != null) {
                                cards.add(el.getCard());
                                if (cards.size() >= 5) break;
                            }
                        }
                    }
                    java.util.List<Image> ghostImages = new java.util.ArrayList<>();
                    for (Card c : cards) {
                        String key = c.getImagePath();
                        String rp = key != null ? imagePathCache.get(key) : null;
                        ghostImages.add(rp != null ? LruImageCache.getImage(rp) : null);
                    }
                    javafx.scene.input.Dragboard db =
                            wrapper.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
                    javafx.scene.image.WritableImage ghost =
                            Controller.DragDropManager.buildDragGhost(
                                    ghostImages, cardWidthProperty.get(), cardHeightProperty.get());
                    if (ghost != null)
                        db.setDragView(ghost, cardWidthProperty.get() / 2.0, cardHeightProperty.get() / 2.0);
                    javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                    content.putString(ce.getCard().getPassCode());
                    db.setContent(content);
                    Controller.DragDropManager.startRightDrag(cards);
                    event.consume();
                    return;
                }

                // Primary element first, then other selected MIDDLE elements in selection
                // order, capped at 5 total.
                java.util.Set<CardElement> selected =
                        Controller.SelectionManager.getSelectedMiddleElements();
                java.util.List<CardElement> dragElements = new java.util.ArrayList<>();
                dragElements.add(ce);
                if ("MIDDLE".equals(Controller.SelectionManager.getActivePart())
                        && selected.size() > 1 && selected.contains(ce)) {
                    for (CardElement el : selected) {
                        if (el != ce) {
                            dragElements.add(el);
                            if (dragElements.size() >= 5) break;
                        }
                    }
                }

                // Resolve raw card images — look up cached path then LRU cache, no border
                java.util.List<Image> ghostImages = new java.util.ArrayList<>();
                for (CardElement el : dragElements) {
                    Card c = el.getCard();
                    String key = c != null ? c.getImagePath() : null;
                    String resolvedPath = key != null ? imagePathCache.get(key) : null;
                    Image img = resolvedPath != null ? LruImageCache.getImage(resolvedPath) : null;
                    ghostImages.add(img); // null entries are handled in buildDragGhost
                }

                javafx.scene.input.Dragboard db =
                        wrapper.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
                javafx.scene.image.WritableImage ghost =
                        Controller.DragDropManager.buildDragGhost(
                                ghostImages, cardWidthProperty.get(), cardHeightProperty.get());
                if (ghost != null) {
                    db.setDragView(ghost,
                            cardWidthProperty.get() / 2.0,
                            cardHeightProperty.get() / 2.0);
                }

                javafx.scene.input.ClipboardContent content =
                        new javafx.scene.input.ClipboardContent();
                content.putString(ce.getCard().getPassCode());
                db.setContent(content);
                Controller.DragDropManager.startMiddleDrag(dragElements);
                event.consume();
            });

            wrapper.setOnDragDone(event -> {
                Controller.DragDropManager.clearCurrentlyDraggedCard();
                event.consume();
            });

            // ── Drop target: card-level (left half = before, right half = after) ────────
            wrapper.setOnDragOver(event -> {
                if (!isInArchetypeGroup()
                        && event.getDragboard().hasString()
                        && Controller.DragDropManager.getDragSourcePane() != null) {
                    event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
                }
                event.consume();
            });

            wrapper.setOnDragDropped(event -> {
                if (isInArchetypeGroup()) {
                    event.setDropCompleted(false);
                    event.consume();
                    return;
                }
                CardElement anchor = getItem();
                if (anchor == null) {
                    event.setDropCompleted(false);
                    event.consume();
                    return;
                }

                CardsGroup group = findGroupForCardElement(anchor);
                if (group == null) {
                    event.setDropCompleted(false);
                    event.consume();
                    return;
                }

                javafx.collections.ObservableList<CardElement> list = observableListFor(group);
                int anchorIdx = list.indexOf(anchor);
                if (anchorIdx < 0) {
                    event.setDropCompleted(false);
                    event.consume();
                    return;
                }

                // Determine left/right half using mouse X relative to the wrapper
                double localX = event.getX();
                boolean insertAfter = isRightHalf(localX, cardWidthProperty.get());
                int insertionIndex = insertAfter ? anchorIdx + 1 : anchorIdx;

                String srcPane = Controller.DragDropManager.getDragSourcePane();
                boolean isMiddle = "MIDDLE".equals(srcPane);

                if (isMiddle) {
                    java.util.List<CardElement> srcElements =
                            new java.util.ArrayList<>(Controller.DragDropManager.getDraggedElements());
                    // Don't move onto itself
                    if (srcElements.size() == 1 && srcElements.get(0) == anchor) {
                        event.setDropCompleted(false);
                        event.consume();
                        return;
                    }
                    dropInsertIntoGroup(group, insertionIndex, srcElements, null);
                    markDirtyAndRefreshForGroup(group);
                } else {
                    java.util.List<Model.CardsLists.Card> srcCards =
                            new java.util.ArrayList<>(Controller.DragDropManager.getDraggedCards());
                    dropInsertIntoGroup(group, insertionIndex, null, srcCards);
                    markDirtyAndRefreshForGroup(group);
                }
                event.setDropCompleted(true);
                event.consume();
            });
        } // end CardGridCell()

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
                    Controller.UserInterfaceFunctions.markMyCollectionDirty();
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
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
                                        Controller.UserInterfaceFunctions.markMyCollectionDirty();
                                        Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
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
                                            Controller.UserInterfaceFunctions.markMyCollectionDirty();
                                            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                                            break outer;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3) Try to save collection if a save helper exists
                /*try {
                    java.lang.reflect.Method save = Controller.UserInterfaceFunctions.class.getMethod("saveCollectionFile");
                    if (save != null) {
                        try {
                            save.invoke(null);
                        } catch (Throwable ignored) {
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                }*/

                Controller.UserInterfaceFunctions.markMyCollectionDirty();
                Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();

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
                Controller.UserInterfaceFunctions.markMyCollectionDirty();
                Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            }
        }

        /**
         * Returns the set of cards to act on for the current context-menu action.
         * If MIDDLE-pane multi-selection is active and includes this cell's card,
         * returns all matching CardElements from the OwnedCardsCollection.
         * Otherwise returns a single-element list containing only getItem().
         */
        private List<CardElement> getEffectiveMiddleElements() {
            CardElement currentItem = getItem();
            if (currentItem == null) return Collections.emptyList();
            boolean isMultiSelect =
                    "MIDDLE".equals(Controller.SelectionManager.getActivePart())
                            && Controller.SelectionManager.getSelectedMiddleElements().size() > 1
                            && Controller.SelectionManager.getSelectedMiddleElements().contains(currentItem);
            if (isMultiSelect) {
                return new ArrayList<>(Controller.SelectionManager.getSelectedMiddleElements());
            }
            return Collections.singletonList(currentItem);
        }

        private boolean isMiddleMultiSelectActive() {
            CardElement currentItem = getItem();
            return currentItem != null
                    && "MIDDLE".equals(Controller.SelectionManager.getActivePart())
                    && Controller.SelectionManager.getSelectedMiddleElements().size() > 1
                    && Controller.SelectionManager.getSelectedMiddleElements().contains(currentItem);
        }

        private void executeCopyAction() {
            CardElement currentItem = getItem();
            if (currentItem == null || currentItem.getCard() == null) return;
            if (isMiddleMultiSelectActive()) {
                java.util.List<Model.CardsLists.Card> cardsToCopy = new java.util.ArrayList<>();
                for (CardElement element : Controller.SelectionManager.getSelectedMiddleElements()) {
                    if (element.getCard() != null) cardsToCopy.add(element.getCard());
                }
                Controller.CardClipboard.copyCards(cardsToCopy);
            } else if ("RIGHT".equals(Controller.SelectionManager.getActivePart())
                    && Controller.SelectionManager.getSelectedCards().size() > 1
                    && Controller.SelectionManager.getSelectedCards().contains(currentItem.getCard())) {
                Controller.CardClipboard.copyCards(
                        new java.util.ArrayList<>(Controller.SelectionManager.getSelectedCards()));
            } else {
                Controller.CardClipboard.copyCards(
                        Collections.singletonList(currentItem.getCard()));
            }
        }

        private void executeCutFromOwnedCollectionAction() {
            executeCopyAction();
            CardElement currentItem = getItem();
            if (currentItem == null) return;
            List<CardElement> elementsToRemove = getEffectiveMiddleElements();
            if (elementsToRemove.size() > 1) {
                Controller.MenuActionHandler.handleBulkRemoveFromOwnedCollection(
                        new ArrayList<>(elementsToRemove));
            } else {
                removeCardElement(currentItem);
                Controller.UserInterfaceFunctions.refreshOwnedCollectionView();
            }
        }

        private void executeCutFromDecksAction() {
            executeCopyAction();
            CardElement currentItem = getItem();
            if (currentItem == null) return;
            if (isMiddleMultiSelectActive()) {
                Controller.MenuActionHandler.handleBulkRemoveFromDecksAndCollections(
                        Controller.SelectionManager.getSelectedCards());
            } else {
                removeCardElementFromDecksList(currentItem);
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
                grayscale.setSaturation(-0.7);   // 70% desaturation
                grayscale.setBrightness(-0.5);   // darken by 50%
                cardImageView.setEffect(grayscale);
            } else {
                cardImageView.setEffect(null);
            }

            // --- Selection visual: accent border driven by SelectionManager ---
            boolean isSelectedInMiddlePane =
                    "MIDDLE".equals(Controller.SelectionManager.getActivePart())
                            && Controller.SelectionManager.getSelectedMiddleElements().contains(cardElement);

            if (isSelectedInMiddlePane) {
                wrapper.setStyle(
                        "-fx-background-color: transparent; " +
                                "-fx-border-color: #cdfc04; " +
                                "-fx-border-width: 2; " +
                                "-fx-border-radius: 6; " +
                                "-fx-padding: 3;");
            } else {
                wrapper.setStyle("-fx-background-color: transparent;");
            }

            // Finalize graphic
            setGraphic(wrapper);
        }

        /**
         * Returns the Deck or ThemeCollection in the DecksAndCollectionsList
         * that owns the given CardsGroup (matched by backing-list identity).
         * Returns null when the group belongs to MyCollection or cannot be found.
         */
        private Object findDacOwnerForCardsGroup(CardsGroup group) {
            if (group == null) return null;
            List<CardElement> list = group.getCardList();
            if (list == null) return null;
            Model.CardsLists.DecksAndCollectionsList dac =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (dac == null) return null;
            if (dac.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                    if (tc == null) continue;
                    if (list == tc.getCardsList() || list == tc.getExceptionsToNotAdd()) return tc;
                    if (tc.getLinkedDecks() != null) {
                        for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Model.CardsLists.Deck deck : unit) {
                                if (deck == null) continue;
                                if (list == deck.getMainDeck()
                                        || list == deck.getExtraDeck()
                                        || list == deck.getSideDeck()) return deck;
                            }
                        }
                    }
                }
            }
            if (dac.getDecks() != null) {
                for (Model.CardsLists.Deck deck : dac.getDecks()) {
                    if (deck == null) continue;
                    if (list == deck.getMainDeck()
                            || list == deck.getExtraDeck()
                            || list == deck.getSideDeck()) return deck;
                }
            }
            return null;
        }

        private String findCurrentLocationPath() {
            try {
                TreeItem<String> item = CardTreeCell.this.getTreeItem();
                if (item == null) return null;
                String groupName = item.getValue();
                TreeItem<String> parent = item.getParent();
                if (parent == null) return groupName;
                Object parentData = (parent instanceof DataTreeItem)
                        ? ((DataTreeItem<?>) parent).getData() : null;

                // Direct child of a ThemeCollection (e.g. "Cards" or "Cards not to add")
                if (parentData instanceof ThemeCollection) {
                    String collName = sanitizeDisplayName(((ThemeCollection) parentData).getName());
                    if ("Cards not to add".equals(groupName)) return collName + " / Exclusion List";
                    return collName;
                }

                // Child of a Deck (Main/Extra/Side Deck group)
                if (parentData instanceof Deck) {
                    String deckName = sanitizeDisplayName(((Deck) parentData).getName());
                    TreeItem<String> sectionItem = parent.getParent(); // DECKS_SECTION
                    if (sectionItem != null) {
                        TreeItem<String> collOrRoot = sectionItem.getParent();
                        if (collOrRoot != null) {
                            Object collData = (collOrRoot instanceof DataTreeItem)
                                    ? ((DataTreeItem<?>) collOrRoot).getData() : null;
                            if (collData instanceof ThemeCollection) {
                                String collName = sanitizeDisplayName(((ThemeCollection) collData).getName());
                                return collName + " / " + deckName + " / " + groupName;
                            }
                        }
                    }
                    return deckName + " / " + groupName;
                }
            } catch (Exception ignored) {
            }
            return null;
        }

        private void removeCardElementFromDecksList(Model.CardsLists.CardElement cardElement) {
            if (cardElement == null) return;
            try {
                if (getGridView() == null || getGridView().getItems() == null) return;
                @SuppressWarnings("unchecked")
                javafx.collections.ObservableList<CardElement> items =
                        (javafx.collections.ObservableList<CardElement>) getGridView().getItems();
                java.util.Iterator<Model.CardsLists.CardElement> it = items.iterator();
                while (it.hasNext()) {
                    if (it.next() == cardElement) {
                        it.remove();
                        // Mark only the owner of this specific list dirty
                        Object owner = null;
                        for (Map.Entry<CardsGroup, javafx.collections.ObservableList<CardElement>> entry
                                : GROUP_OBSERVABLE_LISTS.entrySet()) {
                            if (entry.getValue() == items) {
                                owner = findDacOwnerForCardsGroup(entry.getKey());
                                break;
                            }
                        }
                        if (owner != null) Controller.UserInterfaceFunctions.markDirty(owner);
                        else Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                        Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                        return;
                    }
                }
            } catch (Throwable t) {
                logger.debug("removeCardElementFromDecksList failed", t);
            }
        }

        // ─────────────────────────────────────────────────────────────────────────────
// Target-list resolution helpers
// ─────────────────────────────────────────────────────────────────────────────

        /**
         * Resolves a display-path (built by buildXxxDestinationMenuItems) to the
         * actual backing List<CardElement> inside the DecksAndCollectionsList.
         * <p>
         * Path patterns (names are already sanitized — no = or -):
         * "CollName"                              → tc.getCardsList()
         * "CollName / Exclusion List"             → tc.getExceptionsToNotAdd()
         * "DeckName / Main|Extra|Side Deck"       → standalone deck list
         * "CollName / DeckName / Main|Extra|Side" → linked deck list
         */
        private List<Model.CardsLists.CardElement> resolveDecksTargetList(String path) {
            if (path == null || path.trim().isEmpty()) return null;
            Model.CardsLists.DecksAndCollectionsList dac =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (dac == null) return null;

            String[] parts = path.split("\\s*/\\s*");
            for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
            if (parts.length == 0) return null;

            String lastLower = parts[parts.length - 1].toLowerCase(java.util.Locale.ROOT);
            boolean isMain = lastLower.equals("main deck");
            boolean isExtra = lastLower.equals("extra deck");
            boolean isSide = lastLower.equals("side deck");
            boolean isExcl = lastLower.equals("exclusion list")
                    || lastLower.equals("cards not to add");

            // "CollName" → collection cards list
            if (parts.length == 1 && !isMain && !isExtra && !isSide && !isExcl) {
                Model.CardsLists.ThemeCollection tc = findCollByDisplayName(parts[0], dac);
                if (tc != null) {
                    if (tc.getCardsList() == null) tc.setCardsList(new ArrayList<>());
                    return tc.getCardsList();
                }
                return null;
            }

            // "CollName / Exclusion List"
            if (parts.length == 2 && isExcl) {
                Model.CardsLists.ThemeCollection tc = findCollByDisplayName(parts[0], dac);
                if (tc != null) {
                    if (tc.getExceptionsToNotAdd() == null)
                        tc.setExceptionsToNotAdd(new ArrayList<>());
                    return tc.getExceptionsToNotAdd();
                }
                return null;
            }

            // "DeckName / Main|Extra|Side Deck"  (standalone deck)
            if (parts.length == 2 && (isMain || isExtra || isSide)) {
                Model.CardsLists.Deck d = findStandaloneDeckByDisplayName(parts[0], dac);
                if (d != null) {
                    if (isMain) return d.getMainDeck();
                    if (isExtra) return d.getExtraDeck();
                    return d.getSideDeck();
                }
                return null;
            }

            // "CollName / DeckName / Main|Extra|Side Deck"  (linked deck)
            if (parts.length == 3 && (isMain || isExtra || isSide)) {
                Model.CardsLists.ThemeCollection tc = findCollByDisplayName(parts[0], dac);
                if (tc != null) {
                    Model.CardsLists.Deck d = findLinkedDeckByDisplayName(parts[1], tc);
                    if (d != null) {
                        if (isMain) return d.getMainDeck();
                        if (isExtra) return d.getExtraDeck();
                        return d.getSideDeck();
                    }
                }
                return null;
            }
            return null;
        }

        private Model.CardsLists.ThemeCollection findCollByDisplayName(
                String displayName, Model.CardsLists.DecksAndCollectionsList dac) {
            if (dac == null || dac.getCollections() == null) return null;
            for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                if (tc == null) continue;
                if (sanitizeDisplayName(tc.getName()).equals(displayName)) return tc;
            }
            return null;
        }

        private Model.CardsLists.Deck findStandaloneDeckByDisplayName(
                String displayName, Model.CardsLists.DecksAndCollectionsList dac) {
            if (dac == null || dac.getDecks() == null) return null;
            for (Model.CardsLists.Deck d : dac.getDecks()) {
                if (d == null) continue;
                if (sanitizeDisplayName(d.getName()).equals(displayName)) return d;
            }
            return null;
        }

        private Model.CardsLists.Deck findLinkedDeckByDisplayName(
                String displayName, Model.CardsLists.ThemeCollection tc) {
            if (tc == null || tc.getLinkedDecks() == null) return null;
            for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                if (unit == null) continue;
                for (Model.CardsLists.Deck d : unit) {
                    if (d == null) continue;
                    if (sanitizeDisplayName(d.getName()).equals(displayName)) return d;
                }
            }
            return null;
        }

// ─────────────────────────────────────────────────────────────────────────────
// Menu item factories
// ─────────────────────────────────────────────────────────────────────────────

        /**
         * MOVE: removes the current card element from its source GridView,
         * then adds it to the resolved target list.
         */
        private void addMoveDestItem(List<MenuItem> items, String path, String excludePath) {
            if (path == null || path.equals(excludePath)) return;
            MenuItem mi = new MenuItem();
            Label lbl = new Label(path);
            lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
            HBox g = new HBox(lbl);
            g.setAlignment(Pos.CENTER_LEFT);
            g.setPadding(new Insets(2, 6, 2, 6));
            mi.setGraphic(g);
            mi.setText("");
            mi.setOnAction(e -> {
                // Collect elements to move — full selection when multi-select is active
                List<Model.CardsLists.CardElement> elementsToMove;
                if (isMiddleMultiSelectActive()) {
                    elementsToMove = new ArrayList<>(
                            Controller.SelectionManager.getSelectedMiddleElements());
                } else {
                    Model.CardsLists.CardElement single = getItem();
                    if (single == null) return;
                    elementsToMove = Collections.singletonList(single);
                }

                // Remove each element from its source observable list.
                // Walk GROUP_OBSERVABLE_LISTS so we can track the exact owner per element.
                java.util.Set<Object> sourceOwners = new java.util.LinkedHashSet<>();
                for (Model.CardsLists.CardElement ce : elementsToMove) {
                    boolean removed = false;
                    for (Map.Entry<CardsGroup, javafx.collections.ObservableList<CardElement>> entry
                            : GROUP_OBSERVABLE_LISTS.entrySet()) {
                        if (entry.getValue().remove(ce)) {
                            Object owner = findDacOwnerForCardsGroup(entry.getKey());
                            if (owner != null) sourceOwners.add(owner);
                            removed = true;
                            break;
                        }
                    }
                    // Fallback for single-select: try the cell's own GridView
                    if (!removed && elementsToMove.size() == 1
                            && getGridView() != null && getGridView().getItems() != null) {
                        getGridView().getItems().remove(ce);
                    }
                }

                // Mark source owners dirty
                if (!sourceOwners.isEmpty()) {
                    for (Object owner : sourceOwners)
                        Controller.UserInterfaceFunctions.markDirty(owner);
                } else {
                    Object fallback = resolveDecksTargetOwner(excludePath != null ? excludePath : "");
                    if (fallback != null) Controller.UserInterfaceFunctions.markDirty(fallback);
                    else Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                }

                // Add all elements to the target list
                List<Model.CardsLists.CardElement> targetList = resolveDecksTargetList(path);
                if (targetList != null) {
                    targetList.addAll(elementsToMove);
                    Controller.MenuActionHandler.setLastDecksAddedTarget(path);
                    Object destOwner = resolveDecksTargetOwner(path);
                    if (destOwner != null) Controller.UserInterfaceFunctions.markDirty(destOwner);
                    else Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                } else {
                    logger.warn("addMoveDestItem: could not resolve '{}'", path);
                }
                Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
            });
            items.add(mi);
        }

        /**
         * ADD: creates a new CardElement from the current item's card and adds it
         * to the resolved target list.  Routing through the existing MenuActionHandler
         * methods ensures lastDecksAddedTarget is set and the view scrolls correctly.
         */
        private void addAddDestItem(List<MenuItem> items, String path) {
            if (path == null) return;
            MenuItem mi = new MenuItem();
            Label lbl = new Label(path);
            lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
            HBox g = new HBox(lbl);
            g.setAlignment(Pos.CENTER_LEFT);
            g.setPadding(new Insets(2, 6, 2, 6));
            mi.setGraphic(g);
            mi.setText("");
            mi.setOnAction(e -> {
                CardElement currentItem = getItem();
                if (currentItem == null || currentItem.getCard() == null) return;

                java.util.Collection<Model.CardsLists.Card> cardsToAdd;
                if (isMiddleMultiSelectActive()) {
                    cardsToAdd = Controller.SelectionManager.getSelectedCards();
                } else {
                    cardsToAdd = Collections.singletonList(currentItem.getCard());
                }

                String[] parts = path.split("\\s*/\\s*");
                String lastPart = parts[parts.length - 1].trim().toLowerCase(java.util.Locale.ROOT);
                boolean isExclusion = lastPart.equals("exclusion list")
                        || lastPart.equals("cards not to add");

                if (isExclusion && parts.length >= 2) {
                    Controller.MenuActionHandler.handleBulkAddToExclusionList(
                            cardsToAdd, parts[0].trim());
                } else if (parts.length == 1) {
                    Controller.MenuActionHandler.handleBulkAddToCollectionCards(
                            cardsToAdd, parts[0].trim());
                } else {
                    Controller.MenuActionHandler.handleBulkAddToDeck(cardsToAdd, path);
                }
            });
            items.add(mi);
        }

// ─────────────────────────────────────────────────────────────────────────────
// Menu builders
// ─────────────────────────────────────────────────────────────────────────────

        /**
         * For the MOVE context (non-archetype cards already in D&C).
         * Excludes the card's current location so it cannot be "moved" to itself.
         */
        private List<MenuItem> buildMoveDestinationMenuItems(String excludePath) {
            List<MenuItem> items = new ArrayList<>();
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
                        String coll = sanitizeDisplayName(tc.getName());
                        addMoveDestItem(items, coll, excludePath);
                        if (tc.getLinkedDecks() != null) {
                            for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                                if (unit == null) continue;
                                for (Model.CardsLists.Deck deck : unit) {
                                    if (deck == null) continue;
                                    String base = coll + " / " + sanitizeDisplayName(deck.getName());
                                    addMoveDestItem(items, base + " / Main Deck", excludePath);
                                    addMoveDestItem(items, base + " / Extra Deck", excludePath);
                                    addMoveDestItem(items, base + " / Side Deck", excludePath);
                                }
                            }
                        }
                        addMoveDestItem(items, coll + " / Exclusion List", excludePath);
                    }
                }
                if (dac.getDecks() != null) {
                    for (Model.CardsLists.Deck deck : dac.getDecks()) {
                        if (deck == null) continue;
                        String d = sanitizeDisplayName(deck.getName());
                        addMoveDestItem(items, d + " / Main Deck", excludePath);
                        addMoveDestItem(items, d + " / Extra Deck", excludePath);
                        addMoveDestItem(items, d + " / Side Deck", excludePath);
                    }
                }
            } catch (Exception ex) {
                logger.debug("buildMoveDestinationMenuItems failed", ex);
            }
            return items;
        }

        /**
         * For the ADD context (archetype cards — read-only source, just insert a copy).
         * No exclusion path needed.
         */
        private List<MenuItem> buildAddDestinationMenuItems() {
            List<MenuItem> items = new ArrayList<>();
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
                        String coll = sanitizeDisplayName(tc.getName());
                        addAddDestItem(items, coll);
                        if (tc.getLinkedDecks() != null) {
                            for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                                if (unit == null) continue;
                                for (Model.CardsLists.Deck deck : unit) {
                                    if (deck == null) continue;
                                    String base = coll + " / " + sanitizeDisplayName(deck.getName());
                                    addAddDestItem(items, base + " / Main Deck");
                                    addAddDestItem(items, base + " / Extra Deck");
                                    addAddDestItem(items, base + " / Side Deck");
                                }
                            }
                        }
                        addAddDestItem(items, coll + " / Exclusion List");
                    }
                }
                if (dac.getDecks() != null) {
                    for (Model.CardsLists.Deck deck : dac.getDecks()) {
                        if (deck == null) continue;
                        String d = sanitizeDisplayName(deck.getName());
                        addAddDestItem(items, d + " / Main Deck");
                        addAddDestItem(items, d + " / Extra Deck");
                        addAddDestItem(items, d + " / Side Deck");
                    }
                }
            } catch (Exception ex) {
                logger.debug("buildAddDestinationMenuItems failed", ex);
            }
            return items;
        }

        /**
         * Returns the Deck or ThemeCollection that owns the list at {@code path},
         * or null if the path cannot be resolved.
         */
        private Object resolveDecksTargetOwner(String path) {
            if (path == null) return null;
            Model.CardsLists.DecksAndCollectionsList dac =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (dac == null) return null;

            String[] parts = path.split("\\s*/\\s*");
            for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
            if (parts.length == 0) return null;

            String lastLower = parts[parts.length - 1].toLowerCase(java.util.Locale.ROOT);
            boolean isMain = lastLower.equals("main deck");
            boolean isExtra = lastLower.equals("extra deck");
            boolean isSide = lastLower.equals("side deck");
            boolean isExcl = lastLower.equals("exclusion list")
                    || lastLower.equals("cards not to add");

            // "CollName" or "CollName / Exclusion List"
            if (parts.length == 1 || (parts.length == 2 && isExcl)) {
                return findCollByDisplayName(parts[0], dac);
            }
            // "DeckName / Main|Extra|Side Deck"
            if (parts.length == 2 && (isMain || isExtra || isSide)) {
                Model.CardsLists.Deck d = findStandaloneDeckByDisplayName(parts[0], dac);
                if (d != null) return d;
            }
            // "CollName / DeckName / Main|Extra|Side Deck"
            if (parts.length == 3 && (isMain || isExtra || isSide)) {
                Model.CardsLists.ThemeCollection tc = findCollByDisplayName(parts[0], dac);
                if (tc != null) {
                    Model.CardsLists.Deck d = findLinkedDeckByDisplayName(parts[1], tc);
                    if (d != null) return d;
                }
            }
            return null;
        }
    }
}