package Model.Database;

import Model.CardsLists.Card;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DatabaseTest {

    @Test
    void createAllCardsListTest() {
        Map<Integer, Card> cardsList = Database.getAllCardsList();

        assertNotNull(cardsList, "getAllCardsList() should never return null");
        assertFalse(cardsList.isEmpty(), "The card database should contain at least one card");
    }
}