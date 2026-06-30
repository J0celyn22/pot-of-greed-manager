package Model.FormatList;

import Model.CardsLists.CardElement;
import Model.CardsLists.Deck;
import Model.CardsLists.DecksAndCollectionsList;
import Model.CardsLists.ThemeCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static Model.FormatList.HtmlGenerator.*;

public class OuicheListToHtml {

    private static final Logger logger = LoggerFactory.getLogger(OuicheListToHtml.class);

    /**
     * Generates an HTML file displaying the OuicheList as a detailed card list,
     * grouped by collection and deck with ownership advancement statistics.
     *
     * @param ouicheList the list to display
     * @param dirPath    the path of the output directory
     * @param fileName   the base name of the output file (without extension)
     * @throws IOException if an I/O error occurs during file creation or writing
     */
    public static void generateOuicheListAsListHtml(DecksAndCollectionsList ouicheList, String dirPath, String fileName) throws IOException {
        String sanitizedName = sanitizeFileName(fileName);
        String filePath = dirPath + sanitizedName + " - List.html";
        createHtmlFile(filePath);
        String relativeImagePath = ".." + java.io.File.separator + "Images" + java.io.File.separator;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            addHeader(writer, sanitizedName, relativeImagePath, dirPath);

            AdvancementStats ouicheListAdvancement = computeAdvancement(collectAllCards(ouicheList));
            addTitle1WithAdvancement(writer, sanitizedName, ouicheListAdvancement);
            addMosaicButton(writer, sanitizedName);
            addOuicheListButton(writer);

            if (ouicheList.getCollections() != null) {
                AdvancementStats collectionsAdvancement = computeAdvancement(collectAllCardsFromCollections(ouicheList));
                addTitle2WithAdvancement(writer, "Collections", collectionsAdvancement);

                for (ThemeCollection collection : ouicheList.getCollections()) {
                    addRectangleBeginning(writer);

                    AdvancementStats collectionAdvancement = computeAdvancement(collectAllCardsFromCollection(collection));
                    addTitle2WithAdvancement(writer, collection.getName(), collectionAdvancement);

                    addRectangleBeginning(writer);
                    displayListWithOwnershipStatus(collection.getCardsList(), "Collection", writer, dirPath, relativeImagePath);
                    addRectangleEnd(writer);

                    for (List<Deck> deckUnit : collection.getLinkedDecks()) {
                        addRectangleBeginning(writer);
                        for (Deck deck : deckUnit) {
                            addRectangleBeginning(writer);

                            AdvancementStats deckAdvancement = computeAdvancement(deck.toList());
                            addTitle3WithAdvancement(writer, deck.getName(), deckAdvancement);

                            displayListWithOwnershipStatus(deck.getMainDeck(), "Main deck", writer, dirPath, relativeImagePath);
                            displayListWithOwnershipStatus(deck.getExtraDeck(), "Extra deck", writer, dirPath, relativeImagePath);
                            displayListWithOwnershipStatus(deck.getSideDeck(), "Side deck", writer, dirPath, relativeImagePath);
                            addRectangleEnd(writer);
                        }
                        addRectangleEnd(writer);
                    }

                    addRectangleEnd(writer);
                }
            }

            if (ouicheList.getDecks() != null) {
                AdvancementStats decksAdvancement = computeAdvancement(collectAllCardsFromDecks(ouicheList));
                addTitle2WithAdvancement(writer, "Decks", decksAdvancement);

                for (Deck deck : ouicheList.getDecks()) {
                    addRectangleBeginning(writer);

                    AdvancementStats deckAdvancement = computeAdvancement(deck.toList());
                    addTitle3WithAdvancement(writer, deck.getName(), deckAdvancement);

                    displayListWithOwnershipStatus(deck.getMainDeck(), "Main deck", writer, dirPath, relativeImagePath);
                    displayListWithOwnershipStatus(deck.getExtraDeck(), "Extra deck", writer, dirPath, relativeImagePath);
                    displayListWithOwnershipStatus(deck.getSideDeck(), "Side deck", writer, dirPath, relativeImagePath);
                    addRectangleEnd(writer);
                }
            }

            addFooter(writer);
        } catch (IOException e) {
            logger.error("Failed to write OuicheList list HTML '{}' to '{}'", sanitizedName, filePath, e);
            throw e;
        }
    }

    /**
     * Generates an HTML file displaying the OuicheList as a card mosaic,
     * grouped by collection and deck with ownership advancement statistics.
     *
     * @param ouicheList the list to display
     * @param dirPath    the path of the output directory
     * @param fileName   the base name of the output file (without extension)
     * @throws IOException if an I/O error occurs during file creation or writing
     */
    public static void generateOuicheListAsMosaicHtml(DecksAndCollectionsList ouicheList, String dirPath, String fileName) throws IOException {
        String sanitizedName = sanitizeFileName(fileName);
        String filePath = dirPath + sanitizedName + " - Mosaic.html";
        createHtmlFile(filePath);
        String relativeImagePath = ".." + java.io.File.separator + "Images" + java.io.File.separator;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            addHeader(writer, sanitizedName, relativeImagePath, dirPath);

            AdvancementStats ouicheListAdvancement = computeAdvancement(collectAllCards(ouicheList));
            addTitle1WithAdvancement(writer, sanitizedName, ouicheListAdvancement);
            addListButton(writer, sanitizedName);
            addOuicheListButton(writer);

            if (ouicheList.getCollections() != null) {
                AdvancementStats collectionsAdvancement = computeAdvancement(collectAllCardsFromCollections(ouicheList));
                addTitle2WithAdvancement(writer, "Collections", collectionsAdvancement);

                for (ThemeCollection collection : ouicheList.getCollections()) {
                    addRectangleBeginning(writer);

                    AdvancementStats collectionAdvancement = computeAdvancement(collectAllCardsFromCollection(collection));
                    addTitle2WithAdvancement(writer, collection.getName(), collectionAdvancement);

                    addRectangleBeginning(writer);
                    displayMosaicWithOwnershipStatus(collection.getCardsList(), "Collection", writer, dirPath, relativeImagePath);
                    addRectangleEnd(writer);

                    for (List<Deck> deckUnit : collection.getLinkedDecks()) {
                        addRectangleBeginning(writer);
                        for (Deck deck : deckUnit) {
                            addRectangleBeginning(writer);

                            AdvancementStats deckAdvancement = computeAdvancement(deck.toList());
                            addTitle3WithAdvancement(writer, deck.getName(), deckAdvancement);

                            displayMosaicWithOwnershipStatus(deck.getMainDeck(), "Main deck", writer, dirPath, relativeImagePath);
                            displayMosaicWithOwnershipStatus(deck.getExtraDeck(), "Extra deck", writer, dirPath, relativeImagePath);
                            displayMosaicWithOwnershipStatus(deck.getSideDeck(), "Side deck", writer, dirPath, relativeImagePath);
                            addRectangleEnd(writer);
                        }
                        addRectangleEnd(writer);
                    }

                    addRectangleEnd(writer);
                }
            }

            if (ouicheList.getDecks() != null) {
                AdvancementStats decksAdvancement = computeAdvancement(collectAllCardsFromDecks(ouicheList));
                addTitle2WithAdvancement(writer, "Decks", decksAdvancement);

                for (Deck deck : ouicheList.getDecks()) {
                    addRectangleBeginning(writer);

                    AdvancementStats deckAdvancement = computeAdvancement(deck.toList());
                    addTitle3WithAdvancement(writer, deck.getName(), deckAdvancement);

                    displayMosaicWithOwnershipStatus(deck.getMainDeck(), "Main deck", writer, dirPath, relativeImagePath);
                    displayMosaicWithOwnershipStatus(deck.getExtraDeck(), "Extra deck", writer, dirPath, relativeImagePath);
                    displayMosaicWithOwnershipStatus(deck.getSideDeck(), "Side deck", writer, dirPath, relativeImagePath);
                    addRectangleEnd(writer);
                }
            }

            addFooter(writer);
        } catch (IOException e) {
            logger.error("Failed to write OuicheList mosaic HTML '{}' to '{}'", sanitizedName, filePath, e);
            throw e;
        }
    }

    /**
     * Collects all CardElements from every collection and standalone deck in the list.
     */
    private static List<CardElement> collectAllCards(DecksAndCollectionsList ouicheList) {
        List<CardElement> allCards = new ArrayList<>();
        if (ouicheList.getCollections() != null) {
            for (ThemeCollection collection : ouicheList.getCollections()) {
                allCards.addAll(collectAllCardsFromCollection(collection));
            }
        }
        if (ouicheList.getDecks() != null) {
            for (Deck deck : ouicheList.getDecks()) {
                allCards.addAll(deck.toList());
            }
        }
        return allCards;
    }

    /**
     * Collects all CardElements from every collection in the list.
     */
    private static List<CardElement> collectAllCardsFromCollections(DecksAndCollectionsList ouicheList) {
        List<CardElement> allCards = new ArrayList<>();
        if (ouicheList.getCollections() != null) {
            for (ThemeCollection collection : ouicheList.getCollections()) {
                allCards.addAll(collectAllCardsFromCollection(collection));
            }
        }
        return allCards;
    }

    /**
     * Collects all CardElements from a single ThemeCollection: all linked deck cards
     * followed by the collection's own card list.
     */
    private static List<CardElement> collectAllCardsFromCollection(ThemeCollection collection) {
        List<CardElement> allCards = new ArrayList<>();
        for (List<Deck> deckUnit : collection.getLinkedDecks()) {
            for (Deck deck : deckUnit) {
                allCards.addAll(deck.toList());
            }
        }
        allCards.addAll(collection.getCardsList());
        return allCards;
    }

    /**
     * Collects all CardElements from every standalone deck in the list.
     */
    private static List<CardElement> collectAllCardsFromDecks(DecksAndCollectionsList ouicheList) {
        List<CardElement> allCards = new ArrayList<>();
        if (ouicheList.getDecks() != null) {
            for (Deck deck : ouicheList.getDecks()) {
                allCards.addAll(deck.toList());
            }
        }
        return allCards;
    }

    /**
     * Strips characters illegal in file names and trims surrounding whitespace.
     */
    private static String sanitizeFileName(String name) {
        return name.replace("\\", "-").replace("/", "-").replace("\"", "").trim();
    }
}