package Controller;

import Model.CardsLists.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link CardMoveHandler#doMove}, backing the "Move to..." menu action (e.g. moving an
 * owned card from My Collection straight into a Deck's Main/Extra/Side list).
 * <p>
 * {@code doMove}/{@code findDestinationGroup} never call {@link Utils.DeckCompatibility}
 * anywhere — confirmed by inspection, there are zero references to it in {@code
 * CardMoveHandler.java}. It resolves a destination purely by matching the path string against
 * the owned collection's Box/CardsGroup structure and drops the card there unconditionally. This
 * is the exact same class of defect as {@code CardAddToListHandler.doAddToDeck}, just reached via
 * "Move" instead of "Add".
 * <p>
 * Same try/catch rationale and single-mutation-before-Platform-calls ordering as the other test
 * classes in this suite: the destination-list mutation happens before {@code
 * triggerTabDirtyIndicatorUpdate()}'s unconditional {@code Platform.runLater}.
 */
class CardMoveHandlerDeckCompatibilityTest {

    private DecksAndCollectionsList originalDecksList;
    private OwnedCardsCollection originalOwnedCollection;

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
     * Builds a standalone Deck plus the synthetic Box/CardsGroup mirror that {@code
     * CardMoveHandler.findDestinationGroup} actually searches (it resolves "Deck / Section"
     * destinations against {@code OwnedCardsCollection.getOwnedCollection()}, a {@code List<Box>},
     * not against {@code Deck.getMainDeck()} directly) and wires both into the shared statics
     * {@code doMove} reads from.
     */
    private static Deck deckWithMirroredOwnedCollectionBox(String deckName) {
        Deck deck = new Deck();
        deck.setName(deckName);
        DecksAndCollectionsList dac = new DecksAndCollectionsList();
        dac.setDecks(new ArrayList<>(List.of(deck)));
        UserInterfaceFunctions.setDecksList(dac);

        CardsGroup mainGroup = new CardsGroup("Main Deck", new ArrayList<>());
        CardsGroup extraGroup = new CardsGroup("Extra Deck", new ArrayList<>());
        CardsGroup sideGroup = new CardsGroup("Side Deck", new ArrayList<>());
        Box deckBox = new Box(deckName);
        deckBox.setContent(new ArrayList<>(List.of(mainGroup, extraGroup, sideGroup)));

        OwnedCardsCollection owned = new OwnedCardsCollection();
        owned.setOwnedCollection(new ArrayList<>(List.of(deckBox)));
        OuicheList.setMyCardsCollection(owned);

        return deck;
    }

    private static CardsGroup sectionGroup(String deckName, String sectionDisplayName) {
        OwnedCardsCollection owned = OuicheList.getMyCardsCollection();
        for (Box box : owned.getOwnedCollection()) {
            if (box.getName().equals(deckName)) {
                for (CardsGroup group : box.getContent()) {
                    if (group.getName().equals(sectionDisplayName)) {
                        return group;
                    }
                }
            }
        }
        throw new IllegalStateException("fixture section group not found: " + sectionDisplayName);
    }

    /**
     * Calls doMove, tolerating the FX-toolkit-not-initialized failure — see class javadoc.
     */
    private static void move(Card card, String handlerTarget) {
        try {
            CardMoveHandler.doMove(new CardElement(card), handlerTarget);
        } catch (Throwable ignored) {
            // Expected in this headless test JVM; the list mutation already happened.
        }
    }

    @BeforeEach
    void saveGlobalState() {
        originalDecksList = UserInterfaceFunctions.getDecksList();
        originalOwnedCollection = OuicheList.getMyCardsCollection();
    }

    @AfterEach
    void restoreGlobalState() {
        UserInterfaceFunctions.setDecksList(originalDecksList);
        OuicheList.setMyCardsCollection(originalOwnedCollection);
    }

    // ── Correctly targeted single card ──────────────────────────────────────────

    @Test
    void mainDeckCard_movedToMainDeck_landsInMainDeck() {
        deckWithMirroredOwnedCollectionBox("TestDeck");
        move(mainDeckMonster("Main Card"), "TestDeck / Main Deck");

        assertEquals(1, sectionGroup("TestDeck", "Main Deck").getCardList().size());
        assertEquals(0, sectionGroup("TestDeck", "Extra Deck").getCardList().size());
    }

    @Test
    void extraDeckCard_movedToExtraDeck_landsInExtraDeck() {
        deckWithMirroredOwnedCollectionBox("TestDeck");
        move(extraDeckMonster("Extra Card"), "TestDeck / Extra Deck");

        assertEquals(1, sectionGroup("TestDeck", "Extra Deck").getCardList().size());
        assertEquals(0, sectionGroup("TestDeck", "Main Deck").getCardList().size());
    }

    @Test
    void anyCard_movedToSideDeck_landsInSideDeckRegardlessOfType() {
        deckWithMirroredOwnedCollectionBox("TestDeck");
        move(extraDeckMonster("Extra Card"), "TestDeck / Side Deck");
        move(mainDeckMonster("Main Card"), "TestDeck / Side Deck");

        assertEquals(2, sectionGroup("TestDeck", "Side Deck").getCardList().size());
    }

    // ── Mismatched target — documents the missing redirect ──────────────────────
    // Models what happens to every non-primary card of a mixed "Move to..." bulk
    // selection, for the exact same reason as CardAddToListHandler: the destination
    // is resolved purely from the path string, with no DeckCompatibility check at all.

    @Test
    void extraDeckCard_movedToMainDeckTarget_shouldRedirectToExtraDeck() {
        deckWithMirroredOwnedCollectionBox("TestDeck");
        move(extraDeckMonster("Extra Card"), "TestDeck / Main Deck");

        assertEquals(0, sectionGroup("TestDeck", "Main Deck").getCardList().size(),
                "Extra-deck card must not stay in the Main Deck");
        assertEquals(1, sectionGroup("TestDeck", "Extra Deck").getCardList().size(),
                "Extra-deck card should have been redirected to the Extra Deck");
    }

    @Test
    void mainDeckCard_movedToExtraDeckTarget_shouldRedirectToMainDeck() {
        deckWithMirroredOwnedCollectionBox("TestDeck");
        move(mainDeckMonster("Main Card"), "TestDeck / Extra Deck");

        assertEquals(0, sectionGroup("TestDeck", "Extra Deck").getCardList().size(),
                "Main-deck card must not stay in the Extra Deck");
        assertEquals(1, sectionGroup("TestDeck", "Main Deck").getCardList().size(),
                "Main-deck card should have been redirected to the Main Deck");
    }
}