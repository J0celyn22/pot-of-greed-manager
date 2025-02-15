// ImageCache.java
package Utils;

import javafx.scene.image.Image;

import java.util.HashMap;
import java.util.Map;

public class ImageCache {

    private static final Map<String, Image> imageCache = new HashMap<>();

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
