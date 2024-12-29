package Model.CardsLists;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-physical list of cards
 */
/*public class Deck extends  CardsGroup {

    @Override
    public String getName() {
        return super.getName();
    }

    @Override
    public void setName(String name) {
        super.setName(name);
    }

    @Override
    public List<Card> getCardList() {
        return super.getCardList();
    }

    @Override
    public void setCardList(List<Card> cardList) {
        super.setCardList(cardList);
    }

    public Deck(String name) {
        super(name);
    }

    public Deck(String name, List<Card> collectionFileList) {
        super(name, collectionFileList);
    }

    @Override
    public void AddCard(Card cardToAdd) {
        super.AddCard(cardToAdd);
    }

    @Override
    public String toString() {
        return super.toString();
    }
}*/

public class Deck {
    private String name;
    private List<CardElement> mainDeck;
    private List<CardElement> extraDeck;
    private List<CardElement> sideDeck;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<CardElement> getMainDeck() {
        return mainDeck;
    }

    public void setMainDeck(List<CardElement> mainDeck) {
        this.mainDeck = mainDeck;
    }

    public List<CardElement> getExtraDeck() {
        return extraDeck;
    }

    public void setExtraDeck(List<CardElement> extraDeck) {
        this.extraDeck = extraDeck;
    }

    public List<CardElement> getSideDeck() {
        return sideDeck;
    }

    public void setSideDeck(List<CardElement> sideDeck) {
        this.sideDeck = sideDeck;
    }

    public Deck(String filePath) {
        this.mainDeck = new ArrayList<>();
        this.extraDeck = new ArrayList<>();
        this.sideDeck = new ArrayList<>();

        if(filePath.contains(".ydk")) {
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
                    } else if (!line.contains("#") && currentList != null && line != "") {
                        currentList.add(new CardElement(new Card(line)));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else {
            this.name = name;
        }
    }

    public Deck() {
        this.mainDeck = new ArrayList<>();
        this.extraDeck = new ArrayList<>();
        this.sideDeck = new ArrayList<>();
    }

    /*public Deck(String name, Map<Card, String> cardList) {
        this.name = name;
        this.mainDeck = cardList;
        this.extraDeck = cardList;
        this.sideDeck = cardList;
    }*/

    public void saveDeck(String filePath) {
        filePath += "/" + this.name + ".ydk";
        try (PrintWriter writer = new PrintWriter(new File(filePath))) {
            writer.println("#created with PotOfGreedManager");
            writeCards(writer, "#main", mainDeck);
            writeCards(writer, "#extra", extraDeck);
            writeCards(writer, "!side", sideDeck);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void writeCards(PrintWriter writer, String header, List<CardElement> cards) {
        if (!cards.isEmpty()) {
            writer.println(header);
            for (int i = 0; i < cards.size(); i++) {
                writer.println(cards.get(i).getCard().getPassCode());
            }
        }
    }

    public void AddCardMain(Card cardToAdd) {
        this.mainDeck.add(new CardElement(cardToAdd));
    }

    public void AddCardExtra(Card cardToAdd) {
        this.extraDeck.add(new CardElement(cardToAdd));
    }

    public void AddCardSide(Card cardToAdd) {
        this.sideDeck.add(new CardElement(cardToAdd));
    }

    public void AddCardMain(CardElement cardToAdd) {
        this.mainDeck.add(cardToAdd);
    }

    public void AddCardExtra(CardElement cardToAdd) {
        this.extraDeck.add(cardToAdd);
    }

    public void AddCardSide(CardElement cardToAdd) {
        this.sideDeck.add(cardToAdd);
    }

    public void AddCardMain(Card cardToAdd, String parameters) {
        Boolean specificArtwork = false;
        Boolean isOwned = false;
        Boolean isInDeck = false;
        Boolean dontRemove = false;
        if(parameters.contains("*")) {
            specificArtwork = true;
        }
        if(parameters.contains("O")) {
            isOwned = true;
        }
        if(parameters.contains("D")) {
            isInDeck = true;
        }
        if(parameters.contains("+")) {
            dontRemove = true;
        }
        this.mainDeck.add(new CardElement(cardToAdd, specificArtwork, isOwned, dontRemove, isInDeck));
    }

    public void AddCardExtra(Card cardToAdd, String parameters) {
        Boolean specificArtwork = false;
        Boolean isOwned = false;
        Boolean isInDeck = false;
        Boolean dontRemove = false;
        if(parameters.contains("*")) {
            specificArtwork = true;
        }
        if(parameters.contains("O")) {
            isOwned = true;
        }
        if(parameters.contains("D")) {
            isInDeck = true;
        }
        if(parameters.contains("+")) {
            dontRemove = true;
        }
        this.extraDeck.add(new CardElement(cardToAdd, specificArtwork, isOwned, dontRemove, isInDeck));
    }

    public void AddCardSide(Card cardToAdd, String parameters) {
        Boolean specificArtwork = false;
        Boolean isOwned = false;
        Boolean isInDeck = false;
        Boolean dontRemove = false;
        if(parameters.contains("*")) {
            specificArtwork = true;
        }
        if(parameters.contains("O")) {
            isOwned = true;
        }
        if(parameters.contains("D")) {
            isInDeck = true;
        }
        if(parameters.contains("+")) {
            dontRemove = true;
        }
        this.sideDeck.add(new CardElement(cardToAdd, specificArtwork, isOwned, dontRemove, isInDeck));
    }

    public List<CardElement> toList() {
        List<CardElement> returnValue = new ArrayList<>();

        returnValue.addAll(this.getMainDeck());
        returnValue.addAll(this.getExtraDeck());
        returnValue.addAll(this.getSideDeck());

        return returnValue;
    }

    public Integer getCardCount() {
        Integer returnValue = 0;
        returnValue += this.getMainDeck().size();
        returnValue += this.getExtraDeck().size();
        returnValue += this.getSideDeck().size();
        return returnValue;
    }

    public String getPrice() {
        float returnValue = 0;
        returnValue += Float.valueOf(this.getMainDeckPrice());
        returnValue += Float.valueOf(this.getExtraDeckPrice());
        returnValue += Float.valueOf(this.getSideDeckPrice());
        return String.valueOf(returnValue);
    }

    public String getMainDeckPrice() {
        float returnValue = 0;

        for (int i = 0; i < this.getMainDeck().size(); i++) {
            if(this.getMainDeck().get(i) != null) {
                if(this.getMainDeck().get(i).getPrice() != null) {
                    returnValue += Float.valueOf(this.getMainDeck().get(i).getPrice());
                }
            }
        }

        return String.valueOf(returnValue);
    }

    public String getExtraDeckPrice() {
        float returnValue = 0;

        for (int i = 0; i < this.getExtraDeck().size(); i++) {
            if(this.getExtraDeck().get(i) != null) {
                if(this.getExtraDeck().get(i).getPrice() != null) {
                    returnValue += Float.valueOf(this.getExtraDeck().get(i).getPrice());
                }
            }
        }

        return String.valueOf(returnValue);
    }

    public String getSideDeckPrice() {
        float returnValue = 0;

        for (int i = 0; i < this.getSideDeck().size(); i++) {
            if(this.getSideDeck().get(i) != null) {
                if(this.getSideDeck().get(i).getPrice() != null) {
                    returnValue += Float.valueOf(this.getSideDeck().get(i).getPrice());
                }
            }
        }

        return String.valueOf(returnValue);
    }
}
