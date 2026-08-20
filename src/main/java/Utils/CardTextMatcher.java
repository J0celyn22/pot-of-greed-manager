package Utils;

import Model.CardsLists.Card;
import Model.Database.Database;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves raw recognized text (e.g. from the camera scanner's OCR step)
 * into a {@link Card} from the loaded card database.
 *
 * <p>This is the bridge between "some text was read off a card" and this
 * project's actual card model — it does not perform OCR itself. Matching is
 * tried in priority order against the fields that are cheapest and least
 * ambiguous to recognize first:
 * <ol>
 *   <li>Pass code — the printed 6-8 digit card number</li>
 *   <li>Print code — the printed set code, e.g. {@code "LOB-EN001"}</li>
 *   <li>Card name — checked against every language {@link Card} declares
 *       (English, French, Japanese, Spanish, German, Italian, Chinese,
 *       Korean, Portuguese). Only English, French, and Japanese are actually
 *       populated by {@link Database} today — see {@link #findByName} for
 *       details — but all nine are checked so this stays correct without
 *       changes if that ever expands.</li>
 * </ol>
 *
 * <p>Matching within each tier is an exact match after normalization
 * (case-insensitive and diacritic-insensitive for names; whitespace-stripped
 * and uppercased for codes). There is deliberately no fuzzy/edit-distance
 * fallback here — the right tolerance for OCR noise depends on which OCR
 * library ends up chosen and what its actual error patterns look like, and
 * guessing a threshold now would mean tuning it against nothing.
 */
public final class CardTextMatcher {

    private static final Logger logger = LoggerFactory.getLogger(CardTextMatcher.class);

    private static final Pattern PASS_CODE_PATTERN = Pattern.compile("\\d{6,8}");

    private CardTextMatcher() {
    }

    /**
     * Resolves raw recognized text into a {@link Card}, trying pass code,
     * then print code, then name, in that order, stopping at the first tier
     * that produces a match.
     *
     * @param recognizedText the raw text recognized off a card (may be
     *                       {@code null} or blank)
     * @return the matched card and which field it matched on, or
     * {@link Optional#empty()} if nothing in the database matches
     */
    public static Optional<MatchResult> matchText(String recognizedText) {
        if (recognizedText == null) {
            return Optional.empty();
        }
        String trimmed = recognizedText.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        String codeCandidate = trimmed.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");

        if (PASS_CODE_PATTERN.matcher(codeCandidate).matches()) {
            Optional<Card> passCodeMatch = findByPassCode(codeCandidate);
            if (passCodeMatch.isPresent()) {
                MatchResult result = new MatchResult(passCodeMatch.get(), MatchField.PASS_CODE);
                logMatch(trimmed, result);
                return Optional.of(result);
            }
        }

        Optional<Card> printCodeMatch = findByPrintCode(codeCandidate);
        if (printCodeMatch.isPresent()) {
            MatchResult result = new MatchResult(printCodeMatch.get(), MatchField.PRINT_CODE);
            logMatch(trimmed, result);
            return Optional.of(result);
        }

        Optional<Card> nameMatch = findByName(trimmed);
        if (nameMatch.isPresent()) {
            MatchResult result = new MatchResult(nameMatch.get(), MatchField.NAME);
            logMatch(trimmed, result);
            return Optional.of(result);
        }

        logger.debug("No card match found for recognized text \"{}\"", trimmed);
        return Optional.empty();
    }

    /**
     * Looks up a card by exact pass code among all loaded cards.
     * <p>
     * {@link Database#getAllCardsList()} is keyed by card-image id, not by
     * pass code, so this is a linear scan rather than a map lookup — the same
     * cost {@code Controller.CardFilterMatcher} already pays scanning the
     * same ~22,000-card list on every filter keystroke.
     * </p>
     *
     * @param normalizedPassCode the candidate pass code, digits only
     * @return the matching card, or empty if no card has this pass code
     */
    static Optional<Card> findByPassCode(String normalizedPassCode) {
        for (Card card : Database.getAllCardsList().values()) {
            if (normalizedPassCode.equals(card.getPassCode())) {
                return Optional.of(card);
            }
        }
        return Optional.empty();
    }

    /**
     * Looks up a card by print code via {@link Database#getAllPrintedCardsList()},
     * which is already keyed by print code — an O(1) lookup in the common
     * case. Falls back to a case-insensitive scan, since stored print codes
     * come straight from the source JSON with no case normalization applied
     * (see {@code Model.Database.PrintCodeToKonamiId}), so their casing isn't
     * guaranteed to be uppercase.
     *
     * @param normalizedPrintCode the candidate print code, uppercased and
     *                            whitespace-stripped
     * @return the matching card, or empty if no card has this print code
     */
    static Optional<Card> findByPrintCode(String normalizedPrintCode) {
        try {
            Map<String, Card> printedCards = Database.getAllPrintedCardsList();
            Card directMatch = printedCards.get(normalizedPrintCode);
            if (directMatch != null) {
                return Optional.of(directMatch);
            }
            for (Map.Entry<String, Card> entry : printedCards.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(normalizedPrintCode)) {
                    return Optional.of(entry.getValue());
                }
            }
            return Optional.empty();
        } catch (URISyntaxException uriSyntaxException) {
            throw new RuntimeException(uriSyntaxException);
        }
    }

    /**
     * Looks up a card by exact name (case-insensitive, diacritic-insensitive)
     * against every language name field {@link Card} declares.
     * <p>
     * Only {@code name_EN}, {@code name_FR}, and {@code name_JA} are ever set
     * today — {@code addresses.json} only wires up English, French, and
     * Japanese name indexes ({@code en.json}/{@code fr.json}/{@code ja.json}
     * from ygoresources.com), so {@code name_ES}, {@code name_DE},
     * {@code name_IT}, {@code name_CN}, {@code name_KR}, and {@code name_PT}
     * are always {@code null} on every loaded card at present; there is no
     * data source for those languages in the project yet, so those six
     * branches are inert until one is added. All nine are still checked here
     * so this method doesn't need to change if that data source ever shows
     * up — {@link #normalizeForNameCompare} returns {@code ""} for a
     * {@code null} field, which never matches a non-empty target.
     * </p>
     *
     * @param rawName the candidate name, not yet normalized
     * @return the matching card, or empty if no card has this name
     */
    static Optional<Card> findByName(String rawName) {
        String normalizedTarget = normalizeForNameCompare(rawName);
        if (normalizedTarget.isEmpty()) {
            return Optional.empty();
        }
        for (Card card : Database.getAllCardsList().values()) {
            if (matchesAnyLanguageName(card, normalizedTarget)) {
                return Optional.of(card);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks whether {@code card}'s name in any declared language normalizes
     * to {@code normalizedTarget}.
     *
     * @param card             the card whose name fields to check
     * @param normalizedTarget the already-normalized name being searched for
     * @return {@code true} if any language's name matches
     */
    private static boolean matchesAnyLanguageName(Card card, String normalizedTarget) {
        return normalizedTarget.equals(normalizeForNameCompare(card.getName_EN()))
                || normalizedTarget.equals(normalizeForNameCompare(card.getName_FR()))
                || normalizedTarget.equals(normalizeForNameCompare(card.getName_JA()))
                || normalizedTarget.equals(normalizeForNameCompare(card.getName_ES()))
                || normalizedTarget.equals(normalizeForNameCompare(card.getName_DE()))
                || normalizedTarget.equals(normalizeForNameCompare(card.getName_IT()))
                || normalizedTarget.equals(normalizeForNameCompare(card.getName_CN()))
                || normalizedTarget.equals(normalizeForNameCompare(card.getName_KR()))
                || normalizedTarget.equals(normalizeForNameCompare(card.getName_PT()));
    }

    /**
     * Normalizes a name for comparison: trims, lowercases, and strips
     * diacritics, so e.g. OCR dropping an accent still matches the accented
     * stored name.
     *
     * @param name the name to normalize (may be {@code null})
     * @return the normalized name, never {@code null}
     */
    static String normalizeForNameCompare(String name) {
        if (name == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(name.trim(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static void logMatch(String recognizedText, MatchResult result) {
        logger.debug("Matched recognized text \"{}\" to card {} via {}",
                recognizedText, result.getCard().getNameOrNumber(), result.getMatchedField());
    }

    /**
     * Which field a {@link MatchResult} was resolved through.
     */
    public enum MatchField {
        PASS_CODE,
        PRINT_CODE,
        NAME
    }

    /**
     * A successful text-to-card resolution.
     */
    public static final class MatchResult {
        private final Card card;
        private final MatchField matchedField;

        MatchResult(Card card, MatchField matchedField) {
            this.card = card;
            this.matchedField = matchedField;
        }

        /**
         * @return the card the recognized text was resolved to
         */
        public Card getCard() {
            return card;
        }

        /**
         * @return which field (pass code, print code, or name) produced the match
         */
        public MatchField getMatchedField() {
            return matchedField;
        }
    }
}