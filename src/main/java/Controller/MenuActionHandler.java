package Controller; // adapt to your package

import Model.CardsLists.*;
import View.CardTreeCell;
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
            CardTreeCell.observableListFor(src.group).remove(src.element);
            logger.debug("doAddCategoryAndMove: removed card '{}' from group '{}'",
                    toAdd.getCard() != null ? toAdd.getCard().getName_EN() : "?",
                    src.group.getName());
        } else {
            logger.debug("doAddCategoryAndMove: source location not found for card '{}'; "
                            + "card will be added to new category without removal",
                    clickedElement.getCard() != null ? clickedElement.getCard().getName_EN() : "?");
        }

        // 4) Add the element to the new category via its ObservableList
        CardTreeCell.observableListFor(newGroup).add(toAdd);
        logger.debug("doAddCategoryAndMove: card '{}' added to new category '{}'",
                toAdd.getCard() != null ? toAdd.getCard().getName_EN() : "?",
                categoryName);

        UserInterfaceFunctions.markMyCollectionDirty();
        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
    }

    // ── Add category and move ─────────────────────────────────────────────────

    /**
     * Adds a copy of the given {@link Card} to the correct deck list
     * (Main Deck / Extra Deck / Side Deck) identified by {@code handlerTarget}.
     *
     * @param card          the card from AllExistingCards (not {@code null})
     * @param handlerTarget e.g. {@code "DeckName / Main Deck"} or
     *                      {@code "CollectionName / DeckName / Extra Deck"}
     */
    public static void handleAddToDeck(Card card, String handlerTarget) {
        if (card == null || handlerTarget == null || handlerTarget.trim().isEmpty()) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                doAddToDeck(card, handlerTarget);
            } else {
                Platform.runLater(() -> doAddToDeck(card, handlerTarget));
            }
            lastDecksAddedTarget = handlerTarget;
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        } catch (Throwable throwable) {
            logger.error("handleAddToDeck failed for target '{}'", handlerTarget, throwable);
        }
    }

    /**
     * Adds a copy of the given {@link Card} to the card list of the named
     * {@link ThemeCollection}.
     *
     * @param card           the card to add (not {@code null})
     * @param collectionName the name of the target collection
     */
    public static void handleAddToCollectionCards(Card card, String collectionName) {
        if (card == null || collectionName == null || collectionName.trim().isEmpty()) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                doAddToCollectionCards(card, collectionName);
            } else {
                Platform.runLater(() -> doAddToCollectionCards(card, collectionName));
            }
            lastDecksAddedTarget = collectionName + " / Cards";
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        } catch (Throwable throwable) {
            logger.error("handleAddToCollectionCards failed for collection '{}'", collectionName, throwable);
        }
    }

    // ── Add to deck ───────────────────────────────────────────────────────────

    private static void doAddToCollectionCards(Card card, String collectionName) {
        DecksAndCollectionsList decksAndCollections = UserInterfaceFunctions.getDecksList();
        if (decksAndCollections == null) {
            try {
                UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
            } catch (Throwable ignored) {
            }
            decksAndCollections = UserInterfaceFunctions.getDecksList();
        }
        if (decksAndCollections == null || decksAndCollections.getCollections() == null) {
            return;
        }

        java.util.function.Function<String, String> normalizer = input -> {
            if (input == null) {
                return "";
            }
            String normalized = input.trim()
                    .replaceAll("[=\\-]", "")
                    .replaceAll("\\s+", " ");
            normalized = java.text.Normalizer
                    .normalize(normalized, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            return normalized.toLowerCase().trim();
        };
        String targetNorm = normalizer.apply(collectionName);

        ThemeCollection foundCollection = null;
        for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
            if (themeCollection == null) {
                continue;
            }
            if (normalizer.apply(themeCollection.getName()).equals(targetNorm)) {
                foundCollection = themeCollection;
                break;
            }
        }
        if (foundCollection == null) {
            logger.info("handleAddToCollectionCards: collection '{}' not found", collectionName);
            return;
        }

        if (foundCollection.getCardsList() == null) {
            foundCollection.setCardsList(new ArrayList<>());
        }
        CardElement newElement = new CardElement(card);
        foundCollection.getCardsList().add(newElement);
        logger.debug("handleAddToCollectionCards: added '{}' to collection '{}'",
                card.getName_EN(), collectionName);

        try {
            OuicheList.onDeckCardAdded(newElement, null, null, foundCollection.getName());
            UserInterfaceFunctions.refreshOuicheListView();
        } catch (Throwable throwable) {
            logger.error("OuicheList update failed after adding '{}' to collection '{}'",
                    card.getName_EN(), collectionName, throwable);
        }

        UserInterfaceFunctions.markDirty(foundCollection);
        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
    }

    // ── Add to collection cards ───────────────────────────────────────────────

    /**
     * Adds a copy of the given {@link Card} to the exclusion list ("cards not to
     * add") of the named {@link ThemeCollection}.
     *
     * @param card           the card to exclude (not {@code null})
     * @param collectionName the name of the target collection
     */
    public static void handleAddToExclusionList(Card card, String collectionName) {
        if (card == null || collectionName == null || collectionName.trim().isEmpty()) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                doAddToExclusionList(card, collectionName);
            } else {
                Platform.runLater(() -> doAddToExclusionList(card, collectionName));
            }
            lastDecksAddedTarget = collectionName + " / Cards not to add";
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        } catch (Throwable throwable) {
            logger.debug("handleAddToExclusionList failed for collection {}",
                    collectionName, throwable);
        }
    }

    private static void doAddToExclusionList(Card card, String collectionName) {
        DecksAndCollectionsList decksAndCollections = UserInterfaceFunctions.getDecksList();
        if (decksAndCollections == null) {
            try {
                UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
            } catch (Throwable ignored) {
            }
            decksAndCollections = UserInterfaceFunctions.getDecksList();
        }
        if (decksAndCollections == null || decksAndCollections.getCollections() == null) {
            return;
        }

        java.util.function.Function<String, String> normalizer = input -> {
            if (input == null) {
                return "";
            }
            String normalized = input.trim()
                    .replaceAll("[=\\-]", "")
                    .replaceAll("\\s+", " ");
            normalized = java.text.Normalizer
                    .normalize(normalized, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            return normalized.toLowerCase().trim();
        };
        String targetNorm = normalizer.apply(collectionName);

        ThemeCollection foundCollection = null;
        for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
            if (themeCollection == null) {
                continue;
            }
            if (normalizer.apply(themeCollection.getName()).equals(targetNorm)) {
                foundCollection = themeCollection;
                break;
            }
        }
        if (foundCollection == null) {
            logger.info("handleAddToExclusionList: collection '{}' not found", collectionName);
            return;
        }

        if (foundCollection.getExceptionsToNotAdd() == null) {
            foundCollection.setExceptionsToNotAdd(new ArrayList<>());
        }
        foundCollection.getExceptionsToNotAdd().add(new CardElement(card));
        logger.debug("handleAddToExclusionList: added '{}' to exclusion list of '{}'",
                card.getName_EN(), collectionName);

        UserInterfaceFunctions.markDirty(foundCollection);
        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
    }

    // ── Add to exclusion list ─────────────────────────────────────────────────

    private static void doAddToDeck(Card card, String handlerTarget) {
        DecksAndCollectionsList decksAndCollections = UserInterfaceFunctions.getDecksList();
        if (decksAndCollections == null) {
            try {
                UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
            } catch (Throwable ignored) {
            }
            decksAndCollections = UserInterfaceFunctions.getDecksList();
        }
        if (decksAndCollections == null) {
            logger.warn("doAddToDeck: DecksAndCollectionsList not available");
            return;
        }

        java.util.function.Function<String, String> normalizer = input -> {
            if (input == null) {
                return "";
            }
            String normalized = input.trim()
                    .replaceAll("[=\\-]", "")
                    .replaceAll("\\s+", " ");
            normalized = java.text.Normalizer
                    .normalize(normalized, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            return normalized.toLowerCase().trim();
        };

        String[] parts = handlerTarget.split("/");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }

        if (parts.length < 2) {
            logger.debug("doAddToDeck: handlerTarget '{}' has fewer than 2 parts", handlerTarget);
            return;
        }

        String lastNorm = normalizer.apply(parts[parts.length - 1]);
        boolean isMain = lastNorm.equals("main deck") || lastNorm.equals("main");
        boolean isExtra = lastNorm.equals("extra deck") || lastNorm.equals("extra");
        boolean isSide = lastNorm.equals("side deck") || lastNorm.equals("side");

        if (!isMain && !isExtra && !isSide) {
            logger.debug("doAddToDeck: last part '{}' is not a recognised deck list", lastNorm);
            return;
        }

        String deckNameNorm = normalizer.apply(parts[parts.length - 2]);
        Deck targetDeck = findDeckInDac(deckNameNorm, normalizer, decksAndCollections);

        if (targetDeck == null) {
            logger.info("doAddToDeck: deck '{}' not found in DecksAndCollectionsList", deckNameNorm);
            return;
        }

        CardElement newElement = new CardElement(card);
        String sectionName;
        if (isMain) {
            targetDeck.getMainDeck().add(newElement);
            sectionName = "main";
        } else if (isExtra) {
            targetDeck.getExtraDeck().add(newElement);
            sectionName = "extra";
        } else {
            targetDeck.getSideDeck().add(newElement);
            sectionName = "side";
        }
        logger.debug("doAddToDeck: added '{}' to '{}'", card.getName_EN(), handlerTarget);

        try {
            String parentCollectionName = findCollectionNameForDeck(targetDeck, decksAndCollections);
            OuicheList.onDeckCardAdded(newElement, targetDeck.getName(), sectionName, parentCollectionName);
            UserInterfaceFunctions.refreshOuicheListView();
        } catch (Throwable throwable) {
            logger.error("OuicheList update failed after adding '{}' to deck '{}'",
                    card.getName_EN(), handlerTarget, throwable);
        }

        UserInterfaceFunctions.markDirty(targetDeck);
        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
    }

    private static Deck findDeckInDac(String deckNameNorm,
                                      java.util.function.Function<String, String> normalizer,
                                      DecksAndCollectionsList decksAndCollections) {
        // Search standalone decks first
        if (decksAndCollections.getDecks() != null) {
            for (Deck deck : decksAndCollections.getDecks()) {
                if (deck != null && normalizer.apply(deck.getName()).equals(deckNameNorm)) {
                    return deck;
                }
            }
        }
        // Search inside collections
        if (decksAndCollections.getCollections() != null) {
            for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
                if (themeCollection == null || themeCollection.getLinkedDecks() == null) {
                    continue;
                }
                for (List<Deck> unit : themeCollection.getLinkedDecks()) {
                    if (unit == null) {
                        continue;
                    }
                    for (Deck deck : unit) {
                        if (deck != null && normalizer.apply(deck.getName()).equals(deckNameNorm)) {
                            return deck;
                        }
                    }
                }
            }
        }
        return null;
    }

    // ── doAddToDeck + findDeckInDac ───────────────────────────────────────────

    /**
     * Returns the name of the {@link ThemeCollection} that owns {@code deck} within
     * {@code decksAndCollections}, or {@code null} if the deck is standalone.
     */
    private static String findCollectionNameForDeck(Deck deck,
                                                    DecksAndCollectionsList decksAndCollections) {
        if (deck == null || decksAndCollections == null
                || decksAndCollections.getCollections() == null) {
            return null;
        }
        for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
            if (themeCollection == null || themeCollection.getLinkedDecks() == null) {
                continue;
            }
            for (List<Deck> unit : themeCollection.getLinkedDecks()) {
                if (unit == null) {
                    continue;
                }
                for (Deck linkedDeck : unit) {
                    if (linkedDeck == deck) {
                        return themeCollection.getName();
                    }
                }
            }
        }
        return null;
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

    private static boolean collectionContainsCard(
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
        OwnedCardsCollection owned = safeGetOwnedCollection();
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
                        CardTreeCell.observableListFor(group);
                if (observableList.remove(targetElement)) {
                    CardTreeCell.triggerHeightAdjustment(group);
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
                    doAddToDeck(card, handlerTarget);
                }
            } else {
                final List<Card> copy = new ArrayList<>(cards);
                Platform.runLater(() -> {
                    for (Card card : copy) {
                        doAddToDeck(card, handlerTarget);
                    }
                });
            }
            lastDecksAddedTarget = handlerTarget;
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
                    doAddToCollectionCards(card, collectionName);
                }
            } else {
                final List<Card> copy = new ArrayList<>(cards);
                Platform.runLater(() -> {
                    for (Card card : copy) {
                        doAddToCollectionCards(card, collectionName);
                    }
                });
            }
            lastDecksAddedTarget = collectionName + " / Cards";
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
                    doAddToExclusionList(card, collectionName);
                }
            } else {
                final List<Card> copy = new ArrayList<>(cards);
                Platform.runLater(() -> {
                    for (Card card : copy) {
                        doAddToExclusionList(card, collectionName);
                    }
                });
            }
            lastDecksAddedTarget = collectionName + " / Cards not to add";
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        } catch (Throwable throwable) {
            logger.debug("handleBulkAddToExclusionList failed for {}", collectionName, throwable);
        }
    }

    /**
     * Inserts copy-constructed snapshots of {@code elementsToInsert} immediately
     * after {@code afterElement} in the group that contains it, preserving
     * {@code condition}, {@code rarity}, {@code customTags}, and all other
     * per-copy fields.
     * <p>
     * This is the preferred overload for copy/paste and duplicate operations
     * originating from the middle pane (owned collection or D&amp;C), where the
     * full {@link CardElement} context is available.
     * </p>
     * <p>
     * Deck-compatibility routing (main ↔ extra section redirect) is applied
     * identically to {@link #handleInsertCardsAfterElement}.
     * </p>
     *
     * @param elementsToInsert the source elements whose copy-constructed snapshots
     *                         will be inserted; not {@code null} or empty
     * @param afterElement     the anchor element after which to insert
     * @return {@code true} if at least one element was inserted
     */
    public static boolean handleInsertElementsAfterElement(
            List<CardElement> elementsToInsert, CardElement afterElement) {
        if (elementsToInsert == null || elementsToInsert.isEmpty()
                || afterElement == null) {
            return false;
        }

        CardsGroup hostGroup = CardTreeCell.findGroupForCardElement(afterElement);
        if (hostGroup == null) {
            OwnedCardsCollection owned = safeGetOwnedCollection();
            if (owned != null) {
                hostGroup = findGroupContainingElement(afterElement, owned);
            }
        }
        if (hostGroup == null) {
            logger.warn("handleInsertElementsAfterElement: could not find group "
                    + "for anchor element");
            return false;
        }

        // ── Deck-compatibility redirect ────────────────────────────────────
        String groupName = hostGroup.getName();
        boolean targetIsMainOrExtra =
                Utils.DeckCompatibility.isMainDeckSection(groupName)
                        || Utils.DeckCompatibility.isExtraDeckSection(groupName);

        if (targetIsMainOrExtra) {
            List<CardElement> compatible = new ArrayList<>();
            List<CardElement> incompatible = new ArrayList<>();
            for (CardElement source : elementsToInsert) {
                if (source == null || source.getCard() == null) {
                    continue;
                }
                if (Utils.DeckCompatibility.isCompatibleWith(
                        source.getCard(), groupName)) {
                    compatible.add(source);
                } else {
                    incompatible.add(source);
                }
            }

            List<CardElement> addedToHost = new ArrayList<>();
            if (!compatible.isEmpty()) {
                javafx.collections.ObservableList<CardElement> list =
                        CardTreeCell.observableListFor(hostGroup);
                int insertionIndex = list.indexOf(afterElement);
                if (insertionIndex < 0) {
                    insertionIndex = list.size() - 1;
                }
                for (int i = 0; i < compatible.size(); i++) {
                    int pos = Math.min(insertionIndex + 1 + i, list.size());
                    CardElement newElement = new CardElement(compatible.get(i));
                    list.add(pos, newElement);
                    addedToHost.add(newElement);
                }
                CardTreeCell.triggerHeightAdjustment(hostGroup);
            }
            CardGroupRegistry.notifyOuicheListOfGroupAdditions(hostGroup, addedToHost);

            List<CardElement> addedToAlt = new ArrayList<>();
            if (!incompatible.isEmpty()) {
                String redirect = Utils.DeckCompatibility.redirectSection(
                        incompatible.get(0).getCard(), groupName);
                if (redirect != null) {
                    Deck ownerDeck = CardTreeCell.findDeckOwnerForGroup(hostGroup);
                    if (ownerDeck != null) {
                        String sectionKey = redirect
                                .toLowerCase(java.util.Locale.ROOT)
                                .replace(" deck", "")
                                .trim();
                        CardsGroup altGroup =
                                CardTreeCell.getDeckSectionGroup(ownerDeck, sectionKey);
                        if (altGroup != null) {
                            javafx.collections.ObservableList<CardElement> altList =
                                    CardTreeCell.observableListFor(altGroup);
                            for (CardElement source : incompatible) {
                                CardElement newElement = new CardElement(source);
                                altList.add(newElement);
                                addedToAlt.add(newElement);
                            }
                            CardTreeCell.triggerHeightAdjustment(altGroup);
                            CardGroupRegistry.notifyOuicheListOfGroupAdditions(altGroup, addedToAlt);
                        }
                    }
                }
            }
            return !addedToHost.isEmpty() || !addedToAlt.isEmpty();
        }
        // ──────────────────────────────────────────────────────────────────

        // Normal (non-deck-section) insertion
        javafx.collections.ObservableList<CardElement> observableList =
                CardTreeCell.observableListFor(hostGroup);
        int insertionIndex = observableList.indexOf(afterElement);
        if (insertionIndex < 0) {
            insertionIndex = observableList.size() - 1;
        }
        List<CardElement> addedToGroup = new ArrayList<>();
        for (int i = 0; i < elementsToInsert.size(); i++) {
            CardElement source = elementsToInsert.get(i);
            if (source == null) {
                continue;
            }
            int targetIndex = insertionIndex + 1 + i;
            if (targetIndex > observableList.size()) {
                targetIndex = observableList.size();
            }
            CardElement newElement = new CardElement(source);
            observableList.add(targetIndex, newElement);
            addedToGroup.add(newElement);
        }
        CardTreeCell.triggerHeightAdjustment(hostGroup);
        CardGroupRegistry.notifyOuicheListOfGroupAdditions(hostGroup, addedToGroup);
        return !addedToGroup.isEmpty();
    }

    /**
     * Pastes copy-constructed snapshots of {@code clipboardElements} immediately
     * after {@code afterElement} in the group that contains it in the
     * {@link OwnedCardsCollection}, preserving {@code condition}, {@code rarity},
     * {@code customTags}, and all other per-copy fields.
     * <p>
     * Prefer this overload over
     * {@link #handlePasteAfterElementInOwnedCollection(List, CardElement)} when
     * a full {@link CardElement} context is available.
     * </p>
     *
     * @param clipboardElements the element snapshots to paste (not {@code null}
     *                          or empty)
     * @param afterElement      the anchor element; paste position is after this
     *                          element
     */
    public static void handlePasteElementsAfterElementInOwnedCollection(
            List<CardElement> clipboardElements, CardElement afterElement) {
        if (clipboardElements == null || clipboardElements.isEmpty()
                || afterElement == null) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                doPasteElementsAfterElementInOwnedCollection(
                        clipboardElements, afterElement);
            } else {
                Platform.runLater(() ->
                        doPasteElementsAfterElementInOwnedCollection(
                                clipboardElements, afterElement));
            }
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionView();
        } catch (Throwable throwable) {
            logger.debug("handlePasteElementsAfterElementInOwnedCollection failed",
                    throwable);
        }
    }

    private static void doPasteElementsAfterElementInOwnedCollection(
            List<CardElement> clipboardElements, CardElement afterElement) {
        OwnedCardsCollection owned = safeGetOwnedCollection();
        if (owned == null || owned.getOwnedCollection() == null) {
            return;
        }
        CardsGroup targetGroup =
                findGroupContainingElement(afterElement, owned);
        if (targetGroup == null) {
            logger.warn("doPasteElementsAfterElementInOwnedCollection: "
                    + "group not found for anchor element");
            return;
        }
        javafx.collections.ObservableList<CardElement> observableList =
                CardTreeCell.observableListFor(targetGroup);
        int insertionIndex = observableList.indexOf(afterElement);
        if (insertionIndex < 0) {
            insertionIndex = observableList.size() - 1;
        }
        for (int i = 0; i < clipboardElements.size(); i++) {
            CardElement source = clipboardElements.get(i);
            if (source == null) {
                continue;
            }
            int targetIndex = insertionIndex + 1 + i;
            if (targetIndex > observableList.size()) {
                targetIndex = observableList.size();
            }
            observableList.add(targetIndex, new CardElement(source));
        }
        CardTreeCell.triggerHeightAdjustment(targetGroup);
    }

    /**
     * Pastes {@code clipboardCards} immediately after {@code afterElement} in the
     * group that contains it in the {@link OwnedCardsCollection}.
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
        if (clipboardCards == null || clipboardCards.isEmpty() || afterElement == null) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                doPasteAfterElementInOwnedCollection(clipboardCards, afterElement);
            } else {
                Platform.runLater(() ->
                        doPasteAfterElementInOwnedCollection(clipboardCards, afterElement));
            }
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionView();
        } catch (Throwable throwable) {
            logger.debug("handlePasteAfterElementInOwnedCollection failed", throwable);
        }
    }

    private static void doPasteAfterElementInOwnedCollection(
            List<Card> clipboardCards, CardElement afterElement) {
        OwnedCardsCollection owned = safeGetOwnedCollection();
        if (owned == null || owned.getOwnedCollection() == null) {
            return;
        }
        CardsGroup targetGroup = findGroupContainingElement(afterElement, owned);
        if (targetGroup == null) {
            logger.warn("doPasteAfterElementInOwnedCollection: group not found for element");
            return;
        }
        javafx.collections.ObservableList<CardElement> observableList =
                CardTreeCell.observableListFor(targetGroup);
        int insertionIndex = observableList.indexOf(afterElement);
        if (insertionIndex < 0) {
            insertionIndex = observableList.size() - 1;
        }
        for (int i = 0; i < clipboardCards.size(); i++) {
            Card card = clipboardCards.get(i);
            if (card == null) {
                continue;
            }
            int targetIndex = insertionIndex + 1 + i;
            if (targetIndex > observableList.size()) {
                targetIndex = observableList.size();
            }
            observableList.add(targetIndex, new CardElement(card));
        }
        CardTreeCell.triggerHeightAdjustment(targetGroup);
    }

    // ── Paste after element ───────────────────────────────────────────────────

    private static CardsGroup findGroupContainingElement(
            CardElement targetElement, OwnedCardsCollection owned) {
        if (targetElement == null || owned == null) {
            return null;
        }
        for (Box box : owned.getOwnedCollection()) {
            CardsGroup found = findGroupContainingElementInBox(targetElement, box);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static CardsGroup findGroupContainingElementInBox(CardElement targetElement, Box box) {
        if (box == null) {
            return null;
        }
        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                if (group != null
                        && group.getCardList() != null
                        && group.getCardList().contains(targetElement)) {
                    return group;
                }
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box subBox : box.getSubBoxes()) {
                CardsGroup found = findGroupContainingElementInBox(targetElement, subBox);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
                        && collectionContainsCard(cardsToRemove, cardElement.getCard());

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

    // ── Bulk remove from Decks & Collections ──────────────────────────────────

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

    /**
     * Inserts fresh {@link CardElement} instances (one per card) immediately
     * after {@code afterElement} in the group that contains it.
     * <p>
     * When the host group is a main- or extra-deck section, cards are split by
     * deck compatibility: compatible cards are inserted at the requested position
     * while incompatible cards are appended to the appropriate sibling section of
     * the same {@link Deck}.
     * </p>
     * <p>
     * The new elements are created with {@code new CardElement(card)}, so
     * no condition, rarity, or custom-tag metadata is preserved.
     * </p>
     *
     * @param cardsToInsert the cards to insert (not {@code null} or empty)
     * @param afterElement  the anchor element after which to insert
     * @return {@code true} if at least one card was inserted or redirected;
     *         {@code false} if the containing group was not found
     * @deprecated Prefer {@link #handleInsertElementsAfterElement(List, CardElement)}
     *     to preserve per-copy fields such as condition and rarity.
     */
    @Deprecated
    public static boolean handleInsertCardsAfterElement(
            List<Card> cardsToInsert, CardElement afterElement) {
        if (cardsToInsert == null || cardsToInsert.isEmpty() || afterElement == null) {
            return false;
        }

        CardsGroup hostGroup = CardTreeCell.findGroupForCardElement(afterElement);
        if (hostGroup == null) {
            OwnedCardsCollection owned = safeGetOwnedCollection();
            if (owned != null) {
                hostGroup = findGroupContainingElement(afterElement, owned);
            }
        }
        if (hostGroup == null) {
            logger.warn("handleInsertCardsAfterElement: could not find group for element");
            return false;
        }

        // ── Deck-compatibility redirect ────────────────────────────────────
        // When the host group is a main/extra deck section, split cards into
        // compatible (inserted after afterElement) and incompatible (appended
        // to the sibling section of the same Deck).
        String groupName = hostGroup.getName();
        boolean targetIsMainOrExtra =
                Utils.DeckCompatibility.isMainDeckSection(groupName)
                        || Utils.DeckCompatibility.isExtraDeckSection(groupName);

        if (targetIsMainOrExtra) {
            List<Card> compatible = new ArrayList<>();
            List<Card> incompatible = new ArrayList<>();
            for (Card card : cardsToInsert) {
                if (card == null) {
                    continue;
                }
                if (Utils.DeckCompatibility.isCompatibleWith(card, groupName)) {
                    compatible.add(card);
                } else {
                    incompatible.add(card);
                }
            }

            List<CardElement> addedToHost = new ArrayList<>();
            if (!compatible.isEmpty()) {
                javafx.collections.ObservableList<CardElement> list =
                        CardTreeCell.observableListFor(hostGroup);
                int insertionIndex = list.indexOf(afterElement);
                if (insertionIndex < 0) {
                    insertionIndex = list.size() - 1;
                }
                for (int i = 0; i < compatible.size(); i++) {
                    int pos = Math.min(insertionIndex + 1 + i, list.size());
                    CardElement newElement = new CardElement(compatible.get(i));
                    list.add(pos, newElement);
                    addedToHost.add(newElement);
                }
                CardTreeCell.triggerHeightAdjustment(hostGroup);
            }
            CardGroupRegistry.notifyOuicheListOfGroupAdditions(hostGroup, addedToHost);

            List<CardElement> addedToAlt = new ArrayList<>();
            if (!incompatible.isEmpty()) {
                String redirect = Utils.DeckCompatibility.redirectSection(
                        incompatible.get(0), groupName);
                if (redirect != null) {
                    Deck ownerDeck = CardTreeCell.findDeckOwnerForGroup(hostGroup);
                    if (ownerDeck != null) {
                        String sectionKey = redirect.toLowerCase(java.util.Locale.ROOT)
                                .replace(" deck", "").trim();
                        CardsGroup altGroup =
                                CardTreeCell.getDeckSectionGroup(ownerDeck, sectionKey);
                        if (altGroup != null) {
                            javafx.collections.ObservableList<CardElement> altList =
                                    CardTreeCell.observableListFor(altGroup);
                            for (Card card : incompatible) {
                                CardElement newElement = new CardElement(card);
                                altList.add(newElement);
                                addedToAlt.add(newElement);
                            }
                            CardTreeCell.triggerHeightAdjustment(altGroup);
                            CardGroupRegistry.notifyOuicheListOfGroupAdditions(altGroup, addedToAlt);
                        }
                    }
                }
            }
            return !addedToHost.isEmpty() || !addedToAlt.isEmpty();
        }
        // ──────────────────────────────────────────────────────────────────

        // Normal (non-deck-section) insertion
        javafx.collections.ObservableList<CardElement> observableList =
                CardTreeCell.observableListFor(hostGroup);
        int insertionIndex = observableList.indexOf(afterElement);
        if (insertionIndex < 0) {
            insertionIndex = observableList.size() - 1;
        }
        List<CardElement> addedToGroup = new ArrayList<>();
        for (int i = 0; i < cardsToInsert.size(); i++) {
            Card card = cardsToInsert.get(i);
            if (card == null) {
                continue;
            }
            int targetIndex = insertionIndex + 1 + i;
            if (targetIndex > observableList.size()) {
                targetIndex = observableList.size();
            }
            CardElement newElement = new CardElement(card);
            observableList.add(targetIndex, newElement);
            addedToGroup.add(newElement);
        }
        CardTreeCell.triggerHeightAdjustment(hostGroup);
        CardGroupRegistry.notifyOuicheListOfGroupAdditions(hostGroup, addedToGroup);
        return !addedToGroup.isEmpty();
    }

    /**
     * Returns the {@link Deck} that owns {@code group} as one of its registered
     * section groups (main / extra / side), or {@code null}.
     * Delegates to {@link CardTreeCell#findDeckOwnerForGroup}.
     *
     * @param group the group to look up
     * @return the owning deck, or {@code null}
     */
    private static Deck findDeckOwnerForGroup(CardsGroup group) {
        if (group == null) {
            return null;
        }
        return CardTreeCell.findDeckOwnerForGroup(group);
    }

    // ── Insert cards after element ────────────────────────────────────────────

    /**
     * Swaps {@code incoming} (an owned copy with better condition/rarity) into the
     * position currently occupied by {@code outgoing} (a degraded copy inside a deck
     * or collection), and moves {@code outgoing} back into the owned collection at the
     * position {@code incoming} came from.
     * <p>
     * Both positions are preserved so the order in the deck/collection and in the
     * owned collection stays stable. Safe to call from any thread.
     * </p>
     *
     * @param incoming the owned {@link CardElement} that is the quality upgrade
     * @param outgoing the deck/collection {@link CardElement} to be replaced
     */
    public static void handleSwap(CardElement incoming, CardElement outgoing) {
        if (incoming == null || outgoing == null) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                doSwap(incoming, outgoing);
            } else {
                Platform.runLater(() -> doSwap(incoming, outgoing));
            }
        } catch (Throwable throwable) {
            logger.debug("handleSwap failed", throwable);
        }
    }

    /**
     * Core swap logic — must be called on the FX Application Thread.
     * <ol>
     *   <li>Locate {@code outgoing} in every DAC list and record its position.</li>
     *   <li>Locate {@code incoming} in the owned collection and record its position.</li>
     *   <li>Replace {@code outgoing} with {@code incoming} in the DAC list (same index).</li>
     *   <li>Replace {@code incoming} with {@code outgoing} in the owned collection
     *       (same index).</li>
     *   <li>Mark both sides dirty and refresh both views.</li>
     * </ol>
     */
    private static void doSwap(CardElement incoming, CardElement outgoing) {
        OwnedCardsCollection owned = safeGetOwnedCollection();
        DecksAndCollectionsList decksAndCollections = null;
        try {
            decksAndCollections = UserInterfaceFunctions.getDecksList();
        } catch (Throwable ignored) {
        }

        if (owned == null || decksAndCollections == null) {
            logger.warn("doSwap: owned collection or decksAndCollections not available");
            return;
        }

        // 1. Find outgoing's location in DAC
        DacLocation outgoingLoc = findInDac(outgoing, decksAndCollections);
        if (outgoingLoc == null) {
            logger.warn("doSwap: outgoing element not found in any DAC list");
            return;
        }

        // 2. Find incoming's location in owned collection
        SourceLocation incomingLoc = findSource(incoming, owned);
        if (incomingLoc == null) {
            logger.warn("doSwap: incoming element not found in owned collection");
            return;
        }

        // 3. Swap in DAC list (in-place, preserving index)
        try {
            outgoingLoc.list.set(outgoingLoc.index, incoming);
        } catch (Throwable exception) {
            logger.debug("doSwap: failed to replace outgoing in DAC list", exception);
            return;
        }

        // 4. Swap in owned collection (in-place via ObservableList)
        try {
            javafx.collections.ObservableList<CardElement> srcObs =
                    CardTreeCell.observableListFor(incomingLoc.group);
            int insertionIndex = incomingLoc.index;
            if (insertionIndex >= 0
                    && insertionIndex < srcObs.size()
                    && srcObs.get(insertionIndex) == incoming) {
                srcObs.set(insertionIndex, outgoing);
            } else {
                int actualIndex = srcObs.indexOf(incoming);
                if (actualIndex >= 0) {
                    srcObs.set(actualIndex, outgoing);
                } else {
                    srcObs.add(outgoing);
                }
            }
            CardTreeCell.triggerHeightAdjustment(incomingLoc.group);
        } catch (Throwable exception) {
            logger.debug("doSwap: failed to replace incoming in owned collection", exception);
        }

        // 5. Mark dirty and refresh
        try {
            UserInterfaceFunctions.markDirty(outgoingLoc.owner);
        } catch (Throwable ignored) {
        }
        try {
            UserInterfaceFunctions.markMyCollectionDirty();
        } catch (Throwable ignored) {
        }
        try {
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        } catch (Throwable ignored) {
        }
        try {
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        } catch (Throwable ignored) {
        }
        try {
            UserInterfaceFunctions.refreshOwnedCollectionView();
        } catch (Throwable ignored) {
        }

        logger.debug("doSwap: swapped '{}' (owned) <-> '{}' (deck/collection)",
                incoming.getCard() != null ? incoming.getCard().getName_EN() : "?",
                outgoing.getCard() != null ? outgoing.getCard().getName_EN() : "?");
    }

    // ── Swap ──────────────────────────────────────────────────────────────────

    /**
     * Locates {@code element} (by object identity) in every card list in the DAC.
     *
     * @param element            the element to search for
     * @param decksAndCollections the DAC to search
     * @return a {@link DacLocation} with the owning object, the list, and the index,
     *         or {@code null} if not found
     */
    private static DacLocation findInDac(CardElement element,
                                         DecksAndCollectionsList decksAndCollections) {
        if (element == null || decksAndCollections == null) {
            return null;
        }
        if (decksAndCollections.getCollections() != null) {
            for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
                if (themeCollection == null) {
                    continue;
                }
                DacLocation loc = findInList(element, themeCollection.getCardsList(),
                        themeCollection);
                if (loc != null) {
                    return loc;
                }
                loc = findInList(element, themeCollection.getExceptionsToNotAdd(),
                        themeCollection);
                if (loc != null) {
                    return loc;
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
                            loc = findInList(element, deck.getMainDeck(), deck);
                            if (loc != null) {
                                return loc;
                            }
                            loc = findInList(element, deck.getExtraDeck(), deck);
                            if (loc != null) {
                                return loc;
                            }
                            loc = findInList(element, deck.getSideDeck(), deck);
                            if (loc != null) {
                                return loc;
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
                DacLocation loc = findInList(element, deck.getMainDeck(), deck);
                if (loc != null) {
                    return loc;
                }
                loc = findInList(element, deck.getExtraDeck(), deck);
                if (loc != null) {
                    return loc;
                }
                loc = findInList(element, deck.getSideDeck(), deck);
                if (loc != null) {
                    return loc;
                }
            }
        }
        return null;
    }

    private static DacLocation findInList(CardElement element, List<CardElement> list,
                                          Object owner) {
        if (list == null) {
            return null;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == element) {
                return new DacLocation(owner, list, i);
            }
        }
        return null;
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
     * Swaps two {@link CardElement} objects that are both in the owned collection,
     * exchanging their positions in-place.
     * <p>
     * This is used when the user swaps two owned copies with each other (My Collection
     * context), where neither element is a D&amp;C definition entry.
     * {@link #handleSwap} cannot be used in that case because {@code doSwap} requires
     * {@code outgoing} to be a DAC element.
     * </p>
     * <p>
     * Safe to call from any thread; the actual swap is always dispatched on the
     * JavaFX Application Thread.
     * </p>
     *
     * @param elementA the first owned element
     * @param elementB the second owned element
     */
    public static void handleSwapOwned(CardElement elementA, CardElement elementB) {
        if (elementA == null || elementB == null || elementA == elementB) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                doSwapOwned(elementA, elementB);
            } else {
                Platform.runLater(() -> doSwapOwned(elementA, elementB));
            }
        } catch (Throwable throwable) {
            logger.debug("handleSwapOwned failed", throwable);
        }
    }

    /**
     * Core owned-to-owned swap logic — must be called on the FX Application Thread.
     * <ol>
     *   <li>Locate both elements in the owned collection by reference identity.</li>
     *   <li>Replace element A with element B in A's group (same index).</li>
     *   <li>Replace element B with element A in B's group (same index).</li>
     *   <li>Mark dirty and refresh the owned-collection view.</li>
     * </ol>
     */
    private static void doSwapOwned(CardElement elementA, CardElement elementB) {
        OwnedCardsCollection owned = safeGetOwnedCollection();
        if (owned == null) {
            logger.warn("doSwapOwned: owned collection not available");
            return;
        }

        SourceLocation locA = findSource(elementA, owned);
        SourceLocation locB = findSource(elementB, owned);

        if (locA == null) {
            logger.warn("doSwapOwned: elementA not found in owned collection");
            return;
        }
        if (locB == null) {
            logger.warn("doSwapOwned: elementB not found in owned collection");
            return;
        }

        if (locA.group == locB.group) {
            // Same group: swap in one ObservableList operation.
            javafx.collections.ObservableList<CardElement> obs =
                    CardTreeCell.observableListFor(locA.group);
            int idxA = (locA.index >= 0 && locA.index < obs.size()
                    && obs.get(locA.index) == elementA)
                    ? locA.index : obs.indexOf(elementA);
            int idxB = (locB.index >= 0 && locB.index < obs.size()
                    && obs.get(locB.index) == elementB)
                    ? locB.index : obs.indexOf(elementB);
            if (idxA >= 0 && idxB >= 0) {
                obs.set(idxA, elementB);
                obs.set(idxB, elementA);
            }
            CardTreeCell.triggerHeightAdjustment(locA.group);
        } else {
            // Different groups: replace each element with the other.
            javafx.collections.ObservableList<CardElement> obsA =
                    CardTreeCell.observableListFor(locA.group);
            javafx.collections.ObservableList<CardElement> obsB =
                    CardTreeCell.observableListFor(locB.group);
            int idxA = (locA.index >= 0 && locA.index < obsA.size()
                    && obsA.get(locA.index) == elementA)
                    ? locA.index : obsA.indexOf(elementA);
            int idxB = (locB.index >= 0 && locB.index < obsB.size()
                    && obsB.get(locB.index) == elementB)
                    ? locB.index : obsB.indexOf(elementB);
            if (idxA >= 0) {
                obsA.set(idxA, elementB);
            } else {
                obsA.add(elementB);
            }
            if (idxB >= 0) {
                obsB.set(idxB, elementA);
            } else {
                obsB.add(elementA);
            }
            CardTreeCell.triggerHeightAdjustment(locA.group);
            CardTreeCell.triggerHeightAdjustment(locB.group);
        }

        UserInterfaceFunctions.markMyCollectionDirty();
        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        UserInterfaceFunctions.refreshOwnedCollectionView();

        logger.debug("doSwapOwned: swapped '{}' <-> '{}'",
                elementA.getCard() != null ? elementA.getCard().getName_EN() : "?",
                elementB.getCard() != null ? elementB.getCard().getName_EN() : "?");
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

    // ── Private record-like holders ───────────────────────────────────────────

    private static final class DacLocation {
        /** The {@link Deck} or {@link ThemeCollection} that owns the list. */
        final Object owner;
        /** The actual list (mainDeck, extraDeck, sideDeck, or cardsList). */
        final List<CardElement> list;
        final int index;

        DacLocation(Object owner, List<CardElement> list, int index) {
            this.owner = owner;
            this.list = list;
            this.index = index;
        }
    }
}