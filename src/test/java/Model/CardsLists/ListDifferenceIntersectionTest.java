package Model.CardsLists;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Partition-based tests for {@link ListDifferenceIntersection}.
 * <p>
 * Two genuinely different core algorithms live here under overloaded names,
 * so they're tested separately:
 * <ul>
 *   <li>The 5-arg {@code ListDifIntersect} <b>removes</b> matched elements
 *       from both lists and returns a separate intersection list.</li>
 *   <li>The 6-arg {@code ListDifIntersect} (with a {@code character} marker)
 *       never removes anything — both lists stay the same size — and instead
 *       <b>mutates matched elements in place</b> via
 *       {@link CardElement#setValues(String)}, marking them with
 *       {@code character}. Because the returned lists are shallow copies,
 *       this mutation is also visible on the caller's original input lists.</li>
 * </ul>
 */
class ListDifferenceIntersectionTest {

    private static final BiPredicate<Card, Card> SAME_KONAMI_ID =
            (c1, c2) -> c1.getKonamiId().equals(c2.getKonamiId());
    private static final BiPredicate<Card, Card> ALWAYS_MATCH = (c1, c2) -> true;

    private static CardElement basicCard(String passCode, String konamiId) {
        Card card = new Card();
        card.setPassCode(passCode);
        card.setKonamiId(konamiId);
        return new CardElement(card);
    }

    private static CardElement flaggedCard(String passCode, String konamiId,
                                           boolean specificArtwork, boolean isOwned,
                                           boolean dontRemove, boolean isInDeck) {
        Card card = new Card();
        card.setPassCode(passCode);
        card.setKonamiId(konamiId);
        if (specificArtwork) card.setArtNumber("2");
        return new CardElement(card, specificArtwork, isOwned, dontRemove, isInDeck);
    }

    // ══════════════════════════════════════════════════════════════════════
    // 5-arg ListDifIntersect — removal + separate intersection list
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void fiveArg_matchingKonamiId_removedFromBothAndAddedToIntersection() {
        CardElement a = basicCard("111", "K1");
        CardElement b = basicCard("111", "K1");

        List<List<CardElement>> result = ListDifferenceIntersection.ListDifIntersect(
                List.of(a), List.of(b), SAME_KONAMI_ID, null, null);

        assertTrue(result.get(0).isEmpty(), "A-minus-B");
        assertTrue(result.get(1).isEmpty(), "B-minus-A");
        assertEquals(List.of(b), result.get(2), "intersection contains the B-side instance");
    }

    @Test
    void fiveArg_noMatch_bothRemainInMinusLists() {
        CardElement a = basicCard("111", "K1");
        CardElement b = basicCard("222", "K2");

        List<List<CardElement>> result = ListDifferenceIntersection.ListDifIntersect(
                List.of(a), List.of(b), SAME_KONAMI_ID, null, null);

        assertEquals(List.of(a), result.get(0));
        assertEquals(List.of(b), result.get(1));
        assertTrue(result.get(2).isEmpty());
    }

    @Test
    void fiveArg_nullKonamiIdOnA_neverMatchedRegardlessOfComparator() {
        // ALWAYS_MATCH would match anything, but a null Konami ID on the A
        // side excludes the element before the comparator is ever consulted.
        CardElement a = basicCard("111", null);
        CardElement b = basicCard("111", "K1");

        List<List<CardElement>> result = ListDifferenceIntersection.ListDifIntersect(
                List.of(a), List.of(b), ALWAYS_MATCH, null, null);

        assertEquals(List.of(a), result.get(0));
        assertEquals(List.of(b), result.get(1));
        assertTrue(result.get(2).isEmpty());
    }

    @Test
    void fiveArg_nullKonamiIdOnB_neverMatchedRegardlessOfComparator() {
        CardElement a = basicCard("111", "K1");
        CardElement b = basicCard("111", null);

        List<List<CardElement>> result = ListDifferenceIntersection.ListDifIntersect(
                List.of(a), List.of(b), ALWAYS_MATCH, null, null);

        assertEquals(List.of(a), result.get(0));
        assertEquals(List.of(b), result.get(1));
        assertTrue(result.get(2).isEmpty());
    }

    @Test
    void fiveArg_mustContain_gatesOnValueA_onlyNotValueB() {
        // A is owned (toString contains "O"); B is not owned but still
        // matches on Konami ID. mustContain=["O"] is only ever checked
        // against valueA, so B's own flags are irrelevant to the gate.
        CardElement a = flaggedCard("111", "K1", false, true, false, false);
        CardElement b = flaggedCard("111", "K1", false, false, false, false);

        List<List<CardElement>> result = ListDifferenceIntersection.ListDifIntersect(
                List.of(a), List.of(b), SAME_KONAMI_ID, List.of("O"), null);

        assertTrue(result.get(0).isEmpty());
        assertTrue(result.get(1).isEmpty());
        assertEquals(List.of(b), result.get(2));
    }

    @Test
    void fiveArg_mustContain_excludesAWhenAbsent_evenWithMatchingB() {
        CardElement a = basicCard("111", "K1"); // not owned, no "O" in toString
        CardElement b = basicCard("111", "K1");

        List<List<CardElement>> result = ListDifferenceIntersection.ListDifIntersect(
                List.of(a), List.of(b), SAME_KONAMI_ID, List.of("O"), null);

        assertEquals(List.of(a), result.get(0), "A never entered the match search");
        assertEquals(List.of(b), result.get(1));
        assertTrue(result.get(2).isEmpty());
    }

    @Test
    void fiveArg_mustNotContain_excludesAWhenPresent() {
        CardElement a = flaggedCard("111", "K1", false, false, true, false); // dontRemove -> "+"
        CardElement b = basicCard("111", "K1");

        List<List<CardElement>> result = ListDifferenceIntersection.ListDifIntersect(
                List.of(a), List.of(b), SAME_KONAMI_ID, null, List.of("+"));

        assertEquals(List.of(a), result.get(0));
        assertEquals(List.of(b), result.get(1));
        assertTrue(result.get(2).isEmpty());
    }

    @Test
    void fiveArg_duplicates_twoInA_oneInB_onlyFirstAConsumed() {
        CardElement a1 = basicCard("111", "K1");
        CardElement a2 = basicCard("111", "K1");
        CardElement b1 = basicCard("111", "K1");

        List<List<CardElement>> result = ListDifferenceIntersection.ListDifIntersect(
                new ArrayList<>(List.of(a1, a2)), new ArrayList<>(List.of(b1)), SAME_KONAMI_ID, null, null);

        assertEquals(List.of(a2), result.get(0), "second A copy has no B left to match");
        assertTrue(result.get(1).isEmpty());
        assertEquals(List.of(b1), result.get(2));
    }

    @Test
    void fiveArg_duplicates_oneInA_twoInB_onlyOneBConsumed() {
        CardElement a1 = basicCard("111", "K1");
        CardElement b1 = basicCard("111", "K1");
        CardElement b2 = basicCard("111", "K1");

        List<List<CardElement>> result = ListDifferenceIntersection.ListDifIntersect(
                new ArrayList<>(List.of(a1)), new ArrayList<>(List.of(b1, b2)), SAME_KONAMI_ID, null, null);

        assertTrue(result.get(0).isEmpty());
        assertEquals(List.of(b2), result.get(1), "second B copy is never claimed");
        assertEquals(List.of(b1), result.get(2));
    }

    @Test
    void fiveArg_emptyLists_noCrashEmptyResults() {
        List<List<CardElement>> result = ListDifferenceIntersection.ListDifIntersect(
                new ArrayList<>(), new ArrayList<>(), SAME_KONAMI_ID, null, null);

        assertTrue(result.get(0).isEmpty());
        assertTrue(result.get(1).isEmpty());
        assertTrue(result.get(2).isEmpty());
    }

    // ══════════════════════════════════════════════════════════════════════
    // 6-arg ListDifIntersect — in-place marking, no removal, size-invariant
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void sixArg_listsNeverShrink_matchedElementsAreMarkedNotRemoved() {
        CardElement a = basicCard("111", "K1");
        CardElement b = basicCard("111", "K1");
        List<CardElement> listA = new ArrayList<>(List.of(a));
        List<CardElement> listB = new ArrayList<>(List.of(b));

        List<List<CardElement>> result = ListDifferenceIntersection.ListDifIntersect(
                listA, listB, SAME_KONAMI_ID, null, null, "D");

        assertEquals(1, result.get(0).size(), "matched elements are marked, not removed");
        assertEquals(1, result.get(1).size());
    }

    @Test
    void sixArg_matchMutatesOriginalListInPlace_sharedReference() {
        // The returned lists are shallow copies, so setValues() on the
        // matched element is visible through the caller's original list too.
        CardElement a = basicCard("111", "K1");
        CardElement b = basicCard("111", "K1");
        List<CardElement> listA = new ArrayList<>(List.of(a));
        List<CardElement> listB = new ArrayList<>(List.of(b));

        assertEquals(false, a.getIsInDeck());
        ListDifferenceIntersection.ListDifIntersect(listA, listB, SAME_KONAMI_ID, null, null, "D");

        assertEquals(true, a.getIsInDeck(), "marking with \"D\" also flips isInDeck on the original A object");
        assertEquals(true, b.getIsInDeck(), "and on the original B object");
    }

    @Test
    void sixArg_alreadyMarkedElement_isSkipped_notReMatched() {
        // A already carries the "D" marker in its toString(); the
        // !valueA.contains(character) guard must exclude it from matching.
        CardElement a = flaggedCard("111", "K1", false, false, false, true); // isInDeck=true -> "D" already present
        CardElement b = basicCard("111", "K1");
        List<CardElement> listA = new ArrayList<>(List.of(a));
        List<CardElement> listB = new ArrayList<>(List.of(b));

        List<List<CardElement>> result = ListDifferenceIntersection.ListDifIntersect(
                listA, listB, SAME_KONAMI_ID, null, null, "D");

        assertEquals(OwnershipStatus.MISSING, result.get(0).get(0).getOwnershipStatus(),
                "A was already marked, so it must not have been (re)matched/mutated further");
    }

    @Test
    void sixArg_characterDoublesAsFlag_ownedMarkerAlsoSetsOwnershipStatus() {
        // Real production usage passes "O" as the marker specifically so
        // that marking-as-matched and marking-as-owned are the same
        // operation, via CardElement#setValues re-parsing the whole string.
        CardElement a = basicCard("111", "K1");
        CardElement b = basicCard("111", "K1");
        List<CardElement> listA = new ArrayList<>(List.of(a));
        List<CardElement> listB = new ArrayList<>(List.of(b));

        ListDifferenceIntersection.ListDifIntersect(listA, listB, SAME_KONAMI_ID, null, null, "O");

        assertEquals(OwnershipStatus.OWNED, a.getOwnershipStatus());
        assertEquals(OwnershipStatus.OWNED, b.getOwnershipStatus());
    }

    @Test
    void sixArg_nonFlagCharacterOnSpecificArtworkCard_currentlyThrowsNumberFormatException() {
        // Documents a real fragility: real call sites only ever use "O", "D"
        // or "+" as the marker (all pre-stripped by setValues before the
        // artwork branch runs). An arbitrary marker like "X" on a
        // specific-artwork element corrupts the "*<number>" segment that
        // setValues tries to parse, crashing the whole pass. Not fixing this
        // here — it's a production-code change, and every current caller
        // avoids it by construction.
        CardElement a = flaggedCard("111", "K1", true, false, false, false); // specificArtwork -> "*2" in toString
        CardElement b = basicCard("111", "K1");
        List<CardElement> listA = new ArrayList<>(List.of(a));
        List<CardElement> listB = new ArrayList<>(List.of(b));

        assertThrows(NumberFormatException.class, () ->
                ListDifferenceIntersection.ListDifIntersect(listA, listB, SAME_KONAMI_ID, null, null, "X"));
    }

    @Test
    void sixArg_usedCardsInB_preventsSecondAFromReusingSameBMatch() {
        CardElement a1 = basicCard("111", "K1");
        CardElement a2 = basicCard("111", "K1");
        CardElement b1 = basicCard("111", "K1");
        List<CardElement> listA = new ArrayList<>(List.of(a1, a2));
        List<CardElement> listB = new ArrayList<>(List.of(b1));

        ListDifferenceIntersection.ListDifIntersect(listA, listB, SAME_KONAMI_ID, null, null, "D");

        assertEquals(true, a1.getIsInDeck(), "first A consumed the only B match");
        assertEquals(false, a2.getIsInDeck(), "second A found no remaining, unused B match");
        assertEquals(true, b1.getIsInDeck());
    }

    // ══════════════════════════════════════════════════════════════════════
    // Representative convenience wrappers
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void wrapper_listDifIntersectPassCode_matchesOnPassCodeEquality() {
        CardElement a = basicCard("111", "K1");
        CardElement b = basicCard("111", "K2"); // different Konami ID, same passCode

        List<List<CardElement>> result = ListDifferenceIntersection.ListDifIntersectPassCode(
                List.of(a), List.of(b));

        assertTrue(result.get(0).isEmpty());
        assertTrue(result.get(1).isEmpty());
        assertEquals(List.of(b), result.get(2));
    }

    @Test
    void wrapper_listDifIntersectArtworkWithExceptions_requiresArtworkMarker_excludesDontRemove() {
        // Fixed presets: mustContain=["*"], mustNotContain=["+"].
        CardElement noArtworkMarker = basicCard("111", "K1"); // toString has no "*" -> excluded before the comparator runs
        CardElement dontRemoveWithArtwork = flaggedCard("222", "K2", true, false, true, false); // "*2+" -> excluded by mustNotContain
        CardElement eligible = flaggedCard("333", "K3", true, false, false, false); // "*2", no "+" -> genuinely eligible

        noArtworkMarker.getCard().setImagePath("img/1.png");
        dontRemoveWithArtwork.getCard().setImagePath("img/2.png");
        eligible.getCard().setImagePath("img/3.png");

        CardElement b1 = basicCard("111", "K1");
        b1.getCard().setImagePath("img/1.png");
        CardElement b2 = basicCard("222", "K2");
        b2.getCard().setImagePath("img/2.png");
        CardElement b3 = basicCard("333", "K3");
        b3.getCard().setImagePath("img/3.png");

        List<List<CardElement>> result = ListDifferenceIntersection.ListDifIntersectArtworkWithExceptions(
                List.of(noArtworkMarker, dontRemoveWithArtwork, eligible), List.of(b1, b2, b3));

        // The first two never reach the comparator at all (excluded by the mustContain/
        // mustNotContain gate); only the third, genuinely eligible pair actually matches
        // on image path and lands in the intersection.
        assertEquals(List.of(noArtworkMarker, dontRemoveWithArtwork), result.get(0));
        assertEquals(List.of(b1, b2), result.get(1));
        assertEquals(List.of(b3), result.get(2));
    }
}