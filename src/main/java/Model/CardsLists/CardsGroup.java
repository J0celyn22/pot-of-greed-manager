package Model.CardsLists;

import java.util.ArrayList;
import java.util.List;

/**
 * Group of cards with a name (e.g. "Fusion Monsters", "\<Deck Name\>" or "\<Collection Name\>" )
 */
public class CardsGroup {
    public String name;

    public List<CardElement> cardList;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<CardElement> getCardList() {
        return cardList;
    }

    public void setCardList(List<CardElement> cardList) {
        this.cardList = cardList;
    }

    public CardsGroup(String name) {
        this.name = name;
        this.cardList = new ArrayList<>();
    }

    public CardsGroup(String name, List<CardElement> cardList) {
        this.name = name;
        this.cardList = cardList;
    }

    public void AddCard(CardElement cardToAdd) {
        this.cardList.add(cardToAdd);
    }

    public String toString() {
        String returnValue = this.name;

        for (int i = 0; i < this.cardList.size(); i++) {
            returnValue = returnValue.concat("\n" + this.cardList.get(i).toString());
        }

        return returnValue;
    }

    public Integer getCardCount() {
        return this.cardList.size();
    }

    public String getPrice() {
        float price = 0;
        for (CardElement card : this.cardList) {
            if(card.getCard() != null) {
                if(card.getPrice() != null) {
                    price += Float.valueOf(card.getPrice());
                }
            }
        }
        return String.valueOf(price);
    }
}
