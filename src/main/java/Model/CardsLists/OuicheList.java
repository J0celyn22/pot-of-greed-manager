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

    public static List<CardElement> getMaOuicheList() {
        return maOuicheList;
    }

    public static void setMaOuicheList(List<CardElement> maOuicheList) {
        OuicheList.maOuicheList = maOuicheList;
    }

    public static List<CardElement> getUnusedCards() {
        return unusedCards;
    }

    public static void setUnusedCards(List<CardElement> unusedCards) {
        OuicheList.unusedCards = unusedCards;
    }

    public static List<CardElement> getListsIntersection() {
        return listsIntersection;
    }

    public static void setListsIntersection(List<CardElement> listsIntersection) {
        OuicheList.listsIntersection = listsIntersection;
    }

    public static DecksAndCollectionsList getDetailedOuicheList() {
        return detailedOuicheList;
    }

    public static void setDetailedOuicheList(DecksAndCollectionsList detailedOuicheList) {
        OuicheList.detailedOuicheList = detailedOuicheList;
    }

    public static List<CardElement> getThirdPartyList() {
        return thirdPartyList;
    }

    public static void setThirdPartyList(List<CardElement> thirdPartyList) {
        OuicheList.thirdPartyList = thirdPartyList;
    }

    public static List<CardElement> getThirdPartyCardsINeedList() {
        return thirdPartyCardsINeedList;
    }

    public static void setThirdPartyCardsINeedList(List<CardElement> thirdPartyCardsINeedList) {
        OuicheList.thirdPartyCardsINeedList = thirdPartyCardsINeedList;
    }

    public static OwnedCardsCollection getMyCardsCollection() {
        return myCardsCollection;
    }

    public static void setMyCardsCollection(OwnedCardsCollection myCardsCollection) {
        OuicheList.myCardsCollection = myCardsCollection;
    }

    public static DecksAndCollectionsList getDecksList() {
        return decksList;
    }

    public static void setDecksList(DecksAndCollectionsList decksList) {
        OuicheList.decksList = decksList;
    }

    //TODO List<Card> => List<List<Card>> => return {maOuicheList, unusedCards, listsIntersection} + modif => Map cf. ThemeCollection
    public static List<CardElement> CreateOuicheList(OwnedCardsCollection ownedCardsCollection, DecksAndCollectionsList decksList) {
        maOuicheList = new ArrayList<>();
        listsIntersection = new ArrayList<>();

        //Add all wished elements from decks and collections
        /*for (int i = 0; i < decksList.getDecks().size(); i++) {
            maOuicheList.addAll(decksList.getDecks().get(i).getMainDeck());
            maOuicheList.addAll(decksList.getDecks().get(i).getExtraDeck());
            maOuicheList.addAll(decksList.getDecks().get(i).getSideDeck());
        }*/
        maOuicheList = decksList.toList();

        //Create list of owned cards
        /*unusedCards = new ArrayList<>();
        for (int i = 0; i < ownedCardsCollection.getOwnedCollection().size(); i++) {
            for (int j = 0; j < ownedCardsCollection.getOwnedCollection().get(i).getContent().size(); j++) {
                for (int k = 0; k < ownedCardsCollection.getOwnedCollection().get(i).getContent().get(j).cardList.size(); k++) {
                    unusedCards.add(ownedCardsCollection.getOwnedCollection().get(i).getContent().get(j).cardList.get(k));
                }
            }
        }*/
        unusedCards = ownedCardsCollection.toList();

        /*//Remove all cards that are already owned (present in ownedCardsCollection)
        Iterator<Card> maOuicheListIterator = maOuicheList.iterator();
        while (maOuicheListIterator.hasNext()) {
            Card maOuicheCard = maOuicheListIterator.next();
            boolean isRemoved = false;
            if (maOuicheCard.getPrintCode() != null && maOuicheCard.getKonamiId() != null) {
                Iterator<Card> ownedCardsIterator = unusedCards.iterator();
                while (ownedCardsIterator.hasNext()) {
                    Card ownedCard = ownedCardsIterator.next();
                    if(ownedCard.getKonamiId() != null) {
                        if (maOuicheCard.getPrintCode().equals(ownedCard.getPrintCode())) {
                            maOuicheListIterator.remove();
                            ownedCardsIterator.remove();
                            listsIntersection.add(ownedCard);
                            isRemoved = true;
                            break;  // Exit the inner loop
                        }
                    }
                }
            }
            if (isRemoved) continue;  // Skip to the next iteration
        }*/
        List<List<CardElement>> tempList = ListDifIntersectPrintcode(maOuicheList, unusedCards);
        maOuicheList = tempList.get(0);
        unusedCards = tempList.get(1);

        /*Iterator<Card> maOuicheListIterator2 = maOuicheList.iterator();
        while (maOuicheListIterator2.hasNext()) {
            Card maOuicheCard = maOuicheListIterator2.next();
            if (maOuicheCard.getKonamiId() != null) {
                boolean isRemoved = false;
                Iterator<Card> ownedCardsIterator = unusedCards.iterator();
                while (ownedCardsIterator.hasNext()) {
                    Card ownedCard = ownedCardsIterator.next();
                    if (maOuicheCard.getKonamiId().equals(ownedCard.getCardId())) {
                        maOuicheListIterator2.remove();
                        ownedCardsIterator.remove();
                        isRemoved = true;
                        break;  // Exit the inner loop
                    }
                }
                if (isRemoved) continue;  // Skip to the next iteration
            }
        }*/
        tempList = ListDifIntersectKonamiId(maOuicheList, unusedCards);
        maOuicheList = tempList.get(0);
        unusedCards = tempList.get(1);

        return maOuicheList;
    }

    public static DecksAndCollectionsList CreateDetailedOuicheList(OwnedCardsCollection ownedCardsCollection, DecksAndCollectionsList decksList) throws Exception {
        detailedOuicheList = new DecksAndCollectionsList();
        listsIntersection = new ArrayList<>();

        //Add all wished elements from decks and collections
        /*maOuicheList = decksList.toList();
        for (int i = 0; i < decksList.getCollections().size(); i++) {
            detailedOuicheList.addCollection(new ThemeCollection());
        }
        for (int i = 0; i < decksList.getDecks().size(); i++) {
            detailedOuicheList.addDeck(new Deck());

        }*/
        detailedOuicheList = decksList;

        //Create list of owned cards
        unusedCards = ownedCardsCollection.toList();

        //Remove all cards that are already owned (present in ownedCardsCollection)
        if(detailedOuicheList.getCollections() != null) {
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
                detailedOuicheList.getCollections().get(i).setCardsMap(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
            }
        }

        if(detailedOuicheList.getDecks() != null) {
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

        if(detailedOuicheList.getCollections() != null) {
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
                detailedOuicheList.getCollections().get(i).setCardsMap(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
            }
        }


        if(detailedOuicheList.getDecks() != null) {
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
                if(card.getOwned() == false) {
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

    public static void importThirdPartyList(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path);

        thirdPartyList = new ArrayList<>();
        for (String line : lines) {
            if(!line.startsWith("=") && !line.startsWith("-") && !line.startsWith("#") && !line.equals("")) {
                thirdPartyList.add(new CardElement(line.trim()));
            }
        }
    }

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

    public static void ouicheListSave(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            file.createNewFile();
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
                if(card.getCard().getPassCode() != null) {
                    //writer.write(card.getCard().getPasscode());
                    writer.write(card.toString());
                }
                else if (card.getCard().getPrintCode() != null)
                {
                    //writer.write(card.getCard().getPasscode());
                    writer.write(card.toString());
                }
                else {
                    System.out.println("Error : no passcode nor printcode for this card");
                }

                writer.newLine();
            }
        }

        writer.close();
    }

    public static void unusedCardsSave(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            file.createNewFile();
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));

        for (CardElement card : unusedCards) {
            //writer.write(card.getCard().getPasscode());
            writer.write(card.toString());
            writer.newLine();
        }

        writer.close();
    }

    public static void thirdPartyCardsINeedListSave(String directoryPath, String filePath) throws IOException {
        Files.createDirectories(Paths.get(directoryPath));
        File file = new File(directoryPath + filePath);
        if (!file.exists()) {
            file.createNewFile();
        }
        BufferedWriter writer = new BufferedWriter(new FileWriter(directoryPath + filePath));

        for (CardElement card : thirdPartyCardsINeedList) {
            //writer.write(card.getCard().getPasscode());
            writer.write(card.toString());
            writer.newLine();
        }

        writer.close();
    }

    public String toString() {
        String returnString = "";

        for (int i = 0; i < maOuicheList.size(); i++) {
            returnString = returnString.concat(maOuicheList.get(i).toString());
        }

        return returnString;
    }

    /*
    OwnedCardsCollection myCardsCollection;
    DecksAndCollectionsList decksList;

    List<CardElement> maOuicheList;
    List<CardElement> unusedCards;
    List<CardElement> listsIntersection;
    DecksAndCollectionsList detailedOuicheList;

    List<CardElement> thirdPartyList;
    List<CardElement> thirdPartyCardsINeedList;

    //TODO for each of these elements, create a getCardsCound and a getPrice function ?
    */
}
