package Model.FormatList;

import Model.CardsLists.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static Model.FormatList.HtmlGenerator.*;

public class OwnedCardsCollectionToHtml {

    private static final Logger logger = LoggerFactory.getLogger(OwnedCardsCollectionToHtml.class);

    /**
     * Generates an HTML file displaying all cards in the collection as a flat list,
     * with each distinct card appearing once alongside its occurrence count.
     *
     * @param collection     the collection to display
     * @param dirPath        the directory path for the output file
     * @param outputFileName the base name of the output file (without extension)
     * @throws IOException if the file cannot be written
     */
    public static void generateListHtml(OwnedCardsCollection collection, String dirPath, String outputFileName) throws IOException {
        List<CardElement> cards = new ArrayList<>();
        for (Box box : collection.getOwnedCollection()) {
            for (CardsGroup group : box.getContent()) {
                cards.addAll(group.getCardList());
            }
        }

        String sanitizedName = sanitizeFileName(outputFileName);
        String filePath = dirPath + sanitizedName + ".html";
        createHtmlFile(filePath);
        String relativeImagePath = ".." + java.io.File.separator + "Images" + java.io.File.separator;
        String imagesDirPath = dirPath + relativeImagePath;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            addHeader(writer, sanitizedName, relativeImagePath, dirPath);
            addTitle(writer, sanitizedName, collection.getCardCount(), collection.getPrice());
            addLinkButtons(writer);

            Map<Card, Integer> cardCount = createCardsMap(cards);
            for (Map.Entry<Card, Integer> entry : cardCount.entrySet()) {
                writeCardElement(writer, entry.getKey(), entry.getValue(), false, imagesDirPath, relativeImagePath);
            }

            addFooter(writer);
        } catch (IOException e) {
            logger.error("Failed to write list HTML for collection '{}' to '{}'", sanitizedName, filePath, e);
            throw e;
        }
    }

    /**
     * Generates an HTML file displaying cards grouped by box and category, as a list.
     *
     * @param collection     the collection to display
     * @param dirPath        the directory path for the output file
     * @param outputFileName the base name of the output file (without extension)
     * @throws IOException if the file cannot be written
     */
    public static void generateCollectionAsListHtml(OwnedCardsCollection collection, String dirPath, String outputFileName) throws IOException {
        String sanitizedName = sanitizeFileName(outputFileName);
        String filePath = dirPath + sanitizedName + " - List.html";
        createHtmlFile(filePath);
        String relativeImagePath = ".." + java.io.File.separator + "Images" + java.io.File.separator;
        String imagesDirPath = dirPath + relativeImagePath;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            addHeader(writer, sanitizedName, relativeImagePath, dirPath);
            addTitle(writer, sanitizedName, collection.getCardCount(), collection.getPrice());
            addMosaicButton(writer, sanitizedName);
            addCollectionButton(writer);

            for (Box box : collection.getOwnedCollection()) {
                addTitle2(writer,
                        box.getName().replace("=", ""),
                        box.getCardCount(),
                        box.getPrice());

                for (CardsGroup group : box.getContent()) {
                    addTitle3(writer,
                            group.getName().replace("-", ""),
                            group.getCardCount(),
                            group.getPrice());

                    Map<Card, Integer> cardCount = createCardsMap(new ArrayList<>(group.getCardList()));
                    for (Map.Entry<Card, Integer> entry : cardCount.entrySet()) {
                        writeCardElement(writer, entry.getKey(), entry.getValue(), false, imagesDirPath, relativeImagePath);
                    }
                }
            }

            addFooter(writer);
        } catch (IOException e) {
            logger.error("Failed to write list HTML for collection '{}' to '{}'", sanitizedName, filePath, e);
            throw e;
        }
    }

    /**
     * Generates an HTML file displaying cards grouped by box and category, as a mosaic.
     *
     * @param collection     the collection to display
     * @param dirPath        the directory path for the output file
     * @param outputFileName the base name of the output file (without extension)
     * @throws IOException if the file cannot be written
     */
    public static void generateCollectionAsMosaicHtml(OwnedCardsCollection collection, String dirPath, String outputFileName) throws IOException {
        String sanitizedName = sanitizeFileName(outputFileName);
        String filePath = dirPath + sanitizedName + " - Mosaic.html";
        createHtmlFile(filePath);
        String relativeImagePath = ".." + java.io.File.separator + "Images" + java.io.File.separator;
        String imagesDirPath = dirPath + relativeImagePath;

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            addHeader(writer, sanitizedName, relativeImagePath, dirPath);
            addTitle(writer, sanitizedName, collection.getCardCount(), collection.getPrice());
            addListButton(writer, sanitizedName);
            addCollectionButton(writer);

            for (Box box : collection.getOwnedCollection()) {
                addRectangleBeginning(writer);
                addTitle2(writer,
                        box.getName().replace("=", ""),
                        box.getCardCount(),
                        box.getPrice());

                for (CardsGroup group : box.getContent()) {
                    HtmlGenerator.addTitle3(writer,
                            group.getName().replace("-", ""),
                            group.getCardCount(),
                            group.getPrice());

                    for (CardElement card : group.getCardList()) {
                        writeCardElement(writer, card, imagesDirPath, relativeImagePath);
                    }
                }
                addRectangleEnd(writer);
            }

            addFooter(writer);
        } catch (IOException e) {
            logger.error("Failed to write mosaic HTML for collection '{}' to '{}'", sanitizedName, filePath, e);
            throw e;
        }
    }

    /**
     * Strips characters that are illegal in file names and trims surrounding whitespace.
     */
    private static String sanitizeFileName(String name) {
        return name.replace("\\", "-").replace("/", "-").replace("\"", "").trim();
    }
}