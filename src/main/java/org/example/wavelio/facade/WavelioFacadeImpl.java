package org.example.wavelio.facade;

import javafx.concurrent.Task;
import org.example.wavelio.bus.EventBus;
import org.example.wavelio.db.DatabaseConfig;
import org.example.wavelio.events.ErrorEvent;
import org.example.wavelio.events.FileLoadedEvent;
import org.example.wavelio.model.LibraryEntry;
import org.example.wavelio.model.WavMetadata;
import org.example.wavelio.repository.AnalysisHistoryRepository;
import org.example.wavelio.repository.LibraryRepository;
import org.example.wavelio.repository.SqliteAnalysisHistoryRepository;
import org.example.wavelio.repository.SqliteLibraryRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public final class WavelioFacadeImpl implements WavelioFacade {

    private final EventBus eventBus;
    private final ExecutorService executor;
    private final LibraryRepository libraryRepository;
    private final AnalysisHistoryRepository analysisHistoryRepository;

    private volatile WavMetadata currentMetadata;
    private volatile double[] waveformData;

    public WavelioFacadeImpl(
        EventBus eventBus,
        ExecutorService executor,
        LibraryRepository libraryRepository,
        AnalysisHistoryRepository analysisHistoryRepository
    ) {
        this.eventBus = eventBus;
        this.executor = executor;
        this.libraryRepository = libraryRepository;
        this.analysisHistoryRepository = analysisHistoryRepository;
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
        return new WavelioFacadeImpl(eventBus, executor, libraryRepository, historyRepository);
    }

    @Override
    public void loadFile(Path path) {
        Task<FileLoadedEvent> task = new Task<>() {
            @Override
            protected FileLoadedEvent call() throws Exception {
                if (path == null || !java.nio.file.Files.exists(path)) {
                    throw new IllegalArgumentException("File does not exist: " + path);
                }
                libraryRepository.upsertByPath(path, path.getFileName().toString());
                return new FileLoadedEvent(path, null);
            }
        };
        task.setOnSucceeded(e -> eventBus.publish(task.getValue()));
        task.setOnFailed(e -> eventBus.publish(new ErrorEvent(
            task.getException() != null ? task.getException().getMessage() : "Load failed",
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
    }

    @Override
    public void play() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void crop(long startMs, long endMs) {
    }

    @Override
    public void anonymize() {
    }

    @Override
    public void saveFile(Path path) {
    }

    @Override
    public List<LibraryEntry> getLibraryEntries() {
        return libraryRepository.findAll();
    }

    @Override
    public Optional<double[]> getWaveformData() {
        return Optional.ofNullable(waveformData);
    }
}
