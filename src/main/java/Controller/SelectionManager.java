package Controller;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central selection state for the three-pane layout.
 * <p>
 * Tracks which items are currently selected in the MIDDLE pane (element-based,
 * preserving instance identity so two elements wrapping the same Card are
 * treated independently) and in the RIGHT pane (card-based, no duplicate Card
 * objects). Only one pane can be "active" at a time; switching panes clears the
 * other side's selection.
 * </p>
 * <p>
 * All mutation methods notify registered {@link Runnable} listeners synchronously
 * after the state change.
 * </p>
 */
public class SelectionManager {

    // MIDDLE-pane: tracks specific CardElement instances (identity-based),
    // so two different elements wrapping the same Card are treated independently.
    private static final Set<CardElement> selectedMiddleElements = new LinkedHashSet<>();

    // RIGHT-pane: tracks Card objects (no duplicate Card objects in the right pane).
    private static final Set<Card> selectedRightCards = new LinkedHashSet<>();

    private static String activePart = null; // "MIDDLE" or "RIGHT"
    private static final CopyOnWriteArrayList<Runnable> selectionChangeListeners =
            new CopyOnWriteArrayList<>();
    private static Card lastMiddleSelectedCard = null;
    private static CardElement rangeAnchorMiddleElement = null;
    private static Card rangeAnchorRightCard = null;
    /**
     * Last element explicitly clicked in the MIDDLE pane (survives clearSelection).
     */
    private static CardElement lastMiddleElement = null;
    private static Object lastClickedNavigationItem = null;

    private SelectionManager() { /* static utility */ }

    // ── Listeners ─────────────────────────────────────────────────────────────

    /**
     * Registers a listener that is invoked after every selection change.
     * Duplicate registrations are silently ignored.
     *
     * @param listener the callback to register; ignored if {@code null}
     */
    public static void addSelectionChangeListener(Runnable listener) {
        if (listener != null) {
            selectionChangeListeners.addIfAbsent(listener);
        }
    }

    /**
     * Removes a previously registered selection-change listener.
     *
     * @param listener the callback to remove
     */
    public static void removeSelectionChangeListener(Runnable listener) {
        selectionChangeListeners.remove(listener);
    }

    private static void notifySelectionChanged() {
        for (Runnable listener : selectionChangeListeners) {
            try {
                listener.run();
            } catch (Throwable ignored) {
            }
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /**
     * Returns the exact {@link CardElement} instances currently selected in the
     * MIDDLE pane as an unmodifiable set.
     *
     * @return immutable set of selected MIDDLE-pane elements; never {@code null}
     */
    public static Set<CardElement> getSelectedMiddleElements() {
        return Collections.unmodifiableSet(selectedMiddleElements);
    }

    /**
     * Compatibility getter used by the RIGHT pane and legacy code.
     * <p>
     * For the MIDDLE pane ({@code activePart == "MIDDLE"}), derives {@link Card}
     * objects from the selected elements. For the RIGHT pane, returns the directly
     * tracked Card set.
     * </p>
     *
     * @return immutable set of selected cards; never {@code null}
     */
    public static Set<Card> getSelectedCards() {
        if ("MIDDLE".equals(activePart)) {
            Set<Card> cards = new LinkedHashSet<>();
            for (CardElement element : selectedMiddleElements) {
                if (element.getCard() != null) {
                    cards.add(element.getCard());
                }
            }
            return Collections.unmodifiableSet(cards);
        }
        return Collections.unmodifiableSet(selectedRightCards);
    }

    /**
     * Returns the currently active pane identifier.
     *
     * @return {@code "MIDDLE"}, {@code "RIGHT"}, or {@code null} if nothing is selected
     */
    public static String getActivePart() {
        return activePart;
    }

    /**
     * Returns the last card that was selected in the MIDDLE pane before the
     * selection was replaced by a new one.
     *
     * @return the previously active MIDDLE-pane card, or {@code null}
     */
    public static Card getLastMiddleSelectedCard() {
        return lastMiddleSelectedCard;
    }

    /**
     * Returns the last {@link CardElement} that was clicked or selected in the
     * MIDDLE pane. This reference survives a {@link #clearSelection()} call so
     * that context-sensitive actions can still reference the previous element.
     *
     * @return the last interacted MIDDLE-pane element, or {@code null}
     */
    public static CardElement getLastMiddleElement() {
        return lastMiddleElement;
    }

    /**
     * Returns the last navigation item (e.g. a tree node or nav-menu entry) that
     * was clicked by the user.
     *
     * @return the last clicked navigation item, or {@code null}
     */
    public static Object getLastClickedNavigationItem() {
        return lastClickedNavigationItem;
    }

    /**
     * Stores the last navigation item that was clicked by the user. Called by the
     * navigation-menu click handlers to maintain context across pane interactions.
     *
     * @param item the navigation item that was just clicked; may be {@code null}
     */
    public static void setLastClickedNavigationItem(Object item) {
        lastClickedNavigationItem = item;
    }

    // ── MIDDLE-pane operations (element-based) ────────────────────────────────

    /**
     * Performs a plain single-click selection in the MIDDLE pane.
     * <p>
     * Clears any existing selection (both panes), then selects {@code element}
     * exclusively. Passing {@code null} clears the selection without selecting
     * anything new.
     * </p>
     *
     * @param element the element to select, or {@code null} to clear
     */
    public static void selectElement(CardElement element) {
        if ("MIDDLE".equals(activePart) && !selectedMiddleElements.isEmpty()) {
            lastMiddleSelectedCard = getLastCardInElementSet(selectedMiddleElements);
        }
        selectedMiddleElements.clear();
        selectedRightCards.clear();
        if (element != null) {
            selectedMiddleElements.add(element);
            lastMiddleElement = element;
        }
        activePart = element != null ? "MIDDLE" : null;
        rangeAnchorMiddleElement = element;
        notifySelectionChanged();
    }

    /**
     * Performs a Ctrl+click toggle in the MIDDLE pane.
     * <p>
     * If the element is already selected it is deselected; otherwise it is added
     * to the current selection. Switching from the RIGHT pane clears the right
     * selection first.
     * </p>
     *
     * @param element the element to toggle; ignored if {@code null}
     */
    public static void toggleElementSelection(CardElement element) {
        if (element == null) {
            return;
        }
        if (!"MIDDLE".equals(activePart)) {
            if ("RIGHT".equals(activePart)) {
                lastMiddleSelectedCard = getLastCardInElementSet(selectedMiddleElements);
            }
            selectedRightCards.clear();
            selectedMiddleElements.clear();
            activePart = "MIDDLE";
            rangeAnchorMiddleElement = null;
        }
        if (selectedMiddleElements.contains(element)) {
            selectedMiddleElements.remove(element);
            if (selectedMiddleElements.isEmpty()) {
                activePart = null;
                rangeAnchorMiddleElement = null;
            }
        } else {
            selectedMiddleElements.add(element);
            rangeAnchorMiddleElement = element;
            lastMiddleElement = element;
        }
        notifySelectionChanged();
    }

    /**
     * Performs a Shift+click range selection in the MIDDLE pane.
     * <p>
     * Selects all elements between the current range anchor and
     * {@code clickedElement} (inclusive) using the visual order defined by
     * {@code orderedElements}. If no anchor exists, the clicked element becomes
     * the new anchor and only it is selected. The anchor is preserved so that
     * subsequent Shift+clicks extend from the same starting point.
     * </p>
     *
     * @param clickedElement  the element the user shift-clicked; ignored if {@code null}
     * @param orderedElements the complete ordered list of visible elements used to
     *                        compute the range boundaries; if {@code null} or empty,
     *                        falls back to a single-element selection
     */
    public static void rangeSelectElements(CardElement clickedElement,
                                           List<CardElement> orderedElements) {
        if (clickedElement == null) {
            return;
        }
        if (orderedElements == null || orderedElements.isEmpty()) {
            selectElement(clickedElement);
            return;
        }
        if (!"MIDDLE".equals(activePart)) {
            selectedRightCards.clear();
            selectedMiddleElements.clear();
            activePart = "MIDDLE";
            rangeAnchorMiddleElement = null;
        }
        activePart = "MIDDLE";

        CardElement anchor = rangeAnchorMiddleElement;
        if (anchor == null || !orderedElements.contains(anchor)) {
            selectedMiddleElements.clear();
            selectedMiddleElements.add(clickedElement);
            rangeAnchorMiddleElement = clickedElement;
            lastMiddleElement = clickedElement;
            notifySelectionChanged();
            return;
        }

        int anchorIndex = orderedElements.indexOf(anchor);
        int targetIndex = orderedElements.indexOf(clickedElement);
        if (anchorIndex < 0 || targetIndex < 0) {
            selectedMiddleElements.clear();
            selectedMiddleElements.add(clickedElement);
            rangeAnchorMiddleElement = clickedElement;
            lastMiddleElement = clickedElement;
            notifySelectionChanged();
            return;
        }

        selectedMiddleElements.clear();
        int fromIndex = Math.min(anchorIndex, targetIndex);
        int toIndex = Math.max(anchorIndex, targetIndex);
        for (int i = fromIndex; i <= toIndex; i++) {
            selectedMiddleElements.add(orderedElements.get(i));
        }
        lastMiddleElement = clickedElement;
        // Keep rangeAnchorMiddleElement for further SHIFT+clicks from the same anchor
        notifySelectionChanged();
    }

    // ── RIGHT-pane operations (card-based) ────────────────────────────────────

    /**
     * Performs a plain single-click selection in the RIGHT (or another named) pane.
     * <p>
     * Clears any existing selection (both panes), then selects {@code card}
     * exclusively under the given {@code part} identifier.
     * </p>
     *
     * @param card the card to select, or {@code null} to clear
     * @param part the pane identifier, e.g. {@code "RIGHT"}
     */
    public static void selectCard(Card card, String part) {
        if ("RIGHT".equals(part) && "MIDDLE".equals(activePart)) {
            lastMiddleSelectedCard = getLastCardInElementSet(selectedMiddleElements);
        }
        selectedMiddleElements.clear();
        selectedRightCards.clear();
        if (card != null) {
            selectedRightCards.add(card);
        }
        activePart = card != null ? part : null;
        rangeAnchorRightCard = card;
        notifySelectionChanged();
    }

    /**
     * Performs a Ctrl+click toggle in the RIGHT (or another named) pane.
     * <p>
     * If the card is already selected it is deselected; otherwise it is added to
     * the current selection. Switching from a different pane clears that pane's
     * selection first.
     * </p>
     *
     * @param card the card to toggle; ignored if {@code null}
     * @param part the pane identifier, e.g. {@code "RIGHT"}
     */
    public static void toggleSelection(Card card, String part) {
        if (card == null) {
            return;
        }
        if (activePart != null && !activePart.equals(part)) {
            if ("RIGHT".equals(part) && "MIDDLE".equals(activePart)) {
                lastMiddleSelectedCard = getLastCardInElementSet(selectedMiddleElements);
            }
            selectedMiddleElements.clear();
            selectedRightCards.clear();
            activePart = part;
            rangeAnchorRightCard = null;
        } else {
            activePart = part;
        }
        if (selectedRightCards.contains(card)) {
            selectedRightCards.remove(card);
            if (selectedRightCards.isEmpty()) {
                activePart = null;
                rangeAnchorRightCard = null;
            }
        } else {
            selectedRightCards.add(card);
            rangeAnchorRightCard = card;
        }
        notifySelectionChanged();
    }

    /**
     * Performs a Shift+click range selection in the RIGHT (or another named) pane.
     * <p>
     * Selects all cards between the current anchor and {@code clickedCard}
     * (inclusive) using the visual order given by {@code orderedCards}. If no
     * anchor exists, the clicked card becomes the new anchor and only it is
     * selected.
     * </p>
     *
     * @param clickedCard  the card the user shift-clicked; ignored if {@code null}
     * @param part         the pane identifier, e.g. {@code "RIGHT"}
     * @param orderedCards the complete ordered list of visible cards; if {@code null}
     *                     or empty, falls back to a single-card selection
     */
    public static void rangeSelect(Card clickedCard, String part, List<Card> orderedCards) {
        if (clickedCard == null) {
            return;
        }
        if (orderedCards == null || orderedCards.isEmpty()) {
            selectCard(clickedCard, part);
            return;
        }
        if (activePart != null && !activePart.equals(part)) {
            if ("RIGHT".equals(part) && "MIDDLE".equals(activePart)) {
                lastMiddleSelectedCard = getLastCardInElementSet(selectedMiddleElements);
            }
            selectedMiddleElements.clear();
            selectedRightCards.clear();
            activePart = part;
            rangeAnchorRightCard = null;
        }
        activePart = part;

        Card anchor = rangeAnchorRightCard;
        if (anchor == null || !orderedCards.contains(anchor)) {
            selectedRightCards.clear();
            selectedRightCards.add(clickedCard);
            rangeAnchorRightCard = clickedCard;
            notifySelectionChanged();
            return;
        }

        int anchorIndex = orderedCards.indexOf(anchor);
        int targetIndex = orderedCards.indexOf(clickedCard);
        if (anchorIndex < 0 || targetIndex < 0) {
            selectedRightCards.clear();
            selectedRightCards.add(clickedCard);
            rangeAnchorRightCard = clickedCard;
            notifySelectionChanged();
            return;
        }

        selectedRightCards.clear();
        int fromIndex = Math.min(anchorIndex, targetIndex);
        int toIndex = Math.max(anchorIndex, targetIndex);
        for (int i = fromIndex; i <= toIndex; i++) {
            selectedRightCards.add(orderedCards.get(i));
        }
        notifySelectionChanged();
    }

    // ── Common ────────────────────────────────────────────────────────────────

    /**
     * Clears all selections in both panes, resets the active part and range
     * anchors, and notifies listeners.
     * <p>
     * Note: {@link #getLastMiddleElement()} is intentionally <em>not</em> cleared
     * here, so callers can still reference the previously active element after a
     * programmatic clear.
     * </p>
     */
    public static void clearSelection() {
        selectedMiddleElements.clear();
        selectedRightCards.clear();
        activePart = null;
        rangeAnchorMiddleElement = null;
        rangeAnchorRightCard = null;
        notifySelectionChanged();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static Card getLastCardInElementSet(Set<CardElement> elements) {
        CardElement last = null;
        for (CardElement element : elements) {
            last = element;
        }
        return last != null ? last.getCard() : null;
    }
}