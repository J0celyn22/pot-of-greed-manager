package Model.CardsLists;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parametric unit tests for the OuicheList incremental-update logic in
 * {@link OuicheListUpdater}, exercised through {@link OuicheList}'s public dispatch
 * methods. All tests are in-memory — no file I/O, database, or JavaFX runtime needed.
 *
 * <p>Coverage:
 * <ul>
 *   <li>onDeckCardAdded — all three sections, with/without available owned copy,
 *       null/unknown deck name, inside a collection vs standalone</li>
 *   <li>onDeckCardRemoved — MISSING and OWNED/OWNED_SUBSTANDARD slots, propagation
 *       to next MISSING, across sections</li>
 *   <li>onOwnedCardAdded — fills first available slot in generation order,
 *       multiple copies, card not needed, quality (substandard)</li>
 *   <li>onOwnedCardRemoved — reverts owned/substandard slot, skips when another
 *       copy still covers the slot, removes from unusedCards</li>
 * </ul>
 */
public class OuicheListUpdaterTest {

    // ── Fixture fields ───────────────────────────────────────────────────────

    private Card cardA;
    private Card cardB;
    private Deck detailedDeck;
    private ThemeCollection detailedCollection;

    // ── Setup ────────────────────────────────────────────────────────────────

    private static Card card(String konamiId, String passCode, String imagePath) {
        Card c = new Card();
        c.setKonamiId(konamiId);
        c.setPassCode(passCode);
        c.setImagePath(imagePath);
        return c;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static CardElement missingSlot(Card card) {
        CardElement element = new CardElement(card);
        element.setOwnershipStatus(OwnershipStatus.MISSING);
        return element;
    }

    private static CardElement ownedSlot(Card card) {
        CardElement element = new CardElement(card);
        element.setOwnershipStatus(OwnershipStatus.OWNED);
        return element;
    }

    private static CardElement substandardSlot(Card card) {
        CardElement element = new CardElement(card);
        element.setOwnershipStatus(OwnershipStatus.OWNED_SUBSTANDARD);
        return element;
    }

    @BeforeEach
    void setUp() {
        cardA = card("KID-001", "00000001", "img/a.jpg");
        cardB = card("KID-002", "00000002", "img/b.jpg");

        // Standalone deck: one MISSING slot for cardA in each section.
        detailedDeck = new Deck();
        detailedDeck.setName("TestDeck");
        detailedDeck.getMainDeck().add(missingSlot(cardA));
        detailedDeck.getExtraDeck().add(missingSlot(cardA));
        detailedDeck.getSideDeck().add(missingSlot(cardA));

        // Non-loose collection: one MISSING slot for cardA in cardsList.
        detailedCollection = new ThemeCollection();
        detailedCollection.setName("TestCollection");
        detailedCollection.setCardsList(new ArrayList<>(List.of(missingSlot(cardA))));

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

    private List<CardElement> sectionFor(Deck deck, String section) {
        switch (section.toLowerCase()) {
            case "main":
                return deck.getMainDeck();
            case "extra":
                return deck.getExtraDeck();
            case "side":
                return deck.getSideDeck();
            default:
                throw new IllegalArgumentException("Unknown section: " + section);
        }
    }

    // =========================================================================
    // onDeckCardAdded — standalone deck, all three sections
    // =========================================================================

    @ParameterizedTest(name = "deckCardAdded_noOwnedCopy_MISSING [{0}]")
    @CsvSource({"main", "extra", "side"})
    void deckCardAdded_noOwnedCopy_newSlotIsMissing(String section) {
        CardElement newSlot = new CardElement(cardB); // cardB has no existing slot

        OuicheList.onDeckCardAdded(newSlot, "TestDeck", section, null);

        List<CardElement> targetSection = sectionFor(detailedDeck, section);
        // Original cardA slot + new cardB slot
        assertEquals(2, targetSection.size());
        assertEquals(OwnershipStatus.MISSING, newSlot.getOwnershipStatus());
    }

    @ParameterizedTest(name = "deckCardAdded_ownedCopyAvailable_OWNED [{0}]")
    @CsvSource({"main", "extra", "side"})
    void deckCardAdded_ownedCopyAvailable_newSlotIsOwned(String section) {
        CardElement unusedCopy = new CardElement(cardB);
        OuicheList.getUnusedCards().add(unusedCopy);
        CardElement newSlot = new CardElement(cardB);

        OuicheList.onDeckCardAdded(newSlot, "TestDeck", section, null);

        assertEquals(OwnershipStatus.OWNED, newSlot.getOwnershipStatus());
        assertTrue(OuicheList.getUnusedCards().isEmpty());
    }

    @ParameterizedTest(name = "deckCardAdded_substandardCopyOnly_SUBSTANDARD [{0}]")
    @CsvSource({"main", "extra", "side"})
    void deckCardAdded_substandardCopyOnly_newSlotIsSubstandard(String section) {
        // Mark the unused copy as Damaged — won't satisfy quality round but fills round 2.
        CardElement damagedCopy = new CardElement(cardB);
        damagedCopy.setCondition(CardCondition.DAMAGED);
        OuicheList.getUnusedCards().add(damagedCopy);

        // New slot requests Near Mint or better.
        CardElement newSlot = new CardElement(cardB);
        newSlot.setCondition(CardCondition.NEAR_MINT);

        OuicheList.onDeckCardAdded(newSlot, "TestDeck", section, null);

        assertEquals(OwnershipStatus.OWNED_SUBSTANDARD, newSlot.getOwnershipStatus());
        assertTrue(OuicheList.getUnusedCards().isEmpty());
    }

    @Test
    void deckCardAdded_unknownDeckName_noOp() {
        int before = detailedDeck.getMainDeck().size();
        OuicheList.onDeckCardAdded(new CardElement(cardB), "NoSuchDeck", "main", null);
        assertEquals(before, detailedDeck.getMainDeck().size());
    }

    @Test
    void deckCardAdded_nullSection_noOp() {
        int before = detailedDeck.getMainDeck().size();
        OuicheList.onDeckCardAdded(new CardElement(cardB), "TestDeck", null, null);
        assertEquals(before, detailedDeck.getMainDeck().size());
    }

    @Test
    void deckCardAdded_nullDeckAndNullCollection_noOp() {
        int before = detailedDeck.getMainDeck().size();
        OuicheList.onDeckCardAdded(new CardElement(cardB), null, "main", null);
        assertEquals(before, detailedDeck.getMainDeck().size());
    }

    // =========================================================================
    // onDeckCardAdded — collection cardsList
    // =========================================================================

    @Test
    void collectionCardAdded_noOwnedCopy_newSlotIsMissing() {
        int before = detailedCollection.getCardsList().size();
        CardElement newSlot = new CardElement(cardB);

        OuicheList.onDeckCardAdded(newSlot, null, null, "TestCollection");

        assertEquals(before + 1, detailedCollection.getCardsList().size());
        assertEquals(OwnershipStatus.MISSING, newSlot.getOwnershipStatus());
    }

    @Test
    void collectionCardAdded_ownedCopyAvailable_newSlotIsOwned() {
        OuicheList.getUnusedCards().add(new CardElement(cardB));
        CardElement newSlot = new CardElement(cardB);

        OuicheList.onDeckCardAdded(newSlot, null, null, "TestCollection");

        assertEquals(OwnershipStatus.OWNED, newSlot.getOwnershipStatus());
        assertTrue(OuicheList.getUnusedCards().isEmpty());
    }

    @Test
    void collectionCardAdded_unknownCollectionName_noOp() {
        int before = detailedCollection.getCardsList().size();
        OuicheList.onDeckCardAdded(new CardElement(cardB), null, null, "NoSuchCollection");
        assertEquals(before, detailedCollection.getCardsList().size());
    }

    // =========================================================================
    // onDeckCardRemoved — all sections, all initial statuses
    // =========================================================================

    @ParameterizedTest(name = "deckCardRemoved_MISSING [{0}]")
    @CsvSource({"main", "extra", "side"})
    void deckCardRemoved_missingSlot_removedWithoutPropagation(String section) {
        // Clear all sections so only the one under test has a slot.
        detailedCollection.setCardsList(new ArrayList<>());
        detailedDeck.getMainDeck().clear();
        detailedDeck.getExtraDeck().clear();
        detailedDeck.getSideDeck().clear();

        List<CardElement> targetSection = sectionFor(detailedDeck, section);
        CardElement slot = missingSlot(cardA);
        targetSection.add(slot);

        OuicheList.onDeckCardRemoved(slot, "TestDeck", section, null);

        assertFalse(targetSection.contains(slot), "Slot should be removed");
        assertTrue(OuicheList.getUnusedCards().isEmpty(),
                "MISSING removal should not trigger propagation");
    }

    @ParameterizedTest(name = "deckCardRemoved_OWNED_propagates [{0}]")
    @CsvSource({"main", "extra", "side"})
    void deckCardRemoved_ownedSlot_propagatesToNextMissing(String section) {
        // Remove competing slots so the only MISSING slot after removal is nextMissing.
        detailedCollection.setCardsList(new ArrayList<>());
        detailedDeck.getMainDeck().clear();
        detailedDeck.getExtraDeck().clear();
        detailedDeck.getSideDeck().clear();

        List<CardElement> targetSection = sectionFor(detailedDeck, section);
        CardElement ownedSlot = ownedSlot(cardA);
        targetSection.add(ownedSlot);
        CardElement nextMissing = missingSlot(cardA);
        targetSection.add(nextMissing);

        OuicheList.onDeckCardRemoved(ownedSlot, "TestDeck", section, null);

        assertFalse(targetSection.contains(ownedSlot), "Removed slot should be gone");
        assertEquals(OwnershipStatus.OWNED, nextMissing.getOwnershipStatus(),
                "Next MISSING slot should be promoted to OWNED");
    }

    @ParameterizedTest(name = "deckCardRemoved_SUBSTANDARD_propagates [{0}]")
    @CsvSource({"main", "extra", "side"})
    void deckCardRemoved_substandardSlot_propagatesToNextMissing(String section) {
        detailedCollection.setCardsList(new ArrayList<>());
        detailedDeck.getMainDeck().clear();
        detailedDeck.getExtraDeck().clear();
        detailedDeck.getSideDeck().clear();

        List<CardElement> targetSection = sectionFor(detailedDeck, section);
        CardElement substandardSlot = substandardSlot(cardA);
        targetSection.add(substandardSlot);
        CardElement nextMissing = missingSlot(cardA);
        targetSection.add(nextMissing);

        OuicheList.onDeckCardRemoved(substandardSlot, "TestDeck", section, null);

        assertFalse(targetSection.contains(substandardSlot));
        assertEquals(OwnershipStatus.OWNED, nextMissing.getOwnershipStatus(),
                "Next MISSING slot should be promoted to OWNED after substandard slot removed");
    }

    @Test
    void deckCardRemoved_ownedSlot_noNextMissing_returnsToUnusedCards() {
        detailedCollection.setCardsList(new ArrayList<>());
        // Clear extra and side so there's no other MISSING slot.
        detailedDeck.getExtraDeck().clear();
        detailedDeck.getSideDeck().clear();

        CardElement ownedSlot = detailedDeck.getMainDeck().get(0);
        ownedSlot.setOwnershipStatus(OwnershipStatus.OWNED);

        OuicheList.onDeckCardRemoved(ownedSlot, "TestDeck", "main", null);

        assertTrue(detailedDeck.getMainDeck().isEmpty());
        // With nowhere to propagate the freed ownership to, the card returns to the
        // unused pool -- intentional (see the comment on the addToUnusedCards call in
        // onDeckCardRemoved): this is also what lets the "add" half of a cross-group
        // move find it again. This test previously asserted the opposite (empty pool),
        // which was stale relative to that documented behavior.
        assertEquals(1, OuicheList.getUnusedCards().size(),
                "The freed card should return to unusedCards, not vanish");
    }

    @Test
    void deckCardRemoved_propagationFindsSlotAcrossSections() {
        detailedCollection.setCardsList(new ArrayList<>());

        // OWNED in main deck, MISSING in extra deck.
        CardElement ownedMain = detailedDeck.getMainDeck().get(0);
        ownedMain.setOwnershipStatus(OwnershipStatus.OWNED);
        detailedDeck.getSideDeck().clear();

        CardElement extraMissing = detailedDeck.getExtraDeck().get(0);

        OuicheList.onDeckCardRemoved(ownedMain, "TestDeck", "main", null);

        assertEquals(OwnershipStatus.OWNED, extraMissing.getOwnershipStatus(),
                "Propagation should cross section boundaries within the same deck");
    }

    // =========================================================================
    // onOwnedCardAdded — fills slots in generation order
    // =========================================================================

    @Test
    void ownedCardAdded_fillsCollectionSlotFirst() {
        // Collection is searched before standalone decks (generation order).
        OuicheList.onOwnedCardAdded(new CardElement(cardA));

        assertEquals(OwnershipStatus.OWNED,
                detailedCollection.getCardsList().get(0).getOwnershipStatus(),
                "Collection slot should be filled first");
        assertEquals(OwnershipStatus.MISSING,
                detailedDeck.getMainDeck().get(0).getOwnershipStatus(),
                "Deck slot stays MISSING — only one copy");
    }

    @Test
    void ownedCardAdded_multipleCopiesFillMultipleSlots() {
        // Three copies: fills collection cardsList, then deck main, extra, side.
        OuicheList.onOwnedCardAdded(new CardElement(cardA));
        OuicheList.onOwnedCardAdded(new CardElement(cardA));
        OuicheList.onOwnedCardAdded(new CardElement(cardA));
        OuicheList.onOwnedCardAdded(new CardElement(cardA));

        assertEquals(OwnershipStatus.OWNED, detailedCollection.getCardsList().get(0).getOwnershipStatus());
        assertEquals(OwnershipStatus.OWNED, detailedDeck.getMainDeck().get(0).getOwnershipStatus());
        assertEquals(OwnershipStatus.OWNED, detailedDeck.getExtraDeck().get(0).getOwnershipStatus());
        assertEquals(OwnershipStatus.OWNED, detailedDeck.getSideDeck().get(0).getOwnershipStatus());
        assertTrue(OuicheList.getUnusedCards().isEmpty());
    }

    @Test
    void ownedCardAdded_excessCopyGoesToUnused() {
        // Four copies fill all four slots; fifth goes to unusedCards.
        for (int i = 0; i < 4; i++) {
            OuicheList.onOwnedCardAdded(new CardElement(cardA));
        }
        CardElement fifthCopy = new CardElement(cardA);
        OuicheList.onOwnedCardAdded(fifthCopy);

        assertEquals(1, OuicheList.getUnusedCards().size());
        assertTrue(OuicheList.getUnusedCards().contains(fifthCopy));
    }

    @Test
    void ownedCardAdded_cardNotNeeded_goesToUnused() {
        // cardB has no slot anywhere in detailedOuicheList.
        CardElement copy = new CardElement(cardB);
        OuicheList.onOwnedCardAdded(copy);

        assertTrue(OuicheList.getUnusedCards().contains(copy));
        // Existing slots unaffected.
        assertEquals(OwnershipStatus.MISSING, detailedDeck.getMainDeck().get(0).getOwnershipStatus());
    }

    @Test
    void ownedCardAdded_qualityMismatch_marksSubstandard() {
        // All existing slots require Near Mint; owned copy is Damaged.
        CardElement nmSlot = missingSlot(cardA);
        nmSlot.setCondition(CardCondition.NEAR_MINT);
        detailedCollection.setCardsList(new ArrayList<>(List.of(nmSlot)));
        detailedDeck.getMainDeck().clear();
        detailedDeck.getExtraDeck().clear();
        detailedDeck.getSideDeck().clear();

        CardElement damagedCopy = new CardElement(cardA);
        damagedCopy.setCondition(CardCondition.DAMAGED);

        OuicheList.onOwnedCardAdded(damagedCopy);

        assertEquals(OwnershipStatus.OWNED_SUBSTANDARD, nmSlot.getOwnershipStatus(),
                "Damaged copy filling a NM-required slot should be OWNED_SUBSTANDARD");
    }

    // =========================================================================
    // onOwnedCardRemoved
    // =========================================================================

    @Test
    void ownedCardRemoved_unusedCopy_removedFromUnusedCards() {
        CardElement unusedCopy = new CardElement(cardA);
        OuicheList.getUnusedCards().add(unusedCopy);

        OuicheList.onOwnedCardRemoved(unusedCopy);

        assertTrue(OuicheList.getUnusedCards().isEmpty());
        // Slot in detailedOuicheList untouched.
        assertEquals(OwnershipStatus.MISSING, detailedCollection.getCardsList().get(0).getOwnershipStatus());
    }

    @ParameterizedTest(name = "ownedCardRemoved_reverts [{0}]")
    @EnumSource(value = OwnershipStatus.class, names = {"OWNED", "OWNED_SUBSTANDARD"})
    void ownedCardRemoved_occupiedSlot_revertsToMissing(OwnershipStatus initialStatus) {
        // Mark the collection slot as occupied (no competing owned-collection category).
        CardElement slot = detailedCollection.getCardsList().get(0);
        slot.setOwnershipStatus(initialStatus);
        // Clear the other slots so there's no ambiguity.
        detailedDeck.getMainDeck().clear();
        detailedDeck.getExtraDeck().clear();
        detailedDeck.getSideDeck().clear();

        CardElement removedCopy = new CardElement(cardA);

        OuicheList.onOwnedCardRemoved(removedCopy);

        assertEquals(OwnershipStatus.MISSING, slot.getOwnershipStatus(),
                initialStatus + " slot should revert to MISSING");
    }

    @Test
    void ownedCardRemoved_anotherCopyStillCoversSlot_slotUnchanged() {
        CardElement slot = detailedCollection.getCardsList().get(0);
        slot.setOwnershipStatus(OwnershipStatus.OWNED);
        detailedDeck.getMainDeck().clear();
        detailedDeck.getExtraDeck().clear();
        detailedDeck.getSideDeck().clear();

        // A category named "TestCollection" still has cardA in the owned collection.
        OwnedCardsCollection ownedCollection = new OwnedCardsCollection();
        Box box = new Box("MyBox");
        CardsGroup group = new CardsGroup("TestCollection");
        group.addCard(new CardElement(cardA)); // remaining copy
        box.getContent().add(group);
        ownedCollection.getOwnedCollection().add(box);
        OuicheList.setMyCardsCollection(ownedCollection);

        OuicheList.onOwnedCardRemoved(new CardElement(cardA));

        assertEquals(OwnershipStatus.OWNED, slot.getOwnershipStatus(),
                "Slot should stay OWNED because another copy is still filed under TestCollection");
    }

    @Test
    void ownedCardRemoved_cardNotInOuicheList_noOp() {
        // Removing cardB which has no slot in detailedOuicheList and is not in unusedCards.
        OuicheList.onOwnedCardRemoved(new CardElement(cardB));
        // Everything should be unchanged.
        assertEquals(OwnershipStatus.MISSING, detailedCollection.getCardsList().get(0).getOwnershipStatus());
        assertEquals(OwnershipStatus.MISSING, detailedDeck.getMainDeck().get(0).getOwnershipStatus());
    }

    // =========================================================================
    // Null / missing OuicheList guards
    // =========================================================================

    @Test
    void allMethods_nullDetailedOuicheList_noException() {
        OuicheList.setDetailedOuicheList(null);
        assertDoesNotThrow(() -> OuicheList.onOwnedCardAdded(new CardElement(cardA)));
        assertDoesNotThrow(() -> OuicheList.onOwnedCardRemoved(new CardElement(cardA)));
        assertDoesNotThrow(() -> OuicheList.onDeckCardAdded(new CardElement(cardA), "X", "main", null));
        assertDoesNotThrow(() -> OuicheList.onDeckCardRemoved(new CardElement(cardA), "X", "main", null));
    }

    @Test
    void allMethods_nullCard_noException() {
        assertDoesNotThrow(() -> OuicheList.onOwnedCardAdded(null));
        assertDoesNotThrow(() -> OuicheList.onOwnedCardRemoved(null));
        assertDoesNotThrow(() -> OuicheList.onDeckCardAdded(null, "TestDeck", "main", null));
        assertDoesNotThrow(() -> OuicheList.onDeckCardRemoved(null, "TestDeck", "main", null));
    }

    @Test
    void allMethods_nullMapsStillWork() {
        // Compact maps null — should not NPE, just skip map updates.
        OuicheList.setMaOuicheList(null);
        OuicheList.setMaOuicheListCounts(null);
        OuicheList.setMaOuicheListSubstandard(null);
        OuicheList.setMaOuicheListSubstandardCounts(null);

        assertDoesNotThrow(() -> OuicheList.onOwnedCardAdded(new CardElement(cardA)));
        assertDoesNotThrow(() -> {
            CardElement slot = detailedDeck.getMainDeck().get(0);
            slot.setOwnershipStatus(OwnershipStatus.OWNED);
            OuicheList.onOwnedCardRemoved(new CardElement(cardA));
        });
    }
}