package Model.CardsLists;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static Model.CardsLists.ListDifferenceIntersection.*;

public class OuicheList {
    private static OwnedCardsCollection myCardsCollection;
    private static DecksAndCollectionsList decksList;

    // The canonical compact form of the OuicheList ("maOuicheList" is a word play on "wishlist").
    // Key = passCode if available, otherwise imagePath.
    // Storing the Map directly means consumers never have to rebuild it themselves.
    // The CardElement instances stored here are INDEPENDENT COPIES of those in detailedOuicheList,
    // so that mutations made by downstream generators cannot affect the detailed view.
    private static LinkedHashMap<String, CardElement> maOuicheList;
    private static LinkedHashMap<String, Integer> maOuicheListCounts;
    private static List<CardElement> unusedCards;
    private static List<CardElement> listsIntersection;
    private static DecksAndCollectionsList detailedOuicheList;

    private static List<CardElement> thirdPartyList;
    private static List<CardElement> thirdPartyCardsINeedList;

    /**
     * Returns the OuicheList as a map of unique cards (key = passCode ?? imagePath)
     * to their representative {@link CardElement}.
     * Insertion order is preserved; the count for each key is in {@link #getMaOuicheListCounts()}.
     *
     * @return the OuicheList map, or {@code null} if not yet generated.
     */
    public static LinkedHashMap<String, CardElement> getMaOuicheList() {
        return maOuicheList;
    }

    /**
     * Returns the required count for each card in the OuicheList,
     * keyed by the same passCode ?? imagePath key as {@link #getMaOuicheList()}.
     *
     * @return the count map, or {@code null} if not yet generated.
     */
    public static LinkedHashMap<String, Integer> getMaOuicheListCounts() {
        return maOuicheListCounts;
    }

    /**
     * Returns the OuicheList as a flat {@link List}, with each card repeated as many
     * times as it is required. Intended for callers that operate on lists
     * (e.g. HTML generators, sub-list creators, third-party scrapers).
     *
     * @return a flat list derived from the OuicheList map, or {@code null} if not yet generated.
     */
    public static List<CardElement> getMaOuicheListAsFlatList() {
        if (maOuicheList == null) return null;
        List<CardElement> flat = new ArrayList<>();
        for (Map.Entry<String, CardElement> entry : maOuicheList.entrySet()) {
            int count = maOuicheListCounts.getOrDefault(entry.getKey(), 1);
            for (int i = 0; i < count; i++) {
                flat.add(entry.getValue());
            }
        }
        return flat;
    }

    /**
     * Gets the list of cards that are not in any deck or collection.
     * @return the list of unused cards
     */
    public static List<CardElement> getUnusedCards() {
        return unusedCards;
    }

    /**
     * Sets the list of cards that are not in any deck or collection.
     * <p>
     * @param unusedCards the new list of unused cards
     */
    public static void setUnusedCards(List<CardElement> unusedCards) {
        OuicheList.unusedCards = unusedCards;
    }

    /**
     * Gets the intersection of all lists of cards, i.e. the cards that are common to all lists.
     * <p>
     * @return the intersection of all lists of cards
     */
    public static List<CardElement> getListsIntersection() {
        return listsIntersection;
    }

    /**
     * Sets the intersection of all lists of cards, i.e. the cards that are common to all lists.
     * <p>
     * @param listsIntersection the new intersection of all lists of cards
     */
    public static void setListsIntersection(List<CardElement> listsIntersection) {
        OuicheList.listsIntersection = listsIntersection;
    }

    /**
     * Gets the detailed ouiche list, which is the list of cards in the user's collection,
     * as well as the cards that are in the decks and collections lists.
     * <p>
     * @return the detailed ouiche list
     */
    public static DecksAndCollectionsList getDetailedOuicheList() {
        return detailedOuicheList;
    }

    /**
     * Sets the detailed ouiche list, which is the list of cards in the user's collection,
     * as well as the cards that are in the decks and collections lists.
     * <p>
     * @param detailedOuicheList the new detailed ouiche list
     */
    public static void setDetailedOuicheList(DecksAndCollectionsList detailedOuicheList) {
        OuicheList.detailedOuicheList = detailedOuicheList;
    }

    /**
     * Gets the list of cards in the third-party list.
     * <p>
     * @return the list of cards in the third-party list
     */
    public static List<CardElement> getThirdPartyList() {
        return thirdPartyList;
    }

    /**
     * Sets the list of cards in the third-party list.
     * <p>
     * @param thirdPartyList the new list of cards in the third-party list
     */
    public static void setThirdPartyList(List<CardElement> thirdPartyList) {
        OuicheList.thirdPartyList = thirdPartyList;
    }

    /**
     * Gets the list of cards in the third-party list that are not in the user's collection and
     * are needed to complete the decks and collections.
     *
     * @return the list of cards in the third-party list that are not in the user's collection and
     *         are needed to complete the decks and collections
     */
    public static List<CardElement> getThirdPartyCardsINeedList() {
        return thirdPartyCardsINeedList;
    }

    /**
     * Sets the list of cards in the third-party list that are not in the user's collection and
     * are needed to complete the decks and collections.
     * <p>
     * @param thirdPartyCardsINeedList the new list of cards in the third-party list that are not in the user's collection and
     *                                 are needed to complete the decks and collections
     */
    public static void setThirdPartyCardsINeedList(List<CardElement> thirdPartyCardsINeedList) {
        OuicheList.thirdPartyCardsINeedList = thirdPartyCardsINeedList;
    }

    /**
     * Retrieves the user's collection of owned cards.
     *
     * @return the OwnedCardsCollection representing the user's card collection
     */
    public static OwnedCardsCollection getMyCardsCollection() {
        return myCardsCollection;
    }

    /**
     * Sets the user's collection of owned cards.
     * <p>
     * @param myCardsCollection the new OwnedCardsCollection representing the user's card collection
     */
    public static void setMyCardsCollection(OwnedCardsCollection myCardsCollection) {
        OuicheList.myCardsCollection = myCardsCollection;
    }

    /**
     * Retrieves the list of all decks in the user's collection.
     *
     * @return the DecksAndCollectionsList representing the user's decks
     */
    public static DecksAndCollectionsList getDecksList() {
        return decksList;
    }

    /**
     * Sets the list of decks and collections in the OuicheList.
     * <p>
     * This method assigns the provided {@link DecksAndCollectionsList} to the
     * {@link #decksList} field of the OuicheList.
     *
     * @param decksList the {@link DecksAndCollectionsList} to set
     */
    public static void setDecksList(DecksAndCollectionsList decksList) {
        OuicheList.decksList = decksList;
    }

    /**
     * Returns the canonical key used to identify a card in the OuicheList map.
     * Prefers passCode for uniqueness; falls back to imagePath when passCode is absent.
     */
    private static String cardKey(CardElement ce) {
        Card card = ce.getCard();
        return card.getPassCode() != null ? card.getPassCode() : card.getImagePath();
    }

    /**
     * Returns a new list of fresh {@link CardElement} copies built from the given list.
     * Each copy wraps the same {@link Card} object but is an independent {@link CardElement}
     * instance, so that {@code setValues()} calls on the copies never affect the originals.
     */
    private static List<CardElement> copyCardElements(List<CardElement> original) {
        if (original == null) return new ArrayList<>();
        List<CardElement> copy = new ArrayList<>(original.size());
        for (CardElement ce : original) {
            copy.add(new CardElement(ce.getCard()));
        }
        return copy;
    }

    /**
     * Builds the compact OuicheList from the detailed OuicheList.
     *
     * <p>The detailed OuicheList uses artwork-aware comparisons that mark owned cards
     * in-place with an {@code "O"} suffix. This method iterates the flat projection of the
     * detailed list, skips every card marked as owned, and aggregates the remaining ones
     * into a {@link LinkedHashMap} (unique card key → representative {@link CardElement})
     * and a parallel count map.
     *
     * <p>Only FRESH {@link CardElement} instances are stored in the map so that downstream
     * callers (HTML generators, SubListCreator, etc.) cannot corrupt the detailed view's
     * ownership markers via {@code setValues()}.
     *
     * <p>If {@link #CreateDetailedOuicheList} has already been called, its markings are
     * reused and the detailed list is not recomputed.
     *
     * @param ownedCardsCollection the user's owned card collection
     * @param decksList            the decks and collections the user wants to complete
     */
    public static void CreateOuicheList(OwnedCardsCollection ownedCardsCollection, DecksAndCollectionsList decksList) throws Exception {
        listsIntersection = new ArrayList<>();

        if (detailedOuicheList == null) {
            CreateDetailedOuicheList(ownedCardsCollection, decksList);
        }

        maOuicheList = new LinkedHashMap<>();
        maOuicheListCounts = new LinkedHashMap<>();
        // Use identity comparison (reference equality) to deduplicate owned instances.
        // When the same CardElement instance from the owned collection validates slots
        // in multiple loose collections, it appears multiple times in toList() but
        // should only be counted once. Separate instances of the same card (different
        // copies) still each count independently.
        java.util.Set<CardElement> seenInstances =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        for (CardElement card : detailedOuicheList.toList()) {
            if (!seenInstances.add(card)) continue;   // same instance already processed
            if (!card.getOwned()) {
                String key = cardKey(card);
                if (key == null) continue;
                if (!maOuicheList.containsKey(key)) {
                    maOuicheList.put(key, new CardElement(card.getCard()));
                    maOuicheListCounts.put(key, 1);
                } else {
                    maOuicheListCounts.merge(key, 1, Integer::sum);
                }
            }
        }
    }

    /**
     * Creates a detailed OuicheList by making the difference between owned cards and the given decks and collections.
     *
     * <p>This method initializes a new {@link DecksAndCollectionsList} for the detailed OuicheList and
     * populates it with cards from the provided decksList. It then removes all cards that are already
     * owned from this list by comparing with the ownedCardsCollection. The comparisons are performed
     * using artwork and Konami Ids, with certain exceptions considered.
     *
     * <p>The method processes both collections and decks within the decksList, iterating over each
     * main, extra, and side deck. The owned cards are updated after each comparison operation to
     * ensure accurate removal of duplicates.
     *
     * @param ownedCardsCollection the OwnedCardsCollection representing the user's card collection
     * @param decksList            the DecksAndCollectionsList representing the user's decks
     * @return the created detailed OuicheList
     */
    public static DecksAndCollectionsList CreateDetailedOuicheList(OwnedCardsCollection ownedCardsCollection, DecksAndCollectionsList decksList) {
        listsIntersection = new ArrayList<>();

        detailedOuicheList = new DecksAndCollectionsList();

        // -----------------------------------------------------------------------
        // Deep-copy phase — non-loose collections first, then standalone decks,
        // then loose collections. This ordering means detailedOuicheList.toList()
        // and the UI display already present loose collections last with no extra work.
        // -----------------------------------------------------------------------

        if (decksList.getCollections() != null) {
            // Non-loose collections first
            for (ThemeCollection originalCollection : decksList.getCollections()) {
                if (Boolean.TRUE.equals(originalCollection.getConnectToWholeCollection())) continue;
                ThemeCollection collectionCopy = new ThemeCollection();
                collectionCopy.setName(originalCollection.getName());
                collectionCopy.setConnectToWholeCollection(false);
                for (List<Deck> deckGroup : originalCollection.getLinkedDecks()) {
                    for (Deck originalDeck : deckGroup) {
                        Deck deckCopy = new Deck();
                        deckCopy.setName(originalDeck.getName());
                        deckCopy.setMainDeck(copyCardElements(originalDeck.getMainDeck()));
                        deckCopy.setExtraDeck(copyCardElements(originalDeck.getExtraDeck()));
                        deckCopy.setSideDeck(copyCardElements(originalDeck.getSideDeck()));
                        collectionCopy.AddDeck(deckCopy);
                    }
                }
                collectionCopy.setCardsList(copyCardElements(originalCollection.getCardsList()));
                detailedOuicheList.addCollection(collectionCopy);
            }
        }

        if (decksList.getDecks() != null) {
            for (Deck originalDeck : decksList.getDecks()) {
                Deck deckCopy = new Deck();
                deckCopy.setName(originalDeck.getName());
                deckCopy.setMainDeck(copyCardElements(originalDeck.getMainDeck()));
                deckCopy.setExtraDeck(copyCardElements(originalDeck.getExtraDeck()));
                deckCopy.setSideDeck(copyCardElements(originalDeck.getSideDeck()));
                detailedOuicheList.addDeck(deckCopy);
            }
        }

        if (decksList.getCollections() != null) {
            // Loose collections last
            for (ThemeCollection originalCollection : decksList.getCollections()) {
                if (!Boolean.TRUE.equals(originalCollection.getConnectToWholeCollection())) continue;
                ThemeCollection collectionCopy = new ThemeCollection();
                collectionCopy.setName(originalCollection.getName());
                collectionCopy.setConnectToWholeCollection(true);
                for (List<Deck> deckGroup : originalCollection.getLinkedDecks()) {
                    for (Deck originalDeck : deckGroup) {
                        Deck deckCopy = new Deck();
                        deckCopy.setName(originalDeck.getName());
                        deckCopy.setMainDeck(copyCardElements(originalDeck.getMainDeck()));
                        deckCopy.setExtraDeck(copyCardElements(originalDeck.getExtraDeck()));
                        deckCopy.setSideDeck(copyCardElements(originalDeck.getSideDeck()));
                        collectionCopy.AddDeck(deckCopy);
                    }
                }
                collectionCopy.setCardsList(copyCardElements(originalCollection.getCardsList()));
                detailedOuicheList.addCollection(collectionCopy);
            }
        }

        // -----------------------------------------------------------------------
        // Ownership-removal phase.
        // unusedCards starts as a fresh copy of the entire owned collection.
        // Each pass consumes from it; later passes get whatever remains.
        // Order: (1) non-loose collections artwork pass
        //        (2) standalone decks artwork pass
        //        (3) non-loose collections KonamiId pass
        //        (4) standalone decks KonamiId pass
        //        (5) loose collections artwork pass      ← new
        //        (6) loose collections KonamiId pass     ← new
        // -----------------------------------------------------------------------
        unusedCards = copyCardElements(ownedCardsCollection.toList());

        // --- (1) Non-loose collections — artwork-specific pass ---
        if (detailedOuicheList.getCollections() != null) {
            for (int i = 0; i < detailedOuicheList.getCollections().size(); i++) {
                ThemeCollection col = detailedOuicheList.getCollections().get(i);
                if (Boolean.TRUE.equals(col.getConnectToWholeCollection())) continue;

                for (int j = 0; j < col.getLinkedDecks().size(); j++) {
                    for (int k = 0; k < col.getLinkedDecks().get(j).size(); k++) {
                        List<List<CardElement>> tempList = ListDifIntersectArtworkWithExceptions(col.getLinkedDecks().get(j).get(k).getMainDeck(), unusedCards, "O");
                        col.getLinkedDecks().get(j).get(k).setMainDeck(tempList.get(0));
                        unusedCards = new ArrayList<>(tempList.get(1));
                        tempList = ListDifIntersectArtworkWithExceptions(col.getLinkedDecks().get(j).get(k).getExtraDeck(), unusedCards, "O");
                        col.getLinkedDecks().get(j).get(k).setExtraDeck(tempList.get(0));
                        unusedCards = new ArrayList<>(tempList.get(1));
                        tempList = ListDifIntersectArtworkWithExceptions(col.getLinkedDecks().get(j).get(k).getSideDeck(), unusedCards, "O");
                        col.getLinkedDecks().get(j).get(k).setSideDeck(tempList.get(0));
                        unusedCards = new ArrayList<>(tempList.get(1));
                    }
                }
                List<List<CardElement>> tempList = ListDifIntersectArtworkWithExceptions(col.getCardsList(), unusedCards, "O");
                col.setCardsList(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
            }
        }

        // --- (2) Standalone decks — artwork-specific pass ---
        if (detailedOuicheList.getDecks() != null) {
            for (int i = 0; i < detailedOuicheList.getDecks().size(); i++) {
                List<List<CardElement>> tempList = ListDifIntersectArtworkWithExceptions(detailedOuicheList.getDecks().get(i).getMainDeck(), unusedCards, "O");
                detailedOuicheList.getDecks().get(i).setMainDeck(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
                tempList = ListDifIntersectArtworkWithExceptions(detailedOuicheList.getDecks().get(i).getExtraDeck(), unusedCards, "O");
                detailedOuicheList.getDecks().get(i).setExtraDeck(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
                tempList = ListDifIntersectArtworkWithExceptions(detailedOuicheList.getDecks().get(i).getSideDeck(), unusedCards, "O");
                detailedOuicheList.getDecks().get(i).setSideDeck(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
            }
        }

        // --- (3) Non-loose collections — KonamiId pass ---
        if (detailedOuicheList.getCollections() != null) {
            for (int i = 0; i < detailedOuicheList.getCollections().size(); i++) {
                ThemeCollection col = detailedOuicheList.getCollections().get(i);
                if (Boolean.TRUE.equals(col.getConnectToWholeCollection())) continue;

                for (int j = 0; j < col.getLinkedDecks().size(); j++) {
                    for (int k = 0; k < col.getLinkedDecks().get(j).size(); k++) {
                        List<List<CardElement>> tempList = ListDifIntersectKonamiIdWithExceptions(col.getLinkedDecks().get(j).get(k).getMainDeck(), unusedCards, "O");
                        col.getLinkedDecks().get(j).get(k).setMainDeck(tempList.get(0));
                        unusedCards = new ArrayList<>(tempList.get(1));
                        tempList = ListDifIntersectKonamiIdWithExceptions(col.getLinkedDecks().get(j).get(k).getExtraDeck(), unusedCards, "O");
                        col.getLinkedDecks().get(j).get(k).setExtraDeck(tempList.get(0));
                        unusedCards = new ArrayList<>(tempList.get(1));
                        tempList = ListDifIntersectKonamiIdWithExceptions(col.getLinkedDecks().get(j).get(k).getSideDeck(), unusedCards, "O");
                        col.getLinkedDecks().get(j).get(k).setSideDeck(tempList.get(0));
                        unusedCards = new ArrayList<>(tempList.get(1));
                    }
                }
                List<List<CardElement>> tempList = ListDifIntersectKonamiIdWithExceptions(col.getCardsList(), unusedCards, "O");
                col.setCardsList(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
            }
        }

        // --- (4) Standalone decks — KonamiId pass ---
        if (detailedOuicheList.getDecks() != null) {
            for (int i = 0; i < detailedOuicheList.getDecks().size(); i++) {
                List<List<CardElement>> tempList = ListDifIntersectKonamiIdWithExceptions(detailedOuicheList.getDecks().get(i).getMainDeck(), unusedCards, "O");
                detailedOuicheList.getDecks().get(i).setMainDeck(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
                tempList = ListDifIntersectKonamiIdWithExceptions(detailedOuicheList.getDecks().get(i).getExtraDeck(), unusedCards, "O");
                detailedOuicheList.getDecks().get(i).setExtraDeck(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
                tempList = ListDifIntersectKonamiIdWithExceptions(detailedOuicheList.getDecks().get(i).getSideDeck(), unusedCards, "O");
                detailedOuicheList.getDecks().get(i).setSideDeck(tempList.get(0));
                unusedCards = new ArrayList<>(tempList.get(1));
            }
        }

        // --- (5) & (6) Loose collections ---
        // Each loose collection validates against a fresh full-ownership pool first
        // (looseOwnedPool). Cards still unvalidated after that fall back to the
        // depleted unusedCards from passes 1–4. Both pools shrink as they are used.
        //
        // Unlike non-loose passes, matched slots are REPLACED by the actual owned
        // CardElement instance from the pool rather than having their copy marked "O".
        // This preserves the identity link to the original owned card, which lets
        // CreateOuicheList deduplicate by instance when counting.
        List<CardElement> looseOwnedPool = copyCardElements(ownedCardsCollection.toList());

        java.util.function.BiPredicate<Card, Card> artworkComparator =
                (c1, c2) -> c1.getImagePath() != null && c2.getImagePath() != null
                        && c1.getImagePath().equals(c2.getImagePath());
        java.util.function.BiPredicate<Card, Card> konamiComparator =
                (c1, c2) -> c1.getKonamiId() != null && c2.getKonamiId() != null
                        && c1.getKonamiId().equals(c2.getKonamiId());

        // Artwork pass: only cards whose toString() contains "*" (specific artwork).
        // KonamiId pass: cards without "*" or "+".
        List<String> artMustContain = List.of("*");
        List<String> artMustNotContain = List.of("+");
        List<String> idMustNotContain = List.of("+", "*");

        if (detailedOuicheList.getCollections() != null) {
            for (int i = 0; i < detailedOuicheList.getCollections().size(); i++) {
                ThemeCollection col = detailedOuicheList.getCollections().get(i);
                if (!Boolean.TRUE.equals(col.getConnectToWholeCollection())) continue;

                for (int j = 0; j < col.getLinkedDecks().size(); j++) {
                    for (int k = 0; k < col.getLinkedDecks().get(j).size(); k++) {

                        // ── Main deck ────────────────────────────────────────
                        List<CardElement> section = col.getLinkedDecks().get(j).get(k).getMainDeck();
                        // artwork pass — looseOwnedPool first, then unusedCards fallback
                        List<List<CardElement>> t = loosePassReplace(section, looseOwnedPool, artworkComparator, artMustContain, artMustNotContain);
                        looseOwnedPool = t.get(1);
                        t = loosePassReplace(t.get(0), unusedCards, artworkComparator, artMustContain, artMustNotContain);
                        unusedCards = t.get(1);
                        // KonamiId pass — looseOwnedPool first, then unusedCards fallback
                        t = loosePassReplace(t.get(0), looseOwnedPool, konamiComparator, null, idMustNotContain);
                        looseOwnedPool = t.get(1);
                        t = loosePassReplace(t.get(0), unusedCards, konamiComparator, null, idMustNotContain);
                        col.getLinkedDecks().get(j).get(k).setMainDeck(t.get(0));
                        unusedCards = t.get(1);

                        // ── Extra deck ───────────────────────────────────────
                        section = col.getLinkedDecks().get(j).get(k).getExtraDeck();
                        t = loosePassReplace(section, looseOwnedPool, artworkComparator, artMustContain, artMustNotContain);
                        looseOwnedPool = t.get(1);
                        t = loosePassReplace(t.get(0), unusedCards, artworkComparator, artMustContain, artMustNotContain);
                        unusedCards = t.get(1);
                        t = loosePassReplace(t.get(0), looseOwnedPool, konamiComparator, null, idMustNotContain);
                        looseOwnedPool = t.get(1);
                        t = loosePassReplace(t.get(0), unusedCards, konamiComparator, null, idMustNotContain);
                        col.getLinkedDecks().get(j).get(k).setExtraDeck(t.get(0));
                        unusedCards = t.get(1);

                        // ── Side deck ────────────────────────────────────────
                        section = col.getLinkedDecks().get(j).get(k).getSideDeck();
                        t = loosePassReplace(section, looseOwnedPool, artworkComparator, artMustContain, artMustNotContain);
                        looseOwnedPool = t.get(1);
                        t = loosePassReplace(t.get(0), unusedCards, artworkComparator, artMustContain, artMustNotContain);
                        unusedCards = t.get(1);
                        t = loosePassReplace(t.get(0), looseOwnedPool, konamiComparator, null, idMustNotContain);
                        looseOwnedPool = t.get(1);
                        t = loosePassReplace(t.get(0), unusedCards, konamiComparator, null, idMustNotContain);
                        col.getLinkedDecks().get(j).get(k).setSideDeck(t.get(0));
                        unusedCards = t.get(1);
                    }
                }

                // ── Cards list ───────────────────────────────────────────────
                List<List<CardElement>> t = loosePassReplace(col.getCardsList(), looseOwnedPool, artworkComparator, artMustContain, artMustNotContain);
                looseOwnedPool = t.get(1);
                t = loosePassReplace(t.get(0), unusedCards, artworkComparator, artMustContain, artMustNotContain);
                unusedCards = t.get(1);
                t = loosePassReplace(t.get(0), looseOwnedPool, konamiComparator, null, idMustNotContain);
                looseOwnedPool = t.get(1);
                t = loosePassReplace(t.get(0), unusedCards, konamiComparator, null, idMustNotContain);
                col.setCardsList(t.get(0));
                unusedCards = t.get(1);
            }
        }

        return detailedOuicheList;
    }

    /**
     * Loose-collection pass: for each element in {@code sectionList}, tries to find
     * a matching element in {@code pool}. On a match the sectionList slot is replaced
     * by the actual pool instance (marked owned=true) and that instance is removed
     * from the pool. Unmatched slots are left as-is.
     * <p>
     * mustContain / mustNotContain filter which sectionList elements are eligible for
     * this pass (same semantics as ListDifIntersect). Already-owned elements are always
     * passed through unchanged so that a previous sub-pass result is never re-processed.
     *
     * @return [modified sectionList, remaining pool]
     */
    private static List<List<CardElement>> loosePassReplace(
            List<CardElement> sectionList,
            List<CardElement> pool,
            java.util.function.BiPredicate<Card, Card> comparator,
            List<String> mustContain,
            List<String> mustNotContain) {

        List<CardElement> result = new ArrayList<>(sectionList.size());
        List<CardElement> remainingPool = new ArrayList<>(pool);

        for (CardElement sectionCard : sectionList) {
            // Already validated in a previous sub-pass — keep as-is.
            if (Boolean.TRUE.equals(sectionCard.getOwned())) {
                result.add(sectionCard);
                continue;
            }
            if (sectionCard.getCard() == null
                    || sectionCard.getCard().getKonamiId() == null) {
                result.add(sectionCard);
                continue;
            }

            String valueA = sectionCard.toString();
            boolean eligible =
                    (mustContain == null || mustContain.stream().allMatch(valueA::contains))
                            && (mustNotContain == null || mustNotContain.stream().noneMatch(valueA::contains));

            if (!eligible) {
                result.add(sectionCard);
                continue;
            }

            // Try to find a match in the pool.
            boolean matched = false;
            java.util.Iterator<CardElement> it = remainingPool.iterator();
            while (it.hasNext()) {
                CardElement poolCard = it.next();
                if (poolCard.getCard() == null
                        || poolCard.getCard().getKonamiId() == null) continue;
                if (comparator.test(sectionCard.getCard(), poolCard.getCard())) {
                    poolCard.setOwned(true);   // mark the actual owned instance
                    result.add(poolCard);      // replace the copy with the real instance
                    it.remove();
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                result.add(sectionCard);       // no match — keep unowned copy
            }
        }

        return List.of(result, remainingPool);
    }


    /**
     * Import the detailed ouiche list from a file.
     * <p>
     * The file is expected to be a text file with the following structure:
     * <ul>
     *     <li>Lines starting with "===" are considered to be title lines, and
     *         are used to separate collections and decks. The text after the
     *         "===" is used as the name of the collection or deck.</li>
     *     <li>Lines starting with "---" are considered to be collection or deck
     *         lines. The text after the "---" is used as the name of the
     *         collection or deck. If the line is "---Collection", the
     *         following lines are considered to be part of a collection. If the
     *         line is "---Decks", the following lines are considered to be part
     *         of a deck.</li>
     *     <li>Lines that do not start with "===" or "---" are considered to be
     *         card lines. The line is expected to contain the name of the card,
     *         and optionally the number of copies of the card that are owned.
     *         If the number of copies is not specified, it is assumed to be 0.
     *         If the number of copies is negative, it is assumed to be 1.
     *         If the card is not in the collection, it is added to the collection
     *         and to the maOuicheList.
     *     </li>
     * </ul>
     * <p>
     * If the file is not a valid detailed ouiche list file, an exception is
     * thrown.
     * @param filePath the path to the file to import
     * @throws Exception if there is an error importing the file
     */
    public static void importOuicheList(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path);

        detailedOuicheList = new DecksAndCollectionsList();
        maOuicheList = new LinkedHashMap<>();
        maOuicheListCounts = new LinkedHashMap<>();

        ThemeCollection currentCollection = null;
        Deck currentDeck = null;
        boolean isDecksSection = false;

        for (String line : lines) {
            if (line.startsWith("===")) {
                if (currentCollection != null) {
                    detailedOuicheList.addCollection(currentCollection);
                }
                currentCollection = new ThemeCollection();
                currentCollection.setName(line.substring(3).trim());
                isDecksSection = false;
            } else if (line.startsWith("---")) {
                if (line.equals("---Collection")) {
                    currentDeck = null;
                } else {
                    currentDeck = new Deck();
                    currentDeck.setName(line.substring(3).trim());
                    if (currentCollection != null) {
                        currentCollection.AddDeck(currentDeck);
                    } else {
                        detailedOuicheList.addDeck(currentDeck);
                    }
                }
            } else if (line.startsWith("===") && line.contains("Decks")) {
                isDecksSection = true;
            } else {
                CardElement card = new CardElement(line.trim());
                if (!card.getOwned()) {
                    if (isDecksSection) {
                        if (currentDeck != null) {
                            currentDeck.AddCardMain(card);
                            String key = cardKey(card);
                            if (key != null) {
                                maOuicheList.putIfAbsent(key, card);
                                maOuicheListCounts.merge(key, 1, Integer::sum);
                            }
                        }
                    } else {
                        if (currentDeck != null) {
                            currentDeck.AddCardMain(card);
                            String key = cardKey(card);
                            if (key != null) {
                                maOuicheList.putIfAbsent(key, card);
                                maOuicheListCounts.merge(key, 1, Integer::sum);
                            }
                        } else if (currentCollection != null) {
                            currentCollection.getCardsList().add(card);
                            String key = cardKey(card);
                            if (key != null) {
                                maOuicheList.putIfAbsent(key, card);
                                maOuicheListCounts.merge(key, 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }

        if (currentCollection != null) {
            detailedOuicheList.addCollection(currentCollection);
        }
    }

    /**
     * Import the third-party list from the given file.
     *
     * This method reads a file line by line and adds each line to the third-party list
     * as a CardElement.
     *
     * The following lines are ignored:
     * <ul>
     * <li>Lines that start with "="</li>
     * <li>Lines that start with "-"</li>
     * <li>Lines that start with "#"</li>
     * <li>Empty lines</li>
     * </ul>
     *
     * @param filePath the path to the file to import
     * @throws Exception if there is an error importing the file
     */
    public static void importThirdPartyList(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path);

        thirdPartyList = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("=") && !line.startsWith("-") && !line.startsWith("#") && !line.isEmpty()) {
                thirdPartyList.add(new CardElement(line.trim()));
            }
        }
    }

    /**
     * Generate a list of cards in the third-party list that are not in the
     * DecksAndCollectionList, but are needed for the decks in the OuicheList.
     * <p>
     * The list is generated by first computing the difference between the
     * third-party list and the OuicheList using the print code, and then
     * computing the difference between the two lists using the Konami ID.
     * <p>
     * The resulting list is stored in the {@link #thirdPartyCardsINeedList} field.
     */
    public static void generateThirdPartyCardsINeedList() {
        thirdPartyCardsINeedList = new ArrayList<>();
        List<CardElement> maOuicheListTemp;
        List<CardElement> thirdPartyListTemp;

        List<CardElement> flatOuicheList = getMaOuicheListAsFlatList();
        List<List<CardElement>> tempList = ListDifIntersectPrintcode(thirdPartyList, flatOuicheList);
        thirdPartyListTemp = tempList.get(0);
        maOuicheListTemp = tempList.get(1);
        thirdPartyCardsINeedList = tempList.get(2);

        tempList = ListDifIntersectKonamiId(thirdPartyListTemp, maOuicheListTemp);
        thirdPartyCardsINeedList.addAll(tempList.get(2));
    }

    /**
     * Save the detailed ouiche list to a file.
     * <p>
     * The file is written in the following format:
     * <pre>
     * ===ThemeCollection1=========
     * ---Deck1------------
     * Card1
     * Card2
     * ...
     * ---Deck2------------
     * Card3
     * Card4
     * ...
     * ...
     * ===Decks==============
     * ---Deck1-------
     * Card5
     * Card6
     * ...
     * ---Deck2-------
     * Card7
     * Card8
     * ...
     * </pre>
     * <p>
     * The file is overwritten if it already exists.
     * <p>
     * If there is an error writing to the file, an IOException is thrown.
     * @param filePath the path to the file to save
     * @throws IOException if there is an error writing to the file
     */
    public static void ouicheListSave(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            boolean newFile = file.createNewFile();
            if (!newFile) {
                throw new IOException("File was not created: " + filePath);
            }
        }
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8));
        for (ThemeCollection collection : detailedOuicheList.getCollections()) {
            writer.write("===" + collection.getName() + "========");
            writer.newLine();
            for (int i = 0; i < collection.getLinkedDecks().size(); i++) {
                for (Deck deck : collection.getLinkedDecks().get(i)) {
                    writer.write("---" + deck.getName() + "------------");
                    writer.newLine();
                    for (CardElement card : deck.toList()) {
                        //writer.write(card.getCard().getPasscode());
                        writer.write(card.toString());
                        writer.newLine();
                    }
                }
            }
            if (!collection.getCardsList().isEmpty()) {
                writer.write("---Collection----------");
                writer.newLine();
                for (CardElement card : collection.getCardsList()) {
                    //writer.write(card.getCard().getPasscode());
                    writer.write(card.toString());
                    writer.newLine();
                }
            }
        }

        writer.write("===Decks===============");
        writer.newLine();
        for (Deck deck : detailedOuicheList.getDecks()) {
            writer.write("---" + deck.getName() + "-------");
            writer.newLine();
            for (CardElement card : deck.toList()) {
                if (card.getCard().getPassCode() != null) {
                    //writer.write(card.getCard().getPasscode());
                    writer.write(card.toString());
                } else if (card.getCard().getPrintCode() != null) {
                    //writer.write(card.getCard().getPasscode());
                    writer.write(card.toString());
                } else {
                    System.out.println("Error : no passcode nor printcode for this card");
                }

                writer.newLine();
            }
        }

        writer.close();
    }

    /**
     * Saves the unused cards to a file.
     * <p>
     * The file is overwritten if it already exists.
     * <p>
     * If there is an error writing to the file, an IOException is thrown.
     * @param filePath the path to the file to save
     * @throws IOException if there is an error writing to the file
     */
    public static void unusedCardsSave(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            boolean newFile = file.createNewFile();
            if (!newFile) {
                throw new IOException("File was not created: " + filePath);
            }
        }
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8));

        for (CardElement card : unusedCards) {
            //writer.write(card.getCard().getPasscode());
            writer.write(card.toString());
            writer.newLine();
        }

        writer.close();
    }

    /**
     * Saves the third party cards I need to a file.
     * <p>
     * The file is overwritten if it already exists.
     * <p>
     * If there is an error writing to the file, an IOException is thrown.
     * @param directoryPath the path of the directory to save the file to
     * @param filePath the name of the file to save
     * @throws IOException if there is an error writing to the file
     */
    public static void thirdPartyCardsINeedListSave(String directoryPath, String filePath) throws IOException {
        Files.createDirectories(Paths.get(directoryPath));
        File file = new File(directoryPath + filePath);
        if (!file.exists()) {
            boolean newFile = file.createNewFile();
            if (!newFile) {
                throw new IOException("File was not created: " + directoryPath + filePath);
            }
        }
        //BufferedWriter writer = new BufferedWriter(new FileWriter(directoryPath + filePath));
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(directoryPath + filePath), StandardCharsets.UTF_8));

        for (CardElement card : thirdPartyCardsINeedList) {
            //writer.write(card.getCard().getPasscode());
            writer.write(card.toString());
            writer.newLine();
        }

        writer.close();
    }

    /**
     * Returns a string representation of this OuicheList.
     * <p>
     * The representation is a newline-separated list of the string
     * representations of all cards in this OuicheList.
     * @return a string representation of this OuicheList
     */
    public String toString() {
        String returnString = "";

        for (CardElement cardElement : maOuicheList.values()) {
            returnString = returnString.concat(cardElement.toString());
        }

        return returnString;
    }
}