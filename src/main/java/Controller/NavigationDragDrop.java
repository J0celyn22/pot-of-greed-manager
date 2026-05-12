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
     */
    public static List<Box> findBoxParentList(Box box, OwnedCardsCollection collection) {
        List<Box> top = collection.getOwnedCollection();
        if (top == null) return null;
        if (top.contains(box)) return top;
        for (Box b : top) {
            List<Box> subs = b.getSubBoxes();
            if (subs != null && subs.contains(box)) return subs;
        }
        return null;
    }

    /**
     * Moves {@code dragged} to just BEFORE {@code target} in target's parent list.
     */
    public static boolean moveBoxBefore(Box dragged, Box target, OwnedCardsCollection collection) {
        if (dragged == target) return false;
        List<Box> targetParent = findBoxParentList(target, collection);
        if (targetParent == null) return false;
        List<Box> draggedParent = findBoxParentList(dragged, collection);
        if (draggedParent != null) draggedParent.remove(dragged);
        int idx = targetParent.indexOf(target);
        targetParent.add(Math.max(0, idx), dragged);
        return true;
    }

    /**
     * Moves {@code dragged} to just AFTER {@code target} in target's parent list.
     */
    public static boolean moveBoxAfter(Box dragged, Box target, OwnedCardsCollection collection) {
        if (dragged == target) return false;
        List<Box> targetParent = findBoxParentList(target, collection);
        if (targetParent == null) return false;
        List<Box> draggedParent = findBoxParentList(dragged, collection);
        if (draggedParent != null) draggedParent.remove(dragged);
        // Re-find target index after potential removal from the same list
        int idx = targetParent.indexOf(target);
        targetParent.add(idx + 1, dragged);
        return true;
    }

    /**
     * Makes {@code dragged} a sub-box of {@code target} (appended at the end of
     * {@code target}'s sub-box list).  Prevents nesting a box into its own descendant.
     */
    public static boolean nestBoxInto(Box dragged, Box target, OwnedCardsCollection collection) {
        if (dragged == target) return false;
        // Guard: do not allow nesting into a descendant of dragged
        if (isDescendant(target, dragged)) return false;
        List<Box> draggedParent = findBoxParentList(dragged, collection);
        if (draggedParent != null) draggedParent.remove(dragged);
        if (target.getSubBoxes() == null) target.setSubBoxes(new ArrayList<>());
        target.getSubBoxes().add(dragged);
        return true;
    }

    /**
     * Moves {@code dragged} to the top-level list of the collection.
     * If it is already at the top level, returns {@code false}.
     */
    public static boolean moveBoxToTopLevel(Box dragged, OwnedCardsCollection collection) {
        List<Box> top = collection.getOwnedCollection();
        if (top == null) return false;
        if (top.contains(dragged)) return false;
        List<Box> parent = findBoxParentList(dragged, collection);
        if (parent != null) parent.remove(dragged);
        top.add(dragged);
        return true;
    }

    /**
     * Returns true if {@code candidate} is a (recursive) sub-box of {@code ancestor}.
     */
    private static boolean isDescendant(Box candidate, Box ancestor) {
        if (ancestor.getSubBoxes() == null) return false;
        for (Box sub : ancestor.getSubBoxes()) {
            if (sub == candidate || isDescendant(candidate, sub)) return true;
        }
        return false;
    }

    // =========================================================================
    // My Collection – CardsGroup (category) reordering
    // =========================================================================

    /**
     * Returns the Box that directly contains {@code group} in its content list,
     * searching top-level boxes and their sub-boxes.
     */
    public static Box findCategoryParent(CardsGroup group, OwnedCardsCollection collection) {
        List<Box> top = collection.getOwnedCollection();
        if (top == null) return null;
        for (Box b : top) {
            if (b.getContent() != null && b.getContent().contains(group)) return b;
            if (b.getSubBoxes() != null) {
                for (Box sub : b.getSubBoxes()) {
                    if (sub.getContent() != null && sub.getContent().contains(group)) return sub;
                }
            }
        }
        return null;
    }

    /**
     * Moves {@code dragged} category BEFORE {@code target} (possibly into a different box).
     */
    public static boolean moveCategoryBefore(CardsGroup dragged, CardsGroup target,
                                             OwnedCardsCollection collection) {
        if (dragged == target) return false;
        Box targetParent = findCategoryParent(target, collection);
        if (targetParent == null) return false;
        Box draggedParent = findCategoryParent(dragged, collection);
        if (draggedParent != null) draggedParent.getContent().remove(dragged);
        int idx = targetParent.getContent().indexOf(target);
        targetParent.getContent().add(Math.max(0, idx), dragged);
        return true;
    }

    /**
     * Moves {@code dragged} category AFTER {@code target}.
     */
    public static boolean moveCategoryAfter(CardsGroup dragged, CardsGroup target,
                                            OwnedCardsCollection collection) {
        if (dragged == target) return false;
        Box targetParent = findCategoryParent(target, collection);
        if (targetParent == null) return false;
        Box draggedParent = findCategoryParent(dragged, collection);
        if (draggedParent != null) draggedParent.getContent().remove(dragged);
        int idx = targetParent.getContent().indexOf(target);
        targetParent.getContent().add(idx + 1, dragged);
        return true;
    }

    /**
     * Moves {@code dragged} category into {@code target} box (appended at the end).
     */
    public static boolean moveCategoryIntoBox(CardsGroup dragged, Box target,
                                              OwnedCardsCollection collection) {
        Box draggedParent = findCategoryParent(dragged, collection);
        if (draggedParent == target) return false;
        if (draggedParent != null) draggedParent.getContent().remove(dragged);
        if (target.getContent() == null) target.setContent(new ArrayList<>());
        target.getContent().add(dragged);
        return true;
    }

    // =========================================================================
    // Decks & Collections – Deck reordering / moving
    // =========================================================================

    /**
     * Locates {@code deck} in the model and returns its position. Never returns null.
     */
    public static DeckLocation findDeckLocation(Deck deck, DecksAndCollectionsList dcl) {
        if (dcl.getCollections() != null) {
            for (ThemeCollection coll : dcl.getCollections()) {
                if (coll.getLinkedDecks() == null) continue;
                for (int u = 0; u < coll.getLinkedDecks().size(); u++) {
                    List<Deck> unit = coll.getLinkedDecks().get(u);
                    if (unit != null && unit.contains(deck)) return new DeckLocation(coll, u);
                }
            }
        }
        return new DeckLocation(null, -1);
    }

    /**
     * Removes {@code deck} from wherever it currently lives in the model.
     * Empty units are pruned after removal.
     */
    public static void removeDeckFromModel(Deck deck, DecksAndCollectionsList dcl) {
        DeckLocation loc = findDeckLocation(deck, dcl);
        if (!loc.isStandalone()) {
            List<Deck> unit = loc.getUnit();
            if (unit != null) {
                unit.remove(deck);
                if (unit.isEmpty()) loc.collection.getLinkedDecks().remove(unit);
            }
        } else {
            if (dcl.getDecks() != null) dcl.getDecks().remove(deck);
        }
    }

    /**
     * Inserts {@code dragged} BEFORE {@code target} deck.
     * If they are in different collections, the dragged deck moves to the target's unit.
     */
    public static boolean moveDeckBefore(Deck dragged, Deck target, DecksAndCollectionsList dcl) {
        if (dragged == target) return false;
        // Snapshot target location before any removal (removal can shift indices)
        DeckLocation targetLoc = findDeckLocation(target, dcl);
        removeDeckFromModel(dragged, dcl);
        // Re-locate target after removal
        DeckLocation newTargetLoc = findDeckLocation(target, dcl);
        if (newTargetLoc.isStandalone()) {
            List<Deck> standalone = dcl.getDecks();
            if (standalone == null) {
                dcl.setDecks(new ArrayList<>());
                standalone = dcl.getDecks();
            }
            int idx = standalone.indexOf(target);
            standalone.add(Math.max(0, idx), dragged);
        } else {
            List<Deck> unit = newTargetLoc.getUnit();
            if (unit == null) return false;
            int idx = unit.indexOf(target);
            unit.add(Math.max(0, idx), dragged);
        }
        return true;
    }

    /**
     * Inserts {@code dragged} AFTER {@code target} deck.
     */
    public static boolean moveDeckAfter(Deck dragged, Deck target, DecksAndCollectionsList dcl) {
        if (dragged == target) return false;
        removeDeckFromModel(dragged, dcl);
        DeckLocation newTargetLoc = findDeckLocation(target, dcl);
        if (newTargetLoc.isStandalone()) {
            List<Deck> standalone = dcl.getDecks();
            if (standalone == null) {
                dcl.setDecks(new ArrayList<>());
                standalone = dcl.getDecks();
            }
            int idx = standalone.indexOf(target);
            standalone.add(idx + 1, dragged);
        } else {
            List<Deck> unit = newTargetLoc.getUnit();
            if (unit == null) return false;
            int idx = unit.indexOf(target);
            unit.add(idx + 1, dragged);
        }
        return true;
    }

    /**
     * Moves {@code dragged} into {@code targetCollection} as a new singleton unit
     * appended at the end of the collection's deck lists.
     */
    public static boolean moveDeckToCollection(Deck dragged, ThemeCollection targetCollection,
                                               DecksAndCollectionsList dcl) {
        removeDeckFromModel(dragged, dcl);
        if (targetCollection.getLinkedDecks() == null) targetCollection.setLinkedDecks(new ArrayList<>());
        List<Deck> newUnit = new ArrayList<>();
        newUnit.add(dragged);
        targetCollection.getLinkedDecks().add(newUnit);
        return true;
    }

    /**
     * Moves {@code dragged} to the standalone deck list of {@code dcl}.
     * Returns false if the deck was already standalone.
     */
    public static boolean moveDeckToStandalone(Deck dragged, DecksAndCollectionsList dcl) {
        if (findDeckLocation(dragged, dcl).isStandalone()) return false;
        removeDeckFromModel(dragged, dcl);
        if (dcl.getDecks() == null) dcl.setDecks(new ArrayList<>());
        dcl.getDecks().add(dragged);
        return true;
    }

    /**
     * Moves {@code dragged} to a brand-new unit inside {@code targetCollection},
     * inserted at position {@code afterUnitIndex + 1} (i.e. right after the separator
     * at index {@code afterUnitIndex}).
     *
     * @param afterUnitIndex the 0-based index of the unit <em>before</em> the separator
     *                       where the drop occurred.  Pass {@code -1} to prepend
     *                       before all existing units.
     */
    public static boolean moveDeckToNewUnit(Deck dragged, ThemeCollection targetCollection,
                                            int afterUnitIndex, DecksAndCollectionsList dcl) {
        removeDeckFromModel(dragged, dcl);
        if (targetCollection.getLinkedDecks() == null) targetCollection.setLinkedDecks(new ArrayList<>());
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

    /**
     * Where a Deck currently lives in the model.
     */
    public static final class DeckLocation {
        /**
         * The collection the deck belongs to, or {@code null} if standalone.
         */
        public final ThemeCollection collection;
        /**
         * The index of the unit (inner list) within the collection, or {@code -1} if standalone.
         */
        public final int unitIndex;

        public DeckLocation(ThemeCollection c, int u) {
            this.collection = c;
            this.unitIndex = u;
        }

        public boolean isStandalone() {
            return collection == null;
        }

        /**
         * Convenience: returns the actual unit list, or {@code null} if standalone.
         */
        public List<Deck> getUnit() {
            if (isStandalone()) return null;
            List<List<Deck>> all = collection.getLinkedDecks();
            if (all == null || unitIndex < 0 || unitIndex >= all.size()) return null;
            return all.get(unitIndex);
        }
    }
}