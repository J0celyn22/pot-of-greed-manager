package Model.FormatList;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link CardListToHtml}.
 * <p>
 * {@code generateOuicheListHtml} and both {@code generateOuicheListMosaicHtml}
 * overloads are deliberately NOT covered here: all three either read
 * {@code OuicheList}'s static state directly or are OuicheList-specific
 * output paths, the same caution zone flagged at the very start of this
 * session (live bug-fix conversation elsewhere touching OuicheList).
 * {@code generateHtml}, {@code generateHtmlWithOwned}, and
 * {@code generateMenu} don't touch that area at all.
 * <p>
 * All fixture cards use a distinct, made-up {@code imagePath} containing no
 * hyphen and no digit-only ".jpg"/".json" body. That's not a cosmetic choice:
 * {@code DataBaseUpdate.computeAddresses} treats any element containing a
 * "-" (and not starting with one) as a print-code-style name and builds a
 * real candidate local path for it, which {@code writeCardElement} then
 * tries to {@code Files.copy} from -- and fails with a real
 * {@code NoSuchFileException}, since no such fixture file exists on disk.
 * A first pass at these tests used names like "test-fixture-1" and hit
 * exactly that. Plain alphanumeric names (no hyphen, not purely numeric)
 * fall through to a literal JSON-key lookup that finds nothing, so no real
 * file copy is ever attempted.
 * <p>
 * {@code dirPath} is always a "Lists" subfolder of the temp dir, mirroring
 * real usage, since {@code generateHtml}/{@code generateHtmlWithOwned}
 * resolve their image directory as {@code dirPath + "../Images/"} -- passing
 * the temp dir directly as {@code dirPath} would make that escape the temp
 * sandbox into its parent directory.
 */
class CardListToHtmlTest {

    private static CardElement cardWith(String imagePath, String nameEn) {
        Card card = new Card();
        card.setImagePath(imagePath);
        card.setName_EN(nameEn);
        return new CardElement(card, false, false, false, false);
    }

    private static CardElement ownedCardWith(String imagePath, String nameEn) {
        Card card = new Card();
        card.setImagePath(imagePath);
        card.setName_EN(nameEn);
        return new CardElement(card, false, true, false, false);
    }

    private static String listsDirPath(Path tempDir) throws IOException {
        Path listsDir = tempDir.resolve("Lists");
        Files.createDirectories(listsDir);
        return listsDir.toString() + "/";
    }

    // ── generateHtml ─────────────────────────────────────────────────────────

    @Test
    void generateHtml_createsFileWithSanitizedName(@TempDir Path tempDir) throws IOException {
        String dirPath = listsDirPath(tempDir);
        // sanitizeFileName: backslash -> "-", "/" -> "-", quote removed entirely (not replaced).
        CardListToHtml.generateHtml(List.of(cardWith("testfixture1", "Card A")), dirPath, "My\\Deck/Name\"");

        assertTrue(Files.exists(tempDir.resolve("Lists/My-Deck-Name.html")));
    }

    @Test
    void generateHtml_writesTitleWithCountAndPrice(@TempDir Path tempDir) throws IOException {
        String dirPath = listsDirPath(tempDir);
        Card card = new Card();
        card.setImagePath("testfixture2");
        card.setPrice("3.0");
        CardElement element = new CardElement(card, false, false, false, false);

        CardListToHtml.generateHtml(List.of(element), dirPath, "MyList");

        String content = Files.readString(tempDir.resolve("Lists/MyList.html"));
        assertTrue(content.contains("<h1>MyList (1 / " + String.format("%.2f", 3.0f) + "€)</h1>"));
    }

    @Test
    void generateHtml_duplicateImagePath_countedTogether(@TempDir Path tempDir) throws IOException {
        String dirPath = listsDirPath(tempDir);
        List<CardElement> cards = List.of(
                cardWith("testfixturedup", "Same Card"),
                cardWith("testfixturedup", "Same Card"));

        CardListToHtml.generateHtml(cards, dirPath, "DupList");

        String content = Files.readString(tempDir.resolve("Lists/DupList.html"));
        // Both cards share an image path, so createCardsMap must merge them
        // into a single entry with count 2, not two separate entries.
        int cardElementBlocks = content.split("<div class=\"card-element\">", -1).length - 1;
        assertEquals(1, cardElementBlocks, "exactly one card-element block should be written");
        assertTrue(content.contains("<p><b>2</b></p>"));
    }

    @Test
    void generateHtml_createsMenuHtmlAlongsideIfMissing(@TempDir Path tempDir) throws IOException {
        String dirPath = listsDirPath(tempDir);

        CardListToHtml.generateHtml(List.of(cardWith("testfixture3", "Card A")), dirPath, "MyList");

        assertTrue(Files.exists(tempDir.resolve("Menu.html")), "Menu.html should be generated in the parent directory");
    }

    @Test
    void generateHtml_doesNotOverwriteExistingMenuHtml(@TempDir Path tempDir) throws IOException {
        String dirPath = listsDirPath(tempDir);
        Files.writeString(tempDir.resolve("Menu.html"), "custom existing menu");

        CardListToHtml.generateHtml(List.of(cardWith("testfixture4", "Card A")), dirPath, "MyList");

        assertEquals("custom existing menu", Files.readString(tempDir.resolve("Menu.html")));
    }

    // ── generateHtmlWithOwned ────────────────────────────────────────────────

    @Test
    void generateHtmlWithOwned_printOwnedFalse_excludesOwnedCards(@TempDir Path tempDir) throws IOException {
        String dirPath = listsDirPath(tempDir);
        List<CardElement> cards = List.of(
                ownedCardWith("testfixtureowned", "Owned Card"),
                cardWith("testfixtureunowned", "Unowned Card"));

        CardListToHtml.generateHtmlWithOwned(cards, dirPath, "MixedList", false);

        String content = Files.readString(tempDir.resolve("Lists/MixedList.html"));
        assertTrue(content.contains("Unowned Card"));
        assertFalse(content.contains("Owned Card"));
    }

    @Test
    void generateHtmlWithOwned_printOwnedTrue_includesBoth_unownedFirst(@TempDir Path tempDir) throws IOException {
        String dirPath = listsDirPath(tempDir);
        List<CardElement> cards = List.of(
                ownedCardWith("testfixtureowned2", "Owned Card"),
                cardWith("testfixtureunowned2", "Unowned Card"));

        CardListToHtml.generateHtmlWithOwned(cards, dirPath, "MixedList2", true);

        String content = Files.readString(tempDir.resolve("Lists/MixedList2.html"));
        assertTrue(content.contains("Unowned Card"));
        assertTrue(content.contains("Owned Card"));
        assertTrue(content.indexOf("Unowned Card") < content.indexOf("Owned Card"),
                "unowned cards must be written before owned cards");
    }

    // ── generateMenu ─────────────────────────────────────────────────────────

    @Test
    void generateMenu_createsFileWithExpectedStaticLinks(@TempDir Path tempDir) throws IOException {
        String dirPath = tempDir.toString() + "/";
        CardListToHtml.generateMenu(dirPath);

        String content = Files.readString(tempDir.resolve("Menu.html"));
        assertTrue(content.contains("<title>Menu</title>"));
        assertTrue(content.contains("Lists\\Collection Complete List.html"));
        assertTrue(content.contains("Lists\\OuicheList.html"));
    }
}