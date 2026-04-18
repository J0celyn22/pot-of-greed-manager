package Controller;

import Model.CardsLists.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CardClipboard {

    private static final List<Card> contents = new ArrayList<>();
    private static final CopyOnWriteArrayList<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    public static void copyCards(List<Card> cards) {
        contents.clear();
        if (cards != null) {
            for (Card card : cards) {
                if (card != null) contents.add(card);
            }
        }
        notifyListeners();
    }

    public static List<Card> getContents() {
        return Collections.unmodifiableList(contents);
    }

    public static boolean isEmpty() {
        return contents.isEmpty();
    }

    public static void clear() {
        contents.clear();
        notifyListeners();
    }

    public static void addChangeListener(Runnable listener) {
        if (listener != null) changeListeners.addIfAbsent(listener);
    }

    private static void notifyListeners() {
        for (Runnable listener : changeListeners) {
            try {
                listener.run();
            } catch (Throwable ignored) {
            }
        }
    }
}