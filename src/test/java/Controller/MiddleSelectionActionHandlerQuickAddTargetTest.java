package Controller;

import Model.CardsLists.Card;
import Model.CardsLists.Deck;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link MiddleSelectionActionHandler#insertCardsAtQuickAddTarget}, extracted (Unit 3
 * of the camera-scanner feature — see the project's camera-scanner plan doc) from what used to
 * be the private {@code KeyboardShortcutHandler.insertCardsAtNumpadTarget}. These guard the
 * extraction itself — that the targeting/fallback control flow and the {@code activeTabIndex}/
 * {@code activeTreeView} parameter threading still behave exactly as the original private method
 * did — not the lower-level paste methods it delegates to, which already have their own coverage
 * ({@link MiddleSelectionActionHandlerDeckCompatibilityTest}).
 *
 * <p>Both tests here deliberately pass a {@code null} activeTreeView, so neither exercises the
 * MIDDLE-pane-last-selected-element priority branch (that needs a live, populated {@code
 * TreeView} built from a {@code DataTreeItem}/{@code CardsGroup} structure, which is out of scope
 * for a contained refactor-regression test) — only the last-clicked-navigation-item fallback and
 * its active-tab gate, which is exactly the branch a null tree naturally reaches.
 *
 * <p>Follows the same FX-toolkit-tolerant pattern as {@link
 * MiddleSelectionActionHandlerDeckCompatibilityTest}: the underlying list mutation happens before
 * any Platform-touching refresh call, so a throw from that refresh call in this headless test JVM
 * is caught and ignored.
 */
class MiddleSelectionActionHandlerQuickAddTargetTest {

    private static final int MY_COLLECTION_TAB_INDEX = 0;
    private static final int DECKS_TAB_INDEX = 1;

    private static Card mainDeckMonster(String name) {
        Card card = new Card();
        card.setName_EN(name);
        card.setCardProperties(new ArrayList<>(List.of("Effect")));
        return card;
    }

    private static Deck freshDeck() {
        Deck deck = new Deck();
        deck.setName("TestDeck");
        return deck;
    }

    /**
     * Tolerates the FX-toolkit-not-initialized failure — see class javadoc.
     */
    private static void insertAtQuickAddTarget(List<Card> cards, int activeTabIndex) {
        try {
            MiddleSelectionActionHandler.insertCardsAtQuickAddTarget(cards, activeTabIndex, null);
        } catch (Throwable ignored) {
            // Expected in this headless test JVM; the list mutation already happened.
        }
    }

    @AfterEach
    void resetSelectionState() {
        SelectionManager.clearSelection();
        SelectionManager.setLastClickedNavigationItem(null);
    }

    @Test
    void lastClickedDeck_onMatchingActiveTab_receivesTheInsertedCards() {
        Deck deck = freshDeck();
        SelectionManager.setLastClickedNavigationItem(deck);

        insertAtQuickAddTarget(List.of(mainDeckMonster("Scanned Card")), DECKS_TAB_INDEX);

        assertEquals(1, deck.getMainDeck().size());
    }

    @Test
    void lastClickedDeck_onNonMatchingActiveTab_isIgnored() {
        Deck deck = freshDeck();
        SelectionManager.setLastClickedNavigationItem(deck);

        // A Deck only belongs to the Decks & Collections tab (index 1) — on My Collection
        // (index 0) navItemBelongsToActiveTab should reject it, and with a null activeTreeView
        // there's nowhere else to fall back to, so nothing should be inserted anywhere.
        insertAtQuickAddTarget(List.of(mainDeckMonster("Scanned Card")), MY_COLLECTION_TAB_INDEX);

        assertTrue(deck.getMainDeck().isEmpty());
    }
}