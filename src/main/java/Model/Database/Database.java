package Model.Database;

import Model.CardsLists.Card;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static Model.Database.CardDatabaseManager.getPassCodeToKonamiId;
import static Model.Database.FileFetcher.fetchFile;
import static Model.Database.KonamiIdToNames.*;

//TODO see DAO design pattern ?
public class Database {
    public static final Map<String, JSONObject> jsonContentMap = new HashMap<>();
    private static final List<String> setsList = new ArrayList<>();
    private static final Map<Integer, Card> allCardsList = new HashMap<>();

    // Static block to initialize the Map
    static {
        populateJsonContentMap();
    }

    /**
     * Fetches a JSON file from the remote location if it does not exist locally, then opens the local copy and returns its content as a JSONObject.
     * If the content is a JSON array, it is wrapped in a JSONObject with the single key "array".
     * If an error occurs while reading the file, null is returned.
     *
     * @param element the element to search for in the addresses.json file, which can be a file name or a key
     * @return the content of the JSON file as a JSONObject, or null if an error occurs
     */
    public static JSONObject openJson(String element) {
        fetchFile(element);
        String[] addresses = DataBaseUpdate.getAddresses(element);
        if (addresses.length > 0) {
            String localPath = addresses[0];
            try {
                String content = new String(Files.readAllBytes(Paths.get(localPath)));
                if (content.startsWith("[")) {
                    // Wrap the JSON array in a JSON object
                    JSONArray jsonArray = new JSONArray(content);
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("array", jsonArray);
                    return jsonObject;
                } else {
                    return new JSONObject(content);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Fetches a file specified by the given element from the remote location if it does not exist locally, then opens the local copy and returns its content as a list of strings.
     * The content is expected to be a JSON array of strings, and the list returned is a list of these strings, without any enclosing "[", "]", or "\" characters.
     * If an error occurs while reading the file, null is returned.
     *
     * @param element the element to search for in the addresses.json file, which can be a file name or a key
     * @return the content of the file as a list of strings, or null if an error occurs
     */
    public static List<String> openSets(String element) {
        fetchFile(element);
        String[] addresses = DataBaseUpdate.getAddresses(element);
        if (addresses.length > 0) {
            String localPath = addresses[0];
            try (Stream<String> lines = Files.lines(Paths.get(localPath))) {
                String content = lines.collect(Collectors.joining());
                return new ArrayList<>(List.of(content.replace("[", "").replace("]", "").replace("\"", "").split(",")));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Populates the jsonContentMap from the JSON object in the addresses.json file.
     * This method is called at the start of the program to initialize the map.
     * If an error occurs while reading the file, it is printed to the console.
     */
    public static void populateJsonContentMap() {
        try {
            String content = new String(Files.readAllBytes(Paths.get("src/main/java/Model/Database/addresses.json")));
            JSONObject json = new JSONObject(content);
            populateMapFromJson(json, "");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Populates the jsonContentMap and setsList from a given JSON object.
     * This method is called recursively to traverse the JSON object.
     * If a JSON object is encountered, the method is called recursively with the JSON object and the path + key + "/".
     * If a string is encountered and it does not contain "<" or ">", it is added to the jsonContentMap or setsList depending on whether the element ends with ".json" or ".txt".
     * The jsonContentMap contains the JSON objects with the key being the element without the part before "/" and the "/".
     * The setsList contains the strings from the JSON array with the key being the element without the part before "/" and the "/".
     * If an error occurs while reading the file, it is not printed to the console.
     *
     * @param json the JSON object to populate the jsonContentMap and setsList from
     * @param path the current path to append to
     */
    private static void populateMapFromJson(JSONObject json, String path) {
        for (String key : json.keySet()) {
            Object value = json.get(key);
            if (value instanceof JSONObject) {
                populateMapFromJson((JSONObject) value, path + key + "/");
            } else if (!key.contains("<") && !key.contains(">")) {
                String element = path + key;
                // Remove the part of the string before "/" and the "/" if there is one or more
                String modifiedElement = element.contains("/") ? element.substring(element.lastIndexOf("/") + 1) : element;
                if (modifiedElement.endsWith(".json")) {
                    JSONObject jsonContent = openJson(modifiedElement);
                    if (jsonContent != null) {
                        jsonContentMap.put(modifiedElement, jsonContent);
                    }
                } else if (modifiedElement.endsWith(".txt")) {
                    List<String> setsContent = openSets(modifiedElement);
                    if (setsContent != null) {
                        setsList.addAll(setsContent);
                    }
                }
            }
        }
    }

    /**
     * Populates the allCardsList map with all the cards from the cardinfo.json file.
     *
     * <p>This method reads the cardinfo.json file and creates a Card object for each card in the JSON array.
     * The method then populates the allCardsList map with the Card objects.
     * </p>
     *
     * <p>The Card objects are created with the following attributes:
     * <ul>
     * <li>passCode: the id of the card</li>
     * <li>konamiId: the Konami ID of the card</li>
     * <li>name_EN: the English name of the card</li>
     * <li>name_FR: the French name of the card</li>
     * <li>name_JA: the Japanese name of the card</li>
     * <li>imagePath: the path to the image of the card</li>
     * <li>archetypes: the archetypes of the card</li>
     * <li>cardType: the type of the card (Monster, Spell, Trap)</li>
     * <li>monsterType: the type of the Monster card (e.g. Normal, Effect, Ritual)</li>
     * <li>level: the level of the Monster card</li>
     * <li>rank: the rank of the Monster card</li>
     * <li>attribute: the attribute of the Monster card (e.g. LIGHT, DARK)</li>
     * <li>linkVal: the link value of the Monster card</li>
     * <li>linkMarker: the link markers of the Monster card</li>
     * <li>scale: the scale of the Monster card</li>
     * <li>price: the price of the card</li>
     * </ul>
     * </p>
     */
    private static void createAllCardsList() {
        JSONObject jsonObject = Database.jsonContentMap.get("cardinfo.json");
        try {
            if (jsonObject != null) {
                JSONArray dataArray = jsonObject.getJSONArray("data");
                for (int i = 0; i < dataArray.length(); i++) {
                    JSONObject dataObject = dataArray.getJSONObject(i);
                    for (int j = 0; j < dataObject.getJSONArray("card_images").length(); j++) {
                        try {
                            Card card = new Card();
                            card.setPassCode(String.valueOf(dataObject.getInt("id")));
                            card.setDescription(dataObject.optString("desc", ""));
                            card.setImagePath(String.valueOf(dataObject.getJSONArray("card_images").getJSONObject(j).getInt("id")));

                            Integer imageId = dataObject.getJSONArray("card_images").getJSONObject(j).getInt("id");
                            String cardId = String.valueOf(getPassCodeToKonamiId().get(imageId));
                            if (cardId != null && !cardId.equals("null")) {
                                card.setKonamiId(cardId);
                                Integer cardIdInt = Integer.valueOf(cardId);
                                if (getKonamiIdToEnNames().get(cardIdInt) != null) {
                                    card.setName_EN(getKonamiIdToEnNames().get(cardIdInt));
                                }
                                if (getKonamiIdToFrNames().get(cardIdInt) != null) {
                                    card.setName_FR(getKonamiIdToFrNames().get(cardIdInt));
                                }
                                if (getKonamiIdToJaNames().get(cardIdInt) != null) {
                                    card.setName_JA(getKonamiIdToJaNames().get(cardIdInt));
                                }
                            } else {
                                String name = dataObject.optString("name", null);
                                if (name != null) {
                                    card.setName_EN(name);
                                    String enCardId = String.valueOf(getEnNamesToKonamiId().get(name));
                                    if (enCardId != null && !enCardId.equals("null")) {
                                        card.setKonamiId(enCardId);
                                    }
                                }
                            }

                            fetchFile(imageId + ".jpg", dataObject.getJSONArray("card_images").getJSONObject(j).getString("image_url"));
                            card.setArtNumber(String.valueOf(j + 1));
                            if (dataObject.has("archetype")) {
                                card.setArchetypes(new ArrayList<>(List.of(dataObject.getString("archetype"))));
                            }
                            card.setCardType(dataObject.getString("type"));

                            // Set additional attributes for Monster cards
                            if (dataObject.getString("type").contains("Monster")) {
                                card.setMonsterType(dataObject.optString("race", ""));
                                card.setAtk(dataObject.optInt("atk", 0));
                                card.setDef(dataObject.optInt("def", 0));
                                card.setLevel(dataObject.optInt("level", 0));
                                card.setRank(dataObject.optInt("rank", 0));
                                card.setAttribute(dataObject.optString("attribute", ""));
                                card.setLinkVal(dataObject.optInt("linkval", 0));
                                if (dataObject.has("linkmarkers")) {
                                    JSONArray linkMarkersArray = dataObject.getJSONArray("linkmarkers");
                                    List<String> linkMarkers = new ArrayList<>();
                                    for (int k = 0; k < linkMarkersArray.length(); k++) {
                                        linkMarkers.add(linkMarkersArray.getString(k));
                                    }
                                    card.setLinkMarker(linkMarkers);
                                }
                                card.setScale(dataObject.optInt("scale", 0));
                            }

                            // Set the price of the card
                            if (dataObject.has("card_prices")) {
                                JSONArray cardPricesArray = dataObject.getJSONArray("card_prices");
                                if (!cardPricesArray.isEmpty()) {
                                    JSONObject cardPriceObject = cardPricesArray.getJSONObject(0);
                                    card.setPrice(cardPriceObject.optString("cardmarket_price", "0.0"));
                                }
                            }

                            // Add properties using typeline
                            if (dataObject.has("typeline")) {
                                JSONArray typelineArray = dataObject.getJSONArray("typeline");
                                List<String> typeline = new ArrayList<>();
                                for (int k = 0; k < typelineArray.length(); k++) {
                                    typeline.add(typelineArray.getString(k));
                                }
                                card.setCardProperties(typeline);
                            } else if (dataObject.has("race")) {
                                String race = dataObject.getString("race");
                                List<String> typeline = new ArrayList<>();
                                typeline.add(race);
                                card.setCardProperties(typeline);
                            }

                            allCardsList.put(imageId, card);
                        } catch (Exception e) {
                            System.out.println("Error during the creation of allCardsList: " + e.getMessage());
                        }
                    }
                }
                System.out.println("allCardsList created");
            }
        } catch (Exception e) {
            System.out.println("Error during the creation of allCardsList: " + e.getMessage());
        }
    }

    /**
     * Retrieves the list of all cards in the database.
     * <p>
     * If the list is empty, it will be created first.
     * </p>
     *
     * @return the list of all cards in the database
     */
    public static Map<Integer, Card> getAllCardsList() {
        if (allCardsList.isEmpty()) {
            createAllCardsList();
        }
        return allCardsList;
    }
}
