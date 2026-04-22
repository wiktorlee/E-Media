package org.example.wavelio;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.wavelio.bus.EventBus;
import org.example.wavelio.controllers.HelloController;
import org.example.wavelio.facade.WavelioFacade;
import org.example.wavelio.facade.WavelioFacadeImpl;

import java.io.IOException;

public class HelloApplication extends Application {

    private EventBus eventBus;
    private WavelioFacade facade;

    @Override
    public void start(Stage stage) throws IOException {
        eventBus = new EventBus();
        facade = WavelioFacadeImpl.create(eventBus);

        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1280, 760);
        HelloController controller = fxmlLoader.getController();
        controller.setFacade(facade);
        controller.setEventBus(eventBus);

        stage.initStyle(StageStyle.DECORATED);
        stage.setResizable(true);
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        stage.setTitle("Wavelio");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}