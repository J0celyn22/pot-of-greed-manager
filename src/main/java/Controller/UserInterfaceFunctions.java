package Controller;

import Model.CardsLists.DecksAndCollectionsList;
import Model.CardsLists.OuicheListIO;
import Model.CardsLists.OwnedCardsCollection;
import Model.CardsLists.SubListCreator;
import Model.FormatList.ArchetypesListsToHtml;
import javafx.scene.control.TextField;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static Controller.CollectionFileIO.*;
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

    public static DecksAndCollectionsList getDecksList() {
        return CollectionStateTracker.getDecksList();
    }

    private static final Logger logger = LoggerFactory.getLogger(UserInterfaceFunctions.class);
    // ── Pending UI-action state ─────────────────────────────────────────────────
    // Delegates to PendingUiActionState; kept here so existing call sites are unaffected.

    public static void setPendingDecksFullRebuild() {
        PendingUiActionState.setPendingDecksFullRebuild();
    }

    public static void setPendingDecksExpandTarget(Object target) {
        PendingUiActionState.setPendingDecksExpandTarget(target);
    }

    public static Object getAndClearPendingDecksExpandTarget() {
        return PendingUiActionState.getAndClearPendingDecksExpandTarget();
    }

    public static boolean getAndClearPendingDecksFullRebuild() {
        return PendingUiActionState.getAndClearPendingDecksFullRebuild();
    }

    public static void setPendingDecksScrollTarget(Object target) {
        PendingUiActionState.setPendingDecksScrollTarget(target);
    }

    public static Object getAndClearPendingDecksScrollTarget() {
        return PendingUiActionState.getAndClearPendingDecksScrollTarget();
    }

    public static void setPendingDecksRenameTarget(Object target) {
        PendingUiActionState.setPendingDecksRenameTarget(target);
    }

    public static void setPendingRenameTarget(Object target) {
        PendingUiActionState.setPendingRenameTarget(target);
    }

    public static Object getAndClearPendingDecksRenameTarget() {
        return PendingUiActionState.getAndClearPendingDecksRenameTarget();
    }

    public static void setPendingDecksCreateCollectionData(Object[] data) {
        PendingUiActionState.setPendingDecksCreateCollectionData(data);
    }

    public static Object[] getAndClearPendingDecksCreateCollectionData() {
        return PendingUiActionState.getAndClearPendingDecksCreateCollectionData();
    }

    // ── Dirty tracking ────────────────────────────────────────────────────────────
    // Delegates to CollectionStateTracker; kept here so existing call sites are unaffected.

    public static void markDirty(Object deckOrCollection) {
        CollectionStateTracker.markDirty(deckOrCollection);
    }

    public static void markMyCollectionDirty() {
        CollectionStateTracker.markMyCollectionDirty();
    }

    public static boolean isDirty(Object deckOrCollection) {
        return CollectionStateTracker.isDirty(deckOrCollection);
    }

    public static boolean isMyCollectionDirty() {
        return CollectionStateTracker.isMyCollectionDirty();
    }

    public static boolean isAnyDeckOrCollectionDirty() {
        return CollectionStateTracker.isAnyDeckOrCollectionDirty();
    }

    public static void clearDirty(Object obj) {
        CollectionStateTracker.clearDirty(obj);
    }

    public static void clearAllDirtyDecksAndCollections() {
        CollectionStateTracker.clearAllDirtyDecksAndCollections();
    }

    public static void clearMyCollectionDirty() {
        CollectionStateTracker.clearMyCollectionDirty();
    }

    public static void markOuicheListDirty() {
        CollectionStateTracker.markOuicheListDirty();
    }

    public static boolean isOuicheListDirty() {
        return CollectionStateTracker.isOuicheListDirty();
    }

    public static void clearOuicheListDirty() {
        CollectionStateTracker.clearOuicheListDirty();
    }

    // ── Save methods ──────────────────────────────────────────────────────────────

    private static void ensureCardPagesGenerated() {
        CollectionStateTracker.ensureCardPagesGenerated();
    }

    /**
     * Clears the card-pages-generated flag so that the next export action
     * triggers a fresh full-database HTML export pass.
     * Call this whenever {@link Model.FilePaths#outputPath} changes.
     */
    public static void resetCardPagesGenerated() {
        CollectionStateTracker.resetCardPagesGenerated();
    }

    /**
     * Marks every deck and collection in the currently loaded Decks and Collections List as dirty.
     */
    public static void markAllDecksAndCollectionsDirty() {
        CollectionStateTracker.markAllDecksAndCollectionsDirty();
    }

    public static void saveMyCollection() throws Exception {
        if (!isMyCollectionDirty()) {
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
        ViewRefreshCoordinator.registerTabDirtyIndicatorUpdater(updater);
    }

    // Setter and getter for decksList.
    public static void setDecksList(DecksAndCollectionsList list) {
        CollectionStateTracker.setDecksList(list);
    }

    /**
     * Returns the status of whether the Ouiche list is loaded.
     *
     * @return true if the Ouiche list is loaded, false otherwise.
     */
    public static Boolean getOuicheListIsLoaded() {
        return CollectionFileIO.isOuicheListLoaded();
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
        CollectionFileIO.readPathsFromFile();
    }

    /**
     * @param filePath The path to the collection save file.
     * @param folderPath The path to the folder containing all the decks.
     * @param thirdPartyListPath The path to the third party list.
     */
    public static void savePathsToFile(File filePath, File folderPath, File thirdPartyListPath) {
        CollectionFileIO.savePathsToFile(filePath, folderPath, thirdPartyListPath);
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
            if (!CollectionFileIO.isMyCollectionLoaded()) {
                loadCollectionFile();
            }

            if (!CollectionFileIO.isDecksAndCollectionLoaded()) {
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

            CollectionFileIO.setDetailedOuicheListLoaded(true);

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

    // ── File browse/load/export ──────────────────────────────────────────────────
    // Delegates to CollectionFileIO; kept here so existing call sites are unaffected.

    public static void browseCollectionFile(FileChooser fileChooser, Stage primaryStage, TextField collectionFileField) {
        CollectionFileIO.browseCollectionFile(fileChooser, primaryStage, collectionFileField);
    }

    public static void loadCollectionFile() throws Exception {
        CollectionFileIO.loadCollectionFile();
    }

    public static void exportCollectionFile() throws Exception {
        CollectionFileIO.exportCollectionFile();
    }

    public static void browseDecksAndCollectionsDirectory(DirectoryChooser folderChooser, Stage primaryStage, TextField decksAndCollectionDirectoryField) {
        CollectionFileIO.browseDecksAndCollectionsDirectory(folderChooser, primaryStage, decksAndCollectionDirectoryField);
    }

    public static void loadDecksAndCollectionsDirectory() throws Exception {
        CollectionFileIO.loadDecksAndCollectionsDirectory();
    }

    public static void exportDecksAndCollectionsDirectory() throws Exception {
        CollectionFileIO.exportDecksAndCollectionsDirectory();
    }

    public static void browseThirdPartyAvailableCards(FileChooser fileChooser, Stage primaryStage, TextField thirdPartyAvailableCardsField) {
        CollectionFileIO.browseThirdPartyAvailableCards(fileChooser, primaryStage, thirdPartyAvailableCardsField);
    }

    public static void browseOuicheList(FileChooser fileChooser, Stage primaryStage, TextField ouicheListField) {
        CollectionFileIO.browseOuicheList(fileChooser, primaryStage, ouicheListField);
    }

    public static void loadThirdPartyAvailableCards() throws Exception {
        CollectionFileIO.loadThirdPartyAvailableCards();
    }

    public static void loadOuicheList() throws Exception {
        CollectionFileIO.loadOuicheList();
    }

    /**
     * Generates a list of third-party cards needed by comparing the OuicheList
     * with a third-party available cards list. If the third-party list or the OuicheList
     * is not already loaded, it attempts to load them from previously selected files.
     *
     * @throws Exception If an error occurs during loading the third-party or OuicheList files.
     */
    public static void generateThirdPartyList() throws Exception {
        if (!CollectionFileIO.isThirdPartyCollectionLoaded()) {
            if (thirdPartyListPath == null) {
                //TODO fenêtre erreur :
                logger.warn("Third Party List must be selected");
            }
            loadThirdPartyAvailableCards();
        }

        if (!CollectionFileIO.isOuicheListLoaded()) {
            if (ouicheListPath == null) {
                //TODO fenêtre erreur :
                logger.warn("OuicheList must be selected");
            } else {
                loadOuicheList();
            }
        }

        if (CollectionFileIO.isOuicheListLoaded()) {
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
        if (!CollectionFileIO.isDetailedOuicheListLoaded()) {
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
            if (!CollectionFileIO.isMyCollectionLoaded()) {
                loadCollectionFile();
            }
            if (!CollectionFileIO.isDecksAndCollectionLoaded()) {
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

            CollectionFileIO.setOuicheListLoaded(true);

            markOuicheListDirty();
            triggerTabDirtyIndicatorUpdate();
        } catch (IOException exception) {
            logger.error("generateOuicheListTypeFunction: failed to write generated HTML output", exception);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
    // ── View-refresh coordination ────────────────────────────────────────────────
    // Delegates to ViewRefreshCoordinator; kept here so existing call sites are unaffected.

    /**
     * Plays the Pot of Greed's laugh sound effect.
     * <p>
     * The sound effect is loaded from the file "PotOfGreedLaugh.mp3" in the resources
     * directory. If the file does not exist, the method does nothing.
     */
    public static void playLaughSound() {
        String audioFilePath = ".\\src\\main\\resources\\PotOfGreedLaugh.mp3";
        if (!new File(audioFilePath).exists()) {
            audioFilePath = "resources\\PotOfGreedLaugh.mp3";
        }
        Media media = new Media(new File(audioFilePath).toURI().toString());
        MediaPlayer mediaPlayer = new MediaPlayer(media);

        // Play the audio
        mediaPlayer.play();
    }

    public static void registerOwnedCollectionRefresher(Runnable refresher) {
        ViewRefreshCoordinator.registerOwnedCollectionRefresher(refresher);
    }

    public static void registerOuicheListRefresher(Runnable refresher) {
        ViewRefreshCoordinator.registerOuicheListRefresher(refresher);
    }

    public static void unregisterOuicheListRefresher(Runnable refresher) {
        ViewRefreshCoordinator.unregisterOuicheListRefresher(refresher);
    }

    public static void refreshOuicheListView() {
        ViewRefreshCoordinator.refreshOuicheListView();
    }

    public static void triggerTabDirtyIndicatorUpdate() {
        ViewRefreshCoordinator.triggerTabDirtyIndicatorUpdate();
    }

    public static void registerDecksTreeRefresher(Runnable refresher) {
        ViewRefreshCoordinator.registerDecksTreeRefresher(refresher);
    }

    public static void unregisterDecksTreeRefresher(Runnable refresher) {
        ViewRefreshCoordinator.unregisterDecksTreeRefresher(refresher);
    }

    public static void registerArchetypesRefresher(Runnable refresher) {
        ViewRefreshCoordinator.registerArchetypesRefresher(refresher);
    }

    public static void unregisterArchetypesRefresher(Runnable refresher) {
        ViewRefreshCoordinator.unregisterArchetypesRefresher(refresher);
    }

    public static void refreshArchetypesView() {
        ViewRefreshCoordinator.refreshArchetypesView();
    }

    public static void registerDecksCollectionsRefresher(Runnable refresher) {
        ViewRefreshCoordinator.registerDecksCollectionsRefresher(refresher);
    }

    public static void refreshDecksAndCollectionsView() {
        ViewRefreshCoordinator.refreshDecksAndCollectionsView();
    }

    public static void unregisterOwnedCollectionRefresher(Runnable refresher) {
        ViewRefreshCoordinator.unregisterOwnedCollectionRefresher(refresher);
    }

    public static void refreshOwnedCollectionView() {
        ViewRefreshCoordinator.refreshOwnedCollectionView();
    }

    public static Object getAndClearPendingRenameTarget() {
        return PendingUiActionState.getAndClearPendingRenameTarget();
    }

    public static void registerOwnedCollectionStructureRefresher(Runnable refresher) {
        ViewRefreshCoordinator.registerOwnedCollectionStructureRefresher(refresher);
    }

    public static void refreshOwnedCollectionStructure() {
        ViewRefreshCoordinator.refreshOwnedCollectionStructure();
    }

    public static void triggerDecksStructureRefresh() {
        ViewRefreshCoordinator.triggerDecksStructureRefresh();
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