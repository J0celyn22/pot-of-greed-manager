package Model.CardsLists;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for the three fixes made alongside the incremental-update feature:
 * <ul>
 *   <li>index-aware insertion in {@link OuicheList#onDeckCardAdded(CardElement, String,
 *       String, String, int)} (previously always appended);</li>
 *   <li>{@link OuicheList#onDeckCardMoved} — intra-group reorder support, which must
 *       reposition the existing detailed-list slot without disturbing its ownership
 *       status;</li>
 *   <li>the {@link CardElement#CardElement(Card)} / {@code findUnusedMatch}
 *       specific-artwork fix, so a slot requesting a non-default artwork never accepts
 *       an owned copy of a different artwork.</li>
 * </ul>
 */
public class OuicheListMoveAndArtworkTest {

    private Card cardX;
    private Card cardY;
    private Deck detailedDeck;
    private ThemeCollection detailedCollection;

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

    private static CardElement ownedSlot(Card card) {
        CardElement e = new CardElement(card);
        e.setOwnershipStatus(OwnershipStatus.OWNED);
        return e;
    }

    private static CardElement substandardSlot(Card card) {
        CardElement e = new CardElement(card);
        e.setOwnershipStatus(OwnershipStatus.OWNED_SUBSTANDARD);
        return e;
    }

    @BeforeEach
    void setUp() {
        cardX = card("KID-901", "00000901", "img/x.jpg");
        cardY = card("KID-902", "00000902", "img/y.jpg");

        detailedDeck = new Deck();
        detailedDeck.setName("TestDeck");

        detailedCollection = new ThemeCollection();
        detailedCollection.setName("TestCollection");
        detailedCollection.setCardsList(new ArrayList<>());

        DecksAndCollectionsList ouicheList = new DecksAndCollectionsList();
        ouicheList.addDeck(detailedDeck);
        ouicheList.addCollection(detailedCollection);

        OuicheList.setDetailedOuicheList(ouicheList);
        OuicheList.setUnusedCards(new ArrayList<>());
        OuicheList.setMaOuicheList(new LinkedHashMap<>());
        OuicheList.setMaOuicheListCounts(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandard(new LinkedHashMap<>());
        OuicheList.setMaOuicheListSubstandardCounts(new LinkedHashMap<>());
        OuicheList.setMyCardsCollection(new OwnedCardsCollection());
    }

    // =========================================================================
    // Index-aware insertion (onDeckCardAdded with an explicit insertionIndex)
    // =========================================================================

    @ParameterizedTest(name = "insertAtIndex_deckMain [{0}]")
    @CsvSource({"0", "1", "2"})
    void deckCardAdded_respectsExplicitInsertionIndex(int index) {
        detailedDeck.getMainDeck().add(missingSlot(card("KID-1", "1", null)));
        detailedDeck.getMainDeck().add(missingSlot(card("KID-2", "2", null)));

        CardElement inserted = new CardElement(cardX);
        OuicheList.onDeckCardAdded(inserted, "TestDeck", "main", null, index);

        assertEquals(3, detailedDeck.getMainDeck().size());
        assertSame(inserted, detailedDeck.getMainDeck().get(index),
                "New slot should land at exactly the requested index");
    }

    @Test
    void deckCardAdded_indexBeyondSize_clampsToAppend() {
        detailedDeck.getMainDeck().add(missingSlot(card("KID-1", "1", null)));

        CardElement inserted = new CardElement(cardX);
        OuicheList.onDeckCardAdded(inserted, "TestDeck", "main", null, 999);

        assertEquals(2, detailedDeck.getMainDeck().size());
        assertSame(inserted, detailedDeck.getMainDeck().get(1), "Should clamp to append at the end");
    }

    @Test
    void deckCardAdded_defaultFourArgOverload_stillAppends() {
        detailedDeck.getMainDeck().add(missingSlot(card("KID-1", "1", null)));

        CardElement inserted = new CardElement(cardX);
        OuicheList.onDeckCardAdded(inserted, "TestDeck", "main", null); // 4-arg legacy overload

        assertSame(inserted, detailedDeck.getMainDeck().get(1),
                "The pre-existing 4-arg overload must keep its append-only behavior "
                        + "for callers that don't know a position (menu actions, paste)");
    }

    @Test
    void collectionCardAdded_respectsExplicitInsertionIndex() {
        detailedCollection.getCardsList().add(missingSlot(card("KID-1", "1", null)));
        detailedCollection.getCardsList().add(missingSlot(card("KID-2", "2", null)));

        CardElement inserted = new CardElement(cardX);
        OuicheList.onDeckCardAdded(inserted, null, null, "TestCollection", 1);

        assertSame(inserted, detailedCollection.getCardsList().get(1));
    }

    // =========================================================================
    // onDeckCardMoved — intra-group reorder
    // =========================================================================

    @Test
    void deckCardMoved_repositionsSlot_andPreservesOwnedSubstandardStatus() {
        CardElement slotX = substandardSlot(cardX);
        CardElement slotY = missingSlot(cardY);
        detailedDeck.getMainDeck().add(slotX);
        detailedDeck.getMainDeck().add(slotY);

        OuicheList.onDeckCardMoved(new CardElement(cardX), "TestDeck", "main", null, 1);

        assertEquals(2, detailedDeck.getMainDeck().size());
        assertEquals("KID-901", detailedDeck.getMainDeck().get(1).getCard().getKonamiId(),
                "cardX should now be at index 1");
        assertEquals(OwnershipStatus.OWNED_SUBSTANDARD,
                detailedDeck.getMainDeck().get(1).getOwnershipStatus(),
                "Reordering must not touch ownership status");
    }

    @Test
    void deckCardMoved_preservesOwnedStatus_collectionCardsList() {
        CardElement slotX = ownedSlot(cardX);
        CardElement slotY = missingSlot(cardY);
        detailedCollection.getCardsList().add(slotX);
        detailedCollection.getCardsList().add(slotY);

        OuicheList.onDeckCardMoved(new CardElement(cardX), null, null, "TestCollection", 1);

        assertEquals("KID-901", detailedCollection.getCardsList().get(1).getCard().getKonamiId());
        assertEquals(OwnershipStatus.OWNED, detailedCollection.getCardsList().get(1).getOwnershipStatus());
    }

    @Test
    void deckCardMoved_noMatchingSlot_noOp() {
        detailedDeck.getMainDeck().add(missingSlot(cardY)); // only cardY present, not cardX

        assertDoesNotThrow(() ->
                OuicheList.onDeckCardMoved(new CardElement(cardX), "TestDeck", "main", null, 0));

        assertEquals(1, detailedDeck.getMainDeck().size(), "Nothing should be inserted or removed");
    }

    @Test
    void deckCardMoved_indexBeyondSize_clampsToAppend() {
        CardElement slotX = missingSlot(cardX);
        detailedDeck.getMainDeck().add(slotX);
        detailedDeck.getMainDeck().add(missingSlot(cardY));

        OuicheList.onDeckCardMoved(new CardElement(cardX), "TestDeck", "main", null, 999);

        assertEquals("KID-901", detailedDeck.getMainDeck().get(1).getCard().getKonamiId(),
                "Out-of-range index should clamp to the end");
    }

    @Test
    void deckCardMoved_unknownDeckName_noOp() {
        detailedDeck.getMainDeck().add(missingSlot(cardX));
        assertDoesNotThrow(() ->
                OuicheList.onDeckCardMoved(new CardElement(cardX), "NoSuchDeck", "main", null, 0));
        assertEquals(1, detailedDeck.getMainDeck().size());
    }

    // =========================================================================
    // Specific-artwork matching fix (CardElement(Card) + findUnusedMatch)
    // =========================================================================

    @Test
    void cardElement_fromDefaultArtworkCard_isNotSpecificArtwork() {
        Card defaultArt = card("KID-903", "00000903", "img/default.jpg");
        defaultArt.setArtNumber("1");

        CardElement element = new CardElement(defaultArt);

        assertFalse(element.getSpecificArtwork(), "Artwork \"1\" is the default — should not be flagged");
    }

    @Test
    void cardElement_fromAlternateArtworkCard_isSpecificArtwork() {
        Card altArt = card("KID-903", "00000903", "img/alt.jpg");
        altArt.setArtNumber("2");

        CardElement element = new CardElement(altArt);

        assertTrue(element.getSpecificArtwork(), "Artwork \"2\" is a named alternate — should be flagged");
    }

    @Test
    void deckCardAdded_specificArtworkSlot_notSatisfiedByDefaultArtworkSurplus() {
        Card defaultArt = card("KID-904", "00000904", "img/default.jpg");
        defaultArt.setArtNumber("1");
        Card altArt = card("KID-904", "00000904", "img/alt.jpg");
        altArt.setArtNumber("2");

        for (int i = 0; i < 3; i++) {
            OuicheList.getUnusedCards().add(new CardElement(defaultArt));
        }

        CardElement altSlot = new CardElement(altArt);
        OuicheList.onDeckCardAdded(altSlot, "TestDeck", "main", null);

        assertEquals(OwnershipStatus.MISSING, altSlot.getOwnershipStatus(),
                "3 owned copies of the DEFAULT artwork must not satisfy a slot requesting "
                        + "a different, specific artwork");
        assertEquals(3, OuicheList.getUnusedCards().size(), "None of the default-art copies should be consumed");
    }

    @Test
    void deckCardAdded_specificArtworkSlot_satisfiedByMatchingArtworkCopy() {
        Card altArt = card("KID-905", "00000905", "img/alt.jpg");
        altArt.setArtNumber("2");

        OuicheList.getUnusedCards().add(new CardElement(altArt));

        CardElement altSlot = new CardElement(altArt);
        OuicheList.onDeckCardAdded(altSlot, "TestDeck", "main", null);

        assertEquals(OwnershipStatus.OWNED, altSlot.getOwnershipStatus());
        assertTrue(OuicheList.getUnusedCards().isEmpty());
    }

    @Test
    void deckCardAdded_specificArtworkSlot_wrongArtworkNotEvenSubstandard_round2AlsoRespectsArtwork() {
        // A wrong-artwork copy, even of otherwise-acceptable-any-quality condition, must
        // not satisfy round 2 (qualityRequired=false) either — artwork exactness applies
        // to both rounds, not just the strict one.
        Card defaultArt = card("KID-906", "00000906", "img/default.jpg");
        defaultArt.setArtNumber("1");
        Card altArt = card("KID-906", "00000906", "img/alt.jpg");
        altArt.setArtNumber("2");

        CardElement damagedDefaultCopy = new CardElement(defaultArt);
        damagedDefaultCopy.setCondition(CardCondition.DAMAGED);
        OuicheList.getUnusedCards().add(damagedDefaultCopy);

        CardElement altSlot = new CardElement(altArt);
        altSlot.setCondition(CardCondition.NEAR_MINT);
        OuicheList.onDeckCardAdded(altSlot, "TestDeck", "main", null);

        assertEquals(OwnershipStatus.MISSING, altSlot.getOwnershipStatus(),
                "Wrong artwork must be rejected even under the loose (any-quality) round");
    }

    @Test
    void deckCardAdded_specificArtworkSlot_wrongQualitySameArtwork_isSubstandard() {
        // Control: same artwork, wrong quality — round 2 should still work normally.
        Card altArt = card("KID-907", "00000907", "img/alt.jpg");
        altArt.setArtNumber("2");

        CardElement damagedAltCopy = new CardElement(altArt);
        damagedAltCopy.setCondition(CardCondition.DAMAGED);
        OuicheList.getUnusedCards().add(damagedAltCopy);

        CardElement altSlot = new CardElement(altArt);
        altSlot.setCondition(CardCondition.NEAR_MINT);
        OuicheList.onDeckCardAdded(altSlot, "TestDeck", "main", null);

        assertEquals(OwnershipStatus.OWNED_SUBSTANDARD, altSlot.getOwnershipStatus());
    }
}