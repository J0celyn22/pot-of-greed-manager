package Controller;

import View.SharedCollectionTab;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Owns the three save buttons, the "*" dirty-indicator on each tab header, and
 * the unsaved-changes prompt shown when the window is closed.
 *
 * <p>Extracted from {@link RealMainController}, which still owns the tab
 * objects, the tab-header handles, and the two sub-controllers whose menus
 * need repopulating after a save. Those are supplied here as constructor
 * dependencies rather than duplicated.
 */
public class SaveStateCoordinator {

    private static final Logger logger = LoggerFactory.getLogger(SaveStateCoordinator.class);

    private final SharedCollectionTab myCollectionTab;
    private final SharedCollectionTab decksTab;
    private final SharedCollectionTab ouicheListTab;
    private final Tab myCollectionTabHandle;
    private final Tab decksTabHandle;
    private final Tab ouicheListTabHandle;
    private final MyCollectionController myCollectionController;
    private final DecksCollectionsController decksController;

    public SaveStateCoordinator(SharedCollectionTab myCollectionTab,
                                SharedCollectionTab decksTab,
                                SharedCollectionTab ouicheListTab,
                                Tab myCollectionTabHandle,
                                Tab decksTabHandle,
                                Tab ouicheListTabHandle,
                                MyCollectionController myCollectionController,
                                DecksCollectionsController decksController) {
        this.myCollectionTab = myCollectionTab;
        this.decksTab = decksTab;
        this.ouicheListTab = ouicheListTab;
        this.myCollectionTabHandle = myCollectionTabHandle;
        this.decksTabHandle = decksTabHandle;
        this.ouicheListTabHandle = ouicheListTabHandle;
        this.myCollectionController = myCollectionController;
        this.decksController = decksController;
    }

    // =========================================================================
    // Tab dirty indicators
    // =========================================================================

    /**
     * Updates the text of the three saveable tab headers to show or remove the
     * {@code "*"} dirty marker based on the current dirty state in
     * {@link UserInterfaceFunctions}.
     */
    public void updateTabDirtyIndicators() {
        if (myCollectionTabHandle != null) {
            boolean dirty = UserInterfaceFunctions.isMyCollectionDirty();
            String base = "My Collection";
            myCollectionTabHandle.setText(dirty ? "* " + base : base);
        }
        if (decksTabHandle != null) {
            boolean dirty = UserInterfaceFunctions.isAnyDeckOrCollectionDirty();
            String base = "Decks and Collections";
            decksTabHandle.setText(dirty ? "* " + base : base);
        }
        if (ouicheListTabHandle != null) {
            boolean dirty = UserInterfaceFunctions.isOuicheListDirty();
            String base = "OuicheList";
            ouicheListTabHandle.setText(dirty ? "* " + base : base);
        }
    }

    // =========================================================================
    // Save buttons
    // =========================================================================

    public void wireSaveButtons() {
        if (myCollectionTab.getSaveButton() != null) {
            myCollectionTab.getSaveButton().setOnAction(event -> {
                try {
                    UserInterfaceFunctions.saveMyCollection();
                    updateTabDirtyIndicators();
                    myCollectionController.populateMyCollectionMenu();
                } catch (Exception exception) {
                    logger.error("Error saving My Collection", exception);
                }
            });
        }

        if (decksTab.getSaveButton() != null) {
            decksTab.getSaveButton().setOnAction(event -> {
                try {
                    UserInterfaceFunctions.saveAllDecksAndCollections();
                    updateTabDirtyIndicators();
                    decksController.populateDecksAndCollectionsMenu();
                } catch (Exception exception) {
                    logger.error("Error saving Decks and Collections", exception);
                }
            });
        }

        if (ouicheListTab.getSaveButton() != null) {
            ouicheListTab.getSaveButton().setOnAction(event -> {
                try {
                    UserInterfaceFunctions.saveOuicheList();
                    updateTabDirtyIndicators();
                } catch (Exception exception) {
                    logger.error("Error saving OuicheList", exception);
                }
            });
        }
    }

    // =========================================================================
    // Window close request
    // =========================================================================

    public void handleWindowCloseRequest(WindowEvent event) {
        List<String> dirtyTabs = new ArrayList<>();
        if (myCollectionTabHandle != null
                && myCollectionTabHandle.getText().startsWith("*")) {
            dirtyTabs.add("My Collection");
        }
        if (decksTabHandle != null && decksTabHandle.getText().startsWith("*")) {
            dirtyTabs.add("Decks & Collections");
        }
        if (ouicheListTabHandle != null && ouicheListTabHandle.getText().startsWith("*")) {
            dirtyTabs.add("OuicheList");
        }

        if (dirtyTabs.isEmpty()) {
            return;
        }

        event.consume();

        Alert alert = new Alert(Alert.AlertType.NONE);
        alert.setTitle("Unsaved Changes");
        alert.setHeaderText("The following tabs have unsaved changes:");
        alert.setContentText(String.join("\n", dirtyTabs));

        ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType noSaveButton = new ButtonType("Don't Save", ButtonBar.ButtonData.NO);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(saveButton, noSaveButton, cancelButton);

        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle(
                "-fx-background-color: #100317; "
                        + "-fx-border-color: #5a2a7a; "
                        + "-fx-border-width: 1;");
        dialogPane.applyCss();
        dialogPane.layout();
        dialogPane.lookupAll(".header-panel")
                .forEach(node -> node.setStyle("-fx-background-color: #100317;"));
        dialogPane.lookupAll(".label")
                .forEach(node -> node.setStyle("-fx-text-fill: white;"));
        dialogPane.lookupAll(".button")
                .forEach(node -> node.setStyle(
                        "-fx-background-color: #2a0a3e; "
                                + "-fx-text-fill: white; "
                                + "-fx-border-color: #7a3aaa; "
                                + "-fx-border-width: 1; "
                                + "-fx-border-radius: 3; "
                                + "-fx-background-radius: 3; "
                                + "-fx-cursor: hand;"));

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isEmpty() || result.get() == cancelButton) {
            return;
        }

        if (result.get() == saveButton) {
            try {
                if (myCollectionTabHandle != null
                        && myCollectionTabHandle.getText().startsWith("*")) {
                    UserInterfaceFunctions.saveMyCollection();
                }
                if (decksTabHandle != null && decksTabHandle.getText().startsWith("*")) {
                    UserInterfaceFunctions.saveAllDecksAndCollections();
                }
                if (ouicheListTabHandle != null && ouicheListTabHandle.getText().startsWith("*")) {
                    UserInterfaceFunctions.saveOuicheList();
                }
                updateTabDirtyIndicators();
            } catch (Exception exception) {
                logger.error("Save-on-close failed", exception);
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("Save Error");
                errorAlert.setHeaderText("Could not save all changes.");
                errorAlert.setContentText(exception.getMessage());
                errorAlert.getDialogPane().setStyle("-fx-background-color: #100317;");
                errorAlert.getDialogPane().applyCss();
                errorAlert.getDialogPane().layout();
                errorAlert.getDialogPane().lookupAll(".label")
                        .forEach(node -> node.setStyle("-fx-text-fill: white;"));
                errorAlert.showAndWait();
                return;
            }
        }

        Platform.exit();
    }
}