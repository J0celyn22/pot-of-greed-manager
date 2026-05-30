package View;

import Controller.CardGroupRegistry;
import Model.CardsLists.*;
import Utils.LruImageCache;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.controlsfx.control.GridCell;
import org.controlsfx.control.GridView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Future;

/**
 * CardGridCell — a {@link GridCell} rendering one {@link CardElement} in the middle-pane
 * {@link GridView}. Promoted from inner class of {@link CardTreeCell} to package-private
 * top-level class. All shared state is accessed through the {@link #outer} reference.
 */
class CardGridCell extends GridCell<CardElement> {
    final CardTreeCell outer;
    private static final Logger logger = LoggerFactory.getLogger(CardGridCell.class);

    private final ImageView cardImageView;
    final StackPane wrapper;
    private Future<?> imageLoadFuture;
    private String currentImageKey;
    /**
     * Glow priority for this cell, read by the hover handler.
     * 0 = none | 1 = white (archetype/artwork missing or needs-sort)
     * 2 = orange (condition or rarity not set) | 3 = red (no printCode).
     * The glow is only applied when outer.incompleteMarkingEnabled for priorities 2 & 3.
     */
    private int currentGlowPriority = 0;
    /**
     * All applicable tooltip warnings for this cell, computed unconditionally.
     * Each entry is a two-element array: [message, cssColor].
     * Priorities 2 & 3 (My Collection completeness) are always collected even
     * when outer.incompleteMarkingEnabled is false, so the hover tooltip is always
     * informative regardless of whether the glow overlay is active.
     */
    private java.util.List<String[]> currentTooltips = new java.util.ArrayList<>();

    public CardGridCell(CardTreeCell outerCell) {
        this.outer = outerCell;
        cardImageView = new ImageView();
        cardImageView.setPreserveRatio(true);
        cardImageView.fitWidthProperty().bind(outer.cardWidthProperty);
        cardImageView.fitHeightProperty().bind(outer.cardHeightProperty);

        wrapper = new StackPane(cardImageView);
        wrapper.getProperties().put("cardWrapper", Boolean.TRUE);
        wrapper.setPadding(new Insets(5));
        wrapper.setStyle("-fx-background-color: transparent;");
        wrapper.setPickOnBounds(true);
        wrapper.setFocusTraversable(true);
        setGraphic(wrapper);

        // ----------------------------
        // Context menu: only in "My Collection" tab
        // ----------------------------
        ContextMenu contextMenu = new ContextMenu();

        // --- Sort card as a real Menu (submenu) with left-aligned accent label ---
        Menu sortingMenu = new Menu();
        {
            Label sortLabel = new Label("Sort card");
            sortLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-weight: normal; -fx-font-size: 13;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox sortGraphic = new HBox(6, sortLabel, spacer);
            sortGraphic.setAlignment(Pos.CENTER_LEFT);
            sortGraphic.setPadding(new Insets(2, 6, 2, 6));

            sortingMenu.setGraphic(sortGraphic);
            sortingMenu.setText(""); // hide default text since we use the graphic

            // Add a disabled placeholder so the Menu is rendered as a submenu by the platform.
            MenuItem placeholder = new MenuItem("Loading...");
            placeholder.setDisable(true);
            sortingMenu.getItems().add(placeholder);

            // Populate the submenu each time it is shown (lazy population).
            // Replace the existing sortingMenu.setOnShowing(...) with this block
            sortingMenu.setOnShowing(evt -> {
                try {
                    sortingMenu.getItems().clear();

                    CardElement ce = getItem();
                    if (ce == null || ce.getCard() == null) {
                        MenuItem none = new MenuItem("No card selected");
                        none.setDisable(true);
                        sortingMenu.getItems().add(none);
                        return;
                    }
                    Model.CardsLists.Card card = ce.getCard();

                    // 1) Decks and Collections section
                    List<MenuItem> dcItems = outer.buildDecksAndCollectionsProposals(card, ce);
                    if (!dcItems.isEmpty()) {
                        sortingMenu.getItems().addAll(dcItems);
                        sortingMenu.getItems().add(new SeparatorMenuItem());
                    }

                    // 2) Type of Cards boxes section
                    List<MenuItem> typeItems = outer.buildTypeBoxProposals(card, ce);
                    if (!typeItems.isEmpty()) {
                        sortingMenu.getItems().addAll(typeItems);
                        sortingMenu.getItems().add(new SeparatorMenuItem());
                    }

                    // 3) Swap section: try to call buildSwapProposals(card, ce) if it exists.
                    //    Use reflection so compilation does not require the method to be present.
                    try {
                        java.lang.reflect.Method swapMethod = null;
                        try {
                            swapMethod = CardTreeCell.class.getDeclaredMethod("buildSwapProposals", Model.CardsLists.Card.class, Model.CardsLists.CardElement.class);
                        } catch (NoSuchMethodException ns) {
                            // try public method on this class (in case it's declared elsewhere)
                            try {
                                swapMethod = CardTreeCell.class.getMethod("buildSwapProposals", Model.CardsLists.Card.class, Model.CardsLists.CardElement.class);
                            } catch (NoSuchMethodException ignored) {
                            }
                        }

                        if (swapMethod != null) {
                            swapMethod.setAccessible(true);
                            @SuppressWarnings("unchecked")
                            List<MenuItem> swapItems = (List<MenuItem>) swapMethod.invoke(outer, card, ce);
                            if (swapItems != null && !swapItems.isEmpty()) {
                                sortingMenu.getItems().addAll(swapItems);
                            }
                            // No placeholder when empty — other sections already filled the menu
                        } else {
                            // If the helper isn't present, do nothing (preserve existing behavior)
                            // Optionally add a disabled placeholder if you prefer:
                            // MenuItem noneSwap = new MenuItem("Swap not available");
                            // noneSwap.setDisable(true);
                            // sortingMenu.getItems().add(noneSwap);
                        }
                    } catch (Throwable t) {
                        // If reflection or invocation fails, log and show a disabled error item
                        logger.debug("Failed to invoke buildSwapProposals via reflection", t);
                        MenuItem err = new MenuItem("Error building swap proposals");
                        err.setDisable(true);
                        sortingMenu.getItems().add(err);
                    }

                    // If nothing was added at all (very unlikely because earlier sections usually add items),
                    // ensure the menu shows a disabled placeholder so it renders correctly.
                    if (sortingMenu.getItems().isEmpty()) {
                        MenuItem none = new MenuItem("No proposals");
                        none.setDisable(true);
                        sortingMenu.getItems().add(none);
                    }
                } catch (Throwable t) {
                    logger.debug("Error building sorting submenu", t);
                    sortingMenu.getItems().clear();
                    MenuItem err = new MenuItem("Error building proposals");
                    err.setDisable(true);
                    sortingMenu.getItems().add(err);
                }
            });
        }
        contextMenu.getItems().add(sortingMenu);

        // --- Swap with... submenu — shown only for degraded D&C cards ---
        // --- Move to... as a Menu (submenu) with same left-aligned accent label ---
        Menu moveToMenu = new Menu();
        {
            Label moveLabel = new Label("Move to...");
            moveLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-weight: normal; -fx-font-size: 13;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox moveGraphic = new HBox(6, moveLabel, spacer);
            moveGraphic.setAlignment(Pos.CENTER_LEFT);
            moveGraphic.setPadding(new Insets(2, 6, 2, 6));

            moveToMenu.setGraphic(moveGraphic);
            moveToMenu.setText("");

            // Add a disabled placeholder so the Menu is rendered as a submenu by the platform.
            MenuItem placeholder = new MenuItem("Loading...");
            placeholder.setDisable(true);
            moveToMenu.getItems().add(placeholder);

            // Populate the "Move to..." submenu lazily when it is shown.
            moveToMenu.setOnShowing(evt -> {
                moveToMenu.getItems().clear();

                // Get the clicked element (the card under the mouse / this cell)
                CardElement clickedElement = getItem();
                if (clickedElement == null) {
                    MenuItem none = new MenuItem("No card selected");
                    none.setDisable(true);
                    moveToMenu.getItems().add(none);
                    return;
                }

                // Obtain the owned collection (load if necessary)
                Model.CardsLists.OwnedCardsCollection owned = null;
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

                if (owned == null || owned.getOwnedCollection() == null || owned.getOwnedCollection().isEmpty()) {
                    MenuItem none = new MenuItem("No boxes available");
                    none.setDisable(true);
                    moveToMenu.getItems().add(none);
                    return;
                }

                // Find current source location (box and group) for the clicked element so we can exclude the exact category
                Model.CardsLists.Box currentBox = null;
                Model.CardsLists.CardsGroup currentGroup = null;
                try {
                    outer:
                    for (Model.CardsLists.Box b : owned.getOwnedCollection()) {
                        if (b == null) continue;
                        if (b.getContent() != null) {
                            for (Model.CardsLists.CardsGroup g : b.getContent()) {
                                if (g == null || g.getCardList() == null) continue;
                                for (Model.CardsLists.CardElement ce : g.getCardList()) {
                                    if (ce == clickedElement) {
                                        currentBox = b;
                                        currentGroup = g;
                                        break outer;
                                    }
                                }
                            }
                        }
                        if (b.getSubBoxes() != null) {
                            for (Model.CardsLists.Box sb : b.getSubBoxes()) {
                                if (sb == null || sb.getContent() == null) continue;
                                for (Model.CardsLists.CardsGroup g : sb.getContent()) {
                                    if (g == null || g.getCardList() == null) continue;
                                    for (Model.CardsLists.CardElement ce : g.getCardList()) {
                                        if (ce == clickedElement) {
                                            currentBox = sb;
                                            currentGroup = g;
                                            break outer;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }

                // Helper to create a MenuItem that triggers the move handler
                java.util.function.BiFunction<String, String, MenuItem> makeMoveItem = (display, handlerTarget) -> {
                    MenuItem mi = new MenuItem(display);
                    mi.setOnAction(ae -> {
                        try {
                            List<CardElement> elementsToMove = getEffectiveMiddleElements();
                            if (elementsToMove.size() > 1) {
                                Controller.MenuActionHandler.handleBulkMove(
                                        new ArrayList<>(elementsToMove), handlerTarget);
                            } else {
                                Controller.MenuActionHandler.handleMove(getItem(), handlerTarget);
                            }
                        } catch (Throwable throwable) {
                            logger.debug("Move action failed for target {}", handlerTarget, throwable);
                        }
                    });
                    return mi;
                };

                // Iterate owned boxes and their groups to create menu entries.
                // We exclude ONLY the exact current group; box-level destinations are always allowed.
                for (Model.CardsLists.Box b : owned.getOwnedCollection()) {
                    if (b == null) continue;
                    String boxName = Model.CardsLists.OwnedCardsCollection.extractName(b.getName() == null ? "" : b.getName(), '=');
                    if (boxName.isEmpty()) boxName = "(Unnamed box)";

                    // Always offer the box-level destination (even if the card currently lives in a category of this box)
                    MenuItem miBox = makeMoveItem.apply(boxName, boxName);
                    moveToMenu.getItems().add(miBox);

                    // Add entries for each group inside the box
                    if (b.getContent() != null) {
                        for (Model.CardsLists.CardsGroup g : b.getContent()) {
                            if (g == null) continue;

                            // Skip unnamed groups entirely — the Box button already covers this destination
                            String rawName = g.getName();
                            if (rawName == null || rawName.trim().isEmpty()) {
                                continue;
                            }

                            String groupName = Model.CardsLists.OwnedCardsCollection.extractName(rawName, '-');
                            if (groupName.isEmpty()) continue;

                            boolean groupIsCurrent =
                                    (currentBox != null && currentGroup != null && currentBox == b && currentGroup == g);
                            if (groupIsCurrent) continue;

                            String display = boxName + " / " + groupName;
                            String handlerTarget = boxName + "/" + groupName;
                            MenuItem miGroup = makeMoveItem.apply(display, handlerTarget);
                            moveToMenu.getItems().add(miGroup);
                        }
                    }


                    // Also include sub-boxes (if any) as separate entries (and their groups)
                    if (b.getSubBoxes() != null) {
                        for (Model.CardsLists.Box sb : b.getSubBoxes()) {
                            if (sb == null) continue;
                            String subBoxName = Model.CardsLists.OwnedCardsCollection.extractName(sb.getName() == null ? "" : sb.getName(), '=');
                            if (subBoxName.isEmpty()) subBoxName = "(Unnamed sub-box)";

                            // Always offer sub-box-level destination too
                            MenuItem miSubBox = makeMoveItem.apply(subBoxName, subBoxName);
                            moveToMenu.getItems().add(miSubBox);

                            if (sb.getContent() != null) {
                                for (Model.CardsLists.CardsGroup g : sb.getContent()) {
                                    if (g == null) continue;
                                    String groupName = Model.CardsLists.OwnedCardsCollection.extractName(g.getName() == null ? "" : g.getName(), '-');
                                    if (groupName.isEmpty()) groupName = "(Unnamed group)";
                                    boolean groupIsCurrent = (currentBox != null && currentGroup != null && currentBox == sb && currentGroup == g);
                                    if (groupIsCurrent) continue;
                                    String display = subBoxName + " / " + groupName;
                                    String handlerTarget = subBoxName + "/" + groupName;
                                    MenuItem mi = makeMoveItem.apply(display, handlerTarget);
                                    moveToMenu.getItems().add(mi);
                                }
                            }
                        }
                    }
                }

                // If no valid destinations were added, show a disabled placeholder
                if (moveToMenu.getItems().isEmpty()) {
                    MenuItem none = new MenuItem("No other destinations");
                    none.setDisable(true);
                    moveToMenu.getItems().add(none);
                }
            });

        }
        contextMenu.getItems().add(moveToMenu);

        // ── Copy ──────────────────────────────────────────────────────────────────────
        MenuItem copyForCollectionMenuItem = new MenuItem();
        {
            Label copyLabel = new Label("Copy");
            copyLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
            HBox copyGraphic = new HBox(copyLabel);
            copyGraphic.setAlignment(Pos.CENTER_LEFT);
            copyGraphic.setPadding(new Insets(2, 6, 2, 6));
            copyForCollectionMenuItem.setGraphic(copyGraphic);
            copyForCollectionMenuItem.setText("");
            copyForCollectionMenuItem.setOnAction(ae -> executeCopyAction());
        }
        contextMenu.getItems().add(copyForCollectionMenuItem);

        // ── Cut ───────────────────────────────────────────────────────────────────────
        MenuItem cutForCollectionMenuItem = new MenuItem();
        {
            Label cutLabel = new Label("Cut");
            cutLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
            HBox cutGraphic = new HBox(cutLabel);
            cutGraphic.setAlignment(Pos.CENTER_LEFT);
            cutGraphic.setPadding(new Insets(2, 6, 2, 6));
            cutForCollectionMenuItem.setGraphic(cutGraphic);
            cutForCollectionMenuItem.setText("");
            cutForCollectionMenuItem.setOnAction(ae -> executeCutFromOwnedCollectionAction());
        }
        contextMenu.getItems().add(cutForCollectionMenuItem);

        // ── Paste ─────────────────────────────────────────────────────────────────────
        final MenuItem pasteAfterCardMenuItem = new MenuItem();
        {
            Label pasteLabel = new Label("Paste");
            pasteLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
            HBox pasteGraphic = new HBox(pasteLabel);
            pasteGraphic.setAlignment(Pos.CENTER_LEFT);
            pasteGraphic.setPadding(new Insets(2, 6, 2, 6));
            pasteAfterCardMenuItem.setGraphic(pasteGraphic);
            pasteAfterCardMenuItem.setText("");
            pasteAfterCardMenuItem.setVisible(false);
            pasteAfterCardMenuItem.setOnAction(ae -> {
                if (Controller.CardClipboard.isEmpty()) return;
                CardElement currentItem = getItem();
                if (currentItem == null) return;
                Controller.MenuActionHandler.handlePasteAfterElementInOwnedCollection(
                        Controller.CardClipboard.getContents(), currentItem);
            });
        }
        contextMenu.getItems().add(pasteAfterCardMenuItem);

        // ── Remove ────────────────────────────────────────────────────────────────────
        MenuItem removeRootItem = new MenuItem();
        Label removeTrashIcon = new Label("\uD83D\uDDD1");
        removeTrashIcon.setStyle("-fx-text-fill: #ff4d4d; -fx-font-size: 13;");
        Label removeLabel = new Label("Remove");
        removeLabel.setStyle("-fx-text-fill: #ff4d4d; -fx-font-weight: bold;");
        HBox removeGraphic = new HBox(6, removeTrashIcon, removeLabel);
        removeGraphic.setAlignment(Pos.CENTER_LEFT);
        removeGraphic.setPadding(new Insets(2, 6, 2, 6));
        removeRootItem.setGraphic(removeGraphic);
        removeRootItem.setText("");
        removeRootItem.setOnAction(ae -> {
            CardElement currentItem = getItem();
            if (currentItem == null) return;
            List<CardElement> elementsToRemove = getEffectiveMiddleElements();
            if (elementsToRemove.size() > 1) {
                Controller.MenuActionHandler.handleBulkRemoveFromOwnedCollection(
                        new ArrayList<>(elementsToRemove));
            } else {
                removeCardElement(currentItem);
                Controller.UserInterfaceFunctions.refreshOwnedCollectionView();
            }
        });

        // ── Edit Card ────────────────────────────────────────────────────────
        MenuItem editCardMenuItem = new MenuItem();
        {
            Label editLabel = new Label("Edit Card");
            editLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
            HBox editGraphic = new HBox(editLabel);
            editGraphic.setAlignment(Pos.CENTER_LEFT);
            editGraphic.setPadding(new Insets(2, 6, 2, 6));
            editCardMenuItem.setGraphic(editGraphic);
            editCardMenuItem.setText("");
            editCardMenuItem.setOnAction(ae -> {
                CardElement currentItem = getItem();
                if (currentItem != null)
                    Controller.MenuActionHandler.handleEditCard(currentItem, wrapper);
            });
        }
        contextMenu.getItems().add(editCardMenuItem);

        contextMenu.getItems().add(new SeparatorMenuItem());
        contextMenu.getItems().add(removeRootItem);

        // Keep the Remove item disabled when no card is selected; update on showing
        contextMenu.setOnShowing(ev -> {
            CardElement currentItem = getItem();
            boolean multiSelect = isMiddleMultiSelectActive();
            sortingMenu.setVisible(!multiSelect);
            editCardMenuItem.setDisable(currentItem == null || multiSelect);
            removeRootItem.setDisable(currentItem == null);
            pasteAfterCardMenuItem.setVisible(!Controller.CardClipboard.isEmpty());
        });

        // ── Context menu for Decks & Collections tab — NON-archetype cards ──
        ContextMenu decksContextMenu = new ContextMenu();
        decksContextMenu.setStyle(
                "-fx-background-color: #100317; -fx-background-radius: 6; " +
                        "-fx-border-color: #3a3a3a; -fx-border-radius: 6; -fx-border-width: 1;"
        );
        {
            // "Move to..." submenu (lazy-populated placeholder — actions implemented later)
            Menu decksMoveMenu = new Menu();
            {
                Label ml = new Label("Move to...");
                ml.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
                HBox mg = new HBox(ml);
                mg.setAlignment(Pos.CENTER_LEFT);
                mg.setPadding(new Insets(2, 6, 2, 6));
                decksMoveMenu.setGraphic(mg);
                decksMoveMenu.setText("");
                MenuItem placeholder = new MenuItem("Loading...");
                placeholder.setDisable(true);
                decksMoveMenu.getItems().add(placeholder);
                decksMoveMenu.setOnShowing(evt -> {
                    decksMoveMenu.getItems().clear();
                    String currentPath = findCurrentLocationPath();
                    List<MenuItem> items = buildMoveDestinationMenuItems(currentPath);
                    if (items.isEmpty()) {
                        MenuItem none = new MenuItem("No destinations available");
                        none.setDisable(true);
                        decksMoveMenu.getItems().add(none);
                    } else {
                        decksMoveMenu.getItems().addAll(items);
                    }
                });
            }
            decksContextMenu.getItems().add(decksMoveMenu);

            // ── Copy (D&C) ────────────────────────────────────────────────────────────────
            MenuItem decksCopyMenuItem = new MenuItem();
            {
                Label lbl = new Label("Copy");
                lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
                HBox g = new HBox(lbl);
                g.setAlignment(Pos.CENTER_LEFT);
                g.setPadding(new Insets(2, 6, 2, 6));
                decksCopyMenuItem.setGraphic(g);
                decksCopyMenuItem.setText("");
                decksCopyMenuItem.setOnAction(ae -> executeCopyAction());
            }
            decksContextMenu.getItems().add(decksCopyMenuItem);

            // ── Cut (D&C) ─────────────────────────────────────────────────────────────────
            MenuItem decksCutMenuItem = new MenuItem();
            {
                Label lbl = new Label("Cut");
                lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
                HBox g = new HBox(lbl);
                g.setAlignment(Pos.CENTER_LEFT);
                g.setPadding(new Insets(2, 6, 2, 6));
                decksCutMenuItem.setGraphic(g);
                decksCutMenuItem.setText("");
                decksCutMenuItem.setOnAction(ae -> executeCutFromDecksAction());
            }
            decksContextMenu.getItems().add(decksCutMenuItem);

            // ── Paste (D&C) ───────────────────────────────────────────────────────────────
            MenuItem decksPasteMenuItem = new MenuItem();
            {
                Label lbl = new Label("Paste");
                lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
                HBox g = new HBox(lbl);
                g.setAlignment(Pos.CENTER_LEFT);
                g.setPadding(new Insets(2, 6, 2, 6));
                decksPasteMenuItem.setGraphic(g);
                decksPasteMenuItem.setText("");
                decksPasteMenuItem.setOnAction(ae -> {
                    if (Controller.CardClipboard.isEmpty()) return;
                    CardElement currentItem = getItem();
                    if (currentItem == null) return;
                    @SuppressWarnings("unchecked")
                    javafx.collections.ObservableList<CardElement> deckGroupItems =
                            (javafx.collections.ObservableList<CardElement>) getGridView().getItems();
                    int insertionIndex = deckGroupItems.indexOf(currentItem);
                    if (insertionIndex < 0) insertionIndex = deckGroupItems.size() - 1;
                    List<Model.CardsLists.Card> clipboardCards = Controller.CardClipboard.getContents();
                    for (int i = 0; i < clipboardCards.size(); i++) {
                        Model.CardsLists.Card card = clipboardCards.get(i);
                        if (card == null) continue;
                        int targetIndex = insertionIndex + 1 + i;
                        if (targetIndex > deckGroupItems.size()) targetIndex = deckGroupItems.size();
                        deckGroupItems.add(targetIndex, new CardElement(card));
                    }
                    for (Map.Entry<Model.CardsLists.CardsGroup,
                            javafx.collections.ObservableList<CardElement>> entry
                            : CardGroupRegistry.GROUP_OBSERVABLE_LISTS.entrySet()) {
                        if (entry.getValue() == deckGroupItems) {
                            CardGroupRegistry.triggerHeightAdjustment(entry.getKey());
                            // Mark only the owner of this specific list dirty
                            Object pasteOwner = findDacOwnerForCardsGroup(entry.getKey());
                            if (pasteOwner != null)
                                Controller.UserInterfaceFunctions.markDirty(pasteOwner);
                            else
                                Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                            break;
                        }
                    }
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
                });
            }
            decksContextMenu.getItems().add(decksPasteMenuItem);

            // ── Edit Card (D&C) ──────────────────────────────────────────────
            MenuItem decksEditCardMenuItem = new MenuItem();
            {
                Label lbl = new Label("Edit Card");
                lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
                HBox g = new HBox(lbl);
                g.setAlignment(Pos.CENTER_LEFT);
                g.setPadding(new Insets(2, 6, 2, 6));
                decksEditCardMenuItem.setGraphic(g);
                decksEditCardMenuItem.setText("");
                decksEditCardMenuItem.setOnAction(ae -> {
                    CardElement currentItem = getItem();
                    if (currentItem != null)
                        Controller.MenuActionHandler.handleEditCard(currentItem, wrapper);
                });
            }
            decksContextMenu.getItems().add(decksEditCardMenuItem);

            decksContextMenu.getItems().add(new SeparatorMenuItem());

            // "Remove" (red + trash icon)
            MenuItem decksRemoveItem = new MenuItem();
            Label decksTrash = new Label("\uD83D\uDDD1");
            decksTrash.setStyle("-fx-text-fill: #ff4d4d; -fx-font-size: 13;");
            Label decksRemoveLabel = new Label("Remove");
            decksRemoveLabel.setStyle("-fx-text-fill: #ff4d4d; -fx-font-weight: bold;");
            HBox decksRemoveGraphic = new HBox(6, decksTrash, decksRemoveLabel);
            decksRemoveGraphic.setAlignment(Pos.CENTER_LEFT);
            decksRemoveGraphic.setPadding(new Insets(2, 6, 2, 6));
            decksRemoveItem.setGraphic(decksRemoveGraphic);
            decksRemoveItem.setText("");
            decksRemoveItem.setOnAction(ae -> {
                CardElement currentItem = getItem();
                if (currentItem == null) return;
                if (isMiddleMultiSelectActive()) {
                    Controller.MenuActionHandler.handleBulkRemoveElementsFromDecksAndCollections(
                            new ArrayList<>(Controller.SelectionManager.getSelectedMiddleElements()));
                    // Remove all selected cards from every list in D&C,
                    // marking only those that actually changed as dirty.
                    /*java.util.Set<Model.CardsLists.Card> cardsToRemove =
                            Controller.SelectionManager.getSelectedCards();
                    java.util.function.Predicate<List<CardElement>> removeMatchingCards = (list) -> {
                        if (list == null) return false;
                        return list.removeIf(ce -> ce != null && ce.getCard() != null
                                && cardsToRemove.contains(ce.getCard()));
                    };
                    java.util.Set<Object> dirtyOwners = new java.util.LinkedHashSet<>();
                    Model.CardsLists.DecksAndCollectionsList dac =
                            Controller.UserInterfaceFunctions.getDecksList();
                    if (dac != null) {
                        if (dac.getCollections() != null) {
                            for (Model.CardsLists.ThemeCollection themeCollection : dac.getCollections()) {
                                if (themeCollection == null) continue;
                                boolean tcChanged =
                                        removeMatchingCards.test(themeCollection.getCardsList())
                                                | removeMatchingCards.test(themeCollection.getExceptionsToNotAdd());
                                if (tcChanged) dirtyOwners.add(themeCollection);
                                if (themeCollection.getLinkedDecks() != null) {
                                    for (List<Model.CardsLists.Deck> unit : themeCollection.getLinkedDecks()) {
                                        if (unit == null) continue;
                                        for (Model.CardsLists.Deck deck : unit) {
                                            if (deck == null) continue;
                                            boolean changed =
                                                    removeMatchingCards.test(deck.getMainDeck())
                                                            | removeMatchingCards.test(deck.getExtraDeck())
                                                            | removeMatchingCards.test(deck.getSideDeck());
                                            if (changed) dirtyOwners.add(deck);
                                        }
                                    }
                                }
                            }
                        }
                        if (dac.getDecks() != null) {
                            for (Model.CardsLists.Deck deck : dac.getDecks()) {
                                if (deck == null) continue;
                                boolean changed =
                                        removeMatchingCards.test(deck.getMainDeck())
                                                | removeMatchingCards.test(deck.getExtraDeck())
                                                | removeMatchingCards.test(deck.getSideDeck());
                                if (changed) dirtyOwners.add(deck);
                            }
                        }
                    }
                    if (!dirtyOwners.isEmpty()) {
                        for (Object owner : dirtyOwners)
                            Controller.UserInterfaceFunctions.markDirty(owner);
                    } else {
                        // Fallback: nothing removed, but guard against inconsistent state
                        Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                    }
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();*/
                } else {
                    // Route single-element remove through the same model-modifying
                    // path as the bulk remove so that the model is updated before
                    // the view refreshes and the full tree rebuild is requested.
                    Controller.MenuActionHandler
                            .handleBulkRemoveElementsFromDecksAndCollections(
                                    java.util.Collections.singletonList(currentItem));
                }
            });
            decksContextMenu.getItems().add(decksRemoveItem);

            decksContextMenu.setOnShowing(ev -> {
                decksPasteMenuItem.setVisible(!Controller.CardClipboard.isEmpty());
                decksEditCardMenuItem.setDisable(getItem() == null);
            });
        }

        // ── Context menu for Decks & Collections tab — ARCHETYPE cards ────
        ContextMenu archetypeCardContextMenu = new ContextMenu();
        archetypeCardContextMenu.setStyle(
                "-fx-background-color: #100317; -fx-background-radius: 6; " +
                        "-fx-border-color: #3a3a3a; -fx-border-radius: 6; -fx-border-width: 1;"
        );
        {
            // "Add to..." submenu (lazy-populated placeholder — actions implemented later)
            Menu archetypeAddMenu = new Menu();
            {
                Label al = new Label("Add to...");
                al.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
                HBox ag = new HBox(al);
                ag.setAlignment(Pos.CENTER_LEFT);
                ag.setPadding(new Insets(2, 6, 2, 6));
                archetypeAddMenu.setGraphic(ag);
                archetypeAddMenu.setText("");
                MenuItem aPlaceholder = new MenuItem("Loading...");
                aPlaceholder.setDisable(true);
                archetypeAddMenu.getItems().add(aPlaceholder);
                archetypeAddMenu.setOnShowing(evt -> {
                    archetypeAddMenu.getItems().clear();
                    // null = no exclusion: archetype cards have no editable location
                    List<MenuItem> items = buildAddDestinationMenuItems();
                    if (items.isEmpty()) {
                        MenuItem none = new MenuItem("No destinations available");
                        none.setDisable(true);
                        archetypeAddMenu.getItems().add(none);
                    } else {
                        archetypeAddMenu.getItems().addAll(items);
                    }
                });
            }
            archetypeCardContextMenu.getItems().add(archetypeAddMenu);

            // ── Copy (Archetype) ──────────────────────────────────────────────────────────
            MenuItem archetypeCopyMenuItem = new MenuItem();
            {
                Label lbl = new Label("Copy");
                lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
                HBox g = new HBox(lbl);
                g.setAlignment(Pos.CENTER_LEFT);
                g.setPadding(new Insets(2, 6, 2, 6));
                archetypeCopyMenuItem.setGraphic(g);
                archetypeCopyMenuItem.setText("");
                archetypeCopyMenuItem.setOnAction(ae -> executeCopyAction());
            }
            archetypeCardContextMenu.getItems().add(archetypeCopyMenuItem);
        }

        // Attach the context menu to the wrapper so right-click shows it
        wrapper.setOnContextMenuRequested(e -> {
            if (outer.isMyCollectionTabSelected()) {
                // Existing My Collection menu (Sort card + Move to + Remove with trash)
                contextMenu.show(wrapper, e.getScreenX(), e.getScreenY());

            } else if (outer.isDecksAndCollectionsTabSelected()) {
                // Determine whether this card lives in an archetype group
                if (outer.isInArchetypeGroup()) {
                    archetypeCardContextMenu.show(wrapper, e.getScreenX(), e.getScreenY());
                } else {
                    decksContextMenu.show(wrapper, e.getScreenX(), e.getScreenY());
                }
            }
            e.consume();
        });

        // ── Selection click handler (MIDDLE pane) ────────────────────────────────────
        wrapper.setOnMouseClicked(event -> {
            if (event.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                return;
            }
            CardElement clickedCardElement = getItem();
            if (clickedCardElement == null || clickedCardElement.getCard() == null) {
                event.consume();
                return;
            }

            // Collect ALL elements from the entire TreeView in display order so that
            // SHIFT+click range selection spans across multiple groups/lists.
            TreeView<String> parentTreeView = outer.getTreeView();
            java.util.List<CardElement> allElementsInTreeOrder =
                    (parentTreeView != null)
                            ? outer.collectAllElementsInTreeOrder(parentTreeView.getRoot())
                            : new java.util.ArrayList<>();

            if (event.isControlDown()) {
                Controller.SelectionManager.toggleElementSelection(clickedCardElement);
            } else if (event.isShiftDown()) {
                Controller.SelectionManager.rangeSelectElements(clickedCardElement, allElementsInTreeOrder);
            } else {
                Controller.SelectionManager.selectElement(clickedCardElement);
            }
            event.consume();
        });

        // ── Middle-pane drag ──────────────────────────────────────────────────────────
        wrapper.setOnDragDetected(event -> {
            CardElement ce = getItem();
            if (ce == null || ce.getCard() == null) return;

            if (outer.isInArchetypeGroup()) {
                // Archetype cards are read-only — behave like a right-pane (ADD) drag.
                // Collect the FULL selection as the drag payload.
                java.util.List<Card> cards = new java.util.ArrayList<>();
                cards.add(ce.getCard());
                java.util.Set<CardElement> sel = Controller.SelectionManager.getSelectedMiddleElements();
                if ("MIDDLE".equals(Controller.SelectionManager.getActivePart())
                        && sel.size() > 1 && sel.contains(ce)) {
                    for (CardElement el : sel) {
                        if (el != ce && el.getCard() != null) cards.add(el.getCard());
                    }
                }

                // Ghost is capped at 5 images — visual only, does not affect payload.
                java.util.List<Image> ghostImages = new java.util.ArrayList<>();
                int ghostCount = Math.min(cards.size(), 5);
                for (int i = 0; i < ghostCount; i++) {
                    Card c = cards.get(i);
                    String key = c.getImagePath();
                    String rp = key != null ? outer.imagePathCache.get(key) : null;
                    ghostImages.add(rp != null ? LruImageCache.getImage(rp) : null);
                }
                javafx.scene.input.Dragboard db =
                        wrapper.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
                javafx.scene.image.WritableImage ghost =
                        Controller.DragDropManager.buildDragGhost(
                                ghostImages, outer.cardWidthProperty.get(), outer.cardHeightProperty.get());
                if (ghost != null)
                    db.setDragView(ghost, outer.cardWidthProperty.get() / 2.0, outer.cardHeightProperty.get() / 2.0);
                javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
                content.putString(ce.getCard().getPassCode());
                db.setContent(content);
                Controller.DragDropManager.startRightDrag(cards);
                event.consume();
                return;
            }

            // Collect the FULL selection as the drag payload — no cap here.
            java.util.Set<CardElement> selected =
                    Controller.SelectionManager.getSelectedMiddleElements();
            java.util.List<CardElement> dragElements = new java.util.ArrayList<>();
            dragElements.add(ce);
            if ("MIDDLE".equals(Controller.SelectionManager.getActivePart())
                    && selected.size() > 1 && selected.contains(ce)) {
                for (CardElement el : selected) {
                    if (el != ce) dragElements.add(el);
                }
            }

            // Ghost is capped at 5 images — visual only, does not affect payload.
            java.util.List<Image> ghostImages = new java.util.ArrayList<>();
            int ghostCount = Math.min(dragElements.size(), 5);
            for (int i = 0; i < ghostCount; i++) {
                CardElement el = dragElements.get(i);
                Card c = el.getCard();
                String key = c != null ? c.getImagePath() : null;
                String resolvedPath = key != null ? outer.imagePathCache.get(key) : null;
                ghostImages.add(resolvedPath != null ? LruImageCache.getImage(resolvedPath) : null);
            }

            javafx.scene.input.Dragboard db =
                    wrapper.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
            javafx.scene.image.WritableImage ghost =
                    Controller.DragDropManager.buildDragGhost(
                            ghostImages, outer.cardWidthProperty.get(), outer.cardHeightProperty.get());
            if (ghost != null) {
                db.setDragView(ghost,
                        outer.cardWidthProperty.get() / 2.0,
                        outer.cardHeightProperty.get() / 2.0);
            }

            javafx.scene.input.ClipboardContent content =
                    new javafx.scene.input.ClipboardContent();
            content.putString(ce.getCard().getPassCode());
            db.setContent(content);
            Controller.DragDropManager.startMiddleDrag(dragElements);
            event.consume();
        });

        wrapper.setOnDragDone(event -> {
            Controller.DragDropManager.clearCurrentlyDraggedCard();
            event.consume();
        });

        // ── Hover popup ───────────────────────────────────────────────────────────────
        // Attached to wrapper (not the outer GridCell) so it fires even when the
        // graphic intercepts all mouse events before they reach the Control.
        // Uses getItem() at event time so the text always matches the current card.
        wrapper.setOnMouseEntered(e -> {
            CardElement item = getItem();
            if (item == null) return;
            outer.hoverLabel.setText(
                    CardHoverPopup.buildTooltipText(item));

            // Switch popup border to orange when this card is an upgrade candidate
            // or a degraded D&C card (both use orange tooltips).
            boolean isOrangePopup = currentGlowPriority == 1
                    && !currentTooltips.isEmpty()
                    && currentTooltips.stream().anyMatch(t ->
                    t[0] != null && (t[0].equals(CardHoverPopup.UPGRADE_CANDIDATE_WARNING)
                            || t[0].equals(CardHoverPopup.DOWNGRADE_WARNING)));
            outer.hoverPopupBox.setStyle(
                    isOrangePopup
                            ? CardHoverPopup.POPUP_BOX_STYLE_ORANGE
                            : CardHoverPopup.POPUP_BOX_STYLE_DEFAULT);

            // Rebuild the warnings box from currentTooltips (computed in updateItem).
            // Always shown when applicable, regardless of whether the card is glowing.
            outer.warningsBox.getChildren().clear();
            for (String[] entry : currentTooltips) {
                Label wl = new Label(entry[0]);
                wl.setStyle("-fx-text-fill: " + entry[1] + "; " +
                        "-fx-font-size: 12; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 2 10 2 10;");
                wl.setWrapText(true);
                wl.setMaxWidth(260);
                outer.warningsBox.getChildren().add(wl);
            }
            boolean hasWarnings = !currentTooltips.isEmpty();
            outer.warningsBox.setVisible(hasWarnings);
            outer.warningsBox.setManaged(hasWarnings);

            outer.hoverPopup.show(wrapper, e.getScreenX() + 14, e.getScreenY() + 14);
        });
        wrapper.setOnMouseMoved(e -> {
            if (outer.hoverPopup.isShowing())
                outer.hoverPopup.show(wrapper, e.getScreenX() + 14, e.getScreenY() + 14);
        });
        wrapper.setOnMouseExited(e -> outer.hoverPopup.hide());

        // ── Drop target: card-level (left half = before, right half = after) ────────
        wrapper.setOnDragOver(event -> {
            if (!outer.isInArchetypeGroup()
                    && event.getDragboard().hasString()
                    && Controller.DragDropManager.getDragSourcePane() != null) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            }
            event.consume();
        });

        wrapper.setOnDragDropped(event -> {
            // Always reset the flag at the start of each drop so it is never
            // stale from a previous drag session (e.g. after a right-pane drag
            // where wrapper.setOnDragDone never fires).
            CardGroupRegistry.dropHandledByCell = false;

            if (outer.isInArchetypeGroup()) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            CardElement anchor = getItem();
            if (anchor == null) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            CardsGroup group = outer.findGroupForCardElement(anchor);
            if (group == null) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            javafx.collections.ObservableList<CardElement> list = outer.observableListFor(group);
            int anchorIdx = list.indexOf(anchor);
            if (anchorIdx < 0) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }

            // Determine left/right half using mouse X relative to the wrapper
            double localX = event.getX();
            boolean insertAfter = outer.isRightHalf(localX, outer.cardWidthProperty.get());
            int insertionIndex = insertAfter ? anchorIdx + 1 : anchorIdx;

            String srcPane = Controller.DragDropManager.getDragSourcePane();
            boolean isMiddle = "MIDDLE".equals(srcPane);

            if (isMiddle) {
                java.util.List<CardElement> srcElements =
                        new java.util.ArrayList<>(Controller.DragDropManager.getDraggedElements());
                // Don't move onto itself
                if (srcElements.size() == 1 && srcElements.get(0) == anchor) {
                    event.setDropCompleted(false);
                    event.consume();
                    return;
                }
                java.util.Set<CardsGroup> srcGroups =
                        outer.dropInsertIntoGroup(group, insertionIndex, srcElements, null);
                for (CardsGroup sg : srcGroups) outer.markDirtyAndRefreshForGroup(sg);
            } else {
                java.util.List<Model.CardsLists.Card> srcCards =
                        new java.util.ArrayList<>(Controller.DragDropManager.getDraggedCards());
                outer.dropInsertIntoGroup(group, insertionIndex, null, srcCards);
                // My Collection only: open edit popup for cards dropped without a printCode.
                if (outer.isMyCollectionTabSelected()) {
                    outer.openEditPopupsForNoPrintCode(srcCards, group, this);
                }
            }
            outer.markDirtyAndRefreshForGroup(group);
            // Signal to grid.setOnDragDropped that this drop is already handled.
            CardGroupRegistry.dropHandledByCell = true;
            event.setDropCompleted(true);
            event.consume();
        });
    } // end CardGridCell()

    // Helper: remove the given CardElement from the owned collection and refresh UI.
    // Tries MenuActionHandler.handleRemove(...) first, falls back to direct removal.
    void removeCardElement(Model.CardsLists.CardElement ce) {
        if (ce == null) return;

        // 1) Try centralized handler via reflection if available
        try {
            java.lang.reflect.Method m = Controller.MenuActionHandler.class.getMethod("handleRemove", Model.CardsLists.CardElement.class);
            if (m != null) {
                m.invoke(null, ce);
                // Ask UI to refresh
                Platform.runLater(() -> {
                    try {
                        if (getGridView() != null) {
                            // try refresh() if present
                            try {
                                java.lang.reflect.Method refresh = getGridView().getClass().getMethod("refresh");
                                refresh.invoke(getGridView());
                            } catch (NoSuchMethodException ns) {
                                getGridView().requestLayout();
                            }
                        } else {
                            wrapper.requestLayout();
                        }
                    } catch (Throwable ignored) {
                    }
                });
                Controller.UserInterfaceFunctions.markMyCollectionDirty();
                Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                return;
            }
        } catch (NoSuchMethodException ns) {
            // handler not present, fall back to direct removal
        } catch (Throwable t) {
            logger.debug("Error invoking MenuActionHandler.handleRemove", t);
        }

        // 2) Fallback: remove from owned collection directly
        try {
            Model.CardsLists.OwnedCardsCollection owned = null;
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
            if (owned != null && owned.getOwnedCollection() != null) {
                outer:
                for (Model.CardsLists.Box b : owned.getOwnedCollection()) {
                    if (b == null) continue;
                    if (b.getContent() != null) {
                        for (Model.CardsLists.CardsGroup g : b.getContent()) {
                            if (g == null || g.getCardList() == null) continue;
                            Iterator<Model.CardsLists.CardElement> it = g.getCardList().iterator();
                            while (it.hasNext()) {
                                Model.CardsLists.CardElement ce2 = it.next();
                                if (ce2 == ce) {
                                    it.remove();
                                    Controller.UserInterfaceFunctions.markMyCollectionDirty();
                                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                                    break outer;
                                }
                            }
                        }
                    }
                    if (b.getSubBoxes() != null) {
                        for (Model.CardsLists.Box sb : b.getSubBoxes()) {
                            if (sb == null || sb.getContent() == null) continue;
                            for (Model.CardsLists.CardsGroup g : sb.getContent()) {
                                if (g == null || g.getCardList() == null) continue;
                                Iterator<Model.CardsLists.CardElement> it = g.getCardList().iterator();
                                while (it.hasNext()) {
                                    Model.CardsLists.CardElement ce2 = it.next();
                                    if (ce2 == ce) {
                                        it.remove();
                                        Controller.UserInterfaceFunctions.markMyCollectionDirty();
                                        Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                                        break outer;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3) Try to save collection if a save helper exists
            /*try {
                java.lang.reflect.Method save = Controller.UserInterfaceFunctions.class.getMethod("saveCollectionFile");
                if (save != null) {
                    try {
                        save.invoke(null);
                    } catch (Throwable ignored) {
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }*/

            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();

        } catch (Throwable t) {
            logger.debug("Error removing card element directly", t);
        } finally {
            // 4) Refresh UI on FX thread
            Platform.runLater(() -> {
                try {
                    if (getGridView() != null) {
                        try {
                            java.lang.reflect.Method refresh = getGridView().getClass().getMethod("refresh");
                            refresh.invoke(getGridView());
                        } catch (NoSuchMethodException ns) {
                            getGridView().requestLayout();
                        }
                    } else {
                        wrapper.requestLayout();
                    }
                } catch (Throwable ignored) {
                }
            });
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        }
    }

    /**
     * Returns the set of cards to act on for the current context-menu action.
     * If MIDDLE-pane multi-selection is active and includes this cell's card,
     * returns all matching CardElements from the OwnedCardsCollection.
     * Otherwise returns a single-element list containing only getItem().
     */
    List<CardElement> getEffectiveMiddleElements() {
        CardElement currentItem = getItem();
        if (currentItem == null) return Collections.emptyList();
        boolean isMultiSelect =
                "MIDDLE".equals(Controller.SelectionManager.getActivePart())
                        && Controller.SelectionManager.getSelectedMiddleElements().size() > 1
                        && Controller.SelectionManager.getSelectedMiddleElements().contains(currentItem);
        if (isMultiSelect) {
            return new ArrayList<>(Controller.SelectionManager.getSelectedMiddleElements());
        }
        return Collections.singletonList(currentItem);
    }

    boolean isMiddleMultiSelectActive() {
        CardElement currentItem = getItem();
        return currentItem != null
                && "MIDDLE".equals(Controller.SelectionManager.getActivePart())
                && Controller.SelectionManager.getSelectedMiddleElements().size() > 1
                && Controller.SelectionManager.getSelectedMiddleElements().contains(currentItem);
    }

    void executeCopyAction() {
        CardElement currentItem = getItem();
        if (currentItem == null || currentItem.getCard() == null) return;
        if (isMiddleMultiSelectActive()) {
            java.util.List<Model.CardsLists.Card> cardsToCopy = new java.util.ArrayList<>();
            for (CardElement element : Controller.SelectionManager.getSelectedMiddleElements()) {
                if (element.getCard() != null) cardsToCopy.add(element.getCard());
            }
            Controller.CardClipboard.copyCards(cardsToCopy);
        } else if ("RIGHT".equals(Controller.SelectionManager.getActivePart())
                && Controller.SelectionManager.getSelectedCards().size() > 1
                && Controller.SelectionManager.getSelectedCards().contains(currentItem.getCard())) {
            Controller.CardClipboard.copyCards(
                    new java.util.ArrayList<>(Controller.SelectionManager.getSelectedCards()));
        } else {
            Controller.CardClipboard.copyCards(
                    Collections.singletonList(currentItem.getCard()));
        }
    }

    void executeCutFromOwnedCollectionAction() {
        executeCopyAction();
        CardElement currentItem = getItem();
        if (currentItem == null) return;
        List<CardElement> elementsToRemove = getEffectiveMiddleElements();
        if (elementsToRemove.size() > 1) {
            Controller.MenuActionHandler.handleBulkRemoveFromOwnedCollection(
                    new ArrayList<>(elementsToRemove));
        } else {
            removeCardElement(currentItem);
            Controller.UserInterfaceFunctions.refreshOwnedCollectionView();
        }
    }

    void executeCutFromDecksAction() {
        executeCopyAction();
        CardElement currentItem = getItem();
        if (currentItem == null) return;
        if (isMiddleMultiSelectActive()) {
            Controller.MenuActionHandler.handleBulkRemoveFromDecksAndCollections(
                    Controller.SelectionManager.getSelectedCards());
        } else {
            removeCardElementFromDecksList(currentItem);
        }
    }

    @Override
    protected void updateItem(CardElement cardElement, boolean empty) {
        super.updateItem(cardElement, empty);

        // Clear previous state for empty cells
        if (empty || cardElement == null) {
            cardImageView.setImage(null);
            cardImageView.setEffect(null);
            wrapper.setEffect(null);
            wrapper.setStyle("-fx-background-color: transparent;");
            currentGlowPriority = 0;
            currentTooltips = new java.util.ArrayList<>();
            setGraphic(wrapper);
            return;
        }

        // --- Image loading (unchanged logic) ---
        String imageKey = outer.safeImageKey(cardElement);
        String cachedFullPath = imageKey == null ? null : outer.imagePathCache.get(imageKey);
        if (cachedFullPath != null) {
            Image cached = LruImageCache.getImage(cachedFullPath);
            if (cached != null) {
                cardImageView.setImage(cached);
            } else {
                cardImageView.setImage(outer.getPlaceholderImage());
                Future<?> f = outer.loadImageWithResolvedPathAsync(cardElement, cardImageView, cachedFullPath);
                if (f != null) outer.outstandingLoads.put(cardImageView, f);
            }
        } else {
            cardImageView.setImage(outer.getPlaceholderImage());
            outer.resolveImagePathAsync(imageKey, resolvedPath -> {
                if (resolvedPath == null) return;
                Image cached = LruImageCache.getImage(resolvedPath);
                if (cached != null) {
                    Platform.runLater(() -> {
                        Object expected = cardImageView.getProperties().get("expectedImagePath");
                        if (Objects.equals(expected, resolvedPath) || expected == null) {
                            cardImageView.setImage(cached);
                            cardImageView.getProperties().remove("expectedImagePath");
                        }
                    });
                } else {
                    cardImageView.getProperties().put("expectedImagePath", resolvedPath);
                    Future<?> f = outer.loadImageWithResolvedPathAsync(cardElement, cardImageView, resolvedPath);
                    if (f != null) outer.outstandingLoads.put(cardImageView, f);
                }
            });
        }

        // ── Tooltip list: always computed, regardless of marking toggle ───────────────
        // Each entry: [message, cssColor]
        java.util.List<String[]> tooltips = new java.util.ArrayList<>();
        int glowPriority = 0;
        try {
            Card card = cardElement.getCard();
            boolean myCollTab = outer.isMyCollectionTabSelected();

            // ── My Collection completeness warnings (always shown in tooltip) ─────────
            // Glow is only applied when outer.incompleteMarkingEnabled; tooltip always shows.
            if (myCollTab && card != null
                    && (card.getPrintCode() == null || card.getPrintCode().isBlank())) {
                tooltips.add(new String[]{
                        "This card has no print code — the edition is unknown.", "#ff3333"});
                if (outer.incompleteMarkingEnabled) glowPriority = 3;
            }
            if (myCollTab
                    && (cardElement.getCondition() == null || cardElement.getRarity() == null)) {
                tooltips.add(new String[]{
                        "This card is missing its condition or rarity.", "#EB9E34"});
                if (outer.incompleteMarkingEnabled && glowPriority < 2) glowPriority = 2;
            }

            // ── White-glow conditions (archetype / artwork missing, needs-sort) ─────────
            // Always evaluated so their tooltip entries are always collected.
            // glowPriority is only elevated to 1 when it is still 0 (higher-priority
            // My Collection glows have already won and keep their effect).
            {
                Object ud = null;
                try {
                    if (this.getGridView() != null) ud = this.getGridView().getUserData();
                } catch (Exception ignored) {
                }

                Set<String> missingSet = null;
                Set<String> missingArtworkSet = null;
                String elementNameFromUD = null;

                if (ud instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) ud;

                    Object ms = map.get("missingSet");
                    if (ms instanceof Set) {
                        @SuppressWarnings("unchecked") Set<String> s = (Set<String>) ms;
                        missingSet = s;
                    } else if (ms instanceof Collection) {
                        Set<String> s = new HashSet<>();
                        for (Object o : (Collection<?>) ms) if (o != null) s.add(o.toString());
                        missingSet = s;
                    }

                    Object mas = map.get("missingArtworkSet");
                    if (mas instanceof Set) {
                        @SuppressWarnings("unchecked") Set<String> s = (Set<String>) mas;
                        missingArtworkSet = s;
                    }

                    Object en = map.get("elementName");
                    if (en instanceof String) elementNameFromUD = (String) en;

                } else if (ud instanceof Set) {
                    @SuppressWarnings("unchecked") Set<String> s = (Set<String>) ud;
                    missingSet = s;
                } else if (ud instanceof String) {
                    elementNameFromUD = (String) ud;
                }

                String konamiId = card == null ? null : card.getKonamiId();
                String passCode = card == null ? null : card.getPassCode();

                // 1a — archetype / explicit missing set
                if (missingSet != null && !missingSet.isEmpty()) {
                    boolean missing = (konamiId != null && missingSet.contains(konamiId))
                            || (passCode != null && missingSet.contains(passCode))
                            || CardGroupRegistry.LEGACY_GLOBAL_MISSING_SET.contains(konamiId)
                            || CardGroupRegistry.LEGACY_GLOBAL_MISSING_SET.contains(passCode);
                    if (missing) {
                        if (outer.isInArchetypeGroup()) {
                            // Archetype missing-card marking: active on any tab.
                            if (glowPriority == 0) glowPriority = 1;
                            tooltips.add(new String[]{
                                    "This card is missing in the collection.",
                                    "#EB9E34"});
                        } else if (myCollTab) {
                            // Non-archetype needs-sorting: My Collection tab only.
                            if (glowPriority == 0) glowPriority = 1;
                            tooltips.add(new String[]{
                                    CardHoverPopup.NEEDS_SORTING_WARNING,
                                    "#EB9E34"});
                        }
                    }
                }

                // 1b — missing artwork set (check both konamiId and passCode)
                if (missingArtworkSet != null && !missingArtworkSet.isEmpty()) {
                    boolean artMissing = (konamiId != null && missingArtworkSet.contains(konamiId))
                            || (passCode != null && missingArtworkSet.contains(passCode));
                    if (artMissing) {
                        if (glowPriority == 0) glowPriority = 1;
                        tooltips.add(new String[]{
                                "Not all artworks of this card are present in this collection.",
                                "#EB9E34"});
                    }
                }

                // 1c — sorting check: only on My Collection tab.
                //      Cards in D&C get DOWNGRADE_WARNING via 1d below, not here.
                if (elementNameFromUD != null
                        && !elementNameFromUD.trim().isEmpty() && outer.isMyCollectionTabSelected()) {
                    try {
                        boolean genuinelyNeeded = false;
                        try {
                            genuinelyNeeded = Controller.CardQualityService
                                    .computeCardNeedsSorting(card, elementNameFromUD);
                        } catch (Throwable ignored) {
                        }

                        boolean upgradeNeeded = false;
                        if (!genuinelyNeeded) {
                            try {
                                upgradeNeeded = Controller.CardQualityService
                                        .computeCardNeedsSortingWithUpgrade(cardElement, elementNameFromUD);
                            } catch (Throwable ignored) {
                            }
                        }

                        if (genuinelyNeeded) {
                            if (glowPriority == 0) glowPriority = 1;
                            tooltips.add(new String[]{
                                    CardHoverPopup.NEEDS_SORTING_WARNING,
                                    "#EB9E34"});
                        } else if (upgradeNeeded) {
                            if (glowPriority == 0) glowPriority = 1;
                            tooltips.add(new String[]{
                                    CardHoverPopup.UPGRADE_CANDIDATE_WARNING,
                                    "#EB9E34"});
                        } else {
                            // Reason 4: this card IS in a D&C-named sorting category and
                            // a better outside copy exists in the owned collection.
                            // Guard: only fire when the element name is a recognised D&C
                            // name, to avoid false positives on type groups such as
                            // "Fusion Monsters".  computeCardNeedsSortingWithUpgrade already
                            // returns false for D&C-named groups (its internal guard), so
                            // upgradeNeeded is false here in both cases — we use
                            // isDeckOrCollectionName to distinguish them.
                            try {
                                boolean isDeckName = Controller.CardQualityService
                                        .isDeckOrCollectionName(elementNameFromUD);
                                if (isDeckName) {
                                    boolean isDegraded = Controller.CardQualityService
                                            .isDegradedCopyInDeckOrCollection(
                                                    cardElement, elementNameFromUD);
                                    if (isDegraded) {
                                        if (glowPriority == 0) {
                                            glowPriority = 1;
                                        }
                                        tooltips.add(new String[]{
                                                CardHoverPopup.DOWNGRADE_WARNING,
                                                "#EB9E34"});
                                    }
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }

            }
        } catch (Exception e) {
            logger.debug("Failed to compute glow in CardGridCell.updateItem", e);
        }

        // ── Apply glow effect to the wrapper StackPane ────────────────────────────────
        if (glowPriority >= 3) {
            // Red — no printCode
            DropShadow inner = new DropShadow();
            inner.setColor(javafx.scene.paint.Color.web("#ff3333", 1.0));
            inner.setOffsetX(0);
            inner.setOffsetY(0);
            inner.setRadius(4);
            inner.setSpread(0.9);
            DropShadow outer = new DropShadow();
            outer.setColor(javafx.scene.paint.Color.web("#ff3333", 0.35));
            outer.setOffsetX(0);
            outer.setOffsetY(0);
            outer.setRadius(14);
            outer.setSpread(0.12);
            outer.setInput(inner);
            wrapper.setEffect(outer);
        } else if (glowPriority >= 1) {
            // White — covers priority 1 (archetype/artwork missing, needs-sort)
            //         AND priority 2 (condition/rarity missing, marking enabled).
            DropShadow inner = new DropShadow();
            inner.setColor(javafx.scene.paint.Color.web("#ffffff", 1.0));
            inner.setOffsetX(0);
            inner.setOffsetY(0);
            inner.setRadius(4);
            inner.setSpread(0.9);
            DropShadow outer = new DropShadow();
            outer.setColor(javafx.scene.paint.Color.web("#ffffff", 0.22));
            outer.setOffsetX(0);
            outer.setOffsetY(0);
            outer.setRadius(14);
            outer.setSpread(0.12);
            outer.setInput(inner);
            wrapper.setEffect(outer);
        } else {
            wrapper.setEffect(null);
        }

        // --- Apply grayscale for owned cards in OuicheList tab (on the imageView only) ---
        // This is intentionally applied to cardImageView, not wrapper, so it is independent
        // of the glow effect above and does not affect other tabs.
        boolean ouicheSelected = outer.isOuicheListTabSelected();
        boolean owned = cardElement.getOwned() != null && cardElement.getOwned();
        if (ouicheSelected && owned) {
            javafx.scene.effect.ColorAdjust grayscale = new javafx.scene.effect.ColorAdjust();
            grayscale.setSaturation(-0.7);   // 70% desaturation
            grayscale.setBrightness(-0.5);   // darken by 50%
            cardImageView.setEffect(grayscale);
        } else {
            cardImageView.setEffect(null);
        }

        // --- Selection visual: accent border driven by SelectionManager ---
        boolean isSelectedInMiddlePane =
                "MIDDLE".equals(Controller.SelectionManager.getActivePart())
                        && Controller.SelectionManager.getSelectedMiddleElements().contains(cardElement);

        if (isSelectedInMiddlePane) {
            wrapper.setStyle(
                    "-fx-background-color: transparent; " +
                            "-fx-border-color: #cdfc04; " +
                            "-fx-border-width: 2; " +
                            "-fx-border-radius: 6; " +
                            "-fx-padding: 3;");
        } else {
            wrapper.setStyle("-fx-background-color: transparent;");
        }

        // Store for the hover handler
        currentGlowPriority = glowPriority;
        currentTooltips = tooltips;

        // Finalize graphic
        setGraphic(wrapper);
    }

    /**
     * Returns the Deck or ThemeCollection in the DecksAndCollectionsList
     * that owns the given CardsGroup (matched by backing-list identity).
     * Returns null when the group belongs to MyCollection or cannot be found.
     */
    Object findDacOwnerForCardsGroup(CardsGroup group) {
        if (group == null) return null;
        List<CardElement> list = group.getCardList();
        if (list == null) return null;
        Model.CardsLists.DecksAndCollectionsList dac =
                Controller.UserInterfaceFunctions.getDecksList();
        if (dac == null) return null;
        if (dac.getCollections() != null) {
            for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                if (tc == null) continue;
                if (list == tc.getCardsList() || list == tc.getExceptionsToNotAdd()) return tc;
                if (tc.getLinkedDecks() != null) {
                    for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                        if (unit == null) continue;
                        for (Model.CardsLists.Deck deck : unit) {
                            if (deck == null) continue;
                            if (list == deck.getMainDeck()
                                    || list == deck.getExtraDeck()
                                    || list == deck.getSideDeck()) return deck;
                        }
                    }
                }
            }
        }
        if (dac.getDecks() != null) {
            for (Model.CardsLists.Deck deck : dac.getDecks()) {
                if (deck == null) continue;
                if (list == deck.getMainDeck()
                        || list == deck.getExtraDeck()
                        || list == deck.getSideDeck()) return deck;
            }
        }
        return null;
    }

    String findCurrentLocationPath() {
        try {
            TreeItem<String> item = outer.getTreeItem();
            if (item == null) return null;
            String groupName = item.getValue();
            TreeItem<String> parent = item.getParent();
            if (parent == null) return groupName;
            Object parentData = (parent instanceof DataTreeItem)
                    ? ((DataTreeItem<?>) parent).getData() : null;

            // Direct child of a ThemeCollection (e.g. "Cards" or "Cards not to add")
            if (parentData instanceof ThemeCollection) {
                String collName = outer.sanitizeDisplayName(((ThemeCollection) parentData).getName());
                if ("Cards not to add".equals(groupName)) return collName + " / Exclusion List";
                return collName;
            }

            // Child of a Deck (Main/Extra/Side Deck group)
            if (parentData instanceof Deck) {
                String deckName = outer.sanitizeDisplayName(((Deck) parentData).getName());
                TreeItem<String> sectionItem = parent.getParent(); // DECKS_SECTION
                if (sectionItem != null) {
                    TreeItem<String> collOrRoot = sectionItem.getParent();
                    if (collOrRoot != null) {
                        Object collData = (collOrRoot instanceof DataTreeItem)
                                ? ((DataTreeItem<?>) collOrRoot).getData() : null;
                        if (collData instanceof ThemeCollection) {
                            String collName = outer.sanitizeDisplayName(((ThemeCollection) collData).getName());
                            return collName + " / " + deckName + " / " + groupName;
                        }
                    }
                }
                return deckName + " / " + groupName;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    void removeCardElementFromDecksList(Model.CardsLists.CardElement cardElement) {
        if (cardElement == null) return;
        try {
            if (getGridView() == null || getGridView().getItems() == null) return;
            @SuppressWarnings("unchecked")
            javafx.collections.ObservableList<CardElement> items =
                    (javafx.collections.ObservableList<CardElement>) getGridView().getItems();
            java.util.Iterator<Model.CardsLists.CardElement> it = items.iterator();
            while (it.hasNext()) {
                if (it.next() == cardElement) {
                    it.remove();
                    // Mark only the owner of this specific list dirty
                    Object owner = null;
                    for (Map.Entry<CardsGroup, javafx.collections.ObservableList<CardElement>> entry
                            : CardGroupRegistry.GROUP_OBSERVABLE_LISTS.entrySet()) {
                        if (entry.getValue() == items) {
                            owner = findDacOwnerForCardsGroup(entry.getKey());
                            break;
                        }
                    }
                    if (owner != null) {
                        Controller.UserInterfaceFunctions.markDirty(owner);
                        if (owner instanceof Model.CardsLists.ThemeCollection) {
                            Controller.UserInterfaceFunctions.setPendingDecksFullRebuild();
                        }
                    } else {
                        Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
                    }
                    Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
                    return;
                }
            }
        } catch (Throwable t) {
            logger.debug("removeCardElementFromDecksList failed", t);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
// Target-list resolution helpers
// ─────────────────────────────────────────────────────────────────────────────

    /**
     * Resolves a display-path (built by buildXxxDestinationMenuItems) to the
     * actual backing List<CardElement> inside the DecksAndCollectionsList.
     * <p>
     * Path patterns (names are already sanitized — no = or -):
     * "CollName"                              → tc.getCardsList()
     * "CollName / Exclusion List"             → tc.getExceptionsToNotAdd()
     * "DeckName / Main|Extra|Side Deck"       → standalone deck list
     * "CollName / DeckName / Main|Extra|Side" → linked deck list
     */
    public List<Model.CardsLists.CardElement> resolveDecksTargetList(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        Model.CardsLists.DecksAndCollectionsList dac =
                Controller.UserInterfaceFunctions.getDecksList();
        if (dac == null) return null;

        String[] parts = path.split("\\s*/\\s*");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        if (parts.length == 0) return null;

        String lastLower = parts[parts.length - 1].toLowerCase(java.util.Locale.ROOT);
        boolean isMain = lastLower.equals("main deck");
        boolean isExtra = lastLower.equals("extra deck");
        boolean isSide = lastLower.equals("side deck");
        boolean isExcl = lastLower.equals("exclusion list")
                || lastLower.equals("cards not to add");

        // "CollName" → collection cards list
        if (parts.length == 1 && !isMain && !isExtra && !isSide && !isExcl) {
            Model.CardsLists.ThemeCollection tc = findCollByDisplayName(parts[0], dac);
            if (tc != null) {
                if (tc.getCardsList() == null) tc.setCardsList(new ArrayList<>());
                return tc.getCardsList();
            }
            return null;
        }

        // "CollName / Exclusion List"
        if (parts.length == 2 && isExcl) {
            Model.CardsLists.ThemeCollection tc = findCollByDisplayName(parts[0], dac);
            if (tc != null) {
                if (tc.getExceptionsToNotAdd() == null)
                    tc.setExceptionsToNotAdd(new ArrayList<>());
                return tc.getExceptionsToNotAdd();
            }
            return null;
        }

        // "DeckName / Main|Extra|Side Deck"  (standalone deck)
        if (parts.length == 2 && (isMain || isExtra || isSide)) {
            Model.CardsLists.Deck d = findStandaloneDeckByDisplayName(parts[0], dac);
            if (d != null) {
                if (isMain) return d.getMainDeck();
                if (isExtra) return d.getExtraDeck();
                return d.getSideDeck();
            }
            return null;
        }

        // "CollName / DeckName / Main|Extra|Side Deck"  (linked deck)
        if (parts.length == 3 && (isMain || isExtra || isSide)) {
            Model.CardsLists.ThemeCollection tc = findCollByDisplayName(parts[0], dac);
            if (tc != null) {
                Model.CardsLists.Deck d = findLinkedDeckByDisplayName(parts[1], tc);
                if (d != null) {
                    if (isMain) return d.getMainDeck();
                    if (isExtra) return d.getExtraDeck();
                    return d.getSideDeck();
                }
            }
            return null;
        }
        return null;
    }

    private Model.CardsLists.ThemeCollection findCollByDisplayName(
            String displayName, Model.CardsLists.DecksAndCollectionsList dac) {
        if (dac == null || dac.getCollections() == null) return null;
        for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
            if (tc == null) continue;
            if (outer.sanitizeDisplayName(tc.getName()).equals(displayName)) return tc;
        }
        return null;
    }

    private Model.CardsLists.Deck findStandaloneDeckByDisplayName(
            String displayName, Model.CardsLists.DecksAndCollectionsList dac) {
        if (dac == null || dac.getDecks() == null) return null;
        for (Model.CardsLists.Deck d : dac.getDecks()) {
            if (d == null) continue;
            if (outer.sanitizeDisplayName(d.getName()).equals(displayName)) return d;
        }
        return null;
    }

    private Model.CardsLists.Deck findLinkedDeckByDisplayName(
            String displayName, Model.CardsLists.ThemeCollection tc) {
        if (tc == null || tc.getLinkedDecks() == null) return null;
        for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
            if (unit == null) continue;
            for (Model.CardsLists.Deck d : unit) {
                if (d == null) continue;
                if (outer.sanitizeDisplayName(d.getName()).equals(displayName)) return d;
            }
        }
        return null;
    }

// ─────────────────────────────────────────────────────────────────────────────
// Menu item factories
// ─────────────────────────────────────────────────────────────────────────────

    /**
     * MOVE: removes the current card element from its source GridView,
     * then adds it to the resolved target list.
     */
    private void addMoveDestItem(List<MenuItem> items, String path, String excludePath) {
        if (path == null || path.equals(excludePath)) return;
        MenuItem mi = new MenuItem();
        Label lbl = new Label(path);
        lbl.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
        HBox g = new HBox(lbl);
        g.setAlignment(Pos.CENTER_LEFT);
        g.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(g);
        mi.setText("");
        mi.setOnAction(e -> {
            // Collect elements to move — full selection when multi-select is active
            List<Model.CardsLists.CardElement> elementsToMove;
            if (isMiddleMultiSelectActive()) {
                elementsToMove = new ArrayList<>(
                        Controller.SelectionManager.getSelectedMiddleElements());
            } else {
                Model.CardsLists.CardElement single = getItem();
                if (single == null) return;
                elementsToMove = Collections.singletonList(single);
            }

            // Remove each element from its source observable list.
            // Walk CardGroupRegistry.GROUP_OBSERVABLE_LISTS so we can track the exact owner per element.
            java.util.Set<Object> sourceOwners = new java.util.LinkedHashSet<>();
            for (Model.CardsLists.CardElement ce : elementsToMove) {
                boolean removed = false;
                for (Map.Entry<CardsGroup, javafx.collections.ObservableList<CardElement>> entry
                        : CardGroupRegistry.GROUP_OBSERVABLE_LISTS.entrySet()) {
                    if (entry.getValue().remove(ce)) {
                        Object owner = findDacOwnerForCardsGroup(entry.getKey());
                        if (owner != null) sourceOwners.add(owner);
                        removed = true;
                        break;
                    }
                }
                // Fallback for single-select: try the cell's own GridView
                if (!removed && elementsToMove.size() == 1
                        && getGridView() != null && getGridView().getItems() != null) {
                    getGridView().getItems().remove(ce);
                }
            }

            // Mark source owners dirty
            if (!sourceOwners.isEmpty()) {
                for (Object owner : sourceOwners)
                    Controller.UserInterfaceFunctions.markDirty(owner);
            } else {
                Object fallback = resolveDecksTargetOwner(excludePath != null ? excludePath : "");
                if (fallback != null) Controller.UserInterfaceFunctions.markDirty(fallback);
                else Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
            }

            // Add all elements to the target list
            List<Model.CardsLists.CardElement> targetList = resolveDecksTargetList(path);
            if (targetList != null) {
                targetList.addAll(elementsToMove);
                Controller.MenuActionHandler.setLastDecksAddedTarget(path);
                Object destOwner = resolveDecksTargetOwner(path);
                if (destOwner != null) Controller.UserInterfaceFunctions.markDirty(destOwner);
                else Controller.UserInterfaceFunctions.markAllDecksAndCollectionsDirty();
            } else {
                logger.warn("addMoveDestItem: could not resolve '{}'", path);
            }
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            // If any source or the destination belongs to a ThemeCollection's own list,
            // missing-sets are stale → force a full rebuild.
            boolean needsFullRebuild =
                    sourceOwners.stream().anyMatch(
                            o -> o instanceof Model.CardsLists.ThemeCollection);
            if (!needsFullRebuild) {
                Object destOwner = resolveDecksTargetOwner(path);
                needsFullRebuild = destOwner instanceof Model.CardsLists.ThemeCollection;
            }
            if (needsFullRebuild) {
                Controller.UserInterfaceFunctions.triggerDecksStructureRefresh();
            } else {
                Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
            }
        });
        items.add(mi);
    }

    /**
     * ADD: creates a new CardElement from the current item's card and adds it
     * to the resolved target list.  Routing through the existing MenuActionHandler
     * methods ensures lastDecksAddedTarget is set and the view scrolls correctly.
     */
    private void addAddDestItem(List<MenuItem> items, String path) {
        if (path == null) return;
        MenuItem mi = new MenuItem();
        Label lbl = new Label(path);
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox g = new HBox(lbl);
        g.setAlignment(Pos.CENTER_LEFT);
        g.setPadding(new Insets(2, 6, 2, 6));
        mi.setGraphic(g);
        mi.setText("");
        mi.setOnAction(e -> {
            CardElement currentItem = getItem();
            if (currentItem == null || currentItem.getCard() == null) return;

            java.util.Collection<Model.CardsLists.Card> cardsToAdd;
            if (isMiddleMultiSelectActive()) {
                cardsToAdd = Controller.SelectionManager.getSelectedCards();
            } else {
                cardsToAdd = Collections.singletonList(currentItem.getCard());
            }

            String[] parts = path.split("\\s*/\\s*");
            String lastPart = parts[parts.length - 1].trim().toLowerCase(java.util.Locale.ROOT);
            boolean isExclusion = lastPart.equals("exclusion list")
                    || lastPart.equals("cards not to add");

            // Check emptiness BEFORE the add so we know whether the tree node exists.
            boolean targetWasEmpty = isDecksTargetEmpty(parts, isExclusion);

            if (isExclusion && parts.length >= 2) {
                Controller.MenuActionHandler.handleBulkAddToExclusionList(
                        cardsToAdd, parts[0].trim());
            } else if (parts.length == 1) {
                Controller.MenuActionHandler.handleBulkAddToCollectionCards(
                        cardsToAdd, parts[0].trim());
            } else {
                Controller.MenuActionHandler.handleBulkAddToDeck(cardsToAdd, path);
            }

            // A collection's own list changed (parts.length == 1 means cardsList,
            // isExclusion means exceptionsToNotAdd) — archetype missing-sets are stale.
            // A previously empty section also needs a structural rebuild for its
            // DataTreeItem to appear. Either way: full rebuild.
            boolean affectsCollectionList = (parts.length == 1 || isExclusion);
            if (affectsCollectionList || targetWasEmpty) {
                Controller.UserInterfaceFunctions.triggerDecksStructureRefresh();
            } else {
                // Deck section — soft refresh is enough.
                Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
            }
        });
        items.add(mi);
    }

    /**
     * Returns true when the current selection is compatible with the
     * named section for a MOVE operation. The Side Deck always returns true.
     */
    boolean moveSectionAllowed(String sectionName) {
        if (Utils.DeckCompatibility.isSideDeckSection(sectionName)) return true;
        // Use only getItem() — the CardElement in the cell being right-clicked.
        // SelectionManager holds stale state from previous interactions and must
        // not be used here; it would show incompatible sections for other cards.
        Model.CardsLists.CardElement item = getItem();
        if (item == null || item.getCard() == null) return true;
        return Utils.DeckCompatibility.isCompatibleWith(item.getCard(), sectionName);
    }

    /**
     * Returns true when the current selection is compatible with the
     * named section for an ADD operation. The Side Deck always returns true.
     */
    boolean addSectionAllowed(String sectionName) {
        return moveSectionAllowed(sectionName); // same logic
    }

    /**
     * Returns true when the card list that {@code path} resolves to is currently
     * empty (meaning the corresponding DataTreeItem was never inserted in the tree
     * and a structural refresh will be needed after the add).
     */
    boolean isDecksTargetEmpty(String[] parts, boolean isExclusion) {
        try {
            Model.CardsLists.DecksAndCollectionsList dac =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (dac == null) return false;

            String collName = parts[0].trim();

            // ── Collection-level targets ─────────────────────────────────────
            if (dac.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                    if (tc == null) continue;
                    String tcName = tc.getName() == null ? "" : tc.getName().trim();
                    if (!collName.equals(tcName)) continue;

                    if (isExclusion) {
                        java.util.List<Model.CardsLists.CardElement> exc =
                                tc.getExceptionsToNotAdd();
                        return exc == null || exc.isEmpty();
                    }
                    if (parts.length == 1) {
                        java.util.List<Model.CardsLists.CardElement> cl = tc.getCardsList();
                        return cl == null || cl.isEmpty();
                    }
                    // "CollName / DeckName / Main|Extra|Side Deck"
                    if (parts.length >= 3 && tc.getLinkedDecks() != null) {
                        String deckName = parts[1].trim();
                        String section = parts[parts.length - 1].trim().toLowerCase(
                                java.util.Locale.ROOT);
                        for (java.util.List<Model.CardsLists.Deck> unit :
                                tc.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Model.CardsLists.Deck d : unit) {
                                if (d == null) continue;
                                String dName = d.getName() == null ? "" : d.getName().trim();
                                if (!deckName.equals(dName)) continue;
                                java.util.List<Model.CardsLists.CardElement> lst =
                                        section.contains("extra") ? d.getExtraDeck()
                                                : section.contains("side") ? d.getSideDeck()
                                                : d.getMainDeck();
                                return lst == null || lst.isEmpty();
                            }
                        }
                    }
                }
            }

            // ── Standalone deck targets ("DeckName / Main|Extra|Side Deck") ──
            if (parts.length >= 2 && dac.getDecks() != null) {
                String section = parts[parts.length - 1].trim().toLowerCase(
                        java.util.Locale.ROOT);
                for (Model.CardsLists.Deck d : dac.getDecks()) {
                    if (d == null) continue;
                    String dName = d.getName() == null ? "" : d.getName().trim();
                    if (!collName.equals(dName)) continue;
                    java.util.List<Model.CardsLists.CardElement> lst =
                            section.contains("extra") ? d.getExtraDeck()
                                    : section.contains("side") ? d.getSideDeck()
                                    : d.getMainDeck();
                    return lst == null || lst.isEmpty();
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

// ─────────────────────────────────────────────────────────────────────────────
// Menu builders
// ─────────────────────────────────────────────────────────────────────────────

    /**
     * For the MOVE context (non-archetype cards already in D&C).
     * Excludes the card's current location so it cannot be "moved" to itself.
     */
    private List<MenuItem> buildMoveDestinationMenuItems(String excludePath) {
        List<MenuItem> items = new ArrayList<>();
        try {
            Model.CardsLists.DecksAndCollectionsList dac =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (dac == null) {
                try {
                    Controller.UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
                } catch (Exception ignored) {
                }
                dac = Controller.UserInterfaceFunctions.getDecksList();
            }
            if (dac == null) return items;

            if (dac.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                    if (tc == null) continue;
                    String coll = outer.sanitizeDisplayName(tc.getName());
                    addMoveDestItem(items, coll, excludePath);
                    if (tc.getLinkedDecks() != null) {
                        for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Model.CardsLists.Deck deck : unit) {
                                if (deck == null) continue;
                                String base = coll + " / " + outer.sanitizeDisplayName(deck.getName());
                                if (moveSectionAllowed("Main Deck"))
                                    addMoveDestItem(items, base + " / Main Deck", excludePath);
                                if (moveSectionAllowed("Extra Deck"))
                                    addMoveDestItem(items, base + " / Extra Deck", excludePath);
                                addMoveDestItem(items, base + " / Side Deck", excludePath);
                            }
                        }
                    }
                    addMoveDestItem(items, coll + " / Exclusion List", excludePath);
                }
            }
            if (dac.getDecks() != null) {
                for (Model.CardsLists.Deck deck : dac.getDecks()) {
                    if (deck == null) continue;
                    String d = outer.sanitizeDisplayName(deck.getName());
                    if (moveSectionAllowed("Main Deck"))
                        addMoveDestItem(items, d + " / Main Deck", excludePath);
                    if (moveSectionAllowed("Extra Deck"))
                        addMoveDestItem(items, d + " / Extra Deck", excludePath);
                    addMoveDestItem(items, d + " / Side Deck", excludePath);
                }
            }
        } catch (Exception ex) {
            logger.debug("buildMoveDestinationMenuItems failed", ex);
        }
        return items;
    }

    /**
     * For the ADD context (archetype cards — read-only source, just insert a copy).
     * No exclusion path needed.
     */
    private List<MenuItem> buildAddDestinationMenuItems() {
        List<MenuItem> items = new ArrayList<>();
        try {
            Model.CardsLists.DecksAndCollectionsList dac =
                    Controller.UserInterfaceFunctions.getDecksList();
            if (dac == null) {
                try {
                    Controller.UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
                } catch (Exception ignored) {
                }
                dac = Controller.UserInterfaceFunctions.getDecksList();
            }
            if (dac == null) return items;

            if (dac.getCollections() != null) {
                for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                    if (tc == null) continue;
                    String coll = outer.sanitizeDisplayName(tc.getName());
                    addAddDestItem(items, coll);
                    if (tc.getLinkedDecks() != null) {
                        for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Model.CardsLists.Deck deck : unit) {
                                if (deck == null) continue;
                                String base = coll + " / " + outer.sanitizeDisplayName(deck.getName());
                                if (addSectionAllowed("Main Deck"))
                                    addAddDestItem(items, base + " / Main Deck");
                                if (addSectionAllowed("Extra Deck"))
                                    addAddDestItem(items, base + " / Extra Deck");
                                addAddDestItem(items, base + " / Side Deck");
                            }
                        }
                    }
                    addAddDestItem(items, coll + " / Exclusion List");
                }
            }
            if (dac.getDecks() != null) {
                for (Model.CardsLists.Deck deck : dac.getDecks()) {
                    if (deck == null) continue;
                    String d = outer.sanitizeDisplayName(deck.getName());
                    if (addSectionAllowed("Main Deck"))
                        addAddDestItem(items, d + " / Main Deck");
                    if (addSectionAllowed("Extra Deck"))
                        addAddDestItem(items, d + " / Extra Deck");
                    addAddDestItem(items, d + " / Side Deck");
                }
            }
        } catch (Exception ex) {
            logger.debug("buildAddDestinationMenuItems failed", ex);
        }
        return items;
    }

    /**
     * Returns the Deck or ThemeCollection that owns the list at {@code path},
     * or null if the path cannot be resolved.
     */
    Object resolveDecksTargetOwner(String path) {
        if (path == null) return null;
        Model.CardsLists.DecksAndCollectionsList dac =
                Controller.UserInterfaceFunctions.getDecksList();
        if (dac == null) return null;

        String[] parts = path.split("\\s*/\\s*");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
        if (parts.length == 0) return null;

        String lastLower = parts[parts.length - 1].toLowerCase(java.util.Locale.ROOT);
        boolean isMain = lastLower.equals("main deck");
        boolean isExtra = lastLower.equals("extra deck");
        boolean isSide = lastLower.equals("side deck");
        boolean isExcl = lastLower.equals("exclusion list")
                || lastLower.equals("cards not to add");

        // "CollName" or "CollName / Exclusion List"
        if (parts.length == 1 || (parts.length == 2 && isExcl)) {
            return findCollByDisplayName(parts[0], dac);
        }
        // "DeckName / Main|Extra|Side Deck"
        if (parts.length == 2 && (isMain || isExtra || isSide)) {
            Model.CardsLists.Deck d = findStandaloneDeckByDisplayName(parts[0], dac);
            if (d != null) return d;
        }
        // "CollName / DeckName / Main|Extra|Side Deck"
        if (parts.length == 3 && (isMain || isExtra || isSide)) {
            Model.CardsLists.ThemeCollection tc = findCollByDisplayName(parts[0], dac);
            if (tc != null) {
                Model.CardsLists.Deck d = findLinkedDeckByDisplayName(parts[1], tc);
                if (d != null) return d;
            }
        }
        return null;
    }

    /**
     * Returns true when the current selection contains at least one card
     * that is compatible with {@code sectionName}.
     * The Side Deck always returns true.
     * Falls back to true when no card info is available (show all).
     * Must live inside CardGridCell so that getItem() returns CardElement.
     */
    private boolean deckSectionAllowedForSelection(String sectionName) {
        if (Utils.DeckCompatibility.isSideDeckSection(sectionName)) return true;
        java.util.List<Model.CardsLists.Card> cards = new java.util.ArrayList<>();
        java.util.Set<Model.CardsLists.CardElement> sel =
                Controller.SelectionManager.getSelectedMiddleElements();
        if (!sel.isEmpty()) {
            for (Model.CardsLists.CardElement ce : sel)
                if (ce != null && ce.getCard() != null) cards.add(ce.getCard());
        } else {
            Model.CardsLists.CardElement item = getItem(); // CardElement here ✓
            if (item != null && item.getCard() != null) cards.add(item.getCard());
        }
        if (cards.isEmpty()) return true;
        return Utils.DeckCompatibility.anyCompatibleWith(cards, sectionName);
    }
}