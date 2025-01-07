package Model.Database;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static Model.FilePaths.databaseDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doReturn;

@SuppressWarnings({"DuplicateExpressions", "InstantiationOfUtilityClass"})
public class IntegrationTest {

    @ParameterizedTest
    @CsvSource({
            "cardinfo.json, ygoprodeck\\cardinfo.json, https://db.ygoprodeck.com/api/v7/cardinfo.php",
            "en.json, ygoresources\\name\\en.json, https://db.ygoresources.com/data/idx/card/name/en",
            "fr.json, ygoresources\\name\\fr.json, https://db.ygoresources.com/data/idx/card/name/fr",
            "ja.json, ygoresources\\name\\ja.json, https://db.ygoresources.com/data/idx/card/name/ja",
            "_sets.txt, ygoresources\\printcode\\_sets.txt, https://db.ygoresources.com/data/idx/printcode/_sets",
            "15AX-JP.json, ygoresources\\printcode\\15AX-JP.json, https://db.ygoresources.com/data/idx/printcode/15AX-JP",
            "46986414.jpg, ygoprodeck\\images\\46986414.jpg, https://images.ygoprodeck.com/images/cards/46986414.jpg",
            "cards_Lite.json, mdpro3\\cards_Lite.json, https://code.moenext.com/sherry_chaos/MDPro3/-/raw/master/Data/cards_Lite.json",
            "archetypes.json, ygoprodeck\\archetypes.json, https://db.ygoprodeck.com/api/v7/archetypes.php"
    })
    public void testFetchFile(String input, String expectedPath, String expectedAddress) throws IOException, URISyntaxException {
        // Get the directory where the JAR file is located
        expectedPath = databaseDir + "\\" + expectedPath;

        // Delete the file if it exists
        Files.deleteIfExists(Paths.get(expectedPath));

        // Fetch the file
        FileFetcher.fetchFile(input);

        // Test the presence of the file
        assertTrue(Files.exists(Paths.get(expectedPath)));
    }

    @Test
    public void testInvalidateAndFetchFile() throws IOException {
        String file = "en.json";
        String[] addresses = DataBaseUpdate.getAddresses(file);
        String localPath = addresses[0];

        // Modify en.json locally
        String originalContent = Files.readString(Paths.get(localPath));
        String modifiedContent = originalContent + "\nModified content";
        Files.writeString(Paths.get(localPath), modifiedContent);

        // Invalidate the path
        FileFetcher.invalidatedPaths.add(localPath);

        // Fetch the file again
        FileFetcher.fetchFile(file);

        // Verify the file has been reverted to remove the changes
        String fetchedContent = Files.readString(Paths.get(localPath));
        assertEquals(originalContent, fetchedContent);
    }

    @Test
    public void testIntegrationSet() throws IOException, URISyntaxException {
        IntegrationTest integrationTest = new IntegrationTest();

        // Call the first integration test
        integrationTest.testFetchFile("cardinfo.json", "ygoprodeck\\cardinfo.json", "https://db.ygoprodeck.com/api/v7/cardinfo.php");
        integrationTest.testFetchFile("en.json", "ygoresources\\name\\en.json", "https://db.ygoresources.com/data/idx/card/name/en");
        integrationTest.testFetchFile("fr.json", "ygoresources\\name\\fr.json", "https://db.ygoresources.com/data/idx/card/name/fr");
        integrationTest.testFetchFile("ja.json", "ygoresources\\name\\ja.json", "https://db.ygoresources.com/data/idx/card/name/ja");
        integrationTest.testFetchFile("_sets.txt", "ygoresources\\printcode\\_sets.txt", "https://db.ygoresources.com/data/idx/printcode/_sets");
        integrationTest.testFetchFile("15AX-JP.json", "ygoresources\\printcode\\15AX-JP.json", "https://db.ygoresources.com/data/idx/printcode/15AX-JP");
        integrationTest.testFetchFile("46986414.jpg", "ygoprodeck\\images\\46986414.jpg", "https://images.ygoprodeck.com/images/cards/46986414.jpg");
        integrationTest.testFetchFile("cards_Lite.json", "mdpro3\\cards_Lite.json", "https://code.moenext.com/sherry_chaos/MDPro3/-/raw/master/Data/cards_Lite.json");
        integrationTest.testFetchFile("archetypes.json", "ygoprodeck\\archetypes.json", "https://db.ygoprodeck.com/api/v7/archetypes.php");

        // Call the second integration test
        integrationTest.testInvalidateAndFetchFile();

        // Call the first integration test again
        integrationTest.testFetchFile("cardinfo.json", "ygoprodeck\\cardinfo.json", "https://db.ygoprodeck.com/api/v7/cardinfo.php");
        integrationTest.testFetchFile("en.json", "ygoresources\\name\\en.json", "https://db.ygoresources.com/data/idx/card/name/en");
        integrationTest.testFetchFile("fr.json", "ygoresources\\name\\fr.json", "https://db.ygoresources.com/data/idx/card/name/fr");
        integrationTest.testFetchFile("ja.json", "ygoresources\\name\\ja.json", "https://db.ygoresources.com/data/idx/card/name/ja");
        integrationTest.testFetchFile("_sets.txt", "ygoresources\\printcode\\_sets.txt", "https://db.ygoresources.com/data/idx/printcode/_sets");
        integrationTest.testFetchFile("15AX-JP.json", "ygoresources\\printcode\\15AX-JP.json", "https://db.ygoresources.com/data/idx/printcode/15AX-JP");
        integrationTest.testFetchFile("46986414.jpg", "ygoprodeck\\images\\46986414.jpg", "https://images.ygoprodeck.com/images/cards/46986414.jpg");
        integrationTest.testFetchFile("cards_Lite.json", "mdpro3\\cards_Lite.json", "https://code.moenext.com/sherry_chaos/MDPro3/-/raw/master/Data/cards_Lite.json");
        integrationTest.testFetchFile("archetypes.json", "ygoprodeck\\archetypes.json", "https://db.ygoprodeck.com/api/v7/archetypes.php");
    }

    @Test
    public void testUpdateLocalRevision() throws IOException, URISyntaxException {
        // Read the current revision
        int currentRevision = DataBaseUpdate.readLocalRevision();

        // Increment the revision by 1
        int newRevision = currentRevision + 1;

        // Update the local revision
        DataBaseUpdate.updateLocalRevision(newRevision);

        // Check the new revision
        int updatedRevision = DataBaseUpdate.readLocalRevision();
        assertEquals(newRevision, updatedRevision);

        // Revert the value in the revision.txt
        DataBaseUpdate.updateLocalRevision(currentRevision);
    }

    @Test
    public void testReadLocalRevision() throws IOException, URISyntaxException {
        // Read the current revision from the file
        String content = Files.readString(Path.of(databaseDir + "\\ygoresources\\revision.txt"));
        int expectedRevision = Integer.parseInt(content.trim());

        // Read the revision using the function
        int actualRevision = DataBaseUpdate.readLocalRevision();

        // Check that the values are equal
        assertEquals(expectedRevision, actualRevision);
    }

    @Disabled
    @Test
    public void testUpdateCache() throws IOException, URISyntaxException {
        String file = "en.json";
        String[] addresses = DataBaseUpdate.getAddresses(file);
        String localPath = addresses[0];
        int currentRevision = DataBaseUpdate.readLocalRevision();

        DataBaseUpdate.updateLocalRevision(currentRevision - 1);

        // Fetch en.json
        FileFetcher.fetchFile(file);

        // Modify en.json locally
        String originalContent = Files.readString(Paths.get(localPath));
        String modifiedContent = originalContent + "\nModified content";
        Files.writeString(Paths.get(localPath), modifiedContent);

        // Mock the fetchManifest function to invalidate en.json's path
        DataBaseUpdate cacheUpdater = Mockito.spy(new DataBaseUpdate());
        doReturn("{ \"ygoresources\": { \"name\": { \"en.json\": 1 } } }").when(cacheUpdater);
        DataBaseUpdate.fetchManifest(anyInt());

        // Call the updateCache function
        DataBaseUpdate.updateCache();

        // Fetch en.json again
        FileFetcher.fetchFile(file);

        // Verify the file has been reverted to remove the changes
        String fetchedContent = Files.readString(Paths.get(localPath));
        assertEquals(originalContent, fetchedContent);

        // Revert the revision number to its original value
        DataBaseUpdate.updateLocalRevision(currentRevision);
    }
}
