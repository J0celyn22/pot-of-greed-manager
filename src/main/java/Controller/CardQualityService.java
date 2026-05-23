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

    // -----------------------------------------------------------------------
    // Card-sorting decision
    // -----------------------------------------------------------------------

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

            // Helper: compare a CardElement to the Card.
            // Match by printCode only when BOTH sides have one; otherwise fall back to passCode.
            // Deck definitions typically store entries by passCode only (no printCode), so requiring
            // a printCode match when the definition entry has none would cause all owned cards that
            // carry a printCode to fail matching entirely.
            BiPredicate<Model.CardsLists.CardElement, Model.CardsLists.Card> matches =
                    (cardElement, referenceCard) -> {
                        if (cardElement == null || cardElement.getCard() == null || referenceCard == null) {
                            return false;
                        }
                        Model.CardsLists.Card elementCard = cardElement.getCard();
                        // Strict printCode match only when both sides have a printCode.
                        if (referenceCard.getPrintCode() != null && elementCard.getPrintCode() != null) {
                            return referenceCard.getPrintCode().equals(elementCard.getPrintCode());
                        }
                        // Otherwise fall back to passCode.
                        if (referenceCard.getPassCode() != null && elementCard.getPassCode() != null) {
                            return referenceCard.getPassCode().equals(elementCard.getPassCode());
                        }
                        return false;
                    };

            // -------------------------
            // 1) Collections with same name (non-destructive)
            // -------------------------
            if (decksList != null && decksList.getCollections() != null) {
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
                        if (matches.test(collElement, card)) {
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
                                    removeFirstMatchingFromList(mainDeck, card, matches);
                                    removeFirstMatchingFromList(extraDeck, card, matches);
                                    removeFirstMatchingFromList(sideDeck, card, matches);

                                    // Note: we do not write these back to the original deck; this is non-destructive.
                                }
                            }
                        }

                        // Also mark the collection card as "consumed" in our local copy (non-destructive)
                        removeFirstMatchingFromList(collCards, card, matches);

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

                                boolean removed = removeFirstMatchingFromList(mainDeck, card, matches)
                                        || removeFirstMatchingFromList(extraDeck, card, matches)
                                        || removeFirstMatchingFromList(sideDeck, card, matches);

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

                    // No match in collection cards nor linked decks -> continue to other checks
                }
            }

            // -------------------------
            // 2) Deck (loose deck) with same name (non-destructive)
            // -------------------------
            if (decksList != null && decksList.getDecks() != null) {
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

                    boolean removed = removeFirstMatchingFromList(mainDeck, card, matches)
                            || removeFirstMatchingFromList(extraDeck, card, matches)
                            || removeFirstMatchingFromList(sideDeck, card, matches);

                    if (removed) {
                        // Found in the deck matched by name (non-destructive removal) -> sorted
                        return false;
                    } else {
                        // Deck matched by name but card not found -> unsorted
                        return true;
                    }
                }
            }

            // -------------------------
            // 3) Type-name matching step
            // -------------------------
            // Build a map of normalized type names -> predicate that checks whether a Card matches that type.
            // Include both French and English names (normalized).
            Map<String, Predicate<Model.CardsLists.Card>> typePredicates = new HashMap<>();

            // Helper to normalize keys
            Function<String, String> normalizer = s -> s == null ? "" :
                    Normalizer.normalize(s, Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);

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

            // If element name corresponds to a known type, apply the logic described
            if (typePredicates.containsKey(normalizedElement)) {
                Predicate<Model.CardsLists.Card> predicate = typePredicates.get(normalizedElement);
                boolean matchesType = predicate != null && predicate.test(card);

                if (!matchesType) {
                    // Card does not match the expected type of this category → it is misplaced
                    // and should be marked as needing sorting.
                    return true;
                }

                // Card matches the type.
                // A deck "needs" this card if its definition contains an entry matching this card.
                // If found → the owned copy is already accounted for → sorted (false).
                // If a deck contains an entry for the same card AND the owned copy is not yet there →
                // that is handled by sections 1 & 2 (name-based matching).  Here we just check
                // whether the specific card is listed in any deck/collection definition at all.

                // Helper to search a deck's lists for a match (non-destructive check)
                Predicate<Model.CardsLists.Deck> deckNeedsThisCard = candidateDeck -> {
                    if (candidateDeck == null) {
                        return false;
                    }
                    List<Model.CardsLists.CardElement> mainDeck = candidateDeck.getMainDeck();
                    if (mainDeck != null) {
                        for (Model.CardsLists.CardElement cardElement : mainDeck) {
                            if (matches.test(cardElement, card)) {
                                return true;
                            }
                        }
                    }
                    List<Model.CardsLists.CardElement> extraDeck = candidateDeck.getExtraDeck();
                    if (extraDeck != null) {
                        for (Model.CardsLists.CardElement cardElement : extraDeck) {
                            if (matches.test(cardElement, card)) {
                                return true;
                            }
                        }
                    }
                    List<Model.CardsLists.CardElement> sideDeck = candidateDeck.getSideDeck();
                    if (sideDeck != null) {
                        for (Model.CardsLists.CardElement cardElement : sideDeck) {
                            if (matches.test(cardElement, card)) {
                                return true;
                            }
                        }
                    }
                    return false;
                };

                // Search collections (cardsList + linked decks), skipping alreadyConsidered
                if (decksList != null && decksList.getCollections() != null) {
                    for (Model.CardsLists.ThemeCollection collection : decksList.getCollections()) {
                        if (collection == null || collection.getName() == null) {
                            continue;
                        }
                        String collectionName = normalizer.apply(collection.getName());
                        if (alreadyConsidered.contains(collectionName)) {
                            continue;
                        }

                        // Check collection's own card list
                        List<Model.CardsLists.CardElement> collCards = collection.getCardsList();
                        if (collCards != null) {
                            for (Model.CardsLists.CardElement cardElement : collCards) {
                                if (matches.test(cardElement, card)) {
                                    // This collection needs this specific card → needs sorting
                                    return true;
                                }
                            }
                        }

                        // Check every linked deck
                        if (collection.getLinkedDecks() != null) {
                            for (List<Model.CardsLists.Deck> unit : collection.getLinkedDecks()) {
                                if (unit == null) {
                                    continue;
                                }
                                for (Model.CardsLists.Deck deck : unit) {
                                    if (deck == null) {
                                        continue;
                                    }
                                    String deckName = deck.getName() == null
                                            ? ""
                                            : normalizer.apply(deck.getName());
                                    if (alreadyConsidered.contains(deckName)) {
                                        continue;
                                    }
                                    if (deckNeedsThisCard.test(deck)) {
                                        // Deck definition requires this specific card → needs sorting
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }

                // Search loose decks
                if (decksList != null && decksList.getDecks() != null) {
                    for (Model.CardsLists.Deck deck : decksList.getDecks()) {
                        if (deck == null || deck.getName() == null) {
                            continue;
                        }
                        String deckName = normalizer.apply(deck.getName());
                        if (alreadyConsidered.contains(deckName)) {
                            continue;
                        }
                        if (deckNeedsThisCard.test(deck)) {
                            // Loose deck requires this specific card → needs sorting
                            return true;
                        }
                    }
                }

                // Card matches the type but no deck/collection definition lists this specific card
                // → nothing to sort it into → not unsorted
                return false;
            }

            // 4) Default: if no type matched, consider card unsorted (mark)
            return true;
        } catch (Exception ex) {
            // On unexpected error, be conservative and return false (do not mark as unsorted)
            logger.debug("computeCardNeedsSorting failed for element '{}': {}", elementName, ex.toString());
            return false;
        }
    }

    /**
     * Extended version of {@link #computeCardNeedsSorting} that additionally
     * returns {@code true} when the owned copy ({@code ownedElement}) would be a
     * quality upgrade over copies already placed in a matching deck or collection —
     * even if that deck/collection already holds the required number of copies.
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
     * @param ownedElement the concrete owned {@link Model.CardsLists.CardElement}
     *                     being evaluated (may be {@code null}, in which case this
     *                     falls back to {@link #computeCardNeedsSorting})
     * @param elementName  the display name of the navigation element that owns
     *                     this card in the collection
     * @return {@code true} if the card needs sorting or is a quality upgrade
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

        // Standard check says "sorted". Now check for a quality-upgrade opportunity.
        try {
            Model.CardsLists.DecksAndCollectionsList decksList = UserInterfaceFunctions.getDecksList();
            if (decksList == null) {
                return false;
            }

            String normalizedElement = java.text.Normalizer.normalize(elementName, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);

            Function<String, String> normalizer = s -> s == null ? "" :
                    java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);

            // Helper: collect all CardElements in a list that match `card`
            Function<List<Model.CardsLists.CardElement>, List<Model.CardsLists.CardElement>>
                    collectMatchingElements = list -> {
                List<Model.CardsLists.CardElement> result = new ArrayList<>();
                if (list == null) {
                    return result;
                }
                for (Model.CardsLists.CardElement cardElement : list) {
                    if (cardElement == null || cardElement.getCard() == null) {
                        continue;
                    }
                    Model.CardsLists.Card candidateCard = cardElement.getCard();
                    boolean same = (card.getPassCode() != null && card.getPassCode().equals(candidateCard.getPassCode()))
                            || (card.getPrintCode() != null && card.getPrintCode().equals(candidateCard.getPrintCode()))
                            || (card.getKonamiId() != null && card.getKonamiId().equals(candidateCard.getKonamiId()));
                    if (same) {
                        result.add(cardElement);
                    }
                }
                return result;
            };

            // --- Collections ---
            if (decksList.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection collection : decksList.getCollections()) {
                    if (collection == null || collection.getName() == null) {
                        continue;
                    }
                    if (!normalizer.apply(collection.getName()).equals(normalizedElement)) {
                        continue;
                    }

                    // Check collection's own card list
                    List<Model.CardsLists.CardElement> slotList = collection.getCardsList();
                    List<Model.CardsLists.CardElement> existingInColl = collectMatchingElements.apply(slotList);
                    if (!existingInColl.isEmpty()
                            && isQualityUpgrade(existingInColl, slotList, ownedElement)) {
                        return true;
                    }

                    // Check every linked deck
                    if (collection.getLinkedDecks() != null) {
                        for (List<Model.CardsLists.Deck> unit : collection.getLinkedDecks()) {
                            if (unit == null) {
                                continue;
                            }
                            for (Model.CardsLists.Deck deck : unit) {
                                if (deck == null) {
                                    continue;
                                }
                                for (List<Model.CardsLists.CardElement> deckList :
                                        java.util.Arrays.asList(deck.getMainDeck(), deck.getExtraDeck(), deck.getSideDeck())) {
                                    List<Model.CardsLists.CardElement> existingInDeck = collectMatchingElements.apply(deckList);
                                    if (!existingInDeck.isEmpty()
                                            && isQualityUpgrade(existingInDeck, deckList, ownedElement)) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- Loose decks ---
            if (decksList.getDecks() != null) {
                for (Model.CardsLists.Deck deck : decksList.getDecks()) {
                    if (deck == null || deck.getName() == null) {
                        continue;
                    }
                    if (!normalizer.apply(deck.getName()).equals(normalizedElement)) {
                        continue;
                    }

                    for (List<Model.CardsLists.CardElement> deckList :
                            java.util.Arrays.asList(deck.getMainDeck(), deck.getExtraDeck(), deck.getSideDeck())) {
                        List<Model.CardsLists.CardElement> existingInDeck = collectMatchingElements.apply(deckList);
                        if (!existingInDeck.isEmpty()
                                && isQualityUpgrade(existingInDeck, deckList, ownedElement)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logger.debug("computeCardNeedsSortingWithUpgrade failed for element '{}': {}", elementName, ex.toString());
        }
        return false;
    }

    /**
     * Remove the first CardElement in the provided list that matches the given Card
     * (by print/pass/konami). Returns {@code true} if an element was removed.
     * This helper operates on the list instance passed — caller should pass a copy
     * if non-destructive behaviour is required.
     */
    private static boolean removeFirstMatchingFromList(
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

            // Card-matching predicate (same logic as the matches predicate in computeCardNeedsSorting)
            Predicate<Model.CardsLists.CardElement> matchesCard = candidateElement -> {
                if (candidateElement == null || candidateElement.getCard() == null) {
                    return false;
                }
                Model.CardsLists.Card other = candidateElement.getCard();
                if (card.getPrintCode() != null && other.getPrintCode() != null) {
                    return card.getPrintCode().equals(other.getPrintCode());
                }
                if (card.getPassCode() != null && other.getPassCode() != null) {
                    return card.getPassCode().equals(other.getPassCode());
                }
                return false;
            };

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
     * contains at least one copy of the same card that is a quality upgrade over this
     * one — i.e. better physical condition, or the expected rarity that this copy lacks.
     *
     * @param deckElement          the element currently sitting in the deck/collection
     * @param deckOrCollectionName the display name of that deck or collection (used to
     *                             locate the slot list and read the expected rarity)
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

            for (Model.CardsLists.CardElement ownedCopy : ownedCopies) {
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
     * upgrades over {@code deckElement} (the copy currently in a deck/collection).
     *
     * @param deckElement          the element currently sitting in the deck/collection
     * @param deckOrCollectionName the display name of that deck or collection
     * @return a (possibly empty) list of owned elements that would improve this slot
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

            for (Model.CardsLists.CardElement ownedCopy : ownedCopies) {
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
     * Collects all {@link Model.CardsLists.CardElement}s in the owned collection that
     * refer to the same card as {@code card} (matched by passCode, printCode, or
     * konamiId).
     */
    static List<Model.CardsLists.CardElement> collectOwnedCopies(
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