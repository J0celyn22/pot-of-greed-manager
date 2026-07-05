package View;

import Controller.CardGroupRegistry;
import Model.CardsLists.*;
import Utils.CardNameUtils;
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

        setupContextMenuHandler();
        setupSelectionClickHandler();
        setupDragSource();
        setupHoverPopup();
        setupDropTarget();
    } // end CardGridCell()

    /**
     * Builds the three context-menu variants for this cell (My Collection, Decks &amp;
     * Collections, Archetype) and wires the right-click handler that shows the
     * correct one for the currently active tab.
     */
    private void setupContextMenuHandler() {
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
    }

    /**
     * Wires MIDDLE-pane click selection: plain click selects, Ctrl+click toggles,
     * Shift+click range-selects across the whole tree in display order.
     */
    private void setupSelectionClickHandler() {
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
    }

    /**
     * Wires MIDDLE-pane drag initiation. Archetype cards (read-only) are dragged with
     * ADD semantics identical to a right-pane drag; all other cards are dragged with
     * MOVE semantics. In both cases the full current selection travels as the payload,
     * while the visual drag ghost is capped at 5 card images.
     */
    private void setupDragSource() {
        wrapper.setOnDragDetected(event -> {
            CardElement draggedCardElement = getItem();
            if (draggedCardElement == null || draggedCardElement.getCard() == null) {
                return;
            }

            if (outer.isInArchetypeGroup()) {
                // Archetype cards are read-only — behave like a right-pane (ADD) drag.
                // Collect the FULL selection as the drag payload.
                java.util.List<Card> cards = new java.util.ArrayList<>();
                cards.add(draggedCardElement.getCard());
                java.util.Set<CardElement> selectedElements =
                        Controller.SelectionManager.getSelectedMiddleElements();
                if ("MIDDLE".equals(Controller.SelectionManager.getActivePart())
                        && selectedElements.size() > 1 && selectedElements.contains(draggedCardElement)) {
                    for (CardElement element : selectedElements) {
                        if (element != draggedCardElement && element.getCard() != null) {
                            cards.add(element.getCard());
                        }
                    }
                }

                // Ghost is capped at 5 images — visual only, does not affect payload.
                java.util.List<Image> ghostImages = new java.util.ArrayList<>();
                int ghostCount = Math.min(cards.size(), 5);
                for (int i = 0; i < ghostCount; i++) {
                    Card ghostCard = cards.get(i);
                    String key = ghostCard.getImagePath();
                    String resolvedPath = key != null ? CardImageLoader.imagePathCache.get(key) : null;
                    ghostImages.add(resolvedPath != null ? LruImageCache.getImage(resolvedPath) : null);
                }
                javafx.scene.input.Dragboard dragboard =
                        wrapper.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
                javafx.scene.image.WritableImage ghost =
                        Controller.DragDropManager.buildDragGhost(
                                ghostImages, outer.cardWidthProperty.get(), outer.cardHeightProperty.get());
                if (ghost != null) {
                    dragboard.setDragView(ghost,
                            outer.cardWidthProperty.get() / 2.0, outer.cardHeightProperty.get() / 2.0);
                }
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(draggedCardElement.getCard().getPassCode());
                dragboard.setContent(content);
                Controller.DragDropManager.startRightDrag(cards);
                event.consume();
                return;
            }

            // Collect the FULL selection as the drag payload — no cap here.
            java.util.Set<CardElement> selected =
                    Controller.SelectionManager.getSelectedMiddleElements();
            java.util.List<CardElement> dragElements = new java.util.ArrayList<>();
            dragElements.add(draggedCardElement);
            if ("MIDDLE".equals(Controller.SelectionManager.getActivePart())
                    && selected.size() > 1 && selected.contains(draggedCardElement)) {
                for (CardElement element : selected) {
                    if (element != draggedCardElement) {
                        dragElements.add(element);
                    }
                }
            }

            // Ghost is capped at 5 images — visual only, does not affect payload.
            java.util.List<Image> ghostImages = new java.util.ArrayList<>();
            int ghostCount = Math.min(dragElements.size(), 5);
            for (int i = 0; i < ghostCount; i++) {
                CardElement dragElement = dragElements.get(i);
                Card card = dragElement.getCard();
                String key = card != null ? card.getImagePath() : null;
                String resolvedPath = key != null ? CardImageLoader.imagePathCache.get(key) : null;
                ghostImages.add(resolvedPath != null ? LruImageCache.getImage(resolvedPath) : null);
            }

            javafx.scene.input.Dragboard dragboard =
                    wrapper.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
            javafx.scene.image.WritableImage ghost =
                    Controller.DragDropManager.buildDragGhost(
                            ghostImages, outer.cardWidthProperty.get(), outer.cardHeightProperty.get());
            if (ghost != null) {
                dragboard.setDragView(ghost,
                        outer.cardWidthProperty.get() / 2.0,
                        outer.cardHeightProperty.get() / 2.0);
            }

            javafx.scene.input.ClipboardContent content =
                    new javafx.scene.input.ClipboardContent();
            content.putString(draggedCardElement.getCard().getPassCode());
            dragboard.setContent(content);
            Controller.DragDropManager.startMiddleDrag(dragElements);
            event.consume();
        });

        wrapper.setOnDragDone(event -> {
            Controller.DragDropManager.clearCurrentlyDraggedCard();
            event.consume();
        });
    }

    /**
     * Wires the hover tooltip popup: shows card details plus any accumulated
     * warnings (computed in {@link #updateItem}) when the mouse enters this cell,
     * follows the mouse while hovering, and hides on exit. Attached to
     * {@code wrapper} rather than the outer {@code Control} so it still fires when
     * the graphic intercepts mouse events first.
     */
    private void setupHoverPopup() {
        wrapper.setOnMouseEntered(event -> {
            CardElement item = getItem();
            if (item == null) {
                return;
            }
            outer.hoverLabel.setText(
                    CardHoverPopup.buildTooltipText(item));

            // Switch popup border to orange when this card has an orange-level warning:
            // upgrade candidate, degraded D&C card, or substandard-quality OuicheList slot.
            boolean isOrangePopup = !currentTooltips.isEmpty()
                    && currentTooltips.stream().anyMatch(tooltip ->
                    tooltip[0] != null && (tooltip[0].equals(CardHoverPopup.UPGRADE_CANDIDATE_WARNING)
                            || tooltip[0].equals(CardHoverPopup.DOWNGRADE_WARNING)
                            || tooltip[0].equals(CardHoverPopup.SUBSTANDARD_QUALITY_WARNING)));
            outer.hoverPopupBox.setStyle(
                    isOrangePopup
                            ? CardHoverPopup.POPUP_BOX_STYLE_ORANGE
                            : CardHoverPopup.POPUP_BOX_STYLE_DEFAULT);

            // Rebuild the warnings box from currentTooltips (computed in updateItem).
            // Always shown when applicable, regardless of whether the card is glowing.
            outer.warningsBox.getChildren().clear();
            for (String[] entry : currentTooltips) {
                Label warningLabel = new Label(entry[0]);
                warningLabel.setStyle("-fx-text-fill: " + entry[1] + "; " +
                        "-fx-font-size: 12; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 2 10 2 10;");
                warningLabel.setWrapText(true);
                warningLabel.setMaxWidth(260);
                outer.warningsBox.getChildren().add(warningLabel);
            }
            boolean hasWarnings = !currentTooltips.isEmpty();
            outer.warningsBox.setVisible(hasWarnings);
            outer.warningsBox.setManaged(hasWarnings);

            outer.hoverPopup.show(wrapper, event.getScreenX() + 14, event.getScreenY() + 14);
        });
        wrapper.setOnMouseMoved(event -> {
            if (outer.hoverPopup.isShowing()) {
                outer.hoverPopup.show(wrapper, event.getScreenX() + 14, event.getScreenY() + 14);
            }
        });
        wrapper.setOnMouseExited(event -> outer.hoverPopup.hide());
    }

    /**
     * Wires the card-level drop target for MOVE operations: the left half of the
     * cell inserts before this card, the right half inserts after. Accepts both
     * MIDDLE-pane reorders/moves and RIGHT-pane (add) drags; archetype groups are
     * read-only and reject all drops.
     */
    private void setupDropTarget() {
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

            CardsGroup group = CardGroupRegistry.findGroupForCardElement(anchor);
            if (group == null) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            javafx.collections.ObservableList<CardElement> list = CardGroupRegistry.observableListFor(group);
            int anchorIdx = list.indexOf(anchor);
            if (anchorIdx < 0) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            // Determine left/right half using mouse X relative to the wrapper
            double localX = event.getX();
            boolean insertAfter = GridViewSizer.isRightHalf(localX, outer.cardWidthProperty.get());
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
                        CardGroupRegistry.dropInsertIntoGroup(group, insertionIndex, srcElements, null);
                movedElements = srcElements;
                for (CardsGroup sourceGroup : moveSourceGroups) {
                    CardGroupRegistry.markDirtyAndRefreshForGroup(sourceGroup);
                }
            } else {
                java.util.List<Model.CardsLists.Card> srcCards =
                        new java.util.ArrayList<>(Controller.DragDropManager.getDraggedCards());
                java.util.List<CardElement> newlyAddedElements = new java.util.ArrayList<>();
                CardGroupRegistry.dropInsertIntoGroup(
                        group, insertionIndex, null, srcCards, newlyAddedElements);
                // My Collection only: open edit popup for cards dropped without a printCode.
                if (outer.isMyCollectionTabSelected()) {
                    outer.openEditPopupsForNoPrintCode(newlyAddedElements, this);
                }
            }
            CardGroupRegistry.markDirtyAndRefreshForGroup(group);

            // OuicheList MOVE notifications: fired AFTER markDirtyAndRefreshForGroup so
            // the D&C rebuild is already queued before the OuicheList rebuild is queued.
            // This guarantees the OuicheList refresh runs in a later pulse and never
            // interferes with the in-progress D&C scene graph update.
            // A move across groups = removal from source + addition to target.
            // A pure intra-group reorder (every element's source group is this same
            // target group) repositions the matching slot instead.
            if (moveSourceGroups != null && movedElements != null) {
                final java.util.List<CardElement> capturedMoved = movedElements;
                boolean anyCrossGroup = moveSourceGroups.stream().anyMatch(sourceGroup -> sourceGroup != group);
                if (anyCrossGroup) {
                    for (CardsGroup sourceGroup : moveSourceGroups) {
                        if (sourceGroup != group) {
                            CardGroupRegistry.notifyOuicheListOfGroupRemovals(sourceGroup, capturedMoved);
                        }
                    }
                    CardGroupRegistry.notifyOuicheListOfGroupAdditions(group, capturedMoved);
                } else if (moveSourceGroups.size() == 1 && moveSourceGroups.contains(group)) {
                    CardGroupRegistry.notifyOuicheListOfGroupReorder(group, capturedMoved);
                }
            }
            // Signal to grid.setOnDragDropped that this drop is already handled.
            CardGroupRegistry.dropHandledByCell = true;
            event.setDropCompleted(true);
            event.consume();
        });
    }

    // Helper: remove the given CardElement from the owned collection and refresh UI.
    // Tries MenuActionHandler.handleRemove(...) first, falls back to direct removal.
    void removeCardElement(Model.CardsLists.CardElement cardElement) {
        if (cardElement == null) {
            return;
        }

        // 1) Try centralized handler via reflection if available
        try {
            java.lang.reflect.Method m = Controller.MenuActionHandler.class.getMethod("handleRemove", Model.CardsLists.CardElement.class);
            if (m != null) {
                m.invoke(null, cardElement);
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
                                Model.CardsLists.CardElement candidateElement = it.next();
                                if (candidateElement == cardElement) {
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
                                    Model.CardsLists.CardElement candidateElement = it.next();
                                    if (candidateElement == cardElement) {
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
        outer.imageLoader.loadCardImage(cardElement, cardImageView);

        GlowComputationResult glowResult = computeGlowAndTooltips(cardElement);
        applyGlowEffect(glowResult.glowPriority());

        if (applyOuicheGrayscaleOrSuppress(cardElement)) {
            return;
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
        currentGlowPriority = glowResult.glowPriority();
        currentTooltips = glowResult.tooltips();

        applyConditionRarityBadges(cardElement);

        // Finalize graphic
        setGraphic(wrapper);
    }

    /**
     * Computes the glow priority and hover-tooltip warnings for {@code cardElement}.
     * Tooltip entries are always collected (so the hover popup can show every
     * applicable warning), while the returned glow priority reflects only the
     * highest-priority visual effect that should be applied to the cell.
     *
     * <p>Priorities (higher wins): 4 = OuicheList substandard-quality (red),
     * 3 = missing print code (red), 2 = missing condition/rarity (white, only when
     * incomplete-marking is enabled), 1 = archetype/artwork missing or needs-sorting
     * (white).</p>
     */
    private GlowComputationResult computeGlowAndTooltips(CardElement cardElement) {
        // Tooltip list: always computed, regardless of marking toggle.
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
                if (outer.incompleteMarkingEnabled) {
                    glowPriority = 3;
                }
            }
            if (myCollTab
                    && (cardElement.getCondition() == null || cardElement.getRarity() == null)) {
                tooltips.add(new String[]{
                        "This card is missing its condition or rarity.", "#EB9E34"});
                if (outer.incompleteMarkingEnabled && glowPriority < 2) {
                    glowPriority = 2;
                }
            }

            // ── White-glow conditions (archetype / artwork missing, needs-sort) ─────────
            // Always evaluated so their tooltip entries are always collected.
            // glowPriority is only elevated to 1 when it is still 0 (higher-priority
            // My Collection glows have already won and keep their effect).
            {
                Object userData = null;
                try {
                    if (this.getGridView() != null) {
                        userData = this.getGridView().getUserData();
                    }
                } catch (Exception ignored) {
                }

                Set<String> missingSet = null;
                Set<String> missingArtworkSet = null;
                String elementNameFromUserData = null;

                if (userData instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) userData;

                    Object missingSetValue = map.get("missingSet");
                    if (missingSetValue instanceof Set) {
                        @SuppressWarnings("unchecked")
                        Set<String> castMissingSet = (Set<String>) missingSetValue;
                        missingSet = castMissingSet;
                    } else if (missingSetValue instanceof Collection) {
                        Set<String> collectedSet = new HashSet<>();
                        for (Object entry : (Collection<?>) missingSetValue) {
                            if (entry != null) {
                                collectedSet.add(entry.toString());
                            }
                        }
                        missingSet = collectedSet;
                    }

                    Object missingArtworkValue = map.get("missingArtworkSet");
                    if (missingArtworkValue instanceof Set) {
                        @SuppressWarnings("unchecked")
                        Set<String> castMissingArtworkSet = (Set<String>) missingArtworkValue;
                        missingArtworkSet = castMissingArtworkSet;
                    }

                    Object elementNameValue = map.get("elementName");
                    if (elementNameValue instanceof String) {
                        elementNameFromUserData = (String) elementNameValue;
                    }

                } else if (userData instanceof Set) {
                    @SuppressWarnings("unchecked")
                    Set<String> castMissingSet = (Set<String>) userData;
                    missingSet = castMissingSet;
                } else if (userData instanceof String) {
                    elementNameFromUserData = (String) userData;
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
                            if (glowPriority == 0) {
                                glowPriority = 1;
                            }
                            tooltips.add(new String[]{
                                    "This card is missing in the collection.",
                                    "#EB9E34"});
                        } else if (myCollTab) {
                            // Non-archetype needs-sorting: My Collection tab only.
                            if (glowPriority == 0) {
                                glowPriority = 1;
                            }
                            tooltips.add(new String[]{
                                    CardHoverPopup.NEEDS_SORTING_WARNING,
                                    "#EB9E34"});
                        }
                    }
                }

                // 1b — missing artwork set (check both konamiId and passCode)
                // Only apply on Decks and Collections tab, not OuicheList
                if (!ouicheTab && missingArtworkSet != null && !missingArtworkSet.isEmpty()) {
                    boolean artMissing = (konamiId != null && missingArtworkSet.contains(konamiId))
                            || (passCode != null && missingArtworkSet.contains(passCode));
                    if (artMissing) {
                        if (glowPriority == 0) {
                            glowPriority = 1;
                        }
                        tooltips.add(new String[]{
                                "Not all artworks of this card are present in this collection.",
                                "#EB9E34"});
                    }
                }

                // 1c — sorting check: only on My Collection tab.
                //      Cards in D&C get DOWNGRADE_WARNING via 1d below, not here.
                if (elementNameFromUserData != null
                        && !elementNameFromUserData.trim().isEmpty()
                        && outer.isMyCollectionTabSelected()) {
                    try {
                        boolean genuinelyNeeded = false;
                        try {
                            genuinelyNeeded = Controller.CardSortingRules
                                    .computeCardNeedsSorting(card, elementNameFromUserData);
                        } catch (Throwable ignored) {
                        }

                        boolean upgradeNeeded = false;
                        if (!genuinelyNeeded) {
                            try {
                                upgradeNeeded = Controller.CardSortingRules
                                        .computeCardNeedsSortingWithUpgrade(
                                                cardElement, elementNameFromUserData);
                            } catch (Throwable ignored) {
                            }
                        }

                        if (genuinelyNeeded) {
                            if (glowPriority == 0) {
                                glowPriority = 1;
                            }
                            tooltips.add(new String[]{
                                    CardHoverPopup.NEEDS_SORTING_WARNING,
                                    "#EB9E34"});
                        } else if (upgradeNeeded) {
                            if (glowPriority == 0) {
                                glowPriority = 1;
                            }
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
                                        .isDeckOrCollectionName(elementNameFromUserData);
                                if (isDeckName) {
                                    boolean isDegraded = Controller.CardQualityService
                                            .isDegradedCopyInDeckOrCollection(
                                                    cardElement, elementNameFromUserData);
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
        return new GlowComputationResult(glowPriority, tooltips);
    }

    /**
     * Applies the drop-shadow "glow" effect to {@code wrapper} matching the given
     * priority: 4+ = red (OuicheList substandard-quality), 3 = red (no print code),
     * 1–2 = white (archetype/artwork missing, needs-sort, or condition/rarity
     * missing), 0 = no glow.
     */
    private void applyGlowEffect(int glowPriority) {
        if (glowPriority >= 4) {
            // Red — OWNED_SUBSTANDARD in OuicheList tab
            DropShadow innerShadow = new DropShadow();
            innerShadow.setColor(javafx.scene.paint.Color.web("#FF3333", 1.0));
            innerShadow.setOffsetX(0);
            innerShadow.setOffsetY(0);
            innerShadow.setRadius(4);
            innerShadow.setSpread(0.9);
            DropShadow outerShadow = new DropShadow();
            outerShadow.setColor(javafx.scene.paint.Color.web("#FF3333", 0.40));
            outerShadow.setOffsetX(0);
            outerShadow.setOffsetY(0);
            outerShadow.setRadius(14);
            outerShadow.setSpread(0.12);
            outerShadow.setInput(innerShadow);
            wrapper.setEffect(outerShadow);
        } else if (glowPriority >= 3) {
            // Red — no printCode
            DropShadow innerShadow = new DropShadow();
            innerShadow.setColor(javafx.scene.paint.Color.web("#ff3333", 1.0));
            innerShadow.setOffsetX(0);
            innerShadow.setOffsetY(0);
            innerShadow.setRadius(4);
            innerShadow.setSpread(0.9);
            DropShadow outerShadow = new DropShadow();
            outerShadow.setColor(javafx.scene.paint.Color.web("#ff3333", 0.35));
            outerShadow.setOffsetX(0);
            outerShadow.setOffsetY(0);
            outerShadow.setRadius(14);
            outerShadow.setSpread(0.12);
            outerShadow.setInput(innerShadow);
            wrapper.setEffect(outerShadow);
        } else if (glowPriority >= 1) {
            // White — covers priority 1 (archetype/artwork missing, needs-sort)
            //         AND priority 2 (condition/rarity missing, marking enabled).
            DropShadow innerShadow = new DropShadow();
            innerShadow.setColor(javafx.scene.paint.Color.web("#ffffff", 1.0));
            innerShadow.setOffsetX(0);
            innerShadow.setOffsetY(0);
            innerShadow.setRadius(4);
            innerShadow.setSpread(0.9);
            DropShadow outerShadow = new DropShadow();
            outerShadow.setColor(javafx.scene.paint.Color.web("#ffffff", 0.22));
            outerShadow.setOffsetX(0);
            outerShadow.setOffsetY(0);
            outerShadow.setRadius(14);
            outerShadow.setSpread(0.12);
            outerShadow.setInput(innerShadow);
            wrapper.setEffect(outerShadow);
        } else {
            wrapper.setEffect(null);
        }
    }

    /**
     * Applies the OuicheList "owned card" grayscale effect, or — when the
     * "Hide owned cards" toggle is active — suppresses the cell entirely.
     *
     * @return {@code true} if the cell was suppressed and {@link #updateItem}
     * should return immediately without doing any further work.
     */
    private boolean applyOuicheGrayscaleOrSuppress(CardElement cardElement) {
        // Apply grayscale for owned cards in OuicheList tab (on the imageView only).
        // This is intentionally applied to cardImageView, not wrapper, so it is
        // independent of the glow effect and does not affect other tabs.
        boolean ouicheSelected = outer.isOuicheListTabSelected();
        boolean owned = cardElement.getOwnershipStatus() != null
                && cardElement.getOwnershipStatus() == OwnershipStatus.OWNED;
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
                return true;
            }
            javafx.scene.effect.ColorAdjust grayscale = new javafx.scene.effect.ColorAdjust();
            grayscale.setSaturation(-0.7);   // 70% desaturation
            grayscale.setBrightness(-0.5);   // darken by 50%
            cardImageView.setEffect(grayscale);
        } else {
            cardImageView.setEffect(null);
        }
        return false;
    }

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
     * Rebuilds the condition/rarity corner badge nodes so they match the current
     * {@code cardElement} and the current overlay toggle state. Shown on My
     * Collection, Decks &amp; Collections, and OuicheList; never shown on Archetypes
     * (read-only, no ownership metadata) or inside archetype groups.
     */
    private void applyConditionRarityBadges(CardElement cardElement) {
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
    }

    /**
     * Returns the Deck or ThemeCollection in the DecksAndCollectionsList
     * that owns the given CardsGroup (matched by backing-list identity).
     * Returns null when the group belongs to MyCollection or cannot be found.
     */
    Object findDecksAndCollectionsOwnerForCardsGroup(CardsGroup group) {
        if (group == null) return null;
        List<CardElement> list = group.getCardList();
        if (list == null) return null;
        Model.CardsLists.DecksAndCollectionsList decksAndCollectionsList =
                Controller.UserInterfaceFunctions.getDecksList();
        if (decksAndCollectionsList == null) return null;
        if (decksAndCollectionsList.getCollections() != null) {
            for (Model.CardsLists.ThemeCollection themeCollection : decksAndCollectionsList.getCollections()) {
                if (themeCollection == null) continue;
                if (list == themeCollection.getCardsList() || list == themeCollection.getExceptionsToNotAdd())
                    return themeCollection;
                if (themeCollection.getLinkedDecks() != null) {
                    for (List<Model.CardsLists.Deck> unit : themeCollection.getLinkedDecks()) {
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
        if (decksAndCollectionsList.getDecks() != null) {
            for (Model.CardsLists.Deck deck : decksAndCollectionsList.getDecks()) {
                if (deck == null) continue;
                if (list == deck.getMainDeck()
                        || list == deck.getExtraDeck()
                        || list == deck.getSideDeck()) return deck;
            }
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
                            owner = findDecksAndCollectionsOwnerForCardsGroup(entry.getKey());
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
                String collName = CardNameUtils.sanitize(((ThemeCollection) parentData).getName());
                if ("Cards not to add".equals(groupName)) return collName + " / Exclusion List";
                return collName;
            }

            // Child of a Deck (Main/Extra/Side Deck group)
            if (parentData instanceof Deck) {
                String deckName = CardNameUtils.sanitize(((Deck) parentData).getName());
                TreeItem<String> sectionItem = parent.getParent(); // DECKS_SECTION
                if (sectionItem != null) {
                    TreeItem<String> collOrRoot = sectionItem.getParent();
                    if (collOrRoot != null) {
                        Object collData = (collOrRoot instanceof DataTreeItem)
                                ? ((DataTreeItem<?>) collOrRoot).getData() : null;
                        if (collData instanceof ThemeCollection) {
                            String collName = CardNameUtils.sanitize(((ThemeCollection) collData).getName());
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

    /**
     * Resolves a display-path (built by the destination-menu-item helpers in
     * {@link CardGridCellContextMenuBuilder}) to the actual backing
     * List<CardElement> inside the DecksAndCollectionsList.
     * <p>
     * Path patterns (names are already sanitized — no = or -):
     * "CollName"                              → themeCollection.getCardsList()
     * "CollName / Exclusion List"             → themeCollection.getExceptionsToNotAdd()
     * "DeckName / Main|Extra|Side Deck"       → standalone deck list
     * "CollName / DeckName / Main|Extra|Side" → linked deck list
     */
    public List<Model.CardsLists.CardElement> resolveDecksTargetList(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        Model.CardsLists.DecksAndCollectionsList decksAndCollectionsList =
                Controller.UserInterfaceFunctions.getDecksList();
        if (decksAndCollectionsList == null) return null;

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
            Model.CardsLists.ThemeCollection themeCollection = findCollByDisplayName(parts[0], decksAndCollectionsList);
            if (themeCollection != null) {
                if (themeCollection.getCardsList() == null) themeCollection.setCardsList(new ArrayList<>());
                return themeCollection.getCardsList();
            }
            return null;
        }

        // "CollName / Exclusion List"
        if (parts.length == 2 && isExcl) {
            Model.CardsLists.ThemeCollection themeCollection = findCollByDisplayName(parts[0], decksAndCollectionsList);
            if (themeCollection != null) {
                if (themeCollection.getExceptionsToNotAdd() == null)
                    themeCollection.setExceptionsToNotAdd(new ArrayList<>());
                return themeCollection.getExceptionsToNotAdd();
            }
            return null;
        }

        // "DeckName / Main|Extra|Side Deck"  (standalone deck)
        if (parts.length == 2 && (isMain || isExtra || isSide)) {
            Model.CardsLists.Deck deck = findStandaloneDeckByDisplayName(parts[0], decksAndCollectionsList);
            if (deck != null) {
                if (isMain) return deck.getMainDeck();
                if (isExtra) return deck.getExtraDeck();
                return deck.getSideDeck();
            }
            return null;
        }

        // "CollName / DeckName / Main|Extra|Side Deck"  (linked deck)
        if (parts.length == 3 && (isMain || isExtra || isSide)) {
            Model.CardsLists.ThemeCollection themeCollection = findCollByDisplayName(parts[0], decksAndCollectionsList);
            if (themeCollection != null) {
                Model.CardsLists.Deck deck = findLinkedDeckByDisplayName(parts[1], themeCollection);
                if (deck != null) {
                    if (isMain) return deck.getMainDeck();
                    if (isExtra) return deck.getExtraDeck();
                    return deck.getSideDeck();
                }
            }
            return null;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────────
// Target-list resolution helpers
// ─────────────────────────────────────────────────────────────────────────────

    private Model.CardsLists.ThemeCollection findCollByDisplayName(
            String displayName, Model.CardsLists.DecksAndCollectionsList decksAndCollectionsList) {
        if (decksAndCollectionsList == null || decksAndCollectionsList.getCollections() == null) return null;
        for (Model.CardsLists.ThemeCollection themeCollection : decksAndCollectionsList.getCollections()) {
            if (themeCollection == null) continue;
            if (CardNameUtils.sanitize(themeCollection.getName()).equals(displayName)) return themeCollection;
        }
        return null;
    }

    private Model.CardsLists.Deck findStandaloneDeckByDisplayName(
            String displayName, Model.CardsLists.DecksAndCollectionsList decksAndCollectionsList) {
        if (decksAndCollectionsList == null || decksAndCollectionsList.getDecks() == null) return null;
        for (Model.CardsLists.Deck deck : decksAndCollectionsList.getDecks()) {
            if (deck == null) continue;
            if (CardNameUtils.sanitize(deck.getName()).equals(displayName)) return deck;
        }
        return null;
    }

    private Model.CardsLists.Deck findLinkedDeckByDisplayName(
            String displayName, Model.CardsLists.ThemeCollection themeCollection) {
        if (themeCollection == null || themeCollection.getLinkedDecks() == null) return null;
        for (List<Model.CardsLists.Deck> unit : themeCollection.getLinkedDecks()) {
            if (unit == null) continue;
            for (Model.CardsLists.Deck deck : unit) {
                if (deck == null) continue;
                if (CardNameUtils.sanitize(deck.getName()).equals(displayName)) return deck;
            }
        }
        return null;
    }

    /**
     * Returns true when the card list that {@code path} resolves to is currently
     * empty (meaning the corresponding DataTreeItem was never inserted in the tree
     * and a structural refresh will be needed after the add).
     */
    boolean isDecksTargetEmpty(String[] parts, boolean isExclusion) {
        try {
            Model.CardsLists.DecksAndCollectionsList decksAndCollectionsList =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (decksAndCollectionsList == null) return false;

            String collName = parts[0].trim();

            // ── Collection-level targets ─────────────────────────────────────
            if (decksAndCollectionsList.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection themeCollection : decksAndCollectionsList.getCollections()) {
                    if (themeCollection == null) continue;
                    String tcName = themeCollection.getName() == null ? "" : themeCollection.getName().trim();
                    if (!collName.equals(tcName)) continue;

                    if (isExclusion) {
                        java.util.List<Model.CardsLists.CardElement> exc =
                                themeCollection.getExceptionsToNotAdd();
                        return exc == null || exc.isEmpty();
                    }
                    if (parts.length == 1) {
                        java.util.List<Model.CardsLists.CardElement> cl = themeCollection.getCardsList();
                        return cl == null || cl.isEmpty();
                    }
                    // "CollName / DeckName / Main|Extra|Side Deck"
                    if (parts.length >= 3 && themeCollection.getLinkedDecks() != null) {
                        String deckName = parts[1].trim();
                        String section = parts[parts.length - 1].trim().toLowerCase(
                                java.util.Locale.ROOT);
                        for (java.util.List<Model.CardsLists.Deck> unit :
                                themeCollection.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Model.CardsLists.Deck deck : unit) {
                                if (deck == null) continue;
                                String candidateDeckName = deck.getName() == null ? "" : deck.getName().trim();
                                if (!deckName.equals(candidateDeckName)) continue;
                                java.util.List<Model.CardsLists.CardElement> lst =
                                        section.contains("extra") ? deck.getExtraDeck()
                                                : section.contains("side") ? deck.getSideDeck()
                                                : deck.getMainDeck();
                                return lst == null || lst.isEmpty();
                            }
                        }
                    }
                }
            }

            // ── Standalone deck targets ("DeckName / Main|Extra|Side Deck") ──
            if (parts.length >= 2 && decksAndCollectionsList.getDecks() != null) {
                String section = parts[parts.length - 1].trim().toLowerCase(
                        java.util.Locale.ROOT);
                for (Model.CardsLists.Deck deck : decksAndCollectionsList.getDecks()) {
                    if (deck == null) continue;
                    String candidateDeckName = deck.getName() == null ? "" : deck.getName().trim();
                    if (!collName.equals(candidateDeckName)) continue;
                    java.util.List<Model.CardsLists.CardElement> lst =
                            section.contains("extra") ? deck.getExtraDeck()
                                    : section.contains("side") ? deck.getSideDeck()
                                    : deck.getMainDeck();
                    return lst == null || lst.isEmpty();
                }
            }
        } catch (Exception ignored) {
        }
        return false;
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
     * Returns the Deck or ThemeCollection that owns the list at {@code path},
     * or null if the path cannot be resolved.
     */
    Object resolveDecksTargetOwner(String path) {
        if (path == null) return null;
        Model.CardsLists.DecksAndCollectionsList decksAndCollectionsList =
                Controller.UserInterfaceFunctions.getDecksList();
        if (decksAndCollectionsList == null) return null;

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
            return findCollByDisplayName(parts[0], decksAndCollectionsList);
        }
        // "DeckName / Main|Extra|Side Deck"
        if (parts.length == 2 && (isMain || isExtra || isSide)) {
            Model.CardsLists.Deck deck = findStandaloneDeckByDisplayName(parts[0], decksAndCollectionsList);
            if (deck != null) return deck;
        }
        // "CollName / DeckName / Main|Extra|Side Deck"
        if (parts.length == 3 && (isMain || isExtra || isSide)) {
            Model.CardsLists.ThemeCollection themeCollection = findCollByDisplayName(parts[0], decksAndCollectionsList);
            if (themeCollection != null) {
                Model.CardsLists.Deck deck = findLinkedDeckByDisplayName(parts[1], themeCollection);
                if (deck != null) return deck;
            }
        }
        return null;
    }

    /**
     * Result of {@link #computeGlowAndTooltips}: the glow priority to apply to the
     * cell (0 = none, higher wins) and the full list of hover-tooltip warning
     * entries (each {@code [message, cssColor]}) collected regardless of priority.
     */
    private record GlowComputationResult(int glowPriority, java.util.List<String[]> tooltips) {
    }
}