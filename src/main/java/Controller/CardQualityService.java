package Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * CardQualityService — pure domain/business-logic helpers for card-quality
 * comparison, upgrade decisions, and the collection/deck traversal utilities
 * shared across this domain.
 *
 * <p>All methods are stateless and operate exclusively on Model objects.
 * This class has no dependency on JavaFX and contains no UI code.</p>
 *
 * <p>Methods were originally extracted from {@link RealMainController} so that
 * {@code RealMainController} is responsible only for UI orchestration.
 * {@link CardSortingRules} was later split out from this class to hold the
 * card-to-navigation-section matching rules (named collection/deck,
 * type-category, default-coverage); it calls back into {@link #cardsMatch},
 * {@link #ELEMENT_MATCHES_CARD}, and the traversal helpers below.</p>
 */
public final class CardQualityService {

    private static final Logger logger = LoggerFactory.getLogger(CardQualityService.class);

    /**
     * Utility class — no instances.
     */
    private CardQualityService() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns whether {@code referenceCard} and {@code elementCard} represent the
     * same physical card for sorting/matching purposes.
     *
     * <p>Match by printCode only when BOTH sides have one; otherwise fall back to
     * passCode. Deck definitions typically store entries by passCode only (no
     * printCode), so requiring a printCode match when the definition entry has
     * none would cause all owned cards that carry a printCode to fail matching
     * entirely.</p>
     *
     * @param referenceCard the card being searched for
     * @param elementCard   the candidate card to compare against
     * @return {@code true} if the two cards should be treated as the same card
     */
    static boolean cardsMatch(Model.CardsLists.Card referenceCard, Model.CardsLists.Card elementCard) {
        if (referenceCard == null || elementCard == null) {
            return false;
        }
        if (referenceCard.getPrintCode() != null && elementCard.getPrintCode() != null) {
            return referenceCard.getPrintCode().equals(elementCard.getPrintCode());
        }
        if (referenceCard.getPassCode() != null && elementCard.getPassCode() != null) {
            return referenceCard.getPassCode().equals(elementCard.getPassCode());
        }
        return false;
    }

    /**
     * Compares a {@link Model.CardsLists.CardElement} against a {@link Model.CardsLists.Card},
     * delegating to {@link #cardsMatch}. Used throughout the card-sorting logic in this class.
     */
    static final BiPredicate<Model.CardsLists.CardElement, Model.CardsLists.Card> ELEMENT_MATCHES_CARD =
            (cardElement, referenceCard) -> cardElement != null
                    && cardsMatch(referenceCard, cardElement.getCard());

    /**
     * Remove the first CardElement in the provided list that matches the given Card
     * (by print/pass/konami). Returns {@code true} if an element was removed.
     * This helper operates on the list instance passed — caller should pass a copy
     * if non-destructive behaviour is required.
     */
    static boolean removeFirstMatchingFromList(
            List<Model.CardsLists.CardElement> list,
            Model.CardsLists.Card card,
            BiPredicate<Model.CardsLists.CardElement, Model.CardsLists.Card> matches) {
        if (list == null || card == null) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            Model.CardsLists.CardElement cardElement = list.get(i);
            if (cardElement == null || cardElement.getCard() == null) {
                continue;
            }
            if (matches.test(cardElement, card)) {
                list.remove(i);
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Net-copies-needed helper
    // -----------------------------------------------------------------------

    /**
     * Returns the number of additional copies of {@code card} that are still needed
     * in the deck or collection identified by {@code elementName}, after accounting
     * for copies already placed in the owned collection under that element's box/group.
     *
     * <p>A positive result means the deck/collection definition lists more copies of
     * this card than are currently owned under its name → the card genuinely fills a
     * blank slot.  Zero or negative means all required copies are already there → any
     * match in {@link #computeCardNeedsSorting} is either a rarity/condition upgrade
     * or a false positive.</p>
     *
     * @return the net number of copies still needed (may be negative if extras exist),
     * or {@code -1} when the element name is not recognised / an error occurs.
     */
    public static int computeNetCopiesNeeded(Model.CardsLists.Card card, String elementName) {
        if (card == null || elementName == null || elementName.trim().isEmpty()) {
            return -1;
        }
        try {
            Model.CardsLists.DecksAndCollectionsList decksList = UserInterfaceFunctions.getDecksList();
            if (decksList == null) {
                return -1;
            }
            Model.CardsLists.OwnedCardsCollection owned = Model.CardsLists.OuicheList.getMyCardsCollection();

            Function<String, String> normalizer = s -> s == null ? "" :
                    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);

            String normalizedElement = normalizer.apply(elementName);

            // Card-matching predicate, delegating to the shared cardsMatch comparison
            // used throughout this class (printCode when both sides have one, else passCode).
            Predicate<Model.CardsLists.CardElement> matchesCard = candidateElement ->
                    candidateElement != null && cardsMatch(card, candidateElement.getCard());

            Function<java.util.List<Model.CardsLists.CardElement>, Integer> countMatchingIn =
                    list -> {
                        if (list == null) {
                            return 0;
                        }
                        int count = 0;
                        for (Model.CardsLists.CardElement element : list) {
                            if (matchesCard.test(element)) {
                                count++;
                            }
                        }
                        return count;
                    };

            // Count how many copies the owned collection has under boxes/groups named like this element
            int ownedCount = 0;
            if (owned != null) {
                for (Model.CardsLists.Box box : owned.getOwnedCollection()) {
                    if (box == null) {
                        continue;
                    }
                    boolean boxMatches = normalizer.apply(box.getName()).equals(normalizedElement);
                    if (box.getContent() != null) {
                        for (Model.CardsLists.CardsGroup cardsGroup : box.getContent()) {
                            if (cardsGroup == null) {
                                continue;
                            }
                            boolean groupMatches = normalizer.apply(
                                            Model.CardsLists.OwnedCardsCollection.extractName(
                                                    cardsGroup.getName() == null ? "" : cardsGroup.getName(), '-'))
                                    .equals(normalizedElement);
                            if (boxMatches || groupMatches) {
                                ownedCount += countMatchingIn.apply(cardsGroup.getCardList());
                            }
                        }
                    }
                }
            }

            // Count how many copies the definition requires
            int definitionCount = 0;
            if (decksList.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection themeCollection : decksList.getCollections()) {
                    if (themeCollection == null) {
                        continue;
                    }
                    if (normalizer.apply(themeCollection.getName()).equals(normalizedElement)) {
                        definitionCount += countMatchingIn.apply(themeCollection.getCardsList());
                        if (themeCollection.getLinkedDecks() != null) {
                            for (java.util.List<Model.CardsLists.Deck> unit : themeCollection.getLinkedDecks()) {
                                if (unit == null) {
                                    continue;
                                }
                                for (Model.CardsLists.Deck deck : unit) {
                                    if (deck == null) {
                                        continue;
                                    }
                                    definitionCount += countMatchingIn.apply(deck.getMainDeck());
                                    definitionCount += countMatchingIn.apply(deck.getExtraDeck());
                                    definitionCount += countMatchingIn.apply(deck.getSideDeck());
                                }
                            }
                        }
                    }
                }
            }
            if (decksList.getDecks() != null) {
                for (Model.CardsLists.Deck deck : decksList.getDecks()) {
                    if (deck == null) {
                        continue;
                    }
                    if (normalizer.apply(deck.getName()).equals(normalizedElement)) {
                        definitionCount += countMatchingIn.apply(deck.getMainDeck());
                        definitionCount += countMatchingIn.apply(deck.getExtraDeck());
                        definitionCount += countMatchingIn.apply(deck.getSideDeck());
                    }
                }
            }

            if (definitionCount == 0) {
                return -1; // element not recognised as named deck/collection
            }
            return definitionCount - ownedCount;

        } catch (Exception ex) {
            logger.debug("computeNetCopiesNeeded failed for element '{}': {}", elementName, ex.toString());
            return -1;
        }
    }

    // -----------------------------------------------------------------------
    // Quality-upgrade helpers
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} when {@code candidate} has a strictly better physical
     * condition than {@code existing}.
     *
     * <p>"Better" means a lower {@link Model.CardsLists.CardCondition} ordinal
     * (MINT=0 … DAMAGED=6). A non-null condition is always considered better than
     * {@code null} (unknown).</p>
     */
    public static boolean isBetterCondition(
            Model.CardsLists.CardCondition candidate,
            Model.CardsLists.CardCondition existing) {
        if (candidate == null) {
            return false; // no condition on candidate → can't claim upgrade
        }
        if (existing == null) {
            return false; // existing has no condition recorded — don't compare
        }
        return candidate.ordinal() < existing.ordinal(); // lower ordinal = better condition
    }

    /**
     * Returns {@code true} when {@code candidate} satisfies the expected rarity
     * of {@code target} but {@code existing} does not.
     *
     * <p>The "expected rarity" is the rarity stored on the {@code target}
     * {@link Model.CardsLists.CardElement} (i.e. the slot in the deck / collection).
     * The check is:
     * <ul>
     *   <li>If {@code target} has no expected rarity → not applicable, return
     *       {@code false}.</li>
     *   <li>If {@code existing} already matches the expected rarity → not an
     *       upgrade, return {@code false}.</li>
     *   <li>If {@code candidate} matches the expected rarity (and existing
     *       doesn't) → return {@code true}.</li>
     * </ul>
     * </p>
     */
    public static boolean satisfiesExpectedRarityBetter(
            Model.CardsLists.CardRarity candidateRarity,
            Model.CardsLists.CardRarity existingRarity,
            Model.CardsLists.CardRarity expectedRarity) {
        if (expectedRarity == null) {
            return false; // no expectation → N/A
        }
        if (existingRarity != null && existingRarity == expectedRarity) {
            return false; // already satisfied
        }
        return candidateRarity != null && candidateRarity == expectedRarity;
    }

    /**
     * Given a list of {@link Model.CardsLists.CardElement}s that are already placed
     * in a deck / collection slot and an incoming candidate
     * {@link Model.CardsLists.CardElement}, returns {@code true} when the candidate
     * is a quality upgrade over at least one of the existing copies: either a better
     * physical condition or it satisfies the expected rarity of a slot that the
     * existing copy does not.
     *
     * @param existingInTarget   the copies of this card already present in the target
     *                           deck / collection list
     * @param targetSlotElements the full slot list from the deck/collection file
     *                           (used to find the "expected" rarity for each slot)
     * @param candidate          the owned CardElement being evaluated
     */
    public static boolean isQualityUpgrade(
            java.util.List<Model.CardsLists.CardElement> existingInTarget,
            java.util.List<Model.CardsLists.CardElement> targetSlotElements,
            Model.CardsLists.CardElement candidate) {
        if (candidate == null) {
            return false;
        }
        if (existingInTarget == null || existingInTarget.isEmpty()) {
            return false;
        }

        Model.CardsLists.CardCondition candidateCondition = candidate.getCondition();
        Model.CardsLists.CardRarity candidateRarity = candidate.getRarity();

        for (Model.CardsLists.CardElement existing : existingInTarget) {
            if (existing == null) {
                continue;
            }

            // 1) Better physical condition?
            // Only compare when the existing copy has a recorded condition — if it has none,
            // it is either a definition-only slot or an untracked owned copy; in neither case
            // do we treat a candidate's condition as an upgrade (we can't know it's "better").
            if (existing.getCondition() != null
                    && isBetterCondition(candidateCondition, existing.getCondition())) {
                return true;
            }

            // 2) Satisfies expected rarity that the existing copy does not?
            //    The "expected rarity" is what the slot in targetSlotElements specifies.
            if (targetSlotElements != null) {
                for (Model.CardsLists.CardElement slot : targetSlotElements) {
                    if (slot == null) {
                        continue;
                    }
                    if (slot.getCard() == null || existing.getCard() == null) {
                        continue;
                    }
                    Model.CardsLists.Card slotCard = slot.getCard();
                    Model.CardsLists.Card existingCard = existing.getCard();
                    boolean sameCard = (slotCard.getPassCode() != null && slotCard.getPassCode().equals(existingCard.getPassCode()))
                            || (slotCard.getPrintCode() != null && slotCard.getPrintCode().equals(existingCard.getPrintCode()))
                            || (slotCard.getKonamiId() != null && slotCard.getKonamiId().equals(existingCard.getKonamiId()));
                    if (!sameCard) {
                        continue;
                    }
                    if (satisfiesExpectedRarityBetter(candidateRarity, existing.getRarity(), slot.getRarity())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Inverse-side helpers: detecting degraded cards inside decks/collections
    // -----------------------------------------------------------------------

    /**
     * Returns {@code true} when {@code candidate} is a quality upgrade over
     * {@code placedCopy} — a copy already physically placed in a deck or collection.
     *
     * <p>Unlike {@link #isQualityUpgrade} (which is used for definition-slot
     * comparisons), this method treats a null condition on the placed copy as
     * "unrecorded" — i.e. any candidate with a known condition is considered an
     * improvement.</p>
     */
    static boolean isUpgradeOverPlacedCopy(
            Model.CardsLists.CardElement candidate,
            Model.CardsLists.CardElement placedCopy,
            java.util.List<Model.CardsLists.CardElement> slotList) {
        if (candidate == null || placedCopy == null) {
            return false;
        }

        // Condition check: if placed copy has no condition, any known candidate condition is better.
        Model.CardsLists.CardCondition candidateCond = candidate.getCondition();
        Model.CardsLists.CardCondition placedCond = placedCopy.getCondition();
        if (candidateCond != null) {
            if (placedCond == null) {
                return true; // placed copy untracked → candidate is better
            }
            if (candidateCond.ordinal() < placedCond.ordinal()) {
                return true; // strictly better condition
            }
        }

        // Rarity check: does the candidate satisfy an expected rarity that the placed copy doesn't?
        if (slotList != null) {
            for (Model.CardsLists.CardElement slot : slotList) {
                if (slot == null || slot.getCard() == null || placedCopy.getCard() == null) {
                    continue;
                }
                Model.CardsLists.Card slotCard = slot.getCard();
                Model.CardsLists.Card placedCard = placedCopy.getCard();
                boolean sameCard = (slotCard.getPassCode() != null && slotCard.getPassCode().equals(placedCard.getPassCode()))
                        || (slotCard.getPrintCode() != null && slotCard.getPrintCode().equals(placedCard.getPrintCode()))
                        || (slotCard.getKonamiId() != null && slotCard.getKonamiId().equals(placedCard.getKonamiId()));
                if (!sameCard) {
                    continue;
                }
                if (satisfiesExpectedRarityBetter(candidate.getRarity(), placedCopy.getRarity(), slot.getRarity())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Given a {@link Model.CardsLists.CardElement} that is already placed inside a
     * deck or collection slot, returns {@code true} when the user's owned collection
     * contains at least one copy of the same card — <em>outside</em> the same sorting
     * category — that is a quality upgrade over this one: i.e. better physical
     * condition, or the expected rarity that this copy lacks.
     *
     * <p>Copies that are themselves physically stored in a sorting category named after
     * the same deck or collection are excluded from consideration.  Swapping two copies
     * that are both already inside the same D&amp;C category is pointless and must not
     * trigger this warning.</p>
     *
     * @param deckElement          the element currently sitting in the deck/collection
     *                             sorting category
     * @param deckOrCollectionName the display name of that deck or collection (used to
     *                             locate the slot list and to exclude same-category copies)
     */
    public static boolean isDegradedCopyInDeckOrCollection(
            Model.CardsLists.CardElement deckElement,
            String deckOrCollectionName) {
        if (deckElement == null || deckOrCollectionName == null) {
            return false;
        }
        Model.CardsLists.Card card = deckElement.getCard();
        if (card == null) {
            return false;
        }
        try {
            Model.CardsLists.OwnedCardsCollection owned =
                    Model.CardsLists.OuicheList.getMyCardsCollection();
            if (owned == null) {
                return false;
            }

            List<Model.CardsLists.CardElement> ownedCopies = collectOwnedCopies(owned, card);
            if (ownedCopies.isEmpty()) {
                return false;
            }

            List<Model.CardsLists.CardElement> slotList =
                    findSlotListForCard(deckOrCollectionName, card);

            // Build an identity set of every copy that is already sitting in the same
            // D&C sorting category.  Those copies are excluded as upgrade candidates:
            // swapping two copies within the same category accomplishes nothing.
            Function<String, String> normalizer = s -> s == null ? "" :
                    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);
            List<Model.CardsLists.CardElement> sameCategoryPlaced =
                    collectPlacedCopies(owned, deckOrCollectionName, card, normalizer);
            java.util.IdentityHashMap<Model.CardsLists.CardElement, Boolean> sameCategorySet =
                    new java.util.IdentityHashMap<>();
            for (Model.CardsLists.CardElement placed : sameCategoryPlaced) {
                sameCategorySet.put(placed, Boolean.TRUE);
            }

            for (Model.CardsLists.CardElement ownedCopy : ownedCopies) {
                if (sameCategorySet.containsKey(ownedCopy)) {
                    // This copy lives in the same sorting category — skip it.
                    continue;
                }
                if (isUpgradeOverPlacedCopy(ownedCopy, deckElement, slotList)) {
                    return true;
                }
            }
        } catch (Exception ex) {
            logger.debug("isDegradedCopyInDeckOrCollection failed: {}", ex.toString());
        }
        return false;
    }

    /**
     * Returns all owned {@link Model.CardsLists.CardElement}s that are quality
     * upgrades over {@code deckElement} (the copy currently in a deck/collection
     * sorting category) and that are <em>not</em> themselves stored in that same
     * sorting category.
     *
     * <p>Copies already sitting in the same D&amp;C-named sorting category are
     * excluded: swapping two copies within the same category accomplishes nothing,
     * and including them would cause the lesser-quality copy to be spuriously marked
     * with the downgrade warning.</p>
     *
     * @param deckElement          the element currently sitting in the deck/collection
     *                             sorting category
     * @param deckOrCollectionName the display name of that deck or collection (used to
     *                             locate the slot list and to exclude same-category copies)
     * @return a (possibly empty) list of owned elements outside the sorting category
     *         that would improve this slot
     */
    public static List<Model.CardsLists.CardElement> findOwnedUpgradeCandidates(
            Model.CardsLists.CardElement deckElement,
            String deckOrCollectionName) {
        List<Model.CardsLists.CardElement> result = new ArrayList<>();
        if (deckElement == null || deckOrCollectionName == null) {
            return result;
        }
        Model.CardsLists.Card card = deckElement.getCard();
        if (card == null) {
            return result;
        }
        try {
            Model.CardsLists.OwnedCardsCollection owned =
                    Model.CardsLists.OuicheList.getMyCardsCollection();
            if (owned == null) {
                return result;
            }

            List<Model.CardsLists.CardElement> ownedCopies =
                    collectOwnedCopies(owned, card);
            if (ownedCopies.isEmpty()) {
                return result;
            }

            List<Model.CardsLists.CardElement> slotList =
                    findSlotListForCard(deckOrCollectionName, card);

            // Build an identity set of every copy already in the same sorting category
            // so we can exclude them — same logic as isDegradedCopyInDeckOrCollection.
            Function<String, String> normalizer = s -> s == null ? "" :
                    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);
            List<Model.CardsLists.CardElement> sameCategoryPlaced =
                    collectPlacedCopies(owned, deckOrCollectionName, card, normalizer);
            java.util.IdentityHashMap<Model.CardsLists.CardElement, Boolean> sameCategorySet =
                    new java.util.IdentityHashMap<>();
            for (Model.CardsLists.CardElement placed : sameCategoryPlaced) {
                sameCategorySet.put(placed, Boolean.TRUE);
            }

            for (Model.CardsLists.CardElement ownedCopy : ownedCopies) {
                if (sameCategorySet.containsKey(ownedCopy)) {
                    // Already in the same sorting category — skip.
                    continue;
                }
                if (isUpgradeOverPlacedCopy(ownedCopy, deckElement, slotList)) {
                    result.add(ownedCopy);
                }
            }
        } catch (Exception ex) {
            logger.debug("findOwnedUpgradeCandidates failed: {}", ex.toString());
        }
        return result;
    }

    /**
     * Returns {@code true} when {@code normalizedName} matches the display name of
     * any {@link Model.CardsLists.ThemeCollection}, any linked
     * {@link Model.CardsLists.Deck}, or any loose {@link Model.CardsLists.Deck} in
     * {@code decksList}.
     *
     * <p>This is the canonical test used to distinguish a "sorting category" group
     * (a group named after a D&amp;C) from a generic type group ("Fusion Monsters",
     * "Unsorted", …).  It is called by {@link #computeCardNeedsSortingWithUpgrade}
     * as a guard and is also exposed so that view code can use it without duplicating
     * the iteration logic.</p>
     *
     * @param elementName the raw (un-normalised) group name to test
     * @return {@code true} if {@code elementName} is a known deck or collection name
     */
    public static boolean isDeckOrCollectionName(String elementName) {
        try {
            Model.CardsLists.DecksAndCollectionsList decksList =
                    UserInterfaceFunctions.getDecksList();
            if (decksList == null) {
                return false;
            }
            Function<String, String> normalizer = s -> s == null ? "" :
                    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);
            return isDeckOrCollectionName(normalizer.apply(elementName), decksList, normalizer);
        } catch (Exception ex) {
            logger.debug("isDeckOrCollectionName failed for '{}': {}", elementName, ex.toString());
            return false;
        }
    }

    /**
     * Internal overload that accepts an already-normalised name and a shared
     * {@code decksList} / {@code normalizer} to avoid redundant loading.
     */
    static boolean isDeckOrCollectionName(
            String normalizedName,
            Model.CardsLists.DecksAndCollectionsList decksList,
            Function<String, String> normalizer) {
        if (decksList == null || normalizedName == null || normalizedName.isEmpty()) {
            return false;
        }
        if (decksList.getCollections() != null) {
            for (Model.CardsLists.ThemeCollection themeCollection : decksList.getCollections()) {
                if (themeCollection == null) {
                    continue;
                }
                if (normalizer.apply(themeCollection.getName()).equals(normalizedName)) {
                    return true;
                }
                if (themeCollection.getLinkedDecks() != null) {
                    for (List<Model.CardsLists.Deck> unit : themeCollection.getLinkedDecks()) {
                        if (unit == null) {
                            continue;
                        }
                        for (Model.CardsLists.Deck deck : unit) {
                            if (deck != null
                                    && normalizer.apply(deck.getName()).equals(normalizedName)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        if (decksList.getDecks() != null) {
            for (Model.CardsLists.Deck deck : decksList.getDecks()) {
                if (deck != null && normalizer.apply(deck.getName()).equals(normalizedName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Collects owned {@link Model.CardsLists.CardElement}s that match {@code card}
     * and are physically placed in a sorting category whose name matches
     * {@code dcName} (i.e. a {@link Model.CardsLists.CardsGroup} whose raw name,
     * after stripping leading/trailing {@code '-'} decorators and normalising, equals
     * the normalised form of {@code dcName}).
     *
     * <p>This is used by {@link #computeCardNeedsSortingWithUpgrade} to find the
     * copies actually sitting in the D&amp;C sorting category, as opposed to the
     * definition-slot entries from the deck/collection file which carry no physical
     * condition.</p>
     */
    static List<Model.CardsLists.CardElement> collectPlacedCopies(
            Model.CardsLists.OwnedCardsCollection owned,
            String dcName,
            Model.CardsLists.Card card,
            Function<String, String> normalizer) {
        List<Model.CardsLists.CardElement> result = new ArrayList<>();
        if (owned == null || dcName == null || card == null) {
            return result;
        }
        String normalizedDcName = normalizer.apply(dcName);
        for (Model.CardsLists.Box box : owned.getOwnedCollection()) {
            if (box == null) {
                continue;
            }
            for (Model.CardsLists.CardsGroup cardsGroup : box.getContent()) {
                if (cardsGroup == null) {
                    continue;
                }
                String rawName = cardsGroup.getName() == null ? "" : cardsGroup.getName();
                String normalizedGroupName = normalizer.apply(
                        Model.CardsLists.OwnedCardsCollection.extractName(rawName, '-'));
                if (!normalizedGroupName.equals(normalizedDcName)) {
                    continue;
                }
                for (Model.CardsLists.CardElement cardElement : cardsGroup.getCardList()) {
                    if (cardElement == null || cardElement.getCard() == null) {
                        continue;
                    }
                    Model.CardsLists.Card ownedCard = cardElement.getCard();
                    boolean same = (card.getPassCode() != null
                            && card.getPassCode().equals(ownedCard.getPassCode()))
                            || (card.getPrintCode() != null
                            && card.getPrintCode().equals(ownedCard.getPrintCode()))
                            || (card.getKonamiId() != null
                            && card.getKonamiId().equals(ownedCard.getKonamiId()));
                    if (same) {
                        result.add(cardElement);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Returns all elements in {@code list} whose card matches {@code card} by
     * passCode, printCode, or konamiId.
     *
     * <p>This is a static helper equivalent of the {@code collectMatchingElements}
     * lambda used inside earlier versions of
     * {@link #computeCardNeedsSortingWithUpgrade}, extracted so it can be shared
     * with the new implementation without re-declaring the lambda in every loop.</p>
     */
    static List<Model.CardsLists.CardElement> collectMatchingElementsInList(
            List<Model.CardsLists.CardElement> list,
            Model.CardsLists.Card card) {
        List<Model.CardsLists.CardElement> result = new ArrayList<>();
        if (list == null || card == null) {
            return result;
        }
        for (Model.CardsLists.CardElement cardElement : list) {
            if (cardElement == null || cardElement.getCard() == null) {
                continue;
            }
            Model.CardsLists.Card candidateCard = cardElement.getCard();
            boolean same = (card.getPassCode() != null
                    && card.getPassCode().equals(candidateCard.getPassCode()))
                    || (card.getPrintCode() != null
                    && card.getPrintCode().equals(candidateCard.getPrintCode()))
                    || (card.getKonamiId() != null
                    && card.getKonamiId().equals(candidateCard.getKonamiId()));
            if (same) {
                result.add(cardElement);
            }
        }
        return result;
    }

    /**
     * Collects all {@link Model.CardsLists.CardElement}s in the owned collection that
     * refer to the same card as {@code card} (matched by passCode, printCode, or
     * konamiId).
     */
    public static List<Model.CardsLists.CardElement> collectOwnedCopies(
            Model.CardsLists.OwnedCardsCollection owned,
            Model.CardsLists.Card card) {
        List<Model.CardsLists.CardElement> result = new ArrayList<>();
        if (owned == null || card == null) {
            return result;
        }
        for (Model.CardsLists.Box box : owned.getOwnedCollection()) {
            if (box == null) {
                continue;
            }
            for (Model.CardsLists.CardsGroup cardsGroup : box.getContent()) {
                if (cardsGroup == null) {
                    continue;
                }
                for (Model.CardsLists.CardElement cardElement : cardsGroup.getCardList()) {
                    if (cardElement == null || cardElement.getCard() == null) {
                        continue;
                    }
                    Model.CardsLists.Card ownedCard = cardElement.getCard();
                    boolean same = (card.getPassCode() != null && card.getPassCode().equals(ownedCard.getPassCode()))
                            || (card.getPrintCode() != null && card.getPrintCode().equals(ownedCard.getPrintCode()))
                            || (card.getKonamiId() != null && card.getKonamiId().equals(ownedCard.getKonamiId()));
                    if (same) {
                        result.add(cardElement);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Finds the deck/collection slot list (main, extra, or side deck) that contains
     * a card matching {@code card}, searching by {@code deckOrCollectionName}.
     * Returns an empty list when nothing matches.
     */
    static List<Model.CardsLists.CardElement> findSlotListForCard(
            String deckOrCollectionName,
            Model.CardsLists.Card card) {
        try {
            Model.CardsLists.DecksAndCollectionsList decksList =
                    UserInterfaceFunctions.getDecksList();
            if (decksList == null) {
                return java.util.Collections.emptyList();
            }

            String normalizedName = java.text.Normalizer
                    .normalize(deckOrCollectionName, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);

            Function<String, String> normalizer = s -> s == null ? "" :
                    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);

            BiPredicate<Model.CardsLists.Card, Model.CardsLists.Card> sameCard =
                    (cardA, cardB) -> cardA != null && cardB != null &&
                            ((cardA.getPassCode() != null && cardA.getPassCode().equals(cardB.getPassCode()))
                                    || (cardA.getPrintCode() != null && cardA.getPrintCode().equals(cardB.getPrintCode()))
                                    || (cardA.getKonamiId() != null && cardA.getKonamiId().equals(cardB.getKonamiId())));

            // Search collections
            if (decksList.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection themeCollection : decksList.getCollections()) {
                    if (themeCollection == null) {
                        continue;
                    }
                    if (normalizer.apply(themeCollection.getName()).equals(normalizedName)) {
                        List<Model.CardsLists.CardElement> collectionList = themeCollection.getCardsList();
                        if (collectionList != null) {
                            for (Model.CardsLists.CardElement cardElement : collectionList) {
                                if (cardElement != null && sameCard.test(cardElement.getCard(), card)) {
                                    return collectionList;
                                }
                            }
                        }
                    }
                    // Also search linked decks
                    if (themeCollection.getLinkedDecks() != null) {
                        for (List<Model.CardsLists.Deck> unit : themeCollection.getLinkedDecks()) {
                            if (unit == null) {
                                continue;
                            }
                            for (Model.CardsLists.Deck deck : unit) {
                                if (deck == null) {
                                    continue;
                                }
                                String deckName = normalizer.apply(deck.getName());
                                if (deckName.equals(normalizedName)
                                        || normalizer.apply(themeCollection.getName()).equals(normalizedName)) {
                                    for (List<Model.CardsLists.CardElement> deckSection :
                                            java.util.Arrays.asList(
                                                    deck.getMainDeck(),
                                                    deck.getExtraDeck(),
                                                    deck.getSideDeck())) {
                                        if (deckSection == null) {
                                            continue;
                                        }
                                        for (Model.CardsLists.CardElement cardElement : deckSection) {
                                            if (cardElement != null && sameCard.test(cardElement.getCard(), card)) {
                                                return deckSection;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Search loose decks
            if (decksList.getDecks() != null) {
                for (Model.CardsLists.Deck deck : decksList.getDecks()) {
                    if (deck == null || !normalizer.apply(deck.getName()).equals(normalizedName)) {
                        continue;
                    }
                    for (List<Model.CardsLists.CardElement> deckSection :
                            java.util.Arrays.asList(
                                    deck.getMainDeck(),
                                    deck.getExtraDeck(),
                                    deck.getSideDeck())) {
                        if (deckSection == null) {
                            continue;
                        }
                        for (Model.CardsLists.CardElement cardElement : deckSection) {
                            if (cardElement != null && sameCard.test(cardElement.getCard(), card)) {
                                return deckSection;
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.debug("findSlotListForCard failed: {}", ex.toString());
        }
        return java.util.Collections.emptyList();
    }
}