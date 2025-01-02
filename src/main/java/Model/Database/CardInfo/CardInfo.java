package Model.Database.CardInfo;

import java.util.List;

public class CardInfo {
    private int passCode;
    private List<CardImage> cardImages;
    private String name;
    private String archetype;

    /**
     * Returns the passcode of this card.
     *
     * @return the passcode of this card as an int
     */
    public int getPassCode() {
        return passCode;
    }

    /**
     * Sets the passcode for this card.
     *
     * @param passCode the passcode to set as an int
     */
    public void setPassCode(int passCode) {
        this.passCode = passCode;
    }

    /**
     * Returns the name of this card.
     *
     * @return the name of this card as a String
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this card.
     * <p>
     * The name should be a String that represents the card's name.
     * </p>
     *
     * @param name the name to set for this card
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the archetype of this card.
     *
     * @return the archetype of this card as a String
     */
    public String getArchetype() {
        return archetype;
    }

    /**
     * Sets the archetype of this card.
     * <p>
     * The archetype is a String representing the archetype of the card.
     * </p>
     *
     * @param archetype the archetype to set for this card
     */
    public void setArchetype(String archetype) {
        this.archetype = archetype;
    }

    /**
     * Retrieves the list of card images associated with this card.
     *
     * @return a list of CardImage objects representing the images of this card
     */
    public List<CardImage> getCardImages() {
        return cardImages;
    }

    /**
     * Sets the list of card images associated with this card.
     * <p>
     * The list should contain CardImage objects representing the images of this card.
     * </p>
     * @param cardImages the list of CardImage objects to set for this card
     */
    public void setCardImages(List<CardImage> cardImages) {
        this.cardImages = cardImages;
    }
}
