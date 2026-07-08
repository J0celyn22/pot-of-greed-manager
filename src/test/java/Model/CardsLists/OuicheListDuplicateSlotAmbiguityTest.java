package Model.CardsLists;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Documents a confirmed, unfixed modeling gap: {@code removeFromSection} (shared by
 * {@link OuicheList#onDeckCardMoved} and {@link OuicheList#onDeckCardRemoved}) identifies
 * "the slot to touch" purely by value ({@code cardKey} — artwork/printCode/condition/rarity,
 * never ownership status). When two or more slots in the same section share that key, it
 * always grabs whichever is first in list order, never the one the person actually
 * dragged/targeted — because the live Decks and Collections element being moved carries no
 * reference back to a specific detailed-list slot; the two structures are only linked by
 * value.
 *
 * <p><b>A note on test design:</b> for a 2-element OWNED/MISSING pair, "move the MISSING one
 * to the front" and "do nothing" produce the exact same value pattern at each index
 * ({@code [OWNED, MISSING]}) — a naive status-by-index assertion would pass whether or not
 * anything actually happened. These tests track the specific {@link CardElement} instances
 * by reference ({@link #assertSame}) so they can actually tell a real fix apart from a
 * coincidental match.
 *
 * <p>Not fixed here: disambiguating "which of several identical slots" needs information
 * this layer doesn't have (e.g. the slot's pre-move index, or a stable per-element id
 * threaded down from the UI/Controller layer). These tests assert the behavior a person
 * would actually want (per their own description: moving the MISSING copy to where the
 * OWNED one was should swap which one is which) and currently fail where marked.
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
    // Reorder among duplicates — should swap which physical slot holds which
    // status; instead, whichever slot is first in list order never moves.
    // =========================================================================

    @Test
    void reorder_missingDuplicateMovedBeforeOwned_shouldSwapStatuses_confirmedGap() {
        CardElement owned = new CardElement(cardX);
        owned.setOwnershipStatus(OwnershipStatus.OWNED);
        CardElement missing = new CardElement(cardX);
        missing.setOwnershipStatus(OwnershipStatus.MISSING);
        deck.getMainDeck().add(owned);   // index 0
        deck.getMainDeck().add(missing); // index 1
        install(deck);

        // The person drags the MISSING card (index 1) to before the OWNED one (index 0).
        OuicheList.onDeckCardMoved(new CardElement(freshCard("KID-501")), "D1", "main", null, 0);

        // Status-by-index alone can't tell a real swap apart from a no-op here (both
        // produce [OWNED, MISSING]) -- track the actual instances instead.
        assertSame(missing, deck.getMainDeck().get(0),
                "The specific card the person dragged should be the one now at the front");
        assertEquals(OwnershipStatus.OWNED, deck.getMainDeck().get(0).getOwnershipStatus(),
                "...and it should now be OWNED, having taken the front slot's status");
        assertSame(owned, deck.getMainDeck().get(1));
        assertEquals(OwnershipStatus.MISSING, deck.getMainDeck().get(1).getOwnershipStatus(),
                "The displaced card should now be MISSING -- currently nothing moves at all");
    }

    @Test
    void reorder_ownedDuplicateMovedBeforeMissing_shouldSwapStatuses_confirmedGap() {
        CardElement missing = new CardElement(cardX);
        missing.setOwnershipStatus(OwnershipStatus.MISSING);
        CardElement owned = new CardElement(cardX);
        owned.setOwnershipStatus(OwnershipStatus.OWNED);
        deck.getMainDeck().add(missing); // index 0
        deck.getMainDeck().add(owned);   // index 1
        install(deck);

        // The person drags the OWNED card (index 1) to before the MISSING one (index 0).
        OuicheList.onDeckCardMoved(new CardElement(freshCard("KID-501")), "D1", "main", null, 0);

        assertSame(owned, deck.getMainDeck().get(0),
                "The specific card the person dragged should be the one now at the front");
        assertEquals(OwnershipStatus.OWNED, deck.getMainDeck().get(0).getOwnershipStatus());
        assertSame(missing, deck.getMainDeck().get(1));
        assertEquals(OwnershipStatus.MISSING, deck.getMainDeck().get(1).getOwnershipStatus());
    }

    @Test
    void reorder_substandardDuplicateMovedBeforeMissing_shouldSwapStatuses_confirmedGap() {
        CardElement missing = new CardElement(cardX);
        missing.setOwnershipStatus(OwnershipStatus.MISSING);
        CardElement substandard = new CardElement(cardX);
        substandard.setOwnershipStatus(OwnershipStatus.OWNED_SUBSTANDARD);
        deck.getMainDeck().add(missing);     // index 0
        deck.getMainDeck().add(substandard); // index 1
        install(deck);

        OuicheList.onDeckCardMoved(new CardElement(freshCard("KID-501")), "D1", "main", null, 0);

        assertSame(substandard, deck.getMainDeck().get(0));
        assertEquals(OwnershipStatus.OWNED_SUBSTANDARD, deck.getMainDeck().get(0).getOwnershipStatus());
        assertSame(missing, deck.getMainDeck().get(1));
        assertEquals(OwnershipStatus.MISSING, deck.getMainDeck().get(1).getOwnershipStatus());
    }

    // =========================================================================
    // Repeated/oscillating reorder attempts — the "locked" symptom: whichever
    // slot is first keeps getting toggled; the actually-targeted card never
    // participates, no matter how many times it's tried.
    // =========================================================================

    @Test
    void reorder_repeatedAttempts_targetedDuplicateNeverActuallyMoves_confirmedGap() {
        CardElement owned = new CardElement(cardX);
        owned.setOwnershipStatus(OwnershipStatus.OWNED);
        CardElement missing = new CardElement(cardX);
        missing.setOwnershipStatus(OwnershipStatus.MISSING);
        deck.getMainDeck().add(owned);
        deck.getMainDeck().add(missing);
        install(deck);

        // Repeatedly try to drag "the missing one" to the front (index 0), as a person
        // stuck on this would keep attempting.
        for (int attempt = 0; attempt < 4; attempt++) {
            OuicheList.onDeckCardMoved(new CardElement(freshCard("KID-501")), "D1", "main", null, 0);
        }

        assertSame(missing, deck.getMainDeck().get(0),
                "After repeatedly targeting the MISSING slot for the front position, it "
                        + "should end up there -- instead the OWNED slot (never the intended "
                        + "target) is the only one that ever actually responds");
        assertEquals(OwnershipStatus.OWNED, deck.getMainDeck().get(0).getOwnershipStatus());
    }

    // =========================================================================
    // Remove among duplicates.
    // =========================================================================

    /**
     * Regression note, not a currently-failing assertion: when the only other
     * duplicate is in the same section, {@code onDeckCardRemoved}'s own cross-list
     * "propagate freed ownership to the next MISSING slot with this KonamiId" logic
     * happens to re-promote the local sibling, which coincidentally produces the
     * right-looking end state even though the wrong physical slot was removed first.
     * This is accidental, not a real fix — see the next test for what happens when
     * that masking isn't available.
     */
    @Test
    void remove_missingDuplicate_localOwnedSiblingMasksTheWrongRemoval() {
        CardElement owned = new CardElement(cardX);
        owned.setOwnershipStatus(OwnershipStatus.OWNED);
        CardElement missing = new CardElement(cardX);
        missing.setOwnershipStatus(OwnershipStatus.MISSING);
        deck.getMainDeck().add(owned);
        deck.getMainDeck().add(missing);
        install(deck);

        OuicheList.onDeckCardRemoved(new CardElement(freshCard("KID-501")), "D1", "main", null);

        assertEquals(1, deck.getMainDeck().size());
        assertEquals(OwnershipStatus.OWNED, deck.getMainDeck().get(0).getOwnershipStatus(),
                "Looks correct, but only because removal grabbed the OWNED slot and this "
                        + "section's own MISSING sibling happened to catch the propagated ownership");
    }

    /**
     * Confirmed gap: the masking above is not a real fix. When a different eligible
     * MISSING slot for the same KonamiId exists elsewhere, earlier in generation order,
     * it — not the local duplicate — receives the propagated ownership, exposing the
     * wrong-slot removal as a visible, remote side effect.
     */
    @Test
    void remove_missingDuplicate_competingSlotElsewhere_removesWrongLocalSlotAndCorruptsUnrelatedDeck_confirmedGap() {
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

        // The person removes "the missing card" from D1, expecting D1 to end up with
        // just the OWNED one, and EarlierDeck to be untouched.
        OuicheList.onDeckCardRemoved(new CardElement(freshCard("KID-501")), "D1", "main", null);

        assertEquals(1, deck.getMainDeck().size());
        assertEquals(OwnershipStatus.OWNED, deck.getMainDeck().get(0).getOwnershipStatus(),
                "D1 should retain its OWNED slot -- instead the OWNED slot was the one removed, "
                        + "leaving a MISSING slot behind in the deck the person removed from");
        assertEquals(OwnershipStatus.MISSING, earlierDeck.getMainDeck().get(0).getOwnershipStatus(),
                "EarlierDeck should be completely unrelated to this removal -- instead it silently "
                        + "gets marked OWNED because it was mistaken for the propagation target");
    }

    // =========================================================================
    // Three duplicates — confirms the same mechanism, not a worse one.
    // =========================================================================

    @Test
    void reorder_threeDuplicates_onlyFirstEverParticipates_confirmedGap() {
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

        // Try to move the LAST missing slot (index 2) to the front.
        OuicheList.onDeckCardMoved(new CardElement(freshCard("KID-501")), "D1", "main", null, 0);

        assertSame(missing2, deck.getMainDeck().get(0),
                "The specific slot dragged (index 2) should be the one now at the front -- "
                        + "instead only the first (already-OWNED) slot ever actually responds "
                        + "to a move request");
        assertEquals(OwnershipStatus.OWNED, deck.getMainDeck().get(0).getOwnershipStatus());
    }
}