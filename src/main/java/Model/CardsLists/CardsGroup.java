package Model.CardsLists;

import java.util.ArrayList;
import java.util.List;

/**
 * Group of cards with a name (e.g. "Fusion Monsters", "\<Deck Name\>" or "\<Collection Name\>" )
 */
public class CardsGroup {
    public String name;

    public List<CardElement> cardList;

    public CardsGroup(String name) {
        this.name = name;
        this.cardList = new ArrayList<>();
    }

    public CardsGroup(String name, List<CardElement> cardList) {
        this.name = name;
        this.cardList = cardList;
    }

    /**
     * Gets the name of the group.
     *
     * @return The name of the group.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the card group.
     *
     * @param name the new name for the card group
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the list of cards in the group.
     * @return The list of cards in the group.
     */
    public List<CardElement> getCardList() {
        return cardList;
    }

    /**
     * Sets the list of cards in the group.
     *
     * @param cardList the new list of CardElement objects to be set for this group
     */
    public void setCardList(List<CardElement> cardList) {
        this.cardList = cardList;
    }

    /**
     * Adds a card to the group.
     * @param cardToAdd the CardElement object to be added to the group
     */
    public void AddCard(CardElement cardToAdd) {
        this.cardList.add(cardToAdd);
    }

    /**
     * Returns the total number of cards in the group.
     *
     * @return The total number of cards as an Integer.
     */
    public Integer getCardCount() {
        return this.cardList.size();
    }

    /**
     * Returns the total price of all cards in the group.
     *
     * <p>
     * The total price is the sum of the prices of all cards in the group.
     * The price of each card is the price of the card object itself, not the
     * price of the card element.
     * </p>
     * @return The total price of all cards in the group as a string.
     */
    public String getPrice() {
        float price = 0;
        for (CardElement card : this.cardList) {
            if (card.getCard() != null) {
                if (card.getPrice() != null) {
                    price += Float.parseFloat(card.getPrice());
                }
            }
        }
        return String.valueOf(price);
    }

    /**
     * Converts this CardsGroup to a string.
     * <p>
     * The string representation of this CardsGroup is in the format of
     * "name\nCardElement1\nCardElement2\n..." where "name" is the name of the
     * group and CardElement1, CardElement2, ... are the strings
     * representations of the cards in the group.
     * </p>
     *
     * @return A string representation of this CardsGroup.
     */
    public String toString() {
        String returnValue = this.name;

        for (CardElement cardElement : this.cardList) {
            returnValue = returnValue.concat("\n" + cardElement.toString());
        }

        return returnValue;
    }
}
