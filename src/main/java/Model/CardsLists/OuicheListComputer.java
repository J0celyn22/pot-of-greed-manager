package Model.CardsLists;

import java.util.*;

/**
 * Pure computation for the OuicheList: building the detailed difference list,
 * all ownership-matching passes, the compact summary maps, and the pool index.
 *
 * <p>All persistent state is stored in {@link OuicheList} and accessed via its
 * static getters and setters.  Nothing in this class is saved to disk — that
 * belongs to {@link OuicheListIO}.
 *
 * <p>Package-private: only {@link OuicheList} and its siblings in
 * {@code Model.CardsLists} should call these methods directly.
 */
final class OuicheListComputer {

    private OuicheListComputer() { /* static utility */ }

    /**
     * Populates {@code targetMap} / {@code targetCounts} with entries from the
     * detailed OuicheList whose {@link OwnershipStatus} equals {@code targetStatus}.
     *
     * <p>The same three counting rules as described in the {@link #CreateOuicheList}
     * Javadoc apply:
     * <ol>
     *   <li>Group-max within a deck group, sum across groups.</li>
     *   <li>Non-dontRemove cardsList shares with decks (max of the two).</li>
     *   <li>Loose-collection keys only added if not already in {@code targetMap}.</li>
     * </ol>
     */
    static void buildCompactMapForStatus(
            OwnershipStatus targetStatus,
            LinkedHashMap<String, CardElement> targetMap,
            LinkedHashMap<String, Integer> targetCounts) {

        // ── Non-loose collections ─────────────────────────────────────────────────
        if (OuicheList.getDetailedOuicheList().getCollections() != null) {
            for (ThemeCollection col : OuicheList.getDetailedOuicheList().getCollections()) {
                if (Boolean.TRUE.equals(col.getConnectToWholeCollection())) {
                    continue;
                }

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
                            if (ce.getOwnershipStatus() != targetStatus) {
                                continue;
                            }
                            String key = OuicheList.cardKey(ce);
                            if (key == null) {
                                continue;
                            }
                            repCards.putIfAbsent(key, ce);
                            if (Boolean.TRUE.equals(ce.getDontRemove())) {
                                deckDontRemove.merge(key, 1, Integer::sum);
                            } else {
                                deckNormal.merge(key, 1, Integer::sum);
                            }
                        }
                        deckNormal.forEach((k, v) -> groupMaxNormal.merge(k, v, Math::max));
                        deckDontRemove.forEach((k, v) -> groupMaxDontRemove.merge(k, v, Math::max));
                    }
                    groupMaxNormal.forEach((k, v) -> deckNormalContrib.merge(k, v, Integer::sum));
                    groupMaxDontRemove.forEach((k, v) -> deckDontRemoveContrib.merge(k, v, Integer::sum));
                }

                Map<String, Integer> listNormal = new LinkedHashMap<>();
                Map<String, Integer> listDontRemove = new LinkedHashMap<>();
                for (CardElement ce : col.getCardsList()) {
                    if (ce.getOwnershipStatus() != targetStatus) {
                        continue;
                    }
                    String key = OuicheList.cardKey(ce);
                    if (key == null) {
                        continue;
                    }
                    repCards.putIfAbsent(key, ce);
                    if (Boolean.TRUE.equals(ce.getDontRemove())) {
                        listDontRemove.merge(key, 1, Integer::sum);
                    } else {
                        listNormal.merge(key, 1, Integer::sum);
                    }
                }

                // Rule 2: non-dontRemove key needs max(deckContrib, cardsListContrib)
                Set<String> allNormalKeys = new HashSet<>(deckNormalContrib.keySet());
                allNormalKeys.addAll(listNormal.keySet());
                for (String key : allNormalKeys) {
                    int needed = Math.max(
                            deckNormalContrib.getOrDefault(key, 0),
                            listNormal.getOrDefault(key, 0));
                    if (needed <= 0) {
                        continue;
                    }
                    addToMap(targetMap, targetCounts, key, needed, repCards.get(key));
                }

                deckDontRemoveContrib.forEach((key, needed) ->
                        addToMap(targetMap, targetCounts, key, needed, repCards.get(key)));
                listDontRemove.forEach((key, needed) ->
                        addToMap(targetMap, targetCounts, key, needed, repCards.get(key)));
            }
        }

        // ── Standalone decks ─────────────────────────────────────────────────────
        if (OuicheList.getDetailedOuicheList().getDecks() != null) {
            for (Deck deck : OuicheList.getDetailedOuicheList().getDecks()) {
                for (CardElement ce : deck.toList()) {
                    if (ce.getOwnershipStatus() != targetStatus) {
                        continue;
                    }
                    String key = OuicheList.cardKey(ce);
                    if (key == null) {
                        continue;
                    }
                    addToMap(targetMap, targetCounts, key, 1, ce);
                }
            }
        }

        // ── Loose collections (Rule 3) ────────────────────────────────────────────
        if (OuicheList.getDetailedOuicheList().getCollections() != null) {
            for (ThemeCollection col : OuicheList.getDetailedOuicheList().getCollections()) {
                if (!Boolean.TRUE.equals(col.getConnectToWholeCollection())) {
                    continue;
                }

                Map<String, Integer> looseContrib = new LinkedHashMap<>();
                Map<String, CardElement> repCards = new LinkedHashMap<>();

                for (List<Deck> group : col.getLinkedDecks()) {
                    Map<String, Integer> groupMax = new HashMap<>();
                    for (Deck deck : group) {
                        Map<String, Integer> deckCounts = new HashMap<>();
                        for (CardElement ce : deck.toList()) {
                            if (ce.getOwnershipStatus() != targetStatus) {
                                continue;
                            }
                            String key = OuicheList.cardKey(ce);
                            if (key == null) {
                                continue;
                            }
                            repCards.putIfAbsent(key, ce);
                            deckCounts.merge(key, 1, Integer::sum);
                        }
                        deckCounts.forEach((k, v) -> groupMax.merge(k, v, Math::max));
                    }
                    groupMax.forEach((k, v) -> looseContrib.merge(k, v, Integer::sum));
                }
                for (CardElement ce : col.getCardsList()) {
                    if (ce.getOwnershipStatus() != targetStatus) {
                        continue;
                    }
                    String key = OuicheList.cardKey(ce);
                    if (key == null) {
                        continue;
                    }
                    repCards.putIfAbsent(key, ce);
                    looseContrib.merge(key, 1, Integer::sum);
                }

                // Rule 3: only add keys not already in the target map
                looseContrib.forEach((key, count) -> {
                    if (!targetMap.containsKey(key)) {
                        targetMap.put(key, new CardElement(repCards.get(key)));
                        targetCounts.put(key, count);
                    }
                });
            }
        }
    }

    /**
     * Adds {@code count} copies of the card identified by {@code key} to the global
     * OuicheList map, delegating to {@link #addToMap}.
     */
    static void addToGlobalMap(String key, int count, CardElement rep) {
        addToMap(OuicheList.getMaOuicheList(), OuicheList.getMaOuicheListCounts(), key, count, rep);
    }

    /**
     * Adds {@code count} copies of the card identified by {@code key} to
     * {@code map} / {@code countsMap}.  Creates a fresh representative entry
     * via the copy constructor (preserving condition, rarity, and artwork flags)
     * on the first insertion, and accumulates counts on subsequent ones.
     */
    static void addToMap(
            LinkedHashMap<String, CardElement> map,
            LinkedHashMap<String, Integer> countsMap,
            String key, int count, CardElement rep) {

        if (!map.containsKey(key)) {
            map.put(key, new CardElement(rep));
            countsMap.put(key, count);
        } else {
            countsMap.merge(key, count, Integer::sum);
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
    public static DecksAndCollectionsList createDetailedOuicheList(OwnedCardsCollection ownedCardsCollection, DecksAndCollectionsList decksList) {
        OuicheList.setListsIntersection(new ArrayList<>());

        OuicheList.setDetailedOuicheList(new DecksAndCollectionsList());

        // -----------------------------------------------------------------------
        // Deep-copy phase — non-loose collections first, then standalone decks,
        // then loose collections. This ordering means OuicheList.getDetailedOuicheList().toList()
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
                        deckCopy.setMainDeck(OuicheList.copyCardElements(originalDeck.getMainDeck()));
                        deckCopy.setExtraDeck(OuicheList.copyCardElements(originalDeck.getExtraDeck()));
                        deckCopy.setSideDeck(OuicheList.copyCardElements(originalDeck.getSideDeck()));
                        if (firstInGroup) {
                            collectionCopy.addDeck(deckCopy);
                            firstInGroup = false;
                        } else {
                            collectionCopy.addDeckToExistingUnit(deckCopy, collectionCopy.getLinkedDecks().size() - 1);
                        }
                    }
                }
                collectionCopy.setCardsList(OuicheList.copyCardElements(originalCollection.getCardsList()));
                OuicheList.getDetailedOuicheList().addCollection(collectionCopy);
            }
        }

        if (decksList.getDecks() != null) {
            for (Deck originalDeck : decksList.getDecks()) {
                Deck deckCopy = new Deck();
                deckCopy.setName(originalDeck.getName());
                deckCopy.setMainDeck(OuicheList.copyCardElements(originalDeck.getMainDeck()));
                deckCopy.setExtraDeck(OuicheList.copyCardElements(originalDeck.getExtraDeck()));
                deckCopy.setSideDeck(OuicheList.copyCardElements(originalDeck.getSideDeck()));
                OuicheList.getDetailedOuicheList().addDeck(deckCopy);
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
                        deckCopy.setMainDeck(OuicheList.copyCardElements(originalDeck.getMainDeck()));
                        deckCopy.setExtraDeck(OuicheList.copyCardElements(originalDeck.getExtraDeck()));
                        deckCopy.setSideDeck(OuicheList.copyCardElements(originalDeck.getSideDeck()));
                        if (firstInGroup) {
                            collectionCopy.addDeck(deckCopy);
                            firstInGroup = false;
                        } else {
                            collectionCopy.addDeckToExistingUnit(deckCopy, collectionCopy.getLinkedDecks().size() - 1);
                        }
                    }
                }
                collectionCopy.setCardsList(OuicheList.copyCardElements(originalCollection.getCardsList()));
                OuicheList.getDetailedOuicheList().addCollection(collectionCopy);
            }
        }

        // -----------------------------------------------------------------------
        // Ownership-removal phase.
        //
        // OuicheList.getUnusedCards() starts as a fresh copy of the entire owned collection.
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
        //   (3) Loose collections: looseOwnedPool first, then OuicheList.getUnusedCards() fallback
        // -----------------------------------------------------------------------
        OuicheList.setUnusedCards(OuicheList.copyCardElements(ownedCardsCollection.toList()));
        OuicheListPoolIndex unusedPool = new OuicheListPoolIndex(OuicheList.getUnusedCards());

        for (OwnershipStatus roundStatus : new OwnershipStatus[]{OwnershipStatus.OWNED, OwnershipStatus.OWNED_SUBSTANDARD}) {
            boolean qualityRequired = (roundStatus == OwnershipStatus.OWNED);

            // --- (1) Non-loose collections ---
            if (OuicheList.getDetailedOuicheList().getCollections() != null) {
                for (ThemeCollection col : OuicheList.getDetailedOuicheList().getCollections()) {
                    if (Boolean.TRUE.equals(col.getConnectToWholeCollection())) {
                        continue;
                    }

                    for (List<Deck> deckGroup : col.getLinkedDecks()) {
                        // (a) non-dontRemove passes for each deck in the group
                        for (Deck deck : deckGroup) {
                            applyNonDontRemovePassesToDeck(deck, unusedPool, qualityRequired, roundStatus);
                        }
                        // (b) Bug 2: free within-group propagation (no pool consumed)
                        if (deckGroup.size() > 1) {
                            propagateGroupValidation(deckGroup);
                        }
                        // (c) Bug 4: dontRemove passes, independent per deck
                        for (Deck deck : deckGroup) {
                            applyDontRemovePassesToDeck(deck, unusedPool, qualityRequired, roundStatus);
                        }
                    }

                    // (d) Bug 3: free satisfaction of cardsList from deck validations, then
                    //     remaining cardsList passes (non-dontRemove, then dontRemove).
                    col.setCardsList(satisfyCardsListFromDeckValidations(col.getCardsList(), col));

                    col.setCardsList(applySectionPass(col.getCardsList(), unusedPool, false, qualityRequired, roundStatus));
                    col.setCardsList(applySectionPass(col.getCardsList(), unusedPool, true, qualityRequired, roundStatus));
                }
            }

            // --- (2) Standalone decks ---
            if (OuicheList.getDetailedOuicheList().getDecks() != null) {
                for (Deck deck : OuicheList.getDetailedOuicheList().getDecks()) {
                    applyNonDontRemovePassesToDeck(deck, unusedPool, qualityRequired, roundStatus);
                    applyDontRemovePassesToDeck(deck, unusedPool, qualityRequired, roundStatus);
                }
            }

            // --- (3) Loose collections ---
            // Each loose collection validates against a fresh full-ownership pool first
            // (looseOwnedPool). Cards still unvalidated after that fall back to the
            // depleted OuicheList.getUnusedCards() from passes 1-2. Both pools shrink as they are used.
            //
            // Unlike non-loose passes, matched slots are REPLACED by the actual owned
            // CardElement instance from the pool, preserving the identity link to the
            // original owned card so CreateOuicheList can deduplicate by instance.
            OuicheListPoolIndex looseOwnedPool = new OuicheListPoolIndex(OuicheList.copyCardElements(ownedCardsCollection.toList()));

            if (OuicheList.getDetailedOuicheList().getCollections() != null) {
                for (ThemeCollection col : OuicheList.getDetailedOuicheList().getCollections()) {
                    if (!Boolean.TRUE.equals(col.getConnectToWholeCollection())) {
                        continue;
                    }

                    for (List<Deck> deckGroup : col.getLinkedDecks()) {
                        for (Deck deck : deckGroup) {
                            deck.setMainDeck(applyLoosePasses(
                                    deck.getMainDeck(), looseOwnedPool, unusedPool, qualityRequired, roundStatus));
                            deck.setExtraDeck(applyLoosePasses(
                                    deck.getExtraDeck(), looseOwnedPool, unusedPool, qualityRequired, roundStatus));
                            deck.setSideDeck(applyLoosePasses(
                                    deck.getSideDeck(), looseOwnedPool, unusedPool, qualityRequired, roundStatus));
                        }
                    }

                    col.setCardsList(applyLoosePasses(
                            col.getCardsList(), looseOwnedPool, unusedPool, qualityRequired, roundStatus));
                }
            }
        }

        OuicheList.setUnusedCards(unusedPool.toList());

        return OuicheList.getDetailedOuicheList();
    }

    /**
     * Bug 2 fix — within-group propagation for non-loose collections.
     *
     * <p>After ownership passes have resolved some cards (status ≠ MISSING,
     * dontRemove = false) in the decks of a group, propagates those resolutions
     * for free to the sibling decks in the same group.  One resolved card in
     * deck D1 satisfies one slot of the same KonamiId in every sibling deck Dk
     * without consuming an additional owned copy.
     *
     * <p>Only cards that were resolved before this call (snapshot) are
     * propagated; slots that become resolved as a result of propagation are
     * never re-propagated, so there is no risk of cascading or double-counting.
     *
     * <p>Propagated slots always receive {@link OwnershipStatus#OWNED} — free
     * within-group propagation only applies to confirmed ownership.
     */
    private static void propagateGroupValidation(List<Deck> deckGroup) {
        // Snapshot the already-resolved (non-dontRemove) KonamiIds per deck.
        List<List<String>> validatedKonamiIdsPerDeck = new ArrayList<>();
        for (Deck deck : deckGroup) {
            List<String> ids = new ArrayList<>();
            collectValidatedKonamiIds(deck.getMainDeck(), ids);
            collectValidatedKonamiIds(deck.getExtraDeck(), ids);
            collectValidatedKonamiIds(deck.getSideDeck(), ids);
            validatedKonamiIdsPerDeck.add(ids);
        }

        for (int srcIdx = 0; srcIdx < deckGroup.size(); srcIdx++) {
            List<String> toPropagate = validatedKonamiIdsPerDeck.get(srcIdx);
            for (int dstIdx = 0; dstIdx < deckGroup.size(); dstIdx++) {
                if (dstIdx == srcIdx) {
                    continue;
                }
                Deck dst = deckGroup.get(dstIdx);
                List<String> remaining = new ArrayList<>(toPropagate);
                propagateToSections(dst, remaining);
            }
        }
    }

    /**
     * Collects KonamiIds of non-MISSING, non-dontRemove cards from
     * {@code section} into {@code out}.  Used by Bug-2 and Bug-3 propagation.
     */
    private static void collectValidatedKonamiIds(List<CardElement> section, List<String> out) {
        for (CardElement ce : section) {
            if (ce.getOwnershipStatus() == OwnershipStatus.MISSING) {
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

    /**
     * For each KonamiId still in {@code remaining}, finds the first matching
     * MISSING, non-dontRemove slot across the deck's three sections and marks
     * it {@link OwnershipStatus#OWNED} (free propagation always grants full
     * ownership), then removes that KonamiId from {@code remaining}.
     */
    private static void propagateToSections(Deck dst, List<String> remaining) {
        propagateSectionPass(dst.getMainDeck(), remaining);
        propagateSectionPass(dst.getExtraDeck(), remaining);
        propagateSectionPass(dst.getSideDeck(), remaining);
    }

    private static void propagateSectionPass(List<CardElement> section, List<String> remaining) {
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
            String konamiId = ce.getCard().getKonamiId();
            int idx = remaining.indexOf(konamiId);
            if (idx >= 0) {
                ce.setOwnershipStatus(OwnershipStatus.OWNED);
                remaining.remove(idx);
            }
        }
    }

    /**
     * Bug 3 fix — free cardsList satisfaction from deck validations.
     *
     * <p>For each non-dontRemove entry in {@code cardsList} that is still
     * {@link OwnershipStatus#MISSING}, checks whether a matching card (by
     * KonamiId) was already resolved in any deck of {@code col}.  If so, that
     * deck resolution satisfies the cardsList entry for free (one resolved copy
     * → one cardsList entry, consumed so it cannot satisfy a second entry).
     *
     * <p>dontRemove cards are intentionally skipped: they must be validated
     * independently in each context (Bug 4).
     *
     * @return the updated cardsList (mutated in place; also returned for chaining)
     */
    private static List<CardElement> satisfyCardsListFromDeckValidations(
            List<CardElement> cardsList, ThemeCollection col) {

        List<String> deckValidatedPool = new ArrayList<>();
        for (List<Deck> group : col.getLinkedDecks()) {
            for (Deck deck : group) {
                collectValidatedKonamiIds(deck.getMainDeck(), deckValidatedPool);
                collectValidatedKonamiIds(deck.getExtraDeck(), deckValidatedPool);
                collectValidatedKonamiIds(deck.getSideDeck(), deckValidatedPool);
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
                continue;  // handled separately by dontRemove passes
            }
            int idx = deckValidatedPool.indexOf(colCard.getCard().getKonamiId());
            if (idx >= 0) {
                colCard.setOwnershipStatus(OwnershipStatus.OWNED);
                deckValidatedPool.remove(idx);
            }
        }
        return cardsList;
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
    static boolean ownedCopySatisfiesQuality(
            CardElement wantedSlot, CardElement ownedCopy) {

        CardCondition requiredCondition = wantedSlot.getEffectiveCondition();
        CardCondition ownedCondition = ownedCopy.getEffectiveCondition();
        if (ownedCondition.ordinal() > requiredCondition.ordinal()) {
            return false;
        }

        CardRarity requiredRarity = wantedSlot.getRarity();
        if (requiredRarity != null) {
            if (requiredRarity != ownedCopy.getRarity()) {
                return false;
            }
        }

        return true;
    }

    // =========================================================================
    // Pool indexing
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
     * @param pool            the available owned cards, indexed by artwork and
     *                        KonamiId for O(1) average lookups (mutated in place)
     * @param dontRemoveOnly  {@code true} to process only {@code "+"}-flagged slots
     * @param qualityRequired {@code true} to enforce quality matching
     * @param status          the {@link OwnershipStatus} to assign on a match
     * @return the updated section list
     */
    private static List<CardElement> applySectionPass(
            List<CardElement> section,
            OuicheListPoolIndex pool,
            boolean dontRemoveOnly,
            boolean qualityRequired,
            OwnershipStatus status) {

        List<CardElement> updatedSection = new ArrayList<>(section.size());

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
                String wantedPath = wantedSlot.getCard().getImagePath();
                if (wantedPath != null) {
                    CardElement poolCard = pool.takeByImagePath(wantedPath, wantedSlot, qualityRequired);
                    if (poolCard != null) {
                        wantedSlot.setOwnershipStatus(status);
                        matched = true;
                    }
                }
            }

            // KonamiId sub-pass: for all non-artwork slots (and artwork slots that
            // did not match above).
            if (!matched) {
                String wantedKonamiId = wantedSlot.getCard().getKonamiId();
                CardElement poolCard = pool.takeByKonamiId(wantedKonamiId, wantedSlot, qualityRequired);
                if (poolCard != null) {
                    wantedSlot.setOwnershipStatus(status);
                    matched = true;
                }
            }

            updatedSection.add(wantedSlot);
        }

        return updatedSection;
    }

    // =========================================================================
    // Section-level pass helpers (non-loose collections and standalone decks)
    // =========================================================================

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
    private static OuicheListPoolIndex applyNonDontRemovePassesToDeck(
            Deck deck,
            OuicheListPoolIndex pool,
            boolean qualityRequired,
            OwnershipStatus status) {

        deck.setMainDeck(applySectionPass(deck.getMainDeck(), pool, false, qualityRequired, status));
        deck.setExtraDeck(applySectionPass(deck.getExtraDeck(), pool, false, qualityRequired, status));
        deck.setSideDeck(applySectionPass(deck.getSideDeck(), pool, false, qualityRequired, status));

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
    private static OuicheListPoolIndex applyDontRemovePassesToDeck(
            Deck deck,
            OuicheListPoolIndex pool,
            boolean qualityRequired,
            OwnershipStatus status) {

        deck.setMainDeck(applySectionPass(deck.getMainDeck(), pool, true, qualityRequired, status));
        deck.setExtraDeck(applySectionPass(deck.getExtraDeck(), pool, true, qualityRequired, status));
        deck.setSideDeck(applySectionPass(deck.getSideDeck(), pool, true, qualityRequired, status));

        return pool;
    }

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
     * @param loosePool       the fresh per-collection owned pool, indexed for O(1)
     *                        average lookups (preferred source, mutated in place)
     * @param unusedPool      the depleted global pool, indexed for O(1) average
     *                        lookups (fallback source, mutated in place)
     * @param qualityRequired {@code true} to enforce quality matching
     * @param status          the {@link OwnershipStatus} to assign on a match
     * @return the updated section list
     */
    private static List<CardElement> applyLoosePasses(
            List<CardElement> section,
            OuicheListPoolIndex loosePool,
            OuicheListPoolIndex unusedPool,
            boolean qualityRequired,
            OwnershipStatus status) {

        List<String> artMustContain = List.of("*");
        List<String> artMustNotContain = List.of("+");
        List<String> idMustNotContain = List.of("+", "*");

        // Artwork sub-pass: loosePool first, then unusedPool fallback.
        List<CardElement> afterArtLoose = loosePassReplace(
                section, loosePool, true,
                artMustContain, artMustNotContain, qualityRequired, status);

        List<CardElement> afterArtUnused = loosePassReplace(
                afterArtLoose, unusedPool, true,
                artMustContain, artMustNotContain, qualityRequired, status);

        // KonamiId sub-pass: loosePool first, then unusedPool fallback.
        List<CardElement> afterIdLoose = loosePassReplace(
                afterArtUnused, loosePool, false,
                null, idMustNotContain, qualityRequired, status);

        return loosePassReplace(
                afterIdLoose, unusedPool, false,
                null, idMustNotContain, qualityRequired, status);
    }

    // =========================================================================
    // Loose-collection pass helper
    // =========================================================================

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
     * @param byArtwork       {@code true} to match by imagePath (and require a
     *                        KonamiId on the candidate), {@code false} to match
     *                        by KonamiId
     * @param qualityRequired when {@code true} a pool card is only accepted when
     *                        {@link #ownedCopySatisfiesQuality} returns {@code true}
     * @param status          the {@link OwnershipStatus} to assign to the pool
     *                        instance on a match
     * @return the updated sectionList
     */
    private static List<CardElement> loosePassReplace(
            List<CardElement> sectionList,
            OuicheListPoolIndex pool,
            boolean byArtwork,
            List<String> mustContain,
            List<String> mustNotContain,
            boolean qualityRequired,
            OwnershipStatus status) {

        List<CardElement> result = new ArrayList<>(sectionList.size());

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
            CardElement poolCard;
            if (byArtwork) {
                String imagePath = sectionCard.getCard().getImagePath();
                poolCard = imagePath != null
                        ? pool.takeByImagePath(imagePath, sectionCard, qualityRequired, true)
                        : null;
            } else {
                poolCard = pool.takeByKonamiId(sectionCard.getCard().getKonamiId(), sectionCard, qualityRequired);
            }

            if (poolCard != null) {
                poolCard.setOwnershipStatus(status);
                result.add(poolCard);   // replace the slot with the real pool instance
            } else {
                result.add(sectionCard);    // no match — keep the MISSING slot as-is
            }
        }

        return result;
    }
}