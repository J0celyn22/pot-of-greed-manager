package Utils;

import Model.CardsLists.Card;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Partition-based tests for {@link CardMatcher}.
 * <p>
 * The class javadoc describes matching "in order of reliability" (Konami ID,
 * then pass code, then print code), which reads like a priority hierarchy —
 * as if a firm mismatch on a higher-priority field should decide the result.
 * That's not what the code does: each tier only ever contributes a
 * short-circuit {@code true}; a tier that doesn't match (whether because the
 * field is null or because it disagrees) simply falls through to the next
 * tier, rather than the "in order of reliability" framing being an actual
 * fallback hierarchy. Concretely: two cards with two different, both-set
 * Konami IDs can still be reported as a match if their pass codes happen to
 * agree. See {@code cardsMatch_differingKonamiIds_stillMatchesOnPassCode}
 * below. Documenting this as current behavior, not changing it.
 */
class CardMatcherTest {

    private static Card cardWith(String konamiId, String passCode, String printCode) {
        Card card = new Card();
        card.setKonamiId(konamiId);
        card.setPassCode(passCode);
        card.setPrintCode(printCode);
        return card;
    }

    @Test
    void cardsMatch_bothNull_returnsFalse() {
        assertFalse(CardMatcher.cardsMatch(null, null));
    }

    @Test
    void cardsMatch_firstCardNull_returnsFalse() {
        assertFalse(CardMatcher.cardsMatch(null, cardWith("K1", "111", "LOB-EN001")));
    }

    @Test
    void cardsMatch_secondCardNull_returnsFalse() {
        assertFalse(CardMatcher.cardsMatch(cardWith("K1", "111", "LOB-EN001"), null));
    }

    @Test
    void cardsMatch_konamiIdMatch_returnsTrue() {
        Card a = cardWith("K1", null, null);
        Card b = cardWith("K1", null, null);
        assertTrue(CardMatcher.cardsMatch(a, b));
    }

    @Test
    void cardsMatch_differingKonamiIds_stillMatchesOnPassCode() {
        // Both Konami IDs are set and disagree, yet the method still reports
        // a match because it falls through to the pass-code check instead of
        // stopping at a definitive Konami ID mismatch.
        Card a = cardWith("K1", "111", null);
        Card b = cardWith("K2", "111", null);
        assertTrue(CardMatcher.cardsMatch(a, b));
    }

    @Test
    void cardsMatch_konamiIdsBothNull_fallsBackToPassCodeMatch() {
        Card a = cardWith(null, "111", null);
        Card b = cardWith(null, "111", null);
        assertTrue(CardMatcher.cardsMatch(a, b));
    }

    @Test
    void cardsMatch_konamiAndPassCodeUnmatched_fallsBackToPrintCodeMatch() {
        Card a = cardWith("K1", "111", "LOB-EN001");
        Card b = cardWith("K2", "222", "LOB-EN001");
        assertTrue(CardMatcher.cardsMatch(a, b));
    }

    @Test
    void cardsMatch_allThreeIdentifiersDiffer_returnsFalse() {
        Card a = cardWith("K1", "111", "LOB-EN001");
        Card b = cardWith("K2", "222", "LOB-EN002");
        assertFalse(CardMatcher.cardsMatch(a, b));
    }

    @Test
    void cardsMatch_allThreeIdentifiersNull_returnsFalse() {
        Card a = cardWith(null, null, null);
        Card b = cardWith(null, null, null);
        assertFalse(CardMatcher.cardsMatch(a, b));
    }

    @Test
    void cardsMatch_firstHasKonamiIdSecondDoesNot_noNullPointerException_fallsThrough() {
        // firstCard.getKonamiId() is non-null, secondCard.getKonamiId() is null;
        // equals(null) is safely false, so it falls through instead of crashing.
        Card a = cardWith("K1", "111", null);
        Card b = cardWith(null, "111", null);
        assertTrue(CardMatcher.cardsMatch(a, b), "falls through to a matching pass code");
    }

    @Test
    void cardsMatch_firstHasPassCodeSecondDoesNot_noNullPointerException_fallsThrough() {
        Card a = cardWith(null, "111", "LOB-EN001");
        Card b = cardWith(null, null, "LOB-EN001");
        assertTrue(CardMatcher.cardsMatch(a, b), "falls through to a matching print code");
    }
}