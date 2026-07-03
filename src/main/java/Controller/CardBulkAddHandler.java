package Controller;

import Model.CardsLists.Card;
import Model.CardsLists.ThemeCollection;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the bulk-add card-menu actions: adding a copy of every card in a
 * selection to the owned collection, a Deck, a {@link ThemeCollection}'s
 * card list, or a collection's exclusion list.
 * <p>
 * Extracted from {@link MenuActionHandler}, which still exposes {@code
 * handleBulkAddCopy}, {@code handleBulkAddToDeck}, {@code
 * handleBulkAddToCollectionCards} and {@code handleBulkAddToExclusionList} as
 * thin delegates. Each method here is just a per-card loop over the
 * single-card logic already extracted to {@link CardCopyHandler} and
 * {@link CardAddToListHandler}; no new core logic lives here.
 * </p>
 */
final class CardBulkAddHandler {

    private static final Logger logger = LoggerFactory.getLogger(CardBulkAddHandler.class);

    private CardBulkAddHandler() {
    }

    /**
     * Adds a copy of each card in {@code cards} to the group identified by
     * {@code handlerTarget} in the owned collection.
     *
     * @param cards         the cards to copy
     * @param handlerTarget canonical target string
     */
    public static void handleBulkAddCopy(java.util.Collection<Card> cards, String handlerTarget) {
        if (cards == null || cards.isEmpty() || handlerTarget == null) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                for (Card card : cards) {
                    CardCopyHandler.doAddCopy(card, handlerTarget);
                }
            } else {
                final List<Card> copy = new ArrayList<>(cards);
                Platform.runLater(() -> {
                    for (Card card : copy) {
                        CardCopyHandler.doAddCopy(card, handlerTarget);
                    }
                });
            }
            UserInterfaceFunctions.refreshOwnedCollectionView();
        } catch (Throwable throwable) {
            logger.debug("handleBulkAddCopy failed for target {}", handlerTarget, throwable);
        }
    }

    /**
     * Adds a copy of each card in {@code cards} to the deck list identified by
     * {@code handlerTarget}.
     *
     * @param cards         the cards to add
     * @param handlerTarget canonical target string, e.g. {@code "DeckName / Main Deck"}
     */
    public static void handleBulkAddToDeck(java.util.Collection<Card> cards, String handlerTarget) {
        if (cards == null || cards.isEmpty() || handlerTarget == null) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                for (Card card : cards) {
                    CardAddToListHandler.doAddToDeck(card, handlerTarget);
                }
            } else {
                final List<Card> copy = new ArrayList<>(cards);
                Platform.runLater(() -> {
                    for (Card card : copy) {
                        CardAddToListHandler.doAddToDeck(card, handlerTarget);
                    }
                });
            }
            MenuActionHandler.setLastDecksAddedTarget(handlerTarget);
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        } catch (Throwable throwable) {
            logger.error("handleBulkAddToDeck failed for target '{}'", handlerTarget, throwable);
        }
    }

    /**
     * Adds a copy of each card in {@code cards} to the card list of the named
     * {@link ThemeCollection}.
     *
     * @param cards          the cards to add
     * @param collectionName the name of the target collection
     */
    public static void handleBulkAddToCollectionCards(
            java.util.Collection<Card> cards, String collectionName) {
        if (cards == null || cards.isEmpty() || collectionName == null) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                for (Card card : cards) {
                    CardAddToListHandler.doAddToCollectionCards(card, collectionName);
                }
            } else {
                final List<Card> copy = new ArrayList<>(cards);
                Platform.runLater(() -> {
                    for (Card card : copy) {
                        CardAddToListHandler.doAddToCollectionCards(card, collectionName);
                    }
                });
            }
            MenuActionHandler.setLastDecksAddedTarget(collectionName + " / Cards");
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        } catch (Throwable throwable) {
            logger.error("handleBulkAddToCollectionCards failed for collection '{}'",
                    collectionName, throwable);
        }
    }

    /**
     * Adds a copy of each card in {@code cards} to the exclusion list of the named
     * {@link ThemeCollection}.
     *
     * @param cards          the cards to exclude
     * @param collectionName the name of the target collection
     */
    public static void handleBulkAddToExclusionList(
            java.util.Collection<Card> cards, String collectionName) {
        if (cards == null || cards.isEmpty() || collectionName == null) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                for (Card card : cards) {
                    CardAddToListHandler.doAddToExclusionList(card, collectionName);
                }
            } else {
                final List<Card> copy = new ArrayList<>(cards);
                Platform.runLater(() -> {
                    for (Card card : copy) {
                        CardAddToListHandler.doAddToExclusionList(card, collectionName);
                    }
                });
            }
            MenuActionHandler.setLastDecksAddedTarget(collectionName + " / Cards not to add");
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        } catch (Throwable throwable) {
            logger.debug("handleBulkAddToExclusionList failed for {}", collectionName, throwable);
        }
    }
}