package Model.FormatList;

import Model.CardsLists.Card;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static Model.FormatList.HtmlGenerator.*;

public class ArchetypesListsToHtml {

    /**
     * Generates HTML files for all archetypes in the provided lists.
     * <p>
     * This method iterates over the provided list of archetype names and their corresponding card lists,
     * generating an HTML file for each archetype. The files are saved in the specified directory path.
     * Each HTML file includes navigation links to the previous and next archetypes in the list.
     * </p>
     *
     * @param dirPath              The directory path where the generated HTML files will be saved.
     * @param archetypesList       A list of archetype names for which HTML files will be generated.
     * @param archetypesCardsLists A list of lists, where each inner list contains the cards belonging to an archetype.
     * @throws IOException If an I/O error occurs during the HTML file generation.
     */
    public static void GenerateAllArchetypesLists(String dirPath, List<String> archetypesList, List<List<Card>> archetypesCardsLists) throws IOException {
        // Create the Archetypes menu file first
        generateArchetypesMenu(dirPath, archetypesList);

        for (int i = 0; i < archetypesList.size(); i++) {
            if (i == 0) {
                generateHtml(dirPath, archetypesCardsLists.get(i), archetypesList.get(i), "", archetypesList.get(i + 1), archetypesList);
            } else if (i == archetypesList.size() - 1) {
                generateHtml(dirPath, archetypesCardsLists.get(i), archetypesList.get(i), archetypesList.get(i - 1), "", archetypesList);
            } else {
                generateHtml(dirPath, archetypesCardsLists.get(i), archetypesList.get(i), archetypesList.get(i - 1), archetypesList.get(i + 1), archetypesList);
            }
        }
    }

    /**
     * Generates an HTML file for a given list of cards, including navigation links and the archetypes menu.
     * <p>This method creates an HTML file for the specified list of cards and saves it in the given
     * directory path. The file includes navigation links to the previous and next HTML files, if
     * applicable. The cards are displayed in an unordered list with their respective counts.</p>
     *
     * @param dirPath              The directory path where the HTML file will be saved.
     * @param cards                A list of Card objects to be included in the HTML file.
     * @param fileName             The name of the file, which will be sanitized and used as the HTML file name.
     * @param previousFileName     The name of the previous file for navigation, or an empty string if none.
     * @param nextFileName         The name of the next file for navigation, or an empty string if none.
     * @param allArchetypesNames   The list of all archetype names (used to build the archetypes menu).
     * @throws IOException If an I/O error occurs during the file creation or writing process.
     */
    public static void generateHtml(String dirPath, List<Card> cards, String fileName, String previousFileName, String nextFileName, List<String> allArchetypesNames) throws IOException {
        fileName = fileName.replace("\\", "-").replace("/", "-").replace("\"", "");
        String filePath = dirPath + fileName + ".html";
        createHtmlFile(filePath);
        String relativeImagePath = "..\\Images\\";
        String imagesDirPath = dirPath + relativeImagePath;
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            addHeader(writer, fileName, relativeImagePath, dirPath);
            addTitle(writer, fileName);

            // Previous / Next navigation (kept for linear navigation)
            writer.write("<ul>\n");
            if (!previousFileName.isEmpty()) {
                writer.write("\t<li><a class=\"menu-link\" href=\"" + previousFileName.replace("\"", "") + ".html\">" + previousFileName + "</a></li>\n");
            }

            if (!nextFileName.isEmpty()) {
                writer.write("\t<li><a class=\"menu-link\" href=\"" + nextFileName.replace("\"", "") + ".html\">" + nextFileName + "</a></li>\n");
            }
            writer.write("</ul>\n");


            Map<Card, Integer> cardCount = new LinkedHashMap<>();
            for (Card card : cards) {
                int count = cardCount.getOrDefault(card, 0);
                cardCount.put(card, count + 1);
            }

            for (Map.Entry<Card, Integer> entry : cardCount.entrySet()) {
                writeCardElement(writer, entry.getKey(), false, imagesDirPath, relativeImagePath);
            }

            // Add archetypes menu links (same menu on every archetype page)
            addArchetypeLinkButtons(writer, allArchetypesNames);
            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generate an Archetypes menu HTML file that lists all archetypes.
     *
     * @param dirPath        The directory path where the menu file will be saved.
     * @param archetypesList The list of archetype names to include in the menu.
     * @throws IOException If an I/O error occurs during file creation or writing.
     */
    public static void generateArchetypesMenu(String dirPath, List<String> archetypesList) throws IOException {
        String filePath = dirPath + "Archetypes Menu.html";
        createHtmlFile(filePath);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            String relativeImagePath = "..\\Images\\";
            addHeader(writer, "Archetypes", relativeImagePath, dirPath);
            addTitle(writer, "Archetypes");

            // Add links to each archetype
            addArchetypeLinkButtons(writer, archetypesList);

            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
