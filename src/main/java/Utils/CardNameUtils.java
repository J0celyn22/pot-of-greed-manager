package Utils;

/**
 * Utility methods for cleaning and normalising card-related display names.
 *
 * <p>The data format uses leading and trailing decoration characters to mark
 * structural elements: {@code =} for boxes and {@code -} for categories.
 * These decorators must be stripped before displaying names in the UI.
 */
public final class CardNameUtils {

    private CardNameUtils() {
    }

    /**
     * Strips leading and trailing decoration characters ({@code =} for boxes,
     * {@code -} for categories) from a raw stored name, preserving any hyphens
     * that are genuinely part of the name itself.
     *
     * @param rawName the raw name as stored in the model (may be {@code null})
     * @return the clean display name, never {@code null}
     */
    public static String sanitize(String rawName) {
        if (rawName == null) {
            return "";
        }
        String stripped = rawName.trim();
        int start = 0;
        while (start < stripped.length()
                && (stripped.charAt(start) == '=' || stripped.charAt(start) == '-')) {
            start++;
        }
        int end = stripped.length();
        while (end > start
                && (stripped.charAt(end - 1) == '=' || stripped.charAt(end - 1) == '-')) {
            end--;
        }
        return stripped.substring(start, end).trim();
    }
}