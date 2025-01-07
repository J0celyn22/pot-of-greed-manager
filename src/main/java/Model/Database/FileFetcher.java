package Model.Database;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

public class FileFetcher {

    public static final Set<String> invalidatedPaths = new HashSet<>();
    private static final int MAX_CALLS_PER_SECOND = 20;
    private static final Semaphore semaphore = new Semaphore(MAX_CALLS_PER_SECOND);

    /**
     * Fetches a file specified by the given element from the remote location.
     * It first checks if the file exists locally and is valid. If it does,
     * then it does not fetch the file again. If the file does not exist or
     * is invalid, it fetches the file from the remote location and saves
     * it locally.
     *
     * @param element the element to search for in the addresses.json file
     */
    public static void fetchFile(String element) {
        String[] addresses = DataBaseUpdate.getAddresses(element);
        if (addresses.length == 0) {
            System.out.println("Element not found in addresses.json");
            return;
        }

        String localPath = addresses[0];
        String remotePath = addresses[1];
        Path localFilePath = Paths.get(localPath);

        if (Files.exists(localFilePath) && !invalidatedPaths.contains(localPath)) {
            System.out.println("File already exists and is valid locally: " + localPath);
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
            System.out.println("File already exists and is valid locally: " + localPath);
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
     * Fetches a file from the remote location.
     *
     * @param remotePath the remote path of the file to fetch
     * @return the contents of the file as a byte array
     * @throws IOException if the file cannot be fetched
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

    /**
     * Fetches all files from the remote locations specified in the addresses.json file, except for <passcode>.jpg and
     * <printcode>.json files, which are processed separately. The <passcode>.jpg files are retrieved by calling
     * {@link #fetchFile(String)} with the passcode and ".jpg" appended to the end. The <printcode>.json files are
     * retrieved by calling {@link #fetchFile(String)} with the printcode and ".json" appended to the end.
     *
     * @throws JSONException if an error occurs while parsing the addresses.json file
     */
    public static void fetchAllFiles() {
        try {
            // Read the JSON file
            String content = new String(Files.readAllBytes(Paths.get("./src/main/java/Model/Database/addresses.json")));
            JSONObject json = new JSONObject(content);

            // Fetch all files except <passcode>.jpg and <printcode>.json
            fetchFilesFromJson(json);

            // Process <passcode>.jpg files
            List<String> passcodes = getPasscodesList(); //TODO
            for (String passcode : passcodes) {
                fetchFile(passcode + ".jpg");
            }

            // Process <printcode>.json files
            List<String> printcodes = getPrintcodesList(); //TODO
            for (String printcode : printcodes) {
                fetchFile(printcode + ".json");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Recursively fetches files from a JSON object, excluding <passcode>.jpg and <printcode>.json files.
     *
     * @param json the JSON object containing file paths to be fetched
     */
    private static void fetchFilesFromJson(JSONObject json) {
        for (String key : json.keySet()) {
            Object value = json.get(key);
            if (value instanceof JSONObject) {
                fetchFilesFromJson((JSONObject) value);
            } else if (!key.equals("<passcode>.jpg") && !key.equals("<printcode>.json")) {
                fetchFile(key);
            }
        }
    }

    /**
     * Returns a list of all passcodes in the database.
     *
     * @return a list of passcodes as strings
     * @todo implement this method
     */
    private static List<String> getPasscodesList() {
        //TODO implement a list of all passcodes in the database
        return List.of("46986414"); // Placeholder
    }

    private static List<String> getPrintcodesList() {
        //TODO implement a list of all printcodes in the database
        return List.of("15AX-JP"); // Placeholder
    }
}

