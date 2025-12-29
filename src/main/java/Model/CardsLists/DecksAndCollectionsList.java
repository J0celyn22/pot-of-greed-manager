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
            for (int i = 0; i < collection.getLinkedDecks().size(); i++) {
                for (Deck linkedDeck : collection.getLinkedDecks().get(i)) {
                    linkedDeckNames.add(linkedDeck.getName());
                }
            }
        }

        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.getPath().endsWith(".ydk")) {
                Deck deck = new Deck(file.getPath());
                if (!linkedDeckNames.contains(deck.getName())) {
                    this.decks.add(deck);
                }
            } /*else if (!file.getPath().endsWith(".ytc")) {
                System.out.println("Unable to load file: " + file.getPath());
            }*/
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
    //public List<CardElement> toList() throws Exception {
    //    List<CardElement> returnValue = new ArrayList<>();
    //    List<ThemeCollection> looseCollections = new ArrayList<>();
//
    //    //NEW ?
    //    //First add the cards from the Collections
    //    if (this.getCollections() != null) {
    //        /*for (int i = 0; i < this.getCollections().size(); i++) {
    //            //First add the cards from the decks linked to the collection
    //            for (int j = 0; j < this.getCollections().get(i).getLinkedDecks().size(); j++) {
    //                //returnValue.addAll(this.getCollections().get(i).getLinkedDecks().get(j).toList());
    //                for (int k = 0; k < this.getCollections().get(i).getLinkedDecks().get(j).toList().size(); k++) {
    //                    if (this.getCollections().get(i).getCardsList().contains(this.getCollections().get(i).getLinkedDecks().get(j).toList().get(k))) {
    //                        if (this.getCollections().get(i).getCardsList().get(this.getCollections().get(i).getCardsList().indexOf(this.getCollections().get(i).getLinkedDecks().get(j).toList().get(k))).getSpecificArtwork()) {
    //                            returnValue.add(this.getCollections().get(i).getCardsList().get(this.getCollections().get(i).getCardsList().indexOf(this.getCollections().get(i).getLinkedDecks().get(j).toList().get(k))));
    //                        }
    //                        else {
    //                            returnValue.add(this.getCollections().get(i).getLinkedDecks().get(j).toList().get(k));
    //                        }
    //                        this.getCollections().get(i).getCardsList().get(this.getCollections().get(i).getCardsList().indexOf(this.getCollections().get(i).getLinkedDecks().get(j).toList().get(k))).setIsInDeck(true);
    //                    }
    //                    //returnValue.add(this.getCollections().get(i).getLinkedDecks().get(j).toList().get(k));
    //                }
    //            }
//
    //            //Then add the remaining cards that have not already been checked
    //            for (int j = 0; j < this.getCollections().get(i).getCardsList().size(); j++) {
    //                if(!this.getCollections().get(i).getCardsList().get(j).getIsInDeck() || this.getCollections().get(i).getCardsList().get(j).getDontRemove()) {
    //                    returnValue.add(this.getCollections().get(i).getCardsList().get(j));
    //                }
    //            }
    //            //returnValue.addAll(this.getCollections().get(i).getCardsList());
    //        }*/
//
    //        for (ThemeCollection collection : this.getCollections()) {
    //            if (!collection.getConnectToWholeCollection()) {
    //                returnValue.addAll(collection.toList());
    //            } else {
    //                looseCollections.add(collection);
    //            }
//
    //        }
    //    }
//
    //    //Then add the cards from the decks that are not in any collection
    //    if (this.getDecks() != null) {
    //        for (int i = 0; i < this.getDecks().size(); i++) {
    //            //If the deck is not in any collection, add it
    //            boolean deckIsInCollections = false;
    //            for (int j = 0; j < this.getCollections().size(); j++) {
    //                if (this.getCollections().get(j).getLinkedDecks().contains(this.getDecks().get(i))) {
    //                    deckIsInCollections = true;
    //                    break;
    //                }
    //            }
    //            if (deckIsInCollections) {
    //                returnValue.addAll(this.getDecks().get(i).toList());
    //            }
    //        }
    //    }
//
    //    for (ThemeCollection collection : looseCollections) {
    //        returnValue.addAll(collection.toList());
    //    }
//
//
    //    //OLD ?
    //    // First, import the collections
    //    /*for (File file : Objects.requireNonNull(dir.listFiles())) {
    //        if (file.getPath().endsWith(".ytc")) {
    //            ThemeCollection themeCollection = new ThemeCollection(file.getPath());
    //            this.collections.add(themeCollection);
    //        }
    //    }
//
    //    // Then, import the decks, excluding those already in any collection's linkedDecks list
    //    Set<String> linkedDeckNames = new HashSet<>();
    //    for (ThemeCollection collection : this.collections) {
    //        for (Deck linkedDeck : collection.getLinkedDecks()) {
    //            linkedDeckNames.add(linkedDeck.getName());
    //        }
    //    }
//
    //    for (File file : Objects.requireNonNull(dir.listFiles())) {
    //        if (file.getPath().endsWith(".ydk")) {
    //            Deck deck = new Deck(file.getPath());
    //            if (!linkedDeckNames.contains(deck.getName())) {
    //                this.decks.add(deck);
    //            }
    //        } else if (!file.getPath().endsWith(".ytc")) {
    //            System.out.println("Unable to load file: " + file.getPath());
    //        }
    //    }*/
//
    //    //OLD CODE, REPLACE IT WITH THE ABOVE
    //    /*List<CardElement> returnValue = new ArrayList<>();
//
    //    if (this.getDecks() != null) {
    //        for (int i = 0; i < this.getDecks().size(); i++) {
    //            returnValue.addAll(this.getDecks().get(i).toList());
    //        }
    //    }
//
    //    if (this.getCollections() != null) {
    //        for (int i = 0; i < this.getCollections().size(); i++) {
    //            returnValue.addAll(this.getCollections().get(i).getCardsList());
    //        }
    //    }*/
    //
    //    //WIP
    //    /*if (this.getCollections() != null) {
    //        for (int i = 0; i < this.getCollections().size(); i++) {
    //            if(!this.getCollections().get(i).getConnectToWholeCollection()) {
    //                returnValue.addAll(this.getCollections().get(i).getCardsList());
    //            }
    //        }
    //    }
//
    //    if (this.getDecks() != null) {
    //        for (int i = 0; i < this.getDecks().size(); i++) {
    //            returnValue.addAll(this.getDecks().get(i).toList());
    //        }
    //    }
//
    //    //Finally add all remaining Cards from ThemeCollections that are linked to the whole Collection
    //    if (this.getCollections() != null) {
    //        //First loop to add the Cards that have to have a specific artwork
    //        for (int i = 0; i < this.getCollections().size(); i++) {
    //            if(this.getCollections().get(i).getConnectToWholeCollection()) {
    //                //TODO add all the Cards with getSpecificArtwork()==true that are not already in the list (if printCode exists, use it for the check, otherwise use passCode)
//
    //            }
    //        }
    //        //Second loop to add the remaining Cards
    //        for (int i = 0; i < this.getCollections().size(); i++) {
    //            if(this.getCollections().get(i).getConnectToWholeCollection()) {
    //                //TODO add all the Cards that are not already in the list (if printCode exists, use it for the check, otherwise use passCode)
    //                //returnValue.addAll(this.getCollections().get(i).getCardsList());
    //            }
    //        }
    //    }*/
//
//
    //    return returnValue;
    //}
    public List<CardElement> toList() throws Exception {
        List<CardElement> returnValue = new ArrayList<>();

        // Defensive null checks
        if (this.collections == null) this.collections = new ArrayList<>();
        if (this.decks == null) this.decks = new ArrayList<>();

        // 1) Add collections that are NOT connected to the whole collection:
        //    For those we simply add their explicit cards list (cardsList).
        for (ThemeCollection collection : this.collections) {
            if (collection == null) continue;
            if (!Boolean.TRUE.equals(collection.getConnectToWholeCollection())) {
                List<CardElement> collCards = collection.toList();
                if (collCards != null) returnValue.addAll(collCards);
            }
        }

        // 2) Add all decks (constructor already excluded decks that are linked to collections)
        for (Deck deck : this.decks) {
            if (deck == null) continue;
            List<CardElement> deckCards = deck.toList();
            if (deckCards != null) returnValue.addAll(deckCards);
        }

        // Build quick lookup sets of print/pass codes already present in returnValue
        java.util.Set<String> presentPrint = new java.util.HashSet<>();
        java.util.Set<String> presentPass = new java.util.HashSet<>();
        for (CardElement ce : returnValue) {
            if (ce == null || ce.getCard() == null) continue;
            String p = ce.getCard().getPrintCode();
            String s = ce.getCard().getPassCode();
            if (p != null) presentPrint.add(p);
            if (s != null) presentPass.add(s);
        }

        // 3) For collections that are connected to the whole collection:
        //    First add cards that require specific artwork (specificArtwork == true)
        for (ThemeCollection collection : this.collections) {
            if (collection == null) continue;
            if (!Boolean.TRUE.equals(collection.getConnectToWholeCollection())) continue;

            List<CardElement> collCards = collection.getCardsList();
            if (collCards == null) continue;

            for (CardElement ce : collCards) {
                if (ce == null || ce.getCard() == null) continue;
                if (!ce.getSpecificArtwork()) continue; // only specific artwork in this pass

                String printCode = ce.getCard().getPrintCode();
                String passCode = ce.getCard().getPassCode();

                boolean alreadyPresent = (printCode != null && presentPrint.contains(printCode))
                        || (passCode != null && presentPass.contains(passCode));

                if (!alreadyPresent) {
                    returnValue.add(ce);
                    if (printCode != null) presentPrint.add(printCode);
                    if (passCode != null) presentPass.add(passCode);
                }
            }
        }

        // 4) Second pass: add remaining cards from those collections (non-specific artwork or any left)
        for (ThemeCollection collection : this.collections) {
            if (collection == null) continue;
            if (!Boolean.TRUE.equals(collection.getConnectToWholeCollection())) continue;

            List<CardElement> collCards = collection.getCardsList();
            if (collCards == null) continue;

            for (CardElement ce : collCards) {
                if (ce == null || ce.getCard() == null) continue;

                String printCode = ce.getCard().getPrintCode();
                String passCode = ce.getCard().getPassCode();

                boolean alreadyPresent = (printCode != null && presentPrint.contains(printCode))
                        || (passCode != null && presentPass.contains(passCode));

                if (!alreadyPresent) {
                    returnValue.add(ce);
                    if (printCode != null) presentPrint.add(printCode);
                    if (passCode != null) presentPass.add(passCode);
                } else if (ce.getDontRemove()) {
                    // If the collection explicitly marks this card as dontRemove, ensure the collection's CardElement
                    // is present (replace a deck-provided element if necessary).
                    // Find first matching entry and replace it with this collection element.
                    boolean replaced = false;
                    for (int i = 0; i < returnValue.size(); i++) {
                        CardElement r = returnValue.get(i);
                        if (r == null || r.getCard() == null) continue;
                        boolean matchByPrint = (printCode != null && printCode.equals(r.getCard().getPrintCode()));
                        boolean matchByPass = (passCode != null && passCode.equals(r.getCard().getPassCode()));
                        if (matchByPrint || matchByPass) {
                            if (r != ce) {
                                returnValue.set(i, ce);
                            }
                            replaced = true;
                            break;
                        }
                    }
                    // If not replaced (shouldn't happen because alreadyPresent==true), ensure it's added once.
                    if (!replaced) {
                        returnValue.add(ce);
                    }
                }
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
