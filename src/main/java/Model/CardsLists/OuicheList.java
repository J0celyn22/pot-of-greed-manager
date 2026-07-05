package Model.CardsLists;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OuicheList {
    private static OwnedCardsCollection myCardsCollection;
    private static DecksAndCollectionsList decksList;

    // The canonical compact form of the OuicheList ("maOuicheList" is a word play on "wishlist").
    // Key = imagePath + "|" + printCode + "|" + conditionCode + "|" + rarityCode.
    // Storing the Map directly means consumers never have to rebuild it themselves.
    // The CardElement instances stored here are INDEPENDENT COPIES of those in detailedOuicheList,
    // so that mutations made by downstream generators cannot affect the detailed view.
    private static LinkedHashMap<String, CardElement> maOuicheList;
    private static LinkedHashMap<String, Integer> maOuicheListCounts;

    // Parallel compact list for OWNED_SUBSTANDARD slots: cards the user owns but
    // at insufficient condition or rarity, keyed by the same composite key.
    private static LinkedHashMap<String, CardElement> maOuicheListSubstandard;
    private static LinkedHashMap<String, Integer> maOuicheListSubstandardCounts;

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
     * keyed by the same composite key as {@link #getMaOuicheList()}.
     *
     * @return the count map, or {@code null} if not yet generated.
     */
    public static LinkedHashMap<String, Integer> getMaOuicheListCounts() {
        return maOuicheListCounts;
    }

    /**
     * Returns the compact substandard-quality list: cards for which the user
     * owns a copy but it does not meet the condition or rarity requirement of
     * the slot.  Keyed by the same composite key as {@link #getMaOuicheList()}.
     *
     * @return the substandard map, or {@code null} if not yet generated.
     */
    public static LinkedHashMap<String, CardElement> getMaOuicheListSubstandard() {
        return maOuicheListSubstandard;
    }

    /**
     * Returns the required count for each card in the substandard list,
     * keyed by the same composite key as {@link #getMaOuicheListSubstandard()}.
     *
     * @return the substandard count map, or {@code null} if not yet generated.
     */
    public static LinkedHashMap<String, Integer> getMaOuicheListSubstandardCounts() {
        return maOuicheListSubstandardCounts;
    }

    /**
     * Sets the missing-cards compact map (for testing and incremental updates).
     */
    public static void setMaOuicheList(LinkedHashMap<String, CardElement> map) {
        maOuicheList = map;
    }

    /**
     * Sets the missing-cards count map (for testing and incremental updates).
     */
    public static void setMaOuicheListCounts(LinkedHashMap<String, Integer> map) {
        maOuicheListCounts = map;
    }

    /**
     * Sets the substandard compact map (for testing and incremental updates).
     */
    public static void setMaOuicheListSubstandard(LinkedHashMap<String, CardElement> map) {
        maOuicheListSubstandard = map;
    }

    /**
     * Sets the substandard count map (for testing and incremental updates).
     */
    public static void setMaOuicheListSubstandardCounts(LinkedHashMap<String, Integer> map) {
        maOuicheListSubstandardCounts = map;
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
    /**
     * Builds the grouping key for a {@link CardElement} in the compact OuicheList.
     *
     * <p>Two slots are grouped together when they share all of: artwork (imagePath),
     * printCode, condition requirement, and rarity requirement.  Absent fields are
     * treated as "no constraint" and represented as empty strings so that two slots
     * with the same artwork but no printCode are still grouped together.
     *
     * <p>Key format: {@code imagePath|printCode|conditionCode|rarityCode}
     */
    static String cardKey(CardElement ce) {
        Card card = ce.getCard();
        // Artwork base: imagePath preferred; passCode or KonamiId as fallbacks.
        String base = card.getImagePath() != null ? card.getImagePath()
                : card.getPassCode() != null ? card.getPassCode()
                : card.getKonamiId();
        if (base == null) {
            return null;
        }
        String printPart = card.getPrintCode() != null ? card.getPrintCode() : "";
        String condPart = ce.getCondition() != null ? ce.getCondition().getCode() : "";
        String rarPart = ce.getRarity() != null ? ce.getRarity().getCode() : "";
        return base + "|" + printPart + "|" + condPart + "|" + rarPart;
    }

    /**
     * Returns a new list of fresh {@link CardElement} copies built from the given list.
     * Each copy wraps the same {@link Card} object but is an independent {@link CardElement}
     * instance, so that {@code setValues()} calls on the copies never affect the originals.
     */
    static List<CardElement> copyCardElements(List<CardElement> original) {
        if (original == null) return new ArrayList<>();
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
     * <p>If {@link #createDetailedOuicheList} has already been called, its markings are
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
    public static void createOuicheList(OwnedCardsCollection ownedCardsCollection,
                                        DecksAndCollectionsList decksList) throws Exception {
        listsIntersection = new ArrayList<>();
        if (detailedOuicheList == null) {
            createDetailedOuicheList(ownedCardsCollection, decksList);
        }

        maOuicheList = new LinkedHashMap<>();
        maOuicheListCounts = new LinkedHashMap<>();
        buildCompactMapForStatus(OwnershipStatus.MISSING, maOuicheList, maOuicheListCounts);

        maOuicheListSubstandard = new LinkedHashMap<>();
        maOuicheListSubstandardCounts = new LinkedHashMap<>();
        buildCompactMapForStatus(
                OwnershipStatus.OWNED_SUBSTANDARD,
                maOuicheListSubstandard,
                maOuicheListSubstandardCounts);
    }

    // =========================================================================
    // Incremental updates
    //
    // The methods below keep an already-generated OuicheList close to what
    // a full {@link #CreateOuicheList} regeneration would produce, without
    // recomputing everything from scratch. Each is a no-op when
    // {@link #detailedOuicheList} is {@code null} (OuicheList not yet generated).
    // The heavy lifting lives in {@link OuicheListUpdater} to keep this file
    // focused on full-generation logic.
    // =========================================================================

    /**
     * Call after a card is added to the {@link OwnedCardsCollection} (My Collection tab).
     *
     * <p>If the added copy can fill a currently-missing (or substandard) slot in the
     * detailed OuicheList, that slot is marked owned (or substandard) following the
     * same matching order as {@link #createDetailedOuicheList}. Otherwise the card is
     * added to {@link #unusedCards} (the "Available cards" list).
     *
     * @param addedCard the {@link CardElement} that was just added to My Collection
     */
    public static void onOwnedCardAdded(CardElement addedCard) {
        if (detailedOuicheList == null || addedCard == null) {
            return;
        }
        OuicheListUpdater.onOwnedCardAdded(addedCard);
    }

    /**
     * Call after a card is removed from the {@link OwnedCardsCollection} (My Collection tab).
     *
     * <p><b>Must be called after</b> {@code removedCard} has actually been removed from
     * {@link #myCardsCollection} — the category-name lookup performed by
     * {@link OuicheListUpdater#onOwnedCardRemoved} reflects the post-removal state of the
     * owned collection.
     *
     * <p>Looks up the detailed OuicheList for the last owned/substandard occurrence of
     * this card and either unmarks it (back to MISSING) or skips it if another owned
     * copy still covers it, per the rules described in
     * {@link OuicheListUpdater#onOwnedCardRemoved}.
     *
     * @param removedCard the {@link CardElement} that was just removed from My Collection
     */
    public static void onOwnedCardRemoved(CardElement removedCard) {
        if (detailedOuicheList == null || removedCard == null) {
            return;
        }
        OuicheListUpdater.onOwnedCardRemoved(removedCard);
    }

    /**
     * Call after a card is added to a {@link Deck} or {@link ThemeCollection#getCardsList()}
     * (Decks and Collections tab).
     *
     * <p>Locates the corresponding deck (or collection) inside the detailed OuicheList by
     * name, inserts the new slot there, then attempts to satisfy it from
     * {@link #unusedCards} (marking it OWNED or OWNED_SUBSTANDARD and removing the consumed
     * copy from the available list) or leaves it MISSING.
     *
     * @param addedCard      the new wanted-card slot (a fresh, {@link OwnershipStatus#MISSING}
     *                       {@link CardElement}, not yet inserted into the detailed OuicheList)
     * @param deckName       the name of the deck the card was added to, or {@code null} if added
     *                       directly to a {@link ThemeCollection#getCardsList()}
     * @param section        {@code "main"}, {@code "extra"}, or {@code "side"} when
     *                       {@code deckName} is non-{@code null}; ignored otherwise
     * @param collectionName the name of the {@link ThemeCollection} that owns the deck named
     *                       {@code deckName} (or that the card was added to directly when
     *                       {@code deckName} is {@code null}), or {@code null} for a standalone
     *                       deck
     */
    public static void onDeckCardAdded(CardElement addedCard, String deckName, String section,
                                       String collectionName) {
        onDeckCardAdded(addedCard, deckName, section, collectionName, Integer.MAX_VALUE);
    }

    /**
     * Call after a card is added to a {@link Deck} or {@link ThemeCollection#getCardsList()}
     * (Decks and Collections tab) at a specific position.
     *
     * <p>Identical to {@link #onDeckCardAdded(CardElement, String, String, String)} except
     * that the new slot is inserted at {@code insertionIndex} within the target section
     * instead of being appended, so the detailed OuicheList's ordering mirrors where the
     * card actually landed in the live Decks and Collections list.
     *
     * @param addedCard      the new wanted-card slot (a fresh, {@link OwnershipStatus#MISSING}
     *                       {@link CardElement}, not yet inserted into the detailed OuicheList)
     * @param deckName       the name of the deck the card was added to, or {@code null} if added
     *                       directly to a {@link ThemeCollection#getCardsList()}
     * @param section        {@code "main"}, {@code "extra"}, or {@code "side"} when
     *                       {@code deckName} is non-{@code null}; ignored otherwise
     * @param collectionName the name of the {@link ThemeCollection} that owns the deck named
     *                       {@code deckName} (or that the card was added to directly when
     *                       {@code deckName} is {@code null}), or {@code null} for a standalone
     *                       deck
     * @param insertionIndex the position within the target section to insert at; values at or
     *                       beyond the section's current size (including
     *                       {@link Integer#MAX_VALUE}) clamp to an append at the end
     */
    public static void onDeckCardAdded(CardElement addedCard, String deckName, String section,
                                       String collectionName, int insertionIndex) {
        if (detailedOuicheList == null || addedCard == null) {
            return;
        }
        OuicheListUpdater.onDeckCardAdded(addedCard, deckName, section, collectionName, insertionIndex);
    }

    /**
     * Call after a card is removed from a {@link Deck} or {@link ThemeCollection#getCardsList()}
     * (Decks and Collections tab).
     *
     * <p>Locates the corresponding deck (or collection) inside the detailed OuicheList by
     * name and removes the matching slot from it. If the removed slot was MISSING, it is
     * simply dropped. Otherwise its OWNED/OWNED_SUBSTANDARD copy is freed and propagated to
     * the next eligible MISSING slot (by KonamiId) across the whole detailed OuicheList,
     * following the rules in {@link OuicheListUpdater#onDeckCardRemoved}.
     *
     * @param removedCard    the {@link CardElement} slot that was just removed
     * @param deckName       the name of the deck the card was removed from, or {@code null} if
     *                       removed from a {@link ThemeCollection#getCardsList()}
     * @param section        {@code "main"}, {@code "extra"}, or {@code "side"} when
     *                       {@code deckName} is non-{@code null}; ignored otherwise
     * @param collectionName the name of the {@link ThemeCollection} that owns the deck named
     *                       {@code deckName} (or that the card was removed from directly when
     *                       {@code deckName} is {@code null}), or {@code null} for a standalone
     *                       deck
     */
    public static void onDeckCardRemoved(CardElement removedCard, String deckName, String section,
                                         String collectionName) {
        if (detailedOuicheList == null || removedCard == null) {
            return;
        }
        OuicheListUpdater.onDeckCardRemoved(removedCard, deckName, section, collectionName);
    }

    /**
     * Call after a card is repositioned within the same {@link Deck} section or
     * {@link ThemeCollection#getCardsList()} (an intra-group reorder in the Decks and
     * Collections tab — no card left or entered the section, only its position changed).
     *
     * <p>Locates the matching slot inside the detailed OuicheList by name/section and moves
     * it to {@code newIndex}, leaving its ownership status untouched — a reorder never
     * changes what is or isn't owned. See {@link OuicheListUpdater#onDeckCardMoved} for the
     * matching rules used to locate the slot.
     *
     * @param movedCard      the live {@link CardElement} that was repositioned
     * @param deckName       the name of the deck the card was reordered within, or
     *                       {@code null} if reordered within a
     *                       {@link ThemeCollection#getCardsList()}
     * @param section        {@code "main"}, {@code "extra"}, or {@code "side"} when
     *                       {@code deckName} is non-{@code null}; ignored otherwise
     * @param collectionName the name of the {@link ThemeCollection} that owns the deck named
     *                       {@code deckName} (or that the card was reordered within directly
     *                       when {@code deckName} is {@code null}), or {@code null} for a
     *                       standalone deck
     * @param newIndex       the card's new position within the target section; values at or
     *                       beyond the section's current size clamp to the end
     */
    public static void onDeckCardMoved(CardElement movedCard, String deckName, String section,
                                       String collectionName, int newIndex) {
        if (detailedOuicheList == null || movedCard == null) {
            return;
        }
        OuicheListUpdater.onDeckCardMoved(movedCard, deckName, section, collectionName, newIndex);
    }


    // ── Computation delegation ────────────────────────────────────────────────
    // The methods below have moved to OuicheListComputer. These package-private
    // forwarding stubs let OuicheListUpdater continue calling addToMap and
    // ownedCopySatisfiesQuality without changes.

    public static DecksAndCollectionsList createDetailedOuicheList(
            OwnedCardsCollection ownedCardsCollection,
            DecksAndCollectionsList inputDecksList) {
        return OuicheListComputer.createDetailedOuicheList(ownedCardsCollection, inputDecksList);
    }

    static void buildCompactMapForStatus(
            OwnershipStatus targetStatus,
            LinkedHashMap<String, CardElement> targetMap,
            LinkedHashMap<String, Integer> targetCounts) {
        OuicheListComputer.buildCompactMapForStatus(targetStatus, targetMap, targetCounts);
    }

    static void addToMap(
            LinkedHashMap<String, CardElement> map,
            LinkedHashMap<String, Integer> countsMap,
            String key, int count, CardElement rep) {
        OuicheListComputer.addToMap(map, countsMap, key, count, rep);
    }

    static boolean ownedCopySatisfiesQuality(CardElement wantedSlot, CardElement ownedCopy) {
        return OuicheListComputer.ownedCopySatisfiesQuality(wantedSlot, ownedCopy);
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