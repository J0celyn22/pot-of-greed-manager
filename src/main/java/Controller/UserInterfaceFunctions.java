package Controller;

import Model.CardsLists.CardElement;
import Model.CardsLists.DecksAndCollectionsList;
import Model.CardsLists.OwnedCardsCollection;
import Model.CardsLists.SubListCreator;
import Model.FormatList.ArchetypesListsToHtml;
import Model.FormatList.DeckToHtml;
import Model.FormatList.OwnedCardsCollectionToHtml;
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
import static Model.FormatList.CardListToHtml.generateHtml;
import static Model.FormatList.CardListToHtml.generateHtmlWithOwned;
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
        } catch (IOException e) {
            // Handle exception if needed.

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
     * If there is an error writing to the file, it is not handled.
     * You can log an error message or handle it as needed.
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
                    //System.out.println("Parent directory was not created: " + parentDir.getAbsolutePath());
                }
            }

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                writer.write(filePath != null ? filePath.getAbsolutePath() : "");
                writer.newLine();
                writer.write(folderPath != null ? folderPath.getAbsolutePath() : "");
                writer.newLine();
                writer.write(thirdPartyListPath != null ? thirdPartyListPath.getAbsolutePath() : "");
            }
        } catch (IOException e) {
            // Handle any exceptions (e.g., write error)
            // You can log an error message or handle it as needed
            e.printStackTrace();
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
     * If there is an error writing to the file, it is not handled. You can
     * log an error message or handle it as needed.
     */
    public static void generateOuicheListFunction() {
        try {
            if (!myCollectionIsLoaded) {
                loadCollectionFile();
            }

            if (!decksAndCollectionIsLoaded) {
                loadDecksAndCollectionsDirectory();
            }

            setDetailedOuicheList(Model.CardsLists.OuicheList.CreateDetailedOuicheList(getMyCardsCollection(), getDecksList()));

            //String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Files.createDirectories(Paths.get(outputPathLists));

            generateOuicheListAsListHtml(getDetailedOuicheList(), outputPathLists, "Detailed OuicheList");
            generateOuicheListAsMosaicHtml(getDetailedOuicheList(), outputPathLists, "Detailed OuicheList");

            generateHtmlWithOwned(getUnusedCards(), outputPathLists, "Available Cards - Complete", true);
            generateHtmlWithOwned(getUnusedCards(), outputPathLists, "Available Cards", false);

            detailedOuicheListIsLoaded = true;

        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
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
        //Generate Archetypes lists from this list
        System.out.println("Generate Archetypes lists from the list of all cards, that is also automatically generated");
        CreateArchetypeLists(Model.Database.Database.getAllCardsList());

        //Generate HTML from these lists
        System.out.println("Generate HTML from these lists");
        String outputPathArchetypes = outputPath + "Archetypes\\";
        Files.createDirectories(Paths.get(outputPathArchetypes));

        // Create the Archetypes menu and then the individual archetype pages
        ArchetypesListsToHtml.generateArchetypesMenu(outputPathArchetypes, archetypesList);
        ArchetypesListsToHtml.GenerateAllArchetypesLists(outputPathArchetypes, archetypesList, archetypesCardsLists);

        System.out.println("Generation complete!");
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
        String dirPath = folderPath.getAbsolutePath();
        setDecksList(new DecksAndCollectionsList(dirPath));

        decksAndCollectionIsLoaded = true;
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
        importThirdPartyList(dirPath);

        thirdPartyCollectionIsLoaded = true;
    }

    /**
     * Loads the OuicheList from the previously selected file.
     *
     * @throws Exception If the file could not be loaded.
     */
    public static void loadOuicheList() throws Exception {
        String dirPath = ouicheListPath.getAbsolutePath();
        importOuicheList(dirPath);

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
                System.out.println("Third Party List must be selected");
            }
            loadThirdPartyAvailableCards();
        }

        if (!ouicheListIsLoaded) {
            if (ouicheListPath == null) {
                //TODO fenêtre erreur :
                System.out.println("OuicheList must be selected");
            } else {
                loadOuicheList();
            }
        }

        if (ouicheListIsLoaded) {
            generateThirdPartyCardsINeedList();
        }
    }

    /**
     * Saves the list of third-party cards needed to the file "3rdPartyList.txt" in the
     * directory specified by outputPath.
     *
     * @throws IOException If the file could not be created.
     */
    public static void saveThirdPartyList() throws IOException {
        thirdPartyCardsINeedListSave(outputPath, "3rdPartyList.txt");
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
        } catch (Exception ex) {
            throw new RuntimeException(ex);
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
            //TODO fenêtre erreur :
            System.out.println("OuicheList must be generated or loaded");
        } else {
            Files.createDirectories(Paths.get(outputPath));
            ouicheListSave(outputPath + "OuicheList.txt");
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
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Generates the OuicheList by calling {@link Model.CardsLists.OuicheList#CreateOuicheList(OwnedCardsCollection, DecksAndCollectionsList)}
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

            setMaOuicheList(Model.CardsLists.OuicheList.CreateOuicheList(getMyCardsCollection(), getDecksList()));

            // Create the output directory.
            //String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            Files.createDirectories(Paths.get(outputPathLists));

            generateHtml(getUnusedCards(), outputPathLists, "Available Cards");
            generateHtml(getMaOuicheList(), outputPathLists, "OuicheList");

            SubListCreator.CreateSubLists(getMaOuicheList());
            SubListCreator.CreateSubMonsterLists(SubListCreator.monsterList);
            SubListCreator.CreateSubSpellLists(SubListCreator.spellList);
            SubListCreator.CreateSubTrapLists(SubListCreator.trapList);

            generateHtml(SubListCreator.monsterList, outputPathLists, "MonsterList");
            generateHtml(SubListCreator.spellList, outputPathLists, "SpellList");
            generateHtml(SubListCreator.trapList, outputPathLists, "TrapList");
            generateHtml(SubListCreator.pyroTypeMonster, outputPathLists, "PyroTypeMonster");
            generateHtml(SubListCreator.aquaTypeMonster, outputPathLists, "AquaTypeMonster");
            generateHtml(SubListCreator.machineTypeMonster, outputPathLists, "MachineTypeMonster");
            generateHtml(SubListCreator.dragonTypeMonster, outputPathLists, "DragonTypeMonster");
            generateHtml(SubListCreator.beastWarriorTypeMonster, outputPathLists, "BeastWarriorTypeMonster");
            generateHtml(SubListCreator.reptileTypeMonster, outputPathLists, "ReptileTypeMonster");
            generateHtml(SubListCreator.plantTypeMonster, outputPathLists, "PlantTypeMonster");
            generateHtml(SubListCreator.fiendTypeMonster, outputPathLists, "FiendTypeMonster");
            generateHtml(SubListCreator.wyrmTypeMonster, outputPathLists, "WyrmTypeMonster");
            generateHtml(SubListCreator.dinosaurTypeMonster, outputPathLists, "DinosaurTypeMonster");
            generateHtml(SubListCreator.spellcasterTypeMonster, outputPathLists, "SpellcasterTypeMonster");
            generateHtml(SubListCreator.fishTypeMonster, outputPathLists, "FishTypeMonster");
            generateHtml(SubListCreator.divineBeastTypeMonster, outputPathLists, "DivineBeastTypeMonster");
            generateHtml(SubListCreator.cyberseTypeMonster, outputPathLists, "CyberseTypeMonster");
            generateHtml(SubListCreator.insectTypeMonster, outputPathLists, "InsectTypeMonster");
            generateHtml(SubListCreator.wingedBeastTypeMonster, outputPathLists, "WingedBeastTypeMonster");
            generateHtml(SubListCreator.warriorTypeMonster, outputPathLists, "WarriorTypeMonster");
            generateHtml(SubListCreator.rockTypeMonster, outputPathLists, "RockTypeMonster");
            generateHtml(SubListCreator.thunderTypeMonster, outputPathLists, "ThunderTypeMonster");
            generateHtml(SubListCreator.zombieTypeMonster, outputPathLists, "ZombieTypeMonster");
            generateHtml(SubListCreator.seaSerpentTypeMonster, outputPathLists, "SeaSerpentTypeMonster");
            generateHtml(SubListCreator.beastTypeMonster, outputPathLists, "BeastTypeMonster");
            generateHtml(SubListCreator.psychicTypeMonster, outputPathLists, "PsychicTypeMonster");
            generateHtml(SubListCreator.fairyTypeMonster, outputPathLists, "FairyTypeMonster");
            generateHtml(SubListCreator.illusionTypeMonster, outputPathLists, "IllusionTypeMonster");

            generateHtml(SubListCreator.normalMonsterCard, outputPathLists, "NormalMonsterCard");
            generateHtml(SubListCreator.toonMonsterCard, outputPathLists, "ToonMonsterCard");
            generateHtml(SubListCreator.tunerMonsterCard, outputPathLists, "TunerMonsterCard");
            generateHtml(SubListCreator.unionMonsterCard, outputPathLists, "UnionMonsterCard");
            generateHtml(SubListCreator.synchroMonsterCard, outputPathLists, "SynchroMonsterCard");
            generateHtml(SubListCreator.pendulumMonsterCard, outputPathLists, "PendulumMonsterCard");
            generateHtml(SubListCreator.ritualMonsterCard, outputPathLists, "RitualMonsterCard");
            generateHtml(SubListCreator.flipMonsterCard, outputPathLists, "FlipMonsterCard");
            generateHtml(SubListCreator.spiritMonsterCard, outputPathLists, "SpiritMonsterCard");
            generateHtml(SubListCreator.xyzMonsterCard, outputPathLists, "XyzMonsterCard");
            generateHtml(SubListCreator.effectMonsterCard, outputPathLists, "EffectMonsterCard");
            generateHtml(SubListCreator.fusionMonsterCard, outputPathLists, "FusionMonsterCard");
            generateHtml(SubListCreator.linkMonsterCard, outputPathLists, "LinkMonsterCard");
            generateHtml(SubListCreator.geminiMonsterCard, outputPathLists, "GeminiMonsterCard");

            generateHtml(SubListCreator.normalSpellCard, outputPathLists, "NormalSpellCard");
            generateHtml(SubListCreator.continuousSpellCard, outputPathLists, "ContinuousSpellCard");
            generateHtml(SubListCreator.quickPlaySpellCard, outputPathLists, "QuickPlaySpellCard");
            generateHtml(SubListCreator.equipSpellCard, outputPathLists, "EquipSpellCard");
            generateHtml(SubListCreator.fieldSpellCard, outputPathLists, "FieldSpellCard");
            generateHtml(SubListCreator.ritualSpellCard, outputPathLists, "RitualSpellCard");

            generateHtml(SubListCreator.normalTrapCard, outputPathLists, "NormalTrapCard");
            generateHtml(SubListCreator.continuousTrapCard, outputPathLists, "ContinuousTrapCard");
            generateHtml(SubListCreator.counterTrapCard, outputPathLists, "CounterTrapCard");

            ouicheListIsLoaded = true;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Register an explicit refresher to be called when the owned-collection view should refresh.
     * Example: OwnedCollectionController can register a lambda that calls its refresh method.
     */
    public static void registerOwnedCollectionRefresher(Runnable refresher) {
        if (refresher != null) explicitRefreshers.addIfAbsent(refresher);
    }

    /**
     * Unregister a previously registered refresher.
     */
    public static void unregisterOwnedCollectionRefresher(Runnable refresher) {
        if (refresher != null) explicitRefreshers.remove(refresher);
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
                for (Runnable r : explicitRefreshers) {
                    try {
                        r.run();
                        logger.debug("refreshOwnedCollectionView: called explicit refresher {}", r);
                    } catch (Throwable t) {
                        logger.debug("refreshOwnedCollectionView: explicit refresher threw", t);
                    }
                }
                // We still continue to scan and refresh controls to be safe.
            }

            // 2) Walk all open windows and refresh TreeView/ListView controls found
            boolean refreshedAny = false;
            for (Window w : Window.getWindows()) {
                try {
                    Scene s = w.getScene();
                    if (s == null) continue;
                    Parent root = s.getRoot();
                    if (root == null) continue;
                    // BFS traversal to find controls
                    Deque<Node> dq = new ArrayDeque<>();
                    dq.add(root);
                    while (!dq.isEmpty()) {
                        Node n = dq.poll();
                        if (n == null) continue;

                        // Refresh TreeView
                        if (n instanceof TreeView) {
                            try {
                                ((TreeView<?>) n).refresh();
                                refreshedAny = true;
                            } catch (Throwable t) {
                                logger.debug("refreshOwnedCollectionView: TreeView.refresh() failed", t);
                            }
                        }

                        // Refresh ListView
                        if (n instanceof ListView) {
                            try {
                                ((ListView<?>) n).refresh();
                                refreshedAny = true;
                            } catch (Throwable t) {
                                logger.debug("refreshOwnedCollectionView: ListView.refresh() failed", t);
                            }
                        }

                        // If node is a Parent, enqueue children
                        if (n instanceof Parent) {
                            ObservableList<Node> children = ((Parent) n).getChildrenUnmodifiable();
                            if (children != null && !children.isEmpty()) {
                                for (Node child : children) dq.add(child);
                            }
                        }
                    }
                } catch (Throwable t) {
                    logger.debug("refreshOwnedCollectionView: scanning window failed", t);
                }
            }

            // 3) If nothing was refreshed, log a hint for integration
            if (!refreshedAny) {
                logger.info("refreshOwnedCollectionView: no TreeView/ListView refreshed. " +
                        "If your owned-collection UI uses custom controls, register an explicit refresher via registerOwnedCollectionRefresher(...).");
            } else {
                logger.debug("refreshOwnedCollectionView: refreshed visible controls");
            }
        } catch (Throwable ex) {
            logger.debug("refreshOwnedCollectionView failed", ex);
        }
    }
}