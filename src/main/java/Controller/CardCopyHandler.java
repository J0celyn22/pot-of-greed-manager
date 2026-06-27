package Controller;

import Model.CardsLists.*;
import View.CardTreeCell;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles the "Add Copy" card-menu action: creating a brand-new
 * {@link CardElement} from a {@link Card} (e.g. one picked from the
 * all-cards database list) and inserting it into a target Box / Category of
 * the owned collection.
 * <p>
 * Extracted from {@link MenuActionHandler}, which still exposes
 * {@code handleAddCopy} as a thin delegate so existing call sites are
 * unaffected. {@code getOrCreateDefaultGroup} remains on
 * {@link MenuActionHandler} since it is also used directly by
 * {@code RealMainController} and {@code NavigationContextMenuBuilder}.
 * </p>
 */
final class CardCopyHandler {

    private static final Logger logger = LoggerFactory.getLogger(CardCopyHandler.class);

    private CardCopyHandler() {
    }

    // ── Add copy to owned collection ────────────────────────────────────────────

    /**
     * Creates a fresh {@link CardElement} from the given {@link Card} and inserts
     * it into the matching Box / Category of the {@link OwnedCardsCollection}.
     * The existing {@link Card} instance is reused directly so its printCode is
     * preserved.
     *
     * @param card          the card from AllExistingCards (not {@code null})
     * @param handlerTarget canonical target string, e.g. {@code "BoxName"},
     *                      {@code "BoxName / CategoryName"},
     *                      {@code "BoxName / SubBoxName"},
     *                      {@code "BoxName / SubBoxName / CategoryName"}
     */
    public static void handleAddCopy(Card card, String handlerTarget) {
        if (card == null || handlerTarget == null || handlerTarget.trim().isEmpty()) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                doAddCopy(card, handlerTarget);
            } else {
                Platform.runLater(() -> doAddCopy(card, handlerTarget));
            }
            UserInterfaceFunctions.refreshOwnedCollectionView();
        } catch (Throwable throwable) {
            logger.debug("handleAddCopy failed for target {}", handlerTarget, throwable);
        }
    }

    static void doAddCopy(Card card, String handlerTarget) {
        OwnedCardsCollection owned = MenuActionHandler.safeGetOwnedCollection();
        if (owned == null || owned.getOwnedCollection() == null) {
            logger.warn("OwnedCardsCollection not available; cannot add card to {}", handlerTarget);
            return;
        }

        CardsGroup dest = findMyCollectionDestination(handlerTarget, owned);
        if (dest == null) {
            logger.info("My Collection destination not found for '{}'; skipping add", handlerTarget);
            return;
        }

        CardElement newElement = new CardElement(card);

        // Add through the ObservableList: updates the model backing list AND notifies the GridView.
        CardTreeCell.observableListFor(dest).add(newElement);
        CardTreeCell.triggerHeightAdjustment(dest);
        logger.debug("doAddCopy: added '{}' to '{}'", card.getName_EN(), handlerTarget);

        MenuActionHandler.setLastAddedTarget(handlerTarget);

        try {
            OuicheList.onOwnedCardAdded(newElement);
            UserInterfaceFunctions.refreshOuicheListView();
        } catch (Throwable throwable) {
            logger.error("OuicheList update failed after adding owned card '{}'",
                    card.getName_EN(), throwable);
        }

        UserInterfaceFunctions.markMyCollectionDirty();
        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
    }

    /**
     * Finds the {@link CardsGroup} inside the {@link OwnedCardsCollection} that
     * matches the given path.
     * <p>
     * Path formats (slash-separated, trimmed, accent- and case-insensitive):
     * <ul>
     *   <li>{@code "BoxName"} → default group of that Box</li>
     *   <li>{@code "BoxName / CategoryName"} → named category inside Box</li>
     *   <li>{@code "BoxName / SubBoxName"} → default group of SubBox inside Box</li>
     *   <li>{@code "BoxName / SubBoxName / CatName"} → named category inside SubBox</li>
     * </ul>
     * </p>
     */
    private static CardsGroup findMyCollectionDestination(String handlerTarget,
                                                          OwnedCardsCollection owned) {
        java.util.function.Function<String, String> normalizer = input -> {
            if (input == null) {
                return "";
            }
            String normalized = input.trim()
                    .replaceAll("[=\\-]", "")
                    .replaceAll("\\s+", " ");
            normalized = java.text.Normalizer
                    .normalize(normalized, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            return normalized.toLowerCase().trim();
        };

        String[] parts = handlerTarget.split("/");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }

        String boxNorm = normalizer.apply(parts[0]);

        for (Box box : owned.getOwnedCollection()) {
            if (box == null || !normalizer.apply(box.getName()).equals(boxNorm)) {
                continue;
            }

            if (parts.length == 1) {
                // "BoxName" → default (empty-named) group, or first group, or create one
                return MenuActionHandler.getOrCreateDefaultGroup(box);
            }

            String secondNorm = normalizer.apply(parts[1]);

            // Could be a Category directly inside the Box
            if (parts.length == 2 && box.getContent() != null) {
                for (CardsGroup cardsGroup : box.getContent()) {
                    if (cardsGroup != null && normalizer.apply(cardsGroup.getName()).equals(secondNorm)) {
                        return cardsGroup;
                    }
                }
            }

            // Could be a SubBox
            if (box.getSubBoxes() != null) {
                for (Box subBox : box.getSubBoxes()) {
                    if (subBox == null || !normalizer.apply(subBox.getName()).equals(secondNorm)) {
                        continue;
                    }

                    if (parts.length == 2) {
                        // "BoxName / SubBoxName" → default group of SubBox
                        return MenuActionHandler.getOrCreateDefaultGroup(subBox);
                    }

                    // "BoxName / SubBoxName / CategoryName"
                    String catNorm = normalizer.apply(parts[2]);
                    if (subBox.getContent() != null) {
                        for (CardsGroup cardsGroup : subBox.getContent()) {
                            if (cardsGroup != null && normalizer.apply(cardsGroup.getName()).equals(catNorm)) {
                                return cardsGroup;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
}