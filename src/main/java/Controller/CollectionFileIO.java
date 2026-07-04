package Controller;

import Model.CardsLists.CardElement;
import Model.CardsLists.DecksAndCollectionsList;
import Model.CardsLists.OuicheListIO;
import Model.CardsLists.OwnedCardsCollection;
import Model.FormatList.DeckToHtml;
import Model.FormatList.OwnedCardsCollectionToHtml;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static Model.CardsLists.OuicheList.getMyCardsCollection;
import static Model.CardsLists.OuicheList.setMyCardsCollection;
import static Model.FilePaths.outputPath;
import static Model.FilePaths.outputPathLists;
import static Model.FormatList.CardListToHtml.generateHtml;

/**
 * Handles browsing for, loading, and exporting the four user-selected file
 * locations: the owned-collection save file, the decks-and-collections
 * directory, the third-party available-cards list, and the OuicheList.
 * <p>
 * Also tracks whether each of those has been loaded yet during the current
 * application session, since loading is expensive and several call sites
 * (browse/export actions, list-generation) need to trigger a load on demand
 * without repeating it.
 */
public class CollectionFileIO {

    private static final Logger logger = LoggerFactory.getLogger(CollectionFileIO.class);

    public static File filePath = null;
    public static File folderPath = null;
    public static File thirdPartyListPath = null;
    public static File ouicheListPath = null;

    private static Boolean myCollectionIsLoaded = false;
    private static Boolean decksAndCollectionIsLoaded = false;
    private static Boolean thirdPartyCollectionIsLoaded = false;
    private static Boolean ouicheListIsLoaded = false;
    private static Boolean detailedOuicheListIsLoaded = false;

    private CollectionFileIO() {
    }

    public static Boolean isMyCollectionLoaded() {
        return myCollectionIsLoaded;
    }

    public static void setMyCollectionLoaded(boolean loaded) {
        myCollectionIsLoaded = loaded;
    }

    public static Boolean isDecksAndCollectionLoaded() {
        return decksAndCollectionIsLoaded;
    }

    public static void setDecksAndCollectionLoaded(boolean loaded) {
        decksAndCollectionIsLoaded = loaded;
    }

    public static Boolean isThirdPartyCollectionLoaded() {
        return thirdPartyCollectionIsLoaded;
    }

    public static void setThirdPartyCollectionLoaded(boolean loaded) {
        thirdPartyCollectionIsLoaded = loaded;
    }

    /**
     * Returns the status of whether the Ouiche list is loaded.
     *
     * @return true if the Ouiche list is loaded, false otherwise.
     */
    public static Boolean isOuicheListLoaded() {
        return ouicheListIsLoaded;
    }

    public static void setOuicheListLoaded(boolean loaded) {
        ouicheListIsLoaded = loaded;
    }

    public static Boolean isDetailedOuicheListLoaded() {
        return detailedOuicheListIsLoaded;
    }

    public static void setDetailedOuicheListLoaded(boolean loaded) {
        detailedOuicheListIsLoaded = loaded;
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
     *
     * @param filePath           The path to the collection save file.
     * @param folderPath         The path to the folder containing all the decks.
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
     * Opens a file chooser dialog to select a collection file and updates
     * the provided text field with the selected file's absolute path.
     *
     * @param fileChooser         The file chooser used to open the dialog.
     * @param primaryStage        The primary stage of the application.
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
     *
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
     * - A complete list of all cards in the collection.
     * - A list of all cards in the collection, grouped by category (deck, collection, etc.).
     * - A mosaic of all cards in the collection.
     *
     * @throws Exception
     */
    public static void exportCollectionFile() throws Exception {
        if (!myCollectionIsLoaded) {
            loadCollectionFile();
        }

        CollectionStateTracker.ensureCardPagesGenerated();

        Files.createDirectories(Paths.get(outputPathLists));

        OwnedCardsCollectionToHtml.generateListHtml(getMyCardsCollection(), outputPathLists, "Collection Complete List");
        OwnedCardsCollectionToHtml.generateCollectionAsListHtml(getMyCardsCollection(), outputPathLists, "Collection");
        OwnedCardsCollectionToHtml.generateCollectionAsMosaicHtml(getMyCardsCollection(), outputPathLists, "Collection");
    }

    /**
     * Opens a directory chooser dialog to select a directory containing decks and collections and updates
     * the provided text field with the selected directory's absolute path.
     *
     * @param folderChooser                    The directory chooser used to open the dialog.
     * @param primaryStage                     The primary stage of the application.
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
     * <p>
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
            CollectionStateTracker.setDecksList(new DecksAndCollectionsList(dirPath));
            decksAndCollectionIsLoaded = true;
        }
    }

    /**
     * Exports the decks and collections to a directory specified by the user.
     * The directory is stored in the static variable outputPathLists.
     * The decks and collections are exported to four different HTML files:
     * - A list of all cards in all decks.
     * - A menu of all decks, with links to each deck's list and mosaic HTML files.
     * - A list of all cards in each deck.
     * - A mosaic of all cards in each deck.
     *
     * @throws Exception
     */
    public static void exportDecksAndCollectionsDirectory() throws Exception {
        if (!decksAndCollectionIsLoaded) {
            loadDecksAndCollectionsDirectory();
        }

        CollectionStateTracker.ensureCardPagesGenerated();

        DecksAndCollectionsList decksList = CollectionStateTracker.getDecksList();

        List<CardElement> decksCardsList = new ArrayList<>();
        for (int i = 0; i < decksList.getDecks().size(); i++) {
            decksCardsList.addAll(decksList.getDecks().get(i).getMainDeck());
            decksCardsList.addAll(decksList.getDecks().get(i).getExtraDeck());
            decksCardsList.addAll(decksList.getDecks().get(i).getSideDeck());
        }

        generateHtml(decksCardsList, outputPathLists, "Decks List");

        DeckToHtml.generateDecksMenu(outputPath + "Decks\\", decksList.getDecks());
        for (int i = 0; i < decksList.getDecks().size(); i++) {
            DeckToHtml.generateDeckAsListHtml(decksList.getDecks().get(i), outputPath + "Decks\\", decksList.getDecks());
            DeckToHtml.generateDeckAsMosaicHtml(decksList.getDecks().get(i), outputPath + "Decks\\", decksList.getDecks());
        }
    }

    /**
     * Opens a file chooser dialog to select a file containing a third-party list of available cards
     * and updates the provided text field with the selected file's absolute path.
     *
     * @param fileChooser                   The file chooser used to open the dialog.
     * @param primaryStage                  The primary stage of the application.
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
}