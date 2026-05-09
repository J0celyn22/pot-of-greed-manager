package View;

import Model.CardsLists.Card;
import Model.CardsLists.CardCondition;
import Model.CardsLists.CardElement;
import Model.CardsLists.CardRarity;
import Model.Database.DataBaseUpdate;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Modal popup for editing the per-copy properties of a {@link CardElement}:
 * condition, rarity, print code, custom tags, and artwork selection.
 *
 * <p>All input controls use the {@code accent-combo} / {@code accent-text-field}
 * CSS classes (black background, yellow-green text and border), identical to
 * {@link FilterPane}.  Clicking outside the window closes it.</p>
 */
public class CardEditPopup extends Stage {

    // ── Theme ────────────────────────────────────────────────────────────────
    private static final String BG = "#100317";
    private static final String ACCENT = "#cdfc04";

    // Label column: fixed width so all controls align on the same left edge.
    private static final double LABEL_COL_WIDTH = 90;
    private static final String LABEL_CSS = "-fx-fill: white; -fx-font-size: 13;";

    // ── Model ────────────────────────────────────────────────────────────────
    private final CardElement element;
    private final Card card;

    // ── Transient UI state ───────────────────────────────────────────────────
    /**
     * Rarity resolved from the combo; may be null (= not set).
     */
    private CardRarity selectedRarity;
    /**
     * 1-based artwork index; 1 = default / not artwork-specific.
     */
    private int selectedArtwork;
    /**
     * Checkbox wired to element.specificArtwork.
     */
    private CheckBox specificArtworkCheck;
    /**
     * All artwork-variant Cards for this card, in database order (index 0 = artwork 1).
     * Populated once in {@link #buildArtworkSelector()} via
     * {@link Model.Database.CardDatabaseManager#getAliasCards(int)}.
     */
    private List<Model.CardsLists.Card> artworkAliases;

    // ── Controls ─────────────────────────────────────────────────────────────
    private ComboBox<String> conditionCombo;
    private ComboBox<String> rarityCombo;
    private ComboBox<String> printCodeCombo;
    private TextField tagsField;
    private javafx.scene.layout.FlowPane artworkBox;

    // ── Lazily-loaded data ────────────────────────────────────────────────────
    private List<String> allRarityDisplayNames; // available-first, then the rest
    private List<String> printcodeSetNames;

    // ── Callback ─────────────────────────────────────────────────────────────
    private Runnable onOk;

    // ════════════════════════════════════════════════════════════════════════
    // Construction
    // ════════════════════════════════════════════════════════════════════════

    public CardEditPopup(CardElement element) {
        this.element = element;
        this.card = (element != null) ? element.getCard() : null;

        this.selectedRarity = (element != null) ? element.getRarity() : null;
        this.selectedArtwork = (element != null
                && Boolean.TRUE.equals(element.getSpecificArtwork())
                && element.getArtwork() > 0)
                ? element.getArtwork() : 1;

        initStyle(StageStyle.UNDECORATED);
        setResizable(false);

        Scene scene = new Scene(buildRoot(), Color.TRANSPARENT);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) close();
        });

        // The popup is a separate Stage with its own Scene, so it gets no
        // stylesheet by default — load the same styles.css as the main window.
        try {
            java.net.URL cssUrl = getClass().getResource("/styles.css");
            if (cssUrl == null) {
                java.io.File f = new java.io.File("src/main/resources/styles.css");
                if (f.exists()) cssUrl = f.toURI().toURL();
            }
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        } catch (Exception ignored) {
        }

        setScene(scene);
        sizeToScene();

        // Close whenever this Stage loses focus (user clicked outside)
        focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) Platform.runLater(this::close);
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // Public API
    // ════════════════════════════════════════════════════════════════════════

    public void setOnOk(Runnable onOk) {
        this.onOk = onOk;
    }

    /**
     * Shows the popup centred on the window that owns {@code anchor}, or centred
     * on the primary screen as a fallback.
     */
    /**
     * Forces the arrow-button triangle of both editable ComboBoxes to the
     * accent colour using inline styles.  Must be called after the Stage has
     * been shown so that JavaFX has already built the combo skin nodes.
     */
    private void applyArrowStyles() {
        for (ComboBox<?> cb : List.of(rarityCombo, printCodeCombo)) {
            javafx.scene.Node arrowBtn = cb.lookup(".arrow-button");
            if (arrowBtn != null)
                arrowBtn.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
            javafx.scene.Node arrow = cb.lookup(".arrow");
            if (arrow != null)
                arrow.setStyle("-fx-background-color: #cdfc04;");
        }
    }

    public void showCenteredOn(javafx.scene.Node anchor) {
        show();
        // Style the editable-combo arrows programmatically — CSS selectors for
        // :editable .arrow-button are overridden by JavaFX's internal stylesheet
        // regardless of specificity, so inline styles are the only reliable fix.
        Platform.runLater(this::applyArrowStyles);
        Platform.runLater(() -> {
            try {
                if (anchor != null && anchor.getScene() != null
                        && anchor.getScene().getWindow() != null) {
                    javafx.stage.Window w = anchor.getScene().getWindow();
                    setX(w.getX() + (w.getWidth() - getWidth()) / 2.0);
                    setY(w.getY() + (w.getHeight() - getHeight()) / 2.0);
                    return;
                }
            } catch (Exception ignored) {
            }
            javafx.stage.Screen s = javafx.stage.Screen.getPrimary();
            javafx.geometry.Rectangle2D b = s.getVisualBounds();
            setX(b.getMinX() + (b.getWidth() - getWidth()) / 2.0);
            setY(b.getMinY() + (b.getHeight() - getHeight()) / 2.0);
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // Root layout
    // ════════════════════════════════════════════════════════════════════════

    private VBox buildRoot() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(18));
        root.setPrefWidth(490);
        root.setStyle(
                "-fx-background-color: " + BG + ";" +
                        "-fx-border-color: " + ACCENT + ";" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 6;" +
                        "-fx-background-radius: 6;");

        // ── Title ────────────────────────────────────────────────────────────
        String cardName = (card != null && card.getName_EN() != null)
                ? card.getName_EN() : "Card";
        Text title = new Text("Edit Card: " + cardName);
        title.setStyle("-fx-fill: " + ACCENT + "; -fx-font-size: 15; -fx-font-weight: bold;");

        // ── Fields ───────────────────────────────────────────────────────────
        conditionCombo = buildConditionCombo();
        rarityCombo = buildRarityCombo();
        printCodeCombo = buildPrintCodeCombo();

        tagsField = new TextField();
        tagsField.getStyleClass().add("accent-text-field");
        tagsField.setPrefHeight(24);
        tagsField.setPromptText("tag1, tag with spaces, another");
        if (element != null && element.getCustomTags() != null
                && !element.getCustomTags().isEmpty())
            tagsField.setText(String.join(", ", element.getCustomTags()));

        artworkBox = new javafx.scene.layout.FlowPane(8, 8);
        artworkBox.setPrefWrapLength(370);
        buildArtworkSelector();

        specificArtworkCheck = new CheckBox("Specific artwork");
        specificArtworkCheck.setStyle("-fx-text-fill: white; -fx-font-size: 12;");
        specificArtworkCheck.setSelected(
                element != null && Boolean.TRUE.equals(element.getSpecificArtwork()));
        // Unchecking the box does not change the highlighted artwork thumbnail —
        // the user can re-check it or click a different thumbnail.


        // ── Buttons ──────────────────────────────────────────────────────────
        Button cancelBtn = makeButton("Cancel", false);
        Button okBtn = makeButton("OK", true);
        cancelBtn.setOnAction(e -> close());
        okBtn.setOnAction(e -> {
            applyChanges();
            if (onOk != null) onOk.run();
            close();
        });

        HBox buttonRow = new HBox(10, cancelBtn, okBtn);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(
                title,
                makeSep(),
                makeRow("Condition", conditionCombo),
                makeRow("Rarity", rarityCombo),
                makeRow("PrintCode", printCodeCombo),
                makeRow("Tags", tagsField),
                makeRow("Artwork",
                        new VBox(6, specificArtworkCheck, artworkBox)),
                makeSep(),
                buttonRow);
        return root;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Condition combo (non-editable drop-down, like FilterPane combos)
    // ════════════════════════════════════════════════════════════════════════

    private ComboBox<String> buildConditionCombo() {
        ComboBox<String> cb = new ComboBox<>();
        cb.getStyleClass().add("accent-combo");
        cb.setPrefHeight(24);
        cb.setMaxHeight(24);

        cb.getItems().add("(Not set)");
        for (CardCondition c : CardCondition.values())
            cb.getItems().add(c.getDisplayName() + "  (" + c.getCode() + ")");

        CardCondition cur = (element != null) ? element.getCondition() : null;
        if (cur != null)
            cb.setValue(cur.getDisplayName() + "  (" + cur.getCode() + ")");
        else
            cb.setValue("(Not set)");

        return cb;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Rarity combo (editable + arrow drop-down + autocomplete while typing)
    // ════════════════════════════════════════════════════════════════════════

    private ComboBox<String> buildRarityCombo() {
        allRarityDisplayNames = buildFullRarityNameList();

        ComboBox<String> cb = new ComboBox<>();
        cb.getStyleClass().add("accent-combo");
        cb.setPrefHeight(24);
        cb.setMaxHeight(24);
        cb.setEditable(true);
        cb.getEditor().getStyleClass().add("accent-text-field");

        // Full list shown when the arrow is clicked
        cb.setItems(FXCollections.observableArrayList(allRarityDisplayNames));

        if (selectedRarity != null)
            cb.setValue(selectedRarity.getDisplayName());

        // While typing, narrow the drop-down list
        cb.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            // Skip if the change was caused by selecting an item (value == text)
            String selected = cb.getSelectionModel().getSelectedItem();
            if (selected != null && selected.equals(newVal)) return;
            filterComboItems(cb, allRarityDisplayNames, newVal);
        });

        // When an item is picked, resolve the enum value
        cb.setOnAction(e -> selectedRarity = resolveRarity(cb.getValue()));

        return cb;
    }

    /**
     * Available rarities for this card first, then all others.
     */
    private List<String> buildFullRarityNameList() {
        List<String> names = new ArrayList<>();
        List<CardRarity> available = (card != null && card.getAvailableRarities() != null)
                ? card.getAvailableRarities() : List.of();
        for (CardRarity r : available)
            names.add(r.getDisplayName());
        for (CardRarity r : CardRarity.values())
            if (!available.contains(r)) names.add(r.getDisplayName());
        return names;
    }

    // ════════════════════════════════════════════════════════════════════════
    // PrintCode combo (editable + arrow drop-down + set-name autocomplete)
    // ════════════════════════════════════════════════════════════════════════

    private ComboBox<String> buildPrintCodeCombo() {
        ComboBox<String> cb = new ComboBox<>();
        cb.getStyleClass().add("accent-combo");
        cb.setPrefHeight(24);
        cb.setMaxHeight(24);
        cb.setEditable(true);
        cb.getEditor().getStyleClass().add("accent-text-field");

        if (card != null && card.getPrintCode() != null)
            cb.setValue(card.getPrintCode());

        // Arrow clicked with empty field → show all set names
        cb.setOnShowing(e -> {
            String typed = cb.getEditor().getText();
            if (typed == null || typed.isBlank()) {
                loadSetNames();
                cb.setItems(FXCollections.observableArrayList(printcodeSetNames));
            }
        });

        // While typing, prefix-match against set names
        cb.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            String selected = cb.getSelectionModel().getSelectedItem();
            if (selected != null && selected.equals(newVal)) return;
            filterPrintcodeItems(cb, newVal);
        });

        // After a set name is chosen, append whatever suffix the user had typed
        cb.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null || newItem.contains("-")) return;
            // newItem is a raw set name — append "-" so the user can type the number
            String full = newItem + "-";
            Platform.runLater(() -> {
                cb.getEditor().setText(full);
                cb.getEditor().positionCaret(full.length());
            });
        });

        return cb;
    }

    private void filterPrintcodeItems(ComboBox<String> cb, String typed) {
        loadSetNames();
        if (typed == null || typed.isBlank()) {
            cb.setItems(FXCollections.observableArrayList(printcodeSetNames));
            return;
        }
        int dashIdx = typed.indexOf('-');
        String setPart = (dashIdx >= 0 ? typed.substring(0, dashIdx) : typed).toUpperCase();
        if (setPart.isEmpty()) return;

        List<String> matches = new ArrayList<>();
        for (String name : printcodeSetNames) {
            if (name.toUpperCase().startsWith(setPart)) {
                matches.add(name);
                if (matches.size() >= 20) break;
            }
        }
        cb.setItems(FXCollections.observableArrayList(matches));
        if (!matches.isEmpty()) cb.show();
        else cb.hide();
    }

    private void loadSetNames() {
        if (printcodeSetNames == null) {
            try {
                printcodeSetNames = Model.Database.PrintCodeToKonamiId.getSetNames();
            } catch (Exception ignored) {
                printcodeSetNames = List.of();
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Shared combo filter helper
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Narrows {@code cb}'s item list to entries containing {@code typed} (case-insensitive).
     */
    private void filterComboItems(ComboBox<String> cb, List<String> fullList, String typed) {
        if (typed == null || typed.isBlank()) {
            cb.setItems(FXCollections.observableArrayList(fullList));
        } else {
            String lower = typed.toLowerCase();
            List<String> filtered = new ArrayList<>();
            for (String s : fullList)
                if (s.toLowerCase().contains(lower)) filtered.add(s);
            cb.setItems(FXCollections.observableArrayList(filtered));
        }
        // Keep the popup open while the user is typing
        if (!cb.isShowing()) cb.show();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Artwork selector
    // ════════════════════════════════════════════════════════════════════════

    private void buildArtworkSelector() {
        artworkBox.getChildren().clear();
        if (card == null) {
            artworkBox.getChildren().add(grayNote("No card available"));
            return;
        }

        // Resolve all artwork-variant Cards via the database alias map.
        try {
            String pc = card.getPassCode();
            if (pc != null && !pc.isBlank()) {
                artworkAliases = Model.Database.CardDatabaseManager.getAliasCards(Integer.parseInt(pc));
            }
        } catch (Exception ignored) {
        }

        if (artworkAliases == null || artworkAliases.isEmpty()) {
            artworkBox.getChildren().add(grayNote("Image not found"));
            return;
        }

        for (Model.CardsLists.Card alias : artworkAliases) {
            String imagePath = alias.getImagePath();
            String[] addresses = imagePath != null
                    ? DataBaseUpdate.getAddresses(imagePath + ".jpg") : null;
            String fileUrl = (addresses != null && addresses.length > 0)
                    ? "file:" + addresses[0] : null;
            int artNum = parseArtNumber(alias);
            artworkBox.getChildren().add(buildArtworkThumb(fileUrl, artNum));
        }
    }

    private int parseArtNumber(Model.CardsLists.Card alias) {
        try {
            return Integer.parseInt(alias.getArtNumber());
        } catch (Exception e) {
            return 1;
        }
    }

    private StackPane buildArtworkThumb(String imagePath, int artNum) {
        ImageView iv = new ImageView();
        iv.setFitWidth(72);
        iv.setFitHeight(102);
        iv.setPreserveRatio(true);
        try {
            iv.setImage(new Image(imagePath, 72, 102, true, true));
        } catch (Exception ignored) {
        }

        StackPane pane = new StackPane(iv);
        // Padding = 3px around image; the accent border sits on the pane itself.
        pane.setPadding(new Insets(3));
        pane.setUserData(artNum);
        pane.setCursor(javafx.scene.Cursor.HAND);
        refreshArtworkBorder(pane, artNum);

        pane.setOnMouseClicked(e -> {
            selectedArtwork = artNum;
            if (specificArtworkCheck != null) specificArtworkCheck.setSelected(true);
            for (javafx.scene.Node n : artworkBox.getChildren())
                if (n instanceof StackPane && n.getUserData() instanceof Integer)
                    refreshArtworkBorder((StackPane) n, (Integer) n.getUserData());
        });
        return pane;
    }

    private void refreshArtworkBorder(StackPane pane, int artNum) {
        if (artNum == selectedArtwork) {
            // Match the selection rectangle used on cards in the middle and right panes:
            // solid accent-colour border, no radius (rectangle), same thickness.
            pane.setStyle(
                    "-fx-border-color: " + ACCENT + ";" +
                            "-fx-border-width: 3;" +
                            "-fx-border-radius: 0;" +
                            "-fx-background-color: transparent;");
        } else {
            pane.setStyle(
                    "-fx-border-color: transparent;" +
                            "-fx-border-width: 3;" +
                            "-fx-border-radius: 0;" +
                            "-fx-background-color: transparent;");
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Apply on OK
    // ════════════════════════════════════════════════════════════════════════

    private void applyChanges() {
        if (element == null) return;

        // ── Condition ────────────────────────────────────────────────────────
        String condVal = conditionCombo.getValue();
        if (condVal == null || condVal.equals("(Not set)")) {
            element.setCondition(null);
        } else {
            CardCondition found = null;
            for (CardCondition c : CardCondition.values())
                if (condVal.startsWith(c.getDisplayName())) {
                    found = c;
                    break;
                }
            element.setCondition(found);
        }

        // ── Rarity ───────────────────────────────────────────────────────────
        // selectedRarity is updated live by setOnAction; fall back to the editor text
        if (selectedRarity == null)
            selectedRarity = resolveRarity(rarityCombo.getEditor().getText());
        element.setRarity(selectedRarity);

        // ── PrintCode ────────────────────────────────────────────────────────
        if (card != null) {
            String pc = printCodeCombo.getEditor().getText().trim();
            card.setPrintCode(pc.isEmpty() ? null : pc);
        }

        // ── Tags ─────────────────────────────────────────────────────────────
        List<String> tags = new ArrayList<>();
        String tagText = tagsField.getText().trim();
        if (!tagText.isEmpty())
            for (String t : tagText.split(",")) {
                String tag = t.strip();
                if (!tag.isEmpty()) tags.add(tag);
            }
        element.setCustomTags(tags);

        // ── Artwork ──────────────────────────────────────────────────────────
        if (artworkAliases != null && artworkAliases.size() > 1) {
            boolean isSpecific = specificArtworkCheck != null
                    && specificArtworkCheck.isSelected();
            element.setSpecificArtwork(isSpecific);
            element.setArtwork(selectedArtwork);
            // Update the Card reference on the element to the chosen alias so that
            // imagePath and passCode reflect the selected artwork everywhere.
            int aliasIdx = selectedArtwork - 1;
            if (aliasIdx >= 0 && aliasIdx < artworkAliases.size()) {
                element.setCard(artworkAliases.get(aliasIdx));
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Small layout / style helpers
    // ════════════════════════════════════════════════════════════════════════

    /**
     * A labelled HBox row: fixed-width white Text on the left, control on the right.
     * Mirrors the FilterPane row style ("Attribute :", "Type :", etc.).
     */
    private HBox makeRow(String labelText, javafx.scene.Node control) {
        Text label = new Text(labelText + " :");
        label.setStyle(LABEL_CSS);

        Region labelRegion = new Region();
        labelRegion.setPrefWidth(LABEL_COL_WIDTH);
        labelRegion.setMinWidth(LABEL_COL_WIDTH);

        // Stack the label over the transparent region so it is left-aligned but
        // the region provides the consistent column width.
        StackPane labelCell = new StackPane(labelRegion, label);
        StackPane.setAlignment(label, Pos.CENTER_LEFT);

        HBox row = new HBox(8, labelCell, control);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        return row;
    }

    private Separator makeSep() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: " + ACCENT + "; -fx-opacity: 0.35;");
        return sep;
    }

    private Button makeButton(String text, boolean isPrimary) {
        Button b = new Button(text);
        String base =
                "-fx-background-color: " + (isPrimary ? "#2a0560" : "#1e0530") + ";" +
                        "-fx-text-fill: " + ACCENT + ";" +
                        "-fx-font-size: 12px; -fx-font-weight: bold;" +
                        "-fx-border-color: " + ACCENT + "; -fx-border-width: 1;" +
                        "-fx-border-radius: 3; -fx-background-radius: 3;" +
                        "-fx-cursor: hand; -fx-padding: 5 18 5 18;";
        b.setStyle(base);
        b.setOnMouseEntered(e -> b.setStyle(base.replace(isPrimary ? "#2a0560" : "#1e0530", "#3d0880")));
        b.setOnMouseExited(e -> b.setStyle(base));
        if (isPrimary) b.setDefaultButton(true);
        return b;
    }

    private Text grayNote(String msg) {
        Text t = new Text(msg);
        t.setStyle("-fx-fill: #888; -fx-font-size: 11;");
        return t;
    }

    // ── Rarity resolution ─────────────────────────────────────────────────────
    private CardRarity resolveRarity(String text) {
        if (text == null || text.isBlank()) return null;
        CardRarity r = CardRarity.fromDisplayName(text.trim());
        if (r == null) r = CardRarity.fromCode(text.trim());
        return r;
    }
}