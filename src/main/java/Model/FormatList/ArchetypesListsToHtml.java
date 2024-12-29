package Model.FormatList;

import Model.CardsLists.Card;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static Model.FormatList.HtmlGenerator.*;

public class ArchetypesListsToHtml {

    public static void GenerateAllArchetypesLists(String dirPath, List<String> archetypesList, List<List<Card>> archetypesCardsLists) throws IOException {
        for (int i = 0; i < archetypesList.size(); i++) {
            if(i == 0) {
                generateHtml(dirPath, archetypesCardsLists.get(i), archetypesList.get(i), "", archetypesList.get(i + 1));
            }
            else if(i == archetypesList.size() - 1) {
                generateHtml(dirPath, archetypesCardsLists.get(i), archetypesList.get(i), archetypesList.get(i - 1), "");
            }
            else {
                generateHtml(dirPath, archetypesCardsLists.get(i), archetypesList.get(i), archetypesList.get(i - 1), archetypesList.get(i + 1));
            }
        }
    }

    public static void generateHtml(String dirPath, List<Card> cards, String fileName, String previousFileName, String nextFileName) throws IOException {
        fileName = fileName.replace("\\", "-").replace("/", "-").replace("\"", "");
        String filePath = dirPath + fileName + ".html";
        createHtmlFile(filePath);
        String relativeImagePath = "..\\Images\\";
        String imagesDirPath = dirPath + relativeImagePath;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            addHeader(writer, fileName, relativeImagePath, dirPath);
            addTitle(writer, fileName);

            writer.write("<ul>\n");
            if(previousFileName != "") {
                writer.write("\t<li><a class=\"menu-link\" href=\"" + previousFileName.replace("\"", "") + ".html\">" + previousFileName + "</a></li>\n");
            }

            if(nextFileName != "") {
                writer.write("\t<li><a class=\"menu-link\" href=\"" + nextFileName.replace("\"", "") + ".html\">" + nextFileName + "</a></li>\n");
            }
            writer.write("</ul>\n");


            Map<Card, Integer> cardCount = new LinkedHashMap<>();
            for (Card card : cards) {
                int count = cardCount.containsKey(card) ? cardCount.get(card) : 0;
                cardCount.put(card, count + 1);
            }

            for (Map.Entry<Card, Integer> entry : cardCount.entrySet()) {
                writeCardElement(writer, entry.getKey(), false, imagesDirPath, relativeImagePath, dirPath);
            }

            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}