package Model.CardsLists;

import Model.Database.Database;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SubListCreator {
    private static List<CardElement> monsterList = new ArrayList<>();
    private static List<CardElement> spellList = new ArrayList<>();
    private static List<CardElement> trapList = new ArrayList<>();

    private static List<CardElement> pyroTypeMonster = new ArrayList<>();
    private static List<CardElement> aquaTypeMonster = new ArrayList<>();
    private static List<CardElement> machineTypeMonster = new ArrayList<>();
    private static List<CardElement> dragonTypeMonster = new ArrayList<>();
    private static List<CardElement> beastWarriorTypeMonster = new ArrayList<>();
    private static List<CardElement> reptileTypeMonster = new ArrayList<>();
    private static List<CardElement> plantTypeMonster = new ArrayList<>();
    private static List<CardElement> fiendTypeMonster = new ArrayList<>();
    private static List<CardElement> wyrmTypeMonster = new ArrayList<>();
    private static List<CardElement> dinosaurTypeMonster = new ArrayList<>();
    private static List<CardElement> spellcasterTypeMonster = new ArrayList<>();
    private static List<CardElement> fishTypeMonster = new ArrayList<>();
    private static List<CardElement> divineBeastTypeMonster = new ArrayList<>();
    private static List<CardElement> cyberseTypeMonster = new ArrayList<>();
    private static List<CardElement> insectTypeMonster = new ArrayList<>();
    private static List<CardElement> wingedBeastTypeMonster = new ArrayList<>();
    private static List<CardElement> warriorTypeMonster = new ArrayList<>();
    private static List<CardElement> rockTypeMonster = new ArrayList<>();
    private static List<CardElement> thunderTypeMonster = new ArrayList<>();
    private static List<CardElement> zombieTypeMonster = new ArrayList<>();
    private static List<CardElement> seaSerpentTypeMonster = new ArrayList<>();
    private static List<CardElement> beastTypeMonster = new ArrayList<>();
    private static List<CardElement> psychicTypeMonster = new ArrayList<>();
    private static List<CardElement> fairyTypeMonster = new ArrayList<>();
    private static List<CardElement> illusionTypeMonster = new ArrayList<>();
    private static List<CardElement> normalMonsterCard = new ArrayList<>();
    private static List<CardElement> toonMonsterCard = new ArrayList<>();
    private static List<CardElement> tunerMonsterCard = new ArrayList<>();
    private static List<CardElement> unionMonsterCard = new ArrayList<>();
    private static List<CardElement> synchroMonsterCard = new ArrayList<>();
    private static List<CardElement> pendulumMonsterCard = new ArrayList<>();
    private static List<CardElement> ritualMonsterCard = new ArrayList<>();
    private static List<CardElement> flipMonsterCard = new ArrayList<>();
    private static List<CardElement> spiritMonsterCard = new ArrayList<>();
    private static List<CardElement> xyzMonsterCard = new ArrayList<>();
    private static List<CardElement> effectMonsterCard = new ArrayList<>();
    private static List<CardElement> fusionMonsterCard = new ArrayList<>();
    private static List<CardElement> linkMonsterCard = new ArrayList<>();
    private static List<CardElement> geminiMonsterCard = new ArrayList<>();
    private static List<CardElement> normalSpellCard = new ArrayList<>();
    private static List<CardElement> continuousSpellCard = new ArrayList<>();
    private static List<CardElement> quickPlaySpellCard = new ArrayList<>();
    private static List<CardElement> equipSpellCard = new ArrayList<>();
    private static List<CardElement> fieldSpellCard = new ArrayList<>();
    private static List<CardElement> ritualSpellCard = new ArrayList<>();
    private static List<CardElement> normalTrapCard = new ArrayList<>();
    private static List<CardElement> continuousTrapCard = new ArrayList<>();
    private static List<CardElement> counterTrapCard = new ArrayList<>();

    private static List<List<Card>> archetypesCardsLists = new ArrayList<>();
    private static List<String> archetypesList = new ArrayList<>();


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

    /**
     * After CreateArchetypeLists() has been called, iterates every archetype
     * and adds its name to the archetypes list of every card that belongs to it.
     * <p>
     * This ensures card.getArchetypes() always reflects ALL archetypes a card
     * participates in (both the primary one returned by the API and any secondary
     * ones discovered via name-matching), fixing the bug where a card was missing
     * from its main archetype because the API only reported a secondary one.
     */
    public static void UpdateCardArchetypes() {
        if (archetypesList == null || archetypesCardsLists == null) return;

        for (int i = 0; i < archetypesList.size(); i++) {
            String archetypeName = archetypesList.get(i);
            if (archetypeName == null) continue;
            if (i >= archetypesCardsLists.size()) continue;

            List<Card> cards = archetypesCardsLists.get(i);
            if (cards == null) continue;

            for (Card card : cards) {
                if (card == null) continue;

                List<String> existing = card.getArchetypes();
                if (existing == null) {
                    existing = new ArrayList<>();
                    card.setArchetypes(existing);
                }

                // Case-insensitive duplicate check
                boolean alreadyPresent = false;
                for (String s : existing) {
                    if (archetypeName.equalsIgnoreCase(s)) {
                        alreadyPresent = true;
                        break;
                    }
                }
                if (!alreadyPresent) {
                    existing.add(archetypeName);
                }
            }
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public static List<CardElement> getMonsterList() {
        return monsterList;
    }

    public static List<CardElement> getSpellList() {
        return spellList;
    }

    public static List<CardElement> getTrapList() {
        return trapList;
    }

    public static List<CardElement> getPyroTypeMonster() {
        return pyroTypeMonster;
    }

    public static List<CardElement> getAquaTypeMonster() {
        return aquaTypeMonster;
    }

    public static List<CardElement> getMachineTypeMonster() {
        return machineTypeMonster;
    }

    public static List<CardElement> getDragonTypeMonster() {
        return dragonTypeMonster;
    }

    public static List<CardElement> getBeastWarriorTypeMonster() {
        return beastWarriorTypeMonster;
    }

    public static List<CardElement> getReptileTypeMonster() {
        return reptileTypeMonster;
    }

    public static List<CardElement> getPlantTypeMonster() {
        return plantTypeMonster;
    }

    public static List<CardElement> getFiendTypeMonster() {
        return fiendTypeMonster;
    }

    public static List<CardElement> getWyrmTypeMonster() {
        return wyrmTypeMonster;
    }

    public static List<CardElement> getDinosaurTypeMonster() {
        return dinosaurTypeMonster;
    }

    public static List<CardElement> getSpellcasterTypeMonster() {
        return spellcasterTypeMonster;
    }

    public static List<CardElement> getFishTypeMonster() {
        return fishTypeMonster;
    }

    public static List<CardElement> getDivineBeastTypeMonster() {
        return divineBeastTypeMonster;
    }

    public static List<CardElement> getCyberseTypeMonster() {
        return cyberseTypeMonster;
    }

    public static List<CardElement> getInsectTypeMonster() {
        return insectTypeMonster;
    }

    public static List<CardElement> getWingedBeastTypeMonster() {
        return wingedBeastTypeMonster;
    }

    public static List<CardElement> getWarriorTypeMonster() {
        return warriorTypeMonster;
    }

    public static List<CardElement> getRockTypeMonster() {
        return rockTypeMonster;
    }

    public static List<CardElement> getThunderTypeMonster() {
        return thunderTypeMonster;
    }

    public static List<CardElement> getZombieTypeMonster() {
        return zombieTypeMonster;
    }

    public static List<CardElement> getSeaSerpentTypeMonster() {
        return seaSerpentTypeMonster;
    }

    public static List<CardElement> getBeastTypeMonster() {
        return beastTypeMonster;
    }

    public static List<CardElement> getPsychicTypeMonster() {
        return psychicTypeMonster;
    }

    public static List<CardElement> getFairyTypeMonster() {
        return fairyTypeMonster;
    }

    public static List<CardElement> getIllusionTypeMonster() {
        return illusionTypeMonster;
    }

    public static List<CardElement> getNormalMonsterCard() {
        return normalMonsterCard;
    }

    public static List<CardElement> getToonMonsterCard() {
        return toonMonsterCard;
    }

    public static List<CardElement> getTunerMonsterCard() {
        return tunerMonsterCard;
    }

    public static List<CardElement> getUnionMonsterCard() {
        return unionMonsterCard;
    }

    public static List<CardElement> getSynchroMonsterCard() {
        return synchroMonsterCard;
    }

    public static List<CardElement> getPendulumMonsterCard() {
        return pendulumMonsterCard;
    }

    public static List<CardElement> getRitualMonsterCard() {
        return ritualMonsterCard;
    }

    public static List<CardElement> getFlipMonsterCard() {
        return flipMonsterCard;
    }

    public static List<CardElement> getSpiritMonsterCard() {
        return spiritMonsterCard;
    }

    public static List<CardElement> getXyzMonsterCard() {
        return xyzMonsterCard;
    }

    public static List<CardElement> getEffectMonsterCard() {
        return effectMonsterCard;
    }

    public static List<CardElement> getFusionMonsterCard() {
        return fusionMonsterCard;
    }

    public static List<CardElement> getLinkMonsterCard() {
        return linkMonsterCard;
    }

    public static List<CardElement> getGeminiMonsterCard() {
        return geminiMonsterCard;
    }

    public static List<CardElement> getNormalSpellCard() {
        return normalSpellCard;
    }

    public static List<CardElement> getContinuousSpellCard() {
        return continuousSpellCard;
    }

    public static List<CardElement> getQuickPlaySpellCard() {
        return quickPlaySpellCard;
    }

    public static List<CardElement> getEquipSpellCard() {
        return equipSpellCard;
    }

    public static List<CardElement> getFieldSpellCard() {
        return fieldSpellCard;
    }

    public static List<CardElement> getRitualSpellCard() {
        return ritualSpellCard;
    }

    public static List<CardElement> getNormalTrapCard() {
        return normalTrapCard;
    }

    public static List<CardElement> getContinuousTrapCard() {
        return continuousTrapCard;
    }

    public static List<CardElement> getCounterTrapCard() {
        return counterTrapCard;
    }

    public static List<List<Card>> getArchetypesCardsLists() {
        return archetypesCardsLists;
    }

    public static List<String> getArchetypesList() {
        return archetypesList;
    }

}