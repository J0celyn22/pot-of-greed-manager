package Controller;

/**
 * Test-only helper that resets every static registry on {@link CardGroupRegistry}, plus
 * (optionally) the real Decks &amp; Collections model installed via
 * {@link UserInterfaceFunctions#setDecksList}, to a known, empty state.
 *
 * <p>Like {@code Model.CardsLists.OuicheListTestSupport} (see its Javadoc for the full
 * rationale), these registries are static and shared across the whole JVM for the
 * lifetime of a test run. Every test class that reads or writes any of them should call
 * {@link #resetRegistries()} (or {@link #resetAll()}, if it doesn't already save/restore
 * {@code decksList} itself) from both {@code @BeforeEach} and {@code @AfterEach}.</p>
 */
public final class CardGroupRegistryTestSupport {

    private CardGroupRegistryTestSupport() {
    }

    /**
     * Clears every {@link CardGroupRegistry} registry. Does not touch
     * {@link UserInterfaceFunctions#setDecksList}: use this in a test class that already
     * saves/restores the real Decks &amp; Collections model itself (e.g. via its own
     * {@code originalDecksList} field) to avoid fighting that pattern.
     */
    public static void resetRegistries() {
        CardGroupRegistry.GROUP_OBSERVABLE_LISTS.clear();
        CardGroupRegistry.GROUP_GRID_VIEWS.clear();
        CardGroupRegistry.GROUP_FILTERED_LISTS.clear();
        CardGroupRegistry.DECK_SECTION_GROUPS.clear();
        CardGroupRegistry.COLLECTION_CARDS_GROUPS.clear();
        CardGroupRegistry.COLLECTION_EXCEPTIONS_GROUPS.clear();
        CardGroupRegistry.MISSING_ARTWORK_SETS.clear();
        CardGroupRegistry.LEGACY_GLOBAL_MISSING_SET.clear();
    }

    /**
     * {@link #resetRegistries()} plus resetting {@code decksList} to {@code null}. Use this
     * in a test class that has no save/restore handling of its own for the real Decks &amp;
     * Collections model.
     */
    public static void resetAll() {
        resetRegistries();
        UserInterfaceFunctions.setDecksList(null);
    }
}