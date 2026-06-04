package Model.CardsLists;

/**
 * Describes how a wanted-card slot in the OuicheList is satisfied by the
 * user's owned collection.
 *
 * <p>The three values form a natural progression:
 * <ol>
 *   <li>{@link #MISSING} — the user does not own any copy of this card that
 *       could fill this slot.</li>
 *   <li>{@link #OWNED_SUBSTANDARD} — the user owns a copy of this card, but
 *       it does not satisfy the condition or rarity requirement attached to
 *       this slot (e.g. the slot asks for Near Mint and the owned copy is
 *       Played).</li>
 *   <li>{@link #OWNED} — the user owns a copy that fully satisfies this slot
 *       (either no condition/rarity requirement was set, or the owned copy
 *       meets it).</li>
 * </ol>
 *
 * <p>This enum is a transient / computed value: it is set during OuicheList
 * generation and is never persisted to file.
 */
public enum OwnershipStatus {

    /**
     * No owned copy is available for this slot.
     */
    MISSING,

    /**
     * An owned copy exists but does not meet the condition or rarity
     * requirement of this slot.
     */
    OWNED_SUBSTANDARD,

    /**
     * An owned copy exists and fully satisfies this slot's requirements.
     */
    OWNED
}