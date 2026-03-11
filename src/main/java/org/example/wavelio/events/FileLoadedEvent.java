package org.example.wavelio.events;

import java.nio.file.Path;

public record FileLoadedEvent(Path path, Object metadata) {
    public FileLoadedEvent(Path path) {
        this(path, null);
    }
}
