package View;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

/**
 * CardScannerPane — swapped into the header row's right pane in place of {@link FilterPane}
 * while the camera card-scanner is active.
 *
 * <p>This is the Unit 2 skeleton for the camera card-scanner feature (see the project's
 * camera-scanner plan doc): a title row with a close button, and a placeholder area where the
 * live camera preview will be rendered once the Python capture/detection sidecar exists. It has
 * no camera, subprocess, or detection logic of its own — {@link #getPreviewContainer()} is
 * exposed so a later unit can drop a live preview node into the same placeholder area without
 * reworking this pane's layout.
 */
public class CardScannerPane extends VBox {

    private final Button closeButton;
    private final Label previewStatusLabel;
    private final StackPane previewContainer;

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
}