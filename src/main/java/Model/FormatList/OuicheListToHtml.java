package Model.FormatList;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.CardsLists.DecksAndCollectionsList;

import java.io.BufferedWriter;
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

public class OuicheListToHtml {
    /**
     * Generate an HTML file displaying a ThemeCollection, as a list
     * @param ouicheList The ThemeCollection to display
     * @param dirPath The path of the output file
     * @throws IOException
     */
    public static void generateOuicheListAsListHtml(DecksAndCollectionsList ouicheList, String dirPath, String fileName) throws IOException {
        String filePath = dirPath + fileName.replace("\\", "-").replace("/", "-").replace("\"", "") + " - List.html";
        createHtmlFile(filePath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            String relativeImagePath = "..\\Images\\";
            addHeader(writer, fileName, relativeImagePath, dirPath);
            addTitle(writer, fileName, ouicheList.getCardCount(), ouicheList.getPrice());
            addMosaicButton(writer, fileName);
            addOuicheListButton(writer);

            if(ouicheList.getCollections() != null) {
                addTitle2(writer, "Collections", ouicheList.getCollectionsCardCount(), ouicheList.getCollectionsPrice());
                for (int i = 0; i < ouicheList.getCollections().size(); i++) {
                    addRectangleBeginning(writer);
                    addTitle2(writer, ouicheList.getCollections().get(i).getName(), ouicheList.getCollections().get(i).getCardCount(), ouicheList.getCollections().get(i).getPrice());

                    for (int j = 0; j < ouicheList.getCollections().get(i).getLinkedDecks().size(); j++) {
                        addRectangleBeginning(writer);
                        addTitle3(writer, ouicheList.getCollections().get(i).getLinkedDecks().get(j).getName(), ouicheList.getCollections().get(i).getLinkedDecks().get(j).getCardCount(), ouicheList.getCollections().get(i).getLinkedDecks().get(j).getPrice());
                        displayListWithOwned(ouicheList.getCollections().get(i).getLinkedDecks().get(j).getMainDeck(), "Main deck", writer, dirPath, relativeImagePath);
                        displayListWithOwned(ouicheList.getCollections().get(i).getLinkedDecks().get(j).getExtraDeck(), "Extra deck", writer, dirPath, relativeImagePath);
                        displayListWithOwned(ouicheList.getCollections().get(i).getLinkedDecks().get(j).getSideDeck(), "Side deck", writer, dirPath, relativeImagePath);
                        addRectangleEnd(writer);
                    }

                    addRectangleBeginning(writer);
                    displayListWithOwned(ouicheList.getCollections().get(i).getCardsList(), "Collection", writer, dirPath, relativeImagePath);
                    addRectangleEnd(writer);

                    addRectangleEnd(writer);
                }
            }

            if(ouicheList.getDecks() != null) {
                addTitle2(writer, "Decks", ouicheList.getDecksCardCount(), ouicheList.getDecksPrice());
                for (int i = 0; i < ouicheList.getDecks().size(); i++) {
                    addRectangleBeginning(writer);
                    addTitle3(writer, ouicheList.getDecks().get(i).getName(), ouicheList.getDecks().get(i).getCardCount(), ouicheList.getDecks().get(i).getPrice());
                    displayListWithOwned(ouicheList.getDecks().get(i).getMainDeck(), "Main deck", writer, dirPath, relativeImagePath);
                    displayListWithOwned(ouicheList.getDecks().get(i).getExtraDeck(), "Extra deck", writer, dirPath, relativeImagePath);
                    displayListWithOwned(ouicheList.getDecks().get(i).getSideDeck(), "Side deck", writer, dirPath, relativeImagePath);
                    addRectangleEnd(writer);
                }
            }

            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generate an HTML file displaying a ThemeCollection, as a mosaic
     * @param ouicheList The Deck to display
     * @param dirPath The path of the output file
     * @throws IOException
     */
    public static void generateOuicheListAsMosaicHtml(DecksAndCollectionsList ouicheList, String dirPath, String fileName) throws IOException {
        String filePath = dirPath + fileName.replace("\\", "-").replace("/", "-").replace("\"", "") + " - Mosaic.html";
        createHtmlFile(filePath);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            String relativeImagePath = "..\\Images\\";
            addHeader(writer, fileName, relativeImagePath, dirPath);
            addTitle(writer, fileName, ouicheList.getCardCount(), ouicheList.getPrice());
            addListButton(writer, fileName);
            addOuicheListButton(writer);

            if(ouicheList.getCollections() != null) {
                addTitle2(writer, "Collections", ouicheList.getCollectionsCardCount(), ouicheList.getCollectionsPrice());
                for (int i = 0; i < ouicheList.getCollections().size(); i++) {
                    addRectangleBeginning(writer);
                    addTitle2(writer, ouicheList.getCollections().get(i).getName(), ouicheList.getCollections().get(i).getCardCount(), ouicheList.getCollections().get(i).getPrice());

                    for (int j = 0; j < ouicheList.getCollections().get(i).getLinkedDecks().size(); j++) {
                        addRectangleBeginning(writer);
                        addTitle3(writer, ouicheList.getCollections().get(i).getLinkedDecks().get(j).getName(), ouicheList.getCollections().get(i).getLinkedDecks().get(j).getCardCount(), ouicheList.getCollections().get(i).getLinkedDecks().get(j).getPrice());
                        displayMosaicWithOwned(ouicheList.getCollections().get(i).getLinkedDecks().get(j).getMainDeck(), "Main deck", writer, dirPath, relativeImagePath);
                        displayMosaicWithOwned(ouicheList.getCollections().get(i).getLinkedDecks().get(j).getExtraDeck(), "Extra deck", writer, dirPath, relativeImagePath);
                        displayMosaicWithOwned(ouicheList.getCollections().get(i).getLinkedDecks().get(j).getSideDeck(), "Side deck", writer, dirPath, relativeImagePath);
                        addRectangleEnd(writer);
                    }

                    addRectangleBeginning(writer);
                    displayMosaicWithOwned(ouicheList.getCollections().get(i).getCardsList(), "Collection", writer, dirPath, relativeImagePath);
                    addRectangleEnd(writer);

                    addRectangleEnd(writer);
                }
            }

            if(ouicheList.getDecks() != null) {
                addTitle2(writer, "Decks", ouicheList.getDecksCardCount(), ouicheList.getDecksPrice());
                for (int i = 0; i < ouicheList.getDecks().size(); i++) {
                    addRectangleBeginning(writer);
                    addTitle3(writer, ouicheList.getDecks().get(i).getName(), ouicheList.getDecks().get(i).getCardCount(), ouicheList.getDecks().get(i).getPrice());
                    displayMosaicWithOwned(ouicheList.getDecks().get(i).getMainDeck(), "Main deck", writer, dirPath, relativeImagePath);
                    displayMosaicWithOwned(ouicheList.getDecks().get(i).getExtraDeck(), "Extra deck", writer, dirPath, relativeImagePath);
                    displayMosaicWithOwned(ouicheList.getDecks().get(i).getSideDeck(), "Side deck", writer, dirPath, relativeImagePath);
                    addRectangleEnd(writer);
                }
            }

            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}