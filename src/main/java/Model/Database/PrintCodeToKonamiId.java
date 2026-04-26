package Model.Database;

import org.json.JSONObject;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

import static Model.FilePaths.databaseDir;

public class PrintCodeToKonamiId {
    private static HashMap<String, String> printCodeToKonamiId;
    private static HashMap<String, List<String>> konamiIdToPrintCodes;

    /**
     * Retrieves the map of print codes to their corresponding Konami IDs.
     *
     * <p>This method returns a map where the keys are print codes and the values
     * are their corresponding Konami IDs. If the map has not been initialized,
     * it will be created before returning.
     *
     * @return a map of print codes to Konami IDs
     */
    public static HashMap<String, String> getPrintCodeToKonamiId() throws URISyntaxException {
        if (printCodeToKonamiId == null) {
            createKonamiIdPrintCodeMaps();
        }
        return printCodeToKonamiId;
    }

    /**
     * Sets the map of print codes to their corresponding Konami IDs.
     *
     * <p>This method assigns the provided map to the internal printCodeToKonamiId map,
     * which is used to map print codes to their corresponding Konami IDs.
     *
     * @param printCodeToKonamiId the map of print codes to Konami IDs
     */
    public void setPrintCodeToKonamiId(HashMap<String, String> printCodeToKonamiId) {
        PrintCodeToKonamiId.printCodeToKonamiId = printCodeToKonamiId;
    }

    /**
     * Retrieves the map of Konami IDs to their corresponding print codes.
     *
     * <p>This method returns a map where the keys are Konami IDs and the values
     * are lists of print codes associated with the given Konami ID. If the map
     * has not yet been initialized, it will be created before returning.
     *
     * @return a map of Konami IDs to lists of print codes
     */
    public static HashMap<String, List<String>> getKonamiIdToPrintCodes() throws URISyntaxException {
        if (konamiIdToPrintCodes == null) {
            createKonamiIdPrintCodeMaps();
        }
        return konamiIdToPrintCodes;
    }

    /**
     * Sets the map of Konami IDs to their corresponding print codes.
     *
     * <p>This method assigns the provided map to the internal konamiIdToPrintCodes map,
     * which is used to map Konami IDs to their corresponding print codes.
     *
     * @param konamiIdToPrintCodes the map of Konami IDs to lists of print codes
     */
    public void setKonamiIdToPrintCodes(HashMap<String, List<String>> konamiIdToPrintCodes) {
        PrintCodeToKonamiId.konamiIdToPrintCodes = konamiIdToPrintCodes;
    }

    /**
     * Creates the maps of print codes to their corresponding Konami IDs and of
     * Konami IDs to their corresponding print codes.
     *
     * <p>This method initializes the maps of print codes to their corresponding
     * Konami IDs and of Konami IDs to their corresponding print codes. It does
     * this by reading from the files listed in the "_sets.txt" file in the
     * "ygoresources" directory and all other JSON files in the same directory.
     * For each file, it processes all the keys and their corresponding Konami
     * IDs, creating a map of print codes to their corresponding Konami IDs and
     * a map of Konami IDs to lists of print codes associated with the given
     * Konami ID.
     *
     * <p>If the map of print codes to their corresponding Konami IDs or the map
     * of Konami IDs to their corresponding print codes has not yet been
     * initialized, it will be created before returning.
     */
    public static void createKonamiIdPrintCodeMaps() throws URISyntaxException {
        List<String> setCodes = Database.openSets("_sets.txt");
        printCodeToKonamiId = new HashMap<>();
        konamiIdToPrintCodes = new HashMap<>();

        // Process files listed in _sets.txt
        if (setCodes != null) {
            for (String setCode : setCodes) {
                JSONObject jsonObject = Database.openJson(setCode + ".json");
                if (jsonObject != null) {
                    for (String key : jsonObject.keySet()) {
                        String konamiId = Integer.toString(jsonObject.getInt(key));
                        if (!printCodeToKonamiId.containsKey(setCode + key)) {
                            printCodeToKonamiId.put(setCode + key, konamiId);
                        }
                        if (konamiIdToPrintCodes.containsKey(konamiId)) {
                            konamiIdToPrintCodes.get(konamiId).add(setCode + key);
                        } else {
                            List<String> printCodes = new ArrayList<>();
                            printCodes.add(setCode + key);
                            konamiIdToPrintCodes.put(konamiId, printCodes);
                        }
                    }
                }
            }
        }

        // Process all other JSON files in the directory
        File directory = new File(databaseDir.resolve(Paths.get("ygoresources", "printcode")).toUri());
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));

        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                String setCode = fileName.substring(0, fileName.length() - 5); // Remove .json extension

                if (setCodes != null && !setCodes.contains(setCode)) {
                    JSONObject jsonObject = Database.openJson(fileName);
                    if (jsonObject != null) {
                        for (String key : jsonObject.keySet()) {
                            String konamiId = Integer.toString(jsonObject.getInt(key));
                            if (!printCodeToKonamiId.containsKey(setCode + key)) {
                                printCodeToKonamiId.put(setCode + key, konamiId);
                            }
                            if (konamiIdToPrintCodes.containsKey(konamiId)) {
                                konamiIdToPrintCodes.get(konamiId).add(setCode + key);
                            } else {
                                List<String> printCodes = new ArrayList<>();
                                printCodes.add(setCode + key);
                                konamiIdToPrintCodes.put(konamiId, printCodes);
                            }
                        }
                    } else {
                        throw new RuntimeException("Failed to open JSON file: " + fileName);
                    }
                } else if (setCodes == null) {
                    throw new RuntimeException("Failed to open JSON file: " + fileName);
                }
            }
        }
    }

    /**
     * Returns a sorted, deduplicated list of set-name prefixes derived from all
     * known set codes (both from _sets.txt and from the printcode directory).
     *
     * <p>Each raw set code (e.g. {@code "LOB-EN"}, {@code "SDVR-JP"}) is stripped
     * of everything from the first {@code '-'} onwards, yielding the base set name
     * (e.g. {@code "LOB"}, {@code "SDVR"}).  The result is sorted alphabetically
     * and contains no duplicates, making it suitable for autocomplete suggestions.
     *
     * @return sorted list of unique set-name prefixes
     */
    public static List<String> getSetNames() throws URISyntaxException {
        // Ensure the maps are initialised so we can reuse the set-code discovery.
        // (createKonamiIdPrintCodeMaps already reads _sets.txt + directory.)
        if (printCodeToKonamiId == null) {
            createKonamiIdPrintCodeMaps();
        }

        // Re-read _sets.txt codes and directory file names to collect raw set codes,
        // then strip the '-...' suffix from each one.
        TreeSet<String> names = new TreeSet<>();

        List<String> setCodes = Database.openSets("_sets.txt");
        if (setCodes != null) {
            for (String code : setCodes) {
                int dash = code.indexOf('-');
                names.add(dash > 0 ? code.substring(0, dash) : code);
            }
        }

        File directory = new File(databaseDir.resolve(
                java.nio.file.Paths.get("ygoresources", "printcode")).toUri());
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                String code = file.getName();
                code = code.substring(0, code.length() - 5); // strip .json
                int dash = code.indexOf('-');
                names.add(dash > 0 ? code.substring(0, dash) : code);
            }
        }

        return new ArrayList<>(names);
    }

    /**
     * Returns a string representation of the mapping of print codes to Konami IDs.
     *
     * <p>This method builds a string that contains each entry in the
     * printCodeToKonamiId map, formatted as "(printCode, konamiId)" on a new line.
     *
     * @return a string representation of the printCodeToKonamiId map
     */
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (String key : printCodeToKonamiId.keySet()) {
            result.append("(").append(key).append(", ").append(printCodeToKonamiId.get(key)).append(")\n");
        }
        return result.toString();
    }
}