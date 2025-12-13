//package View;
//
//import Controller.DragDropManager;
//import Controller.SelectionManager;
//import Model.CardsLists.Card;
//import Model.CardsLists.CardElement;
//import Model.CardsLists.CardsGroup;
//import Model.Database.DataBaseUpdate;
//import Utils.LruImageCache;
//import javafx.application.Platform;
//import javafx.beans.property.DoubleProperty;
//import javafx.beans.property.ObjectProperty;
//import javafx.beans.property.SimpleObjectProperty;
//import javafx.collections.FXCollections;
//import javafx.geometry.Insets;
//import javafx.geometry.Pos;
//import javafx.scene.SnapshotParameters;
//import javafx.scene.control.Label;
//import javafx.scene.control.TreeCell;
//import javafx.scene.control.TreeItem;
//import javafx.scene.effect.DropShadow;
//import javafx.scene.image.Image;
//import javafx.scene.image.ImageView;
//import javafx.scene.image.WritableImage;
//import javafx.scene.input.ClipboardContent;
//import javafx.scene.input.Dragboard;
//import javafx.scene.input.KeyCode;
//import javafx.scene.input.TransferMode;
//import javafx.scene.layout.HBox;
//import javafx.scene.layout.Priority;
//import javafx.scene.layout.StackPane;
//import javafx.scene.layout.VBox;
//import javafx.scene.paint.Color;
//import org.controlsfx.control.GridCell;
//import org.controlsfx.control.GridView;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.concurrent.ExecutorService;
//import java.util.concurrent.Executors;
//import java.util.concurrent.Future;
//
//public class CardTreeCell extends TreeCell<String> {
//
//    private static final Logger logger = LoggerFactory.getLogger(CardTreeCell.class);
//
//    private final DoubleProperty cardWidthProperty;
//    private final DoubleProperty cardHeightProperty;
//
//    // Used for selection visuals.
//    private final ObjectProperty<CardElement> selectedCardElement = new SimpleObjectProperty<>();
//
//    // Executor for asynchronous image loading.
//    private static final ExecutorService imageLoadingExecutor = Executors.newFixedThreadPool(4);
//
//    // Maximum resolution constants.
//    private static final double MAX_CARD_WIDTH = 300;
//    private static final double MAX_CARD_HEIGHT = MAX_CARD_WIDTH * (146.0 / 100.0); // ≈438
//
//    private Label customTriangleLabel;
//    private GridView<CardElement> cardGridView;
//
//    public CardTreeCell(DoubleProperty cardWidthProperty, DoubleProperty cardHeightProperty) {
//        this.cardWidthProperty = cardWidthProperty;
//        this.cardHeightProperty = cardHeightProperty;
//        getStyleClass().add("card-tree-cell");
//    }
//
//    @Override
//    protected void updateItem(String itemName, boolean empty) {
//        super.updateItem(itemName, empty);
//        setText(null);
//        setGraphic(null);
//        getStyleClass().setAll("card-tree-cell");
//
//        if (empty || itemName == null) {
//            return;
//        } else {
//            TreeItem<String> treeItem = getTreeItem();
//            if (treeItem instanceof DataTreeItem) {
//                Object dataObject = ((DataTreeItem<?>) treeItem).getData();
//                if (dataObject instanceof CardsGroup) {
//                    createCardsGroupCell(itemName, (CardsGroup) dataObject);
//                } else if (dataObject instanceof CardElement) {
//                    // Handle card display
//                    CardElement cardElement = (CardElement) dataObject;
//                    HBox cardBox = new HBox(5);
//                    cardBox.setAlignment(Pos.CENTER_LEFT);
//
//                    // Create image view for the card
//                    ImageView imageView = new ImageView();
//                    imageView.setFitWidth(cardWidthProperty.get());
//                    imageView.setFitHeight(cardHeightProperty.get());
//                    imageView.setPreserveRatio(true);
//
//                    // Load image asynchronously
//                    loadCardImage(cardElement.getCard(), imageView);
//
//                    // Add card name label
//                    Label nameLabel = new Label(cardElement.getCard().getName_EN());
//                    nameLabel.setTextFill(javafx.scene.paint.Color.WHITE);
//
//                    cardBox.getChildren().addAll(imageView, nameLabel);
//                    setGraphic(cardBox);
//                } else if (dataObject instanceof String && dataObject.equals("ROOT")) {
//                    Label label = new Label(itemName);
//                    label.getStyleClass().add("tree-root-label");
//                    setGraphic(label);
//                } else {
//                    Label label = new Label(itemName);
//                    label.getStyleClass().add("tree-item-label");
//                    setGraphic(label);
//                }
//            } else {
//                Label label = new Label(itemName);
//                label.getStyleClass().add("tree-item-label");
//                setGraphic(label);
//            }
//        }
//    }
//
//    private void loadCardImage(Card card, ImageView imageView) {
//        String imageUrl = getImagePath(card);
//        if (imageUrl != null) {
//            Image image = LruImageCache.getImage(imageUrl);
//            if (image != null) {
//                imageView.setImage(image);
//            } else {
//                // Load image in background
//                imageLoadingExecutor.submit(() -> {
//                    try {
//                        Image newImage = new Image(imageUrl, true);
//                        newImage.progressProperty().addListener((obs, oldVal, newVal) -> {
//                            if (newVal.doubleValue() == 1.0) {
//                                Platform.runLater(() -> {
//                                    imageView.setImage(newImage);
//                                    LruImageCache.addImage(imageUrl, newImage);
//                                });
//                            }
//                        });
//                    } catch (Exception e) {
//                        logger.error("Error loading image for card: " + card.getName_EN(), e);
//                    }
//                });
//            }
//        }
//    }
//
//    private String getImagePath(Card card) {
//        if (card == null || card.getImagePath() == null) return null;
//        String imageKey = card.getImagePath();
//        String[] addresses = DataBaseUpdate.getAddresses(imageKey + ".jpg");
//        if (addresses != null && addresses.length > 0) {
//            return "file:" + addresses[0];
//        }
//        return null;
//    }
//
//    private void createCardsGroupCell(String itemName, CardsGroup group) {
//        HBox hbox = new HBox();
//        hbox.getStyleClass().add("card-group-hbox");
//        hbox.setSpacing(5);
//
//        customTriangleLabel = new Label();
//        customTriangleLabel.getStyleClass().add("custom-triangle-label");
//        customTriangleLabel.setMinWidth(15);
//        customTriangleLabel.setAlignment(Pos.CENTER);
//
//        Label label = new Label(itemName);
//        label.getStyleClass().add("card-group-label");
//
//        hbox.getChildren().addAll(customTriangleLabel, label);
//
//        VBox vBox = new VBox();
//        vBox.getStyleClass().add("card-group-vbox");
//        vBox.getChildren().add(hbox);
//
//        cardGridView = new GridView<>();
//        cardGridView.getStyleClass().add("card-grid-view");
//        cardGridView.setCellFactory(gridView -> new CardGridCell());
//        cardGridView.setItems(FXCollections.observableArrayList(group.getCardList()));
//        cardGridView.cellWidthProperty().bind(cardWidthProperty);
//        cardGridView.cellHeightProperty().bind(cardHeightProperty);
//        cardGridView.setHorizontalCellSpacing(5);
//        cardGridView.setVerticalCellSpacing(5);
//        cardGridView.setPadding(new Insets(5));
//        cardGridView.prefWidthProperty().bind(getTreeView().widthProperty().subtract(50));
//
//        adjustGridViewHeight(group);
//        cardWidthProperty.addListener((obs, oldVal, newVal) -> adjustGridViewHeight(group));
//        getTreeView().widthProperty().addListener((obs, oldVal, newVal) -> adjustGridViewHeight(group));
//
//        customTriangleLabel.setOnMouseClicked(event -> {
//            boolean isExpanded = !cardGridView.isVisible();
//            cardGridView.setVisible(isExpanded);
//            cardGridView.setManaged(isExpanded);
//            updateCustomTriangle(isExpanded);
//            event.consume();
//        });
//        cardGridView.setVisible(true);
//        cardGridView.setManaged(true);
//        updateCustomTriangle(true);
//
//        vBox.getChildren().add(cardGridView);
//        VBox.setVgrow(cardGridView, Priority.ALWAYS);
//        setGraphic(vBox);
//    }
//
//    private void adjustGridViewHeight(CardsGroup group) {
//        long startTime = System.nanoTime();
//        int numItems = group.getCardList().size();
//        if (numItems <= 0) {
//            cardGridView.setPrefHeight(0);
//            cardGridView.setMinHeight(0);
//            cardGridView.setMaxHeight(0);
//            return;
//        }
//        double availableWidth = getTreeView().getWidth() - 70;
//        double effectiveCellWidth = cardWidthProperty.get();
//        double horizontalSpacing = cardGridView.getHorizontalCellSpacing();
//        int numColumns = (int) Math.floor(availableWidth / (effectiveCellWidth + horizontalSpacing));
//        if (numColumns < 1) {
//            numColumns = 1;
//        }
//        int numRows = (int) Math.ceil((double) numItems / numColumns);
//        double topPadding = cardGridView.getPadding().getTop();
//        double bottomPadding = cardGridView.getPadding().getBottom();
//        double verticalSpacing = cardGridView.getVerticalCellSpacing();
//        double cellExtraPadding = 10;
//        double effectiveCellHeight = cardHeightProperty.get() + cellExtraPadding;
//        double prefHeight = topPadding + bottomPadding + (numRows * effectiveCellHeight)
//                + ((numRows - 1) * verticalSpacing) + 10;
//        cardGridView.setPrefHeight(prefHeight);
//        cardGridView.setMinHeight(prefHeight);
//        cardGridView.setMaxHeight(prefHeight);
//        long endTime = System.nanoTime();
//        logger.debug("Adjusted grid view height to {} for {} items in {} ms",
//                prefHeight, numItems, (endTime - startTime) / 1_000_000.0);
//    }
//
//    private void updateCustomTriangle(boolean isExpanded) {
//        String triangle = isExpanded ? "\u25BC" : "\u25B6"; // ▼ or ▶
//        customTriangleLabel.setText(triangle);
//    }
//
//    private void selectCard(CardElement cardElement) {
//        selectedCardElement.set(cardElement);
//        logger.info("Selected card: {}", cardElement.getCard().getName_EN());
//    }
//
//    /**
//     * Loads the card image asynchronously.
//     */
//    private Future<?> loadImage(CardElement cardElement, ImageView cardImage) {
//        String imagePath = getImagePath(cardElement);
//        if (imagePath != null) {
//            double desiredWidth = MAX_CARD_WIDTH;
//            double desiredHeight = MAX_CARD_HEIGHT;
//            Image image = LruImageCache.getImage(imagePath);
//            if (image != null) {
//                cardImage.setImage(image);
//                logger.debug("Loaded image from cache for card {}", cardElement.getCard().getName_EN());
//                return null;
//            } else {
//                cardImage.setImage(getPlaceholderImage());
//                logger.debug("Loading image asynchronously for card {}", cardElement.getCard().getName_EN());
//                Future<?> future = imageLoadingExecutor.submit(() -> {
//                    long startImageLoad = System.nanoTime();
//                    try {
//                        Image img = new Image(imagePath, desiredWidth, desiredHeight, true, true, false);
//                        if (Thread.currentThread().isInterrupted()) {
//                            return;
//                        }
//                        LruImageCache.addImage(imagePath, img);
//                        Platform.runLater(() -> {
//                            if (cardImage.getScene() != null) {
//                                cardImage.setImage(img);
//                            }
//                        });
//                        long endImageLoad = System.nanoTime();
//                        logger.debug("Asynchronously loaded image for card {} in {} ms",
//                                cardElement.getCard().getName_EN(), (endImageLoad - startImageLoad) / 1_000_000.0);
//                    } catch (Exception e) {
//                        logger.error("Error loading image for card " + cardElement.getCard().getName_EN(), e);
//                    }
//                });
//                return future;
//            }
//        } else {
//            cardImage.setImage(getPlaceholderImage());
//            return null;
//        }
//    }
//
//    /**
//     * Retrieves the file path for a card's image.
//     */
//    private String getImagePath(CardElement cardElement) {
//        if (cardElement == null || cardElement.getCard() == null || cardElement.getCard().getImagePath() == null) {
//            logger.warn("CardElement or its image path is null");
//            return null;
//        }
//        String imageKey = cardElement.getCard().getImagePath();
//        String[] addresses = DataBaseUpdate.getAddresses(imageKey + ".jpg");
//        if (addresses != null && addresses.length > 0) {
//            String fullPath = "file:" + addresses[0];
//            logger.debug("Resolved image path for card '{}' is {}", cardElement.getCard().getName_EN(), fullPath);
//            return fullPath;
//        }
//        logger.warn("No valid addresses found for image path: {}", imageKey);
//        return null;
//    }
//
//    private Image getPlaceholderImage() {
//        return new Image("file:./src/main/resources/placeholder.jpg");
//    }
//
//    public static void shutdownImageLoadingExecutor() {
//        imageLoadingExecutor.shutdownNow();
//    }
//
//    // Inner class: Custom GridCell for displaying individual cards.
//    // Inside your CardTreeCell (or equivalent) – the inner GridCell subclass for the middle part.
//    private class CardGridCell extends org.controlsfx.control.GridCell<CardElement> {
//        private ImageView cardImageView;
//        private StackPane wrapper;
//
//        public CardGridCell() {
//            cardImageView = new ImageView();
//            cardImageView.setPreserveRatio(true);
//            cardImageView.fitWidthProperty().bind(cardWidthProperty);
//            cardImageView.fitHeightProperty().bind(cardHeightProperty);
//
//            wrapper = new StackPane(cardImageView);
//            wrapper.setPadding(new Insets(5));
//            wrapper.setStyle("-fx-background-color: transparent;");
//            wrapper.setPickOnBounds(true);
//            wrapper.setFocusTraversable(true);
//            setGraphic(wrapper);
//
//            // Selection handler for the middle part using "MIDDLE"
//            wrapper.setOnMouseClicked(event -> {
//                if(getItem() == null) return;
//                Card card = getItem().getCard();
//                String currentPart = "MIDDLE";
//                // If the active part is not MIDDLE, clear selection first.
//                if(SelectionManager.getActivePart() == null || !SelectionManager.getActivePart().equals(currentPart)) {
//                    SelectionManager.clearSelection();
//                }
//                // Use a normal click to select exclusively.
//                SelectionManager.selectCard(card, currentPart);
//                if(getGridView() != null) {
//                    getGridView().requestLayout();
//                }
//                event.consume();
//            });
//
//            // Drag handling (unchanged)
//            cardImageView.setOnDragDetected(event -> {
//                if(getItem() == null) return;
//                Dragboard db = cardImageView.startDragAndDrop(TransferMode.MOVE);
//                SnapshotParameters params = new SnapshotParameters();
//                params.setFill(Color.TRANSPARENT);
//                WritableImage ghostImage = cardImageView.snapshot(params, null);
//                if(ghostImage != null) {
//                    db.setDragView(ghostImage, ghostImage.getWidth()/2, ghostImage.getHeight()/2);
//                }
//                ClipboardContent content = new ClipboardContent();
//                content.putString(getItem().getCard().getPassCode());
//                db.setContent(content);
//                DragDropManager.setCurrentlyDraggedCard(getItem().getCard());
//                event.consume();
//            });
//            cardImageView.setOnDragDone(event -> {
//                DragDropManager.clearCurrentlyDraggedCard();
//                event.consume();
//            });
//        }
//
//        @Override
//        protected void updateItem(CardElement item, boolean empty) {
//            super.updateItem(item, empty);
//            if(empty || item == null) {
//                cardImageView.setImage(null);
//                wrapper.setStyle("-fx-background-color: transparent;");
//            } else {
//                String fullUrl = getImagePath(item.getCard());
//                Image image = LruImageCache.getImage(fullUrl);
//                if(image == null) {
//                    image = new Image(fullUrl, cardWidthProperty.get(), cardHeightProperty.get(), true, true);
//                }
//                cardImageView.setImage(image);
//
//                // Apply selection style only if the active part is "MIDDLE".
//                if("MIDDLE".equals(SelectionManager.getActivePart()) &&
//                        SelectionManager.getSelectedCards().contains(item.getCard())) {
//                    wrapper.setStyle("-fx-border-color: #cdfc04; -fx-border-width: 3; -fx-border-radius: 8; -fx-background-radius: 8;");
//                } else {
//                    wrapper.setStyle("-fx-background-color: transparent;");
//                }
//            }
//        }
//
//        private String getImagePath(Card card) {
//            if(card == null || card.getImagePath() == null)
//                return "file:./src/main/resources/placeholder.jpg";
//            String imageKey = card.getImagePath();
//            String[] addresses = Model.Database.DataBaseUpdate.getAddresses(imageKey + ".jpg");
//            if(addresses != null && addresses.length > 0) {
//                return "file:" + addresses[0];
//            }
//            return "file:./src/main/resources/placeholder.jpg";
//        }
//    }
//}

/**
 * V2
 */
//package View;
//
//import Controller.DragDropManager;
//import Controller.SelectionManager;
//import Model.CardsLists.Card;
//import Model.CardsLists.CardElement;
//import Model.CardsLists.CardsGroup;
//import Model.Database.DataBaseUpdate;
//import Utils.LruImageCache;
//import javafx.application.Platform;
//import javafx.beans.property.DoubleProperty;
//import javafx.beans.property.ObjectProperty;
//import javafx.beans.property.SimpleObjectProperty;
//import javafx.collections.FXCollections;
//import javafx.geometry.Insets;
//import javafx.geometry.Pos;
//import javafx.scene.SnapshotParameters;
//import javafx.scene.control.Label;
//import javafx.scene.control.TreeCell;
//import javafx.scene.control.TreeItem;
//import javafx.scene.image.Image;
//import javafx.scene.image.ImageView;
//import javafx.scene.image.WritableImage;
//import javafx.scene.input.ClipboardContent;
//import javafx.scene.input.Dragboard;
//import javafx.scene.input.TransferMode;
//import javafx.scene.layout.HBox;
//import javafx.scene.layout.Priority;
//import javafx.scene.layout.StackPane;
//import javafx.scene.layout.VBox;
//import javafx.scene.paint.Color;
//import org.controlsfx.control.GridCell;
//import org.controlsfx.control.GridView;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import java.util.Objects;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicReference;
//import java.util.function.Consumer;
//
//public class CardTreeCell extends TreeCell<String> {
//
//    private static final Logger logger = LoggerFactory.getLogger(CardTreeCell.class);
//
//    private final DoubleProperty cardWidthProperty;
//    private final DoubleProperty cardHeightProperty;
//
//    // Used for selection visuals.
//    private final ObjectProperty<CardElement> selectedCardElement = new SimpleObjectProperty<>();
//
//    // Executor for asynchronous image loading (decoding)
//    private static final ExecutorService imageLoadingExecutor = Executors.newFixedThreadPool(4);
//
//    // Executor for resolving image paths (off-FX thread)
//    private static final ExecutorService pathResolverExecutor = Executors.newSingleThreadExecutor(r -> {
//        Thread t = new Thread(r, "image-path-resolver");
//        t.setDaemon(true);
//        return t;
//    });
//
//    // Cache for resolved image paths: imageKey -> "file:/full/path.jpg"
//    private static final ConcurrentHashMap<String, String> imagePathCache = new ConcurrentHashMap<>();
//
//    // Keep track of outstanding load tasks per ImageView
//    private final ConcurrentHashMap<ImageView, Future<?>> outstandingLoads = new ConcurrentHashMap<>();
//
//    // Maximum resolution constants (kept from original)
//    private static final double MAX_CARD_WIDTH = 300;
//    private static final double MAX_CARD_HEIGHT = MAX_CARD_WIDTH * (146.0 / 100.0); // ≈438
//
//    private Label customTriangleLabel;
//    private GridView<CardElement> cardGridView;
//
//    public CardTreeCell(DoubleProperty cardWidthProperty, DoubleProperty cardHeightProperty) {
//        this.cardWidthProperty = cardWidthProperty;
//        this.cardHeightProperty = cardHeightProperty;
//        getStyleClass().add("card-tree-cell");
//    }
//
//    @Override
//    protected void updateItem(String itemName, boolean empty) {
//        super.updateItem(itemName, empty);
//        setText(null);
//        setGraphic(null);
//        getStyleClass().setAll("card-tree-cell");
//
//        if (empty || itemName == null) {
//            return;
//        } else {
//            TreeItem<String> treeItem = getTreeItem();
//            if (treeItem instanceof DataTreeItem) {
//                Object dataObject = ((DataTreeItem<?>) treeItem).getData();
//                if (dataObject instanceof CardsGroup) {
//                    createCardsGroupCell(itemName, (CardsGroup) dataObject);
//                } else if (dataObject instanceof CardElement) {
//                    // Single card row (rare in your UI). Keep this lightweight.
//                    CardElement cardElement = (CardElement) dataObject;
//                    HBox cardBox = new HBox(5);
//                    cardBox.setAlignment(Pos.CENTER_LEFT);
//
//                    ImageView imageView = new ImageView();
//                    imageView.setFitWidth(cardWidthProperty.get());
//                    imageView.setFitHeight(cardHeightProperty.get());
//                    imageView.setPreserveRatio(true);
//
//                    // Immediately set cached image if available (fast)
//                    String imageKey = safeImageKey(cardElement);
//                    String cachedFullPath = imageKey == null ? null : imagePathCache.get(imageKey);
//                    if (cachedFullPath != null) {
//                        Image cached = LruImageCache.getImage(cachedFullPath);
//                        if (cached != null) {
//                            imageView.setImage(cached);
//                        } else {
//                            imageView.setImage(getPlaceholderImage());
//                            // start background load using resolved path
//                            Future<?> f = loadImageWithResolvedPathAsync(cardElement, imageView, cachedFullPath);
//                            if (f != null) outstandingLoads.put(imageView, f);
//                        }
//                    } else {
//                        // No cached path: set placeholder and resolve path asynchronously
//                        imageView.setImage(getPlaceholderImage());
//                        resolveImagePathAsync(imageKey, resolvedPath -> {
//                            if (resolvedPath == null) {
//                                // no path found -> keep placeholder
//                                return;
//                            }
//                            // If image already cached in LRU, set it quickly on FX thread
//                            Image cached = LruImageCache.getImage(resolvedPath);
//                            if (cached != null) {
//                                Platform.runLater(() -> imageView.setImage(cached));
//                            } else {
//                                // start background load using resolved path
//                                Future<?> f = loadImageWithResolvedPathAsync(cardElement, imageView, resolvedPath);
//                                if (f != null) outstandingLoads.put(imageView, f);
//                            }
//                        });
//                    }
//
//                    Label nameLabel = new Label(cardElement.getCard().getName_EN());
//                    nameLabel.setTextFill(javafx.scene.paint.Color.WHITE);
//
//                    cardBox.getChildren().addAll(imageView, nameLabel);
//                    setGraphic(cardBox);
//                } else if (dataObject instanceof String && dataObject.equals("ROOT")) {
//                    Label label = new Label(itemName);
//                    label.getStyleClass().add("tree-root-label");
//                    setGraphic(label);
//                } else {
//                    Label label = new Label(itemName);
//                    label.getStyleClass().add("tree-item-label");
//                    setGraphic(label);
//                }
//            } else {
//                Label label = new Label(itemName);
//                label.getStyleClass().add("tree-item-label");
//                setGraphic(label);
//            }
//        }
//    }
//
//    /**
//     * Resolve an imageKey to a full file:... path off the FX thread and cache it.
//     * The callback is invoked on the pathResolverExecutor thread; it may call
//     * background loaders which will themselves use Platform.runLater when needed.
//     */
//    private void resolveImagePathAsync(String imageKey, Consumer<String> callback) {
//        if (imageKey == null) {
//            callback.accept(null);
//            return;
//        }
//        // If cached, return immediately (fast)
//        String cached = imagePathCache.get(imageKey);
//        if (cached != null) {
//            callback.accept(cached);
//            return;
//        }
//
//        // Otherwise submit a resolver task (non-FX)
//        pathResolverExecutor.submit(() -> {
//            try {
//                String[] addresses = DataBaseUpdate.getAddresses(imageKey + ".jpg");
//                String resolved = null;
//                if (addresses != null && addresses.length > 0) {
//                    resolved = "file:" + addresses[0];
//                    imagePathCache.put(imageKey, resolved);
//                }
//                callback.accept(resolved);
//            } catch (Exception e) {
//                logger.warn("Failed to resolve image path for key {}", imageKey, e);
//                callback.accept(null);
//            }
//        });
//    }
//
//    /**
//     * Load an image given a resolved path (file:...) on the imageLoadingExecutor and set it on the ImageView.
//     * Returns the Future so callers can cancel if the cell is reused.
//     */
//    private Future<?> loadImageWithResolvedPathAsync(CardElement cardElement, ImageView imageView, String resolvedPath) {
//        if (resolvedPath == null) {
//            Platform.runLater(() -> imageView.setImage(getPlaceholderImage()));
//            return null;
//        }
//
//        // If already cached in LRU, set immediately
//        Image cached = LruImageCache.getImage(resolvedPath);
//        if (cached != null) {
//            Platform.runLater(() -> imageView.setImage(cached));
//            return null;
//        }
//
//        // Submit background task to create Image (decoding) off FX thread
//        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
//        Future<?> future = imageLoadingExecutor.submit(() -> {
//            try {
//                // Create Image with backgroundLoading = true to avoid heavy decoding on FX thread.
//                Image img = new Image(resolvedPath, cardWidthProperty.get(), cardHeightProperty.get(), true, true, true);
//
//                // If already fully loaded, cache and set
//                if (img.getProgress() >= 1.0) {
//                    LruImageCache.addImage(resolvedPath, img);
//                    Platform.runLater(() -> {
//                        Future<?> current = outstandingLoads.get(imageView);
//                        if (current == futureRef.get() || current == null) {
//                            imageView.setImage(img);
//                        }
//                    });
//                } else {
//                    // Attach listener on FX thread to set when loading completes
//                    Platform.runLater(() -> {
//                        img.progressProperty().addListener((obs, oldV, newV) -> {
//                            if (newV.doubleValue() >= 1.0) {
//                                LruImageCache.addImage(resolvedPath, img);
//                                Future<?> current = outstandingLoads.get(imageView);
//                                if (current == futureRef.get() || current == null) {
//                                    imageView.setImage(img);
//                                }
//                            }
//                        });
//                    });
//                }
//            } catch (Exception e) {
//                logger.error("Error loading image for card " + (cardElement != null && cardElement.getCard() != null ? cardElement.getCard().getName_EN() : "unknown"), e);
//                Platform.runLater(() -> imageView.setImage(getPlaceholderImage()));
//            } finally {
//                outstandingLoads.remove(imageView, futureRef.get());
//            }
//        });
//
//        futureRef.set(future);
//        outstandingLoads.put(imageView, future);
//        return future;
//    }
//
//    private String getImagePath(Card card) {
//        if (card == null || card.getImagePath() == null) return null;
//        String imageKey = card.getImagePath();
//        String[] addresses = DataBaseUpdate.getAddresses(imageKey + ".jpg");
//        if (addresses != null && addresses.length > 0) {
//            return "file:" + addresses[0];
//        }
//        return null;
//    }
//
//    private void createCardsGroupCell(String itemName, CardsGroup group) {
//        HBox hbox = new HBox();
//        hbox.getStyleClass().add("card-group-hbox");
//        hbox.setSpacing(5);
//
//        customTriangleLabel = new Label();
//        customTriangleLabel.getStyleClass().add("custom-triangle-label");
//        customTriangleLabel.setMinWidth(15);
//        customTriangleLabel.setAlignment(Pos.CENTER);
//
//        Label label = new Label(itemName);
//        label.getStyleClass().add("card-group-label");
//
//        hbox.getChildren().addAll(customTriangleLabel, label);
//
//        VBox vBox = new VBox();
//        vBox.getStyleClass().add("card-group-vbox");
//        vBox.getChildren().add(hbox);
//
//        // Reuse a single observable list instance to avoid allocations on each update
//        cardGridView = new GridView<>();
//        cardGridView.getStyleClass().add("card-grid-view");
//        cardGridView.setCellFactory(gridView -> new CardGridCell());
//        cardGridView.setItems(FXCollections.observableArrayList(group.getCardList()));
//        cardGridView.cellWidthProperty().bind(cardWidthProperty);
//        cardGridView.cellHeightProperty().bind(cardHeightProperty);
//        cardGridView.setHorizontalCellSpacing(5);
//        cardGridView.setVerticalCellSpacing(5);
//        cardGridView.setPadding(new Insets(5));
//        cardGridView.prefWidthProperty().bind(getTreeView().widthProperty().subtract(50));
//
//        adjustGridViewHeight(group);
//        cardWidthProperty.addListener((obs, oldVal, newVal) -> adjustGridViewHeight(group));
//        getTreeView().widthProperty().addListener((obs, oldVal, newVal) -> adjustGridViewHeight(group));
//
//        customTriangleLabel.setOnMouseClicked(event -> {
//            boolean isExpanded = !cardGridView.isVisible();
//            cardGridView.setVisible(isExpanded);
//            cardGridView.setManaged(isExpanded);
//            updateCustomTriangle(isExpanded);
//            event.consume();
//        });
//        cardGridView.setVisible(true);
//        cardGridView.setManaged(true);
//        updateCustomTriangle(true);
//
//        vBox.getChildren().add(cardGridView);
//        VBox.setVgrow(cardGridView, Priority.ALWAYS);
//        setGraphic(vBox);
//    }
//
//    private void adjustGridViewHeight(CardsGroup group) {
//        int numItems = group.getCardList().size();
//        if (numItems <= 0) {
//            cardGridView.setPrefHeight(0);
//            cardGridView.setMinHeight(0);
//            cardGridView.setMaxHeight(0);
//            return;
//        }
//        double availableWidth = getTreeView().getWidth() - 70;
//        double effectiveCellWidth = cardWidthProperty.get();
//        double horizontalSpacing = cardGridView.getHorizontalCellSpacing();
//        int numColumns = (int) Math.floor(availableWidth / (effectiveCellWidth + horizontalSpacing));
//        if (numColumns < 1) {
//            numColumns = 1;
//        }
//        int numRows = (int) Math.ceil((double) numItems / numColumns);
//        double topPadding = cardGridView.getPadding().getTop();
//        double bottomPadding = cardGridView.getPadding().getBottom();
//        double verticalSpacing = cardGridView.getVerticalCellSpacing();
//        double cellExtraPadding = 10;
//        double effectiveCellHeight = cardHeightProperty.get() + cellExtraPadding;
//        double prefHeight = topPadding + bottomPadding + (numRows * effectiveCellHeight)
//                + ((numRows - 1) * verticalSpacing) + 10;
//        cardGridView.setPrefHeight(prefHeight);
//        cardGridView.setMinHeight(prefHeight);
//        cardGridView.setMaxHeight(prefHeight);
//        logger.debug("Adjusted grid view height to {} for {} items", prefHeight, numItems);
//    }
//
//    private void updateCustomTriangle(boolean isExpanded) {
//        String triangle = isExpanded ? "\u25BC" : "\u25B6"; // ▼ or ▶
//        customTriangleLabel.setText(triangle);
//    }
//
//    private void selectCard(CardElement cardElement) {
//        selectedCardElement.set(cardElement);
//        logger.info("Selected card: {}", cardElement.getCard().getName_EN());
//    }
//
//    private Image getPlaceholderImage() {
//        // Keep a single placeholder instance to avoid repeated file reads
//        return PlaceholderHolder.PLACEHOLDER;
//    }
//
//    private static class PlaceholderHolder {
//        static final Image PLACEHOLDER = new Image("file:./src/main/resources/placeholder.jpg");
//    }
//
//    public static void shutdownImageLoadingExecutor() {
//        imageLoadingExecutor.shutdownNow();
//        pathResolverExecutor.shutdownNow();
//    }
//
//    // Inner class: Custom GridCell for displaying individual cards.
//    private class CardGridCell extends GridCell<CardElement> {
//        private final ImageView cardImageView;
//        private final StackPane wrapper;
//        private Future<?> imageLoadFuture;
//        private String currentImageKey; // track which key this cell currently represents
//
//        public CardGridCell() {
//            cardImageView = new ImageView();
//            cardImageView.setPreserveRatio(true);
//            cardImageView.fitWidthProperty().bind(cardWidthProperty);
//            cardImageView.fitHeightProperty().bind(cardHeightProperty);
//
//            wrapper = new StackPane(cardImageView);
//            wrapper.setPadding(new Insets(5));
//            wrapper.setStyle("-fx-background-color: transparent;");
//            wrapper.setPickOnBounds(true);
//            wrapper.setFocusTraversable(true);
//            setGraphic(wrapper);
//
//            // Selection handler for the middle part using "MIDDLE"
//            wrapper.setOnMouseClicked(event -> {
//                if (getItem() == null) return;
//                Card card = getItem().getCard();
//                String currentPart = "MIDDLE";
//                if (SelectionManager.getActivePart() == null || !SelectionManager.getActivePart().equals(currentPart)) {
//                    SelectionManager.clearSelection();
//                }
//                SelectionManager.selectCard(card, currentPart);
//                if (getGridView() != null) {
//                    getGridView().requestLayout();
//                }
//                event.consume();
//            });
//
//            // Drag handling
//            cardImageView.setOnDragDetected(event -> {
//                if (getItem() == null) return;
//                Dragboard db = cardImageView.startDragAndDrop(TransferMode.MOVE);
//                SnapshotParameters params = new SnapshotParameters();
//                params.setFill(Color.TRANSPARENT);
//                WritableImage ghostImage = cardImageView.snapshot(params, null);
//                if (ghostImage != null) {
//                    db.setDragView(ghostImage, ghostImage.getWidth() / 2, ghostImage.getHeight() / 2);
//                }
//                ClipboardContent content = new ClipboardContent();
//                content.putString(getItem().getCard().getPassCode());
//                db.setContent(content);
//                DragDropManager.setCurrentlyDraggedCard(getItem().getCard());
//                event.consume();
//            });
//            cardImageView.setOnDragDone(event -> {
//                DragDropManager.clearCurrentlyDraggedCard();
//                event.consume();
//            });
//        }
//
//        @Override
//        protected void updateItem(CardElement item, boolean empty) {
//            super.updateItem(item, empty);
//
//            // Cancel any previous outstanding load for this cell
//            if (imageLoadFuture != null && !imageLoadFuture.isDone()) {
//                imageLoadFuture.cancel(true);
//                imageLoadFuture = null;
//            }
//            currentImageKey = null;
//
//            if (empty || item == null) {
//                cardImageView.setImage(null);
//                wrapper.setStyle("-fx-background-color: transparent;");
//                return;
//            }
//
//            // Lightweight: compute imageKey only (no DB calls on FX thread)
//            String imageKey = safeImageKey(item);
//            currentImageKey = imageKey;
//
//            // Try to set cached image quickly (no blocking)
//            String resolvedCached = imageKey == null ? null : imagePathCache.get(imageKey);
//            if (resolvedCached != null) {
//                Image cached = LruImageCache.getImage(resolvedCached);
//                if (cached != null) {
//                    cardImageView.setImage(cached);
//                } else {
//                    // placeholder while background load runs
//                    cardImageView.setImage(getPlaceholderImage());
//                    imageLoadFuture = loadImageWithResolvedPathAsync(item, cardImageView, resolvedCached);
//                }
//            } else {
//                // No resolved path cached: set placeholder and resolve path asynchronously
//                cardImageView.setImage(getPlaceholderImage());
//                resolveImagePathAsync(imageKey, resolvedPath -> {
//                    // If cell was reused for another item, skip
//                    if (!Objects.equals(currentImageKey, imageKey)) {
//                        return;
//                    }
//                    if (resolvedPath == null) {
//                        // keep placeholder
//                        return;
//                    }
//                    // If image already cached in LRU, set it quickly on FX thread
//                    Image cached = LruImageCache.getImage(resolvedPath);
//                    if (cached != null) {
//                        Platform.runLater(() -> {
//                            if (Objects.equals(currentImageKey, imageKey)) {
//                                cardImageView.setImage(cached);
//                            }
//                        });
//                    } else {
//                        // start background load using resolved path
//                        Future<?> f = loadImageWithResolvedPathAsync(item, cardImageView, resolvedPath);
//                        // store future so we can cancel if cell is reused
//                        imageLoadFuture = f;
//                        if (f != null) outstandingLoads.put(cardImageView, f);
//                    }
//                });
//            }
//
//            // Apply selection style only if the active part is "MIDDLE".
//            if ("MIDDLE".equals(SelectionManager.getActivePart()) &&
//                    SelectionManager.getSelectedCards().contains(item.getCard())) {
//                wrapper.setStyle("-fx-border-color: #cdfc04; -fx-border-width: 3; -fx-border-radius: 8; -fx-background-radius: 8;");
//            } else {
//                wrapper.setStyle("-fx-background-color: transparent;");
//            }
//        }
//    }
//
//    // Helper methods to safely extract image keys
//    private String safeImageKey(CardElement item) {
//        if (item == null || item.getCard() == null) return null;
//        return item.getCard().getImagePath();
//    }
//
//    private String safeImageKey(Card card) {
//        if (card == null) return null;
//        return card.getImagePath();
//    }
//}


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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
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

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class CardTreeCell extends TreeCell<String> {

    private static final Logger logger = LoggerFactory.getLogger(CardTreeCell.class);
    // Executor for asynchronous image loading (decoding)
    private static final ExecutorService imageLoadingExecutor = Executors.newFixedThreadPool(4);
    // Executor for resolving image paths (off-FX thread)
    private static final ExecutorService pathResolverExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "image-path-resolver");
        t.setDaemon(true);
        return t;
    });
    // Cache for resolved image paths: imageKey -> "file:/full/path.jpg"
    private static final ConcurrentHashMap<String, String> imagePathCache = new ConcurrentHashMap<>();
    // Maximum resolution constants (kept from original)
    private static final double MAX_CARD_WIDTH = 300;
    private static final double MAX_CARD_HEIGHT = MAX_CARD_WIDTH * (146.0 / 100.0); // ≈438
    private final DoubleProperty cardWidthProperty;
    private final DoubleProperty cardHeightProperty;
    // Used for selection visuals.
    private final ObjectProperty<CardElement> selectedCardElement = new SimpleObjectProperty<>();
    // Keep track of outstanding load tasks per ImageView
    private final ConcurrentHashMap<ImageView, Future<?>> outstandingLoads = new ConcurrentHashMap<>();
    private Label customTriangleLabel;
    private GridView<CardElement> cardGridView;

    public CardTreeCell(DoubleProperty cardWidthProperty, DoubleProperty cardHeightProperty) {
        this.cardWidthProperty = cardWidthProperty;
        this.cardHeightProperty = cardHeightProperty;
        getStyleClass().add("card-tree-cell");
    }

    public static void shutdownImageLoadingExecutor() {
        imageLoadingExecutor.shutdownNow();
        pathResolverExecutor.shutdownNow();
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
                if (dataObject instanceof CardsGroup) {
                    createCardsGroupCell(itemName, (CardsGroup) dataObject);
                } else if (dataObject instanceof CardElement) {
                    // Single card row (rare in your UI). Keep this lightweight.
                    CardElement cardElement = (CardElement) dataObject;
                    HBox cardBox = new HBox(5);
                    cardBox.setAlignment(Pos.CENTER_LEFT);

                    ImageView imageView = new ImageView();
                    imageView.setFitWidth(cardWidthProperty.get());
                    imageView.setFitHeight(cardHeightProperty.get());
                    imageView.setPreserveRatio(true);

                    // Immediately set cached image if available (fast)
                    String imageKey = safeImageKey(cardElement);
                    String cachedFullPath = imageKey == null ? null : imagePathCache.get(imageKey);
                    if (cachedFullPath != null) {
                        Image cached = LruImageCache.getImage(cachedFullPath);
                        if (cached != null) {
                            imageView.setImage(cached);
                        } else {
                            imageView.setImage(getPlaceholderImage());
                            // start background load using resolved path
                            Future<?> f = loadImageWithResolvedPathAsync(cardElement, imageView, cachedFullPath);
                            if (f != null) outstandingLoads.put(imageView, f);
                        }
                    } else {
                        // No cached path: set placeholder and resolve path asynchronously
                        imageView.setImage(getPlaceholderImage());
                        resolveImagePathAsync(imageKey, resolvedPath -> {
                            if (resolvedPath == null) {
                                // no path found -> keep placeholder
                                return;
                            }
                            // If image already cached in LRU, set it quickly on FX thread
                            Image cached = LruImageCache.getImage(resolvedPath);
                            if (cached != null) {
                                Platform.runLater(() -> {
                                    // ensure imageView still expects this path
                                    Object expected = imageView.getProperties().get("expectedImagePath");
                                    if (Objects.equals(expected, resolvedPath) || expected == null) {
                                        imageView.setImage(cached);
                                        imageView.getProperties().remove("expectedImagePath");
                                    }
                                });
                            } else {
                                // start background load using resolved path
                                // mark the imageView as expecting this resolved path
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

    /**
     * Resolve an imageKey to a full file:... path off the FX thread and cache it.
     * The callback is invoked on the pathResolverExecutor thread; it may call
     * background loaders which will themselves use Platform.runLater when needed.
     */
    private void resolveImagePathAsync(String imageKey, Consumer<String> callback) {
        if (imageKey == null) {
            callback.accept(null);
            return;
        }
        // If cached, return immediately (fast)
        String cached = imagePathCache.get(imageKey);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        // Otherwise submit a resolver task (non-FX)
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

    /**
     * Load an image given a resolved path (file:...) on the imageLoadingExecutor and set it on the ImageView.
     * Returns the Future so callers can cancel if the cell is reused.
     * <p>
     * IMPORTANT: this method sets and checks the ImageView property "expectedImagePath" to avoid races.
     */
    private Future<?> loadImageWithResolvedPathAsync(CardElement cardElement, ImageView imageView, String resolvedPath) {
        if (resolvedPath == null) {
            Platform.runLater(() -> imageView.setImage(getPlaceholderImage()));
            return null;
        }

        // If already cached in LRU, set immediately
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

        // Mark the imageView as expecting this resolved path (helps avoid races)
        imageView.getProperties().put("expectedImagePath", resolvedPath);

        // Submit background task to create Image (decoding) off FX thread
        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        Future<?> future = imageLoadingExecutor.submit(() -> {
            try {
                // Create Image with backgroundLoading = true to avoid heavy decoding on FX thread.
                Image img = new Image(resolvedPath, cardWidthProperty.get(), cardHeightProperty.get(), true, true, true);

                // If already fully loaded, cache and set
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
                    // Attach listener on FX thread to set when loading completes
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
                    // only set placeholder if the imageView still expects this path (or no expectation)
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

    private String getImagePath(Card card) {
        if (card == null || card.getImagePath() == null) return null;
        String imageKey = card.getImagePath();
        String[] addresses = DataBaseUpdate.getAddresses(imageKey + ".jpg");
        if (addresses != null && addresses.length > 0) {
            return "file:" + addresses[0];
        }
        return null;
    }

    private void createCardsGroupCell(String itemName, CardsGroup group) {
        HBox hbox = new HBox();
        hbox.getStyleClass().add("card-group-hbox");
        hbox.setSpacing(5);

        customTriangleLabel = new Label();
        customTriangleLabel.getStyleClass().add("custom-triangle-label");
        customTriangleLabel.setMinWidth(15);
        customTriangleLabel.setAlignment(Pos.CENTER);

        Label label = new Label(itemName);
        label.getStyleClass().add("card-group-label");

        hbox.getChildren().addAll(customTriangleLabel, label);

        VBox vBox = new VBox();
        vBox.getStyleClass().add("card-group-vbox");
        vBox.getChildren().add(hbox);

        // Reuse a single observable list instance to avoid allocations on each update
        cardGridView = new GridView<>();
        cardGridView.getStyleClass().add("card-grid-view");
        cardGridView.setCellFactory(gridView -> new CardGridCell());
        cardGridView.setItems(FXCollections.observableArrayList(group.getCardList()));
        cardGridView.cellWidthProperty().bind(cardWidthProperty);
        cardGridView.cellHeightProperty().bind(cardHeightProperty);
        cardGridView.setHorizontalCellSpacing(5);
        cardGridView.setVerticalCellSpacing(5);
        cardGridView.setPadding(new Insets(5));
        cardGridView.prefWidthProperty().bind(getTreeView().widthProperty().subtract(50));

        adjustGridViewHeight(group);
        cardWidthProperty.addListener((obs, oldVal, newVal) -> adjustGridViewHeight(group));
        getTreeView().widthProperty().addListener((obs, oldVal, newVal) -> adjustGridViewHeight(group));

        customTriangleLabel.setOnMouseClicked(event -> {
            boolean isExpanded = !cardGridView.isVisible();
            cardGridView.setVisible(isExpanded);
            cardGridView.setManaged(isExpanded);
            updateCustomTriangle(isExpanded);
            event.consume();
        });
        cardGridView.setVisible(true);
        cardGridView.setManaged(true);
        updateCustomTriangle(true);

        vBox.getChildren().add(cardGridView);
        VBox.setVgrow(cardGridView, Priority.ALWAYS);
        setGraphic(vBox);
    }

    private void adjustGridViewHeight(CardsGroup group) {
        int numItems = group.getCardList().size();
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

    private void updateCustomTriangle(boolean isExpanded) {
        String triangle = isExpanded ? "\u25BC" : "\u25B6"; // ▼ or ▶
        customTriangleLabel.setText(triangle);
    }

    private void selectCard(CardElement cardElement) {
        selectedCardElement.set(cardElement);
        logger.info("Selected card: {}", cardElement.getCard().getName_EN());
    }

    private Image getPlaceholderImage() {
        // Keep a single placeholder instance to avoid repeated file reads
        return PlaceholderHolder.PLACEHOLDER;
    }

    // Helper methods to safely extract image keys
    private String safeImageKey(CardElement item) {
        if (item == null || item.getCard() == null) return null;
        return item.getCard().getImagePath();
    }

    private String safeImageKey(Card card) {
        if (card == null) return null;
        return card.getImagePath();
    }

    private static class PlaceholderHolder {
        static final Image PLACEHOLDER = new Image("file:./src/main/resources/placeholder.jpg");
    }

    // Inner class: Custom GridCell for displaying individual cards.
    private class CardGridCell extends GridCell<CardElement> {
        private final ImageView cardImageView;
        private final StackPane wrapper;
        private Future<?> imageLoadFuture;
        private String currentImageKey; // track which key this cell currently represents

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

            // Selection handler for the middle part using "MIDDLE"
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

            // Drag handling
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

            // Cancel any previous outstanding load for this cell and clear expected path
            if (imageLoadFuture != null && !imageLoadFuture.isDone()) {
                imageLoadFuture.cancel(true);
                imageLoadFuture = null;
            }
            // Clear the expectedImagePath property so any late runnables won't set the old image
            cardImageView.getProperties().remove("expectedImagePath");
            currentImageKey = null;

            if (empty || item == null) {
                cardImageView.setImage(null);
                wrapper.setStyle("-fx-background-color: transparent;");
                return;
            }

            // Lightweight: compute imageKey only (no DB calls on FX thread)
            String imageKey = safeImageKey(item);
            currentImageKey = imageKey;

            // Try to set cached image quickly (no blocking)
            String resolvedCached = imageKey == null ? null : imagePathCache.get(imageKey);
            if (resolvedCached != null) {
                Image cached = LruImageCache.getImage(resolvedCached);
                if (cached != null) {
                    cardImageView.setImage(cached);
                    // ensure no stale expected path remains
                    cardImageView.getProperties().remove("expectedImagePath");
                } else {
                    // placeholder while background load runs
                    cardImageView.setImage(getPlaceholderImage());
                    // mark expected path and start load
                    cardImageView.getProperties().put("expectedImagePath", resolvedCached);
                    imageLoadFuture = loadImageWithResolvedPathAsync(item, cardImageView, resolvedCached);
                    if (imageLoadFuture != null) outstandingLoads.put(cardImageView, imageLoadFuture);
                }
            } else {
                // No resolved path cached: set placeholder and resolve path asynchronously
                cardImageView.setImage(getPlaceholderImage());
                resolveImagePathAsync(imageKey, resolvedPath -> {
                    // If cell was reused for another item, skip
                    if (!Objects.equals(currentImageKey, imageKey)) {
                        return;
                    }
                    if (resolvedPath == null) {
                        // keep placeholder
                        return;
                    }
                    // If image already cached in LRU, set it quickly on FX thread
                    Image cached = LruImageCache.getImage(resolvedPath);
                    if (cached != null) {
                        Platform.runLater(() -> {
                            if (Objects.equals(currentImageKey, imageKey)) {
                                cardImageView.setImage(cached);
                                cardImageView.getProperties().remove("expectedImagePath");
                            }
                        });
                    } else {
                        // start background load using resolved path
                        cardImageView.getProperties().put("expectedImagePath", resolvedPath);
                        Future<?> f = loadImageWithResolvedPathAsync(item, cardImageView, resolvedPath);
                        imageLoadFuture = f;
                        if (f != null) outstandingLoads.put(cardImageView, f);
                    }
                });
            }

            // Apply selection style only if the active part is "MIDDLE".
            if ("MIDDLE".equals(SelectionManager.getActivePart()) &&
                    SelectionManager.getSelectedCards().contains(item.getCard())) {
                wrapper.setStyle("-fx-border-color: #cdfc04; -fx-border-width: 3; -fx-border-radius: 8; -fx-background-radius: 8;");
            } else {
                wrapper.setStyle("-fx-background-color: transparent;");
            }
        }
    }
}
