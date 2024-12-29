package Model.FormatList;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.CardsLists.Deck;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static Model.FormatList.HtmlGenerator.*;

public class DeckToHtml {
    /**
     * Generate an HTML file displaying a deck, as a list
     * @param deck The Deck to display
     * @param dirPath The path of the output file
     * @throws IOException
     */
    public static void generateDeckAsListHtml(Deck deck, String dirPath, List<Deck> decksList) throws IOException {
        String filePath = dirPath + deck.getName().replace("\\", "-").replace("/", "-").replace("\"", "") + " - List.html";
        createHtmlFile(filePath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            String relativeImagePath = "..\\Images\\";
            addHeader(writer, deck.getName(), relativeImagePath, dirPath);
            addTitle(writer, deck.getName(), deck.getCardCount(), deck.getPrice());
            addMosaicButton(writer, deck.getName());

            displayList(deck.getMainDeck(), "Main deck", writer, dirPath, relativeImagePath);
            displayList(deck.getExtraDeck(), "Extra deck", writer, dirPath, relativeImagePath);
            displayList(deck.getSideDeck(), "Side deck", writer, dirPath, relativeImagePath);

            addDeckLinkButtons(writer, decksList, "List");
            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generate an HTML file displaying a deck, as a mosaic
     * @param deck The Deck to display
     * @param dirPath The path of the output file
     * @throws IOException
     */
    public static void generateDeckAsMosaicHtml(Deck deck, String dirPath, List<Deck> decksList) throws IOException {
        String filePath = dirPath + deck.getName().replace("\\", "-").replace("/", "-").replace("\"", "") + " - Mosaic.html";
        createHtmlFile(filePath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            String relativeImagePath = "..\\Images\\";
            addHeader(writer, deck.getName(), relativeImagePath, dirPath);
            addTitle(writer, deck.getName(), deck.getCardCount(), deck.getPrice());
            addListButton(writer, deck.getName());

            displayMosaic(deck.getMainDeck(), "Main deck", writer, dirPath, relativeImagePath);
            displayMosaic(deck.getExtraDeck(), "Extra deck", writer, dirPath, relativeImagePath);
            displayMosaic(deck.getSideDeck(), "Side deck", writer, dirPath, relativeImagePath);

            addDeckLinkButtons(writer, decksList, "Mosaic");
            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void generateDecksMenu(String dirPath, List<Deck> decksList) throws IOException {
        String filePath = dirPath + "Decks Menu.html";
        createHtmlFile(filePath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            String relativeImagePath = "..\\Images\\";
            addHeader(writer, "Decks", relativeImagePath, dirPath);
            addTitle(writer, "Decks");

            addDeckLinkButtons(writer, decksList, "Mosaic");

            addLinkButtons(writer);
            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}