package Utils;

import Controller.CardQualityService;
import Model.CardsLists.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only query helpers that operate over card lists and the owned collection.
 *
 * <p>These methods were previously duplicated between {@link View.CardTreeCell}
 * and {@link View.CardGridCellContextMenuBuilder}. They contain no View or UI
 * logic and belong here so callers in both View classes share a single
 * implementation.</p>
 */
public final class CardCollectionQuery {

    private CardCollectionQuery() {
    }

    /**
     * Counts how many {@link CardElement}s in {@code cardList} refer to the
     * same card as {@code card} (matched via {@link CardMatcher#cardsMatch}).
     *
     * @param cardList the list to search (may be {@code null})
     * @param card     the card to count (may be {@code null})
     * @return the number of matching elements, or {@code 0} if either argument
     * is {@code null}
     */
    public static int countCardInList(List<CardElement> cardList, Card card) {
        if (cardList == null || card == null) {
            return 0;
        }
        int count = 0;
        for (CardElement cardElement : cardList) {
            if (cardElement == null || cardElement.getCard() == null) {
                continue;
            }
            if (CardMatcher.cardsMatch(cardElement.getCard(), card)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts how many copies of {@code card} the player owns that are relevant
     * to the given deck name, searching the owned collection with the following
     * priority:
     *
     * <ol>
     *   <li>A {@link Box} whose name matches {@code deckName} — sum all groups
     *       inside it.</li>
     *   <li>A group inside any box whose name matches {@code deckName} — sum
     *       only that group (avoids inflating counts with unrelated groups, which
     *       was Bug 3A).</li>
     *   <li>Groups named "Main Deck", "Extra Deck", or "Side Deck" in any box
     *       that also matches {@code deckName}.</li>
     *   <li>Fallback: any group named like {@code deckName} anywhere in the
     *       collection.</li>
     * </ol>
     *
     * @param owned    the owned collection (may be {@code null})
     * @param deckName the deck display name to look up (may be {@code null})
     * @param card     the card to count (may be {@code null})
     * @return the count of owned copies relevant to the deck, or {@code 0} if
     * any required argument is {@code null}/empty
     */
    public static int countInOwnedForDeckCombined(
            OwnedCardsCollection owned, String deckName, Card card) {
        if (owned == null
                || owned.getOwnedCollection() == null
                || deckName == null
                || deckName.trim().isEmpty()) {
            return 0;
        }
        String deckNorm = CardNameUtils.sanitize(deckName).toLowerCase();
        String mainNorm = CardNameUtils.sanitize("Main Deck").toLowerCase();
        String extraNorm = CardNameUtils.sanitize("Extra Deck").toLowerCase();
        String sideNorm = CardNameUtils.sanitize("Side Deck").toLowerCase();

        // 1) Box name matches deck name — sum all groups inside that box.
        for (Box box : owned.getOwnedCollection()) {
            if (box == null) {
                continue;
            }
            if (CardNameUtils.sanitize(box.getName()).toLowerCase().equals(deckNorm)) {
                int sum = 0;
                if (box.getContent() != null) {
                    for (CardsGroup group : box.getContent()) {
                        if (group != null) {
                            sum += countCardInList(group.getCardList(), card);
                        }
                    }
                }
                return sum;
            }
        }

        // 2) A group inside any box whose name matches the deck — sum only that group.
        // Summing all groups in the box was Bug 3A: cards in unrelated groups like
        // "Unsorted" or "Fusion Monsters" would inflate the count and hide a real deficit.
        for (Box box : owned.getOwnedCollection()) {
            if (box == null || box.getContent() == null) {
                continue;
            }
            int sum = 0;
            boolean hasDeckGroup = false;
            for (CardsGroup group : box.getContent()) {
                if (group == null) {
                    continue;
                }
                if (CardNameUtils.sanitize(group.getName()).toLowerCase().equals(deckNorm)) {
                    hasDeckGroup = true;
                    sum += countCardInList(group.getCardList(), card);
                }
            }
            if (hasDeckGroup) {
                return sum;
            }
        }

        // 3) Groups named Main/Extra/Side Deck inside a box that matches the deck name.
        int sumLists = 0;
        for (Box box : owned.getOwnedCollection()) {
            if (box == null || box.getContent() == null) {
                continue;
            }
            boolean boxMatchesDeck =
                    CardNameUtils.sanitize(box.getName()).toLowerCase().equals(deckNorm);
            boolean anyListFoundInBox = false;
            for (CardsGroup group : box.getContent()) {
                if (group == null) {
                    continue;
                }
                String groupNorm = CardNameUtils.sanitize(group.getName()).toLowerCase();
                if (groupNorm.equals(mainNorm)
                        || groupNorm.equals(extraNorm)
                        || groupNorm.equals(sideNorm)) {
                    sumLists += countCardInList(group.getCardList(), card);
                    anyListFoundInBox = true;
                }
            }
            // Prefer the lists from the box that directly matches the deck name.
            if (boxMatchesDeck && anyListFoundInBox) {
                return sumLists;
            }
        }
        if (sumLists > 0) {
            return sumLists;
        }

        // 4) Fallback: sum any group named like the deck anywhere in the collection.
        int fallbackSum = 0;
        for (Box box : owned.getOwnedCollection()) {
            if (box == null || box.getContent() == null) {
                continue;
            }
            for (CardsGroup group : box.getContent()) {
                if (group == null) {
                    continue;
                }
                if (CardNameUtils.sanitize(group.getName()).toLowerCase().equals(deckNorm)) {
                    fallbackSum += countCardInList(group.getCardList(), card);
                }
            }
        }
        return fallbackSum;
    }

    /**
     * Returns {@code true} if {@code ownedElement} would be a quality upgrade
     * for at least one copy of the same card already present in {@code targetList}.
     *
     * <p>Delegates to {@link CardQualityService#isQualityUpgrade} after collecting
     * the existing copies of the card from {@code targetList}.</p>
     *
     * @param targetList   the list that already contains copies of the card
     *                     (may be {@code null})
     * @param ownedElement the candidate replacement element (may be {@code null})
     * @return {@code true} if at least one existing copy would be upgraded
     */
    public static boolean isQualityUpgradeFor(
            List<CardElement> targetList, CardElement ownedElement) {
        if (ownedElement == null || targetList == null) {
            return false;
        }
        Card card = ownedElement.getCard();
        if (card == null) {
            return false;
        }
        List<CardElement> existingCopies = new ArrayList<>();
        for (CardElement existing : targetList) {
            if (existing == null || existing.getCard() == null) {
                continue;
            }
            if (CardMatcher.cardsMatch(existing.getCard(), card)) {
                existingCopies.add(existing);
            }
        }
        if (existingCopies.isEmpty()) {
            return false;
        }
        return CardQualityService.isQualityUpgrade(existingCopies, targetList, ownedElement);
    }
}