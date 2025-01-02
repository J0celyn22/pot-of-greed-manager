package Model.CardsLists;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A physical list of cards. Cards are stored in boxes.
 */
public class OwnedCardsCollection implements CardsListFile {
    private List<Box> ownedCollection;

    public OwnedCardsCollection(String filePathStr) throws Exception {
        List<String> collectionFileList = CardsListFile.readFile(filePathStr);
        this.ownedCollection = new ArrayList<>();

        for (String s : collectionFileList) {
            if (!s.contains("#") && !s.contains("!") && !s.contains("TKN")) {
                if (s.contains("=")) {
                    AddBox(s);
                } else if (s.contains("--")) {
                    AddCategoryToLastBox(s);
                } else {
                    AddCardToLastBox(new CardElement(s));
                }
            }
        }
    }

    public OwnedCardsCollection() {
        this.ownedCollection = new ArrayList<>();
    }

    /**
     * Retrieves the list of all boxes within this collection.
     *
     * @return the list of boxes within this collection
     */
    public List<Box> getOwnedCollection() {
        return ownedCollection;
    }

    /**
     * Sets the list of boxes within this collection.
     * <p>
     *
     * @param ownedCollection the new list of boxes to set
     */
    public void setOwnedCollection(List<Box> ownedCollection) {
        this.ownedCollection = ownedCollection;
    }

    /**
     * Calculates the total number of categories across all boxes in the owned collection.
     *
     * @return the total number of categories within this collection
     */
    public int getSize() {
        int size = 0;

        for (Box box : this.ownedCollection) {
            size += box.getContent().size();
        }

        return size;
    }

    /**
     * Adds a new box to the collection with the given name.
     *
     * @param boxName the name of the box to add
     */
    public void AddBox(String boxName) {
        this.ownedCollection.add(new Box(boxName));
    }

    /**
     * Adds a new category to the last box in the owned collection with the given name.
     *
     * @param categoryName the name of the category to add
     */
    public void AddCategoryToLastBox(String categoryName) {
        this.ownedCollection.get(this.ownedCollection.size() - 1).AddCategory(categoryName);
    }

    /**
     * Adds a card to the last category in the last box in the owned collection.
     *
     * @param card the card to add to the last category of the last box
     */
    public void AddCardToLastBox(CardElement card) {
        this.ownedCollection.get(this.ownedCollection.size() - 1).AddCardToLastCategory(card);
    }

    /**
     * Saves the owned collection to a file with the given path.
     * <p>
     * The file is formatted as follows:
     * <pre>
     * Box name
     * ===[Box name]========================================
     * Category name
     * ---[Category name]------------------------------------
     * [Card element]
     * [Card element]
     * ...
     * </pre>
     * <p>
     * @param filePathStr the path of the file to write to
     * @throws Exception if an error occurs while writing to the file
     */
    public void SaveCollection(String filePathStr) throws Exception {
        try (PrintWriter writer = new PrintWriter(filePathStr, StandardCharsets.UTF_8)) {
            for (Box box : this.ownedCollection) {
                String boxNameLine = "===" + box.getName() + new String(new char[30 - box.getName().length()]).replace("\0", "=");
                writer.println(boxNameLine);
                for (CardsGroup group : box.getContent()) {
                    String groupNameLine = "---" + group.getName() + new String(new char[30 - group.getName().length()]).replace("\0", "-");
                    writer.println(groupNameLine);
                    for (CardElement card : group.getCardList()) {
                        writer.println(card.toString());
                    }
                }
            }
        }
    }

    /**
     * Returns a list of all CardElement objects contained in the owned collection.
     * <p>
     * This method iterates through each box in the owned collection, and then
     * through each category in those boxes, aggregating all CardElement objects
     * into a single list.
     * </p>
     * @return a List of CardElement objects from all categories in all boxes
     */
    public List<CardElement> toList() {
        List<CardElement> returnValue = new ArrayList<>();

        for (int i = 0; i < this.getOwnedCollection().size(); i++) {
            for (int j = 0; j < this.getOwnedCollection().get(i).getContent().size(); j++) {
                returnValue.addAll(this.getOwnedCollection().get(i).getContent().get(j).cardList);
            }
        }

        return returnValue;
    }

    /**
     * Calculates the total number of cards in all boxes of the owned collection.
     * <p>
     * This method iterates through each box in the owned collection and sums up
     * the total number of cards in each box.
     * </p>
     *
     * @return the total number of cards in the owned collection as an Integer
     */
    public Integer getCardCount() {
        Integer returnValue = 0;
        for (Box box : this.ownedCollection) {
            returnValue += box.getCardCount();
        }
        return returnValue;
    }

    /**
     * Calculates the total price of all cards in all boxes of the owned collection.
     * <p>
     * This method iterates through each box in the owned collection and sums up
     * the total price of all cards in each box.
     * </p>
     *
     * @return the total price of all cards in the owned collection as a String
     */
    public String getPrice() {
        float returnValue = 0;
        for (Box box : this.ownedCollection) {
            returnValue += Float.parseFloat(box.getPrice());
        }
        return String.valueOf(returnValue);
    }

    /**
     * Converts this OwnedCardsCollection to a string.
     * <p>
     * The string representation of this OwnedCardsCollection is a newline-separated
     * list of the string representations of all boxes in the owned collection.
     * </p>
     *
     * @return a string representation of this OwnedCardsCollection
     */
    public String toString() {
        String returnValue = "";

        for (Box box : this.ownedCollection) {
            returnValue = returnValue.concat(box.toString() + "\n");
        }

        return returnValue;
    }
}
