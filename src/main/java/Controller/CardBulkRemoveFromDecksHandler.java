package Controller;

import Model.CardsLists.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the bulk "remove from Decks &amp; Collections" card-menu actions:
 * removing every card matching a given set (by Card identity) or every
 * specific {@link CardElement} (by object identity) from every list in a
 * {@link DecksAndCollectionsList} — collection card lists, exclusion lists,
 * and every Deck's main/extra/side lists.
 * <p>
 * Extracted from {@link MenuActionHandler}, which still exposes {@code
 * handleBulkRemoveFromDecksAndCollections} and {@code
 * handleBulkRemoveElementsFromDecksAndCollections} as thin delegates.
 * {@code collectionContainsCard} stays on {@link MenuActionHandler} because
 * it's also used by {@code findCardElementsForCards}.
 * </p>
 */
final class CardBulkRemoveFromDecksHandler {

    private CardBulkRemoveFromDecksHandler() {
    }

    /**
     * Removes all cards matching any card in {@code cardsToRemove} from every list
     * in the {@link DecksAndCollectionsList} (main, extra, side decks and collection
     * card lists). Marks all dirty owners and triggers a Decks &amp; Collections view
     * refresh.
     *
     * @param cardsToRemove the set of cards whose copies should be removed
     */
    public static void handleBulkRemoveFromDecksAndCollections(
            java.util.Collection<Card> cardsToRemove) {
        if (cardsToRemove == null || cardsToRemove.isEmpty()) {
            return;
        }
        DecksAndCollectionsList decksAndCollections = UserInterfaceFunctions.getDecksList();
        if (decksAndCollections == null) {
            return;
        }

        java.util.function.Predicate<CardElement> matchesPredicate = cardElement ->
                cardElement != null
                        && cardElement.getCard() != null
                        && MenuActionHandler.collectionContainsCard(cardsToRemove, cardElement.getCard());

        java.util.Set<Object> dirtyOwners = new java.util.LinkedHashSet<>();

        if (decksAndCollections.getCollections() != null) {
            for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
                if (themeCollection == null) {
                    continue;
                }
                List<CardElement> removedFromCards =
                        removeMatchingAndCollect(themeCollection.getCardsList(), matchesPredicate);
                List<CardElement> removedFromExceptions =
                        removeMatchingAndCollect(themeCollection.getExceptionsToNotAdd(), matchesPredicate);
                if (!removedFromCards.isEmpty() || !removedFromExceptions.isEmpty()) {
                    dirtyOwners.add(themeCollection);
                    for (CardElement removed : removedFromCards) {
                        OuicheList.onDeckCardRemoved(removed, null, null, themeCollection.getName());
                    }
                }
                if (themeCollection.getLinkedDecks() != null) {
                    for (List<Deck> unit : themeCollection.getLinkedDecks()) {
                        if (unit == null) {
                            continue;
                        }
                        for (Deck deck : unit) {
                            if (deck == null) {
                                continue;
                            }
                            List<CardElement> removedMain =
                                    removeMatchingAndCollect(deck.getMainDeck(), matchesPredicate);
                            List<CardElement> removedExtra =
                                    removeMatchingAndCollect(deck.getExtraDeck(), matchesPredicate);
                            List<CardElement> removedSide =
                                    removeMatchingAndCollect(deck.getSideDeck(), matchesPredicate);
                            if (!removedMain.isEmpty() || !removedExtra.isEmpty() || !removedSide.isEmpty()) {
                                dirtyOwners.add(deck);
                                for (CardElement removed : removedMain) {
                                    OuicheList.onDeckCardRemoved(removed, deck.getName(), "main",
                                            themeCollection.getName());
                                }
                                for (CardElement removed : removedExtra) {
                                    OuicheList.onDeckCardRemoved(removed, deck.getName(), "extra",
                                            themeCollection.getName());
                                }
                                for (CardElement removed : removedSide) {
                                    OuicheList.onDeckCardRemoved(removed, deck.getName(), "side",
                                            themeCollection.getName());
                                }
                            }
                        }
                    }
                }
            }
        }
        if (decksAndCollections.getDecks() != null) {
            for (Deck deck : decksAndCollections.getDecks()) {
                if (deck == null) {
                    continue;
                }
                List<CardElement> removedMain =
                        removeMatchingAndCollect(deck.getMainDeck(), matchesPredicate);
                List<CardElement> removedExtra =
                        removeMatchingAndCollect(deck.getExtraDeck(), matchesPredicate);
                List<CardElement> removedSide =
                        removeMatchingAndCollect(deck.getSideDeck(), matchesPredicate);
                if (!removedMain.isEmpty() || !removedExtra.isEmpty() || !removedSide.isEmpty()) {
                    dirtyOwners.add(deck);
                    for (CardElement removed : removedMain) {
                        OuicheList.onDeckCardRemoved(removed, deck.getName(), "main", null);
                    }
                    for (CardElement removed : removedExtra) {
                        OuicheList.onDeckCardRemoved(removed, deck.getName(), "extra", null);
                    }
                    for (CardElement removed : removedSide) {
                        OuicheList.onDeckCardRemoved(removed, deck.getName(), "side", null);
                    }
                }
            }
        }

        for (Object owner : dirtyOwners) {
            UserInterfaceFunctions.markDirty(owner);
        }
        if (!dirtyOwners.isEmpty()) {
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
            UserInterfaceFunctions.refreshOuicheListView();
        }
    }

    /**
     * Removes exactly the given {@link CardElement} instances (by object identity)
     * from every list in the {@link DecksAndCollectionsList}. Unlike
     * {@link #handleBulkRemoveFromDecksAndCollections}, this never touches other
     * elements that share the same {@link Card} / passCode.
     *
     * @param elementsToRemove the specific element instances to remove
     */
    public static void handleBulkRemoveElementsFromDecksAndCollections(
            java.util.Collection<CardElement> elementsToRemove) {
        if (elementsToRemove == null || elementsToRemove.isEmpty()) {
            return;
        }
        DecksAndCollectionsList decksAndCollections = UserInterfaceFunctions.getDecksList();
        if (decksAndCollections == null) {
            return;
        }

        java.util.Set<CardElement> identitySet =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        identitySet.addAll(elementsToRemove);

        java.util.function.Predicate<CardElement> matchesPredicate =
                cardElement -> cardElement != null && identitySet.contains(cardElement);

        java.util.Set<Object> dirtyOwners = new java.util.LinkedHashSet<>();

        if (decksAndCollections.getCollections() != null) {
            for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
                if (themeCollection == null) {
                    continue;
                }
                List<CardElement> removedFromCards =
                        removeMatchingAndCollect(themeCollection.getCardsList(), matchesPredicate);
                List<CardElement> removedFromExceptions =
                        removeMatchingAndCollect(themeCollection.getExceptionsToNotAdd(), matchesPredicate);
                if (!removedFromCards.isEmpty() || !removedFromExceptions.isEmpty()) {
                    dirtyOwners.add(themeCollection);
                    for (CardElement removed : removedFromCards) {
                        OuicheList.onDeckCardRemoved(removed, null, null, themeCollection.getName());
                    }
                }
                if (themeCollection.getLinkedDecks() != null) {
                    for (List<Deck> unit : themeCollection.getLinkedDecks()) {
                        if (unit == null) {
                            continue;
                        }
                        for (Deck deck : unit) {
                            if (deck == null) {
                                continue;
                            }
                            List<CardElement> removedMain =
                                    removeMatchingAndCollect(deck.getMainDeck(), matchesPredicate);
                            List<CardElement> removedExtra =
                                    removeMatchingAndCollect(deck.getExtraDeck(), matchesPredicate);
                            List<CardElement> removedSide =
                                    removeMatchingAndCollect(deck.getSideDeck(), matchesPredicate);
                            if (!removedMain.isEmpty() || !removedExtra.isEmpty() || !removedSide.isEmpty()) {
                                dirtyOwners.add(deck);
                                for (CardElement removed : removedMain) {
                                    OuicheList.onDeckCardRemoved(removed, deck.getName(), "main",
                                            themeCollection.getName());
                                }
                                for (CardElement removed : removedExtra) {
                                    OuicheList.onDeckCardRemoved(removed, deck.getName(), "extra",
                                            themeCollection.getName());
                                }
                                for (CardElement removed : removedSide) {
                                    OuicheList.onDeckCardRemoved(removed, deck.getName(), "side",
                                            themeCollection.getName());
                                }
                            }
                        }
                    }
                }
            }
        }
        if (decksAndCollections.getDecks() != null) {
            for (Deck deck : decksAndCollections.getDecks()) {
                if (deck == null) {
                    continue;
                }
                List<CardElement> removedMain =
                        removeMatchingAndCollect(deck.getMainDeck(), matchesPredicate);
                List<CardElement> removedExtra =
                        removeMatchingAndCollect(deck.getExtraDeck(), matchesPredicate);
                List<CardElement> removedSide =
                        removeMatchingAndCollect(deck.getSideDeck(), matchesPredicate);
                if (!removedMain.isEmpty() || !removedExtra.isEmpty() || !removedSide.isEmpty()) {
                    dirtyOwners.add(deck);
                    for (CardElement removed : removedMain) {
                        OuicheList.onDeckCardRemoved(removed, deck.getName(), "main", null);
                    }
                    for (CardElement removed : removedExtra) {
                        OuicheList.onDeckCardRemoved(removed, deck.getName(), "extra", null);
                    }
                    for (CardElement removed : removedSide) {
                        OuicheList.onDeckCardRemoved(removed, deck.getName(), "side", null);
                    }
                }
            }
        }

        for (Object owner : dirtyOwners) {
            UserInterfaceFunctions.markDirty(owner);
        }
        if (!dirtyOwners.isEmpty()) {
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
            UserInterfaceFunctions.refreshOuicheListView();
        }
    }

    /**
     * Removes all elements from {@code list} that satisfy {@code predicate},
     * returning the removed elements. Returns an empty list if {@code list} is
     * {@code null} or nothing matched.
     */
    private static List<CardElement> removeMatchingAndCollect(
            List<CardElement> list, java.util.function.Predicate<CardElement> predicate) {
        List<CardElement> removed = new ArrayList<>();
        if (list == null) {
            return removed;
        }
        java.util.Iterator<CardElement> iterator = list.iterator();
        while (iterator.hasNext()) {
            CardElement element = iterator.next();
            if (predicate.test(element)) {
                removed.add(element);
                iterator.remove();
            }
        }
        return removed;
    }
}