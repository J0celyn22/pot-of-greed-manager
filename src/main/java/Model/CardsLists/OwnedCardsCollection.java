package Model.CardsLists;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * A physical list of cards. Cards are stored in boxes.
 */
public class OwnedCardsCollection implements CardsListFile {
    private List<Box> ownedCollection;

    public List<Box> getOwnedCollection() {
        return ownedCollection;
    }

    public void setOwnedCollection(List<Box> ownedCollection) {
        this.ownedCollection = ownedCollection;
    }

    public OwnedCardsCollection(String filePathStr) throws Exception {
        List<String> collectionFileList = CardsListFile.readFile(filePathStr);
        this.ownedCollection = new ArrayList<>();

        for (int i = 0; i < collectionFileList.size(); i++) {
            if (!collectionFileList.get(i).contains("#") && !collectionFileList.get(i).contains("!") && !collectionFileList.get(i).contains("TKN") && !collectionFileList.get(i).equals("SDCK-FR050")) {
                if (collectionFileList.get(i).contains("=")) {
                    AddBox(collectionFileList.get(i));
                } else if (collectionFileList.get(i).contains("--")) {
                    AddCategoryToLastBox(collectionFileList.get(i));
                } /*else if (collectionFileList.get(i).contains(",")) {
                    String[] parts = collectionFileList.get(i).split(",", 2);
                    String part1 = parts[0];
                    String part2 = parts[1];
                    AddCardToLastBox(new Card(part1, part2));
                } else {
                    AddCardToLastBox(new Card(collectionFileList.get(i)));
                }*/
                else {
                    AddCardToLastBox(new CardElement(collectionFileList.get(i)));
                }
            }
        }
    }

    public OwnedCardsCollection() {
        this.ownedCollection = new ArrayList<>();
    }

    /*public OwnedCardsCollection(String filePathStr) throws Exception {
        List<String> collectionFileList = CardsListFile.readFile(filePathStr);
        this.ownedCollection = new ArrayList<>();

        Box currentBox = null;
        CardsGroup currentGroup = null;
        for (String line : collectionFileList) {
            if (line.startsWith("===")) {
                currentBox = new Box(line.substring(3).trim());
                this.ownedCollection.add(currentBox);
            } else if (line.startsWith("---")) {
                currentGroup = new CardsGroup(line.substring(3).trim());
                currentBox.getContent().add(currentGroup);
            } else {
                String[] parts = line.split(",", 2);
                currentGroup.AddCard(new Card(parts[0], parts.length > 1 ? parts[1] : null));
            }
        }
    }*/

    public int getSize() {
        int size = 0;

        for (int i = 0; i < this.ownedCollection.size(); i++) {
            size += ownedCollection.get(i).getContent().size();
        }

        return size;
    }

    public void AddBox(String boxName) {
        this.ownedCollection.add(new Box(boxName));
    }

    public void AddCategoryToLastBox(String categoryName) {
        this.ownedCollection.get(this.ownedCollection.size() - 1).AddCategory(categoryName);
    }

    public void AddCardToLastBox(CardElement card) {
        this.ownedCollection.get(this.ownedCollection.size() - 1).AddCardToLastCategory(card);
    }

    public void SaveCollection(String filePathStr) throws Exception {
        try (PrintWriter writer = new PrintWriter(filePathStr, "UTF-8")) {
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

    public List<CardElement> toList() {
        List<CardElement> returnValue = new ArrayList<>();

        for (int i = 0; i < this.getOwnedCollection().size(); i++) {
            for (int j = 0; j < this.getOwnedCollection().get(i).getContent().size(); j++) {
                for (int k = 0; k < this.getOwnedCollection().get(i).getContent().get(j).cardList.size(); k++) {
                    returnValue.add(this.getOwnedCollection().get(i).getContent().get(j).cardList.get(k));
                }
            }
        }

        return returnValue;
    }

    public String toString() {
        String returnValue = "";

        for (int i = 0; i < this.ownedCollection.size(); i++) {
            returnValue = returnValue.concat(this.ownedCollection.get(i).toString() + "\n");
        }

        return returnValue;
    }

    public Integer getCardCount() {
        Integer returnValue = 0;
        for (int i = 0; i < this.ownedCollection.size(); i++) {
            returnValue += this.ownedCollection.get(i).getCardCount();
        }
        return returnValue;
    }

    public String getPrice() {
        float returnValue = 0;
        for (int i = 0; i < this.ownedCollection.size(); i++) {
            returnValue += Float.valueOf(this.ownedCollection.get(i).getPrice());
        }
        return String.valueOf(returnValue);
    }
}
