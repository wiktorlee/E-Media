package org.example.wavelio.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.wavelio.bus.EventBus;
import org.example.wavelio.events.ErrorEvent;
import org.example.wavelio.events.FileLoadedEvent;
import org.example.wavelio.events.MetadataParsedEvent;
import org.example.wavelio.facade.WavelioFacade;
import org.example.wavelio.model.WavMetadata;

import java.io.File;

public class HelloController {

    @FXML
    private Button openButton;

    @FXML
    private Label statusLabel;

    @FXML
    private Label sampleRateLabel;

    @FXML
    private Label channelsLabel;

    @FXML
    private Label bitsPerSampleLabel;

    @FXML
    private Label durationLabel;

    private WavelioFacade facade;

    public void setFacade(WavelioFacade facade) {
        this.facade = facade;
    }

    public void setEventBus(EventBus eventBus) {
        eventBus.subscribe(FileLoadedEvent.class, e ->
            statusLabel.setText("Wczytano: " + e.path().getFileName()));
        eventBus.subscribe(MetadataParsedEvent.class, e -> applyMetadata(e.metadata()));
        eventBus.subscribe(ErrorEvent.class, e -> {
            statusLabel.setText("Błąd: " + e.message());
            clearMetadataLabels();
        });
    }

    private void applyMetadata(WavMetadata m) {
        sampleRateLabel.setText(Integer.toString(m.sampleRate()));
        channelsLabel.setText(Integer.toString(m.numChannels()));
        bitsPerSampleLabel.setText(Integer.toString(m.bitsPerSample()));
        durationLabel.setText(String.format("%.4f", m.durationSeconds()));
    }

    private void clearMetadataLabels() {
        sampleRateLabel.setText("—");
        channelsLabel.setText("—");
        bitsPerSampleLabel.setText("—");
        durationLabel.setText("—");
    }

    @FXML
    protected void onOpenWav() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Wybierz plik WAV");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Pliki WAV", "*.wav", "*.wave"));
        Window window = openButton.getScene().getWindow();
        File file = chooser.showOpenDialog(window);
        if (file != null) {
            facade.loadFile(file.toPath());
        }
    }
}
