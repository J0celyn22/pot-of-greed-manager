package Model.CardsLists;

public class CardElement {
    Card card;
    Boolean specificArtwork;
    int artwork;
    Boolean isOwned;
    Boolean dontRemove;
    Boolean isInDeck;

    public Card getCard() {
        return card;
    }

    public void setCard(Card card) {
        this.card = card;
    }

    public Boolean getSpecificArtwork() {
        return specificArtwork;
    }

    public void setSpecificArtwork(Boolean specificArtwork) {
        this.specificArtwork = specificArtwork;
    }

    public Boolean getOwned() {
        return isOwned;
    }

    public void setOwned(Boolean owned) {
        isOwned = owned;
    }

    public Boolean getDontRemove() {
        return dontRemove;
    }

    public void setDontRemove(Boolean dontRemove) {
        this.dontRemove = dontRemove;
    }

    public int getArtwork() {
        return artwork;
    }

    public void setArtwork(int artwork) {
        this.artwork = artwork;
    }

    public Boolean getInDeck() {
        return isInDeck;
    }

    public void setInDeck(Boolean inDeck) {
        isInDeck = inDeck;
    }

    public CardElement(Card card, Boolean specificArtwork, Boolean isOwned, Boolean dontRemove, Boolean isInDeck) {
        this.card = card;
        this.specificArtwork = specificArtwork;
        this.isOwned = isOwned;
        this.dontRemove = dontRemove;
        this.isInDeck = isInDeck;
    }

    public CardElement(Card card) {
        this(card, false, false, false, false);
    }

    public CardElement(Card card, String string) throws Exception {
        setValues(string);
        this.card = card;
    }

    public CardElement(String string) throws Exception {
        if (string.contains(",")) {
            String[] parts = string.split(",", 2);
            String part1 = parts[0];
            String part2 = parts[1];
            if(part2.contains("O")) {
                this.isOwned = true;
                part2 = part2.replace("O", "");
            }
            else {
                this.isOwned = false;
            }

            if(part2.contains("D")) {
                this.isInDeck = true;
                part2 = part2.replace("D", "");
            }
            else {
                this.isInDeck = false;
            }

            if(part2.contains("+")) {
                this.dontRemove = true;
                part2 = part2.replace("+", "");
            }
            else {
                this.dontRemove = false;
            }

            if(part2.contains("*")) {
                this.specificArtwork = true;
                part2 = part2.replace("*", "");
                this.artwork = Integer.valueOf(part2);
                this.card = new Card(part1, part2);
            }
            else {
                this.specificArtwork = false;
                this.artwork = 0;
                this.card = new Card(part1);
            }
        }
        else {
            this.isOwned = false;
            this.isInDeck = false;
            this.dontRemove = false;
            this.specificArtwork = false;
            this.artwork = 0;
            this.card = new Card(string);
        }
    }

    public void setValues(String string) throws Exception {
        if(string.contains("O")) {
            this.isOwned = true;
            string = string.replace("O", "");
        }
        else {
            this.isOwned = false;
        }

        if(string.contains("D")) {
            this.isInDeck = true;
            string = string.replace("D", "");
        }
        else {
            this.isInDeck = false;
        }

        if(string.contains("+")) {
            this.dontRemove = true;
            string = string.replace("+", "");
        }
        else {
            this.dontRemove = false;
        }

        if(string.contains("*")) {
            this.specificArtwork = true;
            string = string.replace("*", "");
            this.artwork = Integer.valueOf(string);
        }
        else {
            this.specificArtwork = false;
            this.artwork = 0;
        }
    }

    public String toString() {
        String returnValue = "";

        returnValue = this.getCard().getPassCode();
        if(returnValue == null) {
            returnValue = this.getCard().getPrintCode();
        }
        if(returnValue == null) {
            returnValue = "";
        }
        if(specificArtwork || isOwned || dontRemove) {
            returnValue += ",";
        }
        if(specificArtwork) {
            returnValue += "*";
            returnValue += this.getCard().getArtNumber();
        }
        if(isOwned) {
            returnValue += "O";
        }
        if(isInDeck) {
            returnValue += "D";
        }
        if(dontRemove) {
            returnValue += "+";
        }

        return returnValue;
    }

    public String getPrice() {
        return this.getCard().getPrice();
    }
}
