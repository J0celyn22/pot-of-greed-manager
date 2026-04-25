package View;

import Controller.UserInterfaceFunctions;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
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

import java.util.List;
import java.util.Set;

/*
 * SharedCollectionTab.java
 *
 * Two-column layout: left navigation menu inside a ScrollPane, middle content.
 * Programmatic scrollbar styling for the navigation menu remains.
 *
 * Change (Shops tab):
 *  - ultraJeuxButton handler now uses the new List<ShopResultEntry> return type from
 *    CardScraper.getCardNamesFromWebsite().
 *  - After scraping, results are rendered in the tab's contentPane as a ListView
 *    using ShopResultListCell (image + name + price + wanted count), mirroring the
 *    OuicheList compact-list style.
 *  - Same-name cards are automatically grouped together because CardScraper now
 *    applies name-grouping before returning.
 */
public class SharedCollectionTab extends HBox {

    private static final Logger logger = LoggerFactory.getLogger(SharedCollectionTab.class);

    public ScrollPane getMenuScrollPane() {
        return menuScrollPane;
    }

    @FXML
    private AnchorPane rightHeaderPane;

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
    @FXML
    private AnchorPane rightContentPane;
    private FilterPane filterPane;

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

        // ── Tab-specific header (~1/3 width) ──────────────────────────────────
        headerPane = new AnchorPane();
        headerPane.getStyleClass().add("header-pane");
        headerPane.setStyle("-fx-background-color: #100317;");
        Node headerContent = createHeaderContentForTab(tabType);
        setHeaderContent(headerContent);

        // ── Right header: FilterPane owned here, wired by the controller (~2/3) ──
        rightHeaderPane = new AnchorPane();
        rightHeaderPane.setStyle("-fx-background-color: #100317;");
        HBox.setHgrow(rightHeaderPane, Priority.ALWAYS);

        filterPane = new FilterPane();
        AnchorPane.setTopAnchor(filterPane, 0.0);
        AnchorPane.setBottomAnchor(filterPane, 0.0);
        AnchorPane.setLeftAnchor(filterPane, 0.0);
        AnchorPane.setRightAnchor(filterPane, 0.0);
        rightHeaderPane.getChildren().add(filterPane);

        Separator headerVertSep = new Separator();
        headerVertSep.setOrientation(Orientation.VERTICAL);
        headerVertSep.setStyle("-fx-background-color: white;");
        headerVertSep.setPrefWidth(2);

        HBox headerRow = new HBox(0, headerPane, headerVertSep, rightHeaderPane);
        headerRow.setPrefHeight(200);
        headerRow.getStyleClass().add("header-pane");
        headerPane.prefWidthProperty().bind(headerRow.widthProperty().divide(3).subtract(1));
        headerPane.maxWidthProperty().bind(headerPane.prefWidthProperty());
        headerPane.minWidthProperty().bind(headerPane.prefWidthProperty());

        // ── Horizontal separator between header row and content row ───────────
        Separator sepHoriz = new Separator();
        sepHoriz.setStyle("-fx-background-color: white;");
        sepHoriz.setPrefHeight(2);

        // ── Middle content pane (tree view, grows to fill) ────────────────────
        contentPane = new AnchorPane();
        contentPane.getStyleClass().add("content-pane");
        HBox.setHgrow(contentPane, Priority.ALWAYS);

        // ── Right content placeholder – cards display injected by controller ──
        rightContentPane = new AnchorPane();
        rightContentPane.getStyleClass().add("right-content-pane");
        rightContentPane.setStyle("-fx-background-color: #100317;");
        rightContentPane.setPrefWidth(375);

        Separator contentVertSep = new Separator();
        contentVertSep.setOrientation(Orientation.VERTICAL);
        contentVertSep.setStyle("-fx-background-color: white;");
        contentVertSep.setPrefWidth(2);

        HBox contentRow = new HBox(0, contentPane, contentVertSep, rightContentPane);
        VBox.setVgrow(contentRow, Priority.ALWAYS);

        // ── Assemble displayVBox ───────────────────────────────────────────────
        displayVBox = new VBox();
        displayVBox.setSpacing(0);
        displayVBox.getStyleClass().add("display-vbox");
        displayVBox.getChildren().addAll(headerRow, sepHoriz, contentRow);
        HBox.setHgrow(displayVBox, Priority.ALWAYS);

        // ── Final assembly: nav | middle+right block ──────────────────────────
        this.getChildren().addAll(menuScrollPane, sepLeft, displayVBox);

        // Programmatic styling for the navigation menu scrollbars remains
        styleScrollBarsIn(menuScrollPane);
    }

    private Runnable onDecksLoad;
    private TabType tabType;

    // OuicheList view-mode toggle buttons (accessible by the controller)
    private Button compactDetailedButton;
    private Button mosaicListButton;
    private Button saveButton;

    public Button getCompactDetailedButton() {
        return compactDetailedButton;
    }

    public Button getMosaicListButton() {
        return mosaicListButton;
    }

    public void setOnDecksLoad(Runnable onDecksLoad) {
        this.onDecksLoad = onDecksLoad;
    }

    public Button getSaveButton() {
        return saveButton;
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

                saveButton = new Button("Save");
                saveButton.getStyleClass().add("small-button");
                groupRow.getChildren().add(saveButton);

                collectionFileButton.getStyleClass().add("small-button");
                collectionFileLoadButton.getStyleClass().add("small-button");
                collectionFileGenerateHTMLButton.getStyleClass().add("small-button");

                groupRow.getChildren().addAll(collectionFileButton, collectionFileLoadButton,
                        collectionFileGenerateHTMLButton);

                collectionFileButton.setOnAction(e -> {
                    Stage stage = (Stage) collectionFileButton.getScene().getWindow();
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Select Collection File");
                    fileChooser.getExtensionFilters().addAll(
                            new FileChooser.ExtensionFilter("Text Files", "*.txt"));
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

                saveButton = new Button("Save");
                saveButton.getStyleClass().add("small-button");
                groupRow.getChildren().add(saveButton);

                groupRow.getChildren().addAll(decksAndCollectionsDirectoryButton,
                        decksAndCollectionsDirectoryLoadButton,
                        decksAndCollectionsDirectoryGenerateHTMLButton);

                decksAndCollectionsDirectoryButton.setOnAction(e -> {
                    Stage stage = (Stage) decksAndCollectionsDirectoryButton.getScene().getWindow();
                    DirectoryChooser folderChooser = new DirectoryChooser();
                    folderChooser.setTitle("Select Decks/Collections Folder");
                    UserInterfaceFunctions.browseDecksAndCollectionsDirectory(
                            folderChooser, stage, decksAndCollectionDirectoryField);
                });
                decksAndCollectionsDirectoryLoadButton.setOnAction(e -> {
                    try {
                        UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
                        if (onDecksLoad != null) onDecksLoad.run();
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
                Button generateOuicheListButton =
                        new Button("Generate OuicheList – Decks and Collections");
                saveButton = new Button("Save");
                saveButton.getStyleClass().add("small-button");
                Button generateOuicheListSaveButton = saveButton;
                groupRow3.getChildren().addAll(generateOuicheListButton, generateOuicheListSaveButton);
                ouicheGroup.getChildren().add(groupRow3);

                HBox groupRow4 = new HBox(5);
                Button generateOuicheListTypeButton =
                        new Button("Generate OuicheList – Type of cards");
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

                generateOuicheListButton.setOnAction(
                        e -> UserInterfaceFunctions.generateOuicheList());
                generateOuicheListSaveButton.setOnAction(e -> {
                    try {
                        UserInterfaceFunctions.saveOuicheList();
                    } catch (Exception ex) {
                        logger.error("Error saving OuicheList", ex);
                    }
                });
                generateOuicheListTypeButton.setOnAction(
                        e -> UserInterfaceFunctions.generateOuicheListType());
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

                HBox groupRow6 = new HBox(5);
                groupRow6.setAlignment(Pos.CENTER_LEFT);

                compactDetailedButton = new Button("Compact mode");
                compactDetailedButton.getStyleClass().add("small-button");

                mosaicListButton = new Button("Mosaic");
                mosaicListButton.getStyleClass().add("small-button");
                mosaicListButton.setVisible(false);
                mosaicListButton.setManaged(false);

                compactDetailedButton.setOnAction(e -> {
                    if ("Compact mode".equals(compactDetailedButton.getText())) {
                        compactDetailedButton.setText("Detailed mode");
                        mosaicListButton.setVisible(true);
                        mosaicListButton.setManaged(true);
                    } else {
                        compactDetailedButton.setText("Compact mode");
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
                thirdPartyAvailableCardsField.getStyleClass().addAll(
                        "accent-text-field", "fixed-accent-text-field");

                Button thirdPartyAvailableCardsBrowseButton = new Button("Browse");
                Button thirdPartyAvailableCardsLoadButton = new Button("Load");
                thirdPartyAvailableCardsBrowseButton.getStyleClass().add("small-button");
                thirdPartyAvailableCardsLoadButton.getStyleClass().add("small-button");

                friendsRow1.getChildren().addAll(thirdPartyAvailableCardsField,
                        thirdPartyAvailableCardsBrowseButton, thirdPartyAvailableCardsLoadButton);
                friendsGroup.getChildren().add(friendsRow1);

                Text ouicheListText = new Text("OuicheList");
                ouicheListText.setStyle("-fx-font-size: 14px; -fx-fill: white;");
                friendsGroup.getChildren().add(ouicheListText);

                HBox friendsRow2 = new HBox(5);
                TextField ouicheListField = new TextField();
                ouicheListField.setPromptText("Enter OuicheList file");
                ouicheListField.setPrefColumnCount(30);
                ouicheListField.getStyleClass().addAll("accent-text-field", "fixed-accent-text-field");

                Button ouicheListBrowseButton = new Button("Browse");
                Button loadOuicheListButton = new Button("Load");
                ouicheListBrowseButton.getStyleClass().add("small-button");
                loadOuicheListButton.getStyleClass().add("small-button");

                friendsRow2.getChildren().addAll(ouicheListField,
                        ouicheListBrowseButton, loadOuicheListButton);
                friendsGroup.getChildren().add(friendsRow2);

                HBox friendsRow3 = new HBox(5);
                Button generateThirdPartyListButton = new Button("Generate list");
                Button generateThirdPartyListSaveButton = new Button("Save");
                generateThirdPartyListButton.getStyleClass().add("small-button");
                generateThirdPartyListSaveButton.getStyleClass().add("small-button");

                friendsRow3.getChildren().addAll(
                        generateThirdPartyListButton, generateThirdPartyListSaveButton);
                friendsGroup.getChildren().add(friendsRow3);

                thirdPartyAvailableCardsBrowseButton.setOnAction(e -> {
                    Stage stage = (Stage) thirdPartyAvailableCardsBrowseButton.getScene().getWindow();
                    FileChooser fileChooser = new FileChooser();
                    fileChooser.setTitle("Select 3rd Party Cards File");
                    fileChooser.getExtensionFilters().addAll(
                            new FileChooser.ExtensionFilter("Text Files", "*.txt"));
                    UserInterfaceFunctions.browseThirdPartyAvailableCards(
                            fileChooser, stage, thirdPartyAvailableCardsField);
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
                    fileChooser.getExtensionFilters().addAll(
                            new FileChooser.ExtensionFilter("Text Files", "*.txt"));
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

            // ── SHOPS ─────────────────────────────────────────────────────────────
            case SHOPS: {
                VBox shopsGroup = new VBox(10);
                shopsGroup.setAlignment(Pos.CENTER);

                Button ultraJeuxButton = new Button("UltraJeux");
                ultraJeuxButton.getStyleClass().add("small-button");
                shopsGroup.getChildren().add(ultraJeuxButton);

                ultraJeuxButton.setOnAction(e -> {
                    try {
                        // Ensure OuicheList is loaded
                        if (!UserInterfaceFunctions.getOuicheListIsLoaded()) {
                            if (UserInterfaceFunctions.ouicheListPath == null) {
                                System.out.println("OuicheList must be selected");
                            } else {
                                UserInterfaceFunctions.loadOuicheList();
                            }
                        }

                        if (!UserInterfaceFunctions.getOuicheListIsLoaded()) return;

                        List<Model.CardsLists.CardElement> maOuicheList =
                                Model.CardsLists.OuicheList.getMaOuicheListAsFlatList();
                        double maxPrice = 100;

                        // Show a "Scraping…" placeholder while the (synchronous) call runs
                        Label scraping = new Label("Scraping UltraJeux…");
                        scraping.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 14;");
                        contentPane.getChildren().setAll(scraping);
                        AnchorPane.setTopAnchor(scraping, 10.0);
                        AnchorPane.setLeftAnchor(scraping, 10.0);

                        // Run the scrape
                        List<Model.UltraJeux.ShopResultEntry> shopResults =
                                Model.UltraJeux.CardScraper.getCardNamesFromWebsite(
                                        maOuicheList, maxPrice);

                        // ── Build the results ListView ────────────────────────────────
                        ListView<Model.UltraJeux.ShopResultEntry> listView = new ListView<>();
                        listView.getItems().addAll(shopResults);
                        listView.setCellFactory(lv -> new ShopResultListCell());
                        listView.setStyle(
                                "-fx-background-color: #100317; " +
                                        "-fx-background-insets: 0; " +
                                        "-fx-border-color: transparent;");

                        // Pin the ListView to fill the entire contentPane
                        AnchorPane.setTopAnchor(listView, 0.0);
                        AnchorPane.setBottomAnchor(listView, 0.0);
                        AnchorPane.setLeftAnchor(listView, 0.0);
                        AnchorPane.setRightAnchor(listView, 0.0);

                        // Show a summary label above the list
                        Label summary = new Label(
                                shopResults.size() + " result" + (shopResults.size() != 1 ? "s" : "")
                                        + " — same-card copies are grouped together");
                        summary.setStyle(
                                "-fx-text-fill: #aaaaaa; -fx-font-size: 11; " +
                                        "-fx-padding: 4 8 4 8;");

                        VBox wrapper = new VBox(0, summary, listView);
                        VBox.setVgrow(listView, Priority.ALWAYS);
                        wrapper.setStyle("-fx-background-color: #100317;");

                        AnchorPane.setTopAnchor(wrapper, 0.0);
                        AnchorPane.setBottomAnchor(wrapper, 0.0);
                        AnchorPane.setLeftAnchor(wrapper, 0.0);
                        AnchorPane.setRightAnchor(wrapper, 0.0);

                        contentPane.getChildren().setAll(wrapper);

                    } catch (Exception ex) {
                        ex.printStackTrace();
                        // Show error in contentPane so the user knows something went wrong
                        Label errLabel = new Label("Error during scraping: " + ex.getMessage());
                        errLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12; -fx-wrap-text: true;");
                        errLabel.setWrapText(true);
                        AnchorPane.setTopAnchor(errLabel, 10.0);
                        AnchorPane.setLeftAnchor(errLabel, 10.0);
                        AnchorPane.setRightAnchor(errLabel, 10.0);
                        contentPane.getChildren().setAll(errLabel);
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
        sp.skinProperty().addListener((obs, oldSkin, newSkin) ->
                Platform.runLater(() -> applyStylesToScrollBars(sp)));
        Platform.runLater(() -> applyStylesToScrollBars(sp));
    }

    public VBox getMenuVBox() {
        return menuVBox;
    }

    public AnchorPane getContentPane() {
        return contentPane;
    }

    public AnchorPane getRightHeaderPane() {
        return rightHeaderPane;
    }

    public FilterPane getFilterPane() {
        return filterPane;
    }

    public AnchorPane getRightContentPane() {
        return rightContentPane;
    }

    public AnchorPane getHeaderPane() {
        return headerPane;
    }

    public void setHeaderContent(Node node) {
        headerPane.getChildren().clear();
        headerPane.getChildren().add(node);
    }

    // ── Programmatic scrollbar styling ───────────────────────────────────────────

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
                    inc.setStyle("-fx-background-color: #100317; -fx-background-image: null; " +
                            "-fx-padding: 2; -fx-background-radius: 4;");
                    Node incArrow = inc.lookup(".increment-arrow");
                    if (incArrow == null) incArrow = inc.lookup(".arrow");
                    if (incArrow != null) incArrow.setStyle("-fx-background-color: #cdfc04;");
                }
                if (dec != null) {
                    dec.setStyle("-fx-background-color: #100317; -fx-background-image: null; " +
                            "-fx-padding: 2; -fx-background-radius: 4;");
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