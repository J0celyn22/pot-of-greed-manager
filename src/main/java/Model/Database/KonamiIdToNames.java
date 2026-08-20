package Model.Database;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class KonamiIdToNames {

    private static final Logger logger = LoggerFactory.getLogger(KonamiIdToNames.class);

    private static Map<Integer, String> konamiIdToEnNames;
    private static Map<String, Integer> enNamesToKonamiId;
    private static Map<Integer, String> konamiIdToFrNames;
    private static Map<Integer, String> konamiIdToJaNames;
    private static Map<Integer, String> konamiIdToEsNames;
    private static Map<Integer, String> konamiIdToDeNames;
    private static Map<Integer, String> konamiIdToItNames;
    private static Map<Integer, String> konamiIdToPtNames;
    private static Map<Integer, String> konamiIdToKrNames;

    /**
     * Retrieves the map of Konami IDs to their corresponding English names.
     *
     * <p>This method returns a map where the keys are Konami IDs and the values
     * are their corresponding English names. If the map has not been initialized,
     * it will be created before returning.
     *
     * @return a map of Konami IDs to English names
     */
    public static Map<Integer, String> getKonamiIdToEnNames() {
        if (konamiIdToEnNames == null) {
            CreateKonamiIdToNamesMaps();
        }
        return konamiIdToEnNames;
    }

    /**
     * Sets the map of Konami IDs to their corresponding English names.
     *
     * <p>This method assigns the provided map to the internal konamiIdToEnNames map,
     * which is used to map Konami IDs to their corresponding English names.
     *
     * @param printCodeKonamiIdPairs the map of Konami IDs to English names
     */
    public void setKonamiIdToEnNames(Map<Integer, String> printCodeKonamiIdPairs) {
        konamiIdToEnNames = printCodeKonamiIdPairs;
    }

    /**
     * Retrieves the map of English names to their corresponding Konami IDs.
     *
     * <p>This method returns a map where the keys are English names and the values
     * are their corresponding Konami IDs. If the map has not been initialized,
     * it will be created before returning.
     *
     * @return a map of English names to Konami IDs
     */
    public static Map<String, Integer> getEnNamesToKonamiId() {
        if (enNamesToKonamiId == null) {
            CreateKonamiIdToNamesMaps();
        }
        return enNamesToKonamiId;
    }

    /**
     * Retrieves the map of Konami IDs to their corresponding French names.
     *
     * <p>This method returns a map where the keys are Konami IDs and the values
     * are their corresponding French names. If the map has not been initialized,
     * it will be created before returning.
     *
     * @return a map of Konami IDs to French names
     */
    public static Map<Integer, String> getKonamiIdToFrNames() {
        if (konamiIdToFrNames == null) {
            CreateKonamiIdToNamesMaps();
        }
        return konamiIdToFrNames;
    }

    /**
     * Sets the map of Konami IDs to their corresponding French names.
     *
     * <p>This method assigns the provided map to the internal konamiIdToFrNames map,
     * which is used to map Konami IDs to their corresponding French names.
     *
     * @param konamiIdToFrNames the map of Konami IDs to French names
     */
    public static void setKonamiIdToFrNames(Map<Integer, String> konamiIdToFrNames) {
        KonamiIdToNames.konamiIdToFrNames = konamiIdToFrNames;
    }

    /**
     * Retrieves the map of Konami IDs to their corresponding Japanese names.
     *
     * <p>This method returns a map where the keys are Konami IDs and the values
     * are their corresponding Japanese names. If the map has not been initialized,
     * it will be created before returning.
     *
     * @return a map of Konami IDs to Japanese names
     */
    public static Map<Integer, String> getKonamiIdToJaNames() {
        if (konamiIdToJaNames == null) {
            CreateKonamiIdToNamesMaps();
        }
        return konamiIdToJaNames;
    }

    /**
     * Sets the map of Konami IDs to their corresponding Japanese names.
     *
     * <p>This method assigns the provided map to the internal konamiIdToJaNames map,
     * which is used to map Konami IDs to their corresponding Japanese names.
     *
     * @param konamiIdToJaNames the map of Konami IDs to Japanese names
     */
    public static void setKonamiIdToJaNames(Map<Integer, String> konamiIdToJaNames) {
        KonamiIdToNames.konamiIdToJaNames = konamiIdToJaNames;
    }

    /**
     * Retrieves the map of Konami IDs to their corresponding Spanish names.
     *
     * <p>Lazily initialized the same way as {@link #getKonamiIdToFrNames()}.
     *
     * @return a map of Konami IDs to Spanish names
     */
    public static Map<Integer, String> getKonamiIdToEsNames() {
        if (konamiIdToEsNames == null) {
            CreateKonamiIdToNamesMaps();
        }
        return konamiIdToEsNames;
    }

    /**
     * Retrieves the map of Konami IDs to their corresponding German names.
     *
     * <p>Lazily initialized the same way as {@link #getKonamiIdToFrNames()}.
     *
     * @return a map of Konami IDs to German names
     */
    public static Map<Integer, String> getKonamiIdToDeNames() {
        if (konamiIdToDeNames == null) {
            CreateKonamiIdToNamesMaps();
        }
        return konamiIdToDeNames;
    }

    /**
     * Retrieves the map of Konami IDs to their corresponding Italian names.
     *
     * <p>Lazily initialized the same way as {@link #getKonamiIdToFrNames()}.
     *
     * @return a map of Konami IDs to Italian names
     */
    public static Map<Integer, String> getKonamiIdToItNames() {
        if (konamiIdToItNames == null) {
            CreateKonamiIdToNamesMaps();
        }
        return konamiIdToItNames;
    }

    /**
     * Retrieves the map of Konami IDs to their corresponding Portuguese names.
     *
     * <p>Lazily initialized the same way as {@link #getKonamiIdToFrNames()}.
     *
     * @return a map of Konami IDs to Portuguese names
     */
    public static Map<Integer, String> getKonamiIdToPtNames() {
        if (konamiIdToPtNames == null) {
            CreateKonamiIdToNamesMaps();
        }
        return konamiIdToPtNames;
    }

    /**
     * Retrieves the map of Konami IDs to their corresponding Korean names.
     *
     * <p>Sourced from ygoresources' {@code ko} name index (Korean's ISO 639-1
     * code), lazily initialized the same way as {@link #getKonamiIdToFrNames()}.
     *
     * @return a map of Konami IDs to Korean names
     */
    public static Map<Integer, String> getKonamiIdToKrNames() {
        if (konamiIdToKrNames == null) {
            CreateKonamiIdToNamesMaps();
        }
        return konamiIdToKrNames;
    }

    /**
     * Retrieves the English name associated with the given Konami ID.
     *
     * <p>This method returns the English name corresponding to the specified
     * Konami ID. If the mapping has not been initialized, it will be created
     * before attempting to retrieve the name. If the Konami ID does not have
     * an associated English name, a message is printed to the console and an
     * empty string is returned.
     *
     * @param value the Konami ID for which to retrieve the English name
     * @return the English name associated with the specified Konami ID, or an
     * empty string if no name is found
     */
    public static String getKonamiIdToEnName(int value) {
        String returnValue = "";
        if (konamiIdToEnNames == null) {
            CreateKonamiIdToNamesMaps();
        }
        if (value != 0) {
            returnValue = konamiIdToEnNames.get(value);
        }
        if (returnValue == null) {
            logger.warn("EN name not found: {}", value);
        }
        return returnValue;
    }

    /**
     * Retrieves the French name associated with the given Konami ID.
     *
     * <p>This method returns the French name corresponding to the specified
     * Konami ID. If the mapping has not been initialized, it will be created
     * before attempting to retrieve the name. If the French name is not found
     * in the French mapping, the method will try to find it in the English
     * mapping with other Konami IDs associated with the same card.
     *
     * @param value the Konami ID for which to retrieve the French name
     * @return the French name associated with the specified Konami ID, or an
     *         empty string if no name is found
     * @throws Exception if there is an issue during the map creation process
     */
    public static String getKonamiIdToFrName(int value) throws Exception {
        String returnValue = "";
        if (konamiIdToFrNames == null) {
            CreateKonamiIdToNamesMaps();
        }
        if (konamiIdToFrNames.get(value) != null) {
            returnValue = konamiIdToFrNames.get(value);
        }
        //fr.json doesn't contain all ids from en.json, so we have to try getting the French name with the other ids of this card
        else {
            List<Integer> konamiIds = CardDatabaseManager.getKonamiIdToOtherKonamiId(value);
            int i = 0;
            if (konamiIds != null) {
                while (i < konamiIds.size() && returnValue.isEmpty()) {
                    if (konamiIds.get(i) != value) {
                        returnValue = konamiIdToFrNames.get(konamiIds.get(i));
                    }
                    i++;
                }
            } else {
                logger.warn("FR name not found: {}", value);
            }
        }
        return returnValue;
        //return konamiIdToFrNames.get(value);
    }

    /**
     * Retrieves the Japanese name associated with the given Konami ID.
     *
     * <p>This method returns the Japanese name corresponding to the specified
     * Konami ID. If the mapping has not been initialized, it will be created
     * before attempting to retrieve the name. If the Japanese name is not found
     * in the Japanese mapping, the method will try to find it in the English
     * mapping with other Konami IDs associated with the same card.
     *
     * @param value the Konami ID for which to retrieve the Japanese name
     * @return the Japanese name associated with the specified Konami ID, or an
     *         empty string if no name is found
     * @throws Exception if there is an issue during the map creation process
     */
    public static String getKonamiIdToJaName(int value) throws Exception {
        String returnValue = "";
        if (konamiIdToJaNames == null) {
            CreateKonamiIdToNamesMaps();
        }
        if (konamiIdToJaNames.get(value) != null) {
            returnValue = konamiIdToJaNames.get(value);
        }
        //ja.json doesn't contain all ids from en.json, so we have to try getting the Japanese name with the other ids of this card
        else {
            List<Integer> konamiIds = CardDatabaseManager.getKonamiIdToOtherKonamiId(value);
            int i = 0;
            if (konamiIds != null) {
                while (i < konamiIds.size() && returnValue.isEmpty()) {
                    if (konamiIds.get(i) != value) {
                        returnValue = konamiIdToJaNames.get(konamiIds.get(i));
                    }
                    i++;
                }
            } else {
                logger.warn("JA name not found: {}", value);
            }
        }
        return returnValue;
    }

    /**
     * Initializes the maps of Konami IDs to their corresponding names in
     * different languages.
     *
     * <p>This method initializes the maps of Konami IDs to their corresponding
     * names in English, French, Japanese, Spanish, German, Italian, Portuguese,
     * and Korean (ygoresources' {@code ko} index). It also initializes the
     * reverse map of names to their corresponding Konami IDs in English.
     *
     * <p>Chinese ({@code name_CN} on {@link Model.CardsLists.Card}) isn't
     * covered here — ygoresources' name-index API doesn't expose a Chinese
     * index (only {@code en|ja|de|fr|it|es|pt|ko}), so that field currently
     * has no automated data source and stays unpopulated.
     *
     * @throws Exception if there is an issue during the map creation process
     */
    public static void CreateKonamiIdToNamesMaps() {
        String[] keys = {
                "en.json", "fr.json", "ja.json", "es.json", "de.json", "it.json", "pt.json", "ko.json"
        };
        Map<Integer, String>[] dictionaries = new HashMap[keys.length];
        Map<String, Integer>[] reverseDictionaries = new HashMap[keys.length];

        for (int i = 0; i < keys.length; i++) {
            dictionaries[i] = new HashMap<>();
            reverseDictionaries[i] = new HashMap<>();
            JSONObject jsonObject = Database.getJsonContentMap().get(keys[i]);

            if (jsonObject != null) {
                Iterator<String> jsonKeys = jsonObject.keys();

                while (jsonKeys.hasNext()) {
                    String key = jsonKeys.next();
                    JSONArray jsonArray = jsonObject.getJSONArray(key);

                    for (int j = 0; j < jsonArray.length(); j++) {
                        int value = jsonArray.getInt(j);
                        dictionaries[i].put(value, key);
                        if (i == 0) {
                            reverseDictionaries[i].put(key, value);
                        }
                    }
                }
            }
        }

        konamiIdToEnNames = dictionaries[0];
        konamiIdToFrNames = dictionaries[1];
        konamiIdToJaNames = dictionaries[2];
        konamiIdToEsNames = dictionaries[3];
        konamiIdToDeNames = dictionaries[4];
        konamiIdToItNames = dictionaries[5];
        konamiIdToPtNames = dictionaries[6];
        konamiIdToKrNames = dictionaries[7];
        enNamesToKonamiId = reverseDictionaries[0];
    }

    /**
     * Sets the map of English names to their corresponding Konami IDs.
     *
     * <p>This method assigns the provided map to the internal enNamesToKonamiId map,
     * which is used to map English names to their corresponding Konami IDs.
     *
     * @param printCodeKonamiIdPairs the map of English names to Konami IDs
     */
    public void setEnNameToKonamiId(Map<String, Integer> printCodeKonamiIdPairs) {
        enNamesToKonamiId = printCodeKonamiIdPairs;
    }

    /**
     * Returns a string representation of the Konami ID to names mappings.
     *
     * <p>This method constructs a string that contains the mappings of Konami IDs
     * to their corresponding names in every language covered by
     * {@link #CreateKonamiIdToNamesMaps()}. Each map is represented as a series
     * of key-value pairs, where each pair is enclosed in parentheses and
     * separated by a newline. Each language's mapping is separated by an
     * additional newline character.
     *
     * @return a string representation of the Konami ID to names mappings
     */
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        Map<Integer, String>[] allLanguageMaps = new Map[]{
                konamiIdToEnNames, konamiIdToFrNames, konamiIdToJaNames,
                konamiIdToEsNames, konamiIdToDeNames, konamiIdToItNames,
                konamiIdToPtNames, konamiIdToKrNames
        };
        for (Map<Integer, String> languageMap : allLanguageMaps) {
            for (Integer key : languageMap.keySet()) {
                result.append("(").append(key).append(", ").append(languageMap.get(key)).append(")\n");
            }
            result.append("\n");
        }
        return result.toString();
    }
}