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
import java.io.IOException;
import java.net.URL;

public class RealMain extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Resolves a resource path, trying the classpath first (works in JAR),
     * then falling back to a relative file path (works in IntelliJ dev runs).
     */
    private URL resolveResource(String classpathPath, String fallbackFilePath) {
        URL url = getClass().getResource(classpathPath);
        if (url != null) return url;
        try {
            File f = new File(fallbackFilePath);
            if (f.exists()) return f.toURI().toURL();
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        primaryStage.setTitle("Pot of Greed Manager");

        // Load icon
        URL iconUrl = resolveResource("/WokOfGreedSpirit.jpg", "src/main/resources/WokOfGreedSpirit.jpg");
        if (iconUrl != null) {
            primaryStage.getIcons().add(new Image(iconUrl.toExternalForm()));
        }

        // Load FXML
        URL fxmlUrl = resolveResource("/main_layout.fxml", "src/main/resources/main_layout.fxml");
        if (fxmlUrl == null) {
            throw new IOException("Cannot find main_layout.fxml (tried classpath and filesystem)");
        }
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        final Controller.RealMainController controller = loader.getController();

        Scene scene = new Scene(root);

        // Load CSS
        URL cssUrl = resolveResource("/styles.css", "src/main/resources/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
            System.out.println("Loaded stylesheet: " + cssUrl.toExternalForm());
        } else {
            System.out.println("Could not find styles.css");
        }

        primaryStage.setScene(scene);

        primaryStage.setOnCloseRequest(event -> {
            try {
                if (controller != null) controller.dispose();
            } catch (Throwable t) {
            }
        });

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        primaryStage.setX(screenBounds.getMinX());
        primaryStage.setY(screenBounds.getMinY());
        primaryStage.setWidth(screenBounds.getWidth());
        primaryStage.setHeight(screenBounds.getHeight());

        primaryStage.show();

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