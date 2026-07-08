package Controller;

import Model.CardsLists.*;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
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
 * {@code CardGroupRegistry.someStaticMethod()} instead.</p>
 */
public final class CardGroupRegistry {

    /**
     * Prefix prepended to a {@link CardsGroup} name to mark it as an archetype group
     * (read-only in the Decks & Collections and Archetypes tabs).
     */
    public static final String ARCHETYPE_MARKER = "[ARCHETYPE]";

    // ── Sentinel / marker values ──────────────────────────────────────────────
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
     * Active filter predicate applied to every GridView. Public so View classes can read it.
     */
    public static Predicate<Card> activeMiddleFilter = null;

    /**
     * Optional element-level filter ANDed into every {@link FilteredList} predicate.
     * Unlike {@link #activeMiddleFilter} (which operates on {@link Card}), this predicate
     * receives the individual {@link CardElement} so per-copy fields such as
     * {@code customTags} can be tested precisely. {@code null} means inactive (pass all
     * elements).
     */
    public static Predicate<CardElement> activeMiddleElementFilter = null;

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

    /**
     * Returns the existing {@link CardsGroup} for {@code deck}/{@code section} if its backing
     * list is still the same object as {@code backingList}, otherwise constructs a fresh one,
     * registers it, and returns it.
     *
     * <p>{@link #GROUP_OBSERVABLE_LISTS} is keyed by {@link CardsGroup} <em>wrapper</em> identity.
     * {@code DecksCollectionsController} rebuilds its whole tree on every edit, constructing new
     * wrapper instances around the same never-recreated backing lists. If two different wrappers
     * ever cover the same list, {@link #observableListFor} hands out two independent
     * {@link ObservableList} facades; additions through either facade still mutate the shared data
     * (model and OuicheList stay correct) but listeners on one facade are never notified of
     * mutations through the other, so the GridView bound to one facade silently stops reflecting
     * changes. Reusing the same wrapper eliminates that split entirely.</p>
     */
    public static CardsGroup getOrCreateDeckSectionGroup(
            Deck deck, String section, String displayLabel, List<CardElement> backingList) {
        CardsGroup existing = getDeckSectionGroup(deck, section);
        if (existing != null && existing.getCardList() == backingList) {
            return existing;
        }
        CardsGroup fresh = new CardsGroup(displayLabel, backingList);
        registerDeckSectionGroup(deck, section, fresh);
        return fresh;
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
     * Returns the existing "Cards" {@link CardsGroup} for {@code collection} if its backing
     * list is still the same object as {@code backingList}, otherwise constructs and registers
     * a fresh one. See {@link #getOrCreateDeckSectionGroup} for the rationale.
     */
    public static CardsGroup getOrCreateCollectionCardsGroup(
            ThemeCollection collection, List<CardElement> backingList) {
        CardsGroup existing = getCollectionCardsGroup(collection);
        if (existing != null && existing.getCardList() == backingList) {
            return existing;
        }
        CardsGroup fresh = new CardsGroup("Cards", backingList);
        registerCollectionCardsGroup(collection, fresh);
        return fresh;
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

    /**
     * Returns the existing "Cards not to add" {@link CardsGroup} for {@code collection} if its
     * backing list is still the same object as {@code backingList}, otherwise constructs and
     * registers a fresh one. See {@link #getOrCreateDeckSectionGroup} for the rationale.
     */
    public static CardsGroup getOrCreateCollectionExceptionsGroup(
            ThemeCollection collection, List<CardElement> backingList) {
        CardsGroup existing = getCollectionExceptionsGroup(collection);
        if (existing != null && existing.getCardList() == backingList) {
            return existing;
        }
        CardsGroup fresh = new CardsGroup("Cards not to add", backingList);
        registerCollectionExceptionsGroup(collection, fresh);
        return fresh;
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
     * Registers (or clears) a {@link CardElement}-level filter for the middle pane.
     *
     * <p>This predicate is ANDed with the card-level {@link #activeMiddleFilter} inside
     * {@link #buildCombinedPredicate}, allowing per-copy fields (e.g. {@code customTags})
     * to be tested on individual elements rather than on the shared {@link Card} object.
     *
     * @param predicate a predicate on {@link CardElement}, or {@code null} to remove the filter
     */
    public static void setMiddleElementFilter(Predicate<CardElement> predicate) {
        activeMiddleElementFilter = predicate;
        Platform.runLater(CardGroupRegistry::applyFilterToAllGroups);
    }

    /**
     * Pushes the current {@link #activeMiddleFilter} to every live {@link FilteredList} and
     * recalculates each GridView's preferred height accordingly.
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

            filteredList.setPredicate(buildCombinedPredicate(activeMiddleFilter));

            // Recompute height using the post-filter count.
            WeakReference<GridView<CardElement>> gridRef = GROUP_GRID_VIEWS.get(group);
            GridView<CardElement> grid = gridRef != null ? gridRef.get() : null;
            adjustGridViewHeightStatic(grid, filteredList.size());
        }
    }

    /**
     * Builds a combined {@link Predicate} for the middle-pane {@link FilteredList} of a
     * cards group.
     *
     * <p>The predicate ANDs two independent concerns:
     * <ol>
     *   <li>The active middle-pane card filter (may be {@code null} = pass all).</li>
     *   <li>When "Hide owned cards" is active in the OuicheList, {@link CardElement}s
     *       whose {@link CardElement#getOwnershipStatus()} is {@code OWNED} are excluded so
     *       they occupy zero space in the {@link GridView} — not just rendered invisible.</li>
     * </ol>
     *
     * @param middleFilter the currently active card filter, or {@code null} for none
     * @return a predicate to pass to {@link FilteredList#setPredicate}, or {@code null}
     * when neither filter is active (show everything)
     */
    public static Predicate<CardElement> buildCombinedPredicate(Predicate<Card> middleFilter) {
        boolean hideOwned = OuicheListController.isHideOwnedCardsEnabled();

        if (!hideOwned && middleFilter == null && activeMiddleElementFilter == null) {
            return null; // fastest path — no filtering at all
        }

        final Predicate<Card> capturedFilter = middleFilter;
        final Predicate<CardElement> capturedElementFilter = activeMiddleElementFilter;

        return cardElement -> {
            if (cardElement == null) {
                return false;
            }
            if (hideOwned && OwnershipStatus.OWNED.equals(cardElement.getOwnershipStatus())) {
                return false;
            }
            if (capturedFilter != null) {
                if (cardElement.getCard() == null || !capturedFilter.test(cardElement.getCard())) {
                    return false;
                }
            }
            if (capturedElementFilter != null) {
                return capturedElementFilter.test(cardElement);
            }
            return true;
        };
    }

    /**
     * Re-applies the combined predicate (hide-owned + active middle filter) to every live
     * {@link FilteredList} and recalculates each GridView's preferred height.
     *
     * <p>Called by {@link OuicheListController} after toggling the "Hide owned cards" flag
     * so that owned-card slots are removed or restored without rebuilding the whole tree.
     */
    public static void applyHideOwnedToAllGroups() {
        Platform.runLater(CardGroupRegistry::applyFilterToAllGroups);
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
        return dropInsertIntoGroup(targetGroup, insertionIndex, sourceElements, sourceCards, null);
    }

    /**
     * Same as {@link #dropInsertIntoGroup(CardsGroup, int, List, List)}, but also exposes
     * the freshly-created {@link CardElement} wrappers from the ADD-semantics branch (in the
     * same order as {@code sourceCards}, skipping any {@code null} entries) to callers that
     * need to act on exactly those elements afterward — e.g. opening an edit popup for
     * whichever of them still lack a printCode. Insertion position within the target list
     * depends on where the drop landed (a specific card, an empty group, etc.), so the newly
     * added elements are not reliably at the tail of the list; passing them back directly
     * avoids callers having to guess.
     *
     * @param outNewlyAddedElements if non-{@code null}, the newly-created elements from the
     *                              ADD branch are appended to this list; unused for MOVE
     * @return the set of source groups that were modified by a MOVE operation
     */
    public static Set<CardsGroup> dropInsertIntoGroup(
            CardsGroup targetGroup,
            int insertionIndex,
            List<CardElement> sourceElements,
            List<Card> sourceCards,
            List<CardElement> outNewlyAddedElements) {

        Set<CardsGroup> modifiedSourceGroups = new LinkedHashSet<>();
        if (targetGroup == null) {
            return modifiedSourceGroups;
        }

        // ── Deck-compatibility split ───────────────────────────────────────────
        // If the target is a main/extra deck section, split the payload per card/element:
        // compatible ones are inserted at the requested position in targetGroup, incompatible
        // ones are redirected to the sibling section of the same Deck. Unlike a whole-batch
        // redirect decision, this keeps every card correct regardless of what else is in the
        // same drag/drop payload.
        String targetGroupName = targetGroup.getName();
        boolean targetIsMainOrExtra = Utils.DeckCompatibility.isMainDeckSection(targetGroupName)
                || Utils.DeckCompatibility.isExtraDeckSection(targetGroupName);

        List<CardElement> compatibleElements = sourceElements;
        List<CardElement> incompatibleElements = new ArrayList<>();
        List<Card> compatibleCards = sourceCards;
        List<Card> incompatibleCards = new ArrayList<>();

        if (targetIsMainOrExtra && sourceElements != null) {
            compatibleElements = new ArrayList<>();
            for (CardElement cardElement : sourceElements) {
                if (cardElement != null && cardElement.getCard() != null
                        && !Utils.DeckCompatibility.isCompatibleWith(cardElement.getCard(), targetGroupName)) {
                    incompatibleElements.add(cardElement);
                } else {
                    compatibleElements.add(cardElement);
                }
            }
        } else if (targetIsMainOrExtra && sourceCards != null) {
            compatibleCards = new ArrayList<>();
            for (Card card : sourceCards) {
                if (card != null
                        && !Utils.DeckCompatibility.isCompatibleWith(card, targetGroupName)) {
                    incompatibleCards.add(card);
                } else {
                    compatibleCards.add(card);
                }
            }
        }
        // ────────────────────────────────────────────────────────────────────────

        insertIntoGroupTrackingSources(targetGroup, insertionIndex, compatibleElements, compatibleCards,
                outNewlyAddedElements, modifiedSourceGroups);

        if (!incompatibleElements.isEmpty() || !incompatibleCards.isEmpty()) {
            Card sampleIncompatibleCard = !incompatibleElements.isEmpty()
                    ? incompatibleElements.get(0).getCard()
                    : incompatibleCards.get(0);
            String redirectSectionName =
                    Utils.DeckCompatibility.redirectSection(sampleIncompatibleCard, targetGroupName);
            Deck ownerDeck = findDeckOwnerForGroup(targetGroup);
            CardsGroup redirectGroup = null;
            if (redirectSectionName != null && ownerDeck != null) {
                String redirectKey = redirectSectionName.toLowerCase(java.util.Locale.ROOT)
                        .replace(" deck", "").trim();
                redirectGroup = getDeckSectionGroup(ownerDeck, redirectKey);
            }
            if (redirectGroup != null) {
                insertIntoGroupTrackingSources(redirectGroup, Integer.MAX_VALUE, incompatibleElements,
                        incompatibleCards, outNewlyAddedElements, modifiedSourceGroups);
            }
        }

        return modifiedSourceGroups;
    }

    /**
     * Inserts {@code elementsToInsert} (MOVE semantics) or {@code cardsToInsert} (ADD semantics,
     * mutually exclusive with {@code elementsToInsert}) into {@code group} at {@code
     * insertionIndex}, removing MOVE elements from their current groups first. Shared by both the
     * primary insertion and the sibling-section redirect in {@link #dropInsertIntoGroup}.
     */
    private static void insertIntoGroupTrackingSources(
            CardsGroup group,
            int insertionIndex,
            List<CardElement> elementsToInsert,
            List<Card> cardsToInsert,
            List<CardElement> outNewlyAddedElements,
            Set<CardsGroup> modifiedSourceGroups) {

        ObservableList<CardElement> targetList = observableListFor(group);
        int clampedIndex = Math.max(0, Math.min(insertionIndex, targetList.size()));

        if (elementsToInsert != null && !elementsToInsert.isEmpty()) {
            // MOVE: remove elements from their current groups first.
            for (CardElement cardElement : elementsToInsert) {
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
            for (int elementIndex = 0; elementIndex < elementsToInsert.size(); elementIndex++) {
                int insertPosition = Math.min(clampedIndex + elementIndex, targetList.size());
                targetList.add(insertPosition, elementsToInsert.get(elementIndex));
            }
        } else if (cardsToInsert != null && !cardsToInsert.isEmpty()) {
            // ADD: create new CardElement wrappers.
            List<CardElement> newlyAddedElements = new ArrayList<>();
            for (int cardIndex = 0; cardIndex < cardsToInsert.size(); cardIndex++) {
                Card card = cardsToInsert.get(cardIndex);
                if (card == null) {
                    continue;
                }
                int insertPosition = Math.min(clampedIndex + cardIndex, targetList.size());
                CardElement newElement = new CardElement(card);
                targetList.add(insertPosition, newElement);
                newlyAddedElements.add(newElement);
            }
            notifyOuicheListOfGroupAdditions(group, newlyAddedElements);
            if (outNewlyAddedElements != null) {
                outNewlyAddedElements.addAll(newlyAddedElements);
            }
        }

        triggerHeightAdjustment(group);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OuicheList incremental-update notification
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Notifies the OuicheList of cards just added to {@code targetGroup}, routing to the
     * correct {@link OuicheList} dispatch method based on what {@code targetGroup}
     * represents: a deck section ({@link #DECK_SECTION_GROUPS}), a collection's cardsList
     * ({@link #COLLECTION_CARDS_GROUPS}), or — if neither registry has a match — the owned
     * collection (My Collection tab).
     *
     * <p>Shared by every drop/paste/add path that inserts new {@link CardElement}s into a
     * {@link CardsGroup} via this registry, so the OuicheList always reflects manual
     * additions regardless of which UI gesture (gap-drop, cell-drop, context menu, paste)
     * performed the insertion. Each element's actual index within {@code targetGroup}'s
     * backing list at call time is used as its insertion position in the detailed
     * OuicheList's matching section, so ordering mirrors the live Decks and Collections
     * list rather than always appending.
     *
     * @param targetGroup   the group new elements were added to
     * @param addedElements the newly-created {@link CardElement}s (not pre-existing moves)
     */
    public static void notifyOuicheListOfGroupAdditions(
            CardsGroup targetGroup, List<CardElement> addedElements) {
        if (targetGroup == null || addedElements == null || addedElements.isEmpty()) {
            return;
        }

        // Resolve the owning Deck/ThemeCollection via findOwnerForGroup(), which compares
        // the *backing* List<CardElement> identity (deck.getMainDeck(), etc. — never
        // recreated for the lifetime of the model object) rather than the CardsGroup wrapper
        // reference. The wrapper is recreated on every Decks & Collections tree rebuild
        // (triggered by markDirtyAndRefreshForGroup after every drop/paste/add), so a
        // targetGroup captured by an in-flight gesture can be a stale wrapper around the
        // correct, still-live backing list.
        List<CardElement> backingList = targetGroup.getCardList();
        Object owner = findOwnerForGroup(targetGroup);

        if (owner instanceof Deck deck) {
            String sectionKey = deckSectionKeyForBackingList(deck, backingList);
            if (sectionKey != null) {
                String collectionName = findCollectionNameOwningDeck(deck);
                for (CardElement addedElement : addedElements) {
                    try {
                        int insertionIndex = backingList.indexOf(addedElement);
                        if (insertionIndex < 0) {
                            insertionIndex = Integer.MAX_VALUE;
                        }
                        OuicheList.onDeckCardAdded(
                                addedElement, deck.getName(), sectionKey, collectionName, insertionIndex);
                    } catch (Throwable throwable) {
                        logger.error("OuicheList update failed after adding to deck '{}' section '{}'",
                                deck.getName(), sectionKey, throwable);
                    }
                }
                Controller.UserInterfaceFunctions.refreshOuicheListView();
                return;
            }
        } else if (owner instanceof ThemeCollection collection
                && backingList != null && backingList == collection.getCardsList()) {
            for (CardElement addedElement : addedElements) {
                try {
                    int insertionIndex = backingList.indexOf(addedElement);
                    if (insertionIndex < 0) {
                        insertionIndex = Integer.MAX_VALUE;
                    }
                    OuicheList.onDeckCardAdded(addedElement, null, null, collection.getName(), insertionIndex);
                } catch (Throwable throwable) {
                    logger.error("OuicheList update failed after adding to collection '{}'",
                            collection.getName(), throwable);
                }
            }
            Controller.UserInterfaceFunctions.refreshOuicheListView();
            return;
        }

        // Otherwise (a My Collection group, or a non-cardsList collection section):
        // treat as an owned-card addition.
        for (CardElement addedElement : addedElements) {
            try {
                OuicheList.onOwnedCardAdded(addedElement);
            } catch (Throwable throwable) {
                logger.error("OuicheList update failed after adding to owned collection group", throwable);
            }
        }
        Controller.UserInterfaceFunctions.refreshOuicheListView();
    }

    /**
     * Notifies the OuicheList of cards just removed from {@code sourceGroup} as the
     * "remove" half of a cross-group MOVE gesture. Routing is symmetric to
     * {@link #notifyOuicheListOfGroupAdditions}: deck-section groups dispatch to
     * {@link OuicheList#onDeckCardRemoved}, collection-cardsList groups dispatch to
     * {@link OuicheList#onDeckCardRemoved} with {@code deckName=null}, and unrecognised
     * groups (My Collection) dispatch to {@link OuicheList#onOwnedCardRemoved}.
     *
     * <p>Only call this for cross-group moves ({@code sourceGroup != targetGroup}). For
     * intra-group reorders (pure position changes within a single group), use
     * {@link #notifyOuicheListOfGroupReorder} instead.</p>
     *
     * @param sourceGroup     the group the elements were moved out of
     * @param removedElements the {@link CardElement}s that were removed
     */
    public static void notifyOuicheListOfGroupRemovals(
            CardsGroup sourceGroup, List<CardElement> removedElements) {
        if (sourceGroup == null || removedElements == null || removedElements.isEmpty()) {
            return;
        }

        List<CardElement> backingList = sourceGroup.getCardList();
        Object owner = findOwnerForGroup(sourceGroup);

        if (owner instanceof Deck deck) {
            String sectionKey = deckSectionKeyForBackingList(deck, backingList);
            if (sectionKey != null) {
                String collectionName = findCollectionNameOwningDeck(deck);
                for (CardElement removedElement : removedElements) {
                    try {
                        OuicheList.onDeckCardRemoved(
                                removedElement, deck.getName(), sectionKey, collectionName);
                    } catch (Throwable throwable) {
                        logger.error("OuicheList update failed after removing from deck '{}' section '{}'",
                                deck.getName(), sectionKey, throwable);
                    }
                }
                Controller.UserInterfaceFunctions.refreshOuicheListView();
                return;
            }
        } else if (owner instanceof ThemeCollection collection
                && backingList != null && backingList == collection.getCardsList()) {
            for (CardElement removedElement : removedElements) {
                try {
                    OuicheList.onDeckCardRemoved(removedElement, null, null, collection.getName());
                } catch (Throwable throwable) {
                    logger.error("OuicheList update failed after removing from collection '{}'",
                            collection.getName(), throwable);
                }
            }
            Controller.UserInterfaceFunctions.refreshOuicheListView();
            return;
        }

        // Otherwise (a My Collection group): treat as an owned-card removal.
        for (CardElement removedElement : removedElements) {
            try {
                OuicheList.onOwnedCardRemoved(removedElement);
            } catch (Throwable throwable) {
                logger.error("OuicheList update failed after removing from owned collection group",
                        throwable);
            }
        }
        Controller.UserInterfaceFunctions.refreshOuicheListView();
    }

    /**
     * Notifies the OuicheList of cards repositioned within {@code group} (an intra-group
     * reorder — no card left or entered the group, only display order changed).
     *
     * <p>Routing mirrors {@link #notifyOuicheListOfGroupAdditions}: deck-section groups and
     * collection-cardsList groups dispatch to {@link OuicheList#onDeckCardMoved}, which
     * relocates the matching slot within the detailed OuicheList's corresponding section to
     * the element's new index, preserving that slot's ownership status untouched. Unrecognised
     * groups (My Collection) are a no-op — the OuicheList has no position-sensitive
     * representation of the owned-card pool.
     *
     * <p>Only call this for pure intra-group reorders ({@code sourceGroup == targetGroup} for
     * every moved element). Cross-group moves must go through
     * {@link #notifyOuicheListOfGroupRemovals} + {@link #notifyOuicheListOfGroupAdditions}
     * instead.
     *
     * @param group         the group elements were repositioned within
     * @param movedElements the {@link CardElement}s that were repositioned
     */
    public static void notifyOuicheListOfGroupReorder(
            CardsGroup group, List<CardElement> movedElements) {
        if (group == null || movedElements == null || movedElements.isEmpty()) {
            return;
        }

        List<CardElement> backingList = group.getCardList();
        Object owner = findOwnerForGroup(group);

        if (owner instanceof Deck deck) {
            String sectionKey = deckSectionKeyForBackingList(deck, backingList);
            if (sectionKey != null) {
                String collectionName = findCollectionNameOwningDeck(deck);
                for (CardElement movedElement : movedElements) {
                    try {
                        int newIndex = backingList.indexOf(movedElement);
                        if (newIndex < 0) {
                            continue;
                        }
                        OuicheList.onDeckCardMoved(
                                movedElement, deck.getName(), sectionKey, collectionName, newIndex);
                    } catch (Throwable throwable) {
                        logger.error("OuicheList update failed after reordering deck '{}' section '{}'",
                                deck.getName(), sectionKey, throwable);
                    }
                }
                Controller.UserInterfaceFunctions.refreshOuicheListView();
            }
            return;
        } else if (owner instanceof ThemeCollection collection
                && backingList != null && backingList == collection.getCardsList()) {
            for (CardElement movedElement : movedElements) {
                try {
                    int newIndex = backingList.indexOf(movedElement);
                    if (newIndex < 0) {
                        continue;
                    }
                    OuicheList.onDeckCardMoved(movedElement, null, null, collection.getName(), newIndex);
                } catch (Throwable throwable) {
                    logger.error("OuicheList update failed after reordering collection '{}'",
                            collection.getName(), throwable);
                }
            }
            Controller.UserInterfaceFunctions.refreshOuicheListView();
            return;
        }

        // Otherwise (a My Collection group): the OuicheList has no position-sensitive
        // representation of the owned-card pool, so there is nothing to update.
    }

    /**
     * Returns {@code "main"}, {@code "extra"}, or {@code "side"} when {@code backingList}
     * is the same object (by reference) as the corresponding section list of {@code deck},
     * or {@code null} if it matches none of them.
     */
    private static String deckSectionKeyForBackingList(Deck deck, List<CardElement> backingList) {
        if (deck == null || backingList == null) {
            return null;
        }
        if (backingList == deck.getMainDeck()) {
            return "main";
        }
        if (backingList == deck.getExtraDeck()) {
            return "extra";
        }
        if (backingList == deck.getSideDeck()) {
            return "side";
        }
        return null;
    }

    /**
     * Finds the name of the {@link ThemeCollection} that owns {@code deck} via
     * {@link ThemeCollection#getLinkedDecks()}, or {@code null} for a standalone deck.
     */
    private static String findCollectionNameOwningDeck(Deck deck) {
        if (deck == null) {
            return null;
        }
        for (ThemeCollection collection : COLLECTION_CARDS_GROUPS.keySet()) {
            if (collection == null || collection.getLinkedDecks() == null) {
                continue;
            }
            for (List<Deck> unit : collection.getLinkedDecks()) {
                if (unit == null) {
                    continue;
                }
                for (Deck linkedDeck : unit) {
                    if (linkedDeck == deck) {
                        return collection.getName();
                    }
                }
            }
        }
        return null;
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
        return findOwnerForGroup(group, UserInterfaceFunctions.getDecksList());
    }

    /**
     * Same as {@link #findOwnerForGroup(CardsGroup)}, but searches a caller-supplied
     * {@link DecksAndCollectionsList} instead of always using the live Decks &amp; Collections
     * model. Used to resolve ownership within the OuicheList tab's own detailed (ephemeral)
     * copy of the deck/collection structure — see {@link #resolveRealGroupForOuicheListGroup}.
     *
     * @param group   the group whose owner to find
     * @param dacList the deck/collection list to search
     */
    public static Object findOwnerForGroup(CardsGroup group, DecksAndCollectionsList dacList) {
        if (group == null || dacList == null) {
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
     * Resolves the corresponding <b>real</b> Decks &amp; Collections {@link CardsGroup} for a
     * {@link CardsGroup} that belongs to the OuicheList tab's ephemeral detailed-list copy
     * ({@link OuicheList#getDetailedOuicheList()}).
     *
     * <p>The OuicheList tab renders its own deep-copied {@link Deck}/{@link ThemeCollection}
     * structure so that per-slot ownership status can be annotated without touching the real
     * model. Drag-and-drop performed on that tab must instead mutate the real Decks &amp;
     * Collections model; this resolves the ephemeral group to its real, by-name counterpart so
     * the existing drop/notify pipeline can operate on it unchanged, which in turn re-syncs the
     * OuicheList's own display automatically.</p>
     *
     * @param group a {@link CardsGroup} as encountered anywhere in the UI
     * @return the matching real {@link CardsGroup}, or {@code null} if {@code group} is not
     * part of the OuicheList's ephemeral copy, or no real Deck/ThemeCollection with a matching
     * name can be found
     */
    public static CardsGroup resolveRealGroupForOuicheListGroup(CardsGroup group) {
        if (group == null) {
            return null;
        }
        DecksAndCollectionsList ephemeralList = OuicheList.getDetailedOuicheList();
        if (ephemeralList == null) {
            return null;
        }
        Object ephemeralOwner = findOwnerForGroup(group, ephemeralList);
        if (ephemeralOwner == null) {
            return null;
        }
        DecksAndCollectionsList realList = UserInterfaceFunctions.getDecksList();
        if (realList == null) {
            return null;
        }

        if (ephemeralOwner instanceof Deck ephemeralDeck) {
            String sectionKey = deckSectionKeyForBackingList(ephemeralDeck, group.getCardList());
            if (sectionKey == null) {
                return null;
            }
            String collectionName = findCollectionNameOwningDeck(ephemeralDeck);
            Deck realDeck = realList.findDeckByName(ephemeralDeck.getName(), collectionName);
            if (realDeck == null) {
                return null;
            }
            List<CardElement> realSectionList;
            String sectionLabel;
            if ("main".equals(sectionKey)) {
                realSectionList = realDeck.getMainDeck();
                sectionLabel = "Main Deck";
            } else if ("extra".equals(sectionKey)) {
                realSectionList = realDeck.getExtraDeck();
                sectionLabel = "Extra Deck";
            } else {
                realSectionList = realDeck.getSideDeck();
                sectionLabel = "Side Deck";
            }
            if (realSectionList == null) {
                return null;
            }
            CardsGroup realDeckSectionGroup =
                    getOrCreateDeckSectionGroup(realDeck, sectionKey, sectionLabel, realSectionList);
            // dropInsertIntoGroup's MOVE branch locates an element's current group by
            // scanning GROUP_OBSERVABLE_LISTS only — a group that has never been the target
            // of a drop, nor built by the Decks & Collections tab, has no entry there yet.
            // Force registration now so a group resolved solely through this OuicheList
            // redirect (source or target) is just as discoverable as one built the normal
            // way; otherwise a MOVE whose *source* is such a group silently fails to remove
            // the element (duplicating it at the target instead of relocating it).
            observableListFor(realDeckSectionGroup);
            return realDeckSectionGroup;
        }

        if (ephemeralOwner instanceof ThemeCollection ephemeralCollection
                && group.getCardList() == ephemeralCollection.getCardsList()) {
            ThemeCollection realCollection = realList.findCollectionByName(ephemeralCollection.getName());
            if (realCollection == null) {
                return null;
            }
            CardsGroup realCollectionCardsGroup =
                    getOrCreateCollectionCardsGroup(realCollection, realCollection.getCardsList());
            observableListFor(realCollectionCardsGroup);
            return realCollectionCardsGroup;
        }

        return null;
    }

    /**
     * Resolves the real {@link CardElement} that corresponds to {@code ephemeralElement}, an
     * element belonging to the OuicheList tab's ephemeral detailed-list copy.
     *
     * <p>The OuicheList's per-section card lists are index-parallel to the real Decks &amp;
     * Collections lists: {@code OuicheListComputer} only annotates ownership status in place
     * and never removes or reorders elements when computing the detailed list, and every
     * subsequent incremental update keeps both sides' ordering in sync. The element at the
     * same index in the resolved real group's backing list is therefore the real
     * counterpart.</p>
     *
     * @param ephemeralElement an element as dragged from the OuicheList tab
     * @return the real {@link CardElement}, or {@code null} if {@code ephemeralElement} is not
     * part of the OuicheList's ephemeral copy, or no matching real group/index can be found
     */
    public static CardElement resolveRealElementForOuicheListElement(CardElement ephemeralElement) {
        if (ephemeralElement == null) {
            return null;
        }
        CardsGroup ephemeralGroup = findGroupForCardElement(ephemeralElement);
        if (ephemeralGroup == null) {
            return null;
        }
        CardsGroup realGroup = resolveRealGroupForOuicheListGroup(ephemeralGroup);
        if (realGroup == null) {
            return null;
        }
        List<CardElement> ephemeralBackingList = ephemeralGroup.getCardList();
        List<CardElement> realBackingList = realGroup.getCardList();
        if (ephemeralBackingList == null || realBackingList == null) {
            return null;
        }
        int index = ephemeralBackingList.indexOf(ephemeralElement);
        if (index < 0 || index >= realBackingList.size()) {
            return null;
        }
        return realBackingList.get(index);
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
     *
     * @deprecated use {@link View.GridViewSizer#adjustGridViewHeight} directly.
     * Kept as a forwarding stub for existing callers.
     */
    @Deprecated
    public static void adjustGridViewHeightStatic(GridView<CardElement> grid, int numItems) {
        View.GridViewSizer.adjustGridViewHeight(grid, numItems);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────
}