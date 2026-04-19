package Controller;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central registry for the current drag-and-drop operation.
 *
 * <p>There are two drag sources:
 * <ul>
 *   <li><b>RIGHT pane</b> – rows in a ListView&lt;Card&gt; or mosaic cells.
 *       Payload: an ordered list of {@link Card} objects.
 *       The first card is the one directly under the mouse.</li>
 *   <li><b>MIDDLE pane</b> – GridCell&lt;CardElement&gt; inside a CardTreeCell.
 *       Payload: an ordered list of {@link CardElement} objects.
 *       The first element is the one directly under the mouse.</li>
 * </ul>
 *
 * <p>The Dragboard string content is set to the passCode of the primary card
 * (kept for compatibility), but callers that need the full payload should
 * use {@link #getDraggedCards()} / {@link #getDraggedElements()}.
 */
public final class DragDropManager {

    // ── Payload ───────────────────────────────────────────────────────────────

    /**
     * "RIGHT" or "MIDDLE" – which pane started the drag.
     */
    private static volatile String dragSourcePane = null;

    /**
     * RIGHT-pane drag: ordered cards, index 0 = primary (under mouse).
     */
    private static volatile List<Card> draggedCards = Collections.emptyList();

    /**
     * MIDDLE-pane drag: ordered elements, index 0 = primary (under mouse).
     */
    private static volatile List<CardElement> draggedElements = Collections.emptyList();

    // ── Legacy single-card field (kept for callers that only need one card) ──
    private static volatile Card currentlyDraggedCard = null;

    private DragDropManager() {
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    /**
     * Called when a RIGHT-pane drag starts.
     */
    public static void startRightDrag(List<Card> cards) {
        dragSourcePane = "RIGHT";
        draggedCards = cards != null ? new ArrayList<>(cards) : Collections.emptyList();
        draggedElements = Collections.emptyList();
        currentlyDraggedCard = draggedCards.isEmpty() ? null : draggedCards.get(0);
    }

    /**
     * Called when a MIDDLE-pane drag starts.
     */
    public static void startMiddleDrag(List<CardElement> elements) {
        dragSourcePane = "MIDDLE";
        draggedElements = elements != null ? new ArrayList<>(elements) : Collections.emptyList();
        draggedCards = Collections.emptyList();
        currentlyDraggedCard = draggedElements.isEmpty() || draggedElements.get(0).getCard() == null
                ? null : draggedElements.get(0).getCard();
    }

    /** Clears all drag state. Call in onDragDone. */
    public static void clearCurrentlyDraggedCard() {
        dragSourcePane = null;
        draggedCards = Collections.emptyList();
        draggedElements = Collections.emptyList();
        currentlyDraggedCard = null;
    }

    public static String getDragSourcePane() {
        return dragSourcePane;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /**
     * Unmodifiable view; index 0 is the primary card (under mouse).
     */
    public static List<Card> getDraggedCards() {
        return Collections.unmodifiableList(draggedCards);
    }

    /**
     * Unmodifiable view; index 0 is the primary element (under mouse).
     */
    public static List<CardElement> getDraggedElements() {
        return Collections.unmodifiableList(draggedElements);
    }

    /**
     * The primary (first) card being dragged, regardless of source pane.
     */
    public static Card getCurrentlyDraggedCard() {
        return currentlyDraggedCard;
    }

    /** Legacy API – sets the single dragged card (RIGHT pane, no multi-select). */
    public static void setCurrentlyDraggedCard(Card card) {
        startRightDrag(card != null ? Collections.singletonList(card) : Collections.emptyList());
    }

    // ── Ghost image builder ───────────────────────────────────────────────────

    /**
     * Composes a transparent drag-ghost image from up to 5 card images.
     *
     * <p>Index 0 in {@code cardImages} is the frontmost card (the one directly
     * under the mouse). Each subsequent card is drawn {@code cardWidth/5} px to
     * the right and underneath, fully clipped by the canvas — no bleed-through
     * despite overall transparency.
     *
     * <p>The returned image should be anchored at {@code (cardWidth/2, cardHeight/2)}
     * so the cursor stays centered on the front card.
     *
     * @param cardImages ordered images; {@code null} entries are skipped
     * @param cardWidth  logical card width in pixels
     * @param cardHeight logical card height in pixels
     * @return composited ghost image, or {@code null} if no valid image was found
     */
    public static WritableImage buildDragGhost(
            List<Image> cardImages, double cardWidth, double cardHeight) {
        if (cardImages == null || cardImages.isEmpty()) return null;

        // Count non-null images, cap at 5
        int n = 0;
        for (Image img : cardImages) {
            if (img != null && ++n == 5) break;
        }
        if (n == 0) return null;

        double offset = cardWidth / 5.0;
        double totalW = cardWidth + (n - 1) * offset;
        Canvas canvas = new Canvas(Math.ceil(totalW), Math.ceil(cardHeight));
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Collect the non-null images (up to 5)
        List<Image> valid = new ArrayList<>(n);
        for (Image img : cardImages) {
            if (img != null) {
                valid.add(img);
                if (valid.size() == 5) break;
            }
        }

        // Draw back-to-front so index 0 ends on top
        for (int i = valid.size() - 1; i >= 0; i--) {
            gc.drawImage(valid.get(i), i * offset, 0, cardWidth, cardHeight);
        }

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return canvas.snapshot(sp, null);
    }
}