package View;

import Controller.MenuActionHandler;
import Controller.SelectionManager;
import Controller.UserInterfaceFunctions;
import Model.CardsLists.*;
import Utils.CardNameUtils;
import Utils.DeckCompatibility;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Factory for creating themed {@link ContextMenu} and {@link MenuItem} instances
 * shared across the right-pane card-view cells ({@link CardsListCell},
 * {@link CardsMosaicRowCell}).
 *
 * <p>All public methods are stateless and thread-safe. Methods that read the
 * live model ({@link #buildAllDecksDestinationItemsForCards},
 * {@link #buildMyCollectionDestinationItemsForCards}) must be called on the
 * JavaFX Application Thread because they touch observable lists.
 */
public final class ContextMenuItemFactory {

    private static final Logger logger = LoggerFactory.getLogger(ContextMenuItemFactory.class);

    private ContextMenuItemFactory() {
    }

    // ── ContextMenu factory ────────────────────────────────────────────────────

    /**
     * Creates a {@link ContextMenu} with the application's standard dark styling.
     */
    public static ContextMenu styledContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.setStyle(
                "-fx-background-color: #100317; " +
                        "-fx-background-radius: 6; " +
                        "-fx-border-color: #3a3a3a; " +
                        "-fx-border-radius: 6; " +
                        "-fx-border-width: 1;");
        return contextMenu;
    }

    // ── Menu / MenuItem helpers ────────────────────────────────────────────────

    /**
     * Creates a {@link Menu} whose header label is styled with the accent colour
     * ({@code #cdfc04}), suitable for use as a submenu header inside a dark
     * context menu.
     *
     * @param labelText text to display in the menu header
     * @return a new {@link Menu} with the styled graphic set
     */
    public static Menu makeMenuHeader(String labelText) {
        Menu menu = new Menu();
        Label label = new Label(labelText);
        label.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 13;");
        HBox graphic = new HBox(label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        menu.setGraphic(graphic);
        menu.setText("");
        return menu;
    }

    /**
     * Creates a disabled {@link MenuItem} with text {@code "Loading…"}, used as
     * a placeholder inside lazy-populated submenus.
     */
    public static MenuItem loadingPlaceholder() {
        MenuItem placeholder = new MenuItem("Loading...");
        placeholder.setDisable(true);
        return placeholder;
    }

    // ── Selection resolution ───────────────────────────────────────────────────

    /**
     * Resolves the effective set of cards to act on for a right-pane context-menu
     * action: returns the full multi-selection when the clicked card is part of an
     * active multi-select, or a singleton containing only {@code clickedCard}.
     *
     * @param clickedCard the card that was right-clicked (may be {@code null})
     * @return an unmodifiable collection of cards to act on, never {@code null}
     */
    public static Collection<Card> resolveEffectiveRightPaneCards(Card clickedCard) {
        if (clickedCard == null) {
            return Collections.emptyList();
        }
        boolean isMultiSelect =
                "RIGHT".equals(SelectionManager.getActivePart())
                        && SelectionManager.getSelectedCards().size() > 1
                        && SelectionManager.getSelectedCards().contains(clickedCard);
        return isMultiSelect
                ? SelectionManager.getSelectedCards()
                : Collections.singletonList(clickedCard);
    }

    // ── Deck & Collection destination lists ────────────────────────────────────

    /**
     * Builds a flat list of {@link MenuItem}s covering every destination in the
     * current {@link DecksAndCollectionsList}: each collection's card list and
     * exclusion list, and each deck's main / extra / side deck lists.
     *
     * <p>Main Deck and Extra Deck entries are omitted when not all cards in
     * {@code cards} are compatible with that section (per {@link DeckCompatibility}).
     * Side Deck entries are always included.
     *
     * @param cards the cards to add when an item is selected
     * @return list of styled menu items; empty when the DAC is unavailable
     */
    public static List<MenuItem> buildAllDecksDestinationItemsForCards(Collection<Card> cards) {
        List<MenuItem> menuItems = new ArrayList<>();
        if (cards == null || cards.isEmpty()) {
            return menuItems;
        }

        boolean allowMain = DeckCompatibility.allCompatibleWith(cards, "Main Deck");
        boolean allowExtra = DeckCompatibility.allCompatibleWith(cards, "Extra Deck");

        try {
            DecksAndCollectionsList decksAndCollections = UserInterfaceFunctions.getDecksList();
            if (decksAndCollections == null) {
                try {
                    UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
                } catch (Exception ignored) {
                }
                decksAndCollections = UserInterfaceFunctions.getDecksList();
            }
            if (decksAndCollections == null) {
                return menuItems;
            }

            final Collection<Card> finalCards = cards;

            if (decksAndCollections.getCollections() != null) {
                for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
                    if (themeCollection == null) {
                        continue;
                    }
                    String collectionName = CardNameUtils.sanitize(themeCollection.getName());
                    menuItems.add(makeDecksCollectionMenuItem(collectionName, finalCards));

                    if (themeCollection.getLinkedDecks() != null) {
                        for (List<Deck> unit : themeCollection.getLinkedDecks()) {
                            if (unit == null) {
                                continue;
                            }
                            for (Deck deck : unit) {
                                if (deck == null) {
                                    continue;
                                }
                                String basePath = collectionName + " / "
                                        + CardNameUtils.sanitize(deck.getName());
                                if (allowMain) {
                                    menuItems.add(makeDecksDestinationMenuItem(
                                            basePath + " / Main Deck", finalCards));
                                }
                                if (allowExtra) {
                                    menuItems.add(makeDecksDestinationMenuItem(
                                            basePath + " / Extra Deck", finalCards));
                                }
                                menuItems.add(makeDecksDestinationMenuItem(
                                        basePath + " / Side Deck", finalCards));
                            }
                        }
                    }
                    menuItems.add(makeDecksExclusionMenuItem(collectionName, finalCards));
                }
            }

            if (decksAndCollections.getDecks() != null) {
                for (Deck deck : decksAndCollections.getDecks()) {
                    if (deck == null) {
                        continue;
                    }
                    String deckName = CardNameUtils.sanitize(deck.getName());
                    if (allowMain) {
                        menuItems.add(makeDecksDestinationMenuItem(
                                deckName + " / Main Deck", finalCards));
                    }
                    if (allowExtra) {
                        menuItems.add(makeDecksDestinationMenuItem(
                                deckName + " / Extra Deck", finalCards));
                    }
                    menuItems.add(makeDecksDestinationMenuItem(
                            deckName + " / Side Deck", finalCards));
                }
            }
        } catch (Exception exception) {
            logger.error("buildAllDecksDestinationItemsForCards failed", exception);
        }
        return menuItems;
    }

    // ── My Collection destination list ─────────────────────────────────────────

    /**
     * Builds a flat list of {@link MenuItem}s covering every Box, sub-box, and
     * {@link CardsGroup} in the current owned cards collection.
     *
     * @param cards the cards to add when an item is selected
     * @return list of styled menu items; empty when the collection is unavailable
     */
    public static List<MenuItem> buildMyCollectionDestinationItemsForCards(Collection<Card> cards) {
        List<MenuItem> menuItems = new ArrayList<>();
        if (cards == null || cards.isEmpty()) {
            return menuItems;
        }
        try {
            OwnedCardsCollection ownedCollection = OuicheList.getMyCardsCollection();
            if (ownedCollection == null) {
                try {
                    UserInterfaceFunctions.loadCollectionFile();
                } catch (Exception ignored) {
                }
                ownedCollection = OuicheList.getMyCardsCollection();
            }
            if (ownedCollection == null || ownedCollection.getOwnedCollection() == null) {
                return menuItems;
            }

            final Collection<Card> finalCards = cards;

            for (Box box : ownedCollection.getOwnedCollection()) {
                if (box == null) {
                    continue;
                }
                String boxName = CardNameUtils.sanitize(box.getName());
                if (boxName.isEmpty()) {
                    boxName = "(Unnamed box)";
                }
                menuItems.add(makeMyCollectionDestinationMenuItem(boxName, finalCards));

                if (box.getContent() != null) {
                    for (CardsGroup cardsGroup : box.getContent()) {
                        if (cardsGroup == null) {
                            continue;
                        }
                        String groupName = CardNameUtils.sanitize(cardsGroup.getName());
                        if (groupName.isEmpty()) {
                            continue;
                        }
                        menuItems.add(makeMyCollectionDestinationMenuItem(
                                boxName + " / " + groupName, finalCards));
                    }
                }

                if (box.getSubBoxes() != null) {
                    for (Box subBox : box.getSubBoxes()) {
                        if (subBox == null) {
                            continue;
                        }
                        String subBoxName = CardNameUtils.sanitize(subBox.getName());
                        if (subBoxName.isEmpty()) {
                            subBoxName = "(Unnamed sub-box)";
                        }
                        menuItems.add(makeMyCollectionDestinationMenuItem(subBoxName, finalCards));

                        if (subBox.getContent() != null) {
                            for (CardsGroup cardsGroup : subBox.getContent()) {
                                if (cardsGroup == null) {
                                    continue;
                                }
                                String groupName = CardNameUtils.sanitize(cardsGroup.getName());
                                if (groupName.isEmpty()) {
                                    continue;
                                }
                                menuItems.add(makeMyCollectionDestinationMenuItem(
                                        subBoxName + " / " + groupName, finalCards));
                            }
                        }
                    }
                }
            }
        } catch (Exception exception) {
            logger.error("buildMyCollectionDestinationItemsForCards failed", exception);
        }
        return menuItems;
    }

    // ── Individual item factories ──────────────────────────────────────────────

    /**
     * Creates a menu item that adds {@code cards} to a collection's card list.
     *
     * @param collectionName the display name of the collection
     * @param cards          cards to add on selection
     */
    public static MenuItem makeDecksCollectionMenuItem(String collectionName, Collection<Card> cards) {
        return makeStyledMenuItem(collectionName, () -> {
            if (cards.size() == 1) {
                MenuActionHandler.handleAddToCollectionCards(
                        cards.iterator().next(), collectionName);
            } else {
                MenuActionHandler.handleBulkAddToCollectionCards(cards, collectionName);
            }
        });
    }

    /**
     * Creates a menu item that adds {@code cards} to a collection's exclusion list.
     *
     * @param collectionName the display name of the collection
     * @param cards          cards to add on selection
     */
    public static MenuItem makeDecksExclusionMenuItem(String collectionName, Collection<Card> cards) {
        return makeStyledMenuItem(collectionName + " / Exclusion List", () -> {
            if (cards.size() == 1) {
                MenuActionHandler.handleAddToExclusionList(
                        cards.iterator().next(), collectionName);
            } else {
                MenuActionHandler.handleBulkAddToExclusionList(cards, collectionName);
            }
        });
    }

    /**
     * Creates a menu item that adds {@code cards} to a deck list section.
     *
     * @param destinationPath the full slash-separated path, e.g.
     *                        {@code "DeckName / Main Deck"}
     * @param cards           cards to add on selection
     */
    public static MenuItem makeDecksDestinationMenuItem(String destinationPath,
                                                        Collection<Card> cards) {
        return makeStyledMenuItem(destinationPath, () -> {
            if (cards.size() == 1) {
                MenuActionHandler.handleAddToDeck(cards.iterator().next(), destinationPath);
            } else {
                MenuActionHandler.handleBulkAddToDeck(cards, destinationPath);
            }
        });
    }

    /**
     * Creates a menu item that adds {@code cards} to a box or category in the
     * owned cards collection.
     *
     * @param destinationPath the slash-separated box and optional category name
     * @param cards           cards to add on selection
     */
    public static MenuItem makeMyCollectionDestinationMenuItem(String destinationPath,
                                                               Collection<Card> cards) {
        return makeStyledMenuItem(destinationPath, () -> {
            if (cards.size() == 1) {
                MenuActionHandler.handleAddCopy(cards.iterator().next(), destinationPath);
            } else {
                MenuActionHandler.handleBulkAddCopy(cards, destinationPath);
            }
        });
    }

    // ── Private helper ─────────────────────────────────────────────────────────

    /**
     * Creates a {@link MenuItem} with a white-styled {@link Label} graphic (dark
     * context menu compatible) wired to the given {@code action}.
     */
    private static MenuItem makeStyledMenuItem(String labelText, Runnable action) {
        MenuItem menuItem = new MenuItem();
        Label label = new Label(labelText);
        label.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
        HBox graphic = new HBox(label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setPadding(new Insets(2, 6, 2, 6));
        menuItem.setGraphic(graphic);
        menuItem.setText("");
        menuItem.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
        });
        return menuItem;
    }
}