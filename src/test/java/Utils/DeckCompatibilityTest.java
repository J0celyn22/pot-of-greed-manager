package Utils;

import Model.CardsLists.Card;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DeckCompatibility}, the single source of truth for "which list
 * should this card end up in". Every other test class in this partition-analysis suite
 * (CardAddToListHandler, CardMoveHandler, CardGroupRegistry, CardPasteInsertHandler,
 * MiddleSelectionActionHandler) assumes this class is correct; if one of those higher-level
 * tests fails, come back here first to make sure the rule itself hasn't regressed.
 * <p>
 * Nothing here touches JavaFX or any registry, so every method is directly testable.
 */
class DeckCompatibilityTest {

    private static Card cardWithProperties(String... properties) {
        Card card = new Card();
        card.setName_EN("Test Card");
        card.setCardProperties(new ArrayList<>(List.of(properties)));
        return card;
    }

    private static Card extraDeckCard() {
        return cardWithProperties("Effect", "Fusion");
    }

    private static Card mainDeckMonster() {
        return cardWithProperties("Effect");
    }

    private static Card spellCard() {
        return cardWithProperties("Normal Spell");
    }

    // ── isExtraDeckCard / isMainDeckCard ───────────────────────────────────────

    @Test
    void fusionSynchroXyzLinkMonsters_areExtraDeckCards() {
        assertTrue(DeckCompatibility.isExtraDeckCard(cardWithProperties("Fusion")));
        assertTrue(DeckCompatibility.isExtraDeckCard(cardWithProperties("Synchro")));
        assertTrue(DeckCompatibility.isExtraDeckCard(cardWithProperties("Xyz")));
        assertTrue(DeckCompatibility.isExtraDeckCard(cardWithProperties("Link")));
    }

    @Test
    void normalEffectMonstersSpellsAndTraps_areNotExtraDeckCards() {
        assertFalse(DeckCompatibility.isExtraDeckCard(mainDeckMonster()));
        assertFalse(DeckCompatibility.isExtraDeckCard(spellCard()));
        assertFalse(DeckCompatibility.isExtraDeckCard(cardWithProperties("Continuous Trap")));
    }

    @Test
    void isExtraDeckCard_nullCardOrNullProperties_returnsFalse() {
        assertFalse(DeckCompatibility.isExtraDeckCard(null));
        Card noProps = new Card();
        assertFalse(DeckCompatibility.isExtraDeckCard(noProps));
    }

    @Test
    void isMainDeckCard_isExactInverseOfIsExtraDeckCard() {
        assertTrue(DeckCompatibility.isMainDeckCard(mainDeckMonster()));
        assertFalse(DeckCompatibility.isMainDeckCard(extraDeckCard()));
    }

    // ── section-name helpers (case/whitespace/short-form tolerance) ───────────

    @Test
    void sectionNameHelpers_acceptShortFormsAndAreCaseInsensitive() {
        assertTrue(DeckCompatibility.isMainDeckSection("Main Deck"));
        assertTrue(DeckCompatibility.isMainDeckSection("  main  "));
        assertTrue(DeckCompatibility.isMainDeckSection("MAIN DECK"));

        assertTrue(DeckCompatibility.isExtraDeckSection("Extra Deck"));
        assertTrue(DeckCompatibility.isExtraDeckSection("extra"));

        assertTrue(DeckCompatibility.isSideDeckSection("Side Deck"));
        assertTrue(DeckCompatibility.isSideDeckSection("Side"));
    }

    @Test
    void sectionNameHelpers_rejectUnrelatedOrNullNames() {
        assertFalse(DeckCompatibility.isMainDeckSection(null));
        assertFalse(DeckCompatibility.isMainDeckSection("Cards not to add"));
        assertFalse(DeckCompatibility.isExtraDeckSection("Main Deck"));
        assertFalse(DeckCompatibility.isSideDeckSection("Extra Deck"));
    }

    // ── isCompatibleWith ────────────────────────────────────────────────────────

    @Test
    void mainDeckMonster_isCompatibleOnlyWithMainAndSide() {
        Card card = mainDeckMonster();
        assertTrue(DeckCompatibility.isCompatibleWith(card, "Main Deck"));
        assertTrue(DeckCompatibility.isCompatibleWith(card, "Side Deck"));
        assertFalse(DeckCompatibility.isCompatibleWith(card, "Extra Deck"));
    }

    @Test
    void extraDeckMonster_isCompatibleOnlyWithExtraAndSide() {
        Card card = extraDeckCard();
        assertTrue(DeckCompatibility.isCompatibleWith(card, "Extra Deck"));
        assertTrue(DeckCompatibility.isCompatibleWith(card, "Side Deck"));
        assertFalse(DeckCompatibility.isCompatibleWith(card, "Main Deck"));
    }

    @Test
    void isCompatibleWith_nonDeckSectionOrNullCard() {
        assertFalse(DeckCompatibility.isCompatibleWith(null, "Main Deck"));
        // A non-deck destination (e.g. a ThemeCollection's card list, or "Cards not to add")
        // is never restricted by deck-building rules.
        assertTrue(DeckCompatibility.isCompatibleWith(extraDeckCard(), "Cards not to add"));
    }

    // ── redirectSection ─────────────────────────────────────────────────────────

    @Test
    void redirectSection_extraCardOntoMain_redirectsToExtra() {
        assertEquals("Extra Deck", DeckCompatibility.redirectSection(extraDeckCard(), "Main Deck"));
    }

    @Test
    void redirectSection_mainCardOntoExtra_redirectsToMain() {
        assertEquals("Main Deck", DeckCompatibility.redirectSection(mainDeckMonster(), "Extra Deck"));
    }

    @Test
    void redirectSection_compatibleCard_returnsNull() {
        assertNull(DeckCompatibility.redirectSection(mainDeckMonster(), "Main Deck"));
        assertNull(DeckCompatibility.redirectSection(extraDeckCard(), "Extra Deck"));
    }

    @Test
    void redirectSection_sideDeckOrNullInputs_returnsNull() {
        // Side Deck accepts everything, so there is never a "correct other list".
        assertNull(DeckCompatibility.redirectSection(extraDeckCard(), "Side Deck"));
        assertNull(DeckCompatibility.redirectSection(null, "Main Deck"));
        assertNull(DeckCompatibility.redirectSection(mainDeckMonster(), null));
    }

    // ── allCompatibleWith / anyCompatibleWith (used by menu-gating logic) ──────

    @Test
    void allCompatibleWith_sideDeck_alwaysTrueRegardlessOfMix() {
        List<Card> mixed = List.of(mainDeckMonster(), extraDeckCard(), spellCard());
        assertTrue(DeckCompatibility.allCompatibleWith(mixed, "Side Deck"));
    }

    @Test
    void allCompatibleWith_mainDeck_falseAssoonAsOneExtraCardIsPresent() {
        List<Card> uniform = List.of(mainDeckMonster(), spellCard());
        List<Card> mixed = List.of(mainDeckMonster(), extraDeckCard());
        assertTrue(DeckCompatibility.allCompatibleWith(uniform, "Main Deck"));
        assertFalse(DeckCompatibility.allCompatibleWith(mixed, "Main Deck"));
    }

    @Test
    void allCompatibleWith_extraDeck_falseAssoonAsOneMainCardIsPresent() {
        List<Card> uniform = List.of(extraDeckCard());
        List<Card> mixed = List.of(extraDeckCard(), mainDeckMonster());
        assertTrue(DeckCompatibility.allCompatibleWith(uniform, "Extra Deck"));
        assertFalse(DeckCompatibility.allCompatibleWith(mixed, "Extra Deck"));
    }

    @Test
    void anyCompatibleWith_trueIfAtLeastOneCardMatches() {
        List<Card> mixed = List.of(mainDeckMonster(), extraDeckCard());
        assertTrue(DeckCompatibility.anyCompatibleWith(mixed, "Main Deck"));
        assertTrue(DeckCompatibility.anyCompatibleWith(mixed, "Extra Deck"));
        assertFalse(DeckCompatibility.anyCompatibleWith(List.of(mainDeckMonster()), "Extra Deck"));
    }

    @Test
    void allAndAnyCompatibleWith_emptyOrNullCollections() {
        assertTrue(DeckCompatibility.allCompatibleWith(null, "Main Deck"));
        assertTrue(DeckCompatibility.allCompatibleWith(List.of(), "Extra Deck"));
        assertFalse(DeckCompatibility.anyCompatibleWith(null, "Main Deck"));
        assertFalse(DeckCompatibility.anyCompatibleWith(List.of(), "Extra Deck"));
    }
}