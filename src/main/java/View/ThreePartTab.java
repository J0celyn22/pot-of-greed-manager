package View;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Separator;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class ThreePartTab extends HBox {

    private VBox leftPane;
    private VBox rightPane;
    private AnchorPane topRightPane;
    private AnchorPane bottomRightPane;

    public ThreePartTab() {
        this.setSpacing(0);

        leftPane = new VBox();
        leftPane.getStyleClass().add("threepart-left-pane"); // added style class
        leftPane.setStyle("-fx-background-color: #100317;");
        leftPane.setPrefWidth(375);

        Separator sep = new Separator();
        sep.setOrientation(Orientation.VERTICAL);
        sep.setStyle("-fx-background-color: white;");
        sep.setPrefWidth(2);

        rightPane = new VBox();
        rightPane.getStyleClass().add("threepart-right-pane"); // added style class
        rightPane.setSpacing(0);
        rightPane.setStyle("-fx-background-color: #100317;");
        rightPane.setPrefWidth(375);

        topRightPane = new AnchorPane();
        topRightPane.setStyle("-fx-background-color: #100317; -fx-text-fill: white;");
        VBox.setVgrow(topRightPane, Priority.ALWAYS);

        Separator sepHoriz = new Separator();
        sepHoriz.setStyle("-fx-background-color: white;");
        sepHoriz.setPrefHeight(2);

        bottomRightPane = new AnchorPane();
        bottomRightPane.setStyle("-fx-background-color: #100317; -fx-text-fill: white;");
        VBox.setVgrow(bottomRightPane, Priority.ALWAYS);

        rightPane.getChildren().addAll(topRightPane, sepHoriz, bottomRightPane);

        this.getChildren().addAll(leftPane, sep, rightPane);
    }

    public VBox getLeftPane() {
        return leftPane;
    }

    public VBox getRightPane() {
        return rightPane;
    }

    public AnchorPane getTopRightPane() {
        return topRightPane;
    }

    public AnchorPane getBottomRightPane() {
        return bottomRightPane;
    }

    public void setTopRightContent(Node node) {
        topRightPane.getChildren().clear();
        topRightPane.getChildren().add(node);
    }

    public void setBottomRightContent(Node node) {
        bottomRightPane.getChildren().clear();
        bottomRightPane.getChildren().add(node);
    }
}
