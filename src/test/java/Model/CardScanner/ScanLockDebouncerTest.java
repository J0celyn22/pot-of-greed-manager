package Model.CardScanner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ScanLockDebouncer}, using the package-private fake-clock constructor for
 * deterministic timing rather than sleeping real milliseconds.
 */
class ScanLockDebouncerTest {

    private static final long RELEASE_DELAY_MILLIS = 500;

    @Test
    void onConfidentDetection_firstCall_returnsTrueAndLocks() {
        FakeClock clock = new FakeClock();
        ScanLockDebouncer debouncer = new ScanLockDebouncer(RELEASE_DELAY_MILLIS, clock::getAsLong);

        boolean addEligible = debouncer.onConfidentDetection();

        assertTrue(addEligible, "the first confident detection should be add-eligible");
        assertTrue(debouncer.isLocked());
    }

    @Test
    void onConfidentDetection_whileAlreadyLocked_returnsFalse() {
        FakeClock clock = new FakeClock();
        ScanLockDebouncer debouncer = new ScanLockDebouncer(RELEASE_DELAY_MILLIS, clock::getAsLong);

        debouncer.onConfidentDetection();
        clock.advanceBy(50);
        boolean secondAddEligible = debouncer.onConfidentDetection();

        assertFalse(secondAddEligible, "a repeated detection while still locked must not re-add");
        assertTrue(debouncer.isLocked());
    }

    @Test
    void onNoConfidentDetection_briefMiss_doesNotRelease() {
        FakeClock clock = new FakeClock();
        ScanLockDebouncer debouncer = new ScanLockDebouncer(RELEASE_DELAY_MILLIS, clock::getAsLong);

        debouncer.onConfidentDetection();
        clock.advanceBy(100);
        debouncer.onNoConfidentDetection();

        assertTrue(debouncer.isLocked(), "a miss well under the release delay must not release the lock");
    }

    @Test
    void onNoConfidentDetection_intermittentMisses_doNotAccumulateTowardRelease() {
        FakeClock clock = new FakeClock();
        ScanLockDebouncer debouncer = new ScanLockDebouncer(RELEASE_DELAY_MILLIS, clock::getAsLong);

        debouncer.onConfidentDetection();
        clock.advanceBy(400);
        debouncer.onNoConfidentDetection();
        clock.advanceBy(400);
        debouncer.onConfidentDetection();
        clock.advanceBy(400);
        debouncer.onNoConfidentDetection();

        assertTrue(debouncer.isLocked(),
                "each miss is measured from the last confident detection, not accumulated across misses");
    }

    @Test
    void onNoConfidentDetection_continuousMissForFullDelay_releases() {
        FakeClock clock = new FakeClock();
        ScanLockDebouncer debouncer = new ScanLockDebouncer(RELEASE_DELAY_MILLIS, clock::getAsLong);

        debouncer.onConfidentDetection();
        clock.advanceBy(RELEASE_DELAY_MILLIS);
        debouncer.onNoConfidentDetection();

        assertFalse(debouncer.isLocked(), "a continuous miss lasting the full release delay should release");
    }

    @Test
    void onConfidentDetection_afterRelease_isAddEligibleAgain() {
        FakeClock clock = new FakeClock();
        ScanLockDebouncer debouncer = new ScanLockDebouncer(RELEASE_DELAY_MILLIS, clock::getAsLong);

        debouncer.onConfidentDetection();
        clock.advanceBy(RELEASE_DELAY_MILLIS);
        debouncer.onNoConfidentDetection();
        boolean addEligibleAfterRelease = debouncer.onConfidentDetection();

        assertTrue(addEligibleAfterRelease,
                "the next confident detection after a release should be add-eligible again");
    }

    @Test
    void onNoConfidentDetection_whileAlreadyReleased_isNoOp() {
        FakeClock clock = new FakeClock();
        ScanLockDebouncer debouncer = new ScanLockDebouncer(RELEASE_DELAY_MILLIS, clock::getAsLong);

        debouncer.onNoConfidentDetection();

        assertFalse(debouncer.isLocked());
    }

    @Test
    void isLocked_initialState_isFalse() {
        ScanLockDebouncer debouncer = new ScanLockDebouncer(RELEASE_DELAY_MILLIS);

        assertFalse(debouncer.isLocked());
    }

    /**
     * Mutable fake clock, advanced explicitly by each test as needed.
     */
    private static final class FakeClock {
        private long nowMillis = 0;

        long getAsLong() {
            return nowMillis;
        }

        void advanceBy(long millis) {
            nowMillis += millis;
        }
    }
}