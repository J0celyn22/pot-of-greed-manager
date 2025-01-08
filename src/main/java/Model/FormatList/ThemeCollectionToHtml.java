package Model.FormatList;

import Model.CardsLists.ThemeCollection;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

import static Model.FormatList.HtmlGenerator.*;

public class ThemeCollectionToHtml {
    /**
     * Generate an HTML file displaying a ThemeCollection, as a list
     *
     * @param themeCollection The ThemeCollection to display
     * @param dirPath         The path of the output file
     * @throws IOException
     */
    public static void generateThemeCollectionAsListHtml(ThemeCollection themeCollection, String dirPath) throws IOException {
        String filePath = dirPath + themeCollection.getName().replace("\\", "-").replace("/", "-").replace("\"", "") + " - List.html";
        createHtmlFile(filePath);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            String relativeImagePath = "..\\Images\\";
            addHeader(writer, themeCollection.getName(), relativeImagePath, dirPath);
            addTitle(writer, themeCollection.getName(), themeCollection.getCardCount(), themeCollection.getPrice());

            for (int i = 0; i < themeCollection.getLinkedDecks().size(); i++) {
                addRectangleBeginning(writer);
                addTitle3(writer, themeCollection.getLinkedDecks().get(i).getName(), themeCollection.getLinkedDecks().get(i).getCardCount(), themeCollection.getLinkedDecks().get(i).getPrice());
                displayList(themeCollection.getLinkedDecks().get(i).getMainDeck(), "Main.Main deck", writer, dirPath, relativeImagePath);
                displayList(themeCollection.getLinkedDecks().get(i).getExtraDeck(), "Extra deck", writer, dirPath, relativeImagePath);
                displayList(themeCollection.getLinkedDecks().get(i).getSideDeck(), "Side deck", writer, dirPath, relativeImagePath);
                addRectangleEnd(writer);
            }

            addRectangleBeginning(writer);
            displayList(themeCollection.getCardsList(), "Collection", writer, dirPath, relativeImagePath);
            addRectangleEnd(writer);

            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generate an HTML file displaying a ThemeCollection, as a mosaic
     *
     * @param themeCollection The Deck to display
     * @param dirPath         The path of the output file
     * @throws IOException
     */
    public static void generateThemeCollectionAsMosaicHtml(ThemeCollection themeCollection, String dirPath) throws IOException {
        String filePath = dirPath + themeCollection.getName().replace("\\", "-").replace("/", "-").replace("\"", "") + " - Mosaic.html";
        createHtmlFile(filePath);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            String relativeImagePath = "..\\Images\\";
            addHeader(writer, themeCollection.getName(), relativeImagePath, dirPath);
            addTitle(writer, themeCollection.getName(), themeCollection.getCardCount(), themeCollection.getPrice());

            for (int i = 0; i < themeCollection.getLinkedDecks().size(); i++) {
                addRectangleBeginning(writer);
                addTitle3(writer, themeCollection.getLinkedDecks().get(i).getName(), themeCollection.getLinkedDecks().get(i).getCardCount(), themeCollection.getLinkedDecks().get(i).getPrice());

                displayMosaic(themeCollection.getLinkedDecks().get(i).getMainDeck(), "Main.Main deck", writer, dirPath, relativeImagePath);
                displayMosaic(themeCollection.getLinkedDecks().get(i).getExtraDeck(), "Extra deck", writer, dirPath, relativeImagePath);
                displayMosaic(themeCollection.getLinkedDecks().get(i).getSideDeck(), "Side deck", writer, dirPath, relativeImagePath);
                addRectangleEnd(writer);
            }

            addRectangleBeginning(writer);
            displayMosaic(themeCollection.getCardsList(), "Collection", writer, dirPath, relativeImagePath);
            addRectangleEnd(writer);

            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}