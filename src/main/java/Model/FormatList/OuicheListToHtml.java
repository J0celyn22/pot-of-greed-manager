package Model.FormatList;

import Model.CardsLists.CardElement;
import Model.CardsLists.Deck;
import Model.CardsLists.DecksAndCollectionsList;
import Model.CardsLists.ThemeCollection;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static Model.FormatList.HtmlGenerator.*;

public class OuicheListToHtml {
    /**
     * Generates an HTML file displaying a detailed list of the OuicheList.
     *
     * <p>This function creates an HTML file that represents the provided OuicheList as a detailed list.
     * It includes headers, titles, and buttons for navigation, as well as sections for both collections
     * and decks. Each section displays relevant card information and utilizes the provided directory path
     * for saving the file.</p>
     *
     * @param ouicheList The DecksAndCollectionsList to display.
     * @param dirPath    The path of the output file.
     * @param fileName   The name of the file, which will be sanitized and used as the HTML file name.
     * @throws IOException If an I/O error occurs during file creation or writing.
     */
    public static void generateOuicheListAsListHtml(DecksAndCollectionsList ouicheList, String dirPath, String fileName) throws IOException {
        String filePath = dirPath + fileName.replace("\\", "-").replace("/", "-").replace("\"", "") + " - List.html";
        createHtmlFile(filePath);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            String relativeImagePath = "..\\Images\\";
            addHeader(writer, fileName, relativeImagePath, dirPath);

            AdvancementStats ouicheListAdvancement = computeAdvancement(collectAllCards(ouicheList));
            addTitle1WithAdvancement(writer, fileName, ouicheListAdvancement);

            addMosaicButton(writer, fileName);
            addOuicheListButton(writer);

            if (ouicheList.getCollections() != null) {
                AdvancementStats collectionsAdvancement = computeAdvancement(collectAllCardsFromCollections(ouicheList));
                addTitle2WithAdvancement(writer, "Collections", collectionsAdvancement);

                for (int i = 0; i < ouicheList.getCollections().size(); i++) {
                    ThemeCollection collection = ouicheList.getCollections().get(i);
                    addRectangleBeginning(writer);

                    AdvancementStats collectionAdvancement = computeAdvancement(collectAllCardsFromCollection(collection));
                    addTitle2WithAdvancement(writer, collection.getName(), collectionAdvancement);

                    addRectangleBeginning(writer);
                    displayListWithOwnershipStatus(collection.getCardsList(), "Collection", writer, dirPath, relativeImagePath);
                    addRectangleEnd(writer);

                    for (int j = 0; j < collection.getLinkedDecks().size(); j++) {
                        addRectangleBeginning(writer);
                        for (int k = 0; k < collection.getLinkedDecks().get(j).size(); k++) {
                            Deck deck = collection.getLinkedDecks().get(j).get(k);
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

                for (int i = 0; i < ouicheList.getDecks().size(); i++) {
                    Deck deck = ouicheList.getDecks().get(i);
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
            e.printStackTrace();
        }
    }

    /**
     * Generates an HTML file displaying the OuicheList as a mosaic.
     *
     * <p>This method creates an HTML file that represents the provided OuicheList
     * in a mosaic format. It includes headers, titles, and buttons for navigation,
     * and displays information about collections and decks with their respective
     * card details. The file is saved to the specified directory path with the
     * given file name.</p>
     *
     * @param ouicheList The DecksAndCollectionsList to display.
     * @param dirPath    The path of the output directory.
     * @param fileName   The name of the file, which will be sanitized and used as the HTML file name.
     * @throws IOException If an I/O error occurs during file creation or writing.
     */
    public static void generateOuicheListAsMosaicHtml(DecksAndCollectionsList ouicheList, String dirPath, String fileName) throws IOException {
        String filePath = dirPath + fileName.replace("\\", "-").replace("/", "-").replace("\"", "") + " - Mosaic.html";
        createHtmlFile(filePath);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            String relativeImagePath = "..\\Images\\";
            addHeader(writer, fileName, relativeImagePath, dirPath);

            AdvancementStats ouicheListAdvancement = computeAdvancement(collectAllCards(ouicheList));
            addTitle1WithAdvancement(writer, fileName, ouicheListAdvancement);

            addListButton(writer, fileName);
            addOuicheListButton(writer);

            if (ouicheList.getCollections() != null) {
                AdvancementStats collectionsAdvancement = computeAdvancement(collectAllCardsFromCollections(ouicheList));
                addTitle2WithAdvancement(writer, "Collections", collectionsAdvancement);

                for (int i = 0; i < ouicheList.getCollections().size(); i++) {
                    ThemeCollection collection = ouicheList.getCollections().get(i);
                    addRectangleBeginning(writer);

                    AdvancementStats collectionAdvancement = computeAdvancement(collectAllCardsFromCollection(collection));
                    addTitle2WithAdvancement(writer, collection.getName(), collectionAdvancement);

                    addRectangleBeginning(writer);
                    displayMosaicWithOwnershipStatus(collection.getCardsList(), "Collection", writer, dirPath, relativeImagePath);
                    addRectangleEnd(writer);

                    for (int j = 0; j < collection.getLinkedDecks().size(); j++) {
                        addRectangleBeginning(writer);
                        for (int k = 0; k < collection.getLinkedDecks().get(j).size(); k++) {
                            Deck deck = collection.getLinkedDecks().get(j).get(k);
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

                for (int i = 0; i < ouicheList.getDecks().size(); i++) {
                    Deck deck = ouicheList.getDecks().get(i);
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
            e.printStackTrace();
        }
    }

    /**
     * Collects all CardElements from every collection and standalone deck in the given list.
     * Cards shared between a ThemeCollection's cardsList and its linked decks are each
     * included once per slot they occupy, mirroring how the OuicheList counts them.
     *
     * @param ouicheList The DecksAndCollectionsList to collect cards from.
     * @return A flat list of all CardElements.
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
     * Collects all CardElements from every collection in the given list.
     *
     * @param ouicheList The DecksAndCollectionsList to collect from.
     * @return A flat list of all CardElements belonging to collections.
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
     * followed by the collection's own cardsList.
     *
     * @param collection The ThemeCollection to collect cards from.
     * @return A flat list of all CardElements in the collection.
     */
    private static List<CardElement> collectAllCardsFromCollection(ThemeCollection collection) {
        List<CardElement> allCards = new ArrayList<>();
        for (List<Deck> deckGroup : collection.getLinkedDecks()) {
            for (Deck deck : deckGroup) {
                allCards.addAll(deck.toList());
            }
        }
        allCards.addAll(collection.getCardsList());
        return allCards;
    }

    /**
     * Collects all CardElements from every standalone deck in the given list.
     *
     * @param ouicheList The DecksAndCollectionsList to collect from.
     * @return A flat list of all CardElements belonging to standalone decks.
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
}