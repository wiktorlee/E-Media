module org.example.wavelio {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires java.sql;
    requires org.slf4j;
    requires org.slf4j.simple;

    opens org.example.wavelio to javafx.fxml;
    opens org.example.wavelio.controllers to javafx.fxml;
    exports org.example.wavelio;
    exports org.example.wavelio.bus;
    exports org.example.wavelio.events;
    exports org.example.wavelio.facade;
    exports org.example.wavelio.model;
    exports org.example.wavelio.service;
}