package org.example.wavelio.facade;

import org.example.wavelio.model.WavMetadata;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface WavelioFacade {

    void loadFile(Path path);

    Optional<WavMetadata> getCurrentMetadata();

    void runFFT();

    void play();

    void stop();

    void crop(long startMs, long endMs);

    void anonymize();

    void saveFile(Path path);

    List<?> getLibraryEntries();

    Optional<double[]> getWaveformData();
}
