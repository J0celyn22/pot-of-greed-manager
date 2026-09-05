package Controller;

import Model.CardsLists.Deck;
import Model.CardsLists.ThemeCollection;
import View.NavigationItem;
import View.SharedCollectionTab;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

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

        // Registered once here, for the lifetime of this coordinator, rather than inside
        // handleDecksTabSelected(). That method reruns on every switch to the Decks &
        // Collections tab, and ViewRefresherRegistry.register() dedups by reference — a
        // fresh lambda object created on every visit can never match an earlier one, so
        // registering there left one more copy of this refresher permanently registered
        // per tab visit, all of which then ran (and each triggered its own full Decks &
        // Collections tree rebuild) on every subsequent edit. A stable method reference
        // registered exactly once, mirroring how RealMainController registers its My
        // Collection refresher in initialize(), avoids the accumulation entirely.
        UserInterfaceFunctions.registerDecksCollectionsRefresher(this::runDecksRefresh);
        UserInterfaceFunctions.registerDecksAffectedOwnersRefresher(this::runDecksAffectedOwnersRefresh);
    }

    /**
     * Runs the Decks &amp; Collections refresh: consumes whatever pending UI-action state
     * (rename/expand/scroll targets, the full-rebuild flag) accumulated since the last run,
     * rebuilds or refreshes the tree accordingly, then re-applies any pending navigation
     * targets. Registered once, in the constructor, as the single {@code
     * registerDecksCollectionsRefresher} callback for this coordinator's whole lifetime.
     */
    private void runDecksRefresh() {
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
            logger.error("Decks refresher failed", exception);
        }
    }

    /**
     * Runs the scoped Decks &amp; Collections refresh for a plain card-level edit: forwards
     * {@code affectedOwners} to {@link
     * DecksCollectionsController#refreshDecksAndCollectionsContentForAffectedOwners}, which
     * falls back to a full {@link DecksCollectionsController#displayDecksAndCollections()} on
     * its own when it can't handle the change in place. Registered once, in the constructor, as
     * the single {@code registerDecksAffectedOwnersRefresher} callback for this coordinator's
     * whole lifetime — see {@link #runDecksRefresh()}'s constructor-registration comment for why
     * that matters.
     *
     * @param affectedOwners the owners whose content just changed
     */
    private void runDecksAffectedOwnersRefresh(Set<Object> affectedOwners) {
        try {
            decksController.refreshDecksAndCollectionsContentForAffectedOwners(affectedOwners);
        } catch (Exception exception) {
            logger.error("Decks affected-owners refresher failed", exception);
        }
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
            decksController.displayDecksAndCollections();
        } catch (Exception exception) {
            logger.error("Error displaying Decks and Collections", exception);
        }

        UserInterfaceFunctions.registerOuicheListRefresher(() -> {
            if (!ouicheListLoaded) {
                return;
            }
            try {
                ouicheListController.refreshOuicheListContentInPlace();
                ouicheListController.populateOuicheListMenu();
                coordinator.refreshOuicheListCompactViewIfVisible();
            } catch (Exception exception) {
                logger.warn("OuicheList refresher failed", exception);
            }
        });

        UserInterfaceFunctions.registerOuicheListAffectedGroupsRefresher(affectedOwners -> {
            if (!ouicheListLoaded || affectedOwners == null || affectedOwners.isEmpty()) {
                return;
            }
            try {
                boolean membershipChanged =
                        ouicheListController.refreshOuicheListContentForAffectedGroups(affectedOwners);
                if (membershipChanged) {
                    ouicheListController.populateOuicheListMenu();
                }
                // The compact view's counts (maOuicheList/maOuicheListCounts) changed whenever a
                // slot was filled, independent of whether tree membership did — its own
                // visibility check keeps this a no-op unless it's actually on-screen.
                coordinator.refreshOuicheListCompactViewIfVisible();
            } catch (Exception exception) {
                logger.warn("OuicheList affected-groups refresher failed", exception);
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