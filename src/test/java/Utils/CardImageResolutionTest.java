package Utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-logic coverage for {@link CardImageResolution} — the zoom-blur fix's tiering scheme.
 * No FX toolkit involved: the class has no javafx imports by design.
 */
class CardImageResolutionTest {

    @Test
    void quantizeDecodeWidth_knownValues() {
        assertEquals(64, CardImageResolution.quantizeDecodeWidth(50));
        assertEquals(128, CardImageResolution.quantizeDecodeWidth(100));
        assertEquals(128, CardImageResolution.quantizeDecodeWidth(128));
        assertEquals(192, CardImageResolution.quantizeDecodeWidth(129));
        assertEquals(320, CardImageResolution.quantizeDecodeWidth(300));
    }

    @Test
    void quantizeDecodeWidth_neverUpscales_acrossTheWholeZoomRange() {
        // The property that IS the fix: the decode tier must never fall below the display
        // width, or the ImageView has to stretch a too-small image -- which is the blur bug.
        for (double displayWidth = 50; displayWidth <= 300; displayWidth += 1) {
            int decodeWidth = CardImageResolution.quantizeDecodeWidth(displayWidth);
            assertTrue(decodeWidth >= displayWidth,
                    "decodeWidth " + decodeWidth + " < displayWidth " + displayWidth);
        }
    }

    @Test
    void quantizeDecodeWidth_isMonotonicallyNonDecreasing() {
        int previousDecodeWidth = CardImageResolution.quantizeDecodeWidth(50);
        for (double displayWidth = 51; displayWidth <= 300; displayWidth += 1) {
            int decodeWidth = CardImageResolution.quantizeDecodeWidth(displayWidth);
            assertTrue(decodeWidth >= previousDecodeWidth,
                    "tier decreased at displayWidth=" + displayWidth);
            previousDecodeWidth = decodeWidth;
        }
    }

    @Test
    void quantizeDecodeWidth_zeroAndNegative_returnMinimumTierWithoutException() {
        assertEquals(64, CardImageResolution.quantizeDecodeWidth(0));
        assertEquals(64, CardImageResolution.quantizeDecodeWidth(-50));
    }

    @Test
    void decodeHeightFor_appliesCardAspectRatio() {
        assertEquals(128 * 1.46, CardImageResolution.decodeHeightFor(128), 0.01);
    }

    @Test
    void cacheKey_distinctPerDecodeWidth() {
        String key128 = CardImageResolution.cacheKey("file:/a.jpg", 128);
        String key192 = CardImageResolution.cacheKey("file:/a.jpg", 192);
        assertNotEquals(key128, key192);
    }

    @Test
    void cacheKey_deterministic() {
        assertEquals(
                CardImageResolution.cacheKey("file:/a.jpg", 128),
                CardImageResolution.cacheKey("file:/a.jpg", 128));
    }

    @Test
    void activeDecodeWidth_readsBackWhatWasSet() {
        CardImageResolution.setActiveDecodeWidth(256);
        assertEquals(256, CardImageResolution.getActiveDecodeWidth());
        // Leave the shared static field in its default state for any other test relying on it.
        CardImageResolution.setActiveDecodeWidth(CardImageResolution.quantizeDecodeWidth(100));
    }
}
