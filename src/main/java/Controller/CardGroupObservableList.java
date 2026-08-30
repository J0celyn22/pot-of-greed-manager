package Controller;

import Model.CardsLists.CardElement;
import Model.CardsLists.CardsGroup;
import javafx.collections.ModifiableObservableListBase;

import java.util.List;
import java.util.function.Supplier;

/**
 * {@link javafx.collections.ObservableList} wrapper around a {@link CardsGroup}'s backing card
 * list, returned by {@link CardGroupRegistry#observableListFor}. Behaves exactly like a plain
 * {@code FXCollections.observableList(backing)} wrapper — every structural mutation still
 * updates {@code backing} directly and fires the usual ObservableList change notifications — but
 * additionally detaches the group's live {@link org.controlsfx.control.GridView} (if any) before
 * each mutation and reattaches it afterward, once the mutation has fully settled.
 *
 * <p><b>Why this exists.</b> ControlsFX 11.2.1's {@code GridCell} reacts to its own index changes
 * with an unbounded {@code gridView.getItems().get(getIndex())} call — no check that
 * {@code getIndex()} is still within range of the (possibly just-shrunk) items list. When a
 * group's card count shrinks — a card removed, moved out, or filtered out — while its GridView is
 * on screen, {@code GridRowSkin} does not reconcile cell indices against the new, smaller size
 * before that listener fires, so a cell holding a stale index throws
 * {@link IndexOutOfBoundsException} on the JavaFX Application Thread. That exception aborts
 * ControlsFX's cell-recycling pass partway through, so most cards in the group appear to vanish
 * even though {@code backing} — and therefore the saved model — is completely unaffected. This
 * is a known, still-open upstream defect: see controlsfx issues #1065 and #1070.
 *
 * <p>Detaching the GridView (swapping in a fresh empty list) before the mutation, then
 * reattaching the real, already-updated list afterward, avoids the defect entirely: ControlsFX
 * never sees the mutation as a live incremental update, only as one clean {@code setItems()} call
 * against a list that has already reached its final size, which it lays out from scratch every
 * time. See {@link CardGroupRegistry#detachGridViewForGroup} for the mechanics, including why an
 * empty list is used as the placeholder instead of {@code null}.
 *
 * <p>Every add/remove/move call site in the codebase already goes through
 * {@link CardGroupRegistry#observableListFor}, so routing that method through this class protects
 * all of them without any call site needing to change.
 */
final class CardGroupObservableList extends ModifiableObservableListBase<CardElement> {

    private final List<CardElement> backingList;
    private final CardsGroup group;

    CardGroupObservableList(List<CardElement> backingList, CardsGroup group) {
        this.backingList = backingList;
        this.group = group;
    }

    @Override
    public CardElement get(int index) {
        return backingList.get(index);
    }

    @Override
    public int size() {
        return backingList.size();
    }

    @Override
    protected void doAdd(int index, CardElement element) {
        runWithGridViewDetached(() -> {
            backingList.add(index, element);
            return null;
        });
    }

    @Override
    protected CardElement doSet(int index, CardElement element) {
        return runWithGridViewDetached(() -> backingList.set(index, element));
    }

    @Override
    protected CardElement doRemove(int index) {
        return runWithGridViewDetached(() -> backingList.remove(index));
    }

    /**
     * Detaches the group's live GridView (if any), runs {@code mutation}, then schedules
     * reattachment for once the mutation's change notification has fully propagated. See
     * {@link CardGroupRegistry#detachGridViewForGroup} / {@code reattachGridViewForGroup} for
     * why the reattachment must be deferred rather than done immediately here: this method
     * returns before the {@link ModifiableObservableListBase} machinery that wraps it has
     * notified listeners (such as this group's {@link javafx.collections.transformation.FilteredList})
     * of the change, so reattaching synchronously would race the exact same way an unprotected
     * mutation does.
     */
    private <ResultType> ResultType runWithGridViewDetached(Supplier<ResultType> mutation) {
        CardGroupRegistry.GridViewDetachment detachment = CardGroupRegistry.detachGridViewForGroup(group);
        try {
            return mutation.get();
        } finally {
            CardGroupRegistry.reattachGridViewForGroup(detachment);
        }
    }
}