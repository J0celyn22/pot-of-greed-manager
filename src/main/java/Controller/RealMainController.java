package Controller;

import Model.CardsLists.*;
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
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 *   <li>Global keyboard shortcuts and the window close handler.</li>
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
 * <p>{@link NavigationHelper} provides shared static nav utilities used by all sub-controllers.
 *
 * <p>The {@code @Deprecated} forwarding stubs for CardQualityService are kept here until
 * all external callers in untouched files have been updated.
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

    // ── One-time load guards ──────────────────────────────────────────────────
    private TreeView<String> archetypesTreeView;
    private boolean ouicheListLoaded = false;

    // ── Panes that have already had their ENTER shortcut wired ───────────────
    /**
     * Stores the current OuicheList view type for the OuicheList toggle buttons.
     * Values: "TREE" | "COMPACT_LIST" | "COMPACT_MOSAIC".
     */
    private String activeOuicheListView = "TREE";

    // =========================================================================
    // Deprecated forwarding stubs (kept for callers in untouched files)
    // =========================================================================

    /**
     * @deprecated Use {@link CardQualityService#computeCardNeedsSorting} instead.
     */
    @Deprecated
    public static boolean computeCardNeedsSorting(Card card, String elementName) {
        return CardQualityService.computeCardNeedsSorting(card, elementName);
    }

    /**
     * @deprecated Use {@link CardQualityService#computeCardNeedsSortingWithUpgrade} instead.
     */
    @Deprecated
    public static boolean computeCardNeedsSortingWithUpgrade(CardElement ownedElement,
                                                             String elementName) {
        return CardQualityService.computeCardNeedsSortingWithUpgrade(ownedElement, elementName);
    }

    /**
     * @deprecated Use {@link CardQualityService#computeNetCopiesNeeded} instead.
     */
    @Deprecated
    public static int computeNetCopiesNeeded(Card card, String elementName) {
        return CardQualityService.computeNetCopiesNeeded(card, elementName);
    }

    /**
     * @deprecated Use {@link CardQualityService#isBetterCondition} instead.
     */
    @Deprecated
    public static boolean isBetterCondition(CardCondition candidate, CardCondition existing) {
        return CardQualityService.isBetterCondition(candidate, existing);
    }

    /**
     * @deprecated Use {@link CardQualityService#satisfiesExpectedRarityBetter} instead.
     */
    @Deprecated
    public static boolean satisfiesExpectedRarityBetter(CardRarity candidateRarity,
                                                        CardRarity existingRarity,
                                                        CardRarity expectedRarity) {
        return CardQualityService.satisfiesExpectedRarityBetter(
                candidateRarity, existingRarity, expectedRarity);
    }

    /**
     * @deprecated Use {@link CardQualityService#isQualityUpgrade} instead.
     */
    @Deprecated
    public static boolean isQualityUpgrade(List<CardElement> existingInTarget,
                                           List<CardElement> targetSlotElements,
                                           CardElement candidate) {
        return CardQualityService.isQualityUpgrade(existingInTarget, targetSlotElements, candidate);
    }

    /**
     * @deprecated Use {@link CardQualityService#isDegradedCopyInDeckOrCollection} instead.
     */
    @Deprecated
    public static boolean isDegradedCopyInDeckOrCollection(CardElement deckElement,
                                                           String deckOrCollectionName) {
        return CardQualityService.isDegradedCopyInDeckOrCollection(deckElement,
                deckOrCollectionName);
    }

    /**
     * @deprecated Use {@link CardQualityService#findOwnedUpgradeCandidates} instead.
     */
    @Deprecated
    public static List<CardElement> findOwnedUpgradeCandidates(CardElement deckElement,
                                                               String deckOrCollectionName) {
        return CardQualityService.findOwnedUpgradeCandidates(deckElement, deckOrCollectionName);
    }

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
                        SubListCreator.archetypesList == null ? 0
                                : SubListCreator.archetypesList.size(),
                        SubListCreator.archetypesCardsLists == null ? 0
                                : SubListCreator.archetypesCardsLists.size());
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
                this, cardWidthProperty, cardHeightProperty, archetypesTab, decksController);

        // ── 4. Wire CardDetailPane action buttons ─────────────────────────────
        for (SharedCollectionTab tab : java.util.List.of(myCollectionTab, decksTab, ouicheListTab)) {
            CardDetailPane cdp = tab.getCardDetailPane();
            cdp.setOnMinusOne(this::handleDeleteMiddleSelection);
            cdp.setOnPlusOne(this::handleDuplicateMiddleSelection);
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
                View.CardTreeCell.refreshAllGridViews();
            }
        });

        // Refresh the archetype tab tree view whenever the model changes.
        UserInterfaceFunctions.registerArchetypesRefresher(() -> {
            if (archetypesTreeView != null) {
                archetypesTreeView.refresh();
                View.CardTreeCell.refreshAllGridViews();
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
        wireSaveButtons();

        // ── 8. Display My Collection (the always-visible default tab) ─────────
        try {
            myCollectionController.displayMyCollection();
            myCollectionController.populateMyCollectionMenu();
        } catch (Exception exception) {
            logger.error("Error displaying My Collection", exception);
        }

        // ── 9. Wire tab-switch listener ───────────────────────────────────────
        if (mainTabPane != null) {
            mainTabPane.getSelectionModel().selectedItemProperty()
                    .addListener((obs, oldTab, newTab) -> handleTabSwitch(newTab));
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
            CardTreeCell.refreshAllGridViews();
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
        setupGlobalKeyShortcuts();

        // ── 16. Window close interception ────────────────────────────────────
        Platform.runLater(() -> {
            if (mainTabPane != null
                    && mainTabPane.getScene() != null
                    && mainTabPane.getScene().getWindow() instanceof Stage) {
                ((Stage) mainTabPane.getScene().getWindow())
                        .setOnCloseRequest(this::handleWindowCloseRequest);
            }
        });
    }

    // =========================================================================
    // Tab-switch handler
    // =========================================================================

    /**
     * Called by the tab-change listener whenever the user selects a different tab.
     * Injects the shared right panel, re-applies the middle-pane filter, and
     * delegates to the appropriate sub-controller.
     *
     * @param newTab the newly selected tab (may be null)
     */
    private void handleTabSwitch(Tab newTab) {
        if (newTab == null) {
            return;
        }
        int selectedIndex = mainTabPane.getTabs().indexOf(newTab);

        injectSharedRightPanel(getSharedTabAt(selectedIndex));
        updateMiddlePaneDisplay();

        switch (selectedIndex) {
            case 1 -> handleDecksTabSelected();
            case 2 -> handleOuicheListTabSelected();
            case 3 -> handleArchetypesTabSelected();
        }
    }

    private void handleDecksTabSelected() {
        try {
            decksController.populateDecksAndCollectionsMenu();
            UserInterfaceFunctions.registerDecksCollectionsRefresher(() -> {
                try {
                    String cardTarget = MenuActionHandler.getAndClearLastDecksAddedTarget();
                    Object deckMoveTarget =
                            UserInterfaceFunctions.getAndClearPendingDecksScrollTarget();
                    Object[] createCollData =
                            UserInterfaceFunctions.getAndClearPendingDecksCreateCollectionData();
                    Object renameTarget =
                            UserInterfaceFunctions.getAndClearPendingDecksRenameTarget();
                    boolean needsFullRebuild =
                            UserInterfaceFunctions.getAndClearPendingDecksFullRebuild();
                    Object expandTarget =
                            UserInterfaceFunctions.getAndClearPendingDecksExpandTarget();

                    decksController.populateDecksAndCollectionsMenu();

                    boolean isStructuralChange = deckMoveTarget != null
                            || createCollData != null
                            || renameTarget != null
                            || needsFullRebuild;

                    if (isStructuralChange || cardTarget != null) {
                        final double savedScroll = decksController.getDecksTreeScrollPosition();
                        decksController.displayDecksAndCollections();
                        Platform.runLater(() ->
                                decksController.restoreDecksTreeScrollPosition(savedScroll));
                    } else {
                        if (decksAndCollectionsTreeView != null) {
                            decksAndCollectionsTreeView.refresh();
                            CardTreeCell.refreshAllGridViews();
                        }
                    }

                    if (cardTarget != null) {
                        decksController.scrollToTargetInDecksTree(cardTarget);
                    }
                    if (deckMoveTarget != null) {
                        decksController.scrollToMovedDeck(deckMoveTarget);
                    }

                    if (createCollData != null && createCollData.length == 2
                            && createCollData[0] instanceof ThemeCollection newCollection
                            && createCollData[1] instanceof Deck movedDeck) {
                        Platform.runLater(() -> {
                            NavigationItem toRename = NavigationHelper.findNavItemInMenuVBox(
                                    decksTab.getMenuVBox(), newCollection);
                            if (toRename != null) {
                                toRename.setExpanded(true);
                                NavigationHelper.expandNavAncestors(toRename);
                                NavigationHelper.scrollNavToItem(decksTab, toRename);
                                decksController.startDecksCreateCollectionRename(
                                        toRename, newCollection, movedDeck);
                            } else {
                                logger.warn("Create-Collection rename: NavigationItem not found"
                                        + " for '{}'", newCollection.getName());
                            }
                        });
                    }

                    if (renameTarget != null) {
                        final Object finalTarget = renameTarget;
                        Platform.runLater(() -> {
                            NavigationItem toRename = NavigationHelper.findNavItemInMenuVBox(
                                    decksTab.getMenuVBox(), finalTarget);
                            if (toRename != null) {
                                NavigationHelper.expandNavAncestors(toRename);
                                NavigationHelper.scrollNavToItem(decksTab, toRename);
                                decksController.startDecksAddRename(toRename, finalTarget);
                            } else {
                                logger.warn(
                                        "Pending decks rename: NavigationItem not found for {}",
                                        finalTarget);
                            }
                        });
                    }

                    if (expandTarget != null) {
                        final Object finalExpand = expandTarget;
                        Platform.runLater(() -> {
                            NavigationItem toExpand = NavigationHelper.findNavItemInMenuVBox(
                                    decksTab.getMenuVBox(), finalExpand);
                            if (toExpand != null) {
                                toExpand.setExpanded(true);
                                NavigationHelper.expandNavAncestors(toExpand);
                                NavigationHelper.scrollNavToItem(decksTab, toExpand);
                            }
                        });
                    }

                    updateTabDirtyIndicators();
                } catch (Exception exception) {
                    logger.debug("Decks refresher failed", exception);
                }
            });
            decksController.displayDecksAndCollections();
        } catch (Exception exception) {
            logger.error("Error displaying Decks and Collections", exception);
        }
    }

    private void handleOuicheListTabSelected() {
        if (!ouicheListLoaded) {
            try {
                UserInterfaceFunctions.generateOuicheList();
                ouicheListController.displayOuicheListUnified();
                ouicheListController.populateOuicheListMenu();
                ouicheListLoaded = true;
            } catch (Exception exception) {
                logger.error("Error displaying OuicheList", exception);
            }
        }
    }

    private void handleArchetypesTabSelected() {
        try {
            archetypesController.displayArchetypes();
            archetypesController.populateArchetypesMenu();
        } catch (Exception exception) {
            logger.error("Error displaying Archetypes", exception);
        }
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
    private void injectSharedRightPanel(SharedCollectionTab tab) {
        if (tab == null) {
            return;
        }

        if (sharedFilterPane == null) {
            sharedFilterPane = new FilterPane();
            sharedFilterPane.setOnRightFilterChange(this::updateCardsDisplay);
            sharedFilterPane.setOnLeftFilterChange(this::updateMiddlePaneDisplay);
            sharedFilterPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, evt -> {
                if (evt.getCode() == javafx.scene.input.KeyCode.ENTER) {
                    handleEnterAddFromRightPane();
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
                        if (!matchesPageFilter(card, pageState)) {
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
     */
    void updateMiddlePaneDisplay() {
        FilterPane filterPane = getActiveFilterPane();

        if (filterPane != null) {
            filterPane.saveCurrentPageState();
        }

        List<FilterPane.FilterPageState> activeStates = new ArrayList<>();
        if (filterPane != null) {
            for (int pageIndex = 0; pageIndex < 5; pageIndex++) {
                FilterPane.FilterPageState pageState = filterPane.getPageState(pageIndex);
                if (pageState != null && pageState.enabled && pageState.bottomLeftEnabled) {
                    activeStates.add(pageState);
                }
            }
        }

        if (activeStates.isEmpty()) {
            CardTreeCell.setMiddleFilter(null);
        } else {
            final List<FilterPane.FilterPageState> captured = activeStates;
            CardTreeCell.setMiddleFilter(card -> {
                for (FilterPane.FilterPageState pageState : captured) {
                    if (!matchesPageFilter(card, pageState)) {
                        return false;
                    }
                }
                return true;
            });
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

    /**
     * Returns {@code true} when {@code card} matches all conditions specified in
     * {@code pageState}. This method is the single source of truth for card-filter
     * logic; it is used by both the right-panel and the middle-panel filters.
     *
     * @param card      the card to test
     * @param pageState the filter-page state to test against
     * @return {@code true} if the card passes all conditions in the page
     */
    private boolean matchesPageFilter(Card card, FilterPane.FilterPageState pageState) {
        if (card == null || pageState == null) {
            return true;
        }

        // ── Name filter ───────────────────────────────────────────────────────
        String nameFilter = pageState.name.toLowerCase().trim();
        if (!nameFilter.isEmpty()) {
            boolean matchesName =
                    (card.getName_EN() != null
                            && card.getName_EN().toLowerCase().contains(nameFilter))
                            || (card.getName_FR() != null
                            && card.getName_FR().toLowerCase().contains(nameFilter))
                            || (card.getName_JA() != null
                            && card.getName_JA().toLowerCase().contains(nameFilter));
            if (!matchesName) {
                return false;
            }
        }

        // ── PrintCode / PassCode filter ───────────────────────────────────────
        String codeFilter = pageState.printCode.toLowerCase().trim();
        if (!codeFilter.isEmpty()) {
            boolean matchesCode = isPrintedMode
                    ? (card.getPrintCode() != null
                    && card.getPrintCode().toLowerCase().contains(codeFilter))
                    : (card.getPassCode() != null
                    && card.getPassCode().toLowerCase().contains(codeFilter));
            if (!matchesCode) {
                return false;
            }
        }

        // ── PassCode filter ───────────────────────────────────────────────────
        String passCodeFilter = pageState.passCode.toLowerCase().trim();
        if (!passCodeFilter.isEmpty()) {
            boolean matches = card.getPassCode() != null
                    && card.getPassCode().toLowerCase().contains(passCodeFilter);
            if (!matches) {
                return false;
            }
        }

        // ── Konami ID filter ──────────────────────────────────────────────────
        String konamiIdFilter = pageState.konamiId.toLowerCase().trim();
        if (!konamiIdFilter.isEmpty()) {
            boolean matches = card.getKonamiId() != null
                    && card.getKonamiId().toLowerCase().contains(konamiIdFilter);
            if (!matches) {
                return false;
            }
        }

        // ── Effect / Description filter ───────────────────────────────────────
        String effectFilter = pageState.effect.toLowerCase().trim();
        if (!effectFilter.isEmpty()) {
            boolean matches = card.getDescription() != null
                    && card.getDescription().toLowerCase().contains(effectFilter);
            if (!matches) {
                return false;
            }
        }

        // ── Category (card type) filter ───────────────────────────────────────
        if (!"(All)".equals(pageState.cardType)) {
            if (card.getCardType() == null
                    || !card.getCardType().contains(pageState.cardType)) {
                return false;
            }
        }

        // ── Subtype filter ────────────────────────────────────────────────────
        if (!pageState.cardSubtypes.isEmpty()) {
            if (card.getCardProperties() == null) {
                return false;
            }
            for (String subtype : pageState.cardSubtypes) {
                if (!card.getCardProperties().contains(subtype)) {
                    return false;
                }
            }
        }

        // ── Monster-only fields ───────────────────────────────────────────────
        if ("Monster".equals(pageState.cardType) || "(All)".equals(pageState.cardType)) {
            if (!"(All)".equals(pageState.attribute)) {
                if (card.getAttribute() == null
                        || !card.getAttribute().equalsIgnoreCase(pageState.attribute)) {
                    return false;
                }
            }
            if (!"(All)".equals(pageState.type)) {
                if (card.getCardProperties() == null
                        || !card.getCardProperties().contains(pageState.type)) {
                    return false;
                }
            }
            if (!matchesIntField(pageState.atk, card.getAtk())) {
                return false;
            }
            if (!matchesIntField(pageState.def, card.getDef())) {
                return false;
            }
            if (!pageState.level.isEmpty()) {
                boolean matchesLvRnkLnk =
                        matchesIntField(pageState.level, card.getLevel())
                                || matchesIntField(pageState.level, card.getRank())
                                || matchesIntField(pageState.level, card.getLinkVal());
                if (!matchesLvRnkLnk) {
                    return false;
                }
            }
            if (pageState.cardSubtypes.isEmpty() || pageState.cardSubtypes.contains("Pendulum")) {
                if (!matchesIntField(pageState.scale, card.getScale())) {
                    return false;
                }
            }
        }

        // ── Link Marker filter ────────────────────────────────────────────────
        if (!"Spell".equals(pageState.cardType) && !"Trap".equals(pageState.cardType)) {
            if (pageState.linkMarkers != null && !pageState.linkMarkers.isEmpty()) {
                List<String> cardMarkers = card.getLinkMarker();
                if (cardMarkers == null) {
                    return false;
                }
                for (String marker : pageState.linkMarkers) {
                    if (!cardMarkers.contains(marker)) {
                        return false;
                    }
                }
            }
        }

        // ── Word Count filter ─────────────────────────────────────────────────
        if (!pageState.wordCount.isEmpty()) {
            String desc = card.getDescription();
            int wordCount = (desc == null || desc.isBlank()) ? 0
                    : desc.trim().split("\\s+").length;
            if (!matchesIntField(pageState.wordCount, wordCount)) {
                return false;
            }
        }

        // ── Price filter ──────────────────────────────────────────────────────
        if (!pageState.price.isEmpty()) {
            if (!matchesDoubleField(pageState.price, card.getPrice())) {
                return false;
            }
        }

        // ── Archetype filter ──────────────────────────────────────────────────
        if (!pageState.archetype.isBlank() && !"(All)".equals(pageState.archetype)) {
            List<String> archetypeNames = Model.CardsLists.SubListCreator.archetypesList;
            List<List<Card>> archetypeCardLists =
                    Model.CardsLists.SubListCreator.archetypesCardsLists;
            if (archetypeNames == null || archetypeNames.isEmpty()) {
                return false;
            }
            int archetypeIndex = -1;
            for (int index = 0; index < archetypeNames.size(); index++) {
                if (pageState.archetype.equalsIgnoreCase(archetypeNames.get(index))) {
                    archetypeIndex = index;
                    break;
                }
            }
            if (archetypeIndex < 0 || archetypeIndex >= archetypeCardLists.size()) {
                return false;
            }
            List<Card> members = archetypeCardLists.get(archetypeIndex);
            if (members == null) {
                return false;
            }
            String cardKonamiId = card.getKonamiId();
            String cardPassCode = card.getPassCode();
            boolean isMember = false;
            for (Card member : members) {
                if (member == null) {
                    continue;
                }
                if (cardKonamiId != null && cardKonamiId.equals(member.getKonamiId())) {
                    isMember = true;
                    break;
                }
                if (cardPassCode != null && cardPassCode.equals(member.getPassCode())) {
                    isMember = true;
                    break;
                }
            }
            if (!isMember) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesIntField(String filterText, Integer cardValue) {
        if (filterText == null || filterText.isBlank()) {
            return true;
        }
        filterText = filterText.trim();
        if (filterText.contains("-")) {
            String[] parts = filterText.split("-", 2);
            try {
                int low = Integer.parseInt(parts[0].trim());
                int high = Integer.parseInt(parts[1].trim());
                return cardValue != null && cardValue >= low && cardValue <= high;
            } catch (NumberFormatException ignored) {
                return true;
            }
        }
        try {
            int target = Integer.parseInt(filterText);
            return cardValue != null && cardValue == target;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    private boolean matchesDoubleField(String filterText, String cardPriceStr) {
        if (filterText == null || filterText.isBlank()) {
            return true;
        }
        filterText = filterText.trim();
        if (cardPriceStr == null || cardPriceStr.isBlank()) {
            return false;
        }
        double cardValue;
        try {
            cardValue = Double.parseDouble(cardPriceStr.replace(',', '.').trim());
        } catch (NumberFormatException ignored) {
            return false;
        }
        if (filterText.contains("-")) {
            String[] parts = filterText.split("-", 2);
            try {
                double low = Double.parseDouble(parts[0].trim().replace(',', '.'));
                double high = Double.parseDouble(parts[1].trim().replace(',', '.'));
                return cardValue >= low && cardValue <= high;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        try {
            double target = Double.parseDouble(filterText.replace(',', '.'));
            return cardValue == target;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    // =========================================================================
    // Empty-space handlers (used by sub-controllers when building TreeViews)
    // =========================================================================

    /**
     * Returns an event filter for the middle-pane TreeView that clears the card
     * selection when the user clicks on an empty area below all tree nodes.
     *
     * @return an EventHandler suitable for {@code treeView.addEventFilter(...)}
     */
    public javafx.event.EventHandler<javafx.scene.input.MouseEvent> buildMiddlePaneEmptySpaceFilter() {
        return event -> {
            if (event.isControlDown() || event.isShiftDown()) {
                return;
            }
            Node current = (Node) event.getTarget();
            while (current != null) {
                if (Boolean.TRUE.equals(current.getProperties().get("cardWrapper"))) {
                    return;
                }
                if (current instanceof TreeView) {
                    break;
                }
                current = current.getParent();
            }
            SelectionManager.clearSelection();
        };
    }

    /**
     * Returns an event handler for the right-panel ListView that clears the selection
     * when the user clicks on empty space (i.e. a non-consumed click).
     *
     * @return an EventHandler suitable for {@code listView.addEventHandler(...)}
     */
    public javafx.event.EventHandler<javafx.scene.input.MouseEvent> buildRightPaneEmptySpaceClearHandler() {
        return event -> {
            if (!event.isConsumed() && !event.isControlDown() && !event.isShiftDown()) {
                SelectionManager.clearSelection();
            }
        };
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
                pasteCardsIntoNavigationItem(cards, modelObj);
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
                pasteCardsIntoNavigationItem(cards, modelObj);
                Platform.runLater(() -> {
                    List<CardElement> targetElements = getTargetGroupElements(modelObj);
                    int startIndex = Math.max(0, targetElements.size() - count);
                    SelectionManager.clearSelection();
                    for (int index = startIndex; index < targetElements.size(); index++) {
                        SelectionManager.toggleElementSelection(targetElements.get(index));
                    }
                    CardTreeCell.refreshAllGridViews();
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
        if (myCollectionTabHandle != null) {
            boolean dirty = UserInterfaceFunctions.isMyCollectionDirty();
            String base = "My Collection";
            myCollectionTabHandle.setText(dirty ? "* " + base : base);
        }
        if (decksTabHandle != null) {
            boolean dirty = UserInterfaceFunctions.isAnyDeckOrCollectionDirty();
            String base = "Decks and Collections";
            decksTabHandle.setText(dirty ? "* " + base : base);
        }
        if (ouicheListTabHandle != null) {
            boolean dirty = UserInterfaceFunctions.isOuicheListDirty();
            String base = "OuicheList";
            ouicheListTabHandle.setText(dirty ? "* " + base : base);
        }
    }

    // =========================================================================
    // Save buttons
    // =========================================================================

    private void wireSaveButtons() {
        if (myCollectionTab.getSaveButton() != null) {
            myCollectionTab.getSaveButton().setOnAction(event -> {
                try {
                    UserInterfaceFunctions.saveMyCollection();
                    updateTabDirtyIndicators();
                    myCollectionController.populateMyCollectionMenu();
                } catch (Exception exception) {
                    logger.error("Error saving My Collection", exception);
                }
            });
        }

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
                    CardTreeCell.refreshAllGridViews();
                }
            });
        }

        if (decksTab.getSaveButton() != null) {
            decksTab.getSaveButton().setOnAction(event -> {
                try {
                    UserInterfaceFunctions.saveAllDecksAndCollections();
                    updateTabDirtyIndicators();
                    decksController.populateDecksAndCollectionsMenu();
                } catch (Exception exception) {
                    logger.error("Error saving Decks and Collections", exception);
                }
            });
        }

        if (ouicheListTab.getSaveButton() != null) {
            ouicheListTab.getSaveButton().setOnAction(event -> {
                try {
                    UserInterfaceFunctions.saveOuicheList();
                    updateTabDirtyIndicators();
                } catch (Exception exception) {
                    logger.error("Error saving OuicheList", exception);
                }
            });
        }
    }

    // =========================================================================
    // Global keyboard shortcuts
    // =========================================================================

    private void setupGlobalKeyShortcuts() {
        mainTabPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(
                        javafx.scene.input.KeyEvent.KEY_PRESSED,
                        this::handleGlobalKeyShortcut);
            }
        });
    }

    private void handleGlobalKeyShortcut(javafx.scene.input.KeyEvent event) {
        // CTRL+S and CTRL+SHIFT+S — work even when a text field has focus.
        if (event.getCode() == javafx.scene.input.KeyCode.S && event.isControlDown()) {
            handleSaveShortcut(event.isShiftDown());
            event.consume();
            return;
        }

        // CTRL+Tab (next tab) / CTRL+SHIFT+Tab (previous tab).
        if (event.getCode() == javafx.scene.input.KeyCode.TAB && event.isControlDown()) {
            if (mainTabPane != null) {
                int total = mainTabPane.getTabs().size();
                if (total > 1) {
                    int current = mainTabPane.getSelectionModel().getSelectedIndex();
                    int next = event.isShiftDown()
                            ? (current - 1 + total) % total
                            : (current + 1) % total;
                    mainTabPane.getSelectionModel().select(next);
                }
            }
            event.consume();
            return;
        }

        // Remaining shortcuts should not fire while typing in a text field.
        if (event.getTarget() instanceof javafx.scene.control.TextInputControl) {
            return;
        }

        boolean middleSelectionActive =
                "MIDDLE".equals(SelectionManager.getActivePart())
                        && !SelectionManager.getSelectedMiddleElements().isEmpty();
        boolean anySelectionActive =
                !SelectionManager.getSelectedCards().isEmpty()
                        || !SelectionManager.getSelectedMiddleElements().isEmpty();

        switch (event.getCode()) {
            case ESCAPE -> {
                if (anySelectionActive) {
                    SelectionManager.clearSelection();
                    event.consume();
                }
            }
            case DELETE -> {
                if (middleSelectionActive) {
                    handleDeleteMiddleSelection();
                    event.consume();
                }
            }
            case C -> {
                if (event.isControlDown() && anySelectionActive) {
                    handleCopySelectionToClipboard();
                    event.consume();
                }
            }
            case X -> {
                if (event.isControlDown() && middleSelectionActive) {
                    handleCutFromKeyboard();
                    event.consume();
                }
            }
            case D -> {
                if (event.isControlDown() && middleSelectionActive) {
                    handleDuplicateMiddleSelection();
                    event.consume();
                }
            }
            case V -> {
                if (event.isControlDown() && !CardClipboard.isEmpty()) {
                    handlePasteFromKeyboard();
                    event.consume();
                }
            }
            case NUMPAD1 -> {
                handleNumpadAddFromRightPane(1);
                event.consume();
            }
            case NUMPAD2 -> {
                handleNumpadAddFromRightPane(2);
                event.consume();
            }
            case NUMPAD3 -> {
                handleNumpadAddFromRightPane(3);
                event.consume();
            }
            case NUMPAD4 -> {
                handleNumpadAddFromRightPane(4);
                event.consume();
            }
            case NUMPAD5 -> {
                handleNumpadAddFromRightPane(5);
                event.consume();
            }
            case NUMPAD6 -> {
                handleNumpadAddFromRightPane(6);
                event.consume();
            }
            case NUMPAD7 -> {
                handleNumpadAddFromRightPane(7);
                event.consume();
            }
            case NUMPAD8 -> {
                handleNumpadAddFromRightPane(8);
                event.consume();
            }
            case NUMPAD9 -> {
                handleNumpadAddFromRightPane(9);
                event.consume();
            }
            default -> { }
        }
    }

    private void handleSaveShortcut(boolean saveAll) {
        if (mainTabPane == null) {
            return;
        }
        int selectedIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        try {
            if (saveAll) {
                if (UserInterfaceFunctions.isMyCollectionDirty()) {
                    UserInterfaceFunctions.saveMyCollection();
                }
                if (UserInterfaceFunctions.isAnyDeckOrCollectionDirty()) {
                    UserInterfaceFunctions.saveAllDecksAndCollections();
                }
                if (UserInterfaceFunctions.isOuicheListDirty()) {
                    UserInterfaceFunctions.saveOuicheList();
                }
            } else {
                switch (selectedIndex) {
                    case 0 -> UserInterfaceFunctions.saveMyCollection();
                    case 1 -> UserInterfaceFunctions.saveAllDecksAndCollections();
                    case 2 -> UserInterfaceFunctions.saveOuicheList();
                    default -> {
                    }
                }
            }
            updateTabDirtyIndicators();
        } catch (Exception exception) {
            logger.error("Error during save shortcut", exception);
        }
    }

    public void handleDeleteMiddleSelection() {
        java.util.Set<CardElement> selectedElements =
                SelectionManager.getSelectedMiddleElements();
        if (selectedElements.isEmpty()) {
            return;
        }

        if (selectedElements.size() > 10) {
            boolean confirmed = View.NavigationContextMenuBuilder.confirmWithCustomMessage(
                    "Delete " + selectedElements.size() + " cards?");
            if (!confirmed) {
                return;
            }
        }

        Card cardToReselect = null;
        CardElement elementBeingRemoved = null;
        if (selectedElements.size() == 1) {
            elementBeingRemoved = selectedElements.iterator().next();
            cardToReselect = elementBeingRemoved != null ? elementBeingRemoved.getCard() : null;
        }

        int activeTabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        if (activeTabIndex == 0) {
            MenuActionHandler.handleBulkRemoveFromOwnedCollection(
                    new ArrayList<>(selectedElements));
        } else if (activeTabIndex == 1) {
            MenuActionHandler.handleBulkRemoveElementsFromDecksAndCollections(
                    new ArrayList<>(SelectionManager.getSelectedMiddleElements()));
        }
        SelectionManager.clearSelection();

        if (cardToReselect != null) {
            final Card targetCard = cardToReselect;
            final CardElement removedElement = elementBeingRemoved;
            Platform.runLater(() -> {
                TreeView<String> treeView = getActiveMiddleTreeView();
                if (treeView == null) {
                    return;
                }
                List<CardElement> allElements =
                        View.CardTreeCell.collectAllElementsInTreeOrder(treeView.getRoot());
                CardElement candidate = null;
                for (CardElement cardElement : allElements) {
                    if (cardElement == removedElement) {
                        continue;
                    }
                    Card card = cardElement.getCard();
                    if (card == null) {
                        continue;
                    }
                    if (card == targetCard) {
                        candidate = cardElement;
                        continue;
                    }
                    if (targetCard.getPassCode() != null
                            && targetCard.getPassCode().equals(card.getPassCode())) {
                        candidate = cardElement;
                        continue;
                    }
                    if (targetCard.getPrintCode() != null
                            && targetCard.getPrintCode().equals(card.getPrintCode())) {
                        candidate = cardElement;
                        continue;
                    }
                    if (targetCard.getKonamiId() != null
                            && targetCard.getKonamiId().equals(card.getKonamiId())) {
                        candidate = cardElement;
                    }
                }
                if (candidate != null) {
                    SelectionManager.selectElement(candidate);
                }
            });
        }
    }

    private void handleCopySelectionToClipboard() {
        if ("MIDDLE".equals(SelectionManager.getActivePart())) {
            List<Card> cardsToCopy = new ArrayList<>();
            for (CardElement element : SelectionManager.getSelectedMiddleElements()) {
                if (element.getCard() != null) {
                    cardsToCopy.add(element.getCard());
                }
            }
            if (!cardsToCopy.isEmpty()) {
                CardClipboard.copyCards(cardsToCopy);
            }
        } else {
            java.util.Set<Card> selectedCards = SelectionManager.getSelectedCards();
            if (!selectedCards.isEmpty()) {
                CardClipboard.copyCards(new ArrayList<>(selectedCards));
            }
        }
    }

    private void handleCutFromKeyboard() {
        handleCopySelectionToClipboard();
        handleDeleteMiddleSelection();
    }

    public void handleDuplicateMiddleSelection() {
        TreeView<String> activeTreeView = getActiveMiddleTreeView();
        if (activeTreeView == null) {
            return;
        }
        java.util.Set<CardElement> selectedElements =
                SelectionManager.getSelectedMiddleElements();
        if (selectedElements.isEmpty()) {
            return;
        }
        List<CardElement> allElementsInOrder =
                View.CardTreeCell.collectAllElementsInTreeOrder(activeTreeView.getRoot());
        List<CardElement> selectedInOrder = allElementsInOrder.stream()
                .filter(selectedElements::contains)
                .collect(Collectors.toList());
        if (selectedInOrder.isEmpty()) {
            return;
        }
        CardElement lastElement = selectedInOrder.get(selectedInOrder.size() - 1);
        List<Card> cardsToInsert = selectedInOrder.stream()
                .map(CardElement::getCard)
                .collect(Collectors.toList());

        boolean inserted = MenuActionHandler.handleInsertCardsAfterElement(
                cardsToInsert, lastElement);
        if (!inserted) {
            logger.warn("handleDuplicateMiddleSelection: insertion failed");
            return;
        }

        int activeTabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        if (activeTabIndex == 0) {
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionView();
        } else if (activeTabIndex == 1) {
            Object owner = findDeckOrCollectionOwner(lastElement);
            if (owner != null) {
                UserInterfaceFunctions.markDirty(owner);
            }
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        }
    }

    private void handlePasteFromKeyboard() {
        if (CardClipboard.isEmpty()) {
            return;
        }
        List<Card> clipboardCards = CardClipboard.getContents();

        // Priority 1: after the last element of the current MIDDLE selection
        if ("MIDDLE".equals(SelectionManager.getActivePart())
                && !SelectionManager.getSelectedMiddleElements().isEmpty()) {
            TreeView<String> activeTreeView = getActiveMiddleTreeView();
            if (activeTreeView != null) {
                List<CardElement> allElementsInOrder =
                        View.CardTreeCell.collectAllElementsInTreeOrder(activeTreeView.getRoot());
                java.util.Set<CardElement> selectedElements =
                        SelectionManager.getSelectedMiddleElements();
                CardElement lastElement = null;
                for (int index = allElementsInOrder.size() - 1; index >= 0; index--) {
                    if (selectedElements.contains(allElementsInOrder.get(index))) {
                        lastElement = allElementsInOrder.get(index);
                        break;
                    }
                }
                if (lastElement != null && pasteCardsAfterElement(clipboardCards, lastElement)) {
                    return;
                }
            }
        }

        // Priority 2: after the last MIDDLE element that was explicitly clicked
        CardElement lastMiddleElement = SelectionManager.getLastMiddleElement();
        if (lastMiddleElement != null
                && pasteCardsAfterElement(clipboardCards, lastMiddleElement)) {
            return;
        }

        // Priority 3: into the last clicked navigation-menu item
        Object lastNavItem = SelectionManager.getLastClickedNavigationItem();
        if (lastNavItem != null) {
            pasteCardsIntoNavigationItem(clipboardCards, lastNavItem);
        }
    }

    // =========================================================================
    // Window close request
    // =========================================================================

    private void handleWindowCloseRequest(WindowEvent event) {
        List<String> dirtyTabs = new ArrayList<>();
        if (myCollectionTabHandle != null
                && myCollectionTabHandle.getText().startsWith("*")) {
            dirtyTabs.add("My Collection");
        }
        if (decksTabHandle != null && decksTabHandle.getText().startsWith("*")) {
            dirtyTabs.add("Decks & Collections");
        }
        if (ouicheListTabHandle != null && ouicheListTabHandle.getText().startsWith("*")) {
            dirtyTabs.add("OuicheList");
        }

        if (dirtyTabs.isEmpty()) {
            return;
        }

        event.consume();

        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("The following tabs have unsaved changes:");
        alert.setContentText(String.join("\n", dirtyTabs));

        ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType noSaveButton = new ButtonType("Don't Save", ButtonBar.ButtonData.NO);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(saveButton, noSaveButton, cancelButton);

        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle(
                "-fx-background-color: #100317; "
                        + "-fx-border-color: #5a2a7a; "
                        + "-fx-border-width: 1;");
        dialogPane.applyCss();
        dialogPane.layout();
        dialogPane.lookupAll(".header-panel")
                .forEach(node -> node.setStyle("-fx-background-color: #100317;"));
        dialogPane.lookupAll(".label")
                .forEach(node -> node.setStyle("-fx-text-fill: white;"));
        dialogPane.lookupAll(".button")
                .forEach(node -> node.setStyle(
                        "-fx-background-color: #2a0a3e; "
                                + "-fx-text-fill: white; "
                                + "-fx-border-color: #7a3aaa; "
                                + "-fx-border-width: 1; "
                                + "-fx-border-radius: 3; "
                                + "-fx-background-radius: 3; "
                                + "-fx-cursor: hand;"));

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isEmpty() || result.get() == cancelButton) {
            return;
        }

        if (result.get() == saveButton) {
            try {
                if (myCollectionTabHandle != null
                        && myCollectionTabHandle.getText().startsWith("*")) {
                    UserInterfaceFunctions.saveMyCollection();
                }
                if (decksTabHandle != null && decksTabHandle.getText().startsWith("*")) {
                    UserInterfaceFunctions.saveAllDecksAndCollections();
                }
                if (ouicheListTabHandle != null && ouicheListTabHandle.getText().startsWith("*")) {
                    UserInterfaceFunctions.saveOuicheList();
                }
                updateTabDirtyIndicators();
            } catch (Exception exception) {
                logger.error("Save-on-close failed", exception);
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("Save Error");
                errorAlert.setHeaderText("Could not save all changes.");
                errorAlert.setContentText(exception.getMessage());
                errorAlert.getDialogPane().setStyle("-fx-background-color: #100317;");
                errorAlert.getDialogPane().applyCss();
                errorAlert.getDialogPane().layout();
                errorAlert.getDialogPane().lookupAll(".label")
                        .forEach(node -> node.setStyle("-fx-text-fill: white;"));
                errorAlert.showAndWait();
                return;
            }
        }

        Platform.exit();
    }

    private void handleNumpadAddFromRightPane(int count) {
        int tabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        if (tabIndex != 0 && tabIndex != 1) {
            return;
        }
        List<Card> sourceCards = getNumpadSourceCards();
        if (sourceCards.isEmpty()) {
            return;
        }
        List<Card> toInsert = new ArrayList<>();
        for (Card card : sourceCards) {
            for (int repeatIndex = 0; repeatIndex < count; repeatIndex++) {
                toInsert.add(card);
            }
        }
        insertCardsAtNumpadTarget(toInsert);
    }

    private void handleEnterAddFromRightPane() {
        int tabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        if (tabIndex != 0 && tabIndex != 1) {
            return;
        }
        List<Card> displayed = getDisplayedRightPaneCards();
        if (displayed.size() != 1) {
            return;
        }
        List<Card> toInsert = new ArrayList<>();
        toInsert.add(displayed.get(0));
        insertCardsAtNumpadTarget(toInsert);
    }

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

    private Object findDeckOrCollectionOwner(CardElement element) {
        if (element == null) {
            return null;
        }
        DecksAndCollectionsList decksList = UserInterfaceFunctions.getDecksList();
        if (decksList == null) {
            return null;
        }
        if (decksList.getCollections() != null) {
            for (ThemeCollection collection : decksList.getCollections()) {
                if (collection.getCardsList() != null
                        && collection.getCardsList().contains(element)) {
                    return collection;
                }
                if (collection.getLinkedDecks() != null) {
                    for (List<Deck> unit : collection.getLinkedDecks()) {
                        if (unit == null) {
                            continue;
                        }
                        for (Deck deck : unit) {
                            if (containsElementInDeck(deck, element)) {
                                return deck;
                            }
                        }
                    }
                }
            }
        }
        if (decksList.getDecks() != null) {
            for (Deck deck : decksList.getDecks()) {
                if (containsElementInDeck(deck, element)) {
                    return deck;
                }
            }
        }
        return null;
    }

    private boolean containsElementInDeck(Deck deck, CardElement element) {
        if (deck == null) {
            return false;
        }
        if (deck.getMainDeck() != null && deck.getMainDeck().contains(element)) {
            return true;
        }
        if (deck.getExtraDeck() != null && deck.getExtraDeck().contains(element)) {
            return true;
        }
        if (deck.getSideDeck() != null && deck.getSideDeck().contains(element)) {
            return true;
        }
        return false;
    }

    private List<CardElement> getTargetGroupElements(Object modelObj) {
        if (modelObj instanceof CardsGroup) {
            List<CardElement> list = ((CardsGroup) modelObj).getCardList();
            return list != null ? list : new ArrayList<>();
        }
        if (modelObj instanceof Box box) {
            if (box.getContent() != null && !box.getContent().isEmpty()) {
                return box.getContent().get(box.getContent().size() - 1).getCardList();
            }
        }
        if (modelObj instanceof Deck deck) {
            if (deck.getMainDeck() != null) {
                return deck.getMainDeck();
            }
        }
        if (modelObj instanceof ThemeCollection collection) {
            List<CardElement> cardsList = collection.getCardsList();
            return cardsList != null ? cardsList : new ArrayList<>();
        }
        return new ArrayList<>();
    }

    private boolean pasteCardsAfterElement(List<Card> cards, CardElement anchor) {
        if (anchor == null || cards == null || cards.isEmpty()) {
            return false;
        }
        boolean inserted = MenuActionHandler.handleInsertCardsAfterElement(cards, anchor);
        if (!inserted) {
            return false;
        }
        int activeTabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        if (activeTabIndex == 0) {
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionView();
        } else if (activeTabIndex == 1) {
            Object owner = findDeckOrCollectionOwner(anchor);
            if (owner != null) {
                UserInterfaceFunctions.markDirty(owner);
            }
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshDecksAndCollectionsView();
        }
        return true;
    }

    private void pasteCardsIntoNavigationItem(List<Card> cards, Object modelObj) {
        if (cards == null || cards.isEmpty() || modelObj == null) {
            return;
        }

        if (modelObj instanceof Box box) {
            CardsGroup defaultGroup = MenuActionHandler.getOrCreateDefaultGroup(box);
            if (defaultGroup == null) {
                return;
            }
            javafx.collections.ObservableList<CardElement> observableList =
                    CardTreeCell.observableListFor(defaultGroup);
            for (Card card : cards) {
                if (card != null) {
                    observableList.add(new CardElement(card));
                }
            }
            CardTreeCell.triggerHeightAdjustment(defaultGroup);
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionView();

        } else if (modelObj instanceof CardsGroup group) {
            javafx.collections.ObservableList<CardElement> observableList =
                    CardTreeCell.observableListFor(group);
            for (Card card : cards) {
                if (card != null) {
                    observableList.add(new CardElement(card));
                }
            }
            CardTreeCell.triggerHeightAdjustment(group);
            UserInterfaceFunctions.markMyCollectionDirty();
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            UserInterfaceFunctions.refreshOwnedCollectionView();

        } else if (modelObj instanceof Deck deck) {
            if (deck.getMainDeck() == null) {
                deck.setMainDeck(new ArrayList<>());
            }
            if (deck.getExtraDeck() == null) {
                deck.setExtraDeck(new ArrayList<>());
            }

            List<Card> toMain = new ArrayList<>();
            List<Card> toExtra = new ArrayList<>();
            for (Card card : cards) {
                if (card == null) {
                    continue;
                }
                if (Utils.DeckCompatibility.isExtraDeckCard(card)) {
                    toExtra.add(card);
                } else {
                    toMain.add(card);
                }
            }

            java.util.function.BiConsumer<List<Card>, String> addToSection =
                    (sectionCards, sectionKey) -> {
                        if (sectionCards.isEmpty()) {
                            return;
                        }
                        CardsGroup sectionGroup =
                                CardTreeCell.getDeckSectionGroup(deck, sectionKey);
                        if (sectionGroup != null) {
                            javafx.collections.ObservableList<CardElement> obs =
                                    CardTreeCell.observableListFor(sectionGroup);
                            for (Card card : sectionCards) {
                                obs.add(new CardElement(card));
                            }
                            CardTreeCell.triggerHeightAdjustment(sectionGroup);
                        } else {
                            List<CardElement> rawList = "extra".equals(sectionKey)
                                    ? deck.getExtraDeck()
                                    : deck.getMainDeck();
                            for (Card card : sectionCards) {
                                rawList.add(new CardElement(card));
                            }
                            UserInterfaceFunctions.triggerDecksStructureRefresh();
                        }
                    };

            addToSection.accept(toMain, "main");
            addToSection.accept(toExtra, "extra");

            UserInterfaceFunctions.markDirty(deck);
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();

        } else if (modelObj instanceof ThemeCollection collection) {
            if (collection.getCardsList() == null) {
                collection.setCardsList(new ArrayList<>());
            }

            CardsGroup cardsGroup = CardTreeCell.getCollectionCardsGroup(collection);
            if (cardsGroup != null) {
                javafx.collections.ObservableList<CardElement> obs =
                        CardTreeCell.observableListFor(cardsGroup);
                for (Card card : cards) {
                    if (card != null) {
                        obs.add(new CardElement(card));
                    }
                }
                CardTreeCell.triggerHeightAdjustment(cardsGroup);
            } else {
                for (Card card : cards) {
                    if (card != null) {
                        collection.getCardsList().add(new CardElement(card));
                    }
                }
                UserInterfaceFunctions.triggerDecksStructureRefresh();
            }
            UserInterfaceFunctions.markDirty(collection);
            UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        }
    }

    private List<Card> getNumpadSourceCards() {
        List<Card> displayed = getDisplayedRightPaneCards();
        java.util.Set<Card> selected = SelectionManager.getSelectedCards();
        if (!selected.isEmpty()) {
            List<Card> visible = displayed.stream()
                    .filter(selected::contains)
                    .collect(Collectors.toList());
            if (!visible.isEmpty()) {
                return visible;
            }
        }
        if (displayed.size() == 1) {
            return new ArrayList<>(displayed);
        }
        return java.util.Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<Card> getDisplayedRightPaneCards() {
        if (cardsDisplayContainer == null) {
            return java.util.Collections.emptyList();
        }
        for (Node node : cardsDisplayContainer.getChildren()) {
            if (node instanceof ListView) {
                if (!isMosaicMode) {
                    ListView<Card> listView = (ListView<Card>) node;
                    return new ArrayList<>(listView.getItems());
                } else {
                    ListView<List<Card>> listView = (ListView<List<Card>>) node;
                    List<Card> all = new ArrayList<>();
                    for (List<Card> row : listView.getItems()) {
                        all.addAll(row);
                    }
                    return all;
                }
            }
        }
        return java.util.Collections.emptyList();
    }

    private CardElement getNumpadTargetMiddleElement() {
        if ("MIDDLE".equals(SelectionManager.getActivePart())
                && !SelectionManager.getSelectedMiddleElements().isEmpty()) {
            TreeView<String> treeView = getActiveMiddleTreeView();
            if (treeView != null) {
                List<CardElement> allElements =
                        View.CardTreeCell.collectAllElementsInTreeOrder(treeView.getRoot());
                java.util.Set<CardElement> selected =
                        SelectionManager.getSelectedMiddleElements();
                for (int index = allElements.size() - 1; index >= 0; index--) {
                    if (selected.contains(allElements.get(index))) {
                        return allElements.get(index);
                    }
                }
            }
        }
        CardElement last = SelectionManager.getLastMiddleElement();
        return elemBelongsToActiveTab(last) ? last : null;
    }

    private void insertCardsAtNumpadTarget(List<Card> toInsert) {
        int totalInserted = toInsert.size();

        CardElement targetElement = getNumpadTargetMiddleElement();
        if (targetElement != null && pasteCardsAfterElement(toInsert, targetElement)) {
            selectLastInsertedElement(targetElement, totalInserted);
            return;
        }

        Object navItem = SelectionManager.getLastClickedNavigationItem();
        if (navItem != null && navItemBelongsToActiveTab(navItem)) {
            List<CardElement> backing = getTargetGroupElements(navItem);
            pasteCardsIntoNavigationItem(toInsert, navItem);
            if (!backing.isEmpty()) {
                SelectionManager.selectElement(backing.get(backing.size() - 1));
            }
            return;
        }

        TreeView<String> treeView = getActiveMiddleTreeView();
        if (treeView == null || treeView.getRoot() == null) {
            return;
        }
        List<CardElement> allElements =
                View.CardTreeCell.collectAllElementsInTreeOrder(treeView.getRoot());
        if (!allElements.isEmpty()) {
            CardElement lastElement = allElements.get(allElements.size() - 1);
            if (pasteCardsAfterElement(toInsert, lastElement)) {
                selectLastInsertedElement(lastElement, totalInserted);
                return;
            }
        }

        Object lastNavModel = findLastNavModelInTree(treeView);
        if (lastNavModel != null) {
            List<CardElement> backing = getTargetGroupElements(lastNavModel);
            pasteCardsIntoNavigationItem(toInsert, lastNavModel);
            if (!backing.isEmpty()) {
                SelectionManager.selectElement(backing.get(backing.size() - 1));
            }
        }
    }

    private Object findLastNavModelInTree(TreeView<String> treeView) {
        if (treeView == null || treeView.getRoot() == null) {
            return null;
        }
        Object candidate = null;
        java.util.Queue<TreeItem<String>> queue = new java.util.LinkedList<>();
        queue.add(treeView.getRoot());
        while (!queue.isEmpty()) {
            TreeItem<String> item = queue.poll();
            if (item.getGraphic() instanceof NavigationItem navItem) {
                Object modelObj = navItem.getUserData();
                if (modelObj != null && !getTargetGroupElements(modelObj).isEmpty()) {
                    candidate = modelObj;
                }
            }
            queue.addAll(item.getChildren());
        }
        return candidate;
    }

    private void selectLastInsertedElement(CardElement anchor, int count) {
        TreeView<String> treeView = getActiveMiddleTreeView();
        if (treeView == null) {
            return;
        }
        List<CardElement> allElements =
                View.CardTreeCell.collectAllElementsInTreeOrder(treeView.getRoot());
        int anchorIndex = allElements.indexOf(anchor);
        if (anchorIndex < 0) {
            return;
        }
        int lastIndex = Math.min(anchorIndex + count, allElements.size() - 1);
        SelectionManager.selectElement(allElements.get(lastIndex));
    }

    private boolean navItemBelongsToActiveTab(Object navItem) {
        if (navItem == null) {
            return false;
        }
        int tabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        if (tabIndex == 0) {
            return navItem instanceof Box || navItem instanceof CardsGroup;
        }
        if (tabIndex == 1) {
            return navItem instanceof Deck || navItem instanceof ThemeCollection;
        }
        return false;
    }

    private boolean elemBelongsToActiveTab(CardElement element) {
        if (element == null) {
            return false;
        }
        TreeView<String> treeView = getActiveMiddleTreeView();
        if (treeView == null || treeView.getRoot() == null) {
            return false;
        }
        return View.CardTreeCell.collectAllElementsInTreeOrder(treeView.getRoot())
                .contains(element);
    }

    private CardElement findCardElementForCard(Card card) {
        if (card == null) {
            return null;
        }
        List<CardElement> ownedMatches =
                MenuActionHandler.findCardElementsForCards(
                        java.util.Collections.singletonList(card));
        if (!ownedMatches.isEmpty()) {
            return ownedMatches.get(0);
        }
        DecksAndCollectionsList decksList = UserInterfaceFunctions.getDecksList();
        if (decksList != null) {
            CardElement dacMatch = findCardElementInDac(card, decksList);
            if (dacMatch != null) {
                return dacMatch;
            }
        }
        return null;
    }

    private CardElement findCardElementInDac(Card card, DecksAndCollectionsList decksList) {
        if (decksList.getCollections() != null) {
            for (ThemeCollection collection : decksList.getCollections()) {
                if (collection == null) {
                    continue;
                }
                if (collection.getCardsList() != null) {
                    for (CardElement element : collection.getCardsList()) {
                        if (element != null
                                && Utils.CardMatcher.cardsMatch(element.getCard(), card)) {
                            return element;
                        }
                    }
                }
                if (collection.getLinkedDecks() != null) {
                    for (List<Deck> unit : collection.getLinkedDecks()) {
                        if (unit == null) {
                            continue;
                        }
                        for (Deck deck : unit) {
                            CardElement found = findCardElementInDeckLists(card, deck);
                            if (found != null) {
                                return found;
                            }
                        }
                    }
                }
            }
        }
        if (decksList.getDecks() != null) {
            for (Deck deck : decksList.getDecks()) {
                CardElement found = findCardElementInDeckLists(card, deck);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private CardElement findCardElementInDeckLists(Card card, Deck deck) {
        if (deck == null) {
            return null;
        }
        for (List<CardElement> deckList : java.util.Arrays.asList(
                deck.getMainDeck(), deck.getExtraDeck(), deck.getSideDeck())) {
            if (deckList == null) {
                continue;
            }
            for (CardElement element : deckList) {
                if (element != null
                        && Utils.CardMatcher.cardsMatch(element.getCard(), card)) {
                    return element;
                }
            }
        }
        return null;
    }

    /**
     * Returns the selected {@link CardElement}s from the active middle TreeView ordered
     * by their visual display position (top to bottom). Used by duplicate and cut.
     *
     * @param treeView the active middle TreeView
     * @return elements in display order; empty if nothing is selected or the tree is null
     */
    private List<CardElement> getSelectedElementsInDisplayOrder(TreeView<String> treeView) {
        if (treeView == null
                || SelectionManager.getSelectedMiddleElements().isEmpty()) {
            return new ArrayList<>();
        }
        java.util.Set<CardElement> selectedElements =
                SelectionManager.getSelectedMiddleElements();
        return View.CardTreeCell.collectAllElementsInTreeOrder(treeView.getRoot())
                .stream()
                .filter(selectedElements::contains)
                .collect(Collectors.toList());
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
        CardTreeCell.refreshAllGridViews();
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

    private SharedCollectionTab getSharedTabAt(int index) {
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