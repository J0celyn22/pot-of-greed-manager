package Controller;

import Model.CardsLists.*;
import View.*;
import View.SharedCollectionTab.TabType;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RealMainController {

    private static final Logger logger = LoggerFactory.getLogger(RealMainController.class);

    // Shared properties for zooming the card view cells.
    private final DoubleProperty cardWidthProperty = new SimpleDoubleProperty(100);
    private final DoubleProperty cardHeightProperty = new SimpleDoubleProperty(146);

    @FXML
    private TabPane mainTabPane;

    // Right pane nodes (from main_layout.fxml)
    @FXML
    private VBox allExistingCardsPane;
    @FXML
    private TextField nameTextField;
    @FXML
    private TextField printcodeTextField;
    @FXML
    private Button searchButton;
    @FXML
    private Button listMosaicButton;       // Toggle for view mode.
    @FXML
    private Button printedUniqueButton;    // Toggle for data source.
    @FXML
    private AnchorPane cardsDisplayContainer; // Container for the card display.

    // Instances of our custom controls for tabs.
    private SharedCollectionTab myCollectionTab;
    private SharedCollectionTab decksTab;
    private SharedCollectionTab ouicheListTab;
    private SharedCollectionTab archetypesTab;
    private SharedCollectionTab friendsTab;
    private SharedCollectionTab shopsTab;

    // Flags for toggles.
    // Default: mosaic view is active (isMosaicMode=true) and data source is AllCardsList (isPrintedMode=false).
    private boolean isMosaicMode = true;
    private boolean isPrintedMode = false;

    private boolean ouicheListLoaded = false;
    private VBox decksMosaicContainer;
    private ScrollPane decksScrollPane;

    @FXML
    private void initialize() {
        // Read default paths.
        UserInterfaceFunctions.readPathsFromFile();

        // Initialize toggle button texts to match default display.
        listMosaicButton.setText("List");
        printedUniqueButton.setText("Printed");

        // Create instances of custom controls with proper tab types.
        myCollectionTab = new SharedCollectionTab(TabType.MY_COLLECTION);
        decksTab = new SharedCollectionTab(TabType.DECKS);
        ouicheListTab = new SharedCollectionTab(TabType.OUICHE_LIST);
        archetypesTab = new SharedCollectionTab(TabType.ARCHETYPES);
        friendsTab = new SharedCollectionTab(TabType.FRIENDS);
        shopsTab = new SharedCollectionTab(TabType.SHOPS);

        // Set up zoom functionality for tabs that display card grids.
        setupZoom(myCollectionTab);
        setupZoom(decksTab);
        setupZoom(ouicheListTab);
        setupZoom(archetypesTab);

        // Assign custom controls to the corresponding tabs.
        if (mainTabPane.getTabs().size() >= 6) {
            mainTabPane.getTabs().get(0).setContent(myCollectionTab);
            mainTabPane.getTabs().get(1).setContent(decksTab);
            mainTabPane.getTabs().get(2).setContent(ouicheListTab);
            mainTabPane.getTabs().get(3).setContent(archetypesTab);
            mainTabPane.getTabs().get(4).setContent(friendsTab);
            mainTabPane.getTabs().get(5).setContent(shopsTab);
        }

        try {
            displayMyCollection();
            populateMyCollectionMenu();
        } catch (Exception ex) {
            logger.error("Error displaying My Collection", ex);
        }

        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            int selectedIndex = mainTabPane.getTabs().indexOf(newTab);
            if (selectedIndex == 1) { // Decks tab.
                try {
                    displayDecksAndCollections();
                } catch (Exception e) {
                    logger.error("Error displaying decks and collections", e);
                }
            }
        });

        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            int selectedIndex = mainTabPane.getTabs().indexOf(newTab);
            if (selectedIndex == 1) {  // Assuming index 1 is the Decks tab.
                try {
                    populateDecksAndCollectionsMenu();
                    displayDecksAndCollections();
                } catch (Exception e) {
                    logger.error("Error displaying decks and collections", e);
                }
            } else if (selectedIndex == 2 && !ouicheListLoaded) {
                try {
                    // Trigger generation of the OuicheList
                    UserInterfaceFunctions.generateOuicheList();
                    // Now display the unified layout for OuicheList
                    displayOuicheListUnified();
                    populateOuicheListMenu();
                    ouicheListLoaded = true;
                } catch (Exception ex) {
                    logger.error("Error displaying OuicheList", ex);
                }
            }
        });

        decksTab.setOnDecksLoad(() -> {
            try {
                displayDecksAndCollections();
            } catch (Exception e) {
                logger.error("Error displaying decks and collections", e);
            }
        });

        mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            int selectedIndex = mainTabPane.getTabs().indexOf(newTab);
            // Assuming the OuicheList tab is the third tab (index == 2)
            if (selectedIndex == 2 && !ouicheListLoaded) {
                try {
                    displayOuicheListUnified();
                    populateOuicheListMenu();
                    ouicheListLoaded = true;
                } catch (Exception ex) {
                    logger.error("Error displaying OuicheList", ex);
                }
            }
        });


        // Setup toggle and search actions for the All Existing Cards pane.
        searchButton.setOnAction(e -> updateCardsDisplay());
        listMosaicButton.setOnAction(e -> {
            isMosaicMode = !isMosaicMode;
            listMosaicButton.setText(isMosaicMode ? "List" : "Mosaic");
            updateCardsDisplay();
        });
        printedUniqueButton.setOnAction(e -> {
            isPrintedMode = !isPrintedMode;
            printedUniqueButton.setText(isPrintedMode ? "Unique" : "Printed");
            updateCardsDisplay();
        });
        nameTextField.textProperty().addListener((obs, oldVal, newVal) -> updateCardsDisplay());
        printcodeTextField.textProperty().addListener((obs, oldVal, newVal) -> updateCardsDisplay());

        // Initially populate the cards display.
        updateCardsDisplay();
    }

    private void setupZoom(SharedCollectionTab tab) {
        tab.getContentPane().addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                adjustCardSize(event.getDeltaY());
                event.consume();
            }
        });
    }

    private void adjustCardSize(double delta) {
        double scalingFactor = delta > 0 ? 1.1 : 0.9;
        double newWidth = cardWidthProperty.get() * scalingFactor;
        if (newWidth < 50) newWidth = 50;
        else if (newWidth > 300) newWidth = 300;
        cardWidthProperty.set(newWidth);
        cardHeightProperty.set(newWidth * 146.0 / 100.0);
    }

    /**
     * Groups the given list into sublists (rows) dynamically, based on the available width.
     * Uses the desired cell width and horizontal gap.
     */
    private List<List<Card>> groupCardsIntoRows(List<Card> cards, double availableWidth, double cellWidth, double gap) {
        int cardsPerRow = (int) Math.floor((availableWidth + gap) / (cellWidth + gap));
        if (cardsPerRow < 1) cardsPerRow = 1;
        List<List<Card>> rows = new ArrayList<>();
        for (int i = 0; i < cards.size(); i += cardsPerRow) {
            int end = Math.min(i + cardsPerRow, cards.size());
            rows.add(new ArrayList<>(cards.subList(i, end)));
        }
        return rows;
    }

    /**
     * Updates the bottom-right cards display according to the current toggles and filters.
     * In mosaic mode, a virtual ListView displays rows of card images.
     * In list mode, a virtual ListView displays detailed list cells.
     */
    private void updateCardsDisplay() {
        String nameFilter = nameTextField.getText().toLowerCase().trim();
        String codeFilter = printcodeTextField.getText().toLowerCase().trim();

        List<Card> allCards = null;
        try {
            allCards = isPrintedMode
                    ? Model.Database.Database.getAllPrintedCardsList().values().stream().collect(Collectors.toList())
                    : Model.Database.Database.getAllCardsList().values().stream().collect(Collectors.toList());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        List<Card> filteredCards = allCards.stream().filter(card -> {
            boolean matchesName = nameFilter.isEmpty() ||
                    (card.getName_EN() != null && card.getName_EN().toLowerCase().contains(nameFilter)) ||
                    (card.getName_FR() != null && card.getName_FR().toLowerCase().contains(nameFilter)) ||
                    (card.getName_JA() != null && card.getName_JA().toLowerCase().contains(nameFilter));
            boolean matchesCode = codeFilter.isEmpty() ||
                    (isPrintedMode
                            ? (card.getPrintCode() != null && card.getPrintCode().toLowerCase().contains(codeFilter))
                            : (card.getPassCode() != null && card.getPassCode().toLowerCase().contains(codeFilter)));
            return matchesName && matchesCode;
        }).collect(Collectors.toList());

        // Force load the first image for diagnostic purposes.
        /*if (!filteredCards.isEmpty()) {
            String firstImagePath = filteredCards.get(0).getImagePath();
            Image testImage = null;
            try {
                if (firstImagePath.startsWith("http://") || firstImagePath.startsWith("https://")) {
                    testImage = new Image(firstImagePath);
                } else if (!firstImagePath.startsWith("file:")) {
                    testImage = new Image("file:" + firstImagePath);
                } else {
                    testImage = new Image(firstImagePath);
                }
                logger.info("First image test URL: " + testImage.getUrl());
            } catch(Exception e) {
                logger.error("Failed to load first image for diagnostic. Path: " + firstImagePath, e);
            }
        } else {
            logger.info("No cards after filtering.");
        }*/
        // Force load the first image for diagnostic purposes.
        if (!filteredCards.isEmpty()) {
            String firstImagePath = filteredCards.get(0).getImagePath();
            Image testImage = null;
            try {
                if (firstImagePath.startsWith("http://") || firstImagePath.startsWith("https://")) {
                    testImage = new Image(firstImagePath, 100, 146, true, true, true);
                } else if (!firstImagePath.startsWith("file:")) {
                    testImage = new Image("file:" + firstImagePath, 100, 146, true, true, true);
                } else {
                    testImage = new Image(firstImagePath, 100, 146, true, true, true);
                }
                logger.info("First image test URL: " + testImage.getUrl());
            } catch (Exception e) {
                logger.error("Failed to load first image for diagnostic. Path: " + firstImagePath, e);
            }
        } else {
            logger.info("No cards after filtering.");
        }


        Node view;
        double mosaicImageWidth = 100, mosaicImageHeight = 146;
        double listImageWidth = 80, listImageHeight = 116;
        if (isMosaicMode) {
            double availableWidth = cardsDisplayContainer.getWidth();
            if (availableWidth <= 0) {
                availableWidth = 375; // fallback
            }
            double gap = 5;
            List<List<Card>> rows = groupCardsIntoRows(filteredCards, availableWidth, mosaicImageWidth, gap);
            ListView<List<Card>> mosaicListView = new ListView<>(FXCollections.observableArrayList(rows));
            // Pass only image sizes here; cell will use its internal logic.
            mosaicListView.setCellFactory(param -> new CardsMosaicRowCell(mosaicImageWidth, mosaicImageHeight));
            mosaicListView.setStyle("-fx-background-color: #100317; -fx-control-inner-background: #100317;");
            view = mosaicListView;
        } else {
            ListView<Card> listView = new ListView<>(FXCollections.observableArrayList(filteredCards));
            listView.setCellFactory(param -> new CardsListCell(isPrintedMode, listImageWidth, listImageHeight));
            listView.setStyle("-fx-background-color: #100317; -fx-control-inner-background: #100317;");
            view = listView;
        }

        cardsDisplayContainer.getChildren().clear();
        cardsDisplayContainer.getChildren().add(view);
        AnchorPane.setTopAnchor(view, 0.0);
        AnchorPane.setBottomAnchor(view, 0.0);
        AnchorPane.setLeftAnchor(view, 0.0);
        AnchorPane.setRightAnchor(view, 0.0);
    }


    // --- Methods for My Collection and Decks and Collections (unchanged) ---
    private void displayMyCollection() throws Exception {
        AnchorPane contentPane = myCollectionTab.getContentPane();
        contentPane.getChildren().clear();

        OwnedCardsCollection collection = new OwnedCardsCollection(UserInterfaceFunctions.filePath.getAbsolutePath());
        DataTreeItem<Object> rootItem = new DataTreeItem<>("My Cards Collection", "ROOT");
        rootItem.setExpanded(true);
        for (Box box : collection.getOwnedCollection()) {
            DataTreeItem<Object> boxItem = createBoxTreeItem(box);
            rootItem.getChildren().add(boxItem);
        }

        TreeView<String> cardTreeView = new TreeView<>(rootItem);
        cardTreeView.setCellFactory(param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        cardTreeView.setStyle("-fx-background-color: #100317;");
        cardTreeView.setShowRoot(false);
        contentPane.getChildren().add(cardTreeView);
        AnchorPane.setTopAnchor(cardTreeView, 0.0);
        AnchorPane.setBottomAnchor(cardTreeView, 0.0);
        AnchorPane.setLeftAnchor(cardTreeView, 0.0);
        AnchorPane.setRightAnchor(cardTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        cardTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());
        logger.info("My Collection displayed.");
    }

    private void populateMyCollectionMenu() throws Exception {
        VBox menuVBox = myCollectionTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();
        OwnedCardsCollection collection = new OwnedCardsCollection(UserInterfaceFunctions.filePath.getAbsolutePath());
        for (Box box : collection.getOwnedCollection()) {
            NavigationItem boxItem = createNavigationItem(box, 0);
            navigationMenu.addItem(boxItem);
        }
        menuVBox.getChildren().add(navigationMenu);
    }

    private void displayDecksAndCollections() throws Exception {
        // Retrieve the content pane from the DECKS tab (a SharedCollectionTab instance)
        AnchorPane contentPane = decksTab.getContentPane();
        contentPane.getChildren().clear();

        // Ensure the decks list is loaded. (loadDecksAndCollectionsDirectory() sets folderPath and populates the decks list.)
        if (UserInterfaceFunctions.getDecksList() == null) {
            UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
        }
        DecksAndCollectionsList decksCollection = UserInterfaceFunctions.getDecksList();
        if (decksCollection == null) {
            throw new Exception("DecksAndCollectionsList is null. Please check the decks folder path.");
        }

        // Create the root data tree item for the TreeView (same as in My Collection).
        DataTreeItem<Object> rootItem = new DataTreeItem<>("Decks and Collections", "ROOT");
        rootItem.setExpanded(true);

        // Add each Deck.
        if (decksCollection.getDecks() != null) {
            for (Deck deck : decksCollection.getDecks()) {
                DataTreeItem<Object> deckItem = createDeckTreeItem(deck);
                rootItem.getChildren().add(deckItem);
            }
        }

        // Add each ThemeCollection.
        if (decksCollection.getCollections() != null) {
            for (ThemeCollection collection : decksCollection.getCollections()) {
                DataTreeItem<Object> collItem = createThemeCollectionTreeItem(collection);
                rootItem.getChildren().add(collItem);
            }
        }

        // Create a TreeView using the common CardTreeCell (with collapse triangles, fonts, etc.)
        TreeView<String> treeView = new TreeView<>(rootItem);
        treeView.setCellFactory(param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        treeView.setStyle("-fx-background-color: #100317;");
        treeView.setShowRoot(false);

        // Add the TreeView to the content pane with proper anchoring.
        contentPane.getChildren().add(treeView);
        AnchorPane.setTopAnchor(treeView, 0.0);
        AnchorPane.setBottomAnchor(treeView, 0.0);
        AnchorPane.setLeftAnchor(treeView, 0.0);
        AnchorPane.setRightAnchor(treeView, 0.0);

        // Apply the common stylesheet.
        String stylesheetPath = "src/main/resources/styles.css";
        treeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());

        logger.info("Decks and Collections displayed using the unified layout.");
    }

    /**
     * Populates the left-hand navigation menu for the decks tab.
     * This method uses the same pattern as the My Collection tab.
     */
    private void populateDecksAndCollectionsMenu() throws Exception {
        VBox menuVBox = decksTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        // Ensure the decks list is loaded.
        if (UserInterfaceFunctions.getDecksList() == null) {
            UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
        }
        DecksAndCollectionsList decksCollection = UserInterfaceFunctions.getDecksList();

        if (decksCollection != null) {
            // Add navigation items for ThemeCollections.
            if (decksCollection.getCollections() != null) {
                for (ThemeCollection collection : decksCollection.getCollections()) {
                    NavigationItem navItem = createNavigationItem(collection, 0);
                    navigationMenu.addItem(navItem);
                    //Add subitems for the decks in the collection
                    for (Deck deck : decksCollection.getDecks()) {
                        NavigationItem subNavItem = createNavigationItem(deck, 0);
                        navigationMenu.addItem(subNavItem);
                    }
                }
            }
            // Add navigation items for Decks.
            if (decksCollection.getDecks() != null) {
                //If the deck is not in any collection, add it to the menu
                for (int i = 0; i < decksCollection.getDecks().size(); i++) {
                    //If the deck is not in any collection, add it
                    boolean deckIsInCollections = false;
                    for (int j = 0; j < decksCollection.getCollections().size(); j++) {
                        if (decksCollection.getCollections().get(j).getLinkedDecks().contains(decksCollection.getDecks().get(i))) {
                            deckIsInCollections = true;
                            break;
                        }
                    }
                    if (deckIsInCollections) {
                        //returnValue.addAll(decksCollection.getDecks().get(i).toList());
                        NavigationItem navItem = createNavigationItem(decksCollection.getDecks().get(i), 0);
                        navigationMenu.addItem(navItem);
                    }
                }

                /*for (Deck deck : decksCollection.getDecks()) {
                    NavigationItem navItem = createNavigationItem(deck, 0);
                    navigationMenu.addItem(navItem);
                }*/
            }
        } else {
            Label errorLabel = new Label("No Decks and Collections loaded.");
            errorLabel.setStyle("-fx-text-fill: white;");
            navigationMenu.addItem(new NavigationItem(errorLabel.getText(), 0));
        }

        menuVBox.getChildren().add(navigationMenu);
    }

    // --- Methods for creating tree and navigation items (unchanged) ---
    private DataTreeItem<Object> createBoxTreeItem(Box box) {
        String cleanName = box.getName().replaceAll("[=\\-]", "");
        DataTreeItem<Object> boxItem = new DataTreeItem<>(cleanName, box);
        boxItem.setExpanded(true);
        for (CardsGroup group : box.getContent()) {
            DataTreeItem<Object> groupItem = createGroupTreeItem(group);
            boxItem.getChildren().add(groupItem);
        }
        for (Box subBox : box.getSubBoxes()) {
            DataTreeItem<Object> subBoxItem = createBoxTreeItem(subBox);
            boxItem.getChildren().add(subBoxItem);
        }
        return boxItem;
    }

    private DataTreeItem<Object> createGroupTreeItem(CardsGroup group) {
        String cleanName = group.getName().replaceAll("[=\\-]", "");
        DataTreeItem<Object> groupItem = new DataTreeItem<>(cleanName, group);
        groupItem.setExpanded(true);
        return groupItem;
    }

    private DataTreeItem<Object> createDeckTreeItem(Deck deck) {
        String cleanName = deck.getName().replaceAll("[=\\-]", "");
        DataTreeItem<Object> deckItem = new DataTreeItem<>(cleanName, deck);
        deckItem.setExpanded(true);

        // Create a CardsGroup for Main Deck
        if (!deck.getMainDeck().isEmpty()) {
            // Wrap the list of CardElement into a CardsGroup.
            CardsGroup mainGroup = new CardsGroup("Main Deck", deck.getMainDeck());
            DataTreeItem<Object> mainDeckItem = new DataTreeItem<>("Main Deck", mainGroup);
            mainDeckItem.setExpanded(true);
            deckItem.getChildren().add(mainDeckItem);
        }

        // Create a CardsGroup for Extra Deck (if any)
        if (!deck.getExtraDeck().isEmpty()) {
            CardsGroup extraGroup = new CardsGroup("Extra Deck", deck.getExtraDeck());
            DataTreeItem<Object> extraDeckItem = new DataTreeItem<>("Extra Deck", extraGroup);
            extraDeckItem.setExpanded(true);
            deckItem.getChildren().add(extraDeckItem);
        }

        // Create a CardsGroup for Side Deck (if any)
        if (!deck.getSideDeck().isEmpty()) {
            CardsGroup sideGroup = new CardsGroup("Side Deck", deck.getSideDeck());
            DataTreeItem<Object> sideDeckItem = new DataTreeItem<>("Side Deck", sideGroup);
            sideDeckItem.setExpanded(true);
            deckItem.getChildren().add(sideDeckItem);
        }

        return deckItem;
    }


    private DataTreeItem<Object> createThemeCollectionTreeItem(ThemeCollection collection) {
        String cleanName = collection.getName().replaceAll("[=\\-]", "");
        DataTreeItem<Object> collectionItem = new DataTreeItem<>(cleanName, collection);
        collectionItem.setExpanded(true);

        // Create one CardsGroup for all cards in the collection.
        if (!collection.getCardsList().isEmpty()) {
            CardsGroup group = new CardsGroup("Cards", collection.getCardsList());
            DataTreeItem<Object> groupItem = new DataTreeItem<>("Cards", group);
            groupItem.setExpanded(true);
            collectionItem.getChildren().add(groupItem);
        }

        return collectionItem;
    }


    private NavigationItem createNavigationItem(Object item, int depth) {
        final String name;
        if (item instanceof Box) {
            name = ((Box) item).getName();
        } else if (item instanceof ThemeCollection) {
            name = ((ThemeCollection) item).getName();
        } else if (item instanceof Deck) {
            name = ((Deck) item).getName();
        } else if (item instanceof CardsGroup) {
            name = ((CardsGroup) item).getName();
        } else {
            name = item.toString();
        }
        final String cleanName = name.replaceAll("[=\\-]", "");
        final NavigationItem navItem = new NavigationItem(cleanName, depth);

        // For decks/collections, use the DECKS tab tree view.
        if (item instanceof Deck || item instanceof ThemeCollection) {
            navItem.setOnLabelClicked(event -> expandDeckTreeViewToItem(cleanName));
        } else {
            navItem.setOnLabelClicked(event -> expandTreeViewToItem(cleanName));
        }

        if (item instanceof Box) {
            Box box = (Box) item;
            for (Box subBox : box.getSubBoxes()) {
                NavigationItem subNavItem = createNavigationItem(subBox, depth + 1);
                navItem.addSubItem(subNavItem);
            }
            for (CardsGroup group : box.getContent()) {
                final String groupName = group.getName().replaceAll("[=\\-]", "");
                NavigationItem groupNavItem = new NavigationItem(groupName, depth + 1);
                groupNavItem.setOnLabelClicked(event -> expandTreeViewToItem(groupName));
                navItem.addSubItem(groupNavItem);
            }
        }
        return navItem;
    }

    private void expandDeckTreeViewToItem(String targetName) {
        // Retrieve the TreeView from the DECKS tab's content pane.
        if (decksTab.getContentPane().getChildren().isEmpty()) {
            return;
        }
        Node node = decksTab.getContentPane().getChildren().get(0);
        if (!(node instanceof TreeView)) {
            return;
        }
        TreeView<String> treeView = (TreeView<String>) node;
        for (TreeItem<String> item : treeView.getRoot().getChildren()) {
            if (expandIfMatch(item, targetName, treeView)) {
                break;
            }
        }
    }

    private void expandTreeViewToItem(String targetName) {
        TreeView<String> cardTreeView = (TreeView<String>) myCollectionTab.getContentPane().getChildren().get(0);
        for (TreeItem<String> item : cardTreeView.getRoot().getChildren()) {
            if (expandIfMatch(item, targetName, cardTreeView)) {
                break;
            }
        }
    }

    private boolean expandIfMatch(TreeItem<String> item, String targetName, TreeView<String> treeView) {
        if (item.getValue().equals(targetName)) {
            item.setExpanded(true);
            int index = treeView.getRow(item);
            treeView.scrollTo(index);
            treeView.getSelectionModel().select(item);
            return true;
        } else {
            for (TreeItem<String> child : item.getChildren()) {
                if (expandIfMatch(child, targetName, treeView)) {
                    item.setExpanded(true);
                    return true;
                }
            }
        }
        return false;
    }

    private void displayOuicheListUnified() throws Exception {
        AnchorPane contentPane = ouicheListTab.getContentPane();
        contentPane.getChildren().clear();

        // Retrieve the detailed ouiche list (of type DecksAndCollectionsList)
        DecksAndCollectionsList ouicheDetailed = Model.CardsLists.OuicheList.getDetailedOuicheList();
        if (ouicheDetailed == null) {
            // Trigger generation if not already loaded
            UserInterfaceFunctions.generateOuicheList();
            ouicheDetailed = Model.CardsLists.OuicheList.getDetailedOuicheList();
            if (ouicheDetailed == null) {
                throw new Exception("Failed to generate detailed OuicheList.");
            }
        }

        // Build the common tree structure using DataTreeItem and the same cell renderer.
        DataTreeItem<Object> rootItem = new DataTreeItem<>("OuicheList", "ROOT");
        rootItem.setExpanded(true);

        // Add each Deck to the tree.
        if (ouicheDetailed.getDecks() != null) {
            for (Deck deck : ouicheDetailed.getDecks()) {
                DataTreeItem<Object> deckItem = createDeckTreeItem(deck);
                rootItem.getChildren().add(deckItem);
            }
        }

        // Add each ThemeCollection to the tree.
        if (ouicheDetailed.getCollections() != null) {
            for (ThemeCollection collection : ouicheDetailed.getCollections()) {
                DataTreeItem<Object> collItem = createThemeCollectionTreeItem(collection);
                rootItem.getChildren().add(collItem);
            }
        }

        TreeView<String> treeView = new TreeView<>(rootItem);
        treeView.setCellFactory(param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        treeView.setStyle("-fx-background-color: #100317;");
        treeView.setShowRoot(false);

        contentPane.getChildren().add(treeView);
        AnchorPane.setTopAnchor(treeView, 0.0);
        AnchorPane.setBottomAnchor(treeView, 0.0);
        AnchorPane.setLeftAnchor(treeView, 0.0);
        AnchorPane.setRightAnchor(treeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        treeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());

        logger.info("OuicheList displayed using unified layout.");
    }


    private void populateOuicheListMenu() throws Exception {
        VBox menuVBox = ouicheListTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        // Retrieve the detailed ouiche list (DecksAndCollectionsList)
        DecksAndCollectionsList ouicheDetailed = Model.CardsLists.OuicheList.getDetailedOuicheList();
        if (ouicheDetailed == null) {
            // Trigger generation if needed and retrieve again.
            UserInterfaceFunctions.generateOuicheList();
            ouicheDetailed = Model.CardsLists.OuicheList.getDetailedOuicheList();
            if (ouicheDetailed == null) {
                throw new Exception("Failed to generate detailed OuicheList.");
            }
        }

        // Populate navigation items for ThemeCollections (if any)
        if (ouicheDetailed.getCollections() != null) {
            for (ThemeCollection collection : ouicheDetailed.getCollections()) {
                NavigationItem navItem = new NavigationItem(collection.getName(), 0);
                navItem.setOnLabelClicked(event ->
                        expandOuicheListTreeViewToItem(collection.getName().replaceAll("[=\\-]", ""))
                );
                navigationMenu.addItem(navItem);
            }
        }

        // Populate navigation items for Decks (if any)
        if (ouicheDetailed.getDecks() != null) {
            for (Deck deck : ouicheDetailed.getDecks()) {
                NavigationItem navItem = new NavigationItem(deck.getName(), 0);
                navItem.setOnLabelClicked(event ->
                        expandOuicheListTreeViewToItem(deck.getName().replaceAll("[=\\-]", ""))
                );
                navigationMenu.addItem(navItem);
            }
        }

        menuVBox.getChildren().add(navigationMenu);
    }


    private void expandOuicheListTreeViewToItem(String targetName) {
        if (ouicheListTab.getContentPane().getChildren().isEmpty()) {
            return;
        }
        Node node = ouicheListTab.getContentPane().getChildren().get(0);
        if (!(node instanceof TreeView)) {
            return;
        }
        TreeView<String> treeView = (TreeView<String>) node;
        for (TreeItem<String> item : treeView.getRoot().getChildren()) {
            if (expandIfMatch(item, targetName, treeView)) {
                break;
            }
        }
    }
}
