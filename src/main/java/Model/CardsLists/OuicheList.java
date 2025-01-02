package Model.CardsLists;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static Model.CardsLists.ListDifferenceIntersection.*;

public class OuicheList {
    private static OwnedCardsCollection myCardsCollection;
    private static DecksAndCollectionsList decksList;

    private static List<CardElement> maOuicheList;
    private static List<CardElement> unusedCards;
    private static List<CardElement> listsIntersection;
    private static DecksAndCollectionsList detailedOuicheList;

    private static List<CardElement> thirdPartyList;
    private static List<CardElement> thirdPartyCardsINeedList;

    /**
     * Gets the value of the {@link #maOuicheList} field.
     * <p>
     *
     * @return the value of the {@link #maOuicheList} field
     */
    public static List<CardElement> getMaOuicheList() {
        //TODO if maOuicheList == null => createOuicheList
        return maOuicheList;
    }

    /**
     * Sets the value of the {@link #maOuicheList} field.
     *
     * @param maOuicheList the new value of the {@link #maOuicheList} field
     */
    public static void setMaOuicheList(List<CardElement> maOuicheList) {
        OuicheList.maOuicheList = maOuicheList;
    }

    /**
     * Gets the list of cards that are not in any deck or collection.
     * @return the list of unused cards
     */
    public static List<CardElement> getUnusedCards() {
        return unusedCards;
    }

    /**
     * Sets the list of cards that are not in any deck or collection.
     * <p>
     * @param unusedCards the new list of unused cards
     */
    public static void setUnusedCards(List<CardElement> unusedCards) {
        OuicheList.unusedCards = unusedCards;
    }

    /**
     * Gets the intersection of all lists of cards, i.e. the cards that are common to all lists.
     * <p>
     * @return the intersection of all lists of cards
     */
    public static List<CardElement> getListsIntersection() {
        return listsIntersection;
    }

    /**
     * Sets the intersection of all lists of cards, i.e. the cards that are common to all lists.
     * <p>
     * @param listsIntersection the new intersection of all lists of cards
     */
    public static void setListsIntersection(List<CardElement> listsIntersection) {
        OuicheList.listsIntersection = listsIntersection;
    }

    /**
     * Gets the detailed ouiche list, which is the list of cards in the user's collection,
     * as well as the cards that are in the decks and collections lists.
     * <p>
     * @return the detailed ouiche list
     */
    public static DecksAndCollectionsList getDetailedOuicheList() {
        return detailedOuicheList;
    }

    /**
     * Sets the detailed ouiche list, which is the list of cards in the user's collection,
     * as well as the cards that are in the decks and collections lists.
     * <p>
     * @param detailedOuicheList the new detailed ouiche list
     */
    public static void setDetailedOuicheList(DecksAndCollectionsList detailedOuicheList) {
        OuicheList.detailedOuicheList = detailedOuicheList;
    }

    /**
     * Gets the list of cards in the third-party list.
     * <p>
     * @return the list of cards in the third-party list
     */
    public static List<CardElement> getThirdPartyList() {
        return thirdPartyList;
    }

    /**
     * Sets the list of cards in the third-party list.
     * <p>
     * @param thirdPartyList the new list of cards in the third-party list
     */
    public static void setThirdPartyList(List<CardElement> thirdPartyList) {
        OuicheList.thirdPartyList = thirdPartyList;
    }

    /**
     * Gets the list of cards in the third-party list that are not in the user's collection and
     * are needed to complete the decks and collections.
     *
     * @return the list of cards in the third-party list that are not in the user's collection and
     *         are needed to complete the decks and collections
     */
    public static List<CardElement> getThirdPartyCardsINeedList() {
        return thirdPartyCardsINeedList;
    }

    /**
     * Sets the list of cards in the third-party list that are not in the user's collection and
     * are needed to complete the decks and collections.
     * <p>
     * @param thirdPartyCardsINeedList the new list of cards in the third-party list that are not in the user's collection and
     *                                 are needed to complete the decks and collections
     */
    public static void setThirdPartyCardsINeedList(List<CardElement> thirdPartyCardsINeedList) {
        OuicheList.thirdPartyCardsINeedList = thirdPartyCardsINeedList;
    }

    /**
     * Retrieves the user's collection of owned cards.
     *
     * @return the OwnedCardsCollection representing the user's card collection
     */
    public static OwnedCardsCollection getMyCardsCollection() {
        return myCardsCollection;
    }

    /**
     * Sets the user's collection of owned cards.
     * <p>
     * @param myCardsCollection the new OwnedCardsCollection representing the user's card collection
     */
    public static void setMyCardsCollection(OwnedCardsCollection myCardsCollection) {
        OuicheList.myCardsCollection = myCardsCollection;
    }

    /**
     * Retrieves the list of all decks in the user's collection.
     *
     * @return the DecksAndCollectionsList representing the user's decks
     */
    public static DecksAndCollectionsList getDecksList() {
        return decksList;
    }

    /**
     * Sets the list of decks and collections in the OuicheList.
     * <p>
     * This method assigns the provided {@link DecksAndCollectionsList} to the
     * {@link #decksList} field of the OuicheList.
     *
     * @param decksList the {@link DecksAndCollectionsList} to set
     */
    public static void setDecksList(DecksAndCollectionsList decksList) {
        OuicheList.decksList = decksList;
    }

    /**
     * Generates the OuicheList by calling {@link #CreateDetailedOuicheList(OwnedCardsCollection, DecksAndCollectionsList)}
     * and generates several sublists of the OuicheList based on the different types of cards.
     *
     * @param ownedCardsCollection the OwnedCardsCollection representing the user's card collection
     * @param decksList the DecksAndCollectionsList representing the user's decks
     *
     * @return the OuicheList
     */
    public static List<CardElement> CreateOuicheList(OwnedCardsCollection ownedCardsCollection, DecksAndCollectionsList decksList) {
        maOuicheList = new ArrayList<>();
        listsIntersection = new ArrayList<>();

        //Add all wished elements from decks and collections
        maOuicheList = decksList.toList();

        //Create list of owned cards
        unusedCards = ownedCardsCollection.toList();

        List<List<CardElement>> tempList = ListDifIntersectPrintcode(maOuicheList, unusedCards);
        maOuicheList = tempList.get(0);
        unusedCards = tempList.get(1);

        tempList = ListDifIntersectKonamiId(maOuicheList, unusedCards);
        maOuicheList = tempList.get(0);
        unusedCards = tempList.get(1);

        return maOuicheList;
    }

    /**
     * Creates a detailed OuicheList by making the difference between owned cards and the given decks and collections.
     *
     * <p>This method initializes a new {@link DecksAndCollectionsList} for the detailed OuicheList and
     * populates it with cards from the provided decksList. It then removes all cards that are already
     * owned from this list by comparing with the ownedCardsCollection. The comparisons are performed
     * using artwork and Konami Ids, with certain exceptions considered.
     *
     * <p>The method processes both collections and decks within the decksList, iterating over each
     * main, extra, and side deck. The owned cards are updated after each comparison operation to
     * ensure accurate removal of duplicates.
     *
     * @param ownedCardsCollection the OwnedCardsCollection representing the user's card collection
     * @param decksList            the DecksAndCollectionsList representing the user's decks
     * @return the created detailed OuicheList
     */
    public static DecksAndCollectionsList CreateDetailedOuicheList(OwnedCardsCollection ownedCardsCollection, DecksAndCollectionsList decksList) {
        detailedOuicheList = new DecksAndCollectionsList();
        listsIntersection = new ArrayList<>();

        //Add all wished elements from decks and collections
        detailedOuicheList = decksList;

        //Create list of owned cards
        unusedCards = ownedCardsCollection.toList();

        //Remove all cards that are already owned (present in ownedCardsCollection)
        if (detailedOuicheList.getCollections() != null) {
            for (int i = 0; i < detailedOuicheList.getCollections().size(); i++) {
                for (int j = 0; j < detailedOuicheList.getCollections().get(i).getLinkedDecks().size(); j++) {
                    //Decks
                    List<List<CardElement>> tempList = ListDifIntersectArtworkWithExceptions(detailedOuicheList.getCollections().get(i).getLinkedDecks().get(j).getMainDeck(), unusedCards, "O");
                    detailedOuicheList.getCollections().get(i).getLinkedDecks().get(j).setMainDeck(tempList.get(0));
                    unusedCards = new ArrayList<>(tempList.get(1));
                    tempList = ListDifIntersectArtworkWithExceptions(detailedOuicheList.getCollections().get(i).getLinkedDecks().get(j).getExtraDeck(), unusedCards, "O");
                    detailedOuicheList.getCollections().get(i).getLinkedDecks().get(j).setExtraDeck(tempList.get(0));
                    unusedCards = new ArrayList<>(tempList.get(1));
                    tempList = ListDifIntersectArtworkWithExceptions(detailedOuicheList.getCollections().get(i).getLinkedDecks().get(j).getSideDeck(), unusedCards, "O");
                    detailedOuicheList.getCollections().get(i).getLinkedDecks().get(j).setSideDeck(tempList.get(0));
                    unusedCards = new ArrayList<>(tempList.get(1));
                }
                //Collection
                List<List<CardElement>> tempList = ListDifIntersectArtworkWithExceptions(detailedOuicheList.getCollections().get(i).getCardsList(), unusedCards, "O");
                detailedOuicheList.getCollections().get(i).setCardsList(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
            }
        }

        if (detailedOuicheList.getDecks() != null) {
            for (int i = 0; i < detailedOuicheList.getDecks().size(); i++) {
                List<List<CardElement>> tempList = ListDifIntersectArtworkWithExceptions(detailedOuicheList.getDecks().get(i).getMainDeck(), unusedCards, "O");
                detailedOuicheList.getDecks().get(i).setMainDeck(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
                tempList = ListDifIntersectArtworkWithExceptions(detailedOuicheList.getDecks().get(i).getExtraDeck(), unusedCards, "O");
                detailedOuicheList.getDecks().get(i).setExtraDeck(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
                tempList = ListDifIntersectArtworkWithExceptions(detailedOuicheList.getDecks().get(i).getSideDeck(), unusedCards, "O");
                detailedOuicheList.getDecks().get(i).setSideDeck(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
            }
        }

        if (detailedOuicheList.getCollections() != null) {
            for (int i = 0; i < detailedOuicheList.getCollections().size(); i++) {
                for (int j = 0; j < detailedOuicheList.getCollections().get(i).getLinkedDecks().size(); j++) {
                    //Decks
                    List<List<CardElement>> tempList = ListDifIntersectKonamiIdWithExceptions(detailedOuicheList.getCollections().get(i).getLinkedDecks().get(j).getMainDeck(), unusedCards, "O");
                    detailedOuicheList.getCollections().get(i).getLinkedDecks().get(j).setMainDeck(tempList.get(0));
                    unusedCards = new ArrayList<>(tempList.get(1));
                    tempList = ListDifIntersectKonamiIdWithExceptions(detailedOuicheList.getCollections().get(i).getLinkedDecks().get(j).getExtraDeck(), unusedCards, "O");
                    detailedOuicheList.getCollections().get(i).getLinkedDecks().get(j).setExtraDeck(tempList.get(0));
                    unusedCards = new ArrayList<>(tempList.get(1));
                    tempList = ListDifIntersectKonamiIdWithExceptions(detailedOuicheList.getCollections().get(i).getLinkedDecks().get(j).getSideDeck(), unusedCards, "O");
                    detailedOuicheList.getCollections().get(i).getLinkedDecks().get(j).setSideDeck(tempList.get(0));
                    unusedCards = new ArrayList<>(tempList.get(1));
                }
                //Collection
                List<List<CardElement>> tempList = ListDifIntersectKonamiIdWithExceptions(detailedOuicheList.getCollections().get(i).getCardsList(), unusedCards, "O");
                detailedOuicheList.getCollections().get(i).setCardsList(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
            }
        }


        if (detailedOuicheList.getDecks() != null) {
            for (int i = 0; i < detailedOuicheList.getDecks().size(); i++) {
                List<List<CardElement>> tempList = ListDifIntersectKonamiIdWithExceptions(detailedOuicheList.getDecks().get(i).getMainDeck(), unusedCards, "O");
                detailedOuicheList.getDecks().get(i).setMainDeck(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
                tempList = ListDifIntersectKonamiIdWithExceptions(detailedOuicheList.getDecks().get(i).getExtraDeck(), unusedCards, "O");
                detailedOuicheList.getDecks().get(i).setExtraDeck(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
                tempList = ListDifIntersectKonamiIdWithExceptions(detailedOuicheList.getDecks().get(i).getSideDeck(), unusedCards, "O");
                detailedOuicheList.getDecks().get(i).setSideDeck(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
            }
        }

        return detailedOuicheList;
    }


    /**
     * Import the detailed ouiche list from a file.
     * <p>
     * The file is expected to be a text file with the following structure:
     * <ul>
     *     <li>Lines starting with "===" are considered to be title lines, and
     *         are used to separate collections and decks. The text after the
     *         "===" is used as the name of the collection or deck.</li>
     *     <li>Lines starting with "---" are considered to be collection or deck
     *         lines. The text after the "---" is used as the name of the
     *         collection or deck. If the line is "---Collection", the
     *         following lines are considered to be part of a collection. If the
     *         line is "---Decks", the following lines are considered to be part
     *         of a deck.</li>
     *     <li>Lines that do not start with "===" or "---" are considered to be
     *         card lines. The line is expected to contain the name of the card,
     *         and optionally the number of copies of the card that are owned.
     *         If the number of copies is not specified, it is assumed to be 0.
     *         If the number of copies is negative, it is assumed to be 1.
     *         If the card is not in the collection, it is added to the collection
     *         and to the maOuicheList.
     *     </li>
     * </ul>
     * <p>
     * If the file is not a valid detailed ouiche list file, an exception is
     * thrown.
     * @param filePath the path to the file to import
     * @throws Exception if there is an error importing the file
     */
    public static void importOuicheList(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path);

        detailedOuicheList = new DecksAndCollectionsList();
        maOuicheList = new ArrayList<>();

        ThemeCollection currentCollection = null;
        Deck currentDeck = null;
        boolean isDecksSection = false;

        for (String line : lines) {
            if (line.startsWith("===")) {
                if (currentCollection != null) {
                    detailedOuicheList.addCollection(currentCollection);
                }
                currentCollection = new ThemeCollection();
                currentCollection.setName(line.substring(3).trim());
                isDecksSection = false;
            } else if (line.startsWith("---")) {
                if (line.equals("---Collection")) {
                    currentDeck = null;
                } else {
                    currentDeck = new Deck();
                    currentDeck.setName(line.substring(3).trim());
                    if (currentCollection != null) {
                        currentCollection.AddDeck(currentDeck);
                    } else {
                        detailedOuicheList.addDeck(currentDeck);
                    }
                }
            } else if (line.startsWith("===") && line.contains("Decks")) {
                isDecksSection = true;
            } else {
                CardElement card = new CardElement(line.trim());
                if (!card.getOwned()) {
                    if (isDecksSection) {
                        if (currentDeck != null) {
                            currentDeck.AddCardMain(card);
                            maOuicheList.add(card);
                        }
                    } else {
                        if (currentDeck != null) {
                            currentDeck.AddCardMain(card);
                            maOuicheList.add(card);
                        } else if (currentCollection != null) {
                            currentCollection.getCardsList().add(card);
                            maOuicheList.add(card);
                        }
                    }
                }
            }
        }

        if (currentCollection != null) {
            detailedOuicheList.addCollection(currentCollection);
        }
    }

    /**
     * Import the third-party list from the given file.
     *
     * This method reads a file line by line and adds each line to the third-party list
     * as a CardElement.
     *
     * The following lines are ignored:
     * <ul>
     * <li>Lines that start with "="</li>
     * <li>Lines that start with "-"</li>
     * <li>Lines that start with "#"</li>
     * <li>Empty lines</li>
     * </ul>
     *
     * @param filePath the path to the file to import
     * @throws Exception if there is an error importing the file
     */
    public static void importThirdPartyList(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path);

        thirdPartyList = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("=") && !line.startsWith("-") && !line.startsWith("#") && !line.isEmpty()) {
                thirdPartyList.add(new CardElement(line.trim()));
            }
        }
    }

    /**
     * Generate a list of cards in the third-party list that are not in the
     * DecksAndCollectionList, but are needed for the decks in the OuicheList.
     * <p>
     * The list is generated by first computing the difference between the
     * third-party list and the OuicheList using the print code, and then
     * computing the difference between the two lists using the Konami ID.
     * <p>
     * The resulting list is stored in the {@link #thirdPartyCardsINeedList} field.
     */
    public static void generateThirdPartyCardsINeedList() {
        thirdPartyCardsINeedList = new ArrayList<>();
        List<CardElement> maOuicheListTemp;
        List<CardElement> thirdPartyListTemp;

        List<List<CardElement>> tempList = ListDifIntersectPrintcode(thirdPartyList, maOuicheList);
        thirdPartyListTemp = tempList.get(0);
        maOuicheListTemp = tempList.get(1);
        thirdPartyCardsINeedList = tempList.get(2);

        tempList = ListDifIntersectKonamiId(thirdPartyListTemp, maOuicheListTemp);
        thirdPartyCardsINeedList.addAll(tempList.get(2));
    }

    /**
     * Save the detailed ouiche list to a file.
     * <p>
     * The file is written in the following format:
     * <pre>
     * ===ThemeCollection1=========
     * ---Deck1------------
     * Card1
     * Card2
     * ...
     * ---Deck2------------
     * Card3
     * Card4
     * ...
     * ...
     * ===Decks==============
     * ---Deck1-------
     * Card5
     * Card6
     * ...
     * ---Deck2-------
     * Card7
     * Card8
     * ...
     * </pre>
     * <p>
     * The file is overwritten if it already exists.
     * <p>
     * If there is an error writing to the file, an IOException is thrown.
     * @param filePath the path to the file to save
     * @throws IOException if there is an error writing to the file
     */
    public static void ouicheListSave(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            boolean newFile = file.createNewFile();
            if (!newFile) {
                throw new IOException("Failed to create file: " + filePath);
            }
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
        for (ThemeCollection collection : detailedOuicheList.getCollections()) {
            writer.write("===" + collection.getName() + "========");
            writer.newLine();
            for (Deck deck : collection.getLinkedDecks()) {
                writer.write("---" + deck.getName() + "------------");
                writer.newLine();
                for (CardElement card : deck.toList()) {
                    //writer.write(card.getCard().getPasscode());
                    writer.write(card.toString());
                    writer.newLine();
                }
            }
            if (!collection.getCardsList().isEmpty()) {
                writer.write("---Collection----------");
                writer.newLine();
                for (CardElement card : collection.getCardsList()) {
                    //writer.write(card.getCard().getPasscode());
                    writer.write(card.toString());
                    writer.newLine();
                }
            }
        }

        writer.write("===Decks===============");
        writer.newLine();
        for (Deck deck : detailedOuicheList.getDecks()) {
            writer.write("---" + deck.getName() + "-------");
            writer.newLine();
            for (CardElement card : deck.toList()) {
                if (card.getCard().getPassCode() != null) {
                    //writer.write(card.getCard().getPasscode());
                    writer.write(card.toString());
                } else if (card.getCard().getPrintCode() != null) {
                    //writer.write(card.getCard().getPasscode());
                    writer.write(card.toString());
                } else {
                    System.out.println("Error : no passcode nor printcode for this card");
                }

                writer.newLine();
            }
        }

        writer.close();
    }

    /**
     * Saves the unused cards to a file.
     * <p>
     * The file is overwritten if it already exists.
     * <p>
     * If there is an error writing to the file, an IOException is thrown.
     * @param filePath the path to the file to save
     * @throws IOException if there is an error writing to the file
     */
    public static void unusedCardsSave(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            boolean newFile = file.createNewFile();
            if (!newFile) {
                throw new IOException("Failed to create file: " + filePath);
            }
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));

        for (CardElement card : unusedCards) {
            //writer.write(card.getCard().getPasscode());
            writer.write(card.toString());
            writer.newLine();
        }

        writer.close();
    }

    /**
     * Saves the third party cards I need to a file.
     * <p>
     * The file is overwritten if it already exists.
     * <p>
     * If there is an error writing to the file, an IOException is thrown.
     * @param directoryPath the path of the directory to save the file to
     * @param filePath the name of the file to save
     * @throws IOException if there is an error writing to the file
     */
    public static void thirdPartyCardsINeedListSave(String directoryPath, String filePath) throws IOException {
        Files.createDirectories(Paths.get(directoryPath));
        File file = new File(directoryPath + filePath);
        if (!file.exists()) {
            boolean newFile = file.createNewFile();
            if (!newFile) {
                throw new IOException("Failed to create file: " + directoryPath + filePath);
            }
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(directoryPath + filePath));

        for (CardElement card : thirdPartyCardsINeedList) {
            //writer.write(card.getCard().getPasscode());
            writer.write(card.toString());
            writer.newLine();
        }

        writer.close();
    }

    /**
     * Returns a string representation of this OuicheList.
     * <p>
     * The representation is a newline-separated list of the string
     * representations of all cards in this OuicheList.
     * @return a string representation of this OuicheList
     */
    public String toString() {
        String returnString = "";

        for (CardElement cardElement : maOuicheList) {
            returnString = returnString.concat(cardElement.toString());
        }

        return returnString;
    }
}
