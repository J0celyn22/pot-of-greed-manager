package Model.FormatList;

import Model.CardsLists.Deck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static Model.FormatList.HtmlGenerator.*;

public class DeckToHtml {

    private static final Logger logger = LoggerFactory.getLogger(DeckToHtml.class);

    /**
     * Generates an HTML file displaying a deck as a card list.
     *
     * @param deck      the deck to display
     * @param dirPath   the path of the output directory
     * @param decksList the full list of decks, used to generate navigation links
     * @throws IOException if an I/O error occurs
     */
    public static void generateDeckAsListHtml(Deck deck, String dirPath, List<Deck> decksList) throws IOException {
        String sanitizedName = sanitizeDeckName(deck.getName());
        String filePath = dirPath + sanitizedName + " - List.html";
        createHtmlFile(filePath);
        String relativeImagePath = ".." + java.io.File.separator + "Images" + java.io.File.separator;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            addHeader(writer, deck.getName(), relativeImagePath, dirPath);
            addTitle(writer, deck.getName(), deck.getCardCount(), deck.getPrice());
            addMosaicButton(writer, deck.getName());

            displayList(deck.getMainDeck(), "Main deck", writer, dirPath, relativeImagePath);
            displayList(deck.getExtraDeck(), "Extra deck", writer, dirPath, relativeImagePath);
            displayList(deck.getSideDeck(), "Side deck", writer, dirPath, relativeImagePath);

            addDeckLinkButtons(writer, decksList, "List");
            addFooter(writer);
        } catch (IOException e) {
            logger.error("Failed to write list HTML for deck '{}' to '{}'", deck.getName(), filePath, e);
            throw e;
        }
    }

    /**
     * Generates an HTML file displaying a deck as a card mosaic.
     *
     * @param deck      the deck to display
     * @param dirPath   the path of the output directory
     * @param decksList the full list of decks, used to generate navigation links
     * @throws IOException if an I/O error occurs
     */
    public static void generateDeckAsMosaicHtml(Deck deck, String dirPath, List<Deck> decksList) throws IOException {
        String sanitizedName = sanitizeDeckName(deck.getName());
        String filePath = dirPath + sanitizedName + " - Mosaic.html";
        createHtmlFile(filePath);
        String relativeImagePath = ".." + java.io.File.separator + "Images" + java.io.File.separator;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            addHeader(writer, deck.getName(), relativeImagePath, dirPath);
            addTitle(writer, deck.getName(), deck.getCardCount(), deck.getPrice());
            addListButton(writer, deck.getName());

            displayMosaic(deck.getMainDeck(), "Main deck", writer, dirPath, relativeImagePath);
            displayMosaic(deck.getExtraDeck(), "Extra deck", writer, dirPath, relativeImagePath);
            displayMosaic(deck.getSideDeck(), "Side deck", writer, dirPath, relativeImagePath);

            addDeckLinkButtons(writer, decksList, "Mosaic");
            addFooter(writer);
        } catch (IOException e) {
            logger.error("Failed to write mosaic HTML for deck '{}' to '{}'", deck.getName(), filePath, e);
            throw e;
        }
    }

    /**
     * Generates an HTML menu file listing all available decks.
     *
     * @param dirPath   the path of the output directory
     * @param decksList the full list of decks
     * @throws IOException if an I/O error occurs
     */
    public static void generateDecksMenu(String dirPath, List<Deck> decksList) throws IOException {
        String filePath = dirPath + "Decks Menu.html";
        createHtmlFile(filePath);
        String relativeImagePath = ".." + java.io.File.separator + "Images" + java.io.File.separator;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            addHeader(writer, "Decks", relativeImagePath, dirPath);
            addTitle(writer, "Decks");
            addDeckLinkButtons(writer, decksList, "Mosaic");
            addLinkButtons(writer);
            addFooter(writer);
        } catch (IOException e) {
            logger.error("Failed to write decks menu HTML to '{}'", filePath, e);
            throw e;
        }
    }

    /**
     * Strips characters illegal in file names from a deck name.
     */
    private static String sanitizeDeckName(String name) {
        return name.replace("\\", "-").replace("/", "-").replace("\"", "");
    }
}