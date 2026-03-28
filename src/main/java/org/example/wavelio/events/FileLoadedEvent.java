package org.example.wavelio.events;

import org.example.wavelio.model.WavMetadata;

import java.nio.file.Path;

public record FileLoadedEvent(Path path, WavMetadata metadata) {}
