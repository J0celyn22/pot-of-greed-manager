package Controller;

import Model.CardsLists.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure model-mutation helpers for navigation-menu drag-and-drop reordering.
 *
 * <p>Nothing here touches the JavaFX view; callers are responsible for
 * triggering a model refresh (e.g. {@code UserInterfaceFunctions.refreshOwnedCollectionStructure()}
 * or {@code UserInterfaceFunctions.refreshDecksAndCollectionsView()}) after a
 * successful mutation.
 *
 * <p>All public methods return {@code true} when the model was actually changed,
 * {@code false} when the operation is a no-op (dragging an item onto itself, etc.).
 */
public final class NavigationDragDrop {

    private NavigationDragDrop() {
    }

    // =========================================================================
    // My Collection – Box reordering / nesting
    // =========================================================================

    /**
     * Returns the {@code List<Box>} that directly contains {@code box}, searching
     * both the top-level list and every box's sub-box list.
     * Returns {@code null} if the box is not found.
     *
     * @param box        the box whose parent list is to be found
     * @param collection the owned-cards collection to search within
     * @return the list containing {@code box}, or {@code null} if not found
     */
    public static List<Box> findBoxParentList(Box box, OwnedCardsCollection collection) {
        List<Box> top = collection.getOwnedCollection();
        if (top == null) {
            return null;
        }
        if (top.contains(box)) {
            return top;
        }
        for (Box topLevelBox : top) {
            List<Box> subBoxes = topLevelBox.getSubBoxes();
            if (subBoxes != null && subBoxes.contains(box)) {
                return subBoxes;
            }
        }
        return null;
    }

    /**
     * Moves {@code dragged} to just BEFORE {@code target} in target's parent list.
     *
     * @param dragged    the box being dragged
     * @param target     the box before which {@code dragged} will be inserted
     * @param collection the owned-cards collection containing both boxes
     * @return {@code true} if the model was changed, {@code false} if no-op
     */
    public static boolean moveBoxBefore(Box dragged, Box target, OwnedCardsCollection collection) {
        if (dragged == target) {
            return false;
        }
        List<Box> targetParent = findBoxParentList(target, collection);
        if (targetParent == null) {
            return false;
        }
        List<Box> draggedParent = findBoxParentList(dragged, collection);
        if (draggedParent != null) {
            draggedParent.remove(dragged);
        }
        int insertionIndex = targetParent.indexOf(target);
        targetParent.add(Math.max(0, insertionIndex), dragged);
        return true;
    }

    /**
     * Moves {@code dragged} to just AFTER {@code target} in target's parent list.
     *
     * @param dragged    the box being dragged
     * @param target     the box after which {@code dragged} will be inserted
     * @param collection the owned-cards collection containing both boxes
     * @return {@code true} if the model was changed, {@code false} if no-op
     */
    public static boolean moveBoxAfter(Box dragged, Box target, OwnedCardsCollection collection) {
        if (dragged == target) {
            return false;
        }
        List<Box> targetParent = findBoxParentList(target, collection);
        if (targetParent == null) {
            return false;
        }
        List<Box> draggedParent = findBoxParentList(dragged, collection);
        if (draggedParent != null) {
            draggedParent.remove(dragged);
        }
        // Re-find target index after potential removal from the same list
        int insertionIndex = targetParent.indexOf(target);
        targetParent.add(insertionIndex + 1, dragged);
        return true;
    }

    /**
     * Makes {@code dragged} a sub-box of {@code target} (appended at the end of
     * {@code target}'s sub-box list). Prevents nesting a box into its own descendant.
     *
     * @param dragged    the box being dragged
     * @param target     the box that will become the new parent
     * @param collection the owned-cards collection containing both boxes
     * @return {@code true} if the model was changed, {@code false} if no-op or
     *         if the operation would create a circular nesting
     */
    public static boolean nestBoxInto(Box dragged, Box target, OwnedCardsCollection collection) {
        if (dragged == target) {
            return false;
        }
        // Guard: do not allow nesting into a descendant of dragged
        if (isDescendant(target, dragged)) {
            return false;
        }
        List<Box> draggedParent = findBoxParentList(dragged, collection);
        if (draggedParent != null) {
            draggedParent.remove(dragged);
        }
        if (target.getSubBoxes() == null) {
            target.setSubBoxes(new ArrayList<>());
        }
        target.getSubBoxes().add(dragged);
        return true;
    }

    /**
     * Moves {@code dragged} to the top-level list of the collection.
     * If it is already at the top level, returns {@code false}.
     *
     * @param dragged    the box to promote to top-level
     * @param collection the owned-cards collection to modify
     * @return {@code true} if the model was changed, {@code false} if the box
     *         was already at the top level
     */
    public static boolean moveBoxToTopLevel(Box dragged, OwnedCardsCollection collection) {
        List<Box> top = collection.getOwnedCollection();
        if (top == null) {
            return false;
        }
        if (top.contains(dragged)) {
            return false;
        }
        List<Box> currentParent = findBoxParentList(dragged, collection);
        if (currentParent != null) {
            currentParent.remove(dragged);
        }
        top.add(dragged);
        return true;
    }

    /**
     * Returns {@code true} if {@code candidate} is a (recursive) sub-box of {@code ancestor}.
     *
     * @param candidate the box to test
     * @param ancestor  the box whose subtree is searched
     * @return {@code true} if {@code candidate} appears anywhere in the subtree of {@code ancestor}
     */
    private static boolean isDescendant(Box candidate, Box ancestor) {
        if (ancestor.getSubBoxes() == null) {
            return false;
        }
        for (Box subBox : ancestor.getSubBoxes()) {
            if (subBox == candidate || isDescendant(candidate, subBox)) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // My Collection – CardsGroup (category) reordering
    // =========================================================================

    /**
     * Returns the Box that directly contains {@code group} in its content list,
     * searching top-level boxes and their sub-boxes.
     *
     * @param group      the category whose parent box is to be found
     * @param collection the owned-cards collection to search within
     * @return the {@link Box} containing {@code group}, or {@code null} if not found
     */
    public static Box findCategoryParent(CardsGroup group, OwnedCardsCollection collection) {
        List<Box> top = collection.getOwnedCollection();
        if (top == null) {
            return null;
        }
        for (Box box : top) {
            if (box.getContent() != null && box.getContent().contains(group)) {
                return box;
            }
            if (box.getSubBoxes() != null) {
                for (Box subBox : box.getSubBoxes()) {
                    if (subBox.getContent() != null && subBox.getContent().contains(group)) {
                        return subBox;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Moves {@code dragged} category BEFORE {@code target} (possibly into a different box).
     *
     * @param dragged    the category being dragged
     * @param target     the category before which {@code dragged} will be inserted
     * @param collection the owned-cards collection containing both categories
     * @return {@code true} if the model was changed, {@code false} if no-op
     */
    public static boolean moveCategoryBefore(CardsGroup dragged, CardsGroup target,
                                             OwnedCardsCollection collection) {
        if (dragged == target) {
            return false;
        }
        Box targetParent = findCategoryParent(target, collection);
        if (targetParent == null) {
            return false;
        }
        Box draggedParent = findCategoryParent(dragged, collection);
        if (draggedParent != null) {
            draggedParent.getContent().remove(dragged);
        }
        int insertionIndex = targetParent.getContent().indexOf(target);
        targetParent.getContent().add(Math.max(0, insertionIndex), dragged);
        return true;
    }

    /**
     * Moves {@code dragged} category AFTER {@code target}.
     *
     * @param dragged    the category being dragged
     * @param target     the category after which {@code dragged} will be inserted
     * @param collection the owned-cards collection containing both categories
     * @return {@code true} if the model was changed, {@code false} if no-op
     */
    public static boolean moveCategoryAfter(CardsGroup dragged, CardsGroup target,
                                            OwnedCardsCollection collection) {
        if (dragged == target) {
            return false;
        }
        Box targetParent = findCategoryParent(target, collection);
        if (targetParent == null) {
            return false;
        }
        Box draggedParent = findCategoryParent(dragged, collection);
        if (draggedParent != null) {
            draggedParent.getContent().remove(dragged);
        }
        int insertionIndex = targetParent.getContent().indexOf(target);
        targetParent.getContent().add(insertionIndex + 1, dragged);
        return true;
    }

    /**
     * Moves {@code dragged} category into {@code target} box (appended at the end).
     *
     * @param dragged    the category to move
     * @param target     the box that will receive the category
     * @param collection the owned-cards collection containing both
     * @return {@code true} if the model was changed, {@code false} if the category
     *         was already directly inside {@code target}
     */
    public static boolean moveCategoryIntoBox(CardsGroup dragged, Box target,
                                              OwnedCardsCollection collection) {
        Box draggedParent = findCategoryParent(dragged, collection);
        if (draggedParent == target) {
            return false;
        }
        if (draggedParent != null) {
            draggedParent.getContent().remove(dragged);
        }
        if (target.getContent() == null) {
            target.setContent(new ArrayList<>());
        }
        target.getContent().add(dragged);
        return true;
    }

    // =========================================================================
    // Decks & Collections – Deck reordering / moving
    // =========================================================================

    /**
     * Locates {@code deck} in the model and returns its position. Never returns null;
     * returns a standalone {@link DeckLocation} when the deck cannot be found inside
     * any collection.
     *
     * @param deck the deck to locate
     * @param dcl  the decks-and-collections model to search
     * @return a {@link DeckLocation} describing where the deck lives
     */
    public static DeckLocation findDeckLocation(Deck deck, DecksAndCollectionsList dcl) {
        if (dcl.getCollections() != null) {
            for (ThemeCollection collection : dcl.getCollections()) {
                if (collection.getLinkedDecks() == null) {
                    continue;
                }
                for (int unitIndex = 0; unitIndex < collection.getLinkedDecks().size(); unitIndex++) {
                    List<Deck> unit = collection.getLinkedDecks().get(unitIndex);
                    if (unit != null && unit.contains(deck)) {
                        return new DeckLocation(collection, unitIndex);
                    }
                }
            }
        }
        return new DeckLocation(null, -1);
    }

    /**
     * Removes {@code deck} from wherever it currently lives in the model.
     * Empty units are pruned after removal.
     *
     * @param deck the deck to remove
     * @param dcl  the decks-and-collections model to modify
     */
    public static void removeDeckFromModel(Deck deck, DecksAndCollectionsList dcl) {
        DeckLocation location = findDeckLocation(deck, dcl);
        if (!location.isStandalone()) {
            List<Deck> unit = location.getUnit();
            if (unit != null) {
                unit.remove(deck);
                if (unit.isEmpty()) {
                    location.collection.getLinkedDecks().remove(unit);
                }
            }
        } else {
            if (dcl.getDecks() != null) {
                dcl.getDecks().remove(deck);
            }
        }
    }

    /**
     * Inserts {@code dragged} BEFORE {@code target} deck.
     * If they are in different collections, the dragged deck moves to the target's unit.
     *
     * @param dragged the deck being dragged
     * @param target  the deck before which {@code dragged} will be inserted
     * @param dcl     the decks-and-collections model to modify
     * @return {@code true} if the model was changed, {@code false} if no-op
     */
    public static boolean moveDeckBefore(Deck dragged, Deck target, DecksAndCollectionsList dcl) {
        if (dragged == target) {
            return false;
        }
        // Remove dragged first; then re-locate target (removal may shift indices in the same unit)
        removeDeckFromModel(dragged, dcl);
        DeckLocation newTargetLocation = findDeckLocation(target, dcl);
        if (newTargetLocation.isStandalone()) {
            List<Deck> standalone = dcl.getDecks();
            if (standalone == null) {
                dcl.setDecks(new ArrayList<>());
                standalone = dcl.getDecks();
            }
            int insertionIndex = standalone.indexOf(target);
            standalone.add(Math.max(0, insertionIndex), dragged);
        } else {
            List<Deck> unit = newTargetLocation.getUnit();
            if (unit == null) {
                return false;
            }
            int insertionIndex = unit.indexOf(target);
            unit.add(Math.max(0, insertionIndex), dragged);
        }
        return true;
    }

    /**
     * Inserts {@code dragged} AFTER {@code target} deck.
     *
     * @param dragged the deck being dragged
     * @param target  the deck after which {@code dragged} will be inserted
     * @param dcl     the decks-and-collections model to modify
     * @return {@code true} if the model was changed, {@code false} if no-op
     */
    public static boolean moveDeckAfter(Deck dragged, Deck target, DecksAndCollectionsList dcl) {
        if (dragged == target) {
            return false;
        }
        removeDeckFromModel(dragged, dcl);
        DeckLocation newTargetLocation = findDeckLocation(target, dcl);
        if (newTargetLocation.isStandalone()) {
            List<Deck> standalone = dcl.getDecks();
            if (standalone == null) {
                dcl.setDecks(new ArrayList<>());
                standalone = dcl.getDecks();
            }
            int insertionIndex = standalone.indexOf(target);
            standalone.add(insertionIndex + 1, dragged);
        } else {
            List<Deck> unit = newTargetLocation.getUnit();
            if (unit == null) {
                return false;
            }
            int insertionIndex = unit.indexOf(target);
            unit.add(insertionIndex + 1, dragged);
        }
        return true;
    }

    /**
     * Moves {@code dragged} into {@code targetCollection} as a new singleton unit
     * appended at the end of the collection's deck lists.
     *
     * @param dragged          the deck to move
     * @param targetCollection the collection that will receive the deck as a new unit
     * @param dcl              the decks-and-collections model to modify
     * @return always {@code true} (the operation always changes the model)
     */
    public static boolean moveDeckToCollection(Deck dragged, ThemeCollection targetCollection,
                                               DecksAndCollectionsList dcl) {
        removeDeckFromModel(dragged, dcl);
        if (targetCollection.getLinkedDecks() == null) {
            targetCollection.setLinkedDecks(new ArrayList<>());
        }
        List<Deck> newUnit = new ArrayList<>();
        newUnit.add(dragged);
        targetCollection.getLinkedDecks().add(newUnit);
        return true;
    }

    /**
     * Moves {@code dragged} to the standalone deck list of {@code dcl}.
     * Returns {@code false} if the deck was already standalone.
     *
     * @param dragged the deck to move to the standalone list
     * @param dcl     the decks-and-collections model to modify
     * @return {@code true} if the model was changed, {@code false} if the deck
     *         was already standalone
     */
    public static boolean moveDeckToStandalone(Deck dragged, DecksAndCollectionsList dcl) {
        if (findDeckLocation(dragged, dcl).isStandalone()) {
            return false;
        }
        removeDeckFromModel(dragged, dcl);
        if (dcl.getDecks() == null) {
            dcl.setDecks(new ArrayList<>());
        }
        dcl.getDecks().add(dragged);
        return true;
    }

    /**
     * Moves {@code dragged} to a brand-new unit inside {@code targetCollection},
     * inserted at position {@code afterUnitIndex + 1} (i.e. right after the separator
     * at index {@code afterUnitIndex}).
     *
     * @param dragged          the deck to move
     * @param targetCollection the collection that will receive the new unit
     * @param afterUnitIndex   the 0-based index of the unit <em>before</em> the separator
     *                         where the drop occurred. Pass {@code -1} to prepend
     *                         before all existing units.
     * @param dcl              the decks-and-collections model to modify
     * @return always {@code true} (the operation always changes the model)
     */
    public static boolean moveDeckToNewUnit(Deck dragged, ThemeCollection targetCollection,
                                            int afterUnitIndex, DecksAndCollectionsList dcl) {
        removeDeckFromModel(dragged, dcl);
        if (targetCollection.getLinkedDecks() == null) {
            targetCollection.setLinkedDecks(new ArrayList<>());
        }
        List<Deck> newUnit = new ArrayList<>();
        newUnit.add(dragged);
        int insertAt = afterUnitIndex + 1;
        List<List<Deck>> units = targetCollection.getLinkedDecks();
        if (insertAt <= 0) {
            units.add(0, newUnit);
        } else if (insertAt >= units.size()) {
            units.add(newUnit);
        } else {
            units.add(insertAt, newUnit);
        }
        return true;
    }

    // =========================================================================
    // Inner types
    // =========================================================================

    /**
     * Describes where a Deck currently lives in the model.
     */
    public static final class DeckLocation {

        /**
         * The collection the deck belongs to, or {@code null} if standalone.
         */
        public final ThemeCollection collection;

        /**
         * The index of the unit (inner list) within the collection,
         * or {@code -1} if standalone.
         */
        public final int unitIndex;

        /**
         * @param collection the collection containing the deck, or {@code null} for standalone
         * @param unitIndex  the index of the unit within the collection, or {@code -1} for standalone
         */
        public DeckLocation(ThemeCollection collection, int unitIndex) {
            this.collection = collection;
            this.unitIndex = unitIndex;
        }

        /**
         * Returns {@code true} if the deck is not inside any collection.
         *
         * @return {@code true} when {@link #collection} is {@code null}
         */
        public boolean isStandalone() {
            return collection == null;
        }

        /**
         * Convenience: returns the actual unit list, or {@code null} if standalone
         * or if the unit index is out of range.
         *
         * @return the {@code List<Deck>} at {@link #unitIndex} within the collection,
         *         or {@code null} if not applicable
         */
        public List<Deck> getUnit() {
            if (isStandalone()) {
                return null;
            }
            List<List<Deck>> allUnits = collection.getLinkedDecks();
            if (allUnits == null || unitIndex < 0 || unitIndex >= allUnits.size()) {
                return null;
            }
            return allUnits.get(unitIndex);
        }
    }
}