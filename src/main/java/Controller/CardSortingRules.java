package Controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * CardSortingRules — pure domain logic for deciding which navigation-menu
 * section a card belongs to (named collection, named deck, type-category, or
 * default-coverage), used to detect misfiled cards during sorting.
 *
 * <p>All methods are stateless and operate exclusively on Model objects.
 * This class has no dependency on JavaFX and contains no UI code.</p>
 *
 * <p>Extracted from {@link CardQualityService}, which retains the smaller
 * shared primitives ({@link CardQualityService#cardsMatch}, {@link
 * CardQualityService#ELEMENT_MATCHES_CARD}) and the quality/upgrade and
 * collection-traversal helpers this class calls back into.</p>
 */
public final class CardSortingRules {

    private static final Logger logger = LoggerFactory.getLogger(CardSortingRules.class);

    /**
     * Utility class — no instances.
     */
    private CardSortingRules() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Builds the normalized-type-name → predicate map used by {@link #computeCardNeedsSorting}
     * to recognise category names (monster subtypes, spell/trap subtypes, both French and
     * English) and check whether a given card belongs to that category.
     *
     * @param normalizer the same name-normalization function used by the caller, so map keys
     *                   and lookup keys are normalized identically
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
                if (CardQualityService.ELEMENT_MATCHES_CARD.test(collElement, card)) {
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
                            CardQualityService.removeFirstMatchingFromList(mainDeck, card, CardQualityService.ELEMENT_MATCHES_CARD);
                            CardQualityService.removeFirstMatchingFromList(extraDeck, card, CardQualityService.ELEMENT_MATCHES_CARD);
                            CardQualityService.removeFirstMatchingFromList(sideDeck, card, CardQualityService.ELEMENT_MATCHES_CARD);

                            // Note: we do not write these back to the original deck; this is non-destructive.
                        }
                    }
                }

                // Also mark the collection card as "consumed" in our local copy (non-destructive)
                CardQualityService.removeFirstMatchingFromList(collCards, card, CardQualityService.ELEMENT_MATCHES_CARD);

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

                        boolean removed = CardQualityService.removeFirstMatchingFromList(mainDeck, card, CardQualityService.ELEMENT_MATCHES_CARD)
                                || CardQualityService.removeFirstMatchingFromList(extraDeck, card, CardQualityService.ELEMENT_MATCHES_CARD)
                                || CardQualityService.removeFirstMatchingFromList(sideDeck, card, CardQualityService.ELEMENT_MATCHES_CARD);

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

            boolean removed = CardQualityService.removeFirstMatchingFromList(mainDeck, card, CardQualityService.ELEMENT_MATCHES_CARD)
                    || CardQualityService.removeFirstMatchingFromList(extraDeck, card, CardQualityService.ELEMENT_MATCHES_CARD)
                    || CardQualityService.removeFirstMatchingFromList(sideDeck, card, CardQualityService.ELEMENT_MATCHES_CARD);

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
                        CardQualityService.collectMatchingElementsInList(collection.getCardsList(), card);
                if (!collDefinitionMatches.isEmpty()) {
                    int requiredCount = collDefinitionMatches.size();
                    int placedCount = ownedCollection == null ? 0
                            : CardQualityService.collectPlacedCopies(ownedCollection, collection.getName(), card, normalizer).size();
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
                                    CardQualityService.collectMatchingElementsInList(deck.getMainDeck(), card).size()
                                            + CardQualityService.collectMatchingElementsInList(deck.getExtraDeck(), card).size()
                                            + CardQualityService.collectMatchingElementsInList(deck.getSideDeck(), card).size();
                            if (requiredTotal > 0) {
                                int placedCount = ownedCollection == null ? 0
                                        : CardQualityService.collectPlacedCopies(ownedCollection, deck.getName(), card, normalizer).size();
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
                        CardQualityService.collectMatchingElementsInList(deck.getMainDeck(), card).size()
                                + CardQualityService.collectMatchingElementsInList(deck.getExtraDeck(), card).size()
                                + CardQualityService.collectMatchingElementsInList(deck.getSideDeck(), card).size();
                if (requiredTotal > 0) {
                    int placedCount = ownedCollection == null ? 0
                            : CardQualityService.collectPlacedCopies(ownedCollection, deck.getName(), card, normalizer).size();
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
                        CardQualityService.collectMatchingElementsInList(collection.getCardsList(), card);
                if (!collDefinitionMatches.isEmpty()) {
                    int requiredCount = collDefinitionMatches.size();
                    int placedCount = ownedCollection == null ? 0
                            : CardQualityService.collectPlacedCopies(ownedCollection, collection.getName(), card, normalizer).size();
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
                                    CardQualityService.collectMatchingElementsInList(deck.getMainDeck(), card).size()
                                            + CardQualityService.collectMatchingElementsInList(deck.getExtraDeck(), card).size()
                                            + CardQualityService.collectMatchingElementsInList(deck.getSideDeck(), card).size();
                            if (requiredTotal > 0) {
                                int placedCount = ownedCollection == null ? 0
                                        : CardQualityService.collectPlacedCopies(ownedCollection, deck.getName(), card, normalizer).size();
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
                        CardQualityService.collectMatchingElementsInList(deck.getMainDeck(), card).size()
                                + CardQualityService.collectMatchingElementsInList(deck.getExtraDeck(), card).size()
                                + CardQualityService.collectMatchingElementsInList(deck.getSideDeck(), card).size();
                if (requiredTotal > 0) {
                    int placedCount = ownedCollection == null ? 0
                            : CardQualityService.collectPlacedCopies(ownedCollection, deck.getName(), card, normalizer).size();
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
     * candidate for a slot that is already filled
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
            if (CardQualityService.isDeckOrCollectionName(normalizedElement, decksList, normalizer)) {
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
                            CardQualityService.collectMatchingElementsInList(collection.getCardsList(), card);

                    if (!collDefinitionMatches.isEmpty()) {
                        // How many owned copies are physically in the sorting category?
                        List<Model.CardsLists.CardElement> placedCopies =
                                CardQualityService.collectPlacedCopies(owned, collection.getName(), card, normalizer);
                        // Only worth checking when the slot is fully filled.
                        if (placedCopies.size() >= collDefinitionMatches.size()) {
                            for (Model.CardsLists.CardElement placed : placedCopies) {
                                if (CardQualityService.isUpgradeOverPlacedCopy(
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
                                            CardQualityService.collectMatchingElementsInList(deckSection, card);
                                    if (deckDefinitionMatches.isEmpty()) {
                                        continue;
                                    }
                                    List<Model.CardsLists.CardElement> deckPlaced =
                                            CardQualityService.collectPlacedCopies(
                                                    owned, deck.getName(), card, normalizer);
                                    if (deckPlaced.size() >= deckDefinitionMatches.size()) {
                                        for (Model.CardsLists.CardElement placed : deckPlaced) {
                                            if (CardQualityService.isUpgradeOverPlacedCopy(
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
                                CardQualityService.collectMatchingElementsInList(deckSection, card);
                        if (deckDefinitionMatches.isEmpty()) {
                            continue;
                        }
                        List<Model.CardsLists.CardElement> deckPlaced =
                                CardQualityService.collectPlacedCopies(owned, deck.getName(), card, normalizer);
                        if (deckPlaced.size() >= deckDefinitionMatches.size()) {
                            for (Model.CardsLists.CardElement placed : deckPlaced) {
                                if (CardQualityService.isUpgradeOverPlacedCopy(ownedElement, placed, deckSection)) {
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
}