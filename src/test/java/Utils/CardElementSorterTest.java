package Utils;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Partition-based tests for {@link CardElementSorter}.
 * <p>
 * Note on {@link CardSorter}: the two classes share near-identical
 * {@code mainTypeOrder}/{@code subcatOrder}/{@code effectiveLevel}/
 * {@code safeName} logic and subcategory tables, just operating on
 * {@link CardElement} here vs. plain {@link Card} there. That's flagged
 * separately as a production-code consolidation candidate; this file and
 * {@code CardSorterTest} necessarily duplicate some coverage of that shared
 * logic as a result, but each also covers what's genuinely unique to its
 * own class (null-tolerance here; the 8 {@code SortMode} variants there).
 */
class CardElementSorterTest {

    private static CardElement monster(String name, String subtype, int level, int atk, int def) {
        Card card = new Card();
        card.setCardType("Effect Monster");
        card.setCardProperties(List.of(subtype));
        card.setName_EN(name);
        card.setLevel(level);
        card.setAtk(atk);
        card.setDef(def);
        return new CardElement(card);
    }

    private static CardElement spellOrTrap(String cardType, String subtype, String name) {
        Card card = new Card();
        card.setCardType(cardType);
        card.setCardProperties(List.of(subtype));
        card.setName_EN(name);
        return new CardElement(card);
    }

    // ── Null handling ────────────────────────────────────────────────────────

    @Test
    void sortInPlace_nullList_isNoOp_doesNotThrow() {
        assertDoesNotThrow(() -> CardElementSorter.sortInPlace(null));
    }

    @Test
    void sortInPlace_singleElement_isNoOp() {
        List<CardElement> list = new ArrayList<>(List.of(monster("Only", "Normal", 4, 1000, 1000)));
        assertDoesNotThrow(() -> CardElementSorter.sortInPlace(list));
        assertEquals(1, list.size());
    }

    @Test
    void sortInPlace_mutatesTheGivenListReference() {
        CardElement spell = spellOrTrap("Spell Card", "Normal", "Zebra Spell");
        CardElement monster = monster("Apple Monster", "Normal", 4, 1000, 1000);
        List<CardElement> list = new ArrayList<>(Arrays.asList(spell, monster));

        CardElementSorter.sortInPlace(list);

        assertEquals(List.of(monster, spell), list, "monster (type 0) must sort before spell (type 1), in place");
    }

    @Test
    void sorted_nullList_returnsEmptyListNotNull() {
        List<CardElement> result = CardElementSorter.sorted(null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void sorted_doesNotMutateOriginalList() {
        CardElement spell = spellOrTrap("Spell Card", "Normal", "Zebra");
        CardElement monster = monster("Apple", "Normal", 4, 1000, 1000);
        List<CardElement> original = new ArrayList<>(Arrays.asList(spell, monster));

        List<CardElement> result = CardElementSorter.sorted(original);

        assertEquals(List.of(spell, monster), original, "original order untouched");
        assertEquals(List.of(monster, spell), result);
    }

    @Test
    void sorted_nullElementsSinkToTheBottom() {
        CardElement monster = monster("Apple", "Normal", 4, 1000, 1000);
        List<CardElement> list = new ArrayList<>(Arrays.asList(null, monster, null));

        List<CardElement> result = CardElementSorter.sorted(list);

        assertEquals(monster, result.get(0));
        assertNull(result.get(1));
        assertNull(result.get(2));
    }

    @Test
    void sorted_elementWithNullCard_sinksToTheBottom() {
        CardElement noCard = new CardElement(null, false, false, false, false);
        CardElement monster = monster("Apple", "Normal", 4, 1000, 1000);
        List<CardElement> list = new ArrayList<>(Arrays.asList(noCard, monster));

        List<CardElement> result = CardElementSorter.sorted(list);

        assertEquals(monster, result.get(0));
        assertEquals(noCard, result.get(1));
    }

    // ── Main type ordering: Monster < Spell < Trap < Other ──────────────────

    @Test
    void sorted_mainTypeOrder_monsterSpellTrapOther() {
        CardElement trap = spellOrTrap("Trap Card", "Normal", "T");
        CardElement other = spellOrTrap("Skill Card", "Normal", "O");
        CardElement spell = spellOrTrap("Spell Card", "Normal", "S");
        CardElement mon = monster("M", "Normal", 4, 0, 0);
        List<CardElement> list = new ArrayList<>(Arrays.asList(trap, other, spell, mon));

        List<CardElement> result = CardElementSorter.sorted(list);

        assertEquals(List.of(mon, spell, trap, other), result);
    }

    // ── Monster subcategory: reverse scan, most specific property wins ─────

    @Test
    void sorted_monsterSubcategory_normalBeforeEffect() {
        CardElement effect = monster("B", "Effect", 4, 0, 0);
        CardElement normal = monster("A", "Normal", 4, 0, 0);
        List<CardElement> result = CardElementSorter.sorted(new ArrayList<>(Arrays.asList(effect, normal)));
        assertEquals(List.of(normal, effect), result);
    }

    @Test
    void sorted_monsterMultipleProperties_mostSpecificSubcategoryWins() {
        // A Pendulum-Effect monster must land in the Pendulum bucket (index 3),
        // ahead of a plain Effect monster (index 1), because subcatOrder scans
        // MONSTER_SUBCAT_ORDER in reverse and Pendulum comes after Effect.
        Card pendulumEffectCard = new Card();
        pendulumEffectCard.setCardType("Pendulum Effect Monster");
        pendulumEffectCard.setCardProperties(List.of("Effect", "Pendulum"));
        pendulumEffectCard.setName_EN("PendulumEffect");
        pendulumEffectCard.setLevel(4);
        CardElement pendulumEffect = new CardElement(pendulumEffectCard);

        CardElement plainEffect = monster("PlainEffect", "Effect", 4, 0, 0);

        List<CardElement> result = CardElementSorter.sorted(new ArrayList<>(Arrays.asList(plainEffect, pendulumEffect)));

        assertEquals(List.of(plainEffect, pendulumEffect), result,
                "Effect (index 1) sorts before Pendulum (index 3)");
    }

    // ── Monster tiebreakers: level desc -> ATK desc -> DEF desc -> name asc ──

    @Test
    void sorted_monsterTiebreak_levelDescending() {
        CardElement low = monster("A", "Normal", 4, 0, 0);
        CardElement high = monster("B", "Normal", 8, 0, 0);
        List<CardElement> result = CardElementSorter.sorted(new ArrayList<>(Arrays.asList(low, high)));
        assertEquals(List.of(high, low), result);
    }

    @Test
    void sorted_monsterTiebreak_sameLevel_atkDescending() {
        CardElement lowAtk = monster("A", "Normal", 4, 1000, 0);
        CardElement highAtk = monster("B", "Normal", 4, 2000, 0);
        List<CardElement> result = CardElementSorter.sorted(new ArrayList<>(Arrays.asList(lowAtk, highAtk)));
        assertEquals(List.of(highAtk, lowAtk), result);
    }

    @Test
    void sorted_monsterTiebreak_sameLevelAndAtk_defDescending() {
        CardElement lowDef = monster("A", "Normal", 4, 1000, 500);
        CardElement highDef = monster("B", "Normal", 4, 1000, 1500);
        List<CardElement> result = CardElementSorter.sorted(new ArrayList<>(Arrays.asList(lowDef, highDef)));
        assertEquals(List.of(highDef, lowDef), result);
    }

    @Test
    void sorted_monsterTiebreak_allNumericEqual_nameAscending() {
        CardElement zebra = monster("Zebra", "Normal", 4, 1000, 1000);
        CardElement apple = monster("Apple", "Normal", 4, 1000, 1000);
        List<CardElement> result = CardElementSorter.sorted(new ArrayList<>(Arrays.asList(zebra, apple)));
        assertEquals(List.of(apple, zebra), result);
    }

    @Test
    void sorted_spellSubcategory_nameAscending_noNumericTiebreak() {
        // Spells never enter the mainTypeOrder==0 tiebreak block, so ordering
        // within the same subcategory is name only.
        CardElement zebra = spellOrTrap("Spell Card", "Normal", "Zebra Spell");
        CardElement apple = spellOrTrap("Spell Card", "Normal", "Apple Spell");
        List<CardElement> result = CardElementSorter.sorted(new ArrayList<>(Arrays.asList(zebra, apple)));
        assertEquals(List.of(apple, zebra), result);
    }

    // ── Effective level source: Link -> linkVal, Xyz -> rank, else -> level ──

    @Test
    void sorted_effectiveLevel_linkMonsterUsesLinkVal() {
        Card linkCard = new Card();
        linkCard.setCardType("Link Monster");
        linkCard.setCardProperties(List.of("Link"));
        linkCard.setName_EN("LinkMonster");
        linkCard.setLevel(0); // Link monsters have no Level
        linkCard.setLinkVal(3);
        CardElement link = new CardElement(linkCard);

        // Both must be Link monsters (same subcategory bucket) to actually compare
        // linkVal; a Link vs a Normal monster would just differ by subcategory instead.
        Card link2Card = new Card();
        link2Card.setCardType("Link Monster");
        link2Card.setCardProperties(List.of("Link"));
        link2Card.setName_EN("LinkMonster2");
        link2Card.setLinkVal(5);
        CardElement link2 = new CardElement(link2Card);

        List<CardElement> result = CardElementSorter.sorted(new ArrayList<>(Arrays.asList(link, link2)));
        assertEquals(List.of(link2, link), result, "higher linkVal (5) sorts before lower (3)");
    }

    @Test
    void sorted_effectiveLevel_xyzMonsterUsesRank() {
        Card xyz1 = new Card();
        xyz1.setCardType("Xyz Monster");
        xyz1.setCardProperties(List.of("Xyz"));
        xyz1.setName_EN("Xyz1");
        xyz1.setRank(2);
        xyz1.setLevel(0);

        Card xyz2 = new Card();
        xyz2.setCardType("Xyz Monster");
        xyz2.setCardProperties(List.of("Xyz"));
        xyz2.setName_EN("Xyz2");
        xyz2.setRank(7);
        xyz2.setLevel(0);

        List<CardElement> result = CardElementSorter.sorted(
                new ArrayList<>(Arrays.asList(new CardElement(xyz1), new CardElement(xyz2))));
        assertEquals("Xyz2", result.get(0).getCard().getName_EN(), "higher rank (7) sorts before lower (2)");
    }
}