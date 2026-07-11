package Model.CardsLists;

import Model.Database.CardDatabaseManager;
import Model.Database.Database;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static Model.CardsLists.ListDifferenceIntersection.ListDifIntersectArtworkWithExceptions;

/**
 * Collection of cards with a theme.
 */
public class ThemeCollection {
    private String name;

    private List<CardElement> cardsList;

    private List<CardElement> exceptionsToNotAdd;

    private List<List<Deck>> linkedDecks;

    private List<String> archetypes;

    /**
     * If true, cards in this collection may reside in any other element of the collection to count as owned.
     */
    private Boolean connectToWholeCollection;

    public ThemeCollection(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        this.name = path.getFileName().toString().replaceFirst("[.][^.]+$", "");

        List<String> rawLines = Files.readAllLines(path);
        List<String> lines = new ArrayList<>(rawLines.size());
        for (String raw : rawLines) {
            if (raw == null) {
                lines.add(null);
                continue;
            }
            String cleaned = raw;
            if (!cleaned.isEmpty() && cleaned.charAt(0) == '\uFEFF') {
                cleaned = cleaned.substring(1);
            }
            cleaned = cleaned.replace("\uFEFF", "");
            lines.add(cleaned);
        }

        this.cardsList = new ArrayList<>();
        this.exceptionsToNotAdd = new ArrayList<>();
        this.linkedDecks = new ArrayList<>();
        this.archetypes = new ArrayList<>();

        // ── Loose flag ────────────────────────────────────────────────────────
        // "#Loose" as the very first line.
        if (!lines.isEmpty() && "#Loose".equals(lines.get(0))) {
            this.connectToWholeCollection = true;
            lines.remove(0);                        // consume header so card parsing is clean
        }

        // ── Cards list ────────────────────────────────────────────────────────
        // Stop at any section marker
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            if (line.equals("#Not to add")
                    || line.equals("#Linked decks")
                    || line.equals("#Archetypes")) {
                break;
            }
            if (line.contains(",")) {
                String[] cardInfo = line.split(",");
                cardsList.add(new CardElement(cardInfo[0]));
                int artworkNumber = Integer.parseInt(cardInfo[1].replace("*", "").trim());
                CardElement lastAdded = cardsList.get(cardsList.size() - 1);
                lastAdded.setArtwork(artworkNumber);
                lastAdded.setSpecificArtwork(true);
                if (artworkNumber > 1 && lastAdded.getCard() != null) {
                    updateCardToAlternateArtwork(lastAdded.getCard(), artworkNumber);
                }
            } else {
                cardsList.add(new CardElement(line));
            }
        }

        // ── #Not to add ───────────────────────────────────────────────────────
        int index = lines.indexOf("#Not to add");
        if (index != -1) {
            exceptionsToNotAdd = new ArrayList<>();
            for (int i = index + 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null) {
                    continue;
                }
                if (line.equals("#Linked decks") || line.equals("#Archetypes")) {
                    break;
                }
                exceptionsToNotAdd.add(new CardElement(line));
            }
        }

        // ── #Linked decks (non-loose only) ───────────────────────────────────
        linkedDecks = new ArrayList<>();
        if (!Boolean.TRUE.equals(connectToWholeCollection)) {
            index = lines.indexOf("#Linked decks");
            if (index != -1) {
                for (int i = index + 1; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line == null) {
                        continue;
                    }
                    if (line.equals("#Archetypes")) {
                        break;
                    }
                    if (line.equals("##")) {
                        this.linkedDecks.add(new ArrayList<>());
                    } else {
                        String deckPath = path.getParent().resolve(line + ".ydk").toString();
                        this.addDeck(new Deck(deckPath));
                    }
                }
            }
        }

        // ── #Archetypes ───────────────────────────────────────────────────────
        index = lines.indexOf("#Archetypes");
        if (index != -1) {
            for (int i = index + 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line == null) {
                    continue;
                }
                archetypes.add(line);
            }
        }
    }

    /**
     * Given a Card that was created from its base passCode (artwork 1) and a
     * requested artwork number, updates the card's imagePath and artNumber to
     * those of the requested alternate artwork.
     * <p>
     * Uses passCodeToOtherPassCodes: imageId → ordered list of all imageIds for
     * the same card.  The list is in API order, so index (artworkNumber-1) gives
     * the imageId for the requested artwork.
     */
    private static void updateCardToAlternateArtwork(Card card, int artworkNumber) {
        try {
            if (card.getPassCode() == null) return;
            Integer baseImageId = Integer.valueOf(card.getPassCode());

            Map<Integer, List<Integer>> otherPassCodes =
                    CardDatabaseManager.getPassCodeToOtherPassCodes();
            if (otherPassCodes == null) return;

            List<Integer> allImageIds = otherPassCodes.get(baseImageId);
            if (allImageIds == null || allImageIds.size() < artworkNumber) {
                return;  // Requested artwork doesn't exist in the database
            }

            Integer altImageId = allImageIds.get(artworkNumber - 1);  // 0-indexed
            if (altImageId == null) return;

            Map<Integer, Card> allCards = Database.getAllCardsList();
            if (allCards == null) return;

            Card altCard = allCards.get(altImageId);
            if (altCard != null) {
                card.setImagePath(altCard.getImagePath());
                card.setArtNumber(altCard.getArtNumber());
            }
        } catch (Exception ignored) {
            // If the lookup fails for any reason, keep the original artwork
        }
    }

    public ThemeCollection() {
        this.cardsList = new ArrayList<>();
        this.exceptionsToNotAdd = new ArrayList<>();
        this.linkedDecks = new ArrayList<>();
        this.archetypes = new ArrayList<>();
        this.connectToWholeCollection = false;
    }

    /**
     * Retrieves the name of this theme collection.
     * <p>
     * The name of the theme collection should be a String that represents the name of the theme collection.
     * </p>
     *
     * @return the name of this theme collection as a String
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this theme collection.
     * <p>
     * The name should be a String that represents the name of the theme collection.
     * </p>
     *
     * @param name the name to set for this theme collection
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Retrieves the list of all cards in this theme collection.
     * <p>
     * The returned list contains all cards that are part of this theme collection.
     * </p>
     *
     * @return the list of all cards in this theme collection
     */
    public List<CardElement> getCardsList() {
        return cardsList;
    }

    /**
     * Sets the list of cards in this theme collection.
     * <p>
     * The provided list of cards should contain all cards that are part of this theme collection.
     * </p>
     *
     * @param cardsList the list of cards to set for this theme collection
     */
    public void setCardsList(List<CardElement> cardsList) {
        this.cardsList = cardsList;
    }

    /**
     * Retrieves the list of exceptions to not add to this theme collection.
     * <p>
     * This list contains all cards that are explicitly not to be added to this theme collection.
     * </p>
     *
     * @return the list of exceptions to not add to this theme collection
     */
    public List<CardElement> getExceptionsToNotAdd() {
        return exceptionsToNotAdd;
    }

    /**
     * Sets the list of exceptions for cards that should not be added to this theme collection.
     * <p>
     * This method assigns the given list of {@link CardElement} objects to the exceptions list
     * for this theme collection. These cards will be explicitly excluded from being added to
     * the collection.
     * </p>
     *
     * @param exceptionsToNotAdd the list of {@link CardElement} objects to set as exceptions
     */
    public void setExceptionsToNotAdd(List<CardElement> exceptionsToNotAdd) {
        this.exceptionsToNotAdd = exceptionsToNotAdd;
    }

    /**
     * Retrieves the list of linked decks for this theme collection.
     * <p>
     * These decks are linked to the theme collection. Cards from these decks are
     * automatically added to the theme collection.
     * </p>
     *
     * @return the list of linked decks for this theme collection
     */
    public List<List<Deck>> getLinkedDecks() {
        return linkedDecks;
    }

    /**
     * Sets the list of linked decks for this theme collection.
     * <p>
     * The provided list of decks should contain all decks that are linked to this theme collection.
     * Cards from these decks are automatically added to the theme collection.
     * </p>
     *
     * @param linkedDecks the list of decks to set as linked decks for this theme collection
     */
    public void setLinkedDecks(List<List<Deck>> linkedDecks) {
        this.linkedDecks = linkedDecks;
    }

    /**
     * Retrieves a boolean indicating if this theme collection should connect to the whole collection of cards.
     * <p>
     * If this is set to true, the theme collection will automatically include all cards in the whole collection.
     * </p>
     *
     * @return true if this theme collection should connect to the whole collection, false if not
     */
    public Boolean getConnectToWholeCollection() {
        return connectToWholeCollection;
    }

    /**
     * Sets a boolean indicating if this theme collection should connect to the whole collection of cards.
     * <p>
     * If this is set to true, the theme collection will automatically include all cards in the whole collection.
     * </p>
     *
     * @param connectToWholeCollection true if this theme collection should connect to the whole collection, false if not
     */
    public void setConnectToWholeCollection(Boolean connectToWholeCollection) {
        this.connectToWholeCollection = connectToWholeCollection;
    }

    public List<String> getArchetypes() {
        return archetypes;
    }

    public void setArchetypes(List<String> archetypes) {
        this.archetypes = archetypes;
    }

    /**
     * Saves the theme collection to a file.
     * <p>
     * The file name is the name of the theme collection, followed by ".ytc".
     * The file is written in the following format:
     * <pre>
     * Card1
     * Card2
     * ...
     * Not to add
     * Card3
     * Card4
     * ...
     * Deck1
     * Deck2
     * ...
     * Archetype1
     * Archetype2
     * ...
     * </pre>
     * If the list of cards to not add is empty, the "Not to add" line is not written.
     * If the list of linked decks is empty, the cards are not written.
     * If the list of archetypes is empty, the archetypes are not written.
     * </p>
     * @param savePath the path to which to save the file
     * @throws IOException if there is an error writing to the file
     */
    public void saveToFile(String savePath) throws IOException {
        String dir = savePath.endsWith(File.separator)
                ? savePath : savePath + File.separator;
        Path path = Paths.get(dir).resolve(this.name + ".ytc");
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {

            // 1. Loose flag — always the very first line when present,
            //    before any card data so the parser knows what kind of
            //    collection this is without having to scan the whole file.
            if (Boolean.TRUE.equals(connectToWholeCollection)) {
                writer.write("#Loose");
                writer.newLine();
            }

            // 2. Cards list
            if (cardsList != null) {
                for (CardElement entry : cardsList) {
                    if (entry == null || entry.getCard() == null) continue;
                    writer.write(entry.toThemeCollectionString());
                    writer.newLine();
                }
            }

            // 3. Exceptions / cards not to add
            if (exceptionsToNotAdd != null && !exceptionsToNotAdd.isEmpty()) {
                writer.write("#Not to add");
                writer.newLine();
                for (CardElement card : exceptionsToNotAdd) {
                    if (card == null) continue;
                    writer.write(card.toThemeCollectionString());
                    writer.newLine();
                }
            }

            // 4. Linked decks (non-loose collections only)
            if (!Boolean.TRUE.equals(connectToWholeCollection)
                    && linkedDecks != null && !linkedDecks.isEmpty()) {
                writer.write("#Linked decks");
                writer.newLine();
                for (int i = 0; i < linkedDecks.size(); i++) {
                    List<Deck> unit = linkedDecks.get(i);
                    if (unit == null) continue;
                    for (Deck d : unit) {
                        if (d == null) continue;
                        writer.write(d.getName());
                        writer.newLine();
                    }
                    if (i < linkedDecks.size() - 1) {
                        writer.write("##");
                        writer.newLine();
                    }
                }
            }

            // 5. Archetypes
            if (archetypes != null && !archetypes.isEmpty()) {
                writer.write("#Archetypes");
                writer.newLine();
                for (String archetype : archetypes) {
                    if (archetype == null) continue;
                    writer.write(archetype);
                    writer.newLine();
                }
            }
        }
    }

    /**
     * Adds a deck to the list of linked decks in a new unit.
     *
     * @param deckToAdd the deck to add to the list of linked decks
     */
    public void addDeck(Deck deckToAdd) {
        this.linkedDecks.add(new ArrayList<>());
        this.linkedDecks.get(this.linkedDecks.size() - 1).add(deckToAdd);
    }


    /**
     * Adds a deck to the list of linked decks in an existing deck unit.
     *
     * @param deckToAdd the deck to add to the list of linked decks
     * @param unitIndex the index of the deck unit to add the deck to
     */
    public void addDeckToExistingUnit(Deck deckToAdd, int unitIndex) {
        this.linkedDecks.get(unitIndex).add(deckToAdd);
    }

    /**
     * Creates and updates the list of cards for this theme collection by performing
     * intersection operations with linked decks and applying exceptions.
     * <p>
     * This method first performs a difference-intersection operation with artwork exceptions
     * and then performs a difference-intersection with Konami ID exceptions. The resulting
     * list of cards, after applying these operations, becomes the new list of cards for
     * this theme collection.
     * </p>
     */
    public void createCardsList() {
        List<CardElement> linkedDecksList = new ArrayList<>();
        List<CardElement> tempCardsList;
        List<List<CardElement>> tempList;

        tempList = ListDifIntersectArtworkWithExceptions(cardsList, linkedDecksList, "D");
        tempCardsList = tempList.get(0);
        tempList = ListDifferenceIntersection.ListDifIntersectKonamiIdWithExceptions(tempCardsList, linkedDecksList, "D");

        this.cardsList = tempList.get(0);
    }

    /**
     * Retrieves the total number of cards in this theme collection.
     * <p>
     * The returned value is the total number of cards in the list of cards for
     * this theme collection.
     * </p>
     * @return the total number of cards in this theme collection
     */
    public int getCardCount() {
        return this.cardsList.size();
    }

    /**
     * Calculates the total price of all cards in this theme collection.
     * <p>
     * This method sums the prices of all cards in the list of cards for this theme collection.
     * </p>
     * @return the total price as a String
     */
    public String getPrice() {
        float price = 0;
        for (CardElement cardElement : this.cardsList) {
            if (cardElement.getCard() != null && cardElement.getPrice() != null) {
                price += Float.parseFloat(cardElement.getPrice());
            }
        }
        return String.valueOf(price);
    }

    /**
     * Returns a flat list of all cards in this collection, merging linked deck cards
     * with explicit collection entries.
     * <p>
     * For each linked-deck unit, the maximum number of copies of a card across all
     * decks in that unit is used. Collection entries with a specific artwork override
     * the deck-provided entry for the same card. Cards in the collection's explicit
     * list that were not covered by any linked deck are appended at the end. Cards
     * marked {@code dontRemove} are guaranteed to be represented by the collection's
     * own {@link CardElement} rather than a deck copy.
     * </p>
     */
    public List<CardElement> toList() {
        List<CardElement> result = new ArrayList<>();

        if (this.linkedDecks == null) {
            this.linkedDecks = new ArrayList<>();
        }
        if (this.cardsList == null) {
            this.cardsList = new ArrayList<>();
        }

        // Build quick lookup maps for collection cards.
        // For each printCode / passCode prefer a collection CardElement that has specificArtwork = true.
        // If none with specificArtwork, use any.
        Map<String, CardElement> collectionByPrint = new HashMap<>();
        Map<String, CardElement> collectionByPass = new HashMap<>();

        for (CardElement collectionElement : this.cardsList) {
            if (collectionElement == null || collectionElement.getCard() == null) {
                continue;
            }
            String printCode = collectionElement.getCard().getPrintCode();
            String passCode = collectionElement.getCard().getPassCode();

            if (printCode != null) {
                CardElement existing = collectionByPrint.get(printCode);
                if (existing == null || (!existing.getSpecificArtwork() && collectionElement.getSpecificArtwork())) {
                    collectionByPrint.put(printCode, collectionElement);
                }
            }
            if (passCode != null) {
                CardElement existing = collectionByPass.get(passCode);
                if (existing == null || (!existing.getSpecificArtwork() && collectionElement.getSpecificArtwork())) {
                    collectionByPass.put(passCode, collectionElement);
                }
            }
        }

        // Process linkedDecks by unit. Within a unit a card is added up to the maximum
        // number of copies any single deck in that unit contains (not summed across decks).
        for (List<Deck> unit : this.linkedDecks) {
            if (unit == null) {
                continue;
            }

            // Map key uses printCode if present, otherwise passCode. Prefix to avoid collisions.
            Map<String, Integer> maxCountByKey = new HashMap<>();
            // The actual distinct CardElement instances backing maxCountByKey's count, taken
            // from whichever deck set that max -- NOT a single "representative" object to be
            // repeated. Two identical physical cards within one deck are two separate slots
            // (and must stay two separate CardElement instances here, mirroring the real
            // Decks & Collections model), not the same instance duplicated in the list.
            Map<String, List<CardElement>> deckElementsByKey = new HashMap<>();

            // For each deck in the unit, compute counts of each card in that deck.
            for (Deck deck : unit) {
                if (deck == null) {
                    continue;
                }
                List<CardElement> deckCards = deck.toList();
                if (deckCards == null) {
                    continue;
                }

                Map<String, Integer> countThisDeck = new HashMap<>();
                Map<String, List<CardElement>> elementsThisDeck = new HashMap<>();
                for (CardElement deckCardElement : deckCards) {
                    if (deckCardElement == null || deckCardElement.getCard() == null) {
                        continue;
                    }
                    String dPrint = deckCardElement.getCard().getPrintCode();
                    String dPass = deckCardElement.getCard().getPassCode();
                    String key = (dPrint != null && !dPrint.isEmpty())
                            ? "P:" + dPrint
                            : (dPass != null ? "S:" + dPass : null);
                    if (key == null) {
                        continue;
                    }

                    countThisDeck.put(key, countThisDeck.getOrDefault(key, 0) + 1);
                    elementsThisDeck.computeIfAbsent(key, newKey -> new ArrayList<>()).add(deckCardElement);
                }

                for (Map.Entry<String, Integer> entry : countThisDeck.entrySet()) {
                    String key = entry.getKey();
                    int count = entry.getValue();
                    int previous = maxCountByKey.getOrDefault(key, 0);
                    if (count > previous) {
                        maxCountByKey.put(key, count);
                        deckElementsByKey.put(key, elementsThisDeck.get(key));
                    }
                }
            }

            // Add to result according to max counts, preferring collection elements with specific artwork.
            for (Map.Entry<String, Integer> entry : maxCountByKey.entrySet()) {
                String key = entry.getKey();
                int copies = entry.getValue();
                CardElement collectionElement = null;

                if (key.startsWith("P:")) {
                    collectionElement = collectionByPrint.get(key.substring(2));
                } else if (key.startsWith("S:")) {
                    collectionElement = collectionByPass.get(key.substring(2));
                }

                List<CardElement> deckElements = deckElementsByKey.getOrDefault(key, List.of());

                if (collectionElement != null) {
                    // First copy: prefer the collection element (may have specific artwork).
                    result.add(collectionElement);
                    // Remaining copies: use the deck's own distinct instances, one each --
                    // not the same collection element repeated.
                    for (int i = 1; i < copies; i++) {
                        int deckElementIndex = i - 1;
                        result.add(deckElementIndex < deckElements.size()
                                ? deckElements.get(deckElementIndex) : collectionElement);
                    }
                } else {
                    // No collection element: add each of the deck's own distinct instances.
                    for (int i = 0; i < copies && i < deckElements.size(); i++) {
                        result.add(deckElements.get(i));
                    }
                }
            }
        }

        // Append remaining collection cards not already covered by a linked deck.
        // Cards marked dontRemove are guaranteed to appear as the collection's own element.
        Set<String> presentPrintCodes = new HashSet<>();
        Set<String> presentPassCodes = new HashSet<>();
        for (CardElement resultElement : result) {
            if (resultElement == null || resultElement.getCard() == null) {
                continue;
            }
            if (resultElement.getCard().getPrintCode() != null) {
                presentPrintCodes.add(resultElement.getCard().getPrintCode());
            }
            if (resultElement.getCard().getPassCode() != null) {
                presentPassCodes.add(resultElement.getCard().getPassCode());
            }
        }

        for (CardElement collectionElement : this.cardsList) {
            if (collectionElement == null || collectionElement.getCard() == null) {
                continue;
            }
            String printCode = collectionElement.getCard().getPrintCode();
            String passCode = collectionElement.getCard().getPassCode();

            boolean alreadyPresent;
            if (collectionElement.getDontRemove()) {
                // dontRemove means "always show this exact collection card instead of
                // whatever deck placeholder occupies this card's slot" -- including when
                // that placeholder is a different print of the same card name. Match
                // loosely (print OR passCode) so the replace branch below can find and
                // override it.
                alreadyPresent = (printCode != null && presentPrintCodes.contains(printCode))
                        || (passCode != null && presentPassCodes.contains(passCode));
            } else {
                // Otherwise, only an exact printCode match (or, absent a printCode, an
                // exact passCode match) means "this specific physical card is already
                // accounted for." Two entries that merely share a passCode but carry
                // different printCodes are distinct prints/copies of the same card name
                // and must both appear -- falling back to passCode alone here was
                // silently dropping the second such collection-only entry as a spurious
                // duplicate.
                alreadyPresent = printCode != null
                        ? presentPrintCodes.contains(printCode)
                        : (passCode != null && presentPassCodes.contains(passCode));
            }

            if (!alreadyPresent) {
                result.add(collectionElement);
                if (printCode != null) {
                    presentPrintCodes.add(printCode);
                }
                if (passCode != null) {
                    presentPassCodes.add(passCode);
                }
            } else if (collectionElement.getDontRemove()) {
                // If dontRemove is set, replace the first matching result entry (which may be a
                // deck copy) with this collection element to honour the explicit card choice.
                for (int i = 0; i < result.size(); i++) {
                    CardElement existingEntry = result.get(i);
                    if (existingEntry == null || existingEntry.getCard() == null) {
                        continue;
                    }
                    boolean matchByPrint = (printCode != null
                            && printCode.equals(existingEntry.getCard().getPrintCode()));
                    boolean matchByPass = (passCode != null
                            && passCode.equals(existingEntry.getCard().getPassCode()));
                    if ((matchByPrint || matchByPass) && existingEntry != collectionElement) {
                        result.set(i, collectionElement);
                        break;
                    }
                }
            }
        }

        return result;
    }
}