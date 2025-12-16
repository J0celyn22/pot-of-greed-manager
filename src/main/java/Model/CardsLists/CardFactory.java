package Model.CardsLists;

import java.util.ArrayList;

import static Model.Database.CardDatabaseManager.getKonamiIdToPassCode;
import static Model.Database.Database.getAllCardsList;
import static Model.Database.PrintCodeToKonamiId.getPrintCodeToKonamiId;

public class CardFactory {
    /**
     * Creates a new Card object based on the provided passCode by copying
     * properties from the original Card found in the all cards list.
     *
     * @param passCode the passcode of the card to be created
     * @return a new Card object with properties copied from the original card,
     * or null if the original card does not exist or an error occurs
     * @throws Exception if there is an issue during the card creation process
     */
    public static Card CreateCardFromPassCode(String passCode) {
        try {
            Card originalCard = getAllCardsList().get(Integer.valueOf(passCode));
            if (originalCard == null) {
                return null; // or throw an exception if preferred
            }
            Card newCard = new Card();

            try {
                if (originalCard.getKonamiId() != null) newCard.setKonamiId(originalCard.getKonamiId());
                newCard.setPassCode(passCode);
                if (originalCard.getPrintCode() != null) newCard.setPrintCode(originalCard.getPrintCode());
                if (originalCard.getImagePath() != null) newCard.setImagePath(originalCard.getImagePath());
                if (originalCard.getCardType() != null) newCard.setCardType(originalCard.getCardType());
                if (originalCard.getCardProperties() != null)
                    newCard.setCardProperties(new ArrayList<>(originalCard.getCardProperties()));
                if (originalCard.getMonsterType() != null) newCard.setMonsterType(originalCard.getMonsterType());
                newCard.setAtk(originalCard.getAtk());
                newCard.setDef(originalCard.getDef());
                newCard.setLevel(originalCard.getLevel());
                newCard.setRank(originalCard.getRank());
                if (originalCard.getAttribute() != null) newCard.setAttribute(originalCard.getAttribute());
                newCard.setLinkVal(originalCard.getLinkVal());
                if (originalCard.getLinkMarker() != null)
                    newCard.setLinkMarker(new ArrayList<>(originalCard.getLinkMarker()));
                newCard.setScale(originalCard.getScale());
                if (originalCard.getPrice() != null) newCard.setPrice(originalCard.getPrice());
                if (originalCard.getName_EN() != null) newCard.setName_EN(originalCard.getName_EN());
                if (originalCard.getName_FR() != null) newCard.setName_FR(originalCard.getName_FR());
                if (originalCard.getName_JA() != null) newCard.setName_JA(originalCard.getName_JA());
                if (originalCard.getArchetypes() != null)
                    newCard.setArchetypes(new ArrayList<>(originalCard.getArchetypes()));
                if (originalCard.getArtNumber() != null) newCard.setArtNumber(originalCard.getArtNumber());
            } catch (Exception e) {
                System.out.println(newCard);
            }
            return newCard;
        } catch (Exception e) {
            System.out.println("Error during the Card creation");
            return null;
        }
    }


    /**
     * Creates a new Card object based on the provided print code by copying
     * properties from the original Card found in the all cards list.
     *
     * @param printCode the print code of the card to be created
     * @return a new Card object with properties copied from the original card,
     *         or null if the original card does not exist or an error occurs
     * @throws Exception if there is an issue during the card creation process
     */
    public static Card CreateCardFromPrintCode(String printCode) throws Exception {
        String id = getPrintCodeToKonamiId().get(printCode);
        if (id == null || id.equals("null")) {
            //throw new IllegalArgumentException("Invalid print code: " + printCode);
            System.out.println("Invalid print code: " + printCode);
            return null;
        }

        String passCode = String.valueOf(getKonamiIdToPassCode().get(Integer.valueOf(id)));
        if (passCode == null || passCode.equals("null")) {
            //throw new IllegalArgumentException("Card passcode is null for printcode " + printCode);
            System.out.println("Card passcode is null for printcode " + printCode);
        }

        return CreateCardFromPassCode(passCode);
    }


    /**
     * Creates a new Card object based on the provided id.
     * If the id is in the format of a print code, it will be used to create a new Card object.
     * If the id is a passcode, it will be used to create a new Card object.
     * If the id is null, the method will return null and print a message to the console.
     *
     * @param id the id of the card to be created
     * @return a new Card object with properties copied from the original card,
     *         or null if the original card does not exist or an error occurs
     * @throws Exception if there is an issue during the card creation process
     */
    public static Card createCard(String id) throws Exception {
        if (id != null) {
            if (id.contains("-") && !id.startsWith("-")) {
                return CreateCardFromPrintCode(id);
            } else {
                return CreateCardFromPassCode(id);
            }
        } else {
            System.out.println("Card id is null");
            //return new Card();
            return null;
        }
    }
}

