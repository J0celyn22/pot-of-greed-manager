package Model.CardsLists;

/**
 * Physical condition of a card copy owned by the user.
 * <p>
 * Each value carries a human-readable display name and a short serialisation
 * code that is written to / read from the collection file.
 * </p>
 * <p>
 * Standard grading scale used by most TCG marketplaces:
 * <pre>
 *   M   – Mint
 *   NM  – Near Mint
 *   EX  – Excellent
 *   GD  – Good
 *   PL  – Played
 *   PO  – Poor
 *   DM  – Damaged
 * </pre>
 */
public enum CardCondition {

    MINT("Mint", "M"),
    NEAR_MINT("Near Mint", "NM"),
    EXCELLENT("Excellent", "EX"),
    GOOD("Good", "GD"),
    PLAYED("Played", "PL"),
    POOR("Poor", "PO"),
    DAMAGED("Damaged", "DM");

    // -----------------------------------------------------------------------

    private final String displayName;
    private final String code;

    CardCondition(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    // -----------------------------------------------------------------------
    // Accessors

    /**
     * Looks up a {@code CardCondition} by its serialisation code.
     * The comparison is case-insensitive.
     *
     * @param code the code to look up (e.g. {@code "NM"}, {@code "nm"})
     * @return the matching {@code CardCondition}, or {@code null} if none
     * matches
     */
    public static CardCondition fromCode(String code) {
        if (code == null || code.isEmpty()) return null;
        for (CardCondition c : values()) {
            if (c.code.equalsIgnoreCase(code.trim())) return c;
        }
        return null;
    }

    /**
     * Looks up a {@code CardCondition} by its display name.
     * The comparison is case-insensitive.
     *
     * @param name the display name to look up (e.g. {@code "Near Mint"})
     * @return the matching {@code CardCondition}, or {@code null} if none
     * matches
     */
    public static CardCondition fromDisplayName(String name) {
        if (name == null || name.isEmpty()) return null;
        for (CardCondition c : values()) {
            if (c.displayName.equalsIgnoreCase(name.trim())) return c;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Factory

    /**
     * Returns the human-readable name shown in the UI (e.g. "Near Mint").
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the short serialisation code written to the collection file
     * (e.g. {@code "NM"}).
     */
    public String getCode() {
        return code;
    }

    // -----------------------------------------------------------------------

    /**
     * Returns the display name (e.g. {@code "Near Mint"}).
     */
    @Override
    public String toString() {
        return displayName;
    }
}
