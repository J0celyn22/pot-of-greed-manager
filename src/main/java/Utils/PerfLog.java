package Utils;

import org.slf4j.Logger;

/**
 * Elapsed-time logging for diagnosing card-add-to-UI-refresh latency (camera
 * scanner Unit 10 real-world tuning pass).
 * <p>
 * Every stage is logged at INFO under a consistent {@code [PERF]} prefix so the
 * whole add-to-refresh pipeline can be grepped out of the application log and
 * read back in call order. Intended as a temporary diagnostic aid — once the
 * bottleneck is found, the call sites using this class should be removed or
 * dropped to DEBUG.
 */
public final class PerfLog {

    private PerfLog() {
    }

    /**
     * Returns the current timestamp to later pass to {@link #stage}.
     */
    public static long start() {
        return System.nanoTime();
    }

    /**
     * Logs how long a stage took, in milliseconds, at INFO level.
     *
     * @param logger     the caller's own SLF4J logger, so the log line is
     *                   attributed to the class doing the work
     * @param stageLabel a short, greppable description of the stage
     * @param startNanos the value returned by {@link #start()} at the stage's
     *                   beginning
     */
    public static void stage(Logger logger, String stageLabel, long startNanos) {
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        logger.info("[PERF] {} took {} ms", stageLabel, elapsedMillis);
    }
}