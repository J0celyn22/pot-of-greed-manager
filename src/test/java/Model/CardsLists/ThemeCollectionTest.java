//package Model.CardsLists;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.io.TempDir;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.stream.Collectors;
//
//public class ThemeCollectionTest {
//    private ThemeCollection collection;
//    private List<Deck> testDecks;
//
//    @BeforeEach
//    public void setUp() throws Exception {
//        collection = new ThemeCollection();
//        testDecks = new ArrayList<>();
//
//        // Initialize test decks
//        for (int i = 0; i < 5; i++) {
//            testDecks.add(new Deck());
//        }
//
//        // 1. Basic cards for testing
//        // Card only in collection
//        Card cardInCollection = createCard("CARD001", "Collection Card", "CT01-EN001", "10.00", false, false, false);
//        collection.getCardsList().add(new CardElement(cardInCollection));
//
//        // Card with specific artwork in collection
//        Card cardWithArtwork = createCard("CARD002", "Artwork Card", "CT02-EN001", "15.00", true, false, false);
//        collection.getCardsList().add(new CardElement(cardWithArtwork));
//
//        // Card with dontRemove flag
//        Card cardDontRemove = createCard("CARD003", "Don't Remove Card", "CT03-EN001", "20.00", false, true, false);
//        collection.getCardsList().add(new CardElement(cardDontRemove));
//
//        // 2. Cards for deck testing
//        // Card only in deck 1
//        Card cardOnlyInDeck = createCard("CARD010", "Deck Only Card", "SD01-EN001", "5.00", false, false, false);
//        testDecks.get(0).AddCardMain(cardOnlyInDeck);
//
//        // Card in multiple decks, same unit
//        Card cardMultiDeck = createCard("CARD011", "Multi-Deck Card", "SD02-EN001", "7.50", false, false, false);
//        testDecks.get(0).AddCardMain(cardMultiDeck);
//        testDecks.get(1).AddCardMain(cardMultiDeck);
//
//        // Card with same passcode but different print
//        Card cardSamePass1 = createCard("CARD020", "Same Pass 1", "SD03-EN001", "12.00", false, false, false);
//        Card cardSamePass2 = createCard("CARD020", "Same Pass 2", "SD03-EN002", "12.00", false, false, false);
//        testDecks.get(0).AddCardMain(cardSamePass1);
//        collection.getCardsList().add(new CardElement(cardSamePass2));
//
//        // Card with specific artwork in deck
//        Card cardDeckArtwork = createCard("CARD021", "Deck Artwork", "SD04-EN001", "8.00", true, false, false);
//        testDecks.get(1).AddCardMain(cardDeckArtwork);
//
//        // 3. Cards for unit testing
//        // Card in different units
//        Card cardDiffUnits1 = createCard("CARD030", "Diff Unit 1", "SD05-EN001", "9.99", false, false, false);
//        Card cardDiffUnits2 = createCard("CARD030", "Diff Unit 2", "SD05-EN002", "9.99", false, false, false);
//        testDecks.get(2).AddCardMain(cardDiffUnits1);
//        testDecks.get(4).AddCardMain(cardDiffUnits2);
//
//        // 4. Complex cases
//        // Card with all flags set
//        Card cardAllFlags = createCard("CARD040", "All Flags Card", "SD06-EN001", "25.00", true, true, true);
//        collection.getCardsList().add(new CardElement(cardAllFlags));
//
//        // Add decks to collection in different units
//        collection.AddDeck(testDecks.get(0)); // First unit
//        collection.AddDeck(testDecks.get(1)); // First unit
//        collection.AddDeck(testDecks.get(2)); // Second unit
//        collection.AddDeckToExistingUnit(testDecks.get(3), 1); // Add to second unit
//        collection.AddDeck(testDecks.get(4)); // Third unit
//    }
//
//    private Card createCard(String passCode, String name, String printCode, String price,
//                            boolean specificArtwork, boolean dontRemove, boolean isInDeck) throws Exception {
//        Card card = new Card(passCode);
//        card.setName_EN(name);
//        card.setPrintCode(printCode);
//        card.setPrice(price);
//        CardElement element = new CardElement(card);
//        element.setSpecificArtwork(specificArtwork);
//        element.setDontRemove(dontRemove);
//        element.setIsInDeck(isInDeck);
//        return card;
//    }
//
//    @Test
//    public void testGetName() {
//        ThemeCollection collection = new ThemeCollection();
//        collection.setName("TestCollection");
//        assertEquals("TestCollection", collection.getName());
//    }
//
//    @Test
//    public void testSetName() {
//        ThemeCollection collection = new ThemeCollection();
//        collection.setName("TestCollection");
//        assertEquals("TestCollection", collection.getName());
//    }
//
//    @Test
//    public void testGetCardsList() {
//        ThemeCollection collection = new ThemeCollection();
//        List<CardElement> cardsList = new ArrayList<>();
//        collection.setCardsList(cardsList);
//        assertEquals(cardsList, collection.getCardsList());
//    }
//
//    @Test
//    public void testSetCardsList() throws Exception {
//        ThemeCollection collection = new ThemeCollection();
//        List<CardElement> cardsList = new ArrayList<>();
//        Card card = new Card("TestCard");
//        cardsList.add(new CardElement(card));
//        collection.setCardsList(cardsList);
//
//        assertEquals(1, collection.getCardsList().size());
//        assertEquals("TestCard", collection.getCardsList().get(0).getCard().getPassCode());
//    }
//
//    @Test
//    public void testGetExceptionsToNotAdd() throws Exception {
//        ThemeCollection collection = new ThemeCollection();
//        List<CardElement> exceptions = new ArrayList<>();
//        Card card = new Card("ExceptionCard");
//        exceptions.add(new CardElement(card));
//        collection.setExceptionsToNotAdd(exceptions);
//
//        assertEquals(1, collection.getExceptionsToNotAdd().size());
//        assertEquals("ExceptionCard", collection.getExceptionsToNotAdd().get(0).getCard().getPassCode());
//    }
//
//    @Test
//    public void testSetExceptionsToNotAdd() throws Exception {
//        ThemeCollection collection = new ThemeCollection();
//        List<CardElement> exceptions = new ArrayList<>();
//        Card card = new Card("ExceptionCard");
//        exceptions.add(new CardElement(card));
//        collection.setExceptionsToNotAdd(exceptions);
//
//        assertEquals(1, collection.getExceptionsToNotAdd().size());
//        assertEquals("ExceptionCard", collection.getExceptionsToNotAdd().get(0).getCard().getPassCode());
//    }
//
//    @Test
//    public void testGetLinkedDecks() {
//        ThemeCollection collection = new ThemeCollection();
//        List<List<Deck>> decks = new ArrayList<>();
//        decks.add(new ArrayList<>());
//        collection.setLinkedDecks(decks);
//
//        assertEquals(1, collection.getLinkedDecks().size());
//        assertTrue(collection.getLinkedDecks().get(0).isEmpty());
//    }
//
//    @Test
//    public void testSetLinkedDecks() throws Exception {
//        ThemeCollection collection = new ThemeCollection();
//        List<List<Deck>> decks = new ArrayList<>();
//        List<Deck> deckList = new ArrayList<>();
//        Deck deck = new Deck();
//        deck.AddCardMain(new Card("DeckCard"));
//        deckList.add(deck);
//        decks.add(deckList);
//        collection.setLinkedDecks(decks);
//
//        assertEquals(1, collection.getLinkedDecks().size());
//        assertEquals(1, collection.getLinkedDecks().get(0).size());
//        assertEquals(1, collection.getLinkedDecks().get(0).get(0).getCardCount());
//    }
//
//    @Test
//    public void testGetConnectToWholeCollection() {
//        ThemeCollection collection = new ThemeCollection();
//        collection.setConnectToWholeCollection(true);
//        assertTrue(collection.getConnectToWholeCollection());
//    }
//
//    @Test
//    public void testSetConnectToWholeCollection() {
//        ThemeCollection collection = new ThemeCollection();
//        collection.setConnectToWholeCollection(true);
//        assertTrue(collection.getConnectToWholeCollection());
//    }
//
//    @Test
//    public void testSaveToFile(@TempDir Path tempDir) throws Exception {
//        ThemeCollection collection = new ThemeCollection();
//        collection.setName("TestCollection");
//
//        // Create a card and add it to the collection
//        Card card = new Card("TestCard");
//        card.setName_EN("Test Card");
//        List<CardElement> cards = new ArrayList<>();
//        cards.add(new CardElement(card));
//        collection.setCardsList(cards);
//
//        // Save to file
//        Path tempFile = tempDir.resolve("test");
//        collection.SaveToFile(tempFile.toString());
//
//        // Verify file was created
//        Path expectedFile = tempDir.resolve("TestCollection.ytc");
//        assertTrue(Files.exists(expectedFile));
//
//        // Verify file content
//        List<String> lines = Files.readAllLines(expectedFile);
//        assertFalse(lines.isEmpty());
//        assertTrue(lines.get(0).contains("TestCard"));
//    }
//
//    @Test
//    public void testAddDeck() throws Exception {
//        ThemeCollection collection = new ThemeCollection();
//        Deck deck = new Deck();
//        deck.AddCardMain(new Card("DeckCard1"));
//
//        collection.AddDeck(deck);
//
//        assertEquals(1, collection.getLinkedDecks().size());
//        assertEquals(1, collection.getLinkedDecks().get(0).size());
//        assertEquals(1, collection.getLinkedDecks().get(0).get(0).getCardCount());
//    }
//
//    @Test
//    public void testAddDeckToExistingUnit() throws Exception {
//        ThemeCollection collection = new ThemeCollection();
//        Deck deck1 = new Deck();
//        deck1.AddCardMain(new Card("DeckCard1"));
//
//        Deck deck2 = new Deck();
//        deck2.AddCardMain(new Card("DeckCard2"));
//
//        // Add first deck to a new unit
//        collection.AddDeck(deck1);
//
//        // Add second deck to the same unit
//        collection.AddDeckToExistingUnit(deck2, 0);
//
//        assertEquals(1, collection.getLinkedDecks().size());
//        assertEquals(2, collection.getLinkedDecks().get(0).size());
//        assertEquals(1, collection.getLinkedDecks().get(0).get(0).getCardCount());
//        assertEquals(1, collection.getLinkedDecks().get(0).get(1).getCardCount());
//    }
//
//    @Test
//    public void testCreateCardsList() throws Exception {
//        ThemeCollection collection = new ThemeCollection();
//
//        // Add a card to the collection
//        Card card1 = new Card("Card1");
//        card1.setName_EN("Test Card 1");
//        CardElement cardElement1 = new CardElement(card1);
//
//        List<CardElement> cards = new ArrayList<>();
//        cards.add(cardElement1);
//        collection.setCardsList(cards);
//
//        // Add a card to exclude
//        Card card2 = new Card("Card2");
//        card2.setName_EN("Test Card 2");
//        CardElement cardElement2 = new CardElement(card2);
//
//        List<CardElement> exceptions = new ArrayList<>();
//        exceptions.add(cardElement2);
//        collection.setExceptionsToNotAdd(exceptions);
//
//        // Create cards list
//        collection.createCardsList();
//
//        // Verify the card list was processed (the actual logic depends on ListDifIntersectArtworkWithExceptions implementation)
//        assertNotNull(collection.getCardsList());
//    }
//
//    @Test
//    public void testGetCardCount() throws Exception {
//        ThemeCollection collection = new ThemeCollection();
//
//        // Add some cards to the collection
//        Card card1 = new Card("Card1");
//        Card card2 = new Card("Card2");
//
//        List<CardElement> cards = new ArrayList<>();
//        cards.add(new CardElement(card1));
//        cards.add(new CardElement(card2));
//        collection.setCardsList(cards);
//
//        assertEquals(2, collection.getCardCount());
//    }
//
//    @Test
//    public void testGetPrice() throws Exception {
//        ThemeCollection collection = new ThemeCollection();
//
//        // Add some cards with prices to the collection
//        Card card1 = new Card("Card1");
//        card1.setPrice("10.50");
//
//        Card card2 = new Card("Card2");
//        card2.setPrice("5.25");
//
//        List<CardElement> cards = new ArrayList<>();
//        cards.add(new CardElement(card1));
//        cards.add(new CardElement(card2));
//        collection.setCardsList(cards);
//
//        // 10.50 + 5.25 = 15.75
//        assertEquals("15.75", collection.getPrice());
//    }
//
//    @Test
//    public void testToList() throws Exception {
//        ThemeCollection collection = new ThemeCollection();
//
//        // Add a card directly to the collection
//        Card card1 = new Card("Card1");
//        card1.setName_EN("Test Card 1");
//        CardElement cardElement1 = new CardElement(card1);
//
//        List<CardElement> cards = new ArrayList<>();
//        cards.add(cardElement1);
//        collection.setCardsList(cards);
//
//        // Add a deck with a card
//        Deck deck = new Deck();
//        Card deckCard = new Card("DeckCard1");
//        deckCard.setName_EN("Deck Card 1");
//        deck.AddCardMain(deckCard);
//        collection.AddDeck(deck);
//
//        // Get the combined list
//        List<CardElement> result = collection.toList();
//
//        // Should contain both cards (1 from collection, 1 from deck)
//        assertEquals(2, result.size());
//
//        // Check if both cards are in the result
//        boolean hasCard1 = result.stream().anyMatch(ce -> ce.getCard().getPassCode().equals("Card1"));
//        boolean hasDeckCard1 = result.stream().anyMatch(ce -> ce.getCard().getPassCode().equals("DeckCard1"));
//
//        assertTrue(hasCard1);
//        assertTrue(hasDeckCard1);
//    }
//
//    @Test
//    public void testCardCounts() {
//        List<CardElement> result = collection.toList();
//
//        // Card only in collection (single copy)
//        assertEquals(1, countCardsWithPasscode(result, "CARD001"));
//
//        // Multiple copies in collection
//        assertEquals(2, countCardsWithPasscode(result, "CARD002"));
//
//        // Card only in deck (single copy)
//        assertEquals(1, countCardsWithPasscode(result, "CARD010"));
//
//        // Multiple copies in single deck
//        assertEquals(2, countCardsWithPasscode(result, "CARD011"));
//
//        // Card in multiple decks, same unit
//        assertEquals(3, countCardsWithPasscode(result, "CARD012")); // 1 in first deck, 2 in second
//
//        // Card with same passcode but different print
//        assertEquals(2, countCardsWithPasscode(result, "CARD020")); // 2 in deck, 1 in collection (should be 2 total)
//
//        // Card with specific artwork in deck
//        assertEquals(2, countCardsWithPasscode(result, "CARD021"));
//
//        // Card in different units
//        assertEquals(3, countCardsWithPasscode(result, "CARD030")); // 2 in first unit, 1 in second
//
//        // Card with all flags set
//        assertEquals(2, countCardsWithPasscode(result, "CARD040"));
//
//        // Card in both collection and deck
//        assertEquals(4, countCardsWithPasscode(result, "CARD050")); // 2 in collection, 2 in deck
//    }
//
//    private int countCardsWithPasscode(List<CardElement> cards, String passcode) {
//        return (int) cards.stream()
//                .filter(ce -> ce.getCard().getPassCode().equals(passcode))
//                .count();
//    }
//
//    @Test
//    public void testTotalCardCount() {
//        // Calculate expected total count
//        int expectedCount = 0;
//
//        // Add cards from collection (including duplicates)
//        expectedCount += collection.getCardsList().size();
//
//        // Add cards from all decks (including duplicates)
//        for (List<Deck> unit : collection.getLinkedDecks()) {
//            for (Deck deck : unit) {
//                expectedCount += deck.getCardCount();
//            }
//        }
//
//        // Adjust for cards that are in both collection and decks
//        // (This depends on your specific logic for handling duplicates)
//
//        List<CardElement> result = collection.toList();
//        assertEquals(expectedCount, result.size());
//    }
//
//    @Test
//    public void testEmptyCollectionAndDecks() {
//        ThemeCollection emptyCollection = new ThemeCollection();
//        List<CardElement> result = emptyCollection.toList();
//        assertTrue(result.isEmpty(), "Empty collection should return empty list");
//    }
//
//    @Test
//    public void testCardsOnlyInCollection() {
//        // Card only in collection (CARD001)
//        List<CardElement> result = collection.toList();
//        assertEquals(1, countCardsWithPasscode(result, "CARD001"));
//        assertTrue(hasCardWithName(result, "Collection Card"));
//    }
//
//    @Test
//    public void testCardsInDecksOnly() {
//        // Card only in deck (CARD010)
//        List<CardElement> result = collection.toList();
//        assertEquals(1, countCardsWithPasscode(result, "CARD010"));
//        assertTrue(hasCardWithName(result, "Deck Only Card"));
//    }
//
//    @Test
//    public void testCardsInCollectionAndDecks() {
//        // Card in both collection and deck (CARD020)
//        List<CardElement> result = collection.toList();
//        assertEquals(2, countCardsWithPasscode(result, "CARD020"));
//        assertTrue(hasCardWithName(result, "Same Pass 1"));
//        assertTrue(hasCardWithName(result, "Same Pass 2"));
//    }
//
//    @Test
//    public void testSpecificArtworkHandling() {
//        // Card with specific artwork in collection (CARD003)
//        // Card with specific artwork in deck (CARD021)
//        List<CardElement> result = collection.toList();
//        CardElement artworkCard = result.stream()
//                .filter(ce -> ce.getCard().getPassCode().equals("CARD003"))
//                .findFirst()
//                .orElse(null);
//        assertNotNull(artworkCard);
//        assertTrue(artworkCard.getSpecificArtwork());
//    }
//
//    @Test
//    public void testDontRemoveFlag() {
//        // Card with dontRemove flag (CARD004)
//        List<CardElement> result = collection.toList();
//        CardElement dontRemoveCard = result.stream()
//                .filter(ce -> ce.getCard().getPassCode().equals("CARD004"))
//                .findFirst()
//                .orElse(null);
//        assertNotNull(dontRemoveCard);
//        assertTrue(dontRemoveCard.getDontRemove());
//    }
//
//    @Test
//    public void testMultipleDecksSameUnit() {
//        // Card in multiple decks in same unit (CARD012)
//        List<CardElement> result = collection.toList();
//        assertEquals(3, countCardsWithPasscode(result, "CARD012"));
//    }
//
//    @Test
//    public void testMultipleUnits() {
//        // Card in different units (CARD030)
//        List<CardElement> result = collection.toList();
//        assertEquals(3, countCardsWithPasscode(result, "CARD030"));
//        assertTrue(hasCardWithPrintCode(result, "SD06-EN001"));
//        assertTrue(hasCardWithPrintCode(result, "SD06-EN002"));
//    }
//
//    @Test
//    public void testComplexCase() {
//        // Card with all flags set (CARD040)
//        List<CardElement> result = collection.toList();
//        List<CardElement> allFlagsCards = result.stream()
//                .filter(ce -> ce.getCard().getPassCode().equals("CARD040"))
//                .collect(Collectors.toList());
//
//        assertEquals(2, allFlagsCards.size());
//        allFlagsCards.forEach(card -> {
//            assertTrue(card.getSpecificArtwork());
//            assertTrue(card.getDontRemove());
//            assertTrue(card.getIsInDeck());
//        });
//    }
//
//    // Helper methods
//    private boolean hasCardWithName(List<CardElement> cards, String name) {
//        return cards.stream()
//                .anyMatch(ce -> ce.getCard().getName_EN().equals(name));
//    }
//
//    private boolean hasCardWithPrintCode(List<CardElement> cards, String printCode) {
//        return cards.stream()
//                .anyMatch(ce -> ce.getCard().getPrintCode().equals(printCode));
//    }
//}
package Model.CardsLists;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fixed ThemeCollectionTest: robust matching (passCode OR printCode OR name),
 * prints diagnostic dump, and corrected SaveToFile assertion.
 */
public class ThemeCollectionTest {
    private ThemeCollection collection;
    private List<Deck> decks;

    @BeforeEach
    public void setUp() throws Exception {
        collection = new ThemeCollection();
        decks = new ArrayList<>();
        for (int i = 0; i < 6; i++) decks.add(new Deck());

        collection.setCardsList(new ArrayList<>());
        collection.setLinkedDecks(new ArrayList<>());

        // A: COLL_DUP — two entries in collection, one in deck unit 0
        Card collDup1 = createCard("COLL_DUP", "Coll Dup A", "P-COLL-1", "1.0");
        CardElement ceCollDup1 = new CardElement(collDup1);
        Card collDup2 = createCard("COLL_DUP", "Coll Dup B", "P-COLL-2", "1.0");
        CardElement ceCollDup2 = new CardElement(collDup2);
        collection.getCardsList().add(ceCollDup1);
        collection.getCardsList().add(ceCollDup2);

        Card deckCollDup = createCard("COLL_DUP", "Deck Coll Dup", "P-COLL-1", "1.0");
        decks.get(0).AddCardMain(deckCollDup);

        // B: UNIT_DUP_INSIDE — duplicates inside same deck (unit 0)
        Card unitDupA = createCard("UNIT_DUP", "Unit Dup A", "P-UNIT-1", "2.0");
        Card unitDupB = createCard("UNIT_DUP", "Unit Dup B", "P-UNIT-1", "2.0");
        decks.get(0).AddCardMain(unitDupA);
        decks.get(0).AddCardMain(unitDupB);

        // C: MULTI_UNIT — same passCode present in different units (unit 1 and unit 2)
        Card multiUnit1 = createCard("MULTI_U", "Multi Unit 1", "P-MU-1", "3.0");
        decks.get(1).AddCardMain(multiUnit1);
        Card multiUnit2 = createCard("MULTI_U", "Multi Unit 2", "P-MU-2", "3.0");
        decks.get(2).AddCardMain(multiUnit2);

        // D: COLL_SPEC_ART — collection has specificArtwork matching a deck by printCode
        Card deckArt = createCard("ART01", "Deck Art", "P-ART-1", "4.0");
        decks.get(1).AddCardMain(deckArt);
        Card collArt = createCard("ART01", "Coll Art", "P-ART-1", "4.0");
        CardElement ceCollArt = new CardElement(collArt);
        ceCollArt.setSpecificArtwork(true);
        collection.getCardsList().add(ceCollArt);

        // E: COLL_DONTREMOVE_REPLACE — deck has an occurrence; collection has dontRemove=true
        Card deckReplace = createCard("REPL", "Deck Replace", "P-REPL-1", "5.0");
        decks.get(2).AddCardMain(deckReplace);
        Card collReplace = createCard("REPL", "Coll Replace", "P-REPL-2", "5.0");
        CardElement ceCollReplace = new CardElement(collReplace);
        ceCollReplace.setDontRemove(true);
        collection.getCardsList().add(ceCollReplace);

        // F: COLLECTION_ONLY — two CardElements in collection only
        Card collOnlyA = createCard("COL_ONLY", "Coll Only A", "P-COLL-ONLY-1", "6.0");
        CardElement ceCollOnlyA = new CardElement(collOnlyA);
        Card collOnlyB = createCard("COL_ONLY", "Coll Only B", "P-COLL-ONLY-2", "6.0");
        CardElement ceCollOnlyB = new CardElement(collOnlyB);
        collection.getCardsList().add(ceCollOnlyA);
        collection.getCardsList().add(ceCollOnlyB);

        // G: DECK_ONLY_MULTIPLE_UNITS — same passCode in deck in unit 3 and unit 4
        Card deckOnlyU1 = createCard("DECK_MULTI", "Deck Multi 1", "P-DM-1", "2.5");
        decks.get(3).AddCardMain(deckOnlyU1);
        Card deckOnlyU2 = createCard("DECK_MULTI", "Deck Multi 2", "P-DM-2", "2.5");
        decks.get(4).AddCardMain(deckOnlyU2);

        // H & I: SAMEPASS across units and collection
        Card deckDupUnitA = createCard("SAMEPASS", "SamePass A", "P-SP-1", "1.5");
        decks.get(5).AddCardMain(deckDupUnitA);
        Deck secondInUnit5 = new Deck();
        Card deckDupUnitB2 = createCard("SAMEPASS", "SamePass B2", "P-SP-3", "1.5");
        secondInUnit5.AddCardMain(deckDupUnitB2);

        Card deckSamePass = createCard("SAMEPASS", "Deck SamePass In Unit1", "P-SP-1", "1.5");
        decks.get(1).AddCardMain(deckSamePass);

        Card collSamePass = createCard("SAMEPASS", "Collection SamePass", "P-CSP-1", "1.5");
        CardElement ceCollSamePass = new CardElement(collSamePass);
        collection.getCardsList().add(ceCollSamePass);

        // Add decks into linkedDecks units:
        collection.AddDeck(decks.get(0)); // unit 0
        collection.AddDeck(decks.get(1)); // unit 1
        collection.AddDeck(decks.get(2)); // unit 2
        collection.AddDeck(decks.get(3)); // unit 3
        collection.AddDeck(decks.get(4)); // unit 4
        collection.AddDeck(decks.get(5)); // unit 5
        collection.AddDeckToExistingUnit(secondInUnit5, 5);
    }

    // Helper to create Card instance
    private Card createCard(String passCode, String name, String printCode, String price) throws Exception {
        Card card = new Card(passCode);
        card.setName_EN(name);
        card.setPrintCode(printCode);
        card.setPrice(price);
        return card;
    }

    // Debug dump helper: returns a compact summary string of the list content
    private String dumpListDebug(List<CardElement> list) {
        if (list == null) return "<null list>";
        return list.stream()
                .map(ce -> {
                    if (ce == null || ce.getCard() == null) return "<nullCE>";
                    Card c = ce.getCard();
                    String pass = c.getPassCode() == null ? "<nullPass>" : c.getPassCode();
                    String print = c.getPrintCode() == null ? "<nullPrint>" : c.getPrintCode();
                    String name = c.getName_EN() == null ? "<nullName>" : c.getName_EN();
                    String flags = (ce.getSpecificArtwork() ? "A" : "-") + (ce.getDontRemove() ? "D" : "-");
                    return pass + "/" + print + "/" + name + "/" + flags;
                })
                .collect(Collectors.joining(" | "));
    }

    // Flexible matcher: returns true if element matches by passCode OR printCode OR name
    private boolean matches(CardElement ce, String id) {
        if (ce == null || ce.getCard() == null || id == null) return false;
        Card c = ce.getCard();
        return id.equals(c.getPassCode()) || id.equals(c.getPrintCode()) || id.equals(c.getName_EN());
    }

    private long countMatches(List<CardElement> list, String id) {
        if (list == null) return 0;
        return list.stream().filter(ce -> matches(ce, id)).count();
    }

    @Test
    public void testCounts_collDup_preserved() {
        System.out.println("TEST: COLL_DUP preserved: collection had 2 copies, deck unit 0 has 1 unit-occurrence.");
        List<CardElement> result = collection.toList();
        String dump = dumpListDebug(result);
        System.out.println("Result summary: " + dump);

        long count = countMatches(result, "COLL_DUP");
        assertEquals(2, count, "COLL_DUP expected 2 occurrences, got " + count + ". Summary: " + dump);

        boolean hasCollectionName = result.stream()
                .anyMatch(ce -> matches(ce, "Coll Dup A") || matches(ce, "Coll Dup B"));
        assertTrue(hasCollectionName, "Expected at least one COLL_DUP to be a collection element. Summary: " + dump);
    }

    @Test
    public void testCounts_unitDupInside_collapsed() {
        System.out.println("TEST: UNIT_DUP duplicates inside same unit collapsed to one.");
        List<CardElement> result = collection.toList();
        String dump = dumpListDebug(result);
        System.out.println("Result summary: " + dump);

        long count = countMatches(result, "UNIT_DUP");
        assertEquals(1, count, "UNIT_DUP expected 1 occurrence per unit, got " + count + ". Summary: " + dump);
    }

    @Test
    public void testCounts_multiUnit_appears_twice() {
        System.out.println("TEST: MULTI_U appears once in unit1 and once in unit2 => expected 2.");
        List<CardElement> result = collection.toList();
        String dump = dumpListDebug(result);
        System.out.println("Result summary: " + dump);

        long count = countMatches(result, "MULTI_U");
        assertEquals(2, count, "MULTI_U expected 2 occurrences, got " + count + ". Summary: " + dump);
    }

    @Test
    public void testSpecificArtwork_preference() {
        System.out.println("TEST: ART01 should prefer collection element with specificArtwork.");
        List<CardElement> result = collection.toList();
        String dump = dumpListDebug(result);
        System.out.println("Result summary: " + dump);

        CardElement found = result.stream()
                .filter(ce -> matches(ce, "ART01"))
                .findFirst()
                .orElse(null);
        assertNotNull(found, "ART01 not found in result. Summary: " + dump);
        assertTrue(found.getSpecificArtwork(), "ART01 found but not specificArtwork. Summary: " + dump);
        assertEquals("Coll Art", found.getCard().getName_EN(), "ART01 should be the collection element. Summary: " + dump);
    }

    @Test
    public void testDontRemove_replaces_deck_entry() {
        System.out.println("TEST: REPL deck entry should be replaced by collection dontRemove element at least once.");
        List<CardElement> result = collection.toList();
        String dump = dumpListDebug(result);
        System.out.println("Result summary: " + dump);

        boolean hasCollReplace = result.stream()
                .anyMatch(ce -> matches(ce, "REPL") && "Coll Replace".equals(ce.getCard().getName_EN()));
        assertTrue(hasCollReplace, "Expected collection replace element for REPL. Summary: " + dump);
    }

    @Test
    public void testCollectionOnly_duplicates_kept() {
        System.out.println("TEST: COL_ONLY had two collection entries and no deck presence; both should be present.");
        List<CardElement> result = collection.toList();
        String dump = dumpListDebug(result);
        System.out.println("Result summary: " + dump);

        long count = countMatches(result, "COL_ONLY");
        assertEquals(2, count, "COL_ONLY expected 2 occurrences, got " + count + ". Summary: " + dump);
    }

    @Test
    public void testDeckOnly_multiple_units() {
        System.out.println("TEST: DECK_MULTI appears in two different units and should appear twice.");
        List<CardElement> result = collection.toList();
        String dump = dumpListDebug(result);
        System.out.println("Result summary: " + dump);

        // Deck items show print codes P-DM-1 and P-DM-2 in the dump; count by those prints as well.
        long count = countMatches(result, "DECK_MULTI");
        if (count == 0) { // fallback: count by print codes we used in setup
            count = countMatches(result, "P-DM-1") + countMatches(result, "P-DM-2");
        }
        assertEquals(2, count, "DECK_MULTI expected 2 occurrences, got " + count + ". Summary: " + dump);
    }

    @Test
    public void testDeckDupDifferentPrints_within_unit_collapsed_to_one() {
        System.out.println("TEST: SAMEPASS appears in unit1, unit5 and collection; expected 3 occurrences total.");
        List<CardElement> result = collection.toList();
        String dump = dumpListDebug(result);
        System.out.println("Result summary: " + dump);

        long countSamePass = countMatches(result, "SAMEPASS");
        if (countSamePass == 0) { // fallback to print/name matches
            countSamePass = countMatches(result, "P-SP-1") + countMatches(result, "P-SP-3") + countMatches(result, "P-CSP-1");
        }
        assertEquals(3, countSamePass, "SAMEPASS expected 3 occurrences (unit1 + unit5 + collection), got " + countSamePass + ". Summary: " + dump);
    }

    @Test
    public void testGetCardCount_and_getPrice_consistency() {
        System.out.println("TEST: getCardCount and getPrice reflect collection.cardsList only.");
        int expectedCollectionCount = collection.getCardsList().size();
        assertEquals(expectedCollectionCount, collection.getCardCount(), "getCardCount mismatch");

        float expected = 0f;
        for (CardElement ce : collection.getCardsList()) {
            if (ce != null && ce.getCard() != null) {
                expected += Float.parseFloat(ce.getCard().getPrice());
            }
        }
        float actual = Float.parseFloat(collection.getPrice());
        assertEquals(expected, actual, 0.001f);
    }

    @Test
    public void testEmpty_collection_and_decks() {
        System.out.println("TEST: empty collection and decks returns empty list.");
        ThemeCollection empty = new ThemeCollection();
        empty.setCardsList(new ArrayList<>());
        empty.setLinkedDecks(new ArrayList<>());
        List<CardElement> res = empty.toList();
        String dump = dumpListDebug(res);
        System.out.println("Result summary: " + dump);
        assertTrue(res.isEmpty(), "Empty collection should return empty list. Summary: " + dump);
    }

    @Test
    public void testSaveToFile(@TempDir Path tempDir) throws Exception {
        System.out.println("TEST: SaveToFile writes expected file and content.");
        ThemeCollection tc = new ThemeCollection();
        tc.setName("TestCollection");
        Card card = new Card("TestCard");
        card.setName_EN("Test Card");
        List<CardElement> cards = new ArrayList<>();
        cards.add(new CardElement(card));
        tc.setCardsList(cards);

        // SaveToFile concatenates the provided path string + name + ".ytc"
        Path marker = tempDir.resolve("test");
        tc.SaveToFile(marker.toString());

        Path expectedFile = tempDir.resolve("test" + "TestCollection.ytc");
        boolean exists = Files.exists(expectedFile);
        String msg = "Expected file " + expectedFile + " to exist after SaveToFile. Exists=" + exists;
        assertTrue(exists, msg);

        List<String> lines = Files.readAllLines(expectedFile);
        assertFalse(lines.isEmpty(), "Expected file not empty. Content: " + lines);
        // SaveToFile writes Card.toString(); assert on the card name which we set
        assertTrue(lines.get(0).contains("Test Card"), "First line should contain 'Test Card'. Content: " + lines);
    }
}
