package Controller;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.CardsLists.SubListCreator;
import View.*;
import View.SharedCollectionTab.TabType;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RealMainController — thin coordinator for the Pot of Greed Manager application.
 *
 * <p>Responsibilities retained here:
 * <ul>
 *   <li>{@link #initialize()} wired via FXML {@code fx:controller="Controller.RealMainController"}.</li>
 *   <li>Shared state: card-size properties, the shared right panel (filter + card display),
 *       sort toggle buttons, view-mode flags ({@code isMosaicMode}, {@code isPrintedMode}).</li>
 *   <li>Tab-switch listener that delegates to the four sub-controllers.</li>
 *   <li>View toggle buttons (incomplete-mark, hide-archetypes, condition/rarity overlay).</li>
 *   <li>Coordinator-level helpers used by all sub-controllers:
 *       {@link #buildMiddlePaneEmptySpaceFilter()},
 *       {@link #doCardPasteOnNavItem}, {@link #updateTabDirtyIndicators()}, etc.</li>
 * </ul>
 *
 * <p>Tab-specific logic is fully delegated to:
 * <ul>
 *   <li>{@link MyCollectionController} — My Collection tab</li>
 *   <li>{@link DecksCollectionsController} — Decks and Collections tab</li>
 *   <li>{@link OuicheListController} — OuicheList tab</li>
 *   <li>{@link ArchetypesController} — Archetypes tab</li>
 * </ul>
 *
 * <p>{@link KeyboardShortcutHandler} owns global keyboard shortcuts (save, tab-switch,
 * copy/cut/paste/duplicate, delete) and the numpad/Enter "quick add from the right pane"
 * workflow.
 *
 * <p>{@link SaveStateCoordinator} owns the three save buttons, the tab-header dirty
 * indicators, and the unsaved-changes prompt shown on window close.
 *
 * <p>{@link NavigationHelper} provides shared static nav utilities used by all sub-controllers.
 *
 * <p>{@link CardFilterMatcher} provides the pure card/element filter-matching logic
 * used by {@link #updateCardsDisplay()}, {@link #updateMiddlePaneDisplay()}, and
 * {@link #getCompactOuicheListElementFilter()}.
 */
public class RealMainController {

    private static final Logger logger = LoggerFactory.getLogger(RealMainController.class);

    // ── Shared card-size properties ───────────────────────────────────────────

    private final DoubleProperty cardWidthProperty = new SimpleDoubleProperty(100);
    private final DoubleProperty cardHeightProperty = new SimpleDoubleProperty(146);

    // ── FXML fields (must stay here — wired by FXML loader) ──────────────────

    @FXML
    private TabPane mainTabPane;

    // ── Tab UI containers ─────────────────────────────────────────────────────

    private SharedCollectionTab myCollectionTab;
    private SharedCollectionTab decksTab;
    private SharedCollectionTab ouicheListTab;
    private SharedCollectionTab archetypesTab;
    private SharedCollectionTab friendsTab;
    private SharedCollectionTab shopsTab;

    // ── Tab handles (for dirty-indicator updates) ─────────────────────────────

    private static final String SORT_BTN_PADDING =
            "-fx-font-size: 11px; "
                    + "-fx-padding: 1 3 3 3; "
                    + "-fx-min-width: 36px; "
                    + "-fx-pref-width: 36px; "
                    + "-fx-max-width: 36px;";
    private final java.util.Set<FilterPane> wiredEnterPanes = new java.util.HashSet<>();
    private Tab myCollectionTabHandle;

    // ── Sub-controllers ───────────────────────────────────────────────────────
    private Tab decksTabHandle;
    private Tab ouicheListTabHandle;
    private MyCollectionController myCollectionController;
    private DecksCollectionsController decksController;

    // ── Shared right-panel state ──────────────────────────────────────────────
    private OuicheListController ouicheListController;
    private ArchetypesController archetypesController;
    private KeyboardShortcutHandler keyboardShortcutHandler;
    private SaveStateCoordinator saveStateCoordinator;
    private TabSwitchCoordinator tabSwitchCoordinator;

    // ── View-mode flags ───────────────────────────────────────────────────────

    private boolean isMosaicMode = true;
    private boolean isPrintedMode = false;

    // ── Sort toggle buttons ───────────────────────────────────────────────────
    /**
     * The single FilterPane instance that moves between tabs.
     */
    private FilterPane sharedFilterPane;
    /**
     * AnchorPane that holds the right-panel card list/mosaic view. Shared across tabs.
     */
    private AnchorPane cardsDisplayContainer;
    /**
     * 0 = AZ/ZA  |  1 = ATK  |  2 = DEF  |  3 = LVL.
     * ATK↓ is selected by default.
     */
    private int activeSortButtonIndex = 1;
    private Button sortAzButton;
    private Button sortAtkButton;
    private Button sortDefButton;
    private Button sortLvlButton;
    private Button listMosaicButton;

    // ── Live tree-view references (kept for selection-refresh listener) ────────
    private Button printedUniqueButton;
    private TreeView<String> decksAndCollectionsTreeView;
    private TreeView<String> myCollectionTreeView;
    private TreeView<String> ouicheListTreeView;
    private TreeView<String> archetypesTreeView;

    // ── Panes that have already had their ENTER shortcut wired ───────────────
    /**
     * Stores the current OuicheList view type for the OuicheList toggle buttons.
     * Values: "TREE" | "COMPACT_LIST" | "COMPACT_MOSAIC".
     */
    private String activeOuicheListView = "TREE";

    // =========================================================================
    // FXML lifecycle
    // =========================================================================

    /**
     * FXML entry point — called by the FX loader after field injection.
     *
     * <p>Must stay in this class because {@code main_layout.fxml} has
     * {@code fx:controller="Controller.RealMainController"}.
     */
    @FXML
    private void initialize() {
        UserInterfaceFunctions.readPathsFromFile();

        // ── 1. Warm up the archetype index ───────────────────────────────────
        try {
            Map<Integer, Card> allCards = Model.Database.Database.getAllCardsList();
            if (allCards != null && !allCards.isEmpty()) {
                SubListCreator.CreateArchetypeLists(allCards);
                SubListCreator.UpdateCardArchetypes();
                logger.info("SubListCreator archetypes loaded: names={}, lists={}",
                        SubListCreator.getArchetypesList() == null ? 0
                                : SubListCreator.getArchetypesList().size(),
                        SubListCreator.getArchetypesCardsLists() == null ? 0
                                : SubListCreator.getArchetypesCardsLists().size());
            } else {
                logger.info("Database.getAllCardsList() returned empty/null; "
                        + "archetypes not initialised now.");
            }
        } catch (Exception exception) {
            logger.warn("Failed to initialise SubListCreator archetypes at startup", exception);
        }

        // ── 2. Build tab containers ───────────────────────────────────────────
        myCollectionTab = new SharedCollectionTab(TabType.MY_COLLECTION);
        decksTab = new SharedCollectionTab(TabType.DECKS);
        ouicheListTab = new SharedCollectionTab(TabType.OUICHE_LIST);
        archetypesTab = new SharedCollectionTab(TabType.ARCHETYPES);
        friendsTab = new SharedCollectionTab(TabType.FRIENDS);
        shopsTab = new SharedCollectionTab(TabType.SHOPS);

        setupZoom(myCollectionTab);
        setupZoom(decksTab);
        setupZoom(ouicheListTab);
        setupZoom(archetypesTab);

        // ── 3. Instantiate sub-controllers ───────────────────────────────────
        myCollectionController = new MyCollectionController(
                this, cardWidthProperty, cardHeightProperty, myCollectionTab);
        decksController = new DecksCollectionsController(
                this, cardWidthProperty, cardHeightProperty, decksTab);
        ouicheListController = new OuicheListController(
                this, cardWidthProperty, cardHeightProperty, ouicheListTab, decksController);
        archetypesController = new ArchetypesController(
                this, cardWidthProperty, cardHeightProperty, archetypesTab);

        // ── 3b. Keyboard shortcuts / numpad-add handler ────────────────────────
        keyboardShortcutHandler = new KeyboardShortcutHandler(
                mainTabPane,
                () -> cardsDisplayContainer,
                () -> isMosaicMode,
                this::getActiveMiddleTreeView,
                this::updateTabDirtyIndicators);

        // ── 4. Wire CardDetailPane action buttons ─────────────────────────────
        for (SharedCollectionTab tab : java.util.List.of(myCollectionTab, decksTab, ouicheListTab)) {
            CardDetailPane cdp = tab.getCardDetailPane();
            cdp.setOnMinusOne(keyboardShortcutHandler::handleDeleteMiddleSelection);
            cdp.setOnPlusOne(keyboardShortcutHandler::handleDuplicateMiddleSelection);
            cdp.setOnCompleteToThree(keyboardShortcutHandler::handleCompleteToThree);
            cdp.setOnEdit(() -> {
                java.util.Set<CardElement> sel =
                        SelectionManager.getSelectedMiddleElements();
                if (sel.isEmpty()) {
                    return;
                }
                CardElement element = sel.iterator().next();
                CardEditPopup popup = new CardEditPopup(element);
                popup.setOnOk(() -> {
                    UserInterfaceFunctions.refreshOwnedCollectionView();
                    UserInterfaceFunctions.refreshDecksAndCollectionsView();
                });
                popup.showCenteredOn(cdp);
            });
        }

        // Tag the archetypes tree view so cells can identify it without relying
        // on which tab is currently selected.
        if (archetypesTreeView != null) {
            archetypesTreeView.getProperties().put("tabType", "ARCHETYPES");
        }

        // Lightweight D&C tree refresh: re-renders archetype-card glow states
        // inside Collections without rebuilding the model.
        UserInterfaceFunctions.registerDecksTreeRefresher(() -> {
            if (decksAndCollectionsTreeView != null) {
                decksAndCollectionsTreeView.refresh();
                Controller.CardGroupRegistry.refreshAllGridViews();
            }
        });

        // Refresh the archetype tab tree view whenever the model changes.
        UserInterfaceFunctions.registerArchetypesRefresher(() -> {
            if (archetypesTreeView != null) {
                archetypesTreeView.refresh();
                Controller.CardGroupRegistry.refreshAllGridViews();
            }
        });

        // ── 5. Place tabs and build shared right panel ────────────────────────
        if (mainTabPane != null && mainTabPane.getTabs().size() >= 6) {
            mainTabPane.getTabs().get(0).setContent(myCollectionTab);
            mainTabPane.getTabs().get(1).setContent(decksTab);
            mainTabPane.getTabs().get(2).setContent(ouicheListTab);
            mainTabPane.getTabs().get(3).setContent(archetypesTab);
            mainTabPane.getTabs().get(4).setContent(friendsTab);
            mainTabPane.getTabs().get(5).setContent(shopsTab);

            // cardsDisplayContainer holds the live cards list/mosaic view (shared across tabs).
            cardsDisplayContainer = new AnchorPane();
            cardsDisplayContainer.setStyle("-fx-background-color: #100317;");

            // ── List/Mosaic and Printed/Unique toggle buttons ─────────────────
            listMosaicButton = new Button("List");
            listMosaicButton.getStyleClass().add("small-button");
            listMosaicButton.setStyle("-fx-font-size: 12px; -fx-padding: 3 3 7 3;");
            listMosaicButton.setOnAction(event -> {
                isMosaicMode = !isMosaicMode;
                listMosaicButton.setText(isMosaicMode ? "List" : "Mosaic");
                updateCardsDisplay();
            });

            printedUniqueButton = new Button("Printed");
            printedUniqueButton.getStyleClass().add("small-button");
            printedUniqueButton.setStyle("-fx-font-size: 12px; -fx-padding: 3 3 7 3;");
            printedUniqueButton.setOnAction(event -> {
                isPrintedMode = !isPrintedMode;
                printedUniqueButton.setText(isPrintedMode ? "Unique" : "Printed");
                updateCardsDisplay();
            });

            // ── Sort toggle buttons ───────────────────────────────────────────
            sortAzButton = new Button("AZ");
            sortAtkButton = new Button("ATK↓");
            sortDefButton = new Button("DEF↓");
            sortLvlButton = new Button("LVL↓");
            sortAzButton.getStyleClass().add("sort-button");
            sortAtkButton.getStyleClass().add("sort-button");
            sortDefButton.getStyleClass().add("sort-button");
            sortLvlButton.getStyleClass().add("sort-button");

            applySortButtonSelectedStyle(sortAtkButton);
            applySortButtonUnselectedStyle(sortAzButton);
            applySortButtonUnselectedStyle(sortDefButton);
            applySortButtonUnselectedStyle(sortLvlButton);

            sortAzButton.setOnAction(event -> {
                if (activeSortButtonIndex == 0) {
                    sortAzButton.setText("AZ".equals(sortAzButton.getText()) ? "ZA" : "AZ");
                } else {
                    activeSortButtonIndex = 0;
                    applySortButtonSelectedStyle(sortAzButton);
                    applySortButtonUnselectedStyle(sortAtkButton);
                    applySortButtonUnselectedStyle(sortDefButton);
                    applySortButtonUnselectedStyle(sortLvlButton);
                }
                updateCardsDisplay();
            });

            sortAtkButton.setOnAction(event -> {
                if (activeSortButtonIndex == 1) {
                    sortAtkButton.setText("ATK↓".equals(sortAtkButton.getText()) ? "ATK↑" : "ATK↓");
                } else {
                    activeSortButtonIndex = 1;
                    applySortButtonSelectedStyle(sortAtkButton);
                    applySortButtonUnselectedStyle(sortAzButton);
                    applySortButtonUnselectedStyle(sortDefButton);
                    applySortButtonUnselectedStyle(sortLvlButton);
                }
                updateCardsDisplay();
            });

            sortDefButton.setOnAction(event -> {
                if (activeSortButtonIndex == 2) {
                    sortDefButton.setText("DEF↓".equals(sortDefButton.getText()) ? "DEF↑" : "DEF↓");
                } else {
                    activeSortButtonIndex = 2;
                    applySortButtonSelectedStyle(sortDefButton);
                    applySortButtonUnselectedStyle(sortAzButton);
                    applySortButtonUnselectedStyle(sortAtkButton);
                    applySortButtonUnselectedStyle(sortLvlButton);
                }
                updateCardsDisplay();
            });

            sortLvlButton.setOnAction(event -> {
                if (activeSortButtonIndex == 3) {
                    sortLvlButton.setText("LVL↓".equals(sortLvlButton.getText()) ? "LVL↑" : "LVL↓");
                } else {
                    activeSortButtonIndex = 3;
                    applySortButtonSelectedStyle(sortLvlButton);
                    applySortButtonUnselectedStyle(sortAzButton);
                    applySortButtonUnselectedStyle(sortAtkButton);
                    applySortButtonUnselectedStyle(sortDefButton);
                }
                updateCardsDisplay();
            });

            // Pre-wire all tabs so their FilterPanes have listeners, then
            // re-inject myCollectionTab last so the container lands in the right place.
            injectSharedRightPanel(myCollectionTab);
            injectSharedRightPanel(decksTab);
            injectSharedRightPanel(ouicheListTab);
            injectSharedRightPanel(archetypesTab);
            injectSharedRightPanel(friendsTab);
            injectSharedRightPanel(shopsTab);
            injectSharedRightPanel(myCollectionTab);

            myCollectionTabHandle = mainTabPane.getTabs().get(0);
            decksTabHandle = mainTabPane.getTabs().get(1);
            ouicheListTabHandle = mainTabPane.getTabs().get(2);

            UserInterfaceFunctions.registerTabDirtyIndicatorUpdater(
                    this::updateTabDirtyIndicators);
        }

        // ── 7. Wire save buttons ──────────────────────────────────────────────
        saveStateCoordinator = new SaveStateCoordinator(
                myCollectionTab, decksTab, ouicheListTab,
                myCollectionTabHandle, decksTabHandle, ouicheListTabHandle,
                myCollectionController, decksController);
        saveStateCoordinator.wireSaveButtons();
        wireViewToggleButtons();

        // ── 8. Display My Collection (the always-visible default tab) ─────────
        try {
            myCollectionController.displayMyCollection();
            myCollectionController.populateMyCollectionMenu();
        } catch (Exception exception) {
            logger.error("Error displaying My Collection", exception);
        }

        // ── 9. Wire tab-switch listener ───────────────────────────────────────
        tabSwitchCoordinator = new TabSwitchCoordinator(
                this, decksController, ouicheListController, archetypesController, decksTab);
        if (mainTabPane != null) {
            mainTabPane.getSelectionModel().selectedItemProperty()
                    .addListener((obs, oldTab, newTab) ->
                            tabSwitchCoordinator.handleTabSwitch(mainTabPane.getTabs().indexOf(newTab)));
        }

        // ── 11. Wire OuicheList toggle buttons ────────────────────────────────
        ouicheListController.setupOuicheListButtons();

        // ── 12. Decks "Load" button ───────────────────────────────────────────
        decksTab.setOnDecksLoad(() -> {
            try {
                decksController.displayDecksAndCollections();
            } catch (Exception exception) {
                logger.error("Error displaying Decks and Collections", exception);
            }
        });

        // ── 13. Initial right-panel display ──────────────────────────────────
        updateCardsDisplay();

        // ── 14. Selection-change listener (keeps all trees in sync) ──────────
        SelectionManager.addSelectionChangeListener(() -> Platform.runLater(() -> {
            if (myCollectionTreeView != null) {
                myCollectionTreeView.refresh();
            }
            if (decksAndCollectionsTreeView != null) {
                decksAndCollectionsTreeView.refresh();
            }
            if (ouicheListTreeView != null) {
                ouicheListTreeView.refresh();
            }
            if (archetypesTreeView != null) {
                archetypesTreeView.refresh();
            }
            CardGroupRegistry.refreshAllGridViews();
            if (cardsDisplayContainer != null) {
                for (Node node : cardsDisplayContainer.getChildren()) {
                    if (node instanceof ListView) {
                        ((ListView<?>) node).refresh();
                    }
                }
            }
        }));

        UserInterfaceFunctions.registerOwnedCollectionRefresher(this::refreshFromModel);

        // ── 15. Global keyboard shortcuts ─────────────────────────────────────
        keyboardShortcutHandler.setupGlobalKeyShortcuts();

        // ── 16. Window close interception ────────────────────────────────────
        Platform.runLater(() -> {
            if (mainTabPane != null
                    && mainTabPane.getScene() != null
                    && mainTabPane.getScene().getWindow() instanceof Stage) {
                ((Stage) mainTabPane.getScene().getWindow())
                        .setOnCloseRequest(saveStateCoordinator::handleWindowCloseRequest);
            }
        });
    }

    // =========================================================================
    // Shared right panel
    // =========================================================================

    /**
     * Moves the shared {@code cardsDisplayContainer} (with list/mosaic + printed/unique
     * toggle buttons) into the given tab's right-content pane, and ensures the shared
     * {@link FilterPane} is wired to that tab's right-header pane.
     *
     * <p>Called once for every tab during {@link #initialize()} to pre-wire all FilterPanes,
     * then called again for the active tab on every tab switch so the container is always
     * in the correct pane.
     *
     * @param tab the tab to inject into; no-op if {@code null}
     */
    void injectSharedRightPanel(SharedCollectionTab tab) {
        if (tab == null) {
            return;
        }

        if (sharedFilterPane == null) {
            sharedFilterPane = new FilterPane();
            sharedFilterPane.setOnRightFilterChange(this::updateCardsDisplay);
            sharedFilterPane.setOnLeftFilterChange(this::updateMiddlePaneDisplay);
            sharedFilterPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, evt -> {
                if (evt.getCode() == javafx.scene.input.KeyCode.ENTER) {
                    keyboardShortcutHandler.handleEnterAddFromRightPane();
                    evt.consume();
                }
            });
        }

        AnchorPane rightHeaderPane = tab.getRightHeaderPane();
        if (!rightHeaderPane.getChildren().contains(sharedFilterPane)) {
            rightHeaderPane.getChildren().clear();
            rightHeaderPane.getChildren().add(sharedFilterPane);
            AnchorPane.setTopAnchor(sharedFilterPane, 0.0);
            AnchorPane.setBottomAnchor(sharedFilterPane, 0.0);
            AnchorPane.setLeftAnchor(sharedFilterPane, 0.0);
            AnchorPane.setRightAnchor(sharedFilterPane, 0.0);
        }

        HBox viewModeGroup = new HBox(4, listMosaicButton, printedUniqueButton);
        viewModeGroup.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        HBox sortGroup = new HBox(4, sortAzButton, sortAtkButton, sortDefButton, sortLvlButton);
        sortGroup.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        HBox cardsTopBar = new HBox(10, viewModeGroup, sortGroup);
        cardsTopBar.setPadding(new Insets(5, 10, 5, 10));
        cardsTopBar.setStyle("-fx-background-color: #100317;");

        VBox.setVgrow(cardsDisplayContainer, Priority.ALWAYS);
        VBox rightContentVBox = new VBox(0, cardsTopBar, cardsDisplayContainer);
        rightContentVBox.setStyle("-fx-background-color: #100317;");

        AnchorPane rightContentPane = tab.getRightContentPane();
        rightContentPane.getChildren().clear();
        rightContentPane.getChildren().add(rightContentVBox);
        AnchorPane.setTopAnchor(rightContentVBox, 0.0);
        AnchorPane.setBottomAnchor(rightContentVBox, 0.0);
        AnchorPane.setLeftAnchor(rightContentVBox, 0.0);
        AnchorPane.setRightAnchor(rightContentVBox, 0.0);
    }

    // =========================================================================
    // Cards display (right panel)
    // =========================================================================

    /**
     * Rebuilds the right-panel cards list or mosaic view from the current filter
     * and sort state.
     */
    void updateCardsDisplay() {
        FilterPane filterPane = getActiveFilterPane();

        if (filterPane != null) {
            filterPane.saveCurrentPageState();
        }

        List<FilterPane.FilterPageState> activeStates = new ArrayList<>();
        if (filterPane != null) {
            for (int pageIndex = 0; pageIndex < 5; pageIndex++) {
                FilterPane.FilterPageState pageState = filterPane.getPageState(pageIndex);
                if (pageState != null && pageState.enabled && pageState.bottomRightEnabled) {
                    activeStates.add(pageState);
                }
            }
        }

        List<Card> allCards;
        try {
            allCards = isPrintedMode
                    ? new ArrayList<>(Model.Database.Database.getAllPrintedCardsList().values())
                    : new ArrayList<>(Model.Database.Database.getAllCardsList().values());
        } catch (URISyntaxException uriSyntaxException) {
            throw new RuntimeException(uriSyntaxException);
        }

        List<Card> filteredCards = allCards.stream()
                .filter(card -> {
                    for (FilterPane.FilterPageState pageState : activeStates) {
                        if (!CardFilterMatcher.matchesPageFilter(card, pageState, isPrintedMode)) {
                            return false;
                        }
                    }
                    return true;
                })
                .collect(Collectors.toList());

        filteredCards = Utils.CardSorter.sort(filteredCards, getCurrentSortMode());

        Node cardDisplayView;
        double mosaicImageWidth = 100;
        double mosaicImageHeight = 146;
        double listImageWidth = 80;
        double listImageHeight = 116;

        if (isMosaicMode) {
            double availableWidth =
                    cardsDisplayContainer == null ? 375 : cardsDisplayContainer.getWidth();
            if (availableWidth <= 0) {
                availableWidth = 375;
            }
            double cellGap = 5;
            List<List<Card>> rows = groupCardsIntoRows(
                    filteredCards, availableWidth, mosaicImageWidth, cellGap);
            ListView<List<Card>> mosaicListView =
                    new ListView<>(FXCollections.observableArrayList(rows));
            mosaicListView.setCellFactory(
                    param -> new CardsMosaicRowCell(mosaicImageWidth, mosaicImageHeight));
            mosaicListView.setStyle(
                    "-fx-background-color: #100317; -fx-control-inner-background: #100317;");
            mosaicListView.addEventHandler(
                    javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                    buildRightPaneEmptySpaceClearHandler());
            cardDisplayView = mosaicListView;
        } else {
            ListView<Card> listView =
                    new ListView<>(FXCollections.observableArrayList(filteredCards));
            listView.setCellFactory(
                    param -> new CardsListCell(isPrintedMode, listImageWidth, listImageHeight));
            listView.setStyle(
                    "-fx-background-color: #100317; -fx-control-inner-background: #100317;");
            listView.addEventHandler(
                    javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                    buildRightPaneEmptySpaceClearHandler());
            cardDisplayView = listView;
        }

        if (cardsDisplayContainer != null) {
            cardsDisplayContainer.getChildren().clear();
            cardsDisplayContainer.getChildren().add(cardDisplayView);
            AnchorPane.setTopAnchor(cardDisplayView, 0.0);
            AnchorPane.setBottomAnchor(cardDisplayView, 0.0);
            AnchorPane.setLeftAnchor(cardDisplayView, 0.0);
            AnchorPane.setRightAnchor(cardDisplayView, 0.0);
        }
    }

    /**
     * Re-computes the middle-pane card filter from all active FilterPane pages that
     * have their bottom-left arrow enabled, and pushes it into all live GridViews.
     *
     * <p>Also refreshes the OuicheList compact view (List/Mosaic), if currently
     * displayed, so the same left-pane filters apply there.
     */
    void updateMiddlePaneDisplay() {
        FilterPane filterPane = getActiveFilterPane();

        if (filterPane != null) {
            filterPane.saveCurrentPageState();
        }

        List<FilterPane.FilterPageState> activeStates = CardFilterMatcher.getActiveLeftFilterStates(filterPane);

        if (activeStates.isEmpty()) {
            CardGroupRegistry.setMiddleFilter(null);
            CardGroupRegistry.setMiddleElementFilter(null);
        } else {
            final List<FilterPane.FilterPageState> captured = activeStates;

            // Card-level predicate: all filters except Tags (Tags needs CardElement granularity).
            CardGroupRegistry.setMiddleFilter(card -> {
                for (FilterPane.FilterPageState pageState : captured) {
                    if (!CardFilterMatcher.matchesPageFilter(card, pageState, isPrintedMode)) {
                        return false;
                    }
                }
                return true;
            });

            // Element-level predicate: Tags filter, applied per-copy so that only
            // elements whose customTags actually match are shown, not all copies of
            // a card that happens to have at least one matching copy.
            boolean anyTagsFilter = captured.stream()
                    .anyMatch(ps -> ps.tags != null && !ps.tags.isBlank());
            if (anyTagsFilter) {
                CardGroupRegistry.setMiddleElementFilter(element -> {
                    if (element == null) {
                        return false;
                    }
                    for (FilterPane.FilterPageState pageState : captured) {
                        if (!CardFilterMatcher.matchesTagsFilter(element, pageState.tags)) {
                            return false;
                        }
                    }
                    return true;
                });
            } else {
                CardGroupRegistry.setMiddleElementFilter(null);
            }
        }

        refreshOuicheListCompactViewIfVisible();
    }

    /**
     * Builds a single {@link java.util.function.Predicate} on {@link CardElement} that
     * combines every active left-pane filter (card-level fields plus the per-copy Tags
     * filter), for use by views that operate on {@link CardElement} maps directly, such
     * as the OuicheList compact List/Mosaic.
     * <p>
     * Delegates to {@link CardFilterMatcher#buildElementFilter}.
     *
     * @return a predicate to apply to each {@link CardElement}, or {@code null} when
     * no left-pane filter is currently active (meaning every element passes)
     */
    public java.util.function.Predicate<CardElement> getCompactOuicheListElementFilter() {
        return CardFilterMatcher.buildElementFilter(getActiveFilterPane(), isPrintedMode);
    }

    /**
     * Refreshes the OuicheList compact view (List or Mosaic) so its content reflects
     * the current left-pane filters, but only when the OuicheList tab is currently
     * showing the compact view.
     */
    void refreshOuicheListCompactViewIfVisible() {
        Button compactDetailedButton = ouicheListTab.getCompactDetailedButton();
        Button mosaicListButton = ouicheListTab.getMosaicListButton();
        if (compactDetailedButton == null || mosaicListButton == null) {
            return;
        }
        boolean isCompactMode = "Detailed mode".equals(compactDetailedButton.getText());
        if (!isCompactMode) {
            return;
        }
        boolean isMosaicMode = "List".equals(mosaicListButton.getText());
        try {
            ouicheListController.displayCompactOuicheList(isMosaicMode);
        } catch (Exception exception) {
            logger.error("Error refreshing compact OuicheList after filter change", exception);
        }
    }

    // =========================================================================
    // Sort helpers
    // =========================================================================

    private void applySortButtonSelectedStyle(Button btn) {
        btn.setStyle("-fx-background-color: #cdfc04; -fx-text-fill: black; " + SORT_BTN_PADDING);
    }

    private void applySortButtonUnselectedStyle(Button btn) {
        btn.setStyle(SORT_BTN_PADDING);
    }

    private Utils.CardSorter.SortMode getCurrentSortMode() {
        switch (activeSortButtonIndex) {
            case 0:
                return "ZA".equals(sortAzButton.getText())
                        ? Utils.CardSorter.SortMode.ZA
                        : Utils.CardSorter.SortMode.AZ;
            case 1:
                return "ATK↑".equals(sortAtkButton.getText())
                        ? Utils.CardSorter.SortMode.ATK_ASC
                        : Utils.CardSorter.SortMode.ATK_DESC;
            case 2:
                return "DEF↑".equals(sortDefButton.getText())
                        ? Utils.CardSorter.SortMode.DEF_ASC
                        : Utils.CardSorter.SortMode.DEF_DESC;
            case 3:
                return "LVL↑".equals(sortLvlButton.getText())
                        ? Utils.CardSorter.SortMode.LVL_ASC
                        : Utils.CardSorter.SortMode.LVL_DESC;
            default:
                return Utils.CardSorter.SortMode.ATK_DESC;
        }
    }

    private List<List<Card>> groupCardsIntoRows(List<Card> cards, double availableWidth,
                                                double cellWidth, double gap) {
        int cardsPerRow = (int) Math.floor((availableWidth + gap) / (cellWidth + gap));
        if (cardsPerRow < 1) {
            cardsPerRow = 1;
        }
        List<List<Card>> rows = new ArrayList<>();
        for (int index = 0; index < cards.size(); index += cardsPerRow) {
            int end = Math.min(index + cardsPerRow, cards.size());
            rows.add(new ArrayList<>(cards.subList(index, end)));
        }
        return rows;
    }

    // =========================================================================
    // Zoom
    // =========================================================================

    private void setupZoom(SharedCollectionTab tab) {
        if (tab == null) {
            return;
        }
        tab.getContentPane().addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                adjustCardSize(event.getDeltaY());
                event.consume();
            }
        });
    }

    private void adjustCardSize(double scrollDelta) {
        double scalingFactor = scrollDelta > 0 ? 1.1 : 0.9;
        double newWidth = cardWidthProperty.get() * scalingFactor;
        if (newWidth < 50) {
            newWidth = 50;
        } else if (newWidth > 300) {
            newWidth = 300;
        }
        cardWidthProperty.set(newWidth);
        cardHeightProperty.set(newWidth * 146.0 / 100.0);
    }

    // =========================================================================
    // Filter helpers
    // =========================================================================

    private FilterPane getActiveFilterPane() {
        return sharedFilterPane;
    }

    // =========================================================================
    // Empty-space handlers (used by sub-controllers when building TreeViews)
    // =========================================================================

    /**
     * Returns an event filter for the middle-pane TreeView that clears the card
     * selection when the user clicks on an empty area below all tree nodes.
     * <p>
     * Delegates to {@link CardFilterMatcher#buildMiddlePaneEmptySpaceFilter}.
     *
     * @return an EventHandler suitable for {@code treeView.addEventFilter(...)}
     */
    public javafx.event.EventHandler<javafx.scene.input.MouseEvent> buildMiddlePaneEmptySpaceFilter() {
        return CardFilterMatcher.buildMiddlePaneEmptySpaceFilter();
    }

    /**
     * Returns an event handler for the right-panel ListView that clears the selection
     * when the user clicks on empty space (i.e. a non-consumed click).
     * <p>
     * Delegates to {@link CardFilterMatcher#buildRightPaneEmptySpaceClearHandler}.
     *
     * @return an EventHandler suitable for {@code listView.addEventHandler(...)}
     */
    public javafx.event.EventHandler<javafx.scene.input.MouseEvent> buildRightPaneEmptySpaceClearHandler() {
        return CardFilterMatcher.buildRightPaneEmptySpaceClearHandler();
    }

    // =========================================================================
    // Card paste routing
    // =========================================================================

    /**
     * Routes a card-paste (drag-and-drop from the right panel or the middle pane)
     * onto a model object exposed through a nav item.
     *
     * @param modelObj   the target model object (Box, CardsGroup, Deck, ThemeCollection…)
     * @param sourcePane identifier of the source pane ("RIGHT" / "MIDDLE" / "NAV" etc.)
     * @param event      the drop event
     */
    public void doCardPasteOnNavItem(Object modelObj, String sourcePane,
                                     javafx.scene.input.DragEvent event) {
        if (modelObj == null || sourcePane == null) {
            return;
        }
        if ("RIGHT".equals(sourcePane)) {
            List<Card> cards =
                    new ArrayList<>(DragDropManager.getDraggedCards());
            if (!cards.isEmpty()) {
                MiddleSelectionActionHandler.pasteCardsIntoNavigationItem(cards, modelObj);
            }
        } else if ("MIDDLE".equals(sourcePane)) {
            List<CardElement> elements =
                    new ArrayList<>(DragDropManager.getDraggedElements());
            if (elements.isEmpty()) {
                event.setDropCompleted(false);
                return;
            }
            List<Card> cards = new ArrayList<>();
            for (CardElement cardElement : elements) {
                if (cardElement.getCard() != null) {
                    cards.add(cardElement.getCard());
                }
            }
            MenuActionHandler.handleBulkRemoveElementsFromDecksAndCollections(elements);
            MenuActionHandler.handleBulkRemoveFromOwnedCollection(elements);
            if (!cards.isEmpty()) {
                final int count = cards.size();
                MiddleSelectionActionHandler.pasteCardsIntoNavigationItem(cards, modelObj);
                Platform.runLater(() -> {
                    List<CardElement> targetElements = MiddleSelectionActionHandler.getTargetGroupElements(modelObj);
                    int startIndex = Math.max(0, targetElements.size() - count);
                    SelectionManager.clearSelection();
                    for (int index = startIndex; index < targetElements.size(); index++) {
                        SelectionManager.toggleElementSelection(targetElements.get(index));
                    }
                    CardGroupRegistry.refreshAllGridViews();
                });
            }
        }
    }

    // =========================================================================
    // Tab dirty indicators
    // =========================================================================

    /**
     * Updates the text of the three saveable tab headers to show or remove the
     * {@code "*"} dirty marker based on the current dirty state in
     * {@link UserInterfaceFunctions}.
     */
    public void updateTabDirtyIndicators() {
        saveStateCoordinator.updateTabDirtyIndicators();
    }

    /**
     * Refreshes the Decks &amp; Collections tree view and all grid views in place,
     * without a full tree rebuild. Used by {@link TabSwitchCoordinator} after a
     * non-structural model change.
     */
    void refreshDecksAndCollectionsTreeView() {
        if (decksAndCollectionsTreeView != null) {
            decksAndCollectionsTreeView.refresh();
            CardGroupRegistry.refreshAllGridViews();
        }
    }

    /**
     * Returns the tree view belonging to whichever tab is currently selected.
     *
     * <p>Passed as a {@code Supplier} into {@link KeyboardShortcutHandler} and
     * {@link MiddleSelectionActionHandler} so those classes never need to hold
     * their own references to the four tab tree views.
     */
    private TreeView<String> getActiveMiddleTreeView() {
        if (mainTabPane == null) {
            return null;
        }
        int activeTabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        return switch (activeTabIndex) {
            case 0 -> myCollectionTreeView;
            case 1 -> decksAndCollectionsTreeView;
            case 2 -> ouicheListTreeView;
            case 3 -> archetypesTreeView;
            default -> null;
        };
    }

    // =========================================================================
    // View toggle buttons (incomplete-mark, hide-archetypes, condition/rarity)
    // =========================================================================

    private void wireViewToggleButtons() {
        if (myCollectionTab.getIncompleteMarkButton() != null) {
            Button incompleteMarkButton = myCollectionTab.getIncompleteMarkButton();
            final String incompleteMarkButtonOnStyle =
                    "-fx-background-color: #cdfc04;" +
                            "-fx-text-fill: black;" +
                            "-fx-border-color: #cdfc04;" +
                            "-fx-border-width: 1;" +
                            "-fx-border-radius: 4;" +
                            "-fx-background-radius: 4;" +
                            "-fx-font-size: 12px;" +
                            "-fx-padding: 4 10 4 10;" +
                            "-fx-cursor: hand;";
            final String incompleteMarkButtonOffStyle =
                    "-fx-background-color: #100317;" +
                            "-fx-text-fill: #cdfc04;" +
                            "-fx-border-color: #cdfc04;" +
                            "-fx-border-width: 1;" +
                            "-fx-border-radius: 4;" +
                            "-fx-background-radius: 4;" +
                            "-fx-font-size: 12px;" +
                            "-fx-padding: 4 10 4 10;" +
                            "-fx-cursor: hand;";
            incompleteMarkButton.setStyle(incompleteMarkButtonOnStyle);
            incompleteMarkButton.setOnAction(event -> {
                boolean nowEnabled = !CardTreeCell.isIncompleteMarkingEnabled();
                CardTreeCell.setIncompleteMarkingEnabled(nowEnabled);
                incompleteMarkButton.setStyle(
                        nowEnabled ? incompleteMarkButtonOnStyle : incompleteMarkButtonOffStyle);
                try {
                    myCollectionController.populateMyCollectionMenu();
                } catch (Exception exception) {
                    logger.error(
                            "Error refreshing My Collection menu after incomplete-mark toggle",
                            exception);
                }
                if (myCollectionTreeView != null) {
                    myCollectionTreeView.refresh();
                    CardGroupRegistry.refreshAllGridViews();
                }
            });
        }

        if (decksTab.getHideArchetypesButton() != null) {
            Button hideArchetypesButton = decksTab.getHideArchetypesButton();
            final String hideArchetypesOnStyle =
                    "-fx-background-color: #cdfc04;"
                            + "-fx-text-fill: black;"
                            + "-fx-border-color: #cdfc04;"
                            + "-fx-border-width: 1;"
                            + "-fx-border-radius: 4;"
                            + "-fx-background-radius: 4;"
                            + "-fx-font-size: 12px;"
                            + "-fx-padding: 4 10 4 10;"
                            + "-fx-cursor: hand;";
            final String hideArchetypesOffStyle =
                    "-fx-background-color: #100317;"
                            + "-fx-text-fill: #cdfc04;"
                            + "-fx-border-color: #cdfc04;"
                            + "-fx-border-width: 1;"
                            + "-fx-border-radius: 4;"
                            + "-fx-background-radius: 4;"
                            + "-fx-font-size: 12px;"
                            + "-fx-padding: 4 10 4 10;"
                            + "-fx-cursor: hand;";

            hideArchetypesButton.setOnAction(event -> {
                boolean nowHiding =
                        !DecksCollectionsController.isHideArchetypesEnabled();
                DecksCollectionsController.setHideArchetypesEnabled(nowHiding);

                if (nowHiding) {
                    hideArchetypesButton.setText("Show archetypes & exceptions");
                    hideArchetypesButton.setStyle(hideArchetypesOnStyle);
                } else {
                    hideArchetypesButton.setText("Hide archetypes & exceptions");
                    hideArchetypesButton.setStyle(hideArchetypesOffStyle);
                }

                // A full tree rebuild is required because the archetypes/exceptions
                // sections are DataTreeItem subtrees, not FilteredList-backed GridViews.
                try {
                    decksController.displayDecksAndCollections();
                } catch (Exception exception) {
                    logger.error("Error rebuilding Decks & Collections tree after "
                            + "archetypes toggle", exception);
                }
            });
        }

        // ── "Show condition / rarity" toggle (shared across all three tabs) ──
        // The same static flag in CardTreeCell drives all three mosaic views,
        // but each tab has its own button instance so they all need to be wired.
        final String conditionRarityOnStyle =
                "-fx-background-color: #cdfc04;"
                        + "-fx-text-fill: black;"
                        + "-fx-border-color: #cdfc04;"
                        + "-fx-border-width: 1;"
                        + "-fx-border-radius: 4;"
                        + "-fx-background-radius: 4;"
                        + "-fx-font-size: 12px;"
                        + "-fx-padding: 4 10 4 10;"
                        + "-fx-cursor: hand;";
        final String conditionRarityOffStyle =
                "-fx-background-color: #100317;"
                        + "-fx-text-fill: #cdfc04;"
                        + "-fx-border-color: #cdfc04;"
                        + "-fx-border-width: 1;"
                        + "-fx-border-radius: 4;"
                        + "-fx-background-radius: 4;"
                        + "-fx-font-size: 12px;"
                        + "-fx-padding: 4 10 4 10;"
                        + "-fx-cursor: hand;";

        javafx.event.EventHandler<javafx.event.ActionEvent> conditionRarityToggleHandler =
                event -> {
                    boolean nowEnabled = !CardTreeCell.isShowConditionRarityOverlayEnabled();
                    CardTreeCell.setShowConditionRarityOverlayEnabled(nowEnabled);
                    String newStyle = nowEnabled ? conditionRarityOnStyle : conditionRarityOffStyle;
                    if (myCollectionTab.getShowConditionRarityButton() != null) {
                        myCollectionTab.getShowConditionRarityButton().setStyle(newStyle);
                    }
                    if (decksTab.getShowConditionRarityButton() != null) {
                        decksTab.getShowConditionRarityButton().setStyle(newStyle);
                    }
                    if (ouicheListTab.getShowConditionRarityButton() != null) {
                        ouicheListTab.getShowConditionRarityButton().setStyle(newStyle);
                    }
                    if (myCollectionTreeView != null) {
                        myCollectionTreeView.refresh();
                    }
                    if (decksAndCollectionsTreeView != null) {
                        decksAndCollectionsTreeView.refresh();
                    }
                    if (ouicheListTreeView != null) {
                        ouicheListTreeView.refresh();
                    }
                    CardGroupRegistry.refreshAllGridViews();
                };

        if (myCollectionTab.getShowConditionRarityButton() != null) {
            myCollectionTab.getShowConditionRarityButton()
                    .setOnAction(conditionRarityToggleHandler);
        }
        if (decksTab.getShowConditionRarityButton() != null) {
            decksTab.getShowConditionRarityButton()
                    .setOnAction(conditionRarityToggleHandler);
        }
        if (ouicheListTab.getShowConditionRarityButton() != null) {
            ouicheListTab.getShowConditionRarityButton()
                    .setOnAction(conditionRarityToggleHandler);
        }
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Cleans up resources held by this controller and its sub-controllers.
     *
     * <p>Called by {@code RealMain} when the application window is closed.
     * Unregisters the {@link #refreshFromModel} refresher registered during
     * {@link #initialize()} and delegates disposal to each sub-controller.
     */
    public void dispose() {
        try {
            UserInterfaceFunctions.unregisterOwnedCollectionRefresher(this::refreshFromModel);
        } catch (Throwable ignored) {
        }
        if (myCollectionController != null) {
            myCollectionController.dispose();
        }
    }

    // =========================================================================
    // Generic refresh
    // =========================================================================

    /**
     * Generic reflection-based model refresh. Tries known method names first, then
     * falls back to refreshing all live TreeViews and ListViews found on this controller.
     */
    public void refreshFromModel() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refreshFromModel);
            return;
        }
        if (myCollectionTreeView != null) {
            myCollectionTreeView.refresh();
        }
        if (decksAndCollectionsTreeView != null) {
            decksAndCollectionsTreeView.refresh();
        }
        if (ouicheListTreeView != null) {
            ouicheListTreeView.refresh();
        }
        if (archetypesTreeView != null) {
            archetypesTreeView.refresh();
        }
        CardGroupRegistry.refreshAllGridViews();
    }

    // =========================================================================
    // Shared-state setters (called by sub-controllers after tree rebuilds)
    // =========================================================================

    /**
     * Called by {@link MyCollectionController} after it builds a new My Collection TreeView.
     *
     * @param treeView the newly built TreeView
     */
    public void setMyCollectionTreeView(TreeView<String> treeView) {
        this.myCollectionTreeView = treeView;
    }

    /**
     * Called by {@link DecksCollectionsController} after it builds a new Decks TreeView.
     *
     * @param treeView the newly built TreeView
     */
    public void setDecksAndCollectionsTreeView(TreeView<String> treeView) {
        this.decksAndCollectionsTreeView = treeView;
    }

    /**
     * Called by {@link OuicheListController} after it builds a new OuicheList TreeView.
     *
     * @param treeView the newly built TreeView
     */
    public void setOuicheListTreeView(TreeView<String> treeView) {
        this.ouicheListTreeView = treeView;
    }

    /**
     * Called by {@link ArchetypesController} after it builds a new Archetypes TreeView.
     *
     * @param treeView the newly built TreeView
     */
    public void setArchetypesTreeView(TreeView<String> treeView) {
        this.archetypesTreeView = treeView;
    }

    /**
     * Called by {@link OuicheListController} when the active view type changes.
     *
     * @param viewType "TREE" | "COMPACT_LIST" | "COMPACT_MOSAIC"
     */
    public void setActiveOuicheListView(String viewType) {
        this.activeOuicheListView = viewType;
    }

    // =========================================================================
    // Tab mapping
    // =========================================================================

    SharedCollectionTab getSharedTabAt(int index) {
        return switch (index) {
            case 0 -> myCollectionTab;
            case 1 -> decksTab;
            case 2 -> ouicheListTab;
            case 3 -> archetypesTab;
            case 4 -> friendsTab;
            case 5 -> shopsTab;
            default -> null;
        };
    }
}