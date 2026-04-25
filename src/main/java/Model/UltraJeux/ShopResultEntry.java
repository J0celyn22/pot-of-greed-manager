package Model.UltraJeux;

import Model.CardsLists.Card;

/**
 * ShopResultEntry.java
 * <p>
 * Holds a single card result scraped from a shop's website, enriched with its
 * OuicheList count and occurrence index (1-based, within same card name group).
 * Produced by CardScraper and consumed by ShopResultListCell for display.
 */
public class ShopResultEntry {

    private final Card card;
    private final String name;
    private final double price;
    private final int ouicheCount;
    private final String productUrl;
    /**
     * 1-based index of this entry within the group of entries sharing the same card name.
     */
    private final int occurrence;

    public ShopResultEntry(Card card, String name, double price,
                           int ouicheCount, String productUrl, int occurrence) {
        this.card = card;
        this.name = name;
        this.price = price;
        this.ouicheCount = ouicheCount;
        this.productUrl = productUrl;
        this.occurrence = occurrence;
    }

    /**
     * The matched Card object (never null in practice – only matched entries are stored).
     */
    public Card getCard() {
        return card;
    }

    /**
     * Raw name as scraped from the website.
     */
    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    /**
     * Number of times this card appears in the OuicheList.
     */
    public int getOuicheCount() {
        return ouicheCount;
    }

    /**
     * Product page URL on the shop's website.
     */
    public String getProductUrl() {
        return productUrl;
    }

    /**
     * 1-based occurrence index within the group of entries for this card name.
     * 1 = cheapest / first found, 2 = second copy, etc.
     */
    public int getOccurrence() {
        return occurrence;
    }
}