package Utils;

import javafx.scene.image.Image;

import java.lang.ref.SoftReference;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * LruImageCache.java
 * <p>
 * An enhanced image cache using a least-recently-used (LRU) algorithm.
 * Images are stored in memory via SoftReferences to allow JVM garbage collection when needed.
 * This version uses the original caching logic (with file path as the key).
 */
public class LruImageCache {

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
        System.out.println("LruImageCache: Max cache entries: " + MAX_ENTRIES);
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
     * Retrieves an image from the cache by its file path.
     *
     * @param imagePath the file path of the image
     * @return the cached Image if available; otherwise, null.
     */
    public static synchronized Image getImage(String imagePath) {
        SoftReference<Image> ref = imageCache.get(imagePath);
        if (ref != null) {
            Image image = ref.get();
            if (image != null) {
                return image;
            } else {
                imageCache.remove(imagePath);
            }
        }
        return null;
    }

    /**
     * Caches an image under its file path.
     *
     * @param imagePath the file path of the image
     * @param image     the Image to cache
     */
    public static synchronized void addImage(String imagePath, Image image) {
        imageCache.put(imagePath, new SoftReference<>(image));
    }

    /**
     * Clears the image cache.
     */
    public static synchronized void clearCache() {
        imageCache.clear();
    }
}