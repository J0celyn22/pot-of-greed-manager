package Controller;

import Model.CardsLists.Card;

import java.util.HashSet;
import java.util.Set;

public class SelectionManager {
    private static Set<Card> selectedCards = new HashSet<>();
    private static String activePart = null; // "MIDDLE" or "RIGHT"

    public static Set<Card> getSelectedCards() {
        return selectedCards;
    }

    public static String getActivePart() {
        return activePart;
    }

    public static void clearSelection() {
        selectedCards.clear();
        activePart = null;
    }

    // Normal click – clear previous selection then select card from the given part.
    public static void selectCard(Card card, String part) {
        clearSelection();
        selectedCards.add(card);
        activePart = part;
    }

    // Add to selection – if the active part is not the same, then clear first.
    public static void addToSelection(Card card, String part) {
        if (activePart == null || !activePart.equals(part)) {
            clearSelection();
        }
        selectedCards.add(card);
        activePart = part;
    }

    public static void removeFromSelection(Card card) {
        selectedCards.remove(card);
        if (selectedCards.isEmpty()) {
            activePart = null;
        }
    }
}