package Controller;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.TreeView;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Coordinates refreshing the JavaFX views (Owned Collection, Decks &amp;
 * Collections, OuicheList, Archetypes) after the underlying model changes.
 * <p>
 * Each view has a {@link ViewRefresherRegistry} that controller code registers
 * a rebuild callback with; the {@code refresh*}/{@code trigger*} methods here
 * fire those callbacks (deferring to the JavaFX Application Thread when needed)
 * so that model-change code doesn't need to know which concrete UI controls
 * exist for a given tab.
 */
public class ViewRefreshCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(ViewRefreshCoordinator.class);

    private static final ViewRefresherRegistry explicitRefreshers =
            new ViewRefresherRegistry("refreshOwnedCollectionView");

    private static final ViewRefresherRegistry explicitStructureRefreshers =
            new ViewRefresherRegistry("refreshOwnedCollectionStructure");

    // ── Archetypes refreshers ──────────────────────────────────────────────────
    // Called whenever decks/collections or the owned collection change, because
    // archetype glow states depend on which cards are present in both.
    private static final ViewRefresherRegistry explicitArchetypesRefreshers =
            new ViewRefresherRegistry("refreshArchetypesView");
    // ── Decks tree-view refreshers ────────────────────────────────────────────
    // A lightweight tree.refresh() (no model rebuild) called on every model
    // change so that archetype-card glow states inside Collections stay in sync.
    private static final ViewRefresherRegistry decksTreeRefreshers =
            new ViewRefresherRegistry("doRefreshArchetypesView (decksTree)");

    // ── Decks and Collections refreshers (mirrors the owned-collection pattern) ──
    private static final ViewRefresherRegistry explicitDecksRefreshers =
            new ViewRefresherRegistry("refreshDecksAndCollectionsView");

    // ── OuicheList refreshers ──────────────────────────────────────────────────
    private static final ViewRefresherRegistry explicitOuicheListRefreshers =
            new ViewRefresherRegistry("refreshOuicheListView");

    // ── Tab dirty-indicator callback ──────────────────────────────────────────────
    private static Runnable tabDirtyIndicatorUpdater = null;

    /**
     * Guard that prevents redundant archetype refreshes within the same FX frame.
     */
    private static volatile boolean archetypeRefreshScheduled = false;

    private ViewRefreshCoordinator() {
    }

    /**
     * Runs {@code action} immediately if already on the JavaFX Application Thread, or defers
     * it via {@link Platform#runLater} otherwise.
     *
     * <p>Every {@code refresh*}/{@code trigger*} method in this class is documented as "safe
     * to call from any thread." Honoring that requires this helper: if the JavaFX toolkit has
     * never been started — notably, in this project's own unit tests, which drive Controller
     * classes directly with no {@code Application} thread running — {@link Platform#runLater}
     * throws {@link IllegalStateException} synchronously at the call site rather than simply
     * deferring. None of this class's callers are prepared to handle that, and letting it
     * propagate would abort whatever model-mutation logic is still running in the caller
     * (e.g. {@code CardGroupRegistry.dropInsertIntoGroup}'s sibling-section redirect for a
     * deck-incompatible card, which runs after the primary insertion's own view refresh). The
     * failure is logged rather than surfaced.
     */
    private static void runOnFxThreadOrDefer(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
            return;
        }
        try {
            Platform.runLater(action);
        } catch (IllegalStateException toolkitNotInitialized) {
            logger.debug("Skipped deferred UI refresh — JavaFX toolkit not running", toolkitNotInitialized);
        }
    }

    /**
     * Always defers {@code action} via {@link Platform#runLater}, even when already on the
     * JavaFX Application Thread — for the handful of refreshers (full Decks &amp; Collections
     * tree rebuilds) that are unsafe to run synchronously from inside an in-flight event
     * handler on that same tree. See {@link #refreshDecksAndCollectionsView()}. Guarded the
     * same way as {@link #runOnFxThreadOrDefer} against an unstarted toolkit.
     */
    private static void deferToFxThread(Runnable action) {
        try {
            Platform.runLater(action);
        } catch (IllegalStateException toolkitNotInitialized) {
            logger.debug("Skipped deferred UI refresh — JavaFX toolkit not running", toolkitNotInitialized);
        }
    }

    public static void registerTabDirtyIndicatorUpdater(Runnable updater) {
        tabDirtyIndicatorUpdater = updater;
    }

    /**
     * Register an explicit refresher to be called when the owned-collection view should refresh.
     * Example: OwnedCollectionController can register a lambda that calls its refresh method.
     */
    public static void registerOwnedCollectionRefresher(Runnable refresher) {
        explicitRefreshers.register(refresher);
    }

    /**
     * Registers a callback that is invoked by {@link #refreshOuicheListView()} to
     * rebuild the OuicheList tab after an incremental model update. Duplicate
     * registrations are ignored.
     *
     * @param refresher the callback to register (ignored if {@code null})
     */
    public static void registerOuicheListRefresher(Runnable refresher) {
        explicitOuicheListRefreshers.register(refresher);
    }

    /**
     * Unregisters a previously registered OuicheList refresher.
     *
     * @param refresher the callback to unregister (ignored if {@code null})
     */
    public static void unregisterOuicheListRefresher(Runnable refresher) {
        explicitOuicheListRefreshers.unregister(refresher);
    }

    /**
     * Triggers a rebuild of the OuicheList tab view on the JavaFX Application
     * Thread, calling every registered {@link #registerOuicheListRefresher refresher}.
     * Safe to call from any thread.
     */
    public static void refreshOuicheListView() {
        runOnFxThreadOrDefer(ViewRefreshCoordinator::doRefreshOuicheListView);
    }

    private static void doRefreshOuicheListView() {
        explicitOuicheListRefreshers.runAll();
    }

    public static void triggerTabDirtyIndicatorUpdate() {
        if (tabDirtyIndicatorUpdater != null) {
            runOnFxThreadOrDefer(tabDirtyIndicatorUpdater);
        }
        // Every model modification calls this method, making it the single correct
        // place to keep the archetype markings (glow states) in sync.
        // We schedule via Platform.runLater so that multiple rapid modifications
        // (e.g. paste N cards) coalesce into one refresh at the end of the frame.
        if (!archetypeRefreshScheduled) {
            archetypeRefreshScheduled = true;
            try {
                Platform.runLater(() -> {
                    archetypeRefreshScheduled = false;
                    doRefreshArchetypesView();
                });
            } catch (IllegalStateException toolkitNotInitialized) {
                // The runnable above never ran, so its own reset never fired either --
                // clear the guard here instead, or every later call in this JVM would
                // see archetypeRefreshScheduled stuck true and skip scheduling forever.
                archetypeRefreshScheduled = false;
                logger.debug("Skipped deferred archetype refresh — JavaFX toolkit not running",
                        toolkitNotInitialized);
            }
        }
    }

    public static void registerDecksTreeRefresher(Runnable refresher) {
        decksTreeRefreshers.register(refresher);
    }

    public static void unregisterDecksTreeRefresher(Runnable refresher) {
        decksTreeRefreshers.unregister(refresher);
    }

    public static void registerArchetypesRefresher(Runnable refresher) {
        explicitArchetypesRefreshers.register(refresher);
    }

    public static void unregisterArchetypesRefresher(Runnable refresher) {
        explicitArchetypesRefreshers.unregister(refresher);
    }

    /**
     * Refreshes the Archetypes tab view.  Safe to call from any thread.
     * Should be invoked whenever the decks/collections model or the owned
     * collection changes, so that archetype glow states stay in sync.
     */
    public static void refreshArchetypesView() {
        runOnFxThreadOrDefer(ViewRefreshCoordinator::doRefreshArchetypesView);
    }

    private static void doRefreshArchetypesView() {
        // Re-render the D&C tree cells so archetype-card glow states update.
        // This is a lightweight .refresh() (no model rebuild) and is safe to call
        // at any time without losing selection or scroll position.
        decksTreeRefreshers.runAll();
        explicitArchetypesRefreshers.runAll();
    }

    public static void registerDecksCollectionsRefresher(Runnable refresher) {
        explicitDecksRefreshers.register(refresher);
    }

    public static void refreshDecksAndCollectionsView() {
        // IMPORTANT: always defer via Platform.runLater(), even when already on the FX thread.
        // doRefreshDecksAndCollectionsView() can trigger a full Decks & Collections tree
        // rebuild (DecksCollectionsController.displayDecksAndCollections()), which constructs
        // brand-new CardsGroup/TreeItem/cell instances for the whole tree. This method is most
        // often invoked from inside a drag-and-drop drop handler attached to a cell of that very
        // tree (see CardGridCell's wrapper.setOnDragDropped and CardTreeCell's
        // grid.setOnDragDropped). Running the rebuild synchronously there replaces the scene
        // graph nodes for the cell that is still mid-dispatch of the event — an unsafe JavaFX
        // pattern that caused intermittent state corruption (drops silently stopped reaching the
        // OuicheList after 1-2 successful drops). Deferring to a later pulse lets the originating
        // event finish dispatching on the still-valid scene graph before any rebuild begins.
        deferToFxThread(ViewRefreshCoordinator::doRefreshDecksAndCollectionsView);
    }

    private static void doRefreshDecksAndCollectionsView() {
        // Any call to refreshDecksAndCollectionsView() means the D&C model changed.
        // Always request a full tree rebuild so that archetype missing-sets (and
        // therefore archetype-card glow states) recompute from the updated model.
        PendingUiActionState.setPendingDecksFullRebuild();
        explicitDecksRefreshers.runAll();
    }

    /**
     * Unregister a previously registered refresher.
     */
    public static void unregisterOwnedCollectionRefresher(Runnable refresher) {
        explicitRefreshers.unregister(refresher);
    }

    /**
     * Refresh the Owned Cards Collection view without persisting changes.
     * This method does not save the model; it only forces visible UI controls to refresh.
     */
    public static void refreshOwnedCollectionView() {
        runOnFxThreadOrDefer(ViewRefreshCoordinator::doRefreshOwnedCollectionView);
    }

    // Core implementation: tries explicit refreshers first, then falls back to scanning all windows.
    private static void doRefreshOwnedCollectionView() {
        try {
            // 1) Call any explicit registered refreshers first (preferred)
            explicitRefreshers.runAll();
            // We still continue to scan and refresh controls to be safe.

            // 2) Walk all open windows and refresh TreeView/ListView controls found
            boolean refreshedAny = false;
            for (Window window : Window.getWindows()) {
                try {
                    Scene scene = window.getScene();
                    if (scene == null) {
                        continue;
                    }
                    Parent root = scene.getRoot();
                    if (root == null) {
                        continue;
                    }
                    // BFS traversal to find controls
                    Deque<Node> pendingNodes = new ArrayDeque<>();
                    pendingNodes.add(root);
                    while (!pendingNodes.isEmpty()) {
                        Node node = pendingNodes.poll();
                        if (node == null) {
                            continue;
                        }

                        // Refresh TreeView
                        if (node instanceof TreeView) {
                            try {
                                ((TreeView<?>) node).refresh();
                                refreshedAny = true;
                            } catch (Throwable throwable) {
                                logger.debug("refreshOwnedCollectionView: TreeView.refresh() failed", throwable);
                            }
                        }

                        // Refresh ListView
                        if (node instanceof ListView) {
                            try {
                                ((ListView<?>) node).refresh();
                                refreshedAny = true;
                            } catch (Throwable throwable) {
                                logger.debug("refreshOwnedCollectionView: ListView.refresh() failed", throwable);
                            }
                        }

                        // If node is a Parent, enqueue children
                        if (node instanceof Parent) {
                            ObservableList<Node> children = ((Parent) node).getChildrenUnmodifiable();
                            if (children != null && !children.isEmpty()) {
                                for (Node child : children) {
                                    pendingNodes.add(child);
                                }
                            }
                        }
                    }
                } catch (Throwable throwable) {
                    logger.debug("refreshOwnedCollectionView: scanning window failed", throwable);
                }
            }

            // 3) If nothing was refreshed, log a hint for integration
            if (!refreshedAny) {
                logger.info("refreshOwnedCollectionView: no TreeView/ListView refreshed. " +
                        "If your owned-collection UI uses custom controls, register an explicit refresher via registerOwnedCollectionRefresher(...).");
            } else {
                logger.debug("refreshOwnedCollectionView: refreshed visible controls");
            }
        } catch (Throwable throwable) {
            logger.debug("refreshOwnedCollectionView failed", throwable);
        }
    }

    public static void registerOwnedCollectionStructureRefresher(Runnable refresher) {
        explicitStructureRefreshers.register(refresher);
    }

    /**
     * Triggers a full structural rebuild of the owned-collection view
     * (tree structure changed — boxes or categories were moved/added/removed).
     * Also triggers the normal view refresh so the nav menu stays in sync.
     */
    public static void refreshOwnedCollectionStructure() {
        runOnFxThreadOrDefer(ViewRefreshCoordinator::doRefreshOwnedCollectionStructure);
        // Always update the nav menu too
        refreshOwnedCollectionView();
    }

    private static void doRefreshOwnedCollectionStructure() {
        explicitStructureRefreshers.runAll();
    }

    /**
     * Fires the structural (full tree-rebuild) refreshers for the Decks &amp; Collections tab.
     * Use this instead of {@code refreshDecksAndCollectionsView()} when a section that was
     * previously empty has received its first card and its DataTreeItem doesn't exist yet.
     */
    public static void triggerDecksStructureRefresh() {
        // Signal a full structural rebuild so the refresher calls displayDecksAndCollections().
        PendingUiActionState.setPendingDecksFullRebuild();
        // Always deferred — see the comment in refreshDecksAndCollectionsView() for why this
        // must never run synchronously inside an originating event handler.
        deferToFxThread(ViewRefreshCoordinator::doRefreshDecksAndCollectionsView);
    }
}