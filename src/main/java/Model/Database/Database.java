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

import static Model.Database.KonamiIdToNames.*;
import static Model.Database.FileFetcher.fetchFile;
import static Model.Database.CardDatabaseManager.getPassCodeToKonamiId;

//TODO see DAO design pattern ?
public class Database {
    public static final Map<String, JSONObject> jsonContentMap = new HashMap<>();
    private static final List<String> setsList = new ArrayList<>();
    private static final Map<Integer, Card> allCardsList = new HashMap<>();

    // Function to open a JSON file and return its content as a JSONObject
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


    // Function to open a text file and return its content as a List
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

    // Function to copy an image file to a specified destination path
    public static void copyImage(String element, String destinationPath) {
        element += ".jpg";
        fetchFile(element);
        String[] addresses = DataBaseUpdate.getAddresses(element);
        if (addresses.length > 0) {
            String localPath = addresses[0];
            try {
                Files.copy(Paths.get(localPath), Paths.get(destinationPath));
                System.out.println("Image copied to destination: " + destinationPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Function to populate the static Map with JSON content
    public static void populateJsonContentMap() {
        try {
            String content = new String(Files.readAllBytes(Paths.get("src/main/java/Model/Database/addresses.json")));
            JSONObject json = new JSONObject(content);
            populateMapFromJson(json, "");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Recursive function to populate the Map from the JSON object TODO make it recursive again
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

    private static void createAllCardsList() throws Exception {
        JSONObject jsonObject = (JSONObject) Database.jsonContentMap.get("cardinfo.json");
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
                                if (cardPricesArray.length() > 0) {
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
                            }
                            else if (dataObject.has("race")) {
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


    public static Map<Integer, Card> getAllCardsList() throws Exception {
        if (allCardsList.isEmpty()) {
            createAllCardsList();
        }
        return allCardsList;
    }

    // Static block to initialize the Map
    static {
        populateJsonContentMap();
    }
}
