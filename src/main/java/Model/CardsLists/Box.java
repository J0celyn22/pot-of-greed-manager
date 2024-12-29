package Model.CardsLists;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for physical list of categories. Each category is a list of cards with or without a name.
 */
public class Box {
    String name;

    public List<CardsGroup> content;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<CardsGroup> getContent() {
        return content;
    }

    public void setContent(List<CardsGroup> content) {
        this.content = content;
    }

    public Box(String name) {
        this.name = name;
        this.content = new ArrayList<>();
    }

    public void AddCardToLastCategory(CardElement card) {
        if (this.content.size() == 0) {
            this.content = new ArrayList<>();
            this.content.add(new CardsGroup(""));
        }
        this.content.get(this.content.size() - 1).AddCard(card);
    }

    public void AddCategory(String categoryName) {
        this.content.add(new CardsGroup(categoryName));
    }

    public String toString() {
        String returnValue = this.name;

        for (int i = 0; i < this.content.size(); i++) {
            returnValue = returnValue.concat("\n" + this.content.get(i).toString());
        }

        return returnValue;
    }

    public Integer getCardCount() {
        Integer returnValue = 0;

        for (int i = 0; i < this.content.size(); i++) {
            returnValue = returnValue + this.content.get(i).getCardCount();
        }

        return returnValue;
    }

    public String getPrice() {
        float returnValue = 0;

        for (int i = 0; i < this.content.size(); i++) {
            returnValue += Float.valueOf(this.content.get(i).getPrice());
        }

        return String.valueOf(returnValue);
    }
}
