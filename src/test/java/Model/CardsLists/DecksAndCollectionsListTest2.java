/*package Model.CardsLists;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.CardsLists.Deck;
import Model.CardsLists.ThemeCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DecksAndCollectionsListTest2 {

    private DecksAndCollectionsList decksAndCollectionsList;
    private ThemeCollection testCollection;
    private Deck testDeck;
    private Card testCard1;
    private Card testCard2;
    private Card testCard3;
    private Card testCard4;
    private Card testCard5;
    private Card testCard6;
    private Card testCard7;
    private Card testCard8;
    private Card testCard9;
    private Card testCard10;
    private Card testCard11;
    private Card testCard12;
    private Card testCard13;

    @BeforeEach
    public void setUp() throws Exception {
        // Initialize the main list
        decksAndCollectionsList = new DecksAndCollectionsList();

        // Create test cards
        testCard1 = new Card();
        testCard1.setPassCode("CARD001");
        testCard1.setName_EN("Test Card 1");

        testCard2 = new Card();
        testCard2.setPassCode("CARD002");
        testCard2.setName_EN("Test Card 2");

        testCard3 = new Card();
        testCard3.setPassCode("CARD003");
        testCard3.setName_EN("Test Card 3");

        testCard4 = new Card();
        testCard4.setPassCode("CARD004");
        testCard4.setName_EN("Test Card 4");

        testCard5 = new Card();
        testCard5.setPassCode("CARD005");
        testCard5.setName_EN("Test Card 5");

        testCard6 = new Card();
        testCard6.setPassCode("CARD006");
        testCard6.setName_EN("Test Card 6");

        testCard7 = new Card();
        testCard7.setPassCode("CARD007");
        testCard7.setName_EN("Test Card 7");

        testCard8 = new Card();
        testCard8.setPassCode("CARD008");
        testCard8.setName_EN("Test Card 8");

        // Create a test deck
        testDeck = new Deck();
        testDeck.setName("Test Deck");
        testDeck.getMainDeck().add(new CardElement(testCard1.getPassCode())); // Card 1 - No specific artwork
        testDeck.getMainDeck().add(new CardElement(testCard2.getPassCode())); // Card 2 - Will have specific artwork in collection
        testDeck.getMainDeck().add(new CardElement(testCard5.getPassCode())); // Card 5 - In deck, no specific artwork, dontRemove = true
        testDeck.getMainDeck().add(new CardElement(testCard6.getPassCode())); // Card 6 - In deck, with specific artwork, dontRemove = true

        // Create a test collection
        testCollection = new ThemeCollection();
        testCollection.setName("Test Collection");

        // Add cards to collection with different properties
        // Card 1: In deck, no specific artwork, dontRemove = false
        CardElement ce1 = new CardElement(testCard1.getPassCode());
        ce1.setSpecificArtwork(false);
        ce1.setDontRemove(false);
        testCollection.getCardsList().add(ce1);

        // Card 2: In deck, with specific artwork, dontRemove = false
        CardElement ce2 = new CardElement(testCard2.getPassCode());
        ce2.setSpecificArtwork(true);
        ce2.setDontRemove(false);
        testCollection.getCardsList().add(ce2);

        // Card 3: Not in deck, no specific artwork, dontRemove = false
        CardElement ce3 = new CardElement(testCard3.getPassCode());
        ce3.setSpecificArtwork(false);
        ce3.setDontRemove(false);
        testCollection.getCardsList().add(ce3);

        // Card 4: Not in deck, with specific artwork, dontRemove = false
        CardElement ce4 = new CardElement(testCard4.getPassCode());
        ce4.setSpecificArtwork(true);
        ce4.setDontRemove(false);
        testCollection.getCardsList().add(ce4);

        // Card 5: In deck, no specific artwork, dontRemove = true
        CardElement ce5 = new CardElement(testCard5.getPassCode());
        ce5.setSpecificArtwork(false);
        ce5.setDontRemove(true);
        testCollection.getCardsList().add(ce5);

        // Card 6: In deck, with specific artwork, dontRemove = true
        CardElement ce6 = new CardElement(testCard6.getPassCode());
        ce6.setSpecificArtwork(true);
        ce6.setDontRemove(true);
        testCollection.getCardsList().add(ce6);

        // Card 7: Not in deck, no specific artwork, dontRemove = false
        CardElement ce7 = new CardElement(testCard7.getPassCode());
        ce7.setSpecificArtwork(false);
        ce7.setDontRemove(false);
        testCollection.getCardsList().add(ce7);

        // Card 8: Not in deck, with specific artwork, dontRemove = false
        CardElement ce8 = new CardElement(testCard8.getPassCode());
        ce8.setSpecificArtwork(true);
        ce8.setDontRemove(false);
        testCollection.getCardsList().add(ce8);

        // Add test cases for multiple copies in collection and decks
        // Card 9: 2 in collection, 1 in deck, no specific artwork
        testCard9 = new Card();
        testCard9.setPassCode("CARD009");
        testCard9.setName_EN("Test Card 9");

        CardElement ce9_1 = new CardElement(testCard9.getPassCode());
        ce9_1.setSpecificArtwork(false);
        ce9_1.setDontRemove(false);
        testCollection.getCardsList().add(ce9_1);

        CardElement ce9_2 = new CardElement(testCard9.getPassCode());
        ce9_2.setSpecificArtwork(false);
        ce9_2.setDontRemove(false);
        testCollection.getCardsList().add(ce9_2);

        // Card 10: 2 in collection, 1 in deck, with specific artwork
        testCard10 = new Card();
        testCard10.setPassCode("CARD010");
        testCard10.setName_EN("Test Card 10");

        CardElement ce10_1 = new CardElement(testCard10.getPassCode());
        ce10_1.setSpecificArtwork(true);
        ce10_1.setDontRemove(false);
        testCollection.getCardsList().add(ce10_1);

        CardElement ce10_2 = new CardElement(testCard10.getPassCode());
        ce10_2.setSpecificArtwork(true);
        ce10_2.setDontRemove(false);
        testCollection.getCardsList().add(ce10_2);

        // Create a second deck for testing multiple decks
        Deck secondDeck = new Deck();
        secondDeck.setName("Second Test Deck");

        // Card 11: 1 in collection, in both decks
        testCard11 = new Card();
        testCard11.setPassCode("CARD011");
        testCard11.setName_EN("Test Card 11");

        CardElement ce11 = new CardElement(testCard11.getPassCode());
        ce11.setSpecificArtwork(false);
        ce11.setDontRemove(false);
        testCollection.getCardsList().add(ce11);

        // Card 12: 2 in collection, 1 in each deck
        testCard12 = new Card();
        testCard12.setPassCode("CARD012");
        testCard12.setName_EN("Test Card 12");

        CardElement ce12_1 = new CardElement(testCard12.getPassCode());
        ce12_1.setSpecificArtwork(false);
        ce12_1.setDontRemove(false);
        testCollection.getCardsList().add(ce12_1);

        CardElement ce12_2 = new CardElement(testCard12.getPassCode());
        ce12_2.setSpecificArtwork(false);
        ce12_2.setDontRemove(false);
        testCollection.getCardsList().add(ce12_2);

        // Card 13: 3 in collection, 1 in each deck
        testCard13 = new Card();
        testCard13.setPassCode("CARD013");
        testCard13.setName_EN("Test Card 13");

        CardElement ce13_1 = new CardElement(testCard13.getPassCode());
        ce13_1.setSpecificArtwork(false);
        ce13_1.setDontRemove(false);
        testCollection.getCardsList().add(ce13_1);

        CardElement ce13_2 = new CardElement(testCard13.getPassCode());
        ce13_2.setSpecificArtwork(false);
        ce13_2.setDontRemove(false);
        testCollection.getCardsList().add(ce13_2);

        CardElement ce13_3 = new CardElement(testCard13.getPassCode());
        ce13_3.setSpecificArtwork(false);
        ce13_3.setDontRemove(false);
        testCollection.getCardsList().add(ce13_3);

        // Add cards to the first deck
        testDeck.getMainDeck().add(new CardElement(testCard9.getPassCode()));  // Card 9
        testDeck.getMainDeck().add(new CardElement(testCard10.getPassCode())); // Card 10
        testDeck.getMainDeck().add(new CardElement(testCard11.getPassCode())); // Card 11
        testDeck.getMainDeck().add(new CardElement(testCard12.getPassCode())); // Card 12
        testDeck.getMainDeck().add(new CardElement(testCard13.getPassCode())); // Card 13

        // Add cards to the second deck
        secondDeck.getMainDeck().add(new CardElement(testCard11.getPassCode())); // Card 11
        secondDeck.getMainDeck().add(new CardElement(testCard12.getPassCode())); // Card 12
        secondDeck.getMainDeck().add(new CardElement(testCard13.getPassCode())); // Card 13

        // Add both decks to the collection
        testCollection.getLinkedDecks().add(testDeck);
        testCollection.getLinkedDecks().add(secondDeck);

        // Add the collection to the decks and collections list
        decksAndCollectionsList = new DecksAndCollectionsList();
        List<ThemeCollection> collections = new ArrayList<>();
        collections.add(testCollection);
        decksAndCollectionsList.setCollections(collections);
    }

    @Test
    public void testToList_WithCollectionAndLinkedDeck() {
        // When
        List<CardElement> result = decksAndCollectionsList.toList();

        // Then
        assertNotNull(result, "Result should not be null");
        assertEquals(5, result.size(), "Should contain 5 cards (card7 and card8 should be excluded)");

        // Verify card1 (in deck, no specific artwork) - should be from deck
        CardElement resultCard1 = findCardInList(result, testCard1.getPassCode());
        assertNotNull(resultCard1, "Card1 should be in the result");
        assertFalse(resultCard1.getSpecificArtwork(), "Card1 should not have specific artwork");
        assertTrue(resultCard1.getIsInDeck(), "Card1 should be marked as in deck");

        // Verify card2 (in deck, with specific artwork) - should be from collection
        CardElement resultCard2 = findCardInList(result, testCard2.getPassCode());
        assertNotNull(resultCard2, "Card2 should be in the result");
        assertTrue(resultCard2.getSpecificArtwork(), "Card2 should have specific artwork");
        assertTrue(resultCard2.getIsInDeck(), "Card2 should be marked as in deck");

        // Verify card5 (in deck, no specific artwork, dontRemove = true)
        CardElement resultCard5 = findCardInList(result, testCard5.getPassCode());
        assertNotNull(resultCard5, "Card5 should be in the result");
        assertTrue(resultCard5.getDontRemove(), "Card5 should have dontRemove=true");
        assertTrue(resultCard5.getIsInDeck(), "Card5 should be marked as in deck");

        // Verify card6 (in deck, with specific artwork, dontRemove = true)
        CardElement resultCard6 = findCardInList(result, testCard6.getPassCode());
        assertNotNull(resultCard6, "Card6 should be in the result");
        assertTrue(resultCard6.getSpecificArtwork(), "Card6 should have specific artwork");
        assertTrue(resultCard6.getDontRemove(), "Card6 should have dontRemove=true");
        assertTrue(resultCard6.getIsInDeck(), "Card6 should be marked as in deck");

        // Verify card3 (not in deck, dontRemove=true) - should be included
        CardElement resultCard3 = findCardInList(result, testCard3.getPassCode());
        assertNotNull(resultCard3, "Card3 should be in the result (dontRemove=true)");
        assertTrue(resultCard3.getDontRemove(), "Card3 should have dontRemove=true");

        // Verify card4 (not in deck, with specific artwork, dontRemove=false) - should be included because of specific artwork
        CardElement resultCard4 = findCardInList(result, testCard4.getPassCode());
        assertNotNull(resultCard4, "Card4 should be in the result (specific artwork)");
        assertTrue(resultCard4.getSpecificArtwork(), "Card4 should have specific artwork");

        // Verify card7 (not in deck, no specific artwork, dontRemove=false) - should not be included
        CardElement resultCard7 = findCardInList(result, testCard7.getPassCode());
        assertNull(resultCard7, "Card7 should not be in the result");

        // Verify card8 (not in deck, with specific artwork, dontRemove=false) - should be included because of specific artwork
        CardElement resultCard8 = findCardInList(result, testCard8.getPassCode());
        assertNotNull(resultCard8, "Card8 should be in the result (specific artwork)");
        assertTrue(resultCard8.getSpecificArtwork(), "Card8 should have specific artwork");


    }

    @Test
    public void testToList_WithUnlinkedDeck() throws Exception {
        // Given: Add an unlinked deck
        Deck unlinkedDeck = new Deck();
        unlinkedDeck.setName("Unlinked Deck");
        CardElement unlinkedCard = new CardElement(testCard7.getPassCode());
        unlinkedDeck.getMainDeck().add(unlinkedCard);

        List<Deck> decks = new ArrayList<>();
        decks.add(unlinkedDeck);
        decksAndCollectionsList.setDecks(decks);

        // When
        List<CardElement> result = decksAndCollectionsList.toList();

        // Then: Should include cards from both collection and unlinked deck
        assertTrue(result.size() > 5, "Should include cards from both sources");

        // Verify the unlinked deck's card is included
        CardElement resultUnlinkedCard = findCardInList(result, testCard7.getPassCode());
        assertNotNull(resultUnlinkedCard, "Should include card from unlinked deck");
        assertTrue(resultUnlinkedCard.getIsInDeck(), "Unlinked deck card should be marked as in deck");

        // Verify the original collection cards are still there
        assertNotNull(findCardInList(result, testCard1.getPassCode()), "Original collection cards should still be present");

        // In testToList_WithCollectionAndLinkedDeck():

// Test Card 9: 2 in collection, 1 in deck, no specific artwork
        long card9Count = result.stream()
                .filter(ce -> ce.getCard().getPassCode().equals(testCard9.getPassCode()))
                .count();
        assertEquals(2, card9Count, "Card9 should appear 2 times (both from collection)");

// Test Card 10: 2 in collection, 1 in deck, with specific artwork
        long card10Count = result.stream()
                .filter(ce -> ce.getCard().getPassCode().equals(testCard10.getPassCode()))
                .count();
        assertEquals(2, card10Count, "Card10 should appear 2 times (both from collection)");

// Test Card 11: 1 in collection, in both decks
        long card11Count = result.stream()
                .filter(ce -> ce.getCard().getPassCode().equals(testCard11.getPassCode()))
                .count();
        assertEquals(1, card11Count, "Card11 should appear once (from collection)");

// Test Card 12: 2 in collection, 1 in each deck
        long card12Count = result.stream()
                .filter(ce -> ce.getCard().getPassCode().equals(testCard12.getPassCode()))
                .count();
        assertEquals(2, card12Count, "Card12 should appear 2 times (both from collection)");

// Test Card 13: 3 in collection, 1 in each deck
        long card13Count = result.stream()
                .filter(ce -> ce.getCard().getPassCode().equals(testCard13.getPassCode()))
                .count();
        assertEquals(3, card13Count, "Card13 should appear 3 times (all from collection)");

// Verify isInDeck flag is set correctly for cards in decks
        List<CardElement> card11InResult = new ArrayList<>();
        for (CardElement cardElement : result) {
            if (cardElement.getCard().getPassCode().equals(testCard11.getPassCode())) {
                card11InResult.add(cardElement);
            }
        }
        assertTrue(card11InResult.get(0).getIsInDeck(), "Card11 should be marked as in deck");

        List<CardElement> card12InResult = new ArrayList<>();
        for (CardElement cardElement : result) {
            if (cardElement.getCard().getPassCode().equals(testCard12.getPassCode())) {
                card12InResult.add(cardElement);
            }
        }
        assertTrue(card12InResult.get(0).getIsInDeck(), "First Card12 should be marked as in deck");
        assertTrue(card12InResult.get(1).getIsInDeck(), "Second Card12 should be marked as in deck");

        List<CardElement> card13InResult = new ArrayList<>();
        for (CardElement ce : result) {
            if (ce.getCard().getPassCode().equals(testCard13.getPassCode())) {
                card13InResult.add(ce);
            }
        }
        assertTrue(card13InResult.get(0).getIsInDeck(), "First Card13 should be marked as in deck");
        assertTrue(card13InResult.get(1).getIsInDeck(), "Second Card13 should be marked as in deck");
        assertTrue(card13InResult.get(2).getIsInDeck(), "Third Card13 should be marked as in deck");
    }

    @Test
    public void testToList_EmptyCollection() {
        // Given: Empty the collection
        decksAndCollectionsList.setCollections(new ArrayList<>());

        // When
        List<CardElement> result = decksAndCollectionsList.toList();

        // Then: Should be empty since we have no collections and no unlinked decks
        assertTrue(result.isEmpty(), "Should return empty list for empty collection and no unlinked decks");
    }

    private CardElement findCardInList(List<CardElement> list, String passCode) {
        if (list == null || passCode == null) {
            return null;
        }
        return list.stream()
                .filter(ce -> ce != null && ce.getCard() != null && passCode.equals(ce.getCard().getPassCode()))
                .findFirst()
                .orElse(null);
    }
}*/