package Controller;

import Model.CardsLists.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CardQualityService}, the pure domain/business-logic helpers
 * behind card sorting and quality-upgrade decisions.
 *
 * <p>Most methods here are fully parameter-driven and need no setup. A handful read
 * the shared decks/collections list or owned collection through
 * {@link UserInterfaceFunctions#getDecksList()} / {@link OuicheList#getMyCardsCollection()};
 * for those, the test injects a fixture via the matching setter. {@link #setUp()} and
 * {@link #tearDown()} clear both statics before and after every test, since other test
 * classes in this suite (e.g. {@code OuicheListUpdaterTest}) set
 * {@code OuicheList.myCardsCollection} without cleaning it up, so a clean slate can't be
 * assumed just because this class's own tests clean up after themselves.</p>
 *
 * <p>Coverage:
 * <ul>
 *   <li>isBetterCondition, satisfiesExpectedRarityBetter, isQualityUpgrade,
 *       isUpgradeOverPlacedCopy — the quality-comparison primitives</li>
 *   <li>removeFirstMatchingFromList, collectMatchingElementsInList, collectOwnedCopies,
 *       collectPlacedCopies — the list-matching helpers</li>
 *   <li>isDeckOrCollectionName (both overloads), computeNetCopiesNeeded,
 *       findSlotListForCard — the deck/collection lookup helpers</li>
 *   <li>computeCardNeedsSorting, computeCardNeedsSortingWithUpgrade — representative
 *       scenarios for the main decision paths (not full branch coverage; the method is
 *       610 lines and a candidate for decomposition — see the project backlog)</li>
 *   <li>isDegradedCopyInDeckOrCollection, findOwnedUpgradeCandidates — the
 *       already-placed-copy upgrade checks</li>
 * </ul>
 */
public class CardQualityServiceTest {

    // ── Fixture helpers ──────────────────────────────────────────────────────

    private static Card card(String konamiId, String passCode) {
        Card card = new Card();
        card.setKonamiId(konamiId);
        card.setPassCode(passCode);
        return card;
    }

    private static CardElement element(Card card, CardCondition condition, CardRarity rarity) {
        CardElement element = new CardElement(card);
        element.setCondition(condition);
        element.setRarity(rarity);
        return element;
    }

    private static CardElement slot(Card card, CardRarity expectedRarity) {
        CardElement slot = new CardElement(card);
        slot.setRarity(expectedRarity);
        return slot;
    }

    private static Box boxWithGroup(String boxName, String groupName, List<CardElement> cardList) {
        Box box = new Box(boxName);
        box.getContent().add(new CardsGroup(groupName, cardList));
        return box;
    }

    private static OwnedCardsCollection ownedCollection(Box... boxes) {
        OwnedCardsCollection owned = new OwnedCardsCollection();
        owned.setOwnedCollection(new ArrayList<>(List.of(boxes)));
        return owned;
    }

    // Reset before AND after every test: UserInterfaceFunctions/OuicheList hold
    // shared static state, and other test classes (e.g. OuicheListUpdaterTest) set
    // OuicheList.myCardsCollection via @BeforeEach without cleaning it up afterward,
    // so this class can't assume a clean slate just because its own tests clean up.
    @BeforeEach
    void setUp() {
        UserInterfaceFunctions.setDecksList(null);
        OuicheList.setMyCardsCollection(null);
    }

    @AfterEach
    void tearDown() {
        UserInterfaceFunctions.setDecksList(null);
        OuicheList.setMyCardsCollection(null);
    }

    // ── isBetterCondition ────────────────────────────────────────────────────

    @Test
    void isBetterCondition_strictlyLowerOrdinal_returnsTrue() {
        assertTrue(CardQualityService.isBetterCondition(CardCondition.MINT, CardCondition.NEAR_MINT));
    }

    @Test
    void isBetterCondition_higherOrdinal_returnsFalse() {
        assertFalse(CardQualityService.isBetterCondition(CardCondition.NEAR_MINT, CardCondition.MINT));
    }

    @Test
    void isBetterCondition_equalCondition_returnsFalse() {
        assertFalse(CardQualityService.isBetterCondition(CardCondition.GOOD, CardCondition.GOOD));
    }

    @Test
    void isBetterCondition_nullCandidate_returnsFalse() {
        assertFalse(CardQualityService.isBetterCondition(null, CardCondition.GOOD));
    }

    @Test
    void isBetterCondition_nullExisting_returnsFalse() {
        assertFalse(CardQualityService.isBetterCondition(CardCondition.MINT, null));
    }

    // ── satisfiesExpectedRarityBetter ────────────────────────────────────────

    @Test
    void satisfiesExpectedRarityBetter_noExpectation_returnsFalse() {
        assertFalse(CardQualityService.satisfiesExpectedRarityBetter(
                CardRarity.ULTRA_RARE, CardRarity.COMMON, null));
    }

    @Test
    void satisfiesExpectedRarityBetter_existingAlreadySatisfies_returnsFalse() {
        assertFalse(CardQualityService.satisfiesExpectedRarityBetter(
                CardRarity.ULTRA_RARE, CardRarity.SECRET_RARE, CardRarity.SECRET_RARE));
    }

    @Test
    void satisfiesExpectedRarityBetter_candidateSatisfiesAndExistingDoesNot_returnsTrue() {
        assertTrue(CardQualityService.satisfiesExpectedRarityBetter(
                CardRarity.SECRET_RARE, CardRarity.COMMON, CardRarity.SECRET_RARE));
    }

    @Test
    void satisfiesExpectedRarityBetter_neitherSatisfies_returnsFalse() {
        assertFalse(CardQualityService.satisfiesExpectedRarityBetter(
                CardRarity.COMMON, CardRarity.RARE, CardRarity.SECRET_RARE));
    }

    // ── isQualityUpgrade ─────────────────────────────────────────────────────

    @Test
    void isQualityUpgrade_betterCondition_returnsTrue() {
        Card card = card("K1", "P1");
        CardElement existing = element(card, CardCondition.NEAR_MINT, null);
        CardElement candidate = element(card, CardCondition.MINT, null);

        assertTrue(CardQualityService.isQualityUpgrade(List.of(existing), null, candidate));
    }

    @Test
    void isQualityUpgrade_satisfiesExpectedRarityOfSlot_returnsTrue() {
        Card card = card("K1", "P1");
        CardElement existing = element(card, null, CardRarity.COMMON);
        CardElement candidate = element(card, null, CardRarity.SECRET_RARE);
        CardElement matchingSlot = slot(card, CardRarity.SECRET_RARE);

        assertTrue(CardQualityService.isQualityUpgrade(List.of(existing), List.of(matchingSlot), candidate));
    }

    @Test
    void isQualityUpgrade_noImprovement_returnsFalse() {
        Card card = card("K1", "P1");
        CardElement existing = element(card, CardCondition.MINT, CardRarity.SECRET_RARE);
        CardElement candidate = element(card, CardCondition.DAMAGED, CardRarity.COMMON);

        assertFalse(CardQualityService.isQualityUpgrade(List.of(existing), null, candidate));
    }

    @Test
    void isQualityUpgrade_nullCandidate_returnsFalse() {
        Card card = card("K1", "P1");
        CardElement existing = element(card, CardCondition.NEAR_MINT, null);
        assertFalse(CardQualityService.isQualityUpgrade(List.of(existing), null, null));
    }

    @Test
    void isQualityUpgrade_emptyExistingList_returnsFalse() {
        CardElement candidate = element(card("K1", "P1"), CardCondition.MINT, null);
        assertFalse(CardQualityService.isQualityUpgrade(List.of(), null, candidate));
    }

    // ── isUpgradeOverPlacedCopy ──────────────────────────────────────────────

    @Test
    void isUpgradeOverPlacedCopy_placedHasNoCondition_anyKnownConditionWins() {
        Card card = card("K1", "P1");
        CardElement placed = element(card, null, null);
        CardElement candidate = element(card, CardCondition.DAMAGED, null);

        assertTrue(CardQualityService.isUpgradeOverPlacedCopy(candidate, placed, null));
    }

    @Test
    void isUpgradeOverPlacedCopy_strictlyBetterCondition_returnsTrue() {
        Card card = card("K1", "P1");
        CardElement placed = element(card, CardCondition.GOOD, null);
        CardElement candidate = element(card, CardCondition.MINT, null);

        assertTrue(CardQualityService.isUpgradeOverPlacedCopy(candidate, placed, null));
    }

    @Test
    void isUpgradeOverPlacedCopy_rarityUpgradeForMatchingSlot_returnsTrue() {
        Card card = card("K1", "P1");
        CardElement placed = element(card, CardCondition.MINT, CardRarity.COMMON);
        CardElement candidate = element(card, CardCondition.MINT, CardRarity.SECRET_RARE);
        CardElement matchingSlot = slot(card, CardRarity.SECRET_RARE);

        assertTrue(CardQualityService.isUpgradeOverPlacedCopy(candidate, placed, List.of(matchingSlot)));
    }

    @Test
    void isUpgradeOverPlacedCopy_noImprovement_returnsFalse() {
        Card card = card("K1", "P1");
        CardElement placed = element(card, CardCondition.MINT, null);
        CardElement candidate = element(card, CardCondition.DAMAGED, null);

        assertFalse(CardQualityService.isUpgradeOverPlacedCopy(candidate, placed, null));
    }

    @Test
    void isUpgradeOverPlacedCopy_nullCandidateOrPlaced_returnsFalse() {
        CardElement placed = element(card("K1", "P1"), CardCondition.GOOD, null);
        assertFalse(CardQualityService.isUpgradeOverPlacedCopy(null, placed, null));
        assertFalse(CardQualityService.isUpgradeOverPlacedCopy(placed, null, null));
    }

    // ── removeFirstMatchingFromList (via package access) ────────────────────

    @Test
    void removeFirstMatchingFromList_removesFirstMatchAndStops() {
        Card card = card("K1", "P1");
        CardElement first = element(card, null, null);
        CardElement second = element(card, null, null);
        List<CardElement> list = new ArrayList<>(List.of(first, second));

        boolean removed = CardQualityService.removeFirstMatchingFromList(
                list, card, (candidateElement, referenceCard) ->
                        candidateElement.getCard().getPassCode().equals(referenceCard.getPassCode()));

        assertTrue(removed);
        assertEquals(1, list.size());
        assertSame(second, list.get(0));
    }

    @Test
    void removeFirstMatchingFromList_noMatch_returnsFalseAndLeavesListUntouched() {
        List<CardElement> list = new ArrayList<>(List.of(element(card("K1", "P1"), null, null)));

        boolean removed = CardQualityService.removeFirstMatchingFromList(
                list, card("K2", "P2"), (candidateElement, referenceCard) -> false);

        assertFalse(removed);
        assertEquals(1, list.size());
    }

    @Test
    void removeFirstMatchingFromList_nullListOrCard_returnsFalse() {
        assertFalse(CardQualityService.removeFirstMatchingFromList(null, card("K1", "P1"), (a, b) -> true));
        assertFalse(CardQualityService.removeFirstMatchingFromList(new ArrayList<>(), null, (a, b) -> true));
    }

    // ── collectMatchingElementsInList (via package access) ──────────────────

    @Test
    void collectMatchingElementsInList_matchesByPassCode() {
        Card target = card("K1", "P1");
        CardElement matching = element(card("K1", "P1"), null, null);
        CardElement other = element(card("K2", "P2"), null, null);

        List<CardElement> result = CardQualityService.collectMatchingElementsInList(
                List.of(matching, other), target);

        assertEquals(1, result.size());
        assertSame(matching, result.get(0));
    }

    @Test
    void collectMatchingElementsInList_nullListOrCard_returnsEmpty() {
        assertTrue(CardQualityService.collectMatchingElementsInList(null, card("K1", "P1")).isEmpty());
        assertTrue(CardQualityService.collectMatchingElementsInList(List.of(), null).isEmpty());
    }

    // ── collectOwnedCopies ───────────────────────────────────────────────────

    @Test
    void collectOwnedCopies_findsMatchesAcrossBoxesAndGroups() {
        Card target = card("K1", "P1");
        CardElement matchInBoxA = element(card("K1", "P1"), null, null);
        CardElement matchInBoxB = element(card("K1", "P1"), null, null);
        CardElement nonMatch = element(card("K2", "P2"), null, null);

        OwnedCardsCollection owned = ownedCollection(
                boxWithGroup("Box A", "Group 1", List.of(matchInBoxA, nonMatch)),
                boxWithGroup("Box B", "Group 1", List.of(matchInBoxB)));

        List<CardElement> result = CardQualityService.collectOwnedCopies(owned, target);

        assertEquals(2, result.size());
        assertTrue(result.contains(matchInBoxA));
        assertTrue(result.contains(matchInBoxB));
    }

    @Test
    void collectOwnedCopies_nullOwnedOrCard_returnsEmpty() {
        assertTrue(CardQualityService.collectOwnedCopies(null, card("K1", "P1")).isEmpty());
        assertTrue(CardQualityService.collectOwnedCopies(ownedCollection(), null).isEmpty());
    }

    // ── collectPlacedCopies (via package access) ─────────────────────────────

    @Test
    void collectPlacedCopies_matchesOnlyTheNamedSortingGroup() {
        Card target = card("K1", "P1");
        CardElement placedInTargetGroup = element(card("K1", "P1"), null, null);
        CardElement placedElsewhere = element(card("K1", "P1"), null, null);

        OwnedCardsCollection owned = ownedCollection(
                boxWithGroup("Box A", "Fusion Monsters", List.of(placedInTargetGroup)),
                boxWithGroup("Box A", "Unsorted", List.of(placedElsewhere)));

        List<CardElement> result = CardQualityService.collectPlacedCopies(
                owned, "Fusion Monsters", target, name -> name == null ? "" : name.toLowerCase());

        assertEquals(1, result.size());
        assertSame(placedInTargetGroup, result.get(0));
    }

    @Test
    void collectPlacedCopies_nullOwnedOrNameOrCard_returnsEmpty() {
        assertTrue(CardQualityService.collectPlacedCopies(
                null, "Group", card("K1", "P1"), s -> s).isEmpty());
        assertTrue(CardQualityService.collectPlacedCopies(
                ownedCollection(), null, card("K1", "P1"), s -> s).isEmpty());
    }

    // ── isDeckOrCollectionName (pure, package-access overload) ───────────────

    @Test
    void isDeckOrCollectionNamePure_matchesCollectionName_returnsTrue() {
        ThemeCollection collection = new ThemeCollection();
        collection.setName("Fusion Monsters");
        DecksAndCollectionsList decksList = new DecksAndCollectionsList();
        decksList.setCollections(List.of(collection));

        assertTrue(CardQualityService.isDeckOrCollectionName(
                "fusion monsters", decksList, s -> s == null ? "" : s.toLowerCase()));
    }

    @Test
    void isDeckOrCollectionNamePure_matchesLinkedDeckName_returnsTrue() {
        Deck linkedDeck = new Deck();
        linkedDeck.setName("Linked Deck");
        ThemeCollection collection = new ThemeCollection();
        collection.setName("Some Collection");
        collection.setLinkedDecks(List.of(List.of(linkedDeck)));
        DecksAndCollectionsList decksList = new DecksAndCollectionsList();
        decksList.setCollections(List.of(collection));

        assertTrue(CardQualityService.isDeckOrCollectionName(
                "linked deck", decksList, s -> s == null ? "" : s.toLowerCase()));
    }

    @Test
    void isDeckOrCollectionNamePure_matchesLooseDeckName_returnsTrue() {
        Deck looseDeck = new Deck();
        looseDeck.setName("Standalone Deck");
        DecksAndCollectionsList decksList = new DecksAndCollectionsList();
        decksList.setDecks(List.of(looseDeck));

        assertTrue(CardQualityService.isDeckOrCollectionName(
                "standalone deck", decksList, s -> s == null ? "" : s.toLowerCase()));
    }

    @Test
    void isDeckOrCollectionNamePure_noMatch_returnsFalse() {
        DecksAndCollectionsList decksList = new DecksAndCollectionsList();
        assertFalse(CardQualityService.isDeckOrCollectionName(
                "nothing", decksList, s -> s == null ? "" : s.toLowerCase()));
    }

    @Test
    void isDeckOrCollectionNamePure_nullDecksListOrName_returnsFalse() {
        assertFalse(CardQualityService.isDeckOrCollectionName(
                "name", null, s -> s));
        assertFalse(CardQualityService.isDeckOrCollectionName(
                null, new DecksAndCollectionsList(), s -> s));
    }

    // ── isDeckOrCollectionName (public overload, reads UserInterfaceFunctions) ─

    @Test
    void isDeckOrCollectionName_withMatchingGlobalDecksList_returnsTrue() {
        ThemeCollection collection = new ThemeCollection();
        collection.setName("Fusion Monsters");
        DecksAndCollectionsList decksList = new DecksAndCollectionsList();
        decksList.setCollections(List.of(collection));
        UserInterfaceFunctions.setDecksList(decksList);

        assertTrue(CardQualityService.isDeckOrCollectionName("Fusion Monsters"));
    }

    @Test
    void isDeckOrCollectionName_noGlobalDecksListLoaded_returnsFalse() {
        UserInterfaceFunctions.setDecksList(null);
        assertFalse(CardQualityService.isDeckOrCollectionName("Anything"));
    }

    // ── computeNetCopiesNeeded ───────────────────────────────────────────────

    @Test
    void computeNetCopiesNeeded_definitionWantsMoreThanOwned_returnsPositive() {
        Card card = card("K1", "P1");
        Deck deck = new Deck();
        deck.setName("Test Deck");
        deck.setMainDeck(List.of(new CardElement(card), new CardElement(card)));
        deck.setExtraDeck(new ArrayList<>());
        deck.setSideDeck(new ArrayList<>());
        DecksAndCollectionsList decksList = new DecksAndCollectionsList();
        decksList.setDecks(List.of(deck));
        UserInterfaceFunctions.setDecksList(decksList);

        assertEquals(2, CardQualityService.computeNetCopiesNeeded(card, "Test Deck"));
    }

    @Test
    void computeNetCopiesNeeded_alreadyFullyOwned_returnsZero() {
        Card card = card("K1", "P1");
        Deck deck = new Deck();
        deck.setName("Test Deck");
        deck.setMainDeck(List.of(new CardElement(card)));
        deck.setExtraDeck(new ArrayList<>());
        deck.setSideDeck(new ArrayList<>());
        DecksAndCollectionsList decksList = new DecksAndCollectionsList();
        decksList.setDecks(List.of(deck));
        UserInterfaceFunctions.setDecksList(decksList);
        OuicheList.setMyCardsCollection(ownedCollection(
                boxWithGroup("Box A", "Test Deck", List.of(new CardElement(card)))));

        assertEquals(0, CardQualityService.computeNetCopiesNeeded(card, "Test Deck"));
    }

    @Test
    void computeNetCopiesNeeded_unrecognisedElementName_returnsMinusOne() {
        UserInterfaceFunctions.setDecksList(new DecksAndCollectionsList());
        assertEquals(-1, CardQualityService.computeNetCopiesNeeded(card("K1", "P1"), "No Such Element"));
    }

    @Test
    void computeNetCopiesNeeded_nullCardOrBlankName_returnsMinusOne() {
        assertEquals(-1, CardQualityService.computeNetCopiesNeeded(null, "Test Deck"));
        assertEquals(-1, CardQualityService.computeNetCopiesNeeded(card("K1", "P1"), "  "));
    }

    // ── computeCardNeedsSorting (representative scenarios) ───────────────────

    @Test
    void computeCardNeedsSorting_nullCardOrElementName_returnsFalse() {
        assertFalse(CardSortingRules.computeCardNeedsSorting(null, "Anything"));
        assertFalse(CardSortingRules.computeCardNeedsSorting(card("K1", "P1"), null));
    }

    @Test
    void computeCardNeedsSorting_collectionMatch_cardPresentNotProtected_returnsSorted() {
        Card card = card("K1", "P1");
        ThemeCollection collection = new ThemeCollection();
        collection.setName("Fusion Monsters");
        CardElement slotElement = new CardElement(card);
        slotElement.setDontRemove(false);
        collection.setCardsList(new ArrayList<>(List.of(slotElement)));
        DecksAndCollectionsList decksList = new DecksAndCollectionsList();
        decksList.setCollections(List.of(collection));
        UserInterfaceFunctions.setDecksList(decksList);

        assertFalse(CardSortingRules.computeCardNeedsSorting(card, "Fusion Monsters"));
    }

    @Test
    void computeCardNeedsSorting_collectionMatch_cardProtectedByDontRemove_returnsSortedConservatively() {
        Card card = card("K1", "P1");
        ThemeCollection collection = new ThemeCollection();
        collection.setName("Fusion Monsters");
        CardElement slotElement = new CardElement(card);
        slotElement.setDontRemove(true);
        collection.setCardsList(new ArrayList<>(List.of(slotElement)));
        DecksAndCollectionsList decksList = new DecksAndCollectionsList();
        decksList.setCollections(List.of(collection));
        UserInterfaceFunctions.setDecksList(decksList);

        assertFalse(CardSortingRules.computeCardNeedsSorting(card, "Fusion Monsters"));
    }

    @Test
    void computeCardNeedsSorting_looseDeckMatch_cardFound_returnsSorted() {
        Card card = card("K1", "P1");
        Deck deck = new Deck();
        deck.setName("Test Deck");
        deck.setMainDeck(new ArrayList<>(List.of(new CardElement(card))));
        deck.setExtraDeck(new ArrayList<>());
        deck.setSideDeck(new ArrayList<>());
        DecksAndCollectionsList decksList = new DecksAndCollectionsList();
        decksList.setDecks(List.of(deck));
        UserInterfaceFunctions.setDecksList(decksList);

        assertFalse(CardSortingRules.computeCardNeedsSorting(card, "Test Deck"));
    }

    @Test
    void computeCardNeedsSorting_looseDeckMatch_cardNotFound_returnsNeedsSorting() {
        Card card = card("K1", "P1");
        Deck deck = new Deck();
        deck.setName("Test Deck");
        deck.setMainDeck(new ArrayList<>(List.of(new CardElement(card("K2", "P2")))));
        deck.setExtraDeck(new ArrayList<>());
        deck.setSideDeck(new ArrayList<>());
        DecksAndCollectionsList decksList = new DecksAndCollectionsList();
        decksList.setDecks(List.of(deck));
        UserInterfaceFunctions.setDecksList(decksList);

        assertTrue(CardSortingRules.computeCardNeedsSorting(card, "Test Deck"));
    }

    @Test
    void computeCardNeedsSorting_typeCategory_cardDoesNotMatchType_returnsNeedsSorting() {
        Card spellCard = card("K1", "P1");
        spellCard.setCardType("Spell Card");
        spellCard.setCardProperties(new ArrayList<>());
        UserInterfaceFunctions.setDecksList(new DecksAndCollectionsList());

        // "Dragon" is a known monster-subtype category; a Spell card never matches it.
        assertTrue(CardSortingRules.computeCardNeedsSorting(spellCard, "Dragon"));
    }

    @Test
    void computeCardNeedsSorting_unrecognisedElementName_returnsFalseConservatively() {
        Card card = card("K1", "P1");
        UserInterfaceFunctions.setDecksList(new DecksAndCollectionsList());

        assertFalse(CardSortingRules.computeCardNeedsSorting(card, "Some Random Group Name"));
    }

    // ── computeCardNeedsSortingWithUpgrade ───────────────────────────────────

    @Test
    void computeCardNeedsSortingWithUpgrade_nullOwnedElementOrCard_returnsFalse() {
        assertFalse(CardSortingRules.computeCardNeedsSortingWithUpgrade(null, "Anything"));
        assertFalse(CardSortingRules.computeCardNeedsSortingWithUpgrade(
                new CardElement((Card) null), "Anything"));
    }

    @Test
    void computeCardNeedsSortingWithUpgrade_baseCheckAlreadyNeedsSorting_returnsTrue() {
        Card card = card("K1", "P1");
        Deck deck = new Deck();
        deck.setName("Test Deck");
        deck.setMainDeck(new ArrayList<>(List.of(new CardElement(card("K2", "P2")))));
        deck.setExtraDeck(new ArrayList<>());
        deck.setSideDeck(new ArrayList<>());
        DecksAndCollectionsList decksList = new DecksAndCollectionsList();
        decksList.setDecks(List.of(deck));
        UserInterfaceFunctions.setDecksList(decksList);

        assertTrue(CardSortingRules.computeCardNeedsSortingWithUpgrade(new CardElement(card), "Test Deck"));
    }

    @Test
    void computeCardNeedsSortingWithUpgrade_elementNameIsKnownDeckOrCollection_returnsFalse() {
        Card card = card("K1", "P1");
        ThemeCollection collection = new ThemeCollection();
        collection.setName("Fusion Monsters");
        collection.setCardsList(new ArrayList<>(List.of(new CardElement(card))));
        DecksAndCollectionsList decksList = new DecksAndCollectionsList();
        decksList.setCollections(List.of(collection));
        UserInterfaceFunctions.setDecksList(decksList);

        // The owned element sits inside the "Fusion Monsters" category itself, which is
        // a recognised D&C name -> reason-4 territory, not reason-3, so this is false.
        assertFalse(CardSortingRules.computeCardNeedsSortingWithUpgrade(
                new CardElement(card), "Fusion Monsters"));
    }

    // ── isDegradedCopyInDeckOrCollection / findOwnedUpgradeCandidates ────────

    @Test
    void isDegradedCopyInDeckOrCollection_noOwnedCopiesElsewhere_returnsFalse() {
        Card card = card("K1", "P1");
        CardElement deckElement = element(card, CardCondition.GOOD, null);
        OuicheList.setMyCardsCollection(ownedCollection());

        assertFalse(CardQualityService.isDegradedCopyInDeckOrCollection(deckElement, "Fusion Monsters"));
    }

    @Test
    void isDegradedCopyInDeckOrCollection_betterCopyOwnedOutsideCategory_returnsTrue() {
        Card card = card("K1", "P1");
        CardElement deckElement = element(card, CardCondition.GOOD, null);
        CardElement betterOwnedCopy = element(card, CardCondition.MINT, null);
        OuicheList.setMyCardsCollection(ownedCollection(
                boxWithGroup("Box A", "Unsorted", List.of(betterOwnedCopy))));

        assertTrue(CardQualityService.isDegradedCopyInDeckOrCollection(deckElement, "Fusion Monsters"));
    }

    @Test
    void isDegradedCopyInDeckOrCollection_betterCopyIsInSameCategory_excludedReturnsFalse() {
        Card card = card("K1", "P1");
        CardElement deckElement = element(card, CardCondition.GOOD, null);
        CardElement betterCopyButSameCategory = element(card, CardCondition.MINT, null);
        OuicheList.setMyCardsCollection(ownedCollection(
                boxWithGroup("Box A", "Fusion Monsters", List.of(betterCopyButSameCategory))));

        assertFalse(CardQualityService.isDegradedCopyInDeckOrCollection(deckElement, "Fusion Monsters"));
    }

    @Test
    void findOwnedUpgradeCandidates_returnsOnlyImprovingCopiesOutsideCategory() {
        Card card = card("K1", "P1");
        CardElement deckElement = element(card, CardCondition.GOOD, null);
        CardElement betterOutside = element(card, CardCondition.MINT, null);
        CardElement worseOutside = element(card, CardCondition.DAMAGED, null);
        OuicheList.setMyCardsCollection(ownedCollection(
                boxWithGroup("Box A", "Unsorted", List.of(betterOutside, worseOutside))));

        List<CardElement> result =
                CardQualityService.findOwnedUpgradeCandidates(deckElement, "Fusion Monsters");

        assertEquals(1, result.size());
        assertSame(betterOutside, result.get(0));
    }

    @Test
    void findOwnedUpgradeCandidates_nullDeckElementOrName_returnsEmpty() {
        assertTrue(CardQualityService.findOwnedUpgradeCandidates(null, "Fusion Monsters").isEmpty());
        assertTrue(CardQualityService.findOwnedUpgradeCandidates(
                element(card("K1", "P1"), CardCondition.GOOD, null), null).isEmpty());
    }
}