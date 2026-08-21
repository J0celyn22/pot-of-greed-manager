package Controller;

import Model.CardScanner.PythonCardScannerBridge;
import Model.CardScanner.ScanLockDebouncer;
import Model.CardsLists.Card;
import Model.Database.Database;
import Utils.CardTextMatcher;
import View.CardScannerPane;
import View.FilterPane;
import View.SharedCollectionTab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Supplier;

/**
 * Owns everything about the camera card-scanner pane that isn't the pane's own view code
 * ({@link CardScannerPane}) or the sidecar transport ({@link PythonCardScannerBridge}):
 * swapping the pane in for {@link FilterPane} and back, starting/stopping the Python subprocess,
 * and — as of Unit 6 — turning its detection events into actual database matches and
 * collection inserts.
 *
 * <p>Extracted out of {@code RealMainController} in Unit 6 alongside the new detection logic,
 * mirroring the same "pull a growing concern out into its own coordinator" pattern already used
 * for {@link KeyboardShortcutHandler}, {@link SaveStateCoordinator}, and
 * {@link TabSwitchCoordinator} — this class owns {@link #sharedCardScannerPane} the same way
 * those own their respective piece of state, rather than {@code RealMainController} growing
 * three more fields and a dozen more methods for what was already its own coherent concern
 * before Unit 6 added detection on top of it.
 *
 * <p>Deliberately does <em>not</em> own {@link FilterPane} itself — {@code RealMainController}
 * still owns {@code sharedFilterPane}, since it's also wired into shortcut handling and filter
 * events unrelated to the scanner. Wherever this class needs to swap the scanner pane in for the
 * filter pane (or back), the caller passes the current {@link FilterPane} instance in rather
 * than this class holding its own reference to it.
 */
public class CardScannerCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(CardScannerCoordinator.class);

    /**
     * The header row's height while the scanner pane is showing — 2.5x
     * {@link SharedCollectionTab#DEFAULT_HEADER_ROW_HEIGHT}, giving the live preview enough
     * room to actually be useful instead of the cramped 200px filters strip it swaps in for.
     */
    private static final double CARD_SCANNER_HEADER_ROW_HEIGHT =
            SharedCollectionTab.DEFAULT_HEADER_ROW_HEIGHT * 2.5;

    /**
     * How long the scan lock takes to release once nothing is being confidently detected —
     * Unit 1's plan-doc starting value. Tunable from here without touching
     * {@link ScanLockDebouncer} itself once this is running against real hardware (Unit 7).
     */
    private static final long DEBOUNCE_RELEASE_MILLIS = 500;

    /**
     * Which tabs the camera button is even clickable on. My Collection, Decks and Collections,
     * and OuicheList — matching (and, for OuicheList, extending) the tabs
     * {@code KeyboardShortcutHandler}'s numpad/enter quick-add already supports. Archetypes
     * (read-only), Friends, and Shops don't hold a card collection a scan could add to.
     */
    private static final Set<SharedCollectionTab.TabType> CAMERA_AVAILABLE_TABS = EnumSet.of(
            SharedCollectionTab.TabType.MY_COLLECTION,
            SharedCollectionTab.TabType.DECKS,
            SharedCollectionTab.TabType.OUICHE_LIST);

    /**
     * Which tab index a confident detection is actually allowed to insert into. Narrower than
     * {@link #CAMERA_AVAILABLE_TABS}: the button is already clickable (and detection already
     * runs, so OCR accuracy can be tested) on Decks and Collections and OuicheList too, but
     * what "add" should even mean on those two tabs — which deck/collection, whether OuicheList
     * should add at all vs. only look cards up — is a real design question this unit isn't
     * answering, so insertion stays scoped to My Collection (tab index 0) until it is.
     */
    private static final int INSERT_ALLOWED_TAB_INDEX = 0;

    private final TabPane mainTabPane;
    private final Supplier<TreeView<String>> activeMiddleTreeViewSupplier;

    private CardScannerPane sharedCardScannerPane;
    /**
     * The Python camera-scanner sidecar for whichever scanner session is currently open, or
     * {@code null} when the scanner pane isn't showing. Deliberately not reused across opens —
     * a fresh subprocess starts every time the scanner pane opens, and this bridge is discarded
     * once it's stopped, so {@code null} vs. non-null always answers "is a camera subprocess
     * currently running" without extra state.
     */
    private PythonCardScannerBridge activeCardScannerBridge;
    /**
     * Reassigned fresh in {@link #startCardScanner()} on every open, not reused across sessions
     * — a card still in frame when one session ends must not suppress the very next session's
     * first detection just because the debouncer thinks it's still locked from before.
     */
    private ScanLockDebouncer debouncer;

    public CardScannerCoordinator(TabPane mainTabPane, Supplier<TreeView<String>> activeMiddleTreeViewSupplier) {
        this.mainTabPane = mainTabPane;
        this.activeMiddleTreeViewSupplier = activeMiddleTreeViewSupplier;
    }

    /**
     * @return whether the camera button should be enabled while {@code tabType} is active.
     * Called by {@code RealMainController} on every tab injection/switch, since
     * {@link FilterPane}'s camera button is a single shared instance reused across all tabs.
     */
    public boolean isCameraAvailableFor(SharedCollectionTab.TabType tabType) {
        return CAMERA_AVAILABLE_TABS.contains(tabType);
    }

    /**
     * Swaps {@link #sharedCardScannerPane} in for {@code filterPane} (or back again) in
     * whichever tab's right-header pane currently holds one of them. No-op if neither is
     * currently parented anywhere (shouldn't happen once {@code RealMainController} has wired
     * a tab at least once, which it always has by the time this button can be clicked).
     */
    public void toggleCardScannerPane(FilterPane filterPane) {
        AnchorPane headerPane = currentRightHeaderPane(filterPane);
        if (headerPane == null) {
            logger.warn("Camera button clicked but neither the FilterPane nor the scanner pane "
                    + "is currently attached to a right-header pane; ignoring.");
            return;
        }
        if (headerPane.getChildren().contains(filterPane)) {
            showCardScannerPaneInHeader(headerPane, filterPane);
        } else {
            showFilterPaneInHeader(headerPane, filterPane);
        }
    }

    /**
     * Closes the scanner pane back to {@code filterPane} if it's currently showing in
     * {@code rightHeaderPane} specifically — called by {@code RealMainController} on every tab
     * switch, since switching tabs makes "which collection a scan should add to" ambiguous and
     * always lands back on the ordinary filters view. No-op if the scanner isn't the one showing
     * there (including if it was never even created yet).
     */
    public void closeIfOpenIn(AnchorPane rightHeaderPane, FilterPane filterPane) {
        if (rightHeaderPane.getChildren().contains(sharedCardScannerPane)) {
            showFilterPaneInHeader(rightHeaderPane, filterPane);
        }
    }

    /**
     * @return the right-header pane currently holding {@code filterPane} or
     * {@link #sharedCardScannerPane}, whichever of the two is presently attached; {@code null}
     * if neither is attached anywhere.
     */
    private AnchorPane currentRightHeaderPane(FilterPane filterPane) {
        if (filterPane.getParent() instanceof AnchorPane parentPane) {
            return parentPane;
        }
        if (sharedCardScannerPane != null
                && sharedCardScannerPane.getParent() instanceof AnchorPane parentPane) {
            return parentPane;
        }
        return null;
    }

    private void showCardScannerPaneInHeader(AnchorPane headerPane, FilterPane filterPane) {
        if (sharedCardScannerPane == null) {
            sharedCardScannerPane = new CardScannerPane();
            sharedCardScannerPane.getCloseButton().setOnAction(event -> {
                if (sharedCardScannerPane.getParent() instanceof AnchorPane currentHeaderPane) {
                    showFilterPaneInHeader(currentHeaderPane, filterPane);
                }
            });
        }
        headerPane.getChildren().clear();
        headerPane.getChildren().add(sharedCardScannerPane);
        AnchorPane.setTopAnchor(sharedCardScannerPane, 0.0);
        AnchorPane.setBottomAnchor(sharedCardScannerPane, 0.0);
        AnchorPane.setLeftAnchor(sharedCardScannerPane, 0.0);
        AnchorPane.setRightAnchor(sharedCardScannerPane, 0.0);
        setHeaderRowHeight(headerPane, CARD_SCANNER_HEADER_ROW_HEIGHT);
        startCardScanner();
    }

    private void showFilterPaneInHeader(AnchorPane headerPane, FilterPane filterPane) {
        stopCardScanner();
        headerPane.getChildren().clear();
        headerPane.getChildren().add(filterPane);
        AnchorPane.setTopAnchor(filterPane, 0.0);
        AnchorPane.setBottomAnchor(filterPane, 0.0);
        AnchorPane.setLeftAnchor(filterPane, 0.0);
        AnchorPane.setRightAnchor(filterPane, 0.0);
        setHeaderRowHeight(headerPane, SharedCollectionTab.DEFAULT_HEADER_ROW_HEIGHT);
    }

    /**
     * The right-header {@code AnchorPane} passed around here ({@code filterPane}'s or
     * {@link #sharedCardScannerPane}'s parent) is itself a child of the tab's single header
     * {@code HBox} row, alongside the tab-specific left header — see
     * {@code View.SharedCollectionTab#buildHeaderRow}. That row has one shared height, so
     * growing the scanner pane taller than the filters view means growing that whole row (and
     * with it, the left header sitting beside it) while the scanner is open, then restoring it
     * on close.
     */
    private void setHeaderRowHeight(AnchorPane headerPane, double height) {
        if (headerPane.getParent() instanceof HBox headerRow) {
            headerRow.setPrefHeight(height);
        }
    }

    /**
     * Starts a fresh {@link PythonCardScannerBridge} subprocess and wires its frame/error/
     * detection events into {@link #sharedCardScannerPane} and {@link #handleDetectionEvent},
     * and resets {@link #debouncer} for the new session. Called every time the scanner pane is
     * shown (not just the first time), matching Unit 4's "opening the pane starts the camera"
     * rule — see {@link #activeCardScannerBridge}'s own comment for why a fresh subprocess per
     * open, rather than one reused across opens, keeps that rule simple to guarantee.
     */
    private void startCardScanner() {
        sharedCardScannerPane.resetPreview();
        sharedCardScannerPane.setPreviewStatusText("Starting camera\u2026");
        debouncer = new ScanLockDebouncer(DEBOUNCE_RELEASE_MILLIS);

        activeCardScannerBridge = new PythonCardScannerBridge(
                sharedCardScannerPane::showPreviewFrame,
                sharedCardScannerPane::setPreviewStatusText,
                this::handleDetectionEvent);
        try {
            activeCardScannerBridge.start();
        } catch (IOException startupIoException) {
            logger.error("Could not start the Python card-scanner bridge \u2014 check that Python is on "
                            + "PATH with the packages in python/requirements.txt installed, and that "
                            + "python/card_scanner_bridge.py is present relative to the working directory.",
                    startupIoException);
            sharedCardScannerPane.setPreviewStatusText(
                    "Could not start the camera process. See the application log for details.");
            activeCardScannerBridge = null;
        }
    }

    /**
     * Stops whichever {@link PythonCardScannerBridge} subprocess is currently running, if any.
     * No-op if the scanner wasn't open (e.g. a tab switch that happened while the ordinary
     * filters view was already showing). The actual shutdown wait runs on a background thread,
     * not the JavaFX application thread, since {@link PythonCardScannerBridge#close()} can
     * block for a few seconds waiting for the sidecar to exit — blocking here would freeze the
     * UI for that long every time the scanner pane closes.
     */
    private void stopCardScanner() {
        if (activeCardScannerBridge == null) {
            return;
        }
        PythonCardScannerBridge bridgeToClose = activeCardScannerBridge;
        activeCardScannerBridge = null;

        Thread shutdownThread = new Thread(bridgeToClose::close, "card-scanner-bridge-shutdown");
        shutdownThread.setDaemon(true);
        shutdownThread.start();
    }

    /**
     * Resolves one detection cycle's OCR candidates via {@link CardTextMatcher#matchCandidates},
     * feeds whether anything matched into {@link #debouncer}, and — only on the specific call
     * that transitions the debouncer from released to locked, i.e. exactly once per "a card was
     * shown" rather than once per frame it stays in view — hands off to
     * {@link #handleCandidateResolution} or {@link #handleUnambiguousResolution} depending on
     * which {@link CardTextMatcher.Resolution} subtype came back.
     *
     * <p>Called on the JavaFX application thread (see {@link PythonCardScannerBridge}'s own
     * listener contract), so no synchronization is needed against {@link #debouncer} or
     * {@link #sharedCardScannerPane}. Wrapped defensively — an unexpected failure resolving or
     * inserting a single detection is logged and reported in the feedback label rather than
     * left to propagate, since this runs on a live UI-thread callback loop that needs to keep
     * working through the next detection cycle regardless.
     *
     * @param recognizedCandidates this cycle's OCR candidate lines, never {@code null}, possibly
     *                             empty
     */
    private void handleDetectionEvent(List<String> recognizedCandidates) {
        if (activeCardScannerBridge == null || debouncer == null) {
            return; // a stray event arrived after the session already ended; ignore it
        }
        try {
            Optional<CardTextMatcher.Resolution> resolution = CardTextMatcher.matchCandidates(recognizedCandidates);
            if (resolution.isEmpty()) {
                debouncer.onNoConfidentDetection();
                return;
            }

            boolean addEligible = debouncer.onConfidentDetection();
            if (!addEligible) {
                return; // still locked onto an earlier detection; this is a continuation, not a new add
            }

            int activeTabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
            TreeView<String> activeTreeView = activeMiddleTreeViewSupplier.get();

            if (resolution.get() instanceof CardTextMatcher.CardCandidates cardCandidates) {
                handleCandidateResolution(cardCandidates, activeTabIndex, activeTreeView);
                return;
            }
            if (resolution.get() instanceof CardTextMatcher.MatchResult matchResult) {
                handleUnambiguousResolution(matchResult, activeTabIndex, activeTreeView);
            }
            // unreachable otherwise: Resolution has exactly these two subtypes
        } catch (RuntimeException unexpectedMatchOrInsertFailure) {
            logger.error("Failed to resolve or insert a camera-scanner detection", unexpectedMatchOrInsertFailure);
            if (sharedCardScannerPane != null) {
                sharedCardScannerPane.setDetectionFeedbackText(
                        "Something went wrong resolving that card. See the application log for details.");
            }
        }
    }

    /**
     * A pass code, print code, or unambiguous name match — inserts via
     * {@link MiddleSelectionActionHandler#insertCardsAtQuickAddTarget} if the active tab allows
     * it, same as Unit 6 always did, or reports why not otherwise. Clears any printCode
     * candidates a previous, ambiguous detection cycle may have left showing, so switching from
     * an ambiguous card to an unambiguous one doesn't leave stale buttons on screen.
     */
    private void handleUnambiguousResolution(
            CardTextMatcher.MatchResult matchResult, int activeTabIndex, TreeView<String> activeTreeView) {
        sharedCardScannerPane.clearPrintCodeCandidates();
        Card matchedCard = matchResult.getCard();
        if (activeTabIndex != INSERT_ALLOWED_TAB_INDEX) {
            sharedCardScannerPane.setDetectionFeedbackText(
                    "Detected: " + matchedCard.getNameOrNumber()
                            + " \u2014 scanning to add isn't wired up for this tab yet");
            return;
        }
        insertResolvedCard(matchedCard, activeTabIndex, activeTreeView);
    }

    /**
     * A name or pass code narrowed to a Konami ID with more than one valid printing — Unit 8's
     * addition, replacing Unit 7's placeholder "picking a specific one isn't wired up yet"
     * message with actual printCode buttons via
     * {@link CardScannerPane#showPrintCodeCandidates(List)}.
     *
     * <p>{@link CardTextMatcher.CardCandidates#getPrintCodeOptions()} and
     * {@link CardTextMatcher.CardCandidates#getArtworkOptions()} are two independent axes, not
     * one artwork list per printCode — every printCode button in a given candidates set shares
     * the same artwork-ambiguity question, so whether a click adds immediately or only selects is
     * decided once for the whole set (see {@link #buildPaneCandidates}), not per button.
     * Artwork disambiguation itself ({@code getArtworkOptions().size() > 1}) is Unit 9's concern;
     * this unit only ever offers printCode buttons.
     *
     * <p>Same tab restriction as the unambiguous case: rendering clickable buttons that could
     * only ever fail to insert isn't useful, so a disallowed tab gets the same kind of "not
     * wired up yet" message instead, with any previously-shown candidates cleared.
     */
    private void handleCandidateResolution(
            CardTextMatcher.CardCandidates cardCandidates, int activeTabIndex, TreeView<String> activeTreeView) {
        String cardLabel = cardCandidates.getDisplayName();
        String forCardName = cardLabel != null ? " for " + cardLabel : "";

        if (activeTabIndex != INSERT_ALLOWED_TAB_INDEX) {
            sharedCardScannerPane.clearPrintCodeCandidates();
            sharedCardScannerPane.setDetectionFeedbackText(
                    "Multiple printings detected" + forCardName
                            + " \u2014 scanning to add isn't wired up for this tab yet");
            return;
        }

        boolean singleArtwork = cardCandidates.getArtworkOptions().size() <= 1;
        sharedCardScannerPane.showPrintCodeCandidates(
                buildPaneCandidates(cardCandidates, singleArtwork, activeTabIndex, activeTreeView));
        sharedCardScannerPane.setDetectionFeedbackText(
                "Multiple printings detected" + forCardName + (singleArtwork
                        ? " \u2014 choose a printCode to add"
                        : " \u2014 choose a printCode (artwork selection isn't wired up yet)"));
    }

    /**
     * Translates {@code cardCandidates}' printCode options into the view-model list
     * {@link CardScannerPane#showPrintCodeCandidates(List)} renders, so {@link CardScannerPane}
     * itself never needs to know about {@link CardTextMatcher}'s types.
     *
     * @param singleArtwork whether {@code cardCandidates} has at most one artwork option overall
     *                      (see {@link #handleCandidateResolution}) — when {@code true}, every
     *                      button here gets an {@code onAdd} callback that looks the specific
     *                      printed card up and inserts it immediately; when {@code false}, every
     *                      button gets a {@code null} callback, since Unit 8 only lets those be
     *                      visually selected (Unit 9 wires up completing the add from there)
     */
    private List<CardScannerPane.PrintCodeCandidate> buildPaneCandidates(
            CardTextMatcher.CardCandidates cardCandidates, boolean singleArtwork,
            int activeTabIndex, TreeView<String> activeTreeView) {
        List<CardScannerPane.PrintCodeCandidate> paneCandidates = new ArrayList<>();
        for (CardTextMatcher.CardCandidates.PrintCodeOption printCodeOption : cardCandidates.getPrintCodeOptions()) {
            String printCode = printCodeOption.getPrintCode();
            String language = printCodeOption.getLanguage();
            String displayLabel = (language == null || language.isBlank())
                    ? printCode
                    : printCode + " (" + language + ")";
            Runnable onAdd = singleArtwork
                    ? () -> insertPrintCode(printCode, activeTabIndex, activeTreeView)
                    : null;
            paneCandidates.add(
                    new CardScannerPane.PrintCodeCandidate(printCode, displayLabel, singleArtwork, onAdd));
        }
        return paneCandidates;
    }

    /**
     * Looks a specific printCode up via {@link Database#getAllPrintedCardsList()} — the same
     * lookup {@link CardTextMatcher.CardCandidates.PrintCodeOption#getPrintCode()}'s own javadoc
     * documents it as a valid key for — and inserts it. A missing entry would mean
     * {@code PrintCodeToKonamiId} and {@code getAllPrintedCardsList()} have drifted out of sync
     * with each other; reported rather than thrown, since one bad button shouldn't crash the
     * whole scanning session.
     */
    private void insertPrintCode(String printCode, int activeTabIndex, TreeView<String> activeTreeView) {
        try {
            Card printedCard = Database.getAllPrintedCardsList().get(printCode);
            if (printedCard == null) {
                logger.warn("printCode candidate \"{}\" had no entry in getAllPrintedCardsList(); ignoring click",
                        printCode);
                sharedCardScannerPane.setDetectionFeedbackText(
                        "Couldn't find a card for print code " + printCode
                                + ". See the application log for details.");
                return;
            }
            insertResolvedCard(printedCard, activeTabIndex, activeTreeView);
        } catch (URISyntaxException uriSyntaxException) {
            logger.error("Failed to look up printCode \"{}\" from a scanner candidate button",
                    printCode, uriSyntaxException);
            sharedCardScannerPane.setDetectionFeedbackText(
                    "Something went wrong adding that card. See the application log for details.");
        }
    }

    /**
     * Shared by the unambiguous auto-add path and a single-artwork printCode button click:
     * inserts {@code card} via {@link MiddleSelectionActionHandler#insertCardsAtQuickAddTarget},
     * clears any printCode candidates left showing, and reports what was added.
     */
    private void insertResolvedCard(Card card, int activeTabIndex, TreeView<String> activeTreeView) {
        MiddleSelectionActionHandler.insertCardsAtQuickAddTarget(List.of(card), activeTabIndex, activeTreeView);
        sharedCardScannerPane.clearPrintCodeCandidates();
        sharedCardScannerPane.setDetectionFeedbackText("Added: " + card.getNameOrNumber());
    }
}