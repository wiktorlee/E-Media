package org.example.wavelio.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import org.example.wavelio.bus.EventBus;
import org.example.wavelio.events.ErrorEvent;
import org.example.wavelio.events.FileLoadedEvent;
import org.example.wavelio.facade.WavelioFacade;

public class HelloController {
    @FXML
    private Label welcomeText;

    private WavelioFacade facade;

    public void setFacade(WavelioFacade facade) {
        this.facade = facade;
    }

    public void setEventBus(EventBus eventBus) {
        eventBus.subscribe(FileLoadedEvent.class, e -> welcomeText.setText("Loaded: " + e.path().getFileName()));
        eventBus.subscribe(ErrorEvent.class, e -> welcomeText.setText("Error: " + e.message()));
    }

    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText("Welcome to JavaFX Application!");
    }
}
