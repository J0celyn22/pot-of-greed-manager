package Controller;

import Model.CardsLists.*;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the bulk "remove from owned collection" card-menu action: removing
 * every selected {@link CardElement} from the {@link OwnedCardsCollection} by
 * object identity, searching every Box and sub-Box.
 * <p>
 * Extracted from {@link MenuActionHandler}, which still exposes {@code
 * handleBulkRemoveFromOwnedCollection} as a thin delegate.
 * </p>
 */
final class CardBulkRemoveFromOwnedHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(CardBulkRemoveFromOwnedHandler.class);

    private CardBulkRemoveFromOwnedHandler() {
    }

    /**
     * Removes all elements in {@code elements} from the {@link OwnedCardsCollection}
     * by object identity. Marks the collection dirty and triggers a view refresh.
     *
     * @param elements the elements to remove
     */
    public static void handleBulkRemoveFromOwnedCollection(List<CardElement> elements) {
        if (elements == null || elements.isEmpty()) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                doBulkRemoveFromOwnedCollection(elements);
            } else {
                final List<CardElement> copy = new ArrayList<>(elements);
                Platform.runLater(() -> doBulkRemoveFromOwnedCollection(copy));
            }
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionView();
        } catch (Throwable throwable) {
            logger.debug("handleBulkRemoveFromOwnedCollection failed", throwable);
        }
    }

    private static void doBulkRemoveFromOwnedCollection(List<CardElement> elements) {
        OwnedCardsCollection owned = MenuActionHandler.safeGetOwnedCollection();
        if (owned == null || owned.getOwnedCollection() == null) {
            return;
        }
        for (CardElement targetElement : elements) {
            removeElementFromOwned(targetElement, owned);
        }
    }

    private static void removeElementFromOwned(CardElement targetElement,
                                               OwnedCardsCollection owned) {
        if (targetElement == null) {
            return;
        }
        for (Box box : owned.getOwnedCollection()) {
            if (box == null) {
                continue;
            }
            if (removeElementFromBox(targetElement, box)) {
                OuicheList.onOwnedCardRemoved(targetElement);
                UserInterfaceFunctions.refreshOuicheListView();
                return;
            }
        }
    }

    private static boolean removeElementFromBox(CardElement targetElement, Box box) {
        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                if (group == null) {
                    continue;
                }
                javafx.collections.ObservableList<CardElement> observableList =
                        CardGroupRegistry.observableListFor(group);
                if (observableList.remove(targetElement)) {
                    CardGroupRegistry.triggerHeightAdjustment(group);
                    return true;
                }
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box subBox : box.getSubBoxes()) {
                if (subBox != null && removeElementFromBox(targetElement, subBox)) {
                    return true;
                }
            }
        }
        return false;
    }
}