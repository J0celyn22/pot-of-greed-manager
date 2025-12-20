package View;

import Controller.DragDropManager;
import Controller.SelectionManager;
import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.CardsLists.CardsGroup;
import Model.Database.DataBaseUpdate;
import Utils.LruImageCache;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
 * <p>
 * The GridView userData is set to:
 * - Boolean.FALSE or null for non-archetype groups
 * - the Set<String> of missing IDs for archetype groups
 * <p>
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

                    Label nameLabel = new Label(cardElement.getCard().getName_EN());
                    nameLabel.setTextFill(javafx.scene.paint.Color.WHITE);

                    cardBox.getChildren().addAll(imageView, nameLabel);
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

        // userData: if archetype, set the missing set; otherwise set Boolean.FALSE
        if (isArchetype) {
            grid.setUserData(missingForThisGroup == null ? Collections.emptySet() : missingForThisGroup);
        } else {
            grid.setUserData(Boolean.FALSE);
        }

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

            wrapper.setOnMouseClicked(event -> {
                if (getItem() == null) return;
                Card card = getItem().getCard();
                String currentPart = "MIDDLE";
                if (SelectionManager.getActivePart() == null || !SelectionManager.getActivePart().equals(currentPart)) {
                    SelectionManager.clearSelection();
                }
                SelectionManager.selectCard(card, currentPart);
                if (getGridView() != null) {
                    getGridView().requestLayout();
                }
                event.consume();
            });

            cardImageView.setOnDragDetected(event -> {
                if (getItem() == null) return;
                Dragboard db = cardImageView.startDragAndDrop(TransferMode.MOVE);
                SnapshotParameters params = new SnapshotParameters();
                params.setFill(Color.TRANSPARENT);
                WritableImage ghostImage = cardImageView.snapshot(params, null);
                if (ghostImage != null) {
                    db.setDragView(ghostImage, ghostImage.getWidth() / 2, ghostImage.getHeight() / 2);
                }
                ClipboardContent content = new ClipboardContent();
                content.putString(getItem().getCard().getPassCode());
                db.setContent(content);
                DragDropManager.setCurrentlyDraggedCard(getItem().getCard());
                event.consume();
            });
            cardImageView.setOnDragDone(event -> {
                DragDropManager.clearCurrentlyDraggedCard();
                event.consume();
            });
        }

        @Override
        protected void updateItem(CardElement item, boolean empty) {
            super.updateItem(item, empty);

            if (imageLoadFuture != null && !imageLoadFuture.isDone()) {
                imageLoadFuture.cancel(true);
                imageLoadFuture = null;
            }
            cardImageView.getProperties().remove("expectedImagePath");
            currentImageKey = null;

            wrapper.setEffect(null);
            cardImageView.setEffect(null);

            if (empty || item == null) {
                cardImageView.setImage(null);
                wrapper.setStyle("-fx-background-color: transparent;");
                return;
            }

            String imageKey = safeImageKey(item);
            currentImageKey = imageKey;

            String resolvedCached = imageKey == null ? null : imagePathCache.get(imageKey);
            if (resolvedCached != null) {
                Image cached = LruImageCache.getImage(resolvedCached);
                if (cached != null) {
                    cardImageView.setImage(cached);
                    cardImageView.getProperties().remove("expectedImagePath");
                } else {
                    cardImageView.setImage(getPlaceholderImage());
                    cardImageView.getProperties().put("expectedImagePath", resolvedCached);
                    imageLoadFuture = loadImageWithResolvedPathAsync(item, cardImageView, resolvedCached);
                    if (imageLoadFuture != null) outstandingLoads.put(cardImageView, imageLoadFuture);
                }
            } else {
                cardImageView.setImage(getPlaceholderImage());
                resolveImagePathAsync(imageKey, resolvedPath -> {
                    if (!Objects.equals(currentImageKey, imageKey)) {
                        return;
                    }
                    if (resolvedPath == null) {
                        return;
                    }
                    Image cached = LruImageCache.getImage(resolvedPath);
                    if (cached != null) {
                        Platform.runLater(() -> {
                            if (Objects.equals(currentImageKey, imageKey)) {
                                cardImageView.setImage(cached);
                                cardImageView.getProperties().remove("expectedImagePath");
                            }
                        });
                    } else {
                        cardImageView.getProperties().put("expectedImagePath", resolvedPath);
                        Future<?> f = loadImageWithResolvedPathAsync(item, cardImageView, resolvedPath);
                        imageLoadFuture = f;
                        if (f != null) outstandingLoads.put(cardImageView, f);
                    }
                });
            }

            // Selection visual
            if ("MIDDLE".equals(SelectionManager.getActivePart()) &&
                    SelectionManager.getSelectedCards().contains(item.getCard())) {
                wrapper.setStyle("-fx-border-color: #cdfc04; -fx-border-width: 3; -fx-border-radius: 8; -fx-background-radius: 8;");
            } else {
                wrapper.setStyle("-fx-background-color: transparent;");
            }

            // Apply archetype glow if this grid is an archetype grid
            try {
                Object ud = null;
                if (getGridView() != null) ud = getGridView().getUserData();

                Set<String> missingSet = null;
                boolean gridIsArchetype = false;

                if (ud instanceof Set) {
                    @SuppressWarnings("unchecked")
                    Set<String> s = (Set<String>) ud;
                    missingSet = s;
                    gridIsArchetype = true;
                } else if (ud instanceof Boolean) {
                    gridIsArchetype = Boolean.TRUE.equals(ud);
                }

                if (gridIsArchetype) {
                    String konamiId = item.getCard() == null ? null : item.getCard().getKonamiId();
                    String passCode = item.getCard() == null ? null : item.getCard().getPassCode();
                    boolean missing = false;
                    if (missingSet != null && !missingSet.isEmpty()) {
                        if (konamiId != null && missingSet.contains(konamiId)) missing = true;
                        if (!missing && passCode != null && missingSet.contains(passCode)) missing = true;
                    } else {
                        // fallback to legacy global set if present
                        missing = legacyGlobalMissingSet.contains(konamiId) || legacyGlobalMissingSet.contains(passCode);
                    }

                    logger.debug("Rendering card: name='{}' konami='{}' pass='{}' gridArchetype={} missing={}",
                            item.getCard() == null ? "null" : item.getCard().getName_EN(),
                            konamiId, passCode, gridIsArchetype, missing);

                    if (missing) {
                        // create a tight, bright inner glow and a softer outer glow that fades quickly
                        DropShadow innerGlow = new DropShadow();
                        innerGlow.setColor(Color.web("#ffffff", 1.0)); // pure white center
                        innerGlow.setOffsetX(0);
                        innerGlow.setOffsetY(0);
                        innerGlow.setRadius(4);    // tight radius close to the card
                        innerGlow.setSpread(0.9);  // concentrated near the edge

                        DropShadow outerGlow = new DropShadow();
                        outerGlow.setColor(Color.web("#ffffff", 0.22)); // faint outer halo
                        outerGlow.setOffsetX(0);
                        outerGlow.setOffsetY(0);
                        outerGlow.setRadius(14);   // larger radius but low intensity
                        outerGlow.setSpread(0.12); // quick fade

                        // chain inner into outer so the center stays bright and the halo fades quickly
                        outerGlow.setInput(innerGlow);

                        wrapper.setEffect(outerGlow);
                    } else {
                        wrapper.setEffect(null);
                    }
                } else {
                    wrapper.setEffect(null);
                }
            } catch (Exception e) {
                logger.debug("Failed to apply archetype glow", e);
                wrapper.setEffect(null);
            }
        }
    }

}
