package Utils;

import Model.CardsLists.Card;

/**
 * Utility methods for determining whether two {@link Card} instances represent
 * the same physical card.
 *
 * <p>Matching is performed in order of reliability:
 * <ol>
 *   <li>Konami ID (most unique identifier)</li>
 *   <li>Pass code</li>
 *   <li>Print code</li>
 * </ol>
 *
 * <p>A {@code null} value on either side is never treated as a match.
 */
public final class CardMatcher {

    private CardMatcher() {
    }

    /**
     * Returns {@code true} when {@code firstCard} and {@code secondCard} represent
     * the same physical card, matched by Konami ID, pass code, or print code.
     *
     * @param firstCard  the first card (may be {@code null})
     * @param secondCard the second card (may be {@code null})
     * @return {@code true} if both cards share at least one matching identifier
     */
    public static boolean cardsMatch(Card firstCard, Card secondCard) {
        if (firstCard == null || secondCard == null) {
            return false;
        }
        if (firstCard.getKonamiId() != null
                && firstCard.getKonamiId().equals(secondCard.getKonamiId())) {
            return true;
        }
        if (firstCard.getPassCode() != null
                && firstCard.getPassCode().equals(secondCard.getPassCode())) {
            return true;
        }
        if (firstCard.getPrintCode() != null
                && firstCard.getPrintCode().equals(secondCard.getPrintCode())) {
            return true;
        }
        return false;
    }
}