package Utils;

import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LruImageCache.java
 * <p>
 * An enhanced image cache using a least-recently-used (LRU) algorithm.
 * Images are stored in memory via SoftReferences to allow JVM garbage collection when needed.
 * Keyed by file path plus decode width (see {@link CardImageResolution#cacheKey}), so an
 * image decoded small never gets served back as a substitute for one wanted larger.
 */
public class LruImageCache {

    private static final Logger logger = LoggerFactory.getLogger(LruImageCache.class);

    // Dynamically determine the maximum number of cached images based on available JVM memory.
    private static final int MAX_ENTRIES;

    static {
        long maxMemory = Runtime.getRuntime().maxMemory();
        if (maxMemory < 128L * 1024 * 1024) {  // Less than 128 MB.
            MAX_ENTRIES = 50;
        } else if (maxMemory < 256L * 1024 * 1024) {  // 128 MB to 256 MB.
            MAX_ENTRIES = 150;
        } else if (maxMemory < 512L * 1024 * 1024) {  // 256 MB to 512 MB.
            MAX_ENTRIES = 300;
        } else if (maxMemory < 1024L * 1024 * 1024) {  // 512 MB to 1024 MB.
            MAX_ENTRIES = 600;
        } else if (maxMemory < 2048L * 1024 * 1024) {  // 1024 MB to 2048 MB.
            MAX_ENTRIES = 1200;
        } else if (maxMemory < 4096L * 1024 * 1024) {  // 2048 MB to 4096 MB.
            MAX_ENTRIES = 2400;
        } else if (maxMemory < 8192L * 1024 * 1024) {  // 4096 MB to 8192 MB.
            MAX_ENTRIES = 4800;
        } else if (maxMemory < 16384L * 1024 * 1024) {  // 8192 MB to 16384 MB.
            MAX_ENTRIES = 9600;
        } else {
            MAX_ENTRIES = 19200;
        }
        logger.debug("Max cache entries: {}", MAX_ENTRIES);
    }

    // The cache stores images using SoftReferences, keyed by their file path.
    private static final Map<String, SoftReference<Image>> imageCache =
            new LinkedHashMap<String, SoftReference<Image>>(MAX_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, SoftReference<Image>> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    /**
     * Retrieves an image decoded at {@code decodeWidth} from the cache, by file path.
     *
     * <p>The decode width is part of the key, not just the path: an image decoded small and
     * then found here for a larger display size would have to be upscaled by the
     * {@code ImageView}, which is exactly the blur this dimension-aware key exists to avoid.
     * See {@link CardImageResolution} for why the width is quantized into tiers rather than
     * used as an exact pixel value.</p>
     *
     * @param imagePath   the file path of the image
     * @param decodeWidth the decode width it was (or would be) loaded at
     * @return the cached Image if available; otherwise, null.
     */
    public static synchronized Image getImage(String imagePath, int decodeWidth) {
        String key = CardImageResolution.cacheKey(imagePath, decodeWidth);
        SoftReference<Image> ref = imageCache.get(key);
        if (ref != null) {
            Image image = ref.get();
            if (image != null) {
                return image;
            } else {
                imageCache.remove(key);
            }
        }
        return null;
    }

    /**
     * Caches an image under its file path and the decode width it was loaded at.
     *
     * @param imagePath   the file path of the image
     * @param decodeWidth the decode width it was loaded at
     * @param image       the Image to cache
     */
    public static synchronized void addImage(String imagePath, int decodeWidth, Image image) {
        String key = CardImageResolution.cacheKey(imagePath, decodeWidth);
        imageCache.put(key, new SoftReference<>(image));
    }

    /**
     * Clears the image cache.
     */
    public static synchronized void clearCache() {
        imageCache.clear();
    }

    /**
     * @return the current number of cached entries (diagnostic use — see {@code CardScannerCoordinator}'s
     * periodic memory-diagnostics log).
     */
    public static synchronized int size() {
        return imageCache.size();
    }
}