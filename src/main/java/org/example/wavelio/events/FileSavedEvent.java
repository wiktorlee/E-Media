package org.example.wavelio.events;

import java.nio.file.Path;

public record FileSavedEvent(Path path) {}

