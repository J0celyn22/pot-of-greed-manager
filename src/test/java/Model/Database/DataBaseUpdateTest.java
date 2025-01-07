package Model.Database;

import Model.FilePaths;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URISyntaxException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataBaseUpdateTest {
    @ParameterizedTest
    @CsvSource({
            "cardinfo.json, ygoprodeck\\cardinfo.json, https://db.ygoprodeck.com/api/v7/cardinfo.php",
            "en.json, ygoresources\\name\\en.json, https://db.ygoresources.com/data/idx/card/name/en",
            "fr.json, ygoresources\\name\\fr.json, https://db.ygoresources.com/data/idx/card/name/fr",
            "ja.json, ygoresources\\name\\ja.json, https://db.ygoresources.com/data/idx/card/name/ja",
            "_sets.txt, ygoresources\\printcode\\_sets.txt, https://db.ygoresources.com/data/idx/printcode/_sets",
            "15AX-JP.json, ygoresources\\printcode\\15AX-JP.json, https://db.ygoresources.com/data/idx/printcode/15AX-JP",
            "46986414.jpg, ygoprodeck\\images\\46986414.jpg, https://images.ygoprodeck.com/images/cards/46986414.jpg",
            "cards_Lite.json, mdpro3\\cards_Lite.json, https://code.moenext.com/sherry_chaos/MDPro3/-/raw/master/Data/cards_Lite.json"
    })
    public void testGetAddresses(String input, String expectedPath, String expectedAddress) throws URISyntaxException {
        String[] res = DataBaseUpdate.getAddresses(input);
        assertEquals(FilePaths.databaseDir + "\\" + expectedPath, res[0]);
        assertEquals(expectedAddress, res[1]);
    }
}