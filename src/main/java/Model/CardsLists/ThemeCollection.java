package Model.CardsLists;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static Model.CardsLists.ListDifferenceIntersection.ListDifIntersectArtworkWithExceptions;
import static Model.CardsLists.ListDifferenceIntersection.ListDifIntersectKonamiIdWithExceptions;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<CardElement> getCardsList() {
        return cardsList;
    }

    public void setCardsList(List<CardElement> cardsList) {
        this.cardsList = cardsList;
    }

    public List<CardElement> getExceptionsToNotAdd() {
        return exceptionsToNotAdd;
    }

    public void setExceptionsToNotAdd(List<CardElement> exceptionsToNotAdd) {
        this.exceptionsToNotAdd = exceptionsToNotAdd;
    }

    public List<Deck> getLinkedDecks() {
        return linkedDecks;
    }

    public void setLinkedDecks(List<Deck> linkedDecks) {
        this.linkedDecks = linkedDecks;
    }

    public Boolean getConnectToWholeCollection() {
        return connectToWholeCollection;
    }

    public void setConnectToWholeCollection(Boolean connectToWholeCollection) {
        this.connectToWholeCollection = connectToWholeCollection;
    }

        /* open file
        *  name = fileName
        *  parcourir lignes fichier jusqu'à ligne "Not to add" (si présent), créer et compléter cardsList, booléen à True si "*" dans la ligne
        *  créer liste exceptionsToNotRemove et ajouter toutes les cartes ayant un "+" dans leur ligne
        *  parcourir lignes fichier à partir de "Not to add" (si présent), créer et compléter exceptionsToNotAdd
        *  si connectToWholeCollection == false :
        *  parcourir decks liés si liste non vide, ouvrir fichiers concernés, parcourir listes, retirer cartes présentes dans la liste sauf si "+" dans la ligne
        *  sinon parcourir Collection et retirer les cartes présentes sauf si "+" présent dans la liste
        * */
    public ThemeCollection(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        this.name = path.getFileName().toString().replaceFirst("[.][^.]+$", "");

        List<String> lines = Files.readAllLines(path);

        for (String line : lines) {
            if (line.equals("#Not to add")) break;

            /*Card card;
            if (line.contains(",")) {
                String[] parts = line.split(",", 2);
                String part1 = parts[0].replace("*", "").replace("+", "").trim();
                String part2 = parts[1];
                card = new Card(part1, part2);
            }
            else {
                card = new Card(line.replace("*", "").replace("+", "").trim());
            }
            String markers = "";
            if(line.contains("*")) {
                markers += "*";
            }
            if(line.contains("+")) {
                markers += "+";
            }
            cardsList.put(card, markers);*/
            cardsList.add(new CardElement(line));
        }

        int index = lines.indexOf("#Not to add");
        if (index != -1) {
            for (int i = index + 1; i < lines.size(); i++) {
                if (lines.get(i).equals("#Not to add")) break;
                exceptionsToNotAdd.add(new CardElement(lines.get(i)));
            }
        }

        index = lines.indexOf("#Link to whole collection");
        if (index != -1) {
            connectToWholeCollection = true;
        }
        else {
            connectToWholeCollection = false;
        }

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


        /* Create or replace file with this.name as a name and "ytc" as extension
        *  parcourir liste cartes et ajouter PassCodes dans fichier, avec "*" si booléen correspondant à True, et "+" si carte dans exceptionsToNotRemove (parcours exceptionsToNotRemove en parallèle)
        *  ajout ligne "Not to add" si exceptionsToNotAdd n'est pas vide
        *  parcourir liste exceptionsToNotAdd si non vide et ajouter PassCodes
        *  Enregistrer/libérer fichier
        * */
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
        }
        else {
            if(linkedDecks.size() != 0) {
                for(int i = 0; i < linkedDecks.size(); i++) {
                    writer.write(linkedDecks.get(i).getName());
                    writer.newLine();
                }
            }
        }

        if(!archetypes.isEmpty()) {
            for(String archetype : archetypes) {
                writer.write(archetype);
                writer.newLine();
            }
        }

        writer.close();
    }

    /*public void AddCard(Card cardToAdd) {
        this.cardsList.add(cardToAdd);
    }*/

    public void AddDeck(Deck deckToAdd) {
        this.linkedDecks.add(deckToAdd);
    }

    /*public String toString() {
        String returnValue = this.name;

        for (int i = 0; i < this.cardsList.size(); i++) {
            returnValue = returnValue.concat("\n" + this.cardsList.get(i).toString());
        }

        return returnValue;
    }*/

    public void AddArchetypeCards(String archetype) {
        //TODO parcourir cartes archétype, ajouter celles qui n'y sont pas, si elles ne sont pas présentes dans la liste des cartes à ne pas ajouter
        // ajouter archétype à liste des archétypes
    }

    public void createCardsList() throws Exception {
        List<CardElement> linkedDecksList = new ArrayList<>();
        List<CardElement> tempCardsList;
        List<List<CardElement>> tempList;

        tempList = ListDifIntersectArtworkWithExceptions(cardsList, linkedDecksList, "D");
        tempCardsList = tempList.get(0);
        tempList = ListDifferenceIntersection.ListDifIntersectKonamiIdWithExceptions(tempCardsList, linkedDecksList, "D");

        this.cardsList = tempList.get(0);
    }

    public List<CardElement> toList() {
        List<CardElement> returnValue = new ArrayList<>();

        for (int i = 0; i < this.getLinkedDecks().size(); i++) {
            returnValue.addAll(this.getLinkedDecks().get(i).toList());
        }

        returnValue.addAll(this.getCardsList());

        return returnValue;
    }

    public void setCardsMap(List<CardElement> cardStringMap) {

    }

    public Integer getCardCount() {
        return this.cardsList.size();
    }

    public String getPrice() {
        float price = 0;
        for (CardElement card : this.cardsList) {
            price += Float.valueOf(card.getCard().getPrice());
        }
        return String.valueOf(price);
    }
}
