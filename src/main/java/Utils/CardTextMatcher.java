package Utils;

import Model.CardsLists.Card;
import Model.Database.CardDatabaseManager;
import Model.Database.CardNameIndex;
import Model.Database.Database;
import Model.Database.PrintCodeToKonamiId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves raw recognized text (e.g. from the camera scanner's OCR step)
 * into a {@link Card} from the loaded card database.
 *
 * <p>This is the bridge between "some text was read off a card" and this
 * project's actual card model — it does not perform OCR itself. Two entry
 * points cover the two shapes a detection can arrive in:
 * <ul>
 *   <li>{@link #matchText(String)} — one known-field string (e.g. the
 *       caller already knows it's trying a pass code). Matching is exact
 *       after normalization, with no fuzzy/edit-distance fallback — the
 *       original Unit 5 tier order below.</li>
 *   <li>{@link #matchCandidates(List)} — several OCR candidate lines from
 *       one detection cycle, when the caller doesn't know which line (if
 *       any) is the name vs. the print code. Tries every candidate through
 *       {@link #matchText} first, then falls back to
 *       {@link Model.Database.CardNameIndex}-backed multi-language name
 *       matching with edit-distance-tolerant print-code narrowing — see
 *       {@link #matchByFuzzyNameAndPrintCode} for the full tier.</li>
 * </ul>
 * Both share the same priority order against the fields that are cheapest
 * and least ambiguous to recognize first:
 * <ol>
 *   <li>Pass code — the printed 6-8 digit card number</li>
 *   <li>Print code — the printed set code, e.g. {@code "LOB-EN001"}</li>
 *   <li>Card name — checked against every language {@link Card} declares
 *       (English, French, Japanese, Spanish, German, Italian, Chinese,
 *       Korean, Portuguese) — see {@link #findByName} for exactly which of
 *       those the live database actually populates today.</li>
 * </ol>
 *
 * <p><b>Unit 7:</b> a pass code or a card name only ever identifies a card's
 * <em>Konami ID</em>, not one specific printing — {@link Database#getAllCardsList()}
 * holds one entry per artwork with {@link Card#getPrintCode()} never set, so handing
 * back whichever entry {@link #findByPassCode} or {@link #findByName} happened to scan
 * first was silently picking an arbitrary artwork with no print code at all. Both tiers
 * now resolve through {@link #resolveKonamiId}, which checks how many print codes
 * {@link PrintCodeToKonamiId} actually knows for that Konami ID: one means an
 * unambiguous {@link MatchResult} resolved via {@link Database#getAllPrintedCardsList()}
 * (which does set the print code correctly); more than one means a {@link CardCandidates}
 * for the caller to choose among instead of guessing. Every public entry point below
 * therefore returns an {@link Optional} of the {@link Resolution} marker interface rather
 * than {@link MatchResult} directly.
 */
public final class CardTextMatcher {

    private static final Logger logger = LoggerFactory.getLogger(CardTextMatcher.class);

    private static final Pattern PASS_CODE_PATTERN = Pattern.compile("\\d{6,8}");

    /**
     * Starting-value tolerance for {@link #matchByFuzzyNameAndPrintCode}'s print-code narrowing
     * step — how far (in {@link CardNameIndex#levenshteinDistance}, on normalized text) a
     * candidate's closest valid print code is allowed to be and still count as a confident match.
     * Not tuned against any real scan yet, same caveat as {@code card_scanner_bridge.py}'s own
     * {@code OCR_CONFIDENCE_THRESHOLD} — see {@link CardNameIndex#matchPrintCode} for why this is
     * deliberately left to the caller rather than fixed inside that method.
     */
    private static final int PRINT_CODE_MAX_EDIT_DISTANCE = 2;

    /**
     * Matches the leading letters of a print code's segment after its last hyphen, up to (not
     * including) its first digit — see {@link #parseLanguageFromPrintCode} for how this is used.
     */
    private static final Pattern PRINT_CODE_LANGUAGE_PATTERN = Pattern.compile("^([A-Za-z]+)\\d");

    private CardTextMatcher() {
    }

    /**
     * Resolves raw recognized text into a {@link Card}, trying pass code,
     * then print code, then name, in that order, stopping at the first tier
     * that produces a match.
     *
     * @param recognizedText the raw text recognized off a card (may be
     *                       {@code null} or blank)
     * @return the matched card and which field it matched on, or a
     * {@link CardCandidates} if it narrows to a Konami ID with more than one valid
     * printing, or {@link Optional#empty()} if nothing in the database matches
     */
    public static Optional<Resolution> matchText(String recognizedText) {
        if (recognizedText == null) {
            return Optional.empty();
        }
        String trimmed = recognizedText.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        Optional<Resolution> codeMatch = matchExactCode(trimmed);
        if (codeMatch.isPresent()) {
            return codeMatch;
        }

        Optional<Card> nameMatch = findByName(trimmed);
        if (nameMatch.isPresent()) {
            Card matchedCard = nameMatch.get();
            return resolveKonamiId(parseKonamiId(matchedCard.getKonamiId()), matchedCard, MatchField.NAME, trimmed);
        }

        logger.debug("No card match found for recognized text \"{}\"", trimmed);
        return Optional.empty();
    }

    /**
     * The pass-code and print-code tiers of {@link #matchText}, split out so
     * {@link #matchCandidates} can run these two unambiguous, code-shaped tiers across every
     * candidate in a detection cycle before attempting any name-based resolution. Without this
     * split, a candidate that happens to be a readable card name would win via {@link #findByName}
     * before a *different* candidate that's a print code ever got a chance to narrow the result
     * to the correct artwork — {@link #matchText} itself doesn't need to care about that, since
     * it only ever sees one string at a time, but {@link #matchCandidates} does.
     *
     * @param trimmed already-trimmed, non-blank text
     */
    private static Optional<Resolution> matchExactCode(String trimmed) {
        String codeCandidate = trimmed.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");

        if (PASS_CODE_PATTERN.matcher(codeCandidate).matches()) {
            Optional<Card> passCodeMatch = findByPassCode(codeCandidate);
            if (passCodeMatch.isPresent()) {
                Card matchedCard = passCodeMatch.get();
                return resolveKonamiId(
                        parseKonamiId(matchedCard.getKonamiId()), matchedCard, MatchField.PASS_CODE, trimmed);
            }
        }

        Optional<Card> printCodeMatch = findByPrintCode(codeCandidate);
        if (printCodeMatch.isPresent()) {
            MatchResult result = new MatchResult(printCodeMatch.get(), MatchField.PRINT_CODE);
            logMatch(trimmed, result);
            Resolution resolution = result;
            return Optional.of(resolution);
        }
        return Optional.empty();
    }

    /**
     * Resolves a whole detection cycle's worth of OCR candidate lines into a {@link Card} —
     * Unit 6's entry point, used instead of {@link #matchText(String)} when the caller has
     * several recognized lines from one frame (name, print code, pass code may each land as a
     * separate line) rather than a single known-field string.
     *
     * <p>Priority order, across *all* candidates at each tier before moving to the next — not
     * per-candidate in isolation, which matters: a name candidate that already resolves to some
     * card must not win before a print-code candidate elsewhere in the same cycle gets a chance
     * to narrow that name down to the specific artwork actually in frame.
     * <ol>
     *   <li>{@link #matchExactCode} on every candidate — a real, exact pass code or print code
     *       needs no narrowing, so this wins outright wherever it's found.</li>
     *   <li>{@link #matchByFuzzyNameAndPrintCode} — an exact name match (via
     *       {@link CardNameIndex}, covering languages {@link #findByName} still can't) narrowed
     *       by an edit-distance-tolerant print-code read from another candidate in the same
     *       cycle, falling back to a representative card (or {@link #findByName}'s own linear
     *       scan) only once no candidate narrows it.</li>
     * </ol>
     *
     * @param recognizedCandidates OCR candidate lines for one detection cycle, ideally ordered
     *                             highest-confidence first (though this method doesn't require
     *                             that ordering — every candidate is tried at each tier); may be
     *                             {@code null} or empty
     * @return the matched card and how it was resolved, a {@link CardCandidates} if it narrows
     * to a Konami ID with more than one valid printing, or {@link Optional#empty()} if nothing
     * in the database matches any candidate
     */
    public static Optional<Resolution> matchCandidates(List<String> recognizedCandidates) {
        if (recognizedCandidates == null || recognizedCandidates.isEmpty()) {
            return Optional.empty();
        }

        for (String candidate : recognizedCandidates) {
            if (candidate == null) {
                continue;
            }
            String trimmed = candidate.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Optional<Resolution> codeMatch = matchExactCode(trimmed);
            if (codeMatch.isPresent()) {
                return codeMatch;
            }
        }

        return matchByFuzzyNameAndPrintCode(recognizedCandidates);
    }

    /**
     * The name-based fallback tier for {@link #matchCandidates}, reached only once no candidate
     * in the cycle resolved via {@link #matchExactCode}. Tries each candidate as a name via
     * {@link CardNameIndex#getKonamiIdsForName(String)}, skipping any candidate that resolves to
     * zero or more-than-one Konami ID — an empty result means it just isn't a name, and more
     * than one means a genuine cross-language collision that {@link CardNameIndex}'s own javadoc
     * says needs manual confirmation rather than a silent guess, which this method has no way to
     * ask for, so it moves on to the next candidate instead of picking one.
     *
     * <p>Once exactly one Konami ID is found, tries to narrow it to a specific print/artwork via
     * {@link #tryNarrowByPrintCode} using every other candidate from the same cycle, before
     * falling back to a representative card for that Konami ID via
     * {@link #findRepresentativeCard} — the same "pick the card the primary artwork's pass code
     * points to" resolution {@link Database} itself already uses when building
     * {@link Database#getAllPrintedCardsList()}. Only once no candidate resolves a Konami ID at
     * all does this fall further back to {@link #findByName}'s own linear scan, as a last resort
     * for a name that exists as a live {@link Card} object but isn't reachable via
     * {@link CardNameIndex} (e.g. a gap between its data source and {@link Database}'s).
     */
    private static Optional<Resolution> matchByFuzzyNameAndPrintCode(List<String> recognizedCandidates) {
        for (String nameCandidate : recognizedCandidates) {
            Set<Integer> konamiIds = CardNameIndex.getKonamiIdsForName(nameCandidate);
            if (konamiIds.size() != 1) {
                continue;
            }
            Integer konamiId = konamiIds.iterator().next();

            Optional<MatchResult> printCodeNarrowed =
                    tryNarrowByPrintCode(konamiId, nameCandidate, recognizedCandidates);
            if (printCodeNarrowed.isPresent()) {
                Resolution resolution = printCodeNarrowed.get();
                return Optional.of(resolution);
            }

            Optional<Card> representativeCard = findRepresentativeCard(konamiId);
            if (representativeCard.isPresent()) {
                return resolveKonamiId(konamiId, representativeCard.get(), MatchField.NAME, nameCandidate);
            }
        }

        for (String nameCandidate : recognizedCandidates) {
            Optional<Card> nameMatch = findByName(nameCandidate);
            if (nameMatch.isPresent()) {
                Card matchedCard = nameMatch.get();
                return resolveKonamiId(
                        parseKonamiId(matchedCard.getKonamiId()), matchedCard, MatchField.NAME, nameCandidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Tries to narrow {@code konamiId} to one specific print code by running every candidate
     * other than {@code nameCandidate} itself through {@link CardNameIndex#matchPrintCode},
     * keeping whichever produces the closest (lowest edit-distance) match across all of them.
     *
     * @return a {@link MatchResult} resolved via {@link Database#getAllPrintedCardsList()} if
     * the closest match is within {@link #PRINT_CODE_MAX_EDIT_DISTANCE}, otherwise empty
     */
    private static Optional<MatchResult> tryNarrowByPrintCode(

            Integer konamiId, String nameCandidate, List<String> recognizedCandidates) {
        try {
            CardNameIndex.PrintCodeMatch bestMatch = null;
            for (String otherCandidate : recognizedCandidates) {
                if (otherCandidate.equals(nameCandidate)) {
                    continue;
                }
                List<CardNameIndex.PrintCodeMatch> matches =
                        CardNameIndex.matchPrintCode(otherCandidate, Set.of(konamiId));
                if (!matches.isEmpty()
                        && (bestMatch == null || matches.get(0).getEditDistance() < bestMatch.getEditDistance())) {
                    bestMatch = matches.get(0);
                }
            }
            if (bestMatch == null || bestMatch.getEditDistance() > PRINT_CODE_MAX_EDIT_DISTANCE) {
                return Optional.empty();
            }
            Card printedCard = Database.getAllPrintedCardsList().get(bestMatch.getPrintCode());
            if (printedCard == null) {
                return Optional.empty();
            }
            MatchResult result = new MatchResult(printedCard, MatchField.NAME_AND_PRINT_CODE);
            logMatch(nameCandidate + " + " + bestMatch.getPrintCode(), result);
            return Optional.of(result);
        } catch (URISyntaxException uriSyntaxException) {
            throw new RuntimeException(uriSyntaxException);
        }
    }

    /**
     * Resolves a Konami ID to a representative {@link Card} when no print code narrowed it to a
     * specific artwork — the same primary-artwork lookup chain
     * {@code Database#createAllPrintedCardsList} already uses internally (Konami ID to pass
     * code via {@link CardDatabaseManager#getKonamiIdToPassCode()}, then pass code to
     * {@link Card} via {@link Database#getAllCardsList()}), reused here rather than duplicated.
     */
    private static Optional<Card> findRepresentativeCard(Integer konamiId) {
        try {
            Integer representativePassCode = CardDatabaseManager.getKonamiIdToPassCode().get(konamiId);
            if (representativePassCode == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(Database.getAllCardsList().get(representativePassCode));
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Unit 7's fix, shared by every tier that identifies a Konami ID via an exact pass-code or
     * name match: rather than handing {@code fallbackCard} straight back (an arbitrary, printCode
     * -less artwork straight out of {@link Database#getAllCardsList()}), checks how many print
     * codes {@link PrintCodeToKonamiId} actually knows are valid for {@code konamiId} and resolves
     * accordingly:
     * <ul>
     *   <li>none known — falls back to {@code fallbackCard} unchanged, the same degraded-but-
     *       working behavior as before this unit, for names/pass codes not yet covered by the
     *       ygoresources print-code data;</li>
     *   <li>exactly one — resolved unambiguously through
     *       {@link Database#getAllPrintedCardsList()}, which does set the print code correctly;</li>
     *   <li>more than one — returns a {@link CardCandidates} instead of guessing, for the caller
     *       to render (Units 8/9) or, until then, simply report as ambiguous.</li>
     * </ul>
     *
     * @param konamiId     the already-identified Konami ID, or {@code null} if the matched card
     *                     has none — falls back to {@code fallbackCard} unchanged in that case too
     * @param fallbackCard the card originally matched via {@link #findByPassCode},
     *                     {@link #findByName}, or {@link #findRepresentativeCard}
     * @param matchedField which field the original match came through
     * @param matchedText  the original recognized text, for logging only
     */
    private static Optional<Resolution> resolveKonamiId(
            Integer konamiId, Card fallbackCard, MatchField matchedField, String matchedText) {
        if (konamiId == null) {
            return Optional.of(wrapAsMatch(fallbackCard, matchedField, matchedText));
        }
        try {
            List<String> validPrintCodes = PrintCodeToKonamiId.getKonamiIdToPrintCodes().get(String.valueOf(konamiId));
            if (validPrintCodes == null || validPrintCodes.isEmpty()) {
                return Optional.of(wrapAsMatch(fallbackCard, matchedField, matchedText));
            }
            if (validPrintCodes.size() == 1) {
                Card printedCard = Database.getAllPrintedCardsList().get(validPrintCodes.get(0));
                return Optional.of(wrapAsMatch(printedCard != null ? printedCard : fallbackCard, matchedField, matchedText));
            }
            return Optional.of(buildCardCandidates(konamiId, validPrintCodes, matchedField, matchedText));
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Wraps {@code card} as a confident {@link MatchResult}, logging it the same way every other
     * confident match already does.
     */
    private static MatchResult wrapAsMatch(Card card, MatchField matchedField, String matchedText) {
        MatchResult result = new MatchResult(card, matchedField);
        logMatch(matchedText, result);
        return result;
    }

    /**
     * Builds a {@link CardCandidates} for a Konami ID with more than one valid print code: every
     * print code from {@code validPrintCodes} (tagged with a best-effort parsed language via
     * {@link #parseLanguageFromPrintCode}), plus every artwork variant via
     * {@link CardDatabaseManager#getAliasCards(int)} — both pieces already existed and are already
     * used elsewhere in the app (the fuzzy print-code tier and {@code View.CardEditPopup}'s
     * artwork picker, respectively), so this just wires them together for a caller to render.
     */
    private static CardCandidates buildCardCandidates(
            Integer konamiId, List<String> validPrintCodes, MatchField matchedField, String matchedText)
            throws Exception {
        List<CardCandidates.PrintCodeOption> printCodeOptions = new ArrayList<>();
        for (String printCode : validPrintCodes) {
            printCodeOptions.add(new CardCandidates.PrintCodeOption(printCode, parseLanguageFromPrintCode(printCode)));
        }

        Integer representativePassCode = CardDatabaseManager.getKonamiIdToPassCode().get(konamiId);
        List<Card> artworkOptions = representativePassCode != null
                ? CardDatabaseManager.getAliasCards(representativePassCode)
                : List.of();

        logger.debug("Recognized text \"{}\" narrowed to Konami ID {} with {} possible printings; "
                        + "deferring the exact print/artwork choice to the caller",
                matchedText, konamiId, printCodeOptions.size());
        return new CardCandidates(konamiId, matchedField, printCodeOptions, artworkOptions);
    }

    /**
     * Parses a {@link Card#getKonamiId()} string into an {@link Integer}, or {@code null} if it's
     * absent or not a valid number — a card resolved via {@link #findByPassCode} or
     * {@link #findByName} isn't guaranteed to have one (e.g. not yet completed in
     * {@link CardDatabaseManager}), and {@link #resolveKonamiId} treats that the same as "no
     * Konami ID" rather than throwing.
     */
    private static Integer parseKonamiId(String rawKonamiId) {
        if (rawKonamiId == null) {
            return null;
        }
        try {
            return Integer.valueOf(rawKonamiId);
        } catch (NumberFormatException numberFormatException) {
            return null;
        }
    }

    /**
     * Best-effort language extraction from a print code string, e.g. {@code "LOB-EN001"} to
     * {@code "EN"} — the letters immediately after the last hyphen and before the trailing digit
     * run. {@link CardNameIndex} merges every language into one name lookup with no language tag
     * of its own to reuse instead, so this parses it straight out of the print code's shape rather
     * than tracking which language the OCR side actually saw (deferred to a later unit — see the
     * class javadoc).
     *
     * @param printCode the print code to parse, exactly as stored (e.g. from
     *                  {@link PrintCodeToKonamiId#getKonamiIdToPrintCodes()})
     * @return the parsed language segment, uppercased, or {@code null} if the print code's shape
     * didn't match (no hyphen, or no letters before a digit run after it)
     */
    private static String parseLanguageFromPrintCode(String printCode) {
        if (printCode == null) {
            return null;
        }
        int lastHyphenIndex = printCode.lastIndexOf('-');
        if (lastHyphenIndex < 0 || lastHyphenIndex == printCode.length() - 1) {
            return null;
        }
        Matcher languageMatcher = PRINT_CODE_LANGUAGE_PATTERN.matcher(printCode.substring(lastHyphenIndex + 1));
        return languageMatcher.find() ? languageMatcher.group(1).toUpperCase(Locale.ROOT) : null;
    }

    /**
     * Looks up a card by exact pass code among all loaded cards.
     * <p>
     * {@link Database#getAllCardsList()} is keyed by card-image id, not by
     * pass code, so this is a linear scan rather than a map lookup — the same
     * cost {@code Controller.CardFilterMatcher} already pays scanning the
     * same ~22,000-card list on every filter keystroke.
     * </p>
     * <p>
     * A pass code is shared across every reprint and artwork of a card, so the returned
     * {@link Card} is just whichever one this scan hits first — never hand it straight back to a
     * caller; every current caller routes it through {@link #resolveKonamiId} instead.
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
     * against every language name field {@link Card} declares, via a linear
     * scan over {@link Database#getAllCardsList()}.
     * <p>
     * {@code name_CN} has no data source in this project yet (nothing populates
     * it in {@link Database#createAllCardsList}) so that branch stays inert;
     * the other eight are all populated today. This method intentionally stays
     * a linear scan over live {@link Card} objects rather than switching to
     * {@link Model.Database.CardNameIndex}'s faster Konami-ID-keyed lookup,
     * so it keeps resolving cards that only exist as live objects in
     * {@link Database#getAllCardsList()} (as opposed to a Konami ID in
     * {@link Model.Database.KonamiIdToNames}) — {@link #matchCandidates}'s
     * fuzzy fallback tier is where {@link Model.Database.CardNameIndex}
     * actually gets used, precisely because it doesn't need that.
     * </p>
     * <p>
     * {@link Database#getAllCardsList()} holds one entry per artwork sharing the same name, so
     * the returned {@link Card} is just whichever artwork this scan hits first — never hand it
     * straight back to a caller; every current caller routes it through {@link #resolveKonamiId}
     * instead.
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
     * Which field a {@link Resolution} — either a {@link MatchResult} or a
     * {@link CardCandidates} — was resolved through. {@link #PRINT_CODE} and
     * {@link #NAME_AND_PRINT_CODE} are always unambiguous and so never appear on a
     * {@link CardCandidates}; {@link #PASS_CODE} and {@link #NAME} can go either way, depending
     * on how many print codes {@link #resolveKonamiId} finds for the underlying Konami ID.
     */
    public enum MatchField {
        PASS_CODE,
        PRINT_CODE,
        NAME,
        /**
         * Resolved via {@link #matchByFuzzyNameAndPrintCode}: an exact name match narrowed to a
         * specific print/artwork by an edit-distance-tolerant print-code match on a separate
         * candidate line, rather than either exact tier in {@link #matchText}.
         */
        NAME_AND_PRINT_CODE
    }

    /**
     * A resolved detection: either a confident {@link MatchResult}, or — when an exact pass-code
     * or name match narrows a card down to a Konami ID with more than one valid printing — a
     * {@link CardCandidates} for the caller to choose among instead of guessing. See the class
     * javadoc's "Unit 7" note and {@link #resolveKonamiId} for why this split exists.
     */
    public sealed interface Resolution {
        /**
         * @return which recognized field (pass code, print code, or name) produced this
         * resolution
         */
        MatchField getMatchedField();
    }

    /**
     * A successful, unambiguous text-to-card resolution.
     */
    public static final class MatchResult implements Resolution {
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

        @Override
        public MatchField getMatchedField() {
            return matchedField;
        }
    }

    /**
     * Every print code and artwork variant valid for a Konami ID that an exact pass-code or name
     * match narrowed down to, when more than one exists and neither {@link #matchText} nor
     * {@link #matchCandidates} can narrow it any further on their own. A future unit's UI can
     * render {@link #getPrintCodeOptions()} and {@link #getArtworkOptions()} as buttons for the
     * user to pick between; until then, a caller can treat receiving one of these as "ambiguous,
     * needs a person" and report accordingly rather than inserting anything.
     */
    public static final class CardCandidates implements Resolution {
        private final int konamiId;
        private final MatchField matchedField;
        private final List<PrintCodeOption> printCodeOptions;
        private final List<Card> artworkOptions;

        CardCandidates(int konamiId, MatchField matchedField,
                       List<PrintCodeOption> printCodeOptions, List<Card> artworkOptions) {
            this.konamiId = konamiId;
            this.matchedField = matchedField;
            this.printCodeOptions = List.copyOf(printCodeOptions);
            this.artworkOptions = List.copyOf(artworkOptions);
        }

        /**
         * @return the Konami ID every candidate below belongs to
         */
        public int getKonamiId() {
            return konamiId;
        }

        @Override
        public MatchField getMatchedField() {
            return matchedField;
        }

        /**
         * @return every print code valid for {@link #getKonamiId()}, each tagged with a
         * best-effort parsed language (see {@link #parseLanguageFromPrintCode}); never empty,
         * since {@link #resolveKonamiId} only builds a {@link CardCandidates} when there are at
         * least two
         */
        public List<PrintCodeOption> getPrintCodeOptions() {
            return printCodeOptions;
        }

        /**
         * @return every artwork variant for {@link #getKonamiId()}, via
         * {@link CardDatabaseManager#getAliasCards(int)}; possibly empty if the Konami ID
         * couldn't be resolved back to a pass code
         */
        public List<Card> getArtworkOptions() {
            return artworkOptions;
        }

        /**
         * A display name for feedback or logging, taken from the first artwork option — {@code
         * null} only if {@link #getArtworkOptions()} came back empty, which would itself point to
         * a deeper database inconsistency worth investigating separately.
         *
         * @return a display name for these candidates, or {@code null}
         */
        public String getDisplayName() {
            return artworkOptions.isEmpty() ? null : artworkOptions.get(0).getNameOrNumber();
        }

        /**
         * One print code valid for a {@link CardCandidates}' Konami ID, tagged with a
         * best-effort parsed language.
         */
        public static final class PrintCodeOption {
            private final String printCode;
            private final String language;

            PrintCodeOption(String printCode, String language) {
                this.printCode = printCode;
                this.language = language;
            }

            /**
             * @return the print code, exactly as stored — usable directly as a key into
             * {@link Database#getAllPrintedCardsList()}
             */
            public String getPrintCode() {
                return printCode;
            }

            /**
             * @return the best-effort parsed language (e.g. {@code "EN"}), or {@code null} if
             * {@link #parseLanguageFromPrintCode} couldn't parse one from this print code's shape
             */
            public String getLanguage() {
                return language;
            }
        }
    }
}