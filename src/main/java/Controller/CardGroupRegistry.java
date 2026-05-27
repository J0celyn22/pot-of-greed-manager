package Controller;

import Model.CardsLists.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import org.controlsfx.control.GridView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * CardGroupRegistry — shared, static service layer for all live {@link CardsGroup} registries
 * that were previously scattered as static fields on {@code CardTreeCell}.
 *
 * <p>All registries use {@link java.util.WeakHashMap} (or {@link HashMap} where strong
 * references are required) so that entries are eligible for GC once the owning model object
 * is no longer referenced elsewhere in the application.</p>
 *
 * <p>Callers outside the View package that previously used
 * {@code CardTreeCell.someStaticMethod()} should be updated to call
 * {@code CardGroupRegistry.someStaticMethod()} instead.
 * {@code CardTreeCell} retains {@code @Deprecated} forwarding stubs for the transition period.</p>
 */
public final class CardGroupRegistry {

    /**
     * Prefix prepended to a {@link CardsGroup} name to mark it as an archetype group
     * (read-only in the Decks & Collections and Archetypes tabs).
     */
    public static final String ARCHETYPE_MARKER = "[ARCHETYPE]";

    // ── Sentinel / marker values ──────────────────────────────────────────────
    /**
     * Padding added on each side by the {@code StackPane} wrapper inside every
     * {@link View.CardGridCell}.  Used when computing how many columns/rows fit in a
     * {@link GridView} and when computing its preferred height.
     */
    public static final double CELL_INNER_PADDING = 5.0;
    /**
     * Maps each {@link CardsGroup} to the {@link ObservableList} that wraps its
     * backing card list and drives the corresponding {@link GridView}.
     * WeakHashMap: entries are GC'd when the CardsGroup is no longer referenced.
     */
    public static final java.util.WeakHashMap<CardsGroup, ObservableList<CardElement>>
            GROUP_OBSERVABLE_LISTS = new java.util.WeakHashMap<>();

    // ── Group-level observable-list registry ──────────────────────────────────
    /**
     * Maps each {@link CardsGroup} to a weak reference of the {@link GridView} that
     * currently renders it.  The reference becomes {@code null} when the cell is recycled.
     */
    public static final java.util.WeakHashMap<CardsGroup, WeakReference<GridView<CardElement>>>
            GROUP_GRID_VIEWS = new java.util.WeakHashMap<>();
    /**
     * Maps each {@link CardsGroup} to a weak reference of the {@link FilteredList} that
     * wraps its observable list, enabling the active middle-pane filter to be updated
     * without rebuilding the tree.
     */
    public static final java.util.WeakHashMap<CardsGroup,
            WeakReference<FilteredList<CardElement>>>
            GROUP_FILTERED_LISTS = new java.util.WeakHashMap<>();
    /**
     * Maps each {@link Deck} to its section sub-map keyed {@code "main"}, {@code "extra"},
     * or {@code "side"}.  Strong HashMap so GC never drops a Deck key mid-operation.
     */
    public static final HashMap<Deck, Map<String, CardsGroup>>
            DECK_SECTION_GROUPS = new HashMap<>();

    // ── Deck-section registry (strong map — Deck lifetime equals model lifetime) ──
    /**
     * Maps each {@link ThemeCollection} to its primary "Cards" {@link CardsGroup} so
     * paste/drop operations can route through the {@link ObservableList} rather than the
     * raw backing list.
     */
    public static final java.util.WeakHashMap<ThemeCollection, CardsGroup>
            COLLECTION_CARDS_GROUPS = new java.util.WeakHashMap<>();

    // ── ThemeCollection card-group registries ─────────────────────────────────
    /**
     * Maps each {@link ThemeCollection} to its "Cards not to add" exclusion
     * {@link CardsGroup}.
     */
    public static final java.util.WeakHashMap<ThemeCollection, CardsGroup>
            COLLECTION_EXCEPTIONS_GROUPS = new java.util.WeakHashMap<>();
    /**
     * Maps each {@link CardsGroup} to the set of pass-codes / Konami IDs for multi-artwork
     * cards whose full artwork set is not yet owned.  Populated by
     * {@code RealMainController} after building the D&C / OuicheList tree.
     */
    public static final java.util.WeakHashMap<CardsGroup, Set<String>>
            MISSING_ARTWORK_SETS = new java.util.WeakHashMap<>();

    // ── Missing-artwork registry ───────────────────────────────────────────────
    /**
     * Legacy global missing-set retained for callers that have not yet been updated to
     * use the per-group API.  The per-group sets are preferred.
     */
    public static final Set<String> LEGACY_GLOBAL_MISSING_SET = ConcurrentHashMap.newKeySet();
    private static final Logger logger = LoggerFactory.getLogger(CardGroupRegistry.class);

    // ── Active middle-pane filter ─────────────────────────────────────────────

    /**
     * Active filter predicate applied to every GridView in the middle pane.
     * {@code null} means no filtering (all cards shown).
     */
    /**
     * Active filter predicate applied to every GridView. Public so CardTreeCell can read it.
     */
    public static Predicate<Card> activeMiddleFilter = null;

    // ── Drop-event double-fire guard ──────────────────────────────────────────

    /**
     * Set to {@code true} by the {@code wrapper.setOnDragDropped} handler in
     * {@link View.CardGridCellDropHandler} when it successfully processes a drop, so
     * the enclosing {@code grid.setOnDragDropped} can detect the double-fire and bail
     * out immediately.  Always reset to {@code false} at the very start of
     * {@code wrapper.setOnDragDropped} so it is never stale across drag sessions.
     * Both handlers execute on the JavaFX Application Thread — no synchronisation needed.
     */
    public static boolean dropHandledByCell = false;

    // ── Private constructor (utility class) ───────────────────────────────────

    private CardGroupRegistry() {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Observable-list accessors
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns (or creates) the {@link ObservableList} that backs the {@link GridView} for
     * the given group.  Adding or removing through this list updates both the model and the
     * GridView automatically.
     */
    public static ObservableList<CardElement> observableListFor(CardsGroup group) {
        if (group == null) {
            return FXCollections.observableArrayList();
        }
        return GROUP_OBSERVABLE_LISTS.computeIfAbsent(group, targetGroup -> {
            List<CardElement> backing = targetGroup.getCardList();
            if (backing == null) {
                backing = new ArrayList<>();
                targetGroup.setCardList(backing);
            }
            return FXCollections.observableList(backing);
        });
    }

    /**
     * Recomputes the preferred height of the {@link GridView} currently rendering
     * {@code group}.  No-ops silently if the group's grid has been GC'd or is not visible.
     */
    public static void triggerHeightAdjustment(CardsGroup group) {
        if (group == null) {
            return;
        }
        Platform.runLater(() -> {
            WeakReference<GridView<CardElement>> gridRef = GROUP_GRID_VIEWS.get(group);
            if (gridRef == null) {
                return;
            }
            GridView<CardElement> grid = gridRef.get();
            if (grid == null) {
                GROUP_GRID_VIEWS.remove(group);
                return;
            }
            int numItems = group.getCardList() == null ? 0 : group.getCardList().size();
            adjustGridViewHeightStatic(grid, numItems);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Deck-section registry
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Registers (or replaces) the {@link CardsGroup} for a specific section of a
     * {@link Deck}.
     *
     * @param deck    the owning deck
     * @param section one of {@code "main"}, {@code "extra"}, or {@code "side"} (lower-case)
     * @param group   the CardsGroup to associate with this section
     */
    public static void registerDeckSectionGroup(Deck deck, String section, CardsGroup group) {
        if (deck == null || section == null || group == null) {
            return;
        }
        DECK_SECTION_GROUPS
                .computeIfAbsent(deck, newDeck -> new HashMap<>())
                .put(section, group);
    }

    /**
     * Returns the {@link CardsGroup} for the given section of a {@link Deck}, or
     * {@code null} if not registered.
     *
     * @param deck    the owning deck
     * @param section one of {@code "main"}, {@code "extra"}, or {@code "side"}
     */
    public static CardsGroup getDeckSectionGroup(Deck deck, String section) {
        if (deck == null || section == null) {
            return null;
        }
        Map<String, CardsGroup> sectionMap = DECK_SECTION_GROUPS.get(deck);
        return sectionMap == null ? null : sectionMap.get(section);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Collection-group registries
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Registers the primary "Cards" group for a {@link ThemeCollection}.
     */
    public static void registerCollectionCardsGroup(ThemeCollection collection, CardsGroup group) {
        if (collection != null && group != null) {
            COLLECTION_CARDS_GROUPS.put(collection, group);
        }
    }

    /**
     * Returns the primary "Cards" group for a {@link ThemeCollection}, or {@code null}.
     */
    public static CardsGroup getCollectionCardsGroup(ThemeCollection collection) {
        return collection == null ? null : COLLECTION_CARDS_GROUPS.get(collection);
    }

    /**
     * Registers the "Cards not to add" exclusion group for a {@link ThemeCollection}.
     */
    public static void registerCollectionExceptionsGroup(ThemeCollection collection, CardsGroup group) {
        if (collection != null && group != null) {
            COLLECTION_EXCEPTIONS_GROUPS.put(collection, group);
        }
    }

    /**
     * Returns the "Cards not to add" exclusion group for a {@link ThemeCollection}, or {@code null}.
     */
    public static CardsGroup getCollectionExceptionsGroup(ThemeCollection collection) {
        return collection == null ? null : COLLECTION_EXCEPTIONS_GROUPS.get(collection);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Missing-artwork registry
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Registers (or replaces) the missing-artwork set for {@code group}.
     * Called by {@code RealMainController} after computing which cards in a Collection
     * are missing at least one of their alternate artworks.
     */
    public static void setMissingArtworkSetForGroup(CardsGroup group, Set<String> artworkIdentifiers) {
        if (group != null) {
            MISSING_ARTWORK_SETS.put(group, artworkIdentifiers);
        }
    }

    /**
     * Marks a card identifier as missing (or present) in the legacy global set.
     * Prefer per-group missing sets via {@link #setMissingArtworkSetForGroup}.
     */
    public static void markArchetypeMissing(String cardIdentifier, boolean missing) {
        if (cardIdentifier == null) {
            return;
        }
        if (missing) {
            LEGACY_GLOBAL_MISSING_SET.add(cardIdentifier);
        } else {
            LEGACY_GLOBAL_MISSING_SET.remove(cardIdentifier);
        }
    }

    /**
     * Clears all entries from the legacy global missing set.
     */
    public static void clearArchetypeMissingSet() {
        LEGACY_GLOBAL_MISSING_SET.clear();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Middle-pane filter
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sets (or clears) the active filter for the middle pane.
     * Pass {@code null} to remove all filtering and restore the full card lists.
     * The change is applied immediately to every live GridView via their FilteredList.
     *
     * @param predicate a predicate on {@link Card}, or {@code null} to remove filtering
     */
    public static void setMiddleFilter(Predicate<Card> predicate) {
        activeMiddleFilter = predicate;
        Platform.runLater(CardGroupRegistry::applyFilterToAllGroups);
    }

    /**
     * Pushes the current {@link #activeMiddleFilter} to every live {@link FilteredList}
     * and recalculates each GridView's preferred height accordingly.
     */
    public static void applyFilterToAllGroups() {
        // Snapshot to avoid ConcurrentModificationException on WeakHashMap iteration.
        List<Map.Entry<CardsGroup, WeakReference<FilteredList<CardElement>>>> snapshot =
                new ArrayList<>(GROUP_FILTERED_LISTS.entrySet());

        for (Map.Entry<CardsGroup, WeakReference<FilteredList<CardElement>>> entry : snapshot) {
            CardsGroup group = entry.getKey();
            FilteredList<CardElement> filteredList = entry.getValue().get();
            if (filteredList == null) {
                GROUP_FILTERED_LISTS.remove(group);
                continue;
            }

            if (activeMiddleFilter == null) {
                filteredList.setPredicate(null);
            } else {
                final Predicate<Card> capturedFilter = activeMiddleFilter;
                filteredList.setPredicate(cardElement ->
                        cardElement != null
                                && cardElement.getCard() != null
                                && capturedFilter.test(cardElement.getCard()));
            }

            // Recompute height using the post-filter count.
            WeakReference<GridView<CardElement>> gridRef = GROUP_GRID_VIEWS.get(group);
            GridView<CardElement> grid = gridRef != null ? gridRef.get() : null;
            adjustGridViewHeightStatic(grid, filteredList.size());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Grid-view refresh
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Calls a forced refresh on every {@link GridView} currently registered in
     * {@link #GROUP_GRID_VIEWS}.  This propagates selection-state changes into nested
     * GridViews that a plain {@code TreeView.refresh()} call does not reach.
     */
    public static void refreshAllGridViews() {
        Platform.runLater(() -> {
            for (WeakReference<GridView<CardElement>> gridRef : GROUP_GRID_VIEWS.values()) {
                GridView<CardElement> grid = gridRef.get();
                if (grid != null) {
                    ObservableList<CardElement> items = grid.getItems();
                    grid.setItems(null);
                    grid.setItems(items);
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drop-insert helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Inserts {@code sourceElements} (or newly-created wrappers for {@code sourceCards})
     * into {@code targetGroup} at {@code insertionIndex}.
     *
     * <p>If {@code sourceElements} is non-null and non-empty each element is first removed
     * from its current group (MOVE semantics); otherwise new {@link CardElement} wrappers
     * are created from the provided cards (ADD semantics).</p>
     *
     * <p>Dirty marking and view refresh are the caller's responsibility.</p>
     *
     * @return the set of source groups that were modified by a MOVE operation
     */
    public static Set<CardsGroup> dropInsertIntoGroup(
            CardsGroup targetGroup,
            int insertionIndex,
            List<CardElement> sourceElements,
            List<Card> sourceCards) {

        Set<CardsGroup> modifiedSourceGroups = new LinkedHashSet<>();

        // ── Deck-compatibility redirect ───────────────────────────────────────
        // If the target is a main/extra deck section and the payload is incompatible,
        // redirect to the sibling section of the same Deck.
        // CRITICAL: when redirected, the original insertionIndex belongs to the wrong list
        // — reset to MAX_VALUE so it clamps to end-of-list on the redirect target.
        CardsGroup originalTargetGroup = targetGroup;
        targetGroup = resolveCompatibleTargetGroup(targetGroup, sourceElements, sourceCards);
        if (targetGroup != originalTargetGroup) {
            insertionIndex = Integer.MAX_VALUE;
        }
        // ──────────────────────────────────────────────────────────────────────

        ObservableList<CardElement> targetList = observableListFor(targetGroup);
        int clampedIndex = Math.max(0, Math.min(insertionIndex, targetList.size()));

        if (sourceElements != null && !sourceElements.isEmpty()) {
            // MOVE: remove elements from their current groups first.
            for (CardElement cardElement : sourceElements) {
                for (Map.Entry<CardsGroup, ObservableList<CardElement>> registryEntry
                        : GROUP_OBSERVABLE_LISTS.entrySet()) {
                    if (registryEntry.getValue().remove(cardElement)) {
                        CardsGroup sourceGroup = registryEntry.getKey();
                        triggerHeightAdjustment(sourceGroup);
                        modifiedSourceGroups.add(sourceGroup);
                        break;
                    }
                }
            }
            // Re-clamp in case removals shifted the index.
            clampedIndex = Math.min(clampedIndex, targetList.size());
            for (int elementIndex = 0; elementIndex < sourceElements.size(); elementIndex++) {
                int insertPosition = Math.min(clampedIndex + elementIndex, targetList.size());
                targetList.add(insertPosition, sourceElements.get(elementIndex));
            }
        } else if (sourceCards != null && !sourceCards.isEmpty()) {
            // ADD: create new CardElement wrappers.
            for (int cardIndex = 0; cardIndex < sourceCards.size(); cardIndex++) {
                Card card = sourceCards.get(cardIndex);
                if (card == null) {
                    continue;
                }
                int insertPosition = Math.min(clampedIndex + cardIndex, targetList.size());
                targetList.add(insertPosition, new CardElement(card));
            }
        }

        triggerHeightAdjustment(targetGroup);
        return modifiedSourceGroups;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Registry search helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Finds the {@link CardsGroup} whose observable list contains {@code targetElement},
     * by searching the live {@link #GROUP_OBSERVABLE_LISTS} registry.
     * Returns {@code null} when not found.
     */
    public static CardsGroup findGroupForCardElement(CardElement targetElement) {
        if (targetElement == null) {
            return null;
        }
        for (Map.Entry<CardsGroup, ObservableList<CardElement>> registryEntry
                : GROUP_OBSERVABLE_LISTS.entrySet()) {
            if (registryEntry.getValue().contains(targetElement)) {
                return registryEntry.getKey();
            }
        }
        return null;
    }

    /**
     * Returns the {@link Deck} that owns {@code group} via the
     * {@link #DECK_SECTION_GROUPS} registry, or {@code null} if not found.
     */
    public static Deck findDeckOwnerForGroup(CardsGroup group) {
        if (group == null) {
            return null;
        }
        for (Map.Entry<Deck, Map<String, CardsGroup>> deckEntry : DECK_SECTION_GROUPS.entrySet()) {
            if (deckEntry.getValue() != null && deckEntry.getValue().containsValue(group)) {
                return deckEntry.getKey();
            }
        }
        return null;
    }

    /**
     * Finds the dirty-markable owner ({@link ThemeCollection} or {@link Deck}) for a
     * {@link CardsGroup}.
     *
     * <p>Matching rules (matching save-file structure):</p>
     * <ul>
     *   <li>{@code ThemeCollection.cardsList} / {@code exceptionsToNotAdd} → {@code ThemeCollection}</li>
     *   <li>{@code Deck.mainDeck} / {@code extraDeck} / {@code sideDeck} → {@code Deck} (not the parent collection)</li>
     * </ul>
     *
     * <p>Uses the typed registries as the primary source (O(1)), then falls back to
     * raw-list identity comparison for any group created outside the registries.</p>
     *
     * @return the owning {@link ThemeCollection} or {@link Deck}, or {@code null} if the
     * group belongs to My Collection or cannot be found.
     */
    public static Object findOwnerForGroup(CardsGroup group) {
        if (group == null) {
            return null;
        }
        DecksAndCollectionsList dacList = UserInterfaceFunctions.getDecksList();
        if (dacList == null) {
            return null;
        }

        // ── 1. Registry lookup (O(1)) ─────────────────────────────────────────
        if (dacList.getCollections() != null) {
            for (ThemeCollection themeCollection : dacList.getCollections()) {
                if (themeCollection == null) {
                    continue;
                }
                // Collection-owned groups → mark the collection.
                if (group == getCollectionCardsGroup(themeCollection)
                        || group == getCollectionExceptionsGroup(themeCollection)) {
                    return themeCollection;
                }
                // Deck-section groups → mark the individual deck.
                if (themeCollection.getLinkedDecks() != null) {
                    for (List<Deck> deckUnit : themeCollection.getLinkedDecks()) {
                        if (deckUnit == null) {
                            continue;
                        }
                        for (Deck deck : deckUnit) {
                            if (deck == null) {
                                continue;
                            }
                            Map<String, CardsGroup> deckSections = DECK_SECTION_GROUPS.get(deck);
                            if (deckSections != null && deckSections.containsValue(group)) {
                                return deck;
                            }
                        }
                    }
                }
            }
        }
        if (dacList.getDecks() != null) {
            for (Deck deck : dacList.getDecks()) {
                if (deck == null) {
                    continue;
                }
                Map<String, CardsGroup> deckSections = DECK_SECTION_GROUPS.get(deck);
                if (deckSections != null && deckSections.containsValue(group)) {
                    return deck;
                }
            }
        }

        // ── 2. Raw-list identity fallback ─────────────────────────────────────
        // For groups created outside the registries; compares the backing List directly.
        List<CardElement> rawList = group.getCardList();
        if (rawList != null) {
            if (dacList.getCollections() != null) {
                for (ThemeCollection themeCollection : dacList.getCollections()) {
                    if (themeCollection == null) {
                        continue;
                    }
                    if (rawList == themeCollection.getCardsList()
                            || rawList == themeCollection.getExceptionsToNotAdd()) {
                        return themeCollection;
                    }
                    if (themeCollection.getLinkedDecks() != null) {
                        for (List<Deck> deckUnit : themeCollection.getLinkedDecks()) {
                            if (deckUnit == null) {
                                continue;
                            }
                            for (Deck deck : deckUnit) {
                                if (deck == null) {
                                    continue;
                                }
                                if (rawList == deck.getMainDeck()
                                        || rawList == deck.getExtraDeck()
                                        || rawList == deck.getSideDeck()) {
                                    return deck;
                                }
                            }
                        }
                    }
                }
            }
            if (dacList.getDecks() != null) {
                for (Deck deck : dacList.getDecks()) {
                    if (deck == null) {
                        continue;
                    }
                    if (rawList == deck.getMainDeck()
                            || rawList == deck.getExtraDeck()
                            || rawList == deck.getSideDeck()) {
                        return deck;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Marks the owner of {@code group} dirty and triggers the appropriate view refresh.
     * This is the preferred single entry point for post-mutation dirty marking in both
     * the My Collection and Decks &amp; Collections trees.
     */
    public static void markDirtyAndRefreshForGroup(CardsGroup group) {
        if (group == null) {
            return;
        }
        Object dacOwner = findOwnerForGroup(group);
        if (dacOwner != null) {
            UserInterfaceFunctions.markDirty(dacOwner);
            // Any card addition, removal or move in any D&C group can affect the
            // archetype missing-sets inside Collections, so always request a full tree
            // rebuild so that archetype-card glow states recompute correctly.
            UserInterfaceFunctions.setPendingDecksFullRebuild();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        } else {
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionView();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GridView height computation (package-private — used by CardTreeCell and CardGridCell)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Recomputes and applies the preferred height of {@code grid} for the given item count.
     * No-ops silently when {@code grid} is {@code null}.
     */
    public static void adjustGridViewHeightStatic(GridView<CardElement> grid, int numItems) {
        if (grid == null) {
            return;
        }
        if (numItems <= 0) {
            applyGridPrefHeight(grid, 0);
            return;
        }
        applyGridPrefHeight(grid, computeGridPrefHeight(grid, numItems));
    }

    /**
     * Computes the correct preferred height for a GridView given its current width and
     * the number of items to display.  Uses the actual rendered cell dimensions
     * (card dimension + 2 × {@link #CELL_INNER_PADDING}) to determine row count.
     */
    public static double computeGridPrefHeight(GridView<CardElement> grid, int numItems) {
        if (numItems <= 0) {
            return 0;
        }
        int columnCount = computeGridColumns(grid);
        int rowCount = (int) Math.ceil((double) numItems / columnCount);
        Insets padding = grid.getPadding();
        double paddingTop = (padding != null) ? padding.getTop() : 0;
        double paddingBottom = (padding != null) ? padding.getBottom() : 0;
        double cellHeight = grid.getCellHeight();
        double verticalSpacing = grid.getVerticalCellSpacing();
        double actualCellHeight = cellHeight + 2 * CELL_INNER_PADDING;
        double rowSpan = actualCellHeight + verticalSpacing;
        return paddingTop + paddingBottom + rowCount * rowSpan + 1.0;
    }

    /**
     * Computes the number of columns that fit inside the current width of {@code grid}.
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
        double horizontalPadding = (padding != null) ? padding.getLeft() + padding.getRight() : 0;
        double innerWidth = totalWidth - horizontalPadding;
        double cardWidth = grid.getCellWidth();
        double horizontalSpacing = grid.getHorizontalCellSpacing();
        double actualCellWidth = cardWidth + 2 * CELL_INNER_PADDING;
        return (int) Math.max(1,
                Math.floor((innerWidth + horizontalSpacing) / (actualCellWidth + horizontalSpacing)));
    }

    /**
     * Applies the given height as the preferred, minimum, and maximum height of the grid.
     */
    public static void applyGridPrefHeight(GridView<CardElement> grid, double height) {
        grid.setPrefHeight(height);
        grid.setMinHeight(height);
        grid.setMaxHeight(height);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * If {@code targetGroup} is a Deck's main or extra deck section and the payload is
     * incompatible (e.g. an extra-deck card dropped onto the Main Deck), returns the
     * sibling section's group for the same Deck.  Falls back to {@code targetGroup}
     * unchanged when redirection is not needed or not possible.
     */
    private static CardsGroup resolveCompatibleTargetGroup(
            CardsGroup targetGroup,
            List<CardElement> sourceElements,
            List<Card> sourceCards) {

        if (targetGroup == null) {
            return null;
        }
        String groupName = targetGroup.getName();
        if (groupName == null) {
            return targetGroup;
        }

        boolean isMainSection = Utils.DeckCompatibility.isMainDeckSection(groupName);
        boolean isExtraSection = Utils.DeckCompatibility.isExtraDeckSection(groupName);
        if (!isMainSection && !isExtraSection) {
            // Side deck or other section — no deck-type restriction applies.
            return targetGroup;
        }

        // Determine the representative card from the payload.
        Card sampleCard = null;
        if (sourceElements != null) {
            for (CardElement cardElement : sourceElements) {
                if (cardElement != null && cardElement.getCard() != null) {
                    sampleCard = cardElement.getCard();
                    break;
                }
            }
        }
        if (sampleCard == null && sourceCards != null) {
            for (Card card : sourceCards) {
                if (card != null) {
                    sampleCard = card;
                    break;
                }
            }
        }
        if (sampleCard == null) {
            return targetGroup;
        }

        String redirectSectionName = Utils.DeckCompatibility.redirectSection(sampleCard, groupName);
        if (redirectSectionName == null) {
            // Card is already compatible with the current section.
            return targetGroup;
        }

        Deck ownerDeck = findDeckOwnerForGroup(targetGroup);
        if (ownerDeck == null) {
            return targetGroup;
        }

        // Map the redirect section display-name to the registry key ("main" or "extra").
        String redirectKey = redirectSectionName.toLowerCase(java.util.Locale.ROOT)
                .replace(" deck", "").trim();
        CardsGroup redirectGroup = getDeckSectionGroup(ownerDeck, redirectKey);
        return redirectGroup != null ? redirectGroup : targetGroup;
    }
}