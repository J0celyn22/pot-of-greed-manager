package Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * CardQualityService — pure domain/business-logic helpers for card sorting and
 * quality-upgrade decisions.
 *
 * <p>All methods are stateless and operate exclusively on Model objects.
 * This class has no dependency on JavaFX and contains no UI code.</p>
 *
 * <p>Methods were extracted from {@link RealMainController} so that
 * {@code RealMainController} is responsible only for UI orchestration.</p>
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
    private static final BiPredicate<Model.CardsLists.CardElement, Model.CardsLists.Card> ELEMENT_MATCHES_CARD =
            (cardElement, referenceCard) -> cardElement != null
                    && cardsMatch(referenceCard, cardElement.getCard());

    /**
     * Builds the normalized-type-name → predicate map used by {@link #computeCardNeedsSorting}
     * to recognise category names (monster subtypes, spell/trap subtypes, both French and
     * English) and check whether a given card belongs to that category.
     *
     * @param normalizer the same name-normalization function used by the caller, so map keys
     *                    and lookup keys are normalized identically
     * @return the populated type-name → predicate map
     */
    private static Map<String, Predicate<Model.CardsLists.Card>> buildTypePredicates(
            Function<String, String> normalizer) {
        Map<String, Predicate<Model.CardsLists.Card>> typePredicates = new HashMap<>();

        // Monster subtype predicates (check cardProperties contains the subtype string as used in SubListCreator)
        typePredicates.put(normalizer.apply("Pyro"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Pyro"));
        typePredicates.put(normalizer.apply("Aqua"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Aqua"));
        typePredicates.put(normalizer.apply("Machine"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Machine"));
        typePredicates.put(normalizer.apply("Dragon"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Dragon"));
        typePredicates.put(normalizer.apply("Bete Guerrier"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Beast-Warrior"));
        typePredicates.put(normalizer.apply("Reptile"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Reptile"));
        typePredicates.put(normalizer.apply("Plante"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Plant"));
        typePredicates.put(normalizer.apply("Demon"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Fiend"));
        typePredicates.put(normalizer.apply("Wyrm"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Wyrm"));
        typePredicates.put(normalizer.apply("Dinosaure"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Dinosaur"));
        typePredicates.put(normalizer.apply("Magicien"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Spellcaster"));
        typePredicates.put(normalizer.apply("Poisson"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Fish"));
        typePredicates.put(normalizer.apply("Bete Divine"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Divine-Beast"));
        typePredicates.put(normalizer.apply("Cyberse"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Cyberse"));
        typePredicates.put(normalizer.apply("Insecte"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Insect"));
        typePredicates.put(normalizer.apply("Bete Ailee"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Winged Beast"));
        typePredicates.put(normalizer.apply("Guerrier"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Warrior"));
        typePredicates.put(normalizer.apply("Rocher"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Rock"));
        typePredicates.put(normalizer.apply("Tonnerre"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Thunder"));
        typePredicates.put(normalizer.apply("Zombie"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Zombie"));
        typePredicates.put(normalizer.apply("Serpent de Mer"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Sea Serpent"));
        typePredicates.put(normalizer.apply("Bete"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Beast"));
        typePredicates.put(normalizer.apply("Psychique"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Psychic"));
        typePredicates.put(normalizer.apply("Elfe"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Fairy"));
        typePredicates.put(normalizer.apply("Illusion"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Illusion"));

        // Normal / subtype monster lists (properties like "Normal", "Toon", "Tuner", etc.)
        typePredicates.put(normalizer.apply("Monstres normaux"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Normal"));
        typePredicates.put(normalizer.apply("Monstres Toon"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Toon"));
        typePredicates.put(normalizer.apply("Syntoniseurs"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Tuner"));
        typePredicates.put(normalizer.apply("Monstres Union"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Union"));
        typePredicates.put(normalizer.apply("Monstres Synchro"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Synchro"));
        typePredicates.put(normalizer.apply("Monstres Pendule"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Pendulum"));
        typePredicates.put(normalizer.apply("Monstres Rituels"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Ritual"));
        typePredicates.put(normalizer.apply("Monstres Flip"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Flip"));
        typePredicates.put(normalizer.apply("Monstres Spirit"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Spirit"));
        typePredicates.put(normalizer.apply("Monstres XYZ"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Xyz"));
        typePredicates.put(normalizer.apply("Monstres a Effet"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Effect"));
        typePredicates.put(normalizer.apply("Monstres Fusion"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Fusion"));
        typePredicates.put(normalizer.apply("Monstres Lien"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Link"));
        typePredicates.put(normalizer.apply("Monstres Gemeaux"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Gemini"));

        // Spell subtypes: require cardType contains "Spell" and property equals subtype
        Predicate<Model.CardsLists.Card> spellPredicateBase = c -> c.getCardType() != null && c.getCardType().contains("Spell");
        typePredicates.put(normalizer.apply("Magies Normales"), c -> spellPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Normal"));
        typePredicates.put(normalizer.apply("Magies Continues"), c -> spellPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Continuous"));
        typePredicates.put(normalizer.apply("Magies Jeu Rapide"), c -> spellPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Quick-Play"));
        typePredicates.put(normalizer.apply("Magies dEquipement"), c -> spellPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Equip"));
        typePredicates.put(normalizer.apply("Magies de Terrain"), c -> spellPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Field"));
        typePredicates.put(normalizer.apply("Magies Rituelles"), c -> spellPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Ritual"));

        // Trap subtypes: require cardType contains "Trap" and property equals subtype
        Predicate<Model.CardsLists.Card> trapPredicateBase = c -> c.getCardType() != null && c.getCardType().contains("Trap");
        typePredicates.put(normalizer.apply("Pieges normaux"), c -> trapPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Normal"));
        typePredicates.put(normalizer.apply("Pieges continus"), c -> trapPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Continuous"));
        typePredicates.put(normalizer.apply("Pieges contre"), c -> trapPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Counter"));

        // Add English equivalents mapping to same predicates (normalized keys)
        typePredicates.put(normalizer.apply("Beast Warrior"), typePredicates.get(normalizer.apply("Bete Guerrier")));
        typePredicates.put(normalizer.apply("Plant"), typePredicates.get(normalizer.apply("Plante")));
        typePredicates.put(normalizer.apply("Fiend"), typePredicates.get(normalizer.apply("Demon")));
        typePredicates.put(normalizer.apply("Dinosaur"), typePredicates.get(normalizer.apply("Dinosaure")));
        typePredicates.put(normalizer.apply("Spellcaster"), typePredicates.get(normalizer.apply("Magicien")));
        typePredicates.put(normalizer.apply("Fish"), typePredicates.get(normalizer.apply("Poisson")));
        typePredicates.put(normalizer.apply("Divine Beast"), typePredicates.get(normalizer.apply("Bete Divine")));
        typePredicates.put(normalizer.apply("Insect"), typePredicates.get(normalizer.apply("Insecte")));
        typePredicates.put(normalizer.apply("Winged Beast"), typePredicates.get(normalizer.apply("Bete Ailee")));
        typePredicates.put(normalizer.apply("Warrior"), typePredicates.get(normalizer.apply("Guerrier")));
        typePredicates.put(normalizer.apply("Rock"), typePredicates.get(normalizer.apply("Rocher")));
        typePredicates.put(normalizer.apply("Thunder"), typePredicates.get(normalizer.apply("Tonnerre")));
        typePredicates.put(normalizer.apply("Sea Serpent"), typePredicates.get(normalizer.apply("Serpent de Mer")));
        typePredicates.put(normalizer.apply("Beast"), typePredicates.get(normalizer.apply("Bete")));
        typePredicates.put(normalizer.apply("Psychic"), typePredicates.get(normalizer.apply("Psychique")));
        typePredicates.put(normalizer.apply("Fairy"), typePredicates.get(normalizer.apply("Elfe")));

        // Normal monster / spell / trap English keys
        typePredicates.put(normalizer.apply("Normal Monsters"), typePredicates.get(normalizer.apply("Monstres normaux")));
        typePredicates.put(normalizer.apply("Toon Monsters"), typePredicates.get(normalizer.apply("Monstres Toon")));
        typePredicates.put(normalizer.apply("Tuner Monsters"), typePredicates.get(normalizer.apply("Syntoniseurs")));
        typePredicates.put(normalizer.apply("Normal Spells"), typePredicates.get(normalizer.apply("Magies Normales")));
        typePredicates.put(normalizer.apply("Continuous Spells"), typePredicates.get(normalizer.apply("Magies Continues")));
        typePredicates.put(normalizer.apply("Quick Play Spells"), typePredicates.get(normalizer.apply("Magies Jeu Rapide")));
        typePredicates.put(normalizer.apply("Equip Spells"), typePredicates.get(normalizer.apply("Magies dEquipement")));
        typePredicates.put(normalizer.apply("Field Spells"), typePredicates.get(normalizer.apply("Magies de Terrain")));
        typePredicates.put(normalizer.apply("Ritual Spells"), typePredicates.get(normalizer.apply("Magies Rituelles")));
        typePredicates.put(normalizer.apply("Normal traps"), typePredicates.get(normalizer.apply("Pieges normaux")));
        typePredicates.put(normalizer.apply("Continuous traps"), typePredicates.get(normalizer.apply("Pieges continus")));
        typePredicates.put(normalizer.apply("Counter traps"), typePredicates.get(normalizer.apply("Pieges contre")));

        return typePredicates;
    }

    // -----------------------------------------------------------------------
    // Card-sorting decision
    // -----------------------------------------------------------------------

    /**
     * Section 1 of {@link #computeCardNeedsSorting}: checks whether {@code normalizedElement}
     * names a {@link Model.CardsLists.ThemeCollection} and, if so, whether {@code card} is
     * already accounted for in that collection's definition (cardsList) or in any of its
     * linked decks.
     *
     * <p>Operates non-destructively: any "removal" used to detect an already-accounted-for
     * copy happens on local copies of the lists, never on the live collection/deck lists.</p>
     *
     * @param card              the card being checked
     * @param normalizedElement the normalized element/category name being matched against
     * @param decksList         the loaded decks/collections list, or {@code null}
     * @param alreadyConsidered names already matched so far in this call; mutated by this
     *                          method as collections/linked-deck names are matched, so later
     *                          sections (type-category, default) don't double-count them
     * @return {@code true} (needs sorting) or {@code false} (sorted) the moment a matching
     * named collection resolves the question, or {@code null} if no collection with this
     * name accounted for the card at all (caller should fall through to the next section)
     */
    private static Boolean checkNamedCollectionMatch(
            Model.CardsLists.Card card,
            String normalizedElement,
            Model.CardsLists.DecksAndCollectionsList decksList,
            Set<String> alreadyConsidered) {
        if (decksList == null || decksList.getCollections() == null) {
            return null;
        }

        for (Model.CardsLists.ThemeCollection collection : decksList.getCollections()) {
            if (collection == null || collection.getName() == null) {
                continue;
            }
            String collectionName = Normalizer.normalize(collection.getName(), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);
            if (!collectionName.equals(normalizedElement)) {
                continue;
            }

            // Mark as considered
            alreadyConsidered.add(collectionName);

            // Work on a local copy of the collection's cardsList (non-destructive)
            List<Model.CardsLists.CardElement> collCardsOrig = collection.getCardsList();
            List<Model.CardsLists.CardElement> collCards = collCardsOrig == null
                    ? new ArrayList<>()
                    : new ArrayList<>(collCardsOrig);

            // Search the collection's cardsList (but NOT exceptionsToNotAdd nor archetypes)
            Model.CardsLists.CardElement matchedElement = null;
            for (Model.CardsLists.CardElement collElement : collCards) {
                if (ELEMENT_MATCHES_CARD.test(collElement, card)) {
                    matchedElement = collElement;
                    break;
                }
            }

            if (matchedElement != null) {
                // If dontRemove == true: consider sorted — unless the owned card is a quality upgrade
                Boolean dontRemove = matchedElement.getDontRemove() == null
                        ? false
                        : matchedElement.getDontRemove();
                if (dontRemove) {
                    // Still mark as needing sorting when the owned card has better condition or
                    // satisfies the expected rarity that the existing copy does not.
                    // We need a CardElement for the owned card to compare; here `card` is just a Card,
                    // so we rely on the caller (CardTreeCell) to do the full check via
                    // computeCardNeedsSortingWithUpgrade.  From computeCardNeedsSorting itself we
                    // conservatively return sorted (false) — upgrade detection is done at the
                    // context-menu level where the CardElement is available.
                    return false; // sorted
                }

                // dontRemove == false: we consider the card sorted for the collection,
                // but we must also analyze the linked decks and remove one occurrence per deck in each unit.
                // Operate on temporary copies of each deck's lists (non-destructive).
                if (collection.getLinkedDecks() != null) {
                    for (List<Model.CardsLists.Deck> unit : collection.getLinkedDecks()) {
                        if (unit == null) {
                            continue;
                        }
                        for (Model.CardsLists.Deck deck : unit) {
                            if (deck == null) {
                                continue;
                            }
                            // Create shallow copies of the deck lists
                            List<Model.CardsLists.CardElement> mainDeck = deck.getMainDeck() == null
                                    ? new ArrayList<>()
                                    : new ArrayList<>(deck.getMainDeck());
                            List<Model.CardsLists.CardElement> extraDeck = deck.getExtraDeck() == null
                                    ? new ArrayList<>()
                                    : new ArrayList<>(deck.getExtraDeck());
                            List<Model.CardsLists.CardElement> sideDeck = deck.getSideDeck() == null
                                    ? new ArrayList<>()
                                    : new ArrayList<>(deck.getSideDeck());

                            // Remove at most one occurrence per deck (from main, then extra, then side)
                            removeFirstMatchingFromList(mainDeck, card, ELEMENT_MATCHES_CARD);
                            removeFirstMatchingFromList(extraDeck, card, ELEMENT_MATCHES_CARD);
                            removeFirstMatchingFromList(sideDeck, card, ELEMENT_MATCHES_CARD);

                            // Note: we do not write these back to the original deck; this is non-destructive.
                        }
                    }
                }

                // Also mark the collection card as "consumed" in our local copy (non-destructive)
                removeFirstMatchingFromList(collCards, card, ELEMENT_MATCHES_CARD);

                // After processing collection card + linked decks (non-destructively), consider the card sorted
                return false;
            }

            // If not found in collection's cardsList, search the linked decks (non-destructive).
            boolean removedFromAnyDeck = false;
            if (collection.getLinkedDecks() != null) {
                for (List<Model.CardsLists.Deck> unit : collection.getLinkedDecks()) {
                    if (unit == null) {
                        continue;
                    }
                    // For each deck in the unit, attempt to remove one occurrence if present (on temp copies)
                    for (Model.CardsLists.Deck deck : unit) {
                        if (deck == null) {
                            continue;
                        }
                        List<Model.CardsLists.CardElement> mainDeck = deck.getMainDeck() == null
                                ? new ArrayList<>()
                                : new ArrayList<>(deck.getMainDeck());
                        List<Model.CardsLists.CardElement> extraDeck = deck.getExtraDeck() == null
                                ? new ArrayList<>()
                                : new ArrayList<>(deck.getExtraDeck());
                        List<Model.CardsLists.CardElement> sideDeck = deck.getSideDeck() == null
                                ? new ArrayList<>()
                                : new ArrayList<>(deck.getSideDeck());

                        boolean removed = removeFirstMatchingFromList(mainDeck, card, ELEMENT_MATCHES_CARD)
                                || removeFirstMatchingFromList(extraDeck, card, ELEMENT_MATCHES_CARD)
                                || removeFirstMatchingFromList(sideDeck, card, ELEMENT_MATCHES_CARD);

                        if (removed) {
                            removedFromAnyDeck = true;
                        }
                        // continue checking other decks in the same unit (we remove at most one per deck)
                    }
                }
            }

            if (removedFromAnyDeck) {
                // We found and "removed" at least one occurrence in linked decks (non-destructively) -> sorted
                return false;
            }

            // No match in this collection's cards nor linked decks -> keep checking other collections
        }
        return null;
    }

    /**
     * Section 2 of {@link #computeCardNeedsSorting}: checks whether {@code normalizedElement}
     * names a standalone {@link Model.CardsLists.Deck} and, if so, whether {@code card} is
     * already present in any of that deck's sections (main/extra/side).
     *
     * <p>Operates non-destructively, on local copies of the deck's lists.</p>
     *
     * @param card              the card being checked
     * @param normalizedElement the normalized element/category name being matched against
     * @param decksList         the loaded decks/collections list, or {@code null}
     * @param alreadyConsidered names already matched so far in this call; mutated by this
     *                          method when a matching deck is found
     * @return {@code true} (needs sorting) or {@code false} (sorted) the moment a deck with
     * this name is found, or {@code null} if no standalone deck has this name at all
     * (caller should fall through to the next section)
     */
    private static Boolean checkNamedDeckMatch(
            Model.CardsLists.Card card,
            String normalizedElement,
            Model.CardsLists.DecksAndCollectionsList decksList,
            Set<String> alreadyConsidered) {
        if (decksList == null || decksList.getDecks() == null) {
            return null;
        }

        for (Model.CardsLists.Deck deck : decksList.getDecks()) {
            if (deck == null || deck.getName() == null) {
                continue;
            }
            String deckName = Normalizer.normalize(deck.getName(), Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);
            if (!deckName.equals(normalizedElement)) {
                continue;
            }

            // Mark as considered
            alreadyConsidered.add(deckName);

            // Work on shallow copies of the lists so we don't mutate the real deck
            List<Model.CardsLists.CardElement> mainDeck = deck.getMainDeck() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(deck.getMainDeck());
            List<Model.CardsLists.CardElement> extraDeck = deck.getExtraDeck() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(deck.getExtraDeck());
            List<Model.CardsLists.CardElement> sideDeck = deck.getSideDeck() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(deck.getSideDeck());

            boolean removed = removeFirstMatchingFromList(mainDeck, card, ELEMENT_MATCHES_CARD)
                    || removeFirstMatchingFromList(extraDeck, card, ELEMENT_MATCHES_CARD)
                    || removeFirstMatchingFromList(sideDeck, card, ELEMENT_MATCHES_CARD);

            // Found in the deck matched by name (non-destructive removal) -> sorted;
            // deck matched by name but card not found -> unsorted
            return !removed;
        }
        return null;
    }

    /**
     * computeCardNeedsSorting (non-destructive version)
     *
     * <p>Same semantics as the previous implementation, but strictly non-destructive:
     * whenever we need to "mark" or "remove" a CardElement from a Collection or Deck
     * for the purpose of the check, we operate on local temporary copies of the lists
     * so the in-memory DecksAndCollectionsList objects are never mutated.</p>
     *
     * <p>Returns {@code true} when the card still needs sorting (should glow),
     * {@code false} when it is considered sorted.</p>
     */
    public static boolean computeCardNeedsSorting(Model.CardsLists.Card card, String elementName) {
        if (card == null || elementName == null) {
            return false;
        }

        try {
            // Normalize elementName for robust matching
            String normalizedElement = Normalizer.normalize(elementName, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);

            // Track already considered element names (collections/decks matched by name)
            final Set<String> alreadyConsidered = new HashSet<>();

            // Ensure decks & collections are loaded if possible
            Model.CardsLists.DecksAndCollectionsList decksList = UserInterfaceFunctions.getDecksList();
            if (decksList == null) {
                try {
                    UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
                    decksList = UserInterfaceFunctions.getDecksList();
                } catch (Exception ignored) {
                    decksList = null;
                }
            }

            Boolean collectionResult = checkNamedCollectionMatch(card, normalizedElement, decksList, alreadyConsidered);
            if (collectionResult != null) {
                return collectionResult;
            }

            Boolean deckResult = checkNamedDeckMatch(card, normalizedElement, decksList, alreadyConsidered);
            if (deckResult != null) {
                return deckResult;
            }

            // -------------------------
            // 3) Type-name matching step
            // -------------------------
            Function<String, String> normalizer = s -> s == null ? "" :
                    Normalizer.normalize(s, Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);

            Map<String, Predicate<Model.CardsLists.Card>> typePredicates = buildTypePredicates(normalizer);

            if (typePredicates.containsKey(normalizedElement)) {
                Model.CardsLists.OwnedCardsCollection ownedCollection = null;
                try {
                    ownedCollection = Model.CardsLists.OuicheList.getMyCardsCollection();
                } catch (Exception ignored) {
                }
                return checkTypeCategoryMatch(card, normalizedElement, decksList,
                        alreadyConsidered, normalizer, typePredicates, ownedCollection);
            }

            // -------------------------
            // 4) Default: element name did not match any known type.
            // -------------------------
            Model.CardsLists.OwnedCardsCollection ownedCollection = null;
            try {
                ownedCollection = Model.CardsLists.OuicheList.getMyCardsCollection();
            } catch (Exception ignored) {
            }
            return checkDefaultCoverageMatch(card, decksList, alreadyConsidered, normalizer, ownedCollection);

        } catch (Exception ex) {
            // On unexpected error, be conservative and return false (do not mark as unsorted)
            logger.debug("computeCardNeedsSorting failed for element '{}': {}", elementName, ex.toString());
            return false;
        }
    }

    /**
     * Section 3 of {@link #computeCardNeedsSorting}: the element name is a recognised
     * card-type category (e.g. "Dragon", "Monstres Fusion", "Magies Continues"). Returns
     * {@code true} (needs sorting) when the card doesn't belong to that type, or when any
     * deck/collection that lists this card still has unfilled placed copies. Returns
     * {@code false} (sorted) when the card's type matches but no deck needs it, or all
     * that do are already fully filled.
     *
     * @param ownedCollection the user's owned collection (for placed-copy counts), or
     *                        {@code null} if unavailable
     */
    private static boolean checkTypeCategoryMatch(
            Model.CardsLists.Card card,
            String normalizedElement,
            Model.CardsLists.DecksAndCollectionsList decksList,
            Set<String> alreadyConsidered,
            Function<String, String> normalizer,
            Map<String, Predicate<Model.CardsLists.Card>> typePredicates,
            Model.CardsLists.OwnedCardsCollection ownedCollection) {
        Predicate<Model.CardsLists.Card> predicate = typePredicates.get(normalizedElement);
        boolean matchesType = predicate != null && predicate.test(card);

        if (!matchesType) {
            // Card does not match the expected type of this category → misplaced → needs sorting.
            return true;
        }

        // Card matches the type. Search every collection (cardsList + linked decks) and every
        // loose deck, skipping ones already accounted for by sections 1/2, to find whether any
        // deck/collection definition still has an unfilled slot for this card.
        if (decksList != null && decksList.getCollections() != null) {
            for (Model.CardsLists.ThemeCollection collection : decksList.getCollections()) {
                if (collection == null || collection.getName() == null) {
                    continue;
                }
                String collectionName = normalizer.apply(collection.getName());
                if (alreadyConsidered.contains(collectionName)) {
                    continue;
                }

                List<Model.CardsLists.CardElement> collDefinitionMatches =
                        collectMatchingElementsInList(collection.getCardsList(), card);
                if (!collDefinitionMatches.isEmpty()) {
                    int requiredCount = collDefinitionMatches.size();
                    int placedCount = ownedCollection == null ? 0
                            : collectPlacedCopies(ownedCollection, collection.getName(), card, normalizer).size();
                    if (requiredCount > placedCount) {
                        return true;
                    }
                    // Fully filled — mark considered so linked decks don't double-count.
                    alreadyConsidered.add(collectionName);
                }

                if (collection.getLinkedDecks() != null) {
                    for (List<Model.CardsLists.Deck> unit : collection.getLinkedDecks()) {
                        if (unit == null) {
                            continue;
                        }
                        for (Model.CardsLists.Deck deck : unit) {
                            if (deck == null) {
                                continue;
                            }
                            String deckName = deck.getName() == null ? "" : normalizer.apply(deck.getName());
                            if (alreadyConsidered.contains(deckName)) {
                                continue;
                            }
                            int requiredTotal =
                                    collectMatchingElementsInList(deck.getMainDeck(), card).size()
                                            + collectMatchingElementsInList(deck.getExtraDeck(), card).size()
                                            + collectMatchingElementsInList(deck.getSideDeck(), card).size();
                            if (requiredTotal > 0) {
                                int placedCount = ownedCollection == null ? 0
                                        : collectPlacedCopies(ownedCollection, deck.getName(), card, normalizer).size();
                                if (requiredTotal > placedCount) {
                                    return true;
                                }
                                alreadyConsidered.add(deckName);
                            }
                        }
                    }
                }
            }
        }

        if (decksList != null && decksList.getDecks() != null) {
            for (Model.CardsLists.Deck deck : decksList.getDecks()) {
                if (deck == null || deck.getName() == null) {
                    continue;
                }
                String deckName = normalizer.apply(deck.getName());
                if (alreadyConsidered.contains(deckName)) {
                    continue;
                }
                int requiredTotal =
                        collectMatchingElementsInList(deck.getMainDeck(), card).size()
                                + collectMatchingElementsInList(deck.getExtraDeck(), card).size()
                                + collectMatchingElementsInList(deck.getSideDeck(), card).size();
                if (requiredTotal > 0) {
                    int placedCount = ownedCollection == null ? 0
                            : collectPlacedCopies(ownedCollection, deck.getName(), card, normalizer).size();
                    if (requiredTotal > placedCount) {
                        return true;
                    }
                }
            }
        }

        // Card matches the type but no deck/collection definition lists this specific card
        // → nothing to sort it into → considered sorted.
        return false;
    }

    /**
     * Section 4 of {@link #computeCardNeedsSorting}: the element name didn't match any
     * named collection, named deck, or recognised type category. Before giving up and
     * returning {@code false} (sorted), we scan every D&amp;C that lists this card for
     * unfilled placed copies. This prevents a spurious {@code false} that would block
     * {@link #computeCardNeedsSortingWithUpgrade} from evaluating the upgrade-candidate
     * case.
     *
     * @param ownedCollection the user's owned collection (for placed-copy counts), or
     *                        {@code null} if unavailable
     * @return {@code true} when any D&amp;C has unfilled slots for this card, {@code false}
     * when all slots are filled (or no D&amp;C lists this card at all)
     */
    private static boolean checkDefaultCoverageMatch(
            Model.CardsLists.Card card,
            Model.CardsLists.DecksAndCollectionsList decksList,
            Set<String> alreadyConsidered,
            Function<String, String> normalizer,
            Model.CardsLists.OwnedCardsCollection ownedCollection) {
        if (decksList != null && decksList.getCollections() != null) {
            for (Model.CardsLists.ThemeCollection collection : decksList.getCollections()) {
                if (collection == null || collection.getName() == null) {
                    continue;
                }
                if (alreadyConsidered.contains(normalizer.apply(collection.getName()))) {
                    continue;
                }

                List<Model.CardsLists.CardElement> collDefinitionMatches =
                        collectMatchingElementsInList(collection.getCardsList(), card);
                if (!collDefinitionMatches.isEmpty()) {
                    int requiredCount = collDefinitionMatches.size();
                    int placedCount = ownedCollection == null ? 0
                            : collectPlacedCopies(ownedCollection, collection.getName(), card, normalizer).size();
                    if (requiredCount > placedCount) {
                        return true;
                    }
                }

                if (collection.getLinkedDecks() != null) {
                    for (List<Model.CardsLists.Deck> unit : collection.getLinkedDecks()) {
                        if (unit == null) {
                            continue;
                        }
                        for (Model.CardsLists.Deck deck : unit) {
                            if (deck == null || deck.getName() == null) {
                                continue;
                            }
                            if (alreadyConsidered.contains(normalizer.apply(deck.getName()))) {
                                continue;
                            }
                            int requiredTotal =
                                    collectMatchingElementsInList(deck.getMainDeck(), card).size()
                                            + collectMatchingElementsInList(deck.getExtraDeck(), card).size()
                                            + collectMatchingElementsInList(deck.getSideDeck(), card).size();
                            if (requiredTotal > 0) {
                                int placedCount = ownedCollection == null ? 0
                                        : collectPlacedCopies(ownedCollection, deck.getName(), card, normalizer).size();
                                if (requiredTotal > placedCount) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (decksList != null && decksList.getDecks() != null) {
            for (Model.CardsLists.Deck deck : decksList.getDecks()) {
                if (deck == null || deck.getName() == null) {
                    continue;
                }
                if (alreadyConsidered.contains(normalizer.apply(deck.getName()))) {
                    continue;
                }
                int requiredTotal =
                        collectMatchingElementsInList(deck.getMainDeck(), card).size()
                                + collectMatchingElementsInList(deck.getExtraDeck(), card).size()
                                + collectMatchingElementsInList(deck.getSideDeck(), card).size();
                if (requiredTotal > 0) {
                    int placedCount = ownedCollection == null ? 0
                            : collectPlacedCopies(ownedCollection, deck.getName(), card, normalizer).size();
                    if (requiredTotal > placedCount) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Extended version of {@link #computeCardNeedsSorting} that additionally
     * returns {@code true} when the owned copy ({@code ownedElement}) would be a
     * quality upgrade over copies <em>already physically placed</em> in any deck or
     * collection whose slot is already fully filled — even if that deck/collection
     * already holds the required number of copies (reason 3 marking).
     *
     * <p>A quality upgrade is defined as:
     * <ul>
     *   <li>The owned copy has a <em>better physical condition</em> (lower
     *       {@link Model.CardsLists.CardCondition} ordinal) than at least one of
     *       the already-placed copies, OR</li>
     *   <li>The deck/collection slot specifies an <em>expected rarity</em> that
     *       none of the already-placed copies satisfy, but the owned copy does.</li>
     * </ul>
     * </p>
     *
     * <p>"Already placed" means owned copies that are stored in a
     * {@link Model.CardsLists.CardsGroup} whose name matches the deck or collection
     * name (the sorting category). Definition-slot entries (which carry no physical
     * condition) are not treated as placed copies.</p>
     *
     * <p>If {@code elementName} itself is a recognised deck or collection name, the
     * card is already sorted into that D&amp;C and this method returns {@code false}
     * immediately — that situation is reason 4, handled by
     * {@link #isDegradedCopyInDeckOrCollection}.</p>
     *
     * @param ownedElement the concrete owned {@link Model.CardsLists.CardElement}
     *                     being evaluated
     * @param elementName  the display name of the navigation group that contains
     *                     this card in the collection (e.g. "Fusion Monsters")
     * @return {@code true} if the card needs sorting or is a quality-upgrade
     *         candidate for a slot that is already filled
     */
    public static boolean computeCardNeedsSortingWithUpgrade(
            Model.CardsLists.CardElement ownedElement,
            String elementName) {
        if (ownedElement == null) {
            return false;
        }
        Model.CardsLists.Card card = ownedElement.getCard();
        if (card == null) {
            return false;
        }

        // First, run the standard check — if it says "needs sorting", we're done.
        if (computeCardNeedsSorting(card, elementName)) {
            return true;
        }

        // Standard check says "sorted". Now check for a quality-upgrade opportunity
        // (reason 3: owned copy is outside the D&C but is better than what is placed).
        try {
            Model.CardsLists.DecksAndCollectionsList decksList =
                    UserInterfaceFunctions.getDecksList();
            if (decksList == null) {
                return false;
            }

            Function<String, String> normalizer = s -> s == null ? "" :
                    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);

            String normalizedElement = normalizer.apply(elementName);

            // Guard: if the card is already placed inside a sorting category that IS
            // named after a known D&C, this is reason-4 territory (a better outside copy
            // should replace this one). Reason 3 does not apply here.
            if (isDeckOrCollectionName(normalizedElement, decksList, normalizer)) {
                return false;
            }

            // Load the owned collection so we can locate physically placed copies.
            Model.CardsLists.OwnedCardsCollection owned =
                    Model.CardsLists.OuicheList.getMyCardsCollection();
            if (owned == null) {
                return false;
            }

            // --- Collections (check ALL, no name filter) ---
            if (decksList.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection collection : decksList.getCollections()) {
                    if (collection == null || collection.getName() == null) {
                        continue;
                    }

                    // How many copies does the collection definition require?
                    List<Model.CardsLists.CardElement> collDefinitionMatches =
                            collectMatchingElementsInList(collection.getCardsList(), card);

                    if (!collDefinitionMatches.isEmpty()) {
                        // How many owned copies are physically in the sorting category?
                        List<Model.CardsLists.CardElement> placedCopies =
                                collectPlacedCopies(owned, collection.getName(), card, normalizer);
                        // Only worth checking when the slot is fully filled.
                        if (placedCopies.size() >= collDefinitionMatches.size()) {
                            for (Model.CardsLists.CardElement placed : placedCopies) {
                                if (isUpgradeOverPlacedCopy(
                                        ownedElement, placed, collection.getCardsList())) {
                                    return true;
                                }
                            }
                        }
                    }

                    // Check every linked deck inside this collection.
                    if (collection.getLinkedDecks() != null) {
                        for (List<Model.CardsLists.Deck> unit : collection.getLinkedDecks()) {
                            if (unit == null) {
                                continue;
                            }
                            for (Model.CardsLists.Deck deck : unit) {
                                if (deck == null || deck.getName() == null) {
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
                                    List<Model.CardsLists.CardElement> deckDefinitionMatches =
                                            collectMatchingElementsInList(deckSection, card);
                                    if (deckDefinitionMatches.isEmpty()) {
                                        continue;
                                    }
                                    List<Model.CardsLists.CardElement> deckPlaced =
                                            collectPlacedCopies(
                                                    owned, deck.getName(), card, normalizer);
                                    if (deckPlaced.size() >= deckDefinitionMatches.size()) {
                                        for (Model.CardsLists.CardElement placed : deckPlaced) {
                                            if (isUpgradeOverPlacedCopy(
                                                    ownedElement, placed, deckSection)) {
                                                return true;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Loose decks (check ALL, no name filter) ---
            if (decksList.getDecks() != null) {
                for (Model.CardsLists.Deck deck : decksList.getDecks()) {
                    if (deck == null || deck.getName() == null) {
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
                        List<Model.CardsLists.CardElement> deckDefinitionMatches =
                                collectMatchingElementsInList(deckSection, card);
                        if (deckDefinitionMatches.isEmpty()) {
                            continue;
                        }
                        List<Model.CardsLists.CardElement> deckPlaced =
                                collectPlacedCopies(owned, deck.getName(), card, normalizer);
                        if (deckPlaced.size() >= deckDefinitionMatches.size()) {
                            for (Model.CardsLists.CardElement placed : deckPlaced) {
                                if (isUpgradeOverPlacedCopy(ownedElement, placed, deckSection)) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.debug("computeCardNeedsSortingWithUpgrade failed for element '{}': {}",
                    elementName, ex.toString());
        }
        return false;
    }

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