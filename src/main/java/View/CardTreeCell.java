// CardTreeCell.java
package View;

import Model.CardsLists.CardElement;
import Model.CardsLists.CardsGroup;
import Model.Database.DataBaseUpdate;
import Utils.ImageCache;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CardTreeCell extends TreeCell<String> {

    // Use the shared executor or create one
    private static final ExecutorService imageLoadExecutor = Executors.newFixedThreadPool(4);
    private Label label;
    private FlowPane flowPane;
    private VBox vBox;
    private List<ImageView> selectedCards = new ArrayList<>();

    public CardTreeCell() {
        // Initialize components
        label = new Label();

        // Apply style to the TreeCell
        setStyle("-fx-background-color: #100317;");

        // Update visuals when item changes
        itemProperty().addListener((obs, oldItem, newItem) -> {
            updateItem(newItem, false);
        });
    }

    @Override
    protected void updateItem(String itemName, boolean empty) {
        super.updateItem(itemName, empty);

        if (empty || itemName == null) {
            setText(null);
            setGraphic(null);
            setStyle("-fx-background-color: #100317;");
        } else {
            TreeItem<String> treeItem = getTreeItem();
            if (treeItem instanceof DataTreeItem) {
                Object dataObject = ((DataTreeItem<?>) treeItem).getData();

                if (dataObject instanceof CardsGroup) {
                    // Display CardsGroup and its cards
                    CardsGroup group = (CardsGroup) dataObject;

                    vBox = new VBox();
                    vBox.setStyle("-fx-background-color: #100317;"); // Ensure background color

                    label.setText(itemName);
                    label.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-background-color: transparent;");
                    vBox.getChildren().add(label);

                    flowPane = new FlowPane();
                    flowPane.setHgap(10);
                    flowPane.setVgap(10);
                    flowPane.setPrefWrapLength(600); // Adjust based on your window size
                    flowPane.setStyle("-fx-background-color: #100317; -fx-padding: 10;");

                    for (CardElement cardElement : group.getCardList()) {
                        ImageView cardImageView = createCardImageView(cardElement);
                        flowPane.getChildren().add(cardImageView);
                    }

                    // Ensure the FlowPane grows to fit content
                    flowPane.setPrefHeight(Region.USE_COMPUTED_SIZE);
                    flowPane.setMinHeight(Region.USE_PREF_SIZE);
                    VBox.setVgrow(flowPane, Priority.ALWAYS);

                    vBox.getChildren().add(flowPane);
                    VBox.setVgrow(vBox, Priority.ALWAYS);

                    setGraphic(vBox);
                    setText(null);

                    // Expand/Collapse on click
                    label.setOnMouseClicked(event -> {
                        if (flowPane.isVisible()) {
                            flowPane.setVisible(false);
                            flowPane.setManaged(false);
                        } else {
                            flowPane.setVisible(true);
                            flowPane.setManaged(true);
                        }
                    });

                } else {
                    // Display Box or other items
                    label.setText(itemName);
                    label.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
                    setText(null);
                    setGraphic(label);
                }
            } else {
                // For root or other TreeItems without data
                label.setText(itemName);
                label.setStyle("-fx-text-fill: white;");
                setText(null);
                setGraphic(label);
            }
        }
    }

    private ImageView createCardImageView(CardElement cardElement) {
        ImageView cardImage = new ImageView();
        cardImage.setFitWidth(100); // Adjust as needed
        cardImage.setFitHeight(146);
        cardImage.setUserData(cardElement);

        // Load the image asynchronously
        String imagePath = getImagePath(cardElement);
        if (imagePath != null) {
            if (ImageCache.containsImage(imagePath)) {
                // Image is already cached
                cardImage.setImage(ImageCache.getImage(imagePath));
            } else {
                // Load image in background
                Task<Image> loadImageTask = new Task<>() {
                    @Override
                    protected Image call() {
                        return new Image(imagePath);
                    }
                };

                loadImageTask.setOnSucceeded(event -> {
                    Image image = loadImageTask.getValue();
                    cardImage.setImage(image);
                    ImageCache.addImage(imagePath, image);
                });

                loadImageTask.setOnFailed(event -> {
                    Throwable exception = loadImageTask.getException();
                    System.err.println("Failed to load image: " + exception.getMessage());
                    Platform.runLater(() -> cardImage.setImage(getPlaceholderImage()));
                });

                imageLoadExecutor.submit(loadImageTask);
            }
        }

        // Handle selection
        cardImage.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                handleCardSelection(event, cardImage);
                event.consume(); // Consume the event to prevent it from reaching the parent TreeCell
            }
        });

        return cardImage;
    }

    private void handleCardSelection(javafx.scene.input.MouseEvent event, ImageView cardImage) {
        // Multi-selection logic
        if (event.isControlDown()) {
            toggleCardSelection(cardImage);
        } else if (event.isShiftDown()) {
            selectRange(cardImage);
        } else {
            clearSelection();
            selectCard(cardImage);
        }
    }

    private void toggleCardSelection(ImageView cardImage) {
        if (selectedCards.contains(cardImage)) {
            selectedCards.remove(cardImage);
            cardImage.setStyle(null);
        } else {
            selectedCards.add(cardImage);
            cardImage.setStyle("-fx-effect: dropshadow(gaussian, blue, 10, 0, 0, 0);");
        }
    }

    private void selectRange(ImageView cardImage) {
        // Implement range selection logic if needed
    }

    private void selectCard(ImageView cardImage) {
        selectedCards.add(cardImage);
        cardImage.setStyle("-fx-effect: dropshadow(gaussian, blue, 10, 0, 0, 0);");
    }

    private void clearSelection() {
        for (ImageView card : selectedCards) {
            card.setStyle(null);
        }
        selectedCards.clear();
    }

    private String getImagePath(CardElement cardElement) {
        String[] addresses = DataBaseUpdate.getAddresses(cardElement.getCard().getImagePath() + ".jpg");
        if (addresses.length > 0) {
            return "file:" + addresses[0];
        }
        return null;
    }

    private Image getPlaceholderImage() {
        // Return a default placeholder image if available
        return null;
    }

    // Clean up resources when the application stops
    public void dispose() {
        imageLoadExecutor.shutdown();
    }
}
