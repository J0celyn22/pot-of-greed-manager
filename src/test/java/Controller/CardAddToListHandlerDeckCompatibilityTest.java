package Controller;

import Model.CardsLists.Card;
import Model.CardsLists.Deck;
import Model.CardsLists.DecksAndCollectionsList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link CardAddToListHandler#doAddToDeck}, the core of the "Add to Deck"
 * context-menu action (reachable from All Existing Cards, Archetypes, and any card-menu
 * "Add to..." submenu), plus the multi-select loop in {@link CardBulkAddHandler#handleBulkAddToDeck}
 * that repeatedly calls it.
 *
 * <h2>Why calls are wrapped in try/catch</h2>
 * {@code doAddToDeck} mutates the target deck list first, then — as its very last step —
 * calls {@link UserInterfaceFunctions#triggerTabDirtyIndicatorUpdate()}, which unconditionally
 * calls {@code Platform.runLater(...)}. In this plain JUnit run there is no JavaFX Application
 * Thread, so that call throws {@code IllegalStateException: Toolkit not initialized}. Because
 * the throw happens strictly after the list mutation we're asserting on, it's safe to swallow
 * it and inspect the deck afterward — the same tolerance the codebase's own
 * {@code CardGroupRegistryOuicheListSyncTest} documents for this exact constraint.
 *
 * <h2>Partition coverage</h2>
 * <ul>
 *   <li>Single card, target matches card type (Main/Extra/Side) — the case the context menu
 *       is gated to only ever offer for a single-card selection.</li>
 *   <li>Single card, target does <b>not</b> match card type — unreachable for a genuine
 *       single-card selection (the menu wouldn't offer it), but this is <em>exactly</em> what
 *       happens to every non-primary card when {@link CardBulkAddHandler#handleBulkAddToDeck}
 *       loops a mixed multi-select over one fixed target, since the menu is gated only by the
 *       single right-clicked cell (see {@code CardGridCell.moveSectionAllowed}).</li>
 *   <li>Bulk add of a mixed-type selection to one explicit section — reproduces the production
 *       loop exactly (it has no logic beyond the loop, see {@code CardBulkAddHandler}'s class
 *       javadoc), and demonstrates the cross-contamination bug directly.</li>
 * </ul>
 */
class CardAddToListHandlerDeckCompatibilityTest {

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
     * Registers {@code deck} as the sole standalone deck of a fresh DecksAndCollectionsList.
     */
    private static Deck registerStandaloneDeck(String deckName) {
        Deck deck = new Deck();
        deck.setName(deckName);
        DecksAndCollectionsList dac = new DecksAndCollectionsList();
        dac.setDecks(new ArrayList<>(List.of(deck)));
        UserInterfaceFunctions.setDecksList(dac);
        return deck;
    }

    /**
     * Calls doAddToDeck, tolerating the FX-toolkit-not-initialized failure — see class javadoc.
     */
    private static void addToDeck(Card card, String handlerTarget) {
        try {
            CardAddToListHandler.doAddToDeck(card, handlerTarget);
        } catch (Throwable ignored) {
            // Expected in this headless test JVM; the list mutation already happened.
        }
    }

    /**
     * Faithfully mirrors {@link CardBulkAddHandler#handleBulkAddToDeck}'s loop body — that
     * method contains no logic beyond {@code for (Card card : cards) doAddToDeck(card, target)}
     * (see its class javadoc), so replaying the loop here exercises the exact same behavior
     * without going through the Platform-dispatch wrapper.
     */
    private static void bulkAddToDeck(List<Card> cards, String handlerTarget) {
        try {
            for (Card card : cards) {
                CardAddToListHandler.doAddToDeck(card, handlerTarget);
            }
        } catch (Throwable ignored) {
            // Same FX-toolkit caveat as addToDeck().
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

    // ── Single card, correctly targeted (what the context menu actually offers) ──

    @Test
    void singleMainDeckCard_addedToMainDeck_landsInMainDeck() {
        Deck deck = registerStandaloneDeck("TestDeck");
        addToDeck(mainDeckMonster("Main Card"), "TestDeck / Main Deck");

        assertEquals(1, deck.getMainDeck().size());
        assertTrue(deck.getExtraDeck().isEmpty());
        assertEquals("Main Card", deck.getMainDeck().get(0).getCard().getName_EN());
    }

    @Test
    void singleExtraDeckCard_addedToExtraDeck_landsInExtraDeck() {
        Deck deck = registerStandaloneDeck("TestDeck");
        addToDeck(extraDeckMonster("Extra Card"), "TestDeck / Extra Deck");

        assertEquals(1, deck.getExtraDeck().size());
        assertTrue(deck.getMainDeck().isEmpty());
    }

    @Test
    void anyCard_addedToSideDeck_landsInSideDeckRegardlessOfType() {
        Deck deck = registerStandaloneDeck("TestDeck");
        addToDeck(extraDeckMonster("Extra Card"), "TestDeck / Side Deck");
        addToDeck(mainDeckMonster("Main Card"), "TestDeck / Side Deck");

        assertEquals(2, deck.getSideDeck().size());
        assertTrue(deck.getMainDeck().isEmpty());
        assertTrue(deck.getExtraDeck().isEmpty());
    }

    // ── Single card, mismatched target — documents the missing redirect ─────────
    // These model exactly what happens to a non-primary card inside a mixed bulk
    // selection (see bulk tests below). Per the stated product rule ("a card should
    // land in the section matching its type no matter how it's added"), these should
    // redirect. doAddToDeck currently never calls Utils.DeckCompatibility at all, so
    // both of these FAIL against current behavior — that failure is the point.

    @Test
    void extraDeckCard_addedToMainDeckTarget_shouldRedirectToExtraDeck() {
        Deck deck = registerStandaloneDeck("TestDeck");
        addToDeck(extraDeckMonster("Extra Card"), "TestDeck / Main Deck");

        assertEquals(0, deck.getMainDeck().size(),
                "Extra-deck card must not stay in the Main Deck");
        assertEquals(1, deck.getExtraDeck().size(),
                "Extra-deck card should have been redirected to the Extra Deck");
    }

    @Test
    void mainDeckCard_addedToExtraDeckTarget_shouldRedirectToMainDeck() {
        Deck deck = registerStandaloneDeck("TestDeck");
        addToDeck(mainDeckMonster("Main Card"), "TestDeck / Extra Deck");

        assertEquals(0, deck.getExtraDeck().size(),
                "Main-deck card must not stay in the Extra Deck");
        assertEquals(1, deck.getMainDeck().size(),
                "Main-deck card should have been redirected to the Main Deck");
    }

    // ── Bulk add of a mixed selection — the actual real-world bug trigger ───────

    @Test
    void bulkAdd_mixedSelectionTargetingMainDeck_extraCardShouldNotEndUpInMainDeck() {
        Deck deck = registerStandaloneDeck("TestDeck");
        List<Card> selection = List.of(
                mainDeckMonster("Main Card 1"),
                extraDeckMonster("Extra Card"),
                mainDeckMonster("Main Card 2"));

        // Simulates: user multi-selects these 3 cards, right-clicks the first (a Main
        // Deck card), and picks "Add to Deck > TestDeck > Main Deck" — the only target
        // the menu shows them, since the gate looks only at the right-clicked cell.
        bulkAddToDeck(selection, "TestDeck / Main Deck");

        assertEquals(2, deck.getMainDeck().size(), "the two main-deck cards belong here");
        assertEquals(1, deck.getExtraDeck().size(),
                "the extra-deck card should have been redirected out of the Main Deck");
        assertEquals(0, deck.getMainDeck().stream()
                        .filter(e -> e.getCard().getName_EN().equals("Extra Card"))
                        .count(),
                "the extra-deck card must not remain in the Main Deck list");
    }

    @Test
    void bulkAdd_mixedSelectionTargetingExtraDeck_mainCardShouldNotEndUpInExtraDeck() {
        Deck deck = registerStandaloneDeck("TestDeck");
        List<Card> selection = List.of(
                extraDeckMonster("Extra Card 1"),
                mainDeckMonster("Main Card"),
                extraDeckMonster("Extra Card 2"));

        bulkAddToDeck(selection, "TestDeck / Extra Deck");

        assertEquals(2, deck.getExtraDeck().size());
        assertEquals(1, deck.getMainDeck().size(),
                "the main-deck card should have been redirected out of the Extra Deck");
    }

    @Test
    void bulkAdd_mixedSelectionTargetingSideDeck_allCardsLandInSideDeckRegardlessOfType() {
        Deck deck = registerStandaloneDeck("TestDeck");
        List<Card> selection = List.of(
                extraDeckMonster("Extra Card"),
                mainDeckMonster("Main Card"));

        bulkAddToDeck(selection, "TestDeck / Side Deck");

        assertEquals(2, deck.getSideDeck().size());
        assertTrue(deck.getMainDeck().isEmpty());
        assertTrue(deck.getExtraDeck().isEmpty());
    }
}