package Controller;

import Model.CardsLists.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Static clipboard holding the most recently copied selection of {@link Card} objects.
 * <p>
 * Only {@link Card} references are stored — not {@link Model.CardsLists.CardElement}
 * instances — so print-code information is not preserved across a copy/paste cycle.
 * Registered change listeners are notified synchronously whenever the clipboard
 * contents change.
 * </p>
 */
public class CardClipboard {

    private static final List<Card> contents = new ArrayList<>();
    private static final CopyOnWriteArrayList<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private CardClipboard() { /* static utility */ }

    /**
     * Replaces the clipboard contents with the given list of cards.
     * {@code null} entries inside the list are silently skipped.
     *
     * @param cards the cards to copy onto the clipboard; may be {@code null},
     *              which is treated as an empty list
     */
    public static void copyCards(List<Card> cards) {
        contents.clear();
        if (cards != null) {
            for (Card card : cards) {
                if (card != null) {
                    contents.add(card);
                }
            }
        }
        notifyListeners();
    }

    /**
     * Returns an unmodifiable view of the current clipboard contents.
     *
     * @return immutable list of cards currently on the clipboard; never {@code null}
     */
    public static List<Card> getContents() {
        return Collections.unmodifiableList(contents);
    }

    /**
     * Returns {@code true} if the clipboard currently holds no cards.
     *
     * @return {@code true} when the clipboard is empty
     */
    public static boolean isEmpty() {
        return contents.isEmpty();
    }

    /**
     * Removes all cards from the clipboard and notifies registered listeners.
     */
    public static void clear() {
        contents.clear();
        notifyListeners();
    }

    /**
     * Registers a listener that is invoked whenever the clipboard contents change
     * (on copy or clear). Duplicate registrations are silently ignored.
     *
     * @param listener the callback to register; ignored if {@code null}
     */
    public static void addChangeListener(Runnable listener) {
        if (listener != null) {
            changeListeners.addIfAbsent(listener);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static void notifyListeners() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (Throwable ignored) {
            }
        }
    }
}