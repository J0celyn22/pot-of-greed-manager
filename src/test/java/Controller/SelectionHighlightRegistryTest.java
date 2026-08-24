package Controller;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import javafx.beans.property.BooleanProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Step 1 of the reactive-selection-highlight plan (see
 * {@code gridview-reactive-selection-plan.md}): that every {@link SelectionManager}
 * MIDDLE-pane mutation path diffs the old and new selected sets and flips exactly the
 * {@link SelectionHighlightRegistry} properties that actually changed, in addition to the
 * existing broadcast-listener notification.
 * <p>
 * These tests only cover the registry-diffing behavior added in this step — the pre-existing
 * {@code selectedMiddleElements} / {@code activePart} semantics are unchanged and already
 * covered elsewhere.
 * </p>
 */
class SelectionHighlightRegistryTest {

    private static CardElement freshElement() {
        return new CardElement(new Card());
    }

    private static boolean isHighlighted(CardElement element) {
        return SelectionHighlightRegistry.getOrCreateSelectedProperty(element).get();
    }

    @AfterEach
    void resetSelectionState() {
        SelectionManager.clearSelection();
    }

    @Test
    void selectingOneElement_marksOnlyThatElementSelected() {
        CardElement element = freshElement();

        SelectionManager.selectElement(element);

        assertTrue(isHighlighted(element));
    }

    @Test
    void selectingADifferentElement_flipsOldFalseAndNewTrue() {
        CardElement first = freshElement();
        CardElement second = freshElement();
        SelectionManager.selectElement(first);

        SelectionManager.selectElement(second);

        assertFalse(isHighlighted(first));
        assertTrue(isHighlighted(second));
    }

    @Test
    void selectingTwoThenDeselectingOne_onlyThatOneFlips() {
        CardElement first = freshElement();
        CardElement second = freshElement();
        SelectionManager.toggleElementSelection(first);
        SelectionManager.toggleElementSelection(second);

        SelectionManager.toggleElementSelection(first);

        assertFalse(isHighlighted(first));
        assertTrue(isHighlighted(second));
    }

    @Test
    void clearSelection_flipsAllPreviouslySelectedElementsFalse() {
        CardElement first = freshElement();
        CardElement second = freshElement();
        SelectionManager.toggleElementSelection(first);
        SelectionManager.toggleElementSelection(second);

        SelectionManager.clearSelection();

        assertFalse(isHighlighted(first));
        assertFalse(isHighlighted(second));
    }

    @Test
    void rangeSelectElements_marksEveryElementInRangeSelected() {
        CardElement first = freshElement();
        CardElement second = freshElement();
        CardElement third = freshElement();
        List<CardElement> orderedElements = List.of(first, second, third);

        SelectionManager.rangeSelectElements(first, orderedElements);
        SelectionManager.rangeSelectElements(third, orderedElements);

        assertTrue(isHighlighted(first));
        assertTrue(isHighlighted(second));
        assertTrue(isHighlighted(third));
    }

    @Test
    void switchingToRightPaneSelection_flipsThePreviousMiddleSelectionFalse() {
        Card card = new Card();
        CardElement element = new CardElement(card);
        SelectionManager.selectElement(element);

        SelectionManager.selectCard(card, "RIGHT");

        assertFalse(isHighlighted(element));
    }

    @Test
    void aNeverSelectedElement_defaultsToNotHighlighted() {
        CardElement selected = freshElement();
        CardElement neverSelected = freshElement();

        SelectionManager.selectElement(selected);

        assertFalse(isHighlighted(neverSelected));
    }

    @Test
    void getOrCreateSelectedProperty_returnsTheSameInstanceOnRepeatedCalls() {
        CardElement element = freshElement();

        BooleanProperty firstLookup = SelectionHighlightRegistry.getOrCreateSelectedProperty(element);
        BooleanProperty secondLookup = SelectionHighlightRegistry.getOrCreateSelectedProperty(element);

        assertTrue(firstLookup == secondLookup);
    }
}