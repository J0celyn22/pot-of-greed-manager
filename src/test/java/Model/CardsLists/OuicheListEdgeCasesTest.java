package Model.CardsLists;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Remaining partition-analysis coverage not already handled by
 * {@link OuicheListUpdaterTest} (Add/Remove basics, My Collection) or
 * {@link OuicheListMoveAndArtworkTest} (position, reorder, artwork):
 * <ul>
 *   <li>guard behavior for {@link OuicheList#onDeckCardMoved} when the OuicheList
 *       hasn't been generated yet, or the card is {@code null} — the existing
 *       {@code allMethods_null*} tests in {@code OuicheListUpdaterTest} predate this
 *       method and don't cover it;</li>
 *   <li>cross-group MOVE ownership-neutrality — a move is a remove-then-add pair at
 *       this layer (mirroring exactly how {@code CardGridCell}/{@code CardTreeCell}
 *       orchestrate a real drag), and must round-trip a slot's status unchanged;</li>
 *   <li>multi-card add with partial supply (some slots satisfied, some not);</li>
 *   <li>duplicate slots (same KonamiId + artwork appearing twice) handled one at a
 *       time, not double-affected by a single call;</li>
 *   <li>removing the very last slot for a KonamiId anywhere — no propagation target,
 *       must not throw.</li>
 * </ul>
 */
public class OuicheListEdgeCasesTest {

    private Card cardA;
    private Deck detailedDeck;
    private ThemeCollection detailedCollection;

    private static Card card(String konamiId, String passCode, String imagePath) {
        Card c = new Card();
        c.setKonamiId(konamiId);
        c.setPassCode(passCode);
        c.setImagePath(imagePath);
        return c;
    }

    private static CardElement missingSlot(Card card) {
        CardElement e = new CardElement(card);
        e.setOwnershipStatus(OwnershipStatus.MISSING);
        return e;
    }

    @BeforeEach
    void setUp() {
        cardA = card("KID-801", "00000801", "img/a.jpg");

        detailedDeck = new Deck();
        detailedDeck.setName("TestDeck");

        detailedCollection = new ThemeCollection();
        detailedCollection.setName("TestCollection");
        detailedCollection.setCardsList(new ArrayList<>());

        DecksAndCollectionsList ouicheList = new DecksAndCollectionsList();
        ouicheList.addDeck(detailedDeck);
        ouicheList.addCollection(detailedCollection);

        OuicheList.setDetailedOuicheList(ouicheList);
        OuicheList.setUnusedCards(new ArrayList<>());
        OuicheList.setMaOuicheList(new LinkedHashMap<>());
        OuicheList.setMaOuicheListCounts(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandard(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandardCounts(new LinkedHashMap<>());
        OuicheList.setMyCardsCollection(new OwnedCardsCollection());
    }

    // =========================================================================
    // Guard cases for onDeckCardMoved (new this session — not covered by
    // OuicheListUpdaterTest's pre-existing allMethods_null* tests, which predate it)
    // =========================================================================

    @Test
    void deckCardMoved_nullDetailedOuicheList_noException() {
        OuicheList.setDetailedOuicheList(null);
        assertDoesNotThrow(() ->
                OuicheList.onDeckCardMoved(new CardElement(cardA), "TestDeck", "main", null, 0));
    }

    @Test
    void deckCardMoved_nullCard_noException() {
        assertDoesNotThrow(() ->
                OuicheList.onDeckCardMoved(null, "TestDeck", "main", null, 0));
    }

    // =========================================================================
    // Cross-group MOVE ownership-neutrality: a move is a remove-then-add pair at
    // this layer. Using the SAME live CardElement instance for both halves (as
    // CardGridCell/CardTreeCell do — they pass the same dragged elements to both
    // notifyOuicheListOfGroupRemovals and notifyOuicheListOfGroupAdditions) so
    // quality/condition data carries through the round trip exactly as it would
    // in production.
    // =========================================================================

    @Test
    void crossGroupMove_ownedCard_staysOwnedAfterMove() {
        Deck otherDeck = new Deck();
        otherDeck.setName("OtherDeck");
        OuicheList.getDetailedOuicheList().addDeck(otherDeck);

        CardElement liveElement = new CardElement(cardA); // the one "dragged" element
        liveElement.setOwnershipStatus(OwnershipStatus.OWNED);
        detailedDeck.getMainDeck().add(liveElement);

        OuicheList.onDeckCardRemoved(liveElement, "TestDeck", "main", null);
        OuicheList.onDeckCardAdded(liveElement, "OtherDeck", "main", null, Integer.MAX_VALUE);

        assertEquals(OwnershipStatus.OWNED, liveElement.getOwnershipStatus());
        assertTrue(otherDeck.getMainDeck().contains(liveElement));
        assertFalse(detailedDeck.getMainDeck().contains(liveElement));
    }

    @Test
    void crossGroupMove_missingCard_staysMissingAfterMove() {
        Deck otherDeck = new Deck();
        otherDeck.setName("OtherDeck");
        OuicheList.getDetailedOuicheList().addDeck(otherDeck);

        CardElement liveElement = missingSlot(cardA);
        detailedDeck.getMainDeck().add(liveElement);

        OuicheList.onDeckCardRemoved(liveElement, "TestDeck", "main", null);
        OuicheList.onDeckCardAdded(liveElement, "OtherDeck", "main", null, Integer.MAX_VALUE);

        assertEquals(OwnershipStatus.MISSING, liveElement.getOwnershipStatus());
        assertTrue(otherDeck.getMainDeck().contains(liveElement));
    }

    @Test
    void crossGroupMove_deckToCollectionCardsList_ownedCardStaysOwned() {
        CardElement liveElement = new CardElement(cardA);
        liveElement.setOwnershipStatus(OwnershipStatus.OWNED);
        detailedDeck.getMainDeck().add(liveElement);

        OuicheList.onDeckCardRemoved(liveElement, "TestDeck", "main", null);
        OuicheList.onDeckCardAdded(liveElement, null, null, "TestCollection", Integer.MAX_VALUE);

        assertEquals(OwnershipStatus.OWNED, liveElement.getOwnershipStatus());
        assertTrue(detailedCollection.getCardsList().contains(liveElement));
    }

    /**
     * Confirmed bug, found while writing this suite (not the linked-deck-group gap):
     * {@code onDeckCardRemoved}, when no other MISSING slot exists to propagate to, puts
     * the exact same live {@link CardElement} instance it was called with back into
     * {@link OuicheList#getUnusedCards()} (see the comment on that call site — it's
     * intentional, meant to let the following {@code onDeckCardAdded} half of a move find
     * it). But {@code onDeckCardAdded} then searches {@code unusedCards} for a match using
     * that <em>same instance</em> as both the wanted slot and a candidate in the pool.
     * {@link OuicheListComputer#ownedCopySatisfiesQuality} compares a slot's requested
     * condition/rarity against a candidate's — when they're literally the same object,
     * every comparison trivially passes, so the element satisfies its own round-1
     * (quality-required) check regardless of what condition it actually required.
     *
     * <p>Net effect: cross-group moving an OWNED_SUBSTANDARD card (same live-element
     * remove-then-add pair, exactly how {@code CardGridCell}/{@code CardTreeCell} do it)
     * silently upgrades it to OWNED — no new copy was found, the card just matched itself.
     * The OWNED case in the test above isn't affected in a way that's visible (OWNED
     * staying OWNED looks correct either way), which is why this only shows up for
     * OWNED_SUBSTANDARD.
     */
    @Test
    void crossGroupMove_substandardCard_selfMatchesAndIsIncorrectlyUpgradedToOwned_confirmedBug() {
        Deck otherDeck = new Deck();
        otherDeck.setName("OtherDeck");
        OuicheList.getDetailedOuicheList().addDeck(otherDeck);

        CardElement liveElement = new CardElement(cardA);
        liveElement.setCondition(CardCondition.NEAR_MINT); // the slot's requested condition
        liveElement.setOwnershipStatus(OwnershipStatus.OWNED_SUBSTANDARD);
        detailedDeck.getMainDeck().add(liveElement);

        OuicheList.onDeckCardRemoved(liveElement, "TestDeck", "main", null);
        OuicheList.onDeckCardAdded(liveElement, "OtherDeck", "main", null, Integer.MAX_VALUE);

        assertEquals(OwnershipStatus.OWNED_SUBSTANDARD, liveElement.getOwnershipStatus(),
                "Moving a SUBSTANDARD card should not silently upgrade it to OWNED just "
                        + "because it transiently self-matched in the unused pool -- currently fails");
    }

    // =========================================================================
    // Multi-card add — partial supply (some slots satisfied, some not)
    // =========================================================================

    @Test
    void deckCardAdded_twoSlotsOneUnusedCopy_firstInsertedGetsOwned_secondStaysMissing() {
        OuicheList.getUnusedCards().add(new CardElement(cardA)); // only 1 copy available

        CardElement slot1 = new CardElement(cardA);
        CardElement slot2 = new CardElement(cardA);
        OuicheList.onDeckCardAdded(slot1, "TestDeck", "main", null, 0);
        OuicheList.onDeckCardAdded(slot2, "TestDeck", "main", null, 1);

        assertEquals(OwnershipStatus.OWNED, slot1.getOwnershipStatus());
        assertEquals(OwnershipStatus.MISSING, slot2.getOwnershipStatus());
        assertTrue(OuicheList.getUnusedCards().isEmpty());
    }

    @Test
    void deckCardAdded_twoSlotsTwoUnusedCopies_bothGetOwned() {
        OuicheList.getUnusedCards().add(new CardElement(cardA));
        OuicheList.getUnusedCards().add(new CardElement(cardA));

        CardElement slot1 = new CardElement(cardA);
        CardElement slot2 = new CardElement(cardA);
        OuicheList.onDeckCardAdded(slot1, "TestDeck", "main", null, 0);
        OuicheList.onDeckCardAdded(slot2, "TestDeck", "main", null, 1);

        assertEquals(OwnershipStatus.OWNED, slot1.getOwnershipStatus());
        assertEquals(OwnershipStatus.OWNED, slot2.getOwnershipStatus());
    }

    // =========================================================================
    // Duplicate slots (same KonamiId + artwork appearing more than once)
    // =========================================================================

    @Test
    void deckCardRemoved_duplicateOwnedSlots_oneCallRemovesExactlyOne() {
        CardElement dup1 = new CardElement(cardA);
        dup1.setOwnershipStatus(OwnershipStatus.OWNED);
        CardElement dup2 = new CardElement(cardA);
        dup2.setOwnershipStatus(OwnershipStatus.OWNED);
        detailedDeck.getMainDeck().add(dup1);
        detailedDeck.getMainDeck().add(dup2);

        OuicheList.onDeckCardRemoved(new CardElement(cardA), "TestDeck", "main", null);

        assertEquals(1, detailedDeck.getMainDeck().size(),
                "Exactly one of the two duplicate slots should be removed, not both");
    }

    @Test
    void deckCardRemoved_duplicateOwnedSlots_twoSequentialCallsRemoveBoth() {
        CardElement dup1 = new CardElement(cardA);
        dup1.setOwnershipStatus(OwnershipStatus.OWNED);
        CardElement dup2 = new CardElement(cardA);
        dup2.setOwnershipStatus(OwnershipStatus.OWNED);
        detailedDeck.getMainDeck().add(dup1);
        detailedDeck.getMainDeck().add(dup2);

        OuicheList.onDeckCardRemoved(new CardElement(cardA), "TestDeck", "main", null);
        OuicheList.onDeckCardRemoved(new CardElement(cardA), "TestDeck", "main", null);

        assertTrue(detailedDeck.getMainDeck().isEmpty(),
                "Two sequential removals should account for both duplicates, with no crash");
    }

    // =========================================================================
    // Removing the last remaining slot for a KonamiId anywhere — no propagation
    // target must not throw, and the freed copy returns to the unused pool.
    // =========================================================================

    @Test
    void deckCardRemoved_lastOwnedSlotForKonamiId_freedCopyGoesToUnusedCards_noException() {
        CardElement onlySlot = new CardElement(cardA);
        onlySlot.setOwnershipStatus(OwnershipStatus.OWNED);
        detailedDeck.getMainDeck().add(onlySlot);

        assertDoesNotThrow(() ->
                OuicheList.onDeckCardRemoved(new CardElement(cardA), "TestDeck", "main", null));

        assertTrue(detailedDeck.getMainDeck().isEmpty());
        assertEquals(1, OuicheList.getUnusedCards().size(),
                "With no other MISSING slot to propagate to, the freed physical card "
                        + "returns to the unused pool");
    }
}