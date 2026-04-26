package View;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

import java.util.List;

/**
 * FilterPane — the top-right "command" area of the AllExistingCards tab.
 *
 *  Col 1          │ Col 2            │ Col 3          │ Col 4
 *  ───────────────┼──────────────────┼────────────────┼─────────────────
 *  Category : [T] [ST]               │ Year           │  [1][2][3][4][5]
 *  Attribute : [combo]               │ Word Count     │  [Disable]
 *  Type      : [combo]               │ Pack      (right-aligned combo)
 *  Lv/Rank/Link:[f][LinkMkr] Scale:[f│ State     (right-aligned combo)  [Disable all]
 *  ATK:[f] DEF:[f]                   │ Rarity    (right-aligned combo)  Clear
 *                 │ Name             │                │  [Active] [All]
 *                 │ PrintCode        │                │  [📷]
 *                 │ PassCode         │
 *                 │ Konami ID        │
 *                 │ Effect           │
 *                 │ Genesys Points   │
 *  ──────────────────────────────────────────────────────────────────────
 *  [LM-BottomLeft]                                 [LM-BottomRight]
 *
 * ─────────────────────────────────────────────────────────────────────
 * FILTER PAGE SYSTEM (5 independent filter pages, selected via [1]-[5])
 * ─────────────────────────────────────────────────────────────────────
 *  • All 5 pages start DISABLED.
 *  • Modifying any filter field on a page ENABLES that page.
 *  • "Disable" button toggles the current page; text changes to "Enable"
 *    while disabled, reverts to "Disable" when re-enabled.
 *  • "Disable all" disables all pages (text never changes).
 *  • Number buttons [1]-[5]:
 *      - Selected page → bold text + rectangular (non-rounded) border.
 *      - Enabled pages  → black text + yellow-green background.
 *  • Bottom-left/right images are clickable per page (toggle enabled/disabled).
 *      - Clicking them does NOT enable or disable the page.
 *      - Page 0  : bottomLeft  starts DISABLED.
 *      - Page 1  : bottomRight starts DISABLED.
 *      - Pages 3 & 4 : both    start DISABLED.
 *      - All others  : both    start ENABLED.
 *  • Bottom-right ENABLED  → page filters apply to AllExistingCards list (right pane).
 *  • Bottom-left  ENABLED  → page filters apply to the middle-pane display.
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

    // ── Column 1 ──────────────────────────────────────────
    private ComboBox<String> cardTypeCombo;
    private ComboBox<String> cardSubtypeCombo;
    private ComboBox<String> attributeCombo;
    private ComboBox<String> typeCombo;
    private TextField levelField;
    private TextField scaleField;
    private TextField atkField;
    private static final double COL3_LABEL_WIDTH = 82;

    // ── Column 2 ──────────────────────────────────────────
    private TextField nameTextField;
    private TextField printcodeTextField;
    private TextField passCodeTextField;
    private TextField konamiIdTextField;
    private TextField effectTextField;
    private TextField genesysPointsField;
    /**
     * Default bottomLeft/bottomRight flags per page, used by init and reset.
     */
    private static final boolean[][] PAGE_ARROW_DEFAULTS = {
            {false, true},  // page 0: left disabled, right enabled
            {true, false},  // page 1: left enabled,  right disabled
            {true, true},  // page 2: both enabled
            {false, false},  // page 3: both disabled
            { false, false},  // page 4: both disabled
    };

    // ── Column 3 ──────────────────────────────────────────
    private TextField yearField;
    /**
     * Updates the Disable/Enable button text to match the current page's state.
     */
    // ═══════════════════════════════════════════════════════════════════
    // Category / Subtype combo helpers
    // ═══════════════════════════════════════════════════════════════════

    private static final String SEP = "---";
    private ComboBox<String> packCombo;
    private ComboBox<String> stateCombo;
    private ComboBox<String> rarityCombo;
    private static final String[] MONSTER_SUBTYPES = {
            "Normal", "Effect", "Pendulum", "Ritual",
            SEP,
            "Fusion", "Synchro", "Xyz", "Link",
            SEP,
            "Tuner", "Union", "Gemini", "Toon", "Flip", "Spirit"
    };
    private Button disableButton;
    private Button disableAllButton;
    private Button activeButton;
    private Button allButton;
    private Button cameraButton;
    private static final String[] SPELL_SUBTYPES = {
            "Normal", "Continuous", "Quick-Play", "Equip", "Field", "Ritual"
    };
    private static final String[] TRAP_SUBTYPES = {
            "Normal", "Continuous", "Counter"
    };
    // ── Column 4 ──────────────────────────────────────────
    private final Label[] numberSelectors = new Label[5];

    // ── Bottom corner images ───────────────────────────────
    private ImageView bottomLeftIV;
    private ImageView bottomRightIV;

    // ── Multi-select subtype state ─────────────────────────
    /**
     * The live selection set for the subtype multi-select combo.
     * An empty set means "(All)" — no subtype filter is applied.
     * Insertion-ordered so the button-cell summary stays stable.
     */
    private final java.util.Set<String> selectedSubtypes = new java.util.LinkedHashSet<>();
    private TextField        defField;
    private Button    linkMarkersButton;

    // ═══════════════════════════════════════════════════════════════════
    // Page state
    // ═══════════════════════════════════════════════════════════════════

    private final FilterPageState[] pageStates = new FilterPageState[5];
    private TextField        wordCountField;
    private LinkMarkerPopup linkMarkerPopup;

    // ═══════════════════════════════════════════════════════════════════
    // External callbacks
    // ═══════════════════════════════════════════════════════════════════

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
    private ContextMenu printcodeAutoComplete;
    private List<String>      printcodeSetNames;

    // ═══════════════════════════════════════════════════════════════════
    // Constructor
    // ═══════════════════════════════════════════════════════════════════
    /**
     * Reference to the button cell so we can refresh its text without a value change.
     */
    private javafx.scene.control.ListCell<String> subtypeButtonCell;

    // ═══════════════════════════════════════════════════════════════════
    // Column builders  (unchanged from original)
    // ═══════════════════════════════════════════════════════════════════
    /**
     * When true, {@code cardSubtypeCombo.hide()} is a no-op so the popup stays open
     * during a plain click (Monster category only). Reset inside the MOUSE_RELEASED
     * filter so external closes (click outside) still work normally.
     */
    private boolean subtypeKeepOpen = false;
    /**
     * True when the subtype combo is in multi-select mode (Monster category only).
     * False for Spell, Trap, or no category — standard single-select behaviour.
     */
    private boolean subtypeMultiSelectMode = false;
    private int currentPage = 0;
    /** Set to true while loading a page so listeners do not fire. */
    private boolean suppressListeners = false;

    /** Col 4: [1..5] / Disable / Disable all / Clear / Active+All / Camera */
    private VBox buildColumn4() {
        VBox col = new VBox(6);
        col.setStyle("-fx-background-color: #100317;");
        col.setAlignment(Pos.TOP_LEFT);
        col.setPadding(new Insets(0, 0, 0, 4));

        // Line 1 – number selectors [1][2][3][4][5]
        // Using Labels instead of Buttons: Labels have no ButtonSkin, so setStyle()
        // is never overridden by press/focus/hover CSS re-passes.
        HBox numbersRow = new HBox(3);
        numbersRow.setAlignment(Pos.CENTER_RIGHT);
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            Label nb = new Label(String.valueOf(i + 1));
            nb.setPrefWidth(20);
            nb.setPrefHeight(20);
            nb.setMinWidth(20);
            nb.setMinHeight(20);
            nb.setMaxWidth(20);
            nb.setMaxHeight(20);
            nb.setAlignment(Pos.CENTER);
            nb.setFocusTraversable(false);
            nb.setOnMouseClicked(e -> selectPage(idx));
            nb.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> applyNumberSelectorStyle(idx, true));
            nb.addEventHandler(MouseEvent.MOUSE_EXITED,  e -> applyNumberSelectorStyle(idx, false));
            nb.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> applyNumberSelectorStyle(idx, true));
            nb.addEventHandler(MouseEvent.MOUSE_RELEASED,e -> applyNumberSelectorStyle(idx, true));
            numberSelectors[i] = nb;
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

    // ═══════════════════════════════════════════════════════════════════
    // Page state initialisation
    // ═══════════════════════════════════════════════════════════════════

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
        bottomLeftIV = loadCornerImage("LM-BottomLeft_Disabled.png",  42, 42);
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
        Region leftHit  = new Region();
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
        StackPane.setAlignment(leftHit,    Pos.BOTTOM_LEFT);
        StackPane.setAlignment(rightHit,   Pos.BOTTOM_RIGHT);
        VBox.setVgrow(stack, Priority.ALWAYS);

        this.getChildren().add(stack);

        // ── Initialise the 5 page states ──
        initPageStates();

        // ── Wire all interactive controls ──
        wireControls();

        // ── Apply initial disabled state (no category selected = monster fields gray) ──
        updateMonsterFieldsDisabled("(All)", "(All)");

        // ── Reflect initial state visually (page 0 selected, all disabled) ──
        updateNumberButtonStyles();
        updateDisableButtonText();
        // bottomLeftIV / bottomRightIV already constructed with the page-0 defaults
    }

    private static String text(TextField tf) {
        return (tf == null || tf.getText() == null) ? "" : tf.getText();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Control wiring
    // ═══════════════════════════════════════════════════════════════════

    private static String comboVal(ComboBox<String> cb) {
        if (cb == null) return "(All)";
        String v = cb.getValue();
        return v != null ? v : "(All)";
    }

    private static void setTF(TextField tf, String value) {
        if (tf != null) tf.setText(value != null ? value : "");
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
            fireLeftFilterChange();
        });
    }

    // ═══════════════════════════════════════════════════════════════════
    // Page management
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

    // ═══════════════════════════════════════════════════════════════════
    // State save / load
    // ═══════════════════════════════════════════════════════════════════

    private static void setCB(ComboBox<String> cb, String value) {
        if (cb == null) return;
        if (value != null && cb.getItems().contains(value)) {
            cb.setValue(value);
        } else if (!cb.getItems().isEmpty()) {
            cb.setValue(cb.getItems().get(0));   // fall back to "(All)"
        }
    }

    /** Col 1: Category / Attribute / Type / Lv+[LinkMkr]+Scale / ATK+DEF */
    private VBox buildColumn1() {
        VBox col = new VBox(6);
        col.setStyle("-fx-background-color: #100317;");

        cardTypeCombo    = makeCombo();
        cardTypeCombo.getItems().addAll("Monster", "Spell", "Trap");

        // Anonymous subclass so hide() can be blocked during plain clicks.
        cardSubtypeCombo = new ComboBox<String>() {
            @Override public void hide() {
                if (!subtypeKeepOpen) super.hide();
            }
        };
        cardSubtypeCombo.getItems().add("(All)");
        cardSubtypeCombo.setValue("(All)");
        cardSubtypeCombo.getStyleClass().add("accent-combo");
        cardSubtypeCombo.setPrefHeight(24);
        cardSubtypeCombo.setMaxHeight(24);
        cardSubtypeCombo.setPrefWidth(150);
        cardSubtypeCombo.setMinWidth(150);
        installMultiSelectCellFactory(cardSubtypeCombo);
        updateSubtypeCombo("(All)");

        // When the category changes: rebuild subtype list, enable/disable fields.
        cardTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressListeners) return;
            updateSubtypeCombo(newVal);
            updateMonsterFieldsDisabled(newVal, null);
        });

        HBox line1 = makeRow(makeFixedLabel("Category :", COL1_LABEL_WIDTH), cardTypeCombo, cardSubtypeCombo);

        attributeCombo = makeCombo();
        attributeCombo.getItems().addAll(
                "Fire", "Water", "Wind", "Earth", "Light", "Dark", "Divine"
        );
        HBox line2 = makeRow(makeFixedLabel("Attribute :", COL1_LABEL_WIDTH), attributeCombo);

        typeCombo = makeCombo();
        typeCombo.getItems().addAll(
                "Aqua", "Beast", "Beast-Warrior", "Cyberse", "Dinosaur",
                "Divine-Beast", "Dragon", "Fairy", "Fiend", "Fish",
                "Illusion", "Insect", "Machine", "Plant", "Psychic",
                "Pyro", "Reptile", "Rock", "Sea Serpent", "Spellcaster",
                "Thunder", "Warrior", "Winged Beast", "Wyrm", "Zombie"
        );
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

        atkField = makeNarrowField(null, 90);
        defField = makeNarrowField(null, 90);
        Region defGap = new Region();
        defGap.setMinWidth(12);
        defGap.setPrefWidth(12);
        defGap.setMaxWidth(12);
        HBox line5 = makeRow(makeLabel("ATK :"), atkField, defGap, makeLabel("DEF :"), defField);

        col.getChildren().addAll(line1, line2, line3, line4, line5);
        return col;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Visual update helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Refreshes all number selector styles (non-hovered state).
     */
    private void updateNumberButtonStyles() {
        for (int i = 0; i < 5; i++) {
            applyNumberSelectorStyle(i, false);
        }
    }

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
        printcodeAutoComplete = new ContextMenu();
        printcodeAutoComplete.setStyle(
                "-fx-background-color: #100317;"
                        + "-fx-border-color: #cdfc04;-fx-border-width: 1;");
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

    /** Col 3: Year / Word Count / Pack / State / Rarity (combos right-aligned) */
    private VBox buildColumn3() {
        VBox col = new VBox(6);
        col.setStyle("-fx-background-color: #100317;");

        yearField = makeNarrowField(null, 70);
        HBox line1 = makeRow(makeFixedLabel("Year :", COL3_LABEL_WIDTH), yearField);

        wordCountField = makeNarrowField(null, 115);
        HBox line2 = makeRow(makeFixedLabel("Word Count :", COL3_LABEL_WIDTH), wordCountField);

        packCombo   = makeCombo();
        stateCombo = makeCombo();
        rarityCombo = makeCombo();

        HBox line3 = makeRightRow(makeLabel("Pack :"),   packCombo);
        HBox line4 = makeRightRow(makeLabel("State :"),  stateCombo);
        HBox line5 = makeRightRow(makeLabel("Rarity :"), rarityCombo);

        col.getChildren().addAll(line1, line2, line3, line4, line5);
        return col;
    }

    private void initPageStates() {
        for (int i = 0; i < 5; i++)
            pageStates[i] = new FilterPageState(
                    PAGE_ARROW_DEFAULTS[i][0], PAGE_ARROW_DEFAULTS[i][1]);
    }

    private void wireControls() {

        // ── Number selectors 1-5 — wired directly in buildColumn4() ────────

        // ── Link Markers popup ────────────────────────────────────────────
        linkMarkerPopup = new LinkMarkerPopup();
        linkMarkerPopup.setOnMarkersChanged(() -> {
            enableCurrentPage();
            saveCurrentPageState();
            fireRightFilterChange();
        });
        linkMarkersButton.setOnAction(e -> {
            if (linkMarkerPopup.isShowing()) {
                linkMarkerPopup.hide();
            } else {
                javafx.geometry.Bounds b = linkMarkersButton.localToScreen(
                        linkMarkersButton.getBoundsInLocal());
                if (b != null)
                    linkMarkerPopup.show(linkMarkersButton,
                            b.getMinX(), b.getMinY() - 110);
            }
        });

        // ── Active / All clear buttons ────────────────────────────────────
        activeButton.setOnAction(e -> {
            resetPageState(currentPage);
            loadPageState(currentPage);
            updateNumberButtonStyles();
            updateDisableButtonText();
            updateBottomImages();
            fireRightFilterChange();
            fireLeftFilterChange();
        });
        allButton.setOnAction(e -> {
            for (int i = 0; i < 5; i++) resetPageState(i);
            loadPageState(currentPage);
            updateNumberButtonStyles();
            updateDisableButtonText();
            updateBottomImages();
            fireRightFilterChange();
            fireLeftFilterChange();
        });

        // ── Disable / Enable toggle ──────────────────────────────────────
        disableButton.setOnAction(e -> {
            pageStates[currentPage].enabled = !pageStates[currentPage].enabled;
            updateNumberButtonStyles();
            updateDisableButtonText();
            fireRightFilterChange();
            fireLeftFilterChange();
        });

        // ── Disable All — text never changes ────────────────────────────
        disableAllButton.setOnAction(e -> {
            for (FilterPageState ps : pageStates) ps.enabled = false;
            updateNumberButtonStyles();
            updateDisableButtonText();
            fireRightFilterChange();
            fireLeftFilterChange();
        });

        // ── Bottom-left / bottom-right click handling ────────────────────
        // Handled by the dedicated hit-target Regions in the StackPane (constructor).
        // No handlers needed on the ImageViews themselves.

        // ── All filter text fields ───────────────────────────────────────
        wireTextField(nameTextField);
        wireTextField(printcodeTextField);
        printcodeTextField.textProperty().addListener((obs, o, n) -> {
            if (suppressListeners) {
                printcodeAutoComplete.hide();
                return;
            }
            updatePrintcodeAutoComplete(n);
        });
        printcodeTextField.focusedProperty().addListener((obs, o, is) -> {
            if (!is) printcodeAutoComplete.hide();
        });
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
        // cardSubtypeCombo is handled by installMultiSelectCellFactory
        wireComboBox(attributeCombo);
        wireComboBox(typeCombo);
        wireComboBox(packCombo);
        wireComboBox(stateCombo);
        wireComboBox(rarityCombo);
    }

    /**
     * Attaches a text-change listener that:
     *  1. Enables the current page.
     *  2. Saves the page's state snapshot.
     *  3. Fires the right-filter-change callback.
     */
    private void wireTextField(TextField tf) {
        if (tf == null) return;
        tf.textProperty().addListener((obs, oldVal, newVal) -> {
            if (suppressListeners) return;
            enableCurrentPage();
            saveCurrentPageState();
            fireRightFilterChange();
            fireLeftFilterChange();
        });
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
        ps.effect        = text(effectTextField);
        ps.genesysPoints = text(genesysPointsField);
        ps.level = text(levelField);
        ps.scale = text(scaleField);
        ps.atk = text(atkField);
        ps.def = text(defField);
        ps.year = text(yearField);
        ps.wordCount = text(wordCountField);
        ps.cardType = comboVal(cardTypeCombo);
        ps.cardSubtypes = new java.util.LinkedHashSet<>(selectedSubtypes);
        ps.attribute = comboVal(attributeCombo);
        ps.type = comboVal(typeCombo);
        ps.pack = comboVal(packCombo);
        ps.state = comboVal(stateCombo);
        ps.rarity = comboVal(rarityCombo);
        ps.linkMarkers   = new java.util.LinkedHashSet<>(linkMarkerPopup.getEnabledMarkers());
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
            loadSubtypeSelections(ps.cardSubtypes);
            setCB(attributeCombo, ps.attribute);
            setCB(typeCombo, ps.type);
            setCB(packCombo, ps.pack);
            setCB(stateCombo,            ps.state);
            setCB(rarityCombo,           ps.rarity);
            linkMarkerPopup.setEnabledMarkers(ps.linkMarkers);
        } finally {
            suppressListeners = false;
        }
    }

    /**
     * Installs the multi-select cell factory on {@code cardSubtypeCombo}.
     *
     * <ul>
     *   <li><b>Plain click</b> — toggles the item in/out of {@link #selectedSubtypes},
     *       fires filter callbacks, and keeps the popup open.</li>
     *   <li><b>Shift+click</b> — selects only that item (single-select semantics),
     *       fires filter callbacks, and lets the popup close normally.</li>
     *   <li><b>{@value SEP} rows</b> — always non-selectable visual dividers.</li>
     * </ul>
     * The button cell always shows a comma-joined summary of the current selection,
     * or "(All)" when nothing is selected.
     */
    private void installMultiSelectCellFactory(ComboBox<String> cb) {

        cb.setCellFactory(lv -> new javafx.scene.control.ListCell<String>() {
            {
                addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    String item = getItem();
                    if (item == null || SEP.equals(item)) {
                        event.consume();
                        return;
                    }
                    if (subtypeMultiSelectMode && !event.isShiftDown()) {
                        // ── Monster, plain click: toggle + keep open ─────────
                        subtypeKeepOpen = true;
                        toggleSubtype(item);
                        updateSubtypeButtonCell();
                        lv.refresh();
                        if (!suppressListeners) {
                            enableCurrentPage();
                            saveCurrentPageState();
                            fireRightFilterChange();
                            fireLeftFilterChange();
                        }
                        // Flag is reset in the MOUSE_RELEASED filter below.
                    } else {
                        // ── Single-select (Spell/Trap, or Shift+click) ───────
                        // Sync selectedSubtypes to the one chosen item, then let
                        // the event propagate so the ComboBox closes normally.
                        selectedSubtypes.clear();
                        if (!"(All)".equals(item)) selectedSubtypes.add(item);
                        updateSubtypeButtonCell();
                        lv.refresh();
                        if (!suppressListeners) {
                            enableCurrentPage();
                            saveCurrentPageState();
                            fireRightFilterChange();
                            fireLeftFilterChange();
                        }
                        // Do NOT consume → ComboBox closes normally.
                    }
                });

                // ── MOUSE_RELEASED: only intercept in multi-select mode ──────
                // ComboBoxListViewSkin.setOnMouseReleased() calls cb.hide().
                // Consuming here (filter phase) prevents that handler from firing.
                // In single-select mode we let it through so the popup closes.
                addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
                    if (subtypeMultiSelectMode && !event.isShiftDown()) {
                        event.consume();
                        subtypeKeepOpen = false; // reset now that RELEASED has fired
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                // Reset all per-cell state so recycled cells are fully clean.
                setText(null);
                setGraphic(null);
                setDisable(false);
                setMouseTransparent(false);
                setStyle("");

                if (empty || item == null) {
                    // nothing to render
                } else if (SEP.equals(item)) {
                    // ── Separator row ────────────────────────────────────────
                    javafx.scene.layout.Region line = new javafx.scene.layout.Region();
                    line.setPrefHeight(1);
                    line.setMinHeight(1);
                    line.setMaxHeight(1);
                    line.setMaxWidth(Double.MAX_VALUE);
                    line.setStyle("-fx-background-color: rgba(205,252,4,0.45);");
                    setGraphic(line);
                    setDisable(true);
                    setMouseTransparent(true);
                    setStyle("-fx-padding: 3 4 3 4; -fx-background-color: #100317;");
                } else if (subtypeMultiSelectMode) {
                    // ── Monster multi-select: show checkmark for selections ───
                    boolean isSelected = selectedSubtypes.isEmpty()
                            ? "(All)".equals(item)
                            : selectedSubtypes.contains(item);
                    setText(isSelected ? "✔  " + item : item);
                    setStyle(isSelected
                            ? "-fx-background-color: #393912; -fx-text-fill: #cdfc04;"
                            : "");
                } else {
                    // ── Spell/Trap single-select: plain text, no checkmarks ───
                    setText(item);
                }
            }
        });

        subtypeButtonCell = new javafx.scene.control.ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(computeSubtypeButtonText());
            }
        };
        cb.setButtonCell(subtypeButtonCell);
    }

    /**
     * Toggles {@code item} in {@link #selectedSubtypes}.
     * <ul>
     *   <li>Clicking "(All)" clears all other selections (empty set = no filter).</li>
     *   <li>Clicking a specific item removes "(All)" semantics and toggles that item.
     *       If the set becomes empty the result is equivalent to "(All)".</li>
     * </ul>
     */
    private void toggleSubtype(String item) {
        if ("(All)".equals(item)) {
            selectedSubtypes.clear();
        } else {
            if (selectedSubtypes.contains(item)) {
                selectedSubtypes.remove(item);
                // empty set now means "(All)" — that's intentional
            } else {
                selectedSubtypes.add(item);
            }
        }
    }

    /**
     * Applies the correct inline style to number selector {@code i}.
     * Because these are Labels (no ButtonSkin), setStyle() is authoritative —
     * no CSS pseudo-state re-pass can override it.
     *
     * @param hovered true when the mouse is currently over this selector
     */
    private void applyNumberSelectorStyle(int i, boolean hovered) {
        if (i < 0 || i >= 5 || numberSelectors[i] == null) return;
        boolean sel = (i == currentPage);
        boolean enabled = pageStates[i].enabled;
        Label   lbl     = numberSelectors[i];

        String bg, fg;
        if (enabled) {
            bg = hovered ? "#b5d600" : "#9dc000";   // yellow-green; slightly darker on hover
            fg = "black";
        } else {
            bg = hovered ? "rgba(205,252,4,0.15)" : "black";
            fg = "#cdfc04";                          // yellow-green text on black bg
        }

        String borderWidth = sel ? "2.5" : "1.5";
        String fontWeight  = sel ? "bold" : "normal";

        lbl.setStyle(
                "-fx-background-color: " + bg + ";" +
                        "-fx-text-fill: " + fg + ";" +
                        "-fx-font-size: 11px;" +
                        "-fx-font-weight: " + fontWeight + ";" +
                        "-fx-border-color: #cdfc04;" +
                        "-fx-border-width: "     + borderWidth + ";" +
                        "-fx-border-radius: 3;" +
                        "-fx-background-radius: 3;" +
                        "-fx-padding: 0;" +
                        "-fx-alignment: center;" +
                        "-fx-cursor: hand;"
        );
    }

    /**
     * Rebuilds {@code cardSubtypeCombo} items for the given category.
     * Always runs under {@code suppressListeners} so it never enables the page.
     */
    private void updateSubtypeCombo(String category) {
        suppressListeners = true;
        try {
            selectedSubtypes.clear();
            subtypeMultiSelectMode = "Monster".equals(category);
            cardSubtypeCombo.getItems().clear();
            cardSubtypeCombo.getItems().add("(All)");
            String[] extras =
                    "Monster".equals(category) ? MONSTER_SUBTYPES :
                            "Spell".equals(category) ? SPELL_SUBTYPES :
                                    "Trap"   .equals(category) ? TRAP_SUBTYPES    : new String[0];
            for (String s : extras) cardSubtypeCombo.getItems().add(s);
            cardSubtypeCombo.setValue("(All)");
            updateSubtypeButtonCell();
        } finally {
            suppressListeners = false;
        }
    }

    /**
     * Enables or disables monster-specific fields based on the selected category
     * and subcategory.
     *
     * Rules:
     *  - category != "Monster"  → Level/Rank/Link, ATK, DEF, Scale, Attribute,
     *                              Type, Link Markers all disabled.
     *  - category == "Monster"  → all enabled, except Scale which is only active
     *                              when subtype == "Pendulum".
     */
    private void updateMonsterFieldsDisabled(String category, String subtype) {
        boolean notMonster = !("Monster".equals(category) || "(All)".equals(category));
        if (attributeCombo != null) attributeCombo.setDisable(notMonster);
        if (typeCombo != null) typeCombo.setDisable(notMonster);
        if (levelField != null) levelField.setDisable(notMonster);
        if (atkField != null) atkField.setDisable(notMonster);
        if (defField          != null) defField.setDisable(notMonster);
        if (linkMarkersButton != null) linkMarkersButton.setDisable(notMonster);
    }

    /**
     * Forces the button cell to redisplay the current selection summary.
     * Must be called after every change to {@link #selectedSubtypes}.
     */
    private void updateSubtypeButtonCell() {
        if (subtypeButtonCell != null) {
            subtypeButtonCell.setText(computeSubtypeButtonText());
        }
    }

    // ─── PrintCode autocomplete ───────────────────────────────────────────

    /**
     * Builds the summary text shown in the combo button cell.
     *
     * @return "(All)" when nothing is selected, or a comma-joined list of selections
     */
    private String computeSubtypeButtonText() {
        if (selectedSubtypes.isEmpty()) return "(All)";
        String joined = selectedSubtypes.stream()
                .collect(java.util.stream.Collectors.joining(", "));
        return joined.isEmpty() ? "(All)" : joined;
    }

    private void updateDisableButtonText() {
        if (disableButton != null) {
            disableButton.setText(pageStates[currentPage].enabled ? "Disable" : "Enable");
        }
    }

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

    // ═══════════════════════════════════════════════════════════════════
    // Callback helpers
    // ═══════════════════════════════════════════════════════════════════

    private void fireRightFilterChange() {
        if (onRightFilterChange != null) onRightFilterChange.run();
    }

    private void fireLeftFilterChange() {
        if (onLeftFilterChange != null) onLeftFilterChange.run();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Factory helpers  (unchanged from original)
    // ═══════════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════════
    // Small value-extraction helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Loads a saved {@code Set<String>} back into {@link #selectedSubtypes} and
     * refreshes the button cell.  Called by {@link #loadPageState}.
     */
    private void loadSubtypeSelections(java.util.Set<String> saved) {
        selectedSubtypes.clear();
        if (saved != null) selectedSubtypes.addAll(saved);
        updateSubtypeButtonCell();
        // Refresh popup cells in case the combo is open (rare, but safe)
        if (cardSubtypeCombo != null) {
            javafx.application.Platform.runLater(() -> {
                if (cardSubtypeCombo.getSkin() != null) {
                    javafx.scene.control.skin.ComboBoxListViewSkin<?> skin =
                            (javafx.scene.control.skin.ComboBoxListViewSkin<?>)
                                    cardSubtypeCombo.getSkin();
                    javafx.scene.Node list = skin.getPopupContent();
                    if (list instanceof javafx.scene.control.ListView) {
                        ((javafx.scene.control.ListView<?>) list).refresh();
                    }
                }
            });
        }
    }

    /**
     * Resets page {@code index} to its default state (disabled, all fields
     * cleared, arrows restored to page-specific defaults).
     * Does NOT reload UI or fire callbacks — callers handle that.
     */
    private void resetPageState(int index) {
        pageStates[index] = new FilterPageState(
                PAGE_ARROW_DEFAULTS[index][0], PAGE_ARROW_DEFAULTS[index][1]);
    }

    private void updatePrintcodeAutoComplete(String typedText) {
        printcodeAutoComplete.hide();
        printcodeAutoComplete.getItems().clear();
        if (typedText == null || typedText.isEmpty()) return;
        int dashIdx = typedText.indexOf('-');
        String setPart = (dashIdx >= 0
                ? typedText.substring(0, dashIdx) : typedText).toUpperCase();
        if (setPart.isEmpty()) return;
        if (printcodeSetNames == null) {
            try {
                printcodeSetNames = Model.Database.PrintCodeToKonamiId.getSetNames();
            } catch (Exception e) {
                printcodeSetNames = java.util.Collections.emptyList();
            }
        }
        int added = 0;
        for (String name : printcodeSetNames) {
            if (name.toUpperCase().startsWith(setPart)) {
                MenuItem item = new MenuItem(name);
                item.setStyle("-fx-text-fill: #cdfc04;-fx-font-size: 12px;"
                        + "-fx-background-color: #100317;-fx-padding: 2 8 2 8;");
                item.setOnAction(e -> {
                    String suffix = dashIdx >= 0 ? typedText.substring(dashIdx) : "-";
                    String replacement = name + suffix;
                    suppressListeners = true;
                    try {
                        printcodeTextField.setText(replacement);
                        printcodeTextField.positionCaret(replacement.length());
                    } finally {
                        suppressListeners = false;
                    }
                    printcodeAutoComplete.hide();
                    enableCurrentPage();
                    saveCurrentPageState();
                    fireRightFilterChange();
                });
                printcodeAutoComplete.getItems().add(item);
                if (++added >= 12) break;
            }
        }
        if (!printcodeAutoComplete.getItems().isEmpty()
                && printcodeTextField.getScene() != null)
            printcodeAutoComplete.show(printcodeTextField,
                    javafx.geometry.Side.BOTTOM, 0, 0);
    }

    public TextField getAtkField() {
        return atkField; }

    // ═══════════════════════════════════════════════════════════════════
    // Public accessors
    // ═══════════════════════════════════════════════════════════════════

    // ── Page state ────────────────────────────────────────────────────

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

    // ── Callbacks ─────────────────────────────────────────────────────

    /**
     * Sets the callback invoked when any change may affect the AllExistingCards display.
     * This includes: filter value edits, page enable/disable, bottom-right toggle.
     */
    public void setOnRightFilterChange(Runnable callback) {
        this.onRightFilterChange = callback;
    }

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

    public TextField getDefField()                { return defField; }

    public Button getLinkMarkersButton()          { return linkMarkersButton; }

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

    public ComboBox<String> getRarityCombo() {
        return rarityCombo;
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

    /**
     * @param index 0-based (0 = button "1", …, 4 = button "5")
     */
    public Label getNumberSelector(int index) {
        return numberSelectors[index];
    }

    // ── Column 4 ──────────────────────────────────────────────────────

    public Button getCameraButton()               { return cameraButton; }

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
        /**
         * Selected subtypes for the multi-select combo. Empty set means "(All)".
         */
        public java.util.Set<String> cardSubtypes = new java.util.LinkedHashSet<>();
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

        /**
         * Enabled link markers. Empty = no filter.
         */
        public java.util.Set<String> linkMarkers = new java.util.LinkedHashSet<>();

        FilterPageState(boolean bottomLeftEnabled, boolean bottomRightEnabled) {
            this.bottomLeftEnabled = bottomLeftEnabled;
            this.bottomRightEnabled = bottomRightEnabled;
        }
    }
}