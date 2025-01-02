package Model.Database;

import Model.Database.CardInfo.CardImage;
import Model.Database.CardInfo.CardInfo;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CardDatabaseManager {
    private static Map<Integer, Integer> passCodeToKonamiId;
    private static Map<Integer, Integer> konamiIdToPassCode;
    private static Map<Integer, List<Integer>> passCodeToOtherPassCodes;
    private static Map<Integer, List<Integer>> konamiIdToOtherKonamiIds;
    private static Map<Integer, String> konamiIdToArchetype;
    private static List<Integer> cardPasscodesList;

    /**
     * Retrieves the map of passcodes to their corresponding other passcodes.
     *
     * <p>This method returns the map of passcodes using a passcode that correspond to the same card.
     * The map is created only once and then reused for following calls.
     *
     * @return the map of passcodes to their corresponding other passcodes
     * @throws Exception if there is an issue during the map creation process
     */
    public static Map<Integer, List<Integer>> getPassCodeToOtherPassCodes() throws Exception {
        if (passCodeToOtherPassCodes == null) {
            createDatabaseMaps();
        }
        return passCodeToOtherPassCodes;
    }

    /**
     * Sets the map of passcodes to their corresponding other passcodes.
     *
     * <p>This method sets the map of passcodes using a passcode that correspond to the same card.
     *
     * @param passCodeToOtherPassCodes the map of passcodes
     */
    public static void setPassCodeToOtherPassCodes(Map<Integer, List<Integer>> passCodeToOtherPassCodes) {
        CardDatabaseManager.passCodeToOtherPassCodes = passCodeToOtherPassCodes;
    }

    /**
     * Retrieves the map of passcodes to their corresponding Konami IDs.
     *
     * <p>This method returns the map of passcodes to their corresponding Konami IDs.
     * The map is created only once and then reused for following calls.
     *
     * @return the map of passcodes to their corresponding Konami IDs
     * @throws Exception if there is an issue during the map creation process
     */
    public static Map<Integer, Integer> getPassCodeToKonamiId() throws Exception {
        if (passCodeToKonamiId == null) {
            createDatabaseMaps();
        }
        return passCodeToKonamiId;
    }

    /**
     * Sets the map of passcodes to their corresponding Konami IDs.
     *
     * <p>This method assigns the provided map to the internal passCodeToKonamiId map,
     * which is used to map passcodes to their corresponding Konami IDs.
     *
     * @param passCodeToKonamiId the map of passcodes to Konami IDs
     */
    public static void setPassCodeToKonamiId(Map<Integer, Integer> passCodeToKonamiId) {
        CardDatabaseManager.passCodeToKonamiId = passCodeToKonamiId;
    }

    /**
     * Retrieves the map of Konami IDs to their corresponding passcodes.
     *
     * <p>This method returns the map of Konami IDs to their corresponding passcodes.
     * The map is created only once and then reused for following calls.
     *
     * @return the map of Konami IDs to their corresponding passcodes
     * @throws Exception if there is an issue during the map creation process
     */
    public static Map<Integer, Integer> getKonamiIdToPassCode() throws Exception {
        if (konamiIdToPassCode == null) {
            createDatabaseMaps();
        }
        return konamiIdToPassCode;
    }

    /**
     * Sets the map of Konami IDs to their corresponding passcodes.
     *
     * <p>This method assigns the provided map to the internal konamiIdToPassCode map,
     * which is used to map Konami IDs to their corresponding passcodes.
     *
     * @param konamiIdToPassCode the map of Konami IDs to passcodes
     */
    public static void setKonamiIdToPassCode(Map<Integer, Integer> konamiIdToPassCode) {
        CardDatabaseManager.konamiIdToPassCode = konamiIdToPassCode;
    }

    /**
     * Retrieves the map of Konami IDs to their corresponding other Konami IDs.
     *
     * <p>This method returns the map of Konami IDs to their corresponding other Konami IDs.
     * The map is created only once and then reused for following calls.
     *
     * @return the map of Konami IDs to their corresponding other Konami IDs
     */
    public static Map<Integer, List<Integer>> getKonamiIdToOtherKonamiIds() {
        return konamiIdToOtherKonamiIds;
    }

    /**
     * Sets the map of Konami IDs to their corresponding other Konami IDs.
     *
     * <p>This method assigns the provided map to the internal konamiIdToOtherKonamiIds map,
     * which is used to map Konami IDs to their corresponding other Konami IDs.
     *
     * @param konamiIdToOtherKonamiIds the map of Konami IDs to other Konami IDs
     */
    public static void setKonamiIdToOtherKonamiIds(Map<Integer, List<Integer>> konamiIdToOtherKonamiIds) {
        CardDatabaseManager.konamiIdToOtherKonamiIds = konamiIdToOtherKonamiIds;
    }

    /**
     * Retrieves the map of Konami IDs to their corresponding archetypes.
     *
     * <p>This method returns the map of Konami IDs to their corresponding archetypes.
     * The map is created only once and then reused for following calls.
     *
     * @return the map of Konami IDs to their corresponding archetypes
     */
    public static Map<Integer, String> getKonamiIdToArchetype() {
        return konamiIdToArchetype;
    }

    /**
     * Sets the map of Konami IDs to their corresponding archetypes.
     *
     * <p>This method assigns the provided map to the internal konamiIdToArchetype map,
     * which is used to map Konami IDs to their corresponding archetypes.
     *
     * @param konamiIdToArchetype the map of Konami IDs to archetypes
     */
    public static void setKonamiIdToArchetype(Map<Integer, String> konamiIdToArchetype) {
        CardDatabaseManager.konamiIdToArchetype = konamiIdToArchetype;
    }

    /**
     * Retrieves a list of other Konami IDs associated with the given Konami ID.
     *
     * <p>This method returns a list of Konami IDs that are considered equivalent
     * or related to the specified Konami ID in the database. If the mapping has
     * not yet been initialized, it will be created.
     *
     * @param konamiId the Konami ID for which to retrieve associated IDs
     * @return a list of other Konami IDs related to the specified Konami ID
     * @throws Exception if there is an issue during the map creation process
     */
    public static List<Integer> getKonamiIdToOtherKonamiId(Integer konamiId) throws Exception {
        if (konamiIdToOtherKonamiIds == null) {
            createDatabaseMaps();
        }
        return konamiIdToOtherKonamiIds.get(konamiId);
    }

    /**
     * Retrieves the archetype associated with the given Konami ID.
     *
     * <p>This method returns the archetype associated with the given Konami ID in the database.
     * If the mapping has not yet been initialized, it will be created.
     *
     * @param konamiId the Konami ID for which to retrieve the archetype
     * @return the archetype associated with the specified Konami ID
     * @throws Exception if there is an issue during the map creation process
     */
    public static String getKonamiIdToArchetype(Integer konamiId) throws Exception {
        if (konamiIdToArchetype == null) {
            createDatabaseMaps();
        }
        return konamiIdToArchetype.get(konamiId);
    }

    /**
     * Retrieves the list of card passcodes.
     *
     * <p>This method returns a list of integer passcodes for cards.
     * If the list has not been initialized, it will trigger the creation
     * of the necessary database maps.
     *
     * @return a list of card passcodes as integers
     * @throws Exception if there is an issue during the map creation process
     */
    public static List<Integer> getCardPasscodesList() throws Exception {
        if (cardPasscodesList == null) {
            createDatabaseMaps();
        }
        return cardPasscodesList;
    }

    /**
     * Sets the list of card passcodes.
     *
     * <p>This method sets the internal list of card passcodes to the provided list.
     * The list is used to map passcodes to their corresponding Konami IDs.
     *
     * @param cardPasscodesList the list of card passcodes as integers
     */
    public static void setCardPasscodesList(List<Integer> cardPasscodesList) {
        CardDatabaseManager.cardPasscodesList = cardPasscodesList;
    }

    /**
     * Creates the database maps.
     *
     * <p>This method creates the following maps from the JSON data:
     * <ul>
     * <li>passCodeToKonamiId: a map of passcodes to their corresponding Konami IDs</li>
     * <li>konamiIdToPassCode: a map of Konami IDs to their corresponding passcodes</li>
     * <li>passCodeToOtherPassCodes: a map of passcodes to their corresponding other passcodes</li>
     * <li>konamiIdToOtherKonamiIds: a map of Konami IDs to their corresponding other Konami IDs</li>
     * <li>konamiIdToArchetype: a map of Konami IDs to their corresponding archetypes</li>
     * <li>cardPasscodesList: a list of card passcodes as integers</li>
     * </ul>
     *
     * @throws Exception if there is an issue during the map creation process
     */
    public static void createDatabaseMaps() throws Exception {
        JSONObject enJsonObject = Database.openJson("en.json");
        JSONObject cardinfoJsonObject = Database.openJson("cardinfo.json");

        if (enJsonObject == null || cardinfoJsonObject == null) {
            throw new Exception("Database not found");
        }
        Map<String, List<Integer>> enJson = parseEnJson(enJsonObject);
        List<CardInfo> cardinfoJson = parseCardInfoJson(cardinfoJsonObject);

        passCodeToKonamiId = new HashMap<>();
        konamiIdToPassCode = new HashMap<>();
        createPassCodeToIdMap();
        passCodeToOtherPassCodes = new HashMap<>();
        konamiIdToOtherKonamiIds = new HashMap<>();
        konamiIdToArchetype = new HashMap<>();
        cardPasscodesList = new ArrayList<>();

        for (CardInfo card : cardinfoJson) {
            cardPasscodesList.add(card.getPassCode());
            List<Integer> numbers = enJson.get(card.getName());
            if (numbers != null && !numbers.isEmpty()) {
                for (Integer number : numbers) {
                    konamiIdToOtherKonamiIds.put(number, numbers);
                    konamiIdToArchetype.put(number, card.getArchetype());
                }

                List<Integer> cardImageIds = new ArrayList<>();
                for (CardImage cardImage : card.getCardImages()) {
                    cardImageIds.add(cardImage.getId());

                    if (!passCodeToKonamiId.containsKey(cardImage.getId())) {
                        passCodeToKonamiId.put(cardImage.getId(), numbers.get(0));
                    }
                    if (!konamiIdToPassCode.containsKey(numbers.get(0))) {
                        konamiIdToPassCode.put(numbers.get(0), cardImage.getId());
                    }
                }
                for (Integer cardImageId : cardImageIds) {
                    passCodeToOtherPassCodes.put(cardImageId, cardImageIds);
                }
            }
        }
    }

    /**
     * Parses the English JSON file and returns a map from card names to lists
     * of their corresponding Konami IDs.
     *
     * @param enJsonObject the English JSON file as a JSONObject
     * @return a map from card names to lists of their corresponding Konami IDs
     */
    private static Map<String, List<Integer>> parseEnJson(JSONObject enJsonObject) {
        Map<String, List<Integer>> enJson = new HashMap<>();
        for (String key : enJsonObject.keySet()) {
            JSONArray jsonArray = enJsonObject.getJSONArray(key);
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                list.add(jsonArray.getInt(i));
            }
            enJson.put(key, list);
        }
        return enJson;
    }

    /**
     * Parses the cardinfo JSON file and returns a list of CardInfo objects.
     *
     * <p>This method parses the given cardinfo JSON file and returns a list of
     * CardInfo objects containing the passcode, name, archetype, and card images
     * for each card.
     *
     * @param cardinfoJsonObject the cardinfo JSON file as a JSONObject
     * @return a list of CardInfo objects
     */
    private static List<CardInfo> parseCardInfoJson(JSONObject cardinfoJsonObject) {
        List<CardInfo> cardinfoJson = new ArrayList<>();
        JSONArray dataArray = cardinfoJsonObject.getJSONArray("data");
        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject dataObject = dataArray.getJSONObject(i);
            CardInfo cardInfo = new CardInfo();
            cardInfo.setPassCode(dataObject.getInt("id"));
            cardInfo.setName(dataObject.getString("name"));
            cardInfo.setArchetype(dataObject.optString("archetype", ""));
            JSONArray cardImagesArray = dataObject.getJSONArray("card_images");
            List<CardImage> cardImages = new ArrayList<>();
            for (int j = 0; j < cardImagesArray.length(); j++) {
                JSONObject cardImageObject = cardImagesArray.getJSONObject(j);
                CardImage cardImage = new CardImage();
                cardImage.setId(cardImageObject.getInt("id"));
                cardImages.add(cardImage);
            }
            cardInfo.setCardImages(cardImages);
            cardinfoJson.add(cardInfo);
        }
        return cardinfoJson;
    }

    /**
     * Converts a given passcode to its corresponding Konami ID.
     *
     * <p>This method attempts to retrieve the Konami ID associated with a given
     * passcode.
     * The method ensures that database maps are created if they have not been
     * initialized. If multiple passcodes exist for the same card, the first one
     * is used.
     *
     * @param passCode the passcode of the card to be converted
     * @return the Konami ID as a String, or null if the passcode is not found
     */
    public static String passCodeToId(int passCode) {
        try {
            if (passCodeToKonamiId == null) {
                createDatabaseMaps();
            }
            //TODO temporary, add exceptions properly if necessary (custom Future Fusion)
            if (passCode == 99900042) {
                passCode = 77565204;
            }
            //Get first passcode if multiple exist for this card
            return passCodeToKonamiId.get(getPassCodeToOtherPassCodes().get(passCode).get(0)).toString();
        } catch (Exception e) {
            //throw new Exception("ID not found : " + passCode);
            System.out.println("Passcode not found : " + passCode);
            return null;
        }
    }

    /**
     * Retrieves the artwork number associated with a given passcode.
     *
     * <p>This method retrieves the artwork number associated with a given
     * passcode. It does this by searching for the passcode in the list of
     * passcodes associated with the given card, and returning the index of the
     * passcode (1-indexed) if it is found. If the passcode is not found, the
     * method returns {@code 1}.
     *
     * @param passCode the passcode of the card to be converted
     * @return the artwork number as a String, or "1" if the passcode is not found
     * @throws Exception if there is an issue during the conversion process
     */
    public static String passCodeToArtNumber(int passCode) throws Exception {
        int returnValue = 1;
        for (int i = 0; i < getPassCodeToOtherPassCodes().get(passCode).size(); i++) {
            if (getPassCodeToOtherPassCodes().get(passCode).get(i) == passCode) {
                returnValue = i + 1;
            }
        }

        return String.valueOf(returnValue);
    }

    /**
     * Converts an artwork passcode to a passcode that is present in the list of card passcodes.
     *
     * <p>This method takes an artwork passcode as input and attempts to find a corresponding
     * passcode that is present in the list of card passcodes. If the artwork passcode is already
     * present in the list of card passcodes, the method returns the artwork passcode as a String.
     * Otherwise, the method iterates through the list of passcodes associated with the given
     * artwork passcode and returns the first passcode that is present in the list of card passcodes.
     *
     * @param artPassCode the artwork passcode to be converted
     * @return the passcode as a String, or throws an Exception if no passcode is found
     * @throws Exception if there is an issue during the conversion process
     */
    public static String artPassCodeToPassCode(int artPassCode) throws Exception {
        String returnValue; //If the passcode is not present in passCodeToKonamiId, we look for another one which is
        if (getCardPasscodesList().contains(artPassCode)) {
            returnValue = String.valueOf(artPassCode);
        } else {
            int i = 0;
            while (!getCardPasscodesList().contains(getPassCodeToOtherPassCodes().get(artPassCode).get(i)) && i < getPassCodeToOtherPassCodes().get(artPassCode).size()) {
                i++;
            }
            returnValue = getPassCodeToOtherPassCodes().get(artPassCode).get(i).toString();
        }

        return returnValue;
    }

    /**
     * Creates the maps of passcodes to Konami IDs and Konami IDs to passcodes from the cards_Lite.json file.
     *
     * <p>This method reads the cards_Lite.json file and creates two maps: one from passcodes to Konami IDs and
     * another from Konami IDs to passcodes. The method also prints the size of the maps and their contents for
     * debugging purposes.
     *
     * @throws JSONException if there is an issue with the JSON file
     */
    public static void createPassCodeToIdMap() {
        passCodeToKonamiId = new HashMap<>();
        konamiIdToPassCode = new HashMap<>();
        try {
            JSONObject jsonObject = Database.openJson("cards_Lite.json");
            if (jsonObject != null) {
                for (String key : jsonObject.keySet()) {
                    JSONObject innerObject = jsonObject.getJSONObject(key);
                    int id = innerObject.getInt("id");
                    int cid = innerObject.getInt("cid");
                    passCodeToKonamiId.put(id, cid);
                    konamiIdToPassCode.put(cid, id);
                }
            }

            // Print map size and contents for debugging
            System.out.println("Map size: " + passCodeToKonamiId.size());
            passCodeToKonamiId.forEach((id, cid) -> System.out.println("ID: " + id + ", CID: " + cid));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
