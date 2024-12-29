package Model.CardsLists;

import java.util.ArrayList;

import static Model.Database.Database.getAllCardsList;
import static Model.Database.CardDatabaseManager.getKonamiIdToPassCode;
import static Model.Database.PrintCodeToKonamiId.getPrintCodeToKonamiId;

public class CardFactory {
    public static Card CreateCardFromPassCode(String passCode) throws Exception {
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
                if (originalCard.getCardProperties() != null) newCard.setCardProperties(new ArrayList<>(originalCard.getCardProperties()));
                if (originalCard.getMonsterType() != null) newCard.setMonsterType(originalCard.getMonsterType());
                newCard.setAtk(originalCard.getAtk());
                newCard.setDef(originalCard.getDef());
                newCard.setLevel(originalCard.getLevel());
                newCard.setRank(originalCard.getRank());
                if (originalCard.getAttribute() != null) newCard.setAttribute(originalCard.getAttribute());
                newCard.setLinkVal(originalCard.getLinkVal());
                if (originalCard.getLinkMarker() != null) newCard.setLinkMarker(new ArrayList<>(originalCard.getLinkMarker()));
                newCard.setScale(originalCard.getScale());
                if (originalCard.getPrice() != null) newCard.setPrice(originalCard.getPrice());
                if (originalCard.getName_EN() != null) newCard.setName_EN(originalCard.getName_EN());
                if (originalCard.getName_FR() != null) newCard.setName_FR(originalCard.getName_FR());
                if (originalCard.getName_JA() != null) newCard.setName_JA(originalCard.getName_JA());
                if (originalCard.getArchetypes() != null) newCard.setArchetypes(new ArrayList<>(originalCard.getArchetypes()));
                if (originalCard.getArtNumber() != null) newCard.setArtNumber(originalCard.getArtNumber());
            }
            catch (Exception e) {
                System.out.println(newCard);
            }
            return newCard;
        }
        catch (Exception e) {
            System.out.println("Error during the Card creation");
            return null;
        }
    }


    public static Card CreateCardFromPrintCode(String printCode) throws Exception {
        String id = getPrintCodeToKonamiId().get(printCode);
        if (id == null || id.equals("null")) {
            //throw new IllegalArgumentException("Invalid print code: " + printCode);
            System.out.println("Invalid print code: " + printCode);
            return null;
        }

        String passCode = String.valueOf(getKonamiIdToPassCode().get(Integer.valueOf(id)));
        if (passCode == null || passCode.equals("null")) {
            throw new IllegalArgumentException("Card passcode is null for printcode " + printCode);
        }

        return CreateCardFromPassCode(passCode);
    }


    public static Card createCard(String id) throws Exception {
        if(id != null) {
            if (id.contains("-") && !id.startsWith("-")) {
                return CreateCardFromPrintCode(id);
            } else {
                return CreateCardFromPassCode(id);
            }
        }
        else {
            System.out.println("Card id is null");
            //return new Card();
            return null;
        }
    }
}

