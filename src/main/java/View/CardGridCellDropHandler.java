package View;

import Controller.CardGroupRegistry;
import Controller.DragDropManager;
import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.CardsLists.CardsGroup;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CardGridCellDropHandler — wires all JavaFX drag-and-drop event handlers onto the
 * {@code wrapper} {@link StackPane} of a given {@link CardGridCell}.
 *
 * <p>This class was extracted from the anonymous lambda drag handlers that were previously
 * inlined inside the {@code CardGridCell} inner class constructor.  Extracting them here
 * keeps the drag logic in one cohesive place, making it easier to test and modify
 * independently of rendering concerns.</p>
 *
 * <p><strong>Double-fire guard:</strong> The {@code wrapper.setOnDragDropped} handler sets
 * {@link CardGroupRegistry#dropHandledByCell} to {@code true} when it successfully handles
 * a drop.  The enclosing {@code grid.setOnDragDropped} handler (wired in
 * {@code CardTreeCell.createCardsGroupCell}) checks this flag immediately and bails out
 * if it is set, preventing double-processing of the same drop event.  The flag is always
 * reset to {@code false} at the very start of {@code wrapper.setOnDragDropped} so it is
 * never stale across drag sessions.</p>
 */
public final class CardGridCellDropHandler {

    private static final Logger logger = LoggerFactory.getLogger(CardGridCellDropHandler.class);

    /**
     * The cell whose drag events are being handled.
     */
    private final CardGridCell cell;

    /**
     * The StackPane that the drag handlers are attached to.
     */
    private final StackPane wrapper;

    /**
     * Creates a new handler and immediately attaches all drag-and-drop event handlers to
     * {@code wrapper}.
     *
     * @param cell    the {@link CardGridCell} that owns the wrapper
     * @param wrapper the {@link StackPane} wrapper that receives drag events
     */
    public CardGridCellDropHandler(CardGridCell cell, StackPane wrapper) {
        this.cell = cell;
        this.wrapper = wrapper;
        wireDragHandlers();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Handler wiring
    // ─────────────────────────────────────────────────────────────────────────

    private void wireDragHandlers() {
        wrapper.setOnDragDetected(this::handleDragDetected);
        wrapper.setOnDragDone(this::handleDragDone);
        wrapper.setOnDragOver(this::handleDragOver);
        wrapper.setOnDragDropped(this::handleDragDropped);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drag-detected — builds the payload and starts the drag session
    // ─────────────────────────────────────────────────────────────────────────

    private void handleDragDetected(javafx.scene.input.MouseEvent event) {
        CardElement draggedElement = cell.getItem();
        if (draggedElement == null || draggedElement.getCard() == null) {
            return;
        }

        if (cell.outer.isInArchetypeGroup()) {
            // Archetype cards are read-only — behave like a right-pane (ADD) drag.
            startArchetypeDrag(draggedElement);
            event.consume();
            return;
        }

        // Collect the full middle-pane selection as the drag payload.
        Set<CardElement> selectedElements = Controller.SelectionManager.getSelectedMiddleElements();
        List<CardElement> dragElements = new ArrayList<>();
        dragElements.add(draggedElement);
        if ("MIDDLE".equals(Controller.SelectionManager.getActivePart())
                && selectedElements.size() > 1
                && selectedElements.contains(draggedElement)) {
            for (CardElement selectedElement : selectedElements) {
                if (selectedElement != draggedElement) {
                    dragElements.add(selectedElement);
                }
            }
        }

        // Build the ghost image (capped at 5 for performance — payload is uncapped).
        List<Image> ghostImages = new ArrayList<>();
        int ghostImageCount = Math.min(dragElements.size(), 5);
        for (int imageIndex = 0; imageIndex < ghostImageCount; imageIndex++) {
            CardElement element = dragElements.get(imageIndex);
            Card card = element.getCard();
            String imageKey = card != null ? card.getImagePath() : null;
            String resolvedPath = imageKey != null ? CardTreeCell.imagePathCache.get(imageKey) : null;
            ghostImages.add(resolvedPath != null
                    ? Utils.LruImageCache.getImage(resolvedPath) : null);
        }

        Dragboard dragboard = wrapper.startDragAndDrop(TransferMode.MOVE);
        javafx.scene.image.WritableImage ghost = DragDropManager.buildDragGhost(
                ghostImages,
                cell.outer.cardWidthProperty.get(),
                cell.outer.cardHeightProperty.get());
        if (ghost != null) {
            dragboard.setDragView(
                    ghost,
                    cell.outer.cardWidthProperty.get() / 2.0,
                    cell.outer.cardHeightProperty.get() / 2.0);
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(draggedElement.getCard().getPassCode());
        dragboard.setContent(content);
        DragDropManager.startMiddleDrag(dragElements);
        event.consume();
    }

    /**
     * Starts an ADD drag for an archetype card (read-only source).
     */
    private void startArchetypeDrag(CardElement archetypeElement) {
        List<Card> draggedCards = new ArrayList<>();
        draggedCards.add(archetypeElement.getCard());

        Set<CardElement> selectedElements = Controller.SelectionManager.getSelectedMiddleElements();
        if ("MIDDLE".equals(Controller.SelectionManager.getActivePart())
                && selectedElements.size() > 1
                && selectedElements.contains(archetypeElement)) {
            for (CardElement selectedElement : selectedElements) {
                if (selectedElement != archetypeElement && selectedElement.getCard() != null) {
                    draggedCards.add(selectedElement.getCard());
                }
            }
        }

        List<Image> ghostImages = new ArrayList<>();
        int ghostImageCount = Math.min(draggedCards.size(), 5);
        for (int imageIndex = 0; imageIndex < ghostImageCount; imageIndex++) {
            Card card = draggedCards.get(imageIndex);
            String imageKey = card.getImagePath();
            String resolvedPath = imageKey != null ? CardTreeCell.imagePathCache.get(imageKey) : null;
            ghostImages.add(resolvedPath != null
                    ? Utils.LruImageCache.getImage(resolvedPath) : null);
        }

        Dragboard dragboard = wrapper.startDragAndDrop(TransferMode.MOVE);
        javafx.scene.image.WritableImage ghost = DragDropManager.buildDragGhost(
                ghostImages,
                cell.outer.cardWidthProperty.get(),
                cell.outer.cardHeightProperty.get());
        if (ghost != null) {
            dragboard.setDragView(
                    ghost,
                    cell.outer.cardWidthProperty.get() / 2.0,
                    cell.outer.cardHeightProperty.get() / 2.0);
        }

        ClipboardContent content = new ClipboardContent();
        content.putString(archetypeElement.getCard().getPassCode());
        dragboard.setContent(content);
        DragDropManager.startRightDrag(draggedCards);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drag-done — clears drag state
    // ─────────────────────────────────────────────────────────────────────────

    private void handleDragDone(javafx.scene.input.DragEvent event) {
        DragDropManager.clearCurrentlyDraggedCard();
        event.consume();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drag-over — accepts compatible transfer modes
    // ─────────────────────────────────────────────────────────────────────────

    private void handleDragOver(javafx.scene.input.DragEvent event) {
        if (!cell.outer.isInArchetypeGroup()
                && event.getDragboard().hasString()
                && DragDropManager.getDragSourcePane() != null) {
            event.acceptTransferModes(TransferMode.MOVE);
        }
        event.consume();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drag-dropped — performs the actual insert / move operation
    // ─────────────────────────────────────────────────────────────────────────

    private void handleDragDropped(javafx.scene.input.DragEvent event) {
        // Always reset the flag at the very start so it is never stale from a previous
        // drag session (e.g. after a right-pane drag where wrapper.setOnDragDone did not fire).
        CardGroupRegistry.dropHandledByCell = false;

        if (cell.outer.isInArchetypeGroup()) {
            event.setDropCompleted(false);
            event.consume();
            return;
        }

        CardElement anchorElement = cell.getItem();
        if (anchorElement == null) {
            event.setDropCompleted(false);
            event.consume();
            return;
        }

        CardsGroup targetGroup = CardGroupRegistry.findGroupForCardElement(anchorElement);
        if (targetGroup == null) {
            event.setDropCompleted(false);
            event.consume();
            return;
        }

        ObservableList<CardElement> targetList = CardGroupRegistry.observableListFor(targetGroup);
        int anchorIndex = targetList.indexOf(anchorElement);
        if (anchorIndex < 0) {
            event.setDropCompleted(false);
            event.consume();
            return;
        }

        // Determine insertion side: left half = before the card, right half = after.
        boolean insertAfterAnchor = CardTreeCell.isRightHalf(
                event.getX(), cell.outer.cardWidthProperty.get());
        int insertionIndex = insertAfterAnchor ? anchorIndex + 1 : anchorIndex;

        String sourcePane = DragDropManager.getDragSourcePane();
        boolean isMiddleDrag = "MIDDLE".equals(sourcePane);

        if (isMiddleDrag) {
            List<CardElement> sourceElements =
                    new ArrayList<>(DragDropManager.getDraggedElements());
            // Don't move an element onto itself.
            if (sourceElements.size() == 1 && sourceElements.get(0) == anchorElement) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            Set<CardsGroup> modifiedSourceGroups =
                    CardGroupRegistry.dropInsertIntoGroup(
                            targetGroup, insertionIndex, sourceElements, null);
            for (CardsGroup sourceGroup : modifiedSourceGroups) {
                CardGroupRegistry.markDirtyAndRefreshForGroup(sourceGroup);
            }
        } else {
            // RIGHT-pane drag: ADD semantics.
            List<Card> sourceCards = new ArrayList<>(DragDropManager.getDraggedCards());
            CardGroupRegistry.dropInsertIntoGroup(
                    targetGroup, insertionIndex, null, sourceCards);
            // My Collection only: open edit popup for cards dropped without a printCode.
            if (cell.outer.isMyCollectionTabSelected()) {
                cell.outer.openEditPopupsForNoPrintCode(sourceCards, targetGroup, cell);
            }
        }

        CardGroupRegistry.markDirtyAndRefreshForGroup(targetGroup);
        // Signal to the enclosing grid.setOnDragDropped that this drop is already handled.
        CardGroupRegistry.dropHandledByCell = true;
        event.setDropCompleted(true);
        event.consume();
    }
}