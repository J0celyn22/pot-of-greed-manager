package Model.Database;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.HashMap;

import static Model.Database.PrintCodeToKonamiId.getPrintCodeToKonamiId;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PrintCodeToKonamiIdTest {

    @Test
    public void testGetPrintCodeKonamiIdPairs() throws URISyntaxException {
        HashMap<String, String> printCodeToKonamiId = getPrintCodeToKonamiId();

        assertNotNull(printCodeToKonamiId, "getPrintCodeToKonamiId() should never return null");
        assertFalse(printCodeToKonamiId.isEmpty(), "The print code to Konami ID map should contain at least one entry");
    }
}