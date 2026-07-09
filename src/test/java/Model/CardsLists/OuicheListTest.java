package Model.CardsLists;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class OuicheListTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Reset all OuicheList static state before each test
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a minimal Card whose cardKey resolves to {@code passCode + "|||"}.
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
     * Wraps {@code card} in a CardElement with the given ownership status.
     */
    private static CardElement makeSlot(Card card, OwnershipStatus status) {
        CardElement element = new CardElement(card);
        element.setOwnershipStatus(status);
        return element;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers shared by move-scenario tests
    // ─────────────────────────────────────────────────────────────────────────

    /** Builds a standalone Deck whose main section contains {@code mainSlots}. */
    private static Deck makeDeck(String name, List<CardElement> mainSlots) {
        Deck deck = new Deck();
        deck.setName(name);
        deck.setMainDeck(mainSlots);
        deck.setExtraDeck(new ArrayList<>());
        deck.setSideDeck(new ArrayList<>());
        return deck;
    }

    /** Builds a non-loose ThemeCollection whose cardsList contains {@code slots}. */
    private static ThemeCollection makeCollection(String name, List<CardElement> slots) {
        ThemeCollection collection = new ThemeCollection();
        collection.setName(name);
        collection.setCardsList(slots);
        collection.setConnectToWholeCollection(false);
        return collection;
    }

    /**
     * Initialises the compact missing-cards maps so each element in {@code slots}
     * appears once. Substandard maps are initialised empty.
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
     * Initialises empty compact maps and the given unusedCards list.
     */
    private static void initEmptyMaps(List<CardElement> unusedCards) {
        OuicheList.setMaOuicheList(new LinkedHashMap<>());
        OuicheList.setMaOuicheListCounts(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandard(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandardCounts(new LinkedHashMap<>());
        OuicheList.setUnusedCards(unusedCards);
    }

    @BeforeEach
    void resetOuicheList() {
        // Was previously missing OuicheList.setMyCardsCollection(null) and
        // OuicheList.setDecksList(null) — an incomplete reset here was part of why
        // this class's leftover state could interfere with other OuicheList-touching
        // test classes depending on run order. Delegate to the shared helper so this
        // can never drift out of sync again (see OuicheListTestSupport's Javadoc).
        OuicheListTestSupport.resetAll();
    }

    @AfterEach
    void tearDown() {
        // Leave a clean slate so this test can never leak state into whatever runs next.
        OuicheListTestSupport.resetAll();
    }

    @Test
    public void testGetThirdPartyCardsINeedList() {
        List<CardElement> cards = new ArrayList<>();
        OuicheList.setThirdPartyCardsINeedList(cards);
        // Fixed: was incorrectly asserting against getThirdPartyList()
        assertEquals(cards, OuicheList.getThirdPartyCardsINeedList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Move scenario tests
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Moving a MISSING slot from one deck's main section to another: the source
     * section must be empty afterwards and the destination must gain a MISSING slot.
     * The missing count stays at 1 (one need removed, one need added).
     */
    @Test
    void moveMissingCard_betweenStandaloneDecks_slotTransferred() {
        Card cardX = makeCard("K_X", "P_X");
        CardElement ouicheSlot = makeSlot(cardX, OwnershipStatus.MISSING);
        CardElement liveElement = makeSlot(cardX, OwnershipStatus.MISSING);

        List<CardElement> deckAMain = new ArrayList<>(List.of(ouicheSlot));
        List<CardElement> deckBMain = new ArrayList<>();

        DecksAndCollectionsList dal = new DecksAndCollectionsList();
        dal.addDeck(makeDeck("DeckA", deckAMain));
        dal.addDeck(makeDeck("DeckB", deckBMain));
        OuicheList.setDetailedOuicheList(dal);

        initMissingMaps(ouicheSlot);
        OuicheList.setUnusedCards(new ArrayList<>());

        OuicheList.onDeckCardRemoved(liveElement, "DeckA", "main", null);
        OuicheList.onDeckCardAdded(liveElement, "DeckB", "main", null);

        assertTrue(deckAMain.isEmpty(), "Source section must be empty after the move");
        assertEquals(1, deckBMain.size(), "Destination must contain exactly one slot");
        assertEquals(OwnershipStatus.MISSING, deckBMain.get(0).getOwnershipStatus(),
                "No owned copy available — slot must be MISSING");
        assertEquals(1,
                OuicheList.getMaOuicheListCounts()
                        .getOrDefault(OuicheList.cardKey(liveElement), 0),
                "Missing count must still be 1 after a MISSING→MISSING move");
        assertTrue(OuicheList.getUnusedCards().isEmpty(),
                "unusedCards must remain empty — no owned copy was involved");
    }

    /**
     * Moving an OWNED slot when no other MISSING slot for that card exists.
     *
     * <p>onDeckCardRemoved frees the owned copy into unusedCards (new behaviour).
     * onDeckCardAdded picks it up from unusedCards and marks the new slot OWNED.
     * Net result: source empty, destination OWNED, unusedCards drained.</p>
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

        List<CardElement> unusedCards = new ArrayList<>();
        initEmptyMaps(unusedCards);

        OuicheList.onDeckCardRemoved(liveElement, "DeckA", "main", null);
        OuicheList.onDeckCardAdded(liveElement, "DeckB", "main", null);

        assertTrue(deckAMain.isEmpty(), "Source section must be empty after the move");
        assertEquals(1, deckBMain.size(), "Destination must contain exactly one slot");
        assertEquals(OwnershipStatus.OWNED, deckBMain.get(0).getOwnershipStatus(),
                "Physical card still owned — destination slot must be OWNED");
        assertTrue(unusedCards.isEmpty(),
                "Physical card must have been consumed from unusedCards by onDeckCardAdded");
        assertTrue(OuicheList.getMaOuicheList().isEmpty(),
                "No MISSING entry must appear — the slot is fully OWNED");
    }

    /**
     * Moving an OWNED slot when another MISSING slot for the same card already
     * exists in DeckC (listed before the destination DeckB).
     *
     * <p>onDeckCardRemoved finds DeckC's MISSING slot and promotes it to OWNED
     * (the freed copy fills that existing need). Nothing goes into unusedCards.
     * onDeckCardAdded then creates a new MISSING slot in DeckB because unusedCards
     * is empty.</p>
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

        // DeckA first, DeckC second — findNextMissingSlotByKonamiId walks this order,
        // so DeckC's MISSING slot is found before DeckB is even considered.
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

        assertTrue(deckAMain.isEmpty(), "Source section DeckA must be empty");
        assertEquals(OwnershipStatus.OWNED, ouicheSlotC.getOwnershipStatus(),
                "Freed ownership must propagate to the existing MISSING slot in DeckC");
        assertEquals(1, deckBMain.size(), "Destination DeckB must contain the moved slot");
        assertEquals(OwnershipStatus.MISSING, deckBMain.get(0).getOwnershipStatus(),
                "DeckB slot is MISSING — freed copy was consumed by DeckC");
        assertTrue(unusedCards.isEmpty(),
                "unusedCards must be empty — freed copy went to DeckC, not unusedCards");
        assertEquals(1,
                OuicheList.getMaOuicheListCounts()
                        .getOrDefault(OuicheList.cardKey(liveElement), 0),
                "Missing count must be 1: DeckC's MISSING became OWNED, DeckB added a new MISSING");
    }

    /**
     * onDeckCardRemoved for an OWNED slot with no next MISSING: the physical card
     * must appear in unusedCards so the add-half of a subsequent MOVE can find it.
     * This tests the fix in isolation.
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

        List<CardElement> unusedCards = new ArrayList<>();
        initEmptyMaps(unusedCards);

        OuicheList.onDeckCardRemoved(liveElement, "DeckA", "main", null);

        assertEquals(1, unusedCards.size(),
                "Physical card must be in unusedCards after removing an OWNED slot "
                        + "with no next MISSING slot");
        assertSame(liveElement, unusedCards.get(0),
                "The exact liveElement reference must be added to unusedCards");
    }

    /**
     * onDeckCardRemoved for an OWNED_SUBSTANDARD slot with no next MISSING slot
     * must also free the physical card into unusedCards.
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

        // OWNED_SUBSTANDARD slot lives in the substandard compact map.
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
                        + "slot with no next MISSING slot");
    }

    /**
     * Moving a MISSING slot from a standalone deck's main section to a
     * ThemeCollection's cardsList.
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

        OuicheList.onDeckCardRemoved(liveElement, "DeckA", "main", null);
        OuicheList.onDeckCardAdded(liveElement, null, null, "ColB");

        assertTrue(deckAMain.isEmpty(), "Source deck section must be empty after the move");
        assertEquals(1, colBCards.size(), "Destination collection must contain one slot");
        assertEquals(OwnershipStatus.MISSING, colBCards.get(0).getOwnershipStatus(),
                "No owned copy available — slot must be MISSING");
        assertEquals(1,
                OuicheList.getMaOuicheListCounts()
                        .getOrDefault(OuicheList.cardKey(liveElement), 0),
                "Missing count must remain 1 after a MISSING→MISSING cross-entity move");
    }

    /**
     * Moving a MISSING slot from a ThemeCollection's cardsList to a standalone
     * deck's main section.
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

        OuicheList.onDeckCardRemoved(liveElement, null, null, "ColA");
        OuicheList.onDeckCardAdded(liveElement, "DeckB", "main", null);

        assertTrue(colACards.isEmpty(), "Source collection must be empty after the move");
        assertEquals(1, deckBMain.size(), "Destination deck section must contain one slot");
        assertEquals(OwnershipStatus.MISSING, deckBMain.get(0).getOwnershipStatus(),
                "No owned copy available — slot must be MISSING");
        assertEquals(1,
                OuicheList.getMaOuicheListCounts()
                        .getOrDefault(OuicheList.cardKey(liveElement), 0),
                "Missing count must remain 1 after a MISSING→MISSING cross-entity move");
    }

    /**
     * Moving an OWNED slot from a ThemeCollection's cardsList to a standalone
     * deck's main section when no other MISSING slot exists for that card.
     * The physical card must flow through unusedCards so the destination slot
     * ends up OWNED.
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

        List<CardElement> unusedCards = new ArrayList<>();
        initEmptyMaps(unusedCards);

        OuicheList.onDeckCardRemoved(liveElement, null, null, "ColA");
        OuicheList.onDeckCardAdded(liveElement, "DeckB", "main", null);

        assertTrue(colACards.isEmpty(), "Source collection must be empty after the move");
        assertEquals(1, deckBMain.size(), "Destination deck section must contain one slot");
        assertEquals(OwnershipStatus.OWNED, deckBMain.get(0).getOwnershipStatus(),
                "Physical card still owned — destination slot must be OWNED");
        assertTrue(unusedCards.isEmpty(),
                "Physical card must have been consumed from unusedCards by onDeckCardAdded");
        assertTrue(OuicheList.getMaOuicheList().isEmpty(),
                "No MISSING entry must appear — the slot is fully OWNED");
    }
}