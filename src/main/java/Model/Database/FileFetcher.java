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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

import static Model.Database.Database.getAllCardsList;
import static Model.Database.PrintCodeToKonamiId.getPrintCodeToKonamiId;

public class FileFetcher {

    public static final Set<String> invalidatedPaths = new HashSet<>();
    private static final int MAX_CALLS_PER_SECOND = 20;
    private static final Semaphore semaphore = new Semaphore(MAX_CALLS_PER_SECOND);

    /**
     * Fetches a file specified by the given element from the remote location.
     * It first checks if the file exists locally and is valid.
     * If not, it fetches the file and saves it locally.
     */
    public static void fetchFile(String element) {
        String[] addresses = DataBaseUpdate.getAddresses(element);
        if (addresses.length == 0) {
            System.out.println("Element not found in addresses.json: " + element);
            return;
        }
        String localPath = addresses[0];
        String remotePath = addresses[1];
        Path localFilePath = Paths.get(localPath);
        if (Files.exists(localFilePath) && !invalidatedPaths.contains(localPath)) {
            // File exists and is valid; do nothing.
        } else {
            try {
                semaphore.acquire();
                if (!Files.exists(localFilePath.getParent())) {
                    Files.createDirectories(localFilePath.getParent());
                }
                byte[] fileBytes = fetchRemoteFile(remotePath);
                Files.write(localFilePath, fileBytes);
                System.out.println("File fetched and saved locally: " + localPath);
                invalidatedPaths.remove(localPath);
            } catch (Exception e) {
                System.out.println("Error fetching file (" + element + "): " + e.getMessage());
            } finally {
                semaphore.release();
            }
        }
    }

    /**
     * Fetches a file from the remote location and saves it locally.
     *
     * @param element    the element to search for in the addresses.json file
     * @param remotePath the remote path of the file to fetch
     */
    public static void fetchFile(String element, String remotePath) {
        String[] addresses = DataBaseUpdate.getAddresses(element);
        if (addresses.length == 0) {
            System.out.println("Element not found in addresses.json");
            return;
        }

        String localPath = addresses[0];
        Path localFilePath = Paths.get(localPath);

        if (Files.exists(localFilePath) && !invalidatedPaths.contains(localPath)) {
            //System.out.println("File already exists and is valid locally: " + localPath);
        } else {
            try {
                semaphore.acquire();

                // Create the directory if it does not exist
                if (!Files.exists(localFilePath.getParent())) {
                    Files.createDirectories(localFilePath.getParent());
                }

                byte[] fileBytes = fetchRemoteFile(remotePath);
                Files.write(localFilePath, fileBytes);
                System.out.println("File fetched and saved locally: " + localPath);
                invalidatedPaths.remove(localPath);
                semaphore.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Fetches a file from the remote location using its URL.
     *
     * @param remotePath the remote URL of the file
     * @return the file's contents as a byte array
     * @throws IOException if an error occurs during the fetch
     */
    private static byte[] fetchRemoteFile(String remotePath) throws IOException {
        URL url = new URL(remotePath);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
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

    // The overloaded fetchFile(String element, String remotePath) version would be similarly updated.

    /**
     * Recursively fetches files from a JSON object.
     */
    private static void fetchFilesFromJson(JSONObject json) {
        for (String key : json.keySet()) {
            Object value = json.get(key);
            if (value instanceof JSONObject) {
                fetchFilesFromJson((JSONObject) value);
            } else if (!key.equals("<passcode>.jpg") && !key.equals("<printcode>.json") && !key.equals("<konamiId>.json")) {
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
                encoded = Files.readAllBytes(Paths.get(Paths.get("./src/main/java/Model/Database/addresses.json").toUri()));
            } catch (Exception e) {
                encoded = Files.readAllBytes(Paths.get(Paths.get("resources/addresses.json").toUri()));
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