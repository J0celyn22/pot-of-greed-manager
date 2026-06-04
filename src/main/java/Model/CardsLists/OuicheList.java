package Model.CardsLists;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static Model.CardsLists.ListDifferenceIntersection.ListDifIntersectKonamiId;
import static Model.CardsLists.ListDifferenceIntersection.ListDifIntersectPrintcode;

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
        if (original == null) {
            return new ArrayList<>();
        }
        List<CardElement> copy = new ArrayList<>(original.size());
        for (CardElement ce : original) {
            copy.add(new CardElement(ce));
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
    /**
     * Builds the compact OuicheList map from the already-computed detailed OuicheList.
     *
     * <p>Three counting rules are enforced:
     * <ol>
     *   <li><b>Group-max (Rule 1):</b> Cards in the same deck group (List&lt;Deck&gt;) are
     *       alternative configurations, so for each card key the contribution of a group is
     *       {@code max} over its decks, not the sum. Independent groups (different entries in
     *       {@code linkedDecks}) are then summed.</li>
     *   <li><b>Collection/deck sharing (Rule 2):</b> Non-{@code dontRemove} cards in the
     *       {@code cardsList} of a ThemeCollection share with the deck groups of the same
     *       collection. The total needed for such a key is
     *       {@code max(sum-of-group-maxes, cardsList-count)}, not the sum of both.
     *       {@code dontRemove} cards are always counted independently (summed on top).</li>
     *   <li><b>Loose-collection deduplication (Rule 3):</b> Cards in loose collections
     *       ({@code connectToWholeCollection = true}) are only counted if their key is not
     *       already present in the map from a non-loose context.  If the key is new, the
     *       loose collection contributes its unowned count; if already present, the loose
     *       contribution is silently dropped (you will already buy the card for the other
     *       context).</li>
     * </ol>
     */
    public static void CreateOuicheList(OwnedCardsCollection ownedCardsCollection,
                                        DecksAndCollectionsList decksList) throws Exception {
        listsIntersection = new ArrayList<>();
        if (detailedOuicheList == null) {
            CreateDetailedOuicheList(ownedCardsCollection, decksList);
        }

        maOuicheList = new LinkedHashMap<>();
        maOuicheListCounts = new LinkedHashMap<>();

        // ── Non-loose collections ─────────────────────────────────────────────────
        if (detailedOuicheList.getCollections() != null) {
            for (ThemeCollection col : detailedOuicheList.getCollections()) {
                if (Boolean.TRUE.equals(col.getConnectToWholeCollection())) continue;

                // Per-key unowned counts from deck groups (Rule 1: max within group, sum across groups)
                Map<String, Integer> deckNormalContrib = new LinkedHashMap<>();
                Map<String, Integer> deckDontRemoveContrib = new LinkedHashMap<>();
                Map<String, CardElement> repCards = new LinkedHashMap<>();

                for (List<Deck> group : col.getLinkedDecks()) {
                    Map<String, Integer> groupMaxNormal = new HashMap<>();
                    Map<String, Integer> groupMaxDontRemove = new HashMap<>();

                    for (Deck deck : group) {
                        Map<String, Integer> deckNormal = new HashMap<>();
                        Map<String, Integer> deckDontRemove = new HashMap<>();

                        for (CardElement ce : deck.toList()) {
                            if (ce.getOwnershipStatus() != OwnershipStatus.MISSING) continue;
                            String key = cardKey(ce);
                            if (key == null) continue;
                            repCards.putIfAbsent(key, ce);
                            if (Boolean.TRUE.equals(ce.getDontRemove()))
                                deckDontRemove.merge(key, 1, Integer::sum);
                            else
                                deckNormal.merge(key, 1, Integer::sum);
                        }
                        deckNormal.forEach((k, v) -> groupMaxNormal.merge(k, v, Math::max));
                        deckDontRemove.forEach((k, v) -> groupMaxDontRemove.merge(k, v, Math::max));
                    }
                    // Sum group contributions (groups are independent configurations)
                    groupMaxNormal.forEach((k, v) -> deckNormalContrib.merge(k, v, Integer::sum));
                    groupMaxDontRemove.forEach((k, v) -> deckDontRemoveContrib.merge(k, v, Integer::sum));
                }

                // Per-key unowned counts from cardsList
                Map<String, Integer> listNormal = new LinkedHashMap<>();
                Map<String, Integer> listDontRemove = new LinkedHashMap<>();
                for (CardElement ce : col.getCardsList()) {
                    if (ce.getOwnershipStatus() != OwnershipStatus.MISSING) continue;
                    String key = cardKey(ce);
                    if (key == null) continue;
                    repCards.putIfAbsent(key, ce);
                    if (Boolean.TRUE.equals(ce.getDontRemove()))
                        listDontRemove.merge(key, 1, Integer::sum);
                    else
                        listNormal.merge(key, 1, Integer::sum);
                }

                // Rule 2: non-dontRemove key needs max(deckContrib, cardsListContrib)
                Set<String> allNormalKeys = new HashSet<>(deckNormalContrib.keySet());
                allNormalKeys.addAll(listNormal.keySet());
                for (String key : allNormalKeys) {
                    int needed = Math.max(
                            deckNormalContrib.getOrDefault(key, 0),
                            listNormal.getOrDefault(key, 0));
                    if (needed <= 0) continue;
                    addToGlobalMap(key, needed, repCards.get(key));
                }

                // dontRemove deck slots: group-max already applied; sum across collections
                deckDontRemoveContrib.forEach((key, needed) ->
                        addToGlobalMap(key, needed, repCards.get(key)));

                // dontRemove cardsList: always independent
                listDontRemove.forEach((key, needed) ->
                        addToGlobalMap(key, needed, repCards.get(key)));
            }
        }

        // ── Standalone decks ─────────────────────────────────────────────────────
        if (detailedOuicheList.getDecks() != null) {
            for (Deck deck : detailedOuicheList.getDecks()) {
                for (CardElement ce : deck.toList()) {
                    if (ce.getOwnershipStatus() != OwnershipStatus.MISSING) continue;
                    String key = cardKey(ce);
                    if (key == null) continue;
                    addToGlobalMap(key, 1, ce);
                }
            }
        }

        // ── Loose collections (Rule 3) ────────────────────────────────────────────
        // Build each loose collection's contribution exactly like a non-loose one
        // (group-max for decks, max with cardsList), then only add keys that are
        // NOT already in the map from the non-loose pass above.
        if (detailedOuicheList.getCollections() != null) {
            for (ThemeCollection col : detailedOuicheList.getCollections()) {
                if (!Boolean.TRUE.equals(col.getConnectToWholeCollection())) continue;

                Map<String, Integer> looseContrib = new LinkedHashMap<>();
                Map<String, CardElement> repCards = new LinkedHashMap<>();

                for (List<Deck> group : col.getLinkedDecks()) {
                    Map<String, Integer> groupMax = new HashMap<>();
                    for (Deck deck : group) {
                        Map<String, Integer> deckCounts = new HashMap<>();
                        for (CardElement ce : deck.toList()) {
                            if (ce.getOwnershipStatus() != OwnershipStatus.MISSING) continue;
                            String key = cardKey(ce);
                            if (key == null) continue;
                            repCards.putIfAbsent(key, ce);
                            deckCounts.merge(key, 1, Integer::sum);
                        }
                        deckCounts.forEach((k, v) -> groupMax.merge(k, v, Math::max));
                    }
                    groupMax.forEach((k, v) -> looseContrib.merge(k, v, Integer::sum));
                }
                for (CardElement ce : col.getCardsList()) {
                    if (ce.getOwnershipStatus() != OwnershipStatus.MISSING) continue;
                    String key = cardKey(ce);
                    if (key == null) continue;
                    repCards.putIfAbsent(key, ce);
                    looseContrib.merge(key, 1, Integer::sum);
                }

                // Rule 3: only contribute keys not already in the map
                looseContrib.forEach((key, count) -> {
                    if (!maOuicheList.containsKey(key)) {
                        maOuicheList.put(key, new CardElement(repCards.get(key).getCard()));
                        maOuicheListCounts.put(key, count);
                    }
                    // else: already needed for a non-loose context — don't add
                });
            }
        }
    }

    /**
     * Adds {@code count} copies of the card identified by {@code key} to the global
     * OuicheList map, creating a fresh representative entry on the first insertion and
     * accumulating (summing) counts on subsequent ones.
     */
    private static void addToGlobalMap(String key, int count, CardElement rep) {
        if (!maOuicheList.containsKey(key)) {
            maOuicheList.put(key, new CardElement(rep.getCard()));
            maOuicheListCounts.put(key, count);
        } else {
            maOuicheListCounts.merge(key, count, Integer::sum);
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
                // Bug 1 fix: preserve deck-group boundaries so that within-group
                // propagation (Bug 2) can work correctly on the copy.
                for (List<Deck> deckGroup : originalCollection.getLinkedDecks()) {
                    boolean firstInGroup = true;
                    for (Deck originalDeck : deckGroup) {
                        Deck deckCopy = new Deck();
                        deckCopy.setName(originalDeck.getName());
                        deckCopy.setMainDeck(copyCardElements(originalDeck.getMainDeck()));
                        deckCopy.setExtraDeck(copyCardElements(originalDeck.getExtraDeck()));
                        deckCopy.setSideDeck(copyCardElements(originalDeck.getSideDeck()));
                        if (firstInGroup) {
                            collectionCopy.AddDeck(deckCopy);
                            firstInGroup = false;
                        } else {
                            collectionCopy.AddDeckToExistingUnit(deckCopy, collectionCopy.getLinkedDecks().size() - 1);
                        }
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
                // Bug 1 fix: preserve deck-group boundaries for loose collections too.
                for (List<Deck> deckGroup : originalCollection.getLinkedDecks()) {
                    boolean firstInGroup = true;
                    for (Deck originalDeck : deckGroup) {
                        Deck deckCopy = new Deck();
                        deckCopy.setName(originalDeck.getName());
                        deckCopy.setMainDeck(copyCardElements(originalDeck.getMainDeck()));
                        deckCopy.setExtraDeck(copyCardElements(originalDeck.getExtraDeck()));
                        deckCopy.setSideDeck(copyCardElements(originalDeck.getSideDeck()));
                        if (firstInGroup) {
                            collectionCopy.AddDeck(deckCopy);
                            firstInGroup = false;
                        } else {
                            collectionCopy.AddDeckToExistingUnit(deckCopy, collectionCopy.getLinkedDecks().size() - 1);
                        }
                    }
                }
                collectionCopy.setCardsList(copyCardElements(originalCollection.getCardsList()));
                detailedOuicheList.addCollection(collectionCopy);
            }
        }

        // -----------------------------------------------------------------------
        // Ownership-removal phase.
        //
        // unusedCards starts as a fresh copy of the entire owned collection.
        // The whole phase runs TWICE:
        //   Round 1 (qualityRequired = true,  status = OWNED):
        //     A pool card is only accepted when it meets the wanted slot's
        //     condition/rarity requirements (or when the slot has none).
        //     Matched slots are marked OWNED.
        //   Round 2 (qualityRequired = false, status = OWNED_SUBSTANDARD):
        //     Runs on the slots still MISSING after Round 1, using the pool
        //     cards that were not consumed in Round 1.
        //     Any card match — regardless of quality — is accepted.
        //     Matched slots are marked OWNED_SUBSTANDARD.
        //
        // Within each round the order mirrors the original ordering:
        //   (1) Non-loose collections: per collection, per deck group
        //       (a) non-dontRemove passes (artwork then KonamiId)
        //       (b) Bug 2: free within-group propagation
        //       (c) Bug 4: dontRemove passes (artwork then KonamiId, independent per deck)
        //       (d) Bug 3 + cardsList passes (non-dontRemove then dontRemove)
        //   (2) Standalone decks: non-dontRemove then dontRemove (artwork then KonamiId)
        //   (3) Loose collections: looseOwnedPool first, then unusedCards fallback
        // -----------------------------------------------------------------------
        unusedCards = copyCardElements(ownedCardsCollection.toList());

        for (OwnershipStatus roundStatus : new OwnershipStatus[]{OwnershipStatus.OWNED, OwnershipStatus.OWNED_SUBSTANDARD}) {
            boolean qualityRequired = (roundStatus == OwnershipStatus.OWNED);

            // --- (1) Non-loose collections ---
            if (detailedOuicheList.getCollections() != null) {
                for (ThemeCollection col : detailedOuicheList.getCollections()) {
                    if (Boolean.TRUE.equals(col.getConnectToWholeCollection())) {
                        continue;
                    }

                    for (List<Deck> deckGroup : col.getLinkedDecks()) {
                        // (a) non-dontRemove passes for each deck in the group
                        for (Deck deck : deckGroup) {
                            unusedCards = applyNonDontRemovePassesToDeck(deck, unusedCards, qualityRequired, roundStatus);
                        }
                        // (b) Bug 2: free within-group propagation (no pool consumed)
                        if (deckGroup.size() > 1) {
                            propagateGroupValidation(deckGroup, roundStatus);
                        }
                        // (c) Bug 4: dontRemove passes, independent per deck
                        for (Deck deck : deckGroup) {
                            unusedCards = applyDontRemovePassesToDeck(deck, unusedCards, qualityRequired, roundStatus);
                        }
                    }

                    // (d) Bug 3: free satisfaction of cardsList from deck validations, then
                    //     remaining cardsList passes (non-dontRemove, then dontRemove).
                    col.setCardsList(satisfyCardsListFromDeckValidations(col.getCardsList(), col, roundStatus));

                    List<List<CardElement>> sectionResult;
                    sectionResult = applySectionPass(col.getCardsList(), unusedCards, false, qualityRequired, roundStatus);
                    col.setCardsList(sectionResult.get(0));
                    unusedCards = sectionResult.get(1);

                    sectionResult = applySectionPass(col.getCardsList(), unusedCards, true, qualityRequired, roundStatus);
                    col.setCardsList(sectionResult.get(0));
                    unusedCards = sectionResult.get(1);
                }
            }

            // --- (2) Standalone decks ---
            if (detailedOuicheList.getDecks() != null) {
                for (Deck deck : detailedOuicheList.getDecks()) {
                    unusedCards = applyNonDontRemovePassesToDeck(deck, unusedCards, qualityRequired, roundStatus);
                    unusedCards = applyDontRemovePassesToDeck(deck, unusedCards, qualityRequired, roundStatus);
                }
            }

            // --- (3) Loose collections ---
            // Each loose collection validates against a fresh full-ownership pool first
            // (looseOwnedPool). Cards still unvalidated after that fall back to the
            // depleted unusedCards from passes 1-2. Both pools shrink as they are used.
            //
            // Unlike non-loose passes, matched slots are REPLACED by the actual owned
            // CardElement instance from the pool, preserving the identity link to the
            // original owned card so CreateOuicheList can deduplicate by instance.
            List<CardElement> looseOwnedPool = copyCardElements(ownedCardsCollection.toList());

            if (detailedOuicheList.getCollections() != null) {
                for (ThemeCollection col : detailedOuicheList.getCollections()) {
                    if (!Boolean.TRUE.equals(col.getConnectToWholeCollection())) {
                        continue;
                    }

                    for (List<Deck> deckGroup : col.getLinkedDecks()) {
                        for (Deck deck : deckGroup) {
                            List<List<CardElement>> mainResult = applyLoosePasses(
                                    deck.getMainDeck(), looseOwnedPool, unusedCards, qualityRequired, roundStatus);
                            deck.setMainDeck(mainResult.get(0));
                            looseOwnedPool = mainResult.get(1);
                            unusedCards = mainResult.get(2);

                            List<List<CardElement>> extraResult = applyLoosePasses(
                                    deck.getExtraDeck(), looseOwnedPool, unusedCards, qualityRequired, roundStatus);
                            deck.setExtraDeck(extraResult.get(0));
                            looseOwnedPool = extraResult.get(1);
                            unusedCards = extraResult.get(2);

                            List<List<CardElement>> sideResult = applyLoosePasses(
                                    deck.getSideDeck(), looseOwnedPool, unusedCards, qualityRequired, roundStatus);
                            deck.setSideDeck(sideResult.get(0));
                            looseOwnedPool = sideResult.get(1);
                            unusedCards = sideResult.get(2);
                        }
                    }

                    List<List<CardElement>> cardsListResult = applyLoosePasses(
                            col.getCardsList(), looseOwnedPool, unusedCards, qualityRequired, roundStatus);
                    col.setCardsList(cardsListResult.get(0));
                    looseOwnedPool = cardsListResult.get(1);
                    unusedCards = cardsListResult.get(2);
                }
            }
        }

        return detailedOuicheList;
    }

    /**
     * Bug 2 fix — within-group propagation for non-loose collections.
     *
     * <p>Only slots resolved in the <em>current</em> round (status ==
     * {@code roundStatus}) are propagated. Slots from earlier rounds are
     * excluded so their status cannot bleed into a later round.
     */
    private static void propagateGroupValidation(
            List<Deck> deckGroup, OwnershipStatus roundStatus) {

        List<List<String>> idsPerDeck = new ArrayList<>();
        for (Deck deck : deckGroup) {
            List<String> ids = new ArrayList<>();
            collectKonamiIdsByStatus(deck.getMainDeck(), ids, roundStatus);
            collectKonamiIdsByStatus(deck.getExtraDeck(), ids, roundStatus);
            collectKonamiIdsByStatus(deck.getSideDeck(), ids, roundStatus);
            idsPerDeck.add(ids);
        }

        for (int srcIdx = 0; srcIdx < deckGroup.size(); srcIdx++) {
            for (int dstIdx = 0; dstIdx < deckGroup.size(); dstIdx++) {
                if (dstIdx == srcIdx) {
                    continue;
                }
                List<String> remaining = new ArrayList<>(idsPerDeck.get(srcIdx));
                propagateToSections(deckGroup.get(dstIdx), remaining, roundStatus);
            }
        }
    }

    /**
     * Collects KonamiIds of non-dontRemove cards whose status equals
     * {@code status} into {@code out}, preserving duplicates.
     */
    private static void collectKonamiIdsByStatus(
            List<CardElement> section,
            List<String> out,
            OwnershipStatus status) {

        for (CardElement ce : section) {
            if (ce.getOwnershipStatus() != status) {
                continue;
            }
            if (Boolean.TRUE.equals(ce.getDontRemove())) {
                continue;
            }
            if (ce.getCard() == null || ce.getCard().getKonamiId() == null) {
                continue;
            }
            out.add(ce.getCard().getKonamiId());
        }
    }

    private static void propagateToSections(
            Deck dst, List<String> remainingIds, OwnershipStatus roundStatus) {

        propagateSectionPass(dst.getMainDeck(), remainingIds, roundStatus);
        propagateSectionPass(dst.getExtraDeck(), remainingIds, roundStatus);
        propagateSectionPass(dst.getSideDeck(), remainingIds, roundStatus);
    }

    private static void propagateSectionPass(
            List<CardElement> section,
            List<String> remaining,
            OwnershipStatus roundStatus) {

        for (CardElement ce : section) {
            if (remaining.isEmpty()) {
                return;
            }
            if (ce.getOwnershipStatus() != OwnershipStatus.MISSING) {
                continue;
            }
            if (Boolean.TRUE.equals(ce.getDontRemove())) {
                continue;
            }
            if (ce.getCard() == null || ce.getCard().getKonamiId() == null) {
                continue;
            }
            int idx = remaining.indexOf(ce.getCard().getKonamiId());
            if (idx >= 0) {
                ce.setOwnershipStatus(roundStatus);
                remaining.remove(idx);
            }
        }
    }

    /**
     * Bug 3 fix — free cardsList satisfaction from deck validations.
     *
     * <p>Only deck slots resolved in the current round ({@code roundStatus})
     * are collected. Slots from earlier rounds were already used in their
     * own round's call and must not re-satisfy collection slots.
     *
     * <p>Per-group deduplication: the group contributes
     * {@code max(resolved count in any single deck)} entries per KonamiId,
     * so two decks in the same group each resolving the same card count as one.
     */
    private static List<CardElement> satisfyCardsListFromDeckValidations(
            List<CardElement> cardsList,
            ThemeCollection col,
            OwnershipStatus roundStatus) {

        List<String> poolIds = new ArrayList<>();

        for (List<Deck> group : col.getLinkedDecks()) {
            Map<String, Integer> groupMaxCounts = new LinkedHashMap<>();

            for (Deck deck : group) {
                Map<String, Integer> deckCounts = new LinkedHashMap<>();
                countKonamiIdsByStatus(deck.getMainDeck(), deckCounts, roundStatus);
                countKonamiIdsByStatus(deck.getExtraDeck(), deckCounts, roundStatus);
                countKonamiIdsByStatus(deck.getSideDeck(), deckCounts, roundStatus);

                for (Map.Entry<String, Integer> entry : deckCounts.entrySet()) {
                    groupMaxCounts.merge(entry.getKey(), entry.getValue(), Math::max);
                }
            }

            for (Map.Entry<String, Integer> entry : groupMaxCounts.entrySet()) {
                for (int i = 0; i < entry.getValue(); i++) {
                    poolIds.add(entry.getKey());
                }
            }
        }

        for (CardElement colCard : cardsList) {
            if (colCard.getCard() == null || colCard.getCard().getKonamiId() == null) {
                continue;
            }
            if (colCard.getOwnershipStatus() != OwnershipStatus.MISSING) {
                continue;
            }
            if (Boolean.TRUE.equals(colCard.getDontRemove())) {
                continue;
            }
            int idx = poolIds.indexOf(colCard.getCard().getKonamiId());
            if (idx >= 0) {
                colCard.setOwnershipStatus(roundStatus);
                poolIds.remove(idx);
            }
        }
        return cardsList;
    }

    /**
     * Counts non-dontRemove cards with {@code status} per KonamiId in
     * {@code section}, accumulating into {@code counts}.
     */
    private static void countKonamiIdsByStatus(
            List<CardElement> section,
            Map<String, Integer> counts,
            OwnershipStatus status) {

        for (CardElement ce : section) {
            if (ce.getOwnershipStatus() != status) {
                continue;
            }
            if (Boolean.TRUE.equals(ce.getDontRemove())) {
                continue;
            }
            if (ce.getCard() == null || ce.getCard().getKonamiId() == null) {
                continue;
            }
            counts.merge(ce.getCard().getKonamiId(), 1, Integer::sum);
        }
    }


    // =========================================================================
    // Quality helpers
    // =========================================================================

    /**
     * Returns {@code true} when {@code ownedCopy} meets the condition and rarity
     * requirements that {@code wantedSlot} specifies.
     *
     * <p>Rules:
     * <ul>
     *   <li>If the wanted slot has no condition requirement, any owned condition is
     *       accepted.</li>
     *   <li>If the wanted slot has a condition requirement, the owned copy must have
     *       a condition whose {@link CardCondition#ordinal()} is less than or equal
     *       to the wanted condition's ordinal (i.e. at least as good: MINT &lt;
     *       NEAR_MINT &lt; … &lt; DAMAGED).</li>
     *   <li>The same rule applies to rarity: if the wanted slot specifies a rarity,
     *       the owned copy must have exactly that rarity (or a higher-ordinal one,
     *       since rarer = lower ordinal for most sets — exact match is safest here,
     *       so we require equality for rarity).</li>
     * </ul>
     *
     * @param wantedSlot the slot in the OuicheList that must be filled
     * @param ownedCopy  the candidate owned {@link CardElement} from the pool
     * @return {@code true} if the owned copy satisfies the quality requirement
     */
    private static boolean ownedCopySatisfiesQuality(
            CardElement wantedSlot, CardElement ownedCopy) {

        // Use the effective condition on both sides so that unset values default
        // to CardCondition.GOOD (see CardElement.getEffectiveCondition()).
        // Lower ordinal = better condition (MINT=0 … DAMAGED=6).
        // A copy satisfies the requirement when its ordinal is ≤ the required ordinal.
        CardCondition requiredCondition = wantedSlot.getEffectiveCondition();
        CardCondition ownedCondition = ownedCopy.getEffectiveCondition();
        if (ownedCondition.ordinal() > requiredCondition.ordinal()) {
            return false;
        }

        // Rarity: explicit requirement must match exactly.
        // No requirement (null) means any rarity is accepted.
        CardRarity requiredRarity = wantedSlot.getRarity();
        if (requiredRarity != null) {
            if (requiredRarity != ownedCopy.getRarity()) {
                return false;
            }
        }

        return true;
    }

    // =========================================================================
    // Section-level pass helpers (non-loose collections and standalone decks)
    // =========================================================================

    /**
     * Applies one full pair of passes (artwork then KonamiId) to a single
     * {@code section} list, consuming matching cards from {@code pool}.
     *
     * <p>Each slot in the section is eligible only if:
     * <ul>
     *   <li>Its {@link CardElement#toString()} does NOT contain {@code "+"} when
     *       {@code dontRemoveOnly} is {@code false} (normal pass).</li>
     *   <li>Its {@link CardElement#toString()} DOES contain {@code "+"} when
     *       {@code dontRemoveOnly} is {@code true} (Bug-4 dontRemove pass).</li>
     *   <li>It is not already marked {@link OwnershipStatus#OWNED} or
     *       {@link OwnershipStatus#OWNED_SUBSTANDARD} from a previous round.</li>
     * </ul>
     *
     * <p>When a pool card matches:
     * <ul>
     *   <li>If {@code qualityRequired} is {@code true}, the pool card is only
     *       accepted when {@link #ownedCopySatisfiesQuality} returns {@code true}
     *       for the (wanted slot, pool card) pair.</li>
     *   <li>The matched slot is marked with {@code status} and the pool card is
     *       consumed.</li>
     * </ul>
     *
     * @param section         the wanted-card section to process (modified in place
     *                        via the returned replacement list)
     * @param pool            the available owned cards (modified in place via the
     *                        returned replacement list)
     * @param dontRemoveOnly  {@code true} to process only {@code "+"}-flagged slots
     * @param qualityRequired {@code true} to enforce quality matching
     * @param status          the {@link OwnershipStatus} to assign on a match
     * @return a two-element list: {@code [updatedSection, remainingPool]}
     */
    private static List<List<CardElement>> applySectionPass(
            List<CardElement> section,
            List<CardElement> pool,
            boolean dontRemoveOnly,
            boolean qualityRequired,
            OwnershipStatus status) {

        List<CardElement> updatedSection = new ArrayList<>(section.size());
        List<CardElement> remainingPool = new ArrayList<>(pool);

        for (CardElement wantedSlot : section) {
            // Already resolved in this round or a previous one — skip.
            if (wantedSlot.getOwnershipStatus() != OwnershipStatus.MISSING) {
                updatedSection.add(wantedSlot);
                continue;
            }
            if (wantedSlot.getCard() == null
                    || wantedSlot.getCard().getKonamiId() == null) {
                updatedSection.add(wantedSlot);
                continue;
            }

            // Eligibility: dontRemoveOnly controls which flag set is processed.
            String slotString = wantedSlot.toString();
            boolean hasDontRemove = slotString.contains("+");
            if (dontRemoveOnly != hasDontRemove) {
                updatedSection.add(wantedSlot);
                continue;
            }

            boolean hasSpecificArtwork = slotString.contains("*");

            // Artwork sub-pass: only for specific-artwork slots.
            boolean matched = false;
            if (hasSpecificArtwork) {
                java.util.Iterator<CardElement> it = remainingPool.iterator();
                while (it.hasNext()) {
                    CardElement poolCard = it.next();
                    if (poolCard.getCard() == null) {
                        continue;
                    }
                    String wantedPath = wantedSlot.getCard().getImagePath();
                    String poolPath = poolCard.getCard().getImagePath();
                    if (wantedPath == null || poolPath == null) {
                        continue;
                    }
                    if (!wantedPath.equals(poolPath)) {
                        continue;
                    }
                    if (qualityRequired && !ownedCopySatisfiesQuality(wantedSlot, poolCard)) {
                        continue;
                    }
                    wantedSlot.setOwnershipStatus(status);
                    it.remove();
                    matched = true;
                    break;
                }
            }

            // KonamiId sub-pass: for all non-artwork slots (and artwork slots that
            // did not match above).
            if (!matched) {
                java.util.Iterator<CardElement> it = remainingPool.iterator();
                while (it.hasNext()) {
                    CardElement poolCard = it.next();
                    if (poolCard.getCard() == null
                            || poolCard.getCard().getKonamiId() == null) {
                        continue;
                    }
                    if (!wantedSlot.getCard().getKonamiId()
                            .equals(poolCard.getCard().getKonamiId())) {
                        continue;
                    }
                    if (qualityRequired && !ownedCopySatisfiesQuality(wantedSlot, poolCard)) {
                        continue;
                    }
                    wantedSlot.setOwnershipStatus(status);
                    it.remove();
                    matched = true;
                    break;
                }
            }

            updatedSection.add(wantedSlot);
        }

        return List.of(updatedSection, remainingPool);
    }

    /**
     * Applies the non-dontRemove passes (artwork then KonamiId) to all three
     * sections of {@code deck}, threading the pool through each section in order
     * (main → extra → side).
     *
     * @param deck            the deck whose sections are updated in place
     * @param pool            the available owned cards at entry
     * @param qualityRequired {@code true} to enforce quality matching
     * @param status          the {@link OwnershipStatus} to assign on a match
     * @return the pool as it stands after all three sections have been processed
     */
    private static List<CardElement> applyNonDontRemovePassesToDeck(
            Deck deck,
            List<CardElement> pool,
            boolean qualityRequired,
            OwnershipStatus status) {

        List<List<CardElement>> result;

        result = applySectionPass(deck.getMainDeck(), pool, false, qualityRequired, status);
        deck.setMainDeck(result.get(0));
        pool = result.get(1);

        result = applySectionPass(deck.getExtraDeck(), pool, false, qualityRequired, status);
        deck.setExtraDeck(result.get(0));
        pool = result.get(1);

        result = applySectionPass(deck.getSideDeck(), pool, false, qualityRequired, status);
        deck.setSideDeck(result.get(0));
        pool = result.get(1);

        return pool;
    }

    /**
     * Applies the dontRemove (Bug-4) passes (artwork then KonamiId) to all three
     * sections of {@code deck}, threading the pool through each section in order.
     *
     * @param deck            the deck whose sections are updated in place
     * @param pool            the available owned cards at entry
     * @param qualityRequired {@code true} to enforce quality matching
     * @param status          the {@link OwnershipStatus} to assign on a match
     * @return the pool as it stands after all three sections have been processed
     */
    private static List<CardElement> applyDontRemovePassesToDeck(
            Deck deck,
            List<CardElement> pool,
            boolean qualityRequired,
            OwnershipStatus status) {

        List<List<CardElement>> result;

        result = applySectionPass(deck.getMainDeck(), pool, true, qualityRequired, status);
        deck.setMainDeck(result.get(0));
        pool = result.get(1);

        result = applySectionPass(deck.getExtraDeck(), pool, true, qualityRequired, status);
        deck.setExtraDeck(result.get(0));
        pool = result.get(1);

        result = applySectionPass(deck.getSideDeck(), pool, true, qualityRequired, status);
        deck.setSideDeck(result.get(0));
        pool = result.get(1);

        return pool;
    }

    // =========================================================================
    // Loose-collection pass helper
    // =========================================================================

    /**
     * Applies one full artwork+KonamiId loose pass to {@code section}, trying
     * {@code loosePool} first and falling back to {@code unusedPool} for each
     * unmatched slot.
     *
     * <p>Unlike the non-loose pass, matched slots are <em>replaced</em> by the
     * actual pool {@link CardElement} instance (marked with {@code status}) so
     * that {@code CreateOuicheList} can deduplicate by instance identity.
     *
     * <p>Slots already resolved from a previous round (status ≠ {@code MISSING})
     * are passed through unchanged.
     *
     * @param section         the wanted-card section (artwork pass only touches
     *                        {@code "*"}-flagged slots; KonamiId pass touches the rest)
     * @param loosePool       the fresh per-collection owned pool (preferred source)
     * @param unusedPool      the depleted global pool (fallback source)
     * @param qualityRequired {@code true} to enforce quality matching
     * @param status          the {@link OwnershipStatus} to assign on a match
     * @return a three-element list: {@code [updatedSection, remainingLoosePool,
     * remainingUnusedPool]}
     */
    private static List<List<CardElement>> applyLoosePasses(
            List<CardElement> section,
            List<CardElement> loosePool,
            List<CardElement> unusedPool,
            boolean qualityRequired,
            OwnershipStatus status) {

        java.util.function.BiPredicate<Card, Card> artworkComparator =
                (c1, c2) -> c1.getImagePath() != null
                        && c2.getImagePath() != null
                        && c1.getImagePath().equals(c2.getImagePath());
        java.util.function.BiPredicate<Card, Card> konamiComparator =
                (c1, c2) -> c1.getKonamiId() != null
                        && c2.getKonamiId() != null
                        && c1.getKonamiId().equals(c2.getKonamiId());

        List<String> artMustContain = List.of("*");
        List<String> artMustNotContain = List.of("+");
        List<String> idMustNotContain = List.of("+", "*");

        // Artwork sub-pass: loosePool first, then unusedPool fallback.
        List<List<CardElement>> artLoose = loosePassReplace(
                section, loosePool, artworkComparator,
                artMustContain, artMustNotContain, qualityRequired, status);
        loosePool = artLoose.get(1);

        List<List<CardElement>> artUnused = loosePassReplace(
                artLoose.get(0), unusedPool, artworkComparator,
                artMustContain, artMustNotContain, qualityRequired, status);
        unusedPool = artUnused.get(1);

        // KonamiId sub-pass: loosePool first, then unusedPool fallback.
        List<List<CardElement>> idLoose = loosePassReplace(
                artUnused.get(0), loosePool, konamiComparator,
                null, idMustNotContain, qualityRequired, status);
        loosePool = idLoose.get(1);

        List<List<CardElement>> idUnused = loosePassReplace(
                idLoose.get(0), unusedPool, konamiComparator,
                null, idMustNotContain, qualityRequired, status);
        unusedPool = idUnused.get(1);

        return List.of(idUnused.get(0), loosePool, unusedPool);
    }

    /**
     * Loose-collection pass: for each element in {@code sectionList}, tries to find
     * a matching element in {@code pool}. On a match the sectionList slot is replaced
     * by the actual pool instance (marked with {@code status}) and that instance is
     * removed from the pool. Unmatched slots are left as-is.
     *
     * <p>{@code mustContain} / {@code mustNotContain} filter which sectionList
     * elements are eligible for this pass (same semantics as the old
     * {@code ListDifIntersect} helpers). Already-resolved elements (status ≠
     * {@code MISSING}) are always passed through unchanged.
     *
     * @param qualityRequired when {@code true} a pool card is only accepted when
     *                        {@link #ownedCopySatisfiesQuality} returns {@code true}
     * @param status          the {@link OwnershipStatus} to assign to the pool
     *                        instance on a match
     * @return [modified sectionList, remaining pool]
     */
    private static List<List<CardElement>> loosePassReplace(
            List<CardElement> sectionList,
            List<CardElement> pool,
            java.util.function.BiPredicate<Card, Card> comparator,
            List<String> mustContain,
            List<String> mustNotContain,
            boolean qualityRequired,
            OwnershipStatus status) {

        List<CardElement> result = new ArrayList<>(sectionList.size());
        List<CardElement> remainingPool = new ArrayList<>(pool);

        for (CardElement sectionCard : sectionList) {
            // Already resolved in this round or a previous one — keep as-is.
            if (sectionCard.getOwnershipStatus() != OwnershipStatus.MISSING) {
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
                    (mustContain == null
                            || mustContain.stream().allMatch(valueA::contains))
                            && (mustNotContain == null
                            || mustNotContain.stream().noneMatch(valueA::contains));

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
                        || poolCard.getCard().getKonamiId() == null) {
                    continue;
                }
                if (!comparator.test(sectionCard.getCard(), poolCard.getCard())) {
                    continue;
                }
                if (qualityRequired
                        && !ownedCopySatisfiesQuality(sectionCard, poolCard)) {
                    continue;
                }
                poolCard.setOwnershipStatus(status);
                result.add(poolCard);   // replace the slot with the real pool instance
                it.remove();
                matched = true;
                break;
            }

            if (!matched) {
                result.add(sectionCard);    // no match — keep the MISSING slot as-is
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
                if (card.getOwnershipStatus() == OwnershipStatus.MISSING) {
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