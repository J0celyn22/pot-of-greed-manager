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
    private CardDetailPane cardDetailPane;
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

        Separator leftSeparator = buildLeftNavigationPane();
        HBox headerRow = buildHeaderRow(tabType);
        HBox contentRow = buildContentRow();

        // ── Assemble displayVBox ───────────────────────────────────────────────
        Separator horizontalSeparator = new Separator();
        horizontalSeparator.setStyle("-fx-background-color: white;");
        horizontalSeparator.setPrefHeight(2);

        displayVBox = new VBox();
        displayVBox.setSpacing(0);
        displayVBox.getStyleClass().add("display-vbox");
        displayVBox.getChildren().addAll(headerRow, horizontalSeparator, contentRow);
        HBox.setHgrow(displayVBox, Priority.ALWAYS);

        VBox leftColumnVBox = buildLeftColumn();

        // ── Final assembly: [detail+nav column] | middle+right block ──────────
        this.getChildren().addAll(leftColumnVBox, leftSeparator, displayVBox);

        // Programmatic styling for the navigation menu scrollbars remains
        styleScrollBarsIn(menuScrollPane);
    }

    /**
     * Builds the left navigation menu (fixed width, scrollable) and the vertical
     * separator between it and the middle/right content. The menu's contents are
     * populated later by the controller; this only builds the scaffolding.
     *
     * @return the separator to place between the left column and displayVBox
     */
    private Separator buildLeftNavigationPane() {
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
        Separator leftSeparator = new Separator();
        leftSeparator.setOrientation(Orientation.VERTICAL);
        leftSeparator.setStyle("-fx-background-color: white;");
        leftSeparator.setPrefWidth(2);
        return leftSeparator;
    }

    /**
     * Builds the header row: the tab-specific header (~1/3 width) plus the
     * FilterPane header (~2/3 width), separated by a vertical divider.
     */
    private HBox buildHeaderRow(TabType tabType) {
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
        return headerRow;
    }

    /**
     * Builds the content row: the middle tree-view pane (grows to fill) and the
     * right-pane placeholder (cards display injected later by the controller).
     */
    private HBox buildContentRow() {
        contentPane = new AnchorPane();
        contentPane.getStyleClass().add("content-pane");
        HBox.setHgrow(contentPane, Priority.ALWAYS);

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
        return contentRow;
    }

    /**
     * Builds the left column: the collapsible card-detail pane stacked above the
     * navigation menu (which grows to fill whatever height the detail pane leaves).
     */
    private VBox buildLeftColumn() {
        cardDetailPane = new CardDetailPane();

        Separator detailNavSep = new Separator();
        detailNavSep.setStyle("-fx-background-color: #333333;");
        detailNavSep.setPrefHeight(1);

        // menuScrollPane must grow to fill whatever height remains after the
        // detail pane (which has a variable / collapsible height).
        VBox.setVgrow(menuScrollPane, Priority.ALWAYS);

        VBox leftColumnVBox = new VBox(0, cardDetailPane, detailNavSep, menuScrollPane);
        leftColumnVBox.setPrefWidth(375);
        leftColumnVBox.setStyle("-fx-background-color: #100317;");
        return leftColumnVBox;
    }

    private Runnable onDecksLoad;
    private TabType tabType;

    // OuicheList view-mode toggle buttons (accessible by the controller)
    private Button compactDetailedButton;
    private Button mosaicListButton;
    private Button saveButton;

    /**
     * Toggle button that hides / shows owned (gray) cards in the OuicheList.
     */
    private Button hideOwnedCardsButton;
    private Button incompleteMarkButton;
    /**
     * Toggle button that hides / shows Archetypes and Exceptions sections in the Decks tab.
     */
    private Button hideArchetypesButton;
    /**
     * Toggle button that shows / hides the condition and rarity corner badges on card images.
     * Present on the My Collection, Decks and Collections, and OuicheList tabs.
     */
    private Button showConditionRarityButton;

    public Button getIncompleteMarkButton() {
        return incompleteMarkButton;
    }

    public Button getShowConditionRarityButton() {
        return showConditionRarityButton;
    }

    public Button getCompactDetailedButton() {
        return compactDetailedButton;
    }

    public Button getMosaicListButton() {
        return mosaicListButton;
    }

    /**
     * Returns the "Hide owned cards / Show owned cards" toggle button
     * that is placed in the OuicheList tab header.
     * The button is only non-null when this tab was constructed with
     * {@link TabType#OUICHE_LIST}.
     */
    public Button getHideOwnedCardsButton() {
        return hideOwnedCardsButton;
    }

    /**
     * Returns the "Hide archetypes &amp; exceptions / Show archetypes &amp; exceptions" toggle
     * button placed in the Decks &amp; Collections tab header.
     * Only non-null when this tab was constructed with {@link TabType#DECKS}.
     */
    public Button getHideArchetypesButton() {
        return hideArchetypesButton;
    }

    public void setOnDecksLoad(Runnable onDecksLoad) {
        this.onDecksLoad = onDecksLoad;
    }

    public Button getSaveButton() {
        return saveButton;
    }

    /**
     * Builds the header toolbar content for the given tab type by delegating to a
     * per-tab builder method. The returned node is placed in the top header area of
     * the shared tab layout.
     */
    private Node createHeaderContentForTab(TabType type) {
        HBox headerContent = new HBox(20);
        headerContent.setPadding(new Insets(10));
        headerContent.setAlignment(Pos.CENTER_LEFT);

        switch (type) {
            case MY_COLLECTION:
                headerContent.getChildren().add(buildMyCollectionHeader());
                break;
            case DECKS:
                headerContent.getChildren().add(buildDecksHeader());
                break;
            case OUICHE_LIST:
                headerContent.getChildren().add(buildOuicheListHeader());
                break;
            case FRIENDS:
                headerContent.getChildren().add(buildFriendsHeader());
                break;
            case ARCHETYPES:
                headerContent.getChildren().add(buildArchetypesHeader());
                break;
            case SHOPS:
                headerContent.getChildren().add(buildShopsHeader());
                break;
            default:
                break;
        }
        return headerContent;
    }

    /**
     * Builds the My Collection tab header: Browse/Load/Generate HTML buttons,
     * Save button, and the "Mark incomplete cards" and "Show condition / rarity" toggles.
     */
    private VBox buildMyCollectionHeader() {
        VBox group = new VBox(8);
        group.setAlignment(Pos.CENTER_LEFT);

        // Row 1: Browse | Load | Generate HTML
        TextField collectionFileField = new TextField();
        collectionFileField.setPromptText("Enter collection file path");
        collectionFileField.setPrefColumnCount(30);
        collectionFileField.setText(UserInterfaceFunctions.filePath != null
                ? UserInterfaceFunctions.filePath.getAbsolutePath()
                : "");
        collectionFileField.setVisible(false);
        collectionFileField.setManaged(false);
        collectionFileField.getStyleClass().add("accent-text-field");

        Button browseButton = new Button("Browse");
        Button loadButton = new Button("Load");
        Button generateHtmlButton = new Button("Generate HTML");
        browseButton.getStyleClass().add("small-button");
        loadButton.getStyleClass().add("small-button");
        generateHtmlButton.getStyleClass().add("small-button");

        browseButton.setOnAction(e -> {
            Stage stage = (Stage) browseButton.getScene().getWindow();
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Collection File");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text Files", "*.txt"));
            UserInterfaceFunctions.browseCollectionFile(fileChooser, stage, collectionFileField);
        });
        loadButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.loadCollectionFile();
                logger.info("Collection file loaded.");
            } catch (Exception ex) {
                logger.error("Error loading collection file", ex);
            }
        });
        generateHtmlButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.exportCollectionFile();
            } catch (Exception ex) {
                logger.error("Error generating collection HTML", ex);
            }
        });

        HBox row1 = new HBox(5, browseButton, loadButton, generateHtmlButton);

        // Row 2: Save
        saveButton = new Button("Save");
        saveButton.getStyleClass().add("small-button");
        HBox row2 = new HBox(5, saveButton);

        // Row 3: "Mark incomplete cards" toggle — off by default (dark bg, yellow border)
        incompleteMarkButton = new Button("Mark incomplete cards");
        incompleteMarkButton.setStyle(
                "-fx-background-color: #100317;"
                        + "-fx-text-fill: #cdfc04;"
                        + "-fx-border-color: #cdfc04;"
                        + "-fx-border-width: 1;"
                        + "-fx-border-radius: 4;"
                        + "-fx-background-radius: 4;"
                        + "-fx-font-size: 12px;"
                        + "-fx-padding: 4 10 4 10;"
                        + "-fx-cursor: hand;");
        HBox row3 = new HBox(5, incompleteMarkButton);

        // Row 4: "Show condition / rarity" toggle — on by default (yellow bg, black text)
        showConditionRarityButton = new Button("Show condition / rarity");
        showConditionRarityButton.setStyle(
                "-fx-background-color: #cdfc04;"
                        + "-fx-text-fill: black;"
                        + "-fx-border-color: #cdfc04;"
                        + "-fx-border-width: 1;"
                        + "-fx-border-radius: 4;"
                        + "-fx-background-radius: 4;"
                        + "-fx-font-size: 12px;"
                        + "-fx-padding: 4 10 4 10;"
                        + "-fx-cursor: hand;");
        HBox row4 = new HBox(5, showConditionRarityButton);

        group.getChildren().addAll(row1, row2, row3, row4);
        return group;
    }

    /**
     * Builds the Decks and Collections tab header: Browse/Load/Generate HTML buttons,
     * Save button, and the "Hide archetypes & exceptions" and "Show condition / rarity" toggles.
     */
    private VBox buildDecksHeader() {
        VBox group = new VBox(8);
        group.setAlignment(Pos.CENTER_LEFT);

        TextField directoryField = new TextField();
        directoryField.setPromptText("Enter decks/collections directory");
        directoryField.setPrefColumnCount(30);
        directoryField.setVisible(false);
        directoryField.setManaged(false);
        directoryField.getStyleClass().add("accent-text-field");

        Button browseButton = new Button("Browse");
        Button loadButton = new Button("Load");
        Button generateHtmlButton = new Button("Generate HTML");
        browseButton.getStyleClass().add("small-button");
        loadButton.getStyleClass().add("small-button");
        generateHtmlButton.getStyleClass().add("small-button");

        browseButton.setOnAction(e -> {
            Stage stage = (Stage) browseButton.getScene().getWindow();
            DirectoryChooser folderChooser = new DirectoryChooser();
            folderChooser.setTitle("Select Decks/Collections Folder");
            UserInterfaceFunctions.browseDecksAndCollectionsDirectory(
                    folderChooser, stage, directoryField);
        });
        loadButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
                if (onDecksLoad != null) {
                    onDecksLoad.run();
                }
            } catch (Exception ex) {
                logger.error("Error loading decks and collections directory", ex);
            }
        });
        generateHtmlButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.exportDecksAndCollectionsDirectory();
            } catch (Exception ex) {
                logger.error("Error generating decks/collections HTML", ex);
            }
        });

        saveButton = new Button("Save");
        saveButton.getStyleClass().add("small-button");

        // Row 1: Save | Browse | Load | Generate HTML
        HBox row1 = new HBox(5, saveButton, browseButton, loadButton, generateHtmlButton);

        // Row 2: "Hide archetypes & exceptions" toggle — off by default (dark bg, yellow border)
        hideArchetypesButton = new Button("Hide archetypes & exceptions");
        hideArchetypesButton.setStyle(
                "-fx-background-color: #100317;"
                        + "-fx-text-fill: #cdfc04;"
                        + "-fx-border-color: #cdfc04;"
                        + "-fx-border-width: 1;"
                        + "-fx-border-radius: 4;"
                        + "-fx-background-radius: 4;"
                        + "-fx-font-size: 12px;"
                        + "-fx-padding: 4 10 4 10;"
                        + "-fx-cursor: hand;");
        HBox row2 = new HBox(5, hideArchetypesButton);
        row2.setAlignment(Pos.CENTER_LEFT);

        // Row 3: "Show condition / rarity" toggle — on by default (yellow bg, black text)
        showConditionRarityButton = new Button("Show condition / rarity");
        showConditionRarityButton.setStyle(
                "-fx-background-color: #cdfc04;"
                        + "-fx-text-fill: black;"
                        + "-fx-border-color: #cdfc04;"
                        + "-fx-border-width: 1;"
                        + "-fx-border-radius: 4;"
                        + "-fx-background-radius: 4;"
                        + "-fx-font-size: 12px;"
                        + "-fx-padding: 4 10 4 10;"
                        + "-fx-cursor: hand;");
        HBox row3 = new HBox(5, showConditionRarityButton);
        row3.setAlignment(Pos.CENTER_LEFT);

        group.getChildren().addAll(row1, row2, row3);
        return group;
    }

    /**
     * Builds the OuicheList tab header: generation and save buttons, compact/mosaic
     * mode toggles, and the "Hide owned cards" and "Show condition / rarity" toggles.
     */
    private VBox buildOuicheListHeader() {
        VBox group = new VBox(10);
        group.setAlignment(Pos.CENTER_LEFT);

        // Row 1: Generate (Decks and Collections) | Save
        Button generateDecksButton = new Button("Generate OuicheList \u2013 Decks and Collections");
        generateDecksButton.getStyleClass().add("large-button");
        saveButton = new Button("Save");
        saveButton.getStyleClass().add("small-button");
        generateDecksButton.setOnAction(e -> UserInterfaceFunctions.generateOuicheList());
        saveButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.saveOuicheList();
            } catch (Exception ex) {
                logger.error("Error saving OuicheList", ex);
            }
        });
        HBox row1 = new HBox(5, generateDecksButton, saveButton);

        // Row 2: Generate (Type of cards)
        Button generateTypeButton = new Button("Generate OuicheList \u2013 Type of cards");
        generateTypeButton.getStyleClass().add("large-button");
        generateTypeButton.setOnAction(e -> UserInterfaceFunctions.generateOuicheListType());
        HBox row2 = new HBox(5, generateTypeButton);

        // Row 3: Generate All Lists
        Button generateAllButton = new Button("Generate All Lists");
        generateAllButton.getStyleClass().add("large-button");
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
        HBox row3 = new HBox(5, generateAllButton);

        // Row 4: Compact / Detailed mode toggle, Mosaic / List toggle
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

        HBox row4 = new HBox(5, compactDetailedButton, mosaicListButton);
        row4.setAlignment(Pos.CENTER_LEFT);

        // Row 5: "Hide owned cards" toggle — off by default (dark bg, yellow border)
        hideOwnedCardsButton = new Button("Hide owned cards");
        hideOwnedCardsButton.setStyle(
                "-fx-background-color: #100317;"
                        + "-fx-text-fill: #cdfc04;"
                        + "-fx-border-color: #cdfc04;"
                        + "-fx-border-width: 1;"
                        + "-fx-border-radius: 4;"
                        + "-fx-background-radius: 4;"
                        + "-fx-font-size: 12px;"
                        + "-fx-padding: 4 10 4 10;"
                        + "-fx-cursor: hand;");
        HBox row5 = new HBox(5, hideOwnedCardsButton);
        row5.setAlignment(Pos.CENTER_LEFT);

        // Row 6: "Show condition / rarity" toggle — on by default (yellow bg, black text)
        showConditionRarityButton = new Button("Show condition / rarity");
        showConditionRarityButton.setStyle(
                "-fx-background-color: #cdfc04;"
                        + "-fx-text-fill: black;"
                        + "-fx-border-color: #cdfc04;"
                        + "-fx-border-width: 1;"
                        + "-fx-border-radius: 4;"
                        + "-fx-background-radius: 4;"
                        + "-fx-font-size: 12px;"
                        + "-fx-padding: 4 10 4 10;"
                        + "-fx-cursor: hand;");
        HBox row6 = new HBox(5, showConditionRarityButton);
        row6.setAlignment(Pos.CENTER_LEFT);

        group.getChildren().addAll(row1, row2, row3, row4, row5, row6);
        return group;
    }

    /**
     * Builds the Friends tab header: browse/load fields for 3rd-party available-cards
     * and OuicheList files, plus generate/save buttons.
     */
    private VBox buildFriendsHeader() {
        VBox group = new VBox(10);
        group.setAlignment(Pos.CENTER_LEFT);

        // 3rd Party Available Cards section
        Text thirdPartyLabel = new Text("3rd Party Available Cards");
        thirdPartyLabel.setStyle("-fx-font-size: 14px; -fx-fill: white;");

        TextField thirdPartyField = new TextField();
        thirdPartyField.setPromptText("Enter 3rd party cards file");
        thirdPartyField.setPrefColumnCount(30);
        thirdPartyField.getStyleClass().addAll("accent-text-field", "fixed-accent-text-field");

        Button thirdPartyBrowseButton = new Button("Browse");
        Button thirdPartyLoadButton = new Button("Load");
        thirdPartyBrowseButton.getStyleClass().add("small-button");
        thirdPartyLoadButton.getStyleClass().add("small-button");

        thirdPartyBrowseButton.setOnAction(e -> {
            Stage stage = (Stage) thirdPartyBrowseButton.getScene().getWindow();
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select 3rd Party Cards File");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text Files", "*.txt"));
            UserInterfaceFunctions.browseThirdPartyAvailableCards(
                    fileChooser, stage, thirdPartyField);
        });
        thirdPartyLoadButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.loadThirdPartyAvailableCards();
            } catch (Exception ex) {
                logger.error("Error loading 3rd party available cards", ex);
            }
        });

        HBox thirdPartyRow = new HBox(5, thirdPartyField, thirdPartyBrowseButton, thirdPartyLoadButton);

        // OuicheList section
        Text ouicheListLabel = new Text("OuicheList");
        ouicheListLabel.setStyle("-fx-font-size: 14px; -fx-fill: white;");

        TextField ouicheListField = new TextField();
        ouicheListField.setPromptText("Enter OuicheList file");
        ouicheListField.setPrefColumnCount(30);
        ouicheListField.getStyleClass().addAll("accent-text-field", "fixed-accent-text-field");

        Button ouicheListBrowseButton = new Button("Browse");
        Button ouicheListLoadButton = new Button("Load");
        ouicheListBrowseButton.getStyleClass().add("small-button");
        ouicheListLoadButton.getStyleClass().add("small-button");

        ouicheListBrowseButton.setOnAction(e -> {
            Stage stage = (Stage) ouicheListBrowseButton.getScene().getWindow();
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select OuicheList File");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text Files", "*.txt"));
            UserInterfaceFunctions.browseOuicheList(fileChooser, stage, ouicheListField);
        });
        ouicheListLoadButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.loadOuicheList();
            } catch (Exception ex) {
                logger.error("Error loading OuicheList", ex);
            }
        });

        HBox ouicheListRow = new HBox(5, ouicheListField, ouicheListBrowseButton, ouicheListLoadButton);

        // Generate / Save row
        Button generateListButton = new Button("Generate list");
        Button saveListButton = new Button("Save");
        generateListButton.getStyleClass().add("small-button");
        saveListButton.getStyleClass().add("small-button");

        generateListButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.generateThirdPartyList();
            } catch (Exception ex) {
                logger.error("Error generating 3rd party list", ex);
            }
        });
        saveListButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.saveThirdPartyList();
            } catch (Exception ex) {
                logger.error("Error saving 3rd party list", ex);
            }
        });

        HBox actionsRow = new HBox(5, generateListButton, saveListButton);

        group.getChildren().addAll(
                thirdPartyLabel, thirdPartyRow,
                ouicheListLabel, ouicheListRow,
                actionsRow);
        return group;
    }

    /**
     * Builds the Archetypes tab header: a single "Generate Archetype Lists" button.
     */
    private VBox buildArchetypesHeader() {
        VBox group = new VBox(10);
        group.setAlignment(Pos.CENTER);
        Button generateButton = new Button("Generate Archetype Lists");
        generateButton.getStyleClass().add("large-button");
        generateButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.generateArchetypesListsFunction();
            } catch (Exception ex) {
                logger.error("Error generating archetype lists", ex);
            }
        });
        group.getChildren().add(generateButton);
        return group;
    }

    /**
     * Builds the Shops tab header: a max-price field and the UltraJeux scrape button.
     * Scrape results are displayed in {@link #contentPane}.
     */
    private VBox buildShopsHeader() {
        VBox group = new VBox(10);
        group.setAlignment(Pos.CENTER_LEFT);

        Label maxPriceLabel = new Label("Max price (\u20ac)");
        maxPriceLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12;");
        TextField maxPriceField = new TextField("0.30");
        maxPriceField.setPrefColumnCount(5);
        maxPriceField.getStyleClass().add("accent-text-field");
        HBox maxPriceRow = new HBox(8, maxPriceLabel, maxPriceField);
        maxPriceRow.setAlignment(Pos.CENTER_LEFT);

        Button ultraJeuxButton = new Button("UltraJeux");
        ultraJeuxButton.getStyleClass().add("small-button");
        ultraJeuxButton.setOnAction(e -> runUltraJeuxScrape(maxPriceField));

        group.getChildren().addAll(maxPriceRow, ultraJeuxButton);
        return group;
    }

    /**
     * Runs the UltraJeux scrape using the price entered in {@code maxPriceField} and
     * displays the results (or an error label) in {@link #contentPane}.
     */
    private void runUltraJeuxScrape(TextField maxPriceField) {
        try {
            if (!UserInterfaceFunctions.getOuicheListIsLoaded()) {
                if (UserInterfaceFunctions.ouicheListPath == null) {
                    logger.warn("UltraJeux scrape requested but no OuicheList path is set.");
                } else {
                    UserInterfaceFunctions.loadOuicheList();
                }
            }
            if (!UserInterfaceFunctions.getOuicheListIsLoaded()) {
                return;
            }

            List<Model.CardsLists.CardElement> ouicheList =
                    Model.CardsLists.OuicheList.getMaOuicheListAsFlatList();

            double maxPrice;
            try {
                maxPrice = Double.parseDouble(
                        maxPriceField.getText().trim().replace(',', '.'));
                if (maxPrice <= 0) {
                    throw new NumberFormatException("Price must be positive");
                }
            } catch (NumberFormatException nfe) {
                maxPriceField.setStyle(maxPriceField.getStyle() + " -fx-border-color: #ff6666;");
                Label errorLabel = new Label("Invalid max price \u2014 please enter a positive number.");
                errorLabel.setStyle("-fx-text-fill: #ff6666; -fx-font-size: 12;");
                AnchorPane.setTopAnchor(errorLabel, 10.0);
                AnchorPane.setLeftAnchor(errorLabel, 10.0);
                contentPane.getChildren().setAll(errorLabel);
                return;
            }
            maxPriceField.setStyle("");

            Label scrapingLabel = new Label("Scraping UltraJeux\u2026");
            scrapingLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 14;");
            AnchorPane.setTopAnchor(scrapingLabel, 10.0);
            AnchorPane.setLeftAnchor(scrapingLabel, 10.0);
            contentPane.getChildren().setAll(scrapingLabel);

            List<Model.UltraJeux.ShopResultEntry> shopResults =
                    Model.UltraJeux.CardScraper.getCardNamesFromWebsite(ouicheList, maxPrice);

            ListView<Model.UltraJeux.ShopResultEntry> resultsListView = new ListView<>();
            resultsListView.getItems().addAll(shopResults);
            resultsListView.setCellFactory(lv -> new ShopResultListCell());
            resultsListView.setStyle(
                    "-fx-background-color: #100317;"
                            + "-fx-background-insets: 0;"
                            + "-fx-border-color: transparent;");
            AnchorPane.setTopAnchor(resultsListView, 0.0);
            AnchorPane.setBottomAnchor(resultsListView, 0.0);
            AnchorPane.setLeftAnchor(resultsListView, 0.0);
            AnchorPane.setRightAnchor(resultsListView, 0.0);

            String resultCount = shopResults.size() + " result"
                    + (shopResults.size() != 1 ? "s" : "")
                    + " \u2014 same-card copies are grouped together";
            Label summaryLabel = new Label(resultCount);
            summaryLabel.setStyle(
                    "-fx-text-fill: #aaaaaa; -fx-font-size: 11; -fx-padding: 4 8 4 8;");

            VBox resultsWrapper = new VBox(0, summaryLabel, resultsListView);
            VBox.setVgrow(resultsListView, Priority.ALWAYS);
            resultsWrapper.setStyle("-fx-background-color: #100317;");
            AnchorPane.setTopAnchor(resultsWrapper, 0.0);
            AnchorPane.setBottomAnchor(resultsWrapper, 0.0);
            AnchorPane.setLeftAnchor(resultsWrapper, 0.0);
            AnchorPane.setRightAnchor(resultsWrapper, 0.0);

            contentPane.getChildren().setAll(resultsWrapper);

        } catch (Exception ex) {
            logger.error("Error during UltraJeux scraping", ex);
            Label errorLabel = new Label("Error during scraping: " + ex.getMessage());
            errorLabel.setStyle(
                    "-fx-text-fill: #ff6666; -fx-font-size: 12; -fx-wrap-text: true;");
            errorLabel.setWrapText(true);
            AnchorPane.setTopAnchor(errorLabel, 10.0);
            AnchorPane.setLeftAnchor(errorLabel, 10.0);
            AnchorPane.setRightAnchor(errorLabel, 10.0);
            contentPane.getChildren().setAll(errorLabel);
        }
    }

    private void styleScrollBarsIn(ScrollPane scrollPane) {
        if (scrollPane == null) {
            return;
        }
        scrollPane.skinProperty().addListener((obs, oldSkin, newSkin) ->
                Platform.runLater(() -> applyStylesToScrollBars(scrollPane)));
        Platform.runLater(() -> applyStylesToScrollBars(scrollPane));
    }

    public VBox getMenuVBox() {
        return menuVBox;
    }

    public CardDetailPane getCardDetailPane() {
        return cardDetailPane;
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

    private void applyStylesToScrollBars(ScrollPane scrollPane) {
        try {
            Set<Node> bars = scrollPane.lookupAll(".scroll-bar");
            bars.addAll(scrollPane.lookupAll(".overlay-scroll-bar"));

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