package View;

import Controller.CardGroupRegistry;
import Controller.MenuActionHandler;
import Controller.UserInterfaceFunctions;
import Model.CardsLists.*;
import Model.Database.DataBaseUpdate;
import Utils.CardMatcher;
import Utils.LruImageCache;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.controlsfx.control.GridView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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

    static final ConcurrentHashMap<String, String> imagePathCache = new ConcurrentHashMap<>();
    /**
     * When {@code true}, cards in the My Collection tab that have a missing
     * printCode glow red, and cards missing condition or rarity glow orange.
     * Toggled by the "Mark incomplete cards" button in the header.
     */
    static volatile boolean incompleteMarkingEnabled = true;

    private static final ExecutorService imageLoadingExecutor = Executors.newFixedThreadPool(4);
    private static final ExecutorService pathResolverExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "image-path-resolver");
        t.setDaemon(true);
        return t;
    });
    final DoubleProperty cardWidthProperty;
    final DoubleProperty cardHeightProperty;

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
    final ConcurrentHashMap<ImageView, Future<?>> outstandingLoads = new ConcurrentHashMap<>();

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
     * The StackPane wrapper inside every GridCell has Insets(5) padding on each side,
     * adding 2×5 = 10 px to both the rendered cell width and height beyond the bound
     * cardWidth / cardHeight properties.
     */
    private static final double CELL_INNER_PADDING = 5.0;

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
            adjustGridViewHeightStatic(grid, numItems);
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
     * Convenience overload used by {@link CardTreeCellContextMenuBuilder}.
     */
    static void showRenamePopup(CardTreeCell ownerCell, String seedName,
                                java.util.function.Consumer<String> onConfirm) {
        Label anchor = new Label(seedName == null ? "" : seedName);
        Node graphic = ownerCell.getGraphic();
        if (graphic instanceof javafx.scene.layout.Pane) {
            for (Node child : ((javafx.scene.layout.Pane) graphic).getChildren()) {
                if (child instanceof Label) {
                    anchor = (Label) child;
                    break;
                }
            }
        }
        showRenamePopup(anchor, seedName, onConfirm);
    }

    /**
     * Helper: determine whether the currently selected Tab is the
     * Decks and Collections tab.
     */
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
     * Rebuilds a decorated name (e.g. {@code "===OldName==="}) replacing only
     * the inner text while preserving leading/trailing decorator characters.
     * Returns {@code newDisplayName} as-is if no decoration is present.
     */
    private static String rebuildDecoratedName(String raw, String newDisplayName, char decorator) {
        if (raw == null || raw.isEmpty()) return newDisplayName;
        int leading = 0;
        while (leading < raw.length() && raw.charAt(leading) == decorator) leading++;
        int trailing = 0;
        while (trailing < raw.length() && raw.charAt(raw.length() - 1 - trailing) == decorator) trailing++;
        if (leading == 0 && trailing == 0) return newDisplayName;
        return raw.substring(0, leading) + newDisplayName + raw.substring(raw.length() - trailing);
    }

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
            for (int i = 0; i < sourceCards.size(); i++) {
                Model.CardsLists.Card card = sourceCards.get(i);
                if (card == null) continue;
                int pos = Math.min(idx + i, targetList.size());
                targetList.add(pos, new CardElement(card));
            }
        }
        triggerHeightAdjustment(targetGroup);
        return modifiedSourceGroups;
    }

    // Helper: sanitize display names (strip only leading/trailing decoration chars)
    static String sanitizeDisplayName(String raw) {
        if (raw == null) return "";
        // Use extractName with '=' first to strip box-level decoration,
        // then with '-' to strip category-level decoration.
        // Since stored names are already clean, this is effectively a no-op
        // for clean names but correctly handles any residual decoration.
        String s = raw.trim();
        int start = 0;
        while (start < s.length() && (s.charAt(start) == '=' || s.charAt(start) == '-')) start++;
        int end = s.length();
        while (end > start && (s.charAt(end - 1) == '=' || s.charAt(end - 1) == '-')) end--;
        return s.substring(start, end).trim();
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
                if (grid != null) {
                    javafx.collections.ObservableList<CardElement> items = grid.getItems();
                    grid.setItems(null);
                    grid.setItems(items);
                }
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

    public static void shutdownImageLoadingExecutor() {
        imageLoadingExecutor.shutdownNow();
        pathResolverExecutor.shutdownNow();
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

            if (activeMiddleFilter == null) {
                fl.setPredicate(null);
            } else {
                final java.util.function.Predicate<Card> captured = activeMiddleFilter;
                fl.setPredicate(ce -> ce != null && ce.getCard() != null && captured.test(ce.getCard()));
            }

            // Recompute height using the post-filter count
            java.lang.ref.WeakReference<GridView<CardElement>> gridRef = CardGroupRegistry.GROUP_GRID_VIEWS.get(group);
            GridView<CardElement> grid = gridRef != null ? gridRef.get() : null;
            adjustGridViewHeightStatic(grid, fl.size());
        }
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

    private static class PlaceholderHolder {
        static final Image PLACEHOLDER = new Image("file:./src/main/resources/placeholder.jpg");
    }

    /**
     * Static counterpart of adjustGridViewHeight, driven by the grid's own bound properties.
     */
    private static void adjustGridViewHeightStatic(GridView<CardElement> grid, int numItems) {
        if (grid == null) return;
        if (numItems <= 0) {
            applyGridPrefHeight(grid, 0);
            return;
        }
        applyGridPrefHeight(grid, computeGridPrefHeight(grid, numItems));
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
     * Correct column count: uses the ACTUAL rendered cell width (cardWidth + 2×padding).
     */
    private static int computeGridColumns(GridView<CardElement> grid) {
        double totalW = grid.getWidth();
        if (totalW <= 0) totalW = grid.getPrefWidth();
        if (totalW <= 0) return 1;

        Insets pad = grid.getPadding();
        double padLR = (pad != null) ? pad.getLeft() + pad.getRight() : 0;
        double innerW = totalW - padLR;

        double cardW = grid.getCellWidth();
        double hSpace = grid.getHorizontalCellSpacing();
        double actualCellW = cardW + 2 * CELL_INNER_PADDING;   // e.g. 100 + 10 = 110

        int cols = (int) Math.max(1,
                Math.floor((innerW + hSpace) / (actualCellW + hSpace)));

        /*org.slf4j.LoggerFactory.getLogger(CardTreeCell.class).debug(
                "[GridView-cols] totalW={}  padLR={}  innerW={}  cardW={}  actualCellW={}  hSpace={}  cols={}",
                String.format("%.1f", totalW),
                String.format("%.1f", padLR),
                String.format("%.1f", innerW),
                String.format("%.1f", cardW),
                String.format("%.1f", actualCellW),
                String.format("%.1f", hSpace),
                cols);*/

        return cols;
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

    // ---------- Helpers for building proposals (add inside CardTreeCell class) ----------

    /**
     * Count how many copies of `card` are present in the owned collection for the given deck,
     * treating the deck as a single category that may contain Main/Extra/Side together.
     * <p>
     * Matching rules (tolerant):
     * - Prefer Box whose name equals deckName and sum counts of all groups inside it (useful when deck is a Box).
     * - If a Box contains a group named deckName, sum counts of groups in that Box (useful when deck is a group owner).
     * - If groups named "Main Deck"/"Extra Deck"/"Side Deck" exist under a matching Box, sum those specifically.
     * - Fallback: sum counts of any groups named like the deck anywhere.
     */
    private int countInOwnedForDeckCombined(OwnedCardsCollection owned, String deckName, Model.CardsLists.Card card) {
        if (owned == null || owned.getOwnedCollection() == null || deckName == null || deckName.trim().isEmpty())
            return 0;
        String deckNorm = sanitizeDisplayName(deckName).toLowerCase();
        String mainNorm = sanitizeDisplayName("Main Deck").toLowerCase();
        String extraNorm = sanitizeDisplayName("Extra Deck").toLowerCase();
        String sideNorm = sanitizeDisplayName("Side Deck").toLowerCase();

        // 1) If a Box name equals deckName, sum all groups inside that Box (prefer this)
        for (Box b : owned.getOwnedCollection()) {
            if (b == null) continue;
            if (sanitizeDisplayName(b.getName()).toLowerCase().equals(deckNorm)) {
                int sum = 0;
                if (b.getContent() != null) {
                    for (CardsGroup g : b.getContent()) {
                        if (g == null) continue;
                        sum += countCardInList(g.getCardList(), card);
                    }
                }
                return sum;
            }
        }

        // 2) If a Box contains a group named deckName, sum groups in that Box (deck represented as a group owner)
        for (Box b : owned.getOwnedCollection()) {
            if (b == null || b.getContent() == null) continue;
            boolean hasDeckGroup = false;
            for (CardsGroup g : b.getContent()) {
                if (g == null) continue;
                if (sanitizeDisplayName(g.getName()).toLowerCase().equals(deckNorm)) {
                    hasDeckGroup = true;
                    break;
                }
            }
            if (hasDeckGroup) {
                int sum = 0;
                for (CardsGroup g : b.getContent()) {
                    if (g == null) continue;
                    sum += countCardInList(g.getCardList(), card);
                }
                return sum;
            }
        }

        // 3) If a Box contains explicit Main/Extra/Side groups, sum those across the owned collection
        int sumLists = 0;
        for (Box b : owned.getOwnedCollection()) {
            if (b == null || b.getContent() == null) continue;
            boolean boxMatchesDeck = sanitizeDisplayName(b.getName()).toLowerCase().equals(deckNorm);
            boolean anyListFoundInBox = false;
            for (CardsGroup g : b.getContent()) {
                if (g == null) continue;
                String gNorm = sanitizeDisplayName(g.getName()).toLowerCase();
                if (gNorm.equals(mainNorm) || gNorm.equals(extraNorm) || gNorm.equals(sideNorm)) {
                    sumLists += countCardInList(g.getCardList(), card);
                    anyListFoundInBox = true;
                }
            }
            // If we found list groups in a box that matches the deck name, prefer that box's lists
            if (boxMatchesDeck && anyListFoundInBox) return sumLists;
        }
        if (sumLists > 0) return sumLists;

        // 4) Fallback: sum counts of any group named like the deck anywhere
        int fallbackSum = 0;
        for (Box b : owned.getOwnedCollection()) {
            if (b == null || b.getContent() == null) continue;
            for (CardsGroup g : b.getContent()) {
                if (g == null) continue;
                if (sanitizeDisplayName(g.getName()).toLowerCase().equals(deckNorm)) {
                    fallbackSum += countCardInList(g.getCardList(), card);
                }
            }
        }
        return fallbackSum;
    }

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
                        Object g = map.get("group");
                        Object m = map.get("missing");
                        if (g instanceof CardsGroup) group = (CardsGroup) g;
                        if (m instanceof Set) {
                            @SuppressWarnings("unchecked")
                            Set<String> s = (Set<String>) m;
                            missingForThisGroup = s;
                        } else if (m instanceof Collection) {
                            Set<String> s = new HashSet<>();
                            for (Object o : (Collection<?>) m) if (o != null) s.add(o.toString());
                            missingForThisGroup = s;
                        }
                    } catch (Exception e) {
                        logger.debug("Failed to extract archetype map data", e);
                    }
                }

                if (group != null) {
                    createCardsGroupCell(itemName, group, missingForThisGroup);
                } else if (dataObject instanceof CardElement) {
                    CardElement cardElement = (CardElement) dataObject;
                    HBox cardBox = new HBox(5);
                    cardBox.setAlignment(Pos.CENTER_LEFT);

                    ImageView imageView = new ImageView();
                    imageView.setFitWidth(cardWidthProperty.get());
                    imageView.setFitHeight(cardHeightProperty.get());
                    imageView.setPreserveRatio(true);

                    String imageKey = safeImageKey(cardElement);
                    String cachedFullPath = imageKey == null ? null : imagePathCache.get(imageKey);
                    if (cachedFullPath != null) {
                        Image cached = LruImageCache.getImage(cachedFullPath);
                        if (cached != null) {
                            imageView.setImage(cached);
                        } else {
                            imageView.setImage(getPlaceholderImage());
                            Future<?> f = loadImageWithResolvedPathAsync(cardElement, imageView, cachedFullPath);
                            if (f != null) outstandingLoads.put(imageView, f);
                        }
                    } else {
                        imageView.setImage(getPlaceholderImage());
                        resolveImagePathAsync(imageKey, resolvedPath -> {
                            if (resolvedPath == null) return;
                            Image cached = LruImageCache.getImage(resolvedPath);
                            if (cached != null) {
                                Platform.runLater(() -> {
                                    Object expected = imageView.getProperties().get("expectedImagePath");
                                    if (Objects.equals(expected, resolvedPath) || expected == null) {
                                        imageView.setImage(cached);
                                        imageView.getProperties().remove("expectedImagePath");
                                    }
                                });
                            } else {
                                imageView.getProperties().put("expectedImagePath", resolvedPath);
                                Future<?> f = loadImageWithResolvedPathAsync(cardElement, imageView, resolvedPath);
                                if (f != null) outstandingLoads.put(imageView, f);
                            }
                        });
                    }

                    Label nameLabel = new Label(cardElement.getCard() == null ? "" : cardElement.getCard().getName_EN());
                    nameLabel.setTextFill(javafx.scene.paint.Color.WHITE);

                    cardBox.getChildren().addAll(imageView, nameLabel);

                    // ----------------------------
                    // Determine whether to apply the "unsorted" glow
                    // ----------------------------
                    boolean needsSorting = false;
                    Set<String> missingSet = null;
                    String elementNameFromUD = null;
                    try {
                        // Retrieve userData from the GridView that was stored in createCardsGroupCell.
                        Object ud = null;
                        try {
                            if (this.cardGridView != null) ud = this.cardGridView.getUserData();
                        } catch (Exception ignored) {
                            ud = null;
                        }

                        if (ud instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> map = (Map<String, Object>) ud;
                            Object ms = map.get("missingSet");
                            if (ms instanceof Set) {
                                @SuppressWarnings("unchecked")
                                Set<String> s = (Set<String>) ms;
                                missingSet = s;
                            } else if (ms instanceof Collection) {
                                Set<String> s = new HashSet<>();
                                for (Object o : (Collection<?>) ms) if (o != null) s.add(o.toString());
                                missingSet = s;
                            }
                            Object en = map.get("elementName");
                            if (en instanceof String) elementNameFromUD = (String) en;
                        } else if (ud instanceof Set) {
                            @SuppressWarnings("unchecked")
                            Set<String> s = (Set<String>) ud;
                            missingSet = s;
                        } else if (ud instanceof String) {
                            elementNameFromUD = (String) ud;
                        }

                        // If a missing set exists and indicates this card is missing, use that (archetype glow)
                        if (missingSet != null && !missingSet.isEmpty()) {
                            String konamiId = cardElement.getCard() == null ? null : cardElement.getCard().getKonamiId();
                            String passCode = cardElement.getCard() == null ? null : cardElement.getCard().getPassCode();
                            boolean missing = false;
                            if (konamiId != null && missingSet.contains(konamiId)) missing = true;
                            if (!missing && passCode != null && missingSet.contains(passCode)) missing = true;
                            // fallback to legacy global set if needed (keeps previous behavior)
                            if (!missing && (missingSet.isEmpty())) {
                                missing = CardGroupRegistry.LEGACY_GLOBAL_MISSING_SET.contains(konamiId) || CardGroupRegistry.LEGACY_GLOBAL_MISSING_SET.contains(passCode);
                            }
                            needsSorting = missing;
                        } else {
                            // 2) No archetype missing-set -> sorting/upgrade check.
                            // My Collection: check if needs sorting, then if upgrade.
                            // D&C: already handled by isDegraded block below.
                            String elementName = elementNameFromUD;

                            if (elementName != null && !elementName.trim().isEmpty() && isMyCollectionTabSelected()) {
                                try {
                                    boolean genuinelyNeeded = Controller.CardQualityService
                                            .computeCardNeedsSorting(cardElement.getCard(), elementName);
                                    if (genuinelyNeeded) {
                                        needsSorting = true;
                                    } else {
                                        needsSorting = Controller.CardQualityService
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
                    if (!needsSorting && elementNameFromUD != null && !elementNameFromUD.trim().isEmpty()
                            && isDecksAndCollectionsTabSelected()) {
                        try {
                            isDegraded = Controller.CardQualityService
                                    .isDegradedCopyInDeckOrCollection(cardElement, elementNameFromUD);
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
                } else if (dataObject instanceof String && dataObject.equals("ROOT")) {
                    Label label = new Label(itemName);
                    label.getStyleClass().add("tree-root-label");
                    setGraphic(label);

                } else if (dataObject instanceof Model.CardsLists.ThemeCollection) {
                    // ── Collection header row ──────────────────────────────────
                    // Shown in the Decks & Collections and OuicheList tree.
                    // Right-click: "Add Deck" + "Add Archetype"
                    Model.CardsLists.ThemeCollection tc =
                            (Model.CardsLists.ThemeCollection) dataObject;
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
                                    tc.setName(newName);
                                    label.setText(newName);
                                    UserInterfaceFunctions.markDirty(tc);
                                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                                    UserInterfaceFunctions.refreshDecksAndCollectionsView();
                                }));

                        saveBtn.setOnAction(e -> {
                            try {
                                UserInterfaceFunctions.saveSingleDeckOrCollection(tc);
                            } catch (Exception ex) {
                                logger.error("Failed to save collection '{}'", tc.getName(), ex);
                            }
                        });

                        ContextMenu collectionCm = NavigationContextMenuBuilder.styledContextMenu();
                        collectionCm.getItems().addAll(
                                NavigationContextMenuBuilder.makeItem("Add Deck"),
                                NavigationContextMenuBuilder.makeItem("Add archetype")
                        );
                        titleRow.setOnContextMenuRequested(e -> {
                            collectionCm.show(titleRow, e.getScreenX(), e.getScreenY());
                            e.consume();
                        });
                        setGraphic(titleRow);
                    } else {
                        setGraphic(label);
                    }

                } else if (dataObject instanceof Model.CardsLists.Box) {
                    // ── Box header — My Collection tab ────────────────────────
                    Model.CardsLists.Box box = (Model.CardsLists.Box) dataObject;
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
                                    box.setName(rebuildDecoratedName(raw, newName, '='));
                                    label.setText(newName);
                                    UserInterfaceFunctions.markMyCollectionDirty();
                                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                                    UserInterfaceFunctions.refreshOwnedCollectionStructure();
                                }));
                        setGraphic(titleRow);
                    } else {
                        setGraphic(label);
                    }

                } else if (dataObject instanceof Model.CardsLists.Deck) {
                    // ── Deck header — Decks & Collections tab ─────────────────
                    Model.CardsLists.Deck deck = (Model.CardsLists.Deck) dataObject;
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
                                CardsGroup sg = getDeckSectionGroup(deck, section);
                                if (sg == null) continue;
                                javafx.collections.ObservableList<CardElement> obs =
                                        observableListFor(sg);
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
                    } else {
                        setGraphic(label);
                    }

                } else if ("ARCHETYPES_SECTION".equals(dataObject)) {
                    // ── "Archetypes" section header ────────────────────────────
                    // Right-click: "Add" (add a new archetype to this collection)
                    Label label = new Label(itemName);
                    label.getStyleClass().add("tree-item-label");
                    setGraphic(label);

                    if (isDecksAndCollectionsTabSelected()) {
                        ContextMenu archetypesSectionCm = NavigationContextMenuBuilder.styledContextMenu();
                        archetypesSectionCm.getItems().add(
                                NavigationContextMenuBuilder.makeItem("Add")
                        );
                        label.setOnContextMenuRequested(e -> {
                            archetypesSectionCm.show(label, e.getScreenX(), e.getScreenY());
                            e.consume();
                        });
                    }

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
            boolean sa = a.getSpecificArtwork() == null ? false : a.getSpecificArtwork();
            boolean sb = b.getSpecificArtwork() == null ? false : b.getSpecificArtwork();
            if (sa != sb) return false;
            if (a.getArtwork() != b.getArtwork()) return false;
            boolean oa = a.getOwned() == null ? false : a.getOwned();
            boolean ob = b.getOwned() == null ? false : b.getOwned();
            if (oa != ob) return false;
            boolean ida = a.getIsInDeck() == null ? false : a.getIsInDeck();
            boolean idb = b.getIsInDeck() == null ? false : b.getIsInDeck();
            if (ida != idb) return false;
            boolean da = a.getDontRemove() == null ? false : a.getDontRemove();
            boolean db = b.getDontRemove() == null ? false : b.getDontRemove();
            if (da != db) return false;
            return true;
        };

        // 1) Try identity search first (fast and exact)
        for (Box box : owned.getOwnedCollection()) {
            if (box == null || box.getContent() == null) continue;
            String boxLabel = sanitizeDisplayName(box.getName());
            for (CardsGroup g : box.getContent()) {
                if (g == null) continue;
                List<CardElement> list = g.getCardList();
                if (list == null) continue;
                for (int i = 0; i < list.size(); i++) {
                    CardElement ce = list.get(i);
                    if (ce == target) {
                        String groupLabel = sanitizeDisplayName(g.getName());
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
            String boxLabel = sanitizeDisplayName(box.getName());
            for (CardsGroup g : box.getContent()) {
                if (g == null) continue;
                List<CardElement> list = g.getCardList();
                if (list == null) continue;
                for (int i = 0; i < list.size(); i++) {
                    CardElement ce = list.get(i);
                    if (ce == null || ce.getCard() == null) continue;
                    if (sameCard.test(ce.getCard(), targetCard)) {
                        String groupLabel = sanitizeDisplayName(g.getName());
                        candidates.add(new FoundCandidate(boxLabel, groupLabel, i, ce));
                    }
                }
            }
        }

        if (candidates.isEmpty()) return null;

        // 3) Prefer candidates that match metadata exactly
        for (FoundCandidate fc : candidates) {
            if (elementMetadataEquals.test(fc.element, target)) {
                return fc.box + "/" + fc.group + "@" + fc.index;
            }
        }

        // 4) If none matched metadata exactly, return the first candidate that is not the same instance
        // (we already checked identity earlier, but keep defensive check)
        for (FoundCandidate fc : candidates) {
            if (fc.element != target) {
                return fc.box + "/" + fc.group + "@" + fc.index;
            }
        }

        // 5) As a last resort, return the first candidate's location
        FoundCandidate fc = candidates.get(0);
        return fc.box + "/" + fc.group + "@" + fc.index;
    }

    /**
     * Count how many occurrences of the given card exist in the provided CardElement list.
     * Matching uses passCode, then printCode, then konamiId.
     */
    private int countCardInList(List<CardElement> list, Model.CardsLists.Card card) {
        if (list == null || card == null) return 0;
        int count = 0;
        for (CardElement ce : list) {
            if (ce == null || ce.getCard() == null) continue;
            if (CardMatcher.cardsMatch(ce.getCard(), card)) count++;
        }
        return count;
    }

    // Placeholder handlers: only log for now. Implement move/swap logic later.
    private void onProposeMoveToLocation(CardElement clickedElement, String target) {
        logger.info("Proposed move: {} -> {}", clickedElement == null ? "null" : clickedElement.toString(), target);
        // TODO: implement actual move logic later
    }

    private void onProposeSwapWith(CardElement clickedElement, String otherLocation) {
        logger.info("Proposed swap: {} <-> {}", clickedElement == null ? "null" : clickedElement.toString(), otherLocation);
        // TODO: implement actual swap logic later
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
    private static double computeGridPrefHeight(GridView<CardElement> grid, int numItems) {
        if (numItems <= 0) return 0;

        int cols = computeGridColumns(grid);
        int rows = (int) Math.ceil((double) numItems / cols);
        Insets pad = grid.getPadding();
        double top = (pad != null) ? pad.getTop() : 0;
        double bottom = (pad != null) ? pad.getBottom() : 0;
        double cardH = grid.getCellHeight();
        double vSpace = grid.getVerticalCellSpacing();
        double actualCellH = cardH + 2 * CELL_INNER_PADDING;  // e.g. 146 + 10 = 156
        double rowSpan = actualCellH + vSpace;              // e.g. 156 + 5  = 161
        double h = top + bottom + rows * rowSpan + 1.0;

        /*org.slf4j.LoggerFactory.getLogger(CardTreeCell.class).debug(
                "[GridView-h] items={}  cols={}  rows={}  cardH={}  actualCellH={}  "
                        + "vSpace={}  rowSpan={}  top={}  bot={}  h={}",
                numItems, cols, rows,
                String.format("%.1f", cardH),
                String.format("%.1f", actualCellH),
                String.format("%.1f", vSpace),
                String.format("%.1f", rowSpan),
                String.format("%.1f", top),
                String.format("%.1f", bottom),
                String.format("%.2f", h));*/

        return h;
    }

    private static void applyGridPrefHeight(GridView<CardElement> grid, double h) {
        grid.setPrefHeight(h);
        grid.setMinHeight(h);
        grid.setMaxHeight(h);
    }

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

    void resolveImagePathAsync(String imageKey, Consumer<String> callback) {
        if (imageKey == null) {
            callback.accept(null);
            return;
        }
        String cached = imagePathCache.get(imageKey);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        pathResolverExecutor.submit(() -> {
            try {
                String[] addresses = DataBaseUpdate.getAddresses(imageKey + ".jpg");
                String resolved = null;
                if (addresses != null && addresses.length > 0) {
                    resolved = "file:" + addresses[0];
                    imagePathCache.put(imageKey, resolved);
                }
                callback.accept(resolved);
            } catch (Exception e) {
                logger.warn("Failed to resolve image path for key {}", imageKey, e);
                callback.accept(null);
            }
        });
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

    Future<?> loadImageWithResolvedPathAsync(CardElement cardElement, ImageView imageView, String resolvedPath) {
        if (resolvedPath == null) {
            Platform.runLater(() -> imageView.setImage(getPlaceholderImage()));
            return null;
        }

        Image cached = LruImageCache.getImage(resolvedPath);
        if (cached != null) {
            Platform.runLater(() -> {
                Object expected = imageView.getProperties().get("expectedImagePath");
                if (Objects.equals(expected, resolvedPath) || expected == null) {
                    imageView.setImage(cached);
                    imageView.getProperties().remove("expectedImagePath");
                }
            });
            return null;
        }

        imageView.getProperties().put("expectedImagePath", resolvedPath);

        AtomicReference<Future<?>> futureRef = new AtomicReference<>();
        Future<?> future = imageLoadingExecutor.submit(() -> {
            try {
                Image img = new Image(resolvedPath, cardWidthProperty.get(), cardHeightProperty.get(), true, true, true);

                if (img.getProgress() >= 1.0) {
                    LruImageCache.addImage(resolvedPath, img);
                    Platform.runLater(() -> {
                        Object expected = imageView.getProperties().get("expectedImagePath");
                        if (Objects.equals(expected, resolvedPath)) {
                            imageView.setImage(img);
                            imageView.getProperties().remove("expectedImagePath");
                        }
                    });
                } else {
                    Platform.runLater(() -> {
                        img.progressProperty().addListener((obs, oldV, newV) -> {
                            if (newV.doubleValue() >= 1.0) {
                                LruImageCache.addImage(resolvedPath, img);
                                Object expected = imageView.getProperties().get("expectedImagePath");
                                if (Objects.equals(expected, resolvedPath)) {
                                    imageView.setImage(img);
                                    imageView.getProperties().remove("expectedImagePath");
                                }
                            }
                        });
                    });
                }
            } catch (Exception e) {
                logger.error("Error loading image for card " + (cardElement != null && cardElement.getCard() != null ? cardElement.getCard().getName_EN() : "unknown"), e);
                Platform.runLater(() -> {
                    Object expected = imageView.getProperties().get("expectedImagePath");
                    if (expected == null || Objects.equals(expected, resolvedPath)) {
                        imageView.setImage(getPlaceholderImage());
                        imageView.getProperties().remove("expectedImagePath");
                    }
                });
            } finally {
                outstandingLoads.remove(imageView, futureRef.get());
            }
        });

        futureRef.set(future);
        outstandingLoads.put(imageView, future);
        return future;
    }

    /**
     * Build menu items for Decks and Collections proposals.
     * Each MenuItem label follows the requested format:
     * - CollectionName
     * - CollectionName/DeckName/Main Deck (or Extra/Side)
     */
    /**
     * Builds "Swap with..." menu items for a CardElement.
     *
     * <p>Two directions:</p>
     * <ul>
     *   <li><b>My Collection card (upgrade candidate):</b> proposes to swap this owned copy
     *       with the degraded copy already in a deck/collection.</li>
     *   <li><b>D&amp;C card (degraded copy):</b> proposes to swap with the better owned copy.</li>
     * </ul>
     *
     * @param card    the {@link Model.CardsLists.Card} of the clicked element
     * @param clicked the {@link CardElement} that was right-clicked
     * @return a (possibly empty) list of orange-styled swap MenuItems
     */
    @SuppressWarnings("unused") // called via reflection from sortingMenu.setOnShowing
    private List<MenuItem> buildSwapProposals(Model.CardsLists.Card card, CardElement clicked) {
        List<MenuItem> items = new ArrayList<>();
        try {
            boolean inDeckContext = false;
            // Determine if this cell is in a D&C group (vs My Collection)
            CardsGroup parentGroup = getParentGroup(clicked);
            if (parentGroup != null && findDeckOwnerForGroup(parentGroup) != null) {
                inDeckContext = true;
            }

            if (inDeckContext) {
                // D&C card — find owned upgrade candidates
                String elemName = elementNameFromUDForContextMenu();
                if (elemName == null || elemName.trim().isEmpty()) return items;
                List<Model.CardsLists.CardElement> candidates =
                        Controller.CardQualityService.findOwnedUpgradeCandidates(clicked, elemName);
                if (candidates.isEmpty()) return items;
                for (Model.CardsLists.CardElement candidate : candidates) {
                    String label = buildSwapCandidateLabel(candidate);
                    MenuItem mi = new MenuItem(label);
                    mi.setStyle("-fx-text-fill: #EB9E34;");
                    final CardElement finalCandidate = candidate;
                    mi.setOnAction(ev ->
                            Controller.MenuActionHandler.handleSwap(finalCandidate, clicked));
                    items.add(mi);
                }
            } else {
                // My Collection card — find D&C degraded entries this card can upgrade
                // Use computeCardNeedsSortingWithUpgrade to find matching deck/collection,
                // then find the specific degraded element in it.
                Model.CardsLists.DecksAndCollectionsList dac =
                        Controller.UserInterfaceFunctions.getDecksList();
                if (dac == null) return items;

                java.util.function.BiPredicate<Model.CardsLists.Card, Model.CardsLists.Card> sameCard =
                        (a, b) -> a != null && b != null
                                && ((a.getPassCode() != null && a.getPassCode().equals(b.getPassCode()))
                                || (a.getPrintCode() != null && b.getPrintCode() != null
                                && a.getPrintCode().equals(b.getPrintCode())));

                // Collect all deck/collection elements for the same card that are degraded
                List<Model.CardsLists.CardElement> degradedSlots = new ArrayList<>();
                List<String> degradedNames = new ArrayList<>();

                if (dac.getCollections() != null) {
                    for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                        if (tc == null) continue;
                        if (tc.getCardsList() != null) {
                            for (Model.CardsLists.CardElement ce : tc.getCardsList()) {
                                if (ce == null || !sameCard.test(ce.getCard(), card)) continue;
                                if (Controller.CardQualityService
                                        .isDegradedCopyInDeckOrCollection(ce, tc.getName())) {
                                    List<Model.CardsLists.CardElement> candidates =
                                            Controller.CardQualityService
                                                    .findOwnedUpgradeCandidates(ce, tc.getName());
                                    if (candidates.stream().anyMatch(c -> c == clicked)) {
                                        degradedSlots.add(ce);
                                        degradedNames.add(tc.getName());
                                    }
                                }
                            }
                        }
                        if (tc.getLinkedDecks() != null) {
                            for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                                if (unit == null) continue;
                                for (Model.CardsLists.Deck d : unit) {
                                    if (d == null) continue;
                                    for (List<Model.CardsLists.CardElement> lst :
                                            java.util.Arrays.asList(d.getMainDeck(), d.getExtraDeck(), d.getSideDeck())) {
                                        if (lst == null) continue;
                                        for (Model.CardsLists.CardElement ce : lst) {
                                            if (ce == null || !sameCard.test(ce.getCard(), card)) continue;
                                            if (Controller.CardQualityService
                                                    .isDegradedCopyInDeckOrCollection(ce, d.getName())) {
                                                List<Model.CardsLists.CardElement> candidates =
                                                        Controller.CardQualityService
                                                                .findOwnedUpgradeCandidates(ce, d.getName());
                                                if (candidates.stream().anyMatch(c -> c == clicked)) {
                                                    degradedSlots.add(ce);
                                                    degradedNames.add(d.getName());
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (dac.getDecks() != null) {
                    for (Model.CardsLists.Deck d : dac.getDecks()) {
                        if (d == null) continue;
                        for (List<Model.CardsLists.CardElement> lst :
                                java.util.Arrays.asList(d.getMainDeck(), d.getExtraDeck(), d.getSideDeck())) {
                            if (lst == null) continue;
                            for (Model.CardsLists.CardElement ce : lst) {
                                if (ce == null || !sameCard.test(ce.getCard(), card)) continue;
                                if (Controller.CardQualityService
                                        .isDegradedCopyInDeckOrCollection(ce, d.getName())) {
                                    List<Model.CardsLists.CardElement> candidates =
                                            Controller.CardQualityService
                                                    .findOwnedUpgradeCandidates(ce, d.getName());
                                    if (candidates.stream().anyMatch(c -> c == clicked)) {
                                        degradedSlots.add(ce);
                                        degradedNames.add(d.getName());
                                    }
                                }
                            }
                        }
                    }
                }

                for (int i = 0; i < degradedSlots.size(); i++) {
                    Model.CardsLists.CardElement slot = degradedSlots.get(i);
                    String name = degradedNames.get(i);
                    String label = "Swap into " + name + buildSlotConditionSuffix(slot);
                    MenuItem mi = new MenuItem(label);
                    mi.setStyle("-fx-text-fill: #EB9E34;");
                    final CardElement finalSlot = slot;
                    mi.setOnAction(ev ->
                            Controller.MenuActionHandler.handleSwap(clicked, finalSlot));
                    items.add(mi);
                }
            }
        } catch (Throwable t) {
            logger.debug("buildSwapProposals failed", t);
        }
        return items;
    }

    Image getPlaceholderImage() {
        return PlaceholderHolder.PLACEHOLDER;
    }

    /**
     * Reads the elementName from the ancestor GridView userData (best-effort).
     */
    private String elementNameFromUDForContextMenu() {
        try {
            Node n = this;
            while (n != null && !(n instanceof javafx.scene.control.TreeView)) n = n.getParent();
            // For tree cells the userData comes from the ancestor TreeView or the GridView it hosts
            // Try the GridView path (used by CardGridCell embedded in the tree)
            n = this;
            while (n != null) {
                if (n instanceof GridView) {
                    Object ud = ((GridView<?>) n).getUserData();
                    if (ud instanceof java.util.Map) {
                        Object en = ((java.util.Map<?, ?>) ud).get("elementName");
                        if (en instanceof String) return (String) en;
                    }
                }
                n = n.getParent();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private String buildSwapCandidateLabel(Model.CardsLists.CardElement ce) {
        if (ce == null) return "(unknown)";
        StringBuilder sb = new StringBuilder();
        if (ce.getCondition() != null) sb.append(ce.getCondition().getDisplayName());
        if (ce.getRarity() != null) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append(ce.getRarity().getDisplayName());
        }
        return sb.length() > 0 ? sb.toString() : "Copy in collection";
    }

    private String buildSlotConditionSuffix(Model.CardsLists.CardElement ce) {
        if (ce == null) return "";
        StringBuilder sb = new StringBuilder(" (");
        if (ce.getCondition() != null) sb.append(ce.getCondition().getDisplayName());
        if (ce.getRarity() != null) {
            if (sb.length() > 2) sb.append(" / ");
            sb.append(ce.getRarity().getDisplayName());
        }
        if (sb.length() == 2) return "";
        sb.append(")");
        return sb.toString();
    }

    /**
     * Returns true when {@code ownedElement} is a quality upgrade over at least
     * one copy of the same card already present in {@code targetList}.
     * Delegates to {@link Controller.CardQualityService#isQualityUpgrade}.
     */
    private boolean isQualityUpgradeFor(
            List<CardElement> targetList,
            CardElement ownedElement) {
        if (ownedElement == null || targetList == null) return false;
        Model.CardsLists.Card card = ownedElement.getCard();
        if (card == null) return false;
        List<CardElement> existingCopies = new ArrayList<>();
        for (CardElement ce : targetList) {
            if (ce == null || ce.getCard() == null) continue;
            if (CardMatcher.cardsMatch(ce.getCard(), card)) existingCopies.add(ce);
        }
        if (existingCopies.isEmpty()) return false;
        return Controller.CardQualityService.isQualityUpgrade(existingCopies, targetList, ownedElement);
    }

    String safeImageKey(CardElement item) {
        if (item == null) return null;
        try {
            Card c = item.getCard();
            if (c != null) return c.getImagePath();
        } catch (Exception ignored) {
        }
        try {
            String s = item.toString();
            if (s != null && !s.trim().isEmpty()) return s.trim();
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Given a local X coordinate inside a {@link GridView} cell and the cell width,
     * returns {@code true} if the point is on the right half of the card image
     * (i.e. insert AFTER), {@code false} for the left half (insert BEFORE).
     */
    public static boolean isRightHalf(double localX, double cardWidth) {
        return localX >= (cardWidth + 2 * CELL_INNER_PADDING) / 2.0;
    }

    /**
     * Finds the CardsGroup whose observable list contains the given CardElement,
     * by searching the live CardGroupRegistry.GROUP_OBSERVABLE_LISTS registry.
     */

    /**
     * Computes the insertion index inside {@code group}'s list when the user
     * drops between cards at pixel position {@code gridLocalX, gridLocalY} inside
     * the {@link GridView}.
     *
     * <p>Returns -1 when the drop position is outside the card rows entirely
     * (e.g. below the last row).
     */
    public static int computeGapInsertionIndex(
            GridView<CardElement> grid, CardsGroup group,
            double gridLocalX, double gridLocalY) {
        if (group == null || group.getCardList() == null) return -1;
        int n = group.getCardList().size();
        if (n == 0) return 0;

        Insets pad = grid.getPadding();
        double padL = pad != null ? pad.getLeft() : 0;
        double padT = pad != null ? pad.getTop() : 0;
        double hSpace = grid.getHorizontalCellSpacing();
        double vSpace = grid.getVerticalCellSpacing();
        double cellW = grid.getCellWidth() + 2 * CELL_INNER_PADDING;
        double cellH = grid.getCellHeight() + 2 * CELL_INNER_PADDING;
        int cols = computeGridColumns(grid);

        double x = gridLocalX - padL;
        double y = gridLocalY - padT;
        if (x < 0 || y < 0) return 0;

        int col = (int) Math.floor(x / (cellW + hSpace));
        int row = (int) Math.floor(y / (cellH + vSpace));

        double colFrac = (x - col * (cellW + hSpace)) / cellW;
        // If click is in the spacing gap between columns, treat as right-half of left card
        boolean inHGap = colFrac > 1.0;
        col = Math.min(col, cols - 1);

        int flatIndex = row * cols + col;
        if (flatIndex >= n) return -1; // below last row content

        // If in the right half (or in a gap), insert after this card
        if (inHGap || colFrac >= 0.5) flatIndex++;
        return Math.min(flatIndex, n);
    }

    // ── Drop execution helpers (called from drag-drop handlers) ────────────────

    /**
     * Build menu items for Type-of-Cards boxes proposals.
     * We compute candidate element names using RealMainController.computeCardNeedsSorting(card, elementName)
     * by testing existing Box and Group names in the OwnedCardsCollection.
     */
    List<MenuItem> buildTypeBoxProposals(Model.CardsLists.Card card, CardElement clickedElement) {
        List<MenuItem> items = new ArrayList<>();
        try {
            if (card == null) return items;

            Set<String> desiredFrenchCategories = new LinkedHashSet<>();
            String cardType = card.getCardType() == null ? "" : card.getCardType().trim();
            List<String> properties = card.getCardProperties();
            Set<String> propSet = new HashSet<>();
            if (properties != null) {
                for (String p : properties) if (p != null) propSet.add(p.trim());
            }

            if (cardType.toLowerCase().contains("trap")) {
                if (propSet.contains("Counter")) desiredFrenchCategories.add("Pièges Contre");
                if (propSet.contains("Continuous")) desiredFrenchCategories.add("Pièges Continus");
                if (propSet.contains("Normal")) desiredFrenchCategories.add("Pièges Normaux");
                desiredFrenchCategories.add("Pièges");
            }
            if (cardType.toLowerCase().contains("spell")) {
                if (propSet.contains("Continuous")) desiredFrenchCategories.add("Magies Continues");
                if (propSet.contains("Quick-Play") || propSet.contains("Quick Play"))
                    desiredFrenchCategories.add("Magies Jeu-Rapide");
                if (propSet.contains("Equip")) desiredFrenchCategories.add("Magies Équipement");
                if (propSet.contains("Field")) desiredFrenchCategories.add("Magies Terrain");
                if (propSet.contains("Ritual")) desiredFrenchCategories.add("Magies Rituel");
                if (propSet.contains("Normal")) desiredFrenchCategories.add("Magies Normales");
                desiredFrenchCategories.add("Magies");
            }
            if (cardType.toLowerCase().contains("monster")) {
                if (propSet.contains("Effect")) desiredFrenchCategories.add("Monstres à Effet");
                if (propSet.contains("Tuner")) desiredFrenchCategories.add("Monstres Syntoniseurs");
                if (propSet.contains("Synchro")) desiredFrenchCategories.add("Monstres Synchro");
                if (propSet.contains("Pendulum")) desiredFrenchCategories.add("Monstres Pendule");
                if (propSet.contains("Fusion")) desiredFrenchCategories.add("Monstres Fusion");
                if (propSet.contains("Xyz")) desiredFrenchCategories.add("Monstres Xyz");
                if (propSet.contains("Link")) desiredFrenchCategories.add("Monstres Lien");
                if (propSet.contains("Ritual")) desiredFrenchCategories.add("Monstres Rituel");
                if (propSet.contains("Normal")) desiredFrenchCategories.add("Monstres Normaux");
                if (propSet.contains("Toon")) desiredFrenchCategories.add("Monstres Toon");
                if (propSet.contains("Flip")) desiredFrenchCategories.add("Monstres Flip");
                if (propSet.contains("Spirit")) desiredFrenchCategories.add("Monstres Spirit");
                if (propSet.contains("Union")) desiredFrenchCategories.add("Monstres Union");
                if (propSet.contains("Gemini")) desiredFrenchCategories.add("Monstres Gemini");
                desiredFrenchCategories.add("Monstres");
            }

            if (desiredFrenchCategories.isEmpty()) return items;

            OwnedCardsCollection owned = null;
            try {
                owned = Model.CardsLists.OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
            if (owned == null) {
                try {
                    Controller.UserInterfaceFunctions.loadCollectionFile();
                } catch (Throwable ignored) {
                }
                try {
                    owned = Model.CardsLists.OuicheList.getMyCardsCollection();
                } catch (Throwable ignored) {
                }
            }
            if (owned == null || owned.getOwnedCollection() == null) return items;

            Map<String, List<String>> existingCategoryToLocations = new LinkedHashMap<>();
            for (Box box : owned.getOwnedCollection()) {
                String rawBoxName = box.getName() == null ? "" : box.getName();
                String sanitizedBox = sanitizeDisplayName(rawBoxName).toLowerCase();
                existingCategoryToLocations.computeIfAbsent(sanitizedBox, k -> new ArrayList<>()).add(rawBoxName);
                if (box.getContent() != null) {
                    for (CardsGroup g : box.getContent()) {
                        String rawGroupName = g.getName() == null ? "" : g.getName();
                        String sanitizedGroup = sanitizeDisplayName(rawGroupName).toLowerCase();
                        existingCategoryToLocations.computeIfAbsent(sanitizedGroup, k -> new ArrayList<>())
                                .add(rawBoxName + "/" + rawGroupName);
                    }
                }
            }

            for (String desired : desiredFrenchCategories) {
                if (desired == null || desired.trim().isEmpty()) continue;
                String desiredSan = sanitizeDisplayName(desired).toLowerCase();
                List<String> locations = existingCategoryToLocations.get(desiredSan);
                if (locations == null || locations.isEmpty()) continue;

                for (String rawLocation : locations) {
                    String[] parts = rawLocation.split("/", 2);
                    String boxRaw = parts.length > 0 ? parts[0] : "";
                    String groupRaw = parts.length > 1 ? parts[1] : null;

                    boolean alreadyThere = false;
                    for (Box box : owned.getOwnedCollection()) {
                        if (!sanitizeDisplayName(box.getName()).equalsIgnoreCase(sanitizeDisplayName(boxRaw))) continue;
                        if (groupRaw == null) {
                            if (box.getContent() != null) {
                                for (CardsGroup g : box.getContent()) {
                                    if (countCardInList(g.getCardList(), card) > 0) {
                                        alreadyThere = true;
                                        break;
                                    }
                                }
                            }
                        } else {
                            if (box.getContent() != null) {
                                for (CardsGroup g : box.getContent()) {
                                    if (sanitizeDisplayName(g.getName()).equalsIgnoreCase(sanitizeDisplayName(groupRaw))) {
                                        if (countCardInList(g.getCardList(), card) > 0) alreadyThere = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (alreadyThere) break;
                    }
                    if (alreadyThere) continue;

                    String displayBox = sanitizeDisplayName(boxRaw);
                    String displayTarget = groupRaw == null ? displayBox : displayBox + "/" + sanitizeDisplayName(groupRaw);
                    final String handlerTargetCopy = groupRaw == null ? displayBox : displayBox + "/" + sanitizeDisplayName(groupRaw);

                    // create MenuItem and wire the move handler
                    MenuItem mi = new MenuItem(displayTarget);
                    mi.setOnAction(ev -> MenuActionHandler.handleMove(clickedElement, handlerTargetCopy));
                    items.add(mi);
                }
            }

        } catch (Exception e) {
            logger.debug("buildTypeBoxProposals failed", e);
        }
        return items;
    }

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
     * Finds the {@link CardsGroup} that contains {@code element} by searching all
     * groups in My Collection and D&amp;C groups registered in {@link #CardGroupRegistry.DECK_SECTION_GROUPS}.
     */
    private CardsGroup getParentGroup(CardElement element) {
        if (element == null) return null;
        try {
            // Search D&C groups first (faster lookup)
            for (java.util.Map<String, CardsGroup> secs : CardGroupRegistry.DECK_SECTION_GROUPS.values()) {
                if (secs == null) continue;
                for (CardsGroup g : secs.values()) {
                    if (g != null && g.getCardList() != null && g.getCardList().contains(element))
                        return g;
                }
            }
            // Search My Collection
            Model.CardsLists.OwnedCardsCollection owned =
                    Model.CardsLists.OuicheList.getMyCardsCollection();
            if (owned != null) {
                for (Model.CardsLists.Box box : owned.getOwnedCollection()) {
                    if (box == null) continue;
                    for (CardsGroup g : box.getContent()) {
                        if (g != null && g.getCardList() != null && g.getCardList().contains(element))
                            return g;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    List<MenuItem> buildDecksAndCollectionsProposals(Model.CardsLists.Card card, CardElement clickedElement) {
        List<MenuItem> items = new ArrayList<>();
        try {
            if (card == null) return items;

            if (UserInterfaceFunctions.getDecksList() == null) {
                UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
            }
            DecksAndCollectionsList dac = UserInterfaceFunctions.getDecksList();
            if (dac == null) return items;

            OwnedCardsCollection owned = null;
            try {
                owned = Model.CardsLists.OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
            if (owned == null) {
                try {
                    Controller.UserInterfaceFunctions.loadCollectionFile();
                } catch (Throwable ignored) {
                }
                try {
                    owned = Model.CardsLists.OuicheList.getMyCardsCollection();
                } catch (Throwable ignored) {
                }
            }
            if (owned == null || owned.getOwnedCollection() == null) return items;

            OwnedCardsCollection finalOwned = owned;
            java.util.function.Predicate<String> existsLocation = (name) -> {
                if (name == null || name.trim().isEmpty()) return false;
                String targetSan = sanitizeDisplayName(name).toLowerCase();
                for (Box b : finalOwned.getOwnedCollection()) {
                    if (b == null) continue;
                    String boxSan = sanitizeDisplayName(b.getName()).toLowerCase();
                    if (boxSan.equals(targetSan)) return true;
                    if (b.getContent() != null) {
                        for (CardsGroup g : b.getContent()) {
                            if (g == null) continue;
                            String groupSan = sanitizeDisplayName(g.getName()).toLowerCase();
                            if (groupSan.equals(targetSan)) return true;
                        }
                    }
                }
                if (name.contains("/")) {
                    String[] parts = name.split("/");
                    for (String p : parts) {
                        String pSan = sanitizeDisplayName(p).toLowerCase();
                        if (pSan.isEmpty()) continue;
                        for (Box b : finalOwned.getOwnedCollection()) {
                            if (b == null) continue;
                            String boxSan = sanitizeDisplayName(b.getName()).toLowerCase();
                            if (boxSan.equals(pSan)) return true;
                            if (b.getContent() != null) {
                                for (CardsGroup g : b.getContent()) {
                                    if (g == null) continue;
                                    String groupSan = sanitizeDisplayName(g.getName()).toLowerCase();
                                    if (groupSan.equals(pSan)) return true;
                                }
                            }
                        }
                    }
                    String last = name.substring(name.lastIndexOf('/') + 1);
                    String lastSan = sanitizeDisplayName(last).toLowerCase();
                    if (!lastSan.isEmpty()) {
                        for (Box b : finalOwned.getOwnedCollection()) {
                            if (b == null) continue;
                            String boxSan = sanitizeDisplayName(b.getName()).toLowerCase();
                            if (boxSan.equals(lastSan)) return true;
                            if (b.getContent() != null) {
                                for (CardsGroup g : b.getContent()) {
                                    if (g == null) continue;
                                    String groupSan = sanitizeDisplayName(g.getName()).toLowerCase();
                                    if (groupSan.equals(lastSan)) return true;
                                }
                            }
                        }
                    }
                }
                return false;
            };

            Map<String, Boolean> proposedTargets = new LinkedHashMap<>();
            // Targets that are purely quality-upgrade proposals (no missing copies).
            // These get orange styling in the context menu.
            Set<String> upgradeOnlyTargets = new HashSet<>();

            if (dac.getCollections() != null) {
                for (ThemeCollection tc : dac.getCollections()) {
                    if (tc == null) continue;
                    int countInCollection = countCardInList(tc.toList(), card);
                    if (countInCollection <= 0) continue;

                    boolean collectionProposed = false;
                    for (Box box : owned.getOwnedCollection()) {
                        if (box == null || box.getContent() == null) continue;
                        for (CardsGroup group : box.getContent()) {
                            int countInBoxGroup = countCardInList(group.getCardList(), card);
                            boolean needsMore = countInCollection > countInBoxGroup;
                            boolean qualityUpgrade = !needsMore
                                    && isQualityUpgradeFor(tc.getCardsList(), clickedElement);
                            if (needsMore || qualityUpgrade) {
                                String target = sanitizeDisplayName(tc.getName());
                                boolean exists = existsLocation.test(tc.getName());
                                proposedTargets.put(target, exists);
                                if (qualityUpgrade) upgradeOnlyTargets.add(target);
                                collectionProposed = true;
                                break;
                            }
                        }
                        if (collectionProposed) break;
                    }

                    if (tc.getLinkedDecks() != null) {
                        for (List<Deck> unit : tc.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Deck deck : unit) {
                                if (deck == null) continue;
                                int countMain = countCardInList(deck.getMainDeck(), card);
                                int countExtra = countCardInList(deck.getExtraDeck(), card);
                                int countSide = countCardInList(deck.getSideDeck(), card);

                                // compute total required by this deck across all lists
                                int requiredTotal = countMain + countExtra + countSide;
                                if (requiredTotal > 0) {
                                    int presentTotal = countInOwnedForDeckCombined(owned, deck.getName(), card);
                                    boolean needsMore = requiredTotal > presentTotal;
                                    // Also propose when the owned copy is a quality upgrade for any sub-list
                                    boolean upgradeMain = !needsMore && countMain > 0 && isQualityUpgradeFor(deck.getMainDeck(), clickedElement);
                                    boolean upgradeExtra = !needsMore && countExtra > 0 && isQualityUpgradeFor(deck.getExtraDeck(), clickedElement);
                                    boolean upgradeSide = !needsMore && countSide > 0 && isQualityUpgradeFor(deck.getSideDeck(), clickedElement);
                                    if (needsMore || upgradeMain || upgradeExtra || upgradeSide) {
                                        if (countMain > 0
                                                && Utils.DeckCompatibility.isCompatibleWith(card, "Main Deck")
                                                && (needsMore || upgradeMain)) {
                                            String target = sanitizeDisplayName(deck.getName()) + "/Main Deck";
                                            boolean exists = existsLocation.test(deck.getName());
                                            proposedTargets.put(target, exists);
                                            if (!needsMore && upgradeMain) upgradeOnlyTargets.add(target);
                                        }
                                        if (countExtra > 0
                                                && Utils.DeckCompatibility.isCompatibleWith(card, "Extra Deck")
                                                && (needsMore || upgradeExtra)) {
                                            String target = sanitizeDisplayName(deck.getName()) + "/Extra Deck";
                                            boolean exists = existsLocation.test(deck.getName());
                                            proposedTargets.put(target, exists);
                                            if (!needsMore && upgradeExtra) upgradeOnlyTargets.add(target);
                                        }
                                        if (countSide > 0 && (needsMore || upgradeSide)) {
                                            String target = sanitizeDisplayName(deck.getName()) + "/Side Deck";
                                            boolean exists = existsLocation.test(deck.getName());
                                            proposedTargets.put(target, exists);
                                            if (!needsMore && upgradeSide) upgradeOnlyTargets.add(target);
                                        }
                                    }
                                }

                            }
                        }
                    }
                }
            }

            if (dac.getDecks() != null) {
                for (Deck deck : dac.getDecks()) {
                    if (deck == null) continue;
                    int countMain = countCardInList(deck.getMainDeck(), card);
                    int countExtra = countCardInList(deck.getExtraDeck(), card);
                    int countSide = countCardInList(deck.getSideDeck(), card);

                    // compute total required by this deck across all lists
                    int requiredTotal = countMain + countExtra + countSide;
                    if (requiredTotal > 0) {
                        int presentTotal = countInOwnedForDeckCombined(owned, deck.getName(), card);
                        boolean needsMore = requiredTotal > presentTotal;
                        // Also propose when the owned copy is a quality upgrade for any sub-list
                        boolean upgradeMain = !needsMore && countMain > 0 && isQualityUpgradeFor(deck.getMainDeck(), clickedElement);
                        boolean upgradeExtra = !needsMore && countExtra > 0 && isQualityUpgradeFor(deck.getExtraDeck(), clickedElement);
                        boolean upgradeSide = !needsMore && countSide > 0 && isQualityUpgradeFor(deck.getSideDeck(), clickedElement);
                        if (needsMore || upgradeMain || upgradeExtra || upgradeSide) {
                            if (countMain > 0
                                    && Utils.DeckCompatibility.isCompatibleWith(card, "Main Deck")
                                    && (needsMore || upgradeMain)) {
                                String target = sanitizeDisplayName(deck.getName()) + "/Main Deck";
                                boolean exists = existsLocation.test(deck.getName());
                                proposedTargets.put(target, exists);
                                if (!needsMore && upgradeMain) upgradeOnlyTargets.add(target);
                            }
                            if (countExtra > 0
                                    && Utils.DeckCompatibility.isCompatibleWith(card, "Extra Deck")
                                    && (needsMore || upgradeExtra)) {
                                String target = sanitizeDisplayName(deck.getName()) + "/Extra Deck";
                                boolean exists = existsLocation.test(deck.getName());
                                proposedTargets.put(target, exists);
                                if (!needsMore && upgradeExtra) upgradeOnlyTargets.add(target);
                            }
                            if (countSide > 0 && (needsMore || upgradeSide)) {
                                String target = sanitizeDisplayName(deck.getName()) + "/Side Deck";
                                boolean exists = existsLocation.test(deck.getName());
                                proposedTargets.put(target, exists);
                                if (!needsMore && upgradeSide) upgradeOnlyTargets.add(target);
                            }
                        }
                    }
                }
            }

            // Deduplicate by visible label to avoid multiple identical "Add ..." entries
            Set<String> labelsAdded = new HashSet<>();
            for (Map.Entry<String, Boolean> e : proposedTargets.entrySet()) {
                String rawTarget = e.getKey();
                boolean exists = e.getValue() == null ? false : e.getValue();
                if (rawTarget == null || rawTarget.trim().isEmpty()) continue;
                // handlerTarget keeps the full string (incl. /Main Deck suffix) for findDestinationGroup.
                final String handlerTarget = rawTarget;

                // Build the visible label: strip /Main Deck, /Extra Deck, /Side Deck from the display.
                String displayTarget = rawTarget;
                if (displayTarget.endsWith("/Main Deck") || displayTarget.endsWith("/Extra Deck")
                        || displayTarget.endsWith("/Side Deck")) {
                    displayTarget = displayTarget.substring(0, displayTarget.lastIndexOf('/'));
                }

                String label;
                if (exists) {
                    label = displayTarget;
                } else {
                    String baseName = displayTarget;
                    if (baseName.contains("/")) {
                        String[] parts = baseName.split("/");
                        baseName = parts[parts.length - 1];
                    }
                    label = "Add " + baseName;
                }

                if (labelsAdded.contains(label)) continue;
                labelsAdded.add(label);

                boolean isUpgradeOnly = upgradeOnlyTargets.contains(rawTarget);
                MenuItem mi = new MenuItem(label);
                mi.setStyle(isUpgradeOnly
                        ? "-fx-text-fill: #EB9E34;"
                        : "-fx-text-fill: #cdfc04;");
                if (label.startsWith("Add ")) {
                    final String catName = label.substring(4).trim();
                    mi.setOnAction(ev ->
                            MenuActionHandler.handleAddCategoryAndMove(clickedElement, catName));
                } else if (!label.startsWith("Swap")) {
                    mi.setOnAction(ev -> MenuActionHandler.handleMove(clickedElement, handlerTarget));
                }
                items.add(mi);
            }

        } catch (Exception ex) {
            logger.debug("buildDecksAndCollectionsProposals failed", ex);
        }
        return items;
    }

    /**
     * Create the visual cell for a CardsGroup.
     * <p>
     * If this group belongs to an archetype, the provided missingForThisGroup set is used.
     * If this group belongs to the "My Cards Collection" tree, compute a "to-sort" set
     * (cards that contain "Trap" in name_EN or "Piège" in name_FR) and set it as the GridView userData.
     */
    private void createCardsGroupCell(String itemName, CardsGroup group, Set<String> missingForThisGroup) {
        HBox hbox = new HBox();
        hbox.getStyleClass().add("card-group-hbox");
        hbox.setSpacing(5);

        customTriangleLabel = new Label();
        customTriangleLabel.getStyleClass().add("custom-triangle-label");
        customTriangleLabel.setMinWidth(15);
        customTriangleLabel.setAlignment(Pos.CENTER);

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
                        capturedGroup.setName(rebuildDecoratedName(raw, newName, '-'));
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

        VBox vBox = new VBox();
        vBox.getStyleClass().add("card-group-vbox");
        vBox.getChildren().add(hbox);

        GridView<CardElement> grid = new GridView<>();
        grid.getStyleClass().add("card-grid-view");
        grid.setCellFactory(gridView -> new CardGridCell(this));
        javafx.collections.ObservableList<CardElement> groupItems = observableListFor(group);
        // Wrap in a FilteredList so the active middle-pane filter can be applied without
        // touching the underlying model list (which is used by drag-drop and save).
        javafx.collections.transformation.FilteredList<CardElement> filteredItems =
                new javafx.collections.transformation.FilteredList<>(groupItems);
        if (activeMiddleFilter != null) {
            final java.util.function.Predicate<Card> captured = activeMiddleFilter;
            filteredItems.setPredicate(ce -> ce != null && ce.getCard() != null && captured.test(ce.getCard()));
        }
        CardGroupRegistry.GROUP_FILTERED_LISTS.put(group, new java.lang.ref.WeakReference<>(filteredItems));
        grid.setItems(filteredItems);
        // Register as the current renderer so triggerHeightAdjustment() can find it.
        CardGroupRegistry.GROUP_GRID_VIEWS.put(group, new java.lang.ref.WeakReference<>(grid));
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

        Map<String, Object> ud = new HashMap<>();
        ud.put("missingSet", missingForThisGroup == null ? Collections.emptySet() : missingForThisGroup);
        ud.put("elementName", displayName);
        ud.put("isArchetype", isArchetype);
        ud.put("missingArtworkSet", missingArtworkSet == null ? Collections.emptySet() : missingArtworkSet);
        grid.setUserData(ud);

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

        vBox.getChildren().add(grid);
        VBox.setVgrow(grid, Priority.ALWAYS);

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

            int insertionIndex = computeGapInsertionIndex(
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
                for (CardsGroup sg : srcGroups) markDirtyAndRefreshForGroup(sg);
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

        setGraphic(vBox);

        // For Decks & Collections tab only; different menu per group type.
        hbox.setOnContextMenuRequested(event -> {
            if (!isDecksAndCollectionsTabSelected()) {
                event.consume();
                return;
            }

            ContextMenu headerCm;

            if (isArchetype) {
                // Archetype group header  →  "Remove" (red + trash icon)
                headerCm = NavigationContextMenuBuilder.styledContextMenu();
                headerCm.getItems().add(NavigationContextMenuBuilder.makeRemoveItem());
            } else {
                // Regular group header (Cards, Main Deck, etc.) — no menu for now
                event.consume();
                return;
            }

            headerCm.show(hbox, event.getScreenX(), event.getScreenY());
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
        if (numItems <= 0) {
            applyGridPrefHeight(cardGridView, 0);
            return;
        }
        applyGridPrefHeight(cardGridView, computeGridPrefHeight(cardGridView, numItems));
        logger.debug("Adjusted grid view height to {} for {} items",
                cardGridView.getPrefHeight(), numItems);
    }
    // Add this static method to CardTreeCell, after the existing refreshAllGridViews-like statics:

    // Replace the existing inner class CardGridCell with this complete implementation
    // CardGridCell extracted to View/CardGridCell.java — instantiated via new CardGridCell(this)
}