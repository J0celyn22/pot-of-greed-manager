package Controller;

import Model.CardsLists.*;
import View.CardTreeCell;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Handles the "Move" card-menu action: relocating a {@link CardElement} from
 * its current position in the owned collection to a target Box / CardsGroup
 * identified by a path string.
 * <p>
 * Extracted from {@link MenuActionHandler}, which still exposes
 * {@code handleMove} as a thin delegate so existing call sites are unaffected.
 * The shared lookup helpers {@code safeGetOwnedCollection}, {@code findSource}
 * and {@code SourceLocation} remain on {@link MenuActionHandler} itself, since
 * they are also used by the Swap and Add-Category-And-Move operations.
 * </p>
 */
final class CardMoveHandler {

    private static final Logger logger = LoggerFactory.getLogger(CardMoveHandler.class);

    private CardMoveHandler() {
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

    // ── Core move implementation ──────────────────────────────────────────────

    static void doMove(CardElement clickedElement, String handlerTarget) {
        // 1) obtain shared owned collection
        OwnedCardsCollection owned = MenuActionHandler.safeGetOwnedCollection();
        if (owned == null || owned.getOwnedCollection() == null) {
            logger.warn("OwnedCardsCollection not available; cannot move card to {}", handlerTarget);
            return;
        }

        // 2) find source element (prefer exact instance)
        MenuActionHandler.SourceLocation src = MenuActionHandler.findSource(clickedElement, owned);

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

    private static final class Destination {
        final Box box;
        final CardsGroup group;

        Destination(Box box, CardsGroup group) {
            this.box = box;
            this.group = group;
        }
    }
}