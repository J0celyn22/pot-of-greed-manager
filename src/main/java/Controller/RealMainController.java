package Controller;

import Model.CardsLists.*;
import View.*;
import View.SharedCollectionTab.TabType;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
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
    private static final String ARCHETYPE_MARKER = "[ARCHETYPE]";
    private final DoubleProperty cardWidthProperty = new SimpleDoubleProperty(100);
    private final DoubleProperty cardHeightProperty = new SimpleDoubleProperty(146);
    // Cache keyed by container object (Box or CardsGroup or other list owner).
    // Each value is a synchronized List<Boolean> where each index corresponds to the
    // card position inside that container. A null entry means "not yet computed".
    private final java.util.concurrent.ConcurrentHashMap<Object, List<Boolean>> positionSortCache = new java.util.concurrent.ConcurrentHashMap<>();
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
    private TreeView<String> ouicheTreeView;

    private boolean isMosaicMode = true;
    private boolean isPrintedMode = false;
    private boolean ouicheListLoaded = false;
    private TreeView<String> archetypesTreeView;
    private TreeView<String> decksAndCollectionsTreeView;

    private Tab myCollectionTabHandle;
    private Tab decksTabHandle;
    private Tab ouicheListTabHandle;

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

    private static String sanitize(String raw) {
        if (raw == null) return "";
        // Strip only leading/trailing decorator characters (= for boxes, - for categories),
        // preserving hyphens that are genuinely part of the name.
        String s = raw.trim();
        // Strip leading = or -
        int start = 0;
        while (start < s.length() && (s.charAt(start) == '=' || s.charAt(start) == '-')) start++;
        // Strip trailing = or -
        int end = s.length();
        while (end > start && (s.charAt(end - 1) == '=' || s.charAt(end - 1) == '-')) end--;
        return s.substring(start, end).trim();
    }

    private static boolean cardsMatchLoose(
            Model.CardsLists.Card a, Model.CardsLists.Card b) {
        if (a == null || b == null) return false;
        if (a.getPassCode() != null && a.getPassCode().equals(b.getPassCode())) return true;
        if (a.getPrintCode() != null && a.getPrintCode().equals(b.getPrintCode())) return true;
        if (a.getKonamiId() != null && a.getKonamiId().equals(b.getKonamiId())) return true;
        return false;
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

    private void adjustCardSize(double delta) {
        double scalingFactor = delta > 0 ? 1.1 : 0.9;
        double newWidth = cardWidthProperty.get() * scalingFactor;
        if (newWidth < 50) newWidth = 50;
        else if (newWidth > 300) newWidth = 300;
        cardWidthProperty.set(newWidth);
        cardHeightProperty.set(newWidth * 146.0 / 100.0);
    }

    // --- My Collection display ---

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
            // Clear selection when clicking on empty space in the mosaic list
            mosaicListView.addEventHandler(
                    javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                    buildRightPaneEmptySpaceClearHandler());
            view = mosaicListView;
        } else {
            ListView<Card> listView = new ListView<>(FXCollections.observableArrayList(filteredCards));
            listView.setCellFactory(param -> new CardsListCell(isPrintedMode, listImageWidth, listImageHeight));
            listView.setStyle("-fx-background-color: #100317; -fx-control-inner-background: #100317;");
            // Clear selection when clicking on empty space in the card list
            listView.addEventHandler(
                    javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                    buildRightPaneEmptySpaceClearHandler());
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

    @FXML
    private void initialize() {
        UserInterfaceFunctions.readPathsFromFile();

        try {
            Map<Integer, Card> allCards = Model.Database.Database.getAllCardsList();
            if (allCards != null && !allCards.isEmpty()) {
                SubListCreator.CreateArchetypeLists(allCards);
                // Enrich every card's archetype list with ALL archetypes it belongs to,
                // fixing the bug where secondary archetypes hid the primary one.
                SubListCreator.UpdateCardArchetypes();
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

            // Store tab handles for dirty-indicator updates
            if (mainTabPane != null && mainTabPane.getTabs().size() >= 2) {
                myCollectionTabHandle = mainTabPane.getTabs().get(0);
                decksTabHandle = mainTabPane.getTabs().get(1);
                ouicheListTabHandle = mainTabPane.getTabs().get(2);
            }
            UserInterfaceFunctions.registerTabDirtyIndicatorUpdater(this::updateTabDirtyIndicators);
        }

        // Wire My Collection save button
        if (myCollectionTab.getSaveButton() != null) {
            myCollectionTab.getSaveButton().setOnAction(e -> {
                try {
                    UserInterfaceFunctions.saveMyCollection();
                    updateTabDirtyIndicators();
                    populateMyCollectionMenu();         // refresh nav to remove "*" markers
                } catch (Exception ex) {
                    logger.error("Error saving My Collection", ex);
                }
            });
        }

        // Wire Decks and Collections save button
        if (decksTab.getSaveButton() != null) {
            decksTab.getSaveButton().setOnAction(e -> {
                try {
                    UserInterfaceFunctions.saveAllDecksAndCollections();
                    updateTabDirtyIndicators();
                    populateDecksAndCollectionsMenu();  // refresh nav to remove "*" markers
                } catch (Exception ex) {
                    logger.error("Error saving Decks and Collections", ex);
                }
            });
        }

        // Wire OuicheList save button
        if (ouicheListTab.getSaveButton() != null) {
            ouicheListTab.getSaveButton().setOnAction(e -> {
                try {
                    UserInterfaceFunctions.saveOuicheList();
                    updateTabDirtyIndicators();
                } catch (Exception ex) {
                    logger.error("Error saving OuicheList", ex);
                }
            });
        }

        try {
            displayMyCollection();
            populateMyCollectionMenu();
            UserInterfaceFunctions.registerOwnedCollectionRefresher(() -> {
                try {
                    String target = MenuActionHandler.getAndClearLastAddedTarget();
                    populateMyCollectionMenu();
                    // Refresh cells in-place: preserves scroll position and does NOT rebuild the tree.
                    // "Move to..." goes through here with target == null → no scroll ever triggered.
                    if (myCollectionTreeView != null) {
                        myCollectionTreeView.refresh();
                    }
                    // Scroll to the newly added card only for "Add to..." actions.
                    if (target != null) {
                        scrollToNewCardInGroup(target);
                    }
                    // ── Dirty indicator ──
                    updateTabDirtyIndicators();
                } catch (Exception e) {
                    logger.debug("My Collection refresher failed", e);
                }
            });
            UserInterfaceFunctions.registerOwnedCollectionStructureRefresher(() -> {
                try {
                    displayMyCollection();
                    populateMyCollectionMenu();
                    Object renameTarget = UserInterfaceFunctions.getAndClearPendingRenameTarget();
                    if (renameTarget != null) {
                        final Object finalTarget = renameTarget;
                        Platform.runLater(() -> {
                            logger.debug("Pending rename: searching nav for target={}", finalTarget);
                            NavigationItem toRename = findNavItemInMenuVBox(
                                    myCollectionTab.getMenuVBox(), finalTarget);
                            if (toRename != null) {
                                logger.debug("Pending rename: found NavigationItem '{}', starting inline rename",
                                        toRename.getLabel().getText());
                                // Expand parent (e.g. Box that contains the new Category)
                                expandNavAncestors(toRename);
                                // Scroll nav menu so the rename field is visible
                                scrollNavToItem(myCollectionTab, toRename);
                                startAddRename(toRename, finalTarget);
                            } else {
                                logger.warn("Pending rename: NavigationItem not found for target={}", finalTarget);
                            }
                        });
                    }
                } catch (Exception e) {
                    logger.debug("My Collection structure refresher failed", e);
                }
            });
        } catch (Exception ex) {
            logger.error("Error displaying My Collection", ex);
        }

        if (mainTabPane != null) {
            mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                int selectedIndex = mainTabPane.getTabs().indexOf(newTab);
                if (selectedIndex == 1) {
                    try {
                        populateDecksAndCollectionsMenu();
                        UserInterfaceFunctions.registerDecksCollectionsRefresher(() -> {
                            try {
                                // REPLACE the entire inner block (lines 750–813) with:

                                String cardTarget = MenuActionHandler.getAndClearLastDecksAddedTarget();
                                Object deckMoveTarget = UserInterfaceFunctions.getAndClearPendingDecksScrollTarget();
                                Object[] createCollData = UserInterfaceFunctions.getAndClearPendingDecksCreateCollectionData();
// Read rename target early so we know whether a structural rebuild is needed
                                Object renameTarget = UserInterfaceFunctions.getAndClearPendingDecksRenameTarget();

                                populateDecksAndCollectionsMenu();

// Structural change: deck/collection added, moved, or created-from-deck → full rebuild.
// Data-only change: card added/removed/moved/pasted/cut → refresh in-place to keep scroll position.
                                boolean isStructuralChange = (deckMoveTarget != null) || (createCollData != null) || (renameTarget != null);
                                if (isStructuralChange || cardTarget != null) {
                                    displayDecksAndCollections();
                                } else {
                                    if (decksAndCollectionsTreeView != null) {
                                        decksAndCollectionsTreeView.refresh();
                                        View.CardTreeCell.refreshAllGridViews();
                                    }
                                }

                                // Scroll content tree to where a card was added
                                if (cardTarget != null) {
                                    scrollToTargetInDecksTree(cardTarget);
                                }

                                // Scroll / expand nav to a moved deck
                                if (deckMoveTarget != null) {
                                    scrollToMovedDeck(deckMoveTarget);
                                }

                                // "Create Collection from Deck" rename flow
                                if (createCollData != null && createCollData.length == 2
                                        && createCollData[0] instanceof ThemeCollection
                                        && createCollData[1] instanceof Deck) {

                                    final ThemeCollection newColl = (ThemeCollection) createCollData[0];
                                    final Deck movedDeck = (Deck) createCollData[1];

                                    Platform.runLater(() -> {
                                        NavigationItem toRename = findNavItemInMenuVBox(
                                                decksTab.getMenuVBox(), newColl);
                                        if (toRename != null) {
                                            toRename.setExpanded(true);
                                            expandNavAncestors(toRename);
                                            scrollNavToItem(decksTab, toRename);
                                            startDecksCreateCollectionRename(toRename, newColl, movedDeck);
                                        } else {
                                            logger.warn("Create-Collection rename: NavigationItem not found for {}",
                                                    newColl.getName());
                                        }
                                    });
                                }

                                // Normal add/rename for newly created Deck or Collection
                                if (renameTarget != null) {
                                    final Object finalTarget = renameTarget;
                                    Platform.runLater(() -> {
                                        NavigationItem toRename = findNavItemInMenuVBox(
                                                decksTab.getMenuVBox(), finalTarget);
                                        if (toRename != null) {
                                            expandNavAncestors(toRename);
                                            scrollNavToItem(decksTab, toRename);
                                            startDecksAddRename(toRename, finalTarget);
                                        } else {
                                            logger.warn("Pending decks rename: NavigationItem not found for target={}",
                                                    finalTarget);
                                        }
                                    });
                                }
// Update dirty indicators
                                updateTabDirtyIndicators();
                            } catch (Exception e) {
                                logger.debug("Decks refresher failed", e);
                            }
                        });
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

        // Refresh both the middle-pane tree views and the right-pane cards display
        // whenever the selection changes, so the visual selection border stays in sync.
        // CHANGE in initialize(), the SelectionManager.addSelectionChangeListener block:

        Controller.SelectionManager.addSelectionChangeListener(() -> {
            Platform.runLater(() -> {
                if (myCollectionTreeView != null) myCollectionTreeView.refresh();
                if (decksAndCollectionsTreeView != null) decksAndCollectionsTreeView.refresh();
                if (ouicheTreeView != null) ouicheTreeView.refresh();
                if (archetypesTreeView != null) archetypesTreeView.refresh();

                // Also refresh all live GridViews — TreeView.refresh() does not
                // propagate into nested ControlsFX GridViews, so cells off-screen
                // keep a stale selection border until explicitly refreshed here.
                View.CardTreeCell.refreshAllGridViews();

                if (cardsDisplayContainer != null) {
                    for (javafx.scene.Node node : cardsDisplayContainer.getChildren()) {
                        if (node instanceof javafx.scene.control.ListView) {
                            ((javafx.scene.control.ListView<?>) node).refresh();
                        }
                    }
                }
            });
        });

        UserInterfaceFunctions.registerOwnedCollectionRefresher(this::refreshFromModel);
        setupGlobalKeyShortcuts();

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

    /**
     * Starts an inline rename on a freshly created Deck or ThemeCollection.
     * Confirm → updates the model name and refreshes the decks view.
     * Cancel → removes the element from the model and refreshes.
     */
    private void startDecksAddRename(NavigationItem navItem, Object modelObj) {
        navItem.startInlineRename(
                newName -> {
                    if (modelObj instanceof Deck) ((Deck) modelObj).setName(newName);
                    else if (modelObj instanceof ThemeCollection)
                        ((ThemeCollection) modelObj).setName(newName);
                    // The element now has a real name — mark it dirty
                    UserInterfaceFunctions.markDirty(modelObj);
                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    UserInterfaceFunctions.refreshDecksAndCollectionsView();
                },
                () -> {
                    // Cancel: remove the freshly created element
                    try {
                        DecksAndCollectionsList dac = UserInterfaceFunctions.getDecksList();
                        if (dac != null) {
                            if (modelObj instanceof Deck) {
                                Deck d = (Deck) modelObj;
                                boolean removed = dac.getDecks() != null && dac.getDecks().remove(d);
                                if (!removed && dac.getCollections() != null) {
                                    outer:
                                    for (ThemeCollection tc : dac.getCollections()) {
                                        if (tc == null || tc.getLinkedDecks() == null) continue;
                                        for (List<Deck> unit : tc.getLinkedDecks()) {
                                            if (unit != null && unit.remove(d)) break outer;
                                        }
                                    }
                                }
                            } else if (modelObj instanceof ThemeCollection) {
                                if (dac.getCollections() != null)
                                    dac.getCollections().remove(modelObj);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    // Element was never committed — clear its dirty flag
                    UserInterfaceFunctions.clearDirty(modelObj);
                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    UserInterfaceFunctions.refreshDecksAndCollectionsView();
                }
        );
    }

    /**
     * Expands all NavigationItem ancestors of the given item in the nav menu,
     * so the item itself becomes visible.
     */
    private void expandNavAncestors(NavigationItem item) {
        if (item == null) return;
        javafx.scene.Node parent = item.getParent();
        while (parent != null) {
            if (parent instanceof NavigationItem) {
                ((NavigationItem) parent).setExpanded(true);
            }
            parent = parent.getParent();
        }
    }

    /**
     * Scrolls the nav-menu ScrollPane inside {@code tab} so that {@code item}
     * is centred in the viewport.
     */
    private void scrollNavToItem(SharedCollectionTab tab, NavigationItem item) {
        if (tab == null || item == null) return;
        Platform.runLater(() -> {
            try {
                ScrollPane sp = tab.getMenuScrollPane();
                VBox content = tab.getMenuVBox();
                if (sp == null || content == null) return;

                // Walk up from item to content to accumulate Y offset
                double itemY = 0;
                javafx.scene.Node node = item;
                while (node != null && node != content) {
                    itemY += node.getBoundsInParent().getMinY();
                    node = node.getParent();
                }

                javafx.geometry.Bounds vb = sp.getViewportBounds();
                javafx.geometry.Bounds cb = content.getBoundsInLocal();
                if (vb == null || cb == null) return;
                double viewportH = vb.getHeight();
                double contentH = cb.getHeight();
                if (contentH <= viewportH) return;

                double itemH = item.getBoundsInLocal().getHeight();
                double targetY = itemY - (viewportH - itemH) / 2.0;
                targetY = Math.max(0, Math.min(targetY, contentH - viewportH));
                sp.setVvalue(targetY / (contentH - viewportH));
            } catch (Throwable ignored) {
            }
        });
    }

    /**
     * Scrolls the decks-and-collections TreeView so the node corresponding to
     * {@code handlerTarget} is visible after a tree rebuild.
     * Maps known aliases ("Exclusion List" → "Cards not to add").
     */
    private void scrollToTargetInDecksTree(String handlerTarget) {
        if (decksAndCollectionsTreeView == null || handlerTarget == null) return;

        String[] rawParts = handlerTarget.split("\\s*/\\s*");
        String[] parts = new String[rawParts.length];
        for (int i = 0; i < rawParts.length; i++) {
            String p = rawParts[i].trim();
            if (p.equalsIgnoreCase("Exclusion List")) p = "Cards not to add";
            parts[i] = p;
        }

        Platform.runLater(() -> {
            if (decksAndCollectionsTreeView == null) return;
            TreeItem<String> root = decksAndCollectionsTreeView.getRoot();
            if (root == null) return;

            // Try full path, then last-2, then last-1
            TreeItem<String> target = findTreeItemByPath(root, parts, 0);
            if (target == null && parts.length >= 2)
                target = findTreeItemByPath(root,
                        new String[]{parts[parts.length - 2], parts[parts.length - 1]}, 0);
            if (target == null)
                target = findTreeItemByPath(root, new String[]{parts[parts.length - 1]}, 0);
            if (target == null) return;

            // Expand ancestors so the node is reachable
            for (TreeItem<String> a = target.getParent(); a != null; a = a.getParent())
                a.setExpanded(true);

            final TreeItem<String> finalTarget = target;
            Platform.runLater(() -> {
                int row = decksAndCollectionsTreeView.getRow(finalTarget);
                if (row >= 0) decksAndCollectionsTreeView.scrollTo(row);
            });
        });
    }

    /**
     * After an "Add to..." action, scrolls the tree so the newly added card
     * (always the last item in its group's GridView) is visible.
     * If the target cell's bottom is already within the viewport, nothing happens.
     */
    private void scrollToNewCardInGroup(String handlerTarget) {
        if (myCollectionTreeView == null || handlerTarget == null) return;

        String[] parts = handlerTarget.split("\\s*/\\s*");

        Platform.runLater(() -> {
            if (myCollectionTreeView == null) return;
            TreeItem<String> root = myCollectionTreeView.getRoot();
            if (root == null) return;

            // --- Locate the target TreeItem ---
            TreeItem<String> target = findTreeItemByPath(root, parts, 0);
            if (target == null && parts.length > 1)
                target = findTreeItemByPath(root, new String[]{parts[parts.length - 1]}, 0);
            if (target == null)
                target = findTreeItemByPath(root, new String[]{parts[0]}, 0);
            if (target == null) return;

            // If a Box was found instead of a CardsGroup, descend to its first CardsGroup child
            // (happens when handlerTarget has only a box name, e.g. "BoxName").
            if (target instanceof DataTreeItem) {
                Object data = ((DataTreeItem<?>) target).getData();
                if (!(data instanceof CardsGroup)) {
                    for (TreeItem<String> child : target.getChildren()) {
                        if (child instanceof DataTreeItem
                                && ((DataTreeItem<?>) child).getData() instanceof CardsGroup) {
                            target = child;
                            break;
                        }
                    }
                }
            }

            // Expand all ancestors so the row has a valid index.
            for (TreeItem<String> a = target.getParent(); a != null; a = a.getParent())
                a.setExpanded(true);

            final int targetRow = myCollectionTreeView.getRow(target);
            if (targetRow < 0) return;

            // --- Check visibility via VirtualFlow ---
            javafx.scene.control.skin.VirtualFlow<?> vf = getVirtualFlow();
            boolean rowInView = false;
            if (vf != null) {
                int first = vf.getFirstVisibleCell() != null
                        ? vf.getFirstVisibleCell().getIndex() : -1;
                int last = vf.getLastVisibleCell() != null
                        ? vf.getLastVisibleCell().getIndex() : -1;
                rowInView = first >= 0 && targetRow >= first && targetRow <= last;
            }

            // If the row is completely outside the viewport, scroll its top into view first
            // so the cell gets rendered and can be measured in the next pass.
            if (!rowInView) {
                myCollectionTreeView.scrollTo(targetRow);
            }

            // Deferred second pass: now that the cell is rendered (or was already visible),
            // scroll further if needed so the BOTTOM of the cell (= last/new card) is visible.
            Platform.runLater(() -> adjustScrollToShowCellBottom(targetRow));
        });
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
        myCollectionTreeView.addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                buildMiddlePaneEmptySpaceFilter());
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
     * Starts an inline rename for a ThemeCollection that was just created from a
     * standalone Deck ("Create Collection" action).
     * <p>
     * Confirm → renames the collection and refreshes.
     * Cancel  → removes the collection and moves the deck back to the standalone
     * list, then refreshes.
     */
    private void startDecksCreateCollectionRename(NavigationItem navItem,
                                                  ThemeCollection newColl,
                                                  Deck movedDeck) {
        navItem.startInlineRename(
                newName -> {
                    newColl.setName(newName);
                    // Both the collection and the deck it now contains are dirty
                    UserInterfaceFunctions.markDirty(newColl);
                    UserInterfaceFunctions.markDirty(movedDeck);
                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    UserInterfaceFunctions.refreshDecksAndCollectionsView();
                },
                () -> {
                    // Cancel: undo — remove the collection, restore the deck to standalone
                    try {
                        DecksAndCollectionsList dac = UserInterfaceFunctions.getDecksList();
                        if (dac != null) {
                            if (dac.getCollections() != null)
                                dac.getCollections().remove(newColl);
                            if (dac.getDecks() == null)
                                dac.setDecks(new java.util.ArrayList<>());
                            dac.getDecks().add(movedDeck);
                        }
                    } catch (Throwable ignored) {
                    }
                    // The collection was never saved — remove it from dirty tracking
                    UserInterfaceFunctions.clearDirty(newColl);
                    // The deck is back standalone; clear its "dirty from the failed create" flag too
                    // It may legitimately be dirty from previous edits, but those would have been
                    // marked before this action — clearDirty here only removes the flag we added.
                    UserInterfaceFunctions.clearDirty(movedDeck);
                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    UserInterfaceFunctions.refreshDecksAndCollectionsView();
                }
        );
    }

    /**
     * Searches the live menuVBox for the NavigationItem whose userData == target.
     * This must be called AFTER all nav rebuilds have completed.
     */
    private NavigationItem findNavItemInMenuVBox(VBox menuVBox, Object target) {
        if (menuVBox == null || target == null) return null;
        for (javafx.scene.Node node : menuVBox.getChildren()) {
            NavigationItem found = findNavItemInNode(node, target);
            if (found != null) return found;
        }
        logger.debug("findNavItemInMenuVBox: no item found for target={}", target);
        return null;
    }

    private NavigationItem findNavItemInNode(javafx.scene.Node node, Object target) {
        if (node instanceof NavigationMenu) {
            for (NavigationItem item : ((NavigationMenu) node).getItems()) {
                NavigationItem found = findNavItemByUserDataInItem(item, target);
                if (found != null) return found;
            }
        } else if (node instanceof NavigationItem) {
            return findNavItemByUserDataInItem((NavigationItem) node, target);
        }
        return null;
    }

    private NavigationItem findNavItemByUserDataInItem(NavigationItem item, Object target) {
        if (item == null) return null;
        logger.debug("findNavItemByUserDataInItem: checking '{}' userData={}",
                item.getLabel() != null ? item.getLabel().getText() : "?",
                item.getUserData());
        if (item.getUserData() == target) return item;
        for (NavigationItem sub : item.getSubItems()) {
            NavigationItem found = findNavItemByUserDataInItem(sub, target);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Populate the My Collection navigation menu.
     * <p>
     * IMPORTANT: This method wires click handlers so that clicking a navigation label
     * selects and scrolls the corresponding node in the My Collection TreeView.
     * <p>
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
                String boxName = Model.CardsLists.OwnedCardsCollection.extractName(rawBoxName, '=');
                NavigationItem boxItem = createNavigationItem(boxName, 0);
                boxItem.setUserData(box);
                attachNavItemDropHandlers(boxItem, box);
                boxItem.setOnLabelClicked(evt -> {
                    Controller.SelectionManager.setLastClickedNavigationItem(box);
                    navigateToTree(myCollectionTreeView, boxName);
                });

                // --- highlight logic (unchanged) ---
                boolean boxHasUnsorted = boxHasUnsortedCards(box, boxName);
                applyNavigationItemHighlight(boxItem, boxHasUnsorted);

                // --- NEW: context menu for Box items ---
                {
                    ContextMenu boxCm = NavigationContextMenuBuilder.forMyCollectionBox(box, collection);
                    boxItem.setOnContextMenuRequested(e -> {
                        boxCm.show(boxItem, e.getScreenX(), e.getScreenY());
                        e.consume(); // prevent event from reaching the menuVBox background handler
                    });
                }

                // Add groups (Cards groups) under the box
                if (box.getContent() != null) {
                    for (CardsGroup group : box.getContent()) {
                        String rawGroupName = group.getName() == null ? "" : group.getName();
                        String groupName = Model.CardsLists.OwnedCardsCollection.extractName(rawGroupName, '-');
                        if (groupName.isEmpty()) continue; // skip unnamed groups (boxes with cards directly)
                        NavigationItem groupItem = createNavigationItem(groupName, 1);
                        groupItem.setUserData(group);
                        attachNavItemDropHandlers(groupItem, group);
                        groupItem.setOnLabelClicked(evt -> {
                            Controller.SelectionManager.setLastClickedNavigationItem(group);
                            navigateToTree(myCollectionTreeView, boxName, groupName);
                        });

                        // highlight (unchanged)
                        boolean groupHasUnsorted = groupHasUnsortedCards(group, groupName);
                        applyNavigationItemHighlight(groupItem, groupHasUnsorted);
                        if (groupHasUnsorted && !boxHasUnsorted) {
                            applyNavigationItemHighlight(boxItem, true);
                            boxHasUnsorted = true;
                        }

                        // --- NEW: context menu for Category items ---
                        {
                            ContextMenu groupCm = NavigationContextMenuBuilder.forMyCollectionCategory(group, box, collection);
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
                        String subBoxName = Model.CardsLists.OwnedCardsCollection.extractName(rawSubBoxName, '=');
                        NavigationItem subBoxItem = createNavigationItem(subBoxName, 1);
                        subBoxItem.setUserData(subBox);
                        attachNavItemDropHandlers(subBoxItem, subBox);
                        subBoxItem.setOnLabelClicked(evt -> {
                            Controller.SelectionManager.setLastClickedNavigationItem(subBox);
                            navigateToTree(myCollectionTreeView, subBoxName);
                        });

                        boolean subBoxHasUnsorted = boxHasUnsortedCards(subBox, subBoxName);
                        applyNavigationItemHighlight(subBoxItem, subBoxHasUnsorted);
                        if (subBoxHasUnsorted && !boxHasUnsorted) {
                            applyNavigationItemHighlight(boxItem, true);
                            boxHasUnsorted = true;
                        }

                        // Sub-boxes are treated like Boxes for the context menu
                        {
                            ContextMenu subBoxCm = NavigationContextMenuBuilder.forMyCollectionBox(subBox, collection);
                            subBoxItem.setOnContextMenuRequested(e -> {
                                subBoxCm.show(subBoxItem, e.getScreenX(), e.getScreenY());
                                e.consume();
                            });
                        }

                        if (subBox.getContent() != null) {
                            for (CardsGroup group : subBox.getContent()) {
                                String rawGName = group.getName() == null ? "" : group.getName();
                                String gName = Model.CardsLists.OwnedCardsCollection.extractName(rawGName, '-');
                                if (gName.isEmpty()) continue; // skip unnamed groups
                                NavigationItem gItem = createNavigationItem(gName, 2);
                                gItem.setUserData(group);
                                attachNavItemDropHandlers(gItem, group);
                                gItem.setOnLabelClicked(evt -> {
                                    Controller.SelectionManager.setLastClickedNavigationItem(group);
                                    navigateToTree(myCollectionTreeView, subBoxName, gName);
                                });

                                boolean gHasUnsorted = groupHasUnsortedCards(group, gName);
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
                                    ContextMenu gCm = NavigationContextMenuBuilder.forMyCollectionCategory(group, subBox, collection);
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
     * Starts an inline rename on a freshly created Box or CardsGroup.
     * Confirm → updates the model name and does a full structure refresh.
     * Cancel → removes the element from the model and does a full structure refresh.
     */
    private void startAddRename(NavigationItem navItem, Object modelObj) {
        navItem.startInlineRename(
                newName -> {
                    if (modelObj instanceof Box) ((Box) modelObj).setName(newName);
                    else if (modelObj instanceof CardsGroup) ((CardsGroup) modelObj).setName(newName);
                    // Mark dirty — the new element now has a real name, so it needs saving
                    UserInterfaceFunctions.markMyCollectionDirty();
                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    UserInterfaceFunctions.refreshOwnedCollectionStructure();
                },
                () -> {
                    // Cancel: remove the freshly created element
                    try {
                        OwnedCardsCollection owned = OuicheList.getMyCardsCollection();
                        if (owned != null) {
                            if (modelObj instanceof Box) {
                                Box b = (Box) modelObj;
                                if (!owned.getOwnedCollection().remove(b)) {
                                    for (Box top : owned.getOwnedCollection()) {
                                        if (top.getSubBoxes() != null && top.getSubBoxes().remove(b)) break;
                                    }
                                }
                            } else if (modelObj instanceof CardsGroup) {
                                CardsGroup cat = (CardsGroup) modelObj;
                                outer:
                                for (Box top : owned.getOwnedCollection()) {
                                    if (top.getContent() != null && top.getContent().remove(cat)) break;
                                    if (top.getSubBoxes() != null) {
                                        for (Box sub : top.getSubBoxes()) {
                                            if (sub.getContent() != null && sub.getContent().remove(cat))
                                                break outer;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    // Nothing new was actually persisted — no dirty change needed
                    UserInterfaceFunctions.refreshOwnedCollectionStructure();
                }
        );
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


// --- Decks & Collections display (left unchanged except for keeping decksTreeView field) ---

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

// --- OuicheList helpers (navigation wiring preserved) ---

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
        ouicheTreeView.addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                buildMiddlePaneEmptySpaceFilter());
        contentPane.getChildren().add(ouicheTreeView);
        AnchorPane.setTopAnchor(ouicheTreeView, 0.0);
        AnchorPane.setBottomAnchor(ouicheTreeView, 0.0);
        AnchorPane.setLeftAnchor(ouicheTreeView, 0.0);
        AnchorPane.setRightAnchor(ouicheTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        ouicheTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());
    }

// --- Archetypes tab: display and menu population (unchanged) ---

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
                collectionNavItem.setUserData(collection);

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
                            deckSubItem.setUserData(linkedDeck);
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
                navItem.setUserData(deck);
                navItem.setOnLabelClicked(evt -> navigateToTree(ouicheTreeView, deck.getName()));
                navigationMenu.addItem(navItem);
            }
        }

        menuVBox.getChildren().add(navigationMenu);
    }

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
        archetypesTreeView.addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                buildMiddlePaneEmptySpaceFilter());
        contentPane.getChildren().add(archetypesTreeView);
        AnchorPane.setTopAnchor(archetypesTreeView, 0.0);
        AnchorPane.setBottomAnchor(archetypesTreeView, 0.0);
        AnchorPane.setLeftAnchor(archetypesTreeView, 0.0);
        AnchorPane.setRightAnchor(archetypesTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        archetypesTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());

        logger.info("Archetypes displayed with {} archetype(s).", rootItem.getChildren().size());
    }

// --- Tree item builders and helpers (unchanged) ---

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
                if (groupHasUnsortedCards(group, Model.CardsLists.OwnedCardsCollection.extractName(group.getName() == null ? "" : group.getName(), '-'))) {
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
                String subBoxDisplayName = Model.CardsLists.OwnedCardsCollection.extractName(sub.getName() == null ? "" : sub.getName(), '=');
                if (boxHasUnsortedCards(sub, subBoxDisplayName)) return true;
            }
        }

        return false;
    }

    private DataTreeItem<Object> createBoxTreeItem(Box box) {
        String cleanName = Model.CardsLists.OwnedCardsCollection.extractName(box.getName() == null ? "" : box.getName(), '=');
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
        String displayName = Model.CardsLists.OwnedCardsCollection.extractName(group.getName() == null ? "" : group.getName(), '-');
        DataTreeItem<Object> groupItem = new DataTreeItem<>(displayName, group);
        groupItem.setExpanded(true);
        return groupItem;
    }

    private DataTreeItem<Object> createDeckTreeItem(Deck deck) {
        String cleanName = deck.getName() == null ? "" : deck.getName();
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
        decksAndCollectionsTreeView.addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                buildMiddlePaneEmptySpaceFilter());
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

    //TODO WTF, there is logic from deck import ! The deck should already be imported, just use the decks that are in the Collection !
    private DataTreeItem<Object> createThemeCollectionTreeItem(ThemeCollection collection, TabType tabType) {
        String cleanName = collection.getName() == null ? "" : collection.getName();
        DataTreeItem<Object> collectionItem = new DataTreeItem<>(cleanName, collection);
        collectionItem.setExpanded(true);

        if (collection.getCardsList() != null && !collection.getCardsList().isEmpty()) {
            // Use the model list directly — no copy — so the GridView observes the real list
            // and any removal from tc.cardsList is immediately reflected in the view.
            CardsGroup group = new CardsGroup("Cards", collection.getCardsList());
            DataTreeItem<Object> groupItem = new DataTreeItem<>("Cards", group);
            groupItem.setExpanded(true);
            collectionItem.getChildren().add(groupItem);
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
            List<CardElement> exceptions = collection.getExceptionsToNotAdd();
            if (exceptions != null && !exceptions.isEmpty()) {
                CardsGroup exceptionsGroup = new CardsGroup("Cards not to add", exceptions);
                DataTreeItem<Object> exceptionsNode = new DataTreeItem<>("Cards not to add", exceptionsGroup);
                exceptionsNode.setExpanded(true);
                collectionItem.getChildren().add(exceptionsNode);
            }
        }

        return collectionItem;
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
                    collectionNavItem.setUserData(collection);
                    attachNavItemDropHandlers(collectionNavItem, collection);
                    if (UserInterfaceFunctions.isDirty(collection)) {
                        collectionNavItem.getLabel().setText("* " + sanitize(collection.getName()));
                        collectionNavItem.getLabel().setStyle(
                                "-fx-text-fill: #cdfc04; -fx-font-weight: bold;");
                    }

                    // highlight (unchanged)
                    boolean hasMissing = collectionHasMissing(collection);
                    applyNavigationItemHighlight(collectionNavItem, hasMissing);

                    // navigation wiring (unchanged)
                    collectionNavItem.setOnLabelClicked(evt -> navigateToTree(decksAndCollectionsTreeView, collection.getName()));

                    collectionNavItem.setExpanded(false);

                    // --- NEW: context menu for Collection items ---
                    {
                        ContextMenu collCm = NavigationContextMenuBuilder.forDecksCollection(collection, decksCollection);
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
                                deckSubItem.setUserData(linkedDeck);
                                attachNavItemDropHandlers(deckSubItem, linkedDeck);
                                if (UserInterfaceFunctions.isDirty(linkedDeck)) {
                                    deckSubItem.getLabel().setText("* " + sanitize(linkedDeck.getName()));
                                    deckSubItem.getLabel().setStyle(
                                            "-fx-text-fill: #cdfc04; -fx-font-weight: bold;");
                                }

                                // navigation wiring (unchanged)
                                deckSubItem.setOnLabelClicked(evt -> navigateToTree(decksAndCollectionsTreeView, collection.getName(), "Decks", linkedDeck.getName()));

                                // --- NEW: context menu for Deck items (inside a Collection) ---
                                {
                                    ContextMenu deckCm = NavigationContextMenuBuilder.forDecksDeck(linkedDeck, decksCollection);
                                    deckSubItem.setOnContextMenuRequested(e -> {
                                        deckCm.show(deckSubItem, e.getScreenX(), e.getScreenY());
                                        e.consume();
                                    });
                                }

                                collectionNavItem.addSubItem(deckSubItem);
                                // Linked deck items
                                deckSubItem.setOnLabelClicked(evt -> {
                                    Controller.SelectionManager.setLastClickedNavigationItem(linkedDeck);
                                    navigateToTree(decksAndCollectionsTreeView, collection.getName(), "Decks", linkedDeck.getName());
                                });
                            }
                        }
                    }

                    // Collection items
                    collectionNavItem.setOnLabelClicked(evt -> {
                        Controller.SelectionManager.setLastClickedNavigationItem(collection);
                        navigateToTree(decksAndCollectionsTreeView, collection.getName());
                    });
                }
            }

            if (decksCollection.getDecks() != null) {
                for (Deck deck : decksCollection.getDecks()) {
                    NavigationItem navItem = createNavigationItem(deck.getName(), 0);
                    navItem.setUserData(deck);
                    attachNavItemDropHandlers(navItem, deck);
                    if (UserInterfaceFunctions.isDirty(deck)) {
                        navItem.getLabel().setText("* " + sanitize(deck.getName()));
                        navItem.getLabel().setStyle(
                                "-fx-text-fill: #cdfc04; -fx-font-weight: bold;");
                    }

                    // navigation wiring (unchanged)
                    navItem.setOnLabelClicked(evt -> navigateToTree(decksAndCollectionsTreeView, deck.getName()));

                    // --- NEW: context menu for standalone Deck items ---
                    {
                        ContextMenu deckCm = NavigationContextMenuBuilder.forDecksDeck(deck, decksCollection);
                        navItem.setOnContextMenuRequested(e -> {
                            deckCm.show(navItem, e.getScreenX(), e.getScreenY());
                            e.consume();
                        });
                    }

                    navigationMenu.addItem(navItem);
// Standalone deck items
                    navItem.setOnLabelClicked(evt -> {
                        Controller.SelectionManager.setLastClickedNavigationItem(deck);
                        navigateToTree(decksAndCollectionsTreeView, deck.getName());
                    });
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
        // Do NOT strip here — callers pass already-clean names.
        // Stripping here would remove legitimate hyphens inside names.
        NavigationItem item = new NavigationItem(name, depth);
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

        // The OuicheList is already stored as unique-card → CardElement / count maps;
        // no local rebuilding needed.
        java.util.Map<String, CardElement> uniqueCards = Model.CardsLists.OuicheList.getMaOuicheList();
        java.util.Map<String, Integer> cardCounts = Model.CardsLists.OuicheList.getMaOuicheListCounts();

        if (uniqueCards == null || uniqueCards.isEmpty()) {
            javafx.scene.control.Label empty = new javafx.scene.control.Label("OuicheList is empty.");
            empty.setStyle("-fx-text-fill: white;");
            contentPane.getChildren().add(empty);
            return;
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

    /**
     * Scrolls just enough so the bottom of the target group's cell (= the newly added card)
     * is visible. Does nothing if it is already visible or if the group is above the viewport.
     */
    private void scrollToLastCardInGroup(String handlerTarget) {
        if (myCollectionTreeView == null || handlerTarget == null) return;

        String[] parts = handlerTarget.split("\\s*/\\s*");
        TreeItem<String> root = myCollectionTreeView.getRoot();
        if (root == null) return;

        // Locate the group TreeItem
        TreeItem<String> target = findTreeItemByPath(root, parts, 0);
        if (target == null && parts.length > 1)
            target = findTreeItemByPath(root, new String[]{parts[parts.length - 1]}, 0);
        if (target == null)
            target = findTreeItemByPath(root, new String[]{parts[0]}, 0);
        if (target == null) return;

        // If we landed on a Box, descend to its first CardsGroup child
        if (target instanceof DataTreeItem
                && !(((DataTreeItem<?>) target).getData() instanceof CardsGroup)) {
            for (TreeItem<String> child : target.getChildren()) {
                if (child instanceof DataTreeItem
                        && ((DataTreeItem<?>) child).getData() instanceof CardsGroup) {
                    target = child;
                    break;
                }
            }
        }

        for (TreeItem<String> a = target.getParent(); a != null; a = a.getParent())
            a.setExpanded(true);

        final int targetRow = myCollectionTreeView.getRow(target);
        if (targetRow < 0) return;

        javafx.scene.control.skin.VirtualFlow<?> vf = getVirtualFlow();
        int firstVisible = vf != null && vf.getFirstVisibleCell() != null
                ? vf.getFirstVisibleCell().getIndex() : -1;
        int lastVisible = vf != null && vf.getLastVisibleCell() != null
                ? vf.getLastVisibleCell().getIndex() : -1;

        if (firstVisible >= 0 && targetRow >= firstVisible && targetRow <= lastVisible) {
            // Row is in viewport — check whether the cell bottom (last card) is visible
            adjustScrollToShowCellBottom(targetRow);
        } else if (lastVisible >= 0 && targetRow > lastVisible) {
            // Row is below the viewport — bring it in, then fine-tune to cell bottom
            myCollectionTreeView.scrollTo(targetRow);
            Platform.runLater(() -> adjustScrollToShowCellBottom(targetRow));
        }
        // Row above viewport → user scrolled away; don't disturb.
    }

    /**
     * Scrolls down by exactly the amount needed to reveal the bottom edge of the rendered
     * cell at {@code row}. No-ops if the bottom is already within the viewport.
     */
    private void adjustScrollToShowCellBottom(int row) {
        if (myCollectionTreeView == null) return;

        Bounds treeBounds = myCollectionTreeView.localToScene(myCollectionTreeView.getBoundsInLocal());
        if (treeBounds == null) return;
        double treeBottom = treeBounds.getMaxY();

        for (Node node : myCollectionTreeView.lookupAll(".card-tree-cell")) {
            if (!(node instanceof TreeCell)) continue;
            @SuppressWarnings("unchecked")
            TreeCell<String> cell = (TreeCell<String>) node;
            if (cell.isEmpty() || cell.getTreeItem() == null) continue;
            if (myCollectionTreeView.getRow(cell.getTreeItem()) != row) continue;

            Bounds cellBounds = cell.localToScene(cell.getBoundsInLocal());
            if (cellBounds == null) break;

            double cellBottom = cellBounds.getMaxY();
            if (cellBottom > treeBottom) {
                javafx.scene.control.skin.VirtualFlow<?> vf = getVirtualFlow();
                if (vf != null) vf.scrollPixels(cellBottom - treeBottom);
            }
            break;
        }
    }

    private javafx.scene.control.skin.VirtualFlow<?> getVirtualFlow() {
        if (myCollectionTreeView == null) return null;
        try {
            for (Node n : myCollectionTreeView.lookupAll(".virtual-flow")) {
                if (n instanceof javafx.scene.control.skin.VirtualFlow)
                    return (javafx.scene.control.skin.VirtualFlow<?>) n;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * After a deck move, scrolls the Decks tree so the deck's node is visible,
     * and scrolls the nav menu so the deck's NavigationItem is visible.
     */
    private void scrollToMovedDeck(Object deckObj) {
        if (!(deckObj instanceof Deck)) return;
        Deck deck = (Deck) deckObj;
        String deckName = deck.getName() == null ? "" : deck.getName().replaceAll("[=\\-]", "");

        // ── Scroll the main tree ─────────────────────────────────────────────
        Platform.runLater(() -> {
            if (decksAndCollectionsTreeView == null) return;
            TreeItem<String> root = decksAndCollectionsTreeView.getRoot();
            if (root == null) return;
            TreeItem<String> target = findTreeItemByPath(root, new String[]{deckName}, 0);
            if (target == null) return;
            for (TreeItem<String> a = target.getParent(); a != null; a = a.getParent())
                a.setExpanded(true);
            Platform.runLater(() -> {
                int row = decksAndCollectionsTreeView.getRow(target);
                if (row >= 0) decksAndCollectionsTreeView.scrollTo(row);
            });
        });

        // ── Scroll + expand the nav menu ─────────────────────────────────────
        Platform.runLater(() -> {
            NavigationItem navItem = findNavItemInMenuVBox(decksTab.getMenuVBox(), deckObj);
            if (navItem == null) {
                // fall back to name-based search
                navItem = findNavItemByNameInMenuVBox(decksTab.getMenuVBox(), deckName);
            }
            if (navItem != null) {
                expandNavAncestors(navItem);
                scrollNavToItem(decksTab, navItem);
            }
        });
    }

    /**
     * Name-based fallback search through the nav menu when userData identity fails.
     */
    private NavigationItem findNavItemByNameInMenuVBox(VBox menuVBox, String name) {
        if (menuVBox == null || name == null) return null;
        for (javafx.scene.Node node : menuVBox.getChildren()) {
            NavigationItem found = findNavItemByNameInNode(node, name);
            if (found != null) return found;
        }
        return null;
    }

    private NavigationItem findNavItemByNameInNode(javafx.scene.Node node, String name) {
        if (node instanceof NavigationMenu) {
            for (NavigationItem item : ((NavigationMenu) node).getItems()) {
                NavigationItem found = findNavItemByNameInItem(item, name);
                if (found != null) return found;
            }
        } else if (node instanceof NavigationItem) {
            return findNavItemByNameInItem((NavigationItem) node, name);
        }
        return null;
    }

    private NavigationItem findNavItemByNameInItem(NavigationItem item, String name) {
        if (item == null) return null;
        if (item.getLabel() != null && name.equals(item.getLabel().getText())) return item;
        for (NavigationItem sub : item.getSubItems()) {
            NavigationItem found = findNavItemByNameInItem(sub, name);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Updates the text of the My Collection and Decks & Collections tabs
     * to show a "*" prefix when unsaved changes exist.
     */
    private void updateTabDirtyIndicators() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateTabDirtyIndicators);
            return;
        }
        if (mainTabPane == null) return;

        // My Collection
        if (myCollectionTabHandle != null) {
            boolean dirty = UserInterfaceFunctions.isMyCollectionDirty();
            myCollectionTabHandle.setText(dirty ? "* My Collection" : "My Collection");
            myCollectionTabHandle.setStyle(dirty ? "-fx-font-weight: bold;" : "");
        }

        // Decks and Collections
        if (decksTabHandle != null) {
            boolean dirty = UserInterfaceFunctions.isAnyDeckOrCollectionDirty();
            decksTabHandle.setText(dirty ? "* Decks and Collections" : "Decks and Collections");
            decksTabHandle.setStyle(dirty ? "-fx-font-weight: bold;" : "");
        }

        // OuicheList
        if (ouicheListTabHandle != null) {
            boolean dirty = UserInterfaceFunctions.isOuicheListDirty();
            ouicheListTabHandle.setText(dirty ? "* OuicheList" : "OuicheList");
            ouicheListTabHandle.setStyle(dirty ? "-fx-font-weight: bold;" : "");
        }
    }

    /**
     * Event FILTER (capture phase) for the middle-pane TreeViews.
     * During the capture phase the event is never "consumed" yet, so instead of
     * checking isConsumed() we walk up from the click target looking for a node
     * marked as a card wrapper. If we find one we leave the event alone; otherwise
     * we clear the selection.
     */
    private javafx.event.EventHandler<javafx.scene.input.MouseEvent> buildMiddlePaneEmptySpaceFilter() {
        return event -> {
            if (event.isControlDown() || event.isShiftDown()) return;
            // Walk up from the exact node that was clicked
            javafx.scene.Node current = (javafx.scene.Node) event.getTarget();
            while (current != null) {
                if (Boolean.TRUE.equals(current.getProperties().get("cardWrapper"))) {
                    return; // Landed on a card — let the card's own handler process it
                }
                if (current instanceof javafx.scene.control.TreeView) break;
                current = current.getParent();
            }
            // Did not land on any card wrapper → empty space click → clear selection
            Controller.SelectionManager.clearSelection();
        };
    }
// ── Keyboard shortcuts ─────────────────────────────────────────────────────────

    /**
     * Event HANDLER (bubble phase) for the right-pane ListViews.
     * Card cells call event.consume(), so by the time the event reaches the ListView
     * it is already consumed. Empty-space clicks are not consumed, so we clear here.
     */
    private javafx.event.EventHandler<javafx.scene.input.MouseEvent> buildRightPaneEmptySpaceClearHandler() {
        return event -> {
            if (!event.isConsumed() && !event.isControlDown() && !event.isShiftDown()) {
                Controller.SelectionManager.clearSelection();
            }
        };
    }

    private void setupGlobalKeyShortcuts() {
        // Register on the scene once it becomes available
        mainTabPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(
                        javafx.scene.input.KeyEvent.KEY_PRESSED,
                        this::handleGlobalKeyShortcut);
            }
        });
    }

// ── ESC / Delete ───────────────────────────────────────────────────────────────

    private void handleGlobalKeyShortcut(javafx.scene.input.KeyEvent event) {
        if (event.getTarget() instanceof javafx.scene.control.TextInputControl) return;

        boolean middleSelectionActive =
                "MIDDLE".equals(Controller.SelectionManager.getActivePart())
                        && !Controller.SelectionManager.getSelectedMiddleElements().isEmpty();
        boolean anySelectionActive =
                !Controller.SelectionManager.getSelectedCards().isEmpty()
                        || !Controller.SelectionManager.getSelectedMiddleElements().isEmpty();

        switch (event.getCode()) {
            case ESCAPE:
                if (anySelectionActive) {
                    Controller.SelectionManager.clearSelection();
                    event.consume();
                }
                break;
            case DELETE:
                if (middleSelectionActive) {
                    handleDeleteMiddleSelection();
                    event.consume();
                }
                break;
            case C:
                if (event.isControlDown() && anySelectionActive) {
                    handleCopySelectionToClipboard();
                    event.consume();
                }
                break;
            case X:
                if (event.isControlDown() && middleSelectionActive) {
                    handleCutFromKeyboard();
                    event.consume();
                }
                break;
            case D:
                if (event.isControlDown() && middleSelectionActive) {
                    handleDuplicateMiddleSelection();
                    event.consume();
                }
                break;
            case V:
                if (event.isControlDown() && !Controller.CardClipboard.isEmpty()) {
                    handlePasteFromKeyboard();
                    event.consume();
                }
                break;
            default:
                break;
        }
    }

// ── CTRL+C ─────────────────────────────────────────────────────────────────────

    private void handleDeleteMiddleSelection() {
        java.util.Set<Model.CardsLists.CardElement> selectedElements =
                Controller.SelectionManager.getSelectedMiddleElements();
        if (selectedElements.isEmpty()) return;

        if (selectedElements.size() > 10) {
            boolean confirmed = View.NavigationContextMenuBuilder.confirmWithCustomMessage(
                    "Delete " + selectedElements.size() + " cards?");
            if (!confirmed) return;
        }

        int activeTabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        if (activeTabIndex == 0) {
            Controller.MenuActionHandler.handleBulkRemoveFromOwnedCollection(
                    new ArrayList<>(selectedElements));
        } else if (activeTabIndex == 1) {
            Controller.MenuActionHandler.handleBulkRemoveElementsFromDecksAndCollections(
                    new ArrayList<>(Controller.SelectionManager.getSelectedMiddleElements()));
        }
        Controller.SelectionManager.clearSelection();
    }

// ── CTRL+X ─────────────────────────────────────────────────────────────────────

    private void handleCopySelectionToClipboard() {
        if ("MIDDLE".equals(Controller.SelectionManager.getActivePart())) {
            java.util.List<Model.CardsLists.Card> cardsToCopy = new java.util.ArrayList<>();
            for (Model.CardsLists.CardElement element :
                    Controller.SelectionManager.getSelectedMiddleElements()) {
                if (element.getCard() != null) cardsToCopy.add(element.getCard());
            }
            if (!cardsToCopy.isEmpty()) Controller.CardClipboard.copyCards(cardsToCopy);
        } else {
            java.util.Set<Model.CardsLists.Card> selectedCards =
                    Controller.SelectionManager.getSelectedCards();
            if (!selectedCards.isEmpty())
                Controller.CardClipboard.copyCards(new ArrayList<>(selectedCards));
        }
    }

// ── CTRL+D ─────────────────────────────────────────────────────────────────────

    private void handleCutFromKeyboard() {
        handleCopySelectionToClipboard();
        handleDeleteMiddleSelection(); // already handles confirmation for >10 cards
    }

// ── CTRL+V ─────────────────────────────────────────────────────────────────────

    private void handleDuplicateMiddleSelection() {
        TreeView<String> activeTreeView = getActiveMiddleTreeView();
        if (activeTreeView == null) return;

        java.util.Set<Model.CardsLists.CardElement> selectedElements =
                Controller.SelectionManager.getSelectedMiddleElements();
        if (selectedElements.isEmpty()) return;

        // Get selected elements in tree display order
        List<Model.CardsLists.CardElement> allElementsInOrder =
                View.CardTreeCell.collectAllElementsInTreeOrder(activeTreeView.getRoot());
        List<Model.CardsLists.CardElement> selectedInOrder = allElementsInOrder.stream()
                .filter(selectedElements::contains)
                .collect(Collectors.toList());
        if (selectedInOrder.isEmpty()) return;

        Model.CardsLists.CardElement lastElement =
                selectedInOrder.get(selectedInOrder.size() - 1);
        List<Model.CardsLists.Card> cardsToInsert = selectedInOrder.stream()
                .map(Model.CardsLists.CardElement::getCard)
                .collect(Collectors.toList());

        boolean inserted = Controller.MenuActionHandler.handleInsertCardsAfterElement(
                cardsToInsert, lastElement);
        if (!inserted) {
            logger.warn("handleDuplicateMiddleSelection: insertion failed");
            return;
        }

        int activeTabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        if (activeTabIndex == 0) {
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshOwnedCollectionView();
        } else if (activeTabIndex == 1) {
            Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
        }
    }

// ── Navigation-item drag-drop ──────────────────────────────────────────────

    private void handlePasteFromKeyboard() {
        if (Controller.CardClipboard.isEmpty()) return;
        List<Model.CardsLists.Card> clipboardCards = Controller.CardClipboard.getContents();

        // Priority 1: after the last element of the current MIDDLE selection
        if ("MIDDLE".equals(Controller.SelectionManager.getActivePart())
                && !Controller.SelectionManager.getSelectedMiddleElements().isEmpty()) {
            TreeView<String> activeTreeView = getActiveMiddleTreeView();
            if (activeTreeView != null) {
                List<Model.CardsLists.CardElement> allElementsInOrder =
                        View.CardTreeCell.collectAllElementsInTreeOrder(activeTreeView.getRoot());
                java.util.Set<Model.CardsLists.CardElement> selectedElements =
                        Controller.SelectionManager.getSelectedMiddleElements();
                // Walk backwards to find the last selected element in display order
                Model.CardsLists.CardElement lastElement = null;
                for (int i = allElementsInOrder.size() - 1; i >= 0; i--) {
                    if (selectedElements.contains(allElementsInOrder.get(i))) {
                        lastElement = allElementsInOrder.get(i);
                        break;
                    }
                }
                if (lastElement != null && pasteCardsAfterElement(clipboardCards, lastElement)) {
                    return;
                }
            }
        }

        // Priority 2: after the last MIDDLE element that was explicitly clicked
        Model.CardsLists.CardElement lastMiddleElement =
                Controller.SelectionManager.getLastMiddleElement();
        if (lastMiddleElement != null
                && pasteCardsAfterElement(clipboardCards, lastMiddleElement)) {
            return;
        }

        // Priority 3: into the last clicked navigation-menu item
        Object lastNavItem = Controller.SelectionManager.getLastClickedNavigationItem();
        if (lastNavItem != null) {
            pasteCardsIntoNavigationItem(clipboardCards, lastNavItem);
        }
    }

// ── Paste helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns the backing CardElement list for the given nav-item model object.
     * Used to locate newly appended elements after a paste so they can be re-selected.
     */
    private List<Model.CardsLists.CardElement> getTargetGroupElements(Object navItem) {
        if (navItem instanceof Model.CardsLists.CardsGroup) {
            List<Model.CardsLists.CardElement> list =
                    ((Model.CardsLists.CardsGroup) navItem).getCardList();
            return list != null ? list : Collections.emptyList();
        } else if (navItem instanceof Model.CardsLists.Box) {
            Model.CardsLists.CardsGroup dg =
                    Controller.MenuActionHandler.getOrCreateDefaultGroup((Model.CardsLists.Box) navItem);
            if (dg == null) return Collections.emptyList();
            List<Model.CardsLists.CardElement> list = dg.getCardList();
            return list != null ? list : Collections.emptyList();
        } else if (navItem instanceof Model.CardsLists.Deck) {
            List<Model.CardsLists.CardElement> list =
                    ((Model.CardsLists.Deck) navItem).getMainDeck();
            return list != null ? list : Collections.emptyList();
        } else if (navItem instanceof Model.CardsLists.ThemeCollection) {
            List<Model.CardsLists.CardElement> list =
                    ((Model.CardsLists.ThemeCollection) navItem).getCardsList();
            return list != null ? list : Collections.emptyList();
        }
        return Collections.emptyList();
    }

    /**
     * Attaches drag-over and drag-dropped handlers to a NavigationItem.
     *
     * <p>RIGHT → nav: add copies of the dragged Cards into the model object.
     * <p>MIDDLE → nav: move the dragged CardElements (remove from source, add to target).
     *
     * @param navItem  the NavigationItem node
     * @param modelObj the model object stored as userData (Box, CardsGroup, Deck, ThemeCollection)
     */
    private void attachNavItemDropHandlers(NavigationItem navItem, Object modelObj) {
        navItem.setOnDragOver(event -> {
            if (event.getDragboard().hasString()
                    && Controller.DragDropManager.getDragSourcePane() != null) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            }
            event.consume();
        });

        navItem.setOnDragDropped(event -> {
            String srcPane = Controller.DragDropManager.getDragSourcePane();
            if (srcPane == null) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            if ("RIGHT".equals(srcPane)) {
                // Add copies of the dragged Cards
                java.util.List<Model.CardsLists.Card> cards =
                        new java.util.ArrayList<>(Controller.DragDropManager.getDraggedCards());
                if (!cards.isEmpty()) pasteCardsIntoNavigationItem(cards, modelObj);

            } else if ("MIDDLE".equals(srcPane)) {
                java.util.List<Model.CardsLists.CardElement> elements =
                        new java.util.ArrayList<>(Controller.DragDropManager.getDraggedElements());
                if (elements.isEmpty()) {
                    event.setDropCompleted(false);
                    event.consume();
                    return;
                }

                java.util.List<Model.CardsLists.Card> cards = new java.util.ArrayList<>();
                for (Model.CardsLists.CardElement ce : elements) {
                    if (ce.getCard() != null) cards.add(ce.getCard());
                }

                Controller.MenuActionHandler.handleBulkRemoveElementsFromDecksAndCollections(elements);
                Controller.MenuActionHandler.handleBulkRemoveFromOwnedCollection(elements);

                if (!cards.isEmpty()) {
                    final int n = cards.size();
                    pasteCardsIntoNavigationItem(cards, modelObj);
                    // Re-select the newly appended elements so selection borders follow the move
                    Platform.runLater(() -> {
                        List<Model.CardsLists.CardElement> targetElements =
                                getTargetGroupElements(modelObj);
                        int startIdx = Math.max(0, targetElements.size() - n);
                        Controller.SelectionManager.clearSelection();
                        for (int i = startIdx; i < targetElements.size(); i++) {
                            Controller.SelectionManager.toggleElementSelection(targetElements.get(i));
                        }
                        View.CardTreeCell.refreshAllGridViews();
                    });
                }
            }

            event.setDropCompleted(true);
            event.consume();
        });
    }

    /**
     * Inserts clipboardCards after targetElement.
     * Returns true if the insertion succeeded; marks dirty and refreshes only on success.
     */
    private boolean pasteCardsAfterElement(
            List<Model.CardsLists.Card> clipboardCards,
            Model.CardsLists.CardElement targetElement) {
        boolean inserted = Controller.MenuActionHandler.handleInsertCardsAfterElement(
                clipboardCards, targetElement);
        if (!inserted) return false;

        int activeTabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        if (activeTabIndex == 0) {
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshOwnedCollectionView();
        } else if (activeTabIndex == 1) {
            Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
        }
        return true;
    }

    /**
     * Returns the selected CardElements ordered by their position in the tree.
     */
    private List<Model.CardsLists.CardElement> getSelectedElementsInDisplayOrder(
            TreeView<String> treeView) {
        if (treeView == null
                || Controller.SelectionManager.getSelectedMiddleElements().isEmpty()) {
            return new ArrayList<>();
        }
        java.util.Set<Model.CardsLists.CardElement> selectedElements =
                Controller.SelectionManager.getSelectedMiddleElements();
        return View.CardTreeCell.collectAllElementsInTreeOrder(treeView.getRoot())
                .stream()
                .filter(selectedElements::contains)
                .collect(Collectors.toList());
    }

// ── Shared utilities ───────────────────────────────────────────────────────────

    private void pasteCardsIntoNavigationItem(
            List<Model.CardsLists.Card> clipboardCards, Object navItem) {
        if (navItem instanceof Model.CardsLists.Box) {
            Model.CardsLists.Box box = (Model.CardsLists.Box) navItem;
            Model.CardsLists.CardsGroup defaultGroup =
                    Controller.MenuActionHandler.getOrCreateDefaultGroup(box);
            if (defaultGroup == null) return;
            javafx.collections.ObservableList<Model.CardsLists.CardElement> observableList =
                    View.CardTreeCell.observableListFor(defaultGroup);
            for (Model.CardsLists.Card card : clipboardCards) {
                if (card != null) observableList.add(new Model.CardsLists.CardElement(card));
            }
            View.CardTreeCell.triggerHeightAdjustment(defaultGroup);
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshOwnedCollectionView();

        } else if (navItem instanceof Model.CardsLists.CardsGroup) {
            Model.CardsLists.CardsGroup group = (Model.CardsLists.CardsGroup) navItem;
            javafx.collections.ObservableList<Model.CardsLists.CardElement> observableList =
                    View.CardTreeCell.observableListFor(group);
            for (Model.CardsLists.Card card : clipboardCards) {
                if (card != null) observableList.add(new Model.CardsLists.CardElement(card));
            }
            View.CardTreeCell.triggerHeightAdjustment(group);
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshOwnedCollectionView();

        } else if (navItem instanceof Model.CardsLists.Deck) {
            Model.CardsLists.Deck deck = (Model.CardsLists.Deck) navItem;
            for (Model.CardsLists.Card card : clipboardCards) {
                if (card != null) deck.getMainDeck().add(new Model.CardsLists.CardElement(card));
            }
            Controller.UserInterfaceFunctions.markDirty(deck);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();

        } else if (navItem instanceof Model.CardsLists.ThemeCollection) {
            Model.CardsLists.ThemeCollection collection =
                    (Model.CardsLists.ThemeCollection) navItem;
            if (collection.getCardsList() == null)
                collection.setCardsList(new ArrayList<>());
            for (Model.CardsLists.Card card : clipboardCards) {
                if (card != null)
                    collection.getCardsList().add(new Model.CardsLists.CardElement(card));
            }
            Controller.UserInterfaceFunctions.markDirty(collection);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
        }
    }

    private TreeView<String> getActiveMiddleTreeView() {
        if (mainTabPane == null) return null;
        int activeTabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        switch (activeTabIndex) {
            case 0:
                return myCollectionTreeView;
            case 1:
                return decksAndCollectionsTreeView;
            case 2:
                return ouicheTreeView;
            case 3:
                return archetypesTreeView;
            default:
                return null;
        }
    }

    /**
     * Returns only the selected cards, filtered and ordered by their position in the tree.
     */
    private List<Model.CardsLists.Card> getSelectedCardsInDisplayOrder(
            TreeView<String> treeView) {
        if (treeView == null || Controller.SelectionManager.getSelectedCards().isEmpty())
            return new ArrayList<>();
        java.util.Set<Model.CardsLists.Card> selectedCards =
                Controller.SelectionManager.getSelectedCards();
        List<Model.CardsLists.Card> allCardsInTreeOrder =
                View.CardTreeCell.collectAllCardsInTreeOrder(treeView.getRoot());
        return allCardsInTreeOrder.stream()
                .filter(selectedCards::contains)
                .collect(Collectors.toList());
    }

    /**
     * Finds the first CardElement that matches the given Card, searching first the
     * owned collection and then the Decks & Collections list.
     */
    private Model.CardsLists.CardElement findCardElementForCard(Model.CardsLists.Card card) {
        if (card == null) return null;
        // Try owned collection
        List<Model.CardsLists.CardElement> ownedMatches =
                Controller.MenuActionHandler.findCardElementsForCards(
                        java.util.Collections.singletonList(card));
        if (!ownedMatches.isEmpty()) return ownedMatches.get(0);
        // Try D&C
        Model.CardsLists.DecksAndCollectionsList dac =
                Controller.UserInterfaceFunctions.getDecksList();
        if (dac != null) {
            Model.CardsLists.CardElement dacMatch = findCardElementInDac(card, dac);
            if (dacMatch != null) return dacMatch;
        }
        return null;
    }

    private Model.CardsLists.CardElement findCardElementInDac(
            Model.CardsLists.Card card, Model.CardsLists.DecksAndCollectionsList dac) {
        if (dac.getCollections() != null) {
            for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                if (tc == null) continue;
                if (tc.getCardsList() != null) {
                    for (Model.CardsLists.CardElement ce : tc.getCardsList()) {
                        if (ce != null && cardsMatchLoose(ce.getCard(), card)) return ce;
                    }
                }
                if (tc.getLinkedDecks() != null) {
                    for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                        if (unit == null) continue;
                        for (Model.CardsLists.Deck deck : unit) {
                            Model.CardsLists.CardElement found =
                                    findCardElementInDeckLists(card, deck);
                            if (found != null) return found;
                        }
                    }
                }
            }
        }
        if (dac.getDecks() != null) {
            for (Model.CardsLists.Deck deck : dac.getDecks()) {
                Model.CardsLists.CardElement found = findCardElementInDeckLists(card, deck);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Model.CardsLists.CardElement findCardElementInDeckLists(
            Model.CardsLists.Card card, Model.CardsLists.Deck deck) {
        if (deck == null) return null;
        for (List<Model.CardsLists.CardElement> deckList : java.util.Arrays.asList(
                deck.getMainDeck(), deck.getExtraDeck(), deck.getSideDeck())) {
            if (deckList == null) continue;
            for (Model.CardsLists.CardElement ce : deckList) {
                if (ce != null && cardsMatchLoose(ce.getCard(), card)) return ce;
            }
        }
        return null;
    }
}