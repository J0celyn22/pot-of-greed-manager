package Model.CardsLists;

import java.util.List;
import java.util.Objects;

/**
 * Card object
 */
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

    public String getKonamiId() {
        return konamiId;
    }

    public void setKonamiId(String konamiId) {
        this.konamiId = konamiId;
    }

    public String getPassCode() {
        return passCode;
    }

    public void setPassCode(String passCode) {
        this.passCode = passCode;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public void setCardProperties(List<String> cardProperties) {
        this.cardProperties = cardProperties;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setMonsterType(String monsterType) {
        this.monsterType = monsterType;
    }

    public void setAtk(int atk) {
        this.atk = atk;
    }

    public void setDef(int def) {
        this.def = def;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public void setLinkVal(int linkVal) {
        this.linkVal = linkVal;
    }

    public void setLinkMarker(List<String> linkMarker) {
        this.linkMarker = linkMarker;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public String getPrintCode() {
        return printCode;
    }

    public void setPrintCode(String printCode) {
        this.printCode = printCode;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getCardType() {
        return cardType;
    }

    public List<String> getCardProperties() {
        return cardProperties;
    }

    public String getMonsterType() {
        return monsterType;
    }

    public int getAtk() {
        return atk;
    }

    public int getDef() {
        return def;
    }

    public int getLevel() {
        return level;
    }

    public int getRank() {
        return rank;
    }

    public String getAttribute() {
        return attribute;
    }

    public int getLinkVal() {
        return linkVal;
    }

    public List<String> getLinkMarker() {
        return linkMarker;
    }

    public int getScale() {
        return scale;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getName_FR() {
        return name_FR;
    }

    public void setName_FR(String name_FR) {
        this.name_FR = name_FR;
    }

    public String getName_EN() {
        return name_EN;
    }

    public void setName_EN(String name_EN) {
        this.name_EN = name_EN;
    }

    public String getName_JA() {
        return name_JA;
    }

    public void setName_JA(String name_JA) {
        this.name_JA = name_JA;
    }

    public String getName_ES() {
        return name_ES;
    }

    public void setName_ES(String name_ES) {
        this.name_ES = name_ES;
    }

    public String getName_DE() {
        return name_DE;
    }

    public void setName_DE(String name_DE) {
        this.name_DE = name_DE;
    }

    public String getName_IT() {
        return name_IT;
    }

    public void setName_IT(String name_IT) {
        this.name_IT = name_IT;
    }

    public String getName_CN() {
        return name_CN;
    }

    public void setName_CN(String name_CN) {
        this.name_CN = name_CN;
    }

    public String getName_KR() {
        return name_KR;
    }

    public void setName_KR(String name_KR) {
        this.name_KR = name_KR;
    }

    public String getName_PT() {
        return name_PT;
    }

    public void setName_PT(String name_PT) {
        this.name_PT = name_PT;
    }

    public String getArtNumber() {
        return artNumber;
    }

    public void setArtNumber(String artNumber) {
        this.artNumber = artNumber;
    }

    public List<String> getArchetypes() {
        return archetypes;
    }

    public void setArchetypes(List<String> archetypes) {
        this.archetypes = archetypes;
    }

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
        if(tempCard != null) {
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

    public String getNameOrNumber() {
        String returnValue = "";
        if(this.name_EN != null) {
            if(this.name_EN != "") {
                returnValue = this.name_EN;
            }
        }
        if(returnValue == "" && this.printCode != null) {
            if(this.printCode != "") {
                returnValue = this.printCode;
            }
        }
        if(returnValue == "" && this.passCode != null) {
            if(this.passCode != "") {
                returnValue = this.passCode;
            }
        }
        if(returnValue == "" && this.konamiId != null) {
            if(this.konamiId != "") {
                returnValue = this.konamiId;
            }
        }

        return returnValue;
    }

    public String toString() {
        String returnValue = "";

        if(this.name_EN != null) {
            returnValue += this.name_EN;
        }

        if(this.printCode != null) {
            returnValue += " - " + this.printCode;
        }

        if(this.passCode != null) {
            returnValue += " - " + this.passCode;
        }

        if(this.konamiId != null) {
            returnValue += " - " + this.konamiId;
        }

        return returnValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(imagePath);
    }
}