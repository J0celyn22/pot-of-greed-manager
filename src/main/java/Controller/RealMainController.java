// RealMainController.java
package Controller;

import Model.CardsLists.Box;
import Model.CardsLists.CardsGroup;
import Model.CardsLists.OwnedCardsCollection;
import View.CardTreeCell;
import View.DataTreeItem;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
import java.nio.file.Paths;

public class RealMainController {

    @FXML
    private VBox leftPaneContainer;

    @FXML
    private ScrollPane leftPaneScroll;

    @FXML
    private VBox leftPane;

    @FXML
    private VBox middlePane;

    @FXML
    private VBox rightPane;

    @FXML
    private TextField collectionFileField;

    @FXML
    private Button collectionFileLoadButton;

    private OwnedCardsCollection myCardsCollection;

    private TreeView<String> cardTreeView;

    // For multi-selection tracking
    private int lastSelectedIndex = -1;

    @FXML
    private void initialize() {

        middlePane.setStyle("-fx-background-color: #100317; -fx-text-fill: white;");

        collectionFileLoadButton.setOnAction(event -> {
            try {
                loadCollection();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Ensure scroll pane for left pane
        leftPaneScroll.setContent(leftPane);
        leftPane.setStyle("-fx-background-color: #100317; -fx-text-fill: white;");

        Platform.runLater(() -> {
            // Future UI setup can be done here
        });
    }

    private void loadCollection() throws Exception {
        String filePath = collectionFileField.getText();
        myCardsCollection = new OwnedCardsCollection(filePath);
        populateMiddlePane();
        populateLeftPane(); // If you wish to keep the left pane
    }

    private void populateMiddlePane() {
        middlePane.getChildren().clear();

        // Build the root item
        DataTreeItem<Object> rootItem = new DataTreeItem<>("My Card Collection", null);
        rootItem.setExpanded(true); // Expand root by default

        // Build the tree structure
        for (Box box : myCardsCollection.getOwnedCollection()) {
            DataTreeItem<Object> boxItem = createBoxTreeItem(box);
            rootItem.getChildren().add(boxItem);
        }

        // Create the TreeView and set the custom cell factory
        cardTreeView = new TreeView<>(rootItem);
        cardTreeView.setCellFactory(param -> new CardTreeCell());

        // Enable multiple selection
        cardTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Set the background style
        cardTreeView.setStyle("-fx-background-color: #100317;");

        // Handle mouse clicks for selection
        cardTreeView.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            handleMouseClick(event);
        });

        // Add the TreeView to the middle pane
        VBox.setVgrow(cardTreeView, Priority.ALWAYS);
        middlePane.getChildren().add(cardTreeView);

        // Ensure middlePane resizes correctly
        VBox.setVgrow(middlePane, Priority.ALWAYS);

        // Load stylesheet using direct file path
        Path stylesheetPath = Paths.get("./src/main/resources/styles.css");
        cardTreeView.getStylesheets().add(stylesheetPath.toUri().toString());
    }

    private DataTreeItem<Object> createBoxTreeItem(Box box) {
        String boxName = box.getName().replaceAll("[=\\-]", "");
        DataTreeItem<Object> boxItem = new DataTreeItem<>(boxName, box);
        boxItem.setExpanded(true); // Expand boxes by default

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
        String groupName = group.getName().replaceAll("[=\\-]", "");
        DataTreeItem<Object> groupItem = new DataTreeItem<>(groupName, group);
        groupItem.setExpanded(true); // Expand groups by default

        return groupItem;
    }

    // Handle mouse clicks for multi-selection
    private void handleMouseClick(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            TreeItem<String> selectedItem = cardTreeView.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                int currentIndex = cardTreeView.getRow(selectedItem);

                if (event.isShiftDown() && lastSelectedIndex >= 0) {
                    // Select range between lastSelectedIndex and currentIndex
                    int start = Math.min(lastSelectedIndex, currentIndex);
                    int end = Math.max(lastSelectedIndex, currentIndex);

                    cardTreeView.getSelectionModel().clearSelection();
                    for (int i = start; i <= end; i++) {
                        cardTreeView.getSelectionModel().select(i);
                    }
                } else if (event.isControlDown()) {
                    // Toggle selection
                    if (cardTreeView.getSelectionModel().isSelected(currentIndex)) {
                        cardTreeView.getSelectionModel().clearSelection(currentIndex);
                    } else {
                        cardTreeView.getSelectionModel().select(currentIndex);
                    }
                    lastSelectedIndex = currentIndex;
                } else {
                    // Single selection
                    cardTreeView.getSelectionModel().clearSelection();
                    cardTreeView.getSelectionModel().select(currentIndex);
                    lastSelectedIndex = currentIndex;
                }
            }
        }
    }

    private void populateLeftPane() {
        // Implement navigation interactions here
        leftPane.getChildren().clear();
        for (Box box : myCardsCollection.getOwnedCollection()) {
            createArborescenceNode(box, leftPane, 0);
        }
    }

    private void createArborescenceNode(Box box, VBox parent, int indent) {
        VBox boxNode = new VBox();
        boxNode.setStyle("-fx-background-color: #100317; -fx-text-fill: white;");
        Label boxLabel = new Label(box.getName().replaceAll("[=\\-]", ""));
        boxLabel.setStyle("-fx-text-fill: white;");

        boxNode.getChildren().add(boxLabel);
        VBox innerNode = new VBox();
        innerNode.setManaged(true);
        innerNode.setVisible(true);
        boxNode.getChildren().add(innerNode);
        boxNode.setStyle("-fx-padding: 0 0 0 " + indent + ";");

        for (CardsGroup group : box.getContent()) {
            Label groupLabel = new Label(group.getName().replaceAll("[=\\-]", ""));
            groupLabel.setStyle("-fx-padding: 0 0 0 " + (indent + 20) + "; -fx-text-fill: white;");
            innerNode.getChildren().add(groupLabel);

            groupLabel.setOnMouseClicked(event -> {
                expandTreeViewToItem(group.getName().replaceAll("[=\\-]", ""));
            });
        }

        for (Box subBox : box.getSubBoxes()) {
            createArborescenceNode(subBox, innerNode, indent + 20);
        }

        parent.getChildren().add(boxNode);

        // Set up click event to expand corresponding TreeView item
        boxLabel.setOnMouseClicked(event -> {
            expandTreeViewToItem(box.getName().replaceAll("[=\\-]", ""));
        });
    }

    private void expandTreeViewToItem(String targetName) {
        // Traverse the TreeView and expand to the target item
        for (TreeItem<String> item : cardTreeView.getRoot().getChildren()) {
            if (expandIfMatch(item, targetName)) {
                break;
            }
        }
    }

    private boolean expandIfMatch(TreeItem<String> item, String targetName) {
        if (item.getValue().equals(targetName)) {
            item.setExpanded(true);
            int index = cardTreeView.getRow(item);
            cardTreeView.scrollTo(index);
            return true;
        } else {
            for (TreeItem<String> child : item.getChildren()) {
                if (expandIfMatch(child, targetName)) {
                    item.setExpanded(true);
                    return true;
                }
            }
        }
        return false;
    }

    // Clean up resources when the application stops
    public void stop() {
        // Shutdown any executors if needed
    }
}
