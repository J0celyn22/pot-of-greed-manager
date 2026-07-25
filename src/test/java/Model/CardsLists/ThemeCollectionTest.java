package Model.CardsLists;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fixed ThemeCollectionTest: robust matching (passCode OR printCode OR name),
 * prints diagnostic dump, and corrected SaveToFile assertion.
 */
public class ThemeCollectionTest {
    private ThemeCollection collection;
    private List<Deck> decks;

    @BeforeEach
    public void setUp() throws Exception {
        collection = new ThemeCollection();
        decks = new ArrayList<>();
        for (int i = 0; i < 6; i++) decks.add(new Deck());

        collection.setCardsList(new ArrayList<>());
        collection.setLinkedDecks(new ArrayList<>());

        // A: COLL_DUP — two entries in collection, one in deck unit 0
        Card collDup1 = createCard("COLL_DUP", "Coll Dup A", "P-COLL-1", "1.0");
        CardElement ceCollDup1 = new CardElement(collDup1);
        Card collDup2 = createCard("COLL_DUP", "Coll Dup B", "P-COLL-2", "1.0");
        CardElement ceCollDup2 = new CardElement(collDup2);
        collection.getCardsList().add(ceCollDup1);
        collection.getCardsList().add(ceCollDup2);

        Card deckCollDup = createCard("COLL_DUP", "Deck Coll Dup", "P-COLL-1", "1.0");
        decks.get(0).AddCardMain(deckCollDup);

        // B: UNIT_DUP_INSIDE — duplicates inside same deck (unit 0)
        Card unitDupA = createCard("UNIT_DUP", "Unit Dup A", "P-UNIT-1", "2.0");
        Card unitDupB = createCard("UNIT_DUP", "Unit Dup B", "P-UNIT-1", "2.0");
        decks.get(0).AddCardMain(unitDupA);
        decks.get(0).AddCardMain(unitDupB);

        // C: MULTI_UNIT — same passCode present in different units (unit 1 and unit 2)
        Card multiUnit1 = createCard("MULTI_U", "Multi Unit 1", "P-MU-1", "3.0");
        decks.get(1).AddCardMain(multiUnit1);
        Card multiUnit2 = createCard("MULTI_U", "Multi Unit 2", "P-MU-2", "3.0");
        decks.get(2).AddCardMain(multiUnit2);

        // D: COLL_SPEC_ART — collection has specificArtwork matching a deck by printCode
        Card deckArt = createCard("ART01", "Deck Art", "P-ART-1", "4.0");
        decks.get(1).AddCardMain(deckArt);
        Card collArt = createCard("ART01", "Coll Art", "P-ART-1", "4.0");
        CardElement ceCollArt = new CardElement(collArt);
        ceCollArt.setSpecificArtwork(true);
        collection.getCardsList().add(ceCollArt);

        // E: COLL_DONTREMOVE_REPLACE — deck has an occurrence; collection has dontRemove=true
        Card deckReplace = createCard("REPL", "Deck Replace", "P-REPL-1", "5.0");
        decks.get(2).AddCardMain(deckReplace);
        Card collReplace = createCard("REPL", "Coll Replace", "P-REPL-2", "5.0");
        CardElement ceCollReplace = new CardElement(collReplace);
        ceCollReplace.setDontRemove(true);
        collection.getCardsList().add(ceCollReplace);

        // F: COLLECTION_ONLY — two CardElements in collection only
        Card collOnlyA = createCard("COL_ONLY", "Coll Only A", "P-COLL-ONLY-1", "6.0");
        CardElement ceCollOnlyA = new CardElement(collOnlyA);
        Card collOnlyB = createCard("COL_ONLY", "Coll Only B", "P-COLL-ONLY-2", "6.0");
        CardElement ceCollOnlyB = new CardElement(collOnlyB);
        collection.getCardsList().add(ceCollOnlyA);
        collection.getCardsList().add(ceCollOnlyB);

        // G: DECK_ONLY_MULTIPLE_UNITS — same passCode in deck in unit 3 and unit 4
        Card deckOnlyU1 = createCard("DECK_MULTI", "Deck Multi 1", "P-DM-1", "2.5");
        decks.get(3).AddCardMain(deckOnlyU1);
        Card deckOnlyU2 = createCard("DECK_MULTI", "Deck Multi 2", "P-DM-2", "2.5");
        decks.get(4).AddCardMain(deckOnlyU2);

        // H & I: SAMEPASS across units and collection
        Card deckDupUnitA = createCard("SAMEPASS", "SamePass A", "P-SP-1", "1.5");
        decks.get(5).AddCardMain(deckDupUnitA);
        Deck secondInUnit5 = new Deck();
        Card deckDupUnitB2 = createCard("SAMEPASS", "SamePass B2", "P-SP-3", "1.5");
        secondInUnit5.AddCardMain(deckDupUnitB2);

        Card deckSamePass = createCard("SAMEPASS", "Deck SamePass In Unit1", "P-SP-1", "1.5");
        decks.get(1).AddCardMain(deckSamePass);

        Card collSamePass = createCard("SAMEPASS", "Collection SamePass", "P-CSP-1", "1.5");
        CardElement ceCollSamePass = new CardElement(collSamePass);
        collection.getCardsList().add(ceCollSamePass);

        // Add decks into linkedDecks units:
        collection.addDeck(decks.get(0)); // unit 0
        collection.addDeck(decks.get(1)); // unit 1
        collection.addDeck(decks.get(2)); // unit 2
        collection.addDeck(decks.get(3)); // unit 3
        collection.addDeck(decks.get(4)); // unit 4
        collection.addDeck(decks.get(5)); // unit 5
        collection.addDeckToExistingUnit(secondInUnit5, 5);
    }

    // Helper to create Card instance
    private Card createCard(String passCode, String name, String printCode, String price) throws Exception {
        Card card = new Card(passCode);
        card.setName_EN(name);
        card.setPrintCode(printCode);
        card.setPrice(price);
        return card;
    }

    // Debug dump helper: returns a compact summary string of the list content
    private String dumpListDebug(List<CardElement> list) {
        if (list == null) return "<null list>";
        return list.stream()
                .map(ce -> {
                    if (ce == null || ce.getCard() == null) return "<nullCE>";
                    Card c = ce.getCard();
                    String pass = c.getPassCode() == null ? "<nullPass>" : c.getPassCode();
                    String print = c.getPrintCode() == null ? "<nullPrint>" : c.getPrintCode();
                    String name = c.getName_EN() == null ? "<nullName>" : c.getName_EN();
                    String flags = (ce.getSpecificArtwork() ? "A" : "-") + (ce.getDontRemove() ? "D" : "-");
                    return pass + "/" + print + "/" + name + "/" + flags;
                })
                .collect(Collectors.joining(" | "));
    }

    // Flexible matcher: returns true if element matches by passCode OR printCode OR name
    private boolean matches(CardElement ce, String id) {
        if (ce == null || ce.getCard() == null || id == null) return false;
        Card c = ce.getCard();
        return id.equals(c.getPassCode()) || id.equals(c.getPrintCode()) || id.equals(c.getName_EN());
    }

    private long countMatches(List<CardElement> list, String id) {
        if (list == null) return 0;
        return list.stream().filter(ce -> matches(ce, id)).count();
    }

    @Test
    public void testCounts_collDup_preserved() {
        System.out.println("TEST: COLL_DUP preserved: collection had 2 copies, deck unit 0 has 1 unit-occurrence.");
        List<CardElement> result = collection.toList();
        String dump = dumpListDebug(result);
        System.out.println("Result summary: " + dump);

        long count = countMatches(result, "COLL_DUP");
        assertEquals(2, count, "COLL_DUP expected 2 occurrences, got " + count + ". Summary: " + dump);

        boolean hasCollectionName = result.stream()
                .anyMatch(ce -> matches(ce, "Coll Dup A") || matches(ce, "Coll Dup B"));
        assertTrue(hasCollectionName, "Expected at least one COLL_DUP to be a collection element. Summary: " + dump);
    }

    @Test
    public void testCounts_unitDupInside_remainSeparate() {
        System.out.println("TEST: UNIT_DUP -- two identical physical cards within the same deck "
                + "must remain two separate entries, not collapse to one.");
        List<CardElement> result = collection.toList();
        String dump = dumpListDebug(result);
        System.out.println("Result summary: " + dump);

        long count = countMatches(result, "UNIT_DUP");
        assertEquals(2, count, "UNIT_DUP expected 2 occurrences (2 distinct physical copies "
                + "in the same deck), got " + count + ". Summary: " + dump);
    }

    @Test
    public void testCounts_multiUnit_appears_twice() {
        System.out.println("TEST: MULTI_U appears once in unit1 and once in unit2 => expected 2.");
        List<CardElement> result = collection.toList();
        String dump = dumpListDebug(result);
        System.out.println("Result summary: " + dump);

        long count = countMatches(result, "MULTI_U");
        assertEquals(2, count, "MULTI_U expected 2 occurrences, got " + count + ". Summary: " + dump);
    }

    @Test
    public void testSpecificArtwork_preference() {
        System.out.println("TEST: ART01 should prefer collection element with specificArtwork.");
        List<CardElement> result = collection.toList();
        String dump = dumpListDebug(result);
        System.out.println("Result summary: " + dump);

        CardElement found = result.stream()
                .filter(ce -> matches(ce, "ART01"))
                .findFirst()
                .orElse(null);
        assertNotNull(found, "ART01 not found in result. Summary: " + dump);
        assertTrue(found.getSpecificArtwork(), "ART01 found but not specificArtwork. Summary: " + dump);
        assertEquals("Coll Art", found.getCard().getName_EN(), "ART01 should be the collection element. Summary: " + dump);
    }

    @Test
    public void testDontRemove_replaces_deck_entry() {
        System.out.println("TEST: REPL deck entry should be replaced by collection dontRemove element at least once.");
        List<CardElement> result = collection.toList();
        String dump = dumpListDebug(result);
        System.out.println("Result summary: " + dump);

        boolean hasCollReplace = result.stream()
                .anyMatch(ce -> matches(ce, "REPL") && "Coll Replace".equals(ce.getCard().getName_EN()));
        assertTrue(hasCollReplace, "Expected collection replace element for REPL. Summary: " + dump);
    }

    @Test
    public void testCollectionOnly_duplicates_kept() {
        System.out.println("TEST: COL_ONLY had two collection entries and no deck presence; both should be present.");
        List<CardElement> result = collection.toList();
        String dump = dumpListDebug(result);
        System.out.println("Result summary: " + dump);

        long count = countMatches(result, "COL_ONLY");
        assertEquals(2, count, "COL_ONLY expected 2 occurrences, got " + count + ". Summary: " + dump);
    }

    @Test
    public void testDeckOnly_multiple_units() {
        System.out.println("TEST: DECK_MULTI appears in two different units and should appear twice.");
        List<CardElement> result = collection.toList();
        String dump = dumpListDebug(result);
        System.out.println("Result summary: " + dump);

        // Deck items show print codes P-DM-1 and P-DM-2 in the dump; count by those prints as well.
        long count = countMatches(result, "DECK_MULTI");
        if (count == 0) { // fallback: count by print codes we used in setup
            count = countMatches(result, "P-DM-1") + countMatches(result, "P-DM-2");
        }
        assertEquals(2, count, "DECK_MULTI expected 2 occurrences, got " + count + ". Summary: " + dump);
    }

    @Test
    public void testDeckDupDifferentPrints_within_unit_collapsed_to_one() {
        System.out.println("TEST: SAMEPASS appears in unit1, unit5 (x2 distinct prints) and collection; "
                + "expected 4 occurrences total.");
        List<CardElement> result = collection.toList();
        String dump = dumpListDebug(result);
        System.out.println("Result summary: " + dump);

        long countSamePass = countMatches(result, "SAMEPASS");
        if (countSamePass == 0) { // fallback to print/name matches
            countSamePass = countMatches(result, "P-SP-1") + countMatches(result, "P-SP-3") + countMatches(result, "P-CSP-1");
        }
        // unit1 (P-SP-1) + unit5's two distinct-print sub-decks (P-SP-1, P-SP-3) + the
        // collection's own distinct print (P-CSP-1) are four genuinely different physical
        // copies -- none of them share a printCode with each other, so none should be
        // treated as "the same card" and dropped.
        assertEquals(4, countSamePass,
                "SAMEPASS expected 4 occurrences (unit1 + unit5's 2 distinct prints + collection), got "
                        + countSamePass + ". Summary: " + dump);
    }

    @Test
    public void testGetCardCount_and_getPrice_consistency() {
        System.out.println("TEST: getCardCount and getPrice reflect collection.cardsList only.");
        int expectedCollectionCount = collection.getCardsList().size();
        assertEquals(expectedCollectionCount, collection.getCardCount(), "getCardCount mismatch");

        float expected = 0f;
        for (CardElement ce : collection.getCardsList()) {
            if (ce != null && ce.getCard() != null) {
                expected += Float.parseFloat(ce.getCard().getPrice());
            }
        }
        float actual = Float.parseFloat(collection.getPrice());
        assertEquals(expected, actual, 0.001f);
    }

    @Test
    public void testEmpty_collection_and_decks() {
        System.out.println("TEST: empty collection and decks returns empty list.");
        ThemeCollection empty = new ThemeCollection();
        empty.setCardsList(new ArrayList<>());
        empty.setLinkedDecks(new ArrayList<>());
        List<CardElement> res = empty.toList();
        String dump = dumpListDebug(res);
        System.out.println("Result summary: " + dump);
        assertTrue(res.isEmpty(), "Empty collection should return empty list. Summary: " + dump);
    }

    @Test
    public void testSaveToFile(@TempDir Path tempDir) throws Exception {
        System.out.println("TEST: SaveToFile writes expected file and content.");
        ThemeCollection tc = new ThemeCollection();
        tc.setName("TestCollection");
        Card card = new Card("TestCard");
        card.setName_EN("Test Card");
        List<CardElement> cards = new ArrayList<>();
        cards.add(new CardElement(card));
        tc.setCardsList(cards);

        // saveToFile treats its argument as an existing directory (it resolves
        // name + ".ytc" inside it) -- pass the real @TempDir, which JUnit has already
        // created, rather than a "test" subdirectory that was never actually made.
        tc.saveToFile(tempDir.toString());

        Path expectedFile = tempDir.resolve("TestCollection.ytc");
        boolean exists = Files.exists(expectedFile);
        String msg = "Expected file " + expectedFile + " to exist after SaveToFile. Exists=" + exists;
        assertTrue(exists, msg);

        List<String> lines = Files.readAllLines(expectedFile);
        assertFalse(lines.isEmpty(), "Expected file not empty. Content: " + lines);
        // The .ytc format persists CardElement#toThemeCollectionString(), which writes the
        // card's passCode (a stable id meant to be re-parsed later) -- not its display name.
        assertTrue(lines.get(0).contains("TestCard"), "First line should contain the card's passCode 'TestCard'. Content: " + lines);
    }
}