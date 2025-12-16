package View;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

/**
 * AllExistingCardsPane - minimal version that does not programmatically style scrollbars.
 * Adds the "accent-text-field" style class to its text fields and the "small-button"
 * class to the right-pane buttons so they keep the fixed small size on hover.
 */
public class AllExistingCardsPane extends VBox {

    private TextField nameTextField;
    private TextField printcodeTextField;
    private Button searchButton;
    private Button listMosaicButton;
    private Button printedUniqueButton;

    public AllExistingCardsPane() {
        this.setPadding(new Insets(10));
        this.setSpacing(0);
        this.getStyleClass().add("all-existing-cards-pane");
        this.setStyle("-fx-background-color: #100317;");

        VBox topSection = new VBox(5);
        topSection.setPadding(new Insets(10));

        Text nameLabel = new Text("Name :");
        nameLabel.setStyle("-fx-fill: white; -fx-font-size: 14;");
        nameTextField = new TextField();
        nameTextField.setPromptText("Enter name...");
        // accent style (these fields expand normally)
        nameTextField.getStyleClass().add("accent-text-field");

        Text printcodeLabel = new Text("Printcode :");
        printcodeLabel.setStyle("-fx-fill: white; -fx-font-size: 14;");
        printcodeTextField = new TextField();
        printcodeTextField.setPromptText("Enter printcode...");
        // accent style (these fields expand normally)
        printcodeTextField.getStyleClass().add("accent-text-field");

        searchButton = new Button("Search");
        listMosaicButton = new Button("List/Mosaic");
        printedUniqueButton = new Button("Printed/Unique");

        // Ensure these right-pane buttons use the small-button class so they keep fixed small size
        searchButton.getStyleClass().add("small-button");
        listMosaicButton.getStyleClass().add("small-button");
        printedUniqueButton.getStyleClass().add("small-button");

        topSection.getChildren().addAll(nameLabel, nameTextField, printcodeLabel, printcodeTextField, searchButton);

        HBox bottomSection = new HBox(10);
        bottomSection.setPadding(new Insets(10));
        bottomSection.setStyle("-fx-alignment: center;");
        bottomSection.getChildren().addAll(listMosaicButton, printedUniqueButton);

        Separator separator = new Separator();
        separator.setStyle("-fx-background-color: white; -fx-opacity: 0.7;");
        separator.setPrefHeight(1);

        this.getChildren().addAll(topSection, separator, bottomSection);
    }

    // Accessor methods to allow wiring of actions externally.
    public TextField getNameTextField() {
        return nameTextField;
    }

    public TextField getPrintcodeTextField() {
        return printcodeTextField;
    }

    public Button getSearchButton() {
        return searchButton;
    }

    public Button getListMosaicButton() {
        return listMosaicButton;
    }

    public Button getPrintedUniqueButton() {
        return printedUniqueButton;
    }
}
