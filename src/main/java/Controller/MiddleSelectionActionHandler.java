package Controller;

import Model.CardsLists.*;
import View.CardTreeCell;
import View.NavigationItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Holds the finder/locator logic and the paste/insert orchestration used by the
 * middle-pane keyboard shortcuts (delete, duplicate, complete-to-three, paste,
 * numpad-add) and by drag-and-drop paste onto navigation items.
 * <p>
 * Extracted from {@link RealMainController}, which still owns the actual keyboard
 * dispatch and the methods that read coordinator instance state (active tab index,
 * the active middle TreeView, the right-pane display). Methods here that need that
 * context take it as an explicit parameter (e.g. {@code activeTabIndex}) rather than
 * reading it directly, which is what makes the move safe.
 * </p>
 */
final class MiddleSelectionActionHandler {

    private static final Logger logger = LoggerFactory.getLogger(MiddleSelectionActionHandler.class);

    private MiddleSelectionActionHandler() {
    }

    /**
     * Returns the {@link List} that directly contains {@code element},
     * depending on the active tab:
     * <ul>
     *   <li>Tab 0 (My Collection): the {@link CardsGroup} cardList that holds the element.</li>
     *   <li>Tab 1 (Decks and Collections): the specific deck-section list (main/extra/side)
     *       that holds the element, or the ThemeCollection cardsList.</li>
     * </ul>
     * Returns {@code null} when no container is found.
     */
    static List<CardElement> findDirectContainer(CardElement element, int activeTabIndex) {
        if (element == null) {
            return null;
        }
        if (activeTabIndex == 0) {
            // My Collection: search CardsGroups inside every Box.
            OwnedCardsCollection ownedCollection = Model.CardsLists.OuicheList.getMyCardsCollection();
            if (ownedCollection == null) {
                return null;
            }
            for (Box box : ownedCollection.getOwnedCollection()) {
                List<CardsGroup> groups = box.getContent();
                if (groups == null) {
                    continue;
                }
                for (CardsGroup group : groups) {
                    List<CardElement> cardList = group.getCardList();
                    if (cardList != null && cardList.contains(element)) {
                        return cardList;
                    }
                }
            }
        } else if (activeTabIndex == 1) {
            DecksAndCollectionsList decksList = UserInterfaceFunctions.getDecksList();
            if (decksList == null) {
                return null;
            }
            // Search deck sections of standalone decks.
            for (Deck deck : decksList.getDecks()) {
                if (deck == null) {
                    continue;
                }
                if (deck.getMainDeck() != null && deck.getMainDeck().contains(element)) {
                    return deck.getMainDeck();
                }
                if (deck.getExtraDeck() != null && deck.getExtraDeck().contains(element)) {
                    return deck.getExtraDeck();
                }
                if (deck.getSideDeck() != null && deck.getSideDeck().contains(element)) {
                    return deck.getSideDeck();
                }
            }
            // Search ThemeCollection cardsLists and linked deck sections.
            for (ThemeCollection collection : decksList.getCollections()) {
                if (collection == null) {
                    continue;
                }
                List<CardElement> cardsList = collection.getCardsList();
                if (cardsList != null && cardsList.contains(element)) {
                    return cardsList;
                }
                if (collection.getLinkedDecks() == null) {
                    continue;
                }
                for (List<Deck> unit : collection.getLinkedDecks()) {
                    if (unit == null) {
                        continue;
                    }
                    for (Deck deck : unit) {
                        if (deck == null) {
                            continue;
                        }
                        if (deck.getMainDeck() != null && deck.getMainDeck().contains(element)) {
                            return deck.getMainDeck();
                        }
                        if (deck.getExtraDeck() != null && deck.getExtraDeck().contains(element)) {
                            return deck.getExtraDeck();
                        }
                        if (deck.getSideDeck() != null && deck.getSideDeck().contains(element)) {
                            return deck.getSideDeck();
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds the {@link Deck} or {@link ThemeCollection} that owns {@code element}
     * in the live decksList, searching collection card lists, their linked decks,
     * and standalone decks.
     *
     * @return the owning {@code Deck} or {@code ThemeCollection}, or {@code null}
     * if not found
     */
    static Object findDeckOrCollectionOwner(CardElement element) {
        if (element == null) {
            return null;
        }
        DecksAndCollectionsList decksList = UserInterfaceFunctions.getDecksList();
        if (decksList == null) {
            return null;
        }
        if (decksList.getCollections() != null) {
            for (ThemeCollection collection : decksList.getCollections()) {
                if (collection.getCardsList() != null
                        && collection.getCardsList().contains(element)) {
                    return collection;
                }
                if (collection.getLinkedDecks() != null) {
                    for (List<Deck> unit : collection.getLinkedDecks()) {
                        if (unit == null) {
                            continue;
                        }
                        for (Deck deck : unit) {
                            if (containsElementInDeck(deck, element)) {
                                return deck;
                            }
                        }
                    }
                }
            }
        }
        if (decksList.getDecks() != null) {
            for (Deck deck : decksList.getDecks()) {
                if (containsElementInDeck(deck, element)) {
                    return deck;
                }
            }
        }
        return null;
    }

    static boolean containsElementInDeck(Deck deck, CardElement element) {
        if (deck == null) {
            return false;
        }
        if (deck.getMainDeck() != null && deck.getMainDeck().contains(element)) {
            return true;
        }
        if (deck.getExtraDeck() != null && deck.getExtraDeck().contains(element)) {
            return true;
        }
        if (deck.getSideDeck() != null && deck.getSideDeck().contains(element)) {
            return true;
        }
        return false;
    }

    /**
     * Returns the {@link CardElement} list backing the given navigation-item model
     * object, used as the paste/insert target for that item.
     *
     * @param modelObj a {@link Box}, {@link CardsGroup}, {@link Deck}, or
     *                 {@link ThemeCollection} navigation-item model object
     * @return the backing list, or an empty list if {@code modelObj} doesn't map
     * to one (e.g. an empty {@link Box})
     */
    static List<CardElement> getTargetGroupElements(Object modelObj) {
        if (modelObj instanceof CardsGroup) {
            List<CardElement> list = ((CardsGroup) modelObj).getCardList();
            return list != null ? list : new java.util.ArrayList<>();
        }
        if (modelObj instanceof Box box) {
            if (box.getContent() != null && !box.getContent().isEmpty()) {
                return box.getContent().get(box.getContent().size() - 1).getCardList();
            }
        }
        if (modelObj instanceof Deck deck) {
            if (deck.getMainDeck() != null) {
                return deck.getMainDeck();
            }
        }
        if (modelObj instanceof ThemeCollection collection) {
            List<CardElement> cardsList = collection.getCardsList();
            return cardsList != null ? cardsList : new java.util.ArrayList<>();
        }
        return new java.util.ArrayList<>();
    }

    /**
     * Returns the name of the {@link ThemeCollection} that owns {@code deck} in
     * the live decksList, or {@code null} for standalone decks.
     */
    static String findCollectionNameForDeck(Deck deck) {
        DecksAndCollectionsList decksList = UserInterfaceFunctions.getDecksList();
        if (deck == null || decksList == null || decksList.getCollections() == null) {
            return null;
        }
        for (ThemeCollection collection : decksList.getCollections()) {
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

    /**
     * Walks {@code treeView} breadth-first and returns the model object (a
     * {@link Box}, {@link CardsGroup}, {@link Deck}, or {@link ThemeCollection})
     * of the last navigation item whose backing list is non-empty.
     *
     * @param treeView the active middle TreeView
     * @return the last such model object, or {@code null} if none is found
     */
    static Object findLastNavModelInTree(TreeView<String> treeView) {
        if (treeView == null || treeView.getRoot() == null) {
            return null;
        }
        Object candidate = null;
        Queue<TreeItem<String>> queue = new LinkedList<>();
        queue.add(treeView.getRoot());
        while (!queue.isEmpty()) {
            TreeItem<String> item = queue.poll();
            if (item.getGraphic() instanceof NavigationItem navItem) {
                Object modelObj = navItem.getUserData();
                if (modelObj != null && !getTargetGroupElements(modelObj).isEmpty()) {
                    candidate = modelObj;
                }
            }
            queue.addAll(item.getChildren());
        }
        return candidate;
    }

    /**
     * Inserts copy-constructed snapshots of {@code elements} immediately after
     * {@code anchor} in the group that contains it, preserving {@code condition},
     * {@code rarity}, {@code customTags}, and artwork flags.
     *
     * @param elements       the element snapshots to paste (not {@code null} or empty)
     * @param anchor         the element after which to insert
     * @param activeTabIndex the currently selected main-tab index (0 = My Collection,
     *                       1 = Decks and Collections)
     * @return {@code true} if at least one element was inserted
     */
    static boolean pasteElementsAfterElement(List<CardElement> elements, CardElement anchor,
                                             int activeTabIndex) {
        if (anchor == null || elements == null || elements.isEmpty()) {
            return false;
        }
        boolean inserted = MenuActionHandler.handleInsertElementsAfterElement(
                elements, anchor);
        if (!inserted) {
            return false;
        }
        if (activeTabIndex == 0) {
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionView();
        } else if (activeTabIndex == 1) {
            Object owner = findDeckOrCollectionOwner(anchor);
            if (owner != null) {
                UserInterfaceFunctions.markDirty(owner);
            }
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        }
        return true;
    }

    /**
     * Same as {@link #pasteElementsAfterElement} but for raw {@link Card}s rather
     * than pre-built {@link CardElement} snapshots (e.g. right-pane numpad-add).
     *
     * @param activeTabIndex the currently selected main-tab index (0 = My Collection,
     *                       1 = Decks and Collections)
     */
    static boolean pasteCardsAfterElement(List<Card> cards, CardElement anchor, int activeTabIndex) {
        if (anchor == null || cards == null || cards.isEmpty()) {
            return false;
        }
        boolean inserted = MenuActionHandler.handleInsertCardsAfterElement(cards, anchor);
        if (!inserted) {
            return false;
        }
        if (activeTabIndex == 0) {
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionView();
        } else if (activeTabIndex == 1) {
            Object owner = findDeckOrCollectionOwner(anchor);
            if (owner != null) {
                UserInterfaceFunctions.markDirty(owner);
            }
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        }
        return true;
    }

    /**
     * Inserts copy-constructed snapshots of {@code elements} into the group or
     * deck section identified by {@code modelObj}, preserving {@code condition},
     * {@code rarity}, {@code customTags}, and artwork flags.
     *
     * @param elements the element snapshots to paste
     * @param modelObj a {@link Box}, {@link CardsGroup}, {@link Deck}, or
     *                 {@link ThemeCollection} navigation-item model object
     */
    static void pasteElementsIntoNavigationItem(List<CardElement> elements, Object modelObj) {
        if (elements == null || elements.isEmpty() || modelObj == null) {
            return;
        }

        if (modelObj instanceof Box box) {
            CardsGroup defaultGroup =
                    MenuActionHandler.getOrCreateDefaultGroup(box);
            if (defaultGroup == null) {
                return;
            }
            javafx.collections.ObservableList<CardElement> observableList =
                    CardGroupRegistry.observableListFor(defaultGroup);
            List<CardElement> addedElements = new java.util.ArrayList<>();
            for (CardElement element : elements) {
                if (element != null) {
                    CardElement newElement = new CardElement(element);
                    observableList.add(newElement);
                    addedElements.add(newElement);
                }
            }
            CardGroupRegistry.triggerHeightAdjustment(defaultGroup);
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionView();
            for (CardElement added : addedElements) {
                try {
                    OuicheList.onOwnedCardAdded(added);
                } catch (Throwable throwable) {
                    logger.error("OuicheList update failed after paste into box", throwable);
                }
            }
            if (!addedElements.isEmpty()) {
                UserInterfaceFunctions.refreshOuicheListView();
            }

        } else if (modelObj instanceof CardsGroup group) {
            javafx.collections.ObservableList<CardElement> observableList =
                    CardGroupRegistry.observableListFor(group);
            List<CardElement> addedElements = new java.util.ArrayList<>();
            for (CardElement element : elements) {
                if (element != null) {
                    CardElement newElement = new CardElement(element);
                    observableList.add(newElement);
                    addedElements.add(newElement);
                }
            }
            CardGroupRegistry.triggerHeightAdjustment(group);
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionView();
            for (CardElement added : addedElements) {
                try {
                    OuicheList.onOwnedCardAdded(added);
                } catch (Throwable throwable) {
                    logger.error("OuicheList update failed after paste into group", throwable);
                }
            }
            if (!addedElements.isEmpty()) {
                UserInterfaceFunctions.refreshOuicheListView();
            }

        } else {
            // For Deck and ThemeCollection targets, condition/rarity/tags are not
            // used by those contexts, so delegate to the Card-list path.
            List<Card> cards = new java.util.ArrayList<>(elements.size());
            for (CardElement element : elements) {
                if (element != null && element.getCard() != null) {
                    cards.add(element.getCard());
                }
            }
            pasteCardsIntoNavigationItem(cards, modelObj);
        }
    }

    /**
     * Same as {@link #pasteElementsIntoNavigationItem} but for raw {@link Card}s
     * rather than pre-built {@link CardElement} snapshots (e.g. right-pane numpad-add
     * and right-pane drag-and-drop).
     *
     * @param cards    the cards to paste
     * @param modelObj a {@link Box}, {@link CardsGroup}, {@link Deck}, or
     *                 {@link ThemeCollection} navigation-item model object
     */
    static void pasteCardsIntoNavigationItem(List<Card> cards, Object modelObj) {
        if (cards == null || cards.isEmpty() || modelObj == null) {
            return;
        }

        if (modelObj instanceof Box box) {
            CardsGroup defaultGroup = MenuActionHandler.getOrCreateDefaultGroup(box);
            if (defaultGroup == null) {
                return;
            }
            javafx.collections.ObservableList<CardElement> observableList =
                    CardGroupRegistry.observableListFor(defaultGroup);
            for (Card card : cards) {
                if (card != null) {
                    observableList.add(new CardElement(card));
                }
            }
            CardGroupRegistry.triggerHeightAdjustment(defaultGroup);
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionView();

        } else if (modelObj instanceof CardsGroup group) {
            javafx.collections.ObservableList<CardElement> observableList =
                    CardGroupRegistry.observableListFor(group);
            for (Card card : cards) {
                if (card != null) {
                    observableList.add(new CardElement(card));
                }
            }
            CardGroupRegistry.triggerHeightAdjustment(group);
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionView();

        } else if (modelObj instanceof Deck deck) {
            if (deck.getMainDeck() == null) {
                deck.setMainDeck(new java.util.ArrayList<>());
            }
            if (deck.getExtraDeck() == null) {
                deck.setExtraDeck(new java.util.ArrayList<>());
            }

            List<Card> toMain = new java.util.ArrayList<>();
            List<Card> toExtra = new java.util.ArrayList<>();
            for (Card card : cards) {
                if (card == null) {
                    continue;
                }
                if (Utils.DeckCompatibility.isExtraDeckCard(card)) {
                    toExtra.add(card);
                } else {
                    toMain.add(card);
                }
            }

            String parentCollectionName = findCollectionNameForDeck(deck);
            List<CardElement> addedElements = new java.util.ArrayList<>();

            java.util.function.BiConsumer<List<Card>, String> addToSection =
                    (sectionCards, sectionKey) -> {
                        if (sectionCards.isEmpty()) {
                            return;
                        }
                        CardsGroup sectionGroup =
                                CardGroupRegistry.getDeckSectionGroup(deck, sectionKey);
                        if (sectionGroup != null) {
                            javafx.collections.ObservableList<CardElement> obs =
                                    CardGroupRegistry.observableListFor(sectionGroup);
                            for (Card card : sectionCards) {
                                CardElement newElement = new CardElement(card);
                                obs.add(newElement);
                                addedElements.add(newElement);
                            }
                            CardGroupRegistry.triggerHeightAdjustment(sectionGroup);
                        } else {
                            List<CardElement> rawList = "extra".equals(sectionKey)
                                    ? deck.getExtraDeck()
                                    : deck.getMainDeck();
                            for (Card card : sectionCards) {
                                CardElement newElement = new CardElement(card);
                                rawList.add(newElement);
                                addedElements.add(newElement);
                            }
                            UserInterfaceFunctions.triggerDecksStructureRefresh();
                        }
                    };

            addToSection.accept(toMain, "main");
            addToSection.accept(toExtra, "extra");

            UserInterfaceFunctions.markDirty(deck);
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();

            for (CardElement addedElement : addedElements) {
                String sectionName = Utils.DeckCompatibility.isExtraDeckCard(addedElement.getCard())
                        ? "extra" : "main";
                try {
                    OuicheList.onDeckCardAdded(addedElement, deck.getName(), sectionName,
                            parentCollectionName);
                } catch (Throwable throwable) {
                    logger.error("OuicheList update failed after paste into deck '{}'",
                            deck.getName(), throwable);
                }
            }
            if (!addedElements.isEmpty()) {
                UserInterfaceFunctions.refreshOuicheListView();
            }

        } else if (modelObj instanceof ThemeCollection collection) {
            if (collection.getCardsList() == null) {
                collection.setCardsList(new java.util.ArrayList<>());
            }

            CardsGroup cardsGroup = CardGroupRegistry.getCollectionCardsGroup(collection);
            List<CardElement> addedElements = new java.util.ArrayList<>();
            if (cardsGroup != null) {
                javafx.collections.ObservableList<CardElement> obs =
                        CardGroupRegistry.observableListFor(cardsGroup);
                for (Card card : cards) {
                    if (card != null) {
                        CardElement newElement = new CardElement(card);
                        obs.add(newElement);
                        addedElements.add(newElement);
                    }
                }
                CardGroupRegistry.triggerHeightAdjustment(cardsGroup);
            } else {
                for (Card card : cards) {
                    if (card != null) {
                        CardElement newElement = new CardElement(card);
                        collection.getCardsList().add(newElement);
                        addedElements.add(newElement);
                    }
                }
                UserInterfaceFunctions.triggerDecksStructureRefresh();
            }
            UserInterfaceFunctions.markDirty(collection);
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            for (CardElement addedElement : addedElements) {
                try {
                    OuicheList.onDeckCardAdded(addedElement, null, null, collection.getName());
                } catch (Throwable throwable) {
                    logger.error("OuicheList update failed after paste into collection '{}'",
                            collection.getName(), throwable);
                }
            }
            if (!addedElements.isEmpty()) {
                UserInterfaceFunctions.refreshOuicheListView();
            }
        }
    }

    /**
     * Copies the current selection to {@link CardClipboard}: full {@link CardElement}
     * snapshots when the MIDDLE selection is active (preserving condition, rarity, and
     * custom tags across the copy/paste cycle), or bare {@link Card}s when copying from
     * the right pane (where condition/rarity are not available).
     */
    static void handleCopySelectionToClipboard() {
        if ("MIDDLE".equals(SelectionManager.getActivePart())) {
            List<CardElement> elementsToCopy = new java.util.ArrayList<>();
            for (CardElement element : SelectionManager.getSelectedMiddleElements()) {
                if (element.getCard() != null) {
                    elementsToCopy.add(element);
                }
            }
            if (!elementsToCopy.isEmpty()) {
                CardClipboard.copyElements(elementsToCopy);
            }
        } else {
            java.util.Set<Card> selectedCards = SelectionManager.getSelectedCards();
            if (!selectedCards.isEmpty()) {
                CardClipboard.copyCards(new java.util.ArrayList<>(selectedCards));
            }
        }
    }

    /**
     * Inserts one copy of each selected element immediately after itself, processing
     * in tree order so earlier insertions don't shift later anchors.
     *
     * @param activeTreeView the active middle TreeView
     * @param activeTabIndex the currently selected main-tab index (0 = My Collection,
     *                       1 = Decks and Collections)
     */
    static void handleDuplicateMiddleSelection(TreeView<String> activeTreeView, int activeTabIndex) {
        if (activeTreeView == null) {
            return;
        }
        java.util.Set<CardElement> selectedElements =
                SelectionManager.getSelectedMiddleElements();
        if (selectedElements.isEmpty()) {
            return;
        }
        List<CardElement> allElementsInOrder =
                CardTreeCell.collectAllElementsInTreeOrder(activeTreeView.getRoot());
        List<CardElement> selectedInOrder = allElementsInOrder.stream()
                .filter(selectedElements::contains)
                .collect(java.util.stream.Collectors.toList());
        if (selectedInOrder.isEmpty()) {
            return;
        }

        // Insert one copy of each selected element immediately after itself,
        // processing in tree order so earlier insertions don't shift later anchors.
        boolean anyInserted = false;
        for (CardElement selectedElement : selectedInOrder) {
            List<CardElement> singleCopy = new java.util.ArrayList<>();
            singleCopy.add(new CardElement(selectedElement));
            boolean inserted = MenuActionHandler.handleInsertElementsAfterElement(
                    singleCopy, selectedElement);
            if (inserted) {
                anyInserted = true;
            } else {
                logger.warn("handleDuplicateMiddleSelection: insertion failed for element");
            }
        }

        if (!anyInserted) {
            return;
        }

        CardElement lastElement = selectedInOrder.get(selectedInOrder.size() - 1);
        if (activeTabIndex == 0) {
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionView();
        } else if (activeTabIndex == 1) {
            Object owner = findDeckOrCollectionOwner(lastElement);
            if (owner != null) {
                UserInterfaceFunctions.markDirty(owner);
            }
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        }
    }

    /**
     * For each selected card element, counts how many copies of the same card
     * (by konamiId) are already in that element's direct container (a CardsGroup,
     * a deck section, or a ThemeCollection cardsList), then inserts copies after
     * the element until the container holds exactly 3. Elements whose container
     * already has 3 or more copies are skipped; no cards are ever removed.
     *
     * @param activeTreeView the active middle TreeView
     * @param activeTabIndex the currently selected main-tab index (0 = My Collection,
     *                       1 = Decks and Collections)
     */
    static void handleCompleteToThree(TreeView<String> activeTreeView, int activeTabIndex) {
        if (activeTreeView == null) {
            return;
        }
        java.util.Set<CardElement> selectedElements =
                SelectionManager.getSelectedMiddleElements();
        if (selectedElements.isEmpty()) {
            return;
        }

        // Process elements in tree order so insertions are stable.
        List<CardElement> allElementsInOrder =
                CardTreeCell.collectAllElementsInTreeOrder(activeTreeView.getRoot());
        List<CardElement> selectedInOrder = allElementsInOrder.stream()
                .filter(selectedElements::contains)
                .collect(java.util.stream.Collectors.toList());
        if (selectedInOrder.isEmpty()) {
            return;
        }

        boolean anyInserted = false;

        for (CardElement selectedElement : selectedInOrder) {
            if (selectedElement.getCard() == null) {
                continue;
            }
            String konamiId = selectedElement.getCard().getKonamiId();

            // Find the direct container list that holds this element.
            List<CardElement> directContainer =
                    findDirectContainer(selectedElement, activeTabIndex);
            if (directContainer == null) {
                continue;
            }

            // Count copies of the same card already in that container.
            int existingCount = 0;
            for (CardElement containerElement : directContainer) {
                if (containerElement == null || containerElement.getCard() == null) {
                    continue;
                }
                String containerKonamiId = containerElement.getCard().getKonamiId();
                if (konamiId != null && konamiId.equals(containerKonamiId)) {
                    existingCount++;
                }
            }

            int copiesToAdd = 3 - existingCount;
            if (copiesToAdd <= 0) {
                continue;
            }

            // Build the list of copies to insert after the selected element.
            List<CardElement> copies = new java.util.ArrayList<>();
            for (int i = 0; i < copiesToAdd; i++) {
                copies.add(new CardElement(selectedElement));
            }

            boolean inserted = MenuActionHandler.handleInsertElementsAfterElement(
                    copies, selectedElement);
            if (inserted) {
                anyInserted = true;
            } else {
                logger.warn("handleCompleteToThree: insertion failed for element with konamiId={}", konamiId);
            }
        }

        if (!anyInserted) {
            return;
        }

        if (activeTabIndex == 0) {
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionView();
        } else if (activeTabIndex == 1) {
            CardElement lastElement = selectedInOrder.get(selectedInOrder.size() - 1);
            Object owner = findDeckOrCollectionOwner(lastElement);
            if (owner != null) {
                UserInterfaceFunctions.markDirty(owner);
            }
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        }
    }

    /**
     * Returns whether {@code navItem}'s model type matches the active tab
     * (Box/CardsGroup for My Collection, Deck/ThemeCollection for Decks and Collections).
     */
    static boolean navItemBelongsToActiveTab(Object navItem, int activeTabIndex) {
        if (navItem == null) {
            return false;
        }
        if (activeTabIndex == 0) {
            return navItem instanceof Box || navItem instanceof CardsGroup;
        }
        if (activeTabIndex == 1) {
            return navItem instanceof Deck || navItem instanceof ThemeCollection;
        }
        return false;
    }

    /**
     * Returns whether {@code element} is currently present in {@code activeTreeView}.
     */
    static boolean elemBelongsToActiveTab(CardElement element, TreeView<String> activeTreeView) {
        if (element == null || activeTreeView == null || activeTreeView.getRoot() == null) {
            return false;
        }
        return CardTreeCell.collectAllElementsInTreeOrder(activeTreeView.getRoot())
                .contains(element);
    }

    /**
     * Removes the current middle-pane selection from the owned collection or from
     * decks/collections (depending on the active tab), then — if exactly one element
     * was selected — tries to reselect whatever now occupies its place, matching by
     * identity first and falling back to passCode/printCode/konamiId.
     *
     * @param activeTabIndex         the currently selected main-tab index (0 = My
     *                               Collection, 1 = Decks and Collections)
     * @param activeTreeViewSupplier supplies the active middle TreeView; called again
     *                               after the deletion's UI refresh has run (via
     *                               {@code Platform.runLater}), not at the time this
     *                               method is invoked, so that the reselect logic sees
     *                               the up-to-date tree
     */
    static void handleDeleteMiddleSelection(int activeTabIndex,
                                            java.util.function.Supplier<TreeView<String>> activeTreeViewSupplier) {
        java.util.Set<CardElement> selectedElements =
                SelectionManager.getSelectedMiddleElements();
        if (selectedElements.isEmpty()) {
            return;
        }

        if (selectedElements.size() > 10) {
            boolean confirmed = View.NavigationContextMenuBuilder.confirmWithCustomMessage(
                    "Delete " + selectedElements.size() + " cards?");
            if (!confirmed) {
                return;
            }
        }

        Card cardToReselect = null;
        CardElement elementBeingRemoved = null;
        if (selectedElements.size() == 1) {
            elementBeingRemoved = selectedElements.iterator().next();
            cardToReselect = elementBeingRemoved != null ? elementBeingRemoved.getCard() : null;
        }

        if (activeTabIndex == 0) {
            MenuActionHandler.handleBulkRemoveFromOwnedCollection(
                    new java.util.ArrayList<>(selectedElements));
        } else if (activeTabIndex == 1) {
            MenuActionHandler.handleBulkRemoveElementsFromDecksAndCollections(
                    new java.util.ArrayList<>(SelectionManager.getSelectedMiddleElements()));
        }
        SelectionManager.clearSelection();

        if (cardToReselect != null) {
            final Card targetCard = cardToReselect;
            final CardElement removedElement = elementBeingRemoved;
            javafx.application.Platform.runLater(() -> {
                TreeView<String> treeView = activeTreeViewSupplier.get();
                if (treeView == null) {
                    return;
                }
                List<CardElement> allElements =
                        CardTreeCell.collectAllElementsInTreeOrder(treeView.getRoot());
                CardElement candidate = null;
                for (CardElement cardElement : allElements) {
                    if (cardElement == removedElement) {
                        continue;
                    }
                    Card card = cardElement.getCard();
                    if (card == null) {
                        continue;
                    }
                    if (card == targetCard) {
                        candidate = cardElement;
                        continue;
                    }
                    if (targetCard.getPassCode() != null
                            && targetCard.getPassCode().equals(card.getPassCode())) {
                        candidate = cardElement;
                        continue;
                    }
                    if (targetCard.getPrintCode() != null
                            && targetCard.getPrintCode().equals(card.getPrintCode())) {
                        candidate = cardElement;
                        continue;
                    }
                    if (targetCard.getKonamiId() != null
                            && targetCard.getKonamiId().equals(card.getKonamiId())) {
                        candidate = cardElement;
                    }
                }
                if (candidate != null) {
                    SelectionManager.selectElement(candidate);
                }
            });
        }
    }
}