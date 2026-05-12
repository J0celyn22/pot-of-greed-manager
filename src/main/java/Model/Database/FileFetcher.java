package Model.Database;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Semaphore;

import static Model.Database.Database.getAllCardsList;
import static Model.Database.PrintCodeToKonamiId.getPrintCodeToKonamiId;
import static Model.FilePaths.databaseDir;

public class FileFetcher {

    // -------------------------------------------------------------------------
    // Invalidated-paths set: persisted to disk so it survives restarts.
    // Files in this set are known to be stale; they are re-fetched on next use
    // (or eagerly via refetchInvalidatedFiles). The old local copy is kept
    // intact until the new version has been successfully downloaded, so the
    // application continues to work when offline.
    // -------------------------------------------------------------------------
    private static final Set<String> invalidatedPaths = new HashSet<>();

    static {
        loadInvalidatedPaths();
    }

    /**
     * Marks a local path as stale and persists the updated set to disk.
     */
    public static void addInvalidatedPath(String path) {
        invalidatedPaths.add(path);
        persistInvalidatedPaths();
    }

    /**
     * Removes a local path from the stale set (called after a successful
     * re-fetch) and persists the updated set to disk.
     */
    public static void removeInvalidatedPath(String path) {
        invalidatedPaths.remove(path);
        persistInvalidatedPaths();
    }

    /** Read-only view of the current stale-path set (used by DataBaseUpdate). */
    public static Set<String> getInvalidatedPaths() {
        return Collections.unmodifiableSet(invalidatedPaths);
    }

    // -------------------------------------------------------------------------

    private static final int MAX_CALLS_PER_SECOND = 20;
    private static final Semaphore semaphore = new Semaphore(MAX_CALLS_PER_SECOND);

    // ------------------------------------------------------------------
    // Persistence helpers
    // ------------------------------------------------------------------

    private static Path getInvalidatedPathsFile() {
        return databaseDir.resolve(Paths.get("ygoresources", "invalidated_paths.txt"));
    }

    /**
     * Loads the persisted stale-path set from disk into memory.
     * Called once in the static initialiser. A missing file is silently ignored
     * (it simply means there are no previously-known stale files).
     */
    private static void loadInvalidatedPaths() {
        Path file = getInvalidatedPathsFile();
        if (!Files.exists(file)) return;
        try {
            Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(invalidatedPaths::add);
            if (!invalidatedPaths.isEmpty()) {
                System.out.println("Restored " + invalidatedPaths.size()
                        + " stale file(s) from previous session.");
            }
        } catch (IOException e) {
            System.out.println("Could not load invalidated paths: " + e.getMessage());
        }
    }

    /**
     * Writes the current stale-path set to disk so it survives restarts.
     * An empty set clears the file, which is correct — there is nothing left to
     * retry.
     */
    private static void persistInvalidatedPaths() {
        Path file = getInvalidatedPathsFile();
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, invalidatedPaths, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("Could not persist invalidated paths: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Re-fetch helpers
    // ------------------------------------------------------------------

    /**
     * Tries to re-fetch every file that is currently marked stale.
     * This is called eagerly from {@link DataBaseUpdate#updateCache()} right
     * after invalidation so that fresh data is available without waiting for the
     * next lazy access. If the application is offline the fetch will fail
     * silently, the old file remains usable, and the path stays in the persisted
     * set so that the next startup retries automatically.
     */
    public static void refetchInvalidatedFiles() {
        // Snapshot to avoid ConcurrentModificationException (successful fetches
        // call removeInvalidatedPath which modifies the live set).
        List<String> snapshot = new ArrayList<>(invalidatedPaths);
        for (String localPath : snapshot) {
            // Derive the element name (e.g. "en.json", "LOB-EN.json", "12345.jpg")
            // from the stored local path — this is what getAddresses() expects.
            String element = Paths.get(localPath).getFileName().toString();
            fetchFile(element);
        }
    }

    // ------------------------------------------------------------------
    // Core fetch methods
    // ------------------------------------------------------------------

    /**
     * Fetches a file specified by the given element from the remote location.
     * If the local file exists and is not stale, nothing is done.
     * If it is stale or absent, a download is attempted. On failure the old file
     * (if any) is left untouched so the application can keep working offline;
     * the path stays in {@code invalidatedPaths} for the next retry.
     */
    public static void fetchFile(String element) {
        String[] addresses = DataBaseUpdate.getAddresses(element);
        if (addresses.length == 0) {
            System.out.println("Element not found in addresses.json: " + element);
            return;
        }
        String localPath  = addresses[0];
        String remotePath = addresses[1];
        Path localFilePath = Paths.get(localPath);

        if (Files.exists(localFilePath) && !invalidatedPaths.contains(localPath)) {
            // File exists and is not stale — nothing to do.
            return;
        }

        try {
            semaphore.acquire();
            try {
                if (!Files.exists(localFilePath.getParent())) {
                    Files.createDirectories(localFilePath.getParent());
                }
                byte[] fileBytes = fetchRemoteFile(remotePath);
                Files.write(localFilePath, fileBytes);
                System.out.println("File fetched and saved locally: " + localPath);
                // Remove from stale set only after a confirmed successful write.
                removeInvalidatedPath(localPath);
            } finally {
                semaphore.release();
            }
        } catch (java.io.FileNotFoundException e) {
            // 4xx from the server — re-throw so callers (e.g.
            // completeKonamiIdToPassCode) can record this in NotFoundCache.
            // Do NOT keep the old file as valid; it simply doesn't exist.
            throw new RuntimeException(e);
        } catch (Exception e) {
            // Network or I/O error — keep the old file as-is and leave the path
            // in invalidatedPaths so the next call retries.
            System.out.println("Error fetching file (" + element + "): " + e.getMessage());
        }
    }

    /**
     * Fetches a file from the remote location, using a caller-supplied remote
     * URL instead of the one stored in addresses.json.
     *
     * @param element    the element name (used to resolve the local path)
     * @param remotePath the remote URL to download from
     */
    public static void fetchFile(String element, String remotePath) {
        String[] addresses = DataBaseUpdate.getAddresses(element);
        if (addresses.length == 0) {
            System.out.println("Element not found in addresses.json");
            return;
        }

        String localPath   = addresses[0];
        Path localFilePath = Paths.get(localPath);

        if (Files.exists(localFilePath) && !invalidatedPaths.contains(localPath)) {
            return;
        }

        try {
            semaphore.acquire();
            try {
                if (!Files.exists(localFilePath.getParent())) {
                    Files.createDirectories(localFilePath.getParent());
                }
                byte[] fileBytes = fetchRemoteFile(remotePath);
                Files.write(localFilePath, fileBytes);
                System.out.println("File fetched and saved locally: " + localPath);
                removeInvalidatedPath(localPath);
            } finally {
                // FIX: release moved to finally so it is always called, even on
                // exception. Previously the release() was inside the try block,
                // causing a semaphore leak on any I/O failure.
                semaphore.release();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Fetches a file from the remote location using its URL.
     *
     * @param remotePath the remote URL of the file
     * @return the file's contents as a byte array
     * @throws java.io.FileNotFoundException if the server returns a 4xx status
     *         code (card/resource genuinely absent from the database) — callers
     *         should catch this specifically and record the ID in
     *         {@link NotFoundCache} so the request is not retried too soon
     * @throws IOException for any other network or I/O error
     */
    private static byte[] fetchRemoteFile(String remotePath) throws IOException {
        URL url = new URL(remotePath);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode >= 400 && responseCode < 500) {
            // 4xx — the resource does not exist on the server.
            // Throw FileNotFoundException (a subclass of IOException) so the
            // caller can catch it separately from transient network failures.
            throw new java.io.FileNotFoundException(
                    "Server returned HTTP " + responseCode + " for URL: " + remotePath);
        }

        try (InputStream inputStream = connection.getInputStream();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return outputStream.toByteArray();
        }
    }

    /**
     * Recursively fetches files from a JSON object.
     */
    private static void fetchFilesFromJson(JSONObject json) {
        for (String key : json.keySet()) {
            Object value = json.get(key);
            if (value instanceof JSONObject) {
                fetchFilesFromJson((JSONObject) value);
            } else if (!key.equals("<passcode>.jpg")
                    && !key.equals("<printcode>.json")
                    && !key.equals("<konamiId>.json")) {
                fetchFile(key);
            }
        }
    }

    /**
     * Returns a list of all passcodes in the database (as strings).
     */
    private static List<String> getPasscodesList() {
        List<String> returnValue = new ArrayList<>();
        for (int key : getAllCardsList().keySet()) {
            returnValue.add(String.valueOf(key));
        }
        return returnValue;
    }

    /**
     * Returns a list of all printcodes in the database (as strings).
     */
    private static List<String> getPrintcodesList() throws URISyntaxException {
        List<String> returnValue = new ArrayList<>();
        for (String key : getPrintCodeToKonamiId().keySet()) {
            returnValue.add(key);
        }
        return returnValue;
    }

    /**
     * Fetches all files from remote locations specified in the addresses.json file.
     */
    public static void fetchAllFiles() {
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
            fetchFilesFromJson(json);

            // Process <passcode>.jpg files
            List<String> passcodes = getPasscodesList();
            for (String passcode : passcodes) {
                fetchFile(passcode + ".jpg");
            }

            // Process <printcode>.json files
            List<String> printcodes = getPrintcodesList();
            for (String printcode : printcodes) {
                fetchFile(printcode + ".json");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}