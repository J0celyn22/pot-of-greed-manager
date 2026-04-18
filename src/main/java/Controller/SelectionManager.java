package Controller;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

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
    // Last element explicitly clicked in the MIDDLE pane (survives clearSelection)
    private static CardElement lastMiddleElement = null;
    private static Object lastClickedNavigationItem = null;

    // ── Listeners ─────────────────────────────────────────────────────────────

    public static void addSelectionChangeListener(Runnable listener) {
        if (listener != null) selectionChangeListeners.addIfAbsent(listener);
    }

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
     * MIDDLE-pane: the exact CardElement instances that are selected.
     */
    public static Set<CardElement> getSelectedMiddleElements() {
        return Collections.unmodifiableSet(selectedMiddleElements);
    }

    /**
     * Compatibility getter used by the RIGHT pane and legacy code.
     * For MIDDLE pane, derives Card objects from the selected elements.
     * For RIGHT pane, returns the directly tracked Card set.
     */
    public static Set<Card> getSelectedCards() {
        if ("MIDDLE".equals(activePart)) {
            Set<Card> cards = new LinkedHashSet<>();
            for (CardElement element : selectedMiddleElements) {
                if (element.getCard() != null) cards.add(element.getCard());
            }
            return Collections.unmodifiableSet(cards);
        }
        return Collections.unmodifiableSet(selectedRightCards);
    }

    public static String getActivePart() {
        return activePart;
    }

    public static Card getLastMiddleSelectedCard() {
        return lastMiddleSelectedCard;
    }

    /**
     * The last CardElement clicked/selected in the MIDDLE pane. Survives clearSelection().
     */
    public static CardElement getLastMiddleElement() {
        return lastMiddleElement;
    }

    /**
     * @deprecated Use {@link #getLastMiddleElement()}
     */
    @Deprecated
    public static Card getLastMiddleCard() {
        return lastMiddleElement != null ? lastMiddleElement.getCard() : null;
    }

    public static Object getLastClickedNavigationItem() {
        return lastClickedNavigationItem;
    }

    public static void setLastClickedNavigationItem(Object item) {
        lastClickedNavigationItem = item;
    }

    // ── MIDDLE-pane operations (element-based) ────────────────────────────────

    /**
     * Plain single click in the MIDDLE pane.
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
     * CTRL+click in the MIDDLE pane.
     */
    public static void toggleElementSelection(CardElement element) {
        if (element == null) return;
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
     * SHIFT+click in the MIDDLE pane.
     */
    public static void rangeSelectElements(CardElement clickedElement,
                                           List<CardElement> orderedElements) {
        if (clickedElement == null) return;
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

    // ── RIGHT-pane operations (card-based, unchanged API) ─────────────────────

    /** Plain single click in the RIGHT pane. */
    public static void selectCard(Card card, String part) {
        if ("RIGHT".equals(part) && "MIDDLE".equals(activePart)) {
            lastMiddleSelectedCard = getLastCardInElementSet(selectedMiddleElements);
        }
        selectedMiddleElements.clear();
        selectedRightCards.clear();
        if (card != null) selectedRightCards.add(card);
        activePart = card != null ? part : null;
        rangeAnchorRightCard = card;
        notifySelectionChanged();
    }

    /**
     * CTRL+click in the RIGHT pane.
     */
    public static void toggleSelection(Card card, String part) {
        if (card == null) return;
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
     * SHIFT+click in the RIGHT pane.
     */
    public static void rangeSelect(Card clickedCard, String part, List<Card> orderedCards) {
        if (clickedCard == null) return;
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

    public static void clearSelection() {
        selectedMiddleElements.clear();
        selectedRightCards.clear();
        activePart = null;
        rangeAnchorMiddleElement = null;
        rangeAnchorRightCard = null;
        notifySelectionChanged();
    }

    // ── Legacy compatibility ──────────────────────────────────────────────────

    /** @deprecated Use {@link #toggleElementSelection(CardElement)} for the MIDDLE pane. */
    @Deprecated
    public static void addToSelection(Card card, String part) {
        toggleSelection(card, part);
    }

    public static void removeFromSelection(Card card) {
        selectedRightCards.remove(card);
        if (selectedRightCards.isEmpty() && "RIGHT".equals(activePart)) activePart = null;
        notifySelectionChanged();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static Card getLastCardInElementSet(Set<CardElement> elements) {
        CardElement last = null;
        for (CardElement element : elements) last = element;
        return last != null ? last.getCard() : null;
    }
}