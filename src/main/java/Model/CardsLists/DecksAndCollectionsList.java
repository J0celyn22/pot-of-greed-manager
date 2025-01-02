package Model.CardsLists;

import java.io.File;
import java.util.*;

public class DecksAndCollectionsList {
    public List<Deck> decks;
    public List<ThemeCollection> collections;

    public DecksAndCollectionsList(String dirPath) throws Exception {
        this.decks = new ArrayList<>();
        this.collections = new ArrayList<>();
        File dir = new File(dirPath);

        // First, import the collections
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.getPath().endsWith(".ytc")) {
                ThemeCollection themeCollection = new ThemeCollection(file.getPath());
                this.collections.add(themeCollection);
            }
        }

        // Then, import the decks, excluding those already in any collection's linkedDecks list
        Set<String> linkedDeckNames = new HashSet<>();
        for (ThemeCollection collection : this.collections) {
            for (Deck linkedDeck : collection.getLinkedDecks()) {
                linkedDeckNames.add(linkedDeck.getName());
            }
        }

        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.getPath().endsWith(".ydk")) {
                Deck deck = new Deck(file.getPath());
                if (!linkedDeckNames.contains(deck.getName())) {
                    this.decks.add(deck);
                }
            } else if (!file.getPath().endsWith(".ytc")) {
                System.out.println("Unable to load file: " + file.getPath());
            }
        }
    }

    public DecksAndCollectionsList() {
        this.decks = new ArrayList<>();
        this.collections = new ArrayList<>();
    }

    /**
     * Retrieves the list of all decks in the list.
     *
     * @return a List of Deck objects
     */
    public List<Deck> getDecks() {
        return decks;
    }

    /**
     * Sets the list of decks in the list.
     *
     * @param decks the list of Deck objects to set
     */
    public void setDecks(List<Deck> decks) {
        this.decks = decks;
    }

    /**
     * Retrieves the list of all collections in the list.
     * <p>
     * A collection is a group of cards and decks that are linked together.
     * </p>
     *
     * @return a List of ThemeCollection objects
     */
    public List<ThemeCollection> getCollections() {
        return collections;
    }

    /**
     * Sets the list of collections in the DecksAndCollectionsList.
     *
     * @param collections the list of ThemeCollection objects to set
     */
    public void setCollections(List<ThemeCollection> collections) {
        this.collections = collections;
    }

    /**
     * Returns a list of all CardElement objects in the DecksAndCollectionsList.
     * <p>
     * This includes all cards in all decks and collections.
     * </p>
     *
     * @return a List of CardElement objects
     */
    public List<CardElement> toList() {
        List<CardElement> returnValue = new ArrayList<>();

        if (this.getDecks() != null) {
            for (int i = 0; i < this.getDecks().size(); i++) {
                returnValue.addAll(this.getDecks().get(i).toList());
            }
        }

        if (this.getCollections() != null) {
            for (int i = 0; i < this.getCollections().size(); i++) {
                returnValue.addAll(this.getCollections().get(i).getCardsList());
            }
        }

        return returnValue;
    }

    /**
     * Returns a list of all CardElement objects in the DecksAndCollectionsList that are in a collection or a linked deck.
     * <p>
     * This includes all cards in all collections and their linked decks.
     * </p>
     * @return a List of CardElement objects
     */
    public List<CardElement> toListCollectionsAndLinkedDecks() {
        List<CardElement> returnValue = new ArrayList<>();

        for (int i = 0; i < this.getCollections().size(); i++) {
            returnValue.addAll(this.getCollections().get(i).toList());
        }

        return returnValue;
    }

    /**
     * Adds a deck to the list of decks in this DecksAndCollectionsList.
     * @param deckToAdd the deck to add to the list
     */
    public void addDeck(Deck deckToAdd) {
        this.decks.add(deckToAdd);
    }

    /**
     * Adds a collection to the list of collections in this DecksAndCollectionsList.
     * <p>
     * This collection and its linked decks will be included in the results of
     * {@link #toList()} and {@link #toListCollectionsAndLinkedDecks()}.
     * </p>
     * @param collectionToAdd the collection to add to the list
     */
    public void addCollection(ThemeCollection collectionToAdd) {
        this.collections.add(collectionToAdd);
    }

    /**
     * Returns the total number of cards in all decks and collections in this DecksAndCollectionsList.
     * <p>
     * This includes all cards in all decks and collections.
     * </p>
     * @return the total number of cards in all decks and collections
     */
    public Integer getCardCount() {
        return getCollectionsCardCount() + getDecksCardCount();
    }

    /**
     * Calculates the total price of all cards in the decks and collections.
     *
     * <p>
     * This method sums the prices of all cards in both the collections and
     * decks contained within this DecksAndCollectionsList. The price is
     * represented as a string in a float format.
     * </p>
     *
     * @return the total price of all cards as a String
     */
    public String getPrice() {
        float returnValue;

        returnValue = Float.parseFloat(getCollectionsPrice()) + Float.parseFloat(getDecksPrice());

        return String.valueOf(returnValue);
    }

    /**
     * Returns the total number of cards in all collections in this DecksAndCollectionsList.
     * <p>
     * This method sums the number of cards in all collections contained within this
     * DecksAndCollectionsList.
     * </p>
     * @return the total number of cards in all collections
     */
    public Integer getCollectionsCardCount() {
        int returnValue = 0;

        for (ThemeCollection collection : this.collections) {
            returnValue = returnValue + collection.getCardCount();
        }

        return returnValue;
    }

    /**
     * Calculates the total price of all cards in all collections in this DecksAndCollectionsList.
     *
     * <p>
     * This method sums the prices of all cards in all collections contained within this
     * DecksAndCollectionsList. The price is represented as a string in a float format.
     * </p>
     *
     * @return the total price of all cards in all collections as a String
     */
    public String getCollectionsPrice() {
        float returnValue = 0;

        for (ThemeCollection collection : this.collections) {
            returnValue = returnValue + Float.parseFloat(collection.getPrice());
        }

        return String.valueOf(returnValue);
    }

    /**
     * Returns the total number of cards in all decks in this DecksAndCollectionsList.
     * <p>
     * This method sums the number of cards in all decks contained within this
     * DecksAndCollectionsList.
     * </p>
     * @return the total number of cards in all decks
     */
    public Integer getDecksCardCount() {
        int returnValue = 0;

        for (Deck deck : this.decks) {
            returnValue = returnValue + deck.getCardCount();
        }

        return returnValue;
    }

    /**
     * Calculates the total price of all cards in all decks in this DecksAndCollectionsList.
     *
     * <p>
     * This method sums the prices of all cards in all decks contained within this
     * DecksAndCollectionsList. The price is represented as a string in a float format.
     * </p>
     *
     * @return the total price of all cards in all decks as a String
     */
    public String getDecksPrice() {
        float returnValue = 0;

        for (Deck deck : this.decks) {
            returnValue = returnValue + Float.parseFloat(deck.getPrice());
        }

        return String.valueOf(returnValue);
    }

    /**
     * Returns a string representation of this DecksAndCollectionsList.
     * <p>
     * The string representation of this DecksAndCollectionsList is a newline-separated
     * list of the string representations of all decks in this DecksAndCollectionsList.
     * </p>
     *
     * @return a string representation of this DecksAndCollectionsList
     */
    @Override
    public String toString() {
        String returnValue = "";

        for (Deck deck : this.decks) {
            returnValue = returnValue.concat(deck.toString() + "\n");
        }

        return returnValue;
    }
}
