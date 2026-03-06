module org.example.wavelio {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.wavelio to javafx.fxml;
    exports org.example.wavelio;
}