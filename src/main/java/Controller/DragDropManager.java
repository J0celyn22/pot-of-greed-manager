package Controller;

import Model.CardsLists.Card;

public class DragDropManager {
    private static Card currentlyDraggedCard;

    public static Card getCurrentlyDraggedCard() {
        return currentlyDraggedCard;
    }

    public static void setCurrentlyDraggedCard(Card card) {
        currentlyDraggedCard = card;
    }

    public static void clearCurrentlyDraggedCard() {
        currentlyDraggedCard = null;
    }
}
