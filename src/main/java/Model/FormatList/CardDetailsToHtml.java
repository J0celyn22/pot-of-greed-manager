package Model.FormatList;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static Model.FormatList.HtmlGenerator.*;

public class CardDetailsToHtml {
    public static void generateCardDetailsHtml(String dirPath, Card card) throws IOException {
        String outputFileName = card.getArtNumber();
        String filePath = dirPath + outputFileName + ".html";
        createHtmlFile(filePath);
        String relativeImagePath = "..\\Images\\";
        String imagesDirPath = dirPath + relativeImagePath;
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            addHeader(writer, outputFileName, relativeImagePath, dirPath);

            addTitle(writer, card.getName_EN());



            addFooter(writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}