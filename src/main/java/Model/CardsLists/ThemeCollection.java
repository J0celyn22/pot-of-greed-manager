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

    public List<Deck> linkedDecks;

    public List<String> archetypes;

    public Boolean connectToWholeCollection;

    public ThemeCollection(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        this.name = path.getFileName().toString().replaceFirst("[.][^.]+$", "");

        List<String> lines = Files.readAllLines(path);

        cardsList = new ArrayList<>();

        for (String line : lines) {
            if (line.equals("#Not to add")) break;

            cardsList.add(new CardElement(line));
        }

        int index = lines.indexOf("#Not to add");
        if (index != -1) {
            for (int i = index + 1; i < lines.size(); i++) {
                if (lines.get(i).equals("#Not to add")) break;
                if (exceptionsToNotAdd != null) {
                    exceptionsToNotAdd.add(new CardElement(lines.get(i)));
                }
            }
        }

        index = lines.indexOf("#Link to whole collection");
        connectToWholeCollection = index != -1;

        if (!connectToWholeCollection) {
            index = lines.indexOf("#Linked decks");
            if (index != -1) {
                for (int i = index + 1; i < lines.size(); i++) {
                    if (lines.get(i).equals("#Archetypes")) break;
                    String deckPath = path.getParent().toString() + lines.get(i) + ".ydk";
                    linkedDecks.add(new Deck(deckPath));
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
    public List<Deck> getLinkedDecks() {
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
    public void setLinkedDecks(List<Deck> linkedDecks) {
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
                for (Deck linkedDeck : linkedDecks) {
                    writer.write(linkedDeck.getName());
                    writer.newLine();
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
     * Adds a deck to the list of linked decks.
     *
     * @param deckToAdd the deck to add to the list of linked decks
     */
    public void AddDeck(Deck deckToAdd) {
        this.linkedDecks.add(deckToAdd);
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
    public List<CardElement> toList() {
        List<CardElement> returnValue = new ArrayList<>();

        for (int i = 0; i < this.getLinkedDecks().size(); i++) {
            returnValue.addAll(this.getLinkedDecks().get(i).toList());
        }

        returnValue.addAll(this.getCardsList());

        return returnValue;
    }
}
