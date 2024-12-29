package Model.CardsLists;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DecksAndCollectionsList {
    public List<Deck> decks;
    public List<ThemeCollection> collections;

    public List<Deck> getDecks() {
        return decks;
    }

    public void setDecks(List<Deck> decks) {
        this.decks = decks;
    }

    public List<ThemeCollection> getCollections() {
        return collections;
    }

    public void setCollections(List<ThemeCollection> collections) {
        this.collections = collections;
    }

    /*public DecksList(Map<String, List<String>> collectionFileList) throws Exception {
        this.decks = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : collectionFileList.entrySet()) {
            this.decks.add(new Deck(entry.getKey()));
            for (String line : entry.getValue()) {
                //System.out.println(line);
                if (!line.contains("#") && !line.contains("!")&& !line.contains("=")&& !line.contains("--") && !line.equals("")) {
                    if (entry.getKey().endsWith(".ydk")) {
                        if(passCodeToId(Integer.valueOf(line)) != null) {
                            this.decks.get(this.decks.size() - 1).AddCard(new Card(passCodeToId(Integer.valueOf(line))));
                        }
                    }
                    else {
                        this.decks.get(this.decks.size() - 1).AddCard(new Card(line));
                    }
                }
            }
        }
    }*/

    /*public DecksList(Map<String, List<String>> collectionFileList) throws Exception {
        this.decks = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : collectionFileList.entrySet()) {
            // Create a temporary file to hold the deck data
            File tempFile = File.createTempFile("tempDeck", ".ydk");
            try (PrintWriter writer = new PrintWriter(tempFile)) {
                for (String line : entry.getValue()) {
                    writer.println(line);
                }
            }

            // Create a new Deck using the temporary file
            this.decks.add(new Deck(tempFile.getAbsolutePath()));

            // Delete the temporary file
            tempFile.delete();
        }
    }*/

    /*public DecksAndCollectionsList(String dirPath) throws Exception {
        this.decks = new ArrayList<>();
        File dir = new File(dirPath);

        //TODO start with collections, and after that, only add the decks which are not in any collection
        for (File file : dir.listFiles()) {
            if(file.getPath().contains(".ydk")) {
                Deck deck = new Deck(file.getPath());
                this.decks.add(deck);
            }
            else if(file.getPath().contains(".ytc")) {
                ThemeCollection themeCollection = new ThemeCollection(file.getPath());
                this.collections.add(themeCollection);
            }
            else {
                System.out.println("Unable to load file : " + file.getPath());
            }
        }
    }*/

    public DecksAndCollectionsList(String dirPath) throws Exception {
        this.decks = new ArrayList<>();
        this.collections = new ArrayList<>();
        File dir = new File(dirPath);

        // First, import the collections
        for (File file : dir.listFiles()) {
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

        for (File file : dir.listFiles()) {
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

    public DecksAndCollectionsList() throws Exception {
        this.decks = new ArrayList<>();
        this.collections = new ArrayList<>();
    }

    public List<CardElement> toList() {
        List<CardElement> returnValue = new ArrayList<>();

        if(this.getDecks() != null) {
            for (int i = 0; i < this.getDecks().size(); i++) {
                returnValue.addAll(this.getDecks().get(i).toList());
            }
        }

        if(this.getCollections() != null) {
            for (int i = 0; i < this.getCollections().size(); i++) {
                returnValue.addAll(this.getCollections().get(i).getCardsList());
            }
        }

        return returnValue;
    }

    public List<CardElement> toListCollectionsAndLinkedDecks() {
        List<CardElement> returnValue = new ArrayList<>();

        for (int i = 0; i < this.getCollections().size(); i++) {
            returnValue.addAll(this.getCollections().get(i).toList());
        }

        return returnValue;
    }

    public void addDeck(Deck deckToAdd) {
        this.decks.add(deckToAdd);
    }

    public void addCollection(ThemeCollection collectionToAdd) {
        this.collections.add(collectionToAdd);
    }

    @Override
    public String toString() {
        String returnValue = "";

        for (int i = 0; i < this.decks.size(); i++) {
            returnValue = returnValue.concat(this.decks.get(i).toString() + "\n");
        }

        return returnValue;
    }

    public Integer getCardCount() {
        return getCollectionsCardCount() + getDecksCardCount();
    }

    public String getPrice() {
        float returnValue = 0;

        returnValue = Float.valueOf(getCollectionsPrice()) + Float.valueOf(getDecksPrice());

        return String.valueOf(returnValue);
    }

    public Integer getCollectionsCardCount() {
        Integer returnValue = 0;

        for (int i = 0; i < this.collections.size(); i++) {
            returnValue = returnValue + this.collections.get(i).getCardCount();
        }

        return returnValue;
    }

    public String getCollectionsPrice() {
        float returnValue = 0;

            for (int i = 0; i < this.collections.size(); i++) {
            returnValue = returnValue + Float.valueOf(this.collections.get(i).getPrice());
        }

        return String.valueOf(returnValue);
    }

    public Integer getDecksCardCount() {
        Integer returnValue = 0;

        for (int i = 0; i < this.decks.size(); i++) {
            returnValue = returnValue + this.decks.get(i).getCardCount();
        }

        return returnValue;
    }

    public String getDecksPrice() {
        float returnValue = 0;

        for (int i = 0; i < this.decks.size(); i++) {
            returnValue = returnValue + Float.valueOf(this.decks.get(i).getPrice());
        }

        return String.valueOf(returnValue);
    }
}
