// LruImageCache.java
package Utils;

import javafx.scene.image.Image;

import java.util.LinkedHashMap;
import java.util.Map;

public class LruImageCache {

    private static final int MAX_ENTRIES = 1000; // Adjust based on memory constraints

    private static final Map<String, Image> imageCache = new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public static Image getImage(String imagePath) {
        return imageCache.get(imagePath);
    }

    public static void addImage(String imagePath, Image image) {
        imageCache.put(imagePath, image);
    }

    public static boolean containsImage(String imagePath) {
        return imageCache.containsKey(imagePath);
    }

    // Optional: Method to clear the cache
    public static void clearCache() {
        imageCache.clear();
    }
}
