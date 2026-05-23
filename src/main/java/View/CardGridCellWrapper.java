package View;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.Database.DataBaseUpdate;
import Utils.LruImageCache;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.controlsfx.control.GridCell;
import org.controlsfx.control.GridView;

import java.util.*;

public class CardGridCellWrapper extends GridCell<CardElement> {
    private ImageView cardImageView;
    private DoubleProperty cardWidthProperty;
    private DoubleProperty cardHeightProperty;

    /**
     * Single context-menu instance reused across all update cycles of this cell.
     */
    private final ContextMenu cellContextMenu;

    // Hover popup — one instance per cell, shared across update cycles.
    // Mouse handlers are attached to finalPane in updateItem so the popup
    // always shows the text for the currently-displayed card.
    private final javafx.stage.Popup hoverPopup = new javafx.stage.Popup();
    private final Label hoverLabel = new Label();

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

        // Initialise hover popup
        hoverLabel.setWrapText(true);
        hoverLabel.setMaxWidth(260);
        hoverLabel.setStyle(CardHoverPopup.LABEL_STYLE);
        hoverPopup.getContent().add(hoverLabel);
        hoverPopup.setAutoFix(true);
        hoverPopup.setAutoHide(false);

        // Build the context menu once; items use getItem() at action time so
        // they always operate on the element currently shown in this cell.
        cellContextMenu = buildCellContextMenu();

        // Show the context menu on right-click, but only in the right tabs
        // and never for archetype (immutable) grid views.
        setOnContextMenuRequested(event -> {
            CardElement ce = getItem();
            if (ce == null || !isEditableTab() || isImmutableCell()) {
                event.consume();
                return;
            }
            cellContextMenu.show(this, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    @Override
    protected void updateItem(CardElement cardElement, boolean empty) {
        super.updateItem(cardElement, empty);

        final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CardGridCellWrapper.class);
        logger.debug("updateItem called: cardElement={} empty={}", cardElement, empty);

        // Clear state for empty cells
        if (empty || cardElement == null) {
            logger.debug("Clearing graphic for empty cell");
            setTooltip(null);
            hoverPopup.hide();
            Node g = getGraphic();
            if (g != null) {
                g.setOnMouseEntered(null);
                g.setOnMouseMoved(null);
                g.setOnMouseExited(null);
            }

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

        // Read elementName from GridView userData once, for use in hover and glow logic.
        String _elementNameUD = null;
        try {
            Node _n = this;
            while (_n != null && !(_n instanceof GridView)) _n = _n.getParent();
            if (_n instanceof GridView) {
                Object _ud = ((GridView<?>) _n).getUserData();
                if (_ud instanceof Map) {
                    Object _en = ((Map<?, ?>) _ud).get("elementName");
                    if (_en instanceof String) _elementNameUD = (String) _en;
                } else if (_ud instanceof String) {
                    _elementNameUD = (String) _ud;
                }
            }
        } catch (Exception ignored) {
        }
        final String elementNameFromUD_hover = _elementNameUD != null ? _elementNameUD : "";

        // Hover popup — attach directly to finalPane (the graphic StackPane)
        // rather than using setTooltip() on the GridCell, because the graphic
        // covers the entire cell and consumes mouse events before they reach
        // the cell Control, which would prevent Tooltip from ever triggering.
        Card tooltipCard = (cardElement.getCard() != null) ? cardElement.getCard() : new Card();
        final String baseTooltipText = CardHoverPopup.buildTooltipText(cardElement);
        finalPane.setOnMouseEntered(e -> {
            // Re-check degraded status at hover time so the style is always current.
            boolean degradedNow = false;
            try {
                if (!elementNameFromUD_hover.isEmpty() && isDecksAndCollectionsTabSelected()) {
                    degradedNow = Controller.CardQualityService
                            .isDegradedCopyInDeckOrCollection(finalCardElement, elementNameFromUD_hover);
                }
            } catch (Throwable ignored) {
            }
            String tooltipText = degradedNow
                    ? baseTooltipText + "\n" + CardHoverPopup.DOWNGRADE_WARNING
                    : baseTooltipText;
            hoverLabel.setText(tooltipText);
            hoverLabel.setStyle(degradedNow
                    ? CardHoverPopup.LABEL_STYLE_ORANGE
                    : CardHoverPopup.LABEL_STYLE);
            hoverPopup.show(finalPane, e.getScreenX() + 14, e.getScreenY() + 14);
        });
        finalPane.setOnMouseMoved(e ->
                hoverPopup.show(finalPane, e.getScreenX() + 14, e.getScreenY() + 14)
        );
        finalPane.setOnMouseExited(e -> hoverPopup.hide());

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
                        grayscale.setSaturation(-0.7);   // 70% desaturation
                        grayscale.setBrightness(-0.5);   // darken by 50%
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
                                needsSorting = Controller.CardQualityService.computeCardNeedsSortingWithUpgrade(finalCardElement, elementNameFromUD);
                            } catch (Throwable t) {
                                needsSorting = false;
                            }
                        } else {
                            needsSorting = false;
                        }
                    }

                    // --- Degraded-in-deck check (Decks & Collections tab) ---
                    // When this card is inside a deck/collection and the owned collection
                    // contains a better copy, glow orange.
                    boolean isDegraded = false;
                    if (!needsSorting && elementNameFromUD != null && !elementNameFromUD.trim().isEmpty()) {
                        boolean isDecksTab = false;
                        try {
                            isDecksTab = isDecksAndCollectionsTabSelected();
                        } catch (Exception ignored) {
                        }
                        if (isDecksTab) {
                            try {
                                isDegraded = Controller.CardQualityService
                                        .isDegradedCopyInDeckOrCollection(finalCardElement, elementNameFromUD);
                            } catch (Throwable t) {
                                isDegraded = false;
                            }
                        }
                    }

                    // Apply glow to the finalPane (keeps other visuals intact)
                    if (needsSorting || isDegraded) {
                        String glowColor = isDegraded ? "#EB9E34" : "#ffffff";
                        double outerAlpha = isDegraded ? 0.35 : 0.22;
                        DropShadow innerGlow = new DropShadow();
                        innerGlow.setColor(javafx.scene.paint.Color.web(glowColor, 1.0));
                        innerGlow.setOffsetX(0);
                        innerGlow.setOffsetY(0);
                        innerGlow.setRadius(4);
                        innerGlow.setSpread(0.9);

                        DropShadow outerGlow = new DropShadow();
                        outerGlow.setColor(javafx.scene.paint.Color.web(glowColor, outerAlpha));
                        outerGlow.setOffsetX(0);
                        outerGlow.setOffsetY(0);
                        outerGlow.setRadius(14);
                        outerGlow.setSpread(0.12);

                        outerGlow.setInput(innerGlow);
                        finalPane.setEffect(outerGlow);

                        // Orange hover label for degraded cards
                        hoverLabel.setStyle(isDegraded
                                ? CardHoverPopup.LABEL_STYLE_ORANGE
                                : CardHoverPopup.LABEL_STYLE);
                    } else {
                        finalPane.setEffect(null);
                        hoverLabel.setStyle(CardHoverPopup.LABEL_STYLE);
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
                    grayscale.setSaturation(-0.8);   // 80% desaturation
                    grayscale.setBrightness(-0.3);   // darken by 30%
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
                            needsSorting = Controller.CardQualityService.computeCardNeedsSortingWithUpgrade(current, elementNameFromUD);
                        } catch (Throwable t) {
                            needsSorting = false;
                        }
                    } else {
                        needsSorting = false;
                    }
                }

                // Degraded-in-deck check
                boolean isDegraded = false;
                if (!needsSorting && elementNameFromUD != null && !elementNameFromUD.trim().isEmpty()) {
                    try {
                        if (isDecksAndCollectionsTabSelected()) {
                            isDegraded = Controller.CardQualityService
                                    .isDegradedCopyInDeckOrCollection(current, elementNameFromUD);
                        }
                    } catch (Throwable t) {
                        isDegraded = false;
                    }
                }

                if (needsSorting || isDegraded) {
                    String glowColor = isDegraded ? "#EB9E34" : "#ffffff";
                    double outerAlpha = isDegraded ? 0.35 : 0.22;
                    DropShadow innerGlow = new DropShadow();
                    innerGlow.setColor(javafx.scene.paint.Color.web(glowColor, 1.0));
                    innerGlow.setOffsetX(0);
                    innerGlow.setOffsetY(0);
                    innerGlow.setRadius(4);
                    innerGlow.setSpread(0.9);

                    DropShadow outerGlow = new DropShadow();
                    outerGlow.setColor(javafx.scene.paint.Color.web(glowColor, outerAlpha));
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
                    hoverLabel.setStyle(isDegraded
                            ? CardHoverPopup.LABEL_STYLE_ORANGE
                            : CardHoverPopup.LABEL_STYLE);
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
                    hoverLabel.setStyle(CardHoverPopup.LABEL_STYLE);
                }
            } catch (Exception e) {
                logger.warn("reapplyEffectsForCurrentItem failed", e);
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // Context-menu helpers
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Builds the single context menu attached to this cell.
     * Items resolve {@link #getItem()} at action time, so the same menu object
     * is safe to reuse as the cell is recycled for different elements.
     */
    private ContextMenu buildCellContextMenu() {
        ContextMenu cm = new ContextMenu();
        cm.setStyle("-fx-background-color: #100317; -fx-background-radius: 6;" +
                "-fx-border-color: #3a3a3a; -fx-border-radius: 6; -fx-border-width: 1;");

        MenuItem editItem = new MenuItem();
        Label lbl = new Label("Edit Card");
        lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
        HBox g = new HBox(lbl);
        g.setAlignment(Pos.CENTER_LEFT);
        g.setPadding(new Insets(2, 6, 2, 6));
        editItem.setGraphic(g);
        editItem.setText("");
        editItem.setOnAction(e -> {
            CardElement ce = getItem();
            if (ce != null) {
                Controller.MenuActionHandler.handleEditCard(ce, this);
            }
        });
        cm.getItems().add(editItem);

        // "Swap with..." — populated lazily when the menu opens.
        // Only visible when this card (inside a deck/collection) has a better copy
        // available in the owned collection.
        Menu swapMenu = new Menu();
        {
            Label swapLbl = new Label("Swap with...");
            swapLbl.setStyle("-fx-text-fill: #EB9E34; -fx-font-size: 13;");
            HBox swapG = new HBox(swapLbl);
            swapG.setAlignment(Pos.CENTER_LEFT);
            swapG.setPadding(new Insets(2, 6, 2, 6));
            swapMenu.setGraphic(swapG);
            swapMenu.setText("");

            MenuItem loadingPlaceholder = new MenuItem("Loading...");
            loadingPlaceholder.setDisable(true);
            swapMenu.getItems().add(loadingPlaceholder);

            swapMenu.setOnShowing(evt -> {
                swapMenu.getItems().clear();
                CardElement ce = getItem();
                if (ce == null) {
                    MenuItem none = new MenuItem("No card selected");
                    none.setDisable(true);
                    swapMenu.getItems().add(none);
                    return;
                }
                // Find the elementName from the ancestor GridView userData
                String elemName = null;
                try {
                    Node n = this;
                    while (n != null && !(n instanceof GridView)) n = n.getParent();
                    if (n instanceof GridView) {
                        Object ud = ((GridView<?>) n).getUserData();
                        if (ud instanceof Map) {
                            Object en = ((Map<?, ?>) ud).get("elementName");
                            if (en instanceof String) elemName = (String) en;
                        } else if (ud instanceof String) {
                            elemName = (String) ud;
                        }
                    }
                } catch (Exception ignored) {
                }

                if (elemName == null || elemName.trim().isEmpty()) {
                    MenuItem none = new MenuItem("Not in a deck/collection context");
                    none.setDisable(true);
                    swapMenu.getItems().add(none);
                    return;
                }

                final String finalElemName = elemName;
                List<Model.CardsLists.CardElement> candidates;
                try {
                    candidates = Controller.CardQualityService
                            .findOwnedUpgradeCandidates(ce, finalElemName);
                } catch (Exception ex) {
                    candidates = new java.util.ArrayList<>();
                }

                if (candidates.isEmpty()) {
                    MenuItem none = new MenuItem("No upgrade copies in collection");
                    none.setDisable(true);
                    swapMenu.getItems().add(none);
                } else {
                    for (Model.CardsLists.CardElement candidate : candidates) {
                        String label = buildCandidateLabel(candidate);
                        MenuItem mi = new MenuItem(label);
                        final Model.CardsLists.CardElement finalCe = ce;
                        final Model.CardsLists.CardElement finalCandidate = candidate;
                        mi.setOnAction(ev ->
                                Controller.MenuActionHandler.handleSwap(finalCandidate, finalCe));
                        swapMenu.getItems().add(mi);
                    }
                }
            });
        }
        cm.getItems().add(swapMenu);

        // Hide the swap menu by default; show it only when in Decks & Collections tab.
        cm.setOnShowing(evt -> {
            boolean inDecksTab = false;
            try {
                inDecksTab = isDecksAndCollectionsTabSelected();
            } catch (Exception ignored) {
            }
            swapMenu.setVisible(inDecksTab);
        });

        return cm;
    }

    /**
     * Builds a human-readable label for an upgrade-candidate {@link Model.CardsLists.CardElement},
     * showing its location in the collection plus condition and rarity when known.
     */
    private String buildCandidateLabel(Model.CardsLists.CardElement ce) {
        if (ce == null) return "(unknown)";
        StringBuilder sb = new StringBuilder();
        if (ce.getCondition() != null) sb.append(ce.getCondition().getDisplayName());
        if (ce.getRarity() != null) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(ce.getRarity().getDisplayName());
        }
        if (sb.length() == 0) sb.append("Copy in collection");
        return sb.toString();
    }

    /**
     * Returns {@code true} when the currently selected tab is one where the
     * middle-pane elements are editable: "My Collection" or "Decks and Collections".
     */
    private boolean isEditableTab() {
        try {
            TabPane tp = findNearestTabPane();
            if (tp == null) return false;
            Tab sel = tp.getSelectionModel().getSelectedItem();
            if (sel == null || sel.getText() == null) return false;
            String t = sel.getText().trim();
            // Strip the "* " dirty marker if present
            if (t.startsWith("* ")) t = t.substring(2).trim();
            return t.equalsIgnoreCase("My Collection")
                    || t.equalsIgnoreCase("Decks and Collections")
                    || t.equalsIgnoreCase("Decks & Collections");
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Returns {@code true} when the ancestor {@link GridView} has been tagged
     * as immutable (e.g. archetype lists).
     *
     * <p>The controller marks archetype GridViews by storing a {@code Map} in
     * {@link GridView#setUserData(Object)} with the key {@code "isImmutable"}
     * mapped to {@link Boolean#TRUE}.  Any GridView <em>without</em> that
     * marker is treated as editable.</p>
     */
    private boolean isImmutableCell() {
        try {
            Node node = this;
            while (node != null && !(node instanceof GridView)) node = node.getParent();
            if (node instanceof GridView) {
                Object ud = ((GridView<?>) node).getUserData();
                if (ud instanceof java.util.Map) {
                    Object flag = ((java.util.Map<?, ?>) ud).get("isImmutable");
                    return Boolean.TRUE.equals(flag);
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    // ════════════════════════════════════════════════════════════════════════

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

    private boolean isDecksAndCollectionsTabSelected() {
        try {
            TabPane tp = findNearestTabPane();
            if (tp != null) {
                Tab sel = tp.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    String t = sel.getText();
                    if (t == null) return false;
                    String stripped = t.trim();
                    if (stripped.startsWith("* ")) stripped = stripped.substring(2).trim();
                    return stripped.equalsIgnoreCase("Decks and Collections")
                            || stripped.equalsIgnoreCase("Decks & Collections");
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}