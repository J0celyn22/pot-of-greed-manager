package Model.Database;

import Model.CardsLists.Card;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static Model.Database.Database.populateJsonContentMap;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {
    /*@Test
    void populateJsonContentMapTest() throws IOException {
        populateJsonContentMap();

        int currentRevision = DataBaseUpdate.readLocalRevision();
        assert true;
    }*/

    @Test
    void createAllCardsListTest() throws Exception {
        Map<Integer, Card> cardsList = Database.getAllCardsList();
        for(int i = 0; i < cardsList.size(); i++) {
            System.out.println(cardsList.get(i));
        }

        assert true;
    }
}