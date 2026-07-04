package View;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

/**
 * FilterPaneWidgetFactory — small, stateless JavaFX control factories shared by
 * {@link FilterPane}'s column builders. Pulled out of FilterPane because these
 * carry no instance state of their own; every method here is a pure function of
 * its arguments.
 */
final class FilterPaneWidgetFactory {

    private FilterPaneWidgetFactory() {
    }

    static Text makeLabel(String text) {
        return styledText(text, false);
    }

    static Text styledText(String text, boolean bold) {
        Text label = new Text(text);
        label.setStyle("-fx-fill: white; -fx-font-size: 13;"
                + (bold ? " -fx-font-weight: bold;" : ""));
        return label;
    }

    static HBox makeFixedLabel(String text, double width) {
        Text label = makeLabel(text);
        HBox box = new HBox(label);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMinWidth(width);
        box.setPrefWidth(width);
        box.setMaxWidth(width);
        return box;
    }

    static TextField makeField(String prompt) {
        TextField textField = new TextField();
        if (prompt != null && !prompt.isEmpty()) {
            textField.setPromptText(prompt);
        }
        textField.getStyleClass().add("accent-text-field");
        textField.setPrefHeight(24);
        textField.setMaxHeight(24);
        return textField;
    }

    static TextField makeNarrowField(String prompt, double prefWidth) {
        TextField textField = makeField(prompt);
        textField.setPrefWidth(prefWidth);
        textField.setMaxWidth(prefWidth);
        return textField;
    }

    static ComboBox<String> makeCombo() {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().add("(All)");
        comboBox.setValue("(All)");
        comboBox.getStyleClass().add("accent-combo");
        comboBox.setPrefHeight(24);
        comboBox.setMaxHeight(24);
        comboBox.setPrefWidth(150);
        comboBox.setMinWidth(150);
        return comboBox;
    }

    static Button makeSmallButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("small-button");
        return button;
    }

    static Button makeCol4Button(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("col4-button");
        return button;
    }

    static HBox makeRow(javafx.scene.Node... nodes) {
        HBox row = new HBox(5);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(nodes);
        return row;
    }

    static HBox makeRightRow(javafx.scene.Node... nodes) {
        HBox row = new HBox(5);
        row.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().add(spacer);
        row.getChildren().addAll(nodes);
        return row;
    }

    static ImageView loadCornerImage(String filename, double width, double height) {
        try {
            Image image = new Image("file:./src/main/resources/" + filename);
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(width);
            imageView.setFitHeight(height);
            imageView.setPreserveRatio(true);
            return imageView;
        } catch (Exception e) {
            return new ImageView();
        }
    }

    static ImageView loadIcon(String filename, double width, double height) {
        try {
            Image image = new Image("file:./src/main/resources/" + filename);
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(width);
            imageView.setFitHeight(height);
            imageView.setPreserveRatio(true);
            return imageView;
        } catch (Exception e) {
            return null;
        }
    }

    static Image loadImage(String filename) {
        try {
            return new Image("file:./src/main/resources/" + filename);
        } catch (Exception e) {
            return null;
        }
    }
}