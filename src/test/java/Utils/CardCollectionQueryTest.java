package Utils;

import Model.CardsLists.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Partition-based tests for {@link CardCollectionQuery}.
 * <p>
 * Tracing {@code countInOwnedForDeckCombined}'s four documented priority
 * tiers by hand against its own control flow (not just reading the javadoc)
 * turned up something worth flagging prominently: Tiers 1 and 2 both search
 * every box in the collection before falling through, using the exact same
 * name-matching conditions Tier 3's "box matches" branch and all of Tier 4
 * rely on. That means by the time execution ever reaches Tier 3 or Tier 4,
 * Tier 1/2 have already proven no box or group is named after the deck —
 * so Tier 3's {@code boxMatchesDeck} branch can never fire, and Tier 4 can
 * never find anything either. In practice, whenever a deck's cards are only
 * findable via the generic "Main Deck"/"Extra Deck"/"Side Deck" convention
 * (Tier 3's fallthrough), the count returned is the sum across
 * <b>every</b> box in the whole collection, not just the boxes belonging to
 * that deck. See {@code countInOwned_tier3_fallthroughSumsAcrossUnrelatedBoxes_documentedLikelyBug}
 * below, which demonstrates this concretely. Flagging this as a likely real
 * bug, not fixing it — that's a production-code call for the maintainer.
 */
class CardCollectionQueryTest {

    private static CardElement cardWith(String konamiId) {
        Card card = new Card();
        card.setKonamiId(konamiId);
        card.setPassCode(konamiId);
        return new CardElement(card);
    }

    private static Card lookupCard(String konamiId) {
        Card card = new Card();
        card.setKonamiId(konamiId);
        card.setPassCode(konamiId);
        return card;
    }

    // ── countCardInList ──────────────────────────────────────────────────────

    @Test
    void countCardInList_nullList_returnsZero() {
        assertEquals(0, CardCollectionQuery.countCardInList(null, lookupCard("K1")));
    }

    @Test
    void countCardInList_nullCard_returnsZero() {
        assertEquals(0, CardCollectionQuery.countCardInList(List.of(cardWith("K1")), null));
    }

    @Test
    void countCardInList_emptyList_returnsZero() {
        assertEquals(0, CardCollectionQuery.countCardInList(List.of(), lookupCard("K1")));
    }

    @Test
    void countCardInList_skipsNullElementsAndElementsWithNullCard() {
        CardElement withoutCard = new CardElement(null, false, false, false, false);
        List<CardElement> list = new java.util.ArrayList<>();
        list.add(null);
        list.add(withoutCard);
        list.add(cardWith("K1"));

        assertEquals(1, CardCollectionQuery.countCardInList(list, lookupCard("K1")));
    }

    @Test
    void countCardInList_countsOnlyMatchingCards() {
        List<CardElement> list = List.of(cardWith("K1"), cardWith("K2"), cardWith("K1"));
        assertEquals(2, CardCollectionQuery.countCardInList(list, lookupCard("K1")));
    }

    @Test
    void countCardInList_noMatches_returnsZero() {
        List<CardElement> list = List.of(cardWith("K2"), cardWith("K3"));
        assertEquals(0, CardCollectionQuery.countCardInList(list, lookupCard("K1")));
    }

    // ── countInOwnedForDeckCombined ──────────────────────────────────────────

    @Test
    void countInOwned_nullOwned_returnsZero() {
        assertEquals(0, CardCollectionQuery.countInOwnedForDeckCombined(null, "MyDeck", lookupCard("K1")));
    }

    @Test
    void countInOwned_nullOwnedCollection_returnsZero() {
        OwnedCardsCollection owned = new OwnedCardsCollection();
        owned.setOwnedCollection(null);
        assertEquals(0, CardCollectionQuery.countInOwnedForDeckCombined(owned, "MyDeck", lookupCard("K1")));
    }

    @Test
    void countInOwned_nullDeckName_returnsZero() {
        OwnedCardsCollection owned = new OwnedCardsCollection();
        assertEquals(0, CardCollectionQuery.countInOwnedForDeckCombined(owned, null, lookupCard("K1")));
    }

    @Test
    void countInOwned_blankDeckName_returnsZero() {
        OwnedCardsCollection owned = new OwnedCardsCollection();
        assertEquals(0, CardCollectionQuery.countInOwnedForDeckCombined(owned, "   ", lookupCard("K1")));
    }

    @Test
    void countInOwned_tier1_boxNameMatches_sumsAllGroupsInBox() {
        Box box = new Box("MyDeck");
        CardsGroup group1 = new CardsGroup("Main Deck");
        group1.addCard(cardWith("K1"));
        CardsGroup group2 = new CardsGroup("Extra Deck");
        group2.addCard(cardWith("K1"));
        box.getContent().add(group1);
        box.getContent().add(group2);

        OwnedCardsCollection owned = new OwnedCardsCollection();
        owned.getOwnedCollection().add(box);

        assertEquals(2, CardCollectionQuery.countInOwnedForDeckCombined(owned, "MyDeck", lookupCard("K1")));
    }

    @Test
    void countInOwned_tier1_boxNameWithDecorators_sanitizedBeforeMatching() {
        Box box = new Box("==MyDeck==");
        CardsGroup group = new CardsGroup("Main Deck");
        group.addCard(cardWith("K1"));
        box.getContent().add(group);

        OwnedCardsCollection owned = new OwnedCardsCollection();
        owned.getOwnedCollection().add(box);

        assertEquals(1, CardCollectionQuery.countInOwnedForDeckCombined(owned, "MyDeck", lookupCard("K1")));
    }

    @Test
    void countInOwned_tier1_matchingBoxShortCircuits_evenWithZeroCopies() {
        Box matchingButEmpty = new Box("MyDeck"); // no groups at all -> sum is 0
        Box otherBoxWithRelevantGroup = new Box("SomeOtherBox");
        CardsGroup group = new CardsGroup("MyDeck"); // would satisfy Tier 2 if reached
        group.addCard(cardWith("K1"));
        otherBoxWithRelevantGroup.getContent().add(group);

        OwnedCardsCollection owned = new OwnedCardsCollection();
        owned.getOwnedCollection().add(matchingButEmpty);
        owned.getOwnedCollection().add(otherBoxWithRelevantGroup);

        assertEquals(0, CardCollectionQuery.countInOwnedForDeckCombined(owned, "MyDeck", lookupCard("K1")),
                "Tier 1's matching box returns immediately, even at 0, without falling through to Tier 2");
    }

    @Test
    void countInOwned_tier2_groupNameMatch_sumsOnlyThatGroup_notSiblingGroups() {
        // Documents the "Bug 3A" fix mentioned in the class javadoc: an unrelated
        // sibling group in the same box must not inflate the count.
        Box box = new Box("SomeBox"); // does not match "MyDeck"
        CardsGroup matchingGroup = new CardsGroup("MyDeck");
        matchingGroup.addCard(cardWith("K1"));
        CardsGroup unrelatedGroup = new CardsGroup("Unsorted");
        unrelatedGroup.addCard(cardWith("K1"));
        unrelatedGroup.addCard(cardWith("K1"));
        box.getContent().add(matchingGroup);
        box.getContent().add(unrelatedGroup);

        OwnedCardsCollection owned = new OwnedCardsCollection();
        owned.getOwnedCollection().add(box);

        assertEquals(1, CardCollectionQuery.countInOwnedForDeckCombined(owned, "MyDeck", lookupCard("K1")));
    }

    @Test
    void countInOwned_tier2_firstMatchingBoxWins_laterMatchingBoxIgnored() {
        Box firstBox = new Box("BoxA");
        CardsGroup firstGroup = new CardsGroup("MyDeck");
        firstGroup.addCard(cardWith("K1"));
        firstBox.getContent().add(firstGroup);

        Box secondBox = new Box("BoxB");
        CardsGroup secondGroup = new CardsGroup("MyDeck");
        secondGroup.addCard(cardWith("K1"));
        secondGroup.addCard(cardWith("K1"));
        secondBox.getContent().add(secondGroup);

        OwnedCardsCollection owned = new OwnedCardsCollection();
        owned.getOwnedCollection().add(firstBox);
        owned.getOwnedCollection().add(secondBox);

        assertEquals(1, CardCollectionQuery.countInOwnedForDeckCombined(owned, "MyDeck", lookupCard("K1")),
                "only the first box (in collection order) with a matching-named group counts");
    }

    @Test
    void countInOwned_tier3_fallthroughSumsAcrossUnrelatedBoxes_documentedLikelyBug() {
        // Neither box is named "MyDeck", and neither has a group literally
        // named "MyDeck" — so Tiers 1 and 2 find nothing. Both boxes do have
        // a "Main Deck" group, though, and Tier 3's fallthrough sums Main/
        // Extra/Side Deck groups across the WHOLE collection once reached,
        // not just a box belonging to "MyDeck". Two unrelated decks' Main
        // Deck counts get added together.
        Box unrelatedDeckOne = new Box("Dragon Deck");
        CardsGroup mainOne = new CardsGroup("Main Deck");
        mainOne.addCard(cardWith("K1"));
        mainOne.addCard(cardWith("K1"));
        unrelatedDeckOne.getContent().add(mainOne);

        Box unrelatedDeckTwo = new Box("Spellcaster Deck");
        CardsGroup mainTwo = new CardsGroup("Main Deck");
        mainTwo.addCard(cardWith("K1"));
        unrelatedDeckTwo.getContent().add(mainTwo);

        OwnedCardsCollection owned = new OwnedCardsCollection();
        owned.getOwnedCollection().add(unrelatedDeckOne);
        owned.getOwnedCollection().add(unrelatedDeckTwo);

        int result = CardCollectionQuery.countInOwnedForDeckCombined(owned, "MyDeck", lookupCard("K1"));

        assertEquals(3, result,
                "current behavior sums Main Deck occurrences across every box, " +
                        "not just ones actually related to \"MyDeck\" -- likely unintended");
    }

    @Test
    void countInOwned_noMatchAnywhere_returnsZero() {
        Box box = new Box("SomeBox");
        CardsGroup group = new CardsGroup("Unsorted");
        group.addCard(cardWith("K1"));
        box.getContent().add(group);

        OwnedCardsCollection owned = new OwnedCardsCollection();
        owned.getOwnedCollection().add(box);

        assertEquals(0, CardCollectionQuery.countInOwnedForDeckCombined(owned, "MyDeck", lookupCard("K1")));
    }

    @Test
    void countInOwned_nullBoxesAndGroupsSkippedGracefully_noCrash() {
        OwnedCardsCollection owned = new OwnedCardsCollection();
        owned.getOwnedCollection().add(null);
        Box boxWithNullContent = new Box("BoxWithNullContent");
        boxWithNullContent.setContent(null);
        owned.getOwnedCollection().add(boxWithNullContent);
        Box boxWithNullGroup = new Box("BoxWithNullGroup");
        boxWithNullGroup.getContent().add(null);
        owned.getOwnedCollection().add(boxWithNullGroup);

        assertDoesNotThrow(() ->
                CardCollectionQuery.countInOwnedForDeckCombined(owned, "MyDeck", lookupCard("K1")));
    }

    // ── isQualityUpgradeFor ──────────────────────────────────────────────────

    @Test
    void isQualityUpgradeFor_nullOwnedElement_returnsFalse() {
        assertFalse(CardCollectionQuery.isQualityUpgradeFor(List.of(cardWith("K1")), null));
    }

    @Test
    void isQualityUpgradeFor_nullTargetList_returnsFalse() {
        assertFalse(CardCollectionQuery.isQualityUpgradeFor(null, cardWith("K1")));
    }

    @Test
    void isQualityUpgradeFor_nullCardOnElement_returnsFalse() {
        CardElement noCard = new CardElement(null, false, false, false, false);
        assertFalse(CardCollectionQuery.isQualityUpgradeFor(List.of(cardWith("K1")), noCard));
    }

    @Test
    void isQualityUpgradeFor_noMatchingExistingCopies_returnsFalse() {
        List<CardElement> targetList = List.of(cardWith("K2"), cardWith("K3"));
        assertFalse(CardCollectionQuery.isQualityUpgradeFor(targetList, cardWith("K1")));
    }

    @Test
    void isQualityUpgradeFor_matchingExistingCopyWithBetterCondition_returnsTrue() {
        CardElement existing = cardWith("K1");
        existing.setCondition(CardCondition.POOR);
        List<CardElement> targetList = List.of(existing);

        CardElement candidate = cardWith("K1");
        candidate.setCondition(CardCondition.MINT);

        assertTrue(CardCollectionQuery.isQualityUpgradeFor(targetList, candidate));
    }
}