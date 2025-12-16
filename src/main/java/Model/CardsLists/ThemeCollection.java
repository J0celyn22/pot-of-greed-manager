package Model.CardsLists;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static Model.CardsLists.ListDifferenceIntersection.ListDifIntersectArtworkWithExceptions;

/**
 * Collection of cards with a theme.
 */
public class ThemeCollection {
    public String name;

    public List<CardElement> cardsList;

    public List<CardElement> exceptionsToNotAdd;

    public List<List<Deck>> linkedDecks;

    public List<String> archetypes;

    //If true, the cards of this collection may be in any other element of the Collection for them to be validated as owned
    public Boolean connectToWholeCollection;

    public ThemeCollection(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        this.name = path.getFileName().toString().replaceFirst("[.][^.]+$", "");

        List<String> lines = Files.readAllLines(path);

        this.cardsList = new ArrayList<>();
        this.exceptionsToNotAdd = new ArrayList<>();
        this.linkedDecks = new ArrayList<>();
        this.archetypes = new ArrayList<>();

        for (String line : lines) {
            if (line.equals("#Not to add") || line.equals("#Link to whole collection") || line.equals("#Linked decks") || line.equals("#Archetypes"))
                break;
            if (line.contains(",")) {
                String[] cardInfo = line.split(",");
                cardsList.add(new CardElement(cardInfo[0]));
                cardsList.get(cardsList.size() - 1).setArtwork(Integer.parseInt(cardInfo[1]));
                cardsList.get(cardsList.size() - 1).setSpecificArtwork(true);
            } else {
                cardsList.add(new CardElement(line));
            }
        }

        int index = lines.indexOf("#Not to add");
        if (index != -1) {
            exceptionsToNotAdd = new ArrayList<>();
            for (int i = index + 1; i < lines.size(); i++) {
                if (lines.get(i).equals("#Link to whole collection") || lines.get(i).equals("#Linked decks") || lines.get(i).equals("#Archetypes"))
                    break;
                exceptionsToNotAdd.add(new CardElement(lines.get(i)));
            }
        }

        index = lines.indexOf("#Link to whole collection");
        connectToWholeCollection = index != -1;

        linkedDecks = new ArrayList<>();
        if (!connectToWholeCollection) {
            index = lines.indexOf("#Linked decks");
            if (index != -1) {
                for (int i = index + 1; i < lines.size(); i++) {
                    if (lines.get(i).equals("#Archetypes")) break;
                    if (lines.get(i).equals("##")) {
                        this.linkedDecks.add(new ArrayList<>());
                    } else {
                        String deckPath = "%s\\%s.ydk".formatted(path.getParent().toString(), lines.get(i));
                        this.AddDeck(new Deck(deckPath));
                    }
                }
            }
        }

        index = lines.indexOf("#Archetypes");
        if (index != -1) {
            for (int i = index + 1; i < lines.size(); i++) {
                archetypes.add(lines.get(i));
            }
        }
    }

    public ThemeCollection() {
        this.cardsList = new ArrayList<>();
        this.exceptionsToNotAdd = new ArrayList<>();
        this.linkedDecks = new ArrayList<>();
        this.archetypes = new ArrayList<>();
        this.connectToWholeCollection = false;
    }

    /**
     * Retrieves the name of this theme collection.
     * <p>
     * The name of the theme collection should be a String that represents the name of the theme collection.
     * </p>
     *
     * @return the name of this theme collection as a String
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this theme collection.
     * <p>
     * The name should be a String that represents the name of the theme collection.
     * </p>
     *
     * @param name the name to set for this theme collection
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Retrieves the list of all cards in this theme collection.
     * <p>
     * The returned list contains all cards that are part of this theme collection.
     * </p>
     *
     * @return the list of all cards in this theme collection
     */
    public List<CardElement> getCardsList() {
        return cardsList;
    }

    /**
     * Sets the list of cards in this theme collection.
     * <p>
     * The provided list of cards should contain all cards that are part of this theme collection.
     * </p>
     *
     * @param cardsList the list of cards to set for this theme collection
     */
    public void setCardsList(List<CardElement> cardsList) {
        this.cardsList = cardsList;
    }

    /**
     * Retrieves the list of exceptions to not add to this theme collection.
     * <p>
     * This list contains all cards that are explicitly not to be added to this theme collection.
     * </p>
     *
     * @return the list of exceptions to not add to this theme collection
     */
    public List<CardElement> getExceptionsToNotAdd() {
        return exceptionsToNotAdd;
    }

    /**
     * Sets the list of exceptions for cards that should not be added to this theme collection.
     * <p>
     * This method assigns the given list of {@link CardElement} objects to the exceptions list
     * for this theme collection. These cards will be explicitly excluded from being added to
     * the collection.
     * </p>
     *
     * @param exceptionsToNotAdd the list of {@link CardElement} objects to set as exceptions
     */
    public void setExceptionsToNotAdd(List<CardElement> exceptionsToNotAdd) {
        this.exceptionsToNotAdd = exceptionsToNotAdd;
    }

    /**
     * Retrieves the list of linked decks for this theme collection.
     * <p>
     * These decks are linked to the theme collection. Cards from these decks are
     * automatically added to the theme collection.
     * </p>
     *
     * @return the list of linked decks for this theme collection
     */
    public List<List<Deck>> getLinkedDecks() {
        return linkedDecks;
    }

    /**
     * Sets the list of linked decks for this theme collection.
     * <p>
     * The provided list of decks should contain all decks that are linked to this theme collection.
     * Cards from these decks are automatically added to the theme collection.
     * </p>
     *
     * @param linkedDecks the list of decks to set as linked decks for this theme collection
     */
    public void setLinkedDecks(List<List<Deck>> linkedDecks) {
        this.linkedDecks = linkedDecks;
    }

    /**
     * Retrieves a boolean indicating if this theme collection should connect to the whole collection of cards.
     * <p>
     * If this is set to true, the theme collection will automatically include all cards in the whole collection.
     * </p>
     *
     * @return true if this theme collection should connect to the whole collection, false if not
     */
    public Boolean getConnectToWholeCollection() {
        return connectToWholeCollection;
    }

    /**
     * Sets a boolean indicating if this theme collection should connect to the whole collection of cards.
     * <p>
     * If this is set to true, the theme collection will automatically include all cards in the whole collection.
     * </p>
     *
     * @param connectToWholeCollection true if this theme collection should connect to the whole collection, false if not
     */
    public void setConnectToWholeCollection(Boolean connectToWholeCollection) {
        this.connectToWholeCollection = connectToWholeCollection;
    }

    /**
     * Saves the theme collection to a file.
     * <p>
     * The file name is the name of the theme collection, followed by ".ytc".
     * The file is written in the following format:
     * <pre>
     * Card1
     * Card2
     * ...
     * Not to add
     * Card3
     * Card4
     * ...
     * Deck1
     * Deck2
     * ...
     * Archetype1
     * Archetype2
     * ...
     * </pre>
     * If the list of cards to not add is empty, the "Not to add" line is not written.
     * If the list of linked decks is empty, the cards are not written.
     * If the list of archetypes is empty, the archetypes are not written.
     * </p>
     * @param savePath the path to which to save the file
     * @throws IOException if there is an error writing to the file
     */
    public void SaveToFile(String savePath) throws IOException {
        Path path = Paths.get(savePath + this.name + ".ytc");
        BufferedWriter writer = Files.newBufferedWriter(path);

        for (CardElement entry : cardsList) {
            writer.write(entry.getCard().toString());
            writer.newLine();
        }

        if (!exceptionsToNotAdd.isEmpty()) {
            writer.write("Not to add");
            writer.newLine();
            for (CardElement card : exceptionsToNotAdd) {
                writer.write(card.toString());
                writer.newLine();
            }
        } else {
            if (!linkedDecks.isEmpty()) {
                for (int i = 0; i < linkedDecks.size(); i++) {
                    for (Deck linkedDeck : linkedDecks.get(i)) {
                        writer.write(linkedDeck.getName());
                        writer.newLine();
                    }
                    if (i < linkedDecks.size() - 1) {
                        writer.write("##");
                        writer.newLine();
                    }
                }

            }
        }

        if (!archetypes.isEmpty()) {
            for (String archetype : archetypes) {
                writer.write(archetype);
                writer.newLine();
            }
        }

        writer.close();
    }

    /*public void AddCard(Card cardToAdd) {
        this.cardsList.add(cardToAdd);
    }*/

    /**
     * Adds a deck to the list of linked decks in a new unit.
     *
     * @param deckToAdd the deck to add to the list of linked decks
     */
    public void AddDeck(Deck deckToAdd) {
        this.linkedDecks.add(new ArrayList<>());
        this.linkedDecks.get(this.linkedDecks.size() - 1).add(deckToAdd);
        //this.linkedDecks.add(deckToAdd);
    }

    /**
     * Adds a deck to the list of linked decks in an existing deck unit.
     *
     * @param deckToAdd the deck to add to the list of linked decks
     */
    public void AddDeckToExistingUnit(Deck deckToAdd, int unitIndex) {
        this.linkedDecks.get(unitIndex).add(deckToAdd);
    }

    public void AddArchetypeCards(String archetype) {
        //TODO parcourir cartes archétype, ajouter celles qui n'y sont pas, si elles ne sont pas présentes dans la liste des cartes à ne pas ajouter
        // ajouter archétype à liste des archétypes
    }

    /**
     * Creates and updates the list of cards for this theme collection by performing
     * intersection operations with linked decks and applying exceptions.
     * <p>
     * This method first performs a difference-intersection operation with artwork exceptions
     * and then performs a difference-intersection with Konami ID exceptions. The resulting
     * list of cards, after applying these operations, becomes the new list of cards for
     * this theme collection.
     * </p>
     */
    public void createCardsList() {
        List<CardElement> linkedDecksList = new ArrayList<>();
        List<CardElement> tempCardsList;
        List<List<CardElement>> tempList;

        tempList = ListDifIntersectArtworkWithExceptions(cardsList, linkedDecksList, "D");
        tempCardsList = tempList.get(0);
        tempList = ListDifferenceIntersection.ListDifIntersectKonamiIdWithExceptions(tempCardsList, linkedDecksList, "D");

        this.cardsList = tempList.get(0);
    }

    /**
     * Retrieves the total number of cards in this theme collection.
     * <p>
     * The returned value is the total number of cards in the list of cards for
     * this theme collection.
     * </p>
     * @return the total number of cards in this theme collection
     */
    public Integer getCardCount() {
        return this.cardsList.size();
    }

    /**
     * Calculates the total price of all cards in this theme collection.
     * <p>
     * This method sums the prices of all cards in the list of cards for this theme collection.
     * </p>
     * @return the total price as a String
     */
    public String getPrice() {
        float price = 0;
        for (CardElement card : this.cardsList) {
            price += Float.parseFloat(card.getCard().getPrice());
        }
        return String.valueOf(price);
    }

    /**
     * Retrieves the list of all cards in this theme collection.
     * <p>
     * The returned list contains all cards that are part of this theme collection.
     * This includes all cards in all linked decks and all cards in the list of cards
     * for this theme collection.
     * </p>
     *
     * @return the list of all cards in this theme collection
     */
    /*public List<CardElement> toList() {
        List<CardElement> returnValue = new ArrayList<>();

        /*for (int i = 0; i < this.getLinkedDecks().size(); i++) {
            returnValue.addAll(this.getLinkedDecks().get(i).toList());
        }

        returnValue.addAll(this.getCardsList());*/
/*
        //First add all cards from linked decks
        for (int i = 0; i < this.getLinkedDecks().size(); i++) {
            for (int j = 0 ; j < this.getLinkedDecks().get(i).size(); j++) {
                for(int k = 0; k < this.getLinkedDecks().get(i).get(j).getCardCount() ; k++) {
                    if(this.linkedDecks.get(i).get(j).toList().get(k).getIsInDeck() == false) {
                        //If there are other decks after this one in the unit, look for this card in the other decks
                        if (j < this.getLinkedDecks().get(i).size() - 1) {
                            boolean hasBeenFound = false;
                            // Search for this cards in all the following decks and set isInDeck to true
                            for(int l = j+1; l < this.getLinkedDecks().get(i).size(); l++) {
                                for(int m = 0; m < this.getLinkedDecks().get(i).get(l).getCardCount(); m++) {
                                    if(this.linkedDecks.get(i).get(l).toList().get(m).getCard().getPrintCode().equals(this.linkedDecks.get(i).get(j).toList().get(k).getCard().getPrintCode())) {
                                        this.linkedDecks.get(i).get(l).toList().get(m).setIsInDeck(true);
                                        hasBeenFound = true;
                                    }
                                }
                            }
                            //If the card has not been found, do the same search again, but looking at the passcode instead of the printcode
                            if(!hasBeenFound) {
                                for(int l = j+1; l < this.getLinkedDecks().get(i).size(); l++) {
                                    for(int m = 0; m < this.getLinkedDecks().get(i).get(l).getCardCount(); m++) {
                                        if(this.linkedDecks.get(i).get(l).toList().get(m).getCard().getPassCode().equals(this.linkedDecks.get(i).get(j).toList().get(k).getCard().getPassCode())) {
                                            this.linkedDecks.get(i).get(l).toList().get(m).setIsInDeck(true);
                                        }
                                    }
                                }
                            }
                        }
                        //Do the same search in the Collection, with cards that have a specific artwork
                        boolean hasBeenFoundCollection = false;
                        for(int l = 0; l < this.getCardsList().size(); l++) {
                            if(this.getCardsList().get(l).getCard().getPrintCode().equals(this.linkedDecks.get(i).get(j).toList().get(k).getCard().getPrintCode()) && this.getCardsList().get(l).getSpecificArtwork() == true) {
                                this.getCardsList().get(l).setIsInDeck(true);
                                hasBeenFoundCollection = true;
                                returnValue.add(this.getCardsList().get(l));
                            }
                        }

                        //If the card has not been found in the collection, do the same search again, but looking at the passcode instead of the printcode
                        if(!hasBeenFoundCollection) {
                            for(int l = 0; l < this.getCardsList().size(); l++) {
                                if(this.getCardsList().get(l).getCard().getPassCode().equals(this.linkedDecks.get(i).get(j).toList().get(k).getCard().getPassCode()) && this.getCardsList().get(l).getSpecificArtwork() == true) {
                                    this.getCardsList().get(l).setIsInDeck(true);
                                    hasBeenFoundCollection = true;
                                    returnValue.add(this.getCardsList().get(l));
                                }
                            }
                        }

                        //If the card has not been found in the collection, do the same search again, but with no secific artwork
                        if(!hasBeenFoundCollection) {
                            for (int l = 0; l < this.getCardsList().size(); l++) {
                                if (this.getCardsList().get(l).getCard().getPrintCode().equals(this.linkedDecks.get(i).get(j).toList().get(k).getCard().getPrintCode())) {
                                    this.getCardsList().get(l).setIsInDeck(true);
                                    hasBeenFoundCollection = true;
                                    returnValue.add(this.linkedDecks.get(i).get(j).toList().get(k));
                                }
                            }
                        }

                        //If the card has not been found in the collection, do the same search again, but looking at the passcode instead of the printcode
                        if(!hasBeenFoundCollection) {
                            for(int l = 0; l < this.getCardsList().size(); l++) {
                                if(this.getCardsList().get(l).getCard().getPassCode().equals(this.linkedDecks.get(i).get(j).toList().get(k).getCard().getPassCode())) {
                                    this.getCardsList().get(l).setIsInDeck(true);
                                    returnValue.add(this.linkedDecks.get(i).get(j).toList().get(k));
                                }
                            }
                        }

                        //Add the card normally if it has not been found in the collection
                        if (!hasBeenFoundCollection) {
                            returnValue.add(this.linkedDecks.get(i).get(j).toList().get(k));
                        }
                    }
                }
                //returnValue.addAll(deck.toList());
            }
        }

        //Then add the cards from the collection. It should not add the isInDeck cards, except if they are also flagged "doNotRemove"
        for (CardElement card : this.getCardsList()) {
            if (!card.getIsInDeck() || card.getDontRemove()) {
                returnValue.add(card);
            }
        }

        return returnValue;
    }*/
    public List<CardElement> toList() {
        List<CardElement> result = new ArrayList<>();

        // Defensive guards in case fields are null
        if (this.linkedDecks == null) this.linkedDecks = new ArrayList<>();
        if (this.cardsList == null) this.cardsList = new ArrayList<>();

        // Build quick lookup maps for collection cards.
        // For each printCode / passCode prefer a collection CardElement that has specificArtwork = true.
        // If none with specificArtwork, use any.
        java.util.Map<String, CardElement> collectionByPrint = new java.util.HashMap<>();
        java.util.Map<String, CardElement> collectionByPass = new java.util.HashMap<>();

        for (CardElement ce : this.cardsList) {
            if (ce == null || ce.getCard() == null) continue;
            String print = ce.getCard().getPrintCode();
            String pass = ce.getCard().getPassCode();

            if (print != null) {
                CardElement existing = collectionByPrint.get(print);
                if (existing == null || (!existing.getSpecificArtwork() && ce.getSpecificArtwork())) {
                    collectionByPrint.put(print, ce);
                }
            }
            if (pass != null) {
                CardElement existing = collectionByPass.get(pass);
                if (existing == null || (!existing.getSpecificArtwork() && ce.getSpecificArtwork())) {
                    collectionByPass.put(pass, ce);
                }
            }
        }

        // Process linkedDecks by unit. Within a unit a card (by print or pass) is added only once.
        for (List<Deck> unit : this.linkedDecks) {
            if (unit == null) continue;
            java.util.Set<String> seenPrintInUnit = new java.util.HashSet<>();
            java.util.Set<String> seenPassInUnit = new java.util.HashSet<>();

            for (Deck deck : unit) {
                if (deck == null) continue;
                List<CardElement> deckList = deck.toList();
                if (deckList == null) continue;

                for (CardElement deckCe : deckList) {
                    if (deckCe == null || deckCe.getCard() == null) continue;
                    String dPrint = deckCe.getCard().getPrintCode();
                    String dPass = deckCe.getCard().getPassCode();

                    boolean alreadySeen = (dPrint != null && seenPrintInUnit.contains(dPrint))
                            || (dPass != null && seenPassInUnit.contains(dPass));
                    if (alreadySeen) continue;

                    // Prefer collection CardElement with specific artwork if present (match print first, then pass)
                    CardElement toAdd = null;
                    if (dPrint != null && collectionByPrint.containsKey(dPrint)) {
                        toAdd = collectionByPrint.get(dPrint);
                    } else if (dPass != null && collectionByPass.containsKey(dPass)) {
                        toAdd = collectionByPass.get(dPass);
                    } else {
                        toAdd = deckCe;
                    }

                    result.add(toAdd);

                    if (dPrint != null) seenPrintInUnit.add(dPrint);
                    if (dPass != null) seenPassInUnit.add(dPass);
                }
            }
        }

        // After decks, add remaining collection cards that were not already added.
        // Always include cards with dontRemove if they were not already included.
        // Use sets of print/pass codes already present in result for quick checks.
        java.util.Set<String> presentPrint = new java.util.HashSet<>();
        java.util.Set<String> presentPass = new java.util.HashSet<>();
        for (CardElement ce : result) {
            if (ce == null || ce.getCard() == null) continue;
            if (ce.getCard().getPrintCode() != null) presentPrint.add(ce.getCard().getPrintCode());
            if (ce.getCard().getPassCode() != null) presentPass.add(ce.getCard().getPassCode());
        }

        for (CardElement ce : this.cardsList) {
            if (ce == null || ce.getCard() == null) continue;
            String print = ce.getCard().getPrintCode();
            String pass = ce.getCard().getPassCode();

            boolean alreadyPresent = (print != null && presentPrint.contains(print))
                    || (pass != null && presentPass.contains(pass));

            if (!alreadyPresent) {
                result.add(ce);
                if (print != null) presentPrint.add(print);
                if (pass != null) presentPass.add(pass);
            } else if (ce.getDontRemove() && !alreadyPresent) {
                // Redundant due to check above; kept for clarity (dontRemove must be present at least once).
                result.add(ce);
            } else if (ce.getDontRemove() && alreadyPresent) {
                // If dontRemove should guarantee presence even if card was represented by a deck entry,
                // ensure at least one entry is the collection element rather than the deck element.
                // If a deck element was used before, replace the first matching entry with this collection element.
                // Find index of first matching result entry and replace it with the collection element.
                for (int i = 0; i < result.size(); i++) {
                    CardElement r = result.get(i);
                    if (r == null || r.getCard() == null) continue;
                    boolean matchByPrint = (print != null && print.equals(r.getCard().getPrintCode()));
                    boolean matchByPass = (pass != null && pass.equals(r.getCard().getPassCode()));
                    if (matchByPrint || matchByPass) {
                        // If it's already the same collection element, nothing to do.
                        if (r == ce) break;
                        result.set(i, ce);
                        break;
                    }
                }
            }
        }

        return result;
    }

}
