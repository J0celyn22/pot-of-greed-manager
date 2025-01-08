package Model.FormatList;

import Model.CardsLists.DecksAndCollectionsList;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

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
     * @param dirPath The path of the output file.
     * @param fileName The name of the file, which will be sanitized and used as the HTML file name.
     * @throws IOException If an I/O error occurs during file creation or writing.
     */
    public static void generateOuicheListAsListHtml(DecksAndCollectionsList ouicheList, String dirPath, String fileName) throws IOException {
        String filePath = dirPath + fileName.replace("\\", "-").replace("/", "-").replace("\"", "") + " - List.html";
        createHtmlFile(filePath);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            String relativeImagePath = "..\\Images\\";
            addHeader(writer, fileName, relativeImagePath, dirPath);
            addTitle(writer, fileName, ouicheList.getCardCount(), ouicheList.getPrice());
            addMosaicButton(writer, fileName);
            addOuicheListButton(writer);

            if (ouicheList.getCollections() != null) {
                addTitle2(writer, "Collections", ouicheList.getCollectionsCardCount(), ouicheList.getCollectionsPrice());
                for (int i = 0; i < ouicheList.getCollections().size(); i++) {
                    addRectangleBeginning(writer);
                    addTitle2(writer, ouicheList.getCollections().get(i).getName(), ouicheList.getCollections().get(i).getCardCount(), ouicheList.getCollections().get(i).getPrice());

                    for (int j = 0; j < ouicheList.getCollections().get(i).getLinkedDecks().size(); j++) {
                        addRectangleBeginning(writer);
                        addTitle3(writer, ouicheList.getCollections().get(i).getLinkedDecks().get(j).getName(), ouicheList.getCollections().get(i).getLinkedDecks().get(j).getCardCount(), ouicheList.getCollections().get(i).getLinkedDecks().get(j).getPrice());
                        displayListWithOwned(ouicheList.getCollections().get(i).getLinkedDecks().get(j).getMainDeck(), "Main.Main deck", writer, dirPath, relativeImagePath);
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

            if (ouicheList.getDecks() != null) {
                addTitle2(writer, "Decks", ouicheList.getDecksCardCount(), ouicheList.getDecksPrice());
                for (int i = 0; i < ouicheList.getDecks().size(); i++) {
                    addRectangleBeginning(writer);
                    addTitle3(writer, ouicheList.getDecks().get(i).getName(), ouicheList.getDecks().get(i).getCardCount(), ouicheList.getDecks().get(i).getPrice());
                    displayListWithOwned(ouicheList.getDecks().get(i).getMainDeck(), "Main.Main deck", writer, dirPath, relativeImagePath);
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
     * Generates an HTML file displaying the OuicheList as a mosaic.
     *
     * <p>This method creates an HTML file that represents the provided OuicheList
     * in a mosaic format. It includes headers, titles, and buttons for navigation,
     * and displays information about collections and decks with their respective
     * card details. The file is saved to the specified directory path with the
     * given file name.</p>
     *
     * @param ouicheList The DecksAndCollectionsList to display.
     * @param dirPath The path of the output directory.
     * @param fileName The name of the file, which will be sanitized and used as the HTML file name.
     * @throws IOException If an I/O error occurs during file creation or writing.
     */
    public static void generateOuicheListAsMosaicHtml(DecksAndCollectionsList ouicheList, String dirPath, String fileName) throws IOException {
        String filePath = dirPath + fileName.replace("\\", "-").replace("/", "-").replace("\"", "") + " - Mosaic.html";
        createHtmlFile(filePath);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            String relativeImagePath = "..\\Images\\";
            addHeader(writer, fileName, relativeImagePath, dirPath);
            addTitle(writer, fileName, ouicheList.getCardCount(), ouicheList.getPrice());
            addListButton(writer, fileName);
            addOuicheListButton(writer);

            if (ouicheList.getCollections() != null) {
                addTitle2(writer, "Collections", ouicheList.getCollectionsCardCount(), ouicheList.getCollectionsPrice());
                for (int i = 0; i < ouicheList.getCollections().size(); i++) {
                    addRectangleBeginning(writer);
                    addTitle2(writer, ouicheList.getCollections().get(i).getName(), ouicheList.getCollections().get(i).getCardCount(), ouicheList.getCollections().get(i).getPrice());

                    for (int j = 0; j < ouicheList.getCollections().get(i).getLinkedDecks().size(); j++) {
                        addRectangleBeginning(writer);
                        addTitle3(writer, ouicheList.getCollections().get(i).getLinkedDecks().get(j).getName(), ouicheList.getCollections().get(i).getLinkedDecks().get(j).getCardCount(), ouicheList.getCollections().get(i).getLinkedDecks().get(j).getPrice());
                        displayMosaicWithOwned(ouicheList.getCollections().get(i).getLinkedDecks().get(j).getMainDeck(), "Main.Main deck", writer, dirPath, relativeImagePath);
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

            if (ouicheList.getDecks() != null) {
                addTitle2(writer, "Decks", ouicheList.getDecksCardCount(), ouicheList.getDecksPrice());
                for (int i = 0; i < ouicheList.getDecks().size(); i++) {
                    addRectangleBeginning(writer);
                    addTitle3(writer, ouicheList.getDecks().get(i).getName(), ouicheList.getDecks().get(i).getCardCount(), ouicheList.getDecks().get(i).getPrice());
                    displayMosaicWithOwned(ouicheList.getDecks().get(i).getMainDeck(), "Main.Main deck", writer, dirPath, relativeImagePath);
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