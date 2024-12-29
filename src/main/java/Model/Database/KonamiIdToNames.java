package Model.Database;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class KonamiIdToNames {
    private static Map<Integer, String> konamiIdToEnNames;
    private static Map<String, Integer> enNamesToKonamiId;
    private static Map<Integer, String> konamiIdToFrNames;
    private static Map<Integer, String> konamiIdToJaNames;

    public static Map<Integer, String> getKonamiIdToEnNames() throws Exception {
        if (konamiIdToEnNames == null) {
            KonamiIdToNames();
        }
        return konamiIdToEnNames;
    }

    public void setKonamiIdToEnNames(Map<Integer, String> printCodeKonamiIdPairs) {
        this.konamiIdToEnNames = printCodeKonamiIdPairs;
    }

    public static Map<String, Integer> getEnNamesToKonamiId() throws Exception {
        if (enNamesToKonamiId == null) {
            KonamiIdToNames();
        }
        return enNamesToKonamiId;
    }

    public void setEnNameToKonamiId(Map<String, Integer> printCodeKonamiIdPairs) {
        this.enNamesToKonamiId = printCodeKonamiIdPairs;
    }

    public static Map<Integer, String> getKonamiIdToFrNames() throws Exception {
        if (konamiIdToFrNames == null) {
            KonamiIdToNames();
        }
        return konamiIdToFrNames;
    }

    public static void setKonamiIdToFrNames(Map<Integer, String> konamiIdToFrNames) {
        KonamiIdToNames.konamiIdToFrNames = konamiIdToFrNames;
    }

    public static Map<Integer, String> getKonamiIdToJaNames() throws Exception {
        if (konamiIdToJaNames == null) {
            KonamiIdToNames();
        }
        return konamiIdToJaNames;
    }

    public static void setKonamiIdToJaNames(Map<Integer, String> konamiIdToJaNames) {
        KonamiIdToNames.konamiIdToJaNames = konamiIdToJaNames;
    }

    public static String getKonamiIdToEnName(int value) throws Exception {
        String returnValue = "";
        if (konamiIdToEnNames == null) {
            KonamiIdToNames();
        }
        if (value != 0) {
            returnValue = konamiIdToEnNames.get(value);
        }
        if(returnValue == null) {
            System.out.println("EN Name not found : " + value);
        }
        return returnValue;
    }

    public static String getKonamiIdToFrName(int value) throws Exception {
        String returnValue = "";
        if (konamiIdToFrNames == null) {
            KonamiIdToNames();
        }
        if (konamiIdToFrNames.get(value) != null) {
            returnValue = konamiIdToFrNames.get(value);
        }
        //fr.json doesn't contain all ids from en.json, so we have to try getting the French name with the other ids of this card
        else {
            List<Integer> konamiIds = CardDatabaseManager.getKonamiIdToOtherKonamiId(Integer.valueOf(value));
            int i = 0;
            if (konamiIds != null) {
                while (i < konamiIds.size() && returnValue == "") {
                    if (konamiIds.get(i) != value) {
                        returnValue = konamiIdToFrNames.get(konamiIds.get(i));
                    }
                    i++;
                }
            }
            else {
                System.out.println("FR Name not found : " + value);
            }
        }
        return returnValue;
        //return konamiIdToFrNames.get(value);
    }

    public static String getKonamiIdToJaName(int value) throws Exception {
        String returnValue = "";
        if (konamiIdToJaNames == null) {
            KonamiIdToNames();
        }
        if (konamiIdToJaNames.get(value) != null) {
            returnValue = konamiIdToJaNames.get(value);
        }
        //ja.json doesn't contain all ids from en.json, so we have to try getting the Japanese name with the other ids of this card
        else {
            List<Integer> konamiIds = CardDatabaseManager.getKonamiIdToOtherKonamiId(Integer.valueOf(value));
            int i = 0;
            if (konamiIds != null) {
                while (i < konamiIds.size() && returnValue == "") {
                    if (konamiIds.get(i) != value) {
                        returnValue = konamiIdToJaNames.get(konamiIds.get(i));
                    }
                    i++;
                }
            }
            else {
                System.out.println("JA Name not found : " + value);
            }
        }
        return returnValue;
    }

    public static void KonamiIdToNames() throws Exception {
        String[] keys = {"en.json", "fr.json", "ja.json"};
        Map<Integer, String>[] dictionaries = new HashMap[keys.length];
        Map<String, Integer>[] reverseDictionaries = new HashMap[keys.length];

        for (int i = 0; i < keys.length; i++) {
            dictionaries[i] = new HashMap<>();
            reverseDictionaries[i] = new HashMap<>();
            JSONObject jsonObject = (JSONObject) Database.jsonContentMap.get(keys[i]);

            if (jsonObject != null) {
                Iterator<String> jsonKeys = jsonObject.keys();

                while (jsonKeys.hasNext()) {
                    String key = jsonKeys.next();
                    JSONArray jsonArray = jsonObject.getJSONArray(key);

                    for (int j = 0; j < jsonArray.length(); j++) {
                        int value = jsonArray.getInt(j);
                        dictionaries[i].put(value, key);
                        if(i == 0) {
                            reverseDictionaries[i].put(key, value);
                        }
                    }
                }
            }
        }

        konamiIdToEnNames = dictionaries[0];
        konamiIdToFrNames = dictionaries[1];
        konamiIdToJaNames = dictionaries[2];
        enNamesToKonamiId = reverseDictionaries[0];
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for(Integer key : this.konamiIdToEnNames.keySet()) {
            result.append("(").append(key).append(", ").append(this.konamiIdToEnNames.get(key)).append(")\n");
        }
        result.append("\n");
        for(Integer key : this.konamiIdToFrNames.keySet()) {
            result.append("(").append(key).append(", ").append(this.konamiIdToFrNames.get(key)).append(")\n");
        }
        result.append("\n");
        for(Integer key : this.konamiIdToJaNames.keySet()) {
            result.append("(").append(key).append(", ").append(this.konamiIdToJaNames.get(key)).append(")\n");
        }
        return result.toString();
    }
}
