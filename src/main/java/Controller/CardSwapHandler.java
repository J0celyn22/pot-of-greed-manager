package Controller;

import Model.CardsLists.*;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Handles the "Swap" card-menu actions: replacing a degraded copy in a Deck
 * or Collection with a better-condition owned copy ({@code doSwap}), and
 * swapping two owned copies with each other ({@code doSwapOwned}).
 * <p>
 * Extracted from {@link MenuActionHandler}, which still exposes
 * {@code handleSwap} and {@code handleSwapOwned} as thin delegates so
 * existing call sites are unaffected. The shared lookup helpers
 * {@code safeGetOwnedCollection}, {@code findSource} and
 * {@code SourceLocation} remain on {@link MenuActionHandler} itself, since
 * they are also used by the Move and Add-Category-And-Move operations.
 * </p>
 */
final class CardSwapHandler {

    private static final Logger logger = LoggerFactory.getLogger(CardSwapHandler.class);

    private CardSwapHandler() {
    }

    // ── Swap ──────────────────────────────────────────────────────────────────

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
        OwnedCardsCollection owned = MenuActionHandler.safeGetOwnedCollection();
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
        MenuActionHandler.SourceLocation incomingLoc = MenuActionHandler.findSource(incoming, owned);
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
                    CardGroupRegistry.observableListFor(incomingLoc.group);
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
            CardGroupRegistry.triggerHeightAdjustment(incomingLoc.group);
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

    /**
     * Locates {@code element} (by object identity) in every card list in the DAC.
     *
     * @param element             the element to search for
     * @param decksAndCollections the DAC to search
     * @return a {@link DacLocation} with the owning object, the list, and the index,
     * or {@code null} if not found
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
        OwnedCardsCollection owned = MenuActionHandler.safeGetOwnedCollection();
        if (owned == null) {
            logger.warn("doSwapOwned: owned collection not available");
            return;
        }

        MenuActionHandler.SourceLocation locA = MenuActionHandler.findSource(elementA, owned);
        MenuActionHandler.SourceLocation locB = MenuActionHandler.findSource(elementB, owned);

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
                    CardGroupRegistry.observableListFor(locA.group);
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
            CardGroupRegistry.triggerHeightAdjustment(locA.group);
        } else {
            // Different groups: replace each element with the other.
            javafx.collections.ObservableList<CardElement> obsA =
                    CardGroupRegistry.observableListFor(locA.group);
            javafx.collections.ObservableList<CardElement> obsB =
                    CardGroupRegistry.observableListFor(locB.group);
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
            CardGroupRegistry.triggerHeightAdjustment(locA.group);
            CardGroupRegistry.triggerHeightAdjustment(locB.group);
        }

        UserInterfaceFunctions.markMyCollectionDirty();
        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        UserInterfaceFunctions.refreshOwnedCollectionView();

        logger.debug("doSwapOwned: swapped '{}' <-> '{}'",
                elementA.getCard() != null ? elementA.getCard().getName_EN() : "?",
                elementB.getCard() != null ? elementB.getCard().getName_EN() : "?");
    }

    private static final class DacLocation {
        /**
         * The {@link Deck} or {@link ThemeCollection} that owns the list.
         */
        final Object owner;
        /**
         * The actual list (mainDeck, extraDeck, sideDeck, or cardsList).
         */
        final List<CardElement> list;
        final int index;

        DacLocation(Object owner, List<CardElement> list, int index) {
            this.owner = owner;
            this.list = list;
            this.index = index;
        }
    }
}