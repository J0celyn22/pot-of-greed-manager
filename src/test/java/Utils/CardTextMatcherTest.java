package Utils;

import Model.CardsLists.Card;
import Model.Database.Database;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CardTextMatcher}.
 * <p>
 * Split into two groups: pure normalization/routing behavior that needs no
 * card data (fast, deterministic), and integration checks against whatever
 * the live {@link Database} currently has loaded, mirroring the style
 * {@code Model.Database.DatabaseTest} already uses elsewhere in this suite.
 * The integration checks derive their expected values from the live data at
 * test time rather than hardcoding a specific card, so they stay valid as
 * the underlying database is updated.
 */
class CardTextMatcherTest {

    // --- normalizeForNameCompare -------------------------------------------------

    private static Card buildSyntheticCardWithAllLanguageNames() {
        Card card = new Card();
        card.setPassCode("99999999");
        card.setName_EN("Test Card English Name Zzyx");
        card.setName_FR("Nom Français de Test Zzyx");
        card.setName_JA("テストカード日本語名Zzyx");
        card.setName_ES("Nombre Español de Prueba Zzyx");
        card.setName_DE("Deutscher Testkartenname Zzyx");
        card.setName_IT("Nome Carta di Prova Italiano Zzyx");
        card.setName_CN("测试卡片中文名Zzyx");
        card.setName_KR("테스트카드한국어이름Zzyx");
        card.setName_PT("Nome de Carta de Teste Português Zzyx");
        return card;
    }

    private static Card findAnyCardWithPassCode() {
        return Database.getAllCardsList().values().stream()
                .filter(card -> card.getPassCode() != null && !card.getPassCode().isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Expected at least one loaded card with a pass code"));
    }

    private static Card findAnyCardWithEnglishName() {
        return Database.getAllCardsList().values().stream()
                .filter(card -> card.getName_EN() != null && !card.getName_EN().isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Expected at least one loaded card with an English name"));
    }

    // --- matchText input handling --------------------------------------------

    private static Map.Entry<String, Card> findAnyPrintedCard() throws Exception {
        return Database.getAllPrintedCardsList().entrySet().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Expected at least one loaded printed card"));
    }

    @Test
    void normalizeForNameCompare_null_returnsEmptyString() {
        assertEquals("", CardTextMatcher.normalizeForNameCompare(null));
    }

    @Test
    void normalizeForNameCompare_stripsDiacriticsAndLowercases() {
        assertEquals("resonance", CardTextMatcher.normalizeForNameCompare("Résonance"));
    }

    // --- integration: pass code tier, against live data -----------------------

    @Test
    void normalizeForNameCompare_trimsWhitespace() {
        assertEquals("blue-eyes white dragon",
                CardTextMatcher.normalizeForNameCompare("  Blue-Eyes White Dragon  "));
    }

    @Test
    void matchText_null_returnsEmpty() {
        assertFalse(CardTextMatcher.matchText(null).isPresent());
    }

    // --- integration: print code tier, against live data -----------------------

    @Test
    void matchText_blank_returnsEmpty() {
        assertFalse(CardTextMatcher.matchText("   ").isPresent());
    }

    @Test
    void matchText_garbageText_returnsEmpty() {
        assertFalse(CardTextMatcher.matchText("zzqxv nonsense not a card 12345678901").isPresent());
    }

    // --- integration: name tier, against live data -----------------------------

    @Test
    void matchText_realPassCode_resolvesViaPassCodeTier() {
        Card sampleCard = findAnyCardWithPassCode();

        Optional<CardTextMatcher.MatchResult> result = CardTextMatcher.matchText(sampleCard.getPassCode());

        assertTrue(result.isPresent(), "a real pass code should resolve to a card");
        assertEquals(CardTextMatcher.MatchField.PASS_CODE, result.get().getMatchedField());
        assertEquals(sampleCard.getPassCode(), result.get().getCard().getPassCode());
    }

    @Test
    void matchText_realPassCode_withSurroundingWhitespace_stillResolves() {
        Card sampleCard = findAnyCardWithPassCode();

        Optional<CardTextMatcher.MatchResult> result =
                CardTextMatcher.matchText("  " + sampleCard.getPassCode() + "  ");

        assertTrue(result.isPresent(), "whitespace around a real pass code should still resolve");
        assertEquals(CardTextMatcher.MatchField.PASS_CODE, result.get().getMatchedField());
    }

    // --- name tier: all nine declared languages, via a synthetic card -----------

    @Test
    void matchText_realPrintCode_resolvesViaPrintCodeTier() throws Exception {
        Map.Entry<String, Card> samplePrintedCard = findAnyPrintedCard();

        Optional<CardTextMatcher.MatchResult> result =
                CardTextMatcher.matchText(samplePrintedCard.getKey());

        assertTrue(result.isPresent(), "a real print code should resolve to a card");
        assertEquals(CardTextMatcher.MatchField.PRINT_CODE, result.get().getMatchedField());
    }

    @Test
    void matchText_realPrintCode_lowercased_stillResolvesCaseInsensitively() throws Exception {
        Map.Entry<String, Card> samplePrintedCard = findAnyPrintedCard();

        Optional<CardTextMatcher.MatchResult> result =
                CardTextMatcher.matchText(samplePrintedCard.getKey().toLowerCase());

        assertTrue(result.isPresent(), "print code matching should be case-insensitive");
        assertEquals(CardTextMatcher.MatchField.PRINT_CODE, result.get().getMatchedField());
    }

    @Test
    void matchText_realEnglishName_resolvesViaNameTier() {
        Card sampleCard = findAnyCardWithEnglishName();

        Optional<CardTextMatcher.MatchResult> result =
                CardTextMatcher.matchText(sampleCard.getName_EN());

        assertTrue(result.isPresent(), "a real English card name should resolve to a card");
        assertEquals(CardTextMatcher.MatchField.NAME, result.get().getMatchedField());
    }

    // --- tier priority: a digit-only string must not fall through to name tier ---

    @Test
    void matchText_realEnglishName_differentCase_stillResolves() {
        Card sampleCard = findAnyCardWithEnglishName();

        Optional<CardTextMatcher.MatchResult> result =
                CardTextMatcher.matchText(sampleCard.getName_EN().toUpperCase());

        assertTrue(result.isPresent(), "name matching should be case-insensitive");
        assertEquals(CardTextMatcher.MatchField.NAME, result.get().getMatchedField());
    }

    // --- test fixtures, derived from live data ---------------------------------

    /**
     * Only name_EN, name_FR, and name_JA are ever populated by the live
     * database today (see {@code CardTextMatcher.findByName}'s javadoc), so
     * there's no real card data to test the other six languages against.
     * This registers one synthetic card with a distinct name in all nine
     * languages directly into {@link Database}'s live card map, confirms
     * every one of the nine resolves back to it, then removes the synthetic
     * entry so it doesn't leak into other tests.
     */
    @Test
    void matchText_syntheticCardWithAllNineLanguageNames_resolvesViaEachOne() {
        Card syntheticCard = buildSyntheticCardWithAllLanguageNames();
        Integer syntheticKey = -999001;
        Database.getAllCardsList().put(syntheticKey, syntheticCard);
        try {
            String[] namesToTry = {
                    syntheticCard.getName_EN(), syntheticCard.getName_FR(), syntheticCard.getName_JA(),
                    syntheticCard.getName_ES(), syntheticCard.getName_DE(), syntheticCard.getName_IT(),
                    syntheticCard.getName_CN(), syntheticCard.getName_KR(), syntheticCard.getName_PT()
            };
            for (String name : namesToTry) {
                Optional<CardTextMatcher.MatchResult> result = CardTextMatcher.matchText(name);
                assertTrue(result.isPresent(), "expected a match for name \"" + name + "\"");
                assertEquals(CardTextMatcher.MatchField.NAME, result.get().getMatchedField());
                assertEquals(syntheticCard.getPassCode(), result.get().getCard().getPassCode());
            }
        } finally {
            Database.getAllCardsList().remove(syntheticKey);
        }
    }

    @Test
    void matchText_syntheticCardWithAccentedSpanishName_matchesWithoutAccent() {
        Card syntheticCard = buildSyntheticCardWithAllLanguageNames();
        Integer syntheticKey = -999002;
        Database.getAllCardsList().put(syntheticKey, syntheticCard);
        try {
            String nameWithoutAccent = "Nombre Espanol de Prueba Zzyx";

            Optional<CardTextMatcher.MatchResult> result = CardTextMatcher.matchText(nameWithoutAccent);

            assertTrue(result.isPresent(), "a de-accented Spanish name should still resolve");
            assertEquals(CardTextMatcher.MatchField.NAME, result.get().getMatchedField());
        } finally {
            Database.getAllCardsList().remove(syntheticKey);
        }
    }

    @Test
    void matchText_digitOnlyStringWithNoPassCodeMatch_doesNotAlsoTryNameTier() {
        // "0000000" is very unlikely to be any card's pass code or name; this
        // just confirms the digit-shaped routing doesn't throw or misbehave
        // when the pass-code tier finds nothing.
        Optional<CardTextMatcher.MatchResult> result = CardTextMatcher.matchText("0000000");

        assertFalse(result.isPresent());
    }
}