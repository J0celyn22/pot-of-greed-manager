package Model.CardMarket;

/**
 * CardMarketSeller.java
 * <p>
 * A single CardMarket seller that the Shops tab can scrape. Kept separate from
 * the display name so a seller can be relabeled later without touching their
 * CardMarket username.
 * <p>
 * To add a new seller, add one entry to {@link CardMarketSellers#ALL_SELLERS}.
 */
public class CardMarketSeller {

    private final String displayName;
    private final String username;

    public CardMarketSeller(String displayName, String username) {
        this.displayName = displayName;
        this.username = username;
    }

    /**
     * Name shown to the user in the seller dropdown.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * CardMarket username as it appears in the seller's offers URL:
     * https://www.cardmarket.com/en/YuGiOh/Users/&lt;username&gt;/Offers/Singles
     */
    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
