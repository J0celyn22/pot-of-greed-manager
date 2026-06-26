package Model.CardsLists;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OuicheListTest {

    // ─────────────────────────────────────────────────────────────────────────
    // State reset
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a minimal {@link Card} with the given KonamiId and passCode (no
     * imagePath, no printCode) so that {@link OuicheList#cardKey} returns
     * {@code passCode + "|||"}.
     */
    private static Card makeCard(String konamiId, String passCode) {
        Card card = new Card();
        card.setKonamiId(konamiId);
        card.setPassCode(passCode);
        return card;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Getter / setter round-trip tests (original suite)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    public void testGetUnusedCards() {
        List<CardElement> cards = new ArrayList<>();
        OuicheList.setUnusedCards(cards);
        assertEquals(cards, OuicheList.getUnusedCards());
    }

    @Test
    public void testSetUnusedCards() {
        List<CardElement> cards = new ArrayList<>();
        OuicheList.setUnusedCards(cards);
        assertEquals(cards, OuicheList.getUnusedCards());
    }

    @Test
    public void testGetListsIntersection() {
        List<CardElement> cards = new ArrayList<>();
        OuicheList.setListsIntersection(cards);
        assertEquals(cards, OuicheList.getListsIntersection());
    }

    @Test
    public void testSetListsIntersection() {
        List<CardElement> cards = new ArrayList<>();
        OuicheList.setListsIntersection(cards);
        assertEquals(cards, OuicheList.getListsIntersection());
    }

    @Test
    public void testGetDetailedOuicheList() {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        OuicheList.setDetailedOuicheList(list);
        assertEquals(list, OuicheList.getDetailedOuicheList());
    }

    @Test
    public void testSetDetailedOuicheList() {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        OuicheList.setDetailedOuicheList(list);
        assertEquals(list, OuicheList.getDetailedOuicheList());
    }

    @Test
    public void testGetThirdPartyList() {
        List<CardElement> cards = new ArrayList<>();
        OuicheList.setThirdPartyList(cards);
        assertEquals(cards, OuicheList.getThirdPartyList());
    }

    @Test
    public void testSetThirdPartyList() {
        List<CardElement> cards = new ArrayList<>();
        OuicheList.setThirdPartyList(cards);
        assertEquals(cards, OuicheList.getThirdPartyList());
    }

    /**
     * Creates a {@link CardElement} wrapping {@code card} with the given
     * {@code status} (no condition, no rarity set — quality checks will always
     * pass since the same defaults apply to both the wanted slot and the owned copy).
     */
    private static CardElement makeSlot(Card card, OwnershipStatus status) {
        CardElement element = new CardElement(card);
        element.setOwnershipStatus(status);
        return element;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers shared by move-scenario tests
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a standalone {@link Deck} with the given name whose main section
     * contains {@code mainSlots} (extra and side sections are empty).
     */
    private static Deck makeDeck(String name, List<CardElement> mainSlots) {
        Deck deck = new Deck();
        deck.setName(name);
        deck.setMainDeck(mainSlots);
        deck.setExtraDeck(new ArrayList<>());
        deck.setSideDeck(new ArrayList<>());
        return deck;
    }

    /**
     * Builds a non-loose {@link ThemeCollection} with the given name whose
     * cardsList contains {@code slots} (no linked decks).
     */
    private static ThemeCollection makeCollection(String name, List<CardElement> slots) {
        ThemeCollection collection = new ThemeCollection();
        collection.setName(name);
        collection.setCardsList(slots);
        collection.setConnectToWholeCollection(false);
        return collection;
    }

    /**
     * Initialises the compact missing-cards maps so that
     * {@link OuicheList#getMaOuicheList()} and its counts map contain exactly
     * one entry per element in {@code slots}.  The substandard maps are
     * initialised to empty.
     */
    private static void initMissingMaps(CardElement... slots) {
        LinkedHashMap<String, CardElement> missingMap = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> missingCounts = new LinkedHashMap<>();
        for (CardElement slot : slots) {
            String key = OuicheList.cardKey(slot);
            missingMap.put(key, slot);
            missingCounts.merge(key, 1, Integer::sum);
        }
        OuicheList.setMaOuicheList(missingMap);
        OuicheList.setMaOuicheListCounts(missingCounts);
        OuicheList.setMaOuicheListSubstandard(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandardCounts(new LinkedHashMap<>());
    }

    /**
     * Resets all OuicheList static state before each test so that no residual
     * data from a previous test can influence the next one.
     */
    @BeforeEach
    void resetOuicheList() {
        OuicheList.setDetailedOuicheList(null);
        OuicheList.setUnusedCards(null);
        OuicheList.setMaOuicheList(null);
        OuicheList.setMaOuicheListCounts(null);
        OuicheList.setMaOuicheListSubstandard(null);
        OuicheList.setMaOuicheListSubstandardCounts(null);
        OuicheList.setListsIntersection(null);
        OuicheList.setThirdPartyList(null);
        OuicheList.setThirdPartyCardsINeedList(null);
    }

    @Test
    public void testGetThirdPartyCardsINeedList() {
        List<CardElement> cards = new ArrayList<>();
        OuicheList.setThirdPartyCardsINeedList(cards);
        assertEquals(cards, OuicheList.getThirdPartyCardsINeedList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Move scenario tests
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Moving a MISSING slot from one standalone deck's main section to another
     * standalone deck's main section: the slot must disappear from the source
     * section and appear as MISSING in the destination section.  The missing-card
     * count for that card stays at 1 (one need removed from the source, one need
     * added to the destination).
     */
    @Test
    void moveMissingCard_betweenStandaloneDecks_slotTransferred() {
        Card cardX = makeCard("K_X", "P_X");
        // ouicheSlot is the OuicheList's copy held in DeckA's main section.
        CardElement ouicheSlot = makeSlot(cardX, OwnershipStatus.MISSING);
        // liveElement is the physical deck element being dragged by the user.
        CardElement liveElement = makeSlot(cardX, OwnershipStatus.MISSING);

        List<CardElement> deckAMain = new ArrayList<>(List.of(ouicheSlot));
        List<CardElement> deckBMain = new ArrayList<>();

        DecksAndCollectionsList dal = new DecksAndCollectionsList();
        dal.addDeck(makeDeck("DeckA", deckAMain));
        dal.addDeck(makeDeck("DeckB", deckBMain));
        OuicheList.setDetailedOuicheList(dal);

        initMissingMaps(ouicheSlot);
        OuicheList.setUnusedCards(new ArrayList<>());

        // Simulate the MOVE: remove from DeckA, add to DeckB.
        OuicheList.onDeckCardRemoved(liveElement, "DeckA", "main", null);
        OuicheList.onDeckCardAdded(liveElement, "DeckB", "main", null);

        assertTrue(deckAMain.isEmpty(),
                "Source section must be empty after the move");
        assertEquals(1, deckBMain.size(),
                "Destination section must contain exactly one slot");
        assertEquals(OwnershipStatus.MISSING, deckBMain.get(0).getOwnershipStatus(),
                "The moved slot has no owned copy to fill it → stays MISSING");
        assertEquals(1, OuicheList.getMaOuicheListCounts()
                        .getOrDefault(OuicheList.cardKey(liveElement), 0),
                "Missing count for the card must still be 1 after a MISSING→MISSING move");
        assertTrue(OuicheList.getUnusedCards().isEmpty(),
                "unusedCards must remain empty — no owned copy was involved");
    }

    /**
     * Moving an OWNED slot from one standalone deck section to another when no
     * other MISSING slot exists for that card.
     *
     * <p>Expected chain:
     * <ol>
     *   <li>{@code onDeckCardRemoved} removes the OWNED slot from the OuicheList's
     *       source section; finding no next MISSING slot to promote, it places the
     *       physical card back into {@code unusedCards}.</li>
     *   <li>{@code onDeckCardAdded} creates the new slot in the destination section
     *       and immediately finds the physical card in {@code unusedCards}, so the
     *       slot is marked OWNED and {@code unusedCards} is drained back to empty.</li>
     * </ol>
     */
    @Test
    void moveOwnedCard_betweenStandaloneDecks_ownershipTransferredViaUnusedCards() {
        Card cardX = makeCard("K_X", "P_X");
        CardElement ouicheSlot = makeSlot(cardX, OwnershipStatus.OWNED);
        CardElement liveElement = makeSlot(cardX, OwnershipStatus.OWNED);

        List<CardElement> deckAMain = new ArrayList<>(List.of(ouicheSlot));
        List<CardElement> deckBMain = new ArrayList<>();

        DecksAndCollectionsList dal = new DecksAndCollectionsList();
        dal.addDeck(makeDeck("DeckA", deckAMain));
        dal.addDeck(makeDeck("DeckB", deckBMain));
        OuicheList.setDetailedOuicheList(dal);

        // OWNED slot: not in the missing map.
        OuicheList.setMaOuicheList(new LinkedHashMap<>());
        OuicheList.setMaOuicheListCounts(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandard(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandardCounts(new LinkedHashMap<>());
        List<CardElement> unusedCards = new ArrayList<>();
        OuicheList.setUnusedCards(unusedCards);

        OuicheList.onDeckCardRemoved(liveElement, "DeckA", "main", null);
        OuicheList.onDeckCardAdded(liveElement, "DeckB", "main", null);

        assertTrue(deckAMain.isEmpty(),
                "Source section must be empty after the move");
        assertEquals(1, deckBMain.size(),
                "Destination section must contain exactly one slot");
        assertEquals(OwnershipStatus.OWNED, deckBMain.get(0).getOwnershipStatus(),
                "Physical card still owned — destination slot must be OWNED");
        assertTrue(unusedCards.isEmpty(),
                "Physical card must have been consumed from unusedCards by onDeckCardAdded");
        assertTrue(OuicheList.getMaOuicheList().isEmpty(),
                "No new MISSING entry must appear — the slot is fully OWNED");
    }

    /**
     * Moving an OWNED slot when another MISSING slot for the same card already
     * exists elsewhere in the OuicheList.
     *
     * <p>Expected chain:
     * <ol>
     *   <li>{@code onDeckCardRemoved} removes the OWNED slot from DeckA; finding
     *       the existing MISSING slot in DeckC, it promotes that slot to OWNED
     *       (the freed copy satisfies DeckC's need) and does NOT put anything in
     *       {@code unusedCards}.</li>
     *   <li>{@code onDeckCardAdded} adds the new slot to DeckB but finds
     *       {@code unusedCards} empty, so the new slot is MISSING.</li>
     * </ol>
     */
    @Test
    void moveOwnedCard_whenAnotherMissingSlotExists_ownershipPropagatesAndNewSlotIsMissing() {
        Card cardX = makeCard("K_X", "P_X");
        CardElement ouicheSlotA = makeSlot(cardX, OwnershipStatus.OWNED);
        CardElement ouicheSlotC = makeSlot(cardX, OwnershipStatus.MISSING);
        CardElement liveElement = makeSlot(cardX, OwnershipStatus.OWNED);

        List<CardElement> deckAMain = new ArrayList<>(List.of(ouicheSlotA));
        List<CardElement> deckCMain = new ArrayList<>(List.of(ouicheSlotC));
        List<CardElement> deckBMain = new ArrayList<>();

        // Add DeckA, DeckC, then DeckB: findNextMissingSlotByKonamiId walks
        // decks in list order, so DeckC's MISSING slot is found before DeckB.
        DecksAndCollectionsList dal = new DecksAndCollectionsList();
        dal.addDeck(makeDeck("DeckA", deckAMain));
        dal.addDeck(makeDeck("DeckC", deckCMain));
        dal.addDeck(makeDeck("DeckB", deckBMain));
        OuicheList.setDetailedOuicheList(dal);

        initMissingMaps(ouicheSlotC);
        List<CardElement> unusedCards = new ArrayList<>();
        OuicheList.setUnusedCards(unusedCards);

        OuicheList.onDeckCardRemoved(liveElement, "DeckA", "main", null);
        OuicheList.onDeckCardAdded(liveElement, "DeckB", "main", null);

        assertTrue(deckAMain.isEmpty(),
                "Source section DeckA must be empty after the move");
        assertEquals(OwnershipStatus.OWNED, ouicheSlotC.getOwnershipStatus(),
                "Freed ownership must propagate to the existing MISSING slot in DeckC");
        assertEquals(1, deckBMain.size(),
                "Destination section DeckB must contain the moved slot");
        assertEquals(OwnershipStatus.MISSING, deckBMain.get(0).getOwnershipStatus(),
                "DeckB slot is MISSING — the freed copy was used for DeckC, not DeckB");
        assertTrue(unusedCards.isEmpty(),
                "unusedCards must be empty — the freed copy was consumed by the DeckC slot");
        assertEquals(1, OuicheList.getMaOuicheListCounts()
                        .getOrDefault(OuicheList.cardKey(liveElement), 0),
                "Missing count must be 1: DeckC's MISSING entry was replaced by DeckB's new MISSING entry");
    }

    /**
     * {@link OuicheList#onDeckCardRemoved} for an OWNED slot with no other
     * MISSING slot in the OuicheList must place the physical card into
     * {@code unusedCards} so the next {@code onDeckCardAdded} (i.e. the "add"
     * half of a MOVE) can reclaim it.  This tests the fix in isolation.
     */
    @Test
    void onDeckCardRemoved_ownedCard_noNextMissing_placedInUnusedCards() {
        Card cardX = makeCard("K_X", "P_X");
        CardElement ouicheSlot = makeSlot(cardX, OwnershipStatus.OWNED);
        CardElement liveElement = makeSlot(cardX, OwnershipStatus.OWNED);

        List<CardElement> deckAMain = new ArrayList<>(List.of(ouicheSlot));

        DecksAndCollectionsList dal = new DecksAndCollectionsList();
        dal.addDeck(makeDeck("DeckA", deckAMain));
        OuicheList.setDetailedOuicheList(dal);

        OuicheList.setMaOuicheList(new LinkedHashMap<>());
        OuicheList.setMaOuicheListCounts(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandard(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandardCounts(new LinkedHashMap<>());
        List<CardElement> unusedCards = new ArrayList<>();
        OuicheList.setUnusedCards(unusedCards);

        OuicheList.onDeckCardRemoved(liveElement, "DeckA", "main", null);

        assertEquals(1, unusedCards.size(),
                "Physical card must be in unusedCards after removing an OWNED slot "
                        + "with no next MISSING slot to promote");
        assertSame(liveElement, unusedCards.get(0),
                "The exact liveElement reference must be added to unusedCards");
    }

    /**
     * {@link OuicheList#onDeckCardRemoved} for an OWNED_SUBSTANDARD slot with no
     * other MISSING slot must also free the physical card into {@code unusedCards}.
     */
    @Test
    void onDeckCardRemoved_ownedSubstandardCard_noNextMissing_placedInUnusedCards() {
        Card cardX = makeCard("K_X", "P_X");
        CardElement ouicheSlot = makeSlot(cardX, OwnershipStatus.OWNED_SUBSTANDARD);
        CardElement liveElement = makeSlot(cardX, OwnershipStatus.OWNED_SUBSTANDARD);

        List<CardElement> deckAMain = new ArrayList<>(List.of(ouicheSlot));

        DecksAndCollectionsList dal = new DecksAndCollectionsList();
        dal.addDeck(makeDeck("DeckA", deckAMain));
        OuicheList.setDetailedOuicheList(dal);

        // Substandard slot is in the substandard compact map, not the missing map.
        OuicheList.setMaOuicheList(new LinkedHashMap<>());
        OuicheList.setMaOuicheListCounts(new LinkedHashMap<>());
        LinkedHashMap<String, CardElement> substandardMap = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> substandardCounts = new LinkedHashMap<>();
        String key = OuicheList.cardKey(ouicheSlot);
        substandardMap.put(key, ouicheSlot);
        substandardCounts.put(key, 1);
        OuicheList.setMaOuicheListSubstandard(substandardMap);
        OuicheList.setMaOuicheListSubstandardCounts(substandardCounts);
        List<CardElement> unusedCards = new ArrayList<>();
        OuicheList.setUnusedCards(unusedCards);

        OuicheList.onDeckCardRemoved(liveElement, "DeckA", "main", null);

        assertEquals(1, unusedCards.size(),
                "Physical card must be in unusedCards after removing an OWNED_SUBSTANDARD "
                        + "slot with no next MISSING slot to promote");
    }

    /**
     * Moving a MISSING slot from a standalone deck's main section to a
     * {@link ThemeCollection}'s cardsList: the deck section must become empty and
     * the collection's cardsList must gain the MISSING slot.
     */
    @Test
    void moveMissingCard_fromDeckToCollection_slotTransferred() {
        Card cardX = makeCard("K_X", "P_X");
        CardElement ouicheSlot = makeSlot(cardX, OwnershipStatus.MISSING);
        CardElement liveElement = makeSlot(cardX, OwnershipStatus.MISSING);

        List<CardElement> deckAMain = new ArrayList<>(List.of(ouicheSlot));
        List<CardElement> colBCards = new ArrayList<>();

        DecksAndCollectionsList dal = new DecksAndCollectionsList();
        dal.addDeck(makeDeck("DeckA", deckAMain));
        dal.addCollection(makeCollection("ColB", colBCards));
        OuicheList.setDetailedOuicheList(dal);

        initMissingMaps(ouicheSlot);
        OuicheList.setUnusedCards(new ArrayList<>());

        // Remove from DeckA.main; add to ColB.cardsList (deckName=null, collectionName="ColB").
        OuicheList.onDeckCardRemoved(liveElement, "DeckA", "main", null);
        OuicheList.onDeckCardAdded(liveElement, null, null, "ColB");

        assertTrue(deckAMain.isEmpty(),
                "Source deck section must be empty after the move");
        assertEquals(1, colBCards.size(),
                "Destination collection must contain exactly one slot");
        assertEquals(OwnershipStatus.MISSING, colBCards.get(0).getOwnershipStatus(),
                "No owned copy available — slot must be MISSING");
        assertEquals(1, OuicheList.getMaOuicheListCounts()
                        .getOrDefault(OuicheList.cardKey(liveElement), 0),
                "Missing count must remain 1 after a MISSING→MISSING cross-entity move");
    }

    /**
     * Moving a MISSING slot from a {@link ThemeCollection}'s cardsList to a
     * standalone deck's main section: the collection's cardsList must become empty
     * and the deck's main section must gain the MISSING slot.
     */
    @Test
    void moveMissingCard_fromCollectionToDeck_slotTransferred() {
        Card cardX = makeCard("K_X", "P_X");
        CardElement ouicheSlot = makeSlot(cardX, OwnershipStatus.MISSING);
        CardElement liveElement = makeSlot(cardX, OwnershipStatus.MISSING);

        List<CardElement> colACards = new ArrayList<>(List.of(ouicheSlot));
        List<CardElement> deckBMain = new ArrayList<>();

        DecksAndCollectionsList dal = new DecksAndCollectionsList();
        dal.addCollection(makeCollection("ColA", colACards));
        dal.addDeck(makeDeck("DeckB", deckBMain));
        OuicheList.setDetailedOuicheList(dal);

        initMissingMaps(ouicheSlot);
        OuicheList.setUnusedCards(new ArrayList<>());

        // Remove from ColA.cardsList (deckName=null); add to DeckB.main.
        OuicheList.onDeckCardRemoved(liveElement, null, null, "ColA");
        OuicheList.onDeckCardAdded(liveElement, "DeckB", "main", null);

        assertTrue(colACards.isEmpty(),
                "Source collection must be empty after the move");
        assertEquals(1, deckBMain.size(),
                "Destination deck section must contain exactly one slot");
        assertEquals(OwnershipStatus.MISSING, deckBMain.get(0).getOwnershipStatus(),
                "No owned copy available — slot must be MISSING");
        assertEquals(1, OuicheList.getMaOuicheListCounts()
                        .getOrDefault(OuicheList.cardKey(liveElement), 0),
                "Missing count must remain 1 after a MISSING→MISSING cross-entity move");
    }

    /**
     * Moving an OWNED slot from a collection's cardsList to a standalone deck's
     * main section when no other MISSING slot for that card exists.  The physical
     * card must flow through unusedCards so the destination slot ends up OWNED.
     */
    @Test
    void moveOwnedCard_fromCollectionToDeck_ownershipTransferredViaUnusedCards() {
        Card cardX = makeCard("K_X", "P_X");
        CardElement ouicheSlot = makeSlot(cardX, OwnershipStatus.OWNED);
        CardElement liveElement = makeSlot(cardX, OwnershipStatus.OWNED);

        List<CardElement> colACards = new ArrayList<>(List.of(ouicheSlot));
        List<CardElement> deckBMain = new ArrayList<>();

        DecksAndCollectionsList dal = new DecksAndCollectionsList();
        dal.addCollection(makeCollection("ColA", colACards));
        dal.addDeck(makeDeck("DeckB", deckBMain));
        OuicheList.setDetailedOuicheList(dal);

        OuicheList.setMaOuicheList(new LinkedHashMap<>());
        OuicheList.setMaOuicheListCounts(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandard(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandardCounts(new LinkedHashMap<>());
        List<CardElement> unusedCards = new ArrayList<>();
        OuicheList.setUnusedCards(unusedCards);

        OuicheList.onDeckCardRemoved(liveElement, null, null, "ColA");
        OuicheList.onDeckCardAdded(liveElement, "DeckB", "main", null);

        assertTrue(colACards.isEmpty(),
                "Source collection must be empty after the move");
        assertEquals(1, deckBMain.size(),
                "Destination deck section must contain exactly one slot");
        assertEquals(OwnershipStatus.OWNED, deckBMain.get(0).getOwnershipStatus(),
                "Physical card still owned — destination slot must be OWNED");
        assertTrue(unusedCards.isEmpty(),
                "Physical card must have been consumed from unusedCards by onDeckCardAdded");
        assertTrue(OuicheList.getMaOuicheList().isEmpty(),
                "No MISSING entry must appear — the slot is fully OWNED");
    }
}