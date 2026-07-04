package View;

import Controller.DragDropManager;
import Controller.MenuActionHandler;
import Model.CardsLists.CardElement;
import javafx.scene.Node;
import javafx.scene.input.TransferMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Installs the shared MIDDLE-pane drop-target behavior used by the right-pane
 * card cells ({@link CardsListCell}, {@link CardsMosaicRowCell}). Dropping a
 * middle-pane element onto the installed node removes it from its source
 * (delete / remove-from-deck semantics).
 */
public final class RightPaneRemovalDropTarget {

    private RightPaneRemovalDropTarget() {
    }

    /**
     * Wires the drag-over and drag-dropped handlers that let {@code node} accept
     * MIDDLE-pane drags and remove the dropped elements from their source.
     *
     * @param node the right-pane cell node that should accept the drop
     */
    public static void install(Node node) {
        node.setOnDragOver(event -> {
            if ("MIDDLE".equals(DragDropManager.getDragSourcePane())
                    && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        node.setOnDragDropped(event -> {
            if (!"MIDDLE".equals(DragDropManager.getDragSourcePane())) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            List<CardElement> draggedElements =
                    new ArrayList<>(DragDropManager.getDraggedElements());
            if (!draggedElements.isEmpty()) {
                MenuActionHandler.handleBulkRemoveElementsFromDecksAndCollections(draggedElements);
                MenuActionHandler.handleBulkRemoveFromOwnedCollection(draggedElements);
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }
}