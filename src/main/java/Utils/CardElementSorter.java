package Utils;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * CardElementSorter
 * <p>
 * Sorts {@link CardElement} lists for the middle-pane views
 * (Decks &amp; Collections tab: Deck sections, "Cards", "Cards not to add").
 *
 * <h3>Sort order</h3>
 * <ol>
 *   <li><b>Main type</b>: Monster → Spell → Trap → Other</li>
 *   <li><b>Subcategory</b> within each type:
 *     <ul>
 *       <li>Monsters : Normal → Effect → Ritual → Pendulum → Fusion → Synchro → Link → (others)</li>
 *       <li>Spells   : Normal → Continuous → Quick-Play → Equip → Field → Ritual → (others)</li>
 *       <li>Traps    : Normal → Continuous → Counter → (others)</li>
 *     </ul>
 *   </li>
 *   <li><b>Within a monster subcategory</b>:
 *     effective level descending → ATK descending → DEF descending → name ascending</li>
 *   <li><b>Within a spell / trap subcategory</b>:
 *     name ascending</li>
 * </ol>
 *
 * <h3>Effective level</h3>
 * <ul>
 *   <li>Link monsters → {@link Card#getLinkVal()}</li>
 *   <li>Xyz  monsters → {@link Card#getRank()}</li>
 *   <li>All others    → {@link Card#getLevel()}</li>
 * </ul>
 *
 * <h3>Subcategory assignment for monsters</h3>
 * The scan runs in <em>reverse</em> order (Link first, Normal last) so that
 * multi-property cards (e.g. Pendulum-Effect) land in the most specific bucket.
 */
public final class CardElementSorter {

    // ── Subcategory tables ─────────────────────────────────────────────────────

    private static final List<String> MONSTER_SUBCAT_ORDER = Arrays.asList(
            "Normal",    // 0
            "Effect",    // 1
            "Ritual",    // 2
            "Pendulum",  // 3
            "Fusion",    // 4
            "Synchro",   // 5
            "Link"       // 6
            // 7+ → unknown subcategory, sorted after Link
    );

    private static final List<String> SPELL_SUBCAT_ORDER = Arrays.asList(
            "Normal",       // 0
            "Continuous",   // 1
            "Quick-Play",   // 2
            "Equip",        // 3
            "Field",        // 4
            "Ritual"        // 5
    );

    private static final List<String> TRAP_SUBCAT_ORDER = Arrays.asList(
            "Normal",       // 0
            "Continuous",   // 1
            "Counter"       // 2
    );

    // ── Comparator ────────────────────────────────────────────────────────────

    /**
     * The single shared comparator used for all sorting operations.
     * Lambda parameters (a, b) follow the standard {@link Comparator} convention
     * and are kept as-is; local result variables use descriptive names.
     */
    private static final Comparator<CardElement> COMPARATOR = (a, b) -> {
        Card cardA = (a == null) ? null : a.getCard();
        Card cardB = (b == null) ? null : b.getCard();

        // Null guards: null elements sink to the bottom
        if (cardA == null && cardB == null) {
            return 0;
        }
        if (cardA == null) {
            return 1;
        }
        if (cardB == null) {
            return -1;
        }

        // 1. Main type
        int mainTypeCmp = Integer.compare(mainTypeOrder(cardA), mainTypeOrder(cardB));
        if (mainTypeCmp != 0) {
            return mainTypeCmp;
        }

        // 2. Subcategory
        int subcatCmp = Integer.compare(subcatOrder(cardA), subcatOrder(cardB));
        if (subcatCmp != 0) {
            return subcatCmp;
        }

        // 3. Monster-specific tiebreakers
        if (mainTypeOrder(cardA) == 0) {
            int levelCmp = Integer.compare(effectiveLevel(cardB), effectiveLevel(cardA)); // desc
            if (levelCmp != 0) {
                return levelCmp;
            }
            int atkCmp = Integer.compare(cardB.getAtk(), cardA.getAtk()); // desc
            if (atkCmp != 0) {
                return atkCmp;
            }
            int defCmp = Integer.compare(cardB.getDef(), cardA.getDef()); // desc
            if (defCmp != 0) {
                return defCmp;
            }
        }

        // 4. Alphabetical (spell / trap / other fall through to here directly)
        return safeName(cardA).compareToIgnoreCase(safeName(cardB));
    };

    // No instances.
    private CardElementSorter() {
    }

    // ── Classification helpers ─────────────────────────────────────────────────

    /**
     * Returns the main-type bucket: 0 = Monster, 1 = Spell, 2 = Trap, 3 = Other.
     *
     * @param card the card to classify
     * @return the main-type sort order index
     */
    private static int mainTypeOrder(Card card) {
        if (card == null) {
            return 3;
        }
        String cardType = card.getCardType();
        if (cardType == null) {
            return 3;
        }
        if (cardType.contains("Monster")) {
            return 0;
        }
        if (cardType.contains("Spell")) {
            return 1;
        }
        if (cardType.contains("Trap")) {
            return 2;
        }
        return 3;
    }

    /**
     * Returns the subcategory sort index within the card's main type.
     * For monsters the scan is in <em>reverse</em> order so more specific
     * properties (Link, Synchro…) beat generic ones (Effect, Normal).
     *
     * @param card the card to classify
     * @return the subcategory sort order index
     */
    private static int subcatOrder(Card card) {
        if (card == null) {
            return 0;
        }
        String cardType = card.getCardType();
        List<String> properties = card.getCardProperties();
        if (cardType == null || properties == null || properties.isEmpty()) {
            return 0;
        }
        if (cardType.contains("Monster")) {
            // Reverse scan: most specific property wins
            for (int i = MONSTER_SUBCAT_ORDER.size() - 1; i >= 0; i--) {
                if (properties.contains(MONSTER_SUBCAT_ORDER.get(i))) {
                    return i;
                }
            }
            return MONSTER_SUBCAT_ORDER.size(); // unknown → after Link
        }
        if (cardType.contains("Spell")) {
            for (int i = 0; i < SPELL_SUBCAT_ORDER.size(); i++) {
                if (properties.contains(SPELL_SUBCAT_ORDER.get(i))) {
                    return i;
                }
            }
            return SPELL_SUBCAT_ORDER.size();
        }
        if (cardType.contains("Trap")) {
            for (int i = 0; i < TRAP_SUBCAT_ORDER.size(); i++) {
                if (properties.contains(TRAP_SUBCAT_ORDER.get(i))) {
                    return i;
                }
            }
            return TRAP_SUBCAT_ORDER.size();
        }
        return 0;
    }

    /**
     * Returns the effective level for sorting purposes:
     * <ul>
     *   <li>Link monsters → {@link Card#getLinkVal()}</li>
     *   <li>Xyz  monsters → {@link Card#getRank()}</li>
     *   <li>All others    → {@link Card#getLevel()}</li>
     * </ul>
     *
     * @param card the card whose effective level is to be determined
     * @return the effective level, or 0 if {@code card} is {@code null}
     */
    private static int effectiveLevel(Card card) {
        if (card == null) {
            return 0;
        }
        List<String> properties = card.getCardProperties();
        if (properties != null) {
            if (properties.contains("Link")) {
                return card.getLinkVal();
            }
            if (properties.contains("Xyz")) {
                return card.getRank();
            }
        }
        return card.getLevel();
    }

    /**
     * Returns a null-safe English name for comparisons.
     *
     * @param card the card whose name is needed
     * @return the English name, or an empty string if {@code null}
     */
    private static String safeName(Card card) {
        if (card == null) {
            return "";
        }
        String name = card.getName_EN();
        return name == null ? "" : name;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sorts {@code elements} in-place.
     * Null elements are pushed to the end; the list itself is never replaced.
     *
     * @param elements the list to sort (may be empty, never {@code null})
     */
    public static void sortInPlace(List<CardElement> elements) {
        if (elements == null || elements.size() <= 1) {
            return;
        }
        elements.sort(COMPARATOR);
    }

    /**
     * Returns a new sorted list; the original is never mutated.
     *
     * @param elements source list (may be empty or {@code null})
     * @return a new {@link ArrayList} with the same elements in sorted order
     */
    public static List<CardElement> sorted(List<CardElement> elements) {
        if (elements == null) {
            return new ArrayList<>();
        }
        List<CardElement> result = new ArrayList<>(elements);
        result.sort(COMPARATOR);
        return result;
    }
}