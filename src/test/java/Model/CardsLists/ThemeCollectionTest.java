package Model.CardsLists;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

public class ThemeCollectionTest {

    @Test
    public void testGetName() {
        ThemeCollection collection = new ThemeCollection();
        collection.setName("TestCollection");
        assertEquals("TestCollection", collection.getName());
    }

    @Test
    public void testSetName() {
        ThemeCollection collection = new ThemeCollection();
        collection.setName("TestCollection");
        assertEquals("TestCollection", collection.getName());
    }

    @Test
    public void testGetCardsMap() {
        ThemeCollection collection = new ThemeCollection();
        List<CardElement> cardsList = new ArrayList<>();
        collection.setCardsList(cardsList);
        assertEquals(cardsList, collection.getCardsList());
    }

    @Test
    public void testGetCardsList() throws Exception {
        ThemeCollection collection = new ThemeCollection();
        List<CardElement> cardsList = new ArrayList<>();
        Card card = new Card("TestCard");
        cardsList.add(new CardElement(card));
        collection.setCardsList(cardsList);

        List<CardElement> cardsList2 = collection.getCardsList();
        assertEquals(1, cardsList2.size());
        assertEquals("TestCard", cardsList2.get(0).getCard().getPassCode());
    }

    @Test
    public void testSetCardsList() {
        ThemeCollection collection = new ThemeCollection();
        List<CardElement> cardsList = new ArrayList<>();
        collection.setCardsList(cardsList);
        assertEquals(cardsList, collection.getCardsList());
    }

    @Test
    public void testGetExceptionsToNotAdd() {
        ThemeCollection collection = new ThemeCollection();
        List<CardElement> exceptions = new ArrayList<>();
        collection.setExceptionsToNotAdd(exceptions);
        assertEquals(exceptions, collection.getExceptionsToNotAdd());
    }

    @Test
    public void testSetExceptionsToNotAdd() {
        ThemeCollection collection = new ThemeCollection();
        List<CardElement> exceptions = new ArrayList<>();
        collection.setExceptionsToNotAdd(exceptions);
        assertEquals(exceptions, collection.getExceptionsToNotAdd());
    }

    @Test
    public void testGetLinkedDecks() {
        ThemeCollection collection = new ThemeCollection();
        List<Deck> decks = new ArrayList<>();
        collection.setLinkedDecks(decks);
        assertEquals(decks, collection.getLinkedDecks());
    }

    @Test
    public void testSetLinkedDecks() {
        ThemeCollection collection = new ThemeCollection();
        List<Deck> decks = new ArrayList<>();
        collection.setLinkedDecks(decks);
        assertEquals(decks, collection.getLinkedDecks());
    }

    @Test
    public void testGetConnectToWholeCollection() {
        ThemeCollection collection = new ThemeCollection();
        collection.setConnectToWholeCollection(true);
        assertTrue(collection.getConnectToWholeCollection());
    }

    @Test
    public void testSetConnectToWholeCollection() {
        ThemeCollection collection = new ThemeCollection();
        collection.setConnectToWholeCollection(true);
        assertTrue(collection.getConnectToWholeCollection());
    }

    @Test
    public void testToList() throws Exception {
        ThemeCollection collection = new ThemeCollection();
        Deck deck = new Deck();
        deck.AddCardMain(new Card("TestCard"));
        collection.AddDeck(deck);
        List<CardElement> cards = collection.toList();
        assertEquals(1, cards.size());
        assertEquals("TestCard", cards.get(0).getCard().getPassCode());
    }
}
