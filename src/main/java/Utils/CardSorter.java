package Utils;

import Model.CardsLists.Card;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * CardSorter
 * <p>
 * Sorts a list of {@link Card} objects according to the following hierarchy:
 *
 * <ol>
 *   <li><b>Main type</b>: Monster → Spell → Trap → Other</li>
 *   <li><b>Subcategory</b> (within each main type):
 *     <ul>
 *       <li>Monsters : Normal → Effect → Ritual → Pendulum → Fusion → Synchro → Link → (others)</li>
 *       <li>Spells   : Normal → Continuous → Quick-Play → Equip → Field → Ritual → (others)</li>
 *       <li>Traps    : Normal → Continuous → Counter → (others)</li>
 *     </ul>
 *   </li>
 *   <li><b>Within a subcategory</b>, the order depends on {@link SortMode}:
 *     <ul>
 *       <li>AZ / ZA        — name alphabetical (asc/desc); monster tiebreakers: LVL desc, ATK desc</li>
 *       <li>ATK_DESC / ATK_ASC — ATK for monsters, ties by name; spells/traps fall back to name</li>
 *       <li>DEF_DESC / DEF_ASC — same as ATK but using DEF</li>
 *       <li>LVL_DESC / LVL_ASC — effective level for monsters (level/rank/link), ties: name asc,
 *                                 then ATK desc; spells/traps fall back to name</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h3>Effective level for monsters</h3>
 * <ul>
 *   <li>Link monsters  → {@code linkVal}</li>
 *   <li>Xyz  monsters  → {@code rank}</li>
 *   <li>All others     → {@code level}</li>
 * </ul>
 *
 * <h3>Subcategory assignment for monsters with multiple properties</h3>
 * The scan runs in <em>reverse</em> display order (Link first, Normal last) so the most
 * specific property wins (e.g. a Pendulum-Effect card lands in the Pendulum bucket).
 */
public final class CardSorter {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Monster display order. Subcategory assignment uses <em>reverse</em> scan
     * so the most specific property wins when a card has several.
     */
    private static final List<String> MONSTER_SUBCAT_ORDER = Arrays.asList(
            "Normal",    // 0
            "Effect",    // 1
            "Ritual",    // 2
            "Pendulum",  // 3
            "Fusion",    // 4
            "Synchro",   // 5
            "Link"       // 6
            // index 7+ → "other" subcategory (e.g. Xyz)
    );
    private static final List<String> SPELL_SUBCAT_ORDER = Arrays.asList(
            "Normal",       // 0
            "Continuous",   // 1
            "Quick-Play",   // 2
            "Equip",        // 3
            "Field",        // 4
            "Ritual"        // 5
    );

    // -------------------------------------------------------------------------
    // Subcategory tables
    // -------------------------------------------------------------------------
    private static final List<String> TRAP_SUBCAT_ORDER = Arrays.asList(
            "Normal",       // 0
            "Continuous",   // 1
            "Counter"       // 2
    );

    // No instances.
    private CardSorter() {
    }

    /**
     * Returns a new sorted list; the original list is never mutated.
     *
     * @param cards cards to sort (may be empty, never {@code null})
     * @param mode  the desired sort mode
     * @return a new {@link ArrayList} containing the same cards in sorted order
     */
    public static List<Card> sort(List<Card> cards, SortMode mode) {
        List<Card> result = new ArrayList<>(cards);
        result.sort(buildComparator(mode));
        return result;
    }

    // -------------------------------------------------------------------------
    // Classification helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the main-type bucket: 0=Monster, 1=Spell, 2=Trap, 3=Other.
     */
    private static int mainTypeOrder(Card c) {
        String t = c.getCardType();
        if (t == null) return 3;
        if (t.contains("Monster")) return 0;
        if (t.contains("Spell")) return 1;
        if (t.contains("Trap")) return 2;
        return 3;
    }

    /**
     * Returns the subcategory sort index within the card's main type.
     * For monsters the scan is in <em>reverse</em> order so more specific
     * properties (Link, Synchro…) beat generic ones (Effect, Normal).
     */
    private static int subcatOrder(Card c) {
        String t = c.getCardType();
        List<String> props = c.getCardProperties();
        if (t == null || props == null || props.isEmpty()) return 0;

        if (t.contains("Monster")) {
            for (int i = MONSTER_SUBCAT_ORDER.size() - 1; i >= 0; i--) {
                if (props.contains(MONSTER_SUBCAT_ORDER.get(i))) return i;
            }
            return MONSTER_SUBCAT_ORDER.size(); // unknown → last
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

    /**
     * Returns the "effective level" for a monster card:
     * <ul>
     *   <li>Link monsters → {@link Card#getLinkVal()}</li>
     *   <li>Xyz  monsters → {@link Card#getRank()}</li>
     *   <li>All others    → {@link Card#getLevel()}</li>
     * </ul>
     * Returns 0 for non-monsters (unused in practice since the level sort is
     * only applied inside the monster main-type bucket).
     */
    private static int effectiveLevel(Card c) {
        List<String> props = c.getCardProperties();
        if (props != null) {
            if (props.contains("Link")) return c.getLinkVal();
            if (props.contains("Xyz")) return c.getRank();
        }
        return c.getLevel();
    }

    /**
     * Null-safe English name for comparisons.
     */
    private static String safeName(Card c) {
        String n = c.getName_EN();
        return (n == null) ? "" : n;
    }

    // -------------------------------------------------------------------------
    // Comparator builder
    // -------------------------------------------------------------------------

    private static Comparator<Card> buildComparator(SortMode mode) {

        // Structural comparators shared by every mode
        Comparator<Card> byMainType = Comparator.comparingInt(CardSorter::mainTypeOrder);
        Comparator<Card> bySubcat = Comparator.comparingInt(CardSorter::subcatOrder);
        Comparator<Card> byNameAsc = Comparator.comparing(
                CardSorter::safeName, String.CASE_INSENSITIVE_ORDER);

        // All mode-specific lambdas below are the final stage of the chain
        //   byMainType → bySubcat → <mode-specific>
        // At that point both cards share the same main type and subcategory,
        // so mainTypeOrder(a) == mainTypeOrder(b) is always true.

        switch (mode) {

            // ── Alphabetical ─────────────────────────────────────────────────
            // Name is the primary sort key.
            // Monster tiebreakers (same name can occur in "printed" mode):
            //   LVL descending, then ATK descending.
            case AZ:
                return byMainType.thenComparing(bySubcat).thenComparing((a, b) -> {
                    int nameCmp = safeName(a).compareToIgnoreCase(safeName(b));
                    if (nameCmp != 0) return nameCmp;
                    if (mainTypeOrder(a) == 0) {
                        int lvlCmp = Integer.compare(effectiveLevel(b), effectiveLevel(a)); // desc
                        if (lvlCmp != 0) return lvlCmp;
                        return Integer.compare(b.getAtk(), a.getAtk()); // desc
                    }
                    return 0;
                });

            case ZA:
                return byMainType.thenComparing(bySubcat).thenComparing((a, b) -> {
                    int nameCmp = safeName(b).compareToIgnoreCase(safeName(a)); // reversed
                    if (nameCmp != 0) return nameCmp;
                    if (mainTypeOrder(a) == 0) {
                        int lvlCmp = Integer.compare(effectiveLevel(b), effectiveLevel(a)); // desc
                        if (lvlCmp != 0) return lvlCmp;
                        return Integer.compare(b.getAtk(), a.getAtk()); // desc
                    }
                    return 0;
                });

            // ── ATK ──────────────────────────────────────────────────────────
            case ATK_DESC:
                return byMainType.thenComparing(bySubcat).thenComparing((a, b) -> {
                    if (mainTypeOrder(a) == 0) {
                        int cmp = Integer.compare(b.getAtk(), a.getAtk()); // highest first
                        if (cmp != 0) return cmp;
                    }
                    return safeName(a).compareToIgnoreCase(safeName(b));
                });

            case ATK_ASC:
                return byMainType.thenComparing(bySubcat).thenComparing((a, b) -> {
                    if (mainTypeOrder(a) == 0) {
                        int cmp = Integer.compare(a.getAtk(), b.getAtk()); // lowest first
                        if (cmp != 0) return cmp;
                    }
                    return safeName(a).compareToIgnoreCase(safeName(b));
                });

            // ── DEF ──────────────────────────────────────────────────────────
            case DEF_DESC:
                return byMainType.thenComparing(bySubcat).thenComparing((a, b) -> {
                    if (mainTypeOrder(a) == 0) {
                        int cmp = Integer.compare(b.getDef(), a.getDef()); // highest first
                        if (cmp != 0) return cmp;
                    }
                    return safeName(a).compareToIgnoreCase(safeName(b));
                });

            case DEF_ASC:
                return byMainType.thenComparing(bySubcat).thenComparing((a, b) -> {
                    if (mainTypeOrder(a) == 0) {
                        int cmp = Integer.compare(a.getDef(), b.getDef()); // lowest first
                        if (cmp != 0) return cmp;
                    }
                    return safeName(a).compareToIgnoreCase(safeName(b));
                });

            // ── LVL (level / rank / link value) ──────────────────────────────
            // Primary: effective level.
            // Tiebreaker 1: name alphabetical asc.
            // Tiebreaker 2: ATK descending (highest first).
            // Spells / traps / others fall back to name only (no level/ATK).
            case LVL_DESC:
                return byMainType.thenComparing(bySubcat).thenComparing((a, b) -> {
                    if (mainTypeOrder(a) == 0) {
                        int lvlCmp = Integer.compare(effectiveLevel(b), effectiveLevel(a)); // highest first
                        if (lvlCmp != 0) return lvlCmp;
                        int nameCmp = safeName(a).compareToIgnoreCase(safeName(b));
                        if (nameCmp != 0) return nameCmp;
                        return Integer.compare(b.getAtk(), a.getAtk()); // ATK desc as final tiebreaker
                    }
                    return safeName(a).compareToIgnoreCase(safeName(b));
                });

            case LVL_ASC:
                return byMainType.thenComparing(bySubcat).thenComparing((a, b) -> {
                    if (mainTypeOrder(a) == 0) {
                        int lvlCmp = Integer.compare(effectiveLevel(a), effectiveLevel(b)); // lowest first
                        if (lvlCmp != 0) return lvlCmp;
                        int nameCmp = safeName(a).compareToIgnoreCase(safeName(b));
                        if (nameCmp != 0) return nameCmp;
                        return Integer.compare(b.getAtk(), a.getAtk());
                    }
                    return safeName(a).compareToIgnoreCase(safeName(b));
                });

            default:
                return byMainType.thenComparing(bySubcat).thenComparing(byNameAsc);
        }
    }

    /**
     * All supported sort modes, one per button label.
     */
    public enum SortMode {
        /**
         * Alphabetical ascending (A→Z); monster tiebreakers: LVL desc, then ATK desc.
         */
        AZ,
        /**
         * Alphabetical descending (Z→A); same tiebreakers as AZ.
         */
        ZA,
        /**
         * Highest ATK first; ties broken by name asc.
         */
        ATK_DESC,
        /**
         * Lowest ATK first; ties broken by name asc.
         */
        ATK_ASC,
        /**
         * Highest DEF first; ties broken by name asc.
         */
        DEF_DESC,
        /**
         * Lowest DEF first; ties broken by name asc.
         */
        DEF_ASC,
        /**
         * Highest effective level first; ties: name asc, then ATK desc.
         */
        LVL_DESC,
        /**
         * Lowest effective level first; ties: name asc, then ATK desc.
         */
        LVL_ASC
    }
}