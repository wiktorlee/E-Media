package org.example.wavelio.repository;

import org.example.wavelio.db.DatabaseConfig;
import org.example.wavelio.model.AnalysisHistoryEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SqliteAnalysisHistoryRepository implements AnalysisHistoryRepository {

    private final DatabaseConfig config;

    public SqliteAnalysisHistoryRepository(DatabaseConfig config) {
        this.config = config;
    }

    @Override
    public AnalysisHistoryEntry add(long entryId, String description) {
        long now = config.nowEpochMillis();
        try (Connection conn = config.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO analysis_history (entry_id, analyzed_at, description) VALUES (?, ?, ?)",
                 Statement.RETURN_GENERATED_KEYS
             )) {
            ps.setLong(1, entryId);
            ps.setLong(2, now);
            ps.setString(3, description);
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    return new AnalysisHistoryEntry(
                        id,
                        entryId,
                        Instant.ofEpochMilli(now),
                        description
                    );
                }
            }
            throw new IllegalStateException("No generated key for analysis_history insert");
        } catch (SQLException e) {
            throw new IllegalStateException("Error inserting analysis history", e);
        }
    }

    @Override
    public List<AnalysisHistoryEntry> findByEntryId(long entryId) {
        List<AnalysisHistoryEntry> result = new ArrayList<>();
        try (Connection conn = config.openConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT id, entry_id, analyzed_at, description FROM analysis_history " +
                     "WHERE entry_id = ? ORDER BY analyzed_at DESC"
             )) {
            ps.setLong(1, entryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    long analyzedAt = rs.getLong("analyzed_at");
                    String description = rs.getString("description");
                    result.add(new AnalysisHistoryEntry(
                        id,
                        rs.getLong("entry_id"),
                        Instant.ofEpochMilli(analyzedAt),
                        description
                    ));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error loading analysis history", e);
        }
        return result;
    }
}

