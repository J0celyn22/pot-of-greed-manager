package Controller;

import Model.CardScanner.PythonCardScannerBridge;
import Model.CardScanner.ScanLockDebouncer;
import Model.CardsLists.Card;
import Model.Database.Database;
import Utils.CardTextMatcher;
import View.CardScannerArtworkGallery;
import View.CardScannerPane;
import View.FilterPane;
import View.SharedCollectionTab;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import javafx.scene.control.*;
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
 *
 * <p>Unit 9 adds a second pane this class swaps into an active tab: {@link #sharedArtworkGallery}
 * claims the active tab's {@code rightContentPane} (via {@link #activeRightContentPaneSupplier})
 * whenever a printCode candidate has more than one artwork, the same way
 * {@link #sharedCardScannerPane} claims the right-header pane — see
 * {@link #showArtworkGalleryInRightContentPane} and {@link #hideArtworkGallery} for the
 * show/hide pair mirroring {@link #showCardScannerPaneInHeader}/{@link #showFilterPaneInHeader}.
 * Unlike the header swap, this never replaces the tab's own content — the gallery is added as a
 * sibling node and only its visibility toggles, so hiding it never needs to reconstruct whatever
 * the tab was already showing in that pane.
 *
 * <p>Unit 10 changes when {@link #resetCandidateSelectionState} gets called. It used to run both
 * on every successful add and the instant the debounce lock released (the card leaving frame) —
 * real-world use showed that made it impossible to add several copies of a card, or to add it
 * once it was no longer in frame. Now it only runs from {@link #handleCandidateResolution} and
 * {@link #handleUnambiguousResolution}, i.e. only when a genuinely new detection cycle begins
 * (see {@link #handleDetectionEvent}'s {@code addEligible} check) — a card being shown, whether
 * it's the same one again or a different one. Between detections, the printCode buttons and
 * artwork gallery from the last one stay exactly as they were, fully clickable.
 *
 * <p>Unit 10 also throttles matching itself, not just the buttons: {@link #handleDetectionEvent}
 * used to call {@link CardTextMatcher#matchCandidates} on every detection cycle regardless of
 * {@link #debouncer}'s state, including the ~4 cycles/second it kept re-running while already
 * locked onto an already-resolved card — real, wasted work competing against
 * {@link PythonCardScannerBridge}'s own preview-frame updates on the same JavaFX application
 * thread. Now a lock skips full matching for {@link #MATCH_PAUSE_AFTER_LOCK_MILLIS} at a time
 * (see {@link #matchResumeAtMillis}), falling back to {@link #feedDebouncerWithoutMatching} to
 * keep {@link #debouncer}'s release timing accurate without paying matching's cost. Deliberately
 * does not apply while released — a card that hasn't resolved yet still gets a full match
 * attempt every cycle, since throttling that case would make the scanner less effective exactly
 * when it's trying hardest to find a match.
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
     * How long {@link #handleDetectionEvent} skips a full {@link CardTextMatcher#matchCandidates}
     * call after a real match attempt leaves {@link #debouncer} locked — Unit 10's fix for the
     * plan doc's "gate matchCandidates behind the debounce state" tuning item. Applies whether
     * this is the detection that just locked the debouncer or a later resume tick reconfirming
     * the same card; never applies while released, since an unresolved card in frame still needs
     * a full attempt every cycle. See {@link #matchResumeAtMillis} for how this is tracked.
     */
    private static final long MATCH_PAUSE_AFTER_LOCK_MILLIS = 1000;

    /**
     * How often {@link #memoryDiagnosticsTimer} logs a snapshot while the scanner is open. 15s
     * is frequent enough to catch a leak within a normal scan session without flooding the log
     * the way logging every frame would.
     */
    private static final long MEMORY_DIAGNOSTICS_INTERVAL_MILLIS = 15_000;

    /**
     * The sub-rectangle of each captured frame OCR is restricted to while "rapid scanning mode"
     * ({@link CardScannerPane#getRapidScanToggle()}) is on, as fractions (0 to 1) of the frame's
     * own width/height — see {@link PythonCardScannerBridge#setDetectionRoi} for why fractions
     * rather than pixels. Positioned center-to-bottom-right rather than dead center: measured
     * against a real scan, the print code sits at roughly x 0.50-0.69, y 0.59-0.68, and the
     * center-left quadrant is where the light used for contrast tends to cause glare. This is a
     * starting rectangle from one example, generously margined around that measurement, not a
     * tuned final value — expect to adjust once the guide rectangle (not yet drawn on the preview
     * — see {@link CardScannerPane}'s own javadoc) makes it easy to see what it actually covers
     * while aligning a real card.
     */
    private static final Rectangle2D RAPID_SCAN_ROI = new Rectangle2D(0.40, 0.42, 0.55, 0.40);

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
    /**
     * Supplies the active tab's {@code rightContentPane} — {@link #sharedArtworkGallery} is
     * mounted into whichever one this returns at the moment artwork disambiguation is needed,
     * mirroring how {@link #activeMiddleTreeViewSupplier} already supplies the active tab's
     * middle tree for insertion. Consulted fresh on every artwork render rather than cached,
     * since which tab is active can change between detections (though in practice a tab switch
     * already closes the scanner entirely — see {@link #closeIfOpenIn} — before this would ever
     * observe a stale pane).
     */
    private final Supplier<AnchorPane> activeRightContentPaneSupplier;
    private final DoubleProperty cardWidthProperty;
    private final DoubleProperty cardHeightProperty;

    private CardScannerPane sharedCardScannerPane;
    /**
     * The Unit 9 artwork picker, lazily created the first time it's needed (mirroring
     * {@link #sharedCardScannerPane}'s own lazy creation) and reused across every scanner session
     * and every tab it's mounted into — {@link #showArtworkGalleryInRightContentPane} re-parents
     * it into whichever {@code rightContentPane} {@link #activeRightContentPaneSupplier} returns
     * rather than building a new instance per tab, the same "one shared instance moved around"
     * pattern {@code RealMainController} already uses for {@code cardsDisplayContainer}.
     */
    private CardScannerArtworkGallery sharedArtworkGallery;
    /**
     * The Python camera-scanner sidecar for whichever scanner session is currently open, or
     * {@code null} when the scanner pane isn't showing. Deliberately not reused across opens —
     * a fresh subprocess starts every time the scanner pane opens, and this bridge is discarded
     * once it's stopped, so {@code null} vs. non-null always answers "is a camera subprocess
     * currently running" without extra state.
     */
    private PythonCardScannerBridge activeCardScannerBridge;
    /**
     * Periodic memory-diagnostics logger, running only while a scanner session is open (started
     * in {@link #startCardScanner()}, cancelled in {@link #stopCardScanner()}). A stand-in for a
     * real heap dump: a paid profiler isn't available in J0celyn's environment, so this logs the
     * size of every cache/registry a leak was plausible in — see this class's own log line for
     * the full list — every {@link #MEMORY_DIAGNOSTICS_INTERVAL_MILLIS} while scanning, so a
     * real session's log shows which one (if any) actually grows unbounded.
     */
    private Timer memoryDiagnosticsTimer;
    /**
     * Reassigned fresh in {@link #startCardScanner()} on every open, not reused across sessions
     * — a card still in frame when one session ends must not suppress the very next session's
     * first detection just because the debouncer thinks it's still locked from before.
     */
    private ScanLockDebouncer debouncer;
    /**
     * The wall-clock time before which {@link #handleDetectionEvent} should skip a full
     * {@link CardTextMatcher#matchCandidates} call and use the cheap presence check in
     * {@link #feedDebouncerWithoutMatching} instead. Set to {@link #MATCH_PAUSE_AFTER_LOCK_MILLIS}
     * past "now" every time a real match attempt ends with {@link #debouncer} locked; left alone
     * (and therefore already in the past, so matching runs normally) while released. Reset to 0
     * in {@link #startCardScanner()} for the same reason {@link #debouncer} is reassigned fresh —
     * a leftover timestamp from a previous session must not pause the very next session's first
     * detection.
     */
    private long matchResumeAtMillis = 0;

    /**
     * Which camera device {@link #startCardScanner()} opens, set via {@link #selectCamera(int)}
     * (Unit 10's "choose camera" button). Unlike {@link #debouncer}, this deliberately survives
     * across close/reopen — once someone picks a non-default camera there's no reason to forget
     * it just because they closed the pane, so it only resets on application restart.
     */
    private int selectedCameraIndex = 0;

    /**
     * The active tab index and middle tree as of the detection cycle currently being handled —
     * captured in {@link #handleDetectionEvent} and read back by
     * {@link #completeArtworkDisambiguationAdd}, since {@link CardScannerPane#setOnCandidateAdd}
     * only reports {@code (printCode, artworkId)}, not the insertion target. Both fields stay
     * meaningful for as long as the artwork gallery they were captured for stays showing — which,
     * as of Unit 10, can span several adds and the card leaving frame, not just one — until the
     * next detection cycle overwrites them (see {@link #handleDetectionEvent}). The tab can't
     * change out from under a pending selection in practice, since switching tabs already closes
     * the scanner entirely (see {@link #closeIfOpenIn}) before either field would be read stale.
     */
    private int activeTabIndex;
    private TreeView<String> activeTreeView;
    /**
     * The candidates currently offering artwork disambiguation — set by
     * {@link #handleCandidateResolution} right before the artwork gallery renders, and read back
     * by {@link #completeArtworkDisambiguationAdd} to resolve an artwork identifier (an artNumber
     * string — see {@link CardScannerArtworkResolver#buildArtworkOptions}) back to the specific
     * {@link Card} it names.
     */
    private CardTextMatcher.CardCandidates activeCardCandidates;

    public CardScannerCoordinator(
            TabPane mainTabPane,
            Supplier<TreeView<String>> activeMiddleTreeViewSupplier,
            Supplier<AnchorPane> activeRightContentPaneSupplier,
            DoubleProperty cardWidthProperty,
            DoubleProperty cardHeightProperty) {
        this.mainTabPane = mainTabPane;
        this.activeMiddleTreeViewSupplier = activeMiddleTreeViewSupplier;
        this.activeRightContentPaneSupplier = activeRightContentPaneSupplier;
        this.cardWidthProperty = cardWidthProperty;
        this.cardHeightProperty = cardHeightProperty;
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
            sharedCardScannerPane.getChooseCameraButton().setOnAction(event -> chooseCameraRequested());
            sharedCardScannerPane.getRapidScanToggle().selectedProperty().addListener(
                    (observable, wasEnabled, isEnabled) -> applyRapidScanMode(isEnabled));
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
        hideArtworkGallery();
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
     * open, rather than one reused across opens, keeps that rule simple to guarantee. Opens on
     * whichever camera {@link #selectedCameraIndex} currently holds — also called by
     * {@link #selectCamera(int)} to restart an already-running session on a newly picked camera.
     */
    private void startCardScanner() {
        sharedCardScannerPane.resetPreview();
        sharedCardScannerPane.setPreviewStatusText("Starting camera\u2026");
        // Re-registered on every open (harmless — always the same method reference) purely for
        // symmetry with resetPreview()/the frame and error listeners just below being reset per
        // session; completeArtworkDisambiguationAdd itself reads activeTabIndex/activeTreeView
        // fresh from this instance's fields at call time, not from anything captured here.
        sharedCardScannerPane.setOnCandidateAdd(this::completeArtworkDisambiguationAdd);
        debouncer = new ScanLockDebouncer(DEBOUNCE_RELEASE_MILLIS);
        matchResumeAtMillis = 0;

        activeCardScannerBridge = new PythonCardScannerBridge(
                sharedCardScannerPane::showPreviewFrame,
                sharedCardScannerPane::setPreviewStatusText,
                this::handleDetectionEvent);
        try {
            activeCardScannerBridge.start(selectedCameraIndex);
            startMemoryDiagnosticsTimer();
            applyRapidScanMode(sharedCardScannerPane.getRapidScanToggle().isSelected());
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
     * Applies (or clears) {@link #RAPID_SCAN_ROI} on whichever bridge is currently running. A
     * no-op if none is (e.g. the toggle was flipped before a camera ever started, or startup
     * itself failed) — {@link #startCardScanner()} calls this again once a bridge does exist, so
     * nothing is lost by the toggle's own listener silently doing nothing in the meantime.
     * <p>
     * Called from two places: {@link CardScannerPane#getRapidScanToggle()}'s own listener when
     * the person flips it, and once after every fresh {@link PythonCardScannerBridge} starts in
     * {@link #startCardScanner()} — {@link #sharedCardScannerPane} (and so the toggle's selected
     * state) is reused across opens, but the subprocess behind it is not, so a brand new process
     * has to be told explicitly rather than assuming it already knows the toggle was left on.
     */
    private void applyRapidScanMode(boolean enabled) {
        if (activeCardScannerBridge == null) {
            return;
        }
        activeCardScannerBridge.setDetectionRoi(enabled,
                RAPID_SCAN_ROI.getMinX(), RAPID_SCAN_ROI.getMinY(),
                RAPID_SCAN_ROI.getWidth(), RAPID_SCAN_ROI.getHeight());
    }

    /**
     * Starts {@link #memoryDiagnosticsTimer}, logging one {@code [MEM-DIAG]} line every
     * {@link #MEMORY_DIAGNOSTICS_INTERVAL_MILLIS} for as long as the scanner stays open. See
     * {@link #memoryDiagnosticsTimer}'s own comment for why this exists. Reads
     * {@link #activeCardScannerBridge} fresh on every tick (via the enclosing instance) rather
     * than capturing it, so a session restart (camera re-selected) picks up the new bridge
     * without restarting this timer.
     */
    private void startMemoryDiagnosticsTimer() {
        stopMemoryDiagnosticsTimer();
        memoryDiagnosticsTimer = new Timer("card-scanner-memory-diagnostics", true);
        memoryDiagnosticsTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                PythonCardScannerBridge bridge = activeCardScannerBridge;
                Runtime runtime = Runtime.getRuntime();
                long heapUsedMegabytes = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
                long heapMaxMegabytes = runtime.maxMemory() / (1024 * 1024);
                logger.info("[MEM-DIAG] heap {} MB / {} MB max | LruImageCache {} entries | "
                                + "CardImageLoader path cache {} entries | "
                                + "SelectionHighlightRegistry {} tracked elements, {} active listener "
                                + "subscriptions | CardGridCell {} instances ever created | "
                                + "frames delivered this session: {}",
                        heapUsedMegabytes, heapMaxMegabytes,
                        Utils.LruImageCache.size(),
                        View.CardImageLoader.pathCacheSize(),
                        SelectionHighlightRegistry.trackedElementCount(),
                        SelectionHighlightRegistry.activeSubscriptionCount(),
                        View.CardTreeCell.cardGridCellInstancesCreatedCount(),
                        bridge == null ? "n/a" : bridge.getDeliveredFrameCount());
            }
        }, MEMORY_DIAGNOSTICS_INTERVAL_MILLIS, MEMORY_DIAGNOSTICS_INTERVAL_MILLIS);
    }

    /**
     * Stops {@link #memoryDiagnosticsTimer} if running. Safe to call even when it isn't (e.g.
     * {@link #stopCardScanner}'s early-return path for a scanner that was never started).
     */
    private void stopMemoryDiagnosticsTimer() {
        if (memoryDiagnosticsTimer != null) {
            memoryDiagnosticsTimer.cancel();
            memoryDiagnosticsTimer = null;
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
        stopMemoryDiagnosticsTimer();
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
     * Handles a click on {@link CardScannerPane#getChooseCameraButton()}. Disables the button
     * and probes for available cameras on a background thread — see
     * {@link PythonCardScannerBridge#listAvailableCameras()}'s own javadoc for why this can't
     * just be a command sent to whatever session may already be running — then hands the result
     * to {@link #presentCameraChoices} back on the JavaFX application thread.
     */
    private void chooseCameraRequested() {
        Button chooseCameraButton = sharedCardScannerPane.getChooseCameraButton();
        chooseCameraButton.setDisable(true);
        sharedCardScannerPane.setPreviewStatusText("Looking for cameras\u2026");

        Thread probeThread = new Thread(() -> {
            List<Integer> foundCameraIndices;
            try {
                foundCameraIndices = PythonCardScannerBridge.listAvailableCameras();
            } catch (IOException probeIoException) {
                logger.error("Could not run the camera probe \u2014 check that Python is on PATH "
                                + "and python/card_scanner_bridge.py is present relative to the "
                                + "working directory.",
                        probeIoException);
                foundCameraIndices = List.of();
            }
            List<Integer> availableCameraIndices = foundCameraIndices;
            Platform.runLater(() -> presentCameraChoices(chooseCameraButton, availableCameraIndices));
        }, "card-scanner-camera-probe");
        probeThread.setDaemon(true);
        probeThread.start();
    }

    /**
     * Re-enables {@code chooseCameraButton} and shows a {@link ContextMenu} listing
     * {@code availableCameraIndices}, anchored below the button. Guards against the scanner pane
     * having been closed while the probe was still running — {@code chooseCameraButton} would no
     * longer be attached to a window in that case, and showing a context menu against a detached
     * node isn't meaningful.
     */
    private void presentCameraChoices(Button chooseCameraButton, List<Integer> availableCameraIndices) {
        chooseCameraButton.setDisable(false);
        if (chooseCameraButton.getScene() == null || chooseCameraButton.getScene().getWindow() == null) {
            return;
        }

        if (availableCameraIndices.isEmpty()) {
            sharedCardScannerPane.setPreviewStatusText("No cameras were found.");
            return;
        }
        sharedCardScannerPane.setPreviewStatusText(" ");

        ContextMenu cameraMenu = new ContextMenu();
        for (int cameraIndex : availableCameraIndices) {
            MenuItem menuItem = new MenuItem("Camera " + cameraIndex);
            menuItem.setOnAction(event -> selectCamera(cameraIndex));
            cameraMenu.getItems().add(menuItem);
        }
        cameraMenu.show(chooseCameraButton, Side.BOTTOM, 0, 0);
    }

    /**
     * Records {@code cameraIndex} as {@link #selectedCameraIndex} and restarts the scanner
     * session on it. Always restarts rather than checking whether a session is currently live —
     * {@link CardScannerPane#getChooseCameraButton()} is only reachable while the scanner pane is
     * showing, so a restart is always what picking a camera from that pane should do, including
     * when the previous session's camera failed to open in the first place.
     */
    private void selectCamera(int cameraIndex) {
        selectedCameraIndex = cameraIndex;
        stopCardScanner();
        startCardScanner();
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
            if (debouncer.isLocked() && System.currentTimeMillis() < matchResumeAtMillis) {
                feedDebouncerWithoutMatching(recognizedCandidates);
                return;
            }

            Optional<CardTextMatcher.Resolution> resolution = CardTextMatcher.matchCandidates(recognizedCandidates);
            if (resolution.isEmpty()) {
                // The debounce lock releasing here (the card leaving frame for a continuous
                // 0.5s+) deliberately does *not* clear any printCode buttons or artwork gallery
                // still showing from the last detection — see this class's own Unit 10 javadoc
                // note. They stay clickable so a card can still be added, or added again, once
                // it's no longer in frame; they're only replaced once a new detection actually
                // resolves, in handleCandidateResolution/handleUnambiguousResolution below.
                debouncer.onNoConfidentDetection();
                return;
            }

            boolean addEligible = debouncer.onConfidentDetection();
            if (debouncer.isLocked()) {
                // A real match attempt just ran and ended up locked, whether this is the
                // detection that just locked it or a later resume tick reconfirming the same
                // card — either way, the next MATCH_PAUSE_AFTER_LOCK_MILLIS worth of detections
                // can skip full matching again. Left unset while released, so an unresolved card
                // still gets a full attempt on every following cycle.
                matchResumeAtMillis = System.currentTimeMillis() + MATCH_PAUSE_AFTER_LOCK_MILLIS;
            }
            if (!addEligible) {
                return; // still locked onto an earlier detection; this is a continuation, not a new add
            }

            activeTabIndex = mainTabPane.getSelectionModel().getSelectedIndex();
            activeTreeView = activeMiddleTreeViewSupplier.get();

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
     * The cheap substitute for a full {@link CardTextMatcher#matchCandidates} call while
     * {@link #handleDetectionEvent} is inside {@link #matchResumeAtMillis}'s pause window —
     * feeds {@link #debouncer} the same confident/not-confident signal a real match attempt would
     * have, using only whether OCR read anything at all this cycle. {@link ScanLockDebouncer}'s
     * release timing is wall-clock-based, not cycle-count-based (see its own javadoc), so this
     * keeps the lock/release behavior identical to running a full match every cycle — a genuine
     * match attempt still happens as soon as the pause elapses, so nothing here can leave the
     * debouncer locked forever on stale state. Never touches {@link #sharedCardScannerPane},
     * {@link #activeCardCandidates}, or any insertion.
     *
     * @param recognizedCandidates this cycle's OCR candidate lines, as passed to
     *                             {@link #handleDetectionEvent}
     */
    private void feedDebouncerWithoutMatching(List<String> recognizedCandidates) {
        if (recognizedCandidates == null || recognizedCandidates.isEmpty()) {
            debouncer.onNoConfidentDetection();
        } else {
            debouncer.onConfidentDetection();
        }
    }

    /**
     * Clears both halves of the printCode/artwork selection matrix and hides the artwork gallery
     * — called from {@link #handleCandidateResolution} and {@link #handleUnambiguousResolution}
     * at the start of a genuinely new detection cycle (see {@link #handleDetectionEvent}'s
     * {@code addEligible} check), so it never inherits stale selection state from whichever card
     * was in frame before. As of Unit 10, this deliberately does <em>not</em> run just because the
     * debounce lock releases or an add completes — see this class's own javadoc note.
     */
    private void resetCandidateSelectionState() {
        if (sharedCardScannerPane != null) {
            sharedCardScannerPane.clearPrintCodeCandidates();
        }
        hideArtworkGallery();
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
        resetCandidateSelectionState();
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
     * decided once for the whole set (see {@link #buildPaneCandidates}), not per button. When
     * {@code getArtworkOptions().size() > 1}, this also renders the artwork gallery via
     * {@link #showArtworkGalleryInRightContentPane} alongside the printCode buttons.
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
            resetCandidateSelectionState();
            sharedCardScannerPane.setDetectionFeedbackText(
                    "Multiple printings detected" + forCardName
                            + " \u2014 scanning to add isn't wired up for this tab yet");
            return;
        }

        boolean singleArtwork = cardCandidates.getArtworkOptions().size() <= 1;
        activeCardCandidates = singleArtwork ? null : cardCandidates;
        sharedCardScannerPane.showPrintCodeCandidates(
                buildPaneCandidates(cardCandidates, singleArtwork, activeTabIndex, activeTreeView));
        if (singleArtwork) {
            hideArtworkGallery();
        } else {
            showArtworkGalleryInRightContentPane(cardCandidates);
        }
        sharedCardScannerPane.setDetectionFeedbackText(
                "Multiple printings detected" + forCardName + (singleArtwork
                        ? " \u2014 choose a printCode to add"
                        : " \u2014 choose a printCode and an artwork to add"));
    }

    /**
     * Mounts {@link #sharedArtworkGallery} (creating it the first time) into whichever
     * {@code rightContentPane} {@link #activeRightContentPaneSupplier} currently returns, as a
     * sibling of that pane's existing content rather than replacing it, and renders one tile per
     * {@link CardTextMatcher.CardCandidates#getArtworkOptions()} entry.
     */
    private void showArtworkGalleryInRightContentPane(CardTextMatcher.CardCandidates cardCandidates) {
        AnchorPane rightContentPane = activeRightContentPaneSupplier.get();
        if (rightContentPane == null) {
            logger.warn("No active rightContentPane available to show the artwork gallery in; "
                    + "printCode buttons will still render, but artwork disambiguation is unavailable "
                    + "for this detection.");
            return;
        }
        if (sharedArtworkGallery == null) {
            sharedArtworkGallery = new CardScannerArtworkGallery(cardWidthProperty, cardHeightProperty);
        }
        if (!rightContentPane.getChildren().contains(sharedArtworkGallery)) {
            rightContentPane.getChildren().add(sharedArtworkGallery);
            AnchorPane.setTopAnchor(sharedArtworkGallery, 0.0);
            AnchorPane.setBottomAnchor(sharedArtworkGallery, 0.0);
            AnchorPane.setLeftAnchor(sharedArtworkGallery, 0.0);
            AnchorPane.setRightAnchor(sharedArtworkGallery, 0.0);
        }
        sharedArtworkGallery.showArtworkOptions(
                CardScannerArtworkResolver.buildArtworkOptions(cardCandidates.getArtworkOptions()),
                this::onArtworkTileClicked);
        sharedCardScannerPane.getSelectedArtworkId().ifPresent(sharedArtworkGallery::setSelectedArtwork);
    }

    /**
     * Hides {@link #sharedArtworkGallery} if it's currently showing, restoring whichever tab's
     * normal card-grid content it was covering — a no-op if the gallery was never created or
     * isn't currently mounted anywhere. Never removes the gallery node from its parent; only
     * {@link CardScannerArtworkGallery#clearArtworkOptions()}'s own visibility toggle controls
     * whether it's seen, so re-showing it later doesn't need to re-parent it.
     */
    private void hideArtworkGallery() {
        activeCardCandidates = null;
        if (sharedArtworkGallery != null) {
            sharedArtworkGallery.clearArtworkOptions();
        }
    }

    /**
     * Click handler for one artwork tile, wired via {@link #showArtworkGalleryInRightContentPane}.
     * Reports the click to {@link #sharedCardScannerPane} via
     * {@link CardScannerPane#onArtworkSelected(String, boolean)} — which owns the actual
     * click-matrix decision (add now vs. just select, see its own javadoc) — then restyles the
     * clicked tile as selected regardless of which outcome came back. As of Unit 10 the gallery
     * stays showing either way: a completed add no longer clears it (see this class's own javadoc
     * note), so the clicked tile simply stays highlighted, ready for another add.
     *
     * @param artworkId  the clicked tile's identifier
     * @param selectOnly whether the click was CTRL/Cmd-held, forwarded to
     *                   {@link CardScannerPane#onArtworkSelected(String, boolean)} unchanged
     */
    private void onArtworkTileClicked(String artworkId, boolean selectOnly) {
        sharedCardScannerPane.onArtworkSelected(artworkId, selectOnly);
        if (sharedArtworkGallery != null) {
            sharedArtworkGallery.setSelectedArtwork(artworkId);
        }
    }

    /**
     * Completes a printCode+artwork add reported by {@link CardScannerPane}'s click matrix (see
     * {@link CardScannerPane#setOnCandidateAdd}) — the counterpart to
     * {@link #insertPrintCode}/{@link #insertResolvedCard} for the artwork-disambiguation path.
     * Resolves {@code artworkId} back to its {@link Card} via
     * {@link CardScannerArtworkResolver#resolveArtworkCard}, against {@link #activeCardCandidates}
     * (set by {@link #handleCandidateResolution} for exactly this purpose) — see this class's own
     * javadoc note on why an artwork option's {@link Card} and a printCode option are two
     * independent axes with no existing combined lookup: the artwork card already carries the
     * correct identity/image for the artwork half, and setting its printCode field is how
     * {@link Model.CardsLists.CardElement#toCollectionString()} persists the printCode half
     * alongside it, without this class needing a new database lookup for the combined pair.
     *
     * <p>Copies the resolved card via {@link Card#Card(Card)} before setting its printCode and
     * inserting it — {@link #activeCardCandidates} (and the {@link Card} this resolves to inside
     * it) can now, as of Unit 10, be reused across several adds from the same detection, so
     * mutating the resolved card in place would corrupt whichever previous add already inserted a
     * {@link Model.CardsLists.CardElement} pointing at that same instance (see the copy
     * constructor's own javadoc for exactly how). The copy is what actually gets inserted;
     * {@link #activeCardCandidates}'s own artwork list is never mutated by this method.
     */
    private void completeArtworkDisambiguationAdd(String printCode, String artworkId) {
        if (activeCardCandidates == null) {
            logger.warn("Received a printCode+artwork add callback with no active candidates set; ignoring "
                    + "(printCode={}, artworkId={})", printCode, artworkId);
            return;
        }
        Card artworkCard = CardScannerArtworkResolver.resolveArtworkCard(
                activeCardCandidates.getArtworkOptions(), artworkId);
        if (artworkCard == null) {
            logger.warn("Could not resolve artwork identifier \"{}\" back to a Card among the active "
                    + "candidates; ignoring click", artworkId);
            sharedCardScannerPane.setDetectionFeedbackText(
                    "Couldn't find that artwork. See the application log for details.");
            return;
        }
        Card cardToInsert = new Card(artworkCard);
        cardToInsert.setPrintCode(printCode);
        insertResolvedCard(cardToInsert, activeTabIndex, activeTreeView);
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
     *                      button gets a {@code null} callback, since a multi-artwork candidate's
     *                      printCode buttons only ever complete an add via
     *                      {@link CardScannerPane#toggleMultiArtworkSelection} (an artwork already
     *                      selected) or the artwork gallery's own click (see
     *                      {@link #onArtworkTileClicked}), never directly
     */
    private List<CardScannerPane.PrintCodeCandidate> buildPaneCandidates(
            CardTextMatcher.CardCandidates cardCandidates, boolean singleArtwork,
            int activeTabIndex, TreeView<String> activeTreeView) {
        List<CardScannerPane.PrintCodeCandidate> paneCandidates = new ArrayList<>();
        for (CardTextMatcher.CardCandidates.PrintCodeOption printCodeOption : cardCandidates.getPrintCodeOptions()) {
            String printCode = printCodeOption.getPrintCode();
            // Buttons show only the printCode itself now — every candidate offered here is
            // already narrowed to the language(s) the detected card name is actually in (see
            // CardTextMatcher.narrowByDetectedName), so an explicit "(EN)"/"(FR)" suffix on the
            // button no longer adds information and was cluttering the label.
            String displayLabel = printCode;
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
     * Shared by the unambiguous auto-add path, a single-artwork printCode button click, and a
     * completed printCode+artwork add: inserts {@code card} via
     * {@link MiddleSelectionActionHandler#insertCardsAtQuickAddTarget} and reports what was
     * added. As of Unit 10, this no longer clears any printCode candidates or artwork gallery
     * left showing — see this class's own javadoc note — so the same buttons stay available to
     * add another copy of the same card, or a different candidate from the same detection.
     */
    private void insertResolvedCard(Card card, int activeTabIndex, TreeView<String> activeTreeView) {
        MiddleSelectionActionHandler.insertCardsAtQuickAddTarget(List.of(card), activeTabIndex, activeTreeView);
        sharedCardScannerPane.setDetectionFeedbackText("Added: " + card.getNameOrNumber());
    }
}