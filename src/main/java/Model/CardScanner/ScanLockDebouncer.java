package Model.CardScanner;

import java.util.function.LongSupplier;

/**
 * Implements the camera scanner's lock/release debounce, exactly as specified in Unit 1 of the
 * camera card-scanner plan doc: once something is confidently detected, the scanner stays
 * "locked" — no further add-eligible detections — for as long as something keeps being
 * confidently detected on every following cycle, even through a brief miss or flicker. The lock
 * only releases once a continuous run of misses lasts at least {@link #releaseDelayMillis}. The
 * very next confident detection after release is add-eligible again, whether it's the same
 * physical card shown a second time or a different one.
 *
 * <p>Deliberately holds no notion of "the current card" or any card-matching logic — it only
 * tracks whether the boolean "something confidently detected" signal is currently locked or
 * released. {@code Controller.CardScannerCoordinator} is the caller that decides what counts as
 * a confident detection (a successful {@link Utils.CardTextMatcher} resolution, not raw OCR
 * confidence alone) and what to do with an add-eligible signal.
 *
 * <p>Package-private constructor overload takes an injectable clock so the 500ms-class timing in
 * this class's tests doesn't depend on real wall-clock delays.
 */
public class ScanLockDebouncer {

    private final long releaseDelayMillis;
    private final LongSupplier clockMillis;

    private boolean locked = false;
    private long lastConfidentDetectionMillis = -1;

    /**
     * @param releaseDelayMillis how long a continuous run of "nothing confidently detected"
     *                           cycles must last before the lock releases. Unit 1's plan doc
     *                           starting value is 500ms; passed in here rather than hardcoded so
     *                           it's tunable (e.g. from Unit 7) without editing this class.
     */
    public ScanLockDebouncer(long releaseDelayMillis) {
        this(releaseDelayMillis, System::currentTimeMillis);
    }

    ScanLockDebouncer(long releaseDelayMillis, LongSupplier clockMillis) {
        this.releaseDelayMillis = releaseDelayMillis;
        this.clockMillis = clockMillis;
    }

    /**
     * Reports a confident detection for the current cycle.
     *
     * @return {@code true} exactly when this call is the one that transitions the debouncer from
     * released to locked — i.e. the caller should treat this specific detection as add-eligible.
     * {@code false} means the lock was already held (something was already being confidently
     * detected before this call), so this detection is a continuation, not a new add.
     */
    public boolean onConfidentDetection() {
        lastConfidentDetectionMillis = clockMillis.getAsLong();
        if (!locked) {
            locked = true;
            return true;
        }
        return false;
    }

    /**
     * Reports that the current cycle found nothing confidently detected. Releases the lock once
     * {@link #releaseDelayMillis} has passed since the last confident detection; otherwise a
     * no-op, so a single missed cycle mid-view doesn't release the lock on its own.
     */
    public void onNoConfidentDetection() {
        if (locked && lastConfidentDetectionMillis >= 0
                && clockMillis.getAsLong() - lastConfidentDetectionMillis >= releaseDelayMillis) {
            locked = false;
        }
    }

    /**
     * @return {@code true} if the debouncer is currently locked (something has been confidently
     * detected recently enough that a new detection would not be add-eligible).
     */
    public boolean isLocked() {
        return locked;
    }
}