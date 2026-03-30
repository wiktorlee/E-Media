package org.example.wavelio.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.wavelio.bus.EventBus;
import org.example.wavelio.events.ErrorEvent;
import org.example.wavelio.events.FFTProgressEvent;
import org.example.wavelio.events.FFTResultEvent;
import org.example.wavelio.events.FileLoadedEvent;
import org.example.wavelio.events.MetadataParsedEvent;
import org.example.wavelio.facade.WavelioFacade;
import org.example.wavelio.model.FftAnalysisResult;
import org.example.wavelio.model.WavMetadata;

import java.io.File;

public class HelloController {

    @FXML
    private Button openButton;

    @FXML
    private Button runFftButton;

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

    @FXML
    private Label peakHzLabel;

    @FXML
    private Label peakDbLabel;

    @FXML
    private ProgressBar fftProgressBar;

    @FXML
    private Label fftProgressLabel;

    private WavelioFacade facade;

    public void setFacade(WavelioFacade facade) {
        this.facade = facade;
    }

    public void setEventBus(EventBus eventBus) {
        eventBus.subscribe(FileLoadedEvent.class, e ->
            statusLabel.setText("Wczytano: " + e.path().getFileName()));
        eventBus.subscribe(MetadataParsedEvent.class, e -> applyMetadata(e.metadata()));
        eventBus.subscribe(FFTProgressEvent.class, e -> applyFftProgress(e.percent()));
        eventBus.subscribe(FFTResultEvent.class, e -> applyFftResult(e.spectrumData()));
        eventBus.subscribe(ErrorEvent.class, e -> {
            statusLabel.setText("Błąd: " + e.message());
            clearMetadataLabels();
            clearFftLabels();
            runFftButton.setDisable(false);
            fftProgressBar.setProgress(0.0);
            fftProgressLabel.setText("0%");
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

    private void applyFftResult(FftAnalysisResult result) {
        if (result.channels().isEmpty() || result.frequencyAxisHz().length == 0) {
            clearFftLabels();
            return;
        }
        double[] db = result.channels().get(0).magnitudesDb();
        double[] hz = result.frequencyAxisHz();
        int peak = 0;
        for (int i = 1; i < Math.min(db.length, hz.length); i++) {
            if (db[i] > db[peak]) {
                peak = i;
            }
        }
        peakHzLabel.setText(String.format("%.2f", hz[peak]));
        peakDbLabel.setText(String.format("%.2f", db[peak]));
        statusLabel.setText("FFT zakończone");
        runFftButton.setDisable(false);
    }

    private void clearFftLabels() {
        peakHzLabel.setText("—");
        peakDbLabel.setText("—");
    }

    private void applyFftProgress(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        fftProgressBar.setProgress(clamped / 100.0);
        fftProgressLabel.setText(clamped + "%");
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
        runFftButton.setDisable(false);
        fftProgressBar.setProgress(0.0);
        fftProgressLabel.setText("0%");
    }

    @FXML
    protected void onRunFft() {
        statusLabel.setText("FFT w toku...");
        runFftButton.setDisable(true);
        fftProgressBar.setProgress(0.0);
        fftProgressLabel.setText("0%");
        facade.runFFT();
    }
}
