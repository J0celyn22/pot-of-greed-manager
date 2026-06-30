package View;

import Model.CardsLists.CardElement;
import Model.CardsLists.CardsGroup;
import javafx.geometry.Insets;
import org.controlsfx.control.GridView;

/**
 * Static geometry helpers for sizing and laying out a ControlsFX
 * {@link GridView} of {@link CardElement}s.
 *
 * <p>All methods here are pure functions of the grid's own bound properties
 * (width, cell width/height, spacing, padding) and the item count — none of
 * them depend on {@link CardTreeCell} instance state. This is what allows
 * them to live in a standalone utility class rather than as methods on the
 * cell itself.</p>
 */
public final class GridViewSizer {

    /**
     * Extra padding (in pixels, on each side) added around a card's rendered
     * width/height to get its actual on-screen footprint, accounting for the
     * cell's border and inner spacing that {@link GridView} does not expose
     * directly via {@code getCellWidth()}/{@code getCellHeight()}.
     */
    private static final double CELL_INNER_PADDING = 5.0;

    private GridViewSizer() {
    }

    /**
     * Returns {@link #CELL_INNER_PADDING} for callers (such as
     * {@link CardTreeCell}) that need the same constant for related
     * pixel-geometry calculations outside this class.
     */
    public static double getCellInnerPadding() {
        return CELL_INNER_PADDING;
    }

    /**
     * Computes how many columns currently fit in {@code grid}, using the
     * grid's actual rendered width (falling back to its preferred width if
     * not yet laid out) and actual rendered cell width (including
     * {@link #CELL_INNER_PADDING}).
     *
     * @param grid the grid to measure (must not be {@code null})
     * @return the column count, at least {@code 1}
     */
    public static int computeGridColumns(GridView<CardElement> grid) {
        double totalWidth = grid.getWidth();
        if (totalWidth <= 0) {
            totalWidth = grid.getPrefWidth();
        }
        if (totalWidth <= 0) {
            return 1;
        }

        Insets padding = grid.getPadding();
        double horizontalPadding =
                (padding != null) ? padding.getLeft() + padding.getRight() : 0;
        double innerWidth = totalWidth - horizontalPadding;

        double cardWidth = grid.getCellWidth();
        double horizontalSpacing = grid.getHorizontalCellSpacing();
        double actualCellWidth = cardWidth + 2 * CELL_INNER_PADDING;

        return (int) Math.max(1,
                Math.floor((innerWidth + horizontalSpacing)
                        / (actualCellWidth + horizontalSpacing)));
    }

    /**
     * Computes the preferred pixel height for {@code grid} so that exactly
     * the rows needed for {@code itemCount} items (at the current column
     * count) are shown, with no extra empty row.
     *
     * @param grid      the grid to measure
     * @param itemCount the number of items the grid will display
     * @return the preferred height in pixels, or {@code 0} if
     * {@code itemCount <= 0}
     */
    public static double computeGridPrefHeight(GridView<CardElement> grid, int itemCount) {
        if (itemCount <= 0) {
            return 0;
        }

        int columns = computeGridColumns(grid);
        int rows = (int) Math.ceil((double) itemCount / columns);
        Insets padding = grid.getPadding();
        double top = (padding != null) ? padding.getTop() : 0;
        double bottom = (padding != null) ? padding.getBottom() : 0;
        double cardHeight = grid.getCellHeight();
        double verticalSpacing = grid.getVerticalCellSpacing();
        double actualCellHeight = cardHeight + 2 * CELL_INNER_PADDING;
        double rowSpan = actualCellHeight + verticalSpacing;

        return top + bottom + rows * rowSpan + 1.0;
    }

    /**
     * Sets {@code grid}'s preferred, minimum, and maximum height all to
     * {@code height}, effectively locking the grid's height so it never
     * grows or shrinks based on its container.
     */
    public static void applyGridPrefHeight(GridView<CardElement> grid, double height) {
        grid.setPrefHeight(height);
        grid.setMinHeight(height);
        grid.setMaxHeight(height);
    }

    /**
     * Convenience combining {@link #computeGridPrefHeight} and
     * {@link #applyGridPrefHeight}: recomputes and applies {@code grid}'s
     * height for the given item count in one call.
     *
     * @param grid      the grid to resize (no-op if {@code null})
     * @param itemCount the number of items currently shown
     */
    public static void adjustGridViewHeight(GridView<CardElement> grid, int itemCount) {
        if (grid == null) {
            return;
        }
        if (itemCount <= 0) {
            applyGridPrefHeight(grid, 0);
            return;
        }
        applyGridPrefHeight(grid, computeGridPrefHeight(grid, itemCount));
    }

    /**
     * Given a local X coordinate inside a {@link GridView} cell and the cell
     * width, returns {@code true} if the point is on the right half of the
     * card image (i.e. insert AFTER), {@code false} for the left half
     * (insert BEFORE).
     */
    public static boolean isRightHalf(double localX, double cardWidth) {
        return localX >= (cardWidth + 2 * CELL_INNER_PADDING) / 2.0;
    }

    /**
     * Computes the insertion index inside {@code group}'s list when the user
     * drops between cards at pixel position {@code gridLocalX, gridLocalY}
     * inside {@code grid}.
     *
     * @return the insertion index, or {@code -1} when the drop position is
     * outside the card rows entirely (e.g. below the last row)
     */
    public static int computeGapInsertionIndex(
            GridView<CardElement> grid,
            CardsGroup group,
            double gridLocalX,
            double gridLocalY) {
        if (group == null || group.getCardList() == null) {
            return -1;
        }
        int itemCount = group.getCardList().size();
        if (itemCount == 0) {
            return 0;
        }

        Insets padding = grid.getPadding();
        double paddingLeft = padding != null ? padding.getLeft() : 0;
        double paddingTop = padding != null ? padding.getTop() : 0;
        double horizontalSpacing = grid.getHorizontalCellSpacing();
        double verticalSpacing = grid.getVerticalCellSpacing();
        double cellWidth = grid.getCellWidth() + 2 * CELL_INNER_PADDING;
        double cellHeight = grid.getCellHeight() + 2 * CELL_INNER_PADDING;
        int columns = computeGridColumns(grid);

        double localX = gridLocalX - paddingLeft;
        double localY = gridLocalY - paddingTop;
        if (localX < 0 || localY < 0) {
            return 0;
        }

        int column = (int) Math.floor(localX / (cellWidth + horizontalSpacing));
        int row = (int) Math.floor(localY / (cellHeight + verticalSpacing));

        double columnFraction =
                (localX - column * (cellWidth + horizontalSpacing)) / cellWidth;
        // If click is in the spacing gap between columns, treat as right-half of left card.
        boolean inHorizontalGap = columnFraction > 1.0;
        column = Math.min(column, columns - 1);

        int flatIndex = row * columns + column;
        if (flatIndex >= itemCount) {
            return -1;
        }

        // If in the right half (or in a gap), insert after this card.
        if (inHorizontalGap || columnFraction >= 0.5) {
            flatIndex++;
        }
        return Math.min(flatIndex, itemCount);
    }
}