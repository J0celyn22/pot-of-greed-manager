package Controller;

import Model.CardsLists.Box;
import View.NavigationItem;
import javafx.scene.control.Label;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;

import java.util.function.BiConsumer;

/**
 * Attaches JavaFX drag-and-drop event handlers to left-navigation-menu items
 * (Boxes, sub-Boxes, CardsGroups, Decks, ThemeCollections).
 * <p>
 * Extracted from identical copies previously maintained separately in
 * {@link MyCollectionController} and {@link DecksCollectionsController}. This
 * class only wires the JavaFX event handlers; the current-drag bookkeeping
 * lives in {@link DragDropManager}, drop-position resolution lives in
 * {@link NavigationHelper}, and the actual model reordering on a completed
 * nav-to-nav drop is left to the caller via the {@code onNavDrop} callback
 * (see {@link NavigationDragDrop} for that reordering logic).
 * </p>
 */
final class NavigationDragDropWiring {

    private NavigationDragDropWiring() {
    }

    /**
     * Makes {@code navItem} a drop target for card payloads coming from the
     * RIGHT or MIDDLE panes, delegating the actual paste to
     * {@link RealMainController#doCardPasteOnNavItem}.
     *
     * @param navItem     the navigation item to make droppable
     * @param modelObj    the model object (Box, CardsGroup, Deck, ThemeCollection…)
     *                    that {@code navItem} represents
     * @param coordinator the main controller that performs the actual paste
     */
    static void attachNavItemDropHandlers(NavigationItem navItem, Object modelObj,
                                          RealMainController coordinator) {
        navItem.setOnDragOver(event -> {
            if (event.getDragboard().hasString()
                    && DragDropManager.getDragSourcePane() != null) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        navItem.setOnDragDropped(event -> {
            String sourcePane = DragDropManager.getDragSourcePane();
            if (sourcePane == null) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            coordinator.doCardPasteOnNavItem(modelObj, sourcePane, event);
            event.setDropCompleted(true);
            event.consume();
        });
    }

    /**
     * Makes {@code navItem}'s label a drag source for nav-to-nav reordering
     * (dragging a Box/Deck/CardsGroup onto another nav item).
     *
     * @param navItem  the navigation item to make draggable
     * @param modelObj the model object {@code navItem} represents
     */
    static void enableNavDragSource(NavigationItem navItem, Object modelObj) {
        Label label = navItem.getLabel();
        label.setOnDragDetected(event -> {
            Dragboard dragboard = label.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString("NAV");
            dragboard.setContent(content);
            DragDropManager.startNavDrag(modelObj);
            event.consume();
        });
        label.setOnDragDone(event -> {
            DragDropManager.clearCurrentlyDraggedCard();
            navItem.clearDropIndicator();
            event.consume();
        });
    }

    /**
     * Makes {@code navItem} a drop target for both nav-to-nav reordering and
     * card payloads. Shows a live drop-position indicator while a nav drag is
     * in progress, and invokes {@code onNavDrop} once a nav-to-nav drop
     * completes so the caller can apply the actual model reordering (typically
     * via {@link NavigationDragDrop}).
     *
     * @param navItem     the navigation item to make a drop target
     * @param modelObj    the model object {@code navItem} represents
     * @param coordinator the main controller that performs card-payload pastes
     * @param onNavDrop   invoked with (draggedModelObject, dropPosition) once a
     *                    nav-to-nav drop completes onto a different item
     */
    static void enableNavItemAsNavDndTarget(NavigationItem navItem, Object modelObj,
                                            RealMainController coordinator,
                                            BiConsumer<Object,
                                                    NavigationItem.DropPosition> onNavDrop) {
        navItem.setOnDragOver(event -> {
            String sourcePane = DragDropManager.getDragSourcePane();
            if (event.getDragboard().hasString() && sourcePane != null) {
                event.acceptTransferModes(TransferMode.MOVE);
                if ("NAV".equals(sourcePane)) {
                    boolean isContainer = modelObj instanceof Box;
                    NavigationItem.DropPosition dropPos =
                            NavigationHelper.resolveDropPosition(navItem, event.getY(), isContainer);
                    navItem.showDropIndicator(dropPos);
                }
            }
            event.consume();
        });

        navItem.setOnDragExited(event -> {
            navItem.clearDropIndicator();
            event.consume();
        });

        navItem.setOnDragDropped(event -> {
            String sourcePane = DragDropManager.getDragSourcePane();
            if (sourcePane == null) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            if ("NAV".equals(sourcePane)) {
                Object dragged = DragDropManager.getDraggedNavObject();
                if (dragged != null && dragged != modelObj) {
                    boolean isContainer = modelObj instanceof Box;
                    NavigationItem.DropPosition dropPos =
                            NavigationHelper.resolveDropPosition(navItem, event.getY(), isContainer);
                    navItem.clearDropIndicator();
                    onNavDrop.accept(dragged, dropPos);
                }
            } else {
                coordinator.doCardPasteOnNavItem(modelObj, sourcePane, event);
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }
}