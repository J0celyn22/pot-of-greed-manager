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
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
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
     */
    private List<Model.CardsLists.Card> artworkAliases;

    /**
     * The popup content VBox, built once in the constructor.
     */
    private VBox content;

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

        // Determine the initially-selected artwork index.
        // Priority: explicit element.artwork flag → card.artNumber → default 1.
        int resolvedArtwork = 1;
        if (element != null && Boolean.TRUE.equals(element.getSpecificArtwork()) && element.getArtwork() > 0) {
            resolvedArtwork = element.getArtwork();
        } else if (this.card != null) {
            try {
                String an = this.card.getArtNumber();
                if (an != null && !an.isBlank()) {
                    int parsed = Integer.parseInt(an.trim());
                    if (parsed > 0) resolvedArtwork = parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        this.selectedArtwork = resolvedArtwork;

        initStyle(StageStyle.TRANSPARENT);
        setResizable(false);

        // Build the popup content now (fields like conditionCombo are wired here),
        // but defer scene construction to showCenteredOn where the owner window's
        // position and size are known.
        content = buildRoot();
        // Consume clicks inside the content so they don't reach the overlay's
        // close-on-click handler.
        content.setOnMouseClicked(javafx.event.Event::consume);
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

    /**
     * Shows the popup centred on an already-resolved {@link javafx.stage.Window}.
     * Prefer this over {@link #showCenteredOn(javafx.scene.Node)} whenever the
     * anchor node may have been recycled (e.g. after a ListView refresh).
     */
    public void showCenteredOn(javafx.stage.Window ownerWindow) {
        try {
            if (ownerWindow instanceof javafx.stage.Stage) {
                initOwner(ownerWindow);
            }
        } catch (Exception ignored) {
        }

        double overlayX, overlayY, overlayW, overlayH;
        if (ownerWindow != null) {
            overlayX = ownerWindow.getX();
            overlayY = ownerWindow.getY();
            overlayW = ownerWindow.getWidth();
            overlayH = ownerWindow.getHeight();
        } else {
            javafx.geometry.Rectangle2D screen =
                    javafx.stage.Screen.getPrimary().getVisualBounds();
            overlayX = screen.getMinX();
            overlayY = screen.getMinY();
            overlayW = screen.getWidth();
            overlayH = screen.getHeight();
        }

        javafx.scene.Group contentGroup = new javafx.scene.Group(content);
        javafx.scene.layout.StackPane overlay = new javafx.scene.layout.StackPane(contentGroup);
        overlay.setPrefSize(overlayW, overlayH);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.45);");
        overlay.setOnMouseClicked(e -> close());

        Scene scene = new Scene(overlay, overlayW, overlayH);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) close();
        });

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
        setX(overlayX);
        setY(overlayY);
        show();
        Platform.runLater(this::applyArrowStyles);
    }

    public void showCenteredOn(javafx.scene.Node anchor) {
        // Resolve the owner window — must be set before show().
        javafx.stage.Window ownerWindow = null;
        try {
            if (anchor != null && anchor.getScene() != null
                    && anchor.getScene().getWindow() instanceof javafx.stage.Stage) {
                ownerWindow = anchor.getScene().getWindow();
                initOwner(ownerWindow);
            }
        } catch (Exception ignored) {
        }

        // Build the overlay sized to the main window.
        // Falls back to the primary screen if the owner is unknown.
        double overlayX, overlayY, overlayW, overlayH;
        if (ownerWindow != null) {
            overlayX = ownerWindow.getX();
            overlayY = ownerWindow.getY();
            overlayW = ownerWindow.getWidth();
            overlayH = ownerWindow.getHeight();
        } else {
            javafx.geometry.Rectangle2D screen =
                    javafx.stage.Screen.getPrimary().getVisualBounds();
            overlayX = screen.getMinX();
            overlayY = screen.getMinY();
            overlayW = screen.getWidth();
            overlayH = screen.getHeight();
        }

        // Semi-transparent overlay covers exactly the main window area.
        // Clicking outside the popup content closes it (click-outside behaviour).
        // Group prevents StackPane from stretching the popup content to fill
        // the overlay — a Group is never resized by its parent layout.
        javafx.scene.Group contentGroup = new javafx.scene.Group(content);
        StackPane overlay = new StackPane(contentGroup);
        overlay.setPrefSize(overlayW, overlayH);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.45);");
        overlay.setOnMouseClicked(e -> close());

        Scene scene = new Scene(overlay, overlayW, overlayH);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) close();
        });

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
        setX(overlayX);
        setY(overlayY);
        show();
        Platform.runLater(this::applyArrowStyles);
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
        Button cancelBtn = PopupStyleHelper.makeButton("Cancel", false, ACCENT);
        Button okBtn = PopupStyleHelper.makeButton("OK", true, ACCENT);
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
                PopupStyleHelper.makeSeparator(ACCENT),
                makeRow("Condition", conditionCombo),
                makeRow("Rarity", rarityCombo),
                makeRow("PrintCode", printCodeCombo),
                makeRow("Tags", tagsField),
                makeRow("Artwork",
                        new VBox(6, specificArtworkCheck, artworkBox)),
                PopupStyleHelper.makeSeparator(ACCENT),
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

        // ── Item list ────────────────────────────────────────────────────────
        // Create ONE mutable ObservableList and never call cb.setItems() again.
        // Replacing the items list while the popup is open causes JavaFX layout
        // glitches (one-line-high popup, wrong scrollbar, etc.).
        loadSetNames();
        javafx.collections.ObservableList<String> pcItems =
                FXCollections.observableArrayList(printcodeSetNames);
        cb.setItems(pcItems);

        // ── Seed current printCode BEFORE wiring listeners ───────────────────
        // Setting text before the listeners are attached avoids a spurious
        // filter call on construction.
        if (card != null && card.getPrintCode() != null && !card.getPrintCode().isBlank())
            cb.getEditor().setText(card.getPrintCode());

        // ── Suppression flag ─────────────────────────────────────────────────
        // Prevents the text listener re-firing when the selection listener's
        // Platform.runLater sets the editor text programmatically.
        final boolean[] suppressText = {false};

        // ── Text listener: filter items in-place while the user types ─────────
        cb.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressText[0]) return;
            // Skip if this change was caused by the selection model setting the text
            String sel = cb.getSelectionModel().getSelectedItem();
            if (sel != null && sel.equals(newVal)) return;

            // Once the user has typed past the "-" (e.g. "LEDE-EN001"), they are
            // entering the card number — set-name filtering is no longer useful and
            // calling items.setAll() would trigger JavaFX's internal clearAndSelect(),
            // which calls editor.setText(value) and wipes the typed suffix.
            if (newVal != null && newVal.contains("-")) {
                cb.hide();
                return;
            }

            updatePrintcodeItems(pcItems, newVal);

            if (!pcItems.isEmpty() && !cb.isShowing()) cb.show();
            else if (pcItems.isEmpty()) cb.hide();
        });

        // After a set name is chosen, append "-" so the user can type the number
        cb.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem == null || newItem.contains("-")) return;
            // This listener re-fires every time items.setAll() is called while
            // the selection is on a set name (e.g. "LEDE"). If the editor already
            // shows that set name followed by a dash (possibly with more characters
            // the user has typed, e.g. "LEDE-EN001"), do NOT overwrite it.
            String currentText = cb.getEditor().getText();
            if (currentText != null && currentText.startsWith(newItem + "-")) return;
            String full = newItem + "-";
            Platform.runLater(() -> {
                suppressText[0] = true;
                cb.getEditor().setText(full);
                Platform.runLater(() -> {
                    cb.getEditor().deselect();
                    cb.getEditor().positionCaret(full.length());
                    suppressText[0] = false;
                });
            });
        });

        return cb;
    }

    /**
     * Mutates {@code items} in-place to the set names that prefix-match {@code typed}.
     * Never replaces the list reference — the combo's popup stays properly sized.
     */
    private void updatePrintcodeItems(javafx.collections.ObservableList<String> items,
                                      String typed) {
        loadSetNames();
        if (typed == null || typed.isBlank()) {
            items.setAll(printcodeSetNames);
            return;
        }
        int dashIdx = typed.indexOf('-');
        String setPart = (dashIdx >= 0 ? typed.substring(0, dashIdx) : typed).toUpperCase();
        if (setPart.isEmpty()) {
            items.setAll(printcodeSetNames);
            return;
        }
        List<String> matches = new ArrayList<>();
        for (String name : printcodeSetNames) {
            if (name.toUpperCase().startsWith(setPart)) {
                matches.add(name);
                if (matches.size() >= 20) break;
            }
        }
        items.setAll(matches);
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
                            "-fx-border-width: 1.5;" +
                            "-fx-border-radius: 4;" +
                            "-fx-background-color: transparent;");
        } else {
            pane.setStyle(
                    "-fx-border-color: transparent;" +
                            "-fx-border-width: 1.5;" +
                            "-fx-border-radius: 4;" +
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
            int aliasIdx = selectedArtwork - 1;
            if (aliasIdx >= 0 && aliasIdx < artworkAliases.size()) {
                Card alias = artworkAliases.get(aliasIdx);
                // The printCode belongs to this physical copy, not to the artwork
                // variant. Carry it over so it is not lost when the card reference
                // is swapped (card.getPrintCode() was already set above).
                if (card != null) alias.setPrintCode(card.getPrintCode());
                element.setCard(alias);
            }
        }

        // ── Dirty marking ────────────────────────────────────────────────────
        // Editing a card's properties changes the containing element (deck,
        // theme collection, box or category), so mark that owner dirty and
        // refresh the corresponding view, mirroring the pattern used by every
        // other mutation path (move, swap, paste, drag-and-drop, etc.).
        Model.CardsLists.CardsGroup containingGroup = CardTreeCell.findGroupForCardElement(element);
        if (containingGroup != null) {
            CardTreeCell.markDirtyAndRefreshForGroup(containingGroup);
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