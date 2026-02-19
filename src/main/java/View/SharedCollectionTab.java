package View;

import Controller.UserInterfaceFunctions;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/*
 * SharedCollectionTab.java
 *
 * Two-column layout: left navigation menu inside a ScrollPane, middle content.
 * Programmatic scrollbar styling for the navigation menu remains.
 */
public class SharedCollectionTab extends HBox {

    private static final Logger logger = LoggerFactory.getLogger(SharedCollectionTab.class);

    public SharedCollectionTab(TabType tabType) {
        this.tabType = tabType;
        this.setSpacing(0);

        // Left pane: Navigation menu (fixed width)
        menuVBox = new VBox();
        menuVBox.getStyleClass().add("navigation-menu");
        menuVBox.setSpacing(5);
        menuVBox.setPadding(new Insets(10));

        menuScrollPane = new ScrollPane(menuVBox);
        menuScrollPane.getStyleClass().add("navigation-scroll-pane");

        // Make the ScrollPane background transparent so the menu VBox background shows through
        menuScrollPane.setStyle(
                "-fx-background-color: transparent; " +
                        "-fx-background: transparent; " +
                        "-fx-background-insets: 0; " +
                        "-fx-padding: 0;"
        );

        menuScrollPane.setFitToWidth(true);
        menuScrollPane.setFitToHeight(true);
        menuScrollPane.setPrefWidth(375);
        menuScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        menuScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        // Vertical separator between left and middle.
        Separator sepLeft = new Separator();
        sepLeft.setOrientation(Orientation.VERTICAL);
        sepLeft.setStyle("-fx-background-color: white;");
        sepLeft.setPrefWidth(2);

        // Middle pane: Display area (header and content).
        displayVBox = new VBox();
        displayVBox.setSpacing(0);
        displayVBox.getStyleClass().add("display-vbox");

        // Header pane (top middle part)
        headerPane = new AnchorPane();
        headerPane.setPrefHeight(200);
        headerPane.getStyleClass().add("header-pane");
        Node headerContent = createHeaderContentForTab(tabType);
        setHeaderContent(headerContent);

        // Horizontal separator between header and content.
        Separator sepHoriz = new Separator();
        sepHoriz.setStyle("-fx-background-color: white;");
        sepHoriz.setPrefHeight(2);

        // Content pane (collection/decks view)
        contentPane = new AnchorPane();
        contentPane.getStyleClass().add("content-pane");
        VBox.setVgrow(contentPane, Priority.ALWAYS);

        displayVBox.getChildren().addAll(headerPane, sepHoriz, contentPane);
        HBox.setHgrow(displayVBox, Priority.ALWAYS);

        // Assemble the two columns only (left and middle)
        this.getChildren().addAll(menuScrollPane, sepLeft, displayVBox);

        // Programmatic styling for the navigation menu scrollbars remains (inline styles)
        styleScrollBarsIn(menuScrollPane);
    }

    @FXML
    private ScrollPane menuScrollPane;
    @FXML
    private VBox menuVBox;
    @FXML
    private VBox displayVBox;
    @FXML
    private AnchorPane headerPane;
    @FXML
    private AnchorPane contentPane;

    private Runnable onDecksLoad;
    private TabType tabType;

    // OuicheList view-mode toggle buttons (accessible by the controller)
    private Button compactDetailedButton;
    private Button mosaicListButton;

    /**
     * Returns the Compact/Detailed toggle button for the OuicheList tab.
     * Returns null if this tab is not OUICHE_LIST.
     */
    public Button getCompactDetailedButton() {
        return compactDetailedButton;
    }

    /**
     * Returns the Mosaic/List toggle button for the OuicheList tab.
     * Returns null if this tab is not OUICHE_LIST.
     */
    public Button getMosaicListButton() {
        return mosaicListButton;
    }

    public void setOnDecksLoad(Runnable onDecksLoad) {
        this.onDecksLoad = onDecksLoad;
    }

    private Node createHeaderContentForTab(TabType type) {
        HBox headerContent = new HBox(20);
        headerContent.setPadding(new Insets(10));
        headerContent.setAlignment(Pos.CENTER_LEFT);

        switch (type) {
            case MY_COLLECTION: {
                HBox groupRow = new HBox(5);
                TextField collectionFileField = new TextField();
                collectionFileField.setPromptText("Enter collection file path");
                collectionFileField.setPrefColumnCount(30);
                collectionFileField.setText(UserInterfaceFunctions.filePath != null ?
                        UserInterfaceFunctions.filePath.getAbsolutePath() : "");
                collectionFileField.setVisible(false);
                collectionFileField.setManaged(false);
                collectionFileField.getStyleClass().add("accent-text-field");

                Button collectionFileButton = new Button("Browse");
                Button collectionFileLoadButton = new Button("Load");
                Button collectionFileGenerateHTMLButton = new Button("Generate HTML");

                // small buttons: keep compact size
                collectionFileButton.getStyleClass().add("small-button");
                collectionFileLoadButton.getStyleClass().add("small-button");
                // make Generate HTML small as requested
                collectionFileGenerateHTMLButton.getStyleClass().add("small-button");

                groupRow.getChildren().addAll(collectionFileButton, collectionFileLoadButton, collectionFileGenerateHTMLButton);

                collectionFileButton.setOnAction(e -> {
                    Stage stage = (Stage) collectionFileButton.getScene().getWindow();
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Select Collection File");
                    fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
                    UserInterfaceFunctions.browseCollectionFile(fileChooser, stage, collectionFileField);
                });
                collectionFileLoadButton.setOnAction(e -> {
                    try {
                        UserInterfaceFunctions.loadCollectionFile();
                        logger.info("Collection file loaded.");
                    } catch (Exception ex) {
                        logger.error("Error loading collection file", ex);
                    }
                });
                collectionFileGenerateHTMLButton.setOnAction(e -> {
                    try {
                        UserInterfaceFunctions.exportCollectionFile();
                    } catch (Exception ex) {
                        logger.error("Error generating collection HTML", ex);
                    }
                });
                headerContent.getChildren().add(groupRow);
                break;
            }
            case DECKS: {
                HBox groupRow = new HBox(5);
                TextField decksAndCollectionDirectoryField = new TextField();
                decksAndCollectionDirectoryField.setPromptText("Enter decks/collections directory");
                decksAndCollectionDirectoryField.setPrefColumnCount(30);
                decksAndCollectionDirectoryField.setVisible(false);
                decksAndCollectionDirectoryField.setManaged(false);
                decksAndCollectionDirectoryField.getStyleClass().add("accent-text-field");

                Button decksAndCollectionsDirectoryButton = new Button("Browse");
                Button decksAndCollectionsDirectoryLoadButton = new Button("Load");
                Button decksAndCollectionsDirectoryGenerateHTMLButton = new Button("Generate HTML");

                decksAndCollectionsDirectoryButton.getStyleClass().add("small-button");
                decksAndCollectionsDirectoryLoadButton.getStyleClass().add("small-button");
                decksAndCollectionsDirectoryGenerateHTMLButton.getStyleClass().add("small-button");

                groupRow.getChildren().addAll(decksAndCollectionsDirectoryButton, decksAndCollectionsDirectoryLoadButton, decksAndCollectionsDirectoryGenerateHTMLButton);

                decksAndCollectionsDirectoryButton.setOnAction(e -> {
                    Stage stage = (Stage) decksAndCollectionsDirectoryButton.getScene().getWindow();
                    DirectoryChooser folderChooser = new DirectoryChooser();
                    folderChooser.setTitle("Select Decks/Collections Folder");
                    UserInterfaceFunctions.browseDecksAndCollectionsDirectory(folderChooser, stage, decksAndCollectionDirectoryField);
                });
                decksAndCollectionsDirectoryLoadButton.setOnAction(e -> {
                    try {
                        UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
                        if (onDecksLoad != null) {
                            onDecksLoad.run();
                        }
                    } catch (Exception ex) {
                        logger.error("Error loading decks and collections directory", ex);
                    }
                });
                decksAndCollectionsDirectoryGenerateHTMLButton.setOnAction(e -> {
                    try {
                        UserInterfaceFunctions.exportDecksAndCollectionsDirectory();
                    } catch (Exception ex) {
                        logger.error("Error generating decks/collections HTML", ex);
                    }
                });
                headerContent.getChildren().add(groupRow);
                break;
            }
            case OUICHE_LIST: {
                VBox ouicheGroup = new VBox(10);
                ouicheGroup.setAlignment(Pos.CENTER_LEFT);

                HBox groupRow3 = new HBox(5);
                Button generateOuicheListButton = new Button("Generate OuicheList – Decks and Collections");
                Button generateOuicheListSaveButton = new Button("Save");
                groupRow3.getChildren().addAll(generateOuicheListButton, generateOuicheListSaveButton);
                ouicheGroup.getChildren().add(groupRow3);

                HBox groupRow4 = new HBox(5);
                Button generateOuicheListTypeButton = new Button("Generate OuicheList – Type of cards");
                groupRow4.getChildren().add(generateOuicheListTypeButton);
                ouicheGroup.getChildren().add(groupRow4);

                HBox groupRow5 = new HBox(5);
                Button generateAllButton = new Button("Generate All Lists");
                groupRow5.getChildren().add(generateAllButton);
                ouicheGroup.getChildren().add(groupRow5);

                generateOuicheListButton.getStyleClass().add("large-button");
                generateOuicheListTypeButton.getStyleClass().add("large-button");
                generateAllButton.getStyleClass().add("large-button");
                generateOuicheListSaveButton.getStyleClass().add("small-button");

                generateOuicheListButton.setOnAction(e -> UserInterfaceFunctions.generateOuicheList());
                generateOuicheListSaveButton.setOnAction(e -> {
                    try {
                        UserInterfaceFunctions.saveOuicheList();
                    } catch (Exception ex) {
                        logger.error("Error saving OuicheList", ex);
                    }
                });
                generateOuicheListTypeButton.setOnAction(e -> UserInterfaceFunctions.generateOuicheListType());
                generateAllButton.setOnAction(e -> {
                    try {
                        UserInterfaceFunctions.loadCollectionFile();
                        UserInterfaceFunctions.exportCollectionFile();
                        UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
                        UserInterfaceFunctions.exportDecksAndCollectionsDirectory();
                        UserInterfaceFunctions.generateOuicheList();
                        UserInterfaceFunctions.generateOuicheListType();
                    } catch (Exception ex) {
                        logger.error("Error during generate-all operation", ex);
                    }
                });

                // --- View-mode toggle row (bottom of the header) ---
                HBox groupRow6 = new HBox(5);
                groupRow6.setAlignment(Pos.CENTER_LEFT);

                compactDetailedButton = new Button("Compact OuicheList");
                compactDetailedButton.getStyleClass().add("small-button");

                mosaicListButton = new Button("Mosaic");
                mosaicListButton.getStyleClass().add("small-button");
                // Only visible when Detailed mode is active
                mosaicListButton.setVisible(false);
                mosaicListButton.setManaged(false);

                compactDetailedButton.setOnAction(e -> {
                    if ("Compact OuicheList".equals(compactDetailedButton.getText())) {
                        compactDetailedButton.setText("Detailed OuicheList");
                        mosaicListButton.setVisible(true);
                        mosaicListButton.setManaged(true);
                    } else {
                        compactDetailedButton.setText("Compact OuicheList");
                        mosaicListButton.setVisible(false);
                        mosaicListButton.setManaged(false);
                    }
                });

                mosaicListButton.setOnAction(e -> {
                    if ("Mosaic".equals(mosaicListButton.getText())) {
                        mosaicListButton.setText("List");
                    } else {
                        mosaicListButton.setText("Mosaic");
                    }
                });

                groupRow6.getChildren().addAll(compactDetailedButton, mosaicListButton);
                ouicheGroup.getChildren().add(groupRow6);

                headerContent.getChildren().add(ouicheGroup);
                break;
            }
            case FRIENDS: {
                VBox friendsGroup = new VBox(10);
                friendsGroup.setAlignment(Pos.CENTER_LEFT);
                Text thirdPartyAvailableCardsText = new Text("3rd Party Available Cards");
                thirdPartyAvailableCardsText.setStyle("-fx-font-size: 14px; -fx-fill: white;");
                friendsGroup.getChildren().add(thirdPartyAvailableCardsText);

                HBox friendsRow1 = new HBox(5);
                TextField thirdPartyAvailableCardsField = new TextField();
                thirdPartyAvailableCardsField.setPromptText("Enter 3rd party cards file");
                thirdPartyAvailableCardsField.setPrefColumnCount(30);

                thirdPartyAvailableCardsField.getStyleClass().addAll("accent-text-field", "fixed-accent-text-field");

                Button thirdPartyAvailableCardsBrowseButton = new Button("Browse");
                Button thirdPartyAvailableCardsLoadButton = new Button("Load");

                thirdPartyAvailableCardsBrowseButton.getStyleClass().add("small-button");
                thirdPartyAvailableCardsLoadButton.getStyleClass().add("small-button");

                friendsRow1.getChildren().addAll(thirdPartyAvailableCardsField, thirdPartyAvailableCardsBrowseButton, thirdPartyAvailableCardsLoadButton);
                friendsGroup.getChildren().add(friendsRow1);

                Text ouicheListText = new Text("OuicheList");
                ouicheListText.setStyle("-fx-font-size: 14px; -fx-fill: white;");
                friendsGroup.getChildren().add(ouicheListText);

                HBox friendsRow2 = new HBox(5);
                TextField ouicheListField = new TextField();
                ouicheListField.setPromptText("Enter OuicheList file");
                ouicheListField.setPrefColumnCount(30);
                // fixed-size accent text field so hover/focus won't change layout
                ouicheListField.getStyleClass().addAll("accent-text-field", "fixed-accent-text-field");

                Button ouicheListBrowseButton = new Button("Browse");
                Button loadOuicheListButton = new Button("Load");
                // small buttons
                ouicheListBrowseButton.getStyleClass().add("small-button");
                loadOuicheListButton.getStyleClass().add("small-button");

                friendsRow2.getChildren().addAll(ouicheListField, ouicheListBrowseButton, loadOuicheListButton);
                friendsGroup.getChildren().add(friendsRow2);

                HBox friendsRow3 = new HBox(5);
                Button generateThirdPartyListButton = new Button("Generate list");
                Button generateThirdPartyListSaveButton = new Button("Save");
                // small buttons for these
                generateThirdPartyListButton.getStyleClass().add("small-button");
                generateThirdPartyListSaveButton.getStyleClass().add("small-button");

                friendsRow3.getChildren().addAll(generateThirdPartyListButton, generateThirdPartyListSaveButton);
                friendsGroup.getChildren().add(friendsRow3);

                thirdPartyAvailableCardsBrowseButton.setOnAction(e -> {
                    Stage stage = (Stage) thirdPartyAvailableCardsBrowseButton.getScene().getWindow();
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Select 3rd Party Cards File");
                    fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
                    UserInterfaceFunctions.browseThirdPartyAvailableCards(fileChooser, stage, thirdPartyAvailableCardsField);
                });
                thirdPartyAvailableCardsLoadButton.setOnAction(e -> {
                    try {
                        UserInterfaceFunctions.loadThirdPartyAvailableCards();
                    } catch (Exception ex) {
                        logger.error("Error loading 3rd party available cards", ex);
                    }
                });
                ouicheListBrowseButton.setOnAction(e -> {
                    Stage stage = (Stage) ouicheListBrowseButton.getScene().getWindow();
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Select OuicheList File");
                    fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
                    UserInterfaceFunctions.browseOuicheList(fileChooser, stage, ouicheListField);
                });
                loadOuicheListButton.setOnAction(e -> {
                    try {
                        UserInterfaceFunctions.loadOuicheList();
                    } catch (Exception ex) {
                        logger.error("Error loading OuicheList", ex);
                    }
                });
                generateThirdPartyListButton.setOnAction(e -> {
                    try {
                        UserInterfaceFunctions.generateThirdPartyList();
                    } catch (Exception ex) {
                        logger.error("Error generating 3rd party list", ex);
                    }
                });
                generateThirdPartyListSaveButton.setOnAction(e -> {
                    try {
                        UserInterfaceFunctions.saveThirdPartyList();
                    } catch (Exception ex) {
                        logger.error("Error saving 3rd party list", ex);
                    }
                });
                headerContent.getChildren().add(friendsGroup);
                break;
            }
            case ARCHETYPES: {
                VBox archetypesGroup = new VBox(10);
                archetypesGroup.setAlignment(Pos.CENTER);
                Button generateArchetypesListsButton = new Button("Generate Archetype Lists");
                // Make the archetypes button larger as requested
                generateArchetypesListsButton.getStyleClass().add("large-button");
                archetypesGroup.getChildren().add(generateArchetypesListsButton);
                generateArchetypesListsButton.setOnAction(e -> {
                    try {
                        UserInterfaceFunctions.generateArchetypesListsFunction();
                    } catch (Exception ex) {
                        logger.error("Error generating archetype lists", ex);
                    }
                });
                headerContent.getChildren().add(archetypesGroup);
                break;
            }
            case SHOPS: {
                VBox shopsGroup = new VBox(10);
                shopsGroup.setAlignment(Pos.CENTER);

                Button ultraJeuxButton = new Button("UltraJeux");
                // make UltraJeux small as requested
                ultraJeuxButton.getStyleClass().add("small-button");
                shopsGroup.getChildren().add(ultraJeuxButton);

                ultraJeuxButton.setOnAction(e -> {
                    try {
                        if (!UserInterfaceFunctions.getOuicheListIsLoaded()) {
                            if (UserInterfaceFunctions.ouicheListPath == null) {
                                System.out.println("OuicheList must be selected");
                            } else {
                                UserInterfaceFunctions.loadOuicheList();
                            }
                        }
                        if (UserInterfaceFunctions.getOuicheListIsLoaded()) {
                            java.util.List<Model.CardsLists.CardElement> maOuicheList =
                                    Model.CardsLists.OuicheList.getMaOuicheList();
                            double maxPrice = 100;
                            java.util.Map<String, java.util.List<String>> cardNamesFromWebsite =
                                    Model.UltraJeux.CardScraper.getCardNamesFromWebsite(maOuicheList, maxPrice);

                            for (java.util.Map.Entry<String, java.util.List<String>> entry : cardNamesFromWebsite.entrySet()) {
                                System.out.println("Page: " + entry.getKey());
                                for (String cardName : entry.getValue()) {
                                    System.out.println("Card Name: " + cardName + ", Page: " + entry.getKey());
                                }
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });

                headerContent.getChildren().add(shopsGroup);
                break;
            }
        }
        return headerContent;
    }

    private void styleScrollBarsIn(ScrollPane sp) {
        if (sp == null) return;

        // Re-apply when skin changes (scrollbars are created by the skin)
        sp.skinProperty().addListener((obs, oldSkin, newSkin) -> Platform.runLater(() -> applyStylesToScrollBars(sp)));

        // Try immediately (in case skin already exists)
        Platform.runLater(() -> applyStylesToScrollBars(sp));
    }

    public VBox getMenuVBox() {
        return menuVBox;
    }

    public AnchorPane getContentPane() {
        return contentPane;
    }

    public AnchorPane getHeaderPane() {
        return headerPane;
    }

    public void setHeaderContent(Node node) {
        headerPane.getChildren().clear();
        headerPane.getChildren().add(node);
    }

    // -------------------------
    // Programmatic scrollbar styling helper (keeps inline styling for nav scrollpane)
    // -------------------------

    private void applyStylesToScrollBars(ScrollPane sp) {
        try {
            Set<Node> bars = sp.lookupAll(".scroll-bar");
            bars.addAll(sp.lookupAll(".overlay-scroll-bar"));

            for (Node bar : bars) {
                bar.setStyle(
                        "-fx-background-color: transparent; " +
                                "-fx-background-image: null; " +
                                "-fx-padding: 0;"
                );

                Node track = bar.lookup(".track");
                if (track != null) {
                    track.setStyle(
                            "-fx-background-color: #100317; " +
                                    "-fx-background-image: null; " +
                                    "-fx-background-insets: 0; " +
                                    "-fx-background-radius: 4;"
                    );
                }

                Node thumb = bar.lookup(".thumb");
                if (thumb != null) {
                    thumb.setStyle(
                            "-fx-background-color: #cdfc04; " +
                                    "-fx-background: #cdfc04; " +
                                    "-fx-background-image: null; " +
                                    "-fx-background-insets: 2; " +
                                    "-fx-background-radius: 6; " +
                                    "-fx-pref-width: 10; " +
                                    "-fx-pref-height: 24; " +
                                    "-fx-opacity: 1; " +
                                    "-fx-effect: null;"
                    );
                }

                Node inc = bar.lookup(".increment-button");
                Node dec = bar.lookup(".decrement-button");
                if (inc != null) {
                    inc.setStyle("-fx-background-color: #100317; -fx-background-image: null; -fx-padding: 2; -fx-background-radius: 4;");
                    Node incArrow = inc.lookup(".increment-arrow");
                    if (incArrow == null) incArrow = inc.lookup(".arrow");
                    if (incArrow != null) incArrow.setStyle("-fx-background-color: #cdfc04;");
                }
                if (dec != null) {
                    dec.setStyle("-fx-background-color: #100317; -fx-background-image: null; -fx-padding: 2; -fx-background-radius: 4;");
                    Node decArrow = dec.lookup(".decrement-arrow");
                    if (decArrow == null) decArrow = dec.lookup(".arrow");
                    if (decArrow != null) decArrow.setStyle("-fx-background-color: #cdfc04;");
                }
            }
        } catch (Exception e) {
            logger.debug("applyStylesToScrollBars failed", e);
        }
    }

    public enum TabType {
        MY_COLLECTION,
        DECKS,
        OUICHE_LIST,
        FRIENDS,
        ARCHETYPES,
        SHOPS;
    }
}
