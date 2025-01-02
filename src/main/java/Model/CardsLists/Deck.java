package Model.CardsLists;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-physical list of cards
 */
public class Deck {
    private String name;
    private List<CardElement> mainDeck;
    private List<CardElement> extraDeck;
    private List<CardElement> sideDeck;

    public Deck(String filePath) {
        this.mainDeck = new ArrayList<>();
        this.extraDeck = new ArrayList<>();
        this.sideDeck = new ArrayList<>();

        if (filePath.contains(".ydk")) {
            File file = new File(filePath);
            this.name = file.getName().replace(".ydk", "");

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                List<CardElement> currentList = null;
                while ((line = br.readLine()) != null) {
                    if (line.equals("#main")) {
                        currentList = mainDeck;
                    } else if (line.equals("#extra")) {
                        currentList = extraDeck;
                    } else if (line.equals("!side")) {
                        currentList = sideDeck;
                    } else if (!line.contains("#") && currentList != null && !line.isEmpty()) {
                        currentList.add(new CardElement(new Card(line)));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public Deck() {
        this.mainDeck = new ArrayList<>();
        this.extraDeck = new ArrayList<>();
        this.sideDeck = new ArrayList<>();
    }

    /**
     * Retrieves the name of the deck.
     * <p>
     * The name of the deck should be a String that represents the name of the
     * deck.
     * </p>
     *
     * @return the name of the deck as a String
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this deck.
     * <p>
     * The name should be a String that represents the name of the deck.
     * </p>
     *
     * @param name the name to set for this deck
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Retrieves the main deck list.
     * <p>
     * The main deck is the list of cards that are in the main deck of the deck.
     * </p>
     *
     * @return the main deck list as a List of CardElements
     */
    public List<CardElement> getMainDeck() {
        return mainDeck;
    }

    /**
     * Sets the main deck list.
     * <p>
     * This method assigns a new list of CardElements to be the main deck of this deck.
     * </p>
     *
     * @param mainDeck the new list of CardElements to set as the main deck
     */
    public void setMainDeck(List<CardElement> mainDeck) {
        this.mainDeck = mainDeck;
    }

    /**
     * Retrieves the extra deck list.
     * <p>
     * The extra deck is the list of cards that are in the extra deck of the deck.
     * </p>
     *
     * @return the extra deck list as a List of CardElements
     */
    public List<CardElement> getExtraDeck() {
        return extraDeck;
    }

    /**
     * Sets the extra deck list.
     * <p>
     * This method assigns a new list of CardElements to be the extra deck of this deck.
     * </p>
     *
     * @param extraDeck the new list of CardElements to set as the extra deck
     */
    public void setExtraDeck(List<CardElement> extraDeck) {
        this.extraDeck = extraDeck;
    }

    /**
     * Retrieves the side deck list.
     * <p>
     * The side deck is the list of cards that are in the side deck of the deck.
     * </p>
     *
     * @return the side deck list as a List of CardElements
     */
    public List<CardElement> getSideDeck() {
        return sideDeck;
    }

    /**
     * Sets the side deck list.
     * <p>
     * This method assigns a new list of CardElements to be the side deck of this deck.
     * </p>
     *
     * @param sideDeck the new list of CardElements to set as the side deck
     */
    public void setSideDeck(List<CardElement> sideDeck) {
        this.sideDeck = sideDeck;
    }

    /**
     * Saves the deck to the specified file path as a .ydk file.
     * <p>
     * This method will append the deck name to the file path and add the .ydk extension.
     * </p>
     * @param filePath the file path to save the deck to
     */
    public void saveDeck(String filePath) {
        filePath += "/" + this.name + ".ydk";
        try (PrintWriter writer = new PrintWriter(filePath)) {
            writer.println("#created with PotOfGreedManager");
            writeCards(writer, "#main", mainDeck);
            writeCards(writer, "#extra", extraDeck);
            writeCards(writer, "!side", sideDeck);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Writes a list of cards to the given writer as a .ydk file segment.
     * <p>
     * This method will write the given header followed by the passcodes of the cards
     * in the given list. If the list is empty, nothing is written.
     * </p>
     * @param writer     the writer to write to
     * @param header     the header to write before writing the cards
     * @param cards      the list of cards to write
     */
    private void writeCards(PrintWriter writer, String header, List<CardElement> cards) {
        if (!cards.isEmpty()) {
            writer.println(header);
            for (CardElement card : cards) {
                writer.println(card.getCard().getPassCode());
            }
        }
    }

    /**
     * Adds a card to the main deck.
     * <p>
     * This method creates a new CardElement from the given card and adds it to the main deck.
     * </p>
     *
     * @param cardToAdd the Card object to be added to the main deck
     */
    public void AddCardMain(Card cardToAdd) {
        this.mainDeck.add(new CardElement(cardToAdd));
    }

    /**
     * Adds a card to the extra deck.
     * <p>
     * This method creates a new CardElement from the given card and adds it to the extra deck.
     * </p>
     *
     * @param cardToAdd the Card object to be added to the extra deck
     */
    public void AddCardExtra(Card cardToAdd) {
        this.extraDeck.add(new CardElement(cardToAdd));
    }

    /**
     * Adds a card to the side deck.
     * <p>
     * This method creates a new CardElement from the given card and adds it to the side deck.
     * </p>
     *
     * @param cardToAdd the Card object to be added to the side deck
     */
    public void AddCardSide(Card cardToAdd) {
        this.sideDeck.add(new CardElement(cardToAdd));
    }

    /**
     * Adds a CardElement to the main deck.
     * <p>
     * This method directly adds the given CardElement to the main deck.
     * </p>
     *
     * @param cardToAdd the CardElement to be added to the main deck
     */
    public void AddCardMain(CardElement cardToAdd) {
        this.mainDeck.add(cardToAdd);
    }

    /**
     * Adds a CardElement to the extra deck.
     * <p>
     * This method directly adds the given CardElement to the extra deck.
     * </p>
     *
     * @param cardToAdd the CardElement to be added to the extra deck
     */
    public void AddCardExtra(CardElement cardToAdd) {
        this.extraDeck.add(cardToAdd);
    }

    /**
     * Adds a CardElement to the side deck.
     * <p>
     * This method directly adds the given CardElement to the side deck.
     * </p>
     *
     * @param cardToAdd the CardElement to be added to the side deck
     */
    public void AddCardSide(CardElement cardToAdd) {
        this.sideDeck.add(cardToAdd);
    }

    /**
     * Adds a card to the main deck with the given parameters.
     * <p>
     * The parameters string should contain the following characters to set the corresponding
     * properties of the CardElement:
     * <ul>
     *     <li>'*': the card has a specific artwork</li>
     *     <li>'O': the card is owned</li>
     *     <li>'D': the card is in the deck</li>
     *     <li]+'+': the card should not be removed even if it is present in a Deck from the ThemeCollection deck</li>
     * </ul>
     * </p>
     * @param cardToAdd the Card object to be added to the main deck
     * @param parameters the string of parameters to set on the CardElement
     */
    public void AddCardMain(Card cardToAdd, String parameters) {
        boolean specificArtwork = false;
        boolean isOwned = false;
        boolean isInDeck = false;
        boolean dontRemove = false;
        if (parameters.contains("*")) {
            specificArtwork = true;
        }
        if (parameters.contains("O")) {
            isOwned = true;
        }
        if (parameters.contains("D")) {
            isInDeck = true;
        }
        if (parameters.contains("+")) {
            dontRemove = true;
        }
        this.mainDeck.add(new CardElement(cardToAdd, specificArtwork, isOwned, dontRemove, isInDeck));
    }

    /**
     * Adds a card to the extra deck with the given parameters.
     * <p>
     * The parameters string should contain the following characters to set the corresponding
     * properties of the CardElement:
     * <ul>
     *     <li>'*': the card has a specific artwork</li>
     *     <li>'O': the card is owned</li>
     *     <li>'D': the card is in the deck</li>
     *     <li>'+': the card should not be removed even if it is present in a Deck from the ThemeCollection deck</li>
     * </ul>
     * </p>
     * @param cardToAdd the Card object to be added to the extra deck
     * @param parameters the string of parameters to set on the CardElement
     */
    public void AddCardExtra(Card cardToAdd, String parameters) {
        boolean specificArtwork = false;
        boolean isOwned = false;
        boolean isInDeck = false;
        boolean dontRemove = false;
        if (parameters.contains("*")) {
            specificArtwork = true;
        }
        if (parameters.contains("O")) {
            isOwned = true;
        }
        if (parameters.contains("D")) {
            isInDeck = true;
        }
        if (parameters.contains("+")) {
            dontRemove = true;
        }
        this.extraDeck.add(new CardElement(cardToAdd, specificArtwork, isOwned, dontRemove, isInDeck));
    }

    /**
     * Adds a card to the side deck with the given parameters.
     * <p>
     * The parameters string should contain the following characters to set the corresponding
     * properties of the CardElement:
     * <ul>
     *     <li>'*': the card has a specific artwork</li>
     *     <li>'O': the card is owned</li>
     *     <li>'D': the card is in the deck</li>
     *     <li>'+': the card should not be removed even if it is present in a Deck from the ThemeCollection deck</li>
     * </ul>
     * </p>
     * @param cardToAdd the Card object to be added to the side deck
     * @param parameters the string of parameters to set on the CardElement
     */
    public void AddCardSide(Card cardToAdd, String parameters) {
        boolean specificArtwork = false;
        boolean isOwned = false;
        boolean isInDeck = false;
        boolean dontRemove = false;
        if (parameters.contains("*")) {
            specificArtwork = true;
        }
        if (parameters.contains("O")) {
            isOwned = true;
        }
        if (parameters.contains("D")) {
            isInDeck = true;
        }
        if (parameters.contains("+")) {
            dontRemove = true;
        }
        this.sideDeck.add(new CardElement(cardToAdd, specificArtwork, isOwned, dontRemove, isInDeck));
    }

    /**
     * Returns the total number of cards in the deck.
     * <p>
     * This method returns the total number of cards in the main deck, extra deck, and side deck.
     * </p>
     * @return The total number of cards as an Integer.
     */
    public Integer getCardCount() {
        int returnValue = 0;
        returnValue += this.getMainDeck().size();
        returnValue += this.getExtraDeck().size();
        returnValue += this.getSideDeck().size();
        return returnValue;
    }

    /**
     * Calculates the total price of all cards in the deck.
     * <p>
     * This method sums the total price of all cards in the main deck, extra deck, and side deck.
     * </p>
     * @return The total price as a String.
     */
    public String getPrice() {
        float returnValue = 0;
        returnValue += Float.parseFloat(this.getMainDeckPrice());
        returnValue += Float.parseFloat(this.getExtraDeckPrice());
        returnValue += Float.parseFloat(this.getSideDeckPrice());
        return String.valueOf(returnValue);
    }

    /**
     * Calculates the total price of all cards in the main deck.
     * <p>
     * This method sums the total price of all cards in the main deck.
     * </p>
     * @return The total price as a String.
     */
    public String getMainDeckPrice() {
        float returnValue = 0;

        for (int i = 0; i < this.getMainDeck().size(); i++) {
            if (this.getMainDeck().get(i) != null) {
                if (this.getMainDeck().get(i).getPrice() != null) {
                    returnValue += Float.parseFloat(this.getMainDeck().get(i).getPrice());
                }
            }
        }

        return String.valueOf(returnValue);
    }

    /**
     * Calculates the total price of all cards in the extra deck.
     * <p>
     * This method sums the total price of all cards in the extra deck.
     * </p>
     * @return The total price as a String.
     */
    public String getExtraDeckPrice() {
        float returnValue = 0;

        for (int i = 0; i < this.getExtraDeck().size(); i++) {
            if (this.getExtraDeck().get(i) != null) {
                if (this.getExtraDeck().get(i).getPrice() != null) {
                    returnValue += Float.parseFloat(this.getExtraDeck().get(i).getPrice());
                }
            }
        }

        return String.valueOf(returnValue);
    }

    /**
     * Calculates the total price of all cards in the side deck.
     * <p>
     * This method sums the total price of all cards in the side deck.
     * </p>
     * @return The total price as a String.
     */
    public String getSideDeckPrice() {
        float returnValue = 0;

        for (int i = 0; i < this.getSideDeck().size(); i++) {
            if (this.getSideDeck().get(i) != null) {
                if (this.getSideDeck().get(i).getPrice() != null) {
                    returnValue += Float.parseFloat(this.getSideDeck().get(i).getPrice());
                }
            }
        }

        return String.valueOf(returnValue);
    }

    /**
     * Returns a list containing all the cards in this deck.
     * <p>
     * The list contains all the cards in the main deck, extra deck, and side deck.
     * </p>
     *
     * @return A list of CardElement objects.
     */
    public List<CardElement> toList() {
        List<CardElement> returnValue = new ArrayList<>();

        returnValue.addAll(this.getMainDeck());
        returnValue.addAll(this.getExtraDeck());
        returnValue.addAll(this.getSideDeck());

        return returnValue;
    }
}
