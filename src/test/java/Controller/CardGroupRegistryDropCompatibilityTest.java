package Controller;

import Model.CardsLists.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CardGroupRegistry#dropInsertIntoGroup}, the shared insertion routine behind
 * every drag-and-drop card addition/move — both a cell-anchored drop (dragging onto an existing
 * card) and a gap drop (dragging into empty space in a grid) funnel into this same method.
 *
 * <h2>Two payload shapes exercised here</h2>
 * <ul>
 *   <li><b>ADD</b> ({@code sourceCards}) — dragging from a read-only source (All Existing Cards,
 *       an Archetype tab) where fresh {@link CardElement} wrappers are created.</li>
 *   <li><b>MOVE</b> ({@code sourceElements}) — dragging an existing owned {@link CardElement},
 *       e.g. reordering within a deck or moving a card from one deck section to another.</li>
 * </ul>
 *
 * <h2>Why calls are wrapped in try/catch, and why MOVE elements are never pre-registered</h2>
 * The mutation of the target list always completes before {@code dropInsertIntoGroup} calls
 * {@code triggerHeightAdjustment}/{@code notifyOuicheListOfGroupAdditions}, both of which touch
 * {@code Platform.runLater} unconditionally and throw in this headless test JVM — so wrapping the
 * call and asserting on the deck afterward is safe, exactly as in the other test classes in this
 * suite. The one exception is the MOVE branch's <i>removal</i> loop, which calls
 * {@code triggerHeightAdjustment(sourceGroup)} once per element <i>during</i> the loop — if that
 * throws on the first element, later elements in the same call are never processed. This test
 * suite sidesteps that by passing "loose" {@link CardElement}s that were never registered via
 * {@code CardGroupRegistry.observableListFor} on any group, so the removal loop never finds a
 * match for them and never reaches that call. This only affects how the fixtures are built, not
 * the insertion behavior being verified.
 */
class CardGroupRegistryDropCompatibilityTest {

    private DecksAndCollectionsList originalDecksList;

    private static Card cardWithProperties(String name, String... properties) {
        Card card = new Card();
        card.setName_EN(name);
        card.setCardProperties(new ArrayList<>(List.of(properties)));
        return card;
    }

    private static Card mainDeckMonster(String name) {
        return cardWithProperties(name, "Effect");
    }

    private static Card extraDeckMonster(String name) {
        return cardWithProperties(name, "Effect", "Fusion");
    }

    /**
     * Builds a standalone Deck, registers its three sections as CardsGroups in the registry
     * (as the real navigation-tree wiring does), and points UserInterfaceFunctions at it so
     * {@code findDeckOwnerForGroup}/{@code findOwnerForGroup} can resolve it.
     */
    private static Deck deckWithRegisteredSections(String deckName) {
        Deck deck = new Deck();
        deck.setName(deckName);
        CardGroupRegistry.getOrCreateDeckSectionGroup(deck, "main", "Main Deck", deck.getMainDeck());
        CardGroupRegistry.getOrCreateDeckSectionGroup(deck, "extra", "Extra Deck", deck.getExtraDeck());
        CardGroupRegistry.getOrCreateDeckSectionGroup(deck, "side", "Side Deck", deck.getSideDeck());

        DecksAndCollectionsList dac = new DecksAndCollectionsList();
        dac.setDecks(new ArrayList<>(List.of(deck)));
        UserInterfaceFunctions.setDecksList(dac);
        return deck;
    }

    /**
     * Calls dropInsertIntoGroup, tolerating the FX-toolkit-not-initialized failure.
     */
    private static void drop(CardsGroup targetGroup, List<CardElement> sourceElements,
                             List<Card> sourceCards) {
        try {
            CardGroupRegistry.dropInsertIntoGroup(targetGroup, Integer.MAX_VALUE,
                    sourceElements, sourceCards);
        } catch (Throwable ignored) {
            // Expected in this headless test JVM; the list mutation already happened.
        }
    }

    @BeforeEach
    void saveDecksList() {
        originalDecksList = UserInterfaceFunctions.getDecksList();
    }

    @AfterEach
    void restoreDecksList() {
        UserInterfaceFunctions.setDecksList(originalDecksList);
    }

    // ── ADD (sourceCards): single card — already correctly redirected today ─────

    @Test
    void add_singleExtraDeckCard_droppedOnMainDeck_redirectsToExtraDeck() {
        Deck deck = deckWithRegisteredSections("TestDeck");
        CardsGroup mainGroup = CardGroupRegistry.getDeckSectionGroup(deck, "main");

        drop(mainGroup, null, List.of(extraDeckMonster("Extra Card")));

        assertEquals(0, deck.getMainDeck().size());
        assertEquals(1, deck.getExtraDeck().size());
    }

    @Test
    void add_singleMainDeckCard_droppedOnExtraDeck_redirectsToMainDeck() {
        Deck deck = deckWithRegisteredSections("TestDeck");
        CardsGroup extraGroup = CardGroupRegistry.getDeckSectionGroup(deck, "extra");

        drop(extraGroup, null, List.of(mainDeckMonster("Main Card")));

        assertEquals(0, deck.getExtraDeck().size());
        assertEquals(1, deck.getMainDeck().size());
    }

    // ── ADD (sourceCards): mixed multi-card drag — the actual bug ───────────────
    // resolveCompatibleTargetGroup inspects only the FIRST non-null card in the payload
    // ("sampleCard") and redirects (or doesn't) the ENTIRE batch based on that one card
    // alone. Both tests below assert the correct per-card outcome, and both currently
    // FAIL: whichever card happens to be first decides the fate of the whole selection.

    @Test
    void add_mixedMultiCardDrag_firstCardMainDeckCompatible_extraCardStillEndsUpWrong() {
        Deck deck = deckWithRegisteredSections("TestDeck");
        CardsGroup mainGroup = CardGroupRegistry.getDeckSectionGroup(deck, "main");

        // First card in the payload is Main-Deck-compatible, so resolveCompatibleTargetGroup
        // decides "no redirect needed" for the whole batch — even though the second card isn't.
        List<Card> mixedSelection = List.of(
                mainDeckMonster("Main Card"),
                extraDeckMonster("Extra Card"));

        drop(mainGroup, null, mixedSelection);

        assertEquals(1, deck.getMainDeck().size(), "the main-deck card belongs here");
        assertEquals(1, deck.getExtraDeck().size(),
                "the extra-deck card should have been redirected, not left in the Main Deck");
    }

    @Test
    void add_mixedMultiCardDrag_firstCardExtraDeckCompatible_mainCardIncorrectlyRedirectedToo() {
        Deck deck = deckWithRegisteredSections("TestDeck");
        CardsGroup mainGroup = CardGroupRegistry.getDeckSectionGroup(deck, "main");

        // First card in the payload is Extra-Deck-only, so resolveCompatibleTargetGroup
        // redirects the WHOLE batch to the Extra Deck — including the main-deck card that
        // was actually correctly targeted at the Main Deck to begin with.
        List<Card> mixedSelection = List.of(
                extraDeckMonster("Extra Card"),
                mainDeckMonster("Main Card"));

        drop(mainGroup, null, mixedSelection);

        assertEquals(1, deck.getMainDeck().size(),
                "the main-deck card was dropped on the Main Deck and should stay there");
        assertEquals(1, deck.getExtraDeck().size(), "the extra-deck card belongs here");
    }

    // ── MOVE (sourceElements): same defect, existing-card reordering/moving ─────

    @Test
    void move_mixedMultiElementDrag_firstElementMainDeckCompatible_extraElementStillEndsUpWrong() {
        Deck deck = deckWithRegisteredSections("TestDeck");
        CardsGroup mainGroup = CardGroupRegistry.getDeckSectionGroup(deck, "main");

        // "Loose" elements: never registered via observableListFor on any source group, so the
        // MOVE branch's per-element removal loop never matches them (see class javadoc).
        CardElement mainElement = new CardElement(mainDeckMonster("Main Card"));
        CardElement extraElement = new CardElement(extraDeckMonster("Extra Card"));

        drop(mainGroup, List.of(mainElement, extraElement), null);

        assertEquals(1, deck.getMainDeck().size());
        assertEquals(1, deck.getExtraDeck().size(),
                "the extra-deck element should have been redirected, not left in the Main Deck");
    }

    // ── Side Deck: never redirects, regardless of type or payload size ──────────

    @Test
    void add_mixedMultiCardDrag_droppedOnSideDeck_allCardsStayInSideDeck() {
        Deck deck = deckWithRegisteredSections("TestDeck");
        CardsGroup sideGroup = CardGroupRegistry.getDeckSectionGroup(deck, "side");

        drop(sideGroup, null, List.of(
                mainDeckMonster("Main Card"),
                extraDeckMonster("Extra Card")));

        assertEquals(2, deck.getSideDeck().size());
        assertTrue(deck.getMainDeck().isEmpty());
        assertTrue(deck.getExtraDeck().isEmpty());
    }
}