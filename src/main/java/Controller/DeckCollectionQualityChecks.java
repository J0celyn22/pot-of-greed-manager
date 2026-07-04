package Controller;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.CardsLists.Deck;
import Model.CardsLists.ThemeCollection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.*;

/**
 * DeckCollectionQualityChecks — pure query/predicate helpers for detecting
 * missing cards, missing artworks, and identifier presence within a
 * {@link ThemeCollection} or {@link Deck}.
 *
 * <p>All methods are stateless and operate exclusively on Model objects.
 * This class has no dependency on JavaFX and contains no UI code.</p>
 *
 * <p>Extracted from {@link DecksCollectionsController}, which retains the
 * tree-building, drag-and-drop, and rename/scroll logic that actually needs
 * the shared TreeView and coordinator state. Nothing in this class ever
 * touched that state, so the cut required no dependency threading.</p>
 */
public final class DeckCollectionQualityChecks {

    private static final Logger logger =
            LoggerFactory.getLogger(DeckCollectionQualityChecks.class);

    /**
     * Utility class — no instances.
     */
    private DeckCollectionQualityChecks() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns {@code true} when at least one archetype in {@code collection} has a
     * missing card. Returns {@code false} if the collection provides no archetypes.
     */
    public static boolean collectionHasMissing(ThemeCollection collection) {
        if (collection == null) {
            return false;
        }
        try {
            Method archetypesMethod = collection.getClass().getMethod("getArchetypes");
            Object result = archetypesMethod.invoke(collection);
            if (!(result instanceof List)) {
                return false;
            }
            List<?> archetypes = (List<?>) result;
            if (archetypes == null || archetypes.isEmpty()) {
                return false;
            }
            for (Object archetypeObj : archetypes) {
                if (archetypeObj == null) {
                    continue;
                }
                List<CardElement> elements = new ArrayList<>();
                if (archetypeObj instanceof String archetypeName) {
                    archetypeName = archetypeName.trim();
                    if (archetypeName.isEmpty()) {
                        continue;
                    }
                    elements = buildElementsFromGlobalArchetype(archetypeName);
                } else {
                    String name = null;
                    try {
                        Method nameMethod = archetypeObj.getClass().getMethod("getName");
                        Object nameVal = nameMethod.invoke(archetypeObj);
                        if (nameVal != null) {
                            name = nameVal.toString();
                        }
                    } catch (Exception ignored) {
                    }
                    try {
                        Method cardsMethod = archetypeObj.getClass().getMethod("getCards");
                        Object cardsVal = cardsMethod.invoke(archetypeObj);
                        if (cardsVal instanceof List) {
                            for (Object entry : (List<?>) cardsVal) {
                                if (entry instanceof CardElement) {
                                    elements.add((CardElement) entry);
                                } else if (entry instanceof Card) {
                                    elements.add(new CardElement((Card) entry));
                                } else if (entry instanceof String) {
                                    try {
                                        elements.add(new CardElement((String) entry));
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    if (elements.isEmpty() && name != null) {
                        elements = buildElementsFromGlobalArchetype(name);
                    }
                }
                Set<String> missing = computeMissingIdsForElements(collection, elements);
                if (!missing.isEmpty()) {
                    return true;
                }
            }
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (Exception exception) {
            logger.debug("collectionHasMissing: reflection failed for collection "
                    + (collection.getName()), exception);
            return false;
        }
        return false;
    }

    /**
     * Returns {@code true} when at least one multi-artwork card in {@code collection}
     * is missing a sibling artwork.
     */
    public static boolean collectionHasMissingArtworks(ThemeCollection collection) {
        return !computeCardsWithMissingArtworks(collection).isEmpty();
    }

    /**
     * Computes the set of konamiIds and passCodes that ARE present in {@code collection}
     * but belong to a multi-artwork card missing at least one sibling artwork.
     *
     * @param collection the ThemeCollection to analyse
     * @return the set of identifiers to mark; empty if none
     */
    public static Set<String> computeCardsWithMissingArtworks(ThemeCollection collection) {
        Set<String> result = new HashSet<>();
        if (collection == null) {
            return result;
        }

        Map<Integer, Card> allCards;
        try {
            allCards = Model.Database.Database.getAllCardsList();
        } catch (Exception exception) {
            logger.warn("computeCardsWithMissingArtworks: DB unavailable for '{}': {}",
                    collection.getName(), exception.toString());
            return result;
        }
        if (allCards == null || allCards.isEmpty()) {
            return result;
        }

        Map<String, Set<String>> nameToAllArtNumbers = new HashMap<>();
        Map<String, Card> dbByKonamiId = new HashMap<>();

        for (Map.Entry<Integer, Card> entry : allCards.entrySet()) {
            Card dbCard = entry.getValue();
            if (dbCard == null) {
                continue;
            }
            String name = dbCard.getName_FR();
            if (name == null || name.isBlank()) {
                name = dbCard.getName_EN();
            }
            if (name == null || name.isBlank()) {
                continue;
            }
            String artNumber = dbCard.getArtNumber();
            if (artNumber == null || artNumber.isBlank()) {
                artNumber = "1";
            }
            nameToAllArtNumbers.computeIfAbsent(name, k -> new HashSet<>()).add(artNumber);
            if (dbCard.getKonamiId() != null && !dbCard.getKonamiId().isBlank()) {
                dbByKonamiId.putIfAbsent(dbCard.getKonamiId(), dbCard);
            }
        }

        nameToAllArtNumbers.entrySet().removeIf(entry -> entry.getValue().size() <= 1);
        if (nameToAllArtNumbers.isEmpty()) {
            return result;
        }

        List<CardElement> collectionCards = new ArrayList<>();
        if (collection.getCardsList() != null) {
            collectionCards.addAll(collection.getCardsList());
        }
        if (collection.getExceptionsToNotAdd() != null) {
            collectionCards.addAll(collection.getExceptionsToNotAdd());
        }

        Map<String, Set<String>> presentArtNumbers = new HashMap<>();
        Map<String, List<CardElement>> cardsByName = new HashMap<>();

        for (CardElement cardElement : collectionCards) {
            if (cardElement == null || cardElement.getCard() == null) {
                continue;
            }
            Card card = cardElement.getCard();
            String name = card.getName_FR();
            if (name == null || name.isBlank()) {
                name = card.getName_EN();
            }
            if (name == null || name.isBlank()) {
                continue;
            }
            if (!nameToAllArtNumbers.containsKey(name)) {
                continue;
            }

            String artNumber = null;
            if (card.getArtNumber() != null && !card.getArtNumber().isBlank()) {
                artNumber = card.getArtNumber();
            }
            if (artNumber == null && card.getPassCode() != null
                    && !card.getPassCode().isBlank()) {
                try {
                    Card dbCard = allCards.get(Integer.parseInt(card.getPassCode().trim()));
                    if (dbCard != null && dbCard.getArtNumber() != null
                            && !dbCard.getArtNumber().isBlank()) {
                        artNumber = dbCard.getArtNumber();
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (artNumber == null && card.getKonamiId() != null
                    && !card.getKonamiId().isBlank()) {
                Card dbCard = dbByKonamiId.get(card.getKonamiId());
                if (dbCard != null && dbCard.getArtNumber() != null
                        && !dbCard.getArtNumber().isBlank()) {
                    artNumber = dbCard.getArtNumber();
                }
            }
            if (artNumber == null || artNumber.isBlank()) {
                artNumber = "1";
            }

            presentArtNumbers.computeIfAbsent(name, k -> new HashSet<>()).add(artNumber);
            cardsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(cardElement);
        }

        for (Map.Entry<String, Set<String>> entry : presentArtNumbers.entrySet()) {
            String name = entry.getKey();
            Set<String> haveArts = entry.getValue();
            Set<String> allArts = nameToAllArtNumbers.get(name);
            if (haveArts.containsAll(allArts)) {
                continue;
            }
            List<CardElement> toMark = cardsByName.get(name);
            if (toMark == null) {
                continue;
            }
            for (CardElement cardElement : toMark) {
                if (cardElement.getCard() == null) {
                    continue;
                }
                Card card = cardElement.getCard();
                if (card.getKonamiId() != null && !card.getKonamiId().isBlank()) {
                    result.add(card.getKonamiId());
                }
                if (card.getPassCode() != null && !card.getPassCode().isBlank()) {
                    result.add(card.getPassCode().trim());
                }
            }
        }
        return result;
    }

    /**
     * Computes the set of konamiIds and passCodes from {@code elements} that are
     * NOT present in {@code collection}.
     *
     * @param collection the ThemeCollection to check against
     * @param elements   the archetype elements to test
     * @return the set of missing identifiers
     */
    public static Set<String> computeMissingIdsForElements(ThemeCollection collection,
                                                           List<CardElement> elements) {
        Set<String> missing = new HashSet<>();
        if (elements == null || elements.isEmpty()) {
            return missing;
        }
        for (CardElement cardElement : elements) {
            if (cardElement == null) {
                continue;
            }
            Card card = cardElement.getCard();
            if (card == null) {
                continue;
            }
            String konamiId = card.getKonamiId();
            String passCode = card.getPassCode();

            boolean found = false;
            if (konamiId != null && !konamiId.isEmpty()) {
                found = isKonamiIdPresentInCollection(collection, konamiId);
            }
            if (!found && passCode != null && !passCode.isEmpty()) {
                found = isPassCodePresentInCollection(collection, passCode);
            }

            if (!found) {
                if (konamiId != null && !konamiId.isEmpty()) {
                    missing.add(konamiId);
                }
                if (passCode != null && !passCode.isEmpty()) {
                    missing.add(passCode);
                }
            }
        }
        return missing;
    }

    /**
     * Returns {@code true} if {@code konamiId} is present anywhere in {@code collection}.
     */
    public static boolean isKonamiIdPresentInCollection(ThemeCollection collection, String konamiId) {
        if (konamiId == null || konamiId.isEmpty()) {
            return false;
        }
        try {
            List<CardElement> cardsList = collection.getCardsList();
            if (cardsList != null) {
                for (CardElement cardElement : cardsList) {
                    if (cardElement == null) {
                        continue;
                    }
                    Card card = cardElement.getCard();
                    if (card != null && konamiId.equals(card.getKonamiId())) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        try {
            Method exceptionsMethod = collection.getClass().getMethod("getExceptionsToNotAdd");
            Object exceptionsVal = exceptionsMethod.invoke(collection);
            if (exceptionsVal instanceof List) {
                for (Object entry : (List<?>) exceptionsVal) {
                    if (entry instanceof CardElement) {
                        Card card = ((CardElement) entry).getCard();
                        if (card != null && konamiId.equals(card.getKonamiId())) {
                            return true;
                        }
                    } else if (entry instanceof Card card) {
                        if (konamiId.equals(card.getKonamiId())) {
                            return true;
                        }
                    }
                }
            }
        } catch (NoSuchMethodException | java.lang.reflect.InvocationTargetException
                 | IllegalAccessException ignored) {
        }
        try {
            List<List<Deck>> linked = collection.getLinkedDecks();
            if (linked != null) {
                for (List<Deck> unit : linked) {
                    if (unit == null) {
                        continue;
                    }
                    for (Deck deck : unit) {
                        if (deck == null) {
                            continue;
                        }
                        if (deckContainsKonamiId(deck, konamiId)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code passCode} is present anywhere in {@code collection}.
     */
    public static boolean isPassCodePresentInCollection(ThemeCollection collection, String passCode) {
        if (passCode == null || passCode.isEmpty()) {
            return false;
        }
        try {
            List<CardElement> cardsList = collection.getCardsList();
            if (cardsList != null) {
                for (CardElement cardElement : cardsList) {
                    if (cardElement == null) {
                        continue;
                    }
                    Card card = cardElement.getCard();
                    if (card != null && passCode.equals(card.getPassCode())) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        try {
            Method exceptionsMethod = collection.getClass().getMethod("getExceptionsToNotAdd");
            Object exceptionsVal = exceptionsMethod.invoke(collection);
            if (exceptionsVal instanceof List) {
                for (Object entry : (List<?>) exceptionsVal) {
                    Card card = null;
                    if (entry instanceof CardElement) {
                        card = ((CardElement) entry).getCard();
                    } else if (entry instanceof Card) {
                        card = (Card) entry;
                    } else if (entry instanceof String stringEntry) {
                        if (!stringEntry.isBlank() && stringEntry.equals(passCode)) {
                            return true;
                        }
                    }
                    if (card != null && passCode.equals(card.getPassCode())) {
                        return true;
                    }
                }
            }
        } catch (NoSuchMethodException | java.lang.reflect.InvocationTargetException
                 | IllegalAccessException ignored) {
        }
        try {
            List<List<Deck>> linked = collection.getLinkedDecks();
            if (linked != null) {
                for (List<Deck> unit : linked) {
                    if (unit == null) {
                        continue;
                    }
                    for (Deck deck : unit) {
                        if (deck == null) {
                            continue;
                        }
                        if (deckContainsPassCode(deck, passCode)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Returns {@code true} if any section of {@code deck} contains a card with {@code konamiId}.
     */
    public static boolean deckContainsKonamiId(Deck deck, String konamiId) {
        if (deck == null || konamiId == null) {
            return false;
        }
        List<List<CardElement>> sections = new ArrayList<>();
        if (deck.getMainDeck() != null) {
            sections.add(deck.getMainDeck());
        }
        if (deck.getExtraDeck() != null) {
            sections.add(deck.getExtraDeck());
        }
        if (deck.getSideDeck() != null) {
            sections.add(deck.getSideDeck());
        }
        for (List<CardElement> section : sections) {
            try {
                for (CardElement cardElement : section) {
                    if (cardElement == null) {
                        continue;
                    }
                    Card card = cardElement.getCard();
                    if (card != null && konamiId.equals(card.getKonamiId())) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if any section of {@code deck} contains a card with {@code passCode}.
     */
    public static boolean deckContainsPassCode(Deck deck, String passCode) {
        if (deck == null || passCode == null) {
            return false;
        }
        List<List<CardElement>> sections = new ArrayList<>();
        if (deck.getMainDeck() != null) {
            sections.add(deck.getMainDeck());
        }
        if (deck.getExtraDeck() != null) {
            sections.add(deck.getExtraDeck());
        }
        if (deck.getSideDeck() != null) {
            sections.add(deck.getSideDeck());
        }
        for (List<CardElement> section : sections) {
            try {
                for (CardElement cardElement : section) {
                    if (cardElement == null) {
                        continue;
                    }
                    Card card = cardElement.getCard();
                    if (card != null && passCode.equals(card.getPassCode())) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if all card lists in {@code deck} are empty or null.
     */
    public static boolean isDeckEmpty(Deck deck) {
        if (deck == null) {
            return true;
        }
        boolean mainEmpty = deck.getMainDeck() == null || deck.getMainDeck().isEmpty();
        boolean extraEmpty = deck.getExtraDeck() == null || deck.getExtraDeck().isEmpty();
        boolean sideEmpty = deck.getSideDeck() == null || deck.getSideDeck().isEmpty();
        return mainEmpty && extraEmpty && sideEmpty;
    }

    /**
     * Returns {@code true} if the collection's cards list and exceptions list are both empty.
     */
    public static boolean isCollectionEmpty(ThemeCollection collection) {
        if (collection == null) {
            return true;
        }
        boolean cardsEmpty = collection.getCardsList() == null
                || collection.getCardsList().isEmpty();
        boolean exceptionsEmpty = collection.getExceptionsToNotAdd() == null
                || collection.getExceptionsToNotAdd().isEmpty();
        boolean linkedDecksEmpty = collection.getLinkedDecks() == null
                || collection.getLinkedDecks().stream().allMatch(unit ->
                unit == null || unit.isEmpty());
        return cardsEmpty && exceptionsEmpty && linkedDecksEmpty;
    }

    // ── Archetype element builder ─────────────────────────────────────────────

    /**
     * Builds a list of {@link CardElement}s for the given archetype name from the
     * global SubListCreator data.
     *
     * @param archetypeName the archetype name to look up (case-insensitive)
     * @return the list of elements; empty if the archetype is not found
     */
    public static List<CardElement> buildElementsFromGlobalArchetype(String archetypeName) {
        List<CardElement> elements = new ArrayList<>();
        if (archetypeName == null || archetypeName.trim().isEmpty()) {
            return elements;
        }
        try {
            List<String> globalNames = Model.CardsLists.SubListCreator.getArchetypesList();
            List<List<Card>> globalLists = Model.CardsLists.SubListCreator.getArchetypesCardsLists();
            if (globalNames == null || globalLists == null) {
                return elements;
            }
            for (int index = 0; index < globalNames.size(); index++) {
                String globalName = globalNames.get(index);
                if (globalName == null) {
                    continue;
                }
                if (globalName.equalsIgnoreCase(archetypeName.trim())) {
                    List<Card> cardsForArchetype =
                            globalLists.size() > index ? globalLists.get(index) : null;
                    if (cardsForArchetype != null) {
                        for (Card card : cardsForArchetype) {
                            if (card != null) {
                                elements.add(new CardElement(card));
                            }
                        }
                    }
                    break;
                }
            }
        } catch (Exception exception) {
            logger.debug("buildElementsFromGlobalArchetype failed for {}: {}",
                    archetypeName, exception.getMessage());
        }
        return elements;
    }

}