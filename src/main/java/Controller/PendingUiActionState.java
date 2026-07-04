package Controller;

/**
 * Holds transient, one-shot UI state describing an action the Decks &amp; Collections
 * tree view should perform on its next rebuild or refresh: renaming a node inline,
 * expanding a node, scrolling to a node, forcing a full tree rebuild, or seeding the
 * "create collection from deck" dialog.
 * <p>
 * Each piece of state is set once by the action that triggers it and consumed
 * (read and cleared) exactly once by the view code that reacts to it, hence the
 * "getAndClear" naming pattern used throughout.
 */
public class PendingUiActionState {

    private static Object pendingRenameTarget = null;
    private static Object pendingDecksRenameTarget = null;

    /**
     * Set to true when a rename confirm requires a full middle-pane tree rebuild
     * but must NOT trigger another inline-rename attempt.
     */
    private static volatile boolean pendingDecksFullRebuild = false;
    private static Object pendingDecksExpandTarget = null;
    private static Object pendingDecksScrollTarget = null;
    // Stores {ThemeCollection, Deck} for the "Create Collection from Deck" flow
    private static Object[] pendingDecksCreateCollectionData = null;

    private PendingUiActionState() {
    }

    public static void setPendingRenameTarget(Object target) {
        pendingRenameTarget = target;
    }

    public static Object getAndClearPendingRenameTarget() {
        Object renameTarget = pendingRenameTarget;
        pendingRenameTarget = null;
        return renameTarget;
    }

    public static void setPendingDecksRenameTarget(Object target) {
        pendingDecksRenameTarget = target;
    }

    public static Object getAndClearPendingDecksRenameTarget() {
        Object renameTarget = pendingDecksRenameTarget;
        pendingDecksRenameTarget = null;
        return renameTarget;
    }

    public static void setPendingDecksFullRebuild() {
        pendingDecksFullRebuild = true;
    }

    public static boolean getAndClearPendingDecksFullRebuild() {
        boolean wasPendingFullRebuild = pendingDecksFullRebuild;
        pendingDecksFullRebuild = false;
        return wasPendingFullRebuild;
    }

    public static void setPendingDecksExpandTarget(Object target) {
        pendingDecksExpandTarget = target;
    }

    public static Object getAndClearPendingDecksExpandTarget() {
        Object expandTarget = pendingDecksExpandTarget;
        pendingDecksExpandTarget = null;
        return expandTarget;
    }

    public static void setPendingDecksScrollTarget(Object target) {
        pendingDecksScrollTarget = target;
    }

    public static Object getAndClearPendingDecksScrollTarget() {
        Object scrollTarget = pendingDecksScrollTarget;
        pendingDecksScrollTarget = null;
        return scrollTarget;
    }

    public static void setPendingDecksCreateCollectionData(Object[] data) {
        pendingDecksCreateCollectionData = data;
    }

    public static Object[] getAndClearPendingDecksCreateCollectionData() {
        Object[] createCollectionData = pendingDecksCreateCollectionData;
        pendingDecksCreateCollectionData = null;
        return createCollectionData;
    }
}