package Model.CardsLists;

import java.io.File;
import java.util.*;

public class DecksAndCollectionsList {
    private List<Deck> decks;
    private List<ThemeCollection> collections;

    public DecksAndCollectionsList(String dirPath) throws Exception {
        this.decks = new ArrayList<>();
        this.collections = new ArrayList<>();
        File dir = new File(dirPath);

        // First, import the collections
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.getPath().endsWith(".ytc")) {
                this.collections.add(new ThemeCollection(file.getPath()));
            }
        }

        // Build the set of deck names already linked to a collection,
        // so they are not imported again as standalone decks.
        Set<String> linkedDeckNames = new HashSet<>();
        for (ThemeCollection collection : this.collections) {
            for (List<Deck> unit : collection.getLinkedDecks()) {
                for (Deck linkedDeck : unit) {
                    linkedDeckNames.add(linkedDeck.getName());
                }
            }
        }

        // Then import standalone decks, excluding those already linked
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.getPath().endsWith(".ydk")) {
                Deck deck = new Deck(file.getPath());
                if (!linkedDeckNames.contains(deck.getName())) {
                    this.decks.add(deck);
                }
            }
        }
    }

    public DecksAndCollectionsList() {
        this.decks = new ArrayList<>();
        this.collections = new ArrayList<>();
    }

    /**
     * Returns all standalone decks (those not linked to any collection).
     */
    public List<Deck> getDecks() {
        return decks;
    }

    public void setDecks(List<Deck> decks) {
        this.decks = decks;
    }

    /**
     * Returns all theme collections.
     */
    public List<ThemeCollection> getCollections() {
        return collections;
    }

    public void setCollections(List<ThemeCollection> collections) {
        this.collections = collections;
    }

    /**
     * Returns a flat list of every CardElement across all collections and standalone decks.
     * <p>
     * The merge logic handles "loose" collections (those with connectToWholeCollection = true)
     * separately: their cards are de-duplicated against the rest of the list, with
     * specific-artwork entries taking priority.
     * </p>
     */
    public List<CardElement> toList() throws Exception {
        List<CardElement> result = new ArrayList<>();

        if (this.collections == null) {
            this.collections = new ArrayList<>();
        }
        if (this.decks == null) {
            this.decks = new ArrayList<>();
        }

        // 1) Non-loose collections: add their explicit card lists directly.
        for (ThemeCollection collection : this.collections) {
            if (collection == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(collection.getConnectToWholeCollection())) {
                List<CardElement> collectionCards = collection.toList();
                if (collectionCards != null) {
                    result.addAll(collectionCards);
                }
            }
        }

        // 2) Standalone decks (constructor already excluded decks linked to collections).
        for (Deck deck : this.decks) {
            if (deck == null) {
                continue;
            }
            List<CardElement> deckCards = deck.toList();
            if (deckCards != null) {
                result.addAll(deckCards);
            }
        }

        // Build quick lookup sets of print/pass codes already present in the result.
        Set<String> presentPrintCodes = new HashSet<>();
        Set<String> presentPassCodes = new HashSet<>();
        for (CardElement cardElement : result) {
            if (cardElement == null || cardElement.getCard() == null) {
                continue;
            }
            String printCode = cardElement.getCard().getPrintCode();
            String passCode = cardElement.getCard().getPassCode();
            if (printCode != null) {
                presentPrintCodes.add(printCode);
            }
            if (passCode != null) {
                presentPassCodes.add(passCode);
            }
        }

        // 3) Loose collections, first pass: add cards that require a specific artwork.
        for (ThemeCollection collection : this.collections) {
            if (collection == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(collection.getConnectToWholeCollection())) {
                continue;
            }

            List<CardElement> collectionCards = collection.getCardsList();
            if (collectionCards == null) {
                continue;
            }

            for (CardElement cardElement : collectionCards) {
                if (cardElement == null || cardElement.getCard() == null) {
                    continue;
                }
                if (!cardElement.getSpecificArtwork()) {
                    continue;
                }

                String printCode = cardElement.getCard().getPrintCode();
                String passCode = cardElement.getCard().getPassCode();
                boolean alreadyPresent = (printCode != null && presentPrintCodes.contains(printCode))
                        || (passCode != null && presentPassCodes.contains(passCode));

                if (!alreadyPresent) {
                    result.add(cardElement);
                    if (printCode != null) {
                        presentPrintCodes.add(printCode);
                    }
                    if (passCode != null) {
                        presentPassCodes.add(passCode);
                    }
                }
            }
        }

        // 4) Loose collections, second pass: add remaining cards (non-specific artwork).
        for (ThemeCollection collection : this.collections) {
            if (collection == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(collection.getConnectToWholeCollection())) {
                continue;
            }

            List<CardElement> collectionCards = collection.getCardsList();
            if (collectionCards == null) {
                continue;
            }

            for (CardElement cardElement : collectionCards) {
                if (cardElement == null || cardElement.getCard() == null) {
                    continue;
                }

                String printCode = cardElement.getCard().getPrintCode();
                String passCode = cardElement.getCard().getPassCode();
                boolean alreadyPresent = (printCode != null && presentPrintCodes.contains(printCode))
                        || (passCode != null && presentPassCodes.contains(passCode));

                if (!alreadyPresent) {
                    result.add(cardElement);
                    if (printCode != null) {
                        presentPrintCodes.add(printCode);
                    }
                    if (passCode != null) {
                        presentPassCodes.add(passCode);
                    }
                } else if (cardElement.getDontRemove()) {
                    // This card is explicitly marked dontRemove: ensure the collection's
                    // CardElement replaces any deck-provided entry for the same card.
                    for (int index = 0; index < result.size(); index++) {
                        CardElement existing = result.get(index);
                        if (existing == null || existing.getCard() == null) {
                            continue;
                        }
                        boolean matchByPrint = (printCode != null
                                && printCode.equals(existing.getCard().getPrintCode()));
                        boolean matchByPass = (passCode != null
                                && passCode.equals(existing.getCard().getPassCode()));
                        if ((matchByPrint || matchByPass) && existing != cardElement) {
                            result.set(index, cardElement);
                            break;
                        }
                    }
                }
            }
        }

        return result;
    }

    /**
     * Returns a flat list of all cards from collections and their linked decks,
     * excluding standalone decks.
     */
    public List<CardElement> toListCollectionsAndLinkedDecks() {
        List<CardElement> result = new ArrayList<>();
        for (ThemeCollection collection : this.getCollections()) {
            result.addAll(collection.toList());
        }
        return result;
    }

    /**
     * Adds a standalone deck to this list.
     */
    public void addDeck(Deck deckToAdd) {
        this.decks.add(deckToAdd);
    }

    /**
     * Adds a collection to this list.
     */
    public void addCollection(ThemeCollection collectionToAdd) {
        this.collections.add(collectionToAdd);
    }

    /**
     * Returns the total number of cards across all collections and standalone decks.
     */
    public int getCardCount() {
        return getCollectionsCardCount() + getDecksCardCount();
    }

    /**
     * Returns the combined price of all cards across all collections and standalone decks.
     */
    public String getPrice() {
        float total = Float.parseFloat(getCollectionsPrice()) + Float.parseFloat(getDecksPrice());
        return String.valueOf(total);
    }

    /**
     * Returns the total number of cards across all collections.
     */
    public int getCollectionsCardCount() {
        int count = 0;
        for (ThemeCollection collection : this.collections) {
            count += collection.getCardCount();
        }
        return count;
    }

    /**
     * Returns the combined price of all cards across all collections.
     */
    public String getCollectionsPrice() {
        float total = 0;
        for (ThemeCollection collection : this.collections) {
            total += Float.parseFloat(collection.getPrice());
        }
        return String.valueOf(total);
    }

    /**
     * Returns the total number of cards across all standalone decks.
     */
    public int getDecksCardCount() {
        int count = 0;
        for (Deck deck : this.decks) {
            count += deck.getCardCount();
        }
        return count;
    }

    /**
     * Returns the combined price of all cards across all standalone decks.
     */
    public String getDecksPrice() {
        float total = 0;
        for (Deck deck : this.decks) {
            total += Float.parseFloat(deck.getPrice());
        }
        return String.valueOf(total);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Deck deck : this.decks) {
            sb.append(deck.toString()).append('\n');
        }
        return sb.toString();
    }
}