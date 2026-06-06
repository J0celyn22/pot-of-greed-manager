package View;

import Controller.SelectionManager;
import Model.CardsLists.Card;
import Model.Database.DataBaseUpdate;
import Utils.LruImageCache;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Collapsible pane displayed above the navigation menu in the left column.
 *
 * <p>Single card: names, image (ratio-preserved, left-aligned within the fixed
 * rectangle), scrollable description.
 *
 * <p>Multiple cards: "n cards" label, then the same fixed rectangle subdivided
 * into k rows (k = ceil(sqrt(n))). Each row holds images constrained only by
 * their height (cellH), with ratio preserved — images sit flush to each other
 * with only the gap between them, left-aligned, no phantom spacing.
 */
public class CardDetailPane extends VBox {

    // ── Layout constants ──────────────────────────────────────────────────────
    /**
     * Full width of the fixed image rectangle.
     */
    private static final double CONTENT_WIDTH = 335.0;
    /**
     * Fixed height of the image rectangle (single or multi-card).
     */
    private static final double IMAGE_AREA_HEIGHT = 220.0;
    /**
     * Fixed visible height of the description scroll pane (~8 lines).
     */
    private static final double DESC_HEIGHT = 130.0;
    /**
     * Gap between images in the multi-card grid.
     */
    private static final double GRID_GAP = 4.0;
    private static final String BTN_NORMAL =
            "-fx-background-color: #1e0535; " +
                    "-fx-text-fill: #cdfc04; " +
                    "-fx-font-size: 12; " +
                    "-fx-font-weight: bold; " +
                    "-fx-padding: 4 10; " +
                    "-fx-cursor: hand; " +
                    "-fx-border-color: #cdfc04; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 4; " +
                    "-fx-background-radius: 4;";
    private static final String BTN_HOVER =
            "-fx-background-color: #cdfc04; " +
                    "-fx-text-fill: #100317; " +
                    "-fx-font-size: 12; " +
                    "-fx-font-weight: bold; " +
                    "-fx-padding: 4 10; " +
                    "-fx-cursor: hand; " +
                    "-fx-border-color: #cdfc04; " +
                    "-fx-border-width: 1; " +
                    "-fx-border-radius: 4; " +
                    "-fx-background-radius: 4;";
    // ── Children ──────────────────────────────────────────────────────────────
    private final Button toggleButton;
    private final VBox contentBox;
    private final Runnable selectionListener;
    private boolean expanded = true;
    // ── Action callbacks (wired by RealMainController) ───────────────────────
    private Runnable onMinusOne;
    private Runnable onPlusOne;
    private Runnable onCompleteToThree;
    private Runnable onEdit;

    public CardDetailPane() {
        setSpacing(0);
        setStyle("-fx-background-color: #100317;");
        getStyleClass().add("card-detail-pane");

        toggleButton = new Button("▼  Card Detail");
        toggleButton.setMaxWidth(Double.MAX_VALUE);
        applyToggleButtonStyle();
        toggleButton.setOnAction(e -> setExpanded(!expanded));

        contentBox = new VBox(6);
        contentBox.setPadding(new Insets(8));
        contentBox.setStyle("-fx-background-color: #100317;");

        getChildren().addAll(toggleButton, contentBox);

        selectionListener = this::refreshContent;
        SelectionManager.addSelectionChangeListener(selectionListener);
        refreshContent();
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds the metadata column shown to the right of the image(s).
     * Displays Rarity, Condition (single-card only) and word-count statistics.
     *
     * <p>Single card: shows the exact word count.<br>
     * Multiple cards: shows "{min} to {max} words - Average: {avg}" computed
     * across all cards that have a non-empty description.
     *
     * @param element  the selected {@link Model.CardsLists.CardElement} for
     *                 rarity/condition (may be {@code null})
     * @param card     the primary {@link Card} (used for single-card word count
     *                 and as a fallback when {@code allCards} is empty)
     * @param allCards all selected cards (used for multi-card word-count stats);
     *                 pass a single-element list (or null) for the single-card view
     */
    private static Node makeMetaPanel(Model.CardsLists.CardElement element,
                                      Card card,
                                      java.util.List<Card> allCards) {
        VBox panel = new VBox(8);
        panel.setAlignment(Pos.TOP_LEFT);
        panel.setStyle("-fx-background-color: #0a0015;");
        panel.setPadding(new Insets(4, 0, 4, 0));

        if (element != null) {
            Model.CardsLists.CardRarity rarity = element.getRarity();
            if (rarity != null) {
                panel.getChildren().add(makeMetaEntry("Rarity", rarity.getDisplayName()));
            }
            Model.CardsLists.CardCondition condition = element.getCondition();
            if (condition != null) {
                panel.getChildren().add(makeMetaEntry("Cond.", condition.getDisplayName()));
            }
        }

        // ── Word count ────────────────────────────────────────────────────────
        boolean multi = allCards != null && allCards.size() > 1;
        if (multi) {
            // Compute min, max, average across all cards with a description
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            long total = 0;
            int count = 0;
            for (Card c : allCards) {
                String desc = c.getDescription();
                if (desc == null || desc.isEmpty()) continue;
                int w = desc.trim().split("\\s+").length;
                if (w < min) min = w;
                if (w > max) max = w;
                total += w;
                count++;
            }
            if (count > 0) {
                long avg = Math.round((double) total / count);
                String value;
                if (min == max) {
                    // All descriptions have the same word count
                    value = min + " words\nAverage: " + avg;
                } else {
                    value = min + " to " + max + " words\nAverage: " + avg;
                }
                panel.getChildren().add(makeMetaEntry("Words", value));
            }
        } else if (card != null) {
            String desc = card.getDescription();
            if (desc != null && !desc.isEmpty()) {
                int w = desc.trim().split("\\s+").length;
                panel.getChildren().add(makeMetaEntry("Words", String.valueOf(w)));
            }
        }

        return panel;
    }

    /**
     * Convenience overload for the single-card view.
     */
    private static Node makeMetaPanel(Model.CardsLists.CardElement element, Card card) {
        return makeMetaPanel(element, card, null);
    }

    // ── Expand / collapse ─────────────────────────────────────────────────────

    /**
     * One labeled metadata row: small grey label above a white value.
     */
    private static VBox makeMetaEntry(String label, String value) {
        Label lbl = new Label(label.toUpperCase());
        lbl.setStyle(
                "-fx-text-fill: #888888; " +
                        "-fx-font-size: 9; " +
                        "-fx-font-weight: bold;");
        Label val = new Label(value);
        val.setWrapText(true);
        val.setStyle(
                "-fx-text-fill: #cdfc04; " +
                        "-fx-font-size: 11; " +
                        "-fx-font-weight: bold;");
        VBox entry = new VBox(1, lbl, val);
        return entry;
    }

    /**
     * All three buttons share the same base style (dark bg, accent text/border).
     * Hover is handled via mouse event handlers because inline styles do not
     * support the :hover pseudo-class.
     * The {@code accent} parameter is kept for API compatibility but no longer
     * affects initial styling — it just documents intent at the call site.
     */
    private static Button makeActionButton(String text, boolean accent) {
        Button btn = new Button(text);
        btn.setStyle(BTN_NORMAL);
        btn.setOnMouseEntered(e -> btn.setStyle(BTN_HOVER));
        btn.setOnMouseExited(e -> btn.setStyle(BTN_NORMAL));
        return btn;
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    /**
     * Wraps any node in a StackPane locked to CONTENT_WIDTH x IMAGE_AREA_HEIGHT.
     * min = pref = max prevents VBox from resizing it. TOP_LEFT alignment keeps
     * content flush to the top-left corner; unused space shows as dark background.
     */
    private static StackPane makeFixedRectangle(Node content) {
        StackPane area = new StackPane(content);
        area.setMinSize(CONTENT_WIDTH, IMAGE_AREA_HEIGHT);
        area.setPrefSize(CONTENT_WIDTH, IMAGE_AREA_HEIGHT);
        area.setMaxSize(CONTENT_WIDTH, IMAGE_AREA_HEIGHT);
        area.setAlignment(Pos.TOP_LEFT);
        area.setStyle("-fx-background-color: #0a0015;");
        return area;
    }

    // ── Single card ───────────────────────────────────────────────────────────

    /**
     * Loads a card image constrained by height only, preserving aspect ratio.
     * Width is left unconstrained (0) so JavaFX derives it from the ratio.
     *
     * @param preferHighRes if true (single-card view), always loads directly at
     *                      full size, ignoring the LRU cache which may hold a small thumbnail.
     *                      If false (multi-card), tries the cache first since thumbnail resolution
     *                      is sufficient at the reduced cell height.
     */
    private static ImageView loadImageView(Card card, double fitHeight,
                                           boolean preferHighRes) {
        Image image = null;

        String fullUrl = null;
        if (card.getImagePath() != null && !card.getImagePath().isEmpty()) {
            String[] addresses = DataBaseUpdate.getAddresses(card.getImagePath() + ".jpg");
            if (addresses != null && addresses.length > 0) {
                fullUrl = "file:" + addresses[0];
            }
        }

        if (fullUrl != null) {
            // For single-card: load directly at the requested height so we get
            // full resolution. The cached thumbnail would look blurry at 220 px.
            // For multi-card: cache is fine because cellH is small.
            if (!preferHighRes) {
                image = LruImageCache.getImage(fullUrl);
            }

            if (image == null || image.isError()) {
                try {
                    // 0 width = unconstrained, JavaFX derives it from height + ratio
                    image = new Image(fullUrl, 0, fitHeight, true, true);
                } catch (Exception ignored) {
                    image = null;
                }
            }
        }

        if (image == null || image.isError()) {
            image = new Image("file:./src/main/resources/placeholder.jpg",
                    0, fitHeight, true, true);
        }

        ImageView iv = new ImageView(image);
        iv.setFitHeight(fitHeight);
        iv.setFitWidth(0);
        iv.setPreserveRatio(true);
        return iv;
    }

    /**
     * Convenience overload for multi-card (cache allowed).
     */
    private static ImageView loadImageView(Card card, double fitHeight) {
        return loadImageView(card, fitHeight, false);
    }

    private static Node makePlaceholder() {
        Label lbl = new Label("No card selected");
        lbl.setStyle("-fx-text-fill: #666666; -fx-font-size: 12; -fx-font-style: italic;");
        return lbl;
    }

    /**
     * Called when the user clicks "-1" (remove selected card).
     */
    public void setOnMinusOne(Runnable r) {
        this.onMinusOne = r;
    }

    /**
     * Called when the user clicks "+1" (duplicate selected card).
     */
    public void setOnPlusOne(Runnable r) {
        this.onPlusOne = r;
    }

    /**
     * Called when the user clicks "↑3" (complete to 3 copies in the direct container).
     */
    public void setOnCompleteToThree(Runnable r) {
        this.onCompleteToThree = r;
    }

    // ── Multiple cards ────────────────────────────────────────────────────────

    /**
     * Called when the user clicks the Edit button (open CardEditPopup).
     */
    public void setOnEdit(Runnable r) {
        this.onEdit = r;
    }

    public void dispose() {
        SelectionManager.removeSelectionChangeListener(selectionListener);
    }

    // ── Metadata panel ────────────────────────────────────────────────────────

    private void setExpanded(boolean expand) {
        this.expanded = expand;
        contentBox.setVisible(expand);
        contentBox.setManaged(expand);
        toggleButton.setText(expand ? "▼  Card Detail" : "▶  Card Detail");
    }

    private void applyToggleButtonStyle() {
        toggleButton.setStyle(
                "-fx-background-color: #1e0535; " +
                        "-fx-text-fill: #cdfc04; " +
                        "-fx-font-size: 12; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 6 10; " +
                        "-fx-cursor: hand; " +
                        "-fx-border-color: #cdfc04; " +
                        "-fx-border-width: 0 0 1 0; " +
                        "-fx-background-radius: 0; " +
                        "-fx-alignment: center-left;"
        );
    }

    private void refreshContent() {
        Platform.runLater(() -> {
            contentBox.getChildren().clear();

            Set<Card> selected = SelectionManager.getSelectedCards();
            if (selected == null || selected.isEmpty()) {
                contentBox.getChildren().add(makePlaceholder());
                return;
            }

            List<Card> cards = new ArrayList<>(selected);
            boolean middleSingle = cards.size() == 1
                    && "MIDDLE".equals(SelectionManager.getActivePart());
            if (cards.size() == 1) {
                buildSingleCardView(cards.get(0), middleSingle);
            } else {
                buildMultiCardView(cards);
            }
            // Show -1 / +1 (and Edit when applicable) for any MIDDLE selection
            if ("MIDDLE".equals(SelectionManager.getActivePart())) {
                contentBox.getChildren().add(makeActionButtons(middleSingle));
            }
        });
    }

    // ── Action buttons ───────────────────────────────────────────────────────

    private void buildSingleCardView(Card card, boolean showEdit) {
        VBox namesBox = buildNamesBox(card);
        if (!namesBox.getChildren().isEmpty()) {
            contentBox.getChildren().add(namesBox);
        }
        contentBox.getChildren().add(makeSingleImageArea(card));
        String desc = card.getDescription();
        if (desc != null && !desc.isEmpty()) {
            contentBox.getChildren().add(makeDescriptionScroll(desc));
        }
    }

    private VBox buildNamesBox(Card card) {
        VBox box = new VBox(2);
        addNameRow(box, card.getName_EN(), "EN");
        addNameRow(box, card.getName_FR(), "FR");
        addNameRow(box, card.getName_JA(), "JA");
        if (box.getChildren().isEmpty()) addNameRow(box, card.getNameOrNumber(), null);
        return box;
    }

    private void addNameRow(VBox target, String name, String langCode) {
        if (name == null || name.isEmpty()) return;
        String text = (langCode != null) ? langCode + ": " + name : name;
        Label lbl = new Label(text);
        lbl.setWrapText(true);
        lbl.setMaxWidth(CONTENT_WIDTH);
        lbl.setStyle("-fx-text-fill: white; -fx-font-size: 12; -fx-font-weight: bold;");
        target.getChildren().add(lbl);
    }

    /**
     * Single-card image area: image on the left (ratio-preserved, clickable),
     * metadata panel on the right (rarity, condition, word count).
     * The whole block occupies exactly IMAGE_AREA_HEIGHT.
     */
    private Node makeSingleImageArea(Card card) {
        ImageView iv = loadImageView(card, IMAGE_AREA_HEIGHT, true);
        iv.setStyle("-fx-cursor: hand;");
        iv.setOnMouseClicked(e -> showFullResPopup(card));

        // Retrieve metadata from the selected CardElement (not from Card)
        Model.CardsLists.CardElement element =
                Controller.SelectionManager.getSelectedMiddleElements().stream()
                        .findFirst().orElse(null);
        Node meta = makeMetaPanel(element, card);

        HBox row = new HBox(8, iv, meta);
        row.setAlignment(Pos.TOP_LEFT);
        row.setMinHeight(IMAGE_AREA_HEIGHT);
        row.setPrefHeight(IMAGE_AREA_HEIGHT);
        row.setMaxHeight(IMAGE_AREA_HEIGHT);
        row.setStyle("-fx-background-color: #0a0015;");
        return row;
    }

    // ── Shared fixed rectangle ────────────────────────────────────────────────

    private Node makeDescriptionScroll(String desc) {
        Label lbl = new Label(desc);
        lbl.setWrapText(true);
        lbl.setMaxWidth(CONTENT_WIDTH - 12);
        lbl.setStyle("-fx-text-fill: #dddddd; -fx-font-size: 11; -fx-padding: 4;");
        ScrollPane scroll = new ScrollPane(lbl);
        scroll.setFitToWidth(true);
        scroll.setMinHeight(DESC_HEIGHT);
        scroll.setPrefHeight(DESC_HEIGHT);
        scroll.setMaxHeight(DESC_HEIGHT);
        scroll.setStyle("-fx-background-color: #0a0015; -fx-background: #0a0015;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.NEVER);
        return scroll;
    }

    // ── Image loading ─────────────────────────────────────────────────────────

    private void buildMultiCardView(List<Card> cards) {
        Label countLabel = new Label(cards.size() + " cards");
        countLabel.setStyle(
                "-fx-text-fill: #cdfc04; -fx-font-size: 13; -fx-font-weight: bold;");
        contentBox.getChildren().add(countLabel);
        contentBox.getChildren().add(makeMultiImageArea(cards));
    }

    /**
     * Builds the multi-card image area.
     *
     * <p>k = ceil(sqrt(n)) determines the number of rows (and the height divisor).
     * Each image is constrained only by its height:
     * <pre>  cellH = (IMAGE_AREA_HEIGHT - (k-1)*gap) / k</pre>
     * With ratio preserved, each image renders at its natural width — no phantom
     * spacing, no distortion. Images in a row are placed in an HBox left-to-right.
     * Rows are stacked in a VBox, which is then placed top-left in the fixed rectangle.
     */
    private Node makeMultiImageArea(List<Card> cards) {
        int n = cards.size();
        int k = (int) Math.ceil(Math.sqrt(n));

        // cellH drives the sizing; images choose their own width via ratio
        double cellH = (IMAGE_AREA_HEIGHT - (k - 1) * GRID_GAP) / k;

        VBox rows = new VBox(GRID_GAP);
        rows.setAlignment(Pos.TOP_LEFT);

        int idx = 0;
        while (idx < n) {
            HBox row = new HBox(GRID_GAP);
            row.setAlignment(Pos.CENTER_LEFT);
            // Lock row height to exactly one cell — prevents VBox from stretching rows
            row.setMinHeight(cellH);
            row.setPrefHeight(cellH);
            row.setMaxHeight(cellH);

            for (int col = 0; col < k && idx < n; col++, idx++) {
                // Images are constrained by height only — natural width, no cell wrapper
                final Card card = cards.get(idx);
                ImageView iv = loadImageView(card, cellH);
                iv.setStyle("-fx-cursor: hand;");
                iv.setOnMouseClicked(e -> showFullResPopup(card));
                row.getChildren().add(iv);
            }

            rows.getChildren().add(row);
        }

        // Metadata panel: use the first element available
        Model.CardsLists.CardElement element =
                Controller.SelectionManager.getSelectedMiddleElements().stream()
                        .findFirst().orElse(null);
        // For multi-selection, rarity/condition from the first element;
        // word-count stats (min/max/avg) computed across all selected cards.
        Card firstCard = element != null ? element.getCard() : cards.get(0);
        Node meta = makeMetaPanel(element, firstCard, cards);

        HBox wrapper = new HBox(8, rows, meta);
        wrapper.setAlignment(Pos.TOP_LEFT);
        wrapper.setMinHeight(IMAGE_AREA_HEIGHT);
        wrapper.setPrefHeight(IMAGE_AREA_HEIGHT);
        wrapper.setMaxHeight(IMAGE_AREA_HEIGHT);
        wrapper.setStyle("-fx-background-color: #0a0015;");
        return wrapper;
    }

    // ─────────────────────────────────────────────────────────────────────────

    // ── Full-resolution popup ────────────────────────────────────────────────

    /**
     * Builds the row of action buttons shown below the image / description.
     * -1 and +1 are always shown for a MIDDLE selection.
     * Edit is only shown when exactly 1 card is selected from MIDDLE.
     */
    private javafx.scene.layout.HBox makeActionButtons(boolean showEdit) {
        javafx.scene.layout.HBox bar = new javafx.scene.layout.HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(4, 0, 0, 0));

        Button minusBtn = makeActionButton("-1", false);
        minusBtn.setOnAction(e -> {
            if (onMinusOne != null) onMinusOne.run();
        });

        Button plusBtn = makeActionButton("+1", false);
        plusBtn.setOnAction(e -> {
            if (onPlusOne != null) onPlusOne.run();
        });

        Button completeToThreeBtn = makeActionButton("↑3", false);
        completeToThreeBtn.setOnAction(e -> {
            if (onCompleteToThree != null) onCompleteToThree.run();
        });

        bar.getChildren().addAll(minusBtn, plusBtn, completeToThreeBtn);

        if (showEdit) {
            // ✏ = pencil (U+270F); no external icon library needed
            Button editBtn = makeActionButton("✏  Edit", true);
            editBtn.setOnAction(e -> {
                if (onEdit != null) onEdit.run();
            });
            bar.getChildren().add(editBtn);
        }

        return bar;
    }

    /**
     * Opens a full-screen transparent overlay with a dark dimming layer and the
     * card image centered on top.
     *
     * <ul>
     *   <li>The overlay intercepts all mouse events on the rest of the app,
     *       preventing hover handlers from firing and closing the popup.</li>
     *   <li>Clicking the dark area (outside the image) closes the popup.</li>
     *   <li>Clicking the image itself is consumed — it does not close.</li>
     *   <li>ESC also closes the popup.</li>
     * </ul>
     */
    private void showFullResPopup(Card card) {
        String fullUrl = null;
        if (card.getImagePath() != null && !card.getImagePath().isEmpty()) {
            String[] addresses = DataBaseUpdate.getAddresses(card.getImagePath() + ".jpg");
            if (addresses != null && addresses.length > 0) {
                fullUrl = "file:" + addresses[0];
            }
        }
        if (fullUrl == null) return;

        Image image;
        try {
            image = new Image(fullUrl);
        } catch (Exception e) {
            return;
        }
        if (image.isError()) return;

        // Fit image within 90 % of the screen, ratio preserved
        javafx.geometry.Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        double maxW = screen.getWidth() * 0.9;
        double maxH = screen.getHeight() * 0.9;

        ImageView iv = new ImageView(image);
        iv.setPreserveRatio(true);
        iv.setFitWidth(Math.min(image.getWidth(), maxW));
        iv.setFitHeight(Math.min(image.getHeight(), maxH));

        // Clicking the image must NOT close the popup — consume the event
        iv.setOnMouseClicked(javafx.event.Event::consume);

        // Root covers the entire screen; clicking it (outside the image) closes
        StackPane root = new StackPane(iv);
        root.setPrefSize(screen.getWidth(), screen.getHeight());
        root.setAlignment(Pos.CENTER);
        // Semi-transparent dark overlay — blocks hover events on the app beneath
        root.setStyle("-fx-background-color: rgba(0, 0, 0, 0.72);");
        root.setOnMouseClicked(e -> {
            Stage s = (Stage) root.getScene().getWindow();
            s.close();
        });

        Scene scene = new Scene(root, screen.getWidth(), screen.getHeight());
        // Transparent fill so the rounded/dark overlay shows through
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                ((Stage) scene.getWindow()).close();
            }
        });

        // TRANSPARENT style is required for a see-through scene background
        Stage popup = new Stage(StageStyle.TRANSPARENT);
        popup.setScene(scene);
        popup.setX(screen.getMinX());
        popup.setY(screen.getMinY());
        popup.show();
        popup.requestFocus();
    }
}