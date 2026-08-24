package Model.Database;

import Utils.CardNameUtils;

import java.net.URISyntaxException;
import java.util.*;

/**
 * Fast lookup structures for resolving card text read from a physical card
 * (e.g. via OCR) against the database, built once and cached lazily the same
 * way as {@link KonamiIdToNames} and {@link PrintCodeToKonamiId}.
 *
 * <p>Resolution happens in two stages, matching how a physical card is
 * actually read: the large title is legible enough for exact matching once
 * normalized (see {@link #getKonamiIdsForName(String)}), while the small
 * print/set code in the corner usually isn't — so a print-code read is only
 * ever compared against the small, closed set of print codes that are
 * actually valid for the name-matched candidate(s) (see
 * {@link #matchPrintCode(String, Set)}), rather than the whole database.
 */
public final class CardNameIndex {

    private static Map<String, Set<Integer>> normalizedNameToKonamiIds;

    private CardNameIndex() {
    }

    /**
     * Returns every Konami ID whose name matches {@code rawName} in any of
     * the languages {@link KonamiIdToNames} currently covers, once both
     * sides are normalized via {@link CardNameUtils#normalizeForCompare(String)}.
     *
     * <p>An empty set means no match. More than one entry means the
     * normalized name is genuinely ambiguous — most often two different
     * cards happening to share an exact name across two different
     * languages — and callers should treat that as needing manual
     * confirmation rather than silently picking one.
     *
     * @param rawName the name as read (e.g. from OCR), in any covered language
     * @return the matching Konami IDs, never {@code null}, possibly empty
     */
    public static Set<Integer> getKonamiIdsForName(String rawName) {
        if (normalizedNameToKonamiIds == null) {
            buildNormalizedNameIndex();
        }
        Set<Integer> matches = normalizedNameToKonamiIds.get(CardNameUtils.normalizeForCompare(rawName));
        return matches != null ? matches : Set.of();
    }

    /**
     * Builds the combined normalized-name -> Konami ID index from every
     * language {@link KonamiIdToNames} currently exposes.
     *
     * <p>Each normalized name maps to a {@code Set}, not a single value, so
     * that a genuine cross-language collision between two different cards is
     * preserved and detectable instead of one language's entry silently
     * overwriting another's.
     */
    private static void buildNormalizedNameIndex() {
        normalizedNameToKonamiIds = new HashMap<>();
        List<Map<Integer, String>> languageMaps = List.of(
                KonamiIdToNames.getKonamiIdToEnNames(),
                KonamiIdToNames.getKonamiIdToFrNames(),
                KonamiIdToNames.getKonamiIdToJaNames(),
                KonamiIdToNames.getKonamiIdToEsNames(),
                KonamiIdToNames.getKonamiIdToDeNames(),
                KonamiIdToNames.getKonamiIdToItNames(),
                KonamiIdToNames.getKonamiIdToPtNames(),
                KonamiIdToNames.getKonamiIdToKrNames());
        for (Map<Integer, String> languageMap : languageMaps) {
            for (Map.Entry<Integer, String> entry : languageMap.entrySet()) {
                String normalizedName = CardNameUtils.normalizeForCompare(entry.getValue());
                if (normalizedName.isEmpty()) {
                    continue;
                }
                normalizedNameToKonamiIds
                        .computeIfAbsent(normalizedName, unusedKey -> new LinkedHashSet<>())
                        .add(entry.getKey());
            }
        }
    }

    /**
     * Matches a (possibly OCR-noisy) print code against only the print codes
     * that are actually valid for {@code candidateKonamiIds} — the small,
     * closed set from {@link PrintCodeToKonamiId#getKonamiIdToPrintCodes()}
     * — rather than the whole database. Ranked closest-match-first by edit
     * distance on the normalized text (same normalization as
     * {@link #getKonamiIdsForName(String)}, reused here since it's the same
     * "compare noisy scanned text to a known string" problem).
     *
     * <p>Deciding what edit distance (or how many close results) counts as
     * "confident enough to auto-add" is left to the caller: that threshold
     * needs tuning against real scans rather than being fixed here. Once a
     * print code is chosen — automatically or by the user picking among the
     * ranked candidates — resolve it to the exact {@link Model.CardsLists.Card}
     * via {@link Database#getAllPrintedCardsList()}, which is keyed by print
     * code and already exact.
     *
     * @param rawPrintCode       the print code as read (e.g. from OCR)
     * @param candidateKonamiIds the Konami ID(s) already narrowed down by a name match
     * @return every print code valid for those candidates, ranked closest-first;
     * empty if none of the candidates have any known print codes
     */
    public static List<PrintCodeMatch> matchPrintCode(String rawPrintCode, Set<Integer> candidateKonamiIds)
            throws URISyntaxException {
        if (candidateKonamiIds.isEmpty()) {
            return List.of();
        }
        String normalizedOcrCode = CardNameUtils.normalizeForCompare(rawPrintCode);
        Map<String, List<String>> konamiIdToPrintCodes = PrintCodeToKonamiId.getKonamiIdToPrintCodes();

        List<PrintCodeMatch> matches = new ArrayList<>();
        for (Integer konamiId : candidateKonamiIds) {
            List<String> validPrintCodes = konamiIdToPrintCodes.get(String.valueOf(konamiId));
            if (validPrintCodes == null) {
                continue;
            }
            for (String validPrintCode : validPrintCodes) {
                int editDistance = levenshteinDistance(
                        normalizedOcrCode, CardNameUtils.normalizeForCompare(validPrintCode));
                matches.add(new PrintCodeMatch(validPrintCode, konamiId, editDistance));
            }
        }
        matches.sort(Comparator.comparingInt(PrintCodeMatch::getEditDistance));
        return matches;
    }

    /**
     * Classic dynamic-programming Levenshtein (single-character insertion,
     * deletion, or substitution) edit distance between two strings.
     *
     * <p>Package-private rather than private so {@code CardNameIndexTest} can
     * exercise it directly without needing a live database.
     */
    static int levenshteinDistance(String first, String second) {
        int[][] distances = new int[first.length() + 1][second.length() + 1];
        for (int i = 0; i <= first.length(); i++) {
            distances[i][0] = i;
        }
        for (int j = 0; j <= second.length(); j++) {
            distances[0][j] = j;
        }
        for (int i = 1; i <= first.length(); i++) {
            for (int j = 1; j <= second.length(); j++) {
                int substitutionCost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                distances[i][j] = Math.min(
                        Math.min(distances[i - 1][j] + 1, distances[i][j - 1] + 1),
                        distances[i - 1][j - 1] + substitutionCost);
            }
        }
        return distances[first.length()][second.length()];
    }

    /**
     * A single candidate print code from {@link #matchPrintCode(String, Set)},
     * with how far it is (in normalized-text edit distance) from the raw
     * scanned print code.
     */
    public static final class PrintCodeMatch {
        private final String printCode;
        private final int konamiId;
        private final int editDistance;

        public PrintCodeMatch(String printCode, int konamiId, int editDistance) {
            this.printCode = printCode;
            this.konamiId = konamiId;
            this.editDistance = editDistance;
        }

        /**
         * The candidate print code, exactly as stored in the database — usable
         * directly as a key into {@link Database#getAllPrintedCardsList()}.
         */
        public String getPrintCode() {
            return printCode;
        }

        /**
         * The Konami ID this print code belongs to (one of the candidates
         * passed into {@link #matchPrintCode(String, Set)}).
         */
        public int getKonamiId() {
            return konamiId;
        }

        /**
         * Edit distance between the normalized scanned text and this print
         * code's own normalized text — 0 means an exact match once normalized.
         */
        public int getEditDistance() {
            return editDistance;
        }
    }
}