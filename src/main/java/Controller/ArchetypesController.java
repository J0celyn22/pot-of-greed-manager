package Controller;

import Model.CardsLists.Card;
import Model.CardsLists.CardElement;
import Model.CardsLists.CardsGroup;
import View.*;
import javafx.beans.property.DoubleProperty;
import javafx.scene.control.Label;
import javafx.scene.control.TreeView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

/**
 * ArchetypesController — manages all display and navigation logic for the
 * "Archetypes" tab.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Building the read-only Archetypes {@link TreeView} from the global
 *       {@code SubListCreator.getArchetypesList()} / {@code archetypesCardsLists}.</li>
 *   <li>Populating the left-hand navigation menu with archetype entries.</li>
 *   <li>Building the flat list of {@link CardElement}s for a named archetype
 *       (shared with {@link DecksCollectionsController} via delegation).</li>
 * </ul>
 *
 * <p>The Archetypes tab is read-only: no drag-and-drop, no renames, and no
 * dirty tracking are performed here.
 */
public class ArchetypesController {

    /**
     * Sentinel prepended to CardsGroup names that represent archetype groups.
     */
    public static final String ARCHETYPE_MARKER = "[ARCHETYPE]";
    private static final Logger logger = LoggerFactory.getLogger(ArchetypesController.class);

    // ── Injected shared state ─────────────────────────────────────────────────
    private final DoubleProperty cardWidthProperty;
    private final DoubleProperty cardHeightProperty;
    private final SharedCollectionTab archetypesTab;
    private final RealMainController coordinator;
    private final DecksCollectionsController decksController;

    // ── Live tree view ────────────────────────────────────────────────────────

    private TreeView<String> archetypesTreeView;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates an ArchetypesController.
     *
     * @param coordinator        the thin coordinator
     * @param cardWidthProperty  shared card-width property
     * @param cardHeightProperty shared card-height property
     * @param archetypesTab      the tab UI container for Archetypes
     * @param decksController    used to delegate {@code buildElementsFromGlobalArchetype}
     */
    public ArchetypesController(RealMainController coordinator,
                                DoubleProperty cardWidthProperty,
                                DoubleProperty cardHeightProperty,
                                SharedCollectionTab archetypesTab,
                                DecksCollectionsController decksController) {
        this.coordinator = coordinator;
        this.cardWidthProperty = cardWidthProperty;
        this.cardHeightProperty = cardHeightProperty;
        this.archetypesTab = archetypesTab;
        this.decksController = decksController;
    }

    // ── Display ───────────────────────────────────────────────────────────────

    /**
     * Builds and installs the read-only Archetypes {@link TreeView} into the tab's
     * content pane.
     *
     * <p>Each archetype becomes a tree item whose value is a {@link Map} containing
     * {@code "group"} (the {@link CardsGroup} with an {@link #ARCHETYPE_MARKER}-prefixed
     * name) and {@code "missing"} (always an empty set — the Archetypes tab is
     * purely read-only with no missing-card colouring).
     *
     * <p>If neither global list is available, a placeholder item is shown instead.
     */
    public void displayArchetypes() {
        AnchorPane contentPane = archetypesTab.getContentPane();
        contentPane.getChildren().clear();

        List<String> archetypeNames;
        List<List<Card>> archetypeCardLists;
        try {
            archetypeNames = Model.CardsLists.SubListCreator.getArchetypesList();
            archetypeCardLists = Model.CardsLists.SubListCreator.getArchetypesCardsLists();
        } catch (Throwable throwable) {
            logger.warn("displayArchetypes: could not read SubListCreator static fields",
                    throwable);
            archetypeNames = null;
            archetypeCardLists = null;
        }

        DataTreeItem<Object> rootItem = new DataTreeItem<>("Archetypes", "ROOT");
        rootItem.setExpanded(true);

        if (archetypeNames != null && archetypeCardLists != null
                && archetypeNames.size() == archetypeCardLists.size()) {

            for (int index = 0; index < archetypeNames.size(); index++) {
                String archetypeName = archetypeNames.get(index);
                if (archetypeName == null) {
                    continue;
                }
                List<Card> cardsForArchetype = archetypeCardLists.get(index);
                List<CardElement> elements = new ArrayList<>();
                if (cardsForArchetype != null) {
                    for (Card card : cardsForArchetype) {
                        if (card != null) {
                            elements.add(new CardElement(card));
                        }
                    }
                }
                CardsGroup archetypeGroup =
                        new CardsGroup(ARCHETYPE_MARKER + archetypeName, elements);
                Map<String, Object> data = new HashMap<>();
                data.put("group", archetypeGroup);
                data.put("missing", Collections.emptySet());
                DataTreeItem<Object> archetypeNode =
                        new DataTreeItem<>(archetypeName, data);
                archetypeNode.setExpanded(false);
                rootItem.getChildren().add(archetypeNode);
            }
        } else {
            DataTreeItem<Object> placeholder =
                    new DataTreeItem<>("No archetypes available", "NO_ARCHETYPES");
            placeholder.setExpanded(false);
            rootItem.getChildren().add(placeholder);
        }

        archetypesTreeView = new TreeView<>(rootItem);
        archetypesTreeView.setCellFactory(
                param -> new CardTreeCell(cardWidthProperty, cardHeightProperty));
        archetypesTreeView.setStyle("-fx-background-color: #100317;");
        archetypesTreeView.setShowRoot(false);
        archetypesTreeView.addEventFilter(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                coordinator.buildMiddlePaneEmptySpaceFilter());

        contentPane.getChildren().add(archetypesTreeView);
        AnchorPane.setTopAnchor(archetypesTreeView, 0.0);
        AnchorPane.setBottomAnchor(archetypesTreeView, 0.0);
        AnchorPane.setLeftAnchor(archetypesTreeView, 0.0);
        AnchorPane.setRightAnchor(archetypesTreeView, 0.0);

        String stylesheetPath = "src/main/resources/styles.css";
        archetypesTreeView.getStylesheets().add(new File(stylesheetPath).toURI().toString());

        coordinator.setArchetypesTreeView(archetypesTreeView);

        logger.info("Archetypes displayed with {} archetype(s).",
                rootItem.getChildren().size());
    }

    // ── Navigation menu ───────────────────────────────────────────────────────

    /**
     * Rebuilds the left-hand navigation menu for the Archetypes tab.
     *
     * <p>Each archetype name is rendered as a flat (depth-0) nav item that scrolls
     * the tree to the corresponding archetype group on click.
     *
     * @throws Exception if the menu VBox cannot be accessed
     */
    public void populateArchetypesMenu() throws Exception {
        VBox menuVBox = archetypesTab.getMenuVBox();
        menuVBox.getChildren().clear();
        NavigationMenu navigationMenu = new NavigationMenu();

        List<String> archetypeNames;
        try {
            archetypeNames = Model.CardsLists.SubListCreator.getArchetypesList();
        } catch (Throwable throwable) {
            logger.warn("populateArchetypesMenu: could not read SubListCreator.getArchetypesList()",
                    throwable);
            archetypeNames = null;
        }

        if (archetypeNames == null || archetypeNames.isEmpty()) {
            Label emptyLabel = new Label("No archetypes.");
            emptyLabel.setStyle("-fx-text-fill: white;");
            menuVBox.getChildren().add(emptyLabel);
            return;
        }

        for (String archetypeName : archetypeNames) {
            if (archetypeName == null || archetypeName.isBlank()) {
                continue;
            }

            NavigationItem navItem =
                    NavigationHelper.createNavigationItem(archetypeName, 0);
            navItem.setItemType(NavigationItem.ItemType.CATEGORY);

            final String finalArchetypeName = archetypeName;
            navItem.setOnLabelClicked(evt -> {
                SelectionManager.setLastClickedNavigationItem(finalArchetypeName);
                NavigationHelper.navigateToTree(archetypesTreeView, finalArchetypeName);
            });

            navigationMenu.addItem(navItem);
        }

        menuVBox.getChildren().add(navigationMenu);
    }

    // ── Archetype element builder ─────────────────────────────────────────────

    /**
     * Builds a list of {@link CardElement}s for the named archetype from the global
     * SubListCreator data, delegating to {@link DecksCollectionsController}.
     *
     * @param archetypeName the archetype name to look up (case-insensitive)
     * @return the list of elements; empty if the archetype is not found
     */
    public List<CardElement> buildElementsFromGlobalArchetype(String archetypeName) {
        return decksController.buildElementsFromGlobalArchetype(archetypeName);
    }

    // ── Accessor ──────────────────────────────────────────────────────────────

    /**
     * Returns the currently displayed Archetypes TreeView (may be null).
     */
    public TreeView<String> getArchetypesTreeView() {
        return archetypesTreeView;
    }
}