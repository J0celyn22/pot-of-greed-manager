package Controller; // adapt to your package

import Model.CardsLists.*;
import View.CardTreeCell;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
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

    // ── Move ──────────────────────────────────────────────────────────────────

    /**
     * Public entry point for "move" menu items.
     * <p>
     * Moves {@code clickedElement} from its current position in the
     * {@link OwnedCardsCollection} into the group identified by
     * {@code handlerTarget}. The operation is dispatched on the FX thread and
     * a collection-view refresh is triggered afterwards.
     * </p>
     *
     * @param clickedElement the {@link CardElement} to move (may be {@code null})
     * @param handlerTarget  canonical target string, e.g. {@code "Box"},
     *                       {@code "Box/Group"}, {@code "Deck/Main Deck"}
     */
    public static void handleMove(CardElement clickedElement, String handlerTarget) {
        if (handlerTarget == null || handlerTarget.trim().isEmpty()) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                doMove(clickedElement, handlerTarget);
            } else {
                Platform.runLater(() -> doMove(clickedElement, handlerTarget));
            }
            UserInterfaceFunctions.refreshOwnedCollectionView();
        } catch (Throwable throwable) {
            logger.debug("handleMove failed for target {}", handlerTarget, throwable);
        }
    }

    // ── Add copy to owned collection ──────────────────────────────────────────

    /**
     * Creates a fresh {@link CardElement} from the given {@link Card} and inserts
     * it into the matching Box / Category of the {@link OwnedCardsCollection}.
     * The existing {@link Card} instance is reused directly so its printCode is
     * preserved.
     *
     * @param card          the card from AllExistingCards (not {@code null})
     * @param handlerTarget canonical target string, e.g. {@code "BoxName"},
     *                      {@code "BoxName / CategoryName"},
     *                      {@code "BoxName / SubBoxName"},
     *                      {@code "BoxName / SubBoxName / CategoryName"}
     */
    public static void handleAddCopy(Card card, String handlerTarget) {
        if (card == null || handlerTarget == null || handlerTarget.trim().isEmpty()) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                doAddCopy(card, handlerTarget);
            } else {
                Platform.runLater(() -> doAddCopy(card, handlerTarget));
            }
            UserInterfaceFunctions.refreshOwnedCollectionView();
        } catch (Throwable throwable) {
            logger.debug("handleAddCopy failed for target {}", handlerTarget, throwable);
        }
    }

    private static void doAddCopy(Card card, String handlerTarget) {
        OwnedCardsCollection owned = safeGetOwnedCollection();
        if (owned == null || owned.getOwnedCollection() == null) {
            logger.warn("OwnedCardsCollection not available; cannot add card to {}", handlerTarget);
            return;
        }

        CardsGroup dest = findMyCollectionDestination(handlerTarget, owned);
        if (dest == null) {
            logger.info("My Collection destination not found for '{}'; skipping add", handlerTarget);
            return;
        }

        CardElement newElement = new CardElement(card);

        // Add through the ObservableList: updates the model backing list AND notifies the GridView.
        CardTreeCell.observableListFor(dest).add(newElement);
        CardTreeCell.triggerHeightAdjustment(dest);
        logger.debug("doAddCopy: added '{}' to '{}'", card.getName_EN(), handlerTarget);

        lastAddedTarget = handlerTarget;

        UserInterfaceFunctions.markMyCollectionDirty();
        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
    }

    /**
     * Finds the {@link CardsGroup} inside the {@link OwnedCardsCollection} that
     * matches the given path.
     * <p>
     * Path formats (slash-separated, trimmed, accent- and case-insensitive):
     * <ul>
     *   <li>{@code "BoxName"} → default group of that Box</li>
     *   <li>{@code "BoxName / CategoryName"} → named category inside Box</li>
     *   <li>{@code "BoxName / SubBoxName"} → default group of SubBox inside Box</li>
     *   <li>{@code "BoxName / SubBoxName / CatName"} → named category inside SubBox</li>
     * </ul>
     * </p>
     */
    private static CardsGroup findMyCollectionDestination(String handlerTarget,
                                                          OwnedCardsCollection owned) {
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

        String boxNorm = normalizer.apply(parts[0]);

        for (Box box : owned.getOwnedCollection()) {
            if (box == null || !normalizer.apply(box.getName()).equals(boxNorm)) {
                continue;
            }

            if (parts.length == 1) {
                // "BoxName" → default (empty-named) group, or first group, or create one
                return getOrCreateDefaultGroup(box);
            }

            String secondNorm = normalizer.apply(parts[1]);

            // Could be a Category directly inside the Box
            if (parts.length == 2 && box.getContent() != null) {
                for (CardsGroup cardsGroup : box.getContent()) {
                    if (cardsGroup != null && normalizer.apply(cardsGroup.getName()).equals(secondNorm)) {
                        return cardsGroup;
                    }
                }
            }

            // Could be a SubBox
            if (box.getSubBoxes() != null) {
                for (Box subBox : box.getSubBoxes()) {
                    if (subBox == null || !normalizer.apply(subBox.getName()).equals(secondNorm)) {
                        continue;
                    }

                    if (parts.length == 2) {
                        // "BoxName / SubBoxName" → default group of SubBox
                        return getOrCreateDefaultGroup(subBox);
                    }

                    // "BoxName / SubBoxName / CategoryName"
                    String catNorm = normalizer.apply(parts[2]);
                    if (subBox.getContent() != null) {
                        for (CardsGroup cardsGroup : subBox.getContent()) {
                            if (cardsGroup != null && normalizer.apply(cardsGroup.getName()).equals(catNorm)) {
                                return cardsGroup;
                            }
                        }
                    }
                }
            }
        }
        return null;
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

    // ── Core move implementation ──────────────────────────────────────────────

    private static void doMove(CardElement clickedElement, String handlerTarget) {
        // 1) obtain shared owned collection
        OwnedCardsCollection owned = safeGetOwnedCollection();
        if (owned == null || owned.getOwnedCollection() == null) {
            logger.warn("OwnedCardsCollection not available; cannot move card to {}", handlerTarget);
            return;
        }

        // 2) find source element (prefer exact instance)
        SourceLocation src = findSource(clickedElement, owned);

        // 3) find destination group
        Destination dest = findDestinationGroup(handlerTarget, owned);
        if (dest == null || dest.group == null) {
            logger.info("Destination not found for '{}'; skipping move", handlerTarget);
            return;
        }

        // 4) remove from source via ObservableList (updates model + GridView automatically)
        if (src != null && src.group != null && src.index >= 0) {
            try {
                javafx.collections.ObservableList<CardElement> srcObs =
                        CardTreeCell.observableListFor(src.group);
                if (src.index < srcObs.size() && srcObs.get(src.index) == src.element) {
                    srcObs.remove(src.index);
                } else {
                    srcObs.remove(src.element);
                }
                CardTreeCell.triggerHeightAdjustment(src.group);
            } catch (Throwable exception) {
                logger.debug("Failed to remove element from source; continuing", exception);
            }
        }

        // 5) prepare element to add
        CardElement toAdd = src != null ? src.element : null;
        if (toAdd == null && clickedElement != null && clickedElement.getCard() != null) {
            toAdd = constructCardElementFallback(clickedElement);
        }
        if (toAdd == null) {
            logger.warn("No CardElement available to add for target {}", handlerTarget);
            return;
        }

        // 6) add to destination via ObservableList (updates model + GridView automatically)
        try {
            CardTreeCell.observableListFor(dest.group).add(toAdd);
            CardTreeCell.triggerHeightAdjustment(dest.group);
        } catch (Throwable exception) {
            logger.debug("Failed to add element to destination", exception);
        }

        UserInterfaceFunctions.markMyCollectionDirty();
        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static OwnedCardsCollection safeGetOwnedCollection() {
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

    private static SourceLocation findSource(CardElement clickedElement,
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

    private static CardElement constructCardElementFallback(CardElement clickedElement) {
        if (clickedElement == null) {
            return null;
        }
        try {
            // Try direct constructor new CardElement(Card)
            try {
                Constructor<?> ctor = Class.forName("Model.CardsLists.CardElement")
                        .getConstructor(Card.class);
                return (CardElement) ctor.newInstance(clickedElement.getCard());
            } catch (Throwable ignored) {
            }
            // Fallback: default constructor + setter
            try {
                Class<?> ceClass = Class.forName("Model.CardsLists.CardElement");
                Object ceObj = ceClass.getDeclaredConstructor().newInstance();
                try {
                    Method setCard = ceClass.getMethod("setCard", Card.class);
                    setCard.invoke(ceObj, clickedElement.getCard());
                } catch (Throwable ignored) {
                }
                return (CardElement) ceObj;
            } catch (Throwable exception) {
                logger.debug("Reflection fallback failed constructing CardElement", exception);
            }
        } catch (Throwable throwable) {
            logger.debug("constructCardElementFallback failed", throwable);
        }
        return null;
    }

    /**
     * Finds the {@link Destination} (owning Box + CardsGroup) within the
     * {@link OwnedCardsCollection} that best matches the given handler-target path.
     * <p>
     * Matching is accent-insensitive, case-insensitive, and punctuation-collapsed.
     * The method tries progressively looser strategies before returning {@code null}.
     * </p>
     * <p>
     * <strong>Note:</strong> this method is intentionally not split in this phase;
     * decomposition into focused sub-strategies is deferred to Phase 4.
     * </p>
     */
    private static Destination findDestinationGroup(String handlerTarget,
                                                    OwnedCardsCollection owned) {
        if (handlerTarget == null
                || handlerTarget.trim().isEmpty()
                || owned == null
                || owned.getOwnedCollection() == null) {
            return null;
        }

        // Normalize: accent-insensitive, case-insensitive, punctuation-collapsed
        java.util.function.Function<String, String> normalizer = input -> {
            if (input == null) {
                return "";
            }
            String normalized = input.trim().replaceAll("\\s+", " ");
            normalized = java.text.Normalizer
                    .normalize(normalized, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            normalized = normalized.replaceAll("[\\p{Punct}]+", " ");
            return normalized.toLowerCase().trim();
        };

        String[] parts = handlerTarget.split("/");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }

        // Detect if last component is a deck-list name (Main / Extra / Side Deck)
        String lastComponent = parts[parts.length - 1];
        String lastNorm = normalizer.apply(lastComponent);
        boolean isDeckList = lastNorm.equals("main deck")
                || lastNorm.equals("extra deck")
                || lastNorm.equals("side deck")
                || lastNorm.equals("maindeck")
                || lastNorm.equals("extradeck")
                || lastNorm.equals("sidedeck")
                || lastNorm.equals("main")
                || lastNorm.equals("extra")
                || lastNorm.equals("side");

        // Helper: search owned boxes/groups by normalized box name and optional group name.
        // IMPORTANT: if groupName is non-empty, do NOT fall back to the first group of the box.
        java.util.function.BiFunction<String, String, Destination> findBoxAndGroupByBoxThenGroup =
                (boxName, groupName) -> {
                    String bNorm = normalizer.apply(boxName);
                    String gNorm = groupName == null ? "" : normalizer.apply(groupName);
                    for (Box box : owned.getOwnedCollection()) {
                        if (box == null) {
                            continue;
                        }
                        if (normalizer.apply(box.getName()).equals(bNorm)) {
                            if (box.getContent() != null) {
                                // If a group name was provided, require an exact group match
                                if (!gNorm.isEmpty()) {
                                    for (CardsGroup cardsGroup : box.getContent()) {
                                        if (cardsGroup == null) {
                                            continue;
                                        }
                                        if (normalizer.apply(cardsGroup.getName()).equals(gNorm)) {
                                            return new Destination(box, cardsGroup);
                                        }
                                    }
                                    // Explicit group requested but not found — do not return a fallback
                                    return null;
                                }
                                // No group requested: prefer a box-level group (empty name) if present
                                for (CardsGroup cardsGroup : box.getContent()) {
                                    if (cardsGroup == null) {
                                        continue;
                                    }
                                    String gName = cardsGroup.getName();
                                    if (gName == null || gName.trim().isEmpty()) {
                                        return new Destination(box, cardsGroup);
                                    }
                                }
                                // No box-level group: create one via reflection
                                try {
                                    Class<?> cgClass = Class.forName("Model.CardsLists.CardsGroup");
                                    Object newGroupObj = null;
                                    try {
                                        newGroupObj = cgClass.getDeclaredConstructor().newInstance();
                                    } catch (NoSuchMethodException noSuchMethod) {
                                        try {
                                            java.lang.reflect.Constructor<?> ctor =
                                                    cgClass.getDeclaredConstructor(String.class);
                                            ctor.setAccessible(true);
                                            newGroupObj = ctor.newInstance("");
                                        } catch (NoSuchMethodException ignored) {
                                            newGroupObj = null;
                                        }
                                    }
                                    if (newGroupObj != null) {
                                        try {
                                            java.lang.reflect.Method setName =
                                                    cgClass.getMethod("setName", String.class);
                                            setName.invoke(newGroupObj, "");
                                        } catch (Throwable ignored) {
                                        }
                                        try {
                                            java.lang.reflect.Method setCardList =
                                                    cgClass.getMethod("setCardList", java.util.List.class);
                                            setCardList.invoke(newGroupObj,
                                                    new java.util.ArrayList<CardElement>());
                                        } catch (Throwable ignored) {
                                        }
                                        try {
                                            box.getContent().add((CardsGroup) newGroupObj);
                                            return new Destination(box, (CardsGroup) newGroupObj);
                                        } catch (ClassCastException ignored) {
                                        }
                                    }
                                } catch (Throwable ignored) {
                                    // Reflection failed; fall back to returning first group if present
                                }
                                // Fallback: return first group if present (legacy behaviour)
                                if (!box.getContent().isEmpty()) {
                                    return new Destination(box, box.getContent().get(0));
                                }
                            }
                        }
                    }
                    return null;
                };

        // ── Strategy 1: target points to a deck list ─────────────────────────
        if (isDeckList) {
            String deckName = parts.length >= 2 ? parts[parts.length - 2] : null;
            String collectionName = parts.length >= 3 ? parts[0] : null;

            DecksAndCollectionsList decksAndCollections = null;
            try {
                decksAndCollections = UserInterfaceFunctions.getDecksList();
            } catch (Throwable ignored) {
            }

            // Try: if collectionName present, find via collection → deck lookup
            if (decksAndCollections != null && collectionName != null && deckName != null) {
                String collNorm = normalizer.apply(collectionName);
                String deckNorm = normalizer.apply(deckName);
                if (decksAndCollections.getCollections() != null) {
                    for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
                        if (themeCollection == null) {
                            continue;
                        }
                        if (normalizer.apply(themeCollection.getName()).equals(collNorm)) {
                            if (themeCollection.getLinkedDecks() != null) {
                                for (List<Deck> unit : themeCollection.getLinkedDecks()) {
                                    if (unit == null) {
                                        continue;
                                    }
                                    for (Deck deck : unit) {
                                        if (deck == null) {
                                            continue;
                                        }
                                        if (normalizer.apply(deck.getName()).equals(deckNorm)) {
                                            // Prefer Box = collectionName, Group = deckName
                                            Destination dest = findBoxAndGroupByBoxThenGroup
                                                    .apply(themeCollection.getName(), deckName);
                                            if (dest != null) {
                                                // Try to find the specific list group (Main/Extra/Side)
                                                String listNorm = normalizer.apply(lastComponent);
                                                if (dest.box.getContent() != null) {
                                                    for (CardsGroup cardsGroup : dest.box.getContent()) {
                                                        if (cardsGroup == null) {
                                                            continue;
                                                        }
                                                        if (normalizer.apply(cardsGroup.getName())
                                                                .equals(listNorm)) {
                                                            return new Destination(dest.box, cardsGroup);
                                                        }
                                                    }
                                                }
                                                // deckName matched a group but list not found — do not fallback
                                                return dest;
                                            }
                                            // Fallback: Box = deckName, Group = listName
                                            Destination dest2 = findBoxAndGroupByBoxThenGroup
                                                    .apply(deckName, lastComponent);
                                            if (dest2 != null) {
                                                return dest2;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Try top-level decks (not in collections)
            if (decksAndCollections != null
                    && deckName != null
                    && decksAndCollections.getDecks() != null) {
                String deckNorm = normalizer.apply(deckName);
                for (Deck deck : decksAndCollections.getDecks()) {
                    if (deck == null) {
                        continue;
                    }
                    if (normalizer.apply(deck.getName()).equals(deckNorm)) {
                        Destination dest = findBoxAndGroupByBoxThenGroup
                                .apply(deck.getName(), lastComponent);
                        if (dest != null) {
                            return dest;
                        }
                        // Fallback: find any group named like the list across all owned boxes
                        String listNorm = normalizer.apply(lastComponent);
                        for (Box box : owned.getOwnedCollection()) {
                            if (box == null || box.getContent() == null) {
                                continue;
                            }
                            for (CardsGroup cardsGroup : box.getContent()) {
                                if (cardsGroup == null) {
                                    continue;
                                }
                                if (normalizer.apply(cardsGroup.getName()).equals(listNorm)) {
                                    return new Destination(box, cardsGroup);
                                }
                            }
                        }
                    }
                }
            }

            // Generic fallback: match group name to deckName or listName (exact)
            if (deckName != null) {
                String deckNorm = normalizer.apply(deckName);
                for (Box box : owned.getOwnedCollection()) {
                    if (box == null || box.getContent() == null) {
                        continue;
                    }
                    for (CardsGroup cardsGroup : box.getContent()) {
                        if (cardsGroup == null) {
                            continue;
                        }
                        String gNorm = normalizer.apply(cardsGroup.getName());
                        if (gNorm.equals(deckNorm) || gNorm.equals(lastNorm)) {
                            return new Destination(box, cardsGroup);
                        }
                    }
                }
            }

            // Permissive: match last component (list) anywhere (exact)
            for (Box box : owned.getOwnedCollection()) {
                if (box == null || box.getContent() == null) {
                    continue;
                }
                for (CardsGroup cardsGroup : box.getContent()) {
                    if (cardsGroup == null) {
                        continue;
                    }
                    if (normalizer.apply(cardsGroup.getName()).equals(lastNorm)) {
                        return new Destination(box, cardsGroup);
                    }
                }
            }

            // Nothing found for deck target
            return null;
        }

        // ── Strategy 2: collection or box/group target ────────────────────────
        String comp0 = parts[0];
        String comp0Norm = normalizer.apply(comp0);
        for (Box box : owned.getOwnedCollection()) {
            if (box == null) {
                continue;
            }
            if (normalizer.apply(box.getName()).equals(comp0Norm)) {
                if (parts.length == 1) {
                    if (box.getContent() != null && !box.getContent().isEmpty()) {
                        Destination dest = findBoxAndGroupByBoxThenGroup.apply(box.getName(), "");
                        if (dest != null) {
                            return dest;
                        }
                    }
                } else {
                    // A group was requested; require exact group match
                    String requestedGroup = parts[parts.length - 1];
                    if (box.getContent() != null) {
                        for (CardsGroup cardsGroup : box.getContent()) {
                            if (cardsGroup == null) {
                                continue;
                            }
                            if (normalizer.apply(cardsGroup.getName())
                                    .equals(normalizer.apply(requestedGroup))) {
                                return new Destination(box, cardsGroup);
                            }
                        }
                    }
                }
            }
        }

        // Try to match a group name equal to first component across all boxes (exact)
        for (Box box : owned.getOwnedCollection()) {
            if (box == null || box.getContent() == null) {
                continue;
            }
            for (CardsGroup cardsGroup : box.getContent()) {
                if (cardsGroup == null) {
                    continue;
                }
                if (normalizer.apply(cardsGroup.getName()).equals(comp0Norm)) {
                    return new Destination(box, cardsGroup);
                }
            }
        }

        // Try matching any path component as a group name (useful for "Collection/Deck" targets)
        for (String part : parts) {
            String partNorm = normalizer.apply(part);
            if (partNorm.isEmpty()) {
                continue;
            }
            for (Box box : owned.getOwnedCollection()) {
                if (box == null || box.getContent() == null) {
                    continue;
                }
                for (CardsGroup cardsGroup : box.getContent()) {
                    if (cardsGroup == null) {
                        continue;
                    }
                    if (normalizer.apply(cardsGroup.getName()).equals(partNorm)) {
                        return new Destination(box, cardsGroup);
                    }
                }
            }
        }

        // Final permissive fallback: partial contains-match on last component
        String lastNorm2 = normalizer.apply(parts[parts.length - 1]);
        if (!lastNorm2.isEmpty()) {
            for (Box box : owned.getOwnedCollection()) {
                if (box == null || box.getContent() == null) {
                    continue;
                }
                for (CardsGroup cardsGroup : box.getContent()) {
                    if (cardsGroup == null) {
                        continue;
                    }
                    String gNorm = normalizer.apply(cardsGroup.getName());
                    if (gNorm.contains(lastNorm2) || lastNorm2.contains(gNorm)) {
                        return new Destination(box, cardsGroup);
                    }
                }
            }
        }

        return null;
    }

    // ── Value holders (private inner classes) ─────────────────────────────────

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
            logger.debug("handleAddToDeck failed for target {}", handlerTarget, throwable);
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
            logger.debug("handleAddToCollectionCards failed for collection {}",
                    collectionName, throwable);
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
        foundCollection.getCardsList().add(new CardElement(card));
        logger.debug("handleAddToCollectionCards: added '{}' to collection '{}'",
                card.getName_EN(), collectionName);

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
        if (isMain) {
            targetDeck.getMainDeck().add(newElement);
        } else if (isExtra) {
            targetDeck.getExtraDeck().add(newElement);
        } else {
            targetDeck.getSideDeck().add(newElement);
        }
        logger.debug("doAddToDeck: added '{}' to '{}'", card.getName_EN(), handlerTarget);

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
                    doMove(element, handlerTarget);
                }
            } else {
                final List<CardElement> copy = new ArrayList<>(elements);
                Platform.runLater(() -> {
                    for (CardElement element : copy) {
                        doMove(element, handlerTarget);
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
                    doAddCopy(card, handlerTarget);
                }
            } else {
                final List<Card> copy = new ArrayList<>(cards);
                Platform.runLater(() -> {
                    for (Card card : copy) {
                        doAddCopy(card, handlerTarget);
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
            logger.debug("handleBulkAddToDeck failed for target {}", handlerTarget, throwable);
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
            logger.debug("handleBulkAddToCollectionCards failed for {}", collectionName, throwable);
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
     * Pastes {@code clipboardCards} immediately after {@code afterElement} in the
     * group that contains it in the {@link OwnedCardsCollection}.
     *
     * @param clipboardCards the cards to paste (not {@code null} or empty)
     * @param afterElement   the anchor element; the paste position is after this element
     */
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

        java.util.function.Predicate<List<CardElement>> removeMatching = list -> {
            if (list == null) {
                return false;
            }
            return list.removeIf(cardElement ->
                    cardElement != null
                            && cardElement.getCard() != null
                            && collectionContainsCard(cardsToRemove, cardElement.getCard()));
        };

        java.util.Set<Object> dirtyOwners = new java.util.LinkedHashSet<>();

        if (decksAndCollections.getCollections() != null) {
            for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
                if (themeCollection == null) {
                    continue;
                }
                boolean tcChanged =
                        removeMatching.test(themeCollection.getCardsList())
                                | removeMatching.test(themeCollection.getExceptionsToNotAdd());
                if (tcChanged) {
                    dirtyOwners.add(themeCollection);
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
                            boolean changed =
                                    removeMatching.test(deck.getMainDeck())
                                            | removeMatching.test(deck.getExtraDeck())
                                            | removeMatching.test(deck.getSideDeck());
                            if (changed) {
                                dirtyOwners.add(deck);
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
                boolean changed =
                        removeMatching.test(deck.getMainDeck())
                                | removeMatching.test(deck.getExtraDeck())
                                | removeMatching.test(deck.getSideDeck());
                if (changed) {
                    dirtyOwners.add(deck);
                }
            }
        }

        for (Object owner : dirtyOwners) {
            UserInterfaceFunctions.markDirty(owner);
        }
        if (!dirtyOwners.isEmpty()) {
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
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

        // Identity-based set so two CardElements wrapping the same Card are treated independently
        java.util.Set<CardElement> identitySet =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        identitySet.addAll(elementsToRemove);

        java.util.function.Predicate<List<CardElement>> removeMatching = list -> {
            if (list == null) {
                return false;
            }
            return list.removeIf(cardElement ->
                    cardElement != null && identitySet.contains(cardElement));
        };

        java.util.Set<Object> dirtyOwners = new java.util.LinkedHashSet<>();

        if (decksAndCollections.getCollections() != null) {
            for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
                if (themeCollection == null) {
                    continue;
                }
                boolean tcChanged =
                        removeMatching.test(themeCollection.getCardsList())
                                | removeMatching.test(themeCollection.getExceptionsToNotAdd());
                if (tcChanged) {
                    dirtyOwners.add(themeCollection);
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
                            boolean changed =
                                    removeMatching.test(deck.getMainDeck())
                                            | removeMatching.test(deck.getExtraDeck())
                                            | removeMatching.test(deck.getSideDeck());
                            if (changed) {
                                dirtyOwners.add(deck);
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
                boolean changed =
                        removeMatching.test(deck.getMainDeck())
                                | removeMatching.test(deck.getExtraDeck())
                                | removeMatching.test(deck.getSideDeck());
                if (changed) {
                    dirtyOwners.add(deck);
                }
            }
        }

        for (Object owner : dirtyOwners) {
            UserInterfaceFunctions.markDirty(owner);
        }
        if (!dirtyOwners.isEmpty()) {
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        }
    }

    // ── Bulk remove from Decks & Collections ──────────────────────────────────

    /**
     * Inserts copies of {@code cardsToInsert} immediately after {@code afterElement}
     * in the group that contains it.
     * <p>
     * When the host group is a main- or extra-deck section, cards are split by
     * deck compatibility: compatible cards are inserted at the requested position
     * while incompatible cards are appended to the appropriate sibling section of
     * the same {@link Deck}.
     * </p>
     *
     * @param cardsToInsert the cards to insert
     * @param afterElement  the anchor element
     * @return {@code true} if at least one card was inserted or redirected;
     *         {@code false} if the containing group was not found
     */
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

            // Insert compatible cards after afterElement in hostGroup
            if (!compatible.isEmpty()) {
                javafx.collections.ObservableList<CardElement> list =
                        CardTreeCell.observableListFor(hostGroup);
                int insertionIndex = list.indexOf(afterElement);
                if (insertionIndex < 0) {
                    insertionIndex = list.size() - 1;
                }
                for (int i = 0; i < compatible.size(); i++) {
                    int pos = Math.min(insertionIndex + 1 + i, list.size());
                    list.add(pos, new CardElement(compatible.get(i)));
                }
                CardTreeCell.triggerHeightAdjustment(hostGroup);
            }

            // Append incompatible cards to the end of the sibling section
            if (!incompatible.isEmpty()) {
                String redirect = Utils.DeckCompatibility.redirectSection(
                        incompatible.get(0), groupName);
                if (redirect != null) {
                    Deck ownerDeck = CardTreeCell.findDeckOwnerForGroup(hostGroup);
                    if (ownerDeck != null) {
                        String sectionKey = redirect.toLowerCase(java.util.Locale.ROOT)
                                .replace(" deck", "").trim(); // "main" or "extra"
                        CardsGroup altGroup =
                                CardTreeCell.getDeckSectionGroup(ownerDeck, sectionKey);
                        if (altGroup != null) {
                            javafx.collections.ObservableList<CardElement> altList =
                                    CardTreeCell.observableListFor(altGroup);
                            for (Card card : incompatible) {
                                altList.add(new CardElement(card));
                            }
                            CardTreeCell.triggerHeightAdjustment(altGroup);
                        }
                    }
                }
            }
            return !compatible.isEmpty() || !incompatible.isEmpty();
        }
        // ──────────────────────────────────────────────────────────────────

        // Normal (non-deck-section) insertion
        javafx.collections.ObservableList<CardElement> observableList =
                CardTreeCell.observableListFor(hostGroup);
        int insertionIndex = observableList.indexOf(afterElement);
        if (insertionIndex < 0) {
            insertionIndex = observableList.size() - 1;
        }

        for (int i = 0; i < cardsToInsert.size(); i++) {
            Card card = cardsToInsert.get(i);
            if (card == null) {
                continue;
            }
            int targetIndex = insertionIndex + 1 + i;
            if (targetIndex > observableList.size()) {
                targetIndex = observableList.size();
            }
            observableList.add(targetIndex, new CardElement(card));
        }
        CardTreeCell.triggerHeightAdjustment(hostGroup);
        return true;
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

    private static final class SourceLocation {
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

    // ── Edit card ─────────────────────────────────────────────────────────────

    private static final class Destination {
        final Box box;
        final CardsGroup group;

        Destination(Box box, CardsGroup group) {
            this.box = box;
            this.group = group;
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