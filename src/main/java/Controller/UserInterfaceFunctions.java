package Controller;

import Model.CardsLists.*;
import Model.FormatList.*;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeView;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static Model.CardsLists.OuicheList.*;
import static Model.CardsLists.SubListCreator.*;
import static Model.FilePaths.outputPath;
import static Model.FilePaths.outputPathLists;
import static Model.FormatList.CardListToHtml.*;
import static Model.FormatList.OuicheListToHtml.generateOuicheListAsListHtml;
import static Model.FormatList.OuicheListToHtml.generateOuicheListAsMosaicHtml;

/**
 * Class containing all functions called by the user interface.
 */
public class UserInterfaceFunctions {
    //private static String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
    //private static final String outputPath = ".\\Output\\"/* + dateTime + "\\"*/;
    //private static final String outputPathLists = outputPath + "Lists\\";
    public static File filePath = null;
    // Use folderPath for the decks and collections folder.
    public static File folderPath = null;
    public static File thirdPartyListPath = null;
    public static File ouicheListPath = null;
    private static Boolean myCollectionIsLoaded = false;
    private static Boolean decksAndCollectionIsLoaded = false;
    private static Boolean thirdPartyCollectionIsLoaded = false;
    private static Boolean ouicheListIsLoaded = false;
    private static Boolean detailedOuicheListIsLoaded = false;

    // Added static variable to hold the decks list.
    private static DecksAndCollectionsList decksList = null;

    public static DecksAndCollectionsList getDecksList() {
        return decksList;
    }

    private static final Logger logger = LoggerFactory.getLogger(UserInterfaceFunctions.class);

    private static final CopyOnWriteArrayList<Runnable> explicitRefreshers = new CopyOnWriteArrayList<>();

    private static final CopyOnWriteArrayList<Runnable> explicitStructureRefreshers = new CopyOnWriteArrayList<>();
    private static Object pendingRenameTarget = null;

    private static Object pendingDecksRenameTarget = null;

    /**
     * Set to true when a rename confirm requires a full middle-pane tree rebuild
     * but must NOT trigger another inline-rename attempt.
     */
    private static volatile boolean pendingDecksFullRebuild = false;
    private static Object pendingDecksExpandTarget = null;

    public static void setPendingDecksFullRebuild() {
        pendingDecksFullRebuild = true;
    }

    public static void setPendingDecksExpandTarget(Object target) {
        pendingDecksExpandTarget = target;
    }

    public static Object getAndClearPendingDecksExpandTarget() {
        Object expandTarget = pendingDecksExpandTarget;
        pendingDecksExpandTarget = null;
        return expandTarget;
    }

    public static boolean getAndClearPendingDecksFullRebuild() {
        boolean wasPendingFullRebuild = pendingDecksFullRebuild;
        pendingDecksFullRebuild = false;
        return wasPendingFullRebuild;
    }

    private static Object pendingDecksScrollTarget = null;
    // Stores {ThemeCollection, Deck} for the "Create Collection from Deck" flow
    private static Object[] pendingDecksCreateCollectionData = null;

    public static void setPendingDecksScrollTarget(Object target) {
        pendingDecksScrollTarget = target;
    }

    public static Object getAndClearPendingDecksScrollTarget() {
        Object scrollTarget = pendingDecksScrollTarget;
        pendingDecksScrollTarget = null;
        return scrollTarget;
    }

    public static void setPendingDecksRenameTarget(Object target) {
        pendingDecksRenameTarget = target;
    }

    public static void setPendingRenameTarget(Object target) {
        pendingRenameTarget = target;
    }

    public static Object getAndClearPendingDecksRenameTarget() {
        Object renameTarget = pendingDecksRenameTarget;
        pendingDecksRenameTarget = null;
        return renameTarget;
    }

    public static void setPendingDecksCreateCollectionData(Object[] data) {
        pendingDecksCreateCollectionData = data;
    }

    public static Object[] getAndClearPendingDecksCreateCollectionData() {
        Object[] createCollectionData = pendingDecksCreateCollectionData;
        pendingDecksCreateCollectionData = null;
        return createCollectionData;
    }

    // ── Dirty tracking ────────────────────────────────────────────────────────────
    private static final java.util.Set<Object> dirtyDecksAndCollections =
            java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private static volatile boolean myCollectionDirty = false;
    private static volatile boolean ouicheListDirty = false;

    /**
     * Set to {@code true} once {@link CardToHtml#generateAllCardPages(String)} has
     * been run during the current application session. Prevents the (slow) full-database
     * pass from being repeated on every individual export action.
     * Reset by {@link #resetCardPagesGenerated()} when the output directory changes.
     */
    private static volatile boolean cardPagesGenerated = false;
    // ── Tab dirty-indicator callback ──────────────────────────────────────────────
    private static Runnable tabDirtyIndicatorUpdater = null;

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

    // ── Save methods ──────────────────────────────────────────────────────────────

    /**
     * Generates HTML detail pages for every card in the database, if this has
     * not already been done in the current session.
     *
     * <p>The generation is guarded by {@link #cardPagesGenerated}: the full-database
     * pass runs at most once per session regardless of how many export actions the
     * user triggers. Call {@link #resetCardPagesGenerated()} when the output directory
     * changes so that the next export re-generates against the new location.</p>
     */
    private static void ensureCardPagesGenerated() {
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

    public static void saveMyCollection() throws Exception {
        if (!myCollectionDirty) {
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
        clearMyCollectionDirty();
        logger.info("My Collection saved to {}", filePath.getAbsolutePath());
    }

    public static void saveAllDecksAndCollections() throws Exception {
        if (folderPath == null) {
            logger.warn("saveAllDecksAndCollections: no folder path configured");
            return;
        }
        DecksAndCollectionsList decksAndCollectionsList = getDecksList();
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
                boolean collectionIsDirty = isDirty(themeCollection);

                boolean anyLinkedDeckIsDirty = false;
                if (themeCollection.getLinkedDecks() != null) {
                    for (List<Model.CardsLists.Deck> unit : themeCollection.getLinkedDecks()) {
                        if (unit == null) {
                            continue;
                        }
                        for (Model.CardsLists.Deck deck : unit) {
                            if (deck != null && isDirty(deck)) {
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
                    clearDirty(themeCollection);
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
                            if (isDirty(deck)) {
                                deck.saveDeck(dir);
                                clearDirty(deck);
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
                if (isDirty(deck)) {
                    deck.saveDeck(dir);
                    clearDirty(deck);
                    logger.info("Saved standalone deck '{}'", deck.getName());
                }
            }
        }

        // All dirty flags have been individually cleared above;
        // clear the set to catch any stragglers.
        clearAllDirtyDecksAndCollections();
        logger.info("Save complete — only modified elements were written.");
    }

    public static void registerTabDirtyIndicatorUpdater(Runnable updater) {
        tabDirtyIndicatorUpdater = updater;
    }

    // ── Archetypes refreshers ──────────────────────────────────────────────────
    // Called whenever decks/collections or the owned collection change, because
    // archetype glow states depend on which cards are present in both.
    private static final CopyOnWriteArrayList<Runnable> explicitArchetypesRefreshers = new CopyOnWriteArrayList<>();
    // ── Decks tree-view refreshers ────────────────────────────────────────────
    // A lightweight tree.refresh() (no model rebuild) called on every model
    // change so that archetype-card glow states inside Collections stay in sync.
    private static final CopyOnWriteArrayList<Runnable> decksTreeRefreshers = new CopyOnWriteArrayList<>();

    // Setter and getter for decksList.
    public static void setDecksList(DecksAndCollectionsList list) {
        decksList = list;
    }

    /**
     * Returns the status of whether the Ouiche list is loaded.
     *
     * @return true if the Ouiche list is loaded, false otherwise.
     */
    public static Boolean getOuicheListIsLoaded() {
        return ouicheListIsLoaded;
    }

    /**
     * Reads the default file and folder paths from a file named "default_folders.txt".
     * The file is expected to contain two lines, the first line being the path to the
     * collection save file, and the second line being the path to the folder containing
     * all the decks.
     * <p>
     * If the file does not exist or there is an error reading the file, the default paths
     * are not changed.
     */
    public static void readPathsFromFile() {
        try {
            File file = new File("default_folders.txt");
            if (file.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                String line;
                if ((line = reader.readLine()) != null) {
                    filePath = new File(line);
                }
                if ((line = reader.readLine()) != null) {
                    // folderPath is used for decks and collections.
                    folderPath = new File(line);
                }
                if ((line = reader.readLine()) != null) {
                    thirdPartyListPath = new File(line);
                }
                reader.close();
            }
        } catch (IOException exception) {
            logger.debug("readPathsFromFile: could not read default_folders.txt", exception);
        }
    }

    /**
     * Writes the given file paths to a file named "default_folders.txt".
     * The file is expected to contain three lines, the first line being the path to the
     * collection save file, the second line being the path to the folder containing
     * all the decks and the third line being the path to the third party list.
     * The paths are written in absolute terms.
     * If a path is null, an empty string is written instead.
     * <p>
     * If the file does not exist, it is created. If it does exist, its contents are
     * overwritten.
     * <p>
     * If there is an error writing to the file, it is logged and otherwise ignored.
     * @param filePath The path to the collection save file.
     * @param folderPath The path to the folder containing all the decks.
     * @param thirdPartyListPath The path to the third party list.
     */
    public static void savePathsToFile(File filePath, File folderPath, File thirdPartyListPath) {
        try {
            File file = new File("default_folders.txt");
            File parentDir = file.getParentFile();

            if (parentDir != null && !parentDir.exists()) {
                boolean mkdirs = parentDir.mkdirs();
                if (!mkdirs) {
                    logger.warn("savePathsToFile: parent directory was not created: {}",
                            parentDir.getAbsolutePath());
                }
            }

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                writer.write(filePath != null ? filePath.getAbsolutePath() : "");
                writer.newLine();
                writer.write(folderPath != null ? folderPath.getAbsolutePath() : "");
                writer.newLine();
                writer.write(thirdPartyListPath != null ? thirdPartyListPath.getAbsolutePath() : "");
            }
        } catch (IOException exception) {
            logger.error("savePathsToFile: failed to write default_folders.txt", exception);
        }
    }

    /**
     * Plays the Pot of Greed's laugh sound effect.
     * <p>
     * The sound effect is loaded from the file "PotOfGreedLaugh.mp3" in the resources
     * directory. If the file does not exist, the method does nothing.
     */
    public static void playLaughSound() {
        // Load the audio file
        String audioFilePath;
        Media media;
        MediaPlayer mediaPlayer;
        try { //TODO find a better way to do this without having to put the jar version of the path in a catch
            audioFilePath = ".\\src\\main\\resources\\PotOfGreedLaugh.mp3";
            media = new Media(new File(audioFilePath).toURI().toString());
            mediaPlayer = new MediaPlayer(media);
        } catch (Exception e) {
            audioFilePath = "resources\\PotOfGreedLaugh.mp3";
            media = new Media(new File(audioFilePath).toURI().toString());
            mediaPlayer = new MediaPlayer(media);
        }

        // Play the audio
        mediaPlayer.play();
    }

    /**
     * Generates the detailed ouiche list in both list and mosaic formats.
     * <p>
     * If the collection and decks and collections have not been loaded yet,
     * they are loaded first. The detailed ouiche list is then saved to
     * files in the directory specified by {@code outputPathLists}, both as
     * a list and as a mosaic. Additionally, the available cards are saved
     * as a list and a mosaic, both with and without the "Complete" tag.
     * <p>
     * If there is an error writing to the file, it is logged and otherwise ignored.
     */
    public static void generateOuicheListFunction() {
        try {
            if (!myCollectionIsLoaded) {
                loadCollectionFile();
            }

            if (!decksAndCollectionIsLoaded) {
                loadDecksAndCollectionsDirectory();
            }

            ensureCardPagesGenerated();

            setDetailedOuicheList(Model.CardsLists.OuicheList.createDetailedOuicheList(getMyCardsCollection(), getDecksList()));

            //String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Files.createDirectories(Paths.get(outputPathLists));

            generateOuicheListAsListHtml(getDetailedOuicheList(), outputPathLists, "Detailed OuicheList");
            generateOuicheListAsMosaicHtml(getDetailedOuicheList(), outputPathLists, "Detailed OuicheList");

            generateHtmlWithOwned(getUnusedCards(), outputPathLists, "Available Cards - Complete", true);
            generateHtmlWithOwned(getUnusedCards(), outputPathLists, "Available Cards", false);

            detailedOuicheListIsLoaded = true;

            markOuicheListDirty();
            triggerTabDirtyIndicatorUpdate();
        } catch (IOException exception) {
            logger.error("generateOuicheListFunction: failed to write OuicheList HTML output", exception);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Generate archetypes lists from the list of all cards, and generate HTML files
     * from these lists.
     * <p>
     * The archetypes lists are generated from the list of all cards, which is also
     * automatically generated. Then, the HTML files are generated from these lists.
     * The HTML files are saved in a directory named "Archetypes" in the output
     * directory.
     * <p>
     * If there is an error writing to the file, it is not handled. You can log an
     * error message or handle it as needed.
     * @throws Exception
     */
    public static void generateArchetypesListsFunction() throws Exception {
        logger.info("Generating archetype lists from the full card database");
        CreateArchetypeLists(Model.Database.Database.getAllCardsList());

        logger.info("Generating archetype HTML pages");
        String outputPathArchetypes = outputPath + "Archetypes\\";
        Files.createDirectories(Paths.get(outputPathArchetypes));

        // Create the Archetypes menu and then the individual archetype pages
        ArchetypesListsToHtml.generateArchetypesMenu(outputPathArchetypes, getArchetypesList());
        ArchetypesListsToHtml.GenerateAllArchetypesLists(outputPathArchetypes, getArchetypesList(), getArchetypesCardsLists());

        logger.info("Archetype list generation complete");
    }

    /**
     * Opens a file chooser dialog to select a collection file and updates
     * the provided text field with the selected file's absolute path.
     *
     * @param fileChooser The file chooser used to open the dialog.
     * @param primaryStage The primary stage of the application.
     * @param collectionFileField The text field to display the selected file's path.
     */
    public static void browseCollectionFile(FileChooser fileChooser, Stage primaryStage, TextField collectionFileField) {
        File tempFilePath = fileChooser.showOpenDialog(primaryStage);
        if (tempFilePath != null) {
            collectionFileField.setText(tempFilePath.getAbsolutePath());
            filePath = tempFilePath;
        }
    }

    /**
     * Load the collection file previously selected in the GUI.
     * The file's absolute path is stored in the static variable filePath.
     * The collection is loaded into the static variable myCardsCollection.
     * The static variable myCollectionIsLoaded is set to true.
     * @throws Exception
     */
    public static void loadCollectionFile() throws Exception {
        if (!myCollectionIsLoaded) {
            String filePathStr = filePath.getAbsolutePath();
            setMyCardsCollection(new OwnedCardsCollection(filePathStr));

            myCollectionIsLoaded = true;
        }
    }

    /**
     * Exports the collection to a directory specified by the user.
     * The directory is stored in the static variable outputPathLists.
     * The collection is exported to three different HTML files:
     *  - A complete list of all cards in the collection.
     *  - A list of all cards in the collection, grouped by category (deck, collection, etc.).
     *  - A mosaic of all cards in the collection.
     * @throws Exception
     */
    public static void exportCollectionFile() throws Exception {
        if (!myCollectionIsLoaded) {
            loadCollectionFile();
        }

        ensureCardPagesGenerated();

        Files.createDirectories(Paths.get(outputPathLists));

        OwnedCardsCollectionToHtml.generateListHtml(getMyCardsCollection(), outputPathLists, "Collection Complete List");
        OwnedCardsCollectionToHtml.generateCollectionAsListHtml(getMyCardsCollection(), outputPathLists, "Collection");
        OwnedCardsCollectionToHtml.generateCollectionAsMosaicHtml(getMyCardsCollection(), outputPathLists, "Collection");
    }

    /**
     * Opens a directory chooser dialog to select a directory containing decks and collections and updates
     * the provided text field with the selected directory's absolute path.
     *
     * @param folderChooser The directory chooser used to open the dialog.
     * @param primaryStage The primary stage of the application.
     * @param decksAndCollectionDirectoryField The text field to display the selected directory's path.
     */
    public static void browseDecksAndCollectionsDirectory(DirectoryChooser folderChooser, Stage primaryStage, TextField decksAndCollectionDirectoryField) {
        File tempFolderPath = folderChooser.showDialog(primaryStage);
        if (tempFolderPath != null) {
            decksAndCollectionDirectoryField.setText(tempFolderPath.getAbsolutePath());
            folderPath = tempFolderPath;
        }
    }

    /**
     * Loads the directory containing decks and collections into the application.
     *
     * The directory path is retrieved from the static variable `folderPath`, and
     * a new `DecksAndCollectionsList` object is created and set using this path.
     * The static variable `decksAndCollectionIsLoaded` is updated to true upon
     * successful loading.
     *
     * @throws Exception if an error occurs during directory loading or initialization
     */
    public static void loadDecksAndCollectionsDirectory() throws Exception {
        if (!decksAndCollectionIsLoaded) {
            String dirPath = folderPath.getAbsolutePath();
            setDecksList(new DecksAndCollectionsList(dirPath));
            decksAndCollectionIsLoaded = true;
        }
    }

    /**
     * Exports the decks and collections to a directory specified by the user.
     * The directory is stored in the static variable outputPathLists.
     * The decks and collections are exported to four different HTML files:
     *  - A list of all cards in all decks.
     *  - A menu of all decks, with links to each deck's list and mosaic HTML files.
     *  - A list of all cards in each deck.
     *  - A mosaic of all cards in each deck.
     * @throws Exception
     */
    public static void exportDecksAndCollectionsDirectory() throws Exception {
        if (!decksAndCollectionIsLoaded) {
            loadDecksAndCollectionsDirectory();
        }

        ensureCardPagesGenerated();

        List<CardElement> decksCardsList = new ArrayList<>();
        for (int i = 0; i < getDecksList().getDecks().size(); i++) {
            decksCardsList.addAll(getDecksList().getDecks().get(i).getMainDeck());
            decksCardsList.addAll(getDecksList().getDecks().get(i).getExtraDeck());
            decksCardsList.addAll(getDecksList().getDecks().get(i).getSideDeck());
        }

        generateHtml(decksCardsList, outputPathLists, "Decks List");

        DeckToHtml.generateDecksMenu(outputPath + "Decks\\", getDecksList().getDecks());
        for (int i = 0; i < getDecksList().getDecks().size(); i++) {
            DeckToHtml.generateDeckAsListHtml(getDecksList().getDecks().get(i), outputPath + "Decks\\", getDecksList().getDecks());
            DeckToHtml.generateDeckAsMosaicHtml(getDecksList().getDecks().get(i), outputPath + "Decks\\", getDecksList().getDecks());
        }
    }

    /**
     * Opens a file chooser dialog to select a file containing a third-party list of available cards
     * and updates the provided text field with the selected file's absolute path.
     *
     * @param fileChooser The file chooser used to open the dialog.
     * @param primaryStage The primary stage of the application.
     * @param thirdPartyAvailableCardsField The text field to display the selected file's path.
     */
    public static void browseThirdPartyAvailableCards(FileChooser fileChooser, Stage primaryStage, TextField thirdPartyAvailableCardsField) {
        thirdPartyListPath = fileChooser.showOpenDialog(primaryStage);
        if (thirdPartyListPath != null) {
            thirdPartyAvailableCardsField.setText(thirdPartyListPath.getAbsolutePath());
        }
    }

    /**
     * Opens a file chooser dialog to select a file containing the OuicheList
     * and updates the provided text field with the selected file's absolute path.
     *
     * @param fileChooser     The file chooser used to open the dialog.
     * @param primaryStage    The primary stage of the application.
     * @param ouicheListField The text field to display the selected file's path.
     */
    public static void browseOuicheList(FileChooser fileChooser, Stage primaryStage, TextField ouicheListField) {
        ouicheListPath = fileChooser.showOpenDialog(primaryStage);
        if (ouicheListPath != null) {
            ouicheListField.setText(ouicheListPath.getAbsolutePath());
        }
    }

    /**
     * Loads the third-party available cards list from the previously selected file.
     *
     * @throws Exception If the file could not be loaded.
     */
    public static void loadThirdPartyAvailableCards() throws Exception {
        String dirPath = thirdPartyListPath.getAbsolutePath();
        OuicheListIO.importThirdPartyList(dirPath);

        thirdPartyCollectionIsLoaded = true;
    }

    /**
     * Loads the OuicheList from the previously selected file.
     *
     * @throws Exception If the file could not be loaded.
     */
    public static void loadOuicheList() throws Exception {
        String dirPath = ouicheListPath.getAbsolutePath();
        OuicheListIO.importOuicheList(dirPath);

        ouicheListIsLoaded = true;
    }

    /**
     * Generates a list of third-party cards needed by comparing the OuicheList
     * with a third-party available cards list. If the third-party list or the OuicheList
     * is not already loaded, it attempts to load them from previously selected files.
     *
     * @throws Exception If an error occurs during loading the third-party or OuicheList files.
     */
    public static void generateThirdPartyList() throws Exception {
        if (!thirdPartyCollectionIsLoaded) {
            if (thirdPartyListPath == null) {
                //TODO fenêtre erreur :
                logger.warn("Third Party List must be selected");
            }
            loadThirdPartyAvailableCards();
        }

        if (!ouicheListIsLoaded) {
            if (ouicheListPath == null) {
                //TODO fenêtre erreur :
                logger.warn("OuicheList must be selected");
            } else {
                loadOuicheList();
            }
        }

        if (ouicheListIsLoaded) {
            OuicheListIO.generateThirdPartyCardsINeedList();
        }
    }

    /**
     * Saves the list of third-party cards needed to the file "3rdPartyList.txt" in the
     * directory specified by outputPath.
     *
     * @throws IOException If the file could not be created.
     */
    public static void saveThirdPartyList() throws IOException {
        OuicheListIO.thirdPartyCardsINeedListSave(outputPath, "3rdPartyList.txt");
    }

    /**
     * Generates the OuicheList by calling {@link #generateOuicheListFunction()} and
     * plays a laugh sound when finished.
     *
     * @throws RuntimeException If an exception occurs during generation.
     */
    public static void generateOuicheList() {
        try {
            Thread savePathsThread = new Thread(() -> savePathsToFile(filePath, folderPath, null));
            savePathsThread.start();

            generateOuicheListFunction();
            playLaughSound();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Saves the OuicheList to the file "OuicheList.txt" in the
     * directory specified by outputPath.
     *
     * @throws IOException If the file could not be created.
     */
    public static void saveOuicheList() throws IOException {
        if (!detailedOuicheListIsLoaded) {
            //TODO : fenêtre erreur ?
            logger.warn("OuicheList must be generated or loaded");
        } else {
            Files.createDirectories(Paths.get(outputPath));
            OuicheListIO.ouicheListSave(outputPath + "OuicheList.txt");
            clearOuicheListDirty();
            logger.info("OuicheList saved.");
        }
    }

    /**
     * Generates the OuicheList by calling {@link #generateOuicheListTypeFunction()} and
     * plays a laugh sound when finished.
     *
     * @throws RuntimeException If an exception occurs during generation.
     */
    public static void generateOuicheListType() {
        try {
            Thread savePathsThread = new Thread(() -> savePathsToFile(filePath, folderPath, null));
            savePathsThread.start();

            generateOuicheListTypeFunction();
            playLaughSound();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Generates the OuicheList by calling {@link Model.CardsLists.OuicheList#createOuicheList(OwnedCardsCollection, DecksAndCollectionsList)}
     * and generates several sublists of the OuicheList based on the different types of cards.
     *
     * @throws RuntimeException if an exception occurs during generation.
     */
    public static void generateOuicheListTypeFunction() {
        try {
            if (!myCollectionIsLoaded) {
                loadCollectionFile();
            }
            if (!decksAndCollectionIsLoaded) {
                loadDecksAndCollectionsDirectory();
            }

            // createOuicheList is now void and builds the internal LinkedHashMap directly.
            // All callers that need a List<CardElement> use getMaOuicheListAsFlatList().
            Model.CardsLists.OuicheList.createOuicheList(getMyCardsCollection(), getDecksList());

            ensureCardPagesGenerated();

            // Create the output directory.
            //String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Files.createDirectories(Paths.get(outputPathLists));

            generateHtml(getUnusedCards(), outputPathLists, "Available Cards");
            generateHtml(getMaOuicheListAsFlatList(), outputPathLists, "OuicheList");
            generateOuicheListMosaicHtml(getMaOuicheListAsFlatList(), outputPathLists);

            SubListCreator.CreateSubLists(getMaOuicheListAsFlatList());
            SubListCreator.CreateSubMonsterLists(SubListCreator.getMonsterList());
            SubListCreator.CreateSubSpellLists(SubListCreator.getSpellList());
            SubListCreator.CreateSubTrapLists(SubListCreator.getTrapList());

            generateHtml(SubListCreator.getMonsterList(), outputPathLists, "MonsterList");
            generateHtml(SubListCreator.getSpellList(), outputPathLists, "SpellList");
            generateHtml(SubListCreator.getTrapList(), outputPathLists, "TrapList");
            generateHtml(SubListCreator.getPyroTypeMonster(), outputPathLists, "PyroTypeMonster");
            generateHtml(SubListCreator.getAquaTypeMonster(), outputPathLists, "AquaTypeMonster");
            generateHtml(SubListCreator.getMachineTypeMonster(), outputPathLists, "MachineTypeMonster");
            generateHtml(SubListCreator.getDragonTypeMonster(), outputPathLists, "DragonTypeMonster");
            generateHtml(SubListCreator.getBeastWarriorTypeMonster(), outputPathLists, "BeastWarriorTypeMonster");
            generateHtml(SubListCreator.getReptileTypeMonster(), outputPathLists, "ReptileTypeMonster");
            generateHtml(SubListCreator.getPlantTypeMonster(), outputPathLists, "PlantTypeMonster");
            generateHtml(SubListCreator.getFiendTypeMonster(), outputPathLists, "FiendTypeMonster");
            generateHtml(SubListCreator.getWyrmTypeMonster(), outputPathLists, "WyrmTypeMonster");
            generateHtml(SubListCreator.getDinosaurTypeMonster(), outputPathLists, "DinosaurTypeMonster");
            generateHtml(SubListCreator.getSpellcasterTypeMonster(), outputPathLists, "SpellcasterTypeMonster");
            generateHtml(SubListCreator.getFishTypeMonster(), outputPathLists, "FishTypeMonster");
            generateHtml(SubListCreator.getDivineBeastTypeMonster(), outputPathLists, "DivineBeastTypeMonster");
            generateHtml(SubListCreator.getCyberseTypeMonster(), outputPathLists, "CyberseTypeMonster");
            generateHtml(SubListCreator.getInsectTypeMonster(), outputPathLists, "InsectTypeMonster");
            generateHtml(SubListCreator.getWingedBeastTypeMonster(), outputPathLists, "WingedBeastTypeMonster");
            generateHtml(SubListCreator.getWarriorTypeMonster(), outputPathLists, "WarriorTypeMonster");
            generateHtml(SubListCreator.getRockTypeMonster(), outputPathLists, "RockTypeMonster");
            generateHtml(SubListCreator.getThunderTypeMonster(), outputPathLists, "ThunderTypeMonster");
            generateHtml(SubListCreator.getZombieTypeMonster(), outputPathLists, "ZombieTypeMonster");
            generateHtml(SubListCreator.getSeaSerpentTypeMonster(), outputPathLists, "SeaSerpentTypeMonster");
            generateHtml(SubListCreator.getBeastTypeMonster(), outputPathLists, "BeastTypeMonster");
            generateHtml(SubListCreator.getPsychicTypeMonster(), outputPathLists, "PsychicTypeMonster");
            generateHtml(SubListCreator.getFairyTypeMonster(), outputPathLists, "FairyTypeMonster");
            generateHtml(SubListCreator.getIllusionTypeMonster(), outputPathLists, "IllusionTypeMonster");

            generateHtml(SubListCreator.getNormalMonsterCard(), outputPathLists, "NormalMonsterCard");
            generateHtml(SubListCreator.getToonMonsterCard(), outputPathLists, "ToonMonsterCard");
            generateHtml(SubListCreator.getTunerMonsterCard(), outputPathLists, "TunerMonsterCard");
            generateHtml(SubListCreator.getUnionMonsterCard(), outputPathLists, "UnionMonsterCard");
            generateHtml(SubListCreator.getSynchroMonsterCard(), outputPathLists, "SynchroMonsterCard");
            generateHtml(SubListCreator.getPendulumMonsterCard(), outputPathLists, "PendulumMonsterCard");
            generateHtml(SubListCreator.getRitualMonsterCard(), outputPathLists, "RitualMonsterCard");
            generateHtml(SubListCreator.getFlipMonsterCard(), outputPathLists, "FlipMonsterCard");
            generateHtml(SubListCreator.getSpiritMonsterCard(), outputPathLists, "SpiritMonsterCard");
            generateHtml(SubListCreator.getXyzMonsterCard(), outputPathLists, "XyzMonsterCard");
            generateHtml(SubListCreator.getEffectMonsterCard(), outputPathLists, "EffectMonsterCard");
            generateHtml(SubListCreator.getFusionMonsterCard(), outputPathLists, "FusionMonsterCard");
            generateHtml(SubListCreator.getLinkMonsterCard(), outputPathLists, "LinkMonsterCard");
            generateHtml(SubListCreator.getGeminiMonsterCard(), outputPathLists, "GeminiMonsterCard");

            generateHtml(SubListCreator.getNormalSpellCard(), outputPathLists, "NormalSpellCard");
            generateHtml(SubListCreator.getContinuousSpellCard(), outputPathLists, "ContinuousSpellCard");
            generateHtml(SubListCreator.getQuickPlaySpellCard(), outputPathLists, "QuickPlaySpellCard");
            generateHtml(SubListCreator.getEquipSpellCard(), outputPathLists, "EquipSpellCard");
            generateHtml(SubListCreator.getFieldSpellCard(), outputPathLists, "FieldSpellCard");
            generateHtml(SubListCreator.getRitualSpellCard(), outputPathLists, "RitualSpellCard");

            generateHtml(SubListCreator.getNormalTrapCard(), outputPathLists, "NormalTrapCard");
            generateHtml(SubListCreator.getContinuousTrapCard(), outputPathLists, "ContinuousTrapCard");
            generateHtml(SubListCreator.getCounterTrapCard(), outputPathLists, "CounterTrapCard");

            ouicheListIsLoaded = true;

            markOuicheListDirty();
            triggerTabDirtyIndicatorUpdate();
        } catch (IOException exception) {
            logger.error("generateOuicheListTypeFunction: failed to write generated HTML output", exception);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Register an explicit refresher to be called when the owned-collection view should refresh.
     * Example: OwnedCollectionController can register a lambda that calls its refresh method.
     */
    public static void registerOwnedCollectionRefresher(Runnable refresher) {
        if (refresher != null) {
            explicitRefreshers.addIfAbsent(refresher);
        }
    }

    // ── Decks and Collections refreshers (mirrors the owned-collection pattern) ──
    private static final CopyOnWriteArrayList<Runnable> explicitDecksRefreshers = new CopyOnWriteArrayList<>();

    // ── OuicheList refreshers ──────────────────────────────────────────────────
    private static final CopyOnWriteArrayList<Runnable> explicitOuicheListRefreshers = new CopyOnWriteArrayList<>();

    /**
     * Registers a callback that is invoked by {@link #refreshOuicheListView()} to
     * rebuild the OuicheList tab after an incremental model update. Duplicate
     * registrations are ignored.
     *
     * @param refresher the callback to register (ignored if {@code null})
     */
    public static void registerOuicheListRefresher(Runnable refresher) {
        if (refresher != null) {
            explicitOuicheListRefreshers.addIfAbsent(refresher);
        }
    }

    /**
     * Unregisters a previously registered OuicheList refresher.
     *
     * @param refresher the callback to unregister (ignored if {@code null})
     */
    public static void unregisterOuicheListRefresher(Runnable refresher) {
        if (refresher != null) {
            explicitOuicheListRefreshers.remove(refresher);
        }
    }

    /**
     * Triggers a rebuild of the OuicheList tab view on the JavaFX Application
     * Thread, calling every registered {@link #registerOuicheListRefresher refresher}.
     * Safe to call from any thread.
     */
    public static void refreshOuicheListView() {
        if (Platform.isFxApplicationThread()) {
            doRefreshOuicheListView();
        } else {
            Platform.runLater(UserInterfaceFunctions::doRefreshOuicheListView);
        }
    }

    private static void doRefreshOuicheListView() {
        for (Runnable refresher : explicitOuicheListRefreshers) {
            try {
                refresher.run();
            } catch (Throwable throwable) {
                logger.debug("refreshOuicheListView: refresher threw", throwable);
            }
        }
    }

    /**
     * Guard that prevents redundant archetype refreshes within the same FX frame.
     */
    private static volatile boolean archetypeRefreshScheduled = false;

    public static void triggerTabDirtyIndicatorUpdate() {
        if (tabDirtyIndicatorUpdater != null) {
            if (javafx.application.Platform.isFxApplicationThread()) {
                tabDirtyIndicatorUpdater.run();
            } else {
                javafx.application.Platform.runLater(tabDirtyIndicatorUpdater);
            }
        }
        // Every model modification calls this method, making it the single correct
        // place to keep the archetype markings (glow states) in sync.
        // We schedule via Platform.runLater so that multiple rapid modifications
        // (e.g. paste N cards) coalesce into one refresh at the end of the frame.
        if (!archetypeRefreshScheduled) {
            archetypeRefreshScheduled = true;
            javafx.application.Platform.runLater(() -> {
                archetypeRefreshScheduled = false;
                doRefreshArchetypesView();
            });
        }
    }

    public static void registerDecksTreeRefresher(Runnable refresher) {
        if (refresher != null) {
            decksTreeRefreshers.addIfAbsent(refresher);
        }
    }

    public static void unregisterDecksTreeRefresher(Runnable refresher) {
        if (refresher != null) {
            decksTreeRefreshers.remove(refresher);
        }
    }

    public static void registerArchetypesRefresher(Runnable refresher) {
        if (refresher != null) {
            explicitArchetypesRefreshers.addIfAbsent(refresher);
        }
    }

    public static void unregisterArchetypesRefresher(Runnable refresher) {
        if (refresher != null) {
            explicitArchetypesRefreshers.remove(refresher);
        }
    }

    /**
     * Refreshes the Archetypes tab view.  Safe to call from any thread.
     * Should be invoked whenever the decks/collections model or the owned
     * collection changes, so that archetype glow states stay in sync.
     */
    public static void refreshArchetypesView() {
        if (Platform.isFxApplicationThread()) {
            doRefreshArchetypesView();
        } else {
            Platform.runLater(UserInterfaceFunctions::doRefreshArchetypesView);
        }
    }

    private static void doRefreshArchetypesView() {
        // Re-render the D&C tree cells so archetype-card glow states update.
        // This is a lightweight .refresh() (no model rebuild) and is safe to call
        // at any time without losing selection or scroll position.
        for (Runnable runnable : decksTreeRefreshers) {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                logger.debug("doRefreshArchetypesView: decksTree refresher threw", throwable);
            }
        }
        for (Runnable runnable : explicitArchetypesRefreshers) {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                logger.debug("refreshArchetypesView: refresher threw", throwable);
            }
        }
    }

    public static void registerDecksCollectionsRefresher(Runnable refresher) {
        if (refresher != null) {
            explicitDecksRefreshers.addIfAbsent(refresher);
        }
    }

    public static void refreshDecksAndCollectionsView() {
        // IMPORTANT: always defer via Platform.runLater(), even when already on the FX thread.
        // doRefreshDecksAndCollectionsView() can trigger a full Decks & Collections tree
        // rebuild (DecksCollectionsController.displayDecksAndCollections()), which constructs
        // brand-new CardsGroup/TreeItem/cell instances for the whole tree. This method is most
        // often invoked from inside a drag-and-drop drop handler attached to a cell of that very
        // tree (see CardGridCell's wrapper.setOnDragDropped and CardTreeCell's
        // grid.setOnDragDropped). Running the rebuild synchronously there replaces the scene
        // graph nodes for the cell that is still mid-dispatch of the event — an unsafe JavaFX
        // pattern that caused intermittent state corruption (drops silently stopped reaching the
        // OuicheList after 1-2 successful drops). Deferring to a later pulse lets the originating
        // event finish dispatching on the still-valid scene graph before any rebuild begins.
        Platform.runLater(UserInterfaceFunctions::doRefreshDecksAndCollectionsView);
    }

    private static void doRefreshDecksAndCollectionsView() {
        // Any call to refreshDecksAndCollectionsView() means the D&C model changed.
        // Always request a full tree rebuild so that archetype missing-sets (and
        // therefore archetype-card glow states) recompute from the updated model.
        setPendingDecksFullRebuild();
        for (Runnable runnable : explicitDecksRefreshers) {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                logger.debug("refreshDecksAndCollectionsView: refresher threw", throwable);
            }
        }
    }

    /**
     * Unregister a previously registered refresher.
     */
    public static void unregisterOwnedCollectionRefresher(Runnable refresher) {
        if (refresher != null) {
            explicitRefreshers.remove(refresher);
        }
    }

    /**
     * Refresh the Owned Cards Collection view without persisting changes.
     * This method does not save the model; it only forces visible UI controls to refresh.
     */
    public static void refreshOwnedCollectionView() {
        if (Platform.isFxApplicationThread()) {
            doRefreshOwnedCollectionView();
        } else {
            Platform.runLater(UserInterfaceFunctions::doRefreshOwnedCollectionView);
        }
    }

    // Core implementation: tries explicit refreshers first, then falls back to scanning all windows.
    private static void doRefreshOwnedCollectionView() {
        try {
            // 1) Call any explicit registered refreshers first (preferred)
            if (!explicitRefreshers.isEmpty()) {
                for (Runnable refresher : explicitRefreshers) {
                    try {
                        refresher.run();
                        logger.debug("refreshOwnedCollectionView: called explicit refresher {}", refresher);
                    } catch (Throwable throwable) {
                        logger.debug("refreshOwnedCollectionView: explicit refresher threw", throwable);
                    }
                }
                // We still continue to scan and refresh controls to be safe.
            }

            // 2) Walk all open windows and refresh TreeView/ListView controls found
            boolean refreshedAny = false;
            for (Window window : Window.getWindows()) {
                try {
                    Scene scene = window.getScene();
                    if (scene == null) {
                        continue;
                    }
                    Parent root = scene.getRoot();
                    if (root == null) {
                        continue;
                    }
                    // BFS traversal to find controls
                    Deque<Node> pendingNodes = new ArrayDeque<>();
                    pendingNodes.add(root);
                    while (!pendingNodes.isEmpty()) {
                        Node node = pendingNodes.poll();
                        if (node == null) {
                            continue;
                        }

                        // Refresh TreeView
                        if (node instanceof TreeView) {
                            try {
                                ((TreeView<?>) node).refresh();
                                refreshedAny = true;
                            } catch (Throwable throwable) {
                                logger.debug("refreshOwnedCollectionView: TreeView.refresh() failed", throwable);
                            }
                        }

                        // Refresh ListView
                        if (node instanceof ListView) {
                            try {
                                ((ListView<?>) node).refresh();
                                refreshedAny = true;
                            } catch (Throwable throwable) {
                                logger.debug("refreshOwnedCollectionView: ListView.refresh() failed", throwable);
                            }
                        }

                        // If node is a Parent, enqueue children
                        if (node instanceof Parent) {
                            ObservableList<Node> children = ((Parent) node).getChildrenUnmodifiable();
                            if (children != null && !children.isEmpty()) {
                                for (Node child : children) {
                                    pendingNodes.add(child);
                                }
                            }
                        }
                    }
                } catch (Throwable throwable) {
                    logger.debug("refreshOwnedCollectionView: scanning window failed", throwable);
                }
            }

            // 3) If nothing was refreshed, log a hint for integration
            if (!refreshedAny) {
                logger.info("refreshOwnedCollectionView: no TreeView/ListView refreshed. " +
                        "If your owned-collection UI uses custom controls, register an explicit refresher via registerOwnedCollectionRefresher(...).");
            } else {
                logger.debug("refreshOwnedCollectionView: refreshed visible controls");
            }
        } catch (Throwable throwable) {
            logger.debug("refreshOwnedCollectionView failed", throwable);
        }
    }

    public static Object getAndClearPendingRenameTarget() {
        Object renameTarget = pendingRenameTarget;
        pendingRenameTarget = null;
        return renameTarget;
    }

    public static void registerOwnedCollectionStructureRefresher(Runnable refresher) {
        if (refresher != null) {
            explicitStructureRefreshers.addIfAbsent(refresher);
        }
    }

    /**
     * Triggers a full structural rebuild of the owned-collection view
     * (tree structure changed — boxes or categories were moved/added/removed).
     * Also triggers the normal view refresh so the nav menu stays in sync.
     */
    public static void refreshOwnedCollectionStructure() {
        if (Platform.isFxApplicationThread()) {
            doRefreshOwnedCollectionStructure();
        } else {
            Platform.runLater(UserInterfaceFunctions::doRefreshOwnedCollectionStructure);
        }
        // Always update the nav menu too
        refreshOwnedCollectionView();
    }

    private static void doRefreshOwnedCollectionStructure() {
        for (Runnable runnable : explicitStructureRefreshers) {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                logger.debug("refreshOwnedCollectionStructure: refresher threw", throwable);
            }
        }
    }

    /**
     * Fires the structural (full tree-rebuild) refreshers for the Decks &amp; Collections tab.
     * Use this instead of {@code refreshDecksAndCollectionsView()} when a section that was
     * previously empty has received its first card and its DataTreeItem doesn't exist yet.
     */
    public static void triggerDecksStructureRefresh() {
        // Signal a full structural rebuild so the refresher calls displayDecksAndCollections().
        setPendingDecksFullRebuild();
        // Always deferred — see the comment in refreshDecksAndCollectionsView() for why this
        // must never run synchronously inside an originating event handler.
        Platform.runLater(UserInterfaceFunctions::doRefreshDecksAndCollectionsView);
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
            clearDirty(deck);
            triggerTabDirtyIndicatorUpdate();
            // Rebuild the nav menu so the "* name" asterisk is cleared there too.
            refreshDecksAndCollectionsView();
            logger.info("Saved deck '{}'", deck.getName());

        } else if (obj instanceof Model.CardsLists.ThemeCollection themeCollection) {
            themeCollection.saveToFile(dir);
            clearDirty(themeCollection);
            // Also flush any dirty linked decks so the whole collection is consistent on disk.
            if (themeCollection.getLinkedDecks() != null) {
                for (java.util.List<Model.CardsLists.Deck> unit : themeCollection.getLinkedDecks()) {
                    if (unit == null) {
                        continue;
                    }
                    for (Model.CardsLists.Deck deck : unit) {
                        if (isDirty(deck)) {
                            deck.saveDeck(dir);
                            clearDirty(deck);
                            logger.info("Saved linked deck '{}' while saving collection '{}'",
                                    deck.getName(), themeCollection.getName());
                        }
                    }
                }
            }
            triggerTabDirtyIndicatorUpdate();
            // Rebuild the nav menu so the "* name" asterisk is cleared there too.
            refreshDecksAndCollectionsView();
            logger.info("Saved collection '{}'", themeCollection.getName());
        }
    }
}