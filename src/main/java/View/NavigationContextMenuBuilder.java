package View;

import Model.CardsLists.Box;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class NavigationContextMenuBuilder {

    private static final Logger logger = LoggerFactory.getLogger(NavigationContextMenuBuilder.class);

    private NavigationContextMenuBuilder() { /* static factory */ }

    // =========================================================================
    // My Collection tab
    // =========================================================================

    public static ContextMenu forMyCollectionBox(
            Model.CardsLists.Box box,
            Model.CardsLists.OwnedCardsCollection owned) {

        ContextMenu cm = styledContextMenu();

        Menu moveMenu = makeLazyMenu("Move to...");
        moveMenu.setOnShowing(evt -> {
            moveMenu.getItems().clear();

            if (owned == null || owned.getOwnedCollection() == null) {
                moveMenu.getItems().add(disabledItem("No destinations available"));
                return;
            }

            // Determine whether this box is a sub-box (and if so, who is its parent)
            Box parentOfBox = findParentOfBox(box, owned);
            boolean isSubBox = parentOfBox != null;

            // Offer every top-level box (and their sub-boxes) except the box itself
            // and boxes that are descendants of it (to avoid cycles).
            java.util.Set<Box> descendants = collectDescendants(box);

            for (Box topBox : owned.getOwnedCollection()) {
                if (topBox == null || topBox == box) continue;
                if (descendants.contains(topBox)) continue;

                String name = sanitize(topBox.getName());
                if (name.isEmpty()) name = "(Unnamed box)";
                final Box target = topBox;
                MenuItem mi = makeActionItem(name, () -> {
                    doMoveBoxToSubBox(box, target, owned);
                    Controller.UserInterfaceFunctions.markMyCollectionDirty();
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    refreshOwnedCollectionView();
                });
                moveMenu.getItems().add(mi);

                // Also offer sub-boxes of this top-level box as targets
                if (topBox.getSubBoxes() != null) {
                    for (Box sub : topBox.getSubBoxes()) {
                        if (sub == null || sub == box) continue;
                        if (descendants.contains(sub)) continue;
                        String subName = sanitize(sub.getName());
                        if (subName.isEmpty()) subName = "(Unnamed sub-box)";
                        final Box subTarget = sub;
                        MenuItem subMi = makeActionItem(name + " / " + subName, () -> {
                            doMoveBoxToSubBox(box, subTarget, owned);
                            Controller.UserInterfaceFunctions.markMyCollectionDirty();
                            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                            refreshOwnedCollectionView();
                        });
                        moveMenu.getItems().add(subMi);
                    }
                }
            }

            // If it is already a sub-box, offer "No Box" to promote it to top level
            if (isSubBox) {
                if (!moveMenu.getItems().isEmpty()) moveMenu.getItems().add(new SeparatorMenuItem());
                MenuItem noBox = makeActionItem("No Box (top level)", () -> {
                    doMoveBoxToTopLevel(box, owned);
                    Controller.UserInterfaceFunctions.markMyCollectionDirty();
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    refreshOwnedCollectionView();
                });
                moveMenu.getItems().add(noBox);
            }

            if (moveMenu.getItems().isEmpty()) {
                moveMenu.getItems().add(disabledItem("No other boxes"));
            }
        });

        Runnable removeAction = () -> {
            if (!isBoxEmpty(box) && !confirmRemoval("Box")) return;
            if (owned != null && owned.getOwnedCollection() != null) {
                if (owned.getOwnedCollection().remove(box)) {
                    Controller.UserInterfaceFunctions.markMyCollectionDirty();
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    refreshOwnedCollectionView();
                    return;
                }
                for (Model.CardsLists.Box parent : owned.getOwnedCollection()) {
                    if (parent == null || parent.getSubBoxes() == null) continue;
                    if (parent.getSubBoxes().remove(box)) {
                        Controller.UserInterfaceFunctions.markMyCollectionDirty();
                        Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                        refreshOwnedCollectionView();
                        return;
                    }
                }
            }
        };

        cm.getItems().addAll(
                moveMenu,
                makeAddBoxInItem(box, owned),
                makeAddBoxAfterItem(box, owned),
                makeAddCategoryItem(box, null, owned),
                makeRenameItem(box, null, owned),
                makePasteItem(() -> {
                    // Paste clipboard cards to the default group of this box
                    Model.CardsLists.CardsGroup defaultGroup =
                            Controller.MenuActionHandler.getOrCreateDefaultGroup(box);
                    if (defaultGroup == null) return;
                    javafx.collections.ObservableList<Model.CardsLists.CardElement> observableList =
                            CardTreeCell.observableListFor(defaultGroup);
                    for (Model.CardsLists.Card card : Controller.CardClipboard.getContents()) {
                        if (card != null) observableList.add(new Model.CardsLists.CardElement(card));
                    }
                    CardTreeCell.triggerHeightAdjustment(defaultGroup);
                    Controller.UserInterfaceFunctions.markMyCollectionDirty();
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    refreshOwnedCollectionView();
                }),
                new SeparatorMenuItem(),
                makeRemoveItem(removeAction)
        );
        return cm;
    }

    public static ContextMenu forMyCollectionCategory(
            Model.CardsLists.CardsGroup category,
            Model.CardsLists.Box parentBox,
            Model.CardsLists.OwnedCardsCollection owned) {

        ContextMenu cm = styledContextMenu();

        Menu moveMenu = makeLazyMenu("Move to...");
        moveMenu.setOnShowing(evt -> {
            moveMenu.getItems().clear();

            if (owned == null || owned.getOwnedCollection() == null) {
                moveMenu.getItems().add(disabledItem("No destinations available"));
                return;
            }

            for (Model.CardsLists.Box box : owned.getOwnedCollection()) {
                if (box == null) continue;
                String boxName = sanitize(box.getName());
                if (boxName.isEmpty()) boxName = "(Unnamed box)";

                // Box-level destination (append category to box.content)
                final Model.CardsLists.Box destBox = box;
                final String destBoxName = boxName;
                MenuItem miBox = makeActionItem(destBoxName, () -> {
                    doMoveCategory(category, parentBox, destBox, null, owned);
                    Controller.UserInterfaceFunctions.markMyCollectionDirty();
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    refreshOwnedCollectionView();
                });
                moveMenu.getItems().add(miBox);

                // Group-level destinations: insert category after the named group
                if (box.getContent() != null) {
                    for (Model.CardsLists.CardsGroup g : box.getContent()) {
                        if (g == null || g == category) continue;
                        String groupName = sanitize(g.getName());
                        if (groupName.isEmpty()) continue;
                        final Model.CardsLists.CardsGroup afterGroup = g;
                        MenuItem miGroup = makeActionItem(destBoxName + " / " + groupName, () -> {
                            doMoveCategory(category, parentBox, destBox, afterGroup, owned);
                            Controller.UserInterfaceFunctions.markMyCollectionDirty();
                            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                            refreshOwnedCollectionView();
                        });
                        moveMenu.getItems().add(miGroup);
                    }
                }

                // Sub-boxes
                if (box.getSubBoxes() != null) {
                    for (Model.CardsLists.Box sb : box.getSubBoxes()) {
                        if (sb == null) continue;
                        String subName = sanitize(sb.getName());
                        if (subName.isEmpty()) subName = "(Unnamed sub-box)";
                        final Model.CardsLists.Box destSub = sb;
                        final String destSubName = subName;
                        MenuItem miSub = makeActionItem(destSubName, () -> {
                            doMoveCategory(category, parentBox, destSub, null, owned);
                            Controller.UserInterfaceFunctions.markMyCollectionDirty();
                            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                            refreshOwnedCollectionView();
                        });
                        moveMenu.getItems().add(miSub);

                        if (sb.getContent() != null) {
                            for (Model.CardsLists.CardsGroup g : sb.getContent()) {
                                if (g == null || g == category) continue;
                                String groupName = sanitize(g.getName());
                                if (groupName.isEmpty()) continue;
                                final Model.CardsLists.CardsGroup afterGroup = g;
                                MenuItem miSubGroup = makeActionItem(destSubName + " / " + groupName, () -> {
                                    doMoveCategory(category, parentBox, destSub, afterGroup, owned);
                                    Controller.UserInterfaceFunctions.markMyCollectionDirty();
                                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                                    refreshOwnedCollectionView();
                                });
                                moveMenu.getItems().add(miSubGroup);
                            }
                        }
                    }
                }
            }

            if (moveMenu.getItems().isEmpty()) {
                moveMenu.getItems().add(disabledItem("No destinations available"));
            }
        });

        Runnable removeAction = () -> {
            if (!isCategoryEmpty(category) && !confirmRemoval("Category")) return;
            if (parentBox != null && parentBox.getContent() != null) {
                parentBox.getContent().remove(category);
                Controller.UserInterfaceFunctions.markMyCollectionDirty();
                Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                refreshOwnedCollectionView();
            }
        };

        cm.getItems().addAll(
                moveMenu,
                makeAddCategoryItem(parentBox, category, owned),
                makeRenameItem(null, category, owned),
                makePasteItem(() -> {
                    // Paste clipboard cards at the end of this category
                    javafx.collections.ObservableList<Model.CardsLists.CardElement> observableList =
                            CardTreeCell.observableListFor(category);
                    for (Model.CardsLists.Card card : Controller.CardClipboard.getContents()) {
                        if (card != null) observableList.add(new Model.CardsLists.CardElement(card));
                    }
                    CardTreeCell.triggerHeightAdjustment(category);
                    Controller.UserInterfaceFunctions.markMyCollectionDirty();
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    refreshOwnedCollectionView();
                }),
                new SeparatorMenuItem(),
                makeRemoveItem(removeAction)
        );
        return cm;
    }

    public static ContextMenu forMyCollectionEmpty() {
        ContextMenu cm = styledContextMenu();
        cm.getItems().add(makeAddBoxAtTopLevelItem());
        return cm;
    }

    /**
     * "Add Box in" — creates a sub-box inside the clicked box.
     */
    private static MenuItem makeAddBoxInItem(Model.CardsLists.Box parentBox,
                                             Model.CardsLists.OwnedCardsCollection owned) {
        return makeActionItem("Add Box in", () -> {
            Model.CardsLists.Box newBox = new Model.CardsLists.Box("New Box");
            if (parentBox.getSubBoxes() == null) parentBox.setSubBoxes(new java.util.ArrayList<>());
            parentBox.getSubBoxes().add(newBox);
            Controller.UserInterfaceFunctions.setPendingRenameTarget(newBox);
            Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Box after" — creates a sibling box immediately after the clicked box.
     */
    private static MenuItem makeAddBoxAfterItem(Model.CardsLists.Box referenceBox,
                                                Model.CardsLists.OwnedCardsCollection owned) {
        return makeActionItem("Add Box after", () -> {
            Model.CardsLists.Box newBox = new Model.CardsLists.Box("New Box");
            // Find where referenceBox lives and insert after it
            java.util.List<Model.CardsLists.Box> topLevel = owned.getOwnedCollection();
            int idx = topLevel.indexOf(referenceBox);
            if (idx >= 0) {
                topLevel.add(idx + 1, newBox);
            } else {
                // Try sub-box lists
                boolean inserted = false;
                outer:
                for (Model.CardsLists.Box top : topLevel) {
                    if (top.getSubBoxes() == null) continue;
                    idx = top.getSubBoxes().indexOf(referenceBox);
                    if (idx >= 0) {
                        top.getSubBoxes().add(idx + 1, newBox);
                        inserted = true;
                        break;
                    }
                }
                if (!inserted) topLevel.add(newBox); // fallback
            }
            Controller.UserInterfaceFunctions.setPendingRenameTarget(newBox);
            Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Category" — creates a new category.
     * If {@code afterCategory} is non-null it is inserted after that category;
     * otherwise it is appended to {@code targetBox}.
     */
    private static MenuItem makeAddCategoryItem(
            Model.CardsLists.Box targetBox,
            Model.CardsLists.CardsGroup afterCategory,
            Model.CardsLists.OwnedCardsCollection owned) {

        return makeActionItem("Add Category", () -> {
            Model.CardsLists.CardsGroup newCat = new Model.CardsLists.CardsGroup("New Category");
            if (targetBox.getContent() == null) targetBox.setContent(new java.util.ArrayList<>());
            if (afterCategory != null) {
                int idx = targetBox.getContent().indexOf(afterCategory);
                if (idx >= 0) {
                    targetBox.getContent().add(idx + 1, newCat);
                } else {
                    targetBox.getContent().add(newCat);
                }
            } else {
                targetBox.getContent().add(newCat);
            }
            Controller.UserInterfaceFunctions.setPendingRenameTarget(newCat);
            Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Box" from the empty nav-menu background — appends at the top level.
     */
    private static MenuItem makeAddBoxAtTopLevelItem() {
        return makeActionItem("Add Box", () -> {
            Model.CardsLists.OwnedCardsCollection owned = null;
            try {
                owned = Model.CardsLists.OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
            if (owned == null) return;
            Model.CardsLists.Box newBox = new Model.CardsLists.Box("New Box");
            owned.getOwnedCollection().add(newBox);
            Controller.UserInterfaceFunctions.setPendingRenameTarget(newBox);
            Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    // =========================================================================
    // Decks and Collections tab
    // =========================================================================

    /**
     * Collections cannot be moved, so there is no "Move to..." entry.
     * Signature updated: now takes the actual ThemeCollection and DAC
     * so the Remove button can work.
     */
    public static ContextMenu forDecksCollection(
            Model.CardsLists.ThemeCollection collection,
            Model.CardsLists.DecksAndCollectionsList dac) {

        ContextMenu cm = styledContextMenu();

        Runnable removeAction = () -> {
            if (!isCollectionEmpty(collection) && !confirmRemoval("Collection")) return;
            if (dac != null && dac.getCollections() != null) {
                dac.getCollections().remove(collection);
                Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                refreshDecksAndCollectionsView();
            }
        };

        cm.getItems().addAll(
                makeDecksAddCollectionAfterItem(collection, dac),
                makeDecksAddLinkedDeckToCollectionItem(collection),
                makeDecksAddArchetypeMenu(collection),
                makeDecksRenameItem(collection),
                makePasteItem(() -> {
                    // Paste clipboard cards at the end of the collection's cards list
                    if (collection.getCardsList() == null)
                        collection.setCardsList(new java.util.ArrayList<>());
                    for (Model.CardsLists.Card card : Controller.CardClipboard.getContents()) {
                        if (card != null)
                            collection.getCardsList().add(new Model.CardsLists.CardElement(card));
                    }
                    Controller.UserInterfaceFunctions.markDirty(collection);
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    refreshDecksAndCollectionsView();
                }),
                new SeparatorMenuItem(),
                makeRemoveItem(removeAction)
        );
        return cm;
    }

    /**
     * Builds the lazy "Add archetype" submenu for a ThemeCollection.
     * <p>
     * Ordering rules:
     * 1. Archetypes whose name is contained in the collection name (accent-
     * and case-insensitive) always appear first, highlighted in accent colour.
     * 2. Among the rest, sort by the number of collection cards that belong to
     * that archetype (highest first).
     * 3. Within each tier, preserve the original SubListCreator order.
     * <p>
     * Archetypes already present in collection.getArchetypes() are excluded.
     * <p>
     * Action: stub — will be wired in a later task.
     */
    private static Menu makeDecksAddArchetypeMenu(
            Model.CardsLists.ThemeCollection collection) {

        Menu menu = makeLazyMenu("Add archetype");
        menu.setOnShowing(evt -> {
            menu.getItems().clear();

            List<String> allNames =
                    Model.CardsLists.SubListCreator.archetypesList;
            List<List<Model.CardsLists.Card>> allCards =
                    Model.CardsLists.SubListCreator.archetypesCardsLists;

            if (allNames == null || allNames.isEmpty()) {
                menu.getItems().add(disabledItem("No archetypes available"));
                return;
            }

            // ── Archetypes already attached to this collection ────────────────
            java.util.Set<String> alreadyAdded = new java.util.HashSet<>();
            try {
                List<String> existing = collection.getArchetypes();
                if (existing != null) {
                    for (String s : existing)
                        if (s != null) alreadyAdded.add(normalizeForSort(s));
                }
            } catch (Throwable ignored) {
            }

            // ── Gather all card identifiers that belong to the collection ─────
            java.util.Set<String> collPassCodes = new java.util.HashSet<>();
            java.util.Set<String> collKonamiIds = new java.util.HashSet<>();

            if (collection.getCardsList() != null) {
                for (Model.CardsLists.CardElement ce : collection.getCardsList()) {
                    if (ce == null || ce.getCard() == null) continue;
                    if (ce.getCard().getPassCode() != null)
                        collPassCodes.add(ce.getCard().getPassCode());
                    if (ce.getCard().getKonamiId() != null)
                        collKonamiIds.add(ce.getCard().getKonamiId());
                }
            }
            if (collection.getLinkedDecks() != null) {
                for (List<Model.CardsLists.Deck> unit : collection.getLinkedDecks()) {
                    if (unit == null) continue;
                    for (Model.CardsLists.Deck d : unit) {
                        if (d == null) continue;
                        for (Model.CardsLists.CardElement ce : d.toList()) {
                            if (ce == null || ce.getCard() == null) continue;
                            if (ce.getCard().getPassCode() != null)
                                collPassCodes.add(ce.getCard().getPassCode());
                            if (ce.getCard().getKonamiId() != null)
                                collKonamiIds.add(ce.getCard().getKonamiId());
                        }
                    }
                }
            }

            // ── Normalised collection name for "name contains archetype" check ─
            String collNorm = normalizeForSort(
                    collection.getName() == null ? "" : collection.getName());

            // ── For each archetype: compute inName flag + card count ──────────
            // Entry: [originalIndex, cardCount, inNameFlag(1/0)]
            List<int[]> entries = new java.util.ArrayList<>();

            for (int i = 0; i < allNames.size(); i++) {
                String archName = allNames.get(i);
                if (archName == null) continue;

                // Skip archetypes already attached to this collection
                if (alreadyAdded.contains(normalizeForSort(archName))) continue;

                boolean inName = collNorm.contains(normalizeForSort(archName));

                // Count collection cards that appear in this archetype's card list
                int count = 0;
                if (allCards != null && i < allCards.size()) {
                    List<Model.CardsLists.Card> archCardList = allCards.get(i);
                    if (archCardList != null) {
                        for (Model.CardsLists.Card c : archCardList) {
                            if (c == null) continue;
                            boolean matchPass = c.getPassCode() != null
                                    && collPassCodes.contains(c.getPassCode());
                            boolean matchKonami = c.getKonamiId() != null
                                    && collKonamiIds.contains(c.getKonamiId());
                            if (matchPass || matchKonami) count++;
                        }
                    }
                }

                entries.add(new int[]{i, count, inName ? 1 : 0});
            }

            // ── Sort: inName DESC → count DESC → original index ASC ──────────
            entries.sort((a, b) -> {
                if (b[2] != a[2]) return b[2] - a[2];
                if (b[1] != a[1]) return b[1] - a[1];
                return a[0] - b[0];
            });

            // ── Build menu items ──────────────────────────────────────────────
            boolean separatorInserted = false;
            boolean inNameSectionEnded = false;

            for (int[] entry : entries) {
                int idx = entry[0];
                int count = entry[1];
                boolean inName = entry[2] == 1;
                String archName = allNames.get(idx);

                // Separator between the "in-name" tier and the rest
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

                HBox g = new HBox(lbl);
                g.setAlignment(Pos.CENTER_LEFT);
                g.setPadding(new Insets(2, 6, 2, 6));

                MenuItem mi = new MenuItem();
                mi.setGraphic(g);
                mi.setText("");

                final String finalArchName = archName;
                mi.setOnAction(e -> {
                    // Ensure the collection has an archetypes list
                    if (collection.getArchetypes() == null)
                        collection.setArchetypes(new java.util.ArrayList<>());

                    // Avoid duplicates (case-insensitive)
                    boolean duplicate = false;
                    for (String existing : collection.getArchetypes()) {
                        if (finalArchName.equalsIgnoreCase(existing)) {
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

                    // Rebuild the view so the new archetype node appears in the tree
                    refreshDecksAndCollectionsView();
                });

                menu.getItems().add(mi);
                if (inName) separatorInserted = true;
            }

            if (menu.getItems().isEmpty())
                menu.getItems().add(disabledItem("All archetypes already added"));
        });

        return menu;
    }

    /**
     * Normalises a string for accent- and case-insensitive comparison:
     * lower-case, NFD decomposition, strip combining marks, collapse
     * non-alphanumeric runs to a single space.
     */
    private static String normalizeForSort(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(java.util.Locale.ROOT).trim();
        t = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        t = t.replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return t;
    }

    public static ContextMenu forDecksDeck(
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList dac) {

        ContextMenu cm = styledContextMenu();

        Model.CardsLists.ThemeCollection parentColl = findParentCollectionOfDeck(deck, dac);
        boolean isLinked = parentColl != null;

        MenuItem addDeckItem = isLinked
                ? makeDecksAddLinkedDeckAfterItem(deck, parentColl)
                : makeDecksAddStandaloneDeckAfterItem(deck, dac);

        Menu moveMenu = makeLazyMenu("Move to...");
        moveMenu.setOnShowing(evt -> {
            moveMenu.getItems().clear();

            if (dac == null) {
                moveMenu.getItems().add(disabledItem("No data available"));
                return;
            }

            if (dac.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                    if (tc == null) continue;
                    String collName = sanitize(tc.getName());
                    if (collName.isEmpty()) collName = "(Unnamed collection)";

                    List<List<Model.CardsLists.Deck>> units = tc.getLinkedDecks();
                    int groupCount = (units == null) ? 0 : units.size();

                    for (int i = 0; i < groupCount; i++) {
                        final int unitIndex = i;
                        final Model.CardsLists.ThemeCollection targetColl = tc;
                        final String label = collName + " / Deck group " + (i + 1);

                        // Skip the unit this deck already belongs to
                        if (tc == parentColl && units.get(i) != null
                                && units.get(i).contains(deck)) continue;

                        moveMenu.getItems().add(
                                makeMoveDeckToUnitItem(label, deck, dac, targetColl, unitIndex));
                    }

                    // Always offer "New Deck group" for every collection
                    final Model.CardsLists.ThemeCollection targetCollNew = tc;
                    final String newGroupLabel = collName + " / New Deck group";
                    moveMenu.getItems().add(
                            makeMoveDeckToNewUnitItem(newGroupLabel, deck, dac, targetCollNew));
                }
            }

            // "No Collection" — only when deck is currently linked to a collection
            if (isLinked) {
                if (!moveMenu.getItems().isEmpty())
                    moveMenu.getItems().add(new SeparatorMenuItem());
                moveMenu.getItems().add(
                        makeMoveDeckToStandaloneItem(deck, dac, parentColl));
            }

            if (moveMenu.getItems().isEmpty())
                moveMenu.getItems().add(disabledItem("No destinations available"));
        });

        Runnable removeAction = () -> {
            if (!isDeckEmpty(deck) && !confirmRemoval("Deck")) return;
            boolean removed = false;
            if (dac.getDecks() != null) removed = dac.getDecks().remove(deck);
            if (!removed && dac.getCollections() != null) {
                outer:
                for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                    if (tc == null || tc.getLinkedDecks() == null) continue;
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
                refreshDecksAndCollectionsView();
            }
        };

        // Build item list — "Create Collection" only for standalone decks
        List<javafx.scene.control.MenuItem> menuItems = new java.util.ArrayList<>();
        menuItems.add(moveMenu);
        menuItems.add(makeDecksAddCollectionAfterDeckItem(dac));
        menuItems.add(addDeckItem);
        if (!isLinked) {
            menuItems.add(makeDecksCreateCollectionFromDeckItem(deck, dac));
        }
        menuItems.add(makeDecksRenameItem(deck));
        menuItems.add(makePasteItem(() -> {
            // Paste clipboard cards at the end of the deck's Main Deck
            for (Model.CardsLists.Card card : Controller.CardClipboard.getContents()) {
                if (card != null) deck.getMainDeck().add(new Model.CardsLists.CardElement(card));
            }
            Controller.UserInterfaceFunctions.markDirty(deck);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            refreshDecksAndCollectionsView();
        }));
        menuItems.add(new SeparatorMenuItem());
        menuItems.add(makeRemoveItem(removeAction));
        cm.getItems().addAll(menuItems);
        return cm;
    }

    /**
     * "Create Collection" — only for standalone decks.
     * <p>
     * Creates a new ThemeCollection pre-named after the deck, moves the deck
     * into it (new unit), then triggers the "pending create-collection rename"
     * flow so the user sees the rename field with the deck already inside.
     * Cancelling reverts everything: the collection is removed and the deck is
     * returned to the standalone list.
     */
    private static MenuItem makeDecksCreateCollectionFromDeckItem(
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList dac) {

        return makeActionItem("Create Collection", () -> {
            // Use the deck's current name as the default collection name
            String defaultName = (deck.getName() == null || deck.getName().trim().isEmpty())
                    ? "New Collection"
                    : deck.getName().trim();

            // Create the collection
            Model.CardsLists.ThemeCollection newColl = new Model.CardsLists.ThemeCollection();
            newColl.setName(defaultName);

            // Remove deck from the standalone list
            if (dac.getDecks() != null) dac.getDecks().remove(deck);

            // Place the deck in the new collection (AddDeck creates a new unit)
            newColl.AddDeck(deck);

            // Register the collection in the DAC
            if (dac.getCollections() == null) dac.setCollections(new java.util.ArrayList<>());
            dac.getCollections().add(newColl);

            // Store {collection, deck} so the refresher can launch the special rename
            Controller.UserInterfaceFunctions.setPendingDecksCreateCollectionData(
                    new Object[]{newColl, deck});

            // Both the new collection and the moved deck are dirty
            Controller.UserInterfaceFunctions.markDirty(newColl);
            Controller.UserInterfaceFunctions.markDirty(deck);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();

            refreshDecksAndCollectionsView();
        });
    }

    public static ContextMenu forDecksEmpty() {
        ContextMenu cm = styledContextMenu();
        cm.getItems().addAll(
                makeDecksAddCollectionAtEndItem(),
                makeDecksAddStandaloneDeckAtEndItem()
        );
        return cm;
    }

    // ── Decks-tab helpers ─────────────────────────────────────────────────────

    /**
     * Finds the ThemeCollection that contains the given deck as a linked deck,
     * or null if the deck is standalone.
     */
    private static Model.CardsLists.ThemeCollection findParentCollectionOfDeck(
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList dac) {
        if (dac == null || deck == null || dac.getCollections() == null) return null;
        for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
            if (tc == null || tc.getLinkedDecks() == null) continue;
            for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                if (unit == null) continue;
                for (Model.CardsLists.Deck d : unit) {
                    if (d == deck) return tc;
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

        if (dac == null || deck == null) return;

        // Remove from standalone list
        if (dac.getDecks() != null) dac.getDecks().remove(deck);

        // Remove from every collection unit
        if (dac.getCollections() != null) {
            for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                if (tc == null || tc.getLinkedDecks() == null) continue;
                for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                    if (unit != null) unit.remove(deck);
                }
                // Prune empty units so the collection stays tidy
                tc.getLinkedDecks().removeIf(u -> u == null || u.isEmpty());
            }
        }
    }

    /**
     * MenuItem: move {@code deck} to an existing unit (by index) in {@code targetColl}.
     */
    private static MenuItem makeMoveDeckToUnitItem(
            String label,
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList dac,
            Model.CardsLists.ThemeCollection targetColl,
            int unitIndex) {

        return makeActionItem(label, () -> {
            removeDeckFromCurrentLocation(deck, dac);

            List<List<Model.CardsLists.Deck>> units = targetColl.getLinkedDecks();
            if (units == null) {
                units = new java.util.ArrayList<>();
                targetColl.setLinkedDecks(units);
            }
            // The unit index may have shifted after removal — clamp it
            int safeIdx = Math.min(unitIndex, units.size() - 1);
            if (safeIdx < 0) {
                // No units left — create one
                List<Model.CardsLists.Deck> newUnit = new java.util.ArrayList<>();
                newUnit.add(deck);
                units.add(newUnit);
            } else {
                units.get(safeIdx).add(deck);
            }

            Controller.UserInterfaceFunctions.setPendingDecksScrollTarget(deck);
            refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * MenuItem: move {@code deck} to a brand-new unit appended to {@code targetColl}.
     */
    private static MenuItem makeMoveDeckToNewUnitItem(
            String label,
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList dac,
            Model.CardsLists.ThemeCollection targetColl) {

        return makeActionItem(label, () -> {
            removeDeckFromCurrentLocation(deck, dac);
            targetColl.AddDeck(deck);   // AddDeck always creates a new unit
            Controller.UserInterfaceFunctions.setPendingDecksScrollTarget(deck);
            refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * MenuItem: move {@code deck} out of its collection into the standalone decks list.
     */
    private static MenuItem makeMoveDeckToStandaloneItem(
            Model.CardsLists.Deck deck,
            Model.CardsLists.DecksAndCollectionsList dac,
            Model.CardsLists.ThemeCollection currentParent) {

        return makeActionItem("No Collection", () -> {
            removeDeckFromCurrentLocation(deck, dac);
            if (dac.getDecks() == null) dac.setDecks(new java.util.ArrayList<>());
            dac.getDecks().add(deck);
            Controller.UserInterfaceFunctions.setPendingDecksScrollTarget(deck);
            refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Collection" from a Collection context: inserts after {@code reference}.
     */
    private static MenuItem makeDecksAddCollectionAfterItem(
            Model.CardsLists.ThemeCollection reference,
            Model.CardsLists.DecksAndCollectionsList dac) {
        return makeActionItem("Add Collection", () -> {
            Model.CardsLists.ThemeCollection newColl = new Model.CardsLists.ThemeCollection();
            newColl.setName("New Collection");
            if (dac.getCollections() == null) dac.setCollections(new java.util.ArrayList<>());
            int idx = dac.getCollections().indexOf(reference);
            if (idx >= 0) dac.getCollections().add(idx + 1, newColl);
            else dac.getCollections().add(newColl);
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newColl);
            refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markDirty(newColl);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Collection" from a Deck context: appends at end of the collections list.
     */
    private static MenuItem makeDecksAddCollectionAfterDeckItem(
            Model.CardsLists.DecksAndCollectionsList dac) {
        return makeActionItem("Add Collection", () -> {
            Model.CardsLists.ThemeCollection newColl = new Model.CardsLists.ThemeCollection();
            newColl.setName("New Collection");
            if (dac.getCollections() == null) dac.setCollections(new java.util.ArrayList<>());
            dac.getCollections().add(newColl);
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newColl);
            refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markDirty(newColl);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Collection" from the empty-background context: appends at end.
     */
    private static MenuItem makeDecksAddCollectionAtEndItem() {
        return makeActionItem("Add Collection", () -> {
            Model.CardsLists.DecksAndCollectionsList dac =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (dac == null) return;
            Model.CardsLists.ThemeCollection newColl = new Model.CardsLists.ThemeCollection();
            newColl.setName("New Collection");
            if (dac.getCollections() == null) dac.setCollections(new java.util.ArrayList<>());
            dac.getCollections().add(newColl);
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newColl);
            refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markDirty(newColl);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Deck" from a Collection context: adds a new linked deck unit to the collection.
     */
    private static MenuItem makeDecksAddLinkedDeckToCollectionItem(
            Model.CardsLists.ThemeCollection collection) {
        return makeActionItem("Add Deck", () -> {
            Model.CardsLists.Deck newDeck = new Model.CardsLists.Deck();
            newDeck.setName("New Deck");
            collection.AddDeck(newDeck);   // adds as a new unit at the end
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newDeck);
            refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markDirty(collection);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Deck" from a linked Deck context: inserts a new linked-deck unit
     * immediately after the unit that contains {@code reference}.
     */
    private static MenuItem makeDecksAddLinkedDeckAfterItem(
            Model.CardsLists.Deck reference,
            Model.CardsLists.ThemeCollection parentCollection) {
        return makeActionItem("Add Deck", () -> {
            Model.CardsLists.Deck newDeck = new Model.CardsLists.Deck();
            newDeck.setName("New Deck");
            List<List<Model.CardsLists.Deck>> units = parentCollection.getLinkedDecks();
            if (units != null) {
                int unitIdx = -1;
                outer:
                for (int i = 0; i < units.size(); i++) {
                    List<Model.CardsLists.Deck> unit = units.get(i);
                    if (unit == null) continue;
                    for (Model.CardsLists.Deck d : unit) {
                        if (d == reference) {
                            unitIdx = i;
                            break outer;
                        }
                    }
                }
                List<Model.CardsLists.Deck> newUnit = new java.util.ArrayList<>();
                newUnit.add(newDeck);
                if (unitIdx >= 0) units.add(unitIdx + 1, newUnit);
                else units.add(newUnit);
            } else {
                parentCollection.AddDeck(newDeck);
            }
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newDeck);
            refreshDecksAndCollectionsView();
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
        return makeActionItem("Add Deck", () -> {
            Model.CardsLists.Deck newDeck = new Model.CardsLists.Deck();
            newDeck.setName("New Deck");
            if (dac.getDecks() == null) dac.setDecks(new java.util.ArrayList<>());
            int idx = dac.getDecks().indexOf(reference);
            if (idx >= 0) dac.getDecks().add(idx + 1, newDeck);
            else dac.getDecks().add(newDeck);
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newDeck);
            refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markDirty(newDeck);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Add Deck" from the empty-background context: appends a standalone deck at end.
     */
    private static MenuItem makeDecksAddStandaloneDeckAtEndItem() {
        return makeActionItem("Add Deck", () -> {
            Model.CardsLists.DecksAndCollectionsList dac =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (dac == null) return;
            Model.CardsLists.Deck newDeck = new Model.CardsLists.Deck();
            newDeck.setName("New Deck");
            if (dac.getDecks() == null) dac.setDecks(new java.util.ArrayList<>());
            dac.getDecks().add(newDeck);
            Controller.UserInterfaceFunctions.setPendingDecksRenameTarget(newDeck);
            refreshDecksAndCollectionsView();
            Controller.UserInterfaceFunctions.markDirty(newDeck);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        });
    }

    /**
     * "Rename" for an existing Deck or ThemeCollection: walks up from the context-menu
     * owner node to find the NavigationItem and starts an inline rename directly on it.
     * No pending-rename mechanism needed since the object already exists in the model.
     */
    private static MenuItem makeDecksRenameItem(Object modelObj) {
        MenuItem mi = new MenuItem();
        Label lbl = new Label("Rename");
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox g = new HBox(lbl);
        g.setAlignment(Pos.CENTER_LEFT);
        g.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(g);
        mi.setText("");

        mi.setOnAction(e -> {
            javafx.scene.Node owner = null;
            try {
                owner = mi.getParentPopup().getOwnerNode();
            } catch (Throwable ignored) {
            }
            View.NavigationItem navItem = findNavigationItemAncestor(owner);
            if (navItem == null) return;

            navItem.startInlineRename(
                    newName -> {
                        if (modelObj instanceof Model.CardsLists.Deck)
                            ((Model.CardsLists.Deck) modelObj).setName(newName);
                        else if (modelObj instanceof Model.CardsLists.ThemeCollection)
                            ((Model.CardsLists.ThemeCollection) modelObj).setName(newName);
                        navItem.getLabel().setText(newName);
                        refreshDecksAndCollectionsView();
                        Controller.UserInterfaceFunctions.markDirty(modelObj);
                        Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    },
                    null   // cancel is a no-op for an existing element
            );
        });

        return mi;
    }

    // =========================================================================
    // Public helpers (used by CardTreeCell)
    // =========================================================================

    public static MenuItem makeItem(String text) {
        MenuItem mi = new MenuItem();
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(graphic);
        mi.setText("");
        mi.setOnAction(e -> { /* TODO: implement action for: " + text + " */ });
        return mi;
    }

    /**
     * Remove item with no action (stub).
     */
    public static MenuItem makeRemoveItem() {
        return makeRemoveItem(null);
    }

    /**
     * Remove item wired to the given action.
     */
    public static MenuItem makeRemoveItem(Runnable action) {
        MenuItem mi = new MenuItem();
        Label trashIcon = new Label("\uD83D\uDDD1");
        trashIcon.setStyle("-fx-text-fill: #ff4d4d; -fx-font-size: 13;");
        Label removeLabel = new Label("Remove");
        removeLabel.setStyle("-fx-text-fill: #ff4d4d; -fx-font-weight: bold; -fx-font-size: 13;");
        HBox graphic = new HBox(6, trashIcon, removeLabel);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(graphic);
        mi.setText("");
        mi.setOnAction(e -> {
            if (action != null) action.run(); });
        return mi;
    }

    public static ContextMenu styledContextMenu() {
        ContextMenu cm = new ContextMenu();
        cm.setStyle(
                "-fx-background-color: #100317; " +
                        "-fx-background-radius: 6; " +
                        "-fx-border-color: #3a3a3a; " +
                        "-fx-border-radius: 6; "            +
                        "-fx-border-width: 1;"
        );
        return cm;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private static Menu makeLazyMenu(String text) {
        Menu m = new Menu();
        Label lbl = new Label(text);
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox g = new HBox(lbl);
        g.setAlignment(Pos.CENTER_LEFT);
        g.setPadding(new Insets(2, 6, 2, 6));
        m.setGraphic(g);
        m.setText("");
        MenuItem ph = new MenuItem("Loading...");
        ph.setDisable(true);
        m.getItems().add(ph);
        return m;
    }

    private static MenuItem disabledItem(String text) {
        MenuItem mi = new MenuItem(text);
        mi.setDisable(true);
        return mi;
    }

    /*private static String sanitize(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[=\\-]", "").trim();
    }*/
    private static String sanitize(String raw) {
        if (raw == null) return "";
        // Strip only leading/trailing decorator characters (= for boxes, - for categories),
        // preserving hyphens that are genuinely part of the name.
        String s = raw.trim();
        // Strip leading = or -
        int start = 0;
        while (start < s.length() && (s.charAt(start) == '=' || s.charAt(start) == '-')) start++;
        // Strip trailing = or -
        int end = s.length();
        while (end > start && (s.charAt(end - 1) == '=' || s.charAt(end - 1) == '-')) end--;
        return s.substring(start, end).trim();
    }

    // ── Emptiness checks ─────────────────────────────────────────────────────

    private static boolean isBoxEmpty(Model.CardsLists.Box box) {
        if (box == null) return true;
        if (box.getSubBoxes() != null && !box.getSubBoxes().isEmpty()) return false;
        if (box.getContent() != null) {
            for (Model.CardsLists.CardsGroup g : box.getContent()) {
                if (g == null) continue;
                if (g.getCardList() != null && !g.getCardList().isEmpty()) return false;
            }
        }
        return true;
    }

    private static boolean isCategoryEmpty(Model.CardsLists.CardsGroup category) {
        if (category == null) return true;
        return category.getCardList() == null || category.getCardList().isEmpty();
    }

    private static boolean isCollectionEmpty(Model.CardsLists.ThemeCollection tc) {
        if (tc == null) return true;
        // Check the collection's own card list
        try {
            java.util.List<?> list = tc.toList();
            if (list != null && !list.isEmpty()) return false;
        } catch (Exception ignored) {
        }
        // Check linked decks
        if (tc.getLinkedDecks() != null) {
            for (List<Model.CardsLists.Deck> group : tc.getLinkedDecks()) {
                if (group != null && !group.isEmpty()) return false;
            }
        }
        // Check archetypes via reflection (method name may vary)
        try {
            java.lang.reflect.Method m = tc.getClass().getMethod("getArchetypes");
            Object result = m.invoke(tc);
            if (result instanceof java.util.Collection && !((java.util.Collection<?>) result).isEmpty())
                return false;
        } catch (Exception ignored) {
        }
        return true;
    }

    private static boolean isDeckEmpty(Model.CardsLists.Deck deck) {
        if (deck == null) return true;
        if (deck.getMainDeck() != null && !deck.getMainDeck().isEmpty()) return false;
        if (deck.getExtraDeck() != null && !deck.getExtraDeck().isEmpty()) return false;
        if (deck.getSideDeck() != null && !deck.getSideDeck().isEmpty()) return false;
        return true;
    }

    // ── Confirmation dialog ───────────────────────────────────────────────────

    /**
     * Shows a styled Yes/No confirmation dialog.
     * Enter triggers Yes, Escape triggers No.
     */
    public static boolean confirmWithCustomMessage(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm");
        alert.setHeaderText(null);

        ButtonType yes = new ButtonType("Yes", ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType("No", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(yes, no);

        javafx.scene.control.DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle(
                "-fx-background-color: #100317;" +
                        "-fx-border-color: #cdfc04;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;");
        dialogPane.setContentText(message);

        javafx.scene.Node contentLabel = dialogPane.lookup(".content.label");
        if (contentLabel instanceof javafx.scene.control.Label) {
            ((javafx.scene.control.Label) contentLabel)
                    .setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13px;");
        }

        javafx.scene.Node barNode = dialogPane.lookup(".button-bar");
        if (barNode instanceof javafx.scene.control.ButtonBar) {
            ((javafx.scene.control.ButtonBar) barNode).setStyle(
                    "-fx-background-color: #100317; -fx-padding: 10 20 14 20;");
        }

        alert.getDialogPane().getScene().windowProperty().addListener((obs, oldWindow, newWindow) -> {
            if (newWindow != null) stylePopupButtons(dialogPane, yes, no);
        });
        if (dialogPane.getScene() != null && dialogPane.getScene().getWindow() != null) {
            stylePopupButtons(dialogPane, yes, no);
        }

        javafx.stage.Stage stage = (javafx.stage.Stage) dialogPane.getScene().getWindow();
        stage.initStyle(javafx.stage.StageStyle.UNDECORATED);

        java.util.Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == yes;
    }

    // Replace the old confirmRemoval body with a call to the new method:
    private static boolean confirmRemoval(String elementType) {
        return confirmWithCustomMessage("Do you really want to remove this " + elementType + "?");
    }

    private static void stylePopupButtons(javafx.scene.control.DialogPane dp,
                                          ButtonType yes, ButtonType no) {
        String base =
                "-fx-background-radius: 4;" +
                        "-fx-border-radius: 4;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-font-size: 13px;" +
                        "-fx-padding: 6 18 6 18;" +
                        "-fx-cursor: hand;";

        String yesNormal = base + "-fx-background-color: black; -fx-text-fill: #ff4d4d; -fx-border-color: #ff4d4d;";
        String yesHover = base + "-fx-background-color: #c92c2c; -fx-text-fill: black; -fx-font-weight: bold; -fx-border-color: #ff4d4d;";
        String noNormal = base + "-fx-background-color: black; -fx-text-fill: #cdfc04; -fx-border-color: #cdfc04;";
        String noHover = base + "-fx-background-color: #a4c904; -fx-text-fill: black; -fx-font-weight: bold; -fx-border-color: #cdfc04;";

        javafx.scene.Node yesNode = dp.lookupButton(yes);
        javafx.scene.Node noNode = dp.lookupButton(no);

        if (yesNode instanceof javafx.scene.control.Button btn) {
            btn.setStyle(yesNormal);
            btn.setOnMouseEntered(e -> btn.setStyle(yesHover));
            btn.setOnMouseExited(e -> btn.setStyle(yesNormal));
        }
        if (noNode instanceof javafx.scene.control.Button btn) {
            btn.setStyle(noNormal);
            btn.setOnMouseEntered(e -> btn.setStyle(noHover));
            btn.setOnMouseExited(e -> btn.setStyle(noNormal));
        }
    }

    // ── UI refresh helpers ────────────────────────────────────────────────────
    private static void refreshOwnedCollectionView() {
        Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
    }

    private static void refreshDecksAndCollectionsView() {
        Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
        //Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
    }

    // ── Move helpers ─────────────────────────────────────────────────────────────

    /**
     * Creates a MenuItem with a white-styled label wired to the given action.
     */
    private static MenuItem makeActionItem(String text, Runnable action) {
        MenuItem mi = new MenuItem();
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(graphic);
        mi.setText("");
        mi.setOnAction(e -> {
            if (action != null) action.run();
        });
        return mi;
    }

    /**
     * Moves {@code box} to become a sub-box of {@code targetBox}.
     * The box is first removed from its current location in the collection.
     */
    private static void doMoveBoxToSubBox(
            Model.CardsLists.Box box,
            Model.CardsLists.Box targetBox,
            Model.CardsLists.OwnedCardsCollection owned) {

        if (box == null || targetBox == null || owned == null) return;
        removeBoxFromAnywhere(box, owned);
        if (targetBox.getSubBoxes() == null) targetBox.setSubBoxes(new java.util.ArrayList<>());
        targetBox.getSubBoxes().add(box);
    }

    /**
     * Promotes {@code box} from its current sub-box position to the top level
     * of the collection.
     */
    private static void doMoveBoxToTopLevel(
            Model.CardsLists.Box box,
            Model.CardsLists.OwnedCardsCollection owned) {

        if (box == null || owned == null) return;
        removeBoxFromAnywhere(box, owned);
        owned.getOwnedCollection().add(box);
    }

    /**
     * Removes {@code box} from wherever it currently lives (top-level or sub-box).
     */
    private static void removeBoxFromAnywhere(
            Model.CardsLists.Box box,
            Model.CardsLists.OwnedCardsCollection owned) {

        if (box == null || owned == null || owned.getOwnedCollection() == null) return;
        if (owned.getOwnedCollection().remove(box)) return;
        for (Model.CardsLists.Box top : owned.getOwnedCollection()) {
            if (top == null || top.getSubBoxes() == null) continue;
            if (top.getSubBoxes().remove(box)) return;
        }
    }

    /**
     * Finds the Box that contains {@code box} as a sub-box, or {@code null}
     * if {@code box} is at the top level.
     */
    private static Model.CardsLists.Box findParentOfBox(
            Model.CardsLists.Box box,
            Model.CardsLists.OwnedCardsCollection owned) {

        if (owned == null || owned.getOwnedCollection() == null) return null;
        for (Model.CardsLists.Box top : owned.getOwnedCollection()) {
            if (top == null || top.getSubBoxes() == null) continue;
            for (Model.CardsLists.Box sub : top.getSubBoxes()) {
                if (sub == box) return top;
            }
        }
        return null;
    }

    /**
     * Returns all boxes that are descendants of {@code root} (to prevent
     * moving a box into one of its own descendants, which would create a cycle).
     */
    private static java.util.Set<Model.CardsLists.Box> collectDescendants(
            Model.CardsLists.Box root) {

        java.util.Set<Model.CardsLists.Box> result = new java.util.HashSet<>();
        if (root == null || root.getSubBoxes() == null) return result;
        java.util.Deque<Model.CardsLists.Box> stack = new java.util.ArrayDeque<>(root.getSubBoxes());
        while (!stack.isEmpty()) {
            Model.CardsLists.Box cur = stack.pop();
            if (cur == null || !result.add(cur)) continue;
            if (cur.getSubBoxes() != null) stack.addAll(cur.getSubBoxes());
        }
        return result;
    }

    /**
     * Moves {@code category} from {@code fromBox} to {@code toBox}.
     * <p>
     * If {@code insertAfter} is non-null, the category is inserted immediately
     * after that group in {@code toBox}'s content list; otherwise it is appended.
     */
    private static void doMoveCategory(
            Model.CardsLists.CardsGroup category,
            Model.CardsLists.Box fromBox,
            Model.CardsLists.Box toBox,
            Model.CardsLists.CardsGroup insertAfter,
            Model.CardsLists.OwnedCardsCollection owned) {

        if (category == null || toBox == null) return;

        // Remove from source
        if (fromBox != null && fromBox.getContent() != null) {
            fromBox.getContent().remove(category);
        } else {
            // Fallback: search the whole collection
            if (owned != null && owned.getOwnedCollection() != null) {
                outer:
                for (Model.CardsLists.Box b : owned.getOwnedCollection()) {
                    if (b == null) continue;
                    for (Model.CardsLists.Box candidate : new java.util.ArrayList<>(
                            b.getSubBoxes() == null ? java.util.Collections.emptyList() : b.getSubBoxes())) {
                        if (candidate != null && candidate.getContent() != null
                                && candidate.getContent().remove(category)) break outer;
                    }
                    if (b.getContent() != null && b.getContent().remove(category)) break;
                }
            }
        }

        // Ensure target has a content list
        if (toBox.getContent() == null) toBox.setContent(new java.util.ArrayList<>());

        // Insert after the reference group, or append
        if (insertAfter != null) {
            int idx = toBox.getContent().indexOf(insertAfter);
            if (idx >= 0 && idx + 1 <= toBox.getContent().size()) {
                toBox.getContent().add(idx + 1, category);
                return;
            }
        }
        toBox.getContent().add(category);
    }

    /**
     * Builds a "Rename" MenuItem that stores the target model object.
     * The actual inline-editor is launched on the NavigationItem that owns the
     * context menu, found by walking the scene from the menu's anchor.
     * <p>
     * Pass non-null {@code box} for a Box rename, non-null {@code category} for a Category rename.
     */
    private static MenuItem makeRenameItem(
            Model.CardsLists.Box box,
            Model.CardsLists.CardsGroup category,
            Model.CardsLists.OwnedCardsCollection owned) {

        MenuItem mi = new MenuItem();
        Label lbl = new Label("Rename");
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox g = new HBox(lbl);
        g.setAlignment(Pos.CENTER_LEFT);
        g.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(g);
        mi.setText("");

        mi.setOnAction(e -> {
            // Find the NavigationItem whose label was right-clicked.
            // The ContextMenu remembers the node it was shown on via getOwnerNode().
            javafx.scene.Node owner = null;
            try {
                owner = mi.getParentPopup().getOwnerNode();
            } catch (Throwable ignored) {
            }

            // Walk up from the owner node to find the NavigationItem ancestor.
            View.NavigationItem navItem = findNavigationItemAncestor(owner);
            if (navItem == null) return;

            navItem.startInlineRename(
                    newName -> {
                        // Update the model
                        if (box != null) {
                            // Preserve decoration characters (=) so the file format stays intact.
                            // Simply replace the visible part between the leading/trailing = signs.
                            String raw = box.getName() == null ? "" : box.getName();
                            String updated = rebuildDecoratedName(raw, newName, '=');
                            box.setName(updated);
                            navItem.getLabel().setText(newName);
                        } else if (category != null) {
                            String raw = category.getName() == null ? "" : category.getName();
                            String updated = rebuildDecoratedName(raw, newName, '-');
                            category.setName(updated);
                            navItem.getLabel().setText(newName);
                        }
                        // Refresh both views
                        Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
                        Controller.UserInterfaceFunctions.markMyCollectionDirty();
                        Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    },
                    null  // cancel: nothing to do
            );
        });

        return mi;
    }

    /**
     * Rebuilds a decorated name like "===OldName======" or "---OldName----"
     * by replacing the non-decoration portion with {@code newDisplayName}.
     * If the raw name contains no decoration characters the new display name
     * is returned as-is.
     */
    private static String rebuildDecoratedName(String raw, String newDisplayName, char decorator) {
        if (raw == null || raw.isEmpty()) return newDisplayName;
        // Count leading decoration characters
        int leading = 0;
        while (leading < raw.length() && raw.charAt(leading) == decorator) leading++;
        int trailing = 0;
        while (trailing < raw.length() && raw.charAt(raw.length() - 1 - trailing) == decorator) trailing++;
        if (leading == 0 && trailing == 0) return newDisplayName;
        String prefix = raw.substring(0, leading);
        String suffix = raw.substring(raw.length() - trailing);
        return prefix + newDisplayName + suffix;
    }

    /**
     * Walks up the scene-graph from {@code node} and returns the first
     * {@link View.NavigationItem} ancestor, or {@code null} if none is found.
     */
    private static View.NavigationItem findNavigationItemAncestor(javafx.scene.Node node) {
        javafx.scene.Node cur = node;
        while (cur != null) {
            if (cur instanceof View.NavigationItem) return (View.NavigationItem) cur;
            cur = cur.getParent();
        }
        return null;
    }

    private static MenuItem makePasteItem(Runnable pasteAction) {
        MenuItem mi = new MenuItem();
        Label lbl = new Label("Paste");
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox g = new HBox(lbl);
        g.setAlignment(Pos.CENTER_LEFT);
        g.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(g);
        mi.setText("");
        mi.setVisible(!Controller.CardClipboard.isEmpty());
        // Refresh visibility each time the menu opens
        Controller.CardClipboard.addChangeListener(() -> {
            javafx.application.Platform.runLater(() -> {
                boolean notEmpty = !Controller.CardClipboard.isEmpty();
                mi.setVisible(notEmpty);
            });
        });
        mi.setOnAction(e -> {
            if (!Controller.CardClipboard.isEmpty()) pasteAction.run();
        });
        return mi;
    }
}