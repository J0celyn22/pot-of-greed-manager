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

        boolean ouicheListGenerated = OuicheList.isGenerated();
        java.util.Set<Object> dirtyOwners = new java.util.LinkedHashSet<>();

        if (decksAndCollections.getCollections() != null) {
            for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
                if (themeCollection == null) {
                    continue;
                }
                List<CardElement> removedFromCards =
                        removeMatchingAndCollect(
                                CardGroupRegistry.getOrCreateCollectionCardsGroup(
                                        themeCollection, themeCollection.getCardsList()),
                                matchesPredicate);
                List<CardElement> removedFromExceptions =
                        removeMatchingAndCollect(
                                CardGroupRegistry.getOrCreateCollectionExceptionsGroup(
                                        themeCollection, themeCollection.getExceptionsToNotAdd()),
                                matchesPredicate);
                if (!removedFromCards.isEmpty() || !removedFromExceptions.isEmpty()) {
                    dirtyOwners.add(themeCollection);
                    if (ouicheListGenerated) {
                        for (CardElement removed : removedFromCards) {
                            OuicheList.onDeckCardRemoved(removed, null, null, themeCollection.getName(), -1);
                        }
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
                                    removeMatchingAndCollect(
                                            CardGroupRegistry.getOrCreateDeckSectionGroup(
                                                    deck, "main", "Main Deck", deck.getMainDeck()),
                                            matchesPredicate);
                            List<CardElement> removedExtra =
                                    removeMatchingAndCollect(
                                            CardGroupRegistry.getOrCreateDeckSectionGroup(
                                                    deck, "extra", "Extra Deck", deck.getExtraDeck()),
                                            matchesPredicate);
                            List<CardElement> removedSide =
                                    removeMatchingAndCollect(
                                            CardGroupRegistry.getOrCreateDeckSectionGroup(
                                                    deck, "side", "Side Deck", deck.getSideDeck()),
                                            matchesPredicate);
                            if (!removedMain.isEmpty() || !removedExtra.isEmpty() || !removedSide.isEmpty()) {
                                dirtyOwners.add(deck);
                                if (ouicheListGenerated) {
                                    for (CardElement removed : removedMain) {
                                        OuicheList.onDeckCardRemoved(removed, deck.getName(), "main",
                                                themeCollection.getName(), -1);
                                    }
                                    for (CardElement removed : removedExtra) {
                                        OuicheList.onDeckCardRemoved(removed, deck.getName(), "extra",
                                                themeCollection.getName(), -1);
                                    }
                                    for (CardElement removed : removedSide) {
                                        OuicheList.onDeckCardRemoved(removed, deck.getName(), "side",
                                                themeCollection.getName(), -1);
                                    }
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
                        removeMatchingAndCollect(
                                CardGroupRegistry.getOrCreateDeckSectionGroup(
                                        deck, "main", "Main Deck", deck.getMainDeck()),
                                matchesPredicate);
                List<CardElement> removedExtra =
                        removeMatchingAndCollect(
                                CardGroupRegistry.getOrCreateDeckSectionGroup(
                                        deck, "extra", "Extra Deck", deck.getExtraDeck()),
                                matchesPredicate);
                List<CardElement> removedSide =
                        removeMatchingAndCollect(
                                CardGroupRegistry.getOrCreateDeckSectionGroup(
                                        deck, "side", "Side Deck", deck.getSideDeck()),
                                matchesPredicate);
                if (!removedMain.isEmpty() || !removedExtra.isEmpty() || !removedSide.isEmpty()) {
                    dirtyOwners.add(deck);
                    if (ouicheListGenerated) {
                        for (CardElement removed : removedMain) {
                            OuicheList.onDeckCardRemoved(removed, deck.getName(), "main", null, -1);
                        }
                        for (CardElement removed : removedExtra) {
                            OuicheList.onDeckCardRemoved(removed, deck.getName(), "extra", null, -1);
                        }
                        for (CardElement removed : removedSide) {
                            OuicheList.onDeckCardRemoved(removed, deck.getName(), "side", null, -1);
                        }
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
            if (ouicheListGenerated) {
                UserInterfaceFunctions.refreshOuicheListView();
            }
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

        boolean ouicheListGenerated = OuicheList.isGenerated();
        java.util.Set<Object> dirtyOwners = new java.util.LinkedHashSet<>();

        if (decksAndCollections.getCollections() != null) {
            for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
                if (themeCollection == null) {
                    continue;
                }
                List<CardElement> removedFromCards =
                        removeMatchingAndCollect(
                                CardGroupRegistry.getOrCreateCollectionCardsGroup(
                                        themeCollection, themeCollection.getCardsList()),
                                matchesPredicate);
                List<CardElement> removedFromExceptions =
                        removeMatchingAndCollect(
                                CardGroupRegistry.getOrCreateCollectionExceptionsGroup(
                                        themeCollection, themeCollection.getExceptionsToNotAdd()),
                                matchesPredicate);
                if (!removedFromCards.isEmpty() || !removedFromExceptions.isEmpty()) {
                    dirtyOwners.add(themeCollection);
                    if (ouicheListGenerated) {
                        for (CardElement removed : removedFromCards) {
                            OuicheList.onDeckCardRemoved(removed, null, null, themeCollection.getName(), -1);
                        }
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
                                    removeMatchingAndCollect(
                                            CardGroupRegistry.getOrCreateDeckSectionGroup(
                                                    deck, "main", "Main Deck", deck.getMainDeck()),
                                            matchesPredicate);
                            List<CardElement> removedExtra =
                                    removeMatchingAndCollect(
                                            CardGroupRegistry.getOrCreateDeckSectionGroup(
                                                    deck, "extra", "Extra Deck", deck.getExtraDeck()),
                                            matchesPredicate);
                            List<CardElement> removedSide =
                                    removeMatchingAndCollect(
                                            CardGroupRegistry.getOrCreateDeckSectionGroup(
                                                    deck, "side", "Side Deck", deck.getSideDeck()),
                                            matchesPredicate);
                            if (!removedMain.isEmpty() || !removedExtra.isEmpty() || !removedSide.isEmpty()) {
                                dirtyOwners.add(deck);
                                if (ouicheListGenerated) {
                                    for (CardElement removed : removedMain) {
                                        OuicheList.onDeckCardRemoved(removed, deck.getName(), "main",
                                                themeCollection.getName(), -1);
                                    }
                                    for (CardElement removed : removedExtra) {
                                        OuicheList.onDeckCardRemoved(removed, deck.getName(), "extra",
                                                themeCollection.getName(), -1);
                                    }
                                    for (CardElement removed : removedSide) {
                                        OuicheList.onDeckCardRemoved(removed, deck.getName(), "side",
                                                themeCollection.getName(), -1);
                                    }
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
                        removeMatchingAndCollect(
                                CardGroupRegistry.getOrCreateDeckSectionGroup(
                                        deck, "main", "Main Deck", deck.getMainDeck()),
                                matchesPredicate);
                List<CardElement> removedExtra =
                        removeMatchingAndCollect(
                                CardGroupRegistry.getOrCreateDeckSectionGroup(
                                        deck, "extra", "Extra Deck", deck.getExtraDeck()),
                                matchesPredicate);
                List<CardElement> removedSide =
                        removeMatchingAndCollect(
                                CardGroupRegistry.getOrCreateDeckSectionGroup(
                                        deck, "side", "Side Deck", deck.getSideDeck()),
                                matchesPredicate);
                if (!removedMain.isEmpty() || !removedExtra.isEmpty() || !removedSide.isEmpty()) {
                    dirtyOwners.add(deck);
                    if (ouicheListGenerated) {
                        for (CardElement removed : removedMain) {
                            OuicheList.onDeckCardRemoved(removed, deck.getName(), "main", null, -1);
                        }
                        for (CardElement removed : removedExtra) {
                            OuicheList.onDeckCardRemoved(removed, deck.getName(), "extra", null, -1);
                        }
                        for (CardElement removed : removedSide) {
                            OuicheList.onDeckCardRemoved(removed, deck.getName(), "side", null, -1);
                        }
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
            if (ouicheListGenerated) {
                UserInterfaceFunctions.refreshOuicheListView();
            }
        }
    }

    /**
     * Removes all elements from {@code group}'s live card list that satisfy {@code predicate},
     * returning the removed elements. Returns an empty list if {@code group} is {@code null} or
     * nothing matched.
     *
     * <p>Mutates through {@link CardGroupRegistry#observableListFor(CardsGroup)} rather than the
     * raw backing list directly (e.g. {@code deck.getMainDeck()}), so this detaches/reattaches
     * the group's live GridView around the removal and correctly notifies its FilteredList —
     * see {@link CardGroupObservableList}'s class Javadoc. Removing through the raw list instead
     * changes its size with no notification at all, which is what used to make a card removed
     * here eventually crash ControlsFX's {@code GridCell} on a later, unrelated layout pass.
     */
    private static List<CardElement> removeMatchingAndCollect(
            CardsGroup group, java.util.function.Predicate<CardElement> predicate) {
        List<CardElement> removed = new ArrayList<>();
        if (group == null) {
            return removed;
        }
        List<CardElement> list = CardGroupRegistry.observableListFor(group);
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