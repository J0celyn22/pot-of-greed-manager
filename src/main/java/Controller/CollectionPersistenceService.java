package Controller;

import Model.CardsLists.DecksAndCollectionsList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static Controller.CollectionFileIO.filePath;
import static Controller.CollectionFileIO.folderPath;

/**
 * Persists My Collection, standalone Decks, and Theme Collections to disk, using the
 * dirty-tracking state maintained by {@link CollectionStateTracker} to decide what
 * actually needs writing.
 */
public class CollectionPersistenceService {

    private static final Logger logger = LoggerFactory.getLogger(CollectionPersistenceService.class);

    private CollectionPersistenceService() {
    }

    public static void saveMyCollection() throws Exception {
        if (!CollectionStateTracker.isMyCollectionDirty()) {
            logger.debug("saveMyCollection: collection is not dirty, skipping save");
            return;
        }
        if (filePath == null) {
            logger.warn("saveMyCollection: no file path configured");
            return;
        }
        Model.CardsLists.OwnedCardsCollection owned =
                Model.CardsLists.OuicheList.getMyCardsCollection();
        if (owned == null) {
            return;
        }
        owned.SaveCollection(filePath.getAbsolutePath());
        CollectionStateTracker.clearMyCollectionDirty();
        logger.info("My Collection saved to {}", filePath.getAbsolutePath());
    }

    public static void saveAllDecksAndCollections() throws Exception {
        if (folderPath == null) {
            logger.warn("saveAllDecksAndCollections: no folder path configured");
            return;
        }
        DecksAndCollectionsList decksAndCollectionsList = CollectionStateTracker.getDecksList();
        if (decksAndCollectionsList == null) {
            return;
        }
        String dir = folderPath.getAbsolutePath();

        if (decksAndCollectionsList.getCollections() != null) {
            for (Model.CardsLists.ThemeCollection themeCollection : decksAndCollectionsList.getCollections()) {
                if (themeCollection == null) {
                    continue;
                }

                // Determine whether the collection itself or any of its linked
                // decks are dirty — if so the .ytc file must be saved.
                boolean collectionIsDirty = CollectionStateTracker.isDirty(themeCollection);

                boolean anyLinkedDeckIsDirty = false;
                if (themeCollection.getLinkedDecks() != null) {
                    for (List<Model.CardsLists.Deck> unit : themeCollection.getLinkedDecks()) {
                        if (unit == null) {
                            continue;
                        }
                        for (Model.CardsLists.Deck deck : unit) {
                            if (deck != null && CollectionStateTracker.isDirty(deck)) {
                                anyLinkedDeckIsDirty = true;
                                break;
                            }
                        }
                        if (anyLinkedDeckIsDirty) {
                            break;
                        }
                    }
                }

                // Save .ytc only when needed
                if (collectionIsDirty || anyLinkedDeckIsDirty) {
                    themeCollection.saveToFile(dir);
                    CollectionStateTracker.clearDirty(themeCollection);
                    logger.info("Saved collection '{}'", themeCollection.getName());
                }

                // Save each linked .ydk only when that deck is dirty
                if (themeCollection.getLinkedDecks() != null) {
                    for (List<Model.CardsLists.Deck> unit : themeCollection.getLinkedDecks()) {
                        if (unit == null) {
                            continue;
                        }
                        for (Model.CardsLists.Deck deck : unit) {
                            if (deck == null) {
                                continue;
                            }
                            if (CollectionStateTracker.isDirty(deck)) {
                                deck.saveDeck(dir);
                                CollectionStateTracker.clearDirty(deck);
                                logger.info("Saved linked deck '{}'", deck.getName());
                            }
                        }
                    }
                }
            }
        }

        if (decksAndCollectionsList.getDecks() != null) {
            for (Model.CardsLists.Deck deck : decksAndCollectionsList.getDecks()) {
                if (deck == null) {
                    continue;
                }
                if (CollectionStateTracker.isDirty(deck)) {
                    deck.saveDeck(dir);
                    CollectionStateTracker.clearDirty(deck);
                    logger.info("Saved standalone deck '{}'", deck.getName());
                }
            }
        }

        // All dirty flags have been individually cleared above;
        // clear the set to catch any stragglers.
        CollectionStateTracker.clearAllDirtyDecksAndCollections();
        logger.info("Save complete — only modified elements were written.");
    }

    /**
     * Saves a single {@link Model.CardsLists.Deck} or {@link Model.CardsLists.ThemeCollection}
     * (plus any dirty linked decks when saving a collection) to disk, then clears their dirty
     * flags and updates the tab title indicator.
     * <p>
     * Unlike {@link #saveAllDecksAndCollections()}, this method always writes the target object
     * regardless of its current dirty state, making it suitable for an explicit per-item Save button.
     * </p>
     *
     * @param obj the {@code Deck} or {@code ThemeCollection} to save
     * @throws Exception if the underlying file-write fails
     */
    public static void saveSingleDeckOrCollection(Object obj) throws Exception {
        if (folderPath == null) {
            logger.warn("saveSingleDeckOrCollection: no folder path configured");
            return;
        }
        String dir = folderPath.getAbsolutePath();

        if (obj instanceof Model.CardsLists.Deck) {
            Model.CardsLists.Deck deck = (Model.CardsLists.Deck) obj;
            deck.saveDeck(dir);
            CollectionStateTracker.clearDirty(deck);
            ViewRefreshCoordinator.triggerTabDirtyIndicatorUpdate();
            // Rebuild the nav menu so the "* name" asterisk is cleared there too.
            ViewRefreshCoordinator.refreshDecksAndCollectionsView();
            logger.info("Saved deck '{}'", deck.getName());

        } else if (obj instanceof Model.CardsLists.ThemeCollection themeCollection) {
            themeCollection.saveToFile(dir);
            CollectionStateTracker.clearDirty(themeCollection);
            // Also flush any dirty linked decks so the whole collection is consistent on disk.
            if (themeCollection.getLinkedDecks() != null) {
                for (List<Model.CardsLists.Deck> unit : themeCollection.getLinkedDecks()) {
                    if (unit == null) {
                        continue;
                    }
                    for (Model.CardsLists.Deck deck : unit) {
                        if (CollectionStateTracker.isDirty(deck)) {
                            deck.saveDeck(dir);
                            CollectionStateTracker.clearDirty(deck);
                            logger.info("Saved linked deck '{}' while saving collection '{}'",
                                    deck.getName(), themeCollection.getName());
                        }
                    }
                }
            }
            ViewRefreshCoordinator.triggerTabDirtyIndicatorUpdate();
            // Rebuild the nav menu so the "* name" asterisk is cleared there too.
            ViewRefreshCoordinator.refreshDecksAndCollectionsView();
            logger.info("Saved collection '{}'", themeCollection.getName());
        }
    }
}