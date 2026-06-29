package Model.CardsLists;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class OwnedCardsCollection implements CardsListFile {
    private List<Box> ownedCollection;

    public OwnedCardsCollection(String filePathStr) throws Exception {
        List<String> collectionFileList = CardsListFile.readFile(filePathStr);
        this.ownedCollection = new ArrayList<>();

        for (String line : collectionFileList) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                continue;
            }
            // Only skip pure comment lines — TKN cards, EN-only codes, etc. are valid

            if (trimmed.startsWith("=")) {
                // Box header — strip decorators and store clean name
                this.ownedCollection.add(new Box(extractName(trimmed, '=')));
            } else if (trimmed.startsWith("-")) {
                // Category header — strip decorators and store clean name
                if (!this.ownedCollection.isEmpty()) {
                    Box lastBox = this.ownedCollection.get(this.ownedCollection.size() - 1);
                    lastBox.AddCategory(extractName(trimmed, '-'));
                }
            } else {
                // Card line
                if (!this.ownedCollection.isEmpty()) {
                    try {
                        addCardToLastBox(new CardElement(trimmed));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    public OwnedCardsCollection() {
        this.ownedCollection = new ArrayList<>();
    }

    // --- utility ---

    /**
     * Strips only the leading and trailing decorator characters (= or -)
     * from a raw file line, preserving hyphens inside the name.
     */
    public static String extractName(String s, char decorator) {
        int start = 0;
        while (start < s.length() && s.charAt(start) == decorator) {
            start++;
        }
        int end = s.length();
        while (end > start && s.charAt(end - 1) == decorator) {
            end--;
        }
        return s.substring(start, end).trim();
    }

    /**
     * Box header: [leading '='*count][name][trailing '=']
     * leading  = 5 + depth*3 (depth 0 = top level)
     * total    = 50, but always at least leading + name.length + 3
     */
    private static String formatBoxLine(String name, int depth) {
        int leading = 5 + depth * 3;
        int trailing = Math.max(3, 50 - leading - name.length());
        return "=".repeat(leading) + name + "=".repeat(trailing);
    }

    /**
     * Category header: ---[name][trailing '-']
     * total = 50, but always at least 3 + name.length + 3
     */
    private static String formatCategoryLine(String name) {
        int trailing = Math.max(3, 50 - 3 - name.length());
        return "---" + name + "-".repeat(trailing);
    }

    /**
     * Recursively writes a box and all its categories and sub-boxes to the given writer.
     */
    private static void writeBox(PrintWriter writer, Box box, int depth) {
        String boxName = box.getName() == null ? "" : box.getName();
        writer.println(formatBoxLine(boxName, depth));

        for (CardsGroup group : box.getContent()) {
            String groupName = group.getName() == null ? "" : group.getName();
            // Only write the category header if the group has a name.
            // Unnamed groups arise from boxes where cards appear directly (no category line).
            if (!groupName.isEmpty()) {
                writer.println(formatCategoryLine(groupName));
            }
            for (CardElement card : group.getCardList()) {
                String cardLine = card.toCollectionString();
                if (cardLine != null && !cardLine.isEmpty()) {
                    writer.println(cardLine);
                }
            }
        }

        if (box.getSubBoxes() != null) {
            for (Box subBox : box.getSubBoxes()) {
                writeBox(writer, subBox, depth + 1);
            }
        }
    }

    // --- public API ---

    public List<Box> getOwnedCollection() {
        return ownedCollection;
    }

    public void setOwnedCollection(List<Box> ownedCollection) {
        this.ownedCollection = ownedCollection;
    }

    /**
     * Returns the total number of card categories (CardsGroups) across all boxes.
     * For a count of individual cards, use {@link #getCardCount()}.
     */
    public int getCategoryCount() {
        int count = 0;
        for (Box box : ownedCollection) {
            count += box.getContent().size();
        }
        return count;
    }

    public void addBox(String boxName) {
        this.ownedCollection.add(new Box(boxName));
    }

    public void addCategoryToLastBox(String categoryName) {
        this.ownedCollection.get(this.ownedCollection.size() - 1).AddCategory(categoryName);
    }

    public void addCardToLastBox(CardElement card) {
        this.ownedCollection.get(this.ownedCollection.size() - 1).AddCardToLastCategory(card);
    }

    public void SaveCollection(String filePathStr) throws Exception {
        try (PrintWriter writer = new PrintWriter(filePathStr, StandardCharsets.UTF_8)) {
            for (Box box : this.ownedCollection) {
                writeBox(writer, box, 0);
            }
        }
    }

    /**
     * Returns a flat list of every CardElement across all boxes and categories.
     */
    public List<CardElement> toList() {
        List<CardElement> result = new ArrayList<>();
        for (Box box : ownedCollection) {
            for (CardsGroup group : box.getContent()) {
                result.addAll(group.getCardList());
            }
        }
        return result;
    }

    /**
     * Returns the total number of individual cards across all boxes.
     */
    public int getCardCount() {
        int count = 0;
        for (Box box : ownedCollection) {
            count += box.getCardCount();
        }
        return count;
    }

    public String getPrice() {
        float total = 0;
        for (Box box : ownedCollection) {
            total += Float.parseFloat(box.getPrice());
        }
        return String.valueOf(total);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Box box : ownedCollection) {
            sb.append(box.toString()).append('\n');
        }
        return sb.toString();
    }
}