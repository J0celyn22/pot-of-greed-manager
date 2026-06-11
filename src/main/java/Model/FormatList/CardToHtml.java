package Model.FormatList;

import Model.CardsLists.Card;
import Model.Database.CardDatabaseManager;
import Model.Database.Database;
import Model.Database.PrintCodeToKonamiId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Generates an HTML detail page for a single card artwork.
 *
 * <p>Each page is written to {@code <outputRootDir>\Cards\<imagePath>.html}.
 * Card images are referenced via {@code ../Images/<imagePath>.jpg}.
 * The page shows the full-size image on the left and metadata on the right:
 * EN/FR/JA names, all passCodes, all printCodes, all related Konami IDs,
 * and thumbnail links to every alternate artwork of the same card.</p>
 *
 * <p>Use {@link #generateAllCardPages(String)} to generate pages for every
 * card in the database in a single upfront pass before any HTML export begins.</p>
 */
public class CardToHtml {

    private static final Logger logger = LoggerFactory.getLogger(CardToHtml.class);

    /**
     * Write buffer size per card page file. 4 KB is enough to avoid excessive
     * syscall overhead for the ~2–3 KB pages we produce, while keeping the OS
     * page cache pressure negligible when thousands of pages are written.
     */
    private static final int WRITE_BUFFER_SIZE = 4096;

    /**
     * Generates HTML detail pages for every card in the database that does not
     * already have a non-empty page on disk.
     *
     * <p>This is the only intended call site for page generation. Call it once
     * before starting any HTML export so that all card links are valid from the
     * moment the first deck or list file is written.</p>
     *
     * @param outputRootDir the root output directory (the folder that contains
     *                      {@code Decks\}, {@code Images\}, {@code Cards\}, etc.)
     */
    public static void generateAllCardPages(String outputRootDir) {
        String cardsDirPath = outputRootDir + "Cards\\";
        File cardsDir = new File(cardsDirPath);
        if (!cardsDir.exists()) {
            cardsDir.mkdirs();
        }

        Map<Integer, Card> allCards = Database.getAllCardsList();
        for (Card card : allCards.values()) {
            try {
                generateCardPageIfAbsent(card, cardsDirPath);
            } catch (IOException ioException) {
                logger.warn("Failed to generate card page for imagePath '{}': {}",
                        card.getImagePath(), ioException.getMessage());
            }
        }
    }

    /**
     * Generates the HTML page for {@code card} if a non-empty page does not
     * already exist on disk. Also generates pages for all sibling artworks
     * that are missing, so no thumbnail link is left dangling.
     *
     * @param card         the card to generate a page for
     * @param cardsDirPath absolute path to the {@code Cards\} directory
     * @throws IOException if an I/O error occurs while writing the file
     */
    private static void generateCardPageIfAbsent(Card card, String cardsDirPath) throws IOException {
        String imageFileName = card.getImagePath();
        if (imageFileName == null || imageFileName.isBlank()) {
            return;
        }

        List<Card> siblingArtworks = resolveSiblingArtworks(card);

        writeSinglePageIfAbsent(card, imageFileName, cardsDirPath, siblingArtworks);

        for (Card sibling : siblingArtworks) {
            if (sibling == null || sibling.getImagePath() == null) {
                continue;
            }
            String siblingFileName = sibling.getImagePath();
            if (siblingFileName.equals(imageFileName)) {
                continue;
            }
            writeSinglePageIfAbsent(sibling, siblingFileName, cardsDirPath, siblingArtworks);
        }
    }

    /**
     * Writes the HTML page for {@code card} only when the target file is absent
     * or empty. The file is written atomically from a single {@link BufferedWriter}
     * that is flushed and closed before this method returns.
     */
    private static void writeSinglePageIfAbsent(Card card, String imageFileName,
                                                String cardsDirPath,
                                                List<Card> siblingArtworks) throws IOException {
        File outputFile = new File(cardsDirPath + imageFileName + ".html");
        if (outputFile.exists() && outputFile.length() > 0) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        new FileOutputStream(outputFile, false),
                        StandardCharsets.UTF_8),
                WRITE_BUFFER_SIZE)) {
            writePageHeader(writer, card);
            writePageBody(writer, card, imageFileName, siblingArtworks);
            writePageFooter(writer);
            writer.flush();
        }
    }

    // -------------------------------------------------------------------------
    // Database lookups
    // -------------------------------------------------------------------------

    /**
     * Returns all artwork variants of {@code card} via
     * {@link CardDatabaseManager#getAliasCards(int)}.
     * Falls back to an empty list when the passCode is absent or non-numeric.
     */
    private static List<Card> resolveSiblingArtworks(Card card) {
        if (card.getPassCode() == null || card.getPassCode().isEmpty()) {
            return List.of();
        }
        try {
            int passCodeInt = Integer.parseInt(card.getPassCode());
            return CardDatabaseManager.getAliasCards(passCodeInt);
        } catch (NumberFormatException numberFormatException) {
            return List.of();
        } catch (Exception exception) {
            logger.warn("Failed to resolve sibling artworks for '{}': {}",
                    card.getPassCode(), exception.getMessage());
            return List.of();
        }
    }

    /**
     * Returns all passCodes sharing the same card name via
     * {@link CardDatabaseManager#getPassCodeToOtherPassCodes()}.
     */
    private static List<Integer> resolveAllPassCodes(Card card) {
        if (card.getPassCode() == null || card.getPassCode().isEmpty()) {
            return List.of();
        }
        try {
            int passCodeInt = Integer.parseInt(card.getPassCode());
            List<Integer> others = CardDatabaseManager.getPassCodeToOtherPassCodes().get(passCodeInt);
            if (others != null && !others.isEmpty()) {
                return others;
            }
            return List.of(passCodeInt);
        } catch (NumberFormatException numberFormatException) {
            return List.of();
        } catch (Exception exception) {
            logger.warn("Failed to resolve all passCodes for '{}': {}",
                    card.getPassCode(), exception.getMessage());
            return List.of();
        }
    }

    /**
     * Returns all printCodes for the card's Konami ID via
     * {@link PrintCodeToKonamiId#getKonamiIdToPrintCodes()}.
     */
    private static List<String> resolveAllPrintCodes(Card card) {
        if (card.getKonamiId() == null || card.getKonamiId().isEmpty()) {
            return List.of();
        }
        try {
            List<String> printCodes =
                    PrintCodeToKonamiId.getKonamiIdToPrintCodes().get(card.getKonamiId());
            return printCodes != null ? printCodes : List.of();
        } catch (Exception exception) {
            logger.warn("Failed to resolve printCodes for konamiId '{}': {}",
                    card.getKonamiId(), exception.getMessage());
            return List.of();
        }
    }

    /**
     * Returns all Konami IDs related to the card's own Konami ID via
     * {@link CardDatabaseManager#getKonamiIdToOtherKonamiId(Integer)}.
     */
    private static List<Integer> resolveRelatedKonamiIds(Card card) {
        if (card.getKonamiId() == null || card.getKonamiId().isEmpty()) {
            return List.of();
        }
        try {
            int konamiIdInt = Integer.parseInt(card.getKonamiId());
            List<Integer> related = CardDatabaseManager.getKonamiIdToOtherKonamiId(konamiIdInt);
            if (related != null && !related.isEmpty()) {
                return related;
            }
            return List.of(konamiIdInt);
        } catch (NumberFormatException numberFormatException) {
            return List.of();
        } catch (Exception exception) {
            logger.warn("Failed to resolve related Konami IDs for '{}': {}",
                    card.getKonamiId(), exception.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // HTML writing
    // -------------------------------------------------------------------------

    /**
     * Writes the {@code <head>} section with inline CSS for the card detail page.
     */
    private static void writePageHeader(BufferedWriter writer, Card card) throws IOException {
        String pageTitle = card.getName_EN() != null ? card.getName_EN() : card.getImagePath();
        writer.write("<!DOCTYPE html>\n<html>\n<head>\n");
        writer.write("<meta charset=\"UTF-8\">\n");
        writer.write("<title>" + escapeHtml(pageTitle) + "</title>\n");
        writer.write("<link rel=\"icon\" href=\"..\\Images\\Icon.png\">\n");
        writer.write("<style>\n");
        writer.write(
                "body {\n"
                        + "    background-color: #100317;\n"
                        + "    color: white;\n"
                        + "    font-family: sans-serif;\n"
                        + "    margin: 20px;\n"
                        + "}\n"
                        + ".card-page-layout {\n"
                        + "    display: flex;\n"
                        + "    align-items: flex-start;\n"
                        + "    gap: 30px;\n"
                        + "}\n"
                        + ".card-full-image {\n"
                        + "    width: 421px;\n"
                        + "    height: 614px;\n"
                        + "    border: 2px solid #cdfc04;\n"
                        + "    border-radius: 5px;\n"
                        + "    flex-shrink: 0;\n"
                        + "}\n"
                        + ".card-meta {\n"
                        + "    flex-grow: 1;\n"
                        + "}\n"
                        + ".card-meta p {\n"
                        + "    margin: 4px 0;\n"
                        + "    font-size: 16px;\n"
                        + "}\n"
                        + ".card-meta .label {\n"
                        + "    color: #cdfc04;\n"
                        + "    font-weight: bold;\n"
                        + "}\n"
                        + ".card-meta .name-en {\n"
                        + "    font-size: 22px;\n"
                        + "    font-weight: bold;\n"
                        + "    margin-bottom: 6px;\n"
                        + "}\n"
                        + ".card-meta .name-fr, .card-meta .name-ja {\n"
                        + "    font-size: 16px;\n"
                        + "    margin-bottom: 4px;\n"
                        + "    color: #cccccc;\n"
                        + "}\n"
                        + ".separator {\n"
                        + "    border: 1px solid #cdfc04;\n"
                        + "    margin: 12px 0;\n"
                        + "}\n"
                        + ".artworks-row {\n"
                        + "    display: flex;\n"
                        + "    flex-wrap: wrap;\n"
                        + "    gap: 8px;\n"
                        + "    margin-top: 12px;\n"
                        + "}\n"
                        + ".artwork-thumb {\n"
                        + "    width: 80px;\n"
                        + "    height: 117px;\n"
                        + "    border: 2px solid #cdfc04;\n"
                        + "    border-radius: 3px;\n"
                        + "}\n"
                        + ".artwork-thumb-current {\n"
                        + "    border-color: #ffffff;\n"
                        + "    opacity: 0.6;\n"
                        + "}\n"
        );
        writer.write("</style>\n</head>\n<body>\n");
    }

    /**
     * Writes the main body: image on the left, metadata column on the right.
     */
    private static void writePageBody(BufferedWriter writer, Card card,
                                      String currentImageFileName,
                                      List<Card> siblingArtworks) throws IOException {
        String cardAlt = card.getName_EN() != null ? card.getName_EN() : currentImageFileName;
        writer.write("<div class=\"card-page-layout\">\n");

        writer.write("  <img src=\"..\\Images\\" + currentImageFileName + ".jpg\" "
                + "alt=\"" + escapeHtml(cardAlt) + "\" "
                + "class=\"card-full-image\">\n");

        writer.write("  <div class=\"card-meta\">\n");

        writeMetaName(writer, card.getName_EN(), "name-en");
        writeMetaName(writer, card.getName_FR(), "name-fr");
        writeMetaName(writer, card.getName_JA(), "name-ja");

        writer.write("    <hr class=\"separator\">\n");

        writeJoinedIntField(writer, "PassCodes", resolveAllPassCodes(card));
        writeJoinedStrField(writer, "Print codes", resolveAllPrintCodes(card));
        writeJoinedIntField(writer, "Konami IDs", resolveRelatedKonamiIds(card));

        if (siblingArtworks.size() > 1) {
            writer.write("    <hr class=\"separator\">\n");
            writer.write("    <p><span class=\"label\">Other artworks:</span></p>\n");
            writer.write("    <div class=\"artworks-row\">\n");
            for (Card sibling : siblingArtworks) {
                if (sibling == null || sibling.getImagePath() == null) {
                    continue;
                }
                String siblingFileName = sibling.getImagePath();
                boolean isCurrent = siblingFileName.equals(currentImageFileName);
                String thumbClass = isCurrent
                        ? "artwork-thumb artwork-thumb-current"
                        : "artwork-thumb";
                String siblingAlt = sibling.getName_EN() != null
                        ? sibling.getName_EN()
                        : siblingFileName;

                if (isCurrent) {
                    writer.write("      <img src=\"..\\Images\\" + siblingFileName + ".jpg\" "
                            + "alt=\"" + escapeHtml(siblingAlt) + "\" "
                            + "class=\"" + thumbClass + "\">\n");
                } else {
                    writer.write("      <a href=\"" + siblingFileName + ".html\">"
                            + "<img src=\"..\\Images\\" + siblingFileName + ".jpg\" "
                            + "alt=\"" + escapeHtml(siblingAlt) + "\" "
                            + "class=\"" + thumbClass + "\">"
                            + "</a>\n");
                }
            }
            writer.write("    </div>\n");
        }

        writer.write("  </div>\n");
        writer.write("</div>\n");
    }

    /**
     * Writes a name paragraph with the given CSS class; skips null or blank values.
     */
    private static void writeMetaName(BufferedWriter writer, String name,
                                      String cssClass) throws IOException {
        if (name == null || name.isBlank()) {
            return;
        }
        writer.write("    <p class=\"" + cssClass + "\">" + escapeHtml(name) + "</p>\n");
    }

    /**
     * Writes a labeled field whose values are integers joined by commas.
     * Skips the field when the list is empty.
     */
    private static void writeJoinedIntField(BufferedWriter writer, String label,
                                            List<Integer> values) throws IOException {
        if (values.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                text.append(", ");
            }
            text.append(values.get(index));
        }
        writer.write("    <p><span class=\"label\">" + escapeHtml(label) + ":</span> "
                + escapeHtml(text.toString()) + "</p>\n");
    }

    /**
     * Writes a labeled field whose values are strings joined by commas.
     * Skips the field when the list is empty.
     */
    private static void writeJoinedStrField(BufferedWriter writer, String label,
                                            List<String> values) throws IOException {
        if (values.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                text.append(", ");
            }
            text.append(values.get(index));
        }
        writer.write("    <p><span class=\"label\">" + escapeHtml(label) + ":</span> "
                + escapeHtml(text.toString()) + "</p>\n");
    }

    /**
     * Writes the closing HTML tags.
     */
    private static void writePageFooter(BufferedWriter writer) throws IOException {
        writer.write("</body>\n</html>\n");
    }

    /**
     * Escapes the five XML/HTML special characters in a string.
     */
    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}