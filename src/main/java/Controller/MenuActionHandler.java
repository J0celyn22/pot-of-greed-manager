package Controller; // adapt to your package

import Model.CardsLists.*;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static Utils.CardMatcher.cardsMatch;

/**
 * Centralized handler for context-menu actions (Move, Add, Swap, Edit).
 * <p>
 * All public entry points dispatch to private {@code do*()} methods that must
 * run on the JavaFX Application Thread. Each entry point checks the current
 * thread and uses {@link Platform#runLater} when needed. UI refreshes are
 * triggered after every mutating operation via {@link UserInterfaceFunctions}.
 * </p>
 * <p>
 * Normalization of user-facing path strings (box / group / deck names) is
 * performed by a local {@code normalizer} lambda: accent-insensitive,
 * case-insensitive, punctuation- and whitespace-collapsed.
 * </p>
 */
public final class MenuActionHandler {

    private static final Logger logger = LoggerFactory.getLogger(MenuActionHandler.class);

    /**
     * Stores the last target path used by {@link #handleAddCopy} so the UI can scroll to it.
     */
    private static volatile String lastAddedTarget = null;

    /** Stores the last target path used by {@link #handleAddToDeck} so the UI can scroll to it. */
    private static volatile String lastDecksAddedTarget = null;

    private MenuActionHandler() { /* static utility */ }

    // ── Last-target accessors ─────────────────────────────────────────────────

    /**
     * Sets the last deck-related target path (used by the scroll-to-new-card logic
     * after a Decks &amp; Collections view refresh).
     *
     * @param target the canonical handler-target string of the deck operation
     */
    public static void setLastDecksAddedTarget(String target) {
        lastDecksAddedTarget = target;
    }

    /**
     * Returns and atomically clears the last deck-related target path.
     *
     * @return the last target string, or {@code null} if none was recorded
     */
    public static String getAndClearLastDecksAddedTarget() {
        String target = lastDecksAddedTarget;
        lastDecksAddedTarget = null;
        return target;
    }

    /**
     * Returns and atomically clears the last owned-collection target path
     * recorded by {@link #handleAddCopy}.
     *
     * @return the last target string, or {@code null} if none was recorded
     */
    public static String getAndClearLastAddedTarget() {
        String target = lastAddedTarget;
        lastAddedTarget = null;
        return target;
    }

    /**
     * Sets the last owned-collection target path (used by the scroll-to-new-card
     * logic after a My Collection view refresh). Package-private: set by
     * {@link CardCopyHandler} after a successful add-copy.
     *
     * @param target the canonical handler-target string of the add-copy operation
     */
    static void setLastAddedTarget(String target) {
        lastAddedTarget = target;
    }

    // ── Move ──────────────────────────────────────────────────────────────────

    /**
     * Public entry point for "move" menu items. Delegates to {@link CardMoveHandler}.
     *
     * @param clickedElement the {@link CardElement} to move (may be {@code null})
     * @param handlerTarget  canonical target string, e.g. {@code "Box"},
     *                       {@code "Box/Group"}, {@code "Deck/Main Deck"}
     */
    public static void handleMove(CardElement clickedElement, String handlerTarget) {
        CardMoveHandler.handleMove(clickedElement, handlerTarget);
    }

    // ── Add copy to owned collection ────────────────────────────────────────────

    /**
     * Public entry point for "add copy" menu items. Delegates to {@link CardCopyHandler}.
     *
     * @param card          the card from AllExistingCards (not {@code null})
     * @param handlerTarget canonical target string, e.g. {@code "BoxName"},
     *                      {@code "BoxName / CategoryName"},
     *                      {@code "BoxName / SubBoxName"},
     *                      {@code "BoxName / SubBoxName / CategoryName"}
     */
    public static void handleAddCopy(Card card, String handlerTarget) {
        CardCopyHandler.handleAddCopy(card, handlerTarget);
    }

    /**
     * Returns the default (empty-named) {@link CardsGroup} for a {@link Box}, or
     * the first one if no empty-named group exists, or creates and registers a new
     * empty-named group if the box has no groups at all.
     *
     * @param box the box to inspect or modify
     * @return the default group, or {@code null} if creation failed
     */
    public static CardsGroup getOrCreateDefaultGroup(Box box) {
        if (box.getContent() != null) {
            for (CardsGroup cardsGroup : box.getContent()) {
                if (cardsGroup != null) {
                    String groupName = cardsGroup.getName();
                    if (groupName == null || groupName.trim().isEmpty()) {
                        return cardsGroup;
                    }
                }
            }
            if (!box.getContent().isEmpty()) {
                return box.getContent().get(0);
            }
        }
        // Create a new default group
        try {
            CardsGroup newGroup = new CardsGroup("", new ArrayList<>());
            if (box.getContent() == null) {
                box.getClass().getMethod("setContent", List.class)
                        .invoke(box, new ArrayList<CardsGroup>());
            }
            box.getContent().add(newGroup);
            return newGroup;
        } catch (Throwable throwable) {
            logger.debug("getOrCreateDefaultGroup: could not create group", throwable);
        }
        return null;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    static OwnedCardsCollection safeGetOwnedCollection() {
        OwnedCardsCollection owned = null;
        try {
            owned = OuicheList.getMyCardsCollection();
        } catch (Throwable ignored) {
        }
        if (owned == null) {
            try {
                UserInterfaceFunctions.loadCollectionFile();
            } catch (Throwable ignored) {
            }
            try {
                owned = OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
        }
        return owned;
    }

    static SourceLocation findSource(CardElement clickedElement,
                                     OwnedCardsCollection owned) {
        if (owned == null || owned.getOwnedCollection() == null) {
            return null;
        }
        if (clickedElement != null) {
            // Try exact instance match first
            for (Box box : owned.getOwnedCollection()) {
                if (box == null || box.getContent() == null) {
                    continue;
                }
                for (CardsGroup cardsGroup : box.getContent()) {
                    if (cardsGroup == null || cardsGroup.getCardList() == null) {
                        continue;
                    }
                    List<CardElement> list = cardsGroup.getCardList();
                    for (int i = 0; i < list.size(); i++) {
                        CardElement cardElement = list.get(i);
                        if (cardElement == clickedElement) {
                            return new SourceLocation(box, cardsGroup, cardElement, i);
                        }
                    }
                }
            }
            // Fallback: match by card identity
            Card targetCard = clickedElement.getCard();
            if (targetCard != null) {
                for (Box box : owned.getOwnedCollection()) {
                    if (box == null || box.getContent() == null) {
                        continue;
                    }
                    for (CardsGroup cardsGroup : box.getContent()) {
                        if (cardsGroup == null || cardsGroup.getCardList() == null) {
                            continue;
                        }
                        List<CardElement> list = cardsGroup.getCardList();
                        for (int i = 0; i < list.size(); i++) {
                            CardElement cardElement = list.get(i);
                            if (cardElement != null && cardsMatch(cardElement.getCard(), targetCard)) {
                                return new SourceLocation(box, cardsGroup, cardElement, i);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Creates a new {@link CardsGroup} named {@code categoryName} in the last
     * {@link Box} of the owned collection, then moves {@code clickedElement} into
     * it. Both the navigation menu and the middle tree are refreshed afterwards.
     *
     * @param clickedElement the element to move into the new category
     * @param categoryName   the name for the new category (trimmed internally)
     */
    public static void handleAddCategoryAndMove(CardElement clickedElement, String categoryName) {
        if (clickedElement == null || categoryName == null || categoryName.trim().isEmpty()) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                doAddCategoryAndMove(clickedElement, categoryName.trim());
            } else {
                Platform.runLater(() -> doAddCategoryAndMove(clickedElement, categoryName.trim()));
            }
            // Structural refresh: rebuilds both the middle TreeView and the nav menu
            UserInterfaceFunctions.refreshOwnedCollectionStructure();
        } catch (Throwable throwable) {
            logger.debug("handleAddCategoryAndMove failed for category '{}'", categoryName, throwable);
        }
    }

    private static void doAddCategoryAndMove(CardElement clickedElement, String categoryName) {
        OwnedCardsCollection owned = safeGetOwnedCollection();
        if (owned == null
                || owned.getOwnedCollection() == null
                || owned.getOwnedCollection().isEmpty()) {
            logger.warn("doAddCategoryAndMove: OwnedCardsCollection not available or empty");
            return;
        }

        // 1) Find the last top-level Box
        Box lastBox = owned.getOwnedCollection()
                .get(owned.getOwnedCollection().size() - 1);

        // 2) Create the new category and append it to that box
        CardsGroup newGroup = new CardsGroup(categoryName);
        if (lastBox.getContent() == null) {
            lastBox.setContent(new java.util.ArrayList<>());
        }
        lastBox.getContent().add(newGroup);
        logger.debug("doAddCategoryAndMove: created category '{}' in box '{}'",
                categoryName, lastBox.getName());

        // 3) Remove the card element from its current location (if found)
        SourceLocation src = findSource(clickedElement, owned);
        CardElement toAdd = src != null ? src.element : clickedElement;

        if (src != null && src.group != null) {
            // Use the ObservableList so the source GridView shrinks immediately
            CardGroupRegistry.observableListFor(src.group).remove(src.element);
            logger.debug("doAddCategoryAndMove: removed card '{}' from group '{}'",
                    toAdd.getCard() != null ? toAdd.getCard().getName_EN() : "?",
                    src.group.getName());
        } else {
            logger.debug("doAddCategoryAndMove: source location not found for card '{}'; "
                            + "card will be added to new category without removal",
                    clickedElement.getCard() != null ? clickedElement.getCard().getName_EN() : "?");
        }

        // 4) Add the element to the new category via its ObservableList
        CardGroupRegistry.observableListFor(newGroup).add(toAdd);
        logger.debug("doAddCategoryAndMove: card '{}' added to new category '{}'",
                toAdd.getCard() != null ? toAdd.getCard().getName_EN() : "?",
                categoryName);

        UserInterfaceFunctions.markMyCollectionDirty();
        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
    }

    // ── Add to deck / collection cards / exclusion list ────────────────────────

    /**
     * Public entry point for "add to deck" menu items. Delegates to
     * {@link CardAddToListHandler}.
     *
     * @param card          the card from AllExistingCards (not {@code null})
     * @param handlerTarget e.g. {@code "DeckName / Main Deck"} or
     *                      {@code "CollectionName / DeckName / Extra Deck"}
     */
    public static void handleAddToDeck(Card card, String handlerTarget) {
        CardAddToListHandler.handleAddToDeck(card, handlerTarget);
    }

    /**
     * Public entry point for "add to collection cards" menu items. Delegates to
     * {@link CardAddToListHandler}.
     *
     * @param card           the card to add (not {@code null})
     * @param collectionName the name of the target collection
     */
    public static void handleAddToCollectionCards(Card card, String collectionName) {
        CardAddToListHandler.handleAddToCollectionCards(card, collectionName);
    }

    /**
     * Public entry point for "add to exclusion list" menu items. Delegates to
     * {@link CardAddToListHandler}.
     *
     * @param card           the card to exclude (not {@code null})
     * @param collectionName the name of the target collection
     */
    public static void handleAddToExclusionList(Card card, String collectionName) {
        CardAddToListHandler.handleAddToExclusionList(card, collectionName);
    }

    /**
     * Finds all {@link CardElement} instances in the {@link OwnedCardsCollection}
     * whose {@link Card} matches any card in {@code targetCards}
     * (by passCode / printCode / konamiId).
     *
     * @param targetCards the set of cards to match against
     * @return list of matching elements; never {@code null}
     */
    public static List<CardElement> findCardElementsForCards(
            java.util.Collection<Card> targetCards) {
        List<CardElement> result = new ArrayList<>();
        if (targetCards == null || targetCards.isEmpty()) {
            return result;
        }
        OwnedCardsCollection owned = safeGetOwnedCollection();
        if (owned == null || owned.getOwnedCollection() == null) {
            return result;
        }
        for (Box box : owned.getOwnedCollection()) {
            if (box == null) {
                continue;
            }
            collectMatchingElementsFromBox(box, targetCards, result);
        }
        return result;
    }

    private static void collectMatchingElementsFromBox(
            Box box, java.util.Collection<Card> targetCards, List<CardElement> result) {
        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                if (group == null || group.getCardList() == null) {
                    continue;
                }
                for (CardElement cardElement : group.getCardList()) {
                    if (cardElement != null
                            && cardElement.getCard() != null
                            && collectionContainsCard(targetCards, cardElement.getCard())) {
                        result.add(cardElement);
                    }
                }
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box subBox : box.getSubBoxes()) {
                if (subBox != null) {
                    collectMatchingElementsFromBox(subBox, targetCards, result);
                }
            }
        }
    }

    // ── Bulk operations ───────────────────────────────────────────────────────

    /**
     * Returns the {@link Deck} or {@link ThemeCollection} inside the current
     * {@link DecksAndCollectionsList} that owns the list containing {@code element}
     * (matched by object identity). Returns {@code null} if not found.
     *
     * @param element the element to search for
     * @return the owning DAC object, or {@code null}
     */
    public static Object findDacOwnerForCardElement(CardElement element) {
        if (element == null) {
            return null;
        }
        DecksAndCollectionsList decksAndCollections = UserInterfaceFunctions.getDecksList();
        if (decksAndCollections == null) {
            return null;
        }
        if (decksAndCollections.getCollections() != null) {
            for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
                if (themeCollection == null) {
                    continue;
                }
                if (listContainsElementByIdentity(themeCollection.getCardsList(), element)
                        || listContainsElementByIdentity(
                        themeCollection.getExceptionsToNotAdd(), element)) {
                    return themeCollection;
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
                            if (listContainsElementByIdentity(deck.getMainDeck(), element)
                                    || listContainsElementByIdentity(deck.getExtraDeck(), element)
                                    || listContainsElementByIdentity(deck.getSideDeck(), element)) {
                                return deck;
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
                if (listContainsElementByIdentity(deck.getMainDeck(), element)
                        || listContainsElementByIdentity(deck.getExtraDeck(), element)
                        || listContainsElementByIdentity(deck.getSideDeck(), element)) {
                    return deck;
                }
            }
        }
        return null;
    }

    private static boolean listContainsElementByIdentity(List<CardElement> list,
                                                         CardElement target) {
        if (list == null || target == null) {
            return false;
        }
        for (CardElement cardElement : list) {
            if (cardElement == target) {
                return true;
            }
        }
        return false;
    }

    static boolean collectionContainsCard(
            java.util.Collection<Card> cards, Card target) {
        for (Card card : cards) {
            if (cardsMatch(card, target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Moves each element in {@code elements} to the group identified by
     * {@code handlerTarget}. Equivalent to calling {@link #handleMove} for each
     * element, but dispatched as a single batch on the FX thread.
     *
     * @param elements      the elements to move
     * @param handlerTarget canonical target string
     */
    public static void handleBulkMove(List<CardElement> elements, String handlerTarget) {
        if (elements == null || elements.isEmpty() || handlerTarget == null) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                for (CardElement element : elements) {
                    CardMoveHandler.doMove(element, handlerTarget);
                }
            } else {
                final List<CardElement> copy = new ArrayList<>(elements);
                Platform.runLater(() -> {
                    for (CardElement element : copy) {
                        CardMoveHandler.doMove(element, handlerTarget);
                    }
                });
            }
            UserInterfaceFunctions.refreshOwnedCollectionView();
        } catch (Throwable throwable) {
            logger.debug("handleBulkMove failed for target {}", handlerTarget, throwable);
        }
    }

    /**
     * Public entry point for the bulk "remove from owned collection" menu item.
     * Delegates to {@link CardBulkRemoveFromOwnedHandler}.
     *
     * @param elements the elements to remove
     */
    public static void handleBulkRemoveFromOwnedCollection(List<CardElement> elements) {
        CardBulkRemoveFromOwnedHandler.handleBulkRemoveFromOwnedCollection(elements);
    }

    /**
     * Public entry point for the bulk "add copy" menu item. Delegates to
     * {@link CardBulkAddHandler}.
     *
     * @param cards         the cards to copy
     * @param handlerTarget canonical target string
     */
    public static void handleBulkAddCopy(java.util.Collection<Card> cards, String handlerTarget) {
        CardBulkAddHandler.handleBulkAddCopy(cards, handlerTarget);
    }

    /**
     * Public entry point for the bulk "add to deck" menu item. Delegates to
     * {@link CardBulkAddHandler}.
     *
     * @param cards         the cards to add
     * @param handlerTarget canonical target string, e.g. {@code "DeckName / Main Deck"}
     */
    public static void handleBulkAddToDeck(java.util.Collection<Card> cards, String handlerTarget) {
        CardBulkAddHandler.handleBulkAddToDeck(cards, handlerTarget);
    }

    /**
     * Public entry point for the bulk "add to collection cards" menu item.
     * Delegates to {@link CardBulkAddHandler}.
     *
     * @param cards          the cards to add
     * @param collectionName the name of the target collection
     */
    public static void handleBulkAddToCollectionCards(
            java.util.Collection<Card> cards, String collectionName) {
        CardBulkAddHandler.handleBulkAddToCollectionCards(cards, collectionName);
    }

    /**
     * Public entry point for the bulk "add to exclusion list" menu item.
     * Delegates to {@link CardBulkAddHandler}.
     *
     * @param cards          the cards to exclude
     * @param collectionName the name of the target collection
     */
    public static void handleBulkAddToExclusionList(
            java.util.Collection<Card> cards, String collectionName) {
        CardBulkAddHandler.handleBulkAddToExclusionList(cards, collectionName);
    }

    /**
     * Public entry point for "insert card-element snapshots after element"
     * (preferred paste/duplicate overload). Delegates to
     * {@link CardPasteInsertHandler}.
     *
     * @param elementsToInsert the source elements whose copy-constructed snapshots
     *                         will be inserted; not {@code null} or empty
     * @param afterElement     the anchor element after which to insert
     * @return {@code true} if at least one element was inserted
     */
    public static boolean handleInsertElementsAfterElement(
            List<CardElement> elementsToInsert, CardElement afterElement) {
        return CardPasteInsertHandler.handleInsertElementsAfterElement(
                elementsToInsert, afterElement);
    }

    /**
     * Public entry point for "paste element snapshots after element" in the owned
     * collection. Delegates to {@link CardPasteInsertHandler}.
     *
     * @param clipboardElements the element snapshots to paste (not {@code null}
     *                          or empty)
     * @param afterElement      the anchor element; paste position is after this
     *                          element
     */
    public static void handlePasteElementsAfterElementInOwnedCollection(
            List<CardElement> clipboardElements, CardElement afterElement) {
        CardPasteInsertHandler.handlePasteElementsAfterElementInOwnedCollection(
                clipboardElements, afterElement);
    }

    /**
     * Public entry point for "paste cards after element" in the owned collection.
     * Delegates to {@link CardPasteInsertHandler}.
     *
     * @param clipboardCards the cards to paste (not {@code null} or empty)
     * @param afterElement   the anchor element; the paste position is after this element
     * @deprecated Prefer
     *     {@link #handlePasteElementsAfterElementInOwnedCollection(List, CardElement)}
     *     to preserve per-copy fields such as condition and rarity.
     */
    @Deprecated
    public static void handlePasteAfterElementInOwnedCollection(
            List<Card> clipboardCards, CardElement afterElement) {
        CardPasteInsertHandler.handlePasteAfterElementInOwnedCollection(
                clipboardCards, afterElement);
    }

    /**
     * Public entry point for "insert cards after element" (legacy, condition/
     * rarity-losing overload). Delegates to {@link CardPasteInsertHandler}.
     *
     * @param cardsToInsert the cards to insert (not {@code null} or empty)
     * @param afterElement  the anchor element after which to insert
     * @return {@code true} if at least one card was inserted or redirected;
     * {@code false} if the containing group was not found
     * @deprecated Prefer {@link #handleInsertElementsAfterElement(List, CardElement)}
     * to preserve per-copy fields such as condition and rarity.
     */
    @Deprecated
    public static boolean handleInsertCardsAfterElement(
            List<Card> cardsToInsert, CardElement afterElement) {
        return CardPasteInsertHandler.handleInsertCardsAfterElement(cardsToInsert, afterElement);
    }

    /**
     * Public entry point for the bulk "remove matching cards from Decks &amp;
     * Collections" menu item. Delegates to {@link CardBulkRemoveFromDecksHandler}.
     *
     * @param cardsToRemove the set of cards whose copies should be removed
     */
    public static void handleBulkRemoveFromDecksAndCollections(
            java.util.Collection<Card> cardsToRemove) {
        CardBulkRemoveFromDecksHandler.handleBulkRemoveFromDecksAndCollections(cardsToRemove);
    }

    /**
     * Public entry point for the bulk "remove specific elements from Decks &amp;
     * Collections" menu item. Delegates to {@link CardBulkRemoveFromDecksHandler}.
     *
     * @param elementsToRemove the specific element instances to remove
     */
    public static void handleBulkRemoveElementsFromDecksAndCollections(
            java.util.Collection<CardElement> elementsToRemove) {
        CardBulkRemoveFromDecksHandler.handleBulkRemoveElementsFromDecksAndCollections(elementsToRemove);
    }


    /**
     * Returns the {@link Deck} that owns {@code group} as one of its registered
     * section groups (main / extra / side), or {@code null}.
     * Delegates to {@link CardGroupRegistry#findDeckOwnerForGroup}.
     *
     * @param group the group to look up
     * @return the owning deck, or {@code null}
     */
    private static Deck findDeckOwnerForGroup(CardsGroup group) {
        if (group == null) {
            return null;
        }
        return CardGroupRegistry.findDeckOwnerForGroup(group);
    }

    // ── Swap ──────────────────────────────────────────────────────────────────

    /**
     * Public entry point for "swap" menu items (upgrading a deck/collection copy
     * with a better-condition owned copy). Delegates to {@link CardSwapHandler}.
     *
     * @param incoming the owned {@link CardElement} that is the quality upgrade
     * @param outgoing the deck/collection {@link CardElement} to be replaced
     */
    public static void handleSwap(CardElement incoming, CardElement outgoing) {
        CardSwapHandler.handleSwap(incoming, outgoing);
    }

    /**
     * Opens the {@link View.CardEditPopup} for {@code element}, then refreshes all
     * relevant views after the user confirms with OK.
     * <p>
     * Safe to call from any thread; the popup is always shown on the JavaFX
     * Application Thread.
     * </p>
     *
     * @param element the {@link CardElement} to edit (must not be {@code null})
     * @param anchor  any scene node used to centre the popup on the same window;
     *                may be {@code null} (popup will centre on screen)
     */
    public static void handleEditCard(CardElement element, javafx.scene.Node anchor) {
        if (element == null) {
            return;
        }
        Runnable show = () -> {
            try {
                View.CardEditPopup popup = new View.CardEditPopup(element);
                popup.setOnOk(() -> {
                    try {
                        UserInterfaceFunctions.refreshOwnedCollectionView();
                    } catch (Throwable ignored) {
                    }
                    try {
                        UserInterfaceFunctions.refreshDecksAndCollectionsView();
                    } catch (Throwable ignored) {
                    }
                });
                popup.showCenteredOn(anchor);
            } catch (Throwable throwable) {
                logger.error("handleEditCard failed", throwable);
            }
        };
        if (Platform.isFxApplicationThread()) {
            show.run();
        } else {
            Platform.runLater(show);
        }
    }

    /**
     * Public entry point for the owned-to-owned "swap" menu item, used when the
     * user swaps two owned copies with each other (My Collection context, where
     * neither element is a D&amp;C definition entry). Delegates to
     * {@link CardSwapHandler}.
     *
     * @param elementA the first owned element
     * @param elementB the second owned element
     */
    public static void handleSwapOwned(CardElement elementA, CardElement elementB) {
        CardSwapHandler.handleSwapOwned(elementA, elementB);
    }

    static final class SourceLocation {
        final Box box;
        final CardsGroup group;
        final CardElement element;
        final int index;

        SourceLocation(Box box, CardsGroup group, CardElement element, int index) {
            this.box = box;
            this.group = group;
            this.element = element;
            this.index = index;
        }
    }

}