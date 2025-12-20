package Controller;

import Model.CardsLists.*;
import View.*;
import View.SharedCollectionTab.TabType;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RealMainController - updated to fix My Collection navigation wiring only.
 * <p>
 * Important: this file intentionally modifies only the My Collection menu wiring so that
 * clicking items in the My Collection navigation selects and scrolls the corresponding
 * node in the main My Collection TreeView. Decks & Collections navigation is left unchanged.
 */
public class RealMainController {

    private static final Logger logger = LoggerFactory.getLogger(RealMainController.class);

    private final DoubleProperty cardWidthProperty = new SimpleDoubleProperty(100);
    private final DoubleProperty cardHeightProperty = new SimpleDoubleProperty(146);

    @FXML
    private TabPane mainTabPane;
    @FXML
    private VBox allExistingCardsPane;
    @FXML
    private TextField nameTextField;
    @FXML
    private TextField printcodeTextField;
    @FXML
    private Button searchButton;
    private static final String ARCHETYPE_MARKER = "[ARCHETYPE]";
    @FXML
    private Button listMosaicButton;
    @FXML
    private Button printedUniqueButton;

    private SharedCollectionTab myCollectionTab;
    private SharedCollectionTab decksTab;
    private SharedCollectionTab ouicheListTab;
    private SharedCollectionTab archetypesTab;
    private SharedCollectionTab friendsTab;
    private SharedCollectionTab shopsTab;
    @FXML
    private AnchorPane cardsDisplayContainer;
    // keep references to the TreeViews so navigation items can select/scroll to nodes
    private TreeView<String> myCollectionTreeView;
    private TreeView<String> decksTreeView;
    private TreeView<String> ouicheTreeView;

    private boolean isMosaicMode = true;
    private boolean isPrintedMode = false;
    private boolean ouicheListLoaded = false;
    private TreeView<String> archetypesTreeView;

    @FXML
    private void initialize() {
        UserInterfaceFunctions.readPathsFromFile();

        try {
            Map<Integer, Card> allCards = Model.Database.Database.getAllCardsList();
            if (allCards != null && !allCards.isEmpty()) {
                SubListCreator.CreateArchetypeLists(allCards);
                logger.info("SubListCreator archetypes loaded: names={}, lists={}",
                        SubListCreator.archetypesList == null ? 0 : SubListCreator.archetypesList.size(),
                        SubListCreator.archetypesCardsLists == null ? 0 : SubListCreator.archetypesCardsLists.size());
            } else {
                logger.info("Database.getAllCardsList() returned empty or null; archetypes not initialized now.");
            }
        } catch (Exception e) {
            logger.warn("Failed to initialize SubListCreator archetypes at startup", e);
        }

        if (listMosaicButton != null) listMosaicButton.setText("List");
        if (printedUniqueButton != null) printedUniqueButton.setText("Printed");

        myCollectionTab = new SharedCollectionTab(TabType.MY_COLLECTION);
        decksTab = new SharedCollectionTab(TabType.DECKS);
        ouicheListTab = new SharedCollectionTab(TabType.OUICHE_LIST);
        archetypesTab = new SharedCollectionTab(TabType.ARCHETYPES);
        friendsTab = new SharedCollectionTab(TabType.FRIENDS);
        shopsTab = new SharedCollectionTab(TabType.SHOPS);

        setupZoom(myCollectionTab);
        setupZoom(decksTab);
        setupZoom(ouicheListTab);
        setupZoom(archetypesTab);

        if (mainTabPane != null && mainTabPane.getTabs().size() >= 6) {
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

        if (mainTabPane != null) {
            mainTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
                int selectedIndex = mainTabPane.getTabs().indexOf(newTab);
                if (selectedIndex == 1) {
                    try {
                        populateDecksAndCollectionsMenu();
                        displayDecksAndCollections();
                    } catch (Exception e) {
                        logger.error("Error displaying decks and collections", e);
                    }
                } else if (selectedIndex == 2 && !ouicheListLoaded) {
                    try {
                        UserInterfaceFunctions.generateOuicheList();
                        displayOuicheListUnified();
                        populateOuicheListMenu();
                        ouicheListLoaded = true;
                    } catch (Exception ex) {
                        logger.error("Error displaying OuicheList", ex);
                    }
                } else if (selectedIndex == 3) {
                    try {
                        displayArchetypes();
                        populateArchetypesMenu();
                    } catch (Exception ex) {
                        logger.error("Error displaying Archetypes", ex);
                    }
                }
            });
        }

        decksTab.setOnDecksLoad(() -> {
            try {
                displayDecksAndCollections();
            } catch (Exception e) {
                logger.error("Error displaying decks and collections", e);
            }
        });

        if (searchButton != null) searchButton.setOnAction(e -> updateCardsDisplay());
        if (listMosaicButton != null) listMosaicButton.setOnAction(e -> {
            isMosaicMode = !isMosaicMode;
            listMosaicButton.setText(isMosaicMode ? "List" : "Mosaic");
            updateCardsDisplay();
        });
        if (printedUniqueButton != null) printedUniqueButton.setOnAction(e -> {
            isPrintedMode = !isPrintedMode;
            printedUniqueButton.setText(isPrintedMode ? "Unique" : "Printed");
            updateCardsDisplay();
        });
        if (nameTextField != null)
            nameTextField.textProperty().addListener((obs, oldVal, newVal) -> updateCardsDisplay());
        if (printcodeTextField != null)
            printcodeTextField.textProperty().addListener((obs, oldVal, newVal) -> updateCardsDisplay());

        updateCardsDisplay();
    }

    private void setupZoom(SharedCollectionTab tab) {
        if (tab == null) return;
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

    private void updateCardsDisplay() {
        String nameFilter = (nameTextField == null ? "" : nameTextField.getText()).toLowerCase().trim();
        String codeFilter = (printcodeTextField == null ? "" : printcodeTextField.getText()).toLowerCase().trim();

        List<Card> allCards;
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

        Node view;
        double mosaicImageWidth = 100, mosaicImageHeight = 146;
        double listImageWidth = 80, listImageHeight = 116;
        if (isMosaicMode) {
            double availableWidth = (cardsDisplayContainer == null ? 375 : cardsDisplayContainer.getWidth());
            if (availableWidth <= 0) availableWidth = 375;
            double gap = 5;
            List<List<Card>> rows = groupCardsIntoRows(filteredCards, availableWidth, mosaicImageWidth, gap);
            ListView<List<Card>> mosaicListView = new ListView<>(FXCollections.observableArrayList(rows));
            mosaicListView.setCellFactory(param -> new CardsMosaicRowCell(mosaicImageWidth, mosaicImageHeight));
            mosaicListView.setStyle("-fx-background-color: #100317; -fx-control-inner-background: #100317;");
            view = mosaicListView;
        } else {
            ListView<Card> listView = new ListView<>(FXCollections.observableArrayList(filteredCards));
            listView.setCellFactory(param -> new CardsListCell(isPrintedMode, listImageWidth, listImageHeight));
            listView.setStyle("-fx-background-color: #100317; -fx-control-inner-background: #100317;");
            view = listView;
        }

        if (cardsDisplayContainer != null) {
            cardsDisplayContainer.getChildren().clear();
            cardsDisplayContainer.getChildren().add(view);
            AnchorPane.setTopAnchor(view, 0.0);
            AnchorPane.setBottomAnchor(view, 0.0);
            AnchorPane.setLeftAnchor(view, 0.0);
            AnchorPane.setRightAnchor(view, 0.0);
        }
    }

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

    // --- My Collection display ---

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

        myCollectionTreeView = new TreeView<>(rootItem);
        myCollectionTreeView.setCellFactory(param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        myCollectionTreeView.setStyle("-fx-background-color: #100317;");
        myCollectionTreeView.setShowRoot(false);
        contentPane.getChildren().add(myCollectionTreeView);
        AnchorPane.setTopAnchor(myCollectionTreeView, 0.0);
        AnchorPane.setBottomAnchor(myCollectionTreeView, 0.0);
        AnchorPane.setLeftAnchor(myCollectionTreeView, 0.0);
        AnchorPane.setRightAnchor(myCollectionTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        myCollectionTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());
        logger.info("My Collection displayed.");
    }

    /**
     * Populate the My Collection navigation menu.
     *
     * IMPORTANT: This method wires click handlers so that clicking a navigation label
     * selects and scrolls the corresponding node in the My Collection TreeView.
     *
     * The path matching used by navigateToTree(...) mirrors the tree structure created by
     * createBoxTreeItem/createGroupTreeItem so matching succeeds reliably.
     */
    private void populateMyCollectionMenu() throws Exception {
        VBox menuVBox = myCollectionTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();
        OwnedCardsCollection collection = new OwnedCardsCollection(UserInterfaceFunctions.filePath.getAbsolutePath());

        if (collection.getOwnedCollection() != null) {
            for (Box box : collection.getOwnedCollection()) {
                String boxName = box.getName() == null ? "" : box.getName().replaceAll("[=\\-]", "");
                NavigationItem boxItem = createNavigationItem(boxName, 0);

                // navigation wiring: click navigates to the box node in myCollectionTreeView
                boxItem.setOnLabelClicked(evt -> navigateToTree(myCollectionTreeView, boxName));

                // Add groups (Cards groups) under the box
                if (box.getContent() != null) {
                    for (CardsGroup group : box.getContent()) {
                        String groupName = group.getName() == null ? "" : group.getName().replaceAll("[=\\-]", "");
                        NavigationItem groupItem = createNavigationItem(groupName, 1);
                        // navigate to box -> group
                        groupItem.setOnLabelClicked(evt -> navigateToTree(myCollectionTreeView, boxName, groupName));
                        boxItem.addSubItem(groupItem);
                    }
                }

                // Add sub-boxes and their groups
                if (box.getSubBoxes() != null) {
                    for (Box subBox : box.getSubBoxes()) {
                        String subBoxName = subBox.getName() == null ? "" : subBox.getName().replaceAll("[=\\-]", "");
                        NavigationItem subBoxItem = createNavigationItem(subBoxName, 1);
                        // navigate to box -> subBox
                        subBoxItem.setOnLabelClicked(evt -> navigateToTree(myCollectionTreeView, subBoxName));
                        if (subBox.getContent() != null) {
                            for (CardsGroup g : subBox.getContent()) {
                                String gName = g.getName() == null ? "" : g.getName().replaceAll("[=\\-]", "");
                                NavigationItem gItem = createNavigationItem(gName, 2);
                                // navigate to subBox -> group
                                gItem.setOnLabelClicked(evt -> navigateToTree(myCollectionTreeView, subBoxName, gName));
                                subBoxItem.addSubItem(gItem);
                            }
                        }
                        boxItem.addSubItem(subBoxItem);
                    }
                }

                navigationMenu.addItem(boxItem);
            }
        } else {
            NavigationItem none = createNavigationItem("No boxes available", 0);
            navigationMenu.addItem(none);
        }

        menuVBox.getChildren().add(navigationMenu);
    }

    // --- Decks & Collections display (left unchanged except for keeping decksTreeView field) ---

    private void displayDecksAndCollections() throws Exception {
        AnchorPane contentPane = decksTab.getContentPane();
        contentPane.getChildren().clear();

        if (UserInterfaceFunctions.getDecksList() == null) {
            UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
        }
        DecksAndCollectionsList decksCollection = UserInterfaceFunctions.getDecksList();
        if (decksCollection == null) {
            throw new Exception("DecksAndCollectionsList is null. Please check the decks folder path.");
        }

        DataTreeItem<Object> rootItem = new DataTreeItem<>("Decks and Collections", "ROOT");
        rootItem.setExpanded(true);

        if (decksCollection.getCollections() != null) {
            for (ThemeCollection collection : decksCollection.getCollections()) {
                DataTreeItem<Object> collItem = createThemeCollectionTreeItem(collection);
                rootItem.getChildren().add(collItem);
            }
        }

        if (decksCollection.getDecks() != null) {
            for (Deck deck : decksCollection.getDecks()) {
                if (!isDeckLinked(deck, decksCollection.getCollections())) {
                    DataTreeItem<Object> deckItem = createDeckTreeItem(deck);
                    rootItem.getChildren().add(deckItem);
                }
            }
        }

        decksTreeView = new TreeView<>(rootItem);
        decksTreeView.setCellFactory(param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        decksTreeView.setStyle("-fx-background-color: #100317;");
        decksTreeView.setShowRoot(false);

        contentPane.getChildren().add(decksTreeView);
        AnchorPane.setTopAnchor(decksTreeView, 0.0);
        AnchorPane.setBottomAnchor(decksTreeView, 0.0);
        AnchorPane.setLeftAnchor(decksTreeView, 0.0);
        AnchorPane.setRightAnchor(decksTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        decksTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());

        logger.info("Decks and Collections displayed using the unified layout.");
    }

    private void populateDecksAndCollectionsMenu() throws Exception {
        VBox menuVBox = decksTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        if (UserInterfaceFunctions.getDecksList() == null) {
            UserInterfaceFunctions.loadDecksAndCollectionsDirectory();
        }
        DecksAndCollectionsList decksCollection = UserInterfaceFunctions.getDecksList();

        if (decksCollection != null) {
            if (decksCollection.getCollections() != null) {
                for (ThemeCollection collection : decksCollection.getCollections()) {
                    NavigationItem collectionNavItem = createNavigationItem(collection.getName(), 0);

                    // Determine if this collection has missing cards in its archetypes only
                    boolean hasMissing = collectionHasMissing(collection);

                    // Apply highlight only to the collection title (bold + #cdfc04) if missing
                    applyNavigationItemHighlight(collectionNavItem, hasMissing);

                    // navigation wiring: click navigates to the collection node in decksTreeView
                    collectionNavItem.setOnLabelClicked(evt -> navigateToTree(decksTreeView, collection.getName()));

                    collectionNavItem.setExpanded(false);
                    navigationMenu.addItem(collectionNavItem);

                    if (collection.getLinkedDecks() != null) {
                        for (List<Deck> unit : collection.getLinkedDecks()) {
                            if (unit == null) continue;
                            for (Deck linkedDeck : unit) {
                                if (linkedDeck == null) continue;
                                NavigationItem deckSubItem = createNavigationItem(linkedDeck.getName(), 1);
                                // deckSubItem remains default style (white, not bold)
                                deckSubItem.setOnLabelClicked(evt -> navigateToTree(decksTreeView, collection.getName(), "Decks", linkedDeck.getName()));
                                collectionNavItem.addSubItem(deckSubItem);
                            }
                        }
                    }
                }
            }

            if (decksCollection.getDecks() != null) {
                for (Deck deck : decksCollection.getDecks()) {
                    if (!isDeckLinked(deck, decksCollection.getCollections())) {
                        NavigationItem navItem = createNavigationItem(deck.getName(), 0);
                        navItem.setOnLabelClicked(evt -> navigateToTree(decksTreeView, deck.getName()));
                        navigationMenu.addItem(navItem);
                    }
                }
            }
        } else {
            Label errorLabel = new Label("No Decks and Collections loaded.");
            errorLabel.setStyle("-fx-text-fill: white;");
            navigationMenu.addItem(new NavigationItem(errorLabel.getText(), 0));
        }

        menuVBox.getChildren().add(navigationMenu);
    }

    // --- OuicheList helpers (navigation wiring preserved) ---

    private void displayOuicheListUnified() throws Exception {
        AnchorPane contentPane = ouicheListTab.getContentPane();
        contentPane.getChildren().clear();

        if (Model.CardsLists.OuicheList.getDetailedOuicheList() == null) {
            UserInterfaceFunctions.generateOuicheList();
        }
        DecksAndCollectionsList ouicheDetailed = Model.CardsLists.OuicheList.getDetailedOuicheList();
        if (ouicheDetailed == null) {
            throw new Exception("Failed to generate detailed OuicheList.");
        }

        DataTreeItem<Object> rootItem = new DataTreeItem<>("OuicheList", "ROOT");
        rootItem.setExpanded(true);

        if (ouicheDetailed.getCollections() != null) {
            for (ThemeCollection collection : ouicheDetailed.getCollections()) {
                DataTreeItem<Object> collItem = createThemeCollectionTreeItem(collection);
                rootItem.getChildren().add(collItem);
            }
        }

        if (ouicheDetailed.getDecks() != null) {
            for (Deck deck : ouicheDetailed.getDecks()) {
                if (!isDeckLinked(deck, ouicheDetailed.getCollections())) {
                    DataTreeItem<Object> deckItem = createDeckTreeItem(deck);
                    rootItem.getChildren().add(deckItem);
                }
            }
        }

        ouicheTreeView = new TreeView<>(rootItem);
        ouicheTreeView.setCellFactory(param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        ouicheTreeView.setStyle("-fx-background-color: #100317;");
        ouicheTreeView.setShowRoot(false);

        contentPane.getChildren().add(ouicheTreeView);
        AnchorPane.setTopAnchor(ouicheTreeView, 0.0);
        AnchorPane.setBottomAnchor(ouicheTreeView, 0.0);
        AnchorPane.setLeftAnchor(ouicheTreeView, 0.0);
        AnchorPane.setRightAnchor(ouicheTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        ouicheTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());
    }

    private void populateOuicheListMenu() throws Exception {
        VBox menuVBox = ouicheListTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        DecksAndCollectionsList ouicheDetailed = Model.CardsLists.OuicheList.getDetailedOuicheList();
        if (ouicheDetailed == null) {
            UserInterfaceFunctions.generateOuicheList();
            ouicheDetailed = Model.CardsLists.OuicheList.getDetailedOuicheList();
            if (ouicheDetailed == null) {
                Label errorLabel = new Label("No OuicheList available.");
                errorLabel.setStyle("-fx-text-fill: white;");
                navigationMenu.addItem(new NavigationItem(errorLabel.getText(), 0));
                menuVBox.getChildren().add(navigationMenu);
                return;
            }
        }

        if (ouicheDetailed.getCollections() != null) {
            for (ThemeCollection collection : ouicheDetailed.getCollections()) {
                NavigationItem collectionNavItem = createNavigationItem(collection.getName(), 0);

                // navigation wiring: click navigates to the collection node in ouicheTreeView
                collectionNavItem.setOnLabelClicked(evt -> navigateToTree(ouicheTreeView, collection.getName()));

                collectionNavItem.setExpanded(false);
                navigationMenu.addItem(collectionNavItem);

                if (collection.getLinkedDecks() != null) {
                    for (List<Deck> unit : collection.getLinkedDecks()) {
                        if (unit == null) continue;
                        for (Deck linkedDeck : unit) {
                            if (linkedDeck == null) continue;
                            NavigationItem deckSubItem = createNavigationItem(linkedDeck.getName(), 1);
                            // navigate to collection -> Decks -> deckName
                            deckSubItem.setOnLabelClicked(evt -> navigateToTree(ouicheTreeView, collection.getName(), "Decks", linkedDeck.getName()));
                            collectionNavItem.addSubItem(deckSubItem);
                        }
                    }
                }
            }
        }

        if (ouicheDetailed.getDecks() != null) {
            for (Deck deck : ouicheDetailed.getDecks()) {
                if (!isDeckLinked(deck, ouicheDetailed.getCollections())) {
                    NavigationItem navItem = createNavigationItem(deck.getName(), 0);
                    navItem.setOnLabelClicked(evt -> navigateToTree(ouicheTreeView, deck.getName()));
                    navigationMenu.addItem(navItem);
                }
            }
        }

        menuVBox.getChildren().add(navigationMenu);
    }

    // --- Archetypes tab: display and menu population (unchanged) ---

    private void displayArchetypes() throws Exception {
        AnchorPane contentPane = archetypesTab.getContentPane();
        contentPane.getChildren().clear();

        DataTreeItem<Object> rootItem = new DataTreeItem<>("Archetypes", "ROOT");
        rootItem.setExpanded(true);

        List<String> globalNames = SubListCreator.archetypesList;
        List<List<Card>> globalLists = SubListCreator.archetypesCardsLists;

        if (globalNames != null && globalLists != null && globalNames.size() == globalLists.size()) {
            for (int i = 0; i < globalNames.size(); i++) {
                String archetypeName = globalNames.get(i);
                if (archetypeName == null) continue;
                List<Card> cardsForArchetype = globalLists.get(i);
                List<CardElement> elements = new ArrayList<>();
                if (cardsForArchetype != null) {
                    for (Card c : cardsForArchetype) {
                        if (c != null) elements.add(new CardElement(c));
                    }
                }
                CardsGroup archetypeGroup = new CardsGroup(ARCHETYPE_MARKER + archetypeName, elements);
                Map<String, Object> data = new HashMap<>();
                data.put("group", archetypeGroup);
                data.put("missing", Collections.emptySet());
                DataTreeItem<Object> archetypeNode = new DataTreeItem<>(archetypeName, data);
                archetypeNode.setExpanded(false);
                rootItem.getChildren().add(archetypeNode);
            }
        } else {
            DataTreeItem<Object> placeholder = new DataTreeItem<>("No archetypes available", "NO_ARCHETYPES");
            placeholder.setExpanded(false);
            rootItem.getChildren().add(placeholder);
        }

        archetypesTreeView = new TreeView<>(rootItem);
        archetypesTreeView.setCellFactory(param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        archetypesTreeView.setStyle("-fx-background-color: #100317;");
        archetypesTreeView.setShowRoot(false);

        contentPane.getChildren().add(archetypesTreeView);
        AnchorPane.setTopAnchor(archetypesTreeView, 0.0);
        AnchorPane.setBottomAnchor(archetypesTreeView, 0.0);
        AnchorPane.setLeftAnchor(archetypesTreeView, 0.0);
        AnchorPane.setRightAnchor(archetypesTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        archetypesTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());

        logger.info("Archetypes displayed with {} archetype(s).", rootItem.getChildren().size());
    }

    private void populateArchetypesMenu() throws Exception {
        VBox menuVBox = archetypesTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        List<String> globalNames = SubListCreator.archetypesList;
        if (globalNames != null && !globalNames.isEmpty()) {
            for (String archetypeName : globalNames) {
                if (archetypeName == null) continue;
                NavigationItem item = createNavigationItem(archetypeName, 0);
                item.setOnLabelClicked(evt -> navigateToTree(archetypesTreeView, archetypeName));
                navigationMenu.addItem(item);
            }
        } else {
            NavigationItem none = createNavigationItem("No archetypes available", 0);
            navigationMenu.addItem(none);
        }

        menuVBox.getChildren().add(navigationMenu);
    }

    // --- Tree item builders and helpers (unchanged) ---

    private DataTreeItem<Object> createBoxTreeItem(Box box) {
        String cleanName = box.getName() == null ? "" : box.getName().replaceAll("[=\\-]", "");
        DataTreeItem<Object> boxItem = new DataTreeItem<>(cleanName, box);
        boxItem.setExpanded(true);
        if (box.getContent() != null) {
            for (CardsGroup group : box.getContent()) {
                DataTreeItem<Object> groupItem = createGroupTreeItem(group);
                boxItem.getChildren().add(groupItem);
            }
        }
        if (box.getSubBoxes() != null) {
            for (Box subBox : box.getSubBoxes()) {
                DataTreeItem<Object> subBoxItem = createBoxTreeItem(subBox);
                boxItem.getChildren().add(subBoxItem);
            }
        }
        return boxItem;
    }

    private DataTreeItem<Object> createGroupTreeItem(CardsGroup group) {
        String displayName = group.getName() == null ? "" : group.getName().replaceAll("[=\\-]", "");
        DataTreeItem<Object> groupItem = new DataTreeItem<>(displayName, group);
        groupItem.setExpanded(true);
        return groupItem;
    }

    private DataTreeItem<Object> createDeckTreeItem(Deck deck) {
        String cleanName = deck.getName() == null ? "" : deck.getName().replaceAll("[=\\-]", "");
        DataTreeItem<Object> deckItem = new DataTreeItem<>(cleanName, deck);
        deckItem.setExpanded(true);

        if (deck.getMainDeck() != null && !deck.getMainDeck().isEmpty()) {
            CardsGroup mainGroup = new CardsGroup("Main Deck", deck.getMainDeck());
            DataTreeItem<Object> mainDeckItem = new DataTreeItem<>("Main Deck", mainGroup);
            mainDeckItem.setExpanded(true);
            deckItem.getChildren().add(mainDeckItem);
        }

        if (deck.getExtraDeck() != null && !deck.getExtraDeck().isEmpty()) {
            CardsGroup extraGroup = new CardsGroup("Extra Deck", deck.getExtraDeck());
            DataTreeItem<Object> extraDeckItem = new DataTreeItem<>("Extra Deck", extraGroup);
            extraDeckItem.setExpanded(true);
            deckItem.getChildren().add(extraDeckItem);
        }

        if (deck.getSideDeck() != null && !deck.getSideDeck().isEmpty()) {
            CardsGroup sideGroup = new CardsGroup("Side Deck", deck.getSideDeck());
            DataTreeItem<Object> sideDeckItem = new DataTreeItem<>("Side Deck", sideGroup);
            sideDeckItem.setExpanded(true);
            deckItem.getChildren().add(sideDeckItem);
        }

        return deckItem;
    }

    private DataTreeItem<Object> createThemeCollectionTreeItem(ThemeCollection collection) {
        String cleanName = collection.getName() == null ? "" : collection.getName().replaceAll("[=\\-]", "");
        DataTreeItem<Object> collectionItem = new DataTreeItem<>(cleanName, collection);
        collectionItem.setExpanded(true);

        // --- Cards parsing with marker handling and linked-deck name capture ---
        List<CardElement> cleaned = new ArrayList<>();
        List<String> linkedDeckNames = new ArrayList<>();
        boolean inLinkedDecksMarker = false;

        if (collection.getCardsList() != null && !collection.getCardsList().isEmpty()) {
            for (CardElement ce : collection.getCardsList()) {
                if (ce == null) continue;

                String text = null;
                try {
                    text = ce.toString();
                    if (text != null) text = text.trim();
                } catch (Exception ignored) {
                }

                // Detect marker lines that start a linked-decks block
                if (text != null && (text.startsWith("#") || text.equalsIgnoreCase("Linked decks") || text.equalsIgnoreCase("#Linked decks"))) {
                    inLinkedDecksMarker = true;
                    continue;
                }

                if (inLinkedDecksMarker) {
                    if (text != null && !text.isEmpty()) {
                        boolean looksLikeRealCard = false;
                        try {
                            Card maybeCard = ce.getCard();
                            if (maybeCard != null) {
                                if ((maybeCard.getKonamiId() != null && !maybeCard.getKonamiId().trim().isEmpty())
                                        || (maybeCard.getPassCode() != null && !maybeCard.getPassCode().trim().isEmpty())
                                        || (maybeCard.getName_EN() != null && !maybeCard.getName_EN().trim().isEmpty())) {
                                    looksLikeRealCard = true;
                                }
                            }
                        } catch (Exception ignored) {
                        }

                        if (!looksLikeRealCard) {
                            linkedDeckNames.add(text);
                            continue;
                        }
                    }
                    // if it looks like a real card, fall through and treat as card
                }

                if (!isMarkerElement(ce)) {
                    cleaned.add(ce);
                }
            }

            if (!cleaned.isEmpty()) {
                CardsGroup group = new CardsGroup("Cards", cleaned);
                DataTreeItem<Object> groupItem = new DataTreeItem<>("Cards", group);
                groupItem.setExpanded(true);
                collectionItem.getChildren().add(groupItem);
            }
        }

        // --- Decks section: prefer explicit linkedDecks; otherwise use captured linkedDeckNames ---
        DataTreeItem<Object> decksParent = new DataTreeItem<>("Decks", "DECKS_SECTION");
        decksParent.setExpanded(true);
        boolean decksParentHasChildren = false;

        if (collection.getLinkedDecks() != null && !collection.getLinkedDecks().isEmpty()) {
            int unitIndex = 1;
            for (List<Deck> unit : collection.getLinkedDecks()) {
                if (unit == null || unit.isEmpty()) {
                    DataTreeItem<Object> emptyUnit = new DataTreeItem<>("Group " + unitIndex, "DECK_GROUP");
                    emptyUnit.setExpanded(false);
                    decksParent.getChildren().add(emptyUnit);
                    unitIndex++;
                    continue;
                }

                if (unit.size() > 1) {
                    DataTreeItem<Object> unitNode = new DataTreeItem<>("Group " + unitIndex, "DECK_GROUP");
                    unitNode.setExpanded(false);
                    for (Deck d : unit) {
                        DataTreeItem<Object> deckItem = createDeckTreeItem(d);
                        unitNode.getChildren().add(deckItem);
                    }
                    decksParent.getChildren().add(unitNode);
                } else {
                    Deck single = unit.get(0);
                    DataTreeItem<Object> deckItem = createDeckTreeItem(single);
                    decksParent.getChildren().add(deckItem);
                }
                unitIndex++;
            }
            decksParentHasChildren = !decksParent.getChildren().isEmpty();
        }

        if ((collection.getLinkedDecks() == null || collection.getLinkedDecks().isEmpty()) && !linkedDeckNames.isEmpty()) {
            for (String deckName : linkedDeckNames) {
                if (deckName == null || deckName.trim().isEmpty()) continue;
                DataTreeItem<Object> deckItem = new DataTreeItem<>(deckName, deckName);
                deckItem.setExpanded(true);
                decksParent.getChildren().add(deckItem);
            }
            decksParentHasChildren = !decksParent.getChildren().isEmpty();
        }

        if (decksParentHasChildren) {
            collectionItem.getChildren().add(decksParent);
        }

        // --- Archetypes parent (expanded by default) ---
        DataTreeItem<Object> archetypesParent = new DataTreeItem<>("Archetypes", "ARCHETYPES_SECTION");
        archetypesParent.setExpanded(true);
        boolean archetypesAdded = false;
        boolean hasArchetypesMethod = false;

        try {
            Method m = collection.getClass().getMethod("getArchetypes");
            hasArchetypesMethod = true;
            Object res = m.invoke(collection);
            if (res instanceof List) {
                List<?> archetypes = (List<?>) res;
                logger.debug("Collection '{}' getArchetypes() returned list size={}", collection.getName(), archetypes.size());

                for (Object archetypeObj : archetypes) {
                    if (archetypeObj == null) continue;
                    if (archetypeObj instanceof String) {
                        String archetypeName = ((String) archetypeObj).trim();
                        if (archetypeName.isEmpty()) continue;
                        List<CardElement> elements = buildElementsFromGlobalArchetype(archetypeName);
                        Set<String> missing = computeMissingIdsForElements(collection, elements);
                        CardsGroup archetypeGroup = new CardsGroup(ARCHETYPE_MARKER + archetypeName, elements);
                        Map<String, Object> data = new HashMap<>();
                        data.put("group", archetypeGroup);
                        data.put("missing", missing);
                        DataTreeItem<Object> archetypeNode = new DataTreeItem<>(archetypeName, data);
                        archetypeNode.setExpanded(false);
                        archetypesParent.getChildren().add(archetypeNode);
                        archetypesAdded = true;
                    } else {
                        String name = null;
                        List<CardElement> elements = new ArrayList<>();
                        try {
                            Method nameMethod = archetypeObj.getClass().getMethod("getName");
                            Object nameVal = nameMethod.invoke(archetypeObj);
                            if (nameVal != null) name = nameVal.toString();
                        } catch (Exception ignored) {
                        }
                        try {
                            Method cardsMethod = archetypeObj.getClass().getMethod("getCards");
                            Object cardsVal = cardsMethod.invoke(archetypeObj);
                            if (cardsVal instanceof List) {
                                for (Object o : (List<?>) cardsVal) {
                                    if (o instanceof CardElement) elements.add((CardElement) o);
                                    else if (o instanceof Card) elements.add(new CardElement((Card) o));
                                    else if (o instanceof String) {
                                        try {
                                            elements.add(new CardElement((String) o));
                                        } catch (Exception ignored) {
                                        }
                                    }
                                }
                            }
                        } catch (Exception ignored) {
                        }

                        if ((elements == null || elements.isEmpty()) && name != null) {
                            elements = buildElementsFromGlobalArchetype(name);
                        }

                        if (name == null) name = "Archetype";
                        Set<String> missing = computeMissingIdsForElements(collection, elements);
                        CardsGroup archetypeGroup = new CardsGroup(ARCHETYPE_MARKER + name, elements);
                        Map<String, Object> data = new HashMap<>();
                        data.put("group", archetypeGroup);
                        data.put("missing", missing);
                        DataTreeItem<Object> archetypeNode = new DataTreeItem<>(name, data);
                        archetypeNode.setExpanded(false);
                        archetypesParent.getChildren().add(archetypeNode);
                        archetypesAdded = true;
                    }
                }
            }
        } catch (NoSuchMethodException ignored) {
            hasArchetypesMethod = false;
        } catch (Exception e) {
            logger.debug("Archetypes reflection failed for collection " + collection.getName(), e);
        }

        // Only fallback to global SubListCreator archetypes when the collection does NOT provide getArchetypes()
        if (!hasArchetypesMethod && !archetypesAdded) {
            try {
                List<String> globalNames = SubListCreator.archetypesList;
                List<List<Card>> globalLists = SubListCreator.archetypesCardsLists;
                if (globalNames != null && globalLists != null && globalNames.size() == globalLists.size()) {
                    for (int i = 0; i < globalNames.size(); i++) {
                        String archetypeName = globalNames.get(i);
                        if (archetypeName == null) continue;
                        List<Card> cardsForArchetype = globalLists.get(i);
                        List<CardElement> elements = new ArrayList<>();
                        if (cardsForArchetype != null) {
                            for (Card c : cardsForArchetype) {
                                if (c != null) elements.add(new CardElement(c));
                            }
                        }
                        Set<String> missing = computeMissingIdsForElements(collection, elements);
                        CardsGroup archetypeGroup = new CardsGroup(ARCHETYPE_MARKER + archetypeName, elements);
                        Map<String, Object> data = new HashMap<>();
                        data.put("group", archetypeGroup);
                        data.put("missing", missing);
                        DataTreeItem<Object> archetypeNode = new DataTreeItem<>(archetypeName, data);
                        archetypeNode.setExpanded(false);
                        archetypesParent.getChildren().add(archetypeNode);
                        archetypesAdded = true;
                    }
                }
            } catch (Exception e) {
                logger.debug("Fallback archetypes population failed", e);
            }
        }

        if (archetypesAdded) {
            logger.info("Collection '{}' -> added {} archetype group(s)", collection.getName(), archetypesParent.getChildren().size());
            collectionItem.getChildren().add(archetypesParent);
        }

        // 4) Exceptions / Cards not to add
        try {
            Method exceptionsMethod = collection.getClass().getMethod("getExceptionsToNotAdd");
            Object exceptionsVal = exceptionsMethod.invoke(collection);
            if (exceptionsVal instanceof List) {
                List<?> exceptionsList = (List<?>) exceptionsVal;
                List<CardElement> exceptionElements = new ArrayList<>();
                for (Object o : exceptionsList) {
                    if (o instanceof CardElement) exceptionElements.add((CardElement) o);
                    else if (o instanceof Card) exceptionElements.add(new CardElement((Card) o));
                    else if (o instanceof String) {
                        try {
                            exceptionElements.add(new CardElement((String) o));
                        } catch (Exception ignored) {
                        }
                    }
                }
                if (!exceptionElements.isEmpty()) {
                    CardsGroup exceptionsGroup = new CardsGroup("Cards not to add", exceptionElements);
                    DataTreeItem<Object> exceptionsNode = new DataTreeItem<>("Cards not to add", exceptionsGroup);
                    exceptionsNode.setExpanded(true);
                    collectionItem.getChildren().add(exceptionsNode);
                }
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            logger.debug("Failed to read exceptionsToNotAdd for collection " + collection.getName(), e);
        }

        return collectionItem;
    }

    private Set<String> computeMissingIdsForElements(ThemeCollection collection, List<CardElement> elements) {
        Set<String> missing = new HashSet<>();
        if (elements == null || elements.isEmpty()) return missing;

        for (CardElement ce : elements) {
            if (ce == null) continue;
            Card c = ce.getCard();
            if (c == null) continue;

            String konami = c.getKonamiId();
            String pass = c.getPassCode();

            boolean found = false;
            if (konami != null && !konami.isEmpty()) found = isKonamiPresentInCollection(collection, konami);
            if (!found && pass != null && !pass.isEmpty()) found = isPassPresentInCollection(collection, pass);

            if (!found) {
                if (konami != null && !konami.isEmpty()) missing.add(konami);
                if (pass != null && !pass.isEmpty()) missing.add(pass);
                logger.debug("Marking as missing for collection '{}': konami='{}' pass='{}'", collection.getName(), konami, pass);
            } else {
                logger.debug("Not marking for collection '{}': konami='{}' pass='{}' (found)", collection.getName(), konami, pass);
            }
        }
        return missing;
    }

    private List<CardElement> buildElementsFromGlobalArchetype(String archetypeName) {
        List<CardElement> elements = new ArrayList<>();
        if (archetypeName == null || archetypeName.trim().isEmpty()) return elements;
        try {
            List<String> globalNames = SubListCreator.archetypesList;
            List<List<Card>> globalLists = SubListCreator.archetypesCardsLists;
            if (globalNames == null || globalLists == null) return elements;
            for (int i = 0; i < globalNames.size(); i++) {
                String globalName = globalNames.get(i);
                if (globalName == null) continue;
                if (globalName.equalsIgnoreCase(archetypeName.trim())) {
                    List<Card> cardsForArchetype = globalLists.size() > i ? globalLists.get(i) : null;
                    if (cardsForArchetype != null) {
                        for (Card c : cardsForArchetype) {
                            if (c != null) elements.add(new CardElement(c));
                        }
                    }
                    break;
                }
            }
        } catch (Exception e) {
            logger.debug("buildElementsFromGlobalArchetype failed for {}: {}", archetypeName, e.getMessage());
        }
        return elements;
    }

    private boolean isKonamiPresentInCollection(ThemeCollection collection, String konamiId) {
        if (konamiId == null || konamiId.isEmpty()) return false;
        try {
            List<CardElement> cardsList = collection.getCardsList();
            if (cardsList != null) {
                for (CardElement ce : cardsList) {
                    if (ce == null) continue;
                    Card cc = ce.getCard();
                    if (cc != null && konamiId.equals(cc.getKonamiId())) return true;
                }
            }
        } catch (Exception ignored) {
        }

        try {
            Method exceptionsMethod = collection.getClass().getMethod("getExceptionsToNotAdd");
            Object exceptionsVal = exceptionsMethod.invoke(collection);
            if (exceptionsVal instanceof List) {
                for (Object o : (List<?>) exceptionsVal) {
                    if (o instanceof CardElement) {
                        Card cc = ((CardElement) o).getCard();
                        if (cc != null && konamiId.equals(cc.getKonamiId())) return true;
                    } else if (o instanceof Card) {
                        Card cc = (Card) o;
                        if (cc != null && konamiId.equals(cc.getKonamiId())) return true;
                    }
                }
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {
        }

        try {
            List<List<Deck>> linked = collection.getLinkedDecks();
            if (linked != null) {
                for (List<Deck> unit : linked) {
                    if (unit == null) continue;
                    for (Deck d : unit) {
                        if (d == null) continue;
                        if (deckContainsKonami(d, konamiId)) return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isPassPresentInCollection(ThemeCollection collection, String passCode) {
        if (passCode == null || passCode.isEmpty()) return false;
        try {
            List<CardElement> cardsList = collection.getCardsList();
            if (cardsList != null) {
                for (CardElement ce : cardsList) {
                    if (ce == null) continue;
                    Card cc = ce.getCard();
                    if (cc != null && passCode.equals(cc.getPassCode())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            Method exceptionsMethod = collection.getClass().getMethod("getExceptionsToNotAdd");
            Object exceptionsVal = exceptionsMethod.invoke(collection);
            if (exceptionsVal instanceof List) {
                for (Object o : (List<?>) exceptionsVal) {
                    if (o instanceof CardElement) {
                        Card cc = ((CardElement) o).getCard();
                        if (cc != null && passCode.equals(cc.getPassCode())) return true;
                    } else if (o instanceof Card) {
                        Card cc = (Card) o;
                        if (cc != null && passCode.equals(cc.getPassCode())) return true;
                    } else if (o instanceof String) {
                        String s = ((String) o).trim();
                        if (!s.isEmpty() && s.equals(passCode)) return true;
                    }
                }
            }
        } catch (NoSuchMethodException ignored) {
        } catch (Exception ignored) {
        }
        try {
            List<List<Deck>> linked = collection.getLinkedDecks();
            if (linked != null) {
                for (List<Deck> unit : linked) {
                    if (unit == null) continue;
                    for (Deck d : unit) {
                        if (d == null) continue;
                        if (deckContainsPass(d, passCode)) return true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean deckContainsKonami(Deck deck, String konamiId) {
        if (deck == null || konamiId == null) return false;
        try {
            if (deck.getMainDeck() != null) {
                for (CardElement ce : deck.getMainDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && konamiId.equals(c.getKonamiId())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (deck.getExtraDeck() != null) {
                for (CardElement ce : deck.getExtraDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && konamiId.equals(c.getKonamiId())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (deck.getSideDeck() != null) {
                for (CardElement ce : deck.getSideDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && konamiId.equals(c.getKonamiId())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean deckContainsPass(Deck deck, String passCode) {
        if (deck == null || passCode == null) return false;
        try {
            if (deck.getMainDeck() != null) {
                for (CardElement ce : deck.getMainDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && passCode.equals(c.getPassCode())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (deck.getExtraDeck() != null) {
                for (CardElement ce : deck.getExtraDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && passCode.equals(c.getPassCode())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (deck.getSideDeck() != null) {
                for (CardElement ce : deck.getSideDeck()) {
                    if (ce == null) continue;
                    Card c = ce.getCard();
                    if (c != null && passCode.equals(c.getPassCode())) return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private NavigationItem createNavigationItem(String name, int depth) {
        if (name == null) name = "";
        String clean = name.replaceAll("[=\\-]", "");
        NavigationItem item = new NavigationItem(clean, depth);
        item.setOnLabelClicked(event -> {
        });
        return item;
    }

    /**
     * Determine whether a ThemeCollection has at least one missing card in its archetypes.
     * <p>
     * IMPORTANT: returns true only when the collection provides a non-empty getArchetypes() list
     * and at least one archetype yields missing cards for that collection.
     * <p>
     * Collections that do not expose getArchetypes(), or that expose it but it is empty/null,
     * will return false (no highlight).
     */
    private boolean collectionHasMissing(ThemeCollection collection) {
        if (collection == null) return false;

        boolean hasArchetypesMethod = false;
        try {
            Method m = collection.getClass().getMethod("getArchetypes");
            hasArchetypesMethod = true;
            Object res = m.invoke(collection);
            if (!(res instanceof List)) {
                // method exists but doesn't return a list -> treat as no archetypes
                return false;
            }
            List<?> archetypes = (List<?>) res;
            if (archetypes == null || archetypes.isEmpty()) {
                // collection explicitly has no archetypes -> cannot have missing archetype cards
                return false;
            }

            // For each archetype provided by the collection, compute missing IDs and return true if any missing
            for (Object archetypeObj : archetypes) {
                if (archetypeObj == null) continue;
                List<CardElement> elements = new ArrayList<>();
                if (archetypeObj instanceof String) {
                    String archetypeName = ((String) archetypeObj).trim();
                    if (archetypeName.isEmpty()) continue;
                    elements = buildElementsFromGlobalArchetype(archetypeName);
                } else {
                    String name = null;
                    try {
                        Method nameMethod = archetypeObj.getClass().getMethod("getName");
                        Object nameVal = nameMethod.invoke(archetypeObj);
                        if (nameVal != null) name = nameVal.toString();
                    } catch (Exception ignored) {
                    }
                    try {
                        Method cardsMethod = archetypeObj.getClass().getMethod("getCards");
                        Object cardsVal = cardsMethod.invoke(archetypeObj);
                        if (cardsVal instanceof List) {
                            for (Object o : (List<?>) cardsVal) {
                                if (o instanceof CardElement) elements.add((CardElement) o);
                                else if (o instanceof Card) elements.add(new CardElement((Card) o));
                                else if (o instanceof String) {
                                    try {
                                        elements.add(new CardElement((String) o));
                                    } catch (Exception ignored) {
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    if ((elements == null || elements.isEmpty()) && name != null) {
                        elements = buildElementsFromGlobalArchetype(name);
                    }
                }
                Set<String> missing = computeMissingIdsForElements(collection, elements);
                if (!missing.isEmpty()) return true;
            }
        } catch (NoSuchMethodException ignored) {
            // collection does not provide getArchetypes -> cannot have missing archetype cards
            return false;
        } catch (Exception e) {
            logger.debug("collectionHasMissing: reflection failed for collection " + (collection == null ? "null" : collection.getName()), e);
            return false;
        }

        return false;
    }

    private void applyNavigationItemHighlight(NavigationItem navItem, boolean highlight) {
        if (navItem == null) return;

        String highlightCss = "-fx-font-weight: bold; -fx-text-fill: #cdfc04;";
        String defaultCss = "-fx-font-weight: normal; -fx-text-fill: white;";

        try {
            Method getLabelMethod = null;
            try {
                getLabelMethod = navItem.getClass().getMethod("getLabel");
            } catch (NoSuchMethodException ignored) {
            }
            if (getLabelMethod != null) {
                Object labelObj = getLabelMethod.invoke(navItem);
                if (labelObj instanceof javafx.scene.control.Label) {
                    javafx.scene.control.Label label = (javafx.scene.control.Label) labelObj;
                    label.setStyle(highlight ? highlightCss : defaultCss);
                    return;
                }
            }

            try {
                Method setLabelStyle = navItem.getClass().getMethod("setLabelStyle", String.class);
                setLabelStyle.invoke(navItem, highlight ? highlightCss : defaultCss);
                return;
            } catch (NoSuchMethodException ignored) {
            }

            try {
                Field labelField = null;
                try {
                    labelField = navItem.getClass().getField("label");
                } catch (NoSuchFieldException ignored) {
                    try {
                        labelField = navItem.getClass().getField("titleLabel");
                    } catch (NoSuchFieldException ignored2) {
                        labelField = null;
                    }
                }
                if (labelField != null) {
                    Object labelObj = labelField.get(navItem);
                    if (labelObj instanceof javafx.scene.control.Label) {
                        javafx.scene.control.Label label = (javafx.scene.control.Label) labelObj;
                        label.setStyle(highlight ? highlightCss : defaultCss);
                        return;
                    }
                }
            } catch (Exception ignored) {
            }

            try {
                Field labelField = null;
                try {
                    labelField = navItem.getClass().getDeclaredField("label");
                } catch (NoSuchFieldException ignored) {
                    try {
                        labelField = navItem.getClass().getDeclaredField("titleLabel");
                    } catch (NoSuchFieldException ignored2) {
                        labelField = null;
                    }
                }
                if (labelField != null) {
                    labelField.setAccessible(true);
                    Object labelObj = labelField.get(navItem);
                    if (labelObj instanceof javafx.scene.control.Label) {
                        javafx.scene.control.Label label = (javafx.scene.control.Label) labelObj;
                        label.setStyle(highlight ? highlightCss : defaultCss);
                        return;
                    }
                }
            } catch (Exception ignored) {
            }

            try {
                Method setStyleMethod = navItem.getClass().getMethod("setStyle", String.class);
                setStyleMethod.invoke(navItem, highlight ? highlightCss : defaultCss);
                return;
            } catch (NoSuchMethodException ignored) {
            }

        } catch (Exception e) {
            logger.debug("applyNavigationItemHighlight: failed to apply highlight to NavigationItem: {}", e.getMessage());
        }
    }

    private boolean isMarkerElement(CardElement ce) {
        if (ce == null) return true;

        try {
            Card c = ce.getCard();
            if (c != null) {
                if ((c.getKonamiId() != null && !c.getKonamiId().trim().isEmpty())
                        || (c.getPassCode() != null && !c.getPassCode().trim().isEmpty())
                        || (c.getName_EN() != null && !c.getName_EN().trim().isEmpty())
                        || (c.getName_FR() != null && !c.getName_FR().trim().isEmpty())
                        || (c.getName_JA() != null && !c.getName_JA().trim().isEmpty())) {
                    return false;
                }
            }
        } catch (Exception ignored) {
        }

        String s = ce.toString();
        if (s == null) return true;
        s = s.trim();
        if (s.isEmpty()) return true;
        if (s.startsWith("#")) return true;
        if (s.equalsIgnoreCase("Linked decks")) return true;
        if (s.equalsIgnoreCase("#Linked decks")) return true;
        if (s.equalsIgnoreCase("Linked decks:")) return true;
        return false;
    }

    /**
     * Navigate to a node in the given TreeView by matching a path of node text values.
     * Example: navigateToTree(decksTreeView, "CollectionName", "Decks", "Zombie")
     */
    private void navigateToTree(TreeView<String> treeView, String... path) {
        if (treeView == null || path == null || path.length == 0) return;
        TreeItem<String> root = treeView.getRoot();
        if (root == null) return;

        TreeItem<String> found = findTreeItemByPath(root, path, 0);
        if (found != null) {
            // expand parents so the node is visible
            TreeItem<String> parent = found.getParent();
            while (parent != null) {
                parent.setExpanded(true);
                parent = parent.getParent();
            }

            // select and scroll to the row
            final TreeItem<String> toSelect = found;
            Platform.runLater(() -> {
                treeView.getSelectionModel().select(toSelect);
                int row = treeView.getRow(toSelect);
                if (row >= 0) treeView.scrollTo(row);
            });
        }
    }

    private TreeItem<String> findTreeItemByPath(TreeItem<String> node, String[] path, int index) {
        if (node == null || path == null || index >= path.length) return null;
        String nodeValue = node.getValue();
        if (nodeValue == null) nodeValue = "";

        if (!nodeValue.equals(path[index])) {
            for (TreeItem<String> child : node.getChildren()) {
                TreeItem<String> res = findTreeItemByPath(child, path, index);
                if (res != null) return res;
            }
            return null;
        }

        if (index == path.length - 1) {
            return node;
        }

        for (TreeItem<String> child : node.getChildren()) {
            TreeItem<String> res = findTreeItemByPath(child, path, index + 1);
            if (res != null) return res;
        }
        return null;
    }

    private boolean isDeckLinked(Deck deck, List<ThemeCollection> collections) {
        if (collections == null || deck == null) return false;
        String deckNorm = normalizeName(deck.getName());
        for (ThemeCollection c : collections) {
            if (c == null || c.getLinkedDecks() == null) continue;
            for (List<Deck> unit : c.getLinkedDecks()) {
                if (unit == null) continue;
                for (Deck d : unit) {
                    if (d == null) continue;
                    String otherNorm = normalizeName(d.getName());
                    if (deckNorm.equals(otherNorm)) return true;
                }
            }
        }
        return false;
    }

    private String normalizeName(String s) {
        if (s == null) return "";
        String trimmed = s.trim().toLowerCase(Locale.ROOT);
        String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        normalized = normalized.replaceAll("[^\\p{Alnum}\\s]", " ").replaceAll("\\s+", " ").trim();
        return normalized;
    }
}