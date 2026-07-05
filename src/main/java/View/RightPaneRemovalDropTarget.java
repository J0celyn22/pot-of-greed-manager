package View;

import Controller.CardGroupRegistry;
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
                // OuicheList tab elements are an ephemeral copy of the real Decks &
                // Collections model (see CardGroupRegistry.resolveRealGroupForOuicheListGroup).
                // Translate each to its real counterpart before removing, and route those
                // exclusively to the Decks & Collections removal — the OuicheList mirrors
                // Decks & Collections only, so it must never affect the Owned Cards
                // Collection. Elements that aren't OuicheList copies (a normal drag from the
                // My Collection or Decks & Collections tabs) keep their original behavior of
                // being tried against both removal paths.
                List<CardElement> decksAndCollectionsElements = new ArrayList<>();
                List<CardElement> ownedCollectionElements = new ArrayList<>();
                for (CardElement element : draggedElements) {
                    CardElement realElement = CardGroupRegistry.resolveRealElementForOuicheListElement(element);
                    if (realElement != null) {
                        decksAndCollectionsElements.add(realElement);
                    } else {
                        decksAndCollectionsElements.add(element);
                        ownedCollectionElements.add(element);
                    }
                }
                MenuActionHandler.handleBulkRemoveElementsFromDecksAndCollections(decksAndCollectionsElements);
                if (!ownedCollectionElements.isEmpty()) {
                    MenuActionHandler.handleBulkRemoveFromOwnedCollection(ownedCollectionElements);
                }
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }
}