package Model.FormatList;

import Model.CardsLists.Deck;
import Model.CardsLists.ThemeCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static Model.FormatList.HtmlGenerator.*;

public class ThemeCollectionToHtml {

    private static final Logger logger = LoggerFactory.getLogger(ThemeCollectionToHtml.class);

    /**
     * Generates an HTML file displaying a ThemeCollection as a card list,
     * with each linked deck unit in its own section followed by the collection's
     * own card list.
     *
     * @param themeCollection the collection to display
     * @param dirPath         the path of the output directory
     * @throws IOException if an I/O error occurs
     */
    public static void generateThemeCollectionAsListHtml(ThemeCollection themeCollection, String dirPath) throws IOException {
        String sanitizedName = sanitizeCollectionName(themeCollection.getName());
        String filePath = dirPath + sanitizedName + " - List.html";
        createHtmlFile(filePath);
        String relativeImagePath = ".." + java.io.File.separator + "Images" + java.io.File.separator;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            addHeader(writer, themeCollection.getName(), relativeImagePath, dirPath);
            addTitle(writer, themeCollection.getName(), themeCollection.getCardCount(), themeCollection.getPrice());

            for (List<Deck> deckUnit : themeCollection.getLinkedDecks()) {
                addRectangleBeginning(writer);
                for (Deck deck : deckUnit) {
                    addRectangleBeginning(writer);
                    addTitle3(writer, deck.getName(), deck.getCardCount(), deck.getPrice());
                    displayList(deck.getMainDeck(), "Main deck", writer, dirPath, relativeImagePath);
                    displayList(deck.getExtraDeck(), "Extra deck", writer, dirPath, relativeImagePath);
                    displayList(deck.getSideDeck(), "Side deck", writer, dirPath, relativeImagePath);
                    addRectangleEnd(writer);
                }
                addRectangleEnd(writer);
            }

            addRectangleBeginning(writer);
            displayList(themeCollection.getCardsList(), "Collection", writer, dirPath, relativeImagePath);
            addRectangleEnd(writer);

            addFooter(writer);
        } catch (IOException e) {
            logger.error("Failed to write list HTML for collection '{}' to '{}'", themeCollection.getName(), filePath, e);
            throw e;
        }
    }

    /**
     * Generates an HTML file displaying a ThemeCollection as a card mosaic,
     * with each linked deck unit in its own section followed by the collection's
     * own card list.
     *
     * @param themeCollection the collection to display
     * @param dirPath         the path of the output directory
     * @throws IOException if an I/O error occurs
     */
    public static void generateThemeCollectionAsMosaicHtml(ThemeCollection themeCollection, String dirPath) throws IOException {
        String sanitizedName = sanitizeCollectionName(themeCollection.getName());
        String filePath = dirPath + sanitizedName + " - Mosaic.html";
        createHtmlFile(filePath);
        String relativeImagePath = ".." + java.io.File.separator + "Images" + java.io.File.separator;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            addHeader(writer, themeCollection.getName(), relativeImagePath, dirPath);
            addTitle(writer, themeCollection.getName(), themeCollection.getCardCount(), themeCollection.getPrice());

            for (List<Deck> deckUnit : themeCollection.getLinkedDecks()) {
                addRectangleBeginning(writer);
                for (Deck deck : deckUnit) {
                    addRectangleBeginning(writer);
                    addTitle3(writer, deck.getName(), deck.getCardCount(), deck.getPrice());
                    displayMosaic(deck.getMainDeck(), "Main deck", writer, dirPath, relativeImagePath);
                    displayMosaic(deck.getExtraDeck(), "Extra deck", writer, dirPath, relativeImagePath);
                    displayMosaic(deck.getSideDeck(), "Side deck", writer, dirPath, relativeImagePath);
                    addRectangleEnd(writer);
                }
                addRectangleEnd(writer);
            }

            addRectangleBeginning(writer);
            displayMosaic(themeCollection.getCardsList(), "Collection", writer, dirPath, relativeImagePath);
            addRectangleEnd(writer);

            addFooter(writer);
        } catch (IOException e) {
            logger.error("Failed to write mosaic HTML for collection '{}' to '{}'", themeCollection.getName(), filePath, e);
            throw e;
        }
    }

    /**
     * Strips characters illegal in file names from a collection name.
     */
    private static String sanitizeCollectionName(String name) {
        return name.replace("\\", "-").replace("/", "-").replace("\"", "");
    }
}