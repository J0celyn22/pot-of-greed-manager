package View;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

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
 */
public class CardScannerPane extends VBox {

    private final Button closeButton;
    private final Label previewStatusLabel;
    private final StackPane previewContainer;

    /**
     * Lazily created the first time {@link #showPreviewFrame(Image)} is called, and torn back
     * down by {@link #resetPreview()} — so re-opening the scanner starts back at the plain
     * status label rather than showing a stale frame from whatever session last used this
     * shared pane instance.
     */
    private ImageView previewImageView;

    public CardScannerPane() {
        this.setStyle("-fx-background-color: #100317;");
        this.getStyleClass().add("camera-scanner-pane");
        this.setSpacing(8);
        this.setPadding(new Insets(8));

        Label titleLabel = new Label("Card Scanner");
        titleLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 14; -fx-font-weight: bold;");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        closeButton = new Button("Close");
        closeButton.setPrefWidth(80);

        HBox topBar = new HBox(10, titleLabel, closeButton);
        topBar.setAlignment(Pos.CENTER_LEFT);

        previewStatusLabel = new Label("Camera preview will appear here.");
        previewStatusLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12;");
        previewStatusLabel.setWrapText(true);
        previewStatusLabel.setTextAlignment(TextAlignment.CENTER);

        previewContainer = new StackPane(previewStatusLabel);
        previewContainer.setStyle(
                "-fx-background-color: #1a0a26; -fx-border-color: #cdfc04; -fx-border-width: 1;");
        previewContainer.setPadding(new Insets(10));
        VBox.setVgrow(previewContainer, Priority.ALWAYS);
        // A StackPane's own preferred/min size is computed from its children by default, but
        // previewImageView's fit dimensions are bound back to this container's width/height
        // (see showPreviewFrame). Leaving the container's size computed would make that a
        // circular dependency, growing a little further every frame instead of settling. Pinning
        // it here means only VBox's vgrow(ALWAYS) above decides this pane's actual size.
        previewContainer.setMinSize(0, 0);
        previewContainer.setPrefSize(0, 0);

        this.getChildren().addAll(topBar, previewContainer);
    }

    public Button getCloseButton() {
        return closeButton;
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
     * Renders a single live preview frame. The first call replaces {@link #getPreviewContainer()}'s
     * status-label content with an {@link ImageView} sized to fill the container while
     * preserving the frame's aspect ratio; every call after that just swaps the displayed
     * image, without rebuilding the view.
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
        previewImageView.setImage(frame);
    }

    /**
     * Clears any live frame that was being shown and restores the plain status label, so the
     * next time this pane is opened it starts from a known, non-stale state rather than
     * displaying whatever frame was on screen when it was last closed. Has no effect on the
     * label's text beyond restoring it to being visible again — callers that want a specific
     * message showing (e.g. "Starting camera…") should call {@link #setPreviewStatusText(String)}
     * afterward.
     */
    public void resetPreview() {
        if (previewImageView != null) {
            previewContainer.getChildren().setAll(previewStatusLabel);
            previewImageView = null;
        }
    }
}