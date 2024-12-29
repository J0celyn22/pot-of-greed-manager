package Model.CardsLists;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

public class OwnedCardsCollectionTest {

    @Test
    public void testGetOwnedCollection() throws Exception {
        OwnedCardsCollection collection = new OwnedCardsCollection();
        List<Box> boxes = new ArrayList<>();
        collection.setOwnedCollection(boxes);
        assertEquals(boxes, collection.getOwnedCollection());
    }

    @Test
    public void testSetOwnedCollection() throws Exception {
        OwnedCardsCollection collection = new OwnedCardsCollection();
        List<Box> boxes = new ArrayList<>();
        collection.setOwnedCollection(boxes);
        assertEquals(boxes, collection.getOwnedCollection());
    }

    @Test
    public void testGetSize() throws Exception {
        OwnedCardsCollection collection = new OwnedCardsCollection();
        List<Box> boxes = new ArrayList<>();
        Box box = new Box("TestBox");
        CardsGroup group = new CardsGroup("TestGroup");
        group.AddCard(new CardElement("TestCard"));
        box.getContent().add(group);
        boxes.add(box);
        collection.setOwnedCollection(boxes);
        assertEquals(1, collection.getSize());
    }

    @Test
    public void testAddBox() throws Exception {
        OwnedCardsCollection collection = new OwnedCardsCollection();
        collection.setOwnedCollection(new ArrayList<>());
        collection.AddBox("TestBox");
        assertEquals(1, collection.getOwnedCollection().size());
        assertEquals("TestBox", collection.getOwnedCollection().get(0).getName());
    }

    @Test
    public void testAddCategoryToLastBox() throws Exception {
        OwnedCardsCollection collection = new OwnedCardsCollection();
        collection.setOwnedCollection(new ArrayList<>());
        collection.AddBox("TestBox");
        collection.AddCategoryToLastBox("TestCategory");
        assertEquals(1, collection.getOwnedCollection().get(0).getContent().size());
        assertEquals("TestCategory", collection.getOwnedCollection().get(0).getContent().get(0).getName());
    }

    @Test
    public void testAddCardToLastBox() throws Exception {
        OwnedCardsCollection collection = new OwnedCardsCollection();
        collection.setOwnedCollection(new ArrayList<>());
        collection.AddBox("TestBox");
        collection.AddCategoryToLastBox("TestCategory");
        collection.AddCardToLastBox(new CardElement("TestCard"));
        assertEquals(1, collection.getOwnedCollection().get(0).getContent().get(0).getCardList().size());
        assertEquals("TestCard", collection.getOwnedCollection().get(0).getContent().get(0).getCardList().get(0).getCard().getPassCode());
    }

    @Test
    public void testToList() throws Exception {
        OwnedCardsCollection collection = new OwnedCardsCollection();
        List<Box> boxes = new ArrayList<>();
        Box box = new Box("TestBox");
        CardsGroup group = new CardsGroup("TestGroup");
        CardElement card = new CardElement("TestCard");
        group.AddCard(card);
        box.getContent().add(group);
        boxes.add(box);
        collection.setOwnedCollection(boxes);
        List<CardElement> cards = collection.toList();
        assertEquals(1, cards.size());
        assertEquals("TestCard", cards.get(0).getCard().getPassCode());
    }

    @Test
    public void testToString() throws Exception {
        OwnedCardsCollection collection = new OwnedCardsCollection();
        List<Box> boxes = new ArrayList<>();
        Box box = new Box("TestBox");
        CardsGroup group = new CardsGroup("TestGroup");
        CardElement card = new CardElement("TestCard");
        group.AddCard(card);
        box.getContent().add(group);
        boxes.add(box);
        collection.setOwnedCollection(boxes);
        String expected = "TestBox\nTestGroup\nTestCard\n";
        assertEquals(expected, collection.toString());
    }
}
