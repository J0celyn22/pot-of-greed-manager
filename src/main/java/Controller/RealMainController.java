package Controller;

import Model.CardsLists.*;
import View.*;
import View.SharedCollectionTab.TabType;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RealMainController - updated to fix My Collection navigation wiring only.
 * <p>
 * Important: this file intentionally modifies only the My Collection menu wiring so that
 * clicking items in the My Collection navigation selects and scrolls the corresponding
 * node in the main My Collection TreeView. Decks & Collections navigation is left unchanged.
 */
public class RealMainController {

    private static final Logger logger = LoggerFactory.getLogger(RealMainController.class);

    private final DoubleProperty cardWidthProperty = new SimpleDoubleProperty(100);
    private final DoubleProperty cardHeightProperty = new SimpleDoubleProperty(146);

    @FXML
    private TabPane mainTabPane;
    @FXML
    private VBox allExistingCardsPane;
    @FXML
    private TextField nameTextField;
    @FXML
    private TextField printcodeTextField;
    @FXML
    private Button searchButton;
    private static final String ARCHETYPE_MARKER = "[ARCHETYPE]";
    @FXML
    private Button listMosaicButton;
    @FXML
    private Button printedUniqueButton;

    private SharedCollectionTab myCollectionTab;
    private SharedCollectionTab decksTab;
    private SharedCollectionTab ouicheListTab;
    private SharedCollectionTab archetypesTab;
    private SharedCollectionTab friendsTab;
    private SharedCollectionTab shopsTab;
    @FXML
    private AnchorPane cardsDisplayContainer;
    // keep references to the TreeViews so navigation items can select/scroll to nodes
    private TreeView<String> myCollectionTreeView;
    // Cache keyed by container object (Box or CardsGroup or other list owner).
    // Each value is a synchronized List<Boolean> where each index corresponds to the
    // card position inside that container. A null entry means "not yet computed".
    private final java.util.concurrent.ConcurrentHashMap<Object, List<Boolean>> positionSortCache = new java.util.concurrent.ConcurrentHashMap<>();
    private TreeView<String> ouicheTreeView;

    private boolean isMosaicMode = true;
    private boolean isPrintedMode = false;
    private boolean ouicheListLoaded = false;
    private TreeView<String> archetypesTreeView;
    private TreeView<String> decksAndCollectionsTreeView;

    /**
     * computeCardNeedsSorting (non-destructive version)
     * <p>
     * Same semantics as the previous implementation, but strictly non-destructive:
     * whenever we need to "mark" or "remove" a CardElement from a Collection or Deck
     * for the purpose of the check, we operate on local temporary copies of the lists
     * so the in-memory DecksAndCollectionsList objects are never mutated.
     * <p>
     * Returns true when the card still needs sorting (should glow), false when it is considered sorted.
     */
    public static boolean computeCardNeedsSorting(Model.CardsLists.Card card, String elementName) {
        if (card == null || elementName == null) return false;

        try {
            // Normalize elementName for robust matching
            String elem = Normalizer.normalize(elementName, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);

            // Track already considered element names (collections/decks matched by name)
            final java.util.Set<String> alreadyConsidered = new HashSet<>();

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

            // Helper: compare a CardElement to the Card (match by printCode if exists, otherwise by passCode)
            java.util.function.BiPredicate<Model.CardsLists.CardElement, Model.CardsLists.Card> matches =
                    (ce, c) -> {
                        if (ce == null || ce.getCard() == null || c == null) return false;
                        Model.CardsLists.Card other = ce.getCard();
                        if (c.getPrintCode() != null) {
                            if (other.getPrintCode() != null) {
                                if (other.getPrintCode().equals(c.getPrintCode())) {
                                    return true;
                                } else return false;
                            } else return false;
                        }
                        if (other.getPassCode() != null && other.getPassCode().equals(c.getPassCode())) return true;
                        return false;
                    };

            // -------------------------
            // 1) Collections with same name (non-destructive)
            // -------------------------
            if (decksList != null && decksList.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection collection : decksList.getCollections()) {
                    if (collection == null || collection.getName() == null) continue;
                    String collName = Normalizer.normalize(collection.getName(), Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);
                    if (!collName.equals(elem)) continue;

                    // Mark as considered
                    alreadyConsidered.add(collName);

                    // Work on a local copy of the collection's cardsList (non-destructive)
                    List<Model.CardsLists.CardElement> collCardsOrig = collection.getCardsList();
                    List<Model.CardsLists.CardElement> collCards = collCardsOrig == null
                            ? new ArrayList<>()
                            : new ArrayList<>(collCardsOrig);

                    // Search the collection's cardsList (but NOT exceptionsToNotAdd nor archetypes)
                    Model.CardsLists.CardElement matchedCe = null;
                    for (Model.CardsLists.CardElement ce : collCards) {
                        if (matches.test(ce, card)) {
                            matchedCe = ce;
                            break;
                        }
                    }

                    if (matchedCe != null) {
                        // If dontRemove == true: consider sorted and stop (no mutation)
                        Boolean dontRemove = matchedCe.getDontRemove() == null ? false : matchedCe.getDontRemove();
                        if (dontRemove) {
                            return false; // sorted
                        }

                        // dontRemove == false: we consider the card sorted for the collection,
                        // but we must also analyze the linked decks and remove one occurrence per deck in each unit.
                        // Operate on temporary copies of each deck's lists (non-destructive).
                        if (collection.getLinkedDecks() != null) {
                            for (List<Model.CardsLists.Deck> unit : collection.getLinkedDecks()) {
                                if (unit == null) continue;
                                for (Model.CardsLists.Deck d : unit) {
                                    if (d == null) continue;
                                    // Create shallow copies of the deck lists
                                    List<Model.CardsLists.CardElement> main = d.getMainDeck() == null ? new ArrayList<>() : new ArrayList<>(d.getMainDeck());
                                    List<Model.CardsLists.CardElement> extra = d.getExtraDeck() == null ? new ArrayList<>() : new ArrayList<>(d.getExtraDeck());
                                    List<Model.CardsLists.CardElement> side = d.getSideDeck() == null ? new ArrayList<>() : new ArrayList<>(d.getSideDeck());

                                    // Remove at most one occurrence per deck (from main, then extra, then side)
                                    removeFirstMatchingFromList(main, card, matches);
                                    removeFirstMatchingFromList(extra, card, matches);
                                    removeFirstMatchingFromList(side, card, matches);

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
                            if (unit == null) continue;
                            // For each deck in the unit, attempt to remove one occurrence if present (on temp copies)
                            for (Model.CardsLists.Deck d : unit) {
                                if (d == null) continue;
                                List<Model.CardsLists.CardElement> main = d.getMainDeck() == null ? new ArrayList<>() : new ArrayList<>(d.getMainDeck());
                                List<Model.CardsLists.CardElement> extra = d.getExtraDeck() == null ? new ArrayList<>() : new ArrayList<>(d.getExtraDeck());
                                List<Model.CardsLists.CardElement> side = d.getSideDeck() == null ? new ArrayList<>() : new ArrayList<>(d.getSideDeck());

                                boolean removed = removeFirstMatchingFromList(main, card, matches)
                                        || removeFirstMatchingFromList(extra, card, matches)
                                        || removeFirstMatchingFromList(side, card, matches);

                                if (removed) removedFromAnyDeck = true;
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
                    if (deck == null || deck.getName() == null) continue;
                    String deckName = Normalizer.normalize(deck.getName(), Normalizer.Form.NFD)
                            .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);
                    if (!deckName.equals(elem)) continue;

                    // Mark as considered
                    alreadyConsidered.add(deckName);

                    // Work on shallow copies of the lists so we don't mutate the real deck
                    List<Model.CardsLists.CardElement> main = deck.getMainDeck() == null ? new ArrayList<>() : new ArrayList<>(deck.getMainDeck());
                    List<Model.CardsLists.CardElement> extra = deck.getExtraDeck() == null ? new ArrayList<>() : new ArrayList<>(deck.getExtraDeck());
                    List<Model.CardsLists.CardElement> side = deck.getSideDeck() == null ? new ArrayList<>() : new ArrayList<>(deck.getSideDeck());

                    boolean removed = removeFirstMatchingFromList(main, card, matches)
                            || removeFirstMatchingFromList(extra, card, matches)
                            || removeFirstMatchingFromList(side, card, matches);

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
            Map<String, java.util.function.Predicate<Model.CardsLists.Card>> typePredicates = new HashMap<>();

            // Helper to normalize keys
            java.util.function.Function<String, String> norm = s -> s == null ? "" :
                    Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);

            // Monster subtype predicates (check cardProperties contains the subtype string as used in SubListCreator)
            typePredicates.put(norm.apply("Pyro"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Pyro"));
            typePredicates.put(norm.apply("Aqua"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Aqua"));
            typePredicates.put(norm.apply("Machine"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Machine"));
            typePredicates.put(norm.apply("Dragon"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Dragon"));
            typePredicates.put(norm.apply("Bete Guerrier"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Beast-Warrior"));
            typePredicates.put(norm.apply("Reptile"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Reptile"));
            typePredicates.put(norm.apply("Plante"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Plant"));
            typePredicates.put(norm.apply("Demon"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Fiend"));
            typePredicates.put(norm.apply("Wyrm"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Wyrm"));
            typePredicates.put(norm.apply("Dinosaure"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Dinosaur"));
            typePredicates.put(norm.apply("Magicien"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Spellcaster"));
            typePredicates.put(norm.apply("Poisson"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Fish"));
            typePredicates.put(norm.apply("Bete Divine"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Divine-Beast"));
            typePredicates.put(norm.apply("Cyberse"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Cyberse"));
            typePredicates.put(norm.apply("Insecte"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Insect"));
            typePredicates.put(norm.apply("Bete Ailee"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Winged Beast"));
            typePredicates.put(norm.apply("Guerrier"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Warrior"));
            typePredicates.put(norm.apply("Rocher"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Rock"));
            typePredicates.put(norm.apply("Tonnerre"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Thunder"));
            typePredicates.put(norm.apply("Zombie"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Zombie"));
            typePredicates.put(norm.apply("Serpent de Mer"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Sea Serpent"));
            typePredicates.put(norm.apply("Bete"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Beast"));
            typePredicates.put(norm.apply("Psychique"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Psychic"));
            typePredicates.put(norm.apply("Elfe"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Fairy"));
            typePredicates.put(norm.apply("Illusion"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Illusion"));

            // Normal / subtype monster lists (properties like "Normal", "Toon", "Tuner", etc.)
            typePredicates.put(norm.apply("Monstres normaux"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Normal"));
            typePredicates.put(norm.apply("Monstres Toon"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Toon"));
            typePredicates.put(norm.apply("Syntoniseurs"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Tuner"));
            typePredicates.put(norm.apply("Monstres Union"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Union"));
            typePredicates.put(norm.apply("Monstres Synchro"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Synchro"));
            typePredicates.put(norm.apply("Monstres Pendule"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Pendulum"));
            typePredicates.put(norm.apply("Monstres Rituels"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Ritual"));
            typePredicates.put(norm.apply("Monstres Flip"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Flip"));
            typePredicates.put(norm.apply("Monstres Spirit"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Spirit"));
            typePredicates.put(norm.apply("Monstres XYZ"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Xyz"));
            typePredicates.put(norm.apply("Monstres a Effet"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Effect"));
            typePredicates.put(norm.apply("Monstres Fusion"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Fusion"));
            typePredicates.put(norm.apply("Monstres Lien"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Link"));
            typePredicates.put(norm.apply("Monstres Gemeaux"), c -> c.getCardProperties() != null && c.getCardProperties().contains("Gemini"));

            // Spell subtypes: require cardType contains "Spell" and property equals subtype
            java.util.function.Predicate<Model.CardsLists.Card> spellPredicateBase = c -> c.getCardType() != null && c.getCardType().contains("Spell");
            typePredicates.put(norm.apply("Magies Normales"), c -> spellPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Normal"));
            typePredicates.put(norm.apply("Magies Continues"), c -> spellPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Continuous"));
            typePredicates.put(norm.apply("Magies Jeu Rapide"), c -> spellPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Quick-Play"));
            typePredicates.put(norm.apply("Magies dEquipement"), c -> spellPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Equip"));
            typePredicates.put(norm.apply("Magies de Terrain"), c -> spellPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Field"));
            typePredicates.put(norm.apply("Magies Rituelles"), c -> spellPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Ritual"));

            // Trap subtypes: require cardType contains "Trap" and property equals subtype
            java.util.function.Predicate<Model.CardsLists.Card> trapPredicateBase = c -> c.getCardType() != null && c.getCardType().contains("Trap");
            typePredicates.put(norm.apply("Pieges normaux"), c -> trapPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Normal"));
            typePredicates.put(norm.apply("Pieges continus"), c -> trapPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Continuous"));
            typePredicates.put(norm.apply("Pieges contre"), c -> trapPredicateBase.test(c) && c.getCardProperties() != null && c.getCardProperties().contains("Counter"));

            // Add English equivalents mapping to same predicates (normalized keys)
            typePredicates.put(norm.apply("Pyro"), typePredicates.get(norm.apply("Pyro")));
            typePredicates.put(norm.apply("Aqua"), typePredicates.get(norm.apply("Aqua")));
            typePredicates.put(norm.apply("Machine"), typePredicates.get(norm.apply("Machine")));
            typePredicates.put(norm.apply("Dragon"), typePredicates.get(norm.apply("Dragon")));
            typePredicates.put(norm.apply("Beast Warrior"), typePredicates.get(norm.apply("Bete Guerrier")));
            typePredicates.put(norm.apply("Plant"), typePredicates.get(norm.apply("Plante")));
            typePredicates.put(norm.apply("Fiend"), typePredicates.get(norm.apply("Demon")));
            typePredicates.put(norm.apply("Dinosaur"), typePredicates.get(norm.apply("Dinosaure")));
            typePredicates.put(norm.apply("Spellcaster"), typePredicates.get(norm.apply("Magicien")));
            typePredicates.put(norm.apply("Fish"), typePredicates.get(norm.apply("Poisson")));
            typePredicates.put(norm.apply("Divine Beast"), typePredicates.get(norm.apply("Bete Divine")));
            typePredicates.put(norm.apply("Cyberse"), typePredicates.get(norm.apply("Cyberse")));
            typePredicates.put(norm.apply("Insect"), typePredicates.get(norm.apply("Insecte")));
            typePredicates.put(norm.apply("Winged Beast"), typePredicates.get(norm.apply("Bete Ailee")));
            typePredicates.put(norm.apply("Warrior"), typePredicates.get(norm.apply("Guerrier")));
            typePredicates.put(norm.apply("Rock"), typePredicates.get(norm.apply("Rocher")));
            typePredicates.put(norm.apply("Thunder"), typePredicates.get(norm.apply("Tonnerre")));
            typePredicates.put(norm.apply("Zombie"), typePredicates.get(norm.apply("Zombie")));
            typePredicates.put(norm.apply("Sea Serpent"), typePredicates.get(norm.apply("Serpent de Mer")));
            typePredicates.put(norm.apply("Beast"), typePredicates.get(norm.apply("Bete")));
            typePredicates.put(norm.apply("Psychic"), typePredicates.get(norm.apply("Psychique")));
            typePredicates.put(norm.apply("Fairy"), typePredicates.get(norm.apply("Elfe")));
            typePredicates.put(norm.apply("Illusion"), typePredicates.get(norm.apply("Illusion")));

            // Normal monster / spell / trap English keys
            typePredicates.put(norm.apply("Normal Monsters"), typePredicates.get(norm.apply("Monstres normaux")));
            typePredicates.put(norm.apply("Toon Monsters"), typePredicates.get(norm.apply("Monstres Toon")));
            typePredicates.put(norm.apply("Tuner Monsters"), typePredicates.get(norm.apply("Syntoniseurs")));
            typePredicates.put(norm.apply("Normal Spells"), typePredicates.get(norm.apply("Magies Normales")));
            typePredicates.put(norm.apply("Continuous Spells"), typePredicates.get(norm.apply("Magies Continues")));
            typePredicates.put(norm.apply("Quick Play Spells"), typePredicates.get(norm.apply("Magies Jeu Rapide")));
            typePredicates.put(norm.apply("Equip Spells"), typePredicates.get(norm.apply("Magies dEquipement")));
            typePredicates.put(norm.apply("Field Spells"), typePredicates.get(norm.apply("Magies de Terrain")));
            typePredicates.put(norm.apply("Ritual Spells"), typePredicates.get(norm.apply("Magies Rituelles")));
            typePredicates.put(norm.apply("Normal traps"), typePredicates.get(norm.apply("Pieges normaux")));
            typePredicates.put(norm.apply("Continuous traps"), typePredicates.get(norm.apply("Pieges continus")));
            typePredicates.put(norm.apply("Counter traps"), typePredicates.get(norm.apply("Pieges contre")));

            // If element name corresponds to a known type, apply the logic described
            if (typePredicates.containsKey(elem)) {
                java.util.function.Predicate<Model.CardsLists.Card> predicate = typePredicates.get(elem);
                boolean matchesType = predicate != null && predicate.test(card);

                if (!matchesType) {
                    // If the element is a type but the card does not match that type => card is not sorted
                    return true;
                }

                // Card matches the type: now search Collections (cardsList + every linked deck) and loose decks
                // BUT skip any collection/deck whose normalized name is in alreadyConsidered.

                // Helper to search a deck's lists for a match (non-destructive check)
                java.util.function.Predicate<Model.CardsLists.Deck> deckContainsCard = d -> {
                    if (d == null) return false;
                    List<Model.CardsLists.CardElement> main = d.getMainDeck();
                    if (main != null) {
                        for (Model.CardsLists.CardElement ce : main) {
                            if (matches.test(ce, card)) return true;
                        }
                    }
                    List<Model.CardsLists.CardElement> extra = d.getExtraDeck();
                    if (extra != null) {
                        for (Model.CardsLists.CardElement ce : extra) {
                            if (matches.test(ce, card)) return true;
                        }
                    }
                    List<Model.CardsLists.CardElement> side = d.getSideDeck();
                    if (side != null) {
                        for (Model.CardsLists.CardElement ce : side) {
                            if (matches.test(ce, card)) return true;
                        }
                    }
                    return false;
                };

                // Search collections (cardsList + linked decks), skipping alreadyConsidered collections
                if (decksList != null && decksList.getCollections() != null) {
                    for (Model.CardsLists.ThemeCollection collection : decksList.getCollections()) {
                        if (collection == null || collection.getName() == null) continue;
                        String collName = norm.apply(collection.getName());
                        if (alreadyConsidered.contains(collName)) continue; // skip previously considered element

                        // Search collection's cardsList (but NOT exceptionsToNotAdd nor archetypes)
                        List<Model.CardsLists.CardElement> collCards = collection.getCardsList();
                        if (collCards != null) {
                            for (Model.CardsLists.CardElement ce : collCards) {
                                if (matches.test(ce, card)) {
                                    // Found in collection's cardsList -> card is not sorted
                                    return true;
                                }
                            }
                        }

                        // Search every linked deck inside this collection
                        if (collection.getLinkedDecks() != null) {
                            for (List<Model.CardsLists.Deck> unit : collection.getLinkedDecks()) {
                                if (unit == null) continue;
                                for (Model.CardsLists.Deck d : unit) {
                                    if (d == null) continue;
                                    String dName = d.getName() == null ? "" : norm.apply(d.getName());
                                    if (alreadyConsidered.contains(dName)) continue; // skip if previously considered
                                    if (deckContainsCard.test(d)) {
                                        return true; // found in a deck inside a collection -> not sorted
                                    }
                                }
                            }
                        }
                    }
                }

                // Search loose decks (decksList.getDecks()), skipping alreadyConsidered decks
                if (decksList != null && decksList.getDecks() != null) {
                    for (Model.CardsLists.Deck d : decksList.getDecks()) {
                        if (d == null || d.getName() == null) continue;
                        String dName = norm.apply(d.getName());
                        if (alreadyConsidered.contains(dName)) continue;
                        if (deckContainsCard.test(d)) {
                            return true; // found in a loose deck -> not sorted
                        }
                    }
                }

                // Not found in any collection or deck -> sorted
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

    private void setupZoom(SharedCollectionTab tab) {
        if (tab == null) return;
        tab.getContentPane().addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                adjustCardSize(event.getDeltaY());
                event.consume();
            }
        });
    }

    /**
     * Remove the first CardElement in the provided list that matches the given Card (by print/pass/konami).
     * Returns true if an element was removed. This helper operates on the list instance passed (caller should
     * pass a copy if non-destructive behavior is required).
     */
    private static boolean removeFirstMatchingFromList(List<Model.CardsLists.CardElement> list,
                                                       Model.CardsLists.Card card,
                                                       java.util.function.BiPredicate<Model.CardsLists.CardElement, Model.CardsLists.Card> matches) {
        if (list == null || card == null) return false;
        for (int i = 0; i < list.size(); i++) {
            Model.CardsLists.CardElement ce = list.get(i);
            if (ce == null || ce.getCard() == null) continue;
            if (matches.test(ce, card)) {
                list.remove(i);
                return true;
            }
        }
        return false;
    }

    private void adjustCardSize(double delta) {
        double scalingFactor = delta > 0 ? 1.1 : 0.9;
        double newWidth = cardWidthProperty.get() * scalingFactor;
        if (newWidth < 50) newWidth = 50;
        else if (newWidth > 300) newWidth = 300;
        cardWidthProperty.set(newWidth);
        cardHeightProperty.set(newWidth * 146.0 / 100.0);
    }

    private void updateCardsDisplay() {
        String nameFilter = (nameTextField == null ? "" : nameTextField.getText()).toLowerCase().trim();
        String codeFilter = (printcodeTextField == null ? "" : printcodeTextField.getText()).toLowerCase().trim();

        List<Card> allCards;
        try {
            allCards = isPrintedMode
                    ? Model.Database.Database.getAllPrintedCardsList().values().stream().collect(Collectors.toList())
                    : Model.Database.Database.getAllCardsList().values().stream().collect(Collectors.toList());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        List<Card> filteredCards = allCards.stream().filter(card -> {
            boolean matchesName = nameFilter.isEmpty() ||
                    (card.getName_EN() != null && card.getName_EN().toLowerCase().contains(nameFilter)) ||
                    (card.getName_FR() != null && card.getName_FR().toLowerCase().contains(nameFilter)) ||
                    (card.getName_JA() != null && card.getName_JA().toLowerCase().contains(nameFilter));
            boolean matchesCode = codeFilter.isEmpty() ||
                    (isPrintedMode
                            ? (card.getPrintCode() != null && card.getPrintCode().toLowerCase().contains(codeFilter))
                            : (card.getPassCode() != null && card.getPassCode().toLowerCase().contains(codeFilter)));
            return matchesName && matchesCode;
        }).collect(Collectors.toList());

        Node view;
        double mosaicImageWidth = 100, mosaicImageHeight = 146;
        double listImageWidth = 80, listImageHeight = 116;
        if (isMosaicMode) {
            double availableWidth = (cardsDisplayContainer == null ? 375 : cardsDisplayContainer.getWidth());
            if (availableWidth <= 0) availableWidth = 375;
            double gap = 5;
            List<List<Card>> rows = groupCardsIntoRows(filteredCards, availableWidth, mosaicImageWidth, gap);
            ListView<List<Card>> mosaicListView = new ListView<>(FXCollections.observableArrayList(rows));
            mosaicListView.setCellFactory(param -> new CardsMosaicRowCell(mosaicImageWidth, mosaicImageHeight));
            mosaicListView.setStyle("-fx-background-color: #100317; -fx-control-inner-background: #100317;");
            view = mosaicListView;
        } else {
            ListView<Card> listView = new ListView<>(FXCollections.observableArrayList(filteredCards));
            listView.setCellFactory(param -> new CardsListCell(isPrintedMode, listImageWidth, listImageHeight));
            listView.setStyle("-fx-background-color: #100317; -fx-control-inner-background: #100317;");
            view = listView;
        }

        if (cardsDisplayContainer != null) {
            cardsDisplayContainer.getChildren().clear();
            cardsDisplayContainer.getChildren().add(view);
            AnchorPane.setTopAnchor(view, 0.0);
            AnchorPane.setBottomAnchor(view, 0.0);
            AnchorPane.setLeftAnchor(view, 0.0);
            AnchorPane.setRightAnchor(view, 0.0);
        }
    }

    private List<List<Card>> groupCardsIntoRows(List<Card> cards, double availableWidth, double cellWidth, double gap) {
        int cardsPerRow = (int) Math.floor((availableWidth + gap) / (cellWidth + gap));
        if (cardsPerRow < 1) cardsPerRow = 1;
        List<List<Card>> rows = new ArrayList<>();
        for (int i = 0; i < cards.size(); i += cardsPerRow) {
            int end = Math.min(i + cardsPerRow, cards.size());
            rows.add(new ArrayList<>(cards.subList(i, end)));
        }
        return rows;
    }

    // --- My Collection display ---

    @FXML
    private void initialize() {
        UserInterfaceFunctions.readPathsFromFile();

        try {
            Map<Integer, Card> allCards = Model.Database.Database.getAllCardsList();
            if (allCards != null && !allCards.isEmpty()) {
                SubListCreator.CreateArchetypeLists(allCards);
                logger.info("SubListCreator archetypes loaded: names={}, lists={}",
                        SubListCreator.archetypesList == null ? 0 : SubListCreator.archetypesList.size(),
                        SubListCreator.archetypesCardsLists == null ? 0 : SubListCreator.archetypesCardsLists.size());
            } else {
                logger.info("Database.getAllCardsList() returned empty or null; archetypes not initialized now.");
            }
        } catch (Exception e) {
            logger.warn("Failed to initialize SubListCreator archetypes at startup", e);
        }

        if (listMosaicButton != null) listMosaicButton.setText("List");
        if (printedUniqueButton != null) printedUniqueButton.setText("Printed");

        myCollectionTab = new SharedCollectionTab(TabType.MY_COLLECTION);
        decksTab = new SharedCollectionTab(TabType.DECKS);
        ouicheListTab = new SharedCollectionTab(TabType.OUICHE_LIST);
        archetypesTab = new SharedCollectionTab(TabType.ARCHETYPES);
        friendsTab = new SharedCollectionTab(TabType.FRIENDS);
        shopsTab = new SharedCollectionTab(TabType.SHOPS);

        setupZoom(myCollectionTab);
        setupZoom(decksTab);
        setupZoom(ouicheListTab);
        setupZoom(archetypesTab);

        if (mainTabPane != null && mainTabPane.getTabs().size() >= 6) {
            mainTabPane.getTabs().get(0).setContent(myCollectionTab);
            mainTabPane.getTabs().get(1).setContent(decksTab);
            mainTabPane.getTabs().get(2).setContent(ouicheListTab);
            mainTabPane.getTabs().get(3).setContent(archetypesTab);
            mainTabPane.getTabs().get(4).setContent(friendsTab);
            mainTabPane.getTabs().get(5).setContent(shopsTab);
        }

        try {
            displayMyCollection();
            populateMyCollectionMenu();
        } catch (Exception ex) {
            logger.error("Error displaying My Collection", ex);
        }

        if (mainTabPane != null) {
            mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                int selectedIndex = mainTabPane.getTabs().indexOf(newTab);
                if (selectedIndex == 1) {
                    try {
                        populateDecksAndCollectionsMenu();
                        displayDecksAndCollections();
                    } catch (Exception e) {
                        logger.error("Error displaying decks and collections", e);
                    }
                } else if (selectedIndex == 2 && !ouicheListLoaded) {
                    try {
                        UserInterfaceFunctions.generateOuicheList();
                        displayOuicheListUnified();
                        populateOuicheListMenu();
                        ouicheListLoaded = true;
                    } catch (Exception ex) {
                        logger.error("Error displaying OuicheList", ex);
                    }
                } else if (selectedIndex == 3) {
                    try {
                        displayArchetypes();
                        populateArchetypesMenu();
                    } catch (Exception ex) {
                        logger.error("Error displaying Archetypes", ex);
                    }
                }
            });
        }

        // Wire OuicheList compact/detailed toggle buttons
        setupOuicheListButtons();

        decksTab.setOnDecksLoad(() -> {
            try {
                displayDecksAndCollections();
            } catch (Exception e) {
                logger.error("Error displaying decks and collections", e);
            }
        });

        if (searchButton != null) searchButton.setOnAction(e -> updateCardsDisplay());
        if (listMosaicButton != null) listMosaicButton.setOnAction(e -> {
            isMosaicMode = !isMosaicMode;
            listMosaicButton.setText(isMosaicMode ? "List" : "Mosaic");
            updateCardsDisplay();
        });
        if (printedUniqueButton != null) printedUniqueButton.setOnAction(e -> {
            isPrintedMode = !isPrintedMode;
            printedUniqueButton.setText(isPrintedMode ? "Unique" : "Printed");
            updateCardsDisplay();
        });
        if (nameTextField != null)
            nameTextField.textProperty().addListener((obs, oldVal, newVal) -> updateCardsDisplay());
        if (printcodeTextField != null)
            printcodeTextField.textProperty().addListener((obs, oldVal, newVal) -> updateCardsDisplay());

        updateCardsDisplay();

        UserInterfaceFunctions.registerOwnedCollectionRefresher(this::refreshFromModel);

        // Put this in RealMainController.initialize() or equivalent setup method
        final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Controller.RealMainController.class);

        /*if (mainTabPane != null) {
            mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                String tabName = newTab == null ? "<null>" : newTab.getText();
                logger.debug("mainTabPane selection changed -> {}", tabName);

                // When OuicheList is selected, run the marking/refresh logic that previously ran for Decks and Collections.
                if (tabName != null && tabName.trim().equalsIgnoreCase("OuicheList")) {
                    logger.debug("OuicheList selected: running marking/refresh for OuicheList");
                    try {
                        // --- Replace the next line with your existing marking/refresh method(s) ---
                        // Example: recompute archetypes / mark missing for the OuicheList collection(s)
                        // markMissingForAllCollections(); // <-- your real method here
                        // Or call the same method you call when Decks and Collections is selected,
                        // but pass the OuicheList context/collection name if needed.
                    } catch (Throwable t) {
                        logger.warn("Error while running OuicheList marking/refresh", t);
                    }
                } else {
                    // Optional: if you need to clear or refresh visuals when leaving OuicheList
                    logger.debug("Non-OuicheList tab selected: {}", tabName);
                }
            });
        }*/
    }

    public void refreshFromModel() {
        // Ensure we run on FX thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refreshFromModel);
            return;
        }

        Logger logger = LoggerFactory.getLogger(RealMainController.class);
        try {
            // 1) Try to call an explicit refresh method if you already have one (common names)
            String[] candidateMethodNames = {
                    "refresh", "refreshView", "reload", "reloadModel", "updateView", "rebuildTree"
            };

            java.util.Set<String> invoked = new java.util.HashSet<>();
            for (String mName : candidateMethodNames) {
                if (mName == null || mName.trim().isEmpty()) continue;
                try {
                    Method m = null;
                    try {
                        m = this.getClass().getMethod(mName);
                    } catch (NoSuchMethodException ignored) {
                        // method not present, continue
                    }
                    if (m == null) continue;

                    // Defensive checks: only call no-arg methods, skip if it's this method
                    if (m.getParameterCount() != 0) continue;
                    if (m.getName().equals("refreshFromModel") && m.getDeclaringClass() == this.getClass()) continue;

                    // Avoid invoking the same method twice
                    if (invoked.contains(m.getName())) continue;

                    try {
                        m.invoke(this);
                        invoked.add(m.getName());
                        logger.debug("refreshFromModel: invoked controller method {}", mName);
                        return; // assume that method handled refresh
                    } catch (Throwable t) {
                        logger.debug("refreshFromModel: invoking {} failed", mName, t);
                    }
                } catch (Throwable t) {
                    logger.debug("refreshFromModel: reflection check for {} failed", mName, t);
                }
            }

            // 2) Try to refresh TreeView/ListView fields declared on this controller
            boolean refreshed = false;
            Field[] fields = this.getClass().getDeclaredFields();
            for (Field f : fields) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(this);
                    if (val == null) continue;

                    if (val instanceof TreeView) {
                        try {
                            ((TreeView<?>) val).refresh();
                            refreshed = true;
                            logger.debug("refreshFromModel: refreshed TreeView field '{}'", f.getName());
                        } catch (Throwable t) {
                            logger.debug("refreshFromModel: TreeView.refresh() failed on field {}", f.getName(), t);
                        }
                    } else if (val instanceof ListView) {
                        try {
                            ((ListView<?>) val).refresh();
                            refreshed = true;
                            logger.debug("refreshFromModel: refreshed ListView field '{}'", f.getName());
                        } catch (Throwable t) {
                            logger.debug("refreshFromModel: ListView.refresh() failed on field {}", f.getName(), t);
                        }
                    } else if (val instanceof Parent) {
                        // traverse the scene graph rooted at this Parent to find TreeView/ListView
                        Parent root = (Parent) val;
                        Deque<Node> dq = new ArrayDeque<>();
                        dq.add(root);
                        while (!dq.isEmpty()) {
                            Node n = dq.poll();
                            if (n == null) continue;
                            if (n instanceof TreeView) {
                                try {
                                    ((TreeView<?>) n).refresh();
                                    refreshed = true;
                                } catch (Throwable t) {
                                    logger.debug("refreshFromModel: TreeView.refresh() failed during traversal", t);
                                }
                            } else if (n instanceof ListView) {
                                try {
                                    ((ListView<?>) n).refresh();
                                    refreshed = true;
                                } catch (Throwable t) {
                                    logger.debug("refreshFromModel: ListView.refresh() failed during traversal", t);
                                }
                            }
                            if (n instanceof Parent) {
                                try {
                                    for (Node child : ((Parent) n).getChildrenUnmodifiable()) dq.add(child);
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                        if (refreshed)
                            logger.debug("refreshFromModel: refreshed controls found under Parent field '{}'", f.getName());
                    }
                } catch (Throwable t) {
                    logger.debug("refreshFromModel: inspecting field {} failed", f.getName(), t);
                }
            }

            // 3) If nothing refreshed, try a last-resort re-acquire of the model so bindings update
            if (!refreshed) {
                try {
                    // Re-acquire the shared OwnedCardsCollection so any bound controls can pick up changes.
                    // This does not save anything.
                    Model.CardsLists.OwnedCardsCollection owned = null;
                    try {
                        owned = Model.CardsLists.OuicheList.getMyCardsCollection();
                    } catch (Throwable ignored) {
                    }
                    if (owned == null) {
                        try {
                            Controller.UserInterfaceFunctions.loadCollectionFile();
                        } catch (Throwable ignored) {
                        }
                        try {
                            owned = Model.CardsLists.OuicheList.getMyCardsCollection();
                        } catch (Throwable ignored) {
                        }
                    }
                    logger.debug("refreshFromModel: re-acquired owned collection = {}", owned != null);
                } catch (Throwable t) {
                    logger.debug("refreshFromModel: model re-acquire failed", t);
                }
            }

            if (!refreshed) {
                logger.info("refreshFromModel: no controls refreshed automatically. " +
                        "If you have a specific view control, either implement a dedicated refresh method or register an explicit refresher.");
            }
        } catch (Throwable ex) {
            logger.debug("refreshFromModel failed", ex);
        }
    }

    private void displayMyCollection() throws Exception {
        AnchorPane contentPane = myCollectionTab.getContentPane();
        contentPane.getChildren().clear();

        //OwnedCardsCollection collection = new OwnedCardsCollection(UserInterfaceFunctions.filePath.getAbsolutePath());
        OwnedCardsCollection collection = null;
        try {
            collection = Model.CardsLists.OuicheList.getMyCardsCollection();
        } catch (Throwable ignored) {
        }
        if (collection == null) {
            try {
                Controller.UserInterfaceFunctions.loadCollectionFile();
            } catch (Throwable ignored) {
            }
            try {
                collection = Model.CardsLists.OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
        }
        if (collection == null || collection.getOwnedCollection() == null) {
            logger.warn("OwnedCardsCollection is not available.");
        }

        DataTreeItem<Object> rootItem = new DataTreeItem<>("My Cards Collection", "ROOT");
        rootItem.setExpanded(true);
        for (Box box : collection.getOwnedCollection()) {
            DataTreeItem<Object> boxItem = createBoxTreeItem(box);
            rootItem.getChildren().add(boxItem);
        }

        myCollectionTreeView = new TreeView<>(rootItem);
        myCollectionTreeView.setUserData("MY_COLLECTION");
        myCollectionTreeView.setCellFactory(param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        myCollectionTreeView.setStyle("-fx-background-color: #100317;");
        myCollectionTreeView.setShowRoot(false);
        contentPane.getChildren().add(myCollectionTreeView);
        AnchorPane.setTopAnchor(myCollectionTreeView, 0.0);
        AnchorPane.setBottomAnchor(myCollectionTreeView, 0.0);
        AnchorPane.setLeftAnchor(myCollectionTreeView, 0.0);
        AnchorPane.setRightAnchor(myCollectionTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        myCollectionTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());
        logger.info("My Collection displayed.");
    }

    /**
     * Populate the My Collection navigation menu.
     *
     * IMPORTANT: This method wires click handlers so that clicking a navigation label
     * selects and scrolls the corresponding node in the My Collection TreeView.
     *
     * The path matching used by navigateToTree(...) mirrors the tree structure created by
     * createBoxTreeItem/createGroupTreeItem so matching succeeds reliably.
     */
    private void populateMyCollectionMenu() throws Exception {
        VBox menuVBox = myCollectionTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        OwnedCardsCollection collection = null;
        try {
            collection = Model.CardsLists.OuicheList.getMyCardsCollection();
        } catch (Throwable ignored) {
        }
        if (collection == null) {
            try {
                Controller.UserInterfaceFunctions.loadCollectionFile();
            } catch (Throwable ignored) {
            }
            try {
                collection = Model.CardsLists.OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
        }
        if (collection == null || collection.getOwnedCollection() == null) {
            logger.warn("OwnedCardsCollection is not available.");
        }

        if (collection != null && collection.getOwnedCollection() != null) {
            for (Box box : collection.getOwnedCollection()) {
                String rawBoxName = box.getName() == null ? "" : box.getName();
                String boxName = rawBoxName.replaceAll("[=\\-]", "");
                NavigationItem boxItem = createNavigationItem(boxName, 0);

                // --- navigation wiring (unchanged) ---
                boxItem.setOnLabelClicked(evt -> navigateToTree(myCollectionTreeView, boxName));

                // --- highlight logic (unchanged) ---
                boolean boxHasUnsorted = boxHasUnsortedCards(box, boxName);
                applyNavigationItemHighlight(boxItem, boxHasUnsorted);

                // --- NEW: context menu for Box items ---
                {
                    ContextMenu boxCm = NavigationContextMenuBuilder.forMyCollectionBox(boxName);
                    boxItem.setOnContextMenuRequested(e -> {
                        boxCm.show(boxItem, e.getScreenX(), e.getScreenY());
                        e.consume(); // prevent event from reaching the menuVBox background handler
                    });
                }

                // Add groups (Cards groups) under the box
                if (box.getContent() != null) {
                    for (CardsGroup group : box.getContent()) {
                        String rawGroupName = group.getName() == null ? "" : group.getName();
                        String groupName = rawGroupName.replaceAll("[=\\-]", "");
                        NavigationItem groupItem = createNavigationItem(groupName, 1);

                        // navigation wiring: click navigates to box -> group (unchanged)
                        groupItem.setOnLabelClicked(evt -> navigateToTree(myCollectionTreeView, boxName, groupName));

                        // highlight (unchanged)
                        boolean groupHasUnsorted = groupHasUnsortedCards(group, groupName);
                        applyNavigationItemHighlight(groupItem, groupHasUnsorted);
                        if (groupHasUnsorted && !boxHasUnsorted) {
                            applyNavigationItemHighlight(boxItem, true);
                            boxHasUnsorted = true;
                        }

                        // --- NEW: context menu for Category items ---
                        {
                            ContextMenu groupCm = NavigationContextMenuBuilder.forMyCollectionCategory(groupName);
                            groupItem.setOnContextMenuRequested(e -> {
                                groupCm.show(groupItem, e.getScreenX(), e.getScreenY());
                                e.consume();
                            });
                        }

                        boxItem.addSubItem(groupItem);
                    }
                }

                // Add sub-boxes and their groups (unchanged logic; add context menus to sub-items too)
                if (box.getSubBoxes() != null) {
                    for (Box subBox : box.getSubBoxes()) {
                        String rawSubBoxName = subBox.getName() == null ? "" : subBox.getName();
                        String subBoxName = rawSubBoxName.replaceAll("[=\\-]", "");
                        NavigationItem subBoxItem = createNavigationItem(subBoxName, 1);

                        subBoxItem.setOnLabelClicked(evt -> navigateToTree(myCollectionTreeView, subBoxName));

                        boolean subBoxHasUnsorted = boxHasUnsortedCards(subBox, subBoxName);
                        applyNavigationItemHighlight(subBoxItem, subBoxHasUnsorted);
                        if (subBoxHasUnsorted && !boxHasUnsorted) {
                            applyNavigationItemHighlight(boxItem, true);
                            boxHasUnsorted = true;
                        }

                        // Sub-boxes are treated like Boxes for the context menu
                        {
                            ContextMenu subBoxCm = NavigationContextMenuBuilder.forMyCollectionBox(subBoxName);
                            subBoxItem.setOnContextMenuRequested(e -> {
                                subBoxCm.show(subBoxItem, e.getScreenX(), e.getScreenY());
                                e.consume();
                            });
                        }

                        if (subBox.getContent() != null) {
                            for (CardsGroup g : subBox.getContent()) {
                                String rawGName = g.getName() == null ? "" : g.getName();
                                String gName = rawGName.replaceAll("[=\\-]", "");
                                NavigationItem gItem = createNavigationItem(gName, 2);

                                gItem.setOnLabelClicked(evt -> navigateToTree(myCollectionTreeView, subBoxName, gName));

                                boolean gHasUnsorted = groupHasUnsortedCards(g, gName);
                                applyNavigationItemHighlight(gItem, gHasUnsorted);
                                if (gHasUnsorted && !subBoxHasUnsorted) {
                                    applyNavigationItemHighlight(subBoxItem, true);
                                    subBoxHasUnsorted = true;
                                    if (!boxHasUnsorted) {
                                        applyNavigationItemHighlight(boxItem, true);
                                        boxHasUnsorted = true;
                                    }
                                }

                                // Sub-groups are treated like Categories for the context menu
                                {
                                    ContextMenu gCm = NavigationContextMenuBuilder.forMyCollectionCategory(gName);
                                    gItem.setOnContextMenuRequested(e -> {
                                        gCm.show(gItem, e.getScreenX(), e.getScreenY());
                                        e.consume();
                                    });
                                }

                                subBoxItem.addSubItem(gItem);
                            }
                        }
                        boxItem.addSubItem(subBoxItem);
                    }
                }

                navigationMenu.addItem(boxItem);
            }
        } else {
            NavigationItem none = createNavigationItem("No boxes available", 0);
            navigationMenu.addItem(none);
        }

        menuVBox.getChildren().add(navigationMenu);

        // --- NEW: empty-area context menu ---
        // NavigationItems consume their own events (e.consume() above), so this
        // handler only fires when the user right-clicks the blank background.
        ContextMenu emptyCm = NavigationContextMenuBuilder.forMyCollectionEmpty();
        menuVBox.setOnContextMenuRequested(e -> {
            emptyCm.show(menuVBox, e.getScreenX(), e.getScreenY());
            // Do NOT consume – let the event propagate normally in case the
            // scroll-pane also needs to react (e.g., for scroll handling).
        });
    }

    /**
     * Returns true if the given box or any of its groups/sub-boxes contains at least one card
     * that computeCardNeedsSorting(...) reports as needing sorting.
     *
     * @param box            The Box to inspect.
     * @param boxDisplayName The sanitized display name used in the navigation (no = or -).
     * @return true if at least one card in the box (or its groups/subboxes) needs sorting.
     */
    private boolean boxHasUnsortedCards(Box box, String boxDisplayName) {
        if (box == null) return false;

        // 1) Check cards that may be directly present in categories (groups) of this box
        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                // If a group contains unsorted cards, the box is considered to have unsorted cards
                if (groupHasUnsortedCards(group, group.getName() == null ? "" : group.getName().replaceAll("[=\\-]", ""))) {
                    return true;
                }
            }
        }

        // 2) If the Box itself may directly contain cards (some formats may put cards at box-level),
        //    check them by iterating groups with empty names or by checking the group's card list directly.
        //    (This covers the case where a Box may contain cards without a named category.)
        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                if (group.getName() == null || group.getName().trim().isEmpty()) {
                    // treat as box-level cards
                    if (group.getCardList() != null) {
                        for (CardElement ce : group.getCardList()) {
                            if (ce == null || ce.getCard() == null) continue;
                            try {
                                boolean needs = Controller.RealMainController.computeCardNeedsSorting(ce.getCard(), boxDisplayName);
                                if (needs) return true;
                            } catch (Throwable t) {
                                // If compute method fails, be conservative and treat as not needing sorting
                            }
                        }
                    }
                }
            }
        }

        // 3) Check sub-boxes recursively
        if (box.getSubBoxes() != null) {
            for (Box sub : box.getSubBoxes()) {
                String subBoxDisplayName = sub.getName() == null ? "" : sub.getName().replaceAll("[=\\-]", "");
                if (boxHasUnsortedCards(sub, subBoxDisplayName)) return true;
            }
        }

        return false;
    }

    /**
     * Returns true if the given CardsGroup contains at least one card that needs sorting.
     *
     * @param group            The CardsGroup to inspect.
     * @param groupDisplayName The sanitized display name used in the navigation (no = or -).
     * @return true if at least one card in the group needs sorting.
     */
    private boolean groupHasUnsortedCards(CardsGroup group, String groupDisplayName) {
        if (group == null || group.getCardList() == null) return false;

        for (CardElement ce : group.getCardList()) {
            if (ce == null || ce.getCard() == null) continue;
            try {
                boolean needs = Controller.RealMainController.computeCardNeedsSorting(ce.getCard(), groupDisplayName);
                if (needs) return true;
            } catch (Throwable t) {
                // If compute method fails, ignore this card (do not mark as unsorted)
            }
        }
        return false;
    }

    private boolean groupHasUnsortedCards(CardsGroup group) {
        if (group == null) return false;

        List<CardElement> list;
        try {
            list = group.getCardList();
        } catch (Exception e) {
            return false;
        }
        if (list == null || list.isEmpty()) return false;

        String groupName = group.getName() == null ? "" : group.getName();

        for (int i = 0; i < list.size(); i++) {
            CardElement ce = list.get(i);
            if (ce == null) continue;
            Model.CardsLists.Card c = ce.getCard();
            if (c == null) continue;

            // Use the container = group and index = i for caching, pass group name
            if (cardNeedsSorting(c, group, i, groupName)) return true;
        }
        return false;
    }


    // --- Decks & Collections display (left unchanged except for keeping decksTreeView field) ---

    private boolean boxHasUnsortedCards(Box box) {
        if (box == null) return false;

        String boxName = box.getName() == null ? "" : box.getName();

        // 1) Defensive check: the Box itself might directly expose a list of cards.
        try {
            // Try method getCardList()
            Method getCardListMethod = null;
            try {
                getCardListMethod = box.getClass().getMethod("getCardList");
            } catch (NoSuchMethodException ignored) {
                // not present, we'll try field next
            }

            if (getCardListMethod != null) {
                Object maybeList = getCardListMethod.invoke(box);
                if (maybeList instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<?> raw = (List<?>) maybeList;
                    if (containsUnsortedFromRawList(raw, boxName)) return true;
                }
            } else {
                // Try to find a field named "cardList" or "cardsList"
                Field cardListField = null;
                try {
                    cardListField = box.getClass().getDeclaredField("cardList");
                } catch (NoSuchFieldException e1) {
                    try {
                        cardListField = box.getClass().getDeclaredField("cardsList");
                    } catch (NoSuchFieldException ignored) {
                        cardListField = null;
                    }
                }
                if (cardListField != null) {
                    cardListField.setAccessible(true);
                    Object maybeList = cardListField.get(box);
                    if (maybeList instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<?> raw = (List<?>) maybeList;
                        if (containsUnsortedFromRawList(raw, boxName)) return true;
                    }
                }
            }
        } catch (Exception e) {
            // Reflection may fail for many reasons; log at debug and continue with normal checks.
            logger.debug("boxHasUnsortedCards: reflection check failed for box '{}': {}", boxName, e.toString());
        }

        // 2) Check groups inside the box (normal case)
        if (box.getContent() != null) {
            for (CardsGroup g : box.getContent()) {
                if (groupHasUnsortedCards(g)) return true;
            }
        }

        // 3) Recurse into sub-boxes
        if (box.getSubBoxes() != null) {
            for (Box sb : box.getSubBoxes()) {
                if (sb == null) continue;
                if (boxHasUnsortedCards(sb)) return true;
            }
        }

        return false;
    }

    /**
     * Inspect a raw list (List<?>) for unsorted cards.
     *
     * @param raw         the raw list instance (used as container key)
     * @param elementName the name of the element (Box name) that owns this list
     * @return true if any card at any position needs sorting
     */
    private boolean containsUnsortedFromRawList(List<?> raw, String elementName) {
        if (raw == null || raw.isEmpty()) return false;
        // Use the raw list object itself as the container key so positions map to this list instance
        Object containerKey = raw;
        for (int i = 0; i < raw.size(); i++) {
            Object o = raw.get(i);
            if (o == null) continue;
            Model.CardsLists.Card card = null;
            if (o instanceof Model.CardsLists.CardElement) {
                card = ((Model.CardsLists.CardElement) o).getCard();
            } else if (o instanceof Model.CardsLists.Card) {
                card = (Model.CardsLists.Card) o;
            } else {
                // Unknown element type: skip
                continue;
            }
            if (card == null) continue;

            if (cardNeedsSorting(card, containerKey, i, elementName)) return true;
        }
        return false;
    }

    // --- OuicheList helpers (navigation wiring preserved) ---

    private void displayOuicheListUnified() throws Exception {
        AnchorPane contentPane = ouicheListTab.getContentPane();
        contentPane.getChildren().clear();

        if (Model.CardsLists.OuicheList.getDetailedOuicheList() == null) {
            UserInterfaceFunctions.generateOuicheList();
        }
        DecksAndCollectionsList ouicheDetailed = Model.CardsLists.OuicheList.getDetailedOuicheList();
        if (ouicheDetailed == null) {
            throw new Exception("Failed to generate detailed OuicheList.");
        }

        DataTreeItem<Object> rootItem = new DataTreeItem<>("OuicheList", "ROOT");
        rootItem.setExpanded(true);

        if (ouicheDetailed.getCollections() != null) {
            for (ThemeCollection collection : ouicheDetailed.getCollections()) {
                DataTreeItem<Object> collItem = createThemeCollectionTreeItem(collection, TabType.OUICHE_LIST);
                rootItem.getChildren().add(collItem);
            }
        }

        if (ouicheDetailed.getDecks() != null) {
            for (Deck deck : ouicheDetailed.getDecks()) {
                DataTreeItem<Object> deckItem = createDeckTreeItem(deck);
                rootItem.getChildren().add(deckItem);
            }
        }

        ouicheTreeView = new TreeView<>(rootItem);
        ouicheTreeView.setCellFactory(param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        ouicheTreeView.setStyle("-fx-background-color: #100317;");
        ouicheTreeView.setShowRoot(false);

        contentPane.getChildren().add(ouicheTreeView);
        AnchorPane.setTopAnchor(ouicheTreeView, 0.0);
        AnchorPane.setBottomAnchor(ouicheTreeView, 0.0);
        AnchorPane.setLeftAnchor(ouicheTreeView, 0.0);
        AnchorPane.setRightAnchor(ouicheTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        ouicheTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());
    }

    private void populateOuicheListMenu() throws Exception {
        VBox menuVBox = ouicheListTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        DecksAndCollectionsList ouicheDetailed = Model.CardsLists.OuicheList.getDetailedOuicheList();
        if (ouicheDetailed == null) {
            UserInterfaceFunctions.generateOuicheList();
            ouicheDetailed = Model.CardsLists.OuicheList.getDetailedOuicheList();
            if (ouicheDetailed == null) {
                Label errorLabel = new Label("No OuicheList available.");
                errorLabel.setStyle("-fx-text-fill: white;");
                navigationMenu.addItem(new NavigationItem(errorLabel.getText(), 0));
                menuVBox.getChildren().add(navigationMenu);
                return;
            }
        }

        if (ouicheDetailed.getCollections() != null) {
            for (ThemeCollection collection : ouicheDetailed.getCollections()) {
                NavigationItem collectionNavItem = createNavigationItem(collection.getName(), 0);

                // navigation wiring: click navigates to the collection node in ouicheTreeView
                collectionNavItem.setOnLabelClicked(evt -> navigateToTree(ouicheTreeView, collection.getName()));

                collectionNavItem.setExpanded(false);
                navigationMenu.addItem(collectionNavItem);

                if (collection.getLinkedDecks() != null) {
                    for (List<Deck> unit : collection.getLinkedDecks()) {
                        if (unit == null) continue;
                        for (Deck linkedDeck : unit) {
                            if (linkedDeck == null) continue;
                            NavigationItem deckSubItem = createNavigationItem(linkedDeck.getName(), 1);
                            // navigate to collection -> Decks -> deckName
                            deckSubItem.setOnLabelClicked(evt -> navigateToTree(ouicheTreeView, collection.getName(), "Decks", linkedDeck.getName()));
                            collectionNavItem.addSubItem(deckSubItem);
                        }
                    }
                }
            }
        }

        if (ouicheDetailed.getDecks() != null) {
            for (Deck deck : ouicheDetailed.getDecks()) {
                NavigationItem navItem = createNavigationItem(deck.getName(), 0);
                navItem.setOnLabelClicked(evt -> navigateToTree(ouicheTreeView, deck.getName()));
                navigationMenu.addItem(navItem);
            }
        }

        menuVBox.getChildren().add(navigationMenu);
    }

    // --- Archetypes tab: display and menu population (unchanged) ---

    private void displayArchetypes() throws Exception {
        AnchorPane contentPane = archetypesTab.getContentPane();
        contentPane.getChildren().clear();

        DataTreeItem<Object> rootItem = new DataTreeItem<>("Archetypes", "ROOT");
        rootItem.setExpanded(true);

        List<String> globalNames = SubListCreator.archetypesList;
        List<List<Card>> globalLists = SubListCreator.archetypesCardsLists;

        if (globalNames != null && globalLists != null && globalNames.size() == globalLists.size()) {
            for (int i = 0; i < globalNames.size(); i++) {
                String archetypeName = globalNames.get(i);
                if (archetypeName == null) continue;
                List<Card> cardsForArchetype = globalLists.get(i);
                List<CardElement> elements = new ArrayList<>();
                if (cardsForArchetype != null) {
                    for (Card c : cardsForArchetype) {
                        if (c != null) elements.add(new CardElement(c));
                    }
                }
                CardsGroup archetypeGroup = new CardsGroup(ARCHETYPE_MARKER + archetypeName, elements);
                Map<String, Object> data = new HashMap<>();
                data.put("group", archetypeGroup);
                data.put("missing", Collections.emptySet());
                DataTreeItem<Object> archetypeNode = new DataTreeItem<>(archetypeName, data);
                archetypeNode.setExpanded(false);
                rootItem.getChildren().add(archetypeNode);
            }
        } else {
            DataTreeItem<Object> placeholder = new DataTreeItem<>("No archetypes available", "NO_ARCHETYPES");
            placeholder.setExpanded(false);
            rootItem.getChildren().add(placeholder);
        }

        archetypesTreeView = new TreeView<>(rootItem);
        archetypesTreeView.setCellFactory(param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        archetypesTreeView.setStyle("-fx-background-color: #100317;");
        archetypesTreeView.setShowRoot(false);

        contentPane.getChildren().add(archetypesTreeView);
        AnchorPane.setTopAnchor(archetypesTreeView, 0.0);
        AnchorPane.setBottomAnchor(archetypesTreeView, 0.0);
        AnchorPane.setLeftAnchor(archetypesTreeView, 0.0);
        AnchorPane.setRightAnchor(archetypesTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        archetypesTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());

        logger.info("Archetypes displayed with {} archetype(s).", rootItem.getChildren().size());
    }

    private void populateArchetypesMenu() throws Exception {
        VBox menuVBox = archetypesTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        List<String> globalNames = SubListCreator.archetypesList;
        if (globalNames != null && !globalNames.isEmpty()) {
            for (String archetypeName : globalNames) {
                if (archetypeName == null) continue;
                NavigationItem item = createNavigationItem(archetypeName, 0);
                item.setOnLabelClicked(evt -> navigateToTree(archetypesTreeView, archetypeName));
                navigationMenu.addItem(item);
            }
        } else {
            NavigationItem none = createNavigationItem("No archetypes available", 0);
            navigationMenu.addItem(none);
        }

        menuVBox.getChildren().add(navigationMenu);
    }

    // --- Tree item builders and helpers (unchanged) ---

    private DataTreeItem<Object> createBoxTreeItem(Box box) {
        String cleanName = box.getName() == null ? "" : box.getName().replaceAll("[=\\-]", "");
        DataTreeItem<Object> boxItem = new DataTreeItem<>(cleanName, box);
        boxItem.setExpanded(true);
        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                DataTreeItem<Object> groupItem = createGroupTreeItem(group);
                boxItem.getChildren().add(groupItem);
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box subBox : box.getSubBoxes()) {
                DataTreeItem<Object> subBoxItem = createBoxTreeItem(subBox);
                boxItem.getChildren().add(subBoxItem);
            }
        }
        return boxItem;
    }

    private DataTreeItem<Object> createGroupTreeItem(CardsGroup group) {
        String displayName = group.getName() == null ? "" : group.getName().replaceAll("[=\\-]", "");
        DataTreeItem<Object> groupItem = new DataTreeItem<>(displayName, group);
        groupItem.setExpanded(true);
        return groupItem;
    }

    private DataTreeItem<Object> createDeckTreeItem(Deck deck) {
        String cleanName = deck.getName() == null ? "" : deck.getName().replaceAll("[=\\-]", "");
        DataTreeItem<Object> deckItem = new DataTreeItem<>(cleanName, deck);
        deckItem.setExpanded(true);

        if (deck.getMainDeck() != null && !deck.getMainDeck().isEmpty()) {
            CardsGroup mainGroup = new CardsGroup("Main Deck", deck.getMainDeck());
            DataTreeItem<Object> mainDeckItem = new DataTreeItem<>("Main Deck", mainGroup);
            mainDeckItem.setExpanded(true);
            deckItem.getChildren().add(mainDeckItem);
        }

        if (deck.getExtraDeck() != null && !deck.getExtraDeck().isEmpty()) {
            CardsGroup extraGroup = new CardsGroup("Extra Deck", deck.getExtraDeck());
            DataTreeItem<Object> extraDeckItem = new DataTreeItem<>("Extra Deck", extraGroup);
            extraDeckItem.setExpanded(true);
            deckItem.getChildren().add(extraDeckItem);
        }

        if (deck.getSideDeck() != null && !deck.getSideDeck().isEmpty()) {
            CardsGroup sideGroup = new CardsGroup("Side Deck", deck.getSideDeck());
            DataTreeItem<Object> sideDeckItem = new DataTreeItem<>("Side Deck", sideGroup);
            sideDeckItem.setExpanded(true);
            deckItem.getChildren().add(sideDeckItem);
        }

        return deckItem;
    }

    //TODO WTF, there is logic from deck import ! The deck should already be imported, just use the decks that are in the Collection !
    private DataTreeItem<Object> createThemeCollectionTreeItem(ThemeCollection collection, TabType tabType) {
        String cleanName = collection.getName() == null ? "" : collection.getName().replaceAll("[=\\-]", "");
        DataTreeItem<Object> collectionItem = new DataTreeItem<>(cleanName, collection);
        collectionItem.setExpanded(true);

        // --- Cards parsing with marker handling and linked-deck name capture ---
        List<CardElement> cleaned = new ArrayList<>();
        List<String> linkedDeckNames = new ArrayList<>();
        boolean inLinkedDecksMarker = false;

        if (collection.getCardsList() != null && !collection.getCardsList().isEmpty()) {
            for (CardElement ce : collection.getCardsList()) {
                if (ce == null) continue;

                String text = null;
                try {
                    text = ce.toString();
                    if (text != null) text = text.trim();
                } catch (Exception ignored) {
                }

                // Detect marker lines that start a linked-decks block
                if (text != null && (text.startsWith("#") || text.equalsIgnoreCase("Linked decks") || text.equalsIgnoreCase("#Linked decks"))) {
                    inLinkedDecksMarker = true;
                    continue;
                }

                if (inLinkedDecksMarker) {
                    if (text != null && !text.isEmpty()) {
                        boolean looksLikeRealCard = false;
                        try {
                            Card maybeCard = ce.getCard();
                            if (maybeCard != null) {
                                if ((maybeCard.getKonamiId() != null && !maybeCard.getKonamiId().trim().isEmpty())
                                        || (maybeCard.getPassCode() != null && !maybeCard.getPassCode().trim().isEmpty())
                                        || (maybeCard.getName_EN() != null && !maybeCard.getName_EN().trim().isEmpty())) {
                                    looksLikeRealCard = true;
                                }
                            }
                        } catch (Exception ignored) {
                        }

                        if (!looksLikeRealCard) {
                            linkedDeckNames.add(text);
                            continue;
                        }
                    }
                    // if it looks like a real card, fall through and treat as card
                }

                if (!isMarkerElement(ce)) {
                    cleaned.add(ce);
                }
            }

            if (!cleaned.isEmpty()) {
                CardsGroup group = new CardsGroup("Cards", cleaned);
                DataTreeItem<Object> groupItem = new DataTreeItem<>("Cards", group);
                groupItem.setExpanded(true);
                collectionItem.getChildren().add(groupItem);
            }
        }

        // --- Decks section: prefer explicit linkedDecks; otherwise use captured linkedDeckNames ---
        DataTreeItem<Object> decksParent = new DataTreeItem<>("Decks", "DECKS_SECTION");
        decksParent.setExpanded(true);
        boolean decksParentHasChildren = false;

        if (collection.getLinkedDecks() != null && !collection.getLinkedDecks().isEmpty()) {
            int unitIndex = 1;
            for (List<Deck> unit : collection.getLinkedDecks()) {
                if (unit == null || unit.isEmpty()) {
                    DataTreeItem<Object> emptyUnit = new DataTreeItem<>("Group " + unitIndex, "DECK_GROUP");
                    emptyUnit.setExpanded(false);
                    decksParent.getChildren().add(emptyUnit);
                    unitIndex++;
                    continue;
                }

                if (unit.size() > 1) {
                    DataTreeItem<Object> unitNode = new DataTreeItem<>("Group " + unitIndex, "DECK_GROUP");
                    unitNode.setExpanded(false);
                    for (Deck d : unit) {
                        DataTreeItem<Object> deckItem = createDeckTreeItem(d);
                        unitNode.getChildren().add(deckItem);
                    }
                    decksParent.getChildren().add(unitNode);
                } else {
                    Deck single = unit.get(0);
                    DataTreeItem<Object> deckItem = createDeckTreeItem(single);
                    decksParent.getChildren().add(deckItem);
                }
                unitIndex++;
            }
            decksParentHasChildren = !decksParent.getChildren().isEmpty();
        }

        if ((collection.getLinkedDecks() == null || collection.getLinkedDecks().isEmpty()) && !linkedDeckNames.isEmpty()) {
            for (String deckName : linkedDeckNames) {
                if (deckName == null || deckName.trim().isEmpty()) continue;
                DataTreeItem<Object> deckItem = new DataTreeItem<>(deckName, deckName);
                deckItem.setExpanded(true);
                decksParent.getChildren().add(deckItem);
            }
            decksParentHasChildren = !decksParent.getChildren().isEmpty();
        }

        if (decksParentHasChildren) {
            collectionItem.getChildren().add(decksParent);
        }

        if (tabType != TabType.OUICHE_LIST) {
            // --- Archetypes parent (expanded by default) ---
            DataTreeItem<Object> archetypesParent = new DataTreeItem<>("Archetypes", "ARCHETYPES_SECTION");
            archetypesParent.setExpanded(true);
            boolean archetypesAdded = false;
            boolean hasArchetypesMethod = false;

            try {
                Method m = collection.getClass().getMethod("getArchetypes");
                hasArchetypesMethod = true;
                Object res = m.invoke(collection);
                if (res instanceof List) {
                    List<?> archetypes = (List<?>) res;
                    logger.debug("Collection '{}' getArchetypes() returned list size={}", collection.getName(), archetypes.size());

                    for (Object archetypeObj : archetypes) {
                        if (archetypeObj == null) continue;
                        if (archetypeObj instanceof String) {
                            String archetypeName = ((String) archetypeObj).trim();
                            if (archetypeName.isEmpty()) continue;
                            List<CardElement> elements = buildElementsFromGlobalArchetype(archetypeName);
                            Set<String> missing = computeMissingIdsForElements(collection, elements);
                            CardsGroup archetypeGroup = new CardsGroup(ARCHETYPE_MARKER + archetypeName, elements);
                            Map<String, Object> data = new HashMap<>();
                            data.put("group", archetypeGroup);
                            data.put("missing", missing);
                            DataTreeItem<Object> archetypeNode = new DataTreeItem<>(archetypeName, data);
                            archetypeNode.setExpanded(false);
                            archetypesParent.getChildren().add(archetypeNode);
                            archetypesAdded = true;
                        } else {
                            String name = null;
                            List<CardElement> elements = new ArrayList<>();
                            try {
                                Method nameMethod = archetypeObj.getClass().getMethod("getName");
                                Object nameVal = nameMethod.invoke(archetypeObj);
                                if (nameVal != null) name = nameVal.toString();
                            } catch (Exception ignored) {
                            }
                            try {
                                Method cardsMethod = archetypeObj.getClass().getMethod("getCards");
                                Object cardsVal = cardsMethod.invoke(archetypeObj);
                                if (cardsVal instanceof List) {
                                    for (Object o : (List<?>) cardsVal) {
                                        if (o instanceof CardElement) elements.add((CardElement) o);
                                        else if (o instanceof Card) elements.add(new CardElement((Card) o));
                                        else if (o instanceof String) {
                                            try {
                                                elements.add(new CardElement((String) o));
                                            } catch (Exception ignored) {
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }

                            if ((elements == null || elements.isEmpty()) && name != null) {
                                elements = buildElementsFromGlobalArchetype(name);
                            }

                            if (name == null) name = "Archetype";
                            Set<String> missing = computeMissingIdsForElements(collection, elements);
                            CardsGroup archetypeGroup = new CardsGroup(ARCHETYPE_MARKER + name, elements);
                            Map<String, Object> data = new HashMap<>();
                            data.put("group", archetypeGroup);
                            data.put("missing", missing);
                            DataTreeItem<Object> archetypeNode = new DataTreeItem<>(name, data);
                            archetypeNode.setExpanded(false);
                            archetypesParent.getChildren().add(archetypeNode);
                            archetypesAdded = true;
                        }
                    }
                }
            } catch (NoSuchMethodException ignored) {
                hasArchetypesMethod = false;
            } catch (Exception e) {
                logger.debug("Archetypes reflection failed for collection " + collection.getName(), e);
            }

            // Only fallback to global SubListCreator archetypes when the collection does NOT provide getArchetypes()
            if (!hasArchetypesMethod && !archetypesAdded) {
                try {
                    List<String> globalNames = SubListCreator.archetypesList;
                    List<List<Card>> globalLists = SubListCreator.archetypesCardsLists;
                    if (globalNames != null && globalLists != null && globalNames.size() == globalLists.size()) {
                        for (int i = 0; i < globalNames.size(); i++) {
                            String archetypeName = globalNames.get(i);
                            if (archetypeName == null) continue;
                            List<Card> cardsForArchetype = globalLists.get(i);
                            List<CardElement> elements = new ArrayList<>();
                            if (cardsForArchetype != null) {
                                for (Card c : cardsForArchetype) {
                                    if (c != null) elements.add(new CardElement(c));
                                }
                            }
                            Set<String> missing = computeMissingIdsForElements(collection, elements);
                            CardsGroup archetypeGroup = new CardsGroup(ARCHETYPE_MARKER + archetypeName, elements);
                            Map<String, Object> data = new HashMap<>();
                            data.put("group", archetypeGroup);
                            data.put("missing", missing);
                            DataTreeItem<Object> archetypeNode = new DataTreeItem<>(archetypeName, data);
                            archetypeNode.setExpanded(false);
                            archetypesParent.getChildren().add(archetypeNode);
                            archetypesAdded = true;
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Fallback archetypes population failed", e);
                }
            }

            if (archetypesAdded) {
                logger.info("Collection '{}' -> added {} archetype group(s)", collection.getName(), archetypesParent.getChildren().size());
                collectionItem.getChildren().add(archetypesParent);
            }

            // 4) Exceptions / Cards not to add
            try {
                Method exceptionsMethod = collection.getClass().getMethod("getExceptionsToNotAdd");
                Object exceptionsVal = exceptionsMethod.invoke(collection);
                if (exceptionsVal instanceof List) {
                    List<?> exceptionsList = (List<?>) exceptionsVal;
                    List<CardElement> exceptionElements = new ArrayList<>();
                    for (Object o : exceptionsList) {
                        if (o instanceof CardElement) exceptionElements.add((CardElement) o);
                        else if (o instanceof Card) exceptionElements.add(new CardElement((Card) o));
                        else if (o instanceof String) {
                            try {
                                exceptionElements.add(new CardElement((String) o));
                            } catch (Exception ignored) {
                            }
                        }
                    }
                    if (!exceptionElements.isEmpty()) {
                        CardsGroup exceptionsGroup = new CardsGroup("Cards not to add", exceptionElements);
                        DataTreeItem<Object> exceptionsNode = new DataTreeItem<>("Cards not to add", exceptionsGroup);
                        exceptionsNode.setExpanded(true);
                        collectionItem.getChildren().add(exceptionsNode);
                    }
                }
            } catch (NoSuchMethodException ignored) {
            } catch (Exception e) {
                logger.debug("Failed to read exceptionsToNotAdd for collection " + collection.getName(), e);
            }
        }

        return collectionItem;
    }

    private void displayDecksAndCollections() throws Exception {
        AnchorPane contentPane = decksTab.getContentPane();
        contentPane.getChildren().clear();

        if (UserInterfaceFunctions.getDecksList() == null) {
            UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
        }
        DecksAndCollectionsList decksCollection = UserInterfaceFunctions.getDecksList();
        if (decksCollection == null) {
            throw new Exception("DecksAndCollectionsList is null. Please check the decks folder path.");
        }

        DataTreeItem<Object> rootItem = new DataTreeItem<>("Decks and Collections", "ROOT");
        rootItem.setExpanded(true);

        if (decksCollection.getCollections() != null) {
            for (ThemeCollection collection : decksCollection.getCollections()) {
                DataTreeItem<Object> collItem = createThemeCollectionTreeItem(collection, TabType.DECKS);
                rootItem.getChildren().add(collItem);
            }
        }

        if (decksCollection.getDecks() != null) {
            for (Deck deck : decksCollection.getDecks()) {
                DataTreeItem<Object> deckItem = createDeckTreeItem(deck);
                rootItem.getChildren().add(deckItem);
            }
        }

        decksAndCollectionsTreeView = new TreeView<>(rootItem);
        decksAndCollectionsTreeView.setUserData("DECKS_COLLECTIONS");
        decksAndCollectionsTreeView.setCellFactory(param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        decksAndCollectionsTreeView.setStyle("-fx-background-color: #100317;");
        decksAndCollectionsTreeView.setShowRoot(false);

        contentPane.getChildren().add(decksAndCollectionsTreeView);
        AnchorPane.setTopAnchor(decksAndCollectionsTreeView, 0.0);
        AnchorPane.setBottomAnchor(decksAndCollectionsTreeView, 0.0);
        AnchorPane.setLeftAnchor(decksAndCollectionsTreeView, 0.0);
        AnchorPane.setRightAnchor(decksAndCollectionsTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        decksAndCollectionsTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());

        logger.info("Decks and Collections displayed using the unified layout.");
    }

    private List<CardElement> buildElementsFromGlobalArchetype(String archetypeName) {
        List<CardElement> elements = new ArrayList<>();
        if (archetypeName == null || archetypeName.trim().isEmpty()) return elements;
        try {
            List<String> globalNames = SubListCreator.archetypesList;
            List<List<Card>> globalLists = SubListCreator.archetypesCardsLists;
            if (globalNames == null || globalLists == null) return elements;
            for (int i = 0; i < globalNames.size(); i++) {
                String globalName = globalNames.get(i);
                if (globalName == null) continue;
                if (globalName.equalsIgnoreCase(archetypeName.trim())) {
                    List<Card> cardsForArchetype = globalLists.size() > i ? globalLists.get(i) : null;
                    if (cardsForArchetype != null) {
                        for (Card c : cardsForArchetype) {
                            if (c != null) elements.add(new CardElement(c));
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            logger.debug("buildElementsFromGlobalArchetype failed for {}: {}", archetypeName, e.getMessage());
        }
        return elements;
    }

    private void populateDecksAndCollectionsMenu() throws Exception {
        VBox menuVBox = decksTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        if (UserInterfaceFunctions.getDecksList() == null) {
            UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
        }
        DecksAndCollectionsList decksCollection = UserInterfaceFunctions.getDecksList();

        if (decksCollection != null) {
            if (decksCollection.getCollections() != null) {
                for (ThemeCollection collection : decksCollection.getCollections()) {
                    NavigationItem collectionNavItem = createNavigationItem(collection.getName(), 0);

                    // highlight (unchanged)
                    boolean hasMissing = collectionHasMissing(collection);
                    applyNavigationItemHighlight(collectionNavItem, hasMissing);

                    // navigation wiring (unchanged)
                    collectionNavItem.setOnLabelClicked(evt -> navigateToTree(decksAndCollectionsTreeView, collection.getName()));

                    collectionNavItem.setExpanded(false);

                    // --- NEW: context menu for Collection items ---
                    {
                        ContextMenu collCm = NavigationContextMenuBuilder.forDecksCollection(collection.getName());
                        collectionNavItem.setOnContextMenuRequested(e -> {
                            collCm.show(collectionNavItem, e.getScreenX(), e.getScreenY());
                            e.consume();
                        });
                    }

                    navigationMenu.addItem(collectionNavItem);

                    if (collection.getLinkedDecks() != null) {
                        for (List<Deck> unit : collection.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Deck linkedDeck : unit) {
                                if (linkedDeck == null) continue;
                                NavigationItem deckSubItem = createNavigationItem(linkedDeck.getName(), 1);

                                // navigation wiring (unchanged)
                                deckSubItem.setOnLabelClicked(evt -> navigateToTree(decksAndCollectionsTreeView, collection.getName(), "Decks", linkedDeck.getName()));

                                // --- NEW: context menu for Deck items (inside a Collection) ---
                                {
                                    ContextMenu deckCm = NavigationContextMenuBuilder.forDecksDeck(linkedDeck.getName());
                                    deckSubItem.setOnContextMenuRequested(e -> {
                                        deckCm.show(deckSubItem, e.getScreenX(), e.getScreenY());
                                        e.consume();
                                    });
                                }

                                collectionNavItem.addSubItem(deckSubItem);
                            }
                        }
                    }
                }
            }

            if (decksCollection.getDecks() != null) {
                for (Deck deck : decksCollection.getDecks()) {
                    NavigationItem navItem = createNavigationItem(deck.getName(), 0);

                    // navigation wiring (unchanged)
                    navItem.setOnLabelClicked(evt -> navigateToTree(decksAndCollectionsTreeView, deck.getName()));

                    // --- NEW: context menu for standalone Deck items ---
                    {
                        ContextMenu deckCm = NavigationContextMenuBuilder.forDecksDeck(deck.getName());
                        navItem.setOnContextMenuRequested(e -> {
                            deckCm.show(navItem, e.getScreenX(), e.getScreenY());
                            e.consume();
                        });
                    }

                    navigationMenu.addItem(navItem);
                }
            }
        } else {
            Label errorLabel = new Label("No Decks and Collections loaded.");
            errorLabel.setStyle("-fx-text-fill: white;");
            navigationMenu.addItem(new NavigationItem(errorLabel.getText(), 0));
        }

        menuVBox.getChildren().add(navigationMenu);

        // --- NEW: empty-area context menu ---
        ContextMenu emptyCm = NavigationContextMenuBuilder.forDecksEmpty();
        menuVBox.setOnContextMenuRequested(e -> {
            emptyCm.show(menuVBox, e.getScreenX(), e.getScreenY());
        });
    }

    private Set<String> computeMissingIdsForElements(ThemeCollection collection, List<CardElement> elements) {
        Set<String> missing = new HashSet<>();
        if (elements == null || elements.isEmpty()) return missing;

        for (CardElement ce : elements) {
            if (ce == null) continue;
            Card c = ce.getCard();
            if (c == null) continue;

            String konamiId = c.getKonamiId();
            String passCode = c.getPassCode();

            boolean found = false;
            if (konamiId != null && !konamiId.isEmpty()) found = isKonamiIdPresentInCollection(collection, konamiId);
            if (!found && passCode != null && !passCode.isEmpty())
                found = isPassCodePresentInCollection(collection, passCode);

            if (!found) {
                if (konamiId != null && !konamiId.isEmpty()) missing.add(konamiId);
                if (passCode != null && !passCode.isEmpty()) missing.add(passCode);
                logger.debug("Marking as missing for collection '{}': konamiId='{}' passCode='{}'", collection.getName(), konamiId, passCode);
            } else {
                logger.debug("Not marking for collection '{}': konamiId='{}' passCode='{}' (found)", collection.getName(), konamiId, passCode);
            }
        }
        return missing;
    }

    private boolean isKonamiIdPresentInCollection(ThemeCollection collection, String konamiId) {
        if (konamiId == null || konamiId.isEmpty()) return false;
        try {
            List<CardElement> cardsList = collection.getCardsList();
            if (cardsList != null) {
                for (CardElement ce : cardsList) {
                    if (ce == null) continue;
                    Card cc = ce.getCard();
                    if (cc != null && konamiId.equals(cc.getKonamiId())) return true;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Method exceptionsMethod = collection.getClass().getMethod("getExceptionsToNotAdd");
            Object exceptionsVal = exceptionsMethod.invoke(collection);
            if (exceptionsVal instanceof List) {
                for (Object o : (List<?>) exceptionsVal) {
                    if (o instanceof CardElement) {
                        Card cc = ((CardElement) o).getCard();
                        if (cc != null && konamiId.equals(cc.getKonamiId())) return true;
                    } else if (o instanceof Card) {
                        Card cc = (Card) o;
                        if (cc != null && konamiId.equals(cc.getKonamiId())) return true;
                    }
                }
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {
        }

        try {
            List<List<Deck>> linked = collection.getLinkedDecks();
            if (linked != null) {
                for (List<Deck> unit : linked) {
                    if (unit == null) continue;
                    for (Deck d : unit) {
                        if (d == null) continue;
                        if (deckContainsKonamiId(d, konamiId)) return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isPassCodePresentInCollection(ThemeCollection collection, String passCode) {
        if (passCode == null || passCode.isEmpty()) return false;
        try {
            List<CardElement> cardsList = collection.getCardsList();
            if (cardsList != null) {
                for (CardElement ce : cardsList) {
                    if (ce == null) continue;
                    Card cc = ce.getCard();
                    if (cc != null && passCode.equals(cc.getPassCode())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            Method exceptionsMethod = collection.getClass().getMethod("getExceptionsToNotAdd");
            Object exceptionsVal = exceptionsMethod.invoke(collection);
            if (exceptionsVal instanceof List) {
                for (Object o : (List<?>) exceptionsVal) {
                    if (o instanceof CardElement) {
                        Card cc = ((CardElement) o).getCard();
                        if (cc != null && passCode.equals(cc.getPassCode())) return true;
                    } else if (o instanceof Card) {
                        Card cc = (Card) o;
                        if (cc != null && passCode.equals(cc.getPassCode())) return true;
                    } else if (o instanceof String) {
                        String s = ((String) o).trim();
                        if (!s.isEmpty() && s.equals(passCode)) return true;
                    }
                }
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {
        }
        try {
            List<List<Deck>> linked = collection.getLinkedDecks();
            if (linked != null) {
                for (List<Deck> unit : linked) {
                    if (unit == null) continue;
                    for (Deck d : unit) {
                        if (d == null) continue;
                        if (deckContainsPassCode(d, passCode)) return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private NavigationItem createNavigationItem(String name, int depth) {
        if (name == null) name = "";
        String clean = name.replaceAll("[=\\-]", "");
        NavigationItem item = new NavigationItem(clean, depth);
        item.setOnLabelClicked(event -> {
        });
        return item;
    }

    /**
     * Determine whether a ThemeCollection has at least one missing card in its archetypes.
     * <p>
     * IMPORTANT: returns true only when the collection provides a non-empty getArchetypes() list
     * and at least one archetype yields missing cards for that collection.
     * <p>
     * Collections that do not expose getArchetypes(), or that expose it but it is empty/null,
     * will return false (no highlight).
     */
    private boolean collectionHasMissing(ThemeCollection collection) {
        if (collection == null) return false;

        boolean hasArchetypesMethod = false;
        try {
            Method m = collection.getClass().getMethod("getArchetypes");
            hasArchetypesMethod = true;
            Object res = m.invoke(collection);
            if (!(res instanceof List)) {
                // method exists but doesn't return a list -> treat as no archetypes
                return false;
            }
            List<?> archetypes = (List<?>) res;
            if (archetypes == null || archetypes.isEmpty()) {
                // collection explicitly has no archetypes -> cannot have missing archetype cards
                return false;
            }

            // For each archetype provided by the collection, compute missing IDs and return true if any missing
            for (Object archetypeObj : archetypes) {
                if (archetypeObj == null) continue;
                List<CardElement> elements = new ArrayList<>();
                if (archetypeObj instanceof String) {
                    String archetypeName = ((String) archetypeObj).trim();
                    if (archetypeName.isEmpty()) continue;
                    elements = buildElementsFromGlobalArchetype(archetypeName);
                } else {
                    String name = null;
                    try {
                        Method nameMethod = archetypeObj.getClass().getMethod("getName");
                        Object nameVal = nameMethod.invoke(archetypeObj);
                        if (nameVal != null) name = nameVal.toString();
                    } catch (Exception ignored) {
                    }
                    try {
                        Method cardsMethod = archetypeObj.getClass().getMethod("getCards");
                        Object cardsVal = cardsMethod.invoke(archetypeObj);
                        if (cardsVal instanceof List) {
                            for (Object o : (List<?>) cardsVal) {
                                if (o instanceof CardElement) elements.add((CardElement) o);
                                else if (o instanceof Card) elements.add(new CardElement((Card) o));
                                else if (o instanceof String) {
                                    try {
                                        elements.add(new CardElement((String) o));
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    if ((elements == null || elements.isEmpty()) && name != null) {
                        elements = buildElementsFromGlobalArchetype(name);
                    }
                }
                Set<String> missing = computeMissingIdsForElements(collection, elements);
                if (!missing.isEmpty()) return true;
            }
        } catch (NoSuchMethodException ignored) {
            // collection does not provide getArchetypes -> cannot have missing archetype cards
            return false;
        } catch (Exception e) {
            logger.debug("collectionHasMissing: reflection failed for collection " + (collection == null ? "null" : collection.getName()), e);
            return false;
        }

        return false;
    }

    private boolean deckContainsKonamiId(Deck deck, String konamiId) {
        if (deck == null || konamiId == null) return false;
        try {
            if (deck.getMainDeck() != null) {
                for (CardElement ce : deck.getMainDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && konamiId.equals(c.getKonamiId())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (deck.getExtraDeck() != null) {
                for (CardElement ce : deck.getExtraDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && konamiId.equals(c.getKonamiId())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (deck.getSideDeck() != null) {
                for (CardElement ce : deck.getSideDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && konamiId.equals(c.getKonamiId())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isMarkerElement(CardElement ce) {
        if (ce == null) return true;

        try {
            Card c = ce.getCard();
            if (c != null) {
                if ((c.getKonamiId() != null && !c.getKonamiId().trim().isEmpty())
                        || (c.getPassCode() != null && !c.getPassCode().trim().isEmpty())
                        || (c.getName_EN() != null && !c.getName_EN().trim().isEmpty())
                        || (c.getName_FR() != null && !c.getName_FR().trim().isEmpty())
                        || (c.getName_JA() != null && !c.getName_JA().trim().isEmpty())) {
                    return false;
                }
            }
        } catch (Exception ignored) {
        }

        String s = ce.toString();
        if (s == null) return true;
        s = s.trim();
        if (s.isEmpty()) return true;
        if (s.startsWith("#")) return true;
        if (s.equalsIgnoreCase("Linked decks")) return true;
        if (s.equalsIgnoreCase("#Linked decks")) return true;
        if (s.equalsIgnoreCase("Linked decks:")) return true;
        return false;
    }

    /**
     * Navigate to a node in the given TreeView by matching a path of node text values.
     * Example: navigateToTree(decksTreeView, "CollectionName", "Decks", "Zombie")
     */
    private void navigateToTree(TreeView<String> treeView, String... path) {
        if (treeView == null || path == null || path.length == 0) return;
        TreeItem<String> root = treeView.getRoot();
        if (root == null) return;

        TreeItem<String> found = findTreeItemByPath(root, path, 0);
        if (found != null) {
            // expand parents so the node is visible
            TreeItem<String> parent = found.getParent();
            while (parent != null) {
                parent.setExpanded(true);
                parent = parent.getParent();
            }

            // select and scroll to the row
            final TreeItem<String> toSelect = found;
            Platform.runLater(() -> {
                treeView.getSelectionModel().select(toSelect);
                int row = treeView.getRow(toSelect);
                if (row >= 0) treeView.scrollTo(row);
            });
        }
    }

    private TreeItem<String> findTreeItemByPath(TreeItem<String> node, String[] path, int index) {
        if (node == null || path == null || index >= path.length) return null;
        String nodeValue = node.getValue();
        if (nodeValue == null) nodeValue = "";

        if (!nodeValue.equals(path[index])) {
            for (TreeItem<String> child : node.getChildren()) {
                TreeItem<String> res = findTreeItemByPath(child, path, index);
                if (res != null) return res;
            }
            return null;
        }

        if (index == path.length - 1) {
            return node;
        }

        for (TreeItem<String> child : node.getChildren()) {
            TreeItem<String> res = findTreeItemByPath(child, path, index + 1);
            if (res != null) return res;
        }
        return null;
    }

    private String normalizeName(String s) {
        if (s == null) return "";
        String trimmed = s.trim().toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        normalized = normalized.replaceAll("[^\\p{Alnum}\\s]", " ").replaceAll("\\s+", " ").trim();
        return normalized;
    }

    private boolean deckContainsPassCode(Deck deck, String passCode) {
        if (deck == null || passCode == null) return false;
        try {
            if (deck.getMainDeck() != null) {
                for (CardElement ce : deck.getMainDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && passCode.equals(c.getPassCode())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (deck.getExtraDeck() != null) {
                for (CardElement ce : deck.getExtraDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && passCode.equals(c.getPassCode())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (deck.getSideDeck() != null) {
                for (CardElement ce : deck.getSideDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && passCode.equals(c.getPassCode())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void applyNavigationItemHighlight(NavigationItem navItem, boolean highlight) {
        if (navItem == null) return;

        String highlightCss = "-fx-font-weight: bold; -fx-text-fill: #cdfc04;";
        String defaultCss = "-fx-font-weight: bold; -fx-text-fill: white;";

        try {
            Method getLabelMethod = null;
            try {
                getLabelMethod = navItem.getClass().getMethod("getLabel");
            } catch (NoSuchMethodException ignored) {
            }
            if (getLabelMethod != null) {
                Object labelObj = getLabelMethod.invoke(navItem);
                if (labelObj instanceof javafx.scene.control.Label) {
                    javafx.scene.control.Label label = (javafx.scene.control.Label) labelObj;
                    label.setStyle(highlight ? highlightCss : defaultCss);
                    return;
                }
            }

            try {
                Method setLabelStyle = navItem.getClass().getMethod("setLabelStyle", String.class);
                setLabelStyle.invoke(navItem, highlight ? highlightCss : defaultCss);
                return;
            } catch (NoSuchMethodException ignored) {
            }

            try {
                Field labelField = null;
                try {
                    labelField = navItem.getClass().getField("label");
                } catch (NoSuchFieldException ignored) {
                    try {
                        labelField = navItem.getClass().getField("titleLabel");
                    } catch (NoSuchFieldException ignored2) {
                        labelField = null;
                    }
                }
                if (labelField != null) {
                    Object labelObj = labelField.get(navItem);
                    if (labelObj instanceof javafx.scene.control.Label) {
                        javafx.scene.control.Label label = (javafx.scene.control.Label) labelObj;
                        label.setStyle(highlight ? highlightCss : defaultCss);
                        return;
                    }
                }
            } catch (Exception ignored) {
            }

            try {
                Field labelField = null;
                try {
                    labelField = navItem.getClass().getDeclaredField("label");
                } catch (NoSuchFieldException ignored) {
                    try {
                        labelField = navItem.getClass().getDeclaredField("titleLabel");
                    } catch (NoSuchFieldException ignored2) {
                        labelField = null;
                    }
                }
                if (labelField != null) {
                    labelField.setAccessible(true);
                    Object labelObj = labelField.get(navItem);
                    if (labelObj instanceof javafx.scene.control.Label) {
                        javafx.scene.control.Label label = (javafx.scene.control.Label) labelObj;
                        label.setStyle(highlight ? highlightCss : defaultCss);
                        return;
                    }
                }
            } catch (Exception ignored) {
            }

            try {
                Method setStyleMethod = navItem.getClass().getMethod("setStyle", String.class);
                setStyleMethod.invoke(navItem, highlight ? highlightCss : defaultCss);
                return;
            } catch (NoSuchMethodException ignored) {
            }

        } catch (Exception e) {
            logger.debug("applyNavigationItemHighlight: failed to apply highlight to NavigationItem: {}", e.getMessage());
        }
    }

    /**
     * Cached, position-aware wrapper that returns whether the card at a given index
     * inside a container needs sorting.
     *
     * @param card        the Card to test
     * @param container   the container object (CardsGroup, Box, or the raw List instance)
     * @param index       the index of the card inside the container (0-based)
     * @param elementName the name of the element (Box name or CardsGroup name) that contains the card
     * @return true if the card needs sorting (should glow), false otherwise
     */
    private boolean cardNeedsSorting(Model.CardsLists.Card card, Object container, int index, String elementName) {
        if (card == null) return false;
        if (container == null || index < 0) {
            // Fallback to non-cached compute if we don't have container/index
            return computeCardNeedsSorting(card, elementName);
        }

        // Obtain or create the per-container list of cached results
        List<Boolean> cacheList = positionSortCache.computeIfAbsent(container, k ->
                Collections.synchronizedList(new ArrayList<>()));

        // Ensure the list is large enough to hold the index
        synchronized (cacheList) {
            while (cacheList.size() <= index) {
                cacheList.add(null);
            }
            Boolean cached = cacheList.get(index);
            if (cached != null) {
                return cached;
            }
        }

        // Compute the result (heavy operation) and store it
        boolean result = computeCardNeedsSorting(card, elementName);

        synchronized (cacheList) {
            cacheList.set(index, result);
        }
        return result;
    }

    public void dispose() {
        try {
            UserInterfaceFunctions.unregisterOwnedCollectionRefresher(this::refreshFromModel);
        } catch (Throwable ignored) {
        }
        // Add other cleanup here if needed (stop background tasks, remove listeners, etc.)
    }

    private void setupOuicheListButtons() {
        Button compactBtn = ouicheListTab.getCompactDetailedButton();
        Button mosaicBtn = ouicheListTab.getMosaicListButton();
        if (compactBtn == null || mosaicBtn == null) return;

        compactBtn.setOnAction(e -> {
            if ("Compact mode".equals(compactBtn.getText())) {
                // ---- Switch to Compact mode ----
                compactBtn.setText("Detailed mode");
                mosaicBtn.setVisible(true);
                mosaicBtn.setManaged(true);
                mosaicBtn.setText("Mosaic"); // always reset sub-toggle when entering compact
                try {
                    displayCompactOuicheList(false);
                } catch (Exception ex) {
                    logger.error("Error displaying compact OuicheList", ex);
                }
            } else {
                // ---- Switch back to Detailed mode ----
                compactBtn.setText("Compact mode");
                mosaicBtn.setVisible(false);
                mosaicBtn.setManaged(false);
                mosaicBtn.setText("Mosaic");
                try {
                    displayOuicheListUnified();
                } catch (Exception ex) {
                    logger.error("Error displaying detailed OuicheList", ex);
                }
            }
        });

        mosaicBtn.setOnAction(e -> {
            if ("Mosaic".equals(mosaicBtn.getText())) {
                mosaicBtn.setText("List");
                try {
                    displayCompactOuicheList(true);
                } catch (Exception ex) {
                    logger.error("Error switching compact OuicheList to mosaic", ex);
                }
            } else {
                mosaicBtn.setText("Mosaic");
                try {
                    displayCompactOuicheList(false);
                } catch (Exception ex) {
                    logger.error("Error switching compact OuicheList to list", ex);
                }
            }
        });
    }

    private void displayCompactOuicheList(boolean mosaicMode) throws Exception {
        AnchorPane contentPane = ouicheListTab.getContentPane();
        contentPane.getChildren().clear();

        // Ensure data is loaded
        if (Model.CardsLists.OuicheList.getMaOuicheList() == null) {
            UserInterfaceFunctions.generateOuicheListType();
        }
        java.util.List<CardElement> ouicheList = Model.CardsLists.OuicheList.getMaOuicheList();
        if (ouicheList == null || ouicheList.isEmpty()) {
            javafx.scene.control.Label empty = new javafx.scene.control.Label("OuicheList is empty.");
            empty.setStyle("-fx-text-fill: white;");
            contentPane.getChildren().add(empty);
            return;
        }

        // Deduplicate: preserve insertion order, count occurrences by passCode (fallback: imagePath)
        java.util.Map<String, CardElement> uniqueCards = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> cardCounts = new java.util.LinkedHashMap<>();
        for (CardElement ce : ouicheList) {
            if (ce == null || ce.getCard() == null) continue;
            Card card = ce.getCard();
            String key = card.getPassCode() != null ? card.getPassCode() : card.getImagePath();
            if (key == null) continue;
            if (!uniqueCards.containsKey(key)) {
                uniqueCards.put(key, ce);
                cardCounts.put(key, 1);
            } else {
                cardCounts.merge(key, 1, Integer::sum);
            }
        }

        javafx.scene.Node content = mosaicMode
                ? buildCompactMosaicView(uniqueCards)
                : buildCompactListView(uniqueCards, cardCounts);

        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #100317; -fx-background: #100317;");
        scrollPane.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);

        contentPane.getChildren().add(scrollPane);
        AnchorPane.setTopAnchor(scrollPane, 0.0);
        AnchorPane.setBottomAnchor(scrollPane, 0.0);
        AnchorPane.setLeftAnchor(scrollPane, 0.0);
        AnchorPane.setRightAnchor(scrollPane, 0.0);
    }

    /**
     * Builds the compact list view: one row per unique card, showing image + FR/EN/JA names
     * + exemplary count (top-right) + unit/total price (bottom-right), mirroring the HTML export format.
     */
    private javafx.scene.Node buildCompactListView(
            java.util.Map<String, CardElement> uniqueCards,
            java.util.Map<String, Integer> cardCounts) {

        VBox listBox = new VBox(6);
        listBox.setPadding(new Insets(10));
        listBox.setStyle("-fx-background-color: #100317;");

        final double IMG_W = 80.0;
        final double IMG_H = 116.0;

        for (java.util.Map.Entry<String, CardElement> entry : uniqueCards.entrySet()) {
            CardElement ce = entry.getValue();
            Card card = ce.getCard();
            int count = cardCounts.get(entry.getKey());

            // ---- Outer row ----
            HBox row = new HBox(10);
            row.setPadding(new Insets(8));
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.setStyle(
                    "-fx-border-color: white; -fx-border-width: 1; " +
                            "-fx-border-radius: 5; -fx-background-radius: 5; " +
                            "-fx-background-color: black;");

            // ---- Card image (left) ----
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView();
            iv.setFitWidth(IMG_W);
            iv.setFitHeight(IMG_H);
            iv.setPreserveRatio(true);
            loadCardImageInto(card, iv, IMG_W, IMG_H);

            // ---- Names (centre, takes remaining space) ----
            VBox namesBox = new VBox(4);
            HBox.setHgrow(namesBox, Priority.ALWAYS);
            namesBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            javafx.scene.control.Label frLabel = new javafx.scene.control.Label(
                    card.getName_FR() != null ? card.getName_FR() : "");
            javafx.scene.control.Label enLabel = new javafx.scene.control.Label(
                    card.getName_EN() != null ? card.getName_EN() : "");
            javafx.scene.control.Label jaLabel = new javafx.scene.control.Label(
                    card.getName_JA() != null ? card.getName_JA() : "");

            frLabel.setStyle("-fx-text-fill: white;       -fx-font-size: 13;");
            enLabel.setStyle("-fx-text-fill: white;     -fx-font-size: 13;");
            jaLabel.setStyle("-fx-text-fill: white;     -fx-font-size: 13;");
            frLabel.setWrapText(true);
            enLabel.setWrapText(true);
            jaLabel.setWrapText(true);

            namesBox.getChildren().addAll(frLabel, enLabel, jaLabel);

            // ---- Count + price (right, top-aligned) ----
            VBox valueBox = new VBox(4);
            valueBox.setAlignment(javafx.geometry.Pos.TOP_RIGHT);

            javafx.scene.control.Label countLabel = new javafx.scene.control.Label(
                    "\u00d7" + count); // × symbol
            countLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold;");
            valueBox.getChildren().add(countLabel);

            if (card.getPrice() != null && !card.getPrice().trim().isEmpty()) {
                try {
                    float unitPrice = Float.parseFloat(card.getPrice());
                    float totalPrice = unitPrice * count;
                    javafx.scene.control.Label unitPriceLabel = new javafx.scene.control.Label(
                            String.format("%.2f\u20ac", unitPrice));
                    javafx.scene.control.Label totalPriceLabel = new javafx.scene.control.Label(
                            String.format("= %.2f\u20ac", totalPrice));
                    unitPriceLabel.setStyle("-fx-text-fill: white; -fx-font-size: 11; -fx-font-weight: bold;");
                    totalPriceLabel.setStyle("-fx-text-fill: white; -fx-font-size: 11; -fx-font-weight: bold;");
                    valueBox.getChildren().addAll(unitPriceLabel, totalPriceLabel);
                } catch (NumberFormatException ignored) {
                }
            }

            row.getChildren().addAll(iv, namesBox, valueBox);
            listBox.getChildren().add(row);
        }

        return listBox;
    }

    /**
     * Builds the compact mosaic view: one image per unique card, wrapped in a FlowPane.
     * No titles, no categories — pure image grid.
     */
    private javafx.scene.Node buildCompactMosaicView(
            java.util.Map<String, CardElement> uniqueCards) {

        javafx.scene.layout.FlowPane flow = new javafx.scene.layout.FlowPane();
        flow.setHgap(5);
        flow.setVgap(5);
        flow.setPadding(new Insets(10));
        flow.setStyle("-fx-background-color: #100317;");

        double cellW = cardWidthProperty.get();
        double cellH = cardHeightProperty.get();

        for (java.util.Map.Entry<String, CardElement> entry : uniqueCards.entrySet()) {
            Card card = entry.getValue().getCard();

            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView();
            iv.setFitWidth(cellW);
            iv.setFitHeight(cellH);
            iv.setPreserveRatio(true);
            loadCardImageInto(card, iv, cellW, cellH);

            javafx.scene.layout.StackPane wrapper = new javafx.scene.layout.StackPane(iv);
            wrapper.setPadding(new Insets(2));
            flow.getChildren().add(wrapper);
        }

        return flow;
    }

    /**
     * Loads a card image asynchronously into the given ImageView, using LruImageCache
     * and DataBaseUpdate exactly like the other cell renderers in the application.
     */
    private void loadCardImageInto(Card card, javafx.scene.image.ImageView iv,
                                   double fitW, double fitH) {
        if (card == null || card.getImagePath() == null) return;

        String imageKey = card.getImagePath();
        String[] addresses = Model.Database.DataBaseUpdate.getAddresses(imageKey + ".jpg");
        if (addresses == null || addresses.length == 0) return;

        final String resolvedPath = "file:" + addresses[0];

        // Try the LRU cache first (fast path, runs on FX thread)
        javafx.scene.image.Image cached = Utils.LruImageCache.getImage(resolvedPath);
        if (cached != null) {
            iv.setImage(cached);
            return;
        }

        // Background load so we never block the FX thread
        Thread loader = new Thread(() -> {
            try {
                javafx.scene.image.Image img =
                        new javafx.scene.image.Image(resolvedPath, fitW, fitH, true, true);
                Utils.LruImageCache.addImage(resolvedPath, img);
                javafx.application.Platform.runLater(() -> iv.setImage(img));
            } catch (Exception e) {
                logger.debug("loadCardImageInto: failed to load image for {}", resolvedPath, e);
            }
        }, "compact-ouiche-img-loader");
        loader.setDaemon(true);
        loader.start();
    }
}