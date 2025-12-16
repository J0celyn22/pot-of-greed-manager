package Model.Database;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static Model.FilePaths.databaseDir;

public class DataBaseUpdate {
    /**
     * Updates the local cache to the latest revision available online.
     *
     * It does this by comparing the local revision with the remote revision and then fetching the manifests
     * for the missing revisions. The manifests are used to invalidate the paths for files that have changed.
     * Finally, the local revision is updated to match the latest revision.
     */
    public static void updateCache() {
        try {
            int localRevision = readLocalRevision();
            int remoteRevision = getRemoteRevision();
            if (localRevision != 0) {
                for (int revision = localRevision; revision < remoteRevision; revision++) {
                    String manifestContent = fetchManifest(revision);
                    JSONObject manifestJson = new JSONObject(manifestContent);
                    invalidatePaths(manifestJson);
                }
            }
            updateLocalRevision(remoteRevision);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads the current revision from the local file at ../Database/ygoresources/revision.txt.
     */
    public static int readLocalRevision() throws IOException, URISyntaxException {
        Path filePath = databaseDir.resolve(Paths.get("ygoresources", "revision.txt"));
        if (!Files.exists(filePath)) {
            Files.createDirectories(filePath.getParent());
            Files.createFile(filePath);
            Files.writeString(filePath, "0");
        }
        byte[] encoded = Files.readAllBytes(Paths.get(filePath.toUri()));
        String content = new String(encoded, StandardCharsets.UTF_8);
        return Integer.parseInt(content.trim());
    }

    /**
     * Reads the current revision from the remote location at https://db.ygoresources.com/manifest/0.
     */
    public static int getRemoteRevision() throws IOException {
        URL url = new URL("https://db.ygoresources.com/manifest/0");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        return Integer.parseInt(connection.getHeaderField("X-Cache-Revision"));
    }

    /**
     * Fetches the manifest file for the specified revision from the remote location.
     */
    public static String fetchManifest(int revision) throws IOException {
        URL url = new URL("https://db.ygoresources.com/manifest/" + revision);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        try (InputStream inputStream = connection.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Invalidates local file paths based on the provided manifest JSON object.
     */
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
        String[] cardInfoAddresses = DataBaseUpdate.getAddresses("cardinfo.json");
        if (cardInfoAddresses.length > 0) {
            String cardInfoLocalPath = cardInfoAddresses[0];
            FileFetcher.invalidatedPaths.add(cardInfoLocalPath);
            System.out.println("Invalidated local path: " + cardInfoLocalPath);
        }
        String[] archetypesAddresses = DataBaseUpdate.getAddresses("archetypes.json");
        if (archetypesAddresses.length > 0) {
            String archetypesLocalPath = archetypesAddresses[0];
            FileFetcher.invalidatedPaths.add(archetypesLocalPath);
            System.out.println("Invalidated local path: " + archetypesLocalPath);
        }
        String[] setsAddresses = DataBaseUpdate.getAddresses("_sets.txt");
        if (setsAddresses.length > 0) {
            String setsLocalPath = setsAddresses[0];
            FileFetcher.invalidatedPaths.add(setsLocalPath);
            System.out.println("Invalidated local path: " + setsLocalPath);
        }
    }

    /**
     * Updates the local revision number in the file located at ../Database/ygoresources/revision.txt.
     */
    public static void updateLocalRevision(int newRevision) throws IOException, URISyntaxException {
        Path revisionFilePath = databaseDir.resolve(Paths.get("ygoresources", "revision.txt"));
        if (!Files.exists(revisionFilePath)) {
            Files.createDirectories(revisionFilePath.getParent());
            Files.createFile(revisionFilePath);
        }
        Files.write(revisionFilePath, String.valueOf(newRevision).getBytes());
    }

    /**
     * Retrieves the local and remote paths for a given element in the addresses.json file.
     */
    public static String[] getAddresses(String element) {
        try {
            byte[] encoded;
            try {
                encoded = Files.readAllBytes(Paths.get(Paths.get("./src/main/java/Model/Database/addresses.json").toUri()));
            } catch (Exception e) {
                encoded = Files.readAllBytes(Paths.get(Paths.get("resources/addresses.json").toUri()));
            }
            String content = new String(encoded, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(content);
            String key = element;
            String replacement = "";
            if (element.matches("\\d+\\.jpg")) {
                key = "<passcode>.jpg";
                replacement = element.split("\\.")[0];
            } else if (element.matches("\\d+\\.json")) {
                key = "<konamiId>.json";
                replacement = element.split("\\.")[0];
            } else if (element.contains("-")) {
                key = "<printcode>.json";
                replacement = element.split("\\.")[0];
            }
            String[] result = findElement(json, key, element, String.valueOf(databaseDir), replacement);
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
     */
    private static String[] findElement(JSONObject json, String key, String originalKey, String path, String replacement) {
        for (String k : json.keySet()) {
            Object value = json.get(k);
            if (value instanceof JSONObject) {
                if (!path.endsWith("\\")) {
                    path += "\\";
                }
                String[] result = findElement((JSONObject) value, key, originalKey, path + k + "\\", replacement);
                if (result != null) {
                    return result;
                }
            } else if (k.equals(key)) {
                String remotePath = value.toString().replace("<passcode>", replacement)
                        .replace("<printcode>", replacement)
                        .replace("<konamiId>", replacement);
                return new String[]{path + originalKey, remotePath};
            }
        }
        return null;
    }
}