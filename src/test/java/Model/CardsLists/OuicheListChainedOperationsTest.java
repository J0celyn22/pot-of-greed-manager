package Model.CardsLists;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Operations performed immediately on a card that was itself just added or moved, in
 * the same sequence of calls a real drag-drop gesture (or a duplicate/re-entrant event)
 * would make. These are distinct from the steady-state tests elsewhere in this suite
 * because the card involved hasn't "settled" — its detailed-list slot is the exact same
 * live object that was just touched by the previous call, which is precisely the
 * condition that produced a self-matching bug, now fixed and documented in
 * {@link OuicheListEdgeCasesTest#crossGroupMove_substandardCard_preservesStatus_regressionGuard}.
 */
public class OuicheListChainedOperationsTest {

    private Card cardA;
    private Deck deckA;
    private Deck deckB;
    private Deck deckC;

    private static Card card(String konamiId) {
        Card c = new Card();
        c.setKonamiId(konamiId);
        c.setPassCode(konamiId);
        c.setImagePath("img/" + konamiId + ".jpg");
        return c;
    }

    @BeforeEach
    void setUp() {
        cardA = card("KID-701");

        deckA = new Deck();
        deckA.setName("A");
        deckB = new Deck();
        deckB.setName("B");
        deckC = new Deck();
        deckC.setName("C");

        DecksAndCollectionsList detailed = new DecksAndCollectionsList();
        detailed.addDeck(deckA);
        detailed.addDeck(deckB);
        detailed.addDeck(deckC);

        OuicheList.setDetailedOuicheList(detailed);
        OuicheList.setUnusedCards(new ArrayList<>());
        OuicheList.setMaOuicheList(new LinkedHashMap<>());
        OuicheList.setMaOuicheListCounts(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandard(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandardCounts(new LinkedHashMap<>());
        OuicheList.setMyCardsCollection(new OwnedCardsCollection());
    }

    // =========================================================================
    // Regression guard: onDeckCardAdded must be idempotent for a freshly-added slot.
    // =========================================================================

    /**
     * Regression guard for a bug found while writing this suite (fixed in
     * {@code OuicheListUpdater}): calling {@code onDeckCardAdded} twice with the exact
     * same, already-inserted slot object (a duplicate event firing, or any accidental
     * re-entrant call) used to burn a second real owned copy from
     * {@link OuicheList#getUnusedCards()} even though nothing new was added to the
     * section — the reference-based {@code targetSection.contains(addedCard)} guard
     * correctly stopped a second insertion, but the ownership-matching logic below it
     * ran unconditionally regardless of whether an insertion actually happened. The fix
     * makes the whole method a no-op (not just the insertion) when the slot is already
     * present.
     */
    @Test
    void deckCardAdded_calledTwiceOnSameFreshSlot_isIdempotent() {
        OuicheList.getUnusedCards().add(new CardElement(cardA));
        OuicheList.getUnusedCards().add(new CardElement(cardA));

        CardElement slot = new CardElement(cardA);
        OuicheList.onDeckCardAdded(slot, "A", "main", null, Integer.MAX_VALUE);
        assertEquals(OwnershipStatus.OWNED, slot.getOwnershipStatus());
        assertEquals(1, OuicheList.getUnusedCards().size(), "First add should consume exactly one copy");

        OuicheList.onDeckCardAdded(slot, "A", "main", null, Integer.MAX_VALUE);

        assertEquals(1, deckA.getMainDeck().size(),
                "The slot must not be inserted a second time");
        assertEquals(1, OuicheList.getUnusedCards().size(),
                "A second call on the same already-added slot must not consume another "
                        + "copy from the pool");
    }

    // =========================================================================
    // Remove immediately after Add — should be a clean net no-op.
    // =========================================================================

    @Test
    void deckCardRemoved_immediatelyAfterAdd_isCleanNoOp() {
        OuicheList.getUnusedCards().add(new CardElement(cardA));

        CardElement slot = new CardElement(cardA);
        OuicheList.onDeckCardAdded(slot, "A", "main", null, Integer.MAX_VALUE);
        OuicheList.onDeckCardRemoved(slot, "A", "main", null);

        assertTrue(deckA.getMainDeck().isEmpty());
        assertEquals(1, OuicheList.getUnusedCards().size(),
                "The consumed copy should be returned, ending up exactly where it started");
    }

    // =========================================================================
    // Move immediately after Add.
    // =========================================================================

    @Test
    void deckCardMoved_immediatelyAfterAdd_ownedCard_landsCorrectlyInNewDeck() {
        OuicheList.getUnusedCards().add(new CardElement(cardA));

        CardElement slot = new CardElement(cardA);
        OuicheList.onDeckCardAdded(slot, "A", "main", null, Integer.MAX_VALUE);
        OuicheList.onDeckCardRemoved(slot, "A", "main", null);
        OuicheList.onDeckCardAdded(slot, "B", "main", null, Integer.MAX_VALUE);

        assertEquals(OwnershipStatus.OWNED, slot.getOwnershipStatus());
        assertTrue(deckA.getMainDeck().isEmpty());
        assertTrue(deckB.getMainDeck().contains(slot));
        assertEquals(0, OuicheList.getUnusedCards().size());
    }

    // =========================================================================
    // Remove immediately after Move — confirms the fix for the self-match bug
    // (see OuicheListEdgeCasesTest) holds up under a further chained operation:
    // the SUBSTANDARD status now correctly survives the move, and removing it
    // immediately afterward is still a clean, exception-free, fully-accounted-for
    // operation.
    // =========================================================================

    @Test
    void deckCardRemoved_immediatelyAfterMove_ofASubstandardCard_remainsConsistent() {
        CardElement slot = new CardElement(cardA);
        slot.setCondition(CardCondition.NEAR_MINT);
        slot.setOwnershipStatus(OwnershipStatus.OWNED_SUBSTANDARD);
        deckA.getMainDeck().add(slot);

        OuicheList.onDeckCardRemoved(slot, "A", "main", null);
        OuicheList.onDeckCardAdded(slot, "B", "main", null, Integer.MAX_VALUE);
        assertEquals(OwnershipStatus.OWNED_SUBSTANDARD, slot.getOwnershipStatus(),
                "Fixed: the move must not upgrade it to OWNED");

        assertDoesNotThrow(() -> OuicheList.onDeckCardRemoved(slot, "B", "main", null));

        assertTrue(deckB.getMainDeck().isEmpty());
        assertEquals(1, OuicheList.getUnusedCards().size(),
                "The freed copy should still return cleanly to the pool");
    }

    // =========================================================================
    // Double-hop move (A -> B -> C) — confirms the fix holds across repeated hops:
    // SUBSTANDARD status survives both moves, doesn't get lost, doesn't duplicate
    // the slot, doesn't throw, doesn't leak unusedCards entries.
    // =========================================================================

    @Test
    void deckCardMoved_twoConsecutiveHops_substandardCard_statusSurvivesBothHops() {
        CardElement slot = new CardElement(cardA);
        slot.setCondition(CardCondition.NEAR_MINT);
        slot.setOwnershipStatus(OwnershipStatus.OWNED_SUBSTANDARD);
        deckA.getMainDeck().add(slot);

        OuicheList.onDeckCardRemoved(slot, "A", "main", null);
        OuicheList.onDeckCardAdded(slot, "B", "main", null, Integer.MAX_VALUE);
        assertEquals(OwnershipStatus.OWNED_SUBSTANDARD, slot.getOwnershipStatus());

        OuicheList.onDeckCardRemoved(slot, "B", "main", null);
        OuicheList.onDeckCardAdded(slot, "C", "main", null, Integer.MAX_VALUE);

        assertEquals(OwnershipStatus.OWNED_SUBSTANDARD, slot.getOwnershipStatus(),
                "Should remain SUBSTANDARD, unchanged, across a second hop");
        assertTrue(deckA.getMainDeck().isEmpty());
        assertTrue(deckB.getMainDeck().isEmpty());
        assertEquals(1, deckC.getMainDeck().size(), "Exactly one slot, not duplicated across hops");
        assertTrue(OuicheList.getUnusedCards().isEmpty(), "No stray pool entries left behind");
    }

    // =========================================================================
    // Reorder immediately after Add.
    // =========================================================================

    @Test
    void deckCardMoved_reorderImmediatelyAfterAdd_preservesStatusAndRelocatesCorrectly() {
        deckA.getMainDeck().add(new CardElement(card("KID-702")));
        OuicheList.getUnusedCards().add(new CardElement(cardA));

        CardElement slot = new CardElement(cardA);
        OuicheList.onDeckCardAdded(slot, "A", "main", null, 0);
        assertEquals(OwnershipStatus.OWNED, slot.getOwnershipStatus());

        OuicheList.onDeckCardMoved(new CardElement(cardA), "A", "main", null, 1);

        assertEquals(2, deckA.getMainDeck().size());
        assertEquals("KID-701", deckA.getMainDeck().get(1).getCard().getKonamiId());
        assertEquals(OwnershipStatus.OWNED, deckA.getMainDeck().get(1).getOwnershipStatus(),
                "Reordering a freshly-added slot must not disturb its just-computed status");
    }
}