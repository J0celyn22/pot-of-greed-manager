package Controller;

import Model.CardsLists.*;
import javafx.collections.ObservableList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for {@link CardPasteInsertHandler#handleInsertElementsAfterElement} and
 * {@link CardPasteInsertHandler#handleInsertCardsAfterElement} — the "paste after anchor"
 * operation used for copy/paste and duplication onto a Deck section.
 * <p>
 * Unlike {@code CardAddToListHandler.doAddToDeck} and {@code CardMoveHandler.doMove}, these
 * methods already do the right thing: they split the incoming list by
 * {@code Utils.DeckCompatibility.isCompatibleWith(...)} <em>per card</em> before inserting
 * anything, then redirect only the incompatible subset to the sibling section of the same
 * {@link Deck}. These tests exist to guard that correct behavior against regression, since it's
 * the template the other broken call sites should eventually be fixed to match.
 *
 * <h2>Why compatible/incompatible partitions are tested with single-type lists</h2>
 * The method mutates the host section first, then calls {@code triggerHeightAdjustment(hostGroup)}
 * — Platform-touching and unconditional — <em>before</em> it goes on to process any incompatible
 * (redirected) cards. In this headless test JVM that call throws, which would abort the method
 * before the redirect branch ever ran. Passing a list where every card falls in the same bucket
 * (all-compatible, or all-incompatible) keeps the relevant mutation fully complete before that
 * throw, without ever needing the two branches to run back-to-back in the same call. Since the
 * two branches operate on disjoint sublists and disjoint target groups, testing them in separate
 * calls is equivalent to testing them together. The Side Deck case has no split logic at all
 * (it's never a main/extra section), so a genuinely mixed-type list is safe there.
 */
class CardPasteInsertHandlerDeckCompatibilityTest {

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
     * Builds a Deck with all three sections registered, plants a pre-existing "anchor" card in
     * {@code anchorSectionDisplayName} (so {@code findGroupForCardElement} can locate the host
     * group the way it would for a real anchor card already sitting in the deck), and wires the
     * DecksAndCollectionsList so redirect target lookups resolve.
     */
    private static Fixture deckWithAnchorIn(String sectionKey, String sectionDisplayName) {
        Deck deck = new Deck();
        deck.setName("TestDeck");
        CardGroupRegistry.getOrCreateDeckSectionGroup(deck, "main", "Main Deck", deck.getMainDeck());
        CardGroupRegistry.getOrCreateDeckSectionGroup(deck, "extra", "Extra Deck", deck.getExtraDeck());
        CardGroupRegistry.getOrCreateDeckSectionGroup(deck, "side", "Side Deck", deck.getSideDeck());

        DecksAndCollectionsList dac = new DecksAndCollectionsList();
        dac.setDecks(new ArrayList<>(List.of(deck)));
        UserInterfaceFunctions.setDecksList(dac);

        CardsGroup hostGroup = CardGroupRegistry.getDeckSectionGroup(deck, sectionKey);
        CardElement anchor = new CardElement(cardWithProperties("Anchor Card",
                sectionKey.equals("extra") ? new String[]{"Effect", "Fusion"} : new String[]{"Effect"}));
        CardGroupRegistry.observableListFor(hostGroup).add(anchor);

        return new Fixture(deck, anchor);
    }

    /**
     * Calls handleInsertElementsAfterElement, tolerating the FX-toolkit-not-initialized failure.
     */
    private static void pasteElements(List<CardElement> toInsert, CardElement anchor) {
        try {
            CardPasteInsertHandler.handleInsertElementsAfterElement(toInsert, anchor);
        } catch (Throwable ignored) {
            // Expected in this headless test JVM — see class javadoc.
        }
    }

    private static void pasteCards(List<Card> toInsert, CardElement anchor) {
        try {
            CardPasteInsertHandler.handleInsertCardsAfterElement(toInsert, anchor);
        } catch (Throwable ignored) {
            // Expected in this headless test JVM — see class javadoc.
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

    @Test
    void allCompatibleElements_pastedOnMainDeckAnchor_allLandInMainDeck() {
        Fixture fixture = deckWithAnchorIn("main", "Main Deck");
        List<CardElement> toInsert = List.of(
                new CardElement(mainDeckMonster("Card 1")),
                new CardElement(mainDeckMonster("Card 2")));

        pasteElements(toInsert, fixture.anchor);

        // anchor + 2 pasted cards, none redirected
        assertEquals(3, fixture.deck.getMainDeck().size());
        assertEquals(0, fixture.deck.getExtraDeck().size());
    }

    // ── handleInsertElementsAfterElement (the recommended, non-deprecated overload) ──

    @Test
    void allIncompatibleElements_pastedOnMainDeckAnchor_allRedirectToExtraDeck() {
        Fixture fixture = deckWithAnchorIn("main", "Main Deck");
        List<CardElement> toInsert = List.of(
                new CardElement(extraDeckMonster("Extra Card 1")),
                new CardElement(extraDeckMonster("Extra Card 2")));

        pasteElements(toInsert, fixture.anchor);

        assertEquals(1, fixture.deck.getMainDeck().size(), "only the anchor stays");
        assertEquals(2, fixture.deck.getExtraDeck().size(),
                "both extra-deck elements should be redirected here");
    }

    @Test
    void allCompatibleElements_pastedOnExtraDeckAnchor_allLandInExtraDeck() {
        Fixture fixture = deckWithAnchorIn("extra", "Extra Deck");
        List<CardElement> toInsert = List.of(new CardElement(extraDeckMonster("Extra Card")));

        pasteElements(toInsert, fixture.anchor);

        assertEquals(2, fixture.deck.getExtraDeck().size(), "anchor + 1 pasted card");
        assertEquals(0, fixture.deck.getMainDeck().size());
    }

    @Test
    void allIncompatibleElements_pastedOnExtraDeckAnchor_allRedirectToMainDeck() {
        Fixture fixture = deckWithAnchorIn("extra", "Extra Deck");
        List<CardElement> toInsert = List.of(new CardElement(mainDeckMonster("Main Card")));

        pasteElements(toInsert, fixture.anchor);

        assertEquals(1, fixture.deck.getExtraDeck().size(), "only the anchor stays");
        assertEquals(1, fixture.deck.getMainDeck().size(),
                "the main-deck element should be redirected here");
    }

    @Test
    void mixedTypeElements_pastedOnSideDeckAnchor_allStayInSideDeckRegardlessOfType() {
        Fixture fixture = deckWithAnchorIn("side", "Side Deck");
        List<CardElement> toInsert = List.of(
                new CardElement(mainDeckMonster("Main Card")),
                new CardElement(extraDeckMonster("Extra Card")));

        pasteElements(toInsert, fixture.anchor);

        ObservableList<CardElement> sideList =
                CardGroupRegistry.observableListFor(CardGroupRegistry.getDeckSectionGroup(fixture.deck, "side"));
        assertEquals(3, sideList.size(), "anchor + both pasted cards, no split for Side Deck");
        assertEquals(0, fixture.deck.getMainDeck().size());
        assertEquals(0, fixture.deck.getExtraDeck().size());
    }

    @Test
    void deprecatedCardOverload_allIncompatibleCards_pastedOnMainDeckAnchor_redirectToExtraDeck() {
        Fixture fixture = deckWithAnchorIn("main", "Main Deck");

        pasteCards(List.of(extraDeckMonster("Extra Card")), fixture.anchor);

        assertEquals(1, fixture.deck.getMainDeck().size(), "only the anchor stays");
        assertEquals(1, fixture.deck.getExtraDeck().size());
    }

    // ── handleInsertCardsAfterElement (deprecated Card-list overload) — same rule applies ──

    @Test
    void deprecatedCardOverload_allCompatibleCards_pastedOnExtraDeckAnchor_stayInExtraDeck() {
        Fixture fixture = deckWithAnchorIn("extra", "Extra Deck");

        pasteCards(List.of(extraDeckMonster("Extra Card")), fixture.anchor);

        assertEquals(2, fixture.deck.getExtraDeck().size(), "anchor + pasted card");
        assertEquals(0, fixture.deck.getMainDeck().size());
    }

    private static final class Fixture {
        final Deck deck;
        final CardElement anchor;

        Fixture(Deck deck, CardElement anchor) {
            this.deck = deck;
            this.anchor = anchor;
        }
    }
}