package org.example.wavelio.facade;

import org.example.wavelio.model.LibraryEntry;
import org.example.wavelio.model.InfoMetadata;
import org.example.wavelio.model.SpectrogramResult;
import org.example.wavelio.model.WavMetadata;
import org.example.wavelio.model.XmpMetadata;
import org.example.wavelio.service.WindowType;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface WavelioFacade {

    void loadFile(Path path);

    Optional<WavMetadata> getCurrentMetadata();

    void runFFT();

    void runSpectrogram(WindowType windowType);

    void play();

    void stop();

    void crop(long startMs, long endMs);

    void anonymize();

    void saveFile(Path path);

    void setPendingInfoOverride(Optional<InfoMetadata> info);

    void setPendingXmpOverride(Optional<XmpMetadata> xmp);

    List<LibraryEntry> getLibraryEntries();

    Optional<double[][]> getWaveformData();

    Optional<SpectrogramResult> getSpectrogramData();

    Optional<XmpMetadata> getXmpData();
}
