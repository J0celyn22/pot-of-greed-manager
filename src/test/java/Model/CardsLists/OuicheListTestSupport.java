package Model.CardsLists;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Test-only helper that resets every static field on {@link OuicheList} to a known,
 * empty state.
 *
 * <p>{@link OuicheList} — and, by extension, {@link OuicheListUpdater} and
 * {@link OuicheListComputer} — hold all of their state in static fields, shared across
 * the whole JVM for the lifetime of a test run. JUnit does not guarantee any particular
 * order between test classes (or even between test methods within Gradle/Maven's
 * parallel runners), so a test class whose {@code @BeforeEach} only resets the handful
 * of fields <em>it</em> happens to use can silently inherit stale state left behind by a
 * completely unrelated test that ran first — or leak its own state into whatever runs
 * next. This was the exact cause of the OuicheListDeckGroupPropagationTest failures that
 * only showed up when the full suite ran and never in isolation.</p>
 *
 * <p>Every test class that reads or writes any {@link OuicheList} static field must call
 * {@link #resetAll()} from both {@code @BeforeEach} (so the test starts from a clean,
 * known slate regardless of what ran before it) and {@code @AfterEach} (so it can never
 * leak its own state into whatever runs next).</p>
 */
public final class OuicheListTestSupport {

    private OuicheListTestSupport() {
    }

    /**
     * Resets every {@link OuicheList} static field. Collections are reset to fresh,
     * empty instances rather than {@code null} where downstream code does not
     * null-check them; {@link OuicheList#getDetailedOuicheList()},
     * {@link OuicheList#getDecksList()}, and {@link OuicheList#getMyCardsCollection()}
     * are reset to {@code null} since callers are expected to install their own before
     * use.
     */
    public static void resetAll() {
        OuicheList.setDetailedOuicheList(null);
        OuicheList.setDecksList(null);
        OuicheList.setMyCardsCollection(null);
        OuicheList.setUnusedCards(new ArrayList<>());
        OuicheList.setListsIntersection(new ArrayList<>());
        OuicheList.setThirdPartyList(new ArrayList<>());
        OuicheList.setThirdPartyCardsINeedList(new ArrayList<>());
        OuicheList.setMaOuicheList(new LinkedHashMap<>());
        OuicheList.setMaOuicheListCounts(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandard(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandardCounts(new LinkedHashMap<>());
    }
}