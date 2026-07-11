package Utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Partition-based tests for {@link CardNameUtils}.
 */
class CardNameUtilsTest {

    // ── sanitize ─────────────────────────────────────────────────────────────

    @Test
    void sanitize_null_returnsEmptyString() {
        assertEquals("", CardNameUtils.sanitize(null));
    }

    @Test
    void sanitize_emptyString_returnsEmptyString() {
        assertEquals("", CardNameUtils.sanitize(""));
    }

    @Test
    void sanitize_whitespaceOnly_returnsEmptyString() {
        assertEquals("", CardNameUtils.sanitize("   "));
    }

    @Test
    void sanitize_noDecorators_returnsUnchanged() {
        assertEquals("MyDeck", CardNameUtils.sanitize("MyDeck"));
    }

    @Test
    void sanitize_leadingDecoratorsOnly_stripped() {
        assertEquals("MyDeck", CardNameUtils.sanitize("==MyDeck"));
    }

    @Test
    void sanitize_trailingDecoratorsOnly_stripped() {
        assertEquals("MyDeck", CardNameUtils.sanitize("MyDeck=="));
    }

    @Test
    void sanitize_bothLeadingAndTrailing_stripped() {
        assertEquals("MyDeck", CardNameUtils.sanitize("==MyDeck=="));
    }

    @Test
    void sanitize_mixedDecoratorCharacters_bothStripped() {
        assertEquals("MyDeck", CardNameUtils.sanitize("=-MyDeck-="));
    }

    @Test
    void sanitize_genuineInternalHyphen_preserved() {
        assertEquals("My-Deck", CardNameUtils.sanitize("My-Deck"));
    }

    @Test
    void sanitize_genuineInternalHyphen_preservedEvenWithOuterDecorators() {
        assertEquals("My-Deck", CardNameUtils.sanitize("=My-Deck="));
    }

    @Test
    void sanitize_entirelyDecoratorCharacters_returnsEmptyString() {
        assertEquals("", CardNameUtils.sanitize("===="));
    }

    @Test
    void sanitize_whitespaceBetweenDecoratorAndText_trimmedByFinalTrim() {
        // The decorator-stripping loops stop at the space, leaving " MyDeck "
        // as the substring; the method's own trailing .trim() call is what
        // actually removes that inner-but-now-edge whitespace.
        assertEquals("MyDeck", CardNameUtils.sanitize("== MyDeck =="));
    }

    @Test
    void sanitize_outerWhitespaceAroundDecorators_trimmedFirst() {
        assertEquals("MyDeck", CardNameUtils.sanitize("  ==MyDeck==  "));
    }

    // ── rebuildDecoratedName ─────────────────────────────────────────────────

    @Test
    void rebuildDecoratedName_nullRaw_returnsNewNameUnchanged() {
        assertEquals("Red-Eyes", CardNameUtils.rebuildDecoratedName(null, "Red-Eyes", '='));
    }

    @Test
    void rebuildDecoratedName_emptyRaw_returnsNewNameUnchanged() {
        assertEquals("Red-Eyes", CardNameUtils.rebuildDecoratedName("", "Red-Eyes", '='));
    }

    @Test
    void rebuildDecoratedName_rawWithNoDecoratorsOfThatType_returnsNewNameUnchanged() {
        assertEquals("Red-Eyes", CardNameUtils.rebuildDecoratedName("Blue-Eyes", "Red-Eyes", '='));
    }

    @Test
    void rebuildDecoratedName_docstringExample_bothSidesPreserved() {
        assertEquals("==Red-Eyes==", CardNameUtils.rebuildDecoratedName("==Blue-Eyes==", "Red-Eyes", '='));
    }

    @Test
    void rebuildDecoratedName_leadingOnly_prefixPreservedNoSuffix() {
        assertEquals("==Red-Eyes", CardNameUtils.rebuildDecoratedName("==Blue-Eyes", "Red-Eyes", '='));
    }

    @Test
    void rebuildDecoratedName_trailingOnly_suffixPreservedNoPrefix() {
        assertEquals("Red-Eyes==", CardNameUtils.rebuildDecoratedName("Blue-Eyes==", "Red-Eyes", '='));
    }

    @Test
    void rebuildDecoratedName_decoratorCharacterIsTypeSpecific() {
        // Raw has "-" decorators, but the decorator argument here is "=" ->
        // no match, so the "-" decorators are not recognized as such.
        assertEquals("Red-Eyes", CardNameUtils.rebuildDecoratedName("-Blue-Eyes-", "Red-Eyes", '='));
    }

    @Test
    void rebuildDecoratedName_oldInternalContentIsFullyReplaced_notMerged() {
        // The whole segment between the outer decorators is discarded and
        // replaced by newDisplayName -- any decorator-like characters that
        // happened to be inside the old content (not just at the edges) are
        // simply gone, not selectively preserved.
        assertEquals("=Red-Eyes=", CardNameUtils.rebuildDecoratedName("=Old=Name=", "Red-Eyes", '='));
    }

    @Test
    void rebuildDecoratedName_entirelyDecoratorCharacters_treatedAsBothLeadingAndTrailing() {
        assertEquals("==NewName==", CardNameUtils.rebuildDecoratedName("==", "NewName", '='));
    }
}