package Model.Database;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URL;
import java.util.Map;

import static Model.Database.ReadFile.readFile;

public class DataBaseUpdate {
    public static void updateCache() {
        try {
            int localRevision = readLocalRevision();
            int remoteRevision = getRemoteRevision();

            for (int revision = localRevision; revision < remoteRevision; revision++) {
                String manifestContent = fetchManifest(revision);
                JSONObject manifestJson = new JSONObject(manifestContent);
                invalidatePaths(manifestJson);
            }

            updateLocalRevision(remoteRevision);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int readLocalRevision() throws IOException {
        String content = new String(Files.readAllBytes(Paths.get("src/main/resources/revision.txt")));
        return Integer.parseInt(content.trim());
    }

    public static int getRemoteRevision() throws IOException {
        URL url = new URL("https://db.ygoresources.com/manifest/0"); // Dummy request to get the header
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        //connection.setRequestMethod("HEAD");
        return Integer.parseInt(connection.getHeaderField("X-Cache-Revision"));
    }

    public static String fetchManifest(int revision) throws IOException {
        URL url = new URL("https://db.ygoresources.com/manifest/" + revision);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        try (InputStream inputStream = connection.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void invalidatePaths(JSONObject manifestJson) {
        for (String key : manifestJson.keySet()) {
            JSONObject subJson = manifestJson.getJSONObject(key);
            for (String subKey : subJson.keySet()) {
                Object subValue = subJson.get(subKey);
                if (subValue instanceof JSONObject) {
                    JSONObject subSubJson = (JSONObject) subValue;
                    for (String subSubKey : subSubJson.keySet()) {
                        String[] addresses = DataBaseUpdate.getAddresses(subSubKey);
                        if (addresses.length > 0) {
                            String localPath = addresses[0];
                            FileFetcher.invalidatedPaths.add(localPath);
                            System.out.println("Invalidated local path: " + localPath);
                        }
                    }
                }
            }
        }

        // Invalidate the specific key "cardinfo.json"
        String[] cardInfoAddresses = DataBaseUpdate.getAddresses("cardinfo.json");
        if (cardInfoAddresses.length > 0) {
            String cardInfoLocalPath = cardInfoAddresses[0];
            FileFetcher.invalidatedPaths.add(cardInfoLocalPath);
            System.out.println("Invalidated local path: " + cardInfoLocalPath);
        }

        // Invalidate the specific key "archetypes.json"
        String[] archetypesAddresses = DataBaseUpdate.getAddresses("archetypes.json");
        if (archetypesAddresses.length > 0) {
            String archetypesLocalPath = archetypesAddresses[0];
            FileFetcher.invalidatedPaths.add(archetypesLocalPath);
            System.out.println("Invalidated local path: " + archetypesLocalPath);
        }

        // Invalidate the specific key "_sets.txt"
        String[] setsAddresses = DataBaseUpdate.getAddresses("_sets.txt");
        if (setsAddresses.length > 0) {
            String setsLocalPath = setsAddresses[0];
            FileFetcher.invalidatedPaths.add(setsLocalPath);
            System.out.println("Invalidated local path: " + setsLocalPath);
        }
    }



    public static void updateLocalRevision(int newRevision) throws IOException {
        Path revisionFilePath = Paths.get("src/main/resources/revision.txt");
        if (!Files.exists(revisionFilePath)) {
            Files.createFile(revisionFilePath);
        }
        Files.write(revisionFilePath, String.valueOf(newRevision).getBytes());
    }

    /**
     * Retrieves the local and remote paths for a given element in the addresses.json file.
     *
     * @param element the element to search for in the addresses.json file, which can be a file name or a key
     * @return an array containing the local path and the remote path if the element is found, an empty array otherwise
     */
    public static String[] getAddresses(String element) {
        try {
            // Read the JSON file
            String content = new String(Files.readAllBytes(Paths.get("src/main/java/Model/Database/addresses.json")));
            JSONObject json = new JSONObject(content);

            // Determine the key to use based on the exceptions
            String key = element;
            String replacement = "";
            if (element.matches("\\d+\\.jpg")) {
                key = "<passcode>.jpg";
                replacement = element.split("\\.")[0];
            } else if (element.contains("-")) {
                key = "<printcode>.json";
                replacement = element.split("\\.")[0];
            } /*else if (element.equals("_sets.txt")) {
                key = "_sets.txt";
            }*/

            // Find the element in the JSON object
            String[] result = findElement(json, key, element, "..\\Database\\", replacement);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new String[0];
    }

    /**
     * Recursively searches for a key in a JSON object and returns the corresponding remote path if found.
     *
     * @param json        the JSON object to search in
     * @param key         the key to search for
     * @param originalKey the original key to return in the array
     * @param path        the current path to append to
     * @param replacement the replacement string for passcode and printcode
     * @return an array containing the local path and the remote path if the key is found, null otherwise
     */
    //TODO redo to make it recursive again + repair sets
    private static String[] findElement(JSONObject json, String key, String originalKey, String path, String replacement) {
        // Iterate over keys in the JSON object
        for (String k : json.keySet()) {
            Object value = json.get(k);
            // If the value is a JSON object, recursively call findElement
            if (value instanceof JSONObject) {
                String[] result = findElement((JSONObject) value, key, originalKey, path + k + "\\", replacement);
                if (result != null) {
                    return result;
                }
            } else if (k.equals(key)) {
                // If the key is found, return the local path and the remote path with replacements
                String remotePath = value.toString().replace("<passcode>", replacement).replace("<printcode>", replacement);
                return new String[]{path + originalKey, remotePath};
            }
        }
        // If the key is not found, return null
        return null;
    }

    //TODO method to download all non-existing files
}
