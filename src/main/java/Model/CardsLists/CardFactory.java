package Model.CardsLists;

import java.util.ArrayList;

import static Model.Database.CardDatabaseManager.getKonamiIdToPassCode;
import static Model.Database.Database.getAllCardsList;
import static Model.Database.PrintCodeToKonamiId.getPrintCodeToKonamiId;

public class CardFactory {

    // ------------------------------------------------------------------
    // Internal helper
    // ------------------------------------------------------------------

    /**
     * Returns a minimal, non-null {@link Card} that carries {@code id} as its
     * only identifier. Used as a last-resort fallback when no database entry
     * can be found, so that the card still round-trips through save/load
     * correctly.
     *
     * @param id      the raw identifier typed by the user (print code,
     *                passcode, etc.)
     * @param isPrint {@code true} → store {@code id} as printCode;
     *                {@code false} → store it as passCode
     */
    private static Card stubCard(String id, boolean isPrint) {
        Card stub = new Card();
        if (isPrint) {
            stub.setPrintCode(id);
        } else {
            stub.setPassCode(id);
        }
        return stub;
    }

    // ------------------------------------------------------------------
    // Public factory methods
    // ------------------------------------------------------------------

    /**
     * Creates a {@link Card} from a passcode.
     *
     * <p>If the passcode cannot be parsed as an integer, or if no matching
     * entry exists in the card database, a minimal stub card is returned that
     * carries the raw passcode so that the element round-trips correctly
     * through save/load. The method therefore <em>never</em> returns
     * {@code null}.
     *
     * @param passCode the passcode of the card to create
     * @return a fully-populated card when the database entry exists, or a
     *         stub card with only the passcode set when it does not
     */
    public static Card CreateCardFromPassCode(String passCode) {
        // Guard: unparseable passcode (e.g. "null" string, empty, etc.)
        if (passCode == null || passCode.isEmpty()) {
            System.out.println("CreateCardFromPassCode: passcode is null/empty");
            return new Card(); // nothing useful to store
        }

        int numericPassCode;
        try {
            numericPassCode = Integer.parseInt(passCode);
        } catch (NumberFormatException e) {
            System.out.println("CreateCardFromPassCode: unparseable passcode '" + passCode + "'");
            return stubCard(passCode, false);
        }

        Card originalCard;
        try {
            originalCard = getAllCardsList().get(numericPassCode);
        } catch (Exception e) {
            System.out.println("CreateCardFromPassCode: database error for passcode " + passCode
                    + " — " + e.getMessage());
            return stubCard(passCode, false);
        }

        if (originalCard == null) {
            System.out.println("CreateCardFromPassCode: no database entry for passcode " + passCode);
            return stubCard(passCode, false);
        }

        // Happy path — copy all available fields.
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
            if (originalCard.getAvailableRarities() != null)
                newCard.setAvailableRarities(new ArrayList<>(originalCard.getAvailableRarities()));
        } catch (Exception e) {
            // Partial copy — at least the passcode is already set on newCard.
            System.out.println("CreateCardFromPassCode: partial copy for passcode "
                    + passCode + " — " + e.getMessage());
        }
        return newCard;
    }


    /**
     * Creates a {@link Card} from a print code (e.g. {@code "LOB-EN001"}).
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Look up the Konami ID from the print-code map.</li>
     *   <li>Look up the passcode from the Konami-ID map.</li>
     *   <li>Delegate to {@link #CreateCardFromPassCode(String)} for the full copy.</li>
     *   <li>Restore the original print code on the result.</li>
     * </ol>
     *
     * <p>At any step that fails, a stub card is returned that carries at least
     * the print code (and the Konami ID when it is known), so the element
     * round-trips correctly. The method <em>never</em> returns {@code null}.
     *
     * @param printCode the print code of the card to create
     * @return a fully-populated card, a partially-populated card, or a stub —
     *         always non-null
     */
    public static Card CreateCardFromPrintCode(String printCode) throws Exception {
        // Step 1 — resolve print code → Konami ID
        String konamiIdStr;
        try {
            konamiIdStr = getPrintCodeToKonamiId().get(printCode);
        } catch (Exception e) {
            System.out.println("CreateCardFromPrintCode: database error for print code '"
                    + printCode + "' — " + e.getMessage());
            return stubCard(printCode, true);
        }

        if (konamiIdStr == null || konamiIdStr.equals("null")) {
            System.out.println("CreateCardFromPrintCode: print code not in database: " + printCode);
            return stubCard(printCode, true);
        }

        // Step 2 — resolve Konami ID → passcode
        Integer numericKonamiId;
        try {
            numericKonamiId = Integer.valueOf(konamiIdStr);
        } catch (NumberFormatException e) {
            System.out.println("CreateCardFromPrintCode: unparseable Konami ID '"
                    + konamiIdStr + "' for print code " + printCode);
            Card stub = stubCard(printCode, true);
            stub.setKonamiId(konamiIdStr);
            return stub;
        }

        Integer passCodeInt;
        try {
            passCodeInt = getKonamiIdToPassCode().get(numericKonamiId);
        } catch (Exception e) {
            System.out.println("CreateCardFromPrintCode: database error resolving Konami ID "
                    + konamiIdStr + " — " + e.getMessage());
            Card stub = stubCard(printCode, true);
            stub.setKonamiId(konamiIdStr);
            return stub;
        }

        if (passCodeInt == null) {
            // Konami ID is known but not yet mapped to a passcode.
            System.out.println("CreateCardFromPrintCode: no passcode for Konami ID "
                    + konamiIdStr + " (print code " + printCode + ")");
            Card stub = stubCard(printCode, true);
            stub.setKonamiId(konamiIdStr);
            return stub;
        }

        // Step 3 — full card from passcode, then restore the print code.
        Card card = CreateCardFromPassCode(String.valueOf(passCodeInt));
        // CreateCardFromPassCode is always non-null; set the print code regardless
        // of whether it was a full copy or a stub.
        card.setPrintCode(printCode);
        return card;
    }


    /**
     * Creates a {@link Card} from any identifier string.
     *
     * <p>If the string contains a {@code '-'} that is not a leading minus sign
     * it is treated as a print code; otherwise it is treated as a passcode.
     * If {@code id} is {@code null} an empty card is returned so that callers
     * never receive {@code null}.
     *
     * @param id the identifier to resolve (print code or passcode)
     * @return a non-null {@link Card}; may be a stub when the identifier is
     *         unknown to the database
     */
    public static Card createCard(String id) throws Exception {
        if (id == null) {
            System.out.println("createCard: id is null — returning empty card");
            return new Card();
        }
        if (id.contains("-") && !id.startsWith("-")) {
            return CreateCardFromPrintCode(id);
        } else {
            return CreateCardFromPassCode(id);
        }
    }
}