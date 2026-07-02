package View;

import Controller.CardGroupRegistry;
import Controller.MenuActionHandler;
import Controller.SelectionManager;
import Controller.UserInterfaceFunctions;
import Model.CardsLists.*;
import Utils.CardCollectionQuery;
import Utils.CardNameUtils;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiFunction;

/**
 * CardGridDestinationMenuBuilder — builds the "Move to...", "Add to...", and
 * "Sort card" destination/proposal submenu content for {@link CardGridCellContextMenuBuilder}.
 *
 * <p>This covers every submenu that has to walk the current My Collection / Decks &amp;
 * Collections structure to propose destinations for a card or card group:</p>
 * <ul>
 *   <li>"Move to..." for My Collection cards ({@link #populateMoveToMenu})</li>
 *   <li>"Move to..." for Decks &amp; Collections cards ({@link #buildMoveDestinationMenuItems})</li>
 *   <li>"Add to..." for archetype cards ({@link #buildAddDestinationMenuItems})</li>
 *   <li>"Sort card" proposals based on existing Decks &amp; Collections membership
 *       ({@link #buildDecksAndCollectionsProposals}) and card type/properties
 *       ({@link #buildTypeBoxProposals})</li>
 * </ul>
 */
final class CardGridDestinationMenuBuilder {

    private static final Logger logger =
            LoggerFactory.getLogger(CardGridDestinationMenuBuilder.class);

    private CardGridDestinationMenuBuilder() {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // "Move to..." population for My Collection tab
    // ─────────────────────────────────────────────────────────────────────────

    static void populateMoveToMenu(Menu moveToMenu, CardGridCell cell) {
        moveToMenu.getItems().clear();

        CardElement clickedElement = cell.getItem();
        if (clickedElement == null) {
            moveToMenu.getItems().add(ContextMenuItemFactory.disabledItem("No card selected"));
            return;
        }

        OwnedCardsCollection ownedCollection = loadOwnedCollection();
        if (ownedCollection == null
                || ownedCollection.getOwnedCollection() == null
                || ownedCollection.getOwnedCollection().isEmpty()) {
            moveToMenu.getItems().add(ContextMenuItemFactory.disabledItem("No boxes available"));
            return;
        }

        // Identify the element's current box and group so we can skip it.
        Box currentBox = null;
        Box currentGroupBox = null;
        CardsGroup currentGroup = null;
        try {
            outerSearch:
            for (Box box : ownedCollection.getOwnedCollection()) {
                if (box == null) {
                    continue;
                }
                if (box.getContent() != null) {
                    for (CardsGroup group : box.getContent()) {
                        if (group == null || group.getCardList() == null) {
                            continue;
                        }
                        for (CardElement groupElement : group.getCardList()) {
                            if (groupElement == clickedElement) {
                                currentBox = box;
                                currentGroupBox = box;
                                currentGroup = group;
                                break outerSearch;
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
                            for (CardElement groupElement : group.getCardList()) {
                                if (groupElement == clickedElement) {
                                    currentBox = subBox;
                                    currentGroupBox = subBox;
                                    currentGroup = group;
                                    break outerSearch;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        final Box capturedCurrentBox = currentGroupBox;
        final CardsGroup capturedCurrentGroup = currentGroup;

        BiFunction<String, String, MenuItem> makeMoveItem = (displayName, handlerTarget) -> {
            MenuItem menuItem = new MenuItem(displayName);
            menuItem.setOnAction(moveEvent -> {
                try {
                    List<CardElement> elementsToMove = cell.getEffectiveMiddleElements();
                    if (elementsToMove.size() > 1) {
                        MenuActionHandler.handleBulkMove(new ArrayList<>(elementsToMove), handlerTarget);
                    } else {
                        MenuActionHandler.handleMove(cell.getItem(), handlerTarget);
                    }
                } catch (Throwable moveError) {
                    logger.debug("Move action failed for target {}", handlerTarget, moveError);
                }
            });
            return menuItem;
        };

        for (Box box : ownedCollection.getOwnedCollection()) {
            if (box == null) {
                continue;
            }
            String boxName = CardNameUtils.sanitize(box.getName() == null ? "" : box.getName());
            if (boxName.isEmpty()) {
                boxName = "(Unnamed box)";
            }

            moveToMenu.getItems().add(makeMoveItem.apply(boxName, boxName));

            if (box.getContent() != null) {
                for (CardsGroup group : box.getContent()) {
                    if (group == null) {
                        continue;
                    }
                    String rawGroupName = group.getName();
                    if (rawGroupName == null || rawGroupName.trim().isEmpty()) {
                        continue;
                    }
                    String groupName = CardNameUtils.sanitize(rawGroupName);
                    if (groupName.isEmpty()) {
                        continue;
                    }
                    boolean isCurrentGroup = capturedCurrentBox != null
                            && capturedCurrentGroup != null
                            && capturedCurrentBox == box
                            && capturedCurrentGroup == group;
                    if (isCurrentGroup) {
                        continue;
                    }
                    String display = boxName + " / " + groupName;
                    String handlerTarget = boxName + "/" + groupName;
                    moveToMenu.getItems().add(makeMoveItem.apply(display, handlerTarget));
                }
            }

            if (box.getSubBoxes() != null) {
                for (Box subBox : box.getSubBoxes()) {
                    if (subBox == null) {
                        continue;
                    }
                    String subBoxName = CardNameUtils.sanitize(
                            subBox.getName() == null ? "" : subBox.getName());
                    if (subBoxName.isEmpty()) {
                        subBoxName = "(Unnamed sub-box)";
                    }
                    moveToMenu.getItems().add(makeMoveItem.apply(subBoxName, subBoxName));

                    if (subBox.getContent() != null) {
                        for (CardsGroup group : subBox.getContent()) {
                            if (group == null) {
                                continue;
                            }
                            String groupName = CardNameUtils.sanitize(
                                    group.getName() == null ? "" : group.getName());
                            if (groupName.isEmpty()) {
                                groupName = "(Unnamed group)";
                            }
                            boolean isCurrentGroup = capturedCurrentBox != null
                                    && capturedCurrentGroup != null
                                    && capturedCurrentBox == subBox
                                    && capturedCurrentGroup == group;
                            if (isCurrentGroup) {
                                continue;
                            }
                            String display = subBoxName + " / " + groupName;
                            String handlerTarget = subBoxName + "/" + groupName;
                            moveToMenu.getItems().add(makeMoveItem.apply(display, handlerTarget));
                        }
                    }
                }
            }
        }

        if (moveToMenu.getItems().isEmpty()) {
            moveToMenu.getItems().add(ContextMenuItemFactory.disabledItem("No other destinations"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // "Move to..." for Decks & Collections (MOVE semantics)
    // ─────────────────────────────────────────────────────────────────────────

    static List<MenuItem> buildMoveDestinationMenuItems(
            String excludePath, CardGridCell cell) {
        List<MenuItem> items = new ArrayList<>();
        try {
            DecksAndCollectionsList decksAndCollectionsList = ensureDecksAndCollectionsList();
            if (decksAndCollectionsList == null) {
                return items;
            }

            if (decksAndCollectionsList.getCollections() != null) {
                for (ThemeCollection themeCollection : decksAndCollectionsList.getCollections()) {
                    if (themeCollection == null) {
                        continue;
                    }
                    String collName = CardNameUtils.sanitize(themeCollection.getName());
                    addMoveDestItem(items, collName, excludePath, cell);
                    if (themeCollection.getLinkedDecks() != null) {
                        for (List<Deck> deckUnit : themeCollection.getLinkedDecks()) {
                            if (deckUnit == null) {
                                continue;
                            }
                            for (Deck deck : deckUnit) {
                                if (deck == null) {
                                    continue;
                                }
                                String deckBase = collName + " / "
                                        + CardNameUtils.sanitize(deck.getName());
                                if (cell.moveSectionAllowed("Main Deck")) {
                                    addMoveDestItem(items, deckBase + " / Main Deck", excludePath, cell);
                                }
                                if (cell.moveSectionAllowed("Extra Deck")) {
                                    addMoveDestItem(items, deckBase + " / Extra Deck", excludePath, cell);
                                }
                                addMoveDestItem(items, deckBase + " / Side Deck", excludePath, cell);
                            }
                        }
                    }
                    addMoveDestItem(items, collName + " / Exclusion List", excludePath, cell);
                }
            }
            if (decksAndCollectionsList.getDecks() != null) {
                for (Deck deck : decksAndCollectionsList.getDecks()) {
                    if (deck == null) {
                        continue;
                    }
                    String deckName = CardNameUtils.sanitize(deck.getName());
                    if (cell.moveSectionAllowed("Main Deck")) {
                        addMoveDestItem(items, deckName + " / Main Deck", excludePath, cell);
                    }
                    if (cell.moveSectionAllowed("Extra Deck")) {
                        addMoveDestItem(items, deckName + " / Extra Deck", excludePath, cell);
                    }
                    addMoveDestItem(items, deckName + " / Side Deck", excludePath, cell);
                }
            }
        } catch (Exception buildError) {
            logger.debug("buildMoveDestinationMenuItems failed", buildError);
        }
        return items;
    }

    private static void addMoveDestItem(
            List<MenuItem> items, String path, String excludePath, CardGridCell cell) {
        if (path == null || path.equals(excludePath)) {
            return;
        }
        MenuItem menuItem = new MenuItem();
        Label pathLabel = new Label(path);
        pathLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
        HBox graphic = new HBox(pathLabel);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        menuItem.setGraphic(graphic);
        menuItem.setText("");
        menuItem.setOnAction(event -> {
            // Build the list of elements to move.
            List<CardElement> elementsToMove;
            if (cell.isMiddleMultiSelectActive()) {
                elementsToMove = new ArrayList<>(SelectionManager.getSelectedMiddleElements());
            } else {
                CardElement singleElement = cell.getItem();
                if (singleElement == null) {
                    return;
                }
                elementsToMove = Collections.singletonList(singleElement);
            }

            // Remove each element from its source observable list.
            Set<Object> sourceOwners = new LinkedHashSet<>();
            for (CardElement cardElement : elementsToMove) {
                boolean wasRemoved = false;
                for (Map.Entry<CardsGroup, ObservableList<CardElement>> registryEntry
                        : CardGroupRegistry.GROUP_OBSERVABLE_LISTS.entrySet()) {
                    if (registryEntry.getValue().remove(cardElement)) {
                        Object owner = cell.findDacOwnerForCardsGroup(registryEntry.getKey());
                        if (owner != null) {
                            sourceOwners.add(owner);
                        }
                        wasRemoved = true;
                        break;
                    }
                }
                if (!wasRemoved && elementsToMove.size() == 1
                        && cell.getGridView() != null
                        && cell.getGridView().getItems() != null) {
                    cell.getGridView().getItems().remove(cardElement);
                }
            }

            // Mark source owners dirty.
            if (!sourceOwners.isEmpty()) {
                for (Object owner : sourceOwners) {
                    UserInterfaceFunctions.markDirty(owner);
                }
            } else {
                Object fallbackOwner = cell.resolveDecksTargetOwner(
                        excludePath != null ? excludePath : "");
                if (fallbackOwner != null) {
                    UserInterfaceFunctions.markDirty(fallbackOwner);
                } else {
                    UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                }
            }

            // Add all elements to the target list.
            List<CardElement> targetList = cell.resolveDecksTargetList(path);
            if (targetList != null) {
                targetList.addAll(elementsToMove);
                MenuActionHandler.setLastDecksAddedTarget(path);
                Object destOwner = cell.resolveDecksTargetOwner(path);
                if (destOwner != null) {
                    UserInterfaceFunctions.markDirty(destOwner);
                } else {
                    UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                }
            } else {
                logger.warn("addMoveDestItem: could not resolve path '{}'", path);
            }

            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();

            // If any source or destination belongs to a ThemeCollection's own list,
            // missing-sets are stale → force a full rebuild.
            boolean needsFullRebuild = sourceOwners.stream().anyMatch(
                    owner -> owner instanceof ThemeCollection);
            if (!needsFullRebuild) {
                Object destOwner = cell.resolveDecksTargetOwner(path);
                needsFullRebuild = destOwner instanceof ThemeCollection;
            }
            if (needsFullRebuild) {
                UserInterfaceFunctions.triggerDecksStructureRefresh();
            } else {
                UserInterfaceFunctions.refreshDecksAndCollectionsView();
            }
        });
        items.add(menuItem);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // "Add to..." for archetype cards (ADD semantics)
    // ─────────────────────────────────────────────────────────────────────────

    static List<MenuItem> buildAddDestinationMenuItems(CardGridCell cell) {
        List<MenuItem> items = new ArrayList<>();
        try {
            DecksAndCollectionsList decksAndCollectionsList = ensureDecksAndCollectionsList();
            if (decksAndCollectionsList == null) {
                return items;
            }

            if (decksAndCollectionsList.getCollections() != null) {
                for (ThemeCollection themeCollection : decksAndCollectionsList.getCollections()) {
                    if (themeCollection == null) {
                        continue;
                    }
                    String collName = CardNameUtils.sanitize(themeCollection.getName());
                    addAddDestItem(items, collName, cell);
                    if (themeCollection.getLinkedDecks() != null) {
                        for (List<Deck> deckUnit : themeCollection.getLinkedDecks()) {
                            if (deckUnit == null) {
                                continue;
                            }
                            for (Deck deck : deckUnit) {
                                if (deck == null) {
                                    continue;
                                }
                                String deckBase = collName + " / "
                                        + CardNameUtils.sanitize(deck.getName());
                                if (cell.addSectionAllowed("Main Deck")) {
                                    addAddDestItem(items, deckBase + " / Main Deck", cell);
                                }
                                if (cell.addSectionAllowed("Extra Deck")) {
                                    addAddDestItem(items, deckBase + " / Extra Deck", cell);
                                }
                                addAddDestItem(items, deckBase + " / Side Deck", cell);
                            }
                        }
                    }
                    addAddDestItem(items, collName + " / Exclusion List", cell);
                }
            }
            if (decksAndCollectionsList.getDecks() != null) {
                for (Deck deck : decksAndCollectionsList.getDecks()) {
                    if (deck == null) {
                        continue;
                    }
                    String deckName = CardNameUtils.sanitize(deck.getName());
                    if (cell.addSectionAllowed("Main Deck")) {
                        addAddDestItem(items, deckName + " / Main Deck", cell);
                    }
                    if (cell.addSectionAllowed("Extra Deck")) {
                        addAddDestItem(items, deckName + " / Extra Deck", cell);
                    }
                    addAddDestItem(items, deckName + " / Side Deck", cell);
                }
            }
        } catch (Exception buildError) {
            logger.debug("buildAddDestinationMenuItems failed", buildError);
        }
        return items;
    }

    private static void addAddDestItem(
            List<MenuItem> items, String path, CardGridCell cell) {
        if (path == null) {
            return;
        }
        MenuItem menuItem = new MenuItem();
        Label pathLabel = new Label(path);
        pathLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(pathLabel);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        menuItem.setGraphic(graphic);
        menuItem.setText("");
        menuItem.setOnAction(event -> {
            CardElement currentItem = cell.getItem();
            if (currentItem == null || currentItem.getCard() == null) {
                return;
            }

            java.util.Collection<Card> cardsToAdd;
            if (cell.isMiddleMultiSelectActive()) {
                cardsToAdd = SelectionManager.getSelectedCards();
            } else {
                cardsToAdd = Collections.singletonList(currentItem.getCard());
            }

            String[] pathParts = path.split("\\s*/\\s*");
            String lastPart = pathParts[pathParts.length - 1].trim()
                    .toLowerCase(java.util.Locale.ROOT);
            boolean isExclusion = lastPart.equals("exclusion list")
                    || lastPart.equals("cards not to add");

            boolean targetWasEmpty = cell.isDecksTargetEmpty(pathParts, isExclusion);

            if (isExclusion && pathParts.length >= 2) {
                MenuActionHandler.handleBulkAddToExclusionList(cardsToAdd, pathParts[0].trim());
            } else if (pathParts.length == 1) {
                MenuActionHandler.handleBulkAddToCollectionCards(cardsToAdd, pathParts[0].trim());
            } else {
                MenuActionHandler.handleBulkAddToDeck(cardsToAdd, path);
            }

            boolean affectsCollectionList = (pathParts.length == 1 || isExclusion);
            if (affectsCollectionList || targetWasEmpty) {
                UserInterfaceFunctions.triggerDecksStructureRefresh();
            } else {
                UserInterfaceFunctions.refreshDecksAndCollectionsView();
            }
        });
        items.add(menuItem);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // D&C and type-box proposals (previously in CardTreeCell)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds "Sort card" menu items proposing Decks & Collections destinations for
     * {@code card}.  Orange-styled items are pure quality-upgrade proposals; lime-styled
     * items represent cards that are genuinely needed.
     */
    static List<MenuItem> buildDecksAndCollectionsProposals(
            Card card, CardElement clickedElement, CardGridCell cell) {
        List<MenuItem> items = new ArrayList<>();
        try {
            if (card == null) {
                return items;
            }
            if (UserInterfaceFunctions.getDecksList() == null) {
                UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
            }
            DecksAndCollectionsList decksAndCollectionsList = UserInterfaceFunctions.getDecksList();
            if (decksAndCollectionsList == null) {
                return items;
            }

            OwnedCardsCollection ownedCollection = loadOwnedCollection();
            if (ownedCollection == null || ownedCollection.getOwnedCollection() == null) {
                return items;
            }

            Map<String, Boolean> proposedTargets = new LinkedHashMap<>();
            Set<String> upgradeOnlyTargets = new HashSet<>();

            if (decksAndCollectionsList.getCollections() != null) {
                for (ThemeCollection themeCollection : decksAndCollectionsList.getCollections()) {
                    if (themeCollection == null) {
                        continue;
                    }
                    int countInCollection = CardCollectionQuery.countCardInList(themeCollection.toList(), card);
                    if (countInCollection <= 0) {
                        continue;
                    }
                    // Compare the TC's required count against the total copies
                    // already placed in the TC-named group(s) of the owned collection,
                    // not against any single group in isolation (Bug 3B fix).
                    int countInOwned = CardCollectionQuery.countInOwnedForDeckCombined(
                            ownedCollection, themeCollection.getName(), card);
                    boolean needsMore = countInCollection > countInOwned;
                    boolean qualityUpgrade = !needsMore
                            && CardCollectionQuery.isQualityUpgradeFor(themeCollection.getCardsList(),
                            clickedElement);
                    if (needsMore || qualityUpgrade) {
                        String target = CardNameUtils.sanitize(
                                themeCollection.getName());
                        boolean existsInOwned = locationExistsInOwned(
                                themeCollection.getName(), ownedCollection);
                        proposedTargets.put(target, existsInOwned);
                        if (qualityUpgrade) {
                            upgradeOnlyTargets.add(target);
                        }
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
                                addDeckSectionProposals(deck, card, clickedElement,
                                        ownedCollection, proposedTargets, upgradeOnlyTargets);
                            }
                        }
                    }
                }
            }

            if (decksAndCollectionsList.getDecks() != null) {
                for (Deck deck : decksAndCollectionsList.getDecks()) {
                    if (deck == null) {
                        continue;
                    }
                    addDeckSectionProposals(deck, card, clickedElement,
                            ownedCollection, proposedTargets, upgradeOnlyTargets);
                }
            }

            // Build the deduplicated menu items.
            Set<String> labelsAdded = new HashSet<>();
            for (Map.Entry<String, Boolean> proposalEntry : proposedTargets.entrySet()) {
                String rawTarget = proposalEntry.getKey();
                boolean existsInOwned = proposalEntry.getValue() != null
                        && proposalEntry.getValue();
                if (rawTarget == null || rawTarget.trim().isEmpty()) {
                    continue;
                }
                final String handlerTarget = rawTarget;

                // Strip "/Main Deck" etc. from the visible label.
                String displayTarget = rawTarget;
                if (displayTarget.endsWith("/Main Deck")
                        || displayTarget.endsWith("/Extra Deck")
                        || displayTarget.endsWith("/Side Deck")) {
                    displayTarget = displayTarget.substring(0, displayTarget.lastIndexOf('/'));
                }

                String itemLabel;
                if (existsInOwned) {
                    itemLabel = displayTarget;
                } else {
                    String baseName = displayTarget;
                    if (baseName.contains("/")) {
                        String[] parts = baseName.split("/");
                        baseName = parts[parts.length - 1];
                    }
                    itemLabel = "Add " + baseName;
                }

                if (labelsAdded.contains(itemLabel)) {
                    continue;
                }
                labelsAdded.add(itemLabel);

                boolean isUpgradeOnly = upgradeOnlyTargets.contains(rawTarget);
                MenuItem menuItem = new MenuItem(itemLabel);
                menuItem.setStyle(isUpgradeOnly
                        ? "-fx-text-fill: #EB9E34;"
                        : "-fx-text-fill: #cdfc04;");
                if (itemLabel.startsWith("Add ")) {
                    final String catName = itemLabel.substring(4).trim();
                    menuItem.setOnAction(ev ->
                            MenuActionHandler.handleAddCategoryAndMove(clickedElement, catName));
                } else if (!itemLabel.startsWith("Swap")) {
                    menuItem.setOnAction(ev ->
                            MenuActionHandler.handleMove(clickedElement, handlerTarget));
                }
                items.add(menuItem);
            }
        } catch (Exception buildError) {
            logger.debug("buildDecksAndCollectionsProposals failed", buildError);
        }
        return items;
    }

    /**
     * Adds main/extra/side deck section proposals for a single Deck to {@code proposedTargets}.
     */
    private static void addDeckSectionProposals(
            Deck deck, Card card, CardElement clickedElement,
            OwnedCardsCollection ownedCollection,
            Map<String, Boolean> proposedTargets,
            Set<String> upgradeOnlyTargets) {

        int countMain = CardCollectionQuery.countCardInList(deck.getMainDeck(), card);
        int countExtra = CardCollectionQuery.countCardInList(deck.getExtraDeck(), card);
        int countSide = CardCollectionQuery.countCardInList(deck.getSideDeck(), card);
        int requiredTotal = countMain + countExtra + countSide;
        if (requiredTotal <= 0) {
            return;
        }

        int presentTotal = CardCollectionQuery.countInOwnedForDeckCombined(ownedCollection, deck.getName(), card);
        boolean needsMore = requiredTotal > presentTotal;
        boolean upgradeMain = !needsMore && countMain > 0
                && CardCollectionQuery.isQualityUpgradeFor(deck.getMainDeck(), clickedElement);
        boolean upgradeExtra = !needsMore && countExtra > 0
                && CardCollectionQuery.isQualityUpgradeFor(deck.getExtraDeck(), clickedElement);
        boolean upgradeSide = !needsMore && countSide > 0
                && CardCollectionQuery.isQualityUpgradeFor(deck.getSideDeck(), clickedElement);

        if (!needsMore && !upgradeMain && !upgradeExtra && !upgradeSide) {
            return;
        }

        boolean existsInOwned = locationExistsInOwned(deck.getName(), ownedCollection);
        String deckName = CardNameUtils.sanitize(deck.getName());

        if (countMain > 0 && Utils.DeckCompatibility.isCompatibleWith(card, "Main Deck")
                && (needsMore || upgradeMain)) {
            String target = deckName + "/Main Deck";
            proposedTargets.put(target, existsInOwned);
            if (!needsMore && upgradeMain) {
                upgradeOnlyTargets.add(target);
            }
        }
        if (countExtra > 0 && Utils.DeckCompatibility.isCompatibleWith(card, "Extra Deck")
                && (needsMore || upgradeExtra)) {
            String target = deckName + "/Extra Deck";
            proposedTargets.put(target, existsInOwned);
            if (!needsMore && upgradeExtra) {
                upgradeOnlyTargets.add(target);
            }
        }
        if (countSide > 0 && (needsMore || upgradeSide)) {
            String target = deckName + "/Side Deck";
            proposedTargets.put(target, existsInOwned);
            if (!needsMore && upgradeSide) {
                upgradeOnlyTargets.add(target);
            }
        }
    }

    /**
     * Builds "Sort card" menu items proposing type-of-cards box destinations based on the
     * card's type and properties.
     */
    static List<MenuItem> buildTypeBoxProposals(
            Card card, CardElement clickedElement, CardGridCell cell) {
        List<MenuItem> items = new ArrayList<>();
        try {
            if (card == null) {
                return items;
            }

            Set<String> desiredFrenchCategories = new java.util.LinkedHashSet<>();
            String cardType = card.getCardType() == null ? "" : card.getCardType().trim();
            List<String> cardProperties = card.getCardProperties();
            Set<String> propertySet = new HashSet<>();
            if (cardProperties != null) {
                for (String property : cardProperties) {
                    if (property != null) {
                        propertySet.add(property.trim());
                    }
                }
            }

            if (cardType.toLowerCase().contains("trap")) {
                if (propertySet.contains("Counter")) {
                    desiredFrenchCategories.add("Pièges Contre");
                }
                if (propertySet.contains("Continuous")) {
                    desiredFrenchCategories.add("Pièges Continus");
                }
                if (propertySet.contains("Normal")) {
                    desiredFrenchCategories.add("Pièges Normaux");
                }
                desiredFrenchCategories.add("Pièges");
            }
            if (cardType.toLowerCase().contains("spell")) {
                if (propertySet.contains("Continuous")) {
                    desiredFrenchCategories.add("Magies Continues");
                }
                if (propertySet.contains("Quick-Play") || propertySet.contains("Quick Play")) {
                    desiredFrenchCategories.add("Magies Jeu-Rapide");
                }
                if (propertySet.contains("Equip")) {
                    desiredFrenchCategories.add("Magies Équipement");
                }
                if (propertySet.contains("Field")) {
                    desiredFrenchCategories.add("Magies Terrain");
                }
                if (propertySet.contains("Ritual")) {
                    desiredFrenchCategories.add("Magies Rituel");
                }
                if (propertySet.contains("Normal")) {
                    desiredFrenchCategories.add("Magies Normales");
                }
                desiredFrenchCategories.add("Magies");
            }
            if (cardType.toLowerCase().contains("monster")) {
                if (propertySet.contains("Effect")) {
                    desiredFrenchCategories.add("Monstres à Effet");
                }
                if (propertySet.contains("Tuner")) {
                    desiredFrenchCategories.add("Monstres Syntoniseurs");
                }
                if (propertySet.contains("Synchro")) {
                    desiredFrenchCategories.add("Monstres Synchro");
                }
                if (propertySet.contains("Pendulum")) {
                    desiredFrenchCategories.add("Monstres Pendule");
                }
                if (propertySet.contains("Fusion")) {
                    desiredFrenchCategories.add("Monstres Fusion");
                }
                if (propertySet.contains("Xyz")) {
                    desiredFrenchCategories.add("Monstres Xyz");
                }
                if (propertySet.contains("Link")) {
                    desiredFrenchCategories.add("Monstres Lien");
                }
                if (propertySet.contains("Ritual")) {
                    desiredFrenchCategories.add("Monstres Rituel");
                }
                if (propertySet.contains("Normal")) {
                    desiredFrenchCategories.add("Monstres Normaux");
                }
                if (propertySet.contains("Toon")) {
                    desiredFrenchCategories.add("Monstres Toon");
                }
                if (propertySet.contains("Flip")) {
                    desiredFrenchCategories.add("Monstres Flip");
                }
                if (propertySet.contains("Spirit")) {
                    desiredFrenchCategories.add("Monstres Spirit");
                }
                if (propertySet.contains("Union")) {
                    desiredFrenchCategories.add("Monstres Union");
                }
                if (propertySet.contains("Gemini")) {
                    desiredFrenchCategories.add("Monstres Gemini");
                }
                desiredFrenchCategories.add("Monstres");
            }

            if (desiredFrenchCategories.isEmpty()) {
                return items;
            }

            OwnedCardsCollection ownedCollection = loadOwnedCollection();
            if (ownedCollection == null || ownedCollection.getOwnedCollection() == null) {
                return items;
            }

            // Build a map from sanitised name → raw location strings.
            Map<String, List<String>> categoryToLocations = new LinkedHashMap<>();
            for (Box box : ownedCollection.getOwnedCollection()) {
                String rawBoxName = box.getName() == null ? "" : box.getName();
                String sanitisedBox = CardNameUtils.sanitize(rawBoxName).toLowerCase();
                categoryToLocations.computeIfAbsent(sanitisedBox, k -> new ArrayList<>())
                        .add(rawBoxName);
                if (box.getContent() != null) {
                    for (CardsGroup group : box.getContent()) {
                        String rawGroupName = group.getName() == null ? "" : group.getName();
                        String sanitisedGroup = CardNameUtils.sanitize(rawGroupName).toLowerCase();
                        categoryToLocations
                                .computeIfAbsent(sanitisedGroup, k -> new ArrayList<>())
                                .add(rawBoxName + "/" + rawGroupName);
                    }
                }
            }

            for (String desired : desiredFrenchCategories) {
                if (desired == null || desired.trim().isEmpty()) {
                    continue;
                }
                String desiredSanitised = CardNameUtils.sanitize(desired).toLowerCase();
                List<String> locations = categoryToLocations.get(desiredSanitised);
                if (locations == null || locations.isEmpty()) {
                    continue;
                }

                for (String rawLocation : locations) {
                    String[] locationParts = rawLocation.split("/", 2);
                    String boxRaw = locationParts.length > 0 ? locationParts[0] : "";
                    String groupRaw = locationParts.length > 1 ? locationParts[1] : null;

                    // Skip if the card is already in this location.
                    boolean alreadyThere = false;
                    for (Box box : ownedCollection.getOwnedCollection()) {
                        if (!CardNameUtils.sanitize(box.getName()).equalsIgnoreCase(
                                CardNameUtils.sanitize(boxRaw))) {
                            continue;
                        }
                        if (groupRaw == null) {
                            if (box.getContent() != null) {
                                for (CardsGroup group : box.getContent()) {
                                    if (CardCollectionQuery.countCardInList(group.getCardList(), card) > 0) {
                                        alreadyThere = true;
                                        break;
                                    }
                                }
                            }
                        } else {
                            if (box.getContent() != null) {
                                for (CardsGroup group : box.getContent()) {
                                    if (CardNameUtils.sanitize(group.getName())
                                            .equalsIgnoreCase(CardNameUtils.sanitize(groupRaw))) {
                                        if (CardCollectionQuery.countCardInList(group.getCardList(), card) > 0) {
                                            alreadyThere = true;
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                        if (alreadyThere) {
                            break;
                        }
                    }
                    if (alreadyThere) {
                        continue;
                    }

                    String displayBox = CardNameUtils.sanitize(boxRaw);
                    String displayTarget = groupRaw == null
                            ? displayBox
                            : displayBox + "/" + CardNameUtils.sanitize(groupRaw);
                    final String handlerTarget = displayTarget;

                    MenuItem menuItem = new MenuItem(displayTarget);
                    menuItem.setOnAction(ev -> MenuActionHandler.handleMove(clickedElement, handlerTarget));
                    items.add(menuItem);
                }
            }
        } catch (Exception buildError) {
            logger.debug("buildTypeBoxProposals failed", buildError);
        }
        return items;
    }

    private static boolean locationExistsInOwned(String name, OwnedCardsCollection owned) {
        if (name == null || name.trim().isEmpty() || owned == null
                || owned.getOwnedCollection() == null) {
            return false;
        }
        String targetSan = CardNameUtils.sanitize(name).toLowerCase();
        for (Box box : owned.getOwnedCollection()) {
            if (box == null) {
                continue;
            }
            if (CardNameUtils.sanitize(box.getName()).toLowerCase().equals(targetSan)) {
                return true;
            }
            if (box.getContent() != null) {
                for (CardsGroup group : box.getContent()) {
                    if (group != null && CardNameUtils.sanitize(group.getName())
                            .toLowerCase().equals(targetSan)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data-loading helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static OwnedCardsCollection loadOwnedCollection() {
        OwnedCardsCollection owned = null;
        try {
            owned = Model.CardsLists.OuicheList.getMyCardsCollection();
        } catch (Throwable ignored) {
        }
        if (owned == null) {
            try {
                UserInterfaceFunctions.loadCollectionFile();
            } catch (Throwable ignored) {
            }
            try {
                owned = Model.CardsLists.OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
        }
        return owned;
    }

    private static DecksAndCollectionsList ensureDecksAndCollectionsList() {
        DecksAndCollectionsList decksAndCollectionsList = UserInterfaceFunctions.getDecksList();
        if (decksAndCollectionsList == null) {
            try {
                UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
            } catch (Exception ignored) {
            }
            decksAndCollectionsList = UserInterfaceFunctions.getDecksList();
        }
        return decksAndCollectionsList;
    }
}