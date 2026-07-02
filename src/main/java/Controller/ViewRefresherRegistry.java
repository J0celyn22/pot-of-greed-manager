package Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A small named registry of {@link Runnable} "refresher" callbacks for a single
 * view, used to decouple a model-change notification (e.g. "the OuicheList
 * changed") from the concrete UI code that redraws that view.
 * <p>
 * Extracted from {@link UserInterfaceFunctions}, which previously kept several
 * near-identical copies of this register/unregister/run-all pattern — one per
 * view (Owned Collection, Decks &amp; Collections, OuicheList, Archetypes, and
 * the Decks tree used by Archetypes).
 * </p>
 */
final class ViewRefresherRegistry {

    private final String name;
    private final Logger logger = LoggerFactory.getLogger(ViewRefresherRegistry.class);
    private final CopyOnWriteArrayList<Runnable> refreshers = new CopyOnWriteArrayList<>();

    /**
     * @param name a short label identifying which view this registry belongs to,
     *             used only to make debug log lines distinguishable
     */
    ViewRefresherRegistry(String name) {
        this.name = name;
    }

    /**
     * Registers a callback to be invoked by {@link #runAll()}. Duplicate
     * registrations (by reference) are ignored.
     *
     * @param refresher the callback to register (ignored if {@code null})
     */
    void register(Runnable refresher) {
        if (refresher != null) {
            refreshers.addIfAbsent(refresher);
        }
    }

    /**
     * Unregisters a previously registered callback.
     *
     * @param refresher the callback to unregister (ignored if {@code null})
     */
    void unregister(Runnable refresher) {
        if (refresher != null) {
            refreshers.remove(refresher);
        }
    }

    /**
     * Returns {@code true} if no callback is currently registered.
     */
    boolean isEmpty() {
        return refreshers.isEmpty();
    }

    /**
     * Runs every registered callback in registration order. If a callback throws,
     * the exception is logged at debug level and the remaining callbacks still run,
     * so one broken refresher cannot prevent the others from updating their view.
     */
    void runAll() {
        for (Runnable refresher : refreshers) {
            try {
                refresher.run();
            } catch (Throwable throwable) {
                logger.debug("{}: a refresher threw", name, throwable);
            }
        }
    }
}