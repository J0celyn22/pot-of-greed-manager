package Controller;

import Model.CardsLists.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyCollectionQualityChecks — pure query/predicate helpers for detecting
 * unsorted cards, missing print codes, and incomplete cards (missing
 * condition or rarity) within a My Collection {@link Box} or {@link CardsGroup}.
 *
 * <p>All methods are stateless with respect to caller state and operate
 * exclusively on Model objects, aside from a position-aware cache used to
 * avoid recomputing sort-need results on every navigation-menu rebuild. This
 * class has no dependency on JavaFX and contains no UI code.</p>
 *
 * <p>Extracted from {@link MyCollectionController}, which retains the
 * tree-building, navigation drag-and-drop, rename, and scroll logic that
 * actually needs the shared TreeView, tab, and coordinator state. Nothing in
 * this class ever touched that state, so the cut required no dependency
 * threading.</p>
 */
public final class MyCollectionQualityChecks {

    private static final Logger logger = LoggerFactory.getLogger(MyCollectionQualityChecks.class);

    /**
     * Cached, position-aware sort-need results.
     * Key = container object (CardsGroup, Box, or raw list); value = per-index cache.
     */
    private static final ConcurrentHashMap<Object, List<Boolean>> positionSortCache =
            new ConcurrentHashMap<>();

    /**
     * Utility class — no instances.
     */
    private MyCollectionQualityChecks() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns {@code true} if {@code group} contains at least one card that needs
     * sorting according to its display name.
     *
     * @param group            the group to inspect
     * @param groupDisplayName the sanitized display name (no = or -)
     */
    public static boolean groupHasUnsortedCards(CardsGroup group, String groupDisplayName) {
        if (group == null || group.getCardList() == null) {
            return false;
        }

        // Check once whether this group's name is a known D&C name.
        // This gates the reason-4 check (degraded copy in D&C-named category) so we
        // don't call isDegradedCopyInDeckOrCollection on type groups like "Monstres Fusion".
        boolean isDeckOrCollGroup = false;
        try {
            isDeckOrCollGroup = CardQualityService.isDeckOrCollectionName(groupDisplayName);
        } catch (Throwable ignored) {
        }

        for (CardElement cardElement : group.getCardList()) {
            if (cardElement == null || cardElement.getCard() == null) {
                continue;
            }
            try {
                if (CardSortingRules.computeCardNeedsSorting(
                        cardElement.getCard(), groupDisplayName)) {
                    return true;
                }
                if (CardSortingRules.computeCardNeedsSortingWithUpgrade(
                        cardElement, groupDisplayName)) {
                    return true;
                }
                // Reason 4: card is in a D&C-named sorting category and a better
                // outside copy exists in the owned collection.
                // computeCardNeedsSortingWithUpgrade returns false for D&C-named groups
                // by design (its isDeckOrCollectionName guard), so we check separately.
                if (isDeckOrCollGroup) {
                    if (CardQualityService.isDegradedCopyInDeckOrCollection(
                            cardElement, groupDisplayName)) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code group} contains at least one card that needs
     * sorting (single-arg overload — uses the group's own name).
     */
    public static boolean groupHasUnsortedCards(CardsGroup group) {
        if (group == null) {
            return false;
        }
        List<CardElement> cardList;
        try {
            cardList = group.getCardList();
        } catch (Exception exception) {
            return false;
        }
        if (cardList == null || cardList.isEmpty()) {
            return false;
        }
        String groupName = group.getName() == null ? "" : group.getName();
        for (int index = 0; index < cardList.size(); index++) {
            CardElement cardElement = cardList.get(index);
            if (cardElement == null) {
                continue;
            }
            Card card = cardElement.getCard();
            if (card == null) {
                continue;
            }
            if (cardNeedsSortingCached(card, group, index, groupName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code box} or any nested sub-box/group contains
     * at least one card that needs sorting.
     *
     * @param box            the Box to inspect
     * @param boxDisplayName the sanitized display name (no =)
     */
    public static boolean boxHasUnsortedCards(Box box, String boxDisplayName) {
        if (box == null) {
            return false;
        }
        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                String rawGroupName = group.getName() == null ? "" : group.getName();
                String groupName = OwnedCardsCollection.extractName(rawGroupName, '-');
                if (groupHasUnsortedCards(group, groupName)) {
                    return true;
                }
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box subBox : box.getSubBoxes()) {
                String subBoxDisplayName = OwnedCardsCollection.extractName(
                        subBox.getName() == null ? "" : subBox.getName(), '=');
                if (boxHasUnsortedCards(subBox, subBoxDisplayName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code box} or any of its groups/sub-boxes contains
     * at least one card that needs sorting (single-arg overload using reflection).
     */
    public static boolean boxHasUnsortedCards(Box box) {
        if (box == null) {
            return false;
        }
        String boxName = box.getName() == null ? "" : box.getName();

        try {
            Method getCardListMethod = null;
            try {
                getCardListMethod = box.getClass().getMethod("getCardList");
            } catch (NoSuchMethodException ignored) {
            }

            if (getCardListMethod != null) {
                Object maybeList = getCardListMethod.invoke(box);
                if (maybeList instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<?> rawList = (List<?>) maybeList;
                    if (containsUnsortedFromRawList(rawList, boxName)) {
                        return true;
                    }
                }
            } else {
                Field cardListField = null;
                try {
                    cardListField = box.getClass().getDeclaredField("cardList");
                } catch (NoSuchFieldException firstException) {
                    try {
                        cardListField = box.getClass().getDeclaredField("cardsList");
                    } catch (NoSuchFieldException ignored) {
                    }
                }
                if (cardListField != null) {
                    cardListField.setAccessible(true);
                    Object maybeList = cardListField.get(box);
                    if (maybeList instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<?> rawList = (List<?>) maybeList;
                        if (containsUnsortedFromRawList(rawList, boxName)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception exception) {
            logger.debug("boxHasUnsortedCards: reflection check failed for box '{}': {}",
                    boxName, exception.toString());
        }

        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                if (groupHasUnsortedCards(group)) {
                    return true;
                }
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box subBox : box.getSubBoxes()) {
                if (subBox != null && boxHasUnsortedCards(subBox)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when {@code group} has a card whose {@link Card#getPrintCode()}
     * is null or blank.
     */
    public static boolean groupHasMissingPrintCode(CardsGroup group) {
        if (group == null || group.getCardList() == null) {
            return false;
        }
        for (CardElement cardElement : group.getCardList()) {
            if (cardElement == null || cardElement.getCard() == null) {
                continue;
            }
            Card card = cardElement.getCard();
            if (card.getPrintCode() == null || card.getPrintCode().isBlank()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when {@code group} has a card that has a printCode
     * but is missing condition or rarity.
     */
    public static boolean groupHasIncompleteCards(CardsGroup group) {
        if (group == null || group.getCardList() == null) {
            return false;
        }
        for (CardElement cardElement : group.getCardList()) {
            if (cardElement == null || cardElement.getCard() == null) {
                continue;
            }
            Card card = cardElement.getCard();
            if (card.getPrintCode() == null || card.getPrintCode().isBlank()) {
                continue;
            }
            if (cardElement.getCondition() == null || cardElement.getRarity() == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when {@code box} or any nested sub-box/group has a
     * card with a missing printCode.
     */
    public static boolean boxHasMissingPrintCode(Box box) {
        if (box == null) {
            return false;
        }
        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                if (groupHasMissingPrintCode(group)) {
                    return true;
                }
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box subBox : box.getSubBoxes()) {
                if (subBox != null && boxHasMissingPrintCode(subBox)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when {@code box} or any nested sub-box/group has a
     * card that has a printCode but is missing condition or rarity.
     */
    public static boolean boxHasIncompleteCards(Box box) {
        if (box == null) {
            return false;
        }
        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                if (groupHasIncompleteCards(group)) {
                    return true;
                }
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box subBox : box.getSubBoxes()) {
                if (subBox != null && boxHasIncompleteCards(subBox)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Cached, position-aware wrapper that returns whether the card at a given
     * index inside a container needs sorting.
     */
    private static boolean cardNeedsSortingCached(Card card, Object container, int index,
                                                  String elementName) {
        if (card == null) {
            return false;
        }
        if (container == null || index < 0) {
            return CardSortingRules.computeCardNeedsSorting(card, elementName);
        }

        List<Boolean> cacheList = positionSortCache.computeIfAbsent(container,
                key -> Collections.synchronizedList(new ArrayList<>()));

        synchronized (cacheList) {
            while (cacheList.size() <= index) {
                cacheList.add(null);
            }
            Boolean cached = cacheList.get(index);
            if (cached != null) {
                return cached;
            }
        }

        boolean result = CardSortingRules.computeCardNeedsSorting(card, elementName);

        synchronized (cacheList) {
            cacheList.set(index, result);
        }
        return result;
    }

    /**
     * Scans a raw (untyped) list for any entry that needs sorting.
     */
    private static boolean containsUnsortedFromRawList(List<?> rawList, String elementName) {
        if (rawList == null || rawList.isEmpty()) {
            return false;
        }
        for (int index = 0; index < rawList.size(); index++) {
            Object entry = rawList.get(index);
            if (entry == null) {
                continue;
            }
            Card card = null;
            if (entry instanceof CardElement) {
                card = ((CardElement) entry).getCard();
            } else if (entry instanceof Card) {
                card = (Card) entry;
            } else {
                continue;
            }
            if (card == null) {
                continue;
            }
            if (cardNeedsSortingCached(card, rawList, index, elementName)) {
                return true;
            }
        }
        return false;
    }
}