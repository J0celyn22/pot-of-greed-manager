package View;

import Controller.UserInterfaceFunctions;
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

/*
 * SharedCollectionTab.java
 *
 * This class builds the UI for each tab using a three-column layout.
 * The left pane is used for the navigation menu, the middle for the content (which is further
 * divided into the header and the main display area), and previously the right pane was empty.
 * For our updated design, we remove the blank right pane so that the tab only has the left and middle parts.
 */
public class SharedCollectionTab extends HBox {

    private static final Logger logger = LoggerFactory.getLogger(SharedCollectionTab.class);
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
    // Callback for DECKS tab load button updates.
    private Runnable onDecksLoad;
    // The empty right part has been removed
    private TabType tabType;
    public SharedCollectionTab(TabType tabType) {
        this.tabType = tabType;
        this.setSpacing(0);

        // Left pane: Navigation menu (fixed width)
        menuVBox = new VBox();
        menuVBox.setStyle("-fx-background-color: #100317; -fx-text-fill: white;");
        menuScrollPane = new ScrollPane(menuVBox);
        menuScrollPane.setFitToWidth(true);
        menuScrollPane.setFitToHeight(true);
        menuScrollPane.setPrefWidth(375);

        // Vertical separator between left and middle.
        Separator sepLeft = new Separator();
        sepLeft.setOrientation(Orientation.VERTICAL);
        sepLeft.setStyle("-fx-background-color: white;");
        sepLeft.setPrefWidth(2);

        // Middle pane: Display area (header and content).
        displayVBox = new VBox();
        displayVBox.setSpacing(0);
        displayVBox.setStyle("-fx-background-color: #100317; -fx-text-fill: white;");

        // Header pane (top middle part)
        headerPane = new AnchorPane();
        headerPane.setPrefHeight(200);
        headerPane.setStyle("-fx-background-color: #100317;");
        Node headerContent = createHeaderContentForTab(tabType);
        setHeaderContent(headerContent);

        // Horizontal separator between header and content.
        Separator sepHoriz = new Separator();
        sepHoriz.setStyle("-fx-background-color: white;");
        sepHoriz.setPrefHeight(2);

        // Content pane (collection/decks view)
        contentPane = new AnchorPane();
        contentPane.setStyle("-fx-background-color: #100317; -fx-text-fill: white;");
        VBox.setVgrow(contentPane, Priority.ALWAYS);

        displayVBox.getChildren().addAll(headerPane, sepHoriz, contentPane);
        HBox.setHgrow(displayVBox, Priority.ALWAYS);

        // Assemble the two columns only (left and middle)
        this.getChildren().addAll(menuScrollPane, sepLeft, displayVBox);
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

                Button collectionFileButton = new Button("Browse");
                Button collectionFileLoadButton = new Button("Load");
                Button collectionFileGenerateHTMLButton = new Button("Generate HTML");
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

                Button decksAndCollectionsDirectoryButton = new Button("Browse");
                Button decksAndCollectionsDirectoryLoadButton = new Button("Load");
                Button decksAndCollectionsDirectoryGenerateHTMLButton = new Button("Generate HTML");
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
                Button thirdPartyAvailableCardsBrowseButton = new Button("Browse");
                Button thirdPartyAvailableCardsLoadButton = new Button("Load");
                friendsRow1.getChildren().addAll(thirdPartyAvailableCardsField, thirdPartyAvailableCardsBrowseButton, thirdPartyAvailableCardsLoadButton);
                friendsGroup.getChildren().add(friendsRow1);

                Text ouicheListText = new Text("OuicheList");
                ouicheListText.setStyle("-fx-font-size: 14px; -fx-fill: white;");
                friendsGroup.getChildren().add(ouicheListText);

                HBox friendsRow2 = new HBox(5);
                TextField ouicheListField = new TextField();
                ouicheListField.setPromptText("Enter OuicheList file");
                ouicheListField.setPrefColumnCount(30);
                Button ouicheListBrowseButton = new Button("Browse");
                Button loadOuicheListButton = new Button("Load");
                friendsRow2.getChildren().addAll(ouicheListField, ouicheListBrowseButton, loadOuicheListButton);
                friendsGroup.getChildren().add(friendsRow2);

                HBox friendsRow3 = new HBox(5);
                Button generateThirdPartyListButton = new Button("Generate list");
                Button generateThirdPartyListSaveButton = new Button("Save");
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
            /*case SHOPS: {
                VBox shopsGroup = new VBox(10);
                shopsGroup.setAlignment(Pos.CENTER);
                Button scrapeCardsButton = new Button("Scrape Cards");
                shopsGroup.getChildren().add(scrapeCardsButton);
                scrapeCardsButton.setOnAction(e -> {
                    try {
                        if (!UserInterfaceFunctions.getOuicheListIsLoaded()) {
                            if (UserInterfaceFunctions.ouicheListPath == null) {
                                System.out.println("OuicheList must be selected");
                            } else {
                                UserInterfaceFunctions.loadOuicheList();
                            }
                        }
                        if (UserInterfaceFunctions.getOuicheListIsLoaded()) {
                            java.util.List<Model.CardsLists.CardElement> maOuicheList = Model.CardsLists.OuicheList.getMaOuicheList();
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
            }*/
            case SHOPS: {
                VBox shopsGroup = new VBox(10);
                shopsGroup.setAlignment(Pos.CENTER);

                // New UltraJeux button
                Button ultraJeuxButton = new Button("UltraJeux");
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

    public enum TabType {
        MY_COLLECTION,
        DECKS,
        OUICHE_LIST,
        FRIENDS,
        ARCHETYPES,
        SHOPS;
    }
}