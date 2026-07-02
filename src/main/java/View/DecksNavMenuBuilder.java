package View;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static Utils.CardNameUtils.sanitize;

/**
 * Static factory for context menus shown on the Decks and Collections navigation pane.
 *
 * <p>Shared styling and dialog helpers live in {@link NavigationContextMenuBuilder}.
 * This class contains only the Decks and Collections specific entry points and
 * their private supporting methods.</p>
 */
public final class DecksNavMenuBuilder {

    private static final Logger logger = LoggerFactory.getLogger(DecksNavMenuBuilder.class);

    private DecksNavMenuBuilder() {
    }

    // =========================================================================
    // Public entry points
    // =========================================================================

    /**
     * Context menu for a {@link Model.CardsLists.ThemeCollection} node.
     * Collections cannot be moved, so there is no "Move to…" entry.
     */
    public static ContextMenu forDecksCollection(
            Model.CardsLists.ThemeCollection collection,
            Model.CardsLists.DecksAndCollectionsList decksAndCollections) {

        ContextMenu contextMenu = NavigationContextMenuBuilder.styledContextMenu();

        Runnable removeAction = () -> {
            if (!NavigationContextMenuBuilder.isCollectionEmpty(collection)
                    && !NavigationContextMenuBuilder.confirmRemoval("Collection")) {
                return;
            }
            if (decksAndCollections != null && decksAndCollections.getCollections() != null) {
                decksAndCollections.getCollections().remove(collection);
                Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            }
        };

        contextMenu.getItems().addAll(
                makeDecksAddCollectionAfterItem(collection, decksAndCollections),
                makeDecksAddLooseCollectionAfterItem(collection, decksAndCollections),
                makeDecksAddLinkedDeckToCollectionItem(collection),
                makeDecksAddArchetypeMenu(collection),
                makeDecksRenameItem(collection),
                NavigationContextMenuBuilder.makePasteItem(() -> {
                    if (collection.getCardsList() == null) {
                        collection.setCardsList(new java.util.ArrayList<>());
                    }
                    for (Model.CardsLists.CardElement clipboardElement
                            : Controller.CardClipboard.getContents()) {
                        if (clipboardElement != null
                                && clipboardElement.getCard() != null) {
                            collection.getCardsList().add(
                                    new Model.CardsLists.CardElement(
                                            clipboardElement.getCard()));
                        }
                    }
                    Controller.UserInterfaceFunctions.markDirty(collection);
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
                }),
                new SeparatorMenuItem(),
                NavigationContextMenuBuilder.makeRemoveItem(removeAction)
        );
        return contextMenu;
    }

    /**
     * Context menu for a {@link Model.CardsLists.Deck} node. Provides Move,
     * Add Collection/Deck, Create Collection (standalone only), Rename, Paste,
     * and Remove actions.
     */
    public static ContextMenu forDecksDeck(
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList decksAndCollections) {

        ContextMenu contextMenu = NavigationContextMenuBuilder.styledContextMenu();

        Model.CardsLists.ThemeCollection parentCollection =
                findParentCollectionOfDeck(deck, decksAndCollections);
        boolean isLinked = parentCollection != null;

        MenuItem addDeckItem = isLinked
                ? makeDecksAddLinkedDeckAfterItem(deck, parentCollection)
                : makeDecksAddStandaloneDeckAfterItem(deck, decksAndCollections);

        Menu moveMenu = NavigationContextMenuBuilder.makeLazyMenu("Move to...");
        moveMenu.setOnShowing(evt ->
                populateDeckMoveMenu(moveMenu, deck, decksAndCollections, parentCollection, isLinked));

        Runnable removeAction = () -> {
            if (!NavigationContextMenuBuilder.isDeckEmpty(deck)
                    && !NavigationContextMenuBuilder.confirmRemoval("Deck")) {
                return;
            }
            boolean removed = false;
            if (decksAndCollections.getDecks() != null) {
                removed = decksAndCollections.getDecks().remove(deck);
            }
            if (!removed && decksAndCollections.getCollections() != null) {
                outer:
                for (Model.CardsLists.ThemeCollection themeCollection : decksAndCollections.getCollections()) {
                    if (themeCollection == null || themeCollection.getLinkedDecks() == null) {
                        continue;
                    }
                    for (List<Model.CardsLists.Deck> group : themeCollection.getLinkedDecks()) {
                        if (group != null && group.remove(deck)) {
                            removed = true;
                            break outer;
                        }
                    }
                }
            }
            if (removed) {
                Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            }
        };

        List<MenuItem> menuItems = new java.util.ArrayList<>();
        menuItems.add(moveMenu);
        menuItems.add(makeDecksAddCollectionAfterDeckItem(decksAndCollections));
        menuItems.add(makeDecksAddLooseCollectionAfterDeckItem(decksAndCollections));
        menuItems.add(addDeckItem);
        if (!isLinked) {
            menuItems.add(makeDecksCreateCollectionFromDeckItem(deck, decksAndCollections));
        }
        menuItems.add(makeDecksRenameItem(deck));
        menuItems.add(NavigationContextMenuBuilder.makePasteItem(() -> {
            for (Model.CardsLists.CardElement clipboardElement
                    : Controller.CardClipboard.getContents()) {
                if (clipboardElement != null && clipboardElement.getCard() != null) {
                    deck.getMainDeck().add(
                            new Model.CardsLists.CardElement(clipboardElement.getCard()));
                }
            }
            Controller.UserInterfaceFunctions.markDirty(deck);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
        }));
        menuItems.add(new SeparatorMenuItem());
        menuItems.add(NavigationContextMenuBuilder.makeRemoveItem(removeAction));
        contextMenu.getItems().addAll(menuItems);
        return contextMenu;
    }

    /**
     * Context menu shown when right-clicking the empty background of the Decks
     * and Collections navigation pane. Provides Add Collection, Add Loose Collection,
     * and Add Deck actions.
     */
    public static ContextMenu forDecksEmpty() {
        ContextMenu contextMenu = NavigationContextMenuBuilder.styledContextMenu();
        contextMenu.getItems().addAll(
                makeDecksAddCollectionAtEndItem(),
                makeDecksAddLooseCollectionAtEndItem(),
                makeDecksAddStandaloneDeckAtEndItem()
        );
        return contextMenu;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Populates {@code moveMenu} with every valid "move this deck to..." destination:
     * every existing deck group within every collection, a "New Deck group" entry
     * per collection, and — when {@code deck} is currently linked — a "No Collection"
     * entry to make it standalone. The deck group it already belongs to is excluded.
     */
    private static void populateDeckMoveMenu(
            Menu moveMenu,
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList decksAndCollections,
            Model.CardsLists.ThemeCollection parentCollection,
            boolean isLinked) {
        moveMenu.getItems().clear();

        if (decksAndCollections == null) {
            moveMenu.getItems().add(NavigationContextMenuBuilder.disabledItem(
                    "No data available"));
            return;
        }

        if (decksAndCollections.getCollections() != null) {
            for (Model.CardsLists.ThemeCollection themeCollection : decksAndCollections.getCollections()) {
                if (themeCollection == null) {
                    continue;
                }
                String collName = sanitize(themeCollection.getName());
                if (collName.isEmpty()) {
                    collName = "(Unnamed collection)";
                }

                List<List<Model.CardsLists.Deck>> units = themeCollection.getLinkedDecks();
                int groupCount = (units == null) ? 0 : units.size();

                for (int unitIndex = 0; unitIndex < groupCount; unitIndex++) {
                    final int finalUnitIndex = unitIndex;
                    final Model.CardsLists.ThemeCollection targetColl = themeCollection;
                    final String label = collName + " / Deck group " + (unitIndex + 1);

                    if (themeCollection == parentCollection
                            && units.get(unitIndex) != null
                            && units.get(unitIndex).contains(deck)) {
                        continue;
                    }
                    moveMenu.getItems().add(makeMoveDeckToUnitItem(
                            label, deck, decksAndCollections, targetColl, finalUnitIndex));
                }

                final Model.CardsLists.ThemeCollection targetCollNew = themeCollection;
                final String newGroupLabel = collName + " / New Deck group";
                moveMenu.getItems().add(makeMoveDeckToNewUnitItem(
                        newGroupLabel, deck, decksAndCollections, targetCollNew));
            }
        }

        if (isLinked) {
            if (!moveMenu.getItems().isEmpty()) {
                moveMenu.getItems().add(new SeparatorMenuItem());
            }
            moveMenu.getItems().add(
                    makeMoveDeckToStandaloneItem(deck, decksAndCollections, parentCollection));
        }

        if (moveMenu.getItems().isEmpty()) {
            moveMenu.getItems().add(NavigationContextMenuBuilder.disabledItem(
                    "No destinations available"));
        }
    }

    /**
     * Builds the lazy "Add archetype" submenu for a ThemeCollection.
     *
     * <p>Ordering rules:
     * <ol>
     *   <li>Archetypes whose name is contained in the collection name (accent-
     *       and case-insensitive) appear first, highlighted in accent colour.</li>
     *   <li>Among the rest, sort by the number of collection cards that belong
     *       to that archetype (highest first).</li>
     *   <li>Within each tier, preserve the original SubListCreator order.</li>
     * </ol>
     * Archetypes already present in {@code collection.getArchetypes()} are excluded.</p>
     */
    private static Menu makeDecksAddArchetypeMenu(
            Model.CardsLists.ThemeCollection collection) {

        Menu menu = NavigationContextMenuBuilder.makeLazyMenu("Add archetype");
        menu.setOnShowing(evt -> populateArchetypeMenu(menu, collection));
        return menu;
    }

    /**
     * Populates {@code menu} with every archetype not already on {@code collection},
     * ordered per the rules described on {@link #makeDecksAddArchetypeMenu}.
     */
    private static void populateArchetypeMenu(
            Menu menu,
            Model.CardsLists.ThemeCollection collection) {
        menu.getItems().clear();

        List<String> allNames =
                Model.CardsLists.SubListCreator.getArchetypesList();
        List<List<Model.CardsLists.Card>> allCards =
                Model.CardsLists.SubListCreator.getArchetypesCardsLists();

        if (allNames == null || allNames.isEmpty()) {
            menu.getItems().add(NavigationContextMenuBuilder.disabledItem(
                    "No archetypes available"));
            return;
        }

        java.util.Set<String> alreadyAdded = new java.util.HashSet<>();
        try {
            List<String> existing = collection.getArchetypes();
            if (existing != null) {
                for (String archName : existing) {
                    if (archName != null) {
                        alreadyAdded.add(normalizeForSort(archName));
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        java.util.Set<String> collPassCodes = new java.util.HashSet<>();
        java.util.Set<String> collKonamiIds = new java.util.HashSet<>();

        if (collection.getCardsList() != null) {
            for (Model.CardsLists.CardElement cardElement : collection.getCardsList()) {
                if (cardElement == null || cardElement.getCard() == null) {
                    continue;
                }
                if (cardElement.getCard().getPassCode() != null) {
                    collPassCodes.add(cardElement.getCard().getPassCode());
                }
                if (cardElement.getCard().getKonamiId() != null) {
                    collKonamiIds.add(cardElement.getCard().getKonamiId());
                }
            }
        }
        if (collection.getLinkedDecks() != null) {
            for (List<Model.CardsLists.Deck> unit : collection.getLinkedDecks()) {
                if (unit == null) {
                    continue;
                }
                for (Model.CardsLists.Deck deck : unit) {
                    if (deck == null) {
                        continue;
                    }
                    for (Model.CardsLists.CardElement cardElement : deck.toList()) {
                        if (cardElement == null || cardElement.getCard() == null) {
                            continue;
                        }
                        if (cardElement.getCard().getPassCode() != null) {
                            collPassCodes.add(cardElement.getCard().getPassCode());
                        }
                        if (cardElement.getCard().getKonamiId() != null) {
                            collKonamiIds.add(cardElement.getCard().getKonamiId());
                        }
                    }
                }
            }
        }

        String collNorm = normalizeForSort(
                collection.getName() == null ? "" : collection.getName());

        // Each entry: [originalIndex, cardCount, inNameFlag(1/0)]
        List<int[]> entries = new java.util.ArrayList<>();
        for (int i = 0; i < allNames.size(); i++) {
            String archName = allNames.get(i);
            if (archName == null) {
                continue;
            }
            if (alreadyAdded.contains(normalizeForSort(archName))) {
                continue;
            }
            boolean inName = collNorm.contains(normalizeForSort(archName));
            int count = 0;
            if (allCards != null && i < allCards.size()) {
                List<Model.CardsLists.Card> archCardList = allCards.get(i);
                if (archCardList != null) {
                    for (Model.CardsLists.Card card : archCardList) {
                        if (card == null) {
                            continue;
                        }
                        boolean matchPass = card.getPassCode() != null
                                && collPassCodes.contains(card.getPassCode());
                        boolean matchKonami = card.getKonamiId() != null
                                && collKonamiIds.contains(card.getKonamiId());
                        if (matchPass || matchKonami) {
                            count++;
                        }
                    }
                }
            }
            entries.add(new int[]{i, count, inName ? 1 : 0});
        }

        // Sort: inName DESC → count DESC → original index ASC
        entries.sort((firstEntry, secondEntry) -> {
            if (secondEntry[2] != firstEntry[2]) {
                return secondEntry[2] - firstEntry[2];
            }
            if (secondEntry[1] != firstEntry[1]) {
                return secondEntry[1] - firstEntry[1];
            }
            return firstEntry[0] - secondEntry[0];
        });

        boolean separatorInserted = false;
        boolean inNameSectionEnded = false;

        for (int[] entry : entries) {
            int idx = entry[0];
            int count = entry[1];
            boolean inName = entry[2] == 1;
            String archName = allNames.get(idx);

            if (!inNameSectionEnded && !inName && separatorInserted) {
                menu.getItems().add(new SeparatorMenuItem());
                inNameSectionEnded = true;
            }

            String labelText = count > 0
                    ? archName + "  (" + count + ")"
                    : archName;

            Label label = new Label(labelText);
            label.setStyle(inName
                    ? "-fx-text-fill: #cdfc04; -fx-font-size: 13; -fx-font-weight: bold;"
                    : "-fx-text-fill: white; -fx-font-size: 13;");

            HBox graphic = new HBox(label);
            graphic.setAlignment(Pos.CENTER_LEFT);
            graphic.setPadding(new Insets(2, 6, 2, 6));

            MenuItem menuItem = new MenuItem();
            menuItem.setGraphic(graphic);
            menuItem.setText("");

            final String finalArchName = archName;
            final List<Model.CardsLists.Card> finalArchCards =
                    (allCards != null && idx < allCards.size())
                            ? allCards.get(idx) : null;

            menuItem.setOnAction(e -> {
                if (collection.getArchetypes() == null) {
                    collection.setArchetypes(new java.util.ArrayList<>());
                }
                boolean duplicate = false;
                for (String existingName : collection.getArchetypes()) {
                    if (finalArchName.equalsIgnoreCase(existingName)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    collection.getArchetypes().add(finalArchName);
                    Controller.UserInterfaceFunctions.markDirty(collection);
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    logger.debug("Added archetype '{}' to collection '{}'",
                            finalArchName, collection.getName());
                }
                Controller.UserInterfaceFunctions.triggerDecksStructureRefresh();
                List<Model.CardsLists.Card> popupCards =
                        finalArchCards != null
                                ? finalArchCards
                                : java.util.Collections.emptyList();
                javafx.application.Platform.runLater(() -> {
                    ArchetypeCardSelectionPopup popup =
                            new ArchetypeCardSelectionPopup(
                                    finalArchName, popupCards, collection);
                    popup.setOnOk(() -> {
                        Controller.UserInterfaceFunctions.setPendingDecksFullRebuild();
                        Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
                    });
                    popup.showCenteredOn(menuItem.getParentPopup() != null
                            ? menuItem.getParentPopup().getOwnerNode() : null);
                });
            });

            menu.getItems().add(menuItem);
            if (inName) {
                separatorInserted = true;
            }
        }

        if (menu.getItems().isEmpty()) {
            menu.getItems().add(NavigationContextMenuBuilder.disabledItem(
                    "All archetypes already added"));
        }
    }

    /**
     * Normalises a string for accent- and case-insensitive comparison:
     * lower-case, NFD decomposition, strip combining marks, collapse
     * non-alphanumeric runs to a single space.
     */
    private static String normalizeForSort(String text) {
        if (text == null) {
            return "";
        }
        String result = text.toLowerCase(java.util.Locale.ROOT).trim();
        result = java.text.Normalizer.normalize(result, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        result = result.replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return result;
    }

    /**
     * "Create Collection" — only for standalone decks. Creates a new ThemeCollection
     * pre-named after the deck, moves the deck into it, then triggers the
     * pending-create-collection rename flow.
     */
    private static MenuItem makeDecksCreateCollectionFromDeckItem(
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList decksAndCollections) {
        return NavigationContextMenuBuilder.makeActionItem("Create Collection", () -> {
            String defaultName = (deck.getName() == null || deck.getName().trim().isEmpty())
                    ? "New Collection"
                    : deck.getName().trim();
            Model.CardsLists.ThemeCollection newCollection =
                    new Model.CardsLists.ThemeCollection();
            newCollection.setName(defaultName);
            if (decksAndCollections.getDecks() != null) {
                decksAndCollections.getDecks().remove(deck);
            }
            newCollection.addDeck(deck);
            if (decksAndCollections.getCollections() == null) {
                decksAndCollections.setCollections(new java.util.ArrayList<>());
            }
            decksAndCollections.getCollections().add(newCollection);
            Controller.UserInterfaceFunctions.setPendingDecksCreateCollectionData(
                    new Object[]{newCollection, deck});
            Controller.UserInterfaceFunctions.markDirty(newCollection);
            Controller.UserInterfaceFunctions.markDirty(deck);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
        });
    }

    /**
     * "Add Loose Collection" from a Collection context: inserts after {@code reference}.
     */
    private static MenuItem makeDecksAddLooseCollectionAfterItem(
            Model.CardsLists.ThemeCollection reference,
            Model.CardsLists.DecksAndCollectionsList decksAndCollections) {
        return NavigationContextMenuBuilder.makeActionItem("Add Loose Collection", () -> {
            Model.CardsLists.ThemeCollection newCollection =
                    new Model.CardsLists.ThemeCollection();
            newCollection.setName("New Loose Collection");
            newCollection.setConnectToWholeCollection(true);
            if (decksAndCollections.getCollections() == null) {
                decksAndCollections.setCollections(new java.util.ArrayList<>());
            }
            int idx = decksAndCollections.getCollections().indexOf(reference);
            if (idx >= 0) {
                decksAndCollections.getCollections().add(idx + 1, newCollection);
            } else {
                decksAndCollections.getCollections().add(newCollection);
            }
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newCollection);
            NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markDirty(newCollection);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Loose Collection" from a Deck context: appends at end.
     */
    private static MenuItem makeDecksAddLooseCollectionAfterDeckItem(
            Model.CardsLists.DecksAndCollectionsList decksAndCollections) {
        return NavigationContextMenuBuilder.makeActionItem("Add Loose Collection", () -> {
            Model.CardsLists.ThemeCollection newCollection =
                    new Model.CardsLists.ThemeCollection();
            newCollection.setName("New Loose Collection");
            newCollection.setConnectToWholeCollection(true);
            if (decksAndCollections.getCollections() == null) {
                decksAndCollections.setCollections(new java.util.ArrayList<>());
            }
            decksAndCollections.getCollections().add(newCollection);
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newCollection);
            NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markDirty(newCollection);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Loose Collection" from the empty-background context: appends at end.
     */
    private static MenuItem makeDecksAddLooseCollectionAtEndItem() {
        return NavigationContextMenuBuilder.makeActionItem("Add Loose Collection", () -> {
            Model.CardsLists.DecksAndCollectionsList decksAndCollections =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (decksAndCollections == null) {
                return;
            }
            Model.CardsLists.ThemeCollection newCollection =
                    new Model.CardsLists.ThemeCollection();
            newCollection.setName("New Loose Collection");
            newCollection.setConnectToWholeCollection(true);
            if (decksAndCollections.getCollections() == null) {
                decksAndCollections.setCollections(new java.util.ArrayList<>());
            }
            decksAndCollections.getCollections().add(newCollection);
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newCollection);
            NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markDirty(newCollection);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Collection" from a Collection context: inserts after {@code reference}.
     */
    private static MenuItem makeDecksAddCollectionAfterItem(
            Model.CardsLists.ThemeCollection reference,
            Model.CardsLists.DecksAndCollectionsList decksAndCollections) {
        return NavigationContextMenuBuilder.makeActionItem("Add Collection", () -> {
            Model.CardsLists.ThemeCollection newCollection =
                    new Model.CardsLists.ThemeCollection();
            newCollection.setName("New Collection");
            if (decksAndCollections.getCollections() == null) {
                decksAndCollections.setCollections(new java.util.ArrayList<>());
            }
            int idx = decksAndCollections.getCollections().indexOf(reference);
            if (idx >= 0) {
                decksAndCollections.getCollections().add(idx + 1, newCollection);
            } else {
                decksAndCollections.getCollections().add(newCollection);
            }
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newCollection);
            NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markDirty(newCollection);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Collection" from a Deck context: appends at end.
     */
    private static MenuItem makeDecksAddCollectionAfterDeckItem(
            Model.CardsLists.DecksAndCollectionsList decksAndCollections) {
        return NavigationContextMenuBuilder.makeActionItem("Add Collection", () -> {
            Model.CardsLists.ThemeCollection newCollection =
                    new Model.CardsLists.ThemeCollection();
            newCollection.setName("New Collection");
            if (decksAndCollections.getCollections() == null) {
                decksAndCollections.setCollections(new java.util.ArrayList<>());
            }
            decksAndCollections.getCollections().add(newCollection);
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newCollection);
            NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markDirty(newCollection);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Collection" from the empty-background context: appends at end.
     */
    private static MenuItem makeDecksAddCollectionAtEndItem() {
        return NavigationContextMenuBuilder.makeActionItem("Add Collection", () -> {
            Model.CardsLists.DecksAndCollectionsList decksAndCollections =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (decksAndCollections == null) {
                return;
            }
            Model.CardsLists.ThemeCollection newCollection =
                    new Model.CardsLists.ThemeCollection();
            newCollection.setName("New Collection");
            if (decksAndCollections.getCollections() == null) {
                decksAndCollections.setCollections(new java.util.ArrayList<>());
            }
            decksAndCollections.getCollections().add(newCollection);
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newCollection);
            NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markDirty(newCollection);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Deck" from a Collection context: adds a new linked deck unit to the collection.
     */
    private static MenuItem makeDecksAddLinkedDeckToCollectionItem(
            Model.CardsLists.ThemeCollection collection) {
        return NavigationContextMenuBuilder.makeActionItem("Add Deck", () -> {
            Model.CardsLists.Deck newDeck = new Model.CardsLists.Deck();
            newDeck.setName("New Deck");
            collection.addDeck(newDeck);
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newDeck);
            NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markDirty(collection);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Deck" from a linked Deck context: inserts a new unit immediately after
     * the unit that contains {@code reference}.
     */
    private static MenuItem makeDecksAddLinkedDeckAfterItem(
            Model.CardsLists.Deck reference,
            Model.CardsLists.ThemeCollection parentCollection) {
        return NavigationContextMenuBuilder.makeActionItem("Add Deck", () -> {
            Model.CardsLists.Deck newDeck = new Model.CardsLists.Deck();
            newDeck.setName("New Deck");
            List<List<Model.CardsLists.Deck>> units = parentCollection.getLinkedDecks();
            if (units != null) {
                int unitIdx = -1;
                outer:
                for (int i = 0; i < units.size(); i++) {
                    List<Model.CardsLists.Deck> unit = units.get(i);
                    if (unit == null) {
                        continue;
                    }
                    for (Model.CardsLists.Deck deck : unit) {
                        if (deck == reference) {
                            unitIdx = i;
                            break outer;
                        }
                    }
                }
                List<Model.CardsLists.Deck> newUnit = new java.util.ArrayList<>();
                newUnit.add(newDeck);
                if (unitIdx >= 0) {
                    units.add(unitIdx + 1, newUnit);
                } else {
                    units.add(newUnit);
                }
            } else {
                parentCollection.addDeck(newDeck);
            }
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newDeck);
            NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markDirty(parentCollection);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Deck" from a standalone Deck context: inserts after {@code reference}.
     */
    private static MenuItem makeDecksAddStandaloneDeckAfterItem(
            Model.CardsLists.Deck reference,
            Model.CardsLists.DecksAndCollectionsList decksAndCollections) {
        return NavigationContextMenuBuilder.makeActionItem("Add Deck", () -> {
            Model.CardsLists.Deck newDeck = new Model.CardsLists.Deck();
            newDeck.setName("New Deck");
            if (decksAndCollections.getDecks() == null) {
                decksAndCollections.setDecks(new java.util.ArrayList<>());
            }
            int idx = decksAndCollections.getDecks().indexOf(reference);
            if (idx >= 0) {
                decksAndCollections.getDecks().add(idx + 1, newDeck);
            } else {
                decksAndCollections.getDecks().add(newDeck);
            }
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newDeck);
            NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markDirty(newDeck);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Deck" from the empty-background context: appends a standalone deck at end.
     */
    private static MenuItem makeDecksAddStandaloneDeckAtEndItem() {
        return NavigationContextMenuBuilder.makeActionItem("Add Deck", () -> {
            Model.CardsLists.DecksAndCollectionsList decksAndCollections =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (decksAndCollections == null) {
                return;
            }
            Model.CardsLists.Deck newDeck = new Model.CardsLists.Deck();
            newDeck.setName("New Deck");
            if (decksAndCollections.getDecks() == null) {
                decksAndCollections.setDecks(new java.util.ArrayList<>());
            }
            decksAndCollections.getDecks().add(newDeck);
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newDeck);
            NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markDirty(newDeck);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Rename" for an existing Deck or ThemeCollection. Walks up from the context-menu
     * owner node to find the NavigationItem and starts an inline rename on it.
     */
    private static MenuItem makeDecksRenameItem(Object modelObject) {
        MenuItem menuItem = new MenuItem();
        Label label = new Label("Rename");
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        menuItem.setGraphic(graphic);
        menuItem.setText("");

        menuItem.setOnAction(e -> {
            javafx.scene.Node owner = null;
            try {
                owner = menuItem.getParentPopup().getOwnerNode();
            } catch (Throwable ignored) {
            }
            NavigationItem navItem =
                    NavigationContextMenuBuilder.findNavigationItemAncestor(owner);
            if (navItem == null) {
                return;
            }
            navItem.startInlineRename(
                    newName -> {
                        if (modelObject instanceof Model.CardsLists.Deck) {
                            ((Model.CardsLists.Deck) modelObject).setName(newName);
                        } else if (modelObject instanceof Model.CardsLists.ThemeCollection) {
                            ((Model.CardsLists.ThemeCollection) modelObject).setName(newName);
                        }
                        navItem.getLabel().setText(newName);
                        NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
                        Controller.UserInterfaceFunctions.markDirty(modelObject);
                        Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    },
                    null
            );
        });
        return menuItem;
    }

    /**
     * Returns the ThemeCollection that contains {@code deck} as a linked deck,
     * or {@code null} if the deck is standalone.
     */
    private static Model.CardsLists.ThemeCollection findParentCollectionOfDeck(
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList decksAndCollections) {
        if (decksAndCollections == null || deck == null || decksAndCollections.getCollections() == null) {
            return null;
        }
        for (Model.CardsLists.ThemeCollection themeCollection : decksAndCollections.getCollections()) {
            if (themeCollection == null || themeCollection.getLinkedDecks() == null) {
                continue;
            }
            for (List<Model.CardsLists.Deck> unit : themeCollection.getLinkedDecks()) {
                if (unit == null) {
                    continue;
                }
                for (Model.CardsLists.Deck candidate : unit) {
                    if (candidate == deck) {
                        return themeCollection;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Removes {@code deck} from wherever it currently lives (any collection unit
     * or the standalone list) without triggering a UI refresh.
     */
    private static void removeDeckFromCurrentLocation(
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList decksAndCollections) {
        if (decksAndCollections == null || deck == null) {
            return;
        }
        if (decksAndCollections.getDecks() != null) {
            decksAndCollections.getDecks().remove(deck);
        }
        if (decksAndCollections.getCollections() != null) {
            for (Model.CardsLists.ThemeCollection themeCollection : decksAndCollections.getCollections()) {
                if (themeCollection == null || themeCollection.getLinkedDecks() == null) {
                    continue;
                }
                for (List<Model.CardsLists.Deck> unit : themeCollection.getLinkedDecks()) {
                    if (unit != null) {
                        unit.remove(deck);
                    }
                }
                themeCollection.getLinkedDecks().removeIf(unit -> unit == null || unit.isEmpty());
            }
        }
    }

    /**
     * Moves {@code deck} to an existing unit (by index) in {@code targetCollection}.
     */
    private static MenuItem makeMoveDeckToUnitItem(
            String label,
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList decksAndCollections,
            Model.CardsLists.ThemeCollection targetCollection,
            int unitIndex) {
        return NavigationContextMenuBuilder.makeActionItem(label, () -> {
            removeDeckFromCurrentLocation(deck, decksAndCollections);
            List<List<Model.CardsLists.Deck>> units = targetCollection.getLinkedDecks();
            if (units == null) {
                units = new java.util.ArrayList<>();
                targetCollection.setLinkedDecks(units);
            }
            int safeIdx = Math.min(unitIndex, units.size() - 1);
            if (safeIdx < 0) {
                List<Model.CardsLists.Deck> newUnit = new java.util.ArrayList<>();
                newUnit.add(deck);
                units.add(newUnit);
            } else {
                units.get(safeIdx).add(deck);
            }
            Controller.UserInterfaceFunctions.setPendingDecksScrollTarget(deck);
            NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * Moves {@code deck} to a brand-new unit appended to {@code targetCollection}.
     */
    private static MenuItem makeMoveDeckToNewUnitItem(
            String label,
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList decksAndCollections,
            Model.CardsLists.ThemeCollection targetCollection) {
        return NavigationContextMenuBuilder.makeActionItem(label, () -> {
            removeDeckFromCurrentLocation(deck, decksAndCollections);
            targetCollection.addDeck(deck);
            Controller.UserInterfaceFunctions.setPendingDecksScrollTarget(deck);
            NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * Moves {@code deck} out of its collection into the standalone decks list.
     */
    private static MenuItem makeMoveDeckToStandaloneItem(
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList decksAndCollections,
            Model.CardsLists.ThemeCollection currentParent) {
        return NavigationContextMenuBuilder.makeActionItem("No Collection", () -> {
            removeDeckFromCurrentLocation(deck, decksAndCollections);
            if (decksAndCollections.getDecks() == null) {
                decksAndCollections.setDecks(new java.util.ArrayList<>());
            }
            decksAndCollections.getDecks().add(deck);
            Controller.UserInterfaceFunctions.setPendingDecksScrollTarget(deck);
            NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }
}