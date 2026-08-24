package Model.Database;

import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CardNameIndex}.
 *
 * <p>{@code levenshteinDistance} and {@code matchPrintCode} with an empty
 * candidate set are pure, deterministic logic and tested directly with no
 * live database needed. {@code getKonamiIdsForName} depends on
 * {@link KonamiIdToNames}' live, lazily-fetched data the same way
 * {@code DatabaseTest} and {@code PrintCodeToKonamiIdTest} depend on theirs,
 * so those cases stick to the same general, non-hardcoded-content style
 * already used there rather than asserting on specific card names or IDs.
 */
class CardNameIndexTest {

    // ── levenshteinDistance ──────────────────────────────────────────────────

    @Test
    void levenshteinDistance_identicalStrings_returnsZero() {
        assertEquals(0, CardNameIndex.levenshteinDistance("lobenoo1", "lobenoo1"));
    }

    @Test
    void levenshteinDistance_bothEmpty_returnsZero() {
        assertEquals(0, CardNameIndex.levenshteinDistance("", ""));
    }

    @Test
    void levenshteinDistance_oneEmpty_returnsOtherLength() {
        assertEquals(5, CardNameIndex.levenshteinDistance("", "lobop"));
        assertEquals(5, CardNameIndex.levenshteinDistance("lobop", ""));
    }

    @Test
    void levenshteinDistance_singleSubstitution_returnsOne() {
        // "lobenoo1" vs "lobenoo7" -- one character misread by OCR.
        assertEquals(1, CardNameIndex.levenshteinDistance("lobenoo1", "lobenoo7"));
    }

    @Test
    void levenshteinDistance_singleInsertion_returnsOne() {
        assertEquals(1, CardNameIndex.levenshteinDistance("lobenoo1", "lobenoo01"));
    }

    @Test
    void levenshteinDistance_singleDeletion_returnsOne() {
        assertEquals(1, CardNameIndex.levenshteinDistance("lobenoo01", "lobenoo1"));
    }

    @Test
    void levenshteinDistance_transposition_countsAsTwoNotOne() {
        // Levenshtein has no dedicated transposition operation, unlike
        // Damerau-Levenshtein -- a swapped adjacent pair costs two edits
        // (two substitutions), not one. Documenting the actual behavior of
        // the algorithm actually used here.
        assertEquals(2, CardNameIndex.levenshteinDistance("ab", "ba"));
    }

    @Test
    void levenshteinDistance_completelyDifferentStrings_returnsMaxLength() {
        assertEquals(3, CardNameIndex.levenshteinDistance("abc", "xyz"));
    }

    @Test
    void levenshteinDistance_caseSensitive_treatedAsDifferentCharacters() {
        // The raw distance function itself does not normalize case -- callers
        // are expected to normalize (see CardNameUtils.normalizeForCompare)
        // before comparing, same as matchPrintCode and getKonamiIdsForName do.
        assertEquals(1, CardNameIndex.levenshteinDistance("lob", "loB"));
        assertEquals(3, CardNameIndex.levenshteinDistance("lob", "LOB"));
    }

    // ── matchPrintCode ───────────────────────────────────────────────────────

    @Test
    void matchPrintCode_emptyCandidateSet_returnsEmptyListWithoutTouchingDatabase() throws URISyntaxException {
        List<CardNameIndex.PrintCodeMatch> matches = CardNameIndex.matchPrintCode("LOB-EN001", Set.of());

        assertNotNull(matches);
        assertTrue(matches.isEmpty());
    }

    // ── getKonamiIdsForName ──────────────────────────────────────────────────

    @Test
    void getKonamiIdsForName_neverReturnsNull() {
        assertNotNull(CardNameIndex.getKonamiIdsForName("Dark Magician"));
    }

    @Test
    void getKonamiIdsForName_gibberishText_returnsEmptySet() {
        // No real card should ever normalize to this exact nonsense string,
        // so this is a safe assertion regardless of what's actually in the
        // live database.
        Set<Integer> matches = CardNameIndex.getKonamiIdsForName("zzzznotarealcardnamezzzz12345");

        assertNotNull(matches);
        assertTrue(matches.isEmpty());
    }

    @Test
    void getKonamiIdsForName_nullName_returnsEmptySetNotException() {
        assertNotNull(CardNameIndex.getKonamiIdsForName(null));
    }
}