package org.example.wavelio.controllers;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.wavelio.bus.EventBus;
import org.example.wavelio.events.ErrorEvent;
import org.example.wavelio.events.FFTProgressEvent;
import org.example.wavelio.events.FFTResultEvent;
import org.example.wavelio.events.FileSavedEvent;
import org.example.wavelio.events.FileLoadedEvent;
import org.example.wavelio.events.InfoMetadataParsedEvent;
import org.example.wavelio.events.MetadataParsedEvent;
import org.example.wavelio.events.WaveformReadyEvent;
import org.example.wavelio.facade.WavelioFacade;
import org.example.wavelio.facade.WavelioFacadeImpl;
import org.example.wavelio.model.FftAnalysisResult;
import org.example.wavelio.model.InfoMetadata;
import org.example.wavelio.model.WavMetadata;
import org.example.wavelio.ui.WaveformCanvas;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public class HelloController {

    @FXML
    private Button openButton;

    @FXML
    private Button runFftButton;

    @FXML
    private Button cropButton;

    @FXML
    private Button anonymizeButton;

    @FXML
    private Button saveButton;

    @FXML
    private Button saveAsButton;

    @FXML
    private Label statusLabel;

    @FXML
    private Label fileNameLabel;

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

    @FXML
    private ListView<String> statusListView;

    @FXML
    private WaveformCanvas waveformCanvas;

    @FXML
    private TextField infoInamField;

    @FXML
    private TextField infoIartField;

    @FXML
    private TextField infoIcmtField;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private WavelioFacade facade;
    private Path currentPath;
    private double currentDurationSeconds;
    private WaveformCanvas.OptionalSelection currentSelection = WaveformCanvas.OptionalSelection.empty();
    private Optional<InfoMetadata> currentInfo = Optional.empty();

    public void setFacade(WavelioFacade facade) {
        this.facade = facade;
    }

    public void setEventBus(EventBus eventBus) {
        eventBus.subscribe(FileLoadedEvent.class, e -> {
            statusLabel.setText("Wczytano: " + e.path().getFileName());
            fileNameLabel.setText(e.path().getFileName().toString());
            appendStatus("Wczytano plik: " + e.path().getFileName());
            runFftButton.setDisable(false);
            cropButton.setDisable(true);
            anonymizeButton.setDisable(false);
            saveButton.setDisable(false);
            saveAsButton.setDisable(false);
            currentPath = e.path();
        });
        eventBus.subscribe(MetadataParsedEvent.class, e -> {
            applyMetadata(e.metadata());
            appendStatus("Metadane WAV gotowe");
        });
        eventBus.subscribe(InfoMetadataParsedEvent.class, e -> {
            applyInfo(e.info());
            appendStatus("Metadane INFO gotowe");
        });
        eventBus.subscribe(WaveformReadyEvent.class, e -> {
            applyWaveform(e.channels());
            if (e.channels() != null && e.channels().length > 0 && e.channels()[0].length > 0) {
                appendStatus("Waveform gotowy");
            }
        });
        eventBus.subscribe(FFTProgressEvent.class, e -> applyFftProgress(e.percent()));
        eventBus.subscribe(FFTResultEvent.class, e -> {
            applyFftResult(e.spectrumData());
            appendStatus("FFT zakończone");
        });
        eventBus.subscribe(FileSavedEvent.class, e -> {
            statusLabel.setText("Zapisano: " + e.path().getFileName());
            appendStatus("Zapisano plik: " + e.path().getFileName());
        });
        eventBus.subscribe(ErrorEvent.class, e -> {
            statusLabel.setText("Błąd: " + e.message());
            appendStatus("Błąd: " + e.message());
            runFftButton.setDisable(false);
        });

        runFftButton.setDisable(true);
        waveformCanvas.setChannels(new double[0][0]);
        waveformCanvas.setSelectionListener(sel -> {
            currentSelection = sel;
            cropButton.setDisable(!(sel.present() && currentDurationSeconds > 0));
        });
        cropButton.setDisable(true);
        anonymizeButton.setDisable(true);
        saveButton.setDisable(true);
        saveAsButton.setDisable(true);

        infoInamField.textProperty().addListener((obs, o, n) -> pushInfoOverrideToFacade());
        infoIartField.textProperty().addListener((obs, o, n) -> pushInfoOverrideToFacade());
        infoIcmtField.textProperty().addListener((obs, o, n) -> pushInfoOverrideToFacade());
    }

    private void applyMetadata(WavMetadata m) {
        sampleRateLabel.setText(Integer.toString(m.sampleRate()));
        channelsLabel.setText(Integer.toString(m.numChannels()));
        bitsPerSampleLabel.setText(Integer.toString(m.bitsPerSample()));
        durationLabel.setText(String.format("%.4f", m.durationSeconds()));
        currentDurationSeconds = m.durationSeconds();
        cropButton.setDisable(!(currentSelection.present() && currentDurationSeconds > 0));
    }

    private void applyInfo(Optional<InfoMetadata> info) {
        currentInfo = info == null ? Optional.empty() : info;
        infoInamField.setText(currentInfo.flatMap(m -> m.get("INAM")).orElse(""));
        infoIartField.setText(currentInfo.flatMap(m -> m.get("IART")).orElse(""));
        infoIcmtField.setText(currentInfo.flatMap(m -> m.get("ICMT")).orElse(""));
        pushInfoOverrideToFacade();
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

    private void applyWaveform(double[][] channels) {
        waveformCanvas.setChannels(channels);
        if (channels != null && channels.length > 0 && channels[0].length > 0) {
            statusLabel.setText("Waveform gotowy");
        }
    }

    @FXML
    protected void onCrop() {
        if (currentPath == null || currentDurationSeconds <= 0 || !currentSelection.present()) {
            return;
        }
        long startMs = (long) Math.round(currentSelection.startRatio() * currentDurationSeconds * 1000.0);
        long endMs = (long) Math.round(currentSelection.endRatio() * currentDurationSeconds * 1000.0);
        facade.crop(startMs, endMs);
        statusLabel.setText("Crop ustawiony: " + startMs + "ms - " + endMs + "ms. Zapisz plik.");
        appendStatus("Ustawiono crop: " + startMs + "ms - " + endMs + "ms");
    }

    @FXML
    protected void onAnonymize() {
        facade.anonymize();
        statusLabel.setText("Anonimizacja włączona. Zapisz plik.");
        appendStatus("Włączono anonimizację");
    }

    @FXML
    protected void onSave() {
        if (currentPath == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Potwierdź zapis");
        confirm.setHeaderText("Nadpisać bieżący plik?");
        confirm.setContentText(currentPath.toString());
        Optional<ButtonType> r = confirm.showAndWait();
        if (r.isPresent() && r.get() == ButtonType.OK) {
            facade.saveFile(currentPath);
            appendStatus("Zapis (nadpisanie) uruchomiony");
        }
    }

    @FXML
    protected void onSaveAs() {
        if (currentPath == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Zapisz jako...");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pliki WAV", "*.wav", "*.wave"));
        chooser.setInitialFileName(suggestSaveAsName(currentPath));
        Window window = openButton.getScene().getWindow();
        File file = chooser.showSaveDialog(window);
        if (file != null) {
            facade.saveFile(file.toPath());
            appendStatus("Zapis jako uruchomiony: " + file.getName());
        }
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
            runFftButton.setDisable(true);
            statusLabel.setText("Wczytywanie pliku...");
            appendStatus("Rozpoczęto wczytywanie pliku");
            currentPath = null;
            currentDurationSeconds = 0.0;
            currentSelection = WaveformCanvas.OptionalSelection.empty();
            facade.loadFile(file.toPath());
        }
        fftProgressBar.setProgress(0.0);
        fftProgressLabel.setText("0%");
    }

    @FXML
    protected void onRunFft() {
        statusLabel.setText("FFT w toku...");
        runFftButton.setDisable(true);
        appendStatus("Uruchomiono FFT");
        fftProgressBar.setProgress(0.0);
        fftProgressLabel.setText("0%");
        facade.runFFT();
    }

    private void appendStatus(String message) {
        String line = "[" + LocalTime.now().format(TIME_FORMAT) + "] " + message;
        statusListView.getItems().add(0, line);
        if (statusListView.getItems().size() > 30) {
            statusListView.getItems().remove(statusListView.getItems().size() - 1);
        }
    }

    private void pushInfoOverrideToFacade() {
        if (!(facade instanceof WavelioFacadeImpl impl)) {
            return;
        }
        InfoMetadata info = new InfoMetadata();
        if (!infoInamField.getText().isBlank()) info.put("INAM", infoInamField.getText());
        if (!infoIartField.getText().isBlank()) info.put("IART", infoIartField.getText());
        if (!infoIcmtField.getText().isBlank()) info.put("ICMT", infoIcmtField.getText());
        if (info.isEmpty()) {
            impl.setPendingInfoOverride(Optional.empty());
        } else {
            impl.setPendingInfoOverride(Optional.of(info));
        }
    }

    private static String suggestSaveAsName(Path current) {
        String name = current.getFileName() != null ? current.getFileName().toString() : "output.wav";
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base + "_edited.wav";
    }
}
