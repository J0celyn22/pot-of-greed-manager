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

    public static Map<Integer, List<Integer>> getPassCodeToOtherPassCodes() throws Exception {
        if (passCodeToOtherPassCodes == null) {
            createDatabaseMaps();
        }
        return passCodeToOtherPassCodes;
    }

    public static void setPassCodeToOtherPassCodes(Map<Integer, List<Integer>> passCodeToOtherPassCodes) {
        CardDatabaseManager.passCodeToOtherPassCodes = passCodeToOtherPassCodes;
    }

    public static Map<Integer, Integer> getPassCodeToKonamiId() throws Exception {
        if (passCodeToKonamiId == null) {
            createDatabaseMaps();
        }
        return passCodeToKonamiId;
    }

    public static void setPassCodeToKonamiId(Map<Integer, Integer> passCodeToKonamiId) {
        CardDatabaseManager.passCodeToKonamiId = passCodeToKonamiId;
    }

    public static Map<Integer, Integer> getKonamiIdToPassCode() throws Exception {
        if (konamiIdToPassCode == null) {
            createDatabaseMaps();
        }
        return konamiIdToPassCode;
    }

    public static void setKonamiIdToPassCode(Map<Integer, Integer> konamiIdToPassCode) {
        CardDatabaseManager.konamiIdToPassCode = konamiIdToPassCode;
    }

    public static Map<Integer, List<Integer>> getKonamiIdToOtherKonamiIds() {
        return konamiIdToOtherKonamiIds;
    }

    public static void setKonamiIdToOtherKonamiIds(Map<Integer, List<Integer>> konamiIdToOtherKonamiIds) {
        CardDatabaseManager.konamiIdToOtherKonamiIds = konamiIdToOtherKonamiIds;
    }

    public static Map<Integer, String> getKonamiIdToArchetype() {
        return konamiIdToArchetype;
    }

    public static void setKonamiIdToArchetype(Map<Integer, String> konamiIdToArchetype) {
        CardDatabaseManager.konamiIdToArchetype = konamiIdToArchetype;
    }

    public static List<Integer> getKonamiIdToOtherKonamiId(Integer konamiId) throws Exception {
        if (konamiIdToOtherKonamiIds == null) {
            createDatabaseMaps();
        }
        return konamiIdToOtherKonamiIds.get(konamiId);
    }

    public static String getKonamiIdToArchetype(Integer konamiId) throws Exception {
        if (konamiIdToArchetype == null) {
            createDatabaseMaps();
        }
        return konamiIdToArchetype.get(konamiId);
    }

    public static List<Integer> getCardPasscodesList() throws Exception {
        if (cardPasscodesList == null) {
            createDatabaseMaps();
        }
        return cardPasscodesList;
    }

    public static void setCardPasscodesList(List<Integer> cardPasscodesList) {
        CardDatabaseManager.cardPasscodesList = cardPasscodesList;
    }

    /*public static void createDatabaseMaps() throws Exception {
        JSONObject enJsonObject = Database.openJson("en.json");
        JSONObject cardinfoJsonObject = Database.openJson("cardinfo.json");

        Map<String, List<Integer>> enJson = new HashMap<>();
        for(String key : enJsonObject.keySet()){
            JSONArray jsonArray = enJsonObject.getJSONArray(key);
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                list.add(jsonArray.getInt(i));
            }
            enJson.put(key, list);
        }

        List<CardInfo> cardinfoJson = new ArrayList<>();
        JSONArray dataArray = cardinfoJsonObject.getJSONArray("data");
        for (int i = 0; i < dataArray.length(); i++) {
            JSONObject dataObject = dataArray.getJSONObject(i);
            CardInfo cardInfo = new CardInfo();
            cardInfo.setPassCode(dataObject.getInt("id"));
            cardInfo.setName(dataObject.getString("name"));
            if (dataObject.has("archetype")) {
                cardInfo.setArchetype(dataObject.getString("archetype"));
            } else {
                cardInfo.setArchetype(""); // Set to an empty string if "archetype" doesn't exist
            }

            JSONArray cardImagesArray = dataObject.getJSONArray("card_images");
            List<CardImage> cardImages = new ArrayList<>();
            for (int j = 0; j < cardImagesArray.length(); j++) {
                JSONObject cardImageObject = cardImagesArray.getJSONObject(j);
                CardImage cardImage = new CardImage();
                cardImage.setId(cardImageObject.getInt("id"));
                // populate other fields...
                cardImages.add(cardImage);
            }
            cardInfo.setCardImages(cardImages);

            cardinfoJson.add(cardInfo);
        }

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
                for (int i = 0; i < numbers.size(); i++) {
                    konamiIdToOtherKonamiIds.put(numbers.get(i), numbers);
                    konamiIdToArchetype.put(numbers.get(i), card.getArchetype());
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
                for (int i = 0; i < cardImageIds.size(); i++) {
                    passCodeToOtherPassCodes.put(cardImageIds.get(i), cardImageIds);
                }
            }
        }
    }*/

    public static void createDatabaseMaps() throws Exception {
        JSONObject enJsonObject = Database.openJson("en.json");
        JSONObject cardinfoJsonObject = Database.openJson("cardinfo.json");

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

    public static String passCodeToId(int passCode) throws Exception {
        try {
            if (passCodeToKonamiId == null) {
                createDatabaseMaps();
            }
            //TODO temporary, add exceptions properly if necessary (custom Future Fusion)
            if(passCode == 99900042) {
                passCode = 77565204;
            }
            //Get first passcode if multiple exist for this card
            String konamiId = "";
            konamiId = passCodeToKonamiId.get(getPassCodeToOtherPassCodes().get(passCode).get(0)).toString();
            return konamiId;
        }
        catch (Exception e) {
            //throw new Exception("ID not found : " + passCode);
            System.out.println("Passcode not found : " + passCode);
            return null;
        }
    }

    public static String passCodeToArtNumber(int passCode) throws Exception {
        int returnValue = 1;
        for(int i = 0; i < getPassCodeToOtherPassCodes().get(passCode).size(); i++) {
            if (getPassCodeToOtherPassCodes().get(passCode).get(i) == passCode) {
                returnValue = i + 1;
            }
        }

        return String.valueOf(returnValue);
    }

    public static String artPassCodeToPassCode(int artPassCode) throws Exception {
        String returnValue = ""; //If the passcode is not present in passCodeToKonamiId, we look for another one which is
        if (getCardPasscodesList().contains(artPassCode)) {
            returnValue = String.valueOf(artPassCode);
        }
        else {
            int i = 0;
            while(!getCardPasscodesList().contains(getPassCodeToOtherPassCodes().get(artPassCode).get(i)) && i < getPassCodeToOtherPassCodes().get(artPassCode).size()) {
                i++;
            }
            returnValue = getPassCodeToOtherPassCodes().get(artPassCode).get(i).toString();
        }

        return returnValue;
    }

    public static void createPassCodeToIdMap() throws Exception {
        passCodeToKonamiId = new HashMap<>();
        konamiIdToPassCode = new HashMap<>();
        try {
            JSONObject jsonObject = Database.openJson("cards_Lite.json");
            for (String key : jsonObject.keySet()) {
                JSONObject innerObject = jsonObject.getJSONObject(key);
                int id = innerObject.getInt("id");
                int cid = innerObject.getInt("cid");
                passCodeToKonamiId.put(id, cid);
                konamiIdToPassCode.put(cid, id);
            }

            // Print map size and contents for debugging
            System.out.println("Map size: " + passCodeToKonamiId.size());
            passCodeToKonamiId.forEach((id, cid) -> System.out.println("ID: " + id + ", CID: " + cid));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
