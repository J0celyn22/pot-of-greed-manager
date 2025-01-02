package View;

import Controller.UserInterfaceFunctions;
import Model.CardsLists.CardElement;
import Model.Database.DataBaseUpdate;
import Model.UltraJeux.CardScraper;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static Controller.UserInterfaceFunctions.*;
import static Model.CardsLists.OuicheList.getMaOuicheList;

public class RealMain extends Application {
    /**
     * Updates the local cache to the latest revision available online.
     * This needs to be done every time the application is launched so that
     * the local cache is always up to date.
     * <p>
     * Then, it launches the JavaFX application.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        DataBaseUpdate.updateCache();
        launch(args);
    }

    /**
     * This is the main method of the JavaFX application.
     * It will launch the GUI and set up the main layout.
     * It will also set up the FileChooser dialogs and the DirectoryChooser dialogs.
     * It will also set up the TextFields and the Buttons.
     * It will also set up the event handlers for the buttons.
     * It will also load the OuicheList from the file selected by the user.
     * It will also generate the HTML files for the Collection and the Decks and Collections.
     * It will also generate the OuicheList.
     * It will also generate the Archetypes lists.
     * @param primaryStage the stage for the application
     * @throws FileNotFoundException if the file selected by the user cannot be found
     */
    @Override
    public void start(Stage primaryStage) throws FileNotFoundException {
        primaryStage.setTitle("Pot of Greed Manager");

        Image iconImage = new Image(new FileInputStream("./src/main/resources/WokOfGreedSpirit.jpg"));

        primaryStage.getIcons().addAll(
                new Image(new FileInputStream("./src/main/resources/WokOfGreedSpirit_64.jpg")),
                new Image(new FileInputStream("./src/main/resources/WokOfGreedSpirit_32.jpg")),
                new Image(new FileInputStream("./src/main/resources/WokOfGreedSpirit_16.jpg"))
        );

        UserInterfaceFunctions.readPathsFromFile();

        // Create FileChooser
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Resource File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
        );

        // Create DirectoryChooser
        DirectoryChooser folderChooser = new DirectoryChooser();
        folderChooser.setTitle("Select Decks/Collections Folder");
        if (folderPath != null) {
            folderChooser.setInitialDirectory(folderPath);
        }

        // Create FileChooser
        FileChooser thirdPartyListFileChooser = new FileChooser();
        thirdPartyListFileChooser.setTitle("Select Resource File");
        thirdPartyListFileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
        );

        // Create FileChooser
        FileChooser ouicheListFileChooser = new FileChooser();
        ouicheListFileChooser.setTitle("Select Ouiche List File");
        ouicheListFileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt")
        );

        // Text fields for displaying folder paths
        TextField collectionFileField = new TextField();
        collectionFileField.setEditable(true);
        collectionFileField.setPrefColumnCount(30); // Adjust the width
        collectionFileField.setText(filePath != null ? filePath.getAbsolutePath() : "");

        TextField decksAndCollectionDirectoryField = new TextField();
        decksAndCollectionDirectoryField.setEditable(true);
        decksAndCollectionDirectoryField.setPrefColumnCount(30); // Adjust the width
        decksAndCollectionDirectoryField.setText(folderPath != null ? folderPath.getAbsolutePath() : "");

        // Titles
        Text collectionFileText = new Text("My Collection");
        collectionFileText.setStyle("-fx-font-size: 30px; -fx-fill: white;");

        Text decksAndCollectionDirectoryText = new Text("Decks and Collections");
        decksAndCollectionDirectoryText.setStyle("-fx-font-size: 30px; -fx-fill: white;");

        Text thirdPartyAvailableCardsText = new Text("3rd party available cards");
        thirdPartyAvailableCardsText.setStyle("-fx-font-size: 30px; -fx-fill: white;");

        Text ouicheListText = new Text("OuicheList");
        ouicheListText.setStyle("-fx-font-size: 30px; -fx-fill: white;");

        // Buttons to open DirectoryChooser dialogs
        Button collectionFileButton = new Button("Browse");
        collectionFileButton.setOnAction(e -> UserInterfaceFunctions.browseCollectionFile(fileChooser, primaryStage, collectionFileField));

        Button collectionFileLoadButton = new Button("Load");
        collectionFileLoadButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.loadCollectionFile();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        /*Button collectionFileSaveButton = new Button("Save");
        collectionFileSaveButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.saveCollectionFile();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });*/

        Button collectionFileGenerateHTMLButton = new Button("Generate HTML");
        collectionFileGenerateHTMLButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.exportCollectionFile();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        Button decksAndCollectionsDirectoryButton = new Button("Browse");
        decksAndCollectionsDirectoryButton.setOnAction(e -> UserInterfaceFunctions.browseDecksAndCollectionsDirectory(folderChooser, primaryStage, decksAndCollectionDirectoryField));

        Button decksAndCollectionsDirectoryLoadButton = new Button("Load");
        decksAndCollectionsDirectoryLoadButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        /*Button decksAndCollectionsDirectorySaveButton = new Button("Save");
        decksAndCollectionsDirectorySaveButton.setOnAction(e -> UserInterfaceFunctions.saveDecksAndCollectionsDirectory());*/

        Button decksAndCollectionsDirectoryGenerateHTMLButton = new Button("Generate HTML");
        decksAndCollectionsDirectoryGenerateHTMLButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.exportDecksAndCollectionsDirectory();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        // New set of title, DirectoryChooser, TextField, Browse, Load, Save buttons
        TextField thirdPartyAvailableCardsField = new TextField();
        thirdPartyAvailableCardsField.setEditable(true);
        thirdPartyAvailableCardsField.setPrefColumnCount(30); // Adjust the width
        collectionFileField.setText(thirdPartyListPath != null ? thirdPartyListPath.getAbsolutePath() : "");


        Button thirdPartyAvailableCardsBrowseButton = new Button("Browse");
        thirdPartyAvailableCardsBrowseButton.setOnAction(e -> UserInterfaceFunctions.browseThirdPartyAvailableCards(thirdPartyListFileChooser, primaryStage, thirdPartyAvailableCardsField));

        Button thirdPartyAvailableCardsLoadButton = new Button("Load");
        thirdPartyAvailableCardsLoadButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.loadThirdPartyAvailableCards();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        // New set of title, DirectoryChooser, TextField, Browse, Load, Save buttons
        TextField ouicheListField = new TextField();
        ouicheListField.setEditable(true);
        ouicheListField.setPrefColumnCount(30); // Adjust the width
        ouicheListField.setText(ouicheListPath != null ? ouicheListPath.getAbsolutePath() : "");

        Button ouicheListBrowseButton = new Button("Browse");
        ouicheListBrowseButton.setOnAction(e -> UserInterfaceFunctions.browseOuicheList(ouicheListFileChooser, primaryStage, ouicheListField));

        Button loadOuicheListButton = new Button("Load");
        loadOuicheListButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.loadOuicheList();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        Button generateThirdPartyListButton = new Button("Generate list");
        generateThirdPartyListButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.generateThirdPartyList();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        Button generateThirdPartyListSaveButton = new Button("Save");
        generateThirdPartyListSaveButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.saveThirdPartyList();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        // Create Generate button
        Button generateOuicheListButton = new Button("Generate Ouichelist – Decks and Collections");
        generateOuicheListButton.setOnAction(e -> UserInterfaceFunctions.generateOuicheList());

        Button generateOuicheListSaveButton = new Button("Save");
        generateOuicheListSaveButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.saveOuicheList();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });

        Button generateOuicheListTypeButton = new Button("Generate Ouichelist – Type of cards");
        generateOuicheListTypeButton.setOnAction(e -> UserInterfaceFunctions.generateOuicheListType());

        Button generateAllButton = new Button("Generate All Lists");
        generateAllButton.setOnAction(e -> {
            try {
                //Load Collection
                UserInterfaceFunctions.loadCollectionFile();
                //Generate Collection
                UserInterfaceFunctions.exportCollectionFile();
                //Load decks and collections
                UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
                //Generate decks and collections
                UserInterfaceFunctions.exportDecksAndCollectionsDirectory();
                //Generate OuicheList - Decks and collections
                UserInterfaceFunctions.generateOuicheList();
                //Generate OuicheList - Type of cards
                UserInterfaceFunctions.generateOuicheListType();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        // Create Generate Archetypes lists button
        Button generateArchetypesListsButton = new Button("Generate Archetypes Lists");
        generateArchetypesListsButton.setOnAction(e -> {
            try {
                UserInterfaceFunctions.generateArchetypesListsFunction();
                UserInterfaceFunctions.playLaughSound();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });

        // Layout for the first set of elements
        VBox firstSet = new VBox(10);
        firstSet.getChildren().addAll(
                collectionFileText,
                new HBox(collectionFileField, collectionFileButton, collectionFileLoadButton/*, collectionFileSaveButton*/, collectionFileGenerateHTMLButton),
                decksAndCollectionDirectoryText,
                new HBox(decksAndCollectionDirectoryField, decksAndCollectionsDirectoryButton, decksAndCollectionsDirectoryLoadButton/*, decksAndCollectionsDirectorySaveButton*/, decksAndCollectionsDirectoryGenerateHTMLButton),
                new HBox(generateOuicheListButton, generateOuicheListSaveButton),
                new HBox(generateOuicheListTypeButton/*, generateOuicheListTypeSaveButton*/),
                new HBox(generateAllButton)
        );
        firstSet.setSpacing(20);
        firstSet.setAlignment(Pos.CENTER_LEFT);
        firstSet.setPadding(new Insets(10));
        firstSet.setStyle("-fx-background-color: rgba(50, 50, 50, 0.5);");
        firstSet.setMaxHeight(400); // Adjust the height

        // Layout for the second set of elements
        VBox secondSet = new VBox(10);
        secondSet.getChildren().addAll(
                thirdPartyAvailableCardsText,
                new HBox(thirdPartyAvailableCardsField, thirdPartyAvailableCardsBrowseButton, thirdPartyAvailableCardsLoadButton),
                ouicheListText,
                new HBox(ouicheListField, ouicheListBrowseButton, loadOuicheListButton),
                new HBox(generateThirdPartyListButton, generateThirdPartyListSaveButton)
        );
        secondSet.setSpacing(20);
        secondSet.setAlignment(Pos.CENTER_LEFT);
        secondSet.setPadding(new Insets(10));
        secondSet.setStyle("-fx-background-color: rgba(50, 50, 50, 0.5);");
        secondSet.setMaxHeight(200); // Adjust the height

        // Main layout
        VBox mainLayout = new VBox(20);
        HBox contentLayout = new HBox(20);
        contentLayout.getChildren().addAll(firstSet, secondSet);
        contentLayout.setSpacing(20);
        contentLayout.setAlignment(Pos.CENTER_LEFT);

        mainLayout.getChildren().addAll(contentLayout, generateArchetypesListsButton);
        mainLayout.setSpacing(20);
        mainLayout.setAlignment(Pos.CENTER_LEFT);

        VBox.setMargin(generateArchetypesListsButton, new Insets(10, 10, 10, 20));

        BackgroundImage backgroundImage = new BackgroundImage(
                iconImage,
                BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER,
                BackgroundSize.DEFAULT
        );

        mainLayout.setBackground(new Background(backgroundImage));

        //TODO TEMP ?
        // Add the card scraper button
        Button cardScraperButton = new Button("Scrape Cards");
        cardScraperButton.setOnAction(e -> {
            try {
                if (!getOuicheListIsLoaded()) {
                    if (ouicheListPath == null) {
                        //TODO fenêtre erreur :
                        System.out.println("OuicheList must be selected");
                    } else {
                        loadOuicheList();
                    }
                }

                if (getOuicheListIsLoaded()) {
                    List<CardElement> maOuicheList = getMaOuicheList(); // Populate this list with your CardElement objects
                    double maxPrice = 100; // Set the maximum price

                    Map<String, List<String>> cardNamesFromWebsite = CardScraper.getCardNamesFromWebsite(maOuicheList, maxPrice);

                    for (Map.Entry<String, List<String>> entry : cardNamesFromWebsite.entrySet()) {
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

        // Add the card scraper button to the main layout
        mainLayout.getChildren().add(cardScraperButton);
        VBox.setMargin(cardScraperButton, new Insets(10, 10, 10, 20));
        //TODO TEMP ? END

        Scene scene = new Scene(mainLayout, 600, 300);
        primaryStage.setScene(scene);

        // Set the stage dimensions to match the screen size
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        primaryStage.setX(screenBounds.getMinX());
        primaryStage.setY(screenBounds.getMinY());
        primaryStage.setWidth(screenBounds.getWidth());
        primaryStage.setHeight(screenBounds.getHeight());

        primaryStage.show();
    }
}