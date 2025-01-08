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
        for (int i = 0; i < archetypesList.size(); i++) {
            if (i == 0) {
                generateHtml(dirPath, archetypesCardsLists.get(i), archetypesList.get(i), "", archetypesList.get(i + 1));
            } else if (i == archetypesList.size() - 1) {
                generateHtml(dirPath, archetypesCardsLists.get(i), archetypesList.get(i), archetypesList.get(i - 1), "");
            } else {
                generateHtml(dirPath, archetypesCardsLists.get(i), archetypesList.get(i), archetypesList.get(i - 1), archetypesList.get(i + 1));
            }
        }
    }

    /**
     * Generates an HTML file for a given list of cards, including navigation links.
     *
     * <p>This method creates an HTML file for the specified list of cards and saves it in the given
     * directory path. The file includes navigation links to the previous and next HTML files, if
     * applicable. The cards are displayed in an unordered list with their respective counts.</p>
     *
     * @param dirPath          The directory path where the HTML file will be saved.
     * @param cards            A list of Card objects to be included in the HTML file.
     * @param fileName         The name of the file, which will be sanitized and used as the HTML file name.
     * @param previousFileName The name of the previous file for navigation, or an empty string if none.
     * @param nextFileName     The name of the next file for navigation, or an empty string if none.
     * @throws IOException If an I/O error occurs during the file creation or writing process.
     */
    public static void generateHtml(String dirPath, List<Card> cards, String fileName, String previousFileName, String nextFileName) throws IOException {
        fileName = fileName.replace("\\", "-").replace("/", "-").replace("\"", "");
        String filePath = dirPath + fileName + ".html";
        createHtmlFile(filePath);
        String relativeImagePath = "..\\Images\\";
        String imagesDirPath = dirPath + relativeImagePath;
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            addHeader(writer, fileName, relativeImagePath, dirPath);
            addTitle(writer, fileName);

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

            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}