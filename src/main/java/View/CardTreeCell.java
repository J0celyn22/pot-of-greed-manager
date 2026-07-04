package View;

import Controller.CardGroupRegistry;
import Controller.UserInterfaceFunctions;
import Model.CardsLists.*;
import Utils.CardMatcher;
import Utils.CardNameUtils;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.controlsfx.control.GridView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * CardTreeCell - updated to accept DataTreeItem data that may be:
 * - a CardsGroup (normal groups), or
 * - a Map<String,Object> with keys "group" (CardsGroup) and "missing" (Set<String>) for archetype groups.
 *
 * Additionally: when rendering groups that belong to the "My Cards Collection" tree,
 * the GridView userData is set to a Set<String> of identifiers for cards that "need sorting".
 * For this first iteration "need sorting" is defined as:
 *   - card.getName_EN() contains "Trap" (case-insensitive)
 *   - OR card.getName_FR() contains "Piège" (case-insensitive)
 *
 * The renderer checks only that per-group missing set when deciding to glow.
 */
public class CardTreeCell extends TreeCell<String> {

    private static final Logger logger = LoggerFactory.getLogger(CardTreeCell.class);

    /**
     * When {@code true}, cards in the My Collection tab that have a missing
     * printCode glow red, and cards missing condition or rarity glow orange.
     * Toggled by the "Mark incomplete cards" button in the header.
     */
    static volatile boolean incompleteMarkingEnabled = true;

    /**
     * When {@code true}, the condition/rarity overlay is rendered on cards
     * in the grid views. Toggled by the "Show condition/rarity" button in the header.
     */
    private static volatile boolean showConditionRarityOverlayEnabled = false;

    final DoubleProperty cardWidthProperty;
    final DoubleProperty cardHeightProperty;

    /**
     * Handles all async image loading for this cell.
     */
    final CardImageLoader imageLoader;

    private Label customTriangleLabel;
    private GridView<CardElement> cardGridView;

    private static final String ARCHETYPE_MARKER = "[ARCHETYPE]";

    /**
     * Maps each Deck to a sub-map keyed "main" / "extra" / "side".
     * Strong HashMap so GC never drops a Deck key mid-operation.
     */

    // Shared hover popup — one instance per CardTreeCell.
    // CardGridCell instances access it via CardTreeCell.this.
    final javafx.stage.Popup hoverPopup = new javafx.stage.Popup();
    final Label hoverLabel = new Label();
    /**
     * The VBox that wraps hoverLabel + warningsBox and carries the popup border.
     */
    final javafx.scene.layout.VBox hoverPopupBox = new javafx.scene.layout.VBox(0);
    /**
     * Maps each CardsGroup to the set of passCodes that belong to a
     * multi-artwork card whose collection is missing at least one artwork.
     * Populated by RealMainController when building the D&C / OuicheList tree.
     * WeakHashMap: entries are GC'd when the CardsGroup is no longer referenced.
     */

    /**
     * Legacy global set kept for compatibility; preferred flow is per-group missing sets.
     */

    public static void markArchetypeMissing(String id, boolean missing) {
        if (id == null) return;
        if (missing) CardGroupRegistry.LEGACY_GLOBAL_MISSING_SET.add(id);
        else CardGroupRegistry.LEGACY_GLOBAL_MISSING_SET.remove(id);
    }

    // ── Static registries ──────────────────────────────────────────────────────
    // WeakHashMap: entries are GC'd when the CardsGroup itself is no longer referenced.


    /**
     * Maps each ThemeCollection to its "Cards" CardsGroup so pasteCardsIntoNavigationItem
     * can add through the ObservableList rather than the raw backing list.
     */

    /**
     * Maps each ThemeCollection to its "Cards not to add" CardsGroup.
     */

    public static void clearArchetypeMissingSet() {
        CardGroupRegistry.LEGACY_GLOBAL_MISSING_SET.clear();
    }
    /**
     * Container for per-warning labels shown below the main info in the hover
     * popup. Each applicable warning gets its own coloured Label added at
     * hover time, so multiple warnings can appear simultaneously.
     */
    final javafx.scene.layout.VBox warningsBox = new javafx.scene.layout.VBox(2);

    public CardTreeCell(DoubleProperty cardWidthProperty, DoubleProperty cardHeightProperty) {
        this.cardWidthProperty = cardWidthProperty;
        this.cardHeightProperty = cardHeightProperty;
        this.imageLoader = new CardImageLoader(cardWidthProperty, cardHeightProperty);
        getStyleClass().add("card-tree-cell");

        // One popup per CardTreeCell, shared across all CardGridCell instances
        // inside this tree cell. Mouse handlers are wired in CardGridCell().
        hoverLabel.setWrapText(true);
        hoverLabel.setMaxWidth(260);
        hoverLabel.setStyle(CardHoverPopup.LABEL_STYLE);

        warningsBox.setVisible(false);
        warningsBox.setManaged(false);
        warningsBox.setStyle("-fx-padding: 0 0 4 0;");

        // Wrap both labels in a styled VBox so they share one rounded border.
        hoverPopupBox.getChildren().addAll(hoverLabel, warningsBox);
        hoverPopupBox.setStyle(CardHoverPopup.POPUP_BOX_STYLE_DEFAULT);
        // Remove the border/background from the individual label now that the VBox carries it.
        hoverLabel.setStyle(
                "-fx-text-fill: white; " +
                        "-fx-font-size: 12; " +
                        "-fx-padding: 8 10 4 10;"
        );

        hoverPopup.getContent().add(hoverPopupBox);
        hoverPopup.setAutoFix(true);
        hoverPopup.setAutoHide(false);
    }

    /**
     * Registers (or replaces) the missing-artwork set for {@code group}.
     * Called by RealMainController after computing which cards in a Collection
     * are missing at least one of their alternate artworks.
     */
    public static void setMissingArtworkSetForGroup(CardsGroup group, Set<String> artworks) {
        if (group != null) CardGroupRegistry.MISSING_ARTWORK_SETS.put(group, artworks);
    }

    /**
     * Returns whether the incomplete-card marking overlay is currently active.
     */
    public static boolean isIncompleteMarkingEnabled() {
        return incompleteMarkingEnabled;
    }

    /**
     * Enables or disables the incomplete-card marking overlay for My Collection.
     */
    public static void setIncompleteMarkingEnabled(boolean enabled) {
        incompleteMarkingEnabled = enabled;
    }
    /**
     * Optional element-level filter ANDed into every FilteredList predicate.
     * Unlike {@link #activeMiddleFilter} (which operates on {@link Card}), this
     * predicate receives the individual {@link CardElement} so per-copy fields
     * such as {@code customTags} can be tested precisely.
     * {@code null} means inactive (pass all elements).
     */
    private static java.util.function.Predicate<CardElement> activeMiddleElementFilter = null;

    /**
     * Returns whether the condition/rarity overlay is currently active.
     */
    public static boolean isShowConditionRarityOverlayEnabled() {
        return showConditionRarityOverlayEnabled;
    }

    /**
     * Returns {@code true} when at least one copy of {@code card} is present in
     * the user's owned collection, using the same {@link CardMatcher#cardsMatch}
     * identity check that {@code CardGridCell} uses to decide gray-rendering in
     * the OuicheList view.
     *
     * <p>Called by {@link Controller.OuicheListController#displayCompactOuicheList}
     * to filter owned cards out of the compact view when the "Hide owned cards"
     * toggle is active.
     *
     * @param card the card to look up; must not be {@code null}
     * @return {@code true} if the card is found in the owned collection
     */
    public static boolean isCardOwnedInCollection(Card card) {
        if (card == null) {
            return false;
        }
        Model.CardsLists.OwnedCardsCollection ownedCollection = null;
        try {
            ownedCollection = Model.CardsLists.OuicheList.getMyCardsCollection();
        } catch (Throwable ignored) {
        }
        if (ownedCollection == null || ownedCollection.getOwnedCollection() == null) {
            return false;
        }
        for (Box box : ownedCollection.getOwnedCollection()) {
            if (box == null) {
                continue;
            }
            if (box.getContent() != null) {
                for (CardsGroup group : box.getContent()) {
                    if (group == null || group.getCardList() == null) {
                        continue;
                    }
                    for (CardElement cardElement : group.getCardList()) {
                        if (cardElement == null || cardElement.getCard() == null) {
                            continue;
                        }
                        if (CardMatcher.cardsMatch(cardElement.getCard(), card)) {
                            return true;
                        }
                    }
                }
            }
            if (box.getSubBoxes() != null) {
                for (Box subBox : box.getSubBoxes()) {
                    if (subBox == null || subBox.getContent() == null) {
                        continue;
                    }
                    for (CardsGroup group : subBox.getContent()) {
                        if (group == null || group.getCardList() == null) {
                            continue;
                        }
                        for (CardElement cardElement : group.getCardList()) {
                            if (cardElement == null || cardElement.getCard() == null) {
                                continue;
                            }
                            if (CardMatcher.cardsMatch(cardElement.getCard(), card)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Public accessors used by RealMainController and MenuActionHandler paths.
     */
    public static void registerCollectionCardsGroup(
            Model.CardsLists.ThemeCollection tc, CardsGroup group) {
        if (tc != null && group != null) CardGroupRegistry.COLLECTION_CARDS_GROUPS.put(tc, group);
    }

    public static CardsGroup getCollectionCardsGroup(
            Model.CardsLists.ThemeCollection tc) {
        return tc == null ? null : CardGroupRegistry.COLLECTION_CARDS_GROUPS.get(tc);
    }

    public static void registerCollectionExceptionsGroup(
            Model.CardsLists.ThemeCollection tc, CardsGroup group) {
        if (tc != null && group != null) CardGroupRegistry.COLLECTION_EXCEPTIONS_GROUPS.put(tc, group);
    }

    public static CardsGroup getCollectionExceptionsGroup(
            Model.CardsLists.ThemeCollection tc) {
        return tc == null ? null : CardGroupRegistry.COLLECTION_EXCEPTIONS_GROUPS.get(tc);
    }

    public static void registerDeckSectionGroup(
            Model.CardsLists.Deck deck, String section, CardsGroup group) {
        if (deck == null || section == null || group == null) return;
        CardGroupRegistry.DECK_SECTION_GROUPS
                .computeIfAbsent(deck, d -> new java.util.HashMap<>())
                .put(section, group);
    }

    public static CardsGroup getDeckSectionGroup(
            Model.CardsLists.Deck deck, String section) {
        if (deck == null || section == null) return null;
        java.util.Map<String, CardsGroup> map = CardGroupRegistry.DECK_SECTION_GROUPS.get(deck);
        return map == null ? null : map.get(section);
    }

    /**
     * Tracks the FilteredList wrapping each group's ObservableList so the predicate
     * can be updated without rebuilding the tree.
     */
    /**
     * Active filter predicate for the middle pane.
     * When non-null every GridView shows only cards that pass this predicate.
     * When null all cards are shown (no filter).
     */
    private static java.util.function.Predicate<Card> activeMiddleFilter = null;

    /**
     * Enables or disables the condition/rarity overlay on card grid cells.
     */
    public static void setShowConditionRarityOverlayEnabled(boolean enabled) {
        showConditionRarityOverlayEnabled = enabled;
    }
    /**
     * The StackPane wrapper inside every GridCell has Insets(5) padding on each side,
     * adding 2×5 = 10 px to both the rendered cell width and height beyond the bound
     * cardWidth / cardHeight properties.
     */

    /**
     * Returns (or creates) the ObservableList that backs the GridView for {@code group}.
     * Adding/removing through this list updates both the model and the GridView automatically.
     */
    public static javafx.collections.ObservableList<CardElement> observableListFor(CardsGroup group) {
        if (group == null) return javafx.collections.FXCollections.observableArrayList();
        return CardGroupRegistry.GROUP_OBSERVABLE_LISTS.computeIfAbsent(group, g -> {
            java.util.List<CardElement> backing = g.getCardList();
            if (backing == null) {
                backing = new java.util.ArrayList<>();
                g.setCardList(backing);
            }
            return javafx.collections.FXCollections.observableList(backing);
        });
    }

    /**
     * Recomputes the prefHeight of the GridView currently rendering {@code group}.
     * No-ops silently if the group's grid is not visible or has been GC'd.
     */
    public static void triggerHeightAdjustment(CardsGroup group) {
        if (group == null) return;
        javafx.application.Platform.runLater(() -> {
            java.lang.ref.WeakReference<GridView<CardElement>> ref = CardGroupRegistry.GROUP_GRID_VIEWS.get(group);
            if (ref == null) return;
            GridView<CardElement> grid = ref.get();
            if (grid == null) {
                CardGroupRegistry.GROUP_GRID_VIEWS.remove(group);
                return;
            }
            int numItems = group.getCardList() == null ? 0 : group.getCardList().size();
            GridViewSizer.adjustGridViewHeight(grid, numItems);
        });
    }

    /**
     * Creates a small styled action button for tree-cell title rows.
     */
    private static Button makeInlineActionButton(String text) {
        Button btn = new Button(text);
        btn.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-text-fill: #cdfc04;" +
                        "-fx-border-color: #cdfc04;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 4;" +
                        "-fx-background-radius: 4;" +
                        "-fx-font-size: 11px;" +
                        "-fx-padding: 2 6 2 6;" +
                        "-fx-cursor: hand;"
        );
        return btn;
    }

    /**
     * Helper: determine whether the currently selected Tab is the first tab in the TabPane ancestor.
     * Uses the TreeView ancestor to find a TabPane and checks the selected index.
     * Returns true only when the selected tab index is 0.
     */
    private boolean isFirstTabSelected() {
        try {
            Node node = getTreeView();
            while (node != null && !(node instanceof TabPane)) {
                node = node.getParent();
            }
            if (node instanceof TabPane) {
                TabPane tp = (TabPane) node;
                SingleSelectionModel<Tab> sm = tp.getSelectionModel();
                if (sm != null) {
                    int idx = sm.getSelectedIndex();
                    return idx == 0;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }


    /**
     * Removes the leading "* " dirty marker from a tab label if present.
     */
    private static String stripDirtyPrefix(String tabText) {
        if (tabText == null) return "";
        String trimmed = tabText.trim();
        return trimmed.startsWith("* ") ? trimmed.substring(2).trim() : trimmed;
    }

    /**
     * Finds the dirty-markable owner (ThemeCollection or Deck) for a CardsGroup.
     * <p>
     * Rules matching the save-file structure:
     * ThemeCollection.cardsList / exceptionsToNotAdd  → ThemeCollection
     * Deck.mainDeck / extraDeck / sideDeck            → Deck  (NOT the parent collection)
     * <p>
     * Uses the typed registries as the primary source (O(1)), then falls back to
     * raw-list identity comparison for any group not in the registry.
     */
    public static Object findOwnerForGroup(CardsGroup group) {
        if (group == null) return null;
        Model.CardsLists.DecksAndCollectionsList dac =
                Controller.UserInterfaceFunctions.getDecksList();
        if (dac == null) return null;

        // ── 1. Registry lookup ────────────────────────────────────────────────
        if (dac.getCollections() != null) {
            for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                if (tc == null) continue;
                // Collection-owned groups → mark the collection
                if (group == getCollectionCardsGroup(tc)
                        || group == getCollectionExceptionsGroup(tc)) return tc;
                // Deck-section groups → mark the deck only
                if (tc.getLinkedDecks() != null) {
                    for (java.util.List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                        if (unit == null) continue;
                        for (Model.CardsLists.Deck d : unit) {
                            if (d == null) continue;
                            java.util.Map<String, CardsGroup> secs = CardGroupRegistry.DECK_SECTION_GROUPS.get(d);
                            if (secs != null && secs.containsValue(group)) return d;
                        }
                    }
                }
            }
        }
        if (dac.getDecks() != null) {
            for (Model.CardsLists.Deck d : dac.getDecks()) {
                if (d == null) continue;
                java.util.Map<String, CardsGroup> secs = CardGroupRegistry.DECK_SECTION_GROUPS.get(d);
                if (secs != null && secs.containsValue(group)) return d;
            }
        }

        // ── 2. Raw-list identity fallback ─────────────────────────────────────
        // For groups created outside the registries. Compares the CardsGroup's
        // backing List (not the ObservableList wrapper) against model lists.
        java.util.List<Model.CardsLists.CardElement> rawList = group.getCardList();
        if (rawList != null) {
            if (dac.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                    if (tc == null) continue;
                    if (rawList == tc.getCardsList()
                            || rawList == tc.getExceptionsToNotAdd()) return tc;
                    if (tc.getLinkedDecks() != null) {
                        for (java.util.List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Model.CardsLists.Deck d : unit) {
                                if (d == null) continue;
                                if (rawList == d.getMainDeck()
                                        || rawList == d.getExtraDeck()
                                        || rawList == d.getSideDeck()) return d;
                            }
                        }
                    }
                }
            }
            if (dac.getDecks() != null) {
                for (Model.CardsLists.Deck d : dac.getDecks()) {
                    if (d == null) continue;
                    if (rawList == d.getMainDeck()
                            || rawList == d.getExtraDeck()
                            || rawList == d.getSideDeck()) return d;
                }
            }
        }
        return null;
    }

    /**
     * Marks the owner of {@code group} dirty (My Collection, or the specific
     * Deck / ThemeCollection in D&C) and triggers the appropriate view refresh.
     */
    static void markDirtyAndRefreshForGroup(CardsGroup group) {
        if (group == null) return;
        Object dacOwner = findOwnerForGroup(group);
        if (dacOwner != null) {
            Controller.UserInterfaceFunctions.markDirty(dacOwner);
            // Any card addition, removal or move in any D&C group can affect the
            // archetype missing-sets inside Collections, so always force a full tree
            // rebuild so that archetype-card glow states recompute correctly.
            Controller.UserInterfaceFunctions.setPendingDecksFullRebuild();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
        } else {
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshOwnedCollectionView();
        }
    }

    static void showRenamePopup(Label labelAnchor, String seedName,
                                java.util.function.Consumer<String> onConfirm) {
        showRenamePopup(labelAnchor, seedName, 0, onConfirm);
    }

    /**
     * Shows a floating rename {@link Popup} anchored below {@code anchor}.
     *
     * <p>The popup is completely independent of the {@code TreeCell} lifecycle:
     * {@code updateItem} rebuilds cannot touch it, so there is no risk of the
     * editor being destroyed by a selection change or a view refresh triggered
     * between {@code MOUSE_PRESSED} and {@code MOUSE_RELEASED}.</p>
     *
     * <p>The popup is dismissed on ✓ / Enter (confirm), ✗ / Escape (cancel), or
     * when it auto-hides because the user clicks outside it.</p>
     *
     * @param seedName   the current (display) name to pre-fill the text field
     * @param onConfirm  called with the trimmed non-empty new name on confirm
     */

    static void showRenamePopup(Label labelAnchor, String seedName, double extraOffsetY,
                                java.util.function.Consumer<String> onConfirm) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);

        String seed = seedName == null ? "" : seedName.trim();
        if (seed.startsWith("* ")) seed = seed.substring(2).trim();

        // Match the text field width to the label so the popup feels "in place".
        double labelWidth = labelAnchor.getWidth();
        double tfWidth = Math.max(labelWidth, 180);

        TextField tf = new TextField(seed);
        tf.setPrefWidth(tfWidth);
        tf.setStyle(
                "-fx-background-color: #1a0428;" +
                        "-fx-text-fill: #cdfc04;" +
                        "-fx-border-color: #cdfc04;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 4 0 0 4;" +
                        "-fx-background-radius: 4 0 0 4;" +
                        "-fx-font-size: 13px;" +
                        "-fx-padding: 4 8 4 8;"
        );

        Button confirmBtn = new Button("✓");
        confirmBtn.setStyle(
                "-fx-background-color: #1a0428;" +
                        "-fx-text-fill: #00cc44;" +
                        "-fx-border-color: #00cc44;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 0;" +
                        "-fx-background-radius: 0;" +
                        "-fx-font-size: 13px;" +
                        "-fx-padding: 4 8 4 8;" +
                        "-fx-cursor: hand;"
        );
        Button cancelBtn = new Button("✗");
        cancelBtn.setStyle(
                "-fx-background-color: #1a0428;" +
                        "-fx-text-fill: #ff4040;" +
                        "-fx-border-color: #ff4040;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 0 4 4 0;" +
                        "-fx-background-radius: 0 4 4 0;" +
                        "-fx-font-size: 13px;" +
                        "-fx-padding: 4 8 4 8;" +
                        "-fx-cursor: hand;"
        );

        HBox row = new HBox(0, tf, confirmBtn, cancelBtn);
        row.setStyle(
                "-fx-background-color: #1a0428;" +
                        "-fx-border-color: #cdfc04;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 4;" +
                        "-fx-background-radius: 4;" +
                        "-fx-padding: 0;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.7), 8, 0, 0, 2);"
        );

        Runnable doConfirm = () -> {
            String newName = tf.getText() == null ? "" : tf.getText().trim();
            if (!newName.isEmpty()) {
                popup.hide();
                onConfirm.accept(newName);
            }
        };
        Runnable doCancel = () -> popup.hide();

        tf.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                e.consume();
                doConfirm.run();
            } else if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                e.consume();
                doCancel.run();
            }
        });
        confirmBtn.setOnAction(e -> doConfirm.run());
        cancelBtn.setOnAction(e -> doCancel.run());

        popup.getContent().add(row);

        // Align the popup's top-left with the label's top-left so it sits
        // directly on top of the title it is replacing.
        Bounds screenBounds = labelAnchor.localToScreen(labelAnchor.getBoundsInLocal());
        if (screenBounds != null) {
            popup.show(labelAnchor.getScene().getWindow(),
                    screenBounds.getMinX() - 20,
                    screenBounds.getMinY() - 15 + extraOffsetY);
        } else {
            popup.show(labelAnchor.getScene().getWindow(), 100, 100);
        }

        tf.requestFocus();
        tf.selectAll();
    }

    // ── Inline-rename helpers ─────────────────────────────────────────────────

    /**
     * Shows a floating rename {@link Popup} overlaid on top of {@code labelAnchor}.
     *
     * <p>The popup is completely independent of the {@code TreeCell} lifecycle:
     * {@code updateItem} rebuilds cannot touch it, so there is no risk of the
     * editor being destroyed by a selection change or a view refresh triggered
     * between {@code MOUSE_PRESSED} and {@code MOUSE_RELEASED}.</p>
     *
     * @param labelAnchor the label whose position and width the popup should match
     * @param seedName    the current display name to pre-fill the text field
     * @param onConfirm   called with the trimmed non-empty new name on confirm
     */

    /**
     * Inserts {@code newElements} into {@code targetList} at {@code insertionIndex}.
     * If {@code sourceElements} is non-null and non-empty, each source element is
     * first removed from its current group (MOVE semantics); otherwise new
     * {@link CardElement} wrappers are created from the cards (ADD semantics).
     *
     * <p>Dirty marking and view refresh are handled by the caller.
     */
    public static java.util.Set<CardsGroup> dropInsertIntoGroup(
            CardsGroup targetGroup,
            int insertionIndex,
            java.util.List<Model.CardsLists.CardElement> sourceElements,
            java.util.List<Model.CardsLists.Card> sourceCards) {

        java.util.Set<CardsGroup> modifiedSourceGroups = new java.util.LinkedHashSet<>();

        // ── Deck-compatibility redirect ────────────────────────────────────────
        // If the target is a main/extra deck section and the payload is incompatible,
        // redirect to the sibling section of the same Deck.
        // CRITICAL: when redirected, insertionIndex belongs to the ORIGINAL (wrong)
        // list — reset to MAX_VALUE so it clamps to end-of-list on the new target.
        CardsGroup originalGroup = targetGroup;
        targetGroup = resolveCompatibleTargetGroup(targetGroup, sourceElements, sourceCards);
        if (targetGroup != originalGroup) {
            insertionIndex = Integer.MAX_VALUE; // always append to the redirect target
        }
        // ──────────────────────────────────────────────────────────────────────

        javafx.collections.ObservableList<CardElement> targetList = observableListFor(targetGroup);
        int idx = Math.max(0, Math.min(insertionIndex, targetList.size()));

        if (sourceElements != null && !sourceElements.isEmpty()) {
            // MOVE: remove from sources first (may be in different groups)
            for (CardElement ce : sourceElements) {
                for (java.util.Map.Entry<CardsGroup,
                        javafx.collections.ObservableList<CardElement>> entry
                        : CardGroupRegistry.GROUP_OBSERVABLE_LISTS.entrySet()) {
                    if (entry.getValue().remove(ce)) {
                        CardsGroup srcGroup = entry.getKey();
                        triggerHeightAdjustment(srcGroup);
                        modifiedSourceGroups.add(srcGroup);
                        break;
                    }
                }
            }
            // Re-compute index in case removals shifted it
            idx = Math.min(idx, targetList.size());
            for (int i = 0; i < sourceElements.size(); i++) {
                int pos = Math.min(idx + i, targetList.size());
                targetList.add(pos, sourceElements.get(i));
            }
        } else if (sourceCards != null && !sourceCards.isEmpty()) {
            // ADD: create new elements
            java.util.List<CardElement> newlyAddedElements = new java.util.ArrayList<>();
            for (int i = 0; i < sourceCards.size(); i++) {
                Model.CardsLists.Card card = sourceCards.get(i);
                if (card == null) continue;
                int pos = Math.min(idx + i, targetList.size());
                CardElement newElement = new CardElement(card);
                targetList.add(pos, newElement);
                newlyAddedElements.add(newElement);
            }
            CardGroupRegistry.notifyOuicheListOfGroupAdditions(targetGroup, newlyAddedElements);
        }
        triggerHeightAdjustment(targetGroup);
        return modifiedSourceGroups;
    }

    /**
     * Returns the Deck that owns {@code group} via the registered section map,
     * or null if not found.
     */
    public static Model.CardsLists.Deck findDeckOwnerForGroup(CardsGroup group) {
        if (group == null) return null;
        for (java.util.Map.Entry<Model.CardsLists.Deck,
                java.util.Map<String, CardsGroup>> entry : CardGroupRegistry.DECK_SECTION_GROUPS.entrySet()) {
            if (entry.getValue() != null && entry.getValue().containsValue(group))
                return entry.getKey();
        }
        return null;
    }

    public static CardsGroup findGroupForCardElement(CardElement targetElement) {
        if (targetElement == null) return null;
        for (java.util.Map.Entry<CardsGroup,
                javafx.collections.ObservableList<CardElement>> entry
                : CardGroupRegistry.GROUP_OBSERVABLE_LISTS.entrySet()) {
            if (entry.getValue().contains(targetElement)) return entry.getKey();
        }
        return null;
    }

    /**
     * Calls refresh() on every GridView currently registered in CardGroupRegistry.GROUP_GRID_VIEWS.
     * This propagates selection-state changes into nested GridViews that a plain
     * TreeView.refresh() call does not reach.
     */
    public static void refreshAllGridViews() {
        Platform.runLater(() -> {
            for (java.lang.ref.WeakReference<GridView<CardElement>> ref : CardGroupRegistry.GROUP_GRID_VIEWS.values()) {
                GridView<CardElement> grid = ref.get();
                if (grid == null) {
                    continue;
                }
                javafx.collections.ObservableList<CardElement> items = grid.getItems();
                if (items == null) {
                    continue;
                }
                // ControlsFX's GridView has no public refresh(); toggling the items
                // property forces its skin to rebuild every cell so external state
                // (e.g. selection highlighting) repaints. An empty list is used as
                // the transient value instead of null, because the skin calls
                // getItems().size() synchronously during the property change and
                // has no null guard — passing null here throws a NullPointerException.
                grid.setItems(javafx.collections.FXCollections.emptyObservableList());
                grid.setItems(items);
            }
        });
    }

    /**
     * Sets (or clears) the active filter for the middle pane.
     * Pass {@code null} to remove all filtering and restore the full card lists.
     * The change is applied immediately to every live GridView via their FilteredList.
     *
     * @param predicate a {@link java.util.function.Predicate} on {@link Card}, or {@code null}
     */
    public static void setMiddleFilter(java.util.function.Predicate<Card> predicate) {
        activeMiddleFilter = predicate;
        CardGroupRegistry.activeMiddleFilter = predicate;
        Platform.runLater(CardTreeCell::applyFilterToAllGroups);
    }

    /**
     * Registers (or clears) a {@link CardElement}-level filter for the middle pane.
     *
     * <p>This predicate is ANDed with the card-level {@link #activeMiddleFilter} inside
     * {@link #buildCombinedPredicate}, allowing per-copy fields (e.g. {@code customTags})
     * to be tested on individual elements rather than on the shared {@link Card} object.
     *
     * @param predicate a predicate on {@link CardElement}, or {@code null} to remove the filter
     */
    public static void setMiddleElementFilter(java.util.function.Predicate<CardElement> predicate) {
        activeMiddleElementFilter = predicate;
        Platform.runLater(CardTreeCell::applyFilterToAllGroups);
    }

    public static void shutdownImageLoadingExecutor() {
        CardImageLoader.shutdown();
    }

    /**
     * Pushes the current {@link #activeMiddleFilter} to every live FilteredList and
     * recalculates each GridView's preferred height accordingly.
     */
    private static void applyFilterToAllGroups() {
        // Iterate over a snapshot to avoid ConcurrentModificationException
        List<Map.Entry<CardsGroup,
                java.lang.ref.WeakReference<javafx.collections.transformation.FilteredList<CardElement>>>>
                snapshot = new ArrayList<>(CardGroupRegistry.GROUP_FILTERED_LISTS.entrySet());

        for (Map.Entry<CardsGroup,
                java.lang.ref.WeakReference<javafx.collections.transformation.FilteredList<CardElement>>> entry
                : snapshot) {
            CardsGroup group = entry.getKey();
            javafx.collections.transformation.FilteredList<CardElement> fl = entry.getValue().get();
            if (fl == null) {
                CardGroupRegistry.GROUP_FILTERED_LISTS.remove(group);
                continue;
            }

            fl.setPredicate(buildCombinedPredicate(activeMiddleFilter));

            // Recompute height using the post-filter count
            java.lang.ref.WeakReference<GridView<CardElement>> gridRef = CardGroupRegistry.GROUP_GRID_VIEWS.get(group);
            GridView<CardElement> grid = gridRef != null ? gridRef.get() : null;
            GridViewSizer.adjustGridViewHeight(grid, fl.size());
        }
    }

    /**
     * Builds a combined {@link java.util.function.Predicate} for the middle-pane
     * {@link javafx.collections.transformation.FilteredList} of a cards group.
     *
     * <p>The predicate ANDs two independent concerns:
     * <ol>
     *   <li>The active middle-pane card filter (may be {@code null} = pass all).</li>
     *   <li>When "Hide owned cards" is active in the OuicheList, {@link CardElement}s
     *       whose {@link CardElement#getOwned()} is {@code true} are excluded so they
     *       occupy zero space in the {@link GridView} — not just rendered invisible.</li>
     * </ol>
     *
     * @param middleFilter the currently active card filter, or {@code null} for none
     * @return a predicate to pass to
     * {@link javafx.collections.transformation.FilteredList#setPredicate},
     * or {@code null} when neither filter is active (show everything)
     */
    private static java.util.function.Predicate<CardElement> buildCombinedPredicate(
            java.util.function.Predicate<Card> middleFilter) {

        boolean hideOwned = Controller.OuicheListController.isHideOwnedCardsEnabled();

        if (!hideOwned && middleFilter == null && activeMiddleElementFilter == null) {
            return null; // fastest path — no filtering at all
        }

        final java.util.function.Predicate<Card> capturedFilter = middleFilter;
        final java.util.function.Predicate<CardElement> capturedElementFilter = activeMiddleElementFilter;

        return ce -> {
            if (ce == null) {
                return false;
            }
            if (hideOwned && OwnershipStatus.OWNED.equals(ce.getOwnershipStatus())) {
                return false;
            }
            if (capturedFilter != null) {
                if (ce.getCard() == null || !capturedFilter.test(ce.getCard())) {
                    return false;
                }
            }
            if (capturedElementFilter != null) {
                return capturedElementFilter.test(ce);
            }
            return true;
        };
    }

    /**
     * Re-applies the combined predicate (hide-owned + active middle filter) to every
     * live {@link javafx.collections.transformation.FilteredList} and recalculates
     * each GridView's preferred height.
     *
     * <p>Called by {@link Controller.OuicheListController} after toggling the
     * "Hide owned cards" flag so that owned-card slots are removed or restored
     * without rebuilding the whole tree.
     */
    public static void applyHideOwnedToAllGroups() {
        Platform.runLater(CardTreeCell::applyFilterToAllGroups);
    }

    private void updateCustomTriangle(boolean isExpanded) {
        String triangle = isExpanded ? "\u25BC" : "\u25B6";
        if (customTriangleLabel != null) customTriangleLabel.setText(triangle);
    }

    /**
     * Helper: determine whether the currently selected Tab is the My Collection tab.
     * Uses the TreeView ancestor to find a TabPane and checks the selected Tab text.
     * Returns true only when the selected tab's text equals "My Collection" (case-insensitive).
     */
    boolean isMyCollectionTabSelected() {
        try {
            Node node = getTreeView();
            while (node != null && !(node instanceof TabPane))
                node = node.getParent();
            if (node instanceof TabPane) {
                Tab sel = ((TabPane) node).getSelectionModel().getSelectedItem();
                if (sel != null) {
                    String cleanText = stripDirtyPrefix(sel.getText());
                    return cleanText.equalsIgnoreCase("My Collection");
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }



    /**
     * Helper: determine whether the currently selected Tab is the OuicheList tab.
     * Uses the TreeView ancestor to find a TabPane and checks the selected Tab text.
     * Returns true only when the selected tab's text equals "OuicheList" (case-insensitive).
     */
    boolean isOuicheListTabSelected() {
        try {
            Node node = getTreeView();
            while (node != null && !(node instanceof TabPane))
                node = node.getParent();
            if (node instanceof TabPane) {
                Tab sel = ((TabPane) node).getSelectionModel().getSelectedItem();
                if (sel != null) {
                    String cleanText = stripDirtyPrefix(sel.getText());
                    return cleanText.equalsIgnoreCase("OuicheList");
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Return a list of MenuItems representing sorting propositions for the given card.
     * Currently returns an empty list (no propositions). Later this method will compute
     * and return one or several actionable MenuItems.
     * <p>
     * Keep this method small and side-effect free; actions attached to returned MenuItems
     * can call into controller code to perform operations.
     */
    private List<MenuItem> getSortingPropositionsFor(Card card) {
        // Placeholder implementation: return empty list for now.
        // Later: compute proposals and return MenuItems with setOnAction handlers.
        return Collections.emptyList();
    }

    boolean isDecksAndCollectionsTabSelected() {
        try {
            Node node = getTreeView();
            while (node != null && !(node instanceof TabPane))
                node = node.getParent();
            if (node instanceof TabPane) {
                Tab sel = ((TabPane) node).getSelectionModel().getSelectedItem();
                if (sel != null) {
                    String cleanText = stripDirtyPrefix(sel.getText());
                    return cleanText.equalsIgnoreCase("Decks and Collections")
                            || cleanText.equalsIgnoreCase("Decks & Collections");
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    // ── OuicheList advancement stats helpers ──────────────────────────────────

    /**
     * Computes advancement stats for the given card list and returns a formatted
     * display string: {@code (missing/total - missingPrice€/totalPrice€ - pct%)}.
     * Returns an empty string if the list is null or empty.
     *
     * @param cardsList The list of card elements with ownership status.
     * @return The formatted advancement string, or {@code ""} if unavailable.
     */
    private String buildAdvancementLabel(List<CardElement> cardsList) {
        if (cardsList == null || cardsList.isEmpty()) {
            return "";
        }
        int totalCount = 0;
        int missingCount = 0;
        float missingPriceSum = 0f;
        float totalPriceSum = 0f;

        for (CardElement cardElement : cardsList) {
            if (cardElement == null || cardElement.getCard() == null) {
                continue;
            }
            totalCount++;
            boolean isMissing = cardElement.getOwnershipStatus() != OwnershipStatus.OWNED;
            float cardPrice = 0f;
            String rawPrice = cardElement.getCard().getPrice();
            if (rawPrice != null && !rawPrice.isEmpty()) {
                try {
                    cardPrice = Float.parseFloat(rawPrice);
                } catch (NumberFormatException numberFormatException) {
                    logger.warn("Could not parse card price: {}", rawPrice);
                }
            }
            totalPriceSum += cardPrice;
            if (isMissing) {
                missingCount++;
                missingPriceSum += cardPrice;
            }
        }

        if (totalCount == 0) {
            return "";
        }
        int ownedCount = totalCount - missingCount;
        String ownedPct = String.format("%.1f", (ownedCount * 100f) / totalCount);
        String missingPriceFmt = String.format("%.2f", missingPriceSum);
        String totalPriceFmt = String.format("%.2f", totalPriceSum);
        return "(" + missingCount + "/" + totalCount
                + " - " + missingPriceFmt + "€/" + totalPriceFmt + "€"
                + " - " + ownedPct + "%)";
    }

    /**
     * Collects all {@link CardElement}s from a single {@link ThemeCollection}:
     * all cards in every linked deck followed by the collection's own cardsList.
     *
     * @param collection The ThemeCollection to collect cards from.
     * @return A flat list of all CardElements in the collection.
     */
    private List<CardElement> collectCardsFromCollection(
            Model.CardsLists.ThemeCollection collection) {
        List<CardElement> allCards = new ArrayList<>();
        if (collection.getLinkedDecks() != null) {
            for (List<Model.CardsLists.Deck> deckGroup : collection.getLinkedDecks()) {
                if (deckGroup == null) {
                    continue;
                }
                for (Model.CardsLists.Deck deck : deckGroup) {
                    if (deck != null) {
                        allCards.addAll(deck.toList());
                    }
                }
            }
        }
        if (collection.getCardsList() != null) {
            allCards.addAll(collection.getCardsList());
        }
        return allCards;
    }

    /**
     * Builds an {@link HBox} title row with the item name on the left and the
     * advancement stats label pushed to the right via a flexible spring.
     * Used exclusively in the OuicheList detailed-mode tree.
     *
     * @param name  The display name (collection, deck, or section name).
     * @param stats The formatted stats string from {@link #buildAdvancementLabel}.
     * @return An HBox ready to be passed to {@link #setGraphic(Node)}.
     */
    private HBox buildOuicheListTitleRow(String name, String stats) {
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("tree-item-label");

        HBox spring = new HBox();
        HBox.setHgrow(spring, Priority.ALWAYS);

        Label statsLabel = new Label(stats);
        statsLabel.setStyle(
                "-fx-text-fill: #aaaaaa;"
                        + "-fx-font-size: 11px;"
                        + "-fx-padding: 0 4 0 0;");

        HBox titleRow = new HBox(6, nameLabel, spring, statsLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        return titleRow;
    }

    // ---------- Helpers for building proposals ----------

    @Override
    protected void updateItem(String itemName, boolean empty) {
        super.updateItem(itemName, empty);
        setText(null);
        setGraphic(null);
        getStyleClass().setAll("card-tree-cell");

        if (empty || itemName == null) {
            return;
        } else {
            TreeItem<String> treeItem = getTreeItem();
            if (treeItem instanceof DataTreeItem) {
                Object dataObject = ((DataTreeItem<?>) treeItem).getData();

                // Two possible data shapes:
                // 1) CardsGroup (normal)
                // 2) Map<String,Object> with "group" and "missing" keys (archetype group)
                CardsGroup group = null;
                Set<String> missingForThisGroup = null;

                if (dataObject instanceof CardsGroup) {
                    group = (CardsGroup) dataObject;
                } else if (dataObject instanceof Map) {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>) dataObject;
                        Object groupValue = map.get("group");
                        Object missingValue = map.get("missing");
                        if (groupValue instanceof CardsGroup) {
                            group = (CardsGroup) groupValue;
                        }
                        if (missingValue instanceof Set) {
                            @SuppressWarnings("unchecked")
                            Set<String> castMissingSet = (Set<String>) missingValue;
                            missingForThisGroup = castMissingSet;
                        } else if (missingValue instanceof Collection) {
                            Set<String> collectedSet = new HashSet<>();
                            for (Object entry : (Collection<?>) missingValue) {
                                if (entry != null) {
                                    collectedSet.add(entry.toString());
                                }
                            }
                            missingForThisGroup = collectedSet;
                        }
                    } catch (Exception e) {
                        logger.debug("Failed to extract archetype map data", e);
                    }
                }

                if (group != null) {
                    createCardsGroupCell(itemName, group, missingForThisGroup);
                } else if (dataObject instanceof CardElement) {
                    buildCardElementCell((CardElement) dataObject);
                } else if (dataObject instanceof String && dataObject.equals("ROOT")) {
                    Label label = new Label(itemName);
                    label.getStyleClass().add("tree-root-label");
                    setGraphic(label);

                } else if (dataObject instanceof Model.CardsLists.ThemeCollection) {
                    // ── Collection header row ──────────────────────────────────
                    // Shown in the Decks & Collections and OuicheList tree.
                    // Right-click: "Add Deck" + "Add Archetype"
                    buildCollectionHeaderCell(itemName, (Model.CardsLists.ThemeCollection) dataObject);

                } else if (dataObject instanceof Model.CardsLists.Box) {
                    // ── Box header — My Collection tab ────────────────────────
                    buildBoxHeaderCell(itemName, (Model.CardsLists.Box) dataObject);

                } else if (dataObject instanceof Model.CardsLists.Deck) {
                    // ── Deck header — Decks & Collections tab ─────────────────
                    buildDeckHeaderCell(itemName, (Model.CardsLists.Deck) dataObject);

                } else if ("ARCHETYPES_SECTION".equals(dataObject)) {
                    // ── "Archetypes" section header ────────────────────────────
                    // Right-click: "Add" (add a new archetype to this collection)
                    buildArchetypesSectionHeaderCell(itemName);

                } else {
                    // Default: plain label (section headers like "Decks", deck names, etc.)
                    Label label = new Label(itemName);
                    label.getStyleClass().add("tree-item-label");
                    setGraphic(label);
                }
            } else {
                Label label = new Label(itemName);
                label.getStyleClass().add("tree-item-label");
                setGraphic(label);
            }
        }
    }

    /**
     * Builds a leaf card-tile cell for a bare {@link CardElement} in the tree
     * (used for flat, non-grouped listings). Applies the same "needs sorting" /
     * "upgrade candidate" / "degraded copy" white or orange glow logic as the
     * mosaic grid cells, based on the missing-set / element-name metadata stored
     * in {@code cardGridView}'s userData by {@link #createCardsGroupCell}.
     */
    private void buildCardElementCell(CardElement cardElement) {
        HBox cardBox = new HBox(5);
        cardBox.setAlignment(Pos.CENTER_LEFT);

        ImageView imageView = new ImageView();
        imageView.setFitWidth(cardWidthProperty.get());
        imageView.setFitHeight(cardHeightProperty.get());
        imageView.setPreserveRatio(true);

        imageLoader.loadCardImage(cardElement, imageView);

        Label nameLabel = new Label(cardElement.getCard() == null ? "" : cardElement.getCard().getName_EN());
        nameLabel.setTextFill(javafx.scene.paint.Color.WHITE);

        cardBox.getChildren().addAll(imageView, nameLabel);

        // ----------------------------
        // Determine whether to apply the "unsorted" glow
        // ----------------------------
        boolean needsSorting = false;
        Set<String> missingSet = null;
        String elementNameFromUserData = null;
        try {
            // Retrieve userData from the GridView that was stored in createCardsGroupCell.
            Object userData = null;
            try {
                if (this.cardGridView != null) {
                    userData = this.cardGridView.getUserData();
                }
            } catch (Exception ignored) {
                userData = null;
            }

            if (userData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) userData;
                Object missingSetValue = map.get("missingSet");
                if (missingSetValue instanceof Set) {
                    @SuppressWarnings("unchecked")
                    Set<String> castMissingSet = (Set<String>) missingSetValue;
                    missingSet = castMissingSet;
                } else if (missingSetValue instanceof Collection) {
                    Set<String> collectedSet = new HashSet<>();
                    for (Object entry : (Collection<?>) missingSetValue) {
                        if (entry != null) {
                            collectedSet.add(entry.toString());
                        }
                    }
                    missingSet = collectedSet;
                }
                Object elementNameValue = map.get("elementName");
                if (elementNameValue instanceof String) {
                    elementNameFromUserData = (String) elementNameValue;
                }
            } else if (userData instanceof Set) {
                @SuppressWarnings("unchecked")
                Set<String> castMissingSet = (Set<String>) userData;
                missingSet = castMissingSet;
            } else if (userData instanceof String) {
                elementNameFromUserData = (String) userData;
            }

            // If a missing set exists and indicates this card is missing, use that (archetype glow)
            if (missingSet != null && !missingSet.isEmpty()) {
                String konamiId = cardElement.getCard() == null ? null : cardElement.getCard().getKonamiId();
                String passCode = cardElement.getCard() == null ? null : cardElement.getCard().getPassCode();
                boolean missing = false;
                if (konamiId != null && missingSet.contains(konamiId)) {
                    missing = true;
                }
                if (!missing && passCode != null && missingSet.contains(passCode)) {
                    missing = true;
                }
                // fallback to legacy global set if needed (keeps previous behavior)
                if (!missing && (missingSet.isEmpty())) {
                    missing = CardGroupRegistry.LEGACY_GLOBAL_MISSING_SET.contains(konamiId)
                            || CardGroupRegistry.LEGACY_GLOBAL_MISSING_SET.contains(passCode);
                }
                needsSorting = missing;
            } else {
                // 2) No archetype missing-set -> sorting/upgrade check.
                // My Collection: check if needs sorting, then if upgrade.
                // D&C: already handled by isDegraded block below.
                String elementName = elementNameFromUserData;

                if (elementName != null && !elementName.trim().isEmpty() && isMyCollectionTabSelected()) {
                    try {
                        boolean genuinelyNeeded = Controller.CardSortingRules
                                .computeCardNeedsSorting(cardElement.getCard(), elementName);
                        if (genuinelyNeeded) {
                            needsSorting = true;
                        } else {
                            needsSorting = Controller.CardSortingRules
                                    .computeCardNeedsSortingWithUpgrade(cardElement, elementName);
                        }
                    } catch (Throwable t) {
                        needsSorting = false;
                    }
                } else {
                    needsSorting = false;
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to compute/apply archetype/sorting decision", e);
            needsSorting = false;
        }

        // Degraded-in-deck check: orange glow when a better owned copy exists.
        // Only run on D&C tab (not My Collection) and only when not already glowing.
        boolean isDegraded = false;
        if (!needsSorting && elementNameFromUserData != null && !elementNameFromUserData.trim().isEmpty()
                && isDecksAndCollectionsTabSelected()) {
            try {
                isDegraded = Controller.CardQualityService
                        .isDegradedCopyInDeckOrCollection(cardElement, elementNameFromUserData);
            } catch (Throwable ignored) {
            }
        }

        // Apply glow if needed to the cardBox (keeps other visuals intact)
        if (needsSorting || isDegraded) {
            String glowColor = isDegraded ? "#EB9E34" : "#ffffff";
            double outerAlpha = isDegraded ? 0.35 : 0.22;
            DropShadow innerGlow = new DropShadow();
            innerGlow.setColor(javafx.scene.paint.Color.web(glowColor, 1.0));
            innerGlow.setOffsetX(0);
            innerGlow.setOffsetY(0);
            innerGlow.setRadius(4);
            innerGlow.setSpread(0.9);

            DropShadow outerGlow = new DropShadow();
            outerGlow.setColor(javafx.scene.paint.Color.web(glowColor, outerAlpha));
            outerGlow.setOffsetX(0);
            outerGlow.setOffsetY(0);
            outerGlow.setRadius(14);
            outerGlow.setSpread(0.12);

            outerGlow.setInput(innerGlow);
            cardBox.setEffect(outerGlow);
        } else {
            cardBox.setEffect(null);
        }

        setGraphic(cardBox);
    }

    /**
     * Builds the ThemeCollection header row shown in the Decks &amp; Collections and
     * OuicheList tree: plain label everywhere else, but on Decks &amp; Collections it
     * gets inline Rename/Save buttons and an "Add Deck" / "Add archetype" context
     * menu, and on OuicheList it shows the advancement stats row instead.
     */
    private void buildCollectionHeaderCell(String itemName, Model.CardsLists.ThemeCollection themeCollection) {
        Label label = new Label(itemName);
        label.getStyleClass().add("tree-item-label");

        if (isDecksAndCollectionsTabSelected()) {
            HBox titleRow = new HBox(6, label);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            HBox spring = new HBox();
            HBox.setHgrow(spring, Priority.ALWAYS);
            Button renameBtn = makeInlineActionButton("✎ Rename");
            Button saveBtn = makeInlineActionButton("💾 Save");
            titleRow.getChildren().addAll(spring, renameBtn, saveBtn);

            renameBtn.setOnAction(e -> showRenamePopup(label, label.getText(),
                    newName -> {
                        themeCollection.setName(newName);
                        label.setText(newName);
                        UserInterfaceFunctions.markDirty(themeCollection);
                        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                        UserInterfaceFunctions.refreshDecksAndCollectionsView();
                    }));

            saveBtn.setOnAction(e -> {
                try {
                    UserInterfaceFunctions.saveSingleDeckOrCollection(themeCollection);
                } catch (Exception ex) {
                    logger.error("Failed to save collection '{}'", themeCollection.getName(), ex);
                }
            });

            ContextMenu collectionContextMenu = NavigationContextMenuBuilder.styledContextMenu();
            collectionContextMenu.getItems().addAll(
                    NavigationContextMenuBuilder.makeItem("Add Deck"),
                    NavigationContextMenuBuilder.makeItem("Add archetype")
            );
            titleRow.setOnContextMenuRequested(e -> {
                collectionContextMenu.show(titleRow, e.getScreenX(), e.getScreenY());
                e.consume();
            });
            setGraphic(titleRow);
        } else if (isOuicheListTabSelected()) {
            List<CardElement> collectionCards = collectCardsFromCollection(themeCollection);
            String stats = buildAdvancementLabel(collectionCards);
            setGraphic(buildOuicheListTitleRow(itemName, stats));
        } else {
            setGraphic(label);
        }
    }

    /**
     * Builds the Box header row shown in the My Collection tree: plain label
     * everywhere else, but on My Collection it gets an inline Rename button.
     */
    private void buildBoxHeaderCell(String itemName, Model.CardsLists.Box box) {
        Label label = new Label(itemName);
        label.getStyleClass().add("tree-item-label");
        if (isMyCollectionTabSelected()) {
            HBox titleRow = new HBox(6, label);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            HBox spring = new HBox();
            HBox.setHgrow(spring, Priority.ALWAYS);
            Button renameBtn = makeInlineActionButton("✎ Rename");
            titleRow.getChildren().addAll(spring, renameBtn);
            renameBtn.setOnAction(e -> showRenamePopup(label, label.getText(),
                    newName -> {
                        String raw = box.getName() == null ? "" : box.getName();
                        box.setName(CardNameUtils.rebuildDecoratedName(raw, newName, '='));
                        label.setText(newName);
                        UserInterfaceFunctions.markMyCollectionDirty();
                        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                        UserInterfaceFunctions.refreshOwnedCollectionStructure();
                    }));
            setGraphic(titleRow);
        } else {
            setGraphic(label);
        }
    }

    /**
     * Builds the Deck header row shown in the Decks &amp; Collections tree: plain
     * label everywhere else, but on Decks &amp; Collections it gets inline
     * Sort/Rename/Save buttons, and on OuicheList it shows the advancement stats
     * row instead.
     */
    private void buildDeckHeaderCell(String itemName, Model.CardsLists.Deck deck) {
        Label label = new Label(itemName);
        label.getStyleClass().add("tree-item-label");
        if (isDecksAndCollectionsTabSelected()) {
            HBox titleRow = new HBox(6, label);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            HBox spring = new HBox();
            HBox.setHgrow(spring, Priority.ALWAYS);
            Button sortBtn = makeInlineActionButton("⇅ Sort");
            Button renameBtn = makeInlineActionButton("✎ Rename");
            Button saveBtn = makeInlineActionButton("💾 Save");
            titleRow.getChildren().addAll(spring, sortBtn, renameBtn, saveBtn);

            // Sort: sorts Main Deck, Extra Deck and Side Deck using the
            // standard middle-pane ordering (CardElementSorter).
            sortBtn.setOnAction(e -> {
                boolean changed = false;
                for (String section : new String[]{"main", "extra", "side"}) {
                    CardsGroup sectionGroup = getDeckSectionGroup(deck, section);
                    if (sectionGroup == null) {
                        continue;
                    }
                    javafx.collections.ObservableList<CardElement> obs =
                            observableListFor(sectionGroup);
                    java.util.List<CardElement> sorted =
                            Utils.CardElementSorter.sorted(
                                    new java.util.ArrayList<>(obs));
                    obs.setAll(sorted);
                    changed = true;
                }
                if (changed) {
                    UserInterfaceFunctions.markDirty(deck);
                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    UserInterfaceFunctions.refreshDecksAndCollectionsView();
                }
            });

            renameBtn.setOnAction(e -> showRenamePopup(label, label.getText(),
                    newName -> {
                        deck.setName(newName);
                        label.setText(newName);
                        UserInterfaceFunctions.markDirty(deck);
                        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                        UserInterfaceFunctions.refreshDecksAndCollectionsView();
                    }));
            saveBtn.setOnAction(e -> {
                try {
                    UserInterfaceFunctions.saveSingleDeckOrCollection(deck);
                } catch (Exception ex) {
                    logger.error("Failed to save deck '{}'", deck.getName(), ex);
                }
            });
            setGraphic(titleRow);
        } else if (isOuicheListTabSelected()) {
            String stats = buildAdvancementLabel(deck.toList());
            setGraphic(buildOuicheListTitleRow(itemName, stats));
        } else {
            setGraphic(label);
        }
    }

    /**
     * Builds the "Archetypes" section header row. On Decks &amp; Collections it gets
     * an "Add" context-menu item for adding a new archetype to this collection.
     */
    private void buildArchetypesSectionHeaderCell(String itemName) {
        Label label = new Label(itemName);
        label.getStyleClass().add("tree-item-label");
        setGraphic(label);

        if (isDecksAndCollectionsTabSelected()) {
            ContextMenu archetypesSectionContextMenu = NavigationContextMenuBuilder.styledContextMenu();
            archetypesSectionContextMenu.getItems().add(
                    NavigationContextMenuBuilder.makeItem("Add")
            );
            label.setOnContextMenuRequested(e -> {
                archetypesSectionContextMenu.show(label, e.getScreenX(), e.getScreenY());
                e.consume();
            });
        }
    }

    /**
     * Inner-class helper: returns true when the grid view this cell belongs
     * to renders cards from an archetype group.
     * Reads the "isArchetype" flag stored in the GridView's userData map by
     * createCardsGroupCell().
     */
    boolean isInArchetypeGroup() {
        try {
            // CardGridCell (inner class) calls this through the outer CardTreeCell instance.
            // We access cardGridView which is a field of the outer CardTreeCell.
            if (cardGridView == null) return false;
            Object ud = cardGridView.getUserData();
            if (ud instanceof Map) {
                Object ia = ((Map<?, ?>) ud).get("isArchetype");
                return ia instanceof Boolean && (Boolean) ia;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Find the location string for a CardElement inside the given OwnedCardsCollection.
     * Returned format: "BoxName/GroupName@index" where BoxName and GroupName are sanitized
     * (remove '=' and '-') and index is the position inside the group's list.
     * <p>
     * This method is robust: it first tries to locate the exact same CardElement instance
     * (identity). If that fails (for example because the UI and the collection use different
     * object instances), it attempts to find the best matching CardElement by comparing:
     * - card identity (passCode / printCode / konamiId)
     * - CardElement metadata (specificArtwork, artwork, isOwned, dontRemove, isInDeck)
     * <p>
     * If multiple candidates remain, the first matching candidate is returned. Returns null
     * only if no matching element is found in the provided collection.
     */
    private String findElementLocation(CardElement target, OwnedCardsCollection owned) {
        if (target == null || owned == null || owned.getOwnedCollection() == null) return null;

        // Helper to compare two Card objects (same logic as cardsMatch)
        java.util.function.BiPredicate<Model.CardsLists.Card, Model.CardsLists.Card> sameCard =
                (a, b) -> {
                    if (a == null || b == null) return false;
                    if (a.getPassCode() != null && b.getPassCode() != null && a.getPassCode().equals(b.getPassCode()))
                        return true;
                    if (a.getPrintCode() != null && b.getPrintCode() != null && a.getPrintCode().equals(b.getPrintCode()))
                        return true;
                    if (a.getKonamiId() != null && b.getKonamiId() != null && a.getKonamiId().equals(b.getKonamiId()))
                        return true;
                    return false;
                };

        // Helper to compare CardElement metadata for a stronger match when identity differs
        java.util.function.BiPredicate<CardElement, CardElement> elementMetadataEquals = (a, b) -> {
            if (a == null || b == null) return false;
            boolean specificArtworkA = a.getSpecificArtwork() == null ? false : a.getSpecificArtwork();
            boolean specificArtworkB = b.getSpecificArtwork() == null ? false : b.getSpecificArtwork();
            if (specificArtworkA != specificArtworkB) return false;
            if (a.getArtwork() != b.getArtwork()) return false;
            boolean ownedA = a.getOwnershipStatus() == null ? false : a.getOwned();
            boolean ownedB = b.getOwnershipStatus() == null ? false : b.getOwned();
            if (ownedA != ownedB) return false;
            boolean isInDeckA = a.getIsInDeck() == null ? false : a.getIsInDeck();
            boolean isInDeckB = b.getIsInDeck() == null ? false : b.getIsInDeck();
            if (isInDeckA != isInDeckB) return false;
            boolean dontRemoveA = a.getDontRemove() == null ? false : a.getDontRemove();
            boolean dontRemoveB = b.getDontRemove() == null ? false : b.getDontRemove();
            if (dontRemoveA != dontRemoveB) return false;
            return true;
        };

        // 1) Try identity search first (fast and exact)
        for (Box box : owned.getOwnedCollection()) {
            if (box == null || box.getContent() == null) continue;
            String boxLabel = CardNameUtils.sanitize(box.getName());
            for (CardsGroup cardsGroup : box.getContent()) {
                if (cardsGroup == null) continue;
                List<CardElement> list = cardsGroup.getCardList();
                if (list == null) continue;
                for (int i = 0; i < list.size(); i++) {
                    CardElement cardElement = list.get(i);
                    if (cardElement == target) {
                        String groupLabel = CardNameUtils.sanitize(cardsGroup.getName());
                        return boxLabel + "/" + groupLabel + "@" + i;
                    }
                }
            }
        }

        // 2) If identity search failed, try to find by card identity + metadata
        Model.CardsLists.Card targetCard = target.getCard();
        if (targetCard == null) return null;

        // Collect candidates that match by card identity
        List<FoundCandidate> candidates = new ArrayList<>();
        for (Box box : owned.getOwnedCollection()) {
            if (box == null || box.getContent() == null) continue;
            String boxLabel = CardNameUtils.sanitize(box.getName());
            for (CardsGroup cardsGroup : box.getContent()) {
                if (cardsGroup == null) continue;
                List<CardElement> list = cardsGroup.getCardList();
                if (list == null) continue;
                for (int i = 0; i < list.size(); i++) {
                    CardElement cardElement = list.get(i);
                    if (cardElement == null || cardElement.getCard() == null) continue;
                    if (sameCard.test(cardElement.getCard(), targetCard)) {
                        String groupLabel = CardNameUtils.sanitize(cardsGroup.getName());
                        candidates.add(new FoundCandidate(boxLabel, groupLabel, i, cardElement));
                    }
                }
            }
        }

        if (candidates.isEmpty()) return null;

        // 3) Prefer candidates that match metadata exactly
        for (FoundCandidate candidate : candidates) {
            if (elementMetadataEquals.test(candidate.element, target)) {
                return candidate.box + "/" + candidate.group + "@" + candidate.index;
            }
        }

        // 4) If none matched metadata exactly, return the first candidate that is not the same instance
        // (we already checked identity earlier, but keep defensive check)
        for (FoundCandidate candidate : candidates) {
            if (candidate.element != target) {
                return candidate.box + "/" + candidate.group + "@" + candidate.index;
            }
        }

        // 5) As a last resort, return the first candidate's location
        FoundCandidate candidate = candidates.get(0);
        return candidate.box + "/" + candidate.group + "@" + candidate.index;
    }

    // Small helper holder used by findElementLocation
    private static class FoundCandidate {
        final String box;
        final String group;
        final int index;
        final CardElement element;

        FoundCandidate(String box, String group, int index, CardElement element) {
            this.box = box;
            this.group = group;
            this.index = index;
            this.element = element;
        }
    }

    /**
     * Correct preferred height: uses the ACTUAL rendered row span
     * (cardHeight + 2×padding + verticalSpacing).
     */
    /**
     * Collects all Card objects from the entire TreeView in display order
     * (depth-first traversal, collecting only from CardsGroup nodes).
     * Enables SHIFT+click range selection that spans multiple groups/lists.
     */
    public static List<Card> collectAllCardsInTreeOrder(TreeItem<String> rootItem) {
        List<Card> allCards = new ArrayList<>();
        if (rootItem == null) return allCards;
        collectCardsRecursive(rootItem, allCards);
        return allCards;
    }

    /**
     * Collects all CardElement objects from the entire TreeView in display order.
     * Enables SHIFT+click range selection spanning multiple groups.
     */
    public static List<CardElement> collectAllElementsInTreeOrder(TreeItem<String> rootItem) {
        List<CardElement> allElements = new ArrayList<>();
        if (rootItem == null) return allElements;
        collectElementsRecursive(rootItem, allElements);
        return allElements;
    }

    private static void collectElementsRecursive(TreeItem<String> treeItem, List<CardElement> result) {
        if (treeItem == null) return;
        if (treeItem instanceof DataTreeItem) {
            Object data = ((DataTreeItem<?>) treeItem).getData();
            CardsGroup cardsGroup = null;
            if (data instanceof CardsGroup) {
                cardsGroup = (CardsGroup) data;
            } else if (data instanceof Map) {
                Object groupObj = ((Map<?, ?>) data).get("group");
                if (groupObj instanceof CardsGroup) cardsGroup = (CardsGroup) groupObj;
            }
            if (cardsGroup != null) {
                List<CardElement> cardList = cardsGroup.getCardList();
                if (cardList != null) result.addAll(cardList);
                return;
            }
        }
        for (TreeItem<String> child : treeItem.getChildren()) {
            collectElementsRecursive(child, result);
        }
    }

    public static void collectCardsRecursive(TreeItem<String> treeItem, List<Card> result) {
        if (treeItem == null) return;

        if (treeItem instanceof DataTreeItem) {
            Object data = ((DataTreeItem<?>) treeItem).getData();
            CardsGroup cardsGroup = null;
            if (data instanceof CardsGroup) {
                cardsGroup = (CardsGroup) data;
            } else if (data instanceof Map) {
                Object groupObj = ((Map<?, ?>) data).get("group");
                if (groupObj instanceof CardsGroup) {
                    cardsGroup = (CardsGroup) groupObj;
                }
            }
            if (cardsGroup != null) {
                List<CardElement> cardList = cardsGroup.getCardList();
                if (cardList != null) {
                    for (CardElement cardElement : cardList) {
                        if (cardElement != null && cardElement.getCard() != null) {
                            result.add(cardElement.getCard());
                        }
                    }
                }
                return; // Leaf group node — don't recurse into visual children
            }
        }
        for (TreeItem<String> child : treeItem.getChildren()) {
            collectCardsRecursive(child, result);
        }
    }

    /**
     * Returns true when this cell's TreeView has been tagged as the archetypes
     * tree view (via {@code getProperties().put("tabType", "ARCHETYPES")}).
     */
    private boolean isInArchetypesTreeView() {
        try {
            javafx.scene.control.TreeView<?> tv = getTreeView();
            return tv != null && "ARCHETYPES".equals(tv.getProperties().get("tabType"));
        } catch (Exception ignored) {
        }
        return false;
    }



    /**
     * If {@code targetGroup} is a Deck's main or extra deck section and the
     * payload is incompatible (e.g. extra-deck card dropped on Main Deck),
     * returns the other section's CardsGroup for the same Deck.
     * Falls back to {@code targetGroup} unchanged when redirection is not
     * needed or not possible.
     */
    private static CardsGroup resolveCompatibleTargetGroup(
            CardsGroup targetGroup,
            java.util.List<Model.CardsLists.CardElement> sourceElements,
            java.util.List<Model.CardsLists.Card> sourceCards) {

        if (targetGroup == null) return targetGroup;
        String groupName = targetGroup.getName();
        if (groupName == null) return targetGroup;

        boolean isMain = Utils.DeckCompatibility.isMainDeckSection(groupName);
        boolean isExtra = Utils.DeckCompatibility.isExtraDeckSection(groupName);
        if (!isMain && !isExtra) return targetGroup; // side deck or other — no restriction

        // Collect first card to test
        Model.CardsLists.Card sample = null;
        if (sourceElements != null) {
            for (Model.CardsLists.CardElement ce : sourceElements) {
                if (ce != null && ce.getCard() != null) {
                    sample = ce.getCard();
                    break;
                }
            }
        }
        if (sample == null && sourceCards != null) {
            for (Model.CardsLists.Card c : sourceCards) {
                if (c != null) {
                    sample = c;
                    break;
                }
            }
        }
        if (sample == null) return targetGroup;

        String redirect = Utils.DeckCompatibility.redirectSection(sample, groupName);
        if (redirect == null) return targetGroup; // already compatible

        // Find the Deck that owns this group
        Model.CardsLists.Deck ownerDeck = findDeckOwnerForGroup(targetGroup);
        if (ownerDeck == null) return targetGroup;

        // Find the redirect group (other section) in the same Deck
        String redirectKey = redirect.toLowerCase(java.util.Locale.ROOT)
                .replace(" deck", "").trim(); // "main" or "extra"
        CardsGroup redirectGroup = getDeckSectionGroup(ownerDeck, redirectKey);
        return redirectGroup != null ? redirectGroup : targetGroup;
    }


    /**
     * Build menu items for Decks and Collections proposals.
     * Each MenuItem label follows the requested format:
     * - CollectionName
     * - CollectionName/DeckName/Main Deck (or Extra/Side)
     */
    // ── Drop execution helpers (called from drag-drop handlers) ────────────────

    /**
     * After a RIGHT-pane drag-drop into a My Collection group, opens a
     * {@link View.CardEditPopup} for every dropped card that has no printCode.
     *
     * <p>Popups are opened in reverse card order so the <em>first</em> card's popup
     * ends up on top (the last {@code show()} call wins z-order).</p>
     *
     * @param droppedCards the cards just inserted
     * @param targetGroup  the group they were inserted into (used to find the
     *                     matching new CardElement at the tail of the list)
     * @param sceneNode    any node currently in the scene, used to resolve the owner window
     */
    void openEditPopupsForNoPrintCode(
            java.util.List<Model.CardsLists.Card> droppedCards,
            CardsGroup targetGroup,
            javafx.scene.Node sceneNode) {
        if (droppedCards == null || droppedCards.isEmpty() || targetGroup == null) return;

        // Resolve the window before any potential refresh.
        javafx.stage.Window ownerWindow = null;
        try {
            if (sceneNode != null && sceneNode.getScene() != null)
                ownerWindow = sceneNode.getScene().getWindow();
        } catch (Exception ignored) {
        }
        // Also try the CardTreeCell itself as a fallback.
        if (ownerWindow == null) {
            try {
                if (getScene() != null) ownerWindow = getScene().getWindow();
            } catch (Exception ignored) {
            }
        }
        final javafx.stage.Window finalOwner = ownerWindow;

        // Collect the cards that need a popup (no printCode).
        java.util.List<Model.CardsLists.Card> noPrint = new java.util.ArrayList<>();
        for (Model.CardsLists.Card c : droppedCards) {
            if (c != null && (c.getPrintCode() == null || c.getPrintCode().isBlank())) {
                noPrint.add(c);
            }
        }
        if (noPrint.isEmpty()) return;

        // Find the freshly-inserted CardElements at the tail of the group's list.
        // dropInsertIntoGroup appended them in order, so the last N elements correspond
        // to the N dropped cards (where N = noPrint.size() within droppedCards).
        javafx.collections.ObservableList<CardElement> obsList = observableListFor(targetGroup);
        int listSize = obsList.size();

        // Build a map: card → the last CardElement in the group that wraps it.
        java.util.Map<Model.CardsLists.Card, CardElement> cardToElement = new java.util.LinkedHashMap<>();
        for (int i = listSize - droppedCards.size(); i < listSize; i++) {
            if (i < 0) continue;
            CardElement el = obsList.get(i);
            if (el != null && el.getCard() != null) {
                cardToElement.put(el.getCard(), el);
            }
        }

        // Open in reverse order so the first card's popup is on top.
        for (int i = noPrint.size() - 1; i >= 0; i--) {
            Model.CardsLists.Card c = noPrint.get(i);
            CardElement el = cardToElement.get(c);
            if (el != null) {
                Controller.MenuActionHandler.handleEditCard(el, null);
            }
        }
    }

    /**
     * Create the visual cell for a CardsGroup.
     * <p>
     * If this group belongs to an archetype, the provided missingForThisGroup set is used.
     * If this group belongs to the "My Cards Collection" tree, compute a "to-sort" set
     * (cards that contain "Trap" in name_EN or "Piège" in name_FR) and set it as the GridView userData.
     */
    private void createCardsGroupCell(String itemName, CardsGroup group, Set<String> missingForThisGroup) {
        String rawGroupName = group.getName() == null ? "" : group.getName();
        boolean isArchetype;
        String displayName = rawGroupName;
        if (rawGroupName.startsWith(ARCHETYPE_MARKER)) {
            isArchetype = true;
            displayName = rawGroupName.substring(ARCHETYPE_MARKER.length());
        } else {
            isArchetype = false;
        }
        displayName = Model.CardsLists.OwnedCardsCollection.extractName(displayName, '-');

        HBox hbox = buildGroupHeaderRow(group, displayName, isArchetype);

        VBox vBox = new VBox();
        vBox.getStyleClass().add("card-group-vbox");
        vBox.getChildren().add(hbox);

        // ── Card items: shared setup for both display modes ──────────────────────
        javafx.collections.ObservableList<CardElement> groupItems = observableListFor(group);
        javafx.collections.transformation.FilteredList<CardElement> filteredItems =
                new javafx.collections.transformation.FilteredList<>(groupItems);
        filteredItems.setPredicate(buildCombinedPredicate(activeMiddleFilter));
        CardGroupRegistry.GROUP_FILTERED_LISTS.put(group, new java.lang.ref.WeakReference<>(filteredItems));

        boolean useListMode = isOuicheListTabSelected()
                && Controller.OuicheListController.isDetailedListMode();

        if (useListMode) {
            // ── List mode (OuicheList tab only) ──────────────────────────────────
            // One bordered row per card.  MISSING → white border.
            // OWNED_SUBSTANDARD → red border + required condition / rarity.
            // OWNED → grayed (hideOwned filter already removes them when active).
            VBox listBox = buildListModeGroupContent(filteredItems);
            vBox.getChildren().add(listBox);
        } else {
            // ── Mosaic (grid) mode — default for all tabs ────────────────────────
            GridView<CardElement> grid = buildMosaicModeGroupContent(
                    group, filteredItems, isArchetype, displayName, missingForThisGroup);
            vBox.getChildren().add(grid);
            VBox.setVgrow(grid, Priority.ALWAYS);
        } // end mosaic branch

        setGraphic(vBox);

        // For Decks & Collections tab only; different menu per group type.
        wireGroupHeaderContextMenu(hbox, isArchetype);
    }

    /**
     * Builds the group-title row: the collapse/expand triangle, the display-name
     * label, and (context-dependent) an inline "Rename" button on the My Collection
     * tab or a "Sort" button for the "Cards"/"Cards not to add" groups on the
     * Decks &amp; Collections tab.
     */
    private HBox buildGroupHeaderRow(CardsGroup group, String displayName, boolean isArchetype) {
        HBox hbox = new HBox();
        hbox.getStyleClass().add("card-group-hbox");
        hbox.setSpacing(5);

        customTriangleLabel = new Label();
        customTriangleLabel.getStyleClass().add("custom-triangle-label");
        customTriangleLabel.setMinWidth(15);
        customTriangleLabel.setAlignment(Pos.CENTER);

        Label label = new Label(displayName);
        label.getStyleClass().add("card-group-label");

        hbox.getChildren().addAll(customTriangleLabel, label);

        // ── Rename button for CardsGroup titles in the My Collection tab ──────
        if (!isArchetype && isMyCollectionTabSelected()) {
            HBox spring = new HBox();
            HBox.setHgrow(spring, Priority.ALWAYS);
            Button renameBtn = makeInlineActionButton("✎ Rename");
            final CardsGroup capturedGroup = group;
            renameBtn.setOnAction(e -> showRenamePopup(label, label.getText(), 4,
                    newName -> {
                        String raw = capturedGroup.getName() == null ? "" : capturedGroup.getName();
                        capturedGroup.setName(CardNameUtils.rebuildDecoratedName(raw, newName, '-'));
                        label.setText(newName);
                        UserInterfaceFunctions.markMyCollectionDirty();
                        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                        UserInterfaceFunctions.refreshOwnedCollectionStructure();
                    }));
            hbox.getChildren().addAll(spring, renameBtn);
        }

        // ── Sort button for "Cards" and "Cards not to add" in the D&C tab ─────
        // Placed to the far right of the group title; only shown for those two
        // group names — not for archetype groups (read-only) or deck sections
        // (sorted via the Deck header Sort button instead).
        if (!isArchetype
                && isDecksAndCollectionsTabSelected()
                && ("Cards".equals(displayName) || "Cards not to add".equals(displayName))) {
            HBox sortSpring = new HBox();
            HBox.setHgrow(sortSpring, Priority.ALWAYS);
            Button sortBtn = makeInlineActionButton("⇅ Sort");
            final CardsGroup capturedGroup = group;
            sortBtn.setOnAction(e -> {
                javafx.collections.ObservableList<CardElement> obs =
                        observableListFor(capturedGroup);
                java.util.List<CardElement> sorted =
                        Utils.CardElementSorter.sorted(
                                new java.util.ArrayList<>(obs));
                obs.setAll(sorted);
                markDirtyAndRefreshForGroup(capturedGroup);
            });
            hbox.getChildren().addAll(sortSpring, sortBtn);
        }

        return hbox;
    }

    /**
     * Builds the OuicheList detailed-list-mode content: one bordered row per card
     * (white border = missing, red border = owned but substandard condition/rarity,
     * grayed = fully owned), wired to the collapse/expand triangle.
     */
    private VBox buildListModeGroupContent(
            javafx.collections.transformation.FilteredList<CardElement> filteredItems) {
        VBox listBox = new VBox(4);
        listBox.setPadding(new Insets(5));
        listBox.setStyle("-fx-background-color: #100317;");

        for (CardElement cardElement : filteredItems) {
            if (cardElement == null || cardElement.getCard() == null) {
                continue;
            }

            OwnershipStatus status = cardElement.getOwnershipStatus();
            boolean isOwned = status == OwnershipStatus.OWNED;
            boolean isSubstandard = status == OwnershipStatus.OWNED_SUBSTANDARD;

            String borderColor = isSubstandard ? "#FF3333" : "white";

            HBox row = new HBox(8);
            row.setPadding(new Insets(4, 6, 4, 6));
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle(
                    "-fx-border-color: " + borderColor + "; "
                            + "-fx-border-width: 1; "
                            + "-fx-border-radius: 4; "
                            + "-fx-background-radius: 4; "
                            + "-fx-background-color: #0a0012;");

            // Thumbnail
            ImageView img = new ImageView();
            img.setFitWidth(40.0);
            img.setFitHeight(58.0);
            img.setPreserveRatio(true);

            imageLoader.loadCardImage(cardElement, img);

            // Grayscale for fully-owned cards
            if (isOwned) {
                ColorAdjust grayscale = new ColorAdjust();
                grayscale.setSaturation(-0.7);
                grayscale.setBrightness(-0.5);
                img.setEffect(grayscale);
            }

            // Names + quality requirements
            VBox namesBox = new VBox(2);
            HBox.setHgrow(namesBox, Priority.ALWAYS);
            namesBox.setAlignment(Pos.CENTER_LEFT);

            String nameEN = cardElement.getCard().getName_EN();
            String nameFR = cardElement.getCard().getName_FR();
            String displayNameText = (nameEN != null && !nameEN.isEmpty()) ? nameEN
                    : (nameFR != null ? nameFR : "");
            Label nameLabel = new Label(displayNameText);
            nameLabel.setStyle("-fx-text-fill: "
                    + (isOwned ? "#888888" : "white")
                    + "; -fx-font-size: 12;");
            nameLabel.setWrapText(true);
            namesBox.getChildren().add(nameLabel);

            if (isSubstandard) {
                Model.CardsLists.CardCondition condition = cardElement.getCondition();
                Model.CardsLists.CardRarity rarity = cardElement.getRarity();
                if (condition != null) {
                    Label condLabel = new Label(
                            "Required condition: " + condition.getDisplayName());
                    condLabel.setStyle("-fx-text-fill: #EB9E34; -fx-font-size: 11;");
                    namesBox.getChildren().add(condLabel);
                }
                if (rarity != null) {
                    Label rarLabel = new Label(
                            "Required rarity: " + rarity.getDisplayName());
                    rarLabel.setStyle("-fx-text-fill: #EB9E34; -fx-font-size: 11;");
                    namesBox.getChildren().add(rarLabel);
                }
            }

            row.getChildren().addAll(img, namesBox);
            listBox.getChildren().add(row);
        }

        this.cardGridView = null;
        customTriangleLabel.setOnMouseClicked(event -> {
            boolean isExpanded = !listBox.isVisible();
            listBox.setVisible(isExpanded);
            listBox.setManaged(isExpanded);
            updateCustomTriangle(isExpanded);
            event.consume();
        });
        listBox.setVisible(true);
        listBox.setManaged(true);
        updateCustomTriangle(true);

        return listBox;
    }

    /**
     * Builds the mosaic (grid) mode content — the default display for all tabs.
     * Wires the GridView's userData (missing-set/artwork/element-name metadata used
     * by {@link CardGridCell#updateItem}), the height-adjustment listeners, the
     * collapse/expand triangle, and the between-card drop target for the gaps the
     * per-cell drop target ({@link CardGridCell}) doesn't cover.
     */
    private GridView<CardElement> buildMosaicModeGroupContent(
            CardsGroup group,
            javafx.collections.transformation.FilteredList<CardElement> filteredItems,
            boolean isArchetype,
            String displayName,
            Set<String> missingForThisGroup) {
        GridView<CardElement> grid = new GridView<>();
        grid.getStyleClass().add("card-grid-view");
        grid.setCellFactory(gridView -> new CardGridCell(this));
        CardGroupRegistry.GROUP_GRID_VIEWS.put(group, new java.lang.ref.WeakReference<>(grid));
        grid.setItems(filteredItems);
        grid.cellWidthProperty().bind(cardWidthProperty);
        grid.cellHeightProperty().bind(cardHeightProperty);
        grid.setHorizontalCellSpacing(6);
        grid.setVerticalCellSpacing(6);
        grid.setPadding(new Insets(5));
        grid.prefWidthProperty().bind(getTreeView().widthProperty().subtract(50));

        /*
         * Store a Map as userData so the renderer has both pieces of information:
         *  - "missingSet" : Set<String> (may be empty or null)
         *  - "elementName": String (displayName)
         *
         * This preserves the archetype missing-set behavior (used by Decks & Collections / Archetypes)
         * while also providing the element name for My Collection compute logic.
         */
        Set<String> missingArtworkSet = CardGroupRegistry.MISSING_ARTWORK_SETS.get(group);

        Map<String, Object> userData = new HashMap<>();
        userData.put("missingSet", missingForThisGroup == null ? Collections.emptySet() : missingForThisGroup);
        userData.put("elementName", displayName);
        userData.put("isArchetype", isArchetype);
        userData.put("missingArtworkSet", missingArtworkSet == null ? Collections.emptySet() : missingArtworkSet);
        userData.put("hideOwnedCards", Controller.OuicheListController.isHideOwnedCardsEnabled());
        grid.setUserData(userData);

        this.cardGridView = grid;

        // Initial call: best-effort using prefWidth (grid not yet laid out)
        adjustGridViewHeight(group);
        // Deferred correction: re-runs after the first layout pass when grid.getWidth() is valid
        Platform.runLater(() -> adjustGridViewHeight(group));

        // Listeners: deferred so grid.getWidth() reflects the new size after layout
        cardWidthProperty.addListener((obs, oldVal, newVal) ->
                Platform.runLater(() -> adjustGridViewHeight(group)));
        getTreeView().widthProperty().addListener((obs, oldVal, newVal) ->
                Platform.runLater(() -> adjustGridViewHeight(group)));

        customTriangleLabel.setOnMouseClicked(event -> {
            boolean isExpanded = !grid.isVisible();
            grid.setVisible(isExpanded);
            grid.setManaged(isExpanded);
            updateCustomTriangle(isExpanded);
            event.consume();
        });
        grid.setVisible(true);
        grid.setManaged(true);
        updateCustomTriangle(true);

        // ── GridView drop target: between-card gaps ───────────────────────────────
        // The wrapper inside each CardGridCell consumes card-level events; the
        // GridView only receives events that land in gaps between or below cards.
        grid.setOnDragOver(event -> {
            if (!isArchetype
                    && event.getDragboard().hasString()
                    && Controller.DragDropManager.getDragSourcePane() != null) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            }
            event.consume();
        });

        grid.setOnDragDropped(event -> {
            if (isArchetype) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            // wrapper.setOnDragDropped fires first (child before parent).
            // If it handled the drop it sets CardGroupRegistry.dropHandledByCell = true.
            // Consume the flag immediately so it never persists past this point.
            if (CardGroupRegistry.dropHandledByCell) {
                CardGroupRegistry.dropHandledByCell = false; // always reset so next drag starts clean
                event.setDropCompleted(true);
                event.consume();
                return;
            }

            int insertionIndex = GridViewSizer.computeGapInsertionIndex(
                    grid, group, event.getX(), event.getY());
            if (insertionIndex < 0) {
                // Click is outside the card rows — treat as append
                insertionIndex = group.getCardList() == null ? 0 : group.getCardList().size();
            }

            String srcPane = Controller.DragDropManager.getDragSourcePane();
            if (srcPane == null) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            boolean isMiddle = "MIDDLE".equals(srcPane);
            if (isMiddle) {
                java.util.List<CardElement> srcElements =
                        new java.util.ArrayList<>(Controller.DragDropManager.getDraggedElements());
                java.util.Set<CardsGroup> srcGroups =
                        dropInsertIntoGroup(group, insertionIndex, srcElements, null);
                for (CardsGroup sourceGroup : srcGroups) {
                    markDirtyAndRefreshForGroup(sourceGroup);
                }
            } else {
                java.util.List<Model.CardsLists.Card> srcCards =
                        new java.util.ArrayList<>(Controller.DragDropManager.getDraggedCards());
                dropInsertIntoGroup(group, insertionIndex, null, srcCards);
                // My Collection only: open edit popup for cards dropped without a printCode.
                if (isMyCollectionTabSelected()) {
                    openEditPopupsForNoPrintCode(srcCards, group, this);
                }
            }
            markDirtyAndRefreshForGroup(group);
            event.setDropCompleted(true);
            event.consume();
        });

        return grid;
    }

    /**
     * Wires the group-title right-click menu: only active on Decks &amp; Collections,
     * and only offers anything for archetype group headers ("Remove").
     */
    private void wireGroupHeaderContextMenu(HBox hbox, boolean isArchetype) {
        hbox.setOnContextMenuRequested(event -> {
            if (!isDecksAndCollectionsTabSelected()) {
                event.consume();
                return;
            }

            ContextMenu headerContextMenu;

            if (isArchetype) {
                // Archetype group header  →  "Remove" (red + trash icon)
                headerContextMenu = NavigationContextMenuBuilder.styledContextMenu();
                headerContextMenu.getItems().add(NavigationContextMenuBuilder.makeRemoveItem());
            } else {
                // Regular group header (Cards, Main Deck, etc.) — no menu for now
                event.consume();
                return;
            }

            headerContextMenu.show(hbox, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }


    private void adjustGridViewHeight(CardsGroup group) {
        if (cardGridView == null) return;
        // Use the filtered count when a filter is active so the row height is correct.
        int numItems;
        java.lang.ref.WeakReference<javafx.collections.transformation.FilteredList<CardElement>> flRef =
                CardGroupRegistry.GROUP_FILTERED_LISTS.get(group);
        javafx.collections.transformation.FilteredList<CardElement> fl =
                flRef != null ? flRef.get() : null;
        if (fl != null) {
            numItems = fl.size();
        } else {
            numItems = group.getCardList() == null ? 0 : group.getCardList().size();
        }
        GridViewSizer.adjustGridViewHeight(cardGridView, numItems);
        logger.debug("Adjusted grid view height to {} for {} items",
                cardGridView.getPrefHeight(), numItems);
    }
    // CardGridCell is implemented in View/CardGridCell.java — instantiated via new CardGridCell(this)
}