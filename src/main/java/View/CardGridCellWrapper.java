package View;

import Model.CardsLists.CardElement;
import Model.Database.DataBaseUpdate;
import Utils.LruImageCache;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import org.controlsfx.control.GridCell;
import org.controlsfx.control.GridView;

import java.util.*;

public class CardGridCellWrapper extends GridCell<CardElement> {
    private ImageView cardImageView;
    private DoubleProperty cardWidthProperty;
    private DoubleProperty cardHeightProperty;

    public CardGridCellWrapper(DoubleProperty cardWidthProperty, DoubleProperty cardHeightProperty) {
        this.cardWidthProperty = cardWidthProperty;
        this.cardHeightProperty = cardHeightProperty;
        cardImageView = new ImageView();
        cardImageView.setPreserveRatio(true);
        cardImageView.fitWidthProperty().bind(cardWidthProperty);
        cardImageView.fitHeightProperty().bind(cardHeightProperty);

        StackPane pane = new StackPane(cardImageView);
        pane.setPadding(new Insets(5));
        setGraphic(pane);
    }

    @Override
    protected void updateItem(CardElement cardElement, boolean empty) {
        super.updateItem(cardElement, empty);

        final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CardGridCellWrapper.class);
        logger.debug("updateItem called: cardElement={} empty={}", cardElement, empty);

        // Clear state for empty cells
        if (empty || cardElement == null) {
            logger.debug("Clearing graphic for empty cell");

            // Remove any per-pane strong listener we stored previously (cleanup)
            try {
                StackPane existingPane = (StackPane) getGraphic();
                if (existingPane != null) {
                    Object strong = existingPane.getProperties().get("tabSelectionListenerStrong");
                    Object registeredOn = existingPane.getProperties().get("tabSelectionListenerRegisteredOn");
                    if (strong instanceof ChangeListener && registeredOn instanceof TabPane) {
                        try {
                            ((TabPane) registeredOn).getSelectionModel().selectedItemProperty().removeListener((ChangeListener<Tab>) strong);
                            logger.debug("Removed strong tab listener from TabPane during clear: {}", registeredOn);
                        } catch (Exception ignored) {
                        }
                    }
                    existingPane.getProperties().remove("tabSelectionListenerStrong");
                    existingPane.getProperties().remove("tabSelectionListenerRegisteredOn");
                    existingPane.getProperties().remove("tabSelectionSceneListener");
                }
            } catch (Exception ignored) {
            }

            cardImageView.setImage(null);
            cardImageView.setEffect(null);
            StackPane existingPane = (StackPane) getGraphic();
            if (existingPane != null) existingPane.setEffect(null);
            setGraphic(null);
            return;
        }

        // Ensure consistent graphic container
        StackPane pane = (StackPane) getGraphic();
        if (pane == null) {
            pane = new StackPane(cardImageView);
            pane.setPadding(new Insets(5));
            setGraphic(pane);
        }

        // final references for lambdas to avoid cell-reuse issues
        final StackPane finalPane = pane;
        final CardElement finalCardElement = cardElement;

        // --- Helpers --------------------------------------------------------

        // Lightweight tab detection supplier (keeps previous behavior)
        final java.util.function.Supplier<Boolean> isOuicheListSelected = () -> isOuicheListTabSelected();

        // Lightweight detection for My Collection tab (explicit name)
        final java.util.function.Supplier<Boolean> isMyCollectionSelected = () -> isMyCollectionTabSelected();

        // Apply grayscale to the imageView if this is OuicheList and the CardElement is owned.
        final Runnable applyGrayscaleIfNeeded = () -> {
            logger.debug("applyGrayscaleIfNeeded called for item={}, owned={}", finalCardElement, finalCardElement == null ? "<null>" : finalCardElement.getOwned());
            Platform.runLater(() -> {
                // Guard against cell reuse
                if (getItem() != finalCardElement) return;
                try {
                    boolean owned = finalCardElement.getOwned() != null && finalCardElement.getOwned();
                    boolean ouiche = false;
                    try {
                        ouiche = isOuicheListSelected.get();
                    } catch (Exception ignored) {
                    }
                    // inside Platform.runLater in applyGrayscaleIfNeeded
                    String detectedTab = "<unknown>";
                    try {
                        Scene s = cardImageView == null ? null : cardImageView.getScene();
                        if (s != null) {
                            Node lookup = s.lookup("#mainTabPane");
                            if (lookup instanceof TabPane) {
                                Tab sel = ((TabPane) lookup).getSelectionModel().getSelectedItem();
                                detectedTab = sel == null ? "<null>" : sel.getText();
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    logger.debug("Cell sees selected tab='{}' (owned={}) for item={}", detectedTab, owned, finalCardElement);
                    if (owned && ouiche) {
                        ColorAdjust grayscale = new ColorAdjust();
                        grayscale.setSaturation(-1.0);
                        cardImageView.setEffect(grayscale);
                    } else {
                        // Important: do not clear pane effect here; only clear imageView effect
                        cardImageView.setEffect(null);
                    }
                } catch (Exception e) {
                    cardImageView.setEffect(null);
                }
            });
        };

        // Compute and apply glow on the container (finalPane) when needed.
        // NOTE: computeCardNeedsSorting is now called only when the "My Collection" tab is selected.
        final Runnable applyGlowIfNeeded = () -> {
            Platform.runLater(() -> {
                // Guard against cell reuse
                if (getItem() != finalCardElement) {
                    finalPane.setEffect(null);
                    return;
                }
                try {
                    boolean needsSorting = false;

                    // Find ancestor GridView and read its userData (same structure as CardTreeCell)
                    Object ud = null;
                    Node node = this;
                    while (node != null && !(node instanceof GridView)) node = node.getParent();
                    if (node instanceof GridView) ud = ((GridView<?>) node).getUserData();

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

                    // Archetype missing-set logic (unchanged)
                    if (missingSet != null && !missingSet.isEmpty()) {
                        String konamiId = finalCardElement.getCard() == null ? null : finalCardElement.getCard().getKonamiId();
                        String passCode = finalCardElement.getCard() == null ? null : finalCardElement.getCard().getPassCode();
                        boolean missing = false;
                        if (konamiId != null && missingSet.contains(konamiId)) missing = true;
                        if (!missing && passCode != null && missingSet.contains(passCode)) missing = true;
                        needsSorting = missing;
                    } else {
                        // If no missing set, compute unsorted only when "My Collection" tab is selected
                        boolean isMyCollection = false;
                        try {
                            isMyCollection = isMyCollectionSelected.get();
                        } catch (Exception ignored) {
                        }

                        if (elementNameFromUD != null && !elementNameFromUD.trim().isEmpty() && isMyCollection) {
                            try {
                                needsSorting = Controller.RealMainController.computeCardNeedsSorting(finalCardElement.getCard(), elementNameFromUD);
                            } catch (Throwable t) {
                                needsSorting = false;
                            }
                        } else {
                            needsSorting = false;
                        }
                    }

                    // Apply glow to the finalPane (keeps other visuals intact)
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
                        finalPane.setEffect(outerGlow);
                    } else {
                        finalPane.setEffect(null);
                    }
                } catch (Exception e) {
                    finalPane.setEffect(null);
                }
            });
        };

        // --- Register a single global TabPane listener on the scene (one-time per scene).
        // When the selected tab changes we iterate the scene and call reapplyEffectsForCurrentItem()
        // on every live CardGridCellWrapper instance. This avoids calling non-existent refresh/requestLayout.
        try {
            final org.slf4j.Logger regLogger = org.slf4j.LoggerFactory.getLogger(CardGridCellWrapper.class);
            Scene scene = (cardImageView == null) ? null : cardImageView.getScene();
            if (scene != null) {
                final Object registered = scene.getProperties().get("ouicheGlobalTabListenerRegistered");
                if (registered == null) {
                    // Try to find the TabPane (prefer fx:id lookup)
                    TabPane mainTabPane = null;
                    try {
                        Node lookup = scene.lookup("#mainTabPane");
                        if (lookup instanceof TabPane) mainTabPane = (TabPane) lookup;
                    } catch (Exception ignored) {
                    }

                    // fallback: search scene graph for a TabPane
                    if (mainTabPane == null) {
                        Parent root = scene.getRoot();
                        Deque<Node> stack = new ArrayDeque<>();
                        stack.push(root);
                        while (!stack.isEmpty()) {
                            Node cur = stack.pop();
                            if (cur instanceof TabPane) {
                                mainTabPane = (TabPane) cur;
                                break;
                            }
                            if (cur instanceof Parent) {
                                for (Node child : ((Parent) cur).getChildrenUnmodifiable()) stack.push(child);
                            }
                        }
                    }

                    if (mainTabPane != null) {
                        ChangeListener<Tab> globalListener = (obs, oldTab, newTab) -> {
                            try {
                                String name = (newTab == null ? "<null>" : newTab.getText());
                                regLogger.debug("Global tab changed -> {}", name);
                            } catch (Exception ignored) {
                            }

                            // Traverse scene graph and call reapplyEffectsForCurrentItem on each live CardGridCellWrapper
                            try {
                                Parent root = scene.getRoot();
                                Deque<Node> stack = new ArrayDeque<>();
                                stack.push(root);
                                while (!stack.isEmpty()) {
                                    Node cur = stack.pop();
                                    if (cur instanceof CardGridCellWrapper) {
                                        try {
                                            ((CardGridCellWrapper) cur).reapplyEffectsForCurrentItem();
                                        } catch (Exception e) {
                                            regLogger.warn("Error reapplying effects for cell", e);
                                        }
                                    }
                                    if (cur instanceof Parent) {
                                        for (Node child : ((Parent) cur).getChildrenUnmodifiable()) stack.push(child);
                                    }
                                }
                            } catch (Exception e) {
                                regLogger.warn("Error while iterating scene nodes on tab change", e);
                            }
                        };

                        // Register the listener and keep a strong reference in scene properties
                        mainTabPane.getSelectionModel().selectedItemProperty().addListener(globalListener);
                        scene.getProperties().put("ouicheGlobalTabListenerRegistered", Boolean.TRUE);
                        scene.getProperties().put("ouicheGlobalTabListenerStrongRef", globalListener);
                        regLogger.debug("Registered global tab listener on TabPane: {}", mainTabPane);

                        // Trigger an initial run so current visible cells reflect the current tab immediately
                        try {
                            globalListener.changed(null, null, mainTabPane.getSelectionModel().getSelectedItem());
                        } catch (Exception ignored) {
                        }
                    } else {
                        regLogger.debug("No TabPane found in scene when attempting to register global listener");
                    }
                }
            } else {
                regLogger.debug("Scene not available yet; global listener registration deferred until scene is set");
                // Subsequent updateItem calls will attempt registration again.
            }
        } catch (Exception ignored) {
        }

        // --- Image loading (non-blocking) ----------------------------------

        // Build image key (same logic as CardTreeCell.safeImageKey)
        String imageKey = null;
        try {
            if (finalCardElement.getCard() != null) imageKey = finalCardElement.getCard().getImagePath();
        } catch (Exception ignored) {
        }

        logger.debug("imageKey resolved to {}", imageKey);

        // Try to use a cached resolved path first (LruImageCache stores by resolved path)
        if (imageKey != null) {
            try {
                if (imageKey.startsWith("file:") || imageKey.startsWith("http")) {
                    Image cached = LruImageCache.getImage(imageKey);
                    if (cached != null) {
                        logger.debug("Cache hit for resolved imageKey {}", imageKey);
                        if (getItem() == finalCardElement) {
                            cardImageView.setImage(cached);
                            applyGrayscaleIfNeeded.run();
                            applyGlowIfNeeded.run();
                        }
                        return;
                    } else {
                        logger.debug("No cached image for resolved imageKey {}", imageKey);
                    }
                }
            } catch (Exception ignored) {
            }
        } else {
            logger.debug("No imageKey available for this cardElement");
        }

        // Show placeholder immediately
        Image placeholder = new Image("file:./src/main/resources/placeholder.jpg",
                cardWidthProperty.get(), cardHeightProperty.get(), true, true, true);
        logger.debug("Setting placeholder image");
        cardImageView.setImage(placeholder);
        applyGrayscaleIfNeeded.run();
        applyGlowIfNeeded.run();

        // Resolve path and load image off the FX thread to avoid blocking during scrolling.
        final String finalImageKey = imageKey;
        Thread loader = new Thread(() -> {
            try {
                String resolved = null;
                if (finalImageKey != null) {
                    try {
                        String[] addresses = DataBaseUpdate.getAddresses(finalImageKey + ".jpg");
                        if (addresses != null && addresses.length > 0) {
                            resolved = "file:" + addresses[0];
                            logger.debug("Resolved path for {} -> {}", finalImageKey, resolved);
                        } else {
                            logger.debug("No addresses returned for imageKey {}", finalImageKey);
                        }
                    } catch (Throwable t) {
                        resolved = null;
                        logger.warn("Exception while resolving addresses for {}", finalImageKey, t);
                    }
                }

                final String resolvedPath = resolved;

                // If resolved path exists, check cache
                if (resolvedPath != null) {
                    Image cached = LruImageCache.getImage(resolvedPath);
                    if (cached != null) {
                        logger.debug("Cache hit for resolvedPath {}", resolvedPath);
                        Platform.runLater(() -> {
                            if (getItem() == finalCardElement) {
                                cardImageView.setImage(cached);
                                applyGrayscaleIfNeeded.run();
                                applyGlowIfNeeded.run();
                            }
                        });
                        return;
                    }
                }

                // If no resolved path, keep placeholder and exit
                if (resolvedPath == null) {
                    logger.debug("Resolved path is null, keeping placeholder");
                    Platform.runLater(() -> {
                        if (getItem() == finalCardElement) {
                            applyGrayscaleIfNeeded.run();
                            applyGlowIfNeeded.run();
                        }
                    });
                    return;
                }

                // Load image with JavaFX background loading enabled
                final Image img = new Image(resolvedPath, cardWidthProperty.get(), cardHeightProperty.get(), true, true, true);

                if (img.getProgress() >= 1.0) {
                    try {
                        LruImageCache.addImage(resolvedPath, img);
                    } catch (Exception ignored) {
                    }
                    logger.debug("Image already fully loaded for {}", resolvedPath);
                    Platform.runLater(() -> {
                        if (getItem() == finalCardElement) {
                            cardImageView.setImage(img);
                            applyGrayscaleIfNeeded.run();
                            applyGlowIfNeeded.run();
                        }
                    });
                } else {
                    logger.debug("Image loading in background for {}", resolvedPath);
                    // Keep placeholder until image finishes loading
                    img.progressProperty().addListener((obs, oldV, newV) -> {
                        if (newV.doubleValue() >= 1.0) {
                            try {
                                LruImageCache.addImage(resolvedPath, img);
                            } catch (Exception ignored) {
                            }
                            logger.debug("Background load finished for {}", resolvedPath);
                            Platform.runLater(() -> {
                                if (getItem() == finalCardElement) {
                                    cardImageView.setImage(img);
                                    applyGrayscaleIfNeeded.run();
                                    applyGlowIfNeeded.run();
                                }
                            });
                        }
                    });
                }
            } catch (Throwable t) {
                logger.warn("Exception while loading image for cardElement", t);
                Platform.runLater(() -> {
                    if (getItem() == finalCardElement) {
                        cardImageView.setImage(placeholder);
                        applyGrayscaleIfNeeded.run();
                        applyGlowIfNeeded.run();
                    }
                });
            }
        }, "cardgrid-image-loader");
        loader.setDaemon(true);
        loader.start();
    }

    /**
     * Called by the global tab-change listener to force this cell to re-evaluate and reapply its effects.
     * Safe to call from any thread; schedules UI updates on the FX thread and guards against cell reuse.
     */
    private void reapplyEffectsForCurrentItem() {
        final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CardGridCellWrapper.class);
        final CardElement current = getItem();
        if (current == null) return;

        Platform.runLater(() -> {
            try {
                if (getItem() != current) return;

                // GRAYSCALE
                boolean owned = current.getOwned() != null && current.getOwned();
                boolean ouiche = isOuicheListTabSelected();
                logger.debug("reapplyEffectsForCurrentItem: owned={} ouicheSelected={}", owned, ouiche);
                if (owned && ouiche) {
                    ColorAdjust grayscale = new ColorAdjust();
                    grayscale.setSaturation(-1.0);
                    cardImageView.setEffect(grayscale);
                } else {
                    cardImageView.setEffect(null);
                }

                // GLOW (reuse same logic as applyGlowIfNeeded but only final application)
                boolean needsSorting = false;
                Object ud = null;
                Node node = this;
                while (node != null && !(node instanceof GridView)) node = node.getParent();
                if (node instanceof GridView) ud = ((GridView<?>) node).getUserData();

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

                if (missingSet != null && !missingSet.isEmpty()) {
                    String konamiId = current.getCard() == null ? null : current.getCard().getKonamiId();
                    String passCode = current.getCard() == null ? null : current.getCard().getPassCode();
                    boolean missing = false;
                    if (konamiId != null && missingSet.contains(konamiId)) missing = true;
                    if (!missing && passCode != null && missingSet.contains(passCode)) missing = true;
                    needsSorting = missing;
                } else {
                    if (elementNameFromUD != null && !elementNameFromUD.trim().isEmpty() && isMyCollectionTabSelected()) {
                        try {
                            needsSorting = Controller.RealMainController.computeCardNeedsSorting(current.getCard(), elementNameFromUD);
                        } catch (Throwable t) {
                            needsSorting = false;
                        }
                    } else {
                        needsSorting = false;
                    }
                }

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
                    Node g = getGraphic();
                    if (g instanceof StackPane) {
                        ((StackPane) g).setEffect(outerGlow);
                    } else {
                        try {
                            StackPane pane = (StackPane) getGraphic();
                            if (pane != null) pane.setEffect(outerGlow);
                        } catch (Exception ignored) {
                        }
                    }
                } else {
                    Node g = getGraphic();
                    if (g instanceof StackPane) {
                        ((StackPane) g).setEffect(null);
                    } else {
                        try {
                            StackPane pane = (StackPane) getGraphic();
                            if (pane != null) pane.setEffect(null);
                        } catch (Exception ignored) {
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("reapplyEffectsForCurrentItem failed", e);
            }
        });
    }

    private TabPane findNearestTabPane() {
        try {
            // Prefer an explicit fx:id lookup if your main TabPane has id "mainTabPane"
            if (cardImageView != null && cardImageView.getScene() != null) {
                Node lookup = cardImageView.getScene().lookup("#mainTabPane");
                if (lookup instanceof TabPane) return (TabPane) lookup;
            }

            // Fallback: walk up parents from this cell
            Node n = this;
            while (n != null && !(n instanceof TabPane)) n = n.getParent();
            if (n instanceof TabPane) return (TabPane) n;

            // Final fallback: search scene graph for a TabPane
            if (cardImageView != null && cardImageView.getScene() != null) {
                Parent root = cardImageView.getScene().getRoot();
                Deque<Node> stack = new ArrayDeque<>();
                stack.push(root);
                while (!stack.isEmpty()) {
                    Node cur = stack.pop();
                    if (cur instanceof TabPane) return (TabPane) cur;
                    if (cur instanceof Parent) {
                        for (Node child : ((Parent) cur).getChildrenUnmodifiable()) stack.push(child);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }



    /**
     * Resolves the image path using a method similar to that used in CardTreeCell.
     */
    private String getImagePath(CardElement cardElement) {
        if (cardElement == null || cardElement.getCard() == null || cardElement.getCard().getImagePath() == null) {
            return null;
        }
        // Get the base image key.
        String imageKey = cardElement.getCard().getImagePath();
        // Retrieve possible addresses using DataBaseUpdate.
        String[] addresses = DataBaseUpdate.getAddresses(imageKey + ".jpg");
        if (addresses != null && addresses.length > 0) {
            return "file:" + addresses[0];
        }
        return null;
    }

    private boolean isOuicheListTabSelected() {
        try {
            TabPane tp = findNearestTabPane();
            if (tp != null) {
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

    private boolean isMyCollectionTabSelected() {
        try {
            TabPane tp = findNearestTabPane();
            if (tp != null) {
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
}