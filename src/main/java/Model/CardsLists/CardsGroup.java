package Model.CardsLists;

import java.util.ArrayList;
import java.util.List;

/**
 * A named group of cards (e.g. "Fusion Monsters", a deck name, or a collection name).
 */
public class CardsGroup {
    private String name;

    private List<CardElement> cardList;

    public CardsGroup(String name) {
        this.name = name;
        this.cardList = new ArrayList<>();
    }

    public CardsGroup(String name, List<CardElement> cardList) {
        this.name = name;
        this.cardList = cardList;
    }

    /**
     * Returns the name of this group.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this group.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the list of cards in this group.
     */
    public List<CardElement> getCardList() {
        return cardList;
    }

    /**
     * Replaces the card list for this group.
     */
    public void setCardList(List<CardElement> cardList) {
        this.cardList = cardList;
    }

    /**
     * Appends a card to the end of this group.
     */
    public void addCard(CardElement cardToAdd) {
        this.cardList.add(cardToAdd);
    }

    /**
     * Returns the total number of cards in this group.
     */
    public int getCardCount() {
        return this.cardList.size();
    }

    /**
     * Returns the sum of prices of all cards in this group.
     * Cards with a null card reference or null price are skipped.
     */
    public String getPrice() {
        float price = 0;
        for (CardElement cardElement : this.cardList) {
            if (cardElement.getCard() != null && cardElement.getPrice() != null) {
                price += Float.parseFloat(cardElement.getPrice());
            }
        }
        return String.valueOf(price);
    }

    /**
     * Returns a string representation of this group: the group name followed by
     * each card on its own line.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.name);
        for (CardElement cardElement : this.cardList) {
            sb.append('\n').append(cardElement.toString());
        }
        return sb.toString();
    }
}