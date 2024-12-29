package Model.CardsLists;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

public class DecksAndCollectionsListTest {

    @Test
    public void testGetDecks() throws Exception {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        List<Deck> decks = new ArrayList<>();
        list.setDecks(decks);
        assertEquals(decks, list.getDecks());
    }

    @Test
    public void testSetDecks() throws Exception {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        List<Deck> decks = new ArrayList<>();
        list.setDecks(decks);
        assertEquals(decks, list.getDecks());
    }

    @Test
    public void testGetCollections() throws Exception {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        List<ThemeCollection> collections = new ArrayList<>();
        list.setCollections(collections);
        assertEquals(collections, list.getCollections());
    }

    @Test
    public void testSetCollections() throws Exception {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        List<ThemeCollection> collections = new ArrayList<>();
        list.setCollections(collections);
        assertEquals(collections, list.getCollections());
    }

    @Test
    public void testToList() throws Exception {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        List<Deck> decks = new ArrayList<>();
        Deck deck = new Deck();
        deck.AddCardMain(new Card("TestCard"));
        decks.add(deck);
        list.setDecks(decks);
        List<CardElement> cards = list.toList();
        assertEquals(1, cards.size());
        assertEquals("TestCard", cards.get(0).getCard().getPassCode());
    }

    @Test
    public void testToListCollectionsAndLinkedDecks() throws Exception {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        List<ThemeCollection> collections = new ArrayList<>();
        ThemeCollection collection = new ThemeCollection();
        Deck deck = new Deck();
        deck.AddCardMain(new Card("TestCard"));
        collection.AddDeck(deck);
        collections.add(collection);
        list.setCollections(collections);
        List<CardElement> cards = list.toListCollectionsAndLinkedDecks();
        assertEquals(1, cards.size());
        assertEquals("TestCard", cards.get(0).getCard().getPassCode());
    }

    @Test
    public void testAddDeck() throws Exception {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        list.setDecks(new ArrayList<>());
        Deck deck = new Deck();
        list.addDeck(deck);
        assertEquals(1, list.getDecks().size());
    }

    @Test
    public void testAddCollection() throws Exception {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        list.setCollections(new ArrayList<>());
        ThemeCollection collection = new ThemeCollection();
        list.addCollection(collection);
        assertEquals(1, list.getCollections().size());
    }

    @Test
    public void testToString() throws Exception {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        List<Deck> decks = new ArrayList<>();
        Deck deck = new Deck();
        deck.setName("TestDeck");
        decks.add(deck);
        list.setDecks(decks);
        String expected = "TestDeck\n";
        assertEquals(expected, list.toString());
    }
}
