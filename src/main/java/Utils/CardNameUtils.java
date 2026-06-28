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

    /**
     * Re-applies a decorator prefix and suffix to a new display name, preserving
     * the same leading and trailing decoration characters that were present in the
     * original raw stored name.
     *
     * <p>Example: {@code rebuildDecoratedName("==Blue-Eyes==", "Red-Eyes", '=')}
     * returns {@code "==Red-Eyes=="}.</p>
     *
     * @param raw            the original raw name as stored (may be {@code null} or empty)
     * @param newDisplayName the new display name to embed between the decorators
     * @param decorator      the decoration character ({@code '='} for boxes,
     *                       {@code '-'} for categories)
     * @return the reconstructed raw name with decorators preserved, or
     * {@code newDisplayName} if {@code raw} has no leading/trailing decorators
     */
    public static String rebuildDecoratedName(
            String raw, String newDisplayName, char decorator) {
        if (raw == null || raw.isEmpty()) {
            return newDisplayName;
        }
        int leading = 0;
        while (leading < raw.length() && raw.charAt(leading) == decorator) {
            leading++;
        }
        int trailing = 0;
        while (trailing < raw.length()
                && raw.charAt(raw.length() - 1 - trailing) == decorator) {
            trailing++;
        }
        if (leading == 0 && trailing == 0) {
            return newDisplayName;
        }
        String prefix = raw.substring(0, leading);
        String suffix = raw.substring(raw.length() - trailing);
        return prefix + newDisplayName + suffix;
    }
}