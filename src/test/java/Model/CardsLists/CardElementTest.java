package Model.CardsLists;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Partition-based tests for {@link CardElement}.
 * <p>
 * All fixture {@link Card}s use non-numeric, non-print-code identifiers
 * (e.g. "TESTCARD") so construction never depends on the local card
 * database or network — see {@code CardFactory.createCardFromPassCode},
 * which falls back to a passCode-only stub whenever the id isn't a plain
 * integer.
 */
class CardElementTest {

    // ── CardElement(Card, Boolean, Boolean, Boolean, Boolean) ───────────────
    // Partition: isOwned true / false / null (null must behave like false).

    @Test
    void fullConstructor_isOwnedTrue_setsOwned() {
        Card card = new Card();
        CardElement ce = new CardElement(card, false, true, false, false);
        assertEquals(OwnershipStatus.OWNED, ce.getOwnershipStatus());
    }

    @Test
    void fullConstructor_isOwnedFalse_setsMissing() {
        Card card = new Card();
        CardElement ce = new CardElement(card, false, false, false, false);
        assertEquals(OwnershipStatus.MISSING, ce.getOwnershipStatus());
    }

    @Test
    void fullConstructor_isOwnedNull_treatedAsMissing() {
        Card card = new Card();
        CardElement ce = new CardElement(card, false, null, false, false);
        assertEquals(OwnershipStatus.MISSING, ce.getOwnershipStatus());
    }

    // ── CardElement(Card) — hasNonDefaultArtwork / parseArtNumber boundary ──

    @Test
    void cardConstructor_nullCard_noArtworkCrash() {
        CardElement ce = new CardElement((Card) null);
        assertEquals(Boolean.FALSE, ce.getSpecificArtwork());
        assertEquals(0, ce.getArtwork());
    }

    @Test
    void cardConstructor_nullArtNumber_notSpecificArtwork() {
        Card card = new Card();
        CardElement ce = new CardElement(card);
        assertEquals(Boolean.FALSE, ce.getSpecificArtwork());
    }

    @Test
    void cardConstructor_artNumberExactlyOne_isDefault_notSpecific() {
        Card card = new Card();
        card.setArtNumber("1");
        CardElement ce = new CardElement(card);
        assertEquals(Boolean.FALSE, ce.getSpecificArtwork(),
                "artNumber \"1\" is the default artwork and must not be treated as specific");
    }

    @Test
    void cardConstructor_artNumberTwo_justAboveBoundary_isSpecific() {
        Card card = new Card();
        card.setArtNumber("2");
        CardElement ce = new CardElement(card);
        assertEquals(Boolean.TRUE, ce.getSpecificArtwork());
        assertEquals(2, ce.getArtwork());
    }

    @Test
    void cardConstructor_artNumberWithWhitespace_isTrimmed() {
        Card card = new Card();
        card.setArtNumber(" 3 ");
        CardElement ce = new CardElement(card);
        assertEquals(Boolean.TRUE, ce.getSpecificArtwork());
        assertEquals(3, ce.getArtwork());
    }

    @Test
    void cardConstructor_nonNumericArtNumber_degradesToZero_doesNotThrow() {
        Card card = new Card();
        card.setArtNumber("abc");
        CardElement ce = assertDoesNotThrow(() -> new CardElement(card));
        assertEquals(Boolean.FALSE, ce.getSpecificArtwork());
        assertEquals(0, ce.getArtwork());
    }

    @Test
    void cardConstructor_artNumberZero_notSpecific() {
        Card card = new Card();
        card.setArtNumber("0");
        CardElement ce = new CardElement(card);
        assertEquals(Boolean.FALSE, ce.getSpecificArtwork());
    }

    // ── Copy constructor ─────────────────────────────────────────────────────

    @Test
    void copyConstructor_resetsOwnershipToMissing_evenIfSourceWasOwned() {
        Card card = new Card();
        CardElement source = new CardElement(card, false, true, false, false);
        assertEquals(OwnershipStatus.OWNED, source.getOwnershipStatus());

        CardElement copy = new CardElement(source);
        assertEquals(OwnershipStatus.MISSING, copy.getOwnershipStatus());
    }

    @Test
    void copyConstructor_customTags_isDefensiveCopy() {
        Card card = new Card();
        CardElement source = new CardElement(card, false, false, false, false);
        source.addCustomTag("trade");

        CardElement copy = new CardElement(source);
        copy.addCustomTag("signed");

        assertEquals(List.of("trade"), source.getCustomTags(),
                "mutating the copy's tags must not affect the source");
        assertEquals(List.of("trade", "signed"), copy.getCustomTags());
    }

    @Test
    void copyConstructor_nullCustomTagsOnSource_becomesEmptyNotNull() {
        Card card = new Card();
        CardElement source = new CardElement(card, false, false, false, false);
        source.setCustomTags(null);

        CardElement copy = new CardElement(source);
        assertNotNull(copy.getCustomTags());
        assertTrue(copy.getCustomTags().isEmpty());
    }

    @Test
    void copyConstructor_copiesDontRemoveIsInDeckAndRawCode() {
        Card card = new Card();
        CardElement source = new CardElement(card, false, false, true, true);
        source.setRawCode("RAW1");

        CardElement copy = new CardElement(source);
        assertEquals(true, copy.getDontRemove());
        assertEquals(true, copy.getIsInDeck());
        assertEquals("RAW1", copy.getRawCode());
    }

    // ── CardElement(String) — no-comma partition: bare id ───────────────────

    @Test
    void stringConstructor_noComma_allFlagsDefaultOff() throws Exception {
        CardElement ce = new CardElement("TESTCARD");
        assertEquals(OwnershipStatus.MISSING, ce.getOwnershipStatus());
        assertEquals(false, ce.getIsInDeck());
        assertEquals(false, ce.getDontRemove());
        assertEquals(Boolean.FALSE, ce.getSpecificArtwork());
        assertEquals(0, ce.getArtwork());
        assertEquals("TESTCARD", ce.getRawCode());
        assertEquals("TESTCARD", ce.getCard().getPassCode());
    }

    @Test
    void stringConstructor_emptyStringId_cardHasNullPassCode() throws Exception {
        CardElement ce = new CardElement("");
        assertEquals("", ce.getRawCode());
        assertNull(ce.getCard().getPassCode(),
                "an empty id never reaches the stub-card fallback, so passCode stays unset");
    }

    // ── CardElement(String) — comma partition: id,flags ─────────────────────

    @Test
    void stringConstructor_commaNoFlags_allDefaultOff() throws Exception {
        CardElement ce = new CardElement("TESTCARD,");
        assertEquals(OwnershipStatus.MISSING, ce.getOwnershipStatus());
        assertEquals(false, ce.getIsInDeck());
        assertEquals(false, ce.getDontRemove());
        assertEquals(Boolean.FALSE, ce.getSpecificArtwork());
        assertEquals("TESTCARD", ce.getRawCode());
    }

    @Test
    void stringConstructor_ownedFlagOnly() throws Exception {
        CardElement ce = new CardElement("TESTCARD,O");
        assertEquals(OwnershipStatus.OWNED, ce.getOwnershipStatus());
        assertEquals(false, ce.getIsInDeck());
        assertEquals(false, ce.getDontRemove());
    }

    @Test
    void stringConstructor_inDeckFlagOnly() throws Exception {
        CardElement ce = new CardElement("TESTCARD,D");
        assertEquals(true, ce.getIsInDeck());
        assertEquals(OwnershipStatus.MISSING, ce.getOwnershipStatus());
    }

    @Test
    void stringConstructor_dontRemoveFlagOnly() throws Exception {
        CardElement ce = new CardElement("TESTCARD,+");
        assertEquals(true, ce.getDontRemove());
    }

    @Test
    void stringConstructor_specificArtworkOnly() throws Exception {
        CardElement ce = new CardElement("TESTCARD,*2");
        assertEquals(Boolean.TRUE, ce.getSpecificArtwork());
        assertEquals(2, ce.getArtwork());
    }

    @Test
    void stringConstructor_allFlagsCombined() throws Exception {
        CardElement ce = new CardElement("TESTCARD,OD+*3");
        assertEquals(OwnershipStatus.OWNED, ce.getOwnershipStatus());
        assertEquals(true, ce.getIsInDeck());
        assertEquals(true, ce.getDontRemove());
        assertEquals(Boolean.TRUE, ce.getSpecificArtwork());
        assertEquals(3, ce.getArtwork());
    }

    @Test
    void stringConstructor_malformedArtwork_currentlyThrowsNumberFormatException() {
        // Documents current behaviour: unlike parseArtNumber() a few lines above
        // it in the source, this branch has no try/catch around the parse, so a
        // malformed artwork suffix crashes construction instead of degrading to
        // "no specific artwork". Flagging this as a real fragility, not fixing
        // it here since that's a production-code change, not a test-only one.
        assertThrows(NumberFormatException.class, () -> new CardElement("TESTCARD,*abc"));
    }

    // ── CardElement(String) — pipe extension partition ──────────────────────

    @Test
    void stringConstructor_noPipeSection_conditionRarityTagsAllUnset() throws Exception {
        CardElement ce = new CardElement("TESTCARD,O");
        assertNull(ce.getCondition());
        assertNull(ce.getRarity());
        assertTrue(ce.getCustomTags().isEmpty());
    }

    @Test
    void stringConstructor_pipeConditionOnly() throws Exception {
        CardElement ce = new CardElement("TESTCARD,O|NM");
        assertEquals(CardCondition.NEAR_MINT, ce.getCondition());
        assertNull(ce.getRarity());
        assertTrue(ce.getCustomTags().isEmpty());
    }

    @Test
    void stringConstructor_pipeConditionAndRarity() throws Exception {
        CardElement ce = new CardElement("TESTCARD,O|NM|SR");
        assertEquals(CardCondition.NEAR_MINT, ce.getCondition());
        assertEquals(CardRarity.SUPER_RARE, ce.getRarity());
        assertTrue(ce.getCustomTags().isEmpty());
    }

    @Test
    void stringConstructor_pipeConditionRarityAndTags() throws Exception {
        CardElement ce = new CardElement("TESTCARD,O|NM|SR|trade;signed");
        assertEquals(CardCondition.NEAR_MINT, ce.getCondition());
        assertEquals(CardRarity.SUPER_RARE, ce.getRarity());
        assertEquals(List.of("trade", "signed"), ce.getCustomTags());
    }

    @Test
    void stringConstructor_pipeEmptyConditionButRarityPresent() throws Exception {
        CardElement ce = new CardElement("TESTCARD,O||SR");
        assertNull(ce.getCondition());
        assertEquals(CardRarity.SUPER_RARE, ce.getRarity());
    }

    @Test
    void stringConstructor_pipeUnrecognizedCode_silentlyNull() throws Exception {
        CardElement ce = new CardElement("TESTCARD,O|XX");
        assertNull(ce.getCondition(), "an unrecognized condition code must not throw, just resolve to null");
    }

    @Test
    void stringConstructor_pipeTagsWithBlankEntries_areFiltered() throws Exception {
        CardElement ce = new CardElement("TESTCARD,O|NM|SR|trade; ;signed");
        assertEquals(List.of("trade", "signed"), ce.getCustomTags());
    }

    // ── setValues(String) — its own, distinct flags+artwork format ──────────
    // (real usage: ListDifferenceIntersection calls setValues(toString() + marker),
    //  and the "*,N" comma form matches how the artwork branch actually splits.)

    @Test
    void setValues_noFlags_allDefaultOff() {
        CardElement ce = new CardElement(new Card());
        ce.setValues("");
        assertEquals(OwnershipStatus.MISSING, ce.getOwnershipStatus());
        assertEquals(false, ce.getIsInDeck());
        assertEquals(false, ce.getDontRemove());
        assertEquals(Boolean.FALSE, ce.getSpecificArtwork());
        assertEquals(0, ce.getArtwork());
    }

    @Test
    void setValues_ownedFlagOnly() {
        CardElement ce = new CardElement(new Card());
        ce.setValues("O");
        assertEquals(OwnershipStatus.OWNED, ce.getOwnershipStatus());
    }

    @Test
    void setValues_inDeckFlagOnly() {
        CardElement ce = new CardElement(new Card());
        ce.setValues("D");
        assertEquals(true, ce.getIsInDeck());
    }

    @Test
    void setValues_dontRemoveFlagOnly() {
        CardElement ce = new CardElement(new Card());
        ce.setValues("+");
        assertEquals(true, ce.getDontRemove());
    }

    @Test
    void setValues_artworkMarker_commaFormFromRealUsage() {
        // Matches the actual production call shape: flags first, then the
        // comma-separated artwork number, e.g. "OD*,5".
        CardElement ce = new CardElement(new Card());
        ce.setValues("OD*,5");
        assertEquals(OwnershipStatus.OWNED, ce.getOwnershipStatus());
        assertEquals(true, ce.getIsInDeck());
        assertEquals(Boolean.TRUE, ce.getSpecificArtwork());
        assertEquals(5, ce.getArtwork());
    }

    @Test
    void setValues_artworkMarkerAlone_zeroBoundary() {
        CardElement ce = new CardElement(new Card());
        ce.setValues("*,0");
        assertEquals(Boolean.TRUE, ce.getSpecificArtwork());
        assertEquals(0, ce.getArtwork());
    }

    // ── getEffectiveCondition / getOwnershipStatus / setOwnershipStatus null-safety ──

    @Test
    void getEffectiveCondition_defaultsToGood_whenUnset() {
        CardElement ce = new CardElement(new Card());
        assertNull(ce.getCondition());
        assertEquals(CardCondition.GOOD, ce.getEffectiveCondition());
    }

    @Test
    void getEffectiveCondition_returnsExplicitCondition_whenSet() {
        CardElement ce = new CardElement(new Card());
        ce.setCondition(CardCondition.MINT);
        assertEquals(CardCondition.MINT, ce.getEffectiveCondition());
    }

    @Test
    void setOwnershipStatus_null_coercesToMissing() {
        CardElement ce = new CardElement(new Card(), false, true, false, false);
        assertEquals(OwnershipStatus.OWNED, ce.getOwnershipStatus());
        ce.setOwnershipStatus(null);
        assertEquals(OwnershipStatus.MISSING, ce.getOwnershipStatus());
    }

    // ── addCustomTag / removeCustomTag ───────────────────────────────────────

    @Test
    void addCustomTag_null_isNoOp() {
        CardElement ce = new CardElement(new Card());
        ce.addCustomTag(null);
        assertTrue(ce.getCustomTags().isEmpty());
    }

    @Test
    void addCustomTag_blank_isNoOp() {
        CardElement ce = new CardElement(new Card());
        ce.addCustomTag("   ");
        assertTrue(ce.getCustomTags().isEmpty());
    }

    @Test
    void addCustomTag_duplicate_isNotAddedTwice() {
        CardElement ce = new CardElement(new Card());
        ce.addCustomTag("trade");
        ce.addCustomTag("trade");
        assertEquals(List.of("trade"), ce.getCustomTags());
    }

    @Test
    void removeCustomTag_nonExistent_isNoOpDoesNotThrow() {
        CardElement ce = new CardElement(new Card());
        assertDoesNotThrow(() -> ce.removeCustomTag("nope"));
    }

    @Test
    void removeCustomTag_existing_isRemoved() {
        CardElement ce = new CardElement(new Card());
        ce.addCustomTag("trade");
        ce.removeCustomTag("trade");
        assertTrue(ce.getCustomTags().isEmpty());
    }

    // ── toString() — id fallback and flag-marker partitions ─────────────────

    @Test
    void toString_passCodeSet_usedAsBase() {
        Card card = new Card();
        card.setPassCode("111");
        CardElement ce = new CardElement(card, false, false, false, false);
        assertEquals("111", ce.toString());
    }

    @Test
    void toString_passCodeNull_fallsBackToPrintCode() {
        Card card = new Card();
        card.setPrintCode("LOB-EN001");
        CardElement ce = new CardElement(card, false, false, false, false);
        assertEquals("LOB-EN001", ce.toString());
    }

    @Test
    void toString_bothIdsNull_emptyBase() {
        Card card = new Card();
        CardElement ce = new CardElement(card, false, false, false, false);
        assertEquals("", ce.toString());
    }

    @Test
    void toString_allFlagsSet_correctOrderAndMarkers() {
        Card card = new Card();
        card.setPassCode("111");
        card.setArtNumber("4");
        CardElement ce = new CardElement(card, true, true, true, true);
        ce.setArtwork(4);
        assertEquals("111,*4OD+", ce.toString());
    }

    @Test
    void toString_noQualifyingFlags_noCommaAppended() {
        Card card = new Card();
        card.setPassCode("111");
        CardElement ce = new CardElement(card, false, false, false, true); // isInDeck alone doesn't trigger the comma
        assertEquals("111D", ce.toString());
    }

    // ── toCollectionString() — pipe section presence partition ──────────────

    @Test
    void toCollectionString_noExtras_noPipeAppended() {
        Card card = new Card();
        card.setPassCode("111");
        CardElement ce = new CardElement(card, false, false, false, false);
        assertEquals("111", ce.toCollectionString());
    }

    @Test
    void toCollectionString_withConditionRarityAndTags_pipeAppended() {
        Card card = new Card();
        card.setPassCode("111");
        CardElement ce = new CardElement(card, false, true, false, false);
        ce.setCondition(CardCondition.NEAR_MINT);
        ce.setRarity(CardRarity.SUPER_RARE);
        ce.addCustomTag("trade");
        ce.addCustomTag("signed");
        assertEquals("111,O|NM|SR|trade;signed", ce.toCollectionString());
    }

    @Test
    void toCollectionString_printCodeFallback_thenRawCodeFallback() {
        CardElement ce = new CardElement(new Card(), false, false, false, false);
        ce.setRawCode("RAWID");
        assertEquals("RAWID", ce.toCollectionString());
    }

    // ── toThemeCollectionString() — two distinct artwork sources ─────────────

    @Test
    void toThemeCollectionString_specificArtworkFromOwnArtworkField() {
        Card card = new Card();
        card.setPassCode("111");
        CardElement ce = new CardElement(card, true, false, false, false);
        ce.setArtwork(5);
        assertEquals("111,*5", ce.toThemeCollectionString());
    }

    @Test
    void toThemeCollectionString_notSpecific_fallsBackToCardArtNumber_aboveOne() {
        Card card = new Card();
        card.setPassCode("111");
        card.setArtNumber("2");
        CardElement ce = new CardElement(card, false, false, false, false);
        assertEquals("111,*2", ce.toThemeCollectionString());
    }

    @Test
    void toThemeCollectionString_cardArtNumberIsDefault_noMarkerAppended() {
        Card card = new Card();
        card.setPassCode("111");
        card.setArtNumber("1");
        CardElement ce = new CardElement(card, false, false, false, false);
        assertEquals("111", ce.toThemeCollectionString());
    }

    @Test
    void toThemeCollectionString_cardArtNumberNonNumeric_ignoredNotThrown() {
        Card card = new Card();
        card.setPassCode("111");
        card.setArtNumber("abc");
        CardElement ce = new CardElement(card, false, false, false, false);
        assertEquals("111", assertDoesNotThrow(ce::toThemeCollectionString));
    }
}