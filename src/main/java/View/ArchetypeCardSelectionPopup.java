package View;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.CardsLists.ThemeCollection;
import Model.Database.DataBaseUpdate;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Modal popup shown after an archetype is added to a {@link ThemeCollection}.
 *
 * <p>Displays all cards belonging to the archetype as clickable thumbnails.
 * All cards are selected by default (yellow-green border).  Clicking a
 * selected card deselects it; clicking an unselected card re-selects it.
 * No Ctrl/Shift modifiers are supported.</p>
 *
 * <p>On <b>OK</b>: selected cards are appended to
 * {@link ThemeCollection#getCardsList()} and unselected cards are appended to
 * {@link ThemeCollection#getExceptionsToNotAdd()}.</p>
 *
 * <p>On <b>Cancel</b>: the popup closes with no card changes (the archetype
 * name was already added to the collection before this popup was opened).</p>
 *
 * <p>The popup does <em>not</em> close on outside click — only the two
 * buttons dismiss it.</p>
 */
public class ArchetypeCardSelectionPopup extends Stage {

    // ── Theme ────────────────────────────────────────────────────────────────
    private static final String BG = "#100317";
    private static final String ACCENT = "#cdfc04";
    private static final int THUMB_W = 80;
    private static final int THUMB_H = 116;
    private static final int TILE_GAP = 8;
    private static final double BORDER_W = 1.5;
    private static final int POPUP_W = 860;
    private static final int POPUP_MAX_H = 700;

    // ── Model ────────────────────────────────────────────────────────────────
    private final ThemeCollection collection;
    private final List<Card> archetypeCards;

    /**
     * Cards currently selected (will go to cardsList on OK).
     * All cards are in this set by default.
     */
    private final Set<Card> selected = new LinkedHashSet<>();

    // ── Callback ─────────────────────────────────────────────────────────────
    private Runnable onOk;

    // ════════════════════════════════════════════════════════════════════════
    // Construction
    // ════════════════════════════════════════════════════════════════════════

    /**
     * @param archetypeName  display name shown in the title (not used for logic)
     * @param archetypeCards all cards belonging to the archetype
     * @param collection     the collection to which cards will be added on OK
     */
    public ArchetypeCardSelectionPopup(String archetypeName,
                                       List<Card> archetypeCards,
                                       ThemeCollection collection) {
        this.collection = collection;
        this.archetypeCards = archetypeCards != null ? archetypeCards : List.of();

        // All cards selected by default
        selected.addAll(this.archetypeCards);

        initStyle(StageStyle.UNDECORATED);
        setResizable(false);
        // Do NOT close on focus-loss (user must use a button)

        Scene scene = new Scene(buildRoot(archetypeName), Color.TRANSPARENT);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) close();
        });

        // Load stylesheet so accent-combo / accent-text-field classes apply
        try {
            java.net.URL cssUrl = getClass().getResource("/styles.css");
            if (cssUrl == null) {
                java.io.File f = new java.io.File("src/main/resources/styles.css");
                if (f.exists()) cssUrl = f.toURI().toURL();
            }
            if (cssUrl != null) scene.getStylesheets().add(cssUrl.toExternalForm());
        } catch (Exception ignored) {
        }

        setScene(scene);
        sizeToScene();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Public API
    // ════════════════════════════════════════════════════════════════════════

    private static Set<String> existingKonamiIds(List<CardElement> list) {
        Set<String> ids = new java.util.HashSet<>();
        if (list == null) return ids;
        for (CardElement ce : list) {
            if (ce != null && ce.getCard() != null && ce.getCard().getKonamiId() != null)
                ids.add(ce.getCard().getKonamiId());
        }
        return ids;
    }

    /**
     * Callback fired after the user clicks OK and cards have been distributed.
     */
    public void setOnOk(Runnable onOk) {
        this.onOk = onOk;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Layout
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Shows the popup centred on the window that owns {@code anchor}.
     */
    public void showCenteredOn(javafx.scene.Node anchor) {
        show();
        Platform.runLater(() -> {
            try {
                if (anchor != null && anchor.getScene() != null
                        && anchor.getScene().getWindow() != null) {
                    javafx.stage.Window w = anchor.getScene().getWindow();
                    setX(w.getX() + (w.getWidth() - getWidth()) / 2.0);
                    setY(w.getY() + (w.getHeight() - getHeight()) / 2.0);
                    return;
                }
            } catch (Exception ignored) {
            }
            javafx.stage.Screen s = javafx.stage.Screen.getPrimary();
            javafx.geometry.Rectangle2D b = s.getVisualBounds();
            setX(b.getMinX() + (b.getWidth() - getWidth()) / 2.0);
            setY(b.getMinY() + (b.getHeight() - getHeight()) / 2.0);
        });
    }

    // ── Card grid ─────────────────────────────────────────────────────────────

    private VBox buildRoot(String archetypeName) {
        VBox root = new VBox(12);
        root.setPrefWidth(POPUP_W);
        root.setPadding(new Insets(18));
        root.setStyle(
                "-fx-background-color: " + BG + ";" +
                        "-fx-border-color: " + ACCENT + ";" +
                        "-fx-border-width: 1.5;" +
                        "-fx-border-radius: 6;" +
                        "-fx-background-radius: 6;");

        // ── Title ────────────────────────────────────────────────────────────
        Text title = new Text("Add archetype: " + archetypeName);
        title.setStyle("-fx-fill: " + ACCENT + "; -fx-font-size: 15; -fx-font-weight: bold;");

        // ── "Cards to add :" label ────────────────────────────────────────────
        Text cardsLabel = new Text("Cards to add :");
        cardsLabel.setStyle("-fx-fill: white; -fx-font-size: 13;");

        // ── Card grid (wrapping) in a scroll pane ─────────────────────────────
        FlowPane grid = buildCardGrid();

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setMaxHeight(POPUP_MAX_H - 160); // leave room for title + buttons
        scroll.setStyle("-fx-background-color: " + BG + "; -fx-border-color: transparent;");
        scroll.getStyleClass().add("edge-to-edge");

        // ── Buttons ──────────────────────────────────────────────────────────
        Button cancelBtn = makeButton("Cancel", false);
        Button okBtn = makeButton("OK", true);
        cancelBtn.setOnAction(e -> close());
        okBtn.setOnAction(e -> {
            applyChanges();
            if (onOk != null) onOk.run();
            close();
        });

        HBox buttonRow = new HBox(10, cancelBtn, okBtn);
        buttonRow.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(title, makeSep(), cardsLabel, scroll, makeSep(), buttonRow);
        return root;
    }

    private FlowPane buildCardGrid() {
        FlowPane pane = new FlowPane(TILE_GAP, TILE_GAP);
        pane.setPrefWrapLength(POPUP_W - 40);
        pane.setStyle("-fx-background-color: " + BG + ";");
        pane.setPadding(new Insets(4));

        for (Card card : archetypeCards) {
            StackPane thumb = buildThumb(card);
            pane.getChildren().add(thumb);
        }
        return pane;
    }

    private StackPane buildThumb(Card card) {
        ImageView iv = new ImageView();
        iv.setFitWidth(THUMB_W);
        iv.setFitHeight(THUMB_H);
        iv.setPreserveRatio(true);

        // Load image
        try {
            if (card.getImagePath() != null) {
                String[] addr = DataBaseUpdate.getAddresses(card.getImagePath() + ".jpg");
                if (addr != null && addr.length > 0)
                    iv.setImage(new Image("file:" + addr[0], THUMB_W, THUMB_H, true, true));
            }
        } catch (Exception ignored) {
        }

        // Tooltip with card name
        if (card.getName_EN() != null) {
            Label nameLbl = new Label(card.getName_EN());
            nameLbl.setStyle("-fx-text-fill: white; -fx-font-size: 9;");
            nameLbl.setMaxWidth(THUMB_W);
            nameLbl.setWrapText(true);
        }

        StackPane pane = new StackPane(iv);
        pane.setPadding(new Insets(BORDER_W));
        pane.setCursor(javafx.scene.Cursor.HAND);
        pane.setUserData(card);
        refreshBorder(pane, card);

        pane.setOnMouseClicked(e -> {
            if (selected.contains(card)) {
                selected.remove(card);
            } else {
                selected.add(card);
            }
            refreshBorder(pane, card);
        });

        return pane;
    }

    // ════════════════════════════════════════════════════════════════════════
    // Apply on OK
    // ════════════════════════════════════════════════════════════════════════

    private void refreshBorder(StackPane pane, Card card) {
        if (selected.contains(card)) {
            pane.setStyle(
                    "-fx-border-color: " + ACCENT + ";" +
                            "-fx-border-width: " + BORDER_W + ";" +
                            "-fx-border-radius: 4;" +
                            "-fx-background-color: transparent;");
        } else {
            pane.setStyle(
                    "-fx-border-color: transparent;" +
                            "-fx-border-width: " + BORDER_W + ";" +
                            "-fx-border-radius: 4;" +
                            "-fx-background-color: transparent;");
        }
    }

    private void applyChanges() {
        if (collection == null) return;

        // Ensure both lists exist
        if (collection.getCardsList() == null)
            collection.setCardsList(new ArrayList<>());
        if (collection.getExceptionsToNotAdd() == null)
            collection.setExceptionsToNotAdd(new ArrayList<>());

        // Collect existing Konami IDs in each list to avoid duplicates
        Set<String> existingCards = existingKonamiIds(collection.getCardsList());
        Set<String> existingExceptions = existingKonamiIds(collection.getExceptionsToNotAdd());

        for (Card card : archetypeCards) {
            String kid = card.getKonamiId();

            if (selected.contains(card)) {
                // → cardsList
                if (kid == null || !existingCards.contains(kid)) {
                    collection.getCardsList().add(new CardElement(card));
                    if (kid != null) existingCards.add(kid);
                }
            } else {
                // → exceptionsToNotAdd
                if (kid == null || !existingExceptions.contains(kid)) {
                    collection.getExceptionsToNotAdd().add(new CardElement(card));
                    if (kid != null) existingExceptions.add(kid);
                }
            }
        }

        // Mark the collection dirty and request a full rebuild so the new cards appear
        Controller.UserInterfaceFunctions.markDirty(collection);
        Controller.UserInterfaceFunctions.setPendingDecksFullRebuild();
        Controller.UserInterfaceFunctions.triggerTabDirtyIndicatorUpdate();
        Controller.UserInterfaceFunctions.refreshDecksAndCollectionsView();
    }

    // ════════════════════════════════════════════════════════════════════════
    // Style helpers
    // ════════════════════════════════════════════════════════════════════════

    private javafx.scene.control.Separator makeSep() {
        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        sep.setStyle("-fx-background-color: " + ACCENT + "; -fx-opacity: 0.35;");
        return sep;
    }

    private Button makeButton(String text, boolean isPrimary) {
        Button b = new Button(text);
        String base =
                "-fx-background-color: " + (isPrimary ? "#2a0560" : "#1e0530") + ";" +
                        "-fx-text-fill: " + ACCENT + ";" +
                        "-fx-font-size: 12px; -fx-font-weight: bold;" +
                        "-fx-border-color: " + ACCENT + "; -fx-border-width: 1;" +
                        "-fx-border-radius: 3; -fx-background-radius: 3;" +
                        "-fx-cursor: hand; -fx-padding: 5 18 5 18;";
        b.setStyle(base);
        b.setOnMouseEntered(e -> b.setStyle(base.replace(
                isPrimary ? "#2a0560" : "#1e0530", "#3d0880")));
        b.setOnMouseExited(e -> b.setStyle(base));
        if (isPrimary) b.setDefaultButton(true);
        return b;
    }
}