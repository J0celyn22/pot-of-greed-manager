package Model.FormatList;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.CardsLists.OwnedCardsCollection;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static Model.FormatList.HtmlGenerator.*;

public class OwnedCardsCollectionToHtml {
    /**
     * Generate an HTML file displaying a list of all cards with each card appearing only once, with its number of occurences displayed
     *
     * @param collection     The Collection to display
     * @param dirPath        The path of the output file
     * @param outputFileName The name of the output file
     * @throws IOException
     */
    public static void generateListHtml(OwnedCardsCollection collection, String dirPath, String outputFileName) throws IOException {
        List<CardElement> cards = new ArrayList<>();
        for (int i = 0; i < collection.getOwnedCollection().size(); i++) {
            for (int j = 0; j < collection.getOwnedCollection().get(i).getContent().size(); j++) {
                cards.addAll(collection.getOwnedCollection().get(i).getContent().get(j).cardList);
            }
        }

        outputFileName = outputFileName.replace("\\", "-").replace("/", "-").replace("\"", "");
        String filePath = dirPath + outputFileName.replace("\\", "-").replace("/", "-").replace("\"", "") + ".html";
        createHtmlFile(filePath);
        String relativeImagePath = "..\\Images\\";
        String imagesDirPath = dirPath + relativeImagePath;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            addHeader(writer, outputFileName, relativeImagePath, dirPath);
            addTitle(writer, outputFileName, collection.getCardCount(), collection.getPrice());
            addLinkButtons(writer);

            Map<Card, Integer> cardCount = createCardsMap(cards);

            for (Map.Entry<Card, Integer> entry : cardCount.entrySet()) {
                writeCardElement(writer, entry.getKey(), entry.getValue(), false, imagesDirPath, relativeImagePath);
            }

            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generate an HTML file displaying a list of all cards within their boxes and categories, as a list
     *
     * @param collection     The Collection to display
     * @param dirPath        The path of the output file
     * @param outputFileName The name of the output file
     * @throws IOException
     */
    public static void generateCollectionAsListHtml(OwnedCardsCollection collection, String dirPath, String outputFileName) throws IOException {
        outputFileName = outputFileName.replace("\\", "-").replace("/", "-").replace("\"", "");
        String filePath = dirPath + outputFileName.replace("\\", "-").replace("/", "-").replace("\"", "") + " - List.html";
        createHtmlFile(filePath);
        String relativeImagePath = "..\\Images\\";
        String imagesDirPath = dirPath + relativeImagePath;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            addHeader(writer, outputFileName, relativeImagePath, dirPath);
            addTitle(writer, outputFileName, collection.getCardCount(), collection.getPrice());
            addMosaicButton(writer, outputFileName);
            addCollectionButton(writer);

            List<CardElement> cards;
            for (int i = 0; i < collection.getOwnedCollection().size(); i++) {
                addTitle2(writer, collection.getOwnedCollection().get(i).getName().replace("=", ""), collection.getOwnedCollection().get(i).getCardCount(), collection.getOwnedCollection().get(i).getPrice());
                for (int j = 0; j < collection.getOwnedCollection().get(i).getContent().size(); j++) {
                    addTitle3(writer, collection.getOwnedCollection().get(i).getContent().get(j).getName().replace("-", ""), collection.getOwnedCollection().get(i).getContent().get(j).getCardCount(), collection.getOwnedCollection().get(i).getContent().get(j).getPrice());

                    cards = new ArrayList<>(collection.getOwnedCollection().get(i).getContent().get(j).cardList);

                    Map<Card, Integer> cardCount = createCardsMap(cards);

                    for (Map.Entry<Card, Integer> entry : cardCount.entrySet()) {
                        writeCardElement(writer, entry.getKey(), entry.getValue(), false, imagesDirPath, relativeImagePath);
                    }
                }
            }

            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generate an HTML file displaying a list of all cards within their boxes and categories, as a mosaic
     *
     * @param collection     The Collection to display
     * @param dirPath        The path of the output file
     * @param outputFileName The name of the output file
     * @throws IOException
     */
    public static void generateCollectionAsMosaicHtml(OwnedCardsCollection collection, String dirPath, String outputFileName) throws IOException {
        outputFileName = outputFileName.replace("\\", "-").replace("/", "-").replace("\"", "");
        String filePath = dirPath + outputFileName.replace("\\", "-").replace("/", "-").replace("\"", "") + " - Mosaic.html";
        createHtmlFile(filePath);
        String relativeImagePath = "..\\Images\\";
        String imagesDirPath = dirPath + relativeImagePath;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            addHeader(writer, outputFileName, relativeImagePath, dirPath);
            addTitle(writer, outputFileName, collection.getCardCount(), collection.getPrice());
            addListButton(writer, outputFileName);
            addCollectionButton(writer);

            for (int i = 0; i < collection.getOwnedCollection().size(); i++) {
                addRectangleBeginning(writer);
                addTitle2(writer, collection.getOwnedCollection().get(i).getName().replace("=", ""), collection.getOwnedCollection().get(i).getCardCount(), collection.getOwnedCollection().get(i).getPrice());
                for (int j = 0; j < collection.getOwnedCollection().get(i).getContent().size(); j++) {
                    HtmlGenerator.addTitle3(writer, collection.getOwnedCollection().get(i).getContent().get(j).getName().replace("-", ""), collection.getOwnedCollection().get(i).getContent().get(j).getCardCount(), collection.getOwnedCollection().get(i).getContent().get(j).getPrice());

                    for (int k = 0; k < collection.getOwnedCollection().get(i).getContent().get(j).cardList.size(); k++) {
                        writeCardElement(writer, collection.getOwnedCollection().get(i).getContent().get(j).cardList.get(k), imagesDirPath, relativeImagePath);
                    }
                }
                addRectangleEnd(writer);
            }

            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}