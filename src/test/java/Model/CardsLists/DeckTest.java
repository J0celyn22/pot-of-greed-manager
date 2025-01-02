package Model.CardsLists;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeckTest {

    @Test
    public void testGetName() {
        Deck deck = new Deck();
        deck.setName("TestDeck");
        assertEquals("TestDeck", deck.getName());
    }

    @Test
    public void testSetName() {
        Deck deck = new Deck();
        deck.setName("TestDeck");
        assertEquals("TestDeck", deck.getName());
    }

    @Test
    public void testGetMainDeck() {
        Deck deck = new Deck();
        List<CardElement> mainDeck = new ArrayList<>();
        deck.setMainDeck(mainDeck);
        assertEquals(mainDeck, deck.getMainDeck());
    }

    @Test
    public void testSetMainDeck() {
        Deck deck = new Deck();
        List<CardElement> mainDeck = new ArrayList<>();
        deck.setMainDeck(mainDeck);
        assertEquals(mainDeck, deck.getMainDeck());
    }

    @Test
    public void testGetExtraDeck() {
        Deck deck = new Deck();
        List<CardElement> extraDeck = new ArrayList<>();
        deck.setExtraDeck(extraDeck);
        assertEquals(extraDeck, deck.getExtraDeck());
    }

    @Test
    public void testSetExtraDeck() {
        Deck deck = new Deck();
        List<CardElement> extraDeck = new ArrayList<>();
        deck.setExtraDeck(extraDeck);
        assertEquals(extraDeck, deck.getExtraDeck());
    }

    @Test
    public void testGetSideDeck() {
        Deck deck = new Deck();
        List<CardElement> sideDeck = new ArrayList<>();
        deck.setSideDeck(sideDeck);
        assertEquals(sideDeck, deck.getSideDeck());
    }

    @Test
    public void testSetSideDeck() {
        Deck deck = new Deck();
        List<CardElement> sideDeck = new ArrayList<>();
        deck.setSideDeck(sideDeck);
        assertEquals(sideDeck, deck.getSideDeck());
    }

    @Test
    public void testAddCardMain() throws Exception {
        Deck deck = new Deck();
        CardElement card = new CardElement("TestCard");
        deck.AddCardMain(card);
        assertTrue(deck.getMainDeck().contains(card));
    }

    @Test
    public void testAddCardExtra() throws Exception {
        Deck deck = new Deck();
        CardElement card = new CardElement("TestCard");
        deck.AddCardExtra(card);
        assertTrue(deck.getExtraDeck().contains(card));
    }

    @Test
    public void testAddCardSide() throws Exception {
        Deck deck = new Deck();
        CardElement card = new CardElement("TestCard");
        deck.AddCardSide(card);
        assertTrue(deck.getSideDeck().contains(card));
    }

    @Test
    public void testToList() throws Exception {
        Deck deck = new Deck();
        Card card = new Card("TestCard");
        deck.AddCardMain(card);
        assertEquals(1, deck.toList().size());
        assertEquals("TestCard", deck.toList().get(0).getCard().getPassCode());
    }
}
