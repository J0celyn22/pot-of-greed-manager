package Controller;

import Model.CardsLists.*;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the "Add to Deck", "Add to Collection Cards", and "Add to Exclusion
 * List" card-menu actions: adding a single owned card into a Deck's Main/Extra/
 * Side list, a {@link ThemeCollection}'s card list, or a collection's
 * "cards not to add" exclusion list.
 * <p>
 * Extracted from {@link MenuActionHandler}, which still exposes {@code
 * handleAddToDeck}, {@code handleAddToCollectionCards} and {@code
 * handleAddToExclusionList} as thin delegates so existing call sites are
 * unaffected. The core {@code doAddToDeck}/{@code doAddToCollectionCards}/
 * {@code doAddToExclusionList} methods stay package-visible (not private)
 * because {@link MenuActionHandler}'s bulk-add operations call them directly
 * for each card in a batch, the same way bulk-copy calls into
 * {@link CardCopyHandler#doAddCopy}.
 * </p>
 */
final class CardAddToListHandler {

    private static final Logger logger = LoggerFactory.getLogger(CardAddToListHandler.class);

    private CardAddToListHandler() {
    }

    // ── Add to deck ──────────────────────────────────────────────────────────

    /**
     * Adds a copy of the given {@link Card} to the correct deck list
     * (Main Deck / Extra Deck / Side Deck) identified by {@code handlerTarget}.
     *
     * @param card          the card from AllExistingCards (not {@code null})
     * @param handlerTarget e.g. {@code "DeckName / Main Deck"} or
     *                      {@code "CollectionName / DeckName / Extra Deck"}
     */
    public static void handleAddToDeck(Card card, String handlerTarget) {
        if (card == null || handlerTarget == null || handlerTarget.trim().isEmpty()) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                doAddToDeck(card, handlerTarget);
            } else {
                Platform.runLater(() -> doAddToDeck(card, handlerTarget));
            }
            MenuActionHandler.setLastDecksAddedTarget(handlerTarget);
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        } catch (Throwable throwable) {
            logger.error("handleAddToDeck failed for target '{}'", handlerTarget, throwable);
        }
    }

    static void doAddToDeck(Card card, String handlerTarget) {
        DecksAndCollectionsList decksAndCollections = UserInterfaceFunctions.getDecksList();
        if (decksAndCollections == null) {
            try {
                UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
            } catch (Throwable ignored) {
            }
            decksAndCollections = UserInterfaceFunctions.getDecksList();
        }
        if (decksAndCollections == null) {
            logger.warn("doAddToDeck: DecksAndCollectionsList not available");
            return;
        }

        java.util.function.Function<String, String> normalizer = input -> {
            if (input == null) {
                return "";
            }
            String normalized = input.trim()
                    .replaceAll("[=\\-]", "")
                    .replaceAll("\\s+", " ");
            normalized = java.text.Normalizer
                    .normalize(normalized, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            return normalized.toLowerCase().trim();
        };

        String[] parts = handlerTarget.split("/");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }

        if (parts.length < 2) {
            logger.debug("doAddToDeck: handlerTarget '{}' has fewer than 2 parts", handlerTarget);
            return;
        }

        String lastNorm = normalizer.apply(parts[parts.length - 1]);
        boolean isMain = lastNorm.equals("main deck") || lastNorm.equals("main");
        boolean isExtra = lastNorm.equals("extra deck") || lastNorm.equals("extra");
        boolean isSide = lastNorm.equals("side deck") || lastNorm.equals("side");

        if (!isMain && !isExtra && !isSide) {
            logger.debug("doAddToDeck: last part '{}' is not a recognised deck list", lastNorm);
            return;
        }

        String deckNameNorm = normalizer.apply(parts[parts.length - 2]);
        Deck targetDeck = findDeckInDac(deckNameNorm, normalizer, decksAndCollections);

        if (targetDeck == null) {
            logger.info("doAddToDeck: deck '{}' not found in DecksAndCollectionsList", deckNameNorm);
            return;
        }

        String requestedSectionName;
        if (isMain) {
            requestedSectionName = "Main Deck";
        } else if (isExtra) {
            requestedSectionName = "Extra Deck";
        } else {
            requestedSectionName = "Side Deck";
        }
        String redirectSectionName = Utils.DeckCompatibility.redirectSection(card, requestedSectionName);
        String effectiveSectionName = redirectSectionName != null ? redirectSectionName : requestedSectionName;

        CardElement newElement = new CardElement(card);
        String sectionName;
        if (Utils.DeckCompatibility.isMainDeckSection(effectiveSectionName)) {
            targetDeck.getMainDeck().add(newElement);
            sectionName = "main";
        } else if (Utils.DeckCompatibility.isExtraDeckSection(effectiveSectionName)) {
            targetDeck.getExtraDeck().add(newElement);
            sectionName = "extra";
        } else {
            targetDeck.getSideDeck().add(newElement);
            sectionName = "side";
        }
        logger.debug("doAddToDeck: added '{}' to '{}'", card.getName_EN(), handlerTarget);

        try {
            String parentCollectionName = findCollectionNameForDeck(targetDeck, decksAndCollections);
            OuicheList.onDeckCardAdded(newElement, targetDeck.getName(), sectionName, parentCollectionName);
            UserInterfaceFunctions.refreshOuicheListView();
        } catch (Throwable throwable) {
            logger.error("OuicheList update failed after adding '{}' to deck '{}'",
                    card.getName_EN(), handlerTarget, throwable);
        }

        UserInterfaceFunctions.markDirty(targetDeck);
        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
    }

    private static Deck findDeckInDac(String deckNameNorm,
                                      java.util.function.Function<String, String> normalizer,
                                      DecksAndCollectionsList decksAndCollections) {
        // Search standalone decks first
        if (decksAndCollections.getDecks() != null) {
            for (Deck deck : decksAndCollections.getDecks()) {
                if (deck != null && normalizer.apply(deck.getName()).equals(deckNameNorm)) {
                    return deck;
                }
            }
        }
        // Search inside collections
        if (decksAndCollections.getCollections() != null) {
            for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
                if (themeCollection == null || themeCollection.getLinkedDecks() == null) {
                    continue;
                }
                for (List<Deck> unit : themeCollection.getLinkedDecks()) {
                    if (unit == null) {
                        continue;
                    }
                    for (Deck deck : unit) {
                        if (deck != null && normalizer.apply(deck.getName()).equals(deckNameNorm)) {
                            return deck;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns the name of the {@link ThemeCollection} that owns {@code deck} within
     * {@code decksAndCollections}, or {@code null} if the deck is standalone.
     */
    private static String findCollectionNameForDeck(Deck deck,
                                                    DecksAndCollectionsList decksAndCollections) {
        if (deck == null || decksAndCollections == null
                || decksAndCollections.getCollections() == null) {
            return null;
        }
        for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
            if (themeCollection == null || themeCollection.getLinkedDecks() == null) {
                continue;
            }
            for (List<Deck> unit : themeCollection.getLinkedDecks()) {
                if (unit == null) {
                    continue;
                }
                for (Deck linkedDeck : unit) {
                    if (linkedDeck == deck) {
                        return themeCollection.getName();
                    }
                }
            }
        }
        return null;
    }

    // ── Add to collection cards ──────────────────────────────────────────────

    /**
     * Adds a copy of the given {@link Card} to the card list of the named
     * {@link ThemeCollection}.
     *
     * @param card           the card to add (not {@code null})
     * @param collectionName the name of the target collection
     */
    public static void handleAddToCollectionCards(Card card, String collectionName) {
        if (card == null || collectionName == null || collectionName.trim().isEmpty()) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                doAddToCollectionCards(card, collectionName);
            } else {
                Platform.runLater(() -> doAddToCollectionCards(card, collectionName));
            }
            MenuActionHandler.setLastDecksAddedTarget(collectionName + " / Cards");
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        } catch (Throwable throwable) {
            logger.error("handleAddToCollectionCards failed for collection '{}'", collectionName, throwable);
        }
    }

    static void doAddToCollectionCards(Card card, String collectionName) {
        DecksAndCollectionsList decksAndCollections = UserInterfaceFunctions.getDecksList();
        if (decksAndCollections == null) {
            try {
                UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
            } catch (Throwable ignored) {
            }
            decksAndCollections = UserInterfaceFunctions.getDecksList();
        }
        if (decksAndCollections == null || decksAndCollections.getCollections() == null) {
            return;
        }

        java.util.function.Function<String, String> normalizer = input -> {
            if (input == null) {
                return "";
            }
            String normalized = input.trim()
                    .replaceAll("[=\\-]", "")
                    .replaceAll("\\s+", " ");
            normalized = java.text.Normalizer
                    .normalize(normalized, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            return normalized.toLowerCase().trim();
        };
        String targetNorm = normalizer.apply(collectionName);

        ThemeCollection foundCollection = null;
        for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
            if (themeCollection == null) {
                continue;
            }
            if (normalizer.apply(themeCollection.getName()).equals(targetNorm)) {
                foundCollection = themeCollection;
                break;
            }
        }
        if (foundCollection == null) {
            logger.info("handleAddToCollectionCards: collection '{}' not found", collectionName);
            return;
        }

        if (foundCollection.getCardsList() == null) {
            foundCollection.setCardsList(new ArrayList<>());
        }
        CardElement newElement = new CardElement(card);
        foundCollection.getCardsList().add(newElement);
        logger.debug("handleAddToCollectionCards: added '{}' to collection '{}'",
                card.getName_EN(), collectionName);

        try {
            OuicheList.onDeckCardAdded(newElement, null, null, foundCollection.getName());
            UserInterfaceFunctions.refreshOuicheListView();
        } catch (Throwable throwable) {
            logger.error("OuicheList update failed after adding '{}' to collection '{}'",
                    card.getName_EN(), collectionName, throwable);
        }

        UserInterfaceFunctions.markDirty(foundCollection);
        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
    }

    // ── Add to exclusion list ────────────────────────────────────────────────

    /**
     * Adds a copy of the given {@link Card} to the exclusion list ("cards not to
     * add") of the named {@link ThemeCollection}.
     *
     * @param card           the card to exclude (not {@code null})
     * @param collectionName the name of the target collection
     */
    public static void handleAddToExclusionList(Card card, String collectionName) {
        if (card == null || collectionName == null || collectionName.trim().isEmpty()) {
            return;
        }
        try {
            if (Platform.isFxApplicationThread()) {
                doAddToExclusionList(card, collectionName);
            } else {
                Platform.runLater(() -> doAddToExclusionList(card, collectionName));
            }
            MenuActionHandler.setLastDecksAddedTarget(collectionName + " / Cards not to add");
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        } catch (Throwable throwable) {
            logger.debug("handleAddToExclusionList failed for collection {}",
                    collectionName, throwable);
        }
    }

    static void doAddToExclusionList(Card card, String collectionName) {
        DecksAndCollectionsList decksAndCollections = UserInterfaceFunctions.getDecksList();
        if (decksAndCollections == null) {
            try {
                UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
            } catch (Throwable ignored) {
            }
            decksAndCollections = UserInterfaceFunctions.getDecksList();
        }
        if (decksAndCollections == null || decksAndCollections.getCollections() == null) {
            return;
        }

        java.util.function.Function<String, String> normalizer = input -> {
            if (input == null) {
                return "";
            }
            String normalized = input.trim()
                    .replaceAll("[=\\-]", "")
                    .replaceAll("\\s+", " ");
            normalized = java.text.Normalizer
                    .normalize(normalized, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            return normalized.toLowerCase().trim();
        };
        String targetNorm = normalizer.apply(collectionName);

        ThemeCollection foundCollection = null;
        for (ThemeCollection themeCollection : decksAndCollections.getCollections()) {
            if (themeCollection == null) {
                continue;
            }
            if (normalizer.apply(themeCollection.getName()).equals(targetNorm)) {
                foundCollection = themeCollection;
                break;
            }
        }
        if (foundCollection == null) {
            logger.info("handleAddToExclusionList: collection '{}' not found", collectionName);
            return;
        }

        if (foundCollection.getExceptionsToNotAdd() == null) {
            foundCollection.setExceptionsToNotAdd(new ArrayList<>());
        }
        foundCollection.getExceptionsToNotAdd().add(new CardElement(card));
        logger.debug("handleAddToExclusionList: added '{}' to exclusion list of '{}'",
                card.getName_EN(), collectionName);

        UserInterfaceFunctions.markDirty(foundCollection);
        UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
    }
}