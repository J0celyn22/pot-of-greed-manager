package View;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class RealMain extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        primaryStage.setTitle("Pot of Greed Manager");
        Image iconImage;
        try {
            iconImage = new Image(new FileInputStream("./src/main/resources/WokOfGreedSpirit.jpg"));
        } catch (IOException e) {
            iconImage = new Image("file:./src/main/resources/WokOfGreedSpirit.jpg");
        }
        primaryStage.getIcons().add(iconImage);

        FXMLLoader loader = new FXMLLoader(new File("src/main/resources/main_layout.fxml").toURI().toURL());
        Parent root = loader.load();

        // Get the controller instance from the loader
        final Controller.RealMainController controller = loader.getController();

        Scene scene = new Scene(root);

        // Attach stylesheet (keep this)
        try {
            String cssPath = new File("src/main/resources/styles.css").toURI().toURL().toExternalForm();
            scene.getStylesheets().add(cssPath);
            System.out.println("Loaded stylesheet: " + cssPath);
        } catch (Exception ex) {
            System.out.println("Could not load stylesheet: " + ex);
        }

        primaryStage.setScene(scene);

        // Ensure controller.dispose() runs when the window is closed (call instance method)
        primaryStage.setOnCloseRequest(event -> {
            try {
                if (controller != null) controller.dispose();
            } catch (Throwable t) {
                // log if you have a logger available
            }
        });

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        primaryStage.setX(screenBounds.getMinX());
        primaryStage.setY(screenBounds.getMinY());
        primaryStage.setWidth(screenBounds.getWidth());
        primaryStage.setHeight(screenBounds.getHeight());

        primaryStage.show();

        // Force one CSS/layout pass after show so skins pick up stylesheet rules
        Platform.runLater(() -> {
            try {
                scene.getRoot().applyCss();
                scene.getRoot().layout();
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        try {
            CardTreeCell.shutdownImageLoadingExecutor();
        } catch (Exception ignored) {
        }
    }
}
