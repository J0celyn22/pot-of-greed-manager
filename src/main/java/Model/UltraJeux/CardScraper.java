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
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static Model.Database.PrintCodeToKonamiId.getPrintCodeToKonamiId;
import static Model.FilePaths.outputPath;

public class CardScraper {

    /**
     * Fetches the edition (set) list from the site and returns a map editionName -> editionId.
     * (Assumes your working getEditionMap implementation is present; keep the version that already works.)
     */
    private static Map<String, String> getEditionMap() throws IOException {
        Map<String, String> editionMap = new HashMap<>();
        String editionListUrl = "https://www.ultrajeux.com/search3.php?submit=Ok&jeu=2";

        Document doc = Jsoup.connect(editionListUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
                .referrer("https://www.google.com")
                .timeout(30_000)
                .get();

        Element container = doc.selectFirst("div#container.search");
        if (container == null) container = doc.selectFirst("div.search");
        if (container == null) return editionMap;

        Element affinage = container.selectFirst("div.affinage");
        if (affinage == null) affinage = doc.selectFirst("div.affinage");
        if (affinage == null) return editionMap;

        Elements mainBlocks = affinage.select("div.main_bloc_article");
        Element editionBlock = null;
        for (Element mb : mainBlocks) {
            Element titleSpan = mb.selectFirst("span.main_bloc_article_titre");
            if (titleSpan != null) {
                String title = titleSpan.text().trim();
                if (title.equalsIgnoreCase("Edition") || title.toLowerCase().contains("edition")) {
                    editionBlock = mb;
                    break;
                }
            }
        }
        if (editionBlock == null) return editionMap;

        Elements links = editionBlock.select("ul li a[href]");
        Pattern idPattern = Pattern.compile("(?:num_edition%5B%5D|num_edition\\[\\])=([0-9]+)");
        for (Element a : links) {
            String href = a.attr("href").trim();
            String label = a.text().trim();
            if (label.isEmpty()) label = a.attr("title").trim();
            if (label.isEmpty()) label = "Edition";

            Matcher m = idPattern.matcher(href);
            String id = null;
            if (m.find()) {
                id = m.group(1);
            } else {
                int q = href.indexOf('?');
                if (q >= 0) {
                    String query = href.substring(q + 1);
                    for (String part : query.split("&")) {
                        if (part.startsWith("num_edition")) {
                            int eq = part.indexOf('=');
                            if (eq >= 0 && eq + 1 < part.length()) {
                                id = part.substring(eq + 1);
                                break;
                            }
                        }
                    }
                }
            }

            if (id != null && !id.isEmpty()) {
                editionMap.put(label, id);
            }
        }

        return editionMap;
    }

    /**
     * Retrieves card names from the website by iterating all editions (sets) and paginating each edition.
     * Uses robust parsing with fallbacks for different page layouts.
     */
    public static Map<String, List<String>> getCardNamesFromWebsite(List<CardElement> maOuicheList, double maxPrice) throws Exception {
        final double OVERPRICE_THRESHOLD = 0.30;
        Map<String, Integer> ouicheCountMap = buildOuicheCountMap(maOuicheList);

        Map<String, List<String>> result = new HashMap<>();
        Pattern printCodePattern = Pattern.compile("\\b([A-Z0-9]{2,}(?:-?[A-Z0-9]+)?)\\b");

        Map<String, String> editionMap = getEditionMap();
        System.out.println("Found " + editionMap.size() + " editions.");

        Map<String, Integer> occurrenceCounts = new HashMap<>();

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputPath + "\\ListeUltraJeux.txt"), StandardCharsets.UTF_8))) {

            List<Entry> collected = new ArrayList<>();

            for (Map.Entry<String, String> editionEntry : editionMap.entrySet()) {
                String editionName = editionEntry.getKey();
                String editionId = editionEntry.getValue();

                //writer.write("=== Edition: " + editionName + " (id=" + editionId + ") ===\n");
                System.out.println("Scraping edition: " + editionName + " (id=" + editionId + ")");

                int pageNumber = 1;
                boolean hasMorePages = true;

                while (hasMorePages) {
                    Thread.sleep(1000);
                    Thread.sleep((long) (Math.random() * 2000));

                    int offset = (pageNumber - 1) * 50;
                    String url = "https://www.ultrajeux.com/search3.php?submit=Ok&tri=prix&order=0&jeu=2&prix_min=0&prix_ref_max=30&dispo=1&num_edition%5B%5D="
                            + editionId + "&limit=" + offset;

                    Document doc;
                    try {
                        doc = Jsoup.connect(url)
                                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
                                .referrer("https://www.google.com")
                                .timeout(30_000)
                                .get();
                    } catch (IOException e) {
                        System.err.println("Failed to fetch page for edition " + editionName + " page " + pageNumber + ": " + e.getMessage());
                        writer.write("Failed to fetch page " + pageNumber + " for edition " + editionName + ": " + e.getMessage() + "\n");
                        break;
                    }

                    // Strategy A: div.block_produit (preferred)
                    Elements products = doc.select("div.block_produit");
                    boolean usedFallbackTable = false;

                    // Strategy B fallback: table rows (older layout)
                    if (products.isEmpty()) {
                        Elements rows = doc.select("table tr");
                        if (!rows.isEmpty()) {
                            usedFallbackTable = true;
                            // parse rows into pseudo-products
                            for (Element row : rows) {
                                Elements cols = row.select("td");
                                if (cols.size() < 2) continue;
                                // Heuristic: find link, name and price in the row
                                Element linkEl = row.selectFirst("a[href]");
                                String productUrl = (linkEl != null) ? linkEl.absUrl("href") : url;
                                String name = null;
                                if (linkEl != null) name = linkEl.text().trim();
                                if (name == null || name.isEmpty()) {
                                    // try first column text
                                    name = cols.get(0).text().trim();
                                }
                                if (name == null || name.isEmpty()) continue;

                                // price: try last column that contains digits
                                double price = -1;
                                for (int i = cols.size() - 1; i >= 0; i--) {
                                    String pt = cols.get(i).text().replace("\u00A0", " ").replace("€", "").trim();
                                    pt = pt.replace(',', '.').replaceAll("[^0-9.]", "");
                                    if (pt.isEmpty()) continue;
                                    try {
                                        price = Double.parseDouble(pt);
                                        break;
                                    } catch (NumberFormatException ignored) {
                                    }
                                }
                                if (price < 0) continue;

                                Entry entry = new Entry(name, price, editionName, editionId, pageNumber, productUrl);
                                // try to extract print code from row text
                                Matcher mpc = printCodePattern.matcher(row.text());
                                if (mpc.find()) entry.extraNote = mpc.group(1);

                                // flags
                                if (price > maxPrice) entry.skipped = true;
                                else if (price >= OVERPRICE_THRESHOLD) entry.noMatch = true;
                                else {
                                    String konamiId = null;
                                    if (entry.extraNote != null)
                                        konamiId = getPrintCodeToKonamiId().get(entry.extraNote);
                                    Card card = null;
                                    if (konamiId != null) card = findCardById(maOuicheList, konamiId);
                                    else {
                                        String normalized = normalizeForCompare(name);
                                        if (!normalized.isEmpty())
                                            card = findCardByNormalizedName(maOuicheList, normalized, name);
                                        else card = findCardByName(maOuicheList, name);
                                    }
                                    if (card != null) {
                                        entry.matched = true;
                                        String img = card.getImagePath();
                                        if (img != null) {
                                            entry.ouicheCount = ouicheCountMap.getOrDefault(img, 0);
                                        } else {
                                            entry.ouicheCount = 0;
                                        }
                                    } else entry.noMatch = true;
                                }
                                if (entry.matched) {
                                    collected.add(entry);
                                }
                            }
                        }
                    }

                    // If not using table fallback, parse div.block_produit
                    if (!usedFallbackTable) {
                        if (products.isEmpty()) {
                            // no products on this page -> stop
                            hasMorePages = false;
                            break;
                        }

                        for (Element product : products) {
                            // name
                            Element nameEl = product.selectFirst("div.contenu p.titre");
                            if (nameEl == null) nameEl = product.selectFirst("p.titre");
                            if (nameEl == null) {
                                // try any link text inside product
                                Element link = product.selectFirst("a[href]");
                                if (link != null) nameEl = link;
                            }
                            if (nameEl == null) continue;
                            String name = nameEl.text().trim();
                            if (name.isEmpty()) continue;

                            // subtitle / print code
                            String subtitle = "";
                            Element sousTitreEl = product.selectFirst("div.contenu p.sous_titre");
                            if (sousTitreEl == null) sousTitreEl = product.selectFirst("p.sous_titre");
                            if (sousTitreEl != null) subtitle = sousTitreEl.text().trim();

                            String printCode = null;
                            if (!subtitle.isEmpty()) {
                                Matcher m = printCodePattern.matcher(subtitle);
                                if (m.find()) printCode = m.group(1).trim();
                            }

                            // price
                            Element priceEl = product.selectFirst("span.prix");
                            double price;
                            if (priceEl == null) {
                                // try other heuristics: any element with class containing "prix" or last numeric text
                                priceEl = product.selectFirst("[class*=prix]");
                            }
                            if (priceEl == null) {
                                // fallback: search for any numeric token in product text that looks like a price
                                String all = product.text();
                                Matcher pm = Pattern.compile("([0-9]+[\\.,]?[0-9]*)\\s*€").matcher(all);
                                if (pm.find()) {
                                    String pt = pm.group(1).replace(',', '.');
                                    price = Double.parseDouble(pt);
                                } else {
                                    // try last number in text
                                    Matcher nm = Pattern.compile("([0-9]+[\\.,]?[0-9]*)").matcher(all);
                                    double found = -1;
                                    while (nm.find()) {
                                        try {
                                            found = Double.parseDouble(nm.group(1).replace(',', '.'));
                                        } catch (NumberFormatException ignored) {
                                        }
                                    }
                                    if (found < 0) continue;
                                    price = found;
                                }
                            } else {
                                String priceText = priceEl.text().replace("\u00A0", " ").replace("€", "").trim();
                                priceText = priceText.replace(',', '.').replaceAll("[^0-9.]", "");
                                try {
                                    price = Double.parseDouble(priceText);
                                } catch (NumberFormatException ex) {
                                    continue;
                                }
                            }

                            // product link
                            Element linkEl = product.selectFirst("a[href]");
                            String productUrl = (linkEl != null) ? linkEl.absUrl("href") : url;
                            if (productUrl == null || productUrl.isEmpty()) productUrl = url;

                            Entry entry = new Entry(name, price, editionName, editionId, pageNumber, productUrl);
                            entry.extraNote = (printCode != null ? printCode : "");

                            // flags and matching
                            if (price > maxPrice) {
                                entry.skipped = true;
                            } else if (price >= OVERPRICE_THRESHOLD) {
                                entry.noMatch = true;
                            } else {
                                String konamiId = null;
                                if (printCode != null) konamiId = getPrintCodeToKonamiId().get(printCode);

                                Card card = null;
                                if (konamiId != null) {
                                    card = findCardById(maOuicheList, konamiId);
                                } else {
                                    String normalized = normalizeForCompare(name);
                                    if (!normalized.isEmpty()) {
                                        card = findCardByNormalizedName(maOuicheList, normalized, name);
                                    } else {
                                        card = findCardByName(maOuicheList, name);
                                    }
                                }

                                if (card != null) {
                                    entry.matched = true;
                                    String img = card.getImagePath();
                                    if (img != null) {
                                        entry.ouicheCount = ouicheCountMap.getOrDefault(img, 0);
                                    } else {
                                        entry.ouicheCount = 0;
                                    }
                                } else entry.noMatch = true;
                            }

                            if (entry.matched) {
                                collected.add(entry);
                            }
                        }
                    }

                    // decide whether to continue pagination
                    if (collected.isEmpty() && !usedFallbackTable) {
                        // if no items found on first page, stop
                        hasMorePages = false;
                    } else {
                        // increment page if we found items on this page
                        pageNumber++;
                        // Some pages may have fewer than 50 items; we continue until a page returns no items
                        // The loop will break when a page yields no products
                    }
                } // end pagination
            }

            // sort collected entries by price ascending
            collected.sort(Comparator.comparingDouble(e -> e.price));

            // write sorted entries and compute occurrence at display time
            for (Entry e : collected) {
                int occ = occurrenceCounts.getOrDefault(e.name, 0) + 1;
                occurrenceCounts.put(e.name, occ);

                String priceStr = String.format(Locale.US, "%.2f", e.price);
                StringBuilder line = new StringBuilder();
                line//.append("Card Name: ")
                        .append(e.name)
                        //.append(", Edition: ").append(e.editionName)
                        //.append(", Page: ").append(e.pageNumber)
                        .append(", Price: ").append(priceStr).append("€");

                if (e.skipped) {
                    line.append(" (skipped > maxPrice)");
                } else if (e.noMatch && !e.matched) {
                    line.append(" - NO MATCH");
                }

                if (occ > 1) {
                    line.append(", Occurrence: ").append(occ);
                }

                line.append(", InOuicheList: ").append(e.ouicheCount);

                line.append(" Link: ").append(e.productUrl);

                writer.write(line.toString() + "\n");
                System.out.println(line.toString());

                if (e.matched) {
                    String pageKey = "Edition: " + e.editionName + " - Page " + e.pageNumber;
                    result.computeIfAbsent(pageKey, k -> new ArrayList<>()).add(e.name);
                }
            }

            writer.write("\n");
            //} // end edition loop

        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    // Build a map imagePath -> count from the provided maOuicheList
    private static Map<String, Integer> buildOuicheCountMap(List<CardElement> maOuicheList) {
        Map<String, Integer> map = new HashMap<>();
        if (maOuicheList == null) return map;
        for (CardElement ce : maOuicheList) {
            if (ce == null || ce.getCard() == null) continue;
            String img = ce.getCard().getImagePath();
            if (img == null) continue;
            map.put(img, map.getOrDefault(img, 0) + 1);
        }
        return map;
    }

    private static class Entry {
        String name;
        double price;
        String editionName;
        String editionId;
        int pageNumber;
        String productUrl;
        boolean matched;
        boolean noMatch;
        boolean skipped;
        String extraNote;
        int ouicheCount = 0;

        Entry(String name, double price, String editionName, String editionId, int pageNumber, String productUrl) {
            this.name = name;
            this.price = price;
            this.editionName = editionName;
            this.editionId = editionId;
            this.pageNumber = pageNumber;
            this.productUrl = productUrl;
        }
    }


    // Normalizes a String: lowercase, strip diacritics, remove punctuation and extra spaces
    private static String normalizeForCompare(String s) {
        if (s == null) return "";
        String t = s.toLowerCase().trim();
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        t = t.replaceAll("[^\\p{Alnum}\\s]", "");
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    // Find by normalized name first, else fallback to original name comparison
    private static Card findCardByNormalizedName(List<CardElement> maOuicheList, String normalizedName, String originalName) {
        for (CardElement element : maOuicheList) {
            Card c = element.getCard();
            if (c.getName_EN() != null) {
                String nEN = normalizeForCompare(c.getName_EN());
                if (!nEN.isEmpty() && nEN.equals(normalizedName)) return c;
                if (nEN.isEmpty() && c.getName_EN().equalsIgnoreCase(originalName)) return c;
            }
            if (c.getName_FR() != null) {
                String nFR = normalizeForCompare(c.getName_FR());
                if (!nFR.isEmpty() && nFR.equals(normalizedName)) return c;
                if (nFR.isEmpty() && c.getName_FR().equalsIgnoreCase(originalName)) return c;
            }
        }
        return null;
    }

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