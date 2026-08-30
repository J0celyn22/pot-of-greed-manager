package Utils;

/**
 * Quantizes card-image decode dimensions into a small set of fixed tiers, and tracks which
 * tier is currently active for the main collection grid's zoom level.
 *
 * <p>{@link LruImageCache} used to be keyed by file path alone, so once an image was decoded
 * at one size it stayed that size in the cache forever — zooming in on the collection grid
 * (Ctrl+scroll) stretched the already-decoded thumbnail instead of loading a sharper one,
 * which is the root cause of the "resolution doesn't follow zoom" report. Quantizing into
 * tiers (rather than caching one entry per exact pixel width) keeps the cache useful between
 * two adjacent zoom steps instead of invalidating on every scroll tick.</p>
 *
 * <p>No JavaFX imports — this class is pure arithmetic, testable without the FX toolkit.</p>
 */
public final class CardImageResolution {

    /**
     * Height/width ratio of card images, matching the 146/100 used by
     * {@code Controller.RealMainController.adjustCardSize} for the same cards.
     */
    public static final double CARD_ASPECT_RATIO = 1.46;

    private static final int TIER_STEP = 64;
    private static final int MIN_TIER = 64;

    /**
     * The decode width the main collection grid is currently loading at, published by the
     * zoom debounce in {@code Controller.RealMainController} once a zoom gesture settles.
     * Written on the FX thread, read from the image-decoding background threads —
     * {@code volatile} is the visibility guarantee needed, no other synchronization applies
     * since it's a single independent value.
     *
     * <p>A single global tier, not one per view: the whole app shares one pair of
     * {@code cardWidthProperty}/{@code cardHeightProperty} instances (handed by reference from
     * {@code RealMainController} to every sub-controller, including the camera-scanner
     * artwork gallery), so there is only one zoom state to track.</p>
     */
    private static volatile int activeDecodeWidth = quantizeDecodeWidth(100);

    private CardImageResolution() {
    }

    /**
     * Rounds {@code displayWidth} up to the nearest multiple of {@value #TIER_STEP} pixels
     * (minimum {@value #MIN_TIER}). The result is always {@code >= displayWidth}, so an
     * {@code ImageView} bound to it never has to upscale a decoded image — which is exactly
     * what makes this the fix for the blur-at-zoom report: no upscale, no blur.
     */
    public static int quantizeDecodeWidth(double displayWidth) {
        if (displayWidth <= 0) {
            return MIN_TIER;
        }
        int tierCount = (int) Math.ceil(displayWidth / TIER_STEP);
        return Math.max(MIN_TIER, tierCount * TIER_STEP);
    }

    /**
     * The decode height matching {@code decodeWidth}, at the fixed card aspect ratio.
     */
    public static double decodeHeightFor(int decodeWidth) {
        return decodeWidth * CARD_ASPECT_RATIO;
    }

    /**
     * Builds the composite cache key for {@code imagePath} decoded at {@code decodeWidth}.
     * The only place a cache key should ever be constructed — callers pass the components,
     * never a pre-built key, so every reader and writer stays in sync automatically.
     */
    public static String cacheKey(String imagePath, int decodeWidth) {
        return imagePath + "@" + decodeWidth;
    }

    public static int getActiveDecodeWidth() {
        return activeDecodeWidth;
    }

    public static void setActiveDecodeWidth(int decodeWidth) {
        activeDecodeWidth = decodeWidth;
    }
}
