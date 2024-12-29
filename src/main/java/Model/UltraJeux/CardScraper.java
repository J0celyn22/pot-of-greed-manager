package Model.UltraJeux;

import Model.CardsLists.Card;

import java.util.ArrayList;

import static Model.Database.PrintCodeToKonamiId.getPrintCodeToKonamiId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import Model.CardsLists.CardElement;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class CardScraper {

    public static Map<String, List<String>> getCardNamesFromWebsite(List<CardElement> maOuicheList, double maxPrice) throws Exception {
        Map<String, List<String>> result = new HashMap<>();
        int pageNumber = 1;
        boolean hasMorePages = true;

        while (hasMorePages) {
            Thread.sleep(500); // Add a delay of 500 ms
            String url = "https://www.ultrajeux.com/search3.php?submit=Ok&jeu=2&prix_min=0&prix_max=1&prix_ref_max=45&dispo=1&tri=prix&order=0&limit=" + (pageNumber - 1) * 50;
            Document doc = Jsoup.connect(url).get();
            Elements rows = doc.select("table tr");

            if (rows.isEmpty()) {
                hasMorePages = false;
                break;
            }

            for (Element row : rows) {
                Elements columns = row.select("td");
                if (columns.size() < 5) continue;

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
                    System.out.println("Max price reached at page: " + pageNumber);
                    continue;
                }

                String konamiId = getPrintCodeToKonamiId().get(printCode);
                Card card = null;

                if (konamiId != null) {
                    card = findCardById(maOuicheList, konamiId);
                } else {
                    card = findCardByName(maOuicheList, name);
                }

                if (card != null) {
                    result.computeIfAbsent("Page " + pageNumber, k -> new ArrayList<>()).add(name);
                    System.out.println("Card Name: " + name + ", Page: " + pageNumber);
                }
            }
            pageNumber++;
        }


        return result;
    }

    private static Card findCardById(List<CardElement> maOuicheList, String konamiId) {
        for (CardElement element : maOuicheList) {
            if(element.getCard().getKonamiId() != null) {
                if (element.getCard().getKonamiId().equals(konamiId)) {
                    return element.getCard();
                }
            }
        }
        return null;
    }

    private static Card findCardByName(List<CardElement> maOuicheList, String name) {
        for (CardElement element : maOuicheList) {
            Card card = element.getCard();
            if(card.getName_EN()!= null) {
                if (card.getName_EN().equals(name)) {
                    return card;
                }
            }

            if(card.getName_FR()!= null) {
                if (card.getName_FR().equals(name)) {
                    return card;
                }
            }
        }
        return null;
    }
}



