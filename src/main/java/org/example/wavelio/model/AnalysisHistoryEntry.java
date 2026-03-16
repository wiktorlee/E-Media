package org.example.wavelio.model;

import java.time.Instant;

public record AnalysisHistoryEntry(
    long id,
    long entryId,
    Instant analyzedAt,
    String description
) {
}

