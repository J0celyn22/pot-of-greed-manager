package Controller;

import Model.CardsLists.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the OuicheList tab's drag-and-drop redirect added to
 * {@link CardGroupRegistry}: {@link CardGroupRegistry#resolveRealGroupForOuicheListGroup}
 * and {@link CardGroupRegistry#resolveRealElementForOuicheListElement}.
 *
 * <p>The OuicheList tab renders its own deep-copied {@link Deck}/{@link ThemeCollection}
 * structure ({@link OuicheList#getDetailedOuicheList()}). Drag-and-drop performed there must
 * resolve to the corresponding real {@link CardsGroup}/{@link CardElement} in the live Decks
 * &amp; Collections model (installed via {@link UserInterfaceFunctions#setDecksList}) so the
 * mutation lands on the real model rather than the ephemeral copy; {@link OuicheListUpdaterTest}
 * covers the reverse (Decks &amp; Collections/Owned Collection → OuicheList) sync direction.</p>
 *
 * <p>Nothing here touches {@code Platform.runLater} or the JavaFX toolkit — only
 * {@code ObservableList} wrapping, which does not require it — so every method is directly
 * testable against plain model fixtures. {@code dropInsertIntoGroup}'s MOVE branch itself
 * (which does call {@code Platform.runLater} via {@code triggerHeightAdjustment}) is out of
 * scope here for that reason.</p>
 *
 * <p>Coverage:
 * <ul>
 *   <li>resolveRealGroupForOuicheListGroup — standalone deck sections (main/extra/side),
 *       a deck nested inside a collection, a collection's cardsList, and negative cases
 *       (null group, no detailed OuicheList, an already-real group, an unrelated group, no
 *       matching real deck/collection by name)</li>
 *   <li>resolveRealGroupForOuicheListGroup — registers the resolved real group into
 *       {@code GROUP_OBSERVABLE_LISTS} so {@code dropInsertIntoGroup}'s MOVE-removal scan can
 *       find it even when the Decks &amp; Collections tab was never opened this session</li>
 *   <li>resolveRealElementForOuicheListElement — index-parity resolution in a deck section
 *       and a collection's cardsList, non-zero index, and negative/defensive cases (null
 *       element, element not part of any registered group, real list shorter than the
 *       ephemeral one)</li>
 *   <li>Repeat-interaction scenarios on a card whose real group/element was only just
 *       resolved (mirrors the "freshly added/moved card" cases in the forward-sync suite,
 *       but for this redirect direction) — the exact class of scenario that silently failed
 *       before groups resolved this way were registered in {@code GROUP_OBSERVABLE_LISTS}</li>
 * </ul>
 */
public class CardGroupRegistryOuicheListSyncTest {

    // ── Fixture fields ───────────────────────────────────────────────────────

    private Card cardA;
    private Card cardB;

    // Ephemeral (OuicheList tab) side — deep copies, distinct instances from the real side.
    private Deck ephemeralStandaloneDeck;
    private ThemeCollection ephemeralCollection;
    private Deck ephemeralLinkedDeck;

    // Real (Decks & Collections) side — same names, distinct instances.
    private Deck realStandaloneDeck;
    private ThemeCollection realCollection;
    private Deck realLinkedDeck;

    // ── Setup helpers ────────────────────────────────────────────────────────

    private static Card card(String konamiId, String passCode, String imagePath) {
        Card newCard = new Card();
        newCard.setKonamiId(konamiId);
        newCard.setPassCode(passCode);
        newCard.setImagePath(imagePath);
        return newCard;
    }

    private static CardElement missingSlot(Card card) {
        CardElement element = new CardElement(card);
        element.setOwnershipStatus(OwnershipStatus.MISSING);
        return element;
    }

    private static Deck deck(String name) {
        Deck newDeck = new Deck();
        newDeck.setName(name);
        return newDeck;
    }

    private static ThemeCollection themeCollection(String name) {
        ThemeCollection collection = new ThemeCollection();
        collection.setName(name);
        return collection;
    }

    /**
     * Builds a {@link CardsGroup} wrapping {@code backingList} without registering it
     * anywhere — mirrors how {@code DecksCollectionsController.createDeckTreeItem}/
     * {@code createThemeCollectionTreeItem} construct a section's group: raw-list identity
     * is what {@code findOwnerForGroup}'s fallback and {@code deckSectionKeyForBackingList}
     * key off, regardless of registry state.
     */
    private static CardsGroup groupFor(List<CardElement> backingList) {
        return new CardsGroup("ignored-name", backingList);
    }

    /**
     * Same as {@link #groupFor}, but also registers the group's observable list so
     * {@code findGroupForCardElement} can locate it — mirroring the OuicheList tab tree
     * actually having been built (a prerequisite for a drag to originate from it at all).
     */
    private static void registerEphemeralGroup(List<CardElement> backingList) {
        CardGroupRegistry.observableListFor(groupFor(backingList));
    }

    @BeforeEach
    void setUp() {
        cardA = card("KID-001", "00000001", "img/a.jpg");
        cardB = card("KID-002", "00000002", "img/b.jpg");

        // Registries are static/global — start every test from a clean slate.
        CardGroupRegistry.GROUP_OBSERVABLE_LISTS.clear();
        CardGroupRegistry.DECK_SECTION_GROUPS.clear();
        CardGroupRegistry.COLLECTION_CARDS_GROUPS.clear();
        CardGroupRegistry.COLLECTION_EXCEPTIONS_GROUPS.clear();
        CardGroupRegistry.GROUP_GRID_VIEWS.clear();
        CardGroupRegistry.GROUP_FILTERED_LISTS.clear();

        // ── Ephemeral side (OuicheList's own deep copy) ──
        ephemeralStandaloneDeck = deck("StandaloneDeck");
        ephemeralStandaloneDeck.getMainDeck().add(missingSlot(cardA));
        ephemeralStandaloneDeck.getExtraDeck().add(missingSlot(cardA));
        ephemeralStandaloneDeck.getSideDeck().add(missingSlot(cardA));

        ephemeralLinkedDeck = deck("LinkedDeck");
        ephemeralLinkedDeck.getMainDeck().add(missingSlot(cardB));

        ephemeralCollection = themeCollection("TestCollection");
        ephemeralCollection.setCardsList(new ArrayList<>(List.of(missingSlot(cardA))));
        ephemeralCollection.addDeck(ephemeralLinkedDeck);

        DecksAndCollectionsList ephemeralList = new DecksAndCollectionsList();
        ephemeralList.addDeck(ephemeralStandaloneDeck);
        ephemeralList.addCollection(ephemeralCollection);
        OuicheList.setDetailedOuicheList(ephemeralList);

        // ── Real side (live Decks & Collections model) — same names, fresh instances ──
        realStandaloneDeck = deck("StandaloneDeck");
        realStandaloneDeck.getMainDeck().add(new CardElement(cardA));
        realStandaloneDeck.getExtraDeck().add(new CardElement(cardA));
        realStandaloneDeck.getSideDeck().add(new CardElement(cardA));

        realLinkedDeck = deck("LinkedDeck");
        realLinkedDeck.getMainDeck().add(new CardElement(cardB));

        realCollection = themeCollection("TestCollection");
        realCollection.setCardsList(new ArrayList<>(List.of(new CardElement(cardA))));
        realCollection.addDeck(realLinkedDeck);

        DecksAndCollectionsList realList = new DecksAndCollectionsList();
        realList.addDeck(realStandaloneDeck);
        realList.addCollection(realCollection);
        UserInterfaceFunctions.setDecksList(realList);

        // Register every ephemeral section as if the OuicheList tab tree had already been
        // built (a prerequisite for any drag to originate from it).
        registerEphemeralGroup(ephemeralStandaloneDeck.getMainDeck());
        registerEphemeralGroup(ephemeralStandaloneDeck.getExtraDeck());
        registerEphemeralGroup(ephemeralStandaloneDeck.getSideDeck());
        registerEphemeralGroup(ephemeralLinkedDeck.getMainDeck());
        registerEphemeralGroup(ephemeralCollection.getCardsList());
    }

    // =========================================================================
    // resolveRealGroupForOuicheListGroup — standalone deck sections
    // =========================================================================

    @Test
    void resolvesMainDeckSection_standaloneDeck() {
        CardsGroup resolved =
                CardGroupRegistry.resolveRealGroupForOuicheListGroup(groupFor(ephemeralStandaloneDeck.getMainDeck()));

        assertNotNull(resolved);
        assertSame(realStandaloneDeck.getMainDeck(), resolved.getCardList());
    }

    @Test
    void resolvesExtraDeckSection_standaloneDeck() {
        CardsGroup resolved = CardGroupRegistry
                .resolveRealGroupForOuicheListGroup(groupFor(ephemeralStandaloneDeck.getExtraDeck()));

        assertNotNull(resolved);
        assertSame(realStandaloneDeck.getExtraDeck(), resolved.getCardList());
    }

    @Test
    void resolvesSideDeckSection_standaloneDeck() {
        CardsGroup resolved = CardGroupRegistry
                .resolveRealGroupForOuicheListGroup(groupFor(ephemeralStandaloneDeck.getSideDeck()));

        assertNotNull(resolved);
        assertSame(realStandaloneDeck.getSideDeck(), resolved.getCardList());
    }

    // =========================================================================
    // resolveRealGroupForOuicheListGroup — deck nested inside a collection
    // =========================================================================

    @Test
    void resolvesMainDeckSection_deckInsideCollection() {
        CardsGroup resolved =
                CardGroupRegistry.resolveRealGroupForOuicheListGroup(groupFor(ephemeralLinkedDeck.getMainDeck()));

        assertNotNull(resolved);
        assertSame(realLinkedDeck.getMainDeck(), resolved.getCardList());
    }

    // =========================================================================
    // resolveRealGroupForOuicheListGroup — collection cardsList
    // =========================================================================

    @Test
    void resolvesCollectionCardsList() {
        CardsGroup resolved =
                CardGroupRegistry.resolveRealGroupForOuicheListGroup(groupFor(ephemeralCollection.getCardsList()));

        assertNotNull(resolved);
        assertSame(realCollection.getCardsList(), resolved.getCardList());
    }

    // =========================================================================
    // resolveRealGroupForOuicheListGroup — negative cases
    // =========================================================================

    @Test
    void nullGroup_returnsNull() {
        assertNull(CardGroupRegistry.resolveRealGroupForOuicheListGroup(null));
    }

    @Test
    void noDetailedOuicheList_returnsNull() {
        OuicheList.setDetailedOuicheList(null);

        CardsGroup resolved =
                CardGroupRegistry.resolveRealGroupForOuicheListGroup(groupFor(ephemeralStandaloneDeck.getMainDeck()));

        assertNull(resolved);
    }

    @Test
    void groupAlreadyReal_returnsNull() {
        // A group backed by the *real* model's list is not part of the OuicheList's
        // ephemeral copy — resolving it must be a no-op.
        CardsGroup resolved =
                CardGroupRegistry.resolveRealGroupForOuicheListGroup(groupFor(realStandaloneDeck.getMainDeck()));

        assertNull(resolved);
    }

    @Test
    void unrelatedGroup_returnsNull() {
        CardsGroup resolved =
                CardGroupRegistry.resolveRealGroupForOuicheListGroup(groupFor(new ArrayList<>()));

        assertNull(resolved);
    }

    @Test
    void noMatchingRealDeckByName_returnsNull() {
        // Ephemeral deck exists only in the OuicheList copy — the real model has no deck
        // by that name (e.g. it was deleted from Decks & Collections after the OuicheList
        // was last generated).
        Deck orphanEphemeralDeck = deck("DeletedDeck");
        orphanEphemeralDeck.getMainDeck().add(missingSlot(cardA));
        OuicheList.getDetailedOuicheList().addDeck(orphanEphemeralDeck);

        CardsGroup resolved =
                CardGroupRegistry.resolveRealGroupForOuicheListGroup(groupFor(orphanEphemeralDeck.getMainDeck()));

        assertNull(resolved);
    }

    // =========================================================================
    // resolveRealGroupForOuicheListGroup — GROUP_OBSERVABLE_LISTS registration
    // =========================================================================

    @Test
    void resolvedGroup_isRegisteredInObservableListRegistry() {
        CardsGroup resolved =
                CardGroupRegistry.resolveRealGroupForOuicheListGroup(groupFor(ephemeralStandaloneDeck.getMainDeck()));

        assertTrue(CardGroupRegistry.GROUP_OBSERVABLE_LISTS.containsKey(resolved),
                "A group resolved through the OuicheList redirect must be registered so "
                        + "dropInsertIntoGroup's MOVE-removal scan can find it even when the "
                        + "Decks & Collections tab was never opened this session");
    }

    @Test
    void resolvingTwice_returnsTheSameGroupInstance() {
        CardsGroup ephemeralGroup = groupFor(ephemeralStandaloneDeck.getMainDeck());

        CardsGroup firstResolution = CardGroupRegistry.resolveRealGroupForOuicheListGroup(ephemeralGroup);
        CardsGroup secondResolution = CardGroupRegistry.resolveRealGroupForOuicheListGroup(ephemeralGroup);

        assertSame(firstResolution, secondResolution,
                "Repeated resolution must return the same registered CardsGroup wrapper, not a "
                        + "fresh, unregistered one each time");
    }

    // =========================================================================
    // resolveRealElementForOuicheListElement
    // =========================================================================

    @Test
    void resolvesElement_atMatchingIndex() {
        CardElement ephemeralElement = ephemeralStandaloneDeck.getMainDeck().get(0);

        CardElement resolved = CardGroupRegistry.resolveRealElementForOuicheListElement(ephemeralElement);

        assertSame(realStandaloneDeck.getMainDeck().get(0), resolved);
    }

    @Test
    void resolvesElement_inCollectionCardsList() {
        CardElement ephemeralElement = ephemeralCollection.getCardsList().get(0);

        CardElement resolved = CardGroupRegistry.resolveRealElementForOuicheListElement(ephemeralElement);

        assertSame(realCollection.getCardsList().get(0), resolved);
    }

    @Test
    void resolvesElement_atNonZeroIndex() {
        ephemeralStandaloneDeck.getMainDeck().add(missingSlot(cardB));
        realStandaloneDeck.getMainDeck().add(new CardElement(cardB));
        CardElement ephemeralElement = ephemeralStandaloneDeck.getMainDeck().get(1);

        CardElement resolved = CardGroupRegistry.resolveRealElementForOuicheListElement(ephemeralElement);

        assertSame(realStandaloneDeck.getMainDeck().get(1), resolved);
    }

    @Test
    void nullElement_returnsNull() {
        assertNull(CardGroupRegistry.resolveRealElementForOuicheListElement(null));
    }

    @Test
    void elementNotInAnyRegisteredGroup_returnsNull() {
        // Never rendered by the OuicheList tab tree, so findGroupForCardElement can't
        // locate any owning group for it.
        CardElement orphan = new CardElement(cardA);

        assertNull(CardGroupRegistry.resolveRealElementForOuicheListElement(orphan));
    }

    @Test
    void realListShorterThanEphemeralList_returnsNull() {
        // Defensive case: index parity has drifted (shouldn't normally happen, but the
        // lookup must not throw or return the wrong element).
        ephemeralStandaloneDeck.getMainDeck().add(missingSlot(cardB));
        CardElement ephemeralElement = ephemeralStandaloneDeck.getMainDeck().get(1);

        assertNull(CardGroupRegistry.resolveRealElementForOuicheListElement(ephemeralElement));
    }

    // =========================================================================
    // Repeat-interaction scenarios: a second move/remove targeting a card whose real
    // group was only just resolved (and thus only just registered) by a *previous*
    // interaction. This is the exact class of scenario — acting again on a freshly
    // touched card — that silently failed before resolveRealGroupForOuicheListGroup
    // registered its result in GROUP_OBSERVABLE_LISTS.
    // =========================================================================

    @Test
    void secondInteractionWithSameCard_stillResolvesRealCounterpart() {
        CardElement ephemeralElement = ephemeralStandaloneDeck.getMainDeck().get(0);

        // First interaction: e.g. the user drags this card once.
        CardElement firstResolution = CardGroupRegistry.resolveRealElementForOuicheListElement(ephemeralElement);
        assertNotNull(firstResolution);

        // Second interaction with the very same ephemeral element (e.g. dragged again
        // immediately afterward, before any full OuicheList regeneration) must resolve
        // just as reliably as the first.
        CardElement secondResolution = CardGroupRegistry.resolveRealElementForOuicheListElement(ephemeralElement);

        assertSame(firstResolution, secondResolution,
                "A card must resolve to its real counterpart on every interaction, not just "
                        + "the first one for a given group");
    }

    @Test
    void movingBetweenTwoNeverBeforeTouchedRealGroups_bothGetRegistered() {
        // Neither the source (Main Deck) nor the target (Extra Deck) has been touched
        // this session (no Decks & Collections tab visit, no prior drop) — this is the
        // exact scenario that silently failed to remove the source-side element before
        // both groups were registered in GROUP_OBSERVABLE_LISTS.
        CardsGroup realSource =
                CardGroupRegistry.resolveRealGroupForOuicheListGroup(groupFor(ephemeralStandaloneDeck.getMainDeck()));
        CardsGroup realTarget = CardGroupRegistry
                .resolveRealGroupForOuicheListGroup(groupFor(ephemeralStandaloneDeck.getExtraDeck()));

        assertTrue(CardGroupRegistry.GROUP_OBSERVABLE_LISTS.containsKey(realSource),
                "Source group must be registered so a MOVE away from it can find and remove "
                        + "the element instead of duplicating it at the target");
        assertTrue(CardGroupRegistry.GROUP_OBSERVABLE_LISTS.containsKey(realTarget));
    }
}