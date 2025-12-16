package Model.FormatList;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.CardsLists.Deck;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static Model.Database.DataBaseUpdate.getAddresses;

public class HtmlGenerator {

    /**
     * Creates an HTML file at the specified file path. If the parent directory
     * does not exist, it attempts to create it. Throws an IOException if the
     * parent directory or the file cannot be created.
     *
     * @param filePath The path where the HTML file should be created.
     * @throws IOException If the parent directory or the file cannot be created.
     */
    public static void createHtmlFile(String filePath) throws IOException {
        File file = new File(filePath);
        File parentDir = file.getParentFile();

        if (parentDir != null && !parentDir.exists()) {
            boolean mkdirs = parentDir.mkdirs();
            if (!mkdirs) {
                //System.out.println("Parent directory was not created: " + parentDir.getAbsolutePath());
            }
        }

        if (!file.exists()) {
            boolean newFile = file.createNewFile();
            if (!newFile) {
                throw new IOException("File was not created: " + file.getAbsolutePath());
            }
        }
    }

    /**
     * Adds the HTML header for a page of cards.
     *
     * The header includes the title of the page, a link to the icon for the page,
     * and CSS styles for the page. The icon is automatically copied from the
     * resources directory to the specified directory if it does not already
     * exist. The CSS styles are for a menu with rectangles and a title. The
     * rectangles are centered and have a yellow border. The title is centered
     * and has a yellow border. The cards are displayed in a flexbox layout with
     * a yellow border and a grayscale filter.
     *
     * @param writer The writer to use to write the HTML.
     * @param title The title of the page.
     * @param relativeImagePath The relative path to the icon.
     * @param dirPath The directory path where the icon should be copied.
     * @throws IOException If there is an error writing the HTML or copying the icon.
     */
    public static void addHeader(BufferedWriter writer, String title, String relativeImagePath, String dirPath) throws IOException {
        //Create relativeImagePath directory if it doesn't exist
        boolean mkdirs = new File(dirPath + relativeImagePath).mkdirs();
        if (!mkdirs) {
            //System.out.println("Directory was not created: " + dirPath + relativeImagePath);
        }

        //If relativeImagePath + "Icon.png" does not exist, copy it from resources
        if (!new File(relativeImagePath + "Icon.png").exists()) {
            //Path source = Paths.get("src/main/resources/Icon.png");
            Path source;
            Path target;
            byte[] encoded;
            try { //TODO find a better way to do this without having to put the jar version of the path in a catch
                source = Paths.get("./src/main/resources/Icon.png");
                target = Paths.get(dirPath + relativeImagePath + "Icon.png");
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                source = Paths.get("resources/Icon.png");
                target = Paths.get(dirPath + relativeImagePath + "Icon.png");
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        //If relativeImagePath + "Icon.png" does not exist, copy it from resources
        /*if (!new File(relativeImagePath + "Icon.png").exists()) {
            InputStream resourceStream = HtmlGenerator.class.getClassLoader().getResourceAsStream("Icon.png");
            if (resourceStream == null) {
                throw new FileNotFoundException("Resource not found: Icon.png");
            }
            Path target = Paths.get(dirPath + relativeImagePath + "Icon.png");

            // Ensure parent directories exist
            Files.createDirectories(target.getParent());

            // Copy resource to target location
            Files.copy(resourceStream, target, StandardCopyOption.REPLACE_EXISTING);
        }*/


        writer.write("<html>\n<head>\n<meta charset=\"UTF-8\">\n<title>" + title + "</title>\n<link rel=\"icon\" href=\"" + relativeImagePath + "Icon.png\">\n<link rel=\"manifest\" href=\".\\manifest.json\">\n<style>\n/* Styling for the rectangles */\n" +
                "ul {\n" +
                "    list-style: none;\n" +
                "    padding: 0;\n" +
                "    display: flex; /* Use flexbox for layout */\n" +
                "    justify-content: left; /* Center the menu horizontally */\n" +
                "    flex-wrap: wrap; /* Allow items to wrap to a new line */\n" +
                "}\n" +
                "\n" +
                "li {\n" +
                "    margin-bottom: 10px;\n" +
                "    position: relative;\n" +
                "    margin-right: 10px; /* Add spacing between items */\n" +
                "    width: 200px;\n" +
                "}\n" +
                "\n" +
                "a.menu-link {\n" +
                "    display: block;\n" +
                "    padding: 10px;\n" +
                "    background-color: black; /* Background color */\n" +
                "    color: white; /* Text color */\n" +
                "    text-decoration: none;\n" +
                "    border: 2px solid #cdfc04; /* Border color (same as rectangle color) */\n" +
                "    border-radius: 5px;\n" +
                "}\n" +
                "\n" +
                "h1, h2, h3 {\n" +
                "    color: white; /* Title text color */\n" +
                "}\n" +
                "\n" +
                "h1 {\n" +
                "    text-align: center; /* Center the title */\n" +
                "}\n" +
                "\n" +
                "h2, h3 {\n" +
                "    text-align: left; /* Align the title to the left */\n" +
                "}\n" +
                "\n" +
                "body {\n" +
                "    background-color: #100317;\n" +
                "    color: white;\n" +
                "}\n" +
                "\n" +
                ".rectangle-beginning {\n" +
                "\tborder: 2px solid #cdfc04;\n" +
                "\tborder-radius: 5px;\n" +
                "\tbackground-color: black;\n" +
                "\tmargin-bottom: 8px;\n" +
                "\tpadding-left: 5px;\n" +
                "\tpadding-right: 5px;\n" +
                "}\n" +
                "\n" +
                ".card-element {\n" +
                "\tborder: 2px solid #cdfc04;\n" +
                "\tborder-radius: 5px;\n" +
                "\tbackground-color: black;\n" +
                "\tmargin-bottom: 8px;\n" +
                "\tdisplay: flex;\n" +
                "\talign-items: center;\n" +
                "\tpadding-left: 5px;\n" +
                "\tpadding-right: 5px;\n" +
                "\twidth: 650px;\n" +
                "}\n" +
                "\n" +
                ".card-image {\n" +
                "\twidth: 100px;\n" +
                "\theight: 146px;\n" +
                "\tmargin-right: 10px;\n" +
                "\tborder-left: 2px solid #100317;\n" +
                "\tborder-top: 5px solid #100317;\n" +
                "\tborder-bottom: 5px solid #100317;\n" +
                "}\n" +
                "\n" +
                ".card-image-large {\n" +
                "\twidth: 200px;\n" +
                "\theight: 292px;\n" +
                "\tmargin-right: 10px;\n" +
                "\tborder-left: 2px solid #100317;\n" +
                "\tborder-top: 5px solid #100317;\n" +
                "\tborder-bottom: 5px solid #100317;\n" +
                "}\n" +
                "\n" +
                ".card-details {\n" +
                "\tflex-grow: 1;\n" +
                "\tmargin-right: 5px;\n" +
                "\tmargin-top: -15px;\n" +
                "\tmargin-bottom: -15px;\n" +
                "}\n" +
                "\n" +
                ".card-value {\n" +
                "\tflex-grow: 1;\n" +
                "\tmargin-right: 5px;\n" +
                "\tmargin-top: -100px;\n" +
                "\ttext-align: right;\n" +
                "\tfont-size: 25px;\n" +
                "}\n" +
                "\n" +
                ".grayscale {\n" +
                "\tfilter: grayscale(100%);\n" +
                "}\n</style>\n</head>\n<body>\n");
    }

    /**
     * Write a link to a list HTML page to the given writer.
     *
     * @param writer   The writer to which to write the link
     * @param fileName The name of the file for which to write a link
     * @throws IOException If the file cannot be written
     */
    public static void addListButton(BufferedWriter writer, String fileName) throws IOException {
        writer.write("<ul>\n" +
                "    <li><a class=\"menu-link\" href=\"" + fileName + " - List.html\">" + fileName + " - List</a></li>\n" +
                "</ul>\n");
    }

    /**
     * Write a link to a mosaic HTML page to the given writer.
     *
     * @param writer   The writer to which to write the link
     * @param fileName The name of the file for which to write a link
     * @throws IOException If the file cannot be written
     */
    public static void addMosaicButton(BufferedWriter writer, String fileName) throws IOException {
        writer.write(
                "<ul>\n" +
                        "    <li><a class=\"menu-link\" href=\"" + fileName + " - Mosaic.html\">" + fileName + " - Mosaic</a></li>\n" +
                        "</ul>\n");
    }

    /**
     * Write a link to the Collection Complete List HTML page to the given writer.
     *
     * @param writer The writer to which to write the link
     * @throws IOException If the file cannot be written
     */
    public static void addCollectionButton(BufferedWriter writer) throws IOException {
        writer.write(
                "<ul>\n" +
                        "    <li><a class=\"menu-link\" href=\"Collection Complete List.html\">Collection Complete List</a></li>\n" +
                        "</ul>\n");
    }

    /**
     * Write a link to the OuicheList HTML page to the given writer.
     *
     * @param writer The writer to which to write the link
     * @throws IOException If the file cannot be written
     */
    public static void addOuicheListButton(BufferedWriter writer) throws IOException {
        writer.write(
                "<ul>\n" +
                        "    <li><a class=\"menu-link\" href=\"OuicheList.html\">OuicheList</a></li>\n" +
                        "</ul>\n");
    }

    /**
     * Write a title to the given writer.
     *
     * @param writer The writer to which to write the title
     * @param title  The title to write
     * @throws IOException If the file cannot be written
     */
    public static void addTitle(BufferedWriter writer, String title) throws IOException {
        writer.write("<h1>" + title + "</h1>\n");
    }

    /**
     * Write a title of level 2 to the given writer.
     *
     * @param writer The writer to which to write the title
     * @param title  The title to write
     * @throws IOException If the file cannot be written
     */
    public static void addTitle2(BufferedWriter writer, String title) throws IOException {
        writer.write("<h2>" + title + "</h2>\n");
    }

    /**
     * Write a title of level 3 to the given writer.
     *
     * @param writer The writer to which to write the title
     * @param title  The title to write
     * @throws IOException If the file cannot be written
     */
    public static void addTitle3(BufferedWriter writer, String title) throws IOException {
        writer.write("<h3>" + title + "</h3>\n");
    }

    /**
     * Write a title to the given writer.
     *
     * @param writer      The writer to which to write the title
     * @param title       The title to write
     * @param cardsNumber The number of cards in the deck/collection
     * @param cardsPrice  The price of the deck/collection
     * @throws IOException If the file cannot be written
     */
    public static void addTitle(BufferedWriter writer, String title, Integer cardsNumber, String cardsPrice) throws IOException {
        writer.write("<h1>" + title + " (" + cardsNumber + " / " + cardsPrice + "€)" + "</h1>\n");
    }

    /**
     * Write a title of level 2 to the given writer, with the number of cards and the price of the deck/collection.
     *
     * @param writer      The writer to which to write the title
     * @param title       The title to write
     * @param cardsNumber The number of cards in the deck/collection
     * @param cardsPrice  The price of the deck/collection
     * @throws IOException If the file cannot be written
     */
    public static void addTitle2(BufferedWriter writer, String title, Integer cardsNumber, String cardsPrice) throws IOException {
        writer.write("<h2>" + title + " (" + cardsNumber + " / " + cardsPrice + "€)" + "</h2>\n");
    }

    /**
     * Write a title of level 3 to the given writer, with the number of cards and the price of the deck/collection.
     *
     * @param writer      The writer to which to write the title
     * @param title       The title to write
     * @param cardsNumber The number of cards in the deck/collection
     * @param cardsPrice  The price of the deck/collection
     * @throws IOException If the file cannot be written
     */
    public static void addTitle3(BufferedWriter writer, String title, Integer cardsNumber, String cardsPrice) throws IOException {
        writer.write("<h3>" + title + " (" + cardsNumber + " / " + cardsPrice + "€)" + "</h3>\n");
    }

    /**
     * Calculates the total price of all card elements in the provided list.
     * Only card elements with a non-null card and a non-null price are considered.
     *
     * @param cardsList A list of CardElement objects to calculate the total price for.
     * @return The total price as a String.
     */
    public static String getPriceCardElement(List<CardElement> cardsList) {
        float price = 0;
        for (CardElement card : cardsList) {
            if (card.getCard() != null) {
                if (card.getPrice() != null) {
                    price += Float.parseFloat(card.getCard().getPrice());
                }
            }
        }
        return String.valueOf(price);
    }

    /**
     * Get the total price of a list of cards.
     *
     * @param cardsList The list of cards to calculate the total price of.
     * @return The total price of the cards as a string.
     */
    public static String getPriceCard(List<Card> cardsList) {
        float price = 0;
        for (Card card : cardsList) {
            price += Float.parseFloat(card.getPrice());
        }
        return String.valueOf(price);
    }

    /**
     * Adds the beginning of a rectangle to the HTML file.
     *
     * <p>This is used to create a rectangle with a rounded corner and a title.
     * The rectangle is defined by a div with the class "rectangle-beginning".
     * <p>The rectangle contains an empty image (TODO : remove) used to make sure the titles appear.
     *
     * @param writer The writer to write to.
     * @throws IOException If the file cannot be written.
     */
    public static void addRectangleBeginning(BufferedWriter writer) throws IOException {
        writer.write("<div class=\"rectangle-beginning\">\n" +
                "<img src=\"\" alt=\"\"> <!--TODO Remove : only here to make sure the titles appear...-->\n");
    }

    /**
     * Closes a rectangle HTML element by writing a closing div tag to the given writer.
     *
     * <p>This method is used to mark the end of a rectangle that was started with
     * {@code addRectangleBeginning}. It writes the closing {@code </div>} tag to the output.
     *
     * @param writer The BufferedWriter to which the closing div tag will be written.
     * @throws IOException If an I/O error occurs while writing to the writer.
     */
    public static void addRectangleEnd(BufferedWriter writer) throws IOException {
        writer.write("</div>\n");
    }

    /**
     * Adds links to the HTML file to easily navigate between different lists.
     *
     * <p>This method is used to add a list of links to the HTML file that was generated
     * by the program. The links include the different lists of cards, the different
     * types of cards, and the different attributes of cards.
     *
     * @param writer The writer to write to.
     * @throws IOException If an I/O error occurs while writing to the writer.
     */
    public static void addLinkButtons(BufferedWriter writer) throws IOException {
        writer.write(
                "<ul>\n" +
                        "    <li><a class=\"menu-link\" href=\"Collection - Mosaic.html\">Collection - Mosaic</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"Collection Complete List.html\">Collection</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"Available Cards.html\">Available Cards List</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"Decks List.html\">Decks List</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"..\\Decks\\Decks Menu.html\">Decks Menu</a></li>\n" +
                        "</ul>\n" +
                        "<ul>\n" +
                        "    <li><a class=\"menu-link\" href=\"Detailed OuicheList - Mosaic.html\">Complete List - Mosaic</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"OuicheList.html\">Complete List</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"MonsterList.html\">Monster List</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"SpellList.html\">Spell List</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"TrapList.html\">Trap List</a></li>\n" +
                        "</ul>\n" +
                        "<h2>Monster types</h2>\n" +
                        "<ul>\n" +
                        "    <li><a class=\"menu-link\" href=\"PyroTypeMonster.html\">Pyro</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"AquaTypeMonster.html\">Aqua</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"MachineTypeMonster.html\">Machine</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"DragonTypeMonster.html\">Dragon</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"BeastWarriorTypeMonster.html\">BeastWarrior</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"ReptileTypeMonster.html\">Reptile</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"PlantTypeMonster.html\">Plant</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"FiendTypeMonster.html\">Fiend</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"WyrmTypeMonster.html\">Wyrm</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"DinosaurTypeMonster.html\">Dinosaur</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"SpellcasterTypeMonster.html\">Spellcaster</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"FishTypeMonster.html\">Fish</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"DivineBeastTypeMonster.html\">DivineBeast</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"CyberseTypeMonster.html\">Cyberse</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"InsectTypeMonster.html\">Insect</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"WingedBeastTypeMonster.html\">WingedBeast</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"WarriorTypeMonster.html\">Warrior</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"RockTypeMonster.html\">Rock</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"ThunderTypeMonster.html\">Thunder</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"ZombieTypeMonster.html\">Zombie</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"SeaSerpentTypeMonster.html\">SeaSerpent</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"BeastTypeMonster.html\">Beast</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"PsychicTypeMonster.html\">Psychic</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"FairyTypeMonster.html\">Fairy</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"IllusionTypeMonster.html\">Illusion</a></li>\n" +
                        "</ul>\n" +
                        "<h2>Monster cardtypes</h2>\n" +
                        "<ul>\n" +
                        "    <li><a class=\"menu-link\" href=\"NormalMonsterCard.html\">Normal</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"ToonMonsterCard.html\">Toon</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"TunerMonsterCard.html\">Tuner</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"UnionMonsterCard.html\">Union</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"SynchroMonsterCard.html\">Synchro</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"PendulumMonsterCard.html\">Pendulum</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"RitualMonsterCard.html\">Ritual</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"FlipMonsterCard.html\">Flip</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"SpiritMonsterCard.html\">Spirit</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"XyzMonsterCard.html\">Xyz</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"EffectMonsterCard.html\">Effect</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"FusionMonsterCard.html\">Fusion</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"LinkMonsterCard.html\">Link</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"GeminiMonsterCard.html\">Gemini</a></li>\n" +
                        "</ul>\n" +
                        "<h2>Spell cardtypes</h2>\n" +
                        "<ul>\n" +
                        "    <li><a class=\"menu-link\" href=\"NormalSpellCard.html\">Normal</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"ContinuousSpellCard.html\">Continuous</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"QuickPlaySpellCard.html\">QuickPlay</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"EquipSpellCard.html\">Equip</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"FieldSpellCard.html\">Field</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"RitualSpellCard.html\">Ritual</a></li>\n" +
                        "</ul>\n" +
                        "<h2>Trap cardtypes</h2>\n" +
                        "<ul>\n" +
                        "    <li><a class=\"menu-link\" href=\"NormalTrapCard.html\">Normal</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"ContinuousTrapCard.html\">Continuous</a></li>\n" +
                        "    <li><a class=\"menu-link\" href=\"CounterTrapCard.html\">Counter</a></li>\n" +
                        "</ul>\n");
    }

    /**
     * Writes HTML link buttons for a list of decks to the given writer.
     *
     * <p>This method generates a list of HTML link buttons for each deck in the provided
     * list of decks. Each button links to an HTML page corresponding to the deck's name
     * and the specified type. Additionally, a link to the general Decks List is included.
     *
     * @param writer    The BufferedWriter to which the HTML content will be written.
     * @param decksList A list of Deck objects for which link buttons will be created.
     * @param type      A string representing the type of deck, used in the link URLs.
     * @throws IOException If an I/O error occurs while writing to the writer.
     */
    public static void addDeckLinkButtons(BufferedWriter writer, List<Deck> decksList, String type) throws IOException {
        writer.write(
                "<ul>\n" +
                        "    <li><a class=\"menu-link\" href=\"..\\Lists\\Decks List.html\">Decks List</a></li>\n" +
                        "</ul>\n");
        writer.write("<ul>\n");
        for (Deck deck : decksList) {
            writer.write("<li><a class=\"menu-link\" href=\"" + deck.getName() + " - " + type + ".html\">" + deck.getName() + "</a></li>\n");
        }
        writer.write("</ul>\n");
    }

    /**
     * Writes the HTML closing tags to the given writer.
     *
     * <p>This method generates the closing tags for the HTML document, which are
     * <code>&lt;/body&gt;</code> and <code>&lt;/html&gt;</code>.
     *
     * @param writer The BufferedWriter to which the HTML closing tags will be written.
     * @throws IOException If an I/O error occurs while writing to the writer.
     */
    public static void addFooter(BufferedWriter writer) throws IOException {
        writer.write("</body>\n</html>");
    }

    /**
     * Writes a list of CardElements to the given writer as an HTML list.
     *
     * <p>This method generates an HTML list of CardElements, each of which is displayed
     * with a link to its corresponding HTML page. A title is also generated, which
     * displays the name of the list and the total number of cards in the list.
     *
     * @param cardsList          A list of CardElements to be written to the writer.
     * @param name               A string representing the name of the list to be displayed in the title.
     * @param writer             The BufferedWriter to which the HTML content will be written.
     * @param dirPath            The directory path used to construct the links to the HTML pages.
     * @param imagesRelativePath The relative path from the output files to the images directory.
     * @throws IOException If an I/O error occurs while writing to the writer.
     */
    public static void displayList(List<CardElement> cardsList, String name, BufferedWriter writer, String dirPath, String imagesRelativePath) throws IOException {
        if (cardsList != null) {
            if (!cardsList.isEmpty()) {
                String imagesDirPath = dirPath + imagesRelativePath;
                HtmlGenerator.addTitle3(writer, name.replace("=", ""), cardsList.size(), getPriceCardElement(cardsList));
                Map<Card, Integer> cardCount = createCardsMap(cardsList);

                for (Map.Entry<Card, Integer> entry : cardCount.entrySet()) {
                    writeCardElement(writer, entry.getKey(), entry.getValue(), false, imagesDirPath, imagesRelativePath);
                }
            }
        }
    }

    /**
     * Writes a list of CardElements to the given writer as a mosaic.
     *
     * <p>This method generates an HTML mosaic of CardElements, each of which is displayed
     * with a link to its corresponding HTML page. A title is also generated, which
     * displays the name of the list and the total number of cards in the list.
     *
     * @param cardsList          A list of CardElements to be written to the writer.
     * @param name               A string representing the name of the list to be displayed in the title.
     * @param writer             The BufferedWriter to which the HTML content will be written.
     * @param dirPath            The directory path used to construct the links to the HTML pages.
     * @param imagesRelativePath The relative path from the output files to the images directory.
     * @throws IOException If an I/O error occurs while writing to the writer.
     */
    public static void displayMosaic(List<CardElement> cardsList, String name, BufferedWriter writer, String dirPath, String imagesRelativePath) throws IOException {
        if (cardsList != null) {
            if (!cardsList.isEmpty()) {
                String imagesDirPath = dirPath + imagesRelativePath;
                HtmlGenerator.addTitle3(writer, name.replace("=", ""), cardsList.size(), getPriceCardElement(cardsList));

                for (CardElement card : cardsList) {
                    writeCardElement(writer, card, imagesDirPath, imagesRelativePath);
                }
            }
        }
    }

    /**
     * Writes a list of CardElements to the given writer as an HTML list, where cards which are not owned are displayed first, followed by cards which are owned.
     *
     * <p>This method generates an HTML list of CardElements, each of which is displayed
     * with a link to its corresponding HTML page. A title is also generated, which
     * displays the name of the list and the total number of cards in the list.
     *
     * @param cardsList          A list of CardElements to be written to the writer.
     * @param name               A string representing the name of the list to be displayed in the title.
     * @param writer             The BufferedWriter to which the HTML content will be written.
     * @param dirPath            The directory path used to construct the links to the HTML pages.
     * @param imagesRelativePath The relative path from the output files to the images directory.
     * @throws IOException If an I/O error occurs while writing to the writer.
     */
    public static void displayListWithOwned(List<CardElement> cardsList, String name, BufferedWriter writer, String dirPath, String imagesRelativePath) throws IOException {
        if (cardsList != null) {
            if (!cardsList.isEmpty()) {
                String imagesDirPath = dirPath + imagesRelativePath;
                HtmlGenerator.addTitle3(writer, name.replace("=", ""), cardsList.size(), getPriceCardElement(cardsList));

                Map<CardElement, Integer>[] cardCount = createCardsMapWithNotOwned(cardsList);
                Map<CardElement, Integer> cardCountWithO = cardCount[0];
                Map<CardElement, Integer> cardCountWithoutO = cardCount[1];

                for (Map.Entry<CardElement, Integer> entry : cardCountWithoutO.entrySet()) {
                    writeCardElement(writer, entry.getKey(), entry.getValue(), imagesDirPath, imagesRelativePath);
                }

                for (Map.Entry<CardElement, Integer> entry : cardCountWithO.entrySet()) {
                    writeCardElement(writer, entry.getKey(), entry.getValue(), imagesDirPath, imagesRelativePath);
                }
            }
        }
    }

    /**
     * Writes a mosaic of CardElements to the given writer.
     *
     * <p>This method generates an HTML mosaic of CardElements, each of which is displayed
     * with a link to its corresponding HTML page. A title is also generated, which
     * displays the name of the list and the total number of cards in the list.
     *
     * <p>The cards are displayed in the order in which they appear in the list, which
     * means that cards which are not owned will be displayed first, followed by cards
     * which are owned.
     *
     * @param cardsList          A list of CardElements to be written to the writer.
     * @param name               A string representing the name of the list to be displayed in the title.
     * @param writer             The BufferedWriter to which the HTML content will be written.
     * @param dirPath            The directory path used to construct the links to the HTML pages.
     * @param imagesRelativePath The relative path from the output files to the images directory.
     * @throws IOException If an I/O error occurs while writing to the writer.
     */
    public static void displayMosaicWithOwned(List<CardElement> cardsList, String name, BufferedWriter writer, String dirPath, String imagesRelativePath) throws IOException {
        if (cardsList != null) {
            if (!cardsList.isEmpty()) {
                String imagesDirPath = dirPath + imagesRelativePath;
                HtmlGenerator.addTitle3(writer, name.replace("=", ""), cardsList.size(), getPriceCardElement(cardsList));

                for (CardElement entry : cardsList) {
                    writeCardElement(writer, entry, imagesDirPath, imagesRelativePath);
                }
            }
        }
    }

    /**
     * Creates two maps separating owned and not owned card elements from the provided list.
     *
     * <p>This method iterates through the given list of CardElements and categorizes them
     * into two separate maps: one for owned cards and one for not owned cards. The maps
     * track the count of each card element based on their image path. If a card is owned,
     * it is added to the 'cardCountWithO' map, otherwise it is added to the 'cardCountWithoutO' map.
     * The method returns an array containing both maps.
     *
     * @param cardsList A list of CardElements to be categorized into owned and not owned.
     * @return An array of two maps: the first map contains owned CardElements and their counts,
     * the second map contains not owned CardElements and their counts.
     */
    static Map<CardElement, Integer>[] createCardsMapWithNotOwned(List<CardElement> cardsList) {
        Map<CardElement, Integer> cardCountWithO = new LinkedHashMap<>();
        Map<CardElement, Integer> cardCountWithoutO = new LinkedHashMap<>();

        for (CardElement card : cardsList) {
            boolean found = false;
            Boolean isOwned = card.getOwned();

            if (isOwned) {
                for (Map.Entry<CardElement, Integer> entry : cardCountWithO.entrySet()) {
                    if (entry.getKey().getCard().getImagePath() != null && card.getCard().getImagePath() != null) {
                        if (entry.getKey().getCard().getImagePath().equals(card.getCard().getImagePath())) {
                            cardCountWithO.put(entry.getKey(), entry.getValue() + 1);
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    cardCountWithO.put(card, 1);
                }
            } else {
                for (Map.Entry<CardElement, Integer> entry : cardCountWithoutO.entrySet()) {
                    if (entry.getKey().getCard().getImagePath() != null && card.getCard().getImagePath() != null) {
                        if (entry.getKey().getCard().getImagePath().equals(card.getCard().getImagePath())) {
                            cardCountWithoutO.put(entry.getKey(), entry.getValue() + 1);
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    cardCountWithoutO.put(card, 1);
                }
            }
        }

        return new Map[]{cardCountWithO, cardCountWithoutO};
    }

    /**
     * Creates a map that counts the occurrences of each unique card based on their image path.
     *
     * @param cardsList The list of CardElement objects to be processed.
     *                  Each CardElement contains a Card object whose image path is used for uniqueness.
     * @return A map where each key is a Card object and the corresponding value is the count of how many times
     * that card appears in the input list.
     */
    static Map<Card, Integer> createCardsMap(List<CardElement> cardsList) {
        Map<Card, Integer> cardCount = new LinkedHashMap<>();
        for (CardElement card : cardsList) {
            boolean found = false;
            for (Map.Entry<Card, Integer> entry : cardCount.entrySet()) {
                if (entry.getKey() != null) {
                    if (entry.getKey().getImagePath() != null) {
                        if (entry.getKey().getImagePath().equals(card.getCard().getImagePath())) {
                            cardCount.put(entry.getKey(), entry.getValue() + 1);
                            found = true;
                            break;
                        }
                    }
                }
            }
            if (!found) {
                cardCount.put(card.getCard(), 1);
            }
        }

        return cardCount;
    }

    /**
     * Writes an HTML element representing a card to the specified writer.
     *
     * <p>This method generates the HTML for a card element, including the image
     * and link to the card's details page. It handles the copying of the card's
     * image to the specified directory if necessary, and applies styling based
     * on whether the card is owned.
     *
     * @param writer             The BufferedWriter to which the HTML content will be written.
     * @param entryKey           The CardElement object representing the card to be displayed.
     * @param entryValue         An integer representing the count of the card.
     * @param imagesDirPath      The directory path where the card's image will be stored.
     * @param imagesRelativePath The relative path to the images directory for HTML.
     * @throws IOException If an I/O error occurs while writing to the writer.
     */
    public static void writeCardElement(BufferedWriter writer, CardElement entryKey, Integer entryValue, String imagesDirPath, String imagesRelativePath) throws IOException {
        writeCardElement(writer, entryKey.getCard(), entryValue, entryKey.getOwned(), imagesDirPath, imagesRelativePath);
    }

    /**
     * Writes an HTML element representing a card to the specified writer.
     *
     * <p>This method generates the HTML for a card element, including the image
     * and link to the card's details page. It handles the copying of the card's
     * image to the specified directory if necessary, and applies styling based
     * on whether the card is owned.
     *
     * @param writer             The BufferedWriter to which the HTML content will be written.
     * @param entryKey           The Card object representing the card to be displayed.
     * @param entryValue         An integer representing the count of the card.
     * @param isOwned            A boolean indicating whether the card is owned.
     * @param imagesDirPath      The directory path where the card's image will be stored.
     * @param imagesRelativePath The relative path to the images directory for HTML.
     * @throws IOException If an I/O error occurs while writing to the writer.
     */
    public static void writeCardElement(BufferedWriter writer, Card entryKey, Integer entryValue, Boolean isOwned, String imagesDirPath, String imagesRelativePath) throws IOException {
        String imagePath = "";
        if (getAddresses(entryKey.getImagePath() + ".jpg").length != 0) {
            imagePath = getAddresses(entryKey.getImagePath() + ".jpg")[0];
        }

        String imageFileName = "";
        String cardName = entryKey.getNameOrNumber();
        if (imagePath != null && !imagePath.isEmpty()) {
            Path sourcePath = Paths.get(imagePath);

            imageFileName = sourcePath.getFileName().toString();

            Path targetDirPath = Paths.get(imagesDirPath);
            if (!Files.exists(targetDirPath)) {
                Files.createDirectories(targetDirPath);
            }
            Path targetPath = targetDirPath.resolve(imageFileName);
            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        writer.write("<div class=\"card-element\">\n");
        String imgStyle = "card-image";
        if (imagePath != null) {
            if (isOwned) {
                imgStyle += " grayscale";
            }
        }
        String imagesTemp = imagesRelativePath + imageFileName;

        writer.write(
                "<a href=\"" + "https://yugioh.fandom.com/wiki/" + entryKey.getPassCode() + /*"..\\CardDetails\\" + imageFileName.replaceAll("\\.[^.]+$", "") + ".html\">"*/"\">" +
                        "<img src=\"" + imagesTemp + "\" alt=\"" + cardName + "\" class=\"" + imgStyle + "\">" +
                        "</a>\n");

        //generateCardDetailsHtml(dirPath, entryKey);

        writer.write("<div class=\"card-details\">\n");
        writer.write("<p>" + entryKey.getName_FR() + "</p>\n");
        writer.write("<p>" + entryKey.getName_EN() + "</p>\n");
        writer.write("<p>" + entryKey.getName_JA() + "</p>\n");
        writer.write("</div>\n");
        writer.write("<div class=\"card-value\">\n");
        writer.write("<p><b>" + entryValue + "</b></p>\n");
        if (entryKey.getPrice() != null) {
            writer.write("<b>" + entryValue * Float.parseFloat(entryKey.getPrice()) + "€</b>\n"); //TODO TEST
        }
        writer.write("</div>\n");
        writer.write("</div>\n");
    }

    /**
     * Writes a CardElement to the given writer as an HTML element.
     *
     * <p>This method generates an HTML element which displays the card's image,
     * name and value. The image is linked to the card's HTML page. The value is
     * displayed in the following format: <code>&lt;b&gt;[value]&lt;/b&gt;</code>.
     * If the card is owned, the image is displayed in grayscale.
     *
     * @param writer             The BufferedWriter to which the HTML content will be written.
     * @param card               The CardElement to be written to the writer.
     * @param imagesDirPath      The directory path used to construct the links to the images.
     * @param imagesRelativePath The relative path from the output files to the images directory.
     * @throws IOException If an I/O error occurs while writing to the writer.
     */
    public static void writeCardElement(BufferedWriter writer, CardElement card, String imagesDirPath, String imagesRelativePath) throws IOException {
        writeCardElement(writer, card.getCard(), card.getOwned(), imagesDirPath, imagesRelativePath);
    }

    /**
     * Writes a Card to the given writer as an HTML element.
     *
     * <p>This method generates an HTML element which displays the card's image,
     * name and value. The image is linked to the card's HTML page. The value is
     * displayed in the following format: <code>&lt;b&gt;[value]&lt;/b&gt;</code>.
     * If the card is owned, the image is displayed in grayscale.
     *
     * @param writer             The BufferedWriter to which the HTML content will be written.
     * @param card               The Card to be written to the writer.
     * @param isOwned            A boolean indicating if the card is owned.
     * @param imagesDirPath      The directory path used to construct the links to the images.
     * @param imagesRelativePath The relative path from the output files to the images directory.
     * @throws IOException If an I/O error occurs while writing to the writer.
     */
    public static void writeCardElement(BufferedWriter writer, Card card, Boolean isOwned, String imagesDirPath, String imagesRelativePath) throws IOException {
        String imagePath = "";
        if (getAddresses(card.getImagePath() + ".jpg").length != 0) {
            imagePath = getAddresses(card.getImagePath() + ".jpg")[0];
        }

        String imageFileName = "";
        String cardName = card.getNameOrNumber();

        if (imagePath != null && !imagePath.isEmpty()) {
            Path sourcePath = Paths.get(imagePath);

            imageFileName = sourcePath.getFileName().toString();
            Path targetDirPath = Paths.get(imagesDirPath);
            if (!Files.exists(targetDirPath)) {
                Files.createDirectories(targetDirPath);
            }
            Path targetPath = targetDirPath.resolve(imageFileName);

            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        String imgStyle = "card-image-large";
        if (imagePath != null) {
            if (isOwned) {
                imgStyle += " grayscale";
            }
        }
        writer.write(
                //"<a href=\"" + "..\\CardDetails\\" + imageFileName.replaceAll("\\.[^.]+$", "") + ".html\">" +
                "<a href=\"" + "https://yugioh.fandom.com/wiki/" + card.getPassCode() + /*"..\\CardDetails\\" + imageFileName.replaceAll("\\.[^.]+$", "") + ".html\">"*/"\">" +
                        "<img src=\"" + imagesRelativePath + imageFileName + "\" alt=\"" + cardName + "\" class=\"" + imgStyle + "\">" +
                        "</a>\n");

        //generateCardDetailsHtml(dirPath, card);
    }
}