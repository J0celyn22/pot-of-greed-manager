package Model.Database;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PrintCodeToKonamiId {
    private static HashMap<String, String> printCodeToKonamiId;
    private static HashMap<String, List<String>> konamiIdToPrintCodes;

    public static HashMap<String, String> getPrintCodeToKonamiId() throws Exception {
        if (printCodeToKonamiId == null) {
            createKonamiIdPrintCodeMaps();
        }
        return printCodeToKonamiId;
    }

    public void setPrintCodeToKonamiId(HashMap<String, String> printCodeToKonamiId) {
        this.printCodeToKonamiId = printCodeToKonamiId;
    }

    public static HashMap<String, List<String>> getKonamiIdToPrintCodes() throws Exception {
        if (konamiIdToPrintCodes == null) {
            createKonamiIdPrintCodeMaps();
        }
        return konamiIdToPrintCodes;
    }

    public void setKonamiIdToPrintCodes(HashMap<String, List<String>> konamiIdToPrintCodes) {
        this.konamiIdToPrintCodes = konamiIdToPrintCodes;
    }

    public static void createKonamiIdPrintCodeMaps() throws Exception {
        List<String> setCodes = Database.openSets("_sets.txt");
        printCodeToKonamiId = new HashMap<>();
        konamiIdToPrintCodes = new HashMap<>();

        // Process files listed in _sets.txt
        for (String setCode : setCodes) {
            JSONObject jsonObject = Database.openJson(setCode + ".json");
            for (String key : jsonObject.keySet()) {
                String konamiId = Integer.toString(jsonObject.getInt(key));
                printCodeToKonamiId.put(setCode + key, konamiId);
                //If the Konami ID is already in the map, add the setCode + key to the list of print codes for that Konami ID
                if (konamiIdToPrintCodes.containsKey(konamiId)) {
                    konamiIdToPrintCodes.get(konamiId).add(setCode + key);
                }
                else {
                    //If the Konami ID is not in the map, create a new list with the setCode + key and add it to the map
                    List<String> printCodes = new ArrayList<>();
                    printCodes.add(setCode + key);
                    konamiIdToPrintCodes.put(konamiId, printCodes);
                }
            }
        }

        // Process all other JSON files in the directory
        File directory = new File("..\\Database\\ygoresources\\printcode");
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));

        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                String setCode = fileName.substring(0, fileName.length() - 5); // Remove .json extension

                if (!setCodes.contains(setCode)) {
                    JSONObject jsonObject = Database.openJson(fileName);
                    for (String key : jsonObject.keySet()) {
                        String konamiId = Integer.toString(jsonObject.getInt(key));
                        printCodeToKonamiId.put(setCode + key, konamiId);
                        //If the Konami ID is already in the map, add the setCode + key to the list of print codes for that Konami ID
                        if (konamiIdToPrintCodes.containsKey(konamiId)) {
                            konamiIdToPrintCodes.get(konamiId).add(setCode + key);
                        }
                        else {
                            //If the Konami ID is not in the map, create a new list with the setCode + key and add it to the map
                            List<String> printCodes = new ArrayList<>();
                            printCodes.add(setCode + key);
                            konamiIdToPrintCodes.put(konamiId, printCodes);
                        }
                    }
                }
            }
        }
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        for(String key : this.printCodeToKonamiId.keySet()) {
            result.append("(").append(key).append(", ").append(this.printCodeToKonamiId.get(key)).append(")\n");
        }
        return result.toString();
    }
}
