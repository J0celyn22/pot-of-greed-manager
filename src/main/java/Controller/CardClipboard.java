package Controller;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Static clipboard holding the most recently copied selection of
 * {@link CardElement} snapshots.
 * <p>
 * Each entry is a copy-constructed {@link CardElement} so that
 * {@code condition}, {@code rarity}, {@code customTags}, artwork flags, and
 * every other per-copy field are preserved across a copy/paste cycle.
 * </p>
 * <p>
 * Registered change listeners are notified synchronously whenever the
 * clipboard contents change.
 * </p>
 */
public class CardClipboard {

    private static final List<CardElement> contents = new ArrayList<>();
    private static final CopyOnWriteArrayList<Runnable> changeListeners =
            new CopyOnWriteArrayList<>();

    private CardClipboard() { /* static utility */ }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Replaces the clipboard contents with snapshots of the given
     * {@link CardElement} list.
     * <p>
     * Each element is copy-constructed via {@link CardElement#CardElement(CardElement)}
     * so that {@code condition}, {@code rarity}, {@code customTags}, and all
     * other per-copy fields are preserved. {@code null} entries are silently
     * skipped.
     * </p>
     *
     * @param elements the elements to copy onto the clipboard; may be
     *                 {@code null}, which is treated as an empty list
     */
    public static void copyElements(List<CardElement> elements) {
        contents.clear();
        if (elements != null) {
            for (CardElement element : elements) {
                if (element != null) {
                    contents.add(new CardElement(element));
                }
            }
        }
        notifyListeners();
    }

    /**
     * Convenience overload that wraps each {@link Card} in a plain
     * {@link CardElement} before storing it.
     * <p>
     * Use this only when no {@link CardElement} context is available (e.g.
     * when copying from the right-pane card list where only {@link Card}
     * objects exist). For the middle-pane owned collection, prefer
     * {@link #copyElements(List)} so that condition / rarity / tags are kept.
     * </p>
     *
     * @param cards the cards to copy; may be {@code null}
     * @deprecated Prefer {@link #copyElements(List)} to preserve per-copy
     * fields such as condition and rarity.
     */
    @Deprecated
    public static void copyCards(List<Card> cards) {
        contents.clear();
        if (cards != null) {
            for (Card card : cards) {
                if (card != null) {
                    contents.add(new CardElement(card));
                }
            }
        }
        notifyListeners();
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns an unmodifiable view of the current clipboard contents as
     * {@link CardElement} snapshots.
     *
     * @return immutable list of element snapshots; never {@code null}
     */
    public static List<CardElement> getContents() {
        return Collections.unmodifiableList(contents);
    }

    /**
     * Returns an unmodifiable view of the clipboard contents as plain
     * {@link Card} references extracted from the stored element snapshots.
     * <p>
     * Provided for callers that have not yet been updated to work with
     * {@link CardElement} snapshots. Prefer {@link #getContents()} to
     * avoid discarding per-copy metadata.
     * </p>
     *
     * @return immutable list of cards; never {@code null}
     * @deprecated Use {@link #getContents()} to retrieve the full
     *             {@link CardElement} snapshots, including condition and rarity.
     */
    @Deprecated
    public static List<Card> getCardContents() {
        List<Card> cards = new ArrayList<>(contents.size());
        for (CardElement element : contents) {
            if (element.getCard() != null) {
                cards.add(element.getCard());
            }
        }
        return Collections.unmodifiableList(cards);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the clipboard currently holds no elements.
     *
     * @return {@code true} when the clipboard is empty
     */
    public static boolean isEmpty() {
        return contents.isEmpty();
    }

    /**
     * Removes all elements from the clipboard and notifies registered
     * listeners.
     */
    public static void clear() {
        contents.clear();
        notifyListeners();
    }

    /**
     * Registers a listener that is invoked whenever the clipboard contents
     * change (on copy or clear). Duplicate registrations are silently ignored.
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