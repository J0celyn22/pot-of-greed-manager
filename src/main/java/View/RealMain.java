package View;

import Model.Database.DataBaseUpdate;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class RealMain extends Application {

    public static void main(String[] args) {
        DataBaseUpdate.updateCache();
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        primaryStage.setTitle("Pot of Greed Manager");

        Image iconImage;
        try {
            iconImage = new Image(new FileInputStream("./src/main/resources/WokOfGreedSpirit.jpg"));
        } catch (FileNotFoundException e) {
            iconImage = new Image("/resources/WokOfGreedSpirit.jpg");
        }

        primaryStage.getIcons().add(iconImage);

        // Load the FXML file using an instance of FXMLLoader
        String fxmlPath = System.getProperty("user.dir") + "/src/main/resources/main_layout.fxml";
        FileInputStream fxmlStream = new FileInputStream(new File(fxmlPath));
        FXMLLoader loader = new FXMLLoader();
        HBox root = loader.load(fxmlStream);

        Scene scene = new Scene(root);
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
