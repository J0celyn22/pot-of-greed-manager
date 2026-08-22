package View;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;

import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * CardScannerPane — swapped into the header row's right pane in place of {@link FilterPane}
 * while the camera card-scanner is active.
 *
 * <p>This started as the Unit 2 skeleton for the camera card-scanner feature (see the project's
 * camera-scanner plan doc): a title row with a close button, and a placeholder area where the
 * live camera preview would be rendered once the Python capture sidecar existed.
 * {@link #showPreviewFrame(Image)} is Unit 4's addition — it renders the actual live feed into
 * that same placeholder area, replacing the status label with an {@link ImageView} the first
 * time it's called. This pane still has no subprocess or detection logic of its own; frames
 * come from whichever bridge class the owning controller wires up (see
 * {@code Model.CardScanner.PythonCardScannerBridge}), not from anything inside this class.
 *
 * <p>{@link #setDetectionFeedbackText(String)} is Unit 6's addition — a one-line status shown
 * below the preview reporting what the detection loop just did. Like the preview frames
 * themselves, this pane only displays what it's told; the actual OCR/matching/insertion
 * decisions live in {@code Controller.CardScannerCoordinator}.
 *
 * <p>Unit 8 reworks the main content area from a single full-width preview into a two-column
 * row: the live preview stays on the left, and a new right-hand area
 * ({@link #showPrintCodeCandidates(List)}) renders one button per printCode candidate whenever
 * a detection resolves to a name shared by several printed versions of the same card. Like the
 * preview and feedback label, this pane only renders what it's told and reports selection state
 * back out — it has no opinion on how a {@code CardCandidates} result gets turned into
 * {@link PrintCodeCandidate} objects; that translation lives in
 * {@code Controller.CardScannerCoordinator}.
 *
 * <p>Unit 9 completes the artwork-disambiguation path for printCode candidates that have more
 * than one artwork. The header-row strip built by Unit 8 doesn't have width left for a third
 * region, so the artwork images themselves don't live in this class at all — they render as
 * wrapping rows inside the active tab's {@code rightContentPane}, owned and swapped in/out by
 * {@code Controller.CardScannerCoordinator}. This class's own share of Unit 9 is the selection
 * state and the click matrix: {@link #getSelectedPrintCode()} already existed for Unit 8;
 * {@link #onArtworkSelected(String)} lets the coordinator report an artwork click into this
 * class's own selection bookkeeping without this class needing to know anything about how
 * artwork images are rendered, and {@link #toggleMultiArtworkSelection} now completes an add
 * (instead of only selecting) when an artwork is already reported selected — see
 * {@link ClickOutcome} for the result a click can produce.
 *
 * <p>Unit 10 adds {@link #getChooseCameraButton()} next to the close button — like every other
 * button this class exposes, it only renders the control; {@code CardScannerCoordinator} owns
 * what clicking it actually does (probing for cameras and restarting the sidecar on whichever
 * one gets picked).
 */
public class CardScannerPane extends VBox {

    private final Button closeButton;
    private final Button chooseCameraButton;
    private final Label previewStatusLabel;
    private final StackPane previewContainer;
    private final Label detectionFeedbackLabel;
    private final TilePane candidatesTilePane;
    private final ScrollPane candidatesScrollPane;

    /**
     * Lazily created the first time {@link #showPreviewFrame(Image)} is called, and torn back
     * down by {@link #resetPreview()} — so re-opening the scanner starts back at the plain
     * status label rather than showing a stale frame from whatever session last used this
     * shared pane instance.
     */
    private ImageView previewImageView;

    /**
     * The aspect ratio (width / height) {@link #previewContainer}'s {@code maxWidth} is currently
     * bound to, or {@code -1} if it isn't bound to one yet (before the first frame, or since the
     * last {@link #resetPreview()}). Tracked so {@link #showPreviewFrame(Image)} only re-binds
     * when a frame's dimensions actually differ from the last one, rather than replacing an
     * identical binding on every single frame of a live video feed.
     */
    private double boundPreviewAspectRatio = -1;

    /**
     * Whichever multi-artwork printCode button is currently marked "selected," or {@code null}
     * when none is. Single-artwork buttons never populate this field — clicking one adds
     * immediately instead of selecting (see {@link PrintCodeCandidate#hasSingleArtwork()}).
     */
    private Button selectedMultiArtworkButton;
    private String selectedPrintCode;

    /**
     * The artwork identifier last reported via {@link #onArtworkSelected(String)}, or
     * {@code null} once cleared via {@link #clearPrintCodeCandidates()}. This class has no
     * opinion on what the identifier actually is or how the artwork it names gets rendered —
     * {@code CardScannerCoordinator} owns both — it only tracks whether one is currently
     * selected, the same role {@link #selectedPrintCode} already plays for printCode buttons.
     */
    private String selectedArtworkId;

    /**
     * Invoked once per successful add completed by the artwork half of Unit 9's click matrix: a
     * multi-artwork printCode button clicked while an artwork was already selected (see
     * {@link #toggleMultiArtworkSelection}), or an artwork click reported via
     * {@link #onArtworkSelected(String)} while a printCode was already selected. Receives the
     * {@code (printCode, artworkId)} pair that was just completed. Not invoked for a
     * single-artwork printCode button click — that path still calls
     * {@link PrintCodeCandidate#onAdd()} directly, the same as Unit 8, since there's no artwork
     * identifier to report in that case. {@code CardScannerCoordinator} wires this to complete
     * the actual database lookup and insert.
     */
    private BiConsumer<String, String> onCandidateAdd;

    public CardScannerPane() {
        this.setStyle("-fx-background-color: #100317;");
        this.getStyleClass().add("camera-scanner-pane");
        this.setSpacing(8);
        this.setPadding(new Insets(8));

        Label titleLabel = new Label("Card Scanner");
        titleLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 14; -fx-font-weight: bold;");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        chooseCameraButton = new Button("Camera");
        chooseCameraButton.setPrefWidth(80);

        closeButton = new Button("Close");
        closeButton.setPrefWidth(80);

        HBox topBar = new HBox(10, titleLabel, chooseCameraButton, closeButton);
        topBar.setAlignment(Pos.CENTER_LEFT);

        previewStatusLabel = new Label("Camera preview will appear here.");
        previewStatusLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12;");
        previewStatusLabel.setWrapText(true);
        previewStatusLabel.setTextAlignment(TextAlignment.CENTER);

        previewContainer = new StackPane(previewStatusLabel);
        previewContainer.setStyle(
                "-fx-background-color: #1a0a26; -fx-border-color: #cdfc04; -fx-border-width: 1;");
        previewContainer.setPadding(new Insets(10));
        HBox.setHgrow(previewContainer, Priority.ALWAYS);
        // A StackPane's own preferred/min size is computed from its children by default, but
        // previewImageView's fit dimensions are bound back to this container's width/height
        // (see showPreviewFrame). Leaving the container's size computed would make that a
        // circular dependency, growing a little further every frame instead of settling. Pinning
        // it here means only the row's hgrow(ALWAYS) above (bounded by maxWidth once a frame's
        // aspect ratio is known — see showPreviewFrame) decides this pane's actual size.
        previewContainer.setMinSize(0, 0);
        previewContainer.setPrefSize(0, 0);

        candidatesTilePane = buildCandidatesTilePane();
        candidatesScrollPane = buildCandidatesScrollPane(candidatesTilePane);

        HBox mainContentRow = new HBox(8, previewContainer, candidatesScrollPane);
        VBox.setVgrow(mainContentRow, Priority.ALWAYS);

        detectionFeedbackLabel = new Label(" ");
        detectionFeedbackLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 12; -fx-font-weight: bold;");
        detectionFeedbackLabel.setWrapText(true);
        detectionFeedbackLabel.setTextAlignment(TextAlignment.CENTER);
        detectionFeedbackLabel.setMaxWidth(Double.MAX_VALUE);
        detectionFeedbackLabel.setAlignment(Pos.CENTER);

        this.getChildren().addAll(topBar, mainContentRow, detectionFeedbackLabel);
    }

    /**
     * Builds the vertical-orientation tile pane that lays out one button per printCode
     * candidate. {@link Orientation#VERTICAL} fills a column top-to-bottom and starts a new
     * column to the right once the available height runs out, which is exactly the "column,
     * then overflow into a second column" behavior Unit 8 calls for — no manual wrap-tracking
     * needed. Tile height is left unset so it derives from the buttons' own preferred height;
     * only the width is pinned so labels of different lengths don't produce ragged columns.
     */
    private TilePane buildCandidatesTilePane() {
        TilePane tilePane = new TilePane(Orientation.VERTICAL);
        tilePane.setHgap(6);
        tilePane.setVgap(6);
        tilePane.setPrefTileWidth(150);
        tilePane.setTileAlignment(Pos.CENTER_LEFT);
        tilePane.setStyle("-fx-background-color: #100317;");
        return tilePane;
    }

    /**
     * Wraps {@code tilePane} in a {@link ScrollPane} so a card with an unusually large number
     * of printCodes (more columns than fit the pane's width) is reachable by scrolling
     * horizontally instead of squeezing the preview down or clipping candidates outright.
     * Hidden and unmanaged by default — {@link #showPrintCodeCandidates(List)} reveals it, and
     * {@link #clearPrintCodeCandidates()} hides it again, so the preview gets the full row width
     * back whenever there are no candidates to show.
     */
    private ScrollPane buildCandidatesScrollPane(TilePane tilePane) {
        ScrollPane scrollPane = new ScrollPane(tilePane);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background: #100317; -fx-background-color: #100317;"
                + "-fx-border-color: #cdfc04; -fx-border-width: 1;");
        // Wide enough for at least two 150px tile columns (see buildCandidatesTilePane) plus
        // hgap and the pane's own border/scrollbar allowance — previously pinned to 170 (one
        // column), which meant a card with more printCodes than fit one column always overflowed
        // into a horizontal scrollbar instead of actually using a second column.
        scrollPane.setPrefWidth(330);
        scrollPane.setMaxWidth(500);
        scrollPane.setManaged(false);
        scrollPane.setVisible(false);
        return scrollPane;
    }

    public Button getCloseButton() {
        return closeButton;
    }

    /**
     * The button that lets the person pick which camera device to scan from. Clicking it has no
     * behavior of its own here — {@code CardScannerCoordinator} wires the click, the same way it
     * wires {@link #getCloseButton()}.
     */
    public Button getChooseCameraButton() {
        return chooseCameraButton;
    }

    /**
     * The placeholder area a later unit will render the live camera preview into. Currently
     * holds only a status label; a later unit can clear/replace its content (e.g. with an
     * {@code ImageView}) without needing to rebuild the surrounding title bar or close button.
     */
    public StackPane getPreviewContainer() {
        return previewContainer;
    }

    /**
     * Updates the placeholder's status text (e.g. "Starting camera…", "Camera unavailable").
     * Has no effect once a later unit replaces {@link #getPreviewContainer()}'s content with an
     * actual preview node instead of this label.
     */
    public void setPreviewStatusText(String text) {
        previewStatusLabel.setText(text);
    }

    /**
     * Updates the feedback line shown below the preview — Unit 6's addition, reporting what the
     * detection loop just did (e.g. "Added: Dark Magician", "Multiple printings detected",
     * "Scanning isn't wired up for this tab yet"). A blank string clears it back to empty rather
     * than leaving stale text showing between detections.
     */
    public void setDetectionFeedbackText(String text) {
        detectionFeedbackLabel.setText(text == null || text.isBlank() ? " " : text);
    }

    /**
     * Renders a single live preview frame. The first call replaces {@link #getPreviewContainer()}'s
     * status-label content with an {@link ImageView} sized to fill the container while
     * preserving the frame's aspect ratio; every call after that just swaps the displayed
     * image, without rebuilding the view.
     *
     * <p>Also (re-)binds {@link #previewContainer}'s {@code maxWidth} to {@code height * aspect
     * ratio} the first time a frame arrives, and again if a later frame's aspect ratio actually
     * differs (see {@link #boundPreviewAspectRatio}) — without this, {@code HBox.hgrow(ALWAYS)}
     * alone would let the container stretch across all leftover row width regardless of the
     * video's actual shape, leaving it letterboxed and looking off-center rather than flush
     * against the preview column's left edge.
     *
     * @param frame a decoded preview frame, as produced by
     *              {@code Model.CardScanner.PythonCardScannerBridge}. Must be called on the
     *              JavaFX application thread, same as any other scene-graph update.
     */
    public void showPreviewFrame(Image frame) {
        if (previewImageView == null) {
            previewImageView = new ImageView();
            previewImageView.setPreserveRatio(true);
            previewImageView.fitWidthProperty().bind(previewContainer.widthProperty());
            previewImageView.fitHeightProperty().bind(previewContainer.heightProperty());
            previewContainer.getChildren().setAll(previewImageView);
        }
        if (frame != null && frame.getWidth() > 0 && frame.getHeight() > 0) {
            double aspectRatio = frame.getWidth() / frame.getHeight();
            if (aspectRatio != boundPreviewAspectRatio) {
                // height * aspectRatio is almost never a whole-pixel value, but Region snaps its
                // own layout bounds to whole pixels while the border stroke is drawn against
                // those snapped bounds. Binding maxWidth to the raw fractional value left a
                // mismatch between what the HBox allotted and where the border actually landed,
                // showing a hairline of the pane's background past the border on the right edge.
                // Flooring keeps the bound width from ever landing above the snapped size the
                // border is drawn at.
                previewContainer.maxWidthProperty().bind(
                        previewContainer.heightProperty().multiply(aspectRatio)
                                .map(value -> Math.floor(value.doubleValue())));
                boundPreviewAspectRatio = aspectRatio;
            }
        }
        previewImageView.setImage(frame);
    }

    /**
     * Clears any live frame that was being shown and restores the plain status label, so the
     * next time this pane is opened it starts from a known, non-stale state rather than
     * displaying whatever frame was on screen when it was last closed. Also unbinds
     * {@link #previewContainer}'s {@code maxWidth} back to unbounded, so the status label goes
     * back to filling the full row width until the next session's first frame re-establishes an
     * aspect ratio to match, and clears any printCode candidates from a previous session (see
     * {@link #clearPrintCodeCandidates()}). Has no effect on the label's text beyond restoring it
     * to being visible again — callers that want a specific message showing (e.g. "Starting
     * camera…") should call {@link #setPreviewStatusText(String)} afterward.
     */
    public void resetPreview() {
        if (previewImageView != null) {
            previewContainer.getChildren().setAll(previewStatusLabel);
            previewImageView = null;
        }
        previewContainer.maxWidthProperty().unbind();
        previewContainer.setMaxWidth(Region.USE_COMPUTED_SIZE);
        boundPreviewAspectRatio = -1;
        clearPrintCodeCandidates();
        setDetectionFeedbackText(null);
    }

    /**
     * Registers the callback invoked once per successful add completed by the artwork half of
     * Unit 9's click matrix (a multi-artwork printCode button clicked while an artwork was
     * already selected, or an artwork click while a printCode was already selected — see
     * {@link #onCandidateAdd}). {@code CardScannerCoordinator} calls this once, the same way it
     * wires {@link #getCloseButton()}'s action, rather than this being passed per-candidate the
     * way {@link PrintCodeCandidate#onAdd()} is: a printCode-and-artwork add can be completed by
     * either half of the pair being clicked last, so one pane-level callback is simpler than
     * trying to thread the same callback through both a {@link PrintCodeCandidate} and every
     * artwork the coordinator renders into {@code rightContentPane}.
     *
     * @param onCandidateAdd receives {@code (printCode, artworkId)} for a completed
     *                       printCode+artwork add; {@code artworkId} is whatever opaque string
     *                       {@code CardScannerCoordinator} passed to
     *                       {@link #onArtworkSelected(String)} for the artwork half of the pair
     */
    public void setOnCandidateAdd(BiConsumer<String, String> onCandidateAdd) {
        this.onCandidateAdd = onCandidateAdd;
    }

    /**
     * Renders one button per candidate in the right-hand column area, replacing whatever was
     * shown there before. Reveals {@link #candidatesScrollPane} if it was hidden. Passing an
     * empty or {@code null} list is equivalent to calling {@link #clearPrintCodeCandidates()}.
     *
     * @param candidates every printCode candidate for the name match currently in frame, in the
     *                   order they should be rendered
     */
    public void showPrintCodeCandidates(List<PrintCodeCandidate> candidates) {
        clearPrintCodeCandidates();
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (PrintCodeCandidate candidate : candidates) {
            candidatesTilePane.getChildren().add(buildCandidateButton(candidate));
        }
        candidatesScrollPane.setManaged(true);
        candidatesScrollPane.setVisible(true);
    }

    /**
     * Removes every printCode candidate button, clears any multi-artwork and artwork selection,
     * and hides the candidates column so the preview reclaims the full row width. Called before
     * rendering a fresh set of candidates, and whenever the pane needs to return to "just the
     * preview" (a new scanning session starting, or a successful add) — see "Unit 9 — decided"'s
     * reset-on-lock-release rule, which {@code CardScannerCoordinator} ties to this same call.
     *
     * <p>Only clears this class's own printCode/artwork-selection bookkeeping — the artwork
     * images themselves live in {@code rightContentPane}, owned by
     * {@code CardScannerCoordinator}, which clears them separately when it calls this method.
     */
    public void clearPrintCodeCandidates() {
        candidatesTilePane.getChildren().clear();
        selectedMultiArtworkButton = null;
        selectedPrintCode = null;
        selectedArtworkId = null;
        candidatesScrollPane.setManaged(false);
        candidatesScrollPane.setVisible(false);
    }

    /**
     * @return the printCode of whichever multi-artwork candidate button is currently marked
     * "selected," or empty if none is. {@code CardScannerCoordinator} reads this to decide
     * whether clicking an artwork image should add immediately (a printCode is already selected)
     * or just select the artwork and wait for a printCode click.
     */
    public Optional<String> getSelectedPrintCode() {
        return Optional.ofNullable(selectedPrintCode);
    }

    /**
     * @return the artwork identifier last reported via {@link #onArtworkSelected(String)}, or
     * empty if none is currently selected. Mirrors {@link #getSelectedPrintCode()}'s role for the
     * other half of the click matrix — {@code CardScannerCoordinator} can read this before
     * rendering a fresh artwork gallery to restyle whichever image was already selected as still
     * selected, without this class needing to know how artwork images are drawn.
     */
    public Optional<String> getSelectedArtworkId() {
        return Optional.ofNullable(selectedArtworkId);
    }

    /**
     * Reports that {@code artworkId} was just clicked in the (externally rendered) artwork
     * gallery. Implements the artwork half of Unit 9's click matrix:
     * <ul>
     *   <li>a printCode is already selected — completes the add via {@link #onCandidateAdd} for
     *       {@code (selectedPrintCode, artworkId)}, then clears all selection state via
     *       {@link #clearPrintCodeCandidates()} (the "successful add clears everything" rule);</li>
     *   <li>no printCode selected yet — just records {@code artworkId} as selected, matching
     *       {@link #toggleMultiArtworkSelection}'s "select and wait" behavior for a printCode
     *       button click with nothing selected on the other axis yet.</li>
     * </ul>
     *
     * @param artworkId opaque identifier for the clicked artwork, round-tripped back to the
     *                  caller unchanged via {@link #onCandidateAdd} — this class never inspects it
     * @return which outcome this click produced, so the caller (which owns the artwork gallery's
     * visuals) knows whether to mark {@code artworkId} selected or clear the whole gallery
     */
    public ClickOutcome onArtworkSelected(String artworkId) {
        if (selectedPrintCode != null) {
            String printCodeToAdd = selectedPrintCode;
            if (onCandidateAdd != null) {
                onCandidateAdd.accept(printCodeToAdd, artworkId);
            }
            clearPrintCodeCandidates();
            return ClickOutcome.ADDED;
        }
        selectedArtworkId = artworkId;
        return ClickOutcome.SELECTED;
    }

    private Button buildCandidateButton(PrintCodeCandidate candidate) {
        Button button = new Button(candidate.displayLabel());
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setPrefHeight(28);
        if (candidate.hasSingleArtwork()) {
            applyAddableButtonStyle(button);
            button.setOnAction(event -> candidate.onAdd().run());
        } else {
            applyMultiArtworkButtonStyle(button, false);
            button.setOnAction(event -> toggleMultiArtworkSelection(button, candidate.printCode()));
        }
        return button;
    }

    /**
     * Click handler for a multi-artwork printCode button — the printCode half of Unit 9's click
     * matrix:
     * <ul>
     *   <li>an artwork is already selected — completes the add via {@link #onCandidateAdd} for
     *       {@code (printCode, selectedArtworkId)}, then clears all selection state (the
     *       "successful add clears everything" rule);</li>
     *   <li>no artwork selected yet, and this button wasn't already selected — selects it,
     *       replacing any other selected printCode button (single-select, not multi);</li>
     *   <li>no artwork selected yet, and this button was already the selected one — deselects it,
     *       since a multi-artwork candidate never adds on its own with nothing on the artwork
     *       side chosen.</li>
     * </ul>
     */
    private void toggleMultiArtworkSelection(Button clickedButton, String printCode) {
        if (selectedArtworkId != null) {
            String artworkIdToAdd = selectedArtworkId;
            if (onCandidateAdd != null) {
                onCandidateAdd.accept(printCode, artworkIdToAdd);
            }
            clearPrintCodeCandidates();
            return;
        }

        boolean wasAlreadySelected = clickedButton.equals(selectedMultiArtworkButton);
        if (selectedMultiArtworkButton != null) {
            applyMultiArtworkButtonStyle(selectedMultiArtworkButton, false);
        }
        if (wasAlreadySelected) {
            selectedMultiArtworkButton = null;
            selectedPrintCode = null;
        } else {
            applyMultiArtworkButtonStyle(clickedButton, true);
            selectedMultiArtworkButton = clickedButton;
            selectedPrintCode = printCode;
        }
    }

    /**
     * Style for a printCode button whose click adds immediately (exactly one artwork) —
     * an outlined "action" look, distinct from the filled/unfilled toggle look used for
     * multi-artwork buttons, so the two click behaviors read differently at a glance.
     */
    private void applyAddableButtonStyle(Button button) {
        button.setStyle(
                "-fx-background-color: #1a0a26;"
                        + "-fx-text-fill: #cdfc04;"
                        + "-fx-border-color: #cdfc04;"
                        + "-fx-border-width: 1;"
                        + "-fx-font-size: 11px;"
                        + "-fx-cursor: hand;"
        );
    }

    /**
     * Selected/unselected style for a multi-artwork printCode button, matching the existing
     * toggle-button convention already used for {@code FilterPane}'s "Multiple artworks" filter
     * button — yellow-green fill with black text when selected, dark fill with yellow-green text
     * otherwise.
     */
    private void applyMultiArtworkButtonStyle(Button button, boolean selected) {
        if (selected) {
            button.setStyle(
                    "-fx-background-color: #cdfc04;"
                            + "-fx-text-fill: black;"
                            + "-fx-font-size: 11px;"
                            + "-fx-cursor: hand;"
            );
        } else {
            button.setStyle(
                    "-fx-background-color: #1a1a1a;"
                            + "-fx-text-fill: #cdfc04;"
                            + "-fx-font-size: 11px;"
                            + "-fx-cursor: hand;"
            );
        }
    }

    /**
     * What a click in the printCode/artwork selection matrix produced — returned by
     * {@link #onArtworkSelected(String)} so a caller that owns the artwork gallery's visuals
     * (which this class never renders itself) knows whether to mark the clicked artwork
     * "selected" or clear the whole gallery because an add just completed. The printCode half of
     * the matrix ({@link #toggleMultiArtworkSelection}) has no equivalent need — its own button
     * already lives inside this class, so it restyles itself directly instead of reporting a
     * result the caller would have to act on.
     */
    public enum ClickOutcome {
        /**
         * The click completed a printCode+artwork add; {@link #onCandidateAdd} was already
         * invoked and all selection state was already cleared via
         * {@link #clearPrintCodeCandidates()}.
         */
        ADDED,
        /**
         * The click only recorded a new selection; no add happened.
         */
        SELECTED
    }

    /**
     * One printCode candidate for a name match shared by several printed versions of the same
     * card, as built by {@code Controller.CardScannerCoordinator} from a {@code CardCandidates}
     * result.
     *
     * @param printCode        the printCode this candidate represents
     * @param displayLabel     the text shown on the button (e.g. printCode plus a parsed
     *                         language tag)
     * @param hasSingleArtwork whether this printCode has exactly one artwork. When {@code true},
     *                         clicking the button invokes {@link #onAdd()} immediately. When
     *                         {@code false}, clicking only marks the button "selected" unless an
     *                         artwork is already selected (see {@link #getSelectedArtworkId()}),
     *                         in which case it completes the add instead — the printCode half of
     *                         Unit 9's click matrix, implemented in
     *                         {@link #toggleMultiArtworkSelection}.
     * @param onAdd            invoked when the button is clicked and {@code hasSingleArtwork} is
     *                         {@code true}; must be non-null in that case, ignored otherwise
     */
    public record PrintCodeCandidate(
            String printCode, String displayLabel, boolean hasSingleArtwork, Runnable onAdd) {

        public PrintCodeCandidate {
            if (hasSingleArtwork && onAdd == null) {
                throw new IllegalArgumentException(
                        "onAdd must be provided for a single-artwork printCode candidate");
            }
        }
    }
}