module PotOfGreedManager {
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.media;
    requires javafx.base;
    requires javafx.fxml;
    requires org.json;
    requires org.jsoup;
    requires java.desktop;

    exports Model.CardsLists to javafx.graphics, javafx.fxml;
    exports Model.FormatList to javafx.graphics, javafx.fxml;
    exports Model.Database to javafx.graphics, javafx.fxml;
    exports Model.Database.CardInfo to javafx.graphics, javafx.fxml;
    exports Model.UltraJeux to javafx.graphics, javafx.fxml;
    exports View to javafx.graphics, javafx.fxml;
    exports Controller to javafx.graphics, javafx.fxml;
    opens Main to javafx.graphics, javafx.fxml;
    opens Controller to javafx.fxml;
}
