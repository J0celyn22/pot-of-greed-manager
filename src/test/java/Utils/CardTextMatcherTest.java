package Utils;

import Model.CardsLists.Card;
import Model.Database.Database;
import org.junit.jupiter.api.Test;

import java.util.List;
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

        Optional<CardTextMatcher.Resolution> result = CardTextMatcher.matchText(sampleCard.getPassCode());

        assertTrue(result.isPresent(), "a real pass code should resolve to a card");
        assertEquals(CardTextMatcher.MatchField.PASS_CODE, result.get().getMatchedField());
        // A confident single-print card matches by pass code directly; a card whose Konami ID
        // has more than one valid print code correctly defers to CardCandidates instead (Unit 7)
        // rather than silently picking one, so only assert the passCode equality in that case.
        if (result.get() instanceof CardTextMatcher.MatchResult matchResult) {
            assertEquals(sampleCard.getPassCode(), matchResult.getCard().getPassCode());
        }
    }

    @Test
    void matchText_realPassCode_withSurroundingWhitespace_stillResolves() {
        Card sampleCard = findAnyCardWithPassCode();

        Optional<CardTextMatcher.Resolution> result =
                CardTextMatcher.matchText("  " + sampleCard.getPassCode() + "  ");

        assertTrue(result.isPresent(), "whitespace around a real pass code should still resolve");
        assertEquals(CardTextMatcher.MatchField.PASS_CODE, result.get().getMatchedField());
    }

    // --- name tier: all nine declared languages, via a synthetic card -----------

    @Test
    void matchText_realPrintCode_resolvesViaPrintCodeTier() throws Exception {
        Map.Entry<String, Card> samplePrintedCard = findAnyPrintedCard();

        Optional<CardTextMatcher.Resolution> result =
                CardTextMatcher.matchText(samplePrintedCard.getKey());

        assertTrue(result.isPresent(), "a real print code should resolve to a card");
        assertEquals(CardTextMatcher.MatchField.PRINT_CODE, result.get().getMatchedField());
    }

    @Test
    void matchText_realPrintCode_lowercased_stillResolvesCaseInsensitively() throws Exception {
        Map.Entry<String, Card> samplePrintedCard = findAnyPrintedCard();

        Optional<CardTextMatcher.Resolution> result =
                CardTextMatcher.matchText(samplePrintedCard.getKey().toLowerCase());

        assertTrue(result.isPresent(), "print code matching should be case-insensitive");
        assertEquals(CardTextMatcher.MatchField.PRINT_CODE, result.get().getMatchedField());
    }

    @Test
    void matchText_realEnglishName_resolvesViaNameTier() {
        Card sampleCard = findAnyCardWithEnglishName();

        Optional<CardTextMatcher.Resolution> result =
                CardTextMatcher.matchText(sampleCard.getName_EN());

        assertTrue(result.isPresent(), "a real English card name should resolve to a card");
        assertEquals(CardTextMatcher.MatchField.NAME, result.get().getMatchedField());
    }

    // --- tier priority: a digit-only string must not fall through to name tier ---

    @Test
    void matchText_realEnglishName_differentCase_stillResolves() {
        Card sampleCard = findAnyCardWithEnglishName();

        Optional<CardTextMatcher.Resolution> result =
                CardTextMatcher.matchText(sampleCard.getName_EN().toUpperCase());

        assertTrue(result.isPresent(), "name matching should be case-insensitive");
        assertEquals(CardTextMatcher.MatchField.NAME, result.get().getMatchedField());
    }

    // --- test fixtures, derived from live data ---------------------------------

    private static String corruptOneCharacter(String text) {
        char[] characters = text.toCharArray();
        char replacement = characters[0] == 'X' ? 'Y' : 'X';
        characters[0] = replacement;
        return new String(characters);
    }

    @Test
    void matchText_syntheticCardWithAccentedSpanishName_matchesWithoutAccent() {
        Card syntheticCard = buildSyntheticCardWithAllLanguageNames();
        Integer syntheticKey = -999002;
        Database.getAllCardsList().put(syntheticKey, syntheticCard);
        try {
            String nameWithoutAccent = "Nombre Espanol de Prueba Zzyx";

            Optional<CardTextMatcher.Resolution> result = CardTextMatcher.matchText(nameWithoutAccent);

            assertTrue(result.isPresent(), "a de-accented Spanish name should still resolve");
            assertEquals(CardTextMatcher.MatchField.NAME, result.get().getMatchedField());
        } finally {
            Database.getAllCardsList().remove(syntheticKey);
        }
    }

    /**
     * Registers one synthetic card with a distinct name in all nine
     * languages directly into {@link Database}'s live card map (there's no
     * real card in the loaded data guaranteed to have a distinct,
     * predictable name in all nine at once), confirms every one of the nine
     * resolves back to it via {@link CardTextMatcher#matchText}'s exact
     * linear-scan tier, then removes the synthetic entry so it doesn't leak
     * into other tests.
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
                Optional<CardTextMatcher.Resolution> result = CardTextMatcher.matchText(name);
                assertTrue(result.isPresent(), "expected a match for name \"" + name + "\"");
                assertEquals(CardTextMatcher.MatchField.NAME, result.get().getMatchedField());
                // The synthetic fixture has no Konami ID, so resolveKonamiId always falls back to
                // wrapping the same card unchanged rather than ever returning CardCandidates.
                CardTextMatcher.MatchResult matchResult =
                        assertInstanceOf(CardTextMatcher.MatchResult.class, result.get());
                assertEquals(syntheticCard.getPassCode(), matchResult.getCard().getPassCode());
            }
        } finally {
            Database.getAllCardsList().remove(syntheticKey);
        }
    }

    // --- matchCandidates: Unit 6's multi-candidate entry point ------------------

    @Test
    void matchText_digitOnlyStringWithNoPassCodeMatch_doesNotAlsoTryNameTier() {
        // "0000000" is very unlikely to be any card's pass code or name; this
        // just confirms the digit-shaped routing doesn't throw or misbehave
        // when the pass-code tier finds nothing.
        Optional<CardTextMatcher.Resolution> result = CardTextMatcher.matchText("0000000");

        assertFalse(result.isPresent());
    }

    @Test
    void matchCandidates_null_returnsEmpty() {
        assertFalse(CardTextMatcher.matchCandidates(null).isPresent());
    }

    @Test
    void matchCandidates_emptyList_returnsEmpty() {
        assertFalse(CardTextMatcher.matchCandidates(List.of()).isPresent());
    }

    @Test
    void matchCandidates_allGarbageCandidates_returnsEmpty() {
        Optional<CardTextMatcher.Resolution> result = CardTextMatcher.matchCandidates(
                List.of("zzqxv nonsense", "not a card either", "0000000"));

        assertFalse(result.isPresent());
    }

    @Test
    void matchCandidates_realPassCodeAmongGarbage_resolvesRegardlessOfPosition() {
        Card sampleCard = findAnyCardWithPassCode();

        Optional<CardTextMatcher.Resolution> result = CardTextMatcher.matchCandidates(
                List.of("some flavor text noise", sampleCard.getPassCode(), "ATK/2500 DEF/2100"));

        assertTrue(result.isPresent(), "a real pass code among other candidates should still resolve");
        assertEquals(CardTextMatcher.MatchField.PASS_CODE, result.get().getMatchedField());
        if (result.get() instanceof CardTextMatcher.MatchResult matchResult) {
            assertEquals(sampleCard.getPassCode(), matchResult.getCard().getPassCode());
        }
    }

    @Test
    void matchCandidates_realPrintCodeAsFirstCandidate_resolvesViaPrintCodeTier() throws Exception {
        Map.Entry<String, Card> samplePrintedCard = findAnyPrintedCard();

        Optional<CardTextMatcher.Resolution> result = CardTextMatcher.matchCandidates(
                List.of(samplePrintedCard.getKey(), "some other noisy line"));

        assertTrue(result.isPresent(), "a real print code should resolve via the exact-code tier");
        assertEquals(CardTextMatcher.MatchField.PRINT_CODE, result.get().getMatchedField());
    }

    @Test
    void matchCandidates_printCodeCandidateWinsOverUnrelatedNameCandidate() throws Exception {
        // Regression check for the ordering bug caught during review: an exact print-code
        // candidate must win outright, even when a different candidate in the same cycle is a
        // real card name for some *other* card entirely (i.e. matchCandidates must not just
        // grab the first candidate that resolves to *anything*).
        Map.Entry<String, Card> samplePrintedCard = findAnyPrintedCard();
        Card unrelatedNamedCard = findAnyCardWithEnglishName();

        Optional<CardTextMatcher.Resolution> result = CardTextMatcher.matchCandidates(
                List.of(unrelatedNamedCard.getName_EN(), samplePrintedCard.getKey()));

        assertTrue(result.isPresent());
        assertEquals(CardTextMatcher.MatchField.PRINT_CODE, result.get().getMatchedField(),
                "an exact print code elsewhere in the cycle should win over a plain name match");
    }

    @Test
    void matchCandidates_nameCandidateAlone_resolvesViaNameFallback() {
        Card sampleCard = findAnyCardWithEnglishName();

        Optional<CardTextMatcher.Resolution> result =
                CardTextMatcher.matchCandidates(List.of("unrelated noise line", sampleCard.getName_EN()));

        assertTrue(result.isPresent(), "a real name with no accompanying print code should still resolve");
        if (result.get() instanceof CardTextMatcher.MatchResult matchResult) {
            assertEquals(sampleCard.getPassCode(), matchResult.getCard().getPassCode());
        } else if (result.get() instanceof CardTextMatcher.CardCandidates candidates
                && sampleCard.getKonamiId() != null) {
            assertEquals(Integer.parseInt(sampleCard.getKonamiId()), candidates.getKonamiId());
        }
    }

    @Test
    void matchCandidates_nameWithSlightlyNoisyPrintCode_narrowsToPrintCodeMatch() throws Exception {
        Map.Entry<String, Card> samplePrintedCard = findAnyPrintedCard();
        String realName = samplePrintedCard.getValue().getName_EN();
        // Skip this run if the sampled printed card has no English name or its print code is too
        // short to safely corrupt one character out of — rather than assert on data we can't
        // control, since this test's whole point is exercising the fuzzy tier against whatever
        // real data happens to be loaded.
        if (realName == null || realName.isBlank() || samplePrintedCard.getKey().length() < 2) {
            return;
        }
        String noisyPrintCode = corruptOneCharacter(samplePrintedCard.getKey());

        Optional<CardTextMatcher.Resolution> result =
                CardTextMatcher.matchCandidates(List.of(realName, noisyPrintCode));

        assertTrue(result.isPresent(), "a real name plus a one-character-off print code should still resolve");
        assertEquals(CardTextMatcher.MatchField.NAME_AND_PRINT_CODE, result.get().getMatchedField());
    }
}