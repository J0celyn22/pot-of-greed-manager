package Model.CardsLists;

import Model.Database.Database;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SubListCreator {
    public static List<CardElement> monsterList = new ArrayList<>();
    public static List<CardElement> spellList = new ArrayList<>();
    public static List<CardElement> trapList = new ArrayList<>();

    public static List<CardElement> pyroTypeMonster = new ArrayList<>();
    public static List<CardElement> aquaTypeMonster = new ArrayList<>();
    public static List<CardElement> machineTypeMonster = new ArrayList<>();
    public static List<CardElement> dragonTypeMonster = new ArrayList<>();
    public static List<CardElement> beastWarriorTypeMonster = new ArrayList<>();
    public static List<CardElement> reptileTypeMonster = new ArrayList<>();
    public static List<CardElement> plantTypeMonster = new ArrayList<>();
    public static List<CardElement> fiendTypeMonster = new ArrayList<>();
    public static List<CardElement> wyrmTypeMonster = new ArrayList<>();
    public static List<CardElement> dinosaurTypeMonster = new ArrayList<>();
    public static List<CardElement> spellcasterTypeMonster = new ArrayList<>();
    public static List<CardElement> fishTypeMonster = new ArrayList<>();
    public static List<CardElement> divineBeastTypeMonster = new ArrayList<>();
    public static List<CardElement> cyberseTypeMonster = new ArrayList<>();
    public static List<CardElement> insectTypeMonster = new ArrayList<>();
    public static List<CardElement> wingedBeastTypeMonster = new ArrayList<>();
    public static List<CardElement> warriorTypeMonster = new ArrayList<>();
    public static List<CardElement> rockTypeMonster = new ArrayList<>();
    public static List<CardElement> thunderTypeMonster = new ArrayList<>();
    public static List<CardElement> zombieTypeMonster = new ArrayList<>();
    public static List<CardElement> seaSerpentTypeMonster = new ArrayList<>();
    public static List<CardElement> beastTypeMonster = new ArrayList<>();
    public static List<CardElement> psychicTypeMonster = new ArrayList<>();
    public static List<CardElement> fairyTypeMonster = new ArrayList<>();
    public static List<CardElement> illusionTypeMonster = new ArrayList<>();
    public static List<CardElement> normalMonsterCard = new ArrayList<>();
    public static List<CardElement> toonMonsterCard = new ArrayList<>();
    public static List<CardElement> tunerMonsterCard = new ArrayList<>();
    public static List<CardElement> unionMonsterCard = new ArrayList<>();
    public static List<CardElement> synchroMonsterCard = new ArrayList<>();
    public static List<CardElement> pendulumMonsterCard = new ArrayList<>();
    public static List<CardElement> ritualMonsterCard = new ArrayList<>();
    public static List<CardElement> flipMonsterCard = new ArrayList<>();
    public static List<CardElement> spiritMonsterCard = new ArrayList<>();
    public static List<CardElement> xyzMonsterCard = new ArrayList<>();
    public static List<CardElement> effectMonsterCard = new ArrayList<>();
    public static List<CardElement> fusionMonsterCard = new ArrayList<>();
    public static List<CardElement> linkMonsterCard = new ArrayList<>();
    public static List<CardElement> geminiMonsterCard = new ArrayList<>();
    public static List<CardElement> normalSpellCard = new ArrayList<>();
    public static List<CardElement> continuousSpellCard = new ArrayList<>();
    public static List<CardElement> quickPlaySpellCard = new ArrayList<>();
    public static List<CardElement> equipSpellCard = new ArrayList<>();
    public static List<CardElement> fieldSpellCard = new ArrayList<>();
    public static List<CardElement> ritualSpellCard = new ArrayList<>();
    public static List<CardElement> normalTrapCard = new ArrayList<>();
    public static List<CardElement> continuousTrapCard = new ArrayList<>();
    public static List<CardElement> counterTrapCard = new ArrayList<>();

    public static List<List<Card>> archetypesCardsLists = new ArrayList<>();
    public static List<String> archetypesList = new ArrayList<>();


    /**
     * Separates the given cardsList into 3 sublists: monsterList, spellList, and trapList.
     *
     * @param cardsList The list of cards to separate.
     */
    public static void CreateSubLists(List<CardElement> cardsList) {
        monsterList = new ArrayList<>();
        spellList = new ArrayList<>();
        trapList = new ArrayList<>();
        for (CardElement card : cardsList) {
            if (card.getCard().getCardType() != null) {
                if (card.getCard().getCardType().contains("Monster")) {
                    monsterList.add(card);
                } else if (card.getCard().getCardType().contains("Spell")) {
                    spellList.add(card);
                } else if (card.getCard().getCardType().contains("Trap")) {
                    trapList.add(card);
                } else {
                    System.out.println("Card type not found : " + card.getCard().getName_EN() + " - " + card.getCard().getPrintCode() + " - " + card.getCard().getKonamiId());
                }
            } else {
                System.out.println("Card type not found : " + card.getCard().getName_EN() + " - " + card.getCard().getPrintCode() + " - " + card.getCard().getKonamiId());
            }
        }
    }

    /**
     * Separates the given monsterList into several sublists based on the different types of monsters.
     * @param cardsList The list of cards to separate.
     */
    public static void CreateSubMonsterLists(List<CardElement> cardsList) {
        pyroTypeMonster = new ArrayList<>();
        aquaTypeMonster = new ArrayList<>();
        machineTypeMonster = new ArrayList<>();
        dragonTypeMonster = new ArrayList<>();
        beastWarriorTypeMonster = new ArrayList<>();
        reptileTypeMonster = new ArrayList<>();
        plantTypeMonster = new ArrayList<>();
        fiendTypeMonster = new ArrayList<>();
        wyrmTypeMonster = new ArrayList<>();
        dinosaurTypeMonster = new ArrayList<>();
        spellcasterTypeMonster = new ArrayList<>();
        fishTypeMonster = new ArrayList<>();
        divineBeastTypeMonster = new ArrayList<>();
        cyberseTypeMonster = new ArrayList<>();
        insectTypeMonster = new ArrayList<>();
        wingedBeastTypeMonster = new ArrayList<>();
        warriorTypeMonster = new ArrayList<>();
        rockTypeMonster = new ArrayList<>();
        thunderTypeMonster = new ArrayList<>();
        zombieTypeMonster = new ArrayList<>();
        seaSerpentTypeMonster = new ArrayList<>();
        beastTypeMonster = new ArrayList<>();
        psychicTypeMonster = new ArrayList<>();
        fairyTypeMonster = new ArrayList<>();
        illusionTypeMonster = new ArrayList<>();
        normalMonsterCard = new ArrayList<>();
        toonMonsterCard = new ArrayList<>();
        tunerMonsterCard = new ArrayList<>();
        unionMonsterCard = new ArrayList<>();
        synchroMonsterCard = new ArrayList<>();
        pendulumMonsterCard = new ArrayList<>();
        ritualMonsterCard = new ArrayList<>();
        flipMonsterCard = new ArrayList<>();
        spiritMonsterCard = new ArrayList<>();
        xyzMonsterCard = new ArrayList<>();
        effectMonsterCard = new ArrayList<>();
        fusionMonsterCard = new ArrayList<>();
        linkMonsterCard = new ArrayList<>();
        geminiMonsterCard = new ArrayList<>();

        for (CardElement card : cardsList) {
            if (card.getCard().getCardType() != null) {
                for (String property : card.getCard().getCardProperties()) {
                    switch (property) {
                        case "Pyro":
                            pyroTypeMonster.add(card);
                            break;

                        case "Aqua":
                            aquaTypeMonster.add(card);
                            break;

                        case "Machine":
                            machineTypeMonster.add(card);
                            break;

                        case "Dragon":
                            dragonTypeMonster.add(card);
                            break;

                        case "Beast-Warrior":
                            beastWarriorTypeMonster.add(card);
                            break;

                        case "Reptile":
                            reptileTypeMonster.add(card);
                            break;

                        case "Plant":
                            plantTypeMonster.add(card);
                            break;

                        case "Fiend":
                            fiendTypeMonster.add(card);
                            break;

                        case "Wyrm":
                            wyrmTypeMonster.add(card);
                            break;

                        case "Dinosaur":
                            dinosaurTypeMonster.add(card);
                            break;

                        case "Spellcaster":
                            spellcasterTypeMonster.add(card);
                            break;

                        case "Fish":
                            fishTypeMonster.add(card);
                            break;

                        case "Divine-Beast":
                            divineBeastTypeMonster.add(card);
                            break;

                        case "Cyberse":
                            cyberseTypeMonster.add(card);
                            break;

                        case "Insect":
                            insectTypeMonster.add(card);
                            break;

                        case "Winged Beast":
                            wingedBeastTypeMonster.add(card);
                            break;

                        case "Warrior":
                            warriorTypeMonster.add(card);
                            break;

                        case "Rock":
                            rockTypeMonster.add(card);
                            break;

                        case "Thunder":
                            thunderTypeMonster.add(card);
                            break;

                        case "Zombie":
                            zombieTypeMonster.add(card);
                            break;

                        case "Sea Serpent":
                            seaSerpentTypeMonster.add(card);
                            break;

                        case "Beast":
                            beastTypeMonster.add(card);
                            break;

                        case "Psychic":
                            psychicTypeMonster.add(card);
                            break;

                        case "Fairy":
                            fairyTypeMonster.add(card);
                            break;

                        case "Illusion":
                            illusionTypeMonster.add(card);
                            break;

                        case "Normal":
                            normalMonsterCard.add(card);
                            break;

                        case "Toon":
                            toonMonsterCard.add(card);
                            break;

                        case "Tuner":
                            tunerMonsterCard.add(card);
                            break;

                        case "Union":
                            unionMonsterCard.add(card);
                            break;

                        case "Synchro":
                            synchroMonsterCard.add(card);
                            break;

                        case "Pendulum":
                            pendulumMonsterCard.add(card);
                            break;

                        case "Ritual":
                            ritualMonsterCard.add(card);
                            break;

                        case "Flip":
                            flipMonsterCard.add(card);
                            break;

                        case "Spirit":
                            spiritMonsterCard.add(card);
                            break;

                        case "Xyz":
                            xyzMonsterCard.add(card);
                            break;

                        case "Effect":
                            effectMonsterCard.add(card);
                            break;

                        case "Fusion":
                            fusionMonsterCard.add(card);
                            break;

                        case "Link":
                            linkMonsterCard.add(card);
                            break;

                        case "Gemini":
                            geminiMonsterCard.add(card);
                            break;

                        default:
                            System.out.println("Monster Card subtype not found : " + card.getCard().getName_EN() + " - " + card.getCard().getPrintCode() + " - " + card.getCard().getKonamiId());
                            break;
                    }
                }
            } else {
                System.out.println("Monster Card subtype not found : " + card.getCard().getName_EN() + " - " + card.getCard().getPrintCode() + " - " + card.getCard().getKonamiId());
            }
        }
    }


    /**
     * Generates several sublists of the given list of Spell cards based on the different types of cards.
     *
     * @param cardsList the list of Spell cards to generate the sublists from
     */
    public static void CreateSubSpellLists(List<CardElement> cardsList) {
        normalSpellCard = new ArrayList<>();
        continuousSpellCard = new ArrayList<>();
        quickPlaySpellCard = new ArrayList<>();
        equipSpellCard = new ArrayList<>();
        fieldSpellCard = new ArrayList<>();
        ritualSpellCard = new ArrayList<>();

        for (CardElement card : cardsList) {
            if (card.getCard().getCardType() != null) {
                for (String property : card.getCard().getCardProperties()) {
                    switch (property) {
                        case "Normal":
                            normalSpellCard.add(card);
                            break;

                        case "Continuous":
                            continuousSpellCard.add(card);
                            break;

                        case "Quick-Play":
                            quickPlaySpellCard.add(card);
                            break;

                        case "Equip":
                            equipSpellCard.add(card);
                            break;

                        case "Field":
                            fieldSpellCard.add(card);
                            break;

                        case "Ritual":
                            ritualSpellCard.add(card);
                            break;

                        default:
                            System.out.println("Spell Card subtype not found : " + card.getCard().getName_EN() + " - " + card.getCard().getPrintCode() + " - " + card.getCard().getKonamiId());
                            break;
                    }
                }
            } else {
                System.out.println("Spell Card subtype not found : " + card.getCard().getName_EN() + " - " + card.getCard().getPrintCode() + " - " + card.getCard().getKonamiId());
            }
        }
    }

    /**
     * Generates several sublists of the given list of Trap cards based on the different types of cards.
     *
     * @param cardsList the list of Trap cards to generate the sublists from
     */
    public static void CreateSubTrapLists(List<CardElement> cardsList) {
        normalTrapCard = new ArrayList<>();
        continuousTrapCard = new ArrayList<>();
        counterTrapCard = new ArrayList<>();

        for (CardElement card : cardsList) {
            if (card.getCard().getCardType() != null) {
                for (String property : card.getCard().getCardProperties()) {
                    switch (property) {
                        case "Normal":
                            normalTrapCard.add(card);
                            break;

                        case "Continuous":
                            continuousTrapCard.add(card);
                            break;

                        case "Counter":
                            counterTrapCard.add(card);
                            break;

                        default:
                            System.out.println("Trap Card subtype not found : " + card.getCard().getName_EN() + " - " + card.getCard().getPrintCode() + " - " + card.getCard().getKonamiId());
                            break;
                    }
                }
            } else {
                System.out.println("Trap Card subtype not found : " + card.getCard().getName_EN() + " - " + card.getCard().getPrintCode() + " - " + card.getCard().getKonamiId());
            }
        }
    }

    /**
     * Creates the archetypes lists from the given list of cards.
     * <p>
     * The archetypes are loaded from the "archetypes.json" file.
     * Each archetype is represented by its name and a list of cards.
     * </p>
     * @param cardsList the list of cards to generate the archetypes lists from
     */
    public static void CreateArchetypeLists(Map<Integer, Card> cardsList) {
        archetypesCardsLists = new ArrayList<>();
        archetypesList = new ArrayList<>();

        // Load archetypes from the JSON file
        JSONObject archetypesListJsonObject = Database.openJson("archetypes.json");
        if (archetypesListJsonObject != null) {
            // Check if the JSON object contains the array
            if (archetypesListJsonObject.has("array")) {
                JSONArray archetypesArray = archetypesListJsonObject.getJSONArray("array");
                for (int i = 0; i < archetypesArray.length(); i++) {
                    String archetypeName = archetypesArray.getJSONObject(i).getString("archetype_name");
                    archetypesList.add(archetypeName);
                    archetypesCardsLists.add(new ArrayList<>());
                }
            }
        }

        // Iterate through the list of cards and add each card to the corresponding archetypes
        for (Map.Entry<Integer, Card> entry : cardsList.entrySet()) {
            Card card = entry.getValue();
            List<Integer> indexes = getIntegers(card);

            if (!indexes.isEmpty()) {
                for (int index : indexes) {
                    archetypesCardsLists.get(index).add(card);
                }
            }
        }
    }

    /**
     * Retrieves a list of indexes for archetypes associated with the given card.
     * <p>
     * This method iterates over the list of archetypes and checks if each archetype
     * is associated with the provided card. An archetype is considered associated
     * if it matches any of the card's archetypes or if the card's English name contains
     * the archetype name.
     * </p>
     *
     * @param card the Card object for which to find associated archetype indexes
     * @return a List of Integer indexes representing the positions of associated archetypes
     */
    private static List<Integer> getIntegers(Card card) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < archetypesList.size(); i++) {
            if (card.getArchetypes() != null) {
                boolean added = false;
                for (int j = 0; j < card.getArchetypes().size(); j++) {
                    if (card.getArchetypes().get(j).equalsIgnoreCase(archetypesList.get(i))) {
                        indexes.add(i);
                        added = true;
                    }
                }
                if (!added) {
                    if (card.getName_EN() != null) {
                        if (card.getName_EN().toLowerCase().contains(archetypesList.get(i).toLowerCase())) {
                            indexes.add(i);
                        }
                    }
                }
            }
        }
        return indexes;
    }
}
