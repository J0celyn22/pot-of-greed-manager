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
    // Subcategory tables
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

    private static final List<String> TRAP_SUBCAT_ORDER = Arrays.asList(
            "Normal",       // 0
            "Continuous",   // 1
            "Counter"       // 2
    );

    // No instances.
    private CardSorter() {
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

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
     * Returns the main-type bucket: 0 = Monster, 1 = Spell, 2 = Trap, 3 = Other.
     *
     * @param card the card to classify
     * @return the main-type sort order index
     */
    private static int mainTypeOrder(Card card) {
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
        String cardType = card.getCardType();
        List<String> properties = card.getCardProperties();
        if (cardType == null || properties == null || properties.isEmpty()) {
            return 0;
        }
        if (cardType.contains("Monster")) {
            for (int i = MONSTER_SUBCAT_ORDER.size() - 1; i >= 0; i--) {
                if (properties.contains(MONSTER_SUBCAT_ORDER.get(i))) {
                    return i;
                }
            }
            return MONSTER_SUBCAT_ORDER.size(); // unknown → last
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
     * Returns the "effective level" for a monster card:
     * <ul>
     *   <li>Link monsters → {@link Card#getLinkVal()}</li>
     *   <li>Xyz  monsters → {@link Card#getRank()}</li>
     *   <li>All others    → {@link Card#getLevel()}</li>
     * </ul>
     * Returns 0 for non-monsters (unused in practice since the level sort is
     * only applied inside the monster main-type bucket).
     *
     * @param card the card whose effective level is to be determined
     * @return the effective level for sorting purposes
     */
    private static int effectiveLevel(Card card) {
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
        String name = card.getName_EN();
        return (name == null) ? "" : name;
    }

    // -------------------------------------------------------------------------
    // Comparator builder
    // -------------------------------------------------------------------------

    /**
     * Builds the {@link Comparator} corresponding to the requested {@link SortMode}.
     * All modes share the same top-level structure:
     * main type → subcategory → mode-specific tiebreakers.
     *
     * @param mode the desired sort mode
     * @return a fully chained comparator for {@link Card} objects
     */
    private static Comparator<Card> buildComparator(SortMode mode) {

        // Structural comparators shared by every mode
        Comparator<Card> byMainType = Comparator.comparingInt(CardSorter::mainTypeOrder);
        Comparator<Card> bySubcat = Comparator.comparingInt(CardSorter::subcatOrder);
        Comparator<Card> byNameAsc = Comparator.comparing(
                CardSorter::safeName, String.CASE_INSENSITIVE_ORDER);

        // All mode-specific lambdas are the final stage of the chain:
        //   byMainType → bySubcat → <mode-specific>
        // At that point both cards share the same main type and subcategory.
        // Lambda parameters (a, b) follow the standard Comparator convention and are kept as-is.

        switch (mode) {

            // ── Alphabetical ─────────────────────────────────────────────────
            // Name is the primary sort key.
            // Monster tiebreakers (same name can occur in "printed" mode):
            //   LVL descending, then ATK descending.
            case AZ:
                return byMainType.thenComparing(bySubcat).thenComparing((a, b) -> {
                    int nameCmp = safeName(a).compareToIgnoreCase(safeName(b));
                    if (nameCmp != 0) {
                        return nameCmp;
                    }
                    if (mainTypeOrder(a) == 0) {
                        int levelCmp = Integer.compare(effectiveLevel(b), effectiveLevel(a)); // desc
                        if (levelCmp != 0) {
                            return levelCmp;
                        }
                        return Integer.compare(b.getAtk(), a.getAtk()); // desc
                    }
                    return 0;
                });

            case ZA:
                return byMainType.thenComparing(bySubcat).thenComparing((a, b) -> {
                    int nameCmp = safeName(b).compareToIgnoreCase(safeName(a)); // reversed
                    if (nameCmp != 0) {
                        return nameCmp;
                    }
                    if (mainTypeOrder(a) == 0) {
                        int levelCmp = Integer.compare(effectiveLevel(b), effectiveLevel(a)); // desc
                        if (levelCmp != 0) {
                            return levelCmp;
                        }
                        return Integer.compare(b.getAtk(), a.getAtk()); // desc
                    }
                    return 0;
                });

            // ── ATK ──────────────────────────────────────────────────────────
            case ATK_DESC:
                return byMainType.thenComparing(bySubcat).thenComparing((a, b) -> {
                    if (mainTypeOrder(a) == 0) {
                        int atkCmp = Integer.compare(b.getAtk(), a.getAtk()); // highest first
                        if (atkCmp != 0) {
                            return atkCmp;
                        }
                    }
                    return safeName(a).compareToIgnoreCase(safeName(b));
                });

            case ATK_ASC:
                return byMainType.thenComparing(bySubcat).thenComparing((a, b) -> {
                    if (mainTypeOrder(a) == 0) {
                        int atkCmp = Integer.compare(a.getAtk(), b.getAtk()); // lowest first
                        if (atkCmp != 0) {
                            return atkCmp;
                        }
                    }
                    return safeName(a).compareToIgnoreCase(safeName(b));
                });

            // ── DEF ──────────────────────────────────────────────────────────
            case DEF_DESC:
                return byMainType.thenComparing(bySubcat).thenComparing((a, b) -> {
                    if (mainTypeOrder(a) == 0) {
                        int defCmp = Integer.compare(b.getDef(), a.getDef()); // highest first
                        if (defCmp != 0) {
                            return defCmp;
                        }
                    }
                    return safeName(a).compareToIgnoreCase(safeName(b));
                });

            case DEF_ASC:
                return byMainType.thenComparing(bySubcat).thenComparing((a, b) -> {
                    if (mainTypeOrder(a) == 0) {
                        int defCmp = Integer.compare(a.getDef(), b.getDef()); // lowest first
                        if (defCmp != 0) {
                            return defCmp;
                        }
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
                        int levelCmp = Integer.compare(effectiveLevel(b), effectiveLevel(a)); // highest first
                        if (levelCmp != 0) {
                            return levelCmp;
                        }
                        int nameCmp = safeName(a).compareToIgnoreCase(safeName(b));
                        if (nameCmp != 0) {
                            return nameCmp;
                        }
                        return Integer.compare(b.getAtk(), a.getAtk()); // ATK desc as final tiebreaker
                    }
                    return safeName(a).compareToIgnoreCase(safeName(b));
                });

            case LVL_ASC:
                return byMainType.thenComparing(bySubcat).thenComparing((a, b) -> {
                    if (mainTypeOrder(a) == 0) {
                        int levelCmp = Integer.compare(effectiveLevel(a), effectiveLevel(b)); // lowest first
                        if (levelCmp != 0) {
                            return levelCmp;
                        }
                        int nameCmp = safeName(a).compareToIgnoreCase(safeName(b));
                        if (nameCmp != 0) {
                            return nameCmp;
                        }
                        return Integer.compare(b.getAtk(), a.getAtk());
                    }
                    return safeName(a).compareToIgnoreCase(safeName(b));
                });

            default:
                return byMainType.thenComparing(bySubcat).thenComparing(byNameAsc);
        }
    }

    // -------------------------------------------------------------------------
    // Sort mode enum
    // -------------------------------------------------------------------------

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