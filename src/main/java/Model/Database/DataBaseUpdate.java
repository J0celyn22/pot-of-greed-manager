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
     * Compares the local revision with the remote one and fetches every
     * manifest in between. Each manifest lists the files that changed; those
     * files are marked stale via {@link FileFetcher#addInvalidatedPath(String)}
     * so that they survive a restart (the old copy stays on disk and keeps the
     * application working offline).
     *
     * After invalidation, {@link FileFetcher#refetchInvalidatedFiles()} is
     * called eagerly so that, when online, fresh data is available immediately
     * without waiting for the next lazy access.
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

            // Eagerly attempt to re-fetch every stale file.
            // If the app is offline the old files remain usable and the stale
            // set is persisted so the next startup retries automatically.
            FileFetcher.refetchInvalidatedFiles();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads the current revision from the local file at
     * {@code <databaseDir>/ygoresources/revision.txt}.
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
     * Reads the current revision from
     * {@code https://db.ygoresources.com/manifest/0} via the
     * {@code X-Cache-Revision} response header.
     */
    public static int getRemoteRevision() throws IOException {
        URL url = new URL("https://db.ygoresources.com/manifest/0");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        return Integer.parseInt(connection.getHeaderField("X-Cache-Revision"));
    }

    /**
     * Fetches the manifest JSON for the given revision from the remote server.
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
     * Marks local paths as stale based on the provided manifest JSON.
     *
     * <p><b>Bug fixed:</b> The ygoresources manifest uses bare identifiers
     * without file-extensions (e.g. {@code "en"}, {@code "fr"}, {@code "ja"},
     * {@code "_sets"}).  Previously these were passed as-is to
     * {@link #getAddresses(String)}, which looks keys up in {@code addresses.json}
     * under names like {@code "en.json"} — so the lookup always failed and
     * those files were never invalidated.
     *
     * <p>The fix is {@link #normalizeManifestKey(String)}, which maps bare
     * manifest keys to the corresponding filenames used in
     * {@code addresses.json} before the lookup is performed.
     *
     * <p>Invalidation is now done via
     * {@link FileFetcher#addInvalidatedPath(String)}, which persists the stale
     * set to disk so that files are still retried after a restart even when the
     * download failed because the app was offline.
     *
     * @param manifestJson the manifest JSON object returned by the remote server
     */
    private static void invalidatePaths(JSONObject manifestJson) {
        for (String key : manifestJson.keySet()) {
            Object value = manifestJson.get(key);
            if (!(value instanceof JSONObject)) continue;
            JSONObject subJson = (JSONObject) value;

            for (String subKey : subJson.keySet()) {
                Object subValue = subJson.get(subKey);
                if (!(subValue instanceof JSONObject)) continue;
                JSONObject subSubJson = (JSONObject) subValue;

                for (String subSubKey : subSubJson.keySet()) {
                    markStale(subSubKey);
                }
            }
        }

        // Always invalidate the YGOProDeck aggregate files so that newly-added
        // or renamed cards are picked up even when they are not listed
        // individually in the ygoresources manifest.
        markStale("cardinfo.json");
        markStale("archetypes.json");
        markStale("_sets.txt");
    }

    /**
     * Normalises a manifest key to a filename that {@link #getAddresses(String)}
     * can look up in {@code addresses.json}.
     *
     * <p>The ygoresources manifest stores identifiers without extensions:
     * <pre>
     *   "en"     → "en.json"
     *   "fr"     → "fr.json"
     *   "ja"     → "ja.json"
     *   "_sets"  → "_sets.txt"
     *   "LOB-EN" → "LOB-EN.json"   (print-code files)
     *   "12345"  → "12345.json"    (Konami-ID files)
     * </pre>
     * Files that already carry an extension (e.g. {@code "cardinfo.json"}) are
     * returned unchanged.
     *
     * @param key the raw key from the manifest JSON
     * @return the corresponding filename with the appropriate extension
     */
    private static String normalizeManifestKey(String key) {
        if (key.contains(".")) {
            // Already has an extension — leave it alone.
            return key;
        }
        if (key.equals("_sets")) {
            return "_sets.txt";
        }
        // Everything else (language codes, print codes, Konami IDs) maps to JSON.
        return key + ".json";
    }

    /**
     * Resolves {@code rawKey} to a local path via {@link #getAddresses(String)}
     * and adds that path to the persisted stale set.
     *
     * @param rawKey the key exactly as it appears in the manifest (or a fixed
     *               filename such as {@code "cardinfo.json"})
     */
    private static void markStale(String rawKey) {
        String filename = normalizeManifestKey(rawKey);
        String[] addresses = getAddresses(filename);
        if (addresses.length > 0) {
            String localPath = addresses[0];
            FileFetcher.addInvalidatedPath(localPath);
            System.out.println("Invalidated local path: " + localPath);
        } else {
            System.out.println("Could not resolve address for manifest key: " + rawKey
                    + " (normalized: " + filename + ")");
        }
    }

    /**
     * Writes the new revision number to
     * {@code <databaseDir>/ygoresources/revision.txt}.
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
     * Retrieves the local and remote paths for a given filename from
     * {@code addresses.json}.
     *
     * <p>Template keys in the filename are handled automatically:
     * <ul>
     *   <li>{@code "12345.jpg"}   → template {@code "<passcode>.jpg"}</li>
     *   <li>{@code "12345.json"}  → template {@code "<konamiId>.json"}</li>
     *   <li>{@code "LOB-EN.json"} → template {@code "<printcode>.json"}</li>
     * </ul>
     *
     * @param element the filename to look up (e.g. {@code "en.json"},
     *                {@code "LOB-EN.json"}, {@code "12345.jpg"})
     * @return a two-element array {@code [localPath, remotePath]}, or an empty
     *         array when the element is not found
     */
    public static String[] getAddresses(String element) {
        try {
            byte[] encoded;
            try {
                encoded = Files.readAllBytes(Paths.get(
                        Paths.get("./src/main/java/Model/Database/addresses.json").toUri()));
            } catch (Exception e) {
                encoded = Files.readAllBytes(Paths.get(
                        Paths.get("resources/addresses.json").toUri()));
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
     * Recursively searches for a key in a JSON object and returns the
     * corresponding {@code [localPath, remotePath]} pair when found.
     */
    private static String[] findElement(JSONObject json, String key, String originalKey,
                                        String path, String replacement) {
        for (String k : json.keySet()) {
            Object value = json.get(k);
            if (value instanceof JSONObject) {
                if (!path.endsWith("\\")) {
                    path += "\\";
                }
                String[] result = findElement((JSONObject) value, key, originalKey,
                        path + k + "\\", replacement);
                if (result != null) {
                    return result;
                }
            } else if (k.equals(key)) {
                String remotePath = value.toString()
                        .replace("<passcode>", replacement)
                        .replace("<printcode>", replacement)
                        .replace("<konamiId>", replacement);
                return new String[]{path + originalKey, remotePath};
            }
        }
        return null;
    }
}