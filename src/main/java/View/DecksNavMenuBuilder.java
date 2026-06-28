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
            Model.CardsLists.DecksAndCollectionsList dac) {

        ContextMenu cm = NavigationContextMenuBuilder.styledContextMenu();

        Runnable removeAction = () -> {
            if (!NavigationContextMenuBuilder.isCollectionEmpty(collection)
                    && !NavigationContextMenuBuilder.confirmRemoval("Collection")) {
                return;
            }
            if (dac != null && dac.getCollections() != null) {
                dac.getCollections().remove(collection);
                Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            }
        };

        cm.getItems().addAll(
                makeDecksAddCollectionAfterItem(collection, dac),
                makeDecksAddLooseCollectionAfterItem(collection, dac),
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
        return cm;
    }

    /**
     * Context menu for a {@link Model.CardsLists.Deck} node. Provides Move,
     * Add Collection/Deck, Create Collection (standalone only), Rename, Paste,
     * and Remove actions.
     */
    public static ContextMenu forDecksDeck(
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList dac) {

        ContextMenu cm = NavigationContextMenuBuilder.styledContextMenu();

        Model.CardsLists.ThemeCollection parentCollection =
                findParentCollectionOfDeck(deck, dac);
        boolean isLinked = parentCollection != null;

        MenuItem addDeckItem = isLinked
                ? makeDecksAddLinkedDeckAfterItem(deck, parentCollection)
                : makeDecksAddStandaloneDeckAfterItem(deck, dac);

        Menu moveMenu = NavigationContextMenuBuilder.makeLazyMenu("Move to...");
        moveMenu.setOnShowing(evt -> {
            moveMenu.getItems().clear();

            if (dac == null) {
                moveMenu.getItems().add(NavigationContextMenuBuilder.disabledItem(
                        "No data available"));
                return;
            }

            if (dac.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                    if (tc == null) {
                        continue;
                    }
                    String collName = sanitize(tc.getName());
                    if (collName.isEmpty()) {
                        collName = "(Unnamed collection)";
                    }

                    List<List<Model.CardsLists.Deck>> units = tc.getLinkedDecks();
                    int groupCount = (units == null) ? 0 : units.size();

                    for (int unitIndex = 0; unitIndex < groupCount; unitIndex++) {
                        final int finalUnitIndex = unitIndex;
                        final Model.CardsLists.ThemeCollection targetColl = tc;
                        final String label = collName + " / Deck group " + (unitIndex + 1);

                        if (tc == parentCollection
                                && units.get(unitIndex) != null
                                && units.get(unitIndex).contains(deck)) {
                            continue;
                        }
                        moveMenu.getItems().add(makeMoveDeckToUnitItem(
                                label, deck, dac, targetColl, finalUnitIndex));
                    }

                    final Model.CardsLists.ThemeCollection targetCollNew = tc;
                    final String newGroupLabel = collName + " / New Deck group";
                    moveMenu.getItems().add(makeMoveDeckToNewUnitItem(
                            newGroupLabel, deck, dac, targetCollNew));
                }
            }

            if (isLinked) {
                if (!moveMenu.getItems().isEmpty()) {
                    moveMenu.getItems().add(new SeparatorMenuItem());
                }
                moveMenu.getItems().add(
                        makeMoveDeckToStandaloneItem(deck, dac, parentCollection));
            }

            if (moveMenu.getItems().isEmpty()) {
                moveMenu.getItems().add(NavigationContextMenuBuilder.disabledItem(
                        "No destinations available"));
            }
        });

        Runnable removeAction = () -> {
            if (!NavigationContextMenuBuilder.isDeckEmpty(deck)
                    && !NavigationContextMenuBuilder.confirmRemoval("Deck")) {
                return;
            }
            boolean removed = false;
            if (dac.getDecks() != null) {
                removed = dac.getDecks().remove(deck);
            }
            if (!removed && dac.getCollections() != null) {
                outer:
                for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                    if (tc == null || tc.getLinkedDecks() == null) {
                        continue;
                    }
                    for (List<Model.CardsLists.Deck> group : tc.getLinkedDecks()) {
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
        menuItems.add(makeDecksAddCollectionAfterDeckItem(dac));
        menuItems.add(makeDecksAddLooseCollectionAfterDeckItem(dac));
        menuItems.add(addDeckItem);
        if (!isLinked) {
            menuItems.add(makeDecksCreateCollectionFromDeckItem(deck, dac));
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
        cm.getItems().addAll(menuItems);
        return cm;
    }

    /**
     * Context menu shown when right-clicking the empty background of the Decks
     * and Collections navigation pane. Provides Add Collection, Add Loose Collection,
     * and Add Deck actions.
     */
    public static ContextMenu forDecksEmpty() {
        ContextMenu cm = NavigationContextMenuBuilder.styledContextMenu();
        cm.getItems().addAll(
                makeDecksAddCollectionAtEndItem(),
                makeDecksAddLooseCollectionAtEndItem(),
                makeDecksAddStandaloneDeckAtEndItem()
        );
        return cm;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

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
        menu.setOnShowing(evt -> {
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

                Label lbl = new Label(labelText);
                lbl.setStyle(inName
                        ? "-fx-text-fill: #cdfc04; -fx-font-size: 13; -fx-font-weight: bold;"
                        : "-fx-text-fill: white; -fx-font-size: 13;");

                HBox graphic = new HBox(lbl);
                graphic.setAlignment(Pos.CENTER_LEFT);
                graphic.setPadding(new Insets(2, 6, 2, 6));

                MenuItem mi = new MenuItem();
                mi.setGraphic(graphic);
                mi.setText("");

                final String finalArchName = archName;
                final List<Model.CardsLists.Card> finalArchCards =
                        (allCards != null && idx < allCards.size())
                                ? allCards.get(idx) : null;

                mi.setOnAction(e -> {
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
                        popup.showCenteredOn(mi.getParentPopup() != null
                                ? mi.getParentPopup().getOwnerNode() : null);
                    });
                });

                menu.getItems().add(mi);
                if (inName) {
                    separatorInserted = true;
                }
            }

            if (menu.getItems().isEmpty()) {
                menu.getItems().add(NavigationContextMenuBuilder.disabledItem(
                        "All archetypes already added"));
            }
        });
        return menu;
    }

    /**
     * Normalises a string for accent- and case-insensitive comparison:
     * lower-case, NFD decomposition, strip combining marks, collapse
     * non-alphanumeric runs to a single space.
     */
    private static String normalizeForSort(String s) {
        if (s == null) {
            return "";
        }
        String result = s.toLowerCase(java.util.Locale.ROOT).trim();
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
            Model.CardsLists.DecksAndCollectionsList dac) {
        return NavigationContextMenuBuilder.makeActionItem("Create Collection", () -> {
            String defaultName = (deck.getName() == null || deck.getName().trim().isEmpty())
                    ? "New Collection"
                    : deck.getName().trim();
            Model.CardsLists.ThemeCollection newCollection =
                    new Model.CardsLists.ThemeCollection();
            newCollection.setName(defaultName);
            if (dac.getDecks() != null) {
                dac.getDecks().remove(deck);
            }
            newCollection.addDeck(deck);
            if (dac.getCollections() == null) {
                dac.setCollections(new java.util.ArrayList<>());
            }
            dac.getCollections().add(newCollection);
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
            Model.CardsLists.DecksAndCollectionsList dac) {
        return NavigationContextMenuBuilder.makeActionItem("Add Loose Collection", () -> {
            Model.CardsLists.ThemeCollection newCollection =
                    new Model.CardsLists.ThemeCollection();
            newCollection.setName("New Loose Collection");
            newCollection.setConnectToWholeCollection(true);
            if (dac.getCollections() == null) {
                dac.setCollections(new java.util.ArrayList<>());
            }
            int idx = dac.getCollections().indexOf(reference);
            if (idx >= 0) {
                dac.getCollections().add(idx + 1, newCollection);
            } else {
                dac.getCollections().add(newCollection);
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
            Model.CardsLists.DecksAndCollectionsList dac) {
        return NavigationContextMenuBuilder.makeActionItem("Add Loose Collection", () -> {
            Model.CardsLists.ThemeCollection newCollection =
                    new Model.CardsLists.ThemeCollection();
            newCollection.setName("New Loose Collection");
            newCollection.setConnectToWholeCollection(true);
            if (dac.getCollections() == null) {
                dac.setCollections(new java.util.ArrayList<>());
            }
            dac.getCollections().add(newCollection);
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
            Model.CardsLists.DecksAndCollectionsList dac =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (dac == null) {
                return;
            }
            Model.CardsLists.ThemeCollection newCollection =
                    new Model.CardsLists.ThemeCollection();
            newCollection.setName("New Loose Collection");
            newCollection.setConnectToWholeCollection(true);
            if (dac.getCollections() == null) {
                dac.setCollections(new java.util.ArrayList<>());
            }
            dac.getCollections().add(newCollection);
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
            Model.CardsLists.DecksAndCollectionsList dac) {
        return NavigationContextMenuBuilder.makeActionItem("Add Collection", () -> {
            Model.CardsLists.ThemeCollection newCollection =
                    new Model.CardsLists.ThemeCollection();
            newCollection.setName("New Collection");
            if (dac.getCollections() == null) {
                dac.setCollections(new java.util.ArrayList<>());
            }
            int idx = dac.getCollections().indexOf(reference);
            if (idx >= 0) {
                dac.getCollections().add(idx + 1, newCollection);
            } else {
                dac.getCollections().add(newCollection);
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
            Model.CardsLists.DecksAndCollectionsList dac) {
        return NavigationContextMenuBuilder.makeActionItem("Add Collection", () -> {
            Model.CardsLists.ThemeCollection newCollection =
                    new Model.CardsLists.ThemeCollection();
            newCollection.setName("New Collection");
            if (dac.getCollections() == null) {
                dac.setCollections(new java.util.ArrayList<>());
            }
            dac.getCollections().add(newCollection);
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
            Model.CardsLists.DecksAndCollectionsList dac =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (dac == null) {
                return;
            }
            Model.CardsLists.ThemeCollection newCollection =
                    new Model.CardsLists.ThemeCollection();
            newCollection.setName("New Collection");
            if (dac.getCollections() == null) {
                dac.setCollections(new java.util.ArrayList<>());
            }
            dac.getCollections().add(newCollection);
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
            Model.CardsLists.DecksAndCollectionsList dac) {
        return NavigationContextMenuBuilder.makeActionItem("Add Deck", () -> {
            Model.CardsLists.Deck newDeck = new Model.CardsLists.Deck();
            newDeck.setName("New Deck");
            if (dac.getDecks() == null) {
                dac.setDecks(new java.util.ArrayList<>());
            }
            int idx = dac.getDecks().indexOf(reference);
            if (idx >= 0) {
                dac.getDecks().add(idx + 1, newDeck);
            } else {
                dac.getDecks().add(newDeck);
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
            Model.CardsLists.DecksAndCollectionsList dac =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (dac == null) {
                return;
            }
            Model.CardsLists.Deck newDeck = new Model.CardsLists.Deck();
            newDeck.setName("New Deck");
            if (dac.getDecks() == null) {
                dac.setDecks(new java.util.ArrayList<>());
            }
            dac.getDecks().add(newDeck);
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
        MenuItem mi = new MenuItem();
        Label label = new Label("Rename");
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(graphic);
        mi.setText("");

        mi.setOnAction(e -> {
            javafx.scene.Node owner = null;
            try {
                owner = mi.getParentPopup().getOwnerNode();
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
        return mi;
    }

    /**
     * Returns the ThemeCollection that contains {@code deck} as a linked deck,
     * or {@code null} if the deck is standalone.
     */
    private static Model.CardsLists.ThemeCollection findParentCollectionOfDeck(
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList dac) {
        if (dac == null || deck == null || dac.getCollections() == null) {
            return null;
        }
        for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
            if (tc == null || tc.getLinkedDecks() == null) {
                continue;
            }
            for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                if (unit == null) {
                    continue;
                }
                for (Model.CardsLists.Deck candidate : unit) {
                    if (candidate == deck) {
                        return tc;
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
            Model.CardsLists.DecksAndCollectionsList dac) {
        if (dac == null || deck == null) {
            return;
        }
        if (dac.getDecks() != null) {
            dac.getDecks().remove(deck);
        }
        if (dac.getCollections() != null) {
            for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                if (tc == null || tc.getLinkedDecks() == null) {
                    continue;
                }
                for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                    if (unit != null) {
                        unit.remove(deck);
                    }
                }
                tc.getLinkedDecks().removeIf(unit -> unit == null || unit.isEmpty());
            }
        }
    }

    /**
     * Moves {@code deck} to an existing unit (by index) in {@code targetCollection}.
     */
    private static MenuItem makeMoveDeckToUnitItem(
            String label,
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList dac,
            Model.CardsLists.ThemeCollection targetCollection,
            int unitIndex) {
        return NavigationContextMenuBuilder.makeActionItem(label, () -> {
            removeDeckFromCurrentLocation(deck, dac);
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
            Model.CardsLists.DecksAndCollectionsList dac,
            Model.CardsLists.ThemeCollection targetCollection) {
        return NavigationContextMenuBuilder.makeActionItem(label, () -> {
            removeDeckFromCurrentLocation(deck, dac);
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
            Model.CardsLists.DecksAndCollectionsList dac,
            Model.CardsLists.ThemeCollection currentParent) {
        return NavigationContextMenuBuilder.makeActionItem("No Collection", () -> {
            removeDeckFromCurrentLocation(deck, dac);
            if (dac.getDecks() == null) {
                dac.setDecks(new java.util.ArrayList<>());
            }
            dac.getDecks().add(deck);
            Controller.UserInterfaceFunctions.setPendingDecksScrollTarget(deck);
            NavigationContextMenuBuilder.refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }
}