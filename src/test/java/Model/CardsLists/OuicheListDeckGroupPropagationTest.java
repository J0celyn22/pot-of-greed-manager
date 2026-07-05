package Model.CardsLists;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Targeted coverage for {@link ThemeCollection#getLinkedDecks()} ("deck groups") — a
 * mechanism where several decks inside a non-loose collection are grouped so that a
 * resolved card in one deck frees up a matching slot in every sibling deck of the same
 * group <em>without consuming an extra owned copy</em>
 * ({@link OuicheListComputer#applySectionPass} calls this "free within-group
 * propagation", implemented there by {@code propagateGroupValidation}).
 *
 * <p><b>Finding:</b> {@link OuicheListUpdater} — the incremental-update engine behind
 * {@link OuicheList#onDeckCardAdded}, {@link OuicheList#onDeckCardRemoved}, and
 * {@link OuicheList#onDeckCardMoved} — never references {@code getLinkedDecks()},
 * {@code getConnectToWholeCollection()}, or anything equivalent to
 * {@code propagateGroupValidation}. Each of those three methods resolves and mutates
 * exactly one named deck; sibling decks in the same group are never consulted.
 *
 * <p>The tests below marked "currentGap" assert the behavior a linked deck group is
 * supposed to have (matching full regeneration) and currently <b>fail</b> — that is
 * intentional and is the point of this file: it documents a confirmed, real gap rather
 * than encoding the bug as expected behavior. The other tests establish the surrounding,
 * already-correct behavior so the gap isn't confused with something else nearby.
 */
public class OuicheListDeckGroupPropagationTest {

    private Card cardA;
    private Deck deckA;
    private Deck deckB;
    private ThemeCollection collection;

    private static Card card(String konamiId, String passCode, String imagePath) {
        Card c = new Card();
        c.setKonamiId(konamiId);
        c.setPassCode(passCode);
        c.setImagePath(imagePath);
        return c;
    }

    private static CardElement missingSlot(Card card) {
        CardElement e = new CardElement(card);
        e.setOwnershipStatus(OwnershipStatus.MISSING);
        return e;
    }

    @BeforeEach
    void setUp() {
        cardA = card("KID-001", "00000001", "img/a.jpg");

        deckA = new Deck();
        deckA.setName("DeckA");
        deckB = new Deck();
        deckB.setName("DeckB");

        collection = new ThemeCollection();
        collection.setName("TestCollection");
        collection.setConnectToWholeCollection(false); // non-loose: the group path under test
        collection.setCardsList(new ArrayList<>());

        DecksAndCollectionsList detailed = new DecksAndCollectionsList();
        detailed.addCollection(collection);
        OuicheList.setDetailedOuicheList(detailed);
        OuicheList.setUnusedCards(new ArrayList<>());
        OuicheList.setMaOuicheList(new LinkedHashMap<>());
        OuicheList.setMaOuicheListCounts(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandard(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandardCounts(new LinkedHashMap<>());
        OuicheList.setMyCardsCollection(new OwnedCardsCollection());
    }

    // =========================================================================
    // Control: decks in the same collection but NOT in the same linked group
    // never affect each other. (Sanity check — should pass regardless of the
    // group-propagation gap below.)
    // =========================================================================

    @Test
    void unlinkedSiblingDecks_addDoesNotCrossOver() {
        deckB.getMainDeck().add(missingSlot(cardA));
        // Two separate, unlinked groups — DeckA and DeckB are never in the same group.
        collection.setLinkedDecks(List.of(List.of(deckA), List.of(deckB)));

        OuicheList.getUnusedCards().add(new CardElement(cardA));
        CardElement newSlot = new CardElement(cardA);
        OuicheList.onDeckCardAdded(newSlot, "DeckA", "main", "TestCollection", Integer.MAX_VALUE);

        assertEquals(OwnershipStatus.OWNED, newSlot.getOwnershipStatus());
        assertEquals(OwnershipStatus.MISSING, deckB.getMainDeck().get(0).getOwnershipStatus(),
                "DeckB is not in DeckA's group, so it must not be affected");
    }

    // =========================================================================
    // The confirmed gap: free within-group propagation is missing on the
    // incremental ADD path.
    // =========================================================================

    @Test
    void linkedDeckGroup_singleCardAdd_doesNotPropagateToSiblingDeck_currentGap() {
        deckB.getMainDeck().add(missingSlot(cardA)); // DeckB already wants cardA
        collection.setLinkedDecks(List.of(List.of(deckA, deckB))); // same group

        OuicheList.getUnusedCards().add(new CardElement(cardA));
        CardElement newSlotInDeckA = new CardElement(cardA);
        OuicheList.onDeckCardAdded(newSlotInDeckA, "DeckA", "main", "TestCollection", Integer.MAX_VALUE);

        assertEquals(OwnershipStatus.OWNED, newSlotInDeckA.getOwnershipStatus(),
                "DeckA's own new slot should still resolve normally from the unused pool");

        // Expected (per full-regen propagateGroupValidation): DeckB's matching slot in the
        // same linked group should become OWNED for free, no extra owned copy required.
        // Currently fails — the incremental updater has no such propagation.
        assertEquals(OwnershipStatus.OWNED, deckB.getMainDeck().get(0).getOwnershipStatus(),
                "Sibling deck in the same linked group did not receive free ownership propagation");
    }

    @Test
    void linkedDeckGroup_multiCardAdd_doesNotPropagateToSiblingSlots_currentGap() {
        deckB.getMainDeck().add(missingSlot(cardA));
        deckB.getMainDeck().add(missingSlot(cardA));
        collection.setLinkedDecks(List.of(List.of(deckA, deckB)));

        OuicheList.getUnusedCards().add(new CardElement(cardA));
        OuicheList.getUnusedCards().add(new CardElement(cardA));
        CardElement slot1 = new CardElement(cardA);
        CardElement slot2 = new CardElement(cardA);
        OuicheList.onDeckCardAdded(slot1, "DeckA", "main", "TestCollection", 0);
        OuicheList.onDeckCardAdded(slot2, "DeckA", "main", "TestCollection", 1);

        long deckBOwnedCount = deckB.getMainDeck().stream()
                .filter(e -> e.getOwnershipStatus() == OwnershipStatus.OWNED)
                .count();

        // Answers the "is the multiple validation functional?" question directly:
        // currently 0 of 2 — the gap applies identically to single- and multi-card adds.
        assertEquals(2, deckBOwnedCount,
                "Both of DeckB's matching MISSING slots should propagate to OWNED for free");
    }

    @Test
    void linkedDeckGroup_addToDeckB_doesNotPropagateBackToDeckA_currentGap() {
        // Same gap, opposite direction — confirms it isn't specific to which deck in the
        // pair happens to receive the direct add.
        deckA.getMainDeck().add(missingSlot(cardA));
        collection.setLinkedDecks(List.of(List.of(deckA, deckB)));

        OuicheList.getUnusedCards().add(new CardElement(cardA));
        CardElement newSlotInDeckB = new CardElement(cardA);
        OuicheList.onDeckCardAdded(newSlotInDeckB, "DeckB", "main", "TestCollection", Integer.MAX_VALUE);

        assertEquals(OwnershipStatus.OWNED, newSlotInDeckB.getOwnershipStatus());
        assertEquals(OwnershipStatus.OWNED, deckA.getMainDeck().get(0).getOwnershipStatus(),
                "Sibling deck in the same linked group did not receive free ownership propagation");
    }

    // =========================================================================
    // Clarifying test: a cross-group MOVE (remove from A, add to B) can look like
    // it "propagates" to a linked sibling, but that's the pre-existing, general,
    // group-agnostic "reallocate this freed copy to the next needy MISSING slot
    // sharing this KonamiId anywhere in the OuicheList" mechanism on the REMOVE
    // side (already covered by OuicheListUpdaterTest) — not a deck-group-specific
    // mechanism. This test exists so that observation isn't mistaken for the gap
    // above being fixed.
    // =========================================================================

    @Test
    void removeFreeingACopy_canIncidentallySatisfyALinkedSibling_viaGeneralPropagation_notGroupLogic() {
        CardElement collectionSlot = new CardElement(cardA);
        collectionSlot.setOwnershipStatus(OwnershipStatus.OWNED);
        collection.getCardsList().add(collectionSlot);

        deckB.getMainDeck().add(missingSlot(cardA));
        collection.setLinkedDecks(List.of(List.of(deckA, deckB)));

        // Remove the OWNED slot from the collection's cardsList (no unused-copy add
        // involved) — the general "propagate to next MISSING KonamiId match anywhere"
        // logic in onDeckCardRemoved finds DeckB's slot purely by KonamiId, with no idea
        // that DeckB happens to be in a linked group at all.
        OuicheList.onDeckCardRemoved(new CardElement(cardA), null, null, "TestCollection");

        assertEquals(OwnershipStatus.OWNED, deckB.getMainDeck().get(0).getOwnershipStatus(),
                "This passes via the general cross-list propagation on removal, "
                        + "unrelated to deck-group membership");
    }
}