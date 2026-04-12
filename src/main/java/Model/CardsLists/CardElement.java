package Model.CardsLists;

public class CardElement {
    Card card;
    Boolean specificArtwork;
    int artwork;
    Boolean isOwned;
    Boolean dontRemove;
    Boolean isInDeck;

    private String rawCode;

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

    public CardElement(Card card, String string) {
        setValues(string);
        this.card = card;
    }

    public CardElement(String string) throws Exception {
        if (string.contains(",")) {
            String[] parts = string.split(",", 2);
            String part1 = parts[0];
            String part2 = parts[1];
            this.rawCode = part1;   // ← preserve original ID
            if (part2.contains("O")) {
                this.isOwned = true;
                part2 = part2.replace("O", "");
            } else {
                this.isOwned = false;
            }
            if (part2.contains("D")) {
                this.isInDeck = true;
                part2 = part2.replace("D", "");
            } else {
                this.isInDeck = false;
            }
            if (part2.contains("+")) {
                this.dontRemove = true;
                part2 = part2.replace("+", "");
            } else {
                this.dontRemove = false;
            }
            if (part2.contains("*")) {
                this.specificArtwork = true;
                part2 = part2.replace("*", "");
                this.artwork = Integer.parseInt(part2.trim());
                this.card = new Card(part1, part2.trim());
            } else {
                this.specificArtwork = false;
                this.artwork = 0;
                this.card = new Card(part1);
            }
        } else {
            this.rawCode = string;  // ← preserve original ID
            this.isOwned = false;
            this.isInDeck = false;
            this.dontRemove = false;
            this.specificArtwork = false;
            this.artwork = 0;
            this.card = new Card(string);
        }
    }

    /**
     * Gets the card associated with this element.
     *
     * @return the associated card
     */
    public Card getCard() {
        return card;
    }

    /**
     * Sets the card associated with this element.
     *
     * @param card the associated card
     */
    public void setCard(Card card) {
        this.card = card;
    }

    /**
     * Checks if this element is associated with a specific artwork number.
     * <p>
     * If this element was created with a specific artwork number, this method
     * returns true. Otherwise, this method returns false.
     * </p>
     *
     * @return true if this element is associated with a specific artwork number, false otherwise
     */
    public Boolean getSpecificArtwork() {
        return specificArtwork;
    }

    /**
     * Sets whether this card element is associated with a specific artwork.
     *
     * @param specificArtwork a Boolean indicating if this element should be linked to a specific artwork
     */
    public void setSpecificArtwork(Boolean specificArtwork) {
        this.specificArtwork = specificArtwork;
    }

    /**
     * Gets whether this card element is owned.
     * <p>
     * If this element was created with the flag 'O' in the string, this method
     * returns true. Otherwise, this method returns false.
     * </p>
     *
     * @return true if this element is owned, false otherwise
     */
    public Boolean getOwned() {
        return isOwned;
    }

    /**
     * Sets whether this card element is owned.
     * <p>
     * If true, this element was created with the flag 'O' in the string.
     * Otherwise, this element was created without the flag 'O' in the string.
     * </p>
     *
     * @param owned a Boolean indicating if this element should be marked as owned.
     */
    public void setOwned(Boolean owned) {
        isOwned = owned;
    }

    /**
     * Retrieves the status of whether this card element should not be removed.
     * It is used in a ThemeCollection to mark elements that should not be removed
     * even if they are present in a Deck from the ThemeCollection.
     *
     * @return true if this element is marked not to be removed, false otherwise
     */
    public Boolean getDontRemove() {
        return dontRemove;
    }

    /**
     * Sets whether this card element should not be removed from a ThemeCollection.
     * <p>
     * This flag is used in a ThemeCollection to mark elements that should not be removed
     * even if they are present in a Deck from the ThemeCollection.
     * </p>
     *
     * @param dontRemove a Boolean indicating if this element should be marked not to be removed.
     */
    public void setDontRemove(Boolean dontRemove) {
        this.dontRemove = dontRemove;
    }

    /**
     * Gets the artwork number of this card element.
     * <p>
     * If this element was created with a specific artwork number, this method
     * returns the artwork number. Otherwise, this method returns 0.
     * </p>
     *
     * @return the artwork number associated with this card element
     */
    public int getArtwork() {
        return artwork;
    }

    /**
     * Sets the artwork number associated with this card element.
     * <p>
     * If artwork is greater than 0, this element is associated with a specific artwork number.
     * Otherwise, this element is not associated with a specific artwork number.
     * </p>
     *
     * @param artwork the artwork number to associate with this card element
     */
    public void setArtwork(int artwork) {
        this.artwork = artwork;
    }

    /**
     * Checks if this element is in a deck.
     * <p>
     * If this element was created with the flag 'D' in the string, this method
     * returns true. Otherwise, this method returns false.
     * </p>
     *
     * @return true if this element is in a deck, false otherwise
     */
    public Boolean getIsInDeck() {
        return isInDeck;
    }

    /**
     * Sets the flag indicating whether this element is in a deck.
     * <p>
     * If inDeck is true, this element is in a deck. Otherwise, this element is
     * not in a deck.
     * </p>
     *
     * @param inDeck true if this element is in a deck, false otherwise
     */
    public void setIsInDeck(Boolean inDeck) {
        isInDeck = inDeck;
    }

    /**
     * Sets the values of the card element based on a given string.
     * <p>
     * The string should be in the format of "O" or "D" or "+" or "*integer"
     * where "O" stands for owned, "D" stands for in a deck, "+" stands for do not remove,
     * and "*integer" stands for specific artwork.
     * </p>
     *
     * @param string the string containing the values to set
     */
    public void setValues(String string) {
        if (string.contains("O")) {
            this.isOwned = true;
            string = string.replace("O", "");
        } else {
            this.isOwned = false;
        }

        if (string.contains("D")) {
            this.isInDeck = true;
            string = string.replace("D", "");
        } else {
            this.isInDeck = false;
        }

        if (string.contains("+")) {
            this.dontRemove = true;
            string = string.replace("+", "");
        } else {
            this.dontRemove = false;
        }

        //TODO retirer "*" et utiliser "," ?
        if (string.contains("*")) {
            this.specificArtwork = true;
            string = string.replace("*", "");
            String[] splitString = string.split(",");
            this.artwork = Integer.parseInt(splitString[1]);
        } else {
            this.specificArtwork = false;
            this.artwork = 0;
        }
    }

    public String getRawCode() {
        return rawCode;
    }

    public void setRawCode(String rawCode) {
        this.rawCode = rawCode;
    }

    /**
     * Converts this card element to a string.
     * <p>
     * The string representation of this card element is in the format of
     * "passcode,artwork,O,D,+" where "passcode" is the passcode of the card,
     * "artwork" is the artwork number of the card, "O" stands for owned,
     * "D" stands for in a deck, and "+" stands for do not remove.
     * </p>
     * @return a string representation of this card element
     */
    public String toString() {
        String returnValue;

        returnValue = this.getCard().getPassCode();
        if (returnValue == null) {
            returnValue = this.getCard().getPrintCode();
        }
        if (returnValue == null) {
            returnValue = "";
        }
        if (specificArtwork || isOwned || dontRemove) {
            returnValue += ",";
        }
        if (specificArtwork) {
            returnValue += "*";
            returnValue += this.getCard().getArtNumber();
        }
        if (isOwned) {
            returnValue += "O";
        }
        if (isInDeck) {
            returnValue += "D";
        }
        if (dontRemove) {
            returnValue += "+";
        }

        return returnValue;
    }

    /**
     * Serialises this element for the OwnedCardsCollection file.
     * Priority: printCode (edition-specific) > passCode (generic) > rawCode (non-DB fallback)
     */
    public String toCollectionString() {
        String id = null;
        if (this.card != null) id = this.card.getPrintCode();
        if (id == null && this.card != null) id = this.card.getPassCode();
        if (id == null) id = this.rawCode;
        if (id == null) id = "";

        if (specificArtwork || isOwned || dontRemove) id += ",";
        if (specificArtwork) {
            id += "*";
            id += this.card.getArtNumber();
        }
        if (isOwned) id += "O";
        if (isInDeck) id += "D";
        if (dontRemove) id += "+";
        return id;
    }

    public String getPrice() {
        return this.getCard().getPrice();
    }

    /**
     * Save format for ThemeCollections: passCode first (printCode as fallback),
     * with artwork marker if a non-default artwork is identified.
     * <p>
     * Two sources for artwork:
     * - CardElement.artwork (set when the card was imported from a .ytc file)
     * - card.getArtNumber()  (set when the card was picked in AllExistingCardsPane)
     */
    public String toThemeCollectionString() {
        // ThemeCollection format: passCode first, printCode as fallback
        String id = this.getCard().getPassCode();
        if (id == null) id = this.getCard().getPrintCode();
        if (id == null) id = "";

        // Explicitly-flagged artwork (from .ytc file import)
        if (this.specificArtwork && this.artwork > 0) {
            return id + ",*" + this.artwork;
        }

        // Artwork picked in AllExistingCardsPane (stored in card.artNumber)
        String cardArtNumber = this.getCard().getArtNumber();
        if (cardArtNumber != null && !cardArtNumber.isEmpty()) {
            try {
                int artNum = Integer.parseInt(cardArtNumber);
                if (artNum > 1) {
                    // Non-default artwork — save the marker so it round-trips correctly
                    return id + ",*" + artNum;
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return id;
    }
}
