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
     * "RIGHT", "MIDDLE", or "NAV" – which pane started the drag.
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

    /**
     * NAV-pane drag: the model object (Box, CardsGroup, Deck, ThemeCollection)
     * being reordered within the navigation menu. Null when the drag did not
     * originate from the navigation menu.
     */
    private static volatile Object draggedNavObject = null;

    private DragDropManager() {
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    /**
     * Called when a RIGHT-pane drag starts.
     *
     * @param cards the ordered list of cards being dragged; index 0 is the primary card
     */
    public static void startRightDrag(List<Card> cards) {
        dragSourcePane = "RIGHT";
        draggedCards = cards != null ? new ArrayList<>(cards) : Collections.emptyList();
        draggedElements = Collections.emptyList();
        currentlyDraggedCard = draggedCards.isEmpty() ? null : draggedCards.get(0);
    }

    /**
     * Called when a MIDDLE-pane drag starts.
     *
     * @param elements the ordered list of card elements being dragged; index 0 is the primary element
     */
    public static void startMiddleDrag(List<CardElement> elements) {
        dragSourcePane = "MIDDLE";
        draggedElements = elements != null ? new ArrayList<>(elements) : Collections.emptyList();
        draggedCards = Collections.emptyList();
        currentlyDraggedCard = draggedElements.isEmpty() || draggedElements.get(0).getCard() == null
                ? null : draggedElements.get(0).getCard();
        draggedNavObject = null;
    }

    /**
     * Called when a NAV-pane drag starts (reordering navigation items).
     *
     * @param modelObject the model object attached to the dragged NavigationItem
     *                    (Box, CardsGroup, Deck, ThemeCollection, etc.)
     */
    public static void startNavDrag(Object modelObject) {
        dragSourcePane = "NAV";
        draggedNavObject = modelObject;
        draggedCards = Collections.emptyList();
        draggedElements = Collections.emptyList();
        currentlyDraggedCard = null;
    }

    /**
     * Clears all drag state. Should be called in the {@code onDragDone} handler.
     */
    public static void clearCurrentlyDraggedCard() {
        dragSourcePane = null;
        draggedCards = Collections.emptyList();
        draggedElements = Collections.emptyList();
        currentlyDraggedCard = null;
        draggedNavObject = null;
    }

    /**
     * Returns the identifier of the pane that initiated the current drag
     * ("RIGHT", "MIDDLE", or "NAV"), or {@code null} if no drag is active.
     *
     * @return the drag-source pane identifier
     */
    public static String getDragSourcePane() {
        return dragSourcePane;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /**
     * Returns an unmodifiable view of the dragged cards; index 0 is the primary card
     * (the one directly under the mouse). Empty when the drag did not originate from
     * the RIGHT pane.
     *
     * @return unmodifiable list of dragged cards
     */
    public static List<Card> getDraggedCards() {
        return Collections.unmodifiableList(draggedCards);
    }

    /**
     * Returns an unmodifiable view of the dragged card elements; index 0 is the primary
     * element (the one directly under the mouse). Empty when the drag did not originate
     * from the MIDDLE pane.
     *
     * @return unmodifiable list of dragged card elements
     */
    public static List<CardElement> getDraggedElements() {
        return Collections.unmodifiableList(draggedElements);
    }

    /**
     * Returns the primary (first) card being dragged, regardless of source pane.
     * May be {@code null} when no drag is active or when the drag source is the NAV pane.
     *
     * @return the primary dragged card, or {@code null}
     */
    public static Card getCurrentlyDraggedCard() {
        return currentlyDraggedCard;
    }

    /**
     * Legacy API – sets the single dragged card (RIGHT pane, no multi-select).
     *
     * @param card the card to register as dragged, or {@code null} to clear
     */
    public static void setCurrentlyDraggedCard(Card card) {
        startRightDrag(card != null ? Collections.singletonList(card) : Collections.emptyList());
    }

    /**
     * Returns the model object being dragged from the navigation menu.
     * Non-null only when {@link #getDragSourcePane()} returns {@code "NAV"}.
     *
     * @return the dragged navigation model object, or {@code null}
     */
    public static Object getDraggedNavObject() {
        return draggedNavObject;
    }

    // ── Ghost image builder ───────────────────────────────────────────────────

    /**
     * Composes a transparent drag-ghost image from up to 5 card images.
     *
     * <p>Index 0 in {@code cardImages} is the frontmost card (the one directly
     * under the mouse). Each subsequent card is drawn {@code cardWidth / 5} px to
     * the right, fully clipped by the canvas — no bleed-through despite overall
     * transparency.
     *
     * <p>The returned image should be anchored at {@code (cardWidth / 2, cardHeight / 2)}
     * so the cursor stays centred on the front card.
     *
     * @param cardImages  ordered images; {@code null} entries are skipped
     * @param cardWidth   logical card width in pixels
     * @param cardHeight  logical card height in pixels
     * @return composited ghost image, or {@code null} if no valid image was found
     */
    public static WritableImage buildDragGhost(
            List<Image> cardImages, double cardWidth, double cardHeight) {
        if (cardImages == null || cardImages.isEmpty()) {
            return null;
        }

        // Count non-null images, capped at 5
        int nonNullCount = 0;
        for (Image image : cardImages) {
            if (image != null && ++nonNullCount == 5) {
                break;
            }
        }
        if (nonNullCount == 0) {
            return null;
        }

        double offset = cardWidth / 5.0;
        double totalWidth = cardWidth + (nonNullCount - 1) * offset;
        Canvas canvas = new Canvas(Math.ceil(totalWidth), Math.ceil(cardHeight));
        GraphicsContext graphicsContext = canvas.getGraphicsContext2D();

        // Collect the non-null images (up to 5)
        List<Image> validImages = new ArrayList<>(nonNullCount);
        for (Image image : cardImages) {
            if (image != null) {
                validImages.add(image);
                if (validImages.size() == 5) {
                    break;
                }
            }
        }

        // Draw back-to-front so index 0 ends on top
        for (int i = validImages.size() - 1; i >= 0; i--) {
            graphicsContext.drawImage(validImages.get(i), i * offset, 0, cardWidth, cardHeight);
        }

        SnapshotParameters snapshotParams = new SnapshotParameters();
        snapshotParams.setFill(Color.TRANSPARENT);
        return canvas.snapshot(snapshotParams, null);
    }
}