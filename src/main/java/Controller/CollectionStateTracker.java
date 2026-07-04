package Controller;

import Model.CardsLists.DecksAndCollectionsList;
import Model.FormatList.CardToHtml;
import Model.FormatList.HtmlGenerator;

import static Model.FilePaths.outputPath;

/**
 * Tracks the currently loaded {@link DecksAndCollectionsList} together with the
 * "dirty" state of the owned collection, the OuicheList, and individual decks and
 * collections, plus the one-shot guard that prevents the full-database HTML export
 * pass from repeating unnecessarily within a session.
 * <p>
 * "Dirty" here means "changed since last save" and drives both the per-tab unsaved-
 * changes indicator and which objects a save pass actually needs to write.
 */
public class CollectionStateTracker {

    private static final java.util.Set<Object> dirtyDecksAndCollections =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static DecksAndCollectionsList decksList = null;
    private static volatile boolean myCollectionDirty = false;
    private static volatile boolean ouicheListDirty = false;

    /**
     * Set to {@code true} once {@link CardToHtml#generateAllCardPages(String)} has
     * been run during the current application session. Prevents the (slow) full-database
     * pass from being repeated on every individual export action.
     * Reset by {@link #resetCardPagesGenerated()} when the output directory changes.
     */
    private static volatile boolean cardPagesGenerated = false;

    private CollectionStateTracker() {
    }

    public static DecksAndCollectionsList getDecksList() {
        return decksList;
    }

    public static void setDecksList(DecksAndCollectionsList list) {
        decksList = list;
    }

    public static void markDirty(Object deckOrCollection) {
        if (deckOrCollection != null) {
            dirtyDecksAndCollections.add(deckOrCollection);
        }
    }

    public static void markMyCollectionDirty() {
        myCollectionDirty = true;
    }

    public static boolean isDirty(Object deckOrCollection) {
        return deckOrCollection != null && dirtyDecksAndCollections.contains(deckOrCollection);
    }

    public static boolean isMyCollectionDirty() {
        return myCollectionDirty;
    }

    public static boolean isAnyDeckOrCollectionDirty() {
        return !dirtyDecksAndCollections.isEmpty();
    }

    public static void clearDirty(Object obj) {
        dirtyDecksAndCollections.remove(obj);
    }

    public static void clearAllDirtyDecksAndCollections() {
        dirtyDecksAndCollections.clear();
    }

    public static void clearMyCollectionDirty() {
        myCollectionDirty = false;
    }

    public static void markOuicheListDirty() {
        ouicheListDirty = true;
    }

    public static boolean isOuicheListDirty() {
        return ouicheListDirty;
    }

    public static void clearOuicheListDirty() {
        ouicheListDirty = false;
    }

    /**
     * Generates HTML detail pages for every card in the database, if this has
     * not already been done in the current application session.
     *
     * <p>The generation is guarded by {@link #cardPagesGenerated}: the full-database
     * pass runs at most once per session regardless of how many export actions the
     * user triggers. Call {@link #resetCardPagesGenerated()} when the output directory
     * changes so that the next export re-generates against the new location.</p>
     */
    public static void ensureCardPagesGenerated() {
        if (cardPagesGenerated) {
            return;
        }
        HtmlGenerator.resetExportSession();
        CardToHtml.generateAllCardPages(outputPath);
        cardPagesGenerated = true;
    }

    /**
     * Clears the card-pages-generated flag so that the next export action
     * triggers a fresh {@link CardToHtml#generateAllCardPages(String)} pass.
     * Call this whenever {@link Model.FilePaths#outputPath} changes.
     */
    public static void resetCardPagesGenerated() {
        cardPagesGenerated = false;
    }

    /**
     * Marks every deck and collection in the currently loaded Decks and Collections List as dirty.
     */
    public static void markAllDecksAndCollectionsDirty() {
        DecksAndCollectionsList decksAndCollectionsList = getDecksList();
        if (decksAndCollectionsList == null) {
            return;
        }
        if (decksAndCollectionsList.getDecks() != null) {
            decksAndCollectionsList.getDecks().forEach(deck -> {
                if (deck != null) {
                    markDirty(deck);
                }
            });
        }
        if (decksAndCollectionsList.getCollections() != null) {
            decksAndCollectionsList.getCollections().forEach(themeCollection -> {
                if (themeCollection == null) {
                    return;
                }
                markDirty(themeCollection);
                if (themeCollection.getLinkedDecks() != null) {
                    themeCollection.getLinkedDecks().forEach(linkedDeckUnit -> {
                        if (linkedDeckUnit != null) {
                            linkedDeckUnit.forEach(deck -> {
                                if (deck != null) {
                                    markDirty(deck);
                                }
                            });
                        }
                    });
                }
            });
        }
    }
}