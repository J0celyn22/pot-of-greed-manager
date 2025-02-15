package Model.CardsLists;

import java.util.ArrayList;
import java.util.List;

/**
 * Container for physical list of categories. Each category is a list of cards with or without a name.
 */
public class Box {
    public List<CardsGroup> content;
    String name;
    public List<Box> subBoxes;

    public Box(String name) {
        this.name = name;
        this.content = new ArrayList<>();
        this.subBoxes = new ArrayList<>();
    }

    /**
     * Gets the name of the box.
     *
     * @return The name of the box.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of the box.
     * @param name The name of the box.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the content of the box, which is a list of categories.
     * @return The content of the box.
     */
    public List<CardsGroup> getContent() {
        return content;
    }

    /**
     * Sets the content of the box.
     * @param content The content of the box. Each item in the list is a category of cards.
     */
    public void setContent(List<CardsGroup> content) {
        this.content = content;
    }


    /**
     * Retrieves the list of all sub-boxes within this box.
     *
     * @return The list of sub-boxes within this box
     */
    public List<Box> getSubBoxes() {
        return subBoxes;
    }

    /**
     * Sets the list of sub-boxes within this box.
     * <p>
     * The sub-boxes are stored in the order they are given, and can be retrieved
     * using {@link #getSubBoxes()}.
     * </p>
     *
     * @param subBoxes The list of sub-boxes to set
     */
    public void setSubBoxes(List<Box> subBoxes) {
        this.subBoxes = subBoxes;
    }

    /**
     * Adds a card to the last category in the box. If the box has no category, a new category is created.
     * @param card The card to add to the last category of the box.
     */
    public void AddCardToLastCategory(CardElement card) {
        if (this.content.isEmpty()) {
            this.content = new ArrayList<>();
            this.content.add(new CardsGroup(""));
        }
        this.content.get(this.content.size() - 1).AddCard(card);
    }

    /**
     * Adds a new category to the box with the given name.
     * @param categoryName The name of the category to add.
     */
    public void AddCategory(String categoryName) {
        this.content.add(new CardsGroup(categoryName));
    }

    /**
     * Returns a string representation of the box, which includes its name
     * followed by the string representation of each category within the box.
     *
     * @return A string representation of the box and its categories.
     */
    public String toString() {
        String returnValue = this.name;

        for (CardsGroup cardsGroup : this.content) {
            returnValue = returnValue.concat("\n" + cardsGroup.toString());
        }

        return returnValue;
    }

    /**
     * Returns the total number of cards in all categories of the box.
     *
     * @return The total number of cards in all categories of the box.
     */
    public Integer getCardCount() {
        int returnValue = 0;

        for (CardsGroup cardsGroup : this.content) {
            returnValue = returnValue + cardsGroup.getCardCount();
        }

        return returnValue;
    }

    /**
     * Returns the total price of all cards in all categories of the box.
     *
     * The total price is the sum of the prices of all cards in all categories
     * of the box. The price of each card is the price of the card object
     * itself, not the price of the card element.
     *
     * @return The total price of all cards in all categories of the box as a
     * string.
     */
    public String getPrice() {
        float returnValue = 0;

        for (CardsGroup cardsGroup : this.content) {
            returnValue += Float.parseFloat(cardsGroup.getPrice());
        }

        return String.valueOf(returnValue);
    }
}
