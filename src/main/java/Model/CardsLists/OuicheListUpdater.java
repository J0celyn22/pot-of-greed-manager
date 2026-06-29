package Model.CardsLists;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Incremental-update helpers for {@link OuicheList}.
 *
 * <p>These methods keep an already-generated {@link OuicheList#getDetailedOuicheList()}
 * (and the derived compact maps) close to what a full
 * {@link OuicheList#CreateOuicheList} regeneration would produce, after a single card
 * is added to or removed from the {@link OwnedCardsCollection} (My Collection tab) or
 * from a {@link Deck} / {@link ThemeCollection#getCardsList()} (Decks and Collections
 * tab).
 *
 * <p>All methods are no-ops when called with a {@code null} detailed OuicheList; callers
 * (see {@link OuicheList#onOwnedCardAdded} and siblings) already guard for this.
 */
final class OuicheListUpdater {

    private OuicheListUpdater() {
    }

    // =========================================================================
    // My Collection — card added
    // =========================================================================

    /**
     * Handles a card being added to the {@link OwnedCardsCollection}.
     *
     * <p>Tries to fill the first eligible MISSING slot in the detailed OuicheList,
     * walking the slots in the same order as {@link OuicheList#CreateDetailedOuicheList}:
     * non-loose collections (linked decks then cardsList), standalone decks, then loose
     * collections (linked decks then cardsList). Round 1 requires the slot's quality
     * requirement to be met (marks {@link OwnershipStatus#OWNED}); round 2 accepts any
     * match (marks {@link OwnershipStatus#OWNED_SUBSTANDARD}).
     *
     * <p>If no slot can be filled, {@code addedCard} is appended to
     * {@link OuicheList#getUnusedCards()} (the "Available cards" list).
     *
     * @param addedCard the newly-owned {@link CardElement}
     */
    static void onOwnedCardAdded(CardElement addedCard) {
        if (addedCard.getCard() == null || addedCard.getCard().getKonamiId() == null) {
            addToUnusedCards(addedCard);
            return;
        }

        // Round 1: quality-respecting match → OWNED.
        CardElement filledSlot = findFillableSlot(addedCard, true);
        if (filledSlot != null) {
            filledSlot.setOwnershipStatus(OwnershipStatus.OWNED);
            moveSlotBetweenCompactMaps(filledSlot, OwnershipStatus.MISSING, OwnershipStatus.OWNED);
            return;
        }

        // Round 2: any match (ignores quality) → OWNED_SUBSTANDARD.
        CardElement substandardSlot = findFillableSlot(addedCard, false);
        if (substandardSlot != null) {
            substandardSlot.setOwnershipStatus(OwnershipStatus.OWNED_SUBSTANDARD);
            moveSlotBetweenCompactMaps(substandardSlot, OwnershipStatus.MISSING, OwnershipStatus.OWNED_SUBSTANDARD);
            return;
        }

        // Nothing to fill — the new copy becomes (or stays) available.
        addToUnusedCards(addedCard);
    }

    /**
     * Appends {@code card} to {@link OuicheList#getUnusedCards()} if not {@code null}.
     */
    private static void addToUnusedCards(CardElement card) {
        List<CardElement> unusedCards = OuicheList.getUnusedCards();
        if (unusedCards != null) {
            unusedCards.add(card);
        }
    }

    /**
     * Walks the detailed OuicheList in generation order and returns the first MISSING
     * slot that {@code ownedCopy} can fill.
     *
     * @param ownedCopy       the newly-owned card
     * @param qualityRequired {@code true} to only accept slots whose quality requirement
     *                        {@code ownedCopy} satisfies (round 1); {@code false} to
     *                        accept any matching slot regardless of quality (round 2)
     * @return the matching slot, or {@code null} if none was found
     */
    private static CardElement findFillableSlot(CardElement ownedCopy, boolean qualityRequired) {
        DecksAndCollectionsList detailedOuicheList = OuicheList.getDetailedOuicheList();

        // (1) Non-loose collections: linked decks then cardsList.
        if (detailedOuicheList.getCollections() != null) {
            for (ThemeCollection collection : detailedOuicheList.getCollections()) {
                if (Boolean.TRUE.equals(collection.getConnectToWholeCollection())) {
                    continue;
                }
                CardElement found = findFillableSlotInCollection(collection, ownedCopy, qualityRequired);
                if (found != null) {
                    return found;
                }
            }
        }

        // (2) Standalone decks.
        if (detailedOuicheList.getDecks() != null) {
            for (Deck deck : detailedOuicheList.getDecks()) {
                CardElement found = findFillableSlotInDeck(deck, ownedCopy, qualityRequired);
                if (found != null) {
                    return found;
                }
            }
        }

        // (3) Loose collections: linked decks then cardsList.
        if (detailedOuicheList.getCollections() != null) {
            for (ThemeCollection collection : detailedOuicheList.getCollections()) {
                if (!Boolean.TRUE.equals(collection.getConnectToWholeCollection())) {
                    continue;
                }
                CardElement found = findFillableSlotInCollection(collection, ownedCopy, qualityRequired);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * Searches the linked-deck groups (in order) and then the cardsList of a single
     * collection for the first fillable MISSING slot.
     */
    private static CardElement findFillableSlotInCollection(
            ThemeCollection collection, CardElement ownedCopy, boolean qualityRequired) {

        if (collection.getLinkedDecks() != null) {
            for (List<Deck> deckGroup : collection.getLinkedDecks()) {
                if (deckGroup == null) {
                    continue;
                }
                for (Deck deck : deckGroup) {
                    CardElement found = findFillableSlotInDeck(deck, ownedCopy, qualityRequired);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }

        return findFillableSlotInSection(collection.getCardsList(), ownedCopy, qualityRequired);
    }

    /**
     * Searches the main, extra, then side sections of {@code deck} for the first
     * fillable MISSING slot.
     */
    private static CardElement findFillableSlotInDeck(
            Deck deck, CardElement ownedCopy, boolean qualityRequired) {
        if (deck == null) {
            return null;
        }
        CardElement found = findFillableSlotInSection(deck.getMainDeck(), ownedCopy, qualityRequired);
        if (found != null) {
            return found;
        }
        found = findFillableSlotInSection(deck.getExtraDeck(), ownedCopy, qualityRequired);
        if (found != null) {
            return found;
        }
        return findFillableSlotInSection(deck.getSideDeck(), ownedCopy, qualityRequired);
    }

    /**
     * Searches {@code section} for the first MISSING slot that {@code ownedCopy}
     * can fill, mirroring the artwork-then-KonamiId sub-pass order of
     * {@link OuicheList#CreateDetailedOuicheList}.
     */
    private static CardElement findFillableSlotInSection(
            List<CardElement> section, CardElement ownedCopy, boolean qualityRequired) {

        if (section == null) {
            return null;
        }

        // Artwork sub-pass: only slots that request a specific artwork.
        for (CardElement wantedSlot : section) {
            if (!isEligibleMissingSlot(wantedSlot)) {
                continue;
            }
            if (!wantedSlot.toString().contains("*")) {
                continue;
            }
            String wantedImagePath = wantedSlot.getCard().getImagePath();
            String ownedImagePath = ownedCopy.getCard().getImagePath();
            if (wantedImagePath == null || ownedImagePath == null
                    || !wantedImagePath.equals(ownedImagePath)) {
                continue;
            }
            if (qualityRequired && !OuicheList.ownedCopySatisfiesQuality(wantedSlot, ownedCopy)) {
                continue;
            }
            return wantedSlot;
        }

        // KonamiId sub-pass: every remaining MISSING slot.
        for (CardElement wantedSlot : section) {
            if (!isEligibleMissingSlot(wantedSlot)) {
                continue;
            }
            String wantedKonamiId = wantedSlot.getCard().getKonamiId();
            String ownedKonamiId = ownedCopy.getCard().getKonamiId();
            if (wantedKonamiId == null || !wantedKonamiId.equals(ownedKonamiId)) {
                continue;
            }
            if (qualityRequired && !OuicheList.ownedCopySatisfiesQuality(wantedSlot, ownedCopy)) {
                continue;
            }
            return wantedSlot;
        }

        return null;
    }

    /**
     * Returns {@code true} when {@code slot} is a valid MISSING slot with a
     * resolvable {@link Card} and {@code KonamiId}.
     */
    private static boolean isEligibleMissingSlot(CardElement slot) {
        return slot != null
                && slot.getOwnershipStatus() == OwnershipStatus.MISSING
                && slot.getCard() != null
                && slot.getCard().getKonamiId() != null;
    }

    // =========================================================================
    // My Collection — card removed
    // =========================================================================

    /**
     * Handles a card being removed from the {@link OwnedCardsCollection}.
     *
     * <p>Looks for the last occurrence (in detailed-OuicheList generation order) of a
     * slot owned/substandard by a card matching {@code removedCard}:
     * <ul>
     *   <li>If that slot is the actual instance being removed (loose-collection slots
     *       hold the real pool {@link CardElement}), it is unmarked immediately
     *       ({@link OwnershipStatus#MISSING}).</li>
     *   <li>Otherwise, the deck/collection containing that slot is checked against the
     *       user's {@link OwnedCardsCollection}: if a {@link CardsGroup} named after
     *       that deck/collection still contains a matching card, the resolution is
     *       still valid (skip to the previous occurrence). If not, the slot is
     *       unmarked.</li>
     * </ul>
     * If the removed copy was substandard, the substandard compact map is also
     * updated. Finally, {@code removedCard} is removed from
     * {@link OuicheList#getUnusedCards()} if it was present there (it was never
     * occupying a slot).
     *
     * @param removedCard the {@link CardElement} that was just removed from My Collection
     */
    static void onOwnedCardRemoved(CardElement removedCard) {
        // The removed copy might simply have been sitting in the "Available cards" list
        // (an exact-attribute copy, since unusedCards holds copies built from the owned
        // collection — see OuicheList.CreateDetailedOuicheList).
        List<CardElement> unusedCards = OuicheList.getUnusedCards();
        if (unusedCards != null && removeMatchingCopy(unusedCards, removedCard)) {
            return;
        }

        List<OccupiedSlot> occupiedSlots = collectOccupiedSlots();

        for (int index = occupiedSlots.size() - 1; index >= 0; index--) {
            OccupiedSlot occupied = occupiedSlots.get(index);
            CardElement slot = occupied.slot;

            if (!sameCard(slot, removedCard)) {
                continue;
            }

            if (occupied.isLoose) {
                // Loose-collection slots hold a copy of the owned card with the same
                // condition/rarity as the original — an exact cardKey match identifies
                // this specific occurrence.
                if (sameCardKey(slot, removedCard)) {
                    unmarkSlot(slot);
                    return;
                }
                continue;
            }

            // Non-loose slot: check whether the deck/collection's matching named
            // category in the owned collection still contains a copy of this card.
            if (ownedCategoryStillHasCard(occupied.contextName, removedCard)) {
                // Still covered by another owned copy — leave this slot marked and
                // continue looking at the previous occurrence.
                continue;
            }

            unmarkSlot(slot);
            return;
        }

        // No occupied slot matched: the removed card was never represented in the
        // detailed OuicheList (e.g. not needed by any deck/collection). Nothing to do.
    }

    /**
     * Removes the first element of {@code list} whose {@link OuicheList#cardKey} equals
     * that of {@code target} (same artwork/printCode/condition/rarity).
     *
     * @return {@code true} if an element was removed
     */
    private static boolean removeMatchingCopy(List<CardElement> list, CardElement target) {
        if (target == null || target.getCard() == null) {
            return false;
        }
        String targetKey = OuicheList.cardKey(target);
        if (targetKey == null) {
            return false;
        }
        for (int index = 0; index < list.size(); index++) {
            CardElement candidate = list.get(index);
            if (candidate == null || candidate.getCard() == null) {
                continue;
            }
            if (targetKey.equals(OuicheList.cardKey(candidate))) {
                list.remove(index);
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when {@code a} and {@code b} have the same
     * {@link OuicheList#cardKey} (same artwork/printCode/condition/rarity) — i.e. they
     * represent the same specific physical copy.
     */
    private static boolean sameCardKey(CardElement a, CardElement b) {
        if (a == null || a.getCard() == null || b == null || b.getCard() == null) {
            return false;
        }
        String keyA = OuicheList.cardKey(a);
        String keyB = OuicheList.cardKey(b);
        return keyA != null && keyA.equals(keyB);
    }

    /**
     * Sets {@code slot} back to {@link OwnershipStatus#MISSING} and moves its
     * representation from the OWNED / OWNED_SUBSTANDARD compact map back to the
     * MISSING compact map.
     */
    private static void unmarkSlot(CardElement slot) {
        OwnershipStatus previousStatus = slot.getOwnershipStatus();
        slot.setOwnershipStatus(OwnershipStatus.MISSING);
        moveSlotBetweenCompactMaps(slot, previousStatus, null);
    }

    /**
     * Returns {@code true} when {@code candidate} represents the same physical card as
     * {@code removedCard} (same KonamiId, and same artwork when the candidate slot
     * requests a specific artwork).
     */
    private static boolean sameCard(CardElement candidate, CardElement removedCard) {
        if (candidate == null || candidate.getCard() == null
                || removedCard == null || removedCard.getCard() == null) {
            return false;
        }
        String candidateKonamiId = candidate.getCard().getKonamiId();
        String removedKonamiId = removedCard.getCard().getKonamiId();
        if (candidateKonamiId == null || !candidateKonamiId.equals(removedKonamiId)) {
            return false;
        }
        if (candidate.toString().contains("*")) {
            String candidateImagePath = candidate.getCard().getImagePath();
            String removedImagePath = removedCard.getCard().getImagePath();
            return candidateImagePath != null && candidateImagePath.equals(removedImagePath);
        }
        return true;
    }

    /**
     * Checks whether the user's {@link OwnedCardsCollection} still has a
     * {@link CardsGroup} (category) named {@code contextName} containing a card
     * matching {@code removedCard}.
     *
     * <p>Used after removing a copy of a card to decide whether a deck/collection's
     * OWNED slot for that card is still backed by another owned copy filed under the
     * same-named category (e.g. a category that mirrors the deck/collection name in
     * My Collection).
     *
     * @param contextName the deck or collection name to look for as a category name,
     *                     or {@code null} if unknown (in which case this returns
     *                     {@code false})
     * @param removedCard the card that was just removed (used for matching)
     * @return {@code true} if a matching category with a matching card still exists
     */
    private static boolean ownedCategoryStillHasCard(String contextName, CardElement removedCard) {
        if (contextName == null) {
            return false;
        }
        OwnedCardsCollection ownedCardsCollection = OuicheList.getMyCardsCollection();
        if (ownedCardsCollection == null || ownedCardsCollection.getOwnedCollection() == null) {
            return false;
        }

        for (Box box : ownedCardsCollection.getOwnedCollection()) {
            if (box == null) {
                continue;
            }
            if (categoryListHasMatchingCard(box.getContent(), contextName, removedCard)) {
                return true;
            }
            if (box.getSubBoxes() != null) {
                for (Box subBox : box.getSubBoxes()) {
                    if (subBox == null) {
                        continue;
                    }
                    if (categoryListHasMatchingCard(subBox.getContent(), contextName, removedCard)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean categoryListHasMatchingCard(
            List<CardsGroup> categories, String contextName, CardElement removedCard) {
        if (categories == null) {
            return false;
        }
        for (CardsGroup group : categories) {
            if (group == null || group.getCardList() == null) {
                continue;
            }
            if (group.getName() == null || !group.getName().equalsIgnoreCase(contextName)) {
                continue;
            }
            for (CardElement ownedElement : group.getCardList()) {
                if (sameCard(ownedElement, removedCard)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Collects every OWNED / OWNED_SUBSTANDARD slot in the detailed OuicheList,
     * in the same generation order as {@link OuicheList#CreateDetailedOuicheList}:
     * non-loose collections (linked decks then cardsList), standalone decks, then
     * loose collections (linked decks then cardsList).
     *
     * <p>Each slot is paired with the name of its owning deck (preferred) or
     * collection, used by {@link #ownedCategoryStillHasCard}.
     */
    private static List<OccupiedSlot> collectOccupiedSlots() {
        List<OccupiedSlot> occupiedSlots = new ArrayList<>();
        DecksAndCollectionsList detailedOuicheList = OuicheList.getDetailedOuicheList();

        if (detailedOuicheList.getCollections() != null) {
            for (ThemeCollection collection : detailedOuicheList.getCollections()) {
                if (Boolean.TRUE.equals(collection.getConnectToWholeCollection())) {
                    continue;
                }
                collectOccupiedSlotsInCollection(collection, occupiedSlots, false);
            }
        }

        if (detailedOuicheList.getDecks() != null) {
            for (Deck deck : detailedOuicheList.getDecks()) {
                collectOccupiedSlotsInSection(deck.getMainDeck(), deck.getName(), occupiedSlots, false);
                collectOccupiedSlotsInSection(deck.getExtraDeck(), deck.getName(), occupiedSlots, false);
                collectOccupiedSlotsInSection(deck.getSideDeck(), deck.getName(), occupiedSlots, false);
            }
        }

        if (detailedOuicheList.getCollections() != null) {
            for (ThemeCollection collection : detailedOuicheList.getCollections()) {
                if (!Boolean.TRUE.equals(collection.getConnectToWholeCollection())) {
                    continue;
                }
                collectOccupiedSlotsInCollection(collection, occupiedSlots, true);
            }
        }

        return occupiedSlots;
    }

    /**
     * Handles a wanted-card slot being removed from a {@link Deck} section or from a
     * {@link ThemeCollection#getCardsList()}.
     *
     * <p>The slot is removed from {@code detailedOuicheList} (matched by reference
     * first, falling back to the same-card identity check used elsewhere in this
     * class). Behaviour then depends on the removed slot's ownership status:
     * <ul>
     *   <li>{@link OwnershipStatus#MISSING}: the slot is dropped from the MISSING
     *       compact map.</li>
     *   <li>{@link OwnershipStatus#OWNED} or {@link OwnershipStatus#OWNED_SUBSTANDARD}:
     *       the slot is dropped from the corresponding compact map, and the freed
     *       "ownership" is propagated to the next eligible MISSING slot across the
     *       whole detailed OuicheList that shares the same KonamiId — that slot is
     *       marked {@link OwnershipStatus#OWNED} (a substandard slot's ownership is
     *       always promoted to full OWNED on the receiving slot, mirroring "mark the
     *       next missing one as owned"). If a substandard slot existed for this card,
     *       its substandard marking moves to the next eligible MISSING slot, if any.</li>
     * </ul>
     */
    static void onDeckCardRemoved(CardElement removedCard, String deckName, String section,
                                  String collectionName) {

        List<CardElement> targetSection = resolveTargetSection(deckName, section, collectionName);
        if (targetSection == null) {
            return;
        }

        CardElement removedSlot = removeFromSection(targetSection, removedCard);
        if (removedSlot == null) {
            return;
        }

        OwnershipStatus removedStatus = removedSlot.getOwnershipStatus();

        if (removedStatus == OwnershipStatus.MISSING) {
            removeOneFromCompactMap(removedSlot, OwnershipStatus.MISSING);
            return;
        }

        // The removed slot was OWNED or OWNED_SUBSTANDARD: drop it from its compact
        // map, then propagate the freed ownership to the next eligible MISSING slot
        // sharing the same KonamiId.
        removeOneFromCompactMap(removedSlot, removedStatus);

        if (removedSlot.getCard() == null || removedSlot.getCard().getKonamiId() == null) {
            return;
        }

        CardElement nextMissing = findNextMissingSlotByKonamiId(removedSlot.getCard().getKonamiId());
        if (nextMissing == null) {
            // No other slot in the OuicheList needs this copy right now. Put the
            // physical card back into unusedCards so it can immediately fill any
            // slot added next (e.g. when this call is the "remove" half of a MOVE
            // drag from one deck/collection to another — the subsequent
            // onDeckCardAdded call for the same card will find it here and mark
            // the new slot OWNED rather than MISSING).
            addToUnusedCards(removedCard);
            return;
        }

        // "Mark the next missing one as owned" — a freed substandard slot also
        // promotes the receiving slot to OWNED, since the freed copy is, by
        // definition, the same physical card the receiving slot would otherwise
        // need to find independently.
        nextMissing.setOwnershipStatus(OwnershipStatus.OWNED);
        moveSlotBetweenCompactMaps(nextMissing, OwnershipStatus.MISSING, OwnershipStatus.OWNED);
    }

    private static void collectOccupiedSlotsInCollection(
            ThemeCollection collection, List<OccupiedSlot> occupiedSlots, boolean isLoose) {

        if (collection.getLinkedDecks() != null) {
            for (List<Deck> deckGroup : collection.getLinkedDecks()) {
                if (deckGroup == null) {
                    continue;
                }
                for (Deck deck : deckGroup) {
                    if (deck == null) {
                        continue;
                    }
                    collectOccupiedSlotsInSection(deck.getMainDeck(), deck.getName(), occupiedSlots, isLoose);
                    collectOccupiedSlotsInSection(deck.getExtraDeck(), deck.getName(), occupiedSlots, isLoose);
                    collectOccupiedSlotsInSection(deck.getSideDeck(), deck.getName(), occupiedSlots, isLoose);
                }
            }
        }

        collectOccupiedSlotsInSection(collection.getCardsList(), collection.getName(), occupiedSlots, isLoose);
    }

    private static void collectOccupiedSlotsInSection(
            List<CardElement> section, String contextName, List<OccupiedSlot> occupiedSlots, boolean isLoose) {
        if (section == null) {
            return;
        }
        for (CardElement element : section) {
            if (element == null) {
                continue;
            }
            if (element.getOwnershipStatus() == OwnershipStatus.OWNED
                    || element.getOwnershipStatus() == OwnershipStatus.OWNED_SUBSTANDARD) {
                occupiedSlots.add(new OccupiedSlot(element, contextName, isLoose));
            }
        }
    }

    // =========================================================================
    // Decks and Collections — card added
    // =========================================================================

    /**
     * Handles a new wanted-card slot being added to a {@link Deck} section or to a
     * {@link ThemeCollection#getCardsList()}.
     *
     * <p>{@code addedCard} is inserted directly into the corresponding live list
     * (it is the caller's responsibility to have created it as a fresh, MISSING
     * {@link CardElement}; this method does not duplicate the insertion if it is
     * already present in the target list).
     *
     * <p>After insertion, an attempt is made to satisfy the new slot from
     * {@link OuicheList#getUnusedCards()}: round 1 (quality-respecting, by artwork
     * then KonamiId) marks it {@link OwnershipStatus#OWNED}; round 2 (any quality)
     * marks it {@link OwnershipStatus#OWNED_SUBSTANDARD}. If neither round finds a
     * match, the slot is left {@link OwnershipStatus#MISSING}. The compact maps are
     * updated accordingly.
     */
    static void onDeckCardAdded(CardElement addedCard, String deckName, String section,
                                String collectionName) {

        List<CardElement> targetSection = resolveTargetSection(deckName, section, collectionName);
        if (targetSection == null) {
            return;
        }

        if (!targetSection.contains(addedCard)) {
            targetSection.add(addedCard);
        }

        if (addedCard.getCard() == null || addedCard.getCard().getKonamiId() == null) {
            addNewMissingSlotToCompactMap(addedCard);
            return;
        }

        List<CardElement> unusedCards = OuicheList.getUnusedCards();
        if (unusedCards == null) {
            unusedCards = new ArrayList<>();
            OuicheList.setUnusedCards(unusedCards);
        }

        CardElement ownedMatch = findUnusedMatch(unusedCards, addedCard, true);
        if (ownedMatch != null) {
            unusedCards.remove(ownedMatch);
            addedCard.setOwnershipStatus(OwnershipStatus.OWNED);
            addNewOwnedSlotToCompactMap(addedCard, OwnershipStatus.OWNED);
            return;
        }

        CardElement substandardMatch = findUnusedMatch(unusedCards, addedCard, false);
        if (substandardMatch != null) {
            unusedCards.remove(substandardMatch);
            addedCard.setOwnershipStatus(OwnershipStatus.OWNED_SUBSTANDARD);
            addNewOwnedSlotToCompactMap(addedCard, OwnershipStatus.OWNED_SUBSTANDARD);
            return;
        }

        addedCard.setOwnershipStatus(OwnershipStatus.MISSING);
        addNewMissingSlotToCompactMap(addedCard);
    }

    /**
     * Searches {@code unusedCards} for a card that can fill {@code wantedSlot},
     * trying artwork match first (for specific-artwork slots), then KonamiId.
     *
     * @param qualityRequired {@code true} to require {@link OuicheList#ownedCopySatisfiesQuality}
     * @return the matching pool card, or {@code null} if none found
     */
    private static CardElement findUnusedMatch(
            List<CardElement> unusedCards, CardElement wantedSlot, boolean qualityRequired) {

        if (wantedSlot.getCard() == null || wantedSlot.getCard().getKonamiId() == null) {
            return null;
        }

        if (wantedSlot.toString().contains("*")) {
            String wantedImagePath = wantedSlot.getCard().getImagePath();
            if (wantedImagePath != null) {
                for (CardElement candidate : unusedCards) {
                    if (candidate.getCard() == null
                            || candidate.getCard().getKonamiId() == null
                            || !wantedImagePath.equals(candidate.getCard().getImagePath())) {
                        continue;
                    }
                    if (qualityRequired && !OuicheList.ownedCopySatisfiesQuality(wantedSlot, candidate)) {
                        continue;
                    }
                    return candidate;
                }
            }
        }

        String wantedKonamiId = wantedSlot.getCard().getKonamiId();
        for (CardElement candidate : unusedCards) {
            if (candidate.getCard() == null
                    || candidate.getCard().getKonamiId() == null
                    || !wantedKonamiId.equals(candidate.getCard().getKonamiId())) {
                continue;
            }
            if (qualityRequired && !OuicheList.ownedCopySatisfiesQuality(wantedSlot, candidate)) {
                continue;
            }
            return candidate;
        }

        return null;
    }

    // =========================================================================
    // Decks and Collections — card removed
    // =========================================================================

    /**
     * Pairs an OWNED / OWNED_SUBSTANDARD slot in the detailed OuicheList with the
     * name of the deck or collection it belongs to (used to look up a matching
     * category in the owned collection).
     */
    private static final class OccupiedSlot {
        final CardElement slot;
        final String contextName;
        final boolean isLoose;

        OccupiedSlot(CardElement slot, String contextName, boolean isLoose) {
            this.slot = slot;
            this.contextName = contextName;
            this.isLoose = isLoose;
        }
    }

    /**
     * Removes the slot corresponding to {@code removedCard} from {@code section},
     * matching by {@link #sameCardKey} first (exact artwork/printCode/condition/rarity
     * match — the most specific possible identification of "this slot") and falling
     * back to {@link #sameCard} (KonamiId / artwork only) for the first match. Note that
     * {@code section} comes from the detailed OuicheList, which holds independent copies
     * of the live deck/collection {@link CardElement}s, so reference equality is never
     * meaningful here.
     *
     * @return the removed {@link CardElement}, or {@code null} if no match was found
     */
    private static CardElement removeFromSection(List<CardElement> section, CardElement removedCard) {
        for (int index = 0; index < section.size(); index++) {
            if (sameCardKey(section.get(index), removedCard)) {
                return section.remove(index);
            }
        }
        for (int index = 0; index < section.size(); index++) {
            if (sameCard(section.get(index), removedCard)) {
                return section.remove(index);
            }
        }
        return null;
    }

    /**
     * Searches the whole detailed OuicheList, in generation order, for the first
     * MISSING slot whose card's KonamiId equals {@code konamiId}.
     */
    private static CardElement findNextMissingSlotByKonamiId(String konamiId) {
        DecksAndCollectionsList detailedOuicheList = OuicheList.getDetailedOuicheList();

        if (detailedOuicheList.getCollections() != null) {
            for (ThemeCollection collection : detailedOuicheList.getCollections()) {
                if (Boolean.TRUE.equals(collection.getConnectToWholeCollection())) {
                    continue;
                }
                CardElement found = findMissingByKonamiIdInCollection(collection, konamiId);
                if (found != null) {
                    return found;
                }
            }
        }

        if (detailedOuicheList.getDecks() != null) {
            for (Deck deck : detailedOuicheList.getDecks()) {
                CardElement found = findMissingByKonamiIdInDeck(deck, konamiId);
                if (found != null) {
                    return found;
                }
            }
        }

        if (detailedOuicheList.getCollections() != null) {
            for (ThemeCollection collection : detailedOuicheList.getCollections()) {
                if (!Boolean.TRUE.equals(collection.getConnectToWholeCollection())) {
                    continue;
                }
                CardElement found = findMissingByKonamiIdInCollection(collection, konamiId);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private static CardElement findMissingByKonamiIdInCollection(ThemeCollection collection, String konamiId) {
        if (collection.getLinkedDecks() != null) {
            for (List<Deck> deckGroup : collection.getLinkedDecks()) {
                if (deckGroup == null) {
                    continue;
                }
                for (Deck deck : deckGroup) {
                    CardElement found = findMissingByKonamiIdInDeck(deck, konamiId);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return findMissingByKonamiIdInSection(collection.getCardsList(), konamiId);
    }

    private static CardElement findMissingByKonamiIdInDeck(Deck deck, String konamiId) {
        if (deck == null) {
            return null;
        }
        CardElement found = findMissingByKonamiIdInSection(deck.getMainDeck(), konamiId);
        if (found != null) {
            return found;
        }
        found = findMissingByKonamiIdInSection(deck.getExtraDeck(), konamiId);
        if (found != null) {
            return found;
        }
        return findMissingByKonamiIdInSection(deck.getSideDeck(), konamiId);
    }

    private static CardElement findMissingByKonamiIdInSection(List<CardElement> section, String konamiId) {
        if (section == null) {
            return null;
        }
        for (CardElement element : section) {
            if (!isEligibleMissingSlot(element)) {
                continue;
            }
            if (konamiId.equals(element.getCard().getKonamiId())) {
                return element;
            }
        }
        return null;
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================

    /**
     * Resolves the live list (within the detailed OuicheList) that backs the section
     * named {@code section} ({@code "main"}, {@code "extra"}, or {@code "side"}) of the
     * deck named {@code deckName}, or the {@code cardsList} of the collection named
     * {@code collectionName} when {@code deckName} is {@code null}.
     *
     * <p>When {@code deckName} is non-{@code null}, the deck is searched first among
     * standalone decks ({@link DecksAndCollectionsList#getDecks()}), then among the
     * linked decks of every collection. {@code collectionName}, if also provided,
     * narrows the search to that collection's linked decks.
     *
     * @return the resolved list, or {@code null} when no matching deck/collection/section
     * could be found in the detailed OuicheList
     */
    private static List<CardElement> resolveTargetSection(
            String deckName, String section, String collectionName) {

        DecksAndCollectionsList detailedOuicheList = OuicheList.getDetailedOuicheList();

        if (deckName != null) {
            if (section == null) {
                return null;
            }
            Deck deck = findDeckByName(detailedOuicheList, deckName, collectionName);
            if (deck == null) {
                return null;
            }
            switch (section.toLowerCase()) {
                case "main":
                    return deck.getMainDeck();
                case "extra":
                    return deck.getExtraDeck();
                case "side":
                    return deck.getSideDeck();
                default:
                    return null;
            }
        }

        if (collectionName != null) {
            ThemeCollection collection = findCollectionByName(detailedOuicheList, collectionName);
            if (collection != null) {
                return collection.getCardsList();
            }
        }

        return null;
    }

    /**
     * Finds a {@link Deck} by name within the detailed OuicheList.
     *
     * <p>If {@code collectionName} is non-{@code null}, only that collection's linked decks
     * are searched. Otherwise, standalone decks are searched first, then every collection's
     * linked decks.
     */
    private static Deck findDeckByName(
            DecksAndCollectionsList detailedOuicheList, String deckName, String collectionName) {

        if (collectionName != null) {
            ThemeCollection collection = findCollectionByName(detailedOuicheList, collectionName);
            return collection == null ? null : findDeckByNameInCollection(collection, deckName);
        }

        if (detailedOuicheList.getDecks() != null) {
            for (Deck deck : detailedOuicheList.getDecks()) {
                if (deck != null && deckName.equals(deck.getName())) {
                    return deck;
                }
            }
        }

        if (detailedOuicheList.getCollections() != null) {
            for (ThemeCollection collection : detailedOuicheList.getCollections()) {
                Deck found = findDeckByNameInCollection(collection, deckName);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private static Deck findDeckByNameInCollection(ThemeCollection collection, String deckName) {
        if (collection == null || collection.getLinkedDecks() == null) {
            return null;
        }
        for (List<Deck> deckGroup : collection.getLinkedDecks()) {
            if (deckGroup == null) {
                continue;
            }
            for (Deck deck : deckGroup) {
                if (deck != null && deckName.equals(deck.getName())) {
                    return deck;
                }
            }
        }
        return null;
    }

    /**
     * Finds a {@link ThemeCollection} by name within the detailed OuicheList, searching
     * non-loose collections first, then loose collections.
     */
    private static ThemeCollection findCollectionByName(
            DecksAndCollectionsList detailedOuicheList, String collectionName) {
        if (detailedOuicheList.getCollections() == null) {
            return null;
        }
        for (ThemeCollection collection : detailedOuicheList.getCollections()) {
            if (collection != null && collectionName.equals(collection.getName())) {
                return collection;
            }
        }
        return null;
    }

    /**
     * Moves a single representative + count for {@code slot} from the compact map
     * corresponding to {@code fromStatus} to the compact map corresponding to
     * {@code toStatus}.
     *
     * <p>{@code toStatus == null} means "the slot becomes {@link OwnershipStatus#MISSING}"
     * and re-adds it to the "cards to acquire" map ({@link OuicheList#getMaOuicheList()}).
     * For a slot that is leaving the detailed OuicheList entirely (no replacement
     * status), call {@link #removeOneFromCompactMap} directly instead of this method.
     *
     * <p>Only {@link OwnershipStatus#OWNED_SUBSTANDARD} has its own dedicated compact map
     * ({@link OuicheList#getMaOuicheListSubstandard()}); {@link OwnershipStatus#MISSING}
     * (and the {@code null} shorthand for it) corresponds to
     * {@link OuicheList#getMaOuicheList()}, the "cards to acquire" map.
     * {@link OwnershipStatus#OWNED} has no compact map: a slot becoming OWNED is removed
     * from whichever map it was in and nothing is added.
     */
    private static void moveSlotBetweenCompactMaps(CardElement slot, OwnershipStatus fromStatus, OwnershipStatus toStatus) {
        removeOneFromCompactMap(slot, fromStatus);
        addOneToCompactMap(slot, toStatus);
    }

    /**
     * Decrements (or removes) the entry for {@code slot} in the compact map
     * corresponding to {@code status}.
     *
     * <p>Map correspondence:
     * <ul>
     *   <li>{@code null} or {@link OwnershipStatus#OWNED}: {@link OuicheList#getMaOuicheList()}
     *       (the "cards to acquire" map — {@code OWNED} means the slot is no longer
     *       needed, so it is removed from this map).</li>
     *   <li>{@link OwnershipStatus#OWNED_SUBSTANDARD}: {@link OuicheList#getMaOuicheListSubstandard()}.</li>
     *   <li>{@link OwnershipStatus#MISSING}: same as {@code null}/{@code OWNED} — the
     *       "cards to acquire" map.</li>
     * </ul>
     */
    private static void removeOneFromCompactMap(CardElement slot, OwnershipStatus status) {
        if (status == null || slot.getCard() == null) {
            return;
        }
        String key = OuicheList.cardKey(slot);
        if (key == null) {
            return;
        }
        if (status == OwnershipStatus.OWNED_SUBSTANDARD) {
            decrementOrRemove(OuicheList.getMaOuicheListSubstandard(), OuicheList.getMaOuicheListSubstandardCounts(), key);
        } else {
            // MISSING (and OWNED, defensively — OWNED slots should not be present here).
            decrementOrRemove(OuicheList.getMaOuicheList(), OuicheList.getMaOuicheListCounts(), key);
        }
    }

    /**
     * Increments (or inserts) the entry for {@code slot} in the compact map
     * corresponding to {@code status}, using the same map correspondence as
     * {@link #removeOneFromCompactMap}.
     */
    private static void addOneToCompactMap(CardElement slot, OwnershipStatus status) {
        if (status == OwnershipStatus.OWNED || slot.getCard() == null) {
            return;
        }
        String key = OuicheList.cardKey(slot);
        if (key == null) {
            return;
        }
        if (status == OwnershipStatus.OWNED_SUBSTANDARD) {
            LinkedHashMap<String, CardElement> substandardMap = OuicheList.getMaOuicheListSubstandard();
            LinkedHashMap<String, Integer> substandardCounts = OuicheList.getMaOuicheListSubstandardCounts();
            if (substandardMap == null || substandardCounts == null) {
                return;
            }
            OuicheList.addToMap(substandardMap, substandardCounts, key, 1, slot);
        } else {
            // null or MISSING — back on the "cards to acquire" list.
            LinkedHashMap<String, CardElement> missingMap = OuicheList.getMaOuicheList();
            LinkedHashMap<String, Integer> missingCounts = OuicheList.getMaOuicheListCounts();
            if (missingMap == null || missingCounts == null) {
                return;
            }
            OuicheList.addToMap(missingMap, missingCounts, key, 1, slot);
        }
    }

    private static void decrementOrRemove(
            LinkedHashMap<String, CardElement> map, LinkedHashMap<String, Integer> countsMap, String key) {
        if (map == null || countsMap == null) {
            return;
        }
        Integer currentCount = countsMap.get(key);
        if (currentCount == null) {
            return;
        }
        if (currentCount <= 1) {
            countsMap.remove(key);
            map.remove(key);
        } else {
            countsMap.put(key, currentCount - 1);
        }
    }

    /**
     * Adds a brand-new MISSING slot's representation to the "cards to acquire" compact
     * map (used by {@link #onDeckCardAdded} when the new slot could not be filled).
     */
    private static void addNewMissingSlotToCompactMap(CardElement slot) {
        addOneToCompactMap(slot, OwnershipStatus.MISSING);
    }

    /**
     * Adds a brand-new OWNED / OWNED_SUBSTANDARD slot's representation to the
     * corresponding compact map (used by {@link #onDeckCardAdded} when the new slot
     * was immediately filled from {@link OuicheList#getUnusedCards()}).
     *
     * <p>For {@link OwnershipStatus#OWNED}, this is a no-op: a fully-owned new slot is
     * not "missing" and therefore has no entry to add.
     */
    private static void addNewOwnedSlotToCompactMap(CardElement slot, OwnershipStatus status) {
        addOneToCompactMap(slot, status);
    }
}