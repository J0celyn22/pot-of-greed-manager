package Model.Database;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;

import static Model.Database.PrintCodeToKonamiId.getPrintCodeToKonamiId;

class PrintCodeToKonamiIdTest {
    @Test
    public void testGetPrintCodeKonamiIdPairs() throws URISyntaxException {
        // Read the current revision from the file
        //String content = new String(Files.readAllBytes(Paths.get("src/main/resources/revision.txt")), StandardCharsets.UTF_8);
        //int expectedRevision = Integer.parseInt(content.trim());

        // Read the revision using the function
        getPrintCodeToKonamiId();

        // Check that the values are equal
        //assertEquals(expectedRevision, actualRevision);
        System.out.println(getPrintCodeToKonamiId());
    }
}