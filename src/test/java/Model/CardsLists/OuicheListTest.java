package Model.CardsLists;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OuicheListTest {

    @Test
    public void testGetMaOuicheList() {
        List<CardElement> cards = new ArrayList<>();
        OuicheList.setMaOuicheList(cards);
        assertEquals(cards, OuicheList.getMaOuicheList());
    }

    @Test
    public void testSetMaOuicheList() {
        List<CardElement> cards = new ArrayList<>();
        OuicheList.setMaOuicheList(cards);
        assertEquals(cards, OuicheList.getMaOuicheList());
    }

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

    @Test
    public void testGetThirdPartyCardsINeedList() {
        List<CardElement> cards = new ArrayList<>();
        OuicheList.setThirdPartyCardsINeedList(cards);
        assertEquals(cards, OuicheList.getThirdPartyList());
    }
}