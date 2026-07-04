package Controller;

import Model.CardsLists.OuicheListIO;
import Model.CardsLists.SubListCreator;
import Model.FormatList.ArchetypesListsToHtml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static Controller.CollectionFileIO.*;
import static Model.CardsLists.OuicheList.*;
import static Model.CardsLists.SubListCreator.*;
import static Model.FilePaths.outputPath;
import static Model.FilePaths.outputPathLists;
import static Model.FormatList.CardListToHtml.*;
import static Model.FormatList.OuicheListToHtml.generateOuicheListAsListHtml;
import static Model.FormatList.OuicheListToHtml.generateOuicheListAsMosaicHtml;

/**
 * Generates the OuicheList, its type-based sublists, the archetype lists, and the
 * third-party needed-cards list, and writes the corresponding HTML/text output files.
 */
public class OuicheListGenerationService {

    private static final Logger logger = LoggerFactory.getLogger(OuicheListGenerationService.class);

    private OuicheListGenerationService() {
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
                CollectionFileIO.loadCollectionFile();
            }

            if (!CollectionFileIO.isDecksAndCollectionLoaded()) {
                CollectionFileIO.loadDecksAndCollectionsDirectory();
            }

            CollectionStateTracker.ensureCardPagesGenerated();

            setDetailedOuicheList(Model.CardsLists.OuicheList.createDetailedOuicheList(getMyCardsCollection(), CollectionStateTracker.getDecksList()));

            Files.createDirectories(Paths.get(outputPathLists));

            generateOuicheListAsListHtml(getDetailedOuicheList(), outputPathLists, "Detailed OuicheList");
            generateOuicheListAsMosaicHtml(getDetailedOuicheList(), outputPathLists, "Detailed OuicheList");

            generateHtmlWithOwned(getUnusedCards(), outputPathLists, "Available Cards - Complete", true);
            generateHtmlWithOwned(getUnusedCards(), outputPathLists, "Available Cards", false);

            CollectionFileIO.setDetailedOuicheListLoaded(true);

            CollectionStateTracker.markOuicheListDirty();
            ViewRefreshCoordinator.triggerTabDirtyIndicatorUpdate();
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
     *
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
            CollectionFileIO.loadThirdPartyAvailableCards();
        }

        if (!CollectionFileIO.isOuicheListLoaded()) {
            if (ouicheListPath == null) {
                //TODO fenêtre erreur :
                logger.warn("OuicheList must be selected");
            } else {
                CollectionFileIO.loadOuicheList();
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
            Thread savePathsThread = new Thread(() -> CollectionFileIO.savePathsToFile(filePath, folderPath, null));
            savePathsThread.start();

            generateOuicheListFunction();
            UserInterfaceFunctions.playLaughSound();
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
            CollectionStateTracker.clearOuicheListDirty();
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
            Thread savePathsThread = new Thread(() -> CollectionFileIO.savePathsToFile(filePath, folderPath, null));
            savePathsThread.start();

            generateOuicheListTypeFunction();
            UserInterfaceFunctions.playLaughSound();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    /**
     * Generates the OuicheList by calling
     * {@link Model.CardsLists.OuicheList#createOuicheList(Model.CardsLists.OwnedCardsCollection, Model.CardsLists.DecksAndCollectionsList)}
     * and generates several sublists of the OuicheList based on the different types of cards.
     *
     * @throws RuntimeException if an exception occurs during generation.
     */
    public static void generateOuicheListTypeFunction() {
        try {
            if (!CollectionFileIO.isMyCollectionLoaded()) {
                CollectionFileIO.loadCollectionFile();
            }
            if (!CollectionFileIO.isDecksAndCollectionLoaded()) {
                CollectionFileIO.loadDecksAndCollectionsDirectory();
            }

            // createOuicheList is void and builds the internal LinkedHashMap directly.
            // All callers that need a List<CardElement> use getMaOuicheListAsFlatList().
            Model.CardsLists.OuicheList.createOuicheList(getMyCardsCollection(), CollectionStateTracker.getDecksList());

            CollectionStateTracker.ensureCardPagesGenerated();

            // Create the output directory.
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

            CollectionStateTracker.markOuicheListDirty();
            ViewRefreshCoordinator.triggerTabDirtyIndicatorUpdate();
        } catch (IOException exception) {
            logger.error("generateOuicheListTypeFunction: failed to write generated HTML output", exception);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}