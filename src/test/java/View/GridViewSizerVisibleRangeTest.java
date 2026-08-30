package View;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Pure-logic coverage for {@link GridViewSizer#computeVisibleIndexRange}, the geometry behind
 * {@link CardCellViewportRegistry}'s viewport gate. No FX toolkit involved — every parameter is
 * a primitive, which is exactly why the method was extracted this way.
 */
class GridViewSizerVisibleRangeTest {

    private static final double ROW_SPAN = 100.0;
    private static final int COLUMNS = 4;
    private static final int ITEM_COUNT = 40;
    private static final double PADDING_TOP = 0.0;
    private static final double GRID_TOP = 0.0;

    @Test
    void viewportEntirelyAboveGrid_isEmpty() {
        int[] range = GridViewSizer.computeVisibleIndexRange(
                GRID_TOP, -500, -100, PADDING_TOP, ROW_SPAN, COLUMNS, ITEM_COUNT, 0);
        assertArrayEquals(new int[]{-1, -1}, range);
    }

    @Test
    void viewportEntirelyBelowGrid_isEmpty() {
        // 40 items / 4 columns = 10 rows -> content ends at row 10 * 100 = 1000.
        int[] range = GridViewSizer.computeVisibleIndexRange(
                GRID_TOP, 5000, 5500, PADDING_TOP, ROW_SPAN, COLUMNS, ITEM_COUNT, 0);
        assertArrayEquals(new int[]{-1, -1}, range);
    }

    @Test
    void viewportSpanningTwoRows_selectsThoseRowsExactly() {
        // Rows 2 and 3 (0-indexed) span y in [200, 400). Bottom kept just short of the row-4
        // boundary so this case doesn't overlap with the boundary-inclusion behavior covered
        // by negativeGridTop_keepsFromIndexNonNegative below.
        int[] range = GridViewSizer.computeVisibleIndexRange(
                GRID_TOP, 200, 399, PADDING_TOP, ROW_SPAN, COLUMNS, ITEM_COUNT, 0);
        // Row 2 starts at index 8, row 3 ends at index 15.
        assertArrayEquals(new int[]{8, 15}, range);
    }

    @Test
    void lastRowPartial_toIndexClampedToItemCount() {
        // 10 items, 4 columns -> rows: [0-3], [4-7], [8-9] (partial last row). Viewport
        // [150, 250) falls in row 1 only (y in [100, 200)) -> row 1 starts at index 4.
        int[] range = GridViewSizer.computeVisibleIndexRange(
                GRID_TOP, 150, 250, PADDING_TOP, ROW_SPAN, COLUMNS, 10, 0);
        assertArrayEquals(new int[]{4, 9}, range);
    }

    @Test
    void marginPushingFirstRowNegative_clampsToZero() {
        // A large margin pushes the top offset negative (row -5 mathematically) -> clamped to
        // row 0. The bottom offset (150 + 500 = 650) reaches into row 6 -> toIndex 27.
        int[] range = GridViewSizer.computeVisibleIndexRange(
                GRID_TOP, 50, 150, PADDING_TOP, ROW_SPAN, COLUMNS, ITEM_COUNT, 500);
        assertArrayEquals(new int[]{0, 27}, range);
    }

    @Test
    void zeroRowSpan_returnsEmptyWithoutDivisionByZero() {
        int[] range = GridViewSizer.computeVisibleIndexRange(
                GRID_TOP, 0, 1000, PADDING_TOP, 0, COLUMNS, ITEM_COUNT, 0);
        assertArrayEquals(new int[]{-1, -1}, range);
    }

    @Test
    void zeroColumns_returnsEmpty() {
        int[] range = GridViewSizer.computeVisibleIndexRange(
                GRID_TOP, 0, 1000, PADDING_TOP, ROW_SPAN, 0, ITEM_COUNT, 0);
        assertArrayEquals(new int[]{-1, -1}, range);
    }

    @Test
    void zeroItemCount_returnsEmpty() {
        int[] range = GridViewSizer.computeVisibleIndexRange(
                GRID_TOP, 0, 1000, PADDING_TOP, ROW_SPAN, COLUMNS, 0, 0);
        assertArrayEquals(new int[]{-1, -1}, range);
    }

    @Test
    void singleColumnSingleItem_selectsIt() {
        int[] range = GridViewSizer.computeVisibleIndexRange(
                GRID_TOP, 0, 50, PADDING_TOP, ROW_SPAN, 1, 1, 0);
        assertArrayEquals(new int[]{0, 0}, range);
    }

    @Test
    void negativeGridTop_keepsFromIndexNonNegative() {
        // Grid scrolled above the scene origin; viewport overlaps rows 0 and 1 (viewport
        // bottom offset of 100 lands exactly on the row-0/row-1 boundary, which floor()
        // resolves by including row 1 too -- over-inclusive at the edge, never under).
        int[] range = GridViewSizer.computeVisibleIndexRange(
                -1000, -950, -900, PADDING_TOP, ROW_SPAN, COLUMNS, ITEM_COUNT, 0);
        assertArrayEquals(new int[]{0, 7}, range);
    }
}
