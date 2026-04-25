package View;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

/**
 * FilterPane — the top-right "command" area of the AllExistingCards tab.
 * <p>
 * Col 1          │ Col 2            │ Col 3          │ Col 4
 * ───────────────┼──────────────────┼────────────────┼─────────────────
 * Category : [T] [ST]               │ Year           │  [1][2][3][4][5]
 * Attribute : [combo]               │ Word Count     │  [Disable]
 * Type      : [combo]               │ Pack      (right-aligned combo)
 * Lv/Rank/Link:[f][LinkMkr] Scale:[f│ State     (right-aligned combo)  [Disable all]
 * ATK:[f] DEF:[f]                   │ Rarity    (right-aligned combo)  Clear
 * │ Name             │                │  [Active] [All]
 * │ PrintCode        │                │  [📷]
 * │ PassCode         │
 * │ Konami ID        │
 * │ Effect           │
 * │ Genesys Points   │
 * ──────────────────────────────────────────────────────────────────────
 * [LM-BottomLeft]                                 [LM-BottomRight]
 * <p>
 * ─────────────────────────────────────────────────────────────────────
 * FILTER PAGE SYSTEM (5 independent filter pages, selected via [1]-[5])
 * ─────────────────────────────────────────────────────────────────────
 * • All 5 pages start DISABLED.
 * • Modifying any filter field on a page ENABLES that page.
 * • "Disable" button toggles the current page; text changes to "Enable"
 * while disabled, reverts to "Disable" when re-enabled.
 * • "Disable all" disables all pages (text never changes).
 * • Number buttons [1]-[5]:
 * - Selected page → bold text + rectangular (non-rounded) border.
 * - Enabled pages  → black text + yellow-green background.
 * • Bottom-left/right images are clickable per page (toggle enabled/disabled).
 * - Clicking them does NOT enable or disable the page.
 * - Page 0  : bottomLeft  starts DISABLED.
 * - Page 1  : bottomRight starts DISABLED.
 * - Pages 3 & 4 : both    start DISABLED.
 * - All others  : both    start ENABLED.
 * • Bottom-right ENABLED  → page filters apply to AllExistingCards list (right pane).
 * • Bottom-left  ENABLED  → page filters apply to the middle-pane display.
 */
public class FilterPane extends VBox {

    // ═══════════════════════════════════════════════════════════════════
    // Per-page filter state (public so the controller can read it)
    // ═══════════════════════════════════════════════════════════════════

    // ─── layout constants ─────────────────────────────────
    private static final double COL1_LABEL_WIDTH = 90;

    // ═══════════════════════════════════════════════════════════════════
    // UI controls
    // ═══════════════════════════════════════════════════════════════════
    private static final double COL3_LABEL_WIDTH = 82;
    // ── Column 4 ──────────────────────────────────────────
    private final Button[] numberButtons = new Button[5];
    private final FilterPageState[] pageStates = new FilterPageState[5];
    // ── Column 1 ──────────────────────────────────────────
    private ComboBox<String> cardTypeCombo;
    private ComboBox<String> cardSubtypeCombo;
    private ComboBox<String> attributeCombo;
    private ComboBox<String> typeCombo;
    private TextField levelField;
    private TextField scaleField;
    private TextField atkField;
    private TextField defField;
    // ── Column 2 ──────────────────────────────────────────
    private TextField nameTextField;
    private TextField printcodeTextField;
    private TextField passCodeTextField;
    private TextField konamiIdTextField;
    private TextField effectTextField;
    private TextField genesysPointsField;
    private Button linkMarkersButton;
    // ── Column 3 ──────────────────────────────────────────
    private TextField yearField;
    private TextField wordCountField;
    private ComboBox<String> packCombo;
    private ComboBox<String> stateCombo;
    private ComboBox<String> rarityCombo;
    private Button disableButton;
    private Button disableAllButton;
    private Button activeButton;
    private Button allButton;
    private Button cameraButton;

    // ═══════════════════════════════════════════════════════════════════
    // Page state
    // ═══════════════════════════════════════════════════════════════════
    // ── Bottom corner images ───────────────────────────────
    private ImageView bottomLeftIV;
    private ImageView bottomRightIV;
    private int currentPage = 0;

    // ═══════════════════════════════════════════════════════════════════
    // External callbacks
    // ═══════════════════════════════════════════════════════════════════
    /**
     * Set to true while loading a page so listeners do not fire.
     */
    private boolean suppressListeners = false;
    /**
     * Fired whenever a change may affect the AllExistingCards right-side display
     * (i.e. a filter value changed, or a page/bottom-right state changed).
     */
    private Runnable onRightFilterChange;
    /**
     * Fired whenever a change may affect the middle-pane display
     * (i.e. a filter value changed, or a bottom-left state changed).
     */
    private Runnable onLeftFilterChange;
    public FilterPane() {
        this.setStyle("-fx-background-color: #100317;");
        this.getStyleClass().add("filter-pane");

        HBox columnsBox = new HBox(10);
        columnsBox.setPadding(new Insets(8, 8, 2, 8));
        columnsBox.setStyle("-fx-background-color: #100317;");

        VBox col1 = buildColumn1();
        VBox col2 = buildColumn2();
        VBox col3 = buildColumn3();
        VBox col4 = buildColumn4();

        HBox.setHgrow(col1, Priority.ALWAYS);
        HBox.setHgrow(col2, Priority.ALWAYS);
        HBox.setHgrow(col3, Priority.ALWAYS);

        columnsBox.getChildren().addAll(col1, col2, col3, col4);

        // ── Bottom images — default for page 0: left disabled, right enabled ──
        bottomLeftIV = loadCornerImage("LM-BottomLeft_Disabled.png", 42, 42);
        bottomRightIV = loadCornerImage("LM-BottomRight_Enabled.png", 42, 42);

        // The overlay HBox is purely decorative — fully mouse-transparent so it
        // never blocks the filter fields sitting underneath it in the StackPane.
        HBox bottomBar = new HBox();
        bottomBar.setStyle("-fx-background-color: transparent;");
        bottomBar.setPadding(new Insets(0));
        bottomBar.setAlignment(Pos.BOTTOM_LEFT);
        bottomBar.setMouseTransparent(true);   // overlay only — no event interception

        Region cornerSpacer = new Region();
        HBox.setHgrow(cornerSpacer, Priority.ALWAYS);
        bottomBar.getChildren().addAll(bottomLeftIV, cornerSpacer, bottomRightIV);

        // Two invisible hit-target Regions, one per corner, anchored inside the
        // StackPane at exactly the size of the images.  They are the ONLY nodes
        // that receive click events for the bottom arrows.
        Region leftHit = new Region();
        leftHit.setPrefSize(42, 42);
        leftHit.setMaxSize(42, 42);
        leftHit.setStyle("-fx-cursor: hand;");
        leftHit.setOnMouseClicked(e -> {
            pageStates[currentPage].bottomLeftEnabled = !pageStates[currentPage].bottomLeftEnabled;
            updateBottomImages();
            fireLeftFilterChange();
        });

        Region rightHit = new Region();
        rightHit.setPrefSize(42, 42);
        rightHit.setMaxSize(42, 42);
        rightHit.setStyle("-fx-cursor: hand;");
        rightHit.setOnMouseClicked(e -> {
            pageStates[currentPage].bottomRightEnabled = !pageStates[currentPage].bottomRightEnabled;
            updateBottomImages();
            fireRightFilterChange();
        });

        // Stack: columnsBox fills everything; bottomBar is visual only;
        // hit targets are anchored to their respective bottom corners.
        StackPane stack = new StackPane(columnsBox, bottomBar, leftHit, rightHit);
        StackPane.setAlignment(columnsBox, Pos.TOP_LEFT);
        StackPane.setAlignment(bottomBar, Pos.BOTTOM_CENTER);
        StackPane.setAlignment(leftHit, Pos.BOTTOM_LEFT);
        StackPane.setAlignment(rightHit, Pos.BOTTOM_RIGHT);
        VBox.setVgrow(stack, Priority.ALWAYS);

        this.getChildren().add(stack);

        // ── Initialise the 5 page states ──
        initPageStates();

        // ── Wire all interactive controls ──
        wireControls();

        // ── Reflect initial state visually (page 0 selected, all disabled) ──
        updateNumberButtonStyles();
        updateDisableButtonText();
        // bottomLeftIV / bottomRightIV already constructed with the page-0 defaults
    }

    // ═══════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════

    private static String text(TextField tf) {
        return (tf == null || tf.getText() == null) ? "" : tf.getText();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Column builders  (unchanged from original)
    // ═══════════════════════════════════════════════════════════════════

    private static String comboVal(ComboBox<String> cb) {
        if (cb == null) return "(All)";
        String v = cb.getValue();
        return v != null ? v : "(All)";
    }

    private static void setTF(TextField tf, String value) {
        if (tf != null) tf.setText(value != null ? value : "");
    }

    private static void setCB(ComboBox<String> cb, String value) {
        if (cb == null) return;
        if (value != null && cb.getItems().contains(value)) {
            cb.setValue(value);
        } else if (!cb.getItems().isEmpty()) {
            cb.setValue(cb.getItems().get(0));   // fall back to "(All)"
        }
    }

    /**
     * Col 1: Category / Attribute / Type / Lv+[LinkMkr]+Scale / ATK+DEF
     */
    private VBox buildColumn1() {
        VBox col = new VBox(6);
        col.setStyle("-fx-background-color: #100317;");

        cardTypeCombo = makeCombo();
        cardSubtypeCombo = makeCombo();
        HBox line1 = makeRow(makeFixedLabel("Category :", COL1_LABEL_WIDTH), cardTypeCombo, cardSubtypeCombo);

        attributeCombo = makeCombo();
        HBox line2 = makeRow(makeFixedLabel("Attribute :", COL1_LABEL_WIDTH), attributeCombo);

        typeCombo = makeCombo();
        HBox line3 = makeRow(makeFixedLabel("Type :", COL1_LABEL_WIDTH), typeCombo);

        levelField = makeNarrowField(null, 55);
        scaleField = makeNarrowField(null, 55);

        linkMarkersButton = new Button("Link markers");
        linkMarkersButton.getStyleClass().add("link-markers-button");
        linkMarkersButton.setPrefWidth(82);
        linkMarkersButton.setMinWidth(82);
        linkMarkersButton.setMaxWidth(82);

        Region scaleGap = new Region();
        scaleGap.setMinWidth(8);
        scaleGap.setPrefWidth(8);
        scaleGap.setMaxWidth(8);

        HBox line4 = makeRow(
                makeFixedLabel("Lv/Rank/Link :", COL1_LABEL_WIDTH),
                levelField,
                linkMarkersButton,
                scaleGap,
                makeLabel("Scale :"),
                scaleField
        );

        atkField = makeNarrowField(null, 55);
        defField = makeNarrowField(null, 55);
        Region defGap = new Region();
        defGap.setMinWidth(12);
        defGap.setPrefWidth(12);
        defGap.setMaxWidth(12);
        HBox line5 = makeRow(makeLabel("ATK :"), atkField, defGap, makeLabel("DEF :"), defField);

        col.getChildren().addAll(line1, line2, line3, line4, line5);
        return col;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Page state initialisation
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Col 2: Name / PrintCode / PassCode / Konami ID / Effect / Genesys Points
     */
    private VBox buildColumn2() {
        VBox col = new VBox(6);
        col.setStyle("-fx-background-color: #100317;");

        nameTextField = makeField(null);
        HBox line1 = makeRow(makeLabel("Name :"), nameTextField);
        HBox.setHgrow(nameTextField, Priority.ALWAYS);

        printcodeTextField = makeField(null);
        HBox line2 = makeRow(makeLabel("PrintCode :"), printcodeTextField);
        HBox.setHgrow(printcodeTextField, Priority.ALWAYS);

        passCodeTextField = makeField(null);
        HBox line3 = makeRow(makeLabel("PassCode :"), passCodeTextField);
        HBox.setHgrow(passCodeTextField, Priority.ALWAYS);

        konamiIdTextField = makeField(null);
        HBox line4 = makeRow(makeLabel("Konami ID :"), konamiIdTextField);
        HBox.setHgrow(konamiIdTextField, Priority.ALWAYS);

        effectTextField = makeField(null);
        HBox line5 = makeRow(makeLabel("Effect :"), effectTextField);
        HBox.setHgrow(effectTextField, Priority.ALWAYS);

        genesysPointsField = makeNarrowField(null, 65);
        HBox line6 = makeRow(makeLabel("Genesys Points :"), genesysPointsField);

        col.getChildren().addAll(line1, line2, line3, line4, line5, line6);
        return col;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Control wiring
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Col 3: Year / Word Count / Pack / State / Rarity (combos right-aligned)
     */
    private VBox buildColumn3() {
        VBox col = new VBox(6);
        col.setStyle("-fx-background-color: #100317;");

        yearField = makeNarrowField(null, 70);
        HBox line1 = makeRow(makeFixedLabel("Year :", COL3_LABEL_WIDTH), yearField);

        wordCountField = makeNarrowField(null, 115);
        HBox line2 = makeRow(makeFixedLabel("Word Count :", COL3_LABEL_WIDTH), wordCountField);

        packCombo = makeCombo();
        stateCombo = makeCombo();
        rarityCombo = makeCombo();

        HBox line3 = makeRightRow(makeLabel("Pack :"), packCombo);
        HBox line4 = makeRightRow(makeLabel("State :"), stateCombo);
        HBox line5 = makeRightRow(makeLabel("Rarity :"), rarityCombo);

        col.getChildren().addAll(line1, line2, line3, line4, line5);
        return col;
    }

    /**
     * Col 4: [1..5] / Disable / Disable all / Clear / Active+All / Camera
     */
    private VBox buildColumn4() {
        VBox col = new VBox(6);
        col.setStyle("-fx-background-color: #100317;");
        col.setAlignment(Pos.TOP_LEFT);
        col.setPadding(new Insets(0, 0, 0, 4));

        // Line 1 – number buttons [1][2][3][4][5]
        HBox numbersRow = new HBox(3);
        numbersRow.setAlignment(Pos.CENTER_RIGHT);
        for (int i = 0; i < 5; i++) {
            Button nb = new Button(String.valueOf(i + 1));
            nb.getStyleClass().add("number-button");
            nb.setPrefWidth(20);
            nb.setPrefHeight(20);
            nb.setMinWidth(20);
            nb.setMinHeight(20);
            nb.setMaxWidth(20);
            nb.setMaxHeight(20);
            numberButtons[i] = nb;
            numbersRow.getChildren().add(nb);
        }

        // Line 2 – Disable/Enable (text updated dynamically)
        disableButton = makeCol4Button("Enable");   // all pages start disabled

        // Line 3 – Disable all
        disableAllButton = makeCol4Button("Disable all");

        // Line 4 – "Clear" plain text
        Text clearText = styledText("Clear", false);
        HBox clearRow = new HBox(clearText);
        clearRow.setAlignment(Pos.CENTER_LEFT);

        // Line 5 – Active + All
        activeButton = makeCol4Button("Active");
        activeButton.setPrefWidth(62);
        activeButton.setMinWidth(62);
        activeButton.setMaxWidth(62);
        allButton = makeCol4Button("All");
        allButton.setPrefWidth(38);
        allButton.setMinWidth(38);
        allButton.setMaxWidth(38);
        HBox activeAllRow = new HBox(4, activeButton, allButton);
        activeAllRow.setAlignment(Pos.CENTER_LEFT);

        // Line 6 – Camera button
        cameraButton = new Button();
        cameraButton.getStyleClass().add("camera-button");
        ImageView camIcon = loadIcon("camera.png", 18, 18);
        if (camIcon != null) {
            cameraButton.setGraphic(camIcon);
        } else {
            cameraButton.setText("\uD83D\uDCF7");
        }
        cameraButton.setPrefWidth(35);
        cameraButton.setMinWidth(35);
        cameraButton.setMaxWidth(35);
        cameraButton.setPrefHeight(35);
        cameraButton.setMinHeight(35);
        cameraButton.setMaxHeight(35);
        HBox cameraRow = new HBox(cameraButton);
        cameraRow.setAlignment(Pos.CENTER_LEFT);
        cameraRow.setMinHeight(35);
        cameraRow.setPrefHeight(35);

        Region gap1 = new Region();
        gap1.setMinHeight(2);
        gap1.setPrefHeight(2);
        gap1.setMaxHeight(2);

        Region gap2 = new Region();
        gap2.setMinHeight(1);
        gap2.setPrefHeight(1);
        gap2.setMaxHeight(1);

        Region gap3 = new Region();
        gap3.setMinHeight(2);
        gap3.setPrefHeight(2);
        gap3.setMaxHeight(2);

        col.getChildren().addAll(
                numbersRow,
                gap1,
                disableButton,
                disableAllButton,
                gap2,
                clearRow,
                activeAllRow,
                gap3,
                cameraRow
        );
        return col;
    }

    private void initPageStates() {
        // Page 0: bottomLeft DISABLED, bottomRight enabled
        pageStates[0] = new FilterPageState(false, true);
        // Page 1: bottomLeft enabled,  bottomRight DISABLED
        pageStates[1] = new FilterPageState(true, false);
        // Page 2: both enabled
        pageStates[2] = new FilterPageState(true, true);
        // Page 3: both DISABLED
        pageStates[3] = new FilterPageState(false, false);
        // Page 4: both DISABLED
        pageStates[4] = new FilterPageState(false, false);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Page management
    // ═══════════════════════════════════════════════════════════════════

    private void wireControls() {

        // ── Number buttons 1-5 ──────────────────────────────────────────
        for (int i = 0; i < 5; i++) {
            final int pageIndex = i;
            numberButtons[i].setOnAction(e -> selectPage(pageIndex));
        }

        // ── Disable / Enable toggle ──────────────────────────────────────
        disableButton.setOnAction(e -> {
            pageStates[currentPage].enabled = !pageStates[currentPage].enabled;
            updateNumberButtonStyles();
            updateDisableButtonText();
            fireRightFilterChange();
        });

        // ── Disable All — text never changes ────────────────────────────
        disableAllButton.setOnAction(e -> {
            for (FilterPageState ps : pageStates) ps.enabled = false;
            updateNumberButtonStyles();
            updateDisableButtonText();
            fireRightFilterChange();
        });

        // ── Bottom-left / bottom-right click handling ────────────────────
        // Handled by the dedicated hit-target Regions in the StackPane (constructor).
        // No handlers needed on the ImageViews themselves.

        // ── All filter text fields ───────────────────────────────────────
        wireTextField(nameTextField);
        wireTextField(printcodeTextField);
        wireTextField(passCodeTextField);
        wireTextField(konamiIdTextField);
        wireTextField(effectTextField);
        wireTextField(genesysPointsField);
        wireTextField(levelField);
        wireTextField(scaleField);
        wireTextField(atkField);
        wireTextField(defField);
        wireTextField(yearField);
        wireTextField(wordCountField);

        // ── All filter combo boxes ───────────────────────────────────────
        wireComboBox(cardTypeCombo);
        wireComboBox(cardSubtypeCombo);
        wireComboBox(attributeCombo);
        wireComboBox(typeCombo);
        wireComboBox(packCombo);
        wireComboBox(stateCombo);
        wireComboBox(rarityCombo);
    }

    /**
     * Attaches a text-change listener that:
     * 1. Enables the current page.
     * 2. Saves the page's state snapshot.
     * 3. Fires the right-filter-change callback.
     */
    private void wireTextField(TextField tf) {
        if (tf == null) return;
        tf.textProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressListeners) return;
            enableCurrentPage();
            saveCurrentPageState();
            fireRightFilterChange();
        });
    }

    /**
     * Same as {@link #wireTextField} but for ComboBoxes.
     */
    private void wireComboBox(ComboBox<String> cb) {
        if (cb == null) return;
        cb.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressListeners) return;
            enableCurrentPage();
            saveCurrentPageState();
            fireRightFilterChange();
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // State save / load
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Switches the visible page to {@code page} (0-based).
     * Saves the current page's state first.
     */
    public void selectPage(int page) {
        if (page < 0 || page >= 5 || page == currentPage) return;
        saveCurrentPageState();
        currentPage = page;
        loadPageState(page);
        updateNumberButtonStyles();
        updateDisableButtonText();
        updateBottomImages();
    }

    /**
     * Returns the 0-based index of the currently selected page.
     */
    public int getCurrentPage() {
        return currentPage;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Visual update helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Enables the current page and refreshes related UI.
     * Called automatically when any filter field is modified.
     */
    private void enableCurrentPage() {
        if (!pageStates[currentPage].enabled) {
            pageStates[currentPage].enabled = true;
            updateNumberButtonStyles();
            updateDisableButtonText();
        }
    }

    /**
     * Copies the current UI field values into {@code pageStates[currentPage]}.
     * Always call this before switching pages or before reading page data externally.
     */
    public void saveCurrentPageState() {
        FilterPageState ps = pageStates[currentPage];
        ps.name = text(nameTextField);
        ps.printCode = text(printcodeTextField);
        ps.passCode = text(passCodeTextField);
        ps.konamiId = text(konamiIdTextField);
        ps.effect = text(effectTextField);
        ps.genesysPoints = text(genesysPointsField);
        ps.level = text(levelField);
        ps.scale = text(scaleField);
        ps.atk = text(atkField);
        ps.def = text(defField);
        ps.year = text(yearField);
        ps.wordCount = text(wordCountField);
        ps.cardType = comboVal(cardTypeCombo);
        ps.cardSubtype = comboVal(cardSubtypeCombo);
        ps.attribute = comboVal(attributeCombo);
        ps.type = comboVal(typeCombo);
        ps.pack = comboVal(packCombo);
        ps.state = comboVal(stateCombo);
        ps.rarity = comboVal(rarityCombo);
    }

    /**
     * Populates the UI fields from {@code pageStates[page]}.
     * Suppresses listeners during population to avoid spurious filter-change events.
     */
    private void loadPageState(int page) {
        FilterPageState ps = pageStates[page];
        suppressListeners = true;
        try {
            setTF(nameTextField, ps.name);
            setTF(printcodeTextField, ps.printCode);
            setTF(passCodeTextField, ps.passCode);
            setTF(konamiIdTextField, ps.konamiId);
            setTF(effectTextField, ps.effect);
            setTF(genesysPointsField, ps.genesysPoints);
            setTF(levelField, ps.level);
            setTF(scaleField, ps.scale);
            setTF(atkField, ps.atk);
            setTF(defField, ps.def);
            setTF(yearField, ps.year);
            setTF(wordCountField, ps.wordCount);
            setCB(cardTypeCombo, ps.cardType);
            setCB(cardSubtypeCombo, ps.cardSubtype);
            setCB(attributeCombo, ps.attribute);
            setCB(typeCombo, ps.type);
            setCB(packCombo, ps.pack);
            setCB(stateCombo, ps.state);
            setCB(rarityCombo, ps.rarity);
        } finally {
            suppressListeners = false;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Callback helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Refreshes the inline styles on each number button to reflect:
     * • selected page  → bold text, rectangular border (no radius)
     * • enabled page   → black text, yellow-green background
     */
    private void updateNumberButtonStyles() {
        for (int i = 0; i < 5; i++) {
            boolean sel = (i == currentPage);
            boolean enabled = pageStates[i].enabled;
            Button btn = numberButtons[i];

            StringBuilder sb = new StringBuilder();

            // Background and text colour
            if (enabled) {
                sb.append("-fx-background-color: #9dc000;");   // yellow-green
                sb.append("-fx-text-fill: black;");
            } else {
                sb.append("-fx-background-color: black;");
                sb.append("-fx-text-fill: #cdfc04;");
            }

            // Border shape: rectangle for selected, rounded for others
            if (sel) {
                sb.append("-fx-font-weight: bold;");
                sb.append("-fx-border-radius: 0;-fx-background-radius: 0;");
            } else {
                sb.append("-fx-font-weight: normal;");
                sb.append("-fx-border-radius: 3;-fx-background-radius: 3;");
            }

            btn.setStyle(sb.toString());
        }
    }

    /**
     * Updates the Disable/Enable button text to match the current page's state.
     */
    private void updateDisableButtonText() {
        if (disableButton != null) {
            disableButton.setText(pageStates[currentPage].enabled ? "Disable" : "Enable");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Factory helpers  (unchanged from original)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Swaps the bottom-left and bottom-right image files to match
     * the current page's {@code bottomLeftEnabled} / {@code bottomRightEnabled} flags.
     */
    private void updateBottomImages() {
        FilterPageState ps = pageStates[currentPage];

        if (bottomLeftIV != null) {
            String filename = ps.bottomLeftEnabled
                    ? "LM-BottomLeft_Enabled.png"
                    : "LM-BottomLeft_Disabled.png";
            Image img = loadImage(filename);
            if (img != null) bottomLeftIV.setImage(img);
        }

        if (bottomRightIV != null) {
            String filename = ps.bottomRightEnabled
                    ? "LM-BottomRight_Enabled.png"
                    : "LM-BottomRight_Disabled.png";
            Image img = loadImage(filename);
            if (img != null) bottomRightIV.setImage(img);
        }
    }

    private void fireRightFilterChange() {
        if (onRightFilterChange != null) onRightFilterChange.run();
    }

    private void fireLeftFilterChange() {
        if (onLeftFilterChange != null) onLeftFilterChange.run();
    }

    private Text makeLabel(String text) {
        return styledText(text, false);
    }

    private Text styledText(String text, boolean bold) {
        Text t = new Text(text);
        t.setStyle("-fx-fill: white; -fx-font-size: 13;"
                + (bold ? " -fx-font-weight: bold;" : ""));
        return t;
    }

    private HBox makeFixedLabel(String text, double width) {
        Text t = makeLabel(text);
        HBox box = new HBox(t);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(width);
        box.setPrefWidth(width);
        box.setMaxWidth(width);
        return box;
    }

    private TextField makeField(String prompt) {
        TextField tf = new TextField();
        if (prompt != null && !prompt.isEmpty()) tf.setPromptText(prompt);
        tf.getStyleClass().add("accent-text-field");
        tf.setPrefHeight(24);
        tf.setMaxHeight(24);
        return tf;
    }

    private TextField makeNarrowField(String prompt, double prefWidth) {
        TextField tf = makeField(prompt);
        tf.setPrefWidth(prefWidth);
        tf.setMaxWidth(prefWidth);
        return tf;
    }

    private ComboBox<String> makeCombo() {
        ComboBox<String> cb = new ComboBox<>();
        cb.getItems().add("(All)");
        cb.setValue("(All)");
        cb.getStyleClass().add("accent-combo");
        cb.setPrefHeight(24);
        cb.setMaxHeight(24);
        cb.setPrefWidth(150);
        cb.setMinWidth(150);
        return cb;
    }

    private Button makeSmallButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("small-button");
        return b;
    }

    private Button makeCol4Button(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("col4-button");
        return b;
    }

    private HBox makeRow(javafx.scene.Node... nodes) {
        HBox row = new HBox(5);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(nodes);
        return row;
    }

    private HBox makeRightRow(javafx.scene.Node... nodes) {
        HBox row = new HBox(5);
        row.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().add(spacer);
        row.getChildren().addAll(nodes);
        return row;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Small value-extraction helpers
    // ═══════════════════════════════════════════════════════════════════

    private ImageView loadCornerImage(String filename, double w, double h) {
        try {
            Image img = new Image("file:./src/main/resources/" + filename);
            ImageView iv = new ImageView(img);
            iv.setFitWidth(w);
            iv.setFitHeight(h);
            iv.setPreserveRatio(true);
            return iv;
        } catch (Exception e) {
            return new ImageView();
        }
    }

    private ImageView loadIcon(String filename, double w, double h) {
        try {
            Image img = new Image("file:./src/main/resources/" + filename);
            ImageView iv = new ImageView(img);
            iv.setFitWidth(w);
            iv.setFitHeight(h);
            iv.setPreserveRatio(true);
            return iv;
        } catch (Exception e) {
            return null;
        }
    }

    private Image loadImage(String filename) {
        try {
            return new Image("file:./src/main/resources/" + filename);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the state snapshot for page {@code index} (0-based).
     * <p>
     * <b>Important:</b> call {@link #saveCurrentPageState()} first if you need
     * the very latest values from the currently-displayed page.
     */
    public FilterPageState getPageState(int index) {
        if (index < 0 || index >= 5) return null;
        return pageStates[index];
    }

    // ═══════════════════════════════════════════════════════════════════
    // Public accessors
    // ═══════════════════════════════════════════════════════════════════

    // ── Page state ────────────────────────────────────────────────────

    public boolean isPageEnabled(int index) {
        FilterPageState ps = getPageState(index);
        return ps != null && ps.enabled;
    }

    public boolean isBottomRightEnabled(int index) {
        FilterPageState ps = getPageState(index);
        return ps != null && ps.bottomRightEnabled;
    }

    public boolean isBottomLeftEnabled(int index) {
        FilterPageState ps = getPageState(index);
        return ps != null && ps.bottomLeftEnabled;
    }

    /**
     * Sets the callback invoked when any change may affect the AllExistingCards display.
     * This includes: filter value edits, page enable/disable, bottom-right toggle.
     */
    public void setOnRightFilterChange(Runnable callback) {
        this.onRightFilterChange = callback;
    }

    // ── Callbacks ─────────────────────────────────────────────────────

    /**
     * Sets the callback invoked when any change may affect the middle-pane display.
     * This includes: filter value edits, bottom-left toggle.
     */
    public void setOnLeftFilterChange(Runnable callback) {
        this.onLeftFilterChange = callback;
    }

    // ── Column 1 ──────────────────────────────────────────────────────
    public ComboBox<String> getCardTypeCombo() {
        return cardTypeCombo;
    }

    public ComboBox<String> getCardSubtypeCombo() {
        return cardSubtypeCombo;
    }

    public ComboBox<String> getAttributeCombo() {
        return attributeCombo;
    }

    public ComboBox<String> getTypeCombo() {
        return typeCombo;
    }

    public TextField getLevelField() {
        return levelField;
    }

    public TextField getScaleField() {
        return scaleField;
    }

    public TextField getAtkField() {
        return atkField;
    }

    public TextField getDefField() {
        return defField;
    }

    // ── Column 2 ──────────────────────────────────────────────────────
    public TextField getNameTextField() {
        return nameTextField;
    }

    public TextField getPrintcodeTextField() {
        return printcodeTextField;
    }

    public TextField getPassCodeTextField() {
        return passCodeTextField;
    }

    public TextField getKonamiIdTextField() {
        return konamiIdTextField;
    }

    public TextField getEffectTextField() {
        return effectTextField;
    }

    public TextField getGenesysPointsField() {
        return genesysPointsField;
    }

    public Button getLinkMarkersButton() {
        return linkMarkersButton;
    }

    // ── Column 3 ──────────────────────────────────────────────────────
    public TextField getYearField() {
        return yearField;
    }

    public TextField getWordCountField() {
        return wordCountField;
    }

    public ComboBox<String> getPackCombo() {
        return packCombo;
    }

    public ComboBox<String> getStateCombo() {
        return stateCombo;
    }

    public ComboBox<String> getRarityCombo() {
        return rarityCombo;
    }

    /**
     * @param index 0-based (0 = button "1", …, 4 = button "5")
     */
    public Button getNumberButton(int index) {
        return numberButtons[index];
    }

    // ── Column 4 ──────────────────────────────────────────────────────

    public Button getDisableButton() {
        return disableButton;
    }

    public Button getDisableAllButton() {
        return disableAllButton;
    }

    public Button getActiveButton() {
        return activeButton;
    }

    public Button getAllButton() {
        return allButton;
    }

    public Button getCameraButton() {
        return cameraButton;
    }

    public static class FilterPageState {
        public boolean enabled = false;
        public boolean bottomLeftEnabled;
        public boolean bottomRightEnabled;

        // Column 2 text fields
        public String name = "";
        public String printCode = "";
        public String passCode = "";
        public String konamiId = "";
        public String effect = "";
        public String genesysPoints = "";

        // Column 1 fields
        public String cardType = "(All)";
        public String cardSubtype = "(All)";
        public String attribute = "(All)";
        public String type = "(All)";
        public String level = "";
        public String scale = "";
        public String atk = "";
        public String def = "";

        // Column 3 fields
        public String year = "";
        public String wordCount = "";
        public String pack = "(All)";
        public String state = "(All)";
        public String rarity = "(All)";

        FilterPageState(boolean bottomLeftEnabled, boolean bottomRightEnabled) {
            this.bottomLeftEnabled = bottomLeftEnabled;
            this.bottomRightEnabled = bottomRightEnabled;
        }
    }
}