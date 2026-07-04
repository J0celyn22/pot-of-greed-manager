package Controller;

import Model.CardsLists.*;
import View.*;
import View.SharedCollectionTab.TabType;
import javafx.beans.property.DoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * OuicheListController — manages all display and navigation logic for the
 * "OuicheList" tab.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Building the OuicheList unified TreeView (mirroring the Decks
 *       structure but filtered to missing cards).</li>
 *   <li>Populating the OuicheList navigation menu.</li>
 *   <li>Rendering the compact OuicheList view (list or mosaic layout).</li>
 *   <li>Async image loading for the compact mosaic/list views.</li>
 *   <li>Wiring the OuicheList action buttons (compact / mosaic toggle).</li>
 * </ul>
 *
 * <p>Compact view data comes from {@link OuicheList#getMaOuicheList()} and
 * {@link OuicheList#getMaOuicheListCounts()}, matching the pattern used in the
 * original {@code RealMainController.displayCompactOuicheList}.
 */
public class OuicheListController {

    private static final Logger logger = LoggerFactory.getLogger(OuicheListController.class);

    // ── Static display flag ───────────────────────────────────────────────────

    /**
     * When {@code true}, owned cards (those the user already has) are hidden
     * from the OuicheList view instead of being shown in gray.
     * Defaults to {@code false} so the existing gray-display behaviour is
     * preserved on first load.
     */
    private static boolean hideOwnedCardsEnabled = false;

    /**
     * When {@code true}, the detailed (unified-tree) OuicheList renders each card group
     * as a list of rows instead of a mosaic grid.  {@code false} means grid/mosaic mode.
     * Toggled by the Mosaic/List button when the detailed view is active.
     */
    private static boolean isDetailedListMode = false;

    /**
     * Returns {@code true} when the detailed OuicheList is in list mode (rows),
     * {@code false} for the default mosaic/grid mode.
     */
    public static boolean isDetailedListMode() {
        return isDetailedListMode;
    }

    /**
     * Returns whether owned-card hiding is currently active.
     */
    public static boolean isHideOwnedCardsEnabled() {
        return hideOwnedCardsEnabled;
    }

    /**
     * Enables or disables owned-card hiding.
     * After changing this flag the caller is responsible for triggering a
     * view refresh so that the tree cells re-render.
     *
     * @param enabled {@code true} to hide owned cards, {@code false} to show them in gray
     */
    public static void setHideOwnedCardsEnabled(boolean enabled) {
        hideOwnedCardsEnabled = enabled;
    }

    // ── Injected shared state ─────────────────────────────────────────────────

    private final DoubleProperty cardWidthProperty;
    private final DoubleProperty cardHeightProperty;
    private final SharedCollectionTab ouicheListTab;
    private final RealMainController coordinator;
    private final DecksCollectionsController decksController;

    // ── Live state ────────────────────────────────────────────────────────────

    private TreeView<String> ouicheListTreeView;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates an OuicheListController.
     *
     * @param coordinator        the thin coordinator
     * @param cardWidthProperty  shared card-width property
     * @param cardHeightProperty shared card-height property
     * @param ouicheListTab      the tab UI container for OuicheList
     * @param decksController    the DecksCollectionsController used to build collection tree items
     */
    public OuicheListController(RealMainController coordinator,
                                DoubleProperty cardWidthProperty,
                                DoubleProperty cardHeightProperty,
                                SharedCollectionTab ouicheListTab,
                                DecksCollectionsController decksController) {
        this.coordinator = coordinator;
        this.cardWidthProperty = cardWidthProperty;
        this.cardHeightProperty = cardHeightProperty;
        this.ouicheListTab = ouicheListTab;
        this.decksController = decksController;
    }

    // ── Display ───────────────────────────────────────────────────────────────

    /**
     * Builds and installs the OuicheList unified {@link TreeView} into the tab's
     * content pane.
     *
     * <p>The tree mirrors the Decks & Collections tree but only shows collections
     * that contain at least one missing card. If no cards are missing, a
     * "Nothing missing!" placeholder label is shown instead.
     *
     * @throws Exception if the DecksAndCollectionsList cannot be loaded
     */
    public void displayOuicheListUnified() throws Exception {
        AnchorPane contentPane = ouicheListTab.getContentPane();
        contentPane.getChildren().clear();

        // Use the pre-computed detailed OuicheList. After the ownership-removal pass,
        // each deck/collection's card lists contain only the cards that are still
        // missing (owned cards are either removed or flagged owned=true).
        DecksAndCollectionsList detailedOuicheList = OuicheList.getDetailedOuicheList();
        if (detailedOuicheList == null) {
            UserInterfaceFunctions.generateOuicheList();
            detailedOuicheList = OuicheList.getDetailedOuicheList();
        }
        if (detailedOuicheList == null) {
            throw new Exception(
                    "DetailedOuicheList is null. Cannot build the OuicheList view.");
        }

        DataTreeItem<Object> rootItem = new DataTreeItem<>("OuicheList", "ROOT");
        rootItem.setExpanded(true);
        boolean anyMissing = false;

        // ── Collections ────────────────────────────────────────────────────────
        if (detailedOuicheList.getCollections() != null) {
            for (ThemeCollection collection : detailedOuicheList.getCollections()) {
                if (!collectionHasMissingCards(collection)) {
                    continue;
                }
                DataTreeItem<Object> collectionItem =
                        decksController.createThemeCollectionTreeItem(
                                collection, TabType.OUICHE_LIST);
                rootItem.getChildren().add(collectionItem);
                anyMissing = true;
            }
        }

        // ── Standalone decks ───────────────────────────────────────────────────
        if (detailedOuicheList.getDecks() != null) {
            for (Deck deck : detailedOuicheList.getDecks()) {
                if (!deckHasMissingCards(deck)) {
                    continue;
                }
                DataTreeItem<Object> deckItem =
                        decksController.createDeckTreeItem(deck);
                rootItem.getChildren().add(deckItem);
                anyMissing = true;
            }
        }

        if (!anyMissing) {
            Label nothingMissingLabel = new Label("Nothing missing!");
            nothingMissingLabel.setStyle("-fx-text-fill: #cdfc04; -fx-font-size: 16px;");
            contentPane.getChildren().add(nothingMissingLabel);
            AnchorPane.setTopAnchor(nothingMissingLabel, 30.0);
            AnchorPane.setLeftAnchor(nothingMissingLabel, 20.0);
            logger.info("OuicheList: nothing missing.");
            return;
        }

        ouicheListTreeView = new TreeView<>(rootItem);
        ouicheListTreeView.setUserData("OUICHE_LIST");
        ouicheListTreeView.setCellFactory(
                param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        ouicheListTreeView.setStyle("-fx-background-color: #100317;");
        ouicheListTreeView.setShowRoot(false);
        ouicheListTreeView.addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                coordinator.buildMiddlePaneEmptySpaceFilter());

        contentPane.getChildren().add(ouicheListTreeView);
        AnchorPane.setTopAnchor(ouicheListTreeView, 0.0);
        AnchorPane.setBottomAnchor(ouicheListTreeView, 0.0);
        AnchorPane.setLeftAnchor(ouicheListTreeView, 0.0);
        AnchorPane.setRightAnchor(ouicheListTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        ouicheListTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());

        coordinator.setOuicheListTreeView(ouicheListTreeView);

        logger.info("OuicheList unified view displayed.");
    }

    // ── Navigation menu ───────────────────────────────────────────────────────

    /**
     * Rebuilds the left-hand navigation menu for the OuicheList.
     * Only collections that have at least one missing card are shown.
     *
     * @throws Exception if the model cannot be loaded
     */
    public void populateOuicheListMenu() throws Exception {
        VBox menuVBox = ouicheListTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        DecksAndCollectionsList detailedOuicheList = OuicheList.getDetailedOuicheList();
        if (detailedOuicheList == null) {
            menuVBox.getChildren().add(navigationMenu);
            return;
        }

        // ── Collections ────────────────────────────────────────────────────────
        if (detailedOuicheList.getCollections() != null) {
            for (ThemeCollection collection : detailedOuicheList.getCollections()) {
                if (!collectionHasMissingCards(collection)) {
                    continue;
                }

                NavigationItem collectionNavItem =
                        NavigationHelper.createNavigationItem(collection.getName(), 0);
                collectionNavItem.setUserData(collection);
                collectionNavItem.setItemType(NavigationItem.ItemType.COLLECTION);
                collectionNavItem.getLabel().setStyle("-fx-font-weight: bold;");
                NavigationHelper.applyNavigationItemHighlight(collectionNavItem, true,
                        "This collection has missing cards.");

                collectionNavItem.setOnLabelClicked(evt -> {
                    SelectionManager.setLastClickedNavigationItem(collection);
                    NavigationHelper.navigateToTree(ouicheListTreeView, collection.getName());
                });

                if (collection.getLinkedDecks() != null) {
                    for (List<Deck> unit : collection.getLinkedDecks()) {
                        if (unit == null) {
                            continue;
                        }
                        for (Deck deck : unit) {
                            if (deck == null || !deckHasMissingCards(deck)) {
                                continue;
                            }
                            NavigationItem deckNavItem =
                                    NavigationHelper.createNavigationItem(deck.getName(), 1);
                            deckNavItem.setUserData(deck);
                            deckNavItem.setItemType(NavigationItem.ItemType.DECK);
                            NavigationHelper.applyNavigationItemHighlight(deckNavItem, true,
                                    "This deck has missing cards.");
                            deckNavItem.setOnLabelClicked(evt -> {
                                SelectionManager.setLastClickedNavigationItem(deck);
                                NavigationHelper.navigateToTree(ouicheListTreeView,
                                        collection.getName(), "Decks", deck.getName());
                            });
                            collectionNavItem.addSubItem(deckNavItem);
                        }
                    }
                }

                navigationMenu.addItem(collectionNavItem);
            }
        }

        // ── Standalone decks ───────────────────────────────────────────────────
        if (detailedOuicheList.getDecks() != null) {
            for (Deck deck : detailedOuicheList.getDecks()) {
                if (!deckHasMissingCards(deck)) {
                    continue;
                }
                NavigationItem deckNavItem =
                        NavigationHelper.createNavigationItem(deck.getName(), 0);
                deckNavItem.setUserData(deck);
                deckNavItem.setItemType(NavigationItem.ItemType.DECK);
                NavigationHelper.applyNavigationItemHighlight(deckNavItem, true,
                        "This deck has missing cards.");
                deckNavItem.setOnLabelClicked(evt -> {
                    SelectionManager.setLastClickedNavigationItem(deck);
                    NavigationHelper.navigateToTree(ouicheListTreeView, deck.getName());
                });
                navigationMenu.addItem(deckNavItem);
            }
        }

        menuVBox.getChildren().add(navigationMenu);
    }

    // ── Compact view ──────────────────────────────────────────────────────────

    /**
     * Displays the compact OuicheList view. Data comes from the already-generated
     * {@link OuicheList#getMaOuicheList()} and {@link OuicheList#getMaOuicheListCounts()}.
     *
     * @param mosaicMode {@code true} for an image-grid, {@code false} for a card-row list
     * @throws Exception if the OuicheList data cannot be generated
     */
    public void displayCompactOuicheList(boolean mosaicMode) throws Exception {
        AnchorPane contentPane = ouicheListTab.getContentPane();
        contentPane.getChildren().clear();

        if (OuicheList.getMaOuicheList() == null) {
            UserInterfaceFunctions.generateOuicheListType();
        }

        Map<String, CardElement> missingCards = OuicheList.getMaOuicheList();
        Map<String, Integer> missingCounts = OuicheList.getMaOuicheListCounts();
        Map<String, CardElement> substandardCards = OuicheList.getMaOuicheListSubstandard();
        Map<String, Integer> substandardCounts = OuicheList.getMaOuicheListSubstandardCounts();

        // Apply the hideOwned filter to the MISSING section only.
        // OWNED_SUBSTANDARD cards always need attention (the user needs better copies)
        // so they are never hidden regardless of this setting.
        if (hideOwnedCardsEnabled && missingCards != null) {
            Map<String, CardElement> filteredCards = new java.util.LinkedHashMap<>();
            Map<String, Integer> filteredCounts =
                    (missingCounts != null) ? new java.util.LinkedHashMap<>() : null;

            for (Map.Entry<String, CardElement> entry : missingCards.entrySet()) {
                CardElement cardElement = entry.getValue();
                if (cardElement == null) {
                    continue;
                }
                boolean isOwned = cardElement.getCard() != null
                        && CardTreeCell.isCardOwnedInCollection(cardElement.getCard());
                if (!isOwned) {
                    filteredCards.put(entry.getKey(), cardElement);
                    if (filteredCounts != null && missingCounts.containsKey(entry.getKey())) {
                        filteredCounts.put(entry.getKey(), missingCounts.get(entry.getKey()));
                    }
                }
            }
            missingCards = filteredCards;
            missingCounts = filteredCounts;
        }

        // Apply the active left-pane filters (FilterPane) to both sections so the
        // compact view respects the same criteria as the detailed (unified-tree) view.
        java.util.function.Predicate<CardElement> leftFilter =
                coordinator.getCompactOuicheListElementFilter();
        if (leftFilter != null) {
            Map.Entry<Map<String, CardElement>, Map<String, Integer>> filteredMissing =
                    applyElementFilter(missingCards, missingCounts, leftFilter);
            missingCards = filteredMissing.getKey();
            missingCounts = filteredMissing.getValue();

            Map.Entry<Map<String, CardElement>, Map<String, Integer>> filteredSubstandard =
                    applyElementFilter(substandardCards, substandardCounts, leftFilter);
            substandardCards = filteredSubstandard.getKey();
            substandardCounts = filteredSubstandard.getValue();
        }

        boolean missingEmpty = missingCards == null || missingCards.isEmpty();
        boolean substandardEmpty = substandardCards == null || substandardCards.isEmpty();

        if (missingEmpty && substandardEmpty) {
            Label emptyLabel = new Label("OuicheList is empty.");
            emptyLabel.setStyle("-fx-text-fill: white;");
            contentPane.getChildren().add(emptyLabel);
            return;
        }

        VBox combinedContent = new VBox(0);
        combinedContent.setStyle("-fx-background-color: #100317;");

        // ── MISSING section ────────────────────────────────────────────────────────
        if (!missingEmpty) {
            combinedContent.getChildren().add(buildSectionHeader("Cards to acquire", false));
            combinedContent.getChildren().add(mosaicMode
                    ? buildCompactMosaicView(missingCards, false)
                    : buildCompactListView(missingCards, missingCounts, false));
        }

        // ── OWNED_SUBSTANDARD section ──────────────────────────────────────────────
        if (!substandardEmpty) {
            combinedContent.getChildren().add(
                    buildSectionHeader("Copies to upgrade (owned but below required quality)", true));
            combinedContent.getChildren().add(mosaicMode
                    ? buildCompactMosaicView(substandardCards, true)
                    : buildCompactListView(substandardCards, substandardCounts, true));
        }

        ScrollPane scrollPane = new ScrollPane(combinedContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #100317; -fx-background: #100317;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        contentPane.getChildren().add(scrollPane);
        AnchorPane.setTopAnchor(scrollPane, 0.0);
        AnchorPane.setBottomAnchor(scrollPane, 0.0);
        AnchorPane.setLeftAnchor(scrollPane, 0.0);
        AnchorPane.setRightAnchor(scrollPane, 0.0);
    }

    /**
     * Builds a styled section-header label for the compact view.
     *
     * @param title         the header text
     * @param isSubstandard {@code true} for orange styling (substandard section),
     *                      {@code false} for the default white styling (missing section)
     */
    private Label buildSectionHeader(String title, boolean isSubstandard) {
        Label header = new Label(title);
        String color = isSubstandard ? "#EB9E34" : "white";
        header.setStyle(
                "-fx-text-fill: " + color + "; "
                        + "-fx-font-size: 15; "
                        + "-fx-font-weight: bold; "
                        + "-fx-padding: 12 10 8 10; "
                        + "-fx-border-color: transparent transparent " + color + " transparent; "
                        + "-fx-border-width: 0 0 1 0;");
        header.setMaxWidth(Double.MAX_VALUE);
        return header;
    }

    /**
     * Builds the compact list view for the MISSING section.
     * Delegates to {@link #buildCompactListView(Map, Map, boolean)}.
     */
    public Node buildCompactListView(Map<String, CardElement> uniqueCards,
                                     Map<String, Integer> cardCounts) {
        return buildCompactListView(uniqueCards, cardCounts, false);
    }

    /**
     * Builds the compact list view with optional substandard styling.
     *
     * <p>When {@code isSubstandard} is {@code true}:
     * <ul>
     *   <li>Row borders are orange instead of white.</li>
     *   <li>The count label is orange.</li>
     *   <li>If the representative {@link CardElement} carries a condition or
     *       rarity requirement, those are shown below the price in orange so
     *       the user knows exactly what quality they need to acquire.</li>
     * </ul>
     */
    public Node buildCompactListView(Map<String, CardElement> uniqueCards,
                                     Map<String, Integer> cardCounts,
                                     boolean isSubstandard) {
        final double imageWidth = 80.0;
        final double imageHeight = 116.0;
        final String rowBorderColor = isSubstandard ? "#EB9E34" : "white";

        VBox listBox = new VBox(6);
        listBox.setPadding(new Insets(10));
        listBox.setStyle("-fx-background-color: #100317;");

        for (Map.Entry<String, CardElement> entry : uniqueCards.entrySet()) {
            CardElement cardElement = entry.getValue();
            Card card = cardElement.getCard();
            int count = cardCounts != null && cardCounts.containsKey(entry.getKey())
                    ? cardCounts.get(entry.getKey()) : 1;

            HBox row = new HBox(10);
            row.setPadding(new Insets(8));
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle(
                    "-fx-border-color: " + rowBorderColor + "; -fx-border-width: 1; "
                            + "-fx-border-radius: 5; -fx-background-radius: 5; "
                            + "-fx-background-color: black;");

            // ── Card image (left) ──────────────────────────────────────────
            ImageView imageView = new ImageView();
            imageView.setFitWidth(imageWidth);
            imageView.setFitHeight(imageHeight);
            imageView.setPreserveRatio(true);
            loadCardImageInto(card, imageView, imageWidth, imageHeight);

            // ── Names (centre, fills remaining space) ──────────────────────
            VBox namesBox = new VBox(4);
            HBox.setHgrow(namesBox, Priority.ALWAYS);
            namesBox.setAlignment(Pos.CENTER_LEFT);

            Label frLabel = new Label(card.getName_FR() != null ? card.getName_FR() : "");
            Label enLabel = new Label(card.getName_EN() != null ? card.getName_EN() : "");
            Label jaLabel = new Label(card.getName_JA() != null ? card.getName_JA() : "");
            frLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
            enLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
            jaLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13;");
            frLabel.setWrapText(true);
            enLabel.setWrapText(true);
            jaLabel.setWrapText(true);
            namesBox.getChildren().addAll(frLabel, enLabel, jaLabel);

            // ── Count + price (right, top-aligned) ────────────────────────
            VBox valueBox = new VBox(4);
            valueBox.setAlignment(Pos.TOP_RIGHT);

            Label countLabel = new Label("\u00d7" + count);
            countLabel.setStyle("-fx-text-fill: " + rowBorderColor
                    + "; -fx-font-size: 14; -fx-font-weight: bold;");
            valueBox.getChildren().add(countLabel);

            if (card.getPrice() != null && !card.getPrice().trim().isEmpty()) {
                try {
                    float unitPrice = Float.parseFloat(card.getPrice().trim());
                    float totalPrice = unitPrice * count;
                    Label unitPriceLabel = new Label(String.format("%.2f\u20ac", unitPrice));
                    Label totalPriceLabel = new Label(String.format("= %.2f\u20ac", totalPrice));
                    unitPriceLabel.setStyle(
                            "-fx-text-fill: white; -fx-font-size: 11; -fx-font-weight: bold;");
                    totalPriceLabel.setStyle(
                            "-fx-text-fill: white; -fx-font-size: 11; -fx-font-weight: bold;");
                    valueBox.getChildren().addAll(unitPriceLabel, totalPriceLabel);
                } catch (NumberFormatException ignored) {
                }
            }

            // ── Quality requirement (substandard section only, below price) ─
            if (isSubstandard) {
                // Always show condition: OWNED_SUBSTANDARD cards failed the quality
                // check, so the effective minimum (GOOD when unset) is always relevant.
                Model.CardsLists.CardCondition condition = cardElement.getEffectiveCondition();
                Model.CardsLists.CardRarity rarity = cardElement.getRarity();

                Label condLabel = new Label("Req. condition: " + condition.getDisplayName());
                condLabel.setStyle("-fx-text-fill: #EB9E34; -fx-font-size: 11;");
                valueBox.getChildren().add(condLabel);

                if (rarity != null) {
                    Label rarLabel = new Label("Req. rarity: " + rarity.getDisplayName());
                    rarLabel.setStyle("-fx-text-fill: #EB9E34; -fx-font-size: 11;");
                    valueBox.getChildren().add(rarLabel);
                }
            }

            row.getChildren().addAll(imageView, namesBox, valueBox);
            listBox.getChildren().add(row);
        }

        return listBox;
    }

    /**
     * Builds the compact mosaic view for the MISSING section.
     * Delegates to {@link #buildCompactMosaicView(Map, boolean)}.
     */
    public Node buildCompactMosaicView(Map<String, CardElement> uniqueCards) {
        return buildCompactMosaicView(uniqueCards, false);
    }

    /**
     * Builds the compact mosaic view with optional substandard styling.
     *
     * <p>When {@code isSubstandard} is {@code true}, each image wrapper receives
     * an orange border to signal that the card needs a quality upgrade.
     */
    public Node buildCompactMosaicView(Map<String, CardElement> uniqueCards,
                                       boolean isSubstandard) {
        double cellWidth = cardWidthProperty.get();
        double cellHeight = cardHeightProperty.get();

        FlowPane flow = new FlowPane();
        flow.setHgap(5);
        flow.setVgap(5);
        flow.setPadding(new Insets(10));
        flow.setStyle("-fx-background-color: #100317;");

        for (Map.Entry<String, CardElement> entry : uniqueCards.entrySet()) {
            Card card = entry.getValue().getCard();

            ImageView imageView = new ImageView();
            imageView.setFitWidth(cellWidth);
            imageView.setFitHeight(cellHeight);
            imageView.setPreserveRatio(true);
            loadCardImageInto(card, imageView, cellWidth, cellHeight);

            StackPane wrapper = new StackPane(imageView);
            wrapper.setPadding(new Insets(2));
            if (isSubstandard) {
                wrapper.setStyle(
                        "-fx-border-color: #EB9E34; "
                                + "-fx-border-width: 2; "
                                + "-fx-border-radius: 3;");
            }
            flow.getChildren().add(wrapper);
        }

        return flow;
    }

    /**
     * Loads a card image asynchronously into the given {@link ImageView}, using
     * {@link Utils.LruImageCache} and {@code DataBaseUpdate.getAddresses} exactly
     * like the other cell renderers in the application.
     *
     * @param card      the card whose image to load
     * @param imageView the ImageView to populate once the image is ready
     * @param fitWidth  the target fit-width passed to the {@link Image} constructor
     * @param fitHeight the target fit-height passed to the {@link Image} constructor
     */
    public void loadCardImageInto(Card card, ImageView imageView,
                                  double fitWidth, double fitHeight) {
        if (card == null || card.getImagePath() == null) {
            return;
        }

        String imageKey = card.getImagePath();
        String[] addresses =
                Model.Database.DataBaseUpdate.getAddresses(imageKey + ".jpg");
        if (addresses == null || addresses.length == 0) {
            return;
        }

        final String resolvedPath = "file:" + addresses[0];

        // Fast path: already cached on the FX thread.
        Image cached = Utils.LruImageCache.getImage(resolvedPath);
        if (cached != null) {
            imageView.setImage(cached);
            return;
        }

        // Background load — never block the FX thread.
        Thread loaderThread = new Thread(() -> {
            try {
                Image loadedImage =
                        new Image(resolvedPath, fitWidth, fitHeight, true, true);
                Utils.LruImageCache.addImage(resolvedPath, loadedImage);
                javafx.application.Platform.runLater(() -> imageView.setImage(loadedImage));
            } catch (Exception exception) {
                logger.debug("loadCardImageInto: failed to load image for {}",
                        resolvedPath, exception);
            }
        }, "compact-ouiche-img-loader");
        loaderThread.setDaemon(true);
        loaderThread.start();
    }

    // ── OuicheList action buttons ─────────────────────────────────────────────

    /**
     * Wires the two toggle buttons that appear in the OuicheList tab header:
     * <ul>
     *   <li><b>Compact / Detailed</b> — toggles between compact (list) and unified tree.</li>
     *   <li><b>Mosaic / List</b> — visible only in compact mode; switches between image
     *       grid and card-row list.</li>
     * </ul>
     * The button instances are retrieved via
     * {@link SharedCollectionTab#getCompactDetailedButton()} and
     * {@link SharedCollectionTab#getMosaicListButton()}.
     */
    public void setupOuicheListButtons() {
        Button compactDetailedButton = ouicheListTab.getCompactDetailedButton();
        Button mosaicListButton = ouicheListTab.getMosaicListButton();
        Button hideOwnedCardsButton = ouicheListTab.getHideOwnedCardsButton();

        if (compactDetailedButton == null || mosaicListButton == null) {
            return;
        }

        compactDetailedButton.setOnAction(event -> {
            if ("Compact mode".equals(compactDetailedButton.getText())) {
                // ── Switch to Compact mode ─────────────────────────────────
                compactDetailedButton.setText("Detailed mode");
                mosaicListButton.setVisible(true);
                mosaicListButton.setManaged(true);
                mosaicListButton.setText("Mosaic");   // currently in list; offers mosaic
                try {
                    displayCompactOuicheList(false);
                } catch (Exception exception) {
                    logger.error("Error displaying compact OuicheList", exception);
                }
            } else {
                // ── Switch back to Detailed mode ───────────────────────────
                compactDetailedButton.setText("Compact mode");
                isDetailedListMode = false;           // default to mosaic/grid
                mosaicListButton.setVisible(true);    // visible in detailed mode too
                mosaicListButton.setManaged(true);
                mosaicListButton.setText("List");     // currently in mosaic; offers list
                try {
                    displayOuicheListUnified();
                } catch (Exception exception) {
                    logger.error("Error displaying detailed OuicheList", exception);
                }
            }
        });

        mosaicListButton.setOnAction(event -> {
            boolean isCompactMode = "Detailed mode".equals(compactDetailedButton.getText());
            if (isCompactMode) {
                // ── Compact mode: toggle compact list ↔ compact mosaic ─────
                if ("Mosaic".equals(mosaicListButton.getText())) {
                    mosaicListButton.setText("List");
                    try {
                        displayCompactOuicheList(true);
                    } catch (Exception exception) {
                        logger.error("Error switching compact OuicheList to mosaic", exception);
                    }
                } else {
                    mosaicListButton.setText("Mosaic");
                    try {
                        displayCompactOuicheList(false);
                    } catch (Exception exception) {
                        logger.error("Error switching compact OuicheList to list", exception);
                    }
                }
            } else {
                // ── Detailed mode: toggle detailed mosaic ↔ detailed list ──
                if ("List".equals(mosaicListButton.getText())) {
                    // Currently in mosaic; switch to list
                    mosaicListButton.setText("Mosaic");
                    isDetailedListMode = true;
                    try {
                        displayOuicheListUnified();
                    } catch (Exception exception) {
                        logger.error("Error switching detailed OuicheList to list mode", exception);
                    }
                } else {
                    // Currently in list; switch to mosaic
                    mosaicListButton.setText("List");
                    isDetailedListMode = false;
                    try {
                        displayOuicheListUnified();
                    } catch (Exception exception) {
                        logger.error("Error switching detailed OuicheList to mosaic mode", exception);
                    }
                }
            }
        });

        // ── Hide / Show owned cards toggle ────────────────────────────────────
        if (hideOwnedCardsButton != null) {
            hideOwnedCardsButton.setOnAction(event -> {
                boolean nowHiding = !hideOwnedCardsEnabled;
                hideOwnedCardsEnabled = nowHiding;

                if (nowHiding) {
                    // Active state: yellow-green background, black text
                    hideOwnedCardsButton.setText("Show owned cards");
                    hideOwnedCardsButton.setStyle(
                            "-fx-background-color: #cdfc04;"
                                    + "-fx-text-fill: black;"
                                    + "-fx-border-color: #cdfc04;"
                                    + "-fx-border-width: 1;"
                                    + "-fx-border-radius: 4;"
                                    + "-fx-background-radius: 4;"
                                    + "-fx-font-size: 12px;"
                                    + "-fx-padding: 4 10 4 10;"
                                    + "-fx-cursor: hand;");
                } else {
                    // Default state: dark background, yellow-green text/border
                    hideOwnedCardsButton.setText("Hide owned cards");
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
                }

                // Refresh whichever view is currently showing.
                boolean isCompactMode = "Detailed mode".equals(compactDetailedButton.getText());
                if (isCompactMode) {
                    boolean isMosaicMode = "List".equals(mosaicListButton.getText());
                    try {
                        displayCompactOuicheList(isMosaicMode);
                    } catch (Exception exception) {
                        logger.error("Error refreshing compact OuicheList after hide-owned toggle",
                                exception);
                    }
                } else {
                    // For the unified tree view, re-applying the FilteredList predicate is
                    // sufficient: owned cards are removed from (or restored to) every live
                    // GridView without rebuilding the whole tree.
                    CardGroupRegistry.applyHideOwnedToAllGroups();
                }
            });
        }
    }

    /**
     * Filters a compact-view {@link CardElement} map (and its parallel counts map, if
     * any) using {@code predicate}, preserving iteration order.
     *
     * @param cards     the source map of unique-card-key to {@link CardElement}, may be {@code null}
     * @param counts    the parallel map of unique-card-key to copy count, may be {@code null}
     * @param predicate the predicate each retained {@link CardElement} must satisfy
     * @return an entry pairing the filtered cards map with the filtered counts map
     * (the counts map is {@code null} when {@code counts} was {@code null})
     */
    private Map.Entry<Map<String, CardElement>, Map<String, Integer>> applyElementFilter(
            Map<String, CardElement> cards,
            Map<String, Integer> counts,
            java.util.function.Predicate<CardElement> predicate) {

        if (cards == null) {
            return new java.util.AbstractMap.SimpleEntry<>(
                    new java.util.LinkedHashMap<>(), new java.util.LinkedHashMap<>());
        }

        Map<String, CardElement> filteredCards = new java.util.LinkedHashMap<>();
        Map<String, Integer> filteredCounts =
                (counts != null) ? new java.util.LinkedHashMap<>() : null;

        for (Map.Entry<String, CardElement> entry : cards.entrySet()) {
            CardElement cardElement = entry.getValue();
            if (cardElement == null || !predicate.test(cardElement)) {
                continue;
            }
            filteredCards.put(entry.getKey(), cardElement);
            if (filteredCounts != null && counts.containsKey(entry.getKey())) {
                filteredCounts.put(entry.getKey(), counts.get(entry.getKey()));
            }
        }

        return new java.util.AbstractMap.SimpleEntry<>(filteredCards, filteredCounts);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns {@code true} when the collection has at least one card element that
     * is not yet owned, across all its linked deck sections and its own cards list.
     *
     * <p>Operates on the <em>detailedOuicheList</em> copy produced by
     * {@link OuicheList#createDetailedOuicheList}, where owned cards have already
     * been removed from the lists (or flagged {@code owned=true}).
     */
    private boolean collectionHasMissingCards(ThemeCollection collection) {
        if (collection == null) {
            return false;
        }
        if (collection.getLinkedDecks() != null) {
            for (List<Deck> unit : collection.getLinkedDecks()) {
                if (unit == null) {
                    continue;
                }
                for (Deck deck : unit) {
                    if (deckHasMissingCards(deck)) {
                        return true;
                    }
                }
            }
        }
        if (collection.getCardsList() != null) {
            for (CardElement cardElement : collection.getCardsList()) {
                if (cardElement.getOwnershipStatus() != OwnershipStatus.OWNED) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} when the deck has at least one card element that is not
     * yet owned, across its main, extra, and side deck lists.
     *
     * <p>Same contract as {@link #collectionHasMissingCards}: operates on the
     * post-ownership-removal copy inside {@code detailedOuicheList}.
     */
    private boolean deckHasMissingCards(Deck deck) {
        if (deck == null) {
            return false;
        }
        for (List<CardElement> section : java.util.Arrays.asList(
                deck.getMainDeck(), deck.getExtraDeck(), deck.getSideDeck())) {
            if (section == null) {
                continue;
            }
            for (CardElement cardElement : section) {
                if (cardElement.getOwnershipStatus() != OwnershipStatus.OWNED) {
                    return true;
                }
            }
        }
        return false;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /**
     * Returns the currently displayed OuicheList TreeView (may be null).
     */
    public TreeView<String> getOuicheListTreeView() {
        return ouicheListTreeView;
    }
}