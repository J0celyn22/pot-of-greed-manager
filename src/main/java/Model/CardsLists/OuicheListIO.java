package Model.CardsLists;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static Model.CardsLists.ListDifferenceIntersection.ListDifIntersectKonamiId;
import static Model.CardsLists.ListDifferenceIntersection.ListDifIntersectPrintcode;

/**
 * File I/O operations for the OuicheList: importing and saving the detailed
 * OuicheList, the unused-cards list, and the third-party cards list.
 *
 * <p>All methods read from and write to {@link OuicheList}'s static state via
 * its public getters and setters.  No computation logic lives here — that
 * belongs to {@link OuicheList} itself.
 */
public final class OuicheListIO {

    private static final Logger logger = LoggerFactory.getLogger(OuicheListIO.class);

    private OuicheListIO() { /* static utility */ }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Imports the detailed OuicheList from a file.
     *
     * <p>The file is expected to be a text file with the following structure:
     * <ul>
     *     <li>Lines starting with {@code "==="} mark a new collection; the text
     *         after {@code "==="} is used as the collection name.</li>
     *     <li>Lines starting with {@code "---"} mark a new deck or the
     *         collection's card list ({@code "---Collection"}); the text after
     *         {@code "---"} is used as the deck name.</li>
     *     <li>All other non-empty lines are card lines parsed as
     *         {@link CardElement}s. Only {@link OwnershipStatus#MISSING} cards
     *         are added to the compact map.</li>
     * </ul>
     *
     * @param filePath the path to the file to import
     * @throws Exception if the file cannot be read or parsed
     */
    public static void importOuicheList(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path);

        DecksAndCollectionsList detailedOuicheList = new DecksAndCollectionsList();
        LinkedHashMap<String, CardElement> maOuicheList = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> maOuicheListCounts = new LinkedHashMap<>();
        LinkedHashMap<String, CardElement> maOuicheListSubstandard = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> maOuicheListSubstandardCounts = new LinkedHashMap<>();

        ThemeCollection currentCollection = null;
        Deck currentDeck = null;
        boolean isDecksSection = false;

        for (String line : lines) {
            if (line.startsWith("===")) {
                if (currentCollection != null) {
                    detailedOuicheList.addCollection(currentCollection);
                }
                currentCollection = new ThemeCollection();
                currentCollection.setName(line.substring(3).trim());
                isDecksSection = false;
            } else if (line.startsWith("---")) {
                if (line.equals("---Collection")) {
                    currentDeck = null;
                } else {
                    currentDeck = new Deck();
                    currentDeck.setName(line.substring(3).trim());
                    if (currentCollection != null) {
                        currentCollection.addDeck(currentDeck);
                    } else {
                        detailedOuicheList.addDeck(currentDeck);
                    }
                }
            } else if (line.startsWith("===") && line.contains("Decks")) {
                isDecksSection = true;
            } else {
                CardElement card = new CardElement(line.trim());
                if (card.getOwnershipStatus() == OwnershipStatus.MISSING) {
                    if (isDecksSection) {
                        if (currentDeck != null) {
                            currentDeck.AddCardMain(card);
                            String key = OuicheList.cardKey(card);
                            if (key != null) {
                                maOuicheList.putIfAbsent(key, card);
                                maOuicheListCounts.merge(key, 1, Integer::sum);
                            }
                        }
                    } else {
                        if (currentDeck != null) {
                            currentDeck.AddCardMain(card);
                            String key = OuicheList.cardKey(card);
                            if (key != null) {
                                maOuicheList.putIfAbsent(key, card);
                                maOuicheListCounts.merge(key, 1, Integer::sum);
                            }
                        } else if (currentCollection != null) {
                            currentCollection.getCardsList().add(card);
                            String key = OuicheList.cardKey(card);
                            if (key != null) {
                                maOuicheList.putIfAbsent(key, card);
                                maOuicheListCounts.merge(key, 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }

        if (currentCollection != null) {
            detailedOuicheList.addCollection(currentCollection);
        }

        OuicheList.setDetailedOuicheList(detailedOuicheList);
        OuicheList.setMaOuicheList(maOuicheList);
        OuicheList.setMaOuicheListCounts(maOuicheListCounts);
        OuicheList.setMaOuicheListSubstandard(maOuicheListSubstandard);
        OuicheList.setMaOuicheListSubstandardCounts(maOuicheListSubstandardCounts);
    }

    /**
     * Imports a third-party available cards list from the given file.
     *
     * <p>Lines starting with {@code "="}, {@code "-"}, or {@code "#"}, and
     * empty lines, are ignored.  All other lines are parsed as
     * {@link CardElement}s and stored in {@link OuicheList#getThirdPartyList()}.
     *
     * @param filePath the path to the file to import
     * @throws Exception if the file cannot be read
     */
    public static void importThirdPartyList(String filePath) throws Exception {
        Path path = Paths.get(filePath);
        List<String> lines = Files.readAllLines(path);

        List<CardElement> thirdPartyList = new ArrayList<>();
        for (String line : lines) {
            if (!line.startsWith("=")
                    && !line.startsWith("-")
                    && !line.startsWith("#")
                    && !line.isEmpty()) {
                thirdPartyList.add(new CardElement(line.trim()));
            }
        }
        OuicheList.setThirdPartyList(thirdPartyList);
    }

    // ── Derived lists ─────────────────────────────────────────────────────────

    /**
     * Builds the list of third-party cards that the user still needs, by
     * computing the intersection of the third-party available list with the
     * compact OuicheList (first by print code, then by Konami ID).
     *
     * <p>The result is stored in {@link OuicheList#getThirdPartyCardsINeedList()}.
     */
    public static void generateThirdPartyCardsINeedList() {
        List<CardElement> flatOuicheList = OuicheList.getMaOuicheListAsFlatList();
        List<List<CardElement>> byPrintCode =
                ListDifIntersectPrintcode(OuicheList.getThirdPartyList(), flatOuicheList);

        List<CardElement> thirdPartyRemainder = byPrintCode.get(0);
        List<CardElement> ouicheListRemainder = byPrintCode.get(1);
        List<CardElement> needed = new ArrayList<>(byPrintCode.get(2));

        List<List<CardElement>> byKonamiId =
                ListDifIntersectKonamiId(thirdPartyRemainder, ouicheListRemainder);
        needed.addAll(byKonamiId.get(2));

        OuicheList.setThirdPartyCardsINeedList(needed);
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    /**
     * Saves the detailed OuicheList to a file.
     *
     * <p>File format: collections are separated by {@code "===Name========"} headers;
     * deck sections within a collection by {@code "---DeckName------------"} headers;
     * the collection's own card list by a {@code "---Collection----------"} header;
     * standalone decks by a {@code "===Decks==============="} header followed by
     * per-deck {@code "---DeckName-------"} headers.
     *
     * <p>The file is created if it does not exist and overwritten if it does.
     *
     * @param filePath the path of the file to write
     * @throws IOException if the file cannot be created or written
     */
    public static void ouicheListSave(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            boolean created = file.createNewFile();
            if (!created) {
                throw new IOException("File was not created: " + filePath);
            }
        }

        DecksAndCollectionsList detailedOuicheList = OuicheList.getDetailedOuicheList();
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {

            for (ThemeCollection collection : detailedOuicheList.getCollections()) {
                writer.write("===" + collection.getName() + "========");
                writer.newLine();
                for (int groupIndex = 0; groupIndex < collection.getLinkedDecks().size(); groupIndex++) {
                    for (Deck deck : collection.getLinkedDecks().get(groupIndex)) {
                        writer.write("---" + deck.getName() + "------------");
                        writer.newLine();
                        for (CardElement card : deck.toList()) {
                            writer.write(card.toString());
                            writer.newLine();
                        }
                    }
                }
                if (!collection.getCardsList().isEmpty()) {
                    writer.write("---Collection----------");
                    writer.newLine();
                    for (CardElement card : collection.getCardsList()) {
                        writer.write(card.toString());
                        writer.newLine();
                    }
                }
            }

            writer.write("===Decks===============");
            writer.newLine();
            for (Deck deck : detailedOuicheList.getDecks()) {
                writer.write("---" + deck.getName() + "-------");
                writer.newLine();
                for (CardElement card : deck.toList()) {
                    if (card.getCard().getPassCode() != null
                            || card.getCard().getPrintCode() != null) {
                        writer.write(card.toString());
                    } else {
                        logger.warn("ouicheListSave: card has neither passcode nor print code — skipping");
                    }
                    writer.newLine();
                }
            }
        }
    }

    /**
     * Saves the unused-cards list to a file.
     *
     * <p>The file is created if it does not exist and overwritten if it does.
     *
     * @param filePath the path of the file to write
     * @throws IOException if the file cannot be created or written
     */
    public static void unusedCardsSave(String filePath) throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            boolean created = file.createNewFile();
            if (!created) {
                throw new IOException("File was not created: " + filePath);
            }
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(filePath), StandardCharsets.UTF_8))) {
            for (CardElement card : OuicheList.getUnusedCards()) {
                writer.write(card.toString());
                writer.newLine();
            }
        }
    }

    /**
     * Saves the third-party cards-I-need list to a file.
     *
     * <p>The target directory is created if it does not exist.
     * The file is created if it does not exist and overwritten if it does.
     *
     * @param directoryPath the directory to write into (with or without trailing separator)
     * @param fileName      the name of the file to write
     * @throws IOException if the file cannot be created or written
     */
    public static void thirdPartyCardsINeedListSave(String directoryPath, String fileName)
            throws IOException {
        Files.createDirectories(Paths.get(directoryPath));
        String fullPath = directoryPath + fileName;
        File file = new File(fullPath);
        if (!file.exists()) {
            boolean created = file.createNewFile();
            if (!created) {
                throw new IOException("File was not created: " + fullPath);
            }
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(fullPath), StandardCharsets.UTF_8))) {
            for (CardElement card : OuicheList.getThirdPartyCardsINeedList()) {
                writer.write(card.toString());
                writer.newLine();
            }
        }
    }
}