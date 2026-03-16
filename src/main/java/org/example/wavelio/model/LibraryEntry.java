package org.example.wavelio.model;

import java.nio.file.Path;
import java.time.Instant;

public record LibraryEntry(
    long id,
    Path filePath,
    String displayName,
    Instant addedAt,
    Instant lastAnalyzedAt
) {

    public LibraryEntry withId(long newId) {
        return new LibraryEntry(newId, filePath, displayName, addedAt, lastAnalyzedAt);
    }

    public LibraryEntry withLastAnalyzedAt(Instant instant) {
        return new LibraryEntry(id, filePath, displayName, addedAt, instant);
    }
}

