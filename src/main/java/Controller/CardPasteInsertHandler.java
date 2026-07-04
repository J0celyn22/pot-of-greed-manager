package Controller;

import Model.CardsLists.*;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the "paste" and "insert" card-menu actions: inserting cards or
 * card-element snapshots immediately after an anchor element, with
 * deck-compatibility redirect (main/extra section) applied where relevant.
 * <p>
 * Extracted from {@link MenuActionHandler}, which still exposes {@code
 * handleInsertElementsAfterElement}, {@code
 * handlePasteElementsAfterElementInOwnedCollection}, {@code
 * handlePasteAfterElementInOwnedCollection} and {@code
 * handleInsertCardsAfterElement} as thin delegates. {@code
 * safeGetOwnedCollection} remains on {@link MenuActionHandler} itself, since
 * it is also used by the Move, Swap, and Add-Category-And-Move operations.
 * </p>
 */
final class CardPasteInsertHandler {

    private static final Logger logger = LoggerFactory.getLogger(CardPasteInsertHandler.class);

    private CardPasteInsertHandler() {
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

        CardsGroup hostGroup = CardGroupRegistry.findGroupForCardElement(afterElement);
        if (hostGroup == null) {
            OwnedCardsCollection owned = MenuActionHandler.safeGetOwnedCollection();
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
                        CardGroupRegistry.observableListFor(hostGroup);
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
                CardGroupRegistry.triggerHeightAdjustment(hostGroup);
            }
            CardGroupRegistry.notifyOuicheListOfGroupAdditions(hostGroup, addedToHost);

            List<CardElement> addedToAlt = new ArrayList<>();
            if (!incompatible.isEmpty()) {
                String redirect = Utils.DeckCompatibility.redirectSection(
                        incompatible.get(0).getCard(), groupName);
                if (redirect != null) {
                    Deck ownerDeck = CardGroupRegistry.findDeckOwnerForGroup(hostGroup);
                    if (ownerDeck != null) {
                        String sectionKey = redirect
                                .toLowerCase(java.util.Locale.ROOT)
                                .replace(" deck", "")
                                .trim();
                        CardsGroup altGroup =
                                CardGroupRegistry.getDeckSectionGroup(ownerDeck, sectionKey);
                        if (altGroup != null) {
                            javafx.collections.ObservableList<CardElement> altList =
                                    CardGroupRegistry.observableListFor(altGroup);
                            for (CardElement source : incompatible) {
                                CardElement newElement = new CardElement(source);
                                altList.add(newElement);
                                addedToAlt.add(newElement);
                            }
                            CardGroupRegistry.triggerHeightAdjustment(altGroup);
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
                CardGroupRegistry.observableListFor(hostGroup);
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
        CardGroupRegistry.triggerHeightAdjustment(hostGroup);
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
        OwnedCardsCollection owned = MenuActionHandler.safeGetOwnedCollection();
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
                CardGroupRegistry.observableListFor(targetGroup);
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
        CardGroupRegistry.triggerHeightAdjustment(targetGroup);
    }

    /**
     * Pastes {@code clipboardCards} immediately after {@code afterElement} in the
     * group that contains it in the {@link OwnedCardsCollection}.
     *
     * @param clipboardCards the cards to paste (not {@code null} or empty)
     * @param afterElement   the anchor element; the paste position is after this element
     * @deprecated Prefer
     * {@link #handlePasteElementsAfterElementInOwnedCollection(List, CardElement)}
     * to preserve per-copy fields such as condition and rarity.
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
        OwnedCardsCollection owned = MenuActionHandler.safeGetOwnedCollection();
        if (owned == null || owned.getOwnedCollection() == null) {
            return;
        }
        CardsGroup targetGroup = findGroupContainingElement(afterElement, owned);
        if (targetGroup == null) {
            logger.warn("doPasteAfterElementInOwnedCollection: group not found for element");
            return;
        }
        javafx.collections.ObservableList<CardElement> observableList =
                CardGroupRegistry.observableListFor(targetGroup);
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
        CardGroupRegistry.triggerHeightAdjustment(targetGroup);
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
     * {@code false} if the containing group was not found
     * @deprecated Prefer {@link #handleInsertElementsAfterElement(List, CardElement)}
     * to preserve per-copy fields such as condition and rarity.
     */
    @Deprecated
    public static boolean handleInsertCardsAfterElement(
            List<Card> cardsToInsert, CardElement afterElement) {
        if (cardsToInsert == null || cardsToInsert.isEmpty() || afterElement == null) {
            return false;
        }

        CardsGroup hostGroup = CardGroupRegistry.findGroupForCardElement(afterElement);
        if (hostGroup == null) {
            OwnedCardsCollection owned = MenuActionHandler.safeGetOwnedCollection();
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
                        CardGroupRegistry.observableListFor(hostGroup);
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
                CardGroupRegistry.triggerHeightAdjustment(hostGroup);
            }
            CardGroupRegistry.notifyOuicheListOfGroupAdditions(hostGroup, addedToHost);

            List<CardElement> addedToAlt = new ArrayList<>();
            if (!incompatible.isEmpty()) {
                String redirect = Utils.DeckCompatibility.redirectSection(
                        incompatible.get(0), groupName);
                if (redirect != null) {
                    Deck ownerDeck = CardGroupRegistry.findDeckOwnerForGroup(hostGroup);
                    if (ownerDeck != null) {
                        String sectionKey = redirect.toLowerCase(java.util.Locale.ROOT)
                                .replace(" deck", "").trim();
                        CardsGroup altGroup =
                                CardGroupRegistry.getDeckSectionGroup(ownerDeck, sectionKey);
                        if (altGroup != null) {
                            javafx.collections.ObservableList<CardElement> altList =
                                    CardGroupRegistry.observableListFor(altGroup);
                            for (Card card : incompatible) {
                                CardElement newElement = new CardElement(card);
                                altList.add(newElement);
                                addedToAlt.add(newElement);
                            }
                            CardGroupRegistry.triggerHeightAdjustment(altGroup);
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
                CardGroupRegistry.observableListFor(hostGroup);
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
        CardGroupRegistry.triggerHeightAdjustment(hostGroup);
        CardGroupRegistry.notifyOuicheListOfGroupAdditions(hostGroup, addedToGroup);
        return !addedToGroup.isEmpty();
    }
}