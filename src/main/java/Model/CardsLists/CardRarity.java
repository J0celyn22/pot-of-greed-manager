package Model.CardsLists;

/**
 * All known Yu-Gi-Oh! card rarities.
 * <p>
 * Each constant stores:
 * <ul>
 *   <li>a human-readable {@code displayName} (matching the {@code set_rarity}
 *       field in the YGOProdeck {@code cardinfo.json} database),</li>
 *   <li>a short {@code code} (matching the content of the
 *       {@code set_rarity_code} field, without surrounding parentheses).</li>
 * </ul>
 * </p>
 *
 * <p>
 * The list of available rarities for a specific card (i.e. the rarities in
 * which that card has been printed) is stored on the {@link Card} object as
 * {@code List<CardRarity> availableRarities}.  A {@link CardElement} owned by
 * the user may carry any {@code CardRarity}, including ones outside that list
 * (the list is only used as a priority / suggestion source in the UI).
 * </p>
 */
public enum CardRarity {

    // ── Standard rarities ──────────────────────────────────────────────────
    COMMON("Common", "C"),
    SHORT_PRINT("Short Print", "SP"),
    SUPER_SHORT_PRINT("Super Short Print", "SSP"),
    RARE("Rare", "R"),
    SUPER_RARE("Super Rare", "SR"),
    ULTRA_RARE("Ultra Rare", "UR"),
    SECRET_RARE("Secret Rare", "ScR"),
    ULTIMATE_RARE("Ultimate Rare", "UtR"),
    GHOST_RARE("Ghost Rare", "GR"),
    HOLOGRAPHIC_RARE("Holographic Rare", "HR"),
    STARLIGHT_RARE("Starlight Rare", "StR"),

    // ── Collector / Anniversary rarities ───────────────────────────────────
    COLLECTORS_RARE("Collector's Rare", "CR"),
    PRISMATIC_SECRET_RARE("Prismatic Secret Rare", "PScR"),
    QUARTER_CENTURY_SECRET_RARE("Quarter Century Secret Rare", "QCSR"),
    QUARTER_CENTURY_ULTRA_RARE("Quarter Century Ultra Rare", "QCUR"),
    EXTRA_SECRET_RARE("Extra Secret Rare", "EScR"),
    TWENTY_FIFTH_SECRET_RARE("25th Anniversary Secret Rare", "25ScR"),
    PHARAOHS_RARE("Pharaoh's Rare", "PhR"),

    // ── Gold / Platinum rarities ───────────────────────────────────────────
    PLATINUM_RARE("Platinum Rare", "PlR"),
    PLATINUM_SECRET_RARE("Platinum Secret Rare", "PlScR"),
    GOLD_RARE("Gold Rare", "GoldR"),
    GOLD_SECRET_RARE("Gold Secret Rare", "GoldScR"),
    PREMIUM_GOLD_RARE("Premium Gold Rare", "PGR"),

    // ── Parallel rarities ──────────────────────────────────────────────────
    PARALLEL_RARE("Parallel Rare", "PR"),
    NORMAL_PARALLEL_RARE("Normal Parallel Rare", "NPR"),
    SUPER_PARALLEL_RARE("Super Parallel Rare", "SPR"),
    ULTRA_PARALLEL_RARE("Ultra Parallel Rare", "UPR"),
    SECRET_PARALLEL_RARE("Secret Parallel Rare", "ScPR"),
    PRISMATIC_ULTIMATE_RARE("Prismatic Ultimate Rare", "PUtR"),

    // ── Foil / Special-print rarities ─────────────────────────────────────
    MOSAIC_RARE("Mosaic Rare", "MR"),
    SHATTERFOIL_RARE("Shatterfoil Rare", "ShR"),
    STARFOIL_RARE("Starfoil Rare", "SFR"),
    RAINBOW_FOIL("Rainbow Foil", "RFoil"),
    GOLD_FOIL("Gold Foil", "GFoil"),

    // ── Duel Terminal parallel rarities ───────────────────────────────────
    DUEL_TERMINAL_NORMAL_PARALLEL_RARE("Duel Terminal Normal Parallel Rare", "DNPR"),
    DUEL_TERMINAL_RARE_PARALLEL_RARE("Duel Terminal Rare Parallel Rare", "DRPR"),
    DUEL_TERMINAL_SUPER_PARALLEL_RARE("Duel Terminal Super Parallel Rare", "DSPR"),
    DUEL_TERMINAL_ULTRA_PARALLEL_RARE("Duel Terminal Ultra Parallel Rare", "DUPR"),
    DUEL_TERMINAL_SECRET_PARALLEL_RARE("Duel Terminal Secret Parallel Rare", "DScPR");

    // -----------------------------------------------------------------------

    private final String displayName;
    private final String code;

    CardRarity(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    // -----------------------------------------------------------------------
    // Accessors

    /**
     * Looks up a {@code CardRarity} by its serialisation code.
     * <p>
     * Parentheses are stripped automatically, so both {@code "StR"} and
     * {@code "(StR)"} are accepted.  The comparison is case-insensitive.
     * </p>
     *
     * @param code the code to look up
     * @return the matching {@code CardRarity}, or {@code null} if none matches
     */
    public static CardRarity fromCode(String code) {
        if (code == null || code.isEmpty()) return null;
        // Strip surrounding parentheses that appear in the raw database field.
        String cleaned = code.trim().replaceAll("^\\(|\\)$", "");
        for (CardRarity r : values()) {
            if (r.code.equalsIgnoreCase(cleaned)) return r;
        }
        return null;
    }

    /**
     * Looks up a {@code CardRarity} by its display name.
     * The comparison is case-insensitive.
     *
     * @param name the display name to look up (e.g. {@code "Starlight Rare"})
     * @return the matching {@code CardRarity}, or {@code null} if none matches
     */
    public static CardRarity fromDisplayName(String name) {
        if (name == null || name.isEmpty()) return null;
        String trimmed = name.trim();
        for (CardRarity r : values()) {
            if (r.displayName.equalsIgnoreCase(trimmed)) return r;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Factory helpers

    /**
     * Returns the human-readable name matching the {@code set_rarity} field
     * in the database (e.g. {@code "Starlight Rare"}).
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the short code matching the {@code set_rarity_code} field in
     * the database, without surrounding parentheses (e.g. {@code "StR"}).
     */
    public String getCode() {
        return code;
    }

    // -----------------------------------------------------------------------

    /**
     * Returns the display name (e.g. {@code "Starlight Rare"}).
     */
    @Override
    public String toString() {
        return displayName;
    }
}
