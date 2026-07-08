package Model.CardMarket;

import java.util.List;

/**
 * CardMarketSellers.java
 * <p>
 * Registry of CardMarket sellers the Shops tab's CardMarket dropdown offers.
 * <p>
 * To add a seller, add one entry to {@link #ALL_SELLERS} below — the dropdown
 * is populated directly from this list, so no other code needs to change.
 */
public final class CardMarketSellers {

    public static final List<CardMarketSeller> ALL_SELLERS = List.of(
            new CardMarketSeller("DateACard", "DateACard")
    );

    private CardMarketSellers() {
    }
}
