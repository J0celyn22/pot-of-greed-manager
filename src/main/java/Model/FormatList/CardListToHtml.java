package Model.FormatList;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.CardsLists.OuicheList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static Model.FormatList.HtmlGenerator.*;

public class CardListToHtml {

    private static final Logger logger = LoggerFactory.getLogger(CardListToHtml.class);

    /**
     * Generates an HTML file from a list of CardElements, showing each card's name
     * and price. Includes a link to Menu.html, creating it if it does not yet exist.
     * If the output file name is "OuicheList", an extra mosaic button and a substandard
     * section are appended.
     *
     * @param cards          the list of cards to display
     * @param dirPath        the directory path where the HTML file will be created
     * @param outputFileName the base name of the output file (without extension)
     * @throws IOException if the file cannot be created or written
     */
    public static void generateHtml(List<CardElement> cards, String dirPath, String outputFileName) throws IOException {
        String sanitizedName = sanitizeFileName(outputFileName);
        String filePath = dirPath + sanitizedName + ".html";
        createHtmlFile(filePath);
        String relativeImagePath = ".." + java.io.File.separator + "Images" + java.io.File.separator;
        String imagesDirPath = dirPath + relativeImagePath;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            addHeader(writer, sanitizedName, relativeImagePath, dirPath);
            addTitle(writer, sanitizedName, cards.size(), getPriceCardElement(cards));
            addLinkButtons(writer);

            if ("OuicheList".equals(sanitizedName)) {
                addOuicheListMosaicButton(writer);
            }

            Map<Card, Integer> cardCount = createCardsMap(cards);
            for (Map.Entry<Card, Integer> entry : cardCount.entrySet()) {
                writeCardElement(writer, entry.getKey(), entry.getValue(), false, imagesDirPath, relativeImagePath);
            }

            // Substandard section: only rendered for the OuicheList output.
            // Reads directly from the static OuicheList maps — no caller change required.
            if ("OuicheList".equals(sanitizedName)) {
                LinkedHashMap<String, CardElement> substandardMap = OuicheList.getMaOuicheListSubstandard();
                LinkedHashMap<String, Integer> substandardCounts = OuicheList.getMaOuicheListSubstandardCounts();
                if (substandardMap != null && !substandardMap.isEmpty()) {
                    writer.write("<h2 style=\"color:#EB9E34;\">"
                            + "Copies to upgrade (owned but below required quality)</h2>\n");
                    for (Map.Entry<String, CardElement> entry : substandardMap.entrySet()) {
                        int count = (substandardCounts != null && substandardCounts.containsKey(entry.getKey()))
                                ? substandardCounts.get(entry.getKey()) : 1;
                        writeCardElementSubstandardList(writer, entry.getValue(), count, imagesDirPath, relativeImagePath);
                    }
                }
            }

            addFooter(writer);

            // Create Menu.html alongside the lists directory if it does not yet exist.
            String menuPath = dirPath + ".." + java.io.File.separator + "Menu.html";
            if (!Files.exists(Paths.get(menuPath))) {
                generateMenu(dirPath + ".." + java.io.File.separator);
            }
        } catch (IOException e) {
            logger.error("Failed to write HTML for card list '{}' to '{}'", sanitizedName, filePath, e);
            throw e;
        }
    }

    /**
     * Generates a flat mosaic HTML page for the OuicheList: one image per unique card
     * (deduplicated by imagePath), in a flex-wrap layout, with no counts or card-detail
     * text. Includes a substandard section at the end.
     *
     * @param cards   the list of card elements to display
     * @param dirPath the directory path where the HTML file will be created
     * @throws IOException if the file cannot be created or written
     */
    public static void generateOuicheListMosaicHtml(List<CardElement> cards, String dirPath) throws IOException {
        String outputFileName = "OuicheList - Mosaic";
        String filePath = dirPath + outputFileName + ".html";
        createHtmlFile(filePath);
        String relativeImagePath = ".." + java.io.File.separator + "Images" + java.io.File.separator;
        String imagesDirPath = dirPath + relativeImagePath;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            addHeader(writer, outputFileName, relativeImagePath, dirPath);
            addTitle(writer, outputFileName, cards.size(), getPriceCardElement(cards));
            addOuicheListButton(writer);
            addLinkButtons(writer);

            // Deduplicate by imagePath, preserving insertion order.
            Map<String, CardElement> byImagePath = new LinkedHashMap<>();
            for (CardElement cardElement : cards) {
                if (cardElement.getCard() != null && cardElement.getCard().getImagePath() != null) {
                    byImagePath.putIfAbsent(cardElement.getCard().getImagePath(), cardElement);
                }
            }
            for (CardElement cardElement : byImagePath.values()) {
                writeCardElement(writer, cardElement, imagesDirPath, relativeImagePath);
            }

            // Substandard section.
            LinkedHashMap<String, CardElement> substandardMap = OuicheList.getMaOuicheListSubstandard();
            if (substandardMap != null && !substandardMap.isEmpty()) {
                writer.write("<h2 style=\"color:#EB9E34;\">"
                        + "Copies to upgrade (owned but below required quality)</h2>\n");
                for (CardElement cardElement : substandardMap.values()) {
                    if (cardElement.getCard() != null) {
                        writeCardElementSubstandardMosaic(writer, cardElement.getCard(), imagesDirPath, relativeImagePath);
                    }
                }
            }

            addFooter(writer);
        } catch (IOException e) {
            logger.error("Failed to write OuicheList mosaic HTML to '{}'", filePath, e);
            throw e;
        }
    }

    /**
     * Generates "OuicheList.html" with two sections: missing cards first (yellow-green
     * borders), then owned-substandard cards (orange borders with required quality labels).
     *
     * @param missingCards     flat list of MISSING card elements (may be null)
     * @param substandardCards flat list of OWNED_SUBSTANDARD card elements (may be null)
     * @param dirPath          output directory path
     * @throws IOException if the file cannot be written
     */
    public static void generateOuicheListHtml(
            List<CardElement> missingCards,
            List<CardElement> substandardCards,
            String dirPath) throws IOException {

        String outputFileName = "OuicheList";
        String filePath = dirPath + outputFileName + ".html";
        createHtmlFile(filePath);
        String relativeImagePath = ".." + java.io.File.separator + "Images" + java.io.File.separator;
        String imagesDirPath = dirPath + relativeImagePath;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {

            int totalCount = (missingCards != null ? missingCards.size() : 0)
                    + (substandardCards != null ? substandardCards.size() : 0);
            String totalPrice = missingCards != null ? getPriceCardElement(missingCards) : "0.00";

            addHeader(writer, outputFileName, relativeImagePath, dirPath);
            addTitle(writer, outputFileName, totalCount, totalPrice);
            addOuicheListMosaicButton(writer);
            addLinkButtons(writer);

            if (missingCards != null && !missingCards.isEmpty()) {
                writer.write("<h2>Cards to acquire</h2>\n");
                Map<Card, Integer> missingCount = createCardsMap(missingCards);
                for (Map.Entry<Card, Integer> entry : missingCount.entrySet()) {
                    writeCardElement(writer, entry.getKey(), entry.getValue(), false, imagesDirPath, relativeImagePath);
                }
            }

            if (substandardCards != null && !substandardCards.isEmpty()) {
                writer.write("<h2 style=\"color:#EB9E34;\">"
                        + "Copies to upgrade (owned but below required quality)</h2>\n");

                // Deduplicate substandard cards by imagePath, summing counts.
                Map<CardElement, Integer> substandardCount = new LinkedHashMap<>();
                for (CardElement cardElement : substandardCards) {
                    if (cardElement.getCard() == null) {
                        continue;
                    }
                    boolean found = false;
                    for (Map.Entry<CardElement, Integer> entry : substandardCount.entrySet()) {
                        if (entry.getKey().getCard().getImagePath() != null
                                && cardElement.getCard().getImagePath() != null
                                && entry.getKey().getCard().getImagePath()
                                .equals(cardElement.getCard().getImagePath())) {
                            substandardCount.put(entry.getKey(), entry.getValue() + 1);
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        substandardCount.put(cardElement, 1);
                    }
                }
                for (Map.Entry<CardElement, Integer> entry : substandardCount.entrySet()) {
                    writeCardElementSubstandardList(writer, entry.getKey(), entry.getValue(), imagesDirPath, relativeImagePath);
                }
            }

            addFooter(writer);
        } catch (IOException e) {
            logger.error("Failed to write OuicheList HTML to '{}'", filePath, e);
            throw e;
        }
    }

    /**
     * Generates "OuicheList - Mosaic.html" with two sections: missing cards first
     * (normal images), then owned-substandard cards (red glow).
     *
     * @param missingCards     flat list of MISSING card elements (may be null)
     * @param substandardCards flat list of OWNED_SUBSTANDARD card elements (may be null)
     * @param dirPath          output directory path
     * @throws IOException if the file cannot be written
     */
    public static void generateOuicheListMosaicHtml(
            List<CardElement> missingCards,
            List<CardElement> substandardCards,
            String dirPath) throws IOException {

        String outputFileName = "OuicheList - Mosaic";
        String filePath = dirPath + outputFileName + ".html";
        createHtmlFile(filePath);
        String relativeImagePath = ".." + java.io.File.separator + "Images" + java.io.File.separator;
        String imagesDirPath = dirPath + relativeImagePath;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {

            int totalCount = (missingCards != null ? missingCards.size() : 0)
                    + (substandardCards != null ? substandardCards.size() : 0);
            String totalPrice = missingCards != null ? getPriceCardElement(missingCards) : "0.00";

            addHeader(writer, outputFileName, relativeImagePath, dirPath);
            addTitle(writer, outputFileName, totalCount, totalPrice);
            addOuicheListButton(writer);
            addLinkButtons(writer);

            if (missingCards != null && !missingCards.isEmpty()) {
                writer.write("<h2>Cards to acquire</h2>\n");
                Map<String, CardElement> byImagePath = new LinkedHashMap<>();
                for (CardElement cardElement : missingCards) {
                    if (cardElement.getCard() != null && cardElement.getCard().getImagePath() != null) {
                        byImagePath.putIfAbsent(cardElement.getCard().getImagePath(), cardElement);
                    }
                }
                for (CardElement cardElement : byImagePath.values()) {
                    writeCardElement(writer, cardElement, imagesDirPath, relativeImagePath);
                }
            }

            if (substandardCards != null && !substandardCards.isEmpty()) {
                writer.write("<h2 style=\"color:#EB9E34;\">"
                        + "Copies to upgrade (owned but below required quality)</h2>\n");
                Map<String, CardElement> byImagePath = new LinkedHashMap<>();
                for (CardElement cardElement : substandardCards) {
                    if (cardElement.getCard() != null && cardElement.getCard().getImagePath() != null) {
                        byImagePath.putIfAbsent(cardElement.getCard().getImagePath(), cardElement);
                    }
                }
                for (CardElement cardElement : byImagePath.values()) {
                    writeCardElementSubstandardMosaic(writer, cardElement.getCard(), imagesDirPath, relativeImagePath);
                }
            }

            addFooter(writer);
        } catch (IOException e) {
            logger.error("Failed to write OuicheList mosaic HTML to '{}'", filePath, e);
            throw e;
        }
    }

    /**
     * Generates a Menu.html file in the given directory, with links to all available lists.
     * Note: the href paths inside the HTML are intentionally Windows-style as they are
     * browser-relative paths within the output folder structure, not Java filesystem paths.
     *
     * @param dirPath the directory path where the menu HTML file will be created
     * @throws IOException if the file cannot be created or written
     */
    public static void generateMenu(String dirPath) throws IOException {
        String outputFileName = "Menu";
        String filePath = dirPath + outputFileName + ".html";
        createHtmlFile(filePath);
        String relativeImagePath = "Images" + java.io.File.separator;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
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
            logger.error("Failed to write Menu HTML to '{}'", filePath, e);
            throw e;
        }
    }

    /**
     * Generates an HTML file from a list of CardElements, with unowned cards displayed
     * first and optionally owned cards appended afterward.
     *
     * @param cards          the list of cards to display
     * @param dirPath        the directory path where the HTML file will be created
     * @param outputFileName the base name of the output file (without extension)
     * @param printOwned     whether to include owned cards in the output
     * @throws IOException if the file cannot be created or written
     */
    public static void generateHtmlWithOwned(List<CardElement> cards, String dirPath, String outputFileName, boolean printOwned) throws IOException {
        String sanitizedName = sanitizeFileName(outputFileName);
        String filePath = dirPath + sanitizedName + ".html";
        createHtmlFile(filePath);
        String relativeImagePath = ".." + java.io.File.separator + "Images" + java.io.File.separator;
        String imagesDirPath = dirPath + relativeImagePath;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            addHeader(writer, sanitizedName, relativeImagePath, dirPath);
            addTitle(writer, sanitizedName, cards.size(), getPriceCardElement(cards));
            addLinkButtons(writer);

            Map<CardElement, Integer>[] cardCount = createCardsMapWithNotOwned(cards);
            Map<CardElement, Integer> ownedCards = cardCount[0];
            Map<CardElement, Integer> unownedCards = cardCount[1];

            for (Map.Entry<CardElement, Integer> entry : unownedCards.entrySet()) {
                writeCardElement(writer, entry.getKey(), entry.getValue(), imagesDirPath, relativeImagePath);
            }

            if (printOwned) {
                for (Map.Entry<CardElement, Integer> entry : ownedCards.entrySet()) {
                    writeCardElement(writer, entry.getKey(), entry.getValue(), imagesDirPath, relativeImagePath);
                }
            }

            addFooter(writer);
        } catch (IOException e) {
            logger.error("Failed to write HTML with owned cards for '{}' to '{}'", sanitizedName, filePath, e);
            throw e;
        }
    }

    /**
     * Strips characters illegal in file names and trims surrounding whitespace.
     */
    private static String sanitizeFileName(String name) {
        return name.replace("\\", "-").replace("/", "-").replace("\"", "").trim();
    }
}