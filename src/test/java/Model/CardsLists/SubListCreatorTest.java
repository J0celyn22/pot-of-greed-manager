package Model.CardsLists;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Partition-based tests for {@link SubListCreator}'s pure categorization
 * logic: {@code CreateSubLists}, {@code CreateSubMonsterLists},
 * {@code CreateSubSpellLists}, {@code CreateSubTrapLists}.
 * <p>
 * {@code CreateArchetypeLists}, {@code getIntegers}, and
 * {@code UpdateCardArchetypes} are deliberately NOT covered here:
 * {@code CreateArchetypeLists} reads {@code archetypes.json} via
 * {@code Database.openJson}, which can fall through to a real network fetch
 * exactly like the already-quarantined {@code IntegrationTest}, and the
 * archetype static fields it populates have no test-facing setter, so there's
 * no seam to test {@code UpdateCardArchetypes} in isolation either. That's a
 * testability gap worth a separate conversation about adding a seam, not
 * something to route around with a live network call here.
 * <p>
 * Note on test isolation: every {@code Create...} method fully replaces its
 * own static lists at the top of the call, so each test below is
 * self-contained as long as it calls the method under test and reads the
 * result within the same test — but these lists are static/shared class
 * state, so this class relies on JUnit's default sequential (non-parallel)
 * execution, same constraint the production code itself has.
 */
class SubListCreatorTest {

    private static CardElement cardWith(String cardType, List<String> properties) {
        Card card = new Card();
        card.setCardType(cardType);
        card.setCardProperties(properties);
        return new CardElement(card);
    }

    // ── CreateSubLists — top-level Monster / Spell / Trap partition ────────

    @Test
    void createSubLists_monsterCardType_goesToMonsterList() {
        CardElement ce = cardWith("Effect Monster", List.of("Effect"));
        SubListCreator.CreateSubLists(List.of(ce));
        assertEquals(List.of(ce), SubListCreator.getMonsterList());
        assertTrue(SubListCreator.getSpellList().isEmpty());
        assertTrue(SubListCreator.getTrapList().isEmpty());
    }

    @Test
    void createSubLists_spellCardType_goesToSpellList() {
        CardElement ce = cardWith("Spell Card", List.of("Normal"));
        SubListCreator.CreateSubLists(List.of(ce));
        assertEquals(List.of(ce), SubListCreator.getSpellList());
        assertTrue(SubListCreator.getMonsterList().isEmpty());
    }

    @Test
    void createSubLists_trapCardType_goesToTrapList() {
        CardElement ce = cardWith("Trap Card", List.of("Normal"));
        SubListCreator.CreateSubLists(List.of(ce));
        assertEquals(List.of(ce), SubListCreator.getTrapList());
    }

    @Test
    void createSubLists_unrecognizedCardType_notAddedToAnyList() {
        // "Skill Card" is a real Yu-Gi-Oh card type that is neither Monster, Spell nor Trap.
        CardElement ce = cardWith("Skill Card", List.of());
        SubListCreator.CreateSubLists(List.of(ce));
        assertTrue(SubListCreator.getMonsterList().isEmpty());
        assertTrue(SubListCreator.getSpellList().isEmpty());
        assertTrue(SubListCreator.getTrapList().isEmpty());
    }

    @Test
    void createSubLists_nullCardType_notAddedToAnyList_noCrash() {
        CardElement ce = cardWith(null, List.of());
        assertDoesNotThrow(() -> SubListCreator.CreateSubLists(List.of(ce)));
        assertTrue(SubListCreator.getMonsterList().isEmpty());
        assertTrue(SubListCreator.getSpellList().isEmpty());
        assertTrue(SubListCreator.getTrapList().isEmpty());
    }

    @Test
    void createSubLists_lowercaseCardType_caseSensitiveMatch_notRecognized() {
        // Documents current behaviour: the "Monster"/"Spell"/"Trap" substring
        // match is case-sensitive, so a differently-cased card type silently
        // falls through to "not categorized" rather than matching.
        CardElement ce = cardWith("effect monster", List.of());
        SubListCreator.CreateSubLists(List.of(ce));
        assertTrue(SubListCreator.getMonsterList().isEmpty(),
                "cardType matching is case-sensitive; lowercase \"monster\" is not recognized");
    }

    @Test
    void createSubLists_emptyInput_allListsEmpty() {
        SubListCreator.CreateSubLists(new ArrayList<>());
        assertTrue(SubListCreator.getMonsterList().isEmpty());
        assertTrue(SubListCreator.getSpellList().isEmpty());
        assertTrue(SubListCreator.getTrapList().isEmpty());
    }

    @Test
    void createSubLists_mixedList_partitionsCorrectly_preservingOrder() {
        CardElement monster1 = cardWith("Normal Monster", List.of("Normal"));
        CardElement spell1 = cardWith("Spell Card", List.of("Normal"));
        CardElement monster2 = cardWith("Effect Monster", List.of("Effect"));
        CardElement trap1 = cardWith("Trap Card", List.of("Continuous"));

        SubListCreator.CreateSubLists(List.of(monster1, spell1, monster2, trap1));

        assertEquals(List.of(monster1, monster2), SubListCreator.getMonsterList());
        assertEquals(List.of(spell1), SubListCreator.getSpellList());
        assertEquals(List.of(trap1), SubListCreator.getTrapList());
    }

    // ── CreateSubMonsterLists — property-switch partition ───────────────────

    @Test
    void createSubMonsterLists_dragonProperty_addedToDragonList() {
        CardElement ce = cardWith("Normal Monster", List.of("Dragon"));
        SubListCreator.CreateSubMonsterLists(List.of(ce));
        assertEquals(List.of(ce), SubListCreator.getDragonTypeMonster());
    }

    @Test
    void createSubMonsterLists_firstSwitchCase_pyro_addedCorrectly() {
        CardElement ce = cardWith("Normal Monster", List.of("Pyro"));
        SubListCreator.CreateSubMonsterLists(List.of(ce));
        assertEquals(List.of(ce), SubListCreator.getPyroTypeMonster());
    }

    @Test
    void createSubMonsterLists_lastSwitchCase_gemini_addedCorrectly() {
        CardElement ce = cardWith("Normal Monster", List.of("Gemini"));
        SubListCreator.CreateSubMonsterLists(List.of(ce));
        assertEquals(List.of(ce), SubListCreator.getGeminiMonsterCard());
    }

    @Test
    void createSubMonsterLists_multipleProperties_addedToEveryMatchingList() {
        // A Tuner Dragon monster must end up in both lists, not just one.
        CardElement ce = cardWith("Effect Monster", List.of("Dragon", "Tuner"));
        SubListCreator.CreateSubMonsterLists(List.of(ce));
        assertEquals(List.of(ce), SubListCreator.getDragonTypeMonster());
        assertEquals(List.of(ce), SubListCreator.getTunerMonsterCard());
    }

    @Test
    void createSubMonsterLists_unknownProperty_notAddedAnywhere() {
        CardElement ce = cardWith("Normal Monster", List.of("NotARealSubtype"));
        assertDoesNotThrow(() -> SubListCreator.CreateSubMonsterLists(List.of(ce)));
        assertTrue(SubListCreator.getDragonTypeMonster().isEmpty());
        assertTrue(SubListCreator.getPyroTypeMonster().isEmpty());
    }

    @Test
    void createSubMonsterLists_nullCardType_propertiesNeverRead_noCrash() {
        // getCardProperties() is guarded behind the cardType != null check, so
        // a null cardType must short-circuit before any property is read.
        CardElement ce = cardWith(null, List.of("Dragon"));
        assertDoesNotThrow(() -> SubListCreator.CreateSubMonsterLists(List.of(ce)));
        assertTrue(SubListCreator.getDragonTypeMonster().isEmpty());
    }

    @Test
    void createSubMonsterLists_emptyPropertiesList_noCrashNoAdditions() {
        CardElement ce = cardWith("Normal Monster", List.of());
        assertDoesNotThrow(() -> SubListCreator.CreateSubMonsterLists(List.of(ce)));
        assertTrue(SubListCreator.getDragonTypeMonster().isEmpty());
    }

    @Test
    void createSubMonsterLists_nonNullCardTypeWithNullProperties_currentlyThrowsNPE() {
        // Documents a real fragility: the null-check only covers cardType, not
        // cardProperties. A card with a set cardType but an unset (null)
        // properties list crashes the whole categorization pass instead of
        // being skipped or logged like the null-cardType case above.
        CardElement ce = cardWith("Normal Monster", null);
        assertThrows(NullPointerException.class,
                () -> SubListCreator.CreateSubMonsterLists(List.of(ce)));
    }

    // ── CreateSubSpellLists — property-switch partition ─────────────────────

    @Test
    void createSubSpellLists_normalProperty_addedToNormalSpellList() {
        CardElement ce = cardWith("Spell Card", List.of("Normal"));
        SubListCreator.CreateSubSpellLists(List.of(ce));
        assertEquals(List.of(ce), SubListCreator.getNormalSpellCard());
    }

    @Test
    void createSubSpellLists_quickPlayProperty_addedToQuickPlayList() {
        CardElement ce = cardWith("Spell Card", List.of("Quick-Play"));
        SubListCreator.CreateSubSpellLists(List.of(ce));
        assertEquals(List.of(ce), SubListCreator.getQuickPlaySpellCard());
    }

    @Test
    void createSubSpellLists_unknownProperty_notAddedAnywhere() {
        CardElement ce = cardWith("Spell Card", List.of("NotARealSubtype"));
        assertDoesNotThrow(() -> SubListCreator.CreateSubSpellLists(List.of(ce)));
        assertTrue(SubListCreator.getNormalSpellCard().isEmpty());
    }

    @Test
    void createSubSpellLists_nullCardType_noCrash() {
        CardElement ce = cardWith(null, List.of("Normal"));
        assertDoesNotThrow(() -> SubListCreator.CreateSubSpellLists(List.of(ce)));
        assertTrue(SubListCreator.getNormalSpellCard().isEmpty());
    }

    // ── CreateSubTrapLists — property-switch partition ──────────────────────

    @Test
    void createSubTrapLists_continuousProperty_addedToContinuousList() {
        CardElement ce = cardWith("Trap Card", List.of("Continuous"));
        SubListCreator.CreateSubTrapLists(List.of(ce));
        assertEquals(List.of(ce), SubListCreator.getContinuousTrapCard());
    }

    @Test
    void createSubTrapLists_counterProperty_addedToCounterList() {
        CardElement ce = cardWith("Trap Card", List.of("Counter"));
        SubListCreator.CreateSubTrapLists(List.of(ce));
        assertEquals(List.of(ce), SubListCreator.getCounterTrapCard());
    }

    @Test
    void createSubTrapLists_unknownProperty_notAddedAnywhere() {
        CardElement ce = cardWith("Trap Card", List.of("NotARealSubtype"));
        assertDoesNotThrow(() -> SubListCreator.CreateSubTrapLists(List.of(ce)));
        assertTrue(SubListCreator.getNormalTrapCard().isEmpty());
        assertTrue(SubListCreator.getContinuousTrapCard().isEmpty());
        assertTrue(SubListCreator.getCounterTrapCard().isEmpty());
    }
}