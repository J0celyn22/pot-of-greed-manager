package Controller;

import Model.CardsLists.CardElement;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.WeakHashMap;

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
     * Returns how many elements currently have a registered property.
     * <p>
     * Exposed only for regression/leak checks (Step 5 of the reactive-selection plan) — not
     * used by any production code path.
     * </p>
     *
     * @return the current number of tracked entries
     */
    static synchronized int trackedElementCount() {
        return selectedProperties.size();
    }
}