package Controller;

import Model.CardsLists.Deck;
import Model.CardsLists.ThemeCollection;
import View.NavigationItem;
import View.SharedCollectionTab;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles tab-switch events on the main tab pane: injecting the shared right
 * panel, refreshing the middle pane, and lazily populating/displaying
 * whichever of Decks &amp; Collections, OuicheList, or Archetypes was just
 * selected.
 *
 * <p>Extracted from {@link RealMainController}, which still owns the shared
 * right-panel plumbing ({@code injectSharedRightPanel}, {@code
 * updateMiddlePaneDisplay}, {@code getSharedTabAt}), the dirty-indicator
 * refresh, and the OuicheList compact-view refresh — those are called back
 * on {@code coordinator} rather than duplicated here.
 */
public class TabSwitchCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(TabSwitchCoordinator.class);

    private final RealMainController coordinator;
    private final DecksCollectionsController decksController;
    private final OuicheListController ouicheListController;
    private final ArchetypesController archetypesController;
    private final SharedCollectionTab decksTab;

    private boolean ouicheListLoaded = false;

    public TabSwitchCoordinator(RealMainController coordinator,
                                DecksCollectionsController decksController,
                                OuicheListController ouicheListController,
                                ArchetypesController archetypesController,
                                SharedCollectionTab decksTab) {
        this.coordinator = coordinator;
        this.decksController = decksController;
        this.ouicheListController = ouicheListController;
        this.archetypesController = archetypesController;
        this.decksTab = decksTab;
    }

    /**
     * Called by the tab-change listener whenever the user selects a different tab.
     * Injects the shared right panel, re-applies the middle-pane filter, and
     * delegates to the appropriate sub-controller.
     *
     * @param selectedIndex the newly selected tab's index (-1 if none)
     */
    public void handleTabSwitch(int selectedIndex) {
        if (selectedIndex < 0) {
            return;
        }

        coordinator.injectSharedRightPanel(coordinator.getSharedTabAt(selectedIndex));
        coordinator.updateMiddlePaneDisplay();

        switch (selectedIndex) {
            case 1 -> handleDecksTabSelected();
            case 2 -> handleOuicheListTabSelected();
            case 3 -> handleArchetypesTabSelected();
            default -> {
            }
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
                        coordinator.refreshDecksAndCollectionsTreeView();
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

                    coordinator.updateTabDirtyIndicators();
                } catch (Exception exception) {
                    logger.debug("Decks refresher failed", exception);
                }
            });
            decksController.displayDecksAndCollections();
        } catch (Exception exception) {
            logger.error("Error displaying Decks and Collections", exception);
        }

        UserInterfaceFunctions.registerOuicheListRefresher(() -> {
            if (!ouicheListLoaded) {
                return;
            }
            try {
                ouicheListController.displayOuicheListUnified();
                ouicheListController.populateOuicheListMenu();
                coordinator.refreshOuicheListCompactViewIfVisible();
            } catch (Exception exception) {
                logger.debug("OuicheList refresher failed", exception);
            }
        });
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
}