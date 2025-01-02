package Model.CardsLists;

import java.util.List;
import java.util.Objects;

public class Card {
    private String konamiId;
    private String passCode;
    private String printCode;
    private String imagePath;

    private String cardType;
    private List<String> cardProperties;
    private String description;

    private String monsterType;
    private int atk;
    private int def;
    private int level;
    private int rank;
    private String attribute;
    private int linkVal;
    private List<String> linkMarker;
    private int scale;

    private String name_FR;
    private String name_EN;
    private String name_JA;
    private String name_ES;
    private String name_DE;
    private String name_IT;
    private String name_CN;
    private String name_KR;
    private String name_PT;

    private String price;

    private List<String> archetypes;

    private String artNumber;

    public Card(String Id, String artNumber) throws Exception {
        this(Id);
        this.artNumber = artNumber;

        //TODO after reformatting the database, modify and add all the other fields
        //this.imagePath = KonamiIdToImagePath.getKonamiIdToImagePath(this.konamiId, this.artNumber);
    }

    public Card() {

    }

    public Card(String id) throws Exception {
        Card tempCard = CardFactory.createCard(id);
        if (tempCard != null) {
            this.konamiId = tempCard.konamiId;
            this.passCode = tempCard.passCode;
            this.printCode = tempCard.printCode;
            this.imagePath = tempCard.imagePath;
            this.cardType = tempCard.cardType;
            this.cardProperties = tempCard.cardProperties;
            this.monsterType = tempCard.monsterType;
            this.atk = tempCard.atk;
            this.def = tempCard.def;
            this.level = tempCard.level;
            this.rank = tempCard.rank;
            this.attribute = tempCard.attribute;
            this.linkVal = tempCard.linkVal;
            this.linkMarker = tempCard.linkMarker;
            this.scale = tempCard.scale;
            this.price = tempCard.price;
            this.name_EN = tempCard.name_EN;
            this.name_FR = tempCard.name_FR;
            this.name_JA = tempCard.name_JA;
            this.archetypes = tempCard.archetypes;
            this.artNumber = tempCard.artNumber;
        }
    }

    /**
     * @return the Konami ID of this card.
     */
    public String getKonamiId() {
        return konamiId;
    }

    /**
     * Sets the Konami ID for this card.
     *
     * @param konamiId the Konami ID to be set
     */
    public void setKonamiId(String konamiId) {
        this.konamiId = konamiId;
    }

    /**
     * Retrieves the passcode of this card.
     *
     * @return the passcode as a String
     */
    public String getPassCode() {
        return passCode;
    }

    /**
     * Sets the passcode of this card.
     *
     * @param passCode the passcode as a String
     */
    public void setPassCode(String passCode) {
        this.passCode = passCode;
    }

    /**
     * Retrieves the description of this card.
     *
     * @return the description as a String
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the description of this card.
     *
     * @param description the description of this card as a String
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Retrieves the print code of this card.
     *
     * @return the print code as a String
     */
    public String getPrintCode() {
        return printCode;
    }

    /**
     * Sets the print code for this card.
     *
     * @param printCode the print code as a String
     */
    public void setPrintCode(String printCode) {
        this.printCode = printCode;
    }

    /**
     * Retrieves the path to the image for this card.
     *
     * @return the path to the image as a String
     */
    public String getImagePath() {
        return imagePath;
    }

    /**
     * Sets the path to the image for this card.
     *
     * @param imagePath the image path as a String
     */
    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    /**
     * Retrieves the type of card.
     *
     * @return the type of card
     */
    public String getCardType() {
        return cardType;
    }

    /**
     * Sets the type of the card.
     *
     * @param cardType the type of the card as a String
     */
    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    /**
     * Retrieves the properties of the card.
     * <p>
     * This will return a list of Strings, each of which describes a property of the card.
     * For example, for a monster card, this might include the ATK and DEF values.
     * </p>
     *
     * @return a list of Strings describing the card's properties
     */
    public List<String> getCardProperties() {
        return cardProperties;
    }

    /**
     * Sets the properties of the card.
     * <p>
     * This will take a list of Strings, each of which describes a property of the card.
     * For example, for a monster card, this might include the ATK and DEF values.
     * </p>
     *
     * @param cardProperties a list of Strings describing the card's properties
     */
    public void setCardProperties(List<String> cardProperties) {
        this.cardProperties = cardProperties;
    }

    /**
     * Retrieves the monster type of this card.
     * <p>
     * This is the type of the monster, such as "Fiend" or "Warrior".
     * </p>
     * @return the monster type as a String
     */
    public String getMonsterType() {
        return monsterType;
    }

    /**
     * Sets the monster type for this card.
     * <p>
     * The monster type is a String that defines the category or classification
     * of the monster, such as "Fiend" or "Warrior".
     * </p>
     *
     * @param monsterType the type of the monster as a String
     */
    public void setMonsterType(String monsterType) {
        this.monsterType = monsterType;
    }

    /**
     * Retrieves the attack points of this card.
     *
     * @return the attack points as an integer
     */
    public int getAtk() {
        return atk;
    }

    /**
     * Sets the attack points for this card.
     * <p>
     * The attack points should be an integer value.
     * </p>
     *
     * @param atk the attack points to set
     */
    public void setAtk(int atk) {
        this.atk = atk;
    }

    /**
     * Retrieves the defense points of this card.
     *
     * @return the defense points as an integer
     */
    public int getDef() {
        return def;
    }

    /**
     * Sets the defense points for this card.
     * <p>
     * The defense points should be an integer value.
     * </p>
     *
     * @param def the defense points to set
     */
    public void setDef(int def) {
        this.def = def;
    }

    /**
     * Retrieves the level of this card.
     *
     * @return the level as an integer
     */
    public int getLevel() {
        return level;
    }

    /**
     * Sets the level of this card.
     * <p>
     * The level should be an integer value representing the card's level.
     * </p>
     *
     * @param level the level to set for this card
     */
    public void setLevel(int level) {
        this.level = level;
    }

    /**
     * Retrieves the rank of this card.
     * <p>
     * The rank should be an integer value representing the card's rank.
     * </p>
     * @return the rank of this card
     */
    public int getRank() {
        return rank;
    }

    /**
     * Sets the rank of this card.
     * <p>
     * The rank should be an integer value representing the card's rank.
     * </p>
     *
     * @param rank the rank to set for this card
     */
    public void setRank(int rank) {
        this.rank = rank;
    }

    /**
     * Retrieves the attribute of this card.
     * <p>
     * The attribute is a String that describes the card's elemental
     * property, such as "Light", "Dark", or "Fire".
     * </p>
     * @return the attribute of this card as a String
     */
    public String getAttribute() {
        return attribute;
    }

    /**
     * Sets the attribute of this card.
     * <p>
     * The attribute should be a String that describes the card's elemental
     * property, such as "Light", "Dark", or "Fire".
     * </p>
     *
     * @param attribute the attribute to set for this card
     */
    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    /**
     * Retrieves the link value of this card.
     * <p>
     * The link value should be an integer value representing the card's link value.
     * </p>
     * @return the link value of this card
     */
    public int getLinkVal() {
        return linkVal;
    }

    /**
     * Sets the link value for this card.
     * <p>
     * The link value should be an integer representing the card's link value,
     * which is typically used in the context of link summoning mechanics.
     * </p>
     *
     * @param linkVal the link value to set for this card
     */
    public void setLinkVal(int linkVal) {
        this.linkVal = linkVal;
    }

    /**
     * Retrieves the link markers of this card.
     *
     * <p>
     * Link markers are used to indicate the positions where other cards
     * can be linked in the context of link summoning mechanics.
     * </p>
     *
     * @return a list of strings representing the link markers
     */
    public List<String> getLinkMarker() {
        return linkMarker;
    }

    /**
     * Sets the link markers for this card.
     * <p>
     * The link markers should be a list of strings representing the
     * positions where other cards can be linked in the context of
     * link summoning mechanics.
     * </p>
     *
     * @param linkMarker the link markers to set for this card
     */
    public void setLinkMarker(List<String> linkMarker) {
        this.linkMarker = linkMarker;
    }

    /**
     * Retrieves the pendulum scale of this card.
     * <p>
     * The pendulum scale is an integer value representing the card's pendulum
     * scale, which is used in the context of pendulum summoning mechanics.
     * </p>
     * @return the pendulum scale of this card
     */
    public int getScale() {
        return scale;
    }

    /**
     * Sets the pendulum scale of this card.
     * <p>
     * The pendulum scale is an integer value representing the card's pendulum
     * scale, which is used in the context of pendulum summoning mechanics.
     * </p>
     *
     * @param scale the pendulum scale to set for this card
     */
    public void setScale(int scale) {
        this.scale = scale;
    }

    /**
     * Retrieves the price of this card.
     * <p>
     * The price should be a String that represents the cost of the card in
     * euros.
     * </p>
     * @return the price of this card as a String
     */
    public String getPrice() {
        return price;
    }

    /**
     * Sets the price of this card.
     * <p>
     * The price should be a String that represents the cost of the card in
     * euros.
     * </p>
     * @param price the price to set for this card
     */
    public void setPrice(String price) {
        this.price = price;
    }

    /**
     * Retrieves the French name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * French.
     * </p>
     * @return the French name of this card as a String
     */
    public String getName_FR() {
        return name_FR;
    }

    /**
     * Sets the French name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * French.
     * </p>
     * @param name_FR the name to set for this card
     */
    public void setName_FR(String name_FR) {
        this.name_FR = name_FR;
    }

    /**
     * Retrieves the English name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * English.
     * </p>
     * @return the English name of this card as a String
     */
    public String getName_EN() {
        return name_EN;
    }

    /**
     * Sets the English name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * English.
     * </p>
     * @param name_EN the name to set for this card
     */
    public void setName_EN(String name_EN) {
        this.name_EN = name_EN;
    }

    /**
     * Retrieves the Japanese name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * Japanese.
     * </p>
     * @return the Japanese name of this card as a String
     */
    public String getName_JA() {
        return name_JA;
    }

    /**
     * Sets the Japanese name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * Japanese.
     * </p>
     * @param name_JA the name to set for this card
     */
    public void setName_JA(String name_JA) {
        this.name_JA = name_JA;
    }

    /**
     * Retrieves the Spanish name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * Spanish.
     * </p>
     * @return the Spanish name of this card as a String
     */
    public String getName_ES() {
        return name_ES;
    }

    /**
     * Sets the Spanish name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * Spanish.
     * </p>
     * @param name_ES the name to set for this card
     */
    public void setName_ES(String name_ES) {
        this.name_ES = name_ES;
    }

    /**
     * Retrieves the German name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * German.
     * </p>
     * @return the German name of this card as a String
     */
    public String getName_DE() {
        return name_DE;
    }

    /**
     * Sets the German name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * German.
     * </p>
     * @param name_DE the name to set for this card
     */
    public void setName_DE(String name_DE) {
        this.name_DE = name_DE;
    }

    /**
     * Retrieves the Italian name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * Italian.
     * </p>
     * @return the Italian name of this card as a String
     */
    public String getName_IT() {
        return name_IT;
    }

    /**
     * Sets the Italian name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * Italian.
     * </p>
     * @param name_IT the name to set for this card
     */
    public void setName_IT(String name_IT) {
        this.name_IT = name_IT;
    }

    /**
     * Retrieves the Chinese name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * Chinese.
     * </p>
     * @return the Chinese name of this card as a String
     */
    public String getName_CN() {
        return name_CN;
    }

    /**
     * Sets the Chinese name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * Chinese.
     * </p>
     * @param name_CN the name to set for this card
     */
    public void setName_CN(String name_CN) {
        this.name_CN = name_CN;
    }

    /**
     * Retrieves the Korean name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * Korean.
     * </p>
     * @return the Korean name of this card as a String
     */
    public String getName_KR() {
        return name_KR;
    }

    /**
     * Sets the Korean name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * Korean.
     * </p>
     * @param name_KR the name to set for this card
     */
    public void setName_KR(String name_KR) {
        this.name_KR = name_KR;
    }

    /**
     * Retrieves the Portuguese name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * Portuguese.
     * </p>
     * @return the Portuguese name of this card as a String
     */
    public String getName_PT() {
        return name_PT;
    }

    /**
     * Sets the Portuguese name of this card.
     * <p>
     * The name should be a String that represents the name of the card in
     * Portuguese.
     * </p>
     * @param name_PT the name to set for this card
     */
    public void setName_PT(String name_PT) {
        this.name_PT = name_PT;
    }

    /**
     * Retrieves the artwork number of this card.
     * <p>
     * The artwork number should be a String that represents the artwork
     * number of the card.
     * </p>
     * @return the artwork number of this card as a String
     */
    public String getArtNumber() {
        return artNumber;
    }

    /**
     * Sets the artwork number of this card.
     * <p>
     * The artwork number should be a String that represents the artwork
     * number of the card.
     * </p>
     * @param artNumber the artwork number to set for this card
     */
    public void setArtNumber(String artNumber) {
        this.artNumber = artNumber;
    }

    /**
     * Retrieves the archetypes of this card.
     * <p>
     * The archetypes are a List of Strings that represent the archetypes of
     * the card.
     * </p>
     * @return the archetypes of this card as a List of Strings
     */
    public List<String> getArchetypes() {
        return archetypes;
    }

    /**
     * Sets the archetypes for this card.
     * <p>
     * The archetypes are represented as a List of Strings, where each String
     * corresponds to an archetype associated with the card.
     * </p>
     * @param archetypes a List of Strings representing the archetypes to set for this card
     */
    public void setArchetypes(List<String> archetypes) {
        this.archetypes = archetypes;
    }

    /**
     * Returns the name of the card if it exists, otherwise the print code, then the pass code, then the Konami ID.
     * <p>
     * If none of the above fields exist, returns an empty string.
     * </p>
     * @return the name or number of this card as a String
     */
    public String getNameOrNumber() {
        String returnValue = "";
        if (this.name_EN != null) {
            if (!this.name_EN.isEmpty()) {
                returnValue = this.name_EN;
            }
        }
        if (returnValue.isEmpty() && this.printCode != null) {
            if (!this.printCode.isEmpty()) {
                returnValue = this.printCode;
            }
        }
        if (returnValue.isEmpty() && this.passCode != null) {
            if (!this.passCode.isEmpty()) {
                returnValue = this.passCode;
            }
        }
        if (returnValue.isEmpty() && this.konamiId != null) {
            if (!this.konamiId.isEmpty()) {
                returnValue = this.konamiId;
            }
        }

        return returnValue;
    }

    /**
     * Returns a string representation of this card.
     * <p>
     * The representation will typically contain the English name of the card
     * followed by the print code, pass code, and Konami ID, separated by
     * dashes. If any of these fields are null or empty, they will not be
     * included in the representation.
     * </p>
     * @return a string representation of this card
     */
    public String toString() {
        String returnValue = "";

        if (this.name_EN != null) {
            returnValue += this.name_EN;
        }

        if (this.printCode != null) {
            returnValue += " - " + this.printCode;
        }

        if (this.passCode != null) {
            returnValue += " - " + this.passCode;
        }

        if (this.konamiId != null) {
            returnValue += " - " + this.konamiId;
        }

        return returnValue;
    }

    /**
     * Returns a hash code for this card.
     * <p>
     * The hash code is based on the image path for this card. This is
     * sufficient since two cards with the same image path are considered
     * equal according to the {@link #equals(Object)} method.
     * </p>
     *
     * @return a hash code for this card
     */
    @Override
    public int hashCode() {
        return Objects.hash(imagePath);
    }
}