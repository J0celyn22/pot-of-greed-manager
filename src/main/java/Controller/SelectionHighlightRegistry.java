package Controller;

import Model.CardsLists.CardElement;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;

import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Push-style registry of "is this element currently selected in the MIDDLE pane" state,
 * keyed by {@link CardElement} instance identity (the same identity semantics
 * {@link SelectionManager} already uses for the MIDDLE pane).
 * <p>
 * {@link SelectionManager}'s mutation methods flip the relevant properties here whenever the
 * underlying selection changes, so a future observer (a rendered grid cell) can react to only
 * the specific element it displays, instead of being told "something changed, re-render
 * everything" the way the existing broadcast listener does.
 * </p>
 * <p>
 * Backed by a {@link WeakHashMap} so a {@link CardElement} that is removed from every
 * collection (and therefore no longer referenced anywhere else) does not keep its property —
 * and this registry's entry for it — alive forever.
 * </p>
 */
public final class SelectionHighlightRegistry {

    private static final WeakHashMap<CardElement, BooleanProperty> selectedProperties = new WeakHashMap<>();

    /**
     * TEMPORARY diagnostic counter (camera-scanner memory-leak investigation): net count of
     * listener registrations made through {@link #subscribe} minus {@link #unsubscribe} calls,
     * i.e. how many listeners are actually attached across every property in {@link
     * #selectedProperties} right now.
     * <p>
     * This is a different signal than {@link #trackedElementCount()}: that count only reflects
     * how many distinct {@link CardElement}s have ever asked for a property, and stays flat once
     * a card's property already exists. This one rises whenever a listener is added without a
     * matching removal — which is what a rendered grid cell does if it is discarded (e.g. by a
     * forced full {@code GridView} rebuild) without ever having {@code updateItem(null, true)}
     * called on it to unsubscribe first. A count that keeps climbing well past the number of
     * cards actually being displayed at once points at exactly that.
     * </p>
     * Remove this field and {@link #activeSubscriptionCount()} once the leak this was added to
     * chase is confirmed fixed.
     */
    private static final AtomicInteger activeSubscriptionCount = new AtomicInteger(0);

    private SelectionHighlightRegistry() { /* static utility */ }

    /**
     * Returns the observable "is {@code element} currently selected" property for
     * {@code element}, creating it on first use.
     * <p>
     * A freshly created property is initialised from {@link SelectionManager}'s current
     * MIDDLE-pane selection rather than defaulting to {@code false}, so an element that is
     * already selected before anything first asks for its property (e.g. a grid cell recycled
     * onto an already-selected element) reports the correct state immediately.
     * </p>
     *
     * @param element the element to look up or create a property for; must not be {@code null}
     * @return the shared {@link BooleanProperty} tracking {@code element}'s selection state
     */
    public static synchronized BooleanProperty getOrCreateSelectedProperty(CardElement element) {
        return selectedProperties.computeIfAbsent(element,
                newElement -> new SimpleBooleanProperty(SelectionManager.getSelectedMiddleElements().contains(newElement)));
    }

    /**
     * Registers {@code listener} on {@code element}'s selected property (creating the property
     * via {@link #getOrCreateSelectedProperty} if needed), and counts the registration toward
     * {@link #activeSubscriptionCount()}.
     * <p>
     * Callers must pass the exact same {@code listener} instance to {@link #unsubscribe} to
     * remove it again — the same requirement {@link BooleanProperty#removeListener} itself has.
     * Prefer this (and {@link #unsubscribe}) over calling {@code addListener}/{@code
     * removeListener} directly on a property returned by {@link #getOrCreateSelectedProperty},
     * so every subscription this registry hands out is covered by the leak diagnostic.
     * </p>
     *
     * @param element  the element whose selected property to subscribe to
     * @param listener the listener to register
     */
    public static void subscribe(CardElement element, ChangeListener<Boolean> listener) {
        getOrCreateSelectedProperty(element).addListener(listener);
        activeSubscriptionCount.incrementAndGet();
    }

    /**
     * Removes a listener previously registered via {@link #subscribe}, and updates {@link
     * #activeSubscriptionCount()} accordingly.
     *
     * @param element  the element the listener was subscribed to
     * @param listener the exact listener instance passed to {@link #subscribe}
     */
    public static void unsubscribe(CardElement element, ChangeListener<Boolean> listener) {
        getOrCreateSelectedProperty(element).removeListener(listener);
        activeSubscriptionCount.decrementAndGet();
    }

    /**
     * Returns how many elements currently have a registered property.
     * <p>
     * Originally exposed only for regression/leak checks (Step 5 of the reactive-selection
     * plan); now also read by {@link CardScannerCoordinator}'s periodic memory-diagnostics log.
     * </p>
     *
     * @return the current number of tracked entries
     */
    public static synchronized int trackedElementCount() {
        return selectedProperties.size();
    }

    /**
     * @return the current net count of active listener registrations made through {@link
     * #subscribe} (temporary diagnostic — see {@link #activeSubscriptionCount}'s own javadoc for
     * why this is a more direct leak signal than {@link #trackedElementCount()}). Also read by
     * {@link CardScannerCoordinator}'s periodic memory-diagnostics log.
     */
    public static int activeSubscriptionCount() {
        return activeSubscriptionCount.get();
    }
}