package Model.CardsLists;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DecksAndCollectionsListTest {

    @Test
    public void testGetDecks() {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        List<Deck> decks = new ArrayList<>();
        list.setDecks(decks);
        assertEquals(decks, list.getDecks());
    }

    @Test
    public void testSetDecks() {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        List<Deck> decks = new ArrayList<>();
        list.setDecks(decks);
        assertEquals(decks, list.getDecks());
    }

    @Test
    public void testGetCollections() {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        List<ThemeCollection> collections = new ArrayList<>();
        list.setCollections(collections);
        assertEquals(collections, list.getCollections());
    }

    @Test
    public void testSetCollections() {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        List<ThemeCollection> collections = new ArrayList<>();
        list.setCollections(collections);
        assertEquals(collections, list.getCollections());
    }

    @Test
    public void testToList_WithStandaloneDeck() throws Exception {
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

    /**
     * Covers the path in {@link DecksAndCollectionsList#toList()} that handles a
     * non-loose {@link ThemeCollection} (connectToWholeCollection = false, the
     * default): that path should simply delegate to the collection's own
     * {@code toList()} and include its cards in the merged result. The more
     * detailed merge scenarios (artwork preference, dontRemove, per-unit
     * duplicate counts) already have thorough, currently-passing coverage in
     * ThemeCollectionTest directly against ThemeCollection#toList(); this test
     * only confirms the delegation itself happens at the
     * DecksAndCollectionsList entry point.
     */
    @Test
    public void testToList_DelegatesToNonLooseCollection() throws Exception {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        ThemeCollection collection = new ThemeCollection();

        Card card = new Card();
        card.setPassCode("COLLCARD1");
        collection.getCardsList().add(new CardElement(card));

        List<ThemeCollection> collections = new ArrayList<>();
        collections.add(collection);
        list.setCollections(collections);

        List<CardElement> cards = list.toList();

        assertEquals(1, cards.size());
        assertEquals("COLLCARD1", cards.get(0).getCard().getPassCode());
    }

    @Test
    public void testToListCollectionsAndLinkedDecks() throws Exception {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        List<ThemeCollection> collections = new ArrayList<>();
        ThemeCollection collection = new ThemeCollection();
        Deck deck = new Deck();
        deck.AddCardMain(new Card("TestCard"));
        collection.addDeck(deck);
        collections.add(collection);
        list.setCollections(collections);
        List<CardElement> cards = list.toListCollectionsAndLinkedDecks();
        assertEquals(1, cards.size());
        assertEquals("TestCard", cards.get(0).getCard().getPassCode());
    }

    @Test
    public void testAddDeck() {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        list.setDecks(new ArrayList<>());
        Deck deck = new Deck();
        list.addDeck(deck);
        assertEquals(1, list.getDecks().size());
    }

    @Test
    public void testAddCollection() {
        DecksAndCollectionsList list = new DecksAndCollectionsList();
        list.setCollections(new ArrayList<>());
        ThemeCollection collection = new ThemeCollection();
        list.addCollection(collection);
        assertEquals(1, list.getCollections().size());
    }

    @Test
    public void testToString() {
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