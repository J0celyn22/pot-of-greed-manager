package Controller;

import Model.CardsLists.*;
import Model.FormatList.DeckToHtml;
import Model.FormatList.OwnedCardsCollectionToHtml;
import javafx.scene.control.TextField;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static Model.CardsLists.OuicheList.*;
import static Model.CardsLists.SubListCreator.*;
import static Model.FormatList.ArchetypesListsToHtml.GenerateAllArchetypesLists;
import static Model.FormatList.CardListToHtml.generateHtml;
import static Model.FormatList.CardListToHtml.generateHtmlWithOwned;
import static Model.FormatList.OuicheListToHtml.generateOuicheListAsListHtml;
import static Model.FormatList.OuicheListToHtml.generateOuicheListAsMosaicHtml;

public class UserInterfaceFunctions {
    //public static HashMap<String, String> printCodeToKonamiId;
    public static File filePath = null;
    public static File folderPath = null;
    public static File thirdPartyListPath = null;
    public static File ouicheListPath = null;

    private static Boolean myCollectionIsLoaded = false;
    private static Boolean decksAndCollectionIsLoaded = false;
    private static Boolean thirdPartyCollectionIsLoaded = false;
    private static Boolean ouicheListIsLoaded = false;
    private static Boolean detailedOuicheListIsLoaded = false;

    //private static String dateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
    private static String outputPath = "..\\Output\\"/* + dateTime + "\\"*/;
    private static String outputPathLists = outputPath + "Lists\\";

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
                    folderPath = new File(line);
                }
                reader.close();
            }
        } catch (IOException e) {
            // Handle any exceptions (e.g., file not found, read error)
            // You can log an error message or use default paths here
        }
    }

    public static void savePathsToFile(File filePath, File folderPath, File thirdPartyListPath) {
        try {
            File file = new File("default_folders.txt");
            BufferedWriter writer = new BufferedWriter(new FileWriter(file));
            writer.write(filePath != null ? filePath.getAbsolutePath() : "");
            writer.newLine();
            writer.write(folderPath != null ? folderPath.getAbsolutePath() : "");
            writer.newLine();
            writer.write(thirdPartyListPath != null ? thirdPartyListPath.getAbsolutePath() : "");
            writer.close();
        } catch (IOException e) {
            // Handle any exceptions (e.g., write error)
            // You can log an error message or handle it as needed
        }
    }

    public static void playLaughSound() {
        // Load the audio file
        String audioFilePath = ".\\src\\main\\resources\\PotOfGreedLaugh.mp3"; // Adjust the path if needed
        Media media = new Media(new File(audioFilePath).toURI().toString());
        MediaPlayer mediaPlayer = new MediaPlayer(media);

        // Play the audio
        mediaPlayer.play();
    }

    public static void generateOuicheListFunction(File filePath, File folderPath) throws Exception {
        try {
            if(myCollectionIsLoaded == false) {
                loadCollectionFile();
            }

            if(decksAndCollectionIsLoaded == false) {
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

        } catch (
                IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void generateArchetypesListsFunction() throws Exception {
        //Generate Archetypes lists from this list
        System.out.println("Generate Archetypes lists from the list of all cards, that is also automatically generated");
        CreateArchetypeLists(Model.Database.Database.getAllCardsList());

        //Generate HTML from these lists
        System.out.println("Generate HTML from these lists");
        String outputPathArchetypes = outputPath + "Archetypes\\"/* + dateTime + "\\"*/;
        Files.createDirectories(Paths.get(outputPathArchetypes));
        GenerateAllArchetypesLists(outputPathArchetypes, archetypesList, archetypesCardsLists);

        System.out.println("Generation complete!");
    }

    // New functions for the events
    public static void browseCollectionFile(FileChooser fileChooser, Stage primaryStage, TextField collectionFileField) {
        File filePath = fileChooser.showOpenDialog(primaryStage);
        if (filePath != null) {
            collectionFileField.setText(filePath.getAbsolutePath());
        }
    }

    public static void loadCollectionFile() throws Exception {
        String filePathStr = filePath.getAbsolutePath();
        setMyCardsCollection(new OwnedCardsCollection(filePathStr));

        myCollectionIsLoaded = true;
    }

    /*public static void saveCollectionFile() throws Exception {
        getMyCardsCollection().SaveCollection(outputPath + "Collection.txt");
    }*/

    public static void exportCollectionFile() throws Exception {
        if(myCollectionIsLoaded == false) {
            loadCollectionFile();
        }

        List<CardElement> ownedCards = new ArrayList<>(); //TODO pas de création de liste ici mais utilisation de la fonction de génération HTML spécifique
        for (int i = 0; i < getMyCardsCollection().getOwnedCollection().size(); i++) {
            for (int j = 0; j < getMyCardsCollection().getOwnedCollection().get(i).getContent().size(); j++) {
                ownedCards.addAll(getMyCardsCollection().getOwnedCollection().get(i).getContent().get(j).cardList);
            }
        }

        Files.createDirectories(Paths.get(outputPathLists));

        OwnedCardsCollectionToHtml.generateListHtml(getMyCardsCollection(), outputPathLists, "Collection Complete List");
        OwnedCardsCollectionToHtml.generateCollectionAsListHtml(getMyCardsCollection(), outputPathLists, "Collection");
        OwnedCardsCollectionToHtml.generateCollectionAsMosaicHtml(getMyCardsCollection(), outputPathLists, "Collection");
    }

    public static void browseDecksAndCollectionsDirectory(DirectoryChooser folderChooser, Stage primaryStage, TextField decksAndCollectionDirectoryField) {
        File folderPath = folderChooser.showDialog(primaryStage);
        if (folderPath != null) {
            decksAndCollectionDirectoryField.setText(folderPath.getAbsolutePath());
        }
    }

    public static void loadDecksAndCollectionsDirectory() throws Exception {
        String dirPath = folderPath.getAbsolutePath();
        setDecksList(new DecksAndCollectionsList(dirPath));

        decksAndCollectionIsLoaded = true;
    }

    /*public static void saveDecksAndCollectionsDirectory() {
        // Empty function for now
        getDecksList().Save(outputPath + "Collection.txt");
    }*/

    public static void exportDecksAndCollectionsDirectory() throws Exception {
        if(decksAndCollectionIsLoaded == false) {
            loadDecksAndCollectionsDirectory();
        }

        List<CardElement> decksCardsList = new ArrayList<>();
        for (int i = 0; i < getDecksList().getDecks().size(); i++) {
            decksCardsList.addAll(getDecksList().getDecks().get(i).getMainDeck());
            decksCardsList.addAll(getDecksList().getDecks().get(i).getExtraDeck());
            decksCardsList.addAll(getDecksList().getDecks().get(i).getSideDeck());
        }

        generateHtml(decksCardsList, outputPathLists , "Decks List");

        DeckToHtml.generateDecksMenu(outputPath + "Decks\\", getDecksList().getDecks());
        for (int i = 0; i < getDecksList().getDecks().size(); i++) {
            DeckToHtml.generateDeckAsListHtml(getDecksList().getDecks().get(i), outputPath + "Decks\\", getDecksList().getDecks());
            DeckToHtml.generateDeckAsMosaicHtml(getDecksList().getDecks().get(i), outputPath + "Decks\\", getDecksList().getDecks());
        }
    }

    public static void browseThirdPartyAvailableCards(FileChooser fileChooser, Stage primaryStage, TextField thirdPartyAvailableCardsField) {
        thirdPartyListPath = fileChooser.showOpenDialog(primaryStage);
        if (thirdPartyListPath != null) {
            thirdPartyAvailableCardsField.setText(filePath.getAbsolutePath());
        }
    }

    public static void browseOuicheList(FileChooser fileChooser, Stage primaryStage, TextField ouicheListField) {
        ouicheListPath = fileChooser.showOpenDialog(primaryStage);
        if (ouicheListPath != null) {
            ouicheListField.setText(filePath.getAbsolutePath());
        }
    }

    public static void loadThirdPartyAvailableCards() throws Exception {
        String dirPath = thirdPartyListPath.getAbsolutePath();
        importThirdPartyList(dirPath);

        thirdPartyCollectionIsLoaded = true;
    }

    public static void loadOuicheList() throws Exception {
        String dirPath = ouicheListPath.getAbsolutePath();
        importOuicheList(dirPath);
        //outputPath + "OuicheList.html"

        ouicheListIsLoaded = true;
    }

    public static void generateThirdPartyList() throws Exception {
        if(thirdPartyCollectionIsLoaded == false) {
            if(thirdPartyListPath == null) {
                //TODO fenêtre erreur :
                System.out.println("Third Party List must be selected");
            }
            loadThirdPartyAvailableCards();
        }

        if(ouicheListIsLoaded == false) {
            if(ouicheListPath == null) {
                //TODO fenêtre erreur :
                System.out.println("OuicheList must be selected");
            }
            loadOuicheList();
        }


        generateThirdPartyCardsINeedList();
    }

    public static void saveThirdPartyList() throws IOException {
        thirdPartyCardsINeedListSave(outputPath, "3rdPartyList.txt");
    }

    public static void generateOuicheList() {
        try {
            Thread savePathsThread = new Thread(() -> savePathsToFile(filePath, folderPath, null));
            savePathsThread.start();

            generateOuicheListFunction(filePath, folderPath);
            playLaughSound();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void saveOuicheList() throws IOException {
        if(detailedOuicheListIsLoaded == false) {
            //TODO fenêtre erreur :
            System.out.println("OuicheList must be generated or loaded");
        }
        else {
            Files.createDirectories(Paths.get(outputPath));
            ouicheListSave(outputPath + "OuicheList.txt");
        }
    }

    public static void generateOuicheListType() {
        try {
            Thread savePathsThread = new Thread(() -> savePathsToFile(filePath, folderPath, null));
            savePathsThread.start();

            generateOuicheListTypeFunction(filePath, folderPath);
            playLaughSound();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static void generateOuicheListTypeFunction(File filePath, File folderPath) throws Exception {
        try {
            if(myCollectionIsLoaded == false) {
                loadCollectionFile();
            }

            if(decksAndCollectionIsLoaded == false) {
                loadDecksAndCollectionsDirectory();
            }

            setMaOuicheList(Model.CardsLists.OuicheList.CreateOuicheList(getMyCardsCollection(), getDecksList()));

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

            generateHtml(SubListCreator. normalSpellCard, outputPathLists, "NormalSpellCard");
            generateHtml(SubListCreator. continuousSpellCard, outputPathLists, "ContinuousSpellCard");
            generateHtml(SubListCreator. quickPlaySpellCard, outputPathLists, "QuickPlaySpellCard");
            generateHtml(SubListCreator. equipSpellCard, outputPathLists, "EquipSpellCard");
            generateHtml(SubListCreator. fieldSpellCard, outputPathLists, "FieldSpellCard");
            generateHtml(SubListCreator. ritualSpellCard, outputPathLists, "RitualSpellCard");

            generateHtml(SubListCreator.normalTrapCard, outputPathLists, "NormalTrapCard");
            generateHtml(SubListCreator.continuousTrapCard, outputPathLists, "ContinuousTrapCard");
            generateHtml(SubListCreator.counterTrapCard, outputPathLists, "CounterTrapCard");

            ouicheListIsLoaded = true;
        } catch (
                IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}