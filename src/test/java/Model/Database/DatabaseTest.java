package Model.Database;

import Model.CardsLists.Card;
import org.junit.jupiter.api.Test;

import java.util.Map;

class DatabaseTest {
    /*@Test
    void populateJsonContentMapTest() throws IOException {
        populateJsonContentMap();

        int currentRevision = DataBaseUpdate.readLocalRevision();
        assert true;
    }*/

    @Test
    void createAllCardsListTest() {
        Map<Integer, Card> cardsList = Database.getAllCardsList();
        for(int i = 0; i < cardsList.size(); i++) {
            System.out.println(cardsList.get(i));
        }

        assert true;
    }
}