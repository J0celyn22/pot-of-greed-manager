package Utils;

import Model.CardsLists.Card;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Partition-based tests for {@link CardSorter}.
 * <p>
 * See {@link CardElementSorterTest}'s class javadoc for the note on shared
 * logic between the two sorter classes. What's unique here is the 8
 * {@link CardSorter.SortMode} variants and — worth flagging explicitly — a
 * real asymmetry versus {@code CardElementSorter}: none of
 * {@code mainTypeOrder}, {@code effectiveLevel}, {@code safeName}, or
 * {@code sort()} itself null-check their {@code Card}/{@code List}
 * arguments here, unlike the {@code CardElement}-based sibling, which
 * explicitly guards every one of these. A {@code null} list or a
 * {@code null} entry inside it crashes instead of degrading gracefully.
 * Documented as current behavior below, not patched.
 */
class CardSorterTest {

    private static Card monster(String name, String subtype, int level, int atk, int def) {
        Card card = new Card();
        card.setCardType("Effect Monster");
        card.setCardProperties(List.of(subtype));
        card.setName_EN(name);
        card.setLevel(level);
        card.setAtk(atk);
        card.setDef(def);
        return card;
    }

    private static Card spellOrTrap(String cardType, String subtype, String name) {
        Card card = new Card();
        card.setCardType(cardType);
        card.setCardProperties(List.of(subtype));
        card.setName_EN(name);
        return card;
    }

    // ── Null-handling asymmetry vs. CardElementSorter ───────────────────────

    @Test
    void sort_nullList_currentlyThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> CardSorter.sort(null, CardSorter.SortMode.AZ));
    }

    @Test
    void sort_nullMode_currentlyThrowsNullPointerException() {
        List<Card> cards = List.of(monster("A", "Normal", 4, 0, 0));
        assertThrows(NullPointerException.class, () -> CardSorter.sort(cards, null));
    }

    @Test
    void sort_listContainingNullCard_currentlyThrowsNullPointerException() {
        // mainTypeOrder(card) calls card.getCardType() with no null-guard.
        List<Card> cards = new ArrayList<>(Arrays.asList(monster("A", "Normal", 4, 0, 0), null));
        assertThrows(NullPointerException.class, () -> CardSorter.sort(cards, CardSorter.SortMode.AZ));
    }

    @Test
    void sort_doesNotMutateOriginalList() {
        Card zebra = monster("Zebra", "Normal", 4, 0, 0);
        Card apple = monster("Apple", "Normal", 4, 0, 0);
        List<Card> original = new ArrayList<>(Arrays.asList(zebra, apple));

        List<Card> result = CardSorter.sort(original, CardSorter.SortMode.AZ);

        assertEquals(List.of(zebra, apple), original, "original order untouched");
        assertEquals(List.of(apple, zebra), result);
    }

    // ── Main type / subcategory (shared logic, spot-checked here too) ──────

    @Test
    void sort_mainTypeOrder_monsterSpellTrapOther() {
        Card trap = spellOrTrap("Trap Card", "Normal", "T");
        Card other = spellOrTrap("Skill Card", "Normal", "O");
        Card spell = spellOrTrap("Spell Card", "Normal", "S");
        Card mon = monster("M", "Normal", 4, 0, 0);

        List<Card> result = CardSorter.sort(new ArrayList<>(Arrays.asList(trap, other, spell, mon)), CardSorter.SortMode.AZ);

        assertEquals(List.of(mon, spell, trap, other), result);
    }

    // ── AZ / ZA ──────────────────────────────────────────────────────────────

    @Test
    void sort_az_alphabeticalAscending() {
        Card zebra = monster("Zebra", "Normal", 4, 1000, 1000);
        Card apple = monster("Apple", "Normal", 4, 1000, 1000);
        List<Card> result = CardSorter.sort(new ArrayList<>(Arrays.asList(zebra, apple)), CardSorter.SortMode.AZ);
        assertEquals(List.of(apple, zebra), result);
    }

    @Test
    void sort_za_alphabeticalDescending() {
        Card zebra = monster("Zebra", "Normal", 4, 1000, 1000);
        Card apple = monster("Apple", "Normal", 4, 1000, 1000);
        List<Card> result = CardSorter.sort(new ArrayList<>(Arrays.asList(apple, zebra)), CardSorter.SortMode.ZA);
        assertEquals(List.of(zebra, apple), result);
    }

    @Test
    void sort_az_sameNameMonsterTiebreak_levelThenAtkDescending() {
        Card lowLevel = monster("Same", "Normal", 4, 2000, 0);
        Card highLevel = monster("Same", "Normal", 8, 1000, 0);
        List<Card> result = CardSorter.sort(new ArrayList<>(Arrays.asList(lowLevel, highLevel)), CardSorter.SortMode.AZ);
        assertEquals(List.of(highLevel, lowLevel), result, "same name -> higher level (8) wins over higher ATK");
    }

    // ── ATK_DESC / ATK_ASC ───────────────────────────────────────────────────

    @Test
    void sort_atkDesc_monstersHighestFirst() {
        Card low = monster("A", "Normal", 4, 1000, 0);
        Card high = monster("B", "Normal", 4, 2000, 0);
        List<Card> result = CardSorter.sort(new ArrayList<>(Arrays.asList(low, high)), CardSorter.SortMode.ATK_DESC);
        assertEquals(List.of(high, low), result);
    }

    @Test
    void sort_atkAsc_monstersLowestFirst() {
        Card low = monster("A", "Normal", 4, 1000, 0);
        Card high = monster("B", "Normal", 4, 2000, 0);
        List<Card> result = CardSorter.sort(new ArrayList<>(Arrays.asList(high, low)), CardSorter.SortMode.ATK_ASC);
        assertEquals(List.of(low, high), result);
    }

    @Test
    void sort_atkDesc_spellsIgnoreAtk_fallBackToName() {
        // Spells always report ATK 0; ATK_DESC must still order them by name,
        // not treat every spell as tied-at-zero in input order.
        Card zebraSpell = spellOrTrap("Spell Card", "Normal", "Zebra Spell");
        Card appleSpell = spellOrTrap("Spell Card", "Normal", "Apple Spell");
        List<Card> result = CardSorter.sort(new ArrayList<>(Arrays.asList(zebraSpell, appleSpell)), CardSorter.SortMode.ATK_DESC);
        assertEquals(List.of(appleSpell, zebraSpell), result);
    }

    // ── DEF_DESC / DEF_ASC ───────────────────────────────────────────────────

    @Test
    void sort_defDesc_monstersHighestFirst() {
        Card low = monster("A", "Normal", 4, 0, 500);
        Card high = monster("B", "Normal", 4, 0, 1500);
        List<Card> result = CardSorter.sort(new ArrayList<>(Arrays.asList(low, high)), CardSorter.SortMode.DEF_DESC);
        assertEquals(List.of(high, low), result);
    }

    @Test
    void sort_defAsc_monstersLowestFirst() {
        Card low = monster("A", "Normal", 4, 0, 500);
        Card high = monster("B", "Normal", 4, 0, 1500);
        List<Card> result = CardSorter.sort(new ArrayList<>(Arrays.asList(high, low)), CardSorter.SortMode.DEF_ASC);
        assertEquals(List.of(low, high), result);
    }

    // ── LVL_DESC / LVL_ASC ───────────────────────────────────────────────────

    @Test
    void sort_lvlDesc_monstersHighestFirst() {
        Card low = monster("A", "Normal", 4, 0, 0);
        Card high = monster("B", "Normal", 8, 0, 0);
        List<Card> result = CardSorter.sort(new ArrayList<>(Arrays.asList(low, high)), CardSorter.SortMode.LVL_DESC);
        assertEquals(List.of(high, low), result);
    }

    @Test
    void sort_lvlAsc_monstersLowestFirst() {
        Card low = monster("A", "Normal", 4, 0, 0);
        Card high = monster("B", "Normal", 8, 0, 0);
        List<Card> result = CardSorter.sort(new ArrayList<>(Arrays.asList(high, low)), CardSorter.SortMode.LVL_ASC);
        assertEquals(List.of(low, high), result);
    }

    @Test
    void sort_lvlDesc_sameLevel_tiebreaksByNameThenAtkDescending() {
        // Three-level tiebreak chain: level -> name asc -> ATK desc.
        Card sameNameLowAtk = monster("Same", "Normal", 4, 1000, 0);
        Card sameNameHighAtk = monster("Same", "Normal", 4, 2000, 0);
        Card differentName = monster("Zeta", "Normal", 4, 9999, 0);

        List<Card> result = CardSorter.sort(
                new ArrayList<>(Arrays.asList(differentName, sameNameLowAtk, sameNameHighAtk)),
                CardSorter.SortMode.LVL_DESC);

        // All share level 4, so name wins first ("Same" before "Zeta"); within
        // the two "Same" cards, ATK desc breaks the remaining tie.
        assertEquals(List.of(sameNameHighAtk, sameNameLowAtk, differentName), result);
    }

    @Test
    void sort_lvlDesc_spellsAndTraps_fallBackToNameOnly() {
        Card zebraTrap = spellOrTrap("Trap Card", "Normal", "Zebra Trap");
        Card appleTrap = spellOrTrap("Trap Card", "Normal", "Apple Trap");
        List<Card> result = CardSorter.sort(new ArrayList<>(Arrays.asList(zebraTrap, appleTrap)), CardSorter.SortMode.LVL_DESC);
        assertEquals(List.of(appleTrap, zebraTrap), result);
    }

    // ── Subcategory reverse-scan (shared logic, spot-checked here too) ──────

    @Test
    void sort_monsterMultipleProperties_mostSpecificSubcategoryWins() {
        Card pendulumEffect = new Card();
        pendulumEffect.setCardType("Pendulum Effect Monster");
        pendulumEffect.setCardProperties(List.of("Effect", "Pendulum"));
        pendulumEffect.setName_EN("PendulumEffect");
        pendulumEffect.setLevel(4);

        Card plainEffect = monster("PlainEffect", "Effect", 4, 0, 0);

        List<Card> result = CardSorter.sort(
                new ArrayList<>(Arrays.asList(pendulumEffect, plainEffect)), CardSorter.SortMode.AZ);

        assertEquals(List.of(plainEffect, pendulumEffect), result,
                "Effect (subcategory index 1) sorts before Pendulum (index 3)");
    }
}