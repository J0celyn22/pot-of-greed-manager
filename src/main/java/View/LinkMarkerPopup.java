package View;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Floating 3×3 grid popup for the Link Marker filter.
 * <p>
 * Layout (image sizes):
 * [TL 20×20] [T  30×20] [TR 20×20]
 * [L  20×30] [OK 30×30] [R  20×30]
 * [BL 20×20] [B  30×20] [BR 20×20]
 * <p>
 * Each arrow button toggles between Enabled / Disabled.
 * "OK" closes the popup.
 * No enabled markers → no link-marker filter applied.
 * <p>
 * Card linkMarker values: "Top-Left", "Top", "Top-Right",
 * "Left", "Right",
 * "Bottom-Left", "Bottom", "Bottom-Right"
 */
public class LinkMarkerPopup extends Popup {

    // ── Marker definitions (grid position → card value → image base name) ────
    private static final Object[][] MARKERS = {
            // { gridCol, gridRow, cardValue, imageBaseName, fitW, fitH }
            {0, 0, "Top-Left", "LM-TopLeft", 20, 20},
            {1, 0, "Top", "LM-Top", 30, 20},
            {2, 0, "Top-Right", "LM-TopRight", 20, 20},
            {0, 1, "Left", "LM-Left", 20, 30},
            {2, 1, "Right", "LM-Right", 20, 30},
            {0, 2, "Bottom-Left", "LM-BottomLeft", 20, 20},
            {1, 2, "Bottom", "LM-Bottom", 30, 20},
            {2, 2, "Bottom-Right", "LM-BottomRight", 20, 20},
    };

    // ── State ────────────────────────────────────────────────────────────────
    /**
     * Currently enabled markers (empty = no filter).
     */
    private final Set<String> enabledMarkers = new LinkedHashSet<>();
    // ── UI references so we can refresh images on load ───────────────────────
    private final ImageView[] markerViews = new ImageView[8];
    private final String[] markerValues = new String[8];
    /**
     * Fired whenever the enabled-marker set changes.
     */
    private Runnable onMarkersChanged;

    // ════════════════════════════════════════════════════════════════════════
    public LinkMarkerPopup() {
        setAutoHide(true);   // closes when clicking outside
        setAutoFix(true);    // keeps popup within screen bounds

        GridPane grid = new GridPane();
        grid.setHgap(3);
        grid.setVgap(3);
        grid.setStyle(
                "-fx-background-color: #100317;" +
                        "-fx-border-color: #cdfc04;" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 4;" +
                        "-fx-background-radius: 4;" +
                        "-fx-padding: 6;"
        );
        grid.setAlignment(Pos.CENTER);

        // ── Arrow buttons ─────────────────────────────────────────────────
        for (int i = 0; i < MARKERS.length; i++) {
            Object[] def = MARKERS[i];
            int col = (int) def[0];
            int row = (int) def[1];
            String cardVal = (String) def[2];
            String imgBase = (String) def[3];
            double fitW = ((Number) def[4]).doubleValue();
            double fitH = ((Number) def[5]).doubleValue();

            markerValues[i] = cardVal;

            ImageView iv = makeImageView(imgBase + "_Disabled.png", fitW, fitH);
            markerViews[i] = iv;

            Button btn = new Button();
            btn.setGraphic(iv);
            btn.setStyle(markerButtonStyle(false));
            btn.setPrefSize(fitW + 8, fitH + 8);
            btn.setMinSize(fitW + 8, fitH + 8);
            btn.setMaxSize(fitW + 8, fitH + 8);
            btn.setFocusTraversable(false);

            final int idx = i;
            btn.setOnMouseClicked(e -> {
                boolean nowEnabled = !enabledMarkers.contains(cardVal);
                if (nowEnabled) enabledMarkers.add(cardVal);
                else enabledMarkers.remove(cardVal);
                updateMarkerImage(idx, imgBase, fitW, fitH, nowEnabled);
                btn.setStyle(markerButtonStyle(nowEnabled));
                if (onMarkersChanged != null) onMarkersChanged.run();
            });

            grid.add(btn, col, row);
        }

        // ── OK button (center cell) ───────────────────────────────────────
        Button ok = new Button("OK");
        ok.setStyle(
                "-fx-background-color: #1e0530;" +
                        "-fx-text-fill: #cdfc04;" +
                        "-fx-font-size: 11px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-border-color: #cdfc04;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 3;" +
                        "-fx-background-radius: 3;" +
                        "-fx-cursor: hand;"
        );
        ok.setPrefSize(38, 38);
        ok.setMinSize(38, 38);
        ok.setMaxSize(38, 38);
        ok.setFocusTraversable(false);
        ok.setOnMouseClicked(e -> hide());
        ok.setOnMouseEntered(e -> ok.setStyle(
                "-fx-background-color: #9dc000;" +
                        "-fx-text-fill: black;" +
                        "-fx-font-size: 11px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-border-color: #cdfc04;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 3;" +
                        "-fx-background-radius: 3;" +
                        "-fx-cursor: hand;"
        ));
        ok.setOnMouseExited(e -> ok.setStyle(
                "-fx-background-color: #1e0530;" +
                        "-fx-text-fill: #cdfc04;" +
                        "-fx-font-size: 11px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-border-color: #cdfc04;" +
                        "-fx-border-width: 1;" +
                        "-fx-border-radius: 3;" +
                        "-fx-background-radius: 3;" +
                        "-fx-cursor: hand;"
        ));
        grid.add(ok, 1, 1);

        // Wrap in a VBox so the Popup has a proper root
        VBox root = new VBox(grid);
        root.setStyle("-fx-background-color: transparent;");
        getContent().add(root);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Public API
    // ════════════════════════════════════════════════════════════════════════

    private static String markerButtonStyle(boolean enabled) {
        String bg = enabled ? "#9dc000" : "#1e0530";
        String br = enabled ? "#9dc000" : "#cdfc04";
        return "-fx-background-color: " + bg + ";" +
                "-fx-border-color: " + br + ";" +
                "-fx-border-width: 1;" +
                "-fx-border-radius: 2;" +
                "-fx-background-radius: 2;" +
                "-fx-cursor: hand;" +
                "-fx-padding: 2;";
    }

    private static Image loadImage(String filename) {
        try {
            return new Image("file:./src/main/resources/" + filename);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the current set of enabled marker card-values (live, not a copy).
     */
    public Set<String> getEnabledMarkers() {
        return enabledMarkers;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Replaces the enabled-marker set and refreshes all button images.
     * Used when loading a saved page state.
     * Does NOT fire {@link #onMarkersChanged}.
     */
    public void setEnabledMarkers(Set<String> markers) {
        enabledMarkers.clear();
        if (markers != null) enabledMarkers.addAll(markers);
        refreshAllImages();
    }

    /**
     * Sets the callback fired whenever the enabled-marker set changes.
     */
    public void setOnMarkersChanged(Runnable callback) {
        this.onMarkersChanged = callback;
    }

    private void updateMarkerImage(int idx, String imgBase,
                                   double fitW, double fitH, boolean enabled) {
        String filename = imgBase + (enabled ? "_Enabled.png" : "_Disabled.png");
        Image img = loadImage(filename);
        if (img != null) markerViews[idx].setImage(img);
        markerViews[idx].setFitWidth(fitW);
        markerViews[idx].setFitHeight(fitH);
    }

    /**
     * Re-syncs all images to the current {@link #enabledMarkers} state.
     */
    private void refreshAllImages() {
        for (int i = 0; i < MARKERS.length; i++) {
            Object[] def = MARKERS[i];
            String imgBase = (String) def[3];
            double fitW = ((Number) def[4]).doubleValue();
            double fitH = ((Number) def[5]).doubleValue();
            boolean enabled = enabledMarkers.contains(markerValues[i]);
            updateMarkerImage(i, imgBase, fitW, fitH, enabled);
        }
    }

    private ImageView makeImageView(String filename, double fitW, double fitH) {
        ImageView iv = new ImageView();
        iv.setFitWidth(fitW);
        iv.setFitHeight(fitH);
        iv.setPreserveRatio(false);
        Image img = loadImage(filename);
        if (img != null) iv.setImage(img);
        return iv;
    }
}