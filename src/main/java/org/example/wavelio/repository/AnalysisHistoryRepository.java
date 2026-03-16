package org.example.wavelio.repository;

import org.example.wavelio.model.AnalysisHistoryEntry;

import java.util.List;

public interface AnalysisHistoryRepository {

    AnalysisHistoryEntry add(long entryId, String description);

    List<AnalysisHistoryEntry> findByEntryId(long entryId);
}

