package org.example.wavelio.controllers;

import javafx.fxml.FXML;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.example.wavelio.bus.EventBus;
import org.example.wavelio.events.ErrorEvent;
import org.example.wavelio.events.FFTResultEvent;
import org.example.wavelio.events.FileSavedEvent;
import org.example.wavelio.events.FileLoadedEvent;
import org.example.wavelio.events.InfoMetadataParsedEvent;
import org.example.wavelio.events.MetadataParsedEvent;
import org.example.wavelio.events.PlaybackPositionEvent;
import org.example.wavelio.events.PlaybackStateChangedEvent;
import org.example.wavelio.events.SpectrogramReadyEvent;
import org.example.wavelio.events.WaveformReadyEvent;
import org.example.wavelio.events.XmpMetadataParsedEvent;
import org.example.wavelio.facade.WavelioFacade;
import org.example.wavelio.model.FftAnalysisResult;
import org.example.wavelio.model.InfoMetadata;
import org.example.wavelio.model.WavMetadata;
import org.example.wavelio.model.XmpMetadata;
import org.example.wavelio.ui.SpectrogramCanvas;
import org.example.wavelio.ui.WaveformCanvas;
import org.example.wavelio.service.WindowType;
import org.example.wavelio.service.PlaybackState;

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
    private Button runSpectrogramButton;

    @FXML
    private ComboBox<WindowType> spectrogramWindowTypeCombo;

    @FXML
    private Button cropButton;

    @FXML
    private Button anonymizeButton;

    @FXML
    private Button saveButton;

    @FXML
    private Button saveAsButton;

    @FXML
    private Button playButton;

    @FXML
    private Button stopButton;

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
    private Label playbackTimeLabel;

    @FXML
    private Label peakHzLabel;

    @FXML
    private Label peakDbLabel;

    @FXML
    private ProgressIndicator fftBusyIndicator;

    @FXML
    private ProgressIndicator spectrogramBusyIndicator;

    @FXML
    private ListView<String> statusListView;

    @FXML
    private WaveformCanvas waveformCanvas;

    @FXML
    private SpectrogramCanvas spectrogramCanvas;

    @FXML
    private CheckBox showBandsCheckBox;

    @FXML
    private TextField infoInamField;

    @FXML
    private TextField infoIartField;

    @FXML
    private TextField infoIcmtField;

    @FXML
    private TextArea xmpRawTextArea;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private WavelioFacade facade;
    private Path currentPath;
    private double currentDurationSeconds;
    private WaveformCanvas.OptionalSelection currentSelection = WaveformCanvas.OptionalSelection.empty();
    private Optional<InfoMetadata> currentInfo = Optional.empty();
    private String currentXmpChunkId = "XMP ";

    public void setFacade(WavelioFacade facade) {
        this.facade = facade;
    }

    public void setEventBus(EventBus eventBus) {
        eventBus.subscribe(FileLoadedEvent.class, e -> {
            statusLabel.setText("Wczytano: " + e.path().getFileName());
            fileNameLabel.setText(e.path().getFileName().toString());
            appendStatus("Wczytano plik: " + e.path().getFileName());
            runFftButton.setDisable(false);
            runSpectrogramButton.setDisable(false);
            cropButton.setDisable(true);
            anonymizeButton.setDisable(false);
            saveButton.setDisable(false);
            saveAsButton.setDisable(false);
            playButton.setDisable(false);
            stopButton.setDisable(true);
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
        eventBus.subscribe(XmpMetadataParsedEvent.class, e -> {
            applyXmp(e.xmp());
            appendStatus("Metadane XMP gotowe");
        });
        eventBus.subscribe(WaveformReadyEvent.class, e -> {
            applyWaveform(e.channels());
            if (e.channels() != null && e.channels().length > 0 && e.channels()[0].length > 0) {
                appendStatus("Waveform gotowy");
            }
        });
        eventBus.subscribe(FFTResultEvent.class, e -> {
            applyFftResult(e.spectrumData());
            appendStatus("FFT zakończone");
            setFftBusy(false);
        });
        eventBus.subscribe(SpectrogramReadyEvent.class, e -> {
            spectrogramCanvas.setSpectrogram(e.result());
            statusLabel.setText("Spektrogram gotowy");
            appendStatus("Spektrogram wygenerowany");
            runSpectrogramButton.setDisable(false);
            setSpectrogramBusy(false);
        });
        eventBus.subscribe(FileSavedEvent.class, e -> {
            statusLabel.setText("Zapisano: " + e.path().getFileName());
            appendStatus("Zapisano plik: " + e.path().getFileName());
        });
        eventBus.subscribe(PlaybackStateChangedEvent.class, e -> applyPlaybackState(e.state()));
        eventBus.subscribe(PlaybackPositionEvent.class, e -> applyPlaybackTime(e.currentMs(), e.durationMs()));
        eventBus.subscribe(ErrorEvent.class, e -> {
            statusLabel.setText("Błąd: " + e.message());
            appendStatus("Błąd: " + e.message());
            runFftButton.setDisable(false);
            runSpectrogramButton.setDisable(false);
            setFftBusy(false);
            setSpectrogramBusy(false);
        });

        runFftButton.setDisable(true);
        runSpectrogramButton.setDisable(true);
        spectrogramWindowTypeCombo.setItems(FXCollections.observableArrayList(WindowType.values()));
        spectrogramWindowTypeCombo.setValue(WindowType.HANN);
        setFftBusy(false);
        setSpectrogramBusy(false);
        waveformCanvas.setChannels(new double[0][0]);
        spectrogramCanvas.setSpectrogram(null);
        spectrogramCanvas.setShowBands(showBandsCheckBox.isSelected());
        showBandsCheckBox.selectedProperty().addListener((obs, oldV, newV) ->
            spectrogramCanvas.setShowBands(Boolean.TRUE.equals(newV))
        );
        waveformCanvas.setSelectionListener(sel -> {
            currentSelection = sel;
            cropButton.setDisable(!(sel.present() && currentDurationSeconds > 0));
        });
        cropButton.setDisable(true);
        anonymizeButton.setDisable(true);
        saveButton.setDisable(true);
        saveAsButton.setDisable(true);
        playButton.setDisable(true);
        stopButton.setDisable(true);
        playbackTimeLabel.setText("00:00 / 00:00");

        infoInamField.textProperty().addListener((obs, o, n) -> pushInfoOverrideToFacade());
        infoIartField.textProperty().addListener((obs, o, n) -> pushInfoOverrideToFacade());
        infoIcmtField.textProperty().addListener((obs, o, n) -> pushInfoOverrideToFacade());
        xmpRawTextArea.setEditable(true);
        xmpRawTextArea.textProperty().addListener((obs, o, n) -> pushXmpOverrideToFacade());
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

    private void applyXmp(Optional<XmpMetadata> xmp) {
        String text = xmp
            .filter(v -> !v.isEmpty())
            .map(v -> v.xml())
            .orElse("Brak metadanych XMP w pliku.");
        currentXmpChunkId = xmp.map(XmpMetadata::chunkId).orElse("XMP ");
        xmpRawTextArea.setText(text);
        pushXmpOverrideToFacade();
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
        setFftBusy(false);
        setSpectrogramBusy(false);
    }

    @FXML
    protected void onRunFft() {
        statusLabel.setText("FFT w toku...");
        runFftButton.setDisable(true);
        appendStatus("Uruchomiono FFT");
        setFftBusy(true);
        facade.runFFT();
    }

    @FXML
    protected void onRunSpectrogram() {
        statusLabel.setText("Generowanie spektrogramu...");
        runSpectrogramButton.setDisable(true);
        appendStatus("Uruchomiono generowanie spektrogramu");
        setSpectrogramBusy(true);
        WindowType selected = spectrogramWindowTypeCombo.getValue();
        if (selected == null) {
            selected = WindowType.HANN;
        }
        facade.runSpectrogram(selected);
    }

    @FXML
    protected void onPlay() {
        facade.play();
    }

    @FXML
    protected void onStop() {
        facade.stop();
    }

    private void setFftBusy(boolean busy) {
        fftBusyIndicator.setManaged(busy);
        fftBusyIndicator.setVisible(busy);
    }

    private void setSpectrogramBusy(boolean busy) {
        spectrogramBusyIndicator.setManaged(busy);
        spectrogramBusyIndicator.setVisible(busy);
    }

    private void appendStatus(String message) {
        String line = "[" + LocalTime.now().format(TIME_FORMAT) + "] " + message;
        statusListView.getItems().add(0, line);
        if (statusListView.getItems().size() > 30) {
            statusListView.getItems().remove(statusListView.getItems().size() - 1);
        }
    }

    private void applyPlaybackState(PlaybackState state) {
        if (state == PlaybackState.PLAYING) {
            playButton.setDisable(true);
            stopButton.setDisable(false);
            statusLabel.setText("Odtwarzanie...");
            return;
        }
        if (state == PlaybackState.ERROR) {
            playButton.setDisable(currentPath == null);
            stopButton.setDisable(true);
            return;
        }
        playButton.setDisable(currentPath == null);
        stopButton.setDisable(true);
    }

    private void applyPlaybackTime(long currentMs, long durationMs) {
        playbackTimeLabel.setText(formatMs(currentMs) + " / " + formatMs(durationMs));
    }

    private static String formatMs(long ms) {
        long safe = Math.max(0L, ms);
        long totalSec = safe / 1000L;
        long min = totalSec / 60L;
        long sec = totalSec % 60L;
        return String.format("%02d:%02d", min, sec);
    }

    private void pushInfoOverrideToFacade() {
        InfoMetadata info = new InfoMetadata();
        if (!infoInamField.getText().isBlank()) info.put("INAM", infoInamField.getText());
        if (!infoIartField.getText().isBlank()) info.put("IART", infoIartField.getText());
        if (!infoIcmtField.getText().isBlank()) info.put("ICMT", infoIcmtField.getText());
        if (info.isEmpty()) {
            facade.setPendingInfoOverride(Optional.empty());
        } else {
            facade.setPendingInfoOverride(Optional.of(info));
        }
    }

    private void pushXmpOverrideToFacade() {
        String raw = xmpRawTextArea.getText();
        if (raw == null) {
            facade.setPendingXmpOverride(Optional.empty());
            return;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "Brak metadanych XMP w pliku.".equals(trimmed)) {
            facade.setPendingXmpOverride(Optional.empty());
            return;
        }
        facade.setPendingXmpOverride(Optional.of(new XmpMetadata(currentXmpChunkId, raw)));
    }

    private static String suggestSaveAsName(Path current) {
        String name = current.getFileName() != null ? current.getFileName().toString() : "output.wav";
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return base + "_edited.wav";
    }
}
