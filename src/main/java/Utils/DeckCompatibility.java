package Utils;

import Model.CardsLists.Card;

import java.util.Collection;
import java.util.List;

/**
 * DeckCompatibility
 * <p>
 * Single source of truth for the Yu-Gi-Oh deck-building rules that govern
 * which cards are legal in the Main Deck vs the Extra Deck:
 *
 * <ul>
 *   <li><b>Extra-deck monsters</b> (Fusion / Synchro / Xyz / Link): may only go in
 *       the Extra Deck. They are illegal in the Main Deck.</li>
 *   <li><b>Main-deck cards</b> (all other monsters, Spells, Traps): may not go in
 *       the Extra Deck.</li>
 *   <li><b>Side Deck</b>: accepts every card type — no restrictions.</li>
 * </ul>
 */
public final class DeckCompatibility {

    // No instances.
    private DeckCompatibility() {
    }

    // ── Card classification ────────────────────────────────────────────────────

    /**
     * Returns {@code true} when {@code card} must go in the Extra Deck
     * (Fusion / Synchro / Xyz / Link monster).
     *
     * @param card the card to test
     * @return {@code true} if the card is an Extra Deck monster
     */
    public static boolean isExtraDeckCard(Card card) {
        if (card == null) {
            return false;
        }
        List<String> properties = card.getCardProperties();
        if (properties == null) {
            return false;
        }
        return properties.contains("Fusion")
                || properties.contains("Synchro")
                || properties.contains("Xyz")
                || properties.contains("Link");
    }

    /**
     * Returns {@code true} when {@code card} must go in the Main Deck
     * (anything that is <em>not</em> an Extra Deck monster).
     *
     * @param card the card to test
     * @return {@code true} if the card belongs in the Main Deck
     */
    public static boolean isMainDeckCard(Card card) {
        return !isExtraDeckCard(card);
    }

    // ── Section name helpers ───────────────────────────────────────────────────

    /**
     * Returns {@code true} when {@code sectionName} (case-insensitive)
     * refers to the Extra Deck section.
     *
     * @param sectionName the section name to test
     * @return {@code true} if the name identifies the Extra Deck
     */
    public static boolean isExtraDeckSection(String sectionName) {
        if (sectionName == null) {
            return false;
        }
        String normalizedName = sectionName.trim().toLowerCase(java.util.Locale.ROOT);
        return normalizedName.equals("extra deck") || normalizedName.equals("extra");
    }

    /**
     * Returns {@code true} when {@code sectionName} refers to the Main Deck.
     *
     * @param sectionName the section name to test
     * @return {@code true} if the name identifies the Main Deck
     */
    public static boolean isMainDeckSection(String sectionName) {
        if (sectionName == null) {
            return false;
        }
        String normalizedName = sectionName.trim().toLowerCase(java.util.Locale.ROOT);
        return normalizedName.equals("main deck") || normalizedName.equals("main");
    }

    /**
     * Returns {@code true} when {@code sectionName} refers to the Side Deck.
     *
     * @param sectionName the section name to test
     * @return {@code true} if the name identifies the Side Deck
     */
    public static boolean isSideDeckSection(String sectionName) {
        if (sectionName == null) {
            return false;
        }
        String normalizedName = sectionName.trim().toLowerCase(java.util.Locale.ROOT);
        return normalizedName.equals("side deck") || normalizedName.equals("side");
    }

    // ── Compatibility checks ───────────────────────────────────────────────────

    /**
     * Returns {@code true} when ALL cards in {@code cards} are compatible with
     * the given {@code sectionName}.
     *
     * <p>Side Deck always returns {@code true}.
     *
     * @param cards       the cards to test
     * @param sectionName the target deck section name
     * @return {@code true} if every card is compatible with the section
     */
    public static boolean allCompatibleWith(Collection<Card> cards, String sectionName) {
        if (cards == null || cards.isEmpty()) {
            return true;
        }
        if (isSideDeckSection(sectionName)) {
            return true;
        }
        if (isExtraDeckSection(sectionName)) {
            for (Card card : cards) {
                if (!isExtraDeckCard(card)) {
                    return false;
                }
            }
            return true;
        }
        if (isMainDeckSection(sectionName)) {
            for (Card card : cards) {
                if (!isMainDeckCard(card)) {
                    return false;
                }
            }
            return true;
        }
        return true; // non-deck section — no restriction
    }

    /**
     * Returns {@code true} when ANY card in {@code cards} is compatible with
     * the given {@code sectionName}.
     *
     * @param cards       the cards to test
     * @param sectionName the target deck section name
     * @return {@code true} if at least one card is compatible with the section
     */
    public static boolean anyCompatibleWith(Collection<Card> cards, String sectionName) {
        if (cards == null || cards.isEmpty()) {
            return false;
        }
        if (isSideDeckSection(sectionName)) {
            return true;
        }
        for (Card card : cards) {
            if (isCompatibleWith(card, sectionName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when {@code card} is compatible with the deck section
     * identified by {@code sectionName}.
     *
     * @param card        the card to test
     * @param sectionName the target deck section name
     * @return {@code true} if the card may be placed in the given section
     */
    public static boolean isCompatibleWith(Card card, String sectionName) {
        if (card == null) {
            return false;
        }
        if (isSideDeckSection(sectionName)) {
            return true;
        }
        if (isExtraDeckSection(sectionName)) {
            return isExtraDeckCard(card);
        }
        if (isMainDeckSection(sectionName)) {
            return isMainDeckCard(card);
        }
        return true; // non-deck section
    }

    /**
     * Given a card and a target section that is incompatible, returns the name of
     * the "other" deck list that should receive the card instead.
     *
     * <ul>
     *   <li>Extra-deck card dropped onto Main Deck → redirect to {@code "Extra Deck"}</li>
     *   <li>Main-deck card dropped onto Extra Deck → redirect to {@code "Main Deck"}</li>
     * </ul>
     *
     * <p>Returns {@code null} if the section is not a main/extra deck section or if
     * {@code card} is {@code null}.
     *
     * @param card        the card that was dropped
     * @param sectionName the section onto which the card was dropped
     * @return the name of the correct target section, or {@code null} if no redirect is needed
     */
    public static String redirectSection(Card card, String sectionName) {
        if (card == null || sectionName == null) {
            return null;
        }
        if (isMainDeckSection(sectionName) && isExtraDeckCard(card)) {
            return "Extra Deck";
        }
        if (isExtraDeckSection(sectionName) && isMainDeckCard(card)) {
            return "Main Deck";
        }
        return null;
    }
}