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

    // ── Card classification ────────────────────────────────────────────────────

    // No instances.
    private DeckCompatibility() {
    }

    /**
     * Returns {@code true} when {@code card} must go in the Extra Deck
     * (Fusion / Synchro / Xyz / Link monster).
     */
    public static boolean isExtraDeckCard(Card card) {
        if (card == null) return false;
        List<String> props = card.getCardProperties();
        if (props == null) return false;
        return props.contains("Fusion")
                || props.contains("Synchro")
                || props.contains("Xyz")
                || props.contains("Link");
    }

    // ── Section name helpers ───────────────────────────────────────────────────

    /**
     * Returns {@code true} when {@code card} must go in the Main Deck
     * (anything that is <em>not</em> an Extra Deck monster).
     */
    public static boolean isMainDeckCard(Card card) {
        return !isExtraDeckCard(card);
    }

    /**
     * Returns {@code true} when {@code sectionName} (case-insensitive)
     * refers to the Extra Deck section.
     */
    public static boolean isExtraDeckSection(String sectionName) {
        if (sectionName == null) return false;
        String s = sectionName.trim().toLowerCase(java.util.Locale.ROOT);
        return s.equals("extra deck") || s.equals("extra");
    }

    /**
     * Returns {@code true} when {@code sectionName} refers to the Main Deck.
     */
    public static boolean isMainDeckSection(String sectionName) {
        if (sectionName == null) return false;
        String s = sectionName.trim().toLowerCase(java.util.Locale.ROOT);
        return s.equals("main deck") || s.equals("main");
    }

    // ── Compatibility checks ───────────────────────────────────────────────────

    /**
     * Returns {@code true} when {@code sectionName} refers to the Side Deck.
     */
    public static boolean isSideDeckSection(String sectionName) {
        if (sectionName == null) return false;
        String s = sectionName.trim().toLowerCase(java.util.Locale.ROOT);
        return s.equals("side deck") || s.equals("side");
    }

    /**
     * Returns {@code true} when ALL cards in {@code cards} are compatible with
     * the given {@code sectionName}.
     *
     * <p>Side Deck always returns {@code true}.
     */
    public static boolean allCompatibleWith(Collection<Card> cards, String sectionName) {
        if (cards == null || cards.isEmpty()) return true;
        if (isSideDeckSection(sectionName)) return true;
        if (isExtraDeckSection(sectionName)) {
            for (Card c : cards) if (!isExtraDeckCard(c)) return false;
            return true;
        }
        if (isMainDeckSection(sectionName)) {
            for (Card c : cards) if (!isMainDeckCard(c)) return false;
            return true;
        }
        return true; // non-deck section — no restriction
    }

    /**
     * Returns {@code true} when ANY card in {@code cards} is compatible with
     * the given {@code sectionName}.
     */
    public static boolean anyCompatibleWith(Collection<Card> cards, String sectionName) {
        if (cards == null || cards.isEmpty()) return false;
        if (isSideDeckSection(sectionName)) return true;
        for (Card c : cards) {
            if (isCompatibleWith(c, sectionName)) return true;
        }
        return false;
    }

    /**
     * Returns {@code true} when {@code card} is compatible with the deck section
     * identified by {@code sectionName}.
     */
    public static boolean isCompatibleWith(Card card, String sectionName) {
        if (card == null) return false;
        if (isSideDeckSection(sectionName)) return true;
        if (isExtraDeckSection(sectionName)) return isExtraDeckCard(card);
        if (isMainDeckSection(sectionName)) return isMainDeckCard(card);
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
     * <p>
     * Returns {@code null} if the section is not a main/extra deck section or if
     * {@code card} is {@code null}.
     */
    public static String redirectSection(Card card, String sectionName) {
        if (card == null || sectionName == null) return null;
        if (isMainDeckSection(sectionName) && isExtraDeckCard(card)) return "Extra Deck";
        if (isExtraDeckSection(sectionName) && isMainDeckCard(card)) return "Main Deck";
        return null;
    }
}