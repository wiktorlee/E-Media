module org.example.wavelio {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;


    opens org.example.wavelio to javafx.fxml;
    opens org.example.wavelio.controllers to javafx.fxml;
    exports org.example.wavelio;
}