package Controller;

import Model.CardsLists.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link NavigationDragDrop}, the pure model-mutation helpers behind
 * navigation-menu drag-and-drop reordering. Nothing here touches JavaFX, so every
 * method is directly testable against plain model fixtures.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Box reordering/nesting — findBoxParentList, moveBoxBefore, moveBoxAfter,
 *       nestBoxInto (including the circular-nesting guard), moveBoxToTopLevel</li>
 *   <li>CardsGroup (category) reordering — findCategoryParent, moveCategoryBefore,
 *       moveCategoryAfter, moveCategoryIntoBox</li>
 *   <li>Deck reordering/moving — findDeckLocation, removeDeckFromModel,
 *       moveDeckBefore, moveDeckAfter, moveDeckToCollection, moveDeckToStandalone,
 *       moveDeckToNewUnit</li>
 *   <li>DeckLocation — isStandalone, getUnit</li>
 * </ul>
 * Not every private branch is exercised — the goal is confidence in the reordering
 * behavior actually exposed to the drag-and-drop wiring, not exhaustive coverage.
 */
public class NavigationDragDropTest {

    // ── Fixture helpers ──────────────────────────────────────────────────────

    private static Box box(String name) {
        return new Box(name);
    }

    private static CardsGroup group(String name) {
        return new CardsGroup(name);
    }

    private static Deck deck(String name) {
        Deck newDeck = new Deck();
        newDeck.setName(name);
        return newDeck;
    }

    private static ThemeCollection themeCollection(String name) {
        ThemeCollection collection = new ThemeCollection();
        collection.setName(name);
        return collection;
    }

    private static OwnedCardsCollection ownedCollectionOf(Box... topLevelBoxes) {
        OwnedCardsCollection collection = new OwnedCardsCollection();
        collection.setOwnedCollection(new ArrayList<>(List.of(topLevelBoxes)));
        return collection;
    }

    private static DecksAndCollectionsList dacOf(List<Deck> standaloneDecks,
                                                 List<ThemeCollection> collections) {
        DecksAndCollectionsList decksAndCollections = new DecksAndCollectionsList();
        decksAndCollections.setDecks(new ArrayList<>(standaloneDecks));
        decksAndCollections.setCollections(new ArrayList<>(collections));
        return decksAndCollections;
    }

    private static List<Deck> unitOf(Deck... decks) {
        return new ArrayList<>(List.of(decks));
    }

    // ── findBoxParentList ────────────────────────────────────────────────────

    @Test
    void findBoxParentListReturnsTopLevelListWhenBoxIsTopLevel() {
        Box boxA = box("A");
        Box boxB = box("B");
        OwnedCardsCollection collection = ownedCollectionOf(boxA, boxB);

        List<Box> parent = NavigationDragDrop.findBoxParentList(boxB, collection);

        assertSame(collection.getOwnedCollection(), parent);
    }

    @Test
    void findBoxParentListReturnsSubBoxListWhenBoxIsNested() {
        Box parentBox = box("Parent");
        Box childBox = box("Child");
        parentBox.getSubBoxes().add(childBox);
        OwnedCardsCollection collection = ownedCollectionOf(parentBox);

        List<Box> parent = NavigationDragDrop.findBoxParentList(childBox, collection);

        assertSame(parentBox.getSubBoxes(), parent);
    }

    @Test
    void findBoxParentListReturnsNullWhenBoxNotFound() {
        Box unrelatedBox = box("Unrelated");
        OwnedCardsCollection collection = ownedCollectionOf(box("A"));

        assertNull(NavigationDragDrop.findBoxParentList(unrelatedBox, collection));
    }

    // ── moveBoxBefore / moveBoxAfter ─────────────────────────────────────────

    @Test
    void moveBoxBeforeReordersWithinTheSameTopLevelList() {
        Box boxA = box("A");
        Box boxB = box("B");
        Box boxC = box("C");
        OwnedCardsCollection collection = ownedCollectionOf(boxA, boxB, boxC);

        boolean changed = NavigationDragDrop.moveBoxBefore(boxC, boxA, collection);

        assertTrue(changed);
        assertEquals(List.of(boxC, boxA, boxB), collection.getOwnedCollection());
    }

    @Test
    void moveBoxAfterReordersWithinTheSameTopLevelList() {
        Box boxA = box("A");
        Box boxB = box("B");
        Box boxC = box("C");
        OwnedCardsCollection collection = ownedCollectionOf(boxA, boxB, boxC);

        boolean changed = NavigationDragDrop.moveBoxAfter(boxA, boxC, collection);

        assertTrue(changed);
        assertEquals(List.of(boxB, boxC, boxA), collection.getOwnedCollection());
    }

    @Test
    void moveBoxBeforePromotesASubBoxToTopLevelWhenTargetIsTopLevel() {
        Box topBox = box("Top");
        Box subBox = box("Sub");
        topBox.getSubBoxes().add(subBox);
        Box otherTopBox = box("OtherTop");
        OwnedCardsCollection collection = ownedCollectionOf(topBox, otherTopBox);

        boolean changed = NavigationDragDrop.moveBoxAfter(subBox, otherTopBox, collection);

        assertTrue(changed);
        assertTrue(topBox.getSubBoxes().isEmpty());
        assertEquals(List.of(topBox, otherTopBox, subBox), collection.getOwnedCollection());
    }

    @Test
    void moveBoxBeforeIsNoOpWhenDraggedAndTargetAreTheSame() {
        Box boxA = box("A");
        OwnedCardsCollection collection = ownedCollectionOf(boxA);

        assertFalse(NavigationDragDrop.moveBoxBefore(boxA, boxA, collection));
    }

    @Test
    void moveBoxBeforeIsNoOpWhenTargetNotInCollection() {
        Box boxA = box("A");
        Box unrelatedTarget = box("Unrelated");
        OwnedCardsCollection collection = ownedCollectionOf(boxA);

        assertFalse(NavigationDragDrop.moveBoxBefore(boxA, unrelatedTarget, collection));
        assertEquals(List.of(boxA), collection.getOwnedCollection());
    }

    // ── nestBoxInto ──────────────────────────────────────────────────────────

    @Test
    void nestBoxIntoMovesDraggedBoxIntoTargetsSubBoxes() {
        Box draggedBox = box("Dragged");
        Box targetBox = box("Target");
        OwnedCardsCollection collection = ownedCollectionOf(draggedBox, targetBox);

        boolean changed = NavigationDragDrop.nestBoxInto(draggedBox, targetBox, collection);

        assertTrue(changed);
        assertEquals(List.of(targetBox), collection.getOwnedCollection());
        assertEquals(List.of(draggedBox), targetBox.getSubBoxes());
    }

    @Test
    void nestBoxIntoRefusesToCreateACircularNesting() {
        Box outerBox = box("Outer");
        Box innerBox = box("Inner");
        outerBox.getSubBoxes().add(innerBox);
        OwnedCardsCollection collection = ownedCollectionOf(outerBox);

        // Dragging Outer into its own descendant Inner would create a cycle.
        boolean changed = NavigationDragDrop.nestBoxInto(outerBox, innerBox, collection);

        assertFalse(changed);
        assertEquals(List.of(outerBox), collection.getOwnedCollection());
        assertEquals(List.of(innerBox), outerBox.getSubBoxes());
    }

    // ── moveBoxToTopLevel ────────────────────────────────────────────────────

    @Test
    void moveBoxToTopLevelPromotesANestedBox() {
        Box parentBox = box("Parent");
        Box childBox = box("Child");
        parentBox.getSubBoxes().add(childBox);
        OwnedCardsCollection collection = ownedCollectionOf(parentBox);

        boolean changed = NavigationDragDrop.moveBoxToTopLevel(childBox, collection);

        assertTrue(changed);
        assertTrue(parentBox.getSubBoxes().isEmpty());
        assertEquals(List.of(parentBox, childBox), collection.getOwnedCollection());
    }

    @Test
    void moveBoxToTopLevelIsNoOpWhenAlreadyTopLevel() {
        Box boxA = box("A");
        OwnedCardsCollection collection = ownedCollectionOf(boxA);

        assertFalse(NavigationDragDrop.moveBoxToTopLevel(boxA, collection));
        assertEquals(List.of(boxA), collection.getOwnedCollection());
    }

    // ── findCategoryParent ───────────────────────────────────────────────────

    @Test
    void findCategoryParentFindsGroupInTopLevelBox() {
        Box parentBox = box("Parent");
        CardsGroup categoryGroup = group("Category");
        parentBox.getContent().add(categoryGroup);
        OwnedCardsCollection collection = ownedCollectionOf(parentBox);

        assertSame(parentBox, NavigationDragDrop.findCategoryParent(categoryGroup, collection));
    }

    @Test
    void findCategoryParentFindsGroupInSubBox() {
        Box topBox = box("Top");
        Box subBox = box("Sub");
        CardsGroup categoryGroup = group("Category");
        subBox.getContent().add(categoryGroup);
        topBox.getSubBoxes().add(subBox);
        OwnedCardsCollection collection = ownedCollectionOf(topBox);

        assertSame(subBox, NavigationDragDrop.findCategoryParent(categoryGroup, collection));
    }

    @Test
    void findCategoryParentReturnsNullWhenNotFound() {
        OwnedCardsCollection collection = ownedCollectionOf(box("Empty"));

        assertNull(NavigationDragDrop.findCategoryParent(group("Orphan"), collection));
    }

    // ── moveCategoryBefore / moveCategoryAfter / moveCategoryIntoBox ────────

    @Test
    void moveCategoryBeforeReordersWithinTheSameBox() {
        Box parentBox = box("Parent");
        CardsGroup groupOne = group("One");
        CardsGroup groupTwo = group("Two");
        parentBox.getContent().add(groupOne);
        parentBox.getContent().add(groupTwo);
        OwnedCardsCollection collection = ownedCollectionOf(parentBox);

        boolean changed = NavigationDragDrop.moveCategoryBefore(groupTwo, groupOne, collection);

        assertTrue(changed);
        assertEquals(List.of(groupTwo, groupOne), parentBox.getContent());
    }

    @Test
    void moveCategoryAfterMovesGroupAcrossBoxes() {
        Box sourceBox = box("Source");
        Box destinationBox = box("Destination");
        CardsGroup movedGroup = group("Moved");
        CardsGroup anchorGroup = group("Anchor");
        sourceBox.getContent().add(movedGroup);
        destinationBox.getContent().add(anchorGroup);
        OwnedCardsCollection collection = ownedCollectionOf(sourceBox, destinationBox);

        boolean changed = NavigationDragDrop.moveCategoryAfter(movedGroup, anchorGroup, collection);

        assertTrue(changed);
        assertTrue(sourceBox.getContent().isEmpty());
        assertEquals(List.of(anchorGroup, movedGroup), destinationBox.getContent());
    }

    @Test
    void moveCategoryIntoBoxAppendsGroupToTargetsContent() {
        Box sourceBox = box("Source");
        Box destinationBox = box("Destination");
        CardsGroup movedGroup = group("Moved");
        sourceBox.getContent().add(movedGroup);
        OwnedCardsCollection collection = ownedCollectionOf(sourceBox, destinationBox);

        boolean changed = NavigationDragDrop.moveCategoryIntoBox(movedGroup, destinationBox, collection);

        assertTrue(changed);
        assertTrue(sourceBox.getContent().isEmpty());
        assertEquals(List.of(movedGroup), destinationBox.getContent());
    }

    @Test
    void moveCategoryIntoBoxIsNoOpWhenAlreadyDirectlyInsideTarget() {
        Box parentBox = box("Parent");
        CardsGroup categoryGroup = group("Category");
        parentBox.getContent().add(categoryGroup);
        OwnedCardsCollection collection = ownedCollectionOf(parentBox);

        assertFalse(NavigationDragDrop.moveCategoryIntoBox(categoryGroup, parentBox, collection));
        assertEquals(List.of(categoryGroup), parentBox.getContent());
    }

    // ── findDeckLocation / removeDeckFromModel ──────────────────────────────

    @Test
    void findDeckLocationLocatesDeckInsideACollectionUnit() {
        Deck linkedDeck = deck("Linked");
        ThemeCollection collection = themeCollection("Collection");
        collection.getLinkedDecks().add(unitOf(linkedDeck));
        DecksAndCollectionsList decksAndCollections = dacOf(List.of(), List.of(collection));

        NavigationDragDrop.DeckLocation location =
                NavigationDragDrop.findDeckLocation(linkedDeck, decksAndCollections);

        assertFalse(location.isStandalone());
        assertSame(collection, location.collection);
        assertEquals(0, location.unitIndex);
    }

    @Test
    void findDeckLocationReportsStandaloneWhenDeckIsNotInAnyCollection() {
        Deck standaloneDeck = deck("Standalone");
        DecksAndCollectionsList decksAndCollections = dacOf(List.of(standaloneDeck), List.of());

        NavigationDragDrop.DeckLocation location =
                NavigationDragDrop.findDeckLocation(standaloneDeck, decksAndCollections);

        assertTrue(location.isStandalone());
        assertNull(location.getUnit());
    }

    @Test
    void removeDeckFromModelPrunesUnitWhenItBecomesEmpty() {
        Deck onlyDeckInUnit = deck("Solo");
        ThemeCollection collection = themeCollection("Collection");
        collection.getLinkedDecks().add(unitOf(onlyDeckInUnit));
        DecksAndCollectionsList decksAndCollections = dacOf(List.of(), List.of(collection));

        NavigationDragDrop.removeDeckFromModel(onlyDeckInUnit, decksAndCollections);

        assertTrue(collection.getLinkedDecks().isEmpty());
    }

    @Test
    void removeDeckFromModelKeepsUnitWhenOtherDecksRemain() {
        Deck deckToRemove = deck("Remove");
        Deck deckToKeep = deck("Keep");
        ThemeCollection collection = themeCollection("Collection");
        collection.getLinkedDecks().add(unitOf(deckToRemove, deckToKeep));
        DecksAndCollectionsList decksAndCollections = dacOf(List.of(), List.of(collection));

        NavigationDragDrop.removeDeckFromModel(deckToRemove, decksAndCollections);

        assertEquals(1, collection.getLinkedDecks().size());
        assertEquals(List.of(deckToKeep), collection.getLinkedDecks().get(0));
    }

    @Test
    void removeDeckFromModelRemovesFromStandaloneList() {
        Deck standaloneDeck = deck("Standalone");
        DecksAndCollectionsList decksAndCollections = dacOf(List.of(standaloneDeck), List.of());

        NavigationDragDrop.removeDeckFromModel(standaloneDeck, decksAndCollections);

        assertTrue(decksAndCollections.getDecks().isEmpty());
    }

    // ── moveDeckBefore / moveDeckAfter ───────────────────────────────────────

    @Test
    void moveDeckBeforeReordersWithinTheStandaloneList() {
        Deck deckA = deck("A");
        Deck deckB = deck("B");
        Deck deckC = deck("C");
        DecksAndCollectionsList decksAndCollections = dacOf(List.of(deckA, deckB, deckC), List.of());

        boolean changed = NavigationDragDrop.moveDeckBefore(deckC, deckA, decksAndCollections);

        assertTrue(changed);
        assertEquals(List.of(deckC, deckA, deckB), decksAndCollections.getDecks());
    }

    @Test
    void moveDeckAfterMovesAStandaloneDeckIntoACollectionUnit() {
        Deck standaloneDeck = deck("Standalone");
        Deck targetDeck = deck("Target");
        ThemeCollection collection = themeCollection("Collection");
        collection.getLinkedDecks().add(unitOf(targetDeck));
        DecksAndCollectionsList decksAndCollections =
                dacOf(List.of(standaloneDeck), List.of(collection));

        boolean changed =
                NavigationDragDrop.moveDeckAfter(standaloneDeck, targetDeck, decksAndCollections);

        assertTrue(changed);
        assertTrue(decksAndCollections.getDecks().isEmpty());
        assertEquals(List.of(targetDeck, standaloneDeck), collection.getLinkedDecks().get(0));
    }

    @Test
    void moveDeckBeforeIsNoOpWhenDraggedAndTargetAreTheSame() {
        Deck theDeck = deck("Solo");
        DecksAndCollectionsList decksAndCollections = dacOf(List.of(theDeck), List.of());

        assertFalse(NavigationDragDrop.moveDeckBefore(theDeck, theDeck, decksAndCollections));
    }

    // ── moveDeckToCollection / moveDeckToStandalone ─────────────────────────

    @Test
    void moveDeckToCollectionAppendsANewSingletonUnit() {
        Deck standaloneDeck = deck("Standalone");
        ThemeCollection targetCollection = themeCollection("Target");
        DecksAndCollectionsList decksAndCollections =
                dacOf(List.of(standaloneDeck), List.of(targetCollection));

        boolean changed = NavigationDragDrop.moveDeckToCollection(
                standaloneDeck, targetCollection, decksAndCollections);

        assertTrue(changed);
        assertTrue(decksAndCollections.getDecks().isEmpty());
        assertEquals(List.of(List.of(standaloneDeck)), targetCollection.getLinkedDecks());
    }

    @Test
    void moveDeckToStandaloneMovesALinkedDeckOut() {
        Deck linkedDeck = deck("Linked");
        ThemeCollection collection = themeCollection("Collection");
        collection.getLinkedDecks().add(unitOf(linkedDeck));
        DecksAndCollectionsList decksAndCollections = dacOf(new ArrayList<>(), List.of(collection));

        boolean changed = NavigationDragDrop.moveDeckToStandalone(linkedDeck, decksAndCollections);

        assertTrue(changed);
        assertTrue(collection.getLinkedDecks().isEmpty());
        assertEquals(List.of(linkedDeck), decksAndCollections.getDecks());
    }

    @Test
    void moveDeckToStandaloneIsNoOpWhenAlreadyStandalone() {
        Deck standaloneDeck = deck("Standalone");
        DecksAndCollectionsList decksAndCollections = dacOf(List.of(standaloneDeck), List.of());

        assertFalse(NavigationDragDrop.moveDeckToStandalone(standaloneDeck, decksAndCollections));
        assertEquals(List.of(standaloneDeck), decksAndCollections.getDecks());
    }

    // ── moveDeckToNewUnit ────────────────────────────────────────────────────

    @Test
    void moveDeckToNewUnitPrependsWhenAfterUnitIndexIsNegative() {
        Deck existingDeck = deck("Existing");
        Deck movedDeck = deck("Moved");
        ThemeCollection targetCollection = themeCollection("Target");
        targetCollection.getLinkedDecks().add(unitOf(existingDeck));
        DecksAndCollectionsList decksAndCollections =
                dacOf(List.of(movedDeck), List.of(targetCollection));

        NavigationDragDrop.moveDeckToNewUnit(movedDeck, targetCollection, -1, decksAndCollections);

        assertEquals(List.of(movedDeck), targetCollection.getLinkedDecks().get(0));
        assertEquals(List.of(existingDeck), targetCollection.getLinkedDecks().get(1));
    }

    @Test
    void moveDeckToNewUnitAppendsWhenAfterUnitIndexIsBeyondTheEnd() {
        Deck existingDeck = deck("Existing");
        Deck movedDeck = deck("Moved");
        ThemeCollection targetCollection = themeCollection("Target");
        targetCollection.getLinkedDecks().add(unitOf(existingDeck));
        DecksAndCollectionsList decksAndCollections =
                dacOf(List.of(movedDeck), List.of(targetCollection));

        NavigationDragDrop.moveDeckToNewUnit(movedDeck, targetCollection, 5, decksAndCollections);

        assertEquals(List.of(existingDeck), targetCollection.getLinkedDecks().get(0));
        assertEquals(List.of(movedDeck), targetCollection.getLinkedDecks().get(1));
    }

    @Test
    void moveDeckToNewUnitInsertsBetweenExistingUnits() {
        Deck firstDeck = deck("First");
        Deck secondDeck = deck("Second");
        Deck movedDeck = deck("Moved");
        ThemeCollection targetCollection = themeCollection("Target");
        targetCollection.getLinkedDecks().add(unitOf(firstDeck));
        targetCollection.getLinkedDecks().add(unitOf(secondDeck));
        DecksAndCollectionsList decksAndCollections =
                dacOf(List.of(movedDeck), List.of(targetCollection));

        NavigationDragDrop.moveDeckToNewUnit(movedDeck, targetCollection, 0, decksAndCollections);

        assertEquals(List.of(firstDeck), targetCollection.getLinkedDecks().get(0));
        assertEquals(List.of(movedDeck), targetCollection.getLinkedDecks().get(1));
        assertEquals(List.of(secondDeck), targetCollection.getLinkedDecks().get(2));
    }

    // ── DeckLocation ─────────────────────────────────────────────────────────

    @Test
    void deckLocationGetUnitReturnsNullWhenIndexOutOfRange() {
        ThemeCollection collection = themeCollection("Collection");
        NavigationDragDrop.DeckLocation location =
                new NavigationDragDrop.DeckLocation(collection, 3);

        assertNull(location.getUnit());
    }

    @Test
    void deckLocationIsStandaloneWhenCollectionIsNull() {
        NavigationDragDrop.DeckLocation location = new NavigationDragDrop.DeckLocation(null, -1);

        assertTrue(location.isStandalone());
        assertNull(location.getUnit());
    }
}