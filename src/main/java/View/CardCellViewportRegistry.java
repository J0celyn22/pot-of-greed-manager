package View;

import Model.CardsLists.CardElement;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.control.TreeView;
import javafx.scene.input.ScrollEvent;
import org.controlsfx.control.GridView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Tracks every live {@link CardGridCell} and decides, on scroll or resize, which ones are
 * close enough to the visible viewport to actually load their image.
 *
 * <p>{@link GridViewSizer#applyGridPrefHeight} locks each {@code GridView}'s height to its
 * full content height so it never collapses inside its parent {@code TreeCell} — but that
 * also means ControlsFX's own virtualization never kicks in: every {@code CardGridCell} in a
 * group is realized and would otherwise load its image immediately, even for a 5000-card
 * group with only 30 cards on screen. This registry restores the missing gate: cells load
 * their image only when their row intersects the {@code TreeView}'s viewport plus a margin,
 * and release it again once they scroll far enough away.</p>
 *
 * <p>Registration uses weak references. A rebuild that discards a {@code GridView} (see
 * {@code CardTreeCell#buildMosaicModeGroupContent}, the {@code reusedGrid == null} branch)
 * throws away every one of its cells without ever calling {@code updateItem(empty=true)} on
 * them — a strongly-held set would keep every discarded cell (and everything it references:
 * its {@code ImageView}, decoded {@code Image}, and parent {@code CardTreeCell}) alive
 * forever, which is exactly the kind of leak this project is already chasing elsewhere.</p>
 */
final class CardCellViewportRegistry {

    private static final Logger logger = LoggerFactory.getLogger(CardCellViewportRegistry.class);

    /**
     * How many consecutive sweeps a grid is allowed to report unresolved geometry (not yet
     * laid out, or detached from the scene) before the registry stops automatically
     * rescheduling on its account. Without this bound, a permanently detached grid would
     * keep requesting another sweep forever.
     */
    private static final int MAX_UNRESOLVED_RETRIES = 3;

    private static final Set<CardGridCell> registeredCells =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static boolean sweepScheduled = false;
    private static int unresolvedRetryCount = 0;

    private CardCellViewportRegistry() {
    }

    /**
     * Registers {@code cell} as currently displaying an item, and requests a sweep so its
     * visibility gets evaluated. Idempotent — calling it again for a cell already registered
     * (the common case: {@code GridView} redrawing a cell that keeps the same item) is a
     * cheap no-op via the underlying set.
     */
    static void register(CardGridCell cell) {
        if (!Platform.isFxApplicationThread()) {
            logger.warn("register() called off the FX thread, ignoring");
            return;
        }
        registeredCells.add(cell);
        markDirty();
    }

    /**
     * Removes {@code cell} from tracking. Called from the empty branch of {@code updateItem}.
     * Not strictly required for correctness (the weak set would drop it on its own once GC'd)
     * but keeps the set small immediately instead of waiting on a collection.
     */
    static void unregister(CardGridCell cell) {
        if (!Platform.isFxApplicationThread()) {
            logger.warn("unregister() called off the FX thread, ignoring");
            return;
        }
        registeredCells.remove(cell);
    }

    /**
     * Requests a viewport sweep. Coalesced onto the next pulse — safe and cheap to call from
     * a tight loop (scroll events, bounds listeners firing on every realized cell).
     */
    static void markDirty() {
        if (sweepScheduled) {
            return;
        }
        sweepScheduled = true;
        Platform.runLater(CardCellViewportRegistry::sweep);
    }

    /**
     * Wires the fallback scroll/resize signals for {@code treeView}: a direct scroll-event
     * filter and a height-change listener. Idempotent per {@code treeView} via a marker in
     * its {@code getProperties()} map (not a second static collection — that would itself
     * become a GC root for every {@code TreeView} ever built).
     *
     * <p>These are a safety net, not the primary signal. The primary signal is each
     * {@code CardTreeCell}'s own {@code boundsInParentProperty} listener (wired in its
     * constructor), which fires whenever {@code VirtualFlow} repositions a realized cell —
     * covering wheel/trackpad scroll, scrollbar drag, keyboard navigation, {@code scrollTo},
     * expand/collapse, and drag autoscroll, without depending on skin internals.</p>
     */
    static void attachScrollHooks(TreeView<?> treeView) {
        if (treeView == null) {
            return;
        }
        Object marker = treeView.getProperties().get("viewportRegistryHooksAttached");
        if (Boolean.TRUE.equals(marker)) {
            return;
        }
        treeView.getProperties().put("viewportRegistryHooksAttached", Boolean.TRUE);
        treeView.addEventFilter(ScrollEvent.ANY, event -> markDirty());
        treeView.heightProperty().addListener((observable, oldValue, newValue) -> markDirty());
    }

    /**
     * Evaluates every registered cell against its {@code GridView}'s current viewport
     * intersection and applies the result. Memoizes the visible-index range per
     * {@code GridView} (at most two {@code localToScene} calls per group per sweep) rather
     * than transforming bounds per cell.
     */
    private static void sweep() {
        sweepScheduled = false;
        Map<GridView<CardElement>, int[]> loadRangeByGrid = new IdentityHashMap<>();
        Map<GridView<CardElement>, int[]> retentionRangeByGrid = new IdentityHashMap<>();
        boolean anyUnresolved = false;

        for (CardGridCell cell : registeredCells) {
            if (cell.isEmpty() || cell.getItem() == null) {
                continue;
            }
            GridView<CardElement> grid = cell.getGridView();
            if (grid == null) {
                continue;
            }

            int[] loadRange = loadRangeByGrid.computeIfAbsent(grid,
                    key -> computeRangeOrNull(key, 1.0));
            int[] retentionRange = retentionRangeByGrid.computeIfAbsent(grid,
                    key -> computeRangeOrNull(key, 3.0));

            if (loadRange == null || retentionRange == null) {
                anyUnresolved = true;
                continue;
            }

            int index = cell.getIndex();
            boolean withinLoadBand = index >= loadRange[0] && index <= loadRange[1];
            boolean withinRetentionBand = index >= retentionRange[0] && index <= retentionRange[1];
            cell.applyViewportState(withinLoadBand, withinRetentionBand);
        }

        if (anyUnresolved && unresolvedRetryCount < MAX_UNRESOLVED_RETRIES) {
            unresolvedRetryCount++;
            markDirty();
        } else {
            unresolvedRetryCount = 0;
        }
    }

    /**
     * Computes the visible index range for {@code grid} at the given viewport-margin
     * multiplier (in units of the {@code TreeView}'s own viewport height), or {@code null}
     * when the geometry isn't resolvable yet (not laid out, or detached from the scene).
     * {@code null} means "unknown", never "nothing visible" — callers must not treat it as
     * an empty range, or a group opened without ever being scrolled would stay permanently
     * blank.
     */
    private static int[] computeRangeOrNull(GridView<CardElement> grid, double marginInViewports) {
        if (grid.getScene() == null) {
            return null;
        }
        // The grid's cell factory always builds CardGridCell instances whose `outer` field
        // is the owning CardTreeCell; any realized cell of this grid gives us that reference.
        CardTreeCell owner = findOwner(grid);
        if (owner == null || owner.getTreeView() == null) {
            return null;
        }
        javafx.scene.control.TreeView<String> treeView = owner.getTreeView();

        Bounds viewportBounds = treeView.localToScene(treeView.getBoundsInLocal());
        Bounds gridBounds = grid.localToScene(grid.getBoundsInLocal());
        if (viewportBounds == null || gridBounds == null
                || viewportBounds.getHeight() <= 0 || gridBounds.getHeight() <= 0) {
            return null;
        }

        int columns = GridViewSizer.computeGridColumns(grid);
        double cardHeight = grid.getCellHeight();
        double verticalSpacing = grid.getVerticalCellSpacing();
        double rowSpan = cardHeight + 2 * GridViewSizer.getCellInnerPadding() + verticalSpacing;
        int itemCount = grid.getItems() == null ? 0 : grid.getItems().size();

        double marginPixels = viewportBounds.getHeight() * marginInViewports;
        javafx.geometry.Insets padding = grid.getPadding();
        double paddingTop = padding != null ? padding.getTop() : 0;

        return GridViewSizer.computeVisibleIndexRange(
                gridBounds.getMinY(),
                viewportBounds.getMinY(),
                viewportBounds.getMaxY(),
                paddingTop,
                rowSpan,
                columns,
                itemCount,
                marginPixels);
    }

    /**
     * Finds the {@code CardTreeCell} that owns {@code grid}, via any of its currently
     * realized {@code CardGridCell} children. {@code GridView} does not expose its owning
     * {@code TreeCell} directly, but every cell it creates carries that reference.
     */
    private static CardTreeCell findOwner(GridView<CardElement> grid) {
        for (CardGridCell cell : registeredCells) {
            if (cell.getGridView() == grid) {
                return cell.outer;
            }
        }
        return null;
    }
}
