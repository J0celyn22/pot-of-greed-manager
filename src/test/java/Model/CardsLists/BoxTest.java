package Model.CardsLists;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Partition-based tests for {@link Box}.
 */
class BoxTest {

    private static CardElement withPrice(String price) {
        Card card = new Card();
        card.setPrice(price);
        return new CardElement(card);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    void constructor_initializesEmptyMutableContentAndSubBoxes() {
        Box box = new Box("MyBox");
        assertNotNull(box.getContent());
        assertTrue(box.getContent().isEmpty());
        assertNotNull(box.getSubBoxes());
        assertTrue(box.getSubBoxes().isEmpty());
    }

    // ── AddCardToLastCategory ────────────────────────────────────────────────

    @Test
    void addCardToLastCategory_emptyContent_createsUnnamedCategoryFirst() {
        Box box = new Box("MyBox");
        box.AddCardToLastCategory(withPrice("1.0"));
        assertEquals(1, box.getContent().size());
        assertEquals("", box.getContent().get(0).getName());
        assertEquals(1, box.getContent().get(0).getCardCount());
    }

    @Test
    void addCardToLastCategory_nonEmptyContent_addsToLastCategoryOnly() {
        Box box = new Box("MyBox");
        box.AddCategory("First");
        box.AddCategory("Second");
        box.AddCardToLastCategory(withPrice("1.0"));

        assertEquals(0, box.getContent().get(0).getCardCount(), "\"First\" must be untouched");
        assertEquals(1, box.getContent().get(1).getCardCount(), "the card goes to \"Second\", the last category");
    }

    @Test
    void addCardToLastCategory_nullContent_currentlyThrowsNullPointerException() {
        // The guard is `if (this.content.isEmpty())`, which itself dereferences
        // content — a null content list crashes instead of being treated like
        // "no category yet".
        Box box = new Box("MyBox");
        box.setContent(null);
        assertThrows(NullPointerException.class, () -> box.AddCardToLastCategory(withPrice("1.0")));
    }

    // ── AddCategory ──────────────────────────────────────────────────────────

    @Test
    void addCategory_appendsNamedGroupToContent() {
        Box box = new Box("MyBox");
        box.AddCategory("Extra Deck");
        assertEquals(1, box.getContent().size());
        assertEquals("Extra Deck", box.getContent().get(0).getName());
        assertEquals(0, box.getContent().get(0).getCardCount());
    }

    // ── getCardCount ─────────────────────────────────────────────────────────

    @Test
    void getCardCount_emptyBox_returnsZero() {
        assertEquals(0, new Box("MyBox").getCardCount());
    }

    @Test
    void getCardCount_sumsAcrossAllCategories() {
        Box box = new Box("MyBox");
        box.AddCategory("Main Deck");
        box.getContent().get(0).addCard(withPrice("1.0"));
        box.getContent().get(0).addCard(withPrice("2.0"));
        box.AddCategory("Extra Deck");
        box.getContent().get(1).addCard(withPrice("3.0"));

        assertEquals(3, box.getCardCount());
    }

    // ── getPrice ─────────────────────────────────────────────────────────────

    @Test
    void getPrice_emptyBox_returnsZero() {
        assertEquals("0.0", new Box("MyBox").getPrice());
    }

    @Test
    void getPrice_sumsAcrossAllCategories() {
        Box box = new Box("MyBox");
        box.AddCategory("Main Deck");
        box.getContent().get(0).addCard(withPrice("1.5"));
        box.AddCategory("Extra Deck");
        box.getContent().get(1).addCard(withPrice("2.5"));

        assertEquals("4.0", box.getPrice());
    }

    // ── toString ─────────────────────────────────────────────────────────────

    @Test
    void toString_emptyBox_isJustTheName() {
        assertEquals("MyBox", new Box("MyBox").toString());
    }

    @Test
    void toString_appendsEachCategoryOnOwnLine() {
        Box box = new Box("MyBox");
        box.AddCategory("Main Deck");
        assertEquals("MyBox\nMain Deck", box.toString());
    }
}