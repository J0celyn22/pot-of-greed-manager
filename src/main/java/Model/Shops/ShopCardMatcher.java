package Model.Shops;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ShopCardMatcher.java
 * <p>
 * Shared logic for resolving a card name scraped from a shop's website against
 * the user's OuicheList. Used by every shop-specific CardScraper (UltraJeux,
 * CardMarket, ...) so the matching rules stay identical across shops.
 */
public final class ShopCardMatcher {

    private ShopCardMatcher() {
    }

    /**
     * Builds a map of imagePath -> number of copies of that card in the OuicheList,
     * used to report how many copies of a matched card the user already owns.
     */
    public static Map<String, Integer> buildOuicheCountMap(List<CardElement> maOuicheList) {
        Map<String, Integer> countMap = new HashMap<>();
        if (maOuicheList == null) {
            return countMap;
        }
        for (CardElement cardElement : maOuicheList) {
            if (cardElement == null || cardElement.getCard() == null) {
                continue;
            }
            String imagePath = cardElement.getCard().getImagePath();
            if (imagePath == null) {
                continue;
            }
            countMap.put(imagePath, countMap.getOrDefault(imagePath, 0) + 1);
        }
        return countMap;
    }

    /**
     * Normalizes a String for name comparison: lowercase, strip diacritics,
     * remove punctuation, collapse extra spaces.
     */
    public static String normalizeForCompare(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.toLowerCase().trim();
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        normalized = normalized.replaceAll("[^\\p{Alnum}\\s]", "");
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    /**
     * Finds a card in the OuicheList whose English or French name matches
     * {@code normalizedName} once both sides are normalized (see {@link #normalizeForCompare}).
     */
    public static Card findCardByNormalizedName(List<CardElement> maOuicheList,
                                                String normalizedName, String originalName) {
        for (CardElement cardElement : maOuicheList) {
            Card card = cardElement.getCard();
            if (card.getName_EN() != null) {
                String normalizedEnglishName = normalizeForCompare(card.getName_EN());
                if (!normalizedEnglishName.isEmpty() && normalizedEnglishName.equals(normalizedName)) {
                    return card;
                }
                if (normalizedEnglishName.isEmpty() && card.getName_EN().equalsIgnoreCase(originalName)) {
                    return card;
                }
            }
            if (card.getName_FR() != null) {
                String normalizedFrenchName = normalizeForCompare(card.getName_FR());
                if (!normalizedFrenchName.isEmpty() && normalizedFrenchName.equals(normalizedName)) {
                    return card;
                }
                if (normalizedFrenchName.isEmpty() && card.getName_FR().equalsIgnoreCase(originalName)) {
                    return card;
                }
            }
        }
        return null;
    }

    /**
     * Finds a card in the OuicheList by exact (unnormalized) English or French name match.
     */
    public static Card findCardByName(List<CardElement> maOuicheList, String name) {
        for (CardElement cardElement : maOuicheList) {
            Card card = cardElement.getCard();
            if (card.getName_EN() != null && card.getName_EN().equals(name)) {
                return card;
            }
            if (card.getName_FR() != null && card.getName_FR().equals(name)) {
                return card;
            }
        }
        return null;
    }

    /**
     * Finds a card in the OuicheList by exact Konami ID match.
     */
    public static Card findCardById(List<CardElement> maOuicheList, String konamiId) {
        for (CardElement cardElement : maOuicheList) {
            if (cardElement.getCard().getKonamiId() != null
                    && cardElement.getCard().getKonamiId().equals(konamiId)) {
                return cardElement.getCard();
            }
        }
        return null;
    }
}
