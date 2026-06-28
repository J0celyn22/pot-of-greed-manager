package Model.Database;

import Model.Database.CardInfo.CardImage;
import Model.Database.CardInfo.CardInfo;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CardDatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(CardDatabaseManager.class);

    private static Map<Integer, Integer> passCodeToKonamiId;
    private static Map<Integer, Integer> konamiIdToPassCode;
    private static Map<Integer, List<Integer>> passCodeToOtherPassCodes;
    private static Map<Integer, List<Integer>> konamiIdToOtherKonamiIds;
    private static Map<Integer, String> konamiIdToArchetype;
    private static List<Integer> cardPasscodesList;

    // Getter and setter methods
    public static Map<Integer, List<Integer>> getPassCodeToOtherPassCodes() throws Exception {
        if (passCodeToOtherPassCodes == null) {
            createDatabaseMaps();
        }
        return passCodeToOtherPassCodes;
    }

    public static void setPassCodeToOtherPassCodes(Map<Integer, List<Integer>> map) {
        passCodeToOtherPassCodes = map;
    }

    public static Map<Integer, Integer> getPassCodeToKonamiId() throws Exception {
        if (passCodeToKonamiId == null) {
            createDatabaseMaps();
        }
        return passCodeToKonamiId;
    }

    public static void setPassCodeToKonamiId(Map<Integer, Integer> map) {
        passCodeToKonamiId = map;
    }

    public static Map<Integer, Integer> getKonamiIdToPassCode() throws Exception {
        if (konamiIdToPassCode == null) {
            createDatabaseMaps();
        }
        return konamiIdToPassCode;
    }

    public static void setKonamiIdToPassCode(Map<Integer, Integer> map) {
        konamiIdToPassCode = map;
    }

    public static Map<Integer, List<Integer>> getKonamiIdToOtherKonamiIds() {
        return konamiIdToOtherKonamiIds;
    }

    public static void setKonamiIdToOtherKonamiIds(Map<Integer, List<Integer>> map) {
        konamiIdToOtherKonamiIds = map;
    }

    public static Map<Integer, String> getKonamiIdToArchetype() {
        return konamiIdToArchetype;
    }

    public static void setKonamiIdToArchetype(Map<Integer, String> map) {
        konamiIdToArchetype = map;
    }

    public static List<Integer> getCardPasscodesList() throws Exception {
        if (cardPasscodesList == null) {
            createDatabaseMaps();
        }
        return cardPasscodesList;
    }

    public static void setCardPasscodesList(List<Integer> list) {
        cardPasscodesList = list;
    }

    // Added missing method to satisfy calls to getKonamiIdToOtherKonamiId(...)
    public static List<Integer> getKonamiIdToOtherKonamiId(Integer konamiId) throws Exception {
        if (konamiIdToOtherKonamiIds == null) {
            createDatabaseMaps();
        }
        return konamiIdToOtherKonamiIds.get(konamiId);
    }

    /**
     * Creates the database maps.
     *
     * This method creates the following maps from the JSON data:
     *   - passCodeToKonamiId: a map of passcodes to their corresponding Konami IDs
     *   - konamiIdToPassCode: a map of Konami IDs to their corresponding passcodes
     *   - passCodeToOtherPassCodes: a map of passcodes to their corresponding other passcodes
     *   - konamiIdToOtherKonamiIds: a map of Konami IDs to their corresponding other Konami IDs
     *   - konamiIdToArchetype: a map of Konami IDs to their corresponding archetypes
     *   - cardPasscodesList: a list of card passcodes as integers
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

        // For each Konami ID from the English names dictionary, if its mapping is missing, try to complete it.
        for (String key : enJson.keySet()) {
            List<Integer> konamiIds = enJson.get(key);
            if (konamiIds != null) {
                for (Integer konamiId : konamiIds) {
                    if (!konamiIdToPassCode.containsKey(konamiId)) {
                        logger.debug("Trying to complete Konami ID: {}", konamiId);
                        completeKonamiIdToPassCode(konamiId);
                    }
                }
            }
        }
    }

    /**
     * Completes the mapping of a given Konami ID to its corresponding passcode.
     *
     * Before attempting any network access, the ID is checked against
     * {@link NotFoundCache}.  If it was previously returned a 4xx response and
     * the retry window has not expired, the method returns immediately without
     * making a request.
     *
     * When the server returns a 4xx response (card genuinely absent from the
     * database), the ID is recorded in {@link NotFoundCache} so that it is not
     * retried until the configured retry window ({@link NotFoundCache#RETRY_DAYS}
     * days) has elapsed.  Transient network errors are still logged normally and
     * are retried on the next startup.
     *
     * @param konamiId the Konami ID that needs its passcode mapping completed
     */
    public static void completeKonamiIdToPassCode(Integer konamiId) {
        // Skip IDs that are known to be absent from the database until their
        // retry window expires.
        if (NotFoundCache.isKnownNotFound(konamiId)) {
            return;
        }

        String fileName = konamiId + ".json";
        try {
            FileFetcher.fetchFile(fileName);
            JSONObject jsonObject = Database.openJson(fileName);
            if (jsonObject != null) {
                JSONArray dataArray = jsonObject.getJSONArray("data");
                if (dataArray.length() > 0) {
                    JSONObject cardData = dataArray.getJSONObject(0);
                    int passcode = cardData.getInt("id");
                    konamiIdToPassCode.put(konamiId, passcode);
                } else {
                    logger.warn("No card data found for Konami ID {}", konamiId);
                    // Empty data array = the server knows of no such card.
                    NotFoundCache.markAsNotFound(konamiId);
                }
            } else {
                logger.warn("Failed to load JSON for Konami ID {}", konamiId);
            }
        } catch (RuntimeException e) {
            // RuntimeException wraps a FileNotFoundException thrown by fetchFile
            // when the server returns 4xx — the card does not exist remotely.
            Throwable cause = (e instanceof RuntimeException && e.getCause() != null)
                    ? e.getCause() : e;
            if (cause instanceof java.io.FileNotFoundException) {
                logger.warn("Konami ID {} not found in remote database (HTTP 4xx) — caching.");
                NotFoundCache.markAsNotFound(konamiId);
            } else {
                // Genuine RuntimeException unrelated to 4xx — log but don't cache.
                logger.error("Error loading file for Konami ID {}: {}", konamiId, e.getMessage());
            }
        } catch (Exception e) {
            // Transient error (network down, timeout, etc.) — do NOT cache,
            // so the next startup retries automatically.
            logger.error("Error loading file for Konami ID {}: {}", konamiId, e.getMessage());
        }
    }

    // Dummy method for createPassCodeToIdMap; assume it's implemented elsewhere.
    public static void createPassCodeToIdMap() {
        // Implementation here
    }

    /**
     * Parses the English JSON file and returns a map from card names to lists of their corresponding Konami IDs.
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

    // Other methods (such as passCodeToId, passCodeToArtNumber, artPassCodeToPassCode, etc.) would be here.

    /**
     * Returns all {@link Model.CardsLists.Card} aliases for the given passCode —
     * i.e. every artwork variant of the same card, in the order they appear in
     * the database (artwork 1 first, artwork 2 second, etc.).
     *
     * <p>The result is derived from {@code passCodeToOtherPassCodes}, which maps
     * every passCode to the full list of passCodes sharing the same card name.
     * Each passCode in that list corresponds to one artwork entry in
     * {@code allCardsList}, with {@link Model.CardsLists.Card#getArtNumber()}
     * already set to its 1-based index.</p>
     *
     * <p>If the passCode is unknown or has no siblings, a list containing only
     * the card itself is returned (single-artwork card).</p>
     *
     * @param passCode the passCode of any artwork variant of the card
     * @return an ordered, non-null list of {@link Model.CardsLists.Card} objects,
     * one per artwork variant
     * @throws Exception if the database maps have not been initialised yet
     */
    public static List<Model.CardsLists.Card> getAliasCards(int passCode) throws Exception {
        if (passCodeToOtherPassCodes == null) {
            createDatabaseMaps();
        }
        Map<Integer, Model.CardsLists.Card> allCards = Database.getAllCardsList();
        List<Integer> siblings = passCodeToOtherPassCodes.get(passCode);

        if (siblings == null || siblings.isEmpty()) {
            // Single-artwork card or passCode not in the map — return just itself
            Model.CardsLists.Card self = allCards.get(passCode);
            return self != null ? List.of(self) : List.of();
        }

        List<Model.CardsLists.Card> result = new ArrayList<>();
        for (Integer sibling : siblings) {
            Model.CardsLists.Card card = allCards.get(sibling);
            if (card != null) result.add(card);
        }
        return result;
    }
}