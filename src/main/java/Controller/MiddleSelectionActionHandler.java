package Controller;

import Model.CardsLists.*;
import View.NavigationItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Holds the pure finder/locator logic used by the middle-pane keyboard shortcuts
 * (delete, duplicate, complete-to-three, paste, numpad-add).
 * <p>
 * Extracted from {@link RealMainController}, which still owns the actual keyboard
 * dispatch and the orchestration methods (gathering the active tab/tree, calling
 * into these finders, then triggering the right refresh/dirty-tracking calls).
 * Every method here takes its context as explicit parameters rather than reading
 * coordinator instance state, which is what makes the move safe.
 * </p>
 */
final class MiddleSelectionActionHandler {

    private MiddleSelectionActionHandler() {
    }

    /**
     * Returns the {@link List} that directly contains {@code element},
     * depending on the active tab:
     * <ul>
     *   <li>Tab 0 (My Collection): the {@link CardsGroup} cardList that holds the element.</li>
     *   <li>Tab 1 (Decks and Collections): the specific deck-section list (main/extra/side)
     *       that holds the element, or the ThemeCollection cardsList.</li>
     * </ul>
     * Returns {@code null} when no container is found.
     */
    static List<CardElement> findDirectContainer(CardElement element, int activeTabIndex) {
        if (element == null) {
            return null;
        }
        if (activeTabIndex == 0) {
            // My Collection: search CardsGroups inside every Box.
            OwnedCardsCollection ownedCollection = Model.CardsLists.OuicheList.getMyCardsCollection();
            if (ownedCollection == null) {
                return null;
            }
            for (Box box : ownedCollection.getOwnedCollection()) {
                List<CardsGroup> groups = box.getContent();
                if (groups == null) {
                    continue;
                }
                for (CardsGroup group : groups) {
                    List<CardElement> cardList = group.getCardList();
                    if (cardList != null && cardList.contains(element)) {
                        return cardList;
                    }
                }
            }
        } else if (activeTabIndex == 1) {
            DecksAndCollectionsList decksList = UserInterfaceFunctions.getDecksList();
            if (decksList == null) {
                return null;
            }
            // Search deck sections of standalone decks.
            for (Deck deck : decksList.getDecks()) {
                if (deck == null) {
                    continue;
                }
                if (deck.getMainDeck() != null && deck.getMainDeck().contains(element)) {
                    return deck.getMainDeck();
                }
                if (deck.getExtraDeck() != null && deck.getExtraDeck().contains(element)) {
                    return deck.getExtraDeck();
                }
                if (deck.getSideDeck() != null && deck.getSideDeck().contains(element)) {
                    return deck.getSideDeck();
                }
            }
            // Search ThemeCollection cardsLists and linked deck sections.
            for (ThemeCollection collection : decksList.getCollections()) {
                if (collection == null) {
                    continue;
                }
                List<CardElement> cardsList = collection.getCardsList();
                if (cardsList != null && cardsList.contains(element)) {
                    return cardsList;
                }
                if (collection.getLinkedDecks() == null) {
                    continue;
                }
                for (List<Deck> unit : collection.getLinkedDecks()) {
                    if (unit == null) {
                        continue;
                    }
                    for (Deck deck : unit) {
                        if (deck == null) {
                            continue;
                        }
                        if (deck.getMainDeck() != null && deck.getMainDeck().contains(element)) {
                            return deck.getMainDeck();
                        }
                        if (deck.getExtraDeck() != null && deck.getExtraDeck().contains(element)) {
                            return deck.getExtraDeck();
                        }
                        if (deck.getSideDeck() != null && deck.getSideDeck().contains(element)) {
                            return deck.getSideDeck();
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds the {@link Deck} or {@link ThemeCollection} that owns {@code element}
     * in the live decksList, searching collection card lists, their linked decks,
     * and standalone decks.
     *
     * @return the owning {@code Deck} or {@code ThemeCollection}, or {@code null}
     * if not found
     */
    static Object findDeckOrCollectionOwner(CardElement element) {
        if (element == null) {
            return null;
        }
        DecksAndCollectionsList decksList = UserInterfaceFunctions.getDecksList();
        if (decksList == null) {
            return null;
        }
        if (decksList.getCollections() != null) {
            for (ThemeCollection collection : decksList.getCollections()) {
                if (collection.getCardsList() != null
                        && collection.getCardsList().contains(element)) {
                    return collection;
                }
                if (collection.getLinkedDecks() != null) {
                    for (List<Deck> unit : collection.getLinkedDecks()) {
                        if (unit == null) {
                            continue;
                        }
                        for (Deck deck : unit) {
                            if (containsElementInDeck(deck, element)) {
                                return deck;
                            }
                        }
                    }
                }
            }
        }
        if (decksList.getDecks() != null) {
            for (Deck deck : decksList.getDecks()) {
                if (containsElementInDeck(deck, element)) {
                    return deck;
                }
            }
        }
        return null;
    }

    static boolean containsElementInDeck(Deck deck, CardElement element) {
        if (deck == null) {
            return false;
        }
        if (deck.getMainDeck() != null && deck.getMainDeck().contains(element)) {
            return true;
        }
        if (deck.getExtraDeck() != null && deck.getExtraDeck().contains(element)) {
            return true;
        }
        if (deck.getSideDeck() != null && deck.getSideDeck().contains(element)) {
            return true;
        }
        return false;
    }

    /**
     * Returns the {@link CardElement} list backing the given navigation-item model
     * object, used as the paste/insert target for that item.
     *
     * @param modelObj a {@link Box}, {@link CardsGroup}, {@link Deck}, or
     *                 {@link ThemeCollection} navigation-item model object
     * @return the backing list, or an empty list if {@code modelObj} doesn't map
     * to one (e.g. an empty {@link Box})
     */
    static List<CardElement> getTargetGroupElements(Object modelObj) {
        if (modelObj instanceof CardsGroup) {
            List<CardElement> list = ((CardsGroup) modelObj).getCardList();
            return list != null ? list : new java.util.ArrayList<>();
        }
        if (modelObj instanceof Box box) {
            if (box.getContent() != null && !box.getContent().isEmpty()) {
                return box.getContent().get(box.getContent().size() - 1).getCardList();
            }
        }
        if (modelObj instanceof Deck deck) {
            if (deck.getMainDeck() != null) {
                return deck.getMainDeck();
            }
        }
        if (modelObj instanceof ThemeCollection collection) {
            List<CardElement> cardsList = collection.getCardsList();
            return cardsList != null ? cardsList : new java.util.ArrayList<>();
        }
        return new java.util.ArrayList<>();
    }

    /**
     * Returns the name of the {@link ThemeCollection} that owns {@code deck} in
     * the live decksList, or {@code null} for standalone decks.
     */
    static String findCollectionNameForDeck(Deck deck) {
        DecksAndCollectionsList decksList = UserInterfaceFunctions.getDecksList();
        if (deck == null || decksList == null || decksList.getCollections() == null) {
            return null;
        }
        for (ThemeCollection collection : decksList.getCollections()) {
            if (collection == null || collection.getLinkedDecks() == null) {
                continue;
            }
            for (List<Deck> unit : collection.getLinkedDecks()) {
                if (unit == null) {
                    continue;
                }
                for (Deck linkedDeck : unit) {
                    if (linkedDeck == deck) {
                        return collection.getName();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Walks {@code treeView} breadth-first and returns the model object (a
     * {@link Box}, {@link CardsGroup}, {@link Deck}, or {@link ThemeCollection})
     * of the last navigation item whose backing list is non-empty.
     *
     * @param treeView the active middle TreeView
     * @return the last such model object, or {@code null} if none is found
     */
    static Object findLastNavModelInTree(TreeView<String> treeView) {
        if (treeView == null || treeView.getRoot() == null) {
            return null;
        }
        Object candidate = null;
        Queue<TreeItem<String>> queue = new LinkedList<>();
        queue.add(treeView.getRoot());
        while (!queue.isEmpty()) {
            TreeItem<String> item = queue.poll();
            if (item.getGraphic() instanceof NavigationItem navItem) {
                Object modelObj = navItem.getUserData();
                if (modelObj != null && !getTargetGroupElements(modelObj).isEmpty()) {
                    candidate = modelObj;
                }
            }
            queue.addAll(item.getChildren());
        }
        return candidate;
    }
}