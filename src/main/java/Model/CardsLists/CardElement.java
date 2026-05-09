package Model.CardsLists;

import java.util.ArrayList;
import java.util.List;

public class CardElement {
    Card card;
    Boolean specificArtwork;
    int artwork;
    Boolean isOwned;
    Boolean dontRemove;
    Boolean isInDeck;

    private String rawCode;

    /**
     * Computed/transient flag set during OuicheList generation.
     * {@code true} means the user owns a copy of this card but it does not
     * satisfy the condition or rarity requirement attached to this wanted slot.
     * Never persisted to file.
     */
    private Boolean isOwnedSubstandard;

    // ── New per-copy fields (collection / owned-cards context only) ─────────

    /**
     * Physical condition of this specific copy (Mint, Near Mint, …).
     * {@code null} means the user has not set a condition yet.
     */
    private CardCondition condition;

    /**
     * Rarity of this specific copy (Common, Super Rare, Starlight Rare, …).
     * {@code null} means the user has not set a rarity yet.
     * The user may choose any {@link CardRarity}, even one not present in
     * {@link Card#getAvailableRarities()} — that list is only a suggestion
     * source.
     */
    private CardRarity rarity;

    /**
     * Free-form tags attached to this specific copy by the user
     * (e.g. "trade", "signed", "misprint").
     * Never {@code null}; may be empty.
     */
    private List<String> customTags;

    public CardElement(Card card, Boolean specificArtwork, Boolean isOwned, Boolean dontRemove, Boolean isInDeck) {
        this.card = card;
        this.specificArtwork = specificArtwork;
        this.isOwned = isOwned;
        this.isOwnedSubstandard = false;
        this.dontRemove = dontRemove;
        this.isInDeck = isInDeck;
        this.condition = null;
        this.rarity = null;
        this.customTags = new ArrayList<>();
    }

    public CardElement(Card card) {
        this(card, false, false, false, false);
    }

    public CardElement(Card card, String string) {
        setValues(string);
        this.card = card;
    }

    /**
     * Copy constructor. Creates a new {@code CardElement} from {@code source},
     * sharing the same {@link Card} reference and copying all artwork flags,
     * the {@code dontRemove} / {@code isInDeck} flags, the raw code, and the
     * quality requirements ({@code condition}, {@code rarity}, {@code customTags}).
     * <p>
     * {@code isOwned} and {@code isOwnedSubstandard} are always reset to
     * {@code false}: copied wanted-card slots must start unmatched so that the
     * OuicheList generation can mark them fresh.
     * </p>
     */
    public CardElement(CardElement source) {
        this.card = source.card;
        this.specificArtwork = source.specificArtwork;
        this.artwork = source.artwork;
        this.rawCode = source.rawCode;
        this.isOwned = false;
        this.isOwnedSubstandard = false;
        this.dontRemove = source.dontRemove;
        this.isInDeck = source.isInDeck;
        this.condition = source.condition;
        this.rarity = source.rarity;
        this.customTags = source.customTags != null
                ? new ArrayList<>(source.customTags) : new ArrayList<>();
    }

    public CardElement(String string) throws Exception {
        // ── Step 1 : extract the new pipe-delimited fields (condition|rarity|tags)
        // Format: <existing>,<flags>|<conditionCode>|<rarityCode>|<tag1>;<tag2>...
        // The pipe section is entirely optional – old files without it are
        // parsed identically to before.
        this.customTags = new ArrayList<>();
        String[] pipeParts = string.split("\\|", -1);
        string = pipeParts[0];   // restore original string without new fields

        if (pipeParts.length > 1 && !pipeParts[1].isEmpty()) {
            this.condition = CardCondition.fromCode(pipeParts[1]);
        }
        if (pipeParts.length > 2 && !pipeParts[2].isEmpty()) {
            this.rarity = CardRarity.fromCode(pipeParts[2]);
        }
        if (pipeParts.length > 3 && !pipeParts[3].isEmpty()) {
            for (String tag : pipeParts[3].split(";")) {
                String t = tag.trim();
                if (!t.isEmpty()) this.customTags.add(t);
            }
        }

        // ── Step 2 : original parsing logic (unchanged) ────────────────────
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

    // ── New per-copy fields ─────────────────────────────────────────────────

    /**
     * Returns whether this wanted-card slot is satisfied by a copy that exists
     * in the owned collection but does not meet the condition or rarity
     * requirement of this slot.
     * <p>
     * This is a transient/computed flag set during OuicheList generation.
     * It is never persisted to file.
     * </p>
     *
     * @return {@code true} if a substandard copy was matched, {@code false}
     * otherwise (including when the slot is fully satisfied via
     * {@link #isOwned})
     */
    public Boolean getIsOwnedSubstandard() {
        return Boolean.TRUE.equals(isOwnedSubstandard);
    }

    /**
     * Sets the substandard-ownership flag. Called by the OuicheList generator
     * during the second (quality-relaxed) ownership pass.
     *
     * @param ownedSubstandard {@code true} to mark this slot as matched by a
     *                         substandard owned copy
     */
    public void setIsOwnedSubstandard(Boolean ownedSubstandard) {
        this.isOwnedSubstandard = ownedSubstandard;
    }

    /**
     * Returns the physical condition of this copy, or {@code null} if not set.
     *
     * @return the {@link CardCondition}, or {@code null}
     */
    public CardCondition getCondition() {
        return condition;
    }

    /**
     * Sets the physical condition of this copy.
     *
     * @param condition the {@link CardCondition} to set, or {@code null} to clear
     */
    public void setCondition(CardCondition condition) {
        this.condition = condition;
    }

    /**
     * Returns the rarity of this copy, or {@code null} if not set.
     * <p>
     * The rarity may differ from the rarities listed in
     * {@link Card#getAvailableRarities()} — that list is only a suggestion source.
     * </p>
     *
     * @return the {@link CardRarity}, or {@code null}
     */
    public CardRarity getRarity() {
        return rarity;
    }

    /**
     * Sets the rarity of this copy.
     *
     * @param rarity the {@link CardRarity} to set, or {@code null} to clear
     */
    public void setRarity(CardRarity rarity) {
        this.rarity = rarity;
    }

    /**
     * Returns the mutable list of custom tags attached to this copy by the user
     * (e.g. {@code "trade"}, {@code "signed"}, {@code "misprint"}).
     * Never {@code null}; may be empty.
     *
     * @return the list of custom tags
     */
    public List<String> getCustomTags() {
        return customTags;
    }

    /**
     * Replaces the list of custom tags.
     *
     * @param customTags the new tag list; passing {@code null} is treated as an
     *                   empty list
     */
    public void setCustomTags(List<String> customTags) {
        this.customTags = customTags != null ? customTags : new ArrayList<>();
    }

    /**
     * Adds a single custom tag if it is not already present.
     *
     * @param tag the tag to add; ignored if {@code null} or blank
     */
    public void addCustomTag(String tag) {
        if (tag != null && !tag.isBlank() && !customTags.contains(tag)) {
            customTags.add(tag);
        }
    }

    /**
     * Removes a single custom tag (no-op if absent).
     *
     * @param tag the tag to remove
     */
    public void removeCustomTag(String tag) {
        customTags.remove(tag);
    }

    // ── End of new fields ───────────────────────────────────────────────────

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
     * <p>
     * Format (all parts after the first are optional and backward-compatible):
     * <pre>
     *   &lt;id&gt;[,&lt;flags&gt;][|&lt;conditionCode&gt;|&lt;rarityCode&gt;|&lt;tag1&gt;;&lt;tag2&gt;…]
     * </pre>
     * Examples:
     * <pre>
     *   12345678                          – basic card, no extras
     *   12345678,OD                       – owned, in deck
     *   12345678,O|NM|SR|trade;signed     – owned, Near Mint, Super Rare, two tags
     *   12345678,O|NM||trade             – owned, Near Mint, no rarity, one tag
     * </pre>
     * The pipe section is written only when at least one new field is set,
     * ensuring round-trip compatibility with older file parsers that do not
     * know about these fields (they will stop at the first {@code |}).
     * </p>
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

        // ── Append new fields ──────────────────────────────────────────────
        boolean hasCondition = condition != null;
        boolean hasRarity = rarity != null;
        boolean hasTags = customTags != null && !customTags.isEmpty();

        if (hasCondition || hasRarity || hasTags) {
            id += "|";
            id += hasCondition ? condition.getCode() : "";
            id += "|";
            id += hasRarity ? rarity.getCode() : "";
            if (hasTags) {
                id += "|" + String.join(";", customTags);
            }
        }

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