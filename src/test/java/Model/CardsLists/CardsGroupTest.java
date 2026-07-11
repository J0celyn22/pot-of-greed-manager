package Model.CardsLists;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Partition-based tests for {@link CardsGroup}.
 */
class CardsGroupTest {

    private static CardElement withPrice(String price) {
        Card card = new Card();
        card.setPrice(price);
        return new CardElement(card);
    }

    // ── Constructors ─────────────────────────────────────────────────────────

    @Test
    void singleArgConstructor_initializesEmptyMutableCardList() {
        CardsGroup group = new CardsGroup("Main Deck");
        assertNotNull(group.getCardList());
        assertTrue(group.getCardList().isEmpty());
        assertDoesNotThrow(() -> group.addCard(withPrice(null)));
    }

    @Test
    void twoArgConstructor_usesProvidedListDirectly() {
        List<CardElement> list = new ArrayList<>(List.of(withPrice("1.0")));
        CardsGroup group = new CardsGroup("Main Deck", list);
        assertEquals(1, group.getCardCount());
        assertSame(list, group.getCardList(), "the constructor stores the given list reference, not a copy");
    }

    @Test
    void twoArgConstructor_withNullList_currentlyThrowsOnAddCard() {
        // No defensive copy/allocation here, unlike the single-arg constructor.
        CardsGroup group = new CardsGroup("Main Deck", null);
        assertThrows(NullPointerException.class, () -> group.addCard(withPrice(null)));
    }

    // ── addCard / getCardCount ───────────────────────────────────────────────

    @Test
    void addCard_appendsToEnd_cardCountReflectsSize() {
        CardsGroup group = new CardsGroup("Main Deck");
        group.addCard(withPrice("1.0"));
        group.addCard(withPrice("2.0"));
        assertEquals(2, group.getCardCount());
        assertEquals("1.0", group.getCardList().get(0).getPrice());
        assertEquals("2.0", group.getCardList().get(1).getPrice());
    }

    @Test
    void getCardCount_emptyGroup_returnsZero() {
        assertEquals(0, new CardsGroup("Empty").getCardCount());
    }

    // ── getPrice ─────────────────────────────────────────────────────────────

    @Test
    void getPrice_emptyGroup_returnsZero() {
        assertEquals("0.0", new CardsGroup("Empty").getPrice());
    }

    @Test
    void getPrice_sumsValidPrices() {
        CardsGroup group = new CardsGroup("Main Deck");
        group.addCard(withPrice("1.5"));
        group.addCard(withPrice("2.5"));
        assertEquals("4.0", group.getPrice());
    }

    @Test
    void getPrice_skipsElementWithNullCard() {
        CardsGroup group = new CardsGroup("Main Deck");
        group.addCard(new CardElement(null, false, false, false, false));
        group.addCard(withPrice("2.5"));
        assertEquals("2.5", group.getPrice());
    }

    @Test
    void getPrice_skipsElementWithNullPrice() {
        CardsGroup group = new CardsGroup("Main Deck");
        group.addCard(withPrice(null));
        group.addCard(withPrice("2.5"));
        assertEquals("2.5", group.getPrice());
    }

    @Test
    void getPrice_malformedPriceString_currentlyThrowsNumberFormatException() {
        // The null-check guards a missing price, but not a non-numeric one.
        CardsGroup group = new CardsGroup("Main Deck");
        group.addCard(withPrice("not-a-number"));
        assertThrows(NumberFormatException.class, group::getPrice);
    }

    // ── toString ─────────────────────────────────────────────────────────────

    @Test
    void toString_emptyGroup_isJustTheName() {
        assertEquals("Main Deck", new CardsGroup("Main Deck").toString());
    }

    @Test
    void toString_appendsEachCardOnOwnLine() {
        Card card = new Card();
        card.setPassCode("111");
        CardsGroup group = new CardsGroup("Main Deck");
        group.addCard(new CardElement(card, false, false, false, false));
        assertEquals("Main Deck\n111", group.toString());
    }
}