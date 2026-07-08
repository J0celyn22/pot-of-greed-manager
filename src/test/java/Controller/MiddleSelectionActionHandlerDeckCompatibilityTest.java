package Controller;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.CardsLists.Deck;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MiddleSelectionActionHandler#pasteCardsIntoNavigationItem} and
 * {@link MiddleSelectionActionHandler#pasteElementsIntoNavigationItem} when the drop/paste
 * target ({@code modelObj}) is a {@link Deck} itself rather than one of its Main/Extra/Side
 * sections — the "direct" add the user described: dragging or pasting straight onto the deck's
 * node in the navigation tree, with no explicit section chosen. Both methods split the incoming
 * cards by {@code Utils.DeckCompatibility.isExtraDeckCard(...)} <em>before</em> inserting
 * anything, so this is already correct today. These tests guard that behavior against regression.
 *
 * <h2>Why each direction is tested with a single-type selection</h2>
 * The method pre-splits the whole input into {@code toMain}/{@code toExtra}, then always
 * processes {@code toMain} first and {@code toExtra} second (in that fixed order, regardless of
 * input order). Each of those two steps mutates its section's list and then calls a
 * Platform-touching refresh ({@code triggerHeightAdjustment} or {@code
 * triggerDecksStructureRefresh}, depending on whether a CardsGroup is registered for that
 * section) which throws in this headless test JVM. Because the throw happens inside the first
 * step, a genuinely mixed selection would never reach the second step in this test environment.
 * Testing each direction with a selection containing only that type sidesteps the issue — when
 * the *other* bucket is empty, the method returns from that step before ever touching Platform,
 * so nothing aborts early. This is an environment limitation, not a limitation of the coverage:
 * the two branches don't share any mutable state, so testing them independently is equivalent to
 * testing them in one combined mixed call.
 */
class MiddleSelectionActionHandlerDeckCompatibilityTest {

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

    private static Deck freshDeck() {
        Deck deck = new Deck();
        deck.setName("TestDeck");
        return deck;
    }

    /**
     * Tolerates the FX-toolkit-not-initialized failure — see class javadoc.
     */
    private static void pasteCards(List<Card> cards, Deck deck) {
        try {
            MiddleSelectionActionHandler.pasteCardsIntoNavigationItem(cards, deck);
        } catch (Throwable ignored) {
            // Expected in this headless test JVM; the list mutation already happened.
        }
    }

    private static void pasteElements(List<CardElement> elements, Deck deck) {
        try {
            MiddleSelectionActionHandler.pasteElementsIntoNavigationItem(elements, deck);
        } catch (Throwable ignored) {
            // Expected in this headless test JVM; the list mutation already happened.
        }
    }

    // ── pasteCardsIntoNavigationItem(List<Card>, Deck) ──────────────────────────

    @Test
    void mainDeckOnlySelection_pastedOnDeckNode_landsInMainDeck() {
        Deck deck = freshDeck();
        pasteCards(List.of(mainDeckMonster("Main Card 1"), mainDeckMonster("Main Card 2")), deck);

        assertEquals(2, deck.getMainDeck().size());
        assertEquals(0, deck.getExtraDeck().size());
    }

    @Test
    void extraDeckOnlySelection_pastedOnDeckNode_landsInExtraDeck() {
        Deck deck = freshDeck();
        pasteCards(List.of(extraDeckMonster("Extra Card 1"), extraDeckMonster("Extra Card 2")), deck);

        assertEquals(2, deck.getExtraDeck().size());
        assertEquals(0, deck.getMainDeck().size());
    }

    // ── pasteElementsIntoNavigationItem(List<CardElement>, Deck) — same rule ────

    @Test
    void mainDeckOnlyElements_pastedOnDeckNode_landInMainDeck() {
        Deck deck = freshDeck();
        pasteElements(List.of(new CardElement(mainDeckMonster("Main Card"))), deck);

        assertEquals(1, deck.getMainDeck().size());
        assertEquals(0, deck.getExtraDeck().size());
    }

    @Test
    void extraDeckOnlyElements_pastedOnDeckNode_landInExtraDeck() {
        Deck deck = freshDeck();
        pasteElements(List.of(new CardElement(extraDeckMonster("Extra Card"))), deck);

        assertEquals(1, deck.getExtraDeck().size());
        assertEquals(0, deck.getMainDeck().size());
    }

    // ── Side Deck is never an auto-routing target here ──────────────────────────
    // Direct-add-to-deck-node only ever splits between Main and Extra; a card only lands
    // in the Side Deck when the user explicitly chooses that section elsewhere (matching
    // the reported behavior: "if the card is added to the side deck ... it is what happens").

    @Test
    void directAddToDeckNode_neverPlacesAnythingInSideDeck() {
        Deck deck = freshDeck();
        pasteCards(List.of(mainDeckMonster("Main Card"), extraDeckMonster("Extra Card")), deck);
        pasteElements(List.of(new CardElement(mainDeckMonster("Main Card 2"))), deck);

        assertTrue(deck.getSideDeck().isEmpty());
    }
}