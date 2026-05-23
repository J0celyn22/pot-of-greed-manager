package Controller;

import Model.CardsLists.*;
import Utils.CardMatcher;
import Utils.CardNameUtils;
import View.*;
import View.SharedCollectionTab.TabType;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
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

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RealMainController - updated to fix My Collection navigation wiring only.
 * <p>
 * Important: this file intentionally modifies only the My Collection menu wiring so that
 * clicking items in the My Collection navigation selects and scrolls the corresponding
 * node in the main My Collection TreeView. Decks & Collections navigation is left unchanged.
 */
public class RealMainController {

    private static final Logger logger = LoggerFactory.getLogger(RealMainController.class);
    private static final String ARCHETYPE_MARKER = "[ARCHETYPE]";
    private final DoubleProperty cardWidthProperty = new SimpleDoubleProperty(100);
    private final DoubleProperty cardHeightProperty = new SimpleDoubleProperty(146);
    // Cache keyed by container object (Box or CardsGroup or other list owner).
    // Each value is a synchronized List<Boolean> where each index corresponds to the
    // card position inside that container. A null entry means "not yet computed".
    private final java.util.concurrent.ConcurrentHashMap<Object, List<Boolean>> positionSortCache = new java.util.concurrent.ConcurrentHashMap<>();
    @FXML
    private TabPane mainTabPane;

    private Button listMosaicButton;

    private Button printedUniqueButton;

    /**
     * Applies the "selected" visual to a sort toggle button:
     * yellow-green background (#cdfc04) with black text.
     * The inline style takes precedence over the CSS class so the button
     * always looks selected regardless of hover state.
     */
    // Padding kept in a constant so it is never accidentally dropped when the
    // button switches between selected / unselected via setStyle().
    private static final String SORT_BTN_PADDING =
            "-fx-font-size: 11px; "
                    + "-fx-padding: 1 3 3 3; "
                    + "-fx-min-width: 36px; "
                    + "-fx-pref-width: 36px; "
                    + "-fx-max-width: 36px;";
    // ── Sort toggle buttons ────────────────────────────────────────────────────
    // Exactly one is always "selected" (yellow-green fill, black text).
    // Clicking the selected one toggles its label; clicking another selects it.
    private Button sortAzButton;   // "AZ"  ↔ "ZA"
    private Button sortAtkButton;  // "ATK↓" ↔ "ATK↑"
    private Button sortDefButton;  // "DEF↓" ↔ "DEF↑"
    private Button sortLvlButton;  // "LVL↓" ↔ "LVL↑"
    private SharedCollectionTab myCollectionTab;
    private SharedCollectionTab decksTab;
    private SharedCollectionTab ouicheListTab;
    private SharedCollectionTab archetypesTab;
    private SharedCollectionTab friendsTab;
    private SharedCollectionTab shopsTab;

    private AnchorPane cardsDisplayContainer;
    // keep references to the TreeViews so navigation items can select/scroll to nodes
    private TreeView<String> myCollectionTreeView;
    private TreeView<String> ouicheTreeView;

    private boolean isMosaicMode = true;
    private boolean isPrintedMode = false;
    private final java.util.Set<FilterPane> wiredEnterPanes = new java.util.HashSet<>();
    private FilterPane sharedFilterPane;
    private boolean ouicheListLoaded = false;
    private TreeView<String> archetypesTreeView;
    private TreeView<String> decksAndCollectionsTreeView;

    private Tab myCollectionTabHandle;
    private Tab decksTabHandle;
    private Tab ouicheListTabHandle;

    /**
     * @deprecated Use {@link CardQualityService#computeCardNeedsSorting} instead.
     */
    @Deprecated
    public static boolean computeCardNeedsSorting(Model.CardsLists.Card card, String elementName) {
        return CardQualityService.computeCardNeedsSorting(card, elementName);
    }

    /**
     * @deprecated Use {@link CardQualityService#computeCardNeedsSortingWithUpgrade} instead.
     */
    @Deprecated
    public static boolean computeCardNeedsSortingWithUpgrade(
            Model.CardsLists.CardElement ownedElement,
            String elementName) {
        return CardQualityService.computeCardNeedsSortingWithUpgrade(ownedElement, elementName);
    }


    // -----------------------------------------------------------------------
    // Net-copies-needed helper
    // -----------------------------------------------------------------------

    /**
     * @deprecated Use {@link CardQualityService#computeNetCopiesNeeded} instead.
     */
    @Deprecated
    public static int computeNetCopiesNeeded(Model.CardsLists.Card card, String elementName) {
        return CardQualityService.computeNetCopiesNeeded(card, elementName);
    }

    // -----------------------------------------------------------------------
    // Quality-upgrade helpers (used by computeCardNeedsSorting and CardTreeCell)
    // -----------------------------------------------------------------------

    /**
     * @deprecated Use {@link CardQualityService#isBetterCondition} instead.
     */
    @Deprecated
    public static boolean isBetterCondition(
            Model.CardsLists.CardCondition candidate,
            Model.CardsLists.CardCondition existing) {
        return CardQualityService.isBetterCondition(candidate, existing);
    }

    /**
     * @deprecated Use {@link CardQualityService#satisfiesExpectedRarityBetter} instead.
     */
    @Deprecated
    public static boolean satisfiesExpectedRarityBetter(
            Model.CardsLists.CardRarity candidateRarity,
            Model.CardsLists.CardRarity existingRarity,
            Model.CardsLists.CardRarity expectedRarity) {
        return CardQualityService.satisfiesExpectedRarityBetter(candidateRarity, existingRarity, expectedRarity);
    }

    /**
     * @deprecated Use {@link CardQualityService#isQualityUpgrade} instead.
     */
    @Deprecated
    public static boolean isQualityUpgrade(
            java.util.List<Model.CardsLists.CardElement> existingInTarget,
            java.util.List<Model.CardsLists.CardElement> targetSlotElements,
            Model.CardsLists.CardElement candidate) {
        return CardQualityService.isQualityUpgrade(existingInTarget, targetSlotElements, candidate);
    }

    // -----------------------------------------------------------------------

    /**
     * @deprecated Use {@link CardQualityService#isDegradedCopyInDeckOrCollection} instead.
     */
    @Deprecated
    public static boolean isDegradedCopyInDeckOrCollection(
            Model.CardsLists.CardElement deckElement,
            String deckOrCollectionName) {
        return CardQualityService.isDegradedCopyInDeckOrCollection(deckElement, deckOrCollectionName);
    }

    /**
     * @deprecated Use {@link CardQualityService#findOwnedUpgradeCandidates} instead.
     */
    @Deprecated
    public static List<Model.CardsLists.CardElement> findOwnedUpgradeCandidates(
            Model.CardsLists.CardElement deckElement,
            String deckOrCollectionName) {
        return CardQualityService.findOwnedUpgradeCandidates(deckElement, deckOrCollectionName);
    }



    private void setupZoom(SharedCollectionTab tab) {
        if (tab == null) return;
        tab.getContentPane().addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                adjustCardSize(event.getDeltaY());
                event.consume();
            }
        });
    }

    private void adjustCardSize(double delta) {
        double scalingFactor = delta > 0 ? 1.1 : 0.9;
        double newWidth = cardWidthProperty.get() * scalingFactor;
        if (newWidth < 50) newWidth = 50;
        else if (newWidth > 300) newWidth = 300;
        cardWidthProperty.set(newWidth);
        cardHeightProperty.set(newWidth * 146.0 / 100.0);
    }

    // --- My Collection display ---

    /**
     * Returns the FilterPane for whichever SharedCollectionTab is currently active,
     * or null if no tab is active.
     */
    private FilterPane getActiveFilterPane() {
        return sharedFilterPane;
    }
    // 0 = AZ/ZA  |  1 = ATK  |  2 = DEF  |  3 = LVL
    private int activeSortButtonIndex = 1; // ATK↓ selected by default


    /**
     * Builds the active card filter for the middle pane from every FilterPage that is
     * both ENABLED and has its bottom-left arrow ENABLED, then pushes it into all live
     * GridViews via {@link View.CardTreeCell#setMiddleFilter}.
     *
     * <p>Called by the {@code onLeftFilterChange} callback wired in
     * {@link #injectSharedRightPanel}.</p>
     */
    private void updateMiddlePaneDisplay() {
        FilterPane fp = getActiveFilterPane();

        // Flush latest field values from the currently-visible page into pageStates.
        if (fp != null) {
            fp.saveCurrentPageState();
        }

        // Collect every page that is both ENABLED and has its bottom-left arrow ENABLED.
        List<FilterPane.FilterPageState> activeStates = new ArrayList<>();
        if (fp != null) {
            for (int i = 0; i < 5; i++) {
                FilterPane.FilterPageState ps = fp.getPageState(i);
                if (ps != null && ps.enabled && ps.bottomLeftEnabled) {
                    activeStates.add(ps);
                }
            }
        }

        if (activeStates.isEmpty()) {
            // No active page targets the middle pane — clear any existing filter.
            View.CardTreeCell.setMiddleFilter(null);
        } else {
            // A card must satisfy ALL active pages (AND semantics across pages).
            final List<FilterPane.FilterPageState> captured = activeStates;
            View.CardTreeCell.setMiddleFilter(card -> {
                for (FilterPane.FilterPageState ps : captured) {
                    if (!matchesPageFilter(card, ps)) return false;
                }
                return true;
            });
        }
    }

    private void updateCardsDisplay() {
        FilterPane fp = getActiveFilterPane();

        // Ensure the currently-visible page's field values are flushed to pageStates
        // before we read them (handles the case where the user typed without switching).
        if (fp != null) {
            fp.saveCurrentPageState();
        }

        // Collect every page that is both ENABLED and has its bottom-right arrow ENABLED.
        // Only those pages contribute to the AllExistingCards filter.
        List<FilterPane.FilterPageState> activeStates = new ArrayList<>();
        if (fp != null) {
            for (int i = 0; i < 5; i++) {
                FilterPane.FilterPageState ps = fp.getPageState(i);
                if (ps != null && ps.enabled && ps.bottomRightEnabled) {
                    activeStates.add(ps);
                }
            }
        }

        List<Card> allCards;
        try {
            allCards = isPrintedMode
                    ? Model.Database.Database.getAllPrintedCardsList().values()
                    .stream().collect(Collectors.toList())
                    : Model.Database.Database.getAllCardsList().values()
                    .stream().collect(Collectors.toList());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        // A card must satisfy ALL active pages' filters (AND semantics across pages).
        List<Card> filteredCards = allCards.stream()
                .filter(card -> {
                    for (FilterPane.FilterPageState ps : activeStates) {
                        if (!matchesPageFilter(card, ps)) return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        // Apply the active sort (main type → subcategory → stat/alpha).
        filteredCards = Utils.CardSorter.sort(filteredCards, getCurrentSortMode());

        Node view;
        double mosaicImageWidth = 100, mosaicImageHeight = 146;
        double listImageWidth = 80, listImageHeight = 116;
        if (isMosaicMode) {
            double availableWidth =
                    (cardsDisplayContainer == null ? 375 : cardsDisplayContainer.getWidth());
            if (availableWidth <= 0) availableWidth = 375;
            double gap = 5;
            List<List<Card>> rows =
                    groupCardsIntoRows(filteredCards, availableWidth, mosaicImageWidth, gap);
            ListView<List<Card>> mosaicListView =
                    new ListView<>(FXCollections.observableArrayList(rows));
            mosaicListView.setCellFactory(
                    param -> new CardsMosaicRowCell(mosaicImageWidth, mosaicImageHeight));
            mosaicListView.setStyle(
                    "-fx-background-color: #100317; -fx-control-inner-background: #100317;");
            mosaicListView.addEventHandler(
                    javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                    buildRightPaneEmptySpaceClearHandler());
            view = mosaicListView;
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
            view = listView;
        }

        if (cardsDisplayContainer != null) {
            cardsDisplayContainer.getChildren().clear();
            cardsDisplayContainer.getChildren().add(view);
            AnchorPane.setTopAnchor(view, 0.0);
            AnchorPane.setBottomAnchor(view, 0.0);
            AnchorPane.setLeftAnchor(view, 0.0);
            AnchorPane.setRightAnchor(view, 0.0);
        }
    }

    /**
     * Returns the SharedCollectionTab for a given tab index, or null if out of range.
     */
    private SharedCollectionTab getSharedTabAt(int index) {
        switch (index) {
            case 0:
                return myCollectionTab;
            case 1:
                return decksTab;
            case 2:
                return ouicheListTab;
            case 3:
                return archetypesTab;
            case 4:
                return friendsTab;
            case 5:
                return shopsTab;
            default:
                return null;
        }
    }

    private List<List<Card>> groupCardsIntoRows(List<Card> cards, double availableWidth, double cellWidth, double gap) {
        int cardsPerRow = (int) Math.floor((availableWidth + gap) / (cellWidth + gap));
        if (cardsPerRow < 1) cardsPerRow = 1;
        List<List<Card>> rows = new ArrayList<>();
        for (int i = 0; i < cards.size(); i += cardsPerRow) {
            int end = Math.min(i + cardsPerRow, cards.size());
            rows.add(new ArrayList<>(cards.subList(i, end)));
        }
        return rows;
    }

    /**
     * Moves the shared cardsDisplayContainer (with list/mosaic + printed/unique toggle buttons)
     * into the given tab's right-content pane.
     * The FilterPane is already owned by each SharedCollectionTab's rightHeaderPane — no injection needed.
     */
    private void injectSharedRightPanel(SharedCollectionTab tab) {
        if (tab == null) return;

        // Wire the filter-change callbacks on this tab's own FilterPane.
        // onRightFilterChange → refresh the AllExistingCards display (right pane).
        // onLeftFilterChange  → refresh the middle-pane display (stub – implement per tab).
        if (sharedFilterPane == null) {
            sharedFilterPane = new FilterPane();
            sharedFilterPane.setOnRightFilterChange(() -> updateCardsDisplay());
            sharedFilterPane.setOnLeftFilterChange(() -> updateMiddlePaneDisplay());
            sharedFilterPane.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, evt -> {
                if (evt.getCode() == javafx.scene.input.KeyCode.ENTER) {
                    handleEnterAddFromRightPane();
                    evt.consume();
                }
            });
        }
        AnchorPane rh = tab.getRightHeaderPane();
        if (!rh.getChildren().contains(sharedFilterPane)) {
            rh.getChildren().clear();
            rh.getChildren().add(sharedFilterPane);
            AnchorPane.setTopAnchor(sharedFilterPane, 0.0);
            AnchorPane.setBottomAnchor(sharedFilterPane, 0.0);
            AnchorPane.setLeftAnchor(sharedFilterPane, 0.0);
            AnchorPane.setRightAnchor(sharedFilterPane, 0.0);
        }

        // Top bar: two button groups with tight inner spacing (4 px),
        // separated by a normal 10 px gap between the two groups.
        HBox viewModeGroup = new HBox(4, listMosaicButton, printedUniqueButton);
        viewModeGroup.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        HBox sortGroup = new HBox(4, sortAzButton, sortAtkButton, sortDefButton, sortLvlButton);
        sortGroup.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        HBox cardsTopBar = new HBox(10, viewModeGroup, sortGroup);
        cardsTopBar.setPadding(new Insets(5, 10, 5, 10));
        cardsTopBar.setStyle("-fx-background-color: #100317;");

        // Cards display → right content pane
        VBox.setVgrow(cardsDisplayContainer, Priority.ALWAYS);
        VBox rightContentVBox = new VBox(0, cardsTopBar, cardsDisplayContainer);
        rightContentVBox.setStyle("-fx-background-color: #100317;");

        AnchorPane rc = tab.getRightContentPane();
        rc.getChildren().clear();
        rc.getChildren().add(rightContentVBox);
        AnchorPane.setTopAnchor(rightContentVBox, 0.0);
        AnchorPane.setBottomAnchor(rightContentVBox, 0.0);
        AnchorPane.setLeftAnchor(rightContentVBox, 0.0);
        AnchorPane.setRightAnchor(rightContentVBox, 0.0);
    }

    /**
     * Intercepts the window's close (X) button.
     * Dirty state is read directly from the tab titles (a "*" prefix means unsaved).
     * Three outcomes:
     * Save       – persists all dirty data, then exits.
     * Don't Save – exits immediately.
     * Cancel     – aborts the close; the app keeps running.
     */
    private void handleWindowCloseRequest(WindowEvent event) {

        // Collect which tabs are dirty from their titles (already maintained elsewhere)
        List<String> dirtyTabs = new ArrayList<>();
        if (myCollectionTabHandle != null && myCollectionTabHandle.getText().startsWith("*"))
            dirtyTabs.add("My Collection");
        if (decksTabHandle != null && decksTabHandle.getText().startsWith("*"))
            dirtyTabs.add("Decks & Collections");
        if (ouicheListTabHandle != null && ouicheListTabHandle.getText().startsWith("*"))
            dirtyTabs.add("OuicheList");

        if (dirtyTabs.isEmpty()) {
            return; // nothing unsaved — let JavaFX close normally
        }

        // Prevent the window from closing while we ask the user
        event.consume();

        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("The following tabs have unsaved changes:");
        alert.setContentText(String.join("\n", dirtyTabs));

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType noSaveBtn = new ButtonType("Don't Save", ButtonBar.ButtonData.NO);
        ButtonType cancelBtn = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(saveBtn, noSaveBtn, cancelBtn);

        // Dark-theme styling
        DialogPane pane = alert.getDialogPane();
        pane.setStyle("-fx-background-color: #100317; -fx-border-color: #5a2a7a; -fx-border-width: 1;");
        pane.applyCss();
        pane.layout();
        pane.lookupAll(".header-panel").forEach(n -> n.setStyle("-fx-background-color: #100317;"));
        pane.lookupAll(".label").forEach(n -> n.setStyle("-fx-text-fill: white;"));
        pane.lookupAll(".button").forEach(n -> n.setStyle(
                "-fx-background-color: #2a0a3e; -fx-text-fill: white; "
                        + "-fx-border-color: #7a3aaa; -fx-border-width: 1; "
                        + "-fx-border-radius: 3; -fx-background-radius: 3; -fx-cursor: hand;"));

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isEmpty() || result.get() == cancelBtn) {
            return; // stay open
        }

        if (result.get() == saveBtn) {
            try {
                if (myCollectionTabHandle != null && myCollectionTabHandle.getText().startsWith("*"))
                    UserInterfaceFunctions.saveMyCollection();
                if (decksTabHandle != null && decksTabHandle.getText().startsWith("*"))
                    UserInterfaceFunctions.saveAllDecksAndCollections();
                if (ouicheListTabHandle != null && ouicheListTabHandle.getText().startsWith("*"))
                    UserInterfaceFunctions.saveOuicheList();
                updateTabDirtyIndicators();
            } catch (Exception ex) {
                logger.error("Save-on-close failed", ex);
                Alert errAlert = new Alert(Alert.AlertType.ERROR);
                errAlert.setTitle("Save Error");
                errAlert.setHeaderText("Could not save all changes.");
                errAlert.setContentText(ex.getMessage());
                errAlert.getDialogPane().setStyle("-fx-background-color: #100317;");
                errAlert.getDialogPane().applyCss();
                errAlert.getDialogPane().layout();
                errAlert.getDialogPane().lookupAll(".label")
                        .forEach(n -> n.setStyle("-fx-text-fill: white;"));
                errAlert.showAndWait();
                return; // keep app open so the user can retry
            }
        }

        // "Don't Save" or successful save → exit
        Platform.exit();
    }

    /**
     * Starts an inline rename on a freshly created Deck or ThemeCollection.
     * Confirm → updates the model name and refreshes the decks view.
     * Cancel → removes the element from the model and refreshes.
     */
    private void startDecksAddRename(NavigationItem navItem, Object modelObj) {
        navItem.startInlineRename(
                newName -> {
                    if (modelObj instanceof Deck) ((Deck) modelObj).setName(newName);
                    else if (modelObj instanceof ThemeCollection)
                        ((ThemeCollection) modelObj).setName(newName);
                    // The element now has a real name — mark it dirty
                    UserInterfaceFunctions.markDirty(modelObj);
                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    UserInterfaceFunctions.setPendingDecksFullRebuild();
                    UserInterfaceFunctions.refreshDecksAndCollectionsView();
                },
                () -> {
                    // Cancel: remove the freshly created element
                    try {
                        DecksAndCollectionsList dac = UserInterfaceFunctions.getDecksList();
                        if (dac != null) {
                            if (modelObj instanceof Deck) {
                                Deck d = (Deck) modelObj;
                                boolean removed = dac.getDecks() != null && dac.getDecks().remove(d);
                                if (!removed && dac.getCollections() != null) {
                                    outer:
                                    for (ThemeCollection tc : dac.getCollections()) {
                                        if (tc == null || tc.getLinkedDecks() == null) continue;
                                        for (List<Deck> unit : tc.getLinkedDecks()) {
                                            if (unit != null && unit.remove(d)) break outer;
                                        }
                                    }
                                }
                            } else if (modelObj instanceof ThemeCollection) {
                                if (dac.getCollections() != null)
                                    dac.getCollections().remove(modelObj);
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    // Element was never committed — clear its dirty flag
                    UserInterfaceFunctions.clearDirty(modelObj);
                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    UserInterfaceFunctions.refreshDecksAndCollectionsView();
                }
        );
    }

    /**
     * Expands all NavigationItem ancestors of the given item in the nav menu,
     * so the item itself becomes visible.
     */
    private void expandNavAncestors(NavigationItem item) {
        if (item == null) return;
        javafx.scene.Node parent = item.getParent();
        while (parent != null) {
            if (parent instanceof NavigationItem) {
                ((NavigationItem) parent).setExpanded(true);
            }
            parent = parent.getParent();
        }
    }

    /**
     * Scrolls the nav-menu ScrollPane inside {@code tab} so that {@code item}
     * is centred in the viewport.
     */
    private void scrollNavToItem(SharedCollectionTab tab, NavigationItem item) {
        if (tab == null || item == null) return;
        Platform.runLater(() -> {
            try {
                ScrollPane sp = tab.getMenuScrollPane();
                VBox content = tab.getMenuVBox();
                if (sp == null || content == null) return;

                // Walk up from item to content to accumulate Y offset
                double itemY = 0;
                javafx.scene.Node node = item;
                while (node != null && node != content) {
                    itemY += node.getBoundsInParent().getMinY();
                    node = node.getParent();
                }

                javafx.geometry.Bounds vb = sp.getViewportBounds();
                javafx.geometry.Bounds cb = content.getBoundsInLocal();
                if (vb == null || cb == null) return;
                double viewportH = vb.getHeight();
                double contentH = cb.getHeight();
                if (contentH <= viewportH) return;

                double itemH = item.getBoundsInLocal().getHeight();
                double targetY = itemY - (viewportH - itemH) / 2.0;
                targetY = Math.max(0, Math.min(targetY, contentH - viewportH));
                sp.setVvalue(targetY / (contentH - viewportH));
            } catch (Throwable ignored) {
            }
        });
    }

    /**
     * Scrolls the decks-and-collections TreeView so the node corresponding to
     * {@code handlerTarget} is visible after a tree rebuild.
     * Maps known aliases ("Exclusion List" → "Cards not to add").
     */
    private void scrollToTargetInDecksTree(String handlerTarget) {
        if (decksAndCollectionsTreeView == null || handlerTarget == null) return;

        String[] rawParts = handlerTarget.split("\\s*/\\s*");
        String[] parts = new String[rawParts.length];
        for (int i = 0; i < rawParts.length; i++) {
            String p = rawParts[i].trim();
            if (p.equalsIgnoreCase("Exclusion List")) p = "Cards not to add";
            parts[i] = p;
        }

        Platform.runLater(() -> {
            if (decksAndCollectionsTreeView == null) return;
            TreeItem<String> root = decksAndCollectionsTreeView.getRoot();
            if (root == null) return;

            // Try full path, then last-2, then last-1
            TreeItem<String> target = findTreeItemByPath(root, parts, 0);
            if (target == null && parts.length >= 2)
                target = findTreeItemByPath(root,
                        new String[]{parts[parts.length - 2], parts[parts.length - 1]}, 0);
            if (target == null)
                target = findTreeItemByPath(root, new String[]{parts[parts.length - 1]}, 0);
            if (target == null) return;

            // Expand ancestors so the node is reachable
            for (TreeItem<String> a = target.getParent(); a != null; a = a.getParent())
                a.setExpanded(true);

            final TreeItem<String> finalTarget = target;
            Platform.runLater(() -> {
                int row = decksAndCollectionsTreeView.getRow(finalTarget);
                if (row >= 0) decksAndCollectionsTreeView.scrollTo(row);
            });
        });
    }

    /**
     * After an "Add to..." action, scrolls the tree so the newly added card
     * (always the last item in its group's GridView) is visible.
     * If the target cell's bottom is already within the viewport, nothing happens.
     */
    private void scrollToNewCardInGroup(String handlerTarget) {
        if (myCollectionTreeView == null || handlerTarget == null) return;

        String[] parts = handlerTarget.split("\\s*/\\s*");

        Platform.runLater(() -> {
            if (myCollectionTreeView == null) return;
            TreeItem<String> root = myCollectionTreeView.getRoot();
            if (root == null) return;

            // --- Locate the target TreeItem ---
            TreeItem<String> target = findTreeItemByPath(root, parts, 0);
            if (target == null && parts.length > 1)
                target = findTreeItemByPath(root, new String[]{parts[parts.length - 1]}, 0);
            if (target == null)
                target = findTreeItemByPath(root, new String[]{parts[0]}, 0);
            if (target == null) return;

            // If a Box was found instead of a CardsGroup, descend to its first CardsGroup child
            // (happens when handlerTarget has only a box name, e.g. "BoxName").
            if (target instanceof DataTreeItem) {
                Object data = ((DataTreeItem<?>) target).getData();
                if (!(data instanceof CardsGroup)) {
                    for (TreeItem<String> child : target.getChildren()) {
                        if (child instanceof DataTreeItem
                                && ((DataTreeItem<?>) child).getData() instanceof CardsGroup) {
                            target = child;
                            break;
                        }
                    }
                }
            }

            // Expand all ancestors so the row has a valid index.
            for (TreeItem<String> a = target.getParent(); a != null; a = a.getParent())
                a.setExpanded(true);

            final int targetRow = myCollectionTreeView.getRow(target);
            if (targetRow < 0) return;

            // --- Check visibility via VirtualFlow ---
            javafx.scene.control.skin.VirtualFlow<?> vf = getVirtualFlow();
            boolean rowInView = false;
            if (vf != null) {
                int first = vf.getFirstVisibleCell() != null
                        ? vf.getFirstVisibleCell().getIndex() : -1;
                int last = vf.getLastVisibleCell() != null
                        ? vf.getLastVisibleCell().getIndex() : -1;
                rowInView = first >= 0 && targetRow >= first && targetRow <= last;
            }

            // If the row is completely outside the viewport, scroll its top into view first
            // so the cell gets rendered and can be measured in the next pass.
            if (!rowInView) {
                myCollectionTreeView.scrollTo(targetRow);
            }

            // Deferred second pass: now that the cell is rendered (or was already visible),
            // scroll further if needed so the BOTTOM of the cell (= last/new card) is visible.
            Platform.runLater(() -> adjustScrollToShowCellBottom(targetRow));
        });
    }

    public void refreshFromModel() {
        // Ensure we run on FX thread
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refreshFromModel);
            return;
        }

        Logger logger = LoggerFactory.getLogger(RealMainController.class);
        try {
            // 1) Try to call an explicit refresh method if you already have one (common names)
            String[] candidateMethodNames = {
                    "refresh", "refreshView", "reload", "reloadModel", "updateView", "rebuildTree"
            };

            java.util.Set<String> invoked = new java.util.HashSet<>();
            for (String mName : candidateMethodNames) {
                if (mName == null || mName.trim().isEmpty()) continue;
                try {
                    Method m = null;
                    try {
                        m = this.getClass().getMethod(mName);
                    } catch (NoSuchMethodException ignored) {
                        // method not present, continue
                    }
                    if (m == null) continue;

                    // Defensive checks: only call no-arg methods, skip if it's this method
                    if (m.getParameterCount() != 0) continue;
                    if (m.getName().equals("refreshFromModel") && m.getDeclaringClass() == this.getClass()) continue;

                    // Avoid invoking the same method twice
                    if (invoked.contains(m.getName())) continue;

                    try {
                        m.invoke(this);
                        invoked.add(m.getName());
                        logger.debug("refreshFromModel: invoked controller method {}", mName);
                        return; // assume that method handled refresh
                    } catch (Throwable t) {
                        logger.debug("refreshFromModel: invoking {} failed", mName, t);
                    }
                } catch (Throwable t) {
                    logger.debug("refreshFromModel: reflection check for {} failed", mName, t);
                }
            }

            // 2) Try to refresh TreeView/ListView fields declared on this controller
            boolean refreshed = false;
            Field[] fields = this.getClass().getDeclaredFields();
            for (Field f : fields) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(this);
                    if (val == null) continue;

                    if (val instanceof TreeView) {
                        try {
                            ((TreeView<?>) val).refresh();
                            refreshed = true;
                            logger.debug("refreshFromModel: refreshed TreeView field '{}'", f.getName());
                        } catch (Throwable t) {
                            logger.debug("refreshFromModel: TreeView.refresh() failed on field {}", f.getName(), t);
                        }
                    } else if (val instanceof ListView) {
                        try {
                            ((ListView<?>) val).refresh();
                            refreshed = true;
                            logger.debug("refreshFromModel: refreshed ListView field '{}'", f.getName());
                        } catch (Throwable t) {
                            logger.debug("refreshFromModel: ListView.refresh() failed on field {}", f.getName(), t);
                        }
                    } else if (val instanceof Parent) {
                        // traverse the scene graph rooted at this Parent to find TreeView/ListView
                        Parent root = (Parent) val;
                        Deque<Node> dq = new ArrayDeque<>();
                        dq.add(root);
                        while (!dq.isEmpty()) {
                            Node n = dq.poll();
                            if (n == null) continue;
                            if (n instanceof TreeView) {
                                try {
                                    ((TreeView<?>) n).refresh();
                                    refreshed = true;
                                } catch (Throwable t) {
                                    logger.debug("refreshFromModel: TreeView.refresh() failed during traversal", t);
                                }
                            } else if (n instanceof ListView) {
                                try {
                                    ((ListView<?>) n).refresh();
                                    refreshed = true;
                                } catch (Throwable t) {
                                    logger.debug("refreshFromModel: ListView.refresh() failed during traversal", t);
                                }
                            }
                            if (n instanceof Parent) {
                                try {
                                    for (Node child : ((Parent) n).getChildrenUnmodifiable()) dq.add(child);
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                        if (refreshed)
                            logger.debug("refreshFromModel: refreshed controls found under Parent field '{}'", f.getName());
                    }
                } catch (Throwable t) {
                    logger.debug("refreshFromModel: inspecting field {} failed", f.getName(), t);
                }
            }

            // 3) If nothing refreshed, try a last-resort re-acquire of the model so bindings update
            if (!refreshed) {
                try {
                    // Re-acquire the shared OwnedCardsCollection so any bound controls can pick up changes.
                    // This does not save anything.
                    Model.CardsLists.OwnedCardsCollection owned = null;
                    try {
                        owned = Model.CardsLists.OuicheList.getMyCardsCollection();
                    } catch (Throwable ignored) {
                    }
                    if (owned == null) {
                        try {
                            Controller.UserInterfaceFunctions.loadCollectionFile();
                        } catch (Throwable ignored) {
                        }
                        try {
                            owned = Model.CardsLists.OuicheList.getMyCardsCollection();
                        } catch (Throwable ignored) {
                        }
                    }
                    logger.debug("refreshFromModel: re-acquired owned collection = {}", owned != null);
                } catch (Throwable t) {
                    logger.debug("refreshFromModel: model re-acquire failed", t);
                }
            }

            if (!refreshed) {
                logger.info("refreshFromModel: no controls refreshed automatically. " +
                        "If you have a specific view control, either implement a dedicated refresh method or register an explicit refresher.");
            }
        } catch (Throwable ex) {
            logger.debug("refreshFromModel failed", ex);
        }
    }

    private void displayMyCollection() throws Exception {
        AnchorPane contentPane = myCollectionTab.getContentPane();
        contentPane.getChildren().clear();

        //OwnedCardsCollection collection = new OwnedCardsCollection(UserInterfaceFunctions.filePath.getAbsolutePath());
        OwnedCardsCollection collection = null;
        try {
            collection = Model.CardsLists.OuicheList.getMyCardsCollection();
        } catch (Throwable ignored) {
        }
        if (collection == null) {
            try {
                Controller.UserInterfaceFunctions.loadCollectionFile();
            } catch (Throwable ignored) {
            }
            try {
                collection = Model.CardsLists.OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
        }
        if (collection == null || collection.getOwnedCollection() == null) {
            logger.warn("OwnedCardsCollection is not available.");
        }

        DataTreeItem<Object> rootItem = new DataTreeItem<>("My Cards Collection", "ROOT");
        rootItem.setExpanded(true);
        for (Box box : collection.getOwnedCollection()) {
            DataTreeItem<Object> boxItem = createBoxTreeItem(box);
            rootItem.getChildren().add(boxItem);
        }

        myCollectionTreeView = new TreeView<>(rootItem);
        myCollectionTreeView.setUserData("MY_COLLECTION");
        myCollectionTreeView.setCellFactory(param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        myCollectionTreeView.setStyle("-fx-background-color: #100317;");
        myCollectionTreeView.setShowRoot(false);
        myCollectionTreeView.addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                buildMiddlePaneEmptySpaceFilter());
        contentPane.getChildren().add(myCollectionTreeView);
        AnchorPane.setTopAnchor(myCollectionTreeView, 0.0);
        AnchorPane.setBottomAnchor(myCollectionTreeView, 0.0);
        AnchorPane.setLeftAnchor(myCollectionTreeView, 0.0);
        AnchorPane.setRightAnchor(myCollectionTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        myCollectionTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());
        logger.info("My Collection displayed.");
    }

    /**
     * Starts an inline rename for a ThemeCollection that was just created from a
     * standalone Deck ("Create Collection" action).
     * <p>
     * Confirm → renames the collection and refreshes.
     * Cancel  → removes the collection and moves the deck back to the standalone
     * list, then refreshes.
     */
    private void startDecksCreateCollectionRename(NavigationItem navItem,
                                                  ThemeCollection newColl,
                                                  Deck movedDeck) {
        navItem.startInlineRename(
                newName -> {
                    newColl.setName(newName);
                    // Both the collection and the deck it now contains are dirty
                    UserInterfaceFunctions.markDirty(newColl);
                    UserInterfaceFunctions.markDirty(movedDeck);
                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    // Force a full structural rebuild so the new name appears in the tree,
                    // and queue the collection to be expanded in the nav after the rebuild.
                    UserInterfaceFunctions.setPendingDecksFullRebuild();
                    UserInterfaceFunctions.setPendingDecksExpandTarget(newColl);
                    UserInterfaceFunctions.refreshDecksAndCollectionsView();
                },
                () -> {
                    // Cancel: undo — remove the collection, restore the deck to standalone
                    try {
                        DecksAndCollectionsList dac = UserInterfaceFunctions.getDecksList();
                        if (dac != null) {
                            if (dac.getCollections() != null)
                                dac.getCollections().remove(newColl);
                            if (dac.getDecks() == null)
                                dac.setDecks(new java.util.ArrayList<>());
                            dac.getDecks().add(movedDeck);
                        }
                    } catch (Throwable ignored) {
                    }
                    // The collection was never saved — remove it from dirty tracking
                    UserInterfaceFunctions.clearDirty(newColl);
                    // The deck is back standalone; clear its "dirty from the failed create" flag too
                    // It may legitimately be dirty from previous edits, but those would have been
                    // marked before this action — clearDirty here only removes the flag we added.
                    UserInterfaceFunctions.clearDirty(movedDeck);
                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    UserInterfaceFunctions.refreshDecksAndCollectionsView();
                }
        );
    }

    /**
     * Searches the live menuVBox for the NavigationItem whose userData == target.
     * This must be called AFTER all nav rebuilds have completed.
     */
    private NavigationItem findNavItemInMenuVBox(VBox menuVBox, Object target) {
        if (menuVBox == null || target == null) return null;
        for (javafx.scene.Node node : menuVBox.getChildren()) {
            NavigationItem found = findNavItemInNode(node, target);
            if (found != null) return found;
        }
        logger.debug("findNavItemInMenuVBox: no item found for target={}", target);
        return null;
    }

    private NavigationItem findNavItemInNode(javafx.scene.Node node, Object target) {
        if (node instanceof NavigationMenu) {
            for (NavigationItem item : ((NavigationMenu) node).getItems()) {
                NavigationItem found = findNavItemByUserDataInItem(item, target);
                if (found != null) return found;
            }
        } else if (node instanceof NavigationItem) {
            return findNavItemByUserDataInItem((NavigationItem) node, target);
        }
        return null;
    }

    private NavigationItem findNavItemByUserDataInItem(NavigationItem item, Object target) {
        if (item == null) return null;
        logger.debug("findNavItemByUserDataInItem: checking '{}' userData={}",
                item.getLabel() != null ? item.getLabel().getText() : "?",
                item.getUserData());
        if (item.getUserData() == target) return item;
        for (NavigationItem sub : item.getSubItems()) {
            NavigationItem found = findNavItemByUserDataInItem(sub, target);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Populate the My Collection navigation menu.
     * <p>
     * IMPORTANT: This method wires click handlers so that clicking a navigation label
     * selects and scrolls the corresponding node in the My Collection TreeView.
     * <p>
     * The path matching used by navigateToTree(...) mirrors the tree structure created by
     * createBoxTreeItem/createGroupTreeItem so matching succeeds reliably.
     */
    /**
     * Determines the {@link View.NavigationItem.DropPosition} from the cursor's Y
     * coordinate relative to {@code navItem}.
     *
     * <p>Two zone profiles:
     * <ul>
     *   <li><b>Container target</b> (Box) – INTO dominates: top/bottom 15 % → BEFORE/AFTER,
     *       middle 70 % → INTO.  Nesting is the primary operation for containers.</li>
     *   <li><b>Non-container target</b> (Deck, CardsGroup) – reorder dominates:
     *       top/bottom 30 % → BEFORE/AFTER, middle 40 % → INTO.</li>
     * </ul>
     *
     * @param isContainerTarget true when the drop target can contain children (i.e. is a Box)
     */
    private static View.NavigationItem.DropPosition resolveDropPosition(
            View.NavigationItem navItem, double y, boolean isContainerTarget) {
        double rowH = 30; // sensible default
        javafx.scene.layout.HBox row = navItem.getRowHBox();
        if (row != null) {
            double h = row.getBoundsInParent().getHeight();
            if (h > 0) rowH = h;
        }
        double beforeThreshold = isContainerTarget ? rowH * 0.15 : rowH * 0.30;
        double afterThreshold = isContainerTarget ? rowH * 0.85 : rowH * 0.70;
        if (y < beforeThreshold) return View.NavigationItem.DropPosition.BEFORE;
        if (y > afterThreshold) return View.NavigationItem.DropPosition.AFTER;
        return View.NavigationItem.DropPosition.INTO;
    }

    /**
     * Handles a NAV drag-drop onto a My Collection navigation item.
     *
     * <p>Rules:
     * <ul>
     *   <li>Box → Box (BEFORE/AFTER): reorder at the same hierarchy level.</li>
     *   <li>Box → Box (INTO): nest the dragged box as a sub-box of the target.</li>
     *   <li>Box → background (null target): promote dragged box to the top-level list.</li>
     *   <li>CardsGroup → CardsGroup (BEFORE/AFTER): reorder; may cross box boundaries.</li>
     *   <li>CardsGroup → Box (INTO / AFTER): move category into that box.</li>
     *   <li>CardsGroup → background: no-op (per spec).</li>
     * </ul>
     *
     * @param dragged    the model object being dragged
     * @param target     the model object of the nav item being dropped onto (never null here)
     * @param pos        resolved drop position
     * @param collection the live OwnedCardsCollection
     */
    private void handleMyCollectionNavDrop(Object dragged, Object target,
                                           View.NavigationItem.DropPosition pos,
                                           Model.CardsLists.OwnedCardsCollection collection) {
        if (dragged == null || target == null || dragged == target) return;

        boolean changed = false;

        if (dragged instanceof Model.CardsLists.Box draggedBox) {
            if (target instanceof Model.CardsLists.Box targetBox) {
                switch (pos) {
                    case BEFORE ->
                            changed = Controller.NavigationDragDrop.moveBoxBefore(draggedBox, targetBox, collection);
                    case AFTER ->
                            changed = Controller.NavigationDragDrop.moveBoxAfter(draggedBox, targetBox, collection);
                    case INTO -> changed = Controller.NavigationDragDrop.nestBoxInto(draggedBox, targetBox, collection);
                }
            } else if (target instanceof Model.CardsLists.CardsGroup targetGroup) {
                // Dropping a Box onto a category: nest the box into the category's parent box.
                Model.CardsLists.Box parentBox =
                        Controller.NavigationDragDrop.findCategoryParent(targetGroup, collection);
                if (parentBox != null) {
                    changed = Controller.NavigationDragDrop.nestBoxInto(draggedBox, parentBox, collection);
                }
            }

        } else if (dragged instanceof Model.CardsLists.CardsGroup draggedGroup) {
            if (target instanceof Model.CardsLists.CardsGroup targetGroup) {
                switch (pos) {
                    case BEFORE ->
                            changed = Controller.NavigationDragDrop.moveCategoryBefore(draggedGroup, targetGroup, collection);
                    case AFTER, INTO ->
                            changed = Controller.NavigationDragDrop.moveCategoryAfter(draggedGroup, targetGroup, collection);
                }
            } else if (target instanceof Model.CardsLists.Box targetBox) {
                // INTO or AFTER on a Box → move to end of that box
                changed = Controller.NavigationDragDrop.moveCategoryIntoBox(draggedGroup, targetBox, collection);
            }
            // CardsGroup dropped on background → no-op (handled at menuVBox level, never reaches here)
        }

        if (changed) {
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
        }
    }

    /**
     * Adds and wires a footer drop-zone to {@code boxItem}.
     *
     * <p>The footer is an 8 px strip below all of the box's sub-items.  It splits
     * 50/50 vertically:
     * <ul>
     *   <li>Top half → INTO: append the dragged object inside {@code box}.</li>
     *   <li>Bottom half → AFTER: place the dragged object after {@code box} at
     *       the same hierarchy level.</li>
     * </ul>
     *
     * <p>A Box dragged onto the top half is nested into {@code box}.
     * A CardsGroup dragged onto the top half is moved into {@code box}.
     * Dropping onto the bottom half calls {@link Controller.NavigationDragDrop#moveBoxAfter}
     * or {@link Controller.NavigationDragDrop#moveCategoryAfter} accordingly.
     * Bottom-half drops of a CardsGroup use {@link #handleMyCollectionNavDrop} with
     * AFTER so the normal category-after logic applies.
     */
    private void enableBoxFooterDropZone(NavigationItem boxItem, Model.CardsLists.Box box,
                                         Model.CardsLists.OwnedCardsCollection collection) {
        HBox footer = boxItem.addFooterZone();

        footer.setOnDragOver(event -> {
            if ("NAV".equals(Controller.DragDropManager.getDragSourcePane())
                    && event.getDragboard().hasString()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
                boolean into = event.getY() < footer.getHeight() / 2.0;
                if (into) {
                    footer.setStyle(
                            "-fx-background-color: transparent;" +
                                    "-fx-border-color: #cdfc04 transparent transparent transparent;" +
                                    "-fx-border-width: 2 0 2 0;");
                } else {
                    footer.setStyle(
                            "-fx-background-color: transparent;" +
                                    "-fx-border-color: transparent transparent #cdfc04 transparent;" +
                                    "-fx-border-width: 2 0 2 0;");
                }
            }
            event.consume();
        });

        footer.setOnDragExited(event -> {
            footer.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 2 0 2 0;");
            event.consume();
        });

        footer.setOnDragDropped(event -> {
            Object dragged = Controller.DragDropManager.getDraggedNavObject();
            boolean into = event.getY() < footer.getHeight() / 2.0;
            boolean changed = false;

            if (dragged instanceof Model.CardsLists.Box draggedBox && dragged != box) {
                if (into) {
                    changed = Controller.NavigationDragDrop.nestBoxInto(draggedBox, box, collection);
                } else {
                    changed = Controller.NavigationDragDrop.moveBoxAfter(draggedBox, box, collection);
                }
            } else if (dragged instanceof Model.CardsLists.CardsGroup draggedGroup) {
                if (into) {
                    changed = Controller.NavigationDragDrop.moveCategoryIntoBox(draggedGroup, box, collection);
                } else {
                    // AFTER: find the last category in this box and insert after it,
                    // or fall back to moving into box when there are no categories.
                    java.util.List<Model.CardsLists.CardsGroup> content = box.getContent();
                    if (content != null && !content.isEmpty()) {
                        Model.CardsLists.CardsGroup last = content.get(content.size() - 1);
                        if (last != draggedGroup) {
                            changed = Controller.NavigationDragDrop.moveCategoryAfter(draggedGroup, last, collection);
                        }
                    } else {
                        changed = Controller.NavigationDragDrop.moveCategoryIntoBox(draggedGroup, box, collection);
                    }
                }
            }

            footer.setStyle("-fx-background-color: transparent; -fx-border-color: transparent; -fx-border-width: 2 0 2 0;");
            if (changed) {
                Controller.UserInterfaceFunctions.markMyCollectionDirty();
                Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                Controller.UserInterfaceFunctions.refreshOwnedCollectionStructure();
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }

    /**
     * Starts an inline rename on a freshly created Box or CardsGroup.
     * Confirm → updates the model name and does a full structure refresh.
     * Cancel → removes the element from the model and does a full structure refresh.
     */
    private void startAddRename(NavigationItem navItem, Object modelObj) {
        navItem.startInlineRename(
                newName -> {
                    if (modelObj instanceof Box) ((Box) modelObj).setName(newName);
                    else if (modelObj instanceof CardsGroup) ((CardsGroup) modelObj).setName(newName);
                    // Mark dirty — the new element now has a real name, so it needs saving
                    UserInterfaceFunctions.markMyCollectionDirty();
                    UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
                    UserInterfaceFunctions.refreshOwnedCollectionStructure();
                },
                () -> {
                    // Cancel: remove the freshly created element
                    try {
                        OwnedCardsCollection owned = OuicheList.getMyCardsCollection();
                        if (owned != null) {
                            if (modelObj instanceof Box) {
                                Box b = (Box) modelObj;
                                if (!owned.getOwnedCollection().remove(b)) {
                                    for (Box top : owned.getOwnedCollection()) {
                                        if (top.getSubBoxes() != null && top.getSubBoxes().remove(b)) break;
                                    }
                                }
                            } else if (modelObj instanceof CardsGroup) {
                                CardsGroup cat = (CardsGroup) modelObj;
                                outer:
                                for (Box top : owned.getOwnedCollection()) {
                                    if (top.getContent() != null && top.getContent().remove(cat)) break;
                                    if (top.getSubBoxes() != null) {
                                        for (Box sub : top.getSubBoxes()) {
                                            if (sub.getContent() != null && sub.getContent().remove(cat))
                                                break outer;
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    // Nothing new was actually persisted — no dirty change needed
                    UserInterfaceFunctions.refreshOwnedCollectionStructure();
                }
        );
    }

    /**
     * Returns true if the given CardsGroup contains at least one card that needs sorting.
     *
     * @param group            The CardsGroup to inspect.
     * @param groupDisplayName The sanitized display name used in the navigation (no = or -).
     * @return true if at least one card in the group needs sorting.
     */
    private boolean groupHasUnsortedCards(CardsGroup group, String groupDisplayName) {
        if (group == null || group.getCardList() == null) return false;

        for (CardElement ce : group.getCardList()) {
            if (ce == null || ce.getCard() == null) continue;
            try {
                // Standard check first
                boolean needs = CardQualityService.computeCardNeedsSorting(ce.getCard(), groupDisplayName);
                if (needs) return true;
                // If not genuinely needed, check for quality upgrade
                boolean upgrade = CardQualityService.computeCardNeedsSortingWithUpgrade(ce, groupDisplayName);
                if (upgrade) return true;
            } catch (Throwable t) {
                // If compute method fails, ignore this card (do not mark as unsorted)
            }
        }
        return false;
    }


// --- Decks & Collections display (left unchanged except for keeping decksTreeView field) ---

    /**
     * Returns true when {@code group} has a card whose {@link Card#getPrintCode()}
     * is null or blank. These cards receive the highest-priority red glow.
     */
    private boolean groupHasMissingPrintCode(CardsGroup group) {
        if (group == null || group.getCardList() == null) return false;
        for (CardElement ce : group.getCardList()) {
            if (ce == null || ce.getCard() == null) continue;
            Card c = ce.getCard();
            if (c.getPrintCode() == null || c.getPrintCode().isBlank()) return true;
        }
        return false;
    }

    /**
     * Returns true when {@code group} has a card that has a printCode but is
     * missing {@link CardElement#getCondition()} or {@link CardElement#getRarity()}.
     * These cards receive the orange glow (lower priority than the red one).
     */
    private boolean groupHasIncompleteCards(CardsGroup group) {
        if (group == null || group.getCardList() == null) return false;
        for (CardElement ce : group.getCardList()) {
            if (ce == null || ce.getCard() == null) continue;
            Card c = ce.getCard();
            // Cards without a printCode are handled by the red glow; skip here.
            if (c.getPrintCode() == null || c.getPrintCode().isBlank()) continue;
            if (ce.getCondition() == null || ce.getRarity() == null) return true;
        }
        return false;
    }

    /**
     * Returns true when {@code box} (or any nested sub-box / group) contains
     * at least one card with a missing printCode.
     */
    private boolean boxHasMissingPrintCode(Box box) {
        if (box == null) return false;
        if (box.getContent() != null) {
            for (CardsGroup g : box.getContent()) {
                if (groupHasMissingPrintCode(g)) return true;
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box sb : box.getSubBoxes()) {
                if (sb != null && boxHasMissingPrintCode(sb)) return true;
            }
        }
        return false;
    }

    /**
     * Returns true when {@code box} (or any nested sub-box / group) contains
     * at least one card that has a printCode but is missing condition or rarity.
     */
    private boolean boxHasIncompleteCards(Box box) {
        if (box == null) return false;
        if (box.getContent() != null) {
            for (CardsGroup g : box.getContent()) {
                if (groupHasIncompleteCards(g)) return true;
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box sb : box.getSubBoxes()) {
                if (sb != null && boxHasIncompleteCards(sb)) return true;
            }
        }
        return false;
    }

    private boolean groupHasUnsortedCards(CardsGroup group) {
        if (group == null) return false;

        List<CardElement> list;
        try {
            list = group.getCardList();
        } catch (Exception e) {
            return false;
        }
        if (list == null || list.isEmpty()) return false;

        String groupName = group.getName() == null ? "" : group.getName();

        for (int i = 0; i < list.size(); i++) {
            CardElement ce = list.get(i);
            if (ce == null) continue;
            Model.CardsLists.Card c = ce.getCard();
            if (c == null) continue;

            // Use the container = group and index = i for caching, pass group name
            if (cardNeedsSorting(c, group, i, groupName)) return true;
        }
        return false;
    }

    private boolean boxHasUnsortedCards(Box box) {
        if (box == null) return false;

        String boxName = box.getName() == null ? "" : box.getName();

        // 1) Defensive check: the Box itself might directly expose a list of cards.
        try {
            // Try method getCardList()
            Method getCardListMethod = null;
            try {
                getCardListMethod = box.getClass().getMethod("getCardList");
            } catch (NoSuchMethodException ignored) {
                // not present, we'll try field next
            }

            if (getCardListMethod != null) {
                Object maybeList = getCardListMethod.invoke(box);
                if (maybeList instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<?> raw = (List<?>) maybeList;
                    if (containsUnsortedFromRawList(raw, boxName)) return true;
                }
            } else {
                // Try to find a field named "cardList" or "cardsList"
                Field cardListField = null;
                try {
                    cardListField = box.getClass().getDeclaredField("cardList");
                } catch (NoSuchFieldException e1) {
                    try {
                        cardListField = box.getClass().getDeclaredField("cardsList");
                    } catch (NoSuchFieldException ignored) {
                        cardListField = null;
                    }
                }
                if (cardListField != null) {
                    cardListField.setAccessible(true);
                    Object maybeList = cardListField.get(box);
                    if (maybeList instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<?> raw = (List<?>) maybeList;
                        if (containsUnsortedFromRawList(raw, boxName)) return true;
                    }
                }
            }
        } catch (Exception e) {
            // Reflection may fail for many reasons; log at debug and continue with normal checks.
            logger.debug("boxHasUnsortedCards: reflection check failed for box '{}': {}", boxName, e.toString());
        }

        // 2) Check groups inside the box (normal case)
        if (box.getContent() != null) {
            for (CardsGroup g : box.getContent()) {
                if (groupHasUnsortedCards(g)) return true;
            }
        }

        // 3) Recurse into sub-boxes
        if (box.getSubBoxes() != null) {
            for (Box sb : box.getSubBoxes()) {
                if (sb == null) continue;
                if (boxHasUnsortedCards(sb)) return true;
            }
        }

        return false;
    }

// --- OuicheList helpers (navigation wiring preserved) ---

    /**
     * Inspect a raw list (List<?>) for unsorted cards.
     *
     * @param raw         the raw list instance (used as container key)
     * @param elementName the name of the element (Box name) that owns this list
     * @return true if any card at any position needs sorting
     */
    private boolean containsUnsortedFromRawList(List<?> raw, String elementName) {
        if (raw == null || raw.isEmpty()) return false;
        // Use the raw list object itself as the container key so positions map to this list instance
        Object containerKey = raw;
        for (int i = 0; i < raw.size(); i++) {
            Object o = raw.get(i);
            if (o == null) continue;
            Model.CardsLists.Card card = null;
            if (o instanceof Model.CardsLists.CardElement) {
                card = ((Model.CardsLists.CardElement) o).getCard();
            } else if (o instanceof Model.CardsLists.Card) {
                card = (Model.CardsLists.Card) o;
            } else {
                // Unknown element type: skip
                continue;
            }
            if (card == null) continue;

            if (cardNeedsSorting(card, containerKey, i, elementName)) return true;
        }
        return false;
    }

    private void displayOuicheListUnified() throws Exception {
        AnchorPane contentPane = ouicheListTab.getContentPane();
        contentPane.getChildren().clear();

        if (Model.CardsLists.OuicheList.getDetailedOuicheList() == null) {
            UserInterfaceFunctions.generateOuicheList();
        }
        DecksAndCollectionsList ouicheDetailed = Model.CardsLists.OuicheList.getDetailedOuicheList();
        if (ouicheDetailed == null) {
            throw new Exception("Failed to generate detailed OuicheList.");
        }

        DataTreeItem<Object> rootItem = new DataTreeItem<>("OuicheList", "ROOT");
        rootItem.setExpanded(true);

        if (ouicheDetailed.getCollections() != null) {
            for (ThemeCollection collection : ouicheDetailed.getCollections()) {
                DataTreeItem<Object> collItem = createThemeCollectionTreeItem(collection, TabType.OUICHE_LIST);
                rootItem.getChildren().add(collItem);
            }
        }

        if (ouicheDetailed.getDecks() != null) {
            for (Deck deck : ouicheDetailed.getDecks()) {
                DataTreeItem<Object> deckItem = createDeckTreeItem(deck);
                rootItem.getChildren().add(deckItem);
            }
        }

        ouicheTreeView = new TreeView<>(rootItem);
        ouicheTreeView.setCellFactory(param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        ouicheTreeView.setStyle("-fx-background-color: #100317;");
        ouicheTreeView.setShowRoot(false);
        ouicheTreeView.addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                buildMiddlePaneEmptySpaceFilter());
        contentPane.getChildren().add(ouicheTreeView);
        AnchorPane.setTopAnchor(ouicheTreeView, 0.0);
        AnchorPane.setBottomAnchor(ouicheTreeView, 0.0);
        AnchorPane.setLeftAnchor(ouicheTreeView, 0.0);
        AnchorPane.setRightAnchor(ouicheTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        ouicheTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());
    }

// --- Archetypes tab: display and menu population (unchanged) ---

    private void populateMyCollectionMenu() throws Exception {
        VBox menuVBox = myCollectionTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        OwnedCardsCollection collection = null;
        try {
            collection = Model.CardsLists.OuicheList.getMyCardsCollection();
        } catch (Throwable ignored) {
        }
        if (collection == null) {
            try {
                Controller.UserInterfaceFunctions.loadCollectionFile();
            } catch (Throwable ignored) {
            }
            try {
                collection = Model.CardsLists.OuicheList.getMyCardsCollection();
            } catch (Throwable ignored) {
            }
        }
        if (collection == null || collection.getOwnedCollection() == null) {
            logger.warn("OwnedCardsCollection is not available.");
        }

        if (collection != null && collection.getOwnedCollection() != null) {
            final Model.CardsLists.OwnedCardsCollection finalCollection = collection;
            for (Box box : collection.getOwnedCollection()) {
                String rawBoxName = box.getName() == null ? "" : box.getName();
                String boxName = Model.CardsLists.OwnedCardsCollection.extractName(rawBoxName, '=');
                NavigationItem boxItem = createNavigationItem(boxName, 0);
                boxItem.setUserData(box);
                boxItem.setItemType(NavigationItem.ItemType.BOX);
                attachNavItemDropHandlers(boxItem, box);
                enableNavDragSource(boxItem, box);
                enableNavItemAsNavDndTarget(boxItem, box,
                        (dragged, pos) -> handleMyCollectionNavDrop(dragged, box, pos, finalCollection));
                boxItem.setOnLabelClicked(evt -> {
                    Controller.SelectionManager.setLastClickedNavigationItem(box);
                    navigateToTree(myCollectionTreeView, boxName);
                });

                // --- highlight logic ---
                // Incomplete-card checks (red > orange) take precedence over the
                // unsorted-card check; all three share the same highlight colour.
                boolean boxHasUnsorted;
                String boxInitMsg;
                if (View.CardTreeCell.isIncompleteMarkingEnabled() && boxHasMissingPrintCode(box)) {
                    boxHasUnsorted = true;
                    boxInitMsg = "This box contains cards without a print code.";
                } else if (View.CardTreeCell.isIncompleteMarkingEnabled() && boxHasIncompleteCards(box)) {
                    boxHasUnsorted = true;
                    boxInitMsg = "This box contains cards with no condition or rarity set.";
                } else {
                    boxHasUnsorted = boxHasUnsortedCards(box, boxName);
                    boxInitMsg = "This box contains unsorted cards.";
                }
                applyNavigationItemHighlight(boxItem, boxHasUnsorted, boxInitMsg);

                // --- NEW: context menu for Box items ---
                {
                    ContextMenu boxCm = NavigationContextMenuBuilder.forMyCollectionBox(box, collection);
                    boxItem.setOnContextMenuRequested(e -> {
                        boxCm.show(boxItem, e.getScreenX(), e.getScreenY());
                        e.consume(); // prevent event from reaching the menuVBox background handler
                    });
                }

                // Add groups (Cards groups) under the box
                if (box.getContent() != null) {
                    for (CardsGroup group : box.getContent()) {
                        String rawGroupName = group.getName() == null ? "" : group.getName();
                        String groupName = Model.CardsLists.OwnedCardsCollection.extractName(rawGroupName, '-');
                        if (groupName.isEmpty()) continue; // skip unnamed groups (boxes with cards directly)
                        NavigationItem groupItem = createNavigationItem(groupName, 1);
                        groupItem.setUserData(group);
                        groupItem.setItemType(NavigationItem.ItemType.CATEGORY);
                        attachNavItemDropHandlers(groupItem, group);
                        enableNavDragSource(groupItem, group);
                        enableNavItemAsNavDndTarget(groupItem, group,
                                (dragged, pos) -> handleMyCollectionNavDrop(dragged, group, pos, finalCollection));
                        groupItem.setOnLabelClicked(evt -> {
                            Controller.SelectionManager.setLastClickedNavigationItem(group);
                            navigateToTree(myCollectionTreeView, boxName, groupName);
                        });

                        // highlight
                        boolean groupHasUnsorted;
                        String groupMsg;
                        if (View.CardTreeCell.isIncompleteMarkingEnabled() && groupHasMissingPrintCode(group)) {
                            groupHasUnsorted = true;
                            groupMsg = "This category contains cards without a print code.";
                        } else if (View.CardTreeCell.isIncompleteMarkingEnabled() && groupHasIncompleteCards(group)) {
                            groupHasUnsorted = true;
                            groupMsg = "This category contains cards with no condition or rarity set.";
                        } else {
                            groupHasUnsorted = groupHasUnsortedCards(group, groupName);
                            groupMsg = "This category contains unsorted cards.";
                        }
                        applyNavigationItemHighlight(groupItem, groupHasUnsorted, groupMsg);
                        if (groupHasUnsorted && !boxHasUnsorted) {
                            applyNavigationItemHighlight(boxItem, true, groupMsg);
                            boxHasUnsorted = true;
                        }

                        // --- NEW: context menu for Category items ---
                        {
                            ContextMenu groupCm = NavigationContextMenuBuilder.forMyCollectionCategory(group, box, collection);
                            groupItem.setOnContextMenuRequested(e -> {
                                groupCm.show(groupItem, e.getScreenX(), e.getScreenY());
                                e.consume();
                            });
                        }

                        boxItem.addSubItem(groupItem);
                    }
                }

                // Add sub-boxes and their groups (unchanged logic; add context menus to sub-items too)
                if (box.getSubBoxes() != null) {
                    for (Box subBox : box.getSubBoxes()) {
                        String rawSubBoxName = subBox.getName() == null ? "" : subBox.getName();
                        String subBoxName = Model.CardsLists.OwnedCardsCollection.extractName(rawSubBoxName, '=');
                        NavigationItem subBoxItem = createNavigationItem(subBoxName, 1);
                        subBoxItem.setUserData(subBox);
                        subBoxItem.setItemType(NavigationItem.ItemType.BOX);
                        attachNavItemDropHandlers(subBoxItem, subBox);
                        enableNavDragSource(subBoxItem, subBox);
                        enableNavItemAsNavDndTarget(subBoxItem, subBox,
                                (dragged, pos) -> handleMyCollectionNavDrop(dragged, subBox, pos, finalCollection));
                        subBoxItem.setOnLabelClicked(evt -> {
                            Controller.SelectionManager.setLastClickedNavigationItem(subBox);
                            navigateToTree(myCollectionTreeView, subBoxName);
                        });

                        boolean subBoxHasUnsorted;
                        String subBoxMsg;
                        if (View.CardTreeCell.isIncompleteMarkingEnabled() && boxHasMissingPrintCode(subBox)) {
                            subBoxHasUnsorted = true;
                            subBoxMsg = "This box contains cards without a print code.";
                        } else if (View.CardTreeCell.isIncompleteMarkingEnabled() && boxHasIncompleteCards(subBox)) {
                            subBoxHasUnsorted = true;
                            subBoxMsg = "This box contains cards with no condition or rarity set.";
                        } else {
                            subBoxHasUnsorted = boxHasUnsortedCards(subBox, subBoxName);
                            subBoxMsg = "This box contains unsorted cards.";
                        }
                        applyNavigationItemHighlight(subBoxItem, subBoxHasUnsorted, subBoxMsg);
                        if (subBoxHasUnsorted && !boxHasUnsorted) {
                            applyNavigationItemHighlight(boxItem, true, subBoxMsg);
                            boxHasUnsorted = true;
                        }

                        // Sub-boxes are treated like Boxes for the context menu
                        {
                            ContextMenu subBoxCm = NavigationContextMenuBuilder.forMyCollectionBox(subBox, collection);
                            subBoxItem.setOnContextMenuRequested(e -> {
                                subBoxCm.show(subBoxItem, e.getScreenX(), e.getScreenY());
                                e.consume();
                            });
                        }

                        if (subBox.getContent() != null) {
                            for (CardsGroup group : subBox.getContent()) {
                                String rawGName = group.getName() == null ? "" : group.getName();
                                String gName = Model.CardsLists.OwnedCardsCollection.extractName(rawGName, '-');
                                if (gName.isEmpty()) continue; // skip unnamed groups
                                NavigationItem gItem = createNavigationItem(gName, 2);
                                gItem.setUserData(group);
                                gItem.setItemType(NavigationItem.ItemType.CATEGORY);
                                attachNavItemDropHandlers(gItem, group);
                                enableNavDragSource(gItem, group);
                                enableNavItemAsNavDndTarget(gItem, group,
                                        (dragged, pos) -> handleMyCollectionNavDrop(dragged, group, pos, finalCollection));
                                gItem.setOnLabelClicked(evt -> {
                                    Controller.SelectionManager.setLastClickedNavigationItem(group);
                                    navigateToTree(myCollectionTreeView, subBoxName, gName);
                                });

                                boolean gHasUnsorted;
                                String gMsg;
                                if (View.CardTreeCell.isIncompleteMarkingEnabled() && groupHasMissingPrintCode(group)) {
                                    gHasUnsorted = true;
                                    gMsg = "This category contains cards without a print code.";
                                } else if (View.CardTreeCell.isIncompleteMarkingEnabled() && groupHasIncompleteCards(group)) {
                                    gHasUnsorted = true;
                                    gMsg = "This category contains cards with no condition or rarity set.";
                                } else {
                                    gHasUnsorted = groupHasUnsortedCards(group, gName);
                                    gMsg = "This category contains unsorted cards.";
                                }
                                applyNavigationItemHighlight(gItem, gHasUnsorted, gMsg);
                                if (gHasUnsorted && !subBoxHasUnsorted) {
                                    applyNavigationItemHighlight(subBoxItem, true, gMsg);
                                    subBoxHasUnsorted = true;
                                    if (!boxHasUnsorted) {
                                        applyNavigationItemHighlight(boxItem, true, gMsg);
                                        boxHasUnsorted = true;
                                    }
                                }

                                // Sub-groups are treated like Categories for the context menu
                                {
                                    ContextMenu gCm = NavigationContextMenuBuilder.forMyCollectionCategory(group, subBox, collection);
                                    gItem.setOnContextMenuRequested(e -> {
                                        gCm.show(gItem, e.getScreenX(), e.getScreenY());
                                        e.consume();
                                    });
                                }

                                subBoxItem.addSubItem(gItem);
                            }
                        }
                        boxItem.addSubItem(subBoxItem);
                        // Wire the footer drop-zone AFTER all sub-items of subBoxItem are added
                        enableBoxFooterDropZone(subBoxItem, subBox, finalCollection);
                    }
                }

                // Wire the footer drop-zone AFTER all sub-items of boxItem are added
                enableBoxFooterDropZone(boxItem, box, finalCollection);
                navigationMenu.addItem(boxItem);
            }
        } else {
            NavigationItem none = createNavigationItem("No boxes available", 0);
            navigationMenu.addItem(none);
        }

        menuVBox.getChildren().add(navigationMenu);

// --- NEW: empty-area context menu ---
// NavigationItems consume their own events (e.consume() above), so this
// handler only fires when the user right-clicks the blank background.
        ContextMenu emptyCm = NavigationContextMenuBuilder.forMyCollectionEmpty();
        menuVBox.setOnContextMenuRequested(e -> {
            emptyCm.show(menuVBox, e.getScreenX(), e.getScreenY());
            // Do NOT consume – let the event propagate normally in case the
            // scroll-pane also needs to react (e.g., for scroll handling).
        });
    }

    private void displayArchetypes() throws Exception {
        AnchorPane contentPane = archetypesTab.getContentPane();
        contentPane.getChildren().clear();

        DataTreeItem<Object> rootItem = new DataTreeItem<>("Archetypes", "ROOT");
        rootItem.setExpanded(true);

        List<String> globalNames = SubListCreator.archetypesList;
        List<List<Card>> globalLists = SubListCreator.archetypesCardsLists;

        if (globalNames != null && globalLists != null && globalNames.size() == globalLists.size()) {
            for (int i = 0; i < globalNames.size(); i++) {
                String archetypeName = globalNames.get(i);
                if (archetypeName == null) continue;
                List<Card> cardsForArchetype = globalLists.get(i);
                List<CardElement> elements = new ArrayList<>();
                if (cardsForArchetype != null) {
                    for (Card c : cardsForArchetype) {
                        if (c != null) elements.add(new CardElement(c));
                    }
                }
                CardsGroup archetypeGroup = new CardsGroup(ARCHETYPE_MARKER + archetypeName, elements);
                Map<String, Object> data = new HashMap<>();
                data.put("group", archetypeGroup);
                data.put("missing", Collections.emptySet());
                DataTreeItem<Object> archetypeNode = new DataTreeItem<>(archetypeName, data);
                archetypeNode.setExpanded(false);
                rootItem.getChildren().add(archetypeNode);
            }
        } else {
            DataTreeItem<Object> placeholder = new DataTreeItem<>("No archetypes available", "NO_ARCHETYPES");
            placeholder.setExpanded(false);
            rootItem.getChildren().add(placeholder);
        }

        archetypesTreeView = new TreeView<>(rootItem);
        archetypesTreeView.setCellFactory(param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        archetypesTreeView.setStyle("-fx-background-color: #100317;");
        archetypesTreeView.setShowRoot(false);
        archetypesTreeView.addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                buildMiddlePaneEmptySpaceFilter());
        contentPane.getChildren().add(archetypesTreeView);
        AnchorPane.setTopAnchor(archetypesTreeView, 0.0);
        AnchorPane.setBottomAnchor(archetypesTreeView, 0.0);
        AnchorPane.setLeftAnchor(archetypesTreeView, 0.0);
        AnchorPane.setRightAnchor(archetypesTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        archetypesTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());

        logger.info("Archetypes displayed with {} archetype(s).", rootItem.getChildren().size());
    }

// --- Tree item builders and helpers (unchanged) ---

    private void populateArchetypesMenu() throws Exception {
        VBox menuVBox = archetypesTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        List<String> globalNames = SubListCreator.archetypesList;
        if (globalNames != null && !globalNames.isEmpty()) {
            for (String archetypeName : globalNames) {
                if (archetypeName == null) continue;
                NavigationItem item = createNavigationItem(archetypeName, 0);
                item.setOnLabelClicked(evt -> navigateToTree(archetypesTreeView, archetypeName));
                navigationMenu.addItem(item);
            }
        } else {
            NavigationItem none = createNavigationItem("No archetypes available", 0);
            navigationMenu.addItem(none);
        }

        menuVBox.getChildren().add(navigationMenu);
    }

    /**
     * Returns true if the given box or any of its groups/sub-boxes contains at least one card
     * that computeCardNeedsSorting(...) reports as needing sorting.
     *
     * @param box            The Box to inspect.
     * @param boxDisplayName The sanitized display name used in the navigation (no = or -).
     * @return true if at least one card in the box (or its groups/subboxes) needs sorting.
     */
    private boolean boxHasUnsortedCards(Box box, String boxDisplayName) {
        if (box == null) return false;

        // Check cards in each named category (group) of this box.
        // Pass the group/category name to computeCardNeedsSorting — that is what matches
        // deck/collection type-category names (e.g. "Monstres Fusion").
        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                String rawGroupName = group.getName() == null ? "" : group.getName();
                String groupName = Model.CardsLists.OwnedCardsCollection.extractName(rawGroupName, '-');
                if (groupHasUnsortedCards(group, groupName)) {
                    return true;
                }
            }
        }

        // Check sub-boxes recursively.
        if (box.getSubBoxes() != null) {
            for (Box sub : box.getSubBoxes()) {
                String subBoxDisplayName = Model.CardsLists.OwnedCardsCollection.extractName(
                        sub.getName() == null ? "" : sub.getName(), '=');
                if (boxHasUnsortedCards(sub, subBoxDisplayName)) return true;
            }
        }

        return false;
    }

    private DataTreeItem<Object> createBoxTreeItem(Box box) {
        String cleanName = Model.CardsLists.OwnedCardsCollection.extractName(box.getName() == null ? "" : box.getName(), '=');
        DataTreeItem<Object> boxItem = new DataTreeItem<>(cleanName, box);
        boxItem.setExpanded(true);
        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                DataTreeItem<Object> groupItem = createGroupTreeItem(group);
                boxItem.getChildren().add(groupItem);
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box subBox : box.getSubBoxes()) {
                DataTreeItem<Object> subBoxItem = createBoxTreeItem(subBox);
                boxItem.getChildren().add(subBoxItem);
            }
        }
        return boxItem;
    }

    private DataTreeItem<Object> createGroupTreeItem(CardsGroup group) {
        String displayName = Model.CardsLists.OwnedCardsCollection.extractName(group.getName() == null ? "" : group.getName(), '-');
        DataTreeItem<Object> groupItem = new DataTreeItem<>(displayName, group);
        groupItem.setExpanded(true);
        return groupItem;
    }

    private DataTreeItem<Object> createDeckTreeItem(Deck deck) {
        String cleanName = deck.getName() == null ? "" : deck.getName();
        DataTreeItem<Object> deckItem = new DataTreeItem<>(cleanName, deck);
        deckItem.setExpanded(true);

        // Helper: wire one deck section (main / extra / side) with the reactive listener.
        // The section node is created and registered even when the list is empty,
        // but only inserted into the tree when non-empty. The ListChangeListener
        // adds / removes it dynamically as cards arrive or depart.
        java.util.function.BiConsumer<List<CardElement>, String> wireSection =
                (rawList, sectionLabel) -> {
                    if (rawList == null) return; // truly absent section — skip
                    CardsGroup group = new CardsGroup(sectionLabel, rawList);
                    CardTreeCell.registerDeckSectionGroup(deck,
                            sectionLabel.toLowerCase(java.util.Locale.ROOT)
                                    .replace(" deck", "").trim(),
                            group);
                    DataTreeItem<Object> sectionItem =
                            new DataTreeItem<>(sectionLabel, group);
                    sectionItem.setExpanded(true);

                    if (!rawList.isEmpty()) {
                        deckItem.getChildren().add(sectionItem);
                    }

                    javafx.collections.ObservableList<CardElement> obs =
                            CardTreeCell.observableListFor(group);
                    obs.addListener(
                            (javafx.collections.ListChangeListener<CardElement>) change ->
                                    Platform.runLater(() -> {
                                        boolean has = !obs.isEmpty();
                                        boolean shown = deckItem.getChildren().contains(sectionItem);
                                        if (has && !shown) deckItem.getChildren().add(sectionItem);
                                        else if (!has && shown) deckItem.getChildren().remove(sectionItem);
                                    })
                    );
                };

        wireSection.accept(deck.getMainDeck(), "Main Deck");
        wireSection.accept(deck.getExtraDeck(), "Extra Deck");
        wireSection.accept(deck.getSideDeck(), "Side Deck");

        return deckItem;
    }

    private void displayDecksAndCollections() throws Exception {
        AnchorPane contentPane = decksTab.getContentPane();
        contentPane.getChildren().clear();

        if (UserInterfaceFunctions.getDecksList() == null) {
            UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
        }
        DecksAndCollectionsList decksCollection = UserInterfaceFunctions.getDecksList();
        if (decksCollection == null) {
            throw new Exception("DecksAndCollectionsList is null. Please check the decks folder path.");
        }

        DataTreeItem<Object> rootItem = new DataTreeItem<>("Decks and Collections", "ROOT");
        rootItem.setExpanded(true);

        if (decksCollection.getCollections() != null) {
            for (ThemeCollection collection : decksCollection.getCollections()) {
                DataTreeItem<Object> collItem = createThemeCollectionTreeItem(collection, TabType.DECKS);
                rootItem.getChildren().add(collItem);
            }
        }

        if (decksCollection.getDecks() != null) {
            for (Deck deck : decksCollection.getDecks()) {
                DataTreeItem<Object> deckItem = createDeckTreeItem(deck);
                rootItem.getChildren().add(deckItem);
            }
        }

        decksAndCollectionsTreeView = new TreeView<>(rootItem);
        decksAndCollectionsTreeView.setUserData("DECKS_COLLECTIONS");
        decksAndCollectionsTreeView.setCellFactory(param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        decksAndCollectionsTreeView.setStyle("-fx-background-color: #100317;");
        decksAndCollectionsTreeView.setShowRoot(false);
        decksAndCollectionsTreeView.addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                buildMiddlePaneEmptySpaceFilter());
        contentPane.getChildren().add(decksAndCollectionsTreeView);
        AnchorPane.setTopAnchor(decksAndCollectionsTreeView, 0.0);
        AnchorPane.setBottomAnchor(decksAndCollectionsTreeView, 0.0);
        AnchorPane.setLeftAnchor(decksAndCollectionsTreeView, 0.0);
        AnchorPane.setRightAnchor(decksAndCollectionsTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        decksAndCollectionsTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());

        logger.info("Decks and Collections displayed using the unified layout.");
    }

    private List<CardElement> buildElementsFromGlobalArchetype(String archetypeName) {
        List<CardElement> elements = new ArrayList<>();
        if (archetypeName == null || archetypeName.trim().isEmpty()) return elements;
        try {
            List<String> globalNames = SubListCreator.archetypesList;
            List<List<Card>> globalLists = SubListCreator.archetypesCardsLists;
            if (globalNames == null || globalLists == null) return elements;
            for (int i = 0; i < globalNames.size(); i++) {
                String globalName = globalNames.get(i);
                if (globalName == null) continue;
                if (globalName.equalsIgnoreCase(archetypeName.trim())) {
                    List<Card> cardsForArchetype = globalLists.size() > i ? globalLists.get(i) : null;
                    if (cardsForArchetype != null) {
                        for (Card c : cardsForArchetype) {
                            if (c != null) elements.add(new CardElement(c));
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            logger.debug("buildElementsFromGlobalArchetype failed for {}: {}", archetypeName, e.getMessage());
        }
        return elements;
    }

    //TODO WTF, there is logic from deck import ! The deck should already be imported, just use the decks that are in the Collection !
    private DataTreeItem<Object> createThemeCollectionTreeItem(ThemeCollection collection, TabType tabType) {
        String cleanName = collection.getName() == null ? "" : collection.getName();
        DataTreeItem<Object> collectionItem = new DataTreeItem<>(cleanName, collection);
        collectionItem.setExpanded(true);

        // ── Cards section ────────────────────────────────────────────────────────
        // Always create the group and register it so that pasteCardsIntoNavigationItem
        // can mutate through the ObservableList. Only insert the DataTreeItem when
        // non-empty; the ListChangeListener keeps it in sync afterwards.
        // Declared here so it is also in scope for the Exceptions section below.
        Set<String> missingArtworkSet = computeCardsWithMissingArtworks(collection);
        {
            List<CardElement> cardsList = collection.getCardsList();
            if (cardsList == null) {
                cardsList = new ArrayList<>();
                collection.setCardsList(cardsList);
            }
            CardsGroup cardsGroup = new CardsGroup("Cards", cardsList);
            CardTreeCell.registerCollectionCardsGroup(collection, cardsGroup);

            // Register the missing-artwork set so CardGridCell can apply the white glow.
            CardTreeCell.setMissingArtworkSetForGroup(cardsGroup, missingArtworkSet);

            DataTreeItem<Object> cardsGroupItem = new DataTreeItem<>("Cards", cardsGroup);
            cardsGroupItem.setExpanded(true);

            if (!cardsList.isEmpty()) {
                collectionItem.getChildren().add(cardsGroupItem);
            }

            javafx.collections.ObservableList<CardElement> obs =
                    CardTreeCell.observableListFor(cardsGroup);
            obs.addListener(
                    (javafx.collections.ListChangeListener<CardElement>) change ->
                            Platform.runLater(() -> {
                                boolean has = !obs.isEmpty();
                                boolean shown = collectionItem.getChildren().contains(cardsGroupItem);
                                if (has && !shown) {
                                    // Insert as first child so it stays above Decks / Archetypes.
                                    collectionItem.getChildren().add(0, cardsGroupItem);
                                } else if (!has && shown) {
                                    collectionItem.getChildren().remove(cardsGroupItem);
                                }
                            })
            );
        }

        // ── Decks section ────────────────────────────────────────────────────────
        DataTreeItem<Object> decksParent = new DataTreeItem<>("Decks", "DECKS_SECTION");
        decksParent.setExpanded(true);
        boolean decksParentHasChildren = false;

        if (collection.getLinkedDecks() != null && !collection.getLinkedDecks().isEmpty()) {
            int unitIndex = 1;
            for (List<Deck> unit : collection.getLinkedDecks()) {
                if (unit == null || unit.isEmpty()) {
                    DataTreeItem<Object> emptyUnit =
                            new DataTreeItem<>("Group " + unitIndex, "DECK_GROUP");
                    emptyUnit.setExpanded(false);
                    decksParent.getChildren().add(emptyUnit);
                    unitIndex++;
                    continue;
                }

                if (unit.size() > 1) {
                    DataTreeItem<Object> unitNode =
                            new DataTreeItem<>("Group " + unitIndex, "DECK_GROUP");
                    unitNode.setExpanded(false);
                    for (Deck d : unit) {
                        DataTreeItem<Object> deckItem = createDeckTreeItem(d);
                        unitNode.getChildren().add(deckItem);
                    }
                    decksParent.getChildren().add(unitNode);
                } else {
                    Deck single = unit.get(0);
                    DataTreeItem<Object> deckItem = createDeckTreeItem(single);
                    decksParent.getChildren().add(deckItem);
                }
                unitIndex++;
            }
            decksParentHasChildren = !decksParent.getChildren().isEmpty();
        }

        if (decksParentHasChildren) {
            collectionItem.getChildren().add(decksParent);
        }

        if (tabType != TabType.OUICHE_LIST) {
            // ── Archetypes section ───────────────────────────────────────────────
            DataTreeItem<Object> archetypesParent =
                    new DataTreeItem<>("Archetypes", "ARCHETYPES_SECTION");
            archetypesParent.setExpanded(true);
            boolean archetypesAdded = false;
            boolean hasArchetypesMethod = false;

            try {
                Method m = collection.getClass().getMethod("getArchetypes");
                hasArchetypesMethod = true;
                Object res = m.invoke(collection);
                if (res instanceof List) {
                    List<?> archetypes = (List<?>) res;
                    logger.debug("Collection '{}' getArchetypes() returned list size={}",
                            collection.getName(), archetypes.size());

                    for (Object archetypeObj : archetypes) {
                        if (archetypeObj == null) continue;
                        if (archetypeObj instanceof String) {
                            String archetypeName = ((String) archetypeObj).trim();
                            if (archetypeName.isEmpty()) continue;
                            List<CardElement> elements =
                                    buildElementsFromGlobalArchetype(archetypeName);
                            Set<String> missing =
                                    computeMissingIdsForElements(collection, elements);
                            CardsGroup archetypeGroup =
                                    new CardsGroup(ARCHETYPE_MARKER + archetypeName, elements);
                            Map<String, Object> data = new HashMap<>();
                            data.put("group", archetypeGroup);
                            data.put("missing", missing);
                            DataTreeItem<Object> archetypeNode =
                                    new DataTreeItem<>(archetypeName, data);
                            archetypeNode.setExpanded(false);
                            archetypesParent.getChildren().add(archetypeNode);
                            archetypesAdded = true;
                        } else {
                            String name = null;
                            List<CardElement> elements = new ArrayList<>();
                            try {
                                Method nameMethod =
                                        archetypeObj.getClass().getMethod("getName");
                                Object nameVal = nameMethod.invoke(archetypeObj);
                                if (nameVal != null) name = nameVal.toString();
                            } catch (Exception ignored) {
                            }
                            try {
                                Method cardsMethod =
                                        archetypeObj.getClass().getMethod("getCards");
                                Object cardsVal = cardsMethod.invoke(archetypeObj);
                                if (cardsVal instanceof List) {
                                    for (Object o : (List<?>) cardsVal) {
                                        if (o instanceof CardElement)
                                            elements.add((CardElement) o);
                                        else if (o instanceof Card)
                                            elements.add(new CardElement((Card) o));
                                        else if (o instanceof String) {
                                            try {
                                                elements.add(new CardElement((String) o));
                                            } catch (Exception ignored) {
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }

                            if ((elements == null || elements.isEmpty()) && name != null) {
                                elements = buildElementsFromGlobalArchetype(name);
                            }
                            if (name == null) name = "Archetype";

                            Set<String> missing =
                                    computeMissingIdsForElements(collection, elements);
                            CardsGroup archetypeGroup =
                                    new CardsGroup(ARCHETYPE_MARKER + name, elements);
                            Map<String, Object> data = new HashMap<>();
                            data.put("group", archetypeGroup);
                            data.put("missing", missing);
                            DataTreeItem<Object> archetypeNode =
                                    new DataTreeItem<>(name, data);
                            archetypeNode.setExpanded(false);
                            archetypesParent.getChildren().add(archetypeNode);
                            archetypesAdded = true;
                        }
                    }
                }
            } catch (NoSuchMethodException ignored) {
                hasArchetypesMethod = false;
            } catch (Exception e) {
                logger.debug("Archetypes reflection failed for collection "
                        + collection.getName(), e);
            }

            // Fallback: global SubListCreator archetypes (only when collection
            // does NOT provide getArchetypes()).
            if (!hasArchetypesMethod && !archetypesAdded) {
                try {
                    List<String> globalNames = SubListCreator.archetypesList;
                    List<List<Card>> globalLists = SubListCreator.archetypesCardsLists;
                    if (globalNames != null && globalLists != null
                            && globalNames.size() == globalLists.size()) {
                        for (int i = 0; i < globalNames.size(); i++) {
                            String archetypeName = globalNames.get(i);
                            if (archetypeName == null) continue;
                            List<Card> cardsForArchetype = globalLists.get(i);
                            List<CardElement> elements = new ArrayList<>();
                            if (cardsForArchetype != null) {
                                for (Card c : cardsForArchetype) {
                                    if (c != null) elements.add(new CardElement(c));
                                }
                            }
                            Set<String> missing =
                                    computeMissingIdsForElements(collection, elements);
                            CardsGroup archetypeGroup =
                                    new CardsGroup(ARCHETYPE_MARKER + archetypeName, elements);
                            Map<String, Object> data = new HashMap<>();
                            data.put("group", archetypeGroup);
                            data.put("missing", missing);
                            DataTreeItem<Object> archetypeNode =
                                    new DataTreeItem<>(archetypeName, data);
                            archetypeNode.setExpanded(false);
                            archetypesParent.getChildren().add(archetypeNode);
                            archetypesAdded = true;
                        }
                    }
                } catch (Exception e) {
                    logger.debug("Fallback archetypes population failed", e);
                }
            }

            if (archetypesAdded) {
                logger.info("Collection '{}' -> added {} archetype group(s)",
                        collection.getName(),
                        archetypesParent.getChildren().size());
                collectionItem.getChildren().add(archetypesParent);
            }

            // ── Cards not to add / Exceptions section ────────────────────────────
            // Same reactive pattern as the "Cards" section above.
            {
                List<CardElement> exceptions = collection.getExceptionsToNotAdd();
                if (exceptions == null) exceptions = new ArrayList<>();

                CardsGroup exceptionsGroup =
                        new CardsGroup("Cards not to add", exceptions);
                CardTreeCell.registerCollectionExceptionsGroup(collection, exceptionsGroup);
                // Reuse the same set computed above (same collection, same scope).
                CardTreeCell.setMissingArtworkSetForGroup(exceptionsGroup, missingArtworkSet);

                DataTreeItem<Object> exceptionsNode =
                        new DataTreeItem<>("Cards not to add", exceptionsGroup);
                exceptionsNode.setExpanded(true);

                if (!exceptions.isEmpty()) {
                    collectionItem.getChildren().add(exceptionsNode);
                }

                javafx.collections.ObservableList<CardElement> excObs =
                        CardTreeCell.observableListFor(exceptionsGroup);
                excObs.addListener(
                        (javafx.collections.ListChangeListener<CardElement>) change ->
                                Platform.runLater(() -> {
                                    boolean has = !excObs.isEmpty();
                                    boolean shown = collectionItem.getChildren()
                                            .contains(exceptionsNode);
                                    if (has && !shown)
                                        collectionItem.getChildren().add(exceptionsNode);
                                    else if (!has && shown)
                                        collectionItem.getChildren().remove(exceptionsNode);
                                })
                );
            }
        }

        return collectionItem;
    }

    private void populateOuicheListMenu() throws Exception {
        VBox menuVBox = ouicheListTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        DecksAndCollectionsList ouicheDetailed = Model.CardsLists.OuicheList.getDetailedOuicheList();
        if (ouicheDetailed == null) {
            UserInterfaceFunctions.generateOuicheList();
            ouicheDetailed = Model.CardsLists.OuicheList.getDetailedOuicheList();
            if (ouicheDetailed == null) {
                Label errorLabel = new Label("No OuicheList available.");
                errorLabel.setStyle("-fx-text-fill: white;");
                navigationMenu.addItem(new NavigationItem(errorLabel.getText(), 0));
                menuVBox.getChildren().add(navigationMenu);
                return;
            }
        }

        if (ouicheDetailed.getCollections() != null) {
            for (ThemeCollection collection : ouicheDetailed.getCollections()) {
                NavigationItem collectionNavItem = createNavigationItem(collection.getName(), 0);
                collectionNavItem.setUserData(collection);
                collectionNavItem.setItemType(NavigationItem.ItemType.COLLECTION);

                // Loose collections are shown in italic in both the Decks tab and OuicheList tab.
                if (Boolean.TRUE.equals(collection.getConnectToWholeCollection())) {
                    String current = collectionNavItem.getLabel().getStyle();
                    collectionNavItem.getLabel().setStyle(current + " -fx-font-style: italic;");
                }

                collectionNavItem.setOnLabelClicked(evt -> navigateToTree(ouicheTreeView, collection.getName()));
                collectionNavItem.setExpanded(false);
                navigationMenu.addItem(collectionNavItem);

                if (collection.getLinkedDecks() != null) {
                    boolean firstNonNullUnit = true;
                    for (List<Deck> unit : collection.getLinkedDecks()) {
                        if (unit == null || unit.isEmpty()) continue;
                        // Insert a separator between consecutive deck lists (not before the first one)
                        if (!firstNonNullUnit) {
                            collectionNavItem.addDeckListSeparator();
                        }
                        firstNonNullUnit = false;
                        for (Deck linkedDeck : unit) {
                            if (linkedDeck == null) continue;
                            NavigationItem deckSubItem = createNavigationItem(linkedDeck.getName(), 1);
                            deckSubItem.setUserData(linkedDeck);
                            deckSubItem.setItemType(NavigationItem.ItemType.DECK);
                            // navigate to collection -> Decks -> deckName
                            deckSubItem.setOnLabelClicked(evt -> navigateToTree(ouicheTreeView, collection.getName(), "Decks", linkedDeck.getName()));
                            collectionNavItem.addSubItem(deckSubItem);
                        }
                    }
                }
            }
        }

        if (ouicheDetailed.getDecks() != null) {
            for (Deck deck : ouicheDetailed.getDecks()) {
                NavigationItem navItem = createNavigationItem(deck.getName(), 0);
                navItem.setUserData(deck);
                navItem.setItemType(NavigationItem.ItemType.DECK);
                navItem.setOnLabelClicked(evt -> navigateToTree(ouicheTreeView, deck.getName()));
                navigationMenu.addItem(navItem);
            }
        }

        menuVBox.getChildren().add(navigationMenu);
    }

    /**
     * Handles a NAV drag-drop onto a Decks &amp; Collections navigation item.
     *
     * <p>Rules:
     * <ul>
     *   <li>Deck → Deck (BEFORE/AFTER): reorder; respects standalone vs in-collection.</li>
     *   <li>Deck → Collection (INTO / AFTER): move deck into that collection as a new unit.</li>
     *   <li>Deck → Collection (BEFORE): same as INTO (prepend as first unit).</li>
     * </ul>
     */
    private void handleDecksNavDrop(Object dragged, Object target,
                                    View.NavigationItem.DropPosition pos,
                                    DecksAndCollectionsList dcl) {
        if (dragged == null || target == null || dragged == target) return;
        if (!(dragged instanceof Model.CardsLists.Deck draggedDeck)) return;

        // Snapshot source collection BEFORE the move — removeDeckFromModel will shift
        // the model and findDeckLocation would return a wrong result afterwards.
        Controller.NavigationDragDrop.DeckLocation srcLoc =
                Controller.NavigationDragDrop.findDeckLocation(draggedDeck, dcl);
        Model.CardsLists.ThemeCollection srcCollection = srcLoc.collection; // null if standalone

        boolean changed = false;
        Model.CardsLists.ThemeCollection destCollection = null;

        if (target instanceof Model.CardsLists.Deck targetDeck) {
            Controller.NavigationDragDrop.DeckLocation targetLoc =
                    Controller.NavigationDragDrop.findDeckLocation(targetDeck, dcl);
            destCollection = targetLoc.collection; // null if standalone
            switch (pos) {
                case BEFORE -> changed = Controller.NavigationDragDrop.moveDeckBefore(draggedDeck, targetDeck, dcl);
                case AFTER, INTO -> changed = Controller.NavigationDragDrop.moveDeckAfter(draggedDeck, targetDeck, dcl);
            }
        } else if (target instanceof Model.CardsLists.ThemeCollection targetColl) {
            destCollection = targetColl;
            changed = Controller.NavigationDragDrop.moveDeckToCollection(draggedDeck, targetColl, dcl);
        }

        if (changed) {
            // Mark the affected collection(s) dirty — NOT the deck itself, which is
            // structurally unchanged; only its position in the collection file changes.
            if (srcCollection != null) Controller.UserInterfaceFunctions.markDirty(srcCollection);
            if (destCollection != null && destCollection != srcCollection)
                Controller.UserInterfaceFunctions.markDirty(destCollection);
            Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
        }
    }

    /**
     * Wires a deck-list separator node as a drop target.
     * Dropping a Deck onto the separator creates a new unit between the two
     * surrounding deck lists of {@code collection}.
     *
     * @param sepNode       the HBox returned by {@link View.NavigationItem#addDeckListSeparator()}
     * @param collection    the ThemeCollection the separator belongs to
     * @param afterUnitIdx  the 0-based index of the unit immediately ABOVE this separator
     * @param dcl           the live DecksAndCollectionsList
     */
    private void wireSeparatorAsDropTarget(javafx.scene.layout.HBox sepNode,
                                           Model.CardsLists.ThemeCollection collection,
                                           int afterUnitIdx,
                                           DecksAndCollectionsList dcl) {
        sepNode.setOnDragOver(event -> {
            if ("NAV".equals(Controller.DragDropManager.getDragSourcePane())
                    && event.getDragboard().hasString()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
                sepNode.setStyle(
                        "-fx-background-color: #cdfc04;" +
                                "-fx-min-height: 1; -fx-pref-height: 1; -fx-max-height: 1;");
            }
            event.consume();
        });
        sepNode.setOnDragExited(event -> {
            sepNode.setStyle(
                    "-fx-background-color: #555577;" +
                            "-fx-min-height: 1; -fx-pref-height: 1; -fx-max-height: 1;");
            event.consume();
        });
        sepNode.setOnDragDropped(event -> {
            Object dragged = Controller.DragDropManager.getDraggedNavObject();
            boolean changed = false;
            if (dragged instanceof Model.CardsLists.Deck draggedDeck) {
                // Snapshot source collection BEFORE the move
                Controller.NavigationDragDrop.DeckLocation srcLoc =
                        Controller.NavigationDragDrop.findDeckLocation(draggedDeck, dcl);
                Model.CardsLists.ThemeCollection srcCollection = srcLoc.collection;

                changed = Controller.NavigationDragDrop.moveDeckToNewUnit(
                        draggedDeck, collection, afterUnitIdx, dcl);

                if (changed) {
                    // Mark source collection dirty if it differs from the target
                    if (srcCollection != null && srcCollection != collection)
                        Controller.UserInterfaceFunctions.markDirty(srcCollection);
                    // Always mark the target collection dirty (new unit was added to it)
                    Controller.UserInterfaceFunctions.markDirty(collection);
                }
            }
            sepNode.setStyle(
                    "-fx-background-color: #555577;" +
                            "-fx-min-height: 1; -fx-pref-height: 1; -fx-max-height: 1;");
            if (changed) {
                Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }

    /**
     * Recursively captures the expanded state of a NavigationItem and all its
     * sub-items into {@code map}, keyed by {@link NavigationItem#getUserData()}.
     */
    private void captureExpandedState(NavigationItem item,
                                      java.util.Map<Object, Boolean> map) {
        if (item == null) return;
        Object key = item.getUserData();
        if (key != null) map.put(key, item.isExpanded());
        for (NavigationItem sub : item.getSubItems()) {
            captureExpandedState(sub, map);
        }
    }

    private Set<String> computeMissingIdsForElements(ThemeCollection collection, List<CardElement> elements) {
        Set<String> missing = new HashSet<>();
        if (elements == null || elements.isEmpty()) return missing;

        for (CardElement ce : elements) {
            if (ce == null) continue;
            Card c = ce.getCard();
            if (c == null) continue;

            String konamiId = c.getKonamiId();
            String passCode = c.getPassCode();

            boolean found = false;
            if (konamiId != null && !konamiId.isEmpty()) found = isKonamiIdPresentInCollection(collection, konamiId);
            if (!found && passCode != null && !passCode.isEmpty())
                found = isPassCodePresentInCollection(collection, passCode);

            if (!found) {
                if (konamiId != null && !konamiId.isEmpty()) missing.add(konamiId);
                if (passCode != null && !passCode.isEmpty()) missing.add(passCode);
                logger.debug("Marking as missing for collection '{}': konamiId='{}' passCode='{}'", collection.getName(), konamiId, passCode);
            } else {
                logger.debug("Not marking for collection '{}': konamiId='{}' passCode='{}' (found)", collection.getName(), konamiId, passCode);
            }
        }
        return missing;
    }

    private boolean isKonamiIdPresentInCollection(ThemeCollection collection, String konamiId) {
        if (konamiId == null || konamiId.isEmpty()) return false;
        try {
            List<CardElement> cardsList = collection.getCardsList();
            if (cardsList != null) {
                for (CardElement ce : cardsList) {
                    if (ce == null) continue;
                    Card cc = ce.getCard();
                    if (cc != null && konamiId.equals(cc.getKonamiId())) return true;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Method exceptionsMethod = collection.getClass().getMethod("getExceptionsToNotAdd");
            Object exceptionsVal = exceptionsMethod.invoke(collection);
            if (exceptionsVal instanceof List) {
                for (Object o : (List<?>) exceptionsVal) {
                    if (o instanceof CardElement) {
                        Card cc = ((CardElement) o).getCard();
                        if (cc != null && konamiId.equals(cc.getKonamiId())) return true;
                    } else if (o instanceof Card) {
                        Card cc = (Card) o;
                        if (cc != null && konamiId.equals(cc.getKonamiId())) return true;
                    }
                }
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {
        }

        try {
            List<List<Deck>> linked = collection.getLinkedDecks();
            if (linked != null) {
                for (List<Deck> unit : linked) {
                    if (unit == null) continue;
                    for (Deck d : unit) {
                        if (d == null) continue;
                        if (deckContainsKonamiId(d, konamiId)) return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isPassCodePresentInCollection(ThemeCollection collection, String passCode) {
        if (passCode == null || passCode.isEmpty()) return false;
        try {
            List<CardElement> cardsList = collection.getCardsList();
            if (cardsList != null) {
                for (CardElement ce : cardsList) {
                    if (ce == null) continue;
                    Card cc = ce.getCard();
                    if (cc != null && passCode.equals(cc.getPassCode())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            Method exceptionsMethod = collection.getClass().getMethod("getExceptionsToNotAdd");
            Object exceptionsVal = exceptionsMethod.invoke(collection);
            if (exceptionsVal instanceof List) {
                for (Object o : (List<?>) exceptionsVal) {
                    if (o instanceof CardElement) {
                        Card cc = ((CardElement) o).getCard();
                        if (cc != null && passCode.equals(cc.getPassCode())) return true;
                    } else if (o instanceof Card) {
                        Card cc = (Card) o;
                        if (cc != null && passCode.equals(cc.getPassCode())) return true;
                    } else if (o instanceof String) {
                        String s = ((String) o).trim();
                        if (!s.isEmpty() && s.equals(passCode)) return true;
                    }
                }
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {
        }
        try {
            List<List<Deck>> linked = collection.getLinkedDecks();
            if (linked != null) {
                for (List<Deck> unit : linked) {
                    if (unit == null) continue;
                    for (Deck d : unit) {
                        if (d == null) continue;
                        if (deckContainsPassCode(d, passCode)) return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private NavigationItem createNavigationItem(String name, int depth) {
        if (name == null) name = "";
        // Do NOT strip here — callers pass already-clean names.
        // Stripping here would remove legitimate hyphens inside names.
        NavigationItem item = new NavigationItem(name, depth);
        item.setOnLabelClicked(event -> {
        });
        return item;
    }

    /**
     * Groups all database cards by their French name (English as fallback), then
     * for each card in {@code collection} (both cardsList and exceptionsToNotAdd)
     * checks whether the database has more than one artwork number for that name
     * and whether the collection holds all of them.
     *
     * <p>Returns a set of konamiIds AND passCodes that ARE present in the collection
     * but belong to a multi-artwork card missing at least one sibling artwork.
     * Both identifiers are added so that CardGridCell can match via either field.
     */
    private Set<String> computeCardsWithMissingArtworks(ThemeCollection collection) {
        Set<String> result = new HashSet<>();
        if (collection == null) return result;

        // ── 1. Load the card database ──────────────────────────────────────────────
        Map<Integer, Card> allCards;
        try {
            allCards = Model.Database.Database.getAllCardsList();
        } catch (Exception e) {
            logger.warn("computeCardsWithMissingArtworks: DB unavailable for '{}': {}",
                    collection.getName(), e.toString());
            return result;
        }
        if (allCards == null || allCards.isEmpty()) {
            logger.warn("computeCardsWithMissingArtworks: DB empty for collection '{}'",
                    collection.getName());
            return result;
        }

        // ── 2. Build DB indexes ────────────────────────────────────────────────────
        // name → Set<artNumber>: the artwork variants that exist in the DB per name.
        // Only names with 2+ artNumbers are kept (true multi-artwork cards).
        // konamiId → DB Card: for resolving collection cards whose passCode is absent.
        Map<String, Set<String>> nameToAllArtNumbers = new HashMap<>();
        Map<String, Card> dbByKonamiId = new HashMap<>();

        for (Map.Entry<Integer, Card> entry : allCards.entrySet()) {
            Card dbCard = entry.getValue();
            if (dbCard == null) continue;

            String name = dbCard.getName_FR();
            if (name == null || name.isBlank()) name = dbCard.getName_EN();
            if (name == null || name.isBlank()) continue;

            String art = dbCard.getArtNumber();
            if (art == null || art.isBlank()) art = "1";

            nameToAllArtNumbers.computeIfAbsent(name, k -> new HashSet<>()).add(art);

            if (dbCard.getKonamiId() != null && !dbCard.getKonamiId().isBlank())
                dbByKonamiId.putIfAbsent(dbCard.getKonamiId(), dbCard);
        }

        nameToAllArtNumbers.entrySet().removeIf(e -> e.getValue().size() <= 1);
        logger.info("computeCardsWithMissingArtworks: {} multi-artwork names for collection '{}'",
                nameToAllArtNumbers.size(), collection.getName());
        if (nameToAllArtNumbers.isEmpty()) return result;

        // ── 3. Resolve each collection card to (canonicalName, artNumber) ──────────
        // artNumber resolution priority:
        //   a) card.getArtNumber()           — set when the user selects a specific artwork
        //   b) allCards.get(parsedPassCode)   — direct DB map lookup by Integer key;
        //      no string-comparison issues, works whenever passCode is a numeric string
        //   c) dbByKonamiId.get(konamiId)    — last resort; unreliable if konamiId is
        //      a name-level ID shared by all artworks, but better than defaulting
        //   d) "1"                           — absolute fallback
        List<CardElement> collectionCards = new ArrayList<>();
        if (collection.getCardsList() != null) collectionCards.addAll(collection.getCardsList());
        if (collection.getExceptionsToNotAdd() != null) collectionCards.addAll(collection.getExceptionsToNotAdd());

        // name → artNumbers present in this collection
        Map<String, Set<String>> presentArtNumbers = new HashMap<>();
        // name → collection CardElements (for marking in step 4)
        Map<String, List<CardElement>> cardsByName = new HashMap<>();

        for (CardElement ce : collectionCards) {
            if (ce == null || ce.getCard() == null) continue;
            Card c = ce.getCard();

            String name = c.getName_FR();
            if (name == null || name.isBlank()) name = c.getName_EN();
            if (name == null || name.isBlank()) continue;
            if (!nameToAllArtNumbers.containsKey(name)) continue;

            // Resolve artNumber for this collection card.
            String artNumber = null;

            // a) card's own artNumber (explicit artwork selection)
            if (c.getArtNumber() != null && !c.getArtNumber().isBlank())
                artNumber = c.getArtNumber();

            // b) direct DB lookup by parsed passCode integer key
            if (artNumber == null && c.getPassCode() != null && !c.getPassCode().isBlank()) {
                try {
                    Card dbCard = allCards.get(Integer.parseInt(c.getPassCode().trim()));
                    if (dbCard != null && dbCard.getArtNumber() != null && !dbCard.getArtNumber().isBlank())
                        artNumber = dbCard.getArtNumber();
                } catch (NumberFormatException ignored) {
                }
            }

            // c) konamiId lookup
            if (artNumber == null && c.getKonamiId() != null && !c.getKonamiId().isBlank()) {
                Card dbCard = dbByKonamiId.get(c.getKonamiId());
                if (dbCard != null && dbCard.getArtNumber() != null && !dbCard.getArtNumber().isBlank())
                    artNumber = dbCard.getArtNumber();
            }

            // d) default
            if (artNumber == null || artNumber.isBlank()) artNumber = "1";

            presentArtNumbers.computeIfAbsent(name, k -> new HashSet<>()).add(artNumber);
            cardsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(ce);
        }

        // ── 4. Completeness check and marking ─────────────────────────────────────
        for (Map.Entry<String, Set<String>> entry : presentArtNumbers.entrySet()) {
            String name = entry.getKey();
            Set<String> haveArts = entry.getValue();
            Set<String> allArts = nameToAllArtNumbers.get(name);

            logger.debug("computeCardsWithMissingArtworks: '{}' — have={} all={} complete={}",
                    name, haveArts, allArts, haveArts.containsAll(allArts));

            if (haveArts.containsAll(allArts)) continue; // collection is complete — do not mark

            List<CardElement> toMark = cardsByName.get(name);
            if (toMark == null) continue;
            for (CardElement ce : toMark) {
                if (ce.getCard() == null) continue;
                Card c = ce.getCard();
                if (c.getKonamiId() != null && !c.getKonamiId().isBlank()) result.add(c.getKonamiId());
                if (c.getPassCode() != null && !c.getPassCode().isBlank()) result.add(c.getPassCode().trim());
            }
        }

        logger.info("computeCardsWithMissingArtworks: collection '{}' → {} identifiers marked",
                collection.getName(), result.size());
        return result;
    }

    /**
     * True when at least one multi-artwork card in {@code collection} is missing a sibling artwork.
     */
    private boolean collectionHasMissingArtworks(ThemeCollection collection) {
        return !computeCardsWithMissingArtworks(collection).isEmpty();
    }

    /**
     * Determine whether a ThemeCollection has at least one missing card in its archetypes.
     * <p>
     * IMPORTANT: returns true only when the collection provides a non-empty getArchetypes() list
     * and at least one archetype yields missing cards for that collection.
     * <p>
     * Collections that do not expose getArchetypes(), or that expose it but it is empty/null,
     * will return false (no highlight).
     */
    private boolean collectionHasMissing(ThemeCollection collection) {
        if (collection == null) return false;

        boolean hasArchetypesMethod = false;
        try {
            Method m = collection.getClass().getMethod("getArchetypes");
            hasArchetypesMethod = true;
            Object res = m.invoke(collection);
            if (!(res instanceof List)) {
                // method exists but doesn't return a list -> treat as no archetypes
                return false;
            }
            List<?> archetypes = (List<?>) res;
            if (archetypes == null || archetypes.isEmpty()) {
                // collection explicitly has no archetypes -> cannot have missing archetype cards
                return false;
            }

            // For each archetype provided by the collection, compute missing IDs and return true if any missing
            for (Object archetypeObj : archetypes) {
                if (archetypeObj == null) continue;
                List<CardElement> elements = new ArrayList<>();
                if (archetypeObj instanceof String) {
                    String archetypeName = ((String) archetypeObj).trim();
                    if (archetypeName.isEmpty()) continue;
                    elements = buildElementsFromGlobalArchetype(archetypeName);
                } else {
                    String name = null;
                    try {
                        Method nameMethod = archetypeObj.getClass().getMethod("getName");
                        Object nameVal = nameMethod.invoke(archetypeObj);
                        if (nameVal != null) name = nameVal.toString();
                    } catch (Exception ignored) {
                    }
                    try {
                        Method cardsMethod = archetypeObj.getClass().getMethod("getCards");
                        Object cardsVal = cardsMethod.invoke(archetypeObj);
                        if (cardsVal instanceof List) {
                            for (Object o : (List<?>) cardsVal) {
                                if (o instanceof CardElement) elements.add((CardElement) o);
                                else if (o instanceof Card) elements.add(new CardElement((Card) o));
                                else if (o instanceof String) {
                                    try {
                                        elements.add(new CardElement((String) o));
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    if ((elements == null || elements.isEmpty()) && name != null) {
                        elements = buildElementsFromGlobalArchetype(name);
                    }
                }
                Set<String> missing = computeMissingIdsForElements(collection, elements);
                if (!missing.isEmpty()) return true;
            }
        } catch (NoSuchMethodException ignored) {
            // collection does not provide getArchetypes -> cannot have missing archetype cards
            return false;
        } catch (Exception e) {
            logger.debug("collectionHasMissing: reflection failed for collection " + (collection == null ? "null" : collection.getName()), e);
            return false;
        }

        return false;
    }

    private boolean deckContainsKonamiId(Deck deck, String konamiId) {
        if (deck == null || konamiId == null) return false;
        try {
            if (deck.getMainDeck() != null) {
                for (CardElement ce : deck.getMainDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && konamiId.equals(c.getKonamiId())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (deck.getExtraDeck() != null) {
                for (CardElement ce : deck.getExtraDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && konamiId.equals(c.getKonamiId())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (deck.getSideDeck() != null) {
                for (CardElement ce : deck.getSideDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && konamiId.equals(c.getKonamiId())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isMarkerElement(CardElement ce) {
        if (ce == null) return true;

        try {
            Card c = ce.getCard();
            if (c != null) {
                if ((c.getKonamiId() != null && !c.getKonamiId().trim().isEmpty())
                        || (c.getPassCode() != null && !c.getPassCode().trim().isEmpty())
                        || (c.getName_EN() != null && !c.getName_EN().trim().isEmpty())
                        || (c.getName_FR() != null && !c.getName_FR().trim().isEmpty())
                        || (c.getName_JA() != null && !c.getName_JA().trim().isEmpty())) {
                    return false;
                }
            }
        } catch (Exception ignored) {
        }

        String s = ce.toString();
        if (s == null) return true;
        s = s.trim();
        if (s.isEmpty()) return true;
        if (s.startsWith("#")) return true;
        if (s.equalsIgnoreCase("Linked decks")) return true;
        if (s.equalsIgnoreCase("#Linked decks")) return true;
        if (s.equalsIgnoreCase("Linked decks:")) return true;
        return false;
    }

    /**
     * Navigate to a node in the given TreeView by matching a path of node text values.
     * Example: navigateToTree(decksTreeView, "CollectionName", "Decks", "Zombie")
     */
    private void navigateToTree(TreeView<String> treeView, String... path) {
        if (treeView == null || path == null || path.length == 0) return;
        TreeItem<String> root = treeView.getRoot();
        if (root == null) return;

        TreeItem<String> found = findTreeItemByPath(root, path, 0);
        if (found != null) {
            // expand parents so the node is visible
            TreeItem<String> parent = found.getParent();
            while (parent != null) {
                parent.setExpanded(true);
                parent = parent.getParent();
            }

            // select and scroll to the row
            final TreeItem<String> toSelect = found;
            Platform.runLater(() -> {
                treeView.getSelectionModel().select(toSelect);
                int row = treeView.getRow(toSelect);
                if (row >= 0) treeView.scrollTo(row);
            });
        }
    }

    private TreeItem<String> findTreeItemByPath(TreeItem<String> node, String[] path, int index) {
        if (node == null || path == null || index >= path.length) return null;
        String nodeValue = node.getValue();
        if (nodeValue == null) nodeValue = "";

        if (!nodeValue.equals(path[index])) {
            for (TreeItem<String> child : node.getChildren()) {
                TreeItem<String> res = findTreeItemByPath(child, path, index);
                if (res != null) return res;
            }
            return null;
        }

        if (index == path.length - 1) {
            return node;
        }

        for (TreeItem<String> child : node.getChildren()) {
            TreeItem<String> res = findTreeItemByPath(child, path, index + 1);
            if (res != null) return res;
        }
        return null;
    }

    private String normalizeName(String s) {
        if (s == null) return "";
        String trimmed = s.trim().toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        normalized = normalized.replaceAll("[^\\p{Alnum}\\s]", " ").replaceAll("\\s+", " ").trim();
        return normalized;
    }

    private boolean deckContainsPassCode(Deck deck, String passCode) {
        if (deck == null || passCode == null) return false;
        try {
            if (deck.getMainDeck() != null) {
                for (CardElement ce : deck.getMainDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && passCode.equals(c.getPassCode())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (deck.getExtraDeck() != null) {
                for (CardElement ce : deck.getExtraDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && passCode.equals(c.getPassCode())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (deck.getSideDeck() != null) {
                for (CardElement ce : deck.getSideDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && passCode.equals(c.getPassCode())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void applyNavigationItemHighlight(NavigationItem navItem, boolean highlight) {
        applyNavigationItemHighlight(navItem, highlight, null);
    }

    private void applyNavigationItemHighlight(NavigationItem navItem, boolean highlight, String warningMessage) {
        if (navItem == null) return;
        javafx.scene.control.Label label = navItem.getLabel();
        if (label == null) return;
        // Strip any existing -fx-text-fill declaration and append the correct color.
        // This preserves all other properties already on the label (bold, italic, etc.).
        String base = label.getStyle() == null ? "" : label.getStyle();
        base = base.replaceAll("-fx-text-fill\\s*:[^;]*(;|$)", "").trim();
        String color = highlight ? "#cdfc04" : "white";
        label.setStyle(base + (base.isEmpty() ? "" : " ") + "-fx-text-fill: " + color + ";");

        if (highlight && warningMessage != null) {
            navItem.setWarningTooltip(warningMessage);
        } else {
            navItem.clearWarningTooltip();
        }
    }

    /**
     * Cached, position-aware wrapper that returns whether the card at a given index
     * inside a container needs sorting.
     *
     * @param card        the Card to test
     * @param container   the container object (CardsGroup, Box, or the raw List instance)
     * @param index       the index of the card inside the container (0-based)
     * @param elementName the name of the element (Box name or CardsGroup name) that contains the card
     * @return true if the card needs sorting (should glow), false otherwise
     */
    private boolean cardNeedsSorting(Model.CardsLists.Card card, Object container, int index, String elementName) {
        if (card == null) return false;
        if (container == null || index < 0) {
            // Fallback to non-cached compute if we don't have container/index
            return CardQualityService.computeCardNeedsSorting(card, elementName);
        }

        // Obtain or create the per-container list of cached results
        List<Boolean> cacheList = positionSortCache.computeIfAbsent(container, k ->
                Collections.synchronizedList(new ArrayList<>()));

        // Ensure the list is large enough to hold the index
        synchronized (cacheList) {
            while (cacheList.size() <= index) {
                cacheList.add(null);
            }
            Boolean cached = cacheList.get(index);
            if (cached != null) {
                return cached;
            }
        }

        // Compute the result (heavy operation) and store it
        boolean result = CardQualityService.computeCardNeedsSorting(card, elementName);

        synchronized (cacheList) {
            cacheList.set(index, result);
        }
        return result;
    }

    public void dispose() {
        try {
            UserInterfaceFunctions.unregisterOwnedCollectionRefresher(this::refreshFromModel);
        } catch (Throwable ignored) {
        }
        // Add other cleanup here if needed (stop background tasks, remove listeners, etc.)
    }

    private void setupOuicheListButtons() {
        Button compactBtn = ouicheListTab.getCompactDetailedButton();
        Button mosaicBtn = ouicheListTab.getMosaicListButton();
        if (compactBtn == null || mosaicBtn == null) return;

        compactBtn.setOnAction(e -> {
            if ("Compact mode".equals(compactBtn.getText())) {
                // ---- Switch to Compact mode ----
                compactBtn.setText("Detailed mode");
                mosaicBtn.setVisible(true);
                mosaicBtn.setManaged(true);
                mosaicBtn.setText("Mosaic"); // always reset sub-toggle when entering compact
                try {
                    displayCompactOuicheList(false);
                } catch (Exception ex) {
                    logger.error("Error displaying compact OuicheList", ex);
                }
            } else {
                // ---- Switch back to Detailed mode ----
                compactBtn.setText("Compact mode");
                mosaicBtn.setVisible(false);
                mosaicBtn.setManaged(false);
                mosaicBtn.setText("Mosaic");
                try {
                    displayOuicheListUnified();
                } catch (Exception ex) {
                    logger.error("Error displaying detailed OuicheList", ex);
                }
            }
        });

        mosaicBtn.setOnAction(e -> {
            if ("Mosaic".equals(mosaicBtn.getText())) {
                mosaicBtn.setText("List");
                try {
                    displayCompactOuicheList(true);
                } catch (Exception ex) {
                    logger.error("Error switching compact OuicheList to mosaic", ex);
                }
            } else {
                mosaicBtn.setText("Mosaic");
                try {
                    displayCompactOuicheList(false);
                } catch (Exception ex) {
                    logger.error("Error switching compact OuicheList to list", ex);
                }
            }
        });
    }

    private void displayCompactOuicheList(boolean mosaicMode) throws Exception {
        AnchorPane contentPane = ouicheListTab.getContentPane();
        contentPane.getChildren().clear();

        // Ensure data is loaded
        if (Model.CardsLists.OuicheList.getMaOuicheList() == null) {
            UserInterfaceFunctions.generateOuicheListType();
        }

        // The OuicheList is already stored as unique-card → CardElement / count maps;
        // no local rebuilding needed.
        java.util.Map<String, CardElement> uniqueCards = Model.CardsLists.OuicheList.getMaOuicheList();
        java.util.Map<String, Integer> cardCounts = Model.CardsLists.OuicheList.getMaOuicheListCounts();

        if (uniqueCards == null || uniqueCards.isEmpty()) {
            javafx.scene.control.Label empty = new javafx.scene.control.Label("OuicheList is empty.");
            empty.setStyle("-fx-text-fill: white;");
            contentPane.getChildren().add(empty);
            return;
        }

        javafx.scene.Node content = mosaicMode
                ? buildCompactMosaicView(uniqueCards)
                : buildCompactListView(uniqueCards, cardCounts);

        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #100317; -fx-background: #100317;");
        scrollPane.setVbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);

        contentPane.getChildren().add(scrollPane);
        AnchorPane.setTopAnchor(scrollPane, 0.0);
        AnchorPane.setBottomAnchor(scrollPane, 0.0);
        AnchorPane.setLeftAnchor(scrollPane, 0.0);
        AnchorPane.setRightAnchor(scrollPane, 0.0);
    }

    /**
     * Builds the compact list view: one row per unique card, showing image + FR/EN/JA names
     * + exemplary count (top-right) + unit/total price (bottom-right), mirroring the HTML export format.
     */
    private javafx.scene.Node buildCompactListView(
            java.util.Map<String, CardElement> uniqueCards,
            java.util.Map<String, Integer> cardCounts) {

        VBox listBox = new VBox(6);
        listBox.setPadding(new Insets(10));
        listBox.setStyle("-fx-background-color: #100317;");

        final double IMG_W = 80.0;
        final double IMG_H = 116.0;

        for (java.util.Map.Entry<String, CardElement> entry : uniqueCards.entrySet()) {
            CardElement ce = entry.getValue();
            Card card = ce.getCard();
            int count = cardCounts.get(entry.getKey());

            // ---- Outer row ----
            HBox row = new HBox(10);
            row.setPadding(new Insets(8));
            row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            row.setStyle(
                    "-fx-border-color: white; -fx-border-width: 1; " +
                            "-fx-border-radius: 5; -fx-background-radius: 5; " +
                            "-fx-background-color: black;");

            // ---- Card image (left) ----
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView();
            iv.setFitWidth(IMG_W);
            iv.setFitHeight(IMG_H);
            iv.setPreserveRatio(true);
            loadCardImageInto(card, iv, IMG_W, IMG_H);

            // ---- Names (centre, takes remaining space) ----
            VBox namesBox = new VBox(4);
            HBox.setHgrow(namesBox, Priority.ALWAYS);
            namesBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            javafx.scene.control.Label frLabel = new javafx.scene.control.Label(
                    card.getName_FR() != null ? card.getName_FR() : "");
            javafx.scene.control.Label enLabel = new javafx.scene.control.Label(
                    card.getName_EN() != null ? card.getName_EN() : "");
            javafx.scene.control.Label jaLabel = new javafx.scene.control.Label(
                    card.getName_JA() != null ? card.getName_JA() : "");

            frLabel.setStyle("-fx-text-fill: white;       -fx-font-size: 13;");
            enLabel.setStyle("-fx-text-fill: white;     -fx-font-size: 13;");
            jaLabel.setStyle("-fx-text-fill: white;     -fx-font-size: 13;");
            frLabel.setWrapText(true);
            enLabel.setWrapText(true);
            jaLabel.setWrapText(true);

            namesBox.getChildren().addAll(frLabel, enLabel, jaLabel);

            // ---- Count + price (right, top-aligned) ----
            VBox valueBox = new VBox(4);
            valueBox.setAlignment(javafx.geometry.Pos.TOP_RIGHT);

            javafx.scene.control.Label countLabel = new javafx.scene.control.Label(
                    "\u00d7" + count); // × symbol
            countLabel.setStyle("-fx-text-fill: white; -fx-font-size: 14; -fx-font-weight: bold;");
            valueBox.getChildren().add(countLabel);

            if (card.getPrice() != null && !card.getPrice().trim().isEmpty()) {
                try {
                    float unitPrice = Float.parseFloat(card.getPrice());
                    float totalPrice = unitPrice * count;
                    javafx.scene.control.Label unitPriceLabel = new javafx.scene.control.Label(
                            String.format("%.2f\u20ac", unitPrice));
                    javafx.scene.control.Label totalPriceLabel = new javafx.scene.control.Label(
                            String.format("= %.2f\u20ac", totalPrice));
                    unitPriceLabel.setStyle("-fx-text-fill: white; -fx-font-size: 11; -fx-font-weight: bold;");
                    totalPriceLabel.setStyle("-fx-text-fill: white; -fx-font-size: 11; -fx-font-weight: bold;");
                    valueBox.getChildren().addAll(unitPriceLabel, totalPriceLabel);
                } catch (NumberFormatException ignored) {
                }
            }

            row.getChildren().addAll(iv, namesBox, valueBox);
            listBox.getChildren().add(row);
        }

        return listBox;
    }

    /**
     * Builds the compact mosaic view: one image per unique card, wrapped in a FlowPane.
     * No titles, no categories — pure image grid.
     */
    private javafx.scene.Node buildCompactMosaicView(
            java.util.Map<String, CardElement> uniqueCards) {

        javafx.scene.layout.FlowPane flow = new javafx.scene.layout.FlowPane();
        flow.setHgap(5);
        flow.setVgap(5);
        flow.setPadding(new Insets(10));
        flow.setStyle("-fx-background-color: #100317;");

        double cellW = cardWidthProperty.get();
        double cellH = cardHeightProperty.get();

        for (java.util.Map.Entry<String, CardElement> entry : uniqueCards.entrySet()) {
            Card card = entry.getValue().getCard();

            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView();
            iv.setFitWidth(cellW);
            iv.setFitHeight(cellH);
            iv.setPreserveRatio(true);
            loadCardImageInto(card, iv, cellW, cellH);

            javafx.scene.layout.StackPane wrapper = new javafx.scene.layout.StackPane(iv);
            wrapper.setPadding(new Insets(2));
            flow.getChildren().add(wrapper);
        }

        return flow;
    }

    /**
     * Loads a card image asynchronously into the given ImageView, using LruImageCache
     * and DataBaseUpdate exactly like the other cell renderers in the application.
     */
    private void loadCardImageInto(Card card, javafx.scene.image.ImageView iv,
                                   double fitW, double fitH) {
        if (card == null || card.getImagePath() == null) return;

        String imageKey = card.getImagePath();
        String[] addresses = Model.Database.DataBaseUpdate.getAddresses(imageKey + ".jpg");
        if (addresses == null || addresses.length == 0) return;

        final String resolvedPath = "file:" + addresses[0];

        // Try the LRU cache first (fast path, runs on FX thread)
        javafx.scene.image.Image cached = Utils.LruImageCache.getImage(resolvedPath);
        if (cached != null) {
            iv.setImage(cached);
            return;
        }

        // Background load so we never block the FX thread
        Thread loader = new Thread(() -> {
            try {
                javafx.scene.image.Image img =
                        new javafx.scene.image.Image(resolvedPath, fitW, fitH, true, true);
                Utils.LruImageCache.addImage(resolvedPath, img);
                javafx.application.Platform.runLater(() -> iv.setImage(img));
            } catch (Exception e) {
                logger.debug("loadCardImageInto: failed to load image for {}", resolvedPath, e);
            }
        }, "compact-ouiche-img-loader");
        loader.setDaemon(true);
        loader.start();
    }

    /**
     * Scrolls just enough so the bottom of the target group's cell (= the newly added card)
     * is visible. Does nothing if it is already visible or if the group is above the viewport.
     */
    private void scrollToLastCardInGroup(String handlerTarget) {
        if (myCollectionTreeView == null || handlerTarget == null) return;

        String[] parts = handlerTarget.split("\\s*/\\s*");
        TreeItem<String> root = myCollectionTreeView.getRoot();
        if (root == null) return;

        // Locate the group TreeItem
        TreeItem<String> target = findTreeItemByPath(root, parts, 0);
        if (target == null && parts.length > 1)
            target = findTreeItemByPath(root, new String[]{parts[parts.length - 1]}, 0);
        if (target == null)
            target = findTreeItemByPath(root, new String[]{parts[0]}, 0);
        if (target == null) return;

        // If we landed on a Box, descend to its first CardsGroup child
        if (target instanceof DataTreeItem
                && !(((DataTreeItem<?>) target).getData() instanceof CardsGroup)) {
            for (TreeItem<String> child : target.getChildren()) {
                if (child instanceof DataTreeItem
                        && ((DataTreeItem<?>) child).getData() instanceof CardsGroup) {
                    target = child;
                    break;
                }
            }
        }

        for (TreeItem<String> a = target.getParent(); a != null; a = a.getParent())
            a.setExpanded(true);

        final int targetRow = myCollectionTreeView.getRow(target);
        if (targetRow < 0) return;

        javafx.scene.control.skin.VirtualFlow<?> vf = getVirtualFlow();
        int firstVisible = vf != null && vf.getFirstVisibleCell() != null
                ? vf.getFirstVisibleCell().getIndex() : -1;
        int lastVisible = vf != null && vf.getLastVisibleCell() != null
                ? vf.getLastVisibleCell().getIndex() : -1;

        if (firstVisible >= 0 && targetRow >= firstVisible && targetRow <= lastVisible) {
            // Row is in viewport — check whether the cell bottom (last card) is visible
            adjustScrollToShowCellBottom(targetRow);
        } else if (lastVisible >= 0 && targetRow > lastVisible) {
            // Row is below the viewport — bring it in, then fine-tune to cell bottom
            myCollectionTreeView.scrollTo(targetRow);
            Platform.runLater(() -> adjustScrollToShowCellBottom(targetRow));
        }
        // Row above viewport → user scrolled away; don't disturb.
    }

    /**
     * Scrolls down by exactly the amount needed to reveal the bottom edge of the rendered
     * cell at {@code row}. No-ops if the bottom is already within the viewport.
     */
    private void adjustScrollToShowCellBottom(int row) {
        if (myCollectionTreeView == null) return;

        Bounds treeBounds = myCollectionTreeView.localToScene(myCollectionTreeView.getBoundsInLocal());
        if (treeBounds == null) return;
        double treeBottom = treeBounds.getMaxY();

        for (Node node : myCollectionTreeView.lookupAll(".card-tree-cell")) {
            if (!(node instanceof TreeCell)) continue;
            @SuppressWarnings("unchecked")
            TreeCell<String> cell = (TreeCell<String>) node;
            if (cell.isEmpty() || cell.getTreeItem() == null) continue;
            if (myCollectionTreeView.getRow(cell.getTreeItem()) != row) continue;

            Bounds cellBounds = cell.localToScene(cell.getBoundsInLocal());
            if (cellBounds == null) break;

            double cellBottom = cellBounds.getMaxY();
            if (cellBottom > treeBottom) {
                javafx.scene.control.skin.VirtualFlow<?> vf = getVirtualFlow();
                if (vf != null) vf.scrollPixels(cellBottom - treeBottom);
            }
            break;
        }
    }

    private javafx.scene.control.skin.VirtualFlow<?> getVirtualFlow() {
        if (myCollectionTreeView == null) return null;
        try {
            for (Node n : myCollectionTreeView.lookupAll(".virtual-flow")) {
                if (n instanceof javafx.scene.control.skin.VirtualFlow)
                    return (javafx.scene.control.skin.VirtualFlow<?>) n;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Returns the [0..1] scroll position of the Decks & Collections tree,
     * or -1 if the tree or its VirtualFlow is not yet available.
     */
    private double getDecksTreeScrollPosition() {
        if (decksAndCollectionsTreeView == null) return -1;
        try {
            for (Node n : decksAndCollectionsTreeView.lookupAll(".virtual-flow")) {
                if (n instanceof javafx.scene.control.skin.VirtualFlow) {
                    return ((javafx.scene.control.skin.VirtualFlow<?>) n).getPosition();
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    /**
     * Restores a previously captured [0..1] scroll position on the Decks &amp; Collections
     * tree. Must be called from the FX thread after the new tree has been laid out.
     */
    private void restoreDecksTreeScrollPosition(double pos) {
        if (pos < 0 || decksAndCollectionsTreeView == null) return;
        try {
            for (Node n : decksAndCollectionsTreeView.lookupAll(".virtual-flow")) {
                if (n instanceof javafx.scene.control.skin.VirtualFlow) {
                    ((javafx.scene.control.skin.VirtualFlow<?>) n).setPosition(pos);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * After a deck move, scrolls the Decks tree so the deck's node is visible,
     * and scrolls the nav menu so the deck's NavigationItem is visible.
     */
    private void scrollToMovedDeck(Object deckObj) {
        if (!(deckObj instanceof Deck)) return;
        Deck deck = (Deck) deckObj;
        String deckName = deck.getName() == null ? "" : deck.getName().replaceAll("[=\\-]", "");

        // ── Scroll the main tree ─────────────────────────────────────────────
        Platform.runLater(() -> {
            if (decksAndCollectionsTreeView == null) return;
            TreeItem<String> root = decksAndCollectionsTreeView.getRoot();
            if (root == null) return;
            TreeItem<String> target = findTreeItemByPath(root, new String[]{deckName}, 0);
            if (target == null) return;
            for (TreeItem<String> a = target.getParent(); a != null; a = a.getParent())
                a.setExpanded(true);
            Platform.runLater(() -> {
                int row = decksAndCollectionsTreeView.getRow(target);
                if (row >= 0) decksAndCollectionsTreeView.scrollTo(row);
            });
        });

        // ── Scroll + expand the nav menu ─────────────────────────────────────
        Platform.runLater(() -> {
            NavigationItem navItem = findNavItemInMenuVBox(decksTab.getMenuVBox(), deckObj);
            if (navItem == null) {
                // fall back to name-based search
                navItem = findNavItemByNameInMenuVBox(decksTab.getMenuVBox(), deckName);
            }
            if (navItem != null) {
                expandNavAncestors(navItem);
                scrollNavToItem(decksTab, navItem);
            }
        });
    }

    /**
     * Name-based fallback search through the nav menu when userData identity fails.
     */
    private NavigationItem findNavItemByNameInMenuVBox(VBox menuVBox, String name) {
        if (menuVBox == null || name == null) return null;
        for (javafx.scene.Node node : menuVBox.getChildren()) {
            NavigationItem found = findNavItemByNameInNode(node, name);
            if (found != null) return found;
        }
        return null;
    }

    private NavigationItem findNavItemByNameInNode(javafx.scene.Node node, String name) {
        if (node instanceof NavigationMenu) {
            for (NavigationItem item : ((NavigationMenu) node).getItems()) {
                NavigationItem found = findNavItemByNameInItem(item, name);
                if (found != null) return found;
            }
        } else if (node instanceof NavigationItem) {
            return findNavItemByNameInItem((NavigationItem) node, name);
        }
        return null;
    }

    private NavigationItem findNavItemByNameInItem(NavigationItem item, String name) {
        if (item == null) return null;
        if (item.getLabel() != null && name.equals(item.getLabel().getText())) return item;
        for (NavigationItem sub : item.getSubItems()) {
            NavigationItem found = findNavItemByNameInItem(sub, name);
            if (found != null) return found;
        }
        return null;
    }

    /**
     * Updates the text of the My Collection and Decks & Collections tabs
     * to show a "*" prefix when unsaved changes exist.
     */
    private void updateTabDirtyIndicators() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::updateTabDirtyIndicators);
            return;
        }
        if (mainTabPane == null) return;

        // My Collection
        if (myCollectionTabHandle != null) {
            boolean dirty = UserInterfaceFunctions.isMyCollectionDirty();
            myCollectionTabHandle.setText(dirty ? "* My Collection" : "My Collection");
            myCollectionTabHandle.setStyle(dirty ? "-fx-font-weight: bold;" : "");
        }

        // Decks and Collections
        if (decksTabHandle != null) {
            boolean dirty = UserInterfaceFunctions.isAnyDeckOrCollectionDirty();
            decksTabHandle.setText(dirty ? "* Decks and Collections" : "Decks and Collections");
            decksTabHandle.setStyle(dirty ? "-fx-font-weight: bold;" : "");
        }

        // OuicheList
        if (ouicheListTabHandle != null) {
            boolean dirty = UserInterfaceFunctions.isOuicheListDirty();
            ouicheListTabHandle.setText(dirty ? "* OuicheList" : "OuicheList");
            ouicheListTabHandle.setStyle(dirty ? "-fx-font-weight: bold;" : "");
        }
    }

    /**
     * Event FILTER (capture phase) for the middle-pane TreeViews.
     * During the capture phase the event is never "consumed" yet, so instead of
     * checking isConsumed() we walk up from the click target looking for a node
     * marked as a card wrapper. If we find one we leave the event alone; otherwise
     * we clear the selection.
     */
    private javafx.event.EventHandler<javafx.scene.input.MouseEvent> buildMiddlePaneEmptySpaceFilter() {
        return event -> {
            if (event.isControlDown() || event.isShiftDown()) return;
            // Walk up from the exact node that was clicked
            javafx.scene.Node current = (javafx.scene.Node) event.getTarget();
            while (current != null) {
                if (Boolean.TRUE.equals(current.getProperties().get("cardWrapper"))) {
                    return; // Landed on a card — let the card's own handler process it
                }
                if (current instanceof javafx.scene.control.TreeView) break;
                current = current.getParent();
            }
            // Did not land on any card wrapper → empty space click → clear selection
            Controller.SelectionManager.clearSelection();
        };
    }
// ── Keyboard shortcuts ─────────────────────────────────────────────────────────

    /**
     * Event HANDLER (bubble phase) for the right-pane ListViews.
     * Card cells call event.consume(), so by the time the event reaches the ListView
     * it is already consumed. Empty-space clicks are not consumed, so we clear here.
     */
    private javafx.event.EventHandler<javafx.scene.input.MouseEvent> buildRightPaneEmptySpaceClearHandler() {
        return event -> {
            if (!event.isConsumed() && !event.isControlDown() && !event.isShiftDown()) {
                Controller.SelectionManager.clearSelection();
            }
        };
    }

    private void setupGlobalKeyShortcuts() {
        // Register on the scene once it becomes available
        mainTabPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.addEventFilter(
                        javafx.scene.input.KeyEvent.KEY_PRESSED,
                        this::handleGlobalKeyShortcut);
            }
        });
    }

// ── ESC / Delete ───────────────────────────────────────────────────────────────

    private void handleGlobalKeyShortcut(javafx.scene.input.KeyEvent event) {
        // CTRL+S and CTRL+SHIFT+S must work even when a text field has focus.
        if (event.getCode() == javafx.scene.input.KeyCode.S && event.isControlDown()) {
            handleSaveShortcut(event.isShiftDown());
            event.consume();
            return;
        }

        // CTRL+Tab (next tab) and CTRL+SHIFT+Tab (previous tab).
        if (event.getCode() == javafx.scene.input.KeyCode.TAB && event.isControlDown()) {
            if (mainTabPane != null) {
                int total = mainTabPane.getTabs().size();
                if (total > 1) {
                    int current = mainTabPane.getSelectionModel().getSelectedIndex();
                    int next = event.isShiftDown()
                            ? (current - 1 + total) % total   // CTRL+SHIFT+Tab → previous
                            : (current + 1) % total;           // CTRL+Tab        → next
                    mainTabPane.getSelectionModel().select(next);
                }
            }
            event.consume();
            return;
        }

        if (event.getTarget() instanceof javafx.scene.control.TextInputControl) return;

        boolean middleSelectionActive =
                "MIDDLE".equals(Controller.SelectionManager.getActivePart())
                        && !Controller.SelectionManager.getSelectedMiddleElements().isEmpty();
        boolean anySelectionActive =
                !Controller.SelectionManager.getSelectedCards().isEmpty()
                        || !Controller.SelectionManager.getSelectedMiddleElements().isEmpty();

        switch (event.getCode()) {
            case ESCAPE:
                if (anySelectionActive) {
                    Controller.SelectionManager.clearSelection();
                    event.consume();
                }
                break;
            case DELETE:
                if (middleSelectionActive) {
                    handleDeleteMiddleSelection();
                    event.consume();
                }
                break;
            case C:
                if (event.isControlDown() && anySelectionActive) {
                    handleCopySelectionToClipboard();
                    event.consume();
                }
                break;
            case X:
                if (event.isControlDown() && middleSelectionActive) {
                    handleCutFromKeyboard();
                    event.consume();
                }
                break;
            case D:
                if (event.isControlDown() && middleSelectionActive) {
                    handleDuplicateMiddleSelection();
                    event.consume();
                }
                break;
            case V:
                if (event.isControlDown() && !Controller.CardClipboard.isEmpty()) {
                    handlePasteFromKeyboard();
                    event.consume();
                }
                break;
            case NUMPAD1:
                handleNumpadAddFromRightPane(1);
                event.consume();
                break;
            case NUMPAD2:
                handleNumpadAddFromRightPane(2);
                event.consume();
                break;
            case NUMPAD3:
                handleNumpadAddFromRightPane(3);
                event.consume();
                break;
            case NUMPAD4:
                handleNumpadAddFromRightPane(4);
                event.consume();
                break;
            case NUMPAD5:
                handleNumpadAddFromRightPane(5);
                event.consume();
                break;
            case NUMPAD6:
                handleNumpadAddFromRightPane(6);
                event.consume();
                break;
            case NUMPAD7:
                handleNumpadAddFromRightPane(7);
                event.consume();
                break;
            case NUMPAD8:
                handleNumpadAddFromRightPane(8);
                event.consume();
                break;
            case NUMPAD9:
                handleNumpadAddFromRightPane(9);
                event.consume();
                break;
            default:
                break;
        }
    }

// ── CTRL+S / CTRL+SHIFT+S ──────────────────────────────────────────────────────

    /**
     * Handles the CTRL+S (save active tab) and CTRL+SHIFT+S (save all tabs) shortcuts.
     * Each tab is only saved when it is actually dirty, to avoid unnecessary writes.
     *
     * @param saveAll {@code true} for CTRL+SHIFT+S (save every dirty tab),
     *                {@code false} for CTRL+S (save only the currently active tab).
     */
    private void handleSaveShortcut(boolean saveAll) {
        if (saveAll) {
            // ── CTRL+SHIFT+S: save every dirty tab ──────────────────────────
            boolean savedAny = false;
            try {
                if (UserInterfaceFunctions.isMyCollectionDirty()) {
                    UserInterfaceFunctions.saveMyCollection();
                    populateMyCollectionMenu();
                    savedAny = true;
                }
            } catch (Exception ex) {
                logger.error("CTRL+SHIFT+S: error saving My Collection", ex);
            }
            try {
                if (UserInterfaceFunctions.isAnyDeckOrCollectionDirty()) {
                    UserInterfaceFunctions.saveAllDecksAndCollections();
                    populateDecksAndCollectionsMenu();
                    savedAny = true;
                }
            } catch (Exception ex) {
                logger.error("CTRL+SHIFT+S: error saving Decks and Collections", ex);
            }
            try {
                if (UserInterfaceFunctions.isOuicheListDirty()) {
                    UserInterfaceFunctions.saveOuicheList();
                    savedAny = true;
                }
            } catch (Exception ex) {
                logger.error("CTRL+SHIFT+S: error saving OuicheList", ex);
            }
            if (savedAny) {
                updateTabDirtyIndicators();
            }
        } else {
            // ── CTRL+S: save only the active tab ────────────────────────────
            if (mainTabPane == null) return;
            int activeIndex = mainTabPane.getSelectionModel().getSelectedIndex();
            try {
                switch (activeIndex) {
                    case 0: // My Collection
                        if (UserInterfaceFunctions.isMyCollectionDirty()) {
                            UserInterfaceFunctions.saveMyCollection();
                            updateTabDirtyIndicators();
                            populateMyCollectionMenu();
                        }
                        break;
                    case 1: // Decks and Collections
                        if (UserInterfaceFunctions.isAnyDeckOrCollectionDirty()) {
                            UserInterfaceFunctions.saveAllDecksAndCollections();
                            updateTabDirtyIndicators();
                            populateDecksAndCollectionsMenu();
                        }
                        break;
                    case 2: // OuicheList
                        if (UserInterfaceFunctions.isOuicheListDirty()) {
                            UserInterfaceFunctions.saveOuicheList();
                            updateTabDirtyIndicators();
                        }
                        break;
                    default:
                        // Other tabs (Archetypes, Friends, Shops) have no save logic.
                        break;
                }
            } catch (Exception ex) {
                logger.error("CTRL+S: error saving tab {}", activeIndex, ex);
            }
        }
    }

// ── CTRL+C ─────────────────────────────────────────────────────────────────────

    public void handleDeleteMiddleSelection() {
        java.util.Set<Model.CardsLists.CardElement> selectedElements =
                Controller.SelectionManager.getSelectedMiddleElements();
        if (selectedElements.isEmpty()) return;

        if (selectedElements.size() > 10) {
            boolean confirmed = View.NavigationContextMenuBuilder.confirmWithCustomMessage(
                    "Delete " + selectedElements.size() + " cards?");
            if (!confirmed) return;
        }

        // When exactly one element is removed, try to find another element for the
        // same card in the tree so we can re-select it after the refresh.
        Model.CardsLists.Card cardToReselect = null;
        Model.CardsLists.CardElement elementBeingRemoved = null;
        if (selectedElements.size() == 1) {
            elementBeingRemoved = selectedElements.iterator().next();
            cardToReselect = elementBeingRemoved != null ? elementBeingRemoved.getCard() : null;
        }

        int activeTabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        if (activeTabIndex == 0) {
            Controller.MenuActionHandler.handleBulkRemoveFromOwnedCollection(
                    new ArrayList<>(selectedElements));
        } else if (activeTabIndex == 1) {
            Controller.MenuActionHandler.handleBulkRemoveElementsFromDecksAndCollections(
                    new ArrayList<>(Controller.SelectionManager.getSelectedMiddleElements()));
        }
        Controller.SelectionManager.clearSelection();

        // After the view refreshes, select the last remaining element for the
        // same card (if any).
        if (cardToReselect != null) {
            final Model.CardsLists.Card targetCard = cardToReselect;
            final Model.CardsLists.CardElement removedElement = elementBeingRemoved;
            javafx.application.Platform.runLater(() -> {
                TreeView<String> tv = getActiveMiddleTreeView();
                if (tv == null) return;
                List<Model.CardsLists.CardElement> allElements =
                        View.CardTreeCell.collectAllElementsInTreeOrder(tv.getRoot());
                // Find the last element (in display order) whose card matches,
                // excluding the removed element itself (it may still be in the
                // list momentarily if the model hasn't flushed yet).
                Model.CardsLists.CardElement candidate = null;
                for (Model.CardsLists.CardElement ce : allElements) {
                    if (ce == removedElement) continue;
                    Model.CardsLists.Card c = ce.getCard();
                    if (c == null) continue;
                    if (c == targetCard) {
                        candidate = ce;
                        continue;
                    }
                    // Fall back to identifier matching
                    if (targetCard.getPassCode() != null
                            && targetCard.getPassCode().equals(c.getPassCode())) {
                        candidate = ce;
                        continue;
                    }
                    if (targetCard.getPrintCode() != null
                            && targetCard.getPrintCode().equals(c.getPrintCode())) {
                        candidate = ce;
                        continue;
                    }
                    if (targetCard.getKonamiId() != null
                            && targetCard.getKonamiId().equals(c.getKonamiId())) {
                        candidate = ce;
                    }
                }
                if (candidate != null) {
                    Controller.SelectionManager.selectElement(candidate);
                }
            });
        }
    }

// ── CTRL+X ─────────────────────────────────────────────────────────────────────

    private void handleCopySelectionToClipboard() {
        if ("MIDDLE".equals(Controller.SelectionManager.getActivePart())) {
            java.util.List<Model.CardsLists.Card> cardsToCopy = new java.util.ArrayList<>();
            for (Model.CardsLists.CardElement element :
                    Controller.SelectionManager.getSelectedMiddleElements()) {
                if (element.getCard() != null) cardsToCopy.add(element.getCard());
            }
            if (!cardsToCopy.isEmpty()) Controller.CardClipboard.copyCards(cardsToCopy);
        } else {
            java.util.Set<Model.CardsLists.Card> selectedCards =
                    Controller.SelectionManager.getSelectedCards();
            if (!selectedCards.isEmpty())
                Controller.CardClipboard.copyCards(new ArrayList<>(selectedCards));
        }
    }

// ── CTRL+D ─────────────────────────────────────────────────────────────────────

    private void handleCutFromKeyboard() {
        handleCopySelectionToClipboard();
        handleDeleteMiddleSelection(); // already handles confirmation for >10 cards
    }

// ── CTRL+V ─────────────────────────────────────────────────────────────────────

    public void handleDuplicateMiddleSelection() {
        TreeView<String> activeTreeView = getActiveMiddleTreeView();
        if (activeTreeView == null) return;

        java.util.Set<Model.CardsLists.CardElement> selectedElements =
                Controller.SelectionManager.getSelectedMiddleElements();
        if (selectedElements.isEmpty()) return;

        // Get selected elements in tree display order
        List<Model.CardsLists.CardElement> allElementsInOrder =
                View.CardTreeCell.collectAllElementsInTreeOrder(activeTreeView.getRoot());
        List<Model.CardsLists.CardElement> selectedInOrder = allElementsInOrder.stream()
                .filter(selectedElements::contains)
                .collect(Collectors.toList());
        if (selectedInOrder.isEmpty()) return;

        Model.CardsLists.CardElement lastElement =
                selectedInOrder.get(selectedInOrder.size() - 1);
        List<Model.CardsLists.Card> cardsToInsert = selectedInOrder.stream()
                .map(Model.CardsLists.CardElement::getCard)
                .collect(Collectors.toList());

        boolean inserted = Controller.MenuActionHandler.handleInsertCardsAfterElement(
                cardsToInsert, lastElement);
        if (!inserted) {
            logger.warn("handleDuplicateMiddleSelection: insertion failed");
            return;
        }

        int activeTabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        if (activeTabIndex == 0) {
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshOwnedCollectionView();
        } else if (activeTabIndex == 1) {
            // Mark only the deck/collection that owns the last element
            Object owner = findDeckOrCollectionOwner(lastElement);
            if (owner != null) Controller.UserInterfaceFunctions.markDirty(owner);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
        }
    }

// ── Navigation-item drag-drop ──────────────────────────────────────────────

    private void handlePasteFromKeyboard() {
        if (Controller.CardClipboard.isEmpty()) return;
        List<Model.CardsLists.Card> clipboardCards = Controller.CardClipboard.getContents();

        // Priority 1: after the last element of the current MIDDLE selection
        if ("MIDDLE".equals(Controller.SelectionManager.getActivePart())
                && !Controller.SelectionManager.getSelectedMiddleElements().isEmpty()) {
            TreeView<String> activeTreeView = getActiveMiddleTreeView();
            if (activeTreeView != null) {
                List<Model.CardsLists.CardElement> allElementsInOrder =
                        View.CardTreeCell.collectAllElementsInTreeOrder(activeTreeView.getRoot());
                java.util.Set<Model.CardsLists.CardElement> selectedElements =
                        Controller.SelectionManager.getSelectedMiddleElements();
                // Walk backwards to find the last selected element in display order
                Model.CardsLists.CardElement lastElement = null;
                for (int i = allElementsInOrder.size() - 1; i >= 0; i--) {
                    if (selectedElements.contains(allElementsInOrder.get(i))) {
                        lastElement = allElementsInOrder.get(i);
                        break;
                    }
                }
                if (lastElement != null && pasteCardsAfterElement(clipboardCards, lastElement)) {
                    return;
                }
            }
        }

        // Priority 2: after the last MIDDLE element that was explicitly clicked
        Model.CardsLists.CardElement lastMiddleElement =
                Controller.SelectionManager.getLastMiddleElement();
        if (lastMiddleElement != null
                && pasteCardsAfterElement(clipboardCards, lastMiddleElement)) {
            return;
        }

        // Priority 3: into the last clicked navigation-menu item
        Object lastNavItem = Controller.SelectionManager.getLastClickedNavigationItem();
        if (lastNavItem != null) {
            pasteCardsIntoNavigationItem(clipboardCards, lastNavItem);
        }
    }

// ── Paste helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns the backing CardElement list for the given nav-item model object.
     * Used to locate newly appended elements after a paste so they can be re-selected.
     */
    private List<Model.CardsLists.CardElement> getTargetGroupElements(Object navItem) {
        if (navItem instanceof Model.CardsLists.CardsGroup) {
            List<Model.CardsLists.CardElement> list =
                    ((Model.CardsLists.CardsGroup) navItem).getCardList();
            return list != null ? list : Collections.emptyList();
        } else if (navItem instanceof Model.CardsLists.Box) {
            Model.CardsLists.CardsGroup dg =
                    Controller.MenuActionHandler.getOrCreateDefaultGroup((Model.CardsLists.Box) navItem);
            if (dg == null) return Collections.emptyList();
            List<Model.CardsLists.CardElement> list = dg.getCardList();
            return list != null ? list : Collections.emptyList();
        } else if (navItem instanceof Model.CardsLists.Deck) {
            List<Model.CardsLists.CardElement> list =
                    ((Model.CardsLists.Deck) navItem).getMainDeck();
            return list != null ? list : Collections.emptyList();
        } else if (navItem instanceof Model.CardsLists.ThemeCollection) {
            List<Model.CardsLists.CardElement> list =
                    ((Model.CardsLists.ThemeCollection) navItem).getCardsList();
            return list != null ? list : Collections.emptyList();
        }
        return Collections.emptyList();
    }

    /**
     * Attaches drag-over and drag-dropped handlers to a NavigationItem.
     *
     * <p>RIGHT → nav: add copies of the dragged Cards into the model object.
     * <p>MIDDLE → nav: move the dragged CardElements (remove from source, add to target).
     *
     * @param navItem  the NavigationItem node
     * @param modelObj the model object stored as userData (Box, CardsGroup, Deck, ThemeCollection)
     */
    private void attachNavItemDropHandlers(NavigationItem navItem, Object modelObj) {
        navItem.setOnDragOver(event -> {
            if (event.getDragboard().hasString()
                    && Controller.DragDropManager.getDragSourcePane() != null) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
            }
            event.consume();
        });

        navItem.setOnDragDropped(event -> {
            String srcPane = Controller.DragDropManager.getDragSourcePane();
            if (srcPane == null) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            doCardPasteOnNavItem(modelObj, srcPane, event);
            event.setDropCompleted(true);
            event.consume();
        });
    }

    /**
     * Shared card-paste logic (RIGHT / MIDDLE pane drops).
     * Extracted so both {@link #attachNavItemDropHandlers} and the nav-DnD combined
     * handler can call it without duplication.
     */
    private void doCardPasteOnNavItem(Object modelObj, String srcPane,
                                      javafx.scene.input.DragEvent event) {
        if ("RIGHT".equals(srcPane)) {
            java.util.List<Model.CardsLists.Card> cards =
                    new java.util.ArrayList<>(Controller.DragDropManager.getDraggedCards());
            if (!cards.isEmpty()) pasteCardsIntoNavigationItem(cards, modelObj);

        } else if ("MIDDLE".equals(srcPane)) {
            java.util.List<Model.CardsLists.CardElement> elements =
                    new java.util.ArrayList<>(Controller.DragDropManager.getDraggedElements());
            if (elements.isEmpty()) {
                event.setDropCompleted(false);
                return;
            }
            java.util.List<Model.CardsLists.Card> cards = new java.util.ArrayList<>();
            for (Model.CardsLists.CardElement ce : elements) {
                if (ce.getCard() != null) cards.add(ce.getCard());
            }
            Controller.MenuActionHandler.handleBulkRemoveElementsFromDecksAndCollections(elements);
            Controller.MenuActionHandler.handleBulkRemoveFromOwnedCollection(elements);
            if (!cards.isEmpty()) {
                final int n = cards.size();
                pasteCardsIntoNavigationItem(cards, modelObj);
                Platform.runLater(() -> {
                    List<Model.CardsLists.CardElement> targetElements =
                            getTargetGroupElements(modelObj);
                    int startIdx = Math.max(0, targetElements.size() - n);
                    Controller.SelectionManager.clearSelection();
                    for (int i = startIdx; i < targetElements.size(); i++) {
                        Controller.SelectionManager.toggleElementSelection(targetElements.get(i));
                    }
                    View.CardTreeCell.refreshAllGridViews();
                });
            }
        }
    }

    /**
     * Makes {@code navItem} a drag SOURCE for navigation-menu reordering.
     * Attaches {@code onDragDetected} (on the label) and {@code onDragDone}.
     * Call this for every nav item that should be draggable.
     */
    private void enableNavDragSource(NavigationItem navItem, Object modelObj) {
        javafx.scene.control.Label lbl = navItem.getLabel();
        lbl.setOnDragDetected(event -> {
            javafx.scene.input.Dragboard db =
                    lbl.startDragAndDrop(javafx.scene.input.TransferMode.MOVE);
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString("NAV");
            db.setContent(cc);
            Controller.DragDropManager.startNavDrag(modelObj);
            event.consume();
        });
        lbl.setOnDragDone(event -> {
            Controller.DragDropManager.clearCurrentlyDraggedCard();
            navItem.clearDropIndicator();
            event.consume();
        });
    }

    private void populateDecksAndCollectionsMenu() throws Exception {
        VBox menuVBox = decksTab.getMenuVBox();

        // Snapshot expanded state of every existing nav item before the rebuild,
        // keyed by the model object stored in userData.
        java.util.Map<Object, Boolean> expandedState = new java.util.IdentityHashMap<>();
        for (javafx.scene.Node node : menuVBox.getChildren()) {
            if (node instanceof NavigationMenu) {
                for (NavigationItem item : ((NavigationMenu) node).getItems()) {
                    captureExpandedState(item, expandedState);
                }
            } else if (node instanceof NavigationItem) {
                captureExpandedState((NavigationItem) node, expandedState);
            }
        }

        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        if (UserInterfaceFunctions.getDecksList() == null) {
            UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
        }
        DecksAndCollectionsList decksCollection = UserInterfaceFunctions.getDecksList();

        if (decksCollection != null) {
            if (decksCollection.getCollections() != null) {
                for (ThemeCollection collection : decksCollection.getCollections()) {
                    NavigationItem collectionNavItem = createNavigationItem(collection.getName(), 0);
                    collectionNavItem.setUserData(collection);
                    collectionNavItem.setItemType(NavigationItem.ItemType.COLLECTION);
                    attachNavItemDropHandlers(collectionNavItem, collection);
                    enableNavItemAsNavDndTarget(collectionNavItem, collection,
                            (dragged, pos) -> handleDecksNavDrop(dragged, collection, pos, decksCollection));
                    // Collections are not draggable (no reordering of collections via DnD, per spec)

                    boolean isDirty = UserInterfaceFunctions.isDirty(collection);
                    boolean isLoose = Boolean.TRUE.equals(collection.getConnectToWholeCollection());

                    if (isDirty) {
                        collectionNavItem.getLabel().setText("* " + CardNameUtils.sanitize(collection.getName()));
                    }
                    // Set base style: bold always, italic for loose collections.
                    // Color is controlled solely by applyNavigationItemHighlight — no yellow for dirty.
                    String labelStyle = "-fx-font-weight: bold;";
                    if (isLoose) labelStyle += " -fx-font-style: italic;";
                    collectionNavItem.getLabel().setStyle(labelStyle);

                    boolean hasMissing = collectionHasMissing(collection);
                    boolean hasMissingArtwork = collectionHasMissingArtworks(collection);
                    boolean highlight = hasMissing || hasMissingArtwork;
                    String highlightMsg;
                    if (hasMissing && hasMissingArtwork) {
                        highlightMsg = "This collection contains missing archetype cards and cards with missing artwork variants.";
                    } else if (hasMissing) {
                        highlightMsg = "This collection contains missing archetype cards.";
                    } else {
                        highlightMsg = "This collection contains cards with missing artwork variants.";
                    }
                    applyNavigationItemHighlight(collectionNavItem, highlight, highlightMsg);

                    // navigation wiring (unchanged)
                    collectionNavItem.setOnLabelClicked(evt -> navigateToTree(decksAndCollectionsTreeView, collection.getName()));

                    // Restore expanded state from before the rebuild; default to false
                    // for items that are new (not previously in the menu).
                    boolean wasExpanded = expandedState.getOrDefault(collection, false);
                    collectionNavItem.setExpanded(wasExpanded);

                    // --- NEW: context menu for Collection items ---
                    {
                        ContextMenu collCm = NavigationContextMenuBuilder.forDecksCollection(collection, decksCollection);
                        collectionNavItem.setOnContextMenuRequested(e -> {
                            collCm.show(collectionNavItem, e.getScreenX(), e.getScreenY());
                            e.consume();
                        });
                    }

                    navigationMenu.addItem(collectionNavItem);

                    if (collection.getLinkedDecks() != null) {
                        boolean firstNonNullUnit = true;
                        int unitIdx = -1; // tracks the index of the last emitted non-empty unit
                        for (List<Deck> unit : collection.getLinkedDecks()) {
                            if (unit == null || unit.isEmpty()) continue;
                            unitIdx++;
                            // Insert a separator between consecutive deck lists (not before the first one)
                            if (!firstNonNullUnit) {
                                final int afterIdx = unitIdx - 1;
                                javafx.scene.layout.HBox sep = collectionNavItem.addDeckListSeparator();
                                wireSeparatorAsDropTarget(sep, collection, afterIdx, decksCollection);
                            }
                            firstNonNullUnit = false;
                            for (Deck linkedDeck : unit) {
                                if (linkedDeck == null) continue;
                                NavigationItem deckSubItem = createNavigationItem(linkedDeck.getName(), 1);
                                deckSubItem.setUserData(linkedDeck);
                                deckSubItem.setItemType(NavigationItem.ItemType.DECK);
                                attachNavItemDropHandlers(deckSubItem, linkedDeck);
                                enableNavDragSource(deckSubItem, linkedDeck);
                                enableNavItemAsNavDndTarget(deckSubItem, linkedDeck,
                                        (dragged, pos) -> handleDecksNavDrop(dragged, linkedDeck, pos, decksCollection));
                                if (UserInterfaceFunctions.isDirty(linkedDeck)) {
                                    deckSubItem.getLabel().setText("* " + CardNameUtils.sanitize(linkedDeck.getName()));
                                }

                                // --- NEW: context menu for Deck items (inside a Collection) ---
                                {
                                    ContextMenu deckCm = NavigationContextMenuBuilder.forDecksDeck(linkedDeck, decksCollection);
                                    deckSubItem.setOnContextMenuRequested(e -> {
                                        deckCm.show(deckSubItem, e.getScreenX(), e.getScreenY());
                                        e.consume();
                                    });
                                }

                                collectionNavItem.addSubItem(deckSubItem);
                                // Linked deck items
                                deckSubItem.setOnLabelClicked(evt -> {
                                    Controller.SelectionManager.setLastClickedNavigationItem(linkedDeck);
                                    navigateToTree(decksAndCollectionsTreeView, collection.getName(), "Decks", linkedDeck.getName());
                                });
                            }
                        }
                    }

                    // Collection items
                    collectionNavItem.setOnLabelClicked(evt -> {
                        Controller.SelectionManager.setLastClickedNavigationItem(collection);
                        navigateToTree(decksAndCollectionsTreeView, collection.getName());
                    });
                }
            }

            if (decksCollection.getDecks() != null) {
                for (Deck deck : decksCollection.getDecks()) {
                    NavigationItem navItem = createNavigationItem(deck.getName(), 0);
                    navItem.setUserData(deck);
                    navItem.setItemType(NavigationItem.ItemType.DECK);
                    attachNavItemDropHandlers(navItem, deck);
                    enableNavDragSource(navItem, deck);
                    enableNavItemAsNavDndTarget(navItem, deck,
                            (dragged, pos) -> handleDecksNavDrop(dragged, deck, pos, decksCollection));
                    if (UserInterfaceFunctions.isDirty(deck)) {
                        navItem.getLabel().setText("* " + CardNameUtils.sanitize(deck.getName()));
                    }

                    // navigation wiring (unchanged)
                    navItem.setOnLabelClicked(evt -> navigateToTree(decksAndCollectionsTreeView, deck.getName()));

                    // --- NEW: context menu for standalone Deck items ---
                    {
                        ContextMenu deckCm = NavigationContextMenuBuilder.forDecksDeck(deck, decksCollection);
                        navItem.setOnContextMenuRequested(e -> {
                            deckCm.show(navItem, e.getScreenX(), e.getScreenY());
                            e.consume();
                        });
                    }

                    navigationMenu.addItem(navItem);
                    // Standalone deck items
                    navItem.setOnLabelClicked(evt -> {
                        Controller.SelectionManager.setLastClickedNavigationItem(deck);
                        navigateToTree(decksAndCollectionsTreeView, deck.getName());
                    });
                }
            }
        } else {
            Label errorLabel = new Label("No Decks and Collections loaded.");
            errorLabel.setStyle("-fx-text-fill: white;");
            navigationMenu.addItem(new NavigationItem(errorLabel.getText(), 0));
        }

        menuVBox.getChildren().add(navigationMenu);

        // --- empty-area context menu ---
        ContextMenu emptyCm = NavigationContextMenuBuilder.forDecksEmpty();
        menuVBox.setOnContextMenuRequested(e -> {
            emptyCm.show(menuVBox, e.getScreenX(), e.getScreenY());
        });
    }

    /**
     * Replaces the drag-over / drag-dropped handlers on {@code navItem} with a
     * combined handler that accepts both card drops (RIGHT / MIDDLE pane) AND
     * navigation-menu reordering drops (NAV pane).
     *
     * <p>For NAV drops, the {@code onNavDrop} callback receives the dragged model
     * object and the resolved {@link View.NavigationItem.DropPosition}.  The callback
     * is responsible for mutating the model and triggering a refresh.
     *
     * <p>Must be called <em>after</em> {@link #attachNavItemDropHandlers} because it
     * overrides the handlers that method sets.
     *
     * @param navItem    the navigation item to configure as a drop target
     * @param modelObj   the model object attached to {@code navItem} (for card drops)
     * @param onNavDrop  callback for NAV drops: {@code (draggedObject, dropPosition)}
     */
    private void enableNavItemAsNavDndTarget(
            NavigationItem navItem,
            Object modelObj,
            java.util.function.BiConsumer<Object, View.NavigationItem.DropPosition> onNavDrop) {

        navItem.setOnDragOver(event -> {
            String src = Controller.DragDropManager.getDragSourcePane();
            if (event.getDragboard().hasString() && src != null) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.MOVE);
                if ("NAV".equals(src)) {
                    // Show drop indicator based on cursor Y within the header row.
                    // Use a wide INTO zone when the target can contain children (Box).
                    boolean isContainer = modelObj instanceof Model.CardsLists.Box;
                    View.NavigationItem.DropPosition pos =
                            resolveDropPosition(navItem, event.getY(), isContainer);
                    navItem.showDropIndicator(pos);
                }
            }
            event.consume();
        });

        navItem.setOnDragExited(event -> {
            navItem.clearDropIndicator();
            event.consume();
        });

        navItem.setOnDragDropped(event -> {
            String srcPane = Controller.DragDropManager.getDragSourcePane();
            if (srcPane == null) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            if ("NAV".equals(srcPane)) {
                Object dragged = Controller.DragDropManager.getDraggedNavObject();
                if (dragged != null && dragged != modelObj) {
                    boolean isContainer = modelObj instanceof Model.CardsLists.Box;
                    View.NavigationItem.DropPosition pos =
                            resolveDropPosition(navItem, event.getY(), isContainer);
                    navItem.clearDropIndicator();
                    onNavDrop.accept(dragged, pos);
                }
            } else {
                doCardPasteOnNavItem(modelObj, srcPane, event);
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }

    /**
     * Inserts clipboardCards after targetElement.
     * Returns true if the insertion succeeded; marks dirty and refreshes only on success.
     */
    private boolean pasteCardsAfterElement(
            List<Model.CardsLists.Card> clipboardCards,
            Model.CardsLists.CardElement targetElement) {
        boolean inserted = Controller.MenuActionHandler.handleInsertCardsAfterElement(
                clipboardCards, targetElement);
        if (!inserted) return false;

        int activeTabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        if (activeTabIndex == 0) {
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshOwnedCollectionView();
        } else if (activeTabIndex == 1) {
            // Mark only the deck/collection that owns the target element
            Object owner = findDeckOrCollectionOwner(targetElement);
            if (owner != null) Controller.UserInterfaceFunctions.markDirty(owner);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
        }
        return true;
    }

    /**
     * Returns the selected CardElements ordered by their position in the tree.
     */
    private List<Model.CardsLists.CardElement> getSelectedElementsInDisplayOrder(
            TreeView<String> treeView) {
        if (treeView == null
                || Controller.SelectionManager.getSelectedMiddleElements().isEmpty()) {
            return new ArrayList<>();
        }
        java.util.Set<Model.CardsLists.CardElement> selectedElements =
                Controller.SelectionManager.getSelectedMiddleElements();
        return View.CardTreeCell.collectAllElementsInTreeOrder(treeView.getRoot())
                .stream()
                .filter(selectedElements::contains)
                .collect(Collectors.toList());
    }

// ── Shared utilities ───────────────────────────────────────────────────────────

    private void pasteCardsIntoNavigationItem(
            List<Model.CardsLists.Card> clipboardCards, Object navItem) {
        if (navItem instanceof Model.CardsLists.Box) {
            Model.CardsLists.Box box = (Model.CardsLists.Box) navItem;
            Model.CardsLists.CardsGroup defaultGroup =
                    Controller.MenuActionHandler.getOrCreateDefaultGroup(box);
            if (defaultGroup == null) return;
            javafx.collections.ObservableList<Model.CardsLists.CardElement> observableList =
                    View.CardTreeCell.observableListFor(defaultGroup);
            for (Model.CardsLists.Card card : clipboardCards) {
                if (card != null) observableList.add(new Model.CardsLists.CardElement(card));
            }
            View.CardTreeCell.triggerHeightAdjustment(defaultGroup);
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshOwnedCollectionView();

        } else if (navItem instanceof Model.CardsLists.CardsGroup) {
            Model.CardsLists.CardsGroup group = (Model.CardsLists.CardsGroup) navItem;
            javafx.collections.ObservableList<Model.CardsLists.CardElement> observableList =
                    View.CardTreeCell.observableListFor(group);
            for (Model.CardsLists.Card card : clipboardCards) {
                if (card != null) observableList.add(new Model.CardsLists.CardElement(card));
            }
            View.CardTreeCell.triggerHeightAdjustment(group);
            Controller.UserInterfaceFunctions.markMyCollectionDirty();
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            Controller.UserInterfaceFunctions.refreshOwnedCollectionView();

        } else if (navItem instanceof Model.CardsLists.Deck) {
            Model.CardsLists.Deck deck = (Model.CardsLists.Deck) navItem;
            if (deck.getMainDeck() == null) deck.setMainDeck(new ArrayList<>());
            if (deck.getExtraDeck() == null) deck.setExtraDeck(new ArrayList<>());

            // Split cards by deck section: extra-deck monsters → Extra Deck,
            // everything else → Main Deck.
            java.util.List<Model.CardsLists.Card> toMain = new java.util.ArrayList<>();
            java.util.List<Model.CardsLists.Card> toExtra = new java.util.ArrayList<>();
            for (Model.CardsLists.Card card : clipboardCards) {
                if (card == null) continue;
                if (Utils.DeckCompatibility.isExtraDeckCard(card)) toExtra.add(card);
                else toMain.add(card);
            }

            java.util.function.BiConsumer<java.util.List<Model.CardsLists.Card>, String>
                    addToSection = (cards, sectionKey) -> {
                if (cards.isEmpty()) return;
                CardsGroup sectionGroup =
                        CardTreeCell.getDeckSectionGroup(deck, sectionKey);
                if (sectionGroup != null) {
                    javafx.collections.ObservableList<Model.CardsLists.CardElement> obs =
                            CardTreeCell.observableListFor(sectionGroup);
                    for (Model.CardsLists.Card card : cards)
                        obs.add(new Model.CardsLists.CardElement(card));
                    CardTreeCell.triggerHeightAdjustment(sectionGroup);
                } else {
                    java.util.List<Model.CardsLists.CardElement> rawList =
                            "extra".equals(sectionKey)
                                    ? deck.getExtraDeck()
                                    : deck.getMainDeck();
                    for (Model.CardsLists.Card card : cards)
                        rawList.add(new Model.CardsLists.CardElement(card));
                    Controller.UserInterfaceFunctions.triggerDecksStructureRefresh();
                }
            };

            addToSection.accept(toMain, "main");
            addToSection.accept(toExtra, "extra");

            Controller.UserInterfaceFunctions.markDirty(deck);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();

        } else if (navItem instanceof Model.CardsLists.ThemeCollection) {
            Model.CardsLists.ThemeCollection collection =
                    (Model.CardsLists.ThemeCollection) navItem;
            if (collection.getCardsList() == null)
                collection.setCardsList(new ArrayList<>());

            CardsGroup cardsGroup = CardTreeCell.getCollectionCardsGroup(collection);
            if (cardsGroup != null) {
                javafx.collections.ObservableList<Model.CardsLists.CardElement> obs =
                        CardTreeCell.observableListFor(cardsGroup);
                for (Model.CardsLists.Card card : clipboardCards) {
                    if (card != null) obs.add(new Model.CardsLists.CardElement(card));
                }
                CardTreeCell.triggerHeightAdjustment(cardsGroup);
            } else {
                // Tree not built yet for this collection — raw add + structural rebuild.
                for (Model.CardsLists.Card card : clipboardCards) {
                    if (card != null)
                        collection.getCardsList().add(new Model.CardsLists.CardElement(card));
                }
                Controller.UserInterfaceFunctions.triggerDecksStructureRefresh();
            }
            Controller.UserInterfaceFunctions.markDirty(collection);
            Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
            // No refreshDecksAndCollectionsView() call needed: the ObservableList
            // listener handles the tree-node visibility; triggerHeightAdjustment
            // handles the GridView size.
        }
    }

    /**
     * Given a CardElement that lives inside a Deck or ThemeCollection,
     * returns the owning Deck or ThemeCollection, or null if not found.
     * Used to mark only the affected object dirty instead of everything.
     */
    private Object findDeckOrCollectionOwner(Model.CardsLists.CardElement elem) {
        if (elem == null) return null;
        Model.CardsLists.DecksAndCollectionsList dac =
                Controller.UserInterfaceFunctions.getDecksList();
        if (dac == null) return null;

        // Search ThemeCollections
        if (dac.getCollections() != null) {
            for (Model.CardsLists.ThemeCollection col : dac.getCollections()) {
                if (col.getCardsList() != null && col.getCardsList().contains(elem))
                    return col;
                // Collections can also contain Decks
                if (col.getLinkedDecks() != null) {
                    for (List<Model.CardsLists.Deck> ld : col.getLinkedDecks()) {
                        for (Model.CardsLists.Deck d : ld) {
                            if (containsElementInDeck(d, elem)) return d;
                        }
                    }
                }
            }
        }

        // Search standalone Decks
        if (dac.getDecks() != null) {
            for (Model.CardsLists.Deck d : dac.getDecks()) {
                if (containsElementInDeck(d, elem)) return d;
            }
        }

        return null;
    }

    /**
     * Returns true if elem is in any card list of this deck.
     */
    private boolean containsElementInDeck(Model.CardsLists.Deck d,
                                          Model.CardsLists.CardElement elem) {
        if (d == null) return false;
        if (d.getMainDeck() != null && d.getMainDeck().contains(elem)) return true;
        if (d.getExtraDeck() != null && d.getExtraDeck().contains(elem)) return true;
        if (d.getSideDeck() != null && d.getSideDeck().contains(elem)) return true;
        return false;
    }

    private TreeView<String> getActiveMiddleTreeView() {
        if (mainTabPane == null) return null;
        int activeTabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
        switch (activeTabIndex) {
            case 0:
                return myCollectionTreeView;
            case 1:
                return decksAndCollectionsTreeView;
            case 2:
                return ouicheTreeView;
            case 3:
                return archetypesTreeView;
            default:
                return null;
        }
    }

    /**
     * Returns only the selected cards, filtered and ordered by their position in the tree.
     */
    private List<Model.CardsLists.Card> getSelectedCardsInDisplayOrder(
            TreeView<String> treeView) {
        if (treeView == null || Controller.SelectionManager.getSelectedCards().isEmpty())
            return new ArrayList<>();
        java.util.Set<Model.CardsLists.Card> selectedCards =
                Controller.SelectionManager.getSelectedCards();
        List<Model.CardsLists.Card> allCardsInTreeOrder =
                View.CardTreeCell.collectAllCardsInTreeOrder(treeView.getRoot());
        return allCardsInTreeOrder.stream()
                .filter(selectedCards::contains)
                .collect(Collectors.toList());
    }

    /**
     * Finds the first CardElement that matches the given Card, searching first the
     * owned collection and then the Decks & Collections list.
     */
    private Model.CardsLists.CardElement findCardElementForCard(Model.CardsLists.Card card) {
        if (card == null) return null;
        // Try owned collection
        List<Model.CardsLists.CardElement> ownedMatches =
                Controller.MenuActionHandler.findCardElementsForCards(
                        java.util.Collections.singletonList(card));
        if (!ownedMatches.isEmpty()) return ownedMatches.get(0);
        // Try D&C
        Model.CardsLists.DecksAndCollectionsList dac =
                Controller.UserInterfaceFunctions.getDecksList();
        if (dac != null) {
            Model.CardsLists.CardElement dacMatch = findCardElementInDac(card, dac);
            if (dacMatch != null) return dacMatch;
        }
        return null;
    }

    private Model.CardsLists.CardElement findCardElementInDac(
            Model.CardsLists.Card card, Model.CardsLists.DecksAndCollectionsList dac) {
        if (dac.getCollections() != null) {
            for (Model.CardsLists.ThemeCollection tc : dac.getCollections()) {
                if (tc == null) continue;
                if (tc.getCardsList() != null) {
                    for (Model.CardsLists.CardElement ce : tc.getCardsList()) {
                        if (ce != null && CardMatcher.cardsMatch(ce.getCard(), card)) return ce;
                    }
                }
                if (tc.getLinkedDecks() != null) {
                    for (List<Model.CardsLists.Deck> unit : tc.getLinkedDecks()) {
                        if (unit == null) continue;
                        for (Model.CardsLists.Deck deck : unit) {
                            Model.CardsLists.CardElement found =
                                    findCardElementInDeckLists(card, deck);
                            if (found != null) return found;
                        }
                    }
                }
            }
        }
        if (dac.getDecks() != null) {
            for (Model.CardsLists.Deck deck : dac.getDecks()) {
                Model.CardsLists.CardElement found = findCardElementInDeckLists(card, deck);
                if (found != null) return found;
            }
        }
        return null;
    }

    private Model.CardsLists.CardElement findCardElementInDeckLists(
            Model.CardsLists.Card card, Model.CardsLists.Deck deck) {
        if (deck == null) return null;
        for (List<Model.CardsLists.CardElement> deckList : java.util.Arrays.asList(
                deck.getMainDeck(), deck.getExtraDeck(), deck.getSideDeck())) {
            if (deckList == null) continue;
            for (Model.CardsLists.CardElement ce : deckList) {
                if (ce != null && CardMatcher.cardsMatch(ce.getCard(), card)) return ce;
            }
        }
        return null;
    }

    /**
     * Returns {@code true} when {@code card} satisfies all active constraints
     * expressed in the given {@link FilterPane.FilterPageState}.
     *
     * <p>Only the fields that are implemented so far are checked here.
     * Add further field checks as the rest of the filter UI is wired up.
     */
// ── Numpad / Enter add ──────────────────────────────────────────────────────
    private void handleNumpadAddFromRightPane(int count) {
        int tabIdx = mainTabPane.getSelectionModel().getSelectedIndex();
        if (tabIdx != 0 && tabIdx != 1) return;
        List<Card> sourceCards = getNumpadSourceCards();
        if (sourceCards.isEmpty()) return;
        List<Card> toInsert = new ArrayList<>();
        for (Card c : sourceCards)
            for (int i = 0; i < count; i++)
                toInsert.add(c);
        insertCardsAtNumpadTarget(toInsert);
    }

    private void handleEnterAddFromRightPane() {
        int tabIdx = mainTabPane.getSelectionModel().getSelectedIndex();
        if (tabIdx != 0 && tabIdx != 1) return;
        List<Card> displayed = getDisplayedRightPaneCards();
        if (displayed.size() != 1) return;
        List<Card> toInsert = new ArrayList<>();
        toInsert.add(displayed.get(0));
        insertCardsAtNumpadTarget(toInsert);
    }

    private boolean navItemBelongsToActiveTab(Object navItem) {
        if (navItem == null) return false;
        int tabIdx = mainTabPane.getSelectionModel().getSelectedIndex();
        if (tabIdx == 0)
            return navItem instanceof Model.CardsLists.Box
                    || navItem instanceof Model.CardsLists.CardsGroup;
        if (tabIdx == 1)
            return navItem instanceof Model.CardsLists.Deck
                    || navItem instanceof Model.CardsLists.ThemeCollection;
        return false;
    }

    private boolean elemBelongsToActiveTab(Model.CardsLists.CardElement elem) {
        if (elem == null) return false;
        TreeView<String> tv = getActiveMiddleTreeView();
        if (tv == null || tv.getRoot() == null) return false;
        return View.CardTreeCell.collectAllElementsInTreeOrder(tv.getRoot()).contains(elem);
    }

    private void insertCardsAtNumpadTarget(List<Card> toInsert) {
        int totalInserted = toInsert.size();

        Model.CardsLists.CardElement targetElem = getNumpadTargetMiddleElement();
        if (targetElem != null && pasteCardsAfterElement(toInsert, targetElem)) {
            selectLastInsertedElement(targetElem, totalInserted);
            return;
        }

        Object navItem = Controller.SelectionManager.getLastClickedNavigationItem();
        if (navItem != null && navItemBelongsToActiveTab(navItem)) {
            List<Model.CardsLists.CardElement> backing = getTargetGroupElements(navItem);
            pasteCardsIntoNavigationItem(toInsert, navItem);
            if (!backing.isEmpty())
                Controller.SelectionManager.selectElement(backing.get(backing.size() - 1));
            return;
        }

        TreeView<String> tv = getActiveMiddleTreeView();
        if (tv == null || tv.getRoot() == null) return;
        List<Model.CardsLists.CardElement> allElems =
                View.CardTreeCell.collectAllElementsInTreeOrder(tv.getRoot());
        if (!allElems.isEmpty()) {
            Model.CardsLists.CardElement lastElem = allElems.get(allElems.size() - 1);
            if (pasteCardsAfterElement(toInsert, lastElem)) {
                selectLastInsertedElement(lastElem, totalInserted);
                return;
            }
        }

        Object lastNavModel = findLastNavModelInTree(tv);
        if (lastNavModel != null) {
            List<Model.CardsLists.CardElement> backing = getTargetGroupElements(lastNavModel);
            pasteCardsIntoNavigationItem(toInsert, lastNavModel);
            if (!backing.isEmpty())
                Controller.SelectionManager.selectElement(backing.get(backing.size() - 1));
        }
    }

    private Object findLastNavModelInTree(TreeView<String> tv) {
        if (tv == null || tv.getRoot() == null) return null;
        Object candidate = null;
        java.util.Queue<TreeItem<String>> queue = new java.util.LinkedList<>();
        queue.add(tv.getRoot());
        while (!queue.isEmpty()) {
            TreeItem<String> item = queue.poll();
            if (item.getGraphic() instanceof NavigationItem) {
                Object modelObj = ((NavigationItem) item.getGraphic()).getUserData();
                if (modelObj != null && !getTargetGroupElements(modelObj).isEmpty())
                    candidate = modelObj;
            }
            queue.addAll(item.getChildren());
        }
        return candidate;
    }

    private void selectLastInsertedElement(Model.CardsLists.CardElement anchor, int count) {
        TreeView<String> tv = getActiveMiddleTreeView();
        if (tv == null) return;
        List<Model.CardsLists.CardElement> allElems =
                View.CardTreeCell.collectAllElementsInTreeOrder(tv.getRoot());
        int anchorIdx = allElems.indexOf(anchor);
        if (anchorIdx < 0) return;
        int lastIdx = Math.min(anchorIdx + count, allElems.size() - 1);
        Controller.SelectionManager.selectElement(allElems.get(lastIdx));
    }

    @SuppressWarnings("unchecked")
    private List<Card> getNumpadSourceCards() {
        List<Card> displayed = getDisplayedRightPaneCards();
        java.util.Set<Card> selected = Controller.SelectionManager.getSelectedCards();
        if (!selected.isEmpty()) {
            List<Card> visible = displayed.stream()
                    .filter(selected::contains)
                    .collect(Collectors.toList());
            if (!visible.isEmpty()) return visible;
        }
        if (displayed.size() == 1) return new ArrayList<>(displayed);
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<Card> getDisplayedRightPaneCards() {
        if (cardsDisplayContainer == null) return Collections.emptyList();
        for (javafx.scene.Node node : cardsDisplayContainer.getChildren()) {
            if (node instanceof javafx.scene.control.ListView) {
                if (!isMosaicMode) {
                    javafx.scene.control.ListView<Card> lv =
                            (javafx.scene.control.ListView<Card>) node;
                    return new ArrayList<>(lv.getItems());
                } else {
                    javafx.scene.control.ListView<List<Card>> lv =
                            (javafx.scene.control.ListView<List<Card>>) node;
                    List<Card> all = new ArrayList<>();
                    for (List<Card> row : lv.getItems()) all.addAll(row);
                    return all;
                }
            }
        }
        return Collections.emptyList();
    }

    private Model.CardsLists.CardElement getNumpadTargetMiddleElement() {
        if ("MIDDLE".equals(Controller.SelectionManager.getActivePart())
                && !Controller.SelectionManager.getSelectedMiddleElements().isEmpty()) {
            TreeView<String> tv = getActiveMiddleTreeView();
            if (tv != null) {
                List<Model.CardsLists.CardElement> allElems =
                        View.CardTreeCell.collectAllElementsInTreeOrder(tv.getRoot());
                java.util.Set<Model.CardsLists.CardElement> sel =
                        Controller.SelectionManager.getSelectedMiddleElements();
                for (int i = allElems.size() - 1; i >= 0; i--)
                    if (sel.contains(allElems.get(i))) return allElems.get(i);
            }
        }
        Model.CardsLists.CardElement last =
                Controller.SelectionManager.getLastMiddleElement();
        return elemBelongsToActiveTab(last) ? last : null;
    }

    private boolean matchesPageFilter(Card card, FilterPane.FilterPageState ps) {
        if (card == null || ps == null) return true;

        // ── Name filter ──────────────────────────────────────────────────
        String nameFilter = ps.name.toLowerCase().trim();
        if (!nameFilter.isEmpty()) {
            boolean matchesName =
                    (card.getName_EN() != null
                            && card.getName_EN().toLowerCase().contains(nameFilter))
                            || (card.getName_FR() != null
                            && card.getName_FR().toLowerCase().contains(nameFilter))
                            || (card.getName_JA() != null
                            && card.getName_JA().toLowerCase().contains(nameFilter));
            if (!matchesName) return false;
        }

        // ── PrintCode / PassCode filter ──────────────────────────────────
        // In Printed mode we match printCode; in Unique mode we match passCode.
        String codeFilter = ps.printCode.toLowerCase().trim();
        if (!codeFilter.isEmpty()) {
            boolean matchesCode = isPrintedMode
                    ? (card.getPrintCode() != null
                    && card.getPrintCode().toLowerCase().contains(codeFilter))
                    : (card.getPassCode() != null
                    && card.getPassCode().toLowerCase().contains(codeFilter));
            if (!matchesCode) return false;
        }

        // ── PassCode filter ──────────────────────────────────────────────
        String passCodeFilter = ps.passCode.toLowerCase().trim();
        if (!passCodeFilter.isEmpty()) {
            boolean matches = card.getPassCode() != null
                    && card.getPassCode().toLowerCase().contains(passCodeFilter);
            if (!matches) return false;
        }

        // ── Konami ID filter ─────────────────────────────────────────────
        String konamiIdFilter = ps.konamiId.toLowerCase().trim();
        if (!konamiIdFilter.isEmpty()) {
            boolean matches = card.getKonamiId() != null
                    && card.getKonamiId().toLowerCase().contains(konamiIdFilter);
            if (!matches) return false;
        }

        // ── Effect / Description filter ──────────────────────────────────
        String effectFilter = ps.effect.toLowerCase().trim();
        if (!effectFilter.isEmpty()) {
            boolean matches = card.getDescription() != null
                    && card.getDescription().toLowerCase().contains(effectFilter);
            if (!matches) return false;
        }

        // ── Category (card type) filter ──────────────────────────────────
        if (!"(All)".equals(ps.cardType)) {
            if (card.getCardType() == null
                    || !card.getCardType().contains(ps.cardType)) return false;
        }

        // ── Subtype filter ───────────────────────────────────────────────────
        // cardSubtypes is a Set; empty means "(All)" — no filter applied.
        // When non-empty, the card must match ALL selected subtypes (AND logic).
        if (!ps.cardSubtypes.isEmpty()) {
            if (card.getCardProperties() == null) return false;
            for (String sub : ps.cardSubtypes) {
                if (!card.getCardProperties().contains(sub)) return false;
            }
        }

        // Monster-only fields: only applied when the category is explicitly "Monster".
        // When "(All)", Spell, or Trap is selected, these fields are grayed in the UI
        // and their stored values must be ignored here too.
        if ("Monster".equals(ps.cardType) || "(All)".equals(ps.cardType)) {

            // ── Attribute filter ─────────────────────────────────────────────
            if (!"(All)".equals(ps.attribute)) {
                if (card.getAttribute() == null
                        || !card.getAttribute().equalsIgnoreCase(ps.attribute)) return false;
            }

            // ── Type (monster type) filter ─────────────────────────────────────
            if (!"(All)".equals(ps.type)) {
                if (card.getCardProperties() == null
                        || !card.getCardProperties().contains(ps.type)) return false;
            }

            // ── ATK filter ───────────────────────────────────────────────────
            if (!matchesIntField(ps.atk, card.getAtk())) return false;

            // ── DEF filter ───────────────────────────────────────────────────
            if (!matchesIntField(ps.def, card.getDef())) return false;

            // ── Level / Rank / Link filter ───────────────────────────────────
            if (!ps.level.isEmpty()) {
                boolean matchesLvRnkLnk =
                        matchesIntField(ps.level, card.getLevel())
                                || matchesIntField(ps.level, card.getRank())
                                || matchesIntField(ps.level, card.getLinkVal());
                if (!matchesLvRnkLnk) return false;
            }

            // ── Scale filter (Pendulum subtype only) ─────────────────────────
            // Apply scale filter when no subtype selected (all monsters) or Pendulum is among selections.
            if (ps.cardSubtypes.isEmpty() || ps.cardSubtypes.contains("Pendulum")) {
                if (!matchesIntField(ps.scale, card.getScale())) return false;
            }

        } // end Monster-only block

        // ── Link Marker filter ───────────────────────────────────────────────
        if (!"Spell".equals(ps.cardType) && !"Trap".equals(ps.cardType)) {
            if (ps.linkMarkers != null && !ps.linkMarkers.isEmpty()) {
                java.util.List<String> cardMarkers = card.getLinkMarker();
                if (cardMarkers == null) return false;
                for (String marker : ps.linkMarkers) {
                    if (!cardMarkers.contains(marker)) return false;
                }
            }
        }

        // ── Word Count filter ──────────────────────────────────────────────
        if (!ps.wordCount.isEmpty()) {
            String desc = card.getDescription();
            int wc = (desc == null || desc.isBlank()) ? 0
                    : desc.trim().split("\\s+").length;
            if (!matchesIntField(ps.wordCount, wc)) return false;
        }

        // ── Price filter ─────────────────────────────────────────────────
        // card.getPrice() is a String (euros); matchesDoubleField handles
        // exact values and "lo-hi" ranges with decimal support.
        if (!ps.price.isEmpty()) {
            if (!matchesDoubleField(ps.price, card.getPrice())) return false;
        }

        // ── Archetype filter ──────────────────────────────────────────────
// Uses SubListCreator.archetypesCardsLists directly — the same
// source used for the HTML exports — so membership is always
// consistent, regardless of whether UpdateCardArchetypes() has run
// or whether the API-provided archetypes match the JSON names.
        if (!ps.archetype.isBlank() && !"(All)".equals(ps.archetype)) {
            List<String> archetypeNames =
                    Model.CardsLists.SubListCreator.archetypesList;
            List<List<Card>> archetypeCardLists =
                    Model.CardsLists.SubListCreator.archetypesCardsLists;

            if (archetypeNames == null || archetypeNames.isEmpty()) return false;

            // Find the index of the selected archetype (case-insensitive)
            int idx = -1;
            for (int i = 0; i < archetypeNames.size(); i++) {
                if (ps.archetype.equalsIgnoreCase(archetypeNames.get(i))) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0 || idx >= archetypeCardLists.size()) return false;

            List<Card> members = archetypeCardLists.get(idx);
            if (members == null) return false;

            // Match by konamiId first, passCode as fallback
            String cardKid = card.getKonamiId();
            String cardPc = card.getPassCode();
            boolean isMember = false;
            for (Card member : members) {
                if (member == null) continue;
                if (cardKid != null && cardKid.equals(member.getKonamiId())) {
                    isMember = true;
                    break;
                }
                if (cardPc != null && cardPc.equals(member.getPassCode())) {
                    isMember = true;
                    break;
                }
            }
            if (!isMember) return false;
        }

        // ── TODO: add further field checks as they are implemented ───────
        // if (!"(All)".equals(ps.attribute)) { ... }
        // if (!"(All)".equals(ps.cardType))  { ... }
        // if (!ps.atk.isEmpty())             { ... }
        // etc.

        return true;
    }

    /**
     * Matches a single integer card stat against a filter string.
     * Formats: empty (skip), "?" (unknown=-1), "N" (exact), "A-B" (range inclusive).
     */
    private boolean matchesIntField(String filter, int cardVal) {
        if (filter == null) return true;
        filter = filter.trim();
        if (filter.isEmpty()) return true;
        if (filter.equals("?")) return cardVal == -1;
        int dashIdx = filter.indexOf('-', 1); // skip index 0 to ignore leading minus
        if (dashIdx > 0) {
            try {
                int lo = Integer.parseInt(filter.substring(0, dashIdx).trim());
                int hi = Integer.parseInt(filter.substring(dashIdx + 1).trim());
                if (lo > hi) {
                    int tmp = lo;
                    lo = hi;
                    hi = tmp;
                }
                if (cardVal == -1) return false;
                return cardVal >= lo && cardVal <= hi;
            } catch (NumberFormatException ignored) {
            }
        }
        try {
            return cardVal == Integer.parseInt(filter);
        } catch (NumberFormatException e) {
            String displayVal = (cardVal == -1) ? "?" : String.valueOf(cardVal);
            return displayVal.toLowerCase().contains(filter.toLowerCase());
        }
    }

    /**
     * Matches a card's price (stored as a String) against a filter string.
     *
     * <p>Supported formats — identical to {@link #matchesIntField} except values
     * are parsed as doubles so decimal prices like "0.50" and ranges like
     * "0.5-5.0" work correctly:</p>
     * <ul>
     *   <li>empty → skip (always passes)</li>
     *   <li>{@code "?"} → card price is null / blank / unparseable</li>
     *   <li>{@code "N"} or {@code "N.N"} → exact match</li>
     *   <li>{@code "A-B"} → inclusive range (both ends may be decimals)</li>
     * </ul>
     * Commas are normalised to dots before parsing so both {@code "1,50"} and
     * {@code "1.50"} are accepted.
     */
    private boolean matchesDoubleField(String filter, String cardPriceStr) {
        if (filter == null) return true;
        filter = filter.trim();
        if (filter.isEmpty()) return true;

        // Parse the card price; null / blank / unparseable → -1 (treated as unknown)
        double cardVal;
        if (cardPriceStr == null || cardPriceStr.isBlank()) {
            cardVal = -1;
        } else {
            try {
                cardVal = Double.parseDouble(cardPriceStr.replace(',', '.').trim());
            } catch (NumberFormatException e) {
                cardVal = -1;
            }
        }

        if (filter.equals("?")) return cardVal == -1;

        // Range "A-B": skip index 0 so a leading minus on A is not mistaken for the separator
        int dashIdx = filter.indexOf('-', 1);
        if (dashIdx > 0) {
            try {
                double lo = Double.parseDouble(filter.substring(0, dashIdx).replace(',', '.').trim());
                double hi = Double.parseDouble(filter.substring(dashIdx + 1).replace(',', '.').trim());
                if (lo > hi) {
                    double tmp = lo;
                    lo = hi;
                    hi = tmp;
                }
                if (cardVal == -1) return false;
                return cardVal >= lo && cardVal <= hi;
            } catch (NumberFormatException ignored) {
            }
        }

        // Exact value
        try {
            double target = Double.parseDouble(filter.replace(',', '.'));
            if (cardVal == -1) return false;
            return cardVal == target;
        } catch (NumberFormatException e) {
            // Partial-string fallback (e.g. user typed "0.5" while price is "0.50")
            String display = (cardPriceStr == null) ? "" : cardPriceStr.trim();
            return display.toLowerCase().contains(filter.toLowerCase());
        }
    }

    @FXML
    private void initialize() {
        UserInterfaceFunctions.readPathsFromFile();

        try {
            Map<Integer, Card> allCards = Model.Database.Database.getAllCardsList();
            if (allCards != null && !allCards.isEmpty()) {
                SubListCreator.CreateArchetypeLists(allCards);
                // Enrich every card's archetype list with ALL archetypes it belongs to,
                // fixing the bug where secondary archetypes hid the primary one.
                SubListCreator.UpdateCardArchetypes();
                logger.info("SubListCreator archetypes loaded: names={}, lists={}",
                        SubListCreator.archetypesList == null ? 0 : SubListCreator.archetypesList.size(),
                        SubListCreator.archetypesCardsLists == null ? 0 : SubListCreator.archetypesCardsLists.size());
            } else {
                logger.info("Database.getAllCardsList() returned empty or null; archetypes not initialized now.");
            }
        } catch (Exception e) {
            logger.warn("Failed to initialize SubListCreator archetypes at startup", e);
        }

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

        // ── Wire CardDetailPane action buttons ───────────────────────────────
        for (View.SharedCollectionTab tab :
                java.util.List.of(myCollectionTab, decksTab, ouicheListTab)) {
            View.CardDetailPane cdp = tab.getCardDetailPane();
            cdp.setOnMinusOne(this::handleDeleteMiddleSelection);
            cdp.setOnPlusOne(this::handleDuplicateMiddleSelection);
            cdp.setOnEdit(() -> {
                java.util.Set<Model.CardsLists.CardElement> sel =
                        Controller.SelectionManager.getSelectedMiddleElements();
                if (sel.isEmpty()) return;
                Model.CardsLists.CardElement element = sel.iterator().next();
                View.CardEditPopup popup = new View.CardEditPopup(element);
                popup.setOnOk(() -> {
                    Controller.UserInterfaceFunctions.refreshOwnedCollectionView();
                    Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
                });
                popup.showCenteredOn(cdp);
            });
        }

        // Tag the archetypes tree view so cells can identify it without relying
        // on which tab is currently selected.
        if (archetypesTreeView != null)
            archetypesTreeView.getProperties().put("tabType", "ARCHETYPES");

        // Lightweight D&C tree refresh: re-renders archetype-card glow states
        // inside Collections without rebuilding the model. Fired on every model
        // change via triggerTabDirtyIndicatorUpdate().
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

        if (mainTabPane != null && mainTabPane.getTabs().size() >= 6) {
            mainTabPane.getTabs().get(0).setContent(myCollectionTab);
            mainTabPane.getTabs().get(1).setContent(decksTab);
            mainTabPane.getTabs().get(2).setContent(ouicheListTab);
            mainTabPane.getTabs().get(3).setContent(archetypesTab);
            mainTabPane.getTabs().get(4).setContent(friendsTab);
            mainTabPane.getTabs().get(5).setContent(shopsTab);

            // cardsDisplayContainer holds the live cards list/mosaic view (shared across tabs)
            cardsDisplayContainer = new AnchorPane();
            cardsDisplayContainer.setStyle("-fx-background-color: #100317;");

            // List/Mosaic and Printed/Unique toggle buttons — shown above the cards display
            listMosaicButton = new Button("List");
            listMosaicButton.getStyleClass().add("small-button");
            // Inline style wins over all CSS rules regardless of specificity.
            // top=4 bottom=8 shifts the label up so "q" in "Unique" is never clipped.
            listMosaicButton.setStyle("-fx-font-size: 12px; -fx-padding: 3 3 7 3;");
            listMosaicButton.setOnAction(e -> {
                isMosaicMode = !isMosaicMode;
                listMosaicButton.setText(isMosaicMode ? "List" : "Mosaic");
                updateCardsDisplay();
            });
            printedUniqueButton = new Button("Printed");
            printedUniqueButton.getStyleClass().add("small-button");
            printedUniqueButton.setStyle("-fx-font-size: 12px; -fx-padding: 3 3 7 3;");
            printedUniqueButton.setOnAction(e -> {
                isPrintedMode = !isPrintedMode;
                printedUniqueButton.setText(isPrintedMode ? "Unique" : "Printed");
                updateCardsDisplay();
            });

            // ── Sort toggle buttons ────────────────────────────────────────────
            sortAzButton = new Button("AZ");
            sortAtkButton = new Button("ATK↓");
            sortDefButton = new Button("DEF↓");
            sortLvlButton = new Button("LVL↓");
            sortAzButton.getStyleClass().add("sort-button");
            sortAtkButton.getStyleClass().add("sort-button");
            sortDefButton.getStyleClass().add("sort-button");
            sortLvlButton.getStyleClass().add("sort-button");

            // ATK↓ is selected by default
            applySortButtonSelectedStyle(sortAtkButton);
            applySortButtonUnselectedStyle(sortAzButton);
            applySortButtonUnselectedStyle(sortDefButton);
            applySortButtonUnselectedStyle(sortLvlButton);

            sortAzButton.setOnAction(e -> {
                if (activeSortButtonIndex == 0) {
                    // Already selected → toggle AZ ↔ ZA
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

            sortAtkButton.setOnAction(e -> {
                if (activeSortButtonIndex == 1) {
                    // Already selected → toggle ATK↓ ↔ ATK↑
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

            sortDefButton.setOnAction(e -> {
                if (activeSortButtonIndex == 2) {
                    // Already selected → toggle DEF↓ ↔ DEF↑
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

            sortLvlButton.setOnAction(e -> {
                if (activeSortButtonIndex == 3) {
                    // Already selected → toggle LVL↓ ↔ LVL↑
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
            injectSharedRightPanel(myCollectionTab);
            // Pre-wire the remaining tabs so their FilterPanes have listeners attached
            injectSharedRightPanel(decksTab);
            injectSharedRightPanel(ouicheListTab);
            injectSharedRightPanel(archetypesTab);
            injectSharedRightPanel(friendsTab);
            injectSharedRightPanel(shopsTab);
            // Re-inject myCollectionTab so its cards container is placed back correctly
            injectSharedRightPanel(myCollectionTab);

            // Store tab handles for dirty-indicator updates
            if (mainTabPane != null && mainTabPane.getTabs().size() >= 2) {
                myCollectionTabHandle = mainTabPane.getTabs().get(0);
                decksTabHandle = mainTabPane.getTabs().get(1);
                ouicheListTabHandle = mainTabPane.getTabs().get(2);
            }
            UserInterfaceFunctions.registerTabDirtyIndicatorUpdater(this::updateTabDirtyIndicators);
        }

        // Wire My Collection save button
        if (myCollectionTab.getSaveButton() != null) {
            myCollectionTab.getSaveButton().setOnAction(e -> {
                try {
                    UserInterfaceFunctions.saveMyCollection();
                    updateTabDirtyIndicators();
                    populateMyCollectionMenu();         // refresh nav to remove "*" markers
                } catch (Exception ex) {
                    logger.error("Error saving My Collection", ex);
                }
            });
        }

        // Wire "Mark incomplete cards" toggle button
        if (myCollectionTab.getIncompleteMarkButton() != null) {
            Button incBtn = myCollectionTab.getIncompleteMarkButton();
            // incompleteMarkingEnabled defaults to true, so initialise to the ON style.
            incBtn.setStyle(
                    "-fx-background-color: #cdfc04;" +
                            "-fx-text-fill: black;" +
                            "-fx-border-color: #cdfc04;" +
                            "-fx-border-width: 1;" +
                            "-fx-border-radius: 4;" +
                            "-fx-background-radius: 4;" +
                            "-fx-font-size: 12px;" +
                            "-fx-padding: 4 10 4 10;" +
                            "-fx-cursor: hand;");
            incBtn.setOnAction(e -> {
                boolean nowEnabled = !View.CardTreeCell.isIncompleteMarkingEnabled();
                View.CardTreeCell.setIncompleteMarkingEnabled(nowEnabled);

                // ON  → green-yellow fill, black text
                // OFF → dark background, green-yellow border/text
                if (nowEnabled) {
                    incBtn.setStyle(
                            "-fx-background-color: #cdfc04;" +
                                    "-fx-text-fill: black;" +
                                    "-fx-border-color: #cdfc04;" +
                                    "-fx-border-width: 1;" +
                                    "-fx-border-radius: 4;" +
                                    "-fx-background-radius: 4;" +
                                    "-fx-font-size: 12px;" +
                                    "-fx-padding: 4 10 4 10;" +
                                    "-fx-cursor: hand;");
                } else {
                    incBtn.setStyle(
                            "-fx-background-color: #100317;" +
                                    "-fx-text-fill: #cdfc04;" +
                                    "-fx-border-color: #cdfc04;" +
                                    "-fx-border-width: 1;" +
                                    "-fx-border-radius: 4;" +
                                    "-fx-background-radius: 4;" +
                                    "-fx-font-size: 12px;" +
                                    "-fx-padding: 4 10 4 10;" +
                                    "-fx-cursor: hand;");
                }

                // Rebuild the nav menu so highlight states update immediately,
                // then force all GridViews to re-render so glow states update.
                try {
                    populateMyCollectionMenu();
                } catch (Exception ex) {
                    logger.error("Error refreshing My Collection menu after incomplete-mark toggle", ex);
                }
                if (myCollectionTreeView != null) {
                    myCollectionTreeView.refresh();
                    View.CardTreeCell.refreshAllGridViews();
                }
            });
        }

        // Wire Decks and Collections save button
        if (decksTab.getSaveButton() != null) {
            decksTab.getSaveButton().setOnAction(e -> {
                try {
                    UserInterfaceFunctions.saveAllDecksAndCollections();
                    updateTabDirtyIndicators();
                    populateDecksAndCollectionsMenu();  // refresh nav to remove "*" markers
                } catch (Exception ex) {
                    logger.error("Error saving Decks and Collections", ex);
                }
            });
        }

        // Wire OuicheList save button
        if (ouicheListTab.getSaveButton() != null) {
            ouicheListTab.getSaveButton().setOnAction(e -> {
                try {
                    UserInterfaceFunctions.saveOuicheList();
                    updateTabDirtyIndicators();
                } catch (Exception ex) {
                    logger.error("Error saving OuicheList", ex);
                }
            });
        }

        try {
            displayMyCollection();
            populateMyCollectionMenu();
            UserInterfaceFunctions.registerOwnedCollectionRefresher(() -> {
                try {
                    String target = MenuActionHandler.getAndClearLastAddedTarget();
                    populateMyCollectionMenu();
                    // Refresh cells in-place: preserves scroll position and does NOT rebuild the tree.
                    // "Move to..." goes through here with target == null → no scroll ever triggered.
                    if (myCollectionTreeView != null) {
                        myCollectionTreeView.refresh();
                    }
                    // Scroll to the newly added card only for "Add to..." actions.
                    if (target != null) {
                        scrollToNewCardInGroup(target);
                    }
                    // ── Dirty indicator ──
                    updateTabDirtyIndicators();
                } catch (Exception e) {
                    logger.debug("My Collection refresher failed", e);
                }
            });
            UserInterfaceFunctions.registerOwnedCollectionStructureRefresher(() -> {
                try {
                    displayMyCollection();
                    populateMyCollectionMenu();
                    Object renameTarget = UserInterfaceFunctions.getAndClearPendingRenameTarget();
                    if (renameTarget != null) {
                        final Object finalTarget = renameTarget;
                        Platform.runLater(() -> {
                            logger.debug("Pending rename: searching nav for target={}", finalTarget);
                            NavigationItem toRename = findNavItemInMenuVBox(
                                    myCollectionTab.getMenuVBox(), finalTarget);
                            if (toRename != null) {
                                logger.debug("Pending rename: found NavigationItem '{}', starting inline rename",
                                        toRename.getLabel().getText());
                                // Expand parent (e.g. Box that contains the new Category)
                                expandNavAncestors(toRename);
                                // Scroll nav menu so the rename field is visible
                                scrollNavToItem(myCollectionTab, toRename);
                                startAddRename(toRename, finalTarget);
                            } else {
                                logger.warn("Pending rename: NavigationItem not found for target={}", finalTarget);
                            }
                        });
                    }
                } catch (Exception e) {
                    logger.debug("My Collection structure refresher failed", e);
                }
            });
        } catch (Exception ex) {
            logger.error("Error displaying My Collection", ex);
        }

        if (mainTabPane != null) {
            mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                int selectedIndex = mainTabPane.getTabs().indexOf(newTab);
                // Move the shared right panel (filter + cards display) to the newly active tab
                injectSharedRightPanel(getSharedTabAt(selectedIndex));
                // Re-evaluate the middle-pane filter for the newly active tab so its pages
                // (which may differ from the previous tab's) are applied correctly.
                updateMiddlePaneDisplay();
                if (selectedIndex == 1) {
                    try {
                        populateDecksAndCollectionsMenu();
                        UserInterfaceFunctions.registerDecksCollectionsRefresher(() -> {
                            try {
                                String cardTarget = MenuActionHandler.getAndClearLastDecksAddedTarget();
                                Object deckMoveTarget = UserInterfaceFunctions.getAndClearPendingDecksScrollTarget();
                                Object[] createCollData = UserInterfaceFunctions.getAndClearPendingDecksCreateCollectionData();
                                Object renameTarget = UserInterfaceFunctions.getAndClearPendingDecksRenameTarget();
                                boolean needsFullRebuild = UserInterfaceFunctions.getAndClearPendingDecksFullRebuild();
                                Object expandTarget = UserInterfaceFunctions.getAndClearPendingDecksExpandTarget();

                                populateDecksAndCollectionsMenu();

                                boolean isStructuralChange = (deckMoveTarget != null) || (createCollData != null)
                                        || (renameTarget != null) || needsFullRebuild;
                                if (isStructuralChange || cardTarget != null) {
                                    // Snapshot scroll position before the rebuild so we can
                                    // restore it afterwards (rebuild creates a new TreeView).
                                    final double savedScroll = getDecksTreeScrollPosition();
                                    displayDecksAndCollections();
                                    // Restore after the new tree has been laid out.
                                    Platform.runLater(() -> restoreDecksTreeScrollPosition(savedScroll));
                                } else {
                                    if (decksAndCollectionsTreeView != null) {
                                        decksAndCollectionsTreeView.refresh();
                                        View.CardTreeCell.refreshAllGridViews();
                                    }
                                }

// Scroll content tree to where a card was added
                                if (cardTarget != null) {
                                    scrollToTargetInDecksTree(cardTarget);
                                }

                                // Scroll / expand nav to a moved deck
                                if (deckMoveTarget != null) {
                                    scrollToMovedDeck(deckMoveTarget);
                                }

                                // "Create Collection from Deck" rename flow
                                if (createCollData != null && createCollData.length == 2
                                        && createCollData[0] instanceof ThemeCollection
                                        && createCollData[1] instanceof Deck) {

                                    final ThemeCollection newColl = (ThemeCollection) createCollData[0];
                                    final Deck movedDeck = (Deck) createCollData[1];

                                    Platform.runLater(() -> {
                                        NavigationItem toRename = findNavItemInMenuVBox(
                                                decksTab.getMenuVBox(), newColl);
                                        if (toRename != null) {
                                            toRename.setExpanded(true);
                                            expandNavAncestors(toRename);
                                            scrollNavToItem(decksTab, toRename);
                                            startDecksCreateCollectionRename(toRename, newColl, movedDeck);
                                        } else {
                                            logger.warn("Create-Collection rename: NavigationItem not found for {}",
                                                    newColl.getName());
                                        }
                                    });
                                }

                                // Normal add/rename for newly created Deck or Collection
                                if (renameTarget != null) {
                                    final Object finalTarget = renameTarget;
                                    Platform.runLater(() -> {
                                        NavigationItem toRename = findNavItemInMenuVBox(
                                                decksTab.getMenuVBox(), finalTarget);
                                        if (toRename != null) {
                                            expandNavAncestors(toRename);
                                            scrollNavToItem(decksTab, toRename);
                                            startDecksAddRename(toRename, finalTarget);
                                        } else {
                                            logger.warn("Pending decks rename: NavigationItem not found for target={}",
                                                    finalTarget);
                                        }
                                    });
                                }

                                // Expand the nav item for a collection that was just renamed after creation
                                if (expandTarget != null) {
                                    final Object finalExpand = expandTarget;
                                    Platform.runLater(() -> {
                                        NavigationItem toExpand = findNavItemInMenuVBox(
                                                decksTab.getMenuVBox(), finalExpand);
                                        if (toExpand != null) {
                                            toExpand.setExpanded(true);
                                            expandNavAncestors(toExpand);
                                            scrollNavToItem(decksTab, toExpand);
                                        }
                                    });
                                }

                                updateTabDirtyIndicators();
                                // Update dirty indicators
                                updateTabDirtyIndicators();
                            } catch (Exception e) {
                                logger.debug("Decks refresher failed", e);
                            }
                        });
                        displayDecksAndCollections();
                    } catch (Exception e) {
                        logger.error("Error displaying decks and collections", e);
                    }
                } else if (selectedIndex == 2 && !ouicheListLoaded) {
                    try {
                        UserInterfaceFunctions.generateOuicheList();
                        displayOuicheListUnified();
                        populateOuicheListMenu();
                        ouicheListLoaded = true;
                    } catch (Exception ex) {
                        logger.error("Error displaying OuicheList", ex);
                    }
                } else if (selectedIndex == 3) {
                    try {
                        displayArchetypes();
                        populateArchetypesMenu();
                    } catch (Exception ex) {
                        logger.error("Error displaying Archetypes", ex);
                    }
                }
            });
        }

        // Wire OuicheList compact/detailed toggle buttons
        setupOuicheListButtons();

        decksTab.setOnDecksLoad(() -> {
            try {
                displayDecksAndCollections();
            } catch (Exception e) {
                logger.error("Error displaying decks and collections", e);
            }
        });

        // Button/field wiring is done per-tab inside injectSharedRightPanel()

        updateCardsDisplay();

        // Refresh both the middle-pane tree views and the right-pane cards display
        // whenever the selection changes, so the visual selection border stays in sync.
        // CHANGE in initialize(), the SelectionManager.addSelectionChangeListener block:

        Controller.SelectionManager.addSelectionChangeListener(() -> {
            Platform.runLater(() -> {
                if (myCollectionTreeView != null) myCollectionTreeView.refresh();
                if (decksAndCollectionsTreeView != null) decksAndCollectionsTreeView.refresh();
                if (ouicheTreeView != null) ouicheTreeView.refresh();
                if (archetypesTreeView != null) archetypesTreeView.refresh();

                // Also refresh all live GridViews — TreeView.refresh() does not
                // propagate into nested ControlsFX GridViews, so cells off-screen
                // keep a stale selection border until explicitly refreshed here.
                View.CardTreeCell.refreshAllGridViews();

                if (cardsDisplayContainer != null) {
                    for (javafx.scene.Node node : cardsDisplayContainer.getChildren()) {
                        if (node instanceof javafx.scene.control.ListView) {
                            ((javafx.scene.control.ListView<?>) node).refresh();
                        }
                    }
                }
            });
        });

        UserInterfaceFunctions.registerOwnedCollectionRefresher(this::refreshFromModel);
        setupGlobalKeyShortcuts();

        // Put this in RealMainController.initialize() or equivalent setup method
        final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Controller.RealMainController.class);

        /*if (mainTabPane != null) {
            mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                String tabName = newTab == null ? "<null>" : newTab.getText();
                logger.debug("mainTabPane selection changed -> {}", tabName);

                // When OuicheList is selected, run the marking/refresh logic that previously ran for Decks and Collections.
                if (tabName != null && tabName.trim().equalsIgnoreCase("OuicheList")) {
                    logger.debug("OuicheList selected: running marking/refresh for OuicheList");
                    try {
                        // --- Replace the next line with your existing marking/refresh method(s) ---
                        // Example: recompute archetypes / mark missing for the OuicheList collection(s)
                        // markMissingForAllCollections(); // <-- your real method here
                        // Or call the same method you call when Decks and Collections is selected,
                        // but pass the OuicheList context/collection name if needed.
                    } catch (Throwable t) {
                        logger.warn("Error while running OuicheList marking/refresh", t);
                    }
                } else {
                    // Optional: if you need to clear or refresh visuals when leaving OuicheList
                    logger.debug("Non-OuicheList tab selected: {}", tabName);
                }
            });
        }*/

        // ── Close-request interception ────────────────────────────────────────
        // Platform.runLater defers the lookup until after start() has called
        // stage.show(), so the Stage is guaranteed to be present.
        Platform.runLater(() -> {
            if (mainTabPane != null
                    && mainTabPane.getScene() != null
                    && mainTabPane.getScene().getWindow() instanceof Stage) {
                ((Stage) mainTabPane.getScene().getWindow())
                        .setOnCloseRequest(this::handleWindowCloseRequest);
            }
        });
    }

    private void applySortButtonSelectedStyle(Button btn) {
        btn.setStyle("-fx-background-color: #cdfc04; -fx-text-fill: black; " + SORT_BTN_PADDING);
    }

    /**
     * Clears the inline style from a sort toggle button so it falls back
     * to the normal "small-button" CSS appearance.
     */
    private void applySortButtonUnselectedStyle(Button btn) {
        btn.setStyle(SORT_BTN_PADDING);
    }

    /**
     * Translates the current state of the four sort toggle buttons into a
     * {@link Utils.CardSorter.SortMode} value consumed by
     * {@link Utils.CardSorter#sort}.
     */
    private Utils.CardSorter.SortMode getCurrentSortMode() {
        switch (activeSortButtonIndex) {
            case 0: // AZ / ZA
                return "ZA".equals(sortAzButton.getText())
                        ? Utils.CardSorter.SortMode.ZA
                        : Utils.CardSorter.SortMode.AZ;
            case 1: // ATK
                return "ATK↑".equals(sortAtkButton.getText())
                        ? Utils.CardSorter.SortMode.ATK_ASC
                        : Utils.CardSorter.SortMode.ATK_DESC;
            case 2: // DEF
                return "DEF↑".equals(sortDefButton.getText())
                        ? Utils.CardSorter.SortMode.DEF_ASC
                        : Utils.CardSorter.SortMode.DEF_DESC;
            case 3: // LVL
                return "LVL↑".equals(sortLvlButton.getText())
                        ? Utils.CardSorter.SortMode.LVL_ASC
                        : Utils.CardSorter.SortMode.LVL_DESC;
            default:
                return Utils.CardSorter.SortMode.ATK_DESC;
        }
    }
}