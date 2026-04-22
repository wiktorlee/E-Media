package org.example.wavelio.facade;

import javafx.concurrent.Task;
import org.example.wavelio.bus.EventBus;
import org.example.wavelio.db.DatabaseConfig;
import org.example.wavelio.events.ErrorEvent;
import org.example.wavelio.events.FFTProgressEvent;
import org.example.wavelio.events.FFTResultEvent;
import org.example.wavelio.events.FileSavedEvent;
import org.example.wavelio.events.FileLoadedEvent;
import org.example.wavelio.events.InfoMetadataParsedEvent;
import org.example.wavelio.events.MetadataParsedEvent;
import org.example.wavelio.events.SpectrogramReadyEvent;
import org.example.wavelio.events.WaveformReadyEvent;
import org.example.wavelio.model.FftAnalysisResult;
import org.example.wavelio.model.InfoMetadata;
import org.example.wavelio.model.LibraryEntry;
import org.example.wavelio.model.SpectrogramResult;
import org.example.wavelio.model.WavMetadata;
import org.example.wavelio.service.FFTService;
import org.example.wavelio.service.InfoChunkService;
import org.example.wavelio.service.StftService;
import org.example.wavelio.service.WavParseService;
import org.example.wavelio.service.WavEditService;
import org.example.wavelio.service.WaveformService;
import org.example.wavelio.service.WindowType;
import org.example.wavelio.repository.AnalysisHistoryRepository;
import org.example.wavelio.repository.LibraryRepository;
import org.example.wavelio.repository.SqliteAnalysisHistoryRepository;
import org.example.wavelio.repository.SqliteLibraryRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public final class WavelioFacadeImpl implements WavelioFacade {

    private static final int WAVEFORM_POINTS = 2400;

    private record WaveformLoadResult(short[][] pcm, double[][] precomputed) {}

    private final EventBus eventBus;
    private final ExecutorService executor;
    private final LibraryRepository libraryRepository;
    private final AnalysisHistoryRepository analysisHistoryRepository;
    private final WavParseService wavParseService;
    private final FFTService fftService;
    private final WaveformService waveformService;
    private final InfoChunkService infoChunkService;
    private final WavEditService wavEditService;
    private final StftService stftService;

    private volatile WavMetadata currentMetadata;
    private volatile Path currentPath;
    /** Precomputed waveform for {@link #getWaveformData()} — same shape as {@link org.example.wavelio.events.WaveformReadyEvent}. */
    private volatile double[][] currentPrecomputedWaveform;
    private volatile short[][] cachedPcmChannels;
    private volatile Path cachedPcmPath;

    private volatile Optional<InfoMetadata> currentInfo = Optional.empty();
    private volatile SpectrogramResult currentSpectrogram;

    private volatile Optional<WavEditService.CropRangeMs> pendingCrop = Optional.empty();
    private volatile boolean pendingAnonymize;
    private volatile Optional<InfoMetadata> pendingInfoOverride = Optional.empty();

    WavelioFacadeImpl(
        EventBus eventBus,
        ExecutorService executor,
        LibraryRepository libraryRepository,
        AnalysisHistoryRepository analysisHistoryRepository,
        WavParseService wavParseService,
        FFTService fftService,
        WaveformService waveformService,
        InfoChunkService infoChunkService,
        WavEditService wavEditService,
        StftService stftService
    ) {
        this.eventBus = eventBus;
        this.executor = executor;
        this.libraryRepository = libraryRepository;
        this.analysisHistoryRepository = analysisHistoryRepository;
        this.wavParseService = wavParseService;
        this.fftService = fftService;
        this.waveformService = waveformService;
        this.infoChunkService = infoChunkService;
        this.wavEditService = wavEditService;
        this.stftService = stftService;
    }

    public static WavelioFacadeImpl create(EventBus eventBus) {
        DatabaseConfig config = DatabaseConfig.forUserHome();
        try {
            config.initializeSchema();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize database schema", e);
        }
        ExecutorService executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "wavelio-worker");
            t.setDaemon(true);
            return t;
        });
        LibraryRepository libraryRepository = new SqliteLibraryRepository(config);
        AnalysisHistoryRepository historyRepository = new SqliteAnalysisHistoryRepository(config);
        WavParseService wavParseService = new WavParseService();
        FFTService fftService = new FFTService();
        WaveformService waveformService = new WaveformService();
        InfoChunkService infoChunkService = new InfoChunkService();
        WavEditService wavEditService = new WavEditService();
        StftService stftService = new StftService();
        return new WavelioFacadeImpl(
            eventBus,
            executor,
            libraryRepository,
            historyRepository,
            wavParseService,
            fftService,
            waveformService,
            infoChunkService,
            wavEditService,
            stftService
        );
    }

    @Override
    public void loadFile(Path path) {
        Task<FileLoadedEvent> task = new Task<>() {
            @Override
            protected FileLoadedEvent call() throws Exception {
                if (path == null || !java.nio.file.Files.exists(path)) {
                    throw new IllegalArgumentException("Plik nie istnieje: " + path);
                }
                libraryRepository.upsertByPath(path, path.getFileName().toString());
                WavMetadata metadata = wavParseService.parse(path);
                return new FileLoadedEvent(path, metadata);
            }
        };
        task.setOnSucceeded(e -> {
            FileLoadedEvent value = task.getValue();
            currentPath = value.path();
            currentMetadata = value.metadata();
            cachedPcmChannels = null;
            cachedPcmPath = null;
            currentPrecomputedWaveform = null;
            currentSpectrogram = null;
            pendingCrop = Optional.empty();
            pendingAnonymize = false;
            pendingInfoOverride = Optional.empty();
            eventBus.publish(value);
            eventBus.publish(new MetadataParsedEvent(value.metadata()));
            startInfoTask(value.path());
            startWaveformTask(value.path());
        });
        task.setOnFailed(ev -> eventBus.publish(new ErrorEvent(
            task.getException() != null ? task.getException().getMessage() : "Nie udało się wczytać pliku",
            task.getException()
        )));
        executor.execute(task);
    }

    @Override
    public Optional<WavMetadata> getCurrentMetadata() {
        return Optional.ofNullable(currentMetadata);
    }

    @Override
    public void runFFT() {
        Path path = currentPath;
        WavMetadata metadata = currentMetadata;
        if (path == null || metadata == null) {
            eventBus.publish(new ErrorEvent("Najpierw wczytaj plik WAV.", null));
            return;
        }

        Task<FftAnalysisResult> task = new Task<>() {
            @Override
            protected FftAnalysisResult call() throws Exception {
                updateProgress(0, 100);
                short[][] pcmChannels;
                if (Objects.equals(path, cachedPcmPath) && cachedPcmChannels != null) {
                    pcmChannels = cachedPcmChannels;
                } else {
                    pcmChannels = wavParseService.readPcm16Channels(path);
                }
                updateProgress(20, 100);
                FftAnalysisResult result = fftService.analyzePerChannelPcm16(
                    pcmChannels,
                    metadata.sampleRate(),
                    p -> updateProgress(20 + (int) Math.round(p * 75.0), 100)
                );
                updateProgress(100, 100);
                return result;
            }
        };
        task.progressProperty().addListener((obs, oldV, newV) -> {
            int percent = (int) Math.round(newV.doubleValue() * 100.0);
            if (percent < 0) {
                percent = 0;
            }
            if (percent > 100) {
                percent = 100;
            }
            eventBus.publish(new FFTProgressEvent(percent));
        });

        task.setOnSucceeded(e -> {
            FftAnalysisResult result = task.getValue();
            eventBus.publish(new FFTProgressEvent(100));
            eventBus.publish(new FFTResultEvent(result));
            try {
                libraryRepository.findByPath(path).ifPresent(entry ->
                    analysisHistoryRepository.add(entry.id(), "FFT " + result.windowSize() + " Hann dB per-channel")
                );
            } catch (Exception ex) {
                eventBus.publish(new ErrorEvent("FFT policzone, ale nie zapisano historii analizy.", ex));
            }
        });
        task.setOnFailed(e -> eventBus.publish(new ErrorEvent(
            task.getException() != null ? task.getException().getMessage() : "Nie udało się wykonać FFT",
            task.getException()
        )));
        executor.execute(task);
    }

    @Override
    public void runSpectrogram(WindowType windowType) {
        Path path = currentPath;
        WavMetadata metadata = currentMetadata;
        if (path == null || metadata == null) {
            eventBus.publish(new ErrorEvent("Najpierw wczytaj plik WAV.", null));
            return;
        }
        WindowType selectedWindow = windowType == null ? WindowType.HANN : windowType;

        Task<SpectrogramResult> task = new Task<>() {
            @Override
            protected SpectrogramResult call() throws Exception {
                short[][] pcmChannels;
                if (Objects.equals(path, cachedPcmPath) && cachedPcmChannels != null) {
                    pcmChannels = cachedPcmChannels;
                } else {
                    pcmChannels = wavParseService.readPcm16Channels(path);
                }
                return stftService.analyzeMonoFromPcm16(
                    pcmChannels,
                    metadata.sampleRate(),
                    2048,
                    512,
                    selectedWindow
                );
            }
        };
        task.setOnSucceeded(e -> {
            SpectrogramResult result = task.getValue();
            currentSpectrogram = result;
            eventBus.publish(new SpectrogramReadyEvent(result));
        });
        task.setOnFailed(e -> eventBus.publish(new ErrorEvent(
            task.getException() != null ? task.getException().getMessage() : "Nie udało się wygenerować spektrogramu",
            task.getException()
        )));
        executor.execute(task);
    }

    @Override
    public void play() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void crop(long startMs, long endMs) {
        WavMetadata m = currentMetadata;
        if (m == null) {
            eventBus.publish(new ErrorEvent("Najpierw wczytaj plik WAV.", null));
            return;
        }
        long durationMs = (long) Math.floor(m.durationSeconds() * 1000.0);
        long s = Math.max(0, Math.min(durationMs, startMs));
        long e = Math.max(0, Math.min(durationMs, endMs));
        if (e < s) {
            long tmp = s;
            s = e;
            e = tmp;
        }
        pendingCrop = Optional.of(new WavEditService.CropRangeMs(s, e));
    }

    @Override
    public void anonymize() {
        if (currentPath == null) {
            eventBus.publish(new ErrorEvent("Najpierw wczytaj plik WAV.", null));
            return;
        }
        pendingAnonymize = true;
    }

    @Override
    public void saveFile(Path path) {
        Path src = currentPath;
        if (src == null) {
            eventBus.publish(new ErrorEvent("Najpierw wczytaj plik WAV.", null));
            return;
        }
        Optional<WavEditService.CropRangeMs> crop = pendingCrop;
        boolean anonymize = pendingAnonymize;
        Optional<InfoMetadata> infoOverride = pendingInfoOverride;

        Task<Path> task = new Task<>() {
            @Override
            protected Path call() throws Exception {
                wavEditService.saveEdited(src, path, crop, anonymize, infoOverride);
                return path;
            }
        };
        task.setOnSucceeded(e -> {
            Path saved = task.getValue();
            eventBus.publish(new FileSavedEvent(saved));
            // Reload from saved file so UI reflects edits.
            loadFile(saved);
        });
        task.setOnFailed(e -> eventBus.publish(new ErrorEvent(
            task.getException() != null ? task.getException().getMessage() : "Nie udało się zapisać pliku",
            task.getException()
        )));
        executor.execute(task);
    }

    @Override
    public List<LibraryEntry> getLibraryEntries() {
        return libraryRepository.findAll();
    }

    @Override
    public Optional<double[][]> getWaveformData() {
        return Optional.ofNullable(currentPrecomputedWaveform);
    }

    @Override
    public Optional<SpectrogramResult> getSpectrogramData() {
        return Optional.ofNullable(currentSpectrogram);
    }

    private void startWaveformTask(Path path) {
        Task<WaveformLoadResult> waveformTask = new Task<>() {
            @Override
            protected WaveformLoadResult call() throws Exception {
                short[][] channels = wavParseService.readPcm16Channels(path);
                double[][] precomputed = waveformService.buildLinearPrecomputed(channels, WAVEFORM_POINTS);
                return new WaveformLoadResult(channels, precomputed);
            }
        };
        waveformTask.setOnSucceeded(e -> {
            if (!Objects.equals(path, currentPath)) {
                return;
            }
            WaveformLoadResult value = waveformTask.getValue();
            cachedPcmChannels = value.pcm();
            cachedPcmPath = path;
            currentPrecomputedWaveform = value.precomputed();
            eventBus.publish(new WaveformReadyEvent(value.precomputed()));
        });
        waveformTask.setOnFailed(e -> {
            if (!Objects.equals(path, currentPath)) {
                return;
            }
            cachedPcmChannels = null;
            cachedPcmPath = null;
            currentPrecomputedWaveform = null;
            eventBus.publish(new WaveformReadyEvent(new double[0][0]));
            eventBus.publish(new ErrorEvent(
                waveformTask.getException() != null ? waveformTask.getException().getMessage() : "Nie udało się przygotować waveform",
                waveformTask.getException()
            ));
        });
        executor.execute(waveformTask);
    }

    private void startInfoTask(Path path) {
        Task<Optional<InfoMetadata>> task = new Task<>() {
            @Override
            protected Optional<InfoMetadata> call() throws Exception {
                return infoChunkService.readInfo(path);
            }
        };
        task.setOnSucceeded(e -> {
            if (!Objects.equals(path, currentPath)) {
                return;
            }
            currentInfo = Optional.ofNullable(task.getValue()).orElse(Optional.empty());
            eventBus.publish(new InfoMetadataParsedEvent(currentInfo));
        });
        task.setOnFailed(e -> {
            if (!Objects.equals(path, currentPath)) {
                return;
            }
            currentInfo = Optional.empty();
            eventBus.publish(new InfoMetadataParsedEvent(Optional.empty()));
            eventBus.publish(new ErrorEvent(
                task.getException() != null ? task.getException().getMessage() : "Nie udało się odczytać metadanych INFO",
                task.getException()
            ));
        });
        executor.execute(task);
    }

    @Override
    public void setPendingInfoOverride(Optional<InfoMetadata> info) {
        pendingInfoOverride = info == null ? Optional.empty() : info;
    }
}
