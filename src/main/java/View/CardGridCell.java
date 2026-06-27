package View;

import Controller.CardGroupRegistry;
import Model.CardsLists.*;
import Utils.LruImageCache;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import org.controlsfx.control.GridCell;
import org.controlsfx.control.GridView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Future;

/**
 * CardGridCell — a {@link GridCell} rendering one {@link CardElement} in the middle-pane
 * {@link GridView}. Promoted from inner class of {@link CardTreeCell} to package-private
 * top-level class. All shared state is accessed through the {@link #outer} reference.
 */
class CardGridCell extends GridCell<CardElement> {
    final CardTreeCell outer;
    private static final Logger logger = LoggerFactory.getLogger(CardGridCell.class);

    private final ImageView cardImageView;
    final StackPane wrapper;
    private Future<?> imageLoadFuture;
    private String currentImageKey;
    /**
     * Glow priority for this cell, read by the hover handler.
     * 0 = none | 1 = white (archetype/artwork missing or needs-sort)
     * 2 = orange (condition or rarity not set) | 3 = red (no printCode).
     * The glow is only applied when outer.incompleteMarkingEnabled for priorities 2 & 3.
     */
    private int currentGlowPriority = 0;
    /**
     * All applicable tooltip warnings for this cell, computed unconditionally.
     * Each entry is a two-element array: [message, cssColor].
     * Priorities 2 & 3 (My Collection completeness) are always collected even
     * when outer.incompleteMarkingEnabled is false, so the hover tooltip is always
     * informative regardless of whether the glow overlay is active.
     */
    private java.util.List<String[]> currentTooltips = new java.util.ArrayList<>();

    public CardGridCell(CardTreeCell outerCell) {
        this.outer = outerCell;
        cardImageView = new ImageView();
        cardImageView.setPreserveRatio(true);
        cardImageView.fitWidthProperty().bind(outer.cardWidthProperty);
        cardImageView.fitHeightProperty().bind(outer.cardHeightProperty);

        wrapper = new StackPane(cardImageView);
        wrapper.getProperties().put("cardWrapper", Boolean.TRUE);
        wrapper.setPadding(new Insets(5));
        wrapper.setStyle("-fx-background-color: transparent;");
        wrapper.setPickOnBounds(true);
        wrapper.setFocusTraversable(true);
        setGraphic(wrapper);

        // Context menu construction (My Collection / Decks & Collections / Archetype)
        // is delegated to CardGridCellContextMenuBuilder, the single place where all
        // three menu variants for this cell type are defined.
        ContextMenu contextMenu = CardGridCellContextMenuBuilder.buildMyCollectionContextMenu(this);
        ContextMenu decksContextMenu = CardGridCellContextMenuBuilder.buildDecksContextMenu(this);
        ContextMenu archetypeCardContextMenu = CardGridCellContextMenuBuilder.buildArchetypeCardContextMenu(this);
        // Attach the context menu to the wrapper so right-click shows it
        wrapper.setOnContextMenuRequested(e -> {
            if (outer.isMyCollectionTabSelected()) {
                // Existing My Collection menu (Sort card + Move to + Remove with trash)
                contextMenu.show(wrapper, e.getScreenX(), e.getScreenY());

            } else if (outer.isDecksAndCollectionsTabSelected()) {
                // Determine whether this card lives in an archetype group
                if (outer.isInArchetypeGroup()) {
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
            TreeView<String> parentTreeView = outer.getTreeView();
            java.util.List<CardElement> allElementsInTreeOrder =
                    (parentTreeView != null)
                            ? outer.collectAllElementsInTreeOrder(parentTreeView.getRoot())
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

            if (outer.isInArchetypeGroup()) {
                // Archetype cards are read-only — behave like a right-pane (ADD) drag.
                // Collect the FULL selection as the drag payload.
                java.util.List<Card> cards = new java.util.ArrayList<>();
                cards.add(ce.getCard());
                java.util.Set<CardElement> sel = Controller.SelectionManager.getSelectedMiddleElements();
                if ("MIDDLE".equals(Controller.SelectionManager.getActivePart())
                        && sel.size() > 1 && sel.contains(ce)) {
                    for (CardElement el : sel) {
                        if (el != ce && el.getCard() != null) cards.add(el.getCard());
                    }
                }

                // Ghost is capped at 5 images — visual only, does not affect payload.
                java.util.List<Image> ghostImages = new java.util.ArrayList<>();
                int ghostCount = Math.min(cards.size(), 5);
                for (int i = 0; i < ghostCount; i++) {
                    Card c = cards.get(i);
                    String key = c.getImagePath();
                    String rp = key != null ? outer.imagePathCache.get(key) : null;
                    ghostImages.add(rp != null ? LruImageCache.getImage(rp) : null);
                }
                javafx.scene.input.Dragboard db =
                        wrapper.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
                javafx.scene.image.WritableImage ghost =
                        Controller.DragDropManager.buildDragGhost(
                                ghostImages, outer.cardWidthProperty.get(), outer.cardHeightProperty.get());
                if (ghost != null)
                    db.setDragView(ghost, outer.cardWidthProperty.get() / 2.0, outer.cardHeightProperty.get() / 2.0);
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(ce.getCard().getPassCode());
                db.setContent(content);
                Controller.DragDropManager.startRightDrag(cards);
                event.consume();
                return;
            }

            // Collect the FULL selection as the drag payload — no cap here.
            java.util.Set<CardElement> selected =
                    Controller.SelectionManager.getSelectedMiddleElements();
            java.util.List<CardElement> dragElements = new java.util.ArrayList<>();
            dragElements.add(ce);
            if ("MIDDLE".equals(Controller.SelectionManager.getActivePart())
                    && selected.size() > 1 && selected.contains(ce)) {
                for (CardElement el : selected) {
                    if (el != ce) dragElements.add(el);
                }
            }

            // Ghost is capped at 5 images — visual only, does not affect payload.
            java.util.List<Image> ghostImages = new java.util.ArrayList<>();
            int ghostCount = Math.min(dragElements.size(), 5);
            for (int i = 0; i < ghostCount; i++) {
                CardElement el = dragElements.get(i);
                Card c = el.getCard();
                String key = c != null ? c.getImagePath() : null;
                String resolvedPath = key != null ? outer.imagePathCache.get(key) : null;
                ghostImages.add(resolvedPath != null ? LruImageCache.getImage(resolvedPath) : null);
            }

            javafx.scene.input.Dragboard db =
                    wrapper.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
            javafx.scene.image.WritableImage ghost =
                    Controller.DragDropManager.buildDragGhost(
                            ghostImages, outer.cardWidthProperty.get(), outer.cardHeightProperty.get());
            if (ghost != null) {
                db.setDragView(ghost,
                        outer.cardWidthProperty.get() / 2.0,
                        outer.cardHeightProperty.get() / 2.0);
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

        // ── Hover popup ───────────────────────────────────────────────────────────────
        // Attached to wrapper (not the outer GridCell) so it fires even when the
        // graphic intercepts all mouse events before they reach the Control.
        // Uses getItem() at event time so the text always matches the current card.
        wrapper.setOnMouseEntered(e -> {
            CardElement item = getItem();
            if (item == null) return;
            outer.hoverLabel.setText(
                    CardHoverPopup.buildTooltipText(item));

            // Switch popup border to orange when this card has an orange-level warning:
            // upgrade candidate, degraded D&C card, or substandard-quality OuicheList slot.
            boolean isOrangePopup = !currentTooltips.isEmpty()
                    && currentTooltips.stream().anyMatch(t ->
                    t[0] != null && (t[0].equals(CardHoverPopup.UPGRADE_CANDIDATE_WARNING)
                            || t[0].equals(CardHoverPopup.DOWNGRADE_WARNING)
                            || t[0].equals(CardHoverPopup.SUBSTANDARD_QUALITY_WARNING)));
            outer.hoverPopupBox.setStyle(
                    isOrangePopup
                            ? CardHoverPopup.POPUP_BOX_STYLE_ORANGE
                            : CardHoverPopup.POPUP_BOX_STYLE_DEFAULT);

            // Rebuild the warnings box from currentTooltips (computed in updateItem).
            // Always shown when applicable, regardless of whether the card is glowing.
            outer.warningsBox.getChildren().clear();
            for (String[] entry : currentTooltips) {
                Label wl = new Label(entry[0]);
                wl.setStyle("-fx-text-fill: " + entry[1] + "; " +
                        "-fx-font-size: 12; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 2 10 2 10;");
                wl.setWrapText(true);
                wl.setMaxWidth(260);
                outer.warningsBox.getChildren().add(wl);
            }
            boolean hasWarnings = !currentTooltips.isEmpty();
            outer.warningsBox.setVisible(hasWarnings);
            outer.warningsBox.setManaged(hasWarnings);

            outer.hoverPopup.show(wrapper, e.getScreenX() + 14, e.getScreenY() + 14);
        });
        wrapper.setOnMouseMoved(e -> {
            if (outer.hoverPopup.isShowing())
                outer.hoverPopup.show(wrapper, e.getScreenX() + 14, e.getScreenY() + 14);
        });
        wrapper.setOnMouseExited(e -> outer.hoverPopup.hide());

        // ── Drop target: card-level (left half = before, right half = after) ────────
        wrapper.setOnDragOver(event -> {
            if (!outer.isInArchetypeGroup()
                    && event.getDragboard().hasString()
                    && Controller.DragDropManager.getDragSourcePane() != null) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            }
            event.consume();
        });

        wrapper.setOnDragDropped(event -> {
            // Always reset the flag at the start of each drop so it is never
            // stale from a previous drag session (e.g. after a right-pane drag
            // where wrapper.setOnDragDone never fires).
            CardGroupRegistry.dropHandledByCell = false;

            if (outer.isInArchetypeGroup()) {
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

            CardsGroup group = outer.findGroupForCardElement(anchor);
            if (group == null) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            javafx.collections.ObservableList<CardElement> list = outer.observableListFor(group);
            int anchorIdx = list.indexOf(anchor);
            if (anchorIdx < 0) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            // Determine left/right half using mouse X relative to the wrapper
            double localX = event.getX();
            boolean insertAfter = outer.isRightHalf(localX, outer.cardWidthProperty.get());
            int insertionIndex = insertAfter ? anchorIdx + 1 : anchorIdx;

            String srcPane = Controller.DragDropManager.getDragSourcePane();
            boolean isMiddle = "MIDDLE".equals(srcPane);

            // Capture move context so OuicheList notifications can be fired after
            // D&C rebuilds are queued (ensuring correct runLater ordering).
            java.util.Set<CardsGroup> moveSourceGroups = null;
            java.util.List<CardElement> movedElements = null;

            if (isMiddle) {
                java.util.List<CardElement> srcElements =
                        new java.util.ArrayList<>(Controller.DragDropManager.getDraggedElements());
                // Don't move onto itself
                if (srcElements.size() == 1 && srcElements.get(0) == anchor) {
                    event.setDropCompleted(false);
                    event.consume();
                    return;
                }
                moveSourceGroups =
                        outer.dropInsertIntoGroup(group, insertionIndex, srcElements, null);
                movedElements = srcElements;
                for (CardsGroup sg : moveSourceGroups) outer.markDirtyAndRefreshForGroup(sg);
            } else {
                java.util.List<Model.CardsLists.Card> srcCards =
                        new java.util.ArrayList<>(Controller.DragDropManager.getDraggedCards());
                outer.dropInsertIntoGroup(group, insertionIndex, null, srcCards);
                // My Collection only: open edit popup for cards dropped without a printCode.
                if (outer.isMyCollectionTabSelected()) {
                    outer.openEditPopupsForNoPrintCode(srcCards, group, this);
                }
            }
            outer.markDirtyAndRefreshForGroup(group);

            // OuicheList MOVE notifications: fired AFTER markDirtyAndRefreshForGroup so
            // the D&C rebuild is already queued before the OuicheList rebuild is queued.
            // This guarantees the OuicheList refresh runs in a later pulse and never
            // interferes with the in-progress D&C scene graph update.
            // A move across groups = removal from source + addition to target.
            // Same-group reorders are pure position changes — OuicheList is unaffected.
            if (moveSourceGroups != null && movedElements != null) {
                final java.util.List<CardElement> capturedMoved = movedElements;
                for (CardsGroup sg : moveSourceGroups) {
                    if (sg != group) {
                        CardGroupRegistry.notifyOuicheListOfGroupRemovals(sg, capturedMoved);
                    }
                }
                if (moveSourceGroups.stream().anyMatch(sg -> sg != group)) {
                    CardGroupRegistry.notifyOuicheListOfGroupAdditions(group, capturedMoved);
                }
            }
            // Signal to grid.setOnDragDropped that this drop is already handled.
            CardGroupRegistry.dropHandledByCell = true;
            event.setDropCompleted(true);
            event.consume();
        });
    } // end CardGridCell()

    // Helper: remove the given CardElement from the owned collection and refresh UI.
    // Tries MenuActionHandler.handleRemove(...) first, falls back to direct removal.
    void removeCardElement(Model.CardsLists.CardElement ce) {
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
    List<CardElement> getEffectiveMiddleElements() {
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

    boolean isMiddleMultiSelectActive() {
        CardElement currentItem = getItem();
        return currentItem != null
                && "MIDDLE".equals(Controller.SelectionManager.getActivePart())
                && Controller.SelectionManager.getSelectedMiddleElements().size() > 1
                && Controller.SelectionManager.getSelectedMiddleElements().contains(currentItem);
    }

    void executeCopyAction() {
        CardElement currentItem = getItem();
        if (currentItem == null || currentItem.getCard() == null) {
            return;
        }
        if (isMiddleMultiSelectActive()) {
            // Multi-selection in the middle pane: copy full CardElement snapshots
            // so condition, rarity, and custom tags are preserved.
            java.util.List<CardElement> elementsToCopy = new java.util.ArrayList<>();
            for (CardElement element : Controller.SelectionManager.getSelectedMiddleElements()) {
                if (element.getCard() != null) {
                    elementsToCopy.add(element);
                }
            }
            Controller.CardClipboard.copyElements(elementsToCopy);
        } else if ("RIGHT".equals(Controller.SelectionManager.getActivePart())
                && Controller.SelectionManager.getSelectedCards().size() > 1
                && Controller.SelectionManager.getSelectedCards().contains(
                currentItem.getCard())) {
            // Right pane: only bare Card objects available; no per-copy metadata.
            Controller.CardClipboard.copyCards(
                    new java.util.ArrayList<>(
                            Controller.SelectionManager.getSelectedCards()));
        } else {
            // Single card: preserve full element if in the middle pane (My Collection,
            // D&C non-archetype); use bare Card for right pane and archetype lists.
            if ("MIDDLE".equals(Controller.SelectionManager.getActivePart())
                    && !outer.isInArchetypeGroup()) {
                Controller.CardClipboard.copyElements(
                        Collections.singletonList(currentItem));
            } else {
                Controller.CardClipboard.copyCards(
                        Collections.singletonList(currentItem.getCard()));
            }
        }
    }

    void executeCutFromOwnedCollectionAction() {
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

    void executeCutFromDecksAction() {
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
            currentGlowPriority = 0;
            currentTooltips = new java.util.ArrayList<>();
            setGraphic(wrapper);
            return;
        }

        // --- Image loading (unchanged logic) ---
        String imageKey = outer.safeImageKey(cardElement);
        String cachedFullPath = imageKey == null ? null : outer.imagePathCache.get(imageKey);
        if (cachedFullPath != null) {
            Image cached = LruImageCache.getImage(cachedFullPath);
            if (cached != null) {
                cardImageView.setImage(cached);
            } else {
                cardImageView.setImage(outer.getPlaceholderImage());
                Future<?> f = outer.loadImageWithResolvedPathAsync(cardElement, cardImageView, cachedFullPath);
                if (f != null) outer.outstandingLoads.put(cardImageView, f);
            }
        } else {
            cardImageView.setImage(outer.getPlaceholderImage());
            outer.resolveImagePathAsync(imageKey, resolvedPath -> {
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
                    Future<?> f = outer.loadImageWithResolvedPathAsync(cardElement, cardImageView, resolvedPath);
                    if (f != null) outer.outstandingLoads.put(cardImageView, f);
                }
            });
        }

        // ── Tooltip list: always computed, regardless of marking toggle ───────────────
        // Each entry: [message, cssColor]
        java.util.List<String[]> tooltips = new java.util.ArrayList<>();
        int glowPriority = 0;
        try {
            Card card = cardElement.getCard();
            boolean myCollTab = outer.isMyCollectionTabSelected();
            boolean ouicheTab = outer.isOuicheListTabSelected();

            // ── OuicheList: substandard-quality red glow (priority 4, highest) ─────────
            // A slot marked OWNED_SUBSTANDARD means the user owns a copy of this card
            // but it does not meet the condition or rarity requirement of this slot.
            // Priority 4 is above all My Collection priorities (max 3) and is only
            // active on the OuicheList tab, so the two sets of glows never conflict.
            if (ouicheTab
                    && cardElement.getOwnershipStatus() == OwnershipStatus.OWNED_SUBSTANDARD) {
                glowPriority = 4;
                tooltips.add(new String[]{
                        CardHoverPopup.SUBSTANDARD_QUALITY_WARNING, "#EB9E34"});
            }

            // ── My Collection completeness warnings (always shown in tooltip) ─────────
            // Glow is only applied when outer.incompleteMarkingEnabled; tooltip always shows.
            if (myCollTab && card != null
                    && (card.getPrintCode() == null || card.getPrintCode().isBlank())) {
                tooltips.add(new String[]{
                        "This card has no print code — the edition is unknown.", "#ff3333"});
                if (outer.incompleteMarkingEnabled) glowPriority = 3;
            }
            if (myCollTab
                    && (cardElement.getCondition() == null || cardElement.getRarity() == null)) {
                tooltips.add(new String[]{
                        "This card is missing its condition or rarity.", "#EB9E34"});
                if (outer.incompleteMarkingEnabled && glowPriority < 2) glowPriority = 2;
            }

            // ── White-glow conditions (archetype / artwork missing, needs-sort) ─────────
            // Always evaluated so their tooltip entries are always collected.
            // glowPriority is only elevated to 1 when it is still 0 (higher-priority
            // My Collection glows have already won and keep their effect).
            {
                Object ud = null;
                try {
                    if (this.getGridView() != null) ud = this.getGridView().getUserData();
                } catch (Exception ignored) {
                }

                Set<String> missingSet = null;
                Set<String> missingArtworkSet = null;
                String elementNameFromUD = null;

                if (ud instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) ud;

                    Object ms = map.get("missingSet");
                    if (ms instanceof Set) {
                        @SuppressWarnings("unchecked") Set<String> s = (Set<String>) ms;
                        missingSet = s;
                    } else if (ms instanceof Collection) {
                        Set<String> s = new HashSet<>();
                        for (Object o : (Collection<?>) ms) if (o != null) s.add(o.toString());
                        missingSet = s;
                    }

                    Object mas = map.get("missingArtworkSet");
                    if (mas instanceof Set) {
                        @SuppressWarnings("unchecked") Set<String> s = (Set<String>) mas;
                        missingArtworkSet = s;
                    }

                    Object en = map.get("elementName");
                    if (en instanceof String) elementNameFromUD = (String) en;

                } else if (ud instanceof Set) {
                    @SuppressWarnings("unchecked") Set<String> s = (Set<String>) ud;
                    missingSet = s;
                } else if (ud instanceof String) {
                    elementNameFromUD = (String) ud;
                }

                String konamiId = card == null ? null : card.getKonamiId();
                String passCode = card == null ? null : card.getPassCode();

                // 1a — archetype / explicit missing set
                if (missingSet != null && !missingSet.isEmpty()) {
                    boolean missing = (konamiId != null && missingSet.contains(konamiId))
                            || (passCode != null && missingSet.contains(passCode))
                            || CardGroupRegistry.LEGACY_GLOBAL_MISSING_SET.contains(konamiId)
                            || CardGroupRegistry.LEGACY_GLOBAL_MISSING_SET.contains(passCode);
                    if (missing) {
                        if (outer.isInArchetypeGroup()) {
                            // Archetype missing-card marking: active on any tab.
                            if (glowPriority == 0) glowPriority = 1;
                            tooltips.add(new String[]{
                                    "This card is missing in the collection.",
                                    "#EB9E34"});
                        } else if (myCollTab) {
                            // Non-archetype needs-sorting: My Collection tab only.
                            if (glowPriority == 0) glowPriority = 1;
                            tooltips.add(new String[]{
                                    CardHoverPopup.NEEDS_SORTING_WARNING,
                                    "#EB9E34"});
                        }
                    }
                }

                // 1b — missing artwork set (check both konamiId and passCode)
                if (missingArtworkSet != null && !missingArtworkSet.isEmpty()) {
                    boolean artMissing = (konamiId != null && missingArtworkSet.contains(konamiId))
                            || (passCode != null && missingArtworkSet.contains(passCode));
                    if (artMissing) {
                        if (glowPriority == 0) glowPriority = 1;
                        tooltips.add(new String[]{
                                "Not all artworks of this card are present in this collection.",
                                "#EB9E34"});
                    }
                }

                // 1c — sorting check: only on My Collection tab.
                //      Cards in D&C get DOWNGRADE_WARNING via 1d below, not here.
                if (elementNameFromUD != null
                        && !elementNameFromUD.trim().isEmpty() && outer.isMyCollectionTabSelected()) {
                    try {
                        boolean genuinelyNeeded = false;
                        try {
                            genuinelyNeeded = Controller.CardQualityService
                                    .computeCardNeedsSorting(card, elementNameFromUD);
                        } catch (Throwable ignored) {
                        }

                        boolean upgradeNeeded = false;
                        if (!genuinelyNeeded) {
                            try {
                                upgradeNeeded = Controller.CardQualityService
                                        .computeCardNeedsSortingWithUpgrade(cardElement, elementNameFromUD);
                            } catch (Throwable ignored) {
                            }
                        }

                        if (genuinelyNeeded) {
                            if (glowPriority == 0) glowPriority = 1;
                            tooltips.add(new String[]{
                                    CardHoverPopup.NEEDS_SORTING_WARNING,
                                    "#EB9E34"});
                        } else if (upgradeNeeded) {
                            if (glowPriority == 0) glowPriority = 1;
                            tooltips.add(new String[]{
                                    CardHoverPopup.UPGRADE_CANDIDATE_WARNING,
                                    "#EB9E34"});
                        } else {
                            // Reason 4: this card IS in a D&C-named sorting category and
                            // a better outside copy exists in the owned collection.
                            // Guard: only fire when the element name is a recognised D&C
                            // name, to avoid false positives on type groups such as
                            // "Fusion Monsters".  computeCardNeedsSortingWithUpgrade already
                            // returns false for D&C-named groups (its internal guard), so
                            // upgradeNeeded is false here in both cases — we use
                            // isDeckOrCollectionName to distinguish them.
                            try {
                                boolean isDeckName = Controller.CardQualityService
                                        .isDeckOrCollectionName(elementNameFromUD);
                                if (isDeckName) {
                                    boolean isDegraded = Controller.CardQualityService
                                            .isDegradedCopyInDeckOrCollection(
                                                    cardElement, elementNameFromUD);
                                    if (isDegraded) {
                                        if (glowPriority == 0) {
                                            glowPriority = 1;
                                        }
                                        tooltips.add(new String[]{
                                                CardHoverPopup.DOWNGRADE_WARNING,
                                                "#EB9E34"});
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }

            }
        } catch (Exception e) {
            logger.debug("Failed to compute glow in CardGridCell.updateItem", e);
        }

        // ── Apply glow effect to the wrapper StackPane ────────────────────────────────
        if (glowPriority >= 4) {
            // Red — OWNED_SUBSTANDARD in OuicheList tab
            DropShadow inner = new DropShadow();
            inner.setColor(javafx.scene.paint.Color.web("#FF3333", 1.0));
            inner.setOffsetX(0);
            inner.setOffsetY(0);
            inner.setRadius(4);
            inner.setSpread(0.9);
            DropShadow outer = new DropShadow();
            outer.setColor(javafx.scene.paint.Color.web("#FF3333", 0.40));
            outer.setOffsetX(0);
            outer.setOffsetY(0);
            outer.setRadius(14);
            outer.setSpread(0.12);
            outer.setInput(inner);
            wrapper.setEffect(outer);
        } else if (glowPriority >= 3) {
            // Red — no printCode
            DropShadow inner = new DropShadow();
            inner.setColor(javafx.scene.paint.Color.web("#ff3333", 1.0));
            inner.setOffsetX(0);
            inner.setOffsetY(0);
            inner.setRadius(4);
            inner.setSpread(0.9);
            DropShadow outer = new DropShadow();
            outer.setColor(javafx.scene.paint.Color.web("#ff3333", 0.35));
            outer.setOffsetX(0);
            outer.setOffsetY(0);
            outer.setRadius(14);
            outer.setSpread(0.12);
            outer.setInput(inner);
            wrapper.setEffect(outer);
        } else if (glowPriority >= 1) {
            // White — covers priority 1 (archetype/artwork missing, needs-sort)
            //         AND priority 2 (condition/rarity missing, marking enabled).
            DropShadow inner = new DropShadow();
            inner.setColor(javafx.scene.paint.Color.web("#ffffff", 1.0));
            inner.setOffsetX(0);
            inner.setOffsetY(0);
            inner.setRadius(4);
            inner.setSpread(0.9);
            DropShadow outer = new DropShadow();
            outer.setColor(javafx.scene.paint.Color.web("#ffffff", 0.22));
            outer.setOffsetX(0);
            outer.setOffsetY(0);
            outer.setRadius(14);
            outer.setSpread(0.12);
            outer.setInput(inner);
            wrapper.setEffect(outer);
        } else {
            wrapper.setEffect(null);
        }

        // --- Apply grayscale for owned cards in OuicheList tab (on the imageView only) ---
        // This is intentionally applied to cardImageView, not wrapper, so it is independent
        // of the glow effect above and does not affect other tabs.
        boolean ouicheSelected = outer.isOuicheListTabSelected();
        boolean owned = cardElement.getOwnershipStatus() != null && cardElement.getOwnershipStatus() == OwnershipStatus.OWNED;
        if (ouicheSelected && owned) {
            // When "Hide owned cards" is active, suppress the cell entirely instead of
            // showing it in gray. The flag is baked into the GridView userData at
            // tree-build time by CardTreeCell.createCardsGroupCell.
            boolean hideOwned = false;
            try {
                Object userData = getGridView() != null ? getGridView().getUserData() : null;
                if (userData instanceof Map) {
                    Object flag = ((Map<?, ?>) userData).get("hideOwnedCards");
                    if (flag instanceof Boolean) {
                        hideOwned = (Boolean) flag;
                    }
                }
            } catch (Exception ignored) {
            }
            if (hideOwned) {
                cardImageView.setImage(null);
                cardImageView.setEffect(null);
                wrapper.setEffect(null);
                wrapper.setStyle("-fx-background-color: transparent;");
                setGraphic(null);
                return;
            }
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

        // Store for the hover handler
        currentGlowPriority = glowPriority;
        currentTooltips = tooltips;

        // ── Condition / rarity corner badges ─────────────────────────────────────────
        // Rebuild the badge nodes on every updateItem call so they always match
        // the current CardElement and the current toggle state.
        // Badges are shown in: My Collection, Decks & Collections, OuicheList mosaic.
        // They are NOT shown on the Archetypes tab (read-only, no ownership metadata).
        wrapper.getChildren().removeIf(node -> Boolean.TRUE.equals(
                node.getProperties().get("conditionRarityBadge")));

        boolean isOverlayTab = outer.isMyCollectionTabSelected()
                || outer.isDecksAndCollectionsTabSelected()
                || outer.isOuicheListTabSelected();
        boolean shouldShowBadges = isOverlayTab
                && CardTreeCell.isShowConditionRarityOverlayEnabled()
                && !outer.isInArchetypeGroup();

        if (shouldShowBadges) {
            CardCondition condition = cardElement.getCondition();
            CardRarity rarity = cardElement.getRarity();

            if (condition != null) {
                Label conditionLabel = buildCornerBadge(condition.getCode());
                StackPane.setAlignment(conditionLabel, javafx.geometry.Pos.TOP_LEFT);
                conditionLabel.getProperties().put("conditionRarityBadge", Boolean.TRUE);
                wrapper.getChildren().add(conditionLabel);
            }

            if (rarity != null) {
                Label rarityLabel = buildCornerBadge(rarity.getCode());
                StackPane.setAlignment(rarityLabel, javafx.geometry.Pos.TOP_RIGHT);
                rarityLabel.getProperties().put("conditionRarityBadge", Boolean.TRUE);
                wrapper.getChildren().add(rarityLabel);
            }
        }

        // Finalize graphic
        setGraphic(wrapper);
    }

    /**
     * Creates a small badge label with the app's dark-purple background and white
     * text, used for the condition and rarity corner indicators.
     *
     * @param text the short code to display (e.g. {@code "NM"}, {@code "StR"})
     * @return a styled {@link Label} ready to be added to the card wrapper
     */
    private Label buildCornerBadge(String text) {
        Label label = new Label(text);
        label.setStyle(
                "-fx-background-color: #0e0140; "
                        + "-fx-text-fill: white; "
                        + "-fx-font-size: 9px; "
                        + "-fx-font-weight: bold; "
                        + "-fx-padding: 1 3 1 3; "
                        + "-fx-background-radius: 2;");
        label.setMouseTransparent(true);
        return label;
    }

    /**
     * Returns the Deck or ThemeCollection in the DecksAndCollectionsList
     * that owns the given CardsGroup (matched by backing-list identity).
     * Returns null when the group belongs to MyCollection or cannot be found.
     */
    Object findDacOwnerForCardsGroup(CardsGroup group) {
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

    String findCurrentLocationPath() {
        try {
            TreeItem<String> item = outer.getTreeItem();
            if (item == null) return null;
            String groupName = item.getValue();
            TreeItem<String> parent = item.getParent();
            if (parent == null) return groupName;
            Object parentData = (parent instanceof DataTreeItem)
                    ? ((DataTreeItem<?>) parent).getData() : null;

            // Direct child of a ThemeCollection (e.g. "Cards" or "Cards not to add")
            if (parentData instanceof ThemeCollection) {
                String collName = outer.sanitizeDisplayName(((ThemeCollection) parentData).getName());
                if ("Cards not to add".equals(groupName)) return collName + " / Exclusion List";
                return collName;
            }

            // Child of a Deck (Main/Extra/Side Deck group)
            if (parentData instanceof Deck) {
                String deckName = outer.sanitizeDisplayName(((Deck) parentData).getName());
                TreeItem<String> sectionItem = parent.getParent(); // DECKS_SECTION
                if (sectionItem != null) {
                    TreeItem<String> collOrRoot = sectionItem.getParent();
                    if (collOrRoot != null) {
                        Object collData = (collOrRoot instanceof DataTreeItem)
                                ? ((DataTreeItem<?>) collOrRoot).getData() : null;
                        if (collData instanceof ThemeCollection) {
                            String collName = outer.sanitizeDisplayName(((ThemeCollection) collData).getName());
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

    void removeCardElementFromDecksList(Model.CardsLists.CardElement cardElement) {
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
                            : CardGroupRegistry.GROUP_OBSERVABLE_LISTS.entrySet()) {
                        if (entry.getValue() == items) {
                            owner = findDacOwnerForCardsGroup(entry.getKey());
                            break;
                        }
                    }
                    if (owner != null) {
                        Controller.UserInterfaceFunctions.markDirty(owner);
                        if (owner instanceof Model.CardsLists.ThemeCollection) {
                            Controller.UserInterfaceFunctions.setPendingDecksFullRebuild();
                        }
                    } else {
                        Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                    }
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
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
     * Resolves a display-path (built by the destination-menu-item helpers in
     * {@link CardGridCellContextMenuBuilder}) to the actual backing
     * List<CardElement> inside the DecksAndCollectionsList.
     * <p>
     * Path patterns (names are already sanitized — no = or -):
     * "CollName"                              → tc.getCardsList()
     * "CollName / Exclusion List"             → tc.getExceptionsToNotAdd()
     * "DeckName / Main|Extra|Side Deck"       → standalone deck list
     * "CollName / DeckName / Main|Extra|Side" → linked deck list
     */
    public List<Model.CardsLists.CardElement> resolveDecksTargetList(String path) {
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
            if (outer.sanitizeDisplayName(tc.getName()).equals(displayName)) return tc;
        }
        return null;
    }

    private Model.CardsLists.Deck findStandaloneDeckByDisplayName(
            String displayName, Model.CardsLists.DecksAndCollectionsList dac) {
        if (dac == null || dac.getDecks() == null) return null;
        for (Model.CardsLists.Deck d : dac.getDecks()) {
            if (d == null) continue;
            if (outer.sanitizeDisplayName(d.getName()).equals(displayName)) return d;
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
                if (outer.sanitizeDisplayName(d.getName()).equals(displayName)) return d;
            }
        }
        return null;
    }

    /**
     * Returns true when the current selection is compatible with the
     * named section for a MOVE operation. The Side Deck always returns true.
     */
    boolean moveSectionAllowed(String sectionName) {
        if (Utils.DeckCompatibility.isSideDeckSection(sectionName)) return true;
        // Use only getItem() — the CardElement in the cell being right-clicked.
        // SelectionManager holds stale state from previous interactions and must
        // not be used here; it would show incompatible sections for other cards.
        Model.CardsLists.CardElement item = getItem();
        if (item == null || item.getCard() == null) return true;
        return Utils.DeckCompatibility.isCompatibleWith(item.getCard(), sectionName);
    }

    /**
     * Returns true when the current selection is compatible with the
     * named section for an ADD operation. The Side Deck always returns true.
     */
    boolean addSectionAllowed(String sectionName) {
        return moveSectionAllowed(sectionName); // same logic
    }

    /**
     * Returns true when the card list that {@code path} resolves to is currently
     * empty (meaning the corresponding DataTreeItem was never inserted in the tree
     * and a structural refresh will be needed after the add).
     */
    boolean isDecksTargetEmpty(String[] parts, boolean isExclusion) {
        try {
            Model.CardsLists.DecksAndCollectionsList dac =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (dac == null) return false;

            String collName = parts[0].trim();

            // ── Collection-level targets ─────────────────────────────────────
            if (dac.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                    if (tc == null) continue;
                    String tcName = tc.getName() == null ? "" : tc.getName().trim();
                    if (!collName.equals(tcName)) continue;

                    if (isExclusion) {
                        java.util.List<Model.CardsLists.CardElement> exc =
                                tc.getExceptionsToNotAdd();
                        return exc == null || exc.isEmpty();
                    }
                    if (parts.length == 1) {
                        java.util.List<Model.CardsLists.CardElement> cl = tc.getCardsList();
                        return cl == null || cl.isEmpty();
                    }
                    // "CollName / DeckName / Main|Extra|Side Deck"
                    if (parts.length >= 3 && tc.getLinkedDecks() != null) {
                        String deckName = parts[1].trim();
                        String section = parts[parts.length - 1].trim().toLowerCase(
                                java.util.Locale.ROOT);
                        for (java.util.List<Model.CardsLists.Deck> unit :
                                tc.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Model.CardsLists.Deck d : unit) {
                                if (d == null) continue;
                                String dName = d.getName() == null ? "" : d.getName().trim();
                                if (!deckName.equals(dName)) continue;
                                java.util.List<Model.CardsLists.CardElement> lst =
                                        section.contains("extra") ? d.getExtraDeck()
                                                : section.contains("side") ? d.getSideDeck()
                                                : d.getMainDeck();
                                return lst == null || lst.isEmpty();
                            }
                        }
                    }
                }
            }

            // ── Standalone deck targets ("DeckName / Main|Extra|Side Deck") ──
            if (parts.length >= 2 && dac.getDecks() != null) {
                String section = parts[parts.length - 1].trim().toLowerCase(
                        java.util.Locale.ROOT);
                for (Model.CardsLists.Deck d : dac.getDecks()) {
                    if (d == null) continue;
                    String dName = d.getName() == null ? "" : d.getName().trim();
                    if (!collName.equals(dName)) continue;
                    java.util.List<Model.CardsLists.CardElement> lst =
                            section.contains("extra") ? d.getExtraDeck()
                                    : section.contains("side") ? d.getSideDeck()
                                    : d.getMainDeck();
                    return lst == null || lst.isEmpty();
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Returns the Deck or ThemeCollection that owns the list at {@code path},
     * or null if the path cannot be resolved.
     */
    Object resolveDecksTargetOwner(String path) {
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