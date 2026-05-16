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

    // ── Classification helpers ─────────────────────────────────────────────────
    private static final Comparator<CardElement> COMPARATOR = (a, b) -> {
        Card ca = (a == null) ? null : a.getCard();
        Card cb = (b == null) ? null : b.getCard();

        // Null guards: null elements sink to the bottom
        if (ca == null && cb == null) return 0;
        if (ca == null) return 1;
        if (cb == null) return -1;

        // 1. Main type
        int mt = Integer.compare(mainTypeOrder(ca), mainTypeOrder(cb));
        if (mt != 0) return mt;

        // 2. Subcategory
        int sc = Integer.compare(subcatOrder(ca), subcatOrder(cb));
        if (sc != 0) return sc;

        // 3. Monster-specific tiebreakers
        if (mainTypeOrder(ca) == 0) {
            int lvl = Integer.compare(effectiveLevel(cb), effectiveLevel(ca)); // desc
            if (lvl != 0) return lvl;
            int atk = Integer.compare(cb.getAtk(), ca.getAtk()); // desc
            if (atk != 0) return atk;
            int def = Integer.compare(cb.getDef(), ca.getDef()); // desc
            if (def != 0) return def;
        }

        // 4. Alphabetical (spell / trap / other fall through to here directly)
        return safeName(ca).compareToIgnoreCase(safeName(cb));
    };

    // No instances.
    private CardElementSorter() {
    }

    private static int mainTypeOrder(Card c) {
        if (c == null) return 3;
        String t = c.getCardType();
        if (t == null) return 3;
        if (t.contains("Monster")) return 0;
        if (t.contains("Spell")) return 1;
        if (t.contains("Trap")) return 2;
        return 3;
    }

    private static int subcatOrder(Card c) {
        if (c == null) return 0;
        String t = c.getCardType();
        List<String> props = c.getCardProperties();
        if (t == null || props == null || props.isEmpty()) return 0;

        if (t.contains("Monster")) {
            // Reverse scan: most specific property wins
            for (int i = MONSTER_SUBCAT_ORDER.size() - 1; i >= 0; i--) {
                if (props.contains(MONSTER_SUBCAT_ORDER.get(i))) return i;
            }
            return MONSTER_SUBCAT_ORDER.size(); // unknown → after Link
        }
        if (t.contains("Spell")) {
            for (int i = 0; i < SPELL_SUBCAT_ORDER.size(); i++) {
                if (props.contains(SPELL_SUBCAT_ORDER.get(i))) return i;
            }
            return SPELL_SUBCAT_ORDER.size();
        }
        if (t.contains("Trap")) {
            for (int i = 0; i < TRAP_SUBCAT_ORDER.size(); i++) {
                if (props.contains(TRAP_SUBCAT_ORDER.get(i))) return i;
            }
            return TRAP_SUBCAT_ORDER.size();
        }
        return 0;
    }

    // ── Comparator ────────────────────────────────────────────────────────────

    /**
     * Level / Rank / Link value — the effective "level" for sorting purposes.
     */
    private static int effectiveLevel(Card c) {
        if (c == null) return 0;
        List<String> props = c.getCardProperties();
        if (props != null) {
            if (props.contains("Link")) return c.getLinkVal();
            if (props.contains("Xyz")) return c.getRank();
        }
        return c.getLevel();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    private static String safeName(Card c) {
        if (c == null) return "";
        String n = c.getName_EN();
        return n == null ? "" : n;
    }

    /**
     * Sorts {@code elements} in-place.
     * Null elements are pushed to the end; the list itself is never replaced.
     *
     * @param elements the list to sort (may be empty, never {@code null})
     */
    public static void sortInPlace(List<CardElement> elements) {
        if (elements == null || elements.size() <= 1) return;
        elements.sort(COMPARATOR);
    }

    /**
     * Returns a new sorted list; the original is never mutated.
     *
     * @param elements source list (may be empty or {@code null})
     * @return a new {@link ArrayList} with the same elements in sorted order
     */
    public static List<CardElement> sorted(List<CardElement> elements) {
        if (elements == null) return new ArrayList<>();
        List<CardElement> result = new ArrayList<>(elements);
        result.sort(COMPARATOR);
        return result;
    }
}