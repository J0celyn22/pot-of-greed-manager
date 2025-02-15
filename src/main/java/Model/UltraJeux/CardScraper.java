package Model.UltraJeux;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static Model.Database.PrintCodeToKonamiId.getPrintCodeToKonamiId;
import static Model.FilePaths.outputPath;

public class CardScraper {
    /**
     * Retrieves card names from a website based on a list of card elements and a maximum price.
     * <p>
     * This method connects to a card-selling website and retrieves card names and their prices
     * from multiple pages until the specified maximum price is reached. The card details are
     * written to an output file, and occurrences of card names are tracked.
     *
     * @param maOuicheList A list of CardElement objects used to match cards by ID or name.
     * @param maxPrice     The maximum price threshold for retrieving card names.
     * @return A map containing page numbers as keys and lists of card names as values.
     * @throws Exception If an error occurs during HTTP connection or file writing.
     */
    public static Map<String, List<String>> getCardNamesFromWebsite(List<CardElement> maOuicheList, double maxPrice) throws Exception {
        Map<String, List<String>> result = new HashMap<>();
        Map<String, Integer> cardNameOccurrences = new HashMap<>();
        int pageNumber = 1;
        boolean hasMorePages = true;

        // Create or replace the output file
        //try (BufferedWriter writer = new BufferedWriter(new FileWriter("../Output/ListeUltraJeux.txt"))) {
        //try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("../Output/ListeUltraJeux.txt"), StandardCharsets.UTF_8))) {
        //try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("../Output/ListeUltraJeux.txt"), StandardCharsets.UTF_8))) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath + "\\ListeUltraJeux.txt"), StandardCharsets.UTF_8))) {
            while (hasMorePages) {
                Thread.sleep(1000); // Add a delay of 1 second between pages to avoid being blocked by the website
                String url = "https://www.ultrajeux.com/search3.php?submit=Ok&jeu=2&prix_min=0&prix_max=1&prix_ref_max=45&dispo=1&tri=prix&order=0&limit=" + (pageNumber - 1) * 50;
                Document doc = Jsoup.connect(url).get();
                Elements rows = doc.select("table tr");

                boolean validPage = false;

                for (Element row : rows) {
                    Elements columns = row.select("td");
                    if (columns.size() < 5) continue;

                    validPage = true;

                    String printCode = columns.get(1).text();
                    String name = columns.get(2).text();
                    String priceText = columns.get(11).text().replace(" \u0080", "").trim();
                    double price;

                    try {
                        price = Double.parseDouble(priceText);
                    } catch (NumberFormatException e) {
                        continue; // Skip this row if the price is not a valid number
                    }

                    if (price > maxPrice) {
                        hasMorePages = false; // Exit the loop if the max price is reached
                        writer.write("Max price reached at page: " + pageNumber + "\n");
                        break;
                    }

                    String konamiId = getPrintCodeToKonamiId().get(printCode);
                    Card card;

                    if (konamiId != null) {
                        card = findCardById(maOuicheList, konamiId);
                    } else {
                        card = findCardByName(maOuicheList, name);
                    }

                    if (card != null) {
                        result.computeIfAbsent("Page " + pageNumber, k -> new ArrayList<>()).add(name);
                        int occurrence = cardNameOccurrences.getOrDefault(name, 0) + 1;
                        cardNameOccurrences.put(name, occurrence);
                        if (occurrence > 1) {
                            writer.write("Card Name: " + name + ", Page: " + pageNumber + ", Price: " + price + "€, Occurrence: " + occurrence + "\n");
                            System.out.println("Card Name: " + name + ", Page: " + pageNumber + ", Price: " + price + "€, Occurrence: " + occurrence);
                        } else {
                            writer.write("Card Name: " + name + ", Page: " + pageNumber + ", Price: " + price + "€\n");
                            System.out.println("Card Name: " + name + ", Page: " + pageNumber + ", Price: " + price + "€");
                        }
                    }
                }

                if (!validPage) {
                    hasMorePages = false;
                }

                pageNumber++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }


    /**
     * Find a card in the given list by its konami id.
     * @param maOuicheList the list of cards to search in
     * @param konamiId the konami id of the card to find
     * @return the card if found, null otherwise
     */
    private static Card findCardById(List<CardElement> maOuicheList, String konamiId) {
        for (CardElement element : maOuicheList) {
            if (element.getCard().getKonamiId() != null) {
                if (element.getCard().getKonamiId().equals(konamiId)) {
                    return element.getCard();
                }
            }
        }
        return null;
    }

    /**
     * Finds a card in the given list by its name in either English or French.
     * @param maOuicheList the list of cards to search in
     * @param name the name of the card to find
     * @return the card if found, null otherwise
     */
    private static Card findCardByName(List<CardElement> maOuicheList, String name) {
        for (CardElement element : maOuicheList) {
            Card card = element.getCard();
            if (card.getName_EN() != null) {
                if (card.getName_EN().equals(name)) {
                    return card;
                }
            }

            if (card.getName_FR() != null) {
                if (card.getName_FR().equals(name)) {
                    return card;
                }
            }
        }
        return null;
    }
}



