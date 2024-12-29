package Model.FormatList;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;

import static Model.FormatList.HtmlGenerator.*;

public class CardListToHtml {
    public static void generateHtml(List<CardElement> cards, String dirPath, String outputFileName) throws IOException {
        outputFileName = outputFileName.replace("\\", "-").replace("/", "-").replace("\"", "");
        String filePath = dirPath + outputFileName + ".html";
        createHtmlFile(filePath);
        String relativeImagePath = "..\\Images\\";
        String imagesDirPath = dirPath + relativeImagePath;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            addHeader(writer, outputFileName, relativeImagePath, dirPath);

            addTitle(writer, outputFileName, cards.size(), getPriceCardElement(cards));
            addLinkButtons(writer);

            Map<Card, Integer> cardCount = createCardsMap(cards);

            for (Map.Entry<Card, Integer> entry : cardCount.entrySet()) {
                writeCardElement(writer, entry.getKey(), entry.getValue(), false, imagesDirPath, relativeImagePath, dirPath);
            }

            addFooter(writer);

            //If Menu.html doesn't exist, create it using generateMenu method
            if(!Files.exists(Paths.get(dirPath + "..\\Menu.html"))) {
                generateMenu(dirPath + "..\\");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void generateMenu(String dirPath) throws IOException {
        String outputFileName = "Menu";
        String filePath = dirPath + outputFileName + ".html";
        createHtmlFile(filePath);
        String relativeImagePath = "Images\\";
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            addHeader(writer, outputFileName, relativeImagePath, dirPath);
            addTitle(writer, outputFileName);

            writer.write(
                    "<ul>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\Collection - Mosaic.html\">Collection - Mosaic</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\Collection Complete List.html\">Collection</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\Available Cards.html\">Available Cards List</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\Decks List.html\">Decks List</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Decks\\Decks Menu.html\">Decks Menu</a></li>\n" +
                            "</ul>\n" +
                            "<ul>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\Detailed OuicheList - Mosaic.html\">Complete List - Mosaic</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\OuicheList.html\">Complete List</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\MonsterList.html\">Monster List</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\SpellList.html\">Spell List</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\TrapList.html\">Trap List</a></li>\n" +
                            "</ul>\n" +
                            "<h2>Monster types</h2>\n" +
                            "<ul>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\PyroTypeMonster.html\">Pyro</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\AquaTypeMonster.html\">Aqua</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\MachineTypeMonster.html\">Machine</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\DragonTypeMonster.html\">Dragon</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\BeastWarriorTypeMonster.html\">BeastWarrior</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\ReptileTypeMonster.html\">Reptile</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\PlantTypeMonster.html\">Plant</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\FiendTypeMonster.html\">Fiend</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\WyrmTypeMonster.html\">Wyrm</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\DinosaurTypeMonster.html\">Dinosaur</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\SpellcasterTypeMonster.html\">Spellcaster</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\FishTypeMonster.html\">Fish</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\DivineBeastTypeMonster.html\">DivineBeast</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\CyberseTypeMonster.html\">Cyberse</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\InsectTypeMonster.html\">Insect</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\WingedBeastTypeMonster.html\">WingedBeast</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\WarriorTypeMonster.html\">Warrior</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\RockTypeMonster.html\">Rock</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\ThunderTypeMonster.html\">Thunder</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\ZombieTypeMonster.html\">Zombie</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\SeaSerpentTypeMonster.html\">SeaSerpent</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\BeastTypeMonster.html\">Beast</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\PsychicTypeMonster.html\">Psychic</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\FairyTypeMonster.html\">Fairy</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\IllusionTypeMonster.html\">Illusion</a></li>\n" +
                            "</ul>\n" +
                            "<h2>Monster cardtypes</h2>\n" +
                            "<ul>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\NormalMonsterCard.html\">Normal</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\ToonMonsterCard.html\">Toon</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\TunerMonsterCard.html\">Tuner</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\UnionMonsterCard.html\">Union</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\SynchroMonsterCard.html\">Synchro</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\PendulumMonsterCard.html\">Pendulum</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\RitualMonsterCard.html\">Ritual</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\FlipMonsterCard.html\">Flip</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\SpiritMonsterCard.html\">Spirit</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\XyzMonsterCard.html\">Xyz</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\EffectMonsterCard.html\">Effect</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\FusionMonsterCard.html\">Fusion</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\LinkMonsterCard.html\">Link</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\GeminiMonsterCard.html\">Gemini</a></li>\n" +
                            "</ul>\n" +
                            "<h2>Spell cardtypes</h2>\n" +
                            "<ul>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\NormalSpellCard.html\">Normal</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\ContinuousSpellCard.html\">Continuous</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\QuickPlaySpellCard.html\">QuickPlay</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\EquipSpellCard.html\">Equip</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\FieldSpellCard.html\">Field</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\RitualSpellCard.html\">Ritual</a></li>\n" +
                            "</ul>\n" +
                            "<h2>Trap cardtypes</h2>\n" +
                            "<ul>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\NormalTrapCard.html\">Normal</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\ContinuousTrapCard.html\">Continuous</a></li>\n" +
                            "    <li><a class=\"menu-link\" href=\"Lists\\CounterTrapCard.html\">Counter</a></li>\n" +
                            "</ul>\n");

            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void generateHtmlWithOwned(List<CardElement> cards, String dirPath, String outputFileName, Boolean printOwned) throws IOException {
        outputFileName = outputFileName.replace("\\", "-").replace("/", "-").replace("\"", "");
        String filePath = dirPath + outputFileName + ".html";
        createHtmlFile(filePath);
        String relativeImagePath = "..\\Images\\";
        String imagesDirPath = dirPath + relativeImagePath;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            addHeader(writer, outputFileName, relativeImagePath, dirPath);
            addTitle(writer, outputFileName, cards.size(), getPriceCardElement(cards));
            addLinkButtons(writer);

            Map<CardElement, Integer>[] cardCount = createCardsMapWithNotOwned(cards);
            Map<CardElement, Integer> cardCountWithO = cardCount[0];
            Map<CardElement, Integer> cardCountWithoutO = cardCount[1];

            for (Map.Entry<CardElement, Integer> entry : cardCountWithoutO.entrySet()) {
                writeCardElement(writer, entry.getKey(), entry.getValue(), imagesDirPath, relativeImagePath, dirPath);
            }

            if(printOwned == true) {
                for (Map.Entry<CardElement, Integer> entry : cardCountWithO.entrySet()) {
                    writeCardElement(writer, entry.getKey(), entry.getValue(), imagesDirPath, relativeImagePath, dirPath);
                }
            }

            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}