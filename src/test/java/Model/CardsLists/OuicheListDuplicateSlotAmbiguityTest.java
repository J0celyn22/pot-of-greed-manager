package Model.CardsLists;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Covers slot disambiguation when a section holds two or more slots that are
 * indistinguishable by value alone ({@code cardKey} — artwork/printCode/condition/rarity,
 * never ownership status): genuine duplicates. {@code removeFromSection} (shared by
 * {@link OuicheList#onDeckCardMoved} and {@link OuicheList#onDeckCardRemoved}) can't tell
 * such slots apart by value, so these callers pass a {@code sourceIndex} — the slot's index
 * within its section immediately before the move/removal — which {@code removeFromSection}
 * prefers over its value-only fallback scan whenever it's available and still matches.
 *
 * <p><b>A note on test design:</b> for a 2-element OWNED/MISSING pair, "move the MISSING one
 * to the front" and "do nothing" produce the exact same value pattern at each index
 * ({@code [OWNED, MISSING]}) — a naive status-by-index assertion would pass whether or not
 * anything actually happened. These tests track the specific {@link CardElement} instances
 * by reference ({@link #assertSame}) so they can actually tell a real fix apart from a
 * coincidental match.
 *
 * <p>Reordering a duplicate slot ordinarily just relocates it, preserving its own ownership
 * status — except when the dragged slot is MISSING and displaces a different, still-present
 * duplicate that's OWNED/OWNED_SUBSTANDARD: since ownership has no meaningful attachment to
 * one interchangeable duplicate over another, that case swaps the two slots' statuses instead
 * (see {@link OuicheListUpdater#onDeckCardMoved} for the full rule). Without {@code
 * sourceIndex}, none of this is possible at all: every one of these scenarios used to be
 * indistinguishable from a no-op, because the slot to touch was always picked by value,
 * always resolving to whichever duplicate happened to be first in list order.
 */
public class OuicheListDuplicateSlotAmbiguityTest {

    private Card cardX;
    private Deck deck;

    private static Card freshCard(String konamiId) {
        Card c = new Card();
        c.setKonamiId(konamiId);
        c.setPassCode(konamiId);
        c.setImagePath("img/" + konamiId + ".jpg");
        return c;
    }

    @BeforeEach
    void setUp() {
        cardX = freshCard("KID-501");
        deck = new Deck();
        deck.setName("D1");
    }

    private void install(Deck... decks) {
        DecksAndCollectionsList detailed = new DecksAndCollectionsList();
        for (Deck d : decks) {
            detailed.addDeck(d);
        }
        OuicheList.setDetailedOuicheList(detailed);
        OuicheList.setUnusedCards(new ArrayList<>());
        OuicheList.setMaOuicheList(new LinkedHashMap<>());
        OuicheList.setMaOuicheListCounts(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandard(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandardCounts(new LinkedHashMap<>());
        OuicheList.setMyCardsCollection(new OwnedCardsCollection());
    }

    // =========================================================================
    // Reorder among duplicates — dragging a MISSING slot onto a same-card
    // OWNED/OWNED_SUBSTANDARD slot swaps which physical slot holds which status.
    // =========================================================================

    @Test
    void reorder_missingDuplicateMovedBeforeOwned_shouldSwapStatuses() {
        CardElement owned = new CardElement(cardX);
        owned.setOwnershipStatus(OwnershipStatus.OWNED);
        CardElement missing = new CardElement(cardX);
        missing.setOwnershipStatus(OwnershipStatus.MISSING);
        deck.getMainDeck().add(owned);   // index 0
        deck.getMainDeck().add(missing); // index 1
        install(deck);

        // The person drags the MISSING card (index 1) to before the OWNED one (index 0).
        OuicheList.onDeckCardMoved(new CardElement(freshCard("KID-501")), "D1", "main", null, 0, 1);

        // Status-by-index alone can't tell a real swap apart from a no-op here (both
        // produce [OWNED, MISSING]) -- track the actual instances instead.
        assertSame(missing, deck.getMainDeck().get(0),
                "The specific card the person dragged should be the one now at the front");
        assertEquals(OwnershipStatus.OWNED, deck.getMainDeck().get(0).getOwnershipStatus(),
                "...and it should now be OWNED, having taken the front slot's status");
        assertSame(owned, deck.getMainDeck().get(1));
        assertEquals(OwnershipStatus.MISSING, deck.getMainDeck().get(1).getOwnershipStatus(),
                "The displaced card should now be MISSING");
    }

    @Test
    void reorder_ownedDuplicateMovedBeforeMissing_relocatesWithoutSwapping() {
        CardElement missing = new CardElement(cardX);
        missing.setOwnershipStatus(OwnershipStatus.MISSING);
        CardElement owned = new CardElement(cardX);
        owned.setOwnershipStatus(OwnershipStatus.OWNED);
        deck.getMainDeck().add(missing); // index 0
        deck.getMainDeck().add(owned);   // index 1
        install(deck);

        // The person drags the OWNED card (index 1) to before the MISSING one (index 0).
        // Unlike the MISSING-displaces-OWNED case above, moving an already-OWNED slot never
        // reassigns ownership -- it just relocates, same as moving any other card.
        OuicheList.onDeckCardMoved(new CardElement(freshCard("KID-501")), "D1", "main", null, 0, 1);

        assertSame(owned, deck.getMainDeck().get(0),
                "The specific card the person dragged should be the one now at the front");
        assertEquals(OwnershipStatus.OWNED, deck.getMainDeck().get(0).getOwnershipStatus());
        assertSame(missing, deck.getMainDeck().get(1));
        assertEquals(OwnershipStatus.MISSING, deck.getMainDeck().get(1).getOwnershipStatus());
    }

    @Test
    void reorder_substandardDuplicateMovedBeforeMissing_relocatesWithoutSwapping() {
        CardElement missing = new CardElement(cardX);
        missing.setOwnershipStatus(OwnershipStatus.MISSING);
        CardElement substandard = new CardElement(cardX);
        substandard.setOwnershipStatus(OwnershipStatus.OWNED_SUBSTANDARD);
        deck.getMainDeck().add(missing);     // index 0
        deck.getMainDeck().add(substandard); // index 1
        install(deck);

        OuicheList.onDeckCardMoved(new CardElement(freshCard("KID-501")), "D1", "main", null, 0, 1);

        assertSame(substandard, deck.getMainDeck().get(0));
        assertEquals(OwnershipStatus.OWNED_SUBSTANDARD, deck.getMainDeck().get(0).getOwnershipStatus());
        assertSame(missing, deck.getMainDeck().get(1));
        assertEquals(OwnershipStatus.MISSING, deck.getMainDeck().get(1).getOwnershipStatus());
    }

    // =========================================================================
    // Repeated/oscillating reorder attempts — once correctly relocated, the
    // targeted slot stays put; retrying with its now-current index is a no-op.
    // =========================================================================

    @Test
    void reorder_repeatedAttempts_targetedDuplicateMovesOnceThenStaysPut() {
        CardElement owned = new CardElement(cardX);
        owned.setOwnershipStatus(OwnershipStatus.OWNED);
        CardElement missing = new CardElement(cardX);
        missing.setOwnershipStatus(OwnershipStatus.MISSING);
        deck.getMainDeck().add(owned);
        deck.getMainDeck().add(missing);
        install(deck);

        // Repeatedly try to drag "the missing one" to the front (index 0), as a real caller
        // would each time: looking up its current position immediately beforehand.
        for (int attempt = 0; attempt < 4; attempt++) {
            int sourceIndex = deck.getMainDeck().indexOf(missing);
            OuicheList.onDeckCardMoved(new CardElement(freshCard("KID-501")), "D1", "main", null, 0, sourceIndex);
        }

        assertSame(missing, deck.getMainDeck().get(0),
                "After repeatedly targeting the MISSING slot for the front position, it "
                        + "should end up there and stay there");
        assertEquals(OwnershipStatus.OWNED, deck.getMainDeck().get(0).getOwnershipStatus());
    }

    // =========================================================================
    // Remove among duplicates.
    // =========================================================================

    /**
     * With {@code sourceIndex} correctly identifying the MISSING slot, removing it is a
     * plain drop -- MISSING removals never propagate -- so the local OWNED sibling is simply
     * left alone, for the straightforward reason rather than by accident.
     */
    @Test
    void remove_missingDuplicate_localOwnedSiblingUnaffected() {
        CardElement owned = new CardElement(cardX);
        owned.setOwnershipStatus(OwnershipStatus.OWNED);
        CardElement missing = new CardElement(cardX);
        missing.setOwnershipStatus(OwnershipStatus.MISSING);
        deck.getMainDeck().add(owned);
        deck.getMainDeck().add(missing);
        install(deck);

        OuicheList.onDeckCardRemoved(new CardElement(freshCard("KID-501")), "D1", "main", null, 1);

        assertEquals(1, deck.getMainDeck().size());
        assertEquals(OwnershipStatus.OWNED, deck.getMainDeck().get(0).getOwnershipStatus(),
                "The MISSING slot was removed directly, so the OWNED sibling is untouched");
    }

    /**
     * With {@code sourceIndex} available, removal targets the exact intended slot even when
     * a competing eligible MISSING slot for the same KonamiId exists elsewhere, earlier in
     * generation order -- that other deck is left alone rather than absorbing a propagation
     * that was never meant for it.
     */
    @Test
    void remove_missingDuplicate_competingSlotElsewhere_removesOnlyTheTargetedLocalSlot() {
        Deck earlierDeck = new Deck(); // earlier in generation order than "D1"
        earlierDeck.setName("EarlierDeck");
        CardElement competingMissing = new CardElement(cardX);
        competingMissing.setOwnershipStatus(OwnershipStatus.MISSING);
        earlierDeck.getMainDeck().add(competingMissing);

        CardElement owned = new CardElement(cardX);
        owned.setOwnershipStatus(OwnershipStatus.OWNED);
        CardElement missing = new CardElement(cardX);
        missing.setOwnershipStatus(OwnershipStatus.MISSING);
        deck.getMainDeck().add(owned);
        deck.getMainDeck().add(missing);
        install(earlierDeck, deck);

        // The person removes "the missing card" (index 1) from D1, expecting D1 to end up
        // with just the OWNED one, and EarlierDeck to be untouched.
        OuicheList.onDeckCardRemoved(new CardElement(freshCard("KID-501")), "D1", "main", null, 1);

        assertEquals(1, deck.getMainDeck().size());
        assertEquals(OwnershipStatus.OWNED, deck.getMainDeck().get(0).getOwnershipStatus(),
                "D1 should retain its OWNED slot");
        assertEquals(OwnershipStatus.MISSING, earlierDeck.getMainDeck().get(0).getOwnershipStatus(),
                "EarlierDeck should be completely unrelated to this removal");
    }

    // =========================================================================
    // Three duplicates — confirms the same mechanism scales, not just the
    // two-slot case.
    // =========================================================================

    @Test
    void reorder_threeDuplicates_targetedSlotMovesRegardlessOfPosition() {
        CardElement owned = new CardElement(cardX);
        owned.setOwnershipStatus(OwnershipStatus.OWNED);
        CardElement missing1 = new CardElement(cardX);
        missing1.setOwnershipStatus(OwnershipStatus.MISSING);
        CardElement missing2 = new CardElement(cardX);
        missing2.setOwnershipStatus(OwnershipStatus.MISSING);
        deck.getMainDeck().add(owned);    // index 0
        deck.getMainDeck().add(missing1); // index 1
        deck.getMainDeck().add(missing2); // index 2
        install(deck);

        // Move the LAST missing slot (index 2) to the front.
        OuicheList.onDeckCardMoved(new CardElement(freshCard("KID-501")), "D1", "main", null, 0, 2);

        assertSame(missing2, deck.getMainDeck().get(0),
                "The specific slot dragged (index 2) should be the one now at the front");
        assertEquals(OwnershipStatus.OWNED, deck.getMainDeck().get(0).getOwnershipStatus());
    }
}